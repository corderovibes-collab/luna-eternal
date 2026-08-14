# Prompts para el arte del PokePad

## Purpose

Los prompts que se le dan a Gemini Pro para generar el arte de la pantalla
principal del PokePad, y las condiciones que el arte tiene que cumplir para ser
utilizable.

## Dependencies

- [`interfaz-cliente.md`](interfaz-cliente.md) §4 — qué piezas hacen falta
- [`pokepad-referencia.md`](pokepad-referencia.md) §1 — de dónde salen las medidas

## Current Status

`FASE 1 de N` — solo la **pantalla principal**. Las pantallas de cada app
(GTS, Tienda, Cazas…) vienen después, con estos mismos prompts adaptados.

---

## 1. Léete esto antes de generar nada

Cuatro cosas. Si el arte falla una, no sirve y hay que regenerarlo.

### 1.0 · La tabla, para no tener que pensar

**Lo que le pides a Gemini es una PROPORCIÓN, no una resolución.** Genera lo
más grande que te deje; del tamaño final me encargo yo.

| Pieza | En Gemini pides | Sale de ahí | Fichero final |
|---|---|---|---|
| **Chasis** | **16:9**, máxima resolución | 1 imagen | **368 × 207** |
| **Celdas** | **16:9** | 3 en fila | **44 × 44** cada una |
| **Iconos** | **1:1** | rejilla 5×3 | **24 × 24** cada uno |
| **Botones** | **16:9** | 6 en fila | 16 × 16 cada uno |

Nada más. Si Gemini te da 1920×1080 para el chasis, perfecto. Si te da
1408×792, también. **Lo único que no puede cambiar es la proporción**, porque
es lo que evita que haya que recortar a ojo y descuadrarlo todo.

### 1.1 · De dónde sale el 368 × 207, y por qué no es 346

El PokePad de referencia usa **346 × 207**, medido en sus PNG. Funciona y se ve
nítido, así que **la altura se conserva**: 207.

Pero 346 × 207 es proporción 5:3, y **ningún generador de imágenes ofrece 5:3**.
Pedirlo en 16:9 y recortar a mano es exactamente como se descuadra un chasis.

```
207 × 16 ÷ 9 = 368       ->   368 × 207 es 16:9 EXACTO
```

Seis por ciento más ancho que el de referencia, misma altura, y **se genera y
se reduce sin recortar ni un píxel**. Los iconos pasan de 24×25 a **24 × 24**
por lo mismo: cuadrado es 1:1, que sí es un preset.

> El tamaño final es cosa nuestra, no de nadie: 346 era **su** número, no una
> ley. Se elige el que hace el trabajo más fácil sin perder calidad.

### 1.2 · Por qué el tamaño final es tan pequeño

La pantalla entera son **368 × 207 píxeles de interfaz**. No 1024. No 2048.

> **⚠️ Una captura de pantalla NO sirve para medir esto, y engaña mucho.**
> Minecraft dibuja las interfaces **escaladas** por el ajuste *GUI Scale*: a
> escala 3, una textura de 346 px ocupa 1 038 px en pantalla. Por eso una
> captura de ~1 100 px de ancho es perfectamente coherente con una textura de
> 346.
>
> El número está **medido sobre los ficheros PNG** del mod de referencia, no
> sobre una captura:
>
> ```
> 346 x 207   12 texturas   los fondos completos, uno por app
>  24 x 25    23 texturas   los iconos de app
>  24 x 17    38 texturas   placas y botones pequeños
> ```
>
> Si algún día hay que rehacer esta medición, se hace así: abrir el jar y leer
> el tamaño de los PNG. Nunca contar píxeles en una captura.

```
tú generas    16:9, lo más grande que te deje Gemini
yo reduzco    368 × 207   con vecino más próximo, nunca bicúbico
```

Un generador de imágenes no sabe dibujar a 368 px de ancho: le sale una mancha.
Por eso se genera grande y se reduce. Y para que reducir no lo convierta en
papilla, el arte tiene que ser **pixel art de verdad** —bloques planos, sin
degradados y sin suavizado—, que es lo que piden todos los prompts con
`crisp pixel art`, `no anti-aliasing`, `flat color blocks`.

### 1.3 · Sin una sola letra

**El texto lo dibuja el juego**, no la textura. Está traducido, y un jugador en
inglés vería «Cazas» pintado a fuego.

