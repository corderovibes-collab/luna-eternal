# Backlog

## Purpose

Registro único de tareas con ID estable, estado y criterios de aceptación.
Cualquier trabajo empieza por una entrada aquí.

## Dependencies

- [`../../CLAUDE.md`](../../CLAUDE.md)

## Related Documents

- [`../technical/infrastructure.md`](../technical/infrastructure.md)
- [`../analysis/current-server-audit.md`](../analysis/current-server-audit.md)

## Current Status

PHASE 0 completada. **3 bloqueantes abiertos** (SEC-001, INF-002, ARCH-001).
Actualizado 2026-08-11.

## Last Decision

D-004 (formatear el servidor de desarrollo) · D-005 (mod propio obligatorio)

---

## Estados

`IDEA · ANALYSIS · DESIGN · READY · IN_PROGRESS · TESTING · REVIEW · DONE · BLOCKED · DEPRECATED`

## Prefijos

`INF` infraestructura · `SEC` seguridad · `AUD` auditoría · `ARCH` arquitectura
`ECO` economía · `PROG` progresión · `UI` interfaz · `PKM` Pokémon
`QST` quests · `TRD` comercio · `WLD` mundo · `SOC` social · `GD` diseño

---

# ✅ Completadas

| ID | Tarea | Resultado |
|---|---|---|
| `AUD-000` | Auditoría de infraestructura | 2 servidores, 3 riesgos críticos → `infrastructure.md` |
| `AUD-001` | Auditoría de Cobblemon oficial | Frontera nativo/propio definida → `cobblemon-audit.md` |
| `AUD-002` | Análisis de Diosesmon PRO | 19 sistemas clasificados → `diosesmon-analysis.md` |
| `AUD-003` | Auditoría del proyecto actual | Es un evento, no un MMORPG → `current-server-audit.md` |
| `INF-001` | Asignar servidor de desarrollo | D-004: `7dc30799` formateado, MC 1.21.1 Fabric, arranca OK |
| `INF-004` | Eliminar core dump de 2,77 GB | Eliminado con el formateo |
| `GD-001` | Visión | **Propuesta**, pendiente de aprobación → `vision.md` |
| `GD-002` | Core loop | **Propuesta**, pendiente de aprobación → `core-loop.md` |
| `PKM-001` | Verificar fase lunar en spawns | ✅ **Confirmado en el jar.** `moonPhase` existe (42 usos, 9 especies). Pero el ciclo dura **2 h 40 min**, no una semana → visión corregida |
| `ARCH-001` | CobbleVerse vs Cobblemon | ✅ **Descartado CobbleVerse por licencia ARR** → `architecture/modpack-decision.md`. Ratificado por D-007 |
| `ECO-002` | Marco de monetización | ✅ 4 niveles + test de 6 preguntas → `economy/monetization.md` |
| `ECO-001` | Modelo económico | ✅ 2 monedas, sources, sinks, velocity, wealth tiers → `economy/economy-overview.md` |
| `SEC-003` | ¿Monetiza producción? | ✅ **No hay señales de tienda.** Sin mod de tienda ni configs de rangos. Solo CobbleDollars interno |
| `AUD-005` | Auditar CobbleDollars | ✅ **Sin arbitraje.** 25 comprables vs 80 vendibles, márgenes 4-12× a favor de la casa. Bien configurado |
| `SEC-002` | `online-mode` de producción | ✅ **Es `false`.** Confirmado por `server.properties` y por UUIDs v3 de los ops |
| `SEC-004` | Reglas comerciales de Mojang | 🟡 **Parcial.** Principio conocido; **texto oficial inaccesible** (timeout ×3). Pendiente de lectura directa |
| `ARCH-003` | Modelo de datos y persistencia | ✅ MariaDB + 5 reglas de esquema → `technical/data-model.md`. **BD provisionada y verificada** |
| `TRD-001` | GTS | ✅ Acceso G0-G4, tasa 1 % + impuesto progresivo, anti-abuso → `trading/gts.md` |
| `PROG-001` | Modelo de progresión | ✅ 5 vías, sin nivel de jugador, desbloqueo de 2 factores → `progression/progression-model.md` |
| `UI-001` | Navegación | ✅ **El Almanaque** → `ui/navigation.md` |
| `B-006` | ¿Quién escribe el mod? | ✅ **Claude** (D-011). Riesgo estructural eliminado |
| `MOD-001` | Esqueleto del mod: Gradle + Fabric 1.21.1 + Java 21 | ✅ `mod/` creado |
| `MOD-002` | Persistencia: Hikari, migraciones, esquema V001 | ✅ `player` · `player_economy` · `ledger_entry` |
| `MOD-003` | Servicios de identidad y economía | ✅ `PlayerService` (R1) · `EconomyService` (R2/R3/R4) |
| `WLD-001` | Estructura del mundo: lobby, ciudadela, mundo | ✅ → `world/world-structure.md` |
| `ECO-004` | ReportCoin, tercera moneda premium | ✅ 3 monedas en bucles cerrados sin conversión. Migración V003 |
| `UI-002` | Framework de menús de servidor | ✅ `Menu` · `Icon` · `LockState` · `PlayerSnapshot` · `MenuService` |
| `UI-005` | El Almanaque (pantalla principal) | ✅ 4 grupos, 19 secciones, estados de bloqueo. `/menu` |

