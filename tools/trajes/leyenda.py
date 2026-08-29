# -*- coding: utf-8 -*-
"""
EL TRAJE LEYENDA, medido de la referencia voxel del usuario.

⚠⚠⚠ ESTE NO SE INTERPRETA, SE MIDE. Las versiones anteriores salieron de una
   ilustracion y habia que adivinar el volumen. La referencia buena YA ES DE
   BLOQUES: se cuentan en la imagen y se trasladan. Es la diferencia entre
   traducir y copiar.

⚠⚠ LA RUEDA VA DETRAS DE LA CABEZA, NO EN LA ESPALDA (decision del usuario,
   «justo asi como la foto»). Y no es un cambio cosmetico: cambia de PIEZA --
   del peto al casco-- y por tanto de comportamiento. Colgada de la cabeza, el
   halo GIRA CUANDO EL JUGADOR MIRA, que es lo que hace que se lea como un halo
   y no como una mochila.

⚠ Todo va inflado 0,4 sobre el cuerpo. Con 0,25 se pega a la capa exterior de la
  skin del jugador y PARPADEA -- ya nos paso con el NOVATO.
"""

import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from trajes import visor, modelo as M          # noqa: E402
from trajes.modelo import Cubo                  # noqa: E402

# Muestreados de la referencia, no elegidos.
BLANCO = (240, 238, 243)
LAVANDA = (198, 190, 214)
ORO = (243, 191, 60)
ORO_OSC = (198, 148, 32)
MORADO = (122, 52, 158)
MORADO_OSC = (92, 38, 122)
MAGENTA = (232, 44, 220)
VERDE = (60, 200, 80)
ROJO = (215, 45, 70)
AZUL = (60, 110, 220)
CIAN = (70, 200, 220)
NEGRO = (38, 32, 46)


# ---------------------------------------------------------------- utilidades

def aro(radio_ext, radio_int, paso, cy, cz, grosor, color, material="metal",
        desde=None):
    """
    Un aro hecho de BARRAS HORIZONTALES.

    ⚠⚠ Es la unica forma de hacer un circulo con cajas alineadas a los ejes: se
       corta en franjas y cada franja es una barra. Una franja que cruza el
       agujero son DOS barras, izquierda y derecha.

    ⚠ El radio exterior se mide en el borde MAS CERCANO al ecuador y el interior
      en el MAS LEJANO. Al reves, el aro queda mordido por dentro.

    `desde` corta el aro por abajo: el halo de la referencia esta abierto.
    """
    cubos = []
    y = -radio_ext
    while y < radio_ext - 1e-6:
        y1 = min(y + paso, radio_ext)
        if desde is not None and y1 <= desde:
            y += paso
            continue
        xo = math.sqrt(max(0.0, radio_ext ** 2 - min(abs(y), abs(y1)) ** 2))
        xi = math.sqrt(max(0.0, radio_int ** 2 - max(abs(y), abs(y1)) ** 2))
        tramos = ([(-xo, xo)] if xi < 0.4 else [(-xo, -xi), (xi, xo)])
        for x0, x1 in tramos:
            cubos.append(Cubo((round(x0, 3), round(cy + y, 3), cz),
                              (round(x1 - x0, 3), round(y1 - y, 3), grosor),
                              color, material))
        y += paso
    return cubos


def disco(cx, cy, cz, radios, color, grosor, material="tela"):
    """Un ovalo escalonado: `radios` son (radio, y0, y1) de abajo arriba."""
    return [Cubo((round(cx - r, 3), round(cy + y0, 3), cz),
                 (round(r * 2, 3), round(y1 - y0, 3), grosor), color, material)
            for r, y0, y1 in radios]


# ---------------------------------------------------------------- el casco

