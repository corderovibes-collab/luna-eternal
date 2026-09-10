# -*- coding: utf-8 -*-
"""
LA TEXTURA: un texel por unidad, como el disfraz.

⚠⚠ EL REPARTO SE CALCULA, y esa regla ya esta pagada en este proyecto: unas
   coordenadas escritas a mano cuadran hasta que alguien cambia un cubo, y
   entonces DOS CUBOS COMPARTEN PIXELES -- que no da ningun error, da la cara
   de una pieza dibujada encima de otra. Se reutiliza `modelo.empaquetar`.

⚠⚠ Y SE PINTA EN PIXEL ART, no en degradado. Un degradado suave delata un
   render 3D y aqui todo lo demas es Minecraft: **cada cara es UN color plano**,
   y ni uno mas. El relieve lo da la geometria y la luz la pone el motor.
"""

from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from trajes import modelo  # noqa: E402
from . import diseno  # noqa: E402


# ⚠⚠⚠ AQUI SE HORNEABA ILUMINACION EN LA TEXTURA --UNA FILA CLARA ARRIBA Y UNA
#    OSCURA ABAJO POR CARA-- Y ERA UN FALLO. Lo destapo el usuario abriendo el
#    modelo en Blockbench: la espada salia como un MOSAICO de tonos, con cada
#    cubo de un amarillo distinto. Y no se veia aqui por dos motivos a la vez:
#
#      1. **EL MOTOR YA SOMBREA LAS CARAS.** Minecraft --y Blockbench-- aclaran
#         la de arriba y oscurecen la de abajo por su cuenta. Hornearlo ademas
#         en la textura lo aplica DOS VECES.
#      2. **A UN TEXEL POR UNIDAD, ESA FILA NO ES UN BORDE: ES MEDIA CARA.**
#         Muchas caras de esta espada miden 2, 3 o 4 pixeles de alto, asi que
#         una fila clara y otra oscura se comen del 25 % al 100 % de la cara --
#         y cubos vecinos acaban con tonos MEDIOS distintos. Eso es el mosaico.
#
#    ⚠⚠ Y EL VISOR LO TAPABA: dibuja plano y ademas aplica su propia luz, asi
#       que en la lamina se veia bien. **Una textura no se juzga en el visor:
#       se juzga en un motor que la ilumine como la va a iluminar el juego.**
#       Las cuatro pasadas de diseño miraron la FORMA, que era lo que tocaba;
#       esto es de COLOR, y ahi la lamina mentia.
#
#    La textura va PLANA. La luz la pone el motor, que es como funciona
#    Minecraft: sus texturas no llevan sombreado propio.


def pintar(piezas, lado=64):
    """
    Devuelve (imagen, cubos ya empaquetados).

    ⚠ El lado se comprueba: si los cubos no caben, `empaquetar` avisa en vez
      de solaparlos en silencio.
    """
    cubos = [p.cubo() for p in piezas]
    modelo.empaquetar(cubos, lado=lado, hueco=1)

    im = Image.new("RGBA", (lado, lado), (0, 0, 0, 0))
    px = im.load()

    for pieza, cubo in zip(piezas, cubos):
        base = cubo.color
        for ox, oy, ancho, alto, nombre in modelo._caras(cubo):
            for y in range(oy, oy + alto):
                for x in range(ox, ox + ancho):
                    px[x, y] = base + (255,)
            # ⚠⚠ AQUI HUBO UNA «CHISPA» PINTADA Y SE RETIRO: a un texel por
            #    unidad, la cara del nucleo mide UN PIXEL de ancho, asi que
            #    cualquier dibujo dentro se pierde. La chispa la hace ahora la
            #    GEOMETRIA, alternando tramos. Queda escrito para que a nadie
            #    le tiente volver a intentarlo en la textura.

    return im, cubos


def guardar(piezas, destino, lado=64):
    im, cubos = pintar(piezas, lado)
    Path(destino).parent.mkdir(parents=True, exist_ok=True)
    im.save(destino)
    return im, cubos
