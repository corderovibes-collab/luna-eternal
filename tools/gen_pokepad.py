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
from PIL import Image, ImageDraw, ImageFont

RAIZ = Path(__file__).resolve().parent.parent
ARTE = RAIZ / "arte" / "pokepad"
SALIDA = (RAIZ / "mod" / "src" / "client" / "resources" / "assets"
          / "lunaeternal" / "textures" / "gui" / "pokepad")
MAQUETA = RAIZ / "build" / "pokepad"

# LA PALETA DE LA PANTALLA. Los mismos numeros que PokePadScreen, y por el mismo
# motivo que las medidas: si la maqueta no ensena los colores del juego, no
# sirve para aprobar nada.
#
# ⚠ EN EL CHASIS v4 LA PANTALLA PASO DE AZUL OSCURO A CASI BLANCA (226,235,253),
# y eso da la vuelta a TODO lo de dentro:
#
#   las celdas   eran mas CLARAS que el fondo; ahora tienen que ser mas OSCURAS
#   el texto     era BLANCO con contorno negro; ahora es NEGRO con contorno
#                claro. No es cambiar de opinion: es la misma decision del
#                usuario --"que se lea"-- aplicada a un fondo que se ha
#                invertido. Blanco sobre blanco no se lee.
#   el resalte   pasa del ambar del chasis al NARANJA FUERTE, que es el unico
#                acento del v4 con contraste suficiente sobre claro
PANTALLA = (226, 235, 253, 255)
CELDA_FONDO = (191, 203, 232, 255)      # aplicacion abierta
CELDA_BORDE = (124, 137, 180, 255)
CELDA_CERRADA = (208, 216, 236, 255)    # cerrada: recula hacia el fondo
BORDE_CERRADA = (160, 170, 198, 255)
CELDA_ENCIMA = (255, 255, 255, 255)
BORDE_ENCIMA = (243, 92, 12, 255)       # muestreado del bisel naranja del v4
TEXTO_COLOR = (22, 32, 58, 255)
TEXTO_CONTORNO = (242, 246, 255, 255)

# Que celda ensena la maqueta con el raton encima. La de en medio, para poder
# compararla con sus ocho vecinas de una sola mirada.
RESALTADA = 7

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
HUECO = 24      # separacion HORIZONTAL entre celdas

# El nombre de cada aplicacion, debajo de su icono.
#
# El alto es en pixeles del arte: la fuente de Minecraft mide 9, asi que 18 es
# exactamente el doble y cae en pixeles enteros. Cualquier otro numero la
# emborrona, que es justo lo que se lleva media noche arreglado en el chasis.
TEXTO_ALTO = 18
TEXTO_AIRE = 5      # aire por debajo del texto, antes de la celda siguiente

# Cuanto SUBE el texto dentro de su celda.
#
# A la mitad de su alto, asi que queda montado a caballo sobre la linea de
# abajo de la celda en vez de colgando en el hueco. Es lo que lo ata a su icono:
# suelto en medio de dos filas, el ojo duda de a cual pertenece.
TEXTO_SOLAPE = TEXTO_ALTO // 2

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

# Solo para la maqueta: en el juego los nombres salen de las traducciones, que
# es lo correcto -- un jugador en ingles no puede ver "Cazas" pintado a fuego.
NOMBRES = {
    "pokedex": "Pokedex",   "cosmeticos": "Cosmeticos", "trabajos": "Trabajos",
    "misiones": "Misiones", "warps": "Viajes",          "clan": "Clan",
    "gts": "GTS",           "tienda": "Tienda",         "tesoros": "Tesoros",
    "wiki": "Wiki",         "cazas": "Cazas",           "kits": "Kits",
    "mochila": "Mochila",   "gyms": "Gimnasios",        "explorar": "Explorar",
}

