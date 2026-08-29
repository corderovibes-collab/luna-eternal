# CLAUDE.md — PokeReport: Luna Eternal

> Documento maestro. **Se lee antes de cualquier trabajo.** Si una decisión
> arquitectónica cambia, se actualiza aquí antes de cerrar la sesión.

**Última actualización:** 2026-08-27
**Fase actual:** PHASE 2 — Core progression · PHASE 7 — Mundo (ciudadela) ·
PHASE 4 — Gimnasios (arrancando)
**Estado:** PHASE 0 y PHASE 1 completadas. 28 documentos, decisiones D-001 a
D-040. **El mod está desplegado y funcionando contra MariaDB:** economía de
tres monedas, ocho vías de progresión, y **ocho pantallas** en el PokePad.
Autotest **423/423** en vivo. **El recorrido del jugador nuevo está completo.**

> **2026-08-27 — RANGOS, MOCHILA, MUNDOS Y ESCALADO. Y tres lecciones que se
> repiten.**
>
> ⚠⚠⚠ **LA INTERFAZ NO CRECÍA CON LA PANTALLA, Y EL CÓDIGO ESTABA COPIADO ONCE
> VECES.** `Math.min(1.0, cabe)`: el chasis **nunca ampliaba**, así que cuanto
> más grande el monitor, más pequeño se veía — **36 % del ancho en un 4K frente
> al 72 % de 1080p**. Hoy crece a **medios pasos** (1 · 1,5 · 2 · 2,5), con lo
> que 1080p **no cambia nada** y 4K pasa al 90 %.
> **Y el arreglo de verdad es que ahora hay un solo sitio**: `recalcular()`
> estaba copiado en once pantallas —«copia literal de CosmeticosScreen», decían
> todas— y medido ese día ya había **seis variantes distintas**.
> ⚠⚠ **Y al extraerlo casi deshago una lección ya pagada**: puse un margen del
> 2 %, y `PokePadScreen` decía con todas las letras que eso ya se probó y se
> retiró porque **encoger es lo que emborrona**. La versión buena estaba en
> **una** de las once; ahora la tienen todas.
>
> ⚠⚠ **UN REGISTRO SINCRONIZADO NO DEGRADA: ECHA.** La mochila añadió un tipo de
> contenedor, y al reiniciar **nadie pudo entrar** hasta reabrir el launcher —
> cero «joined the game» durante seis minutos. Todo lo anterior eran paquetes y
> datos: un cliente viejo pierde funciones pero **entra**. **Hay que avisar
> ANTES de reiniciar, no después.**
>
> ⚠⚠ **Y `setInvulnerable` NO PROTEGE DEL CREATIVO.** El propio Minecraft lo
> dice: la invulnerabilidad no aplica `if (fuente.isSourceCreativePlayer())`. Y
> en este servidor **todos los que colocan decoración son operadores en
> creativo**, o sea el único caso que la bandera no cubre: el fallo parecía «no
> funciona» cuando era «funciona para todos menos para ti».

> **2026-08-25 — LOS OBJETOS PASAN A ESCAPARATE, Y ES UNA RECTIFICACIÓN.**
> D-041 dijo que los objetos van por **libro de órdenes** porque son fungibles.
> Sigue siendo cierto en teoría, y **le faltaba un dato: cuánta gente hay**. Un
> libro necesita las dos caras pobladas para cruzar; con doce personas pones una
> orden de compra y **se queda ahí para siempre**. El usuario lo dijo usándolo:
> *«se pierde uno comprando allí»*, *«opciones duplicadas, botones duplicados»*.
>
> ⚠⚠ **Y los botones duplicados no eran descuido: los pedía el diseño.** La
> pantalla que un libro necesita tiene **dos entradas para todo** — pestañas
> LIBRO / MIS ÓRDENES / HISTORIAL para mirar, y campos PRECIO / CANTIDAD con
> COMPRAR / VENDER para actuar. Un escaparate tiene una: publicas, o compras.
>
> **D-042: las dos mitades del mercado se comportan igual.** Mismo servicio
> (`GtsService`), mismo protocolo, misma pantalla. Quien sepa vender un Pokémon
> sabe vender una pila de piedras. `MarketService` **no se borra**: sigue
> escrito y probado, y vuelve el día que haya gente para que un libro cruce.
> Autotest 319 → **345**.
>
> ⚠⚠⚠ **Y LA MAQUETA SE GANÓ EL SUELDO: CUATRO FALLOS, NINGUNO DABA ERROR.**
> `tools/gen_maqueta_mercado.py` dibuja la pantalla **sobre el chasis real** con
> **las anchuras reales de la fuente del juego** y avisa de desbordes y solapes:
>
> 1. **`filasCaben()` era una fórmula a mano** que ya no cuadraba con
>    `listaY()`. La lista había bajado y la fórmula seguía contando desde donde
>    estaba antes: salían **cinco filas donde caben cuatro**, y la quinta se
>    dibujaba **encima de la paginación**. Es la foto que mandó el usuario.
> 2. La columna **EXPIRA salía marcada** nada más abrir — su orden descendente
>    *era* el orden por defecto. Y en oro sobre naranja, que no se lee.
> 3. **Los nombres de Minecraft son largos.** «Escaleras de ladrillos de piedra»
>    mide 364 px y su columna tiene 240: se metía **encima del vendedor**. Un
>    Pokémon no tiene este problema («Charizard» cabe siempre), así que no se
>    heredaba del GTS.
> 4. **«VENDEDOR / UNIDAD» no cabía con su flecha**: 164 px en 150.
>
> ⚠ **Ninguno se habría visto revisando el código**: los cuatro son *números que
> dejaron de cuadrar*. Es la misma familia que la rejilla del PokePad — «cuadraba
> por casualidad hasta que una medida cambió».
>
> ⚠⚠ **El invariante nuevo es EL PAYLOAD.** `publicarObjeto` escribe
> «identificador + separador + cantidad» y la entrega lo vuelve a leer: dos
> sitios con su propia idea del formato. Si dejaran de estar de acuerdo, **la
> compra no daría ningún error** — el dinero cambiaría de manos y los objetos no
> aparecerían. Es el único fallo de esta mitad que se come mercancía en
> silencio, así que se comprueba de punta a punta.

> **2026-08-23 (tarde) — CLANES Y TIENDA. Ya se puede comprar una Poké Ball.**
> Dos sistemas más y el PokePad pasa de 5 pantallas a **7**. Los **clanes** son
> el primer sistema social del proyecto (D-040): fundar por 5.000 de Plata, 30
> miembros, 3 roles y un tesoro común, con la etiqueta visible **en el chat, el
> tablist y encima de la cabeza**. La **tienda** llevaba la lógica escrita desde
> PHASE 3 y solo le faltaba la pantalla. Autotest 156 → **217**.
>
> ⚠⚠ **La lección de los clanes: EL ESTADO NO ES DE QUIEN LO MIRA.** Todo lo
> anterior —cosméticos, misiones, oficios— era información de un jugador sobre
> sí mismo. Un clan lo comparten treinta personas: si alguien echa a un miembro
> y solo se refresca a sí mismo, **los demás siguen viendo al echado y el echado
> se cree dentro**. Por eso se reenvía a todo el clan. Y por eso «un jugador, un
> clan» **no lo dice una columna sino la CLAVE PRIMARIA**: así falla en la base
> venga de donde venga la petición.
>
> ⚠⚠ **Y el autotest se ganó el sueldo en su primera ejecución en vivo.** Siete
> fallos que eran **uno con seis consecuencias**: `cambiarRol(..., LIDER)`
> delegaba en `traspasar`, o sea que «cambiar el rango de alguien» era en
> realidad **«regalarle el clan»**. Ninguna revisión de código lo habría visto,
> porque delegar *era seguro*: transacción correcta, sin dos líderes, sin
> excepción, sin traza. Lo que había era **un método que hacía algo mucho más
> grande de lo que promete su nombre**.
>
> ⚠ **La tienda acabó con NUEVE artículos, y ese número es la decisión.** El
> usuario la recortó dos veces —«solo Cobblemon» y luego «solo lo básico»— y
> pasó de 28 a 90 a 9. **Una tienda completa vacía el mundo**: si todo se
> compra, explorar solo sirve para conseguir dinero. Los precios quedan
> **provisionales a propósito**, en cinco escalones, hasta el análisis general
> de economía. Detalle en `docs/economy/tienda.md` y `docs/social/clanes.md`.

> **2026-08-23 — EL RECORRIDO DEL JUGADOR NUEVO ESTA CERRADO, POR FIN.** La
> pantalla que faltaba era **la del inicial**, y lo que le faltaba a ella no era
> lógica: `StarterService` llevaba meses escrito y probado, pero **`conceder()`
> no lo llamaba nadie** desde que D-026 borró la interfaz vieja. El PokePad pasa
> de 2 pantallas a 5 —**Trabajos**, **Misiones** e **Inicial** son nuevas— y con
> ellas entran los **OFICIOS** (minar, pescar y cosechar dan Plata) y un **árbol
> de 28 misiones en 6 cadenas**. Autotest 136 → **156**.
>
> ⚠⚠ **Y la lección del día, que salió CUATRO veces con cuatro caras distintas:**
> *un lado cambia algo y el otro no se entera.* Los cosméticos no volvían al
> reconectar (el servidor empujó antes de que el cliente estuviera listo); la
> pantalla del inicial dejaba **atrapado** al jugador (esperaba una confirmación
> que ya había pasado, porque `conceder` es asíncrono y **parece síncrono**); el
> comando de reinicio «no servía» (borraba la fila y el cliente seguía con su
> copia); y reiniciar el inicial dejaba la misión puesta (son **dos tablas**).
> **La regla que queda: si el servidor cambia un estado que el cliente dibuja, el
> servidor lo reenvía.** Y si es el cliente quien sabe cuándo está listo, que
> pregunte él.

> **2026-08-22 — Kanto y Johto, de verdad y en las dos direcciones.** Las
> **256 voces** de la Pokédex están completas (`docs/pokemon/voces-pokedex.md`)
> y el límite de generaciones **por fin cierra**: se descubrió que llevaba seis
> días dejando pasar **29 especies de Gen 3-8** —los spawns los metían mods, no
> Cobblemon— y que **la Pokédex no estaba limitada en absoluto**. Las dos cosas
> arregladas y documentadas en `docs/pokemon/generations.md` §3-ter y §4.

> **2026-08-13 — el día que el servidor pasó a tener cara propia.** El pack ya
> no es una lista de siete mods elegidos a ojo: **parte del modpack oficial de
> Cobblemon** (D-031). Encima van 96 bloques de neón propios (D-029), shaders
> por su canal oficial (D-030) y **323 texturas de interfaz revestidas de azul
> luna**. Lo que sigue faltando es lo mismo de siempre: **la pantalla desde la
> que un jugador nuevo empieza** (`ART-002`). Ver §0-bis.

> **2026-08-12 — la interfaz del PokePad se ha retirado ENTERA** (D-026). No es
> una regresión accidental: se borró a propósito para rehacerla con arte real.
> **Y con ella se han ido los menús de cofre**, también por decisión suya: no
> se quiere ninguna pantalla de Minecraft, ni siquiera provisional. Ver §0-bis.

---

## 0. POR DÓNDE SEGUIR

> Lee esto primero al retomar. Lo demás es contexto.

### Lo que funciona ahora mismo (verificado 2026-08-11)

