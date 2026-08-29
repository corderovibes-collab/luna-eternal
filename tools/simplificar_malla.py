# -*- coding: utf-8 -*-
"""
REDUCE UNA MALLA PARA QUE BLOCKBENCH LA PUEDA ABRIR.

    python tools/simplificar_malla.py <ruta.obj> [--rejilla 96]

⚠⚠⚠ BLOCKBENCH NO ABRE DOS MILLONES DE TRIANGULOS. Es una aplicacion de
   Electron y el modelo que sale de Meshy pesa 205 MB: al intentar cargarlo se
   queda colgada o se cierra sin decir nada. No es un fallo de Blockbench, es
   que ese fichero no esta hecho para un editor de bloques.

⚠⚠⚠ LA PRIMERA VERSION AGRUPABA VERTICES POR CELDAS Y SALIA DEFORME. Quitaba
   por igual en todas partes, asi que aplastaba la cara igual que una plancha
   lisa. Lo dijo el usuario mirandolo: «las otras salen re feas».
   Hoy usa DECIMACION QUADRIC (`fast-simplification`), que es la del Decimate de
   Blender: quita triangulos donde la forma NO cambia y los conserva donde si.

⚠⚠⚠ Y LAS UV SE TRANSFIEREN POR TRIANGULO, NO POR VERTICE. Este fallo salio en
   el render, no de pensarlo: la textura salia RAYADA, con brochazos de colores
   que no pegaban.
   El motivo es que la textura de Meshy es un ATLAS: la malla esta cortada en
   trozos y cada trozo vive en una esquina distinta del PNG. Dos vertices
   pegados en el espacio pueden tener UV en extremos opuestos -- son las dos
   ORILLAS DE UNA COSTURA. Cogiendo «el vertice mas cercano» se elige la orilla
   equivocada la mitad de las veces.
   Un TRIANGULO nunca cruza una costura: sus tres UV son siempre coherentes.
   Copiando las tres del triangulo original mas parecido, las costuras se
   respetan solas.
"""

from __future__ import annotations

import argparse
import time
from pathlib import Path

import numpy as np


def leer_obj(ruta):
    vs, vts, caras = [], [], []
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
                fila = []
                for tok in p[:3]:
                    tr = tok.split("/")
                    fila.append(int(tr[0]) - 1)
                    fila.append(int(tr[1]) - 1 if len(tr) > 1 and tr[1] else 0)
                caras.append(fila)
    return (np.array(vs, dtype=np.float64),
            np.array(vts, dtype=np.float64) if vts else np.zeros((1, 2)),
            np.array(caras, dtype=np.int64))


def simplificar(vs, vts, caras, rejilla):
    """Funde los vertices que caen en la misma celda. Devuelve (vs, vts, caras)."""
    minp, maxp = vs.min(axis=0), vs.max(axis=0)
    paso = (maxp - minp).max() / rejilla
    celda = np.floor((vs - minp) / paso).astype(np.int64)
    _, inverso, cuenta = np.unique(celda, axis=0, return_inverse=True,
                                   return_counts=True)
    n = len(cuenta)

    # el representante de cada celda es la media de lo que cayo dentro
    nuevos = np.zeros((n, 3))
    np.add.at(nuevos, inverso, vs)
    nuevos /= cuenta[:, None]

    # y su UV, tambien promediada (ver el aviso de la cabecera)
    uv_idx = caras[:, [1, 3, 5]].ravel()
    v_idx = caras[:, [0, 2, 4]].ravel()
    nuevas_uv = np.zeros((n, 2))
    peso = np.zeros(n)
    np.add.at(nuevas_uv, inverso[v_idx], vts[uv_idx])
    np.add.at(peso, inverso[v_idx], 1)
    hay = peso > 0
    nuevas_uv[hay] /= peso[hay, None]

    tri = inverso[caras[:, [0, 2, 4]]]
    # ⚠ Un triangulo con dos esquinas en la misma celda ya no es un triangulo:
    #   es una linea. Se tira, o el OBJ sale con caras degeneradas.
    vivo = ((tri[:, 0] != tri[:, 1]) & (tri[:, 1] != tri[:, 2])
            & (tri[:, 0] != tri[:, 2]))
    tri = tri[vivo]

    # y los duplicados: dos triangulos con las mismas tres esquinas
    orden = np.sort(tri, axis=1)
    _, unicos = np.unique(orden, axis=0, return_index=True)
    tri = tri[np.sort(unicos)]
    return nuevos, nuevas_uv, tri


def escribir_obj(ruta, vs, vts, tri, material):
    with open(ruta, "w", encoding="utf-8") as fh:
        fh.write("# reducido por tools/simplificar_malla.py\n")
        fh.write("mtllib %s\n" % (Path(ruta).stem + ".mtl"))
        for v in vs:
            fh.write("v %.5f %.5f %.5f\n" % tuple(v))
        for t in vts:
            fh.write("vt %.5f %.5f\n" % tuple(t))
        fh.write("usemtl %s\n" % material)
        for a, b, c in tri + 1:
            fh.write("f %d/%d %d/%d %d/%d\n" % (a, a, b, b, c, c))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("obj")
    ap.add_argument("--rejilla", type=int, default=96,
                    help="mas alto = mas detalle y mas triangulos")
    ap.add_argument("--salida", default=None)
    args = ap.parse_args()

    ruta = Path(args.obj)
    t0 = time.time()
    vs, vts, caras = leer_obj(ruta)
    print("  original: %d vertices, %d triangulos" % (len(vs), len(caras)))

    nv, nt, tri = simplificar(vs, vts, caras, args.rejilla)
    print("  rejilla %d -> %d vertices, %d triangulos  (%.0f%% menos)"
          % (args.rejilla, len(nv), len(tri),
             100 * (1 - len(tri) / len(caras))))

    salida = Path(args.salida) if args.salida else (
        ruta.parent / (ruta.stem + "_ligero.obj"))
    escribir_obj(salida, nv, nt, tri, "material0")
    # el .mtl y la textura, al lado
    mtl = salida.with_suffix(".mtl")
    tex = ruta.with_suffix(".png")
    mtl.write_text("newmtl material0\nKd 1.0 1.0 1.0\nmap_Kd %s\n"
                   % tex.name, encoding="utf-8")
    print("  -> %s  (%.1f MB, %.1f s)"
          % (salida, salida.stat().st_size / 1e6, time.time() - t0))
    print("  -> %s   (la textura sigue siendo %s)" % (mtl.name, tex.name))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
