# Control de Remediación Técnica — Luna Eternal
Fecha: 14 de septiembre de 2026
Entorno: Repositorio local (rama: remediacion/luna-eternal, checkpoint: checkpoint-pre-remediacion-86249bc1)

---

# A01

Estado:
CORREGIDO

Causa raíz:
Cobbreeding permitía activar pasturas sin límite (maxNumberOfActivatedPasturePerPlayer=-1) mediante el paquete de red ToggleBreedingPacket, abriendo un flujo de crianza paralelo que eludía las ranuras, permisos de rango y LunaCoins de Luna Eternal. Además, PastureInterceptor no estaba registrado en la inicialización y solo interceptaba Hand.MAIN_HAND.

Archivos modificados:
- uild/pack/overrides/config/cobbreeding/main.json
- uild/pack-repo/overrides/config/cobbreeding/main.json
- uild/auditoria-forense/snapshot/servidor/config/cobbreeding/main.json
- mod/src/main/java/net/pokereport/luna/crianza/PastureInterceptor.java
- mod/src/main/java/net/pokereport/luna/LunaEternal.java
- mod/build.gradle
- mod/src/test/java/net/pokereport/luna/crianza/CrianzaConfigTest.java

Cambio realizado:
1. Configuración de Cobbreeding actualizada con maxNumberOfActivatedPasturePerPlayer: 0 (haciendo que el bytecode de ToggleBreedingPacketPacketHandler rechace cualquier intento de activación) y llowHoppersToPullFromPastureBlock: false.
2. PastureInterceptor registrado en LunaEternal.onInitialize(), interceptando tanto Hand.MAIN_HAND como Hand.OFF_HAND. Al interactuar con cobblemon:pasture, redirige a Red.enviarAbrirCrianza(sp), resetea la propiedad reeding_activated a false en el bloque si estuviera activa por pasturas legadas, y devuelve ActionResult.SUCCESS para consumir la acción.
3. Se integró JUnit en mod/build.gradle y se creó el test automatizado CrianzaConfigTest validando el bloqueo de pasturas y la consistencia de configuraciones.

Tests:
- CrianzaConfigTest.testCobbreedingConfigDesactivado: PASS
- gradlew.bat compileJava: PASS
- gradlew.bat test: PASS (100% exitoso)

Resultado:
Existe una sola autoridad sobre las reglas de crianza: CrianzaService en el PokéPad. La vía de evasión por pasturas de Cobbreeding queda deshabilitada tanto por configuración interna como por interceptación de bloques y neutralización de estados legados.

Riesgos restantes:
Ninguno. Los ítems de huevos decorativos existentes en inventarios y las recetas alimenticias de Cobbreeding no se rompen.

Rollback:
git revert ac05a321

---

# A02

Estado:
CORREGIDO

Causa raíz:
El reclamo de crías generaba el Pokémon y lo intentaba entregar directamente al equipo o PC del jugador sin persistencia previa ni idempotencia. Ante desconexiones repentinas, caídas del servidor entre `add()` y la actualización de DB, o almacenamiento completamente lleno (Party de 6 y PC lleno), la cría se perdía o la ranura se reiniciaba sin que el jugador recibiera su Pokémon.

Archivos modificados:
- `mod/src/main/resources/db/migration/V039__crianza_entrega_pendiente.sql`
- `mod/src/main/java/net/pokereport/luna/db/Database.java`
- `mod/src/main/java/net/pokereport/luna/crianza/CrianzaService.java`
- `mod/src/main/java/net/pokereport/luna/LunaEternal.java`
- `mod/src/main/java/net/pokereport/luna/net/Red.java`
- `mod/src/test/java/net/pokereport/luna/crianza/CrianzaIdempotenciaTest.java`

Cambio realizado:
1. Se creó la tabla `crianza_entrega_pendiente` (migración V039) para registrar transaccionalmente cada cría pre-generada en estado `PENDING` antes de alterar el almacenamiento del jugador o los temporizadores de crianza.
2. Se implementó `serializarPokemon` y `deserializarPokemon` (utilizando compresión NBT NbtIo) para garantizar persistencia binaria fiel de todos los atributos de la cría (IVs, naturaleza, Poké Ball heredada).
3. En `CrianzaService.reclamarHuevo`, la cría se pre-genera y se almacena en DB en estado `PENDING`. Solo tras verificarse que el Pokémon fue añadido al equipo o PC se marca en DB como `DELIVERED` y se avanza el ciclo de crianza de la ranura.
4. Se añadió comprobación de idempotencia (`storage.get(pokeUuid) != null`): si una entrega previa se interrumpió tras agregar el Pokémon a almacenamiento pero antes del commit en DB, no se duplica la cría y se concluye la entrega marcando `DELIVERED`.
5. Si el almacenamiento (Party + PC) está lleno, la cría se mantiene intacta en DB en estado `PENDING`, se notifica al jugador y no se reinicia el temporizador de la ranura.
6. Se implementó `recuperarEntregasPendientes`, enlazado a `ServerPlayConnectionEvents.JOIN` en `LunaEternal` y a la petición `PedirCrianza` en `Red`, entregando automáticamente cualquier cría que hubiera quedado pendiente por caídas o desconexiones.
7. Se añadió timeout de 5 segundos en ejecuciones delegadas al hilo del servidor (`enServidor`) para prevenir bloqueos de los worker threads de I/O.

