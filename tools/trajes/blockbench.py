# -*- coding: utf-8 -*-
"""
EXPORTA UN TRAJE A BLOCKBENCH (.bbmodel), con la textura dentro.

Sirve para el paso C del flujo: yo pongo la base y el usuario le da el último
toque con el ojo. Ver `docs/ui/trajes-flujo.md`.

⚠⚠⚠ SE EXPORTA UN FICHERO POR PIEZA, NO UNO CON TODO. Casco, peto, perneras y
   botas son cuatro objetos distintos en el juego y **cada uno tiene que
   sostenerse solo**: un hueso que cuelgue de otro fichero tumba la carga de
   recursos entera del cliente. Dando cuatro proyectos separados, lo que se
   edita es exactamente lo que se publica.

⚠⚠ Y LAS SEIS CARAS VAN CON SU UV DE VERDAD. Escribirlas como [0,0,0,0]
   pensando que `box_uv` las recalcula es un error que ya se pagó: Blockbench
   las lee, un rectángulo de tamaño cero es una cara VACÍA, y el modelo se ve
   como láminas sueltas flotando.
"""

from __future__ import annotations

import base64
import io
import json
import uuid
from pathlib import Path

from . import modelo as M

# Cómo se llama cada cara en Blockbench.
#
# ⚠ `derecha` es la DERECHA DEL MODELO, que mirándolo de frente cae a tu
#   izquierda — por eso es `west` (-X) y no `east`.
NOMBRE_CARA = {
    "derecha": "west", "frente": "north", "izquierda": "east",
    "espalda": "south", "arriba": "up", "abajo": "down",
}


def _caras(c):
    return {NOMBRE_CARA[cara]: {"uv": [ox, oy, ox + aw, oy + ah], "texture": 0}
            for ox, oy, aw, ah, cara in M._caras(c)}


def _grupo(nombre, pivote, hijos):
    return {
        "name": nombre,
        "origin": [round(v, 4) for v in pivote],
        "color": 0,
        "uuid": str(uuid.uuid4()),
        "export": True,
        "mirror_uv": False,
        "isOpen": True,
        "locked": False,
        "visibility": True,
        "children": hijos,
    }


def escribir(traje, pieza, destino, lado=128):
    """
    Escribe `<traje>_<pieza>.bbmodel`. Devuelve la ruta, o None si está vacía.

    ⚠ Empaqueta y pinta con el MISMO código que genera el `.geo.json` del juego,
      así que lo que se ve en Blockbench es lo que se ve dentro.
    """
    huesos = traje.de_pieza(pieza)
    if not huesos:
        return None
    cubos = [c for lista in huesos.values() for c in lista]
    M.empaquetar(cubos, lado)
    img = M.pintar(cubos, lado, sum(ord(ch) for ch in traje.id + pieza))
    buf = io.BytesIO()
    img.save(buf, format="PNG")

    elementos, arbol, anclas = [], [], {}
    for hueso, lista in huesos.items():
        hijos = []
        for i, c in enumerate(lista):
            eid = str(uuid.uuid4())
            hijos.append(eid)
            elementos.append({
                "name": "%s_%d" % (hueso, i + 1),
                "box_uv": True,
                "rescale": False,
                "locked": False,
                "render_order": "default",
                "allow_mirror_modeling": True,
                "from": [round(v, 4) for v in c.origen],
                "to": [round(c.origen[j] + c.tam[j], 4) for j in range(3)],
                "autouv": 0,
                "color": len(elementos) % 8,
                "origin": [round(v, 4) for v in M.pivote_de(hueso)],
                "uv_offset": [c.uv[0], c.uv[1]],
                "faces": _caras(c),
                "uuid": eid,
            })
        interior = _grupo(hueso, M.pivote_de(hueso), hijos)
        # El ancla `biped*` va VACÍA y envuelve al hueso de armadura: es la
        # estructura que espera GeckoLib, y la que trae su plantilla «Armor».
        ancla = M.PADRES[hueso]
        anclas.setdefault(ancla, []).append(interior)
    for ancla, dentro in anclas.items():
        arbol.append(_grupo(ancla, M.PIVOTES[ancla], dentro))

    datos = {
        "meta": {"format_version": "4.10", "model_format": "bedrock",
                 "box_uv": True},
        "name": "%s_%s" % (traje.id, pieza),
        "model_identifier": "",
        "visible_box": [3, 3.5, 1.25],
        "variable_placeholders": "",
        "variable_placeholder_buttons": [],
        "unhandled_root_fields": {},
        "resolution": {"width": lado, "height": lado},
        "elements": elementos,
        "outliner": arbol,
        "textures": [{
            "path": "", "name": "%s_%s.png" % (traje.id, pieza),
            "folder": "", "namespace": "", "id": "0", "group": "",
            "width": lado, "height": lado, "uv_width": lado, "uv_height": lado,
            "particle": False, "use_as_default": False, "layers_enabled": False,
            "sync_to_project": "", "render_mode": "normal",
            "render_sides": "auto", "frame_time": 1,
            "frame_order_type": "loop", "frame_order": "",
            "frame_interpolate": False, "visible": True, "internal": True,
            "saved": False, "uuid": str(uuid.uuid4()), "relative_path": "",
            "source": "data:image/png;base64,"
                      + base64.b64encode(buf.getvalue()).decode("ascii"),
        }],
    }
    ruta = Path(destino) / ("%s_%s.bbmodel" % (traje.id, pieza))
    ruta.parent.mkdir(parents=True, exist_ok=True)
    ruta.write_text(json.dumps(datos), encoding="utf-8")
    img.save(ruta.with_suffix(".png"))
    return ruta


def escribir_todo(traje, destino, lado=128):
    """Las cuatro piezas. Devuelve las rutas que existen."""
    return [r for r in (escribir(traje, p, destino, lado) for p in M.PIEZAS)
            if r]