Todos los prompts llevan `absolutely no text, no letters, no numbers, no
labels`. Si vuelve con letras, se regenera. **No vale borrarlas a mano**: el
hueco tiene que estar pensado desde el principio.

### 1.4 · La composición no se mueve

El código mide **una sola vez** dónde cae el área de contenido. Si un fondo la
deja más arriba o más estrecha, la rejilla no encaja y hay que medir a mano
pantalla por pantalla.

Por eso la fase 1 genera **el chasis vacío**, se congela, y todo lo demás se
dibuja encima respetándolo.

---

## 2. La biblia de estilo

**Esto va pegado al principio de CADA prompt.** Es lo único que consigue que
quince iconos generados por separado parezcan de la misma mano.

```
STYLE BIBLE — paste this before every prompt

Art style: chunky pixel art in the visual language of Minecraft crossed with
classic Pokémon handheld UI. Think "PokeCraft": blocky, tactile, readable.

Rules, all mandatory:
- Crisp pixel art. Flat blocks of color. Hard pixel edges.
- NO anti-aliasing, NO gradients, NO blur, NO glow, NO soft shadows,
  NO lens flare, NO 3D rendering, NO photorealism.
- Maximum 3 tones per color: base, shadow, highlight. Nothing more.
- Bold dark outlines (1 pixel at final scale) around every separate shape.
- Straight-on orthographic view. No perspective, no vanishing point.
- ABSOLUTELY NO TEXT, no letters, no numbers, no labels, no watermarks.
- Pure black background (#000000) unless the prompt says otherwise, so it
  can be keyed out cleanly.

Color palette — use ONLY these, they are the server's identity:
  #1B1E26  graphite      the device shell, dark chrome
  #2A2E3A  graphite soft shell shading
  #8B93D8  moon blue     the screen, main panels
  #6C78C8  moon blue dk  screen shading
  #C8D2F0  moon light    screen highlights, rims
  #16F2E6  neon cyan     accents, active state, glow lines
  #FF34DF  neon magenta  secondary accent, alerts
  #9A34FF  neon purple   tertiary accent
  #F2FAFF  cold white    text areas, brightest highlights
  #FF2A3C  neon red      danger, close button ONLY

Mood: a handheld device from a neon city at permanent night. Cold, calm,
premium. Not toy-like, not cartoonish, not glossy plastic.
```

> **Por qué esta paleta y no la roja del PokePad de referencia:** el servidor ya
> tiene identidad desplegada —96 bloques de neón, 323 texturas de interfaz en
> azul luna, el haz de la Poké Ball—. Un Pad rojo chillón desentonaría con todo
> lo demás. Si prefieres el rojo, se cambia `#1B1E26` por `#FF2A3C` en la
> paleta y se regenera todo: por eso está en un solo sitio.

---

## 3. El sistema de chasis: uno por aplicación

**Copiado del Pad de referencia, porque es lo que hace que cada pantalla se
sienta diseñada.** Ellos no tienen un marco genérico con contenido dentro:
tienen **doce fondos completos de 346×207**, uno por aplicación.

```
pokepad_base_new.png    la rejilla de aplicaciones
pokepad_gts.png         el GTS, entero
pokepad_cazas.png       las Cazas, enteras
pokepad_tienda.png      ...una por app
```

Nosotros teníamos un solo fondo compartido, y por eso todas nuestras pantallas
se parecían.

### 3.1 · La regla que no se puede romper

> **La composición tiene que ser IDÉNTICA en todos los fondos.** El código mide
> una sola vez dónde cae el área de contenido. Si en un fondo queda más arriba
> o más estrecha, la rejilla no encaja y hay que medir a mano pantalla por
> pantalla.

Por eso el fondo base **se genera primero y se congela**, y los demás se piden
como variantes suyas, no desde cero.

### 3.2 · El fondo base

