"""Dibuja la maqueta del MERCADO y del GTS como PNG, para poder MIRARLA.

    python tools/gen_maqueta_mercado.py

POR QUE EXISTE ESTO
-------------------
Peticion del usuario (2026-08-25): «revisa bien la posicion de los textos, si
algo haz tests visuales tu con Playwright o con otra herramienta».

⚠⚠ PLAYWRIGHT NO SIRVE AQUI, y conviene decir por que en vez de intentarlo:
   Playwright maneja paginas web en un navegador. Esto lo dibuja OpenGL dentro
   de Minecraft, con el cliente conectado a un servidor y con el jugador
   dentro. No hay DOM que inspeccionar ni pagina que cargar.

Lo que si sirve --y este proyecto ya lo hacia con `gen_pokepad.py --maqueta` y
`gen_cosmeticos.py`-- es DIBUJAR LA MISMA MAQUETA CON PIL. Se usan las mismas
coordenadas que el codigo Java, se saca un PNG, y ahi se ven de un vistazo las
tres cosas que las capturas del usuario han ido enseñando una a una:

  · texto que se sale del marco
  · cajas que se solapan
  · huecos enormes vacios al lado de zonas apretadas

⚠ ESTO NO SUSTITUYE A MIRARLO EN EL JUEGO. La maqueta y el Java pueden
  separarse, y el dia que se separen la maqueta MENTIRA en silencio -- que es
  el fallo que este proyecto ya ha pagado varias veces con las medidas escritas
  a mano. Por eso las coordenadas se declaran UNA VEZ arriba y se leen tambien
  desde el Java con --verificar.

  Lo que la maqueta SI caza, y el juego tarda una ronda de capturas en enseñar,
  es la geometria: solapes y desbordes.
"""

import argparse
import json
import re
import sys
from pathlib import Path

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    sys.exit("Falta Pillow:  .toolchain/python/python.exe -m pip install pillow")

RAIZ = Path(__file__).resolve().parent.parent
SALIDA = RAIZ / "build" / "pokepad"
FUENTE = RAIZ / "arte" / "pokepad"

# ---------------------------------------------------------------- el chasis
NAT_ANCHO, NAT_ALTO = 1380, 828
PANEL_X, PANEL_Y, PANEL_W, PANEL_H = 63, 70, 315, 692
PANT_X, PANT_Y, PANT_W, PANT_H = 460, 204, 801, 494
NAV_ALTO = 72

FONDO = (18, 23, 34, 255)
PANEL_BG = (26, 32, 46, 255)
PANT_BG = (222, 230, 245, 255)
FILA_BG = (191, 203, 232, 255)
FILA_BORDE = (124, 137, 180, 255)
NARANJA = (243, 92, 12, 255)
ORO = (255, 214, 92, 255)
SUAVE = (90, 102, 140, 255)
OSCURO = (22, 32, 58, 255)
BLANCO = (255, 255, 255, 255)
AZUL = (79, 111, 176, 255)
VERDE = (46, 158, 86, 255)

# ⚠ El aviso que hace util la maqueta: se marca en ROJO todo lo que se salga
#   del marco o se solape con otra caja. Una maqueta que solo dibuja no dice
#   nada; una que AVISA es una prueba.
ALERTA = (255, 60, 60, 255)


# ---------------------------------------------------------- LA FUENTE
#
# ⚠⚠⚠ ESTO ES LO QUE HACE QUE LA MAQUETA PREDIGA EN VEZ DE APROXIMAR.
#
#     Minecraft usa una fuente de ANCHO VARIABLE, y muy variable: una `i` mide
#     2 y una `m` mide 6. Medir con Courier --que es de ancho fijo-- da un
#     ancho equivocado en CADA cadena, y entonces la maqueta dice que algo cabe
#     cuando en el juego se sale, o al reves.
#
#     Estas son las anchuras reales de `default.png`, en unidades de fuente
#     sobre una altura de 9. El codigo Java calcula igual:
#         ancho_en_arte = getWidth(texto) * alto_pedido / fontHeight
ANCHOS = {
    " ": 4, "!": 2, '"': 5, "'": 3, "(": 5, ")": 5, "*": 5, ",": 2,
    ".": 2, ":": 2, ";": 2, "<": 5, ">": 5, "[": 4, "]": 4, "`": 3,
    "f": 5, "i": 2, "k": 5, "l": 3, "t": 4, "{": 5, "}": 5, "|": 2,
    "I": 4, "@": 7, "~": 7, "\u2014": 6, "\u00d7": 6, "\u2726": 6,
}
FONT_HEIGHT = 9
ANCHO_POR_DEFECTO = 6


def ancho_mc(texto, alto):
    """El ancho que tendria ese texto EN EL JUEGO, en unidades de arte."""
    total = sum(ANCHOS.get(c, ANCHO_POR_DEFECTO) for c in texto)
    return total * alto / FONT_HEIGHT


