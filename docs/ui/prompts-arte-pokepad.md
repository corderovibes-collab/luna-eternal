# El arte del PokePad

## Purpose

Qué piezas hacen falta, a qué resolución, y los prompts exactos con los que se
generan.

## Dependencies

- [`interfaz-cliente.md`](interfaz-cliente.md) §4 — qué necesita cada pantalla
- [`pokepad-referencia.md`](pokepad-referencia.md) §1 — de dónde salen las medidas

## Current Status

**La pantalla principal está terminada y verificada en el juego (2026-08-15):**
chasis HD dibujado a píxeles reales, los quince iconos, la cara del jugador, el
saldo y la barra de seis botones. Las pantallas de cada aplicación vienen
después (§3.2).

> **Este documento se ha reescrito dos veces**, y las dos por lo mismo: se
> intentó imitar el estilo pixel art del Pad de referencia y no funcionó. La
> §5 cuenta por qué, con los números delante. Lo que hay ahora es **HD**.

---

## 1. Las resoluciones

| Pieza | Tamaño final | Cuántos ficheros |
|---|---|---|
| **Chasis** | **1380 × 828** | 1 por pantalla |
| **Iconos** | **100 × 100** | 1 cada uno, 15 en total |
| **Botones** | **120 × 96** | 1 cada uno, 6 en total |

### 1.1 · Por qué 1380 × 828 y no otro número

No es arbitrario: **es divisible entre 1, 2, 3, 4 y 6**, que son los valores
que puede tener el ajuste *GUI Scale* de un jugador.

Minecraft multiplica toda la interfaz por ese número. Si el Pad se dibuja al
tamaño real de pantalla —que es lo que hace el código— un texel cae
**exactamente** en un píxel, sea cual sea el ajuste del jugador. Con cualquier
otro tamaño, a alguien le tocaría verlo con los bordes rotos.

La proporción es **5:3**, la misma que tenía el chasis anterior.

### 1.2 · ⚠️ Una captura de pantalla no sirve para medir

Minecraft dibuja las interfaces **escaladas**. A escala 3, una textura de 346 px
ocupa 1 038 en pantalla, así que contar píxeles en una captura da siempre un
número inflado. Las medidas se leen de los PNG, nunca de una imagen del juego.

### 1.3 · Sin una sola letra

**El texto lo dibuja el juego**, no la textura: está traducido, y un jugador en
inglés vería «Cazas» pintado a fuego. Todos los prompts lo prohíben
explícitamente. Si vuelve con letras se regenera — **no vale borrarlas**,
porque el hueco tiene que estar pensado desde el principio.

### 1.4 · La composición no se mueve

El código mide **una sola vez** dónde cae el área de contenido. Por eso el
chasis base se genera primero y se congela; todo lo demás se dibuja respetando
sus medidas, que imprime `tools/gen_pokepad.py` al terminar.

---

## 2. ⚠️ El prompt va en PROSA, no en documento

La primera versión estaba escrita con títulos, listas y una tabla de colores.
**Gemini dibujó el documento**: devolvió una imagen del propio prompt, con el
icono metido en una esquina.

Un generador de imágenes no distingue entre «esto son mis instrucciones» y
«esto es lo que quiero ver»: todo lo que le llega es descripción. Si le mandas
algo con pinta de ficha técnica, dibuja una ficha técnica. Aquel prompt llevaba
además la línea *«paste this before every single icon prompt»*, que es una
instrucción **para la persona**, y también salió pintada.

| | |
|---|---|
| **El sujeto, primero** | Lo que va al principio ancla la imagen |
| **Prosa, no listas** | Ni títulos, ni viñetas, ni tablas |
| **Pocos códigos de color** | Una lista de catorce hex se dibuja como lista |
| **Nada dirigido a ti** | «pega esto antes de…» acaba en la imagen |

---

## 3. El chasis

### 3.1 · El fondo base

