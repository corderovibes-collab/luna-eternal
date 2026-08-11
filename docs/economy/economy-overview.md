# Economía — modelo general

## Purpose

Definir de dónde entra el dinero, por dónde sale, a qué velocidad, y cómo se
controla la inflación. **Antes de crear ninguna tienda.**

## Dependencies

- [`../game-design/core-loop.md`](../game-design/core-loop.md) — el bucle 2 *es* la economía
- [`monetization.md`](monetization.md) — T2 y T3 se validan contra los sinks de aquí

## Related Documents

- [`../analysis/cobblemon-audit.md`](../analysis/cobblemon-audit.md) — señuelos y buckets nativos
- `../trading/gts.md` — pendiente

## Current Status

**PROPUESTA.** Anclada en datos reales medidos en el servidor de producción el
2026-08-11, no en estimaciones.

## Last Decision

Pendiente. Deriva de D-007 (F2P + pago).

---

## 1. Línea base medida en producción

Datos reales de `config/cobbledollars/` (`2a0a48ff`), útiles como punto de
partida porque ya están calibrados contra jugadores reales:

| Objeto | Compra | Venta | Ratio |
|---|---|---|---|
| Poké Ball | 400 | — | — |
| Great Ball | 750 | — | — |
| Poción | 600 | 50 | 0,08 |
| Superpoción | 900 | 175 | 0,19 |
| Hiperpoción | 1 600 | 375 | 0,23 |
| Revivir | 3 000 | 500 | 0,17 |
| Esmeralda | — | 60 | — |
| Élitros | — | 25 000 | — |
| `lumymon:soul_feather` | — | 30 000 | — |

Configuración de fuentes:
```
earnCobbleDollarsFromWildPokemon : true
earnCobbleDollarsFromNPC         : true
cobbleDollarsIncomeMultiplier    : 0.5
```

### ✅ Auditoría de arbitraje — LIMPIA

Comprobados los 25 objetos comprables contra los 80 vendibles: **ningún objeto
se puede comprar más barato de lo que se vende.** No existe ciclo de dinero
infinito. Los 4 objetos presentes en ambos lados tienen márgenes de **4× a 12×**
a favor de la casa.

**Quien configuró esto sabía lo que hacía.** Es el error más común en servidores
Cobblemon y aquí no está. Se conserva el principio: *todo lo comprable pierde
valor al revenderse, sin excepción*.

---

## 2. La decisión de moneda

**Dos monedas. Ni una, ni tres.**

### 💰 PokéDólar — moneda real, transferible

La economía propiamente dicha. Se gana jugando, se gasta en consumibles y
servicios, se intercambia entre jugadores, fluctúa.

### 🔷 Marca — vinculada al jugador, NO transferible

Se gana con logros, descubrimientos, medallas e hitos. **No se puede dar,
vender, comerciar ni depositar.** Muere con la cuenta.

#### Por qué la segunda moneda no es complicación gratuita

Resuelve un problema real que una sola moneda no puede resolver:

> **Cómo recompensar sin inflar.**

Toda recompensa en moneda transferible aumenta la masa monetaria y sube los
precios de todos. Una moneda vinculada al jugador **no puede causar inflación
porque nunca entra en el mercado**. Permite ser generoso con los logros sin
tocar el equilibrio económico.

Y da una segunda cosa: **una tienda que el dinero no puede comprar.** Los
desbloqueos de progresión, los títulos de prestigio y las funciones avanzadas
se pagan en Marcas — así el jugador rico no puede saltarse la progresión
comprándola, y el veterano tiene algo que solo él tiene.

### 🪙 ReportCoin — premium, se compra con dinero real

Decisión del usuario (2026-08-11), equivalente a los *Diosescoins* de
Diosesmon. Se adquiere fuera del juego y **solo compra identidad y comodidad**:
cosméticos, accesorios, títulos, rangos.

> **Revisión de lo que escribí antes.** Este documento decía *"no habrá una
> tercera moneda: cada moneda divide la liquidez"*. Eso vale para monedas que
> compiten dentro de la economía del juego. **ReportCoin no está en la economía
> del juego**: es un token de pago que nunca toca el mercado, así que no divide
> ninguna liquidez. La regla original se mantiene para monedas *de juego*.

### La regla que sostiene las tres: bucles cerrados

```
   💰 PokéDólar          🔷 Marca              🪙 ReportCoin
   ┌──────────┐         ┌──────────┐          ┌──────────┐
   │ se gana  │         │ se gana  │          │ se compra│
   │ jugando  │         │ logrando │          │ con € $  │
   │          │         │          │          │          │
   │ compra:  │         │ compra:  │          │ compra:  │
   │ consumo  │         │ desbloq. │          │ cosmétic.│
   │ mercado  │         │ prestigio│          │ comodidad│
   └──────────┘         └──────────┘          └──────────┘
        ✗ ────── no hay conversión ────── ✗ ────── ✗
```