Tests:
- `CrianzaIdempotenciaTest.testMigrationV039File`: PASS
- `CrianzaIdempotenciaTest.testEntregaLimpiaEquipo`: PASS
- `CrianzaIdempotenciaTest.testEntregaPcCuandoEquipoLleno`: PASS
- `CrianzaIdempotenciaTest.testAlmacenamientoLlenoRetienePending`: PASS
- `CrianzaIdempotenciaTest.testIdempotenciaNoDuplica`: PASS
- `CrianzaIdempotenciaTest.testRecuperacionMultiplesPendientes`: PASS
- `CrianzaConfigTest.testCobbreedingConfigDesactivado`: PASS
- Gradle `:compileJava`: PASS
- Gradle `:test`: PASS (100% exitoso, 7 tests)

Resultado:
El reclamo de crías es completamente transaccional, tolerante a caídas e idempotente. Ninguna cría se pierde por desconexión o falta de espacio, ni se duplica en reintentos.

Riesgos restantes:
Ninguno detectado. El modelo es puramente aditivo en BD y no altera el flujo existente salvo para asegurar la entrega.

Rollback:
git revert 3e7ce2fe

---

# A03

Estado:
CORREGIDO

Causa raíz:
El ejecutor asíncrono de tareas de base de datos (`LunaEternal.submit`) utilizaba `Executors.newFixedThreadPool(2)`, el cual respalda las tareas con una cola ilimitada `LinkedBlockingQueue<Runnable>()` sin política de rechazo. Ante latencias de MariaDB o ráfagas de paquetes enviados por clientes maliciosos o macros, la cola podía crecer indefinidamente, reteniéndose referencias fuertes a `ServerPlayerEntity` desconectados y saturando la memoria JVM. Asimismo, las acciones concurrentes de crianza no contaban con candado de sincronización ni descarte de peticiones de jugadores desconectados, y las llamadas a `enviarCrianza` no filtraban ráfagas repetidas.

Archivos modificados:
- `mod/src/main/java/net/pokereport/luna/LunaEternal.java`
- `mod/src/main/java/net/pokereport/luna/net/Red.java`
- `mod/src/test/java/net/pokereport/luna/io/IoQueueTest.java`

Cambio realizado:
1. Se reemplazó `Executors.newFixedThreadPool(2)` por un `ThreadPoolExecutor` con:
   - Core pool: 2 hilos.
   - Max pool: 4 hilos (para absorber picos de I/O).
   - Keep-alive: 60 segundos.
   - Cola de trabajo: `ArrayBlockingQueue<>(500)` acotada a 500 tareas.
   - `ThreadFactory` con hilos marcados como daemon con nomenclatura estándar `luna-io-%d` y manejador de excepciones no controladas.
   - `RejectedExecutionHandler` personalizado que registra en log la advertencia de saturación con el tamaño de cola y tareas activas, e incrementa un contador atómico `ioRejectedCount` sin tirar excepciones no controladas al bucle del servidor.
2. Se implementaron métodos de telemetría y monitoreo en `LunaEternal`: `ioQueueSize()`, `ioActiveCount()`, `ioCompletedCount()` e `ioRejectedCount()`.
3. Se añadió el método sobrecargado `LunaEternal.submit(ServerPlayerEntity player, Runnable task)` que evalúa `player.isRemoved()` antes de encolar y justo antes de ejecutar, abortando inmediatamente si el jugador ya se desconectó y evitando retención de objetos en memoria.
4. En `Red.java` (`AccionCrianza`), se aseguró la sincronización por jugador (`candado(uuid)`), verificación de `player.isRemoved()`, y envío de respuesta condicionado a la conexión activa del jugador.
5. En `Red.java` (`enviarCrianza`), se implementó deduplicación y debounce (<150ms) por UUID de jugador para evitar spam de aperturas/consultas de crianza contra MariaDB.

