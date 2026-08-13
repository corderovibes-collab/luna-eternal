# Infraestructura

## Purpose

Inventario verificado del hardware, hosting y configuración de servidor
disponibles para el proyecto. Define las restricciones físicas dentro de las
que debe caber cualquier diseño.

## Dependencies

Ninguna. Este es un documento raíz — otros dependen de él.

## Related Documents

- [current-server-audit.md](../analysis/current-server-audit.md)
- [../roadmap/backlog.md](../roadmap/backlog.md)

## Current Status

Auditado por API de Pterodactyl el **2026-08-11**. Datos verificados, no
estimados. **Tres riesgos críticos abiertos.**

## Last Decision

D-003 — Producción (`2a0a48ff`) es READ-ONLY hasta plan aprobado.

---

## 1. Hosting

**Proveedor:** TaroHosting · Panel Pterodactyl en `control.tarohosting.com`

| | Paquete Ender Dragon | Paquete Esqueleto |
|---|---|---|
| **ID** | `2a0a48ff` | `7dc30799` |
| **Nodo** | `s17.mia.us.tarohosting.lat` | `s12.mia.us.tarohosting.lat` |
| **RAM** | 16 384 MB | 4 096 MB |
| **CPU** | 300 % (3 núcleos) | 300 % (3 núcleos) |
| **Disco** | 184 320 MB (70 795 usados, 38 %) | 61 440 MB (2 977 usados, 5 %) |
| **Minecraft** | 1.21.1 Fabric (loader 0.18.4) | 1.20.1 Fabric (loader 0.15.11) |
| **Mods** | 134 (419 MB) | 10 |
| **Estado** | 🟢 running, 14 061 MB usados | ⚫ offline |
| **Contenido** | PokeReport / CobbleVerse | **Backrooms Streamer Survival** |
| **Backups** | **0** (límite del plan: 0) | **0** |

Dirección pública de producción: `s17.mia.us.tarohosting.lat:33445`

---

## 2. RIESGO CRÍTICO 1 — Los backups existen, pero viven en el mismo disco

**Matiz importante.** El panel de Pterodactyl tiene el límite de backups en
**0**: no se pueden crear copias desde el panel. Pero el mod `AdvancedBackups`
**sí está funcionando** en producción (verificado 2026-08-11):

```
14 copias · 49,96 GB · diaria a las 05:00 · última hoy 05:04
último full : 2026-08-07 (12,72 GB)
después     : diferenciales (2,34 → 3,88 → 5,23 → 5,50 GB)
config      : type=differential · chains.length=7 · purge.days=14 · purge.size=100 GB
              frequency.shutdown=true · path=./backups
```

Así que hay protección real contra corrupción, griefing y errores de comando.
Quedan **tres problemas serios**:

**1 · `path=./backups` — el mismo disco que protegen.**
50 GB de copias sobre el mismo volumen que el servidor. Protege de errores
lógicos; **no protege de fallo de disco, del nodo, ni de cierre de la cuenta.**
Un backup que muere con el original no es un backup — es una papelera.

**2 · Ocupan el 71 % del disco usado.**
De los 70,8 GB usados, 50 son backups y ~20 el servidor. Con `purge.size=100 GB`
pueden crecer hasta 100 GB, y el mundo sigue creciendo (la pregeneración quedó
al 30 %). **Un disco lleno en Pterodactyl detiene el servidor y puede corromper
el guardado** — el remedio se convertiría en la causa.

**3 · Cadena diferencial sin full reciente.**
El último completo es del 7-ago. Los cuatro diferenciales posteriores dependen
de él: **si ese full está corrupto, se pierden 4 días de golpe.** Y nunca se ha
probado una restauración.

> **Un backup no verificado no existe.** Es la acción con mejor relación
> coste/beneficio de todo el proyecto: `INF-002`.

**Nada crítico debería tocarse antes de tener una copia fuera del servidor y
una restauración probada.**

