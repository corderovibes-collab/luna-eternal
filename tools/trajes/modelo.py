# -*- coding: utf-8 -*-
"""
EL MODELO DE UN TRAJE: cubos, huesos, textura y el .geo.json de GeckoLib.

Un traje NO es una imagen. Son dos cosas:

  la FORMA     un .geo.json que dice «aqui va un cubo de 3x2x3 encima de la
               cabeza» (las orejas), «aqui una capa», «aqui unas hombreras»
  la PINTURA   un PNG que se estira por encima de esos cubos

⚠⚠⚠ LOS NOMBRES DE LOS HUESOS SON LA CONVENCION DE GECKOLIB, NO UNA ELECCION.
   `bipedHead`, `bipedBody`, `bipedRightArm`... son anclas VACIAS que GeckoLib
   pega al cuerpo del jugador; dentro van los `armor*` con los cubos de verdad.
   Un nombre mal escrito NO DA NINGUN ERROR: el traje sale flotando en el
   origen del mundo, o no sale. Verificado abriendo el mod de Diosesmon
   (`assets/diosesmon-client/geo/eevee/`), y coincide con la wiki de GeckoLib.

⚠⚠ Y LOS PIVOTES DE `armorHead` Y `armorBody` VAN A [0, 24, 0]. Lo dice su wiki
   y es lo que mas facil se escapa: con otro pivote el traje gira alrededor del
   punto equivocado y la cabeza se descoloca al mirar arriba.
"""

from __future__ import annotations

import json
import math
import random
from dataclasses import dataclass, field
from pathlib import Path

from PIL import Image

# ⚠⚠⚠ NO SE LLAMA `geo/`, Y ESA ES LA MITAD DEL ARREGLO DEL 2026-08-28.
#
#    GECKOLIB VALIDA TODO FICHERO QUE ENCUENTRE EN `assets/*/geo/`, esté quien
#    esté usándolo. Nosotros no dibujamos con GeckoLib --estos modelos son cubos
#    rígidos y los pinta Minecraft-- pero al dejarlos en su carpeta, GeckoLib los
#    leía igual. Uno mal formado no da un aviso: TUMBA LA RECARGA DE RECURSOS
#    ENTERA y el juego se queda colgado en la pantalla de carga.
#
#    En `trajes/` no los mira nadie más que nosotros.
CARPETA = "trajes"

# ⚠⚠⚠ UN TEXEL POR UNIDAD, Y NO ES UNA ELECCION NUESTRA. `ModelPart.Cuboid`
#    calcula el ancho de cada casilla con el TAMAÑO DEL CUBO --`u + sizeZ`,
#    `u + sizeZ + sizeX`...-- asi que la relacion esta fijada por Minecraft.
#    Aqui hubo un `ESCALA_UV` que decia poder subirla; no podia, y un ajuste que
#    no hace nada es peor que no tenerlo: el dia que alguien lo suba, la textura
#    se sale de sitio en el juego y aqui todo parecera correcto.

# ---------------------------------------------------------------- el esqueleto

# ⚠ Sale del modelo de jugador de vanilla, no de la nada: si el pivote no
#   coincide con el del hueso al que GeckoLib lo pega, el traje se mueve raro.
PIVOTES = {
    "bipedHead": (0, 24, 0),
    "bipedBody": (0, 24, 0),
    "bipedRightArm": (-5, 22, 0),
    "bipedLeftArm": (5, 22, 0),
    "bipedRightLeg": (-1.9, 12, 0),
    "bipedLeftLeg": (1.9, 12, 0),
}

