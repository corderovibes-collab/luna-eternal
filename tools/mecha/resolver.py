# -*- coding: utf-8 -*-
"""
EL RESOLVER: que modelo, textura y capas dibuja Cobblemon cuando ve `luna_mecha`.

Como resuelve Cobblemon (VaryingRenderableResolver, leido en el fuente):
  - junta TODOS los ficheros de resolver de la especie, ordenados por `order`
    ascendente, y concatena sus variaciones
  - una variacion ENCAJA si el Pokemon tiene TODOS sus aspectos
  - poser, modelo y textura: gana LA ULTIMA variacion que encaje y lo declare
  - capas: se funden POR NOMBRE, y la ultima que declare ese nombre manda

⚠⚠⚠ Y DE AHI SALE LA TRAMPA DE LAS MEGAS. mega_showdown declara sus formas con
   `order` 1, 2 y 3 (mega_y, mega_x, gmax). El nuestro es 60 porque tiene que
   ganar a `charizard_knight` (50) -- pero entonces, con {luna_mecha, mega_x},
   la variacion `[luna_mecha]` (la nuestra, la ultima) VOLVERIA A PONER EL
   MODELO DEL CASCO encima de la mega: un Mega Charizard X con el cuerpo de
   un Charizard normal y casco. Sin un solo error.
   Por eso este fichero REPITE las variaciones de las megas con `luna_mecha`
   delante, COPIADAS DEL JAR de mega_showdown y no escritas a mano: si su
   autor cambia una textura, regenerar lo recoge. Es el mismo truco que
   `charizard_knight` hace en `msd/100_charizard_knight_mega_x.json`.

⚠⚠ LAS VARIACIONES BASE SE DERIVAN DEL RESOLVER OFICIAL, todas: `[]`,
   `[shiny]` y `[alpha_eyes]` pasan a `[luna_mecha]`, `[luna_mecha, shiny]` y
   `[luna_mecha, alpha_eyes]`, con el modelo nuestro, la textura nuestra y CADA
   capa del jar rellenada a 256x256. Asi el shiny lleva la llama que el jar le
   da al shiny (que hoy es la NORMAL: `charizard_flame1-4`, no las
   `_shiny_flame` que tambien viajan en el jar y que el resolver de 1.8.0 no
   usa) y no una que yo crea que le toca.

⚠⚠ ESTO ATA EL PACK A mega_showdown: Cobblemon carga al arrancar TODOS los
   modelos que nombre cualquier variacion (`initialize` -> `texturedModels[id]!!`),
   asi que sin mega_showdown en el cliente el resolver de Charizard entero
   reventaria al cargar recursos. Hoy viaja en todos los clientes (base
   CobbleVerse, D-037) y el generador lo comprueba: cada modelo que nombra el
   resolver existe en nuestro pack o en el jar de mega_showdown.
"""

from __future__ import annotations

import copy
import posixpath

from . import fuentes

DIR_MODELOS = "bedrock/pokemon/models/luna/"
DIR_RESOLVER = "bedrock/pokemon/resolvers/luna/"
DIR_TEXTURAS = "textures/pokemon/luna/"

MODELO = "cobblemon:" + fuentes.NOMBRE + ".geo"
FICHERO_GEO = DIR_MODELOS + fuentes.NOMBRE + ".geo.json"
FICHERO_RESOLVER = DIR_RESOLVER + "%d_%s.json" % (fuentes.ORDEN_RESOLVER, fuentes.NOMBRE)


def recurso_nuestro(recurso_del_jar: str) -> str:
    """`cobblemon:textures/pokemon/0006_charizard/charizard_flame1.png` -> el nuestro."""
    base = posixpath.basename(recurso_del_jar.split(":", 1)[1])
    if not base.startswith("charizard"):
        raise ValueError("no se de que Pokemon es esta textura: " + recurso_del_jar)
    return "cobblemon:" + DIR_TEXTURAS + base.replace("charizard", fuentes.NOMBRE, 1)


def fichero_de(recurso: str) -> str:
    """`cobblemon:textures/...png` -> ruta dentro de assets/cobblemon/."""
    return recurso.split(":", 1)[1]


def _texturas_de_capa(capa: dict):
    t = capa.get("texture")
    if isinstance(t, str):
        return [t]
    if isinstance(t, dict):
        return list(t.get("frames", []))
    return []


def _remapear_capa(capa: dict, usadas: set) -> dict:
    c = copy.deepcopy(capa)
    t = c.get("texture")
    if isinstance(t, str):
        usadas.add(t)
        c["texture"] = recurso_nuestro(t)
    elif isinstance(t, dict):
        for tx in t.get("frames", []):
            usadas.add(tx)
        t["frames"] = [recurso_nuestro(tx) for tx in t.get("frames", [])]
    return c


def construir(oficial: dict, megas: list[dict]) -> tuple[dict, set]:
    """
    (resolver nuestro, texturas del jar que hay que rellenar y copiar).

    ⚠ El orden dentro del fichero IMPORTA (gana la ultima): primero las
      derivadas del oficial, despues las megas en el orden de sus `order`.
    """
    usadas = set()
    variaciones = []
    for v in oficial["variations"]:
        n = {"aspects": [fuentes.ASPECTO] + list(v.get("aspects", []))}
        if "poser" in v:
            n["poser"] = v["poser"]
        if "model" in v:
            n["model"] = MODELO
        if "texture" in v:
            usadas.add(v["texture"])
            n["texture"] = recurso_nuestro(v["texture"])
        if "layers" in v:
            n["layers"] = [_remapear_capa(c, usadas) for c in v["layers"]]
        variaciones.append(n)
    # ⚠ La primera tiene que fijar poser Y modelo pase lo que pase: es la que
    #   convierte el aspecto en el casco. Si el oficial dejara de declararlos
    #   en su primera variacion, aqui seguirian.
    variaciones[0].setdefault("poser", oficial["variations"][0].get("poser", "cobblemon:charizard"))
    variaciones[0]["model"] = MODELO

    for conjunto in sorted(megas, key=lambda s: s.get("order", 0)):
        for v in conjunto["variations"]:
            n = copy.deepcopy(v)
            n["aspects"] = [fuentes.ASPECTO] + list(v.get("aspects", []))
            variaciones.append(n)

    return ({"species": oficial["species"], "order": fuentes.ORDEN_RESOLVER,
             "variations": variaciones}, usadas)


def modelos_nombrados(resolver: dict) -> set:
    return {v["model"] for v in resolver["variations"] if "model" in v}


def texturas_nombradas(resolver: dict) -> set:
    salida = set()
    for v in resolver["variations"]:
        if "texture" in v:
            salida.add(v["texture"])
        for c in v.get("layers", []):
            salida.update(_texturas_de_capa(c))
    return salida
