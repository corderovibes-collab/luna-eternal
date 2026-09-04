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


# Los pares de miembros que en una skin COMPARTEN dibujo.
PARES = (("armorRightArm", "armorLeftArm"),
         ("armorRightLeg", "armorLeftLeg"),
         ("armorRightBoot", "armorLeftBoot"))


def _espejar_pares(t):
    """
    Marca el espejo en el miembro de X POSITIVA cuando los dos comparten caja.

    ⚠⚠⚠ ESTO ES LA REGLA DE VAINILLA, NO UNA CORRECCION AL AUTOR. En una skin de
       64x32 los dos brazos leen EL MISMO recuadro de textura, y Minecraft dibuja
       el de +X con `mirrored()`. La casilla 1 del reparto cae siempre en la cara
       de X MINIMA: en el brazo de x negativa esa es la cara de FUERA, y en el de
       x positiva es la de DENTRO. Sin el espejo, un brazo sale bien y el otro
       con el dibujo cambiado de lado.

    ⚠⚠ Y ASI SALIO: «la manga derecha quedo bien y la izquierda volteada». Un
       brazo correcto y el otro no es la firma de este fallo -- si fuera el
       convenio de caras, estarian mal los dos.

    ⚠ Se mira si COMPARTEN CAJA, no si el fichero trae `mirror_uv`. Dos motivos:
      el .bbmodel marca el espejo en el miembro de X NEGATIVA --al reves que
      vainilla-- y ademas, si algun dia cada brazo tiene su propio dibujo, no hay
      nada que espejar y esto no hace nada.
    """
    avisos = []
    for der, izq in PARES:
        a = [c for c in t.huesos.get(der, []) if c.caja_src]
        b = [c for c in t.huesos.get(izq, []) if c.caja_src]
        if len(a) != 1 or len(b) != 1 or a[0].caja_src != b[0].caja_src:
            continue
        b[0].espejo = True
        avisos.append("%s comparte dibujo con %s: se espeja (la regla de vainilla)"
                      % (izq, der))
    return avisos


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
    avisos += _espejar_pares(t)
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
    # ⚠⚠⚠ SE PINTA UNA SOLA CARA, Y ESO NO ES UN RECORTE: ES EL ARREGLO. La
    #    primera version pintaba la de delante Y la de detras, que sobre el papel
    #    es lo correcto --en el reparto de caja caen en casillas distintas-- y en
    #    el juego se ve como DOS AROS: la plancha tiene grosor, asi que los dos
    #    dibujos quedan separados y desde cualquier angulo que no sea el frontal
    #    exacto se ven los dos bordes. El usuario lo describio exactamente asi:
    #    «como que se duplica».
    #
    #    ⚠⚠ Y SE PUEDE PINTAR UNA SOLA PORQUE LA ARMADURA SE DIBUJA SIN DESCARTE
    #       DE CARAS TRASERAS (`getEntityCutoutNoCull`): un plano se ve por los
    #       dos lados. Con la de atras transparente, desde detras se ve ESTA
    #       misma a traves -- un aro, no dos.
    #
    #    ⚠ Va en la cara NORTE porque el aro cuelga a la espalda (z positivo): a
    #      quien mire al jugador de frente le queda detras, y es esa cara la que
    #      le da.
    c.caras_src = {"north": (idx, rect)}
    return c, []


# ------------------------------------------------- las hombreras al reves

# ⚠⚠⚠ ESTA ES LA UNICA VEZ QUE NO SE RESPETA EL .bbmodel, Y POR ESO SE DICE EN
#    VOZ ALTA AL GENERAR. Las dos hombreras vienen mal puestas en el fichero, y
#    hacen falta DOS correcciones distintas -- se descubrieron una detras de
#    otra, y son cosas diferentes aunque las dos se arreglen girando:
#
#      EL GIRO (roll)      el extremo que va en la AXILA quedaba colgando por
#                          fuera del hombro. Medido antes de tocar:
#                            como viene  0 esquinas dentro de la axila
#                            invertida   2 esquinas dentro de la axila
#                          (axila = pegado al torso, x > -5,5, y por debajo del
#                           hombro, y < 23,5; el torso va de -4 a 4)
#
#      MEDIA VUELTA (yaw)  y aun asi salia LA CARA QUE NO ES hacia afuera: en el
#                          juego se veia el panel ROJO del interior mirando al
#                          frente. Eso no es posicion, es ORIENTACION, y por eso
#                          el primer arreglo no lo toco: invertir el `roll`
#                          cambia hacia donde SE INCLINA la pieza, no hacia donde
#                          MIRA. Lo caza el usuario en el juego, no la lamina.
#
#    ⚠⚠ LAS DOS SON INDEPENDIENTES, y eso es lo que costo ver. La hombrera es
#       casi simetrica de perfil, asi que media vuelta NO cambia su silueta --
#       solo que cara queda fuera--. Por eso la primera correccion parecia
#       completa mirando la lamina: la silueta ya era la buena.
#
# ⚠⚠ SE CORRIGEN SOLO ESTAS DOS, Y NO EL CONVENIO ENTERO. Es la pregunta que hay
#    que hacerse aqui, porque «los giros salen al reves» tiene dos causas
#    posibles y el arreglo es distinto:
#
#      a) el convenio de giro esta mal   -> habria que invertir TODOS los `roll`
#      b) esas dos piezas estan mal      -> se corrigen esas dos
#
#    Es (b), y ahora lo dice el juego y no un razonamiento: la corona, el casco,
#    la cresta, los cuernos y las perneras salen bien en las mismas capturas en
#    las que las hombreras salen mal. Si fuera el convenio, estarian TODAS mal.
#
# ⚠ Se busca por GIRO y por HUESO, no por nombre: los dos cubos se llaman «cube».
HOMBRERAS = ("armorRightArm", "armorLeftArm")
HOMBRERA_GIRO_MINIMO = 45.0
MEDIA_VUELTA = 180.0


