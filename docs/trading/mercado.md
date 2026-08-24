# El Mercado

## Purpose

La economía de jugadores del servidor: **libro de órdenes** para objetos,
**mercado de ejemplares** para Pokémon, y un **índice de precios** que mide la
inflación de verdad en vez de suponerla.

Este documento es el **diseño**. Se escribe entero antes de la primera línea de
código, porque la mitad de las decisiones de aquí no se pueden cambiar después
sin migrar datos de dinero.

## Dependencies

- [CLAUDE.md](../../CLAUDE.md) — P1, P2, P3, P6, D-014
- [gts.md](gts.md) — lo que ya existe, y que **no se tira**
- [economy-overview.md](../economy/economy-overview.md) — las tres monedas, los sinks
- [data-model.md](../technical/data-model.md) — R2, R3, R4
- [clanes.md](../social/clanes.md) — el patrón de `afectados()`, que aquí es obligatorio

## Current Status

**Diseño. Fase 1 en implementación.**

## Last Decision

**D-041 — Dos mercados, no uno.** Ver §2.

## Next Actions

Las fases, en §8.

---

## 1. Qué pidió el usuario

> *«economía súper avanzada estilo Albion… comercio de compra y venta de
> Pokémon o items donde todo tiene inflación… poder crear ofertas de venta y de
> compra bien estructuradas… el panel de las ofertas… que todo conecte con
> todo… así vamos a destacar en el servidor»*

Tres cosas concretas, y ninguna la tiene el GTS actual:

| | Hoy | Lo que hace falta |
|---|---|---|
| Vender | ✅ un anuncio a precio fijo | ✅ y además **órdenes con cantidad y llenado parcial** |
| Comprar | ❌ solo puedes aceptar lo que hay | **Órdenes de compra**: «pago 500 por cada Poké Ball» |
| Precio | ❌ nadie sabe cuánto vale nada | **Libro visible + histórico + índice** |

**La que cambia el juego es la segunda.** Sin órdenes de compra, un mercado es
un escaparate: el vendedor pone un precio y espera. Con órdenes de compra hay
**dos lados empujando**, y de ahí sale un precio de verdad — que es lo que hace
que Albion se sienta un mercado y no una tienda.

---

## 2. ⚠⚠ La decisión que lo estructura todo: DOS mercados

**Un libro de órdenes solo funciona con cosas intercambiables.**

Una orden de compra dice «pago 500 por *una unidad de X*». Eso tiene sentido si
todas las X son iguales:

```
100 Poké Balls a 400   ->  una Poké Ball es una Poké Ball.  FUNGIBLE
un Charizard nivel 78  ->  ¿con qué IVs? ¿naturaleza? ¿shiny?  NO FUNGIBLE
```

Una orden de compra de «un Charizard» es **una orden sin sentido**: el
comprador acabaría recibiendo el peor Charizard del servidor, porque es el que
cualquier vendedor racional le entregaría. Y ese no es un fallo de
implementación que se pueda parchear: es que la pregunta está mal hecha.

> **D-041 — El mercado se parte en dos, y cada mitad usa el mecanismo que le
> corresponde:**
>
> | | Mecanismo | Por qué |
> |---|---|---|
> | **Objetos** | **Libro de órdenes** (compra + venta, llenado parcial) | Son fungibles: 64 Poké Balls son 64 unidades idénticas |
> | **Pokémon** | **Listado por ejemplar** (lo que ya existe) | Cada uno es único; se mira *ese* y se compra *ese* |

**Y por eso el GTS actual no se tira: es exactamente la mitad de Pokémon, ya
hecha y ya probada.** Tiene custodia, impuesto progresivo, entrega diferida y
las columnas desnormalizadas para filtrar por especie, nivel, shiny e IVs. Lo
que se le añade es la pantalla y los filtros.

> ⚠ Meter Pokémon en el libro de órdenes «porque queda más completo» sería el
> error clásico de copiar la forma de Albion sin copiar el motivo. Albion tiene
> libro porque **sus objetos son fungibles**; los que no lo son (equipo con
> encantamiento) van por otra vía.

---

## 3. El libro de órdenes

### 3.1 Las dos caras

```
        VENTA (quien ofrece)                 COMPRA (quien puja)
    ┌──────────────────────────┐      ┌──────────────────────────┐
    │  620  x 12   Ana         │      │  580  x 40   Beto        │
    │  600  x 64   Carla       │      │  550  x 100  Dani        │
    │  595  x  8   Elena  ◄────┼──────┼──►  590  x 5   Fran      │
    └──────────────────────────┘      └──────────────────────────┘
       la más barata primero            la más cara primero
```

**Cuando las dos mejores se cruzan, hay trato.** Elena vende a 595 y Fran paga
590: no se cruzan, no pasa nada. Si Fran subiera a 595, se ejecuta.

### 3.2 Prioridad: precio, y luego tiempo

Se empareja **primero por precio** y, a igual precio, **primero el que llegó
antes**.

