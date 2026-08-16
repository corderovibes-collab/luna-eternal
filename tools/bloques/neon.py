#!/usr/bin/env python3
"""La familia NEON: 16 colores x 6 formas = 96 bloques.

Estaba en `tools/gen_neon.py` y se movio aqui sin cambiar un pixel. El motivo
del traslado no es estetico: los dos generadores escriben en el MISMO arbol de
recursos y el viejo lo borraba entero antes de empezar, asi que el segundo en
ejecutarse se llevaba por delante al primero. Ahora hay un solo punto de
entrada (`tools/gen_bloques.py`) y un solo borrado.

BRILLO Y LUZ SON DOS COSAS DISTINTAS, y es la idea entera de la familia:

    luminance        cuanta luz SUELTA al mundo   -> propiedad `luz` (0/7/15)
    emissiveLighting como se DIBUJA a si mismo    -> siempre a tope

Es el truco del bloque de magma de vanilla. Un cartel de neon con `luz=0`
brilla igual de fuerte pero no ilumina la calle, que es justo lo que hace falta
para que una ciudad nocturna siga siendo nocturna con mil neones encendidos.
"""
from __future__ import annotations

from . import comun as C
from .comun import (NS, Escritor, forma_bloque, forma_escalera, forma_losa,
                    forma_pilar, mezcla, rgb)

# ---------------------------------------------------------------------------
# LA PALETA
#
# Son los 16 tintes de vanilla POR NOMBRE (blanco, cian, magenta...) pero con
# los valores subidos a intensidad de neon: mas saturados y mas claros que la
# lana o el hormigon del mismo nombre. Se conservan los nombres de vanilla a
# proposito — un constructor ya sabe donde esta el "cian" sin mirar una tabla.
#
# `negro` no es un neon: es el grafito con el que se enmarca todo lo demas.
# Un neon necesita algo oscuro al lado o no se lee como neon.
#
# ES ADEMAS LA PALETA DE TODO EL MOD: el hormigon y el vidrio de colores usan
# estos mismos 16 tonos rebajados, para que un neon cian pegue con el hormigon
# cian sin que nadie tenga que buscar la pareja.
# ---------------------------------------------------------------------------
PALETA = [
    # id            es              en             color      MapColor
    ("blanco",     "Blanco",       "White",       "#F2FAFF", "WHITE"),
    ("gris_claro", "Gris claro",   "Light Gray",  "#B7C4D2", "LIGHT_GRAY"),
    ("gris",       "Gris",         "Gray",        "#68788A", "GRAY"),
    ("negro",      "Negro",        "Black",       "#1B1E26", "BLACK"),
    ("marron",     "Marron",       "Brown",       "#B06A2E", "BROWN"),
    ("rojo",       "Rojo",         "Red",         "#FF2A3C", "RED"),
    ("naranja",    "Naranja",      "Orange",      "#FF7A14", "ORANGE"),
    ("amarillo",   "Amarillo",     "Yellow",      "#FFE12E", "YELLOW"),
    ("lima",       "Lima",         "Lime",        "#A6FF29", "LIME"),
    ("verde",      "Verde",        "Green",       "#1EDF5C", "GREEN"),
    ("cian",       "Cian",         "Cyan",        "#16F2E6", "CYAN"),
    ("azul_claro", "Azul claro",   "Light Blue",  "#3FBBFF", "LIGHT_BLUE"),
    ("azul",       "Azul",         "Blue",        "#2A5CFF", "BLUE"),
    ("morado",     "Morado",       "Purple",      "#9A34FF", "PURPLE"),
    ("magenta",    "Magenta",      "Magenta",     "#FF34DF", "MAGENTA"),
    ("rosa",       "Rosa",         "Pink",        "#FF8FC6", "PINK"),
]

# Las 6 formas. `sufijo` vacio = el bloque entero.
#
#   bloque    muro y suelo
#   losa      medio bloque
#   escalera  esquinas y rampas
#   pilar     columna con carcasa oscura y una linea de luz
#   panel     chapa de 1 px pegada a cualquiera de las 6 caras: el cartel
#   tubo      barra de 4x4: el tubo de neon de toda la vida
FORMAS = [
    ("",          "{c}",                 "{c} Neon"),
    ("_losa",     "Losa de neon {m}",    "{c} Neon Slab"),
    ("_escalera", "Escalera de neon {m}", "{c} Neon Stairs"),
    ("_pilar",    "Pilar de neon {m}",   "{c} Neon Pillar"),
    ("_panel",    "Panel de neon {m}",   "{c} Neon Panel"),
    ("_tubo",     "Tubo de neon {m}",    "{c} Neon Tube"),
]

# El grafito de la carcasa del pilar. No es negro puro: el negro puro contra un
# neon a tope produce un borde que vibra.
CARCASA = (22, 24, 30)

