# Auditoría de Cobblemon oficial

## Purpose

Establecer **la frontera**: qué resuelve Cobblemon nativo, qué se resuelve por
configuración/datapack/resourcepack, qué por mod existente, y qué exige
desarrollo propio. Esta frontera decide el alcance de todo el proyecto.

## Dependencies

- [`../../CLAUDE.md`](../../CLAUDE.md) — principio P5 (mínimo de dependencias)

## Related Documents

- [`current-server-audit.md`](current-server-audit.md)
- [`diosesmon-analysis.md`](diosesmon-analysis.md)

## Current Status

Investigación documental completada 2026-08-11 (wiki oficial, changelog 1.7.0,
repositorio GitLab). **Pendiente de verificación empírica en servidor** — la
documentación de Cobblemon es incompleta en la parte de API Java.

## Last Decision

Ninguna todavía. Este documento alimenta `ARCH-001` (B-002).

---

## 1. Identidad del proyecto

| | |
|---|---|
| Repositorio | `gitlab.com/cable-mc/cobblemon` — 15 792 commits, activo desde nov-2021 |
| Licencia | **MPL 2.0** — permite addons propietarios; los cambios *al propio Cobblemon* deben liberarse |
| Lenguaje | Kotlin |
| Loaders | Fabric y NeoForge |
| Versión | **1.7.3** (31-ene-2026) sobre Minecraft 1.21.1 |
| Contenido | 1 025 Pokémon |

La licencia importa: **nuestro addon puede ser cerrado**, siempre que no
modifiquemos archivos fuente de Cobblemon. Diseñar por extensión, no por fork.

---

## 2. Qué da Cobblemon NATIVO

Verificado contra el changelog 1.7.0 y la wiki:

✅ **Sí lo trae**
- Captura, combate, cría, evolución, movimientos, habilidades, naturalezas, IV/EV
- 1 025 especies con modelos y animaciones
- **Montura de Pokémon** (añadido en 1.7 — shift+clic derecho)
- **Objetos cosméticos** equipables en el Pokémon, junto al objeto llevado
- **NPCs con editor de comportamiento** y una carpeta de datapack `behaviours`
- Sistema de spawn por biomas, contextos y presets
- Estructuras propias, PC/almacenamiento, Pokédex

❌ **NO lo trae** — y son justo los pilares de un MMORPG
- **Gimnasios y medallas**
- **Quests**
- **Economía / moneda**
- **GTS o mercado global**
- **Progresión de jugador** transversal
- **Clanes, reputación, rangos**

> Aviso: mucha documentación de terceros atribuye a "Cobblemon 1.7" las
> medallas, el starter kit y los gimnasios. **Eso es CobbleVerse, no
> Cobblemon.** Confundirlos lleva a sobreestimar la base.

**Consecuencia directa:** el MMORPG *no está en Cobblemon*. Cobblemon aporta el
simulador Pokémon —que es enorme y no hay que reinventar— pero **todo lo que
convierte eso en un MMORPG es nuestro**. Eso es una buena noticia: es
exactamente donde vive la identidad propia que exige el brief.

---

## 3. La frontera de implementación

Aplicando P5 (nativo → config → datapack → resourcepack → mod maduro → propio):

| Necesidad | Resuelto por | Coste |
|---|---|---|
| Especies, stats, tipos, movimientos, evoluciones, drops | **Datapack** `data/cobblemon/species/` | Bajo |
| Rarezas y dónde aparece cada Pokémon | **Datapack** `spawn_pool_world/` + `spawn_detail_presets/` | Bajo |
| Pokémon exclusivos del servidor | **Datapack + resourcepack**, sin Java | Medio (arte) |
| Aspecto visual, modelos, animaciones | **Resourcepack** `assets/cobblemon/bedrock/` | Medio (arte) |
| Comportamiento de NPCs | **Datapack** `behaviours/` | Bajo |
| Permisos y rangos | **LuckPerms** (ya instalado) | Bajo |
| Auditoría anti-abuso | **Ledger** (ya instalado) | Bajo |
| Moneda base | **CobbleDollars** (ya instalado) — a auditar | Bajo |
| Combates de entrenador NPC | **CobblemonTrainers / CobblemonNPCs** | Bajo |
| **GTS con filtros** | ⚠️ **Propio** | **Alto** |
| **Progresión de jugador** | ⚠️ **Propio** | **Alto** |
| **UI de navegación** | ⚠️ **Propio** | **Alto** |
| **Colección / Pokédex con recompensa** | ⚠️ **Propio** | Medio |
| **Sinks y control de inflación** | ⚠️ **Propio (diseño)** | Alto |
| **Gimnasios y medallas** | ⚠️ **Propio o CobbleVerse** | Medio |

