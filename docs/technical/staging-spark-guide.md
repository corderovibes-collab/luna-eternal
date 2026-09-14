# Guía de Pruebas de Rendimiento en Staging con Spark Profiler
**Proyecto:** Luna Eternal (Minecraft 1.21.1 Fabric / Cobblemon 1.8 / Java 21)  
**Módulo:** Staging & QA  
**Referencias:** A07 (Sizing JVM) y A08 (Protocolo de Profiling Spark)

---

## 1. Sizing de Memoria JVM (A07)

### 1.1 Restricciones del Contenedor
- **Límite de Memoria del Contenedor (cgroups / Docker):** `10.240 MiB` (10,0 GiB).
- **Problema previo:** En configuraciones anteriores, `-Xmx` se fijaba al 100% de la RAM del contenedor (p. ej. `-Xmx16384M` o `-Xmx4096M`). Esto provocaba que al aproximarse el consumo de Heap a `-Xmx`, la memoria adicional (Metaspace, stacks de hilos, buffers directos de red Netty, código compilado JIT y overhead del driver MariaDB) superara el límite del contenedor, activando el OOM Killer del kernel de Linux (`SIGSEGV` o `SIGKILL`, muerte de hilo G1 Conc).

### 1.2 Distribución Asignada
| Segmento de Memoria | Tamaño Asignado | Porcentaje | Propósito |
|---|---|---|---|
| **Heap (`-Xms` / `-Xmx`)** | `7.680 MiB` (7,5 GiB) | 75,0 % | Espacio de objetos Java: entidades, mundo, Cobblemon, cachés de mod |
| **Metaspace (`-XX:MaxMetaspaceSize`)** | `512 MiB` (0,5 GiB) | 5,0 % | Clases cargadas (134+ mods, Fabric, bytecode dinámico) |
| **Buffer Nativo & Off-Heap** | `2.048 MiB` (2,0 GiB) | 20,0 % | Hilos GC (G1), hilos I/O (`luna-io`), buffers de Netty, JDBC, stacks del SO |
| **Total Máximo Esperado** | `10.240 MiB` (10,0 GiB) | 100,0 % | Garantiza inmunidad ante el OOM Killer del contenedor |

### 1.3 Banderas de Arranque Recomendadas (Java 21 / G1GC)
```bash
java -Xms7680M -Xmx7680M \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=130 \
     -XX:+UnlockExperimentalVMOptions \
     -XX:+DisableExplicitGC \
     -XX:G1NewSizePercent=28 \
     -XX:G1MaxNewSizePercent=38 \
     -XX:G1ReservePercent=15 \
     -XX:InitiatingHeapOccupancyPercent=20 \
     -XX:G1MixedGCLiveThresholdPercent=85 \
     -XX:G1HeapRegionSize=8M \
     -XX:MaxMetaspaceSize=512M \
     -jar server.jar nogui
```

*Nota:* Mantener `-Xms` = `-Xmx` evita la fragmentación del heap y elimina las pausas de redimensionado dinámico de memoria durante picos de carga.

---

## 2. Protocolo de Profiling con Spark (A08)

### 2.1 Preparación
1. Asegurar la presencia del mod `spark` compatible con Fabric 1.21.1.
2. Iniciar el servidor de staging con las banderas JVM especificadas en la Sección 1.
3. Verificar la conectividad a MariaDB de staging con el pool HikariCP (mínimo 2 conexiones, máximo 10).

### 2.2 Los 7 Escenarios Obligatorios de Profiling

#### Escenario 1: Exploración y Generación de Chunks
- **Acción:** 2 jugadores volando rápidamente con montura Cobblemon por territorio no generado a velocidad >30 m/s.
- **Comando Spark:** `/spark sampler --timeout 180`
- **Métricas Esperadas:**
  - TPS sostenido: `>= 19.5`
  - MSPT: `< 45.0 ms`
  - Métodos críticos: `ThreadedAnvilChunkStorage`, generación asíncrona no debe bloquear el hilo principal.