> ⚠ La prioridad por tiempo no es un adorno de realismo: es lo único que impide
> que poner una orden sea una lotería. Sin ella, dos órdenes idénticas se
> resuelven por el orden en que la base decida devolverlas — o sea, al azar — y
> el jugador que lleva tres días esperando ve cómo le adelanta uno que acaba de
> llegar.

### 3.3 El precio de ejecución es el de LA ORDEN QUE YA ESTABA

Si Fran pone una compra a 700 y en el libro hay una venta a 595, se ejecuta a
**595**. Fran paga menos de lo que ofrecía, y la diferencia se le devuelve.

> ⚠⚠ **Esto hay que hacerlo bien o el mercado es una trampa.** Si se ejecutara
> al precio del que llega, poner una orden generosa te costaría exactamente lo
> que ofreciste aunque hubiera oferta barata — y entonces nadie pondría órdenes
> por encima del mínimo, que es justo lo que mata la liquidez.
>
> El que llega **acepta el precio del libro**. Es como funciona cualquier
> mercado real, y es lo que hace seguro poner una orden agresiva.

### 3.4 Llenado parcial

Una venta de 64 puede llenarse con una compra de 10. Quedan 54 vivas.

```
qty_total    lo que se pidió
qty_filled   lo que ya se ejecutó
             quedan = total - filled;  cuando llega a 0, la orden se cierra
```

> ⚠ `qty_filled` y no `qty_restante`: la cantidad original **no se toca nunca**.
> Con `qty_restante` decreciente se pierde el dato de cuánto se pidió, y sin él
> el histórico no puede decir «se pidieron 500 y solo se sirvieron 30», que es
> justo lo que dice si un precio es realista.

---

## 4. ⚠⚠⚠ La custodia es DOBLE, y es el invariante que sostiene todo

El GTS actual ya tiene media lección aprendida: *lo listado sale del inventario
y vive en la tabla*. En un libro de órdenes eso hay que hacerlo **por los dos
lados**:

| Orden | Qué se retiene | Si no se retuviera |
|---|---|---|
| **VENTA** | los **objetos** | vendes 64 Poké Balls, las gastas, y cuando alguien compra no hay nada que entregar |
| **COMPRA** | el **dinero** | pones una compra de 1.000.000, te lo gastas, y cuando alguien vende no hay con qué pagar |

**La de compra es la que se olvida**, y es la que crea dinero de la nada: el
vendedor tiene que cobrar sí o sí —ya entregó— así que el servidor acaba
pagando lo que el comprador no tenía.

> **Regla: una orden no existe hasta que su contrapartida está retenida.** Se
> cobra o se retira en la **misma transacción** que crea la fila (R3), con clave
> de idempotencia (R4).

Al cancelar o caducar, se devuelve lo no ejecutado. **Siempre**, y por el mismo
camino de entrega diferida que ya existe (`GtsDelivery`), porque el dueño puede
estar desconectado.

---

## 5. Adelantarse a los fallos

Esto es la pregunta 8 de P2 —*cómo se abusa*— respondida antes de escribir el
código.

### 5.1 Auto-emparejamiento

**Comprarte a ti mismo está prohibido.** Sin la regla:

- lavas el precio: cruzas contigo al precio que quieras y mueves el índice;
- **evades el impuesto al revés**: no, en realidad te lo comes — pero puedes
  usarlo para *fabricar volumen* y meter un objeto en la cesta del índice.

Se comprueba con `player_id` y **también entre cuentas del mismo clan**? No:
eso castigaría el comercio legítimo entre amigos, que es la mitad de la gracia
de un clan. Se controla por otro lado (§5.3).

### 5.2 Desbordamiento

`precio × cantidad` es un `long`. Con precio y cantidad venidos del cliente,
**el producto puede dar la vuelta y salir negativo** — y cobrar en negativo es
ingresar. Ya pasó en la tienda.

```
precio    1 .. 100_000_000
cantidad  1 .. 10_000
producto  cabe de sobra en long, y se ACOTA ANTES de multiplicar
```

### 5.3 Manipulación del índice

El índice mide precios reales, así que quien pueda moverlo puede mentirle al
servidor entero. Tres defensas, y hacen falta las tres:

1. **Ponderado por volumen** (VWAP), no media simple: una operación de 1 unidad
   no mueve nada.
2. **Mediana por día**, no media: una operación absurda queda fuera.
3. ⚠⚠ **Un objeto solo entra en la cesta si ese día lo negociaron al menos
   `MIN_PARTES` parejas distintas.** Es la que de verdad protege: dos cuentas
   pueden hacer mil operaciones entre ellas y siguen siendo *una* pareja.

### 5.4 Inundar el libro

Una tasa por poner la orden (ya existe: `listingFee`) **y un tope de órdenes
abiertas por jugador**. Sin el tope, mil órdenes de 1 unidad convierten
cualquier consulta del libro en un escaneo.

### 5.5 El estado lo comparten DOS personas

Cuando la orden de A se llena contra la de B, **B tiene que enterarse**: su
orden ha cambiado y su dinero también.

