"""Maqueta de la SUB-PANTALLA DE COSMETICOS del PokePad.

Genera `build/pokepad/maqueta_cosmeticos.png` para aprobar la disposicion ANTES
de escribir el Screen de Java, que es como se aprobo la pantalla principal.

    python tools/gen_cosmeticos.py

⚠ LAS MEDIDAS SE MIDEN, NO SE ESCRIBEN.

  El chasis ha cambiado de estructura cuatro veces y cada vez las medidas a mano
  se quedaron mintiendo EN SILENCIO: el codigo dibujaba donde estaba en la
  version anterior sin que nada fallara. Aqui se localizan a la fuerza:

    la PANTALLA de Rotom   la mancha clara y grande de la derecha
    el PANEL izquierdo     el rectangulo oscuro (33,36,41) del tercio izquierdo,
                           que se distingue del chasis (52,54,62) por luminancia

  Si el arte cambia, estos numeros cambian solos.

QUE ENSEÑA LA MAQUETA

  izquierda   arriba el previsualizador 3D del personaje, abajo el saldo de
              LunaCoins con su "+"
  derecha     cuatro pestañas de categoria y una rejilla 4x2 de celdas, cada una
              con su cosmetico, su precio y su boton de compra

  Se dibuja UNA celda con el raton encima y UNA ya equipada, que son los dos
  estados que no se pueden juzgar de otra forma.

⚠ EL 3D NO SE PUEDE MAQUETAR AQUI. Un modelo de Cobblemon lo dibuja el juego con
  su propio renderizador; Pillow no puede. Los huecos van marcados con su caja y
  su nombre para que se apruebe LA DISPOSICION, que es lo que esta maqueta
  decide. Lo que se vea dentro se juzga ya en el juego.
"""

import sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).parent))
import numpy as np

RAIZ = Path(__file__).resolve().parent.parent
ARTE = RAIZ / "arte" / "pokepad"
SALIDA = RAIZ / "build" / "pokepad"

# Los botones YA EXISTEN y ya se usan en la pantalla principal. Se cogen de ahi
# en vez de dibujar unos nuevos: dos juegos de botones parecidos pero distintos
# es la forma mas facil de que una pantalla se sienta de otra aplicacion.
BOTONES = (RAIZ / "mod" / "src" / "client" / "resources" / "assets"
           / "lunaeternal" / "textures" / "gui" / "pokepad")

# La franja de navegacion, ARRIBA del panel izquierdo.
#
# ⚠ ESTE FONDO NO TRAE RANURA PARA BOTONES, al contrario que el chasis
#   principal: la izquierda la ocupan el preview y las LunaCoins, y la derecha es
#   pantalla de punta a punta. Asi que la franja se abre quitandole alto al
#   preview -- 72 px de 529, que sigue dejando un retrato holgado.
#
#   Arriba y no abajo porque abajo esta el saldo, y meter "cerrar" al lado del
#   "+" de comprar es pedir un clic equivocado sobre dinero real.
NAV_ALTO = 72

# La paleta de la pantalla principal, importada y no copiada: si alli cambia un
# tono, esta maqueta tiene que cambiar con el o deja de servir para aprobar nada.
from gen_pokepad import (  # noqa: E402
    BORDE_ENCIMA,
    CELDA_BORDE,
    CELDA_ENCIMA,
    CELDA_FONDO,
    MONEDA,
    TEXTO_COLOR,
    TEXTO_CONTORNO,
    medir_pantalla,
)

# Las cuatro categorias decididas por el usuario.
CATEGORIAS = ["MASCOTAS", "CAPAS", "SOMBREROS", "AURAS"]

# La rejilla. 4x2 = 8 por pagina.
#
# ⚠ SE PROBO CON 5 Y NO VALIA. Con 5 columnas la celda mide 144 y el boton de
#   compra obliga a apilar precio y boton, que cuesta 72 px de alto: el 3D se
#   quedaba en 124x90 y un Charizard ahi no se distingue. A 4 columnas el pie
#   cabe en UNA fila de 38 y el 3D sube a 163x147.
COLS, FILAS = 4, 2
AIRE = 14          # entre celdas
MARGEN = 12        # de la rejilla al bisel de la pantalla
PESTANA_ALTO = 56

