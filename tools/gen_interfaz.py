#!/usr/bin/env python3
"""
Genera el resource pack que viste la interfaz de Cobblemon con la identidad
del servidor.

QUE HACE, EN UNA FRASE

Lee las texturas de interfaz del jar de Cobblemon, les cambia el color y las
escribe DENTRO del mod `lunaneon` como resource pack incrustado. Nada se dibuja
a mano: si Cobblemon cambia sus texturas, se vuelve a ejecutar esto.

DOS TRANSFORMACIONES, PORQUE HAY DOS FAMILIAS DE PANTALLA

  cian   La Pokedex y su item son CIAN. Se desplaza el tono de los pixeles en
         la banda del cian (165-205 grados) hacia el azul de luna. Todo lo que
         no sea cian se queda: las plataformas de tipo (fuego naranja, planta
         verde) y las carcasas de colores no hay ni que listarlas.

  gris   El resumen, el PC, el combate, el equipo... son 100 % GRISES: cero
         pixeles con color. Ahi no hay tono que desplazar, asi que se TINE:
         se les pone tono de luna y una pizca de saturacion.

Las dos conservan la LUMINANCIA, y esa es la regla que lo gobierna todo.

EL LIMITE, Y POR QUE NADA SE PUEDE OSCURECER

El texto **lo pinta el codigo**, no la textura. En la Pokedex son dos colores
fijos dentro de Cobblemon:

    0x606B6E   gris,     16 usos   los numeros, los nombres, las descripciones
    0x3A96B6   turquesa,  3 usos   acentos

Y ese gris va **encima de los paneles claros**. Un resource pack no puede
tocarlo, asi que si oscurecieramos el fondo el texto quedaria ilegible y no
habria forma de arreglarlo desde aqui. Conservando la luminancia, el contraste
sale exactamente igual que antes.

QUE NO SE TOCA

La carcasa de la Pokedex (`pokedex_base_*`). Hay SIETE Pokedex de colores
--roja, azul, verde, rosa, negra, blanca, amarilla-- y son objetos distintos:
el color lo elige el jugador. Se probo tenirla y, visto en el juego, la
decision fue dejarla como Cobblemon la hizo.

LICENCIA

Cobblemon es MPL-2.0, que permite obra derivada y uso comercial (al contrario
que la CC-BY-NC-ND de stendhal, D-031). El MPL es copyleft POR FICHERO: estas
texturas derivan de las suyas, asi que siguen siendo MPL-2.0 y el pack lleva el
aviso dentro. Ver LICENSE-COBBLEMON.txt en la raiz del pack generado.

Uso:
    python tools/gen_interfaz.py                # escribe en neon/src/main/resources
    python tools/gen_interfaz.py --comparativa  # ademas una imagen antes/despues

Despues hay que recompilar el mod:  cd neon && bash build.sh
"""
import argparse
import colorsys
import io
import json
import shutil
import sys
import zipfile
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_modpack import SALIDA, bajar, version_de  # noqa: E402

RAIZ = Path(__file__).resolve().parent.parent

# El pack va incrustado en el jar de `lunaneon`, que ya llega a todos los
# clientes. La ruta la fija Fabric: `resourcepacks/<ruta del Identifier>`.
ID_PACK = "interfaz_luna"
DESTINO = RAIZ / "neon" / "src" / "main" / "resources" / "resourcepacks" / ID_PACK

# `pack_format` de Minecraft 1.21.1. Un numero equivocado aqui hace que el juego
# marque el pack como incompatible y lo esconda; no falla, simplemente no
# aparece, que es peor.
PACK_FORMAT = 34

