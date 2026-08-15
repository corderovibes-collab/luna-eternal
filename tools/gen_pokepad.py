#!/usr/bin/env python3
"""
Prepara el arte del PokePad y lo deja listo para el juego.

QUE HACE

El arte llega ya cortado, una imagen por pieza y al tamano correcto. Este
script hace lo poco que falta:

    arte/pokepad/fondo_base.png  1380x828  ->  pokepad.png      1380x828
    arte/pokepad/icons/*.png      100x100  ->  <nombre>.png      100x100
    arte/pokepad/botones/*.png    120x96   ->  boton_<nombre>.png 120x96

Y sobre todo mide **donde cae la pantalla azul** dentro del chasis, que es el
numero que congela la composicion: el codigo dibuja la rejilla ahi y no vuelve
a medir.

POR QUE SIGUE HACIENDO FALTA UN SCRIPT SI EL ARTE YA VIENE CORTADO

Por dos cosas que el arte generado con IA trae casi siempre, y que no se ven
hasta que estan dentro del juego:

  fondo opaco   Alguna pieza llega con un rectangulo blanco detras en vez de
                transparencia --a `gts` le paso--. Colado, en el juego se ve un
                recuadro blanco alrededor del icono.

Se arregla aqui, asi que al generar arte no hay que acordarse de nada.

⚠ AQUI SE ENDURECIA TAMBIEN EL ALFA, Y SE QUITO AL PASAR A HD. Con iconos de
25x25 ampliados por el GUI Scale, un borde medio transparente salia como un
halo sucio y volver el alfa binario lo arreglaba. Con arte HD dibujado a
pixeles reales pasa justo lo contrario: ese borde suave ES el antialiasing, y
binarizarlo deja los dibujos dentados. En el chasis son 7.618 pixeles --las
esquinas redondeadas y el bisel enteros--. Ver `alfa_duro`, que sigue ahi
documentada para que no se reinvente.

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
#
# Los dos numeros son los de siempre multiplicados por 4, que es lo que crecio
# el icono (25 -> 100). Se escalan en vez de reelegirse porque la proporcion ya
# estaba ajustada a ojo contra el arte y funcionaba; lo que no valia era el
# valor absoluto, que sobre 100 px es invisible.
AIRE = 12       # margen entre el icono y el borde de su celda
HUECO = 24      # separacion entre celdas

# El orden de la rejilla lo manda `App.TODAS` (App.java), no el alfabeto.
#
# La maqueta se montaba con `sorted(glob)` y salia en orden alfabetico, asi que
# ensenaba una pantalla que NO es la que ve el jugador. Como la maqueta existe
# para aprobar el diseno antes de desplegar, eso la hacia inutil justo para lo
# unico que sirve. Si cambia el orden en App.java, cambia aqui.
ORDEN = ["pokedex", "cosmeticos", "trabajos", "misiones", "warps",
         "clan", "gts", "tienda", "tesoros", "wiki",
         "cazas", "kits", "mochila", "gyms", "explorar"]

# El grosor del borde de la celda y el tamano de la esquina mordida, tambien
# x4. A 1 px sobre una celda de 124 ninguno de los dos se ve.
BORDE = 4
MORDIDA = 4


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


def sangrar_alfa(im: Image.Image, visible: int = 24) -> Image.Image:
    """Contagia el color de lo visible a lo invisible. NO toca el alfa.

    EL PROBLEMA QUE RESUELVE, MEDIDO EN EL JUEGO

    Un PNG guarda color y transparencia por separado, asi que un pixel puede
    ser invisible y llevar un color dentro. El arte generado llega lleno de
    eso: en `explorar` hay 383 pixeles con alfa entre 1 y 23 --invisibles a
    todos los efectos-- y 22 de ellos guardan VERDE PURO, AZUL PURO o ROJO
    PURO. Basura del generador, en teoria inofensiva.

    En teoria. En la captura del juego aparecian exactamente 8 pixeles verde
    puro, 6 azules, 5 rojos y 3 amarillos, que son EXACTAMENTE las cuentas del
    arte. Se veian como motas de colores sobre el contorno de cada icono.

    LA CURA

    Es la estandar en cualquier motor: a lo que no se ve se le pone el color de
    su vecino visible. El alfa no cambia, asi que la imagen compuesta es la
    misma pixel a pixel --lo invisible sigue invisible--, pero ya no hay
    colores raros escondidos que el dibujado pueda sacar a pasear.

    Se propaga en varias pasadas para alcanzar tambien lo que esta lejos del
    borde, que es donde viven las motas mas escandalosas.
    """
    a = np.array(im.convert("RGBA")).astype(np.int32)
    fuente = a[..., 3] >= visible
    rgb = a[..., :3].astype(np.float32)
    # El tope es generoso a proposito: cada pasada avanza UN pixel, asi que una
    # zona invisible ancha necesita tantas como su radio. Con 8 se quedaban 368
    # motas dentro de `boton_atras`, que es el que mas hueco transparente tiene.
    # El bucle sale solo en cuanto no queda nada que contagiar.
    for _ in range(max(im.size)):
        if fuente.all():
            break
        suma = np.zeros_like(rgb)
        cuenta = np.zeros(a.shape[:2], np.float32)
        p_rgb = np.pad(rgb, ((1, 1), (1, 1), (0, 0)), mode="edge")
        p_src = np.pad(fuente, 1, mode="constant")
        for dy in (0, 1, 2):
            for dx in (0, 1, 2):
                if dy == 1 and dx == 1:
                    continue
                m = p_src[dy:dy + a.shape[0], dx:dx + a.shape[1]]
                suma += p_rgb[dy:dy + a.shape[0], dx:dx + a.shape[1]] * m[..., None]
                cuenta += m
        nuevo = (~fuente) & (cuenta > 0)
        if not nuevo.any():
            break
        rgb[nuevo] = suma[nuevo] / cuenta[nuevo][..., None]
        fuente = fuente | nuevo
    a[..., :3] = np.round(rgb).astype(np.int32)
    return Image.fromarray(a.astype(np.uint8), "RGBA")


def preparar(origen: Path) -> Image.Image:
    # Sin `alfa_duro`: ver el aviso de la cabecera. El arte HD conserva su
    # borde suave a proposito.
    return sangrar_alfa(quitar_fondo(Image.open(origen).convert("RGBA")))


# Cada textura se guarda con su .mcmeta, y `clamp` es el que importa.
#
# Sin el, OpenGL repite la textura (GL_REPEAT) y al filtrarla el muestreo del
# borde se mezcla con el borde CONTRARIO: el pixel de arriba con el de abajo y
# el de la izquierda con el de la derecha. Eso dibuja un marco fino alrededor
# de cada icono --el "borde con micropuntos"--, y solo aparece cuando el Pad no
# cae a tamano exacto, que es justo cuando hace falta filtrar.
#
# `blur` va en false porque el filtro se decide en tiempo real desde
# PokePadScreen: depende de si el Pad cabe o no, y eso el .mcmeta no lo sabe.
MCMETA = '{\n  "texture": {\n    "blur": false,\n    "clamp": true\n  }\n}\n'


def guardar(im: Image.Image, destino: Path) -> None:
    im.save(destino)
    destino.with_suffix(".png.mcmeta").write_text(MCMETA, encoding="utf-8")


def medir_pantalla(chasis: Image.Image) -> tuple:
    """Donde cae el area azul.

    Se detecta por color en vez de escribirlo a mano para que siga siendo
    cierto si el chasis se regenera. Y se mide sobre el original SIN cuantizar:
    con la paleta ya reducida, la deteccion se come tambien el marco claro.
    """
    a = np.array(chasis.convert("RGBA")).astype(int)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]

    # Parecido al azul, PERO ademas azul de verdad. La distancia sola no
    # distingue un azul saturado de un gris de la misma luminancia, y el chasis
    # HD esta lleno de grises: con solo la distancia entraban reflejos del
    # cuerpo (132,134,138) y el borde suavizado de la linea cian (135,197,195).
    #   b > r   descarta los grises, donde los tres canales van juntos
    #   b > g   descarta el cian, que tiene el verde por las nubes
    m = ((a[..., 3] > 128)
         & (np.abs(a[..., :3] - np.array(AZUL)).sum(axis=2) < 90)
         & (b - r > 40) & (b > g))

    # Y la caja se saca por MAYORIA de fila y columna, no por el minimo y el
    # maximo. Un puñado de pixeles sueltos en una esquina no cambia el recuento
    # de su fila, pero si arrastraba el extremo: con el chasis HD, 9.000 px
    # perdidos de 415.000 estiraban la pantalla de 825x568 a 1297x788, y el
    # numero salia mal sin que nada fallara.
    filas, cols = m.sum(1), m.sum(0)
    ys = np.where(filas >= filas.max() * 0.5)[0]
    xs = np.where(cols >= cols.max() * 0.5)[0]
    return int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--maqueta", action="store_true",
                    help="monta la pantalla principal para poder mirarla")
    args = ap.parse_args()

    SALIDA.mkdir(parents=True, exist_ok=True)
    for viejo in list(SALIDA.glob("*.png")) + list(SALIDA.glob("*.mcmeta")):
        viejo.unlink()

    print("POKEPAD")
    chasis = Image.open(ARTE / "fondo_base.png").convert("RGBA")
    # El chasis tambien: tiene menos motas, pero es el mismo problema.
    guardar(sangrar_alfa(chasis), SALIDA / "pokepad.png")
    x0, y0, x1, y1 = medir_pantalla(chasis)
    print(f"  chasis    {chasis.size[0]} x {chasis.size[1]}")
    print(f"  pantalla  x {x0}-{x1}  y {y0}-{y1}   ({x1 - x0} x {y1 - y0})")

    # En el orden de la rejilla, no en el del alfabeto. Y se falla aqui si
    # falta uno: un hueco en la pantalla principal es peor que no generar.
    iconos = [ARTE / "icons" / f"icon_{n}.png" for n in ORDEN]
    faltan = [p.name for p in iconos if not p.exists()]
    if faltan:
        raise SystemExit(f"Faltan iconos: {', '.join(faltan)}")
    for p in iconos:
        guardar(preparar(p), SALIDA / f"{p.stem.replace('icon_', '')}.png")
    lado = Image.open(iconos[0]).size[0]
    print(f"  iconos    {len(iconos)} de {lado}x{lado}")

    botones = sorted((ARTE / "botones").glob("*.png"))
    for p in botones:
        guardar(preparar(p), SALIDA / f"boton_{p.stem}.png")
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
    # Copia explicita: `chasis` se sigue usando abajo para restaurar las
    # esquinas mordidas, asi que no puede ser el mismo objeto que se pinta.
    chasis = chasis.convert("RGBA")
    out = chasis.copy()
    d = ImageDraw.Draw(out)
    for i, p in enumerate(iconos):
        cx = rx + (i % 5) * (celda + HUECO)
        cy = ry + (i // 5) * (celda + HUECO)
        d.rectangle([cx, cy, cx + celda - 1, cy + celda - 1],
                    fill=(122, 131, 200, 255), outline=(200, 210, 240, 255),
                    width=BORDE)
        # La esquina mordida tambien crece: a 1 px sobre una celda de 124 no se
        # ve, y la rejilla vuelve a parecer una hoja de calculo.
        #
        # Se RESTAURA el chasis en esas esquinas, no se borran. Borrarlas deja
        # un agujero transparente y en la maqueta salian cuatro puntos negros
        # por celda --el fondo de la lamina--, que es justo lo contrario de lo
        # que hace el juego: alli la esquina deja ver la pantalla de debajo.
        for ex, ey in ((cx, cy), (cx + celda - MORDIDA, cy),
                       (cx, cy + celda - MORDIDA),
                       (cx + celda - MORDIDA, cy + celda - MORDIDA)):
            caja = (ex, ey, ex + MORDIDA, ey + MORDIDA)
            out.paste(chasis.crop(caja), caja)
        ico = Image.open(SALIDA / f"{p.stem.replace('icon_', '')}.png")
        out.alpha_composite(ico, (cx + (celda - lado) // 2,
                                  cy + (celda - lado) // 2))
    # Sin ampliar. El chasis ya mide 1380x828, que es el tamano al que se va a
    # dibujar en pantalla: ampliarlo ensenaria algo que nadie va a ver. Antes se
    # multiplicaba por 3 porque el original media 346 y no se apreciaba nada.
    f = Image.new("RGBA", out.size, (14, 14, 20, 255))
    f.alpha_composite(out)
    MAQUETA.mkdir(parents=True, exist_ok=True)
    f.convert("RGB").save(MAQUETA / "maqueta.png")
    print(f"  -> {(MAQUETA / 'maqueta.png').relative_to(RAIZ)}")


if __name__ == "__main__":
    main()