```
A polished modern handheld gaming device UI frame seen straight on, filling
the whole canvas, aspect ratio 5:3. Dark graphite body with rounded corners
and soft bevels, and thin glowing cyan accent lines inset along its edges.
On the left quarter, three empty recessed slots stacked vertically: a square
one on top, a wide rectangular one in the middle, and a short wide one at the
bottom with a small square button beside it; all three are completely empty,
just the recess and its inner shadow. The remaining three quarters are one
single large recessed screen of flat soft periwinkle blue with a pale rim,
COMPLETELY EMPTY and perfectly rectangular, with no grid, no cells and no
icons inside it. Centered at the top, an empty raised nameplate. Clean
smooth shading, no visible pixels. No text, no letters, no numbers, no
labels, no watermark, no user interface elements, no document.
```

> **La pantalla va vacía a propósito.** Las celdas las dibuja el código (§4).

### 3.2 · Un fondo por aplicación

Copiado del Pad de referencia: ellos tienen **doce fondos completos**, uno por
pantalla, y por eso cada una se siente diseñada. Se piden siempre a partir del
base:

```
Same handheld device frame as before: identical body, identical left column
with its three empty slots, identical nameplate, identical proportions.

The ONLY difference is what is inside the big blue screen area:
<<AQUÍ LA DESCRIPCIÓN DE ESA PANTALLA>>

Everything outside the blue screen must be identical to the base.
```

**No los generes todos ahora.** Cada uno se pide cuando se vaya a construir esa
pantalla: si el base cambia, hay que rehacer los que ya existan.

---

## 4. Las celdas NO se generan: las dibuja el código

**No hay prompt para esto, y es a propósito.** Hubo uno, y las celdas generadas
se veían mal. Mirando el Pad de referencia se entendió por qué:

> En sus **111 texturas no hay ni una sola celda**. Su fondo llega con la
> pantalla **vacía**, y las celdas son rectángulos planos que pinta el código.

Una celda con bisel en relieve, estampada quince veces, es demasiado ruido: la
rejilla parecía una plancha de botones y tapaba los iconos. Planas y un tono
más claras que la pantalla, desaparecen y dejan ver lo único que importa.

Está en `PokePadScreen.celda()`, y son diez líneas.

---

## 5. Los iconos

### 5.1 · Por qué NO son pixel art

Se intentó, imitando al Pad de referencia, y falló por dos motivos distintos:

| | |
|---|---|
| **Una IA no dibuja pixel art** | Entrega una **ilustración encogida**. Con los mismos 625 píxeles, los suyos tienen 9-15 colores y contorno negro duro; los nuestros salían con 387-471 y sin contorno. Comparado ampliado ×13, no hay color |
| **No es la estética que se quiere** | El Pad de referencia es retro deliberado. Este servidor se quiere limpio, moderno y amable |

Así que los iconos son **HD y pulidos**: 100 × 100, sombreado suave, formas
redondeadas. La pizca de Minecraft va en **la forma** —caras cúbicas, bloques—,
no en los píxeles.

### 5.2 · La plantilla

```
A polished modern game menu icon of A_QUI_VA_EL_OBJETO, clean and colorful,
with smooth soft shading, rounded friendly shapes and a bold dark outline
around the silhouette. Semi-realistic stylized look, like a high quality
mobile game icon. Subtle Minecraft influence: some forms are built from soft
cubes and slabs with visible flat facets, but it is NOT pixel art and NOT
made of visible pixels; edges are smooth. Bright, cheerful, family friendly,
inviting. The object fills almost the entire square and nearly touches the
edges, lit from the top left, resting on a soft dark blue ellipse used as a
ground shadow. Plain solid black background. No text, no letters, no
numbers, no labels, no watermark, no border, no user interface, no document,
no color swatches.
```

Del segundo en adelante, añadir al final:
`Same style, outline weight, lighting and ground shadow as the previous icon.`

