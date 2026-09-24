# La Torre de Batalla — Sistema Integral

## Purpose

Cómo funciona la **Torre de Batalla** (`lunaeternal:torre_batalla`) en PokeReport: Luna Eternal:
desafío PvE competitivo por rondas consecutivas (Battle Tower), escalado forzado a Nivel 100,
modos Individual (1v1), Dobles (2v2) y Aleatorio (Draft), ranking sincronizado en 3 hologramas
independientes en la Ciudadela, y un sistema integral de **temporadas con recompensas únicas por ronda**
(R1-100 + Nivel Infinito 101+) gestionado desde el PokePad con interfaz ergonómica y libre de solapamientos.

## Dependencies

- [CLAUDE.md](../../CLAUDE.md) — D-007 (Economía), D-014 (Monedas), D-044 (Torre de Batalla y Recompensas), P6 (El servidor manda), `dibujado.md` (las 6 reglas de UI).
- [dibujado.md](../ui/dibujado.md) — Regla innegociable de 2 pasadas de dibujado para elementos 2D y 3D en Minecraft.
- [gimnasios.md](gimnasios.md) — Patrón de zonas instanciadas y arenas de combate.
- `mod/src/main/java/net/pokereport/luna/torrebatalla/` — Lógica de servidor:
  - `TorreBatallaService.java`: orquestación de combates Cobblemon, registro de victorias y hooks de XP.
  - `TorreRecompensas.java`: temporadas, catálogo R1-100, Master Balls infinitas 101+, bonos de divisas y persistencia JSON.
  - `TorreRanking.java`: rankings 1v1, 2v2, aleatorio y 3 hologramas de alto contraste con 96% de opacidad.
  - `TorreArenasManager.java`: instanciación y coordenadas de arena.
  - `TorrePools.java`: generación de equipos de NPCs Gen 1 y Gen 2 sin legendarios rotos.
- `mod/src/client/java/net/pokereport/luna/client/pokepad/` — Pantallas cliente:
  - `TorreScreen.java`: selección de modo, historial Mortal Kombat y botón `[ 🏆 RECOMPENSAS ]` con contador de premios pendientes.
  - `TorreRecompensasScreen.java`: pantalla de premios en chasis PokéPad con pestañas por rangos, tarjetas en 2 filas, slots rehundidos y botón «RECLAMAR TODO».
- Protocolo de Red (`Red.java`): `PedirRecompensasTorre`, `ReclamarRecompensaTorre`, `EstadoRecompensasTorre`.
- Persistencia: `config/luna_torre_recompensas.json` y tablas MariaDB de ranking.

## Current Status

**Desplegado y en vivo.** Arena construida con plataforma mapeada, hologramas con fondo azul pizarra sólido (`0xF40A0E18`), interfaz de recompensas sin pixelaciones ni empastado blanco, y entrega segura de divisas e ítems verificada.

---

## 1. Modos de Juego y Reglas Competitivas

| Modo | Reglas de Equipo | Nivel y Formato | Comportamiento |
|---|---|---|---|
| **1 vs 1 (Individual)** | 6 Pokémon propios en equipo | Escalado a Nivel 100 | Combate clásico 1 contra 1 por turnos. |
| **2 vs 2 (Dobles)** | 6 Pokémon propios en equipo | Escalado a Nivel 100 | Combate de dobles, 2 Pokémon activos simultáneos. |
| **Aleatorio (Draft)** | 6 Pokémon aleatorios asignados | Escalado a Nivel 100 | Pool balanceado de Gen 1 y Gen 2 sin legendarios rotos. |

### Reglas Innegociables del Combate:
1. **Escalado Nivel 100 Forzado**: Todos los Pokémon participantes (jugador y NPC) se escalan temporalmente a nivel 100 al iniciar el combate para garantizar paridad competitiva pura.
2. **Bloqueo de Escaneo de Pokédex**: En la dimensión `lunaeternal:torre_batalla` se cancela el registro automático en la Pokédex al interactuar con los Pokémon del NPC oponente. Esto evita exploits donde un jugador completa la Pokédex sin capturar o encontrar los Pokémon legalmente en el mundo.
3. **Restricción de 6 Pokémon**: Se exige equipo completo de 6 Pokémon para poder entrar a los modos Individual y Dobles.

---

## 2. Coordenadas de la Arena y Plataforma Mapeada

La arena de combate cuenta con una plataforma schem fija en la dimensión `lunaeternal:torre_batalla`. Las posiciones exactas configuradas son:

