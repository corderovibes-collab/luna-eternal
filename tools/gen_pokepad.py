#!/usr/bin/env python3
"""
Prepara el arte del PokePad y lo deja listo para el juego.

QUE HACE

El arte llega ya cortado, una imagen por pieza y al tamano correcto. Este
script hace lo poco que falta:

    arte/pokepad/fondo_base.png   346x207  ->  pokepad.png       346x207
    arte/pokepad/icons/*.png       25x25   ->  <nombre>.png       25x25
    arte/pokepad/botones/*.png     30x24   ->  boton_<nombre>.png 30x24

Y sobre todo mide **donde cae la pantalla azul** dentro del chasis, que es el
numero que congela la composicion: el codigo dibuja la rejilla ahi y no vuelve
a medir.

POR QUE SIGUE HACIENDO FALTA UN SCRIPT SI EL ARTE YA VIENE CORTADO

Por dos cosas que el arte generado con IA trae casi siempre, y que no se ven
hasta que estan dentro del juego:

  fondo opaco   Alguna pieza llega con un rectangulo blanco detras en vez de
                transparencia --a `gts` le paso--. Colado, en el juego se ve un
                recuadro blanco alrededor del icono.
  bordes suaves Un borde medio transparente se ve como un halo sucio cuando
                Minecraft amplia la interfaz. El alfa se vuelve binario: un
                pixel esta o no esta, que es como funciona el pixel art.

Las dos se arreglan aqui, asi que al generar arte no hay que acordarse de nada.

Uso:
    python tools/gen_pokepad.py             # prepara todo
    python tools/gen_pokepad.py --maqueta   # ademas monta la pantalla para verla
"""
import argparse
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

RAIZ = Path(__file__).resolve().parent.parent
ARTE = RAIZ / "arte" / "pokepad"
SALIDA = (RAIZ / "mod" / "src" / "client" / "resources" / "assets"
          / "lunaeternal" / "textures" / "gui" / "pokepad")
MAQUETA = RAIZ / "build" / "pokepad"

# El azul de la pantalla, para localizarla dentro del chasis.
AZUL = (135, 145, 207)

# La celda se ajusta AL ICONO en vez de repartir el espacio disponible.
#
# Antes se calculaba con el hueco que hubiera --salia de 35 con un icono de
# 25-- y el dibujo ocupaba la mitad de su celda: sobraba aire y los iconos se
# veian pequenos y apagados. Agrandar el icono no es opcion: escalar 25 a 32 no
# es un factor entero y lo emborrona. Asi que se aprieta la celda.
AIRE = 3        # margen entre el icono y el borde de su celda
HUECO = 6       # separacion entre celdas


def quitar_fondo(im: Image.Image) -> Image.Image:
    """Vuelve transparente el fondo opaco, si lo hay.

    Se toma el color de las cuatro esquinas: si coinciden entre si y son
    opacas, eso es un fondo. Y **solo se borra lo CONECTADO al borde**, que es
    la parte importante: una Poke Ball tiene mucho blanco dentro, y borrar
    "todo lo blanco" la agujerearia.
    """
    a = np.array(im.convert("RGBA"))
    h, w = a.shape[:2]
    esquinas = np.array([a[y, x][:3] for y, x in
                         ((0, 0), (0, w - 1), (h - 1, 0), (h - 1, w - 1))], int)
    # Con TOLERANCIA, no por igualdad exacta. Las cuatro esquinas de `gts` eran
    # (254,254,254) y (254,254,253): un punto de diferencia, invisible, y el
    # fondo blanco se colaba entero hasta el juego.
    if a[0, 0][3] < 200 or np.abs(esquinas - esquinas[0]).max() > 6:
        return im                       # ya tiene transparencia, o no es plano

    fondo = esquinas[0]
    parecido = np.abs(a[..., :3].astype(int) - fondo).sum(axis=2) < 40

    # Inundacion desde el borde, sin recursion: se propaga en las cuatro
    # direcciones hasta que deja de crecer.
    marca = np.zeros((h, w), bool)
    marca[0, :] |= parecido[0, :]
    marca[-1, :] |= parecido[-1, :]
    marca[:, 0] |= parecido[:, 0]
    marca[:, -1] |= parecido[:, -1]
    while True:
        antes = marca.sum()
        crece = marca.copy()
        crece[1:, :] |= marca[:-1, :]
        crece[:-1, :] |= marca[1:, :]
        crece[:, 1:] |= marca[:, :-1]
        crece[:, :-1] |= marca[:, 1:]
        marca = crece & parecido
        if marca.sum() == antes:
            break

    a[marca, 3] = 0
    return Image.fromarray(a, "RGBA")


