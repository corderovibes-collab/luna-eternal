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

## 3. Prompt 1 — El chasis

Lo primero y lo único de esta fase que hay que congelar. Es el aparato: marco,
panel lateral y el hueco donde irá la rejilla.

```
[PEGA AQUÍ LA BIBLIA DE ESTILO]

Subject: a handheld gaming device UI frame, seen straight on, filling the
whole canvas. Canvas aspect ratio 16:9.

Layout, exactly:
- Outer shell in graphite (#1B1E26) with soft graphite (#2A2E3A) bevels,
  like a rugged plastic-and-metal handheld console. Rounded corners.
- Thin neon cyan (#16F2E6) accent lines inset along the shell edges, like
  light strips on a device at night. Subtle, 1 pixel, not glowing.
- LEFT COLUMN, about 25% of the width: a vertical dark panel with three
  stacked EMPTY recessed slots:
    top    - a square slot, roughly 1:1, for a player avatar
    middle - a wide rectangular slot, for a rank badge card
    bottom - a short wide slot with a small square button on its right,
             for a currency readout
  All three slots must be EMPTY - just the recess, the frame and the inner
  shadow. Nothing inside them.
- RIGHT AREA, the remaining 75%: one single large recessed screen in moon
  blue (#8B93D8), with a #C8D2F0 rim and #6C78C8 inner shadow. It must be
  COMPLETELY EMPTY and perfectly rectangular - a flat blue panel. This is
  where the game will draw the app grid.
- TOP CENTER: an empty raised nameplate/banner shape spanning the top of
  the screen area, for a title. Empty, no text.

Critical: the empty screen area must be a clean rectangle with uniform
color, no decoration inside it, no grid lines, no icons.
```

**Qué mirar cuando llegue:** que el rectángulo azul esté **vacío y sea
rectangular**. Es lo que se mide una vez y condiciona todo lo demás.

---

## 4. Prompt 2 — La celda, en sus tres estados

La rejilla son celdas iguales repetidas. Hacen falta las tres, **y van juntas
en una fila a propósito**: ver el porqué justo debajo.

```
[PEGA AQUÍ LA BIBLIA DE ESTILO]

Subject: three UI button tiles for a pixel-art game menu, arranged in one
horizontal row on a pure black background, evenly spaced, identical size
and shape, square with slightly rounded corners.
Canvas aspect ratio 16:9.

All three are EMPTY containers - no icon inside, no symbol, no text.
They look gently recessed, like empty sockets waiting for an icon.

Tile 1 - RESTING: moon blue (#8B93D8) fill, #C8D2F0 top and left inner
  rim, #6C78C8 bottom and right inner shadow.
Tile 2 - HOVERED: same shape, slightly brighter fill, and a clean 1 pixel
  neon cyan (#16F2E6) border all around the outside.
Tile 3 - LOCKED: same shape, desaturated graphite (#2A2E3A) fill, dimmer,
  with a small closed padlock in the lower-right corner drawn in #6C78C8.
  The padlock is the only content allowed anywhere.

CRITICAL: the three tiles must be pixel-identical in silhouette, size,
corner rounding and bevel width. ONLY the fill color, the border and the
padlock change between them.
```

### Por qué en fila y no una imagen por celda

**Porque los tres estados son el mismo botón.** Solo cambia el color y el
borde; la forma tiene que ser idéntica píxel a píxel.

Si generas tres imágenes por separado, cada una sale con las esquinas, el
grosor del bisel y las proporciones un poco distintas. Como el juego cambia de
una a otra **en el mismo sitio de la pantalla**, al pasar el ratón por encima
el botón **daría un salto** — se estiraría o se movería un píxel. Es de esos
defectos que no sabes nombrar pero se ven al instante.

Generadas de una sola vez, salen de la misma «mano» y encajan.

### Y la resolución no se resiente, que era la única pega

Es la duda razonable: si tres celdas comparten lienzo, cada una se lleva un
tercio. Pero los números salen holgados:

```
lienzo 1024 de ancho ÷ 3 celdas   ≈  340 px por celda
tamaño final de una celda          =   44 px
                                       ~8 veces más de lo necesario
```

A 44 píxeles finales sobra muchísimo. **Lo que de verdad arruina un botón no es
la falta de detalle, es que baile.**

### ⚠️ Por qué 44 y no 32, que es lo que ponía aquí antes

Era un número puesto a ojo, y estaba **mal**. La cuenta que faltaba es que el
icono tiene que **caber dentro**:

```
celda de 32 px  −  contorno y bisel (~5 px por lado)  =  22 px libres
icono                                                  =  24 px
                                                          NO CABE
```

Con 44 px quedan unos 30 libres y el icono de 24 entra con margen. La rejilla
tampoco sufre: el área azul mide unos 270 px de ancho, y 5 columnas de 44 con
sus separaciones caben de sobra.

