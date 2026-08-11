# Prompts listos para copiar y pegar

Cada bloque es un prompt COMPLETO. **No hay que juntar nada.**

## El estilo: Pokémon, no fantasía gótica

La primera versión de estos prompts pedía piedra tallada, filigrana de plata
y grimorios. **Eso era fantasía oscura, y no es lo que queremos.**

El estilo de un juego de Pokémon es lo contrario:

| Sí | No |
|---|---|
| Esquinas redondeadas | Marcos de piedra tallada |
| Bordes gruesos y limpios | Filigrana y adornos recargados |
| Colores planos con degradado suave | Texturas sucias, desgaste |
| Legible de un vistazo | Detalle que compite con los iconos |
| Amable, pulido | Gótico, medieval |

**La temática de la Luna Eterna es el color, no la forma:** cielo nocturno,
lunas crecientes, estrellas y brillo lunar — pero dibujado limpio y amable.

> Los prompts **no dicen la palabra «Pokémon»**. Nombrarla hace que el modelo
> dibuje criaturas dentro del panel, que es justo lo que no queremos. Se
> describe el estilo en su lugar, que además funciona mejor.

## La resolución no es cuadrada

Las pantallas son **más altas que anchas**. Si generas en 1:1, el script
recorta los lados y se come el borde. Cada prompt lleva ya su `--ar`.

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
> pide el tamaño en palabras: *«image size 1110x1400»*.

Guarda cada resultado en `arte-origen/` con el nombre que dice su bloque.

---

## almanaque

Genera a **1110×1400** · guarda como **`arte-origen/almanaque.png`**

```
a main menu hub panel, big crescent moon emblem glowing at the top center, soft aurora ribbon across the upper edge, clean modern creature-collector RPG menu panel, Nintendo handheld game UI style, rounded corners, thick soft outline, flat cel shading with gentle gradients, friendly polished and highly readable, night sky theme: deep indigo #181425 panel, soft moonlight glow #7860C8, pale moon white #E2D6FF highlights, tiny scattered stars, large EMPTY CLEAN CENTER area, flat front view, game asset, no text, no letters --ar 88:111 --style raw --no text, letters, words, numbers, watermark, people, faces, creatures, animals, monsters, gothic, medieval, ornate filigree, stone carving, grunge, photorealistic, 3d render, clutter in the center, dark muddy colors
```

## cartera

Genera a **1110×1400** · guarda como **`arte-origen/cartera.png`**

```
a wallet and currency panel, three rounded coin badges at the top, warm gold #E8B33C accents on indigo, clean modern creature-collector RPG menu panel, Nintendo handheld game UI style, rounded corners, thick soft outline, flat cel shading with gentle gradients, friendly polished and highly readable, night sky theme: deep indigo #181425 panel, soft moonlight glow #7860C8, pale moon white #E2D6FF highlights, tiny scattered stars, large EMPTY CLEAN CENTER area, flat front view, game asset, no text, no letters --ar 88:111 --style raw --no text, letters, words, numbers, watermark, people, faces, creatures, animals, monsters, gothic, medieval, ornate filigree, stone carving, grunge, photorealistic, 3d render, clutter in the center, dark muddy colors
```

## vias

Genera a **1110×1400** · guarda como **`arte-origen/vias.png`**

```
a progression paths panel, five rounded vertical lanes along the edges, connected dots and soft glowing nodes, clean modern creature-collector RPG menu panel, Nintendo handheld game UI style, rounded corners, thick soft outline, flat cel shading with gentle gradients, friendly polished and highly readable, night sky theme: deep indigo #181425 panel, soft moonlight glow #7860C8, pale moon white #E2D6FF highlights, tiny scattered stars, large EMPTY CLEAN CENTER area, flat front view, game asset, no text, no letters --ar 88:111 --style raw --no text, letters, words, numbers, watermark, people, faces, creatures, animals, monsters, gothic, medieval, ornate filigree, stone carving, grunge, photorealistic, 3d render, clutter in the center, dark muddy colors
```

## misiones

Genera a **1110×1400** · guarda como **`arte-origen/misiones.png`**

```
a quest log panel, rounded checklist ribbon at the top, small bookmark tabs on the left edge, clean modern creature-collector RPG menu panel, Nintendo handheld game UI style, rounded corners, thick soft outline, flat cel shading with gentle gradients, friendly polished and highly readable, night sky theme: deep indigo #181425 panel, soft moonlight glow #7860C8, pale moon white #E2D6FF highlights, tiny scattered stars, large EMPTY CLEAN CENTER area, flat front view, game asset, no text, no letters --ar 88:111 --style raw --no text, letters, words, numbers, watermark, people, faces, creatures, animals, monsters, gothic, medieval, ornate filigree, stone carving, grunge, photorealistic, 3d render, clutter in the center, dark muddy colors
```

## gts

Genera a **1110×1400** · guarda como **`arte-origen/gts.png`**