```
Servidor dev  7dc30799 · s12.mia.us.tarohosting.lat:33043
              MC 1.21.1 Fabric · whitelist ON · TheJuanCE op nivel 4
Cobblemon     1.7.3 instalado · Done (7,2 s) · 4,34 GiB de 8 GB
Mod           lunaeternal 0.1.0 · migraciones V001 a V009 aplicadas
              compila contra la API de Cobblemon 1.7.3
BD            MariaDB s11945_luna · 3 monedas · 5 vías
Autotest      /luna autotest -> 423 + los de gimnasios (2026-08-29)
              +los de MEDALLAS Y RECEPCIONES, y los que importan son:
              que el bit de cada medalla sea SU SALA (si no, ganar a Brock
              enciende la de Misty sin dar ningun error), que la entrada y la
              tarima caigan DENTRO de su ranura (fuera, el jugador de la ranura
              1 aparece en la sala del de la 2), que ningun gimnasio pida mas
              medallas de las que hay (seria INALCANZABLE y se quedaria gris
              para siempre) y que una tarea programada que falla NO cancele a la
              siguiente -- si no, la vuelta de uno se la lleva la de otro y el
              jugador se queda encerrado en la arena
              +8 de Viajes, y la que importa es LA REJILLA: 4x2 son
              OCHO huecos, asi que una NOVENA parada seria
              INALCANZABLE sin dar ningun error
              +26 del escaparate de objetos, y el que importa es el
              PAYLOAD: lo escribe `publicarObjeto` y lo lee la entrega,
              y si dejaran de estar de acuerdo la compra NO DARIA
              NINGUN ERROR -- el dinero cambia de manos y los objetos
              no aparecen
              eran 156 antes de clanes: +51, y la mayoria son de LO QUE
              NO SE PUEDE HACER (ver el bloque Clanes)
              ⚠⚠ Y EN SU PRIMERA EJECUCION EN VIVO CAZO UNO. Fueron 7
                 fallos y era UNO con seis consecuencias:
                 cambiarRol(lider, otro, LIDER) DELEGABA en `traspasar`,
                 asi que "cambiar el rango de alguien" era en realidad
                 "REGALARLE EL CLAN"
                 ⚠ NINGUNA REVISION DE CODIGO LO HABRIA VISTO, porque
                   delegar era SEGURO: traspasar es transaccional y baja
                   al anterior, asi que no quedan dos lideres, no hay
                   excepcion y no hay traza. Lo que habia era un metodo
                   que hacia algo MUCHO MAS GRANDE que su nombre
                 hoy se RECHAZA y remite a `traspasar`
              eran 136 el 22-ago: +8 de cosmeticos, +6 del arbol de
              misiones, +5 de oficios y +1 del prefijo de sombreros
              eran 112 el 12-ago; las nuevas cubren las voces
              y antes 125: los 13 de fondos se fueron con el resource pack
Voces         256 VOCES DE LA POKEDEX · KANTO Y JOHTO COMPLETOS
              (2026-08-22) · docs/pokemon/voces-pokedex.md
              151 de Kanto + 100 de Johto + 5 formas de Alola
              "Pokédex: 256 voces listas" en el log del arranque
              python tools/gen_voces.py --origen "RUTA"
              ⚠⚠ REHACE EL CATALOGO ENTERO EN CADA EJECUCION. Hay que
                 pasarle TODAS las generaciones o borra las que falten
              ⚠ LAS 5 FORMAS DE ALOLA SE CONSERVAN A MANO: su carpeta de
                origen ya no existe en Descargas. Copiar sus .ogg fuera,
                regenerar, y devolverlos + reañadirlos a sounds.json y
                voces.txt. Detalle en el doc §3.3
              ⚠ EL DESFASE DE KANTO, que costo entender: en la grabacion
                se salto Victreebel (71) pero la numeracion siguio
                corrida, asi que `71.MP3` era TENTACOOL (72) y asi hasta
                `150.MP3` = Mew (151). JOHTO NO LO LLEVA: empieza en 152
                --el 151 no existe-- y el hueco ES la realineacion
                NO se dedujo, se VERIFICO escuchando 152.MP3 (Chikorita).
                Equivocarse era asignar mal 100 voces y que nada lo
                detectara: cada Pokemon describiendo al siguiente
              ⚠ los .ogg salen SIEMPRE como modificados en git: un flujo
                Ogg lleva un numero de serie aleatorio, asi que
                reconvertir nunca da los mismos bytes. Es esperado
              ⚠ hace falta ffmpeg (Minecraft solo reproduce OGG Vorbis)
              el jar paso de 24 MB a 41: es lo que mas pesa del mod
Telemetría    /luna economia · informe automático al log cada hora
              tablist con rangos (la barra lateral se borro, D-026)
Pack base     COBBLEVERSE 1.7.42 (D-037, revoca D-031) — ORDEN DEL
              USUARIO, dada despues de leerle las licencias:
                cobbleverse         modpack  All Rights Reserved
                cobbleverse-badges  mod      CC-BY-NC-ND-4.0
              queda escrito porque D-006 lo descarto por eso mismo
              NO redistribuimos nada: el manifiesto guarda URL y hash,
              y cada mod se baja del CDN de Modrinth
              147 ficheros suyos + 8 nuestros · 434 MB (eran 185)
              FUERA: generacion de estructuras (legendary-monuments,
                     repurposed-structures-fabric, biome-replacer,
                     huge-structure-blocks) porque el usuario NO quiere
                     construcciones generadas
                     musica: 421 MB entre su banda sonora y tres packs
                     de radio, mas del doble de todo el pack de antes
                     los 15 packs que ellos marcan "z DO NOT ENABLE z"
              ⚠ EL SLUG NO ES EL NOMBRE DEL JAR. repurposed_structures
                es `repurposed-structures-fabric` en Modrinth, y sin el
                sufijo la exclusion NO SURTE EFECTO Y NO AVISA
              ⚠ SU CONFIGURACION SON 155 FICHEROS, y sueltos eran 155
                peticiones a raw = el 429 otra vez. Van en UN ZIP POR
                CARPETA desde la release, extraido SIN PISAR lo del
                jugador (`keepExisting`, nuevo en el launcher). Da la
                misma garantia que `once` fichero a fichero, y ademas
                un fichero de config NUEVO si llega
              ⚠⚠ `continuity:default` SE APAGA A PROPOSITO. Son texturas
                 conectadas y afectan a 42 bloques: los 16 cristales de
                 color, sus 16 paneles, cristal, tintado, libreria y la
                 familia de la arenisca. El usuario lo reporto en vivo
                 ("mi construccion cambio de textura"). Se queda su
                 `glass_pane_culling_fix`, que no repinta nada
              ⚠ `letmedespawn` ya no existe en Modrinth (404). Va en
                FIJADOS con la URL y el sha1 del pack de Cobblemon,
                comprobados contra el CDN
              ---- historico ---------------------------------------
              EL MODPACK OFICIAL DE COBBLEMON (D-031, revoca D-024)
              cobblemon-fabric 1.7.3 · 76 ficheros · MC 1.21.1
              la lista NO se mantiene a mano: gen_modpack.py baja su
              .mrpack, lee el indice y usa SUS versiones probadas
              encima van EXCLUIDOS (2) y EXTRA (6) nuestros
              FUERA: stendhal (CC-BY-NC-ND, choca con D-007) y
                     bisect-mod (publicidad de otro hosting)
              NO se copia su mundo tutorial: 97 MB de un jugador
              NO se copia su FancyMenu: lleva la marca de Cobblemon
              SUBIR = versiones que sustituimos a la suya, con motivo
              hoy solo fabric-api: Shine exige >=0.116.9 y ellos
              fijan 0.116.8, y el juego NO ARRANCABA
              ⚠ mezclar SUS versiones con mods nuestros a la ultima
                OBLIGA a comprobar el resultado. gen_modpack.py lee
                el fabric.mod.json de cada jar y ABORTA si algo no
                cuadra, en vez de publicar un pack que no arranca.
                Mira `provides` (alias) y los jars ANIDADOS (JiJ), y
                se queda con la version MAS ALTA de cada modulo, que
                es lo que hace Fabric. Sin esas tres cosas salen 19
                falsos positivos y la comprobacion no sirve
Mods          servidor 29 jars · cliente 155 · NO tienen que coincidir
              subieron de 14 a 29 con CobbleVerse: los de REGLAS DE
              JUEGO tienen que estar en el servidor o no hacen nada
                CobbleverseBadges · rctmod + rctapi · cobblemonraiddens
                mega_showdown · fightorflight · y sus dependencias
              ⚠⚠⚠ LOS BLOQUES VIAJAN COMO UN NUMERO, NO COMO UN NOMBRE.
                 El servidor dice "bloque 4721" y el cliente lo busca en
                 SU tabla. Las dos tablas se construyen registrando
                 mods, asi que un mod de bloques que este solo en el
                 cliente las descuadra y SE DIBUJA OTRA COSA
                 El usuario coloco un neon blanco y le salio
                 `lumymon:mesprit_altar`; preguntado el servidor por esa
                 coordenada, contesto AIRE. No era textura ni ID
                 duplicado: eran dos tablas distintas
                 medido: 5.687 bloques de desfase en 18 mods
                 con el pack de Cobblemon NO pasaba, y por eso el diseño
                 de mods_servidor.py aguanto tanto: alli los extras del
                 cliente eran HUD, mapas y tooltips, que no registran
                 nada. CobbleVerse trae DIECIOCHO mods de bloques
                 comprobacion: ningun mod con blockstates y
                 `environment != client` puede faltar en el servidor
              ⚠ VanillaBackport SE ESCAPO del primer analisis: registra
                sus 72 bloques como `minecraft:`, asi que buscando
                "blockstates de namespace propio" parecia inofensivo
              ⚠ `aporta()` MIRA DENTRO DE LOS JARS ANIDADOS (JiJ). Sin
                eso el resolutor abortaba por dependencias que viajan
                DENTRO del propio jar: `trinkets` lleva
                cardinal-components y `sophisticatedcore` lleva
                team_reborn_energy. Dos abortos por el mismo punto ciego
              ⚠⚠ UN MOD QUE REGISTRE ALGO QUE SE SINCRONIZA TIENE QUE
                 ESTAR EN LOS DOS LADOS, SEA O NO DEPENDENCIA DE NADIE.
                 `trinkets` + `accessories-compat-layer` estaban solo
                 en el cliente y NADIE ENTRABA AL SERVIDOR:
                   Failed to decode packet 'clientbound/custom_payload'
                   Caused by: StructFieldException: [exported_slots]
                 que no nombra ni Accessories ni Trinkets. La causa
                 sale del log del CLIENTE, no del servidor
                 `con_dependencias()` no lo caza y no puede: un PUENTE
                 no es dependencia de nadie, por definicion
                 se resolvio QUITANDOLOS DEL CLIENTE --se comprobo que
                 de `accessories-compat-layer` no depende ni un mod de
                 los 147-- en vez de subirlos al servidor
              ⚠ TRES DE ELLOS NO SE ELIGIERON, LOS PIDIO EL LOG:
                tmcraft, LumyMon y Only Bottle Caps. Sin ellos, 55
                tablas de botin de los entrenadores no parseaban
                ("Unknown registry key ... tmcraft:tm_bulkup") = 55
                entrenadores que no sueltan nada al ganarles
              RAM: 8 GB desde el 2026-08-22. Medido en el panel con el
                 servidor arriba: 4,34 GiB de 8, y 1h57m de actividad
                 sin caidas. Antes eran 4 GB y corria POR ENCIMA del
                 limite (4.447 MB), con swap=0 y el OOM-killer armado:
                 158 mods, Cobblemon y 1.714 entrenadores no caben en 4
                 ⚠ Que quepa NO es que sobre. Queda menos de la mitad
                   libre y el mundo esta vacio: no hay jugadores
                   cargando chunks, ni combates, ni entidades. El
                   margen de verdad se mide con gente dentro
              datapack: COBBLEVERSE-RCT-DP-v20 en world/datapacks
                154 entrenadores curados con dialogo. 1559 -> 1714
                se comprueba POR DENTRO que no lleva worldgen antes
                de subirlo; sus otros datapacks SI lo llevan mezclado
              ---- historico ---------------------------------------
              servidor 14 jars · cliente 79 · NO tienen que coincidir
              ⚠ EL SERVIDOR TIENE QUE SER SUBCONJUNTO DEL CLIENTE.
                Al reves echa a la gente con "Registry remapping
                failed". Por eso mods_servidor.py lee las versiones
                DEL MANIFIESTO: mismo fichero byte a byte
              de los 76 del pack, 34 se declaran de cliente y la
              mayoria del resto son interfaz: en el servidor solo
              gastarian RAM. Van los 5 que trabajan de verdad
              (lithium, ferritecore, krypton, clumps, letmedespawn)
              alta/baja: python tools/mods_servidor.py --aplicar
              tabla definitiva en docs/technical/client-pack.md §2-ter
Luz de color  ⚠ LA LUZ DE MINECRAFT NO TIENE COLOR. El motor guarda
              un numero 0-15 y ya: un neon cian ILUMINA EN BLANCO y
              eso NO se puede arreglar desde el mod, no hay API
              Shine (instalado, sin shaders) -> halo del color del
              bloque, solo en emisores: los 96 neones sin declarar
              Shaders (instalados) -> luz de color de verdad, tine
              el suelo. Complementary > Performance > COLORED_LIGHTING
              viene en 0. Hacen cosas distintas, no se estorban
              detalle en docs/world/neon.md §1
Cosmeticos    LA PRIMERA SUB-PANTALLA YA EXISTE Y FUNCIONA EN EL JUEGO
              (2026-08-22, verificado por el usuario)
              detalle completo en docs/ui/cosmeticos.md
              4 pestañas · rejilla 4x2 · previsualizador 3D · saldo
              62 disfraces de 54 especies (CobblemonMoreCosmetics, MIT)
              EL CATALOGO SE GENERA DEL ZIP, no se escribe:
                python tools/gen_catalogo_cosmeticos.py <zip>
              ⚠⚠⚠ Y EL DISFRAZ SE CRAFTEABA, que era peor todavia.
                 `cosmetic_items` aplica el disfraz DANDO UN OBJETO al
                 Pokemon, y el de `charizard_knight` es un
                 `minecraft:iron_helmet`:
                   craftear un yelmo   = disfraz de 2.500 LunaCoins gratis
                   quitarlo con su menu= y encima te quedas el objeto
                 NO era un fallo del codigo: `cosmetic_items` esta PENSADO
                 para conseguirse jugando, y D-039 dice lo contrario. Los
                 dos disenios no pueden convivir
                 NO SE VIGILA LA PUERTA, SE QUITA: el DATAPACK NO SE
                 INSTALA. Sin el, `cosmetic_items` no registra nada y el
                 objeto no aplica ni quita nada. El aspecto se fuerza
                 directo: pokemon.setForcedAspects(...)
                 verificado EN EL BYTECODE de 1.7.3, no en el repo:
                   updateAspects()  aspectos = proveedores + forcedAspects
                   PokemonP3        lo guarda (persiste)
                   ClientPokemonP3  lo sincroniza (lo ven los demas)
                 los assets siguen incrustados: el resolver se activa por
                 ASPECTO y vive en assets/, nunca dependio del datapack
                 ⚠ quien tuviera un disfraz puesto por la via vieja LO HA
                   PERDIDO. Se reequipa desde el PokePad, sin pagar
              ⚠⚠ LA TIENDA VENDIO 62 DISFRACES QUE NO EXISTIAN. Se
                 compraban, se cobraban, y salia el Pokemon NORMAL: sin
                 error, sin aviso, sin nada en el log. Tres fallos, y
                 cada uno bastaba solo para el mismo sintoma:
                   1) el pack NO ESTABA INSTALADO en ningun sitio
                   2) el catalogo se copio de GitHub (HEAD), que declara
                      `species_features` -- una BANDERA. La version
                      PUBLICADA usa `cosmetic_items`, el sistema nativo
                      de Cobblemon 1.7, que se aplica dando un OBJETO:
                      pokemon.swapCosmeticItem(objeto)
                   3) y sin los assets el cliente dibuja el de siempre
                 el aviso de que (2) podia pasar ESTABA ESCRITO en el
                 propio Catalogo.java. Un comentario no comprueba nada;
                 generarlo del zip si: no puede prometer lo que no hay
              ⚠ EL PAR (especie, objeto) ES LO QUE IDENTIFICA UN
                COSMETICO. `charizard_knight` no lo conoce Cobblemon: el
                aplica el objeto y saca el aspecto. Dos de la misma
                especie con el mismo objeto serian indistinguibles, y el
                sintoma seria "a veces sale el equivocado". El generador
                ABORTA si aparece uno
              SOLO LOS ASSETS, y en un solo sitio:
                cliente   lunaneon.jar!/resourcepacks/cosmeticos/
                          ALWAYS_ENABLED (DEFAULT_ENABLED no existe para
                          resource packs, lo dice el javadoc de Fabric)
                servidor  NADA. El datapack se retiro a proposito
                MIT, asi que redistribuirlo SI se puede (CobbleVerse no)
              PONER Y QUITAR, los dos desde el PokePad y solo desde ahi
                el boton de un cosmetico puesto decia EQUIPADO y no hacia
                nada. Un boton se etiqueta con la ACCION: dice QUITAR
                el estado va en la etiqueta de la izquierda: PUESTO en
                verde, en vez de TUYO en gris
                quitarlo NO lo devuelve al catalogo: sigue comprado
              ⚠⚠ Y NADIE HABIA VISTO QUE LA PAGINACION NO EXISTIA. El
                 campo `pagina` se usaba en los tres sitios que tocaba
                 --rejilla, modelos, clic-- pero NADA LO CAMBIABA NUNCA:
                 54 de los 62 cosmeticos eran INALCANZABLES, sin error
                 las flechas van en la banda naranja, y sus medidas salen
                 de RECORRER el PNG: banda y=698..745, adornos en
                 x=732..744, 763..774, 936..947, 966..978, huecos libres
                 437..731, 775..935 y 979..1273
              D-039: NO se consiguen jugando. Solo LunaCoins o eventos
              V011 aplicada · autotest 136/136 tras ella
              ⚠⚠ EL MOD TIENE DOS DESTINOS Y SE OLVIDA EL SEGUNDO:
                 servidor  python tools/desplegar.py mod --reiniciar
                 clientes  python tools/gen_manifest.py --publicar
                 El jar que baja el launcher NO sale del servidor: sale
                 del manifiesto. Subir solo al servidor deja a todo el
                 mundo con el jar viejo, y el sintoma es una pantalla
                 que "no abre" -- se comporta como debe, y eso despista
                 ⚠⚠⚠ Y CON UNA DIMENSION NUEVA NO ES UNA PANTALLA QUE NO
                    ABRE: ES QUE NADIE PUEDE ENTRAR (2026-08-29).
                    Un dimension_type se SINCRONIZA, y el cliente lo
                    valida contra SU COPIA LOCAL del JSON. Sin el jar
                    nuevo:
                      Errors in registry minecraft:dimension_type
                      Caused by: FileNotFoundException:
                        lunaeternal:dimension_type/gimnasios.json
                      -> "Error de protocolo de red"
                    ⚠⚠ Y POR ESO EL ORDEN IMPORTA: PUBLICAR EL
                       MANIFIESTO **ANTES** DE REINICIAR. Al reves hay
                       una ventana en la que el servidor ya tiene la
                       dimension y los clientes no, y en esa ventana no
                       entra nadie
              ⚠⚠⚠ EL TITILEO DE LOS MODELOS COSTO CUATRO INTENTOS.
                 `drawProfilePokemon` hace `rotation.conjugate()`, que
                 MUTA el cuaternion que se le pasa. Con una constante
                 compartida, 8 celdas lo invierten 8 veces (par, vuelve
                 al valor) y con el previsualizador son 9 (impar): el
                 modelo alterna orientacion cada fotograma
                 "solo titila al previsualizar" NO era una pista sobre
                 el previsualizador: era sobre la PARIDAD de llamadas
                 esta es la REGLA 6 de docs/ui/dibujado.md
              ⚠ vendor/cobblemon es HEAD, NO 1.7.3 (clon --depth 1).
                Para una firma concreta: javap sobre el jar instalado
              VERSE EN EL MUNDO SALE GRATIS, y no por casualidad:
              `cosmetic_items` guarda el aspecto EN EL POKEMON, no en
              una tabla nuestra, asi que al sacarlo lo dibuja Cobblemon
              y lo ven todos. Era el trabajo mas grande que quedaba y se
              resolvio dejando de inventar un sistema paralelo
              ⚠ SIN VERIFICAR EN EL JUEGO todavia
Inicial       LA PANTALLA QUE DESBLOQUEA EL PROYECTO (2026-08-23)
              6 iniciales (Kanto + Johto) en 3D . se abre SOLA al entrar
              ninguna cadena de misiones avanzaba sin esto
              ⚠⚠ EL BLOQUEO LLEVABA MESES ABIERTO Y NO ERA LOGICA.
                 feature-gap-analysis.md lo describia: un jugador nuevo
                 NO TENIA NINGUN POKEMON, y sin Pokemon no servia nada de
                 lo construido. StarterService estaba escrito y probado
                 DESDE EL PRINCIPIO --marca primero, entrega despues,
                 deshace si falla, da XP y avanza la mision-- y lo unico
                 que faltaba era QUIEN LLAMARA a conceder()
              ⚠ SE ABRE SOLA porque un icono mas no habria servido: QUIEN
                ACABA DE ENTRAR NO SABE QUE EL POKEPAD EXISTE
              ⚠ Y LO DECIDE EL SERVIDOR (kit_claim), no "?tengo algun
                Pokemon?" -- eso daria falso positivo con quien guarde su
                equipo en el PC
              ⚠⚠ SE ABRE DESDE EL TICK, NO AL RECIBIR EL PAQUETE. Al
                 llegar, el jugador esta en la pantalla de CARGA y
                 currentScreen == null es falso: la apertura se perdia
              ⚠⚠ Y DEJO ATRAPADO A UN JUGADOR. conceder() es ASINCRONO y
                 PARECE SINCRONO: encola y vuelve, asi que preguntar justo
                 despues leia el estado ANTERIOR. La pantalla se quedaba
                 en ENTREGANDO y no se puede cerrar sin elegir.
                 Hoy avisa al terminar EN LOS TRES CAMINOS, y ademas hay
                 SALIDA a los 6 s: UNA PANTALLA QUE NO SE PUEDE CERRAR
                 TIENE QUE TENER SIEMPRE UNA SALIDA
              /luna reiniciarinicial (nivel 4) para volver a verla
                borra la marca Y la mision: son DOS TABLAS distintas
                (kit_claim y quest_progress), y borrar una sola dejaba
                el estado A MEDIAS, que es peor que cualquiera de las dos

Misiones      EL ARBOL, CON SUS RAMAS Y SUS CANDADOS (2026-08-23)
              28 misiones . 6 cadenas . tutorial, entrenador,
              coleccionista, oficios, comercio, diaria
              ⚠ EL MODELO YA ERA UN ARBOL desde PHASE 5 y nadie lo habia
                visto: Quest tiene chain (pestania), requires (arista) y
                order. El tutorial ya branchea
              el reparto se CALCULA --la columna es la PROFUNDIDAD, no el
              campo order-- y el arbol SE ENCOGE SOLO si no cabe: con el
              nodo a 72 la cadena oficios pedia 754 px en 698, y la
              version anterior NO LO DETECTABA: dibujaba fuera del marco,
              que desde dentro se ve como "faltan misiones"
              ⚠ el nodo lleva ICONO segun el tipo de objetivo y el nombre
                DEBAJO: dentro solo cabia partido en dos lineas de letra
                diminuta, y eran cajas grises indistinguibles
              ⚠ 6 invariantes, y los 3 que importan cazan fallos
                INVISIBLES del JSON: un requires a algo que no existe, uno
                que cruza de cadena, y un CICLO. Ninguno da error al
                cargar, y un ciclo cuelga EL DIBUJADO
              /luna reiniciarmision <id> (nivel 4, autocompleta)
              ⚠ t5_gts SE DESBLOQUEO el 2026-08-25 con la pantalla del GTS.
                YA NO QUEDA NINGUNA MISION IMPOSIBLE: t4_tienda, m1_comprar y
                m2_vender se habian desbloqueado con la tienda (23-ago)

Oficios       MINAR, PESCAR Y COSECHAR DAN PLATA (2026-08-23, V012)
              5 Vias -> 8 . MINERO, PESCADOR, AGRICULTOR
              UNA VIA DESBLOQUEA CONTENIDO, UN OFICIO DA DINERO: por eso
              solo los oficios pagan. Si subir de Via tambien pagara, la
              progresion seria una fuente de ingresos (P3)
                nivel  50 . 150 . 400 . 1.000 . 2.500 de Plata
                los tres al maximo -> 100 LunaCoins, UNA VEZ
              ⚠ SE BAJO DIEZ VECES tras calibrar contra la tienda (de 8 a
                3.000): la escala vieja daba 41.000 por oficio, o sea
                TRECE Revivir. EL DINERO DE MINAR SON LAS MENAS, NO EL
                BONO -- un extra que supera a la actividad deja de serlo
              ⚠ CON 100 LUNACOINS NO SE COMPRA NINGUN COSMETICO (el mas
                barato son 1.200). No es un error, pero TRASLADA TODO EL
                PESO A LOS EVENTOS: D-039 decia que eran "la mitad" y con
                esta cifra son practicamente la unica via gratuita
              ⚠ LA MINERIA EXCLUYE EL CREATIVO: un constructor con Axiom
                haria de la ciudadela la mina mas rentable del servidor, y
                ahora mismo el trabajo del proyecto ES construirla
              ⚠ Y el evento es AFTER, no BEFORE: el "antes" se dispara
                aunque el bloque no llegue a romperse
              ⚠ el valor de un bloque se decide por ETIQUETAS, no por una
                lista: un mineral nuevo entra solo
              ⚠ COCINA NO ESTA, y es deliberado: Cobblemon 1.7 tiene olla
                (CookingPotMenu, CookingPotRecipe) pero NO PUBLICA NINGUN
                EVENTO --se revisaron sus 98-- y engancharla pide un mixin
                dentro de su codigo. Declarar el oficio sin enganche
                dejaria uno que NUNCA da XP
              ⚠ V012 hizo falta para TRES NOMBRES: player_path.path es un
                ENUM de MariaDB, no un VARCHAR. Insertar 'MINERO' en el
                enum viejo guarda la CADENA VACIA con un aviso que nadie
                mira. Y el ENUM guarda el INDICE: reordenar los cinco
                viejos convertiria a los Exploradores en Entrenadores
              /luna via <VIA> <xp> (nivel 3) para probar

Curar        LA ULTIMA PANTALLA BASICA (2026-08-23)
              equipo 2x3 con barras de vida . boton . reloj de 30 min
              GRATIS, y el cooldown es lo que lo hace sostenible
              SIN migracion y SIN protocolo nuevo: HealService, PedirCura,
              EstadoCura y Curar llevaban escritos desde el 23-ago por la
              mañana. Lo unico que faltaba era el ICONO y la pantalla
              ⚠ ES LA 16a CELDA y cae en la PAGINA 2. NO se quito ninguna:
                la rejilla ya paginaba y reordenaba (OrdenPad)
              ⚠ `haceFalta` LO DECIDE EL SERVIDOR, no las barras. El cliente
                podria deducirlo --si alguna barra no esta llena-- y entonces
                la regla viviria en dos sitios: bastaria con que el servidor
                contara tambien los PP para que el boton se apagara cuando SI
                tocaba curar
              ⚠ el boton se apaga mientras vuela el paquete, CON SALIDA a los
                1,5 s. Sin la salida, un paquete perdido deja el boton muerto
                y hay que reabrir -- la leccion de la pantalla del inicial
              ⚠ las barras van por TRAMOS (verde >50%, ambar >20%, rojo), no
                por degradado: lo que hay que saber de un vistazo es si ese
                Pokemon aguanta otro combate
              ⚠ los estados llegan en formato Showdown (brn, par, slp...) y se
                traducen EN EL CLIENTE: es presentacion, asi que enseñarlo en
                otro idioma no toca el protocolo
              ⚠ un equipo VACIO es un estado real (lo guardaste todo en el PC)
                y se dice, en vez de dejar la pantalla en blanco
              VERIFICADO EN EL JUEGO (2026-08-23) tras arreglar dos fallos
              que salieron al instalarlo. Los dos abajo, y los dos son de la
              misma familia: ALGO QUE CUADRABA POR CASUALIDAD

Mercado       DOS ESCAPARATES HERMANOS (2026-08-25, D-042)
              detalle y las fases en docs/trading/mercado.md
              POKEMON y OBJETOS . 345/345 . icono GTS
              publicas una oferta -> otro la ve -> la compra -> te llega
              ⚠⚠ ERA UN LIBRO DE ORDENES Y SE RETIRO. D-041 tenia razon en
                 teoria --los objetos SON fungibles-- y le faltaba un dato:
                 CUANTA GENTE HAY. Un libro necesita las dos caras pobladas
                 para cruzar, y con doce personas pones una orden de compra
                 y se queda ahi para siempre
                 ⚠ Y LOS BOTONES DUPLICADOS LOS PEDIA EL DISEÑO, no eran
                   descuido: la pantalla de un libro tiene DOS ENTRADAS PARA
                   TODO --pestañas para mirar, campos y botones para
                   actuar--. Un escaparate tiene una
                 MarketService NO SE BORRA: sigue escrito y probado, y vuelve
                 el dia que haya gente para que un libro cruce
              ⚠⚠ LAS DOS MITADES SE COMPORTAN IGUAL, y eso es la mitad del
                 arreglo: mismo servicio (GtsService), mismo protocolo, misma
                 disposicion. Quien sepa vender un Pokemon sabe vender una
                 pila de piedras
              ⚠⚠ DE DONDE SALE LO QUE SE VENDE (orden del usuario, 24-ago):
                 objetos  DEL INVENTARIO y solo de ahi. La BARRA RAPIDA cuenta
                          (son los 9 primeros huecos de `main`)
                 Pokemon  DEL EQUIPO O DEL PC, da igual: un Pokemon no ocupa
                          inventario, vive en un almacen del servidor
                 ⚠ la MANO SECUNDARIA hoy NO cuenta. No rompe la custodia
                   --contar y sacar miran el mismo sitio-- pero confunde.
                   Arreglarlo es sumarla en `cuantos` Y en `sacar`, LAS DOS
              ⚠⚠ LA CUSTODIA: publicar SACA los objetos del inventario ANTES
                 de crear la oferta. Si la oferta existiera y los objetos
                 siguieran encima, el escaparate venderia lo que su dueño
                 todavia tiene -- el vector de duplicacion numero uno
                 ⚠ Y SI LA PUBLICACION NO SALE, LOS OBJETOS VUELVEN. Es la
                   unica parte que no puede vivir en una transaccion --un
                   inventario no es una tabla-- asi que se deshace a mano.
                   Sin eso, no poder pagar la tasa SE COME LOS OBJETOS
              ⚠⚠⚠ EL INVARIANTE NUEVO ES EL PAYLOAD. `publicarObjeto` escribe
                  «identificador + separador + cantidad» y la entrega lo
                  vuelve a leer: DOS SITIOS con su propia idea del formato. Si
                  dejaran de estar de acuerdo, LA COMPRA NO DARIA NINGUN
                  ERROR -- el dinero cambia de manos y los objetos no
                  aparecen. Es el unico fallo de esta mitad que se come
                  mercancia en silencio, y por eso se prueba de punta a punta
              ⚠ solo objetos SIN datos propios: `item_id` + cantidad describe
                la mercancia POR COMPLETO. Una picoleta encantada NO es
                fungible y no sale en la mochila vendible
              ⚠ nadie se compra a si mismo, y el precio NO VIAJA al comprar:
                viaja el identificador de la oferta y el servidor cobra
                mirando SU fila (P6)
              ⚠ AL VENDEDOR SE LE REFRESCA aunque no haya pulsado nada: su
                oferta desaparecio y su dinero subio. Es la leccion de los
                clanes -- el estado no es de quien lo mira
              EL TASADOR (solo Pokemon): formula + correccion por mercado
                BST . rareza . IVs . EVs . nivel . shiny . habilidad oculta
                y se corrige con la MEDIANA de precio/estimado de las ventas
                CERRADAS, con peso n/(n+K), K=8
                ⚠ solo cuentan las CERRADAS: un precio que nadie paga no dice
                  nada, y mirar lo publicado se mueve gratis
              LO QUE FALTA: fase 4 el INDICE DE PRECIOS . fase 5 el toast de
              «se vendio lo tuyo»
              ⚠ EL INDICE NO SIRVE DE NADA HASTA QUE HAYA OPERACIONES REALES:
                mediria el ruido de dos personas probando
              ⚠ SIN VERIFICAR EN EL JUEGO con dos cuentas

Tienda        COMPRAR Y VENDER, POR CATEGORIAS (2026-08-23)
              2 categorias . 9 articulos . SIN migracion: la logica
              (ShopService, ShopCatalog) llevaba escrita desde PHASE 3 y
              lo unico que faltaba era la pantalla
              ⚠⚠ ES UNA TIENDA DE PRIMEROS AUXILIOS, NO UN CATALOGO.
                 Orden del usuario, en dos pasos el mismo dia:
                   1) "solo articulos de Cobblemon, lo de Minecraft lo
                      consiguen explorando"
                   2) "items basicos, los necesarios. En Poke Ball LA
                      NORMAL. Item de curar entre 20% y 50%. Items que
                      se requieren para craftear cosas basicas. Lo otro
                      lo tienen que conseguir explorando"
                 paso de 90 articulos a NUEVE, y el segundo paso es el
                 que manda: las otras 20 balls, las piedras evolutivas,
                 los objetos de combate y las vitaminas SE JUEGAN
                 ⚠ una tienda completa VACIA EL MUNDO: si todo se compra,
                   explorar solo sirve para conseguir dinero
                 y encaja con lo de ayer: las BAYAS y las BELLOTAS son
                 justo lo que da XP al oficio AGRICULTOR, y la madera y
                 la piedra lo del MINERO. Venderlas competiria con ellos
                 el autotest comprueba lo de Cobblemon: es la clase de
                 regla que se cae sola cuando alguien edita el JSON
              LO QUE HAY, Y POR QUE ESE Y NO OTRO:
                esencial  poke_ball    la normal, dicho por el usuario
                          max_revive   NO es un objeto de combate, es un
                                       MATERIAL: la MAQUINA CURATIVA se
                                       craftea con cobre + hierro +
                                       redstone + UN MAX REVIVE, y el Max
                                       Revive no se craftea, sale de
                                       cofres. Verificado en su receta
                cuidado   potion       20 PS
                          super_potion 60 PS
                          5 curaestados
              ⚠ LOS PS SALEN DE SUS DATOS, no de memoria:
                data/cobblemon/mechanics/potions.json dice 20/60/120.
                Sobre un Pokemon de nivel medio (80-130 PS) son ~20% y
                ~50%, que es la banda que pidio el usuario. LA
                HIPERPOCION (120) cura una barra entera de casi
                cualquiera: por eso no esta, y esa es la linea
              EL CATALOGO SE GENERA DEL JAR, no se escribe:
                python tools/gen_tienda.py
                python tools/gen_tienda.py --buscar stone
              ⚠ misma leccion que los 62 cosmeticos que no existian: un
                catalogo escrito a mano PROMETE cosas. ShopCatalog.load()
                se salta lo que no exista con un aviso QUE NADIE MIRA, asi
                que un identificador mal escrito no da error: da un hueco
                el generador ABORTA en vez de publicar el hueco
              ⚠⚠ LOS PRECIOS SON PROVISIONALES Y A PROPOSITO (orden del
                 usuario): "mas adelante definimos precios porque
                 necesitamos un analisis general de la economia"
                 por eso los precios no se escriben uno a uno: hay CINCO
                 ESCALONES (200 . 400 . 600 . 900 . 3.000) y cada articulo
                 dice a cual pertenece. Aplicar el analisis sera cambiar
                 CINCO cifras en gen_tienda.py
                 los anclajes vienen de la config real de produccion:
                 Poke Ball 400 . Pocion 600 . Superpocion 900 . Revivir 3000
                 la recompra es un PORCENTAJE (10 %) y no un numero suelto,
                 para que el no-arbitraje no pueda romperse tecleando mal
              ⚠ FUERA A PROPOSITO: TODO LO DEMAS. Las otras 20 balls, las
                10 piedras evolutivas, los objetos de combate, las
                vitaminas, los caramelos EXP y la Poke Caña. Se juegan
              ⚠ Y SI ALGUN DIA VUELVEN, que no sea por LunaCoins:
                `rare_candy`, `exp_candy_*` y `lucky_egg` por moneda
                premium seria comprar progresion con dinero real -- T4, la
                linea roja de D-007 y D-014. Por Plata son un sink y estan
                bien
              LAS CATEGORIAS VAN EN EL PANEL IZQUIERDO, no en pestañas
              arriba: llevan icono + nombre + una frase que explica para
              que sirven, y eso en una pestaña de 150 px no entra. Es la
              primera pantalla que usa el panel para algo de verdad
              cantidades x1 . x8 . x64 (un mazo son 64: comprar Poke Balls
              de una en una son 64 clics)
              ⚠ EL PRECIO NO VIAJA EN EL PAQUETE. Va el IDENTIFICADOR del
                articulo y el servidor busca el precio en SU catalogo. Si
                viniera del cliente, un cliente modificado compraria
                Revivir por 1 (P6)
              ⚠ NI UN INDICE: un indice ata al cliente al orden exacto del
                JSON, y cambiar el catalogo con la tienda abierta le haria
                comprar el articulo de al lado
              ⚠⚠ LA CANTIDAD SE ACOTA ANTES DE MULTIPLICAR. Llega del
                 cliente, y un 2.000 millones en `precio * cantidad`
                 DESBORDA el long y sale NEGATIVO: cobrar en negativo es
                 INGRESAR dinero. Acotar despues no sirve de nada
              ⚠ "cuantos tengo" lo cuenta el CLIENTE de su inventario, que
                ya esta sincronizado. Mandarlo obligaria a reenviar el
                catalogo cada vez que alguien recoge algo del suelo
              ⚠ el saldo se REENVIA tras cada compra (`enviarSaldo`, que se
                extrajo del manejador de PedirSaldo). Sin eso el jugador ve
                el numero viejo hasta reabrir -- la leccion del 23-ago
              ⚠ los botones se APAGAN, no desaparecen: que un articulo
                exista y no puedas pagarlo es INFORMACION; que no exista es
                un catalogo distinto
              +7 comprobaciones, y son de DIBUJADO, no de economia:
                las 5 categorias caben en el panel (736 de 762). LA SEXTA
                NO CABRIA, y el sintoma no seria un error sino una
                categoria fuera del marco: invisible e impulsable
                mismo fallo que la cadena `oficios` del arbol de misiones
              ⚠ SIN VERIFICAR EN EL JUEGO todavia

Clanes        EL PRIMER SISTEMA SOCIAL (2026-08-23, V013)
              detalle completo en docs/social/clanes.md
              fundar 5.000 de Plata . 30 miembros . 3 roles . tesoro comun
              se entra POR INVITACION (no hay boton de "pedir entrar")
              la etiqueta sale en CHAT + TABLIST + ENCIMA DE LA CABEZA
              ⚠⚠ EL ESTADO NO ES DE QUIEN LO MIRA, y eso cambia todo. Si
                 alguien echa a un miembro y solo se refresca a si mismo,
                 los demas siguen viendo al echado --y el echado se cree
                 dentro-- hasta que reabran. Se reenvia A TODO EL CLAN
              ⚠⚠ UN JUGADOR, UN CLAN, Y NO LO DICE UNA COLUMNA: lo dice la
                 CLAVE PRIMARIA de clan_member (player_id). Asi, entrar en
                 un segundo clan FALLA EN LA BASE venga de donde venga --
                 dos clicks rapidos en dos invitaciones, o un cliente
                 modificado. En Java se comprueba tambien, para dar un
                 mensaje que se entienda, pero la que manda es esa
              ⚠⚠ EL LIDER NO PUEDE SALIR mientras quede alguien: el clan
                 quedaria VIVO Y SIN GOBIERNO para siempre. Y nadie puede
                 ASCENDER a otro a LIDER --eso es `traspasar`, que ademas
                 BAJA al anterior; si fuera un rol mas habria dos lideres
              ⚠ el indice unico va sobre la version en MINUSCULAS. Sin el,
                «Luna» y «LUNA» son dos clanes que EN EL CHAT SE VEN IGUAL
              ⚠ el nombre solo admite letras, numeros y espacios, y el que
                importa es el §: con un codigo de color dentro la etiqueta
                pintaria el resto de la linea del chat de TODO EL MUNDO
              ⚠ UN EQUIPO DE MARCADOR POR JUGADOR (`luna<hash>`), no uno
                por rango: un jugador solo puede estar en UNO y el rango ya
                usaba el suyo. El prefijo lleva rango + clan juntos. Se hace
                con marcador y no con un paquete nuestro porque el prefijo
                lo pinta VANILLA: un paquete propio solo lo verian los que
                tengan el mod, y esto tiene que verlo todo el mundo
              ⚠ EL TESORO ES DINERO: applyInTransaction, misma Connection
                que la fila del clan (R3) y clave de idempotencia (R4). El
                autotest comprueba SUMA CERO --aportado == sacado + tesoro--
                porque es el unico invariante que caza dinero creado
              ⚠ UN CLAN NO DA NINGUNA VENTAJA, a proposito: identidad y un
                sitio donde juntar dinero. Un bono de clan es una FUENTE (P3)
                y ademas castiga a quien juegue solo
              ⚠ 51 comprobaciones nuevas y la mayoria son de LO QUE NO SE
                PUEDE HACER: una regla de permiso no falla ruidosamente,
                falla dejando que alguien vacie el tesoro
              D-038 CUMPLIDA: `Ficha` llevaba el campo `clan` vacio desde el
              17-ago y decia que encenderlo seria "rellenar tres lineas".
              Fue exacto
              ⚠ SIN VERIFICAR EN EL JUEGO todavia: hacen falta DOS cuentas
              desplegado y V013 aplicada (2026-08-23) . Done (9,2 s)

Rangos        NOVATO . ELITE . CAMPEON . MAESTRO . LEYENDA (2026-08-27, V020)
              y encima ADMIN . DEV . MODERADOR, que son de equipo
              /luna rango                   los que hay y cuanta gente
              /luna rango <jugador> <RANGO> nivel 4, SIN que este conectado
              ⚠⚠ SALIO GRATIS PORQUE EL SISTEMA NO EXISTIA. `Tablist.rankOf`
                 decia la verdad en su comentario: «Provisional... todo el mundo
                 es JUGADOR salvo los operadores». NO habia ni un rango guardado,
                 asi que renombrarlos no le cambio el rango a nadie
              ⚠⚠⚠ VARCHAR Y NO UN ENUM DE MARIADB, y esto ya nos mordio con los
                  oficios (V012): un ENUM guarda EL INDICE, asi que reordenar
                  convierte a unos jugadores en otros, y meter un valor que no
                  esta en la lista NO DA ERROR -- guarda la cadena vacia
              ⚠⚠ EL `escalon` ES UN NUMERO EXPLICITO, no `ordinal()`: con ordinal,
                 meter un rango en medio le cambia el nivel a todos los de abajo
              ⚠⚠ LA CACHE NO ES UNA OPTIMIZACION, ES UN REQUISITO: el rango lo
                 pregunta el tablist, el chat y la mochila, todo EN EL HILO DEL
                 SERVIDOR. Se lee una vez al entrar
                 ⚠ y cambiarlo escribe EN LOS DOS SITIOS. La base PRIMERO
              ⚠⚠ UN OPERADOR SE VE COMO ADMIN PERO NO DESBLOQUEA COMO ADMIN:
                 `rankOf` da ADMIN (informacion operativa) y `escalonDe` da el
                 guardado. Sin esa separacion, dar OP a alguien para mirar una
                 cosa le regalaria la mochila entera
              ⚠ un nombre mal escrito DA ERROR en vez de degradar: `Rank.de` cae
                al mas bajo --correcto al leer una fila vieja-- asi que sin
                comprobarlo aparte, un error de tecleo bajaria a un LEYENDA

Mochila       SIETE FILAS QUE SE ABREN POR RANGO (2026-08-27, V021)
              tecla N o el icono del PokePad
                NOVATO 1 . ELITE 3 . CAMPEON 4 . MAESTRO 6 . LEYENDA 7
              ⚠⚠ SIETE Y NO SEIS, y sale de la aritmetica del usuario: «1, otras
                 2 mas, otra mas, otras 2 mas, y LEYENDA todas» -> 1,3,4,6... y
                 «todas» TENIA que ser mas de 6 o LEYENDA no daria nada nuevo
              ⚠⚠ NO SE USA SOPHISTICATED BACKPACKS aunque estuviera instalado:
                 su almacen vive DENTRO DE UN ITEM (y el usuario no quiere item)
                 y no sabe nada de NUESTROS rangos. Misma razon que D-040 dio
                 para no adoptar un mod de clanes
              ⚠⚠ ESTO NO ES UN MENU DE COFRE DE LOS QUE PROHIBE P9-BIS. D-026
                 retiro aquellos porque eran PANTALLAS DE INFORMACION disfrazadas
                 de inventario. Esto ES un inventario, y arrastrar objetos EXIGE
                 un ScreenHandler. Lo que si se cumple: el dibujo es NUESTRO
              ⚠⚠⚠ EL CANDADO VIVE EN EL SERVIDOR, dentro del Slot. La barra roja
                  es decoracion: un cliente modificado no dibuja nada y hace clic
                  donde quiere (P6)
                  ⚠ Y SI SE PUEDE SACAR de un hueco bloqueado, a proposito: si
                    alguien baja de rango sus objetos quedarian ENCERRADOS PARA
                    SIEMPRE. Sacar si, meter no
              ⚠⚠ SE GUARDA AL CERRAR Y AL DESCONECTAR, y las dos hacen falta: al
                 desconectar Minecraft cierra el contenedor sin avisar, y como el
                 guardado BORRA Y REESCRIBE, sin la segunda se perderia entera
                 ⚠ borrar y escribir van EN UNA TRANSACCION
              ⚠ `payload` lleva el objeto ENTERO (encantamientos incluidos), al
                contrario que el escaparate: aqui entra cualquier cosa

Mundos        HOGAR + TRES SALVAJES DE TRES (2026-08-27, V022)
              icono EXPLORAR . dos tarjetas con arte de 768x512
              ⚠⚠⚠ REPARTIR GENTE ENTRE DIMENSIONES NO BAJA EL LAG. TODAS se
                  tickean EN EL MISMO HILO. 40 jugadores repartidos por tres
                  mundos cargan LOS MISMOS chunks que 40 por uno -- y si la gente
                  se agrupa, UN SOLO MUNDO ES MEJOR porque los comparten.
                  Lo que si arregla es que 40 personas se peleen por el mismo
                  legendario: es un problema DE JUEGO. Por eso la pantalla habla
                  de GENTE y nunca de rendimiento
                  ⚠ el camino de verdad para mucha gente es MAS SERVIDORES con
                    proxy. Y D-009 ya lo permite: economia, clanes, GTS y
                    misiones estan EN MARIADB, asi que un segundo servidor lo
                    compartiria sin escribir una linea
              MEDIDO EN EL PANEL: RAM 10 GB usando 5,79 con el mundo vacio,
              disco 120 GB usando 0,92, CPU 300% = 3 nucleos
              SEIS DIMENSIONES DECLARADAS, TRES EN USO. Fabric NO crea
              dimensiones en caliente: la rotacion semanal sera CAMBIAR TRES
              NUMEROS. Un mundo declarado y vacio no cuesta casi nada
              ⚠ el primero se llama `salvaje` y no `salvaje1`: ya tiene datos
              RADIO 3000 (36 km²), bajado de 5000 por el usuario al ver el coste:
                3000  140.000 chunks  25-45 min/mundo   2-4 h los tres
                5000  390.000 chunks   1-2 h /mundo    9-18 h con reservas
              y esas horas COMPITEN con los jugadores por los mismos 3 nucleos
              ⚠⚠ LA SALIDA AL REPARTO es lo mejor del diseño y la pidio el
                 usuario: un reparto que SEPARA A UN CLAN no es equilibrio, es
                 una averia. La lista de compañeros la salta
                 ⚠ y se comprueba EN EL SERVIDOR que sea de tu clan
              ⚠ el cliente NO elige mundo: manda «salvaje» y decide el servidor
              HOGAR: la primera vez ALEATORIA en 2.000 de radio, y despues DONDE
              LO DEJASTE (V022)
              ⚠⚠ Minecraft recuerda UNA posicion por jugador, la de al
                 desconectar. NO recuerda «donde estaba en el Hogar» mientras
                 esta en el Salvaje, que es justo lo que hace falta
              ⚠⚠ LA PRIMERA AL AZAR ES LO QUE REPARTE LAS CASAS: si todos
                 aparecieran en el mismo punto, todos construirian ahi -- y con
                 protecciones, quien llegue tarde no encuentra sitio
              ⚠ se apunta ANTES de mover, en los tres caminos. Despues ya esta en
                el otro mundo y se guardaria LA POSICION DE DESTINO
              LO QUE FALTA: PRE-GENERAR (Chunky ya instalado, sin usar) y la
              rotacion semanal con las tres reservas
              ⚠ sin pre-generar, el primero que caiga en zona virgen paga la
                generacion con su lag. Es la medida que MAS se nota

Decorativos   POKEMON QUIETOS, Y SON DIEZ COSAS QUE APAGAR (2026-08-26/27)
              /luna decorar <especie> <quieto|dormido|flotando> [x y z] [grados]
              /luna decorar quitar [radio]
              /luna paradas · /luna paradas quitar
              ⚠⚠ ES UN POKEMON DE VERDAD, no una estatua: los modelos viven
                 DENTRO de Cobblemon y no se pueden dibujar sin su entidad
              LA LISTA, y cada una llego porque alguien probo la anterior:
                etiqueta . nivel . IA . captura . daño . DAÑO EN CREATIVO .
                combate . sonido . persistencia . postura . POKEDEX
              ⚠⚠⚠ `setInvulnerable` NO CUBRE EL CREATIVO. Lo dice el propio
                  Minecraft: `&& !fuente.isSourceCreativePlayer()`. Y aqui TODOS
                  los que colocan son operadores en creativo. Hoy tambien se
                  corta con ServerLivingEntityEvents.ALLOW_DAMAGE
              ⚠⚠ `uncatchable` NO IMPIDE EL COMBATE: sin `UNBATTLEABLE`, el
                 Miraidon de una parada seria el jefe final del servidor
              ⚠⚠ LA POKEDEX SON DOS EVENTOS, no uno: POKEMON_SEEN («lo he visto»)
                 y POKEDEX_DATA_CHANGED_PRE («se va a escribir»). Cancelar solo
                 el primero deja abierto el escaneo, que es lo que el usuario
                 probo. Los dos son cancelables en Cobblemon
                 ⚠ y hay que marcar EL POKEMON, no la entidad: los eventos de
                   Pokedex reciben el Pokemon. `getPersistentData()` es el hueco
                   que Cobblemon deja para eso
              ⚠⚠ SE FILTRA POR MARCA Y NO POR ESPECIE, a proposito: asi protege a
                 cualquier decorativo y NO protege a un Miraidon de verdad
              ⚠ `enablePoseTypeRecalculation=false` es la que no es obvia:
                Cobblemon recalcula la postura CADA TICK, asi que «dormido»
                duraria un tick

Paradas       EL MOTO TAXI: SIETE PUNTOS EN LA CIUDADELA (2026-08-27)
              Torre de Batalla . Laboratorio de Oak . Palacio de Entrenadores .
              Monumentos . Torre Comercial . Centro de Curacion . La Montaña
              ⚠⚠ SOLO FUNCIONAN DENTRO DE LA CIUDADELA: si se pudieran usar desde
                 el salvaje serian un «volver a casa» instantaneo, y salir a
                 explorar dejaria de tener riesgo
                 ⚠ fuera SE APAGAN, no se esconden: que existan y no se puedan
                   usar ES INFORMACION
              ⚠ las coordenadas van EN EL CODIGO: son parte de la ciudadela, que
                se construye a mano y cambia con ella
              ⚠ `/luna paradas` se puede repetir: BORRA antes de poner. Sin eso
                quedarian dos superpuestos -- y no se pueden atacar ni capturar
              ⚠⚠ ESTUVIERON EN LA PANTALLA EQUIVOCADA, y el usuario lo cazó: se
                 metieron en EXPLORAR. Hoy tienen la suya (ver Viajes)
                 ⚠ y el MIRAIDON PASO DE CARTEL A BOTON: clic derecho abre
                   Viajes. El usuario lo pidio SIN interaccion y despues cambio
                   de idea, asi que las dos versiones estan escritas
                   ⚠⚠ RESPONDE POR MARCA, NO POR ESPECIE NI POR SER DECORATIVO:
                      `MARCA_PARADA` es una SEGUNDA etiqueta. Con la de siempre,
                      el Kabutops del laboratorio y los tres iniciales abririan
                      Viajes al tocarlos
                   ⚠ y sigue abriendose desde el PokePad: un punto de viaje al
                     que solo se llega tocando su Miraidon obligaria a IR
                     ANDANDO HASTA UN PUNTO DE VIAJE para poder viajar

Viajes        LAS PARADAS, POR FIN EN SU SITIO (2026-08-27)
              icono `warps` . rejilla 4x2 de fichas de color . panel a la
              izquierda con el destino elegido y para que sirve
              SE ABRE TAMBIEN CON CLIC DERECHO en cualquier Miraidon de parada
              ⚠⚠ POR QUE NO ERA UN TROZO DE EXPLORAR, que es como empezo:
                 EXPLORAR responde a «que mundo» -- dos opciones, una decision
                 con consecuencias, un viaje que cambia las reglas
                 VIAJES  responde a «que esquina de la ciudadela» -- siete
                 destinos equivalentes, sin consecuencias, veinte veces al dia
                 mezclarlas obligaba a que la segunda cupiera en el hueco de la
                 primera, y por eso acabaron siendo botoncitos apretados
              ⚠ CADA PARADA TIENE SU COLOR, y no es adorno: con siete fichas del
                mismo tono hay que leerlas todas cada vez. Con color, a la
                tercera visita vas al cuadro naranja sin leer
              ⚠ UN CLIC ELIGE, EL BOTON VIAJA. Con el viaje en la propia ficha,
                un clic despistado te manda al otro lado de la ciudadela
              ⚠ FUERA DE LA CIUDADELA SE APAGAN Y SE DICE POR QUE. Un boton gris
                sin explicacion parece roto; con la razon al lado es una regla
              ⚠⚠ EL RECEPTOR DE `EstadoViajes` ABRE LA PANTALLA, no solo guarda:
                 es lo que convierte el clic derecho en la pantalla. Y SOLO si no
                 hay otra delante -- si no, tocar un Miraidon sin querer con el
                 inventario abierto lo cerraria de golpe
              +8 comprobaciones, y la que importa es LA REJILLA: 4x2 son OCHO
              huecos, asi que una NOVENA parada seria inalcanzable SIN DAR
              NINGUN ERROR. Es la decimosexta aplicacion del PokePad otra vez
              ⚠ el icono del Miraidon se dibuja con ARO y no con dos discos:
                rellenar el interior con alfa 0 no borra nada --`fill` mezcla, y
                mezclar con transparente es no hacer nada-- y saldria macizo
              ⚠ SIN VERIFICAR EN EL JUEGO todavia

Trajes        LOS TRAJES DE RANGO, EN LA PANTALLA DE KITS (2026-08-28, V023)
              5 trajes . NOVATO hecho . los otros 4 «en preparacion»
              3 pestañas: KITS DE RANGO . KITS EXCLUSIVOS . MIS KITS
              saldo de LunaCoins con su «+» . previsualizador 3D
              ⚠⚠⚠ NO HAY OBJETO EN NINGUNA PARTE, y hay CUATRO motivos. Los tres
                 primeros son los de los sombreros --ocupa ranura, se cae al
                 morir, SE PUEDE REGALAR (y entonces el traje ya no vendria del
                 rango, que es lo unico que lo sostiene)--, y el cuarto es de
                 infraestructura: VEINTE OBJETOS SON VEINTE ENTRADAS MAS EN UN
                 REGISTRO QUE SE SINCRONIZA, o sea veinte razones para echar a
                 quien no se actualice. Aqui no se registra nada
              ⚠⚠ CERO PROTECCION, y es un dato MEDIDO: el material de Diosesmon
                 da 3/8/6/3 en su `ModArmorMaterials`, que es EXACTAMENTE
                 diamante. Su traje de pago protege. D-007 y D-014 dicen que se
                 vende identidad, no poder
              ⚠⚠ NO SE USA GECKOLIB aunque este instalado: hace falta para
                 modelos ANIMADOS y estos no lo estan. El .geo.json se convierte
                 a ModelPart de vainilla, que es lo que hace el dibujado de
                 armadura del propio Minecraft
                 ⚠⚠⚠ Y LA CONVERSION DE COORDENADAS ES DONDE SE ROMPE TODO:
                    Bedrock mide Y hacia ARRIBA desde los pies y Java hacia
                    ABAJO desde el pivote del hueso. De ahi y = 24 - oy - sy y
                    z = -oz - sz. Copiada mal NO DA NINGUN ERROR: el traje sale
                    del reves, dentro del cuerpo, o flotando bajo los pies
              ⚠⚠ NO HAY TABLA DE «QUE TRAJES TIENES»: se DERIVA del rango. Una
                 segunda tabla seria una copia que puede quedarse vieja. De
                 propina, BAJAR DE RANGO RETIRA EL TRAJE SOLO
              ⚠⚠ EL ESTADO NO ES DE QUIEN LO MIRA, y aqui es literal: un traje
                 lo ve TODO EL MUNDO MENOS TU. Se reparte a todos al cambiarlo
                 Y se pone al dia a quien entra -- esa segunda mitad es la que
                 siempre se olvida
              ⚠ el traje se carga DENTRO del callback del rango, no al lado:
                `revisar` necesita el escalon y `ranks.cargar` es ASINCRONO.
                Es la trampa de `conceder()` en la pantalla del inicial
              ⚠ se puede llevar CUALQUIERA hasta el tuyo, no solo el tuyo:
                obligar al mas alto convierte una recompensa en un uniforme
              ⚠ el probado se quita en un `finally`: el dibujado pasa por codigo
                de vanilla y lo que se prueba viaja en una estatica. Sin el, una
                excepcion te deja saliendo al mundo con un traje que no tienes
              +10 comprobaciones, y la que importa: UN TRAJE SIN ARTE NO SE PUEDE
              PONER NI SIENDO LEYENDA. Sin eso se equipa, se sincroniza, no da
              ningun error y el jugador NO VE NADA -- el fallo de los 62
              cosmeticos que no existian, otra vez
              ⚠ SIN VERIFICAR EN EL JUEGO todavia

EL ARTE       tools/gen_trajes.py . tools/trajes/ . docs/ui/prompts-trajes.md
              ⚠⚠⚠ EL VISOR ES LA PIEZA QUE IMPORTA, NO UN EXTRA. Sin el, un traje
                 se escribe A CIEGAS: generas un fichero que dice «cubo aqui,
                 cubo alla» y no ves el resultado hasta compilar, desplegar,
                 reiniciar y ponertelo. Media hora por intento = UN intento
                 `--ver novato` dibuja las 4 vistas y NO TOCA NADA
              ⚠⚠ Y DIBUJA EL MANIQUI DEBAJO, que es lo que caza el fallo de
                 verdad. En su segunda pasada encontro DOS SITIOS CON PIEL
                 ASOMANDO --el cinturon no llegaba al final del torso y la
                 hombrera acababa antes que el brazo-- y flotando en negro el
                 traje parecia perfecto
              ⚠⚠ EL BOCETO LO GENERA EL USUARIO CON IA Y AQUI SE TRADUCE. Una IA
                 de imagen NO puede hacer el traje --no genera .geo.json ni
                 textura envuelta-- pero SI el boceto, y con el dejo de inventar.
                 Vale el boceto PLANO Y ORTOGRAFICO, no un render bonito
                 ⚠ LA VISTA DE ESPALDA SE GANO EL SUELDO EN EL PRIMERO: la Poke
                   Ball del NOVATO no se ve de frente, asi que sin esa vista no
                   se habria puesto nunca
              ⚠ el reparto de la textura SE CALCULA: escrito a mano cuadra hasta
                que alguien cambia un cubo, y entonces dos cubos comparten
                pixeles -- la cara de la bota dibujada en el hombro
              ⚠ NOVATO no usa el color de su rango (§f, casi blanco): un traje
                blanco sobre un cuerpo gris no se ve. Lleva el ROJO DEL
                ENTRENADOR. Los otros cuatro si llevan el suyo

Comprobar     UN SELECTOR NO VE LO QUE ESTA EN UN CHUNK DESCARGADO
              (2026-08-29) . COSTO CUATRO DIAGNOSTICOS Y NO HABIA NADA ROTO
              coloque a Brock y su Geodude, el log dijo COLOCADO, y
                execute in <dim> run data get entity @e[type=cobblemon:pokemon]
              contesto "No entity was found". Parecia que el Pokemon no nacia
              ⚠⚠⚠ ESTABA AHI, EN -137.544 69.0 52.0 EXACTOS. @e solo recorre
                 las entidades CARGADAS, y en una dimension sin nadie cerca no
                 hay ninguna. La comprobacion era la que fallaba, no el codigo
              ⚠⚠ Y LO QUE MAS DESPISTO: el TrainerMob SI aparecia y el Pokemon
                 no, estando en el MISMO CHUNK. Un dato que parece una pista
                 sobre el tipo de entidad y era ruido de que chunk estaba
                 cargado en cada momento
              LA FORMA DE COMPROBARLO DE VERDAD, y son dos lineas:
                execute in <dim> run forceload add <x1> <z1> <x2> <z2>
                ... la consulta ...
                execute in <dim> run forceload remove all
              ⚠⚠⚠ Y PEOR: `execute in <dim> run ... @e` NO ACOTA EL SELECTOR EN
                 ESTE SERVIDOR. Comprobado: desde `lunaeternal:lobby`, donde no
                 hay NADA colocado, `@e[tag=luna_lider]` ve los dos --el de la
                 ciudadela y el de la dimension de gimnasios--. Tres censos
                 seguidos me dijeron «hay un Brock en la ciudadela» cuando estaba
                 en otra dimension, y casi me pongo a arreglar algo que no
                 estaba roto
                 LA SONDA QUE SI DICE LA VERDAD ES POR BLOQUE, porque los
                 bloques SI van por la dimension de ejecucion:
                   execute at @e[tag=X] run setblock ~ ~6 ~ minecraft:glowstone
                   execute in <dim> if block <x> <y> <z> minecraft:glowstone
                       run say ESTA EN <dim>
                 (y luego se quita el bloque)
              ⚠ y QUITAR el forceload despues: un chunk cargado para siempre se
                tickea para siempre
              ⚠ para CONTAR, «tag @e[...] add x» dice "Added tag to N
                entities"; «data get ... limit=1» ensena UNO y no dice cuantos
                hay -- que es lo que casi me deja dos Brocks superpuestos

Teletransporte ⚠⚠⚠ NO TELETRANSPORTAR DENTRO DEL EVENTO DE CONEXION
              (2026-08-29) . ROMPIO LA SESION DE UN JUGADOR Y LA TRAZA NO
              NOMBRA LA CAUSA POR NINGUN LADO
              `Combate.alEntrar` sacaba de la arena a quien volviera dentro, y
              lo hacia EN EL ACTO, dentro de ServerPlayConnectionEvents.JOIN
              ⚠⚠⚠ NO FALLA AHI. El jugador acaba de ser añadido al mundo y el
                 gestor de tickets de chunk todavia no lo tiene apuntado en su
                 seccion; sacarlo de la dimension en ese instante deja el apunte
                 A MEDIAS, y el apunte roto SE QUEDA TODA LA SESION
                 reviento SIETE MINUTOS DESPUES, en el siguiente cambio de
                 dimension:
                   NullPointerException: Cannot invoke ObjectSet.remove
                     at ChunkTicketManager.handleChunkLeave
                     at ServerWorld.removePlayer
                     at ServerPlayerEntity.teleport
              ⚠⚠ Y EL VIAJE SE QUEDA A LA MITAD: el jugador SALE de un mundo y
                 NO LLEGA al otro. Real: acabo en el Mundo Hogar con las
                 coordenadas de la ciudadela, y su cliente dibujando al Brock de
                 la ARENA flotando sobre la plaza -- recibio las entidades del
                 mundo nuevo sin haber cambiado de mundo. Un fantasma de cliente
                 que parece un bloque colocado donde no toca
              HOY espera 40 ticks y vuelve a comprobar que sigue conectado
              ⚠ Y AL LLEGAR, EL CHUNK DE DESTINO SE CARGA ANTES DE MOVER: en una
                dimension sin jugadores no hay ni un chunk cargado. Es la misma
                leccion que ya estaba escrita en `TravelService.ensurePlatform`
                para los bloques, aplicada a las entidades
              ⚠ LA REGLA QUE QUEDA: si hay que mover a alguien al entrar, se
                encola. Dos segundos no los nota nadie y se ahorra un fallo que
                aparece en otro sitio y otro dia

Gimnasios     BROCK RECIBE, BROCK COMBATE Y LA MEDALLA LLEGA (2026-08-29, V024)
              dimension `lunaeternal:gimnasios` . los 8 de Kanto
                x = gimnasio * 1024      brock en 0 64 0
                z = ranura   * 128       ocho copias por gimnasio
              EL RECORRIDO ENTERO:
                clic derecho al Brock de la CIUDADELA  -> dialogo propio
                "ESTOY LISTO"  -> ranura + arena + lider + viaje
                clic derecho al Brock de DENTRO        -> combate
                ganar -> MEDALLA en el PokePad (abajo a la izquierda)
              /luna gimnasio                    donde esta cada uno
              /luna gimnasio brock plataforma   el ancla 9x9 (oro = origen)
              /luna gimnasio brock medir        cuanto mide lo construido
              /luna gimnasio brock lider        pone a Brock para MIRARLO
              /luna gimnasio brock posiciones   los bloques de Battle Position
              /luna gimnasio ciudadela [grados] Brock y su Geodude, en la plaza
              /luna gimnasio reclonar           al cambiar el maestro
              /luna gimnasio brock limpiarranuras  borra las copias
              ⚠⚠ `limpiarranuras` HACE FALTA MIENTRAS SE CONSTRUYE, y no es
                 obvio por que: `clonar` NO COPIA EL AIRE, asi que un bloque que
                 QUITES del maestro se queda en la copia para siempre y volver a
                 clonar no lo arregla
                 ⚠ es lento a proposito (46.354 bloques y 2,8 s de retraso
                   medidos en vivo). Se ejecuta a mano, cuando toca
              ⚠⚠⚠ EL COMBATE NO PASA POR `startBattleWith`, Y ESO ES LO QUE HACE
                 QUE FUNCIONE. Ese metodo llama primero a `canBattleAgainst`, que
                 comprueba LA PROGRESION DE RCTMOD -- y la config real de este
                 servidor, leida del panel, dice:
                   initialLevelCap  = 15      y el Onix de Brock es nivel 20
                   initialSeries    = "empty" y Brock es de la serie "kanto"
                   allowOverLeveling = false
                 o sea que a un jugador normal LE HABRIA DICHO QUE NO. Y no con
                 un error: CON UN DIALOGO POR EL CHAT, que es justo lo que el
                 usuario pidio que no hubiera. El clic derecho habria parecido no
                 hacer nada
                 se usa `RCTMod.makeBattle`, que es PUBLICO, es lo mismo que
                 llama rctmod por debajo y no comprueba nada de eso. LA PUERTA
                 DEL GIMNASIO ES NUESTRA: las medallas que hacen falta, en
                 nuestra base y en nuestra pantalla
              ⚠⚠ Y LA VICTORIA SE ESCUCHA EN COBBLEMON (`BATTLE_VICTORY`), no en
                 rctmod: es la fuente mas cercana al hecho y no depende de la
                 contabilidad que acabamos de rodear
              ⚠⚠⚠ VARIOS JUGADORES RETAN AL MISMO LIDER A LA VEZ, y lo cazo el
                 usuario antes de que lo escribiera mal. Con una sala y un Brock,
                 el segundo retador ve el combate del primero y no tiene contra
                 quien luchar. Se INSTANCIA, y se puede porque UN COMBATE DE
                 COBBLEMON NO NECESITA LA SALA: el combate es una interfaz y la
                 sala es la puesta en escena
              ⚠⚠ LA RANURA 0 ES EL MAESTRO Y NO SE JUEGA EN ELLA: es donde se
                 pega el esquema. Las demas se clonan de ella LA PRIMERA VEZ que
                 hacen falta, y la copia se queda hecha
                 ⚠ mientras se construye, `/luna gimnasio reclonar`: una ranura
                   se clona UNA VEZ por arranque, asi que mover a Brock o poner
                   los bloques de posicion NO llega a las copias ya hechas
              ⚠⚠⚠ LAS RANURAS VIVEN EN MEMORIA, NO EN LA BASE. Guardarlas tendria
                 un fallo mudo y PERMANENTE: al reiniciar las ocho figurarian
                 ocupadas para siempre y nadie podria retar a Brock nunca mas,
                 sin un solo error en el log
                 ⚠⚠ y hay que soltarlas en TRES caminos: ganar, perder y
                    DESCONECTARSE. El tercero es el que se olvida
                 ⚠ y al VOLVER a entrar dentro de una arena, fuera: de esa
                   dimension no se sale andando
              LAS POSICIONES SON DESFASES, NO COORDENADAS. Absolutas parecerian
              mas simples y en la ranura 3 el jugador apareceria FUERA de su copia
                entrada  48 78 17.26  -> desfase (48, 14, 17.26)
                tarima   48 72 40.45  -> desfase (48,  8, 40.45)
                ⚠⚠ LA TARIMA ESTA SEIS BLOQUES MAS ABAJO QUE LA ENTRADA, y no es
                   un error de medida: se entra por arriba y se combate abajo. Si
                   algun dia alguien "corrige" uno para que cuadren, el jugador
                   aparece dentro del suelo
              LA CIUDADELA (medido por el usuario):
                Brock    -137.95  69 49.37
                Geodude  -137.544 69 52      SOLO ESTETICO
                ⚠ Geodude porque es el PRIMERO de su equipo (Geodude 16, Bonsly
                  16, Cranidos 18, Onix 20), leido del datapack. Y porque entre
                  los dos puntos hay 2,66 bloques: un Onix mide casi nueve de
                  largo y se comeria la sala
                ⚠ el giro se pasa por comando (`ciudadela <grados>`): hacia donde
                  mira Brock depende de como quede la sala, y eso no se sabe
                  desde el codigo
              ⚠⚠⚠ Y BROCK NO PUEDE RETAR SOLO: la config trae
                 forceBattleOnSight=true con OCHO BLOQUES de alcance, o sea que un
                 Brock en la plaza retaria a quien pase por delante. Eso vive en
                 `ForceIntoBattleGoal`, que es un GOAL, y los Goals no corren con
                 `setAiDisabled(true)`. Comprobado en el jar, no supuesto
              ⚠⚠ Y NO SE LE PUEDE PEGAR NI EN CREATIVO: lleva la etiqueta de los
                 decorativos para heredar SU proteccion, que es la unica que cubre
                 el creativo. La leccion ya estaba pagada; esto es reutilizarla
              LOS BLOQUES DE «BATTLE POSITION» (`cobblemonbattlepositions`, MIT,
              YA instalado en servidor y cliente) son los que colocan a los
              Pokemon y a los entrenadores en la arena. Se ponen UNA VEZ en el
              maestro y las ocho copias los heredan al clonarse
                player_pokemon_position   OBLIGATORIO
                trainer_pokemon_position  OBLIGATORIO
                player_stand_position     opcional (teletransporta al jugador)
                trainer_stand_position    opcional (teletransporta al lider)
              ⚠⚠ SIN LOS DOS OBLIGATORIOS EL COMBATE NO FALLA: SALE MAL Y SE
                 CALLA. Cobblemon coloca los Pokemon donde caiga --encima de una
                 grada, dentro de una pared, detras del jugador-- y no hay ni un
                 aviso. `/luna gimnasio brock posiciones` lo comprueba
              ⚠⚠⚠ Y BUSCA EL BLOQUE MAS CERCANO EN UN RADIO DE 48 (leido de
                 config/cobblemonbattlepositions.json, no supuesto). Las ranuras
                 van a 128 y son copias identicas: si los bloques quedaran muy al
                 norte de una sala de 86 de fondo, DESDE EL FONDO EL BLOQUE DE LA
                 RANURA SIGUIENTE ESTARIA MAS CERCA QUE EL PROPIO -- y el combate
                 colocaria los Pokemon en la sala de otro jugador, sin error
                 lo comprueba `posiciones`, que es donde estan los dos numeros que
                 hacen falta (cuanto mide la sala y donde estan los bloques). En
                 el autotest seria comparar constantes contra constantes, o sea la
                 confianza falsa que ya nos mordio
                 ⚠ el mod TAMBIEN reserva la arena por su cuenta (ArenaKey =
                   dimension + las dos posiciones), y como cada ranura esta en
                   otra Z, cada copia es una arena distinta para el. El instanciado
                   encaja con su diseño sin tocar nada
              ⚠⚠⚠ PASO_RANURA ESTABA A OJO EN 64 Y EL GIMNASIO MIDE 86 DE FONDO.
                 Las copias se habrian pisado 22 bloques: sin error, dos
                 gimnasios fundidos y el segundo jugador dentro de la pared del
                 primero. Hoy 128, y `clonar` MIDE antes de copiar y SE NIEGA si
                 no cabe
                 ⚠⚠⚠ Y LA MEDICION MINTIO DOS VECES, CON LA MISMA CARA Y CON
                    LA CONTRARIA. Es la leccion mas cara de estos dos dias:
                      1) el barrido se limitaba a PASO_RANURA, o sea AL NUMERO
                         QUE TENIA QUE VALIDAR. Salio «64 de fondo», que era el
                         limite del barrido. MEDIR CON LA REGLA QUE INTENTAS
                         VALIDAR SOLO TE DEVUELVE LA REGLA
                      2) lo arregle barriendo ANCHO, y en cuanto existio una
                         copia EL BARRIDO SE LA COMIO: 86 de gimnasio + copia en
                         128 = «214 de fondo». Y `clonar` MIDE ANTES DE COPIAR,
                         asi que la copia siguiente habria sido de 214 y habria
                         escrito encima de las ranuras 1 y 2
                    ⚠⚠ LAS DOS ESTABAN MAL POR EL MISMO MOTIVO DE FONDO: USABAN
                       UN LIMITE EN VEZ DE MIRAR LO QUE HAY. Una sala tiene
                       suelo, asi que sus capas de Z estan TODAS ocupadas; entre
                       una copia y la siguiente hay AIRE. Hoy corta en el aire,
                       que es un dato del mundo y no un numero nuestro
                    ⚠ y DICE lo que se deja fuera: «hay 86 capas mas alla,
                      separadas por aire: son las copias y NO se cuentan»
                    lo destapo `/luna gimnasio brock posiciones` con un numero
                    imposible (-20 de separacion): la comprobacion que se escribio
                    para otra cosa cazo esta
                 ⚠⚠⚠ Y LA COMPROBACION DEL AUTOTEST NO VIGILABA NADA: comparaba
                    el fondo de las ranuras (eje Z) contra la separacion de los
                    gimnasios (eje X). EJES DISTINTOS, asi que pasaba siempre --
                    y pasaba mientras el numero estaba mal. UNA COMPROBACION QUE
                    COMPARA COSAS QUE NO SE TOCAN DA CONFIANZA FALSA, y eso es
                    peor que no tenerla
              ⚠ Brock YA EXISTE ENTERO en el datapack (`kanto_brock`): Geodude 16,
                Bonsly 16, Cranidos 18, Onix 20, dos Full Restore,
                maxTrainerDefeats 1 y spawnWeightFactor 0 (no aparece solo)
              ⚠ rctapi y rctmod pasan a ser `modCompileOnly` (IDs de version de
                Modrinth, NO numeros: el numero sirve el jar de NeoForge)
                ⚠⚠ Y TODO LO QUE LOS TOCA VA DETRAS DE `hayEntrenadores()`. Sin
                   esa guarda, un servidor sin rctmod se cae al arrancar con
                   NoClassDefFoundError -- un error que NO NOMBRA al mod que falta
              LO QUE FALTA: verificarlo en el juego, poner los cuatro bloques de
              posicion en el maestro, y los otros siete gimnasios

Medallas      NO SON UN OBJETO, Y ES ORDEN DEL USUARIO (2026-08-29, V024)
              «obtiene la medalla pero no fisica, la obtienen ya en el PokePad»
              el PokePad YA las dibujaba --dieciseis casillas abajo a la
              izquierda, apagadas las que no se tienen-- y lo unico que faltaba
              era que alguien rellenara el numero
              ⚠⚠ MISMA DECISION QUE LOS TRAJES Y POR EL MISMO MOTIVO: un objeto
                 se tira, se pierde al morir y SE PUEDE REGALAR -- y una medalla
                 regalada deja de decir «yo gane a Brock», que es lo unico que una
                 medalla significa
              ⚠⚠⚠ LA CLAVE PRIMARIA ES (player_id, gym), Y ESA ES LA REGLA. No la
                 dice una comprobacion en Java: la dice la clave, asi que ganarla
                 dos veces FALLA EN LA BASE venga de donde venga la peticion --
                 dos combates que acaban a la vez, un cliente modificado, un
                 reintento. Misma decision que `clan_member`
              ⚠⚠ LA MASCARA SE COMPONE AL LEER, NO SE GUARDA. Guardarla seria mas
                 compacto y NO SE PODRIA CONSULTAR: «cuanta gente tiene la de
                 Brock» seria un barrido con aritmetica de bits, y «cuando la
                 gano» no cabria en ninguna parte
              ⚠⚠ LA CACHE NO ES UNA OPTIMIZACION, ES UN REQUISITO: la mascara la
                 pregunta el dialogo EN EL MOMENTO DEL CLIC, en el hilo del
                 servidor. Se lee una vez al entrar. Misma razon que los rangos
                 ⚠ y conceder escribe EN LOS DOS SITIOS, la base PRIMERO
              ⚠⚠⚠ TRES LISTAS DE MEDALLAS ERAN UNA SOLA. El orden estaba escrito a
                 mano en `Gimnasio.TODOS`, en `PokePadScreen` y en la pantalla
                 nueva, y NADA las obligaba a coincidir: desordenadas, ganar a
                 Brock encenderia la medalla de MISTY sin dar ningun error -- el
                 jugador veria una que no ha ganado y no veria la que si
                 hoy las pantallas leen de `Gimnasio.insignias()`. ES MEJOR QUE
                 UNA COMPROBACION QUE LO DETECTE: asi no puede pasar
              ⚠ el bit de una medalla es `sala()`, y las texturas se REFERENCIAN
                al mod de medallas (van instaladas en el cliente): cero bytes en
                nuestro jar y no se redistribuye nada suyo
              ⚠ EL PROTOCOLO: viaja LA CLAVE del motivo («faltan», «ya_ganada») y
                su numero, no la frase. Un servidor no tiene idioma
              ⚠⚠ Y `EstadoGimnasio` TENIA SIETE CAMPOS. La tupla admite seis, y el
                 septimo --`yaGanada`-- decia lo mismo que `motivo`: dos campos
                 que dicen lo mismo son dos campos que un dia se contradicen. El
                 limite del codec destapo un campo que sobraba de verdad
              ⚠ SIN VERIFICAR EN EL JUEGO todavia

MESHY         DE UN MODELO 3D A BLOQUES (2026-08-29)
              tools/malla_a_construccion.py  .obj -> bloques del mundo
              tools/simplificar_malla.py     decimacion quadric
              tools/malla_a_armadura.py      .obj -> armadura (NO sirve, ver
                                              docs/ui/trajes-flujo.md)
              ⚠⚠⚠ SIRVE PARA CONSTRUCCION Y NO PARA ARMADURA, y la razon es una
                 sola: UNA SALA YA ES LO QUE MINECRAFT SABE HACER. Un muro recto
                 voxelizado sigue siendo un muro recto; un personaje organico se
                 convierte en papilla
                 medido: el gimnasio de Brock 46x16x40 con 8.175 bloques a la
                 primera; la armadura, 710 cajas y «no se aprecia nada»
              ⚠⚠ EL % DE RELLENO ES LA COMPROBACION: una sala hueca ronda el
                 15-35 %. Si sale el 80 %, Meshy devolvio un MACIZO con la
                 fachada bonita. Y se puede saber ANTES de convertir, contando
                 los triangulos que miran HACIA DENTRO (una sala trae ~64 %)
              ⚠⚠ Y MESHY NO HACE MODELOS DE BLOQUES: hace modelos LISOS QUE
                 PARECEN de bloques. Los cubitos de su render son sombreado
                 pintado, no geometria. Por eso al voxelizar sale papilla
              ⚠ decimar: por CELDAS sale deforme (aplasta una cara igual que una
                plancha); QUADRIC conserva la forma. Y las UV se transfieren POR
                TRIANGULO, no por vertice: la textura es un ATLAS y dos vertices
                pegados pueden estar en orillas opuestas de una costura
              ⚠ el paso que hace viable convertir: FUSIONAR SIN MIRAR EL COLOR y
                pintar el detalle en la textura. 710 -> 286 cajas. Es como
                funciona Minecraft: el jugador de vainilla son SEIS CAJAS

Textos        ⚠⚠⚠ UNA CLAVE DE TRADUCCION QUE NO EXISTE SE VE CRUDA
              `Text.translatable("clave.que.no.existe")` NO DA NINGUN ERROR:
              ni al compilar, ni al arrancar, ni al dibujar. Minecraft pinta
              LA CLAVE, y el jugador ve `pokepad.lunaeternal.mercado.tu_plata`
              paso el 2026-08-25: al pasar los objetos a escaparate borre las
              claves del libro de ordenes, y una --el rotulo «TU PLATA»-- la
              seguia usando la pantalla del GTS. El unico aviso fue una captura
                python tools/comprobar_textos.py
              comprueba LOS DOS SENTIDOS (usadas que faltan, y las que sobran)
              y ademas ENTRE IDIOMAS: una clave que este en es_es y no en en_us
              deja al cliente en ingles viendo la clave cruda
              ⚠ los PREFIJOS dinamicos se distinguen POR LA FORMA --acaban en
                `.` o en `_`, porque el codigo los concatena-- y NO con una
                lista de excepciones: una lista hay que mantenerla, y acaba
                siendo el sitio donde se esconden los fallos de verdad

Idioma        ⚠⚠⚠ UN SERVIDOR NO TIENE IDIOMA, Y ESO ES UNA REGLA DEL
              PROTOCOLO (2026-08-25)
                EL SERVIDOR MANDA EL IDENTIFICADOR
                EL CLIENTE PONE EL NOMBRE
              `getName()` devuelve un Text TRADUCIBLE y viaja sin resolver;
              `.getString()` lo CONGELA en el idioma de quien lo llama -- y en
              el servidor solo existe `en_us`
              ⚠ el sintoma: «Black Stained Glass Pane» y «White Neon» con el
                cliente en español, Y CON LOS 607 NOMBRES DE lunaneon YA
                TRADUCIDOS. Nunca llegaron a consultarse
              ⚠ vale para CHAT tambien: `Text.literal(nombreYaResuelto)` sale
                en ingles; `Text.literal("Comprado ").append(pila.getName())`
                lo pinta el cliente en su idioma
              ⚠⚠ Y ARRASTRA AL BUSCADOR: el servidor solo puede buscar por lo
                 que tiene guardado, que esta en ingles. Quien escriba
                 «cristal» NO encontraria «Black Stained Glass Pane» jamas.
                 Por eso el buscador de objetos filtra EN EL CLIENTE, sobre los
                 nombres ya traducidos (y tambien por identificador)
                 ⚠ el precio: filtra sobre lo que el servidor manda, no sobre
                   todo. Con un escaparate pequeño da igual; con miles habra
                   que mandar el idioma del jugador en el paquete
              LA TIENDA NO LO SUFRIA, y por que importa: sus etiquetas se
              escriben A MANO en español en shop_catalog.json, asi que nunca
              dependio de que el servidor resolviera nada

Protocolo     ⚠⚠⚠ UN `writeString(null)` ECHA AL JUGADOR DEL SERVIDOR
              (2026-08-25) . revienta AL CODIFICAR, fuera del hilo del
              servidor, y el mensaje NO DICE QUE CAMPO:
                Failed to encode packet 'clientbound/custom_payload'
              paso de verdad: un Pokemon publicado SIN MOTE dejaba
              `display_name` nulo y abrir el GTS desconectaba
              ⚠⚠ HAY DOS FAMILIAS DE CODIFICADORES Y SE OLVIDA LA SEGUNDA:
                 los `escribir` a mano   ->  Red.cad()
                 los PacketCodec.tuple   ->  Red.CADENA
                 arregle la primera (39 sitios), di el fallo por cerrado, y
                 habia 96. Lo destapo el autotest, no una revision
              ⚠⚠ LA LECCION: UN REPASO A OJO ENCUENTRA LO QUE BUSCAS Y NO LO
                 QUE NO SABIAS QUE EXISTIA. Yo busque «writeString» --que es lo
                 que decia la traza-- y encontre todos los writeString. La otra
                 familia no salia porque no se llama asi.
                 Lo caza una prueba que EJERCITA EL CAMINO: `testProtocoloNulos`
                 codifica un paquete de verdad con TODO a nulo, y no sabe
                 cuantas familias hay ni le importa

Avisos        ui/Aviso.java centraliza como se anuncia un logro
              TOAST (esquina, con el marco de los logros de vanilla) +
              BARRA DE ACCION + CHAT + sonido, y las tres hacen falta:
                toast  se ve estes donde estes
                barra  sale donde ya miras al picar
                chat   PERSISTE: lo unico que se puede releer
              ⚠ EL TOAST SOLO LLEGA A QUIEN TIENE EL MOD. El chat y la
                barra son de vanilla, asi que quien no lo tenga se entera
                igual. Por eso no se manda solo el toast
              ⚠ CADA TOAST LLEVA SU PROPIO TIPO: con el de por defecto dos
                subidas seguidas se pisan y solo se ve la ultima
              ⚠ VIAJA EL TEXTO YA COMPUESTO, no las piezas: si no, el
                formato viviria en el servidor y en el cliente a la vez

PokePad       LA PANTALLA PRINCIPAL ESTA TERMINADA (2026-08-15)
              verificada en el juego por el usuario. Tecla B
              mod/src/client/ · lunaeternal vuelve a tener cliente
              (D-025) y su jar YA SE REPARTE a los jugadores
              CHASIS v4 (2026-08-16): la pantalla paso de AZUL OSCURO
              a CASI BLANCA, y la disposicion entera cambio
              chasis HD a pixeles reales + rejilla 5x3 + 15 iconos
              + cara + saldo + teclado de 6 botones 2x3
              el saldo se PIDE al abrir, no se empuja: primer trozo de
              protocolo. Los tipos de paquete van en el entrypoint
              `main`, el unico que corre en los dos lados
              celdas dibujadas por CODIGO, sin texturas (como ellos)
              ⚠⚠ EL v4 DA LA VUELTA A TODO LO DE DENTRO. No es un
                 retoque de tono, cada decision de contraste apuntaba
                 al reves:
                   celdas  eran MAS CLARAS que el fondo -> MAS OSCURAS
                   nombres eran BLANCOS con contorno negro -> OSCUROS
                           con contorno claro. Es la MISMA decision del
                           usuario ("que se lean") sobre un fondo que
                           se ha invertido: blanco sobre blanco no se
                           lee con contorno ni sin el
                   resalte  del ambar del chasis al NARANJA FUERTE
                            (F35C0C), el unico acento con contraste
                 por eso el color del texto y el de su contorno son DOS
                 CONSTANTES: cambiaron de golpe, y el negro escrito a
                 mano dentro de la funcion se habria quedado
              LOS BOTONES DEJAN DE SER UNA BARRA. En el v3 iban en la
              unica franja libre (981x58) y a 60x48 porque no cabia
              mas; el v4 no tiene esa franja --ahi estan la boca y los
              bigotes de Rotom-- pero trae TRES RANURAS de verdad:
                cara     181x182   la cabeza del jugador, a 168
                botones  249x208   teclado 2x3 de 80x64 (2/3 del arte,
                                   UN TERCIO MAS GRANDES que antes)
                saldo    180x68
              queda LIBRE un cuadradito de 48x48 en 302,651
              ⚠ LAS MEDIDAS SE MIDEN, NO SE ESCRIBEN NI SE ESCALAN.
                medir_pantalla() busca la mancha clara grande de la
                derecha (antes buscaba "azul de verdad": sobre el v4 no
                encontraba NADA) y medir_cajas() el gris de la moldura
                de cada ranura. El chasis ya ha cambiado de estructura
                CUATRO veces, y cada vez las medidas a mano se quedaron
                mintiendo EN SILENCIO: el codigo dibujaba la cara donde
                estaba en la version anterior sin que nada fallara
              medidas: tools/gen_pokepad.py las imprime al terminar
              la maqueta ensena UNA celda con el raton encima, que es
              el unico estado que no se puede juzgar de otra forma
              EL ARTE HD YA ESTA ENTERO (2026-08-14). 22 piezas
                 chasis 1380x828 · 15 iconos 100x100 · 6 botones 120x96
              se descarto el pixel art de 25x25 por dos motivos medidos:
                1) una IA no dibuja pixel art, entrega una
                   ILUSTRACION ENCOGIDA. Con los mismos 625 pixeles
                   los suyos tienen 9-15 colores y contorno negro
                   duro; los nuestros 387-471 y sin contorno
                2) no es la estetica que se quiere: el suyo es retro
                   deliberado, este servidor se quiere limpio
              1380x828 no es arbitrario: divisible entre 1,2,3,4,6,
              que son los GUI Scale posibles. Dibujando el Pad al
              tamano REAL de pantalla, un texel cae en un pixel sea
              cual sea el ajuste del jugador
              ⚠⚠ ANTES DE ESCRIBIR CUALQUIER PANTALLA NUEVA, LEE
                 docs/ui/dibujado.md. Son 6 reglas y ninguna da error
                 al compilar; se pagan en horas depurando en el juego
                 La primera, la que costo una noche entera:
                 HAY QUE ENCENDER RenderSystem.enableBlend() A MANO.
                 Sin eso el juego trata CUALQUIER alfa > 0 como opaco
                 --un pixel con alfa 1 sale a todo color-- y se ve
                 como motas de colores o como cerco negro alrededor
                 de cada icono, segun lo que el arte guarde debajo.
                 Parecen dos fallos y es uno. Y no esta en el arte:
                 se perdieron 3 diagnosticos buscandolo ahi
              barra de 6 botones bajo la pantalla (2026-08-15)
              60x48 --la mitad del arte-- porque el chasis NO tiene
              sitio: la unica franja libre mide 981x58 y el boton 96
              de alto. El prompt del chasis nunca pidio una barra
              solo CERRAR hace algo; los otros 5 van apagados y
              suenan a bloqueado, igual que las 15 celdas
              `atras` llego con un halo claro semitransparente y con
              la elipse de sombra de los iconos: las dos las prohibe
              §6. Se saca de `adelante` VOLTEADO, que es el mismo
              boton con la flecha al otro lado. Opcional regenerarlo
              algun dia con el prompt de §6, que ya lleva la linea
              `No shadow under the button`
              LO SIGUIENTE: la PRIMERA SUB-PANTALLA. Ahi se encienden
              los botones de navegacion (cambiar `CERRAR` por la
              lista de los que ya llevan a algun sitio) y hace falta
              un fondo por pantalla, que se pide con el prompt de
              §3.2 partiendo del chasis base
              docs/ui/prompts-arte-pokepad.md tiene TODOS los prompts
              build/pokepad/prompts-*.txt los tiene ya montados
Interfaz      revestida de azul luna · docs/ui/interfaz-luna.md
              323 texturas (211 KB), ACTIVADO solo
              python tools/gen_interfaz.py --comparativa
              ⚠ el TEXTO lo pinta el codigo de Cobblemon: 0x606B6E
                gris (16 usos) y 0x3A96B6 turquesa (3). Y el gris va
                sobre los paneles CLAROS, asi que la pantalla puede
                cambiar de tono pero NO oscurecerse: se volveria
                ilegible, y un resource pack no lo alcanza
              va INCRUSTADO en el jar de lunaneon (resourcepacks/) y
              se registra con ALWAYS_ENABLED. En el log del arranque:
              "Interfaz: revestido de luna activado"
              alcance: Pokedex y su ITEM (cian->azul), y en gris
              teñido: resumen, PC, combate, equipo, comercio,
              interaccion y pastos. Dos transformaciones porque hay
              dos familias: la Pokedex es cian y el resto es 100%
              GRIS, sin un pixel de color que desplazar
              la carcasa de la Pokedex NO se toca: hay 7 colores y
              los elige el jugador
              ⚠ UNA TEXTURA ANIMADA SE LLEVA SU .mcmeta O DEJA DE
                ESTARLO. Minecraft lo busca en el MISMO pack que
                sirvio la textura, no en el de debajo. Sin el, una
                imagen de N fotogramas apilados pasa a ser una sola
                alta y estrecha. Se vio en el ITEM de la Pokedex:
                su pantalla es 16x48 (3 de 16x16) y el modelo, que
                espera 16x16, mapeaba todo al tercio de arriba --
                pantalla diminuta y caras sin textura. Hoy son 2 de
                323 y gen_interfaz.py los copia siempre que existan
              ⚠ DOS intentos fallidos antes, los dos MUDOS:
                1) .zip suelto + linea en config/yosbr/options.txt.
                   YOSBR copia esa plantilla solo si options.txt no
                   existe: nunca para quien ya ha jugado
                2) DEFAULT_ENABLED. El javadoc de Fabric lo dice:
                   "a resource pack cannot be enabled by default,
                   only data packs can". Se registraba y se quedaba
                   apagado — y no se veia porque yo IGNORABA el
                   booleano que devuelve registerBuiltinResourcePack
Publicar      YA NO QUEDA NI UN FICHERO SIRVIENDOSE DESDE raw (D-036)
              eran 5, y el manifiesto era uno de ellos: LA PRIMERA
              peticion del arranque, en el peor sitio posible
              hoy el launcher pide un PUNTERO de 250 bytes
                releases/pack-manifest/latest.json   <- lo unico mutable
                    -> manifest-<huella>.json        <- inmutable, no se toca
              VOLVER ATRAS = SUBIR 250 BYTES:
                python tools/gen_manifest.py --volver-a <huella>
              antes era regenerar y republicar 185 MB con el pack roto
              cada fichero lleva `urls[]` y no una url suelta: un 4xx
              es definitivo para ESE ORIGEN, no para el fichero
              ⚠ el launcher VERIFICA el sha1 del manifiesto antes de
                fiarse. Ese fichero elige de que URL salen los 185 MB
                que se ejecutan en la maquina del jugador
              ⚠ `url` singular SE MANTIENE ademas de `urls`: los
                launchers 1.0.x leen ese campo, y quitarlo dejaria sin
                pack a quien no puede actualizarse porque el pack no le
                baja. Se retira cuando no quede nadie en 1.0.x
              detalle en docs/technical/distribucion.md
              ---- historico, sigue siendo cierto -------------------
              ⚠⚠ EL PACK NO SE SIRVE DESDE raw.githubusercontent.
              raw NO es un CDN de distribucion: LIMITA POR PETICIONES y
              contesta 429 y 503. Con 117 de las 199 entradas ahi, quien
              instalaba de cero se llevaba "HTTP 429 en manifest.json"
              ANTES DE EMPEZAR -- y a quien ya lo tenia bajado no le
              pasaba nada, porque no pedia nada. De ahi el "a mi me
              funciona y a ellos no", que costo media manana
              lo nuestro va a una RELEASE (CDN de descargas, sin limite):
                110 plantillas de YOSBR -> UN zip de 56 KB
                nuestros dos jars       -> activos de la release
                entradas  199 -> 90     peticiones a raw  117 -> 5
              se pueden empaquetar porque las plantillas NO son la
              configuracion del jugador: son de las que YOSBR copia
              cuando el fichero de verdad no existe. iris.properties y
              openloader se quedan sueltos y `once`, que esos si son
              configuracion viva
              ⚠ LA RELEASE VA COMO PRERELEASE. El autoactualizador mira
                "la ultima release" y una normal le haria creer que hay
                launcher nuevo; allowPrerelease solo se enciende en
                alpha/beta/rc
              ⚠ EL REPO DEL PACK LLEVA .gitattributes CON `* -text`.
                Sin el, git convierte CRLF a LF al hacer commit y el
                manifiesto MIENTE: anuncia el tamano y el sha1 del
                fichero local y el CDN sirve otro mas corto. Eran 24
                configs (options.txt: 8.150 aqui, 7.921 alli) que el
                launcher rebajaba EN CADA ARRANQUE. El CRLF viene del
                pack de Cobblemon, no lo metemos nosotros
                poner el fichero NO BASTA: los blobs ya guardados siguen
                normalizados, hay que `git rm --cached -r .` para que se
                relean. Lo hace gen_manifest.py en cada publicacion
              ⚠ nuestros jars llevan la HUELLA en el nombre
              (lunaneon-0.1.0-3598884202.jar). Contenido nuevo = URL
              nueva, que nunca ha estado en cache
              ⚠ --publicar NO dice "publicado" hasta que el CDN sirve de
                verdad lo subido: empujar al repo no es publicar. Compara
                el JSON y NO los bytes -- git normaliza finales de linea
                y una comparacion byte a byte no acierta jamas
Shaders       INSTALADOS y APAGADOS · client-pack.md §2-quater
              Iris + EuphoriaPatcher + Complementary Unbound r5.8.1
              + MakeUp Ultra Fast (el tier ligero)
              el mismo aspecto que CobbleVerse, por la via oficial:
              Euphoria llega como PARCHEADOR y se genera en el PC
              del jugador, que es lo unico que permite su licencia
              ⚠ NO se copian ni se renombran los packs de CobbleVerse.
                Son Complementary + Euphoria renombrados, y su
                licencia lo prohibe (§1.3.d, §1.2.d, §2.1)
              ⚠ SODIUM BAJO A 0.6.13 y es correcto: Iris estable
                para 1.21.1 exige 0.6.x y se niega a arrancar fuera
                de rango. El numero NO esta escrito a mano: se lee
                del jar de Iris. §2-quinquies
Launcher      EL QUE USA LA GENTE ES EL FORK QT. Ya no es "el nuevo"
              ---------------------------------------------------------
              LA PANTALLA DEL JUGADOR (2026-08-23) . launcher-qt.md §14
              VERIFICADA EN LA VENTANA . v0.2.3
              la ventana deja de ser la rejilla de instancias de Prism:
                PERFILES a la izquierda (Jugador / Constructor)
                el logo centrado
                un boton JUGAR grande, y es la accion de la pantalla
                tarjeta del servidor: si esta en linea y que pack tienes
              ⚠⚠ CORRIGE UN RUMBO: se enseño una maqueta con esta
                 disposicion y se entrego SOLO el tema sobre la ventana de
                 Prism. El alcance decia "sin tocar la estructura de
                 ventanas", pero la maqueta se dibujo ignorando eso.
                 Enseñar una imagen y entregar otra cosa es un fallo
              ⚠ SE OCULTA, NO SE BORRA: `view` sigue creado con su modelo
                y sus conexiones. Borrarlo obligaria a desenredar medio
                MainWindow y a mantenerlo en cada merge con upstream
              ⚠ las barras de la derecha se ocultan DESPUES de
                setVisibilityState: instanceToolBar es un WideBar y
                RESTAURA SU PROPIA VISIBILIDAD desde los ajustes
              ⚠⚠ "Comprobando..." TIENE QUE RESOLVERSE. Un estado que se
                 queda ahi para siempre es peor que no enseñar nada: el
                 jugador no sabe si el servidor esta caido o si el
                 launcher se ha colgado
              ⚠ NO ES UN PING DE MINECRAFT y no se vende como tal. Es una
                conexion TCP: si el puerto acepta, hay algo escuchando. Es
                menos informacion pero es VERDADERA -- enseñar "12
                jugadores" inventado seria peor que no enseñar nada
              ⚠ hace falta un reloj de 4 s: un puerto filtrado por un
                cortafuegos NO RECHAZA, se calla, y Windows esperaria ~20 s
              ⚠ si falla el manifiesto se dice "sin conexion" y NO
                "servidor caido": lo que falla entonces es la red del
                jugador o nuestro CDN
              ⚠⚠ Y MATO EL LAUNCHER AL ARRANCAR: el texto del pie se puso
                 donde `m_statusLeft` TODAVIA NO EXISTE (se crea mas abajo
                 en el mismo constructor). El log terminaba en "applying
                 catpack" sin decir una palabra del motivo
              lo que queda del modo quiosco: el navegador de mods, al que
              todavia se llega desde "Editar"
              ---------------------------------------------------------
              ⚠⚠⚠ COMPILAR ESTO TUMBA EL PC SI NO SE LIMITA (2026-08-23).
                 Paso de verdad y hubo que apagar a lo bruto.
                 Ninja lanza UNA TAREA POR NUCLEO. En la maquina del
                 usuario son 16 nucleos y 13,7 GB de RAM, y al llegar a
                 los 27 EJECUTABLES DE PRUEBA --cada uno enlazado por
                 separado-- eso son 17 `link.exe` a la vez. El enlazador
                 de MSVC pide 1-2 GB CADA UNO: 17-34 GB sobre 13,7
                 ⚠ NO SE MANIFIESTA COMO UN ERROR. Se manifiesta como
                   "esto va lentisimo" y despues como un equipo
                   congelado, que no manda a nadie a mirar el build. Los
                   17 link.exe se llegaron a VER y se leyeron como "va
                   bien, solo tarda"
                 ARREGLADO EN build-launcher.ps1: el tope se calcula de la
                 RAM TOTAL y no de los nucleos --lo que se agota es la
                 memoria--, un cuarto de la RAM en GB, minimo 2. En esa
                 maquina son 3 en vez de 16
                 ⚠ hay `-Trabajos N` para forzarlo, pero NO SE SUBE
                   "porque tienes nucleos"
                 ⚠ y se compila SOLO LO AFECTADO con `-Solo`: se estaban
                   compilando las 29 suites para verificar cambios que
                   tocan dos ficheros
              ---------------------------------------------------------
              DIAGNOSTICO Y REPARAR (2026-08-23) . launcher-qt.md §12
              eran LO UNICO que el fork habia perdido respecto al de
              Electron. Ya las tiene las dos
              ⚠ REPARAR NO ES "ACTUALIZAR OTRA VEZ", y por eso es una
                accion aparte: actualizar SE FIA del estado guardado, asi
                que un fichero que se corrompio DESPUES de instalarse
                sigue apuntado como correcto y sobrevive a CUALQUIER
                numero de actualizaciones. Reparar recalcula el sha1 de
                todo lo que hay en disco
                el motor YA lo sabia hacer (Luna::Mode::Repair, probado
                desde que se escribio LunaSync). Faltaba el boton
              ⚠ va al MENU y no a la barra: es la accion de cuando algo va
                mal, y en la barra invitaria a pulsarla sin motivo
              CADA REGLA DEL DIAGNOSTICO ES UN FALLO REAL: jar corrupto,
              sin memoria, mods que no encajan, Java equivocado, dos mods
              que se pisan, la grafica, la conexion
              ⚠ EL REGISTRO MANDA SOBRE EL CODIGO DE SALIDA. Un juego
                puede morir con codigo 1 por cualquier cosa; si el log
                dice la causa, esa es la que se enseña
              ⚠ PERO EL CODIGO HACE FALTA IGUAL: los dos cierres de golpe
                de Windows (0xC0000005 y 0xC0000409) matan el proceso sin
                que Java escriba UNA SOLA LINEA. Por eso se plumbeo
                LaunchTask::setExitCode
              ⚠⚠ EL FALSO POSITIVO QUE SE HEREDA, y es la prueba que mas
                 vale: el patron de la grafica llevaba `OpenGL 3.2` A
                 SECAS, y Minecraft escribe esa cadena en una linea de
                 EXITO ("Supports OpenGL 3.2.0"). Saltaba en CADA arranque
                 correcto. UN DIAGNOSTICO QUE SE EQUIVOCA SIEMPRE ES PEOR
                 QUE NO TENERLO: enseña a ignorar los avisos, y el dia que
                 haya uno de verdad tampoco lo van a leer
              ⚠ el aviso va POR SEÑAL: Application no tiene ventana donde
                colgar un dialogo y el boton de Reparar vive en MainWindow.
                Y `Accion` viaja COMO INT -- la conexion puede ser en cola
                y un enum sin registrar se pierde SIN DAR NINGUN ERROR
              ⚠ el orden importa en controllerFinished: se diagnostica
                ANTES de `extras.controller.reset()`, que se lleva la
                tarea de la que sale el codigo de salida
              ---------------------------------------------------------
              EL TEMA DE LA CASA (2026-08-23) . launcher-qt.md §11
              VERIFICADO EN LA VENTANA, con instalacion aislada
              LOS COLORES SALEN DEL LOGO, no de un gusto: se muestrearon
              los dos PNG que mando el usuario
                #FFC420 oro       LO UNICO que dice "pulsame"
                #FFE04F oro claro el amarillo alto de las letras
                #F86800 naranja   el borde del rotulo y las llamas
                #5C1210 granate   la banda de detras de las letras
                #E8189B magenta   el neon de la ball. SELECCION
              EL ORO ES INVITACION Y EL MAGENTA ES ESTADO, por eso no
              compiten: con el mismo color, pasar el raton por una lista
              parece que va cambiando lo que tienes elegido
              ⚠ los grises tiran a CALIDOS: sobre un gris azulado el oro
                vira a mostaza y el logo deja de pegar con su interfaz
              ⚠ EL ORO Y EL AMBAR DE AVISO SON LA MISMA FAMILIA, y es el
                precio de que la marca sea dorada. Se separan POR FORMA:
                la accion principal es un DEGRADADO con texto oscuro; el
                aviso es PLANO y siempre con icono. No hay botones de aviso
              ⚠ la tipografia del logo NO se usa en la interfaz: es de
                pixeles y a 13 px seria ilegible. Vive en el rotulo
              EL QSS ES UN FICHERO, no un literal de C++: el de Freesm va
              concatenado en ~40 trozos dentro del .cpp y cambiar un color
              aparece en el commit como una linea de 4.000 caracteres
              ⚠⚠⚠ Y HUBO QUE INICIAR EL RECURSO A MANO. Launcher_logic es
                 una libreria ESTATICA: el enlazador tira el inicializador
                 que genera `rcc` porque no lo referencia nadie, asi que la
                 hoja NO ESTABA en el binario -- sin un solo error de
                 compilacion, y la ventana salia con el gris de Qt
                 Y NO SIRVE PONERLO EN main.cpp con los demas, que es lo
                 primero que uno prueba: esa lista corre DESPUES de
                 construir Application, y el tema se aplica DENTRO de ese
                 constructor
                 lo que hizo que costara minutos y no una tarde fue el
                 qWarning escrito para ese caso: nombro el fichero Y el
                 CMakeLists. Devolver cadena vacia en silencio habria
                 mandado a buscar el fallo en los colores
              ⚠⚠ EL FONDO POR DEFECTO ERA UN MEME DE TYPESCRIPT heredado
                 de Freesm, y ocupa la PANTALLA ENTERA: es lo primero que
                 se ve al abrir. Ahora la ball y el rotulo al 40% de alfa
                 ⚠ CatPainter lo ancla ABAJO A LA DERECHA: con 20 px de
                   margen el rotulo salia CORTADO. Son 90
              ⚠⚠ "Ciudadela" y "-luna" se colaban en la BARRA DE TITULO, y
                 salian de dos sitios que nadie tenia fichados: el nombre
                 en clave de la version y el CANAL, que es el nombre de la
                 rama de git. Orden del usuario: solo la marca y el numero
                 hoy el titulo es `PokeReport Network 0.2.0`; la version
                 completa sigue en "Acerca de" y en el actualizador
              ⚠ SOLO LO VE QUIEN NO TENGA AJUSTES TODAVIA. registerSetting
                pone el valor POR DEFECTO y a quien ya lo haya abierto se
                le respeta el tema guardado (correcto: nadie quiere que le
                cambien la interfaz al actualizar). Para verlo hay que
                entrar UNA VEZ a Ajustes > Apariencia
              el arte de origen VA AL REPO (program_info/*.source.png) y
              los tres PNG derivados se generan: launcher/resources/
              pokereport/COMO-SE-GENERAN.md
              PokeReportTheme 7/7 . LunaInstance 9/9 . Version 62/62
              ---------------------------------------------------------
              EL BUCLE DE ACTUALIZACION, CERRADO (2026-08-23)
              detalle completo en docs/technical/launcher-qt.md §10
              el sintoma eran DOS cosas a la vez, en cada arranque:
              avisaba de version nueva TENIENDO YA LA ULTIMA, y al
              pulsar Actualizar daba error. Y eran TRES fallos, todos
              nacidos DEL MISMO CARACTER: la `v` de las etiquetas
              ⚠⚠⚠ `Version("v0.2.0")` ES MAYOR QUE `Version("0.2.0")`.
                 SIEMPRE, mire los numeros que mire. Version parte la
                 cadena en tramos: el primero de `v0.2.0` es la letra
                 `v` (TEXTO) y el de `0.2.0` es el `0` (NUMERO), y con
                 tipos distintos se comparan POR CODIGO DE CARACTER --
                 0x76 contra 0x30. Ahi se acaba la comparacion, sin
                 llegar a mirar un solo digito:
                   Version("v0.1.0") > Version("9.9.9")  es CIERTO
                 hoy `Version::fromTag()`, y la `v` se quita SOLO para
                 comparar: el dialogo sigue enseñando la etiqueta real
              ⚠⚠ EL INSTALADOR NO SE LLAMABA COMO EL ACTUALIZADOR
                 BUSCA. NSIS lo saca de `Launcher_CommonName`
                 (`LunaEternal-Setup.exe`) y el actualizador solo acepta
                 ficheros que CONTENGAN `Launcher_BUILD_ARTIFACT`
                 (`luna-launcher-win-x64`). Los dos nombres correctos
                 por separado, y sin una letra en comun: la release se
                 publicaba entera y bien, y al aceptar la actualizacion
                 NO HABIA NI UN FICHERO QUE INSTALAR
                 ⚠ el nombre no es libre: el mismo filtro descarta
                   `portable`, `.zip`/`.tar.gz`, `arm64` y `-qt<n>`.
                   `x64` si vale, lo que descarta es `arm64`
              ⚠ Y EL LAUNCHER SE LLAMABA A SI MISMO `0.2.0-1a2b3c4d`:
                misma `v` por el otro lado. `GIT_TAG` (`v0.2.0`) nunca
                era igual a `versionString()` (`0.2.0`), asi que se le
                pegaba el canal detras
                ⚠⚠ CORREGIDO EL 23-ago LEYENDO EL LOG DE LA CI: aqui
                   ponia que le pasaba a TODAS las que se reparten, y ES
                   FALSO. En la CI el refspec sale VACIO, y eso dispara
                   el respaldo "assume stable" de BuildConfig, que
                   sobrescribe GIT_TAG y pone el canal en `stable`: la
                   version sale limpia. Solo pasaba en las
                   compilaciones LOCALES, que van desde una rama
                   el arreglo sigue siendo correcto, pero NO era uno de
                   los que afectaban al jugador. Esos son los otros dos
              LO QUE IMPIDE QUE VUELVA, y es lo que importa:
                Version_test  62 pruebas, 0 fallos (eran 60)
                CI            aborta si CMakeLists.txt y la etiqueta no
                              dicen lo mismo -- y aborta ANTES de
                              compilar 35 min
                CI            aborta si el instalador no lleva
                              ARTIFACT_NAME en el nombre
              ⚠ SIN VERIFICAR DE PUNTA A PUNTA. Compila y las pruebas
                pasan, pero el ciclo de verdad --release nueva, launcher
                viejo, actualizar-- solo se comprueba PUBLICANDO, y hace
                falta subir a 0.2.1: contra v0.2.0 el launcher arreglado
                dira, con razon, que no hay nada que actualizar
              ---------------------------------------------------------
              EL QUE SE REPARTE:  Qt 0.2.0 "Ciudadela" (2026-08-21)
                D:\luna-launcher · rama `luna`
                repo PUBLICO github.com/corderovibes-collab/luna-eternal-launcher
                datos en %APPDATA%\LunaEternal  <- SIN PUNTO
              EL VIEJO:           Electron 1.1.1
                launcher/ · 39 pruebas · datos en %APPDATA%\.lunaeternal
                                                            ^ CON PUNTO
              ⚠⚠ BORRAR LA CARPETA EQUIVOCADA CUESTA 450 MB DE DESCARGA.
                 Se diferencian SOLO en un punto
              ---------------------------------------------------------
              0.2.0 ARREGLA LO QUE HACIA ABANDONAR LA INSTALACION
              PUBLICADA Y DESCARGABLE (2026-08-21). CI verde, 13 pasos,
              35 min -- la PRIMERA vez que ese flujo llega al final
                .../luna-eternal-launcher/releases/download/v0.2.0/
                LunaEternal-Setup.exe   ·   27,1 MB
              Tres fallos, ninguno el que parecia. Detalle completo en
              docs/technical/launcher-qt.md §8
                1) LAS DESCARGAS NO REINTENTABAN NI UNA VEZ. 157 de los
                   159 ficheros tienen UN SOLO origen, asi que
                   `MultipleOptionsTask` no era failover de nada, y
                   `makeFile` no enciende `AutoRetry` (que ademas solo
                   cubre el 429). Con un 2 % de fallo por fichero
                   --wifi domestico normal-- la instalacion COMPLETA
                   fallaba el 96 % de las veces
                2) el error decia "Multiples subtareas fallidas" y nada
                   mas: ni fichero, ni servidor, ni codigo
                3) ⚠ EL INSTALADOR NO LLEVABA EL RUNTIME DE VISUAL C++.
                   El CMakeLists lo excluye a proposito (herencia de
                   aguas arriba). Sin el, en un Windows limpio el doble
                   clic NO HACE NADA: ni ventana, ni error, ni log
              y de propina, `--launch` --el acceso directo que ofrece
              Prism-- se saltaba la sincronizacion entera
              ⚠ LOS REINTENTOS NO SE HAN PROBADO CONTRA UN FALLO REAL.
                Se estrenan en la maquina de un jugador
              ⚠ EL AVISO DE VISUAL C++ TAMPOCO: hace falta un Windows
                limpio, y el de desarrollo ya tiene el runtime
              ⚠⚠ LA VERSION ESTA ESCRITA A MANO EN CMakeLists.txt Y LA CI
                 NO LA SACA DEL TAG. Publicar `v0.2.0` con un 0.1.0
                 dentro deja al autoactualizador en BUCLE: actualiza,
                 sigue viendo la vieja, vuelve a actualizar. Subir de
                 version = tocar el numero Y empujar su etiqueta
              ⚠ `release.yml` (el de Freesm) se disparaba con CUALQUIER
                etiqueta y lanzaba su matriz entera con secretos que no
                tenemos. En v0.1.0 arranco y fallo. Pasa a manual
              ---------------------------------------------------------
              EL FORK YA FUNCIONA DE PUNTA A PUNTA (2026-08-20)
              verificado desde cero: pide el nombre -> crea la instancia
              -> baja 135 mods, 109 configs, servers.dat -> arranca
              instalador 26 MB (el de Electron son 96)
              CI en verde: `git tag v0.2.0 && git push origin v0.2.0`
              compila y cuelga el instalador de la release
              motor en launcher/luna/ · 7 piezas · 65 pruebas propias
                LunaConfig · LunaFetch · LunaManifest · LunaInstance
                LunaSync · LunaDownload · LunaApply · LunaUpdate
              compilar SOLO lo que toca (34 s en vez de minutos):
                powershell tools/build-launcher.ps1 -Solo LunaEternal
              instalador COMPLETO en 2,4 min:
                powershell tools/build-launcher.ps1 -Instalador -SinPruebas
              ⚠⚠ `-Publicar` NO CAMBIA NADA EN MSVC, y esperarlo es tirar
                 el tiempo. `/GL` y `/LTCG` se aplican SIEMPRE en Release
                 (CMakeLists.txt:48-60), fuera de `if(ENABLE_LTO)`:
                 medido, con LTO ON y OFF sale el MISMO exe de 16,4 MB
                 en ~2,5 min. Lo que hacia eterna la compilacion eran
                 los 27 EJECUTABLES DE PRUEBA, cada uno enlazado con
                 LTCG. `-SinPruebas` bajo el ciclo de ~15 min a 2,4
              ⚠ y entonces hay que ACORDARSE de correrlas: saltarselas
                al empaquetar esta bien, saltarselas siempre es como no
                tenerlas. Se corren al tocar launcher/luna/
              ⚠ TOOLCHAIN en .toolchain/ (git-ignorado, ~4,5 GB):
                Qt 6.10.2 · vcpkg · JDK 17 · NSIS 3.11 · Python 3.12
                MSVC va al sistema (Build Tools 2022, trae CMake+Ninja)
                receta en docs/technical/launcher-qt.md §9
                ⚠ EL JDK TIENE QUE SER 17, NO 21: Prism compila el
                  NewLaunch.jar con `-source 7` y JDK 20 lo elimino
                ⚠⚠ PERO EL MOD EXIGE JDK 21 (MC 1.21.1), ASI QUE HACEN
                   FALTA LOS DOS. NO son intercambiables y estan en
                   sitios distintos:
                     launcher Qt  .toolchain/jdk        JDK 17
                     mod          Eclipse Adoptium 21   JDK 21
                   winget install EclipseAdoptium.Temurin.21.JDK
                 ⚠⚠ LA BUSQUEDA DEL JDK 21 ESTABA DUPLICADA, y por eso el
                    MISMO fallo se arreglo DOS VECES con seis dias entre
                    medias. mod/build.sh y neon/build.sh llevaban la ruta
                    escrita a mano --uno con la revision dentro
                    (`jdk-21.0.12.101-hotspot`), el otro apuntando al JDK
                    de PrismLauncher--, y el formateo del 2026-08-20 se
                    llevo las dos carpetas. Se arreglo el de `mod` en
                    cuanto fallo; el de `neon` siguio roto hasta que toco
                    compilarlo. El error decia "no hay JDK 21 en <ruta>",
                    que suena a que falta Java cuando lo que faltaba era
                    ESA revision
                    hoy los dos leen tools/jdk21.sh y aceptan cualquier
                    revision: .toolchain/jdk21, Adoptium o Java
              ⚠ el enlazado falla con LNK1104 si el launcher esta ABIERTO
              ⚠ QtTest en Windows NO escribe por la tuberia: hace falta
                `-o fichero,txt`. Un `exit 0` mudo NO significa que no
                haya corrido nada
              ⚠⚠ TODO EL DETALLE EN docs/technical/launcher-qt.md
                 -- decisiones, siete trampas de la cadena de
                 herramientas, y por donde seguir. LEERLO ANTES DE TOCAR
              ✅ MARCA-001 APLICADA (2026-08-23): el servidor se llama
                PokeReport Network. Detalle en launcher-qt.md §7
                ⚠⚠ EL AVISO QUE HABIA ESTABA EN LA VARIABLE EQUIVOCADA:
                   lo que mueve la carpeta de datos NO es `Launcher_AppID`
                   sino `Launcher_CommonName` --Application.cpp hace
                   setOrganizationName(LAUNCHER_NAME)--, y eso cambia el
                   trabajo entero, porque son DOS variables distintas:
                     DisplayName  TODO lo que el jugador ve. CAMBIADO
                     CommonName   %APPDATA%, carpeta, registro. IGUAL
                   asi que nadie vuelve a descargar 450 MB
                ⚠⚠ PERO EL NOMBRE DE LA INSTANCIA SI HABIA QUE MIGRARLO:
                   findInstance() la busca POR NOMBRE, asi que cambiarlo a
                   secas le crea una SEGUNDA instancia a quien ya la tenga
                   --otros 450 MB, y la suya al lado con pinta de perdida.
                   Hoy `instanceNamesAntiguos()` y findInstance() RENOMBRA
                ⚠ y el instalador borra el acceso directo viejo: se llama
                  como el DisplayName, o sea que NO se sobrescribe solo
                ⚠ los 15 `tr("Luna Eternal")` leen ya del BuildConfig. Un
                  nombre a mano en 15 sitios es un renombrado que se queda
                  a medias SIN DAR NINGUN ERROR
                fuera del fork: gen_modpack.py (servidor y mrpack), el mod
                (LunaEternal.NOMBRE y PREFIJO, en UN sitio), neon y el
                launcher de Electron
                ⚠ MOD_ID NO se toca: es identidad de registro, datapacks y
                  assets. Cambiarlo rompe el mundo guardado
                ✅ DESPLEGADO (2026-08-23, LOS DOS DESTINOS):
                  servidor  mod + neon subidos . Done (9,377 s)
                            autotest 217/217 tras el renombrado
                  clientes  manifiesto 443f2cff9c publicado y servido
                            servers.dat con «§6PokeReport §bNetwork»
                  ⚠ se aviso a los 2 jugadores conectados antes de
                    reiniciar. Un mod nuevo NO se carga en caliente
                  ⚠ queda una linea de log del servidor que dice
                    "Interfaz: revestido de luna activado". NO la ve
                    ningun jugador; se cambia cuando toque recompilar
              lo que el fork NO tiene todavia: el BOTON GRANDE DE JUGAR.
              Es lo ultimo del modo quiosco y es un rediseño de la
              ventana, no un ajuste. Diagnostico y reparar YA los tiene
              (2026-08-23), asi que ya no va por detras del de Electron
              ---- el de Electron sigue siendo el que se reparte -----
              launcher/ · Electron 43 · 1.0.2 · 37/37 (npm test)
              ⚠⚠ EL FETCH DE NODE NO TIENE TIEMPO LIMITE POR DEFECTO.
                 Si la conexion se queda a medias, la promesa no se
                 resuelve NI SE RECHAZA: el jugador ve "Comprobando
                 actualizaciones" clavado, sin error y sin salida. Y esa
                 es la PRIMERA peticion del arranque. Hay 30 s de corte
                 que cubren solo la espera a la cabecera --el reloj se
                 para en cuanto contesta, asi que un fichero de 130 MB no
                 se corta por tardar en bajar
              ⚠ y se rendia demasiado pronto ante un 503: eran 4
                reintentos con tope de 8 s (~7 s en total). Ahora 8 con
                tope de 30 s, mas de dos minutos de margen
              ⚠ una prueba baja el manifiesto EN VIVO y exige que
              nada bajo config/ shaderpacks/ resourcepacks/ pise
              lo del jugador: va marcada `once` o falla. Ya cazo
              un fallo real. Ojo: si GitHub esta estrangulando, esa
              prueba da rojo por 429/503 y NO es culpa del codigo
              PUBLICADO: .../luna-eternal-pack/releases/latest
              se autoactualiza SOLO (electron-updater) y el pack tambien
              dos perfiles en un solo .exe: Jugador · Constructor
              reparar instalacion + diagnostico de por que se cerro
              ⚠ NO publicar otras releases NORMALES en ese repo: el
                actualizador mira "la ultima release" y se perderia. Las
                del pack van como PRERELEASE justo por eso
              ⚠ SU NUCLEO ESTUVO 100% FUERA DE GIT hasta 2026-08-13:
                .gitignore tenia `core` a secas (por los volcados de
                la JVM) y se tragaba launcher/src/main/core/ entero,
                17 ficheros. Corregido a `/core` + `core.[0-9]*` y
                commiteado en 72a7de3. La leccion: una regla de
                .gitignore sin anclar casa a CUALQUIER profundidad,
                y ya habia mordido antes (world-structure.md)
Cliente       (respaldo) mrpack jugador 185 MB · constructor 233 MB
              Fabric Loader 0.19.3 (Cobblemon exige >= 0.17.2)
Dimensiones   lobby · ciudadela · salvaje · gimnasios
              (+ overworld = Mundo Hogar)
Generaciones  Kanto + Johto activas · 608 spawns apagados por datapack
              Y LA POKEDEX TAMBIEN, desde el 2026-08-22 (antes NO)
              docs/pokemon/generations.md §3-ter y §4
              python tools/gen_generaciones.py  ->  world/datapacks/
              ⚠⚠ NO BASTA CON LEER vendor/cobblemon: HAY QUE MIRAR
                 DENTRO DE LOS JARS. El generador solo leia el fuente
                 clonado, y con CobbleVerse (D-037) entraron mods que
                 meten spawns SUYOS en el MISMO namespace
                 `data/cobblemon/spawn_pool_world/`:
                   mega_showdown        24  Castform, Rotom, Rockruff,
                                            Orbeetle, Duraludon...
                   cobblemon-additions   1  Hatenna, Liepard, Purrloin
                 29 especies de Gen 3-8 aparecieron SEIS DIAS en un
                 servidor que se anuncia Kanto+Johto, y nada aviso: el
                 datapack se genera igual de bien, solo que incompleto
                 hoy el script ABORTA si no encuentra la carpeta de mods
                 en vez de publicar un datapack con agujeros
              ⚠ LA POKEDEX SE VACIA, NO SE BORRA. Un datapack no puede
                eliminar un fichero, y la interfaz lista TODAS las dex
                cargadas sin filtrar las vacias (PokedexGUI.kt:173), asi
                que quedan 9 pestañas de region VACIAS. Es lo unico que
                se puede hacer desde datos
                national -> solo agrega kanto y johto
              ⚠ LAS 23 EVOLUCIONES QUE CRUZAN SI ENTRAN EN LA POKEDEX,
                en la dex de su PREEVOLUCION (ursaluna en Johto, porque
                ursaring es de Johto). Kanto 151->162, Johto 100->112.
                Sin eso, quien consiguiera un Ursaluna tendria un
                Pokemon que la Pokedex no reconoce: parece un fallo y no
                una recompensa. Las evoluciones NO se bloquean (ver el
                docstring del generador: son el contenido mas dificil)
              ⚠ SIN VERIFICAR EN EL JUEGO. `/checkspawn` exige un
                jugador conectado; desde consola solo consta que el
                datapack carga sin errores. Es el mismo PKM-004 de
                siempre
Interfaz      TRECE PANTALLAS. Nueve verificadas en el juego:
                PokePad     2026-08-16   la principal, 15 iconos
                Cosmeticos  2026-08-22   4 pestanias
                Trabajos    2026-08-23   8 Vias y oficios, paginado
                Misiones    2026-08-23   arbol de 28 en 6 cadenas
                Inicial     2026-08-23   se abre SOLA al entrar
                Clan        2026-08-23   verificado con 2 cuentas
                Tienda      2026-08-23   SIN VERIFICAR . 9 articulos
                Curar       2026-08-23   la 16a celda, pagina 2
                GTS         2026-08-25   Pokemon: 3D, tasador, filtros
                Mercado     2026-08-25   Objetos: escaparate (D-042)
                Cazas       2026-08-25   2 pestañas, 3+3
                Mochila     2026-08-27   7 filas por rango . CONTENEDOR
                Explorar    2026-08-27   2 mundos, arte 768x512
                Viajes      2026-08-27   7 paradas . rejilla 4x2
                Kits        2026-08-28   trajes de rango . 3D
                Gimnasio    2026-08-29   el dialogo del lider . 8 medallas
              11 de los 16 iconos abren algo: pokedex (la de Cobblemon),
              cosmeticos, trabajos, misiones, clan, tienda, curar, mercado,
              cazas, explorar y viajes
              YA NO QUEDA NINGUNA con logica viva y sin pantalla:
                `kits` era la ultima y hoy es la de TRAJES
              ⚠ el MERCADO refresca al VENDEDOR cuando le compran, sin que el
                pulse nada: su oferta desaparecio y su dinero subio. Es la
                leccion de los clanes aplicada antes de que doliera
              ⚠ CURAR YA ESTA (2026-08-23, tarde). Ver el bloque Curar
Cazas         YA TIENE PANTALLA (2026-08-25, V017)
              2 pestañas (CAZA . CRIANZA) . 3 objetivos en cada una con
              1, 2 y 3 ESTRELLAS . mismas para todo el servidor
              solo captura las avanza; crianza cuenta al ECLOSIONAR
              ⚠⚠ CASI TODO ESTABA HECHO DESDE PHASE 5 y no lo sabia ni yo:
                 HuntService ya sorteaba 3+3, ya derivaba la rareza DE LA
                 POSICION --para que cada ciclo tenga uno facil, uno medio y
                 uno dificil-- y ya filtraba legendarios por etiqueta. Faltaba
                 la pantalla. Mismo patron que la tienda y que el inicial:
                 logica esperando una puerta
              ROTAN CADA 24 H, no 12 (orden del usuario). Con 12 cambiaban DOS
              VECES AL DIA y quien juega por la tarde no veia nunca la del
              ciclo de noche -- lo contrario de lo que hace que se hable de ella
              ⚠ NO HAY RELOJ: `cicloActual` crea el ciclo si el anterior caduco,
                asi que la rotacion la provoca la primera persona que mira
              PREMIOS EN OBJETOS (V017):
                captura  ★ 500 Plata + 5 Poke Ball
                         ★★ 1.200 + 5 Great Ball
                         ★★★ 2.500 + 3 Ultra Ball
                crianza  ★ 1.000 + 3 Exp. Candy S
                         ★★ 2.000 + 2 Exp. Candy M
                         ★★★ 3.500 + 1 Rare Candy
              ⚠⚠ EL PREMIO SE GUARDA EN LA FILA, NO SE CALCULA AL COBRAR. La
                 pantalla lo enseña y entre enseñarlo y cobrarlo pasan hasta
                 24 h: si saliera de la tabla en Java, tocarla pagaria algo
                 DISTINTO de lo prometido -- sin error, y al jugador le
                 pareceria que le hemos engañado. Misma regla que el precio de
                 la tienda
              ⚠ los identificadores se comprobaron contra el JAR antes de
                escribirlos (`gen_tienda.py --buscar candy`) y el autotest los
                comprueba contra el REGISTRO en cada ejecucion. Uno mal escrito
                no da error: da un premio que no se entrega, y el jugador YA HA
                HECHO EL TRABAJO
              ⚠ IMPORTES PROVISIONALES, como los de la tienda, y en UNA tabla
                de seis filas para que el analisis de economia sea cambiar seis
                numeros
                ⚠ SON UNA FUENTE (P3): ~10.000 de Plata al dia si alguien
                  completa las seis. Se sostiene solo porque completarlas es
                  DIFICIL. Si algun dia se hacen mas faciles, esto baja A LA VEZ
              ⚠ un Rare Candy SE GANA jugando y eso esta bien: lo que prohibe
                la linea roja (T4, D-007) es VENDERLO por moneda premium
              LA PANTALLA:
              ⚠ las estrellas se dibujan SIEMPRE LAS TRES, apagando las que no
                tocan. Con solo las encendidas, ★★ y ★★★ se distinguen
                CONTANDO -- y contar es lo que un icono te tiene que ahorrar
              ⚠ la CUENTA ATRAS la lleva el CLIENTE, restando de un instante
                absoluto. Si el servidor mandara "faltan N horas" el numero se
                quedaria viejo en cuanto pasara un minuto
              ⚠ un ciclo caducado dice "cambiando...", no "hace 3 h". Dura
                segundos --lo crea la primera persona que mire-- pero un
                numero negativo asustaria
              ⚠ el cartel de la recompensa se dibuja EL ULTIMO, 3D incluido, y
                se mete hacia dentro cerca del borde
              ⚠ la lista ordena por rareza EN EL CLIENTE aunque el servidor ya
                lo mande ordenado: la estrella es lo que promete la posicion
              +18 comprobaciones, y NINGUNA repite las que ya habia. Las nuevas
              son las estrellas (una de cada), que MAS ESTRELLAS PAGUEN MAS --se
              rompe editando la tabla y nadie lo mira hasta que alguien se queja
              de que la ★ paga mas que la ★★★-- y que los premios EXISTAN
              ⚠ SIN VERIFICAR EN EL JUEGO todavia
Repos         luna-eternal (privado) · luna-eternal-pack (publico)
              ⚠ OJO: la rama de luna-eternal-pack es master, NO main
Manifiesto    publicado y responde 200 · 198 ficheros · pack 0.2.0
              jugador 185 MB · constructor 234 MB
              .../luna-eternal-pack/master/manifest.json
              incluye mods/lunaneon-0.1.0.jar (descarga verificada byte
              a byte contra el local)
Acceso        SIN whitelist · EasyAuth 3.4.4 (/register /login)
              TheJuanCE PRE-REGISTRADO para que nadie le robe el nombre
              /luna constructor <clave> -> OP nivel 2 solo (builder.key)
              guia del equipo en EQUIPO.md
Ciudadela     NOCHE PERMANENTE (fixed_time 18000) · ambient_light 0.45
              SIN borde de mundo mientras se construye
              llegada en 4.27 70 0.36 (medido por el usuario, 29-ago)
              ⚠⚠ ES UN Vec3d Y NO UN BlockPos, Y NO ES UN CAPRICHO: con un
                 bloque se sumaba medio ("la casilla mas 0,5"), asi que el
                 jugador caia en 4,5 · 69 · 0,5 -- que NO es donde el usuario se
                 puso a medir. Los decimales SON la posicion
              antes: 4,69,0
              la luna gigante hay que CONSTRUIRLA: //hsphere
              UNA isla de 56x56 flotando en el vacio: la plaza central
              -28..27 en los dos ejes · suelo y=63 · se camina en 64
              se puede construir hacia ARRIBA (319) y hacia ABAJO (-64)
              se llega con /luna ir ciudadela (nivel 2)
              tools/ciudadela.py --solo-centro / --plano / --limpiar
              el plan de las 9 parcelas sigue vivo, se redibuja cuando toque
Construcción  WorldEdit 7.3.8 + Axiom 5.4.2 + Effortless 3.4.0
              ⚠ EFFORTLESS ES `effortless`, NO `effortless-building`.
                Son DOS mods distintos con el mismo aire y casi el mismo
                nombre: "Effortless Structure" (huskcasaca) y "Effortless
                Building" (Requios). Escribir el slug equivocado NO da
                error: baja el otro
                se eligio Structure por un dato MEDIDO, abriendo los dos
                jars y contando lo que registran:
                  effortless 3.4.0   0 blockstates, 0 objetos, 0 datos
                  effortlessbuilding 4.2   1 objeto + su receta
                un objeto entra en un registro QUE SE SINCRONIZA, y ahi
                esta el precedente de `trinkets`: nadie entraba y el
                error no nombraba al culpable
              LGPL-3.0-only: uso comercial permitido (D-008 ✓), misma
              familia que el conector de MariaDB que ya empaquetamos
              ⚠ SOLO EN EL PERFIL CONSTRUCTOR, y no por peso: ademas de
                colocar, ROMPE VARIOS BLOQUES A LA VEZ. En el mundo
                compartido eso acelera el griefing; los constructores ya
                van filtrados a OP nivel 2 (D-028). Pasarlo a todos es
                mover una linea de EXTRA_CONSTRUCTOR a EXTRA_JUGADOR
              ⚠ `server_side: required`: la colocacion multiple la aplica
                el SERVIDOR (P6). Solo en el cliente no haria nada
              ⚠ mods_servidor.py lee el manifiesto de `raw`, que cachea
                ~3 min. Justo despues de publicar aborta diciendo "no
                esta en el manifiesto del cliente" -- y tiene razon, aun
                no esta. Hay que esperar a que propague
              varios constructores a la vez: SI. OP nivel 2 basta
              alta de un constructor: python tools/constructor.py --anadir
              ⚠ la licencia de Axiom es una cortesia de 30 DIAS, POR
                PERSONA, desde su primer uso. La del dueno vence ~10-sep
Neon          mod lunaneon 0.1.0 · neon/ · DESPLEGADO 2026-08-13
              "Neon: 96 bloques en 16 colores" en el log · Done (5,3 s)
              las 6 formas verificadas en vivo con setblock
              16 colores x 6 formas = 96 bloques (D-029)
              bloque · losa · escalera · pilar · panel · tubo
              brillan SIEMPRE a tope; la luz que sueltan es 0/7/15
              y se cambia con clic derecho o [luz=N]
              ⚠ va en el CLIENTE tambien, y el cliente va PRIMERO:
                Fabric sincroniza el registro y a quien le falte el
                mod no le deja entrar. Orden en docs/world/neon.md §7
Obra          506 bloques mas en el MISMO mod (2026-08-16, D-032)
              compila y verifica; SIN MIRAR EN EL JUEGO todavia
              hormigon  3 acabados x 16 colores x 5 formas = 240
                        pulido · rayado (nervado) · panel prefabricado
                        lleva MURO: un parapeto de azotea es hormigon
              metal     8 aleaciones x 4 acabados x 5 formas = 160
                        acero, acero oscuro, aluminio, titanio, cromo,
                        cobre, laton, grafito
                        liso · cepillado · estriado · remachado
                        lleva PILAR: una viga es metal
              rejilla   8 aleaciones x 3 formas = 24 (huecos de verdad)
              vidrio    2 acabados x 16 colores x 2 formas = 64
              pavimento asfalto, terrazo, losa grande, adoquin = 18
              vanilla NO TIENE ni una escalera, ni una losa, ni un muro
              ni una valla de hormigon: tiene el cubo y se acaba ahi.
              Es el agujero mas grande para construir moderno
              LAS TEXTURAS SE DIBUJAN, no se bajan (D-008): el arte de
              bloques que circula es ARR o CC-BY-NC, y el NC es la
              misma clausula que descarto CobbleVerse (D-006)
              y sale ganando: los 16 colores del hormigon y del vidrio
              son LOS MISMOS 16 del neon rebajados por formula, asi que
              un neon cian pega con un hormigon cian por construccion
              5 pestanas nuevas en el creativo; 602 en una sola no es
              una paleta, es un listin
              ⚠ 29.330 ESTADOS de bloque, casi el doble que vanilla.
                19.776 son del hormigon y casi todos de sus 48 MUROS
                (324 estados cada uno). Si algun dia hay que recortar,
                el sitio es ese — no el numero de colores
              jar de 248 KB a 1,63 MB. Sobre 185 MB no mueve la aguja
Bloques       UN SOLO generador para las SEIS familias:
              python tools/gen_bloques.py             (regenera 3.911)
              python tools/gen_bloques.py --verificar (602 bloques ok)
              python tools/gen_bloques.py --maqueta   (laminas 2x2)
              tools/bloques/  comun.py · neon.py · ciudad.py
              ⚠ tools/gen_neon.py YA NO GENERA NADA: delega y avisa.
                Empezaba borrando assets/lunaneon ENTERO, asi que hoy
                dejaria el mod con 96 bloques y 506 huecos
              ⚠ Catalogo.java y Paleta.java SE GENERAN. A mano se pisan
              la maqueta dibuja cada textura REPETIDA 2x2 a proposito:
              es la unica forma de ver si encaja consigo misma, y una
              que no encaja no se nota en el editor sino en la fachada
              detalle en docs/world/bloques.md
Generadores   ⚠⚠⚠ UN GENERADOR NO PUEDE BORRAR LO QUE NO SABE HACER.
              (2026-08-23) Ejecutar gen_pokepad.py para instalar UN icono
              dejo SEIS PANTALLAS EN MAGENTA. Empezaba borrando `*.png` de
              su carpeta de salida, y ahi no vive solo lo suyo:
                pokepad_cosmeticos.png   el chasis de 6 pantallas
                lunacoin_oro.png . boton_mas_luna.png
              las pone otra mano, y desaparecieron sin un solo error.
              Compilo, se desplego, y solo se vio ABRIENDO una pantalla
              ⚠⚠ Y NINGUNA DE LAS TRES ESTABA EN GIT. Se recuperaron de un
                 jar publicado (build/pack/*.jar las conserva). Un clon
                 limpio del repo TAMPOCO habria podido construir un cliente
                 que funcionara, y nadie lo habria sabido
                 hoy estan versionadas: eso es lo que de verdad lo impide
              ⚠⚠ LA LECCION YA ESTABA ESCRITA, PERO PARA OTRO SCRIPT: el
                 aviso de gen_neon.py ("empezaba borrando assets/lunaneon
                 ENTERO") lleva dias en este documento. No sirvio porque
                 estaba redactado como anecdota de UN generador y no como
                 REGLA. Aqui queda como regla:
                   un generador borra SOLO lo que sabe generar,
                   y DICE por pantalla lo que conserva
                 gen_pokepad ahora imprime "CONSERVADAS (no las genera este
                 script): ..." -- que es lo que hace que el siguiente se
                 entere sin leer el codigo
Rejilla       ⚠⚠ "YA PAGINA" NO ERA VERDAD, y lo escribi yo (2026-08-23).
              Habia un boton de pagina, pero NADIE TROCEABA LA LISTA: el
              bucle recorria `orden.length` entero. Con QUINCE aplicaciones
              cuadraba por casualidad --5 columnas x 3 filas justas-- y la
              DECIMOSEXTA (curar) cayo en una fila 4 que no existe: se
              dibujo suelta debajo del marco, encima del chasis
              ⚠ `i / COLS` devuelve 3 tan tranquilo. Ni error ni aviso
              hoy la rejilla tiene TAMAÑO declarado (POR_PAGINA = COLS x
              REJ_FILAS), y el dibujado y el clic calculan la ranura IGUAL:
              si cada uno la calculara a su manera, pulsar un icono abriria
              el de al lado -- y en la pagina 2, algo estando sobre un
              candado
              ⚠ PAGINAS SE CALCULA, no se escribe. Estaba a 2 a mano y con
                dieciseis seguia valiendo por casualidad; con veintiuna
                habria dejado cinco INALCANZABLES sin decir nada. Es
                exactamente lo que ya paso en Cosmeticos, donde 54 de 62
                eran inalcanzables porque nada cambiaba `pagina`
Herramientas  LO QUE TIENE QUE ESTAR EN LA MAQUINA, y que el formateo
              del 2026-08-20 se llevo. Los CUATRO fallaron al usarlos y
              cada uno paro el trabajo hasta instalarlo:
                JDK 21    compilar el mod       Temurin (ver arriba)
                ffmpeg    convertir las voces   Gyan.FFmpeg
                gh        publicar el pack      GitHub.cli
                node      launcher Electron     OpenJS.NodeJS.LTS
                          (npm test) y las herramientas de diseño
              winget install EclipseAdoptium.Temurin.21.JDK Gyan.FFmpeg GitHub.cli OpenJS.NodeJS.LTS
              ⚠ winget MODIFICA EL PATH PERO NO EL SHELL YA ABIERTO: hay
                que usar la ruta completa o abrir otra terminal
                node: C:\Program Files\nodejs\node.exe
              ⚠ `gh` necesita login aparte, y el token que git tiene
                cacheado NO SIRVE: le falta el permiso `read:org` y
                `gh auth login --with-token` lo rechaza. Va por
                navegador: gh auth login --web
              python: .toolchain/python/python.exe (el `python` del PATH
              es el alias de la Store y NO funciona)
Backups       ⚠⚠ EL PLAN DA **CERO** RANURAS DE BACKUP EN EL PANEL.
              `feature_limits.backups = 0`: no hay boton, no hay red de
              seguridad, y todo lo construido vive en un solo disco ajeno
              python tools/backup.py          mundo + config -> backups/
              python tools/backup_bd.py       la BD -> backups/*.sql
              python tools/backup.py --listar
              LAS DOS SON LA MISMA COPIA PARTIDA EN DOS y se hacen juntas:
                el disco tiene las CONSTRUCCIONES
                la BD tiene monedas, vias, Pokedex, kits, misiones, GTS
                y cosmeticos (D-009). Restaurar solo una deja un servidor
                con las obras intactas y a todo el mundo sin nada
              ⚠⚠ EL MUNDO SE CONGELA ANTES DE COPIAR (save-off +
                 save-all flush). Comprimir un mundo VIVO captura ficheros
                 de region a medio escribir: la copia pesa lo que debe, se
                 descomprime bien, y tiene chunks corruptos que no se ven
                 hasta que alguien camina hasta alli semanas despues
                 `save-on` va en un `finally`: si el script muriera con el
                 mundo congelado, el servidor seguiria funcionando SIN
                 GUARDAR NADA, que es peor que no haber copiado
              ⚠ PRODUCCION (2a0a48ff) NO SE PUEDE COPIAR CON ESTA CLAVE:
                la API devuelve 404. Solo se respalda desarrollo
              medido 2026-08-23: 46,9 MB comprimido · 431 ficheros
                ciudadela 58 regiones (54,1 MB)  <- las construcciones
                salvaje 12 · overworld 12 · lobby 8
                BD: 14 tablas, 252 filas, 30 KB
              ⚠ NO se copian los jars: 400 MB que ya estan en el
                manifiesto publicado y se rebajan solos
              ✅ AUTOMATIZADO Y FUERA DE LA MAQUINA (2026-08-23)
              python tools/backup_auto.py           el ciclo entero
              ⚠ A MANO HAY QUE FIJAR UTF-8 O REVIENTA A MEDIAS:
                  PYTHONUTF8=1 python tools/backup_auto.py
                sin eso muere imprimiendo la salida del hijo
                (UnicodeEncodeError, cp1252) y puede dejar el mundo
                CONGELADO -- el finally de save-on es del HIJO, no del
                padre que acaba de morir. La tarea programada NO tiene
                este problema: backup_auto.cmd ya fija UTF-8 (63afbc5)
              python tools/backup_auto.py --probar  ¿esta todo listo?
              python tools/backup_auto.py --inventario
              tarea de Windows `LunaEternal-Backup`
                LUNES · MIERCOLES · VIERNES · DOMINGO a las 03:30
                si el PC esta apagado a esa hora, se ejecuta al encender
              Drive: rclone remoto `luna-drive`, 5 TiB libres
                LunaEternal-Backups/AAAA/AAAA-MM/AAAA-MM-DD_dia/
                    mundo.tar.gz  bd.sql  jars/  RESUMEN.txt
              retencion: 21 dias en local, 365 en Drive
                se borra por la FECHA DEL NOMBRE, no por la del fichero:
                copiar o mover cambia la mtime y se borraria lo que no toca
              ⚠ EL PERMISO ES `drive.file`, A PROPOSITO: rclone solo ve los
                ficheros que EL crea. Para un proceso que corre solo cada
                dos noches, es la diferencia entre romper una copia y
                vaciarte el Drive
              ⚠ CADA SUBIDA SE VERIFICA POR HASH (`rclone check`). Subir no
                es lo mismo que haber subido: una copia que llego a medias
                pesa distinto y no lo dice nadie
              ⚠ RESUMEN.txt se escribe MIRANDO DENTRO del tar: cuenta las
                regiones por dimension y las filas por tabla. Sirve para
                elegir que copia restaurar sin bajarse varias de 47 MB, y
                para notar que una salio a medias
              ✅ CLIENT_ID PROPIO (2026-08-23). rclone usaba el client_id
                 COMPARTIDO de Google, que lo estaban retirando durante 2026:
                 la copia habria empezado a fallar de madrugada y sin que
                 nadie mirase. Hoy usa uno del proyecto de Google Cloud
                 `luna-eternal-backups`, y el aviso ya no sale
              ⚠ `drive.file` SOLO VE LO QUE CREO ESE MISMO CLIENTE OAUTH.
                Al cambiar de client_id, las copias hechas con el anterior
                DEJAN DE VERSE desde rclone -- siguen en Drive y se abren
                por la web, pero `lsl` dice "directory not found". No es que
                se pierdan; es que las gestiona otro cliente. Si algun dia
                hay que rotar el client_id otra vez, contar con ello
              ⚠ el consentimiento esta en modo PRUEBA y solo admite los
                correos de la lista de usuarios de prueba. Anadir a alguien
                = Google Cloud Console -> Audiencia -> Usuarios de prueba
              medido 2026-08-23: 92,1 MB por copia, ciclo de 75 s
Servidor      allow-flight=true (lo exige Axiom; revertir al abrir)
              enforce-secure-profile=false · require-resource-pack=false
```

