# Core Loop

## Purpose

Definir el bucle de juego en sus tres escalas de tiempo, y las decisiones que
el jugador toma en cada una.

## Dependencies

- [`vision.md`](vision.md)

## Related Documents

- [`../analysis/diosesmon-analysis.md`](../analysis/diosesmon-analysis.md)

## Current Status

**PROPUESTA.** Sustituye al loop planteado en el brief §15, con justificación.

## Last Decision

Pendiente de aprobación.

---

## 1. Por qué NO usamos el loop del brief

El brief propone:

```
EXPLORAR → ENCONTRAR → CAPTURAR → ENTRENAR → OBJETIVOS → RECURSOS
→ MEJORAR → DESBLOQUEAR → ZONAS NUEVAS → RETOS → COMERCIAR
→ COLECCIONAR → COMPETIR → ENDGAME
```

El brief pedía explícitamente analizarlo antes de aceptarlo. Tiene cuatro
problemas estructurales:

**1 · No es un bucle, es una lista.** Catorce pasos que terminan en ENDGAME. Un
bucle vuelve al principio; este acaba. Lo que describe es la *ruta del jugador*
(valiosa, pero es otro documento: `player-journey.md`).

**2 · No hay decisiones.** Cada paso lleva al siguiente automáticamente. Sin
bifurcación no hay juego, hay una cinta transportadora. *Encontrar → capturar*
solo es interesante si a veces la respuesta correcta es **no capturar**.

**3 · Mezcla tres escalas de tiempo.** "Capturar" dura 30 segundos;
"desbloquear zonas" dura semanas. Tratarlos en la misma secuencia impide
diseñar el ritmo de ninguno de los dos.

**4 · No hay fracaso.** Sin posibilidad de perder algo, no hay tensión, y sin
tensión el éxito no sabe a nada.

---

## 2. El modelo propuesto: tres bucles anidados

```
┌─────────────────────────────────────────────────────────────┐
│  BUCLE 3 · DESCUBRIMIENTO           semanas                 │
│  conocer el mundo → acceder → nuevo mundo que conocer       │
│                                                             │
│   ┌───────────────────────────────────────────────────┐     │
│   │  BUCLE 2 · EXPEDICIÓN          30-90 min          │     │
│   │  preparar → salir → arriesgar → volver → invertir │     │
│   │                                                   │     │
│   │    ┌─────────────────────────────────────┐        │     │
│   │    │  BUCLE 1 · ENCUENTRO      2-5 min   │        │     │
│   │    │  leer → decidir → actuar → resultado│        │     │
│   │    └─────────────────────────────────────┘        │     │
│   └───────────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────┘
```

Cada bucle alimenta al de fuera. Cada uno tiene **su propia decisión**.

---

## 3. BUCLE 1 · Encuentro (2-5 min)

```
LEER el entorno  →  DECIDIR  →  ACTUAR  →  RESULTADO
      ↑                                        │
      └────────────────────────────────────────┘
```

**La decisión:** *¿esto merece mis recursos?*

El jugador lleva balls limitadas y de distinto valor. Un encuentro le presenta
información —especie, nivel, señales de rareza, fase lunar, bioma— y debe
decidir si gasta, y **cuánto** gasta.

| Decisión | Consecuencia |
|---|---|
| Ignorar | Ahorras, pero quizá era el bueno |
| Ball barata | Riesgo de fuga |
| Ball cara | Seguro, pero el recurso escaso baja |
| Debilitar antes | Cuesta tiempo y PP, sube la probabilidad |

**Por qué funciona:** convierte una acción reflejo en un juicio. Y el juicio
mejora con el conocimiento del mundo — que es la progresión real (visión §2).

**Fracaso posible:** el Pokémon huye. Se pierde el recurso y la oportunidad.

---

## 4. BUCLE 2 · Expedición (30-90 min)

```
PREPARAR → VIAJAR → EXPLORAR → [presión creciente] → VOLVER → INVERTIR
    ↑                                                            │
    └────────────────────────────────────────────────────────────┘
```

**La decisión:** *¿sigo o vuelvo?*

Cuanto más lejos y más tiempo, mejor el contenido — y menos recursos quedan.
Volver es seguro; seguir es rentable hasta que deja de serlo.

| Fase | Qué ocurre | Decisión |
|---|---|---|
| **Preparar** | Comprar consumibles, elegir equipo y destino | Presupuesto: ¿voy sobrado o voy lejos? |
| **Viajar** | Llegar a la zona | ¿Pago viaje rápido o voy andando y encuentro cosas? |
| **Explorar** | Bucle 1 en repetición | Cada encuentro consume el presupuesto |
| **Presión** | Se agotan los recursos | ¿Sigo con lo justo? |
| **Volver** | Al pueblo: depositar, vender, guardar | — |
| **Invertir** | Mejorar equipo, comerciar, subir de nivel | ¿Ahorro o me reequipo? |

**Por qué funciona:** genera el ritmo de sesión (tensión → alivio) y **crea la
razón para volver al pueblo**, que es donde vive la comunidad y la economía.
Un servidor donde nunca vuelves a un sitio común no tiene comunidad.

