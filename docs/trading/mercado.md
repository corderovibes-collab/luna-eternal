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

**Las dos mitades en vivo y con la misma forma** (2026-08-25). `V015` y `V016`
aplicadas, **345/345** en el autotest. Sin verificar en el juego con dos
cuentas.

## Last Decision

**D-042 — Los objetos van por escaparate, no por libro de órdenes.** Revoca la
mitad de objetos de D-041. Ver §2-bis.

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

## 2-bis. ⚠⚠ D-042: los objetos pasan a ESCAPARATE

**Decisión del usuario, 2026-08-25**, después de usarlo:

> *«hay textos que se sobreponen, opciones que no funcionan, opciones
> duplicadas, botones duplicados… la idea es publicar una oferta así como en el
> de los Pokémon: el comprador ve la oferta, se interesa y la compra, y se te
> quita a ti el ítem»*

**D-041 no estaba mal razonada; le faltaba un dato: cuánta gente hay.** Un libro
de órdenes es el mecanismo correcto para objetos fungibles —eso sigue siendo
cierto— pero un libro **necesita las dos caras pobladas para cruzar**. Con doce
personas no cruza: pones una orden de compra y se queda ahí hasta que alguien
pase por casualidad. Lo que en Albion es *liquidez* aquí es *una lista de
deseos que nadie lee*.

Y el coste no era solo que no cruzara. **La pantalla que un libro necesita tiene
dos entradas para todo**: pestañas LIBRO / MIS ÓRDENES / HISTORIAL para mirar, y
campos PRECIO / CANTIDAD con botones COMPRAR / VENDER para actuar. Eso es lo que
el usuario describió como botones duplicados — y tenía razón, porque lo eran.

> **D-042 — Los objetos se publican como oferta, igual que los Pokémon.**
>
> | | Antes (D-041) | Ahora (D-042) |
> |---|---|---|
> | **Objetos** | Libro de órdenes | **Escaparate**: publicas una pila con su precio |
> | **Pokémon** | Listado por ejemplar | Igual |
>
> Las dos mitades usan **el mismo servicio** (`GtsService`), el mismo protocolo
> y la misma disposición de pantalla. Dos mercados hermanos, no dos mercados
> distintos.

**Lo que se gana, y no es solo la pantalla:**

| | |
|---|---|
| **Funciona con poca gente** | Una oferta publicada se puede comprar el primer día. Una orden de compra puede no cruzarse nunca |
| **Una sola forma de hacer cada cosa** | Publicar, comprar, retirar. No hay dos caminos para lo mismo |
| **Se aprende una vez** | Quien sepa vender un Pokémon sabe vender una pila de piedras |
| **La custodia es más simple** | Solo retiene **mercancía**. La doble custodia de §4 existía porque una orden de compra retiene **dinero**, y sin órdenes de compra esa mitad desaparece |

**Lo que se pierde, y hay que decirlo:**

- **No hay precio de mercado agregado.** Un libro da un *bid/ask*; un escaparate
  da «lo que la gente pide». Para el índice de precios (§6) eso significa medir
  **operaciones cerradas** y no el libro — que de todas formas es lo que la
  fase 4 iba a hacer.
- **No hay órdenes de compra.** No puedes decir «compro cobre a 20». Hoy hay que
  mirar y esperar.

> ⚠ **`MarketService` NO SE BORRA.** Sigue escrito, probado y con sus
> comprobaciones. Lo que cambia es **por dónde entra el jugador**. El día que el
> servidor tenga gente de sobra para que un libro cruce, el motor está ahí y
> volver a encenderlo es una pantalla, no un sistema.

> ⚠ Y el diagnóstico de D-041 sigue siendo cierto en lo suyo: **un Pokémon no
> puede ir a un libro de órdenes**. Lo que ha cambiado es que los objetos
> tampoco lo necesitan *todavía*.

### 2-bis.1 Lo que la maqueta cazó al hacerlo

Cuatro fallos, **y ninguno daba error al compilar ni al ejecutar**:

