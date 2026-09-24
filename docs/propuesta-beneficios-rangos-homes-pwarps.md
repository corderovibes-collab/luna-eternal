# Propuesta de beneficios, homes y pwarps

> ⚠️ **DOCUMENTO HISTÓRICO / SUPERADO**: Esta propuesta inicial fue ajustada según los requerimientos finales del servidor (descartando `ptime`, `pweather`, `hat` y mods externos). El sistema final nativo desplegado y verificado se encuentra documentado en **[`beneficios-rangos-homes-tpa-2026-09-16.md`](beneficios-rangos-homes-tpa-2026-09-16.md)**.

Estado: Superado por implementación nativa y desplegado el 16/09/2026.

## Requisitos confirmados

| Rango | Comandos exclusivos acumulados | Homes | Conserva XP al morir |
|---|---:|---:|---|
| Entrenador/sin rango | 0 | 0 | No |
| Élite | 2 | 3 | No |
| Campeón | 5 | 4 | No |
| Maestro | 10 | 5 | No |
| Leyenda | 14 | 7 | Sí |

Los rangos superiores heredan los anteriores. Las acciones de gestión de homes no inflan el número de comandos exclusivos. Los alias tampoco cuentan como beneficios distintos.

## Hallazgos del servidor

- Fabric 1.21.1; la consulta actual de `/mods` no encontró un proveedor de homes, pwarps ni LuckPerms.
- `LunaDimensions.HOGAR` es `minecraft:overworld`.
- La app `warps` del PokéPad abre `Viajes`: sirve a las paradas de la Ciudadela y debe conservarse.
- `Regreso` guarda la última ubicación al viajar entre mundos. No es una colección de homes y no debe reutilizarse como tal.
- El rango real existe en MariaDB y se consulta mediante `RankService.enCache`. Ese sistema debe seguir siendo la fuente de autoridad.

## Comandos propuestos

| Se desbloquea en | Comando | Función |
|---|---|---|
| Élite | `/craft` | Mesa de trabajo portátil |
| Élite | `/basura` | Contenedor para descartar objetos, con confirmación |
| Campeón | `/enderchest` | Cofre de Ender propio |
| Campeón | `/stonecutter` | Cortapiedras portátil |
| Campeón | `/loom` | Telar portátil |
| Maestro | `/anvil` | Yunque portátil, conservando materiales y coste normal en XP |
| Maestro | `/grindstone` | Afiladora portátil, con mecánica vanilla |
| Maestro | `/cartography` | Mesa de cartografía portátil |
| Maestro | `/smithing` | Mesa de herrería, consumiendo ingredientes normales |
| Maestro | `/back` | Regreso al último teletransporte elegible dentro del Hogar; no a muertes ni arenas |
| Leyenda | `/ptime` | Hora visual personal; no altera el reloj, spawns ni mecánicas del servidor |
| Leyenda | `/pweather` | Clima visual personal; no altera el clima global |
| Leyenda | `/hat` | Sombrero cosmético con objetos admitidos; sin eludir rangos ni Maldición de Ligamiento |
| Leyenda | `/rename` | Renombrar el objeto propio; texto limitado sin formato arbitrario |

Son propuestas de selección, no comandos ya implementados. Los comandos portátiles deben abrir los contenedores reales con sincronización y devolución de objetos al cerrar/desconectar; no copiar inventarios mediante snapshots.

## Mods e integración recomendada

1. **HuskHomes para Fabric 1.21.1**: persistencia de homes, hogares públicos, límites numéricos y teletransportes. Se verificó la existencia de distribuciones 4.9.x para esa versión; fijar la versión exacta y su hash al preparar el despliegue.
2. **LuckPerms para Fabric**: permisos explícitos, herencia y contextos. Sin permisos administrativos ni comodines para rangos comerciales.
3. **Módulo en Luna Eternal**: sincroniza el rango existente con permisos, impone restricciones de dimensión y combate, ofrece los comandos portátiles y la conservación de experiencia.

