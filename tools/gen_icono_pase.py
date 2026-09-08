#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
EL ICONO PROVISIONAL DEL PASE DE BATALLA.

⚠⚠ ESTO NO ES EL ARTE FINAL, Y NO PRETENDE SERLO.

   El arte de verdad lo genera el usuario con Gemini a partir del prompt de
   `docs/ui/prompts-arte-pokepad.md` §5.4-quater, y se instala como
   `arte/pokepad/icons/icon_pase.png`. Este script existe por lo mismo que
   `candado_provisional()` dentro de `gen_pokepad.py`: **una celda del PokePad
   dibuja su propio icono aunque este bloqueada**, asi que dar de alta una
   aplicacion SIN su PNG no da ningun error -- da un CUADRO MAGENTA en la
   pantalla principal, y solo se ve abriendo el Pad. Paso el 2026-08-23 con
   seis pantallas a la vez.

   Con un provisional, la aplicacion se puede montar, probar y desplegar hoy, y
   el dia que llegue el PNG bueno lo sustituye sin tocar una linea de codigo.

⚠ DIBUJA LA MISMA IDEA QUE PIDE EL PROMPT --tres escalones dorados y una luna
  creciente encima-- a proposito: asi la silueta que se ve en la rejilla hoy es
  la que se vera manana, y cambiar el arte no cambia como se lee la pantalla.

Uso:
    python tools/gen_icono_pase.py
"""

from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

RAIZ = Path(__file__).resolve().parent.parent
FUENTE = RAIZ / "arte" / "pokepad" / "icons" / "icon_pase.png"
SALIDA = (RAIZ / "mod" / "src" / "client" / "resources" / "assets" / "lunaeternal"
          / "textures" / "gui" / "pokepad" / "pase.png")

# El lado del icono en el Pad. Igual que el resto de la rejilla.
LADO = 100
# Se dibuja x8 y se reduce: es la unica forma de que las diagonales y la luna
# no salgan dentadas, porque `Image.polygon` no hace antialiasing.
S = 8

ORO = (255, 214, 92)
ORO_OSC = (196, 148, 32)
ORO_CLA = (255, 240, 176)
VIOLETA = (185, 140, 255)
VIOLETA_OSC = (108, 74, 168)
CONTORNO = (24, 20, 40)
SOMBRA = (18, 26, 52)


def _escalon(d: ImageDraw.ImageDraw, x: int, y: int, w: int, h: int) -> None:
    """Un cubo en perspectiva suave: cara, tapa y contorno."""
    prof = int(w * 0.24)
    # Tapa
    d.polygon([(x, y), (x + w, y), (x + w + prof, y - prof), (x + prof, y - prof)],
              fill=ORO_CLA, outline=CONTORNO, width=S // 2)
    # Cara
    d.rectangle([x, y, x + w, y + h], fill=ORO, outline=CONTORNO, width=S // 2)
    # Lateral
    d.polygon([(x + w, y), (x + w + prof, y - prof),
               (x + w + prof, y + h - prof), (x + w, y + h)],
              fill=ORO_OSC, outline=CONTORNO, width=S // 2)


def dibujar() -> Image.Image:
    n = LADO * S
    im = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)

    # La elipse de sombra, que llevan todos los iconos de la rejilla.
    d.ellipse([int(n * 0.10), int(n * 0.80), int(n * 0.90), int(n * 0.95)],
              fill=SOMBRA + (190,))

    # Tres escalones que suben de izquierda a derecha. La silueta escalonada es
    # lo que hace que este icono no se confunda con ninguno de los otros veinte.
    ancho = int(n * 0.21)
    base = int(n * 0.80)
    for i in range(3):
        alto = int(n * (0.14 + i * 0.13))
        x = int(n * 0.14) + i * ancho
        _escalon(d, x, base - alto, ancho, alto)

    # La cinta violeta cruzando: es el color de la Via Luna.
    d.polygon([(int(n * 0.08), int(n * 0.62)), (int(n * 0.30), int(n * 0.52)),
               (int(n * 0.30), int(n * 0.64)), (int(n * 0.08), int(n * 0.74))],
              fill=VIOLETA, outline=CONTORNO, width=S // 2)
    d.polygon([(int(n * 0.08), int(n * 0.74)), (int(n * 0.30), int(n * 0.64)),
               (int(n * 0.30), int(n * 0.70)), (int(n * 0.08), int(n * 0.80))],
              fill=VIOLETA_OSC)

    # La luna creciente sobre el escalon mas alto: la marca de la casa.
    cx, cy, r = int(n * 0.70), int(n * 0.24), int(n * 0.15)
    luna = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    dl = ImageDraw.Draw(luna)
    dl.ellipse([cx - r, cy - r, cx + r, cy + r], fill=ORO_CLA,
               outline=CONTORNO, width=S // 2)
    dl.ellipse([cx - r + int(r * 0.55), cy - r - int(r * 0.05),
                cx + r + int(r * 0.55), cy + r - int(r * 0.05)],
               fill=(0, 0, 0, 0))
    im.alpha_composite(luna)

    # Cuatro destellos, que es lo que dice «esto es un premio».
    for ang, rr, tam in ((0.4, 0.30, 0.030), (2.2, 0.26, 0.022),
                         (3.9, 0.32, 0.026), (5.4, 0.24, 0.018)):
        sx = cx + int(math.cos(ang) * n * rr)
        sy = cy + int(math.sin(ang) * n * rr)
        t = int(n * tam)
        d.polygon([(sx, sy - t), (sx + t // 3, sy), (sx, sy + t), (sx - t // 3, sy)],
                  fill=ORO_CLA)
        d.polygon([(sx - t, sy), (sx, sy - t // 3), (sx + t, sy), (sx, sy + t // 3)],
                  fill=ORO_CLA)

    return im.filter(ImageFilter.SMOOTH)


def main() -> None:
    grande = dibujar()
    FUENTE.parent.mkdir(parents=True, exist_ok=True)
    SALIDA.parent.mkdir(parents=True, exist_ok=True)

    # La fuente se guarda a 512 para que `gen_pokepad.py` la trate como al resto
    # del arte generado por IA (reduce, sangra el alfa y escribe el .mcmeta).
    grande.resize((512, 512), Image.LANCZOS).save(FUENTE)
    grande.resize((LADO, LADO), Image.LANCZOS).save(SALIDA)

    # ⚠ EL .mcmeta NO ES OPCIONAL. Sin `clamp`, OpenGL repite la textura y al
    #   filtrarla mezcla el borde de arriba con el de abajo: sale un marco fino
    #   alrededor del icono, y solo cuando hay que filtrar --que es justo cuando
    #   no se puede permitir--. Regla 4 de docs/ui/dibujado.md.
    meta = SALIDA.with_suffix(".png.mcmeta")
    meta.write_text(
        '{\n  "texture": {\n    "blur": false,\n    "clamp": true\n  }\n}\n',
        encoding="utf-8")

    print(f"PROVISIONAL escrito:")
    print(f"  fuente  {FUENTE.relative_to(RAIZ)}  512x512")
    print(f"  icono   {SALIDA.relative_to(RAIZ)}  {LADO}x{LADO}")
    print(f"  meta    {meta.relative_to(RAIZ)}")
    print()
    print("  El arte definitivo va en la fuente y se instala con gen_pokepad.py")


if __name__ == "__main__":
    main()
