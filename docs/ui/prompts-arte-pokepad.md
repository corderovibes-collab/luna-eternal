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

### 3.1-bis · El fondo base v5 — 1848 × 780

⚠️ **NO SE GENERA DE CERO: SE EDITA EL v4.** El primer intento pidió un chasis
nuevo con un prompt de texto y salió lo previsible — **otro estilo**, texto
metido dentro de los huecos y el panel de saldos flotando **encima** de la
pantalla. Y era evitable: el v4 ya está aprobado y funciona, así que lo que hay
que pedir es *ensancharlo*, no rehacerlo.

> Se adjunta `arte/pokepad/fondo_base.png` (el v4) como imagen de referencia y
> se usa el modo de **edición** del generador, no el de generación.

**La resolución sale de tres cosas, no del gusto:**

| | |
|---|---|
| **2,37 : 1** | Es la proporción de la referencia (1038 × 439 = 2,364) |
| **Divisible entre 1, 2, 3, 4 y 6** | Los valores posibles de *GUI Scale*. A tamaño real, un texel cae en un píxel |
| **Cabe en 1920 × 1017** | La ventana real, medida en las capturas F2. Sobran 72 × 237 |

```
1380 x 828  ->  1848 x 780
o sea: +468 px de ancho, TODOS a la derecha, y 48 menos de alto
```

> ⚠️ **Por qué no más ancho.** 1872 dejaría solo 48 px de margen, y en cuanto el
> Pad no cabe tiene que encogerse — y encoger enciende el filtrado lineal, que es
> lo que hizo que el bisel naranja del v4 se viera «de baja calidad».
> Ver [dibujado.md](dibujado.md) §6.

```
Using the attached image as the base, keep EXACTLY the same art style, the same
dark graphite body, the same amber accent lines, the same orange screen bezel
with its chamfered corners and the same Rotom ears and whiskers. Do not redraw
them, do not restyle them.

Widen the canvas to a 2.37:1 landscape ratio by adding new body on the RIGHT
side only. Keep the left column of three recessed slots and the big empty
off-white screen exactly as they are in the base image.

In the new empty space at the top right, add a raised panel that matches the
body, holding two rows and one bottom row:
  - first row: a small round empty recess, a wide empty readout recess beside
    it, and a small square button recess at its right
  - second row: exactly the same three recesses again
  - bottom row: two square button recesses side by side

This panel must sit entirely on the body, to the RIGHT of the screen, never
overlapping the screen or its orange bezel.

EVERYTHING IS EMPTY. Every recess is just a hollow with its inner shadow: no
text, no letters, no numbers, no labels, no words, no icons, no symbols, no
coins, no gears, no crosses, nothing drawn inside any of them. Clean smooth
shading, no visible pixels, no watermark.
```

> ⚠️ **Lo de «todo vacío» hay que repetirlo, y aun así se cuela.** El intento
> anterior metió «SESIÓN», «LUNACOINS:», «PLATA:» y hasta los números dentro de
> los huecos. Los saldos, las monedas, la cruz y los iconos de los botones los
> dibuja el código encima (§4): si vienen ya pintados, se ven **los dos**.

> **Después de generarlo:** `python tools/gen_pokepad.py --maqueta`. Las medidas
> —pantalla, ranuras, panel, huecos— se miden solas; lo único que hay que hacer
> es mirar la lámina y comprobar que las encontró todas.