| Entidad / Rol | Coordenadas X, Y, Z | Orientación (Yaw, Pitch) |
|---|---|---|
| **Jugador** | `50.489, 117.0, 62.56` | Mirando al Norte (Yaw 180°, Pitch 0°) |
| **NPC Entrenador** | `50.48, 117.0, 38.51` | Mirando al Sur (Yaw 0°, Pitch 0°) |
| **Pokémon NPC (1v1)** | `50.482, 116.0, 46.57` | Mirando al Sur |
| **Pokémon NPC (2v2 - Slot 1)** | `53.493, 116.0, 46.52` | Mirando al Sur |
| **Pokémon NPC (2v2 - Slot 2)** | `47.518, 116.0, 46.502` | Mirando al Sur |
| **Pokémon Jugador (1v1)** | `50.528, 116.0, 54.547` | Mirando al Norte |
| **Pokémon Jugador (2v2 - Slot 1)** | `47.484, 116.0, 54.567` | Mirando al Norte |
| **Pokémon Jugador (2v2 - Slot 2)** | `53.546, 116.0, 54.514` | Mirando al Norte |

---

## 3. Hologramas de Clasificación (Ranking)

Se colocan 3 hologramas de ranking en la Ciudadela, completamente independientes entre sí y desacoplados del NPC recepcionista de la torre:
- **Holograma 1v1**: Récords del modo Individual.
- **Holograma 2v2**: Récords del modo Dobles.
- **Holograma Aleatorio**: Récords del modo Draft.

### Aspecto Visual y Legibilidad:
- **Fondo de Alto Contraste**: Color `0xF40A0E18` (~96% opacidad, azul pizarra noche casi sólido). Elimina la transparencia excesiva que impedía leer el texto contra fondos claros o fuentes de luz del servidor.
- **Tipografía**: Formato en negrita y colores de alto contraste:
  - Puesto #1: Oro brillante (`§e§l`)
  - Puesto #2: Aguamarina / Diamante (`§b§l`)
  - Puesto #3: Verde Esmeralda (`§a§l`)
  - Top 4-10: Blanco puro (`§f§l`)

### Comandos de Gestión:
- `/luna torre_batalla holograma spawn <1vs1|2vs2|aleatorio>`: Coloca el holograma en la ubicación actual del operador.
- `/luna torre_batalla holograma despawn <1vs1|2vs2|aleatorio>`: Retira el holograma del mundo.
- `/luna torre_batalla holograma tp <1vs1|2vs2|aleatorio>`: Teletransporta al operador al holograma.

---

## 4. Sistema de Temporadas y Recompensas

El sistema (`TorreRecompensas.java`) recompensa el avance en la torre de forma periódica por temporadas:

### 1. Dinámica de Temporadas
- Cada temporada tiene un número entero incremental (`temporada_actual`).
- **Regla de Reclamo Único**: Cada ronda superada otorga su recompensa **una sola vez por temporada**. Al avanzar a una nueva temporada, el historial de reclamos se reinicia, permitiendo a los jugadores volver a ganar las recompensas al escalar de nuevo.

### 2. Bonificaciones Periódicas Acumulativas
- **Cada 5 rondas**: **+1,000 Plata** (`CurrencyType.POKEDOLLAR`).
- **Cada 30 rondas**: **+50 LunaCoins** (`CurrencyType.REPORTCOIN`).

### 3. Nivel Infinito (Rondas 101+)
- Superada la ronda 100, la torre pasa al modo Infinito.
- **Por cada victoria consecutiva a partir de la ronda 101**: **1 Master Ball fija y garantizada**.

