# Modelo de progresión

## Purpose

Definir cómo avanza un jugador: qué mide el progreso, qué desbloquea, y cómo se
consigue que sea lento sin ser aburrido.

## Dependencies

- [`../game-design/core-loop.md`](../game-design/core-loop.md) — bucle 3
- [`../economy/economy-overview.md`](../economy/economy-overview.md) — las Marcas

## Related Documents

- [`../trading/gts.md`](../trading/gts.md) — su acceso es progresión
- `../ui/navigation.md` — pendiente

## Current Status

**PROPUESTA.**

## Last Decision

Pendiente.

---

## 1. La decisión de fondo: no hay nivel de jugador

La solución obvia sería un "Nivel de Entrenador" del 1 al 100. **La rechazo**,
y por el criterio del brief §14:

> *"¿Podría este sistema pertenecer a cualquier servidor Cobblemon?"*

Un número que sube al hacer cualquier cosa: sí, podría. Y además tiene tres
defectos reales:

1. **Comprime a todos en una sola escala.** Dos jugadores con nivel 40 son
   intercambiables. Sin diferencia no hay comercio.
2. **Convierte todo en XP.** El jugador deja de elegir qué hacer y hace lo que
   más XP dé por hora.
3. **Se termina.** Al llegar a 100, la progresión ha muerto.

### En su lugar: cinco reputaciones independientes

```
        EXPLORADOR  ███████░░░  IV
        ENTRENADOR  ████░░░░░░  II
     COLECCIONISTA  █████████░  V
       COMERCIANTE  ██░░░░░░░░  I
            CRIADOR  ░░░░░░░░░░  —
```

**Tu progreso no es un número: es un perfil.** Dos jugadores con el mismo tiempo
jugado son personas distintas, y esa diferencia es exactamente lo que crea la
demanda que el GTS necesita.

Cada vía tiene **5 niveles**. Se sube haciendo *esa* actividad — no hay
conversión ni atajos entre vías.

| Vía | Sube con | Abre |
|---|---|---|
| **Explorador** | Descubrimientos, biomas, estructuras, distancia | Zonas remotas, rutas, viaje rápido |
| **Entrenador** | Combates, medallas, niveles, incursiones | Retos, PvP, contenido de grupo |
| **Coleccionista** | Pokédex, especies raras, formas, shinies | Especies exclusivas, almacenamiento |
| **Comerciante** | Volumen y variedad de operaciones | Funciones del GTS (ver [gts.md §2](../trading/gts.md)) |
| **Criador** | Cría, IV/EV, linajes | Competitivo, guardería avanzada |

---

## 2. Cómo se desbloquea algo: dos factores

Ningún desbloqueo se consigue solo con tiempo ni solo con recursos.

```
DESBLOQUEO  =  requisito de Vía   +   coste en 🔷 Marcas
               (haberlo hecho)        (haberlo ganado)
```

**Por qué dos factores.** Cada uno tapa el agujero del otro:

- **Solo requisito de Vía** → todo se desbloquea automáticamente al jugar. No
  hay decisión, y el jugador nunca elige nada.
- **Solo Marcas** → quien acumula lo compra todo. Vuelve a ser un número.

Con ambos, el jugador **tiene que elegir**: las Marcas son finitas y hay más
desbloqueos que Marcas. Esa escasez es la que hace que el perfil de cada uno sea
distinto.

Y recordemos que las Marcas están **vinculadas al jugador y no se comercian**
(`ECO-001` §2): la progresión **no se puede comprar a otro jugador ni con dinero
real**. Esa es la línea que separa esto de T4 en
[monetization.md](../economy/monetization.md).

---

## 3. Estados de desbloqueo

Siguiendo el brief §27, todo desbloqueo tiene estado explícito y **la UI siempre
explica el porqué**:

| Estado | Significa | Qué muestra la interfaz |
|---|---|---|
| `LOCKED` | No cumple requisitos | Qué falta y cuánto |
| `AVAILABLE` | Puede desbloquearlo ya | Coste en Marcas y botón |
| `UNLOCKING` | En proceso (si lleva tiempo) | Cuánto queda |
| `UNLOCKED` | Activo | — |
| `COOLDOWN` | Temporalmente no usable | Cuándo vuelve |
| `DISABLED` | Apagado por el servidor | Motivo |
| `ERROR` | Fallo | Qué hacer |

```
┌─ VIAJE RÁPIDO: COSTAS ──────────── 🔒 ─┐
│ Explorador III      ✔  (tienes IV)     │
│ Descubrir 3 costas  ✔  (3/3)           │
│ Coste: 🔷 40 Marcas    tienes 26       │
│                                        │
│ Te faltan 14 Marcas.                   │
│ [ Cómo conseguir Marcas ]              │
└────────────────────────────────────────┘
```

**Nunca un botón que falla en silencio.** El jugador siempre sabe qué le falta y
cómo conseguirlo — eso es la diferencia entre progresión y frustración.

---