| | |
|---|---|
| `filasCaben()` era una **fórmula a mano** que ya no cuadraba con `listaY()` | La lista había bajado (conmutador y buscador nuevos) y la fórmula seguía contando desde donde estaba antes: salían **cinco filas donde caben cuatro**, y la quinta se dibujaba encima de la paginación |
| La columna **EXPIRA salía marcada** nada más abrir | Su orden descendente era `NUEVO`, que es el orden **por defecto**. Decía que ordenaba por caducidad sin hacerlo — y en oro sobre naranja, que no se lee |
| **Los nombres de Minecraft son largos** | «Escaleras de ladrillos de piedra» mide 364 px y su columna tiene 240: se metía **encima del vendedor**. Un Pokémon no tiene este problema («Charizard» cabe siempre), así que no se heredaba del GTS |
| **«VENDEDOR / UNIDAD» no cabía con su flecha** de ordenación | 164 px en 150 |

> ⚠⚠ **Los cuatro los encontró `tools/gen_maqueta_mercado.py`**, que dibuja la
> pantalla sobre el chasis real con las anchuras reales de la fuente del juego y
> **avisa** de desbordes y solapes. Ninguno se habría visto revisando el código:
> los cuatro son *números que dejaron de cuadrar*.

### 2-bis.2 Dos decisiones de la pantalla que no son estéticas

**La barra tiene cuatro iconos y no seis.** Copiar los del GTS habría dejado
*filtros* y *chollos* sin nada que hacer: un Pokémon tiene IVs, naturaleza y una
tasación, y **una pila de veinte piedras no tiene nada que filtrar**. Un botón
que no hace nada es justo de lo que se quejaba el usuario.

**La confirmación se pide solo al comprar.** Comprar es lo único que no se
deshace: el dinero se va. Publicar sí se deshace —se retira y los objetos
vuelven, solo se pierde la tasa—, así que confirmar ahí sería un clic de más en
la acción que más se repite.

### 2-bis.3 ⚠⚠ El invariante nuevo: el payload

`publicarObjeto` escribe `identificador + separador + cantidad` en un
`byte[]`, y quien entrega lo vuelve a leer. **Son dos sitios distintos con su
propia idea del formato.**

Si dejaran de estar de acuerdo, **la compra no daría ningún error**: el dinero
cambiaría de manos y los objetos no aparecerían. Es el único fallo posible en
esta mitad que se come mercancía en silencio, así que el autotest lo comprueba
de punta a punta: publica, compra, y verifica que salen **el mismo objeto y la
misma cantidad**.

---

## 3. El libro de órdenes *(el motor, hoy sin puerta — ver §2-bis)*

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

## 4-bis. ⚠⚠ De dónde tiene que salir lo que se vende

**Orden del usuario (2026-08-24), y son dos reglas distintas porque son dos
cosas distintas:**

| | Dónde tiene que estar | Por qué |
|---|---|---|
| **Objetos** | **En el inventario**, y solo ahí | Hay que sacarlos para retenerlos, y solo se puede sacar de donde el jugador los tenga encima |
| **Pokémon** | **Equipo o PC, da igual** | Un Pokémon no ocupa inventario: vive en un almacén del servidor, y el PC es tan «suyo» como el equipo |

### Los objetos: la barra rápida SÍ cuenta

`PlayerInventory.main` son **36 huecos, y los nueve primeros son la barra
rápida**. Así que lo que llevas en la mano cuenta igual que lo que llevas en la
mochila — no hay que moverlo a ningún sitio para venderlo.

> ⚠ **Lo que hoy NO cuenta es la mano secundaria** (`offHand`), y queda anotado
> porque es un hueco visible: si llevas 64 Poké Balls en la mano izquierda y las
> intentas vender, el mercado dice «no tienes tantos».
>
> **No es un fallo de custodia** —contar y sacar miran exactamente el mismo
> sitio, así que no se puede vender lo que no se retira— pero sí es confuso.
> Arreglarlo es sumar la mano secundaria en `cuantos` **y** en `sacar`, las dos
> a la vez: cambiar solo una rompe la custodia justo por donde no debe.

### Los Pokémon: el PC cuenta

Es la diferencia que importa para la **fase 3**. Un objeto hay que retirarlo del
inventario porque *está* en el inventario; un Pokémon está en un almacén del
servidor, y el PC es parte de ese almacén.

> ⚠ Obligar a sacarlo al equipo antes de venderlo sería fricción por nada: el
> jugador tendría que hacer sitio en un equipo de seis para poder listar algo
> que no va a usar. Y con el equipo lleno, sencillamente **no podría vender**.

**La custodia sigue siendo la misma regla**, eso no cambia: al listarlo, el
Pokémon sale del equipo *o del PC* y pasa a vivir en `gts_listing`. Lo listado
no puede estar en poder del vendedor — si siguiera en su PC podría seguir
operando con él mientras se vende, y ese es el vector de duplicación número uno.

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

