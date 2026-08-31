# Prompts de arte — Trajes de rango

## Purpose

Los bocetos de los cinco trajes de rango, para generarlos con IA de imagen y
**traducirlos a cubos** con `tools/gen_trajes.py`.

## Dependencies

- `tools/trajes/modelo.py` — cubos, huesos y el `.geo.json` de GeckoLib
- `tools/trajes/visor.py` — el visor que dibuja el traje puesto

## Current Status

ENTRENADOR hecho y traducido de un boceto real. Faltan ÉLITE, CAMPEÓN, MAESTRO y
LEYENDA.

---

## 1. Por qué el boceto importa, y qué boceto sirve

> ⚠⚠⚠ **UNA IA DE IMAGEN NO PUEDE HACER EL TRAJE.** No genera el `.geo.json` ni
> la textura envuelta — eso son coordenadas, no un dibujo. Lo que hace es **el
> boceto**, y con él dejo de inventar el diseño y paso a traducirlo.

Y no vale cualquier boceto. Uno con luces, sombras y perspectiva **no dice dónde
va cada cubo**. Lo que sirve es una **hoja de personaje**: plana, de frente, con
colores lisos.

> ⚠⚠ **LA VISTA DE ESPALDA SE GANÓ EL SUELDO EN EL PRIMERO.** La Poké Ball del
> ENTRENADOR va en la espalda y **de frente no se ve nada**: sin esa vista no se
> habría puesto jamás. **Las tres vistas siempre.**

> ⚠ **Y una imagen por rango.** Las cinco juntas salen pequeñas y borrosas, y lo
> que hay que leer son detalles de dos píxeles.

---

## 2. El bloque técnico

**Va literal al final de cada prompt, sin cambiar una palabra.** Es lo que hace
que el resultado se pueda traducir:

```
THREE VIEWS side by side, evenly spaced: FRONT VIEW, SIDE VIEW, BACK VIEW.
Strictly orthographic and straight-on: no perspective, no camera tilt, no
foreshortening. The character stands upright, arms straight down, symmetrical.

Rendering style: FLAT SOLID COLORS ONLY. No shading, no gradients, no ambient
occlusion, no specular highlights, no rim light, no glow, no bloom, no drop
shadow, no outline. Every surface is one single uncompromised color.

Geometry: BOXY MINECRAFT PROPORTIONS. Perfectly cubic head, rectangular slab
torso, straight rectangular arms and legs, flat feet. Every garment is built
from rectangular blocks that wrap the body. No cloth folds, no curves, no
tapering, no organic shapes. Nothing thinner than a chunky block.

Background: plain flat neutral grey, empty, nothing else in frame.
Full body visible from the top of the head to below the soles.
Maximum 6 flat colors in the entire image.
```

> ⚠ **`Maximum 6 flat colors` es la línea que más trabajo ahorra.** Cada color de
> más es un cubo más que decidir, y a esta escala un traje de doce colores se ve
> sucio, no rico.

---

## 3. Los cinco trajes

La familia sube así, y **cada rango añade sin quitar**:

| | Cabeza | Torso | Espalda | Material nuevo |
|---|---|---|---|---|
| ENTRENADOR | gorra | chaleco | Poké Ball | tela |
| ÉLITE | cinta | chaqueta sin mangas | insignia | primer metal |
| CAMPEÓN | diadema | coraza ligera | capa corta | placas |
| MAESTRO | capucha | abrigo largo | capa entera | guanteletes |
| LEYENDA | corona | armadura ornamentada | capa larga | oro que brilla |

> ⚠⚠ **LO QUE BRILLA SE PIDE COMO UN COLOR APARTE, NO COMO BRILLO.** El bloque
> técnico prohíbe el glow a propósito: **de una imagen con resplandor no puedo
> saber qué parte brilla**. Se pide *«bright yellow-white`#FFF4B0`, used ONLY on
> the parts that should glow»* y aquí se marcan esos cubos como emisivos — que
> es lo que produce el `_glowmask.png` y hace que brillen de noche de verdad.

