"""Genera `Catalogo.java` LEYENDO EL PACK INSTALADO.

    python tools/gen_catalogo_cosmeticos.py <ruta al zip de CobblemonMoreCosmetics>

⚠⚠ POR QUE EXISTE ESTO, Y NO ES COMODIDAD

El catalogo estuvo escrito a mano, con los identificadores copiados de mirar el
repositorio de GitHub. Fallo, y de la peor manera posible:

  - El repositorio (HEAD) declara los cosmeticos como `species_features`.
  - La version PUBLICADA los declara como `cosmetic_items`, que es el sistema
    nativo de Cobblemon 1.7 y funciona distinto: se aplica dando un OBJETO al
    Pokemon, no encendiendo una bandera.
  - Y encima el pack no estaba instalado.

Resultado: la tienda vendia disfraces que no existian. Se compraban, se cobraban,
y salia el Pokemon normal. Sin error, sin aviso, sin nada en el log. El aviso de
que esto podia pasar estaba ESCRITO en el propio `Catalogo.java` -- y aun asi
paso, porque una advertencia en un comentario no comprueba nada.

Generandolo del zip, el catalogo no puede prometer algo que el pack no tenga:
si un cosmetico no esta en el pack, no llega al fichero.

⚠ SE OMITEN LOS COSMETICOS DE VARIOS ASPECTOS a la vez. Algunos objetos aplican
  `["icedragon", "altcolor"]`: son variantes de color de otro cosmetico, y
  venderlas como piezas sueltas confundiria dos cosas distintas en la misma
  rejilla. Cuando haya sitio para variantes, se recuperan de aqui.
"""

import json
import re
import sys
import zipfile
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
DESTINO = (RAIZ / "mod" / "src" / "main" / "java" / "net" / "pokereport"
           / "luna" / "cosmetics" / "CatalogoMascotas.java")

# Precios PROVISIONALES por tramos. CLAUDE.md dice que la economia se calibra con
# datos reales; esto solo reparte para que la tienda no tenga todo al mismo
# precio, que es lo unico que se sabe seguro que estaria mal.
TRAMOS = {
    "legendario": 4000,
    "inicial": 2500,
    "normal": 1500,
}
LEGENDARIOS = {
    "articuno", "zapdos", "moltres", "mewtwo", "mew", "raikou", "entei",
    "suicune", "lugia", "ho_oh", "ho-oh", "celebi", "rayquaza", "kyogre",
    "groudon", "dialga", "palkia", "giratina", "arceus", "darkrai",
}
INICIALES = {
    "charizard", "blastoise", "venusaur", "typhlosion", "feraligatr",
    "meganium", "blaziken", "swampert", "sceptile", "infernape", "empoleon",
    "torterra", "decidueye", "incineroar", "primarina", "cinderace",
    "rillaboom", "inteleon", "greninja", "ceruledge",
}


def precio(especie: str) -> int:
    if especie in LEGENDARIOS:
        return TRAMOS["legendario"]
    if especie in INICIALES:
        return TRAMOS["inicial"]
    return TRAMOS["normal"]


RESOLVERS = "cosmetic/morecosmetics"


def leer(zip_path: Path):
    """Saca (especie, aspecto) de los RESOLVERS, que es lo que se puede DIBUJAR.

    Devuelve `(filas, declarados_sin_arte)`.

    ⚠⚠ ANTES SE LEIA DE `cosmetic_items`, Y ESTABA MAL POR DOS SITIOS A LA VEZ:

      1) `26sinnohbundle` declara SEIS cosmeticos --charizard, decidueye,
         garchomp, gardevoir, greninja y lucario con el aspecto `sinnoh`-- cuyo
         arte NO viene en el pack: es un paquete que se vende aparte. La tienda
         los ofrecia, se cobraban, y salia el Pokemon NORMAL.

      2) `pangoro_operator.json` pone `"pokemon": ["operator"]`, que es una
         ERRATA suya: deberia decir `pangoro`. Ese cosmetico salia como una
         celda en blanco, porque `cobblemon:operator` no existe.

    Los resolvers no tienen ninguno de los dos problemas, porque un resolver ES
    el dibujo: si esta, se puede pintar, y lleva la especie de verdad. Los seis
    del `sinnohbundle` no tienen resolver, y el de `operator` dice `pangoro`.

    ⚠ SOLO LA CARPETA RAIZ. `dex/` y `msd/` son variantes para formas concretas
      --megas, sobre todo-- del MISMO cosmetico. Contarlas daria tres Charizard
      `knight` distintos en la rejilla.

    ⚠ SE EXIGE `model`. Una variacion sin modelo es un recoloreado (shiny,
      hembra) que hereda el del cosmetico base, no un cosmetico aparte.
      Comprobado: exigirlo no pierde ninguno --los 55 que tienen un solo aspecto
      lo tienen--, asi que el filtro solo quita duplicados.
    """
    z = zipfile.ZipFile(zip_path)

    # ⚠⚠ HAY QUE COMPROBAR EL POSER, Y NO ES PARANOIA: FALTA UNO.
    #
    #   `pangoro_operator` tiene su modelo y su textura, pero el resolver pide
    #   `poser: cobblemon:pangoro_operator` y ESE FICHERO NO VIENE EN EL PACK.
    #   Cobblemon no cae al poser base de la especie: dibuja un bulto verde sin
    #   forma. El usuario lo vio antes que ninguna comprobacion nuestra --otra
    #   vez--, y por eso ahora se mira.
    #
    #   Es la tercera vuelta de la misma leccion. Existir un resolver no basta:
    #   tienen que existir LAS TRES PIEZAS que nombra.
    posers = {n.rsplit("/", 1)[1][:-5] for n in z.namelist()
              if "/posers/" in n and n.endswith(".json")}

    filas = set()
    sin_poser = []
    for nombre in z.namelist():
        if "/resolvers/" not in nombre or not nombre.endswith(".json"):
            continue
        if nombre.split("/resolvers/")[1].rsplit("/", 1)[0] != RESOLVERS:
            continue
        datos = json.loads(z.read(nombre))
        especie = datos.get("species", "").split(":")[-1].strip().lower()
        if not especie:
            continue
        for var in datos.get("variations", []):
            aspectos = var.get("aspects", [])
            # Un solo aspecto: los de varios son `["magma", "female"]` y demas,
            # o sea el mismo cosmetico sobre otra forma.
            if len(aspectos) != 1 or not var.get("model"):
                continue
            # El poser puede ser de Cobblemon (`cobblemon:pangoro`) o del pack.
            # Solo se rechaza si el pack lo nombra dentro de SU espacio de
            # cosmeticos y no lo trae: un poser del juego base siempre existe.
            poser = (var.get("poser") or "").split(":")[-1]
            if poser and poser not in posers and poser.endswith(aspectos[0]):
                sin_poser.append((especie, aspectos[0]))
                continue
            filas.add((especie, aspectos[0]))

    # Lo que el pack DECLARA pero no puede dibujar. No se usa para nada mas que
    # para avisar: si un dia el numero cambia, es que el pack ha cambiado y hay
    # que mirarlo, en vez de enterarnos porque un jugador compra un disfraz
    # invisible.
    declarados = set()
    for nombre in z.namelist():
        if "/cosmetic_items/" not in nombre or not nombre.endswith(".json"):
            continue
        datos = json.loads(z.read(nombre))
        for especie in datos.get("pokemon", []):
            # ⚠ Algunas vienen con calificador de forma:
            #   "articuno galarian=false". La especie es lo de antes del espacio.
            especie = especie.split()[0].strip().lower()
            for ci in datos.get("cosmeticItems", []):
                if len(ci.get("aspects", [])) == 1 and especie:
                    declarados.add((especie, ci["aspects"][0]))

    # Los que se caen por falta de poser se suman al aviso: son igual de
    # invisibles para el jugador que los que no tienen arte ninguna.
    return sorted(filas), sorted((declarados - filas) | set(sin_poser))


