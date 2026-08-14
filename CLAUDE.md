# CLAUDE.md — PokeReport: Luna Eternal

> Documento maestro. **Se lee antes de cualquier trabajo.** Si una decisión
> arquitectónica cambia, se actualiza aquí antes de cerrar la sesión.

**Última actualización:** 2026-08-12
**Fase actual:** PHASE 2 — Core progression · PHASE 7 — Mundo (ciudadela)
**Estado:** PHASE 0 y PHASE 1 completadas. 23 documentos, decisiones D-001 a
D-028. **El mod está desplegado y funcionando contra MariaDB:** economía de
tres monedas, cinco vías de progresión, y las interfaces base operativas.

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
Cobblemon     1.7.3 instalado · Done (9,0 s) · ~1,9 GB de 4096
Mod           lunaeternal 0.1.0 · migraciones V001 a V009 aplicadas
              compila contra la API de Cobblemon 1.7.3
BD            MariaDB s11945_luna · 3 monedas · 5 vías
Autotest      /luna autotest -> 112/112 correctos (2026-08-12, en vivo)
              eran 125: los 13 de fondos se fueron con el resource pack
Telemetría    /luna economia · informe automático al log cada hora
              tablist con rangos (la barra lateral se borro, D-026)
Pack base     EL MODPACK OFICIAL DE COBBLEMON (D-031, revoca D-024)
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
Mods          servidor 14 jars · cliente 79 · NO tienen que coincidir
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
Pokedex       revestida de azul luna · docs/ui/pokedex-luna.md
              76 texturas (54 KB), ACTIVADO solo
              python tools/gen_pokedex.py --comparativa
              ⚠ el TEXTO lo pinta el codigo de Cobblemon: 0x606B6E
                gris (16 usos) y 0x3A96B6 turquesa (3). Y el gris va
                sobre los paneles CLAROS, asi que la pantalla puede
                cambiar de tono pero NO oscurecerse: se volveria
                ilegible, y un resource pack no lo alcanza
              va INCRUSTADO en el jar de lunaneon (resourcepacks/) y
              se registra con ALWAYS_ENABLED. En el log del arranque:
              "Pokedex: revestido de luna activado"
              solo se reviste la PANTALLA: la carcasa conserva el
              color de cada Pokedex (hay 7 y las elige el jugador)
              ⚠ DOS intentos fallidos antes, los dos MUDOS:
                1) .zip suelto + linea en config/yosbr/options.txt.
                   YOSBR copia esa plantilla solo si options.txt no
                   existe: nunca para quien ya ha jugado
                2) DEFAULT_ENABLED. El javadoc de Fabric lo dice:
                   "a resource pack cannot be enabled by default,
                   only data packs can". Se registraba y se quedaba
                   apagado — y no se veia porque yo IGNORABA el
                   booleano que devuelve registerBuiltinResourcePack
