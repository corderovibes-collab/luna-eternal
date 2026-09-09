# El lobby: de un prompt a bloques

## Purpose
Los dos prompts —Gemini para la lámina, Meshy para la malla— y el procedimiento
para convertir el resultado en la construcción del lobby.

## Dependencies
`tools/malla_a_construccion.py` · `tools/simplificar_malla.py` ·
`docs/ui/prompts-mundos.md` (estilo de la casa) · CLAUDE.md §MESHY

---

## 1. Lo que decide el diseño no es el gusto: es el pipeline

⚠⚠⚠ **MESHY DEVUELVE SÓLIDOS, Y ESO CONVIERTE UNA SALA EN UN PISAPAPELES.**
Está medido y escrito en CLAUDE.md: *«si sale el 80 %, Meshy devolvió un macizo
con la fachada bonita»*. `malla_a_construccion.py` no lo arregla —no fusiona ni
recorta nada, cada vóxel es un bloque— así que produciría cuarenta mil bloques
de piedra rellena en los que no se puede entrar. **Una sala hueca ocupa el
15-35 % de su caja**, y el propio script lo imprime al terminar.

Por eso la forma que se pide es un **pabellón abierto**: columnas y techo, sin
muros macizos. No es una preferencia estética —

- **sale hueco casi por construcción**: no hay volumen cerrado que rellenar;
- el jugador **ve al guardián desde que aparece**, que es lo único que tiene que
  hacer allí;
- y en una dimensión de vacío, **una planta abierta no tiene rincones** donde
  alguien se quede dando vueltas.

⚠⚠ **NO SE LE PIDE ASPECTO DE BLOQUES.** Es contraintuitivo y está pagado:
*«Meshy no hace modelos de bloques, hace modelos LISOS QUE PARECEN de bloques —
los cubitos de su render son sombreado pintado, no geometría»*. Al vóxelizar,
ese sombreado se pierde y la forma sale en papilla. Se pide **arquitectura
limpia**, y los bloques los pone el conversor.

⚠ **Y el color importa más de lo que parece.** El script lee la textura `.png`
que va al lado del `.obj` y **elige el bloque de Minecraft por color**. Degradados
y texturas fotorrealistas dan elecciones embarradas; **pocos colores planos y bien
separados** dan una construcción legible.

## 2. Las medidas

```
huella      ~24 x 24 bloques      cabe holgado alrededor de la plataforma 9x9
alto         16 bloques           --alto 16, que es el defecto del script
llegada      43.998 / 72 / 47.97  mirando al OESTE (yaw 90)
suelo        y = 71               la casilla de debajo de los pies
```

⚠ **El lobby solo admite y = 0 .. 255** (`min_y: 0`, `height: 256` en su
`dimension_type`), al contrario que la ciudadela y el Hogar, que llegan a −64.

⚠⚠ **Hay que dejarle sitio al guardián.** Lugia es enorme y su cartel flota
**4,2 bloques sobre sus pies**: si el techo queda bajo, el cartel se mete dentro
y no se lee. Con 16 de alto sobra, siempre que el guardián no vaya debajo de una
viga.

## 3. Prompt para Gemini (la lámina)

> Va en inglés a propósito: los modelos de imagen responden mucho mejor, y esto
> no lo lee ningún jugador.

```
A single architectural model of a small open-air pavilion, isolated on a pure
white background.

STRUCTURE: octagonal pavilion, eight thick rounded columns supporting a domed
roof. No walls between the columns — the interior is completely open and
visible from outside. A raised circular floor platform with two shallow steps
running all the way around. A simple ring-shaped ornament at the top of the
dome.

STYLE: clean stylised game architecture in the spirit of a Pokemon Center —
friendly, rounded, chunky shapes, generous proportions, no thin or fragile
parts, no fine ornamental detail. Night-themed palette: off-white marble,
deep indigo blue roof, warm gold trim, and pale cyan glowing panels set into
the columns.

COLOR: flat solid colors only. A limited palette of six clearly separated
colors. No gradients, no photorealistic textures, no reflections, no grunge,
no weathering.

VIEW: single three-quarter elevated view of the whole structure, centered in
frame, the entire model visible including the full floor, nothing cropped at
any edge.

LIGHTING: flat even studio lighting from the front. No cast shadows, no ground
shadow, no ambient occlusion darkening.

BACKGROUND: pure solid white #FFFFFF, completely empty. No floor plane, no
horizon, no gradient, no vignette.

DO NOT INCLUDE: any characters, creatures, people or animals; any text, letters,
numbers, logos or signage; any furniture or props; any voxel, pixel-art,
blocky or Minecraft-like appearance; multiple views or turnaround sheets.
```