---

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
| 5 | `warps` | `a single glowing circular teleport platform seen at a slight three-quarter angle from above, filling the frame, chunky faceted pedestal of pale stone and metal blocks, raised rim a bright neon cyan ring, a red and white creature-catching ball emblem inlaid flat at the center, a low soft cyan glow spilling from the rim. Only one platform, no beam` **(v2)** |
| 6 | `clan` | `a rounded shield emblem with a Poke Ball in the center and two small wings at its sides, friendly and modern, not warlike` |
| 7 | `gts` | `two Poke Balls facing each other with a bright green emerald floating between them and a smooth circular exchange arrow around all three` |
| 8 | `tienda` | `a small friendly shop building with a bright blue tiled roof and a red and white striped awning over its open counter, a big round red and white creature-catching ball sign hanging above the doorway, and a small stack of gold coins resting on the counter` **(v2)** |
| 9 | `tesoros` | `an open wooden treasure chest with golden fittings and a glowing golden Poke Ball rising out of it with two sparkles` |
| 10 | `wiki` | `a big open illustrated field guide book with thick rounded covers and gilded page edges, the left page showing a small drawn portrait of a round friendly creature and the right page a simple sketched map, a red and white creature-catching ball resting on the spine as a bookmark. The pages contain only drawings, never any writing` **(v2)** |
| 11 | `cazas` | `a small orange fox-like creature with pointed ears, big friendly eyes and a thick curled bushy tail, crouching alert in a tuft of tall green grass, framed inside a glowing round targeting reticle with four short crosshair marks around its rim` **(v2)** |
| 12 | `kits` | `a bright gift box with a wide ribbon and bow, and a small golden crown resting on the lid` |
| 13 | `mochila` | `a modern rounded backpack seen from the front with padded straps and a red and white Poke Ball as the front clasp` |
| 14 | `gyms` | `an open hinged gym badge case seen at a slight three-quarter angle, filling the frame, rounded red and white shell with a thick gold rim and clasp, the lid raised behind, the inside lined with dark blue velvet holding six shiny enamel badges in shaped slots in two rows of three — star, teardrop, flame, leaf, gear, crescent — each a different bold color with a gold edge` **(v2)** |
| 15 | `explorar` | `a tall ancient stone gateway arch filling the frame, chunky weathered stone blocks with moss and glowing violet runes carved along its sides, a red and white creature-catching ball emblem as the keystone, the archway filled with a swirling violet and magenta energy vortex and a faint glimpse of distant green hills through it` **(v2)** |

| — | `candado` | `a padlock whose body is a red and white creature-catching ball, upper half glossy red, lower half clean white, a bold dark band across the middle and the round center button shaped as a small keyhole with a thin gold rim, a thick polished steel shackle arcing up out of the top, closed, two faint cyan sparks near the shackle` **(2.ª página)** |

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
> **`warps` costó cuatro intentos y cada uno enseña algo distinto.**
>
> | Intento | Qué se pidió | Por qué no valía |
> |---|---|---|
> | 1 | criatura épica sobre un mapa | contaba un **viaje largo**; esto son TP dentro de la ciudad |
> | 2 | taxi volador con cesta | lo mismo: un **vehículo**, no un sitio |
> | 3 | plataforma + haz alto + dos pads de fondo | el dibujo estaba bien y **la composición lo mataba**: entre el haz y los pads flotantes, la plataforma buena se quedaba en el tercio de abajo y diminuta |
> | 4 | **una sola plataforma llenando el cuadro** | ✅ |
>
> Dos lecciones, y la segunda es la que se repite más:
>
> **Un icono de menú nombra un SITIO, no una acción.** Cuando cuesta dos
> intentos, casi siempre es que se está ilustrando el verbo en vez del
> sustantivo.
>
> **Cada elemento que se añade a un prompt le quita tamaño al principal.** El
> zócalo facetado y el anillo cian del intento 3 estaban perfectos; lo único que
> sobraba era todo lo demás. A 100 × 100 no hay sitio para una escena — hay
> sitio para **un objeto**.
>
> ⚠️ **Y hay que poner el formato en 1:1 antes de generar.** El intento 3 salió a
> 1024 × 559, apaisado. La plantilla dice «fills almost the entire square» pero
> eso no cambia el ajuste de proporción del generador.

> **`gyms` pasó de una medalla suelta al ESTUCHE de medallas abierto**, y es
> mejor por una razón que no es de gusto: **el estuche es un objeto con silueta
> propia**. Una medalla es un disco, y un disco a 100 × 100 compite con la
> Poké Ball, con el emblema del Clan y con la diana de Cazas — cuatro cosas
> redondas en la misma pantalla. El estuche abierto no se parece a nada más de
> la rejilla, y las medallas de dentro cuentan solas de qué va.
>
> **Seis medallas y no ocho.** A 100 × 100 cada una queda en unos 20 px: seis en
> dos filas de tres se leen como formas de colores distintas; ocho se convierten
> en papilla.

> **`explorar` pasó del bloque de hierba a un PORTAL.** El bloque con el caminito,
> la bola y la criatura asomándose salió abarrotado y sin decir nada: tres
> objetos pequeños peleándose por el cuadro. Es otra vez la misma lección — a
> 100 × 100 hay sitio para **un objeto**, no para una escena.
>
> ⚠️ **Y tiene que distinguirse de `warps` a primera vista**, porque los dos son
> «ir a otro sitio» y caen en la misma rejilla. El prompt lo fuerza en las tres
> cosas que se ven de lejos:
>
> | | `warps` | `explorar` |
> |---|---|---|
> | Forma | plataforma **baja y horizontal** | arco **alto y vertical** |
> | Material | metal y tecnología | piedra antigua con musgo |
> | Color | neón **cian** | vórtice **violeta** |
>
> Dos portales cianes en la misma pantalla serían el mismo botón dos veces.

