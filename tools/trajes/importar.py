# -*- coding: utf-8 -*-
"""
DE UN .bbmodel DE BLOCKBENCH A UN TRAJE NUESTRO.

Es la mitad que le faltaba a `blockbench.py`, que solo sabia EXPORTAR. El
usuario dibuja en Blockbench y esto lo trae de vuelta, entero.

⚠⚠⚠ LA REGLA DE ORO: NINGUN ELEMENTO SE PIERDE EN SILENCIO. Cada cubo del
   fichero acaba importado o con un aviso a su nombre, y las cuentas tienen que
   cuadrar (`importados + avisos == elementos`). Un `continue` nuevo sin su
   aviso ROMPE el importador en vez de dejarse una pieza por el camino. Es la
   unica forma de saber que no se esta callando nada: la primera version se
   comio el aro de Arceus --una malla-- y la unica pista fue que el traje salia
   sin su pieza mas caracteristica.


LAS CUATRO CONVERSIONES QUE HAY QUE ACERTAR, Y NINGUNA DA ERROR SI SE FALLA
===========================================================================

1 · LOS EJES.  Blockbench mide Y hacia ARRIBA desde los pies; Minecraft mide Y
    hacia ABAJO desde el pivote del hueso. X y Z **no se tocan**.

        java = (bb_x,  24 - bb_y - alto,  bb_z)

    ⚠⚠⚠ VOLTEAR TAMBIEN LA Z ES EL FALLO CLASICO, y estaba metido en el
       dibujado: `z = -oz - sz`. Con cubos simetricos en Z --que es todo lo que
       se habia probado-- da EXACTAMENTE EL MISMO NUMERO, asi que no se nota;
       en cuanto llega una visera, una corona o una Poke Ball a la espalda, la
       pieza aparece AL OTRO LADO del cuerpo. Verificado contra vanilla en dos
       modelos: la cabeza del jugador y las patas del creeper (bb pivot
       [-2,6,4] -> java (-2,18,4), cubo bb [-4,0,2] -> `cuboid(-2,0,-2,...)`).

2 · LAS ROTACIONES.  El paso anterior es un REFLEJO (det = -1), no un giro, asi
    que invierte el sentido de dos de los tres ejes:

        pitch = -rx      yaw = +ry      roll = -rz

    y el orden se conserva (Blockbench usa ZYX para cubos, y `ModelPart` aplica
    Z, luego Y, luego X: la misma composicion).

    ⚠⚠ Y UN CUBO ROTADO NO CABE EN UN `cuboid()`. `ModelPart` solo sabe girar
       PARTES enteras, asi que cada cubo con rotacion sale como un HUESO HIJO
       con un solo cubo dentro. Es lo mismo que hace Blockbench al exportar un
       modelo de entidad (los `bone_r1`).

3 · LAS CARAS.  El reparto de caja de Minecraft, leido de `ModelPart.Cuboid`:

        [ -X ][ -Z ][ +X ][ +Z ]        <- la fila de los lados
        [ arriba ][ abajo ]             <- encima, en la franja de fondo `d`

    y los nombres de Blockbench caen en ese orden **tal cual vienen**:
    east, north, west, south, up, down.

    ⚠⚠ ESO SE COMPROBO, NO SE SUPUSO, y por tres caminos que tenian que dar lo
       mismo:
         · el cuerpo de Arceus es una SKIN de 64x32 con los desplazamientos
           exactos de vanilla (torso 16,16 · pierna 0,16 · brazo 40,16). Con
           este orden el traje se dibuja EXACTAMENTE como Minecraft dibuja una
           skin, que es lo que su autor queria;
         · las piezas con `mirror_uv` traen east y west intercambiados, que es
           justo lo que hace `mirrored()` en Minecraft;
         · y el casco, que es un modelo de BLOQUE, pasa por un giro de 180º al
           llevarse en la cabeza: su cara +X acaba siendo la -X del jugador, o
           sea el mismo orden.

4 · LA TEXTURA.  Se REHORNEA. Un .bbmodel puede dar a cada cara un trozo
    cualquiera del PNG (la corona lo hace en sus 44 cubos), y `ModelPart` solo
    sabe leer cajas. Asi que para cada cubo se recorta lo que su autor puso en
    cada cara y se pega en el reparto de arriba.

    ⚠⚠ REHORNEAR ES LO QUE HACE QUE LOS DOS MODELOS ENTREN POR LA MISMA PUERTA:
       la corona (UV por cara) y el cuerpo (UV de caja) dejan de ser dos casos.
       Y de paso desaparece el `mirror`: un cubo espejado trae sus caras ya
       intercambiadas y volteadas en el fichero, asi que copiarlas donde dicen
       reproduce el espejo sin tener que llevar la bandera a ninguna parte.

    ⚠ Minecraft calcula el ancho de cada casilla con el TAMAÑO DEL CUBO, no con
      los pixeles que le demos: un texel por unidad, siempre. Por eso el
      horneado usa esas mismas medidas --con decimales incluidos-- y redondea
      solo al pegar. Reservar un pixel de mas «para que quepa» correria las
      casillas de al lado y cada cara leeria un trozo de su vecina.
"""