### 5.3 · Los quince objetos

Menos medieval que la tanda anterior: la tienda deja de ser un puesto de
mercado y el clan deja de ser un estandarte de guerra.

> **Los personajes se DESCRIBEN, no se nombran.** «a cute chubby yellow
> electric mouse creature with long black-tipped ears» funciona mejor que
> escribir el nombre: muchos generadores devuelven algo genérico —o se
> niegan— ante una marca registrada, y describiendo se controla la pose, la
> expresión y el encuadre, que es justo lo que hace falta para que los
> quince parezcan una familia. Como efecto secundario, el dibujo es nuestro.

| # | Fichero | Objeto |
|---|---|---|
| 1 | `pokedex` | `a sleek modern handheld Pokedex device seen from the front, dark grey body with rounded corners, a glowing screen and a big round red and white Poke Ball button` |
| 2 | `cosmeticos` | `a cute chubby yellow electric mouse creature with long black-tipped ears, round red cheeks and a lightning-bolt tail, wearing stylish sunglasses and a backwards cap, smiling and relaxed, with one small sparkle beside it` |
| 3 | `trabajos` | `a shiny iron pickaxe and a fishing rod crossed in an X, wooden handles, a tiny Poke Ball hanging from the fishing line` |
| 4 | `misiones` | `a modern clipboard with a checklist and one green check mark, and a small Poke Ball clipped to its top` |
| 5 | `warps` | `a majestic blue and white bird-like creature with a long flowing ribbon tail and wide spread feathered wings, seen in flight from the side, soaring above a small rounded floating map fragment with a glowing red location pin on it` **(v2)** |
| 6 | `clan` | `a rounded shield emblem with a Poke Ball in the center and two small wings at its sides, friendly and modern, not warlike` |
| 7 | `gts` | `two Poke Balls facing each other with a bright green emerald floating between them and a smooth circular exchange arrow around all three` |
| 8 | `tienda` | `a small friendly shop building with a bright blue tiled roof and a red and white striped awning over its open counter, a big round red and white creature-catching ball sign hanging above the doorway, and a small stack of gold coins resting on the counter` **(v2)** |
| 9 | `tesoros` | `an open wooden treasure chest with golden fittings and a glowing golden Poke Ball rising out of it with two sparkles` |
| 10 | `wiki` | `a big open illustrated field guide book with thick rounded covers and gilded page edges, the left page showing a small drawn portrait of a round friendly creature and the right page a simple sketched map, a red and white creature-catching ball resting on the spine as a bookmark. The pages contain only drawings, never any writing` **(v2)** |
| 11 | `cazas` | `a small orange fox-like creature with pointed ears, big friendly eyes and a thick curled bushy tail, crouching alert in a tuft of tall green grass, framed inside a glowing round targeting reticle with four short crosshair marks around its rim` **(v2)** |
| 12 | `kits` | `a bright gift box with a wide ribbon and bow, and a small golden crown resting on the lid` |
| 13 | `mochila` | `a modern rounded backpack seen from the front with padded straps and a red and white Poke Ball as the front clasp` |
| 14 | `gyms` | `a shiny enamel gym badge shaped like a bold eight-pointed star, thick gold rim, deep crimson and orange enamel facets and a small bright cyan gem at its center, two small white feathered wings spread at its sides, floating just above two smaller badges stacked beneath it` **(v2)** |
| 15 | `explorar` | `a small floating island made from a Minecraft grass block, green grass on top, brown dirt below and a winding stone path across it, with a tiny red and white creature-catching ball on the path and a small green leaf-tailed creature peeking over the edge, one white cloud beside it` **(v2)** |


