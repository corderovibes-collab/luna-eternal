# Tienda

## Purpose

Qué se vende, qué **no** se vende y por qué. La decisión importante de este
documento no es el catálogo: es **lo que se deja fuera**.

## Dependencies

- [CLAUDE.md](../../CLAUDE.md) — P3 (sinks antes que sources), P6, D-007, D-014
- [economy-overview.md](economy-overview.md) — las tres monedas
- [monetization.md](monetization.md) — los cuatro niveles y el test de 6 preguntas

## Related Documents

- [interfaces-catalog.md](../ui/interfaces-catalog.md)
- [dibujado.md](../ui/dibujado.md) — las 6 reglas de dibujar una pantalla

## Current Status

**Desplegada el 2026-08-23.** 2 categorías, 9 artículos, `217/217` en el
autotest. Sin verificar en el juego.

```
tools/gen_tienda.py                                    genera el catalogo
mod/src/main/resources/data/lunaeternal/shop_catalog.json   generado
mod/src/main/java/net/pokereport/luna/shop/            ShopService · ShopCatalog
mod/src/client/.../pokepad/TiendaScreen.java           la pantalla
```

## Last Decision

**La tienda es de primeros auxilios, no un catálogo** (usuario, 2026-08-23).

## Next Actions

- **Verificar en el juego**: comprar, vender, y que el saldo baje solo.
- **El análisis de economía general** (§5). Es lo único que queda abierto aquí,
  y no se puede hacer sin datos de gente jugando.
- Decidir si la **Poké Caña** entra (§3).

---

## 1. Lo que hay

```
LO ESENCIAL
  Poké Ball          400      la normal, y solo la normal
  Máx. Revivir     3.000      material de la Máquina Curativa

CUIDADO
  Poción             600      cura 20 PS
  Superpoción        900      cura 60 PS
  Antídoto           200
  Antiquemar         200
  Antihielo          200
  Antiparalizador    200
  Despertar          200
```

Nueve. Y el número es el mensaje.

---

## 2. Lo que NO hay, que es la decisión

**Dos órdenes del usuario el mismo día, y la segunda es la que manda:**

1. *«en la tienda solo van a ver artículos de Cobblemon, ya lo que sea de
   Minecraft ellos en la exploración lo pueden conseguir»*
2. *«ítems básicos, los necesarios. En Poké Ball la normal. Ítem de curar al
   Pokémon entre 20 % a un 50 %. Ítems que se requieren para craftear cosas
   básicas del Pokémon. Lo otro ellos tienen que conseguirlo explorando»*

Entre las dos, el catálogo pasó de **28 → 90 → 9**. Fuera quedan:

| Fuera | Por qué |
|---|---|
| Las otras 20 Poké Balls | Se craftean con **bellotas**, que es justo lo que da XP al oficio **Agricultor** |
| Las 10 piedras evolutivas | Contenido de exploración; son la recompensa de un cofre |
| Objetos de combate (Restos, Banda Élite…) | Ídem, y además son poder competitivo |
| Vitaminas y caramelos EXP | Progresión. Ver §4 |
| Hiperpoción y superiores | Fuera de la banda 20-50 % (§3) |
| Todo lo de Minecraft | Se consigue explorando y minando |

> ⚠⚠ **Una tienda completa vacía el mundo.** Si todo se compra, explorar deja
> de tener sentido salvo para conseguir dinero — y entonces el juego entero se
> reduce a una barra de progreso hacia el catálogo. **Lo que no está en la
> tienda es lo que hace que jugar valga la pena.**

> ⚠ Y encaja con lo construido el día anterior: las bayas y las bellotas son la
> XP del **Agricultor**, y la madera y la piedra la del **Minero**. Vender lo
> que producen los oficios sería competir con ellos.

---

## 3. Los tres artículos que no son obvios

### Máx. Revivir — es un material, no un objeto de combate

Leído de su receta en el jar de Cobblemon:

```
MAQUINA CURATIVA = cobre + hierro + redstone + UN MAX REVIVE
```

Y el **Máx. Revivir no se craftea**: sale de cofres. Así que sin él, montarse
la base de curación es cuestión de suerte. Es literalmente el caso que pedía el
usuario: *«ítems que se requieren para craftear cosas básicas del Pokémon»*.

### Poción y Superpoción — la banda del 20-50 %

Los números **salen de sus datos, no de memoria**:

```
data/cobblemon/mechanics/potions.json
{"potionRestoreAmount":"20","superPotionRestoreAmount":"60",
 "hyperPotionRestoreAmount":"120"}
```

Sobre un Pokémon de nivel medio (80-130 PS) eso es **~20 %** y **~50 %**.

> ⚠ **La Hiperpoción (120 PS) cura una barra entera de casi cualquiera**, así
> que ahí está la línea. No es un juicio estético: es dónde cae el 50 %.

### La Poké Caña — fuera, y fue lo más discutible

El oficio **Pescador** la necesita, así que estuvo dentro un rato. Se saca al
comprobar de dónde viene su plantilla:

```
pokerod_smithing_template  ->  pesca de tesoro
                               pecios (fishing_boat, big_treasure)
```

O sea que **se consigue explorando**, que es la regla. Su única receta
*duplica* una existente, así que la primera siempre sale de ahí.

> **Si al jugar resulta que arrancar como Pescador se hace cuesta arriba, es
> una línea en `gen_tienda.py`.** Queda anotado aquí para no volver a
> investigarlo desde cero.

---

## 4. La línea que no se cruza

`rare_candy`, `exp_candy_*` y `lucky_egg` **no están hoy**. Si algún día
volvieran, la regla es:

> ⚠⚠ **Solo por Plata, nunca por LunaCoins.** Por moneda del juego son un
> **sink** y están bien. Por moneda premium serían **comprar progresión con
> dinero real** — nivel T4, la línea roja de D-007 y D-014.

Lo mismo vale para `ability_capsule` y las vitaminas: por Plata son economía,
por LunaCoins son poder competitivo vendido.

---

## 5. Los precios son provisionales, y hay un solo sitio donde tocarlos

**Decisión del usuario:** *«más adelante definimos precios porque necesitamos
hacer un análisis general de la economía para que todo quede bien
equilibrado»*.

Por eso **los precios no se escriben artículo a artículo**. Hay cinco escalones
en `tools/gen_tienda.py`, y cada artículo dice a cuál pertenece:

```python
ESCALONES = {
    "basico":  200,
    "comun":   400,     # <- ancla: Poke Ball, de la config real de produccion
    "medio":   600,     # <- ancla: Pocion
    "bueno":   900,     # <- ancla: Superpocion
    "raro":  3_000,     # <- ancla: Revivir
}
RECOMPRA = 0.10
```

**Aplicar el análisis será cambiar cinco cifras y reejecutar el generador.**

> ⚠ **La recompra es un PORCENTAJE, no un número suelto.** El invariante de
> no-arbitraje —vender por más de lo que cuesta es dinero infinito— no puede
> romperse por un despiste al teclear si el precio de venta se *deriva*.

### Lo que el análisis tiene que mirar junto

La tienda **no se calibra sola**. Todos estos números están hoy puestos a ojo,
y el único calibrado contra algo es el de los oficios — contra la tienda
*vieja*, que ya no existe:

| | Hoy | Se calibra con |
|---|---|---|
| Escalones de la tienda | 200 · 400 · 600 · 900 · 3.000 | ingreso mediano por hora |
| Recompra al banco | 10 % plano | cuánto se recicla frente a lo que se produce |
| Paga de los oficios | 50 · 150 · 400 · 1.000 · 2.500 | *se calibró contra la tienda vieja* |
| Fundar un clan | 5.000 | ídem |
| Tope de kits | 6.000/día | ≤25 % del ingreso diario |
| Tramos del GTS | 5-18 % | reparto P50/P99 |