# Cada hueso de armadura cuelga de SU ANCLA `biped*`, y ninguno de otro hueso
# de armadura.
#
# ⚠⚠⚠ LAS BOTAS COLGABAN DE `armorRightLeg` Y ESO TUMBO EL CLIENTE (2026-08-28).
#    Parecia lo natural --una bota va en una pierna-- y tiene un fallo mortal:
#    las botas y las perneras son PIEZAS DISTINTAS, o sea FICHEROS DISTINTOS.
#    `entrenador_boots.geo.json` declaraba `armorRightBoot -> armorRightLeg` y ese
#    padre vive en `entrenador_legs.geo.json`. Dentro de su fichero, el padre NO
#    EXISTE.
#
#    Y no se quedo en un aviso: GeckoLib valida TODO .geo.json que encuentre,
#    lanzo, la recarga de recursos fallo entera, y Minecraft respondio
#    «Caught error loading resourcepacks, removing all selected resourcepacks»
#    -- el juego se quedo colgado en la pantalla de carga para siempre.
#
#    Colgando cada hueso de su ancla, cada fichero se sostiene solo. Y no se
#    pierde nada: `armorRightLeg` no tiene rotacion propia, asi que heredar de
#    el o de la pierna del jugador es exactamente lo mismo.
PADRES = {
    "armorHead": "bipedHead",
    "armorBody": "bipedBody",
    "armorRightArm": "bipedRightArm",
    "armorLeftArm": "bipedLeftArm",
    "armorRightLeg": "bipedRightLeg",
    "armorLeftLeg": "bipedLeftLeg",
    "armorRightBoot": "bipedRightLeg",
    "armorLeftBoot": "bipedLeftLeg",
}

# Que huesos puede tocar cada pieza. Es lo que impide que el peto dibuje una
# bota: en el juego son cuatro objetos distintos y cada uno se pone por su lado.
PIEZAS = {
    "head": ["armorHead"],
    "body": ["armorBody", "armorRightArm", "armorLeftArm"],
    "legs": ["armorRightLeg", "armorLeftLeg"],
    "boots": ["armorRightBoot", "armorLeftBoot"],
}


def pivote_de(hueso):
    """El pivote de un hueso de armadura: el mismo que el de su ancla."""
    ancla = PADRES.get(hueso, hueso)
    while ancla in PADRES:
        ancla = PADRES[ancla]
    return PIVOTES[ancla]


# ---------------------------------------------------------------- los cubos

@dataclass
class Cubo:
    """Un cubo del traje, en coordenadas de Minecraft (el suelo es y=0)."""

    origen: tuple            # esquina minima (x, y, z)
    tam: tuple               # (dx, dy, dz)
    color: tuple             # RGB base
    material: str = "tela"   # tela / metal / cuero / neon
    inflate: float = 0.0
    uv: tuple = field(default=None)   # lo asigna empaquetar()

    # ⚠⚠⚠ UN CUBO GIRADO NO CABE EN UN `cuboid()`. `ModelPart` solo sabe girar
    #    PARTES enteras, asi que un cubo con `rot` sale al .geo.json como un
    #    HUESO HIJO con un solo cubo dentro -- lo mismo que hace Blockbench al
    #    exportar (los `bone_r1`). Meterlo como un cubo mas no da ningun error:
    #    sale RECTO, y una corona con las puntas rectas parece un cubo.
    rot: tuple = None          # grados en espacio Blockbench (x, y, z)
    pivote: tuple = None       # el punto sobre el que gira, en espacio bb

    # De donde sale su pintura, si viene de un .bbmodel:
    #   nombre de cara -> (indice de textura, (rect, voltea_u, voltea_v))
    caras_src: dict = None
    # Si el cubo usa el reparto de caja, de donde se copia entero:
    # (indice de textura, u, v). Ver `importar.caja_uv`.
    caja_src: tuple = None

    def huella(self):
        """Lo que ocupa en la textura: (2*fondo + 2*ancho) x (fondo + alto)."""
        return huella(self)


