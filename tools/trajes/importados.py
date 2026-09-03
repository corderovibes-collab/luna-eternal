# -*- coding: utf-8 -*-
"""
LOS DOS TRAJES QUE VIENEN DE BLOCKBENCH: CAMPEON y LEYENDA.

⚠⚠⚠ ESTOS NO SE MODELAN AQUI: SE IMPORTAN. A diferencia de `leyenda.py`, que se
   midio de una referencia y se escribio cubo a cubo, estos vienen enteros de
   los .bbmodel del usuario. Lo unico que se construye a mano es LA RUEDA de
   Arceus, y por un motivo concreto que esta abajo.

   CAMPEON   arte/trajes/campeon-corona.bbmodel        corona + cuerpo
   LEYENDA   arte/trajes/leyenda-arceus-casco.bbmodel  el casco
             arte/trajes/leyenda-arceus-cuerpo.bbmodel el cuerpo + la rueda

⚠⚠ LOS FICHEROS VIVEN EN EL REPO, NO EN DESCARGAS. La primera version los
   buscaba en `~/Downloads`, y eso es exactamente la leccion que este proyecto
   ya pago con las seis pantallas en magenta: un generador que depende de un
   fichero que no esta en git NO SE PUEDE VOLVER A EJECUTAR, y nadie se entera
   hasta que hace falta.

⚠⚠ EL LADO SE MIDE, NO SE LEE DEL NOMBRE. Los dos ficheros llaman `right_arm`
   al cubo que esta en x=+4 --o sea el brazo IZQUIERDO-- porque lo nombran desde
   el punto de vista de quien mira, que es lo natural dibujando. Que la derecha
   esta en X NEGATIVA lo dice el propio fichero de la corona: su maniqui de
   referencia se llama «brazo derecho» y va de x=-8 a -4, que es donde vanilla
   pone el brazo derecho.
   ⚠ Y asignar por el nombre NO DARIA NINGUN ERROR: los miembros son espejos,
     asi que la silueta sale bien y solo las costuras caen al lado que no es.

⚠⚠ LAS HOLGURAS SON LAS DEL AUTOR, no unas nuestras. El cuerpo viene con
   `inflate` puesto: 0,75 en torso, brazos y botas, y 0,5 en cintura y perneras.
   No es un descuido, son DOS CAPAS separadas 0,25 -- y las dos por encima de
   0,25, que es donde esta la capa exterior de la piel del jugador. A holgura
   cero el traje PARPADEA contra la piel; con dos capas a la misma, parpadean
   entre ellas. Pisarlas con un numero propio rompe las dos cosas a la vez.
"""

from __future__ import annotations

from pathlib import Path

from . import modelo as M
from .importar import (color_medio, cubos_de, leer,
                       transformacion_cabeza)
from .modelo import Cubo

ARTE = Path(__file__).resolve().parents[2] / "arte" / "trajes"

CORONA = ARTE / "campeon-corona.bbmodel"
CASCO = ARTE / "leyenda-arceus-casco.bbmodel"
CUERPO = ARTE / "leyenda-arceus-cuerpo.bbmodel"


# ------------------------------------------------------------- el reparto

# Grupo de primer nivel -> familia. Se reparte POR GRUPO y no por nombre de
# elemento: un grupo es lo que el autor agrupo a proposito, y los nombres de los
# elementos de estos ficheros son «cubo» cuarenta y cuatro veces.
FAMILIA = {
    "torso": "cuerpo",
    "hips": "cuerpo",          # la cintura: es la capa de los pantalones
    "left_arm": "brazo",
    "right_arm": "brazo",
    "left_leg": "pierna",
    "right_leg": "pierna",
    "left_boot": "bota",
    "right_boot": "bota",
}

LADOS = {
    "brazo": ("armorRightArm", "armorLeftArm"),
    "pierna": ("armorRightLeg", "armorLeftLeg"),
    "bota": ("armorRightBoot", "armorLeftBoot"),
}


def _reparto_cuerpo(cabeza=()):
    """
    El reparto del cuerpo, comun a los dos trajes.

    `cabeza` son los grupos cuyo contenido va al casco.
    """
    def reparto(e):
        raiz = e.grupos[0] if e.grupos else ""
        # ⚠ El maniqui del jugador esta DENTRO del fichero de la corona, para
        #   que su autor viera donde cae cada cosa. Copiarlo al traje pondria
        #   una segunda cabeza gris encima de la del jugador.
        if raiz.upper().startswith("EL JUGADOR"):
            return None
        if raiz in cabeza:
            return "armorHead"
        familia = FAMILIA.get(raiz)
        if familia is None:
            raise ValueError(
                "el grupo %r de %r no esta repartido. Un grupo sin hueso se "
                "quedaria fuera del traje sin dar ningun error, asi que hay "
                "que decidirlo aqui" % (raiz, e.nombre))
        if familia == "cuerpo":
            return "armorBody"
        izq, der = LADOS[familia]
        return izq if (e.f[0] + e.to[0]) / 2.0 < 0 else der
    return reparto


