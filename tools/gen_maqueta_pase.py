#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
LA MAQUETA DEL PASE DE BATALLA, sobre el chasis real y con las anchuras reales.

⚠⚠ UNA MAQUETA QUE MIDE ES UNA PRUEBA.

   Es la lección del 2026-08-25 con `gen_maqueta_mercado.py`: en su primera
   pasada seria encontró CUATRO fallos que no daban ningún error —una fila
   dibujada encima de la paginación, una columna que decía ordenar sin ordenar,
   nombres de objeto metiéndose en la columna de al lado y una cabecera que no
   cabía con su flecha—. **Ninguno se habría visto revisando el código**.

⚠⚠⚠ LAS MEDIDAS SE LEEN DE `PaseScreen.java`, NO SE ESCRIBEN AQUÍ.

   Copiarlas sería una SEGUNDA lista de constantes que nada obliga a coincidir
   con la que dibuja el juego, y sería el peor caso posible: una maqueta que
   dice «cabe» midiendo unos números mientras el juego pinta otros.

⚠ Y LOS TEXTOS DE LAS TARJETAS SALEN DEL CATÁLOGO DE VERDAD (`PaseCatalogo.java`),
  no de ejemplos inventados: lo que hay que comprobar es que quepan LOS NOMBRES
  QUE VA A HABER. Los nombres traducidos los pone el cliente, así que aquí se
  mide el identificador formateado, que es igual de largo o más.

Uso:
    python tools/gen_maqueta_pase.py
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(RAIZ / "tools"))

from gen_maqueta_mercado import (  # noqa: E402
    BLANCO, NARANJA, ORO, SUAVE, VERDE,
    Lienzo, ancho_mc, chasis,
    PANEL_X, PANEL_Y, PANEL_W, PANEL_H,
    PANT_X, PANT_Y, PANT_W, PANT_H, NAV_ALTO, SALIDA,
)

PANTALLA = (RAIZ / "mod/src/client/java/net/pokereport/luna/client/pokepad"
            / "PaseScreen.java")
CATALOGO = (RAIZ / "mod/src/main/java/net/pokereport/luna/pase"
            / "PaseCatalogo.java")

VIOLETA = (185, 140, 255, 255)
HUNDIDO = (10, 14, 22, 255)
GRIS = (90, 102, 120, 255)
RAREZA = {
    "COMUN": (143, 160, 200, 255),
    "RARA": (79, 168, 255, 255),
    "EPICA": (185, 140, 255, 255),
    "LEGENDARIA": (255, 214, 92, 255),
}


def constantes() -> dict:
    """Las `private static final int` de la pantalla, leídas del fuente."""
    if not PANTALLA.exists():
        raise SystemExit(f"No encuentro {PANTALLA}")
    texto = PANTALLA.read_text(encoding="utf-8")
    vals = {k: int(v) for k, v in re.findall(
        r"private static final int ([A-Z_]+)\s*=\s*(-?\d+);", texto)}
    faltan = [k for k in ("MARGEN", "GAP", "CARD_W", "CARD_H", "CARDS_Y",
                          "MAPA_Y", "ANILLO_Y", "ANILLO_R", "CAJA_PASE_Y",
                          "CAJA_PASE_H", "BOTON_COMPRAR_DY", "BOTON_COMPRAR_H",
                          "BOTON_RECLAMAR_Y", "BOTON_RECLAMAR_H", "AYUDA_Y")
              if k not in vals]
    if faltan:
        raise SystemExit(
            "PaseScreen.java ha cambiado de forma: no encuentro "
            + ", ".join(faltan) + ". Medir a medias es peor que no medir.")
    return vals