@dataclass
class Traje:
    """Un traje entero: cuatro piezas, cada una con sus huesos y sus cubos."""

    id: str
    nombre: str
    huesos: dict = field(default_factory=dict)   # hueso -> [Cubo]
    # ⚠⚠ UN TRAJE IMPORTADO TRAE SU PINTURA HECHA. Los trajes de casa se pintan
    #    del color de cada cubo (`pintar`); uno que viene de Blockbench ya tiene
    #    el dibujo de su autor, y repintarlo seria tirarlo. Aqui van SUS PNG tal
    #    cual, y cada cubo dice de cual recorta cada cara (`caras_src`).
    #    ⚠ Si hay fuentes, el traje se HORNEA; si no, se pinta. Son los dos
    #      caminos, y no hay un tercero.
    fuentes: list = field(default_factory=list)
    lado: int = 128
    # Lo que salio del horneado, por pieza. Lo rellena `escribir`.
    lados: dict = field(default_factory=dict)

    def poner(self, hueso, *cubos):
        self.huesos.setdefault(hueso, []).extend(cubos)

    def de_pieza(self, pieza):
        """Los huesos de una pieza, sin los vacios."""
        return {h: self.huesos[h] for h in PIEZAS[pieza] if self.huesos.get(h)}


# ---------------------------------------------------------------- la textura

# ⚠⚠ EL REPARTO DE LA TEXTURA SE CALCULA, NO SE ESCRIBE. Es exactamente el
#    fallo que ya nos costo caro en las pantallas: unas coordenadas escritas a
#    mano cuadran hasta que alguien cambia un cubo, y entonces DOS CUBOS
#    COMPARTEN PIXELES -- que no da ningun error, da un traje con la cara de la
#    bota dibujada en el hombro.
def huella(cubo):
    """Lo que ocupa un cubo en la textura, en pixeles enteros."""
    w, h, d = cubo.tam
    return (int(math.ceil(2 * d + 2 * w)), int(math.ceil(d + h)))


# El reparto de caja, con los nombres de cara de Blockbench. Ver `importar.py`.
#
# ⚠⚠ ESTE REPARTO NO ES LIBRE: es el de `ModelPart.Cuboid`, y se verifico contra
#    la cabeza de vanilla -- cubo 8x8x8 en uv [0,0], y la cara va a (8,8). Si el
#    generador y el juego no estuvieran de acuerdo, la textura saldria rotada o
#    cambiada de cara, y solo se veria mirandolo.
#
# ⚠⚠⚠ Y LAS CASILLAS LLEVAN DECIMALES A PROPOSITO. Minecraft calcula el ancho de
#    cada una con el TAMAÑO DEL CUBO, no con los pixeles que le demos: un texel
#    por unidad, siempre. Redondear aqui --«un pixel de mas para que quepa»--
#    correria las casillas siguientes y cada cara leeria un trozo de su vecina.
def casillas(cubo):
    """nombre de cara -> (u0, v0, u1, v1), en coordenadas reales."""
    u, v = cubo.uv
    w, h, d = cubo.tam
    return {
        "east":  (u, v + d, u + d, v + d + h),                          # -X
        "north": (u + d, v + d, u + d + w, v + d + h),                  # -Z
        "west":  (u + d + w, v + d, u + 2 * d + w, v + d + h),          # +X
        "south": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),  # +Z
        "up":    (u + d, v, u + d + w, v + d),                          # +Y
        "down":  (u + d + w, v, u + d + 2 * w, v + d),                  # -Y
    }


# Como llama el visor a cada cara. Es traduccion, no otra convencion.
NOMBRE_VISOR = {"east": "derecha", "north": "frente", "west": "izquierda",
                "south": "espalda", "up": "arriba", "down": "abajo"}


def _caras(cubo):
    """Las seis casillas ya redondeadas a pixel: (ox, oy, ancho, alto, nombre)."""
    salida = []
    for cara, (u0, v0, u1, v1) in casillas(cubo).items():
        x0, y0 = int(round(u0)), int(round(v0))
        x1, y1 = max(int(round(u1)), x0 + 1), max(int(round(v1)), y0 + 1)
        salida.append((x0, y0, x1 - x0, y1 - y0, NOMBRE_VISOR[cara]))
    return salida