---

# 🔴 Bloqueantes abiertos

### SEC-001 — Rotar credenciales comprometidas
`READY` · 🔴 Crítica · dep: ninguna

La API key de Pterodactyl (`ptlc_…`) circuló en texto plano y da control total
sobre **ambos** servidores. La contraseña RCON del servidor de desarrollo también
se expuso (ya eliminada con el formateo, pero la key sigue viva). Esta rotación
figuraba como pendiente **desde el 3 de agosto** en el proyecto anterior.

- [ ] Key antigua revocada en el panel
- [ ] Key nueva solo en `.env` local (ya git-ignorado)
- [ ] Verificado que la antigua devuelve 401
- [ ] Revisar qué automatizaciones consumían la anterior

> Es la tarea más barata y más urgente del proyecto: minutos de trabajo.

---

### INF-002 — Backup fuera del servidor + restauración probada
`READY` · 🔴 Crítica · dep: ninguna

**Corrección respecto al análisis inicial:** los backups **sí existen y
funcionan** (`AdvancedBackups`: 14 copias, 49,96 GB, diaria 05:00, última hoy).
Pero tienen tres problemas:

1. `path=./backups` → **mismo disco que protegen.** No cubren fallo de disco,
   de nodo ni cierre de cuenta.
2. Ocupan el **71 % del disco usado** (50 de 70,8 GB) y `purge.size=100 GB`
   les permite crecer más. Un disco lleno detiene el servidor y puede
   corromper el guardado.
3. Último *full* del 7-ago; los 4 diferenciales posteriores dependen de él.
   **Nunca se ha probado una restauración.**

- [ ] Al menos una copia completa descargada fuera del hosting
- [ ] **Restauración probada** en el servidor de desarrollo (ahora está libre)
- [ ] `purge.size` ajustado para no agotar el disco
- [ ] Forzar un *full* reciente

---

### SEC-003 — ¿Producción monetiza con CobbleVerse instalado? (B-007)
`READY` · 🔴 Crítica · dep: ninguna

La licencia ARR de CobbleVerse prohíbe usar sus datapacks, estructuras y
entrenadores en **servidor público con tienda comercial** (rangos, perks,
artículos virtuales, transacciones). Producción corre CobbleVerse 1.7.42.

**Si hoy se venden rangos o perks, el incumplimiento es actual, no futuro.**
No está comprobado y no consta en la documentación.

- [ ] Confirmar si hay tienda / rangos de pago activos
- [ ] Confirmar si el servidor es público o privado
- [ ] Si aplica: pedir permiso escrito al equipo de CobbleVerse, o retirar el
      contenido afectado

---

### INF-008 — Instalar un JDK 21 propio para compilar
`READY` · 🟠 Alta · dep: ninguna

El único JDK 21 completo de la máquina es el runtime de **PrismLauncher**
(`java-runtime-delta`, 21.0.7). Funciona, pero **PrismLauncher puede
actualizarlo o borrarlo** sin avisar, y entonces el proyecto deja de compilar.

