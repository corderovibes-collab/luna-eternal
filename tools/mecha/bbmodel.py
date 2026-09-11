# -*- coding: utf-8 -*-
"""
UN .bbmodel QUE ENSEÑA LO QUE VA A DIBUJAR EL JUEGO.

⚠⚠⚠ NO ES EL FICHERO DEL USUARIO NI LO SUSTITUYE: es una VISTA. El suyo sigue
   siendo la fuente (arte/charizard-mecha/charizard_helmet_equipped.bbmodel);
   si quiere cambiar el casco, cambia el suyo y se regenera. Este existe
   porque abrir el suyo enseña el box-uv REDONDEADO de Blockbench, y el juego
   reparte con los tamaños flotantes (bedrock.py). Aqui cada cubo del casco
   lleva UV POR CARA con las casillas EXACTAS que muestrea `ModelPart$Cuboid`,
   sobre la textura ya repintada: lo que se ve aqui es lo que se ve en el
   juego, texel a texel. Es el equivalente del .bbmodel «que se abre ya
   animado» de la espada.

⚠⚠ EL CUERPO VA CON BOX-UV, como en el original: tiene tamaños enteros y ahi
   las dos reglas coinciden. Los huesos, pivotes, rotaciones y localizadores
   salen del geo final por la regla inversa a la de exportacion (espejo en X,
   rotaciones X e Y negadas), o sea que se ve donde Blockbench lo pondria.

⚠ Las casillas de `up` y `down` se escriben INVERTIDAS como las escribe el
  propio Blockbench (`up` con u y v al reves, `down` con u al reves): son el
  mismo convenio que sus caras de box-uv. Copiado de su fichero, no deducido.
"""

from __future__ import annotations

import base64
import io
import json
import uuid as _uuid
from pathlib import Path

from . import bedrock, ensamblar, fuentes

# ⚠ La casilla de -x del geo cae en la cara ESTE de Blockbench, porque Blockbench
#   enseña el geo espejado en X. Comprobado contra el fichero del usuario: su
#   `torso` lleva la PRIMERA casilla (u..u+d) en `east`.
CARA_BB = {"-x": "east", "-z": "north", "+x": "west", "+z": "south", "+y": "up", "-y": "down"}


def _uuid5(*partes) -> str:
    return str(_uuid.uuid5(_uuid.NAMESPACE_URL, "lunaeternal:mecha:" + "/".join(str(p) for p in partes)))


def _r(v):
    return [round(float(x), 4) + 0.0 for x in v]


def _caras(u, v, tam, redondeado):
    rects = bedrock.reparto(u, v, tam, redondeado=redondeado)
    caras = {}
    for cara, (u0, v0, u1, v1) in rects.items():
        nombre = CARA_BB[cara]
        if nombre == "up":
            uv = [u1, v1, u0, v0]
        elif nombre == "down":
            uv = [u1, v0, u0, v1]
        else:
            uv = [u0, v0, u1, v1]
        caras[nombre] = {"uv": _r(uv), "texture": 0}
    return caras


def _elemento_cubo(hueso, i, cubo, nuestro):
    desde, hasta = bedrock.a_blockbench(cubo["origin"], cubo["size"])
    # Los cubos del cuerpo se llaman como su hueso, que es como los llama
    # Blockbench (y el fichero del usuario); los del casco, como los bautizo el.
    e = {"name": cubo.get("name") or hueso, "box_uv": not nuestro,
         "render_order": "default", "locked": False, "export": True, "scope": 0,
         "allow_mirror_modeling": True, "from": _r(desde), "to": _r(hasta), "autouv": 0,
         "color": 6, "type": "cube", "uuid": _uuid5("cubo", hueso, i)}
    inflate = cubo.get("inflate") or 0.0
    if inflate:
        e["inflate"] = round(float(inflate), 4)
    if cubo.get("mirror"):
        e["mirror_uv"] = True
    if cubo.get("rotation"):
        e["rotation"] = _r(bedrock.rot_a_geo(cubo["rotation"]))
        e["origin"] = _r(bedrock.pivote_a_geo(cubo.get("pivot") or (0, 0, 0)))
    else:
        e["origin"] = [0.0, 0.0, 0.0]
    u, v = cubo["uv"]
    if not nuestro:
        e["uv_offset"] = [int(u), int(v)]
    # ⚠ El casco con el reparto DEL JUEGO; el cuerpo con el de Blockbench (que
    #   con enteros es el mismo). Las dos reglas viven en bedrock.reparto.
    e["faces"] = _caras(u, v, cubo["size"], redondeado=not nuestro)
    return e


