# -*- coding: utf-8 -*-
"""
LA TEXTURA: un texel por unidad, como el disfraz.

⚠⚠ EL REPARTO SE CALCULA, y esa regla ya esta pagada en este proyecto: unas
   coordenadas escritas a mano cuadran hasta que alguien cambia un cubo, y
   entonces DOS CUBOS COMPARTEN PIXELES -- que no da ningun error, da la cara
   de una pieza dibujada encima de otra. Se reutiliza `modelo.empaquetar`.

⚠⚠ Y SE PINTA EN PIXEL ART, no en degradado. Un degradado suave delata un
   render 3D y aqui todo lo demas es Minecraft: cada cara es color plano con
   UNA linea mas clara arriba y UNA mas oscura abajo. Nada mas.
"""

from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from trajes import modelo  # noqa: E402
from . import diseno  # noqa: E402


def _mezcla(color, delta):
    return tuple(max(0, min(255, c + delta)) for c in color)


# Cuanto se aclara el borde de arriba y se oscurece el de abajo de cada cara.
# ⚠ Son pocos a proposito: la rampa de Pikachu ya es corta (cinco amarillos en
#   total), asi que un sombreado fuerte se saldria de su paleta.
LUZ_ARRIBA = 16
LUZ_ABAJO = -22


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
            # ⚠⚠ LAS TAPAS VAN MAS CLARAS Y LOS SUELOS MAS OSCUROS, y no es
            #    adorno: sin eso una espada de color plano se ve como una
            #    silueta recortada y pierde todo el volumen que le dan los
            #    cubos. Es la misma luz que aplica el visor.
            if nombre == "arriba":
                cara = _mezcla(base, LUZ_ARRIBA)
            elif nombre == "abajo":
                cara = _mezcla(base, LUZ_ABAJO)
            else:
                cara = base
            for y in range(oy, oy + alto):
                for x in range(ox, ox + ancho):
                    px[x, y] = cara + (255,)
            # El filo de arriba y el de abajo, un pixel cada uno.
            if alto >= 3 and nombre in ("frente", "espalda", "derecha", "izquierda"):
                for x in range(ox, ox + ancho):
                    px[x, oy] = _mezcla(base, LUZ_ARRIBA) + (255,)
                    px[x, oy + alto - 1] = _mezcla(base, LUZ_ABAJO) + (255,)
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
