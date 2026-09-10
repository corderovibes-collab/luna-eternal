# -*- coding: utf-8 -*-
"""
EL .BBMODEL, y las comprobaciones que deciden si vale.

⚠⚠ SE ESCRIBE EN EL MISMO FORMATO QUE LA REFERENCIA, no en el que salga:
   `format_version 5.0`, `model_format free`, `box_uv: false` -- o sea UV POR
   CARA. Se comprobo leyendo el fichero del usuario, no de memoria.

⚠⚠⚠ Y AUNQUE LAS UV SEAN POR CARA, SALEN DEL REPARTO DE CAJA. No es una
   contradiccion: el reparto de caja es un caso particular del de cara, asi que
   escribirlo asi da lo mejor de los dos -- Blockbench deja mover una cara
   suelta, y la textura sigue siendo la de una skin de Minecraft.
"""

from __future__ import annotations

import json
import uuid as _uuid
from pathlib import Path

from trajes import modelo

from . import diseno


def _uv_por_cara(cubo, indice_textura):
    """Las seis caras con su recuadro, en el espacio de la textura."""
    salida = {}
    for cara, (u0, v0, u1, v1) in modelo.casillas(cubo).items():
        salida[cara] = {
            "uv": [round(u0, 4), round(v0, 4), round(u1, 4), round(v1, 4)],
            "texture": indice_textura,
        }
    return salida


def construir(piezas, cubos, textura_png, nombre_textura, lado,
              fotogramas=1, ticks=1):
    elementos = []
    por_grupo = {g: [] for g in diseno.ORDEN_GRUPOS}

    for pieza, cubo in zip(piezas, cubos):
        u = str(_uuid.uuid4())
        f = [round(v, 4) for v in cubo.origen]
        t = [round(cubo.origen[i] + cubo.tam[i], 4) for i in range(3)]
        elementos.append({
            "name": pieza.nombre,
            "box_uv": False,
            "rescale": False,
            "locked": False,
            "render_order": "default",
            "allow_mirror_modeling": True,
            "from": f,
            "to": t,
            "autouv": 0,
            "color": diseno.ORDEN_GRUPOS.index(pieza.grupo) % 8,
            # ⚠ El origen de cada pieza es SU PROPIO centro. Con (0,0,0) todas
            #   girarian alrededor del pomo, que no es lo que nadie espera al
            #   seleccionar una pieza suelta en Blockbench.
            "origin": [round((f[i] + t[i]) / 2.0, 4) for i in range(3)],
            "faces": _uv_por_cara(cubo, 0),
            "type": "cube",
            "uuid": u,
        })
        por_grupo[pieza.grupo].append(u)

    grupos = []
    for i, g in enumerate(diseno.ORDEN_GRUPOS):
        grupos.append({
            "name": g,
            # ⚠⚠ EL ORIGEN DEL GRUPO ES SU EJE, y aqui hay una decision: todos
            #    los grupos giran sobre el EJE CENTRAL de la espada (x=0, z=0)
            #    y a la altura donde empieza el grupo. Asi «girar la hoja» la
            #    gira sobre su propio eje y no la manda de paseo.
            "origin": [0, _ALTURA_GRUPO[g], 0],
            "color": i % 8,
            "uuid": str(_uuid.uuid4()),
            "export": True,
            "mirror_uv": False,
            "isOpen": True,
            "locked": False,
            "visibility": True,
            "autouv": 0,
            "children": por_grupo[g],
        })

    return {
        "meta": {"format_version": "5.0", "model_format": "free", "box_uv": False},
        "name": "pikachu electric sword",
        "model_identifier": "pikachu_electric_sword",
        "visible_box": [3, 3, 0],
        "variable_placeholders": "",
        "variable_placeholder_buttons": [],
        "timeline_setups": [],
        "unhandled_root_fields": {},
        # ⚠ La resolucion del proyecto y la de la textura coinciden a
        #   proposito. En la referencia no coinciden (16x16 contra 128x64) y
        #   eso obliga a que cada textura lleve su propio `uv_width` -- una
        #   fuente de confusion que aqui no hace falta arrastrar.
        "resolution": {"width": lado, "height": lado},
        "elements": elementos,
        "outliner": grupos,
        "textures": [{
            "path": "",
            "name": nombre_textura,
            "folder": "",
            "namespace": "",
            "id": "0",
            # ⚠⚠⚠ AQUI ESTA LA DIFERENCIA ENTRE LA QUIETA Y LA ANIMADA, Y SON
            #    DOS PARES DE NUMEROS DISTINTOS QUE ES FACIL CONFUNDIR:
            #      width/height        lo que MIDE la imagen (64 x 512 animada)
            #      uv_width/uv_height  el espacio en el que caen las UV, que es
            #                          SIEMPRE UN FOTOGRAMA (64 x 64)
            #    Si `uv_height` siguiera a la altura real, las UV se repartirian
            #    entre los ocho fotogramas y cada cara de la espada dibujaria un
            #    trozo de un fotograma distinto. No daria error: daria una
            #    espada con las caras descolocadas.
            "width": lado,
            "height": lado * fotogramas,
            "uv_width": lado,
            "uv_height": lado,
            "particle": False,
            "use_as_default": False,
            "layers_enabled": False,
            "sync_to_project": "",
            "render_mode": "default",
            "render_sides": "auto",
            # ⚠⚠ ESTOS CUATRO SON EL `.mcmeta` DE MINECRAFT, CON OTRO NOMBRE.
            #    Blockbench los guarda en el propio .bbmodel y los exporta como
            #    `.mcmeta` al sacar la textura, asi que ponerlos aqui es lo que
            #    hace que el fichero se abra YA ANIMADO en vez de obligar a
            #    configurarlo a mano cada vez.
            "frame_time": ticks,
            "frame_order_type": "loop",
            "frame_order": "",
            "frame_interpolate": False,
            "visible": True,
            "internal": True,
            "saved": False,
            "uuid": str(_uuid.uuid4()),
            "relative_path": "./" + nombre_textura,
            "source": "data:image/png;base64," + textura_png,
        }],
    }