def casco():
    """
    Yelmo, cuernos y el halo.

    ⚠⚠ LA CABEZA DE VANILLA VA DE y=24 A y=32. El yelmo la envuelve por arriba,
       atras y los lados, y por delante baja solo hasta la ceja: la cara se deja
       al aire porque es la unica parte del jugador que sigue siendo suya.
    """
    c = []
    # ---- el halo, DETRAS de la cabeza
    #      ⚠ Abierto por abajo, como en la referencia: cerrarlo lo convertiria
    #        en un plato y taparia los hombros.
    c += aro(8.4, 6.9, 1.0, 30.0, 4.6, 0.7, ORO, "metal", desde=-5.5)
    c += aro(6.9, 5.6, 1.0, 30.0, 4.5, 0.5, LAVANDA, "tela", desde=-5.0)
    # las gemas del halo, en su sitio de la referencia
    for ang, color in ((90, VERDE), (140, ROJO), (40, AZUL),
                       (176, CIAN), (4, CIAN)):
        a = math.radians(ang)
        c.append(Cubo((round(math.cos(a) * 7.6 - 0.7, 3),
                       round(30.0 + math.sin(a) * 7.6 - 0.7, 3), 4.4),
                      (1.4, 1.4, 1.0), color, "neon"))

    # ---- el yelmo
    c.append(Cubo((-4.4, 31.2, -4.4), (8.8, 2.2, 8.8), BLANCO, "metal"))
    c.append(Cubo((-4.4, 24.6, 1.8), (8.8, 6.8, 2.6), BLANCO, "metal"))
    for x in (-4.5, 2.7):
        c.append(Cubo((x, 25.0, -4.6), (1.8, 6.4, 8.4), BLANCO, "metal"))
    # la banda dorada de la ceja y la punta central
    c.append(Cubo((-4.6, 30.0, -4.7), (9.2, 1.5, 1.0), ORO, "metal"))
    c.append(Cubo((-0.9, 28.6, -4.8), (1.8, 1.6, 0.8), ORO, "metal"))
    for x in (-4.7, 2.9):
        c.append(Cubo((x, 26.2, -4.8), (1.8, 4.0, 0.9), ORO, "metal"))

    # ---- LOS CUERNOS: una escalera que se estrecha, que es la unica forma de
    #      curvar con cajas alineadas a los ejes.
    #      ⚠ El remate NO se hace diminuto: por debajo de medio bloque se ve como
    #        una mota que parpadea, no como una punta.
    for sep, y, w, h, d, col in ((3.8, 31.0, 2.4, 1.8, 3.0, BLANCO),
                                 (4.9, 32.4, 2.1, 1.7, 2.6, BLANCO),
                                 (5.7, 33.7, 1.7, 1.6, 2.2, ORO),
                                 (6.1, 34.9, 1.3, 1.4, 1.8, ORO)):
        for signo in (-1, 1):
            x = sep - w if signo > 0 else -sep
            c.append(Cubo((round(x, 3), y, round(-d / 2, 3)), (w, h, d),
                          col, "metal"))
    return c


# ---------------------------------------------------------------- el peto

def pecho():
    """Coraza blanca, túnica morada, la gema y el cinturón."""
    c = []
    # ---- la tunica morada de debajo
    c.append(Cubo((-4.4, 12.6, -2.6), (8.8, 10.8, 5.2), MORADO, "tela"))
    # ---- las placas blancas: pecho, costados y hombros
    c.append(Cubo((-4.6, 19.2, -2.9), (9.2, 4.4, 5.8), BLANCO, "metal"))
    for x in (-4.8, 3.4):
        c.append(Cubo((x, 14.0, -3.0), (1.4, 9.6, 6.0), BLANCO, "metal"))
    c.append(Cubo((-4.7, 22.8, -2.9), (9.4, 1.6, 5.8), ORO, "metal"))
    c.append(Cubo((-4.8, 18.8, -3.0), (9.6, 0.8, 6.0), ORO, "metal"))

    # ---- LA GEMA DEL PECHO, en su aro. Es lo primero que se ve de lejos.
    c += aro(2.6, 1.5, 0.85, 20.6, -3.1, 0.55, ORO)
    c += disco(0, 20.6, -3.5, ((1.0, 0.6, 1.4), (1.5, -0.6, 0.6),
                               (1.0, -1.4, -0.6)), MAGENTA, 0.5, "neon")

    # ---- la faja y el faldon morado que cae por delante
    c.append(Cubo((-4.7, 12.2, -2.9), (9.4, 2.0, 5.8), ORO, "metal"))
    c.append(Cubo((-1.9, 13.4, -3.2), (3.8, 9.6, 0.6), MORADO_OSC, "tela"))
    c.append(Cubo((-1.5, 12.6, -3.3), (3.0, 0.9, 0.5), ORO, "metal"))
    return c


# ---------------------------------------------------------------- los brazos

