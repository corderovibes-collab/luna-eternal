# Prompts listos para copiar y pegar

Cada bloque es un prompt COMPLETO. **No hay que juntar nada.**

## La resolución importa, y no es cuadrada

Las pantallas de Minecraft son **más altas que anchas**. Si generas en 1:1,
el script recorta los lados para cuadrar el aspecto **y se come el marco**.
Por eso cada prompt lleva ya su `--ar` correcto.

| Pantalla | Tamaño final | `--ar` | Genera a |
|---|---|---|---|
| `almanaque` | 176×222 | `88:111` | **1110×1400** |
| `cartera` | 176×222 | `88:111` | **1110×1400** |
| `vias` | 176×222 | `88:111` | **1110×1400** |
| `misiones` | 176×222 | `88:111` | **1110×1400** |
| `gts` | 176×222 | `88:111` | **1110×1400** |
| `pokedex` | 176×222 | `88:111` | **1110×1400** |
| `puerta` | 176×204 | `44:51` | **1208×1400** |
| `tienda` | 176×186 | `88:93` | **1325×1400** |
| `kits` | 176×186 | `88:93` | **1325×1400** |
| `centro` | 176×168 | `22:21` | **1400×1336** |

> **No generes a 176 px de ancho.** Genera grande y deja que el script
> reduzca: reducir conserva detalle, ampliar lo inventa.

> **Si tu IA no acepta `--ar`** (ChatGPT, Leonardo…), borra esa parte y
> pídele el tamaño en palabras: *«image size 1110x1400»*. Y si solo puede
> cuadrado, genera cuadrado y **deja aire en los bordes**: el script
> recortará por ahí en vez de por el marco.

Guarda cada resultado en `arte-origen/` con el nombre que dice su bloque.

---

## almanaque

Genera a **1110×1400** · guarda como **`arte-origen/almanaque.png`**

```
an arcane tome interface, open grimoire border, crescent moon emblem at top center, constellation engravings in the corners, dark fantasy game UI panel, ornate stone frame with silver filigree, deep purple stone #181425 background, glowing moonlight accents #7860C8, soft inner shadow, symmetrical, centered composition, COMPLETELY EMPTY CLEAN CENTER, game asset, UI sprite, flat lighting, no text, no letters, no characters, no creatures --ar 88:111 --style raw --no text, letters, words, watermark, people, faces, pokemon, creatures, clutter in the center, high saturation, rainbow
```

## cartera

Genera a **1110×1400** · guarda como **`arte-origen/cartera.png`**

```
a treasury interface, engraved coin motifs, small stacked coins in the corners, golden accents #E8B33C, dark fantasy game UI panel, ornate stone frame with silver filigree, deep purple stone #181425 background, glowing moonlight accents #7860C8, soft inner shadow, symmetrical, centered composition, COMPLETELY EMPTY CLEAN CENTER, game asset, UI sprite, flat lighting, no text, no letters, no characters, no creatures --ar 88:111 --style raw --no text, letters, words, watermark, people, faces, pokemon, creatures, clutter in the center, high saturation, rainbow
```

## vias

Genera a **1110×1400** · guarda como **`arte-origen/vias.png`**

```
a skill tree interface, five vertical engraved pillars at the edges, connecting silver lines, dark fantasy game UI panel, ornate stone frame with silver filigree, deep purple stone #181425 background, glowing moonlight accents #7860C8, soft inner shadow, symmetrical, centered composition, COMPLETELY EMPTY CLEAN CENTER, game asset, UI sprite, flat lighting, no text, no letters, no characters, no creatures --ar 88:111 --style raw --no text, letters, words, watermark, people, faces, pokemon, creatures, clutter in the center, high saturation, rainbow
```

## misiones

Genera a **1110×1400** · guarda como **`arte-origen/misiones.png`**

```
a quest board interface, aged parchment inset on dark stone, pinned scrolls in the corners, wax seal emblem, dark fantasy game UI panel, ornate stone frame with silver filigree, deep purple stone #181425 background, glowing moonlight accents #7860C8, soft inner shadow, symmetrical, centered composition, COMPLETELY EMPTY CLEAN CENTER, game asset, UI sprite, flat lighting, no text, no letters, no characters, no creatures --ar 88:111 --style raw --no text, letters, words, watermark, people, faces, pokemon, creatures, clutter in the center, high saturation, rainbow
```