# ⚠⚠⚠ AQUI HUBO UNA CORRECCION QUE SOBRABA, Y LA HISTORIA IMPORTA. Los brazos
#    salian volteados en los dos trajes --la armadura pintada por dentro y la
#    piel a la vista por fuera-- y se «arreglo» dandoles media vuelta. Funciono
#    de lado, y rompio otra cosa: la cara de ARRIBA del brazo tiene su ultima
#    columna transparente a proposito --es el borde que va pegado al torso-- y
#    media vuelta la saco al hombro, o sea un HUECO donde antes no habia nada.
#
#    La causa real no era el modelo: era el horneado. Un cubo con `mirror_uv`
#    guarda `east` y `west` intercambiados, y hornear por nombre deshacia ese
#    intercambio. Hoy una caja se copia ENTERA (`importar.caja_uv`), asi que el
#    espejo del autor viaja dentro y no hay nada que corregir aqui.
#
#    ⚠⚠ LA LECCION: una correccion que arregla el sintoma sin explicar la causa
#       tapa el fallo por un lado y lo abre por otro. El brazo se veia bien de
#       frente y aparecio un hueco en el hombro.


def _hombreras_al_reves(t):
    """Endereza las hombreras. Devuelve los avisos, para que se vea."""
    avisos = []
    for hueso in HOMBRERAS:
        for c in t.huesos.get(hueso, []):
            if not (c.rot and abs(c.rot[2]) >= HOMBRERA_GIRO_MINIMO):
                continue
            antes = c.rot
            # ⚠ El orden es ZYX, asi que poner 180 en la Y significa «date media
            #   vuelta y DESPUES inclinate»: primero mira a donde tiene que
            #   mirar, luego cae sobre el hombro.
            c.rot = (antes[0], antes[1] + MEDIA_VUELTA, -antes[2])
            avisos.append(
                "hombrera de %s: venia a (%.1f, %.1f, %.1f) y sale a "
                "(%.1f, %.1f, %.1f) -- giro invertido y media vuelta, a proposito"
                % (hueso, antes[0], antes[1], antes[2], *c.rot))
    if not avisos:
        # ⚠ Si un dia el fichero llega ya corregido, esto deja de encontrar nada
        #   y hay que quitarlo -- o estaria torciendo lo que ya esta bien.
        avisos.append("no he encontrado ninguna hombrera que girar: si el "
                      ".bbmodel ya viene corregido, sobra `_hombreras_al_reves`")
    return avisos


# ⚠⚠⚠ EL CASCO DEJABA VER LA CORONILLA, y es geometria del fichero, no del
#    horneado. Medido barriendo la tapa de la cabeza (y=32, x -4..4, z -4..4) a
#    medio bloque: 96 de 256 puntos al aire, TODOS en la franja de delante
#    (z -4 .. -0,75). Es el trozo de pelo que se ve desde arriba.
#
#    La causa: el casco son dos platos, y el de DELANTE se queda una unidad por
#    debajo del de DETRAS --31,8 contra 32,8-- asi que entre el borde del plato
#    y la cabeza (32) queda una rendija abierta.
#
#    ⚠⚠ SE IGUALAN LOS DOS PLATOS en vez de inventar un cubo tapa. Es geometria
#       del propio autor: el plato de delante crece hasta donde ya llega el de
#       detras, que es donde tenia que llegar. Un cubo nuevo seria arte mio
#       metido en su modelo.
#
#    ⚠ No se busca por nombre --los 19 cubos del casco se llaman «cube»-- sino
#      por su medida y su sitio. Si el .bbmodel cambia y ya no aparece, se DICE:
#      callarlo dejaria el agujero de vuelta sin que nadie se enterara.
CASCO_PLATO = ((-5.0, 29.80543, -4.75), (10.0, 2.0, 4.0))
CASCO_ALTO_BUENO = 3.0


def _tapar_coronilla(t):
    """Iguala el plato frontal del casco con el trasero."""
    for c in t.huesos.get("armorHead", []):
        if c.rot:
            continue
        if (all(abs(c.origen[i] - CASCO_PLATO[0][i]) < 0.01 for i in range(3))
                and all(abs(c.tam[i] - CASCO_PLATO[1][i]) < 0.01 for i in range(3))):
            c.tam = (c.tam[0], CASCO_ALTO_BUENO, c.tam[2])
            return ["casco: el plato de delante sube de %.1f a %.1f para tapar la "
                    "coronilla" % (CASCO_PLATO[1][1], CASCO_ALTO_BUENO)]
    return ["no encuentro el plato frontal del casco: si el .bbmodel cambio, "
            "comprueba que la coronilla siga tapada"]


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

    avisos += _hombreras_al_reves(t)
    avisos += _tapar_coronilla(t)

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
