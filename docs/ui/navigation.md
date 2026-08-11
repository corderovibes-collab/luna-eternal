# Arquitectura de navegación — El Almanaque

## Purpose

Definir el punto de entrada único al juego, sus secciones, y la barra lateral
permanente. **Antes de implementar ningún sistema** (brief §26: UI-first).

## Dependencies

- [`../game-design/core-loop.md`](../game-design/core-loop.md)
- [`../progression/progression-model.md`](../progression/progression-model.md)
- [`../world/world-structure.md`](../world/world-structure.md)
- [`../analysis/diosesmon-analysis.md`](../analysis/diosesmon-analysis.md) §0

## Related Documents

- [`../trading/gts.md`](../trading/gts.md) · [`../economy/economy-overview.md`](../economy/economy-overview.md)

## Current Status

**PROPUESTA v2**, rehecha el 2026-08-11 tras observar el PokePad de Diosesmon.

## Last Decision

Pendiente. Incluye una decisión de identidad (§1).

---

## 0. La regla que manda sobre todas

> **Al jugador le da pereza escribir comandos. Si algo solo se puede hacer con
> un comando, para la mayoría de la gente ese algo no existe.**

Consecuencias, sin excepciones:

- **Todo se hace con clics.** Comandos solo para administración.
- Nada obliga a recordar nombres, IDs ni sintaxis.
- Los comandos que existan son **atajos para quien los quiera**, nunca el
  único camino.
- Si al diseñar un sistema la respuesta es *"el jugador escribe `/algo`"*, el
  diseño está incompleto.

---

## 1. El hub se llama **El Almanaque**

Diosesmon tiene el *PokePad*: una tablet. Funciona, pero es la metáfora obvia
y podría estar en cualquier servidor.

**El Almanaque** es la metáfora correcta para *este* juego. Un almanaque es
literalmente un libro de **fases lunares y de cuándo ocurren las cosas**, que
es el pilar de [vision.md](../game-design/vision.md):

> *El mundo tiene horarios. Saber cuándo es la progresión.*

| PokePad | El Almanaque |
|---|---|
| Un dispositivo que consultas | Un libro que **se va escribiendo** |
| Te da información | **Registra lo que descubres** |
| Igual para todos | El tuyo refleja tu recorrido |
| Metáfora de tecnología | Metáfora de **conocimiento** |

Empieza casi vacío. Las secciones bloqueadas **no se ocultan**: se ven como
páginas en blanco con lo que hace falta para escribirlas. La interfaz es en sí
misma el recordatorio de *"todavía me queda muchísimo por descubrir"*.

Y encaja con la marca: **Luna Eternal**.

---

## 2. Cómo se abre — sin comandos

**Un objeto en el inventario: el Almanaque.** Clic derecho en cualquier
momento y en cualquier sitio.

| Regla | Motivo |
|---|---|
| Se entrega al entrar por primera vez | No hay que buscarlo |
| **No se puede tirar, soltar ni perder** | Perderlo dejaría al jugador sin juego |
| Si falta, se repone solo al conectar | Red de seguridad |
| Ocupa un hueco fijo del inventario | Siempre en el mismo sitio |
| `/menu` existe como atajo | Para quien prefiera teclado |

> Alternativa evaluada y descartada: abrirlo con una tecla. Requiere **mod de
> cliente obligatorio**, y eso excluye jugadores
> ([diosesmon-analysis.md](../analysis/diosesmon-analysis.md) §3). El objeto
> funciona con cualquier cliente.

---

## 3. La barra lateral — el progreso sin abrir nada

Siempre visible a la derecha. Es lo que hace que el jugador **sienta** que
progresa sin tener que consultarlo (principio N4).

```
╔═══════════════════════╗
║   LUNA ETERNAL        ║
╠═══════════════════════╣
║ 🌕 Luna llena         ║   ← identidad del proyecto
╟───────────────────────╢
║ 💰 142.300            ║   PokéDólares
║ 🔷 26 Marcas          ║
║ 🪙 0 ReportCoins      ║
╟───────────────────────╢
║ Vía:   Explorador IV  ║   ← la vía dominante, no un nivel
║ Clan:  Los Errantes   ║
║ Oficio: Criador       ║
╟───────────────────────╢
║ Medallas: ▮▮▮▯▯▯▯▯    ║
╚═══════════════════════╝
```

### Tres decisiones, contra lo que hace Diosesmon

