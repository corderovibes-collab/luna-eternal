# -*- coding: utf-8 -*-
"""
LAS COMPROBACIONES. Con un solo fallo no se exporta.

Cada una existe por un fallo que NO daria ningun error en el juego:

  1. CUERPO == JAR        el casco se ajusto contra ESTE Charizard; con otro
                          quedaria ajustado a una cabeza que no existe
  2. 133 CUBOS, CON HUESO un cubo que no case con la tabla de destino se
                          perderia en silencio, y uno con rotacion o inflate
                          saldria recto y sin ellos
  3. UV DENTRO Y SIN PISAR ninguna casilla fuera de 256x256, ninguna del casco
                          sobre la mitad del cuerpo, ninguna sobre otra
  4. TODAS LAS CARAS PINTADAS un texel transparente dentro de una cara del juego
                          es un agujero en el casco
  5. NADA FLOTA           cada RACIMO de piezas conectadas toca (a 0,5) algun
                          cubo oficial de la cabeza en reposo; uno que no,
                          esta en el aire (los escapes son una cadena que
                          acaba a 2,0 de la cabeza, soldada eslabon a eslabon)
  6. EL MENTON GIRA EN UN EJE la rotacion inversa solo es inversa de verdad
                          con un eje; y los cuernos SIN animar, o los escapes
                          tendrian que ir en su hueso
  7. RESOLVER COMPLETO    las megas repetidas son IGUALES a las de mega_showdown,
                          cada modelo y textura que nombra existe, y el poser
  8. EL ASPECTO ESTA EN JAVA la cadena `luna_mecha` tiene que estar escrita
                          igual en Recompensa.java, o el nivel 100 entregaria
                          un shiny sin casco
  9. EL GEO ES EL OFICIAL MENOS LOS CUERNOS MAS EL CASCO  cada hueso oficial
                          sigue con su pivote, rotacion, padre y localizadores;
                          faltan EXACTAMENTE los cubos de `QUITAR` y ninguno mas

⚠ Ninguna compara un objeto consigo mismo: las que miran el pack (`escrito`)
  releen los ficheros de disco.
"""

from __future__ import annotations

import json
import math
import re
import zipfile

import numpy as np

from . import bedrock, ensamblar, fuentes, resolver

HUESOS_CABEZA = ("head_angle", "muzzle", "nose", "jaw", "jaw2", "neck3", "neck4",
                 "horn_right", "horn_left", "brow", "eye_right", "eye_left",
                 "eyelid_right", "eyelid_left", "tongue", "tongue2", "tongue3")
DISTANCIA_MAX = 0.5

COLORES_HUESOS = {"luna_casco": (90, 160, 255), "luna_hocico": (255, 200, 60),
                  "luna_menton": (255, 80, 80), "luna_gola": (90, 230, 120)}


def _r4(v):
    return tuple(round(float(x), 4) for x in v)


# ------------------------------------------------------------- 1. cuerpo == jar

