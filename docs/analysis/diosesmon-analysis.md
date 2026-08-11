# Análisis de Diosesmon Official PRO

## Purpose

Estudiar Diosesmon como **referencia de producto**: qué sistemas usa, por qué
funcionan, qué riesgos tienen, y qué adaptamos / mejoramos / evitamos.

**No es nuestra especificación.** Se extraen principios de diseño, nunca
implementación.

## Dependencies

- [`../../CLAUDE.md`](../../CLAUDE.md) — §14, identidad propia

## Related Documents

- [`cobblemon-audit.md`](cobblemon-audit.md)

## Current Status

Análisis basado en la presentación pública del modpack (CurseForge, 2026-08-11).
**Limitación honesta:** no se ha jugado en el servidor. Se analiza lo que el
producto *dice* de sí mismo, que revela su arquitectura de producto pero no su
balance económico real. Los apartados de economía y cooldowns quedan como
inferencia, marcados como tal.

## Last Decision

Ninguna. Alimenta `GD-001` y `UI-001`.

---

## 1. Qué es

| | |
|---|---|
| Tipo | Modpack **de cliente** para Fabric, MC 1.21.1 |
| Función | Compañero oficial de la red `mc.diosesmon.net` |
| Enfoque | "Built for high-end rigs", máxima fidelidad visual, cosméticos activados |

**Lo importante no es el modpack: es que sea el cliente de una red.** Diosesmon
no vende un servidor; vende una **plataforma** con cliente propio. Eso es lo que
le permite tener interfaces que ningún servidor vanilla puede ofrecer.

---

## 2. El sistema central: PokePad

Un único hub en el juego que concentra:

- GTS con filtros por especie, nivel, naturaleza, género, shiny, IV/EV, tipo Tera y precio
- Seguimiento de *hunts* y rotación de recompensas
- Reclamo de kits de inicio y de evento
- Viaje rápido entre localizaciones de la red
- Desbloqueo y equipado de prefijos cosméticos
- Moneda integrada para compras

### Por qué funciona — el principio a robar

**Un solo punto de entrada.** El jugador aprende *una* cosa: "todo está en el
PokePad". No hay 20 comandos que memorizar, ni `/gts`, `/kit`, `/warp`, `/shop`,
`/hunt` sueltos.

Esto resuelve el problema de descubribilidad, que es donde mueren los servidores
con muchos sistemas: **si el jugador no sabe que algo existe, ese sistema no
existe.** Un hub único convierte cada sistema nuevo en una entrada más de un
menú que el jugador ya visita.

> 🟢 **ADAPTAR.** Nuestro árbol de navegación (brief §25) es exactamente esto.
> El PokePad valida la idea. Nuestra versión debe tener nombre, estética e
> identidad propias — no un PokePad con otro color.

---

## 3. Tabla de sistemas

| Sistema | Qué hace | Por qué funciona | Riesgos | Veredicto |
|---|---|---|---|---|
| **PokePad (hub)** | Punto único de entrada a todo | Descubribilidad; añadir sistemas no añade complejidad percibida | Si cae, cae todo. Puede volverse un menú-cajón sin jerarquía | 🟢 **Adaptar** |
| **GTS con filtros ricos** | Filtra por 9+ atributos, incluido IV/EV y Tera | Sin filtros un mercado con miles de listados es inusable | Filtrar por IV **hace visible el valor competitivo** → dispara la inflación de los "perfectos" y hunde el resto | 🟡 **Mejorar** — filtros por progresión |
| **Hunts + rotación** | Objetivos rotativos con recompensa | Da razón para volver hoy. Rotación = contenido sin producir contenido | Se degrada a tarea diaria mecánica | 🟡 **Mejorar** — atar a exploración real |
| **Kits (inicio y evento)** | Reclamo desde el hub | Reduce fricción inicial | 🔴 **Fuente principal de inflación** si el cooldown es corto | 🔴 **Evitar el modelo** — rediseñar |
| **PokeStops con cooldown de servidor** | Puntos interactivos en el mundo | Cooldown **en servidor**, no en cliente: no se puede falsear | Degenera en ruta de farmeo: caminar → clic → premio | 🟡 **Mejorar** — que exijan gameplay |
| **Viaje rápido de red** | Teleporte entre zonas | Comodidad en un mundo grande | 🔴 **Mata la exploración**, que es nuestro core loop | 🔴 **Evitar** como está — debe ser *gated* y con coste |
| **Cosméticos (gorros, mascotas, trajes)** | 3 categorías en tiempo real | Monetización **sin ventaja competitiva**. Modelo correcto | Coste de rendimiento en cliente | 🟢 **Adaptar** |
| **Armaduras temáticas (9 sets)** | 3D animadas y emisivas | Identidad visual fuerte, muy vistoso | Si dan estadísticas, es pay-to-win | 🟢 **Adaptar** solo si son cosméticas |
| **40+ bloques decorativos, ascensores** | Construcción y ambientación | Da a los jugadores expresión propia; retención a largo plazo | Coste de mantenimiento del arte | 🟢 **Adaptar** |
| **Scoreboard con medallas y stats** | Progreso siempre visible | **Recuerda al jugador que progresa** sin que abra nada | Ruido en pantalla; hueco si no hay nada que mostrar | 🟢 **Adaptar** |
| **Tablist y menú principal de marca** | Identidad en cada pantalla | La marca se refuerza en cada sesión | — | 🟢 **Adaptar** |
| **Cinemáticas por evento del servidor** | Vídeo lanzado desde servidor | Momentos memorables, control narrativo | Interrumpe; molesta si se repite | 🟢 **Adaptar** — **ya sabemos hacerlo** (Cutscene API) |
| **Player scan** | Ver medallas y Pokédex de otro | **Convierte el progreso en estatus social.** Muy potente y muy barato | Puede humillar al novato | 🟢 **Adaptar** |
| **Party overlay, daycare badges, indicador PvP** | Estado siempre a la vista | Reduce consultas a menús | Saturación de HUD | 🟢 **Adaptar** con moderación |
| **Cambio de servidor sin costuras** | Red multi-servidor | Escala horizontal; separa lobby/juego/PvP | Complejidad de infraestructura y de sincronía de datos | ⚪ **Aplazar** — no lo necesitamos aún |
| **Quests atadas a objetivos Cobblemon** | Gestionadas por servidor | Reutiliza eventos que el mod ya emite | Genéricas: "captura 10 X" | 🟡 **Mejorar** — narrativa, que es nuestra fuerza |
| **Moneda de red transversal** | Una moneda para todo | Simple de entender, un solo balance | Una sola moneda mezcla economías con velocidades distintas | 🟡 **Analizar** en `ECO-001` |
| **Prefijos cosméticos** | Desbloqueables y equipables | Estatus barato de producir | — | 🟢 **Adaptar** |
| **"Built for high-end rigs"** | Prioriza fidelidad visual | Coherente con su público | 🔴 **Excluye jugadores.** Un MMORPG necesita masa crítica | 🔴 **Evitar** |