**Nada de esto se puede hacer sin gente jugando.** `/luna economia` mide, pero
hasta entonces no hay nada que medir.

---

## 6. El catálogo se genera, no se escribe

```bash
python tools/gen_tienda.py
python tools/gen_tienda.py --buscar stone
python tools/gen_tienda.py --listar
```

> ⚠⚠ **Es la misma lección que costó 62 cosméticos que no existían.** Un
> catálogo escrito a mano **promete** cosas, y `ShopCatalog.load()` se salta lo
> que no exista **con un aviso en el log que nadie mira**. Un identificador mal
> escrito no da error: da un hueco.

El generador cruza **dos fuentes** del jar de Cobblemon —la clave de idioma
(`item.cobblemon.X`) y el modelo de objeto (`models/item/X.json`)— y con eso
tiene los **487 objetos que existen de verdad**. Si un identificador del
catálogo no está entre ellos, **aborta** en vez de publicar el hueco.

En el arranque se ve que funcionó:

```
Tienda: 2 categorías, 9 objetos (0 omitidos por no existir)
```

---

## 7. La pantalla

### Las categorías van en el panel izquierdo

En Cosméticos y Misiones son **pestañas arriba** porque son nombres cortos.
Aquí cada una lleva **icono + nombre + una frase que explica para qué sirve**,
y eso en una pestaña de 150 px no entra. Es la primera pantalla que usa ese
panel para algo de verdad.

> ⚠ **Cinco categorías como máximo.** No es una preferencia: la lista vertical
> de tarjetas de 94 px empieza en 156 y el panel acaba en 762, así que la sexta
> se dibujaría **fuera del marco** — invisible e impulsable. El autotest lo
> comprueba, porque es exactamente el fallo que ya pasó con la cadena `oficios`
> del árbol de misiones.

### Lo que viaja y lo que no

| Viaja | No viaja |
|---|---|
| El **identificador** del artículo | El **precio** — lo pone el servidor mirando su catálogo (P6) |
| La categoría | Un **índice** — ataría al cliente al orden exacto del JSON |
| La cantidad (acotada a 1-64) | «Cuántos tengo» — lo cuenta el cliente de su inventario |

> ⚠ **Un índice haría que cambiar el catálogo con la tienda abierta comprara el
> artículo de al lado.**

> ⚠⚠ **La cantidad se acota ANTES de multiplicar.** Llega del cliente, y
> 2.000 millones en `precio * cantidad` **desborda el long y sale negativo** —
> cobrar en negativo es *ingresar* dinero. Acotar después no sirve de nada.

> ⚠ **El saldo se reenvía tras cada compra.** Sin eso el jugador ve el número
> viejo hasta reabrir la pantalla. Es la lección del 2026-08-23: *si el
> servidor cambia un estado que el cliente dibuja, el servidor lo reenvía*.

### Los botones se apagan, no desaparecen

Que un artículo exista y no puedas pagarlo es **información**. Que no exista es
un catálogo distinto.

---

## 8. Autotest

Las de economía ya estaban (no-arbitraje, precios positivos, la premium no se
recompra). Las nuevas son **de dibujado y de alcance**:

```
todo articulo de la tienda es de Cobblemon
todo icono de categoria es de Cobblemon
ningun articulo esta en dos categorias   <- dos precios para el mismo objeto se
                                            veria como "el precio cambia segun
                                            por donde entres"
ningun identificador de categoria se repite
toda categoria tiene al menos un articulo
todo icono de categoria es un objeto real
todo nombre queda legible sin sus codigos de color
las categorias caben en el panel
ninguna categoria pasa de 3 paginas
precio x 64 no desborda el long
```

> ⚠ La de «todo artículo es de Cobblemon» está porque **es la clase de regla que
> se cae sola**: el catálogo se genera, pero alguien puede editar el JSON a mano
> un martes.
