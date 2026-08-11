# GTS — Mercado global

## Purpose

Diseñar el mercado de jugadores: el sistema que hace circular la riqueza y que
sostiene el **único sink que escala con la fortuna**.

## Dependencies

- [`../economy/economy-overview.md`](../economy/economy-overview.md)
- [`../technical/data-model.md`](../technical/data-model.md) — custodia y transacción

## Related Documents

- [`../analysis/diosesmon-analysis.md`](../analysis/diosesmon-analysis.md) — riesgo de los filtros
- [`../game-design/core-loop.md`](../game-design/core-loop.md) — el bucle 3 crea la demanda

## Current Status

**PROPUESTA.** Esquema y flujo transaccional ya resueltos en `data-model.md`.
Aquí se define el diseño de producto: acceso, comisiones, filtros y defensas.

## Last Decision

Pendiente. Deriva de D-007 y D-009.

---

## 1. Qué es el GTS en este proyecto

En la mayoría de servidores el GTS es una comodidad. Aquí es **infraestructura
económica**, y cumple tres funciones que ningún otro sistema cubre:

| Función | Por qué importa |
|---|---|
| **Drena riqueza** | El impuesto es el único sink proporcional a lo que tienes |
| **Da salida a la especialización** | El bucle 3 crea jugadores distintos; sin mercado, esa diferencia no vale nada |
| **Almacena el valor real** | `ECO-001`: la riqueza está en Pokémon, no en dinero. El GTS es donde eso se hace líquido |

> Si el GTS falla, la economía no se degrada: **deja de existir**, porque la
> riqueza queda inmovilizada en cajas de PC.

---

## 2. El acceso es progresión

El brief §20 lo pide explícitamente: no todas las funciones desde el minuto uno.
Y hay una razón de diseño, no solo de ritmo.

| Nivel | Se desbloquea con | Funciones |
|---|---|---|
| **G0** | Al entrar | **Consultar.** Ver qué existe y a qué precio |
| **G1** | Primeras capturas | **Comprar** |
| **G2** | Hito de progresión | **Vender** — 3 listados simultáneos |
| **G3** | Vía comerciante | **Filtros avanzados** (IV, EV, habilidad, Tera) · 10 listados |
| **G4** | Reputación de comerciante | **Historial de precios** · 25 listados · alertas |

### Por qué G0 empieza en "consultar" y no en "nada"

Un jugador nuevo que ve el mercado **aprende qué es valioso** antes de poder
participar. El GTS se convierte en el mejor tutorial de economía del servidor:
sin leer nada, entiende que un shiny vale mucho y un Rattata no.

### Por qué el filtro por IV está en G3

Es la corrección al riesgo detectado en Diosesmon
([diosesmon-analysis.md §5](../analysis/diosesmon-analysis.md)):

> Filtrar por IV convierte un mercado de Pokémon en un mercado de estadísticas.
> Solo se venden los perfectos y el resto vale cero.

Retrasarlo tiene un efecto concreto: durante toda la fase inicial, los Pokémon
se compran **por lo que son** —especie, aspecto, rareza, historia— y no por una
cifra. Cuando el filtro llega, el jugador ya sabe valorar de otra forma.

Y como bonus: el filtro avanzado es una recompensa deseable de la vía
comerciante, que necesitaba tener algo propio.

---

## 3. Comisiones — el diseño del sink

Dos cobros distintos, cada uno resolviendo un problema diferente.

### 3.1 · Tasa de publicación — **1 % del precio pedido, por adelantado, no reembolsable**

Se paga al listar, se pierda o no la venta.

**Por qué porcentual y no fija.** Una tasa fija excluye los Pokémon baratos y
seca la parte baja del mercado, que es donde compran los nuevos. Una tasa
porcentual escala sola.

**Qué previene, y es lo importante:** listar un Ratata a 50 millones para
manipular la percepción de precios **cuesta 500 000 al instante**. El anclaje de
precios —la manipulación más común y más barata en cualquier GTS— pasa a ser
caro. La defensa está en el precio de la tasa, no en detectar al manipulador.

### 3.2 · Impuesto de venta — **progresivo, sobre el precio final**

Lo paga el vendedor al completarse la venta.

| Tramo del precio | Impuesto |
|---|---|
| hasta 10 000 | 5 % |
| 10 001 – 100 000 | 8 % |
| 100 001 – 1 000 000 | 12 % |
| más de 1 000 000 | 18 % |

> ⚠️ **Cifras de partida, no calibradas.** Los tramos dependen de la velocidad
> económica real (`ECO-001` §5), que aún no está medida. Lo que sí es firme es
> la **forma**: progresiva sobre el precio.

**Por qué progresivo.** Es el único mecanismo del diseño que grava más al rico
que al pobre. Los consumibles cuestan igual a todos; el impuesto no. Sin
progresividad, el dinero se acumula arriba y no vuelve a salir nunca.

**Por qué lo paga el vendedor.** Se descuenta de lo que recibe, así el comprador
ve un precio limpio. Y el vendedor, al fijar el precio, ya lo internaliza.

### 3.3 · A dónde va el dinero

**Se destruye.** No va a un fondo, ni a un banco del servidor, ni se
redistribuye. Un sink que reaparece en otro sitio no es un sink.

En el esquema queda como un asiento a la cuenta del sistema
(`ledger_entry.reason = 'gts_tax'`), lo que además da la métrica de cuánta
riqueza se está drenando por día.

---

## 4. Anti-abuso

