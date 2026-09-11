# -*- coding: utf-8 -*-
"""
LAS TEXTURAS: el cuerpo del jar arriba, el casco del usuario abajo -- REPINTADO.

⚠⚠⚠ EL CASCO SE REPINTA AL REPARTO DEL JUEGO, y sin esto el casco saldria con
   la pintura corrida. Blockbench 5 reparte el box-uv con los tamaños
   redondeados hacia arriba; `ModelPart$Cuboid` con los flotantes exactos
   (bedrock.py). Los 133 cubos del casco son fraccionarios, asi que en el
   juego una cara pintada en las columnas 5..13 se muestrearia en 4,4..12: un
   texel corrido, y en la cara de atras hasta 1,8. Sobre placas de 3 y 4
   texels con vetas rojas y remaches de un texel, eso es la pintura fuera de
   sitio en TODAS las piezas.

   La regla del repintado, texel a texel: cada texel pertenece a la cara del
   juego que contiene SU CENTRO, y toma el color de la posicion RELATIVA
   equivalente dentro de la casilla que el usuario pinto en Blockbench. Un
   texel que quede fuera de todas las caras del juego (el margen sobrante de
   redondear) se deja transparente: nadie lo va a muestrear.
   ⚠ Lo que NO puede arreglar: en una frontera fraccionaria (4,4) el texel 4
     es de una sola cara y la vecina le roba 0,4 de texel. Es el precio de
     un modelo fraccionario con box-uv, y es el mismo que pagaria en Bedrock.

⚠⚠ EL CUERPO NO ES DEL PNG DEL USUARIO: su mitad de arriba es escombro
   (fuentes.py). Se pega el shiny del jar --o el normal, para la variante sin
   shiny-- byte a byte. Asi «tiene absolutamente todo lo que tendria un
   Charizard shiny» empieza por tener su textura de verdad.

⚠⚠ LAS LLAMAS SE RELLENAN A 256x256. El geo pasa a declarar 256 de alto y una
   capa se dibuja con las UV del geo: las de 256x128 del jar quedarian
   comprimidas a la mitad de arriba y la llama de la cola saldria en el
   cuerpo. Se rellenan con transparente por abajo -- lo mismo que hace
   `charizard_knight` en este pack (sus llamas ya son 256x256).
"""

from __future__ import annotations

import io
import math

import numpy as np
from PIL import Image

from . import bedrock, fuentes
from .ensamblar import TEXTURA_ALTO

ANCHO = 256


def cuerpo(jar, shiny: bool) -> Image.Image:
    """El Charizard del jar sobre un lienzo de 256x256, sin casco."""
    nombre = "charizard_shiny.png" if shiny else "charizard.png"
    src = Image.open(io.BytesIO(fuentes.leer(jar, fuentes.TEXTURAS_DEL_JAR + nombre))).convert("RGBA")
    lienzo = Image.new("RGBA", (ANCHO, TEXTURA_ALTO), (0, 0, 0, 0))
    lienzo.paste(src, (0, 0))
    return lienzo


def casco_repintado(png_usuario: Image.Image, cubos) -> tuple[Image.Image, dict]:
    """
    Solo el casco, repintado al reparto del juego. Devuelve (imagen, cuentas).

    Las cuentas dicen cuantos texels se movieron de sitio: es el numero que
    justifica que esto exista. Si algun dia el casco se remodela con tamaños
    enteros, saldra cero y el repintado sera la identidad.
    """
    src = np.asarray(png_usuario.convert("RGBA"))
    out = np.zeros_like(src)
    movidos = iguales = 0
    for c in cubos:
        pintado = bedrock.reparto(c.uv[0], c.uv[1], c.tam, redondeado=True)
        juego = bedrock.reparto(c.uv[0], c.uv[1], c.tam, redondeado=False)
        for cara in bedrock.CARAS:
            gu0, gv0, gu1, gv1 = juego[cara]
            pu0, pv0, pu1, pv1 = (int(v) for v in pintado[cara])
            if gu1 - gu0 <= 1e-9 or gv1 - gv0 <= 1e-9 or pu1 <= pu0 or pv1 <= pv0:
                continue
            for y in range(int(math.floor(gv0)), int(math.ceil(gv1))):
                cy = y + 0.5
                if not (gv0 <= cy < gv1):
                    continue
                t = (cy - gv0) / (gv1 - gv0)
                sy = pv0 + min(pv1 - pv0 - 1, int(t * (pv1 - pv0)))
                for x in range(int(math.floor(gu0)), int(math.ceil(gu1))):
                    cx = x + 0.5
                    if not (gu0 <= cx < gu1):
                        continue
                    s = (cx - gu0) / (gu1 - gu0)
                    sx = pu0 + min(pu1 - pu0 - 1, int(s * (pu1 - pu0)))
                    out[y, x] = src[sy, sx]
                    if (sx, sy) == (x, y):
                        iguales += 1
                    else:
                        movidos += 1
    return Image.fromarray(out, "RGBA"), {"movidos": movidos, "en_su_sitio": iguales}


def textura(jar, shiny: bool, png_usuario: Image.Image, cubos) -> tuple[Image.Image, dict]:
    """Cuerpo del jar + casco repintado. Es lo que va al pack."""
    base = cuerpo(jar, shiny)
    casco, cuentas = casco_repintado(png_usuario, cubos)
    base.alpha_composite(casco)
    return base, cuentas


def rellenar(png: bytes) -> Image.Image:
    """Una capa del jar (256x128) sobre 256x256, transparente por abajo."""
    src = Image.open(io.BytesIO(png)).convert("RGBA")
    if src.size == (ANCHO, TEXTURA_ALTO):
        return src
    if src.size != (ANCHO, 128):
        raise ValueError("capa de %dx%d: esperaba 256x128" % src.size)
    lienzo = Image.new("RGBA", (ANCHO, TEXTURA_ALTO), (0, 0, 0, 0))
    lienzo.paste(src, (0, 0))
    return lienzo
