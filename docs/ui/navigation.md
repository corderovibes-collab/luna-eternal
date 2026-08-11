# Arquitectura de navegación

## Purpose

Definir el punto de entrada único al servidor y cómo se organiza todo lo que
cuelga de él. **Antes de implementar ningún sistema** (brief §26: UI-first).

## Dependencies

- [`../game-design/core-loop.md`](../game-design/core-loop.md)
- [`../progression/progression-model.md`](../progression/progression-model.md)
- [`../analysis/diosesmon-analysis.md`](../analysis/diosesmon-analysis.md) — el principio del hub

## Related Documents

- [`../trading/gts.md`](../trading/gts.md)

## Current Status

**PROPUESTA**, incluida una decisión de identidad (§1) que conviene ratificar.

## Last Decision

Pendiente.

---

## 1. El hub se llama **El Almanaque**

Diosesmon tiene el *PokePad*: un dispositivo, una tablet. Funciona, pero es la
metáfora obvia y podría estar en cualquier servidor.

Propongo **El Almanaque**, y no es un cambio de nombre cosmético: **es la
metáfora correcta para este juego en concreto.**

Un almanaque es, literalmente, un libro de **fases lunares, estaciones y cuándo
ocurren las cosas**. Es exactamente lo que
[vision.md](../game-design/vision.md) define como pilar:

> *El mundo tiene horarios. Saber cuándo es la progresión.*

| PokePad | El Almanaque |
|---|---|
| Un dispositivo que consultas | Un libro que **se va escribiendo** |
| Te da información | **Registra lo que descubres** |
| Igual para todos | El tuyo refleja tu recorrido |
| Metáfora de tecnología | Metáfora de **conocimiento** |

El Almanaque empieza **casi vacío** y se llena conforme el jugador descubre.
Las páginas bloqueadas no se ocultan: se ven en blanco, con lo que hace falta
para escribirlas. Eso convierte la propia interfaz en un recordatorio constante
de *"todavía me queda muchísimo por descubrir"* — la frase que el brief pone
como criterio de éxito.

Y es coherente con la marca: **Luna Eternal**.

---

## 2. Principios

| # | Principio | Origen |
|---|---|---|
| **N1** | **Un solo punto de entrada.** Ni `/gts`, ni `/kit`, ni `/warp` sueltos | Diosesmon, principio validado |
| **N2** | **Profundidad, no un cajón.** Máximo 3 niveles; nunca una rejilla plana de 30 botones | Riesgo detectado en Diosesmon §5 |
| **N3** | **Lo bloqueado se ve y se explica.** Nunca un botón que falla | Brief §27 |
| **N4** | **El progreso se ve sin abrir nada.** Scoreboard y tablist | Diosesmon §4 |
| **N5** | **Abre en contexto**, no en un menú | Ver §3 |

### Sobre N5, que es el que más cambia la experiencia

La mayoría de hubs abren en una rejilla de iconos. El Almanaque abre en
**Hoy**: qué fase lunar hay, qué cambia por eso, y qué tienes a medias.

El jugador no entra a "navegar un menú": entra a **enterarse de algo**. Y de
paso, la primera pantalla enseña el pilar del juego sin un tutorial.

---

## 3. El árbol

```
EL ALMANAQUE
│
├── HOY                         ← pantalla de entrada
│   ├── Fase lunar y qué cambia
│   ├── Objetivos en curso
│   ├── Novedades del mercado
│   └── Avisos (expiraciones, entregas)
│
├── VÍAS                        ← el perfil: 5 barras
│   ├── Explorador · Entrenador · Coleccionista · Comerciante · Criador
│   ├── Desbloqueos          (LOCKED / AVAILABLE / UNLOCKED)
│   ├── Marcas 🔷            en qué gastarlas
│   └── Logros y medallas
│
├── POKÉMON
│   ├── Equipo
│   ├── Caja (PC)
│   ├── Pokédex              lo visto, lo capturado, lo que falta
│   ├── Colección            formas, shinies, variantes
│   └── Criadero
│
├── MERCADO
│   ├── GTS                  buscar · vender · mis listados
│   ├── Tiendas              NPC
│   ├── Cartera              💰 PokéDólares · 🔷 Marcas
│   └── Historial            movimientos y precios
│
├── MUNDO
│   ├── Mapa                 lo descubierto
│   ├── Zonas                requisitos de acceso visibles
│   ├── Descubrimientos      el registro del explorador
│   └── Viajes               destinos desbloqueados y coste
│
├── SOCIAL
│   ├── Amigos
│   ├── Clan
│   ├── Clasificaciones      por vía, no una general
│   └── Perfil de jugador    ver el Almanaque público de otro
│
└── PERSONALIZACIÓN
    ├── Cosméticos · Títulos · Efectos
    └── Apariencia del Almanaque
```

**Tres niveles como máximo.** Cualquier cosa a cuatro niveles está mal colocada.

### Decisiones del árbol

**"Vías" va segundo, justo después de Hoy.** Es la respuesta a *"¿quién soy en
este servidor?"*, y ponerlo arriba comunica que el progreso es un perfil, no un
número ([progression-model.md §1](../progression/progression-model.md)).