### ENTRENADOR — *el que acaba de salir de casa* ✅ hecho

```
Character reference sheet of a blocky Minecraft-style Pokemon trainer,
BEGINNER rank. Young rookie trainer, plain and unarmored, everything is
cloth: this is someone on their first day out.

Head: a red baseball cap worn straight, with a wide white panel across the
front half and a red flat brim sticking forward. Face and hair bare.
Torso: an open blue vest over a dark navy shirt, the vest edges forming a
narrow V at the chest, two small white pockets. Dark navy belt.
Arms: SHORT WHITE SLEEVES ending above the elbow, BARE SKIN FOREARMS,
one dark navy wristband on each wrist.
Legs: blue denim jeans.
Feet: white sneakers with a red sole.
BACK: a large POKE BALL emblem centered on the back of the vest, red top
half, white bottom half, dark band across the middle with a round button.

Palette: red #D83E36, white #EEF0F6, blue #3068C4, navy #1E366A,
denim #445E96.
```

### ÉLITE — *el que ya gana combates*

```
Character reference sheet of a blocky Minecraft-style Pokemon trainer,
ELITE rank. A seasoned battler: the cloth is still there but the first
pieces of metal have appeared. Confident, athletic, road-worn.

Head: a green cloth headband tied around the forehead, its two loose ends
falling as short straight blocks behind the head. No hat. Hair bare.
Torso: a sleeveless emerald green battle jacket with a high collar, worn
open over a black undershirt, with a broad dark leather belt and two
metal buckles.
Arms: BARE SHOULDERS AND UPPER ARMS, and on each forearm a long green
bracer with a thin steel plate on the outside.
Legs: dark charcoal trousers with a green stripe down the outer side of
each leg and a leather thigh strap on one side.
Feet: green and white running boots with steel toe caps.
BACK: a single large ANGULAR BADGE emblem between the shoulder blades,
emerald green with a steel border, shaped like a stylized gym badge.

Palette: emerald #2FBF62, black #24262E, steel #9AA3B2,
leather #4A3A2E, white #EEF0F6.
```

### CAMPEÓN — *el que ya tiene medallas*

```
Character reference sheet of a blocky Minecraft-style Pokemon trainer,
CHAMPION rank. Now genuinely armored: layered plates over the cloth,
ceremonial and battle-ready at once. Someone who has beaten a league.

Head: a turquoise metal circlet across the forehead with a raised angular
crest at the center. Hair bare.
Torso: a turquoise armored chestplate with a raised central ridge, layered
over a black tunic, with a row of small round medal discs across the chest
and a wide dark belt with a turquoise gem buckle.
Arms: broad angular turquoise SHOULDER PAULDRONS sitting on top of each
shoulder, black sleeves below them, and full armored gauntlets from elbow
to knuckle.
Legs: black armored trousers with turquoise knee plates.
Feet: dark armored boots with turquoise shin plates.
BACK: a SHORT RECTANGULAR CAPE hanging straight down from the shoulders to
the waist, turquoise on the outside, black lining, with a straight flat
bottom edge. Do not draw it flowing or curved.

Palette: turquoise #56C8D6, black #1E2029, silver #B9C2D0,
deep teal #1F5F6C, white #EEF0F6.
```

### MAESTRO — *el que ya enseña a los demás*

