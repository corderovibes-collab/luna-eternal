#!/usr/bin/env python3
"""
Dibuja (o borra) el PLANO de la ciudadela sobre el terreno.

QUE ES ESTO Y QUE NO ES

No construye la ciudadela: **la replantea**. Deja en el suelo un solar de
192x192 dividido en nueve parcelas con sus avenidas, cada una marcada con su
color y su cartel flotante, para poder empezar a construir sabiendo que va
donde — igual que un arquitecto marca el terreno con cal antes de levantar
nada.

Todo lo que pone es **provisional y se quita de una orden** (`--limpiar`). Los
carteles llevan la etiqueta `luna_plano`, y el suelo son bloques que se
sustituyen construyendo encima.

POR QUE UN SCRIPT Y NO COMANDOS A MANO

Son ~60 comandos con coordenadas calculadas. A mano, un numero mal puesto deja
una avenida de 7 bloques que nadie nota hasta que la calle no cuadra con el
edificio. Aqui la geometria se calcula una vez y se comprueba sola.

    parcela   56x56      lo que ocupa un edificio con su patio
    avenida    8 de ancho
    margen     4 al borde
    total     3*56 + 2*8 + 2*4 = 192

Uso:
    python tools/ciudadela.py --plano            dibuja el replanteo entero
    python tools/ciudadela.py --solo-centro      deja SOLO la plaza, lo demas al vacio
    python tools/ciudadela.py --solo-centro --tam 80    ...y mas grande
    python tools/ciudadela.py --limpiar          lo quita todo, deja el solar liso
    python tools/ciudadela.py --preparar         solo ajustes (borde, gamerules)
"""
import argparse
import os
import sys
import time
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(RAIZ / "tools"))

DIM = "lunaeternal:ciudadela"
Y = 63                      # altura del suelo; se camina en 64

PARCELA, AVENIDA, MARGEN = 56, 8, 4
MITAD = (3 * PARCELA + 2 * AVENIDA + 2 * MARGEN) // 2      # 96

# Tramos en un eje: (inicio, fin) inclusive. Se calculan, no se escriben.
_o = -MITAD + MARGEN
TRAMOS = [(_o, _o + PARCELA - 1)]
_o += PARCELA + AVENIDA
TRAMOS.append((_o, _o + PARCELA - 1))
_o += PARCELA + AVENIDA
TRAMOS.append((_o, _o + PARCELA - 1))

# (columna, fila) -> (nombre, color de borde, color del cartel)
# columna 0 = oeste (-X) · fila 0 = norte (-Z)
ZONAS = {
    (0, 0): ("Salon de Medallas", "yellow_concrete", "yellow"),
    (1, 0): ("Laboratorio", "white_concrete", "white"),
    (2, 0): ("Gremio", "orange_concrete", "gold"),
    (0, 1): ("Centro Pokemon", "red_concrete", "red"),
    (1, 1): ("PLAZA CENTRAL", None, "gold"),
    (2, 1): ("Mercado", "lime_concrete", "green"),
    (0, 2): ("Sastreria", "magenta_concrete", "light_purple"),
    (1, 2): ("Puerta al Mundo", "light_blue_concrete", "aqua"),
    (2, 2): ("Reservado", "gray_concrete", "gray"),
}

SUELO_PARCELA = "minecraft:smooth_stone"
SUELO_PLAZA = "minecraft:smooth_quartz"
SUELO_AVENIDA = "minecraft:polished_andesite"
SUELO_MARGEN = "minecraft:stone_bricks"

# Limite de /fill en vanilla. Superarlo no da error util: dice "Too many blocks".
MAX_FILL = 32768


