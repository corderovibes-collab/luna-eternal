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
  derecha     cuatro pestañas de categoria y una rejilla 5x2 de celdas, cada una
              con su cosmetico y su precio

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

# La rejilla. 5x2 = 10 por pagina: con celdas mas grandes caben 8 y la pantalla
# se ve vacia; con 6 columnas el 3D de dentro no se distingue.
COLS, FILAS = 5, 2
AIRE = 14          # entre celdas
MARGEN = 12        # de la rejilla al bisel de la pantalla
PESTANA_ALTO = 56

# El pie de cada celda: donde va el precio. El resto es el 3D.
PIE = 46

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

    # ----------------------------------------------------- panel izquierdo
    prev_y1 = py1 - SALDO_ALTO
    d.rectangle([px0 + 8, py0 + 8, px1 - 8, prev_y1 - 8],
                outline=(90, 100, 130, 255), width=3)
    texto(d, ((px0 + px1) // 2, (py0 + prev_y1) // 2 - 20),
          "PREVISUALIZADOR 3D", fuente(26),
          color=(150, 160, 190, 255), contorno=(20, 22, 28, 255))
    texto(d, ((px0 + px1) // 2, (py0 + prev_y1) // 2 + 20),
          "%d x %d" % (px1 - px0 - 16, prev_y1 - py0 - 16), fuente(22),
          color=(110, 120, 150, 255), contorno=(20, 22, 28, 255))

    # LunaCoins + el "+", abajo. La moneda es el arte real.
    moneda = Image.open(ARTE / "lunacoin.png").convert("RGBA").resize(
        (MONEDA, MONEDA), Image.LANCZOS)
    cy = (prev_y1 + py1) // 2
    im.alpha_composite(moneda, (px0 + 24, cy - MONEDA // 2))
    texto(d, (px0 + 24 + MONEDA + 14, cy), "12.500", fuente(34),
          color=(255, 214, 92, 255), contorno=(20, 22, 28, 255), anclaje="lm")

    mas = px1 - 24 - 52
    d.rounded_rectangle([mas, cy - 26, mas + 52, cy + 26], radius=10,
                        fill=BORDE_ENCIMA, outline=(255, 214, 92, 255), width=3)
    texto(d, (mas + 26, cy), "+", fuente(38),
          color=(255, 255, 255, 255), contorno=(120, 40, 0, 255))

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

        if equipada:
            texto(d, (cx + cw // 2, cyy + ch - PIE // 2), "EQUIPADO", fuente(18),
                  color=(20, 110, 60, 255))
        else:
            im.alpha_composite(moneda.resize((26, 26), Image.LANCZOS),
                               (cx + 14, cyy + ch - PIE // 2 - 13))
            texto(d, (cx + 46, cyy + ch - PIE // 2), "%d" % precio, fuente(21),
                  anclaje="lm")

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