### 4. Catálogo R1 a 100
El catálogo combina progresión de captura, entrenamiento competitivo y cosméticos coleccionables exclusivos:
- **Balls**: Poké Ball, Great Ball, Ultra Ball, Master Ball.
- **Bayas Estratégicas**: Aranja, Meloc, Atania, Ziuela, Aslac, Caqu, Zidra, etc.
- **Materiales de Minería**: Lingotes de Hierro, Oro, Diamantes, Esmeraldas y Lingotes de Netherite.
- **Entrenamiento Competitivo**: Caramelos Raros, Caramelos EXP (S, M, L, XL), Mentas de Naturaleza, Cápsulas y Parches de Habilidad.
- **Objetos Competitivos Equipables**: Restos, Cinta Elegida, Gafas Elegidas, Pañuelo Elegido, Vidasfera, Casco Dentado, etc.
- **Chapas de Entrenamiento (IVs)**: Chapa Plateada y Chapa Dorada (generadas con NBT de pepita personalizada con nombre y lore en oro).
- **Peluches Cobblemon y Trofeo Exclusivo**:
  - **Ronda 50**: Peluche Snorlax (`cobblemon:snorlax_plush`)
  - **Ronda 70**: Peluche Gengar (`cobblemon:gengar_plush`)
  - **Ronda 90**: Peluche Marshadow (`cobblemon:marshadow_plush`)
  - **Ronda 100**: Peluche Kyogre (`cobblemon:kyogre_plush`)
  - **Ronda 100**: Trofeo Pokémon Shiny Legendario Exclusivo (`cobblemon:shiny_legendary_trophy` / trofeo conmemorativo).

### 5. Comandos de Administración
- `/luna torre_batalla nueva_temporada`: Avanza la temporada, limpia el registro de reclamos, guarda en JSON y notifica por broadcast a todo el servidor.
- `/luna torre_batalla dar_ronda <jugador> <modo> <ronda>`: Establece la ronda y récord del jugador para depuración y testing.

---

## 5. Interfaz de Usuario en el PokéPad

El acceso al sistema se realiza a través de la aplicación **Torre de Batalla** en el PokéPad:

### 1. Pantalla Principal (`TorreScreen.java`)
- Chasis PokéPad estándar.
- Selección de modo de combate (1v1, 2v2, Aleatorio) con arte oficial 512x512.
- Ladder vertical estilo Mortal Kombat que muestra los oponentes superados y por vencer.
- **Botón `[ 🏆 RECOMPENSAS ]`**: Ubicado en el panel lateral izquierdo (`y = 625`). Muestra un badge visual de premios pendientes listos para reclamar. Al pulsar, abre `TorreRecompensasScreen`.

### 2. Pantalla de Recompensas (`TorreRecompensasScreen.java`)
- **Panel Izquierdo**:
  - Récord actual de temporada del jugador.
  - Temporada en curso.
  - Botón verde `[ RECLAMAR TODO ]` (`0xFF22C55E`): Reclama todos los premios pendientes de una sola vez.
  - Cuadro explicativo de reglas y bonificaciones.
- **Pestañas por Rangos**:
  - `[ 1-20 ]`, `[ 21-40 ]`, `[ 41-60 ]`, `[ 61-80 ]`, `[ 81-100 ]`, `[ 101+ Infinito ]`.
- **Estructura Interna de Cada Tarjeta (2 Filas Estrictas)**:
  - **Fila Superior**: Título de la ronda en color según estado (oro/blanco) + Badges de divisa (+1,000 Plata / +50 LunaCoins) en fondo azul oscuro.
  - **Fila Inferior**: Casilleros rehundidos para cada ítem (`0xFF0D121B` con borde `0xFF28364D`), pastilla de cantidad nítida, y botón de estado ("RECLAMAR" verde / "RECLAMADO" gris / "BLOQUEADO" con candado).
- **Pestaña 101+ (Infinito)**:
  - Tarjeta Hero de encabezado en azul noche (`0xFF141C2B`) con borde zafiro, explicando el premio de 1 Master Ball por victoria.
  - Cuadrícula 3×2 con 6 combates infinitos para no dejar huecos vacíos.

### 3. Soluciones Visuales y Lecciones de Dibujado
1. **Regla de 2 Pasadas (`docs/ui/dibujado.md`)**: Primero se dibuja toda la geometría 2D (`ctx.fill`, `marco`), se vacía el buffer con `ctx.draw()`, luego se dibujan los modelos 3D con `ctx.drawItem()`, y al final los tooltips nativos con `ctx.drawItemTooltip()`.
2. **Corrección de `marco()`**: La función `marco(x, y, w, h, grosor, color)` contenía un error en el borde izquierdo (`x + w` en vez de `x + g`), lo que provocaba que rellenara todo el rectángulo de color amarillo sólido. Corregido para que solo dibuje el marco perimetral sin rellenar el interior.
3. **Eliminación del Contorno Blanco Borroso**: Dibujar 4 textos desplazados en blanco creaba un halo empastado que volvía ilegibles los números `x2`, `x3`. Se reemplazó por la sombra nativa de Minecraft (`shadow = true`), garantizando textos limpios y de lectura perfecta.
4. **Separación en 2 Filas**: Disponer títulos, badges, slots y botones en una sola fila causaba solapamientos cuando los nombres eran largos. La separación en 2 filas fijas garantiza más de 100 píxeles de margen horizontal libre en cada componente.

