# El arte del PokePad

## Purpose

Qué piezas hacen falta, a qué resolución, y los prompts exactos con los que se
generan.

## Dependencies

- [`interfaz-cliente.md`](interfaz-cliente.md) §4 — qué necesita cada pantalla
- [`pokepad-referencia.md`](pokepad-referencia.md) §1 — de dónde salen las medidas

## Current Status

`FASE 1` — la **pantalla principal**. Las pantallas de cada aplicación vienen
después (§3.3).

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
| 5 | `transportes` | `a glowing purple portal orb floating above a rounded location pin on a small map` |
| 6 | `clan` | `a rounded shield emblem with a Poke Ball in the center and two small wings at its sides, friendly and modern, not warlike` |
| 7 | `gts` | `two Poke Balls facing each other with a bright green emerald floating between them and a smooth circular exchange arrow around all three` |
| 8 | `tienda` | `a modern shopping bag with rounded handles and a Poke Ball printed on it, next to one small stack of gold coins` |
| 9 | `tesoros` | `an open wooden treasure chest with golden fittings and a glowing golden Poke Ball rising out of it with two sparkles` |
| 10 | `wiki` | `a modern bookshelf cube full of colorful books, with a big friendly rounded question mark floating in front of it` |
| 11 | `cazas` | `a smooth round targeting reticle with a soft blue paw print centered inside it` |
| 12 | `lotes` | `a bright gift box with a wide ribbon and bow, and a small golden crown resting on the lid` |
| 13 | `mochila` | `a modern rounded backpack seen from the front with padded straps and a red and white Poke Ball as the front clasp` |
| 14 | `gimnasios` | `a shiny rounded medal badge with two small white wings and a golden star, hanging from a short ribbon` |
| 15 | `explorar` | `a Minecraft grass block turned into a small round planet, green grass on top and brown dirt below, with one small white cloud beside it` |

---

## 6. Los botones

Seis: atrás, adelante, ajustes, inicio, más y cerrar. **Un botón no es un icono
pequeño**, y pedirlo igual da objetos con sombra flotando:

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

## Next Actions

1. El **chasis** a 1380 × 828 — es lo que bloquea el resto
2. Con él medido, los **quince iconos** a 100 × 100, uno a uno
3. Los **seis botones** a 120 × 96

## Related Systems

- [La interfaz de cliente](interfaz-cliente.md) · [El PokePad de referencia](pokepad-referencia.md)
