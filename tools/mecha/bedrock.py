# -*- coding: utf-8 -*-
"""
LA GEOMETRIA DE BEDROCK TAL Y COMO LA DIBUJA COBBLEMON, no como la enseña Blockbench.

Son dos espacios distintos y hay que saber en cual se esta en cada linea:

  BLOCKBENCH   lo que ve el usuario. `from`/`to` de cada cubo, `origin` de cada
               grupo, rotacion [rx, ry, rz].
  GEO          lo que va en el .geo.json y lo que lee Cobblemon. Blockbench lo
               exporta ESPEJADO EN X y con las rotaciones X e Y NEGADAS:
                   origin = (-to.x, from.y, from.z)   size = to - from
                   pivot  = (-origin.x, ...)          rot = (-rx, -ry, rz)
               Medido contra el jar: los 123 cubos y 130 huesos del cuerpo
               cuadran con esa regla y con ninguna otra.

⚠⚠⚠ EL REPARTO DEL BOX-UV NO ES EL MISMO EN LOS DOS SITIOS, Y ESTE ES EL
   HALLAZGO QUE MANDA EN TODO EL FLUJO:

     Blockbench 5   lay-out con los tamaños REDONDEADOS HACIA ARRIBA
                    (2,2 -> 3, 7,6 -> 8). Se ve en los `faces[].uv` del fichero.
     Cobblemon      `Cube.uv` es `List<Integer>` (solo box-uv) y pasa los
                    tamaños FLOTANTES a `ModelPart$Cuboid`, que reparte con
                    `u + sizeZ`, `u + sizeZ + sizeX`... sin redondear. Leido en
                    el bytecode de 1.8.0 y en el de Minecraft 1.21.1.

   Con tamaños enteros las dos reglas dan lo mismo, y POR ESO NADIE LO NOTA:
   los 89 geos cosmeticos del pack suman ~10.000 cubos y solo 16 son
   fraccionarios. El casco del usuario tiene 133 cubos y LOS 133 SON
   FRACCIONARIOS (0,6 · 2,2 · 4,7 · 0,15...). En el juego, cada cara
   muestrearia una zona corrida hasta 1,8 texels respecto a lo que pinto.
   La salida es repintar la textura al reparto del juego (texturas.py) y
   entregar un .bbmodel con UV por cara para que Blockbench enseñe lo mismo.

⚠⚠ LAS ROTACIONES, EN EL MARCO DEL GEO (y hacia arriba, x sin espejar):
   Cobblemon pasa los angulos tal cual a un `ModelPart`, que vive con la Y
   HACIA ABAJO. Reflejar Y invierte el sentido de los giros en X y en Z, asi
   que en el marco del geo la rotacion efectiva es (-rx, +ry, -rz), y el orden
   el de `ModelPart.rotate`: Z, luego Y, luego X. Comprobado con la mandibula:
   con +85,8 (el valor del ataque `special`) LA BOCA SE ABRE.
"""

from __future__ import annotations

import math

import numpy as np

CARAS = ("-x", "-z", "+x", "+z", "+y", "-y")


# ------------------------------------------------------- blockbench <-> geo

def a_geo(desde, hasta):
    """(origin, size) del geo a partir de from/to de Blockbench."""
    return ((-hasta[0], desde[1], desde[2]),
            tuple(hasta[i] - desde[i] for i in range(3)))


def a_blockbench(origin, size):
    """from/to de Blockbench a partir de origin/size del geo."""
    desde = (-(origin[0] + size[0]), origin[1], origin[2])
    return desde, (desde[0] + size[0], desde[1] + size[1], desde[2] + size[2])


def pivote_a_geo(origen):
    return (-origen[0], origen[1], origen[2])


def rot_a_geo(rot):
    return (-rot[0], -rot[1], rot[2])


# ------------------------------------------------------------- el box-uv

