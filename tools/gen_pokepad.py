#!/usr/bin/env python3
"""
Trocea el arte del PokePad y lo deja listo para el juego.

QUE HACE

El arte llega como cuatro hojas grandes generadas con IA: el chasis, las tres
celdas, los quince iconos y los botones. Este script las recorta pieza a pieza,
las reduce al tamano real de interfaz y escribe los PNG finales.

    arte/pokepad/chasis.png    2656x1600  ->  pokepad.png        344x207
    arte/pokepad/celdas.png    2816x1536  ->  celda_*.png          40x40
    arte/pokepad/iconos.png    2656x1600  ->  icono_*.png          24x24
    arte/pokepad/botones.png   2816x1536  ->  boton_*.png          16x16

POR QUE UN SCRIPT Y NO RECORTAR A MANO

Porque el arte se va a regenerar. Cada vez que llegue una hoja nueva --otra
paleta, otro icono, un chasis retocado-- esto se vuelve a ejecutar y sale todo
recortado igual. Recortar 25 piezas a mano una vez es media tarde; hacerlo cada
vez que cambie algo es no cambiarlo nunca.

Y ademas las medidas dejan de ser un numero en la cabeza de nadie: las hojas de
IA no traen rejilla exacta, asi que cada pieza se localiza **buscando su
contenido**, no contando pixeles.

EL DETALLE QUE MAS CUESTA VER

La hoja de iconos trae **una etiqueta de texto debajo de cada icono**
("Pokedex", "Cosmetics"...). No se pidio y no sirve: el juego dibuja el texto,
que esta traducido. Se descartan buscando el hueco vertical que las separa del
icono y quedandose solo con el grupo de arriba.

Uso:
    python tools/gen_pokepad.py            # trocea y escribe
    python tools/gen_pokepad.py --maqueta  # ademas monta una maqueta para verlo
"""
import argparse
import sys
from pathlib import Path

import numpy as np
from PIL import Image

RAIZ = Path(__file__).resolve().parent.parent
ARTE = RAIZ / "arte" / "pokepad"
# Las texturas van DIRECTAS al mod de cliente. El PokePad vive en
# `lunaeternal` y no en `lunaneon` porque necesita hablar con la economia, el
# GTS y las misiones, que estan ahi (D-025).
SALIDA = (RAIZ / "mod" / "src" / "client" / "resources" / "assets"
          / "lunaeternal" / "textures" / "gui" / "pokepad")
MAQUETA = RAIZ / "build" / "pokepad"

# El tamano final de la interfaz. Sale de la referencia (346x207, medido en sus
# PNG) ajustado a la proporcion EXACTA del chasis que llego, 1.66, para no
# deformarlo ni recortarlo.
ALTO = 207

# Cuantas veces mas grande se guarda la textura respecto al tamano al que se
# dibuja. La interfaz mide 345x207 PIXELES DE INTERFAZ --eso no cambia, es su
# tamano fisico en pantalla-- pero la textura se guarda a 4x.
#
# Por que: Minecraft dibuja las interfaces escaladas por el ajuste GUI Scale.
# A escala 3, esos 345 px ocupan 1035 en pantalla. Con una textura de 345 cada
# texel se estira a 3 px y se ve el pixelado; con una de 1380 hay detalle de
# sobra para esos 1035.
ESCALA = 4

# Los quince iconos, en el orden en que estan en la hoja: 5 columnas x 3 filas.
ICONOS = [
    "pokedex", "cosmeticos", "trabajos", "misiones", "warps",
    "clan", "gts", "tienda", "tesoros", "wiki",
    "cazas", "kits", "mochila", "gyms", "explorar",
]

# Los botones que nos quedamos de la hoja, por posicion en su rejilla 4x2.
# Gemini devolvio 8: repitio la casa y anadio un "+" rojo que no se pidio.
BOTONES = {
    (0, 0): "atras", (1, 0): "adelante", (2, 0): "ajustes", (3, 0): "inicio",
    (1, 1): "mas", (3, 1): "cerrar",
}

CELDAS = ["reposo", "encima", "bloqueada"]


def contenido(a: np.ndarray) -> np.ndarray:
    """Mascara de 'aqui hay dibujo', no fondo.

    El fondo de las hojas es casi negro pero no negro puro, y las etiquetas de
    texto son gris oscuro. Se pide alfa Y algo de luz: asi cae el fondo, y las
    etiquetas se van despues por posicion.
    """
    alfa = a[..., 3] > 60
    luz = a[..., :3].max(axis=2) > 70
    return alfa & luz


