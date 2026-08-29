# -*- coding: utf-8 -*-
"""
EL TRAJE LEYENDA: por ahora, solo el anillo de Arceus.

Se construye por piezas y se mira cada una antes de montar la siguiente. El
anillo va primero porque es lo que decide si el traje queda espectacular o
toscote -- un circulo con cajas es lo mas dificil de todo el traje.

Los colores salen del boceto del usuario, no de mi cabeza.
"""
import math
import sys
sys.path.insert(0, str(__import__("pathlib").Path(__file__).resolve().parent.parent))

from trajes import visor, modelo as M
from trajes.modelo import Cubo

# Leidos del boceto del usuario
ORO = (240, 180, 32)
ORO_OSC = (198, 140, 20)
MORADO = (91, 45, 130)
MORADO_OSC = (48, 28, 72)
MAGENTA = (232, 92, 232)
ROJO = (200, 55, 55)
BLANCO = (238, 240, 246)
NEGRO = (32, 30, 42)


def bandas(radio_ext, radio_int, paso, y0, alto_banda=None):
    """
    Un aro hecho de BARRAS HORIZONTALES.

    ⚠⚠ Es la unica forma de hacer un circulo con cajas alineadas a los ejes: se
       corta en franjas y cada franja es una barra. Una franja que cruza el
       agujero son DOS barras, izquierda y derecha.

    ⚠ El ancho de cada franja se mide en su borde MAS CERCANO AL ECUADOR, o el
      aro queda mordido por dentro. Es la misma regla que la Poke Ball.
    """
    salida = []
    y = -radio_ext
    while y < radio_ext - 1e-6:
        y1 = min(y + paso, radio_ext)
        borde = min(abs(y), abs(y1))
        xo = math.sqrt(max(0.0, radio_ext ** 2 - borde ** 2))
        # el interior se mide al reves: por el borde MAS LEJANO, o el agujero
        # se come el aro
        lejos = max(abs(y), abs(y1))
        xi = math.sqrt(max(0.0, radio_int ** 2 - lejos ** 2))
        if xi < 0.4:
            salida.append((-xo, xo, y0 + y, y1 - y))
        else:
            salida.append((-xo, -xi, y0 + y, y1 - y))
            salida.append((xi, xo, y0 + y, y1 - y))
        y += paso
    return salida


def anillo(cx, cy, cz):
    """El anillo, plano contra la espalda. `cz` es su cara interior."""
    cubos = []
    grosor = 0.6

    # ---- el disco oscuro del fondo
    for x0, x1, y, h in bandas(4.3, 0, 0.9, cy):
        cubos.append(Cubo((round(cx + x0, 3), round(y, 3), cz),
                          (round(x1 - x0, 3), round(h, 3), 0.4),
                          MORADO_OSC, "tela"))

    # ---- el aro dorado, MAS FINO: en el boceto es un borde, no una rosquilla
    for x0, x1, y, h in bandas(5.0, 4.3, 1.0, cy):
        cubos.append(Cubo((round(cx + x0, 3), round(y, 3), cz),
                          (round(x1 - x0, 3), round(h, 3), grosor),
                          ORO, "metal"))

    # ---- LOS OCHO RADIOS. Son lo que hace que sea la rueda de Arceus y no una
    #      rueda cualquiera, asi que van DELANTE del disco y en oro claro.
    #      ⚠ Los cuatro rectos son una barra; los cuatro diagonales hay que
    #        ESCALONARLOS, porque una caja alineada a los ejes no va en diagonal.
    # ⚠ FINOS. Con radios anchos manda el oro y el boceto es al reves: fondo
    #   oscuro con radios finos encima. Se vio comparando, no se dedujo.
    for dx, dy, w, h in ((-0.4, 1.9, 0.8, 2.3), (-0.4, -4.2, 0.8, 2.3),
                         (1.9, -0.4, 2.3, 0.8), (-4.2, -0.4, 2.3, 0.8)):
        cubos.append(Cubo((cx + dx, cy + dy, cz + 0.25), (w, h, 0.45),
                          ORO, "metal"))
    for sx in (-1, 1):
        for sy in (-1, 1):
            for paso_d in (1.5, 2.4, 3.3):
                lado = 0.85
                cubos.append(Cubo(
                    (round(cx + sx * paso_d - (lado / 2 if sx > 0 else lado / 2), 3),
                     round(cy + sy * paso_d - (lado / 2 if sy > 0 else lado / 2), 3),
                     cz + 0.25),
                    (lado, lado, 0.45), ORO, "metal"))

    # ---- las ocho gemas, sobre el aro
    for k in range(8):
        a = math.radians(k * 45 + 22.5)
        gx = cx + math.cos(a) * 4.5 - 0.55
        gy = cy + math.sin(a) * 4.5 - 0.55
        cubos.append(Cubo((round(gx, 3), round(gy, 3), cz + grosor - 0.1),
                          (1.1, 1.1, 0.4), MAGENTA, "neon"))

    # ---- la Poke Ball del centro, MAS GRANDE: en el boceto manda el centro
    for r, y0, y1, color in ((1.2, 1.0, 1.9, ROJO), (1.9, 0.25, 1.0, ROJO),
                             (1.95, -0.25, 0.25, NEGRO),
                             (1.9, -1.0, -0.25, BLANCO),
                             (1.2, -1.9, -1.0, BLANCO)):
        cubos.append(Cubo((round(cx - r, 3), round(cy + y0, 3), cz + 0.55),
                          (round(r * 2, 3), round(y1 - y0, 3), 0.5), color, "tela"))
    cubos.append(Cubo((cx - 0.75, cy - 0.75, cz + 1.0), (1.5, 1.5, 0.3),
                      NEGRO, "metal"))
    cubos.append(Cubo((cx - 0.45, cy - 0.45, cz + 1.15), (0.9, 0.9, 0.25),
                      BLANCO, "tela"))
    return cubos


if __name__ == "__main__":
    t = M.Traje("anillo", "El anillo de Arceus")
    t.poner("armorBody", *anillo(0, 18, 2.6))
    cubos = [c for l in t.de_pieza("body").values() for c in l]
    print("  %d cubos" % len(cubos))
    alto = M.empaquetar(cubos, 128)
    print("  textura: %d px de alto de 128" % alto)
    tex = {"body": M.pintar(cubos, 128, 3)}
    visor.lamina(t, "build/trajes/anillo.png", 13.0, tex)
    # ⚠ Y la espalda A LO GRANDE: es donde vive el anillo y en la lamina sale
    #   del tamaño de una moneda.
    visor.dibujar(t, 180, 0, 340, 430, 22.0, tex).save(
        "build/trajes/anillo_espalda.png")
    print("  -> build/trajes/anillo.png y anillo_espalda.png")