```
[PEGA AQUÍ LA BIBLIA DE ESTILO]

Subject: a handheld gaming device UI frame, seen straight on, filling the
whole canvas. Canvas aspect ratio 5:3.

Pixel art on a grid: chunky uniform square pixels, hard edges, maximum 22
colors in the whole image, no gradients and no anti-aliasing.

Layout, exactly:
- Outer shell in graphite (#1B1E26) with soft graphite (#2A2E3A) bevels,
  rounded corners, like a rugged handheld console.
- Thin neon cyan (#16F2E6) accent lines inset along the shell edges.
- LEFT COLUMN, about 25% of the width: three stacked EMPTY recessed slots -
  a square one on top for an avatar, a wide one in the middle for a rank
  card, a short wide one at the bottom with a small square button beside it.
  All EMPTY: just the recess and its inner shadow.
- RIGHT AREA, the remaining 75%: one single large recessed screen in moon
  blue (#8B93D8) with a #C8D2F0 rim. COMPLETELY EMPTY and perfectly
  rectangular. No grid, no cells, no icons inside it.
- TOP CENTER: an empty raised nameplate shape. Empty, no text.
```

> **La pantalla vacía no es pereza, es la arquitectura.** Las celdas las dibuja
> el código como rectángulos planos —igual que ellos—, y por eso el fondo no
> las lleva pintadas. Ver §4.

### 3.3 · Los fondos de cada aplicación

Se piden **uno a uno**, y siempre a partir del base:

```
[PEGA AQUÍ LA BIBLIA DE ESTILO]

Same handheld device frame as before: identical shell, identical left
column with its three empty slots, identical nameplate, identical size and
proportions. Pixel art, same palette, maximum 22 colors.

The ONLY difference is what is inside the big blue screen area:

<<AQUÍ LA DESCRIPCIÓN DE ESA PANTALLA>>

Everything outside the blue screen must be pixel-identical to the base.
```

Y en `<<...>>` va lo que pide cada una. Por ejemplo:

| Pantalla | Qué lleva dentro |
|---|---|
| **Pokédex** | `a vertical list area on the left with empty rows, and a large empty square preview panel on the right` |
| **GTS** | `a grid of empty listing slots, with an empty wide search bar across the top` |
| **Tienda** | `two columns of empty item rows, and an empty wide banner across the top` |
| **Cazas** | `three tall empty panels side by side, each with an empty circle at its top` |

> **No las generes todas ahora.** Cada fondo se pide cuando se va a construir
> esa pantalla, no antes: si el base cambia, hay que rehacer todos los que ya
> existieran.

### 3.4 · La resolución, ya para siempre

```
tú pides    proporción 5:3, pixel art de rejilla, máximo 22 colores
yo reduzco  345 × 207
```

Un generador no dibuja a 345 px de ancho —sale una mancha—, así que se genera
grande y se reduce. **Lo que hace que la reducción salga bien no es el tamaño,
es que el original ya sea pixel art**: bloques planos y bordes duros
sobreviven a reducirse; los degradados y el suavizado, no.

## 4. Las celdas NO se generan: las dibuja el código

**No hay prompt para esto, y es a propósito.** Aquí había uno pidiendo las tres
celdas —reposo, encima, bloqueada— y se generaron. En el juego se veían mal, y
mirando el Pad de referencia se entendió por qué:

> En sus **111 texturas no hay ni una sola celda**. Su fondo llega con la
> pantalla **vacía**, y las celdas son rectángulos planos que pinta el código.

Una celda con bisel en relieve, estampada quince veces en huecos de 37 píxeles,
es demasiado ruido: la rejilla parecía una plancha de botones y tapaba los
iconos. Planas y de un tono más claras que la pantalla, desaparecen y dejan ver
lo único que importa, que es el icono.

Y sale gratis lo que con textura costaba: los tres estados son tres colores, no
tres imágenes que hay que mantener idénticas en silueta.

```java
reposo     relleno #7A83C8   borde #C8D2F0
encima     relleno #9AA3E8   borde #16F2E6   (cian, para que se vea el foco)
cerrada    la misma celda, con el ICONO apagado
```

> **Las aplicaciones cerradas no llevan candado.** Quince candados en una
> rejilla de quince son más ruido que información, y encima tapan el icono que
> intentan describir. Se distinguen con el icono apagado.

Está en `PokePadScreen.celda()`, y son diez líneas: un rectángulo, otro dentro,
y las cuatro esquinas mordidas —que es como se redondea en pixel art—.

## 5. Los iconos: uno a uno, y nacidos como pixel art

**Cambio de método, y el motivo está medido.** Los primeros salieron de una
hoja de 5×3 y se veían sucios en el juego. Comparando con el Pad de
referencia:

| | Ellos | La primera tanda nuestra |
|---|---|---|
| Colores por icono | **5 a 20** | 2 750 |
| Cómo nacieron | dibujados a 24×25 | ilustración grande, reducida ×22 |
| Sombra | una elipse oscura, **igual en los 23** | ninguna |
| Silueta | llena el cuadro | con aire alrededor, se ve pequeño |

Reducir una ilustración de 2 750 colores a 24 píxeles no la convierte en pixel
art: la emborrona. Hay que pedir pixel art **desde el principio**.

### 5.1 · ⚠️ El prompt va en PROSA, no en documento

La primera versión estaba escrita con títulos, listas y una tabla de colores.
**Gemini dibujó el documento**: devolvió una imagen del propio prompt, con el
icono metido en una esquina.

No es un capricho del modelo. Un generador de imágenes no distingue entre «esto
son mis instrucciones» y «esto es lo que quiero ver»: todo lo que le llega es
descripción. Si le mandas algo con pinta de ficha técnica, dibuja una ficha
técnica. Peor aún, aquella llevaba la línea *«paste this before every single
icon prompt»*, que es una instrucción **para la persona**, y también salió
pintada.

Las cuatro reglas que lo evitan:

| | |
|---|---|
| **El sujeto, primero** | Lo que va al principio es lo que ancla la imagen. Nunca empezar por las normas |
| **Prosa, no listas** | Ni títulos, ni viñetas, ni tablas, ni cabeceras en mayúsculas |
| **Pocos códigos de color** | Una lista de catorce hex se dibuja como lista. Se nombran los colores y se citan dos o tres hex sueltos |
| **Nada dirigido a ti** | «pega esto antes de…», «opcional», «ver §4» — todo eso acaba en la imagen |

### 5.2 · La plantilla

Se cambia solo lo que va después de *«pixel art game icon of»*. Todo lo demás
se repite igual en los quince, y es lo que los hace familia.

```
A 24x24 pixel art game icon of A_QUI_VA_EL_OBJETO, drawn with chunky
uniform square pixels aligned to a coarse grid, hard edges, no
anti-aliasing and no gradients. Flat limited palette of at most 14 colors
with a single dark outline (#1B1E26), one base tone, one shadow tone and
one highlight. The object fills almost the entire square and nearly
touches the edges, lit from the top left, sitting on a flat dark blue
ellipse (#2A3A5E) used as a ground shadow. Plain solid black background.
No text, no letters, no numbers, no labels, no watermark, no border, no
user interface, no document, no color swatches.
```

> Ese final —`no document, no color swatches`— está puesto a mano porque es
> exactamente lo que se equivocó la primera vez. Vale la pena dejarlo.

### 5.3 · Los quince objetos

Cada uno sustituye a `A_QUI_VA_EL_OBJETO`. A partir del segundo, añadir al
final: `Same style, outline weight, palette and ground shadow as the previous
icon.`

| # | Fichero | Objeto |
|---|---|---|
| 1 | `pokedex` | `a chunky closed handheld Pokedex device seen from the front, dark grey casing, with a big round red and white Poke Ball button on its face and two small lights above it` |
| 2 | `cosmeticos` | `a pale cyan Minecraft diamond helmet seen from the front, with a small red and white Poke Ball crest on the forehead and one yellow four-point sparkle` |
| 3 | `trabajos` | `a Minecraft iron pickaxe and a fishing rod crossed in an X, both with wooden handles, a tiny Poke Ball hanging from the fishing line` |
| 4 | `misiones` | `a closed purple book standing upright with golden corners, a red ribbon bookmark and a small Poke Ball emblem on the cover` |
| 5 | `warps` | `a rounded map pin marker colored red and white like a Poke Ball, standing on a small folded green map, with a purple ender pearl floating above it` |
| 6 | `clan` | `a hanging moon blue cloth banner with a pointed bottom on a short wooden pole, a Poke Ball emblem stitched in the middle` |
| 7 | `gts` | `two Poke Balls facing each other with a bright green emerald floating between them and a simple circular arrow looping around all three` |
| 8 | `tienda` | `a small wooden market stall seen from the front with a red and white striped awning and a round Poke Ball sign hanging under it` |
| 9 | `tesoros` | `an open wooden treasure chest with golden hinges and a glowing golden Poke Ball rising out of it with two yellow sparkles` |
| 10 | `wiki` | `a wooden bookshelf block seen from the front full of colorful book spines, with a chunky white question mark floating in front of it` |
| 11 | `cazas` | `a thick round dark metal crosshair ring with a chunky pale blue paw print centered inside it` |
| 12 | `kits` | `a wooden chest wrapped as a gift with a red ribbon and bow, and a small golden crown resting on the lid` |
| 13 | `mochila` | `a brown leather satchel backpack seen from the front with two straps, a flap, and a red and white Poke Ball as the front clasp` |
| 14 | `gyms` | `a small grey stone badge with two white feathered wings spreading from its sides and a golden shine on top` |
| 15 | `explorar` | `a Minecraft grass block turned into a small round planet, green grass on top and brown dirt below, with one small white cloud beside it` |