**Fracaso posible:** quedarse sin recursos lejos y volver con las manos vacías.
Se pierde tiempo e inversión, **nunca los Pokémon capturados** — el castigo debe
picar, no hacer daño real.

---

## 5. BUCLE 3 · Descubrimiento (semanas)

```
CONOCER → DEMOSTRAR → ACCEDER → un mundo nuevo que conocer
    ↑                                        │
    └────────────────────────────────────────┘
```

**La decisión:** *¿en qué me especializo?*

El jugador no puede desbloquearlo todo a la vez. Elegir una rama significa
renunciar temporalmente a otra — y eso crea **jugadores distintos entre sí**,
que es la precondición del comercio.

| Vía | Se demuestra con | Abre |
|---|---|---|
| **Explorador** | Descubrimientos, mapa, puntos de interés | Zonas remotas, rutas |
| **Entrenador** | Combate, medallas, niveles | Retos, PvP, gimnasios |
| **Coleccionista** | Pokédex, especies raras | Especies exclusivas |
| **Comerciante** | Volumen, reputación | Funciones de mercado, filtros GTS |
| **Criador** | Cría, IV/EV | Competitivo |

**Por qué funciona:** el brief pide progresión lenta pero no aburrida. La
lentitud aquí no viene de cooldowns ni de grind: viene de que **hay más caminos
que tiempo**. El jugador siempre está ocupado, y siempre le falta algo — que es
exactamente *"todavía me queda muchísimo por descubrir"*.

**Y sostiene la economía:** si todos los jugadores fueran iguales, nadie tendría
nada que ofrecer a nadie. La especialización **crea la demanda**. El comercio no
es un sistema añadido: es la consecuencia natural del bucle 3.

---

## 6. El tiempo atraviesa los tres bucles

> ✅ **Verificado en el jar de Cobblemon 1.7.3** (`PKM-001`): `moonPhase`,
> `timeRange`, `isRaining`, `structures`, `minY/maxY`, `neededNearbyBlocks` y
> `minLureLevel` son condiciones nativas de aparición. **Datapack, sin Java.**

Pero el dato obliga a separar dos ritmos — el ciclo lunar de Minecraft dura
**2 h 40 min**, no una semana (8 fases × 20 min por día):

### Ritmo corto — fase lunar, hora y clima (nativo, gratis)

| Bucle | Efecto |
|---|---|
| **1 · Encuentro** | Cambia qué aparece y con qué rareza |
| **2 · Expedición** | Cambia el destino que conviene *ahora mismo* |
| **3 · Descubrimiento** | Saber qué sale con luna llena **es** conocimiento del mundo |

Da textura: el mismo sitio no se siente igual dos veces, y **premia al jugador
que sabe esperar veinte minutos** en vez de al que farmea sin pensar.

### Ritmo largo — calendario del servidor (mod propio)

Lo que el ciclo lunar **no** puede dar: eventos semanales, temporadas, "vuelve
el jueves". Requiere un calendario propio, desacoplado de la luna de Minecraft.

**Consecuencia económica:** los precios fluctúan solos en las dos escalas. Lo
abundante hoy es caro mañana. El mercado se mueve sin que nadie intervenga, y
aparece un rol de comerciante que **especula con conocimiento**, no con oro
acumulado.

### El señuelo cierra el bucle 2

`minLureLevel` (1-3) es un sistema nativo con 585 usos en los spawns oficiales.
Un consumible que sube el nivel de señuelo:

- es un **sink económico** (se compra y se gasta),
- da **decisión** al bucle 2 (*¿gasto señuelo aquí o guardo para la zona buena?*),
- y **ya está construido**. No hay que programarlo.

Es la pieza que hace que "preparar la expedición" sea una decisión real y no un
trámite de comprar balls.

---

## 7. Endgame

El endgame no es un cuarto bucle: es el bucle 3 cuando las vías se cruzan.

- **Colección completa** de una rama → prestigio visible
- **Competitivo** → PvP con Pokémon criados, no comprados
- **Contenido de grupo** → usar la calibración medida en el proyecto anterior:
  *% en solitario × 5,2 = margen del grupo de 12*
- **Economía** → los veteranos son los proveedores del mercado
- **Descubrimiento residual** → siempre debe quedar algo sin encontrar

**Regla dura:** el endgame **no puede ser el único sitio donde pasan cosas**. Si
los primeros niveles son un peaje hacia el contenido real, el diseño ha
fracasado.

---

## 8. Qué queda por resolver

Honestidad sobre el estado:

- **Cuánto** cuesta cada consumible y cuánto rinde una expedición → `ECO-001`
- Cuántos descubrimientos hacen falta para abrir una zona → `PROG-001`
- Si las 5 vías son las correctas, o son demasiadas para empezar
- Cómo se enseña todo esto sin un tutorial de 40 minutos → `UI-001`

---

## Next Actions

1. Aprobación o corrección del usuario
2. Verificar fase lunar en Cobblemon
3. `ECO-001` — economía derivada del bucle 2
4. `PROG-001` — modelo de progresión derivado del bucle 3

## Related Systems

- [Visión](vision.md)
- Economía · Progresión · Mundo · UI — pendientes