> ⚠️ **La direccion del servidor de desarrollo es `.lat`, no `.com`.** El
> `.com` estaba escrito en la documentacion y en el generador del pack, y **no
> existe en DNS**: el launcher decia "servidor no responde" con el servidor
> perfectamente vivo. Real, leida del panel: `s12.mia.us.tarohosting.lat:33043`
> (IP `103.195.100.223`).

> ⚠️ **`online-mode` tiene que ser `false`.** Verificado: `TheJuanCE` no existe
> como cuenta premium de Mojang. Con `online-mode=true` el propietario no
> podría entrar en su propio servidor. El anti-abuso hay que construirlo
> (`SEC-006`); el esquema ya es indiferente gracias a D-010.

### ⚠️ Dónde están las credenciales de la base de datos

**No están en el repositorio, y es correcto.** La copia autoritativa vive en el
propio servidor:

```
/config/lunaeternal.properties   en el servidor 7dc30799
```

Se lee por la API de Pterodactyl (`/files/contents`). El panel también muestra
la base y el usuario en su sección de *Databases*, y permite regenerar la
contraseña si se pierde.

> La copia del scratchpad de la sesión **es temporal y desaparece**. Si al
> retomar no aparece, se recupera del servidor — no hay que recrear la base.

### 0-bis · Qué se borró el 2026-08-12, y qué NO

