# -*- coding: utf-8 -*-
"""
LOS TRAJES DE RANGO.

    python tools/gen_trajes.py --ver novato     dibuja la lamina y no toca nada
    python tools/gen_trajes.py --generar        escribe geo + texturas al mod
    python tools/gen_trajes.py --verificar      comprueba los invariantes

⚠⚠⚠ LOS COLORES NO SE ELIGEN: SON LOS DEL RANGO EN EL CHAT.
   NOVATO §f blanco · ELITE §a verde · CAMPEON §b turquesa · MAESTRO §5 morado
   · LEYENDA §6 oro. Es la misma decision que ya se tomo con el neon y el
   hormigon (D-032): si los 16 colores salen de la misma tabla, PEGAN POR
   CONSTRUCCION en vez de porque alguien los emparejara a ojo. Aqui ademas
   significa que quien vea el traje de lejos ya sabe el rango sin leer nada.

⚠⚠ CERO PROTECCION, Y ES UNA DECISION. El traje de Diosesmon da 3/8/6/3 -- que
   es EXACTAMENTE diamante, medido en su `ModArmorMaterials`. O sea que su traje
   de pago protege. Aqui no: D-007 y D-014 dicen que se vende identidad, no
   poder competitivo, y una armadura que protege es poder competitivo aunque
   venga de un rango. El traje es un DISFRAZ.

⚠ SILUETA COMPARTIDA, DETALLE CRECIENTE. Los cinco tienen la misma base
  (hombreras + cinturon + botas) y lo que sube con el rango es lo que se AÑADE:
  NOVATO una gorra; LEYENDA corona, capa y que brille de noche. Si cada rango
  fuera un diseño distinto, no se leerian como una familia y subir de rango no
  se notaria.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(RAIZ / "tools"))

from trajes.modelo import Cubo, Traje, escribir            # noqa: E402
from trajes import visor                                    # noqa: E402

DESTINO = RAIZ / "mod" / "src" / "client" / "resources" / "assets" / "lunaeternal"
LAMINAS = RAIZ / "build" / "trajes"


# ---------------------------------------------------------------- la paleta

# El color del rango, y dos derivados que salen POR FORMULA y no a mano.
RANGOS = {
    "novato":  ("NOVATO",  (198, 204, 214)),
    "elite":   ("ELITE",   (88, 200, 108)),
    "campeon": ("CAMPEON", (86, 200, 214)),
    "maestro": ("MAESTRO", (150, 92, 200)),
    "leyenda": ("LEYENDA", (232, 176, 56)),
}


def oscuro(c, k=0.55):
    return tuple(int(v * k) for v in c)


def claro(c, k=0.35):
    return tuple(int(v + (255 - v) * k) for v in c)


# ⚠⚠ EL TRAJE NO USA EL COLOR DEL CHAT DE NOVATO, Y ES A PROPOSITO. El rango es
#    §f -- casi blanco -- y un traje blanco sobre un cuerpo gris no se ve. La
#    regla sigue siendo «el color dice el rango», pero al mas bajo se le da el
#    ROJO DEL ENTRENADOR CLASICO, que es lo que hace que se lea «Pokemon» desde
#    lejos. Los otros cuatro si llevan el suyo, que ya son colores vivos.
ROJO = (216, 62, 54)
BLANCO = (238, 240, 246)
AZUL = (48, 104, 196)
DENIM = (68, 94, 150)
NEGRO = (46, 48, 58)
CUERO = (74, 58, 46)
BASE = (44, 50, 68)


# ---------------------------------------------------------------- NOVATO

def novato():
    """
    NOVATO: el entrenador que acaba de salir de casa.

    Gorra roja con la banda blanca, chaleco azul, mangas blancas, vaqueros y
    zapatillas. Es LA silueta del entrenador de Pokemon, y esta elegida por eso:
    a un novato hay que reconocerlo sin leer nada.

    ⚠ BASICO A PROPOSITO. Ni metal, ni capa, ni hombreras: eso es lo que va
      apareciendo al subir de rango. Si el primero ya llevara placas, ELITE no
      tendria por donde crecer.
    """
    t = Traje("novato", "Traje NOVATO")

    # ---- la gorra ------------------------------------------------------
    # ⚠⚠ LA CABEZA DE VANILLA VA DE y=24 A y=32. Una gorra que empiece en 30 es
    #    UNA BANDEJA: hay que bajarla hasta las orejas y hacerla mas ancha que
    #    la cabeza, o no se lee como tela puesta encima.
    t.poner("armorHead",
            Cubo((-4.5, 29.2, -4.5), (9.0, 3.4, 9.0), ROJO, "tela"),
            # la media banda blanca del frente, que es lo que la hace «esa» gorra
            Cubo((-4.6, 29.3, -4.9), (9.2, 2.4, 0.9), BLANCO, "tela"),
            # visera
            Cubo((-3.4, 29.1, -8.0), (6.8, 0.9, 3.6), ROJO, "cuero"),
            # ⚠ la Poke Ball son DOS cubos, uno encima del otro: a este tamaño
            #   una bola redonda no existe, y dos mitades si se leen
            Cubo((-0.9, 30.7, -5.1), (1.8, 0.9, 0.4), ROJO, "tela"),
            Cubo((-0.9, 29.8, -5.1), (1.8, 0.9, 0.4), BLANCO, "tela"))

    # ---- el chaleco ----------------------------------------------------
    t.poner("armorBody",
            Cubo((-4.4, 13.0, -2.6), (8.8, 10.4, 5.2), AZUL, "tela"),
            # el cierre y los bolsillos, en blanco: sin ellos el chaleco es una
            # caja azul
            Cubo((-0.5, 13.2, -3.0), (1.0, 10.0, 0.5), BLANCO, "tela"),
            Cubo((-3.6, 14.6, -3.0), (2.4, 2.0, 0.5), BLANCO, "tela"),
            Cubo((1.2, 14.6, -3.0), (2.4, 2.0, 0.5), BLANCO, "tela"),
            # cuello
            Cubo((-4.6, 22.8, -2.8), (9.2, 1.5, 5.6), BLANCO, "tela"),
            # ⚠ el cinturon BAJA HASTA 11,8: el torso acaba en 12 y sin eso
            #   asomaba media unidad de piel en toda la cintura
            Cubo((-4.6, 11.8, -2.8), (9.2, 2.3, 5.6), NEGRO, "cuero"),
            Cubo((-1.1, 12.2, -3.1), (2.2, 1.8, 0.5), (206, 176, 92), "metal"))

    # ⚠ Los dos brazos son ESPEJO, no copia: el derecho vive en x negativa.
    # ⚠⚠ Y la manga llega a 24,2 porque el brazo de vanilla llega a 24: si
    #    acabara antes queda un anillo de piel en cada hombro, y el cuello NO lo
    #    tapa -- solo mide 9,2 de ancho y los brazos viven mas afuera.
    for lado in ("Right", "Left"):
        x = -8.0 if lado == "Right" else 4.0
        t.poner("armor%sArm" % lado,
                Cubo((-0.25 + x, 12.8, -2.25), (4.5, 11.4, 4.5), BLANCO, "tela"),
                Cubo((-0.3 + x, 12.9, -2.3), (4.6, 1.0, 4.6), AZUL, "tela"),
                Cubo((-0.4 + x, 11.3, -2.4), (4.8, 2.0, 4.8), NEGRO, "cuero"))

    # ---- vaqueros ------------------------------------------------------
    for lado, x in (("Right", -3.9), ("Left", -0.1)):
        t.poner("armor%sLeg" % lado,
                Cubo((x - 0.25, 2.8, -2.25), (4.5, 9.6, 4.5), DENIM, "tela"),
                Cubo((x - 0.3, 11.4, -2.3), (4.6, 1.0, 4.6), (54, 74, 120), "tela"))

    # ---- zapatillas ----------------------------------------------------
    for lado, x in (("Right", -3.9), ("Left", -0.1)):
        t.poner("armor%sBoot" % lado,
                Cubo((x - 0.4, 0.5, -2.4), (4.8, 2.9, 4.8), BLANCO, "tela"),
                # ⚠ la puntera va ESTRECHA: dos punteras anchas juntas se leen
                #   como UNA plataforma en vez de como dos zapatos
                Cubo((x + 0.4, 0.7, -2.9), (3.2, 1.2, 0.7), ROJO, "tela"),
                Cubo((x - 0.5, -0.1, -2.5), (5.0, 0.9, 5.4), NEGRO, "cuero"))
    return t


TRAJES = {"novato": novato}


# ---------------------------------------------------------------- comprobar

def verificar(t):
    """
    Los invariantes de un traje.

    ⚠ Ninguno da error al generar: los cuatro producen un traje que se compila,
      se despliega y se ve MAL. Que es justo la familia de fallos que este
      proyecto ya conoce.
    """
    from trajes import modelo
    fallos = []

    for hueso in t.huesos:
        if hueso not in modelo.PADRES:
            fallos.append("hueso desconocido: %s (GeckoLib no lo pega a nada "
                          "y el traje sale flotando)" % hueso)

    # ⚠ Cada pieza se pinta en SU textura. Un cubo que no caiga en ninguna
    #   pieza no se dibuja nunca, sin avisar.
    de_piezas = set()
    for pieza in modelo.PIEZAS:
        de_piezas.update(t.de_pieza(pieza))
    huerfanos = set(t.huesos) - de_piezas
    if huerfanos:
        fallos.append("huesos que no caen en ninguna pieza: %s" % sorted(huerfanos))

    # ⚠⚠ EL TRAJE TIENE QUE TAPAR AL JUGADOR. Si un cubo del traje es MAS
    #    ESTRECHO que la parte del cuerpo que cubre, el jugador asoma -- y eso
    #    en el juego se ve como un brazo gris saliendo de la manga.
    cuerpo = {"armorBody": 8.0, "armorRightArm": 4.0, "armorLeftArm": 4.0,
              "armorRightLeg": 4.0, "armorLeftLeg": 4.0}
    for hueso, minimo in cuerpo.items():
        cubos = t.huesos.get(hueso, [])
        if cubos and max(c.tam[0] for c in cubos) < minimo:
            fallos.append("%s es mas estrecho que el cuerpo (%.1f < %.1f): "
                          "el jugador asomaria"
                          % (hueso, max(c.tam[0] for c in cubos), minimo))

    # ⚠ Y tiene que caber en la textura. Se comprueba pieza a pieza porque cada
    #   una es un PNG distinto.
    for pieza in modelo.PIEZAS:
        cubos = [c for l in t.de_pieza(pieza).values() for c in l]
        if not cubos:
            continue
        try:
            alto = modelo.empaquetar(list(cubos))
        except ValueError as e:
            fallos.append("pieza %s: %s" % (pieza, e))
        else:
            if alto > 128:
                fallos.append("pieza %s necesita %d px de alto" % (pieza, alto))
    return fallos


# ---------------------------------------------------------------- principal

def main():
    p = argparse.ArgumentParser(description="Los trajes de rango")
    p.add_argument("--ver", metavar="TRAJE",
                   help="dibuja la lamina de un traje y NO toca el mod")
    p.add_argument("--generar", action="store_true",
                   help="escribe geo + texturas en el mod")
    p.add_argument("--verificar", action="store_true")
    p.add_argument("--escala", type=float, default=11.0)
    args = p.parse_args()

    if not (args.ver or args.generar or args.verificar):
        p.print_help()
        return 0

    cuales = [args.ver] if args.ver else list(TRAJES)
    salida = 0
    LAMINAS.mkdir(parents=True, exist_ok=True)

    for cual in cuales:
        if cual not in TRAJES:
            print("  no existe el traje %r. Hay: %s" % (cual, ", ".join(TRAJES)))
            return 1
        t = TRAJES[cual]()
        fallos = verificar(t)
        print("\n  %s" % t.nombre)
        for f in fallos:
            print("    x %s" % f)
        if fallos:
            salida = 1
            continue
        print("    invariantes correctos")

        if args.generar:
            for pieza, n, alto, brillo in escribir(t, DESTINO):
                print("    %-6s %2d cubos · textura %d px%s"
                      % (pieza, n, alto, " · con brillo" if brillo else ""))
            print("    -> %s" % DESTINO)

        if args.ver or args.generar:
            # La lamina se dibuja con las texturas de verdad si existen, para
            # que lo que se mira sea lo que se va a ver en el juego.
            texturas = {}
            from PIL import Image
            for pieza in ("head", "body", "legs", "boots"):
                f = (DESTINO / "textures" / "armor" / t.id
                     / ("%s_%s.png" % (t.id, pieza)))
                if f.exists():
                    texturas[pieza] = Image.open(f).convert("RGBA")
            if not texturas:
                # Sin generar todavia: se empaqueta en memoria para poder mirar.
                from trajes import modelo as M
                for pieza in M.PIEZAS:
                    cubos = [c for l in t.de_pieza(pieza).values() for c in l]
                    if cubos:
                        M.empaquetar(cubos)
                        texturas[pieza] = M.pintar(
                            cubos, 128, sum(ord(ch) for ch in t.id + pieza))
            destino = LAMINAS / ("%s.png" % t.id)
            visor.lamina(t, destino, args.escala, texturas)
            print("    lamina -> %s" % destino)

    return salida


if __name__ == "__main__":
    raise SystemExit(main())