> **Seis se rehicieron el 2026-08-16** (marcados **v2**): viajes, tienda, wiki,
> cazas, gimnasios y explorar. El motivo lo dio el usuario: *no se parecían al
> estilo Pokémon*. Y tenía razón — eran objetos correctos pero **genéricos**: un
> pin de mapa, una bolsa de la compra, una estantería, una diana, una medalla y
> un bloque de hierba. Ninguno traía una criatura ni un guiño al mundo.
>
> Lo que cambia en los seis es lo mismo: **entra el mundo Pokémon en el propio
> objeto**. La diana pasa a tener una criatura agazapada dentro; la estantería
> pasa a ser una guía de campo abierta con la ficha de una criatura; la bolsa
> pasa a ser una tienda con su toldo y su bola colgada; la medalla pasa a ser
> una **medalla de gimnasio** de ocho puntas con alas; el pin pasa a ser una
> criatura voladora sobrevolando el mapa; y el bloque de hierba gana un
> caminito, una bola y una criatura asomándose.
>
> Prompts montados y listos para pegar en
> [`build/pokepad/prompts-iconos-v2.txt`](../../build/pokepad/prompts-iconos-v2.txt).

> ⚠️ **Los nombres de fichero de esta tabla son los REALES**, y tres no son los
> obvios: `warps` (no «transportes»), `gyms` (no «gimnasios») y `kits` (no
> «lotes»). El generador los busca por ese nombre exacto y **aborta si falta
> uno** — un hueco en la pantalla principal es peor que no generar.

> **El tamaño ya da igual mientras sea cuadrado.** `a_tamano()` reduce desde
> cualquier resolución con Lanczos sobre alfa premultiplicado, así que el arte
> puede entrar tal y como salga del generador (1024 × 1024 normalmente). Antes
> había que reescalarlo a 100 a mano, y ese paso manual es justo donde se cuela
> un icono a 1023 o reescalado con el filtro equivocado. Ampliar sigue exigiendo
> factor entero: eso es para el pixel art, que sí tiene rejilla que respetar.

---

## 6. Los botones

Seis: atrás, adelante, ajustes, inicio, más y cerrar. Van en **120 × 96**.

> **Aquí decía 128 × 96 y se cambió al recibirlos.** El motivo del 128 era que
> 4:3 es un formato que los generadores ofrecen de fábrica; el generador
> entregó los seis en **120 × 96**, que es 5:4, así que ese argumento se cayó
> solo. Y el número que de verdad importa se cumple igual: **120 y 96 son
> divisibles entre 1, 2, 3, 4 y 6**, los valores del *GUI Scale*, así que a
> tamaño real un texel sigue cayendo en un píxel. Cambiar el número salía más
> barato que regenerar seis imágenes correctas.

**Un botón no es un icono pequeño**, y pedirlo igual da objetos con sombra
flotando:

| | Icono | Botón |
|---|---|---|
| Qué es | un objeto | un **marco** con un símbolo dentro |
| Sombra | elipse debajo | **ninguna**: no está apoyado, está empotrado |
| Silueta | llena el cuadro | el símbolo ocupa la mitad, el resto es marco |

```
A polished modern user interface button, slightly wider than tall, with a
rounded rectangular graphite frame, a soft bevel and a lighter rim along its
top and left side, and centered inside it a clean EL_SIMBOLO in pale blue.
Smooth shading, bold dark outline, no visible pixels. The symbol is chunky,
centered and takes up about half the button. Plain solid black background.
No text, no letters, no numbers, no labels, no watermark, no document.
```

| Fichero | Símbolo |
|---|---|
| `atras` | `left-pointing solid triangle arrow` |
| `adelante` | `right-pointing solid triangle arrow` |
| `ajustes` | `six-toothed gear wheel` |
| `inicio` | `simple house silhouette with a pointed roof` |
| `mas` | `thick plus sign` |
| `cerrar` | `thick X cross in pale blue on a RED button face; the cross must contrast strongly against the red` |

> **`cerrar` es el único en rojo**, y por eso va escrito en su línea: en la
> plantilla saldrían los seis rojos.

---

## 7. Qué hacer con lo que llegue

