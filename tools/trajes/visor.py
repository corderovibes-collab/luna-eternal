# -*- coding: utf-8 -*-
"""
EL VISOR: dibuja el traje puesto sobre un cuerpo de jugador.

⚠⚠⚠ ESTA ES LA PIEZA QUE IMPORTA, Y NO ES UN EXTRA.

Sin ella, un traje se escribe A CIEGAS: se genera un fichero que dice «cubo
aqui, cubo alla» y NO SE VE EL RESULTADO hasta compilar el mod, desplegarlo,
reiniciar el servidor y ponerselo. Eso es media hora por intento, asi que en la
practica se hace UN intento y se acepta lo que salga.

Con el visor son diez intentos en cinco minutos.

Es exactamente la leccion que ya esta pagada en este proyecto con
`gen_maqueta_mercado.py`: en su primera pasada seria encontro CUATRO fallos que
no daban ningun error -- una fila dibujada encima de la paginacion, una columna
que decia ordenar sin ordenar, nombres metiendose en la columna de al lado y una
cabecera que no cabia. Ninguno se habria visto revisando el codigo.

⚠⚠ Y DIBUJA EL CUERPO DEL JUGADOR DEBAJO, en gris. No es adorno: la mitad de
   los fallos de un traje son que EL JUGADOR ASOMA por debajo -- un brazo gris
   saliendo de una hombrera. Sin el maniqui, el traje flotando en negro parece
   perfecto siempre.
"""

from __future__ import annotations

import math

import numpy as np
from PIL import Image

from . import modelo

# El cuerpo de vanilla, para ver por donde asoma el jugador.
MANIQUI = [
    ((-4, 24, -4), (8, 8, 8)),      # cabeza
    ((-4, 12, -2), (8, 12, 4)),     # torso
    ((-8, 12, -2), (4, 12, 4)),     # brazo derecho
    ((4, 12, -2), (4, 12, 4)),      # brazo izquierdo
    ((-3.9, 0, -2), (4, 12, 4)),    # pierna derecha
    ((-0.1, 0, -2), (4, 12, 4)),    # pierna izquierda
]

FONDO = (26, 30, 42, 255)
PIEL = (176, 138, 110)

# Las cuatro vistas. La de 3/4 es la que de verdad dice si un traje funciona:
# de frente todo parece plano y de lado no se ve la silueta.
VISTAS = [("FRENTE", 0, 0), ("3/4", 35, 12), ("LADO", 90, 0), ("ESPALDA", 180, 0)]


def _matriz(giro, inclina):
    a, b = math.radians(giro), math.radians(inclina)
    ry = np.array([[math.cos(a), 0, math.sin(a)],
                   [0, 1, 0],
                   [-math.sin(a), 0, math.cos(a)]])
    rx = np.array([[1, 0, 0],
                   [0, math.cos(b), -math.sin(b)],
                   [0, math.sin(b), math.cos(b)]])
    return rx @ ry


def _quads(origen, tam, inflate=0.0):
    """
    Las seis caras de un cubo: (p0, p1, p3, nombre).

    p0 es la esquina (s=0,t=0) de la textura, p1 la de (1,0) y p3 la de (0,1),
    de forma que un punto es p0 + s*(p1-p0) + t*(p3-p0).

    ⚠ La orientacion de cada cara TIENE que ser la misma que usa `_caras()` al
      pintar, o la textura sale girada. Es un acuerdo entre dos ficheros, o sea
      justo la clase de cosa que se rompe en silencio.
    """
    x0, y0, z0 = (o - inflate for o in origen)
    x1, y1, z1 = (origen[i] + tam[i] + inflate for i in range(3))
    return [
        # -X: mirando desde la izquierda de la imagen; u crece hacia -Z
        ((x0, y1, z1), (x0, y1, z0), (x0, y0, z1), "derecha"),
        # -Z: la cara; u crece hacia +X
        ((x0, y1, z0), (x1, y1, z0), (x0, y0, z0), "frente"),
        # +X: u crece hacia +Z
        ((x1, y1, z0), (x1, y1, z1), (x1, y0, z0), "izquierda"),
        # +Z: u crece hacia -X
        ((x1, y1, z1), (x0, y1, z1), (x1, y0, z1), "espalda"),
        # +Y: u hacia +X, v hacia +Z
        ((x0, y1, z0), (x1, y1, z0), (x0, y1, z1), "arriba"),
        # -Y: u hacia +X, v hacia -Z
        ((x0, y0, z1), (x1, y0, z1), (x0, y0, z0), "abajo"),
    ]


