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
CELDA_CERRADA = (194, 204, 228, 255)    # cerrada: recula hacia el fondo
BORDE_CERRADA = (150, 161, 192, 255)
CELDA_ENCIMA = (255, 240, 220, 255)   # calido, no blanco: ver PokePadScreen
BORDE_ENCIMA = (243, 92, 12, 255)       # muestreado del bisel naranja del v4
TEXTO_COLOR = (22, 32, 58, 255)
TEXTO_CONTORNO = (242, 246, 255, 255)

# LA TARJETA DE ENTRENADOR. Mismos numeros que PokePadScreen.
#
# Las cinco Vias (PROG-001) con su nivel. El proyecto decidio a proposito que NO
# existe un "nivel de jugador": cinco reputaciones independientes hacen que el
# progreso sea un PERFIL y no una cifra. Eso es lo que enseña este panel, y
# hasta ahora no se veia en ninguna pantalla.
# TODAS EN BLANCO, no cada Via en su color: cinco colores en cinco lineas
# seguidas compiten entre si y convierten una tabla en un semaforo. Lo que
# separa una fila de otra es su nombre y cuantas estrellas lleva.
VIAS = ["Explorador", "Entrenador", "Coleccionista", "Comerciante", "Criador"]
# El aire entre la tarjeta y el bisel de su ranura. Se MIDE de donde empieza el
# fondo liso, no se cuenta desde el borde: ver `interior_util`.
TARJ_AIRE = 6
TARJ_FILA = 38
TARJ_COLOR = (255, 255, 255, 255)
ESTRELLA_SEP = 3

# LA MONEDA DE LOS LUNACOINS, al lado de su saldo. 40x40 porque la ranura tiene
# 55 de alto util MEDIDOS, y el aire que la separa del numero.
MONEDA = 40
MONEDA_AIRE = 10

# LA PLATA, la moneda normal. Va arriba a la izquierda del panel de cabecera --
# el mismo donde estan `ajustes` y `cerrar`, pegados a la derecha--, que es el
# unico hueco grande que quedaba libre y deja el saldo principal SIEMPRE a la
# vista. Abajo se queda solo la LunaCoin con su "+", que fue lo que se pidio.
PLATA_AIRE = 14
# Solo para la maqueta: unos niveles de ejemplo. En el juego los manda el
# servidor.
NIVELES_MAQUETA = [3, 2, 1, 4, 0]

# Que aplicaciones estan ABIERTAS. Mismo dato que App.java: la maqueta existe
# para aprobar el diseno, y una celda abierta no se ve igual que una cerrada.
ABIERTAS = {"pokedex"}

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
# ⚠ CRECIERON CON EL CHASIS v5, Y NO POR GUSTO. Al ensanchar la pantalla de 800
# a 956 la rejilla se quedo en 716 y sobraban 120 px de blanco a cada lado: la
# pantalla se veia vacia y la rejilla, perdida en medio.
#
# Lo que crece es LA CELDA, no el icono. Ampliar un icono de 100 a 140 es un
# factor de 1,4 y ablanda el dibujo; darle aire dentro de una celda mayor lo
# deja intacto y llena la pantalla igual. Y es lo que hace la referencia: sus
# iconos tambien tienen margen dentro de su celda.
AIRE = 14       # margen entre el icono y el borde de su celda
HUECO = 26      # separacion HORIZONTAL entre celdas

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
# ⚠ CURAR ES EL DECIMOSEXTO, y por eso cae en la PAGINA 2 de la rejilla.
#   No hay que quitar ninguna celda: la rejilla ya pagina y reordena (OrdenPad),
#   asi que las quince de siempre se quedan donde estaban.
ORDEN = ["pokedex", "cosmeticos", "trabajos", "misiones", "warps",
         "clan", "gts", "tienda", "tesoros", "wiki",
         "cazas", "kits", "mochila", "gyms", "explorar",
         "curar"]

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
    "curar": "Curar",
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

# ⚠ LOS SEIS BOTONES YA NO VIVEN JUNTOS. Decision del usuario sobre el v4:
#
#   atras / adelante  en el BISEL NARANJA de abajo, uno en cada mitad
#   cerrar            arriba a la derecha, en el panel oscuro junto al logo
#   inicio / ajustes / mas   en la ranura mediana del panel izquierdo
#
# Los tres sitios se MIDEN sobre el chasis; aqui solo se dice quien va en cual.
# El orden de la lista es el orden de los indices en PokePadScreen, asi que no
# se toca sin cambiarlo alli.
# ⚠ EN LA PANTALLA PRINCIPAL SOLO HAY CUATRO BOTONES. Decision del usuario, y
# las dos ausencias tienen motivo:
#
#   `mas`     no se usa aqui
#   `inicio`  no tiene sentido en la pantalla de inicio. Es el boton de VOLVER
#             a ella, asi que su sitio es dentro de cada sub-pantalla — el dia
#             que Cosmeticos tenga la suya, ahi si va
#
# El arte de los dos se conserva en arte/pokepad/botones/: lo que se quita es su
# hueco, no el fichero.
BOTONES = ["atras", "adelante", "ajustes", "cerrar"]
SITIOS = {
    "atras":    ("banda", 0),
    "adelante": ("banda", 1),
    # `ajustes` va PEGADO a `cerrar` arriba a la derecha: los dos son controles
    # de la ventana, no del contenido, y juntos se leen como tales.
    "ajustes":  ("panel", 0),
    "cerrar":   ("panel", 1),
    # ⚠ EL "+" DE COMPRAR LUNACOINS NO ES UN BOTON CON TEXTURA. El chasis ya
    # trae su zocalo dibujado --el cuadradito de 48x48 al lado del saldo-- asi
    # que meterle dentro un boton con SU PROPIO marco serian dos marcos, uno
    # dentro de otro. Lo que hace falta ahi es solo la cruz, y esa la dibuja
    # PokePadScreen con dos rectangulos.
}

# Dos tamanos, y cada uno lo manda su sitio:
#
#   panel   80x64, dos tercios del arte. Dos de esos mas su hueco caben de
#           sobra en los 360x93 del panel de arriba
#   banda   60x48, la mitad exacta. Se pidieron mas grandes que los 45x36 con
#           los que empezaron; a 48 de alto sobresalen 5 px por arriba y 5 por
#           abajo del bisel, que mide 37. No estorba: la carita verde que
#           estaba ahi ocupaba 38 con su halo y se salia igual
BOTON_PANEL = (80, 64)

# LA BARRA DE SESION, en la banda de cabecera. De izquierda a derecha:
#
#   [moneda Plata][numero][+]   [moneda Luna][numero][+]   [ajustes][cerrar]
#
# Los dos saldos arriba y juntos: son el mismo tipo de dato y se comparan de un
# vistazo. Abajo a la izquierda ya no hay saldo -- esa ranura queda libre.
BARRA_NUM = 130          # el hueco reservado al numero
BARRA_MAS = (50, 40)     # el "+" de cada saldo
BARRA_AIRE = 10          # entre moneda, numero y "+"
BARRA_HUECO = 34         # entre un saldo y el siguiente
BARRA_MARGEN = 26        # aire hasta el borde derecho de la banda
# El cuadradito mide 48x48 MEDIDOS. El boton va a 40x32 y se apoya un poco
# sobre su moldura: dentro del hueco util (33x33) tendria que bajar a 30x24 y
# un '+' de 24 px de alto es un adorno, no un boton que invita a pulsarlo.
BOTON_CUADRO = (40, 32)
BOTON_BANDA = (60, 48)
BOTON_SEP_X = 20

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


