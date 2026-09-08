#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
EL ICONO DEL PASE DE BATALLA: instalarlo, o dibujar uno provisional.

    python tools/gen_icono_pase.py                          instala lo que haya
    python tools/gen_icono_pase.py --origen "RUTA/al.png"    y ademas lo copia

⚠⚠ EL PROCESADO NO ES SUYO: SE IMPORTA DE `gen_pokepad.py`.

   `preparar` (quitar el fondo + sangrar el alfa), `abrir_hueco`, `a_tamano` y
   `guardar` son las mismas funciones que tratan los otros veinte iconos de la
   rejilla. Copiarlas aqui daria un SEGUNDO pipeline que nada obliga a coincidir
   con el primero, y el sintoma seria «el del pase se ve distinto a los demas»
   -- sin ningun error, y sin nadie que sepa por que.

   ⚠ Por eso este script existe en vez de usar `gen_pokepad.py` directamente:
     aquel EMPIEZA BORRANDO los PNG de todas las aplicaciones y los regenera
     desde `arte/pokepad/icons/`, asi que hoy aborta porque falta
     `icon_torre_batalla.png`. Instalar UN icono no puede depender de que estén
     los veintiuno. (Y la regla de los generadores se cumple igual: este solo
     toca `pase.png` y su `.mcmeta`, que es lo unico que sabe generar.)

⚠ EL PROVISIONAL SIGUE AQUI, y no es codigo muerto: si algun dia falta el arte
  --un clon limpio, un fichero borrado-- dibuja tres escalones dorados y una
  luna creciente en vez de dejar la celda en MAGENTA. Una celda del PokePad
  dibuja su propio icono aunque este bloqueada, asi que dar de alta una
  aplicacion sin su PNG **no da ningun error**: da un cuadro magenta que solo se
  ve abriendo el Pad. Paso el 2026-08-23 con seis pantallas a la vez.
"""

from __future__ import annotations

import argparse
import math
import shutil
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

RAIZ = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(RAIZ / "tools"))

# El pipeline de verdad, el que trata a los demas iconos.
from gen_pokepad import (  # noqa: E402
    ICONO as LADO, abrir_hueco, a_tamano, guardar, preparar,
)

FUENTE = RAIZ / "arte" / "pokepad" / "icons" / "icon_pase.png"
SALIDA = (RAIZ / "mod" / "src" / "client" / "resources" / "assets" / "lunaeternal"
          / "textures" / "gui" / "pokepad" / "pase.png")

# Para el provisional. Se dibuja x8 y se reduce: `polygon` no hace antialiasing.
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
    d.polygon([(x, y), (x + w, y), (x + w + prof, y - prof), (x + prof, y - prof)],
              fill=ORO_CLA, outline=CONTORNO, width=S // 2)
    d.rectangle([x, y, x + w, y + h], fill=ORO, outline=CONTORNO, width=S // 2)
    d.polygon([(x + w, y), (x + w + prof, y - prof),
               (x + w + prof, y + h - prof), (x + w, y + h)],
              fill=ORO_OSC, outline=CONTORNO, width=S // 2)


def provisional() -> Image.Image:
    """
    La misma idea que pide el prompt de Gemini, dibujada a mano.

    ⚠ Dibuja la MISMA SILUETA a proposito: asi lo que se ve en la rejilla
      mientras no hay arte es lo que se vera cuando lo haya, y cambiar el PNG
      no cambia como se lee la pantalla.
    """
    n = LADO * S
    im = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)

    d.ellipse([int(n * 0.10), int(n * 0.80), int(n * 0.90), int(n * 0.95)],
              fill=SOMBRA + (190,))

    ancho = int(n * 0.21)
    base = int(n * 0.80)
    for i in range(3):
        alto = int(n * (0.14 + i * 0.13))
        x = int(n * 0.14) + i * ancho
        _escalon(d, x, base - alto, ancho, alto)

    d.polygon([(int(n * 0.08), int(n * 0.62)), (int(n * 0.30), int(n * 0.52)),
               (int(n * 0.30), int(n * 0.64)), (int(n * 0.08), int(n * 0.74))],
              fill=VIOLETA, outline=CONTORNO, width=S // 2)
    d.polygon([(int(n * 0.08), int(n * 0.74)), (int(n * 0.30), int(n * 0.64)),
               (int(n * 0.30), int(n * 0.70)), (int(n * 0.08), int(n * 0.80))],
              fill=VIOLETA_OSC)

    cx, cy, r = int(n * 0.70), int(n * 0.24), int(n * 0.15)
    luna = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    dl = ImageDraw.Draw(luna)
    dl.ellipse([cx - r, cy - r, cx + r, cy + r], fill=ORO_CLA,
               outline=CONTORNO, width=S // 2)
    dl.ellipse([cx - r + int(r * 0.55), cy - r - int(r * 0.05),
                cx + r + int(r * 0.55), cy + r - int(r * 0.05)],
               fill=(0, 0, 0, 0))
    im.alpha_composite(luna)

    for ang, rr, tam in ((0.4, 0.30, 0.030), (2.2, 0.26, 0.022),
                         (3.9, 0.32, 0.026), (5.4, 0.24, 0.018)):
        sx = cx + int(math.cos(ang) * n * rr)
        sy = cy + int(math.sin(ang) * n * rr)
        t = int(n * tam)
        d.polygon([(sx, sy - t), (sx + t // 3, sy), (sx, sy + t), (sx - t // 3, sy)],
                  fill=ORO_CLA)
        d.polygon([(sx - t, sy), (sx, sy - t // 3), (sx + t, sy), (sx, sy + t // 3)],
                  fill=ORO_CLA)

    return im.filter(ImageFilter.SMOOTH).resize((512, 512), Image.LANCZOS)


def main() -> None:
    ap = argparse.ArgumentParser(description="Instala el icono del Pase.")
    ap.add_argument("--origen", help="PNG del arte; se copia a arte/ y se instala")
    args = ap.parse_args()

    FUENTE.parent.mkdir(parents=True, exist_ok=True)
    SALIDA.parent.mkdir(parents=True, exist_ok=True)

    if args.origen:
        origen = Path(args.origen).expanduser()
        if not origen.exists():
            raise SystemExit(f"No encuentro {origen}")
        shutil.copyfile(origen, FUENTE)
        print(f"  copiado  {origen}  ->  {FUENTE.relative_to(RAIZ)}")

    if FUENTE.exists():
        # ⚠ EL MISMO CAMINO QUE LOS DEMAS ICONOS: quitar el fondo si lo trae,
        #   SANGRAR EL ALFA --que es lo que quita los colores escondidos en los
        #   pixeles invisibles (regla 5 de dibujado.md)-- y llevarlo al lado de
        #   la celda. `abrir_hueco` no hace nada si no hay agujero relleno.
        antes = Image.open(FUENTE)
        icono = a_tamano(abrir_hueco(preparar(FUENTE)), LADO)
        print(f"  arte     {FUENTE.relative_to(RAIZ)}  {antes.size[0]}x{antes.size[1]}"
              f"  ->  {LADO}x{LADO}")
    else:
        icono = a_tamano(provisional(), LADO)
        print("  ⚠ SIN ARTE: se instala el PROVISIONAL. El bueno va en "
              f"{FUENTE.relative_to(RAIZ)}")

    guardar(icono, SALIDA)
    print(f"  icono    {SALIDA.relative_to(RAIZ)}")
    print(f"  meta     {SALIDA.with_suffix('.png.mcmeta').relative_to(RAIZ)}")


if __name__ == "__main__":
    main()
