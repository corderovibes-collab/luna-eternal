# -*- coding: utf-8 -*-
"""
EL TRAJE LEYENDA - ARCEUS, traido del Blockbench del usuario.

⚠⚠⚠ ESTE NO SE MODELA AQUI: SE IMPORTA. A diferencia de `leyenda.py`, que se
   midio de una referencia y se escribio cubo a cubo, este viene entero de
   `armadura arceus.bbmodel`. Lo unico que se construye a mano es LA RUEDA, y
   por un motivo concreto que esta abajo.

LO QUE TRAE EL FICHERO, medido y no supuesto:

    8 cubos de cuerpo    torso, cintura, dos brazos, dos piernas, dos botas
                         con las texturas del autor, que son SKINS de 64x32
    6 barras rotadas     los radios sueltos, a 21, 45 y 67,5 grados
    1 malla `circle`     13 vertices, 12 triangulos: un DISCO plano

⚠⚠⚠ LA RUEDA NO ES GEOMETRIA: ESTA DIBUJADA. El disco no tiene grosor (z va de
   0 a 0) y su dibujo entero --el anillo, los radios y las gemas-- vive en
   `aro blanco.png` con fondo transparente. Reconstruirla con barras, que es
   como se hizo el halo de `leyenda.py`, seria REDIBUJAR A MANO algo que el
   autor ya dibujo. Se pone tal cual, sobre una plancha fina.

   ⚠⚠ Y SE PUEDE PORQUE LA ARMADURA SE DIBUJA CON RECORTE DE ALFA
      (`getArmorCutoutNoCull`): un pixel transparente no se pinta. Sobre una
      capa opaca la rueda saldria dentro de un cuadrado blanco.

⚠⚠ LOS SEIS RADIOS SUELTOS SE QUEDAN FUERA DE ESTA PRIMERA VERSION, y se dice
   en vez de callarlo: llevan rotaciones de tres ejes y el dibujado de armadura
   de Minecraft son cajas rectas. La rueda ya lleva sus radios DIBUJADOS, asi
   que lo que falta es el relieve, no el motivo.
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image

from . import modelo as M
from .importar import importar
from .modelo import Cubo

# El .bbmodel del usuario. Se busca en Descargas y, si no, junto al proyecto.
ORIGEN = [
    Path.home() / "Downloads" / "armadura arceus.bbmodel",
    Path(__file__).resolve().parents[2] / "arte" / "armadura arceus.bbmodel",
]

LADO = 256            # el lienzo. Cabe todo con sitio para la rueda
GROSOR_RUEDA = 1.0

# ⚠ Medido de la malla: 12 vertices de borde a radio 16,56 y uno en el centro.
#   Se redondea a 16 para que la rueda mida 32 EXACTOS y su textura de 32x32
#   caiga pixel a pixel. Con 16,56 habria que reescalar el dibujo del autor, y
#   un reescalado de arte de pixel es justo lo que lo estropea.
RADIO = 16.0
CENTRO_Y = 20.0       # el origen del elemento, tal cual venia
CENTRO_Z = 7.0        # detras del jugador: +Z es la espalda


def _fichero():
    for p in ORIGEN:
        if p.exists():
            return p
    raise FileNotFoundError(
        "no encuentro 'armadura arceus.bbmodel'. Lo busco en %s"
        % " y en ".join(str(p) for p in ORIGEN))


def _rueda(lienzo, aro, u, v):
    """
    La plancha de la rueda: pega el dibujo y devuelve el cubo con su UV.

    ⚠⚠⚠ SE PEGA DOS VECES, Y ESA ES LA PARTE QUE NO SE VE VENIR. Una caja se
       mapea por el reparto de Bedrock, donde la cara de delante y la de detras
       caen en DOS SITIOS DISTINTOS de la textura. Pegando el dibujo una sola
       vez, la rueda se veria por delante y por detras seria basura --lo que
       hubiera en el lienzo al lado--. Y no daria ningun error.

    ⚠ La copia de atras va REFLEJADA porque se mira desde el otro lado: sin
      reflejarla, la rueda estaria del reves segun por donde pases.
    """
    w = int(RADIO * 2)                       # 32
    d = int(GROSOR_RUEDA)                    # 1
    arte = aro.resize((w, w), Image.NEAREST) if aro.size != (w, w) else aro
    lienzo.paste(arte, (u + d, v + d), arte)
    lienzo.paste(arte.transpose(Image.FLIP_LEFT_RIGHT),
                 (u + d + w + d, v + d), arte.transpose(Image.FLIP_LEFT_RIGHT))
    c = Cubo(origen=(-RADIO, CENTRO_Y - RADIO, CENTRO_Z),
             tam=(RADIO * 2, RADIO * 2, GROSOR_RUEDA),
             color=(245, 240, 250), material="metal")
    c.uv = (u, v)
    c.espejo = False
    return c


def arceus():
    """El traje entero: cuerpo importado + rueda."""
    ruta = _fichero()
    t, lienzo, avisos = importar(ruta, "arceus", "Traje LEYENDA · Arceus", LADO)

    # El dibujo de la rueda es la ultima textura del fichero.
    import base64, io, json
    d = json.loads(Path(ruta).read_text(encoding="utf-8"))
    aro = None
    for tex in d.get("textures", []):
        if "aro" in (tex.get("name") or "").lower():
            aro = Image.open(io.BytesIO(base64.b64decode(
                (tex["source"]).split(",", 1)[1]))).convert("RGBA")
    if aro is None:
        avisos.append("no encuentro la textura del aro: la rueda se queda fuera")
    else:
        # ⚠ Debajo de lo ya pegado, que ocupa hasta y=96. Se deja margen y se
        #   comprueba que cabe: una plancha que se saliera del lienzo no daria
        #   error, daria una rueda a medio pintar.
        u, v = 0, 128
        ancho, alto = 2 * int(GROSOR_RUEDA) + 4 * int(RADIO), int(GROSOR_RUEDA) + 2 * int(RADIO)
        if u + ancho > LADO or v + alto > LADO:
            raise ValueError("la rueda (%dx%d) no cabe en el lienzo de %d"
                             % (ancho, alto, LADO))
        t.poner("armorBody", _rueda(lienzo, aro, u, v))

    t.textura = lienzo
    t.lado = LADO
    return t, lienzo, avisos


def traje():
    """Lo que espera el generador: el Traje y nada mas."""
    t, _, avisos = arceus()
    for a in avisos:
        print("      . %s" % a)
    return t