Además hay un `~/.gradle/gradle.properties` **global** con
`org.gradle.java.home` apuntando a JDK 17 (del proyecto Backrooms, MC 1.20.1).
**No se puede cambiar** sin romper ese proyecto, así que `mod/build.sh` lo
sobrescribe por línea de comandos.

- [ ] Instalar Temurin/Zulu JDK 21 en una ruta estable
- [ ] Actualizar las rutas de `mod/build.sh`
- [ ] Verificar que Backrooms sigue compilando con su JDK 17

---

### MOD-004 — Probar el mod contra la base de datos real
`TESTING` · dep: `MOD-003` ✅ · verificado 2026-08-11

Ejecutado en el servidor de desarrollo. **Resultados medidos, no supuestos:**

- [x] Jar desplegado (825 KB, con HikariCP y MariaDB dentro por JiJ)
- [x] Fabric API 0.116.14 instalado — 45 mods cargados
- [x] Conexión a MariaDB establecida
- [x] **Migración V001 aplicada** sin error
- [x] **No se reaplica al reiniciar** — `schema_version` funciona
- [x] Comandos registrados: `/luna estado` responde; `/luna saldo` rechaza la
      consola con el mensaje correcto
- [x] **`failFast` verificado**: con credenciales inválidas el servidor
      registra `FALLO AL ARRANCAR` y **se detiene** en vez de jugar sin
      persistencia
- [x] Arranque limpio: `Done (4.786s)`, 647 MB de 4096

Cubierto por `MOD-005`, que no necesita jugadores conectados.

---

### MOD-005 — Autotest de invariantes económicos
`DONE` ✅ · verificado 2026-08-11 · **19/19 correctas**

`/luna autotest` (nivel 4). Ejecuta la batería contra jugadores sintéticos y
borra todo lo que crea.

- [x] Crédito y débito dejan el saldo correcto
- [x] La misma clave de idempotencia falla la segunda vez y **no mueve saldo**
- [x] Saldo insuficiente se rechaza y **no deja asiento en el libro**
- [x] `transfer` de Marcas se rechaza (`NOT_TRADEABLE`), sin tocar ningún saldo
- [x] Transferencia correcta: origen −300, destino +300
- [x] Transferencia imposible: **no toca a ninguno de los dos**
- [x] `auditDiscrepancy() == 0` en ambos jugadores y ambas monedas
- [x] Limpieza verificada

#### 🐛 Encontró un fallo real en su primera ejecución

`EconomyService.transfer()` deriva una clave por pata añadiendo `":out"` y
`":in"`. Pero `idempotency_key` era `CHAR(36)` y un UUID ocupa exactamente 36
caracteres:

```
Data too long for column 'idempotency_key'
```

**Las transferencias fallaban el 100 % de las veces.** No lo habría detectado
ninguna revisión de código, porque el error solo aparece contra la base real.
Corregido en la migración `V002` (`VARCHAR(64)`).

> Justifica por sí solo el coste de escribir el autotest, y valida el sistema
> de migraciones: V002 se aplicó sin reaplicar V001.

---

### MOD-006 — Ampliar el autotest según crezca el mod
`IDEA` · dep: `MOD-005` ✅

Cada sistema económico nuevo añade sus invariantes aquí **antes** de
desplegarse. Próximos: custodia del GTS (que un Pokémon listado no exista en
dos sitios), comisiones e impuestos, y concurrencia real con dos hilos
comprando el mismo listado.

---

### INF-007 — Backup de la base de datos, coordinado con el del mundo
`READY` · 🔴 Crítica · dep: `ARCH-003` ✅ · **bloquea producción**

Riesgo **nuevo**, creado por D-009: la BD MariaDB vive en un host distinto al
servidor de Minecraft. **`AdvancedBackups` copia el mundo pero NO la base de
datos.** Sin esto, añadimos un punto de fallo sin copia — y es justo el que
guarda la economía.