Publicar      ⚠ nuestros jars se publican con la HUELLA en el nombre
              (lunaneon-0.1.0-3598884202.jar). raw.githubusercontent
              cachea ~3 min POR RUTA y no hay parametro que lo salte:
              con nombre fijo, el manifiesto anunciaba una huella que
              el CDN aun no servia. Contenido nuevo = URL nueva. En
              el cliente el fichero conserva su nombre de siempre
              una ruta NUEVA tarda ~36 s en responder (da 404 antes),
              pero el manifiesto tarda ~3 min en salir de cache: para
              cuando el cliente lo ve, el jar lleva rato disponible
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
Launcher      launcher/ · Electron 43 · 32/32 pruebas (npm test)
              ⚠ una prueba baja el manifiesto EN VIVO y exige que
                nada bajo config/ shaderpacks/ resourcepacks/ pise
                lo del jugador: va marcado `once` o falla. Ya cazo
                un fallo real. Ojo: el CDN de GitHub cachea unos
                minutos, asi que tras publicar puede dar rojo falso
              PUBLICADO: .../luna-eternal-pack/releases/latest
              se autoactualiza SOLO (electron-updater) y el pack tambien
              dos perfiles en un solo .exe: Jugador · Constructor
              reparar instalacion + diagnostico de por que se cerro
              ⚠ NO publicar otras releases en ese repo: el actualizador
                mira "la ultima release" y se perderia
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
Generaciones  Kanto + Johto activas · 583 spawns apagados por datapack
Interfaz      NO HAY. Se borro entera (D-026): Pad y menus de cofre
              a la espera del arte real. Debajo todo sigue vivo:
              /luna autotest sigue verde y la logica no se toco
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
Construcción  WorldEdit 7.3.8 + Axiom 5.4.2 cargados
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
              se genera entero: python tools/gen_neon.py
              se verifica:      python tools/gen_neon.py --verificar
              ⚠ va en el CLIENTE tambien, y el cliente va PRIMERO:
                Fabric sincroniza el registro y a quien le falte el
                mod no le deja entrar. Orden en docs/world/neon.md §7
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
1. Elige inicial (Kanto o Johto)   ✅ StarterService · ⬜ falta pantalla
2. Captura                          ✅ funciona solo, sin interfaz
3. Cura gratis                      ✅ HealService · ⬜ falta pantalla
4. Compra, vende, comercia          ✅ servicios · ⬜ falta pantalla
```

### Y cuando haya interfaz: calibrar con datos reales

**Lo que falta ahí no es código: son datos.** `/luna economia` mide, pero
hasta que alguien juegue de verdad todos los números siguen siendo
estimaciones:

| Número | Hoy | Se calibra con |
|---|---|---|
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
| Paquete Esqueleto | `7dc30799` | 4 GB | **Desarrollo Luna Eternal.** Limpio, MC 1.21.1 Fabric |

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
| D-029 | 2026-08-13 | **Los bloques de neón son un mod propio, `lunaneon`, aparte del grande y sí instalado en el cliente** | La ciudadela es de noche permanente y va llena de neón, y **vanilla no tiene ni una escalera ni una losa que emita luz**. Un datapack no puede cambiar la luz de un bloque (está en el código, no en los datos) y un resource pack solo repinta los ocho que ya existen. Adoptar un mod de neón ajeno choca con D-008: los que hay no declaran licencia comercial clara. **Polymer** mantendría el cliente limpio pero **Axiom no vería los bloques** —es de cliente—, o sea que no se podría construir con la herramienta con la que se construye. Va **separado** de `lunaeternal` para que el jar que se reparte solo tenga bloques: ni economía, ni base de datos. **No toca D-026 ni P9-bis**: aquello va de pantallas, y un bloque no es una pantalla |

## 6. Decisiones PENDIENTES (bloqueantes)

| # | Decisión | Bloquea |
|---|---|---|
| ~~B-001~~ | ~~¿Dónde se desarrolla?~~ | ✅ Resuelta por D-004 |
| ~~B-002~~ | ~~CobbleVerse vs Cobblemon oficial~~ | ✅ Resuelta y **ratificada** (D-006 + D-007) |
| **B-003** | ¿4 GB basta? Sirve para sistemas aislados, **no** como réplica de producción | Presupuesto y pruebas de carga |
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
docs/economy/ · progression/ · trading/ · ui/
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
  la API key de Pterodactyl y la contraseña RCON del servidor de 4 GB.
  Ver backlog `SEC-001`.
- Producción no se toca sin backup verificado previo.

---

## 10. Referencias

| Recurso | Uso |
|---|---|
| https://gitlab.com/cable-mc/cobblemon | Repo oficial. Fuente de verdad para API, eventos, datapacks |
| Diosesmon Official PRO (CurseForge) | Referencia **de producto**, no especificación. Extraer principios, no implementación |
| `D:\PokeReport 2` | Proyecto anterior. READ-ONLY |
