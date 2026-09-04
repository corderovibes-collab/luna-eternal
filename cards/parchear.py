#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
NUESTRA VERSION DE COBBLEMON CARDS: clona, parchea y compila.

    python cards/parchear.py            clona, parchea y compila
    python cards/parchear.py --solo-parchear    deja el fuente listo, sin compilar

⚠⚠⚠ POR QUE HAY UN FORK Y NO USAMOS EL JAR PUBLICADO.

   El release 1.0.4 de Modrinth (13-jul-2026, 17.303 descargas) **NO TIENE
   `enableCardStats`**: se comprobo con `javap` sobre el jar bajado del CDN, y
   su clase de configuracion tiene NUEVE campos frente a los cuarenta y cuatro
   del codigo actual. O sea que el interruptor que apaga lo unico que aqui es
   inadmisible --que una carta de pago de daño, armadura, vida y hasta x100 de
   apariciones-- **no existe en la version publicada**.

   Y su `BinderSpawnModifier` de 1.0.4 es el que TRANSFORMA ENTIDADES YA
   GENERADAS, que es exactamente lo que su propio CHANGELOG describe como
   «rompia el equilibrio del juego» y por lo que lo reescribieron despues.

   Asi que la eleccion real no era «probado contra sin publicar», sino
   «sin interruptor y con el mecanismo malo» contra «con interruptor y con el
   bueno». Se eligio lo segundo, y queda escrito aqui con el dato delante.

⚠⚠ LA CONTRAPARTIDA, Y ES REAL: se compila codigo SIN PUBLICAR. El commit va
   FIJADO abajo, asi que no se mueve solo; pero nadie lo ha probado en
   produccion mas que nosotros. Si algo se comporta raro, el primer sospechoso
   es este fichero.

⚠ La licencia lo permite sin discusion: CC0-1.0 (dominio publico), declarada en
  su LICENSE y en su fabric.mod.json.
