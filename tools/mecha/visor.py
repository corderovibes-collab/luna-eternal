# -*- coding: utf-8 -*-
"""
EL VISOR: el Charizard entero, POSADO, con el casco puesto.

⚠⚠⚠ ES LA PIEZA QUE IMPORTA, y aqui mas que en los trajes: lo que el usuario
   pidio comprobar es DINAMICO -- «que cuando haga una animacion no se salga...
   si abre la boca eso tambien». Un visor en reposo no puede contestar a eso.
   Este compone la cadena de huesos entera (bedrock.transformaciones) y admite
   una POSE: un angulo extra por hueso, con los valores de las animaciones del
   jar. Asi se ve la mandibula a 85,8 grados --el maximo de las 34
   animaciones-- ANTES de compilar, desplegar y entrar.

⚠⚠ SE DIBUJA COMO LO DIBUJA EL JUEGO, no como lo enseña Blockbench: en el marco
   del geo y espejado al final en X, que es lo que hace `LivingEntityRenderer`
   con su `scale(-1, -1, 1)`. Es lo mismo que ve Blockbench, que ya espeja al
   importar. Las rotaciones siguen el convenio de bedrock.py.

⚠⚠ LA TEXTURA SE MUESTREA CON EL REPARTO DEL JUEGO (tamaños flotantes, no
   redondeados). Con la textura del usuario sin repintar, este visor enseña
   la pintura corrida que saldria en el juego; con la repintada, la buena. Es
   la unica forma de juzgar el repintado sin entrar.

Se reutilizan las primitivas del visor de trajes: proyeccion, z-buffer y
rasterizado de un paralelogramo. Ahi esta escrito por que la orientacion de
las caras «es un acuerdo entre dos ficheros»; usando las suyas no se rompe.
"""

from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from trajes.visor import FONDO, LUZ, _matriz, _pintar_quad, _quads  # noqa: E402

from . import bedrock  # noqa: E402

# Los nombres de cara del visor de trajes -> los del reparto del geo.
CARA = {"derecha": "-x", "frente": "-z", "izquierda": "+x",
        "espalda": "+z", "arriba": "+y", "abajo": "-y"}

# ---------------------------------------------------------------- las poses
#
# Los numeros salen de charizard.animation.json (el termino constante de cada
# expresion, o el extremo de sus keyframes), medidos con `--ver`. No son
# inventados: cada pose es un estado por el que el juego pasa de verdad.
IDLE = {"neck": (7.5, 0, 0), "neck2": (22.5, 0, 0), "neck3": (27.5, 0, 0),
        "neck4": (20, 0, 0), "head_correction": (15, 0, 0), "head_angle": (10, 0, 0),
        "torso": (-2.5, 0, 0), "torso2": (5, 0, 0)}
COMBATE = {"neck": (5, 10, 2.5), "neck2": (30, 5, -2.5), "neck3": (35, 0, 0),
           "neck4": (25, 0, 0), "head_correction": (0, 0, -7.5), "head_angle": (10, 0, 0),
           "jaw": (17.5, 0, 0), "torso": (5, -5, 0), "torso2": (0, 2.5, -2.5),
           "body": (0, -20, 0)}
POSES = [
    ("IDLE", IDLE),
    ("COMBATE · boca 17,5", COMBATE),
    ("BOCA ABIERTA 85,8 (special)", {**IDLE, "jaw": (85.82, 0, 0), "jaw2": (7, 0, 0)}),
    ("CABEZA ARRIBA", {**IDLE, "head": (-34.1, 0, 0), "head_angle": (-21.3, 0, 0)}),
    ("CABEZA ABAJO", {**IDLE, "head": (32.8, 0, 0), "head_correction": (15, 0, 0),
                      "head_angle": (10, 0, 0), "neck4": (41.4, 0, 0)}),
    ("MIRANDO DE LADO (q.look)", {**IDLE, "neck": (7.5, 30, 0), "neck2": (22.5, 25, 0),
                                  "neck3": (27.5, 20, 0), "neck4": (20, 15, 0),
                                  "head": (0, 10, 0)}),
]

VISTAS = [("FRENTE", 0, 0), ("3/4", 35, 12), ("LADO", 90, 0), ("ESPALDA", 180, 0)]


# ------------------------------------------------------------- la escena