**Decisión del usuario (D-026): la interfaz del PokePad se retira entera** para
rehacerla con arte propio que él va a enviar. Se borró de verdad, no se
comentó:

**Decisión del usuario (D-026), en dos tiempos el mismo día:** primero retirar
el PokePad, y después —al ver que quedaban los menús de cofre como respaldo—
**retirar también esos**. Su criterio, literal: *todo lo que hay en la PokePad
va a tener interfaz de cliente bien hecha con arte real, así que nada de menús
de Minecraft*.

| Se borró | Se conservó |
|---|---|
| `mod/src/client/` entero y el protocolo del Pad | **Toda la lógica de juego**, intacta y probada |
| Los **11 menús de cofre** y su framework (`Menu`, `Icon`, `LockState`) | La caché de fichas, ahora `PlayerCache` — nunca fue cosa de la interfaz sino de no machacar la base |
| `Skin` + `gui_chars.json` + el resource pack entero | La barra lateral y el tablist: son HUD, no menús |
| El Almanaque (el objeto que abría el menú) | Los servicios: economía, tienda, GTS, Pokédex, kits, misiones, cazas, viaje |
| **La barra lateral** (marcador de vanilla) — 12-ago, estorbaba al construir | El **tablist**, que no molesta y sirve para ver quién está conectado |
| `tools/`: los 6 scripts de arte e instalación | `gen_modpack` · `gen_manifest` · `gen_generaciones` · `discord_setup` |

