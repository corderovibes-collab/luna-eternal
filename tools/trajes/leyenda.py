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
MORADO_CLARO = (124, 62, 168)
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


def casco():
    """
    El casco y los cuernos.

    ⚠⚠ LA CABEZA DE VANILLA VA DE y=24 A y=32, y el casco tiene que ser MAS
       ANCHO que ella: 8,8 sobre 8,0, o sea inflado 0,4. Con 0,25 se pega a la
       capa exterior de la skin y PARPADEA.

    ⚠⚠⚠ Y LA CARA SE DEJA AL AIRE, que es lo que dice el boceto. No es un
       descuido: es la unica parte del jugador que sigue siendo suya. Por eso el
       casco tapa arriba, atras y los lados, y por delante solo baja hasta la
       frente.
    """
    c = []
    # ---- la calota: arriba, atras y los lados
    c.append(Cubo((-4.4, 31.4, -4.4), (8.8, 2.0, 8.8), ORO, "metal"))
    c.append(Cubo((-4.4, 24.6, 1.6), (8.8, 6.8, 2.8), ORO, "metal"))
    for x in (-4.4, 3.6):
        c.append(Cubo((x, 25.4, -4.4), (0.8, 6.0, 8.8), ORO, "metal"))
    # ---- la diadema de la frente, con su gema
    c.append(Cubo((-4.4, 30.0, -4.5), (8.8, 1.6, 0.9), ORO, "metal"))
    c.append(Cubo((-0.8, 30.2, -4.9), (1.6, 1.2, 0.5), MAGENTA, "neon"))
    # ---- las placas de las mejillas, que enmarcan la cara
    # ⚠ Mas anchas y mas hacia delante: a 0,8 en el borde no se veian de frente
    #   y la cara quedaba como un cuadrado de piel suelto.
    for x in (-4.6, 3.0):
        c.append(Cubo((x, 25.0, -4.7), (1.6, 5.2, 2.6), ORO, "metal"))

    # ---- LOS CUERNOS. Cada uno es una escalera de cubos que se estrecha: es la
    #      unica forma de curvar con cajas alineadas a los ejes.
    #      ⚠ El ultimo cubo NO se hace diminuto: por debajo de medio bloque el
    #        remate se ve como una mota que parpadea, no como una punta.
    tramo = [
        # (separacion del centro, altura, ancho, alto, fondo)
        (3.9, 30.4, 2.8, 2.0, 3.4),
        (5.3, 32.0, 2.5, 2.0, 3.0),
        (6.3, 33.8, 2.1, 2.0, 2.5),
        (6.9, 35.6, 1.7, 1.9, 2.0),
        (7.0, 37.2, 1.3, 1.7, 1.6),
        (6.8, 38.6, 1.0, 1.4, 1.3),
    ]
    for i, (sep, y, w, h, d) in enumerate(tramo):
        color = ORO if i < 4 else (250, 205, 90)   # la punta, mas clara
        for signo in (-1, 1):
            x = sep - w if signo > 0 else -sep
            c.append(Cubo((round(x, 3), y, round(-d / 2, 3)),
                          (w, h, d), color, "metal"))
    return c


def pecho():
    """
    El peto, las hombreras y los brazos.

    ⚠ Todo va inflado 0,4 sobre el cuerpo: el torso mide 8x12x4 y el peto 8,8 de
      ancho. Con 0,25 se pega a la capa exterior de la skin y parpadea.
    """
    c = []
    # ---- la base morada
    c.append(Cubo((-4.4, 12.6, -2.6), (8.8, 10.8, 5.2), MORADO, "tela"))
    # ---- el cuello dorado
    c.append(Cubo((-4.6, 22.6, -2.8), (9.2, 1.8, 5.6), ORO, "metal"))
    # ---- las placas doradas que enmarcan el pecho
    # ⚠ Las placas laterales, ESTRECHAS: con 1,6 de ancho tapaban casi todo y
    #   el morado --que es la mitad del traje-- no se veia.
    for x in (-4.7, 3.7):
        c.append(Cubo((x, 14.4, -3.0), (1.0, 8.6, 6.0), ORO, "metal"))
    c.append(Cubo((-3.2, 21.4, -3.0), (6.4, 1.6, 0.6), ORO, "metal"))
    # ---- la gema del esternon, en su aro
    c.append(Cubo((-1.6, 18.6, -3.1), (3.2, 3.2, 0.5), ORO, "metal"))
    c.append(Cubo((-1.1, 19.1, -3.4), (2.2, 2.2, 0.4), MAGENTA, "neon"))
    # ---- el ovalo morado del vientre, escalonado para que se lea redondo
    for r, y0, y1 in ((1.0, 17.4, 18.2), (1.5, 15.4, 17.4), (1.0, 14.6, 15.4)):
        c.append(Cubo((round(-r, 3), y0, -3.05), (round(r * 2, 3),
                      round(y1 - y0, 3), 0.45), MORADO_CLARO, "tela"))
    # ---- el cinturon
    c.append(Cubo((-4.7, 12.2, -2.9), (9.4, 2.2, 5.8), ORO, "metal"))
    c.append(Cubo((-1.3, 12.4, -3.2), (2.6, 1.8, 0.5), MAGENTA, "neon"))

    c.extend(anillo(0, 18.0, 2.6))
    return c