> **El candado de la segunda página es una Poké Ball**, y esa es toda la idea:
> no una Poké Ball *con* un candado al lado ni un candado *con* una pegatina —
> **el cuerpo del candado es la bola, y su botón central es el ojo de la
> cerradura**. Se lee a la vez «cerrado» y «Pokémon» sin meter dos objetos en un
> cuadro de 100 × 100, que es el error que ya costó cuatro intentos en `warps`.
>
> Las chispas cian y el filo dorado no son adorno: son lo que separa este
> candado de uno de «prohibido». Tiene que decir **pronto**, no **no puedes**.
>
> Va **uno repetido quince veces**. Quince dibujos distintos dirían que hay
> quince cosas distintas esperando, y no es verdad: lo que hay es sitio libre.

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

### 5.4 · CARTAS — el icono, y solo el icono (2026-09-02)

El mod [CobblemonCards](../analysis/cobblemon-cards.md) trae **2.920 texturas de
carta**, una por especie y su shiny, y sus propias pantallas de apertura de
sobre. Lo único que dibujamos nosotros es **el icono de la rejilla**.

> ⚠️ **NI CHASIS NI PIEZAS DE DENTRO, y las dos cosas por decisión del usuario.**
> Las sub-pantallas comparten `pokepad_cosmeticos.png` desde el 2026-08-22:
> pedir un fondo propio daría otro estilo y una pantalla que no se parece a las
> demás — que es lo que pasó al intentar rehacer el chasis base (§3.1-bis).
>
> Y **tampoco hace falta un reverso de carta ni un estado vacío**. Se habían
> propuesto los dos, y sobran en cuanto se sabe qué hay dentro: la pantalla no
> es una rejilla de colección con huecos que rellenar, son **tres zonas para
> abrir sobres** (gratis cada 24 h · por Plata cada 24 h · por LunaCoins sin
> límite). No hay casilla vacía que dibujar, y no hace falta tener archivador
> para entrar.

#### `cartas.png` — 100 × 100

Plantilla de §5.2, con este objeto:

```
a small fan of three collectible trading cards held together and spread
slightly apart like a hand of cards, seen from the front at a gentle
three-quarter angle and filling the frame, thick rounded card stock with a
bold gold border and rounded corners, the front card showing a framed
portrait of a round friendly orange fire creature over a warm gradient panel
with a rainbow holographic sheen slanting across it, the two cards behind it
showing only their gold-edged backs with a red and white creature-catching
ball emblem centered on each, two small sparkles at the top right corner
```

`Same style, outline weight, lighting and ground shadow as the previous icon.`

> **Por qué un abanico de tres y no una carta sola.** Una carta suelta es un
> rectángulo, y a 100 × 100 un rectángulo compite con la mochila, con el
> portapapeles de misiones y con el libro de la wiki. El abanico tiene silueta
> propia — es la misma lección por la que `gyms` pasó de una medalla suelta al
> estuche abierto.
>
> Y **el icono nombra un sitio, no una acción**: es *la colección*, no *abrir un
> sobre*. Por eso no se pide un sobre rasgándose, aunque abrir sobres sea
> justo lo que se hace dentro.

---

### 5.4-bis · PROTECCIONES — el icono, y solo el icono (2026-09-04)

El mod [ClaimBlocks](../../pack-servidor/README.md) trae sus propios módulos y su
propio menú. Lo único que dibujamos nosotros es **el icono de la rejilla**.

> ⚠️ **NI CHASIS NI PIEZAS DE DENTRO.** Decisión del usuario, la misma que con
> CARTAS: la pantalla usa **`pokepad_cosmeticos.png`**, el chasis que comparten
> las sub-pantallas desde el 2026-08-22. Un fondo propio daría otro estilo y una
> pantalla que no se parece a las demás.

#### `protecciones.png` — 100 × 100

Plantilla de §5.2, con este objeto:

```
a small square patch of grassy land with neat soil edges seen at a gentle
three-quarter angle and filling the frame, a short stone post planted in the
middle of it holding up a red and white creature-catching ball as a marker,
and a translucent pale cyan energy dome arching over the whole patch like a
glass bell, its rim meeting the soil and glowing softly where it touches,
with two small sparkles on the upper left curve of the dome
```

