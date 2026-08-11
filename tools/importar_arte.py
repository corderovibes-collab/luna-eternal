#!/usr/bin/env python3
"""
Convierte una imagen cualquiera en un fondo de menu valido.

Existe porque ninguna IA de imagenes devuelve un PNG de 176x222 con 54
casillas transparentes en su sitio. Este script hace ese trabajo:

    arte-origen/almanaque.png   (lo que sea, 1024x1024)
              |
              v  recorta al aspecto correcto, escala, perfora
    resourcepack/assets/lunaeternal/textures/gui/almanaque.png

Uso:
    python tools/importar_arte.py              todas las que encuentre
    python tools/importar_arte.py almanaque    solo esa
"""
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_resourcepack import (ANCHO, PANTALLAS, alto_de, casillas_tapadas,
                              huecos)

RAIZ = Path(__file__).resolve().parent.parent
ORIGEN = RAIZ / "arte-origen"
DESTINO = RAIZ / "resourcepack" / "assets" / "lunaeternal" / "textures" / "gui"


def recortar_al_aspecto(img: Image.Image, w: int, h: int) -> Image.Image:
    """Recorta CENTRADO al aspecto de destino, sin deformar.

    Deformar un marco ornamentado se nota muchisimo: los circulos se vuelven
    elipses. Es preferible perder un trozo de los bordes."""
    objetivo = w / h
    actual = img.width / img.height
    if actual > objetivo:
        nuevo_w = round(img.height * objetivo)
        izq = (img.width - nuevo_w) // 2
        img = img.crop((izq, 0, izq + nuevo_w, img.height))
    else:
        nuevo_h = round(img.width / objetivo)
        arr = (img.height - nuevo_h) // 2
        img = img.crop((0, arr, img.width, arr + nuevo_h))
    # LANCZOS al reducir mucho conserva mejor los detalles finos del marco.
    return img.resize((w, h), Image.LANCZOS)


def perforar(img: Image.Image, filas: int) -> Image.Image:
    """Pone a alpha 0 las 18x18 de cada casilla.

    Sin esto el fondo tapa los objetos: el titulo se dibuja DESPUES de ellos.
    Se deja un marco de 1 px semitransparente para que la casilla siga
    leyendose como casilla."""
    img = img.convert("RGBA")
    pix = img.load()
    for (hx, hy) in huecos(filas):
        for x in range(hx, hx + 18):
            for y in range(hy, hy + 18):
                if not (0 <= x < img.width and 0 <= y < img.height):
                    continue
                borde = x in (hx, hx + 17) or y in (hy, hy + 17)
                if borde:
                    r, g, b, _ = pix[x, y]
                    pix[x, y] = (r, g, b, 70)
                else:
                    pix[x, y] = (0, 0, 0, 0)
    return img


def procesar(nombre: str, filas: int) -> bool:
    origen = None
    for ext in (".png", ".jpg", ".jpeg", ".webp"):
        p = ORIGEN / f"{nombre}{ext}"
        if p.exists():
            origen = p
            break
    if origen is None:
        return False

    alto = alto_de(filas)
    img = Image.open(origen).convert("RGBA")
    img = recortar_al_aspecto(img, ANCHO, alto)
    img = perforar(img, filas)

    tapadas = casillas_tapadas(img, filas)
    if tapadas:
        print(f"  {nombre:<12} ERROR: {tapadas} casillas siguen tapadas")
        return False

    DESTINO.mkdir(parents=True, exist_ok=True)
    salida = DESTINO / f"{nombre}.png"
    img.save(salida)
    print(f"  {nombre:<12} {origen.name} -> {ANCHO}x{alto}, casillas perforadas")
    return True


def main() -> None:
    if not ORIGEN.exists():
        ORIGEN.mkdir(parents=True)
        print(f"Creada {ORIGEN}")
        print("Pon ahi las imagenes con el nombre de cada pantalla:")
        print("  " + ", ".join(f"{n}.png" for n in PANTALLAS))
        return

    pedidas = sys.argv[1:] or list(PANTALLAS)
    hechas = 0
    for nombre in pedidas:
        if nombre not in PANTALLAS:
            print(f"  {nombre}: no es una pantalla conocida, se omite")
            continue
        if procesar(nombre, PANTALLAS[nombre]):
            hechas += 1

    if hechas == 0:
        print(f"No habia nada que importar en {ORIGEN}")
        print("Nombres esperados: " + ", ".join(PANTALLAS))
    else:
        print(f"\n{hechas} fondos importados.")
        print("Ahora: python tools/gen_resourcepack.py")


if __name__ == "__main__":
    main()