# QUE SE REVISTE, con que transformacion y que se salta.
#
# Anadir una pantalla es una linea aqui. El modo sale de mirar la textura: si
# tiene pixeles cian es `cian`, si es puro gris es `gris`.
REVESTIDOS = [
    ("textures/gui/pokedex/",   "cian", ("pokedex_base_",)),
    # El item que llevas en la mano: marco del color de cada Pokedex y pantalla
    # cian. El filtro por tono resuelve solo: tine la pantalla y respeta el marco.
    ("textures/item/pokedexes/", "cian", ()),
    ("textures/gui/summary/",   "gris", ()),
    ("textures/gui/pc/",        "gris", ()),
    ("textures/gui/battle/",    "gris", ()),
    ("textures/gui/party/",     "gris", ()),
    ("textures/gui/trade/",     "gris", ()),
    ("textures/gui/interact/",  "gris", ()),
    ("textures/gui/pasture/",   "gris", ()),
]

# La banda de tono que consideramos "cian de la interfaz", en grados.
CIAN = (165, 205)

# Cuanto tinte se le da a las pantallas grises. Elegido mirando las cuatro
# intensidades en el juego: 0.15 no se notaba, 0.34 cansaba en una pantalla
# llena de datos.
TINTE_GRIS = 0.24

# Por debajo de esta saturacion un pixel se considera gris. Sube de 0 para
# incluir los grises "sucios" que no son exactamente neutros.
UMBRAL_GRIS = 0.12

# Destino: azul de luna. Menos saturado que el cian original — la luz de luna
# es fria y lavada, no un neon.
TONO_LUNA = 232 / 360
SATURACION_MAX = 0.46

# EL CASCO NO SE TOCA.
#
# Se reviste la pantalla, no la carcasa. Cobblemon tiene SIETE Pokedex de
# colores distintos --roja, azul, verde, rosa, negra, blanca, amarilla-- y son
# objetos separados: el color es una eleccion del jugador, no decoracion
# nuestra. Pisarlo le quitaba sentido a tener siete.
#
# Se probo tenirlo al 70 % hacia el azul y, visto en el juego, la decision del
# usuario fue clara: la carcasa se queda como Cobblemon la hizo.
SIN_TOCAR = ("pokedex_base_",)


def jar_cobblemon() -> bytes:
    """El jar de Cobblemon, cacheado en build/.

    Se baja de Modrinth en vez de leerlo de una carpeta de la maquina: asi esto
    funciona en cualquier equipo y siempre contra la version que sirve el pack.
    """
    cache = SALIDA / "cobblemon-para-texturas.jar"
    if not cache.exists():
        v = version_de("cobblemon")
        print(f"  bajando Cobblemon {v['version_number']} "
              f"({v['files'][0]['size'] // 1048576} MB)...")
        SALIDA.mkdir(parents=True, exist_ok=True)
        cache.write_bytes(bajar(v["files"][0]["url"]))
    return cache.read_bytes()


