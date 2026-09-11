# -*- coding: utf-8 -*-
"""
EL GEO FINAL: el Charizard oficial, tal cual, mas cuatro huesos con el casco.

⚠⚠⚠ EL CUERPO SE COPIA DEL JAR Y NO SE TOCA NI UN CUBO. Los 130 huesos, los
   123 cubos, los 26 localizadores, las rotaciones y los pivotes salen del
   `charizard.geo.json` que corre en este servidor. El poser oficial
   (`cobblemon:charizard`) y sus 34 animaciones se aplican POR NOMBRE DE
   HUESO, asi que un hueso nuevo que cuelgue de uno oficial hereda su
   movimiento entero sin escribir una animacion. Es lo mismo que hace
   `charizard_knight`, que lleva meses funcionando en este pack.

⚠⚠⚠ CADA PIEZA VA AL HUESO QUE SE MUEVE CON ELLA, Y ESO ES LO QUE PIDIO EL
   USUARIO («que cuando haga una animacion no se salga... si abre la boca eso
   tambien»). El .bbmodel lo trae TODO colgado de `head_angle`, y con eso la
   barbilla se quedaria clavada mientras la mandibula se abre 85,8 grados
   por debajo. Se reparte mirando QUE CUBO OFICIAL ENVUELVE CADA PIEZA:

     luna_casco   <- head_angle   gafas, placa craneal, escapes de los cuernos,
                                  sensores, mejillas y placa de la garganta
     luna_hocico  <- muzzle       el respirador del hocico (mandibula superior)
     luna_menton  <- jaw2         las siete piezas Chin_* (mandibula inferior)
     luna_gola    <- neck4        la gola del cuello

   ⚠ Los cuernos oficiales tienen rotacion estatica y CERO animaciones (medido
     en las 34), asi que sus escapes van rectos en `head_angle`, que es donde
     el usuario los vio en Blockbench.

⚠⚠⚠ EL MENTON CUELGA DE `jaw2` CON LA ROTACION INVERSA A LA ESTATICA DE
   `jaw2` (-7,5 en el jar -> +7,5 aqui). El usuario modelo la barbilla RECTA
   alrededor de la mandibula YA GIRADA que le enseñaba Blockbench. Si se colgara
   sin mas, el juego le sumaria esos 7,5 grados y la barbilla saldria hundida
   en el labio; y si se colgara de `jaw` (que no gira), se quedaria atras en
   las ocho animaciones donde `jaw2` añade hasta 7 grados propios. Con la
   inversa, en reposo cae EXACTAMENTE donde la puso y en movimiento suma solo
   lo que anime `jaw2`. Vale porque `jaw2` gira en UN solo eje; se comprueba.

⚠⚠ Y LA TEXTURA PASA A 256x256: el casco necesita sitio y el cuerpo oficial
   ocupa las 128 filas de arriba de 256 de ancho. Las UV del cuerpo son
   absolutas, asi que no se mueven. Lo que si obliga es a RELLENAR las capas
   de llama del jar a 256x256 (texturas.py): una capa mide lo que declara el
   geo, y una de 256x128 sobre un geo de 256 se dibujaria encogida a la mitad.
"""

from __future__ import annotations

import copy

from . import bedrock, fuentes

# (grupo del .bbmodel, prefijo del nombre del cubo o None) -> hueso nuestro
DESTINO = {
    ("Tactical_Goggles", None): "luna_casco",
    ("Cranial_Plating", None): "luna_casco",
    ("Horn_Exhausts", None): "luna_casco",
    ("Temple_Sensors", None): "luna_casco",
    ("Snout_Respirator", None): "luna_hocico",
    ("Neck_Gorget", None): "luna_gola",
    ("Cheek_Jaw_Armor", "Chin_"): "luna_menton",
    ("Cheek_Jaw_Armor", "Cheek_"): "luna_casco",
    ("Cheek_Jaw_Armor", "Throat_"): "luna_casco",
}

# hueso nuestro -> hueso oficial del que cuelga
PADRE = {
    "luna_casco": "head_angle",
    "luna_hocico": "muzzle",
    "luna_menton": "jaw2",
    "luna_gola": "neck4",
}

TEXTURA_ALTO = 256


def hueso_de(cubo: fuentes.Cubo) -> str:
    """A que hueso nuestro va un cubo del casco. Lanza si no esta en la tabla."""
    if (cubo.grupo, None) in DESTINO:
        return DESTINO[(cubo.grupo, None)]
    for (grupo, prefijo), hueso in DESTINO.items():
        if grupo == cubo.grupo and prefijo and cubo.nombre.startswith(prefijo):
            return hueso
    raise ValueError("cubo sin hueso asignado: %s/%s" % (cubo.grupo, cubo.nombre))


def _r4(v):
    return [round(float(x), 4) + 0.0 for x in v]


def cubo_geo(cubo: fuentes.Cubo) -> dict:
    """
    Un cubo del casco en formato geo.

    ⚠ Lleva `name`, que Bedrock no usa: Cobblemon lo lee con Gson y un campo
      que su clase `Cube` no tiene SE IGNORA. Se deja porque hace legible el
      fichero (que pieza es cada cubo) y porque el .bbmodel de salida y su
      verificacion casan los cubos por ese nombre.
    """
    origin, size = bedrock.a_geo(cubo.desde, cubo.hasta)
    return {"name": cubo.nombre, "origin": _r4(origin), "size": _r4(size),
            "uv": [int(cubo.uv[0]), int(cubo.uv[1])]}


def ensamblar(geo_oficial: dict, cubos_casco: list[fuentes.Cubo]) -> dict:
    """El .geo.json entero. No muta el oficial."""
    geo = copy.deepcopy(geo_oficial)
    g = geo["minecraft:geometry"][0]
    g["description"]["identifier"] = "geometry." + fuentes.NOMBRE
    g["description"]["texture_height"] = TEXTURA_ALTO
    oficiales = {b["name"]: b for b in g["bones"]}

    por_hueso = {h: [] for h in PADRE}
    for c in cubos_casco:
        por_hueso[hueso_de(c)].append(cubo_geo(c))

    for nuestro, padre in PADRE.items():
        p = oficiales[padre]
        hueso = {"name": nuestro, "parent": padre,
                 "pivot": list(p.get("pivot") or [0, 0, 0])}
        estatica = p.get("rotation")
        if estatica and any(abs(v) > 1e-9 for v in estatica):
            hueso["rotation"] = [-v + 0.0 for v in estatica]
        hueso["cubes"] = por_hueso[nuestro]
        g["bones"].append(hueso)
    return geo


def huesos_nuestros(geo: dict) -> list[dict]:
    return [b for b in geo["minecraft:geometry"][0]["bones"] if b["name"] in PADRE]