**1 · La fase lunar va arriba del todo.** Es el pilar del juego. Cada vez que
el jugador mira la pantalla, ve el estado del mundo.

**2 · Ningún hueco dice "¡Cómpralo!".** En Diosesmon, el rango de un jugador
sin rango pone *"¡Cómpralo!"* — el HUD se convierte en un anuncio permanente.
Aquí un hueco vacío **informa** (`Sin clan`), no vende. La tienda tiene su
sitio; el HUD no lo es.

**3 · No se anuncia lo que no existe.** Diosesmon reserva una fila para
`DIVISION: ¡MUY PRONTO!`. Aquí una sección sin construir **no aparece**.

---

## 4. El Almanaque — la pantalla principal

Menú de servidor de 6 filas (54 huecos). **Agrupado por temas**, no una
rejilla plana de iconos.

```
╔═══════════════════════════════════════════════════════════════════╗
║                        EL  ALMANAQUE                              ║
║  [perfil]   🌕 Luna llena · noche      💰142.300  🔷26  🪙0        ║
╠═══════════════════════════════════════════════════════════════════╣
║  TUS POKÉMON                                                      ║
║   📕 Pokédex    📦 Caja(PC)   🎒 Mochila   ❤ Curar   🥚 Criadero  ║
╟───────────────────────────────────────────────────────────────────╢
║  AVENTURA                                                         ║
║   📜 Misiones   🎯 Cazas      🏅 Medallas  💎 Tesoros  🗺 Explorar ║
╟───────────────────────────────────────────────────────────────────╢
║  ECONOMÍA                                                         ║
║   ⚖ GTS        🏪 Tienda     🔨 Oficios   📈 Historial            ║
╟───────────────────────────────────────────────────────────────────╢
║  TÚ                                                               ║
║   ✨ Vías       👕 Cosméticos 🎁 Kits      🛡 Clan     ⭐ Rangos   ║
╟───────────────────────────────────────────────────────────────────╢
║  🚕 Viajes                                            ✖ Cerrar    ║
╚═══════════════════════════════════════════════════════════════════╝
```

### Por qué agrupado y no una rejilla de 15

El PokePad de Diosesmon es una rejilla plana de 15 iconos sin jerarquía. Es
exactamente el "cajón desastre" que este documento advertía en su v1: **con 15
elementos indistintos, el jugador no busca — recuerda posiciones**, y cada
sección nueva empeora el problema.

Cuatro grupos de 4-5 elementos se leen de un vistazo. Y **hay sitio para
crecer**: una sección nueva entra en su grupo sin rediseñar nada.

### Las secciones

| Sección | Qué hace | Se abre en |
|---|---|---|
| **Pokédex** | Vistos, capturados, lo que falta, dónde y cuándo aparece | G0 |
| **Caja (PC)** | Almacenamiento, buscar y ordenar | G0 |
| **Mochila** | Objetos | G0 |
| **Curar** | Restaurar el equipo (con coste/cooldown) | G0 |
| **Criadero** | Cría, IV/EV, linajes | Vía Criador I |
| **Misiones** | Objetivos activos, historia, diarias | G0 |
| **Cazas** | Objetivos rotativos con recompensa | Tras el tutorial |
| **Medallas** | Salón: qué tienes, cuál sigue, **dónde está** | G0 |
| **Tesoros** | Recompensas por descubrimiento y colección | Vía Explorador I |
| **Explorar** | Mapa, zonas, requisitos de acceso visibles | G0 |
| **GTS** | Mercado entre jugadores — ver [gts.md](../trading/gts.md) | G0 consultar · G2 vender |
| **Tienda** | Compra a NPC de consumibles | G0 |
| **Oficios** | Profesiones con progresión propia | Tutorial completo |
| **Historial** | Movimientos de dinero y precios de mercado | G4 |
| **Vías** | Las 5 reputaciones, desbloqueos, Marcas | G0 |
| **Cosméticos** | Aspecto, títulos, efectos | G0 |
| **Kits** | Inicial gratuito · periódicos · de rango | G0 |
| **Clan** | Crear, unirse, gestionar | Nivel de progresión |
| **Rangos** | Qué incluye cada uno y cómo se consigue | G0 |
| **Viajes** | Puntos de la ciudadela y destinos desbloqueados | G0 dentro de la ciudadela |

> **"Medallas" sustituye a "Gyms".** No es un cambio de nombre: en Diosesmon
> abre la sala donde están todos los gimnasios. Aquí **muestra tu progreso y te
> dice dónde ir** — los gimnasios están repartidos por el mundo
> ([world-structure.md](../world/world-structure.md) §3).