## 5-bis. ⚠⚠ EL TASADOR: de qué depende que un Pokémon valga

**Petición del usuario (2026-08-24):** *«una economía súper avanzada sobre las
habilidades, IVs, EVs que tenga, el estado del Pokémon, tipo, si es legendario o
no, si es shiny o no, y basándote en eso se da un precio estimado; también
basándote en lo que han colocado los jugadores… eso con el tiempo se va
sincronizando»*.

Son **dos mitades**, y la segunda es la que lo hace interesante.

### 5-bis.1 La fórmula: qué hace caro a un Pokémon

```
estimado = BASE(especie) × IVs × EVs × shiny × nivel × habilidad
```

**`BASE(especie)` sale de los datos de Cobblemon, no de una lista nuestra.**
Cada especie trae su **total de estadísticas base** y sus **etiquetas**
(`legendary`, `mythical`, `ultra_beast`, `paradox`, `starter`…). Con eso hay de
sobra:

| | Por qué |
|---|---|
| Total de estadísticas base | Es el mejor indicador de «cuánto sirve» que existe, y viene ya calculado |
| Etiquetas de rareza | Un legendario no vale más *por sus números*: vale más porque **hay uno** |

> ⚠⚠ **Mantener a mano una tabla de 1.025 especies sería garantizar que se queda
> vieja.** Es la misma lección que el catálogo de la tienda y los 62 cosméticos
> que no existían: **si se puede derivar del jar, se deriva**.

**Los IVs se miden dos veces, y no es redundante:**

```
total     0..186   cuánto tiene en general
perfectos 0..6     cuántos están a 31
```

> ⚠ El mercado competitivo no paga por «180 de 186»: paga por **cuántos 31**
> tiene. Un 6×31 vale mucho más que la suma de sus partes, y con solo el total
> los dos serían indistinguibles.

**Los EVs valen porque son trabajo**, no potencial: 508 puntos son horas de
alguien. Y **el nivel, igual**.

**Shiny es el multiplicador grande**, y va aparte de todo lo demás: un shiny malo
sigue siendo un shiny.

### 5-bis.2 ⚠⚠⚠ La sincronización: el mercado corrige la fórmula, no la sustituye

Aquí está la parte que hay que hacer bien.

Lo obvio sería: *«mira lo que se ha pagado por esa especie y usa la mediana»*.
**Y estaría mal**, porque mezcla un shiny 6×31 con un ejemplar de nivel 5 recién
capturado: la mediana de esa mezcla no describe a ninguno de los dos.

Lo que se hace es corregir **la calibración**:

```
por cada venta cerrada:   ratio = precio_real / estimado_al_publicar
                          (por eso `estimated` se guarda EN LA FILA)

correccion(especie) = mediana de los ratios de esa especie
estimado_final      = formula × correccion
```

Y la corrección **entra poco a poco**, con peso según cuántas ventas haya:

```
peso = n / (n + K)          n = ventas observadas,  K = 8
final = formula × (1 + peso × (correccion − 1))
```

> **Con cero ventas el tasador es pura fórmula. Con muchas, es casi puro
> mercado.** Y por el camino se mezclan solos, sin que nadie tenga que decidir
> cuándo cambiar de método. Eso es literalmente el *«con el tiempo se va
> sincronizando»* que pidió el usuario.

> ⚠ **`K = 8` es la cifra que decide cuánto tarda.** Con 8 ventas, la fórmula y
> el mercado pesan la mitad cada uno. Es provisional, como todo lo económico, y
> se toca en un solo sitio.

### 5-bis.3 Cómo se abusa, y qué lo impide

| Ataque | Defensa |
|---|---|
| Vendértelo a ti mismo caro para inflar la corrección | Ya prohibido: nadie cruza consigo mismo, en el código **y** en la base |
| Dos cuentas haciéndose ventas entre ellas | La corrección usa la **mediana**, no la media: hacen falta más operaciones falsas que reales |
| Publicar a precio absurdo para mover el índice | **Solo cuentan las ventas CERRADAS**, no las publicadas. Un precio que nadie paga no dice nada |

> ⚠⚠ Esa última es la que más importa y la más fácil de hacer mal. Un tasador
> que mirase lo **publicado** se puede mover gratis: publicas un Magikarp a diez
> millones y ya has movido la referencia. **Un precio solo es información cuando
> alguien lo ha pagado.**