BLANCO = C.BLANCO

# Los tres niveles de la propiedad `luz`. El indice es el valor del blockstate;
# el numero es la luz que suelta. Tiene que coincidir con NIVELES de Neon.java.
NIVELES = [0, 7, 15]


# ---------------------------------------------------------------------------
# TEXTURAS
# ---------------------------------------------------------------------------

def tex_base(c: tuple):
    """La chapa de neon: marco oscuro, filo brillante y nucleo caliente.

    El marco es lo que hace que una pared de 40 bloques no sea una mancha
    plana: al repetirse dibuja una reticula de paneles.
    """
    img, px = C.lienzo()
    marco = mezcla(c, (0, 0, 0), 0.62)
    for y in range(16):
        for x in range(16):
            m = min(x, y, 15 - x, 15 - y)
            if m == 0:
                col = marco
            elif m == 1:
                col = mezcla(c, (0, 0, 0), 0.18)
            else:
                col = mezcla(c, BLANCO, 0.06 + 0.20 * ((m - 2) / 5.0))
            px[x, y] = (*col, 255)
    return img


def tex_pilar(c: tuple):
    """Cara lateral del pilar: carcasa oscura con una linea de luz vertical."""
    img, px = C.lienzo()
    nucleo = mezcla(c, BLANCO, 0.30)
    for x in range(16):
        d = abs(x - 7.5)
        if d <= 1.5:
            i = 1.0
        elif d <= 2.5:
            i = 0.45
        elif d <= 3.5:
            i = 0.15
        else:
            i = 0.0
        col = mezcla(CARCASA, nucleo, i)
        for y in range(16):
            # Anillos de remate arriba y abajo: al apilar pilares se ve la junta.
            fin = mezcla(col, (0, 0, 0), 0.45) if y in (0, 15) else col
            px[x, y] = (*fin, 255)
    return img


def tex_pilar_tapa(c: tuple):
    """Cara superior/inferior del pilar: el ojo de luz."""
    img, px = C.lienzo()
    nucleo = mezcla(c, BLANCO, 0.30)
    for y in range(16):
        for x in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            if d <= 1.5:
                i = 1.0
            elif d <= 2.5:
                i = 0.45
            elif d <= 3.5:
                i = 0.15
            else:
                i = 0.0
            col = mezcla(CARCASA, nucleo, i)
            if min(x, y, 15 - x, 15 - y) == 0:
                col = mezcla(col, (0, 0, 0), 0.45)
            px[x, y] = (*col, 255)
    return img


def tex_tubo(c: tuple):
    """El tubo: nucleo casi blanco que cae hacia el color por los lados."""
    img, px = C.lienzo()
    nucleo = mezcla(c, BLANCO, 0.58)
    borde = mezcla(c, (0, 0, 0), 0.22)
    for x in range(16):
        d = abs(x - 7.5) / 7.5
        col = mezcla(nucleo, borde, d ** 0.8)
        for y in range(16):
            px[x, y] = (*col, 255)
    return img


# ---------------------------------------------------------------------------
# MODELOS PROPIOS
# ---------------------------------------------------------------------------

# Chapa de 1 px. `facing` es hacia donde MIRA la cara visible, asi que el
# volumen se pega al lado CONTRARIO del bloque.
CAJA_PANEL = {
    "up":    ([0, 0, 0],   [16, 1, 16],  "down"),
    "down":  ([0, 15, 0],  [16, 16, 16], "up"),
    "north": ([0, 0, 15],  [16, 16, 16], "south"),
    "south": ([0, 0, 0],   [16, 16, 1],  "north"),
    "west":  ([15, 0, 0],  [16, 16, 16], "east"),
    "east":  ([0, 0, 0],   [1, 16, 16],  "west"),
}

# Barra de 4x4 centrada en cada eje.
CAJA_TUBO = {
    "y": ([6, 0, 6], [10, 16, 10], ("down", "up")),
    "x": ([0, 6, 6], [16, 10, 10], ("west", "east")),
    "z": ([6, 6, 0], [10, 10, 16], ("north", "south")),
}

CARAS = ("down", "up", "north", "south", "west", "east")


def modelo_elemento(textura: str, desde: list, hasta: list, cull: tuple) -> dict:
    """Modelo de una sola caja.

    Las UV se omiten a proposito: Minecraft las deduce de la posicion de la
    caja. Calcularlas a mano aqui son 24 numeros por modelo y seis formas de
    equivocarse, para acabar en lo mismo.
    """
    caras = {}
    for cara in CARAS:
        f = {"texture": "#neon"}
        if cara in cull:
            f["cullface"] = cara
        caras[cara] = f
    return {
        "ambientocclusion": False,
        "textures": {"particle": textura, "neon": textura},
        "elements": [{"from": desde, "to": hasta, "faces": caras}],
    }