def abrir_hueco(im: Image.Image) -> Image.Image:
    """Vacia el hueco de dentro del arco del candado.

    EL PROBLEMA

    El arte llega con ese hueco RELLENO DE BLANCO OPACO en vez de vacio. Sobre
    la celda clara se ve como una mancha blanca dentro del arco — el usuario lo
    describio como "deberia verse la celda ahi" y tenia razon: es un agujero,
    hay que ver a traves.

    `quitar_fondo` no lo pilla porque solo borra lo CONECTADO AL BORDE, y este
    hueco esta encerrado por el propio arco.

    POR QUE NO SE BORRA "TODO EL BLANCO PURO"

    Porque se midio y no cuela: `misiones` tiene 656 px de blanco puro (el papel
    del portapapeles) y `warps` 446 (el resplandor de la plataforma). Una regla
    de color se los llevaria por delante. Asi que se busca por TOPOLOGIA, que es
    lo que de verdad distingue un agujero de una superficie blanca:

      1. se baja por la columna central hasta cruzar el trazo oscuro del arco
      2. el primer blanco que hay DESPUES es el hueco
      3. se inunda desde ahi, y solo por ahi

    Un dibujo sin agujero no tiene ese blanco encerrado detras de un trazo
    oscuro, asi que la funcion no encuentra semilla y no toca nada.
    """
    a = np.array(im.convert("RGBA")).astype(int)
    h, w = a.shape[:2]
    r, g, b, al = (a[..., i] for i in range(4))
    claro = ((al > 200) & (r >= 238) & (g >= 238) & (b >= 238)
             & (np.max(a[..., :3], axis=2) - np.min(a[..., :3], axis=2) <= 8))
    oscuro = (al > 200) & (np.max(a[..., :3], axis=2) < 110)

    # La semilla: bajando por el centro, el primer claro tras un oscuro.
    cx = w // 2
    visto_oscuro, semilla = False, None
    for y in range(h // 2):
        if oscuro[y, cx]:
            visto_oscuro = True
        elif visto_oscuro and claro[y, cx]:
            semilla = y
            break
    if semilla is None:
        return im

    marca = np.zeros((h, w), bool)
    marca[semilla, cx] = True
    while True:
        antes = marca.sum()
        crece = marca.copy()
        crece[1:, :] |= marca[:-1, :]
        crece[:-1, :] |= marca[1:, :]
        crece[:, 1:] |= marca[:, :-1]
        crece[:, :-1] |= marca[:, 1:]
        marca = crece & claro
        if marca.sum() == antes:
            break
    # Si la mancha llega al borde no era un hueco: era el fondo. Se deja.
    if marca[0, :].any() or marca[-1, :].any() or marca[:, 0].any() or marca[:, -1].any():
        return im
    # Y un minimo de tamano. Sin el, en `misiones` se comia 1 px y en `warps` 3:
    # inofensivo, pero es ruido, y un agujero de tres pixeles no es un agujero.
    if marca.sum() < 40:
        return im
    a[marca, 3] = 0
    return Image.fromarray(a.astype(np.uint8), "RGBA")


def a_tamano(im: Image.Image, lado: int) -> Image.Image:
    """Lleva un icono a `lado` x `lado`. Las dos direcciones, con su filtro.

    AMPLIAR solo por factor ENTERO, y con vecino mas proximo. Existe para que el
    arte en pixel art pueda entrar tal cual: un icono de 25x25 es 100/25 = 4
    veces mas pequeno, asi que cada pixel se convierte en un bloque de 4x4 y el
    resultado es exactamente lo que dibujo el artista, sin inventar medios tonos
    ni redondear bordes. Si el factor no fuera entero se aborta: un 1,7
    emborrona el pixel art y ademas mueve las lineas de sitio.

    REDUCIR, en cambio, vale desde cualquier tamano — con `reducir()`, que
    promedia con Lanczos sobre alfa premultiplicado. **Esto es lo que permite
    que el arte llegue como salga del generador**, que es a 1024x1024: antes
    habia que reescalarlo a mano a 100 antes de meterlo en la carpeta, y ese
    paso manual es donde se cuela un icono a 1023 o reescalado con el filtro
    equivocado. Reducir una ilustracion suavizada no tiene el problema de
    ampliar pixel art: no hay rejilla que respetar.
    """
    if im.size == (lado, lado):
        return im
    ancho, alto = im.size
    if ancho != alto:
        raise SystemExit(f"Un icono mide {ancho}x{alto} y no es cuadrado. "
                         f"Reexportalo cuadrado.")
    if ancho > lado:
        return reducir(im, lado, lado)
    if lado % ancho != 0:
        raise SystemExit(f"Un icono mide {ancho}x{alto} y no se amplia a "
                         f"{lado}x{lado} por un factor entero. Reexportalo a "
                         f"{lado} o a un divisor suyo (25, 50, 100).")
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



# ---------------------------------------------------------------------------
# LAS ESTRELLAS DE LA TARJETA DE ENTRENADOR
#
# Cinco por Via, llenas hasta el nivel que tengas. Una estrella dice "nivel 3 de
# 5" sin leer nada; un cuadrado no dice nada, hay que aprenderselo.
#
# Se DIBUJAN, y a cuatro veces su tamano antes de reducir: una estrella de cinco
# puntas a 15 px trazada directamente sale con las puntas dentadas. Reducida
# desde 60 con Lanczos, los bordes salen suaves y se lee como una estrella y no
# como una mancha.
# ---------------------------------------------------------------------------
ESTRELLA = 14          # el lado al que se dibuja en el Pad
ESTRELLA_LLENA = (255, 200, 60, 255)
ESTRELLA_BORDE = (140, 96, 16, 255)
ESTRELLA_VACIA = (58, 64, 82, 255)
ESTRELLA_VACIA_BORDE = (86, 94, 116, 255)


def _puntas(lado: int, radio: float, hundido: float) -> list:
    """Los diez vertices de una estrella de cinco puntas."""
    import math
    centro = lado / 2.0
    puntos = []
    for i in range(10):
        # Se empieza en -90 grados para que la punta mire ARRIBA. Sin eso la
        # estrella sale girada 18 grados y parece torcida sin saber por que.
        ang = math.radians(-90 + i * 36)
        r = radio if i % 2 == 0 else radio * hundido
        puntos.append((centro + r * math.cos(ang), centro + r * math.sin(ang)))
    return puntos


def estrella(llena: bool, lado: int = ESTRELLA) -> Image.Image:
    grande = lado * 4
    img = Image.new("RGBA", (grande, grande), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # 0,42 es la proporcion clasica entre el radio interior y el exterior. Con
    # mas, la estrella se hincha y parece una flor; con menos, se afila y a este
    # tamano las puntas desaparecen al reducir.
    d.polygon(_puntas(grande, grande * 0.46, 0.42),
              fill=ESTRELLA_LLENA if llena else ESTRELLA_VACIA,
              outline=ESTRELLA_BORDE if llena else ESTRELLA_VACIA_BORDE,
              width=max(2, grande // 20))
    return reducir(img, lado, lado)


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

# La franja de la que se copia el color al borrar la carita. Elegida MIDIENDO
# donde estan los bigotes: ocupan x 722-778 y 932-987, asi que de 1020 a 1240 el
# bisel de abajo esta limpio de punta a punta.
DONANTE = (1020, 1240)


def medir_arco(chasis: Image.Image, rej_y: int) -> tuple:
    """Donde esta el arco blanco que sube entre las dos orejas de Rotom.

    Devuelve `(x0, x1, cima)` en pixeles del arte.

    ⚠ SE MIDE, NO SE ESCRIBE. Es el mismo motivo que todo lo demas de este
    fichero: el chasis ya ha cambiado de estructura cinco veces, y una
    coordenada a mano se queda mintiendo EN SILENCIO -- el codigo dibuja la
    boca donde estaba en la version anterior y nada falla.

    El arco se reconoce porque es la unica parte del claro de la pantalla que
    sube POR ENCIMA de su borde recto. Se busca, columna a columna, donde
    empieza el claro; las columnas donde empieza mas arriba que el resto son
    el arco.
    """
    a = np.array(chasis.convert("RGBA"))
    r, g, b, al = (a[..., i].astype(int) for i in range(4))
    # El claro de la pantalla: casi blanco azulado. Se ignora la franja de
    # arriba, donde esta el logo, que tambien es claro.
    claro = (al > 200) & (r > 200) & (g > 210) & (b > 230)
    claro[:150, :] = False

    primero = {}
    for x in range(a.shape[1]):
        col = np.nonzero(claro[:, x])[0]
        if len(col):
            primero[x] = int(col.min())
    if not primero:
        return None
    # El borde recto de la pantalla es el valor MAS COMUN; el arco, lo que sube.
    from collections import Counter
    recto = Counter(primero.values()).most_common(1)[0][0]
    arco = sorted(x for x, y in primero.items() if y < recto - 6)
    if not arco:
        return None
    # Un solo tramo continuo: si hubiera dos, uno seria otra cosa.
    tramos, ini = [], arco[0]
    for i in range(1, len(arco)):
        if arco[i] - arco[i - 1] > 8:
            tramos.append((ini, arco[i - 1])); ini = arco[i]
    tramos.append((ini, arco[-1]))
    x0, x1 = max(tramos, key=lambda tr: tr[1] - tr[0])
    cima = min(primero[x] for x in range(x0, x1 + 1) if x in primero)
    return x0, x1, cima


def poner_boca(chasis: Image.Image, rej_y: int) -> Image.Image:
    """Pega la boca de Rotom en el arco, entre los ojos y encima de la rejilla.

    Va INCRUSTADA en el chasis y no dibujada por el codigo del juego porque no
    cambia nunca: es parte del dibujo, como los ojos. Dibujarla en tiempo de
    ejecucion seria una textura mas que cargar para pintar siempre lo mismo.

    ⚠ El limite de abajo es `rej_y`, donde empieza la rejilla. La rejilla se
    dibuja DESPUES del chasis, asi que una boca que baje de ahi no queda
    "detras": queda TAPADA por la primera fila de celdas.
    """
    origen = ARTE / "icons" / "boca_rotom.png"
    if not origen.exists():
        print(f"  boca      FALTA {origen.relative_to(RAIZ)}")
        return chasis
    arco = medir_arco(chasis, rej_y)
    if arco is None:
        print("  boca      no se encontro el arco entre los ojos; no se pone")
        return chasis
    x0, x1, cima = arco

    boca = preparar(origen)
    # ⚠ RECORTAR AL CONTENIDO ANTES DE ESCALAR.
    #
    # El PNG viene en un lienzo de 100x100 con la boca dentro y transparencia
    # alrededor. Escalando el lienzo, lo que se ajusta al hueco es el AIRE: la
    # boca salio a 39x39 --cuadrada-- cuando en realidad mide 88x59 y ocupaba
    # menos de la mitad de esa caja. Recortada, el hueco lo llena la boca.
    caja = boca.getbbox()
    if caja:
        boca = boca.crop(caja)
    aire = 8
    alto_max = max(1, rej_y - (cima + aire) - aire)
    ancho_max = max(1, (x1 - x0) - aire * 2)
    escala = min(alto_max / boca.height, ancho_max / boca.width)
    w, h = max(1, round(boca.width * escala)), max(1, round(boca.height * escala))
    boca = reducir(boca, w, h)

    # Centrada en el arco horizontalmente, y verticalmente en el hueco que
    # queda entre la cima del arco y la rejilla.
    px = (x0 + x1) // 2 - w // 2
    py = cima + aire + max(0, (alto_max - h) // 2)
    fuera = chasis.convert("RGBA")
    fuera.alpha_composite(boca, (px, py))
    print(f"  boca      {w}x{h} en {px},{py}  "
          f"(arco x {x0}..{x1}, cima y={cima}, rejilla y={rej_y})")
    return fuera


def quitar_carita(im: Image.Image) -> Image.Image:
    """Borra la carita verde de la boca de Rotom. Decision del usuario.

    SE HACE AQUI Y NO PIDIENDO OTRO CHASIS, y merece la pena decir por que: es
    un parche de sesenta pixeles sobre una banda que es HORIZONTALMENTE
    UNIFORME. El bisel naranja de abajo tiene la misma seccion en toda su
    longitud, asi que se copia el trozo de al lado y no queda ni rastro — no hay
    que inventarse ni un pixel, que es lo que convierte un retoque en un parche
    que se nota.

    Y es idempotente: si algun dia llega un chasis que ya no la trae, esta
    funcion no encuentra verde y devuelve la imagen tal cual.
    """
    a = np.array(im.convert("RGBA"))
    r, g, b, al = (a[..., i].astype(int) for i in range(4))
    verde = (g > 120) & (g - r > 50) & (g - b > 40) & (al > 200)
    # Solo en la mitad de abajo: los ojos de Rotom son azules, pero cualquier
    # verde de arriba seria otra cosa y no hay que tocarlo.
    verde[:a.shape[0] // 2] = False
    if not verde.any():
        return im

    ys, xs = np.where(verde)

    # SE RECONSTRUYE POR FILAS, no se copia un bloque de al lado.
    #
    # Copiar un bloque fue el primer intento y salio mal de una forma
    # instructiva: a los lados de la carita estan los BIGOTES de Rotom, asi que
    # el trozo donante los traia consigo y donde habia una cara aparecian dos
    # bigotes de mas. La banda es uniforme en HORIZONTAL, no en vertical: lo que
    # se puede copiar es el color de la fila, no un rectangulo.
    donante = a[:, DONANTE[0]:DONANTE[1], :].astype(int)
    # Mediana y no media: inmune a que se cuele algo raro en la franja donante.
    fila = np.median(donante, axis=1)

    # Y la caja no se estima: se busca. Todo lo que en esta ventana no se
    # parezca a la mediana de su fila es la cara o su halo blanco --que no es
    # verde y por eso no sale en la deteccion de arriba--, porque los bigotes
    # mas cercanos quedan fuera de la ventana.
    vx0, vx1 = max(0, xs.min() - 40), min(a.shape[1], xs.max() + 41)
    vy0, vy1 = max(0, ys.min() - 40), min(a.shape[0], ys.max() + 41)
    ventana = a[vy0:vy1, vx0:vx1, :3].astype(int)
    intruso = np.abs(ventana - fila[vy0:vy1, None, :3]).sum(2) > 60
    fy, fx = np.where(intruso)
    y0, y1 = vy0 + fy.min() - 3, vy0 + fy.max() + 4
    x0, x1 = vx0 + fx.min() - 3, vx0 + fx.max() + 4

    # Y el color con el que se rellena NO es el del donante lejano: es el
    # promedio de las cuatro columnas limpias que quedan a cada lado del parche,
    # fila a fila. Con la mediana lejana quedaba una costura de 23 niveles en el
    # filo inferior del bisel --poco, pero una linea vertical de 40 px de alto se
    # ve--. Cogiendo el color de al lado, la costura es CERO por construccion.
    izq = a[y0:y1, x0 - 6:x0 - 2, :].astype(int).mean(1)
    der = a[y0:y1, x1 + 2:x1 + 6, :].astype(int).mean(1)
    a[y0:y1, x0:x1] = np.round((izq + der) / 2)[:, None, :].astype(a.dtype)
    return Image.fromarray(a, "RGBA")


def medir_cajas(chasis: Image.Image, hasta_x: int = None) -> list:
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
    #
    # ⚠ EL LIMITE DERECHO ES LA PANTALLA, NO UN PORCENTAJE DEL ANCHO. Estaba en
    # `w * 0.29` y funcionaba con el chasis de 1380; al ensancharlo a 1848 ese
    # 5 % de la izquierda paso de 69 a 92 px y dejo FUERA el borde izquierdo de
    # la ranura mediana, que empieza en 80. El script dijo "se esperaban 3
    # ranuras y se han medido 2", que es lo correcto, pero la culpa era de la
    # zona y no del arte.
    tope = hasta_x if hasta_x else int(w * 0.29)
    zona = np.zeros((h, w), bool)
    # El limite izquierdo es fijo y no un porcentaje: el marco exterior del
    # chasis esta SIEMPRE en x 12..24 --ensanchar inserta cuerpo por el medio, no
    # por el borde-- y es del mismo gris que las molduras. Con un porcentaje del
    # ancho se colaba dentro y anadia una banda falsa que desparejaba las tapas:
    # el script encontraba las seis y no acertaba ni una ranura.
    zona[int(h * 0.11):int(h * 0.91), 60:tope] = True
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
    # ⚠ SE DESCARTA LO QUE TOCA EL BORDE DE LA ZONA. Una banda que empieza en la
    # primera fila de la ventana de busqueda no es una moldura: es algo mas
    # grande que la ventana ha cortado. Colada, desparejaba las tapas de dos en
    # dos y el script no acertaba NI UNA ranura pese a encontrarlas todas.
    y_ini, y_fin = int(h * 0.11), int(h * 0.91)
    tapas = [t for t in tramos(m.sum(1) > w * 0.05, 5)
             if t[0] > y_ini and t[1] < y_fin - 1]
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


def medir_banda(chasis: Image.Image, pantalla: tuple) -> tuple:
    """El bisel NARANJA de debajo de la pantalla. (x0, y0, ancho, alto).

    Se filtra por verde bajo (`g < 135`) y no solo por "rojizo": el chasis tiene
    ademas una linea AMBAR (243,176,65) que cruza todo el ancho, y con un filtro
    de rojo a secas la banda salia de 78 px de alto en vez de 37.
    """
    a = np.array(chasis.convert("RGBA")).astype(int)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    m = (r > 190) & (g > 55) & (g < 135) & (b < 85) & (a[..., 3] > 200)
    m[:pantalla[3]] = False                      # solo lo que hay bajo la pantalla
    filas = np.where(m.sum(1) > 300)[0]
    cols = np.where(m[(filas.min() + filas.max()) // 2])[0]
    return (int(cols.min()), int(filas.min()),
            int(cols.max() - cols.min() + 1), int(filas.max() - filas.min() + 1))


def medir_cuadro(chasis: Image.Image, saldo: tuple) -> tuple:
    """El cuadradito de al lado del saldo. (x0, y0, ancho, alto).

    Se busca a la derecha de la ranura del saldo y se descartan las columnas
    cuya moldura ocupa TODA la altura: esa es la linea divisoria vertical del
    panel, que es del mismo gris y estaria pegada al cuadro.
    """
    a = np.array(chasis.convert("RGBA")).astype(int)
    m = ((np.abs(a[..., :3] - np.array(MOLDURA)).max(axis=2) < 17)
         & (a[..., 3] > 200))
    y0, y1 = saldo[1] + 6, saldo[3] - 4
    x0, x1 = saldo[2] + 8, saldo[2] + 90
    sub = m[y0:y1, x0:x1]
    alto = sub.sum(0)
    cols = [c for c in np.where(alto > 3)[0] if alto[c] < (y1 - y0) * 0.7]
    filas = np.where(sub[:, cols].sum(1) > 3)[0]
    if not cols or not len(filas):
        raise SystemExit("No se encuentra el cuadradito al lado del saldo")
    return (x0 + min(cols), y0 + filas.min(),
            max(cols) - min(cols) + 1, filas.max() - filas.min() + 1)


# El gris liso del fondo de una ranura, ya pasados la moldura y el bisel.
FONDO_RANURA = (41, 43, 54)
# Y el de la banda de cabecera, un punto mas oscuro.
FONDO_CABECERA = (31, 33, 43)


def medir_panel_superior(chasis: Image.Image) -> tuple:
    """La banda lisa de la cabecera, a la DERECHA de la placa del logo.

    Es donde va la barra de sesion: los dos saldos con sus «+» y los botones de
    ajustes y cerrar.

    ⚠ SE BUSCA EL TRAMO LISO MAS ANCHO, no una ventana fija en porcentajes del
    ancho. Estaba en `w * 0.65` y con el chasis de 1380 caia justo despues de la
    placa; al ensanchar a 1848 ese 65 % se fue a x 1201 y la banda salia de 418
    px en vez de los 857 que tiene de verdad -- o sea, la mitad del sitio
    disponible desaparecia sin que nada fallara.
    """
    a = np.array(chasis.convert("RGBA")).astype(int)
    h, w = a.shape[:2]
    liso = ((np.abs(a[..., :3] - np.array(FONDO_CABECERA)).max(axis=2) < 8)
            & (a[..., 3] > 200))
    # A media altura de la banda, el tramo liso mas largo.
    cy = int(h * 0.17)
    # Solo la mitad derecha: a la izquierda esta el panel del jugador, que es
    # del mismo gris y siempre gana en anchura.
    xs = np.where(liso[cy])[0]
    xs = xs[xs > w * 0.5]
    if not len(xs):
        raise SystemExit("No se encuentra la banda de la cabecera")
    tramos_x, ini_t, ant = [], None, -9
    for x in xs:
        if x != ant + 1:
            if ini_t is not None:
                tramos_x.append((ini_t, ant))
            ini_t = x
        ant = x
    tramos_x.append((ini_t, ant))
    x0, x1 = max(tramos_x, key=lambda t: t[1] - t[0])
    # Y su alto, por la columna del medio.
    col = liso[:, (x0 + x1) // 2]
    ys = [y for y in range(int(h * 0.05), int(h * 0.25)) if col[y]]
    return (int(x0), min(ys), int(x1 - x0 + 1), max(ys) - min(ys) + 1)


# El aire entre la ficha y los bordes de su hueco.
FICHA_AIRE = 30

# ⚠ EL LADO DERECHO LLEVA MAS AIRE QUE EL IZQUIERDO, Y ES A PROPOSITO.
#
# Peticion del usuario: las celdas quedaban pegadas a la linea naranja que
# separa el panel de la pantalla. No es simetria por simetria -- a la izquierda
# no hay nada contra lo que chocar, y a la derecha si.
#
# Mueve las celdas Y las medallas de una vez, porque las dos salen del mismo
# borde util. Si algun dia hay que separarlas mas, se toca este numero y ya.
FICHA_AIRE_DER = FICHA_AIRE + 18

# EL PANEL DE SESION, bajo la cara.
#
# Sustituye a la tarjeta de las Vias (decision del usuario, 2026-08-17): en vez
# de cinco reputaciones en estrellas, los datos que se miran a diario. El orden
# es el que el pidio, y no es arbitrario: primero lo que tienes, luego a quien
# perteneces, luego lo que has ganado.
FILAS_INFO = ("clan", "trabajo", "division")
FILA_ICONO = 36          # el lado del icono de cada fila
FILA_ALTO = 52           # lo que ocupa una fila entera, icono incluido
FILA_CELDA = FILA_ALTO - 6   # la celda; los 6 que sobran son el aire de abajo
FILA_PAD = 10                # margen interior de la celda

# Los mismos colores que PokePadScreen. El panel del chasis se MIDIO: #222529,
# luma 37, o sea casi negro. Por eso estas celdas van mas CLARAS que su fondo,
# al reves que las de la rejilla -- la regla no es "la celda va oscura", es "la
# celda tiene que separarse de su fondo".
PANEL_FONDO = (34, 37, 41, 255)
FILA_FONDO = (52, 59, 77, 255)
FILA_BORDE = (89, 100, 127, 255)

# El "+" se DIBUJA, no se pega. Era la unica pieza del panel con bisel y
# volumen propios encima de cinco filas planas, y cantaba.
MAS_LADO, MAS_GROSOR, MAS_BRAZO = 34, 6, 18
MAS_APAGADO = (124, 133, 155, 255)
FILA_AIRE = 14           # aire entre el bloque de filas y el de medallas

# Las medallas. Ocho por region y dos filas: Kanto y Johto, que son las
# generaciones encendidas (D-017). Se ensenan SIEMPRE las dieciseis, apagadas
# las que no se tienen — un hueco vacio no dice cuantas faltan.
MEDALLA_COLS = 8
MEDALLA_FILAS = 2
MEDALLA_SEP = 3

# ⚠ LAS MEDALLAS VIVEN EN EL MISMO MARGEN QUE EL TEXTO, y se intento lo otro.
#
# Se probo a centrarlas en el hueco entero para que crecieran de 32 a 36 --se
# pidio que fueran "un poquito mas grandes"-- y EN LA MAQUETA SE VIO que la
# octava columna cruzaba el divisor naranja del panel. O sea: `medir_ficha`
# devuelve un hueco MAS ANCHO que la ranura que se ve dibujada, porque mide la
# zona hundida y el divisor cae dentro de ella.
#
# El margen de texto (FICHA_AIRE) si coincide con el borde visible, asi que es
# el que manda para todo. Que las medallas midan 32 y no 36 es el precio de
# tener ocho por fila; el sitio se gana con el aire de arriba, no invadiendo.

# El orden de gimnasio, no el alfabetico: es como se consiguen y como se
# recuerdan. Tiene que ser EL MISMO que el de PokePadScreen.MEDALLAS.
MEDALLA_ORDEN = (
    "kanto_boulder", "kanto_cascade", "kanto_thunder", "kanto_rainbow",
    "kanto_soul", "kanto_marsh", "kanto_volcano", "kanto_earth",
    "johto_zephyr", "johto_hive", "johto_plain", "johto_fog",
    "johto_storm", "johto_mineral", "johto_glacier", "johto_rising",
)
# Cuantas se ensenan conseguidas en la maqueta. Ni cero ni dieciseis: con cero
# no se ve como queda una encendida, y con todas no se ve el contraste, que es
# justo lo que hay que aprobar.
MEDALLAS_MAQUETA = 5

ETIQUETAS = {"clan": "Clan", "trabajo": "Trabajo", "division": "Division"}


def medir_ficha(chasis: Image.Image, cara: tuple) -> tuple:
    """El hueco libre del panel izquierdo, DEBAJO de la ranura de la cara.

    Es donde va la ficha de sesion. Se mide en vez de escribirse porque el panel
    ya ha cambiado tres veces, y cada vez las medidas a mano se quedaron
    mintiendo en silencio.
    """
    a = np.array(chasis.convert("RGBA")).astype(int)
    h, w = a.shape[:2]
    # El gris del panel, tomado de un punto que seguro esta dentro: justo
    # debajo de la ranura de la cara y a su misma altura horizontal.
    col = a[cara[3] + 30, (cara[0] + cara[2]) // 2, :3]
    liso = ((np.abs(a[..., :3] - col).max(axis=2) < 8) & (a[..., 3] > 200))
    liso[:cara[3] + 10] = False          # todo lo que hay encima de la cara
    liso[:, int(w * 0.35):] = False      # y el resto del chasis
    filas = np.where(liso.sum(1) > 80)[0]
    if not len(filas):
        raise SystemExit("No se encuentra el hueco de la ficha bajo la cara")
    cy = (filas.min() + filas.max()) // 2
    xs = np.where(liso[cy])[0]
    return (int(xs.min()), int(filas.min()),
            int(xs.max() - xs.min() + 1), int(filas.max() - filas.min() + 1))


def interior_util(chasis: Image.Image, caja: tuple) -> tuple:
    """El hueco REALMENTE util de una ranura. (x0, y0, ancho, alto).

    `interior()` descuenta la moldura y ya, pero eso no basta: dentro de la
    moldura hay ademas un BISEL OSCURO de otros 14 px, arriba y a la izquierda,
    que es la sombra interior del hueco. El texto de la tarjeta caia justo
    encima y se leia sucio -- lo vio el usuario antes que yo.

    Asi que no se descuenta un numero: se busca donde empieza de verdad el gris
    liso del fondo. Si el chasis cambia el grosor del bisel, esto lo sigue.
    """
    a = np.array(chasis.convert("RGBA")).astype(int)
    x0, y0, x1, y1 = caja
    liso = ((np.abs(a[..., :3] - np.array(FONDO_RANURA)).max(axis=2) < 10)
            & (a[..., 3] > 200))
    dentro = liso[y0:y1 + 1, x0:x1 + 1]
    # Por mayoria de fila y de columna: dentro hay dibujos y sombras sueltas, y
    # un minimo/maximo se iria detras del primer pixel perdido.
    cols = np.where(dentro.mean(0) > 0.55)[0]
    filas = np.where(dentro.mean(1) > 0.55)[0]
    if not len(cols) or not len(filas):
        raise SystemExit(f"No se encuentra el fondo liso de la ranura {caja}")
    return (x0 + cols.min(), y0 + filas.min(),
            cols.max() - cols.min() + 1, filas.max() - filas.min() + 1)


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

    # ⚠⚠⚠ ESTE BORRADO SE LLEVO POR DELANTE TRES TEXTURAS QUE NO SON SUYAS, Y
    #     DEJO SEIS PANTALLAS EN MAGENTA (2026-08-23).
    #
    #     Borraba `*.png` a secas, y en esta carpeta no vive solo lo que genera
    #     este script: `pokepad_cosmeticos.png`, `lunacoin_oro.png` y
    #     `boton_mas_luna.png` las pone otra mano. Al regenerar para añadir el
    #     icono de curar, desaparecieron -- y con ellas el chasis de Cosmeticos,
    #     Clan, Tienda, Misiones, Trabajos e Inicial, que lo comparten.
    #
    #     ⚠ Y NO DIO NINGUN ERROR. Compilo, se desplego y solo se vio al abrir
    #       una pantalla en el juego: fondo magenta, que es la textura ausente
    #       de Minecraft.
    #
    #     Es exactamente la trampa que ya estaba documentada de `gen_neon.py`
    #     («empezaba borrando assets/lunaneon ENTERO»). Se repitio porque la
    #     leccion estaba escrita para OTRO script.
    #
    #     Hoy solo se borra LO QUE ESTE SCRIPT SABE GENERAR. Lo demas se queda,
    #     y si sobra algo se dice en vez de borrarlo.
    mias = {"pokepad.png"}
    mias.update(f"{n}.png" for n in ORDEN)
    mias.update(f"boton_{n}.png" for n in BOTONES)
    mias.update((CANDADO + ".png", "estrella.png", "estrella_vacia.png",
                 "plata.png", "lunacoin.png",
                 "fila_clan.png", "fila_trabajo.png", "fila_division.png"))
    mias.update({m + ".mcmeta" for m in list(mias)})

    ajenas = []
    for viejo in list(SALIDA.glob("*.png")) + list(SALIDA.glob("*.mcmeta")):
        if viejo.name in mias:
            viejo.unlink()
        else:
            ajenas.append(viejo.name)
    if ajenas:
        print("  CONSERVADAS (no las genera este script):",
              ", ".join(sorted(n for n in ajenas if not n.endswith(".mcmeta"))))

    print("POKEPAD")
    chasis = quitar_carita(Image.open(ARTE / "fondo_base.png").convert("RGBA"))
    # ⚠ El chasis NO se guarda todavia: falta pegarle la boca, y para saber
    #   donde cabe hace falta `rej_y`, que se calcula mas abajo. Se guarda al
    #   final, en cuanto esta esa medida.
    x0, y0, x1, y1 = medir_pantalla(chasis)
    cajas = medir_cajas(chasis, hasta_x=x0 - 20)
    print(f"  chasis    {chasis.size[0]} x {chasis.size[1]}")
    print(f"  pantalla  x {x0}-{x1}  y {y0}-{y1}   ({x1 - x0} x {y1 - y0})")
    if not cajas:
        raise SystemExit("No se encuentra la ranura de la cara en el chasis.")
    c = cajas[0]
    ix, iy, iw, ih = interior(c)
    print(f"  ranura    cara     x {c[0]}-{c[2]}  y {c[1]}-{c[3]}"
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
        # `abrir_hueco` solo actua donde hay un agujero relleno; en los
        # demas iconos no encuentra semilla y devuelve la imagen tal cual.
        guardar(a_tamano(abrir_hueco(preparar(p)), lado),
                SALIDA / f"{p.stem.replace('icon_', '')}.png")
    print(f"  iconos    {len(iconos)} de {lado}x{lado}")
    if ampliados:
        print(f"            ampliados por factor entero: {', '.join(ampliados)}")

    # El candado de la segunda pagina. Si el arte no ha llegado, se dibuja uno
    # provisional en vez de abortar: asi la pagina se puede montar y desplegar
    # hoy, y el dia que llegue el PNG lo sustituye sin tocar una linea de codigo.
    # Se busca por varios nombres: el arte llega a veces a `icons/` y con el
    # nombre que le puso el disenador. Fallar por eso seria pedantesco.
    # De las que haya, LA MAS RECIENTE. El arte llega a veces a `icons/` y con
    # el nombre que le puso el disenador, y la version anterior se queda al
    # lado: elegir por orden de lista significaria seguir usando la vieja.
    candidatas = [c for c in (ARTE / "lunacoin.png",
                              *sorted((ARTE / "icons").glob("icon_lunacoin*.png")))
                  if c.exists()]
    moneda = max(candidatas, key=lambda c: c.stat().st_mtime, default=ARTE / "lunacoin.png")
    if moneda.exists():
        guardar(a_tamano(preparar(moneda), MONEDA), SALIDA / "lunacoin.png")
        print(f"  moneda    {moneda.name} a {MONEDA}x{MONEDA}")
    else:
        print(f"  moneda    FALTA {moneda.relative_to(RAIZ)}")

    plata = ARTE / "icon_plata.png"
    if not plata.exists():
        plata = ARTE / "icons" / "icon_plata.png"
    if plata.exists():
        guardar(a_tamano(preparar(plata), MONEDA), SALIDA / "plata.png")
        print(f"  plata     {plata.name} a {MONEDA}x{MONEDA}")

    # LOS ICONOS DEL PANEL DE SESION.
    #
    # Uno por fila: clan, trabajo y division. Van a FILA_ICONO y no al tamano de
    # los de la rejilla: aqui acompanan a una linea de texto, no son el motivo de
    # la celda, y a 100 se comerian la fila entera.
    for fila in FILAS_INFO:
        origen = ARTE / "icons" / f"icon_fila_{fila}.png"
        if origen.exists():
            guardar(a_tamano(preparar(origen), FILA_ICONO),
                    SALIDA / f"fila_{fila}.png")
        else:
            print(f"  fila      FALTA {origen.relative_to(RAIZ)}")
    print(f"  filas     {len(FILAS_INFO)} iconos de sesion a "
          f"{FILA_ICONO}x{FILA_ICONO}")

    guardar(estrella(True), SALIDA / "estrella.png")
    guardar(estrella(False), SALIDA / "estrella_vacia.png")
    print(f"  estrellas dos de {ESTRELLA}x{ESTRELLA}, dibujadas a 4x y reducidas")

    arte_candado = ARTE / "icons" / f"icon_{CANDADO}.png"
    if arte_candado.exists():
        guardar(a_tamano(abrir_hueco(preparar(arte_candado)), lado),
                SALIDA / f"{CANDADO}.png")
        print(f"  candado   {CANDADO}.png del arte")
    else:
        guardar(candado_provisional(lado), SALIDA / f"{CANDADO}.png")
        print(f"  candado   PROVISIONAL dibujado por codigo "
              f"(falta {arte_candado.relative_to(RAIZ)})")

    # Los botones se guardan YA REDUCIDOS al tamano al que se dibujan.
    #
    # Se reduce AQUI y no en el juego para que la textura mida lo que ocupa y se
    # dibuje 1:1 (regla 2 de docs/ui/dibujado.md). Dejar que lo encoja el juego
    # significa vecino mas proximo, que tira una fila de cada dos.
    sitios = colocar_botones(chasis, cajas)
    # `mas` no esta en BOTONES --no tiene sitio propio en el chasis-- pero hacen
    # falta DOS, uno por saldo, asi que se prepara aparte y a su tamano.
    guardar(reducir(preparar(ARTE / "botones" / "mas.png"), *BARRA_MAS),
            SALIDA / "boton_mas.png")
    for nombre, (bx, by, bw, bh) in sitios.items():
        guardar(reducir(preparar(ARTE / "botones" / f"{nombre}.png"), bw, bh),
                SALIDA / f"boton_{nombre}.png")
    print(f"  botones   {len(sitios)} en tres sitios "
          f"(el arte llega a 120x96)")

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

    # La boca de Rotom, ahora que se sabe donde empieza la rejilla. Va pegada
    # al chasis, no dibujada por el juego: no cambia nunca.
    chasis = poner_boca(chasis, rej_y)
    guardar(sangrar_alfa(chasis), SALIDA / "pokepad.png")

    # La cara, centrada en la ranura de arriba.
    cx, cy, cw, chh = interior(cajas[0])
    cara_x, cara_y = cx + (cw - CARA_LADO) // 2, cy + (chh - CARA_LADO) // 2
    if CARA_LADO > cw or CARA_LADO > chh:
        raise SystemExit(f"La cara ({CARA_LADO}) no cabe en {cw}x{chh}")

    # LA FICHA DE SESION, en el hueco que queda bajo la cara.
    #
    # Sustituye a la tarjeta de las Vias y a las dos ranuras de saldo: el chasis
    # nuevo ya no las tiene. Aqui van, uno debajo de otro, el nombre del
    # jugador, sus dos saldos y el resto de datos de sesion.
    fx, fy, fw, fh = medir_ficha(chasis, cajas[0])
    ficha_x0 = fx + FICHA_AIRE
    ficha_x1 = fx + fw - FICHA_AIRE_DER
    print(f"  ficha     hueco {fw} x {fh} en {fx},{fy}"
          f"   -> util x {ficha_x0}..{ficha_x1}")

    pbx, pby, pbw, pbh = medir_panel_superior(chasis)

    print("\n  PARA PokePadScreen:")
    print(f"    REJ_X = {rej_x}, REJ_Y = {rej_y}")
    print(f"    CELDA = {celda}, HUECO_X = {HUECO}, HUECO_Y = {hueco_y}, "
          f"ICONO = {lado}")
    print(f"    CARA_X = {cara_x}, CARA_Y = {cara_y}, CARA_LADO = {CARA_LADO}")
    # El panel de sesion, fila a fila. El lado de la medalla se DEDUCE del ancho
    # util: ocho por fila con su separacion, y lo que salga. Escribirlo a mano
    # significaria que el dia que el hueco cambie las medallas se salgan del
    # panel sin que nada falle.
    ficha_y = fy + FICHA_AIRE
    ficha_y1 = fy + fh - FICHA_AIRE
    med_ancho = ficha_x1 - ficha_x0
    medalla = (med_ancho - MEDALLA_SEP * (MEDALLA_COLS - 1)) // MEDALLA_COLS
    # Centrado de verdad: lo que sobra al dividir se reparte a los dos lados.
    med_x0 = ficha_x0 + (med_ancho - (medalla * MEDALLA_COLS
                                      + MEDALLA_SEP * (MEDALLA_COLS - 1))) // 2
    alto_filas = FILA_ALTO * (2 + len(FILAS_INFO))       # los dos saldos + las tres
    medallas_y = ficha_y + alto_filas + FILA_AIRE + TEXTO_ALTO + 6
    fondo = medallas_y + MEDALLA_FILAS * (medalla + MEDALLA_SEP)
    if fondo > ficha_y1:
        raise SystemExit(
            f"El panel de sesion no cabe: necesita hasta y={fondo} y el hueco "
            f"acaba en {ficha_y1}. Baja FILA_ALTO o MEDALLA_COLS.")
    print(f"    FICHA_X0 = {ficha_x0}, FICHA_X1 = {ficha_x1}, FICHA_Y = {ficha_y}")
    print(f"    FILA_ALTO = {FILA_ALTO}, FILA_ICONO = {FILA_ICONO}")
    print(f"    MEDALLAS_X = {med_x0}, MEDALLAS_Y = {medallas_y}, "
          f"MEDALLA = {medalla}, MEDALLA_SEP = {MEDALLA_SEP}")
    print(f"    (el panel acaba en y={fondo}, el hueco en y={ficha_y1})")
    print(f"    BOTON = {{      // x, y, ancho, alto")
    for nombre in BOTONES:
        bx, by, bw, bh = sitios[nombre]
        print(f"        {{{bx:>4}, {by:>3}, {bw:>2}, {bh:>2}}},   // {nombre}")
    print(f"    }};")
    print(f"    (rejilla {rej_w}x{rej_h} en una pantalla de {x1 - x0}x{y1 - y0})")

    if args.maqueta:
        for pag in range(2):
            maqueta(Image.open(SALIDA / "pokepad.png"), iconos,
                    rej_x, rej_y, celda, lado, hueco_y,
                    (cara_x, cara_y), sitios,
                    {"x0": ficha_x0, "x1": ficha_x1, "y": ficha_y,
                     "medallas": medallas_y, "medalla": medalla,
                     "med_x": med_x0}, pag)


def colocar_botones(chasis: Image.Image, cajas: list) -> dict:
    """Donde va cada boton y de que tamano. `{nombre: (x, y, ancho, alto)}`.

    Los tres sitios se miden sobre el chasis, asi que si el arte cambia los
    botones se recolocan solos — que es lo que no pasaba antes, cuando la barra
    estaba escrita a mano y se quedaba donde estuvo en la version anterior.
    """
    pantalla = medir_pantalla(chasis)
    bx, by, bw, bh = medir_banda(chasis, pantalla)
    px, py, pw, ph = medir_panel_superior(chasis)

    sitios = {}
    ancho_b, alto_b = BOTON_BANDA
    ancho_p, alto_p = BOTON_PANEL
    ancho_c, alto_c = BOTON_CUADRO

    # LA BARRA ENTERA, pegada a la derecha de la banda. Se compone de izquierda
    # a derecha y se ancla por el final: asi `cerrar` queda en el extremo, que es
    # donde lo busca todo el mundo, y todo lo demas se acomoda solo.
    mas_w, mas_h = BARRA_MAS
    grupo = MONEDA + BARRA_AIRE + BARRA_NUM + BARRA_AIRE + mas_w
    en_panel = sorted((n for n in BOTONES if SITIOS[n][0] == "panel"),
                      key=lambda n: SITIOS[n][1])
    botones_w = len(en_panel) * ancho_p + (len(en_panel) - 1) * BOTON_SEP_X
    # En la banda de arriba solo van `ajustes` y `cerrar`: los saldos se han
    # mudado a la ficha del panel izquierdo, que es donde el chasis les hizo
    # sitio de verdad.
    if botones_w > pw - BARRA_MARGEN:
        raise SystemExit(f"Los botones no caben: {botones_w} en {pw}")
    fila_x = px + pw - BARRA_MARGEN - botones_w

    for nombre in BOTONES:
        sitio, i = SITIOS[nombre]
        if sitio == "banda":
            # Centrado en su mitad de la banda, y centrado en su alto. Se
            # permite que sobresalga: la banda mide 37 y el boton 48.
            centro = bx + bw * (2 * i + 1) // 4
            sitios[nombre] = (centro - ancho_b // 2,
                              by + (bh - alto_b) // 2, ancho_b, alto_b)
        else:
            sitios[nombre] = (fila_x + i * (ancho_p + BOTON_SEP_X),
                              py + (ph - alto_p) // 2, ancho_p, alto_p)
    return sitios


# La segunda pagina: quince celdas con un candado y "Proximamente".
#
# El icono es UNO solo repetido quince veces, no quince distintos: lo que
# comunica es "aqui no hay nada todavia", y quince candados distintos dirian que
# hay quince cosas distintas bloqueadas, que no es verdad.
CANDADO = "candado"


def candado_provisional(lado: int) -> Image.Image:
    """Un candado dibujado por codigo, para mientras no llegue el arte.

    Existe para que la segunda pagina se pueda montar, ver y desplegar HOY sin
    esperar al diseno. Es deliberadamente plano y sin sombra: tiene que cantar
    que es provisional, no colarse como definitivo.
    """
    img = Image.new("RGBA", (lado, lado))
    d = ImageDraw.Draw(img)
    u = lado / 100.0
    cuerpo = (int(24 * u), int(46 * u), int(76 * u), int(88 * u))
    gris, claro = (122, 132, 158, 255), (168, 178, 202, 255)
    # El arco, dos trazos para que tenga grosor sin usar el ancho de linea (que
    # a estos tamanos redondea distinto en cada version de Pillow).
    for r in (int(9 * u), int(10 * u), int(11 * u)):
        d.arc([50 * u - r, 34 * u - r, 50 * u + r, 34 * u + r],
              180, 360, fill=gris)
        d.line([(50 * u - r, 34 * u), (50 * u - r, 47 * u)], fill=gris)
        d.line([(50 * u + r, 34 * u), (50 * u + r, 47 * u)], fill=gris)
    d.rounded_rectangle(cuerpo, radius=int(8 * u), fill=claro, outline=gris,
                        width=max(1, int(3 * u)))
    d.ellipse([46 * u, 61 * u, 54 * u, 69 * u], fill=gris)
    d.line([(50 * u, 66 * u), (50 * u, 76 * u)], fill=gris, width=max(1, int(3 * u)))
    return img


def descargar_medallas() -> dict:
    """Las medallas del mod de CobbleVerse, SOLO para la maqueta.

    ⚠ NO se copian a nuestros assets, y no es un detalle: el mod es
    CC-BY-NC-ND. En el juego el Pad las referencia por identificador —el mod va
    instalado en el cliente, asi que sus texturas ya estan cargadas— y aqui solo
    se bajan a `build/` para poder ENSENAR como queda el panel. `build/` no se
    publica.

    Si el manifiesto no esta generado todavia, se devuelve vacio y la maqueta
    dibuja recuadros: preferible a no poder verla.
    """
    import io
    import json
    import urllib.request
    import zipfile
    cache = SALIDA.parent / "medallas"
    if cache.exists() and any(cache.iterdir()):
        return {p.stem: Image.open(p).convert("RGBA")
                for p in cache.glob("*.png")}
    manifiesto = RAIZ / "build" / "pack" / "manifest.json"
    if not manifiesto.exists():
        return {}
    try:
        entrada = next(f for f in json.loads(manifiesto.read_text(encoding="utf-8"))["files"]
                       if "CobbleverseBadges" in f["path"])
        z = zipfile.ZipFile(io.BytesIO(urllib.request.urlopen(urllib.request.Request(
            entrada["url"], headers={"User-Agent": "PokeReport/0.1"})).read()))
    except Exception as e:                       # noqa: BLE001
        print(f"  medallas  no se pudieron bajar ({e})")
        return {}
    cache.mkdir(parents=True, exist_ok=True)
    salida = {}
    for nombre in MEDALLA_ORDEN:
        ruta = f"assets/cobbleversebadges/textures/item/{nombre}_badge.png"
        if ruta not in z.namelist():
            continue
        (cache / f"{nombre}.png").write_bytes(z.read(ruta))
        salida[nombre] = Image.open(cache / f"{nombre}.png").convert("RGBA")
    print(f"  medallas  {len(salida)} bajadas a build/ (solo para la maqueta)")
    return salida


def maqueta(chasis, iconos, rx, ry, celda, lado, hueco_y,
            cara, sitios, panel, pagina=0) -> None:
    """Monta una pantalla con las celdas dibujadas como las dibuja el juego:
    rectangulos planos. Ver docs/ui/prompts-arte-pokepad.md §4.

    :param pagina: 0 las aplicaciones, 1 la de «Proximamente»
    """
    if pagina:
        # La segunda pagina es la misma rejilla con el mismo candado quince
        # veces. Se monta con los mismos numeros a proposito: si se dibujara
        # aparte, dejaria de comprobar que las dos encajan igual.
        iconos = [SALIDA / f"{CANDADO}.png"] * 15
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
        abierta = not pagina and p.stem.replace("icon_", "") in ABIERTAS
        d.rectangle([cx, cy, cx + celda - 1, cy + celda - 1],
                    fill=CELDA_ENCIMA if encima else
                         (CELDA_FONDO if abierta else CELDA_CERRADA),
                    outline=BORDE_ENCIMA if encima else
                            (CELDA_BORDE if abierta else BORDE_CERRADA),
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
        ico = Image.open(p if pagina else
                         SALIDA / f"{p.stem.replace('icon_', '')}.png")
        out.alpha_composite(ico, (cx + (celda - lado) // 2,
                                  cy + (celda - lado) // 2))
        # El nombre debajo. En el juego lo pinta la fuente de Minecraft; aqui
        # basta con una cualquiera del sistema para juzgar el ENCAJE, que es lo
        # unico que la maqueta tiene que responder.
        nombre = "Proximamente" if pagina else NOMBRES.get(
            p.stem.replace("icon_", ""), "")
        if nombre:
            try:
                fuente = ImageFont.truetype("arial.ttf", TEXTO_ALTO)
            except OSError:
                fuente = ImageFont.load_default()
            ancho = d.textlength(nombre, font=fuente)
            d.text((cx + celda / 2 - ancho / 2, cy + celda - TEXTO_SOLAPE),
                   nombre, font=fuente, fill=TEXTO_COLOR,
                   stroke_width=1, stroke_fill=TEXTO_CONTORNO)
    # Los botones, cada uno en su sitio, y los cinco sin destino apagados igual
    # que en el juego. La maqueta existe para aprobar el diseno, asi que si no
    # ensena lo mismo que se va a ver no sirve de nada.
    for n in BOTONES:
        b = Image.open(SALIDA / f"boton_{n}.png").convert("RGBA")
        if n != "cerrar":
            a = np.array(b).astype(int)
            a[..., :3] = a[..., :3] * 128 // 255      # el mismo tinte del codigo
            b = Image.fromarray(a.astype(np.uint8), "RGBA")
        out.alpha_composite(b, sitios[n][:2])

    # El hueco de la cabeza, para ver si cuadra dentro de su ranura.
    d.rectangle([cara[0], cara[1], cara[0] + CARA_LADO - 1, cara[1] + CARA_LADO - 1],
                fill=(150, 110, 90, 255), outline=(210, 170, 150, 255), width=2)

    # EL PANEL DE SESION. Mismos numeros que PokePadScreen: si la maqueta no
    # ensena lo que se va a ver, no sirve para aprobar nada.
    try:
        fuente = ImageFont.truetype("arial.ttf", TEXTO_ALTO)
        gorda = ImageFont.truetype("arial.ttf", 27)
        chica = ImageFont.truetype("arialbd.ttf", TEXTO_ALTO)
    except OSError:
        fuente = gorda = chica = ImageFont.load_default()

    x0f, x1f, y0f = panel["x0"], panel["x1"], panel["y"]

    def celda_fila(yy, x_ini=None, x_fin=None, fondo=FILA_FONDO,
                   borde=FILA_BORDE, alto=None, fuera=PANEL_FONDO):
        """La misma celda que dibuja PokePadScreen: plana, con borde y las
        cuatro esquinas mordidas del color del fondo de debajo."""
        xa = x0f if x_ini is None else x_ini
        xb = x1f if x_fin is None else x_fin
        h = FILA_CELDA if alto is None else alto
        d.rectangle([xa, yy, xb - 1, yy + h - 1], fill=fondo, outline=borde,
                    width=BORDE)
        for ex, ey in ((xa, yy), (xb - MORDIDA, yy),
                       (xa, yy + h - MORDIDA), (xb - MORDIDA, yy + h - MORDIDA)):
            d.rectangle([ex, ey, ex + MORDIDA - 1, ey + MORDIDA - 1], fill=fuera)

    y = y0f
    # Las dos monedas, cada una en su celda.
    for arte, valor, color, con_mas in (
            ("plata.png", "12,345", (226, 232, 242, 255), False),
            ("lunacoin.png", "48", (255, 211, 74, 255), True)):
        celda_fila(y)
        if (SALIDA / arte).exists():
            out.alpha_composite(Image.open(SALIDA / arte).convert("RGBA"),
                                (x0f + FILA_PAD, y + (FILA_CELDA - MONEDA) // 2))
        d.text((x0f + FILA_PAD + MONEDA + 12, y + FILA_CELDA // 2), valor,
               font=gorda, fill=color, anchor="lm")
        if con_mas:
            # El "+": su propia celda pequena y dos barras cruzadas. Apagado,
            # porque todavia no hay tienda a la que llevar.
            mx = x1f - FILA_PAD - MAS_LADO
            my = y + (FILA_CELDA - MAS_LADO) // 2
            celda_fila(my, mx, mx + MAS_LADO, alto=MAS_LADO, fuera=FILA_FONDO,
                       borde=(150, 161, 192, 255))
            cx, cy = mx + MAS_LADO // 2, my + MAS_LADO // 2
            d.rectangle([cx - MAS_BRAZO // 2, cy - MAS_GROSOR // 2,
                         cx + MAS_BRAZO // 2, cy + MAS_GROSOR // 2],
                        fill=MAS_APAGADO)
            d.rectangle([cx - MAS_GROSOR // 2, cy - MAS_BRAZO // 2,
                         cx + MAS_GROSOR // 2, cy + MAS_BRAZO // 2],
                        fill=MAS_APAGADO)
        y += FILA_ALTO

    # Clan, trabajo y division. Guion en los tres: hoy no hay sistema detras y
    # la maqueta tiene que ensenar el estado REAL, no uno inventado.
    for fila in FILAS_INFO:
        celda_fila(y)
        icono = SALIDA / f"fila_{fila}.png"
        if icono.exists():
            out.alpha_composite(Image.open(icono).convert("RGBA"),
                                (x0f + FILA_PAD, y + (FILA_CELDA - FILA_ICONO) // 2))
        d.text((x0f + FILA_PAD + FILA_ICONO + 12, y + FILA_CELDA // 2),
               ETIQUETAS[fila], font=fuente, fill=(159, 176, 212, 255),
               anchor="lm")
        d.text((x1f - FILA_PAD, y + FILA_CELDA // 2), "-", font=fuente,
               fill=(255, 255, 255, 255), anchor="rm")
        y += FILA_ALTO

    # LAS MEDALLAS. Las dieciseis, apagadas las que no se tienen.
    d.text((x0f, panel["medallas"] - TEXTO_ALTO - 6), "MEDALLAS", font=chica,
           fill=(159, 176, 212, 255))
    medallas = descargar_medallas()
    paso = panel["medalla"] + MEDALLA_SEP
    for i, nombre in enumerate(MEDALLA_ORDEN):
        mx = panel["med_x"] + (i % MEDALLA_COLS) * paso
        my = panel["medallas"] + (i // MEDALLA_COLS) * paso
        im = medallas.get(nombre)
        if im is None:
            d.rectangle([mx, my, mx + panel["medalla"] - 1,
                         my + panel["medalla"] - 1],
                        outline=(90, 100, 130, 255), width=2)
            continue
        im = im.resize((panel["medalla"], panel["medalla"]), Image.NEAREST)
        if i >= MEDALLAS_MAQUETA:
            # El mismo tinte que el codigo: MULTIPLICA, no repinta.
            a = np.array(im).astype(int)
            for c, v in enumerate((0x3C, 0x42, 0x58)):
                a[..., c] = a[..., c] * v // 255
            im = Image.fromarray(a.astype(np.uint8), "RGBA")
        out.alpha_composite(im, (mx, my))

    # Sin ampliar. El chasis ya mide 1380x828, que es el tamano al que se va a
    # dibujar en pantalla: ampliarlo ensenaria algo que nadie va a ver. Antes se
    # multiplicaba por 3 porque el original media 346 y no se apreciaba nada.
    f = Image.new("RGBA", out.size, (14, 14, 20, 255))
    f.alpha_composite(out)
    MAQUETA.mkdir(parents=True, exist_ok=True)
    salida = MAQUETA / ("maqueta.png" if not pagina
                        else f"maqueta-pagina{pagina + 1}.png")
    f.convert("RGB").save(salida)
    print(f"  -> {salida.relative_to(RAIZ)}")


if __name__ == "__main__":
    main()
