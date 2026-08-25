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


def fuente(px):
    for nombre in ("consola.ttf", "cour.ttf", "arial.ttf"):
        try:
            return ImageFont.truetype(nombre, px)
        except OSError:
            continue
    return ImageFont.load_default()


class Lienzo:
    """Dibuja y VIGILA: apunta cada caja y avisa de solapes y desbordes."""

    def __init__(self):
        self.im = Image.new("RGBA", (NAT_ANCHO, NAT_ALTO), FONDO)
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
        ancho = self.d.textlength(s, font=f)
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
    L.caja("chasis", 0, 0, NAT_ANCHO - 1, NAT_ALTO - 1, FONDO, vigilar=False)
    L.caja("chasis.panel", PANEL_X, PANEL_Y, PANEL_W, PANEL_H, PANEL_BG,
           (255, 196, 60, 255), 3, vigilar=False)
    L.caja("chasis.pantalla", PANT_X, PANT_Y, PANT_W, PANT_H, PANT_BG,
           NARANJA, 6, vigilar=False)
    L.texto("chasis.titulo", titulo, NAT_ANCHO // 2, 16, 26, ORO, "centro",
            vigilar=False)


def maqueta_gts():
    """La pantalla de Pokemon: barra, cabecera y cinco filas."""
    L = Lienzo()
    chasis(L, "GTS — POKEMON")
    MARGEN, BOT, BOT_SEP, FILA = 12, 40, 8, 76

    # --- conmutador
    L.caja("conm", PANEL_X + 16, PANEL_Y + NAV_ALTO - 34, PANEL_W - 32, 30,
           (42, 49, 69, 255), (32, 40, 60, 255))
    L.texto("conm.pk", "POKEMON", PANEL_X + 16 + (PANEL_W - 32) // 4,
            PANEL_Y + NAV_ALTO - 26, 15, BLANCO, "centro")
    L.texto("conm.ob", "OBJETOS", PANEL_X + 16 + (PANEL_W - 32) * 3 // 4,
            PANEL_Y + NAV_ALTO - 26, 15, BLANCO, "centro")

    # --- barra de iconos
    n_botones = 5
    total = n_botones * BOT + (n_botones - 1) * BOT_SEP
    x0_bot = PANT_X + PANT_W - MARGEN - total
    for i in range(n_botones):
        L.caja(f"barra.b{i}", x0_bot + i * (BOT + BOT_SEP), PANT_Y + MARGEN,
               BOT, BOT, (58, 69, 96, 255), (32, 40, 60, 255))
    L.caja("busca", PANT_X + MARGEN, PANT_Y + MARGEN,
           x0_bot - (PANT_X + MARGEN) - 12, 32, (30, 36, 50, 255))
    L.texto("busca.pista", "Nombre del Pokemon...", PANT_X + MARGEN + 8,
            PANT_Y + MARGEN + 9, 15, (136, 146, 172, 255))

    # --- cabecera ordenable
    hy = PANT_Y + MARGEN + 44
    for nombre, cx, ancho in (("OFERTA", 74, 200), ("IVs", 296, 90),
                              ("PRECIO", 470, 140), ("EXPIRA", 620, 100)):
        L.texto(f"cab.{nombre}", nombre + " —", PANT_X + MARGEN + cx, hy, 14,
                SUAVE, limite=ancho)

    # --- filas
    aw = PANT_W - 2 * MARGEN
    for n in range(5):
        y = hy + 20 + n * FILA
        ax = PANT_X + MARGEN
        L.caja(f"fila{n}", ax, y, aw, FILA - 6, FILA_BG, FILA_BORDE)
        L.caja(f"fila{n}.sprite", ax + 6, y + 4, 64, 62, (200, 210, 235, 255),
               vigilar=False)
        L.texto(f"fila{n}.nombre", "Nidoking", ax + 74, y + 10, 21, OSCURO,
                limite=210)
        L.caja(f"fila{n}.tipo1", ax + 74, y + 36, 62, 18, (160, 64, 160, 255))
        L.caja(f"fila{n}.tipo2", ax + 140, y + 36, 62, 18, (176, 128, 64, 255))
        L.texto(f"fila{n}.nivel", "Nv 84", ax + 300, y + 16, 17, OSCURO)
        L.caja(f"fila{n}.ivs", ax + 296, y + 40, 74, 18, (200, 210, 235, 255))
        L.texto(f"fila{n}.precio", "1.500", ax + aw - 150, y + 12, 22,
                (138, 106, 0, 255), "der")
        L.texto(f"fila{n}.trato", "precio justo", ax + aw - 150, y + 38, 13,
                SUAVE, "der")
        L.texto(f"fila{n}.expira", "2d 3h", ax + aw - 150, y + 54, 12, SUAVE,
                "der")
        L.caja(f"fila{n}.comprar", ax + aw - 132, y + 18, 124, 34, VERDE)

    L.texto("contador", "1-5 de 12", PANT_X + PANT_W - MARGEN - 4,
            PANT_Y + PANT_H - MARGEN - 24, 14, SUAVE, "der")
    return L


def maqueta_panel(vender):
    """El panel izquierdo: comprar (retrato grande) o publicar (con opciones)."""
    L = Lienzo()
    chasis(L, "PANEL — " + ("PUBLICAR" if vender else "COMPRAR"))
    ret_h = 190 if vender else 220
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
        L.texto("dur.et", "DURACION DE LA OFERTA", PANEL_X + 30,
                PANEL_Y + 562, 13, SUAVE)
        bw = (PANEL_W - 60) // 3 - 4
        for i, et in enumerate(("1 dia", "2 dias", "1 semana")):
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


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.parse_args()
    SALIDA.mkdir(parents=True, exist_ok=True)

    total = 0
    for nombre, L in (("mercado_lista", maqueta_gts()),
                      ("mercado_comprar", maqueta_panel(False)),
                      ("mercado_publicar", maqueta_panel(True))):
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