def a_luna(im: Image.Image, modo: str):
    """Devuelve (imagen, pixeles cambiados). Conserva la luminancia siempre."""
    im = im.convert("RGBA")
    px = im.load()
    tocados = 0
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if modo == "cian":
                if not (CIAN[0] <= h * 360 <= CIAN[1]):
                    continue          # no es cian: plataformas de tipo, marcos
                nueva_s = min(s, SATURACION_MAX)
            else:
                if s > UMBRAL_GRIS:
                    continue          # ya tiene color: un acento, no el cromo
                nueva_s = TINTE_GRIS
            # `v` intacto: es lo que mantiene legible el texto que pinta el codigo.
            nr, ng, nb = colorsys.hsv_to_rgb(TONO_LUNA, nueva_s, v)
            px[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
            tocados += 1
    return im, tocados


AVISO = """\
Texturas derivadas de Cobblemon (https://cobblemon.com), (C) Cobblemon
Contributors, distribuidas bajo la Mozilla Public License 2.0.

Este pack contiene versiones MODIFICADAS de esas texturas: se les ha cambiado
el tono para la identidad visual del servidor PokeReport: Luna Eternal. Al ser
el MPL copyleft por fichero, esas texturas modificadas siguen siendo MPL-2.0.

Texto completo de la licencia: https://mozilla.org/MPL/2.0/
"""


def generar(comparativa: bool = False) -> Path:
    z = zipfile.ZipFile(io.BytesIO(jar_cobblemon()))

    # Se borra antes: si Cobblemon retira una textura, la nuestra tiene que
    # desaparecer o quedaria revistiendo algo que ya no existe.
    if DESTINO.exists():
        shutil.rmtree(DESTINO)
    DESTINO.mkdir(parents=True, exist_ok=True)

    (DESTINO / "pack.mcmeta").write_text(json.dumps({
        "pack": {
            "pack_format": PACK_FORMAT,
            "description": "PokeReport : Luna Eternal — interfaz a luz de luna",
        }
    }, indent=2, ensure_ascii=False), encoding="utf-8")
    (DESTINO / "LICENSE-COBBLEMON.txt").write_text(AVISO, encoding="utf-8")

    total_px, total_inc, total_int = 0, 0, 0
    antes_despues = {}
    for ruta, modo, saltar in REVESTIDOS:
        prefijo = "assets/cobblemon/" + ruta
        nombres = [n for n in z.namelist()
                   if n.startswith(prefijo) and n.endswith(".png")]
        inc, intactas, px = 0, 0, 0
        for n in nombres:
            corto = n[len(prefijo):]
            if saltar and corto.startswith(saltar):
                intactas += 1
                continue          # ver SIN_TOCAR / la columna de REVESTIDOS
            original = Image.open(io.BytesIO(z.read(n)))
            nueva, tocados = a_luna(original, modo)
            if tocados == 0:
                intactas += 1
                continue          # sin cambios: no se incluye, solo pesaria
            destino = DESTINO / n
            destino.parent.mkdir(parents=True, exist_ok=True)
            nueva.save(destino, "PNG")
            inc += 1
            px += tocados
            if corto in ("pokedex_screen.png", "summary_base.png"):
                antes_despues[corto] = (original.convert("RGBA"), nueva)
        total_px += px
        total_inc += inc
        total_int += intactas
        print(f"  {modo:<5} {ruta.replace('textures/', ''):<22} "
              f"{inc:>3} revestidas, {intactas:>3} intactas")

    peso = sum(p.stat().st_size for p in DESTINO.rglob("*") if p.is_file())
    print()
    print(f"  TOTAL {total_inc} texturas revestidas, {total_int} intactas")
    print(f"  ({total_px:,} pixeles cambiados)".replace(",", "."))
    print(f"  -> {DESTINO.relative_to(RAIZ)}  ({peso // 1024} KB)")
    print("     recompila el mod:  cd neon && bash build.sh")

    if comparativa and antes_despues:
        _comparativa(antes_despues)
    return DESTINO


def _comparativa(pares: dict) -> None:
    """Antes y despues de una textura de cada familia."""
    filas = []
    for nombre, (antes, despues) in sorted(pares.items()):
        filas.append((nombre, antes, despues))
    E = 2
    ancho = max(a.size[0] for _, a, _ in filas) * E * 2 + 24
    alto = sum(a.size[1] * E + 16 for _, a, _ in filas)
    hoja = Image.new("RGB", (ancho, alto), (14, 14, 20))
    y = 8
    for _, antes, despues in filas:
        w, h = antes.size
        for i, im in enumerate((antes, despues)):
            esc = im.resize((w * E, h * E), Image.NEAREST)
            hoja.paste(esc, (8 + i * (w * E + 8), y), esc)
        y += h * E + 16
    ruta = SALIDA / "interfaz-comparativa.png"
    hoja.save(ruta)
    print(f"  -> {ruta.relative_to(RAIZ)}  (antes | despues)")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--comparativa", action="store_true",
                    help="genera ademas una imagen antes/despues")
    args = ap.parse_args()
    print("POKEDEX A LUZ DE LUNA")
    generar(args.comparativa)


if __name__ == "__main__":
    main()