### Estructura de un addon (sin escribir Java)

```
addon/
├── pack.mcmeta
├── data/cobblemon/
│   ├── species/custom/(pokemon).json      stats, tipos, movimientos, drops
│   └── spawn_pool_world/(pokemon).json    condiciones de aparición
└── assets/cobblemon/
    ├── bedrock/pokemon/
    │   ├── models/…/(pokemon).geo.json    Blockbench
    │   ├── animations/…/(pokemon).animation.json
    │   ├── posers/(pokemon).json          escala y encuadre en GUI
    │   └── resolvers/…/0_(pokemon)_base.json   une modelo+textura+anim+poser
    ├── textures/pokemon/…/(pokemon).png
    └── lang/en_us.json
```

**Cero Java.** Ya lo hemos hecho: `addon-luna/` en el proyecto anterior es
exactamente esta estructura. La capacidad está probada.

---

## 4. Límites duros verificados

Restricciones que **obligan** a escribir un mod propio. Cada una elimina una
clase entera de diseños:

| Límite | Origen | Impacto en el diseño |
|---|---|---|
| **Un datapack no puede leer el equipo Pokémon de un jugador** | Verificado en el servidor anterior | Cualquier gating por equipo (acceso a zona, requisito de quest, entrada a torneo) exige mod |
| Los diálogos de Cobblemon corren con **permisos del jugador** | Verificado | Un NPC no puede otorgar dinero ni desbloquear nada sin rodeo o mod |
| Easy NPC bloquea `function` y `execute` | `unsafeNpcCommands` | Rodeo conocido: el NPC escribe un `scoreboard` y un reloj de datapack lo ejecuta |
| No hay API documentada de GTS ni de economía | Wiki oficial | El mercado es 100 % nuestro |

> **El primero es el más caro.** Un MMORPG Pokémon quiere decir *"no entras a
> la Zona 4 sin tres Pokémon de nivel 40"*. Con datapacks puros es imposible.
> **Confirma que necesitamos un mod propio de servidor** — no es opcional ni
> evitable, y conviene asumirlo desde el diseño en vez de descubrirlo en PHASE 4.

---

## 4-bis. Palancas nativas de aparición — VERIFICADO EN EL JAR

Extraído de `Cobblemon-fabric-1.7.3+1.21.1.jar` (52 100 entradas, 824 ficheros
de spawn) el 2026-08-11. **Esto es dato medido, no documentación.**

### Condiciones disponibles (frecuencia de uso real)

```
biomes 3670 · minSkyLight/maxSkyLight 1866 · canSeeSky 839 · timeRange 636
weightMultiplier 606 · minLureLevel 585 · structures 507 · minY 309 · maxY 247
neededNearbyBlocks 227 · isRaining 171 · isThundering 60 · percentage 55
moonPhase 42 · isSlimeChunk 41 · minX/maxX 8 · neededBaseBlocks 7 · rodType 6
neededInstalledMods / neededUninstalledMods 824
```

Todas admiten también `anticondition` (1 020 usos), que invierte la regla.

### ✅ `moonPhase` CONFIRMADO — `PKM-001` resuelto

Existe y Cobblemon lo usa en 9 especies. Formato real:

```json
{"moonPhase": 0}                            fase única
{"moonPhase": "1,2,3"}                      lista de fases
{"minSkyLight": 8, "maxSkyLight": 15,
 "biomes": ["#cobblemon:is_hills"],
 "timeRange": "night", "moonPhase": "0"}    combinado
```

