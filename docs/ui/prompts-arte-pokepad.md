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

Tres condiciones. Si el arte falla una, no sirve y hay que regenerarlo.

### 1.1 · El tamaño real es diminuto

La pantalla entera son **346 × 207 píxeles de interfaz**. No 1024. No 2048.

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

Un generador de imágenes no sabe dibujar a 346 px, así que **se genera grande y
se reduce**. Para que reducir no lo convierta en papilla, el arte tiene que ser
**pixel art de verdad**: bloques planos de color, sin degradados y sin
suavizado. Por eso todos los prompts piden `crisp pixel art`, `no
anti-aliasing`, `flat color blocks`.

```
generar en   1384 × 828   (exactamente 4× de 346 × 207)
reducir a     346 × 207   con vecino más próximo, nunca bicúbico
```

> Si Gemini no deja fijar ese tamaño, pide **relación 5:3** y lo más grande que
> permita. Lo importante es la proporción y que los bloques de color sean
> gruesos y limpios.

### 1.2 · Sin una sola letra

**El texto lo dibuja el juego**, no la textura. Está traducido, y un jugador en
inglés vería «Cazas» pintado a fuego.

Todos los prompts llevan `absolutely no text, no letters, no numbers, no
labels`. Si vuelve con letras, se regenera. **No vale borrarlas a mano**: el
hueco tiene que estar pensado desde el principio.

### 1.3 · La composición no se mueve

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
whole canvas. Canvas aspect ratio 5:3.

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

La rejilla son celdas iguales repetidas. Hacen falta las tres.

```
[PEGA AQUÍ LA BIBLIA DE ESTILO]

Subject: three UI button tiles for a pixel-art game menu, arranged in one
horizontal row on a pure black background, evenly spaced, identical size
and shape, square with slightly rounded corners.

All three are EMPTY containers - no icon inside, no text.

Tile 1 - RESTING: moon blue (#8B93D8) fill, #C8D2F0 top-left rim,
  #6C78C8 bottom-right shadow. Calm, flat, slightly recessed.
Tile 2 - HOVERED: same shape, brighter fill, a clean 1px neon cyan
  (#16F2E6) border all around, and the tile looks slightly raised.
Tile 3 - LOCKED: same shape, desaturated graphite (#2A2E3A) fill, dimmer,
  with a small closed padlock shape in the lower-right corner drawn in
  #6C78C8. The padlock is the only content allowed.

The three tiles must be pixel-identical in silhouette and size. Only the
color and the border change.
```

---

## 5. Prompt 3 — Los quince iconos

**La pieza donde más se nota el estilo «PokeCraft»**, porque cada icono mezcla
un objeto de Minecraft con algo de Pokémon.

```
[PEGA AQUÍ LA BIBLIA DE ESTILO]

Subject: a sprite sheet of 15 game menu icons, laid out in a strict grid of
5 columns by 3 rows on a pure black background. Even spacing. Every icon
occupies the same square area and is centered in its cell.

Each icon must read clearly at very small size: one single object, bold
silhouette, no scene, no background, no frame around it.

The icons, in order, left to right, top to bottom:
 1. Pokedex      - a closed handheld dex device, three-quarter blocky
 2. Cosmetics    - a Minecraft-style armor helmet with a small sparkle
 3. Jobs         - a Minecraft iron pickaxe crossed with a wooden axe
 4. Quests       - a Minecraft-style book with a glowing bookmark ribbon
 5. Warps        - a map pin / marker over a tiny folded map
 6. Clan         - a heraldic banner on a pole
 7. GTS          - a stack of gold coins with a small arrow loop around it
 8. Shop         - a small market stall with a striped awning
 9. Treasures    - a Minecraft treasure chest, lid slightly open, light inside
10. Wiki         - a question mark carved as a blocky 3D shape
11. Hunts        - a crosshair / target reticle over a paw print
12. Kits         - a crown resting on a small blocky pedestal
13. Backpack     - a Minecraft-style bag / satchel with straps
14. Gyms         - a stylized badge / medal with wings
15. Explore      - a blocky globe with continents

Style reminder for the icons specifically: they must look like they belong
to the same set - same outline weight, same light direction (top-left),
same level of detail, same visual weight. Solid, chunky, readable.
```

> **Truco si salen desiguales:** genera **uno por uno** reusando la biblia y
> pidiendo *"in the exact same style as the previous icon"*. Es más lento pero
> es la diferencia entre un set y quince dibujos sueltos.

---

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
   5:3? Si falla algo, regenerar — retocar a mano sale más caro.
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
