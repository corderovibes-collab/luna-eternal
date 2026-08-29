# -*- coding: utf-8 -*-
"""
Decimacion quadric + las UV transferidas POR TRIANGULO.

⚠⚠⚠ POR TRIANGULO Y NO POR VERTICE, Y ESO ES TODO EL ARREGLO. La textura de
   Meshy es un ATLAS: la malla esta cortada en trozos y cada trozo vive en una
   esquina distinta del PNG. Dos vertices que en el espacio estan pegados pueden
   tener UV en extremos opuestos -- son las dos orillas de una costura.

   Asignando la UV del vertice mas cercano, en cada costura se coge la orilla
   equivocada y la textura sale rayada. Se vio en el render, no se dedujo.

   Un TRIANGULO, en cambio, nunca cruza una costura: sus tres UV son siempre
   coherentes. Copiando las tres del triangulo original mas parecido, las
   costuras se respetan solas.
"""
import sys
import time
from pathlib import Path

import numpy as np
import fast_simplification as fs

sys.path.insert(0, "tools")
from simplificar_malla import leer_obj   # noqa: E402

ORIG = Path("D:/KITRANGO/skinarceusmodelo/skinarceusmodelo.obj")

vs, vts, caras = leer_obj(ORIG)
tri = caras[:, [0, 2, 4]].astype(np.int32)
uvtri = caras[:, [1, 3, 5]].astype(np.int64)
print("  leido: %d vertices, %d triangulos" % (len(vs), len(tri)))

# rejilla de triangulos originales: una celda guarda uno de los que caen dentro
cen = (vs[tri[:, 0]] + vs[tri[:, 1]] + vs[tri[:, 2]]) / 3.0
minp = vs.min(axis=0)
lado = (vs.max(axis=0) - minp).max()
N = 220
paso = lado / N
cel = np.clip(((cen - minp) / paso).astype(np.int64), 0, N + 2)
clave = cel[:, 0] * (N + 3) ** 2 + cel[:, 1] * (N + 3) + cel[:, 2]
tabla = np.full((N + 3) ** 3, -1, dtype=np.int64)
tabla[clave] = np.arange(len(tri))

VECINOS = np.array([(dx, dy, dz) for dx in (0, -1, 1) for dy in (0, -1, 1)
                    for dz in (0, -1, 1)])

for objetivo, etiqueta in ((0.985, "ligero"), (0.996, "muyligero")):
    t1 = time.time()
    nv, nt = fs.simplify(vs.astype(np.float32), tri, objetivo)
    nt = np.asarray(nt)
    print("  %.1f%% -> %d vertices, %d triangulos (%.1f s)"
          % (objetivo * 100, len(nv), len(nt), time.time() - t1))

    ncen = (nv[nt[:, 0]] + nv[nt[:, 1]] + nv[nt[:, 2]]) / 3.0
    ncel = np.clip(((ncen - minp) / paso).astype(np.int64), 0, N + 2)
    fuente = np.full(len(nt), -1, dtype=np.int64)
    for d in VECINOS:                       # el propio y sus 26 vecinos
        falta = fuente < 0
        if not falta.any():
            break
        c = np.clip(ncel[falta] + d, 0, N + 2)
        k = c[:, 0] * (N + 3) ** 2 + c[:, 1] * (N + 3) + c[:, 2]
        cand = tabla[k]
        idx = np.where(falta)[0]
        fuente[idx[cand >= 0]] = cand[cand >= 0]
    fuente[fuente < 0] = 0
    print("     triangulos sin fuente: %d" % int((fuente == 0).sum()))

    # ⚠ Las tres UV del triangulo original, emparejadas con las tres esquinas
    #   nuevas por CERCANIA: si no, el triangulo sale con la textura girada.
    o_v = vs[tri[fuente]]                    # (n, 3, 3)
    o_uv = vts[uvtri[fuente]]                # (n, 3, 2)
    n_v = nv[nt]                             # (n, 3, 3)
    d = ((n_v[:, :, None, :] - o_v[:, None, :, :]) ** 2).sum(axis=3)
    empareja = d.argmin(axis=2)              # (n, 3)
    uv_final = np.take_along_axis(o_uv, empareja[:, :, None], axis=1)

    salida = ORIG.parent / ("skinarceusmodelo_%s.obj" % etiqueta)
    with open(salida, "w", encoding="utf-8") as fh:
        fh.write("mtllib %s\n" % (salida.stem + ".mtl"))
        for v in nv:
            fh.write("v %.5f %.5f %.5f\n" % tuple(v))
        plano = uv_final.reshape(-1, 2)
        for t in plano:
            fh.write("vt %.6f %.6f\n" % tuple(t))
        fh.write("usemtl material0\n")
        for i, (a, b, c) in enumerate(nt + 1):
            u = i * 3 + 1
            fh.write("f %d/%d %d/%d %d/%d\n" % (a, u, b, u + 1, c, u + 2))
    salida.with_suffix(".mtl").write_text(
        "newmtl material0\nKd 1.0 1.0 1.0\nmap_Kd skinarceusmodelo.png\n",
        encoding="utf-8")
    print("  -> %s (%.1f MB)" % (salida.name, salida.stat().st_size / 1e6))
