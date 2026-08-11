# CLAUDE.md — PokeReport: Luna Eternal

> Documento maestro. **Se lee antes de cualquier trabajo.** Si una decisión
> arquitectónica cambia, se actualiza aquí antes de cerrar la sesión.

**Última actualización:** 2026-08-11
**Fase actual:** PHASE 2 — Core progression
**Estado:** PHASE 0 y PHASE 1 completadas. 22 documentos, decisiones D-001 a
D-022. **El mod está desplegado y funcionando contra MariaDB:** economía de
tres monedas, cinco vías de progresión, y las interfaces base operativas.

---

## 0. POR DÓNDE SEGUIR

> Lee esto primero al retomar. Lo demás es contexto.

### Lo que funciona ahora mismo (verificado 2026-08-11)

```
Servidor dev  7dc30799 · s12.mia.us.tarohosting.com:33043
              MC 1.21.1 Fabric · whitelist ON · TheJuanCE op nivel 4
Cobblemon     1.7.3 instalado · 60 mods · Done (9,0 s) · ~1,9 GB de 4096
Mod           lunaeternal 0.1.0 · migraciones V001 a V009 aplicadas
              compila contra la API de Cobblemon 1.7.3
BD            MariaDB s11945_luna · 3 monedas · 5 vías
Autotest      /luna autotest -> 96/96 invariantes correctos
Interfaz      Almanaque · Cartera · Vías · Puerta del Mundo
              Tienda · GTS · Pokédex · Kits · Inicial · Curar
              Misiones (6 de tutorial + 2 diarias)
Telemetría    /luna economia · informe automático al log cada hora
              Barra lateral y tablist con rangos
Cliente       jugador     -0.1.0.mrpack · 136 MB · 7 mods
              constructor -Constructor-0.1.0.mrpack · 182 MB · +CUI +Axiom
Dimensiones   lobby · ciudadela · salvaje (+ overworld = Mundo Hogar)
Generaciones  Kanto + Johto activas · 583 spawns apagados por datapack
Construcción  WorldEdit 7.3.8 + Axiom 5.4.2 cargados · plataforma 80×80
```

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

### El servidor YA SE PUEDE JUGAR

La auditoría de [feature-gap-analysis.md](docs/analysis/feature-gap-analysis.md)
destapó un bloqueo circular: **un jugador nuevo no tenía ningún Pokémon**, y sin
Pokémon nada de lo construido servía. Resuelto:

```
1. Elige inicial (Kanto o Johto)   ✅
2. Captura                          ✅ se anota en la Pokédex y da Marcas
3. Cura gratis                      ✅ cooldown 10 min
4. Compra, vende, comercia          ✅
```

### Siguiente: calibrar con datos reales

FASE B está cerrada salvo los gimnasios, que necesitan mundo construido.

**Lo que falta ahora no es código: son datos.** `/luna economia` mide, pero
hasta que alguien juegue de verdad todos los números siguen siendo
estimaciones:

| Número | Hoy | Se calibra con |
|---|---|---|
| Margen de expedición | 1,5-2× supuesto | ingreso mediano diario |
| Tope de kits | 6 000/día | ≤25 % del ingreso diario |
| Tramos del impuesto GTS | 5-18 % | reparto P50/P99 |
| XP de las vías | a ojo | tiempo real hasta nivel V |

**Juega una sesión y ejecuta `/luna economia`.** El informe dice solo si hay
inflación, si los sinks funcionan y si la riqueza se está concentrando.

Después: **construir la ciudadela** (`WLD-005`) y **gimnasios** (`UI-015`).

> El recorrido completo del jugador nuevo ya funciona sin tocar un comando:
> elige inicial → captura → registra → compra → vende en GTS → sube de vía.
> Cada paso es una misión que le dice qué hacer y le paga por hacerlo.

> El orden anterior ponía la telemetría primero. **Estaba mal**: medir una
> economía que nadie puede jugar no sirve de nada.

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
| `SEC-001` | **Rotar la API key de Pterodactyl.** Circuló en texto plano; lleva pendiente desde el 3-ago |
| `SEC-006` | ¿El servidor nuevo nace con `online-mode=true`? Condiciona el anti-abuso |
| `SEC-004` | Leer el texto oficial de Mojang sobre monetización (no accesible desde aquí) |
| `INF-002` · `INF-007` | Backups fuera del hosting (mundo **y** base de datos) |
| `BLD-002` | **Pedir la whitelist multijugador de Axiom en su Discord.** Gratis. El mod ya está instalado, pero sin ese trámite no editará en vivo |

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
| P9 | **Interfaz, nunca comando.** Todo se hace con clics (D-012). Si el diseño de un sistema termina en *"el jugador escribe `/algo`"*, está incompleto. |
| P10 | **El cliente mínimo es el mínimo posible, no cero.** ~~Ningún mod obligatorio~~ — **corregido 2026-08-11**: Cobblemon es cliente+servidor, así que hace falta Fabric + Fabric API + Cobblemon. El principio real es **136 MB frente a los 8 GB que exige Diosesmon**. Ver [client-pack.md](docs/technical/client-pack.md). |

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
| D-022 | 2026-08-11 | **Axiom se instala en el servidor**, y el pack de cliente se parte en dos: jugador y constructor | **Corrección mía.** Leí su licencia como si nos afectara la cláusula comercial; esa apunta a quien *cobra por construir*, no a quien construye su propio servidor. El uso no comercial en servidor privado es gratuito y solo pide una whitelist en su Discord. El pack se separa porque son 46 MB que un jugador no usa jamás (P10) |

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