def cuerpo_igual(bb, oficial):
    fallos = []
    huesos = {b["name"]: b for b in oficial["minecraft:geometry"][0]["bones"]}
    cuerpo = fuentes.cuerpo_del_bbmodel(bb)

    def clave_bb(e):
        origin, size = bedrock.a_geo(e["from"], e["to"])
        rot = e.get("rotation") or [0, 0, 0]
        gira = any(abs(x) > 1e-9 for x in rot)
        piv = bedrock.pivote_a_geo(e.get("origin") or [0, 0, 0])
        return (_r4(origin), _r4(size), tuple(e.get("uv_offset") or (0, 0)),
                round(float(e.get("inflate") or 0), 4),
                _r4(bedrock.rot_a_geo(rot)) if gira else (0, 0, 0),
                _r4(piv) if gira else None, bool(e.get("mirror_uv")))

    def clave_geo(c):
        rot = c.get("rotation") or [0, 0, 0]
        gira = any(abs(x) > 1e-9 for x in rot)
        return (_r4(c["origin"]), _r4(c["size"]), tuple(c.get("uv") or (0, 0)),
                round(float(c.get("inflate") or 0), 4), _r4(rot) if gira else (0, 0, 0),
                _r4(c["pivot"]) if gira else None, bool(c.get("mirror")))

    for nombre, es in cuerpo.items():
        if nombre not in huesos:
            fallos.append("el .bbmodel tiene el hueso «%s» y el jar no" % nombre)
            continue
        a = sorted(clave_bb(e) for e in es)
        b = sorted(clave_geo(c) for c in huesos[nombre].get("cubes", []))
        if a != b:
            fallos.append("los cubos de «%s» no son los del jar" % nombre)
    for nombre, b in huesos.items():
        if b.get("cubes") and nombre not in cuerpo:
            fallos.append("el jar tiene cubos en «%s» y el .bbmodel no" % nombre)

    grupos = fuentes.grupos_del_bbmodel(bb)
    for nombre, b in huesos.items():
        if nombre not in grupos:
            fallos.append("el jar tiene el hueso «%s» y el .bbmodel no" % nombre)
            continue
        padre, origen, rot = grupos[nombre]
        if _r4(bedrock.pivote_a_geo(origen)) != _r4(b.get("pivot") or (0, 0, 0)):
            fallos.append("el pivote de «%s» no es el del jar" % nombre)
        mia = bedrock.rot_a_geo(rot)
        suya = b.get("rotation") or (0, 0, 0)
        if any(abs(((x - y + 180) % 360) - 180) > 1e-4 for x, y in zip(mia, suya)):
            fallos.append("la rotacion de «%s» no es la del jar" % nombre)
        if (b.get("parent") or None) != padre:
            fallos.append("«%s» cuelga de «%s» en el .bbmodel y de «%s» en el jar"
                          % (nombre, padre, b.get("parent")))
    return fallos


# --------------------------------------------------------- 2. los 133, con hueso

def cubos_con_hueso(bb, cubos, geo):
    fallos = []
    elementos = {e["uuid"]: e for e in bb["elements"]}
    raros = 0
    for camino, e in fuentes._recorrer(bb):
        if not camino or camino[-1] not in fuentes.GRUPOS_CASCO:
            continue
        if e.get("type") != "cube":
            fallos.append("en el casco hay un elemento que no es cubo: %s (%s)"
                          % (e.get("name"), e.get("type")))
            continue
        if any(abs(x) > 1e-9 for x in (e.get("rotation") or [0, 0, 0])):
            fallos.append("cubo del casco CON ROTACION, y se exportaria recto: " + e["name"])
        if abs(float(e.get("inflate") or 0)) > 1e-9:
            fallos.append("cubo del casco con inflate, y se perderia: " + e["name"])
        if not e.get("box_uv", True):
            fallos.append("cubo del casco sin box-uv (UV por cara, que Cobblemon no lee): "
                          + e["name"])
        if e.get("mirror_uv"):
            fallos.append("cubo del casco con mirror_uv, que aqui no se exporta: " + e["name"])
        raros += 1
    if raros != len(cubos):
        fallos.append("recorri %d elementos del casco y cubos_del_casco dio %d"
                      % (raros, len(cubos)))
    for c in cubos:
        try:
            ensamblar.hueso_de(c)
        except ValueError as ex:
            fallos.append(str(ex))
    en_geo = sum(len(b["cubes"]) for b in ensamblar.huesos_nuestros(geo))
    if en_geo != len(cubos):
        fallos.append("el geo lleva %d cubos del casco y el .bbmodel %d" % (en_geo, len(cubos)))
    grupos = fuentes.grupos_del_bbmodel(bb)
    for g in fuentes.GRUPOS_CASCO:
        if g not in grupos:
            fallos.append("falta el grupo del casco «%s» en el .bbmodel" % g)
    return fallos