from __future__ import annotations

import base64
import io
import json
import math
from dataclasses import dataclass, field
from pathlib import Path

from PIL import Image

from .modelo import Cubo, casillas, empaquetar, hornear, huella  # noqa: F401

# ---------------------------------------------------------------- lectura


@dataclass
class Elemento:
    """Un elemento del .bbmodel, con el camino de grupos al que pertenece."""

    nombre: str
    grupos: tuple            # ("Corona2", "armorHead", "bone")
    tipo: str
    f: tuple = None          # esquina minima
    to: tuple = None         # esquina maxima
    rot: tuple = (0.0, 0.0, 0.0)
    pivote: tuple = (0.0, 0.0, 0.0)
    inflate: float = 0.0
    caras: dict = field(default_factory=dict)   # nombre -> (idx_textura, rect)


@dataclass
class Documento:
    ruta: Path
    elementos: list
    texturas: list           # [(nombre, Image)]
    display: dict


def _rect(uv):
    """
    Un UV de Blockbench como (u0, v0, u1, v1) normalizado, y sus volteos.

    ⚠⚠ UN UV «AL REVES» NO ES OTRO UV: es el mismo con una instruccion de
       reflejo dentro. La cara norte del pie derecho es [4,20,8,32] y la del
       izquierdo [8,20,4,32] -- el MISMO rectangulo con la U dada la vuelta.
       Leyendo `uv[0]` como borde izquierdo, el origen sale corrido justo el
       ancho del cubo y la pieza parece rota cuando esta perfecta.
    """
    u0, v0, u1, v1 = uv
    return ((min(u0, u1), min(v0, v1), max(u0, u1), max(v0, v1)),
            u1 < u0, v1 < v0)


def leer(ruta):
    """Lee un .bbmodel y devuelve su Documento, con los grupos resueltos."""
    ruta = Path(ruta)
    d = json.loads(ruta.read_text(encoding="utf-8"))

    texturas = []
    for t in d.get("textures", []):
        s = t.get("source") or ""
        if not s.startswith("data:"):
            raise ValueError(
                "la textura %r no viaja dentro de %s: en Blockbench hay que "
                "guardar los pixeles, no la ruta" % (t.get("name"), ruta.name))
        img = Image.open(io.BytesIO(base64.b64decode(s.split(",", 1)[1])))
        texturas.append((t.get("name") or "?", img.convert("RGBA")))

    crudos = {e["uuid"]: e for e in d.get("elements", [])}
    grupos = {g["uuid"]: g for g in d.get("groups", [])}

    elementos = []

    def recorrer(nodo, camino):
        if isinstance(nodo, str):
            e = crudos.get(nodo)
            if e is None:
                return
            elementos.append(_elemento(e, camino))
            return
        g = grupos.get(nodo.get("uuid"))
        nombre = g.get("name") if g else "?"
        # ⚠⚠ UN GRUPO GIRADO MOVERIA TODO LO QUE CUELGA DE EL, y aqui el arbol
        #    se aplana: los cubos salen en coordenadas absolutas. Mientras el
        #    giro sea cero eso es exacto; con giro habria que componerlo, asi
        #    que se PARA en vez de dibujar un traje descolocado.
        if g and any(abs(float(v)) > 1e-6 for v in (g.get("rotation") or (0, 0, 0))):
            raise ValueError(
                "el grupo %r de %s lleva rotacion %s. El importador aplana el "
                "arbol, asi que un grupo girado descolocaria todo lo que cuelga "
                "de el sin dar ningun error" % (nombre, ruta.name, g["rotation"]))
        for hijo in nodo.get("children", []):
            recorrer(hijo, camino + (nombre,))

    for nodo in d.get("outliner", []):
        recorrer(nodo, ())

    # ⚠ Un elemento que no cuelgue de ningun sitio del outliner no se dibuja en
    #   Blockbench, asi que tampoco aqui -- pero se dice.
    vistos = sum(1 for _ in elementos)
    if vistos != len(crudos):
        raise ValueError(
            "%s tiene %d elementos y el outliner solo alcanza %d: hay piezas "
            "sueltas fuera del arbol" % (ruta.name, len(crudos), vistos))

    return Documento(ruta=ruta, elementos=elementos, texturas=texturas,
                     display=d.get("display") or {})


