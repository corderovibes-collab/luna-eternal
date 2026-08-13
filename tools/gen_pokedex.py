#!/usr/bin/env python3
"""
Genera el resource pack que viste la Pokedex de Cobblemon de azul luna.

QUE HACE, EN UNA FRASE

Lee las 114 texturas de la Pokedex del jar de Cobblemon, les cambia el TONO
--de cian tropical a azul de luna-- y las escribe DENTRO del mod `lunaneon`
como resource pack incrustado. Nada se dibuja a mano: si Cobblemon cambia sus
texturas, se vuelve a ejecutar esto.

POR QUE VA DENTRO DEL MOD Y NO COMO .ZIP SUELTO

Primer intento: un `.zip` en `resourcepacks/` y una linea anadida a la
plantilla `config/yosbr/options.txt`. **No funciono**, y el motivo es
estructural: YOSBR copia esa plantilla **solo si `options.txt` no existe**. A
quien ya ha jugado una vez no le llega nunca. Se instalaba el pack y se quedaba
apagado.

La via buena estaba delante: en el `options.txt` de cualquier jugador se leen
`cobblemon:gyaradosjump` y `cobblemon:regionbiasforms`. Son resource packs que
Cobblemon lleva DENTRO de su jar y registra con `DEFAULT_ENABLED`. Minecraft los
activa solo la primera vez que los ve, tenga el jugador el `options.txt` que
tenga, y ademas los deja apagables desde el menu de siempre.

EL LIMITE, Y POR QUE EL FONDO NO PUEDE SER OSCURO

El texto de la Pokedex **lo pinta el codigo**, no la textura. Son dos colores
fijos dentro de Cobblemon:

    0x606B6E   gris,     16 usos   los numeros, los nombres, las descripciones
    0x3A96B6   turquesa,  3 usos   acentos

Y ese gris va **encima de los paneles claros**. Un resource pack no puede
tocarlo, asi que si oscurecieramos la pantalla el texto quedaria ilegible y no
habria forma de arreglarlo desde aqui.

De ahi la regla de este script:

    la pantalla cambia de TONO pero conserva la LUMINANCIA

El turquesa fijo, ademas, encaja de forma natural en una paleta azul, asi que
los acentos siguen pegando en vez de chocar.

QUE SE TOCA Y QUE NO

Dos filtros, y los dos evitan tener que mantener listas a mano:

  * **Solo la pantalla.** La carcasa (`pokedex_base_*`) no se toca: su color es
    el del objeto Pokedex --hay siete-- y lo elige el jugador. Ver SIN_TOCAR.
  * **Solo los pixeles cian** (tono 165-205 grados). Asi las plataformas de
    tipo --fuego naranja, planta verde-- se quedan como estan sin listarlas.

LICENCIA

Cobblemon es MPL-2.0, que permite obra derivada y uso comercial (al contrario
que la CC-BY-NC-ND de stendhal, D-031). El MPL es copyleft POR FICHERO: estas
texturas derivan de las suyas, asi que siguen siendo MPL-2.0 y el pack lleva el
aviso dentro. Ver LICENSE-COBBLEMON.txt en la raiz del pack generado.

Uso:
    python tools/gen_pokedex.py                # escribe en neon/src/main/resources
    python tools/gen_pokedex.py --comparativa  # ademas una imagen antes/despues

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
ID_PACK = "pokedex_luna"
DESTINO = RAIZ / "neon" / "src" / "main" / "resources" / "resourcepacks" / ID_PACK
RUTA_TEX = "assets/cobblemon/textures/gui/pokedex/"

# `pack_format` de Minecraft 1.21.1. Un numero equivocado aqui hace que el juego
# marque el pack como incompatible y lo esconda; no falla, simplemente no
# aparece, que es peor.
PACK_FORMAT = 34

# La banda de tono que consideramos "cian de la interfaz", en grados.
CIAN = (165, 205)

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


def a_luna(im: Image.Image) -> Image.Image:
    im = im.convert("RGBA")
    px = im.load()
    tocados = 0
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if not (CIAN[0] <= h * 360 <= CIAN[1]):
                continue              # no es cian: plataformas de tipo, iconos
            # Se conserva v a proposito: es lo que mantiene legible el texto
            # que pinta el codigo.
            nr, ng, nb = colorsys.hsv_to_rgb(TONO_LUNA, min(s, SATURACION_MAX), v)
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
    nombres = [n for n in z.namelist()
               if n.startswith(RUTA_TEX) and n.endswith(".png")]

    # Se borra antes: si Cobblemon retira una textura, la nuestra tiene que
    # desaparecer o quedaria revistiendo algo que ya no existe.
    if DESTINO.exists():
        shutil.rmtree(DESTINO)
    DESTINO.mkdir(parents=True, exist_ok=True)

    (DESTINO / "pack.mcmeta").write_text(json.dumps({
        "pack": {
            "pack_format": PACK_FORMAT,
            "description": "PokeReport : Luna Eternal — Pokedex a luz de luna",
        }
    }, indent=2, ensure_ascii=False), encoding="utf-8")
    (DESTINO / "LICENSE-COBBLEMON.txt").write_text(AVISO, encoding="utf-8")

    total_px, incluidas, intactas = 0, 0, []
    antes_despues = {}
    for n in nombres:
        corto = n[len(RUTA_TEX):]
        if corto.startswith(SIN_TOCAR):
            intactas.append(corto)
            continue          # la carcasa es del jugador: ver SIN_TOCAR
        original = Image.open(io.BytesIO(z.read(n)))
        nueva, tocados = a_luna(original)
        total_px += tocados
        if tocados == 0:
            intactas.append(corto)
            continue          # sin cambios: no se incluye, solo pesaria
        destino = DESTINO / n
        destino.parent.mkdir(parents=True, exist_ok=True)
        nueva.save(destino, "PNG")
        incluidas += 1
        if corto == "pokedex_screen.png":
            antes_despues[corto] = (original.convert("RGBA"), nueva)

    peso = sum(p.stat().st_size for p in DESTINO.rglob("*") if p.is_file())
    print(f"  {incluidas} texturas revestidas, {len(intactas)} intactas")
    print(f"  ({total_px:,} pixeles cambiados)".replace(",", "."))
    print(f"  -> {DESTINO.relative_to(RAIZ)}  ({peso // 1024} KB)")
    print("     recompila el mod:  cd neon && bash build.sh")

    if comparativa and antes_despues:
        _comparativa(antes_despues)
    return DESTINO


def _comparativa(pares: dict) -> None:
    """La pantalla, antes y despues. El casco no sale: no se toca."""
    antes, despues = pares["pokedex_screen.png"]
    E = 2
    ancho, alto = antes.size
    hoja = Image.new("RGB", (ancho * E * 2 + 24, alto * E + 16), (14, 14, 20))
    for i, im in enumerate((antes, despues)):
        esc = im.resize((ancho * E, alto * E), Image.NEAREST)
        hoja.paste(esc, (8 + i * (ancho * E + 8), 8), esc)
    ruta = SALIDA / "pokedex-comparativa.png"
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