def reparto(u, v, tam, redondeado=False):
    """
    cara -> (u0, v0, u1, v1) en texels, con el reparto en cruz de `ModelPart$Cuboid`.

    Las caras van en el orden del jar: -x, -z, +x, +z (fila de abajo) y +y, -y
    (tapa y suelo, en la fila de arriba). `redondeado=True` es lo que hace
    Blockbench 5 (ceil de cada tamaño); sin el, lo que hace el juego.

    ⚠ Los nombres son del marco del GEO. En Blockbench, con la x espejada, la
      primera casilla cae en +x -- pero eso solo cambia el nombre, no que
      casilla es de que cara.
    """
    w, h, d = tam
    if redondeado:
        w, h, d = (math.ceil(x - 1e-9) for x in tam)
    return {
        "-x": (u, v + d, u + d, v + d + h),
        "-z": (u + d, v + d, u + d + w, v + d + h),
        "+x": (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "+z": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
        "+y": (u + d, v, u + d + w, v + d),
        "-y": (u + d + w, v, u + d + 2 * w, v + d),
    }


def fraccionario(tam):
    return any(abs(x - round(x)) > 1e-6 for x in tam)


# ------------------------------------------------------------ las matrices

def _rx(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]], dtype=float)


def _ry(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]], dtype=float)


def _rz(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]], dtype=float)


def giro_geo(rot):
    """La matriz 3x3 de una rotacion [rx, ry, rz] (grados, convenio del geo) en el marco del geo."""
    rx, ry, rz = (math.radians(v) for v in rot)
    return _rz(-rz) @ _ry(ry) @ _rx(-rx)


class Afin:
    """Una transformacion afin: p -> m @ p + t."""

    __slots__ = ("m", "t")

    def __init__(self, m=None, t=None):
        self.m = np.eye(3) if m is None else m
        self.t = np.zeros(3) if t is None else t

    def __matmul__(self, otra):
        return Afin(self.m @ otra.m, self.m @ otra.t + self.t)

    def __call__(self, p):
        return self.m @ np.asarray(p, dtype=float) + self.t

    @staticmethod
    def giro_en(pivote, rot):
        """Girar alrededor de un pivote: T(p) · R · T(-p)."""
        p = np.asarray(pivote, dtype=float)
        m = giro_geo(rot)
        return Afin(m, p - m @ p)


def transformaciones(huesos, pose=None):
    """
    nombre -> Afin de cada hueso del geo, con la pose (rotaciones extra por hueso).

    ⚠ Los pivotes del geo son ABSOLUTOS, y aun asi la cadena se compone como
      M_padre · (T(p)·R·T(-p)): girar el hijo sobre su pivote y despues aplicar
      lo del padre es lo mismo que girarlo sobre el pivote YA MOVIDO por el
      padre. Es como lo hace `ModelPart`.
    """
    pose = pose or {}
    por_nombre = {b["name"]: b for b in huesos}
    cache = {}

    def de(nombre):
        if nombre in cache:
            return cache[nombre]
        b = por_nombre[nombre]
        rot = list(b.get("rotation") or (0, 0, 0))
        extra = pose.get(nombre)
        if extra:
            rot = [rot[i] + extra[i] for i in range(3)]
        propia = Afin.giro_en(b.get("pivot") or (0, 0, 0), rot)
        padre = b.get("parent")
        m = (de(padre) @ propia) if padre else propia
        cache[nombre] = m
        return m

    return {b["name"]: de(b["name"]) for b in huesos}


def esquinas(origin, size, inflate=0.0):
    """Las ocho esquinas de un cubo (con su inflate)."""
    x0, y0, z0 = (origin[i] - inflate for i in range(3))
    x1, y1, z1 = (origin[i] + size[i] + inflate for i in range(3))
    return [np.array(p, dtype=float) for p in
            ((x0, y0, z0), (x1, y0, z0), (x0, y1, z0), (x1, y1, z0),
             (x0, y0, z1), (x1, y0, z1), (x0, y1, z1), (x1, y1, z1))]