**Ninguna se convierte en otra. En ninguna dirección. Nunca.**

De esa única regla se derivan las tres garantías del proyecto:

| Garantía | Porque… |
|---|---|
| **No se puede comprar poder** | ReportCoin no llega a PokéDólar ni a Marca |
| **No se puede comprar progresión** | Las Marcas solo se ganan jugando |
| **Pagar no infla la economía** | ReportCoin nunca entra en el mercado |

> El día que exista una conversión —"cambia 100 ReportCoins por 50 000
> PokéDólares"— **las tres monedas son una sola** y todo el diseño económico
> de este documento deja de tener efecto. Es la línea roja del proyecto.

### Qué puede y qué no puede comprar el ReportCoin

| ✅ Sí | ❌ No |
|---|---|
| Cosméticos, accesorios, monturas visuales | PokéDólares o Marcas |
| Títulos, prefijos, colores | Pokémon, objetos comerciables |
| Rangos (paquete T1+T2) | IVs, EVs, shinies, legendarios |
| Homes, espacio de PC, comodidad | Desbloqueos de progresión |
| Aceleración acotada (T3, con topes) | Acceso a zonas o contenido cerrado |

Clasificación completa y el test de 6 preguntas en
[monetization.md](monetization.md).

### Consecuencia técnica

`Currency` pasa a tener tres valores, y **dos atributos independientes**:

| Moneda | `tradeable` | `purchasable` |
|---|---|---|
| `POKEDOLLAR` | ✅ entre jugadores | ❌ |
| `MARK` | ❌ | ❌ |
| `REPORTCOIN` | ❌ | ✅ con dinero real |

Que `REPORTCOIN` no sea `tradeable` es lo que impide el mercado gris: si se
pudiera transferir, los jugadores lo venderían por PokéDólares y habríamos
creado la conversión que la regla prohíbe — **por la puerta de atrás y sin
darnos cuenta**.

---

## 3. Sources — por dónde entra el dinero

Ordenadas por volumen esperado:

| # | Fuente | Tipo | Escala con | Riesgo |
|---|---|---|---|---|
| 1 | **Pokémon salvajes derrotados/capturados** | 💰 | Tiempo jugado | Medio — es el grifo principal |
| 2 | **Venta al banco de objetos y drops** | 💰 | Actividad | Bajo — precios controlados por nosotros |
| 3 | **Entrenadores NPC** | 💰 | Cooldown | **Alto si no hay cooldown** |
| 4 | **Quests y misiones** | 💰 + 🔷 | Contenido finito | Bajo |
| 5 | **Logros y descubrimientos** | 🔷 | Contenido finito | Ninguno (no inflaciona) |
| 6 | **Comercio entre jugadores** | 💰 | — | **Cero: redistribuye, no crea** |

### El principio del grifo único

**La fuente 1 debe ser la dominante y las demás, marginales.** Si el dinero
principal viene de misiones diarias o de NPCs con cooldown, el jugador optimiza
*hacia el cooldown* y deja de jugar: entra, reclama, se va. Es exactamente lo
que el brief describe como el problema del servidor actual.

Si el dinero viene de jugar, la optimización *es* jugar.

> `cobbleDollarsIncomeMultiplier: 0.5` en producción indica que ya hubo que
> frenar el grifo. Buena señal de que se vigila; conviene medirlo de nuevo con
> el diseño nuevo.

---

## 4. Sinks — por dónde sale

El apartado que casi nunca se diseña, y por eso casi todos los servidores se
inflan.

| # | Sink | Tipo | Frecuencia | Peso |
|---|---|---|---|---|
| 1 | **Consumibles** (balls, pociones, **señuelos**) | 💰 | Constante | 🔥🔥🔥 |
| 2 | **Impuesto y comisión del GTS** | 💰 | Por transacción | 🔥🔥🔥 |
| 3 | **Servicios**: curación, depósito, guardería | 💰 | Recurrente | 🔥🔥 |
| 4 | **Viaje rápido** | 💰 | Recurrente | 🔥🔥 |
| 5 | **Desbloqueos de progresión** | 🔷 | Una vez | 🔥🔥 |
| 6 | **Ampliaciones**: PC, homes, slots | 💰 | Una vez, con tope | 🔥 |
| 7 | **Cosméticos comprables con dinero del juego** | 💰 | Opcional | 🔥🔥 |
| 8 | **Cría y competitivo** | 💰 | Recurrente en endgame | 🔥🔥 |

### Los dos sinks que sostienen todo

