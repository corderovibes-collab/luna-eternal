# -*- coding: utf-8 -*-
"""
UNA POKE BALL EN 3D, PARA PEGARLA A UN TRAJE EN BLOCKBENCH.

    python tools/gen_pokeball.py

Deja tres ficheros en `build/pokeball/`:

    pokeball.bbmodel    <- ESTE. Se abre con File -> Open y ya viene con textura
    pokeball.geo.json   <- por si el .bbmodel diera problemas
    pokeball.png        <- la textura, para el .geo.json

⚠⚠⚠ UNA ESFERA DE CUBOS SE HACE POR CAPAS, NO CON UN CUBO REDONDEADO.
   No existe «esfera» en Minecraft: lo que se ve redondo son discos apilados de
   anchura decreciente. El ancho de cada capa NO se elige a ojo, sale de
   Pitágoras -- r = raiz(R^2 - altura^2) -- y por eso queda simétrica arriba y
   abajo. Escrita a mano, la bola sale con bultos.

⚠⚠ Y LA BANDA NEGRA ES MAS ANCHA QUE LA BOLA, a propósito (6,0 sobre 5,9). Si
   estuviera al ras, desde lejos las dos mitades parecerían tocarse y la Poke
   Ball se leería como «una pelota roja y blanca», no como una Poke Ball. El
   ecuador es lo que la identifica.
"""

from __future__ import annotations

import base64
import json
import math
import sys
import uuid
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(RAIZ / "tools"))

from trajes.modelo import Cubo, empaquetar, pintar, _caras   # noqa: E402

SALIDA = RAIZ / "build" / "pokeball"

ROJO = (216, 62, 54)
BLANCO = (238, 240, 246)
NEGRO = (32, 33, 40)

LADO = 128       # la textura
RADIO = 3.0      # de la bola


def bola(cx, cy, cz):
    """
    Las nueve piezas de la Poke Ball, centradas en (cx, cy, cz).

    Siete capas apiladas + el botón del frente con su aro.
    """
    cubos = []

    # ⚠ Cada capa lleva su ALTURA y su COLOR; el ANCHO se calcula. Los tramos no
    #   son todos iguales: el del ecuador es fino porque ahí va la banda.
    # ⚠⚠ CAPAS DE MEDIO BLOQUE, NO DE UNO ENTERO. Con capas de 1 la bola solo
    #    se estrecha en los extremos y el resto es un CILINDRO: es lo que salio
    #    la primera vez. A medio bloque hay trece escalones y ya se lee redonda.
    tramos = []
    paso = 0.5
    y = -RADIO
    while y < -0.25 - 1e-6:
        tramos.append((y, min(y + paso, -0.25), BLANCO))
        y += paso
    tramos.append((-0.25, 0.25, NEGRO))     # la banda
    y = 0.25
    while y < RADIO - 1e-6:
        tramos.append((y, min(y + paso, RADIO), ROJO))
        y += paso
    for y0, y1, color in tramos:
        # El radio de la capa se mide en su borde MAS ANCHO -- el más cercano al
        # ecuador -- o la bola queda mordida por dentro.
        borde = min(abs(y0), abs(y1))
        r = math.sqrt(max(0.01, RADIO * RADIO - borde * borde))
        if color is NEGRO:
            r = RADIO + 0.05    # ver el aviso de la cabecera
        cubos.append(Cubo(
            (round(cx - r, 3), round(cy + y0, 3), round(cz - r, 3)),
            (round(r * 2, 3), round(y1 - y0, 3), round(r * 2, 3)),
            color, "metal" if color is NEGRO else "tela"))

    # El botón, hacia -Z, que es el frente. Dos cubos: aro y centro.
    cubos.append(Cubo((cx - 1.1, cy - 1.1, cz - RADIO - 0.35),
                      (2.2, 2.2, 0.4), NEGRO, "metal"))
    cubos.append(Cubo((cx - 0.7, cy - 0.7, cz - RADIO - 0.6),
                      (1.4, 1.4, 0.35), BLANCO, "tela"))
    return cubos


# ---------------------------------------------------------------- .bbmodel

# Como se llama cada cara en Blockbench. Es el mismo reparto que usa el pintor
# de `trajes/modelo.py`, asi que lo que se ve en Blockbench es lo que se ve en
# el juego.
#
# ⚠ `derecha` es la DERECHA DEL MODELO, que mirandolo de frente cae a tu
#   izquierda -- por eso es `west` (-X) y no `east`.
NOMBRE_CARA = {
    "derecha": "west", "frente": "north", "izquierda": "east",
    "espalda": "south", "arriba": "up", "abajo": "down",
}


def caras_de(c):
    """Las seis caras con su rectangulo real en la textura."""
    salida = {}
    for ox, oy, aw, ah, cara in _caras(c):
        salida[NOMBRE_CARA[cara]] = {
            "uv": [ox, oy, ox + aw, oy + ah],
            "texture": 0,
        }
    return salida