## gts

Genera a **1110×1400** · guarda como **`arte-origen/gts.png`**

```
a global trade interface, engraved world map border, two crossed arrows emblem at top, dark fantasy game UI panel, ornate stone frame with silver filigree, deep purple stone #181425 background, glowing moonlight accents #7860C8, soft inner shadow, symmetrical, centered composition, COMPLETELY EMPTY CLEAN CENTER, game asset, UI sprite, flat lighting, no text, no letters, no characters, no creatures --ar 88:111 --style raw --no text, letters, words, watermark, people, faces, pokemon, creatures, clutter in the center, high saturation, rainbow
```

## pokedex

Genera a **1110×1400** · guarda como **`arte-origen/pokedex.png`**

```
a bestiary catalogue interface, engraved index tabs on the right edge, magnifying lens emblem at top, dark fantasy game UI panel, ornate stone frame with silver filigree, deep purple stone #181425 background, glowing moonlight accents #7860C8, soft inner shadow, symmetrical, centered composition, COMPLETELY EMPTY CLEAN CENTER, game asset, UI sprite, flat lighting, no text, no letters, no characters, no creatures --ar 88:111 --style raw --no text, letters, words, watermark, people, faces, pokemon, creatures, clutter in the center, high saturation, rainbow
```

## puerta

Genera a **1208×1400** · guarda como **`arte-origen/puerta.png`**

```
a portal gate interface, two carved stone pillars framing the sides, swirling moonlight portal at the top, dark fantasy game UI panel, ornate stone frame with silver filigree, deep purple stone #181425 background, glowing moonlight accents #7860C8, soft inner shadow, symmetrical, centered composition, COMPLETELY EMPTY CLEAN CENTER, game asset, UI sprite, flat lighting, no text, no letters, no characters, no creatures --ar 44:51 --style raw --no text, letters, words, watermark, people, faces, pokemon, creatures, clutter in the center, high saturation, rainbow
```

## tienda

Genera a **1325×1400** · guarda como **`arte-origen/tienda.png`**

```
a merchant shop interface, hanging fabric awning at the top, engraved scales emblem, warm golden trim, dark fantasy game UI panel, ornate stone frame with silver filigree, deep purple stone #181425 background, glowing moonlight accents #7860C8, soft inner shadow, symmetrical, centered composition, COMPLETELY EMPTY CLEAN CENTER, game asset, UI sprite, flat lighting, no text, no letters, no characters, no creatures --ar 88:93 --style raw --no text, letters, words, watermark, people, faces, pokemon, creatures, clutter in the center, high saturation, rainbow
```

## kits

Genera a **1325×1400** · guarda como **`arte-origen/kits.png`**

```
a supply crate interface, riveted metal corner plates, engraved crate lid at the top, dark fantasy game UI panel, ornate stone frame with silver filigree, deep purple stone #181425 background, glowing moonlight accents #7860C8, soft inner shadow, symmetrical, centered composition, COMPLETELY EMPTY CLEAN CENTER, game asset, UI sprite, flat lighting, no text, no letters, no characters, no creatures --ar 88:93 --style raw --no text, letters, words, watermark, people, faces, pokemon, creatures, clutter in the center, high saturation, rainbow
```

## centro

Genera a **1400×1336** · guarda como **`arte-origen/centro.png`**

```
a healing sanctuary interface, soft teal-white glow #9FE8D8, engraved heart-leaf emblem at top center, dark fantasy game UI panel, ornate stone frame with silver filigree, deep purple stone #181425 background, glowing moonlight accents #7860C8, soft inner shadow, symmetrical, centered composition, COMPLETELY EMPTY CLEAN CENTER, game asset, UI sprite, flat lighting, no text, no letters, no characters, no creatures --ar 22:21 --style raw --no text, letters, words, watermark, people, faces, pokemon, creatures, clutter in the center, high saturation, rainbow
```