def fuente(px):
    """Solo para DIBUJAR. Las medidas salen de `ancho_mc`, no de aqui."""
    for nombre in ("consola.ttf", "cour.ttf", "arial.ttf"):
        try:
            return ImageFont.truetype(nombre, max(8, int(px * 0.9)))
        except OSError:
            continue
    return ImageFont.load_default()


# --------------------------------------------------------------- LOS TEXTOS
#
# ⚠⚠ SE LEEN DEL FICHERO DE IDIOMA DE VERDAD, no se escriben aqui.
#
#    Si la maqueta llevara los textos a mano, mediria unos y el juego pintaria
#    otros: en cuanto alguien cambia una traduccion, la maqueta dice «cabe» sobre
#    una cadena que ya no existe. Paso al cambiar «CHOLLOS» por «OFERTAS» y al
#    ponerle las tildes a todo -- son cadenas MAS LARGAS.
LANG = {}
_ruta = RAIZ / "mod/src/client/resources/assets/lunaeternal/lang/es_es.json"
if _ruta.exists():
    LANG = json.loads(_ruta.read_text(encoding="utf-8"))


def T(clave, *args):
    """El texto real de esa clave. Si no existe, se ve -- no se inventa."""
    s = LANG.get("pokepad.lunaeternal." + clave)
    if s is None:
        return "??" + clave + "??"
    return s % args if args else s


class Lienzo:
    """Dibuja y VIGILA: apunta cada caja y avisa de solapes y desbordes."""

    def __init__(self):
        # ⚠ EL CHASIS DE VERDAD, no uno dibujado. Es lo que el usuario pidio:
        #   sobre ESE fondo tiene que quedar todo bien, asi que sobre ESE fondo
        #   hay que comprobarlo. Un chasis inventado deja huecos donde el de
        #   verdad tiene molduras.
        base = FUENTE / "fondo_cosmeticos.png"
        if base.exists():
            self.im = Image.open(base).convert("RGBA")
            if self.im.size != (NAT_ANCHO, NAT_ALTO):
                self.im = self.im.resize((NAT_ANCHO, NAT_ALTO), Image.LANCZOS)
            self.conFondo = True
        else:
            self.im = Image.new("RGBA", (NAT_ANCHO, NAT_ALTO), FONDO)
            self.conFondo = False
        self.d = ImageDraw.Draw(self.im)
        self.cajas = []      # (nombre, x0, y0, x1, y1)
        self.avisos = []

    def caja(self, nombre, x, y, w, h, relleno=None, borde=None, grosor=2,
             vigilar=True):
        if relleno:
            self.d.rectangle([x, y, x + w, y + h], fill=relleno)
        if borde:
            self.d.rectangle([x, y, x + w, y + h], outline=borde, width=grosor)
        if vigilar:
            self._vigilar(nombre, x, y, x + w, y + h)

    def texto(self, nombre, s, x, y, px=16, color=BLANCO, ali="izq",
              limite=None, vigilar=True):
        f = fuente(px)
        # ⚠ EL ANCHO SALE DE LAS MEDIDAS DE MINECRAFT, no de la tipografia con
        #   la que se dibuja el PNG. Dibujar es para mirarlo; MEDIR es la
        #   prueba, y tiene que dar lo mismo que dara el juego.
        ancho = ancho_mc(s, px)
        if ali == "der":
            x = x - ancho
        elif ali == "centro":
            x = x - ancho / 2
        self.d.text((x, y), s, font=f, fill=color)
        if vigilar:
            self._vigilar(nombre, x, y, x + ancho, y + px)
        # ⚠ El limite es lo que caza «este texto no cabe en su columna», que es
        #   distinto de «se sale de la pantalla»: cabe en el marco pero pisa al
        #   de al lado.
        if limite is not None and ancho > limite:
            self.avisos.append(
                f"NO CABE  {nombre}: «{s}» mide {ancho:.0f} y tiene {limite}")
        return ancho

    def _vigilar(self, nombre, x0, y0, x1, y1):
        # ¿Se sale del chasis?
        if x0 < 0 or y0 < 0 or x1 > NAT_ANCHO or y1 > NAT_ALTO:
            self.avisos.append(f"FUERA DEL CHASIS  {nombre}: "
                               f"({x0:.0f},{y0:.0f})-({x1:.0f},{y1:.0f})")
            self.d.rectangle([x0, y0, x1, y1], outline=ALERTA, width=3)
        # ¿Se sale de su contenedor?
        if x0 >= PANT_X - 40:
            if x1 > PANT_X + PANT_W or y1 > PANT_Y + PANT_H:
                self.avisos.append(f"FUERA DE LA PANTALLA  {nombre}: "
                                   f"llega a ({x1:.0f},{y1:.0f}), "
                                   f"el marco acaba en "
                                   f"({PANT_X + PANT_W},{PANT_Y + PANT_H})")
                self.d.rectangle([x0, y0, x1, y1], outline=ALERTA, width=3)
        elif x1 > PANEL_X + PANEL_W or y1 > PANEL_Y + PANEL_H:
            self.avisos.append(f"FUERA DEL PANEL  {nombre}: "
                               f"llega a ({x1:.0f},{y1:.0f}), "
                               f"el panel acaba en "
                               f"({PANEL_X + PANEL_W},{PANEL_Y + PANEL_H})")
            self.d.rectangle([x0, y0, x1, y1], outline=ALERTA, width=3)
        self.cajas.append((nombre, x0, y0, x1, y1))

    def solapes(self):
        """Los pares de cajas que se pisan. ⚠ Solo entre HERMANAS."""
        malos = []
        for i in range(len(self.cajas)):
            n1, a0, b0, a1, b1 = self.cajas[i]
            for j in range(i + 1, len(self.cajas)):
                n2, c0, d0, c1, d1 = self.cajas[j]
                # Una caja DENTRO de otra no es un solape: es un contenedor.
                # Lo que importa es que dos cosas del mismo nivel se crucen.
                if n1.split(".")[0] == n2.split(".")[0]:
                    continue
                sx = min(a1, c1) - max(a0, c0)
                sy = min(b1, d1) - max(b0, d0)
                if sx > 3 and sy > 3:
                    dentro = (a0 <= c0 and a1 >= c1 and b0 <= d0 and b1 >= d1) \
                        or (c0 <= a0 and c1 >= a1 and d0 <= b0 and d1 >= b1)
                    if not dentro:
                        malos.append(f"SE PISAN  {n1} y {n2}  "
                                     f"({sx:.0f}x{sy:.0f} px)")
        return malos