def alfa_duro(im: Image.Image) -> Image.Image:
    """Vuelve el alfa binario: un pixel esta o no esta.

    Un borde medio transparente es lo que produce el halo sucio cuando
    Minecraft amplia la interfaz. En pixel art no existen los medios pixeles.

    **Aqui habia tambien una cuantizacion a 16 colores y se quito**, porque
    empeoraba. Se comparo icono a icono, ampliado x9: con el arte que llega hoy
    --que ya nace como pixel art-- reducir la paleta no arreglaba nada visible
    y en cambio se cargaba los colores. Los lomos de los libros del Wiki se
    volvian barro y la Poke Ball del Pokedex se volvia marron.

    La leccion, por si vuelve la tentacion: **cuantizar sirve cuando el origen
    es una ilustracion suavizada**, no cuando ya es pixel art. Lo que arregla el
    halo es esta funcion, no aquella.
    """
    a = np.array(im.convert("RGBA"))
    a[..., 3] = (a[..., 3] >= 128) * 255
    return Image.fromarray(a, "RGBA")


def preparar(origen: Path) -> Image.Image:
    return alfa_duro(quitar_fondo(Image.open(origen).convert("RGBA")))


def medir_pantalla(chasis: Image.Image) -> tuple:
    """Donde cae el area azul.

    Se detecta por color en vez de escribirlo a mano para que siga siendo
    cierto si el chasis se regenera. Y se mide sobre el original SIN cuantizar:
    con la paleta ya reducida, la deteccion se come tambien el marco claro.
    """
    a = np.array(chasis.convert("RGBA")).astype(int)
    m = (a[..., 3] > 128) & (np.abs(a[..., :3] - np.array(AZUL)).sum(axis=2) < 90)
    ys, xs = np.where(m)
    return int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--maqueta", action="store_true",
                    help="monta la pantalla principal para poder mirarla")
    args = ap.parse_args()

    SALIDA.mkdir(parents=True, exist_ok=True)
    for viejo in SALIDA.glob("*.png"):
        viejo.unlink()

    print("POKEPAD")
    suave = Image.open(ARTE / "fondo_base.png").convert("RGBA")
    alfa_duro(suave).save(SALIDA / "pokepad.png")
    x0, y0, x1, y1 = medir_pantalla(suave)
    print(f"  chasis    {suave.size[0]} x {suave.size[1]}")
    print(f"  pantalla  x {x0}-{x1}  y {y0}-{y1}   ({x1 - x0} x {y1 - y0})")

    iconos = sorted((ARTE / "icons").glob("*.png"))
    for p in iconos:
        preparar(p).save(SALIDA / f"{p.stem.replace('icon_', '')}.png")
    lado = Image.open(iconos[0]).size[0]
    print(f"  iconos    {len(iconos)} de {lado}x{lado}")

    botones = sorted((ARTE / "botones").glob("*.png"))
    for p in botones:
        preparar(p).save(SALIDA / f"boton_{p.stem}.png")
    print(f"  botones   {len(botones)}")

    celda = lado + AIRE * 2
    if celda * 5 + HUECO * 4 > (x1 - x0) or celda * 3 + HUECO * 2 > (y1 - y0):
        raise SystemExit("La rejilla no cabe en la pantalla azul. Baja AIRE o "
                         "HUECO, o el icono es demasiado grande.")
    rej_w, rej_h = celda * 5 + HUECO * 4, celda * 3 + HUECO * 2
    rej_x = x0 + ((x1 - x0) - rej_w) // 2
    rej_y = y0 + ((y1 - y0) - rej_h) // 2
    print(f"\n  PARA PokePadScreen:")
    print(f"    REJ_X = {rej_x}, REJ_Y = {rej_y}")
    print(f"    CELDA = {celda}, HUECO = {HUECO}, ICONO = {lado}")

    if args.maqueta:
        maqueta(Image.open(SALIDA / "pokepad.png"), iconos,
                rej_x, rej_y, celda, lado)


def maqueta(chasis, iconos, rx, ry, celda, lado) -> None:
    """Monta la pantalla principal con las celdas dibujadas como las dibuja el
    juego: rectangulos planos. Ver docs/ui/prompts-arte-pokepad.md §4."""
    out = chasis.convert("RGBA")
    d = ImageDraw.Draw(out)
    for i, p in enumerate(iconos):
        cx = rx + (i % 5) * (celda + HUECO)
        cy = ry + (i // 5) * (celda + HUECO)
        d.rectangle([cx, cy, cx + celda - 1, cy + celda - 1],
                    fill=(122, 131, 200, 255), outline=(200, 210, 240, 255))
        for ex, ey in ((cx, cy), (cx + celda - 1, cy),
                       (cx, cy + celda - 1), (cx + celda - 1, cy + celda - 1)):
            d.point((ex, ey), fill=(0, 0, 0, 0))
        ico = Image.open(SALIDA / f"{p.stem.replace('icon_', '')}.png")
        out.alpha_composite(ico, (cx + (celda - lado) // 2,
                                  cy + (celda - lado) // 2))
    E = 3
    g = out.resize((out.size[0] * E, out.size[1] * E), Image.NEAREST)
    f = Image.new("RGBA", g.size, (14, 14, 20, 255))
    f.alpha_composite(g)
    MAQUETA.mkdir(parents=True, exist_ok=True)
    f.convert("RGB").save(MAQUETA / "maqueta.png")
    print(f"  -> {(MAQUETA / 'maqueta.png').relative_to(RAIZ)}")


if __name__ == "__main__":
    main()