# El tamano al que se DIBUJAN los botones, que no es el que llega.
#
# ⚠ EN EL CHASIS v4 LOS BOTONES DEJAN DE SER UNA BARRA Y PASAN A SER UN TECLADO
# de 2 columnas x 3 filas dentro de la ranura mediana del panel izquierdo.
#
# En el v3 iban en la unica franja libre que quedaba —981 x 58 debajo de la
# pantalla— y a 60x48 porque no cabia mas. El v4 no tiene esa franja: debajo de
# la pantalla estan la boca y los bigotes de Rotom. Pero a cambio trae tres
# ranuras de verdad, y la mediana mide 249 x 208 de hueco util, que da para seis
# botones a 80x64 — DOS TERCIOS EXACTOS del arte, y un tercio mas grandes que
# antes.
#
# Los 4 px de separacion vertical no son un descuido: tres filas de 64 llenan la
# ranura de arriba abajo, y eso es lo que hace que se lea como "esta ranura ES
# el teclado" en vez de como seis botones flotando en una caja.
ICONO = 100
BOTON_W, BOTON_H = 80, 64

# El orden es el de lectura: dos por fila, de izquierda a derecha.
BOTONES = ["atras", "adelante", "inicio", "ajustes", "mas", "cerrar"]
BOTON_COLS = 2
BOTON_SEP_X, BOTON_SEP_Y = 24, 4

# El lado de la cabeza del jugador. La POSICION se mide (va centrada en la
# ranura de arriba); el lado se fija aqui porque tiene que ser multiplo de 8:
# la cabeza de una skin son 8x8 texeles y 168 los reparte en 21 pixeles
# clavados. Con un lado que no lo fuera saldria emborronada justo en lo unico
# que es del jugador.
CARA_LADO = 168


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


def a_tamano(im: Image.Image, lado: int) -> Image.Image:
    """Lleva un icono a `lado` x `lado`, y solo si el factor es EXACTO.

    Existe para que el arte en pixel art pueda entrar tal cual. Un icono de
    25x25 es 100/25 = 4 veces mas pequeno, factor entero, asi que se amplia con
    VECINO MAS PROXIMO: cada pixel del dibujo se convierte en un bloque de 4x4 y
    el resultado es exactamente lo que dibujo el artista, sin inventar medios
    tonos ni redondear bordes.

    Si el factor NO fuera entero se aborta en vez de escalar: un 1,7 emborrona
    el pixel art y ademas mueve las lineas de sitio, y es mejor enterarse aqui
    que en el juego.
    """
    if im.size == (lado, lado):
        return im
    ancho, alto = im.size
    if ancho != alto or lado % ancho != 0:
        raise SystemExit(f"Un icono mide {ancho}x{alto} y no cabe en {lado}x{lado} "
                         f"por un factor entero. Reexportalo a {lado} o a un "
                         f"divisor suyo (25, 50, 100).")
    return im.resize((lado, lado), Image.NEAREST)


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


def reducir(im: Image.Image, w: int, h: int) -> Image.Image:
    """Encoge PREMULTIPLICANDO el alfa, que es la unica forma correcta.

    Sin premultiplicar, al promediar pixeles el color de los invisibles entra
    en la cuenta con el mismo peso que el de los visibles, y el borde se
    ensucia con lo que hubiera detras --que despues de `sangrar_alfa` es el
    color del vecino, pero antes era cualquier cosa--.
    """
    a = np.array(im.convert("RGBA")).astype(np.float32)
    al = a[..., 3:4] / 255.0
    pre = np.concatenate([a[..., :3] * al, a[..., 3:4]], -1)
    chico = np.array(Image.fromarray(pre.astype(np.uint8), "RGBA")
                     .resize((w, h), Image.LANCZOS)).astype(np.float32)
    al2 = np.maximum(chico[..., 3:4] / 255.0, 1e-6)
    out = np.concatenate([np.clip(chico[..., :3] / al2, 0, 255), chico[..., 3:4]], -1)
    return Image.fromarray(out.astype(np.uint8), "RGBA")


def guardar(im: Image.Image, destino: Path) -> None:
    im.save(destino)
    destino.with_suffix(".png.mcmeta").write_text(MCMETA, encoding="utf-8")