## 4. El recorrido

No son "tiers" arbitrarios: son fases definidas por **qué puede hacer** el
jugador.

| Fase | Duración orientativa | Está abierto | Está cerrado |
|---|---|---|---|
| **Llegada** | 1ª hora | Capturar, explorar la zona inicial, consultar GTS | Todo lo demás |
| **Asentamiento** | Días 1-3 | Comprar en GTS, primeras vías a nivel I-II, expediciones | Vender, viaje rápido, zonas 2+ |
| **Especialización** | Semanas 1-3 | Vender en GTS, elegir vía dominante, zonas 2-3 | Filtros avanzados, competitivo |
| **Dominio** | Meses 1-2 | Vía principal a IV-V, zonas remotas, cría | Contenido de grupo mayor |
| **Veteranía** | Mes 3+ | Segunda vía, competitivo, prestigio | Nada estructural |
| **Prestigio** | Continuo | Colección completa, cosméticos caros, liderazgo | — |

**Criterio de aceptación:** en la primera hora el jugador debe haber
**descubierto algo** —una especie que no esperaba, un sitio, una mecánica— aunque
casi todo siga cerrado. La progresión es lenta; **el descubrimiento no**
([vision.md §6](../game-design/vision.md)).

---

## 5. Lento sin grind

El brief lo pide explícitamente y es la parte más difícil. Cuatro mecanismos, y
**ninguno es un cooldown arbitrario**:

### 5.1 · Más caminos que tiempo

La lentitud no viene de que cada cosa cueste mucho, sino de que **hay cinco vías
y no da tiempo a todas**. El jugador siempre avanza rápido en algo y siempre le
falta otra cosa. Es la fuente principal de longevidad y no cuesta nada
implementarla.

### 5.2 · El progreso viene de jugar, no de esperar

Ninguna vía sube con el reloj. Suben capturando, combatiendo, explorando,
comerciando y criando. **Nunca hay un momento en el que la acción óptima sea
desconectarse.**

### 5.3 · Los requisitos son actividades, no cifras

```
❌  "Alcanza 50 000 de XP de Explorador"
✅  "Descubre las tres costas del norte"
```

El segundo es memorable, dirige al jugador a un sitio concreto y genera una
historia. El primero es una barra.

### 5.4 · El conocimiento también progresa, y no se puede farmear

Saber qué sale con luna llena, dónde pescar, qué señuelo usar. **No está en
ninguna barra, no se puede comprar y no se pierde.** Es la progresión más barata
de producir y la más difícil de saltarse — y es la que sostiene el pilar de
identidad de la visión.

---

## 6. Lo que la progresión NO debe bloquear

Errores frecuentes que quedan descartados por decisión:

| No se bloquea | Por qué |
|---|---|
| **Capturar Pokémon** | Es el core loop. Bloquearlo es bloquear el juego |
| **Consultar el GTS** | Es el mejor tutorial de economía que tenemos |
| **Hablar y socializar** | La comunidad no es una recompensa |
| **Ver el mundo** | Se puede *llegar* a sitios difíciles; no se ocultan |
| **Cosméticos de pago** | T1 es independiente de la progresión (D-007) |

> **Regla:** la progresión abre **profundidad**, nunca cierra lo básico. Un
> jugador nuevo debe poder jugar el juego completo en pequeño, no una versión
> mutilada.

---

## 7. Medallas y gimnasios

Son el hito visible de la vía **Entrenador**, y ya sabemos que hay que
construirlos (Cobblemon no los trae, y CobbleVerse está descartado por licencia).

Deciden acceso a contenido de combate, no a exploración ni a economía — así una
sola vía no se convierte en obligatoria para todos.

Diseño detallado pendiente: `PROG-003`.

---

## 8. Riesgos

| Riesgo | Mitigación |
|---|---|
| **Cinco vías son demasiadas para empezar** | Abrir 3 en el lanzamiento (Explorador, Entrenador, Coleccionista) y añadir Comerciante y Criador después. Se puede ampliar; recortar es peor |
| El jugador no entiende que hay cinco | `UI-001`: el perfil de cinco barras es lo primero que ve |
| Una vía queda claramente mejor | Telemetría de reparto; ninguna debe superar el 40 % |
| Las Marcas se calibran mal | Es un número; se ajusta con datos reales |

> El primero es real. **Recomiendo lanzar con tres vías.** Cinco perfiles vacíos
> el primer día comunican peor que tres con contenido denso.

---

## Next Actions

1. `PROG-002` — catálogo de desbloqueos con requisitos y coste en Marcas
2. `PROG-003` — medallas y gimnasios
3. `UI-001` — el perfil de vías como pantalla principal
4. Calibrar: cuántas Marcas por hito, cuántas cuesta cada desbloqueo

## Related Systems

- [Core loop](../game-design/core-loop.md) · [Economía](../economy/economy-overview.md)
- [GTS](../trading/gts.md) · [Monetización](../economy/monetization.md)
