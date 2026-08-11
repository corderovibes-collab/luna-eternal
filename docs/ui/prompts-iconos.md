# Prompts de los iconos del Pad

## Purpose

Sustituir los iconos dibujados por código con arte propio, si se quiere.
Complementa [prompts.md](prompts.md), que son los fondos.

## Antes de generar: los dibujados ya funcionan

`tools/gen_iconos.py` dibuja los 15 con geometría limpia y **se leen a 24 px**,
que es el tamaño real en pantalla. **No hace falta sustituirlos.**

> **Un icono de 32×32 no es una ilustración, es una señal.** A ese tamaño gana
> la silueta, no el detalle — y una IA generativa da justo lo contrario:
> texturas bonitas que a 24 px se vuelven barro. Es el motivo de que los
> dibujara en vez de pedírtelos.

Si aun así quieres arte propio, aquí están los prompts. **El script respeta lo
que pongas**: si existe `arte-origen/icono/<nombre>.png`, usa el tuyo.

---

## Cómo se usan

1. Genera **a 1024×1024**, fondo transparente.
2. Guarda en `arte-origen/icono/` con el nombre exacto de la tabla.
3. `python tools/gen_iconos.py` — reduce a 32×32 y respeta los tuyos.
4. `python tools/gen_resourcepack.py`

**No hace falta hacer los 15.** Los que falten siguen siendo los dibujados, y
se pueden mezclar sin que se note el corte si respetas la paleta.

## Bloque común

Va al final de todos. Lo importante son las tres primeras líneas: **silueta
gruesa, sin detalle fino, legible al reducir**.

```
bold simple icon silhouette, thick clean outline, readable when scaled down
to 24 pixels, no fine detail, flat cel shading with two tones only,
centered single object, clean modern creature-collector RPG game icon,
Nintendo handheld UI style, rounded shapes,
deep indigo #181425 and moonlight purple #7860C8 palette,
pale moon white #E2D6FF highlight,
plain transparent background, no text, no frame, no border, no background
--ar 1:1 --style raw --no text, letters, watermark, realistic, photo,
3d render, gradient mesh, tiny details, thin lines, busy composition
```

## Los 15

| Nombre del fichero | Prompt específico |
|---|---|
| `pokedex` | `a red handheld creature encyclopedia device with a blue screen` |
| `cartera` | `a stack of three silver coins, top one showing a crescent moon` |
| `vias` | `three ascending bars with a rising arrow above them` |
| `misiones` | `a scroll checklist with a bold green checkmark` |
| `kits` | `a wrapped gift box with a golden ribbon bow` |
| `tienda` | `a small shop front with a striped awning` |
| `gts` | `a globe with two arrows circling it` |
| `centro` | `a mint green circle with a red medical cross` |
| `puerta` | `a stone archway portal glowing white inside` |
| `gimnasios` | `a golden hexagonal gym badge with a gem in the centre` |
| `tesoros` | `a closed treasure chest with a golden lock` |
| `clan` | `a heraldic shield, plain and bold` |
| `cosmeticos` | `a four pointed sparkle star` |
| `cazas` | `a target crosshair with a red centre` |
| `explorar` | `a compass with a red and white needle` |

## Cómo saber si un icono sirve

**Míralo al 25 %.** Si a ese tamaño no distingues qué es, no sirve — por bonito
que sea al 100 %. Es la única prueba que importa.

## Related Systems

- [Prompts de fondos](prompts.md) · [Identidad visual](visual-identity.md)
