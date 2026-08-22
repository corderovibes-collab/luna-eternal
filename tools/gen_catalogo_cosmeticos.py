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
           / "luna" / "cosmetics" / "Catalogo.java")

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


def leer(zip_path: Path) -> list:
    """Saca (especie, aspecto, objeto) de los `cosmetic_items` del pack."""
    z = zipfile.ZipFile(zip_path)
    filas = []
    for nombre in z.namelist():
        if "/cosmetic_items/" not in nombre or not nombre.endswith(".json"):
            continue
        datos = json.loads(z.read(nombre))
        for especie in datos.get("pokemon", []):
            # ⚠ Algunas especies vienen con calificador de forma:
            #   "articuno galarian=false". Lo que identifica a la especie es lo
            #   de antes del espacio; el resto es una condicion de forma que el
            #   propio Cobblemon aplica.
            especie = especie.split()[0].strip().lower()
            if not especie:
                continue
            for ci in datos.get("cosmeticItems", []):
                aspectos = ci.get("aspects", [])
                objeto = ci.get("consumedItem", "")
                if len(aspectos) != 1 or not objeto:
                    continue          # ver la cabecera: variantes, fuera
                filas.append((especie, aspectos[0], objeto))
    # Sin duplicados y en orden estable, para que el fichero generado no cambie
    # de un dia para otro sin motivo.
    filas = sorted(set(filas))

    # ⚠ DOS COSMETICOS DE LA MISMA ESPECIE CON EL MISMO OBJETO SERIAN AMBIGUOS.
    #
    #   Se aplican dando el objeto al Pokemon, asi que el par (especie, objeto)
    #   es lo que identifica al cosmetico DE VERDAD -- el identificador que
    #   inventamos aqui no lo sabe Cobblemon. Si hubiera dos, comprar uno
    #   aplicaria el otro la mitad de las veces, y la pantalla diria que llevas
    #   puesto algo distinto de lo que pagaste.
    #
    #   Hoy no hay ninguno (comprobado sobre las 62 piezas). Esto esta aqui para
    #   el dia que el pack añada uno, porque el sintoma seria "a veces sale el
    #   cosmetico equivocado" y nadie lo relacionaria con esto.
    vistos = {}
    choques = []
    for especie, aspecto, objeto in filas:
        clave = (especie, objeto)
        if clave in vistos:
            choques.append("%s + %s -> %s y %s"
                           % (especie, objeto, vistos[clave], aspecto))
        vistos[clave] = aspecto
    if choques:
        raise SystemExit(
            "AMBIGUEDAD: hay cosmeticos que comparten especie y objeto, "
            "asi que no se pueden distinguir al aplicarlos:\n  "
            + "\n  ".join(choques)
            + "\n\nHay que elegir cual se vende, o dejar de identificarlos "
              "por el objeto.")

    return filas


PLANTILLA = '''package net.pokereport.luna.cosmetics;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Que cosmeticos existen y cuanto valen. <b>Vive en el servidor y solo ahi.</b>
 *
 * <p>⚠⚠ ESTE FICHERO SE GENERA. No se edita a mano:
 *
 * <pre>
 * python tools/gen_catalogo_cosmeticos.py &lt;zip de CobblemonMoreCosmetics&gt;
 * </pre>
 *
 * <p>Se genera <b>leyendo el pack instalado</b> porque escribirlo a mano ya
 * fallo: el catalogo prometia disfraces que el pack no tenia, se cobraban, y
 * salia el Pokemon normal. Sin error y sin nada en el log. Generandolo, el
 * catalogo no puede prometer lo que no existe.
 *
 * <p>Con {@code D-039} —los cosmeticos solo se consiguen comprandolos o en
 * eventos— este catalogo es la unica fuente que hay: un identificador que no
 * este aqui se rechaza al comprar.
 *
 * <h2>Como se aplica</h2>
 *
 * Cobblemon 1.7 los aplica por OBJETO, no por bandera: se le da a un Pokemon el
 * {@code objeto} y el motor le pone el aspecto. Por eso cada pieza lo lleva.
 *
 * <h2>⚠ LOS PRECIOS SON PROVISIONALES</h2>
 *
 * CLAUDE.md lo dice de toda la economia: se calibra con datos reales. Los tramos
 * --legendario, inicial, normal-- solo evitan que todo cueste lo mismo, que es
 * lo unico que se sabe seguro que estaria mal.
 */
public final class Catalogo {

    private Catalogo() {
    }

    /**
     * Un cosmetico del catalogo.
     *
     * @param especie identificador completo, {@code cobblemon:charizard}
     * @param aspecto el aspecto que aplica, {@code knight}
     * @param objeto  el objeto que Cobblemon consume para aplicarlo
     * @param precio  en LunaCoins. <b>{@code 0} = NO esta a la venta</b>, solo
     *                sale en eventos (D-039), que no es lo mismo que gratis
     */
    public record Pieza(String id, String categoria, String especie,
                        String aspecto, String objeto, int precio) {

        /** Criatura de Minecraft en vez de Pokemon: la dibuja otro codigo. */
        public boolean esDeMinecraft() {
            return especie.startsWith("minecraft:");
        }
    }

    public static final String MASCOTAS = "mascotas";
    public static final String CAPAS = "capas";
    public static final String SOMBREROS = "sombreros";
    public static final String AURAS = "auras";

    /** GENERADO. Ver la cabecera de la clase. */
    private static final List<Pieza> PIEZAS = List.of(
%(piezas)s
    );

    private static final Map<String, Pieza> POR_ID =
            PIEZAS.stream().collect(Collectors.toMap(Pieza::id, Function.identity()));

    public static List<Pieza> todas() {
        return PIEZAS;
    }

    /** {@code null} si no existe. Quien compre un identificador desconocido se queda sin nada. */
    public static Pieza de(String id) {
        return POR_ID.get(id);
    }

    /** Las categorias, en el orden en que salen las pestañas. */
    public static List<String> categorias() {
        return List.of(MASCOTAS, CAPAS, SOMBREROS, AURAS);
    }
}
'''


def main() -> None:
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    zip_path = Path(sys.argv[1])
    if not zip_path.exists():
        raise SystemExit("no existe %s" % zip_path)

    filas = leer(zip_path)
    if not filas:
        raise SystemExit(
            "El zip no trae ningun `cosmetic_items`. O no es el pack, o cambio "
            "de formato otra vez -- que ya paso una: el repositorio usa "
            "`species_features` y la version publicada `cosmetic_items`.")

    lineas = []
    for especie, aspecto, objeto in filas:
        ident = "%s_%s" % (especie, aspecto)
        lineas.append(
            '            new Pieza("%s", MASCOTAS, "cobblemon:%s", "%s", "%s", %d)'
            % (ident, especie, aspecto, objeto, precio(especie)))

    DESTINO.write_text(
        PLANTILLA % {"piezas": ",\n".join(lineas)}, encoding="utf-8")

    print("%d cosmeticos de %d especies" % (len(filas), len({f[0] for f in filas})))
    print("-> %s" % DESTINO)
    print("\nAVISO: los precios son por tramos y PROVISIONALES.")


if __name__ == "__main__":
    main()
