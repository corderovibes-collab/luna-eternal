# Encargo de arte — prompts para IA

## Purpose

Los prompts para generar el arte de la interfaz, y **el proceso para que lo
generado sea utilizable de verdad**. `ART-001`.

## Dependencies

- [`visual-identity.md`](visual-identity.md) — cómo funciona el sistema

## Current Status

`READY`. La infraestructura está desplegada y validada; falta el arte.

---

## 0. Lee esto antes de generar nada

**Ninguna IA de imágenes va a darte un PNG de 176×222 listo para usar.** No es
pesimismo, es cómo funcionan: generan a 1024×1024, no respetan medidas exactas,
y no saben que hay 54 casillas de 18×18 que deben quedar **transparentes**.

Lo que sí funciona:

```
1. La IA genera el ARTE     (fondo, marco, adornos)  → 1024×1024
2. tools/importar_arte.py   recorta, escala y perfora los agujeros
3. gen_resourcepack.py      valida y empaqueta
```

**El paso 2 es el que hace que sirva.** Está escrito y hace el trabajo sucio:
convierte cualquier imagen en un fondo válido, con las casillas transparentes
en su sitio exacto.

> ⚠️ **El fallo que esto evita** ya nos pasó: el título se dibuja *después* de
> los objetos, así que un fondo opaco **tapa el inventario entero**. Si le pasas
> al generador un PNG sin agujeros, **aborta y te dice cuántas casillas están
> tapadas**.

---

## 1. La identidad visual, que va antes que los prompts

Todo lo generado tiene que parecer del mismo sitio. Estas son las reglas:

| | |
|---|---|
| **Tema** | La *Luna Eterna*. Noche perpetua, luz de luna, magia serena |
| **Base** | Morado muy oscuro `#181425` |
| **Acento** | Morado luminoso `#7860C8` |
| **Luz** | Blanco lunar `#E2D6FF` |
| **Oro** | `#E8B33C`, solo para lo valioso |
| **Material** | Piedra pulida oscura, vetas de plata, cristal |
| **Prohibido** | Colores planos de saturación alta, degradados de arcoíris, estilo «móvil casual» |

**Regla de oro:** el fondo es *fondo*. Si compite con los iconos, está mal. Lo
importante de una pantalla son los objetos, no la decoración.

---

## 2. Prompts para los fondos

Escríbelos **en inglés**: todos los modelos rinden bastante mejor así.

### 2.1 · Prompt base *(común a todas las pantallas)*

Este bloque va **al final de todos** los prompts de fondo:

```
dark fantasy game UI panel, ornate stone frame with silver filigree,
deep purple stone #181425 background, glowing moonlight accents #7860C8,
soft inner shadow, symmetrical, centered composition, clean empty center area,
game asset, UI sprite sheet style, flat lighting, no text, no letters,
no characters, no creatures, transparent background
--ar 1:1 --style raw
```

### 2.2 · Negativo *(para Stable Diffusion; en Midjourney usa `--no`)*

```
text, letters, words, watermark, signature, people, faces, pokemon,
creatures, photorealistic, 3d render, busy center, clutter, high saturation,
rainbow, drop shadow outside frame, cropped frame
```

### 2.3 · Por pantalla

Cada uno se combina con el **prompt base** del §2.1.

| Pantalla | Prompt específico |
|---|---|
| **almanaque** | `an arcane tome interface, open grimoire border, crescent moon emblem at top center, constellation engravings in the corners` |
| **cartera** | `a treasury interface, engraved coin motifs, small stacked coins in the corners, golden accents #E8B33C on dark purple` |
| **vias** | `a skill tree interface, five vertical engraved pillars, connecting silver lines, subtle progression notches` |
| **misiones** | `a quest board interface, aged parchment inset on dark stone, small pinned scrolls in the corners, wax seal emblem` |
| **gts** | `a global trade interface, engraved world map inset, two crossed arrows emblem, subtle latitude lines` |
| **pokedex** | `a bestiary catalogue interface, engraved index tabs on the right edge, magnifying lens emblem at top` |
| **puerta** | `a portal gate interface, two carved stone pillars framing the sides, swirling moonlight portal in the upper area` |
| **tienda** | `a merchant shop interface, hanging fabric awning at the top, small engraved scales emblem, warm golden trim` |
| **kits** | `a supply crate interface, riveted metal corner plates, engraved crate lid at the top` |
| **centro** | `a healing sanctuary interface, soft teal-white glow #9FE8D8, engraved heart-leaf emblem at top center, calm and clean` |

> **Deja el centro vacío a propósito.** Ahí van las casillas. Si el prompt
> genera adornos en el medio, quedarán detrás de los objetos y ensucian.

---

## 3. Prompts para los iconos

Los iconos son objetos con `custom_model_data`. Se generan **a 512×512** y el
script los baja a **32×32**.

### 3.1 · Prompt base de icono

```
single centered game icon, dark fantasy RPG inventory item,
silver and deep purple #7860C8 palette, moonlit rim light,
thick clean silhouette, readable at very small size, flat vector-like shading,
plain transparent background, no text, no border, no frame
--ar 1:1 --style raw
```

### 3.2 · Los que hacen falta

| Icono | Prompt específico |
|---|---|
| `moneda_pokedollar` | `a silver coin with a crescent moon minted on it` |
| `moneda_marca` | `a glowing blue rune mark, arcane sigil` |
| `moneda_premium` | `an ornate golden coin with a star, premium currency` |
| `via_entrenador` | `a crossed sword and pokeball emblem` |
| `via_coleccionista` | `an open display case with a small gem` |
| `via_explorador` | `a brass compass with a moon needle` |
| `via_criador` | `a nest with a single speckled egg` |
| `via_comerciante` | `a merchant scale with coins on one plate` |
| `flecha_atras` | `a simple engraved left arrow, stone tablet style` |
| `flecha_siguiente` | `a simple engraved right arrow, stone tablet style` |
| `candado` | `a closed iron padlock, worn metal` |
| `marca_check` | `a glowing green checkmark, arcane style` |

---

## 4. El proceso, paso a paso

```
1. Genera la imagen con el prompt. Pide 4 variantes y quédate con la que
   tenga el CENTRO MÁS LIMPIO — no la más bonita.

2. Guárdala en  arte-origen/  con el nombre de la pantalla:
       arte-origen/almanaque.png

3. python tools/importar_arte.py
       recorta al aspecto correcto, escala a 176×N, perfora las casillas
       y lo deja en resourcepack/

4. python tools/gen_resourcepack.py
       valida tamaño y transparencia, y empaqueta

5. Publicar el release y apuntar el servidor al nuevo SHA-1
```

> **Qué modelo usar.** Midjourney da el mejor resultado para marcos ornamentados.
> Si usas otro, lo que importa es que respete `--ar 1:1` y deje el centro libre.

---

## 5. Cómo saber si un fondo está bien

Entra al juego y abre la pantalla. Comprueba, en este orden:

1. **¿Se ven los objetos?** Si no, las casillas no son transparentes.
2. **¿El borde del fondo coincide con el borde del menú?** Si sobresale o se
   queda corto, la imagen no mide lo que debe.
3. **¿El título se lee?** Va en la esquina superior izquierda, encima del arte.
4. **¿Distrae?** Si te fijas antes en el fondo que en los iconos, está mal.

## Next Actions

1. Generar los 10 fondos
2. Generar los 12 iconos
3. Pasar por `importar_arte.py` y publicar

## Related Systems

- [Identidad visual](visual-identity.md) · [Catálogo](interfaces-catalog.md)