PLANTILLA = '''package net.pokereport.luna.cosmetics;

import java.util.List;

import net.pokereport.luna.cosmetics.Catalogo.Pieza;

/**
 * Los disfraces de Pokemon. <b>ESTE FICHERO SE GENERA:</b>
 *
 * <pre>
 * python tools/gen_catalogo_cosmeticos.py &lt;zip de CobblemonMoreCosmetics&gt;
 * </pre>
 *
 * <p>Se genera <b>leyendo los RESOLVERS del pack</b>, que es lo unico que dice
 * la verdad sobre lo que se puede dibujar. Escribirlo a mano fallo tres veces
 * seguidas, y las tres con el mismo sintoma --se cobraba y salia el Pokemon
 * normal-- por tres causas distintas:
 *
 * <ol>
 *   <li>Se copio del repositorio (HEAD), que declara los cosmeticos como
 *       {@code species_features}; la version publicada usa {@code cosmetic_items}.</li>
 *   <li>{@code 26sinnohbundle} declara seis cosmeticos cuyo arte se vende aparte,
 *       y {@code pangoro_operator.json} pone {@code "pokemon": ["operator"]},
 *       que es una errata suya.</li>
 *   <li>{@code pangoro_operator} tenia resolver, modelo y textura pero <b>no
 *       poser</b>, y Cobblemon no cae al de la especie: dibuja un bulto.</li>
 * </ol>
 *
 * <p>Hoy se exigen <b>las cuatro piezas</b>. Lo que el pack declara y no puede
 * dibujar se imprime al generar, en vez de acabar en la tienda.
 *
 * <p>⚠ Los precios son por tramos y <b>PROVISIONALES</b>: CLAUDE.md dice que
 * toda la economia se calibra con datos reales.
 */
public final class CatalogoMascotas {

    private CatalogoMascotas() {
    }

    static final List<Pieza> PIEZAS = List.of(
%(piezas)s
    );
}
'''


def main() -> None:
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    zip_path = Path(sys.argv[1])
    if not zip_path.exists():
        raise SystemExit("no existe %s" % zip_path)

    filas, sin_arte = leer(zip_path)
    if not filas:
        raise SystemExit(
            "El zip no trae ningun resolver bajo %s. O no es el pack, o cambio "
            "de estructura -- que ya paso una vez: el repositorio declara los "
            "cosmeticos como `species_features` y la version publicada usa "
            "`cosmetic_items`." % RESOLVERS)

    lineas = []
    for especie, aspecto in filas:
        ident = "%s_%s" % (especie, aspecto)
        lineas.append(
            '            new Pieza("%s", Catalogo.MASCOTAS, "cobblemon:%s", "%s", %d)'
            % (ident, especie, aspecto, precio(especie)))

    DESTINO.write_text(
        PLANTILLA % {"piezas": ",\n".join(lineas)}, encoding="utf-8")

    print("%d cosmeticos de %d especies" % (len(filas), len({f[0] for f in filas})))
    print("-> %s" % DESTINO)

    if sin_arte:
        print("\nFUERA (%d): el pack los declara pero NO trae su arte." % len(sin_arte))
        print("Se venderian y saldria el Pokemon normal, que es lo que ya paso.")
        for especie, aspecto in sin_arte:
            print("   %-16s %s" % (especie, aspecto))

    print("\nAVISO: los precios son por tramos y PROVISIONALES.")


if __name__ == "__main__":
    main()