- [ ] `mysqldump` programado, almacenado **fuera** del hosting
- [ ] Frecuencia ≥ la del mundo (diaria)
- [ ] **Coordinado con el backup del mundo**: restaurar un mundo del martes con
      una base del jueves deja Pokémon que existen en una y no en el otro
- [ ] Restauración probada

---

### SEC-006 — Anti-abuso sin identidad de Mojang (B-004)
`DESIGN` · 🔴 Crítica · dep: ninguna

**Resuelto el "si": el servidor va en `online-mode=false` por obligación, no
por elección.** Verificado 2026-08-11: `TheJuanCE` **no existe como cuenta
premium** (`api.mojang.com` devuelve *"Couldn't find any profile"*). Con
`online-mode=true` el propietario no podría entrar en su propio servidor.

> El UUID offline calculado para ese nombre coincide **exactamente** con el de
> producción (`432ef323-…`), confirmando que los UUID offline son deterministas.

Queda el "cuánto": la multicuenta es imposible de erradicar, así que todo
límite "por jugador" hay que construirlo.

- [x] Determinar si `online-mode=true` es viable → **no lo es**
- [ ] **Carencia**: sin operar en GTS hasta X horas de juego real ← lo primero
- [ ] Límites por IP en operaciones económicas
- [ ] Evaluar vinculación a Discord para poder vender
- [ ] Detección de patrones alt→principal sobre `ledger_entry`

> Amortiguador ya en el diseño: la riqueza vive en **Pokémon**, no en dinero
> (`ECO-001` §5). Con valor en objetos únicos y rastreables, una granja de alts
> hace mucho menos daño que en una economía puramente monetaria.

---

### SEC-004 — Leer el texto oficial de Mojang (B-008)
`BLOCKED` · 🟠 Alta · dep: acceso a la web oficial

Intentado el 2026-08-11: `minecraft.net/usage-guidelines` **no responde**
(timeout, 3 intentos, 2 rutas). Las fuentes secundarias son marketing de
hostings y no sirven como autoridad.

Sí está establecido el principio general —*no vender ventajas que afecten a la
competición entre quien paga y quien no*— y nuestro T3 es justo la zona gris.
La regla del PvP de `monetization.md` §3 es la mitigación principal.

- [ ] Leer el texto oficial vigente **directamente** (lo puede hacer el usuario)
- [ ] Contrastar T3 punto por punto
- [ ] Si T3 queda restringido: apoyar el negocio en T1 + T2

---

### SEC-005 — Auditoría de licencias de todos los mods candidatos
`READY` · 🟠 Alta · dep: `ARCH-001` ✅

D-008: con monetización confirmada, **un solo mod con licencia no comercial
contamina el proyecto entero**. Es exactamente lo que descartó CobbleVerse.

- [ ] Licencia de cada mod candidato, antes de instalarlo
- [ ] Marcar los que exigen permiso escrito
- [ ] Aplicar también a los 134 de producción si algo se reutiliza

---

### ~~B-006~~ — ✅ CERRADA: el mod lo escribe Claude (D-011)

Era el supuesto más frágil del plan: toda la arquitectura (D-005, D-006, D-009)
descansa en un mod de servidor propio, y si no hubiera habido quien lo
escribiera, había que replantearlo todo hacia addons de terceros.

**Confirmado por el usuario el 2026-08-11.** Incluye el mod, el SQL y las
migraciones. El riesgo desaparece; queda el de mantenimiento a largo plazo, que
es normal y gestionable.

---

# 🟠 Alta prioridad

### INF-005 — Pedir a TaroHosting control de las flags de JVM
`READY` · dep: ninguna

Verificado: la API de cliente **solo permite cambiar variables del egg**. El
comando de arranque (`-Xms128M -Xmx4096M`) es exclusivo del administrador del
panel. Con `-Xmx` igual al límite del contenedor no hay margen para metaspace,
pilas ni buffers — el servidor de desarrollo ya murió así (SIGSEGV en G1).

- [ ] Solicitar ajuste del egg o flags personalizadas, **para ambos servidores**
- [ ] Si se deniega: compensar con menos mods y `view-distance` bajo

---

### SEC-002 — Modelo de autenticación (B-004)
`ANALYSIS` · dep: ninguna · bloquea el diseño económico