Lo usan Clefairy, Clefable, Lunatone, Zorua, Zoroark, Frillish, Jellicent,
Basculin y Basculegion. **Se combina libremente con bioma, hora, luz y
estructura, y funciona en `anticondition`.**

> ⚠️ **Pero el ciclo dura 2 h 40 min, no una semana.**
> Un día de Minecraft son 24 000 ticks = 20 min reales. Hay 8 fases, una por
> día → **ciclo lunar completo = 160 minutos.**
> Consecuencia de diseño en [vision.md §3.1](../game-design/vision.md).

### Otras palancas de alto valor para el diseño

| Palanca | Qué permite |
|---|---|
| **`minLureLevel` / `maxLureLevel` (1-3)** | **Sistema de señuelos nativo.** 585 usos. Un consumible que sube el nivel de señuelo altera qué aparece → **sink económico y mecánica de expedición, ya construidos** |
| `structures` + 28 presets | Aparición ligada a estructuras: `ancient_city`, `stronghold`, `mansion`, `ocean_monument`, `trail_ruins`, `end_city`… Exploración con recompensa concreta |
| `neededNearbyBlocks` / `neededBaseBlocks` | Aparición por bloques cercanos → **los jugadores pueden construir para atraer** |
| `neededInstalledMods` | Spawns condicionados a mods presentes: permite un datapack único válido en varias configuraciones |
| `isRaining` / `isThundering` | Clima como palanca |
| `minY` / `maxY` | Profundidad — verticalidad real |
| `percentage`, `weight`, `weightMultiplier` | Control fino de rareza |

### Buckets de rareza nativos

```
common      1657 entradas  58,1 %
uncommon     679           23,8 %
ultra-rare   338           11,9 %
rare         178            6,2 %
```

Cuatro niveles ya definidos. **No hay que inventar un sistema de rareza**: hay
que decidir la *distribución*, que es diseño, no código.

### Tipos de posición

`grounded` 1863 · `fishing` 578 · `submerged` 288 · `surface` 74 · `seafloor` 49

La pesca es un subsistema completo con `rodType` propio.

### Lo que esto significa

**El motor de "un mundo con horarios" ya existe dentro de Cobblemon.** Bioma,
hora, fase lunar, clima, altura, estructura, bloques cercanos y señuelos son
condiciones nativas combinables **por datapack, sin una línea de Java**.

El pilar de identidad de la visión es, en su mayor parte, **trabajo de diseño y
de datos — no de programación**. Eso rebaja muchísimo el riesgo técnico del
proyecto y desplaza el esfuerzo a donde debe estar: decidir *qué* aparece,
*dónde* y *cuándo*.

---

## 5. Conclusión para `ARCH-001`

La arquitectura se perfila en tres capas:

```
┌─ Contenido Pokémon ──────── datapack + resourcepack (sin Java)
│  especies, spawns, rarezas, drops, arte propio
│
├─ Servicios de servidor ──── mods maduros ya instalados
│  LuckPerms · Ledger · CobbleDollars · NPCs/Trainers
│
└─ Sistemas de identidad ──── MOD PROPIO (obligatorio)
   progresión · GTS · colección · UI · gating por equipo · anti-abuso
```

**La tercera capa no es evitable** y es donde vive todo lo que el brief pide
como identidad. Un solo mod de servidor bien diseñado sustituye a decenas de
mods sueltos y elimina la dependencia de terceros en lo crítico — que es
exactamente el principio P5.

Esto también reencuadra `B-002`: la pregunta no es *"¿CobbleVerse sí o no?"*
sino *"¿queremos que el diseño de progresión sea nuestro?"*. Si la respuesta es
sí —y el brief es inequívoco—, CobbleVerse compite directamente con la capa 3.

---

## Next Actions

1. Verificar empíricamente la API Java de Cobblemon (eventos disponibles para
   un mod de servidor) — la wiki no la documenta; hay que leer el repositorio.
2. Evaluar `CobblemonTrainers` vs `CobblemonNPCs` para combates de NPC.
3. Alimentar `ARCH-001` con esta frontera.

## Related Systems

- [Auditoría del servidor actual](current-server-audit.md)
- [Análisis de Diosesmon](diosesmon-analysis.md)
