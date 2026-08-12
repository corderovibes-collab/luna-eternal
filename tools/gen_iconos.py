#!/usr/bin/env python3
"""
Dibuja los iconos del Pad, con volumen.

No hay 3D real y no hace falta. El aspecto de relieve sale de tres cosas
baratas y muy efectivas a este tamano:

    1. degradado vertical  (claro arriba, oscuro abajo)
    2. contorno oscuro     (recorta la silueta contra el fondo)
    3. brillo especular    (una mancha clara arriba a la izquierda)

Se dibuja a 64 px y el Pad lo pinta a 32. Esa reduccion es la que suaviza los
bordes y termina de venderlo: a tamano real parecen modelados.

Si aparece un PNG propio en arte-origen/icono/<nombre>.png, se usa ese.
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

RAIZ = Path(__file__).resolve().parent.parent
PROPIOS = RAIZ / "arte-origen" / "icono"
DESTINO = (RAIZ / "resourcepack" / "assets" / "lunaeternal"
           / "textures" / "pad" / "icono")

LADO = 64
TRAZO = (38, 34, 50, 255)          # contorno, casi negro pero no del todo

# Paleta amable, la de la referencia: saturada pero no chillona.
ROJO, ROJO_OSC = (232, 76, 62, 255), (156, 34, 26, 255)
AZUL, AZUL_OSC = (94, 176, 232, 255), (36, 106, 168, 255)
VERDE, VERDE_OSC = (124, 206, 118, 255), (54, 138, 66, 255)
ORO, ORO_OSC = (250, 206, 78, 255), (198, 138, 22, 255)
MORADO, MORADO_OSC = (168, 130, 236, 255), (104, 70, 172, 255)
BLANCO, GRIS = (255, 255, 255, 255), (206, 214, 226, 255)
MARRON, MARRON_OSC = (176, 122, 74, 255), (112, 72, 40, 255)
ROSA, ROSA_OSC = (246, 150, 176, 255), (206, 92, 128, 255)


def _grad(w, h, a, b):
    img = Image.new("RGBA", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / max(1, h - 1)
        d.line([(0, y), (w, y)],
               fill=tuple(round(x + (y2 - x) * t) for x, y2 in zip(a, b)))
    return img


def _forma(img, metodo, args, c1, c2, borde, ancho, radio=None):
    """Rellena con degradado y perfila. El corazon de todo el fichero."""
    mascara = Image.new("L", img.size, 0)
    dm = ImageDraw.Draw(mascara)
    if radio is None:
        getattr(dm, metodo)(args, fill=255)
    else:
        dm.rounded_rectangle(args, radio, fill=255)
    img.paste(_grad(img.width, img.height, c1, c2), (0, 0), mascara)
    if borde:
        d = ImageDraw.Draw(img)
        if radio is None:
            getattr(d, metodo)(args, outline=borde, width=ancho)
        else:
            d.rounded_rectangle(args, radio, outline=borde, width=ancho)


def elipse(img, caja, c1, c2, borde=TRAZO, w=3):
    _forma(img, "ellipse", caja, c1, c2, borde, w)


def caja(img, c, r, c1, c2, borde=TRAZO, w=3):
    _forma(img, "rounded_rectangle", c, c1, c2, borde, w, radio=r)


def poli(img, pts, c1, c2, borde=TRAZO, w=3):
    _forma(img, "polygon", pts, c1, c2, borde, w)


def arco(img, caja_, ini, fin, c1, c2, borde=TRAZO, w=3):
    """Media luna rellena. pieslice no encaja en _forma porque pide
    angulos, asi que va aparte."""
    mascara = Image.new("L", img.size, 0)
    ImageDraw.Draw(mascara).pieslice(caja_, ini, fin, fill=255)
    img.paste(_grad(img.width, img.height, c1, c2), (0, 0), mascara)
    if borde:
        ImageDraw.Draw(img).pieslice(caja_, ini, fin, outline=borde, width=w)


def brillo(img, caja_):
    """Mancha clara arriba a la izquierda. Es la luz especular."""
    capa = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(capa).ellipse(caja_, fill=(255, 255, 255, 105))
    capa = capa.filter(ImageFilter.GaussianBlur(1.5))
    # Solo donde ya hay icono: si no, la luz flota en el aire.
    capa.putalpha(Image.composite(capa.split()[3],
                                  Image.new("L", img.size, 0),
                                  img.split()[3]))
    img.alpha_composite(capa)


def sombra(img):
    """Sombra propia bajo el icono. Lo despega del fondo de la celda."""
    s = Image.new("RGBA", img.size, (0, 0, 0, 0))
    s.paste((0, 0, 0, 90), (0, 0), img.split()[3])
    s = s.filter(ImageFilter.GaussianBlur(2))
    base = Image.new("RGBA", img.size, (0, 0, 0, 0))
    base.alpha_composite(s, (0, 3))
    base.alpha_composite(img)
    return base


# ---------------------------------------------------------------- iconos

def pokedex(i):
    caja(i, [10, 6, 54, 58], 8, (240, 96, 84, 255), ROJO_OSC)
    caja(i, [16, 13, 48, 34], 4, (150, 214, 246, 255), AZUL_OSC, w=2)
    elipse(i, [17, 40, 31, 54], BLANCO, GRIS, w=2)
    elipse(i, [21, 44, 27, 50], (240, 96, 84, 255), ROJO_OSC, w=2)
    elipse(i, [38, 43, 46, 51], ORO, ORO_OSC, w=2)
    brillo(i, [16, 10, 38, 26])


def cartera(i):
    for n, y in enumerate((40, 28, 16)):
        c1, c2 = (ORO, ORO_OSC) if n == 2 else ((242, 190, 60, 255), ORO_OSC)
        elipse(i, [10, y, 54, y + 18], c1, c2)
    ImageDraw.Draw(i).arc([26, 20, 40, 32], 0, 360, fill=MARRON_OSC, width=3)
    brillo(i, [16, 18, 34, 28])


def cartera_moneda(i):
    cartera(i)


def vias(i):
    for x, alto, c1, c2 in ((10, 20, AZUL, AZUL_OSC),
                            (26, 34, MORADO, MORADO_OSC),
                            (42, 48, VERDE, VERDE_OSC)):
        caja(i, [x, 58 - alto, x + 13, 58], 3, c1, c2, w=2)
    brillo(i, [12, 14, 30, 28])


def misiones(i):
    caja(i, [10, 6, 54, 58], 5, BLANCO, GRIS)
    d = ImageDraw.Draw(i)
    for y in (20, 30, 40):
        d.line([(20, y), (44, y)], fill=(150, 162, 180, 255), width=3)
    d.line([(18, 48), (26, 56), (48, 34)], fill=(60, 190, 90, 255), width=7)
    d.line([(18, 48), (26, 56), (48, 34)], fill=(120, 226, 140, 255), width=3)
    brillo(i, [14, 10, 34, 24])


def kits(i):
    caja(i, [8, 24, 56, 58], 4, (200, 96, 90, 255), ROJO_OSC)
    caja(i, [4, 18, 60, 32], 4, (240, 116, 106, 255), ROJO_OSC)
    d = ImageDraw.Draw(i)
    d.rectangle([28, 18, 36, 58], fill=ORO, outline=ORO_OSC, width=2)
    poli(i, [(32, 18), (14, 4), (24, 20)], ORO, ORO_OSC, w=2)
    poli(i, [(32, 18), (50, 4), (40, 20)], ORO, ORO_OSC, w=2)
    brillo(i, [10, 20, 30, 30])


def tienda(i):
    poli(i, [(4, 30), (60, 30), (54, 12), (10, 12)], (250, 250, 250, 255), GRIS)
    d = ImageDraw.Draw(i)
    for n, x in enumerate(range(12, 56, 11)):
        if n % 2 == 0:
            d.polygon([(x, 12), (x + 11, 12), (x + 8, 30), (x - 3, 30)],
                      fill=(232, 76, 62, 255))
    d.polygon([(4, 30), (60, 30), (54, 12), (10, 12)], outline=TRAZO, width=3)
    caja(i, [10, 30, 54, 58], 3, (222, 226, 234, 255), (150, 158, 172, 255))
    caja(i, [24, 40, 40, 58], 2, AZUL, AZUL_OSC, w=2)
    brillo(i, [12, 32, 28, 42])


def gts(i):
    elipse(i, [4, 4, 60, 60], (120, 196, 246, 255), AZUL_OSC)
    d = ImageDraw.Draw(i)
    d.arc([4, 18, 60, 46], 0, 360, fill=(36, 106, 168, 200), width=2)
    d.line([(32, 5), (32, 59)], fill=(36, 106, 168, 200), width=2)
    poli(i, [(14, 24), (32, 16), (32, 32)], BLANCO, GRIS, w=2)
    poli(i, [(50, 40), (32, 48), (32, 32)], BLANCO, GRIS, w=2)
    brillo(i, [12, 10, 34, 26])


def centro(i):
    elipse(i, [4, 4, 60, 60], (170, 236, 220, 255), (58, 158, 140, 255))
    caja(i, [26, 14, 38, 50], 2, (240, 96, 84, 255), ROJO_OSC, w=2)
    caja(i, [14, 26, 50, 38], 2, (240, 96, 84, 255), ROJO_OSC, w=2)
    brillo(i, [12, 10, 34, 26])


def puerta(i):
    caja(i, [6, 20, 18, 60], 3, (200, 206, 218, 255), (130, 138, 152, 255))
    caja(i, [46, 20, 58, 60], 3, (200, 206, 218, 255), (130, 138, 152, 255))
    arco(i, [14, 6, 50, 56], 180, 360, (188, 154, 250, 255), MORADO_OSC)
    ImageDraw.Draw(i).pieslice([20, 14, 44, 54], 180, 360,
                               fill=(238, 226, 255, 235))
    brillo(i, [18, 12, 38, 28])


def gimnasios(i):
    poli(i, [(32, 4), (56, 18), (56, 46), (32, 60), (8, 46), (8, 18)],
         (252, 216, 96, 255), ORO_OSC)
    poli(i, [(32, 16), (46, 24), (46, 40), (32, 48), (18, 40), (18, 24)],
         (232, 176, 40, 255), (168, 112, 12, 255), w=2)
    elipse(i, [26, 26, 38, 38], BLANCO, GRIS, w=2)
    brillo(i, [16, 10, 36, 26])


def tesoros(i):
    caja(i, [6, 28, 58, 58], 3, (196, 138, 84, 255), MARRON_OSC)
    arco(i, [6, 12, 58, 44], 180, 360, (214, 160, 100, 255), MARRON_OSC)
    ImageDraw.Draw(i).rectangle([6, 28, 58, 32], fill=ORO_OSC)
    caja(i, [26, 26, 38, 42], 2, ORO, ORO_OSC, w=2)
    brillo(i, [12, 14, 32, 26])


def clan(i):
    poli(i, [(32, 4), (58, 14), (54, 44), (32, 60), (10, 44), (6, 14)],
         (120, 196, 246, 255), AZUL_OSC)
    poli(i, [(32, 16), (46, 22), (44, 40), (32, 50), (20, 40), (18, 22)],
         BLANCO, GRIS, w=2)
    brillo(i, [16, 10, 36, 26])


def cosmeticos(i):
    poli(i, [(32, 2), (39, 24), (62, 32), (39, 40), (32, 62),
             (25, 40), (2, 32), (25, 24)], (255, 244, 190, 255), ORO)
    elipse(i, [24, 24, 40, 40], BLANCO, ORO, w=2)
    brillo(i, [20, 12, 38, 28])


def cazas(i):
    elipse(i, [4, 4, 60, 60], BLANCO, GRIS)
    d = ImageDraw.Draw(i)
    d.ellipse([14, 14, 50, 50], outline=(232, 76, 62, 255), width=5)
    for a, b in (((32, 0), (32, 16)), ((32, 48), (32, 64)),
                 ((0, 32), (16, 32)), ((48, 32), (64, 32))):
        d.line([a, b], fill=(232, 76, 62, 255), width=5)
    elipse(i, [27, 27, 37, 37], (240, 96, 84, 255), ROJO_OSC, w=2)
    brillo(i, [12, 10, 32, 26])


def explorar(i):
    elipse(i, [4, 4, 60, 60], (200, 208, 220, 255), (120, 128, 142, 255))
    elipse(i, [12, 12, 52, 52], (120, 196, 246, 255), AZUL_OSC, w=2)
    poli(i, [(32, 14), (40, 32), (32, 50), (24, 32)], BLANCO, GRIS, w=2)
    poli(i, [(32, 14), (40, 32), (32, 32)], (240, 96, 84, 255), ROJO_OSC, w=2)
    brillo(i, [14, 10, 34, 26])


def moneda_dolar(i):
    elipse(i, [6, 6, 58, 58], (252, 216, 96, 255), ORO_OSC)
    elipse(i, [14, 14, 50, 50], (250, 206, 78, 255), (214, 156, 34, 255), w=2)
    d = ImageDraw.Draw(i)
    d.line([(32, 20), (32, 44)], fill=(140, 96, 10, 255), width=4)
    d.arc([22, 20, 42, 32], 180, 360, fill=(140, 96, 10, 255), width=4)
    d.arc([22, 32, 42, 44], 0, 180, fill=(140, 96, 10, 255), width=4)
    brillo(i, [14, 12, 34, 28])


def moneda_marca(i):
    poli(i, [(32, 4), (56, 32), (32, 60), (8, 32)],
         (150, 214, 250, 255), AZUL_OSC)
    poli(i, [(32, 16), (44, 32), (32, 48), (20, 32)], BLANCO, (170, 220, 250, 255), w=2)
    brillo(i, [18, 12, 36, 28])


def moneda_premium(i):
    elipse(i, [6, 6, 58, 58], (254, 232, 150, 255), (206, 148, 24, 255))
    poli(i, [(32, 14), (38, 28), (52, 30), (41, 40), (44, 54),
             (32, 46), (20, 54), (23, 40), (12, 30), (26, 28)],
         (255, 250, 210, 255), (222, 168, 40, 255), w=2)
    brillo(i, [14, 12, 34, 28])


ICONOS = {
    "moneda_dolar": moneda_dolar, "moneda_marca": moneda_marca,
    "moneda_premium": moneda_premium,
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
        externo = next((PROPIOS / f"{nombre}{e}" for e in (".png", ".webp", ".jpg")
                        if (PROPIOS / f"{nombre}{e}").exists()), None)
        if externo:
            Image.open(externo).convert("RGBA") \
                 .resize((LADO, LADO), Image.LANCZOS) \
                 .save(DESTINO / f"{nombre}.png")
            propios += 1
            print(f"  {nombre:<12} {externo.name}, propio")
            continue

        img = Image.new("RGBA", (LADO, LADO), (0, 0, 0, 0))
        dibuja(img)
        sombra(img).save(DESTINO / f"{nombre}.png")
        print(f"  {nombre:<12} dibujado {LADO}x{LADO}")

    print(f"\n{len(ICONOS)} iconos · {propios} propios · "
          f"{len(ICONOS) - propios} dibujados")


if __name__ == "__main__":
    main()