def recortar(a: np.ndarray, x0: int, y0: int, x1: int, y1: int):
    """Caja ajustada al contenido dentro de una region."""
    sub = contenido(a[y0:y1, x0:x1])
    ys, xs = np.where(sub)
    if len(xs) == 0:
        return None
    return (x0 + xs.min(), y0 + ys.min(), x0 + xs.max() + 1, y0 + ys.max() + 1)


def sin_etiqueta(a: np.ndarray, x0: int, y0: int, x1: int, y1: int):
    """Como `recortar`, pero descartando la etiqueta de texto de abajo.

    Las hojas de IA traen un rotulo debajo de cada pieza ("Pokedex",
    "RESTING"...). No se pidio y estorba: el juego dibuja el texto, que esta
    traducido.

    La primera version cortaba en el primer hueco vertical, y **fallo**: una
    chispa suelta encima del icono ya abria un hueco, y en la ultima fila se
    quedaba con el rotulo en vez de con el dibujo. Este descarta por lo que de
    verdad distingue a un rotulo:

        esta ABAJO del todo   y   es BAJO comparado con la pieza

    Asi una chispa separada sigue contando como parte del icono --esta arriba,
    no abajo-- y el rotulo se va aunque no haya hueco limpio.
    """
    alto = y1 - y0
    sub = contenido(a[y0:y1, x0:x1])
    filas = sub.any(axis=1)
    if not filas.any():
        return None

    # Todos los grupos de filas con contenido, de arriba abajo.
    grupos, i = [], 0
    while i < len(filas):
        if filas[i]:
            j = i
            while j < len(filas) and filas[j]:
                j += 1
            grupos.append((i, j))
            i = j
        else:
            i += 1

    # Se tira el ultimo grupo si parece un rotulo: empieza pasado el 60 % de la
    # celda y no llega al 18 % de alto.
    if len(grupos) > 1:
        ini, fin = grupos[-1]
        if ini > alto * 0.60 and (fin - ini) < alto * 0.18:
            grupos.pop()

    arriba, abajo = grupos[0][0], grupos[-1][1]
    cols = sub[arriba:abajo].any(axis=0)
    if not cols.any():
        return None
    izq = int(np.argmax(cols))
    der = len(cols) - int(np.argmax(cols[::-1]))
    return (x0 + izq, y0 + arriba, x0 + der, y0 + abajo)


def encoger(im: Image.Image, w: int, h: int) -> Image.Image:
    """Reduce cuidando el canal alfa.

    LANCZOS sobre una imagen con transparencia mezcla el color de los pixeles
    invisibles y deja un halo oscuro alrededor. Se premultiplica antes y se
    deshace despues, que es la forma correcta.
    """
    a = np.array(im.convert("RGBA")).astype(float)
    alfa = a[..., 3:4] / 255.0
    a[..., :3] *= alfa
    pre = Image.fromarray(a.astype(np.uint8), "RGBA").resize((w, h), Image.LANCZOS)
    b = np.array(pre).astype(float)
    al = b[..., 3:4] / 255.0
    # El umbral NO es cosmetico. Dividir por un alfa casi nulo multiplica el
    # ruido por doscientos, y eso salia como chispas de colores alrededor de
    # cada icono. Por debajo de este alfa el color no se ve igual, asi que se
    # deja tal cual en vez de amplificarlo.
    np.divide(b[..., :3], al, out=b[..., :3], where=al > 0.20)
    return Image.fromarray(np.clip(b, 0, 255).astype(np.uint8), "RGBA")


def guardar(im: Image.Image, nombre: str) -> None:
    SALIDA.mkdir(parents=True, exist_ok=True)
    im.save(SALIDA / f"{nombre}.png")


def hoja(nombre: str) -> tuple:
    ruta = ARTE / f"{nombre}.png"
    if not ruta.exists():
        raise SystemExit(f"Falta {ruta}. Copia ahi el arte antes de trocear.")
    im = Image.open(ruta).convert("RGBA")
    return im, np.array(im).astype(int)


def hacer_chasis() -> tuple:
    im, a = hoja("chasis")
    caja = recortar(a, 0, 0, im.size[0], im.size[1])
    x0, y0, x1, y1 = caja
    ancho = round((x1 - x0) * ALTO / (y1 - y0))
    final = encoger(im.crop(caja), ancho * ESCALA, ALTO * ESCALA)
    guardar(final, "pokepad")
    print(f"  chasis    {im.size[0]}x{im.size[1]} -> {ancho}x{ALTO}")
    return final, im.crop(caja)