---

## 5. Estados: nada falla en silencio

Todo elemento tiene estado explícito (brief §27), y la interfaz **siempre**
explica el porqué:

`AVAILABLE · LOCKED · UNLOCKING · UNLOCKED · COOLDOWN · DISABLED · ERROR`

```
┌─ CRIADERO ─────────────────────── 🔒 ─┐
│ Aún no has escrito esta página.        │
│                                        │
│ Necesitas: Criador I                   │
│ Cómo:      cría tu primer Pokémon en   │
│            la guardería de Solaz       │
│                                        │
│            [ Cómo llegar ]             │
└────────────────────────────────────────┘
```

**Tres elementos obligatorios: qué es · qué falta · qué hacer ahora.** El
tercero es el que casi siempre se olvida y el único que convierte un muro en un
objetivo.

Un icono bloqueado **nunca** se oculta y **nunca** devuelve un error al pulsar.

---

## 6. Fuera del Almanaque

| Elemento | Contenido | Por qué fuera |
|---|---|---|
| **Barra lateral** | §3 | Progreso pasivo |
| **Tablist** | Nombre, título, vía dominante | Identidad social |
| **HUD de captura** | Probabilidad, ventaja de tipo | Decisión del bucle 1 |
| **Avisos** | Ventas del GTS, expiraciones, descubrimientos | No requieren abrir nada |

**Regla de contención:** si algo se mira más de una vez por minuto, va al HUD.
Si se mira una vez por sesión, va al Almanaque. **Nada en los dos sitios.**

---

## 7. Implementación

| Capa | Con qué | Cliente obligatorio |
|---|---|---|
| Menús | **Menús de cofre servidos por el mod propio** | ❌ No |
| Iconos | Objetos vanilla + modelos personalizados por resourcepack | ❌ No |
| Textos y marcos | Fuentes de mapa de bits + espacios negativos | ❌ No |
| Barra lateral | Scoreboard del mod propio | ❌ No |
| Pantallas completas | FancyMenu (resourcepack, opcional) | ❌ No |

**Ningún mod de cliente es obligatorio.** Decisión firme: Diosesmon exige un
modpack "para equipos potentes" y eso excluye jugadores; un MMORPG necesita
masa crítica. El launcher propio puede repartir mejoras **opcionales**.

### El framework de interfaces

Para que esto no acabe en 20 clases copiadas y pegadas, el mod necesita una
base común. Especificación en `UI-002`:

```
Menu            plantilla: título, tamaño, contenido, refresco
MenuItem        icono + nombre + descripción + estado + acción
MenuRegistry    abre por id; guarda la pila para el botón "atrás"
Paginated       listas largas (Pokédex, GTS, PC) con páginas
LockState       AVAILABLE/LOCKED/… y cómo se dibuja cada uno
Sidebar         la barra lateral, con refresco por eventos
```

**Reglas del framework:**

| | |
|---|---|
| Un menú **nunca** consulta la base en el hilo del servidor | Cargar antes, dibujar después |
| Todo clic se **revalida en servidor** | P6: nunca confiar en el cliente |
| Los menús con datos vivos se **refrescan solos** | Saldo, listados del GTS |
| Toda acción con coste pide **confirmación** | Ver [gts.md](../trading/gts.md) §6 |
| Cerrar un menú **nunca** pierde estado | Se reabre donde estaba |

---

## 8. Riesgos

| Riesgo | Mitigación |
|---|---|
| Los menús de cofre limitan el diseño visual | Fuentes y arte propio en resourcepack; ya se hizo en el proyecto anterior |
| El Almanaque crece hasta ser inmanejable | Los 4 grupos son el límite. Sección nueva → entra en un grupo o no entra |
| Consultas a la BD al abrir menús | Caché en memoria del jugador conectado; la BD solo al entrar y al actuar |
| El nombre no gusta | Ratificable ahora; cambiarlo después cuesta arte y textos |

---

## Next Actions

1. Ratificar **El Almanaque**, los 4 grupos y la barra lateral
2. `UI-002` — construir el framework de menús en el mod
3. `UI-003` — barra lateral
4. `UI-004` — maquetas sección por sección

## Related Systems

- [Estructura del mundo](../world/world-structure.md) · [Progresión](../progression/progression-model.md)
- [GTS](../trading/gts.md) · [Economía](../economy/economy-overview.md)