def chasis(L, titulo):
    """Sobre el chasis real no se dibuja nada: ya esta ahi."""
    if not L.conFondo:
        L.caja("chasis.panel", PANEL_X, PANEL_Y, PANEL_W, PANEL_H, PANEL_BG,
               (255, 196, 60, 255), 3, vigilar=False)
        L.caja("chasis.pantalla", PANT_X, PANT_Y, PANT_W, PANT_H, PANT_BG,
               NARANJA, 6, vigilar=False)


def maqueta_gts():
    """La pantalla de la derecha: conmutador, barra, buscador y filas."""
    L = Lienzo()
    chasis(L, "GTS")
    MARGEN, BOT, BOT_SEP, FILA = 12, 44, 8, 70

    # ⚠ La fila de navegacion del panel se dibuja AUNQUE no sea nuestra: es lo
    #   que hace que la maqueta cace que algo se le monta encima. Sin ella, el
    #   conmutador estaba puesto justo sobre INICIO y la X, y la comprobacion
    #   decia «limpio».
    L.caja("nav.atras", PANEL_X + 18, PANEL_Y + NAV_ALTO // 2 - 24, 60, 48,
           None, (120, 130, 160, 120))
    L.texto("nav.inicio", T("inicio"), PANEL_X + 92, PANEL_Y + NAV_ALTO // 2 - 14,
            28, BLANCO)
    L.caja("nav.cerrar", PANEL_X + PANEL_W - 18 - 80,
           PANEL_Y + NAV_ALTO // 2 - 32, 80, 64, None, (120, 130, 160, 120))

    # --- FILA 1: conmutador a la izquierda de la pantalla, iconos a la derecha
    y1 = PANT_Y + MARGEN
    for i, et in enumerate((T("gts.c_pokemon"), T("gts.c_objetos"))):
        bx = PANT_X + MARGEN + i * 104
        L.caja(f"conm{i}", bx, y1, 100, BOT,
               NARANJA if i == 0 else (42, 49, 69, 255), (32, 40, 60, 255))
        L.texto(f"conm{i}.t", et, bx + 50, y1 + 10, 14, BLANCO, "centro",
                limite=94)

    n_botones = 5
    total = n_botones * BOT + (n_botones - 1) * BOT_SEP
    x0_bot = PANT_X + PANT_W - MARGEN - total
    for i in range(n_botones):
        L.caja(f"barra.b{i}", x0_bot + i * (BOT + BOT_SEP), y1, BOT, BOT,
               (58, 69, 96, 255), (32, 40, 60, 255))

    # --- FILA 2: el buscador, a todo lo ancho
    y2 = y1 + BOT + 6
    L.caja("busca", PANT_X + MARGEN, y2, PANT_W - 2 * MARGEN, 28,
           (30, 36, 50, 255))
    L.texto("busca.pista", "Nombre del Pokemon...", PANT_X + MARGEN + 8, y2 + 8,
            14, (136, 146, 172, 255))

    # --- cabecera ordenable
    hy = y2 + 34
    for nombre, cx, ancho in ((T("gts.col_oferta"), 74, 200),
                              (T("gts.col_ivs"), 296, 90),
                              (T("gts.col_precio"), 470, 140),
                              (T("gts.col_expira"), 620, 100)):
        L.texto(f"cab.{nombre}", nombre + " \u2014", PANT_X + MARGEN + cx, hy,
                13, SUAVE, limite=ancho)

    # --- filas
    aw = PANT_W - 2 * MARGEN
    for n in range(5):
        y = hy + 18 + n * FILA
        ax = PANT_X + MARGEN
        L.caja(f"fila{n}", ax, y, aw, FILA - 6, FILA_BG, FILA_BORDE)
        L.caja(f"fila{n}.sprite", ax + 6, y + 3, 58, 58, (200, 210, 235, 255),
               vigilar=False)
        L.texto(f"fila{n}.nombre", "Nidoking", ax + 70, y + 8, 20, OSCURO,
                limite=210)
        L.caja(f"fila{n}.tipo1", ax + 70, y + 34, 62, 17, (160, 64, 160, 255))
        L.caja(f"fila{n}.tipo2", ax + 136, y + 34, 62, 17, (176, 128, 64, 255))
        L.texto(f"fila{n}.nivel", "Nv 84", ax + 300, y + 14, 16, OSCURO)
        L.caja(f"fila{n}.ivs", ax + 296, y + 36, 74, 17, (200, 210, 235, 255))
        L.texto(f"fila{n}.precio", "1.500", ax + aw - 150, y + 10, 21,
                (138, 106, 0, 255), "der")
        L.texto(f"fila{n}.trato", "precio justo", ax + aw - 150, y + 34, 12,
                SUAVE, "der")
        L.texto(f"fila{n}.expira", "2d 3h", ax + aw - 150, y + 48, 11, SUAVE,
                "der")
        L.caja(f"fila{n}.comprar", ax + aw - 132, y + 16, 124, 32, VERDE)

    L.texto("contador", "1-5 de 12", PANT_X + PANT_W - MARGEN - 4,
            PANT_Y + PANT_H - MARGEN - 20, 13, SUAVE, "der")
    return L


def maqueta_panel(vender):
    """El panel izquierdo: comprar (retrato grande) o publicar (con opciones)."""
    L = Lienzo()
    chasis(L, "PANEL — " + ("PUBLICAR" if vender else "COMPRAR"))
    ret_h = 190 if vender else 220
    # ⚠ El conmutador SE FUE A LA DERECHA, que es donde lo pidio el usuario --
    #   y de paso deja de montarse encima de INICIO y de la X, que es lo que
    #   pasaba estando aqui.
    RET_X, RET_Y, RET_W = PANEL_X + 24, PANEL_Y + NAV_ALTO + 4, PANEL_W - 48
    cx = PANEL_X + PANEL_W // 2

    L.caja("retrato", RET_X, RET_Y, RET_W, ret_h, (34, 42, 60, 255),
           (106, 115, 152, 255))
    y = RET_Y + ret_h + 8
    L.texto("nombre", "Zoroark", cx, y, 26, BLANCO, "centro", limite=PANEL_W - 60)
    y += 34
    L.caja("tipo", cx - 40, y, 80, 20, (112, 88, 72, 255))
    y += 26
    L.texto("nivel", "Nv 1   ✦ SHINY", cx, y, 16, ORO, "centro")
    y += 24

    if vender:
        L.texto("sep1", "─" * 30, cx, y, 10, SUAVE, "centro", vigilar=False)
        y += 8
        for i, (et, va) in enumerate((("Naturaleza", "Alegre"),
                                      ("Habilidad", "Ilusion"),
                                      ("IVs perfectos", "3 × 31"),
                                      ("Esta en", "en tu equipo"))):
            L.texto(f"car{i}.et", et, PANEL_X + 30, y + i * 20, 14, SUAVE)
            L.texto(f"car{i}.va", va, PANEL_X + PANEL_W - 30, y + i * 20, 14,
                    (232, 238, 248, 255), "der", limite=PANEL_W // 2 - 20)
        y += 84
        L.texto("est.et", "VALOR ESTIMADO", cx, y + 8, 13, SUAVE, "centro")
        L.texto("est.va", "570", cx, y + 24, 24, ORO, "centro")
        L.texto("precio.et", "TU PRECIO", PANEL_X + 30, PANEL_Y + 508, 13, SUAVE)
        L.caja("precio.campo", PANEL_X + 30, PANEL_Y + 524, PANEL_W - 60, 30,
               (30, 36, 50, 255))
        L.texto("dur.et", T("gts.duracion"), PANEL_X + 30, PANEL_Y + 562, 13, SUAVE)
        bw = (PANEL_W - 60) // 3 - 4
        for i, et in enumerate((T("gts.dur_24"), T("gts.dur_48"),
                                T("gts.dur_168"))):
            bx = PANEL_X + 30 + i * (bw + 6)
            L.caja(f"dur{i}", bx, PANEL_Y + 578, bw, 30, (58, 69, 96, 255))
            L.texto(f"dur{i}.t", et, bx + bw // 2, PANEL_Y + 586, 14, BLANCO,
                    "centro", limite=bw - 6)
        L.caja("publicar", PANEL_X + 30, PANEL_Y + PANEL_H - 74, PANEL_W - 60,
               48, VERDE)
    else:
        L.texto("precio", "1.500", cx, y, 32, ORO, "centro")
        y += 36
        L.texto("trato", "por encima del estimado (334)", cx, y, 13, SUAVE,
                "centro", limite=PANEL_W - 40)
        y += 20 + 8
        anchoTab = (PANEL_W - 60) // 3 - 4
        for i, et in enumerate(("EST", "IVS", "EVS")):
            bx = PANEL_X + 30 + i * (anchoTab + 6)
            L.caja(f"tab{i}", bx, y, anchoTab, 24, (58, 48, 32, 255))
            L.texto(f"tab{i}.t", et, bx + anchoTab // 2, y + 5, 14, ORO, "centro")
        y += 32
        for i, (et, va) in enumerate((("Naturaleza", "Grosera"),
                                      ("Habilidad", "Rivalidad"),
                                      ("Genero", "Macho"),
                                      ("Teratipo", "Veneno"),
                                      ("Rareza", "Comun"),
                                      ("Vendedor", "TheJuanCE"))):
            L.texto(f"det{i}.et", et, PANEL_X + 30, y + i * 19, 14, SUAVE)
            L.texto(f"det{i}.va", va, PANEL_X + PANEL_W - 30, y + i * 19, 14,
                    (232, 238, 248, 255), "der", limite=PANEL_W // 2 - 20)
        L.caja("comprar", PANEL_X + 30, PANEL_Y + PANEL_H - 74, PANEL_W - 60,
               48, VERDE)
    return L


def recortar(s, ancho, alto):
    """Lo mismo que hace el Java: cortar con puntos suspensivos."""
    if ancho_mc(s, alto) <= ancho:
        return s
    corte = len(s)
    while corte > 1 and ancho_mc(s[:corte] + "\u2026", alto) > ancho:
        corte -= 1
    return s[:corte].rstrip() + "\u2026"


def nav(L):
    """La fila de navegacion del panel.

    ⚠ Se dibuja AUNQUE no sea de esta pantalla: es lo que hace que la maqueta
    cace que algo se le monta encima. Sin ella, el conmutador estaba puesto
    justo sobre INICIO y la X, y la comprobacion decia «limpio».
    """
    L.caja("nav.atras", PANEL_X + 18, PANEL_Y + NAV_ALTO // 2 - 24, 60, 48,
           None, (120, 130, 160, 120))
    L.texto("nav.inicio", T("inicio"), PANEL_X + 92, PANEL_Y + NAV_ALTO // 2 - 14,
            28, BLANCO)
    L.caja("nav.cerrar", PANEL_X + PANEL_W - 18 - 80,
           PANEL_Y + NAV_ALTO // 2 - 32, 80, 64, None, (120, 130, 160, 120))


# --- las mismas medidas que el Java. Si cambian alli, cambian aqui.
MARGEN, BOT, BOT_SEP, FILA, PIE, CONM_W = 12, 44, 8, 70, 34, 100
LISTA_Y = PANT_Y + MARGEN + BOT + 6 + 28 + 34
FILAS = max(1, ((PANT_Y + PANT_H - MARGEN - PIE) - (LISTA_Y + 18)) // FILA)


def cabecera_derecha(L, marcado):
    """Conmutador, iconos y buscador: identico en las dos mitades."""
    y1 = PANT_Y + MARGEN
    for i, et in enumerate((T("gts.c_pokemon"), T("gts.c_objetos"))):
        bx = PANT_X + MARGEN + i * (CONM_W + 4)
        act = (i == 0) == (marcado == "pokemon")
        L.caja(f"conm{i}", bx, y1, CONM_W, BOT,
               NARANJA if act else (42, 49, 69, 255), (32, 40, 60, 255))
        L.texto(f"conm{i}.t", et, bx + CONM_W // 2, y1 + 10, 14, BLANCO,
                "centro", limite=CONM_W - 6)

    n = 3 if marcado == "objetos" else 5
    total = n * BOT + (n - 1) * BOT_SEP
    x0 = PANT_X + PANT_W - MARGEN - total
    for i in range(n):
        L.caja(f"barra.b{i}", x0 + i * (BOT + BOT_SEP), y1, BOT, BOT,
               (58, 69, 96, 255), (32, 40, 60, 255))

    y2 = y1 + BOT + 6
    L.caja("busca", PANT_X + MARGEN, y2, PANT_W - 2 * MARGEN, 28,
           (30, 36, 50, 255))
    L.texto("busca.pista", T("mercado.buscar_pista"), PANT_X + MARGEN + 8, y2 + 8,
            14, (136, 146, 172, 255))
    return y2


def pie(L, n_total):
    """Contador y paginacion. ⚠ Es lo que se pisaba con la ultima fila."""
    y = PANT_Y + PANT_H - MARGEN - 28
    L.caja("pag.izq", PANT_X + MARGEN, y, 60, 26, (79, 111, 176, 255))
    L.texto("pag.n", "1 / 3", PANT_X + MARGEN + 100, y + 5, 15, SUAVE)
    L.caja("pag.der", PANT_X + MARGEN + 160, y, 60, 26, (79, 111, 176, 255))
    L.texto("cont", f"1-{FILAS} de {n_total}", PANT_X + PANT_W - MARGEN - 4,
            PANT_Y + PANT_H - MARGEN - 20, 13, SUAVE, "der")


def maqueta_objetos():
    """El escaparate de objetos: la lista, con su cabecera y su pie."""
    L = Lienzo()
    chasis(L, "OBJETOS")
    nav(L)
    cabecera_derecha(L, "objetos")

    hy = LISTA_Y
    for nombre, cx, ancho in ((T("mercado.col_objeto"), 74, 220),
                              (T("mercado.col_unidad"), 320, 150),
                              (T("gts.col_precio"), 480, 145)):
        L.texto(f"cab.{nombre}", nombre + " \u2014", PANT_X + MARGEN + cx, hy,
                14, SUAVE, limite=ancho)

    aw = PANT_W - 2 * MARGEN
    for n in range(FILAS):
        y = hy + 18 + n * FILA
        ax = PANT_X + MARGEN
        L.caja(f"fila{n}", ax, y, aw, FILA - 6, FILA_BG, FILA_BORDE)
        L.caja(f"fila{n}.icono", ax + 10, y + 12, 40, 40, (200, 210, 235, 255),
               vigilar=False)
        L.texto(f"fila{n}.nombre",
                recortar("Escaleras de ladrillos de piedra", 240, 20),
                ax + 70, y + 10, 20, OSCURO, limite=240)
        L.texto(f"fila{n}.cant", "x64", ax + 70, y + 36, 15, SUAVE)
        L.texto(f"fila{n}.vend", "TheJuanCE", ax + 320, y + 12, 14, SUAVE,
                limite=150)
        L.texto(f"fila{n}.unidad", T("mercado.por_unidad", "23"), ax + 320, y + 36,
                14, SUAVE, limite=150)
        L.texto(f"fila{n}.precio", "1.500", ax + aw - 150, y + 10, 21,
                (138, 106, 0, 255), "der")
        L.texto(f"fila{n}.queda", "2d 3h", ax + aw - 150, y + 40, 12, SUAVE,
                "der")
        L.caja(f"fila{n}.comprar", ax + aw - 132, y + 16, 124, 32, VERDE)

    pie(L, 12)
    return L


def maqueta_panel_obj(vender):
    """El panel del escaparate: comprar (retrato grande) o publicar."""
    L = Lienzo()
    chasis(L, "OBJETO — " + ("PUBLICAR" if vender else "COMPRAR"))
    nav(L)
    ret_h = 190 if vender else 220
    RET_X, RET_Y, RET_W = PANEL_X + 24, PANEL_Y + NAV_ALTO + 4, PANEL_W - 48
    cx = PANEL_X + PANEL_W // 2
    L.caja("retrato", RET_X, RET_Y, RET_W, ret_h, (18, 22, 31, 255),
           (57, 65, 92, 255))

    if vender:
        y = RET_Y + ret_h + 8
        L.texto("nombre", recortar("Escaleras de roca musgosa", RET_W, 22),
                cx, y, 22, BLANCO, "centro", limite=RET_W)
        y += 26
        L.texto("tengo", "tienes 64", cx, y, 14, SUAVE, "centro")

        L.texto("cant.et", T("mercado.cantidad"), PANEL_X + 30, PANEL_Y + 404, 13,
                SUAVE)
        bw = (PANEL_W - 60 - 3 * 6) // 4
        for i, et in enumerate(("x1", "x8", "x64", T("mercado.todo"))):
            bx = PANEL_X + 30 + i * (bw + 6)
            L.caja(f"cant{i}", bx, PANEL_Y + 420, bw, 32, (79, 111, 176, 255))
            L.texto(f"cant{i}.t", et, bx + bw // 2, PANEL_Y + 428, 15, BLANCO,
                    "centro", limite=bw - 10)
        L.texto("cant.n", "x64", cx, PANEL_Y + 460, 22, BLANCO, "centro")

        L.texto("precio.et", T("mercado.precio_total"), PANEL_X + 30,
                PANEL_Y + 486, 13, SUAVE)
        L.caja("precio.campo", PANEL_X + 30, PANEL_Y + 502, PANEL_W - 60, 30,
               (30, 36, 50, 255))
        L.texto("precio.ud", T("mercado.por_unidad", "23"), PANEL_X + 30,
                PANEL_Y + 540, 13, SUAVE)

        L.texto("dur.et", T("gts.duracion"), PANEL_X + 30, PANEL_Y + 562, 13, SUAVE)
        dw = (PANEL_W - 60 - 2 * 6) // 3
        for i, et in enumerate((T("gts.dur_24"), T("gts.dur_48"),
                                T("gts.dur_168"))):
            bx = PANEL_X + 30 + i * (dw + 6)
            L.caja(f"dur{i}", bx, PANEL_Y + 578, dw, 32, (58, 69, 96, 255))
            # ⚠ 13 y no 15: `botonPeq` ENCOGE la letra hasta que cabe. La
            #   maqueta tiene que encoger igual o dira que no cabe algo que si.
            L.texto(f"dur{i}.t", et, bx + dw // 2, PANEL_Y + 586, 13, BLANCO,
                    "centro", limite=dw - 10)
        L.caja("publicar", PANEL_X + 30, PANEL_Y + PANEL_H - 72, PANEL_W - 60,
               56, VERDE)
    else:
        y = RET_Y + ret_h + 12
        L.texto("nombre", recortar("Panel de cristal tintado", RET_W, 24),
                cx, y, 24, BLANCO, "centro", limite=RET_W)
        y += 28
        L.texto("cant", "x64", cx, y, 16, SUAVE, "centro")
        y += 26
        y += 14
        L.texto("precio", "1.500", cx, y, 34, ORO, "centro")
        y += 42
        L.texto("unidad", T("mercado.por_unidad", "23"), cx, y, 14, SUAVE, "centro")
        y += 26 + 12
        for i, (et, va) in enumerate((("Vendedor", "TheJuanCE"),
                                      ("EXPIRA", "2d 3h"))):
            L.texto(f"det{i}.et", et, PANEL_X + 30, y + i * 22, 14, SUAVE)
            L.texto(f"det{i}.va", va, PANEL_X + PANEL_W - 30, y + i * 22, 14,
                    (232, 238, 248, 255), "der", limite=PANEL_W // 2 - 20)
        L.caja("comprar", PANEL_X + 30, PANEL_Y + PANEL_H - 72, PANEL_W - 60,
               56, VERDE)
    return L


# --- CAZAS. Mismas medidas que CazasScreen.java.
PEST_W, PEST_H = 130, 36
FILA_CAZA = 140
LISTA_CAZA_Y = PANT_Y + MARGEN + PEST_H + 12
RET_CAZA_H = 226
COL_MODELO, COL_TEXTO = 8, 136
COL_PREMIO, ANCHO_PREMIO = 470, 170
ANCHO_BOTON = 119


def maqueta_cazas():
    """La lista de cazas: 2 pestañas y 3 objetivos grandes."""
    L = Lienzo()
    chasis(L, "CAZAS")
    nav(L)

    for i, clave in enumerate(("caza.p_caza", "caza.p_crianza")):
        bx = PANT_X + MARGEN + i * (PEST_W + 6)
        L.caja(f"pest{i}", bx, PANT_Y + MARGEN, PEST_W, PEST_H,
               NARANJA if i == 0 else (42, 49, 69, 255), (32, 40, 60, 255))
        L.texto(f"pest{i}.t", T(clave), bx + PEST_W // 2, PANT_Y + MARGEN + 11,
                15, BLANCO, "centro", limite=PEST_W - 8)
    # ⚠ La ayuda va PEGADA A LA DERECHA. Si se alineara a la izquierda tras las
    #   pestañas, «Cuenta al ECLOSIONAR el huevo» se montaria encima de ellas.
    L.texto("ayuda", T("caza.ayuda_crianza"), PANT_X + PANT_W - MARGEN,
            PANT_Y + MARGEN + 12, 13, SUAVE, "der")

    aw = PANT_W - 2 * MARGEN
    for n in range(3):
        y = LISTA_CAZA_Y + n * FILA_CAZA
        ax = PANT_X + MARGEN
        L.caja(f"fila{n}", ax, y, aw, FILA_CAZA - 8, FILA_BG, FILA_BORDE)
        L.caja(f"fila{n}.modelo", ax + COL_MODELO, y + 8, 116, 116,
               (200, 210, 235, 255), vigilar=False)
        # ⚠ El nombre mas largo de Kanto+Johto, no uno comodo: si cabe este,
        #   caben todos. «Charizard» habria pasado la prueba y mentido.
        L.texto(f"fila{n}.nombre", "Nidoran\u2642", ax + COL_TEXTO, y + 14, 28,
                OSCURO, limite=COL_PREMIO - COL_TEXTO - 20)
        for e in range(3):
            L.caja(f"fila{n}.est{e}", ax + COL_TEXTO + e * 24, y + 54, 20, 20,
                   ORO if e == 0 else (0, 0, 0, 60), vigilar=False)
        L.caja(f"fila{n}.progreso", ax + COL_TEXTO, y + 88,
               COL_PREMIO - COL_TEXTO - 30, 24, (32, 40, 60, 255))
        L.texto(f"fila{n}.prog", "2 / 3", ax + COL_TEXTO + 160, y + 93, 13,
                BLANCO, "centro", vigilar=False)
        pxx = ax + COL_PREMIO
        L.texto(f"fila{n}.rec", T("caza.recompensa"), pxx, y + 10, 12, SUAVE,
                limite=ANCHO_PREMIO)
        L.texto(f"fila{n}.plata", T("caza.plata", "2.500"), pxx, y + 26, 17,
                (122, 93, 0, 255), limite=ANCHO_PREMIO)
        L.texto(f"fila{n}.marcas", T("caza.marcas", "24"), pxx, y + 48, 15,
                (31, 91, 133, 255), limite=ANCHO_PREMIO)
        # ⚠ DOS objetos, y el mas largo de los que se dan: si cabe «Maximo
        #   Revivir x1», caben todos.
        for i, et in enumerate(("Ultra Ball x4", "Maximo Revivir x1")):
            L.texto(f"fila{n}.obj{i}", et, pxx + 24, y + 70 + i * 22, 14,
                    OSCURO, limite=ANCHO_PREMIO - 24)
        L.caja(f"fila{n}.cobrar", ax + aw - 8 - ANCHO_BOTON, y + 44,
               ANCHO_BOTON, 44, VERDE)
        L.texto(f"fila{n}.cobrar.t", T("caza.cobrar"),
                ax + aw - 8 - ANCHO_BOTON // 2, y + 56, 18, BLANCO, "centro",
                limite=ANCHO_BOTON - 10)
    return L


def maqueta_panel_caza():
    """El panel: modelo, cuenta atras, progreso y recompensa."""
    L = Lienzo()
    chasis(L, "CAZA \u2014 PANEL")
    nav(L)
    RET_X, RET_Y, RET_W = PANEL_X + 24, PANEL_Y + NAV_ALTO + 4, PANEL_W - 48
    cx = PANEL_X + PANEL_W // 2

    L.caja("retrato", RET_X, RET_Y, RET_W, RET_CAZA_H, (18, 22, 31, 255),
           (57, 65, 92, 255))

    cy = RET_Y + RET_CAZA_H + 6
    L.caja("reloj", RET_X, cy, RET_W, 58, (16, 20, 31, 255), (74, 85, 120, 255))
    L.texto("reloj.et", T("caza.rotan"), cx, cy + 6, 13, SUAVE, "centro",
            limite=RET_W - 10)
    # ⚠ 30 px: era el numero peor legible de la pantalla y es el mas importante.
    L.texto("reloj.n", "18h 42m", cx, cy + 24, 30, ORO, "centro",
            limite=RET_W - 10)

    y = cy + 74
    L.texto("nombre", "Charizard", cx, y, 26, BLANCO, "centro", limite=RET_W)
    y += 30
    for e in range(3):
        L.caja(f"est{e}", cx - 37 + e * 26, y + 2, 22, 22,
               ORO if e < 2 else (0, 0, 0, 60), vigilar=False)
    y += 34
    L.caja("progreso", PANEL_X + 30, y, PANEL_W - 60, 20, (32, 40, 60, 255))
    y += 32
    L.texto("rec.et", T("caza.recompensa"), cx, y + 12, 14, SUAVE, "centro")
    y += 36
    L.texto("rec.plata", T("caza.plata", "2.500"), PANEL_X + 30, y, 18, ORO,
            limite=PANEL_W - 60)
    y += 25
    L.texto("rec.marcas", T("caza.marcas", "24"), PANEL_X + 30, y, 18,
            (127, 212, 255, 255), limite=PANEL_W - 60)
    y += 25
    for i, et in enumerate(("Ultra Ball x4", "Maximo Revivir x1")):
        L.texto(f"rec.obj{i}", et, PANEL_X + 56, y + i * 25, 18, BLANCO,
                limite=PANEL_W - 86)
    L.caja("cobrar", PANEL_X + 30, PANEL_Y + PANEL_H - 72, PANEL_W - 60, 56, VERDE)
    return L


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.parse_args()
    SALIDA.mkdir(parents=True, exist_ok=True)

    total = 0
    for nombre, L in (("gts_lista", maqueta_gts()),
                      ("gts_comprar", maqueta_panel(False)),
                      ("gts_publicar", maqueta_panel(True)),
                      ("obj_lista", maqueta_objetos()),
                      ("obj_comprar", maqueta_panel_obj(False)),
                      ("obj_publicar", maqueta_panel_obj(True)),
                      ("cazas_lista", maqueta_cazas()),
                      ("cazas_panel", maqueta_panel_caza())):
        avisos = L.avisos + L.solapes()
        ruta = SALIDA / f"maqueta_{nombre}.png"
        L.im.save(ruta)
        print(f"\n  {nombre}  ->  {ruta}")
        if avisos:
            total += len(avisos)
            for a in avisos:
                print("     " + a)
        else:
            print("     sin desbordes ni solapes")

    print()
    if total:
        print(f"  {total} PROBLEMAS DE MAQUETA. Estan marcados en rojo en el PNG.")
    else:
        print("  Maqueta limpia.")
    print("\n  OJO: esto comprueba GEOMETRIA, no como se ve de verdad en el")
    print("  juego. Sigue haciendo falta abrirlo y mirarlo.")


if __name__ == "__main__":
    main()