**1 · Consumibles.** Escala automáticamente con la actividad: quien más juega,
más gasta. Es el único sink que se autorregula sin intervención. Los
**señuelos** (`minLureLevel`, nativo de Cobblemon) son la pieza clave: son un
gasto *opcional pero deseable*, que es la mejor clase de sink que existe.

**2 · Impuesto del GTS.** Es el único sink que **escala con la riqueza**. Los
consumibles cuestan lo mismo al novato que al millonario; el impuesto sobre una
venta de 500 000 se lleva una fracción proporcional. Sin él, el dinero se
acumula arriba y no vuelve a salir nunca.

> Propuesta: **comisión de publicación fija + impuesto porcentual progresivo**
> sobre el precio de venta. La comisión fija desincentiva inundar el mercado de
> listados basura; el impuesto progresivo drena a los ricos.

### Sink de último recurso

Para el endgame hace falta algo en lo que quemar fortunas: **cosméticos caros
comprables con dinero del juego**. Un jugador con 10 millones necesita un
destino, o el dinero se queda parado inflando el mercado.

Y ojo: estos cosméticos son de moneda del **juego**, no de pago. Coexisten con
los de T1 de [monetization.md](monetization.md) — distintos catálogos, distinta
estética, no compiten.

---

## 5. Velocity — cuánto dinero por hora

No se fija por intuición. Se ancla al **bucle 2** (la expedición):

```
COSTE de una expedición   = balls + pociones + señuelos + viaje
INGRESO de una expedición = dinero de Pokémon + ventas al banco
MARGEN                    = ingreso / coste
```

### Objetivo de diseño: margen ≈ **1,5× – 2,0×**

| Margen | Consecuencia |
|---|---|
| < 1,0 | El jugador pierde dinero jugando. Abandona |
| 1,0 – 1,3 | Demasiado justo; no hay ahorro ni progresión económica |
| **1,5 – 2,0** | **Objetivo.** Progreso real, sin acumulación explosiva |
| 3,0+ | Inflación en semanas |
| > 5,0 | Economía muerta al mes |

**El margen debe bajar con la habilidad, no subir.** Un veterano eficiente
tendrá mejor margen; por eso los sinks de endgame (cría, GTS, cosméticos caros)
deben crecer más rápido que su ingreso.

### La regla que protege la economía entera

> **El dinero no es la recompensa. El Pokémon lo es.**

Una expedición debe rendir poco dinero y mucho **valor no fungible**: los
Pokémon capturados. Eso significa que la riqueza se almacena en criaturas
—únicas, con IVs, naturaleza e historia— y no en un número de banco.

Consecuencias, todas buenas:
- La masa monetaria crece despacio → poca inflación estructural
- El GTS se vuelve central: es donde vive la riqueza real
- Los jugadores se diferencian por *lo que tienen*, no por *cuánto tienen*
- Un duplicado de dinero hace menos daño que en una economía monetaria

---

## 6. Wealth tiers

Medidos en **expediciones de reserva** (cuántas salidas puede financiar sin
ingresar nada), no en cifras absolutas — así la escala sobrevive a los ajustes.

| Nivel | Reserva | Puede permitirse | Sink que le toca |
|---|---|---|---|
| **Nuevo** | < 1 | Nada; depende del tutorial | Ninguno |
| **Precario** | 1 – 3 | Balls básicas | Consumibles |
| **Estable** | 4 – 10 | Señuelos, buenas balls | Consumibles + servicios |
| **Cómodo** | 10 – 30 | Comprar en GTS | + impuestos |
| **Rico** | 30 – 100 | Especular en el mercado | + cría, cosméticos |
| **Endgame** | 100+ | Lo que quiera | **Sinks de prestigio** |

**El diseño falla si alguien llega a "Rico" en la primera semana.** Es el
criterio de aceptación de la calibración.

---

## 7. Control de inflación

| Defensa | Cómo actúa |
|---|---|
| **Riqueza en Pokémon, no en dinero** | Estructural. La más importante de todas |
| **Impuesto progresivo del GTS** | Drena proporcionalmente a los ricos |
| **Sin conversión Marca ↔ PokéDólar** | Aísla las recompensas de la masa monetaria |
| **Sin venta de moneda por dinero real** | Regla dura de `monetization.md` |
| **Precios del banco fijados por nosotros** | Techo de valor de cada objeto |
| **Sinks de endgame** | Destino para las grandes fortunas |
| **Telemetría** | Sin medir, no hay control |

### Lo que hay que medir desde el primer día

```
masa monetaria total          suma de todos los balances
dinero creado / destruido     por día y por fuente
mediana y percentiles         P50, P90, P99 de riqueza
índice de precios del GTS     cesta fija de objetos
margen real de expedición     medido, no supuesto
```