def brazos():
    """
    Devuelve (derecho, izquierdo). Son ESPEJO, no copia: el derecho vive en x
    negativa.

    ⚠⚠ LA HOMBRERA SUBE HASTA 24,2 porque el brazo de vanilla llega a 24. Si
       acabara antes queda un anillo de piel en lo alto de cada hombro, y el
       cuello NO lo tapa: solo mide 9,2 de ancho y los brazos viven mas afuera.
    """
    salida = []
    for lado in ("Right", "Left"):
        c = []
        base = -8.0 if lado == "Right" else 4.0
        fuera = -1 if lado == "Right" else 1
        # manga morada
        c.append(Cubo((base - 0.25, 12.8, -2.25), (4.5, 11.4, 4.5),
                      MORADO, "tela"))
        # guantelete dorado, del codo a los nudillos
        c.append(Cubo((base - 0.4, 12.0, -2.4), (4.8, 5.4, 4.8), ORO, "metal"))
        c.append(Cubo((base - 0.5, 15.8, -2.5), (5.0, 1.2, 5.0), ORO, "metal"))
        # ---- LA HOMBRERA: tres hojas escalonadas que salen y bajan, como en el
        #      boceto. Es lo que le da la silueta al traje.
        c.append(Cubo((base - 0.5, 20.8, -2.5), (5.0, 3.4, 5.0), ORO, "metal"))
        # ⚠⚠ GRANDES Y BAJANDO. En el boceto la hombrera es un ALA que sale y
        #    cae; con escalones pequeños se lee como una coraza cualquiera y el
        #    traje pierde justo lo que lo hace reconocible de lejos.
        hojas = [(0.4, 20.6, 3.2, 4.2, 5.2),
                 (3.0, 19.2, 3.0, 3.8, 4.4),
                 (5.4, 17.9, 2.6, 3.2, 3.6),
                 (7.3, 16.8, 2.0, 2.6, 2.8)]
        for sep, y, w, h, d in hojas:
            x = base + (4.0 + sep - w if fuera > 0 else -sep)
            c.append(Cubo((round(x, 3), y, round(-d / 2, 3)), (w, h, d),
                          ORO, "metal"))
        salida.append(c)
    return salida


def piernas():
    """Grebas: morado con placa dorada en la rodilla."""
    salida = []
    for x in (-3.9, -0.1):
        # ⚠ Solo la rodillera y una banda: con placa en la espinilla y en el
        #   tobillo la pierna salia dorada entera y rompia el reparto de color.
        c = [Cubo((x - 0.25, 2.6, -2.25), (4.5, 9.8, 4.5), MORADO, "tela"),
             Cubo((x - 0.4, 10.4, -2.4), (4.8, 2.0, 4.8), ORO, "metal"),
             Cubo((x - 0.42, 6.4, -2.85), (4.84, 2.2, 0.8), ORO, "metal")]
        salida.append(c)
    return salida


def botas():
    """Botas doradas, con la suela oscura."""
    salida = []
    for x in (-3.9, -0.1):
        salida.append([
            Cubo((x - 0.45, 0.4, -2.45), (4.9, 3.2, 4.9), ORO, "metal"),
            Cubo((x - 0.5, 0.4, -3.1), (5.0, 1.8, 0.8), ORO_OSC, "metal"),
            Cubo((x - 0.5, -0.1, -2.5), (5.0, 0.6, 5.2), NEGRO, "cuero"),
        ])
    return salida


def traje():
    """El LEYENDA entero."""
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
    visor.lamina(t, "build/trajes/leyenda.png", 10.0, tex)
    print("  -> build/trajes/leyenda.png")
