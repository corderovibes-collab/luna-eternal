# -*- coding: utf-8 -*-
"""
DE UN .bbmodel DE BLOCKBENCH A UN TRAJE NUESTRO.

Es la mitad que le faltaba a `blockbench.py`, que solo sabia EXPORTAR. El
usuario dibuja o retoca en Blockbench y esto lo trae de vuelta.

⚠⚠⚠ LO QUE LLEGO NO ERA UNA ARMADURA: ERA UNA SKIN. Medido sobre
   `armadura arceus.bbmodel`, las ocho piezas del cuerpo caen a holgura 0,00 y
   -0,10 sobre el esqueleto de vainilla, y sus UV son las del reparto de una
   skin de 64x32 (`body` = [20,20,28,32]). O sea que el modelo ES el cuerpo del
   jugador, pintado.

   Eso no lo invalida --al contrario, la textura se aprovecha entera-- pero
   OBLIGA a inflarlo: la capa exterior de la skin del jugador esta en 0,25
   CLAVADOS, asi que un traje a holgura cero PARPADEA contra la piel. Se infla
   al importar, y el UV NO cambia al inflar: el dibujo se conserva.

⚠⚠ Y LOS MIEMBROS IZQUIERDOS VIENEN ESPEJADOS. En las skins de 64x32 el brazo y
   la pierna izquierdos no tienen dibujo propio: son el derecho reflejado. Sus
   UV salen invertidas y una comprobacion ingenua los da por rotos. No lo estan:
   se marcan `espejo` y el formato de destino ya sabe que hacer con eso.

⚠ SE USA UN SOLO ATLAS PARA LAS CUATRO PIEZAS, y es a proposito. Cada pieza es
  un PNG distinto en el juego, asi que lo natural seria recortar lo suyo a cada
  una -- y recortar significa RECALCULAR TODAS LAS UV. Metiendo las texturas
  originales en un lienzo de 128x128 y desplazando cada UV por el origen de su
  textura, las UV se conservan tal cual las dibujo el autor. Un poco de PNG de
  mas a cambio de que no haya ni una coordenada reinventada, que es donde este
  proyecto se ha quemado siempre.
"""

from __future__ import annotations

import base64
import io
import json
from pathlib import Path

from PIL import Image

from . import modelo as M
from .modelo import Cubo


# Que clase de pieza es cada nombre, y con cuanta holgura va.
#
# ⚠⚠⚠ EL LADO NO SALE DEL NOMBRE: SALE DE LA POSICION. Y es un fallo que ya me
#    comi entero. El fichero llama `right_arm` al cubo que esta en x=+4 --o sea
#    el brazo IZQUIERDO del modelo-- porque lo nombra desde el punto de vista de
#    quien lo mira, que es lo natural dibujando. Asignando por el nombre, los
#    dos brazos y las dos piernas se cruzan.
#
#    ⚠⚠ Y NO DA NINGUN ERROR NI SE VE A SIMPLE VISTA: los miembros son espejos,
#       asi que el traje se dibuja entero y con la silueta correcta. Lo unico
#       que cambia es que cada lado lleva la textura del otro, REFLEJADA. Las
#       costuras y los detalles caen al lado contrario y se lee como "algo no
#       cuadra" sin poder decir el que.
#
#    En Blockbench la derecha del modelo esta en X NEGATIVO. Eso no se
#    interpreta: se mide.
#
# ⚠⚠ `shoe` y `leg` OCUPAN EL MISMO VOLUMEN (los dos van de y=0 a y=12) con
#    texturas distintas: son DOS CAPAS, no un error. Por eso van a piezas
#    distintas --perneras y botas-- y con holguras distintas: si compartieran
#    holgura se pelearian por los mismos pixeles y parpadearian entre ellas.
#    Lo mismo con `body` y `waist`, que comparten el torso.
CLASE = {
    "body":  ("armorBody",  None,             0.40),
    "waist": ("armorBody",  None,             0.80),
    "arm":   (None,         ("armorRightArm",  "armorLeftArm"),  0.40),
    "leg":   (None,         ("armorRightLeg",  "armorLeftLeg"),  0.40),
    "shoe":  (None,         ("armorRightBoot", "armorLeftBoot"), 0.80),
}