#### Escenario 2: Reclamo Masivo de Crías y Concurrencia I/O
- **Acción:** 5 jugadores abriendo simultáneamente el PokéPad y reclamando 7 ranuras de crianza cada uno en un lapso de 15 segundos.
- **Comando Spark:** `/spark sampler --timeout 120`
- **Métricas Esperadas:**
  - Deadlocks o esperas en candado: `0`
  - Cola `luna-io`: `< 50` tareas acumuladas (límite 500)
  - Tiempo de ejecución de `reclamarHuevo` y `ejecutarEntrega`: `< 15 ms` en DB
  - Confirmación de idempotencia ante desconexión forzada de cliente.

#### Escenario 3: Combates en Gimnasio e Inteligencia Artificial
- **Acción:** 4 combates simultáneos contra líderes de gimnasio (RCTMod / Radical Cobblemon Trainers) con Pokémon nivel 80+ usando ataques con animaciones y partículas.
- **Comando Spark:** `/spark sampler --timeout 180`
- **Métricas Esperadas:**
  - Ticks de IA de combate: `< 8.0 ms`
  - Sin bloqueos de hilo principal en cálculo de daño o cambios de estado.

#### Escenario 4: Consultas de Alta Frecuencia en Mercado / GTS
- **Acción:** Simulación de 20 consultas por segundo de búsqueda, filtrado de categorías y creación/cancelación de órdenes de compra y venta.
- **Comando Spark:** `/spark sampler --timeout 120`
- **Métricas Esperadas:**
  - Conexiones activas en HikariCP: `< 8`
  - Tareas rechazadas (`ioRejectedCount`): `0`
  - Sin saturación de CPU en serialización NBT (`ItemCodec.decode`).

#### Escenario 5: Apertura Masiva de Cajas de Recompensa (Crates)
- **Acción:** Apertura continua de 50 cajas con tiradas aleatorias y emisión de partículas de recompensa.
- **Comando Spark:** `/spark sampler --timeout 60`
- **Métricas Esperadas:**
  - Sin pérdida de paquetes ni retrasos en la cola de red del cliente.
  - Generación de ítems limpia sin avisos de `MapLike[{}]`.

#### Escenario 6: Sincronización de Tablist y Rangos
- **Acción:** Sincronización periódica de 25 jugadores simulados con cálculo de rangos (`Leyenda`, `Maestro`, `Campeón`, `Élite`).
- **Comando Spark:** `/spark sampler --timeout 120`
- **Métricas Esperadas:**
  - Overhead de actualización de Tablist: `< 2.0 ms` por ciclo de refresco.
  - Sin acumulación de objetos temporales en la generación de componentes de texto.

#### Escenario 7: Ráfaga de Reconexión y Recuperación de Pendientes
- **Acción:** 15 jugadores reconectándose en un intervalo de 10 segundos tras un reinicio, ejecutando validación EasyAuth y `recuperarEntregasPendientes`.
- **Comando Spark:** `/spark sampler --timeout 180`
- **Métricas Esperadas:**
  - Tiempo medio de login: `< 1.5 s`
  - Conexiones MariaDB estables sin errores de timeout.
  - Entrega limpia de cualquier cría pendiente sin duplicados.

---

## 3. Criterios de Aprobación de Staging para Pase a Producción

1. **TPS:** Promedio `>= 19.5` en todos los escenarios; caídas momentáneas nunca inferiores a `18.0`.
2. **GC Pauses:** Ninguna pausa superior a `100 ms` en G1GC (percentil 99 `< 50 ms`).
3. **Memoria:** Retención de Heap post-GC `< 5.5 GiB` (de los 7.5 GiB asignados).
4. **I/O Threads:** Contador `LunaEternal.ioRejectedCount()` en `0`.
5. **Logs:** Ausencia total de `MapLike[{}]`, `Parsing error loading custom advancement`, o colisiones SQL.
