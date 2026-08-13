# Estructura del mundo

## Purpose

Definir en qué espacios vive el juego —lobby, ciudadela, mundo— y el recorrido
del jugador desde que entra por primera vez.

## Dependencies

- [`../game-design/core-loop.md`](../game-design/core-loop.md)
- [`../analysis/diosesmon-analysis.md`](../analysis/diosesmon-analysis.md) §0

## Related Documents

- [`../ui/interfaz-cliente.md`](../ui/interfaz-cliente.md)
- [`../progression/progression-model.md`](../progression/progression-model.md)

## Current Status

**PROPUESTA.** Nada de esto está construido todavía. Se documenta ahora para
que la construcción y el código se hagan contra un plano, no improvisando.

## Last Decision

Pendiente.

---

## 1. Tres espacios, tres funciones

```
┌────────────┐   ┌──────────────────┐   ┌─────────────────┐
│   LOBBY    │──▶│    CIUDADELA     │──▶│     MUNDO       │
│ dimensión  │   │    dimensión     │   │   overworld     │
├────────────┤   ├──────────────────┤   ├─────────────────┤
│ recibir    │   │ servicios        │   │ el juego real   │
│ presentar  │   │ comunidad        │   │ exploración     │
│ dar acceso │   │ progresión       │   │ captura         │
└────────────┘   └──────────────────┘   └─────────────────┘
   segundos          minutos                  horas
```

Cada espacio tiene **una** función. Si un espacio necesita dos explicaciones,
está mal diseñado.

---

## 2. LOBBY — la primera impresión

Dimensión pequeña y cerrada. El jugador aparece aquí siempre que entra al
servidor por primera vez.

**Qué hace:** decir dónde está, qué es esto, y cómo entrar.

| Elemento | Función |
|---|---|
| Estructura central | Identidad visual. Lo primero que se ve |
| Panel flotante | Nombre del mundo, descripción de una línea, conectados |
| **NPC de acceso** | Clic derecho → entrar. Un solo gesto, sin comandos |
| Barra lateral | Marca, tu nombre, conectados, ping |

**Decisión:** el lobby **no tiene servicios**. Ni tienda, ni GTS, ni banco.
Es un vestíbulo. Meter funcionalidad aquí crea un segundo centro de gravedad
que compite con la ciudadela y parte la comunidad en dos sitios.

> Diosesmon usa el lobby para elegir entre varios servidores de una red.
> **Nosotros no tenemos red**, así que el lobby es sobre todo presentación y
> un umbral. Cuando haya más de un mundo, ya está el sitio preparado.

---

## 3. CIUDADELA — el centro de todo

Dimensión propia con una ciudad completa. Es el **hogar** del jugador y el
punto de encuentro de la comunidad.

Que sea dimensión aparte tiene tres ventajas concretas:

1. **Se construye sin límites** — no compite con la generación del mundo
2. **No se puede griefear** ni reclamar terreno
3. **Se puede rehacer** sin tocar el mundo de los jugadores

### Qué contiene

| Zona | Contenido | Sistema |
|---|---|---|
| **Laboratorio** | NPC que entrega el inicial | Onboarding |
| **Sala de Gimnasios** | Todos los gimnasios en un mismo edificio | Vía Entrenador |
| **Mercado** | Acceso al GTS, tiendas NPC | Economía |
| **Centro Pokémon** | Curar, PC | Servicios |
| **Gremio** | Clanes, trabajos | Social |
| **Sastrería** | Cosméticos, apariencia | Identidad |
| **Puerta al mundo** | Salida a la exploración | Core loop |
| **Puntos de viaje** | Teletransporte interno entre zonas | Comodidad |

### La sala de gimnasios: una decisión de diseño

Diosesmon los pone todos juntos. **Es cómodo y es un error para nosotros.**

Un gimnasio en una sala es una puerta con un número. Un gimnasio **en su sitio
del mundo** es un viaje, un descubrimiento y una historia. Nuestro pilar es que
el mundo se conoce explorándolo (`vision.md`), y concentrar todo el contenido
en un pasillo lo contradice frontalmente.

**Propuesta intermedia:**

> En la ciudadela hay un **Salón de Medallas**: muestra las medallas, dice
> dónde está el siguiente gimnasio y qué hace falta. **Informa, no sustituye
> al viaje.** Los gimnasios se ganan en el mundo.

Coste: el jugador tarda más. Beneficio: cada medalla es un recuerdo, no una
casilla.

### Viaje interno (poketaxi)

Teletransporte **dentro** de la ciudadela, entre zonas. No sale al mundo.

Es comodidad pura, no rompe nada: la ciudadela no tiene contenido que
descubrir, tiene servicios que usar. **Aquí sí conviene ser generoso.** El
viaje que hay que racionar es el que salta exploración del mundo real
(`vision.md §5`).

---

