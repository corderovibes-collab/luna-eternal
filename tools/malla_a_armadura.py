# -*- coding: utf-8 -*-
"""
LA FORMA EN CAJAS, EL DETALLE EN LA TEXTURA.

⚠⚠⚠ ESTUVE ATACANDO EL PROBLEMA EQUIVOCADO. Intentaba meter el detalle en la
   GEOMETRIA -- una caja por cada matiz-- y por eso salian 700 cubos y aun asi
   no se apreciaba nada.

   Minecraft no hace eso. El jugador de vainilla son SEIS CAJAS y parece una
   persona por la TEXTURA. El detalle va pintado, no construido.

   Asi que:
     1. se fusiona IGNORANDO EL COLOR -> pocas cajas, porque la geometria si
        tiene planos largos;
     2. y despues se PINTA cada cara muestreando el modelo de Meshy.

   El color deja de partir cajas, y el detalle deja de costar geometria: subir
   la densidad de texeles es gratis en cubos.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, "tools")
from simplificar_malla import leer_obj                 # noqa: E402
from trajes import visor, modelo as M                   # noqa: E402
from trajes.modelo import Cubo                          # noqa: E402

OBJ = Path("D:/KITRANGO/skinaceur/skin.obj")
TEX = Path("D:/KITRANGO/skinaceur/skin.png")
ALTO = 32          # un jugador
TEXELES = 4        # texeles por bloque. ⚠ ESTO ES GRATIS EN CUBOS


def muestrear(vs, vts, caras, tex, escala, densidad):
    """Nube de puntos de la superficie, con su color, en coordenadas de bloque."""
    A, B, C = vs[caras[:, 0]], vs[caras[:, 2]], vs[caras[:, 4]]
    area = 0.5 * np.linalg.norm(np.cross(B - A, C - A), axis=1)
    n = np.maximum(6, np.ceil(area * densidad)).astype(np.int32)
    tri = np.repeat(np.arange(len(caras)), n)
    N = len(tri)
    r1 = np.sqrt(np.random.random(N)); r2 = np.random.random(N)
    w0, w1, w2 = (1-r1)[:, None], (r1*(1-r2))[:, None], (r1*r2)[:, None]
    pts = A[tri]*w0 + B[tri]*w1 + C[tri]*w2
    uv = vts[caras[:, 1]][tri]*w0 + vts[caras[:, 3]][tri]*w1 + vts[caras[:, 5]][tri]*w2
    th, tw = tex.shape[:2]
    col = tex[np.clip(((1-uv[:, 1])*th).astype(int), 0, th-1),
              np.clip((uv[:, 0]*tw).astype(int), 0, tw-1)]
    return pts, col


def volumen(pts, col, paso, mn, forma):
    """Rejilla de color: el tono mas votado por celda, a la resolucion que sea."""
    cel = np.clip(np.floor((pts - mn) / paso).astype(np.int32), 0,
                  np.array(forma) - 1)
    plano = cel[:, 0]*forma[1]*forma[2] + cel[:, 1]*forma[2] + cel[:, 2]
    n = int(np.prod(forma))
    acc = np.zeros((n, 3), np.float64); cnt = np.zeros(n, np.int64)
    np.add.at(acc, plano, col); np.add.at(cnt, plano, 1)
    hay = cnt > 0
    out = np.zeros((n, 3), np.uint8)
    out[hay] = (acc[hay] / cnt[hay, None]).astype(np.uint8)
    return out.reshape(tuple(forma) + (3,)), hay.reshape(forma)


def fusionar_sin_color(oc):
    """
    Greedy meshing SOLO por ocupacion.

    ⚠⚠ Sin mirar el color, y por eso salen pocas: la geometria de un torso o de
       un ala TIENE planos largos; lo que no los tenia era el color.
    """
    v = oc.copy(); cajas = []
    nx, ny, nz = v.shape
    for x in range(nx):
        for y in range(ny):
            for z in range(nz):
                if not v[x, y, z]:
                    continue
                X = x
                while X+1 < nx and v[X+1, y, z]: X += 1
                Y = y
                while Y+1 < ny and v[x:X+1, Y+1, z].all(): Y += 1
                Z = z
                while Z+1 < nz and v[x:X+1, y:Y+1, Z+1].all(): Z += 1
                v[x:X+1, y:Y+1, z:Z+1] = False
                cajas.append((x, y, z, X-x+1, Y-y+1, Z-z+1))
    return cajas


def main():
    vs, vts, caras = leer_obj(OBJ)
    tex = np.asarray(Image.open(TEX).convert("RGB"))
    esc = ALTO / (vs[:, 1].max() - vs[:, 1].min())
    p = (vs - np.array([vs[:, 0].mean(), vs[:, 1].min(), vs[:, 2].mean()])) * esc
    pts, col = muestrear(p, vts, caras, tex, esc, 400)
    print("  %d muestras de superficie" % len(pts))

    mn = np.array([pts[:, 0].min(), 0.0, pts[:, 2].min()])
    # ---- rejilla GRUESA: la forma
    forma_g = (np.ceil((pts.max(axis=0) - mn) / 1.0).astype(int) + 1)
    _, oc = volumen(pts, col, 1.0, mn, forma_g)
    print("  rejilla de forma %dx%dx%d, %d voxeles" % (*forma_g, oc.sum()))
    cajas = fusionar_sin_color(oc)
    print("  CAJAS (sin mirar color): %d" % len(cajas))

    # ---- rejilla FINA: el color con el que se pinta
    paso_f = 1.0 / TEXELES
    forma_f = (np.ceil((pts.max(axis=0) - mn) / paso_f).astype(int) + 1)
    vol, hay = volumen(pts, col, paso_f, mn, forma_f)
    print("  rejilla de color %dx%dx%d (%d texeles por bloque)"
          % (*forma_f, TEXELES))

    # ⚠ Los huecos de la rejilla fina se rellenan con el vecino: si no, la
    #   textura sale con puntitos negros donde no cayo ninguna muestra.
    for _ in range(6):
        falta = ~hay
        if not falta.any():
            break
        for eje in (0, 1, 2):
            for d in (1, -1):
                mov = np.roll(vol, d, axis=eje)
                movh = np.roll(hay, d, axis=eje)
                pon = falta & movh
                vol[pon] = mov[pon]; hay = hay | pon
                falta = ~hay
    return cajas, vol, hay, mn, forma_f


cajas, vol, hay, mn, forma_f = main()

# ---- construir los Cubo y pintar sus caras muestreando el volumen de color
cx = (min(c[0] for c in cajas) + max(c[0]+c[3] for c in cajas)) / 2
cz = (min(c[2] for c in cajas) + max(c[2]+c[5] for c in cajas)) / 2
cubos = [Cubo((float(x-cx), float(y), float(z-cz)),
              (float(w), float(h), float(d)), (200, 200, 200), "tela")
         for x, y, z, w, h, d in cajas]
# ⚠ `M.ESCALA_UV` se retiro: Minecraft fija un texel por unidad (ver
#   `trajes/modelo.py`). Este script ya estaba marcado como NO SIRVE en
#   docs/ui/trajes-flujo.md, y esta linea era parte de por que.
lado = 1024
alto_usado = M.empaquetar(cubos, lado)
print("  textura %dx%d, usados %d px" % (lado, lado, alto_usado))

img = Image.new("RGB", (lado, lado), (0, 0, 0))
px = img.load()
NORMAL = {"derecha": (-1, 0, 0), "izquierda": (1, 0, 0), "frente": (0, 0, -1),
          "espalda": (0, 0, 1), "arriba": (0, 1, 0), "abajo": (0, -1, 0)}

for cu, (bx, by, bz, bw, bh, bd) in zip(cubos, cajas):
    for ox, oy, aw, ah, cara in M._caras(cu):
        nrm = np.array(NORMAL[cara], float)
        for j in range(ah):
            for i in range(aw):
                # el punto 3D de este texel, en coordenadas de la rejilla fina
                if cara in ("frente", "espalda"):
                    u, v = i / aw, j / ah
                    P = np.array([bx + u*bw, by + bh - v*bh,
                                  bz + (bd if cara == "espalda" else 0)])
                elif cara in ("derecha", "izquierda"):
                    u, v = i / aw, j / ah
                    P = np.array([bx + (bw if cara == "izquierda" else 0),
                                  by + bh - v*bh, bz + u*bd])
                else:
                    u, v = i / aw, j / ah
                    P = np.array([bx + u*bw, by + (bh if cara == "arriba" else 0),
                                  bz + v*bd])
                # ⚠⚠⚠ SE BUSCA A LO LARGO DE LA NORMAL, no a una distancia fija.
                #    Con una sola lectura salian PARCHES NEGROS enormes: la cara
                #    de una caja no cae exactamente sobre la superficie del
                #    modelo --la caja es una aproximacion-- asi que a veces se
                #    lee un hueco. Probando de fuera hacia dentro se coge el
                #    primer sitio con dato, que es la superficie de verdad.
                #    ⚠ Y el orden importa: de FUERA hacia dentro. Al reves se
                #      coge el forro interior en vez de lo que se ve.
                puesto = False
                for paso_n in (0.6, 0.3, 0.0, -0.3, -0.6, -1.0, 1.0):
                    Q = P + nrm * paso_n
                    c = np.clip((Q * TEXELES).astype(int), 0,
                                np.array(forma_f) - 1)
                    if hay[c[0], c[1], c[2]]:
                        px[ox+i, oy+j] = tuple(int(q) for q in vol[c[0], c[1], c[2]])
                        puesto = True
                        break
                if not puesto:
                    px[ox+i, oy+j] = (200, 160, 40)   # oro, que es lo dominante
img.save("build/trajes/leyenda2_tex.png")

t = M.Traje("pintado", "forma en cajas + detalle pintado (%d)" % len(cajas))
# ⚠ Repartido por ALTURA: lo de la cabeza al casco --el halo incluido, que
#   tiene que girar con ella-- y asi hasta las botas.
piezas = {"armorHead": [], "armorBody": [], "armorRightLeg": [], "armorRightBoot": []}
for cu in cubos:
    c = cu.origen[1] + cu.tam[1] / 2
    piezas["armorRightBoot" if c < 3.2 else "armorRightLeg" if c < 12
           else "armorBody" if c < 24 else "armorHead"].append(cu)
for k, v in piezas.items():
    if v:
        t.poner(k, *v)
        print("    %-16s %d cubos" % (k, len(v)))
visor.SUELO = 34
visor.VISTAS = [("FRENTE", 180, 0), ("3/4", 215, 10), ("LADO", 270, 0), ("ESPALDA", 0, 0)]
visor.lamina(t, "build/trajes/leyenda2.png", 11.0, {"body": img.convert("RGBA")})
print("  -> build/trajes/leyenda2.png")
from trajes import blockbench
for r in blockbench.escribir_todo(t, "build/trajes/leyenda2_bb", lado):
    print("    -> %s" % r.name)