def _hueso_de(nombre, f, to):
    """El hueso y la holgura de un elemento, o None si no se reconoce."""
    n = nombre.lower()
    for clave, (fijo, lados, inflate) in CLASE.items():
        if clave not in n:
            continue
        if fijo:
            return fijo, inflate
        centro_x = (f[0] + to[0]) / 2.0
        return (lados[0] if centro_x < 0 else lados[1]), inflate
    return None


def _texturas(d):
    """Las texturas incrustadas, como imagenes."""
    out = []
    for t in d.get("textures", []):
        s = t.get("source") or ""
        if not s.startswith("data:"):
            raise ValueError(
                "la textura %r no viaja dentro del .bbmodel: en Blockbench hay "
                "que guardar los pixeles, no la ruta" % t.get("name"))
        img = Image.open(io.BytesIO(base64.b64decode(s.split(",", 1)[1])))
        out.append((t.get("name"), img.convert("RGBA")))
    return out


def atlas(texturas, lado=128):
    """
    Mete las texturas en un lienzo y devuelve (imagen, desplazamientos).

    ⚠ Se colocan en filas y se ABORTA si no caben, en vez de recortar. Una
      textura recortada no da ningun error: da un traje con media pieza en
      blanco, que se descubre en el juego.
    """
    lienzo = Image.new("RGBA", (lado, lado), (0, 0, 0, 0))
    desp, x, y, alto_fila = [], 0, 0, 0
    for nombre, img in texturas:
        w, h = img.size
        if x + w > lado:
            x, y, alto_fila = 0, y + alto_fila, 0
        if y + h > lado:
            raise ValueError("las texturas no caben en %dx%d: se sale %s"
                             % (lado, lado, nombre))
        lienzo.paste(img, (x, y))
        desp.append((x, y))
        x += w
        alto_fila = max(alto_fila, h)
    return lienzo, desp


def _rect(uv):
    """
    Un UV de Blockbench como rectangulo (u0, v0, u1, v1) con u0 <= u1.

    ⚠⚠⚠ ESTO ES LO QUE HACIA FALLAR A LOS TRES MIEMBROS IZQUIERDOS, y costo un
       rato porque parecia cosa del espejo. Blockbench guarda un UV VOLTEADO
       escribiendo las esquinas al reves: la cara norte del pie derecho es
       [4,20,8,32] y la del izquierdo [8,20,4,32] -- el MISMO rectangulo, con la
       U dada la vuelta. Tomando `uv[0]` como borde izquierdo, el origen salia
       corrido justo el ancho del cubo, y las tres piezas parecian «no ser de
       caja» cuando lo eran perfectamente.
       Un UV volteado no es un UV distinto: es el mismo con una instruccion de
       reflejo dentro.
    """
    u0, v0, u1, v1 = uv
    return (min(u0, u1), min(v0, v1), max(u0, u1), max(v0, v1))


def _caja_uv(f, t, caras):
    """
    El origen de UV de caja de un cubo, o None si sus caras no son de caja.

    Devuelve (u, v, espejo).
    """
    w, h, d = (t[0] - f[0], t[1] - f[1], t[2] - f[2])
    norte = (caras.get("north") or {}).get("uv")
    if not norte:
        return None

    def encaja(u, v, espejo):
        # El reparto de Bedrock. Con espejo, este y oeste se intercambian.
        oeste, este = u + d + w, u
        if espejo:
            oeste, este = u, u + d + w
        esperado = {
            "north": [u + d, v + d, u + d + w, v + d + h],
            "south": [u + d + w + d, v + d, u + 2 * d + 2 * w, v + d + h],
            "east":  [este, v + d, este + d, v + d + h],
            "west":  [oeste, v + d, oeste + d, v + d + h],
            "up":    [u + d, v, u + d + w, v + d],
            "down":  [u + d + w, v, u + d + 2 * w, v + d],
        }
        for cara, esp in esperado.items():
            real = (caras.get(cara) or {}).get("uv")
            if real is None:
                continue
            # ⚠ Se comparan RECTANGULOS normalizados, no las cuatro cifras en el
            #   orden en que estan escritas: un UV volteado dice lo mismo.
            a = tuple(round(x, 3) for x in _rect(real))
            b = tuple(round(x, 3) for x in _rect(esp))
            if a != b:
                return False
        return True

    nr = _rect(norte)
    u, v = nr[0] - d, nr[1] - d
    for espejo in (False, True):
        if encaja(u, v, espejo):
            return (u, v, espejo)
    return None