def premios() -> list:
    """Los cien premios, leídos de `PaseCatalogo.java`."""
    txt = CATALOGO.read_text(encoding="utf-8")
    salida = []
    for m in re.finditer(
            r'/\*\s*(\d+)\s*\*/\s*(?:o\("([a-z_0-9]+)",\s*(\d+),\s*Rareza\.([A-Z]+)\)'
            r'|p\("([a-z_0-9]+)",\s*(\d+),\s*(true|false)\))', txt):
        nivel = int(m.group(1))
        if m.group(2):
            nombre = m.group(2).replace("_", " ").title()
            salida.append((nivel, nombre, int(m.group(3)), m.group(4), False))
        else:
            salida.append((nivel, m.group(5).title(), 1, "LEGENDARIA", True))
    if len(salida) != 100:
        raise SystemExit(f"He leido {len(salida)} premios de PaseCatalogo.java "
                         f"y tiene que haber 100: ha cambiado de forma")
    return salida


def partir(texto, ancho, alto, max_lineas):
    """Igual que `PaseScreen.partir`: por palabras, con tope de líneas."""
    salida, actual = [], ""
    for palabra in texto.split(" "):
        prueba = palabra if not actual else actual + " " + palabra
        if ancho_mc(prueba, alto) > ancho and actual:
            salida.append(actual)
            actual = palabra
            if len(salida) == max_lineas:
                return salida
        else:
            actual = prueba
    if len(salida) < max_lineas and actual:
        salida.append(actual)
    return salida


