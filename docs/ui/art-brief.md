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
pesimismo, es cómo funcionan: no respetan medidas exactas al píxel, y no saben
que hay 54 casillas de 18×18 que deben quedar **transparentes**.

Lo que sí funciona:

```
1. La IA genera el ARTE     (fondo, borde, adornos)  → ver prompts.md
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

**El estilo es Pokémon; la Luna Eterna es el color, no la forma.**

Es la corrección más importante de este documento. La primera versión pedía
piedra tallada y filigrana de plata: eso es fantasía gótica, y un juego de
Pokémon no se parece en nada a eso.

| Sí | No |
|---|---|
| Esquinas redondeadas | Marcos de piedra tallada |
| Bordes gruesos y limpios | Filigrana, adornos recargados |
| Color plano con degradado suave | Texturas sucias, desgaste, grunge |
| Legible de un vistazo | Detalle que compite con los iconos |
| Amable y pulido | Gótico, medieval, oscuro |

| | |
|---|---|
| **Tema** | La *Luna Eterna*: cielo nocturno, lunas crecientes, estrellas |
| **Base** | Índigo profundo `#181425` |
| **Acento** | Brillo lunar `#7860C8` |
| **Luz** | Blanco luna `#E2D6FF` |
| **Oro** | `#E8B33C`, solo para lo valioso |
| **Sanación** | Menta `#9FE8D8`, solo en el Centro Pokémon |

**Regla de oro:** el fondo es *fondo*. Si compite con los iconos, está mal. Lo
importante de una pantalla son los objetos, no la decoración.

> **Los prompts no dicen la palabra «Pokémon».** Nombrarla hace que el modelo
> dibuje criaturas dentro del panel, que es justo lo que no queremos. Se
> describe el estilo, que además funciona mejor.

---

## 2. Prompts para los fondos

Escríbelos **en inglés**: todos los modelos rinden bastante mejor así.

### 2.1 · Prompt base *(común a todas las pantallas)*

Este bloque va **al final de todos** los prompts de fondo:

Los prompts completos, ya montados y con su `--ar` correcto, están en
**[prompts.md](prompts.md)**. Se copian y se pegan tal cual.

El bloque común es este:

```
clean modern creature-collector RPG menu panel, Nintendo handheld game UI style,
rounded corners, thick soft outline, flat cel shading with gentle gradients,
friendly polished and highly readable,
night sky theme: deep indigo #181425 panel, soft moonlight glow #7860C8,
pale moon white #E2D6FF highlights, tiny scattered stars,
large EMPTY CLEAN CENTER area, flat front view, game asset, no text, no letters
```

### 2.2 · Los 10 prompts

**No se duplican aquí.** Están en **[prompts.md](prompts.md)**, ya montados,
con el `--ar` y la resolución de cada pantalla. Tenerlos en dos sitios fue lo
que dejó una tabla en estilo gótico cuando el estilo ya había cambiado.

> **Deja el centro vacío a propósito.** Ahí van las casillas. Si el prompt
> genera adornos en el medio, quedarán detrás de los objetos y ensucian.

---

## 3. Prompts para los iconos

Los iconos son objetos con `custom_model_data`. Se generan **a 512×512** y el
script los baja a **32×32**.

### 3.1 · Prompt base de icono

```
single centered game icon, clean creature-collector RPG inventory item,
Nintendo handheld game UI style, rounded shapes, thick clean outline,
flat cel shading, indigo and moonlight purple #7860C8 palette,
pale moon white #E2D6FF highlight, bold simple silhouette,
readable at very small size, plain transparent background,
no text, no border, no frame --ar 1:1 --style raw
```

### 3.2 · Los que hacen falta

| Icono | Prompt específico |
|---|---|
| `moneda_pokedollar` | `a rounded silver coin with a simple crescent moon on it` |
| `moneda_marca` | `a glowing rounded blue star token, soft outline` |
| `moneda_premium` | `a shiny rounded golden coin with a star, premium currency` |
| `via_entrenador` | `a rounded badge with a crossed sword and a simple sphere` |
| `via_coleccionista` | `a rounded display case badge with a small gem` |
| `via_explorador` | `a rounded compass with a crescent moon needle` |
| `via_criador` | `a simple rounded nest with one speckled egg` |
| `via_comerciante` | `a rounded balance scale with a coin on one plate` |
| `flecha_atras` | `a rounded left arrow button, soft glow` |
| `flecha_siguiente` | `a rounded right arrow button, soft glow` |
| `candado` | `a simple closed padlock, rounded shapes` |
| `marca_check` | `a bold rounded green checkmark, soft glow` |

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

> **Qué modelo usar.** Da igual, mientras respete el `--ar` de cada pantalla y
> deje el centro libre. Si el tuyo no acepta `--ar`, pide el tamaño en palabras.

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
