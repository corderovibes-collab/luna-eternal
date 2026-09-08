#!/usr/bin/env python3
"""
Sincroniza los mods de TERCEROS del servidor.

LA REGLA DE ORO

    el servidor tiene que ser un SUBCONJUNTO del cliente

Fabric sincroniza el registro al conectar. Si el servidor registra un bloque o
un objeto que al cliente le falta, **al cliente no le deja entrar**: lo echa con
"Registry remapping failed". Al reves no pasa nada — un cliente con mods de mas
es el caso normal.

De ahi sale como funciona este script: **las versiones se leen del manifiesto
que ya usa el launcher**, no de Modrinth. Asi el jar del servidor y el del
jugador son literalmente el mismo fichero, byte a byte, y la pareja no se puede
desincronizar.

POR QUE NO ESTAN LOS 74 DEL PACK OFICIAL

Porque `environment: "*"` en un jar significa "puede cargar en los dos lados",
no "hace falta en los dos". De los 74, **34 se declaran de cliente** y de los 41
restantes la mayoria son interfaz: mapas, recetas, animaciones, tooltips. En un
servidor solo gastarian RAM, y este tiene 4 GB (B-003).

Los que estan aqui son los que hacen trabajo de servidor de verdad. Ninguno
registra bloques ni objetos, asi que **ninguno puede dejar a nadie fuera**.

Uso:
    python tools/mods_servidor.py            # dice que falta y que sobra
    python tools/mods_servidor.py --aplicar  # lo sube
    python tools/mods_servidor.py --aplicar --reiniciar
"""
import argparse
import hashlib
import json
import sys
import urllib.request
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import ptero  # noqa: E402
from gen_manifest import BASE_RAW  # noqa: E402

UA = {"User-Agent": "PokeReport-LunaEternal/0.1 (dev)"}

