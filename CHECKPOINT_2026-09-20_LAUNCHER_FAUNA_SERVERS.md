# Checkpoint PokéReport — launcher, fauna y servidores

Fecha: 2026-09-20 (America/Bogota)

Este documento es el punto de recuperación de la intervención posterior a la
Fase 36E. No contiene tokens, contraseñas, claves SSH ni secretos de forwarding.

## Estado desplegado

- Endpoint oficial: `play.pokereport.online:25565` → `15.235.16.131:25565`.
- El puerto público termina en Velocity. Main (`25567`) y Paper Lobby (`25566`)
  permanecen ligados únicamente al bridge interno.
- Velocity, Cobblemon Main y Paper Lobby quedaron encendidos.
- El Lobby oficial de autenticación sigue siendo la dimensión
  `lunaeternal:lobby` de Main. Paper Lobby está disponible para
  construcción/pruebas, no es la ruta de login.
- Manifiesto vivo: `manifest-3592757b8f.json`.
- SHA-1 del manifiesto: `3592757b8f45aee1a54e3f93e3d29776f895d999`.
- LunaEternal desplegado, SHA-256:
  `96bcda650c520b1b80927f4327822036b7bb353e07f1616e6d8db576ae761bb7`.
- Respaldo puntual del JAR anterior:
  `/root/pokereport-phase36e/pre/lunaeternal-before-launcher-fauna.jar`.

## Reglas que no deben revertirse

1. El launcher y `servers.dat` apuntan exclusivamente al endpoint público de
   Velocity, nunca a los backends.
2. El cliente migra la dirección histórica sin borrar otros servidores del
   usuario.
3. Hogar (`minecraft:overworld`): densidad `0.04`, intervalo `200` ticks,
   únicamente Pokédex `1–251`, sin shiny ni etiquetas `legendary`, `mythical`,
   `ultra_beast`, `paradox` o `restricted`.
4. Salvaje y Salvaje2–6: densidad `3.0`, intervalo `15` ticks, únicamente
   Pokédex `1–251`; el bucket `ultra-rare` recibe solo `×1.10`.
5. RCT se conserva para NPCs oficiales colocados expresamente, pero el spawn
   natural queda en `0/0`, asociación natural desactivada.
6. Raid Dens conserva su JAR `0.11.7` por compatibilidad de registros/mundos,
   pero `enable_spawning=false` en todos los mundos.
7. TMCraft permanece en `1.4.19+1.8.0`, alineado con producción.
8. `config/cobblemon-cards.json` es una regla administrada del servidor; debe
   actualizarse. Los demás ajustes personales siguen protegidos con `once` o
   `keepExisting`.

## Verificaciones cerradas

- `gradlew test remapJar`: BUILD SUCCESSFUL.
- Launcher smoke test: `39` pasadas, `0` fallidas.
- Generación de packs jugador/constructor: Raid Dens y TMCraft clavados a las
  versiones de producción; RCT y Raid Dens salen desactivados.
- Producción: `Done (4.213s)`, `0/10` jugadores, Puerta activa, Tebex conectado,
  sin OOM/watchdog/crash fatal.
- DNS y TCP público `play.pokereport.online:25565`: correctos.

## Rollback mínimo

1. Confirmar cero jugadores.
2. Restaurar el JAR anterior desde `/root/pokereport-phase36e/pre/`.
3. Reiniciar únicamente Main mediante Wings.
4. Verificar `Done`, `/list`, Puerta, Tebex y acceso por Velocity.
5. Para revertir el launcher, volver a publicar el manifiesto anterior mediante
   el puntero `pack-manifest/latest.json`; no editar el puntero antes de subir
   todos los activos.

El informe ampliado está en `FASE_36E_PLAYER_ENTRY_FLOW_AUDIT.md`.
