# Auditoría del servidor y proyecto actual

## Purpose

Inventario de lo que existe hoy en `D:\PokeReport 2` y en el servidor de
producción `2a0a48ff`, y su clasificación respecto al objetivo MMORPG:
**qué se cosecha, qué se rehace, qué se descarta.**

## Dependencies

- [../technical/infrastructure.md](../technical/infrastructure.md)

## Related Documents

- `cobblemon-audit.md` — PENDIENTE
- `diosesmon-analysis.md` — PENDIENTE
- `feature-gap-analysis.md` — PENDIENTE

## Current Status

Auditoría **de inventario** completada 2026-08-11. No se ha modificado ningún
archivo. Falta la auditoría de configuración fina (economía, permisos,
loot tables) — requiere decidir antes B-002.

## Last Decision

D-002 — El evento Luna Eternal se conserva intacto como producto propio.

---

## 1. El hallazgo principal

**`D:\PokeReport 2` no es el prototipo de un MMORPG. Es un evento narrativo en
directo.**

```
El Rastro de Luna — hasta 12 jugadores, ~3 horas
Acto I    La llamada       el Profesor Oak pide ayuda
Acto II   Las señales      tres guardianes, tres incursiones de grupo
Acto III  El cifrado       cinco pruebas
Acto IV   El laboratorio   la Doctora Vex · Hydreigon 70 · tier 7
Acto V    El reencuentro   Luna
```

Esto importa porque **un evento y un MMORPG tienen economías opuestas**:

| | Evento | MMORPG |
|---|---|---|
| Duración | 3 horas | meses |
| Progresión | irrelevante, se regala el equipo | es el producto entero |
| Economía | inexistente | núcleo del diseño |
| Recompensas | generosas, es el clímax | escasas, sostienen el valor |
| Balance | contra 4 jefes conocidos | contra 1 000 especies y jugadores creativos |
| Éxito | que termine bien | que la gente vuelva mañana |

Por eso **el "problema del servidor actual" descrito en el brief —demasiados
privilegios, demasiado rápido— no es un defecto de ejecución: es coherente con
lo que se construyó.** Un evento *debe* dar el equipo hecho. El error sería
juzgarlo como MMORPG fallido, o peor, intentar convertirlo en uno parcheándolo.

**Se construye de nuevo. Pero no desde cero: desde el material cosechado.**

---

## 2. Qué se cosecha (activos de alto valor, ya pagados)

Estos activos costaron trabajo real, no dependen de la arquitectura del evento
y sirven igual en un MMORPG:

| Activo | Qué es | Valor para el MMORPG |
|---|---|---|
| **57 líneas de voz** | Oak + 4 antagonistas, en OGG, con modelo de clonado de 3,1 GB detrás | Irreemplazables sin el modelo. Sirven para NPCs, tutorial, narrativa de zona |
| **`addon-luna/`** | Addon propio de Cobblemon: Luna con modelo, texturas, datos, Blockbench | **Prueba de que sabemos crear especies propias.** Identidad exclusiva |
| **Launcher (Electron)** | Windows + macOS, descarga Java, modpack y assets, CI en GitHub Actions | Control total de distribución. Elimina "no me arranca". Muy difícil de improvisar |
| **`scripts/ptero.py`** | Cliente completo de la API de Pterodactyl | Automatización de despliegue lista |
| **Generadores en Python** | `build_dp.py`, `build_raid.py`, `build_quests.py`, `build_rp.py` | **La metodología, más que el código:** nada escrito a mano, todo regenerable |
| **Skins y assets** | 86 skins de entrenador (KantoNPCs) + skins propias | Base visual de NPCs |
| **Conocimiento operativo** | 26 documentos con el *porqué* de cada decisión | Evita repetir errores ya pagados |

### El activo más valioso no es un archivo

La disciplina de **"nada se escribe a mano, todo se regenera desde un script"**
es exactamente lo que un MMORPG necesita para no volverse inmantenible: 1 000
especies, cientos de quests y miles de precios no se editan a mano. Esa práctica
se mantiene como norma del proyecto nuevo.

### Conocimiento técnico ya verificado (no repetir la investigación)

De `docs/estado-actual.md`, comprobado en servidor real:

- Easy NPC **bloquea `function` y `execute`** en comandos de NPC
  (`unsafeNpcCommands`); `scoreboard`, `give`, `playsound` y `tellraw` sí pasan.
  El rodeo: el NPC escribe un scoreboard y un reloj del datapack lo ejecuta.
- Los diálogos de Cobblemon corren con **permisos del jugador**; Blabber permite
  que sea **el servidor** quien abra el diálogo. Diferencia decisiva para quests.
- `playsound` sin `at @s` suena en el origen del mundo (alcance 16 bloques).
- `execute if entity @s[distance=..] positioned X Y Z` mide mal: el `positioned`
  va **antes** de la comprobación.
- **Litematica es cliente**: instalarla en el servidor no hace nada. Para pegar
  construcciones hace falta **WorldEdit** en servidor (ya instalado).
- Un datapack **no puede leer el equipo Pokémon de un jugador**. Cualquier regla
  que dependa de ello exige un mod propio. *(Muy relevante para gating de zonas
  y requisitos de acceso en el MMORPG.)*