def con_luz(variantes: dict) -> dict:
    """Multiplica un mapa de variantes por los tres valores de `luz`.

    Los tres apuntan al MISMO modelo: `luz` cambia cuanta luz suelta el bloque,
    no como se ve. Minecraft no permite omitir una propiedad de unas variantes
    si aparece en otras, asi que hay que enumerarlas.
    """
    salida = {}
    for clave, valor in variantes.items():
        for nivel in range(len(NIVELES)):
            partes = [p for p in (clave, f"luz={nivel}") if p]
            salida[",".join(partes)] = valor
    return salida


# ---------------------------------------------------------------------------
# GENERACION
# ---------------------------------------------------------------------------

def escritor() -> Escritor:
    """El neon apaga la oclusion ambiental y multiplica sus estados por `luz`."""
    return Escritor(variantes=con_luz, ao=False)


def generar(w: Escritor) -> None:
    for cid, nombre_es, nombre_en, hexa, _ in PALETA:
        _color(w, cid, nombre_es, nombre_en, rgb(hexa))


def _color(w: Escritor, cid: str, nombre_es: str, nombre_en: str, c: tuple) -> None:
    base = f"neon_{cid}"
    tex = w.textura(base, tex_base(c))
    tex_p = w.textura(f"{base}_pilar", tex_pilar(c))
    tex_pt = w.textura(f"{base}_pilar_tapa", tex_pilar_tapa(c))
    tex_t = w.textura(f"{base}_tubo", tex_tubo(c))

    forma_bloque(w, base, tex)
    forma_losa(w, f"{base}_losa", tex, entero=f"{NS}:block/{base}")
    forma_escalera(w, f"{base}_escalera", tex)
    forma_pilar(w, f"{base}_pilar", tex_p, tex_pt)

    # --- panel: la chapa de 1 px, seis orientaciones -----------------------
    variantes = {}
    for cara, (desde, hasta, cull) in CAJA_PANEL.items():
        w.modelo(f"{base}_panel_{cara}", modelo_elemento(tex, desde, hasta, (cull,)))
        variantes[f"facing={cara}"] = {"model": f"{NS}:block/{base}_panel_{cara}"}
    w.item(f"{base}_panel", f"{NS}:block/{base}_panel_up")
    w.estado(f"{base}_panel", variantes)
    w.botin(f"{base}_panel")

    # --- tubo: la barra de 4x4, tres ejes ----------------------------------
    variantes = {}
    for eje, (desde, hasta, cull) in CAJA_TUBO.items():
        w.modelo(f"{base}_tubo_{eje}", modelo_elemento(tex_t, desde, hasta, cull))
        variantes[f"axis={eje}"] = {"model": f"{NS}:block/{base}_tubo_{eje}"}
    w.item(f"{base}_tubo", f"{NS}:block/{base}_tubo_y")
    w.estado(f"{base}_tubo", variantes)
    w.botin(f"{base}_tubo")

    for sufijo, plantilla_es, plantilla_en in FORMAS:
        # "Neon cian" pero "Losa de neon cian": la primera lleva mayuscula al
        # principio y el resto no.
        w.alta(f"{base}{sufijo}",
               plantilla_es.format(c=f"Neon {nombre_es.lower()}",
                                   m=nombre_es.lower()),
               plantilla_en.format(c=nombre_en))


def paleta_java() -> str:
    """`Paleta.java`: la lista de colores tal y como la ve el mod.

    Se genera para que sea IMPOSIBLE que el codigo registre un color cuya
    textura no existe. Si algun dia se anade un color, se anade arriba en
    PALETA y se vuelve a ejecutar el generador; tocar el .java a mano lo pisa.
    """
    filas = ",\n".join(
        f'        new Paleta("{cid}", MapColor.{mapa})'
        for cid, _, _, _, mapa in PALETA)
    return f"""\
package net.pokereport.neon;

import net.minecraft.block.MapColor;

/**
 * Los 16 colores del neón.
 *
 * <p><b>GENERADO por {{@code tools/gen_bloques.py}} — no editar a mano.</b> La
 * lista viva está en la constante {{@code PALETA}} de
 * {{@code tools/bloques/neon.py}}, que además dibuja las texturas y escribe los
 * modelos. Tenerla en dos sitios es como se registra un bloque cuya textura no
 * existe.
 *
 * @param id    parte del identificador: {{@code neon_<id>}}, {{@code neon_<id>_losa}}...
 * @param mapa  color en el mapa y en la brújula de localizador
 */
public record Paleta(String id, MapColor mapa) {{

    public static final Paleta[] COLORES = {{
{filas},
    }};
}}
"""
