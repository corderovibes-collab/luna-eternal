# -*- coding: utf-8 -*-
"""
QUE EL .geo.json Y EL DIBUJADO DE JAVA DIGAN LO MISMO.

⚠⚠⚠ ESTO NO ES UNA PRUEBA DE ADORNO: ES EL UNICO SITIO DONDE SE VE UN SIGNO
   CAMBIADO. Entre `modelo.py` (que escribe el fichero) y `Trajes.java` (que lo
   lee) hay un acuerdo de cuatro partes --los ejes, el pivote, el orden de los
   giros y el signo de cada uno-- y NINGUNA de las cuatro da error si se falla:

     el eje mal      la corona sale detras de la cabeza
     el pivote mal   la punta se va a otro barrio
     el orden mal    el cuerno apunta donde no es, solo si gira en dos ejes
     el signo mal    la cresta se dobla hacia dentro en vez de hacia atras

   Un acuerdo entre dos ficheros que nadie comprueba es exactamente la clase de
   cosa que este proyecto ya ha pagado varias veces.

COMO SE COMPRUEBA. Se rehace AQUI lo que hace Java --leyendo el .geo.json de
verdad, no el objeto en memoria-- y se mira si el cubo acaba donde lo puso su
autor en Blockbench:

    bb  --(lo que escribe modelo.py)-->  .geo.json
        --(lo que hace Trajes.java)-->  java
        --(volver: y = 24 - y)-->       bb otra vez

y las dos puntas tienen que coincidir cubo a cubo, esquina a esquina.

⚠⚠ IDA Y VUELTA POR EL FICHERO, no por la memoria. Comparando el objeto consigo
   mismo pasaria siempre; lo que se quiere saber es si lo ESCRITO se lee bien.
"""

from __future__ import annotations

import json
import math
from pathlib import Path

import numpy as np

from . import modelo as M

# ⚠ El .geo.json redondea a 4 decimales, asi que un giro de 20,94102º se guarda
#   como 20,941. Sobre un brazo de palanca de cinco unidades eso son millonesimas
#   -- pero la tolerancia tiene que dejarlas pasar, o la prueba grita por el
#   redondeo y deja de servir para lo que se escribio.
TOLERANCIA = 1e-3


def _giro(rx, ry, rz):
    """Matriz ZYX: la que usa Blockbench para los cubos y `ModelPart` al pintar."""
    rx, ry, rz = (math.radians(v) for v in (rx, ry, rz))
    mx = np.array([[1, 0, 0], [0, math.cos(rx), -math.sin(rx)],
                   [0, math.sin(rx), math.cos(rx)]])
    my = np.array([[math.cos(ry), 0, math.sin(ry)], [0, 1, 0],
                   [-math.sin(ry), 0, math.cos(ry)]])
    mz = np.array([[math.cos(rz), -math.sin(rz), 0],
                   [math.sin(rz), math.cos(rz), 0], [0, 0, 1]])
    return mz @ my @ mx


def _distancia(a, b):
    """
    Lo lejos que estan dos cajas, mirandolas como CONJUNTOS de esquinas.

    ⚠⚠ COMPARAR LAS LISTAS ORDENADAS NO VALE, y me costo un rato: dos esquinas
       que caen casi en el mismo sitio pueden ordenarse al reves por una
       millonesima de redondeo, y entonces la comparacion posicion a posicion
       dice que la pieza se ha movido 4,6 unidades cuando no se ha movido nada.
       Es una prueba que falla por su propia aritmetica, que es la peor clase de
       prueba que hay.
    """
    peor = 0.0
    for x, y in ((a, b), (b, a)):
        for q in x:
            peor = max(peor, min(float(np.abs(q - r).max()) for r in y))
    return peor


def _esquinas(origen, tam):
    o, t = np.array(origen, float), np.array(tam, float)
    return [o + np.array([t[0] * i, t[1] * j, t[2] * k], float)
            for i in (0, 1) for j in (0, 1) for k in (0, 1)]


def _a_java(p):
    return np.array([p[0], 24.0 - p[1], p[2]])