def _elemento_locator(hueso, nombre, dato):
    if isinstance(dato, dict):
        pos, rot = dato.get("offset", [0, 0, 0]), dato.get("rotation", [0, 0, 0])
    else:
        pos, rot = dato, [0, 0, 0]
    return {"name": nombre, "position": _r(bedrock.pivote_a_geo(pos)),
            "rotation": _r(bedrock.rot_a_geo(rot)), "ignore_inherited_scale": False,
            "visibility": True, "locked": False, "scope": 0,
            "uuid": _uuid5("locator", hueso, nombre), "type": "locator"}


def construir(geo: dict, textura_png) -> dict:
    """El diccionario del .bbmodel. Los cubos del casco ya traen su nombre en el geo."""
    g = geo["minecraft:geometry"][0]
    nuestros = set(ensamblar.PADRE)
    elementos, grupos, hijos = [], {}, {}
    for b in g["bones"]:
        uid = _uuid5("hueso", b["name"])
        grupos[b["name"]] = {
            "name": b["name"], "uuid": uid, "export": True, "locked": False, "scope": 0,
            "selected": False, "_static": {"properties": {}, "temp_data": {}},
            "origin": _r(bedrock.pivote_a_geo(b.get("pivot") or (0, 0, 0))),
            "rotation": _r(bedrock.rot_a_geo(b.get("rotation") or (0, 0, 0))),
            "bedrock_binding": "", "color": 0, "children": [], "reset": False,
            "shade": True, "mirror_uv": False, "visibility": True, "autouv": 0,
            "isOpen": b["name"] in nuestros, "primary_selected": False}
        hijos.setdefault(b.get("parent"), []).append(b["name"])
        for i, c in enumerate(b.get("cubes", [])):
            elementos.append((b["name"], _elemento_cubo(b["name"], i, c, b["name"] in nuestros)))
        for nombre, dato in (b.get("locators") or {}).items():
            elementos.append((b["name"], _elemento_locator(b["name"], nombre, dato)))

    por_hueso = {}
    for hueso, e in elementos:
        por_hueso.setdefault(hueso, []).append(e["uuid"])

    def nodo(nombre):
        return {"uuid": grupos[nombre]["uuid"], "isOpen": grupos[nombre]["isOpen"],
                "children": [nodo(h) for h in hijos.get(nombre, [])] + por_hueso.get(nombre, [])}

    raices = hijos.get(None, [])
    buf = io.BytesIO()
    textura_png.save(buf, format="PNG")
    b64 = base64.b64encode(buf.getvalue()).decode("ascii")
    nombre_png = fuentes.NOMBRE + "_shiny.png"
    w, h = textura_png.size
    return {
        "meta": {"format_version": "5.0", "model_format": "bedrock", "box_uv": True},
        "name": fuentes.NOMBRE, "model_identifier": fuentes.NOMBRE,
        "visible_box": [g["description"].get("visible_bounds_width", 10),
                        g["description"].get("visible_bounds_height", 5),
                        (g["description"].get("visible_bounds_offset") or [0, 1.5, 0])[1]],
        "variable_placeholders": "", "multi_file_ruleset": "", "variable_placeholder_buttons": [],
        "bedrock_animation_mode": "entity", "timeline_setups": [], "unhandled_root_fields": {},
        "resolution": {"width": w, "height": h},
        "elements": [e for _, e in elementos],
        "groups": list(grupos.values()),
        "outliner": [nodo(r) for r in raices],
        "textures": [{
            "name": nombre_png, "relative_path": nombre_png, "folder": "", "namespace": "",
            "id": "0", "group": "", "scope": 0,
            # ⚠ width/height = lo que mide; uv_width/uv_height = el espacio de las
            #   UV. Aqui son iguales porque no hay animacion (la leccion de la
            #   espada, donde se confunden y reparten las UV entre fotogramas).
            "width": w, "height": h, "uv_width": w, "uv_height": h,
            "particle": False, "use_as_default": False, "layers_enabled": False,
            "sync_to_project": "", "file_format": "png", "render_mode": "default",
            "render_sides": "auto", "wrap_mode": "limited", "pbr_channel": "color",
            "fps": 7, "frame_time": 1, "frame_order_type": "loop", "frame_order": "",
            "frame_interpolate": False, "visible": True, "internal": True, "saved": True,
            "uuid": _uuid5("textura", nombre_png),
            "source": "data:image/png;base64," + b64,
        }],
    }