---

## 6. Persistencia y Protocolo de Red

### 1. Fichero de Configuración y Datos
- Ubicación: `config/luna_torre_recompensas.json`.
- Estructura:
```json
{
  "temporada_actual": 1,
  "reclamadas": {
    "uuid-del-jugador": [4, 5, 6, 10]
  }
}
```

### 2. Protocolo de Red (`Red.java`)
- `PedirRecompensasTorre` (C2S): El cliente solicita su estado de recompensas al abrir la pantalla.
- `ReclamarRecompensaTorre(int ronda, boolean todas)` (C2S): Solicita reclamar una ronda específica o todas las acumuladas.
- `EstadoRecompensasTorre(int temporada, int rondaMax, List<Integer> reclamadas, int pendientes)` (S2C): El servidor responde con la temporada actual, récord alcanzado, lista de rondas ya cobradas y total de premios pendientes.
# Auditoría de ciclo de combate — 2026-09-20

Las modalidades usan los identificadores `0=individual`, `1=dobles` y
`2=aleatorio`. Todas crean enfrentamientos 6v6 a nivel 100; dobles selecciona
`GEN_9_DOUBLES`, mientras individual y aleatorio usan `GEN_9_SINGLES`.

Se corrigieron los siguientes fallos de estado:

- aleatorio prestaba seis Pokémon antes de comprobar que la arena estuviera
  libre y podía dejarlos permanentemente al rechazar la entrada;
- una dimensión ausente dejaba una partida registrada que bloqueaba la arena;
- callbacks retrasados de una ronda podían iniciar batalla sobre otro intento;
- victoria/huida eliminaban la asociación RCT y el NPC dentro del callback de
  Cobblemon, antes de terminar su cola visual, causando bloqueos especialmente
  visibles en cambios de Pokémon de dobles;
- los eventos no se vinculaban al UUID de la batalla y un evento tardío podía
  cerrar una ronda nueva;
- no existía recuperación ante una batalla desaparecida o sin avanzar;
- al salir de aleatorio se borraba todo el equipo, no solo los préstamos.

Cada jugador queda ahora vinculado a un `battleId`; la limpieza se difiere
fuera del callback, los préstamos llevan marca propia y un watchdog revisa la
batalla cada cinco segundos. Si el turno no progresa durante tres minutos, el
intento se cierra de forma segura, se informa al jugador y se libera la arena.

### Desconexión durante el modo aleatorio

Se confirmó una carrera de estado entre el evento de desconexión y la
restauración de equipo de RCT: los seis Pokémon prestados podían reaparecer en
la party del jugador al reconectarse y quedar como propios. La limpieza ahora:

- retira únicamente Pokémon con la marca persistente de préstamo de la Torre;
- revisa tanto la party como la PC;
- se ejecuta al salir/desconectarse y nuevamente al reconectar, de inmediato y
  tras 20 ticks para cubrir la carga diferida del almacenamiento;
- vuelve a ejecutarse antes de validar un nuevo intento.

Los Pokémon legítimos de 1v1, 2v2 o de cualquier otro sistema no llevan esa
marca y no son eliminados.

### Sustituciones tras KO simultáneo en 2vs2

Se confirmó un soft-lock específico de dobles cuando, en el mismo turno, cae
un Pokémon del jugador y otro del NPC. Showdown solicita sustituciones
forzadas para ambos lados, pero `StrongBattleAI` resuelve cada hueco de forma
aislada y puede dejar incompleta la respuesta coordinada del NPC; mientras
falta esa respuesta, el cliente del jugador no recibe/libera correctamente su
selección y parece que el turno quedó congelado.

La Torre usa ahora `TorreDoublesAI` únicamente en modo 2vs2:

- conserva `StrongBattleAI` durante turnos normales;
- deriva cambios forzados a `RCTBattleAI`, que evalúa la petición completa de
  Showdown (`forceSwitch`, `wait`, `must` y los huecos simultáneos);
- si la ruta de RCT falla, `RandomBattleAI` elige una sustitución válida como
  mecanismo de emergencia;
- registra cada sustitución del NPC y cualquier fallback para facilitar una
  prueba real sin llenar el log durante ataques normales.

Individual y Aleatorio permanecen en formato singles y no cambian de IA.
