# -*- coding: utf-8 -*-
"""
EL VISOR DE LA ESPADA.

⚠⚠⚠ NO ES UN EXTRA, Y ESTA LECCION YA ESTA PAGADA EN ESTE PROYECTO. Sin
   visor, un modelo se escribe A CIEGAS: se genera un fichero que dice «cubo
   aqui, cubo alla» y no se ve el resultado hasta abrir Blockbench. Con el son
   diez intentos en dos minutos.

⚠⚠ Y DIBUJA A PIKACHU AL LADO, que es la mitad que de verdad importa aqui. El
   encargo no es «una espada bonita»: es una espada que **puesta junto a ESTE
   Pikachu parezca del mismo set**. Flotando sola en negro, cualquier escala
   parece correcta -- la de un cuchillo y la de una lanza.

Se reutilizan las primitivas del visor de trajes (matriz, quads, z-buffer y
muestreo). Ahi esta escrito que la orientacion de las caras «es un acuerdo
entre dos ficheros»: usando las suyas, el acuerdo no se puede romper.
"""

from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from trajes import modelo  # noqa: E402
from trajes.visor import (FONDO, LUZ, _matriz, _muestreo_plano,  # noqa: E402
                          _muestreo_textura, _pintar_quad, _quads)

# El cuerpo del personaje, en unidades enteras: es LITERALMENTE lo que trae el
# .bbmodel de referencia (cabeza 8x8x8 en y=24, torso 8x12x4, brazos 4x12x4,
# piernas 4x12x4) mas sus orejas y su cola.
PIKACHU = [
    ((-4, 24, -4), (8, 8, 8)),        # cabeza
    ((-4, 12, -2), (8, 12, 4)),       # torso
    ((-8, 12, -2), (4, 12, 4)),       # brazo derecho
    ((4, 12, -2), (4, 12, 4)),        # brazo izquierdo
    ((-3.9, 0, -2), (4, 12, 4)),      # pierna derecha
    ((-0.1, 0, -2), (4, 12, 4)),      # pierna izquierda
    ((0.75, 31.75, 1.5), (4, 5, 0.25)),    # oreja derecha
    ((-4.75, 31.75, 1.5), (4, 5, 0.25)),   # oreja izquierda
    ((0, 11.75, 3), (0.25, 5, 6)),         # cola
    ((0, 16.75, 3), (0.25, 4, 8)),
    ((0, 20.75, 4), (0.25, 7, 13)),
]
CUERPO = (0xE0, 0xA2, 0x26)

VISTAS = [("FRENTE", 0, 0), ("3/4", 35, 12), ("LADO", 90, 0), ("ESPALDA", 180, 0)]


def dibujar(cubos, giro, inclina, ancho=260, alto=460, escala=11.0,
            textura=None, con_pikachu=False, desplazar=(0.0, 0.0, 0.0)):
    color_buf = np.zeros((alto, ancho, 4), dtype=np.int16)
    color_buf[:] = FONDO
    z_buf = np.full((alto, ancho), 1e9)
    m = _matriz(giro, inclina)
    cx, cy = ancho / 2.0, alto - 30

    if con_pikachu:
        for origen, tam in PIKACHU:
            for p0, p1, p3, cara in _quads(origen, tam):
                _pintar_quad(color_buf, z_buf, m, p0, p1, p3, escala, cx, cy,
                             _muestreo_plano(CUERPO, LUZ[cara] - 40))

    for c in cubos:
        caras = {n: (ox, oy, aw, ah) for ox, oy, aw, ah, n in modelo._caras(c)}
        origen = tuple(c.origen[i] + desplazar[i] for i in range(3))
        for p0, p1, p3, cara in _quads(origen, c.tam):
            if textura is not None:
                ox, oy, aw, ah = caras[cara]
                mu = _muestreo_textura(textura, ox, oy, aw, ah, LUZ[cara] // 2)
            else:
                mu = _muestreo_plano(c.color, LUZ[cara])
            _pintar_quad(color_buf, z_buf, m, p0, p1, p3, escala, cx, cy, mu)

    return Image.fromarray(color_buf.astype(np.uint8), "RGBA")


def lamina(cubos, destino, textura=None, escala=11.0):
    """Las cuatro vistas de la espada sola, juntas."""
    ancho, alto = 260, 460
    hoja = Image.new("RGBA", (ancho * len(VISTAS), alto + 26), FONDO)
    d = ImageDraw.Draw(hoja)
    for i, (nombre, giro, inclina) in enumerate(VISTAS):
        im = dibujar(cubos, giro, inclina, ancho, alto, escala, textura)
        hoja.paste(im, (ancho * i, 26))
        d.text((ancho * i + 10, 8), nombre, fill=(210, 220, 240, 255))
    Path(destino).parent.mkdir(parents=True, exist_ok=True)
    hoja.save(destino)
    return hoja


def junto_a_pikachu(cubos, destino, textura=None, escala=9.0):
    """
    La espada al lado del personaje, a la MISMA escala.

    ⚠ La espada se corre a la derecha y se sube hasta la altura de la mano
      (y=12): asi la comparacion es la de verdad --«¿puede sostener esto?»--
      y no una de dos objetos flotando cada uno por su lado.
    """
    ancho, alto = 420, 520
    hoja = Image.new("RGBA", (ancho * 2, alto + 26), FONDO)
    d = ImageDraw.Draw(hoja)
    for i, (nombre, giro, inclina) in enumerate([("FRENTE", 0, 0), ("3/4", 35, 12)]):
        im = dibujar(cubos, giro, inclina, ancho, alto, escala, textura,
                     con_pikachu=True, desplazar=(9.5, 5.0, 0.0))
        hoja.paste(im, (ancho * i, 26))
        d.text((ancho * i + 10, 8), "JUNTO A PIKACHU · " + nombre,
               fill=(210, 220, 240, 255))
    Path(destino).parent.mkdir(parents=True, exist_ok=True)
    hoja.save(destino)
    return hoja