**Antes de borrar se rescató la lógica de juego que vivía dentro de los menús**,
que es lo único que no se podía volver a escribir mirando la pantalla:

| Nuevo servicio | Qué salvó |
|---|---|
| `starter/StarterService` | El catálogo de iniciales y la entrega **idempotente con vuelta atrás**: se marca antes de entregar, porque entregar dos es un exploit permanente y no entregar uno se arregla |
| `heal/HealService` | La curación gratuita y su cooldown de 30 minutos |

Consecuencias, verificadas:

```
mod          vuelve a ser SOLO servidor    environment: "server"
             compila limpio                BUILD SUCCESSFUL
cliente      NO instala ningun mod nuestro  → una cosa menos que romper
jugabilidad  el jugador NO tiene pantallas todavia. Es el precio
             aceptado a cambio de no fijar el diseno a un cofre
```

> ⚠️ **Esto sí es una regresión de cara al jugador, y es deliberada.** Hasta que
> exista la interfaz de cliente, un jugador no puede elegir inicial, comprar ni
> usar el GTS. Lo de debajo funciona y está probado; lo que falta es la
> pantalla. **Por eso `ART-002` —el arte— es ahora la tarea que desbloquea el
> proyecto**, y por eso mientras tanto el foco es la ciudadela, que no depende
> de ella.