> **Dato de la referencia:** ellos **no tienen textura de celda**. Las celdas
> van pintadas dentro del fondo de cada pantalla, y por eso cada app es una
> ilustración entera. Nosotros la separamos a propósito: una textura dibujada
> quince veces permite cambiar la lista de apps sin repintar el fondo.

> **Red de seguridad:** si aun así las tres vuelven desiguales, no hace falta
> regenerar. Me quedo con la de reposo y **derivo las otras dos** —más clara
> con borde cian, y desaturada con candado—, que es exactamente lo que ya se
> hizo con los 96 bloques de neón y las 323 texturas de interfaz: una fuente,
> variantes generadas.

---

## 5. Prompt 3 — Los quince iconos

**La pieza donde se ve el estilo «PokeCraft»**: cada icono mezcla a la vista un
objeto de Minecraft con algo de Pokémon. Y son **más amigables** que el resto
de la interfaz a propósito — el chasis es un aparato serio, los iconos son lo
que invita a tocarlo.

> ⚠️ **Este prompt CAMBIA una línea de la biblia de estilo.** La biblia dice
> *«Not toy-like, not cartoonish»*, que es lo correcto para el chasis pero
> pelea con unos iconos amigables. El bloque `MOOD OVERRIDE` de abajo la
> sustituye. Si no lo pegas, salen fríos y técnicos.

```
[PEGA AQUÍ LA BIBLIA DE ESTILO]

MOOD OVERRIDE for these icons only: replace the "Mood" line of the style
bible with this one. Friendly, warm, inviting, collectible. Rounded chunky
shapes, generous thick outlines, a little bit of charm and bounce - like
enamel pins or sticker art. Still pixel art, still the same palette, but
welcoming instead of cold. Cute is good here.

PALETTE ADDITION for these icons only: you may also use warm accents
  #FFE12E  neon yellow    gold, shine, sparkles
  #FF7A14  neon orange    wood, leather, fire
  #1EDF5C  neon green     nature, grass
  #FF2A3C  neon red       Poke Ball tops, ribbons

Subject: a sprite sheet of 15 game menu icons, laid out in a strict grid of
5 columns by 3 rows on a pure black background. Even spacing. Every icon
occupies the same square area and is centered in its cell.

Each icon: ONE single object, bold silhouette, no scene, no background, no
frame around it, no drop shadow. It must read instantly at very small size.

THE FUSION RULE - this is the point of the whole set: every icon combines
a recognizable MINECRAFT object with a recognizable POKEMON element. Not
one or the other. Both, in the same object.

The icons, in order, left to right, top to bottom:
 1. Pokedex     - a chunky blocky handheld dex, closed, with a round red
                  and white Poke Ball style button on its face
 2. Cosmetics   - a Minecraft diamond helmet with a small Poke Ball crest
                  on the forehead and one yellow sparkle
 3. Jobs        - a Minecraft iron pickaxe crossed with a fishing rod,
                  wooden handles, a tiny Poke Ball hanging from the line
 4. Quests      - a Minecraft enchanted book, slightly open, with a Poke
                  Ball used as the bookmark and a warm glow between pages
 5. Warps       - a Minecraft ender pearl floating above a rounded map pin
 6. Clan        - a Minecraft cloth banner on a wooden pole, with a simple
                  Poke Ball emblem stitched on it
 7. GTS         - two Poke Balls facing each other with a green Minecraft
                  emerald between them and a soft circular exchange arrow
 8. Shop        - a small wooden market stall with a striped awning and a
                  round Poke Ball sign hanging from it
 9. Treasures   - a Minecraft wooden treasure chest, lid slightly open,
                  a golden Poke Ball glowing inside
10. Wiki        - a Minecraft bookshelf block with a friendly rounded
                  question mark floating in front of it
11. Hunts       - a chunky rounded paw print inside a simple crosshair ring
12. Kits        - a Minecraft chest wrapped like a gift with a ribbon, a
                  small golden crown resting on the lid
13. Backpack    - a rounded leather satchel with straps and a Poke Ball
                  as the front clasp
14. Gyms        - a badge shaped like a Minecraft block, with small wings
                  and a golden shine
15. Explore     - a Minecraft grass block turned into a little round
                  planet, green top, dirt below, one tiny cloud

Consistency is more important than detail: same outline weight, same light
direction (top-left), same level of detail, same visual weight, same amount
of rounding. They must look like one family, not fifteen drawings.
```

> **Truco si salen desiguales:** genera **uno por uno** reusando la biblia + el
> `MOOD OVERRIDE`, y añade *"in the exact same style, outline weight and level
> of detail as the previous icon"*. Es más lento, pero es la diferencia entre
> un set y quince dibujos sueltos.

> **Aquí sí vale generar de uno en uno**, al revés que con las celdas: son
> quince objetos **distintos**, no el mismo en tres estados. No hay silueta
> compartida que preservar, así que lo único en juego es el detalle.

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