def comprobar(traje, destino):
    """
    Devuelve la lista de fallos. Vacia es que las dos mitades dicen lo mismo.
    """
    fallos = []
    for pieza in M.PIEZAS:
        huesos = traje.de_pieza(pieza)
        if not huesos:
            continue
        f = (Path(destino) / M.CARPETA / traje.id
             / ("%s_%s.geo.json" % (traje.id, pieza)))
        if not f.exists():
            fallos.append("no existe %s" % f.name)
            continue
        bones = json.loads(f.read_text(encoding="utf-8"))
        bones = bones["minecraft:geometry"][0]["bones"]
        porNombre = {b["name"]: b for b in bones}

        # Lo que el traje dice que hay, para poder emparejar por geometria.
        esperados = []
        for hueso, cubos in huesos.items():
            for c in cubos:
                puntos = _esquinas(c.origen, c.tam)
                if c.rot:
                    r, p = _giro(*c.rot), np.array(c.pivote, float)
                    puntos = [r @ (q - p) + p for q in puntos]
                esperados.append((hueso, [np.round(q, 5) for q in puntos]))

        obtenidos = []
        for b in bones:
            if not b.get("cubes"):
                continue
            nombre = b["name"]
            # ⚠ El hueso raiz de la pieza es el que cuelga de un `biped*`; los
            #   girados cuelgan de el. Se recorre hacia arriba igual que hace
            #   Java al construir el arbol.
            cadena, actual = [], b
            while actual is not None and not actual.get("parent", "").startswith("biped"):
                cadena.append(actual)
                actual = porNombre.get(actual.get("parent"))
            if actual is None:
                fallos.append("%s: %s no llega a ningun ancla `biped*`"
                              % (pieza, nombre))
                continue
            cadena.append(actual)
            raiz = cadena[-1]

            # El pivote de cada hueso, en coordenadas Java (lo que hace
            # `pivoteJava`). El de la raiz se lo machaca `copyTransform` con el
            # de vainilla, que es el mismo numero.
            jp = {h["name"]: _a_java(h["pivot"]) for h in cadena if "pivot" in h}

            for cubo in b["cubes"]:
                o, s = np.array(cubo["origin"], float), np.array(cubo["size"], float)
                puntos = _esquinas(o, s)
                # a Java: solo la Y se voltea, y la esquina baja pasa a ser alta
                puntos = [_a_java(q) for q in puntos]
                # de dentro hacia fuera: cada hueso girado aplica su giro sobre
                # su propio pivote, en el espacio de su padre
                for h in cadena:
                    if "rotation" not in h:
                        continue
                    rx, ry, rz = h["rotation"]
                    r = _giro(-rx, ry, -rz)          # los signos de Java
                    p = jp[h["name"]]
                    puntos = [r @ (q - p) + p for q in puntos]
                # y de vuelta a Blockbench para poder comparar
                puntos = [_a_java(q) for q in puntos]
                obtenidos.append((raiz["name"], [np.round(q, 5) for q in puntos]))

        if len(esperados) != len(obtenidos):
            fallos.append("pieza %s: el traje tiene %d cubos y el fichero %d"
                          % (pieza, len(esperados), len(obtenidos)))
            continue

        # ⚠ Se emparejan por POSICION, no por orden: el orden dentro del fichero
        #   es cosa del escritor y cambiarlo no seria un fallo.
        libres = list(obtenidos)
        for hueso, puntos in esperados:
            mejor, dist = None, 1e9
            for i, (h2, p2) in enumerate(libres):
                d = _distancia(puntos, p2)
                if d < dist:
                    mejor, dist, hueso2 = i, d, h2
            if mejor is None or dist > TOLERANCIA:
                fallos.append(
                    "pieza %s: el cubo de %s no aparece donde debia (se desvia "
                    "%.4f). El acuerdo entre modelo.py y Trajes.java se ha roto"
                    % (pieza, hueso, dist))
            elif hueso2 != hueso:
                fallos.append("pieza %s: un cubo de %s sale colgado de %s"
                              % (pieza, hueso, hueso2))
            if mejor is not None:
                libres.pop(mejor)
    return fallos
