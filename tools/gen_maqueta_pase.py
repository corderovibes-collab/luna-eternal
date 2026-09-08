#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
LA MAQUETA DEL PASE DE BATALLA, sobre el chasis real y con las anchuras reales.

⚠⚠ UNA MAQUETA QUE MIDE ES UNA PRUEBA.

   Es la lección del 2026-08-25 con `gen_maqueta_mercado.py`: en su primera
   pasada seria encontró CUATRO fallos que no daban ningún error —una fila
   dibujada encima de la paginación, una columna que decía ordenar sin ordenar,
   nombres de objeto metiéndose en la columna de al lado y una cabecera que no
   cabía con su flecha—. **Ninguno se habría visto revisando el código**: los
   cuatro son *números que dejaron de cuadrar*.

   El Pase tiene exactamente esa forma de riesgo: un carril de seis columnas,
   un panel con once bloques apilados y textos de longitud desconocida.

⚠⚠⚠ LAS MEDIDAS SE LEEN DE `PaseScreen.java`, NO SE ESCRIBEN AQUÍ.

   Copiarlas sería una SEGUNDA lista de constantes que nada obliga a coincidir
   con la que dibuja el juego —el fallo de las tres listas de medallas y el de
   los dos órdenes del PokePad—. Y sería el peor caso posible: una maqueta que
   dice «cabe» midiendo unos números mientras el juego pinta otros.

   Si el fichero cambia de forma, esto ABORTA en vez de medir a medias.

Uso:
    python tools/gen_maqueta_pase.py
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(RAIZ / "tools"))

# El motor de maqueta ya existe: lienzo sobre el chasis real, anchuras de
# Minecraft y vigilancia de desbordes y solapes. Se reutiliza entero.
from gen_maqueta_mercado import (  # noqa: E402
    ALERTA, BLANCO, NARANJA, ORO, OSCURO, SUAVE, VERDE,
    Lienzo, ancho_mc, chasis,
    PANEL_X, PANEL_Y, PANEL_W, PANEL_H,
    PANT_X, PANT_Y, PANT_W, PANT_H, NAV_ALTO, SALIDA,
)

PANTALLA = (RAIZ / "mod/src/client/java/net/pokereport/luna/client/pokepad"
            / "PaseScreen.java")

VIOLETA = (185, 140, 255, 255)
HUNDIDO = (13, 18, 27, 255)
GRIS = (74, 84, 104, 255)


def constantes() -> dict:
    """
    Las `private static final int` de la pantalla, leídas del fuente.

    ⚠ Solo las literales. `COLS` es una expresión y se recalcula abajo con la
      MISMA fórmula, que es lo que hay que comprobar.
    """
    if not PANTALLA.exists():
        raise SystemExit(f"No encuentro {PANTALLA}")
    texto = PANTALLA.read_text(encoding="utf-8")
    vals = dict(re.findall(
        r"private static final int ([A-Z_]+)\s*=\s*(-?\d+);", texto))
    vals = {k: int(v) for k, v in vals.items()}
    # Las que son sumas de otras constantes.
    for nombre, expr in re.findall(
            r"private static final int ([A-Z_]+)\s*=\s*([A-Z_+\-*/ 0-9]+);", texto):
        if nombre in vals:
            continue
        try:
            vals[nombre] = int(eval(expr, {"__builtins__": {}}, vals))
        except Exception:
            pass
    faltan = [k for k in ("MARGEN", "GAP", "CARD_W", "CARD_H", "TRACK_Y",
                          "RAIL_H", "RAIL_Y", "LUNA_Y", "CAJA_LUNA_H",
                          "BOTON_LUNA_DY", "BOTON_LUNA_H", "BOTON_RECLAMAR_H")
              if k not in vals]
    if faltan:
        raise SystemExit(
            "PaseScreen.java ha cambiado de forma: no encuentro "
            + ", ".join(faltan) + ". Medir a medias es peor que no medir.")
    return vals


def y_caja_luna(C: dict) -> int:
    """
    ⚠ Copia deliberada de `PaseScreen.yCajaLuna()`, y es la ÚNICA de este
      fichero. No se puede leer del Java porque es una cadena de sumas dentro
      de un método; lo que sí se puede es comprobar que el resultado cabe, que
      es de lo que trata esta maqueta.
    """
    anillo_y = PANEL_Y + NAV_ALTO + 118
    y = anillo_y + 56 + 16     # radio del anillo + hueco
    y += 22                    # la línea de XP
    y += 12                    # separador
    y += 18 + 20 + 20          # rótulo, barra y pie del tope diario
    y += 12                    # separador
    return y