> **Lo que hay que recordar al rehacerla:** el análisis de la interfaz de
> referencia sigue en [pokepad-referencia.md](docs/ui/pokepad-referencia.md), y
> **el motivo por el que un cofre no basta** (5 columnas, sin inventario,
> botones fuera de la rejilla) no ha cambiado. El plan está en
> [interfaz-cliente.md](docs/ui/interfaz-cliente.md).

### El recorrido del jugador: construido, pero sin puerta de entrada

La auditoría de [feature-gap-analysis.md](docs/analysis/feature-gap-analysis.md)
destapó un bloqueo circular: **un jugador nuevo no tenía ningún Pokémon**, y sin
Pokémon nada de lo construido servía. El sistema se resolvió y **sigue
resuelto**; lo que falta hoy es la pantalla desde la que se usa:

```
1. Elige inicial (Kanto o Johto)   ✅ RESUELTO 2026-08-23 · se abre sola
2. Captura                          ✅ funciona
3. Registra en la Pokedex           ✅ funciona, con aviso de logro
4. Sube oficios y vias              ✅ RESUELTO 2026-08-23
5. Misiones que le guian            ✅ RESUELTO 2026-08-23 · 28 en 6 cadenas
6. Cura gratis                      ✅ RESUELTO 2026-08-23
7. Compra y vende                   ✅ RESUELTO 2026-08-23
8. Comercia objetos (escaparate)    ✅ RESUELTO 2026-08-25 · D-042
9. Comercia Pokemon (GTS)           ✅ RESUELTO 2026-08-25 · con tasador
```