---

## 3. Qué se descarta

| Elemento | Motivo |
|---|---|
| **Motor del evento** (128 funciones de datapack) | Máquina de estados de un guion lineal de 3 h. Un MMORPG no tiene actos |
| **Filtro "PROTOCOLO LUNA"** | Peaje de un solo uso |
| **Jefes de incursión del evento** | Calibrados contra 12 personas coordinadas y un equipo prescrito |
| **Balance de recompensas del evento** | Deliberadamente generoso. Tóxico en economía persistente |

> La **calibración** de incursiones sí se conserva como dato:
> *"% de daño en solitario × 5,2 = margen del grupo de 12; por debajo del 20 %
> en solitario, doce personas pierden."* Medido, no estimado. Sirve para
> dimensionar contenido de grupo en el MMORPG.

---

## 4. El modpack de producción — 134 mods, 419 MB

Minecraft 1.21.1 · Fabric · base **CobbleVerse 1.7.42**.

### Distribución

| Categoría | Nº | Observación |
|---|---|---|
| Cobblemon / Pokémon | 16 | Núcleo del gameplay |
| Librerías | 12 | Coste obligado |
| Optimización | 10 | Bien cubierto |
| Mundo / generación | 6 | |
| NPC / narrativa | 5 | Del evento |
| Economía / quests | 4 | **Insuficiente** para un MMORPG |
| Decoración | 4 | |
| UI cliente | 3 | |
| Resto | 74 | Requiere clasificación individual |

### Cimientos aprovechables ya instalados

Cuatro mods valen más de lo que parece para un MMORPG:

| Mod | Por qué importa |
|---|---|
| **LuckPerms** | Permisos jerárquicos con herencia y contextos. Es la base sobre la que se construyen rangos y desbloqueos progresivos. **No hay que sustituirlo** |
| **Ledger** | Registro auditable de acciones. Es la infraestructura anti-abuso: sin log no hay investigación de exploits ni rollback selectivo |
| **CobbleDollars** | Moneda ya presente. Punto de partida económico — a auditar sus sources |
| **Open Parties and Claims** | Protección de terreno y grupos |
| **Waystones** | Sistema de viaje — candidato natural a ser *gated* por progresión |

### Lo que NO existe y un MMORPG necesita

- **GTS / mercado global** — ningún mod lo cubre hoy
- **Casa de subastas o tiendas de jugador**
- **Persistencia en base de datos** — todo son archivos planos
- **Sistema de progresión de jugador** transversal (niveles, reputación, rangos de juego)
- **Colección / Pokédex con recompensas**
- **Clanes** (hay FTB Teams, que es otra cosa)
- **UI propia de navegación** — el árbol del brief no existe
- **Anti-abuso económico** — sin diseño de sinks, no hay control de inflación

### Riesgo de dependencias

**74 de 134 mods sin clasificar.** Cada mod es superficie de ataque económico
(duplicación, generación infinita), coste de RAM, y un bloqueo potencial cuando
salga Minecraft 1.22. La regla P5 (§4 de CLAUDE.md) exige justificar cada uno.
Esta poda es una tarea de PHASE 1, no de ahora.

---

## 5. CobbleVerse: la pregunta abierta

No se decide aquí. Se deja planteado el eje real de la decisión:

CobbleVerse trae un paquete coherente y probado (badges, loot, progresión,
estructuras) a cambio de que **su diseño de progresión sea el nuestro**. El
brief exige lo contrario: identidad propia y control absoluto de la curva.

| | CobbleVerse | Cobblemon + addons mínimos |
|---|---|---|
| Time-to-playable | rápido | lento |
| Control de progresión | **de CobbleVerse** | **nuestro** |
| Identidad propia | difícil | posible |
| Coste de mantenimiento | depende de terceros | nuestro |
| Riesgo en actualizaciones | alto (cadena larga) | acotado |

Se resuelve en `B-002`, tras la auditoría de Cobblemon oficial, con la matriz
completa que pide el brief.

---

## 6. Lo que esta auditoría todavía NO cubre

Honestidad sobre el alcance — falta:

- Configuración de **CobbleDollars** (sources reales de dinero)
- Grupos y permisos de **LuckPerms** (privilegios actuales)
- **Loot tables** de CobbleVerse y de `pastureLoot` / `lootrmon`
- **Spawn rates** y rarezas de Cobblemon
- Los **74 mods sin clasificar**
- Kits, warps, homes y comandos con ventaja
- Datos económicos reales de los jugadores (balances actuales)

Requiere leer configuración del servidor de producción vía API. Es viable y no
destructivo, pero conviene hacerlo **después** de B-002: si se abandona
CobbleVerse, auditar su balance fino es trabajo perdido.

---

## Next Actions

1. Auditar el repositorio oficial de Cobblemon → `cobblemon-audit.md`
2. Auditar Diosesmon PRO → `diosesmon-analysis.md`
3. Resolver `B-002` con la matriz de arquitectura
4. Solo entonces: auditoría fina de configuración económica

## Related Systems

- [Infraestructura](../technical/infrastructure.md)
- Economía — pendiente
- Progresión — pendiente