---

## 4. Los tres principios que sí valen

**1 · Un punto de entrada, no veinte comandos.**
La complejidad se esconde detrás de una jerarquía, no se reparte por el chat.

**2 · El progreso tiene que verse sin pedirlo.**
Scoreboard, tablist y player scan hacen visible el avance de forma pasiva. Es la
diferencia entre progresar y *sentir* que progresas — y cuesta muy poco.

**3 · Monetizar identidad, nunca poder.**
Cosméticos, trajes, prefijos, mascotas. Ninguna estadística. Es el modelo
correcto y coincide con nuestro principio P4.

---

## 5. Los tres riesgos que debemos evitar

**1 · El hub como cajón desastre.**
Un menú con 30 botones sin jerarquía es tan malo como 30 comandos. Nuestro árbol
necesita **profundidad y estados de bloqueo explicados** (brief §27), no una
rejilla plana.

**2 · Comodidad que devora el core loop.**
Viaje rápido barato + PokeStops + kits frecuentes = el jugador deja de explorar.
Si nuestro core loop es la exploración, **cada sistema de comodidad es un
impuesto sobre él**. Deben tener coste, cooldown y estar *gated* por progresión.

**3 · Filtros de GTS que colapsan el mercado.**
Filtrar por IV convierte un mercado de Pokémon en un mercado de estadísticas:
solo se venden los perfectos, el resto vale cero. Se mitiga con acceso
progresivo a los filtros (los avanzados como desbloqueo) y con comisiones e
impuestos que penalicen el flipping.

---

## 6. Dónde nos diferenciamos

Diosesmon es **fidelidad visual y comodidad**. Cuatro huecos reales:

| Hueco | Nuestra ventaja |
|---|---|
| Narrativa | Tenemos 57 voces grabadas y un modelo de clonado. Diosesmon no tiene historia |
| Pokémon exclusivos | `addon-luna` demuestra que sabemos crear especies propias |
| Accesibilidad | Ellos exigen equipo potente; nosotros podemos no hacerlo |
| Progresión con sentido | Su progresión es acumulación; la nuestra puede ser descubrimiento |

> El brief pregunta: *"¿podría este sistema pertenecer a cualquier servidor
> Cobblemon?"*. Aplicado a Diosesmon, la respuesta a casi todo es **sí** —
> excepto a su presentación. **Ahí está el hueco: nosotros podemos tener
> mundo y relato, no solo interfaz.**

---

## Next Actions

1. `GD-001` — usar los 3 principios y los 3 riesgos como restricciones de diseño
2. `UI-001` — diseñar el hub con jerarquía real y estados de bloqueo
3. Si es posible, entrar a `mc.diosesmon.net` y medir su economía real: precios,
   cooldowns de kits, comisiones de GTS. Convertiría inferencia en dato.

## Related Systems

- [Auditoría de Cobblemon](cobblemon-audit.md)
- Core loop — pendiente
- Economía — pendiente