# ---------------------------------------------------------- 3. UV dentro y sin pisar

def _huella(c):
    r = bedrock.reparto(c.uv[0], c.uv[1], c.tam, redondeado=True)
    return (min(v[0] for v in r.values()), min(v[1] for v in r.values()),
            max(v[2] for v in r.values()), max(v[3] for v in r.values()))


def uv_dentro(cubos, oficial, geo):
    fallos = []
    g = geo["minecraft:geometry"][0]["description"]
    w, h = g["texture_width"], g["texture_height"]
    cuerpo = []
    for b in oficial["minecraft:geometry"][0]["bones"]:
        for c in b.get("cubes", []):
            if isinstance(c.get("uv"), list):
                r = bedrock.reparto(c["uv"][0], c["uv"][1], c["size"], redondeado=True)
                cuerpo.append((min(v[0] for v in r.values()), min(v[1] for v in r.values()),
                               max(v[2] for v in r.values()), max(v[3] for v in r.values())))
    huellas = [(c, _huella(c)) for c in cubos]
    for c, (u0, v0, u1, v1) in huellas:
        if u0 < 0 or v0 < 0 or u1 > w or v1 > h:
            fallos.append("%s: casilla fuera de la textura (%s)" % (c.nombre, (u0, v0, u1, v1)))
        for (bu0, bv0, bu1, bv1) in cuerpo:
            if min(u1, bu1) > max(u0, bu0) and min(v1, bv1) > max(v0, bv0):
                fallos.append("%s: su casilla pisa la textura del cuerpo" % c.nombre)
                break
    for i, (a, ha) in enumerate(huellas):
        for b, hb in huellas[i + 1:]:
            if min(ha[2], hb[2]) > max(ha[0], hb[0]) and min(ha[3], hb[3]) > max(ha[1], hb[1]):
                fallos.append("%s y %s comparten pixeles de textura" % (a.nombre, b.nombre))
    return fallos


# ------------------------------------------------------- 4. todas las caras pintadas

def caras_pintadas(cubos, textura, png_usuario):
    """
    Dos miradas: la textura FINAL (repintada) no tiene agujeros dentro de
    ninguna cara del juego, y la del usuario no los tenia dentro de ninguna
    cara de Blockbench -- si las tuviera, el repintado los arrastraria.
    """
    fallos = []
    final = np.asarray(textura)[..., 3] > 0
    origen = np.asarray(png_usuario.convert("RGBA"))[..., 3] > 0
    agujeros = huecos_origen = 0
    for c in cubos:
        for cara, (u0, v0, u1, v1) in bedrock.reparto(c.uv[0], c.uv[1], c.tam).items():
            if u1 - u0 <= 1e-9 or v1 - v0 <= 1e-9:
                continue
            for y in range(int(math.floor(v0)), int(math.ceil(v1))):
                for x in range(int(math.floor(u0)), int(math.ceil(u1))):
                    if u0 <= x + 0.5 < u1 and v0 <= y + 0.5 < v1 and not final[y, x]:
                        agujeros += 1
        for cara, (u0, v0, u1, v1) in bedrock.reparto(c.uv[0], c.uv[1], c.tam, True).items():
            u0, v0, u1, v1 = (int(v) for v in (u0, v0, u1, v1))
            if u1 > u0 and v1 > v0 and not origen[v0:v1, u0:u1].all():
                huecos_origen += 1
    if agujeros:
        fallos.append("%d texels transparentes DENTRO de caras del casco en la textura final"
                      % agujeros)
    if huecos_origen:
        fallos.append("%d caras del casco con transparencia en el PNG del usuario" % huecos_origen)
    return fallos


# --------------------------------------------------------------- 5. nada flota

