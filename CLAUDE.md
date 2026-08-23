# CLAUDE.md — PokeReport: Luna Eternal

> Documento maestro. **Se lee antes de cualquier trabajo.** Si una decisión
> arquitectónica cambia, se actualiza aquí antes de cerrar la sesión.

**Última actualización:** 2026-08-23
**Fase actual:** PHASE 2 — Core progression · PHASE 7 — Mundo (ciudadela)
**Estado:** PHASE 0 y PHASE 1 completadas. 28 documentos, decisiones D-001 a
D-040. **El mod está desplegado y funcionando contra MariaDB:** economía de
tres monedas, ocho vías de progresión, y **siete pantallas** en el PokePad.
Autotest **217/217** en vivo.

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
Autotest      /luna autotest -> 207/207 correctos (2026-08-23, en vivo)
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
              ⚠ t5_gts NO SE PUEDE COMPLETAR todavia: pide el GTS, que no
                tiene pantalla. t4_tienda, m1_comprar y m2_vender se
                desbloquearon con la tienda (2026-08-23)

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
                era igual a `versionString()` (`0.2.0`), asi que a toda
                compilacion etiquetada se le pegaba el canal detras. No
                era solo feo: ESA es la cadena con la que el
                actualizador se compara
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
                ⚠ SIN DESPLEGAR: el mod compilado y el pack regenerado no
                  se han publicado todavia
              lo que el fork NO tiene todavia: diagnostico de cierres
              ni boton de reparar, que el de Electron SI tiene
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
Dimensiones   lobby · ciudadela · salvaje (+ overworld = Mundo Hogar)
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
Interfaz      SIETE PANTALLAS. Cinco verificadas en el juego:
                PokePad     2026-08-16   la principal, 15 iconos
                Cosmeticos  2026-08-22   4 pestanias
                Trabajos    2026-08-23   8 Vias y oficios, paginado
                Misiones    2026-08-23   arbol de 28 en 6 cadenas
                Inicial     2026-08-23   se abre SOLA al entrar
                Clan        2026-08-23   SIN VERIFICAR (pide 2 cuentas)
                Tienda      2026-08-23   SIN VERIFICAR . 9 articulos
              6 de los 15 iconos abren algo: pokedex (la de Cobblemon),
              cosmeticos, trabajos, misiones, clan y tienda
              SIGUEN SIN PANTALLA, con la logica viva y probada:
                curar . GTS . kits . cazas . viaje
              CURAR es la que mas se nota: ya se compran Poke Balls, pero
              el equipo sigue sin poder curarse desde el Pad
Cazas         HUNT-001 · mismas para todo el servidor · rotan 12 h
              solo captura las avanza; crianza cuenta al ECLOSIONAR
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
              llegada en 4,69,0 (centro de la plaza, medido en el juego)
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
| `heal/HealService` | La curación gratuita y su cooldown de 10 minutos |

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
6. Cura gratis                      ✅ HealService · ⬜ FALTA PANTALLA
7. Compra, vende, comercia          ✅ servicios   · ⬜ FALTA PANTALLA
```

> **El bloqueo circular se cerró el 2026-08-23.** Lo que faltaba no era lógica:
> `StarterService` llevaba meses escrito y probado, y **`conceder()` no lo
> llamaba nadie**. Hoy el jugador entra, elige inicial, captura, **compra Poké
> Balls**, sube oficios, funda un clan, y el árbol de misiones le dice qué hacer
> a continuación.
>
> **Lo único básico que queda es CURAR**, y es la pantalla más pequeña de todas:
> `HealService` ya tiene la curación gratuita y su cooldown de 10 minutos. Solo
> `t5_gts` sigue sin poder completarse, porque el GTS no tiene pantalla.

### ⏭ POR AQUÍ SE SIGUE (2026-08-23, fin de sesión)

| | |
|---|---|
| **1. Verificar en el juego** | Clanes (**hacen falta 2 cuentas**) y tienda. Son lo único de hoy sin comprobar en vivo |
| **2. La pantalla de CURAR** | La más pequeña que queda. `HealService` está escrito y probado |
| **3. El análisis de economía** | Lo pidió el usuario. **No se puede hacer sin gente jugando.** Los números y dónde se tocan, en [tienda.md §5](docs/economy/tienda.md) |
| **4. La ciudadela** | Sigue siendo el foco no-técnico, y nada la bloquea |

> ⚠ **Al retomar, lo primero es probar con dos cuentas.** Todo lo de clanes que
> importa —invitar, aceptar, echar, ascender— cambia el estado que ven *otros*, y
> eso es justo lo que no se puede comprobar solo.

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