**"Aventuras" del brief §25 desaparece.** Sus contenidos (quests, historia,
hunts, eventos) se reparten: los objetivos en curso van a **Hoy**, donde el
jugador los verá de verdad, y las recompensas a **Vías**. Una sección "Aventuras"
sería un cajón que nadie abre dos veces.

**"Clasificaciones" es por vía, nunca general.** Una tabla global reintroduce por
la puerta de atrás el nivel único que se rechazó en `PROG-001`.

**"Perfil de jugador"** es el *player scan* de Diosesmon: ver el Almanaque de
otro convierte el progreso en estatus social. Es de lo más barato de construir y
de lo que más retiene.

---

## 4. La pantalla de entrada

```
╔══════════════════════════════════════════════╗
║  EL ALMANAQUE                    🌕 Llena     ║
╠══════════════════════════════════════════════╣
║  Con luna llena, en las colinas               ║
║  aparecen criaturas que no salen              ║
║  ninguna otra noche.                          ║
║                          [ Ver qué cambia ]   ║
╟──────────────────────────────────────────────╢
║  EN CURSO                                     ║
║   · Descubrir las costas del norte    2/3     ║
║   · Vender 5 objetos en el GTS        3/5     ║
╟──────────────────────────────────────────────╢
║  AVISOS                                       ║
║   · Tu Gardevoir se vendió       +91 000 💰   ║
║   · 1 listado caduca en 6 h                   ║
╟──────────────────────────────────────────────╢
║  💰 142 300      🔷 26 Marcas                 ║
╟──────────────────────────────────────────────╢
║  [Vías] [Pokémon] [Mercado] [Mundo] [Social]  ║
╚══════════════════════════════════════════════╝
```

El aviso de la fase lunar **cambia con el ciclo** y es distinto según lo que el
jugador ya haya descubierto: al principio es vago (*"algo cambia con la luna"*),
y se vuelve específico conforme escribe su Almanaque. La interfaz **premia el
conocimiento**, igual que el juego.

---

## 5. Cómo se ve lo bloqueado

Nunca se oculta. Nunca falla en silencio.

```
┌─ CRIADERO ──────────────────────── 🔒 ─┐
│ Aún no has escrito esta página.        │
│                                        │
│ Necesitas: Criador I                   │
│ Cómo:      cría tu primer Pokémon      │
│            en la guardería de Solaz    │
│                          [ Cómo llegar ]│
└────────────────────────────────────────┘
```

Tres elementos obligatorios: **qué es · qué falta · qué hacer ahora**. El tercero
es el que más se olvida y el único que convierte un muro en un objetivo.

---

## 6. Fuera del Almanaque

No todo cabe en un menú. Lo que vive en pantalla:

| Elemento | Contenido | Por qué fuera |
|---|---|---|
| **Scoreboard** | Vía dominante, medallas, saldo, fase lunar | Progreso pasivo (N4) |
| **Tablist** | Nombre, título, vía dominante | Identidad social |
| **HUD de captura** | Probabilidad, ventaja de tipo | Decisión del bucle 1 |
| **Avisos** | Ventas, expiraciones, descubrimientos | No requieren abrir nada |

**Regla de contención:** si algo se mira más de una vez por minuto, va al HUD.
Si se mira una vez por sesión, va al Almanaque. Nada en los dos sitios.

---

## 7. Implementación

| Capa | Con qué | Estado |
|---|---|---|
| **Menús** | Interfaz de servidor sobre menús de cofre + Polymer | Ya instalado en producción |
| **Estados de bloqueo** | Mod propio, consultando progresión | D-011 |
| **Scoreboard y tablist** | Mod propio | D-011 |
| **Pantallas completas** | FancyMenu (resourcepack) | Ya instalado |
| **HUD** | Fuentes de mapa de bits + espacios negativos | Técnica ya conocida del proyecto anterior |

**Nada de esto exige un mod de cliente obligatorio.** Es una decisión
deliberada: Diosesmon exige un modpack para "high-end rigs", y eso excluye
jugadores ([diosesmon-analysis.md §3](../analysis/diosesmon-analysis.md)). El
launcher propio permite distribuir mejoras opcionales, pero **el servidor debe
ser jugable con un cliente normal**.

---

## 8. Riesgos

| Riesgo | Mitigación |
|---|---|
| Menús de cofre limitan el diseño visual | Fuentes personalizadas y arte en resourcepack. Ya se hizo en el proyecto anterior |
| El Almanaque se convierte en un cajón | N2: máximo 3 niveles, revisión en cada sistema nuevo |
| Demasiada información en "Hoy" | Máximo 3 objetivos y 3 avisos; el resto, en su sección |
| El nombre no gusta | Es ratificable ahora; cambiarlo después cuesta arte y textos |

---

## Next Actions

1. Ratificar **El Almanaque** como nombre e identidad del hub
2. `UI-002` — maquetas de cada sección
3. `UI-003` — scoreboard y tablist
4. Implementación tras el esquema de datos

## Related Systems

- [Progresión](../progression/progression-model.md) · [GTS](../trading/gts.md)
- [Visión](../game-design/vision.md) · [Diosesmon](../analysis/diosesmon-analysis.md)