def _muestras_de(cubo, afin, paso=0.5):
    """Puntos de la superficie de un cubo oficial, ya posado."""
    o, s = cubo["origin"], cubo["size"]
    inf = cubo.get("inflate") or 0.0
    x0, y0, z0 = (o[i] - inf for i in range(3))
    x1, y1, z1 = (o[i] + s[i] + inf for i in range(3))
    xs = np.arange(x0, x1 + 1e-9, paso) if x1 > x0 else np.array([x0])
    ys = np.arange(y0, y1 + 1e-9, paso) if y1 > y0 else np.array([y0])
    zs = np.arange(z0, z1 + 1e-9, paso) if z1 > z0 else np.array([z0])
    pts = []
    for x in xs:
        for y in ys:
            for z in zs:
                en_borde = (x in (x0, x1)) or (y in (y0, y1)) or (z in (z0, z1))
                if en_borde:
                    pts.append(afin((x, y, z)))
    return np.array(pts) if pts else np.zeros((0, 3))


def _distancia_a_caja(puntos, origin, size):
    lo = np.asarray(origin, dtype=float)
    hi = lo + np.asarray(size, dtype=float)
    d = np.maximum(np.maximum(lo - puntos, 0), np.maximum(puntos - hi, 0))
    return float(np.sqrt((d * d).sum(axis=1)).min()) if len(puntos) else float("inf")


def _tocan(a, b, holgura=0.05):
    """Dos cajas (origin, size) se tocan o se solapan, con una holgura."""
    for i in range(3):
        if a[0][i] + a[1][i] + holgura < b[0][i] or b[0][i] + b[1][i] + holgura < a[0][i]:
            return False
    return True


def nada_flota(geo):
    """
    Devuelve (fallos, distancias por cubo). Reposo del fichero, con las
    rotaciones estaticas.

    ⚠⚠ NO SE EXIGE QUE CADA CUBO TOQUE LA CABEZA: se exige que cada GRUPO DE
       CUBOS CONECTADOS entre si toque la cabeza. Los escapes de los cuernos
       son una cadena de nueve piezas que sale de la cabeza y acaba a dos
       unidades de ella --a proposito, son tubos de escape--, y con la regla
       simple los cuatro ultimos eslabones «flotaban» a 1,0-2,0 estando
       soldados al anterior. Lo que de verdad es un fallo es una pieza (o un
       racimo) que no toque NADA: eso si se queda en el aire.
    """
    from .visor import Escena
    escena = Escena(geo)
    muestras = []
    for hueso, cubo, afin in escena.cubos:
        if hueso in HUESOS_CABEZA:
            muestras.append(_muestras_de(cubo, afin))
    nube = np.concatenate(muestras)
    fallos, distancias = [], {}
    for b in ensamblar.huesos_nuestros(geo):
        cajas = [(c["origin"], c["size"]) for c in b["cubes"]]
        dist = [_distancia_a_caja(nube, o, s) for o, s in cajas]
        for c, d in zip(b["cubes"], dist):
            distancias[(b["name"], c.get("name", str(c["origin"])))] = d
        # componentes conexas por contacto
        comp = list(range(len(cajas)))

        def raiz(i):
            while comp[i] != i:
                comp[i] = comp[comp[i]]
                i = comp[i]
            return i
        for i in range(len(cajas)):
            for j in range(i + 1, len(cajas)):
                if _tocan(cajas[i], cajas[j]):
                    comp[raiz(i)] = raiz(j)
        grupos = {}
        for i in range(len(cajas)):
            grupos.setdefault(raiz(i), []).append(i)
        for miembros in grupos.values():
            if min(dist[i] for i in miembros) > DISTANCIA_MAX:
                nombres = ", ".join(b["cubes"][i].get("name", "?") for i in miembros)
                fallos.append("en %s hay %d pieza(s) que no tocan la cabeza ni nada que la "
                              "toque: %s" % (b["name"], len(miembros), nombres))
    return fallos, distancias


# ------------------------------------------------ 6. el menton gira en un eje, cuernos quietos

