# -*- coding: utf-8 -*-
"""
DE UNA MALLA A UNA CONSTRUCCION DE BLOQUES.

    python tools/malla_a_construccion.py <ruta.obj> --alto 16

⚠⚠⚠ ESTO NO ES EL CONVERSOR DE ARMADURAS, Y LA DIFERENCIA ES TODA. Alli cada
   caja se dibuja EN CADA JUGADOR Y EN CADA FOTOGRAMA, asi que 700 eran
   demasiadas. Aqui son BLOQUES DEL MUNDO: se colocan una vez, en un sitio, y
   cuarenta mil no son ningun problema.

   Por eso aqui NO se fusiona ni se recorta nada. Cada voxel es un bloque.

⚠⚠ Y UNA SALA HUECA HAY QUE COMPROBARLA ANTES DE CONVERTIR. Si Meshy devuelve un
   bloque macizo con la fachada bonita, esto produce cuarenta mil bloques de
   piedra rellena y no se ve al entrar. La señal es cuantos triangulos miran
   HACIA DENTRO: una sala de verdad tiene muchos, un macizo casi ninguno.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import numpy as np
from PIL import Image

RAIZ = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(RAIZ / "tools"))

from simplificar_malla import leer_obj          # noqa: E402


def convertir(obj, alto_bloques, densidad=60):
    vs, vts, caras = leer_obj(obj)
    png = obj.with_suffix(".png")
    tex = np.asarray(Image.open(png).convert("RGB")) if png.exists() else None
    print("  %d triangulos, textura %s" % (len(caras),
          "%dx%d" % tex.shape[1::-1] if tex is not None else "ninguna"))

    esc = alto_bloques / (vs[:, 1].max() - vs[:, 1].min())
    p = (vs - vs.min(axis=0)) * esc
    A, B, C = p[caras[:, 0]], p[caras[:, 2]], p[caras[:, 4]]
    area = 0.5 * np.linalg.norm(np.cross(B - A, C - A), axis=1)
    # ⚠ Por AREA: un muro grande necesita muchas muestras y un remache una.
    #   Con un numero fijo, los muros salen con agujeros.
    n = np.maximum(2, np.ceil(area * densidad)).astype(np.int32)
    tri = np.repeat(np.arange(len(caras)), n)
    N = len(tri)
    r1 = np.sqrt(np.random.random(N)); r2 = np.random.random(N)
    w0, w1, w2 = (1-r1)[:, None], (r1*(1-r2))[:, None], (r1*r2)[:, None]
    pts = A[tri]*w0 + B[tri]*w1 + C[tri]*w2
    print("  %d muestras" % N)

    col = None
    if tex is not None:
        uv = (vts[caras[:, 1]][tri]*w0 + vts[caras[:, 3]][tri]*w1
              + vts[caras[:, 5]][tri]*w2)
        th, tw = tex.shape[:2]
        col = tex[np.clip(((1-uv[:, 1])*th).astype(int), 0, th-1),
                  np.clip((uv[:, 0]*tw).astype(int), 0, tw-1)]

    cel = np.floor(pts).astype(np.int32)
    forma = cel.max(axis=0) + 1
    plano = cel[:, 0]*forma[1]*forma[2] + cel[:, 1]*forma[2] + cel[:, 2]
    ncel = int(np.prod(forma))
    acc = np.zeros((ncel, 3), np.float64); cnt = np.zeros(ncel, np.int64)
    if col is not None:
        np.add.at(acc, plano, col); np.add.at(cnt, plano, 1)
    else:
        np.add.at(cnt, plano, 1)
    hay = cnt > 0
    color = np.zeros((ncel, 3), np.uint8)
    if col is not None:
        color[hay] = (acc[hay] / cnt[hay, None]).astype(np.uint8)
    return forma, hay.reshape(forma), color.reshape(tuple(forma)+(3,))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("obj")
    ap.add_argument("--alto", type=float, default=16.0,
                    help="altura de la construccion en bloques")
    args = ap.parse_args()
    forma, hay, color = convertir(Path(args.obj), args.alto)
    print()
    print("  MIDE  %d de ancho x %d de alto x %d de fondo" % tuple(forma))
    print("  SON   %d bloques" % int(hay.sum()))
    print("  ocupa el %.1f%% de su caja (una sala hueca ronda el 15-35%%)"
          % (100 * hay.sum() / np.prod(forma)))
    np.save(RAIZ / "build" / "construccion.npy",
            np.concatenate([hay[..., None].astype(np.uint8), color], axis=3))
    print("  -> build/construccion.npy")


if __name__ == "__main__":
    raise SystemExit(main())