def panel(L: Lienzo, C: dict) -> None:
    cx = PANEL_X + PANEL_W // 2
    L.texto("panel.titulo", "PASE DE BATALLA", cx, PANEL_Y + NAV_ALTO + 8, 26,
            ORO, "centro", limite=PANEL_W - 40)
    L.texto("panel.temporada", "TEMPORADA 12  ·  47 dias", cx,
            PANEL_Y + NAV_ALTO + 40, 15, SUAVE, "centro", limite=PANEL_W - 40)

    r = C["ANILLO_R"]
    L.caja("anillo.caja", cx - r, C["ANILLO_Y"] - r, r * 2, r * 2, HUNDIDO, ORO, 8)
    # ⚠⚠ EL LIMITE DE LO QUE VA DENTRO DEL ARO NO ES EL PANEL: ES EL ANCHO DEL
    #    CIRCULO A ESA ALTURA, y se calcula. Un `r * 1.5` a ojo dijo que «100»
    #    no cabia cuando si cabe --y al reves, en otra altura habria dicho que
    #    cabe algo que se sale--. La cuerda de un circulo a distancia d del
    #    centro mide 2*sqrt(r^2 - d^2), y la d que manda es la del renglon MAS
    #    LEJOS del centro.
    import math

    def cuerda(desde_y, alto_txt):
        d = max(abs(desde_y), abs(desde_y + alto_txt))
        return int(2 * math.sqrt(max(1, r * r - d * d))) - 6

    L.texto("anillo.nivel", "100", cx, C["ANILLO_Y"] - 24, 46, BLANCO, "centro",
            limite=cuerda(-24, 46))
    L.texto("anillo.rotulo", "NIVEL", cx, C["ANILLO_Y"] + 16, 13, SUAVE,
            "centro", limite=cuerda(16, 13))
    L.texto("panel.cuenta", "100  /  100", cx, C["ANILLO_Y"] + r + 10, 16, SUAVE,
            "centro", limite=PANEL_W - 60)

    L.texto("panel.xp", "834 / 834 XP", cx, 364, 17, BLANCO, "centro",
            limite=PANEL_W - 60)
    L.caja("panel.barraxp", PANEL_X + 26, 386, PANEL_W - 52, 15, HUNDIDO, GRIS, 1)

    L.texto("panel.hoy", "XP DE HOY", PANEL_X + 26, C["AYUDA_Y"], 15, SUAVE)
    L.texto("panel.hoy2", "3.600 / 3.600", PANEL_X + PANEL_W - 64, C["AYUDA_Y"],
            15, NARANJA, "der")
    L.caja("panel.ayuda", PANEL_X + PANEL_W - 52, C["AYUDA_Y"] - 4, 26, 26,
           HUNDIDO, VIOLETA, 1)
    L.caja("panel.barrahoy", PANEL_X + 26, 438, PANEL_W - 52, 14, HUNDIDO, GRIS, 1)
    # La más larga de las tres frases del pie.
    L.texto("panel.pie", "Especie nueva en la Pokedex   +120", cx, 460, 13,
            SUAVE, "centro", limite=PANEL_W - 44)

    y = C["CAJA_PASE_Y"]
    L.caja("panel.pase", PANEL_X + 22, y, PANEL_W - 44, C["CAJA_PASE_H"],
           HUNDIDO, VIOLETA, 2)
    L.texto("panel.pase.tit", "DESBLOQUEA EL PASE", cx, y + 12, 16, BLANCO,
            "centro", limite=PANEL_W - 60)
    L.caja("comprar.btn", PANEL_X + 28, y + C["BOTON_COMPRAR_DY"],
           PANEL_W - 56, C["BOTON_COMPRAR_H"], None, VIOLETA, 2)
    L.texto("comprar.precio", "15.000", cx + 16, y + C["BOTON_COMPRAR_DY"] + 10,
            22, ORO, "centro")
    L.texto("saldo.txt", "Tienes 148.500 LunaCoins", cx, y + 82, 13, SUAVE,
            "centro", limite=PANEL_W - 60)

    L.caja("panel.reclamar", PANEL_X + 22, C["BOTON_RECLAMAR_Y"], PANEL_W - 44,
           C["BOTON_RECLAMAR_H"], None, VERDE, 2)
    L.texto("panel.reclamar.t", "RECLAMAR TODO  (100)", PANEL_X + PANEL_W // 2,
            C["BOTON_RECLAMAR_Y"] + 18, 20, BLANCO, "centro", limite=PANEL_W - 60)

    L.texto("panel.hint1", "Sube el pase capturando, minando,",
            PANEL_X + PANEL_W // 2, 672, 13, SUAVE, "centro", limite=PANEL_W - 30)
    L.texto("panel.hint2", "pescando, criando y en la Torre",
            PANEL_X + PANEL_W // 2, 690, 13, SUAVE, "centro", limite=PANEL_W - 30)


def carril(L: Lienzo, C: dict, datos: list) -> None:
    cols = max(1, (PANT_W - 2 * C["MARGEN"] + C["GAP"]) // (C["CARD_W"] + C["GAP"]))
    usado = cols * C["CARD_W"] + (cols - 1) * C["GAP"]
    fila_x = PANT_X + (PANT_W - usado) // 2
    print(f"  columnas calculadas: {cols}  "
          f"(ocupan {usado} de {PANT_W - 2 * C['MARGEN']} disponibles)")

    L.caja("carril.marca", PANT_X + C["MARGEN"], PANT_Y + 14, 8, 40, ORO, None,
           vigilar=False)
    L.texto("carril.tramo", "ENTRENAMIENTO", PANT_X + C["MARGEN"] + 20,
            PANT_Y + 12, 24, ORO)
    L.texto("carril.lema", "EVs, niveles y PP", PANT_X + C["MARGEN"] + 20,
            PANT_Y + 40, 14, SUAVE)
    L.texto("carril.cuenta", "NIVELES 41 - 44",
            PANT_X + PANT_W - C["MARGEN"] - 116, PANT_Y + 20, 15, SUAVE, "der")
    L.caja("carril.izq", PANT_X + PANT_W - C["MARGEN"] - 104, PANT_Y + 14, 44, 34,
           HUNDIDO, SUAVE, 2)
    L.caja("carril.der", PANT_X + PANT_W - C["MARGEN"] - 46, PANT_Y + 14, 44, 34,
           HUNDIDO, SUAVE, 2)

    # ⚠ Se dibuja el tramo con los nombres MAS LARGOS de los cien, no los cuatro
    #   primeros: lo que hay que comprobar es el peor caso.
    peor = sorted(datos, key=lambda d: -ancho_mc(d[1], 15))[:cols]
    for i, (nivel, nombre, cant, rareza, es_poke) in enumerate(peor):
        x = fila_x + i * (C["CARD_W"] + C["GAP"])
        y = C["CARDS_Y"]
        color = RAREZA[rareza]
        L.caja(f"t{i}", x, y, C["CARD_W"], C["CARD_H"], None, color, 2)
        L.caja(f"t{i}.cinta", x, y, C["CARD_W"], 36, color, None, vigilar=False)
        L.texto(f"t{i}.nivel", f"NIVEL {nivel}", x + C["CARD_W"] // 2, y + 9, 19,
                (13, 18, 27, 255), "centro", limite=C["CARD_W"] - 16)
        L.texto(f"t{i}.rareza", rareza, x + C["CARD_W"] // 2, y + 46, 13, color,
                "centro", limite=C["CARD_W"] - 16)
        hueco = 108
        L.caja(f"t{i}.hueco", x + (C["CARD_W"] - hueco) // 2, y + 66, hueco, hueco,
               HUNDIDO, color, 1)
        ty = y + 186
        for j, linea in enumerate(partir(nombre, C["CARD_W"] - 18, 15, 2)):
            L.texto(f"t{i}.txt{j}", linea, x + C["CARD_W"] // 2, ty, 15, BLANCO,
                    "centro", limite=C["CARD_W"] - 18)
            ty += 19
        pie = "✦ VARIOCOLOR  ·  Nivel 50" if es_poke and nivel == 100 else (
            f"x{cant}" if cant > 1 else "ENTRENAMIENTO")
        L.texto(f"t{i}.cant", pie, x + C["CARD_W"] // 2, y + 228, 17, ORO,
                "centro", limite=C["CARD_W"] - 18)
        pie_h, pie_y = 46, y + C["CARD_H"] - 46 - 14
        L.caja(f"t{i}.pie", x + 12, pie_y, C["CARD_W"] - 24, pie_h, None, VERDE, 2)
        L.texto(f"t{i}.pie.t", "REQUIERE PASE", x + C["CARD_W"] // 2, pie_y + 15,
                18, BLANCO, "centro", limite=C["CARD_W"] - 24)

    # El mini-mapa.
    L.caja("mapa", PANT_X + C["MARGEN"], C["MAPA_Y"], PANT_W - 2 * C["MARGEN"], 22,
           HUNDIDO, GRIS, 1)
    L.texto("mapa.pie", "Pulsa la barra para saltar de tramo",
            PANT_X + PANT_W // 2, C["MAPA_Y"] + 30, 13, SUAVE, "centro")


def main() -> None:
    C = constantes()
    datos = premios()
    print("MAQUETA DEL PASE DE BATALLA")
    print(f"  medidas de {PANTALLA.relative_to(RAIZ)}")
    print(f"  premios de {CATALOGO.relative_to(RAIZ)}  ({len(datos)})")

    L = Lienzo()
    chasis(L, "PASE")
    panel(L, C)
    carril(L, C, datos)

    avisos = L.avisos + L.solapes()
    SALIDA.mkdir(parents=True, exist_ok=True)
    ruta = SALIDA / "maqueta-pase.png"
    L.im.save(ruta)
    print(f"\n  {ruta.relative_to(RAIZ)}")
    if avisos:
        for a in avisos:
            print("     " + a)
        print(f"\n  {len(avisos)} PROBLEMAS. Estan marcados en rojo en el PNG.")
    else:
        print("     sin desbordes ni solapes")
        print("\n  Maqueta limpia.")
    print("\n  OJO: esto comprueba GEOMETRIA, no como se ve de verdad en el")
    print("  juego. Sigue haciendo falta abrirlo y mirarlo.")


if __name__ == "__main__":
    main()