def _ejes_animados(animaciones):
    """hueso -> {0,1,2} ejes con algun valor no nulo en alguna animacion."""
    ejes = {}

    def vals(kf):
        if isinstance(kf, (int, float, str)):
            return [[kf, kf, kf]]
        if isinstance(kf, list):
            return [kf]
        if isinstance(kf, dict):
            out = []
            for v in kf.values():
                if isinstance(v, dict):
                    for k in ("pre", "post", "vector"):
                        if k in v:
                            out += vals(v[k])
                else:
                    out += vals(v)
            return out
        return []

    for a in animaciones["animations"].values():
        for nombre, b in a.get("bones", {}).items():
            for v in vals(b.get("rotation", {})):
                for i in range(3):
                    x = v[i]
                    if isinstance(x, str) or abs(float(x)) > 1e-9:
                        ejes.setdefault(nombre, set()).add(i)
    return ejes


def huesos_padre(oficial, animaciones):
    fallos = []
    huesos = {b["name"]: b for b in oficial["minecraft:geometry"][0]["bones"]}
    ejes = _ejes_animados(animaciones)
    for nuestro, padre in ensamblar.PADRE.items():
        if padre not in huesos:
            fallos.append("el jar ya no tiene el hueso «%s» del que cuelga %s" % (padre, nuestro))
            continue
        rot = huesos[padre].get("rotation") or (0, 0, 0)
        no_nulos = [i for i in range(3) if abs(rot[i]) > 1e-9]
        if len(no_nulos) > 1:
            fallos.append("«%s» tiene rotacion estatica en %d ejes: la inversa de %s "
                          "no seria inversa" % (padre, len(no_nulos), nuestro))
        if no_nulos and (ejes.get(padre, set()) - set(no_nulos)):
            fallos.append("«%s» anima ejes distintos de su rotacion estatica; "
                          "la inversa de %s dejaria de valer" % (padre, nuestro))
    for cuerno in ("horn_right", "horn_left", "horns"):
        if cuerno in ejes:
            fallos.append("«%s» tiene animacion en el jar: los escapes de los cuernos "
                          "tendrian que colgar de el, no de head_angle" % cuerno)
    return fallos


# ------------------------------------------------------------ 7. el resolver

def _en_jar(jar, ruta):
    with zipfile.ZipFile(jar) as z:
        return ruta in set(z.namelist())


def _modelos_del_jar(jar):
    with zipfile.ZipFile(jar) as z:
        return {"cobblemon:" + n.rsplit("/", 1)[-1][:-len(".json")]
                for n in z.namelist() if n.endswith(".geo.json") and "/pokemon/models/" in n}


def _posers_del_jar(jar):
    with zipfile.ZipFile(jar) as z:
        return {"cobblemon:" + n.rsplit("/", 1)[-1][:-len(".json")]
                for n in z.namelist() if n.endswith(".json") and "/pokemon/posers/" in n}