def medir_pantalla(chasis: Image.Image, escala: int = 1) -> tuple:
    """Dónde cae el área azul dentro del chasis ya reducido.

    Es **el número que congela la composición**: el código dibuja la rejilla
    ahí y no vuelve a medir. Se detecta por color en vez de escribirlo a mano
    para que siga siendo cierto si el chasis se regenera.
    """
    a = np.array(chasis).astype(int)
    d = np.abs(a[..., :3] - np.array([135, 145, 207])).sum(axis=2)
    m = (a[..., 3] > 128) & (d < 60)
    ys, xs = np.where(m)
    return (int(xs.min()) // escala, int(ys.min()) // escala,
            int(xs.max() + 1) // escala, int(ys.max() + 1) // escala)


def bandas(cuentas: np.ndarray, umbral: float, minimo: int = 3) -> list:
    """Los tramos con contenido de una proyeccion. [(inicio, fin), ...]

    `cuentas` son PIXELES POR FILA, no un booleano, y ese es el detalle que
    costo entender: proyectando con `any()` basta **un solo pixel** para que
    una fila cuente como llena. Entre el icono y su rotulo hay hueco de verdad,
    pero con dos o tres pixeles sueltos dentro --restos del suavizado-- que lo
    puenteaban, y los tramos salian pegados.

    Con un umbral proporcional al tamano de la hoja, el ruido cae y el hueco
    aparece.
    """
    perfil = cuentas > umbral
    tramos, i = [], 0
    while i < len(perfil):
        if perfil[i]:
            j = i
            while j < len(perfil) and perfil[j]:
                j += 1
            if j - i >= minimo:
                tramos.append((i, j))
            i = j
        else:
            i += 1
    return tramos


def ajustar(tramos: list, objetivo: int) -> list:
    """Fuerza `tramos` a tener exactamente `objetivo` elementos.

    Una pieza puede partirse en dos si tiene un hueco interno --al borde cian
    del estado "encima" le pasa--, y entonces salen mas tramos de la cuenta. Se
    fusionan los dos MAS CERCANOS entre si, repetidamente, que es justo lo
    contrario de separar piezas distintas: entre dos piezas siempre hay mas
    espacio que dentro de una.
    """
    tramos = list(tramos)
    while len(tramos) > objetivo:
        huecos = [tramos[i + 1][0] - tramos[i][1] for i in range(len(tramos) - 1)]
        i = huecos.index(min(huecos))
        tramos[i:i + 2] = [(tramos[i][0], tramos[i + 1][1])]
    return tramos


def hacer_tira(nombre: str, cols: int, filas: int, etiquetas, lado: int,
               con_rotulo: bool) -> list:
    """Trocea una hoja localizando sus piezas, no contando pixeles.

    Las hojas de IA no traen una rejilla exacta: las filas no miden lo mismo y
    las piezas no estan centradas igual. Asi que en vez de dividir en partes
    iguales --que fue el primer intento y cortaba los iconos-- se PROYECTA el
    contenido sobre cada eje y se leen los tramos que salen.

    El truco esta en los rotulos. Al proyectar sobre el eje vertical, una hoja
    con rotulos da el DOBLE de tramos de los esperados:

        dibujo · rotulo · dibujo · rotulo · dibujo · rotulo

    Como el rotulo siempre va justo debajo de su dibujo, basta quedarse con los
    tramos PARES. Nada de umbrales de brillo ni de porcentajes a ojo.
    """
    im, a = hoja(nombre)
    W, H = im.size
    m = contenido(a)

    # 5 % del ancho: una fila de icono ronda el 30 %, una de rotulo el 4 %.
    verticales = bandas(m.sum(axis=1), W * 0.05, minimo=max(4, H // 100))
    if con_rotulo and len(verticales) > filas:
        # Se conservan las bandas MAS ALTAS, que son las piezas. Los rotulos
        # --y cualquier adorno que la IA haya metido arriba-- son franjas
        # finas, asi que caen solas.
        #
        # Antes se cogian las bandas pares, dando por hecho el orden
        # "pieza, rotulo, pieza, rotulo". Fallo con la hoja de celdas, que
        # trae ademas una franja fina ARRIBA del todo: se quedaba con ella.
        verticales = sorted(sorted(verticales, key=lambda b: b[0] - b[1])[:filas])
    verticales = ajustar(verticales, filas)
    if len(verticales) != filas:
        raise SystemExit(f"{nombre}: esperaba {filas} filas y encontre "
                         f"{len(verticales)}. Revisa la hoja.")

    piezas = []
    for f, (y0, y1) in enumerate(verticales):
        horizontales = ajustar(
            bandas(m[y0:y1].sum(axis=0), (y1 - y0) * 0.05, minimo=max(4, W // 100)),
            cols)
        if len(horizontales) != cols:
            raise SystemExit(f"{nombre}: fila {f} tiene {len(horizontales)} "
                             f"piezas y esperaba {cols}.")
        for c, (x0, x1) in enumerate(horizontales):
            clave = (c, f) if isinstance(etiquetas, dict) else f * cols + c
            if isinstance(etiquetas, dict):
                if clave not in etiquetas:
                    continue
            elif clave >= len(etiquetas):
                continue
            piezas.append((etiquetas[clave], im.crop((x0, y0, x1, y1))))

    for nom, pieza in piezas:
        guardar(encoger(pieza, lado * ESCALA, lado * ESCALA), nom)
    print(f"  {nombre:<9} {W}x{H} -> {len(piezas)} piezas de {lado}x{lado}")
    return piezas


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--maqueta", action="store_true",
                    help="monta una maqueta con todo en su sitio")
    args = ap.parse_args()

    print("POKEPAD  ·  troceando el arte")
    chasis, _ = hacer_chasis()
    x0, y0, x1, y1 = medir_pantalla(chasis, ESCALA)
    print(f"  pantalla  x {x0}-{x1}  y {y0}-{y1}   ({x1-x0} x {y1-y0})")

    # La celda sale de dividir el area util entre 5 columnas y 3 filas. No se
    # elige a ojo: se calcula, porque es lo que garantiza que la rejilla encaje.
    # La celda sale de la restriccion mas apretada de las dos --ancho o alto--
    # y despues la rejilla se CENTRA en el area azul. Sin centrar, las tres
    # filas se apelotonan arriba y queda un palmo vacio debajo.
    hueco = 4
    margen = 6
    por_ancho = ((x1 - x0) - margen * 2 - hueco * 4) // 5
    por_alto = ((y1 - y0) - margen * 2 - hueco * 2) // 3
    lado = min(por_ancho, por_alto)
    rej_w = lado * 5 + hueco * 4
    rej_h = lado * 3 + hueco * 2
    rej_x = x0 + ((x1 - x0) - rej_w) // 2
    rej_y = y0 + ((y1 - y0) - rej_h) // 2
    print(f"  celda     {lado} x {lado}  (hueco {hueco})")
    print(f"  rejilla   x {rej_x}  y {rej_y}   ({rej_w} x {rej_h})")

    hacer_tira("celdas", 3, 1, CELDAS, lado, True)
    hacer_tira("iconos", 5, 3, ICONOS, 24, True)
    hacer_tira("botones", 4, 2, BOTONES, 16, False)

    if args.maqueta:
        maqueta(chasis, (rej_x, rej_y), lado, hueco)


def maqueta(chasis: Image.Image, origen, lado, hueco) -> None:
    """Monta la pantalla principal para poder mirarla antes de escribir código."""
    rx, ry = origen[0] * ESCALA, origen[1] * ESCALA
    lado, hueco = lado * ESCALA, hueco * ESCALA
    out = chasis.copy()
    celda = Image.open(SALIDA / "reposo.png")
    for i, nombre in enumerate(ICONOS):
        c, f = i % 5, i // 5
        cx = rx + c * (lado + hueco)
        cy = ry + f * (lado + hueco)
        out.alpha_composite(celda, (cx, cy))
        ico = Image.open(SALIDA / f"{nombre}.png")
        out.alpha_composite(ico, (cx + (lado - 24 * ESCALA) // 2,
                                  cy + (lado - 24 * ESCALA) // 2))
    grande = out
    fondo = Image.new("RGBA", grande.size, (14, 14, 20, 255))
    fondo.alpha_composite(grande)
    MAQUETA.mkdir(parents=True, exist_ok=True)
    ruta = MAQUETA / "maqueta.png"
    fondo.convert("RGB").save(ruta)
    print(f"  -> {ruta.relative_to(RAIZ)}")


if __name__ == "__main__":
    main()