def panel(L: Lienzo, C: dict) -> None:
    cx = PANEL_X + PANEL_W // 2
    L.texto("panel.titulo", "PASE DE BATALLA", cx, PANEL_Y + NAV_ALTO + 6, 24,
            ORO, "centro", limite=PANEL_W - 40)
    L.texto("panel.temporada", "TEMPORADA 12", cx, PANEL_Y + NAV_ALTO + 32, 15,
            SUAVE, "centro")

    anillo_y = PANEL_Y + NAV_ALTO + 118
    r = 56
    L.caja("panel.anillo", cx - r, anillo_y - r, r * 2, r * 2, HUNDIDO, ORO, 7)
    L.texto("panel.nivel", "50", cx, anillo_y - 22, 42, BLANCO, "centro")
    L.texto("panel.rotulo", "NIVEL", cx, anillo_y + 20, 13, SUAVE, "centro")

    y = anillo_y + r + 16
    L.texto("panel.xp", "1.280 / 1.280 XP", cx, y, 15, BLANCO, "centro",
            limite=PANEL_W - 60)
    y += 22 + 12
    L.texto("panel.hoy", "XP DE HOY", PANEL_X + 26, y, 14, SUAVE)
    L.texto("panel.hoy2", "2.700 / 2.700", PANEL_X + PANEL_W - 26, y, 14,
            NARANJA, "der")
    y += 18
    L.caja("panel.barra", PANEL_X + 26, y, PANEL_W - 52, 12, HUNDIDO, GRIS, 1)
    y += 20
    # ⚠ La más larga de las tres, que es la que hay que medir.
    L.texto("panel.pie", "Descanso acumulado: tope x3", cx, y, 12, VIOLETA,
            "centro", limite=PANEL_W - 44)

    y = y_caja_luna(C)
    L.caja("panel.luna", PANEL_X + 22, y, PANEL_W - 44, C["CAJA_LUNA_H"],
           HUNDIDO, VIOLETA, 2)
    L.texto("panel.luna.tit", "DESBLOQUEA LA VIA LUNA", cx, y + 7, 13, BLANCO,
            "centro", limite=PANEL_W - 60)
    L.caja("panel.luna.btn", PANEL_X + 26, y + C["BOTON_LUNA_DY"],
           PANEL_W - 52, C["BOTON_LUNA_H"], None, VIOLETA, 2)
    L.texto("panel.luna.precio", "15.000", cx + 12, y + C["BOTON_LUNA_DY"] + 8,
            17, ORO, "centro")
    L.texto("panel.luna.saldo", "Tienes 148.500 LunaCoins", cx, y + 62, 11,
            SUAVE, "centro", limite=PANEL_W - 60)

    y += C["CAJA_LUNA_H"] + 12
    L.caja("panel.reclamar", PANEL_X + 22, y, PANEL_W - 44,
           C["BOTON_RECLAMAR_H"], None, VERDE, 2)
    L.texto("panel.reclamar.t", "RECLAMAR TODO (28)", PANEL_X + PANEL_W // 2,
            y + 14, 17, BLANCO, "centro", limite=PANEL_W - 60)
    y += C["BOTON_RECLAMAR_H"] + 10
    L.texto("panel.dias", "Temporada terminada - reclama lo tuyo",
            PANEL_X + PANEL_W // 2, y, 12, NARANJA, "centro",
            limite=PANEL_W - 44)

    y += 16 + 12
    L.texto("panel.como", "COMO SUBE EL PASE", PANEL_X + PANEL_W // 2, y, 13,
            ORO, "centro")
    y += 18
    # ⚠ Las MISMAS filas que dibuja la pantalla, y con las cifras más largas
    #   posibles: si «Especie nueva en la Pokedex» + «+120» no caben en 263 px,
    #   se pisan y nadie lo ve hasta que abre el Pad.
    filas = [
        ("Capturar un Pokemon", "+12"),
        ("Especie nueva en la Pokedex", "+120"),
        ("Eclosionar un huevo", "+40"),
        ("Pescar", "+8"),
        ("Picar una mena", "+4"),
        ("Cosechar", "+3"),
        ("Ganar un combate", "+6"),
        ("Ronda de la Torre", "+12..120"),
        ("Completar una mision", "+50"),
        ("Ganar una medalla", "+300"),
    ]
    for i, (izq, der) in enumerate(filas):
        ancho_der = ancho_mc(der, 11)
        # El límite del texto de la izquierda es lo que queda hasta el de la
        # derecha: es «se pisan», no «se sale del panel».
        L.texto(f"panel.fila{i}.a", izq, PANEL_X + 26, y, 11, BLANCO,
                limite=PANEL_W - 52 - ancho_der - 8)
        L.texto(f"panel.fila{i}.b", der, PANEL_X + PANEL_W - 26, y, 11, ORO,
                "der")
        y += 12