def resolver_completo(res, jar, jar_mega, megas):
    fallos = []
    aspecto = fuentes.ASPECTO
    v = res["variations"]
    if not v or v[0]["aspects"] != [aspecto] or v[0].get("model") != resolver.MODELO:
        fallos.append("la primera variacion no es [%s] con nuestro modelo" % aspecto)
    if not any(x["aspects"] == [aspecto, "shiny"] and "texture" in x for x in v):
        fallos.append("falta la variacion [%s, shiny] con su textura" % aspecto)
    for x in v:
        if x["aspects"][0] != aspecto:
            fallos.append("una variacion no empieza por %s: %s" % (aspecto, x["aspects"]))
    # las megas, campo a campo
    nuestras = {tuple(x["aspects"]): x for x in v}
    for conjunto in megas:
        for m in conjunto["variations"]:
            clave = tuple([aspecto] + list(m.get("aspects", [])))
            n = nuestras.get(clave)
            if n is None:
                fallos.append("falta la mega %s" % list(clave))
                continue
            for campo in ("poser", "model", "texture", "layers"):
                if m.get(campo) != n.get(campo):
                    fallos.append("la mega %s difiere de mega_showdown en «%s»" % (list(clave), campo))
    # lo que nombra existe
    modelos = _modelos_del_jar(jar) | _modelos_del_jar(jar_mega) | {resolver.MODELO}
    for m in resolver.modelos_nombrados(res):
        if m not in modelos:
            fallos.append("el resolver nombra un modelo que no existe en ningun sitio: " + m)
    posers = _posers_del_jar(jar) | _posers_del_jar(jar_mega)
    for x in v:
        if "poser" in x and x["poser"] not in posers:
            fallos.append("el resolver nombra un poser que no existe: " + x["poser"])
    for t in resolver.texturas_nombradas(res):
        ruta = "assets/cobblemon/" + resolver.fichero_de(t)
        nuestra = t.startswith("cobblemon:" + resolver.DIR_TEXTURAS)
        if not nuestra and not _en_jar(jar, ruta) and not _en_jar(jar_mega, ruta):
            fallos.append("el resolver nombra una textura que no esta en ningun jar: " + t)
    return fallos


# ------------------------------------------------------ 8. el aspecto esta en Java

RECOMPENSA_JAVA = fuentes.RAIZ / "mod/src/main/java/net/pokereport/luna/pase/Recompensa.java"


def aspecto_en_java():
    if not RECOMPENSA_JAVA.exists():
        return ["no encuentro " + str(RECOMPENSA_JAVA)]
    src = RECOMPENSA_JAVA.read_text(encoding="utf-8")
    if not re.search(r'"%s"' % re.escape(fuentes.ASPECTO), src):
        return ["Recompensa.java no escribe el aspecto «%s» en ningun sitio: el nivel 100 "
                "entregaria un shiny sin casco" % fuentes.ASPECTO]
    return []


# --------------------------------------- 9. el geo es el oficial menos los cuernos

def geo_es_el_oficial(oficial, geo, animaciones):
    """
    Cruza el geo FINAL con el del jar: lo unico que puede faltar son los cubos
    de `ensamblar.QUITAR`, y lo unico que puede sobrar son nuestros huesos.

    ⚠ La comprobacion 1 mira el .bbmodel del usuario contra el jar; esta mira
      lo que se va a ESCRIBIR contra el jar. Sin ella, un `pop` de mas en
      ensamblar dejaria un Charizard sin cola y todo lo demas en verde.
    """
    fallos = []
    a = {b["name"]: b for b in oficial["minecraft:geometry"][0]["bones"]}
    b = {x["name"]: x for x in geo["minecraft:geometry"][0]["bones"]}
    for nombre, hueso in a.items():
        mio = b.get(nombre)
        if mio is None:
            fallos.append("el geo final ha perdido el hueso oficial «%s»" % nombre)
            continue
        for campo in ("pivot", "rotation", "parent", "locators"):
            if hueso.get(campo) != mio.get(campo):
                fallos.append("«%s»: el campo «%s» no es el del jar" % (nombre, campo))
        cubos_jar = hueso.get("cubes") or []
        cubos_mios = mio.get("cubes") or []
        if nombre in ensamblar.QUITAR:
            if cubos_mios:
                fallos.append("«%s» esta en QUITAR y sigue con %d cubos" % (nombre, len(cubos_mios)))
            if not cubos_jar:
                fallos.append("«%s» esta en QUITAR y en el jar no tiene cubos: sobra de la lista"
                              % nombre)
        elif cubos_jar != cubos_mios:
            fallos.append("los cubos de «%s» no son los del jar" % nombre)
    for nombre in b:
        if nombre not in a and nombre not in ensamblar.PADRE:
            fallos.append("el geo final tiene un hueso que no es ni del jar ni nuestro: " + nombre)
    ejes = _ejes_animados(animaciones)
    for nombre in ensamblar.QUITAR:
        if nombre in ejes:
            fallos.append("«%s» se anima en el jar y se le quitan los cubos: revisar que no "
                          "haga falta que se vea" % nombre)
    quitados = sum(len(a[n].get("cubes") or []) for n in ensamblar.QUITAR if n in a)
    total = sum(len(x.get("cubes") or []) for x in b.values())
    esperado = sum(len(x.get("cubes") or []) for x in a.values()) - quitados         + sum(len(x["cubes"]) for x in ensamblar.huesos_nuestros(geo))
    if total != esperado:
        fallos.append("el geo final tiene %d cubos y tendria que tener %d" % (total, esperado))
    return fallos