## 6. Prompt 4 — Los botones pequeños

```
[PEGA AQUÍ LA BIBLIA DE ESTILO]

Subject: a set of 6 small square UI buttons for a pixel-art game menu, in
one horizontal row on a pure black background, identical size and frame,
evenly spaced.

The frame of all six is identical: graphite (#2A2E3A) square with a
#8B93D8 inner face and a #C8D2F0 top-left rim.

The symbol inside each, drawn in #F2FAFF:
 1. a left-pointing triangle arrow
 2. a right-pointing triangle arrow
 3. a gear / cog with six teeth
 4. a simple house silhouette
 5. a plus sign, thick and blocky
 6. an X / close cross - this one and ONLY this one has a #FF2A3C face

Symbols must be centered, chunky and readable at tiny size.
```

---

## 7. Qué hacer con lo que llegue

1. **Revisar contra §1**: ¿tiene letras? ¿tiene degradados? ¿la proporción es
   la de la tabla de §1.0? Si falla algo, regenerar — retocar a mano sale más caro.
2. Mandármelo. Yo lo reduzco a las medidas reales, lo recorto en piezas y
   **mido la composición** sobre el chasis.
3. Con el chasis medido y congelado, se escribe el código de la pantalla
   principal (`mod/src/client/`).
4. Solo entonces, la fase siguiente: las pantallas de cada app.

> **No generes las 22 pantallas ahora.** El chasis es el que fija la rejilla;
> si cambia después, hay que rehacer todo lo que se dibujó encima.

## Next Actions

1. Generar el **chasis** (§3) y enviármelo — es lo único que bloquea el resto
2. Con el chasis aprobado, celdas (§4) e iconos (§5)
3. Medir la composición y volver a crear `mod/src/client/`

## Related Systems

- [La interfaz de cliente](interfaz-cliente.md) · [El PokePad de referencia](pokepad-referencia.md)
- [Catálogo de pantallas](interfaces-catalog.md)

---

## 8. La composición, CONGELADA

Medida sobre el arte real el 2026-08-13 con `python tools/gen_pokepad.py`. **El
código dibuja aquí y no vuelve a medir.**

```
pokepad.png     345 x 207     el chasis entero
pantalla azul   x 106-321  y 28-184        (215 x 156)
rejilla         x 113      y 46            (201 x 119)
celda           37 x 37    hueco de 4      5 columnas x 3 filas
icono           24 x 24    centrado en la celda
boton           16 x 16
```

Los números **no están escritos a mano**: el script localiza la pantalla azul
por color y calcula la celda a partir de ella. Si el chasis se regenera, se
reejecuta y salen los nuevos.

### Lo que costó, para no repetirlo

Trocear hojas de IA no es cortar por una rejilla: no la traen. Se localiza cada
pieza proyectando el contenido sobre cada eje, y ahí hubo tres trampas, las
tres descubiertas mirando la maqueta:

| Síntoma | Causa |
|---|---|
| Los iconos se llevaban su rótulo | Proyectar con `any()`: **basta un píxel** para que una fila cuente como llena, y dos o tres restos del suavizado puenteaban el hueco. Se arregló exigiendo un mínimo de píxeles por fila |
| Una celda salía partida en dos | El borde cian del estado «encima» deja un hueco interno. Se fusionan los tramos más cercanos hasta llegar al número esperado |
| Las celdas cogían una franja vacía | Se cogían las bandas pares dando por hecho «pieza, rótulo, pieza…». Esa hoja trae además una franja fina arriba. Ahora se cogen las bandas **más altas**, que son las piezas |

> **La maqueta no es un adorno.** `--maqueta` monta la pantalla completa con
> todo en su sitio, y las tres trampas se vieron ahí antes de escribir una sola
> línea de interfaz.
