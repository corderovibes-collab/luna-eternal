# -*- coding: utf-8 -*-
"""
DE DONDE SALE CADA COSA, y por que de ahi y no de otro sitio.

Tres fuentes, y las tres se leen -- ninguna se escribe:

  1. EL CASCO DEL USUARIO   arte/charizard-mecha/charizard_helmet_equipped.bbmodel
                            + su PNG. Es SU diseño: 133 cubos en siete grupos,
                            colgados del hueso `head_angle` de un Charizard.
  2. EL CHARIZARD OFICIAL   dentro del jar de Cobblemon 1.8.0 que corre este
                            servidor: geo, resolver, animaciones y texturas.
  3. LAS MEGAS              dentro del jar de mega_showdown: sus tres resolvers,
                            que hay que REPETIR en el nuestro (ver resolver.py).

⚠⚠ LOS DOS JARS SE BAJAN DEL MANIFIESTO PUBLICADO (`gen_tienda.jar_de`), no de
   una carpeta de Descargas ni de `vendor/` -- que es HEAD, no 1.8.0. Es la
   leccion de las seis pantallas en magenta: un generador que dependa de un
   fichero fuera de git no se puede volver a ejecutar.

⚠⚠⚠ EL CUERPO DEL .bbmodel DEL USUARIO ES EL DEL JAR, y se comprueba cubo a
   cubo (comprobar.py) porque de eso depende TODO: el casco se ajusto contra
   ese cuerpo. Si el jar trajera otro Charizard --ha pasado: los modelos de
   Cobblemon cambian entre versiones-- el casco quedaria ajustado a una cabeza
   que ya no existe, sin dar ningun error.

⚠⚠⚠ Y LA MITAD DE ARRIBA DEL PNG DEL USUARIO ES ESCOMBRO: 1.632 pixeles
   sueltos donde el shiny del jar tiene 11.912. No es su textura del cuerpo,
   es lo que quedo de una exportacion. Del PNG solo vale la mitad de abajo
   (el casco, filas 130-184); el cuerpo se toma del jar. Si se hubiera usado
   tal cual, el Charizard habria salido INVISIBLE con un casco flotando.
"""

from __future__ import annotations

import json
import sys
import zipfile
from dataclasses import dataclass
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(RAIZ / "tools"))

ARTE = RAIZ / "arte" / "charizard-mecha"
BBMODEL_USUARIO = ARTE / "charizard_helmet_equipped.bbmodel"
PNG_USUARIO = ARTE / "charizard_helmet_equipped.png"
BUILD = RAIZ / "build" / "mecha"

# El pack de cosmeticos que viaja dentro de lunaneon.jar (ALWAYS_ENABLED).
PACK = (RAIZ / "neon" / "src" / "main" / "resources" / "resourcepacks"
        / "cosmeticos" / "assets" / "cobblemon")

# ⚠⚠ EL ASPECTO ES UNA CADENA QUE VIVE EN DOS SITIOS --el resolver del pack y
#    la constante de Java que lo fuerza al entregar el pase-- y nada obliga a
#    que coincidan. Si divergieran no habria error: habria un Charizard shiny
#    normal saliendo del nivel 100. El autotest cruza la constante contra el
#    resolver del jar.
ASPECTO = "luna_mecha"
NOMBRE = "charizard_luna_mecha"
ORDEN_RESOLVER = 60      # > 3 (gmax) y > 50 (charizard_knight): el ultimo manda

GRUPOS_CASCO = (
    "Tactical_Goggles", "Cranial_Plating", "Snout_Respirator", "Horn_Exhausts",
    "Cheek_Jaw_Armor", "Temple_Sensors", "Neck_Gorget",
)
GRUPO_RAIZ_CASCO = "Charizard_Mecha_Helmet"

DENTRO_DEL_JAR = "assets/cobblemon/bedrock/pokemon/"
TEXTURAS_DEL_JAR = "assets/cobblemon/textures/pokemon/0006_charizard/"


# ------------------------------------------------------------------ los jars

def jars() -> tuple[Path, Path]:
    """(cobblemon, mega_showdown), bajados del manifiesto o de la cache."""
    import gen_tienda  # noqa: E402
    m = gen_tienda._manifiesto()
    return (gen_tienda.jar_de("cobblemon", m),
            gen_tienda.jar_de("mega_showdown", m, prefijo="mega_showdown"))


