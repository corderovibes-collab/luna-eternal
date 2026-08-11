# Visión — PokeReport: Luna Eternal

## Purpose

Definir la promesa al jugador: qué experiencia vendemos, para quién, y qué
decisiones de diseño quedan cerradas por ella.

## Dependencies

- [`../analysis/diosesmon-analysis.md`](../analysis/diosesmon-analysis.md)
- [`../analysis/cobblemon-audit.md`](../analysis/cobblemon-audit.md)

## Related Documents

- [`core-loop.md`](core-loop.md)

## Current Status

**PROPUESTA.** Requiere aprobación del usuario antes de que nada dependa de
ella.

## Last Decision

Pendiente.

---

## 1. La promesa

> **Un mundo que solo se deja conocer por partes, y que cambia mientras lo
> conoces.**

El jugador de Luna Eternal no colecciona Pokémon: **descubre un mundo que
esconde Pokémon**. La diferencia no es retórica y decide toda la arquitectura.

| Coleccionar | Descubrir |
|---|---|
| El objetivo es la lista | El objetivo es el sitio |
| Se agota (1 025 y ya está) | No se agota: el sitio cambia |
| Recompensa acumular | Recompensa saber |
| El wiki lo resuelve | El wiki no sabe cuándo |

---

## 2. La frase que resume el proyecto

> *"Todavía me queda muchísimo por descubrir."*

Es el criterio de aceptación del brief y se toma literalmente: **cualquier
sistema que reduzca esa sensación se rechaza**, por cómodo que sea.

Un jugador con 200 horas debe seguir encontrando cosas que no sabía. No porque
le falte grindear, sino porque **el conocimiento del mundo es en sí mismo la
progresión**.

---

## 3. Los cuatro pilares

### 3.1 · El mundo tiene horarios

El mundo no es un decorado estático con Pokémon encima. **Cambia**, y ese cambio
altera qué aparece, dónde y qué es accesible. El conocimiento de *cuándo* es en
sí mismo progresión.

> ✅ **VERIFICADO (2026-08-11, `PKM-001`).** Extraído del jar de Cobblemon
> 1.7.3: `moonPhase` existe como condición de aparición, admite fase única
> (`0`) o lista (`"1,2,3"`), funciona en `anticondition` y se combina con
> bioma, hora, luz y estructura. Cobblemon ya lo usa en 9 especies.
> Junto a `timeRange`, `isRaining`, `structures`, `minY/maxY`,
> `neededNearbyBlocks` y **un sistema de señuelos nativo** (`minLureLevel`),
> **el motor de este pilar ya existe y se maneja por datapack, sin Java.**

#### La corrección que impone el dato

El plan original asumía que el ciclo lunar daría *"una razón para volver un día
concreto"*. **Es falso:** un día de Minecraft dura 20 minutos reales y hay 8
fases, así que **el ciclo lunar completo son 2 h 40 min**, no una semana.

No es un ritmo semanal: es un **ritmo de sesión**. Un jugador de tarde ve pasar
el ciclo entero. Eso cambia para qué sirve:

| | Sirve para | NO sirve para |
|---|---|---|
| **Ciclo lunar** (2 h 40) | Textura y variedad dentro de la sesión; que el mismo sitio no se sienta igual dos veces; recompensar al que sabe esperar 20 minutos | Eventos semanales; "vuelve el jueves" |

Para el ritmo largo hace falta **un calendario propio en el mod de servidor**,
desacoplado de la luna de Minecraft. Alargar el día del juego **no** es la
solución: convertiría las noches en 80 minutos y rompería todo lo demás.

Así que el pilar se divide en dos capas:

```
Ritmo corto  ·  fase lunar + hora + clima     datapack, ya existe, gratis
Ritmo largo  ·  calendario del servidor        mod propio (D-005)
```

Es un mejor diseño que el original, y lo sabemos **antes** de construir nada.

### 3.2 · Salir tiene coste, volver tiene sentido

Las expediciones consumen recursos. Eso convierte cada captura en una decisión
—*¿gasto una ball buena en esto?*— en lugar de un acto reflejo.

Y crea el otro medio del bucle: **volver al pueblo**. Ahí viven la economía, el
comercio, los demás jugadores y el almacenamiento. Un MMORPG sin razón para
volver a un sitio común no tiene comunidad, tiene gente jugando en paralelo.

### 3.3 · El progreso se ve

Medallas, Pokédex, reputación y descubrimientos son **visibles para los demás**.
Es el principio robado a Diosesmon (*player scan*): convierte el progreso en
estatus social, y es de lo más barato que se puede construir.

### 3.4 · Pagar compra identidad, nunca poder

Cosméticos, títulos, efectos, mascotas, personalización. **Cero estadísticas,
cero acceso, cero atajos de progresión.** Principio P4, sin excepciones.

---

## 4. Para quién

| Perfil | Qué le damos |
|---|---|
| **Explorador** | Público principal. El mundo cambia y premia saber |
| **Coleccionista** | Pokédex con recompensa real; especies exclusivas |
| **Comerciante** | Mercado con fluctuación real derivada del ciclo lunar |
| **Competitivo** | PvP y contenido de grupo en endgame |
| **Social** | Clanes, pueblo común, estatus visible |

**No es para:** quien quiere todo desbloqueado el primer día. Es una decisión
consciente, y hay que asumir que se traduce en jugadores perdidos en la primera
hora. Lo mitiga el diseño del onboarding, no el regalar cosas.

---

## 5. Lo que esta visión cierra

Decisiones que ya no están en discusión si se aprueba:

| ✅ Sí | ❌ No |
|---|---|
| Consumibles con coste real | Kits que resuelven la partida |
| Viaje rápido *gated* y con coste | Teleporte libre desde el minuto 1 |
| Zonas con requisito de acceso | Mundo entero abierto al entrar |
| Cosméticos de pago | Ventajas de pago |
| Rarezas que dependen del ciclo | Todo disponible siempre |
| Pokémon exclusivos del servidor | Solo los 1 025 estándar |

---

## 6. El riesgo que asumimos

**Es un diseño exigente y puede espantar a jugadores acostumbrados a servidores
generosos.**

No se mitiga suavizando la progresión —eso destruiría la propuesta— sino
asegurando que **la primera hora sea excelente**: que el jugador entienda pronto
que el mundo tiene secretos, y que descubra uno el primer día.

La progresión es lenta. **El descubrimiento no.**

---

## Next Actions

1. Aprobación del usuario (o corrección de rumbo)
2. Verificar el soporte de fase lunar en spawns de Cobblemon
3. `GD-002` — el core loop derivado de esta visión

## Related Systems

- [Core loop](core-loop.md)
- Economía · Progresión · Mundo — pendientes