```
a global trade panel, simple rounded globe emblem at the top, two soft arrows forming a circle, clean modern creature-collector RPG menu panel, Nintendo handheld game UI style, rounded corners, thick soft outline, flat cel shading with gentle gradients, friendly polished and highly readable, night sky theme: deep indigo #181425 panel, soft moonlight glow #7860C8, pale moon white #E2D6FF highlights, tiny scattered stars, large EMPTY CLEAN CENTER area, flat front view, game asset, no text, no letters --ar 88:111 --style raw --no text, letters, words, numbers, watermark, people, faces, creatures, animals, monsters, gothic, medieval, ornate filigree, stone carving, grunge, photorealistic, 3d render, clutter in the center, dark muddy colors
```

## pokedex

Genera a **1110×1400** · guarda como **`arte-origen/pokedex.png`**

```
a creature index panel, rounded tab strip on the right edge, magnifier emblem at the top left, clean modern creature-collector RPG menu panel, Nintendo handheld game UI style, rounded corners, thick soft outline, flat cel shading with gentle gradients, friendly polished and highly readable, night sky theme: deep indigo #181425 panel, soft moonlight glow #7860C8, pale moon white #E2D6FF highlights, tiny scattered stars, large EMPTY CLEAN CENTER area, flat front view, game asset, no text, no letters --ar 88:111 --style raw --no text, letters, words, numbers, watermark, people, faces, creatures, animals, monsters, gothic, medieval, ornate filigree, stone carving, grunge, photorealistic, 3d render, clutter in the center, dark muddy colors
```

## puerta

Genera a **1208×1400** · guarda como **`arte-origen/puerta.png`**

```
a world gate panel, two rounded pillars at the sides, soft glowing portal ring at the top, clean modern creature-collector RPG menu panel, Nintendo handheld game UI style, rounded corners, thick soft outline, flat cel shading with gentle gradients, friendly polished and highly readable, night sky theme: deep indigo #181425 panel, soft moonlight glow #7860C8, pale moon white #E2D6FF highlights, tiny scattered stars, large EMPTY CLEAN CENTER area, flat front view, game asset, no text, no letters --ar 44:51 --style raw --no text, letters, words, numbers, watermark, people, faces, creatures, animals, monsters, gothic, medieval, ornate filigree, stone carving, grunge, photorealistic, 3d render, clutter in the center, dark muddy colors
```

## tienda

Genera a **1325×1400** · guarda como **`arte-origen/tienda.png`**

```
a friendly shop panel, rounded awning with soft stripes at the top, small price tag emblem, warm gold trim, clean modern creature-collector RPG menu panel, Nintendo handheld game UI style, rounded corners, thick soft outline, flat cel shading with gentle gradients, friendly polished and highly readable, night sky theme: deep indigo #181425 panel, soft moonlight glow #7860C8, pale moon white #E2D6FF highlights, tiny scattered stars, large EMPTY CLEAN CENTER area, flat front view, game asset, no text, no letters --ar 88:93 --style raw --no text, letters, words, numbers, watermark, people, faces, creatures, animals, monsters, gothic, medieval, ornate filigree, stone carving, grunge, photorealistic, 3d render, clutter in the center, dark muddy colors
```

## kits

Genera a **1325×1400** · guarda como **`arte-origen/kits.png`**

```
a daily rewards panel, rounded gift box emblem at the top, soft ribbon banner, clean modern creature-collector RPG menu panel, Nintendo handheld game UI style, rounded corners, thick soft outline, flat cel shading with gentle gradients, friendly polished and highly readable, night sky theme: deep indigo #181425 panel, soft moonlight glow #7860C8, pale moon white #E2D6FF highlights, tiny scattered stars, large EMPTY CLEAN CENTER area, flat front view, game asset, no text, no letters --ar 88:93 --style raw --no text, letters, words, numbers, watermark, people, faces, creatures, animals, monsters, gothic, medieval, ornate filigree, stone carving, grunge, photorealistic, 3d render, clutter in the center, dark muddy colors
```

## centro

Genera a **1400×1336** · guarda como **`arte-origen/centro.png`**

```
a healing center panel, soft mint and white glow #9FE8D8, rounded heart emblem at the top center, calm and clean, clean modern creature-collector RPG menu panel, Nintendo handheld game UI style, rounded corners, thick soft outline, flat cel shading with gentle gradients, friendly polished and highly readable, night sky theme: deep indigo #181425 panel, soft moonlight glow #7860C8, pale moon white #E2D6FF highlights, tiny scattered stars, large EMPTY CLEAN CENTER area, flat front view, game asset, no text, no letters --ar 22:21 --style raw --no text, letters, words, numbers, watermark, people, faces, creatures, animals, monsters, gothic, medieval, ornate filigree, stone carving, grunge, photorealistic, 3d render, clutter in the center, dark muddy colors
```