def escribir(geo, textura_png, destino: Path):
    datos = construir(geo, textura_png)
    destino.parent.mkdir(parents=True, exist_ok=True)
    destino.write_text(json.dumps(datos, ensure_ascii=False), encoding="utf-8")
    return destino


def verificar(ruta: Path, geo: dict) -> list[str]:
    """
    Relee el .bbmodel de disco y lo cruza con el geo.

    ⚠ Compara contra el FICHERO, no contra el diccionario en memoria. Los
      cubos del casco se casan POR NOMBRE (el que les puso el usuario, y que
      el geo conserva), asi que un nombre repetido tambien se ve aqui.
    """
    fallos = []
    try:
        d = json.loads(ruta.read_text(encoding="utf-8"))
    except Exception as ex:  # noqa: BLE001
        return ["el .bbmodel no se puede leer: %s" % ex]
    g = geo["minecraft:geometry"][0]
    grupos = {x["name"]: x for x in d.get("groups", [])}
    for b in g["bones"]:
        if b["name"] not in grupos:
            fallos.append("falta el grupo " + b["name"])
    cubos_geo = sum(len(b.get("cubes", [])) for b in g["bones"])
    cubos_bb = [e for e in d.get("elements", []) if e["type"] == "cube"]
    if cubos_geo != len(cubos_bb):
        fallos.append("el .bbmodel tiene %d cubos y el geo %d" % (len(cubos_bb), cubos_geo))

    nuestros = {}
    for b in ensamblar.huesos_nuestros(geo):
        for c in b["cubes"]:
            if c["name"] in nuestros:
                fallos.append("dos cubos del casco se llaman igual: " + c["name"])
            nuestros[c["name"]] = c
    vistos = 0
    for e in cubos_bb:
        c = nuestros.get(e["name"])
        if c is None:
            continue
        vistos += 1
        if e.get("box_uv"):
            fallos.append("cubo del casco con box_uv en el .bbmodel de salida: " + e["name"])
            continue
        desde, hasta = bedrock.a_blockbench(c["origin"], c["size"])
        if _r(desde) != _r(e["from"]) or _r(hasta) != _r(e["to"]):
            fallos.append("%s no esta donde dice el geo" % e["name"])
        esperado = _caras(c["uv"][0], c["uv"][1], c["size"], redondeado=False)
        for cara, datos in esperado.items():
            if [round(x, 3) for x in e["faces"][cara]["uv"]] != [round(x, 3) for x in datos["uv"]]:
                fallos.append("la UV de %s/%s no es la del juego" % (e["name"], cara))
                break
    if vistos != len(nuestros):
        fallos.append("en el .bbmodel hay %d cubos del casco y en el geo %d" % (vistos, len(nuestros)))
    tex = d.get("textures", [])
    if not tex or not tex[0].get("source", "").startswith("data:image/png;base64,"):
        fallos.append("el .bbmodel no lleva la textura incrustada")
    elif tex[0]["uv_width"] != tex[0]["width"] or tex[0]["uv_height"] != tex[0]["height"]:
        fallos.append("uv_width/uv_height no son los de la imagen")
    return fallos
