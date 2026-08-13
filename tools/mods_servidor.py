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
}

# Lo que NO se toca aunque no este en QUEREMOS: es nuestro o es infraestructura.
# Sin esta lista, `--aplicar` borraria el mod del servidor entero.
INTOCABLES = ("lunaeternal", "lunaneon", "fabric-api", "Cobblemon",
              "EasyAuth", "worldedit", "Axiom")


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
        ids.add(meta.get("id", ""))
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