`Same style, outline weight, lighting and ground shadow as the previous icon.`

> **Por qué una parcela con cúpula y no un escudo.** Un escudo dice *defensa*,
> que es lo que hace un mod de PvP; esto protege **un trozo de suelo**. Y a
> 100 × 100 un escudo es una silueta corriente que compite con media rejilla,
> mientras que **una cúpula sobre una base cuadrada no se parece a ninguno de
> los otros dieciséis** — es la misma lección por la que `gyms` pasó de una
> medalla suelta al estuche abierto y `cartas` de una carta a un abanico.
>
> **Y la ball del poste no es un adorno**: es literalmente lo que el jugador
> coloca. Los cinco escalones son Poké Ball · Great Ball · Ultra Ball · Luxury
> Ball · Master Ball, así que el icono enseña la pieza real del sistema.
>
> ⚠ **El icono nombra un sitio, no una acción**: es *mi terreno protegido*, no
> *reclamar terreno*. Por eso no se pide una mano colocando nada.

---

### 5.4-ter · SANTUARIO — el icono y la luz del memorial (2026-09-04)

Igual que PROTECCIONES: la pantalla usa el chasis compartido y lo único que se
pide es el icono, más una pieza decorativa para el contador de honores.

#### `santuario.png` — 100 × 100

Plantilla de §5.2, con este objeto (un nicho-memorial, no una acción):

```
a small shrine memorial seen at a gentle three-quarter angle and filling the
frame: a flat square stone pedestal with a soft cyan glowing orb of light
floating just above its center, gentle wisps of pale blue light rising from
the orb, a tiny red and white creature-catching ball resting at the base as
an offering, and one small lit candle on each side with warm soft flames.
Same style, outline weight, lighting and ground shadow as the previous icon.
Plain solid black background. No text, no letters, no numbers, no watermark.
```

> ⚠ **El icono nombra un sitio, no una acción**: es *el lugar del homenaje*, no
> *honrar*. La silueta (orbe sobre pedestal) no se parece a ninguna de las
> otras dieciocho.

#### `memorial_luz.png` — 512 × 512 (la pieza del contador de honores)

```
Same style as the previous image. A single gentle glowing orb of soft
cyan-blue light floating over a low round stone bowl filled with small
glowing motes, delicate wisps of light curling upward, a thin silvery
crescent moon behind the orb's upper left, and a few tiny pale sparkles.
Serene, calm, reverent. Plain solid black background. No text, no watermark.
```

> ⚠ Cuadrada y a 512: va detrás del contador ♡ en `MemorialScreen`, con su alfa.
> El generador la conserva (no es de la rejilla): se procesa y se commitea a
> mano, como los sobres de CARTAS.

### 5.5 · Los tres sobres de CARTAS — 512 × 512 (2026-09-02)

La pantalla tiene **tres zonas** y las tres hacen lo mismo: abrir un sobre.
Tres paneles idénticos serían un muro de texto, así que **cada zona lleva su
sobre dibujado**. Es la misma decisión que el color de cada parada en Viajes:
a la tercera visita vas a la tuya sin leer.

> ⚠️ **El panel NO se genera: lo dibuja el código.** El marco, el título, el
> precio y la cuenta atrás salen de `CartasScreen`, igual que las celdas de la
> rejilla (§4). Lo que se pide aquí es **el objeto**, sobre negro plano, como
> los diecisiete iconos — y por el mismo camino: `quitar_fondo` lo hace
> transparente y `a_tamano` lo baja al hueco.

#### ⚠⚠ El color del sobre es el de la moneda que lo compra

No es decoración, es la regla que hace que las tres se distingan sin leerlas:

| Fichero | Zona | Color | Por qué ese |
|---|---|---|---|
| `sobre_diario` | gratis, 1 cada 24 h | **azul luna** | no lo compra ninguna moneda, así que no puede llevar el color de ninguna |
| `sobre_plata` | Plata, 1 cada 24 h | **blanco y plata** | la Plata es blanca desde D-034 |
| `sobre_luna` | LunaCoins, sin límite | **oro** | la LunaCoin es dorada desde D-033 |

> Y por eso el diario **no** es gris ni dorado: si se pareciera a una de las dos
> de pago, la zona gratuita parecería la versión pobre de esa. Azul lo saca de
> la comparación.

#### `sobre_diario.png`