1. **Revisar**: ¿tiene letras? ¿la proporción es la de §1? ¿fondo transparente
   o negro plano? Si falla algo, regenerar — retocar a mano sale más caro.
2. Dejarlo en `arte/pokepad/` (`fondo_base.png`, `icons/`, `botones/`).
3. `python tools/gen_pokepad.py --maqueta` — quita fondos opacos, endurece el
   alfa, **mide la composición** y monta una maqueta para mirarla.
4. Copiar a `PokePadScreen` las medidas que imprime.

---

## 8. La composición, medida

Los números que congelan la pantalla principal. **No se escriben a ojo**: los
imprime `tools/gen_pokepad.py` al preparar el arte y se copian a
`PokePadScreen`. Están en **píxeles del arte**, sobre el chasis de 1380 × 828.

```
pantalla clara  x 460-1260   y 204-698    (800 x 494)
REJ_X = 502     REJ_Y = 236
CELDA = 124     HUECO_X = 24   HUECO_Y = 19   ICONO = 100
```

Y las tres ranuras del panel izquierdo, para lo que dibuja el código encima:

```
cara      x 114-322   y 115-324   hueco util 181 x 182   ← chasis v4
botones   x  80-356   y 360-595   hueco util 249 x 208
saldo     x  80-287   y 624-719   hueco util 180 x  68
placa     LLEVA EL LOGO. No se escribe nada encima (ver aviso)
cuadrito  x 302-349   y 651-698   (48 x 48)   LIBRE, no aloja nada
```

```
CARA_X = 134   CARA_Y = 136   CARA_LADO = 168
SALDO_CX = 184 SALDO_CY = 672
BOTON = {       x, y, ancho, alto — en el orden de BOTONES
    { 618, 698, 45, 36},   atras      mitad izquierda del bisel de abajo
    {1048, 698, 45, 36},   adelante   mitad derecha
    { 178, 376, 80, 64},   inicio     apilados en la ranura mediana
    { 178, 446, 80, 64},   ajustes
    { 178, 516, 80, 64},   mas
    {1207,  85, 80, 64},   cerrar     arriba a la derecha, junto al logo
}
```

### 8.1 · ⚠️ El v4 invierte la pantalla, y con ella todo lo de dentro

La pantalla pasó de **azul oscuro a casi blanca** (`226,235,253`). No es un
retoque de tono: cada decisión de contraste apuntaba al revés.

| | v3 (pantalla azul) | v4 (pantalla clara) |
|---|---|---|
| Celdas | más **claras** que el fondo | más **oscuras** |
| Nombres | **blancos** con contorno negro | **oscuros** con contorno claro |
| Resalte del ratón | el ámbar del chasis `F3B146` | el **naranja fuerte** `F35C0C` |

> **Los nombres oscuros no contradicen la decisión del usuario, la cumplen.** Lo
> que pidió fue *«que se lean»*, y blanco sobre blanco no se lee con contorno ni
> sin él. Por eso el color y su contorno son **dos constantes**: cambiaron de
> golpe al llegar el v4, y si el negro estuviera escrito a mano dentro de la
> función se habría quedado ahí.

### 8.2 · Los botones dejan de ser una barra, y de vivir juntos

En el v3 iban los seis en fila, en la única franja libre que quedaba —981 × 58
debajo de la pantalla— y a 60 × 48 porque no cabía más. Era un apaño, y se
decía aquí mismo.

**El v4 no tiene esa franja** y en cambio tiene tres sitios buenos, así que
—decisión del usuario— se reparten:

| | Dónde | Tamaño | Por qué ese tamaño |
|---|---|---|---|
| `atras` · `adelante` | en el **bisel naranja de abajo**, uno en cada mitad | 45 × 36 | La banda mide **37 px de alto medidos**. Es lo que cabe sin invadir ni la pantalla ni el chasis |
| `cerrar` | **arriba a la derecha**, en el panel oscuro junto al logo | 80 × 64 | Ese hueco mide 360 × 93; sobra sitio |
| `inicio` · `ajustes` · `mas` | apilados en la **ranura mediana** | 80 × 64 | Tres de 64 y dos huecos de 6 llenan sus 208 de alto |

