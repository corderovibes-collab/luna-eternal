# Backlog

## Purpose

Registro único de tareas con ID estable, estado y criterios de aceptación.
Cualquier trabajo empieza por una entrada aquí.

## Dependencies

- [`../../CLAUDE.md`](../../CLAUDE.md)

## Related Documents

- [`../ui/interfaces-catalog.md`](../ui/interfaces-catalog.md) — pantallas
- [`../technical/infrastructure.md`](../technical/infrastructure.md)

## Current Status

PHASE 0 y 1 cerradas. PHASE 2-3 en curso. **3 pendientes del usuario**,
ninguno bloquea el desarrollo. Actualizado 2026-08-11.

## Last Decision

D-020 — Los cofres incluyen legendarios (ver [treasures.md](../economy/treasures.md)).

---

## Estados

`IDEA · ANALYSIS · DESIGN · READY · IN_PROGRESS · TESTING · REVIEW · DONE · BLOCKED · DEPRECATED`

## Prefijos

`INF` infraestructura · `SEC` seguridad · `AUD` auditoría · `ARCH` arquitectura
`ECO` economía · `PROG` progresión · `UI` interfaz · `PKM` Pokémon
`QST` quests · `TRD` comercio · `WLD` mundo · `SOC` social · `GD` diseño
`MOD` mod · `ART` arte

---

# 🔴 Pendiente del usuario

Nada de esto bloquea el desarrollo, pero **sí bloquea el lanzamiento**.

| ID | Qué | Por qué importa |
|---|---|---|
| `SEC-001` | **Rotar la API key de Pterodactyl** | Circuló en texto plano. Da control total de ambos servidores. Pendiente desde el 3-ago |
| `INF-002` | **Copia de seguridad fuera del hosting** | Los backups existen pero viven en el mismo disco que protegen. Y nunca se ha probado una restauración |
| `INF-007` | **Backup de la base de datos**, coordinado con el del mundo | `AdvancedBackups` copia el mundo pero **no** MariaDB, que está en otro host. Riesgo creado por D-009 |
| `SEC-004` | Leer el texto oficial de Mojang sobre monetización | Inaccesible desde aquí (timeout ×3). Condiciona el catálogo de tienda |
| `SEC-006` | Diseñar el anti-abuso sin identidad de Mojang | `online-mode=true` **no es viable** (verificado). Lo primero: carencia para operar en GTS |
| `B-003` | ¿4 GB bastan? | Sirve para desarrollo. **No** como réplica de producción ni para pruebas de carga |

---

# ✅ Completadas

### Auditoría y arquitectura (PHASE 0-1)

| ID | Tarea | Resultado |
|---|---|---|
| `AUD-000` | Infraestructura | 2 servidores por API; 3 riesgos críticos → `infrastructure.md` |
| `AUD-001` | Cobblemon oficial | Frontera nativo/propio; `moonPhase` y `minLureLevel` confirmados leyendo el jar |
| `AUD-002` | Diosesmon | 22 apps y su recorrido, de su wiki → `diosesmon-analysis.md` |
| `AUD-003` | Proyecto actual | Es un **evento**, no un MMORPG → `current-server-audit.md` |
| `AUD-005` | CobbleDollars | **Sin arbitraje**; márgenes 4-12× a favor de la casa |
| `SEC-002` | `online-mode` de producción | Es `false`. Confirmado por properties **y** por UUIDs v3 |
| `SEC-003` | ¿Monetiza producción? | **No hay señales de tienda.** Riesgo legal actual: bajo |
| `ARCH-001` | CobbleVerse vs Cobblemon | **Descartado CobbleVerse** por licencia ARR |
| `ARCH-003` | Modelo de datos | MariaDB + 5 reglas de esquema → `data-model.md` |
| `INF-001` | Servidor de desarrollo | `7dc30799` formateado, MC 1.21.1, backup previo verificado |
| `INF-004` | Core dump de 2,77 GB | Eliminado |
| `GD-001` · `GD-002` | Visión y core loop | Tres bucles anidados → `vision.md`, `core-loop.md` |
| `ECO-001` · `ECO-002` | Economía y monetización | 3 monedas en bucles cerrados; 4 niveles y test de 6 preguntas |
| `ECO-004` | ReportCoin | Tercera moneda, migración V003 |
| `PROG-001` | Progresión | 5 vías, sin nivel de jugador |
| `TRD-001` | GTS (diseño) | Acceso G0-G4, tasa 1 % + impuesto progresivo |
| `UI-001` | Navegación | **El Almanaque**, 4 grupos |
| `WLD-001` | Estructura del mundo | Lobby · ciudadela · mundo → `world-structure.md`, `worlds.md` |
| `PKM-001` | Fase lunar en spawns | ✅ Existe. **Pero el ciclo dura 2 h 40 min**, no una semana |

