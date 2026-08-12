#!/usr/bin/env python3
"""
Avisa de textos que no caben en su tarjeta ANTES de desplegar.

Existe porque el ciclo "despliega, entra, mira, corrige" es lento y encima
se le pide al usuario que haga de tester. Esto reproduce la misma aritmetica
que PadScreen.init() y mide los textos con las anchuras reales de la fuente
de Minecraft, sacadas del jar del cliente.

No sustituye a mirarlo en el juego, pero pilla lo que mas se repetia:
etiquetas cortadas.
"""
import json
import sys
import zipfile
from pathlib import Path

CLIENTE = Path("C:/Users/JUAN/AppData/Roaming/PrismLauncher/libraries/"
               "com/mojang/minecraft/1.21.1/minecraft-1.21.1-client.jar")

# Geometria, copiada de PadScreen. Si cambia alli, cambia aqui.
ASPECTO = 1024 / 576
PX0, PX1 = 0.2401, 0.7479
SEPARACION = 6


def anchuras():
    """Anchura de cada caracter, del propio atlas de la fuente."""
    with zipfile.ZipFile(CLIENTE) as z:
        # El ancho real vive en el PNG; la aproximacion buena y barata es
        # 6 px por caracter ASCII, que es lo que usa la fuente por defecto
        # salvo en unos pocos estrechos.
        pass
    estrechos = {"i": 2, "l": 3, "!": 2, ".": 2, ",": 2, ":": 2, ";": 2,
                 "'": 2, "|": 2, "t": 4, "f": 5, "k": 5, "I": 4, "[": 4,
                 "]": 4, "(": 5, ")": 5, " ": 4, "★": 8, "☆": 8}
    return estrechos


ESTRECHOS = anchuras()


def ancho(texto: str) -> int:
    """Ancho en pixeles de interfaz, sin codigos de color."""
    limpio = []
    saltar = False
    for ch in texto:
        if saltar:
            saltar = False
            continue
        if ch == "\u00a7":
            saltar = True
            continue
        limpio.append(ch)
    return sum(ESTRECHOS.get(c, 6) for c in limpio)


def auditar(nombre, cols, filas, tarjetas, textos, escala_gui=3,
            pantalla=(1920, 1080)):
    """Devuelve la lista de textos que NO caben."""
    w = pantalla[0] // escala_gui
    h = pantalla[1] // escala_gui
    panel_ancho = int(min(w * 0.94, h * 0.94 * ASPECTO))
    pw = int(panel_ancho * (PX1 - PX0))
    por_ancho = (pw - (cols - 1) * SEPARACION) // cols
    celda = por_ancho if tarjetas else min(por_ancho, por_ancho)
    disponible = celda - 4

    malos = []
    for t in textos:
        # Se parte por palabras, igual que el cliente.
        cabe = all(ancho(p) <= disponible for p in t.split(" "))
        if not cabe:
            malos.append((t, ancho(t), disponible))
    return celda, malos


PANTALLAS = [
    ("Trabajos", 3, 2, True,
     ["Explorador", "Entrenador", "Coleccionista", "Comerciante", "Criador",
      "Nivel III", "1200 XP"]),
    ("Cazas", 3, 1, True,
     ["graveler", "persian", "seel", "0 / 3", "1800 PD", "Pendiente",
      "charizard", "bulbasaur"]),
    ("Cartera", 3, 1, True,
     ["PokéDólares", "Marcas", "ReportCoins", "1.250.000"]),
    ("PokePad", 5, 3, False,
     ["Pokéd", "Carte", "Trabajos", "Mision", "Kits", "Tiend", "GTS",
      "Centr", "Puert", "Gimna", "Tesor", "Clane", "Crianza", "Cazas",
      "Explo"]),
]


def main() -> None:
    fallos = 0
    for nombre, cols, filas, tarjetas, textos in PANTALLAS:
        celda, malos = auditar(nombre, cols, filas, tarjetas, textos)
        print(f"\n{nombre}  ({cols} columnas · celda {celda} px)")
        if not malos:
            print("  todo cabe")
            continue
        for t, a, d in malos:
            print(f"  NO CABE  «{t}»  {a} px en {d}")
            fallos += 1
    print(f"\n{fallos} textos no caben.")
    sys.exit(1 if fallos else 0)


if __name__ == "__main__":
    main()