def _elemento(e, camino):
    caras = {}
    # ⚠ Solo los cubos tienen caras de caja. Una MALLA guarda un UV POR VERTICE
    #   --tres numeros por triangulo, no cuatro por rectangulo-- y leerla con la
    #   misma cuchara revienta al desempaquetar. Se deja para el aviso de
    #   `cubos_de`, que es quien sabe decir que se pierde.
    caras_crudas = (e.get("faces") or {}) if (e.get("type") or "cube") == "cube" else {}
    for nombre, cara in caras_crudas.items():
        uv = cara.get("uv")
        if not uv or cara.get("texture") is None:
            continue          # cara sin pintar: se queda transparente
        if cara.get("rotation"):
            # ⚠ El giro de UV por cara no se usa en ninguno de los modelos que
            #   han llegado. Se para en vez de ignorarlo: ignorarlo saldria como
            #   una textura tumbada, sin error.
            raise ValueError("la cara %s de %r gira su UV %sº y eso no esta "
                             "implementado" % (nombre, e.get("name"),
                                               cara["rotation"]))
        caras[nombre] = (int(cara["texture"]), _rect(uv))
    f, to = e.get("from"), e.get("to")
    return Elemento(
        nombre=(e.get("name") or "").strip(),
        grupos=camino,
        tipo=e.get("type") or "cube",
        f=tuple(float(v) for v in f) if f else None,
        to=tuple(float(v) for v in to) if to else None,
        rot=tuple(float(v) for v in (e.get("rotation") or (0, 0, 0))),
        pivote=tuple(float(v) for v in (e.get("origin") or (0, 0, 0))),
        inflate=float(e.get("inflate") or 0.0),
        caras=caras)


# ------------------------------------------------------- cambios de espacio


def identidad(p):
    return p


def transformacion_cabeza(display):
    """
    La cadena que convierte un modelo de BLOQUE en geometria de la cabeza.

    ⚠⚠⚠ EL CASCO DE ARCEUS NO ES UN MODELO DE ENTIDAD: es un modelo de bloque
       de 0..16 pensado para llevarse en la ranura de la cabeza. Ponerlo tal
       cual dejaria el casco en una esquina, a un octavo de su tamaño. Lo que
       lo coloca es la cadena de verdad de Minecraft, y no una estimacion:

         display.head       trasladar (0, 3,75, 0)/16 y escalar 1,6
         HeadFeatureRenderer  escalar (0,625, -0,625, -0,625), girar 180º en Y
                              y trasladar (0, -0,25, 0)

    ⚠⚠ Y EL 1,6 NO ES CASUAL: 1,6 x 0,625 = 1,0 EXACTO. El autor lo ajusto para
       que el casco salga a escala 1:1 sobre la cabeza, asi que la conversion no
       reescala ni un pixel. Comprobado ademas contra la geometria: la visera
       (bloque x 3..13, z 3,25..7,25) cae en x -5..5 y z -4,75..-0,75, o sea
       centrada y por delante de una cabeza que va de -4 a 4.

    Sale: bb = (8 - x,  y + 22,34375,  z - 8), y un REFLEJO en X que obliga a
    invertir los giros de Y y de Z.
    """
    cab = (display or {}).get("head") or {}
    tx, ty, tz = (list(cab.get("translation") or (0, 0, 0)) + [0, 0, 0])[:3]
    esc = (list(cab.get("scale") or (1, 1, 1)) + [1, 1, 1])[:3]
    if any(abs(e - esc[0]) > 1e-6 for e in esc):
        raise ValueError("la escala de `display.head` no es uniforme (%s) y la "
                         "conversion supone que si" % (esc,))
    if cab.get("rotation") and any(abs(float(r)) > 1e-6 for r in cab["rotation"]):
        raise ValueError("`display.head` lleva rotacion %s y eso no esta "
                         "implementado" % (cab["rotation"],))
    k = esc[0] * 0.625        # el factor neto: 1,0 si la escala es 1,6

    # Los tres ejes se comportan distinto, asi que van explicitos: una formula
    # compacta aqui es una trampa para quien la lea dentro de seis meses.
    #   bb_x = cx - k*x   (ESPEJO: el giro de 180º en Y)
    #   bb_y = dy + k*y   (la Y se invierte dos veces y vuelve a subir)
    #   bb_z = dz + k*z
    cx = 8.0 * k - 0.625 * tx
    dy = 28.0 + 0.625 * ty - 8.0 * k
    dz = -8.0 * k + 0.625 * tz

    def convertir(p):
        x, y, z = p
        return (cx - k * x, dy + k * y, dz + k * z)

    convertir.espejo_x = True
    convertir.escala = k
    return convertir