> **El bloqueo circular se cerró el 2026-08-23.** Lo que faltaba no era lógica:
> `StarterService` llevaba meses escrito y probado, y **`conceder()` no lo
> llamaba nadie**. Hoy el jugador entra, elige inicial, captura, **compra Poké
> Balls**, sube oficios, funda un clan, y el árbol de misiones le dice qué hacer
> a continuación.
>
> **El recorrido básico está COMPLETO, y desde el 2026-08-25 el GTS también**:
> entrar, elegir inicial, capturar, comprar, curar, subir oficios, fundar un
> clan, comerciar Pokémon y objetos, y seguir el árbol de misiones. **Ya no
> queda ninguna misión que no se pueda completar** — `t5_gts` era la última.

### ⏭ POR AQUÍ SE SIGUE (2026-08-27, noche)

| | |
|---|---|
| **1. Verificar Viajes en el juego** | La pantalla existe y el clic derecho en un Miraidon la abre. **Falta mirarla**: que las siete fichas se lean, que viajar funcione, y que fuera de la ciudadela se apaguen |
| **2. Pre-generar** | Chunky **ya está instalado y sin usar**. Tres mundos × 3.000 = 2-4 h, **con el servidor vacío**: compite con los jugadores por los mismos 3 núcleos |
| **3. Verificar lo demás de hoy** | Mochila con rangos de verdad, el regreso al Hogar, y que un Miraidon **no** se pueda pegar en creativo ni registrar |
| **4. `ECO-005`** | Las Marcas siguen sin gastarse en nada |
| **5. La ciudadela** | Sigue siendo el foco no-técnico |

> ⚠⚠⚠ **LA REGLA DEL DÍA, y costó seis minutos de servidor inaccesible: UN
> REGISTRO QUE SE SINCRONIZA NO DEGRADA, ECHA.** Todo lo anterior —paquetes,
> datos, texturas— deja entrar a un cliente viejo aunque pierda funciones. Un
> bloque, un objeto o **un tipo de contenedor**, no: las dos tablas tienen que
> coincidir o la conexión se cae en la puerta.
> **Hay que avisar ANTES de reiniciar, no después.**

> ⚠⚠ **La lección del 25-ago: UNA MAQUETA QUE MIDE ES UNA PRUEBA.**
> `tools/gen_maqueta_mercado.py` dibuja las seis pantallas **sobre el chasis
> real** con **las anchuras reales de la fuente del juego**, y avisa de
> desbordes y solapes. En su primera pasada seria encontró **cuatro fallos que
> no daban ningún error**: una fila dibujada encima de la paginación, una
> columna que decía ordenar sin ordenar, nombres de objeto metiéndose en la
> columna de al lado, y una cabecera que no cabía con su flecha.
>
> **Ninguno se habría visto revisando el código.** Los cuatro son *números que
> dejaron de cuadrar* — la misma familia que la rejilla del PokePad. Al mover
> cualquier medida de una pantalla, **reejecutarla es más barato que una captura
> del usuario**.

> ⚠ **Lo que más caro salió el 23-ago fue lo que “cuadraba por casualidad”.** La
> rejilla funcionaba con quince aplicaciones porque 15 = 5×3 exactamente; el
> borrado de un generador funcionaba porque nadie había metido nada ajeno en su
> carpeta. **Ninguno de los dos daba error**, y los dos se rompieron el día que
> cambió una cifra. Al añadir cualquier cosa a una lista —iconos, categorías,
> pestañas— la pregunta es *¿esto cabía por diseño o por suerte?*

### Y cuando haya interfaz: calibrar con datos reales

**Lo que falta ahí no es código: son datos.** `/luna economia` mide, pero
hasta que alguien juegue de verdad todos los números siguen siendo
estimaciones:

> ⚠ **Y ahora hay un sitio concreto donde tocar.** Los 90 precios de la tienda
> salen de **cinco escalones** en `tools/gen_tienda.py`, no de 90 números
> escritos a mano — así que el análisis pendiente se aplica cambiando cinco
> cifras y reejecutando el generador.

| Número | Hoy | Se calibra con |
|---|---|---|
| Escalones de la tienda | 200 · 400 · 600 · 900 · 3.000 | ingreso mediano por hora de juego |
| Recompra al banco | 10 % plano | cuánto se recicla frente a lo que se produce |
| Margen de expedición | 1,5-2× supuesto | ingreso mediano diario |
| Tope de kits | 6 000/día | ≤25 % del ingreso diario |
| Tramos del impuesto GTS | 5-18 % | reparto P50/P99 |
| XP de las vías | a ojo | tiempo real hasta nivel V |

`/luna economia` dice si hay inflación, si los sinks funcionan y si la riqueza
se concentra — pero **hasta que se pueda jugar de verdad no hay nada que
medir**, así que esto espera a la interfaz.

### El foco de ahora: la ciudadela

Y eso **no es programación**, así que el trabajo del proyecto es quitar de en
medio todo lo que impida construirla. Al 2026-08-12 ya no queda nada técnico:

| | |
|---|---|
| ¿Existe la dimensión? | ✅ `lunaeternal:ciudadela`, vacía, plataforma 80×80 en el origen |
| ¿Se puede construir con Axiom en multijugador? | ✅ **sí**, y con varias personas a la vez |
| ¿Hace falta un mod de permisos? | ❌ **no**. OP nivel **2** basta para Axiom *y* WorldEdit — leído de los dos jars, no supuesto |
| ¿Cómo entran los demás? | El launcher, perfil **Constructor** |
| ¿Y los permisos? | `python tools/constructor.py --anadir <nombres>` — whitelist y nivel 2 de una vez |
| ¿Hay dónde construir? | ✅ **la plaza, sola, 56×56 flotando en el vacío**. Decisión del usuario: una zona cada vez |
| ¿Cómo se llega? | `/luna ir ciudadela` (nivel 2) |
| ¿Qué falta? | **Los nombres de quienes van a construir**, pedir la whitelist de Axiom (§Pendiente) y **construir** |

> ⚠️ **No des OP con `/op` a secas: eso es nivel 4** y permite apagar el
> servidor y quitarte a ti el OP. Los constructores van a nivel 2. El
> procedimiento exacto está en
> [construccion.md §3-ter](docs/world/construccion.md).

Después de la ciudadela: **gimnasios** (`UI-015`) y la **interfaz nueva**
cuando llegue el arte (`ART-002`).

> El recorrido completo del jugador nuevo ya funciona sin tocar un comando:
> elige inicial → captura → registra → compra → vende en GTS → sube de vía.
> Cada paso es una misión que le dice qué hacer y le paga por hacerlo.

> El orden anterior ponía la telemetría primero. **Estaba mal**: medir una
> economía que nadie puede jugar no sirve de nada.

> ⚠️ **Al crear una migración, añádela a la lista de `Database.java`.** La
> lista es manual a propósito (el orden debe ser explícito), y olvidarla hizo
> que el servidor arrancara sin la tabla. Y usa `BIGINT UNSIGNED` para
> `player_id`: con `BIGINT` a secas la clave ajena no se forma (errno 150).

> ⚠️ **Si una migración falla a medias, el servidor entra en bucle** y el panel
> se queda en `stopping`. Se sale con `kill` y luego `start`.

> ⚠️ **La lección del jar corrupto sigue viva, aunque el script ya no exista.**
> Reemplazar un jar con Minecraft abierto revienta más tarde con
> `ZipFile invalid LOC header`, dentro de un stack de netty que **no se parece
> en nada a la causa**. `tools/instalar_cliente.py` se borró con el mod de
> cliente (D-026), pero el conocimiento no se pierde: **está codificado en el
> launcher**, que reconoce ese síntoma y ofrece reparar
> (`launcher/src/main/core/diagnostico.js`, con prueba que lo fija).

> ⚠️ **Un mod de servidor puede exigir otro, y no te enteras hasta el
> reinicio.** Subir `letmedespawn` sin `almanac` dejó el servidor **sin
> arrancar** con `Incompatible mods found!`, y el fallo solo aparece al
> reiniciar — el peor momento posible. `tools/mods_servidor.py` ahora resuelve
> las dependencias en cadena leyendo el `fabric.mod.json` de cada jar, y
> **aborta sin tocar nada** si alguna no está en el manifiesto del cliente.
> `python tools/mods_servidor.py` sin argumentos dice qué haría.

> ⚠️ **Al desplegar, verifica el tamaño del jar.** Una subida se corrompió sin
> dar error (`Unexpected end of ZLIB input stream`) y el tamaño coincidía. Lo
> fiable es **borrar el jar antiguo antes de subir** el nuevo.

> ⚠️ **`PKM-004` está desplegado pero NO verificado en el juego.** El datapack
> carga (aparece el último en `datapack list`, que es lo que le da prioridad
> sobre Cobblemon), pero `/checkspawn` **exige un jugador conectado**, así que
> desde consola no puedo comprobar que un Gen 3 ya no aparece.
> **Cuando entres:** `/checkspawn common` en el Mundo Salvaje. Si sale algo de
> Hoenn en adelante, el datapack no está surtiendo efecto.

> **Cobblemon 1.7.3 ya está instalado** y el código fuente clonado en
> `vendor/cobblemon` (336 MB, git-ignorado; se recupera con
> `git clone --depth 1 https://gitlab.com/cable-mc/cobblemon.git vendor/cobblemon`).
> La tienda pasó de 17 a **28 objetos, 0 omitidos**.

Lo otro que falta es **construir la ciudadela**, y eso no es programación.

Catálogo completo en [interfaces-catalog.md](docs/ui/interfaces-catalog.md).

> ⚠️ **No hay interfaz, y es a propósito.** El plan para la nueva está en
> [interfaz-cliente.md](docs/ui/interfaz-cliente.md). **El cuello de botella es
> el arte, no el código**: la implementación anterior se escribió en un día y su
> techo lo puso el arte de relleno.

> **Tesoros ya está decidido** (D-020): cofres con legendarios, como
> Diosesmon. El análisis de riesgo queda archivado en
> [treasures.md](docs/economy/treasures.md) §2 como registro de que la
> decisión se tomó informada. **No volver a plantearlo.**

**Regla de trabajo establecida:** cada sistema nuevo añade sus invariantes a
`/luna autotest` **antes** de desplegarse (`MOD-006`).

> **Se incumplió una vez, con Misiones, y costó caro:** al escribirlos después,
> seis fallaron a la primera. Uno era grave — `advance()` no hacía nada para
> tipos no acumulativos, así que **la primera misión del tutorial no se
> completaba nunca**. Habría llegado a producción.

> Esa regla no es burocracia: en su primera ejecución el autotest encontró que
> **las transferencias fallaban siempre** por una columna 4 caracteres
> demasiado corta. Ninguna revisión de código lo habría visto.

### Pendiente del usuario (nada bloquea el desarrollo)

| | |
|---|---|
| **`WLD-006`** | ⏰ **Pedir la whitelist de SERVIDOR de Axiom** en `#whitelist-request` de su Discord. **Fecha límite ~2026-09-10.** Lo que funciona hoy es una cortesía automática de 30 días — ver [construccion.md §3-bis](docs/world/construccion.md) |
| `LNC-003` | **Certificado de firma de codigo — DIFERIDO a proposito (decision del usuario, 2026-08-17).** No tiene NADA que ver con las cuentas no premium: eso ya funciona y es gratis (`online-mode=false` + cuentas offline). Lo que arregla es que Windows deje de acusar al INSTALADOR de ser un virus — la pantalla azul de «Windows protegio su PC» que sale en cada instalacion limpia y que una parte de la gente no se atreve a saltar. **No rompe nada no tenerlo**: el launcher se reparte sin firmar desde siempre. Lo unico que se pierde es la autoactualizacion en macOS, que sin firma no puede aplicarse (en Windows si funciona). ~120 $/año que con 20 personas no compensan; se revisa cuando explicar donde hay que pulsar cueste mas que el certificado. Ver [distribucion.md §6](docs/technical/distribucion.md) |
| ~~`INF-008`~~ | ✅ **RESUELTO, verificado contra el panel el 2026-08-21.** Se midio jar a jar el servidor contra el manifiesto del cliente: **0 ficheros con el mismo nombre y distinto tamaño, y 0 mods con la misma familia y distinta version**. `fabric-api`, `lithium` y `lunaeternal` ya coinciden; los 141 KB de diferencia de `lunaeternal` no existen. `python tools/mods_servidor.py` dice «nada que hacer», y ahora sabemos que dice la verdad. **Lo que quedo aprendido:** solo hay **2 jars en el servidor que no estan en el cliente** —`EasyAuth-3.4.4` y `worldedit-mod-7.3.8`—, y **eso es correcto**. La regla de §0 («el servidor tiene que ser subconjunto del cliente») es mas estricta que la realidad: lo que echa gente con «Registry remapping failed» es un desajuste en registros **que se sincronizan** (bloques, objetos, entidades). Un mod de servidor que solo añade comandos y autenticacion no registra nada de eso. La regla util es la otra que ya esta escrita: *un mod que registre algo que se sincroniza tiene que estar en los dos lados* |
| ~~`MARCA-001`~~ | ✅ **APLICADA el 2026-08-23.** El servidor se llama **PokeReport Network** (decision del usuario, 2026-08-20). Detalle en [launcher-qt.md §7](docs/technical/launcher-qt.md). **Lo que hizo que costara mucho menos de lo anotado: el aviso estaba en la variable equivocada.** Lo que mueve la carpeta de datos **no es `Launcher_AppID`, es `Launcher_CommonName`** — `Application.cpp` hace `setOrganizationName(LAUNCHER_NAME)`, comprobado leyendolo. Y las dos cosas estan separadas: `Launcher_DisplayName` es **todo lo que el jugador ve** (instalador, menu Inicio, escritorio, «Agregar o quitar programas», titulo de ventana, «Acerca de») y `Launcher_CommonName` es la identidad. Cambiando solo el primero, **el jugador ve el nombre nuevo en todas partes y nadie vuelve a descargar 450 MB**. ⚠⚠ **Lo que si habia que migrar era el nombre de la instancia**: `findInstance()` la busca POR NOMBRE, asi que cambiarlo a secas le crea una **segunda** a quien ya la tenga; hoy `findInstance()` reconoce los nombres viejos y **renombra**. Fuera del fork: `gen_modpack.py`, el mod (`LunaEternal.NOMBRE`/`PREFIJO`, en un solo sitio), `neon` y el launcher de Electron. **`MOD_ID` no se toca**: cambiarlo rompe el mundo guardado. ⚠ **Sin desplegar todavia** |
| ~~`GPL-001`~~ | ✅ **CUMPLIDO, comprobado por la API el 2026-08-21.** `corderovibes-collab/luna-eternal-launcher` es **publico y GPL-3.0**, y el binario se distribuye desde ese mismo repositorio: el fuente completo del fork viaja con el, que es lo que exige la licencia (D-035). **Sigue siendo una obligacion viva, no una casilla marcada:** cualquier cosa que se le añada encima --anti-abuso, capa de identidad, lo que sea-- hay que publicarla tambien. Y con tienda de pago (D-007) eso no es gratis |
| **`LNC-002`** | Crear el token `PACK_TOKEN` para que el launcher publique sus releases — ver [launcher.md §2](docs/technical/launcher.md) |
| **`ART-002`** | Enviar el arte de la interfaz nueva: fondos, botones, iconos (D-026) |
| **`ECO-005`** | **Las Marcas se ganan y no se gastan en NADA.** Medido con `grep` el 2026-08-25: **ocho sitios las dan, cero las cobren**. El diseño es bueno y es la pieza que sostiene todo el modelo de pago —*no se compra progresión* se cumple **porque las Marcas solo se ganan jugando** (D-014)—, pero la mitad que las hace valer algo no existe: hoy son un número que sube. ⚠⚠ **Y se pone más caro cada día**: los precios de una moneda se fijan contra lo que la gente tiene, y todo el mundo está acumulando sin gastar. Cuando llegue la tienda habrá que elegir entre precios altos (y que los nuevos no lleguen nunca) o normales (y que los veteranos lo compren todo el primer día). ⚠ Salida barata si se alarga: **no hace falta la tienda entera**, basta con que exista *algo* que las cobre para tener una referencia. Detalle en [backlog ECO-005](docs/roadmap/backlog.md) |
| `SEC-001` | **Rotar la API key de Pterodactyl.** Ha vuelto a circular en texto plano el 2026-08-12. Está en `.env` (git-ignorado) y funciona; conviene regenerarla en el panel cuando se cierre la fase de construcción |
| `SEC-006` | ¿El servidor nuevo nace con `online-mode=true`? Condiciona el anti-abuso |
| `SEC-004` | Leer el texto oficial de Mojang sobre monetización (no accesible desde aquí) |
| `INF-002` · `INF-007` | Backups fuera del hosting (mundo **y** base de datos) |

---

## 1. Qué estamos construyendo

Un **Pokémon MMORPG persistente sobre Minecraft**: progresión diseñada,
economía controlada, exploración, colección, comercio, competición y endgame.

No es un servidor Cobblemon con mods encima. El criterio de aceptación de
cualquier sistema es:

> *"¿Podría este sistema pertenecer a cualquier servidor Cobblemon?"*
> Si la respuesta es sí, hay que personalizarlo o rechazarlo.

### Qué NO es

- No es una colección de mods.
- No es el evento narrativo actual (ver §3).
- No es progresión regalada.
- **No se vende poder competitivo ni moneda** (nivel T4 de
  [monetization.md](docs/economy/monetization.md)). Sí se venden identidad,
  comodidad y aceleración acotada — el modelo es F2P con paquetes de pago
  (D-007).

---

## 2. Principios rectores

| # | Principio |
|---|---|
| P1 | **Diseño antes que código.** UX → datos → backend → implementación. |
| P2 | **Toda recompensa se justifica** contra las 10 preguntas de progresión (§4). |
| P3 | **Economía antes que tiendas.** Sinks antes que sources. |
| P4 | **Free-to-play con paquetes de pago.** Se vende identidad, comodidad y aceleración **acotada**. La línea roja no es "ventaja" sino **inyección económica y poder competitivo** — ver [monetization.md](docs/economy/monetization.md). |
| P5 | **Mínimo de dependencias.** Nativo → configuración → datapack → resourcepack → mod maduro → sistema propio. En ese orden. |
| P6 | **Nunca confiar en el cliente.** Toda validación económica es de servidor. |
| P7 | **Nada crítico vive solo en la conversación.** Va a documentación. |
| P8 | **Producción es sagrada.** `2a0a48ff` es READ-ONLY hasta que exista plan aprobado. |
| P9-bis | **Todo lo que el jugador ve se dibuja en el cliente, con arte propio, y lo decide el servidor** (D-025, D-026). **Ninguna pantalla se hace como menú de cofre** — ni provisional, ni de respaldo, ni «mientras tanto»: un provisional que funciona se queda, y fija el diseño de lo que venga después a la rejilla de un cofre. Si algo necesita pantalla y no hay arte, **espera**. Ver [interfaz-cliente.md](docs/ui/interfaz-cliente.md). |
| P9 | **Interfaz, nunca comando.** Todo se hace con clics (D-012). Si el diseño de un sistema termina en *"el jugador escribe `/algo`"*, está incompleto. |
| P10 | **El cliente pesa poco; eso no significa que no haga nada.** El límite es el **peso de la descarga**, no la ambición de la interfaz. Referencia: **136 MB frente a los 8 GB de Diosesmon**. Un mod propio de cliente de 300 KB no toca esa cifra, así que **no está limitado por este principio** (D-025). Lo que sí sigue prohibido es engordar el pack con mods ajenos que no aporten (P5). Ver [client-pack.md](docs/technical/client-pack.md). |

### Las 10 preguntas (P2)

Ninguna recompensa se diseña sin responderlas:
qué obtiene · cuándo · qué requisito · qué esfuerzo · qué valor económico ·
qué desbloquea · qué contenido permite · cómo se abusa · cómo afecta a la
economía · qué pasa en endgame.

---

## 3. Contexto: qué existe hoy

**`D:\PokeReport 2` NO es un MMORPG.** Es un **evento narrativo en directo**
para 12 jugadores, ~3 horas, sobre CobbleVerse — con 57 líneas de voz grabadas,
incursiones cooperativas, NPCs con diálogo y cinemáticas.

Es un producto **distinto y de alta calidad**, pero de otra categoría: un evento
tiene un principio y un final; un MMORPG tiene retención. **No se migra, se
cosecha** — ver [current-server-audit.md](docs/analysis/current-server-audit.md).

### Infraestructura real (verificada por API 2026-08-11)

| Servidor | ID | RAM | Rol |
|---|---|---|---|
| Paquete Ender Dragon | `2a0a48ff` | 16 GB | **Producción.** PokeReport actual. READ-ONLY |
| Paquete Esqueleto | `7dc30799` | **8 GB** | **Desarrollo Luna Eternal.** MC 1.21.1 Fabric. Subido de 4 a 8 GB el 2026-08-22 |

Panel: `control.tarohosting.com` · Producción: `s17.mia.us.tarohosting.lat:33445`

El servidor de desarrollo se formateó el 2026-08-11 (D-004), tras verificar el
backup del proyecto que alojaba. Arranca correctamente:
`Minecraft 1.21.1 · Fabric Loader 0.18.4 · Done (12,5 s) · 860 MB`.
Whitelist activada, `online-mode=false` (ver §0).

> ⚠️ **Java 21 es obligatorio para MC 1.21.1.** La imagen del contenedor tenía
> Java 17 y el arranque fallaba con `UnsupportedClassVersionError` (class file
> 65.0 vs 61.0). Corregido a `java_21_zulu`, la misma que producción.

Detalle completo: [docs/technical/infrastructure.md](docs/technical/infrastructure.md)

---

## 4. Reglas de trabajo

### Orden obligatorio

```
CLAUDE.md → doc del dominio → análisis → propuesta → implementación → test → doc
```

Nunca: pregunta → código improvisado.

### Formato de análisis

Todo análisis se entrega como:
`CURRENT STATE · PROBLEM · ROOT CAUSE · OPTIONS · RECOMMENDED SOLUTION ·
DEPENDENCIES · RISKS · IMPLEMENTATION PLAN · TEST PLAN · DOCUMENTATION CHANGES`

### Lectura progresiva (control de contexto)

**No se leen todos los documentos en cada sesión.** Se carga `CLAUDE.md`, luego
el documento del dominio, luego solo sus dependencias declaradas. Cada documento
declara sus `Dependencies` en la cabecera para permitirlo.

### Cabecera estándar de documento

```markdown
# Nombre
## Purpose / ## Dependencies / ## Related Documents
## Current Status / ## Last Decision / ## Next Actions
```

### Definition of Done

Una funcionalidad no está terminada porque funcione. Requiere: diseño ·
arquitectura · dependencias · UX · backend · persistencia · seguridad ·
permisos · economía · progresión · errores · rendimiento · testing ·
documentación · migración · rollback.

---

## 5. Decisiones tomadas