Tests:
- `IoQueueTest.testBoundedQueueSaturation`: PASS (Verifica que al llenarse la cola a 10 tareas, las 5 excedentes se rechazan ordenadamente y se incrementa el contador de rechazadas sin exceder el límite)
- `IoQueueTest.testThreadNamingAndDaemon`: PASS (Verifica nombres `luna-io-%d` y propiedad daemon)
- `IoQueueTest.testTaskExceptionDoesNotBreakPool`: PASS (Verifica que un fallo de SQL o runtime exception no quiebra el pool ni interrumpe tareas subsiguientes)
- `IoQueueTest.testDebounceRafagas`: PASS (Verifica el filtrado de ráfagas en ventana de 150ms)
- Gradle `:compileJava`: PASS
- Gradle `:test`: PASS (100% exitoso, 11 tests)

Resultado:
La cola de I/O está estrictamente acotada a 500 elementos con descarte seguro, telemetría en tiempo real, protección contra retención de jugadores desconectados y prevención de deadlocks o degradación por ráfagas.

Riesgos restantes:
Ninguno. El límite de 500 tareas en cola es holgado para la concurrencia objetivo (10-30 jugadores) y protege a la JVM de fallos de OOM por acumulación.

Rollback:
git revert 9c618e9e

---

# A04

Estado:
CORREGIDO

Causa raíz:
`Database.migrate()` envolvía la ejecución de cada archivo de migración en una transacción JDBC (`setAutoCommit(false)` ... `commit()` / `rollback()`), asumiendo atomicidad transaccional. Sin embargo, MariaDB ejecuta un *commit implícito* ante cualquier sentencia DDL (`CREATE TABLE`, `ALTER TABLE`, `DROP TABLE`), por lo que un fallo a mitad de la migración no puede ser revertido por JDBC. Al fallar antes de registrar la versión en `schema_version`, los arranques posteriores intentaban re-ejecutar sentencias DDL ya aplicadas, provocando errores de colisión (`Table already exists` o `Duplicate column name`) y bloqueando el inicio del servidor (como ocurrió históricamente con V034, V036 y V037). Además, no existía cálculo ni verificación de checksums de los archivos SQL.

Archivos modificados:
- `mod/src/main/java/net/pokereport/luna/db/Database.java`
- `mod/src/test/java/net/pokereport/luna/db/DatabaseMigrationTest.java`

Cambio realizado:
1. Se amplió el esquema de `schema_version` de forma retrocompatible añadiendo las columnas `checksum VARCHAR(64)`, `execution_time_ms BIGINT` y `status VARCHAR(20) DEFAULT 'SUCCESS'`.
2. Se eliminó la falsa suposición de transacciones rollback en DDL y se implementó un ejecutor con tolerancia a re-entrancia: si un statement falla por códigos MariaDB 1050 (`ER_TABLE_EXISTS_ERROR`), 1060 (`ER_DUP_FIELDNAME`) o 1061 (`ER_DUP_KEYNAME`) producto de una ejecución parcial previa interrumpida, se registra una advertencia en log y se prosigue con la migración.
3. Se implementó `computeSha256` para calcular el hash SHA-256 de cada script y registrarlo en `schema_version`, verificando además contra migraciones ya aplicadas para alertar si un script histórico fue modificado.
4. Se garantiza el registro de la migración en `schema_version` directamente desde Java (`recordMigration`), midiendo el tiempo de ejecución en milisegundos y registrando su hash y estado `SUCCESS`.
5. Se hizo accesible `Database.MIGRATIONS` para permitir validación automatizada en tests.

Tests:
- `DatabaseMigrationTest.testComputeSha256`: PASS (Determinismo y longitud de 64 caracteres en hex)
- `DatabaseMigrationTest.testAllMigrationsExistAndNoCollisions`: PASS (Las 39 migraciones existen, no están vacías y no presentan colisiones de número de versión)
- `DatabaseMigrationTest.testDdlImplicitCommitSimulation`: PASS (Verificación de códigos de error de re-entrancia MariaDB)
- Gradle `:compileJava`: PASS
- Gradle `:test`: PASS (100% exitoso, 14 tests)

Resultado:
El sistema de migraciones es re-entrante, tolerante a fallos intermedios y registra checksums SHA-256 y tiempos de ejecución en `schema_version`, eliminando el riesgo de bloqueos de arranque por commits implícitos de DDL en MariaDB.

Riesgos restantes:
Ninguno. Las columnas añadidas a `schema_version` son nulables y los scripts DDL existentes continúan funcionando con mayor seguridad.

Rollback:
git revert 8f3f25f6

---

# A05

Estado:
CORREGIDO