# Mods de terceros que SI van en el servidor, con el motivo al lado. El motivo
# no es decoracion: es lo que impide que dentro de seis meses alguien anada
# medio pack "porque venia en el oficial".
#
# La clave es el ID del mod tal y como aparece al principio del nombre del jar
# en el manifiesto.
QUEREMOS = {
    "lithium":      "Optimiza la logica del servidor: mobs, fisicas, chunks. "
                    "Lo teniamos solo en el cliente, donde hace mucho menos",
    "ferritecore":  "Baja el consumo de memoria de los estados de bloque. En un "
                    "servidor de 4 GB con 96 bloques de neon nuevos, importa",
    "krypton":      "Optimiza la capa de red. Es de servidor por definicion",
    "clumps":       "Junta las bolas de experiencia en una. Menos entidades = "
                    "menos lag en cuanto haya varias personas peleando",
    "letmedespawn": "Los mobs desaparecen como deberian. Sin esto se acumulan "
                    "y se comen el limite de entidades",
    # ⚠ `server_side: required` EN MODRINTH, y no es un formalismo: la
    #   colocacion multiple la APLICA EL SERVIDOR (P6, nunca confiar en el
    #   cliente). Solo en el cliente, las herramientas se ven y no hacen nada.
    #
    #   El jar se llama `effortless-1.21.1-3.4.0.jar`; aqui va el NOMBRE DEL
    #   FICHERO, no el slug de Modrinth --son dos cosas distintas y ya costo un
    #   manifiesto entero con `letmedespawn`/`lmd`.
    "effortless":   "Simetria, matrices y modos de construccion. Lo aplica el "
                    "SERVIDOR, asi que solo en el cliente no haria nada. "
                    "Registra 0 bloques y 0 objetos: no puede descuadrar "
                    "ningun registro",

    # --- lo que llega con CobbleVerse (2026-08-17) -------------------------
    # Estos NO son optimizaciones: son REGLAS DE JUEGO. Un mod de reglas que
    # solo esta en el cliente no hace nada --el servidor es quien decide
    # (P6)-- asi que o van aqui o el jugador ve la interfaz de algo que no
    # existe.
    "CobbleverseBadges":
        "LAS MEDALLAS. Es lo que el usuario pidio, y sin el en el servidor no "
        "hay nada que ganar: quien las guarda es el servidor",
    "rctapi":
        "Dependencia de rctmod y donde vive la logica de los entrenadores",
    "rctmod":
        "Los entrenadores que aparecen por el mundo y con los que se pelea. "
        "Sin el en el servidor no aparece ninguno",
    "cobblemonraiddens":
        "Las incursiones. Las genera y las arbitra el servidor",
    "mega_showdown":
        "Megaevoluciones y formas. Cambia el combate, que es de servidor",
    "fightorflight":
        "Los Pokemon salvajes atacan en vez de quedarse mirando. Es IA de "
        "entidad: solo corre en el servidor",

    # ⚠ ESTOS TRES NO SE ELIGIERON: LOS PIDIO EL LOG.
    #
    # Al cargar el datapack de entrenadores de CobbleVerse, 55 tablas de botin
    # se negaron a parsear con "Unknown registry key ... tmcraft:tm_bulkup" y
    # parecidos: sus recompensas son objetos de estos mods, y sin ellos EN EL
    # SERVIDOR esas 55 tablas no existen — o sea, 55 entrenadores que no sueltan
    # nada al ganarles. El cliente los tiene; el servidor es quien reparte.
    "tmcraft":
        "Las MT que sueltan los entrenadores. 34 tablas de botin sin el",
    "LumyMon":
        "Objetos de recompensa de los entrenadores. 19 tablas de botin sin el",
    "Only Bottle Caps":
        "Las chapas del entrenamiento de IVs. 2 tablas de botin sin el",
    "cobblenav":
        "La cana-navegador. Una tabla de botin la nombra "
        "(`cobblenav:fishingnav_item`) y sin el mod ese objeto no existe",

    # ⚠⚠ ESTOS DOS ECHAN A LA GENTE DEL SERVIDOR SI FALTAN, Y NINGUN
    #    RESOLUTOR DE DEPENDENCIAS LOS ENCUENTRA.
    #
    # `accessories_compat_layer` es un PUENTE entre Trinkets y Accessories, y
    # nada declara depender de un puente: no es dependencia de nadie, asi que
    # `con_dependencias()` no lo trae. Pero registra RANURAS, y las ranuras se
    # sincronizan: con el puente solo en el cliente, el servidor manda su lista
    # y el cliente no sabe leerla. Se cae al ENTRAR, con
    #
    #   DecoderException: Failed to decode packet 'clientbound/custom_payload'
    #   Caused by: StructFieldException: [Field: exported_slots]
    #
    # que no menciona ni Accessories ni Trinkets por ningun lado.
    #
    # Regla que sale de aqui: un mod que registre algo que se SINCRONIZA tiene
    # que estar en los dos lados, sea o no dependencia de alguien.
    # (aqui estuvieron `accessories_compat_layer` y `trinkets`. Se resolvio al
    #  reves: se QUITARON DEL CLIENTE, porque de ese puente no dependia ningun
    #  mod del pack. Ver EXCLUIDOS en gen_modpack.py)
}