Alternativa revisada: Essential Commands, con soporte Fabric 1.21.1 y homes/warps. HuskHomes encaja mejor con hogares públicos; no instalar ambos a la vez por los comandos coincidentes.

No sustituir el scoreboard ni conceder rangos mediante prefijos. La sincronización debe actualizar únicamente el grupo gestionado por Luna y preservar grupos administrativos ajenos. Debe ejecutarse al cargar rango, cambiarlo y reconectar; los accesos se cierran mientras no se haya cargado el rango.

## Homes

- `/sethome <nombre>`, `/home <nombre>`, `/homes`, `/delhome <nombre>`.
- Crear y utilizar homes únicamente con origen y destino en Hogar.
- Los límites son 0/3/4/5/7, no una suma de permisos heredados.
- Si baja el rango, se conservan las posiciones: solo queda accesible la cantidad permitida; sin rango, ninguna. El usuario debe poder gestionar/eliminar posiciones excedentes sin teletransportarse a ellas.
- Propuesta de teletransporte: preparación de 5 segundos, cancelación al moverse/recibir daño/cambiar de dimensión/entrar en combate, cooldown de 30 segundos aplicado al éxito.
- Comprobar suelo, espacio para el jugador, lava/fuego, borde del mundo y validez del destino al finalizar la preparación, no solo al solicitarla.
- Aplicar restricciones también a alias, menús, API y comandos con namespace. No basta con ocultar comandos o negar su ejecución desde una dimensión.

## Pwarps

Usar los hogares públicos de HuskHomes como base y ofrecer `/pwarp` como interfaz del servidor. No conceder `/setwarp`, que administra destinos globales.

Propuesta de límites publicados: Entrenador 0, Élite 1, Campeón 1, Maestro 2, Leyenda 3. Todos pueden consultar y visitar destinos públicos desde Hogar. **Cada pwarp consume un home del propietario**; no añade posiciones extra sobre el límite confirmado. Si se desean cuotas independientes, haría falta otra capa de persistencia y no bastaría con un alias de hogares públicos.

Acciones: listar/buscar, visitar, publicar un home existente, retirar publicación y editar descripción. Identificador por propietario y nombre para evitar colisiones. Solo se puede modificar un destino propio. La administración puede despublicar destinos inseguros sin borrar la casa privada.

Dentro del PokéPad: sección Hogar con pestañas «Mis homes» y «Lugares de jugadores». Mantener las paradas de la Ciudadela en su app actual.

## Experiencia de Leyenda

Conservar nivel, progreso y experiencia total de Minecraft al morir, sin activar `keepInventory` global. Impedir que la XP protegida genere orbes, para evitar duplicarla. Restaurar una sola vez durante el respawn real y contemplar muerte/desconexión/reinicio. No modificar experiencia de Pokémon ni proteger inventario como beneficio implícito.

## Verificación antes del despliegue

- Verificar los cinco rangos con cuentas sin OP: comandos heredados, inaccesibles y cada límite de homes.
- Validar promoción, degradación, expiración, reconexión y carga incompleta de permisos.
- Intentar crear y usar homes/pwarps desde Lobby, Ciudadela, Salvajes, gimnasios y Torre; todos deben rechazarse.
- Ensayar rutas alternativas y un cambio de dimensión durante el warmup.
- Probar contenedores portátiles ante cierre, desconexión, shift-click e inventario lleno.
- Leyenda: conservación exacta de XP sin orbes, sin duplicación tras reconectar y sin cambiar muerte de otros rangos.
- Confirmar que Viajes, rangos visuales, kits, armaduras, permisos administrativos y protección de zonas no cambian.
- Respaldar mods/configuración/base; compilar el puente; instalar las dependencias exactas en entorno de prueba; desplegar después con rollback.

## Fuentes consultadas

- https://william278.net/docs/huskhomes/commands
- https://william278.net/docs/huskhomes/managing-access
- https://github.com/WiIIiam278/HuskHomes
- https://modrinth.com/plugin/huskhomes/version/KWckelph
- https://modrinth.com/mod/essential-commands/versions
- https://luckperms.net/wiki/Installation