class Escena:
    """Los cubos del geo con la transformacion de su hueso ya compuesta."""

    def __init__(self, geo: dict, pose: dict = None, solo=None):
        g = geo["minecraft:geometry"][0]
        self.huesos = g["bones"]
        self.tex_w = g["description"]["texture_width"]
        self.tex_h = g["description"]["texture_height"]
        self.afines = bedrock.transformaciones(self.huesos, pose or {})
        self.cubos = []      # (hueso, cubo, Afin total)
        for b in self.huesos:
            if solo and b["name"] not in solo:
                continue
            for c in b.get("cubes", []):
                m = self.afines[b["name"]]
                if c.get("rotation"):
                    m = m @ bedrock.Afin.giro_en(c.get("pivot") or b.get("pivot") or (0, 0, 0),
                                                 c["rotation"])
                self.cubos.append((b["name"], c, m))

    def punto(self, hueso, p):
        """Un punto del marco del geo, movido por la pose de ese hueso."""
        return self.afines[hueso](p)

    def caja(self, hueso, cubo):
        """Las ocho esquinas de un cubo ya posado."""
        _, _, m = next(x for x in self.cubos if x[0] == hueso and x[1] is cubo)
        return [m(p) for p in bedrock.esquinas(cubo["origin"], cubo["size"],
                                                cubo.get("inflate") or 0.0)]


def _muestreo_rect(arr, rect, sombra):
    u0, v0, u1, v1 = rect
    alto, ancho = arr.shape[:2]

    def leer(s, t):
        u = np.clip(np.floor(u0 + s * (u1 - u0)).astype(int), 0, ancho - 1)
        v = np.clip(np.floor(v0 + t * (v1 - v0)).astype(int), 0, alto - 1)
        c = arr[v, u].copy()
        c[..., :3] = np.clip(c[..., :3] + sombra, 0, 255)
        return c
    return leer


def _muestreo_plano(color, sombra):
    def leer(s, t):
        c = np.zeros(s.shape + (4,), dtype=np.int16)
        c[..., :3] = [max(0, min(255, v + sombra)) for v in color]
        c[..., 3] = 255
        return c
    return leer


def _quads_de(cubo):
    """Las seis caras (p0, p1, p3, cara) de un cubo del geo, con su espejo si lo lleva."""
    origin, size = cubo["origin"], cubo["size"]
    inflate = cubo.get("inflate") or 0.0
    if cubo.get("mirror"):
        # Un cubo espejado es el cubo reflejado en X con sus casillas de
        # siempre; se reflejan sus caras de vuelta y las casillas se quedan
        # donde el reflejo las dejo. Es lo que hace `Cuboid` intercambiando
        # x1 y x2 antes de construir los vertices.
        reflejo = (-(origin[0] + size[0]), origin[1], origin[2])
        caras = _quads(reflejo, size, inflate)
        r = np.array([-1.0, 1.0, 1.0])
        return [(np.asarray(a) * r, np.asarray(b) * r, np.asarray(c) * r, n)
                for a, b, c, n in caras]
    return _quads(origin, size, inflate)