### Implementación (PHASE 2-3)

| ID | Tarea | Verificado |
|---|---|---|
| `MOD-001` | Esqueleto Gradle + Fabric 1.21.1 + Java 21 | compila |
| `MOD-002` | Persistencia: Hikari, migraciones, V001 | migración aplicada y no reaplicada |
| `MOD-003` | Identidad y economía (R1-R5) | — |
| `MOD-004` | Prueba contra la base real | `failFast` impide arrancar sin BD |
| `MOD-005` | **Autotest de invariantes** | **36/36.** Encontró un fallo real (ver abajo) |
| `UI-002` | Framework de menús | — |
| `UI-005` | El Almanaque | — |
| `UI-003` | Barra lateral y objeto del Almanaque | — |
| `UI-010` | Tablist con rangos | 8 equipos creados |
| `UI-007` | Cartera | — |
| `UI-008` | Vías | V004 aplicada |
| `UI-009` | Puerta del Mundo | teletransporta |
| `UI-011` | **Tienda** | **guardián anti-arbitraje probado rompiéndolo** |
| `WLD-002` | Las 4 dimensiones | carpetas creadas, bloques colocados |
| `BLD-001` | **Herramientas de construcción**: WorldEdit 7.3.8 + Axiom 5.4.2 en servidor y cliente, plataforma 80×80 en la ciudadela | ambos mods cargados; `CIUDADELA-SUELO-OK`; SHA1 del jar local = Modrinth |
| `BLD-002` | ~~Whitelist de Axiom~~ **innecesaria** | su API devuelve `commercial:false` → `YES` → `allowedOnServer=true` |

> **Dos fallos reales que solo aparecieron al probar contra el servidor**, y
> que ninguna revisión de código habría visto:
> 1. `idempotency_key` era `CHAR(36)` y un UUID ocupa exactamente 36: **las
>    transferencias fallaban el 100 % de las veces**. Migración V002.
> 2. En una dimensión sin jugadores los chunks no están cargados y escribir
>    bloques se ignora en silencio: el jugador **habría caído al vacío**.
>
> De ahí la regla `MOD-006`.

---

# 🟠 Siguiente

### UI-012 — GTS
`IN_PROGRESS` · dep: `TRD-001` ✅ · `ARCH-003` ✅

El último sistema grande. Sostiene la economía: su impuesto progresivo es el
**único sink que escala con la riqueza**.

- [ ] Migración: tabla `gts_listing` con custodia
- [ ] Publicar (tasa 1 % por adelantado, no reembolsable)
- [ ] Comprar con `SELECT … FOR UPDATE` — impide doble compra
- [ ] Impuesto progresivo, destruido (no va a ningún fondo)
- [ ] Cancelar y recuperar
- [ ] Caducidad
- [ ] **Invariantes en el autotest antes de desplegar** (`MOD-006`)

> Empieza por **objetos**. Los Pokémon usan el mismo esquema y el mismo flujo;
> solo cambia qué se serializa, y hoy Cobblemon no está instalado.

### ECO-005 — Las Marcas no se gastan en NADA

**Estado: pendiente. No rompe nada hoy, y se pone más caro cada día.**

Hay **ocho sitios que dan Marcas y cero que las cobren.** Medido con `grep` el
2026-08-25, no supuesto:

| Se ganan en | Se gastan en |
|---|---|
| Primera captura de cada especie (Pokédex) | — |
| Cazas y crianza | — |
| Misiones | — |

**El diseño está claro y es bueno.** Las Marcas son lo que hace que el modelo
de pago no rompa el juego:

```
No se compra poder       LunaCoins no llega ni a Plata ni a Marcas
No se compra progresión  las Marcas SOLO se ganan jugando
Pagar no infla nada      LunaCoins nunca entra al mercado
```

`economy-overview.md` §🔷 lo dice: la idea es **una tienda que el dinero no
puede comprar** — desbloqueos de progresión, títulos de prestigio, funciones
avanzadas. Así el rico no se salta la progresión pagándola y el veterano tiene
algo que solo él tiene.

**Hoy esa mitad no existe.** Son un número que sube.

> ⚠⚠ **POR QUÉ SE PONE MÁS CARO CADA DÍA, y es la razón de apuntarlo ahora.**
> Los precios de una moneda se fijan contra lo que la gente tiene. Cada día que
> pasa, todo el mundo acumula Marcas sin gastar ninguna — así que cuando llegue
> la tienda habrá que elegir entre poner precios altos (y que los nuevos no
> lleguen nunca) o precios normales (y que los veteranos lo compren todo el
> primer día). **Ninguna de las dos es buena, y las dos se evitan hoy.**
>
> Es el mismo problema que ya está anotado para el índice de precios del
> mercado, al revés: aquel necesita esperar a que haya datos, y este necesita
> **no** esperar a que haya saldos.