## 4. MUNDO — donde se juega

El overworld. Aquí vive el core loop entero: explorar, encontrar, decidir,
capturar, volver.

- Los **gimnasios** están repartidos por él, no en la ciudadela
- Las **zonas** se abren por progresión (`progression-model.md`)
- El **ciclo lunar y el clima** cambian qué aparece (`cobblemon-audit.md` §4-bis)
- La **vuelta a la ciudadela** cierra el bucle 2 de la expedición

---

## 5. El recorrido de entrada

El orden importa más que cualquier elemento suelto. Diosesmon lo tiene bien
resuelto y adoptamos su esqueleto:

```
1. LOBBY
   aparece · ve el mundo · entiende dónde está
   ↓  clic derecho en el NPC

2. LLEGADA A LA CIUDADELA
   se abre un libro: bienvenida y una sola acción posible
   ↓

3. CINEMÁTICA
   IN-GAME, con voz. No un vídeo de YouTube — ver abajo
   ↓

4. LABORATORIO
   NPC entrega el inicial. Primera decisión real del jugador
   ↓

5. PRIMEROS PASOS
   el Almanaque se abre solo por primera vez
   una misión corta que enseña el bucle: salir, capturar, volver
   ↓

6. LIBERTAD
   la ciudadela y el mundo abiertos, con las zonas avanzadas cerradas
   y explicadas
```

### Por qué NO un vídeo de YouTube

Diosesmon abre el navegador en el primer minuto de juego. Es barato y es su
mayor debilidad de onboarding: **el jugador sale del juego justo cuando más
inmerso debería estar**, y algunos no vuelven.

Nosotros tenemos lo que hace falta para hacerlo dentro:

| Activo | Estado |
|---|---|
| **57 líneas de voz** grabadas + modelo de clonado | Ya existen (proyecto anterior) |
| **Cutscene API** — cámara con splines | Ya usada y probada |
| Easy NPC con diálogo y skins | Ya usado y probado |

**Es una ventaja competitiva real y ya pagada.** Renunciar a ella por un enlace
de YouTube sería tirar el activo más difícil de replicar que tenemos.

> Si la cinemática se retrasa, el arranque provisional es un libro + diálogo del
> NPC. **Nunca un enlace externo.**

---

## 6. El inicial

Diosesmon pregunta *"¿Kanto o Johto?"* con dos botones. Funciona, pero es una
decisión sin consecuencias: eliges una lista y ya.

**Propuesta:** que la elección del inicial sea la **primera decisión con
sentido** del juego, ligada a las Vías (`progression-model.md`):

- No se elige una región: se elige un **enfoque de arranque**
- Cada inicial abre una primera misión distinta y un rincón distinto del mundo
- La elección se puede recuperar más adelante — no castiga la ignorancia del
  primer día, pero sí define el comienzo

Detalle en `PKM-002` (pendiente).

---

## 7. Implementación

| Pieza | Cómo | Coste |
|---|---|---|
| Dimensiones lobby y ciudadela | **Datapack** (`dimension` + `dimension_type`) | Bajo |
| Construcción | A mano + **WorldEdit** (ya instalado en producción) | Alto en tiempo |
| NPC de acceso y laboratorio | Mod de NPCs con diálogo | Bajo |
| Libro de bienvenida | Mod propio, al primer acceso | Bajo |
| Cinemática | Cutscene API + voz | Medio |
| Barra lateral | **Mod propio** (scoreboard) | Bajo |
| Puntos de viaje internos | Mod propio | Bajo |

**Las dimensiones son datapack: se pueden crear hoy, vacías, y construirlas
poco a poco.** No hay que esperar a tener la ciudad terminada para que el
recorrido funcione.

---

## 8. Orden de construcción propuesto

| Hito | Qué se construye | Jugable |
|---|---|---|
| **C1** | Dimensiones creadas + barra lateral + Almanaque | Sí, en cajas grises |
| **C2** | Lobby real + NPC de acceso | Sí |
| **C3** | Ciudadela mínima: laboratorio, centro, mercado | Sí, completo |
| **C4** | Salón de Medallas + gimnasios en el mundo | Sí |
| **C5** | Ciudadela completa + cinemática | Producto |

**Nada bloquea al resto.** El código de interfaces (`UI-002`) no espera a que
exista un solo bloque: se prueba en una caja gris y se disfruta igual cuando
la ciudad esté hecha.

---

## Next Actions

1. Ratificar los tres espacios y el recorrido
2. Decidir gimnasios: ¿sala única o repartidos por el mundo? (§3)
3. `WLD-002` — crear las dimensiones por datapack
4. `PKM-002` — diseño del inicial y su primera misión

## Related Systems

- [La interfaz de cliente](../ui/interfaz-cliente.md) · [Core loop](../game-design/core-loop.md)
- [Progresión](../progression/progression-model.md)