# ---------------------------------------------------------------------------
# MODS DE CONTENIDO: LOS QUE REGISTRAN BLOQUES
# ---------------------------------------------------------------------------
# ⚠⚠ ESTO NO ES UNA LISTA DE DESEOS, ES UNA OBLIGACION TECNICA.
#
# Los bloques se mandan por la red como un NUMERO, no por su nombre: el
# servidor dice "bloque 4721" y el cliente lo busca en su tabla. Las dos tablas
# se construyen registrando mods, asi que si el cliente tiene mods de bloques
# que el servidor no tiene, LAS TABLAS NO COINCIDEN y el cliente dibuja otra
# cosa. Medido el 2026-08-17: 5.687 bloques de diferencia.
#
# Sintoma real, y no se parece nada a la causa: el usuario coloco un neon
# blanco y le salio `lumymon:mesprit_altar`. Preguntado el servidor por esa
# coordenada, contesto AIRE. No era textura ni ID duplicado: eran dos tablas
# distintas.
#
# Con el pack de Cobblemon no pasaba y por eso el diseño de este script
# aguanto: alli los mods extra del cliente eran HUD, mapas y tooltips, que no
# registran nada. CobbleVerse trae DIECIOCHO mods de bloques.
#
# ⚠ LA LISTA A MANO ES DEUDA. Lo correcto es DEDUCIRLA del manifiesto —un mod
#   con blockstates propios y `environment != client` va al servidor, punto— y
#   esta escrita asi solo porque habia un servidor roto que arreglar. Cuando se
#   deduzca, este bloque desaparece entero.
CONTENIDO = {
    "rechiseled":        "3.627 bloques de sillar",
    "handcrafted":       "muebles",
    "Luckys-Cozyhome":   "muebles",
    "Carved Wood":       "madera tallada",
    "CobbleFurnies":     "muebles de Cobblemon",
    "toms_storage":      "almacenamiento",
    "cobblemon-additions": "bloques de Cobblemon",
    "sophisticatedstorage": "almacenamiento",
    "pokeblocks":        "bloques decorativos",
    "waystones":         "piedras de viaje",
    # ⚠ FUERA (2026-08-26). La mochila del servidor es NUESTRA y se
    #   desbloquea por rango (V021). Este mod repartia mochilas por TRES
    #   vias --receta, cofre y mob que la lleva puesta-- o sea
    #   almacenamiento por fuera del sistema de rangos.
    #   `sophisticatedcore` y `sophisticatedstorage` SE QUEDAN: storage da
    #   bloques que pueden estar ya colocados en la ciudadela, y depende de
    #   core, no de backpacks.
    # "sophisticatedbackpacks": "mochilas",
    "comforts":          "sacos de dormir y hamacas",
    "beautify":          "decoracion",
    "moar-concrete":     "160 bloques de hormigon",
    "cobblecuisine":     "cocina",
    "Cobbreeding":       "crianza",
    "cobblemon-battle-positions": "posiciones de combate",
    # ⚠ ESTE SE ESCAPO DEL PRIMER ANALISIS. Registra sus 72 bloques en el
    #   namespace `minecraft` --backportea bloques de 1.21.2+-- asi que
    #   buscando "blockstates de namespace propio" parecia que no añadia nada.
    "VanillaBackport":   "72 bloques nuevos, y los registra como `minecraft:`",
    # ⚠⚠⚠ 49 OBJETOS Y 8 BLOQUES, y ademas ES DE SERVIDOR DE VERDAD: sus
    #    sobres los reparte `CartasService`, las cartas caen al capturar y al
    #    derrotar, y sus dos comandos son de operador. Sin el en el servidor no
    #    solo se descuadran las tablas: la mitad del sistema no existe.
    "cobblemon-cards":   "49 objetos y 8 bloques del sistema de cartas",
}
QUEREMOS.update({k: f"Registra bloques ({v}). Si esta solo en el cliente, las "
                    f"tablas de bloques no coinciden y se dibuja otra cosa"
                 for k, v in CONTENIDO.items()})

# Lo que NO se toca aunque no este en QUEREMOS: es nuestro o es infraestructura.
# Sin esta lista, `--aplicar` borraria el mod del servidor entero.
INTOCABLES = ("lunaeternal", "lunaneon", "cobblemon-cards",
              "fabric-api", "Cobblemon",
              "EasyAuth", "worldedit", "Axiom",
              # ⚠ CHUNKY ES SOLO DE SERVIDOR Y ESTA COMPROBADO, no supuesto:
              #   0 blockstates, 0 modelos de objeto, 0 traducciones y un solo
              #   entrypoint `main`. NO REGISTRA NADA QUE SE SINCRONICE, asi que
              #   tenerlo aqui y no en el cliente no descuadra ninguna tabla --
              #   misma categoria que EasyAuth y WorldEdit.
              #   Pre-genera chunks: `/chunky world <mundo>`, `/chunky radius 3000`,
              #   `/chunky start`. TODAVIA NO SE HA PRE-GENERADO NADA.
              "Chunky",
              # ⚠ LIBRERIAS YA INSTALADAS: NO SE BORRAN AUNQUE PAREZCAN SOBRAR.
              #
              # Desde que `aporta()` mira dentro de los jars anidados, estas dos
              # salen como sobrantes: alguien las lleva incrustadas. Puede que
              # sea verdad y puede que no, y la diferencia entre las dos cosas
              # es un servidor que no arranca. Borrar una libreria para ahorrar
              # 800 KB no compensa ni de lejos.
              "cloth-config", "owo-lib")


