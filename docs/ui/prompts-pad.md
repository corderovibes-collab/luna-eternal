# Prompts del Pad — todo, listo para copiar

## Purpose

Generar con IA **el aparato entero** (marco, pantalla, pestaña, botones) y sus
**15 iconos**. Tú generas, yo recorto y monto.

## Dependencies

- [`visual-identity.md`](visual-identity.md) — cómo se dibuja el Pad
- Sustituye a [`prompts.md`](prompts.md), que era para los menús de cofre

---

## 0. Cómo funciona esto

**Genera las piezas por separado, no una captura entera.** Un menú no es una
ilustración: el marco tiene que **estirarse** según cuántas celdas haya (5×3
hoy, 7×4 mañana) sin que las esquinas se deformen. Eso se llama *nueve
rodajas* y exige que el marco venga solo, sin contenido dentro.

```
1. Generas cada pieza con su prompt      → PNG grande, fondo transparente
2. La guardas en  arte-origen/pad/       → con el nombre exacto
3. python tools/gen_resourcepack.py      → recorto, escalo y monto
```

> **Si generas una captura completa del menú, no me sirve.** Los iconos irían
> pegados al fondo y el marco no se podría estirar. Piezas sueltas.

**No hace falta hacerlas todas.** Lo que falte se sigue dibujando por código,
y se puede mezclar sin que cante si respetas la paleta.

## La paleta, que es lo que une todo

| | |
|---|---|
| Rojo carcasa | `#CE3E30` · oscuro `#8E221A` · claro `#EC6A5C` |
| Azul pantalla | `#56AAE0` · oscuro `#2C78B4` · claro `#96D4F5` |
| Blanco | `#F5FAFF` |
| Oro | `#FACE4E` |
| Contorno | `#262232` |

---

## 1. El aparato

### `marco.png` — la carcasa roja *(2048×2048)*

```
a red handheld game console frame, hollow rectangular bezel only, empty
transparent center, thick rounded plastic border, glossy toy plastic with
soft top highlight, bright friendly red #CE3E30 with darker #8E221A shading,
dark clean outline, clean modern creature-collector RPG UI, Nintendo handheld
style, symmetrical, flat front view, game asset, transparent background,
no text, no letters, no screen content, no buttons, no icons
--ar 1:1 --style raw --no text, letters, words, watermark, people, creatures,
screen content, icons, buttons, perspective, 3d room, shadows outside frame
```

> **`hollow` y `empty transparent center` son lo importante.** Necesito el
> marco solo. Si el centro viene relleno, lo recorto y pierde el borde interior.

### `pantalla.png` — la pantalla azul *(2048×2048)*

```
a light blue glossy game screen panel, plain empty rounded rectangle, subtle
top-to-bottom gradient from #96D4F5 to #2C78B4, soft inner shadow at the top
edge, thin darker border, clean glass sheen, Nintendo handheld UI style,
flat front view, game asset, transparent background outside the panel,
no text, no icons, no content
--ar 1:1 --style raw --no text, letters, icons, content, reflections of
objects, people, creatures, perspective
```

### `pestana.png` — la pestaña del título *(1024×512)*

```
a red rounded tab plate for a game UI title, small horizontal plaque, glossy
plastic, bright red #CE3E30 with darker #8E221A bottom shading, thin dark
outline, soft top highlight, Nintendo handheld style, flat front view,
transparent background, no text, no letters
--ar 2:1 --style raw --no text, letters, words, numbers, watermark
```

### `celda.png` — la caja de un icono *(1024×1024)*

```
an empty white rounded square button for a game UI, soft light blue gradient
inside from white to #96D4F5, thin blue border, gentle inner glow, glossy,
Nintendo handheld UI style, flat front view, transparent background,
completely empty center, no icon, no text
--ar 1:1 --style raw --no text, letters, icon, symbol, content, watermark
```

### `boton.png` — botón de la cabecera *(1024×1024)*

```
a small round glossy game UI button, light blue #96D4F5 with darker #2C78B4
bottom, thin dark outline, soft top highlight, Nintendo handheld style,
flat front view, transparent background, empty face, no symbol, no text
--ar 1:1 --style raw --no text, letters, symbol, arrow, icon, watermark
```

---

## 2. Los 15 iconos

### Bloque común

Va al final de **todos**. Las tres primeras líneas son las que hacen que
funcione a 32 píxeles:

```
bold simple icon, thick dark outline, chunky readable silhouette,
glossy 3d-style game asset with soft top highlight and gentle bottom shadow,
still readable when scaled down to 32 pixels,
single centered object, clean modern creature-collector RPG inventory icon,
Nintendo handheld UI style, rounded friendly shapes, saturated but soft colors,
plain transparent background, no frame, no border, no text
--ar 1:1 --style raw --no text, letters, numbers, watermark, realistic photo,
thin lines, tiny details, busy composition, background, frame, drop shadow
on the ground
```

### Cada uno

Genera **a 1024×1024**, guarda en `arte-origen/icono/<nombre>.png`.

| Fichero | Prompt específico |
|---|---|
| `pokedex` | `a red handheld pokedex device with a blue screen and a round white button` |
| `cartera` | `a stack of three golden coins` |
| `vias` | `three ascending colored bars, blue purple and green` |
| `misiones` | `a white checklist page with a bold green checkmark` |
| `kits` | `a red gift box with a golden ribbon and bow` |
| `tienda` | `a small shop stall with a red and white striped awning` |
| `gts` | `a blue globe with two white arrows circling it` |
| `centro` | `a mint green circle with a bold red medical cross` |
| `puerta` | `a stone archway with glowing white light inside` |
| `gimnasios` | `a golden hexagonal gym badge with a white gem` |
| `tesoros` | `a wooden treasure chest with a golden lock, closed` |
| `clan` | `a blue heraldic shield with a white emblem` |
| `cosmeticos` | `a golden four pointed sparkle star` |
| `cazas` | `a red target crosshair` |
| `explorar` | `a compass with a red and white needle` |

---

## 3. Al elegir entre variantes

| Pregunta | Por qué |
|---|---|
| **¿Se entiende al 25 %?** | Es el tamaño real en pantalla. La única prueba que importa |
| ¿El fondo es transparente de verdad? | Un fondo blanco «casi» transparente deja un halo |
| ¿El marco está hueco? | Si trae contenido dentro, no se puede estirar |
| ¿Se parece al resto? | Mejor 15 iconos correctos y coherentes que 3 espectaculares y 12 sueltos |

> **Genera primero UNO** —`pokedex` va bien— y me lo pasas. Lo monto, lo ves en
> el juego, y si el estilo convence sigues con el resto. Así no gastas 20
> generaciones en un estilo que no encaja.

## Next Actions

1. Generar `pokedex` y validarlo en el juego
2. El resto de iconos
3. `marco`, `pantalla`, `pestaña`, `celda`, `boton`

## Related Systems

- [Identidad visual](visual-identity.md) · [Catálogo](interfaces-catalog.md)
