#!/usr/bin/env python3
"""
Compone una vista previa de como vera el jugador cada menu.

Existe para no depender de "entra al juego y dime". Apila exactamente lo
mismo que apila el cliente, y en el mismo orden:

    1. la textura del cofre de vanilla   (del jar del cliente)
    2. objetos falsos en las casillas    (se dibujan ANTES del titulo)
    3. nuestro fondo                     (es el titulo, va ENCIMA)

Ese orden es justo el que causo el fallo de los objetos tapados. Aqui se ve.
"""
import sys
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_resourcepack import ANCHO, NS, PANTALLAS, alto_de, huecos

RAIZ = Path(__file__).resolve().parent.parent
GUI = RAIZ / "resourcepack" / "assets" / "lunaeternal" / "textures" / "gui"
SALIDA = RAIZ / "build" / "preview"

CLIENTE = Path("C:/Users/JUAN/AppData/Roaming/PrismLauncher/libraries/"
               "com/mojang/minecraft/1.21.1/minecraft-1.21.1-client.jar")
ESCALA = 3


def cofre_vanilla(filas: int) -> Image.Image:
    """La textura real de vanilla, recortada a las filas que toquen.

    generic_54.png son 256x256 y contiene un cofre de 6 filas. Para menos
    filas, el juego dibuja la parte de arriba y luego pega el trozo del
    inventario del jugador."""
    # Si nuestro pack sustituye la textura, se usa la NUESTRA: es lo que
    # vera el jugador. Si no, se cae a la de vanilla.
    propia = (RAIZ / "build" / "resourcepack" / "assets" / "minecraft"
              / "textures" / "gui" / "container" / "generic_54.png")
    if propia.exists():
        hoja = Image.open(propia).convert("RGBA")
    else:
        with zipfile.ZipFile(CLIENTE) as z:
            nombre = "assets/minecraft/textures/gui/container/generic_54.png"
            with z.open(nombre) as f:
                hoja = Image.open(f).convert("RGBA").copy()

    alto = alto_de(filas)
    fondo = Image.new("RGBA", (ANCHO, alto), (0, 0, 0, 0))
    # Parte de arriba: titulo + las filas del cofre.
    arriba = hoja.crop((0, 0, ANCHO, 17 + filas * 18))
    fondo.paste(arriba, (0, 0))
    # Parte de abajo: inventario del jugador, que en la hoja empieza en y=126.
    abajo = hoja.crop((0, 126, ANCHO, 126 + 97))
    fondo.paste(abajo, (0, 17 + filas * 18))
    return fondo


def objetos_falsos(img: Image.Image, filas: int) -> Image.Image:
    """Cuadrados de colores donde iran los objetos.

    Si tras componer no se ven, el fondo los esta tapando."""
    d = ImageDraw.Draw(img)
    colores = [(220, 80, 80), (240, 190, 60), (90, 200, 120),
               (90, 160, 240), (200, 120, 230)]
    for i, (hx, hy) in enumerate(huecos(filas)):
        c = colores[i % len(colores)]
        d.ellipse([hx + 3, hy + 3, hx + 14, hy + 14], fill=c)
    return img


def componer(nombre: str, filas: int) -> Image.Image | None:
    lienzo = cofre_vanilla(filas)
    lienzo = objetos_falsos(lienzo, filas)

    # La insignia va ENCIMA: forma parte del titulo, que se dibuja al final.
    insignia = (RAIZ / "build" / "resourcepack" / "assets" / NS
                / "textures" / "gui" / f"banner_{nombre}.png")
    if insignia.exists() and "--sin-glifo" not in sys.argv:
        b = Image.open(insignia).convert("RGBA")
        lienzo.alpha_composite(b, (168 - b.width, 1))

    # El titulo de texto, en (8, 6), para comprobar que se lee sobre el arte.
    d = ImageDraw.Draw(lienzo)
    d.text((8, 6), nombre, fill=(255, 255, 255, 255))

    return lienzo.resize((lienzo.width * ESCALA, lienzo.height * ESCALA),
                         Image.NEAREST)


def main() -> None:
    if not CLIENTE.exists():
        raise SystemExit(f"No encuentro el jar del cliente en {CLIENTE}")

    SALIDA.mkdir(parents=True, exist_ok=True)
    pedidas = [a for a in sys.argv[1:] if not a.startswith("--")] or list(PANTALLAS)
    hechas = []
    for nombre in pedidas:
        if nombre not in PANTALLAS:
            continue
        img = componer(nombre, PANTALLAS[nombre])
        if img is None:
            print(f"  {nombre:<12} sin fondo, se omite")
            continue
        p = SALIDA / f"{nombre}.png"
        img.save(p)
        hechas.append(nombre)
        print(f"  {nombre:<12} {p}")

    # Una hoja de contacto con todas, para verlas de un vistazo.
    if len(hechas) > 1:
        imgs = [Image.open(SALIDA / f"{n}.png") for n in hechas]
        ancho = sum(i.width for i in imgs) + 20 * (len(imgs) - 1)
        alto = max(i.height for i in imgs)
        hoja = Image.new("RGBA", (ancho, alto), (30, 30, 34, 255))
        x = 0
        for i in imgs:
            hoja.paste(i, (x, 0))
            x += i.width + 20
        hoja.save(SALIDA / "_todas.png")
        print(f"\n  {SALIDA / '_todas.png'}")


if __name__ == "__main__":
    main()