_ALTURA_GRUPO = {
    "Blade": diseno.HOJA_Y[0],
    "Energy_Core": diseno.HOJA_Y[0],
    "Guard": diseno.GUARDA_Y[0],
    "Handle": diseno.MANGO_Y[0],
    "Pommel": diseno.POMO_Y[0],
}


# ------------------------------------------------------------ comprobaciones
#
# ⚠⚠⚠ SE COMPRUEBA ANTES DE ENTREGAR, y no es burocracia: son los cinco fallos
#    que un modelo puede tener SIN QUE NADA DE ERROR -- una pieza flotando, un
#    lado mas ancho que el otro, dos cubos ocupando el mismo pixel de textura,
#    una medida fuera de rejilla y un hueco entre tramos.

def _solapan(a, b):
    """¿Se cruzan dos cajas en volumen? Tocarse NO cuenta."""
    for i in range(3):
        if a.origen[i] + a.tam[i] <= b.origen[i] + 1e-9: return False
        if b.origen[i] + b.tam[i] <= a.origen[i] + 1e-9: return False
    return True


def comprobar(piezas, cubos, lado):
    fallos, avisos = [], []

    # 1 · REJILLA
    for p in piezas:
        for i in range(3):
            for v in (p.origen[i], p.origen[i] + p.tam[i]):
                if abs(v / diseno.REJILLA - round(v / diseno.REJILLA)) > 1e-6:
                    fallos.append("%s: %.4f fuera de la rejilla de %.2f"
                                  % (p.nombre, v, diseno.REJILLA))

    # 2 · SIMETRIA. Cada pieza tiene que tener su reflejo en X.
    cajas = {(round(p.origen[0], 4), round(p.origen[1], 4), round(p.origen[2], 4),
              round(p.tam[0], 4), round(p.tam[1], 4), round(p.tam[2], 4))
             for p in piezas}
    asimetricas = [p.nombre for p in piezas if not p.simetrica]
    for p in piezas:
        if not p.simetrica:
            continue
        esp = (round(-p.origen[0] - p.tam[0], 4), round(p.origen[1], 4),
               round(p.origen[2], 4), round(p.tam[0], 4), round(p.tam[1], 4),
               round(p.tam[2], 4))
        if esp not in cajas:
            fallos.append("%s no tiene espejo en X" % p.nombre)
    # ⚠ Se IMPRIMEN. Una excepcion que no se ve deja de ser una excepcion y
    #   pasa a ser un agujero.
    if asimetricas:
        avisos.append("asimetricas A PROPOSITO (%d): el rayo del nucleo"
                      % len(asimetricas))

    # 3 · NADA FLOTANDO. Cada pieza toca o cruza a otra.
    for i, a in enumerate(piezas):
        ca = a.cubo()
        pegada = False
        for j, b in enumerate(piezas):
            if i == j:
                continue
            cb = b.cubo()
            if _solapan(ca, cb):
                pegada = True
                break
            # tocarse por una cara: se cruzan en dos ejes y coinciden en el otro
            toca = 0
            for k in range(3):
                a0, a1 = ca.origen[k], ca.origen[k] + ca.tam[k]
                b0, b1 = cb.origen[k], cb.origen[k] + cb.tam[k]
                if abs(a1 - b0) < 1e-9 or abs(b1 - a0) < 1e-9:
                    toca += 1
                elif a1 > b0 + 1e-9 and b1 > a0 + 1e-9:
                    toca += 0
                else:
                    toca = -99
                    break
            if toca >= 1:
                pegada = True
                break
        if not pegada:
            fallos.append("%s esta FLOTANDO: no toca ninguna otra pieza" % a.nombre)

    # 4 · LA TEXTURA NO SE PISA
    ocupado = {}
    for p, c in zip(piezas, cubos):
        for ox, oy, aw, ah, _ in modelo._caras(c):
            for y in range(oy, oy + ah):
                for x in range(ox, ox + aw):
                    if (x, y) in ocupado and ocupado[(x, y)] != p.nombre:
                        fallos.append("textura: %s pisa a %s en (%d,%d)"
                                      % (p.nombre, ocupado[(x, y)], x, y))
                        return fallos, avisos
                    ocupado[(x, y)] = p.nombre
                    if x >= lado or y >= lado:
                        fallos.append("textura: %s se sale de %dx%d"
                                      % (p.nombre, lado, lado))
                        return fallos, avisos

    # 5 · NINGUN PAPEL MUERTO EN LA PALETA.
    # ⚠⚠ Un papel que nadie usa es una promesa sin cumplir, y aqui aparecieron
    #    DOS solos: `pua` y `chispa` sobrevivieron a las piezas que los usaban.
    #    No dan ningun error --nadie los pide-- pero le dicen al siguiente que
    #    la espada tiene puas y chispas, y no las tiene. La direccion contraria
    #    ya estaba cubierta sola: pedir un papel inexistente revienta al pintar.
    usados_papel = {p.papel for p in piezas}
    muertos = sorted(set(diseno.PAPEL) - usados_papel)
    if muertos:
        fallos.append("la paleta declara papeles que no usa nadie: %s"
                      % ", ".join(muertos))

    # 6 · LOS TRAMOS ENCAJAN SIN HUECO
    tramos = [("pomo", diseno.POMO_Y), ("mango", diseno.MANGO_Y),
              ("guarda", diseno.GUARDA_Y), ("hoja", diseno.HOJA_Y)]
    for (n1, t1), (n2, t2) in zip(tramos, tramos[1:]):
        if abs(t1[1] - t2[0]) > 1e-9:
            fallos.append("hueco entre %s y %s: %.2f -> %.2f" % (n1, n2, t1[1], t2[0]))

    # avisos (no rompen nada, pero conviene verlos)
    usados = len(ocupado)
    avisos.append("textura: %d de %d pixeles usados (%.0f%%)"
                  % (usados, lado * lado, 100.0 * usados / (lado * lado)))
    lo = [min(p.origen[i] for p in piezas) for i in range(3)]
    hi = [max(p.origen[i] + p.tam[i] for p in piezas) for i in range(3)]
    avisos.append("caja: x %.2f..%.2f · y %.2f..%.2f · z %.2f..%.2f  (largo %.2f)"
                  % (lo[0], hi[0], lo[1], hi[1], lo[2], hi[2], hi[1] - lo[1]))
    return fallos, avisos