| Vector | Defensa |
|---|---|
| **Duplicación del Pokémon** | Custodia: al listar sale del PC del jugador. Transacción única con `FOR UPDATE`. Ya resuelto en [`data-model.md`](../technical/data-model.md) §3 |
| **Doble compra del mismo listado** | `SELECT ... FOR UPDATE` + estado `RESERVED` |
| **Doble clic / reintento** | Clave de idempotencia (R4) |
| **Anclaje de precios** | La tasa del 1 % lo hace caro |
| **Spam de listados** | Límite de listados por nivel (3 / 10 / 25) |
| **Estafa** | El GTS es intermediado: no hay confianza que romper |
| **Comercio directo entre jugadores** | Doble confirmación + **si el contenido cambia, ambas confirmaciones se reinician** |
| **Manipulación del historial** | Mediana, no media. Descartar percentiles extremos. Volumen mínimo para publicar precio |
| **Alts y lavado de operaciones** | ⚠️ **Sin defensa real mientras `online-mode=false`** — ver §5 |
| **Venta por dinero real (RMT)** | Difícil de impedir. El impuesto progresivo la encarece; el historial la hace visible |

### Comercio directo: la trampa del último segundo

El GTS no necesita anti-estafa porque nadie confía en nadie: el sistema es el
intermediario. **El comercio directo sí.** La estafa clásica es cambiar el
contenido en el instante entre que el otro confirma y se ejecuta.

> Regla: **cualquier modificación del contenido invalida las dos
> confirmaciones.** Sin excepciones, sin ventanas de gracia.

---

## 5. El límite honesto de este diseño

Con **`online-mode=false`**, dos defensas de la tabla anterior son decorativas:

- **Límite de listados por jugador** → se sortea con una cuenta más
- **Lavado de operaciones** → un alt compra al principal a precio inflado

No hay solución técnica dentro del GTS. Las mitigaciones posibles viven fuera:

1. `online-mode=true` — la única defensa real (`SEC-006`)
2. Restringir el GTS a **cuentas verificadas**
3. **Carencia**: una cuenta nueva no puede vender hasta X horas de juego real
4. Límites por IP, imperfectos pero no triviales de saltar

**Recomendación:** aunque se mantenga el modo offline, implementar la carencia
(punto 3) desde el primer día. Es barata, no molesta a los jugadores legítimos
—que ya llevan horas dentro— y convierte crear un alt en algo con coste.

---

## 6. UX y estados

Cada listado y cada acción tiene estado explícito, siguiendo el brief §27:

```
Listado    ACTIVE · RESERVED · SOLD · CANCELLED · EXPIRED
Acceso     LOCKED (explica qué falta) · AVAILABLE · COOLDOWN · DISABLED
```

**La regla de la UI:** un botón bloqueado nunca falla en silencio ni suelta un
error. Dice **qué es**, **por qué está bloqueado** y **qué hace falta**:

```
┌─ VENDER ─────────────────────── 🔒 ─┐
│ Bloqueado                            │
│ Necesitas: Reputación Comerciante I  │
│ Progreso:  12 / 25 compras           │
│ [ Ver cómo conseguirlo ]             │
└──────────────────────────────────────┘
```

**Confirmación de compra** — mostrando siempre lo que el jugador se lleva y lo
que paga, sin letra pequeña:

```
Comprar Gardevoir Lv.50 ♀
IVs 31/28/31/30/29/31   Naturaleza: Recatada
─────────────────────────────
Pagas:            85 000
Tu saldo después: 42 300
[ Cancelar ]  [ Confirmar ]
```

**Al listar**, el vendedor ve el desglose completo antes de confirmar — nunca
descubre las comisiones después:

```
Precio pedido:        100 000
Tasa de publicación:   -1 000   (ahora, no reembolsable)
Impuesto si se vende:  -8 000   (8 %)
─────────────────────────────
Recibirías:            91 000
```

---

## 7. Historial de precios

Disponible en G4. No es un lujo: es la defensa contra la manipulación y la
herramienta que hace real el rol de comerciante.

- **Mediana**, nunca media — una venta de 10 millones no debe mover el índice
- **Volumen mínimo** antes de publicar precio de una especie
- Ventanas de 7 y 30 días
- Segmentado por atributos relevantes (shiny sí/no, rangos de IV)

Y alimenta la telemetría de `ECO-001` §7: el índice de precios sobre una cesta
fija es el termómetro de la inflación del servidor.

---

## 8. Lo que NO va a haber

Decisiones tomadas por omisión deliberada:

| | Motivo |
|---|---|
| **Casa de subastas** | Duplica el GTS y parte la liquidez. Si el mercado madura y hace falta, se reconsidera |
| **Tiendas de jugador** | Mismo problema, y añade superficie de estafa |
| **Comercio de Marcas** | Regla dura de `ECO-001`: la moneda vinculada no circula |
| **Compra instantánea sin confirmar** | Un clic accidental de 500 000 genera un ticket de soporte |
| **Listados sin caducidad** | Un mercado con listados eternos se llena de precios de hace un año |

---

## Next Actions

1. Calibrar los tramos del impuesto con la velocidad real (`ECO-001` §5)
2. `ECO-003` — telemetría: índice de precios y drenaje diario
3. `UI-001` — el GTS dentro del árbol de navegación
4. Decidir `SEC-006`, que condiciona §5

## Related Systems

- [Economía](../economy/economy-overview.md) · [Modelo de datos](../technical/data-model.md)
- [Core loop](../game-design/core-loop.md) · [Diosesmon](../analysis/diosesmon-analysis.md)