> ⚠⚠ Es exactamente el bug de los clanes, y aquí es peor: allí la etiqueta se
> quedaba puesta; aquí el jugador ve órdenes que ya no existen y puede intentar
> cancelarlas.
>
> **`Resultado.afectados()` desde el primer día.** No es refactor futuro: es
> parte del diseño.

### 5.6 Interbloqueos

Un cruce toca **dos órdenes y dos monederos**. Dos cruces simultáneos que
toquen las mismas filas en orden distinto **se bloquean mutuamente y MariaDB
mata uno**.

> **Regla: las filas se bloquean SIEMPRE en orden ascendente de identificador.**
> Ya está escrita en CLAUDE.md para `player_id`; aquí vale igual para
> `order_id`.

### 5.7 La orden que caduca con el servidor caído

Igual que los listados: se comprueba **al leer** y hay un barrido al arrancar.
Lo retenido se devuelve por entrega diferida.

---

## 6. La inflación, medida y no supuesta

### 6.1 Qué es de verdad

Inflación es **que el dinero valga menos**, y eso se ve en que las cosas cuestan
más. Así que se mide con **una cesta de la compra**:

```
indice(hoy) = 100 * Σ(precio_hoy_i × peso_i) / Σ(precio_base_i × peso_i)
```

- `precio_i` — el **VWAP mediano** del objeto ese día
- `peso_i` — cuánto se negocia normalmente (fijado al crear la cesta, **no
  recalculado a diario**)

> ⚠ **Los pesos se congelan.** Si se recalcularan cada día, un objeto que se
> dispara ganaría peso justo cuando sube, y el índice subiría dos veces por la
> misma causa. Es el error clásico de los índices caseros.

### 6.2 Para qué sirve

No es un adorno de la pantalla. Es **el termómetro que dice si los sinks
funcionan**, y CLAUDE.md lleva desde el principio diciendo que la economía está
sin calibrar porque *no hay nada que medir*. Esto es lo que hay que medir.

Conecta con lo que ya existe:

```
/luna economia     ya mide masa monetaria y concentración
el indice          añade PRECIOS
juntos             dicen si sobra dinero (inflacion) o falta (deflacion)
```

### 6.3 Lo que NO se hace

> ⚠ **El servidor no ajusta precios solo.** Es tentador —«si hay inflación,
> sube el impuesto»— y es como se rompe la confianza en una economía: el
> jugador deja de poder predecir nada. El índice **informa**; los cambios los
> decide una persona y se anuncian.

---

## 7. Cómo conecta con todo lo demás

El usuario pidió que «todo conecte con todo». Lo que conecta de verdad:

| Sistema | Conexión |
|---|---|
| **Oficios** | Lo que producen —menas, bayas, bellotas— es justo lo que **no** vende la tienda. El mercado es su salida natural |
| **Tienda** | Es el **suelo y el techo** del precio: nadie vende una Poción a 700 si el NPC la da a 600. Los nueve artículos anclan el mercado |
| **Clanes** | El tesoro puede **comprar** (una compra del clan, pagada del tesoro) — fase 4 |
| **Misiones** | `m1_comprar`, `m2_vender` y `t5_gts` se completan aquí |
| **Impuesto** | El progresivo que ya existe: **el único sink que escala con la riqueza** |
| **Telemetría** | El índice entra en `/luna economia` |

---

## 8. Las fases

**Cada una se despliega sola y deja el servidor funcionando.** No hay ninguna
que dependa de terminar la siguiente.

### FASE 1 — El libro de órdenes (objetos)

```
V015   market_order . market_trade
       MarketService: poner, cancelar, cruzar
       custodia doble . prioridad precio-tiempo . llenado parcial
       Resultado.afectados() desde el minuto uno
autotest: el cruce, los precios, la custodia, el no-auto-cruce, suma cero
```

### FASE 2 — La pantalla

```
chasis de cosmeticos . panel izquierdo: categorias y buscador
pantalla: el LIBRO del objeto elegido, las dos caras
poner orden de compra / de venta . mis ordenes
```

### FASE 3 — El mercado de Pokémon

```
reutiliza gts_listing, que ya esta hecho
filtros: especie, nivel, shiny, IVs
pestaña propia en la misma pantalla
```

### FASE 4 — El índice y el histórico

```
V016   market_daily (VWAP por objeto y dia)
la cesta, los pesos congelados, el indice
grafico en la pantalla . el indice en /luna economia
```

### FASE 5 — Las conexiones

```
el tesoro del clan compra . las misiones . los avisos de "se vendio lo tuyo"
```

---

## 9. Lo que NO va a tener, y por qué

| | |
|---|---|
| **Mercados por ciudad** | Albion los tiene porque transportar mercancía **es** el juego. Aquí hay una ciudadela: separar precios por zona sería fricción sin contenido |
| **Ventas al descubierto / derivados** | Es un servidor de Pokémon |
| **Comprar con LunaCoins** | D-014. La moneda premium no toca el mercado, ni en una dirección ni en la otra |
| **Ajuste automático de impuestos** | §6.3 |
