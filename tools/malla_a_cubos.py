# -*- coding: utf-8 -*-
"""
DE UNA MALLA (OBJ) A CUBOS DE MINECRAFT.

    python tools/malla_a_cubos.py <ruta.obj> [--bloques 32] [--res 1]

Convierte un modelo 3D --por ejemplo el que sale de Meshy-- en cajas alineadas a
los ejes, que es lo unico que Minecraft sabe dibujar en una armadura.

⚠⚠⚠ NO ES UN CONVERSOR GENERICO, ES UNO PARA ARMADURA. La diferencia esta en
   el paso 3: reparte los cubos por PIEZA (casco, peto, perneras, botas) segun
   donde caen sobre el cuerpo del jugador. Un conversor normal da un monton de
   cubos sueltos, que no se puede llevar puesto.

Los tres pasos y por que cada uno:

  1. VOXELIZAR   se marca cada celda por la que pasa la superficie. Solo la
                 CASCARA: el interior no se ve y multiplicaria los cubos.
  2. FUSIONAR    greedy meshing. Es lo que baja la cuenta de miles a cientos,
                 juntando celdas contiguas del mismo color en una sola caja.
  3. REPARTIR    cada caja a su pieza, por la altura a la que cae.

⚠⚠ Y EL COLOR SE LEE DE LA TEXTURA, no se inventa: cada punto de la superficie
   trae su UV, se mira en el PNG y se acumula por celda. Sin esto habria que
   pintar el traje entero a mano otra vez.
"""

from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path

import numpy as np
from PIL import Image

RAIZ = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(RAIZ / "tools"))


def leer_obj(ruta):
    """
    Lee vertices, UVs y caras.

    ⚠ A mano y no con una libreria: el fichero pesa 200 MB y lo unico que hace
      falta son tres listas. Cargarlo con un cargador general se come la memoria
      construyendo objetos que no vamos a usar.
    """
    vs, vts, caras = [], [], []
    t0 = time.time()
    with open(ruta, "r", encoding="utf-8", errors="ignore") as fh:
        for linea in fh:
            if linea.startswith("v "):
                p = linea.split()
                vs.append((float(p[1]), float(p[2]), float(p[3])))
            elif linea.startswith("vt "):
                p = linea.split()
                vts.append((float(p[1]), float(p[2])))
            elif linea.startswith("f "):
                p = linea.split()[1:]
                if len(p) < 3:
                    continue
                idx = []
                for tok in p[:3]:
                    trozos = tok.split("/")
                    vi = int(trozos[0]) - 1
                    ti = int(trozos[1]) - 1 if len(trozos) > 1 and trozos[1] else -1
                    idx.append((vi, ti))
                caras.append((idx[0][0], idx[1][0], idx[2][0],
                              idx[0][1], idx[1][1], idx[2][1]))
    print("  leido en %.1f s: %d vertices, %d caras"
          % (time.time() - t0, len(vs), len(caras)))
    return (np.array(vs, dtype=np.float32),
            np.array(vts, dtype=np.float32) if vts else None,
            np.array(caras, dtype=np.int64))


def voxelizar(vs, vts, caras, textura, alto_bloques, res):
    """
    Marca las celdas por las que pasa la superficie, con su color.

    ⚠⚠ SE MUESTREA CADA TRIANGULO POR SU AREA, no un numero fijo de puntos por
       triangulo. Con un numero fijo, los triangulos grandes quedan con agujeros
       --se ven celdas vacias en medio de una plancha-- y los pequeños se
       muestrean mil veces para nada.
    """
    # El modelo se escala para que su ALTO sea el que pedimos, y se apoya en 0.
    escala = (alto_bloques * res) / (vs[:, 1].max() - vs[:, 1].min())
    p = (vs - np.array([vs[:, 0].mean(), vs[:, 1].min(), vs[:, 2].mean()],
                       dtype=np.float32)) * escala

    a, b, c = p[caras[:, 0]], p[caras[:, 1]], p[caras[:, 2]]
    area = 0.5 * np.linalg.norm(np.cross(b - a, c - a), axis=1)
    # ⚠ Dos muestras por unidad de area, y minimo 1: menos deja agujeros.
    n = np.maximum(1, np.ceil(area * 2.0)).astype(np.int32)
    total = int(n.sum())
    print("  %d triangulos -> %d muestras" % (len(caras), total))

    tri = np.repeat(np.arange(len(caras)), n)
    r1 = np.sqrt(np.random.random(total).astype(np.float32))
    r2 = np.random.random(total).astype(np.float32)
    w0 = (1 - r1)[:, None]
    w1 = (r1 * (1 - r2))[:, None]
    w2 = (r1 * r2)[:, None]
    pts = a[tri] * w0 + b[tri] * w1 + c[tri] * w2

    minp = pts.min(axis=0)
    celdas = np.floor(pts - np.array([minp[0], 0, minp[2]])).astype(np.int32)
    celdas = np.clip(celdas, 0, None)
    forma = celdas.max(axis=0) + 1
    print("  rejilla %d x %d x %d" % tuple(forma))

    # ---- el color de cada celda, leido de la textura
    color = None
    if vts is not None and textura is not None:
        uv = (vts[caras[:, 3]][tri] * w0 + vts[caras[:, 4]][tri] * w1
              + vts[caras[:, 5]][tri] * w2)
        th, tw = textura.shape[:2]
        px = np.clip((uv[:, 0] * tw).astype(np.int32), 0, tw - 1)
        py = np.clip(((1 - uv[:, 1]) * th).astype(np.int32), 0, th - 1)
        color = textura[py, px]

    plano = (celdas[:, 0] * forma[1] * forma[2]
             + celdas[:, 1] * forma[2] + celdas[:, 2])
    n_celdas = int(forma[0]) * int(forma[1]) * int(forma[2])
    ocupado = np.zeros(n_celdas, dtype=bool)
    ocupado[plano] = True
    acum = np.zeros((n_celdas, 3), dtype=np.float64)
    cuenta = np.zeros(n_celdas, dtype=np.int64)
    if color is not None:
        np.add.at(acum, plano, color)
        np.add.at(cuenta, plano, 1)
    medio = np.zeros((n_celdas, 3), dtype=np.uint8)
    hay = cuenta > 0
    medio[hay] = (acum[hay] / cuenta[hay, None]).astype(np.uint8)
    return ocupado.reshape(forma), medio.reshape(tuple(forma) + (3,))