# El pie de cada celda: precio y boton EN LA MISMA FILA.
#
# ⚠ ESTO SOLO CABE CON 4 COLUMNAS, Y ESE ES EL MOTIVO DE PASAR A 4.
#
#   Con 5 columnas la celda medía 144 de ancho: el precio con su moneda se
#   llevaba 80 y quedaban 60 para el boton, donde "COMPRAR" no entra ni a cuerpo
#   14. Habia que apilarlos, y apilarlos costaba 72 px de alto -- que salian del
#   3D, dejandolo en 124x90. Un Charizard ahi se ve diminuto.
#
#   A 4 columnas la celda pasa a 183 y los dos caben en una fila de 38. El 3D
#   recupera 34 px de alto. O sea que pasar a 4 columnas no gana solo anchura:
#   gana la ALTURA que faltaba, que era el problema de verdad.
PIE = 38

# Los tres estados de una celda. Son los que hacen falta para que la pantalla se
# entienda sin explicarla: si no distingues "comprado" de "puesto", la gente
# vuelve a comprar lo que ya tiene.
COMPRAR, EQUIPAR, EQUIPADO = range(3)

SALDO_ALTO = 147   # la franja de LunaCoins, abajo del panel izquierdo

# Muestra para la maqueta. Los nombres salen de CobblemonMoreCosmetics (MIT);
# los precios son PROVISIONALES -- ver el aviso al final de este fichero.
MUESTRA = [
    ("Charizard", "knight", 2500),
    ("Eevee", "valentines", 1200),
    ("Snorlax", "chef", 1800),
    ("Mewtwo", "boundary", 4000),
    ("Articuno", "steampunk", 3500),
    ("Gardevoir", "icedragon", 3000),
    ("Decidueye", "ninja", 2200),
    ("Cinderace", "captain", 2000),
    ("Weavile", "skier", 1500),
    ("Carbink", "royal", 2800),
]

ENCIMA = 3      # indice de la celda con el raton encima
EQUIPADA = 0    # indice de la celda ya equipada


def medir_panel(chasis: Image.Image) -> tuple:
    """El rectangulo oscuro de la izquierda, localizado por color.

    ⚠ NO vale con "lo oscuro del tercio izquierdo": el chasis TAMBIEN es oscuro
      (52,54,62) y se lo traga entero, dando 745 de alto en vez de 692. El panel
      es (33,36,41), unos 18 puntos de luminancia por debajo, y ese hueco es lo
      que los separa.

    Se recorre desde un punto seguro de dentro hacia fuera en vez de usar
    minimo/maximo, porque cualquier pixel suelto del mismo tono en otra parte
    del chasis estiraria la caja sin que nada fallara.
    """
    a = np.array(chasis.convert("RGBA")).astype(int)
    lum = 0.2126 * a[..., 0] + 0.7152 * a[..., 1] + 0.0722 * a[..., 2]
    es = (a[..., 3] > 200) & (lum < 45)

    def racha(vec, desde):
        i = j = desde
        while i > 0 and vec[i - 1]:
            i -= 1
        while j < len(vec) - 1 and vec[j + 1]:
            j += 1
        return i, j + 1

    cy, cx = a.shape[0] // 2, a.shape[1] // 6
    if not es[cy, cx]:
        raise SystemExit(
            "medir_panel: (%d,%d) no cae dentro del panel oscuro. El arte ha "
            "cambiado de estructura: revisa el fondo antes de seguir." % (cx, cy)
        )
    y0, y1 = racha(es[:, cx], cy)
    x0, x1 = racha(es[cy, :], cx)

    relleno = es[y0:y1, x0:x1].mean()
    if relleno < 0.95:
        raise SystemExit(
            "medir_panel: lo encontrado no es un rectangulo limpio (%.0f%% de "
            "relleno). No se maqueta sobre una medida en la que no se confia."
            % (100 * relleno)
        )
    return x0, y0, x1, y1