def bbmodel(cubos, png_bytes):
    """
    El proyecto de Blockbench, con la textura dentro.

    <p>⚠ `box_uv: true` es lo que hace que cada cubo lleve solo su esquina
    (`uv_offset`) en vez de las seis caras a mano. Es el mismo reparto que usa
    Minecraft, así que lo que se ve en Blockbench es lo que se ve en el juego.
    """
    tex_id = str(uuid.uuid4())
    elementos, hijos = [], []
    for i, c in enumerate(cubos):
        eid = str(uuid.uuid4())
        hijos.append(eid)
        elementos.append({
            "name": "capa_%d" % (i + 1),
            "box_uv": True,
            "rescale": False,
            "locked": False,
            "render_order": "default",
            "allow_mirror_modeling": True,
            "from": [c.origen[0], c.origen[1], c.origen[2]],
            "to": [round(c.origen[j] + c.tam[j], 4) for j in range(3)],
            "autouv": 0,
            "color": i % 8,
            "origin": [0, 0, 0],
            "uv_offset": [c.uv[0], c.uv[1]],
            # ⚠⚠⚠ LAS SEIS CARAS VAN CON SU UV DE VERDAD, Y AQUI ESTUVO EL
            #    FALLO: las escribi como [0,0,0,0] pensando que con `box_uv` no
            #    se miraban. Blockbench SI las mira, y un rectangulo de tamaño
            #    cero es una cara VACIA -- se veian las capas sueltas, como
            #    platos flotando, porque las tapas no se dibujaban.
            "faces": caras_de(c),
            "uuid": eid,
        })
    return {
        "meta": {
            "format_version": "4.10",
            "model_format": "bedrock",
            "box_uv": True,
        },
        "name": "pokeball",
        "model_identifier": "",
        "visible_box": [2, 2, 0],
        "variable_placeholders": "",
        "variable_placeholder_buttons": [],
        "unhandled_root_fields": {},
        "resolution": {"width": LADO, "height": LADO},
        "elements": elementos,
        "outliner": [{
            "name": "pokeball",
            "origin": [0, 0, 0],
            "color": 0,
            "uuid": str(uuid.uuid4()),
            "export": True,
            "mirror_uv": False,
            "isOpen": True,
            "locked": False,
            "visibility": True,
            "children": hijos,
        }],
        "textures": [{
            "path": "",
            "name": "pokeball.png",
            "folder": "",
            "namespace": "",
            "id": "0",
            "group": "",
            "width": LADO,
            "height": LADO,
            "uv_width": LADO,
            "uv_height": LADO,
            "particle": False,
            "use_as_default": False,
            "layers_enabled": False,
            "sync_to_project": "",
            "render_mode": "normal",
            "render_sides": "auto",
            "frame_time": 1,
            "frame_order_type": "loop",
            "frame_order": "",
            "frame_interpolate": False,
            "visible": True,
            "internal": True,
            "saved": False,
            "uuid": tex_id,
            "relative_path": "",
            "source": "data:image/png;base64,"
                      + base64.b64encode(png_bytes).decode("ascii"),
        }],
    }


# ---------------------------------------------------------------- .geo.json

def geo(cubos):
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.pokeball",
                "texture_width": LADO, "texture_height": LADO,
                "visible_bounds_width": 2, "visible_bounds_height": 2,
                "visible_bounds_offset": [0, 1, 0],
            },
            "bones": [{
                "name": "pokeball",
                "pivot": [0, 3, 0],
                "cubes": [{
                    "origin": list(c.origen),
                    "size": list(c.tam),
                    "uv": list(c.uv),
                } for c in cubos],
            }],
        }],
    }


def main():
    SALIDA.mkdir(parents=True, exist_ok=True)

    # ⚠ Centrada en (0, 3, 0): así se apoya en la rejilla del suelo de
    #   Blockbench en vez de salir medio enterrada.
    cubos = bola(0, 3, 0)
    alto = empaquetar(cubos, LADO)
    png = SALIDA / "pokeball.png"
    pintar(cubos, LADO, semilla=7).save(png)

    (SALIDA / "pokeball.geo.json").write_text(
        json.dumps(geo(cubos), indent=2), encoding="utf-8")
    (SALIDA / "pokeball.bbmodel").write_text(
        json.dumps(bbmodel(cubos, png.read_bytes())), encoding="utf-8")

    print("  Poke Ball: %d piezas · %d x %d px de textura (usa %d)"
          % (len(cubos), LADO, LADO, alto))
    print("  mide %.1f de ancho y %.1f de alto" % (RADIO * 2, RADIO * 2))
    print("  -> %s" % SALIDA)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