# Lo que aporta el propio entorno: no hay jar que buscar para esto.
YA_ESTA = {"fabricloader", "fabric", "minecraft", "java", "fabric-api",
           "fabric_api", "cobblemon"}


def manifiesto() -> dict:
    return json.load(urllib.request.urlopen(f"{BASE_RAW}/manifest.json"))


def metadatos(url: str) -> dict:
    import io
    import zipfile
    z = zipfile.ZipFile(io.BytesIO(urllib.request.urlopen(
        urllib.request.Request(url, headers=UA)).read()))
    return json.loads(z.read("fabric.mod.json"), strict=False)


_CACHE_APORTA: dict = {}


def aporta(url: str) -> set:
    """Todos los IDs de mod que un jar mete en el juego al cargarse.

    Son TRES cosas, y mirar solo la primera es lo que hacia abortar por nada:

      1. su propio `id`
      2. sus `provides` — alias con los que otros lo nombran
      3. **LOS JARS ANIDADOS** (`META-INF/jars/`, lo que Fabric llama JiJ), que
         se cargan como mods de pleno derecho

    Sin el punto 3, `sophisticatedcore` "necesitaba" `team_reborn_energy` y el
    script se negaba a aplicar nada — cuando ese mod viaja DENTRO de el, en
    `META-INF/jars/energy-4.1.0.jar`. Lo mismo con `trinkets` y
    `cardinal-components`. Dos abortos seguidos por el mismo punto ciego, y los
    dos con el servidor esperando un arreglo.

    Se recorre en profundidad porque un jar anidado puede anidar otro.
    """
    if url in _CACHE_APORTA:
        return _CACHE_APORTA[url]
    import io
    import zipfile

    def hurgar(z, acc):
        try:
            m = json.loads(z.read("fabric.mod.json"), strict=False)
        except Exception:                                        # noqa: BLE001
            return
        acc.add(m.get("id", ""))
        acc.update(m.get("provides", []) or [])
        for n in z.namelist():
            if n.startswith("META-INF/jars/") and n.endswith(".jar"):
                try:
                    hurgar(zipfile.ZipFile(io.BytesIO(z.read(n))), acc)
                except Exception:                                # noqa: BLE001
                    pass

    datos = urllib.request.urlopen(
        urllib.request.Request(url, headers=UA)).read()
    acc: set = set()
    hurgar(zipfile.ZipFile(io.BytesIO(datos)), acc)
    _CACHE_APORTA[url] = acc
    return acc


def con_dependencias(quiero: dict, man: dict) -> dict:
    """Anade las dependencias que falten, sacadas del manifiesto del cliente.

    Existe por una tarde tonta: `letmedespawn` exige `almanac`, no lo subimos, y
    el servidor **se quedo sin arrancar** con "Incompatible mods found!". El
    fallo no se ve hasta el reinicio, que es el peor momento posible.

    Se resuelve en bucle porque una dependencia puede traer otra. Y si algo no
    aparece en el manifiesto se ABORTA: mejor no aplicar nada que dejar el
    servidor sin levantar.
    """
    candidatos = {f["path"].split("/")[-1]: f for f in man["files"]
                  if f["path"].startswith("mods/")}
    resuelto, pendiente = dict(quiero), list(quiero.values())
    ids = set()

    while pendiente:
        entrada = pendiente.pop()
        meta = metadatos(entrada["url"])
        # No solo su `id`: tambien sus alias y TODO lo que lleve anidado. Ver
        # `aporta()` — mirar solo el id aborto dos veces por dependencias que
        # ya venian dentro del propio jar.
        ids |= aporta(entrada["url"])
        for dep in meta.get("depends", {}):
            if dep in YA_ESTA or dep in ids:
                continue
            # ¿Ya lo tiene el servidor por otra via?
            if any(dep.lower() in i.lower() for i in INTOCABLES):
                continue
            if any(j.lower().startswith(dep.lower()) for j in resuelto):
                continue
            hallado = [j for j in candidatos
                       if j.lower().startswith(dep.lower().replace("_", ""))
                       or j.lower().startswith(dep.lower())]
            if not hallado:
                raise SystemExit(
                    f"'{meta.get('id')}' necesita '{dep}' y no esta en el "
                    f"manifiesto del cliente. NO se aplica nada: subirlo dejaria "
                    f"el servidor sin arrancar.")
            jar = sorted(hallado)[0]
            resuelto[jar] = candidatos[jar]
            pendiente.append(candidatos[jar])
            print(f"  + {jar:<44} dependencia de {meta.get('id')}")
    return resuelto