def color_medio(img, u, v, w, h):
    """El color dominante de una region. Lo usa el visor y los avisos."""
    caja = img.crop((int(u), int(v), int(u + max(1, w)), int(v + max(1, h))))
    px = [p for p in caja.getdata() if p[3] > 8]
    if not px:
        return (160, 160, 160)
    return tuple(sum(p[i] for p in px) // len(px) for i in range(3))


def importar(ruta, id_traje, nombre, lado=128):
    """
    Lee un .bbmodel y devuelve (Traje, atlas, avisos).

    Los avisos son las piezas que NO se pudieron traer. Se devuelven en vez de
    lanzar: media armadura importada y dicho cual falta es mas util que un
    error que no dice cuanto se ha perdido.
    """
    d = json.loads(Path(ruta).read_text(encoding="utf-8"))
    lienzo, desp = atlas(_texturas(d), lado)

    t = M.Traje(id_traje, nombre)
    avisos = []
    for e in d.get("elements", []):
        n = (e.get("name") or "").strip()
        f, to = e.get("from"), e.get("to")
        if not f or not to:
            # ⚠⚠⚠ ESTO SE CAIA EN SILENCIO Y ES EL FALLO DE SIEMPRE. El aro de
            #    Arceus es una MALLA (`type: mesh`, 13 vertices y 12 caras
            #    poligonales), no un cubo, y sin este aviso el importador se lo
            #    saltaba sin decir nada: la pieza mas caracteristica del traje
            #    desaparecia y la unica pista era que las cuentas no cuadraban
            #    -- 8 importadas + 6 avisos sobre 15 elementos.
            #    ⚠⚠ Y una malla NO se puede convertir: el dibujado de armadura
            #       de Minecraft son CAJAS y ya esta. Hay que rehacerla con
            #       cubos, que ademas es como se ve bien en un juego de cubos.
            avisos.append(
                "%r es de tipo %r y no un cubo: el dibujado de armadura solo "
                "sabe hacer cajas, asi que hay que rehacerla con cubos"
                % (n or "sin nombre", e.get("type") or "?"))
            continue

        if e.get("rotation"):
            avisos.append("%s lleva rotacion %s y el destino solo admite cubos "
                          "rectos: fuera" % (n or "cubo", e["rotation"]))
            continue
        destino = _hueso_de(n, f, to)
        if destino is None:
            avisos.append("%r no cae en ningun hueso conocido: fuera"
                          % (n or "cubo sin nombre"))
            continue
        hueso, inflate = destino

        caras = e.get("faces") or {}
        caja = _caja_uv(f, to, caras)
        if caja is None:
            avisos.append("%s no usa UV de caja: fuera" % n)
            continue
        u, v, espejo = caja

        # El indice de textura de la cara norte manda: es la que se ve de frente.
        idx = (caras.get("north") or {}).get("texture") or 0
        ox, oy = desp[idx]

        tam = tuple(round(to[i] - f[i], 4) for i in range(3))
        c = Cubo(origen=tuple(round(x, 4) for x in f), tam=tam,
                 color=color_medio(lienzo, ox + u, oy + v, tam[0], tam[1]),
                 material="metal", inflate=inflate)
        c.uv = (ox + u, oy + v)
        c.espejo = espejo
        t.poner(hueso, c)

    # ⚠⚠ LAS CUENTAS TIENEN QUE CUADRAR. Cada elemento del fichero acaba
    #    importado o con su aviso, y ninguno se pierde por el camino. Es la
    #    unica forma de saber que el importador no se esta callando algo: un
    #    `continue` nuevo sin su aviso rompe esto en vez de perder una pieza.
    traidos = sum(len(l) for l in t.huesos.values())
    if traidos + len(avisos) != len(d.get("elements", [])):
        raise AssertionError(
            "el importador se ha comido algo: %d elementos, %d traidos y %d "
            "avisos" % (len(d.get("elements", [])), traidos, len(avisos)))

    return t, lienzo, avisos