> **La escala de la banda ya la proponía el arte.** 45 × 36 es casi exactamente
> lo que medía la carita verde que había justo ahí (48 px con su halo), así que
> los botones ocupan el sitio que el diseñador ya había reservado visualmente.

> **Las posiciones evitan los bigotes sin que nadie lo pida.** Están medidos en
> `x 722-778` y `x 932-987`; centrar cada botón en su mitad de la banda los deja
> en 618 y 1048, fuera de los dos. Si el chasis cambia, se vuelve a medir y se
> vuelven a colocar solos.

### 8.3 · La carita verde se borra en el generador

Decisión del usuario. `quitar_carita()` lo hace en `gen_pokepad.py`, y merece la
pena decir por qué ahí y no pidiendo otro fondo al diseñador: es un parche de
sesenta píxeles sobre una banda **horizontalmente uniforme**, así que se rellena
con el color de las columnas limpias de al lado, fila a fila. No hay que
inventarse ni un píxel — que es lo que convierte un retoque en un parche que se
nota.

> **El primer intento copiaba un bloque de al lado y salió mal de forma
> instructiva:** a los lados de la carita están los **bigotes**, así que el
> trozo donante los traía consigo y donde había una cara aparecían dos bigotes
> de más. La banda es uniforme *en horizontal*, no en vertical: lo que se puede
> copiar es el color de la fila, no un rectángulo.

> Y es **idempotente**: si algún día llega un chasis que ya no la trae, la
> función no encuentra verde y devuelve la imagen tal cual.

> `gen_pokepad.py` los guarda **ya reducidos** para que se dibujen 1:1 en vez de
> dejar que los encoja el juego (regla 2 de [dibujado.md](dibujado.md)).

> **Cinco de los seis se dibujan apagados**, porque son navegación y todavía no
> hay a dónde ir. Suenan a bloqueado, igual que las quince celdas. Un botón
> apagado que responde «todavía no» informa; uno de aspecto normal que no hace
> nada enseña a no pulsar los botones.

> ⚠️ **`atras` llegó defectuoso y hoy sale de `adelante` volteado.** Traía un
> **halo claro semitransparente** —se veía como un fondo blanco detrás del
> botón— y la **elipse de sombra de los iconos** (616 px). Medido contra
> `adelante`, que sí está limpio:
>
> ```
>              alfa 1-23        alfa 24-60
> atras        (112,129,130)    (183,191,204)   ← halo claro
> adelante     (  1,  1,  1)    ( 13, 14, 18)
> ```
>
> Las dos cosas las prohíbe §6: un botón es un marco empotrado, no un objeto
> apoyado. Como `adelante` es literalmente el mismo botón con la flecha al otro
> lado, se voltea y el estilo cuadra por construcción. Lo único que cambia es
> que el brillo del bisel queda a la derecha, y a 60 × 48 no se aprecia.
>
> Si algún día se regenera de verdad, el prompt de §6 ya lleva añadida la línea
> `No shadow under the button.`

## Next Actions

1. ~~El **chasis** a 1380 × 828~~ ✅ recibido, medido y congelado (§8)
2. ~~Los **quince iconos** a 100 × 100~~ ✅ los quince
3. ~~Los **seis botones**~~ ✅ los seis, colocados en la barra de abajo
4. **La primera sub-pantalla.** Necesita su fondo, que se pide con el prompt de
   §3.2 partiendo del chasis base — y es ahí donde se encienden los botones de
   navegación, que hoy van apagados por no tener destino

## Related Systems

- [La interfaz de cliente](interfaz-cliente.md) · [El PokePad de referencia](pokepad-referencia.md)
