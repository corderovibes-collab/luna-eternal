#!/usr/bin/env python3
"""
Genera el resource pack de Luna Eternal (D-023).

La idea, en una frase: **el titulo de un menu de cofre se escribe con una
fuente propia cuyos caracteres son imagenes**, asi que el menu deja de
parecer un menu de cofre.

Como funciona
-------------
Un resource pack puede definir fuentes. Una fuente tiene "providers":

  space   asigna a ciertos caracteres una anchura, que puede ser NEGATIVA.
          Sirve para mover el cursor hacia atras y dibujar donde quieras.
  bitmap  asigna a un caracter un PNG. El caracter ya no es una letra:
          es una imagen.

Combinando los dos: retrocedes con espacios negativos hasta el borde del
menu y pintas tu fondo. El servidor solo envia texto — no hay mod de cliente
y P6 queda intacto (el servidor sigue controlandolo todo).

Geometria de un menu de cofre (pixeles de interfaz, escala 1)
-------------------------------------------------------------
El titulo se dibuja en (8, 6) respecto de la esquina superior izquierda del
menu. Para llegar al borde hay que retroceder 8. La linea base del texto cae
7 px por debajo del borde superior del area de titulo, y de ahi sale ASCENT.

No se escribe nada a mano: este script genera el JSON de la fuente y el zip.
"""
import hashlib
import json
import shutil
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw

RAIZ = Path(__file__).resolve().parent.parent
FUENTES = RAIZ / "resourcepack"          # PNG de origen, editables a mano
SALIDA = RAIZ / "build"
NS = "lunaeternal"

# 34 = formato de pack para 1.21.1. Si sube MC, sube esto.
PACK_FORMAT = 34

# Zona de uso privado: estos codepoints no significan nada en Unicode, asi
# que nadie los va a escribir por accidente en el chat.
BASE_BITMAP = 0xE000

# Anchuras de espacio negativo que hacen falta. Con potencias de dos se
# compone cualquier desplazamiento sumando unos pocos caracteres.
DESPLAZAMIENTOS = [-1024, -512, -256, -128, -64, -32, -16, -8, -4, -2, -1,
                   1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024]
BASE_SPACE = 0xE100


def codepoint_espacio(px: int) -> str:
    """Caracter que desplaza `px` pixeles. Negativo = hacia atras."""
    return chr(BASE_SPACE + DESPLAZAMIENTOS.index(px))


def desplazar(px: int) -> str:
    """Cadena que desplaza exactamente `px` pixeles, sumando potencias de dos.

    Se usa para colocar el fondo: hay que retroceder 8 px desde donde el
    juego dibuja el titulo hasta el borde izquierdo del menu."""
    salida = []
    restante = px
    for d in sorted(DESPLAZAMIENTOS, key=lambda x: -abs(x)):
        while (restante <= d < 0) or (0 < d <= restante):
            salida.append(codepoint_espacio(d))
            restante -= d
    if restante:
        raise ValueError(f"no se puede componer un desplazamiento de {px} px")
    return "".join(salida)


# --------------------------------------------------------------- texturas

def huecos(filas: int) -> list[tuple[int, int]]:
    """Esquina superior izquierda de cada casilla, en pixeles de interfaz.

    IMPORTANTE. El titulo se dibuja DESPUES de los objetos, asi que nuestro
    fondo queda POR ENCIMA de ellos. Si es opaco, tapa el inventario entero.
    Por eso cada casilla tiene que ser un agujero transparente.

    Geometria de GenericContainerScreenHandler (vanilla):
      cofre    x = 8 + col*18   y = 18 + fila*18
      mochila  x = 8 + col*18   y = 103 + fila*18 + (filas-4)*18
      barra    x = 8 + col*18   y = 161 + (filas-4)*18
    El hueco visible es de 18x18 y empieza un pixel antes.
    """
    i = (filas - 4) * 18
    ys = [18 + f * 18 for f in range(filas)]           # cofre
    ys += [103 + f * 18 + i for f in range(3)]         # mochila
    ys += [161 + i]                                    # barra rapida
    return [(8 + c * 18 - 1, y - 1) for y in ys for c in range(9)]


def casillas_tapadas(img: Image.Image, filas: int) -> int:
    """Cuantas casillas tienen pixeles opacos en el centro. Debe ser 0."""
    rgba = img.convert("RGBA")
    malas = 0
    for (hx, hy) in huecos(filas):
        # Se mira el centro, no el borde: el borde puede llevar marco.
        for dx in (6, 9, 12):
            for dy in (6, 9, 12):
                if rgba.getpixel((hx + dx, hy + dy))[3] > 8:
                    malas += 1
                    break
            else:
                continue
            break
    return malas


def marcador(ancho: int, alto: int, filas: int, titulo: str) -> Image.Image:
    """Textura de relleno hasta que exista arte de verdad (ART-001).

    No es decorativa: el marco y las marcas cada 18 px permiten comprobar que
    la alineacion del espacio negativo es exacta al pixel. Si el fondo baila
    uno solo, aqui se ve."""
    img = Image.new("RGBA", (ancho, alto), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, ancho - 1, alto - 1], fill=(24, 20, 37, 235),
                outline=(120, 96, 200, 255))
    d.rectangle([1, 1, ancho - 2, alto - 2], outline=(60, 48, 100, 255))
    for x in range(0, ancho, 18):
        d.line([(x, 0), (x, 3)], fill=(120, 96, 200, 160))
    d.text((5, 4), titulo[:28], fill=(226, 214, 255, 255))

    # Los agujeros van al final: se pintan a alpha 0 para que los objetos se
    # vean a traves. Con un marco tenue alrededor, para que sigan pareciendo
    # casillas.
    for (hx, hy) in huecos(filas):
        d.rectangle([hx, hy, hx + 17, hy + 17], fill=(0, 0, 0, 0))
        d.rectangle([hx, hy, hx + 17, hy + 17], outline=(120, 96, 200, 90))
    return img