> ⚠ Y hay una salida barata si esto se alarga: **no hace falta la tienda entera
> para frenar la acumulación.** Basta con que exista *algo* que las cobre —un
> título, un desbloqueo— para tener una referencia de cuánto vale una Marca.

**Lo natural cuando toque:** una pestaña de Marcas en la tienda, que ya tiene
las categorías en el panel izquierdo y ya sabe manejar más de una moneda
(`ArticuloTienda` lleva el campo `moneda` desde el primer día).

---

### MOD-006 — Ampliar el autotest con cada sistema
`READY` · permanente

Regla establecida: ningún sistema económico se despliega sin sus invariantes.
Próximos: custodia del GTS (que un objeto listado no exista en dos sitios),
comisiones, y concurrencia real de dos compradores.

### PKM-004 — Datapack de generaciones
`READY` · dep: `AUD-001` ✅

Apagar todo lo que no sea Kanto y Johto con `"enabled": false`. Script que
genera los ~774 ficheros; auditar cadenas evolutivas que cruzan generaciones.

### INF-008 — JDK 21 propio
`READY`

El único JDK 21 completo es el runtime de PrismLauncher: puede desaparecer en
una actualización y dejar el proyecto sin compilar. `mod/build.sh` ya
sobrescribe el `org.gradle.java.home` global (que es de Backrooms y **no se
puede tocar**).

---

# 🟡 Después

| ID | Tarea | Dep. |
|---|---|---|
| `UI-013` | Pokédex | `PKM-004` |
| `UI-014` | Kits | rangos |
| `UI-015` | Tesoros | tablas de probabilidad |
| `ECO-003` | Telemetría económica sobre `ledger_entry` | — |
| `ECO-005` | Tablas de probabilidad y piedad acumulada | D-020 |
| `PROG-002` | Catálogo de desbloqueos | — |
| `PROG-003` | Medallas y gimnasios | — |
| `WLD-003` | Protocolo de reinicio del Salvaje | `INF-002`, `INF-007` |
| `WLD-005` | **Construir la ciudadela** (no es programación) | `BLD-001` ✅ |
| `WLD-006` | Fijar spawn de la ciudadela, NPC del laboratorio, destinos de `LunaTaxi` y `PCLink` en coordenadas reales | `WLD-005` |
| `PKM-002` | Diseño del inicial | — |
| `PKM-003` | Cupo y condiciones de legendarios | — |
| `SOC-001` | Clanes | — |
| ~~`UI-016`~~ · ~~`UI-017`~~ · ~~`UI-018`~~ · ~~`UI-019`~~ · ~~`UI-020`~~ | **Anuladas por D-026.** Eran el camino del resource pack sobre menús de cofre: se retiró entero | — |
| **`ART-002`** | 🔴 **Arte de la interfaz de cliente** — fondos, celdas, iconos, botones. **Es del usuario y bloquea toda la UI** | — |
| `UI-021` | Rehacer `mod/src/client/` y el protocolo, con el arte ya medido | `ART-002` |
| `UI-022` | Las pantallas, por orden del catálogo | `UI-021` |
| ~~`LNC-001`~~ | ~~Adaptar el launcher de Electron~~ ✅ **hecho** — `launcher/`, 25/25 pruebas, `.exe` compilado | — |
| **`LNC-002`** | Crear el secreto `PACK_TOKEN` para publicar releases del launcher | — |
| `LNC-003` | Arte propio del launcher: icono e imagen del raíl (hoy son los del proyecto anterior) | `ART-002` |
| **`WLD-007`** | ⏰ **Pedir la whitelist de servidor de Axiom** — límite ~2026-09-10 | — |
| `WLD-008` | Dar de alta a los constructores: whitelist + `ops.json` nivel **2** | `WLD-007` |
| `INF-009` | Quitar `require-resource-pack` del servidor: el pack se borró con los menús | — |
| `SEC-005` | Licencias de todos los mods candidatos | D-008 |
| `AUD-004` | Clasificar los 74 mods sin categorizar | — |
| `INF-003` | Flags de JVM | `INF-005` |
| `INF-005` | Pedir a TaroHosting control de las flags | — |
| `INF-006` | Duplicar el backup de Backrooms | — |

---

## Next Actions

1. **`UI-012`** — GTS, empezando por el esquema y los invariantes
2. `PKM-004` — datapack de generaciones
3. `ECO-003` — telemetría, que es lo que permitirá calibrar de verdad

## Related Systems

- [Catálogo de interfaces](../ui/interfaces-catalog.md) · [GTS](../trading/gts.md)
- [Infraestructura](../technical/infrastructure.md)