⚠⚠ **Sin personajes ni criaturas, y son dos motivos.** Uno, el guardián lo
coloca el mod (`/luna puerta npc`) y es un Pokémon de verdad de Cobblemon: uno
dibujado sobraría y estorbaría. Dos, una criatura orgánica **es exactamente lo
que Meshy convierte en papilla al vóxelizar** — la lección de la armadura, que
salió con «710 cajas y no se aprecia nada».

⚠ **Sin texto.** Cualquier letra acaba en la malla como relieve, y en bloques
son manchas. El cartel del guardián lo pinta el juego.

## 4. Prompt para Meshy (image-to-3D)

Se sube la lámina de Gemini y se acompaña de este texto:

```
Open octagonal pavilion with eight columns and a domed roof. Fully hollow
interior: the space between the columns and under the dome must be empty,
enclosing no solid volume. Hard-surface architectural geometry with clean flat
faces and sharp edges. Do not fill the interior. Do not add walls between the
columns. Do not add a solid base block under the floor.
```

**Ajustes:**

| | |
|---|---|
| Modo | **Image to 3D** |
| Symmetry | **On** — el pabellón es simétrico y sale más limpio |
| Polycount | **Alto** · si estorba se baja después con `simplificar_malla.py` |
| Texture | **Sí**, y se exporta el PNG |
| Export | **OBJ** — es lo único que lee `malla_a_construccion.py` |

⚠ El `.png` tiene que quedar **al lado del `.obj` y con el mismo nombre**: el
script lo busca así (`obj.with_suffix(".png")`). Sin él la construcción sale sin
color y no avisa — lo dice de pasada en su línea de «textura ninguna».

## 5. El procedimiento, y la comprobación que decide

```bash
python tools/malla_a_construccion.py build/lobby.obj --alto 16
```

Al terminar imprime lo único que hay que mirar:

```
  MIDE  24 de ancho x 16 de alto x 24 de fondo
  SON   N bloques
  ocupa el XX% de su caja (una sala hueca ronda el 15-35%)
```

⚠⚠⚠ **ESE PORCENTAJE ES EL CRITERIO DE ACEPTACIÓN, NO UN DATO CURIOSO.**

| % | Qué significa | Qué hacer |
|---|---|---|
| **15-35** | Pabellón hueco de verdad | Adelante |
| **35-50** | Columnas demasiado gruesas o base maciza | Regenerar pidiendo columnas más finas |
| **> 50** | **Meshy devolvió un sólido** | Regenerar. No sirve, y colocarlo son cuarenta mil bloques de piedra |

⚠ Si sale muy alto dos veces seguidas, el arreglo que funciona es **pedir la
lámina con el pabellón visto desde un ángulo más bajo**, para que se vea el hueco
entre las columnas: Meshy rellena lo que no ve.

## 6. Lo que este camino NO resuelve

⚠⚠ **La luz.** El lobby es noche permanente con `ambient_light 0.4`. La malla
trae color, no emisión: los paneles «brillantes» de la lámina salen como bloques
normales. Se sustituyen a mano por los neones de `lunaneon`, que es justo para lo
que existen.

⚠⚠ **El vacío.** El conversor coloca el pabellón y nada más. **El suelo cerrado o
el anillo de `barrier` va a mano**, y no es opcional: quien se caiga muere y
reaparece en el overworld. La puerta lo devuelve en menos de un segundo
(`Puerta.vigilar`), así que no se rompe nada — pero es feo y desconcierta.