```
Character reference sheet of a blocky Minecraft-style Pokemon trainer,
MASTER rank. Authority rather than combat: a tall dark silhouette with
deep purple, heavy fabric and worked metal. Imposing, calm, ceremonial.

Head: a deep purple hood pulled up over the head, squared off, with a
silver band across the brow. The face stays visible inside the opening.
Torso: a long dark purple coat with a tall standing collar that rises
behind the neck, closed down the center with silver clasps, and a violet
sash across the chest.
Arms: long purple sleeves with heavy silver gauntlets covering the whole
forearm, each with a raised plate on the back of the hand.
Legs: the coat continues past the waist as two straight panels hanging
over black armored trousers.
Feet: black armored boots with purple trim and silver toe plates.
BACK: a FULL-LENGTH RECTANGULAR CAPE from the shoulders down to mid-calf,
deep purple outside, violet lining, straight flat bottom edge, held by two
silver shoulder clasps. Keep it rigid and rectangular, never flowing.

Palette: deep purple #6B3FA0, violet #A66BD8, silver #C2C8D6,
black #1A1B22, white #EEF0F6.
```

### LEYENDA — *el mito*

```
Character reference sheet of a blocky Minecraft-style Pokemon trainer,
LEGEND rank. The final form: ornate golden armor over black, regal and
overwhelming. This character should look like the last boss of a game.

Head: a tall golden CROWN with five angular points, worn over a black
coif, with a bright inset gem at the center of the brow.
Torso: an ornate golden breastplate with raised angular filigree and a
large gem at the sternum, worn over black armor, with a heavy golden belt
and a broad ornamental buckle.
Arms: large winged golden PAULDRONS flaring outward and upward on each
shoulder, black sleeves, full golden gauntlets with ridged knuckles.
Legs: black armored trousers with golden thigh and knee plates.
Feet: black armored boots with tall golden shin guards.
BACK: a LONG RECTANGULAR CAPE from the shoulders to the ankles, black
outside with a wide golden border running all the way around the edge, and
a golden emblem centered between the shoulder blades. Keep it rigid and
rectangular, never flowing.
GLOWING PARTS: the brow gem, the sternum gem and the cape border are
painted in bright pale yellow #FFF4B0. Use that color ONLY on those parts
and nowhere else. Do not add any glow, bloom or light effect: just the
flat color.

Palette: gold #E8B038, pale glow #FFF4B0, black #16171D,
dark gold #8A6416, white #EEF0F6.
```

---

## 4. Lo que rompe la traducción

Cosas que un boceto puede pedir y que **no se pueden hacer con cubos**. Si
aparecen, se pide de nuevo:

| Sale | Por qué no sirve | Cómo se pide |
|---|---|---|
| Capa ondeando | Un cubo no ondea | `rigid rectangular cape, straight flat bottom edge` |
| Sombras o luces | No sé qué es forma y qué es luz | `flat solid colors, no shading` |
| Perspectiva | No puedo medir | `strictly orthographic, straight-on` |
| Humano realista | Las proporciones no encajan | `boxy Minecraft proportions, cubic head` |
| Cadenas, correas finas | Más finas que un píxel | `nothing thinner than a chunky block` |
| Degradados de color | Cada píxel sería un color | `maximum 6 flat colors` |
| Brillo o resplandor | No sé qué parte brilla | pedirlo **como un color plano aparte** |

---

## 5. Consejos de uso

- **Genera los cinco en la misma conversación.** Los modelos mantienen el estilo
  dentro de un hilo, y lo que hace familia a los cinco trajes es que se parezcan
  entre ellos, no que cada uno sea bonito por su cuenta.
- **Empieza pidiendo ÉLITE**, que es el siguiente al que ya está hecho. Si sale
  con el mismo aire que el ENTRENADOR, el resto va rodado.
- Si un boceto sale bien pero con un detalle imposible, **mándalo igual**: se
  traduce lo que se pueda y el detalle se sustituye por su equivalente en cubos.

## Last Decision

2026-08-27 — Los trajes se bocetan con IA de imagen y **se traducen a mano**. El
arte generado por el script (`gen_trajes.py`) sigue sirviendo de respaldo: el
sistema es el mismo y cambiar un traje es sustituir dos ficheros.

## Next Actions

Generar ÉLITE, CAMPEÓN, MAESTRO y LEYENDA. Mientras tanto, el sistema de juego
(objeto, poner/quitar desde el PokePad, candado por rango) no depende del arte.