def apaletar(ocupado, color, n_colores):
    """
    Reduce los colores a una paleta corta, por k-medias.

    ⚠⚠⚠ ESTE PASO ES LO QUE HACE VIABLE TODO. Sin el, cada celda saca un tono
       ligeramente distinto de una textura de 4096 px, asi que dos celdas
       vecinas casi nunca son «del mismo color» y NO SE FUSIONA NADA: medido,
       2.711 voxeles daban 1.298 cajas. Con la paleta bajan a una fraccion.

    ⚠ Y ademas es lo CORRECTO artisticamente: la referencia tiene seis colores.
      Los dos mil tonos del modelo son sombreado horneado en la textura, que en
      Minecraft lo pone la luz del juego -- dejarlo dentro es pintar la sombra
      dos veces.
    """
    idx = np.argwhere(ocupado)
    datos = color[idx[:, 0], idx[:, 1], idx[:, 2]].astype(np.float32)
    # k-medias sencillo: sobra para unos miles de puntos y ocho grupos
    rng = np.random.default_rng(7)
    centros = datos[rng.choice(len(datos), n_colores, replace=False)].copy()
    for _ in range(30):
        d = ((datos[:, None, :] - centros[None, :, :]) ** 2).sum(axis=2)
        cual = d.argmin(axis=1)
        for k in range(n_colores):
            if (cual == k).any():
                centros[k] = datos[cual == k].mean(axis=0)
    centros = centros.astype(np.uint8)
    salida = color.copy()
    salida[idx[:, 0], idx[:, 1], idx[:, 2]] = centros[cual]
    print("  paleta de %d colores: %s" % (n_colores,
          " ".join("#%02X%02X%02X" % tuple(c) for c in centros)))
    return salida


def fusionar(ocupado, color, tolerancia=14):
    """
    Greedy meshing: junta celdas contiguas DEL MISMO COLOR en una sola caja.

    ⚠⚠ DEL MISMO COLOR, y esa es la parte que importa. Fusionando solo por
       ocupacion salen menos cajas pero cada una tendria que promediar colores
       distintos, y el traje sale lavado. La tolerancia deja juntar tonos casi
       iguales, que es lo que da las planchas grandes.
    """
    v = ocupado.copy()
    col = color.astype(np.int16)
    cajas = []
    nx, ny, nz = v.shape
    for x in range(nx):
        for y in range(ny):
            for z in range(nz):
                if not v[x, y, z]:
                    continue
                base = col[x, y, z]

                def igual(bloque):
                    return np.all(np.abs(bloque - base) <= tolerancia)

                X = x
                while (X + 1 < nx and v[X + 1, y, z]
                       and igual(col[X + 1, y, z])):
                    X += 1
                Y = y
                while (Y + 1 < ny and v[x:X + 1, Y + 1, z].all()
                       and igual(col[x:X + 1, Y + 1, z])):
                    Y += 1
                Z = z
                while (Z + 1 < nz and v[x:X + 1, y:Y + 1, Z + 1].all()
                       and igual(col[x:X + 1, y:Y + 1, Z + 1])):
                    Z += 1
                v[x:X + 1, y:Y + 1, z:Z + 1] = False
                cajas.append((x, y, z, X - x + 1, Y - y + 1, Z - z + 1,
                              tuple(int(q) for q in base)))
    return cajas


def main():
    ap = argparse.ArgumentParser(description="De una malla a cubos")
    ap.add_argument("obj")
    ap.add_argument("--bloques", type=float, default=32.0,
                    help="alto del modelo en bloques (un jugador son 32)")
    ap.add_argument("--res", type=int, default=1,
                    help="voxeles por bloque")
    ap.add_argument("--tolerancia", type=int, default=0)
    ap.add_argument("--paleta", type=int, default=8,
                    help="cuantos colores. 0 = no reducir")
    args = ap.parse_args()

    ruta = Path(args.obj)
    vs, vts, caras = leer_obj(ruta)
    png = ruta.with_suffix(".png")
    tex = None
    if png.exists():
        tex = np.asarray(Image.open(png).convert("RGB"))
        print("  textura %dx%d" % (tex.shape[1], tex.shape[0]))

    ocupado, color = voxelizar(vs, vts, caras, tex, args.bloques, args.res)
    print("  voxeles ocupados: %d" % int(ocupado.sum()))
    if args.paleta:
        color = apaletar(ocupado, color, args.paleta)
    t0 = time.time()
    cajas = fusionar(ocupado, color, args.tolerancia)
    print("  CAJAS TRAS FUSIONAR: %d   (%.1f s)" % (len(cajas), time.time() - t0))

    np.save(RAIZ / "build" / "malla_cajas.npy",
            np.array([(x, y, z, w, h, d) + c for x, y, z, w, h, d, c in cajas],
                     dtype=np.int32))
    print("  -> build/malla_cajas.npy")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
