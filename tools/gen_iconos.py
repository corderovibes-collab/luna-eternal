#!/usr/bin/env python3
"""
Dibuja los iconos del Pad.

Por que existiendo prompts para IA: porque un icono de 32x32 no es una
ilustracion, es una senal. A ese tamano gana la SILUETA, no el detalle — y
una IA generativa da justo lo contrario. Estos son geometria limpia y se
leen de un vistazo.

Sirven de dos maneras:
  - como iconos definitivos si el estilo convence;
  - como referencia de silueta si se encargan a un artista.

Si aparece un PNG propio en arte-origen/icono/<nombre>.png, se usa ese y
este script no lo pisa.
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

RAIZ = Path(__file__).resolve().parent.parent
PROPIOS = RAIZ / "arte-origen" / "icono"
DESTINO = (RAIZ / "resourcepack" / "assets" / "lunaeternal"
           / "textures" / "pad" / "icono")

LADO = 32          # se dibuja a 32 y el Pad lo pinta a 24: bordes mas finos
LUZ = (232, 222, 255, 255)      # blanco luna, para los brillos
TRAZO = (18, 14, 32, 255)       # contorno oscuro, da la silueta
MORADO = (140, 112, 220, 255)
MORADO_OSC = (86, 68, 150, 255)
ORO = (232, 179, 60, 255)
ORO_OSC = (168, 120, 30, 255)
VERDE = (120, 210, 150, 255)
MENTA = (159, 232, 216, 255)
ROJO = (226, 96, 110, 255)
AZUL = (110, 170, 240, 255)


def lienzo():
    return Image.new("RGBA", (LADO, LADO), (0, 0, 0, 0))


# ---------------------------------------------------------------- iconos
# Cada funcion dibuja UNA silueta centrada, con contorno. Nada de degradados:
# a 24 px en pantalla, un degradado se convierte en barro.

def pokedex(d):
    d.rounded_rectangle([6, 4, 26, 28], 4, fill=ROJO, outline=TRAZO, width=2)
    d.rounded_rectangle([9, 8, 23, 19], 2, fill=AZUL, outline=TRAZO, width=2)
    d.ellipse([11, 21, 16, 26], fill=LUZ, outline=TRAZO, width=1)
    d.ellipse([18, 22, 21, 25], fill=ORO, outline=TRAZO, width=1)


def cartera(d):
    for i, y in enumerate((20, 15, 10)):
        c = ORO if i == 2 else ORO_OSC
        d.ellipse([7, y, 25, y + 8], fill=c, outline=TRAZO, width=2)
    d.arc([13, 11, 20, 17], 0, 360, fill=TRAZO, width=2)


def vias(d):
    for i, (x, alto) in enumerate(((7, 10), (13, 16), (19, 22))):
        d.rectangle([x, 28 - alto, x + 5, 28], fill=MORADO if i < 2 else LUZ,
                    outline=TRAZO, width=1)
    d.line([(9, 18), (15, 12), (21, 6)], fill=LUZ, width=2)


def misiones(d):
    d.rounded_rectangle([7, 4, 25, 28], 3, fill=LUZ, outline=TRAZO, width=2)
    for y in (10, 15, 20):
        d.line([(11, y), (21, y)], fill=MORADO_OSC, width=2)
    d.line([(11, 24), (14, 27), (22, 19)], fill=VERDE, width=3)


def kits(d):
    d.rounded_rectangle([5, 13, 27, 28], 2, fill=MORADO, outline=TRAZO, width=2)
    d.rounded_rectangle([4, 9, 28, 15], 2, fill=MORADO_OSC, outline=TRAZO, width=2)
    d.line([(16, 9), (16, 28)], fill=ORO, width=3)
    d.polygon([(16, 9), (10, 4), (13, 10)], fill=ORO, outline=TRAZO)
    d.polygon([(16, 9), (22, 4), (19, 10)], fill=ORO, outline=TRAZO)


def tienda(d):
    d.polygon([(4, 14), (28, 14), (25, 6), (7, 6)], fill=ROJO,
              outline=TRAZO)
    for x in (11, 16, 21):
        d.line([(x, 6), (x - 1, 14)], fill=LUZ, width=2)
    d.rectangle([7, 14, 25, 28], fill=MORADO_OSC, outline=TRAZO, width=2)
    d.rectangle([13, 19, 19, 28], fill=MORADO, outline=TRAZO, width=1)


def gts(d):
    d.ellipse([4, 4, 28, 28], fill=AZUL, outline=TRAZO, width=2)
    d.arc([4, 10, 28, 22], 0, 360, fill=TRAZO, width=1)
    d.line([(16, 4), (16, 28)], fill=TRAZO, width=1)
    d.polygon([(9, 12), (16, 9), (16, 15)], fill=LUZ, outline=TRAZO)
    d.polygon([(23, 20), (16, 23), (16, 17)], fill=LUZ, outline=TRAZO)


def centro(d):
    d.ellipse([4, 4, 28, 28], fill=MENTA, outline=TRAZO, width=2)
    d.rectangle([14, 9, 18, 23], fill=ROJO, outline=TRAZO, width=1)
    d.rectangle([9, 14, 23, 18], fill=ROJO, outline=TRAZO, width=1)


def puerta(d):
    d.rectangle([5, 10, 10, 29], fill=MORADO_OSC, outline=TRAZO, width=2)
    d.rectangle([22, 10, 27, 29], fill=MORADO_OSC, outline=TRAZO, width=2)
    d.pieslice([8, 4, 24, 26], 180, 360, fill=MORADO, outline=TRAZO, width=2)
    d.pieslice([11, 8, 21, 24], 180, 360, fill=LUZ, outline=TRAZO, width=1)


def gimnasios(d):
    d.polygon([(16, 3), (27, 10), (27, 22), (16, 29), (5, 22), (5, 10)],
              fill=ORO, outline=TRAZO)
    d.polygon([(16, 9), (22, 13), (22, 20), (16, 24), (10, 20), (10, 13)],
              fill=ORO_OSC, outline=TRAZO)
    d.ellipse([13, 13, 19, 19], fill=LUZ, outline=TRAZO, width=1)


def tesoros(d):
    d.rectangle([5, 15, 27, 28], fill=MORADO_OSC, outline=TRAZO, width=2)
    d.pieslice([5, 7, 27, 23], 180, 360, fill=MORADO, outline=TRAZO, width=2)
    d.line([(5, 16), (27, 16)], fill=TRAZO, width=2)
    d.rectangle([14, 14, 18, 21], fill=ORO, outline=TRAZO, width=1)


def clan(d):
    d.polygon([(16, 3), (28, 8), (26, 21), (16, 29), (6, 21), (4, 8)],
              fill=MORADO, outline=TRAZO)
    d.polygon([(16, 9), (22, 12), (21, 20), (16, 24), (11, 20), (10, 12)],
              fill=LUZ, outline=TRAZO)


def cosmeticos(d):
    d.polygon([(16, 2), (19, 12), (29, 15), (19, 18), (16, 28),
               (13, 18), (3, 15), (13, 12)], fill=LUZ, outline=TRAZO)
    d.ellipse([13, 12, 19, 18], fill=MORADO, outline=TRAZO, width=1)


def cazas(d):
    d.ellipse([3, 3, 29, 29], outline=TRAZO, width=3)
    d.ellipse([3, 3, 29, 29], outline=ROJO, width=2)
    d.ellipse([10, 10, 22, 22], outline=ROJO, width=2)
    d.line([(16, 1), (16, 8)], fill=TRAZO, width=3)
    d.line([(16, 24), (16, 31)], fill=TRAZO, width=3)
    d.line([(1, 16), (8, 16)], fill=TRAZO, width=3)
    d.line([(24, 16), (31, 16)], fill=TRAZO, width=3)
    d.ellipse([14, 14, 18, 18], fill=ROJO)


def explorar(d):
    d.ellipse([3, 3, 29, 29], fill=MORADO_OSC, outline=TRAZO, width=2)
    d.ellipse([7, 7, 25, 25], fill=AZUL, outline=TRAZO, width=1)
    d.polygon([(16, 7), (20, 16), (16, 25), (12, 16)], fill=LUZ,
              outline=TRAZO)
    d.polygon([(16, 7), (20, 16), (16, 16)], fill=ROJO, outline=TRAZO)


ICONOS = {
    "pokedex": pokedex, "cartera": cartera, "vias": vias,
    "misiones": misiones, "kits": kits, "tienda": tienda, "gts": gts,
    "centro": centro, "puerta": puerta, "gimnasios": gimnasios,
    "tesoros": tesoros, "clan": clan, "cosmeticos": cosmeticos,
    "cazas": cazas, "explorar": explorar,
}


def main() -> None:
    DESTINO.mkdir(parents=True, exist_ok=True)
    propios = 0
    for nombre, dibuja in ICONOS.items():
        externo = None
        for ext in (".png", ".webp", ".jpg"):
            p = PROPIOS / f"{nombre}{ext}"
            if p.exists():
                externo = p
                break

        if externo:
            Image.open(externo).convert("RGBA") \
                 .resize((LADO, LADO), Image.LANCZOS) \
                 .save(DESTINO / f"{nombre}.png")
            propios += 1
            print(f"  {nombre:<12} {externo.name}, propio")
            continue

        img = lienzo()
        dibuja(ImageDraw.Draw(img))
        img.save(DESTINO / f"{nombre}.png")
        print(f"  {nombre:<12} dibujado {LADO}x{LADO}")

    print(f"\n{len(ICONOS)} iconos · {propios} propios · "
          f"{len(ICONOS) - propios} dibujados")
    print("Ahora: python tools/gen_resourcepack.py")


if __name__ == "__main__":
    main()