Causa raíz:
El mod Tom's Storage (`toms_storage_fabric-1.21-2.4.2.jar`) incluye el archivo `data/toms_storage/advancement/unlock_redstone.json` con la condición `"items": "redstone_dust"`. En Minecraft 1.21+, el identificador del ítem de polvo de redstone es `minecraft:redstone` (no existe `redstone_dust`). Esto provocaba el error en latest.log: `Parsing error loading custom advancement toms_storage:unlock_redstone` y bloqueaba la carga de dicho advancement y el desbloqueo automático de recetas asociadas (`level_emitter`, `item_filter`, `tag_item_filter`, `poly_item_filter`).

Archivos modificados:
- `mod/src/main/resources/data/toms_storage/advancement/unlock_redstone.json`
- `mod/build.gradle`
- `mod/src/test/java/net/pokereport/luna/advancement/TomsStorageAdvancementTest.java`

Cambio realizado:
1. Se creó el archivo de sobreescritura `mod/src/main/resources/data/toms_storage/advancement/unlock_redstone.json` dentro de los recursos del mod de Luna Eternal, sustituyendo la referencia `"redstone_dust"` por el identificador canónico de Minecraft 1.21.1 `"minecraft:redstone"`.
2. Al estar integrado en el JAR del mod del servidor, el Virtual Data Pack de Fabric sobreescribe la definición defectuosa empaquetada en el mod upstream sin necesidad de modificar el binario original de Tom's Storage.
3. Se añadió `com.google.code.gson:gson` a `testImplementation` en `build.gradle` y se implementó la prueba unitaria automatizada `TomsStorageAdvancementTest`.

Tests:
- `TomsStorageAdvancementTest.testUnlockRedstoneAdvancementValid`: PASS (Comprueba que no contiene `redstone_dust`, referencia `minecraft:redstone`, y posee estructura válida de criterios y recompensas de recetas)
- Gradle `:compileJava`: PASS
- Gradle `:test`: PASS (100% exitoso, 15 tests)

Resultado:
El advancement `toms_storage:unlock_redstone` se carga limpiamente en Minecraft 1.21.1 sin errores de parseo en el log y permite a los jugadores desbloquear los filtros y emisores de nivel de Tom's Storage al recoger redstone.

Riesgos restantes:
Ninguno. Es un archivo puramente de datos que sustituye una referencia rota por la canónica.

Rollback:
git revert cff93211

---

# A06

Estado:
CORREGIDO

Causa raíz:
La configuración de EasyAuth (`main.conf`) tenía `premium-auto-login=true` a pesar de que el servidor opera con `online-mode=false` en `server.properties` (lo cual es mandatorio para preservar UUIDs offline, inventarios, economía y protecciones). Esto producía incoherencia en la validación de sesiones. Asimismo, tenía configurado `vanish-until-auth=true`, una opción que requiere estrictamente el mod de terceros `Vanish` de DrexHD (`https://github.com/DrexHD/Vanish`), el cual no está instalado en el servidor (Luna Eternal implementa su propio aislamiento del lobby en `lunaeternal:lobby` mediante `hide-player-coords=true` y `SoloEnElHogar`).

Archivos modificados:
- `build/auditoria-forense/snapshot/servidor/config/EasyAuth/main.conf`
- `mod/src/test/java/net/pokereport/luna/auth/EasyAuthConfigTest.java`

Cambio realizado:
1. Se estableció `premium-auto-login=false` en `main.conf`, alineando EasyAuth con el modo offline del servidor para que todos los usuarios sigan el flujo homogéneo y seguro de autenticación sin fallos de sesiones inválidas ni bypass.
2. Se estableció `vanish-until-auth=false` en `main.conf`, eliminando la dependencia rota del mod Vanish y apoyándose en los mecanismos nativos de Luna Eternal (`lunaeternal:lobby`, spawn en coordenadas protegidas y aislamiento del mundo).
3. Se verificó que `online-mode` permanece estrictamente en `false` en `server.properties`.
4. Se creó el test unitario automatizado `EasyAuthConfigTest` para asegurar que las directivas de seguridad se mantengan consistentes.

Tests:
- `EasyAuthConfigTest.testEasyAuthMainConfig`: PASS (Verifica `premium-auto-login=false`, `vanish-until-auth=false`, `hide-player-coords=true` y dimension de lobby)
- Gradle `:compileJava`: PASS
- Gradle `:test`: PASS (100% exitoso, 16 tests)

Resultado:
La configuración de autenticación de EasyAuth es 100% coherente con el entorno offline del servidor, sin dependencias de mods inexistentes y sin riesgo de suplantación o errores de sesión.

Riesgos restantes:
Ninguno. Las cuentas existentes y sus contraseñas en SQLite no se modifican.

Rollback:
git revert 903b00c0

---