def moneda_oro(origen: Path, destino: Path) -> Image.Image:
    """La LunaCoin, en oro.

    ⚠ EL ARTE DE `lunacoin.png` ES AZUL PLATEADO, Y ESO CONTRADICE D-034.
      Esa decision dice, literal, que "el dorado es ahora de las LunaCoins" y
      que la Plata pasa a blanco justamente para no competir con el. Con las dos
      monedas en tonos frios --la Plata es gris y la LunaCoin azul-- se
      distinguen mal a 26 px, que es el tamaño al que aparecen en las celdas.

    NO se recolorea rotando el tono. Un giro de azul a dorado desaturado deja un
    marron sucio, porque el azul del arte tiene poca saturacion y el giro
    conserva esa pobreza. Se hace un MAPA DE GRADIENTE: se lee la luminancia de
    cada pixel y se busca su color en una rampa de oro. Asi se conservan el
    relieve, el brillo y la luna, y el resultado es oro de verdad y no azul
    tintado.

    El alfa no se toca: la silueta y el dentado del engranaje siguen siendo los
    del original, pixel a pixel.
    """
    im = Image.open(origen).convert("RGBA")
    a = np.array(im).astype(float)
    lum = (0.2126 * a[..., 0] + 0.7152 * a[..., 1] + 0.0722 * a[..., 2]) / 255.0

    # La rampa: de la sombra al brillo. Los extremos importan mas que el centro
    # -- sin un tope casi blanco la moneda pierde el destello y parece plana.
    paradas = [
        (0.00, (61, 38, 4)),
        (0.35, (146, 96, 15)),
        (0.60, (214, 158, 41)),
        (0.80, (247, 205, 92)),
        (1.00, (255, 243, 199)),
    ]
    xs = np.array([p for p, _ in paradas])
    out = np.zeros_like(a)
    for c in range(3):
        ys = np.array([col[c] for _, col in paradas], dtype=float)
        out[..., c] = np.interp(lum, xs, ys)
    out[..., 3] = a[..., 3]

    oro = Image.fromarray(out.astype("uint8"), "RGBA")
    destino.parent.mkdir(parents=True, exist_ok=True)
    oro.save(destino)
    return oro


def texto(d: ImageDraw.ImageDraw, xy, s, fuente, color=TEXTO_COLOR,
          contorno=TEXTO_CONTORNO, anclaje="mm"):
    """Texto con contorno, que es lo unico que se lee sobre el fondo casi blanco.

    El color y el color del contorno son DOS constantes y no negro escrito a
    mano: en el chasis v4 la pantalla se invirtio y los dos cambiaron de golpe.
    Un negro dentro de la funcion se habria quedado.
    """
    d.text(xy, s, font=fuente, fill=color, anchor=anclaje,
           stroke_width=3, stroke_fill=contorno)