---

## 3. RESUELTO — Servidor de desarrollo asignado

El servidor de 4 GB alojaba otro proyecto del usuario (*Backrooms Streamer
Survival*: 11 dimensiones propias, MC 1.20.1). **El usuario decidió liberarlo**
(decisión D-004).

### Procedimiento ejecutado — 2026-08-11

```
1. Verificado el backup del usuario ANTES de tocar nada:
   D:\BackupsBackrooms\backrooms-tarohosting-2026-08-11.tar.gz  (65 MB)
   gzip íntegro · 215 entradas · world/level.dat · 96 ficheros .mca
   · playerdata · los 10 mods · todos los configs
2. Borradas las 26 entradas de la raíz          → HTTP 204
3. Variables de arranque al espejo de producción:
   MC_VERSION=1.21.1 · LOADER_VERSION=0.18.4 · FABRIC_VERSION=1.1.1
4. Reinstalación                                → HTTP 202
5. Resultado: raíz limpia, Fabric 1.21.1
6. EULA aceptado y arranque de verificación
```

**Estado:** servidor de desarrollo limpio en **MC 1.21.1 Fabric**, espejo de
versión de producción.

> El backup de Backrooms es la **única** copia de ese proyecto. Conviene
> duplicarlo a otro soporte; hoy vive en un solo disco.

---

## 4. RIESGO CRÍTICO 3 — `-Xmx` igual al límite del contenedor

Ambos servidores arrancan así:

```
Ender Dragon   java -Xms128M -Xmx16384M -jar server.jar    (contenedor: 16384 MB)
Esqueleto      java -Xms128M -Xmx4096M  -jar server.jar    (contenedor:  4096 MB)
```

**El heap por sí solo puede crecer hasta ocupar todo el contenedor.** La JVM
necesita además metaspace, pilas de hilos, code cache, buffers directos de red
y las estructuras internas de G1. Ese consumo vive *fuera* del heap, así que
cuando el heap se acerca a `Xmx` el proceso total ya excede el límite y el
kernel mata el contenedor — o la propia JVM revienta.

**Esto ya ocurrió.** El servidor de 4 GB tiene un `hs_err_pid30.log`:

```
SIGSEGV (0xb)  en  G1CMTask::process_grey_task_entry<true>
hilo: ConcurrentGCThread "G1 Conc#0"
tras 1 h 10 m de ejecución
Host: 4G RAM
```

Un SIGSEGV **dentro del hilo concurrente de G1** con el heap dimensionado al
100 % del contenedor es el patrón clásico de muerte por presión de memoria.
Dejó un **core dump de 2,77 GB** (`/core`) que hoy es el 93 % del disco usado.

Producción corre la misma configuración a mayor escala, y va al **86 % de RAM
(14 061 / 16 384 MB)**.

**Corrección recomendada** — dejar cabecera para la JVM y afinar G1:

```
-Xms10G -Xmx10G                      (producción: ~65-75 % del contenedor)
-XX:+UseG1GC -XX:MaxGCPauseMillis=200
-XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40
-XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20
-XX:InitiatingHeapOccupancyPercent=15
-XX:MaxMetaspaceSize=512m
```

`Xms = Xmx` evita que el heap crezca y decrezca; con 134 mods el heap nunca
baja de forma útil y el redimensionado solo añade pausas.

> No aplicar todavía: requiere ventana de reinicio y backup previo (`INF-003`).

### ⚠️ Obstáculo: el comando de arranque no es editable por nosotros

Verificado 2026-08-11: la API de cliente de Pterodactyl **solo permite cambiar
las variables del egg** (`MC_VERSION`, `LOADER_VERSION`, `FABRIC_VERSION`,
`SERVER_JARFILE`). El comando de arranque —donde viven `-Xmx` y las flags de
GC— lo define el egg y es **exclusivo del administrador del panel**.

