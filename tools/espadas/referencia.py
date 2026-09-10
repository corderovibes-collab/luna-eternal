# -*- coding: utf-8 -*-
"""
LO QUE DE VERDAD DICE EL MODELO DE REFERENCIA.

⚠⚠⚠ ESTE FICHERO EXISTE PORQUE UN COMENTARIO NO COMPRUEBA NADA. El docstring
   de `diseno.py` afirma cosas medidas del `.bbmodel` del usuario --que el
   esqueleto va en unidades enteras, que la cabeza mide 8, que ningun cubo
   gira-- y de esas afirmaciones salen la ESCALA de la espada y el maniqui del
   visor. Mientras vivieran solo en un parrafo, nadie podia saber si seguian
   siendo ciertas: se leen igual de bien siendo falsas.
   Y NO ES HIPOTETICO -- al escribir esto, dos de ellas ERAN FALSAS (ver abajo).

⚠⚠ SE LEE LA COPIA DEL REPO, NO LA DE DESCARGAS. Es la leccion de las seis
   pantallas en magenta y la de los .bbmodel de los trajes: un generador que
   depende de un fichero que no esta en git NO SE PUEDE VOLVER A EJECUTAR.
   ⚠ El original del usuario NO SE TOCA: esto solo lee, y encima lee una copia.

⚠⚠⚠ LAS DOS AFIRMACIONES QUE ERAN FALSAS, y por que importan:

   1. «el disfraz va en una rejilla de U = 0,75*raiz(2) cuantizada a U/4».
      MEDIDO: de 144 coordenadas del disfraz, **18 caen en U/4**; de 72
      tamaños, 28. O sea que NO HAY REJILLA -- ni en U, ni en U/2, ni en U/4.
      Lo que hay son numeros irregulares de una importacion.
      ⚠ Y eso REFUERZA la decision en vez de tumbarla: si la referencia
        tuviera una rejilla propia, habria un argumento para copiarla. Como no
        la tiene, la espada define la suya (0,25) y no hereda un artefacto.

   2. «ningun elemento tiene rotacion».
      MEDIDO: **12 elementos SI rotan** -- pero los doce son `locator`, no
      cubos. De los 59 cubos, cero. La frase era verdad de los cubos y falsa
      tal y como estaba escrita, que es la peor forma de estar equivocado:
      parece comprobada.

Lo que SI resiste la medicion, y es lo que sostiene la escala de la espada:
el esqueleto entero en unidades enteras de Minecraft.
"""

from __future__ import annotations

import json
from pathlib import Path

RUTA = (Path(__file__).resolve().parent.parent.parent
        / "arte" / "espadas" / "referencia" / "pikachu-skin.bbmodel")

# ⚠⚠ EL ESQUELETO, tal cual sale del fichero. Los nombres estan EN ESPAÑOL
#    porque asi los dejo su autor -- buscar «head» o «body» devuelve CERO
#    cubos, y una comprobacion que no encuentra nada pasa sola. Costo un
#    intento: el primer sondeo no imprimio ni una linea y parecia que el
#    esqueleto no estaba.
ESQUELETO = {
    "cabeza":           ((-4.0, 24.0, -4.0), (8.0, 8.0, 8.0)),
    "torso":            ((-4.0, 12.0, -2.0), (8.0, 12.0, 4.0)),
    "brazo derecho":    ((-8.0, 12.0, -2.0), (4.0, 12.0, 4.0)),
    "brazo izquierdo":  ((4.0, 12.0, -2.0), (4.0, 12.0, 4.0)),
    "pierna derecha":   ((-3.9, 0.0, -2.0), (4.0, 12.0, 4.0)),
    "pierna izquierda": ((-0.1, 0.0, -2.0), (4.0, 12.0, 4.0)),
}

# La placa mas delgada del disfraz. Es el unico numero de finura que la
# referencia aporta de verdad, y es lo que justifica una rejilla de 0,25.
DELGADO = 0.264


def cargar():
    return json.loads(RUTA.read_text(encoding="utf-8"))


def comprobar():
    """Devuelve la lista de fallos. Vacia = la referencia dice lo que decimos."""
    fallos = []
    if not RUTA.exists():
        return ["falta la referencia en %s" % RUTA]

    d = cargar()
    cubos = [e for e in d.get("elements", []) if e.get("type") == "cube"]
    por_nombre = {(e.get("name") or "").lower(): e for e in cubos}

    # 1 · EL ESQUELETO. De aqui salen la escala de la espada (26 sobre una
    #     coronilla de 32) y el maniqui del visor. Si esto cambiara, la espada
    #     estaria dimensionada contra un personaje que ya no existe.
    for nombre, (origen, tam) in ESQUELETO.items():
        e = por_nombre.get(nombre)
        if e is None:
            fallos.append("referencia: no hay ningun cubo llamado '%s'" % nombre)
            continue
        for i in range(3):
            if abs(e["from"][i] - origen[i]) > 1e-6:
                fallos.append("referencia: '%s' empieza en %s y esperabamos %s"
                              % (nombre, e["from"], list(origen)))
                break
            if abs((e["to"][i] - e["from"][i]) - tam[i]) > 1e-6:
                fallos.append("referencia: '%s' mide %s y esperabamos %s"
                              % (nombre, [round(e["to"][j] - e["from"][j], 4)
                                          for j in range(3)], list(tam)))
                break

    # 2 · LA CORONILLA A 32. Es literalmente el numero contra el que se eligio
    #     que la espada midiera 26.
    cab = por_nombre.get("cabeza")
    if cab is not None and abs(cab["to"][1] - 32.0) > 1e-6:
        fallos.append("referencia: la coronilla esta en %.3f, no en 32"
                      % cab["to"][1])

    # 3 · LA MANO A 12. Con la espada empuñada, es donde cae el mango.
    brazo = por_nombre.get("brazo derecho")
    if brazo is not None and abs(brazo["from"][1] - 12.0) > 1e-6:
        fallos.append("referencia: la mano cae en %.3f, no en 12"
                      % brazo["from"][1])

    # 4 · NINGUN CUBO GIRA. Por eso la espada tampoco necesita rotaciones y
    #     todas sus piezas son cajas rectas.
    girados = [e.get("name") for e in cubos if e.get("rotation")]
    if girados:
        fallos.append("referencia: %d cubos giran (%s) -- la espada asume que "
                      "ninguno lo hace" % (len(girados), ", ".join(map(str, girados[:3]))))

    # 5 · LA FINURA. Es lo que se copio de verdad: la rejilla de 0,25 da el
    #     mismo detalle que su placa mas delgada.
    finos = [e["to"][i] - e["from"][i] for e in cubos for i in range(3)
             if 1e-9 < e["to"][i] - e["from"][i] < 1.0]
    if finos and abs(min(finos) - DELGADO) > 5e-3:
        fallos.append("referencia: la placa mas delgada mide %.4f y "
                      "teniamos apuntado %.4f" % (min(finos), DELGADO))
    return fallos