### 5-bis.4 Qué NO hace el tasador

> ⚠ **No pone el precio: lo sugiere.** El jugador escribe el que quiera. Si el
> servidor fijara precios dejaría de haber mercado — y la mitad de la gracia es
> encontrar a alguien que no sabe lo que tiene.

Se enseña como una referencia («estimado: 12.400») y, cuando el precio escrito
se aleja mucho, un aviso suave. Nada más.

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

### FASE 2 — La pantalla del libro ~~hecha~~ **RETIRADA por D-042**

```
existio, funciono, y el usuario la uso: por eso sabemos que no valia
la pantalla que un LIBRO necesita tiene dos entradas para todo
  --pestañas para mirar, campos y botones para actuar-- y eso es lo que
  el usuario llamo "opciones duplicadas, botones duplicados"
```

### FASE 2-bis — El escaparate de objetos ✅ (2026-08-25)

```
la misma pantalla que los Pokemon, con tres modos:
  LISTA   lo que hay a la venta, con buscador y cabecera ordenable
  VENDER  tu mochila -> elegir, cantidad (1/8/64/TODO), precio, duracion
  MIAS    lo tuyo, para retirarlo
conmutador POKEMON / OBJETOS a la derecha, en las dos mitades
autotest +26: el PAYLOAD de punta a punta (§2-bis.3), el buscador,
              duenoDe, retirar, y lo que NO se puede publicar
```

### FASE 3 — El mercado de Pokémon ✅ (2026-08-25)

```
reutiliza gts_listing, que ya esta hecho
V016   las columnas para FILTRAR: los 6 IVs, los 6 EVs, naturaleza,
       habilidad, genero, tera, rareza y el ESTIMADO al publicar
Tasador  la formula + la correccion por mercado (§5-bis)

la pantalla, con la disposicion que pidio el usuario:
  alternar POKEMON / OBJETOS
  buscador por nombre o por @nick
  filtros avanzados: nivel, precio, los 6 IVs, los 6 EVs,
                     shiny, genero, tera
  panel izquierdo: el ejemplar EN 3D + pestañas EST / IVS / EVS
  crear oferta: eliges de tu equipo o tu PC, lo ves en 3D, precio,
                duracion y PUBLICAR -- con el ESTIMADO al lado
  mis ofertas: ver, cancelar

⚠ SE PUEDE LISTAR DESDE EL EQUIPO O DESDE EL PC (§4-bis)
⚠ Y NO SE PRECARGA NADA: todo lo que se ve lo ha publicado un jugador
```

### FASE 4 — El índice y el histórico ⬜

```
market_daily (VWAP por objeto y dia)
la cesta, los pesos congelados, el indice
grafico en la pantalla . el indice en /luna economia
```

> ⚠⚠ **CON EL ESCAPARATE, EL ÍNDICE SE MIDE IGUAL PERO LA FUENTE CAMBIA.** El
> libro daba precios de cruce; el escaparate da **ventas cerradas**. Es menos
> datos y **mejor dato**: un precio solo es información cuando alguien lo ha
> pagado (§5-bis.3). Lo que sigue en pie es que **no sirve de nada hasta que
> haya operaciones reales** — hoy mediría el ruido de dos personas probando.

### FASE 5 — Las conexiones ⬜

```
el tesoro del clan compra . las misiones . los avisos de "se vendio lo tuyo"
```

> ⚠ El aviso de «se vendió lo tuyo» **ya está a medias y no se ve**: al comprar
> se refresca la pantalla y el saldo del vendedor si está conectado
> (`refrescarMercadoA`), que es la lección de los clanes. Lo que falta es el
> **toast**, para el que no está mirando la pantalla.

---

## 9. Lo que NO va a tener, y por qué

| | |
|---|---|
| **Mercados por ciudad** | Albion los tiene porque transportar mercancía **es** el juego. Aquí hay una ciudadela: separar precios por zona sería fricción sin contenido |
| **Ventas al descubierto / derivados** | Es un servidor de Pokémon |
| **Comprar con LunaCoins** | D-014. La moneda premium no toca el mercado, ni en una dirección ni en la otra |
| **Órdenes de compra («compro cobre a 20»)** | Se fueron con D-042. El motor sigue escrito (§3): vuelven el día que el servidor tenga gente para que un libro cruce |
| **Ajuste automático de impuestos** | §6.3 |