Tras la reinstalación el comando sigue siendo:
```
java -Xms128M -Xmx4096M -jar server.jar
```

Opciones reales:
1. **Pedirlo a TaroHosting** — que ajusten el egg o habiliten flags de JVM.
   Es la vía correcta y la que hay que intentar primero.
2. Un jar lanzador que arranque una segunda JVM con flags propias — **mala
   idea con 4 GB**: duplica el consumo base.
3. Convivir con ello y compensar con menos mods y menor `view-distance`.

Sin la opción 1, el riesgo de OOM permanece. Debe pedirse **para los dos
servidores**, pero especialmente para producción.

---

## 5. Seguridad

| Hallazgo | Detalle | Severidad |
|---|---|---|
| API key de Pterodactyl comprometida | `ptlc_…` circuló en texto plano. Da acceso total: consola, archivos, power. Ya señalada como pendiente en `PokeReport 2` desde el 3-ago y **sigue sin rotarse** | 🔴 alta |
| Contraseña RCON en claro | En `server.properties` del servidor de 4 GB, legible por API | 🟠 media |
| `online-mode=false` (dev) | Sin autenticación de Mojang: cualquiera puede conectarse con cualquier nombre | 🔴 alta para MMORPG |
| `enforce-secure-profile=false` | Sin firma de mensajes | 🟢 baja |

### Por qué `online-mode` decide la arquitectura económica

Producción lleva `easyauth` instalado, lo que indica **modo offline con
autenticación por contraseña**. En un MMORPG con economía real esto importa
mucho más que en un servidor de supervivencia:

- La identidad del jugador es la clave primaria de todo el modelo de datos
  (dinero, Pokémon, progresión, listados de GTS).
- Si la identidad se puede suplantar, **todos** los controles anti-abuso caen a
  la vez: multi-cuenta gratuita, transferencia de riqueza, manipulación de
  mercado.
- El coste de arreglarlo crece con cada sistema que se construya encima.

Es la decisión **B-004** y debe cerrarse antes de diseñar la economía.

---

## 6. ¿Caben 4 GB para un MMORPG Cobblemon?

Evidencia disponible, no estimación:

- Producción sostiene **134 mods con 14 GB de heap** en Minecraft 1.21.1.
- Cobblemon carga ~1 000 especies con modelos, animaciones y datos de combate.
- El servidor de 4 GB **murió por GC con solo 10 mods** (aunque uno de ellos
  genera 11 dimensiones).

**Conclusión preliminar:** 4 GB sirve para un banco de pruebas de sistemas
aislados (economía, UI, datos, un par de jugadores). **No sirve** como réplica
de producción ni para pruebas de carga. Cualquier plan que asuma "desarrollamos
en los 4 GB y migramos" debe asumir que el rendimiento medido allí **no es
transferible**.

Se cuantifica en `B-003` tras la auditoría de Cobblemon.

---

## 7. Tooling existente

`D:\PokeReport 2\scripts\ptero.py` — cliente de la API de Pterodactyl ya
escrito y funcional: listar, leer, escribir, subir, descomprimir, borrar,
power, comandos, variables de arranque, reinstalar.

Incluye un detalle no obvio y valioso: **Cloudflare rechaza el User-Agent por
defecto de `urllib` con error 1010**, así que fuerza un UA de navegador.
Reutilizable tal cual.

---

## Next Actions

1. `INF-001` — Decidir dónde se desarrolla (bloqueante).
2. `INF-002` — Verificar destino y rotación de `AdvancedBackups` en producción.
3. `SEC-001` — Rotar la API key de Pterodactyl y la contraseña RCON.
4. `INF-004` — Borrar el core dump de 2,77 GB del servidor de 4 GB (previa
   confirmación del dueño del proyecto Backrooms).
5. `INF-003` — Corregir flags de JVM en producción, con backup previo.

## Related Systems

- Modelo de datos y persistencia — pendiente
- Seguridad económica y anti-abuso — pendiente
