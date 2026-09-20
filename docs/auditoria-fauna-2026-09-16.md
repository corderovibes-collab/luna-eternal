# Fauna, entrenadores y Raid Dens — 16/09/2026

## Auditoría

Se consultaron los mods y configuraciones del servidor activo, se contrastaron los nombres con el snapshot disponible y se revisaron referencias del código y dependencias declaradas.

- **RCT Mod y RCT API se conservan.** Los gimnasios, adaptador de combates, Oak y Buhonero usan RCT. `cobblemonbattlepositions` también declara dependencia de RCT API.
- **Raid Dens se conserva instalado con generación natural desactivada.** No se encontraron referencias directas al mod en el JAR de Luna Eternal. Quitar su registro de bloques podría afectar estructuras y objetos ya guardados; la petición específica es desactivarlo.
- No se retiraron mods por mera ausencia de referencias en Java: una dependencia puede estar en datos, bloques del mundo o recursos del cliente.

## Configuración aplicada

`config/rctmod-server.toml`:

```toml
globalSpawnChance = 0.0
globalSpawnChanceMinimum = 0.0
maxTrainersPerPlayer = 0
maxTrainersTotal = 0
spawnTrainerAssociation = false
```

Esto desactiva entrenadores ambientales y asociaciones aleatorias. Los NPC colocados por Luna mantienen sus entidades y equipos.

`config/cobblemonraiddens/common.json5`: `enable_spawning=false`. Aplica a la generación natural en todas las dimensiones, incluidas Hogar y los seis Salvajes. **No borra las estructuras/cristales previamente generados ni equivale a desactivar sus interacciones.** No se modificaron regiones ni construcciones para ocultarlos.

`config/cobblemon/main.json` conserva el valor base del mod; LunaEternal aplica
la política efectiva por jugador y dimensión. Además, Hogar rechaza cualquier
spawn salvaje shiny o con etiquetas `legendary`, `mythical`, `ultra_beast`,
`paradox` o `restricted`. Hogar y Salvaje rechazan todo número Pokédex fuera de
`1–251` tanto al aparecer como al cargar entidades antiguas.

El módulo `FaunaControl` ajusta el spawner de cada jugador al entrar o cambiar de dimensión:

| Dimensión | Límite de densidad natural | Intervalo de intentos |
|---|---:|---:|
| Hogar (`minecraft:overworld`) | 0,04 Pokémon/chunk | 200 ticks |
| Salvaje y Salvaje2–6 | 3,0 Pokémon/chunk | 15 ticks |
| Otras | 1 Pokémon/chunk | configuración normal |

Son parámetros del algoritmo de aparición, no una promesa de población exacta:
influyen biomas, condiciones, jugadores próximos y despawn. Solo en Salvaje el
bucket `ultra-rare` recibe un aumento pequeño de `×1,10` (peso base `0,30` a
`0,33`); sigue siendo excepcional y no garantiza un legendario. No se borran
Pokémon de jugadores. Los cebos y otros spawners especiales conservan su lógica
propia.

## Mobs vanilla y excepciones

- Se rechaza la creación de `MobEntity` cuyo tipo pertenece a `minecraft`, en todas las dimensiones. Incluye animales y monstruos; no incluye jugadores, objetos, vehículos, soportes de armadura ni displays.
- Se conservan Pokémon y mobs de otros namespaces.
- Excepciones para NPC personalizados basados en mobs vanilla: etiquetas `luna_*`/`custom_npc`, o nombre personalizado más IA desactivada. Para un NPC propio puede utilizarse la etiqueta administrativa `luna_custom_mob` antes de colocarlo.
- También se filtran entrenadores RCT ambientales no persistentes. Los persistentes y los NPC marcados por Luna están protegidos.
- Al cargar un mob antiguo que incumple la política, se guarda NBT con tipo, UUID y dimensión en `<mundo>/lunaeternal/fauna-archive/<uuid>.nbt` antes de retirarlo. Escritura por archivo temporal; si falla el respaldo no se elimina. No se usa `/kill` masivo y no se generan drops.
- Los chunks descargados no se barren a la fuerza: su fauna se procesa cuando se cargan.
- No se utiliza `doMobSpawning=false` global, para no bloquear también mobs personalizados que dependan del spawner vanilla. Cobblemon usa su propia regla `doPokemonSpawning`, comprobada como activa en Hogar y Salvaje.

## Preservación de cambios anteriores

La comparación contra el launcher vigente solo añadió las clases de fauna y cambió el registro de mixins y la inicialización del servidor. Se conservaron recursos del scoreboard, armaduras, PokéPad y la corrección visual del Buhonero.

Se detectó además que la inicialización del servidor publicada había perdido las llamadas de arranque de las paradas lunares y del Buhonero. Se restauraron esas dos llamadas existentes al integrar la política, sin cambiar su interfaz ni sus reglas de negocio.

## Evidencias y límites

Respaldo, configuración anterior/posterior, inventario de mods y diff: `build/spawn-audit/`. Compilación y tests correctos antes de desplegar. El despliegue sustituye únicamente Luna Eternal y tres configuraciones, con comprobación de hashes y rollback de esos archivos.

Los mods/comandos de homes, pwarps y beneficios de rango siguen pendientes de confirmación del usuario. No forman parte de este despliegue.