def franjas(x1, z1, x2, z2):
    """Parte un rectangulo en trozos que /fill acepte.

    Un 192x192 son 36 864 bloques y /fill corta en 32 768. El fallo, si se
    ignora, es que la mitad del solar simplemente no aparece."""
    ancho = x2 - x1 + 1
    filas_max = max(1, MAX_FILL // ancho)
    z = z1
    while z <= z2:
        hasta = min(z + filas_max - 1, z2)
        yield (x1, z, x2, hasta)
        z = hasta + 1


def llenar(cmds, x1, z1, x2, z2, bloque, y=Y):
    for (a, b, c, d) in franjas(x1, z1, x2, z2):
        cmds.append(f"execute in {DIM} run fill {a} {y} {b} {c} {y} {d} {bloque}")


def marco(cmds, x1, z1, x2, z2, bloque, y=Y):
    """Solo el borde, un bloque de grosor."""
    llenar(cmds, x1, z1, x2, z1, bloque, y)
    llenar(cmds, x1, z2, x2, z2, bloque, y)
    llenar(cmds, x1, z1, x1, z2, bloque, y)
    llenar(cmds, x2, z1, x2, z2, bloque, y)


def cartel(cmds, x, z, texto, color, escala=3):
    """Cartel flotante. Lleva Tag para poder quitarlos todos de una."""
    nbt = ('{text:\'{"text":"' + texto + '","color":"' + color + '","bold":true}\','
           'billboard:"center",alignment:"center",background:0,see_through:true,'
           'Tags:["luna_plano"],'
           'transformation:{scale:[' + f'{escala}f,{escala}f,{escala}f' + '],'
           'left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],'
           'translation:[0f,0f,0f]}}')
    cmds.append(f"execute in {DIM} run summon minecraft:text_display "
                f"{x} {Y + 8} {z} {nbt}")


def comandos_plano():
    cmds = []

    # 1. Solar entero. Primero todo de avenida, y encima las parcelas: asi no
    #    hay que calcular las calles una por una.
    lo, hi = -MITAD, MITAD - 1
    llenar(cmds, lo, hi_z := lo, hi, MITAD - 1, SUELO_AVENIDA)

    # 2. Margen exterior, para que se vea donde acaba el solar.
    marco(cmds, lo, lo, hi, hi, SUELO_MARGEN)
    for d in range(1, MARGEN):
        marco(cmds, lo + d, lo + d, hi - d, hi - d, SUELO_MARGEN)

    # 3. Las nueve parcelas.
    for (col, fila), (nombre, borde, color) in ZONAS.items():
        x1, x2 = TRAMOS[col]
        z1, z2 = TRAMOS[fila]
        suelo = SUELO_PLAZA if borde is None else SUELO_PARCELA
        llenar(cmds, x1, z1, x2, z2, suelo)
        if borde:
            marco(cmds, x1, z1, x2, z2, f"minecraft:{borde}")
        cartel(cmds, (x1 + x2) // 2, (z1 + z2) // 2, nombre.upper(), color,
               escala=4 if borde is None else 3)

    # 4. Rosa de los vientos en la plaza: sin esto nadie sabe donde cae el
    #    norte, y las descripciones de las zonas dejan de significar nada.
    for texto, dx, dz in (("N", 0, -22), ("S", 0, 22), ("E", 22, 0), ("O", -22, 0)):
        cartel(cmds, dx, dz, texto, "gray", escala=2)

    return cmds


def comandos_preparar():
    """Ajustes del sitio. Van aparte porque no se borran con el plano."""
    return [
        # SIN BORDE mientras se construye (decision del usuario, 12-ago).
        # Un borde a 208 se veia desde la plaza como una pared de niebla y
        # marcaba un limite que todavia no esta decidido. Se pondra cuando la
        # ciudadela este acabada y se sepa cuanto ocupa de verdad.
        f"execute in {DIM} run worldborder center 0 0",
        f"execute in {DIM} run worldborder set 59999968",
        # Condiciones de obra. La dimension ya tiene mediodia fijo y cero
        # monstruos por su dimension_type; esto es lo que ese fichero no cubre.
        "gamerule doWeatherCycle false",
        "gamerule doFireTick false",
        "gamerule mobGriefing false",
        # Sin esto, la hierba crece y el hielo se derrite encima de lo que
        # estas construyendo mientras lo construyes.
        "gamerule randomTickSpeed 0",
        "gamerule keepInventory true",
        "gamerule doMobSpawning false",
        "gamerule announceAdvancements false",
    ]


def comandos_solo_centro(tam):
    """Deja una sola isla cuadrada en el centro y vacia todo lo demas.

    Es para empezar por una zona sin que el resto del replanteo distraiga. Se
    borra hasta el aire porque una isla flotante sobre el vacio se lee mucho
    mejor que un cuadrado en medio de un descampado gris: se ve exactamente
    donde acaba lo que estas construyendo.

    El tamano por defecto es el de la parcela real (56), para que lo que se
    construya encaje cuando vuelvan las demas. Si te quedas corto, `--tam` lo
    agranda: la cuadricula se puede volver a dibujar despues alrededor.
    """
    mitad = tam // 2
    lo, hi = -MITAD, MITAD - 1
    cmds = [f"execute in {DIM} run kill @e[type=minecraft:text_display,tag=luna_plano]"]

    # Se limpian VARIAS alturas, no solo el suelo: si alguien ya habia puesto
    # algo encima, dejarlo flotando sobre el vacio seria peor que quitarlo.
    for y in range(Y - 1, Y + 6):
        llenar(cmds, lo, lo, hi, hi, "minecraft:air", y)

    llenar(cmds, -mitad, -mitad, mitad - 1, mitad - 1, SUELO_PLAZA)
    return cmds


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--plano", action="store_true", help="dibuja el replanteo")
    g.add_argument("--solo-centro", action="store_true",
                   help="deja SOLO la plaza; lo demas, vacio")
    g.add_argument("--limpiar", action="store_true", help="quita el plano")
    g.add_argument("--preparar", action="store_true", help="solo ajustes del sitio")
    ap.add_argument("--tam", type=int, default=PARCELA,
                    help=f"lado del cuadrado central (por defecto {PARCELA})")
    args = ap.parse_args()

    import ptero
    import rcon

    if args.solo_centro:
        cmds = [f"execute in {DIM} run forceload add {-MITAD} {-MITAD} {MITAD-1} {MITAD-1}"]
        cmds += comandos_solo_centro(args.tam)
        cmds.append(f"execute in {DIM} run forceload remove all")
        print(f"dejando solo el centro ({args.tam}x{args.tam}): {len(cmds)} comandos")
    elif args.limpiar:
        cmds = [f"execute in {DIM} run kill @e[type=minecraft:text_display,tag=luna_plano]"]
        lo, hi = -MITAD, MITAD - 1
        llenar(cmds, lo, lo, hi, hi, SUELO_PARCELA)
        print(f"limpiando: {len(cmds)} comandos")
    elif args.preparar:
        cmds = comandos_preparar()
        print(f"preparando el sitio: {len(cmds)} comandos")
    else:
        cmds = [f"execute in {DIM} run forceload add {-MITAD} {-MITAD} {MITAD-1} {MITAD-1}",
                f"execute in {DIM} run kill @e[type=minecraft:text_display,tag=luna_plano]"]
        cmds += comandos_plano()
        cmds += comandos_preparar()
        cmds.append(f"execute in {DIM} run setworldspawn 0 {Y+1} 0")
        print(f"dibujando el plano: {len(cmds)} comandos")

    fallos = []
    for i, c in enumerate(cmds, 1):
        salida = rcon.enviar(c, espera=0.35)
        for l in salida:
            if any(m in l for m in ("Too many blocks", "Unknown", "Expected",
                                    "not loaded", "Incorrect", "Invalid")):
                fallos.append((c, l.strip()))
        if i % 15 == 0:
            print(f"  {i}/{len(cmds)}")

    print(f"\n{len(cmds)} comandos enviados.")
    if fallos:
        print(f"{len(fallos)} FALLARON:")
        for c, l in fallos[:8]:
            print(f"  {c[:90]}\n    -> {l[:110]}")
    else:
        print("Sin errores.")


if __name__ == "__main__":
    main()