Producción usa `easyauth`, lo que apunta a modo offline. La identidad es la
clave primaria de dinero, Pokémon y progresión; si se suplanta, todos los
controles anti-abuso caen a la vez.

- [ ] Confirmar el `online-mode` real de producción
- [ ] Evaluar impacto de exigir cuenta legítima sobre la base de jugadores
- [ ] Si se mantiene offline: diseñar mitigaciones **antes** de la economía

---

### ARCH-002 — Diseño del mod propio de servidor
`IDEA` · dep: `B-006`

D-005 y D-006 lo declaran obligatorio. **Alcance cerrado a seis puntos** — todo
lo demás va por datapack:

1. Leer el equipo Pokémon del jugador (gating de zonas y retos)
2. GTS y mercado
3. Progresión transversal y desbloqueos
4. Calendario del servidor (ritmo largo, el que la luna no puede dar)
5. UI de navegación
6. Anti-abuso económico

> El alcance cerrado es la defensa contra el riesgo de subestimarlo.

---

### PROG-002 — Diseñar la distribución de rareza y el ciclo
`IDEA` · dep: `GD-002` aprobado

Cobblemon aporta las palancas; **nosotros decidimos los valores**. Es diseño
puro, sin código, y es donde vive la identidad del mundo.

Disponible de fábrica (verificado): `moonPhase`, `timeRange`, `isRaining`,
`isThundering`, `structures` (28 presets), `minY/maxY`, `neededNearbyBlocks`,
`minLureLevel` (1-3), `canSeeSky`, `minSkyLight/maxSkyLight`, `isSlimeChunk`,
`percentage`, `weight`, `weightMultiplier`.

Buckets nativos: `common` · `uncommon` · `rare` · `ultra-rare`.
Posiciones: `grounded` · `fishing` · `submerged` · `surface` · `seafloor`.

---

# 🟡 Media prioridad

| ID | Tarea | Estado | Dep. |
|---|---|---|---|
| `AUD-004` | Clasificar los 74 mods sin categorizar de producción | `IDEA` | `ARCH-001` |
| `AUD-005` | Auditar CobbleDollars: sources reales de dinero | `IDEA` | `ARCH-001` |
| `AUD-006` | Auditar grupos y permisos de LuckPerms | `IDEA` | — |
| `AUD-007` | Auditar loot tables y spawn rates | `IDEA` | `ARCH-001` |
| `AUD-008` | Medir la economía real de Diosesmon jugando en su servidor | `IDEA` | — |
| `ARCH-003` | Modelo de datos y persistencia | `IDEA` | `ARCH-001` |
| `ECO-001` | Economía: sources, sinks, velocity, inflación | `IDEA` | `GD-002` aprobado |
| `PROG-001` | Modelo de progresión y las 5 vías | `IDEA` | `GD-002` aprobado |
| `UI-001` | Árbol de navegación y estados de bloqueo | `IDEA` | `GD-002` aprobado |
| `INF-006` | Duplicar el backup de Backrooms a otro soporte | `IDEA` | — |

> `INF-006`: el `.tar.gz` de Backrooms es hoy la **única** copia de ese proyecto
> y vive en un solo disco. No es de Luna Eternal, pero lo dejamos anotado.

---

## Next Actions

1. **`SEC-001`** — rotar la key (minutos)
2. **`B-006`** — ¿hay quien escriba el mod? Cierra o tumba toda la arquitectura
3. **`SEC-006`** — decidir `online-mode`; condiciona el anti-abuso entero
4. **`INF-002`** — copia externa y restauración probada (el servidor de
   desarrollo está libre para usarlo de banco de pruebas)
5. **`B-005`** — aprobar visión, core loop, monetización y economía

### Listo para diseñar en cuanto se apruebe

`TRD-001` GTS y comisiones (el sink clave) · `ECO-003` telemetría ·
`PROG-001` progresión · `UI-001` navegación

## Related Systems

- [Infraestructura](../technical/infrastructure.md)
- [Visión](../game-design/vision.md) · [Core loop](../game-design/core-loop.md)