# ⚠⚠ EL REPARTO DE LA TEXTURA SE CALCULA, NO SE ESCRIBE. Es exactamente el
#    fallo que ya nos costo caro en las pantallas: unas coordenadas escritas a
#    mano cuadran hasta que alguien cambia un cubo, y entonces DOS CUBOS
#    COMPARTEN PIXELES -- que no da ningun error, da un traje con la cara de la
#    bota dibujada en el hombro.
def empaquetar(cubos, lado=128, hueco=1, estricto=True):
    """
    Reparte los cubos por la textura y les escribe su `uv`.

    Estanteria simple: se ordenan por altura y se van llenando filas. Devuelve
    el alto usado; si no cabe, lanza (o devuelve None si `estricto` es falso).

    ⚠ El hueco de 1 px no es cortesia: las casillas caen en coordenadas con
      decimales, asi que la ultima puede pasarse del pixel entero. Sin
      separacion, esa cara leeria el borde del cubo de al lado.
    """
    orden = sorted(cubos, key=lambda c: -huella(c)[1])
    x = y = fila = 0
    for c in orden:
        w, h = huella(c)
        if w > lado:
            if not estricto:
                return None
            raise ValueError("un cubo de %s no cabe en %d px de ancho"
                             % (c.tam, lado))
        if x + w > lado:
            x, y, fila = 0, y + fila + hueco, 0
        if y + h > lado:
            if not estricto:
                return None
            raise ValueError("los cubos no caben en %dx%d: hacen falta mas de "
                             "%d px de alto" % (lado, lado, y + h))
        c.uv = (x, y)
        x += w + hueco
        fila = max(fila, h)
    return y + fila


def _mezcla(color, delta):
    return tuple(max(0, min(255, int(c + delta))) for c in color)