def carril(L: Lienzo, C: dict) -> None:
    cols = max(1, (PANT_W - 2 * C["MARGEN"] + C["GAP"]) // (C["CARD_W"] + C["GAP"]))
    print(f"  columnas calculadas: {cols}  "
          f"(ocupan {cols * C['CARD_W'] + (cols - 1) * C['GAP']} "
          f"de {PANT_W - 2 * C['MARGEN']} disponibles)")

    # La leyenda: pastilla de color + nombre, las dos en la misma línea. Se
    # mide el caso PEOR, que es la vía Luna bloqueada (el texto más largo).
    lx = PANT_X + C["MARGEN"]
    L.caja("carril.chip1", lx, PANT_Y + 18, 12, 12, ORO, None, vigilar=False)
    L.texto("carril.via1", "VIA LIBRE", lx + 18, PANT_Y + 18, 15, ORO)
    lx2 = lx + 24 + ancho_mc("VIA LIBRE", 15)
    L.caja("carril.chip2", lx2, PANT_Y + 18, 12, 12, VIOLETA, None, vigilar=False)
    L.texto("carril.via2", "VIA LUNA (bloqueada)", lx2 + 18, PANT_Y + 18, 15,
            VIOLETA)
    L.texto("carril.cuenta", "Niveles 45-50  de  50",
            PANT_X + PANT_W - C["MARGEN"] - 110, PANT_Y + 22, 13, SUAVE, "der")
    L.caja("carril.izq", PANT_X + PANT_W - C["MARGEN"] - 96, PANT_Y + 14, 40, 30,
           HUNDIDO, SUAVE, 1)
    L.caja("carril.der", PANT_X + PANT_W - C["MARGEN"] - 42, PANT_Y + 14, 40, 30,
           HUNDIDO, SUAVE, 1)

    # ⚠ El nombre más largo que puede salir en una tarjeta. Los de Minecraft
    #   son largos --«Escaleras de ladrillos de piedra» mide 364 px-- y eso ya
    #   mordió al escaparate el 25-ago. Aquí se parte en DOS líneas de 106, así
    #   que lo que hay que comprobar es que una PALABRA suelta quepa.
    largos = ["Superpocion", "Caramelo Raro", "Restos", "Sombrero Ampharos shiny",
              "Llave de Gachapon", "2.500 Plata", "Poke Ball"]

    for i in range(cols):
        x = PANT_X + C["MARGEN"] + i * (C["CARD_W"] + C["GAP"])
        for fila, ytop in (("libre", C["TRACK_Y"]), ("luna", C["LUNA_Y"])):
            L.caja(f"tarjeta{i}.{fila}", x, ytop, C["CARD_W"], C["CARD_H"],
                   None, ORO if fila == "libre" else VIOLETA, 2)
            hueco = 56
            L.caja(f"tarjeta{i}.{fila}.hueco", x + (C["CARD_W"] - hueco) // 2,
                   ytop + 14, hueco, hueco, HUNDIDO, GRIS, 1)
            # El nombre, partido en dos líneas como en el juego.
            nombre = largos[i % len(largos)]
            partes = partir(nombre, C["CARD_W"] - 14, 12, 2)
            ty = ytop + hueco + 22
            for j, linea in enumerate(partes):
                L.texto(f"tarjeta{i}.{fila}.txt{j}", linea,
                        x + C["CARD_W"] // 2, ty, 12, BLANCO, "centro",
                        limite=C["CARD_W"] - 14)
                ty += 14
            pie_h = 24
            pie_y = ytop + C["CARD_H"] - pie_h - 8
            L.caja(f"tarjeta{i}.{fila}.pie", x + 8, pie_y, C["CARD_W"] - 16,
                   pie_h, None, VERDE, 1)
            L.texto(f"tarjeta{i}.{fila}.pie.t", "RECLAMAR", x + C["CARD_W"] // 2,
                    pie_y + 6, 13, BLANCO, "centro", limite=C["CARD_W"] - 24)
        # El nodo del carril, entre las dos filas.
        cxn = x + C["CARD_W"] // 2
        cyn = C["RAIL_Y"] + C["RAIL_H"] // 2
        L.caja(f"nodo{i}", cxn - 17, cyn - 17, 34, 34, ORO, BLANCO, 3)
        L.texto(f"nodo{i}.t", "50", cxn, cyn - 7, 15, OSCURO, "centro")


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


def main() -> None:
    C = constantes()
    print("MAQUETA DEL PASE DE BATALLA")
    print(f"  medidas leidas de {PANTALLA.relative_to(RAIZ)}")

    L = Lienzo()
    chasis(L, "PASE")
    panel(L, C)
    carril(L, C)

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