def _giro_de(rot, espejo_x):
    """
    El giro del elemento en espacio Blockbench.

    ⚠ Un ESPEJO en X invierte el sentido de los giros de Y y de Z (y deja el de
      X como estaba). Olvidarlo saca el casco girado al reves y ni compila peor
      ni avisa.
    """
    rx, ry, rz = rot
    return (rx, -ry, -rz) if espejo_x else (rx, ry, rz)


# ---------------------------------------------------------------- importar


def color_medio(img, rect):
    """El color dominante de una region. Solo para el visor y los avisos."""
    u0, v0, u1, v1 = rect
    caja = img.crop((int(u0), int(v0), max(int(u1), int(u0) + 1),
                     max(int(v1), int(v0) + 1)))
    px = [p for p in caja.getdata() if p[3] > 8]
    if not px:
        return (160, 160, 160)
    return tuple(sum(p[i] for p in px) // len(px) for i in range(3))


def cubos_de(doc, reparto, espacio=None, base_fuentes=0):
    """
    Convierte los elementos de un documento en Cubos, ya colocados en su hueso.

    `reparto(elemento)` devuelve el nombre del hueso, `None` para saltarselo sin
    ruido (el maniqui de referencia) o lanza si no sabe.

    Devuelve (huesos, avisos) donde huesos es {hueso: [Cubo]}.
    """
    convertir = espacio or identidad
    espejo_x = bool(getattr(convertir, "espejo_x", False))
    escala = float(getattr(convertir, "escala", 1.0))

    huesos, avisos, saltados = {}, [], 0
    for e in doc.elementos:
        if e.tipo != "cube" or e.f is None or e.to is None:
            # ⚠⚠⚠ ESTO SE CAIA EN SILENCIO Y ERA EL FALLO DE SIEMPRE. El aro de
            #    Arceus es una MALLA (13 vertices, 12 triangulos) y el dibujado
            #    de armadura son CAJAS: no se puede convertir, hay que ponerla a
            #    mano. Pero se DICE, que es la diferencia entre una decision y
            #    una pieza perdida.
            avisos.append("%r es de tipo %r y no un cubo: el dibujado de "
                          "armadura solo sabe hacer cajas"
                          % (e.nombre or "sin nombre", e.tipo))
            continue

        hueso = reparto(e)
        if hueso is None:
            saltados += 1
            continue

        # Los dos extremos pasan por la conversion y luego se reordena, porque
        # un espejo intercambia el minimo con el maximo.
        a, b = convertir(e.f), convertir(e.to)
        origen = tuple(min(a[i], b[i]) for i in range(3))
        tam = tuple(abs(b[i] - a[i]) for i in range(3))

        c = Cubo(origen=tuple(round(v, 5) for v in origen),
                 tam=tuple(round(v, 5) for v in tam),
                 color=(180, 180, 190),
                 material="metal",
                 inflate=round(e.inflate * escala, 5))
        c.caras_src = {n: (base_fuentes + idx, r) for n, (idx, r) in e.caras.items()}
        if any(abs(v) > 1e-6 for v in e.rot):
            c.rot = tuple(round(v, 5) for v in _giro_de(e.rot, espejo_x))
            c.pivote = tuple(round(v, 5) for v in convertir(e.pivote))
        for cara, (idx, (rect, _, _)) in e.caras.items():
            if cara == "north":
                c.color = color_medio(doc.texturas[idx][1], rect)
        huesos.setdefault(hueso, []).append(c)

    traidos = sum(len(v) for v in huesos.values())
    if traidos + len(avisos) + saltados != len(doc.elementos):
        raise AssertionError(
            "el importador se ha comido algo en %s: %d elementos, %d traidos, "
            "%d saltados y %d avisos" % (doc.ruta.name, len(doc.elementos),
                                         traidos, saltados, len(avisos)))
    return huesos, avisos