| # | Fecha | Decisión | Motivo |
|---|---|---|---|
| D-001 | 2026-08-11 | El proyecto nuevo vive en `D:\pokereportversionmejorada`, repo independiente | `PokeReport 2` es un producto distinto (evento); mezclarlos contamina ambos |
| D-002 | 2026-08-11 | El evento Luna Eternal **se conserva intacto** como producto propio | Ya funciona y tiene valor; no es deuda técnica |
| D-003 | 2026-08-11 | Producción `2a0a48ff` es READ-ONLY | Comunidad viva; backups solo en el mismo disco |
| D-004 | 2026-08-11 | Formatear `7dc30799` y asignarlo a Luna Eternal en MC 1.21.1 | Decisión del usuario. Backup del proyecto anterior verificado antes de borrar |
| D-005 | 2026-08-11 | El mod propio de servidor **no es opcional** | Un datapack no puede leer el equipo Pokémon del jugador: sin mod no hay gating por equipo, ni GTS, ni progresión transversal |
| D-006 | 2026-08-11 | **CobbleVerse queda descartado. RATIFICADO** | Licencia ARR: prohíbe usar sus datapacks/estructuras/entrenadores en servidor público con tienda comercial (rangos, perks, artículos virtuales) y prohíbe obras derivadas. El usuario confirmó paquetes de pago → incompatibilidad total |
| D-007 | 2026-08-11 | **Modelo F2P + paquetes de pago** con beneficios, referencia Diosesmon | Decisión del usuario. Sustituye al P4 original. Marco operativo en [monetization.md](docs/economy/monetization.md): 4 niveles y un test de 6 preguntas |
| D-008 | 2026-08-11 | **La licencia es criterio de selección de mods**, antes que la funcionalidad | Con monetización confirmada, cualquier mod con licencia no comercial contamina el proyecto entero. Es lo que descartó CobbleVerse |
| D-009 | 2026-08-11 | **MariaDB como almacén principal**, no ficheros planos | El plan incluye 4 BD por servidor sin coste. Una venta de GTS exige atomicidad: sin transacciones, el Pokémon se pierde o se duplica. BD provisionada y verificada en desarrollo |
| D-010 | 2026-08-11 | **Clave sustituta `player_id`** en todo el esquema, nunca el UUID de Minecraft | Aísla el modelo de datos de la decisión `online-mode` y de los cambios de nombre. Convierte una migración masiva en actualizar una columna |
| D-011 | 2026-08-11 | **El mod propio lo escribe Claude.** B-006 cerrado | El usuario lo confirmó. Elimina el riesgo que sostenía toda la arquitectura (D-005, D-006, D-009). Implica también el SQL/JDBC y las migraciones |
| D-012 | 2026-08-11 | **Todo se hace con clics, nunca con comandos** | Al jugador le da pereza escribir. Si algo solo existe por comando, para la mayoría no existe. Los comandos son atajos opcionales, jamás el único camino |
| D-013 | 2026-08-11 | **Tercera moneda: ReportCoin** (premium, dinero real) | Decisión del usuario, equivalente a los Diosescoins. Es el diseño correcto porque **no se vende la moneda del juego**: se vende un token aparte que nunca toca el mercado |
| D-014 | 2026-08-11 | **Ninguna moneda se convierte en otra, en ninguna dirección** | De esta única regla salen las tres garantías: no se compra poder, no se compra progresión, pagar no infla. `REPORTCOIN` tampoco es transferible: si lo fuera, se revendería por PokéDólares y existiría la conversión por la puerta de atrás |
| D-015 | 2026-08-11 | **Tres espacios: lobby · ciudadela · mundo** | Cada uno con una sola función. Los gimnasios van **repartidos por el mundo**, no en una sala: concentrarlos contradice el pilar de exploración |
| D-016 | 2026-08-11 | **Dos mundos: Hogar (permanente) y Salvaje (se reinicia)** | Un solo mundo no puede ser permanente y fresco a la vez. El reinicio renueva la exploración sin producir contenido, y nada importante vive en el terreno: todo está en la base de datos |
| D-017 | 2026-08-11 | **Arranque con Kanto y Johto (251 especies)**, generaciones después | Con 1 025 ninguna especie importa y la Pokédex es inalcanzable. Se apagan por datapack (`enabled: false`), que es reversible |
| D-037 | 2026-08-17 | **La base del pack pasa a ser COBBLEVERSE, quitandole la generacion de estructuras y la musica.** Revoca D-031 | **Orden del usuario, dada despues de que le enseñara las licencias** — `cobbleverse` es All Rights Reserved y `cobbleverse-badges` es CC-BY-NC-ND-4.0, que es la clausula por la que D-006 lo habia descartado. Queda escrito aqui para que quien lo lea dentro de seis meses vea **el dato y la decision**, no solo la decision. **Lo que si se hace bien:** el manifiesto guarda URL y hash y nunca el jar, asi que cada mod se descarga del CDN de Modrinth — no redistribuimos nada suyo, igual que con los shaders (D-030). Se les quita lo que el usuario no queria: **generacion de estructuras** (4 mods) y **421 MB de musica**, que multiplicaba por cinco la descarga de un jugador nuevo (P10). El pack pasa de 185 a **434 MB**. Tres cosas que solo se ven haciendolo: **el slug de Modrinth no es el nombre del jar** y una exclusion mal escrita no surte efecto *sin avisar*; **su configuracion son 155 ficheros** y sueltos eran 155 peticiones a `raw`, o sea el 429 de D-036 otra vez (van en un zip por carpeta con `keepExisting`); y **`continuity:default` cambia 42 bloques de construir** — lo reporto el usuario en vivo con la ciudadela ya empezada, y se apaga |
| D-038 | 2026-08-17 | **El PokePad enseña datos de sesion —Plata, LunaCoins, Clan, Trabajo, Division y Medallas— en vez de la tarjeta de entrenador** | **Decision del usuario.** La tarjeta enseñaba el nivel de las cinco Vias en estrellas; lo que el queria bajo la cara es lo que se mira a diario. Dos cosas quedan escritas porque no son obvias: **las 16 medallas se referencian por identificador al mod de CobbleVerse y NO se copian sus texturas** — el mod va instalado en el cliente, asi que apuntarlas cuesta cero bytes, no redistribuye nada de un CC-BY-NC-ND y el dibujo lo sigue mandando su autor; y **clan, trabajo y division viajan en el paquete aunque no exista el sistema**, mandando cadena vacia para que el Pad pinte un guion. Un «Sin clan» diria «ya funciona y no tienes ninguno», que no es verdad; y tenerlos ya en el protocolo hace que encenderlos sea rellenar tres lineas en vez de tocar paquete, codec, cache y dibujado |
| D-042 | 2026-08-25 | **Los objetos del mercado se venden por ESCAPARATE y no por libro de órdenes.** Revoca la mitad de objetos de D-041 | **Decisión del usuario, tomada usándolo**: *«opciones duplicadas, botones duplicados… la idea es publicar una oferta así como en el de los Pokémon: el comprador ve la oferta, se interesa y la compra»*. **D-041 no estaba mal razonada; le faltaba un dato: cuánta gente hay.** Un libro de órdenes es el mecanismo correcto para cosas fungibles —eso sigue siendo cierto— pero **un libro necesita las dos caras pobladas para cruzar**. Con doce personas pones una orden de compra y se queda ahí hasta que alguien pase por casualidad: lo que en Albion es *liquidez*, aquí es *una lista de deseos que nadie lee*. ⚠⚠ **Y los botones duplicados no eran descuido: los pedía el diseño.** La pantalla que un libro necesita tiene **dos entradas para todo** —pestañas LIBRO/MIS ÓRDENES/HISTORIAL para mirar, y campos PRECIO/CANTIDAD con COMPRAR/VENDER para actuar—; un escaparate tiene una: publicas, o compras. **Lo que se gana no es solo la pantalla**: funciona con poca gente, hay una sola forma de hacer cada cosa, se aprende una vez (quien sepa vender un Pokémon sabe vender una pila de piedras) y **la custodia se simplifica** — la doble custodia existía porque una orden de compra retiene *dinero*, y sin órdenes de compra esa mitad desaparece. **Lo que se pierde, y hay que decirlo**: no hay órdenes de compra («compro cobre a 20») ni precio agregado de libro; el índice de precios pasa a medir **ventas cerradas**, que es menos dato y **mejor dato** — un precio solo es información cuando alguien lo ha pagado. ⚠ `MarketService` **no se borra**: sigue escrito, probado y con sus comprobaciones, y vuelve el día que el servidor tenga gente para que un libro cruce. Lo que cambia es **por dónde entra el jugador**. Detalle en [mercado.md §2-bis](docs/trading/mercado.md) |
| D-040 | 2026-08-23 | **Los clanes son un sistema propio, no un mod adoptado, y NO dan ninguna ventaja de juego** | **Petición del usuario** («si hay algún mod de clan sería excelente… tipo Albion»). Se buscó: lo que hay para Fabric 1.21.1 son **facciones con terreno** (reclamar chunks, guerra, PvP) o **equipos de chat**, y ninguna de las dos cosas es esto — la ciudadela es una isla que construimos nosotros, así que no hay territorio que repartir. P5 pone «mod maduro» antes que «sistema propio», pero **solo cuando el mod resuelve el problema**. **Lo que decide la cuestión es el tesoro:** un mod ajeno guardaría el dinero en su propio almacén, y entonces habría **dos economías** — la nuestra, con libro de asientos, idempotencia y auditoría (R3, R4), y la suya. Todo lo económico de este proyecto pasa por `applyInTransaction`, y un mod externo no puede pasar por ahí. **Y un clan no desbloquea nada:** da identidad (la etiqueta junto al nombre) y un sitio donde juntar dinero, y se queda ahí. Por diseño, porque una ventaja de clan convierte «tener amigos» en una estadística y castiga a quien juegue solo; y por economía, porque **un bono de clan es una fuente** (P3) y este proyecto tiene el problema contrario. Si algún día se le añade algo, la pregunta de P2 que hay que responder primero es la octava: *cómo se abusa* |
| D-039 | 2026-08-22 | **Los cosmeticos NO se consiguen jugando: solo con LunaCoins o en eventos que organicemos** | **Decision del usuario.** No caen de un cofre, no se craftean, no los suelta un jefe. Dos consecuencias que no son obvias. **La primera es tecnica y es un invariante, no una preferencia:** si no hay ninguna via de mundo, el servidor es la UNICA fuente y el cliente jamas concede un cosmetico -- solo dibuja lo que le mandan (P6). Eso simplifica el anti-abuso a un solo punto: la compra, que va con clave de idempotencia como todo lo economico (R4). **La segunda es de producto, y el propio `monetization.md` la avisa:** «un cosmetico sin nadie que lo vea no vale nada». Si TODO fuera de pago, los unicos que llevarian cosmeticos serian los que pagan, y el escaparate se apaga solo. Los **eventos son los que sostienen eso**: son la via gratuita, y por eso no son un adorno de la decision sino la mitad que la hace funcionar. Conviene que salga algo en cada evento, aunque sea poco. Los cosmeticos siguen siendo **T1 · identidad**, que es venta libre en su propio marco |
| D-035 | 2026-08-17 | **El launcher se rehace como fork de FreesmLauncher** (C++/Qt6, GPL-3.0), en repositorio propio y publico | **Decision del usuario, tomada tras leer el analisis en contra.** Mi recomendacion fue reestructurar el Electron actual: de los cinco riesgos que rompen al crecer —distribucion, identidad, firma, observabilidad, vuelta atras— el fork **no arregla ninguno**, y su funcionalidad estrella (jugar sin cuenta de Microsoft) ya la tenia el nuestro en 25 lineas. El usuario decidio el fork igualmente. **Dos consecuencias que no son opcionales y quedan escritas aqui para que nadie las descubra tarde:** (1) **GPL-3.0 obliga a publicar el codigo fuente completo** del fork, incluido cualquier anti-abuso o capa de identidad que se le añada encima — con tienda de pago (D-007), eso no es gratis; (2) hay que renombrar la marca (GPL §7.c/e, y Prism lo exige a sus forks). Ganancia real medida: instalador de **28,8 MB frente a 95**. Coste: rehacer perfiles, diagnostico, reparar e interfaz en español, y una cadena Qt6+CMake+MSVC+vcpkg en vez de `npm run dist`. Toolchain ya montado en `.toolchain/` (git-ignorado), fork clonado en `D:\luna-launcher` |
| D-036 | 2026-08-17 | **El manifiesto del pack deja de servirse desde `raw` y se resuelve por un PUNTERO inmutable, y cada fichero admite varios origenes** | Tres fallos que solo se ven con gente dentro. `raw.githubusercontent` limita por peticiones y era **la primera peticion del arranque**: ya costo media mañana de «a mi me funciona y a ellos no». `manifest.json` se sobrescribia, asi que una publicacion mala rompia a todo el mundo a la vez y arreglarlo era republicar 185 MB **con el pack roto mientras tanto**. Y cada fichero tenia una sola URL: si el origen cae, no hay a donde ir. Ahora `latest.json` son 250 bytes servidos por el CDN de descargas y **es lo unico mutable de la cadena**; los manifiestos llevan la huella en el nombre y no se tocan jamas. **Volver atras = subir 250 bytes** (`--volver-a <huella>`). El launcher **verifica el sha1 del manifiesto** antes de fiarse: ese fichero elige de que URL salen los 185 MB que se ejecutan en la maquina del jugador. Ficheros que dependian de `raw`: **5 → 0**. Detalle en [distribucion.md](docs/technical/distribucion.md) |
| D-034 | 2026-08-17 | **La moneda normal se llama «Plata»** | **Decisión del usuario.** Mismo mecanismo que D-033: el identificador interno sigue siendo `POKEDOLLAR` —está en la base de datos— y solo cambia el nombre visible. Su color de chat pasa de dorado a **blanco** por lo mismo que el saldo del PokePad: el dorado es ahora de las LunaCoins, y dos monedas del mismo color dejan de distinguirse. De paso, los dos comandos que tenían el nombre escrito a mano pasan a leerlo del enum — si no, habría quedado una pantalla diciendo «Plata» y un `/luna saldo` diciendo otra cosa |
| D-033 | 2026-08-16 | **La moneda premium se llama «LunaCoins», y en el PokePad solo se enseñan DOS monedas** | **Decisión del usuario.** D-018 dejó el nombre sin decidir a propósito y separó el identificador interno (`REPORTCOIN`, que está en la base de datos) del nombre visible, precisamente para que esto fuera *una línea* en vez de una migración de esquema. Lo ha sido. Y en la pantalla principal se ven **PokéDólares y LunaCoins**: las **Marcas** siguen existiendo —son lo que impide que la progresión se compre— pero no salen ahí |
| D-018 | 2026-08-11 | **Una sola moneda premium**, con nombre visible configurable | Dos monedas de pago obligan a elegir *cuál* comprar antes de *qué* comprar. El enum es `REPORTCOIN`; "ReportCoins" o "LunaCoins" es una línea de configuración |
| D-019 | 2026-08-11 | **No se venden Modificadores de estadísticas** (Diosesmon sí) | Un legendario es *una pieza*; un modificador es una mejora repetible sin techo aplicable a cualquier Pokémon. Lo segundo no tiene fondo |
| D-021 | 2026-08-11 | **Cobblemon se instala desde el jar oficial, y su código fuente se clona como referencia** | Tener el fuente da la verdad sobre IDs, spawns y API sin adivinar. Ya sirvió: encontró la `moon_ball`, cuya efectividad depende de la fase lunar |
| D-020 | 2026-08-11 | **Los cofres incluyen legendarios y legendarios shiny**, como Diosesmon | **Decisión del usuario, tomada tras leer el análisis de riesgo** de [treasures.md](docs/economy/treasures.md) §2. T4 admite esta excepción explícita. Obligatorias: probabilidades públicas, piedad acumulada, idempotencia y auditoría |
| D-025 | 2026-08-11 | **Se escribe un mod de cliente propio, `lunaeternal` con entrypoint de cliente, y la interfaz deja de estar atada al menú de cofre** · **implementación retirada por D-026; el criterio sigue vigente** | **Decisión del usuario, y correctora de un error mío.** Leí P10 como "ningún mod propio" y de ahí salió una interfaz con techo bajo. La captura de Diosesmon lo demostró: su POKEPAD **no puede ser un cofre** — no muestra inventario del jugador, tiene 5 columnas (un cofre tiene 9) y botones fuera de la rejilla. Es una pantalla propia. La nuestra también lo será. **P6 no se toca:** toda la lógica sigue en el servidor; el cliente solo dibuja lo que le mandan |
| D-023 | 2026-08-11 | ~~**La interfaz se bonita con un resource pack**~~ **Superada por D-025.** Lo hecho se conserva y se reutiliza (panel, arte, iconos): el mod de cliente dibuja, el resource pack aporta las texturas | Fue la decisión correcta *bajo la restricción equivocada*. En cuanto la restricción cayó, el cofre dejó de ser el techo |
| D-023-bis | 2026-08-11 | ~~**La interfaz se hace bonita con un resource pack**~~ **Anulada por D-026.** El truco de la fuente de espacio negativo funcionaba, y aun así el techo seguía siendo el de un cofre | Fue una corrección correcta a un error mío, pero resolvía el problema equivocado: hacía *bonito* un menú de cofre en vez de dejar de usar menús de cofre |
| D-024 | 2026-08-11 | ~~**El modpack oficial de Cobblemon NO se adopta**; se cosechan mods sueltos~~ **REVOCADA por D-031.** El aviso sobre `stendhal` (CC-BY-NC-ND) seguía siendo correcto y por eso ese mod se excluye a mano | 76 mods y 180 MB de cosas **de cliente**: shaders, mapas, partículas. Cero reglas de juego, que es lo único que nos diferencia. Y **`stendhal` es CC-BY-NC-ND**: no comercial y sin derivadas, la misma trampa que CobbleVerse (D-006) |
| D-022 | 2026-08-11 | **Axiom se instala en el servidor**, y el pack de cliente se parte en dos: jugador y constructor | **Corrección mía.** Leí su licencia como si nos afectara la cláusula comercial; esa apunta a quien *cobra por construir*, no a quien construye su propio servidor. El uso no comercial en servidor privado es gratuito y solo pide una whitelist en su Discord. El pack se separa porque son 46 MB que un jugador no usa jamás (P10) |
| D-026 | 2026-08-12 | **La interfaz del PokePad se borra ENTERA y se rehace con arte real.** El mod vuelve a ser solo de servidor | **Decisión del usuario.** La implementación estaba montada sobre arte de relleno generado por script, y el techo visual lo ponía ese arte, no el código. Rehacer encima habría sido pintar sobre una maqueta. Se borra la implementación, **no el diagnóstico**: sigue siendo cierto que un menú de cofre no puede ser la interfaz (D-025), así que la nueva volverá a ser pantalla propia. Efecto lateral bueno: el cliente deja de instalar ningún mod nuestro |
| D-027 | 2026-08-12 | **El launcher propio (`.exe`) es el canal oficial de distribución**, y se autoactualiza. El `.mrpack` queda como respaldo | El `.mrpack` obliga a instalar PrismLauncher, importar a mano y reimportar en cada actualización. Con 12 personas se puede pedir; con un servidor abierto, no. Se adapta el launcher de `D:\PokeReport 2` —que ya funcionaba— y se le añade lo que le faltaba: **autoactualización del propio launcher** (electron-updater), **perfiles** jugador/constructor en un solo instalador, **reparar** por SHA1 y **diagnóstico** de por qué se cerró el juego |
| D-028 | 2026-08-12 | **Los constructores llevan OP de nivel 2, no 4**, y no se instala ningún mod de permisos | Verificado leyendo los dos jars: Axiom concede todo con `hasPermissionLevel(2)` y WorldEdit con `isOperator()`. LuckPerms sería un mod más para no ganar nada (P5). El nivel importa: con `/op` a secas (nivel 4) un constructor puede apagar el servidor o quitarle el OP al dueño **sin querer** |
| D-031 | 2026-08-13 | **El pack parte del MODPACK OFICIAL de Cobblemon, y se personaliza quitando.** Revoca D-024 | **Decisión del usuario**, con el precedente de Diosesmon: coger el pack oficial y editarlo encima. D-024 decía «se cosechan mods sueltos» y la lista se quedó en 7; el oficial trae 76 probados juntos, con Cobblemon 1.7.3 y MC 1.21.1 — **exactamente nuestras versiones**. La lista **no se mantiene a mano**: `gen_modpack.py` baja su `.mrpack` y lee el índice, así que actualizar es reejecutar. Se quitan dos: **`stendhal`** (CC-BY-NC-ND: el NC prohíbe el uso comercial y el plan incluye venta de paquetes, D-007 — es la cláusula que ya descartó CobbleVerse) y **`bisect-mod`** (publicidad de un hosting que no es el nuestro). Y no se copia su mundo tutorial —97 MB de un jugador— ni su menú de FancyMenu, que lleva la marca de Cobblemon. **P10 se relaja a conciencia**: 143 → 185 MB de descarga |
| D-030 | 2026-08-13 | **Los shaders se reparten por su canal oficial y con su nombre real; no se copian de CobbleVerse ni se renombran a «PokeReport - Shaders»** | **Corrección a una petición del usuario, y las licencias venían dentro de la propia carpeta.** Los packs de CobbleVerse son **Complementary Unbound r5.8.1 + Euphoria Patches renombrados**. Complementary §1.3.d exige que un pack renombrado *«se vea claramente distinto del original, al margen de cambios de ajustes»* —renombrar no lo hace—; §1.2.d prohíbe servirlo por «direct file upload»; y Euphoria §2.1 solo permite obtenerlo **ejecutando su mod Patcher**. Conservar el fichero de licencia dentro es *una* condición, no un salvoconducto. **El resultado para el jugador es idéntico**: el mismo shader, el mismo aspecto, instalado solo y apagado — Complementary por URL de Modrinth y Euphoria como parcheador que se ejecuta en su PC. Pesa más aquí que en un servidor cualquiera porque el plan incluye venta de paquetes (D-007) |
| D-032 | 2026-08-16 | **Los 506 bloques de obra van DENTRO de `lunaneon`, no en un mod nuevo, y sus texturas se dibujan por script en vez de bajarse** | **Petición del usuario** (concreto, escaleras de concreto, vallas, metalizado, «más de 500 si hace falta»). Dos decisiones dentro de una. **Dentro de `lunaneon`** porque un mod más es un jar más que sincronizar y una forma más de dejar a alguien fuera con `Registry remapping failed`; el jar que se reparte sigue teniendo *solo bloques*, que es lo que D-029 pedía. **Dibujadas** porque casi todo el arte de bloques de GitHub y CurseForge es ARR o CC-BY-NC, y el **NC** es exactamente la cláusula que descartó CobbleVerse (D-006) y que choca con la venta de paquetes (D-007) — D-008 dice que la licencia se mira *antes* que la funcionalidad. Efecto lateral bueno y no obvio: los 16 colores del hormigón y del vidrio salen de los 16 del neón por fórmula, así que **pegan por construcción** y no porque alguien los haya emparejado a ojo |
| D-029 | 2026-08-13 | **Los bloques de neón son un mod propio, `lunaneon`, aparte del grande y sí instalado en el cliente** | La ciudadela es de noche permanente y va llena de neón, y **vanilla no tiene ni una escalera ni una losa que emita luz**. Un datapack no puede cambiar la luz de un bloque (está en el código, no en los datos) y un resource pack solo repinta los ocho que ya existen. Adoptar un mod de neón ajeno choca con D-008: los que hay no declaran licencia comercial clara. **Polymer** mantendría el cliente limpio pero **Axiom no vería los bloques** —es de cliente—, o sea que no se podría construir con la herramienta con la que se construye. Va **separado** de `lunaeternal` para que el jar que se reparte solo tenga bloques: ni economía, ni base de datos. **No toca D-026 ni P9-bis**: aquello va de pantallas, y un bloque no es una pantalla |

## 6. Decisiones PENDIENTES (bloqueantes)

| # | Decisión | Bloquea |
|---|---|---|
| ~~B-001~~ | ~~¿Dónde se desarrolla?~~ | ✅ Resuelta por D-004 |
| ~~B-002~~ | ~~CobbleVerse vs Cobblemon oficial~~ | ✅ Resuelta y **ratificada** (D-006 + D-007) |
| ~~B-003~~ | ✅ **RESUELTA por ampliación, no por análisis (2026-08-22).** El servidor pasa de 4 a **8 GB**. Con 4 corría por encima de su límite —4.447 MB medidos— y solo no caía por suerte. Ahora usa **4,34 GiB de 8** con el mundo vacío. ⚠️ **Que quepa no es que sobre:** ese número está medido *sin jugadores*, o sea sin chunks cargados, sin combates y sin entidades. Lo que decía la pregunta original sigue en pie — *sirve para sistemas aislados, no como réplica de producción* — y **el margen real solo se mide con gente dentro**. La prueba de carga sigue pendiente |
| **B-004** | ~~¿`online-mode` real?~~ ✅ **Es `false`.** Decisión pendiente: ¿el servidor nuevo nace en `online-mode=true`? | Anti-abuso (multicuenta ilimitada mientras siga offline). **Ya no bloquea el desarrollo**: D-010 hace el esquema indiferente |
| **B-005** | ¿Se aprueba la visión y el core loop propuestos? | PHASE 2 en adelante |
| ~~B-006~~ | ~~¿Hay capacidad de desarrollo en Java/Kotlin?~~ | ✅ Resuelta por D-011: lo escribe Claude |
| ~~B-007~~ | ~~¿Producción monetiza hoy?~~ | 🟢 **Sin señales de tienda real.** No hay mod de tienda, ni configs de rangos/donaciones. Solo economía interna (CobbleDollars). Riesgo legal actual: bajo. Confirmar con el usuario que no hay venta externa |
| **B-008** | ¿Qué permiten las reglas comerciales vigentes de Mojang? | Sin verificar. Condiciona el catálogo de tienda antes de construirlo |

---

## 7. Estructura documental

```
CLAUDE.md                          ← este documento
.env.example                       ← plantilla de credenciales (.env va ignorado)
docs/README.md                     ← índice de navegación
docs/analysis/                     ← auditorías (qué existe)
docs/architecture/                 ← decisiones estructurales
docs/technical/                    ← infraestructura, modelo de datos
docs/game-design/                  ← visión, core loop
docs/economy/ · progression/ · trading/ · ui/ · social/
                                     economy/tienda.md  ·  social/clanes.md
                                     trading/mercado.md
docs/roadmap/backlog.md            ← tareas con estado
mod/                               ← el mod de servidor (D-011)
neon/                              ← el mod de bloques de neón (D-029)
launcher/                          ← el launcher de escritorio (D-027)
tools/                             ← scripts: pack, manifiesto, neón, despliegue
                                     `mods_servidor.py` decide qué corre en el servidor
```

Los directorios se crean **cuando tienen contenido real**. No se generan stubs
vacíos: un documento vacío cuesta contexto y no aporta información.

### El mod — `mod/`

Fabric, servidor únicamente, Minecraft 1.21.1, Java 21.

```
mod/
├── build.gradle · settings.gradle · gradle.properties
└── src/main/
    ├── java/net/pokereport/luna/
    │   ├── LunaEternal.java        arranque, ciclo de vida, executor de E/S
    │   ├── LunaConfig.java         credenciales desde config/, nunca en el jar
    │   ├── db/Database.java        pool Hikari + motor de migraciones
    │   ├── player/PlayerService.java   R1/D-010: resuelve player_id
    │   ├── economy/                Currency · EconomyService · EconomyException
    │   └── command/LunaCommand.java    comandos de verificación
    └── resources/
        ├── fabric.mod.json
        └── db/migration/V001__initial.sql
```

**Reglas de código que no se negocian:**

| | |
|---|---|
| **Nunca** consultar la base en el hilo del servidor | `LunaEternal.submit()` |
| **Nunca** `float`/`double` para dinero | `BIGINT` / `long` |
| Toda operación económica lleva **clave de idempotencia** | R4 |
| El saldo se actualiza **en la misma transacción** que el asiento | R3 |
| Operaciones compuestas comparten `Connection` | `applyInTransaction` |
| Bloqueo de filas en **orden ascendente** de `player_id` | evita interbloqueos |

**Dependencias empaquetadas** (jar-in-jar): HikariCP (Apache-2.0) y MariaDB
Connector/J (LGPL-2.1). Ambas compatibles con uso comercial (D-008).

---

## 8. Fases

| Fase | Nombre | Estado |
|---|---|---|
| **0** | Auditoría | ✅ **completada** — infra ✔ · servidor actual ✔ · Cobblemon ✔ · Diosesmon ✔ |
| **1** | Arquitectura y visión | ✅ **completada** — visión ✔ core loop ✔ `ARCH-001` ✔ modelo de datos ✔ |
| **2** | Core progression | 🟡 **en curso** — diseño ✔ (`PROG-001`), implementación arrancando |
| 3 | Economía | 🟡 diseño ✔ (`ECO-001`, `ECO-002`); implementación ⬜ |
| 4 | Sistemas Pokémon | ⬜ |
| 5 | Quests | ⬜ |
| 6 | Trading / GTS | 🟡 diseño ✔ (`TRD-001`); implementación ⬜ |
| 7 | Mundo | ⬜ |
| 8 | UI | 🟡 diseño ✔ (`UI-001`, El Almanaque); implementación ⬜ |
| 9 | Social | ⬜ |
| 10 | Rangos y cosméticos | ⬜ |
| 11 | Endgame | ⬜ |
| 12-14 | Testing · Beta cerrada · Lanzamiento | ⬜ |

No se avanza de fase sin criterios de aceptación cumplidos.

---

## 9. Seguridad operativa

- **Ninguna credencial entra en el repositorio ni en documentación.**
  Plantilla en `.env.example`, valores reales en `.env` (git-ignorado).
- Credenciales conocidas como **comprometidas** (circularon en texto plano):
  la API key de Pterodactyl y la contraseña RCON del servidor de desarrollo.
  Ver backlog `SEC-001`.
- Producción no se toca sin backup verificado previo.

---

## 10. Referencias

| Recurso | Uso |
|---|---|
| https://gitlab.com/cable-mc/cobblemon | Repo oficial. Fuente de verdad para API, eventos, datapacks |
| Diosesmon Official PRO (CurseForge) | Referencia **de producto**, no especificación. Extraer principios, no implementación |
| `D:\PokeReport 2` | Proyecto anterior. READ-ONLY |