def dibujar(escena: Escena, giro, inclina, ancho, alto, escala, textura=None,
            centro=(0.0, 20.0, 0.0), resaltar=None, plano=None):
    """
    Una vista. `centro` es el punto del geo que cae en el centro de la imagen.

    `resaltar` es un conjunto de huesos que se pintan de color plano (para
    ver de un vistazo que va en cada hueso); `plano` pinta TODO plano.
    """
    color_buf = np.zeros((alto, ancho, 4), dtype=np.int16)
    color_buf[:] = FONDO
    z_buf = np.full((alto, ancho), 1e9)
    m = _matriz(giro, inclina)
    arr = np.asarray(textura).astype(np.int16) if textura is not None else None
    # ⚠ El espejo en X va en la CAMARA (x -> -x antes de proyectar): asi la
    #   lamina enseña la mano que enseña el juego. Ver el docstring.
    m = m @ np.diag([-1.0, 1.0, 1.0])
    c0 = m @ np.asarray(centro, dtype=float)
    cx, cy = ancho / 2.0 - c0[0] * escala, alto / 2.0 + c0[1] * escala

    for hueso, cubo, afin in escena.cubos:
        rects = bedrock.reparto(cubo["uv"][0], cubo["uv"][1], cubo["size"]) \
            if isinstance(cubo.get("uv"), list) else None
        color = None
        if plano is not None:
            color = plano.get(hueso, (150, 150, 150))
        elif resaltar and hueso in resaltar:
            color = resaltar[hueso]
        for p0, p1, p3, nombre in _quads_de(cubo):
            cara = CARA[nombre]
            luz = LUZ[nombre]
            if color is not None:
                mu = _muestreo_plano(color, luz)
            elif arr is not None and rects is not None:
                r = rects[cara]
                if r[2] - r[0] <= 1e-9 or r[3] - r[1] <= 1e-9:
                    continue
                mu = _muestreo_rect(arr, r, luz // 2)
            else:
                mu = _muestreo_plano((140, 140, 150), luz)
            _pintar_quad(color_buf, z_buf, m, afin(p0), afin(p1), afin(p3),
                         escala, cx, cy, mu)
    return Image.fromarray(color_buf.astype(np.uint8), "RGBA")


# ---------------------------------------------------------------- laminas

def _rotulo(d, x, y, texto, color=(210, 220, 240, 255)):
    d.text((x, y), texto, fill=color)


def lamina_cuerpo(geo, destino, textura, pose=None, titulo=""):
    """Las cuatro vistas del Charizard entero, en la pose que se le pida."""
    escena = Escena(geo, pose)
    ancho, alto, escala = 300, 420, 5.0
    hoja = Image.new("RGBA", (ancho * len(VISTAS), alto + 26), FONDO)
    d = ImageDraw.Draw(hoja)
    for i, (nombre, giro, inclina) in enumerate(VISTAS):
        hoja.paste(dibujar(escena, giro, inclina, ancho, alto, escala, textura,
                           centro=(0, 26, 0)), (i * ancho, 26))
        _rotulo(d, i * ancho + 120, 8, nombre)
    _rotulo(d, 10, 8, titulo, (255, 214, 92, 255))
    hoja.save(destino)
    return destino


def _centro_cabeza(escena):
    """Donde ha ido a parar la cabeza con la pose: el pivote de `head_angle`."""
    return escena.punto("head_angle", (0, 47.5, -0.5))


def lamina_cabeza(geo, destino, textura, pose=None, titulo="", resaltar=None):
    """La cabeza de cerca, cuatro vistas. Es donde se juzga el casco."""
    escena = Escena(geo, pose)
    ancho, alto, escala = 300, 300, 12.0
    centro = _centro_cabeza(escena)
    hoja = Image.new("RGBA", (ancho * len(VISTAS), alto + 26), FONDO)
    d = ImageDraw.Draw(hoja)
    for i, (nombre, giro, inclina) in enumerate(VISTAS):
        hoja.paste(dibujar(escena, giro, inclina, ancho, alto, escala, textura,
                           centro=centro, resaltar=resaltar), (i * ancho, 26))
        _rotulo(d, i * ancho + 120, 8, nombre)
    _rotulo(d, 10, 8, titulo, (255, 214, 92, 255))
    hoja.save(destino)
    return destino


def lamina_poses(geo, destino, textura, titulo="", vistas=(("LADO", 90, 0), ("3/4", 35, 12))):
    """
    Cada pose en una fila, las vistas en columnas. La que decide es LADO con la
    boca abierta: ahi se ve si el menton sigue a la mandibula y si choca con
    la placa de la garganta.
    """
    ancho, alto, escala = 300, 300, 11.0
    hoja = Image.new("RGBA", (ancho * len(vistas) + 200, (alto + 6) * len(POSES) + 26), FONDO)
    d = ImageDraw.Draw(hoja)
    for fila, (nombre, pose) in enumerate(POSES):
        escena = Escena(geo, pose)
        centro = _centro_cabeza(escena)
        y = 26 + fila * (alto + 6)
        _rotulo(d, 10, y + alto // 2, nombre, (255, 214, 92, 255))
        for col, (vnombre, giro, inclina) in enumerate(vistas):
            im = dibujar(escena, giro, inclina, ancho, alto, escala, textura, centro=centro)
            hoja.paste(im, (200 + col * ancho, y))
            if fila == 0:
                _rotulo(d, 200 + col * ancho + 130, 8, vnombre)
    _rotulo(d, 10, 8, titulo, (255, 214, 92, 255))
    hoja.save(destino)
    return destino