def _traje(id_, nombre, partes):
    """
    Monta un Traje a partir de (documento, reparto, espacio) y encadena las
    texturas de todos los ficheros en una sola lista.
    """
    t = M.Traje(id_, nombre)
    avisos = []
    for doc, reparto, espacio in partes:
        base = len(t.fuentes)
        huesos, aviso = cubos_de(doc, reparto, espacio, base_fuentes=base)
        for hueso, cubos in huesos.items():
            t.poner(hueso, *cubos)
        avisos += ["%s: %s" % (doc.ruta.name, a) for a in aviso]
        t.fuentes += [img for _, img in doc.texturas]
    return t, avisos


# ----------------------------------------------------------------- CAMPEON

def campeon():
    """La corona y su armadura. Un solo fichero, cuerpo entero."""
    doc = leer(CORONA)
    return _traje("campeon", "Traje CAMPEON · Corona",
                  [(doc, _reparto_cuerpo(cabeza=("Corona2",)), None)])


# ----------------------------------------------------------------- LEYENDA

# ⚠ Medido de la malla, no elegido: sus doce vertices de borde caen a 16,565 del
#   centro, que es el CIRCUNRADIO de un docecagono cuyo apotema es 16,0 exactos
#   -- o sea el radio del anillo dibujado. Por eso la plancha mide 32 y la
#   textura de 32x32 le cae pixel a pixel. Con 33 habria que reescalar el
#   dibujo, y reescalar arte de pixel es justo lo que lo estropea.
RUEDA_RADIO = 16.0
RUEDA_GROSOR = 1.0


def _rueda(doc):
    """
    La rueda de Arceus: una plancha fina con el dibujo del autor.

    ⚠⚠⚠ LA RUEDA NO ES GEOMETRIA, ESTA DIBUJADA. En el fichero es una MALLA de
       13 vertices y 12 triangulos con grosor CERO, y su dibujo entero --el
       anillo, los radios y las gemas-- vive en «aro blanco.png» con fondo
       transparente. Rehacerla con barras seria REDIBUJAR A MANO lo que su autor
       ya dibujo, y ademas el dibujado de armadura son cajas: no hay forma de
       meter una malla.

    ⚠⚠ Y SE PUEDE PORQUE LA ARMADURA SE PINTA CON RECORTE DE ALFA
       (`getEntityCutoutNoCull`): un pixel transparente no se pinta. Sobre una
       capa opaca la rueda saldria dentro de un cuadrado blanco.

    ⚠ LOS CANTOS SE QUEDAN SIN PINTAR A PROPOSITO. El borde de la plancha es el
      del CUADRADO, y el anillo solo lo toca en cuatro puntos: pintarlo dibujaria
      una barra dorada recta por fuera del aro. Sin pintar, la rueda desaparece
      vista exactamente de canto -- que es lo que hace tambien la malla del
      autor, que no tiene grosor ninguno.
    """
    idx = None
    for i, (n, _) in enumerate(doc.texturas):
        if "aro" in n.lower():
            idx = i
    if idx is None:
        return None, ["no encuentro la textura del aro: la rueda se queda fuera"]

    malla = [e for e in doc.elementos if e.tipo == "mesh"]
    if len(malla) != 1:
        return None, ["esperaba UNA malla (la rueda) y hay %d" % len(malla)]
    cx, cy, cz = malla[0].pivote

    ancho, alto = doc.texturas[idx][1].size
    rect = ((0, 0, ancho, alto), False, False)
    c = Cubo(origen=(cx - RUEDA_RADIO, cy - RUEDA_RADIO, cz),
             tam=(RUEDA_RADIO * 2, RUEDA_RADIO * 2, RUEDA_GROSOR),
             color=color_medio(doc.texturas[idx][1], (0, 0, ancho, alto)),
             material="metal")
    # ⚠⚠ SE PINTA POR LAS DOS CARAS, Y ESA ES LA PARTE QUE NO SE VE VENIR. En el
    #    reparto de caja, delante y detras caen en DOS CASILLAS DISTINTAS.
    #    Pintando solo una, la rueda se veria por delante y por detras seria
    #    BASURA -- lo que hubiera en el lienzo al lado. Y no daria ningun error.
    c.caras_src = {"north": (idx, rect), "south": (idx, rect)}
    return c, []


def leyenda():
    """El casco de Arceus, su armadura y la rueda."""
    casco = leer(CASCO)
    cuerpo = leer(CUERPO)

    # ⚠⚠⚠ EL CASCO ES UN MODELO DE BLOQUE, no de entidad: va de 0 a 16 y se
    #    lleva en la ranura de la cabeza. Sin la conversion de `display.head`
    #    saldria en una esquina y a un octavo de su tamaño.
    espacio = transformacion_cabeza(casco.display)

    def solo_cabeza(e):
        return "armorHead"

    t, avisos = _traje(
        "leyenda", "Traje LEYENDA · Arceus",
        [(casco, solo_cabeza, espacio),
         (cuerpo, _reparto_cuerpo(), None)])

    rueda, mas = _rueda(cuerpo)
    avisos += mas
    if rueda is not None:
        # La rueda usa las texturas del SEGUNDO fichero, que ya van detras de
        # las del casco en la lista.
        base = len(casco.texturas)
        rueda.caras_src = {cara: (base + i, r)
                           for cara, (i, r) in rueda.caras_src.items()}
        t.poner("armorBody", rueda)
    return t, avisos