def escribir(datos, destino):
    Path(destino).parent.mkdir(parents=True, exist_ok=True)
    Path(destino).write_text(json.dumps(datos, indent=2), encoding="utf-8")


# ------------------------------------------------- la vuelta: leer lo escrito
#
# ⚠⚠⚠ SE VUELVE A LEER EL FICHERO DE DISCO, NO EL DICCIONARIO EN MEMORIA.
#    Comparar el objeto consigo mismo pasaria siempre, y esa leccion ya esta
#    pagada aqui dos veces --el JSON de los nichos y la conversion de los
#    trajes-- : lo que rompe un exportador es lo que pasa AL SERIALIZAR.
#
# ⚠⚠ Y se cruza contra la REFERENCIA, no contra una idea nuestra del formato:
#    `meta` tiene que decir exactamente lo mismo que dice el fichero del
#    usuario, porque es el unico formato del que consta que Blockbench abre.

def verificar(destino, piezas, cubos, lado, ruta_referencia=None, fotogramas=1):
    """Lee el .bbmodel escrito y comprueba que dice lo que tenia que decir."""
    fallos = []
    d = json.loads(Path(destino).read_text(encoding="utf-8"))

    # 1 · EL FORMATO, contra el de la referencia si esta disponible.
    if ruta_referencia and Path(ruta_referencia).exists():
        ref = json.loads(Path(ruta_referencia).read_text(encoding="utf-8"))
        if d.get("meta") != ref.get("meta"):
            fallos.append("meta %s != la de la referencia %s"
                          % (d.get("meta"), ref.get("meta")))

    # 2 · LAS PIEZAS: las mismas, con el mismo nombre y la misma caja.
    el = d.get("elements", [])
    if len(el) != len(piezas):
        fallos.append("elementos: %d escritos, %d de diseño" % (len(el), len(piezas)))
    por_nombre = {e.get("name"): e for e in el}
    if len(por_nombre) != len(el):
        fallos.append("hay nombres repetidos entre los elementos")
    for pieza in piezas:
        e = por_nombre.get(pieza.nombre)
        if e is None:
            fallos.append("falta el elemento '%s'" % pieza.nombre)
            continue
        for i in range(3):
            if abs(e["from"][i] - pieza.origen[i]) > 1e-4:
                fallos.append("%s: from %s != %s" % (pieza.nombre, e["from"],
                                                     list(pieza.origen)))
                break
            if abs((e["to"][i] - e["from"][i]) - pieza.tam[i]) > 1e-4:
                fallos.append("%s: tamaño %s != %s"
                              % (pieza.nombre,
                                 [round(e["to"][j] - e["from"][j], 4) for j in range(3)],
                                 list(pieza.tam)))
                break
        if e.get("type") != "cube":
            fallos.append("%s no es un cubo (%s)" % (pieza.nombre, e.get("type")))
        # ⚠ Un nombre generico es justo lo que el encargo prohibe.
        if (pieza.nombre or "").strip().lower() in ("cube", "cubo", "") \
                or (pieza.nombre or "").strip().lower().startswith(("cubo", "cube")):
            fallos.append("nombre generico: '%s'" % pieza.nombre)

    # 3 · EL ARBOL: cada pieza cuelga de UN grupo y de ninguno mas.
    uuids = {e.get("uuid") for e in el}
    if len(uuids) != len(el):
        fallos.append("hay uuid repetidos: el arbol de Blockbench se romperia")
    vistos, grupos = {}, d.get("outliner", [])
    if [g.get("name") for g in grupos] != diseno.ORDEN_GRUPOS:
        fallos.append("los grupos son %s y esperabamos %s"
                      % ([g.get("name") for g in grupos], diseno.ORDEN_GRUPOS))
    for g in grupos:
        for hijo in g.get("children", []):
            if hijo not in uuids:
                fallos.append("%s cuelga de un uuid que no existe" % g.get("name"))
            if hijo in vistos:
                fallos.append("un mismo cubo cuelga de %s y de %s"
                              % (vistos[hijo], g.get("name")))
            vistos[hijo] = g.get("name")
    huerfanos = uuids - set(vistos)
    if huerfanos:
        # ⚠⚠ Un cubo sin grupo SE ABRE IGUAL y aparece suelto en el arbol: no
        #    da ningun error, da una jerarquia que no es la que se prometio.
        fallos.append("%d cubos no cuelgan de ningun grupo" % len(huerfanos))

    # 4 · LAS UV, dentro de la textura.
    for e in el:
        for cara, datos in (e.get("faces") or {}).items():
            u0, v0, u1, v1 = datos["uv"]
            if min(u0, v0, u1, v1) < -1e-6 or max(u0, u1) > lado + 1e-6 \
                    or max(v0, v1) > lado + 1e-6:
                fallos.append("%s/%s: uv %s fuera de 0..%d"
                              % (e.get("name"), cara, datos["uv"], lado))
            if datos.get("texture") != 0:
                fallos.append("%s/%s apunta a una textura que no existe"
                              % (e.get("name"), cara))

    # 5 · LA TEXTURA VIAJA DENTRO, y del tamaño declarado. Sin esto el modelo
    #     se abre en blanco y parece que el arte esta mal hecho.
    tex = d.get("textures", [])
    if len(tex) != 1:
        fallos.append("texturas: %d, esperabamos 1" % len(tex))
    else:
        t = tex[0]
        if not str(t.get("source", "")).startswith("data:image/png;base64,"):
            fallos.append("la textura no viaja incrustada en el .bbmodel")
        # ⚠⚠ LA ALTURA CRECE CON LOS FOTOGRAMAS Y `uv_height` NO. Es justo el
        #    par que se confunde, asi que se comprueban por separado en vez de
        #    en un bucle que los trate igual -- que es como estaba y como habria
        #    dejado pasar una tira con las UV repartidas entre ocho fotogramas.
        esperado = {"width": lado, "height": lado * fotogramas,
                    "uv_width": lado, "uv_height": lado}
        for clave, valor in esperado.items():
            if t.get(clave) != valor:
                fallos.append("textura: %s=%s y esperabamos %d"
                              % (clave, t.get(clave), valor))
        if fotogramas > 1 and not t.get("frame_time"):
            fallos.append("la textura tiene %d fotogramas y ningun frame_time: "
                          "Blockbench la abriria quieta" % fotogramas)
    return fallos