# ------------------------------------------------------------------- todo

def todo(bb, cubos, oficial, geo, res, textura, png_usuario, jar, jar_mega, animaciones):
    fallos, avisos = [], []
    fallos += cuerpo_igual(bb, oficial)
    fallos += cubos_con_hueso(bb, cubos, geo)
    fallos += uv_dentro(cubos, oficial, geo)
    fallos += caras_pintadas(cubos, textura, png_usuario)
    f, distancias = nada_flota(geo)
    fallos += f
    lejos = sorted(distancias.items(), key=lambda kv: -kv[1])[:3]
    avisos.append("la pieza mas separada de la cabeza en reposo: %.2f (%s)"
                  % (lejos[0][1], lejos[0][0][0]) if lejos else "sin piezas")
    fallos += huesos_padre(oficial, animaciones)
    fallos += resolver_completo(res, jar, jar_mega, fuentes.resolvers_mega(jar_mega))
    fallos += aspecto_en_java()
    fallos += geo_es_el_oficial(oficial, geo, animaciones)
    g = geo["minecraft:geometry"][0]["description"]
    if textura.size != (g["texture_width"], g["texture_height"]):
        fallos.append("la textura mide %s y el geo declara %s"
                      % (textura.size, (g["texture_width"], g["texture_height"])))
    fracc = sum(1 for c in cubos if bedrock.fraccionario(c.tam))
    avisos.append("cubos del casco con tamaño fraccionario: %d de %d (por eso se repinta)"
                  % (fracc, len(cubos)))
    return fallos, avisos


def escrito(geo, res, textura, capas):
    """Vuelta por el pack: lo que hay en disco es lo que se acaba de construir."""
    fallos = []
    from PIL import Image

    def igual_json(ruta, datos, que):
        if not ruta.exists():
            fallos.append("no esta en el pack: " + que)
            return
        if json.loads(ruta.read_text(encoding="utf-8")) != datos:
            fallos.append("lo escrito no es lo construido: " + que)

    igual_json(fuentes.PACK / resolver.FICHERO_GEO, geo, "el geo")
    igual_json(fuentes.PACK / resolver.FICHERO_RESOLVER, res, "el resolver")
    d = fuentes.PACK / resolver.DIR_TEXTURAS
    for nombre, im in [(fuentes.NOMBRE + "_shiny.png", textura)] + [
            (resolver.fichero_de(resolver.recurso_nuestro(r)).rsplit("/", 1)[-1], im)
            for r, im in capas.items()]:
        ruta = d / nombre
        if not ruta.exists():
            fallos.append("no esta en el pack: " + nombre)
            continue
        if np.asarray(Image.open(ruta).convert("RGBA")).tobytes() != np.asarray(im).tobytes():
            fallos.append("la textura del pack no es la construida: " + nombre)
    if not (d / (fuentes.NOMBRE + ".png")).exists():
        fallos.append("no esta en el pack: " + fuentes.NOMBRE + ".png")
    bbm = fuentes.ARTE / (fuentes.NOMBRE + ".bbmodel")
    if not bbm.exists():
        fallos.append("no esta el .bbmodel de salida")
    else:
        from . import bbmodel
        fallos += bbmodel.verificar(bbm, geo)
    return fallos