def brazos():
    """
    Las alas y el brazo. Devuelve (derecho, izquierdo): son ESPEJO, no copia.

    ⚠⚠ EL ALA ES UNA MASA ESCALONADA CON SOLAPE, no plumas sueltas. Lo probe con
       piezas separadas y quedo como patas de araña: entre pieza y pieza se veia
       el fondo. Cada tramo tiene que morder al anterior.

    ⚠⚠ Y la hombrera llega a 24,2 porque el brazo de vanilla acaba en 24. Si
       parara antes queda un anillo de piel en cada hombro -- y el cuello NO lo
       tapa, porque solo mide 9,2 y los brazos viven mas afuera.
    """
    salida = []
    for lado in ("Right", "Left"):
        c = []
        base = -8.0 if lado == "Right" else 4.0
        fuera = -1 if lado == "Right" else 1
        # manga morada y guantelete blanco
        c.append(Cubo((base - 0.25, 12.8, -2.25), (4.5, 11.4, 4.5),
                      MORADO, "tela"))
        c.append(Cubo((base - 0.4, 12.0, -2.4), (4.8, 4.6, 4.8),
                      BLANCO, "metal"))
        c.append(Cubo((base - 0.5, 15.6, -2.5), (5.0, 1.1, 5.0), ORO, "metal"))
        c.append(Cubo((base - 0.5, 20.6, -2.5), (5.0, 3.6, 5.0),
                      BLANCO, "metal"))

        # ---- EL ALA: tramos que salen y bajan, cada uno mordiendo al anterior
        plumas = [(0.1, 19.4, 3.6, 5.6, 5.4),
                  (2.4, 18.0, 3.2, 4.8, 4.6),
                  (4.4, 16.8, 2.8, 4.0, 3.8),
                  (6.2, 15.8, 2.2, 3.2, 3.0)]
        for sep, y, w, h, d in plumas:
            x = base + (4.0 + sep - w if fuera > 0 else -sep)
            c.append(Cubo((round(x, 3), y, round(-d / 2, 3)), (w, h, d),
                          BLANCO, "metal"))
            # el filo dorado del borde de abajo, que es lo que la dibuja
            c.append(Cubo((round(x, 3), round(y - 0.7, 3), round(-d / 2, 3)),
                          (w, 0.8, d), ORO, "metal"))
        salida.append(c)
    return salida


# ---------------------------------------------------------------- las piernas

def piernas():
    """Grebas: morado con placa blanca y filo dorado."""
    return [[Cubo((x - 0.25, 2.6, -2.25), (4.5, 9.8, 4.5), MORADO, "tela"),
             Cubo((x - 0.4, 8.6, -2.4), (4.8, 3.4, 4.8), BLANCO, "metal"),
             Cubo((x - 0.45, 8.2, -2.45), (4.9, 0.8, 4.9), ORO, "metal"),
             Cubo((x - 0.42, 4.4, -2.85), (4.84, 1.8, 0.8), ORO, "metal")]
            for x in (-3.9, -0.1)]


def botas():
    """Botas blancas con refuerzo dorado."""
    return [[Cubo((x - 0.45, 0.4, -2.45), (4.9, 3.2, 4.9), BLANCO, "metal"),
             Cubo((x - 0.5, 2.6, -2.5), (5.0, 1.0, 5.0), ORO, "metal"),
             Cubo((x - 0.5, 0.4, -3.1), (5.0, 1.6, 0.8), ORO, "metal"),
             Cubo((x - 0.5, -0.1, -2.5), (5.0, 0.6, 5.2), NEGRO, "cuero")]
            for x in (-3.9, -0.1)]


def traje():
    t = M.Traje("leyenda", "Traje LEYENDA")
    t.poner("armorHead", *casco())
    t.poner("armorBody", *pecho())
    der, izq = brazos()
    t.poner("armorRightArm", *der)
    t.poner("armorLeftArm", *izq)
    pd, pi = piernas()
    t.poner("armorRightLeg", *pd)
    t.poner("armorLeftLeg", *pi)
    bd, bi = botas()
    t.poner("armorRightBoot", *bd)
    t.poner("armorLeftBoot", *bi)
    return t


if __name__ == "__main__":
    t = traje()
    tex, total = {}, 0
    for pieza in M.PIEZAS:
        cubos = [c for l in t.de_pieza(pieza).values() for c in l]
        if cubos:
            total += len(cubos)
            alto = M.empaquetar(cubos, 128)
            tex[pieza] = M.pintar(cubos, 128, sum(ord(x) for x in pieza))
            print("  %-6s %3d cubos . textura %3d px" % (pieza, len(cubos), alto))
    print("  TOTAL: %d cubos" % total)
    visor.lamina(t, "build/trajes/leyenda.png", 9.0, tex)
    print("  -> build/trajes/leyenda.png")