# Geometria de un cofre, en pixeles de interfaz:
#   ancho  = 176 siempre
#   alto   = 17 (barra de titulo) + filas*18 (casillas) + 97 (inventario)
ANCHO = 176


def alto_de(filas: int) -> int:
    return 114 + filas * 18


# La linea base del titulo cae 13 px por debajo del borde superior del menu
# (el titulo se dibuja en y=6 y la fuente de Minecraft tiene ascenso 7).
# Poner ASCENT ahi hace que el borde superior de la textura coincida
# exactamente con el borde del menu.
ASCENT = 13

# Pantalla -> filas del cofre. Tiene que COINCIDIR con el super(...) del menu
# en Java; si no, el fondo queda corto o sobresale.
PANTALLAS = {
    "almanaque": 6,
    "cartera":   6,
    "vias":      6,
    "misiones":  6,
    "gts":       6,
    "pokedex":   6,
    "puerta":    5,
    "tienda":    4,
    "kits":      4,
    "centro":    3,
}


def asegurar_texturas() -> None:
    """Crea los PNG de relleno que falten. NO pisa los que ya existan:
    en cuanto haya arte real, este script deja de tocarlos."""
    destino = FUENTES / "assets" / NS / "textures" / "gui"
    destino.mkdir(parents=True, exist_ok=True)
    for nombre, filas in PANTALLAS.items():
        h = alto_de(filas)
        p = destino / f"{nombre}.png"
        if p.exists():
            img = Image.open(p)
            if img.size != (ANCHO, h):
                raise SystemExit(
                    f"{p.name} mide {img.size} y deberia medir {(ANCHO, h)} "
                    f"({filas} filas). Un pixel de mas descuadra todo el fondo.")
            opacos = casillas_tapadas(img, filas)
            if opacos:
                raise SystemExit(
                    f"{p.name}: {opacos} casillas NO son transparentes. "
                    f"El titulo se dibuja DESPUES de los objetos, asi que un "
                    f"fondo opaco los tapa. Cada casilla de 18x18 tiene que "
                    f"tener alpha 0. Ver docs/ui/art-brief.md")
            print(f"  {nombre:<12} arte propio {img.size[0]}x{img.size[1]}, casillas OK")
            continue
        marcador(ANCHO, h, filas, nombre.upper()).save(p)
        print(f"  {nombre:<12} marcador generado {ANCHO}x{h} ({filas} filas)")


# --------------------------------------------------------------- fuente

def construir_fuente() -> dict:
    """El JSON de la fuente: un provider `space` y un `bitmap` por pantalla."""
    providers = [{
        "type": "space",
        "advances": {codepoint_espacio(px): px for px in DESPLAZAMIENTOS},
    }]
    for i, (nombre, filas) in enumerate(PANTALLAS.items()):
        providers.append({
            "type": "bitmap",
            "file": f"{NS}:gui/{nombre}.png",
            "ascent": ASCENT,
            "height": alto_de(filas),
            "chars": [chr(BASE_BITMAP + i)],
        })
    return {"providers": providers}


def caracteres_pantalla() -> dict:
    """Lo que necesita el mod: para cada pantalla, la cadena exacta que
    dibuja su fondo. Se exporta a JSON para no duplicar la geometria en
    Java."""
    out = {}
    for i, nombre in enumerate(PANTALLAS):
        # -8 lleva del punto donde se dibuja el titulo al borde del menu.
        # Un glifo avanza su anchura + 1, asi que hay que retroceder eso para
        # que el titulo de verdad salga en su sitio y no desplazado 177 px.
        out[nombre] = (desplazar(-8) + chr(BASE_BITMAP + i)
                       + desplazar(-(ANCHO + 1)) + desplazar(8))
    return out


# --------------------------------------------------------------- empaquetado

def main() -> None:
    print("Texturas")
    asegurar_texturas()

    raiz = SALIDA / "resourcepack"
    if raiz.exists():
        shutil.rmtree(raiz)
    shutil.copytree(FUENTES, raiz)

    (raiz / "pack.mcmeta").write_text(json.dumps({
        "pack": {
            "pack_format": PACK_FORMAT,
            "description": "§6PokeReport §f: §bLuna Eternal",
        }
    }, indent=2), encoding="utf-8")

    fuente = raiz / "assets" / NS / "font" / "gui.json"
    fuente.parent.mkdir(parents=True, exist_ok=True)
    fuente.write_text(json.dumps(construir_fuente(), indent=2), encoding="utf-8")

    SALIDA.mkdir(parents=True, exist_ok=True)
    zip_path = SALIDA / "lunaeternal-resourcepack.zip"
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
        for f in sorted(raiz.rglob("*")):
            if f.is_file():
                z.write(f, f.relative_to(raiz).as_posix())

    sha1 = hashlib.sha1(zip_path.read_bytes()).hexdigest()

    # El mod lee esto para no repetir la geometria en Java.
    mapa = RAIZ / "mod" / "src" / "main" / "resources" / "gui_chars.json"
    mapa.parent.mkdir(parents=True, exist_ok=True)
    mapa.write_text(json.dumps(caracteres_pantalla(), indent=2,
                               ensure_ascii=False), encoding="utf-8")

    print(f"\n{zip_path}")
    print(f"  {zip_path.stat().st_size // 1024} KB · {len(PANTALLAS)} pantallas")
    print(f"  sha1 = {sha1}")
    print(f"\nPara server.properties:")
    print(f"  resource-pack-sha1={sha1}")


if __name__ == "__main__":
    main()