> **Sin telemetría, cualquier ajuste económico es adivinar.** `Ledger` ya está
> instalado en producción y registra acciones: es un punto de partida, pero
> hará falta un panel propio. Se diseña en `ECO-003`.

---

## 8. Anti-abuso

Cada vector, con su defensa:

| Vector | Defensa |
|---|---|
| Arbitraje comprar/vender | **Ya resuelto**: nada se vende por más de lo que cuesta. Mantener como invariante y **comprobarlo automáticamente en cada cambio de precios** |
| Multicuenta para transferir riqueza | ⚠️ **Agravado por `online-mode=false`** — ver §9 |
| Farmeo de NPCs | Cooldown por NPC y por jugador |
| Manipulación de mercado | Historial de precios público; límites de listados por jugador |
| Duplicación de objetos | Auditoría con Ledger; alertas por saltos anómalos de balance |
| Abuso de cooldown | Estado en servidor, nunca en cliente |
| Bots de captura | Detección por patrón; el ciclo lunar y los señuelos complican el bot |

---

## 9. ⚠️ El problema que condiciona todo lo anterior

**Producción corre con `online-mode=false`.** Verificado el 2026-08-11 por dos
vías independientes:

```
server.properties : online-mode=false
UUIDs de los ops  : versión 3 (generados localmente, no por Mojang)
                    A1ejandroreport · TheJuanCE · altf44rt
```

Para una economía persistente esto es grave, porque **la identidad es la clave
primaria de todo**: dinero, Pokémon, progresión, listados de GTS.

| Consecuencia | Efecto |
|---|---|
| Multicuenta gratis e ilimitada | Todo límite *por jugador* deja de existir |
| Transferencia de riqueza trivial | Alts que farmean y donan al principal |
| Ban evasion | La sanción no significa nada |
| Suplantación | Un nombre no prueba identidad |

`easyauth` está instalado y mitiga el acceso (exige contraseña), pero **no
resuelve la multicuenta**: cualquiera puede registrar cuentas nuevas
indefinidamente.

### ⛔ `online-mode=true` NO es una opción — verificado 2026-08-11

Recomendé pasar a `online-mode=true`. **Ese consejo era inviable y queda
retirado.** La comprobación:

```
api.mojang.com/users/profiles/minecraft/TheJuanCE
  → "Couldn't find any profile with name TheJuanCE"
```

El propietario del servidor **no tiene cuenta premium de Mojang**. Con
`online-mode=true` no podría entrar en su propio servidor, y es razonable
suponer que buena parte de la comunidad está igual.

> Confirmación adicional: el UUID offline calculado para `TheJuanCE`
> (`432ef323-8ac3-3ba3-8175-aaf88c5589cf`) coincide **exactamente** con el que
> tiene en producción. Los UUID offline son deterministas a partir del nombre.

### Entonces: offline, con mitigaciones obligatorias

El anti-abuso no puede apoyarse en la identidad de Mojang. Hay que construirlo:

| Mitigación | Coste | Efecto |
|---|---|---|
| **Carencia**: una cuenta nueva no opera en el GTS hasta X horas de juego real | Bajo | **La más rentable.** No molesta a nadie legítimo y encarece crear alts |
| Límites por IP para operaciones económicas | Bajo | Imperfecto, pero no trivial de saltar |
| Vinculación a una cuenta externa (Discord) para vender | Medio | Da una identidad real reutilizable |
| Detección de patrones alt→principal en el libro de asientos | Medio | `ledger_entry` ya guarda todo lo necesario |

**La carencia se implementa desde el primer día.** El resto se prioriza con
datos reales.

> Y una nota de diseño que ahora vale doble: **la riqueza vive en Pokémon, no
> en dinero** (§5). Con multicuenta imposible de erradicar, que el valor esté
> en objetos únicos y rastreables —en vez de en un número fungible— limita
> mucho el daño que puede hacer una granja de alts.

Es la decisión **B-004 / `SEC-006`**, ahora con datos. La pregunta ya no es
*"¿online o offline?"* sino *"¿cuánta mitigación construimos?"*.

---

## Next Actions

1. Decidir `B-004` (`online-mode`) — condiciona el anti-abuso entero
2. Aprobar el modelo de dos monedas
3. `ECO-003` — telemetría económica: qué se mide y cómo
4. `TRD-001` — diseñar el GTS con sus comisiones (es el sink clave)
5. Calibrar el margen de expedición con números concretos

## Related Systems

- [Core loop](../game-design/core-loop.md) · [Monetización](monetization.md)
- [Auditoría de Cobblemon](../analysis/cobblemon-audit.md)