def medir_pantalla(chasis: Image.Image) -> tuple:
    """Donde cae la pantalla, la zona CLARA de la derecha.

    Se detecta por color en vez de escribirlo a mano para que siga siendo
    cierto cuando el chasis cambie — y ya ha cambiado cuatro veces.

    ⚠ EN EL v4 LA PANTALLA DEJO DE SER AZUL Y PASO A SER CASI BLANCA. La
    deteccion anterior buscaba `b - r > 40`, o sea "azul de verdad", y sobre un
    (226,235,253) da 27: no encontraba NADA. Un detector afinado a un color
    concreto es un detector que caduca con el arte, asi que ahora se busca lo
    unico que va a seguir siendo cierto en cualquier version — que la pantalla
    es la mancha CLARA y GRANDE del chasis.
    """
    a = np.array(chasis.convert("RGBA")).astype(int)
    lum = 0.2126 * a[..., 0] + 0.7152 * a[..., 1] + 0.0722 * a[..., 2]
    m = (a[..., 3] > 200) & (lum > 190)

    # Fuera el tercio izquierdo: ahi vive el panel del jugador, y sus molduras
    # claras no son pantalla.
    m[:, :a.shape[1] // 3] = False

    # La caja se saca por MAYORIA de fila y columna, no por el minimo y el
    # maximo. Un puñado de pixeles sueltos en una esquina no cambia el recuento
    # de su fila, pero si arrastraba el extremo: con el chasis HD, 9.000 px
    # perdidos de 415.000 estiraban la pantalla de 825x568 a 1297x788, y el
    # numero salia mal sin que nada fallara.
    #
    # Aqui ademas hace un trabajo nuevo: las OREJAS de Rotom que asoman por
    # arriba tambien son claras, y por minimo/maximo estirarian la pantalla
    # setenta pixeles hacia arriba.
    filas, cols = m.sum(1), m.sum(0)
    ys = np.where(filas >= filas.max() * 0.6)[0]
    xs = np.where(cols >= cols.max() * 0.6)[0]
    return int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1


# El gris de la moldura de las ranuras del panel izquierdo. Es el unico tono
# claro de esa zona, asi que sirve de marcador.
MOLDURA = (69, 74, 91)


def medir_cajas(chasis: Image.Image) -> list:
    """Las ranuras del panel izquierdo, por el gris de su moldura.

    Devuelve `[(x0, y0, x1, y1), ...]` de arriba abajo, con las coordenadas de
    la moldura incluida.

    POR QUE SE MIDEN Y NO SE ESCRIBEN

    Porque ya han cambiado de sitio y de numero cuatro veces, y cada vez las
    medidas escritas a mano se quedaron mintiendo en silencio: el codigo seguia
    dibujando la cara donde estaba en el chasis anterior, sin que nada fallara.
    Medir es lo unico que caduca solo.
    """
    a = np.array(chasis.convert("RGBA")).astype(int)
    h, w = a.shape[:2]
    m = (np.abs(a[..., :3] - np.array(MOLDURA)).max(axis=2) < 17) & (a[..., 3] > 200)

    # Solo el panel izquierdo, y sin los bordes del chasis: su marco exterior es
    # de este mismo gris y se colaria como si fuera una ranura mas.
    zona = np.zeros((h, w), bool)
    zona[int(h * 0.11):int(h * 0.91), int(w * 0.05):int(w * 0.29)] = True
    m &= zona

    def tramos(v, minimo):
        salida, ini = [], None
        for i, hay in enumerate(v):
            if hay and ini is None:
                ini = i
            elif not hay and ini is not None:
                if i - ini >= minimo:
                    salida.append((ini, i - 1))
                ini = None
        if ini is not None and len(v) - ini >= minimo:
            salida.append((ini, len(v) - 1))
        return salida

    # Las tapas: filas con mucha moldura. Van de dos en dos — la de arriba y la
    # de abajo de cada ranura.
    tapas = tramos(m.sum(1) > w * 0.05, 5)
    cajas = []
    for i in range(0, len(tapas) - 1, 2):
        (ta0, ta1), (tb0, tb1) = tapas[i], tapas[i + 1]
        laterales = tramos(m[(ta1 + tb0) // 2], 3)
        # Los laterales de una ranura ancha son dos tramos; los de una estrecha
        # se tocan y salen como uno solo. Se distinguen por el ancho.
        j = 0
        while j < len(laterales):
            if j + 1 < len(laterales) and laterales[j][1] - laterales[j][0] < 25:
                cajas.append((laterales[j][0], ta0, laterales[j + 1][1], tb1))
                j += 2
            else:
                cajas.append((laterales[j][0], ta0, laterales[j][1], tb1))
                j += 1
    # Fuera lo que no es una ranura: la linea divisoria vertical del panel (6 px
    # de ancho) y el cuadradito de 48x48 de abajo a la derecha, cuyas tapas son
    # demasiado cortas para que este metodo las vea como banda propia. Ninguno
    # de los dos aloja nada hoy.
    return sorted((c for c in cajas
                   if c[2] - c[0] >= 60 and c[3] - c[1] >= 60),
                  key=lambda c: (c[1], c[0]))


# Grosor de la moldura de una ranura. Medido: entre 13 y 14 px en las tres.
MOLDURA_GROSOR = 14


def interior(caja: tuple) -> tuple:
    """El hueco util de una ranura, sin su moldura. (x0, y0, ancho, alto)."""
    x0, y0, x1, y1 = caja
    return (x0 + MOLDURA_GROSOR, y0 + MOLDURA_GROSOR,
            (x1 - x0 + 1) - MOLDURA_GROSOR * 2,
            (y1 - y0 + 1) - MOLDURA_GROSOR * 2)


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
    cajas = medir_cajas(chasis)
    print(f"  chasis    {chasis.size[0]} x {chasis.size[1]}")
    print(f"  pantalla  x {x0}-{x1}  y {y0}-{y1}   ({x1 - x0} x {y1 - y0})")
    if len(cajas) != 3:
        raise SystemExit(
            f"Se esperaban 3 ranuras en el panel izquierdo y se han medido "
            f"{len(cajas)}: {cajas}.\nEl chasis ha cambiado de estructura; hay "
            f"que decidir que va en cada ranura antes de generar nada.")
    for nombre, c in zip(("cara", "botones", "saldo"), cajas):
        ix, iy, iw, ih = interior(c)
        print(f"  ranura    {nombre:<8} x {c[0]}-{c[2]}  y {c[1]}-{c[3]}"
              f"   hueco util {iw} x {ih} en {ix},{iy}")

    # En el orden de la rejilla, no en el del alfabeto. Y se falla aqui si
    # falta uno: un hueco en la pantalla principal es peor que no generar.
    iconos = [ARTE / "icons" / f"icon_{n}.png" for n in ORDEN]
    faltan = [p.name for p in iconos if not p.exists()]
    if faltan:
        raise SystemExit(f"Faltan iconos: {', '.join(faltan)}")
    # El lado lo manda la CELDA, no el primer fichero que haya: asi un icono
    # en pixel art de 25x25 se amplia por 4 y entra igual que los demas.
    lado = ICONO
    ampliados = []
    for p in iconos:
        original = Image.open(p)
        if original.size != (lado, lado):
            ampliados.append(f"{p.stem.replace('icon_','')} {original.size[0]}px")
        guardar(a_tamano(preparar(p), lado),
                SALIDA / f"{p.stem.replace('icon_', '')}.png")
    print(f"  iconos    {len(iconos)} de {lado}x{lado}")
    if ampliados:
        print(f"            ampliados por factor entero: {', '.join(ampliados)}")

    # Los botones se guardan YA REDUCIDOS al tamano al que se dibujan.
    #
    # El arte llega a 120x96 y en el chasis no cabe: la unica franja libre --la
    # de debajo de la pantalla-- tiene 58 px de alto. Se dibujan a 60x48, que es
    # la mitad exacta y sigue siendo divisible entre 1,2,3,4 y 6.
    #
    # Se reduce AQUI y no en el juego para que la textura mida lo que ocupa y se
    # dibuje 1:1 (regla 2 de docs/ui/dibujado.md). Dejar que lo encoja el juego
    # significa vecino mas proximo, que tira una fila de cada dos.
    botones = sorted((ARTE / "botones").glob("*.png"))
    for p in botones:
        guardar(reducir(preparar(p), BOTON_W, BOTON_H),
                SALIDA / f"boton_{p.stem}.png")
    print(f"  botones   {len(botones)} de {BOTON_W}x{BOTON_H} "
          f"(el arte llega a {Image.open(botones[0]).size[0]}x"
          f"{Image.open(botones[0]).size[1]})")

    celda = lado + AIRE * 2
    # La separacion VERTICAL no es la horizontal: tiene que dar cabida al
    # nombre debajo de cada icono. Se DEDUCE en vez de escribirse a mano, para
    # que cambiar el tamano del texto recoloque la rejilla sola.
    hueco_y = (TEXTO_ALTO - TEXTO_SOLAPE) + TEXTO_AIRE * 2
    rej_w = celda * 5 + HUECO * 4
    # Tres filas de celda, y TRES huecos: los dos de en medio mas el de abajo,
    # porque la ultima fila tambien lleva su nombre debajo.
    rej_h = celda * 3 + hueco_y * 3
    if rej_w > (x1 - x0) or rej_h > (y1 - y0):
        raise SystemExit(
            f"La rejilla no cabe: hace falta {rej_w}x{rej_h} y la pantalla mide "
            f"{x1 - x0}x{y1 - y0}. Baja AIRE, HUECO o TEXTO_ALTO.")
    rej_x = x0 + ((x1 - x0) - rej_w) // 2
    rej_y = y0 + ((y1 - y0) - rej_h) // 2

    # La cara, centrada en la ranura de arriba.
    cx, cy, cw, chh = interior(cajas[0])
    cara_x, cara_y = cx + (cw - CARA_LADO) // 2, cy + (chh - CARA_LADO) // 2
    if CARA_LADO > cw or CARA_LADO > chh:
        raise SystemExit(f"La cara ({CARA_LADO}) no cabe en {cw}x{chh}")

    # El teclado, centrado en la ranura de en medio.
    bx, by, bw, bh = interior(cajas[1])
    filas_b = -(-len(BOTONES) // BOTON_COLS)
    teclado_w = BOTON_COLS * BOTON_W + (BOTON_COLS - 1) * BOTON_SEP_X
    teclado_h = filas_b * BOTON_H + (filas_b - 1) * BOTON_SEP_Y
    if teclado_w > bw or teclado_h > bh:
        raise SystemExit(
            f"El teclado no cabe: hace falta {teclado_w}x{teclado_h} y la "
            f"ranura mide {bw}x{bh}. Baja BOTON_W/H o BOTON_SEP_*.")
    barra_x, barra_y = bx + (bw - teclado_w) // 2, by + (bh - teclado_h) // 2

    # El saldo, centrado en la ranura de abajo.
    sx, sy, sw, sh = interior(cajas[2])
    saldo_cx, saldo_cy = sx + sw // 2, sy + sh // 2

    print(f"\n  PARA PokePadScreen:")
    print(f"    REJ_X = {rej_x}, REJ_Y = {rej_y}")
    print(f"    CELDA = {celda}, HUECO_X = {HUECO}, HUECO_Y = {hueco_y}, "
          f"ICONO = {lado}")
    print(f"    TEXTO_ALTO = {TEXTO_ALTO}, TEXTO_SOLAPE = {TEXTO_SOLAPE}")
    print(f"    CARA_X = {cara_x}, CARA_Y = {cara_y}, CARA_LADO = {CARA_LADO}")
    print(f"    BARRA_X = {barra_x}, BARRA_Y = {barra_y}, BOTON_COLS = {BOTON_COLS}")
    print(f"    BOTON_W = {BOTON_W}, BOTON_H = {BOTON_H}, "
          f"BOTON_SEP_X = {BOTON_SEP_X}, BOTON_SEP_Y = {BOTON_SEP_Y}")
    print(f"    SALDO_CX = {saldo_cx}, SALDO_CY = {saldo_cy}")
    print(f"    (rejilla {rej_w}x{rej_h} en una pantalla de "
          f"{x1 - x0}x{y1 - y0}; teclado {teclado_w}x{teclado_h} en {bw}x{bh})")

    if args.maqueta:
        maqueta(Image.open(SALIDA / "pokepad.png"), iconos,
                rej_x, rej_y, celda, lado, hueco_y,
                (cara_x, cara_y), (barra_x, barra_y), (saldo_cx, saldo_cy))


def maqueta(chasis, iconos, rx, ry, celda, lado, hueco_y,
            cara, barra, saldo) -> None:
    """Monta la pantalla principal con las celdas dibujadas como las dibuja el
    juego: rectangulos planos. Ver docs/ui/prompts-arte-pokepad.md §4."""
    # Copia explicita: `chasis` se sigue usando abajo para restaurar las
    # esquinas mordidas, asi que no puede ser el mismo objeto que se pinta.
    chasis = chasis.convert("RGBA")
    out = chasis.copy()
    d = ImageDraw.Draw(out)
    for i, p in enumerate(iconos):
        cx = rx + (i % 5) * (celda + HUECO)
        cy = ry + (i // 5) * (celda + hueco_y)
        # Las quince estan CERRADAS hoy, asi que la maqueta las pinta cerradas:
        # ensenar la version abierta seria ensenar una pantalla que nadie ve.
        #
        # Menos UNA, la de en medio, que va con el raton encima. Es el unico
        # estado que no se puede juzgar de otra forma —solo aparece al pasar el
        # raton— y es justo donde vive la pregunta abierta: si el naranja del
        # resalte pega con el bisel del chasis o se pelea con el.
        encima = i == RESALTADA
        d.rectangle([cx, cy, cx + celda - 1, cy + celda - 1],
                    fill=CELDA_ENCIMA if encima else CELDA_CERRADA,
                    outline=BORDE_ENCIMA if encima else BORDE_CERRADA,
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
        # El nombre debajo. En el juego lo pinta la fuente de Minecraft; aqui
        # basta con una cualquiera del sistema para juzgar el ENCAJE, que es lo
        # unico que la maqueta tiene que responder.
        nombre = NOMBRES.get(p.stem.replace("icon_", ""), "")
        if nombre:
            try:
                fuente = ImageFont.truetype("arial.ttf", TEXTO_ALTO)
            except OSError:
                fuente = ImageFont.load_default()
            ancho = d.textlength(nombre, font=fuente)
            d.text((cx + celda / 2 - ancho / 2, cy + celda - TEXTO_SOLAPE),
                   nombre, font=fuente, fill=TEXTO_COLOR,
                   stroke_width=1, stroke_fill=TEXTO_CONTORNO)
    # El teclado, con los cinco sin destino apagados igual que en el juego. La
    # maqueta existe para aprobar el diseno, asi que si no ensena lo mismo que se
    # va a ver no sirve de nada.
    for i, n in enumerate(BOTONES):
        b = Image.open(SALIDA / f"boton_{n}.png").convert("RGBA")
        if n != "cerrar":
            a = np.array(b).astype(int)
            a[..., :3] = a[..., :3] * 128 // 255      # el mismo tinte del codigo
            b = Image.fromarray(a.astype(np.uint8), "RGBA")
        out.alpha_composite(b, (barra[0] + (i % BOTON_COLS) * (BOTON_W + BOTON_SEP_X),
                                barra[1] + (i // BOTON_COLS) * (BOTON_H + BOTON_SEP_Y)))

    # El hueco de la cabeza, para ver si cuadra dentro de su ranura.
    d.rectangle([cara[0], cara[1], cara[0] + CARA_LADO - 1, cara[1] + CARA_LADO - 1],
                fill=(150, 110, 90, 255), outline=(210, 170, 150, 255), width=2)

    # Y el saldo, con el mismo amarillo y el mismo centrado que el codigo.
    try:
        fuente = ImageFont.truetype("arial.ttf", 30)
    except OSError:
        fuente = ImageFont.load_default()
    d.text(saldo, "12,345", font=fuente, fill=(255, 225, 46, 255), anchor="mm")

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