def leer(jar: Path, ruta: str) -> bytes:
    with zipfile.ZipFile(jar) as z:
        return z.read(ruta)


def geo_oficial(jar: Path) -> dict:
    return json.loads(leer(jar, DENTRO_DEL_JAR + "models/0006_charizard/charizard.geo.json"))


def resolver_oficial(jar: Path) -> dict:
    return json.loads(leer(jar, DENTRO_DEL_JAR + "resolvers/0006_charizard/0_charizard_base.json"))


def animaciones_oficiales(jar: Path) -> dict:
    return json.loads(leer(jar, DENTRO_DEL_JAR
                           + "animations/0006_charizard/charizard.animation.json"))


def resolvers_mega(jar_mega: Path) -> list[dict]:
    """Los resolvers de Charizard de mega_showdown, en el orden en que los aplica Cobblemon."""
    with zipfile.ZipFile(jar_mega) as z:
        rutas = [n for n in z.namelist()
                 if n.startswith(DENTRO_DEL_JAR + "resolvers/0006_charizard/")
                 and n.endswith(".json")]
        sets = [json.loads(z.read(n)) for n in sorted(rutas)]
    return sorted(sets, key=lambda s: s.get("order", 0))


def textura_del_jar(jar: Path, recurso: str) -> bytes:
    """`cobblemon:textures/pokemon/0006_charizard/x.png` -> sus bytes."""
    assert recurso.startswith("cobblemon:"), recurso
    return leer(jar, "assets/cobblemon/" + recurso.split(":", 1)[1])


# --------------------------------------------------------- el .bbmodel del usuario

@dataclass
class Cubo:
    """Un cubo del casco, en el espacio de Blockbench (x SIN espejar)."""
    nombre: str
    grupo: str
    desde: tuple
    hasta: tuple
    uv: tuple       # `uv_offset` del box-uv

    @property
    def tam(self):
        return tuple(self.hasta[i] - self.desde[i] for i in range(3))


def bbmodel_usuario() -> dict:
    return json.loads(BBMODEL_USUARIO.read_text(encoding="utf-8"))


def _recorrer(bb: dict):
    """(camino de grupos, elemento) por cada elemento del outliner."""
    grupos = {g["uuid"]: g for g in bb["groups"]}
    elementos = {e["uuid"]: e for e in bb["elements"]}

    def walk(nodos, camino):
        for n in nodos:
            if isinstance(n, dict):
                g = grupos[n["uuid"]]
                yield from walk(n.get("children", []), camino + (g["name"],))
            else:
                yield camino, elementos[n]
    yield from walk(bb["outliner"], ())


def cubos_del_casco(bb: dict) -> list[Cubo]:
    """
    Los cubos que cuelgan de los siete grupos del casco, tal cual estan.

    ⚠ Se identifican POR SU GRUPO, no por su nombre ni por su posicion: es la
      leccion de los seis adornos de la armadura de Arceus, que se llamaban
      todos `cube` y por posicion iban al hueso equivocado.
    """
    salida = []
    for camino, e in _recorrer(bb):
        if e.get("type") != "cube" or not camino or camino[-1] not in GRUPOS_CASCO:
            continue
        salida.append(Cubo(e["name"], camino[-1], tuple(e["from"]), tuple(e["to"]),
                           tuple(e["uv_offset"])))
    return salida


def cuerpo_del_bbmodel(bb: dict) -> dict:
    """hueso -> [elemento cubo] de todo lo que NO es casco. Para cruzarlo con el jar."""
    salida = {}
    for camino, e in _recorrer(bb):
        if e.get("type") != "cube" or (camino and camino[-1] in GRUPOS_CASCO):
            continue
        salida.setdefault(camino[-1], []).append(e)
    return salida


def grupos_del_bbmodel(bb: dict) -> dict:
    """nombre -> (padre, origen, rotacion) de cada grupo, casco incluido."""
    grupos = {g["uuid"]: g for g in bb["groups"]}
    salida = {}

    def walk(nodos, padre):
        for n in nodos:
            if isinstance(n, dict):
                g = grupos[n["uuid"]]
                salida[g["name"]] = (padre, tuple(g["origin"]),
                                     tuple(g.get("rotation") or (0, 0, 0)))
                walk(n.get("children", []), g["name"])
    walk(bb["outliner"], None)
    return salida


def png_usuario():
    from PIL import Image
    return Image.open(PNG_USUARIO).convert("RGBA")