```
A polished modern game illustration of a sealed collectible card booster
pack standing upright and seen straight on, filling almost the entire square,
a tall rectangular foil wrapper in deep midnight blue with a soft vertical
gradient and a lighter blue band across its middle, a crisp serrated tear
strip along the top edge, a large red and white creature-catching ball
emblem centered on the wrapper with a slim silver crescent moon behind its
upper left, and two small pale blue sparkles floating beside the pack. Clean
and colorful, smooth soft shading, rounded friendly shapes and a bold dark
outline around the silhouette. Semi-realistic stylized look, like a high
quality mobile game icon. Bright, cheerful, family friendly, inviting. Lit
from the top left, resting on a soft dark blue ellipse used as a ground
shadow. Plain solid black background. No text, no letters, no numbers, no
labels, no watermark, no border, no user interface, no document.
```

#### `sobre_plata.png`

```
Same style, outline weight, lighting and ground shadow as the previous
image. A sealed collectible card booster pack standing upright and seen
straight on, filling almost the entire square, a tall rectangular foil
wrapper in bright polished silver and clean white with a soft brushed
metallic sheen running down it and a white band across its middle, a crisp
serrated tear strip along the top edge, a large red and white
creature-catching ball emblem centered on the wrapper inside a thin silver
ring, and two small white sparkles floating beside the pack. Plain solid
black background. No text, no letters, no numbers, no labels, no watermark,
no border, no user interface, no document.
```

#### `sobre_luna.png`

```
Same style, outline weight, lighting and ground shadow as the previous
image. A sealed premium collectible card booster pack standing upright and
seen straight on, filling almost the entire square, a tall rectangular foil
wrapper in rich warm gold with a deep violet band across its middle and a
faint rainbow holographic shimmer across the gold, an ornate gold serrated
tear strip along the top edge, a large red and white creature-catching ball
emblem centered on the wrapper inside a thick ornate gold ring, and the
corner of a bright golden ticket peeking out from behind the top of the
pack. Three small warm gold sparkles floating around it. Plain solid black
background. No text, no letters, no numbers, no labels, no watermark, no
border, no user interface, no document.
```

> ⚠️ **El billete dorado asomando es lo único que distingue la tercera de un
> sobre caro**, y es lo que hay que ver: esa zona es la única con opción a
> **boleto divino**. Va *asomando por detrás* y no delante, para que no tape la
> Poké Ball — a este tamaño, cada cosa que se añade le quita sitio a la
> principal.

> ⚠️ **Cuadradas y a 512 × 512.** El generador las baja al hueco con Lanczos
> sobre alfa premultiplicado, así que pueden llegar a 1024 sin problema — lo que
> no puede es llegar **apaisada**, que es lo que le pasó a `warps` (1024 × 559)
> por no poner la proporción en 1:1 antes de generar.

> ⚠️ **Un sobre es un rectángulo alto y los tres son el mismo objeto.** Aquí no
> vale la regla de «que tenga silueta propia» que hizo del icono un abanico:
> estas tres van **una al lado de otra**, así que lo que tiene que distinguirlas
> no es la forma sino el color. Que sean iguales de silueta es lo correcto —
> dice «son la misma cosa, a tres precios».

---

## 5-bis · La moneda LunaCoin

Va al lado de su saldo, y **se dibuja a 40 × 40**. Ese número no es una
preferencia: la ranura del saldo tiene **166 × 55 de hueco útil**, medido. Y a
40 px el tamaño manda sobre todo lo demás:

| Decisión | Por qué |
|---|---|
| **De frente y plana** | Una moneda en tres cuartos a 40 px es un óvalo borroso. De frente se lee como moneda al instante |
| **Un solo símbolo grande** | La luna creciente y nada más. A ese tamaño unas estrellitas o un grabado fino desaparecen y solo dejan suciedad |
| **Ni un gramo de oro** | Es lo único que la separa de los PokéDólares de un vistazo |
| **Borde en relieve con muescas** | Es lo que dice «moneda» en vez de «medalla» |

> ⚠️ **Va en `arte/pokepad/lunacoin.png`, NO en `icons/`.** No es un icono de
> aplicación, y el generador recorre `icons/` por la lista `ORDEN`: ahí dentro se
> quedaría sin usar.