def deseados(man: dict) -> dict:
    """{nombre_jar: entrada} de los mods que queremos en el servidor."""
    salida = {}
    for f in man["files"]:
        if not f["path"].startswith("mods/"):
            continue
        jar = f["path"].split("/")[-1]
        for mid in QUEREMOS:
            if jar.lower().startswith(mid.lower()):
                salida[jar] = f
    faltan = set(QUEREMOS) - {m for jar in salida for m in QUEREMOS
                              if jar.lower().startswith(m.lower())}
    if faltan:
        raise SystemExit(f"No estan en el manifiesto del cliente: {sorted(faltan)}.\n"
                         f"Un mod en el servidor que el cliente no tiene deja a "
                         f"todo el mundo sin entrar. Se aborta.")
    return salida


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--aplicar", action="store_true", help="sube y borra de verdad")
    ap.add_argument("--reiniciar", action="store_true",
                    help="reinicia al terminar (los mods solo cargan al arrancar)")
    args = ap.parse_args()

    man = manifiesto()
    quiero = con_dependencias(deseados(man), man)
    hay = {n for n, _, es_fichero in ptero.listar("/mods")
           if es_fichero and n.endswith(".jar")}

    faltan = {j: e for j, e in quiero.items() if j not in hay}
    sobran = [j for j in sorted(hay)
              if j not in quiero and not j.startswith(INTOCABLES)]

    print(f"servidor: {len(hay)} jars · deseados de terceros: {len(quiero)}\n")
    for jar, e in sorted(faltan.items()):
        mid = next((m for m in QUEREMOS if jar.lower().startswith(m.lower())), None)
        motivo = QUEREMOS[mid][:52] if mid else "(dependencia)"
        print(f"  FALTA   {jar:<44} {e['size']//1024:>5} KB  {motivo}")
    for jar in sobran:
        print(f"  SOBRA   {jar:<44} (no esta en QUEREMOS)")
    if not faltan and not sobran:
        print("  nada que hacer")
        return
    if not args.aplicar:
        print("\n  Para aplicarlo:  python tools/mods_servidor.py --aplicar")
        return

    for jar, e in sorted(faltan.items()):
        # Se baja del MISMO sitio que el cliente y se comprueba la huella antes
        # de subir: si el CDN devolviera algo raro, mejor enterarse aqui.
        datos = urllib.request.urlopen(
            urllib.request.Request(e["url"], headers=UA)).read()
        if hashlib.sha1(datos).hexdigest() != e["sha1"]:
            raise SystemExit(f"{jar}: la huella no cuadra. NO se sube.")
        tam = ptero.subir("/mods", jar, datos)
        if tam != len(datos):
            raise SystemExit(f"{jar}: subida corrupta ({tam} vs {len(datos)} B). "
                             f"NO reinicies el servidor.")
        print(f"  subido  {jar}  ({tam} B, verificado)")

    if sobran:
        ptero.borrar("/mods", sobran)
        print(f"  borrados {len(sobran)}")

    if args.reiniciar:
        ptero.comando("say Reiniciando para cargar los mods. Vuelvo en 30 s.")
        ptero.potencia("restart")
        print("\n  Reiniciando.")
    else:
        print("\n  Los mods cargan AL ARRANCAR. Anade --reiniciar cuando puedas.")


if __name__ == "__main__":
    main()