def _pintar_cara(px, ox, oy, ancho, alto, cubo, brillo, rng):
    """
    Pinta una cara.

    ⚠ El BORDE de 1 px mas oscuro no es decoracion: sin el, dos cubos pegados
      del mismo color se ven como un bloque unico y el traje pierde la forma.
      Es la misma razon por la que los iconos del mercado llevan trazo grueso.
    """
    base = _mezcla(cubo.color, brillo)
    for j in range(alto):
        for i in range(ancho):
            c = base
            if cubo.material == "tela":
                c = _mezcla(c, rng.randint(-7, 7))
                if j % 3 == 0:
                    c = _mezcla(c, -5)
            elif cubo.material == "metal":
                # una veta vertical clara, que es lo que lee como metal
                c = _mezcla(c, 12 if i == ancho // 3 else rng.randint(-4, 4))
            elif cubo.material == "cuero":
                c = _mezcla(c, rng.randint(-12, 6))
            elif cubo.material == "neon":
                c = cubo.color   # plano: lo que brilla no lleva sombra
            borde = i == 0 or j == 0 or i == ancho - 1 or j == alto - 1
            if borde and cubo.material != "neon":
                c = _mezcla(c, -34)
            px[ox + i, oy + j] = (c[0], c[1], c[2], 255)


# Cuanto se aclara u oscurece cada cara. Sin esto el traje es una silueta
# plana: el volumen de un cubo se ve porque su tapa esta mas clara que su lado.
BRILLO = {"arriba": 22, "abajo": -30, "frente": 6, "espalda": -12,
          "derecha": -6, "izquierda": -6}


def pintar(cubos, lado=128, semilla=0):
    img = Image.new("RGBA", (lado, lado), (0, 0, 0, 0))
    px = img.load()
    rng = random.Random(semilla)
    for c in cubos:
        for ox, oy, aw, ah, cara in _caras(c):
            _pintar_cara(px, ox, oy, aw, ah, c, BRILLO[cara], rng)
    return img


def mascara_brillo(cubos, lado=128):
    """
    La capa que brilla de noche, solo con los cubos `neon`.

    ⚠ Devuelve None si no hay ninguno: un glowmask completamente negro no es
      «no brilla», es un fichero mas que cargar y una via de que algo brille
      por error.
    """
    neones = [c for c in cubos if c.material == "neon"]
    if not neones:
        return None
    img = Image.new("RGBA", (lado, lado), (0, 0, 0, 0))
    px = img.load()
    rng = random.Random(0)
    for c in neones:
        for ox, oy, aw, ah, _ in _caras(c):
            _pintar_cara(px, ox, oy, aw, ah, c, 0, rng)
    return img


# ---------------------------------------------------------------- el .geo.json

def _cubo_json(c):
    cubo = {"origin": [round(v, 4) for v in c.origen],
            "size": [round(v, 4) for v in c.tam],
            "uv": [round(v, 4) for v in c.uv]}
    if c.inflate:
        cubo["inflate"] = round(c.inflate, 4)
    return cubo


def geo(traje, pieza, lado=128):
    """
    El .geo.json de una pieza.

    ⚠⚠⚠ CADA CUBO GIRADO SALE COMO UN HUESO HIJO (`armorHead_r3`). No es una
       floritura del formato: `ModelPart` gira PARTES, nunca cubos sueltos, asi
       que un giro escrito dentro del cubo se ignoraria y la pieza saldria
       RECTA -- sin un solo error, y una corona con las puntas rectas parece un
       cubo. Es exactamente lo que hace Blockbench al exportar (los `bone_r1`).
    """
    huesos = traje.de_pieza(pieza)
    anclas, salida = [], []
    for hueso in huesos:
        padre = PADRES[hueso]
        if padre.startswith("biped") and padre not in anclas:
            anclas.append(padre)
    for a in anclas:
        salida.append({"name": a, "pivot": list(PIVOTES[a])})
    for hueso, cubos in huesos.items():
        b = {"name": hueso, "parent": PADRES[hueso],
             "pivot": list(pivote_de(hueso)), "cubes": []}
        salida.append(b)
        girados = 0
        for c in cubos:
            if not c.rot:
                b["cubes"].append(_cubo_json(c))
                continue
            girados += 1
            salida.append({
                "name": "%s_r%d" % (hueso, girados),
                "parent": hueso,
                "pivot": [round(v, 4) for v in c.pivote],
                "rotation": [round(v, 4) for v in c.rot],
                "cubes": [_cubo_json(c)],
            })
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.unknown",
                "texture_width": lado, "texture_height": lado,
                "visible_bounds_width": 4, "visible_bounds_height": 5,
                "visible_bounds_offset": [0, 1.25, 0],
            },
            "bones": salida,
        }],
    }


def escribir(traje, destino, lado=None):
    """Genera las cuatro piezas: .geo.json + textura (+ glowmask si toca)."""
    hechos = []
    geo_dir = Path(destino) / CARPETA / traje.id
    tex_dir = Path(destino) / "textures" / "armor" / traje.id
    geo_dir.mkdir(parents=True, exist_ok=True)
    tex_dir.mkdir(parents=True, exist_ok=True)
    for pieza in PIEZAS:
        huesos = traje.de_pieza(pieza)
        if not huesos:
            continue
        cubos = [c for lista in huesos.values() for c in lista]
        if traje.fuentes:
            # ⚠ CADA PIEZA SE HORNEA SOLA, y su lienzo es el mas pequeño en que
            #   quepa. Meter las cuatro en el mismo PNG obligaria a repartir las
            #   UV entre ficheros distintos, que es donde se cuelan los fallos
            #   que no dan error.
            imagen, lado_p = hornear(cubos, traje.fuentes)
        else:
            lado_p = lado or traje.lado
            empaquetar(cubos, lado_p)
            imagen = pintar(cubos, lado_p,
                            sum(ord(ch) for ch in traje.id + pieza))
        traje.lados[pieza] = lado_p
        (geo_dir / ("%s_%s.geo.json" % (traje.id, pieza))).write_text(
            json.dumps(geo(traje, pieza, lado_p), indent=2), encoding="utf-8")
        imagen.save(tex_dir / ("%s_%s.png" % (traje.id, pieza)))
        brillo = mascara_brillo(cubos, lado_p)
        if brillo:
            brillo.save(tex_dir / ("%s_%s_glowmask.png" % (traje.id, pieza)))
        hechos.append((pieza, len(cubos), lado_p, brillo is not None))
    return hechos