```
A polished modern game menu icon of a single round coin seen straight from the
front, filling the frame. The coin is struck in pale moonlit silver with a cool
blue sheen, never gold. Its raised outer rim is notched all the way around like
a milled coin edge, and a bold crescent moon stands out in high relief at the
very center of the face, catching a bright highlight along one edge. A soft
cyan-white glow spills gently outward from behind the coin. Only the coin,
nothing else in the scene, no stars, no sparkles, no engraved lettering. Clean
and colorful, with smooth soft shading, rounded friendly shapes and a bold dark
outline around the silhouette. Semi-realistic stylized look, like a high quality
mobile game icon. Subtle Minecraft influence: some forms are built from soft
cubes and slabs with visible flat facets, but it is NOT pixel art and NOT made
of visible pixels; edges are smooth. Bright, cheerful, family friendly,
inviting. The coin fills almost the entire square and nearly touches the edges,
lit from the top left. Square 1:1 composition, centered. Plain solid black
background. No text, no letters, no numbers, no labels, no watermark, no border,
no user interface, no document, no color swatches.
```

> **Sin elipse de sombra**, al revés que los quince iconos. Aquellos se apoyan
> sobre una celda; esta flota dentro de una ranura oscura y una sombra debajo la
> haría parecer despegada de su sitio.

---

## 5-ter · La moneda Plata

La moneda **normal** del servidor: la que se gana jugando. Va a **40 × 40**, el
mismo hueco y las mismas reglas que la LunaCoin (§5-bis).

**Lo único que importa es que no se confundan.** Van a verse en el mismo sitio,
así que se separan en las dos cosas que se distinguen a 40 px:

| | LunaCoin | Plata |
|---|---|---|
| Metal | **oro** cálido | **plata** fría |
| Símbolo | luna creciente | **Poké Ball en relieve** |

> **La Poké Ball no es un adorno: es lo que la marca como «el dinero del
> juego».** La LunaCoin lleva una luna porque se compra aparte; esta lleva la
> bola porque se gana jugando.

```
A polished modern game menu icon of a single round silver coin seen straight
from the front, filling the frame. The coin is struck in bright polished silver
with a cool grey sheen, never gold and never yellow. Its raised outer rim is
notched all the way around like a milled coin edge, and a classic round
creature-catching ball emblem stands out in high relief at the very center of
the face: a top half, a bold horizontal band across the middle and a small
round button in the center, all rendered as raised silver relief rather than
painted color, catching a bright highlight along one edge. A soft white glow
spills gently outward from behind the coin. Only the coin, nothing else in the
scene, no stars, no sparkles, no engraved lettering. Clean and colorful, with
smooth soft shading, rounded friendly shapes and a bold dark outline around the
silhouette. Semi-realistic stylized look, like a high quality mobile game icon.
Subtle Minecraft influence: some forms are built from soft cubes and slabs with
visible flat facets, but it is NOT pixel art and NOT made of visible pixels;
edges are smooth. Bright, cheerful, family friendly, inviting. The coin fills
almost the entire square and nearly touches the edges, lit from the top left.
Square 1:1 composition, centered. Plain solid black background. No text, no
letters, no numbers, no labels, no watermark, no border, no user interface, no
document, no color swatches.
```

> ⚠️ **`raised silver relief rather than painted color` es la línea que más
> trabaja.** Sin ella el generador devuelve una Poké Ball **roja y blanca**
> pegada sobre la moneda, y entonces ya no es una moneda de plata: es una
> chapa con una pegatina. Acuñada en relieve, la bola es del mismo metal.

Guardar en `arte/pokepad/icons/icon_plata.png`. El generador coge **la más
reciente** de las que encuentre, así que dejar la anterior al lado no estorba.

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
    { 610, 692, 60, 48},   atras      mitad izquierda del bisel de abajo
    {1040, 692, 60, 48},   adelante   mitad derecha
    {1107,  85, 80, 64},   ajustes    arriba a la derecha, junto a cerrar
    {1207,  85, 80, 64},   cerrar     en el extremo
}
```

> ⚠️ **En la pantalla principal solo hay CUATRO botones**, y las dos ausencias
> tienen motivo:
>
> | | |
> |---|---|
> | `mas` | no se usa aquí |
> | `inicio` | **no tiene sentido en la pantalla de inicio.** Es el botón de *volver* a ella, así que su sitio es dentro de cada sub-pantalla — el día que Cosméticos tenga la suya, ahí sí va |
>
> El arte de los dos se conserva en `arte/pokepad/botones/`: lo que se quita es
> su hueco, no el fichero. Y eso deja **la ranura mediana vacía** — es sitio
> disponible, no un olvido.

> **`ajustes` va pegado a `cerrar`** porque los dos son controles de la ventana,
> no del contenido, y juntos se leen como tales.

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
