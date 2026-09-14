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