def main() -> None:
    fondo = ARTE / "fondo_cosmeticos.png"
    if not fondo.exists():
        raise SystemExit("Falta %s" % fondo)

    chasis = Image.open(fondo).convert("RGBA")
    px0, py0, px1, py1 = medir_panel(chasis)
    sx0, sy0, sx1, sy1 = medir_pantalla(chasis)

    print("MEDIDO sobre el arte, no escrito:")
    print("  chasis   %dx%d" % chasis.size)
    print("  panel    x %d..%d (%d)   y %d..%d (%d)"
          % (px0, px1, px1 - px0, py0, py1, py1 - py0))
    print("  pantalla x %d..%d (%d)   y %d..%d (%d)"
          % (sx0, sx1, sx1 - sx0, sy0, sy1, sy1 - sy0))

    im = chasis.copy()
    d = ImageDraw.Draw(im)

    from PIL import ImageFont
    def fuente(n):
        for f in ("segoeuib.ttf", "arialbd.ttf", "DejaVuSans-Bold.ttf"):
            try:
                return ImageFont.truetype(f, n)
            except OSError:
                continue
        return ImageFont.load_default()

    # ------------------------------------------- navegacion, arriba del panel
    # Los botones son los MISMOS de la pantalla principal, no unos nuevos.
    atras = Image.open(BOTONES / "boton_atras.png").convert("RGBA")
    cerrar = Image.open(BOTONES / "boton_cerrar.png").convert("RGBA")
    nav_cy = py0 + NAV_ALTO // 2
    im.alpha_composite(atras, (px0 + 18, nav_cy - atras.height // 2))
    texto(d, (px0 + 18 + atras.width + 10, nav_cy), "INICIO", fuente(20),
          color=(210, 216, 232, 255), contorno=(20, 22, 28, 255), anclaje="lm")
    im.alpha_composite(cerrar, (px1 - 18 - cerrar.width, nav_cy - cerrar.height // 2))

    # ----------------------------------------------------- previsualizador
    prev_y0 = py0 + NAV_ALTO
    prev_y1 = py1 - SALDO_ALTO
    d.rectangle([px0 + 8, prev_y0, px1 - 8, prev_y1 - 8],
                outline=(90, 100, 130, 255), width=3)
    texto(d, ((px0 + px1) // 2, (prev_y0 + prev_y1) // 2 - 20),
          "PREVISUALIZADOR 3D", fuente(26),
          color=(150, 160, 190, 255), contorno=(20, 22, 28, 255))
    texto(d, ((px0 + px1) // 2, (prev_y0 + prev_y1) // 2 + 20),
          "%d x %d" % (px1 - px0 - 16, prev_y1 - prev_y0 - 8), fuente(22),
          color=(110, 120, 150, 255), contorno=(20, 22, 28, 255))

    # ------------------------------------------------ LunaCoins, abajo
    oro = moneda_oro(ARTE / "lunacoin.png", ARTE / "lunacoin_oro.png")
    moneda = oro.resize((MONEDA, MONEDA), Image.LANCZOS)
    cy = (prev_y1 + py1) // 2
    im.alpha_composite(moneda, (px0 + 24, cy - MONEDA // 2))
    texto(d, (px0 + 24 + MONEDA + 14, cy), "12.500", fuente(34),
          color=(255, 214, 92, 255), contorno=(20, 22, 28, 255), anclaje="lm")

    # El "+" es el arte que YA EXISTE. Antes se dibujaba a mano aqui, teniendo
    # `boton_mas.png` al lado: dos "+" distintos en la misma aplicacion.
    mas = Image.open(BOTONES / "boton_mas.png").convert("RGBA")
    im.alpha_composite(mas, (px1 - 24 - mas.width, cy - mas.height // 2))

    # ------------------------------------------------------ pestañas
    ancho = sx1 - sx0 - 2 * MARGEN
    pw = ancho // len(CATEGORIAS)
    for i, cat in enumerate(CATEGORIAS):
        x = sx0 + MARGEN + i * pw
        activa = i == 0
        d.rounded_rectangle([x + 3, sy0 + MARGEN, x + pw - 3,
                             sy0 + MARGEN + PESTANA_ALTO], radius=9,
                            fill=CELDA_ENCIMA if activa else CELDA_FONDO,
                            outline=BORDE_ENCIMA if activa else CELDA_BORDE,
                            width=4 if activa else 2)
        texto(d, (x + pw // 2, sy0 + MARGEN + PESTANA_ALTO // 2), cat, fuente(24))

    # ------------------------------------------------------ rejilla
    gy0 = sy0 + MARGEN + PESTANA_ALTO + AIRE
    alto = sy1 - gy0 - MARGEN
    cw = (ancho - (COLS - 1) * AIRE) // COLS
    ch = (alto - (FILAS - 1) * AIRE) // FILAS
    print("  pestañas %d x %d" % (pw, PESTANA_ALTO))
    print("  celda    %d x %d   (%d x %d = %d por pagina)"
          % (cw, ch, COLS, FILAS, COLS * FILAS))

    for i, (esp, var, precio) in enumerate(MUESTRA[:COLS * FILAS]):
        cx = sx0 + MARGEN + (i % COLS) * (cw + AIRE)
        cyy = gy0 + (i // COLS) * (ch + AIRE)
        encima, equipada = i == ENCIMA, i == EQUIPADA

        d.rounded_rectangle([cx, cyy, cx + cw, cyy + ch], radius=10,
                            fill=CELDA_ENCIMA if encima else CELDA_FONDO,
                            outline=BORDE_ENCIMA if (encima or equipada) else CELDA_BORDE,
                            width=4 if (encima or equipada) else 2)

        # El hueco del 3D: se marca, no se dibuja. Ver la cabecera.
        d.rectangle([cx + 10, cyy + 8, cx + cw - 10, cyy + ch - PIE],
                    outline=(150, 162, 196, 255), width=2)
        texto(d, (cx + cw // 2, cyy + (ch - PIE) // 2 - 8), esp, fuente(19))
        texto(d, (cx + cw // 2, cyy + (ch - PIE) // 2 + 14), var, fuente(16),
              color=(90, 102, 140, 255))

        # ---- pie: precio a la izquierda, boton a la derecha, misma fila
        estado = EQUIPADO if equipada else (EQUIPAR if i == 5 else COMPRAR)
        pie_cy = cyy + ch - PIE // 2 - 4
        bw = 84                                   # ancho del boton
        bx = cx + cw - 10 - bw

        # ⚠⚠ EL PRECIO SE MIDE ANTES DE DIBUJARLO, Y SI NO CABE SE PARA.
        #
        # Con el boton a 96 de ancho, "1200" se salia por debajo y el boton se
        # dibujaba encima: la maqueta enseñaba "120". En una pantalla de compra
        # eso no es un fallo de maquetacion, es un precio DIEZ VECES MENOR del
        # que se va a cobrar. Y no daba ningun error: quedaba bonito y mentia.
        #
        # Asi que no se confia en que quepa: se mide con la fuente real y se
        # aborta si el texto invade el boton. Mejor no generar la maqueta que
        # generar una que miente sobre dinero.
        f_precio = fuente(19)
        if estado == COMPRAR:
            im.alpha_composite(moneda.resize((22, 22), Image.LANCZOS),
                               (cx + 12, pie_cy - 11))
            tx = cx + 38
            ancho_txt = d.textlength("%d" % precio, font=f_precio)
            if tx + ancho_txt + 6 > bx:
                raise SystemExit(
                    "celda %d: el precio %d no cabe junto al boton (necesita "
                    "hasta x=%d y el boton empieza en %d). Sube COLS a menos "
                    "columnas o baja el cuerpo de la fuente."
                    % (i, precio, int(tx + ancho_txt + 6), bx)
                )
            texto(d, (tx, pie_cy), "%d" % precio, f_precio, anclaje="lm")
        else:
            # Ya es tuyo: enseñar el precio otra vez invita a pagarlo dos veces.
            texto(d, (cx + 12, pie_cy), "TUYO", fuente(16),
                  color=(90, 102, 140, 255), anclaje="lm")

        etiqueta, relleno, borde, tinta = {
            COMPRAR: ("COMPRAR", BORDE_ENCIMA, (255, 214, 92, 255), (255, 255, 255, 255)),
            EQUIPAR: ("EQUIPAR", (86, 122, 200, 255), (150, 180, 240, 255), (255, 255, 255, 255)),
            EQUIPADO: ("EQUIPADO", (206, 220, 244, 255), (120, 160, 110, 255), (24, 92, 52, 255)),
        }[estado]
        d.rounded_rectangle([bx, pie_cy - 16, bx + bw, pie_cy + 16],
                            radius=8, fill=relleno, outline=borde, width=3)
        texto(d, (bx + bw // 2, pie_cy), etiqueta, fuente(15),
              color=tinta, contorno=(30, 40, 70, 160) if estado != EQUIPADO
              else TEXTO_CONTORNO)

    SALIDA.mkdir(parents=True, exist_ok=True)
    destino = SALIDA / "maqueta_cosmeticos.png"
    im.save(destino)
    print("\nmaqueta -> %s" % destino)
    # Sin simbolos fuera de ASCII: la consola de Windows es cp1252 y un ⚠ en un
    # print revienta el script ENTERO con UnicodeEncodeError -- despues de haber
    # guardado la maqueta, asi que parece que fallo cuando en realidad ya estaba
    # hecha. Dentro de la imagen si valen, que eso lo dibuja Pillow.
    print("\nAVISO: LOS PRECIOS SON DE RELLENO. CLAUDE.md dice que la economia "
          "se calibra\n  con datos reales, y que hasta que alguien juegue todo "
          "son estimaciones.\n  Estos numeros sirven para ver si CABEN, no para "
          "cobrarlos.")


if __name__ == "__main__":
    main()