def _pegar(lienzo, fuentes, cubo):
    """Recorta lo que el autor puso en cada cara y lo pega en su casilla."""
    # ⚠⚠⚠ UNA CAJA SE COPIA ENTERA, SIN MIRAR NI UNA CARA. Ver `importar.caja_uv`:
    #    interpretando cara a cara se DESHACE el `mirror_uv` del autor y el cubo
    #    sale reflejado -- la armadura del brazo pintada por dentro y la piel a
    #    la vista por fuera.
    if cubo.caja_src:
        idx, u, v = cubo.caja_src
        w, h, d = cubo.tam
        ancho = int(math.ceil(2 * d + 2 * w))
        alto = int(math.ceil(d + h))
        trozo = fuentes[idx].crop((int(round(u)), int(round(v)),
                                   int(round(u)) + ancho, int(round(v)) + alto))
        lienzo.paste(trozo, (int(cubo.uv[0]), int(cubo.uv[1])))
        return
    donde = casillas(cubo)
    for cara, (idx, (rect, voltea_u, voltea_v)) in (cubo.caras_src or {}).items():
        if cara not in donde:
            continue
        if idx >= len(fuentes):
            raise IndexError("la cara %s apunta a la textura %d y solo hay %d"
                             % (cara, idx, len(fuentes)))
        u0, v0, u1, v1 = rect
        trozo = fuentes[idx].crop(
            (int(math.floor(u0)), int(math.floor(v0)),
             max(int(math.ceil(u1)), int(math.floor(u0)) + 1),
             max(int(math.ceil(v1)), int(math.floor(v0)) + 1)))
        if voltea_u:
            trozo = trozo.transpose(Image.FLIP_LEFT_RIGHT)
        if voltea_v:
            trozo = trozo.transpose(Image.FLIP_TOP_BOTTOM)
        a0, b0, a1, b1 = donde[cara]
        px0, py0 = int(round(a0)), int(round(b0))
        px1, py1 = max(int(round(a1)), px0 + 1), max(int(round(b1)), py0 + 1)
        if (px1 - px0, py1 - py0) != trozo.size:
            # ⚠ Estirar es lo que ya hacen Blockbench y Minecraft cuando el UV
            #   no mide lo que el cubo -- y la corona lo hace en casi todas sus
            #   caras. NEAREST porque esto es arte de pixel y suavizarlo lo
            #   emborrona.
            trozo = trozo.resize((px1 - px0, py1 - py0), Image.NEAREST)
        lienzo.paste(trozo, (px0, py0))


def hornear(cubos, fuentes, lados=(64, 128, 256, 512, 1024)):
    """
    Reparte y pinta un traje importado: devuelve (imagen, lado).

    ⚠ Sube de tamaño hasta que quepa en vez de recortar. Una textura recortada
      no da ningun error: da un traje con media pieza en blanco, y eso se
      descubre en el juego.
    """
    for lado in lados:
        for c in cubos:
            c.uv = None
        if empaquetar(cubos, lado, estricto=False) is not None:
            lienzo = Image.new("RGBA", (lado, lado), (0, 0, 0, 0))
            for c in cubos:
                _pegar(lienzo, fuentes, c)
            return lienzo, lado
    raise ValueError("los %d cubos no caben ni en %d px"
                     % (len(cubos), lados[-1]))