def _pintar_quad(color_buf, z_buf, m, p0, p1, p3, escala, cx, cy, muestreo):
    """
    Rasteriza una cara con z-buffer.

    Proyeccion ORTOGRAFICA, asi que la cara es un paralelogramo en pantalla y el
    paso de (s,t) a pixel es afin -- se puede invertir y recorrer solo los
    pixeles de su caja, en vez de dibujar texel a texel.
    """
    p0, p1, p3 = (np.array(p, dtype=float) for p in (p0, p1, p3))
    q0, q1, q3 = (m @ p for p in (p0, p1, p3))

    a0, b0, d0 = q0[0] * escala + cx, -q0[1] * escala + cy, q0[2]
    e1 = q1 - q0
    e3 = q3 - q0
    a1, b1, d1 = e1[0] * escala, -e1[1] * escala, e1[2]
    a2, b2, d2 = e3[0] * escala, -e3[1] * escala, e3[2]

    det = a1 * b2 - a2 * b1
    if abs(det) < 1e-9:
        return   # la cara se ve de canto: no ocupa ni un pixel

    alto, ancho = z_buf.shape
    xs = [a0, a0 + a1, a0 + a2, a0 + a1 + a2]
    ys = [b0, b0 + b1, b0 + b2, b0 + b1 + b2]
    x_min = max(0, int(math.floor(min(xs))))
    x_max = min(ancho - 1, int(math.ceil(max(xs))))
    y_min = max(0, int(math.floor(min(ys))))
    y_max = min(alto - 1, int(math.ceil(max(ys))))
    if x_min > x_max or y_min > y_max:
        return

    px = np.arange(x_min, x_max + 1) + 0.5
    py = np.arange(y_min, y_max + 1) + 0.5
    gx, gy = np.meshgrid(px, py)
    dx, dy = gx - a0, gy - b0
    s = (dx * b2 - dy * a2) / det
    t = (dy * a1 - dx * b1) / det

    dentro = (s >= 0) & (s <= 1) & (t >= 0) & (t <= 1)
    if not dentro.any():
        return
    prof = d0 + s * d1 + t * d2

    # Coordenada de textura -> color. `muestreo` devuelve RGBA o None.
    color = muestreo(s, t)
    if color is None:
        return
    visible = dentro & (color[..., 3] > 0)
    trozo_z = z_buf[y_min:y_max + 1, x_min:x_max + 1]
    gana = visible & (prof < trozo_z)
    if not gana.any():
        return
    trozo_z[gana] = prof[gana]
    color_buf[y_min:y_max + 1, x_min:x_max + 1][gana] = color[gana]


def _muestreo_textura(tex, ox, oy, aw, ah, sombra):
    arr = np.asarray(tex).astype(np.int16)

    def leer(s, t):
        u = np.clip((s * aw).astype(int), 0, aw - 1) + ox
        v = np.clip((t * ah).astype(int), 0, ah - 1) + oy
        c = arr[v, u].copy()
        c[..., :3] = np.clip(c[..., :3] + sombra, 0, 255)
        return c
    return leer


def _muestreo_plano(color, sombra):
    def leer(s, t):
        c = np.zeros(s.shape + (4,), dtype=np.int16)
        c[..., 0] = max(0, min(255, color[0] + sombra))
        c[..., 1] = max(0, min(255, color[1] + sombra))
        c[..., 2] = max(0, min(255, color[2] + sombra))
        c[..., 3] = 255
        return c
    return leer


# La luz: cuanto se aclara cada cara segun a donde mire. Es lo que le da
# volumen a la figura -- sin esto el traje es una mancha de un solo tono.
LUZ = {"arriba": 30, "abajo": -40, "frente": 10, "espalda": -18,
       "derecha": -14, "izquierda": 4}


# ⚠ El suelo va a `alto - SUELO` y no al centro: una figura de pie se encuadra
#   desde los pies, no desde su mitad. Con el centro, cambiar la altura de un
#   sombrero descolocaba TODO el dibujo.
SUELO = 34


def dibujar(traje, giro, inclina, ancho=280, alto=430, escala=10.5,
            texturas=None, con_maniqui=True):
    color_buf = np.zeros((alto, ancho, 4), dtype=np.int16)
    color_buf[:] = FONDO
    z_buf = np.full((alto, ancho), 1e9)
    m = _matriz(giro, inclina)
    cx, cy = ancho / 2.0, alto - SUELO

    if con_maniqui:
        for origen, tam in MANIQUI:
            for p0, p1, p3, cara in _quads(origen, tam):
                _pintar_quad(color_buf, z_buf, m, p0, p1, p3, escala, cx, cy,
                             _muestreo_plano(PIEL, LUZ[cara] - 10))

    for pieza, huesos in (("head", None), ("body", None),
                          ("legs", None), ("boots", None)):
        cubos = [c for lista in traje.de_pieza(pieza).values() for c in lista]
        if not cubos:
            continue
        tex = texturas.get(pieza) if texturas else None
        for c in cubos:
            caras = {n: (ox, oy, aw, ah)
                     for ox, oy, aw, ah, n in modelo._caras(c)}
            for p0, p1, p3, cara in _quads(c.origen, c.tam, c.inflate):
                if tex is not None:
                    ox, oy, aw, ah = caras[cara]
                    mu = _muestreo_textura(tex, ox, oy, aw, ah, LUZ[cara] // 2)
                else:
                    mu = _muestreo_plano(c.color, LUZ[cara])
                _pintar_quad(color_buf, z_buf, m, p0, p1, p3, escala, cx, cy, mu)

    return Image.fromarray(color_buf.astype(np.uint8), "RGBA")


def lamina(traje, destino, escala=10.5, texturas=None):
    """
    Las cuatro vistas en una sola imagen, con sus rotulos.

    ⚠ Van JUNTAS a proposito: comparar dos ficheros abriendolos por turnos no
      es comparar. Un hombro que sobresale solo se nota viendo el frente y el
      lado a la vez.
    """
    from PIL import ImageDraw

    ancho, alto = 280, 430
    hoja = Image.new("RGBA", (ancho * len(VISTAS), alto + 26), FONDO)
    d = ImageDraw.Draw(hoja)
    for i, (nombre, giro, inclina) in enumerate(VISTAS):
        vista = dibujar(traje, giro, inclina, ancho, alto, escala, texturas)
        hoja.paste(vista, (i * ancho, 26))
        d.text((i * ancho + 108, 8), nombre, fill=(210, 220, 240, 255))
        if i:
            d.line([(i * ancho, 0), (i * ancho, alto + 26)],
                   fill=(60, 68, 86, 255))
    d.text((10, 8), traje.nombre, fill=(255, 214, 92, 255))
    hoja.save(destino)
    return destino