"""

from __future__ import annotations

import io
import json
import shutil
import subprocess
import re
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent
REPO = "https://github.com/Howlite-UI/CobblemonCards.git"

# ⚠⚠⚠ FIJADO A UN COMMIT, NO A UNA RAMA. El repositorio NO TIENE NI UNA
#    ETIQUETA --se comprobo con `git tag -l`, que sale vacio-- asi que «la
#    version» no se puede pedir por nombre. Con `main` a secas, dos
#    compilaciones separadas por un dia darian jars distintos y nadie se
#    enteraria hasta que algo fallara en el servidor.
COMMIT = "c02aafb50d5915b83dafa9af28ddebd58be24b5e"   # 2026-08-15
NOMBRE = "cobblemon-cards-1.0.5-luna1.jar"

TRABAJO = RAIZ / "upstream"


def sh(*args, cwd=None, env=None):
    subprocess.run(args, cwd=cwd, check=True, env=env)


def clonar():
    if (TRABAJO / ".git").exists():
        sh("git", "-C", str(TRABAJO), "fetch", "--depth", "50", "origin", COMMIT)
    else:
        TRABAJO.mkdir(parents=True, exist_ok=True)
        sh("git", "-C", str(TRABAJO), "init", "-q")
        sh("git", "-C", str(TRABAJO), "config", "core.longpaths", "true")
        sh("git", "-C", str(TRABAJO), "remote", "add", "origin", REPO)
        sh("git", "-C", str(TRABAJO), "fetch", "--depth", "50", "origin", COMMIT)
    # ⚠ `--force` porque los parches de la vez anterior siguen en el arbol.
    sh("git", "-C", str(TRABAJO), "checkout", "--force", COMMIT)


def una(rel: str, viejo: str, nuevo: str, veces: int = 1):
    p = TRABAJO / rel
    s = io.open(p, encoding="utf-8").read()
    n = s.count(viejo)
    if n == 0 and nuevo in s:
        print("    (ya estaba)", rel)
        return
    assert n == veces, "%s: encaja %d veces, esperaba %d" % (rel, n, veces)
    io.open(p, "w", encoding="utf-8").write(s.replace(viejo, nuevo))
    print("    ok", rel.split("/")[-1])



CARD_MIRROR_JAVA = 'package com.howlite.cobblemoncards.pokereport;\n\nimport com.howlite.cobblemoncards.component.CardData;\nimport net.minecraft.core.component.DataComponents;\nimport net.minecraft.world.item.ItemStack;\nimport net.minecraft.world.item.component.CustomData;\n\n/**\n * PokeReport: espeja (especie, rareza, nota) en custom_data de vainilla.\n *\n * Es lo que permite que un mod SIN compilar contra este sepa que Pokemon y\n * que rareza tiene una carta -- para la habilidad de aparicion de\n * net.pokereport.luna.cards. Ver la cabecera de parchear.py.\n */\npublic final class CardMirror {\n    private CardMirror() {}\n\n    public static void mirror(ItemStack stack, CardData data) {\n        if (data == null) {\n            return;\n        }\n        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {\n            tag.putString("luna_especie", data.pokemonId());\n            tag.putString("luna_rareza", data.rarity());\n            tag.putInt("luna_nota", data.grade());\n        });\n    }\n}\n'


HABILIDAD_JAVA = 'package com.howlite.cobblemoncards.pokereport;\n\nimport java.util.Locale;\nimport java.util.Map;\n\n/**\n * PokeReport: EL MISMO calculo que net.pokereport.luna.cards.HabilidadService\n * (mod/), duplicado aqui porque este proyecto y ese son dos Gradle distintos\n * y no comparten clases -- solo asi el tooltip de una carta puede ensenar de\n * verdad lo que la habilidad va a dar, en vez del "+0.0%" muerto que dejaba\n * el sistema de stats de fabrica (desactivado por CobblemonCardsConfig).\n *\n * ⚠⚠⚠ SI SE CAMBIA UN NUMERO AQUI, HAY QUE CAMBIARLO EN LOS DOS SITIOS. Es\n *    el mismo tipo de invariante que ya vigila este proyecto en otros pares\n *    (el payload del escaparate, publicarObjeto/entrega): dos copias de la\n *    misma verdad que no da ningun error si dejan de coincidir, solo un\n *    numero que miente.\n */\npublic final class Habilidad {\n    private Habilidad() {}\n\n    private static final Map<String, Float> TECHO = Map.of(\n            "common", 1.30f,\n            "uncommon", 1.50f,\n            "rare", 1.80f,\n            "epic", 2.10f,\n            "legendary", 2.40f,\n            "mythic", 2.70f);\n\n    public static float multiplicador(String rareza, int nota) {\n        float techo = TECHO.getOrDefault(\n                rareza == null ? "" : rareza.toLowerCase(Locale.ROOT), 1.0f);\n        float suelo = 1.0f + (techo - 1.0f) * 0.4f;\n        float n = Math.max(0, Math.min(10, nota)) / 10.0f;\n        return suelo + (techo - suelo) * n;\n    }\n\n    /** "+42.0%" a partir del multiplicador. */\n    public static String texto(String rareza, int nota) {\n        float pct = (multiplicador(rareza, nota) - 1.0f) * 100f;\n        return String.format(Locale.ROOT, "+%.1f%%", pct);\n    }\n}\n'

def parchear():
    C = "common/src/main/java/com/howlite/cobblemoncards/CobblemonCardsConfig.java"
    B = ("common/src/main/java/com/howlite/cobblemoncards/item/custom/loot/"
         "BoosterLootTable.java")
    E = "common/src/main/java/com/howlite/cobblemoncards/event/ModEvents.java"

    # ---- 1. EL TOPE DE GENERACION ----------------------------------------
    #
    # ⚠⚠⚠ Sin esto, un servidor que se anuncia Kanto + Johto (D-017) reparte
    #    cartas de Miraidon. `BoosterLootTable` monta su lista con
    #    `PokemonSpecies.getImplemented()` --las 1.025-- y nuestro datapack de
    #    generaciones solo apaga POOLS DE APARICION: no marca especies. No hay
    #    ningun ajuste suyo que lo exprese, y no da ningun error.
    #
    # Va como CONFIG y no a fuego: el dia que se abra Gen 3 es cambiar un
    # numero en config/cobblemon-cards.json, no recompilar.
    una(C, """    @Entry
    public static boolean allowFakemonCards = false;""",
"""    @Entry
    public static boolean allowFakemonCards = false;

    /**
     * PokeReport: highest National Pokedex number that can appear on a card.
     *
     * Species above this are excluded from booster packs, from capture/faint
     * drops and from the emergency fallback list. Defaults to 1025, i.e. the
     * mod behaves exactly as upstream unless a server lowers it.
     */
    @Entry(min = 1, max = 1025)
    public static int maxNationalDex = 1025;""")

    una(B, """                        .filter(species -> {
                            if (CobblemonCardsConfig.allowFakemonCards) return true;""",
"""                        // PokeReport: generation cap. See CobblemonCardsConfig.
                        .filter(species -> species.getNationalPokedexNumber()
                                <= CobblemonCardsConfig.maxNationalDex)
                        .filter(species -> {
                            if (CobblemonCardsConfig.allowFakemonCards) return true;""")

    # ⚠⚠ LA LISTA DE EMERGENCIA LLEVABA rayquaza (384) Y greninja (658), y se
    #    usa cuando el registro de especies todavia no esta listo -- o sea EN
    #    EL ARRANQUE, que es cuando nadie mira. Un tope que no la cubriera
    #    seria un tope con una puerta abierta.
    una(B, """                    return List.of("pikachu", "charizard", "mewtwo", "lucario", "greninja", "gengar", "eevee", "bulbasaur", "squirtle", "rayquaza");""",
"""                    // PokeReport: upstream shipped rayquaza (384) and greninja
                    // (658) here. This runs during startup, when nobody is
                    // watching, so a capped server would leak them silently.
                    return List.of("pikachu", "charizard", "mewtwo", "typhlosion", "gengar", "eevee", "bulbasaur", "squirtle", "lugia", "tyranitar");""")

    una(E, """    private static void handlePokemonDrop(ServerPlayer player, Pokemon pokemon) {""",
"""    private static void handlePokemonDrop(ServerPlayer player, Pokemon pokemon) {
        // PokeReport: same cap as the booster tables. Without it, catching a
        // Gen 5 imported by another mod still drops its card.
        if (pokemon.getSpecies().getNationalPokedexNumber()
                > CobblemonCardsConfig.maxNationalDex) {
            return;
        }""")

    # ---- 2. LOS COMANDOS SUBEN A NIVEL 4 ---------------------------------
    #
    # ⚠⚠ Nuestros constructores son OP nivel 2 por D-028 (Axiom y WorldEdit).
    #    A nivel 2, cualquiera de ellos podia acuñar una carta Mitica de nota
    #    10. Mientras una carta no valga dinero da igual; en cuanto valga, es
    #    una imprenta.
    for f in ("GiveCardCommand", "CustomBoosterCommand"):
        una("common/src/main/java/com/howlite/cobblemoncards/command/%s.java" % f,
            ".requires(source -> source.hasPermission(2))",
            ".requires(source -> source.hasPermission(4))")

    # ---- 3. EL LOG DEJA DE GRITAR ----------------------------------------
    #
    # ⚠ Tres lineas `info` por CADA captura y por CADA derrota, incluida
    #   "Drop failed." el 99 % de las veces. En un servidor con combates eso
    #   es ruido constante en el log que se usa para diagnosticar.
    for viejo in ('CobblemonCards.LOGGER.info("Calculated drop chance for player {}: {} (Base: {}, Bonus: {})", ',
                  'CobblemonCards.LOGGER.info("Drop successful! Generating card for {}", pokemon.getSpecies().getName());',
                  'CobblemonCards.LOGGER.info("Drop failed.");'):
        una(E, viejo, viejo.replace("LOGGER.info", "LOGGER.debug"))

    # ---- 3-bis. EL SUELO DEL LOADER --------------------------------------
    #
    # ⚠⚠⚠ ESTO TIRO EL SERVIDOR, Y EL FALLO SOLO APARECE AL REINICIAR.
    #
    #    Su `fabric.mod.json` pide `fabricloader >=0.18.6` y NUESTRO SERVIDOR
    #    CORRE 0.18.4 (los clientes van en 0.19.5; el desfasado es el
    #    servidor). Resultado, ya en vivo:
    #
    #      Incompatible mods found!
    #      Mod 'Cobblemon Cards' requires version 0.18.6 or later of mod
    #      'Fabric Loader', but only the wrong version is present: 0.18.4!
    #
    #    Es la familia de `letmedespawn`/`almanac`: una dependencia que nadie
    #    ve hasta que el servidor no arranca.
    #
    # ⚠⚠ Y ESE NUMERO NO ES UN REQUISITO, ES UN ARTEFACTO DEL BUILD: sale de
    #    `loader_version` en su gradle.properties --el loader contra el que
    #    ELLOS desarrollan-- y se sustituye en la plantilla. Se comprobo antes
    #    de bajarlo, no se supuso: en sus 129 clases el UNICO uso de la API del
    #    loader es
    #        FabricLoader.getInstance().isModLoaded("accessories")
    #    que existe desde 0.4. No hay nada de 0.18.6 que este mod pueda usar.
    #
    # ⚠ Se baja a 0.18.4 y no mas: es lo que corre el servidor hoy. Poner
    #   `*` taparia el dia que un mod pida de verdad un loader nuevo.
    una("fabric/src/main/resources/fabric.mod.json",
        '"fabricloader": ">=0.18.6"', '"fabricloader": ">=0.18.4"')

    # ---- 5. FUERA EL MERCADER ERRANTE -----------------------------------
    #
    # Decision del usuario (2026-09-02). El mod registraba SIETE ofertas de
    # carta por polvo en el mercader errante: cinco en el nivel 1 y dos en el 2.
    #
    # ⚠ No es que fuera peligroso --cobra en polvo, y el polvo sale de reciclar
    #   cartas, asi que era un SUMIDERO y no una fuente-- es que es un segundo
    #   comerciante que nuestra economia no ve, al lado de una Tienda que se
    #   recorto a NUEVE articulos a proposito.
    una("fabric/src/main/java/com/howlite/cobblemoncards/fabric/FabricCobblemonCards.java",
        """        // 4. Enregistrer les offres du marchand ambulant
        registerWanderingTraderOffers();""",
        """        // 4. PokeReport: el mercader errante NO vende cartas.
        //    Decision del usuario: un segundo comerciante que nuestra
        //    economia no ve, al lado de una Tienda recortada a nueve
        //    articulos a proposito.
        // registerWanderingTraderOffers();""")

    # ---- 6. FUERA LA RESTAURADORA ---------------------------------------
    #
    # Decision del usuario (2026-09-02).
    #
    # ⚠⚠ SE RETIRA HACIENDOLA INALCANZABLE, NO BORRANDO SU REGISTRO. Quitar un
    #    bloque del registro obliga a tocar su receta, su traduccion, su modelo,
    #    su tabla de botin y la pestaña del creativo, y cada cabo suelto es un
    #    error al cargar. Sin receta y fuera del creativo, un jugador no puede
    #    conseguirla -- que es lo que se pedia. Mismo criterio que los sobres
    #    gen3..gen9, que siguen registrados y no se reparten.
    #
    # ⚠ Y ademas sale mejor de diseño: con la nota valiendo algo, calificar pasa
    #   a ser UNA tirada definitiva en vez de «califica, restaura, restaura».
    (TRABAJO / "common/src/main/resources/data/cobblemon-cards/recipe"
             / "card_restorer.json").unlink(missing_ok=True)
    print("    ok receta de la restauradora borrada")
    una("common/src/main/java/com/howlite/cobblemoncards/item/ModCreativeTabs.java",
        "                    output.accept(ModBlocks.CARD_RESTORER);",
        """                    // PokeReport: retirada (decision del usuario).
                    // output.accept(ModBlocks.CARD_RESTORER);""")

    # ---- 7. LAS TRES CALIDADES DE SOBRE ----------------------------------
    #
    # Decision del usuario (2026-09-03):
    #
    #   diario (gratis)   NUNCA da rara o mejor. Poco comun hacia abajo.
    #   plata             NUNCA da rara o mejor. Poco comun hacia abajo.
    #   dorado            puede salir cualquier cosa, y es el UNICO con boleto
    #
    # ⚠⚠⚠ HOY LOS TRES DAN LO MISMO, y eso es lo que hay que romper. Todo sobre
    #    reparte 3 comunes + 1 poco comun + **1 RARA O MEJOR GARANTIZADA**, asi
    #    que pagar LunaCoins no compra absolutamente nada distinto de lo que te
    #    dan gratis una vez al dia.
    #
    # ⚠⚠ ES UN TOPE DURO, NO UNA PROBABILIDAD BAJA, y por eso NO va a config.
    #    «Lo gratis nunca da raras» es un invariante del diseño: en cuanto es un
    #    numero editable, alguien lo pone al 50 y la regla se cae sin que salte
    #    nada. Lo unico que queda en config es la tasa del boleto, que si es un
    #    numero de ajuste.
    #
    # ⚠⚠ LA CALIDAD VIAJA EN `minecraft:custom_data`, NO EN UN OBJETO NUEVO.
    #    Tres sobres distintos serian tres entradas mas en un registro QUE SE
    #    SINCRONIZA --o sea tres razones mas para echar a quien no actualice--
    #    ademas de tres texturas, tres modelos y tres traducciones.
    #    `custom_data` es de vainilla: existe siempre y no registra nada.
    #    Lo pone `CartasService` al entregar el sobre.
    una(C, """    @Entry(min = 1, max = 1025)
    public static int maxNationalDex = 1025;""",
"""    @Entry(min = 1, max = 1025)
    public static int maxNationalDex = 1025;

    /**
     * PokeReport: God Pack chance (%) for the PREMIUM pack only.
     *
     * Upstream rolled godPackTicketChance on every pack regardless of where it
     * came from, so a free daily pack could produce one. Here only the pack
     * bought with premium currency can, and the free and silver tiers are
     * hard-coded to zero -- see BoosterPackItem.
     */
    @Entry(min = 0.0f, max = 100.0f)
    public static float lunaGoldGodPackChance = 2.0f;""")

    B2 = "common/src/main/java/com/howlite/cobblemoncards/item/custom/BoosterPackItem.java"
    una(B2, """                boolean naturalGodPack = level.random.nextFloat() * 100f <= CobblemonCardsConfig.godPackTicketChance;""",
"""                // PokeReport: la calidad la pone CartasService en custom_data.
                // Sin marca -> "dorado", que es el comportamiento de siempre:
                // un sobre que llegue por otra via no se degrada en silencio.
                String lunaCalidad = "dorado";
                var lunaDatos = itemStack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                if (lunaDatos != null && lunaDatos.copyTag().contains("luna_calidad")) {
                    lunaCalidad = lunaDatos.copyTag().getString("luna_calidad");
                }
                boolean lunaEsDorado = "dorado".equals(lunaCalidad);
                // ⚠⚠ EL BOLETO DIVINO SOLO EN EL DORADO. Antes se tiraba en
                //    todos los sobres con la misma probabilidad global, o sea
                //    que el sobre gratis podia dar un God Pack.
                boolean naturalGodPack = lunaEsDorado
                        && level.random.nextFloat() * 100f
                                <= CobblemonCardsConfig.lunaGoldGodPackChance;""")

    una(B2, """                    ItemStack rare = BoosterLootTable.getRandomRewardAbove("rare", this.boosterType);
                    serverRewards.add(rare.copy());
                    displayRewards.add(rare.copy());""",
"""                    // ⚠⚠⚠ LA QUINTA CARTA ES TODA LA DIFERENCIA ENTRE LAS TRES
                    //    ZONAS, y con eso basta:
                    //      dorado   rara garantizada, y dentro 80/15/5 entre
                    //               rara, epica y legendaria
                    //      los otros dos  poco comun, SIEMPRE. Tope duro.
                    //
                    //    No es una probabilidad baja: es que lo gratis NO PUEDE
                    //    dar una rara. Un numero editable aqui seria la regla
                    //    cayendose el dia que alguien lo suba sin darse cuenta.
                    ItemStack rare = lunaEsDorado
                            ? BoosterLootTable.getRandomRewardAbove("rare", this.boosterType)
                            : BoosterLootTable.getRandomReward("uncommon", this.boosterType);
                    serverRewards.add(rare.copy());
                    displayRewards.add(rare.copy());""")

    # ---- 8. ESPEJAR (especie, rareza, nota) EN custom_data ---------------
    #
    # Decision del usuario (2026-09-03): las cartas van a tener una HABILIDAD
    # de aparicion, y esa habilidad la aplica NUESTRO mod, no el suyo.
    #
    # ⚠⚠⚠ SIN COMPILAR CONTRA SU JAR, LEER `CardData` DESDE FUERA NO ES TRIVIAL.
    #    `cobblemon-cards:card_data` es un DataComponentType SUYO, y nuestro
    #    mod deliberadamente no tiene una dependencia de compilacion sobre este
    #    (ver la cabecera de este fichero): asi funciona con el mod instalado y
    #    sin el. Sin esa dependencia, leer un componente MODDED ajeno exige
    #    reflexion o descifrar su codec generico -- fragil y caro.
    #
    #    La salida es la MISMA que ya se uso para el sobre: espejar lo que
    #    hace falta en `minecraft:custom_data`, que es de VAINILLA y CUALQUIER
    #    mod lo lee sin compilar contra nada. Se hace UNA vez, en la creacion
    #    de la carta, y de ahi en adelante viaja con el objeto.
    #
    # ⚠⚠ SE ENGANCHA CON UN AYUDANTE Y UN REGEX, NO TOCANDO CADA SITIO A MANO.
    #    Hay DIEZ lugares en su codigo que escriben CARD_DATA -- calificar,
    #    restaurar (retirada, pero el codigo sigue ahi), dar por comando,
    #    escanear, abrir un sobre, el sobre personalizado. Corregir cada uno a
    #    mano son diez una() fragiles ante el primer cambio de variable. Un
    #    ayudante estatico + una sustitucion que empareja el patron ENTERO
    #    (X.set(ModDataComponents.CARD_DATA, Y);) cubre los diez de una vez, y
    #    sigue cubriendolos si el commit fijado cambia de linea.
    ayudante = TRABAJO / ("common/src/main/java/com/howlite/cobblemoncards"
                          "/pokereport/CardMirror.java")
    ayudante.parent.mkdir(parents=True, exist_ok=True)
    ayudante.write_text(CARD_MIRROR_JAVA, encoding="utf-8")
    print("    ok CardMirror.java creado")

    patron = re.compile(
        r"(\w+)\.set\(ModDataComponents\.CARD_DATA,\s*(\w+)\);")
    total = 0
    for ruta in TRABAJO.rglob("*.java"):
        if ruta == ayudante:
            continue
        texto = ruta.read_text(encoding="utf-8")

        def sub(m):
            nonlocal total
            total += 1
            return (m.group(0) + "\n" + " " * 8
                    + "com.howlite.cobblemoncards.pokereport.CardMirror.mirror("
                    + m.group(1) + ", " + m.group(2) + ");")

        nuevo_texto = patron.sub(sub, texto)
        if nuevo_texto != texto:
            ruta.write_text(nuevo_texto, encoding="utf-8")
    print("    ok %d sitios espejados (de 10 esperados; el 11.o -- el objeto"
          " de muestra del creativo -- no sigue el patron y se deja)" % total)

    # ---- 9. FUERA EL ROLL VIEJO DE ESTADISTICA -----------------------------
    #
    # Decision del usuario (2026-09-03): las cartas ya no llevan una
    # estadistica aleatoria por TIPO (el sistema de fabrica, apagado desde
    # enableCardStats=false pero que seguia SORTEANDOSE y guardandose en cada
    # carta). Se sustituye por CARD_DROP_CHANCE fijo -- un valor neutro que
    # nunca se muestra ni hace nada -- para que no quede ni el dato muerto.
    CARD_STAT_FIJO = "CardStat.CARD_DROP_CHANCE"
    una("common/src/main/java/com/howlite/cobblemoncards/event/ModEvents.java",
        "CardStat randomStat = CardStat.values()[RANDOM.nextInt(CardStat.values().length)];",
        "CardStat randomStat = " + CARD_STAT_FIJO + ";  // PokeReport: sin roll")
    una(B,
        "        CardStat stat = CardStat.values()[RANDOM.nextInt(CardStat.values().length)];",
        "        CardStat stat = " + CARD_STAT_FIJO + ";  // PokeReport: sin roll")
    una("common/src/main/java/com/howlite/cobblemoncards/item/ModCreativeTabs.java",
        "                                CardStat.values()[RANDOM.nextInt(CardStat.values().length)],",
        "                                " + CARD_STAT_FIJO + ",  // PokeReport: sin roll")

    # ---- 10. LA APARICION DE VERDAD, EN VEZ DEL "+0.0%" MUERTO -------------
    #
    # PPP EL "+0.0%" NO ERA UN FALLO DE VISUALIZACION: ERA LA VERDAD DE UN
    #    SISTEMA APAGADO. `formatValue` llama a `getEffectiveValue`, que
    #    multiplica por `getStatMultiplier`, que da CERO en cuanto
    #    enableCardStats=false. El texto decia la verdad -- de un sistema que
    #    ya no usamos. Lo que hacia falta no era arreglarlo, era QUITARLO y
    #    poner en su sitio lo que la carta VA A DAR de verdad: la habilidad
    #    de aparicion de net.pokereport.luna.cards.HabilidadService.
    ayudante_hab = TRABAJO / ("common/src/main/java/com/howlite/cobblemoncards"
                              "/pokereport/Habilidad.java")
    ayudante_hab.parent.mkdir(parents=True, exist_ok=True)
    ayudante_hab.write_text(HABILIDAD_JAVA, encoding="utf-8")
    print("    ok Habilidad.java creado")

    # El tooltip (mayus + raton encima de la carta).
    una("common/src/main/java/com/howlite/cobblemoncards/item/custom/CardItem.java",
        """                    // 1. Statistique (mise en avant avec couleur de rareté)
                    // Le format dépend du mode d'application du stat (valeur plate vs pourcentage).
                    String formattedValue = com.howlite.cobblemoncards.CobblemonCardsConfig.displayPercentStatOnCards
                            ? com.howlite.cobblemoncards.util.CardStatUtil.formatValue(data.stat(), data.statValue())
                            : String.valueOf(data.statValue());

                    // Séparateur décoratif supérieur
                    tooltipComponents.add(Component.literal("─────────────────").withStyle(ChatFormatting.DARK_GRAY));

                    tooltipComponents.add(
                        Component.literal("  " + formattedValue + " ")
                            .withStyle(Style.EMPTY.withColor(rarityColor).withBold(true))
                            .append(data.stat().getTranslatedName()
                                .copy().withStyle(Style.EMPTY.withColor(rarityColor).withBold(false)))
                    );""",
        """                    // PokeReport: la habilidad de aparicion de verdad, no el
                    // "+0.0%" del sistema de stats por tipo (apagado).
                    String formattedValue = com.howlite.cobblemoncards.pokereport.Habilidad
                            .texto(data.rarity(), data.grade());

                    tooltipComponents.add(Component.literal("─────────────────").withStyle(ChatFormatting.DARK_GRAY));

                    tooltipComponents.add(
                        Component.literal("  " + formattedValue + " ")
                            .withStyle(Style.EMPTY.withColor(rarityColor).withBold(true))
                            .append(Component.literal("de aparición (5 min, 1 h de espera)")
                                .withStyle(Style.EMPTY.withColor(rarityColor).withBold(false)))
                    );""")

    # El texto flotante al abrir el sobre (BoosterPackScreen).
    una("common/src/client/java/com/howlite/cobblemoncards/screen/BoosterPackScreen.java",
        """                // Dessiner le bonus si data présent
                if (data != null) {
                    String percent = com.howlite.cobblemoncards.util.CardStatUtil.formatValue(data.stat(), data.statValue());
                    Component bonusText = Component.literal(percent + " ").append(data.stat().getTranslatedName()).withStyle(ChatFormatting.GREEN);
                    graphics.drawCenteredString(this.font, bonusText, xCenter, (int)(currentCardY + 82), statColor);
                }""",
        """                // PokeReport: la habilidad de aparicion de verdad.
                if (data != null) {
                    String percent = com.howlite.cobblemoncards.pokereport.Habilidad
                            .texto(data.rarity(), data.grade());
                    Component bonusText = Component.literal(percent + " de aparición")
                            .withStyle(ChatFormatting.GREEN);
                    graphics.drawCenteredString(this.font, bonusText, xCenter, (int)(currentCardY + 82), statColor);
                }""")

    # ---- 4. EL ESPAÑOL ---------------------------------------------------
    #
    # ⚠ El jar trae en_us y fr_fr. El nuestro se mantiene AQUI, en
    #   `cards/lang/es_es.json`, y se copia al compilar: asi vive en nuestro
    #   repositorio y sobrevive a cualquier reclonado del suyo.
    destino = (TRABAJO / "common/src/main/resources/assets/cobblemon-cards/lang")
    nuestro = RAIZ / "lang" / "es_es.json"
    ingles = json.load(io.open(destino / "en_us.json", encoding="utf-8"))
    espanol = json.load(io.open(nuestro, encoding="utf-8"))
    # ⚠⚠ UNA CLAVE QUE YA NO EXISTE NO DA ERROR: se queda de peso muerto y
    #    tapa que la de verdad se quedo sin traducir. Y una que falte deja al
    #    jugador viendo ingles a medias. Se avisa de las dos.
    sobran = [k for k in espanol if k not in ingles]
    faltan = [k for k in ingles if k not in espanol]
    if sobran:
        print("    ⚠ %d claves de es_es.json ya no existen en en_us: %s"
              % (len(sobran), ", ".join(sobran[:5])))
    if faltan:
        print("    ⚠ %d claves SIN TRADUCIR (saldran en ingles): %s"
              % (len(faltan), ", ".join(faltan[:5])))
    shutil.copy2(nuestro, destino / "es_es.json")
    print("    ok es_es.json  (%d de %d claves)" % (len(espanol), len(ingles)))


def compilar():
    import os
    env = dict(os.environ)
    # El mismo JDK 21 que el mod. `tools/jdk21.sh` es de bash; aqui se busca
    # igual pero sin depender de el, para poder llamarse desde Windows.
    for base in (RAIZ.parent / ".toolchain" / "jdk21",
                 Path("C:/Program Files/Eclipse Adoptium")):
        if not base.exists():
            continue
        cands = [base] if (base / "bin").exists() else sorted(base.glob("jdk-21*"))
        if cands:
            env["JAVA_HOME"] = str(cands[-1])
            break
    if "JAVA_HOME" not in env:
        raise SystemExit("No encuentro un JDK 21. winget install EclipseAdoptium.Temurin.21.JDK")
    print("  JDK: %s" % env["JAVA_HOME"])
    sh(str(TRABAJO / "gradlew.bat"), ":fabric:build", "-x", "test",
       "--console=plain", cwd=str(TRABAJO), env=env)

    salida = TRABAJO / "fabric" / "build" / "libs"
    jars = [j for j in salida.glob("*.jar") if not j.stem.endswith("-sources")]
    if not jars:
        raise SystemExit("El build termino sin dejar jar en %s" % salida)
    destino = RAIZ / "build" / "libs"
    destino.mkdir(parents=True, exist_ok=True)
    for viejo in destino.glob("cobblemon-cards-*.jar"):
        viejo.unlink()
    shutil.copy2(jars[0], destino / NOMBRE)
    print("  -> %s  (%.1f MB)"
          % (destino / NOMBRE, (destino / NOMBRE).stat().st_size / 1048576))


def main():
    print("COBBLEMON CARDS — nuestra version")
    print("  commit fijado %s" % COMMIT[:10])
    clonar()
    print("  parches:")
    parchear()
    if "--solo-parchear" in sys.argv:
        print("  (sin compilar, por --solo-parchear)")
        return 0
    compilar()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
