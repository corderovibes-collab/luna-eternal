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

# ⚠⚠ YA NO ESCRIBE EN EL MOD (2026-08-28, decision del usuario). El arte de los
#    trajes se hace A MANO en Blockbench -- ver docs/ui/trajes-a-mano.md -- y
#    esto queda como BANCO DE PRUEBAS: genera a `build/` para poder mirar el
#    visor sin tocar lo que se reparte a los jugadores.
DESTINO = RAIZ / "build" / "trajes" / "generado"
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
MARINO = (30, 54, 106)
CUERO = (74, 58, 46)
BASE = (44, 50, 68)


# ---------------------------------------------------------------- NOVATO

def novato():
    """
    NOVATO: el entrenador que acaba de salir de casa.

    ⚠⚠⚠ ESTE NO SALE DE MI CABEZA: SALE DE UN BOCETO DEL USUARIO. Lo genero con
       Gemini como hoja de personaje (frente, lado y espalda, plano y sin
       sombras) y aqui se TRADUCE a cubos. Tres cosas suyas que yo no habia
       puesto y que son las que hacen que se lea como entrenador:

         MANGA CORTA Y ANTEBRAZO AL AIRE  el traje se para en el codo
         LA POKE BALL EN LA ESPALDA       grande, no un adorno en la gorra
         MUÑEQUERAS                       dos bandas oscuras en las muñecas

    ⚠⚠ QUE EL ANTEBRAZO QUEDE DESNUDO ES DELIBERADO, y por eso la comprobacion
       de «el jugador asoma» NO aplica a los brazos aqui: lo que asoma es la
       PIEL DEL JUGADOR, que es justo lo que se quiere ver. Una manga larga
       taparia la unica parte del jugador que sigue siendo suya.

    ⚠ BASICO A PROPOSITO: ni metal, ni capa, ni hombreras. Eso es lo que va
      apareciendo al subir. Si el primero ya llevara placas, ELITE no tendria
      por donde crecer.
    """
    t = Traje("novato", "Traje NOVATO")

    # ---- la gorra ------------------------------------------------------
    # ⚠⚠ LA CABEZA DE VANILLA VA DE y=24 A y=32. Una gorra que empiece en 30 es
    #    UNA BANDEJA: hay que bajarla hasta las orejas y hacerla mas ancha que
    #    la cabeza, o no se lee como tela puesta encima.
    t.poner("armorHead",
            Cubo((-4.5, 29.0, -4.5), (9.0, 3.6, 9.0), ROJO, "tela"),
            # la banda blanca del frente. En el boceto es media gorra, no un
            # parche: por eso llega de lado a lado
            Cubo((-4.6, 29.1, -4.9), (9.2, 1.9, 0.7), BLANCO, "tela"),
            # visera
            Cubo((-3.6, 28.9, -8.2), (7.2, 0.9, 3.8), ROJO, "cuero"))

    # ---- el chaleco ----------------------------------------------------
    t.poner("armorBody",
            Cubo((-4.4, 13.0, -2.6), (8.8, 10.4, 5.2), AZUL, "tela"),
            # ⚠ la abertura del frente: sin ella el chaleco es una caja azul.
            #   Va en azul OSCURO porque en el boceto lo que se ve por el hueco
            #   es la camiseta de debajo, no la piel
            Cubo((-1.3, 18.4, -3.0), (2.6, 5.0, 0.5), MARINO, "tela"),
            Cubo((-4.6, 22.6, -2.8), (9.2, 1.6, 5.6), MARINO, "tela"),
            # ⚠⚠ LA POKE BALL VA EN LA ESPALDA Y ES GRANDE. Es lo que mas
            #    identifica al traje, y de frente no se ve NADA -- por eso el
            #    boceto trae la vista de espalda: sin ella esto no se habria
            #    puesto
            #    ⚠ va CASI PLANA sobre la tela (sobresale 0,25): con medio
            #      bloque parecia una chapa pegada, y de lado se veia asomar
            #    ⚠⚠ Y VA ESCALONADA, que es como se finge un circulo con cubos:
            #       dos filas anchas y dos estrechas arriba y abajo. Con dos
            #       cubos a secas la bola se leia como DOS BARRAS, no como una
            #       bola -- se vio en el visor, no se dedujo
            Cubo((-1.5, 20.6, 2.55), (3.0, 0.9, 0.3), ROJO, "tela"),
            Cubo((-2.6, 18.7, 2.55), (5.2, 1.9, 0.3), ROJO, "tela"),
            Cubo((-2.8, 17.9, 2.55), (5.6, 0.8, 0.3), NEGRO, "tela"),
            Cubo((-2.6, 16.0, 2.55), (5.2, 1.9, 0.3), BLANCO, "tela"),
            Cubo((-1.5, 15.1, 2.55), (3.0, 0.9, 0.3), BLANCO, "tela"),
            #    ⚠ y el boton va CENTRADO EN LA BANDA, con su aro oscuro.
            #      Puesto mas abajo la tapaba entera
            Cubo((-1.1, 17.7, 2.8), (2.2, 1.2, 0.25), NEGRO, "tela"),
            Cubo((-0.7, 17.85, 2.95), (1.4, 0.9, 0.25), BLANCO, "tela"),
            # ⚠ el cinturon BAJA HASTA 11,8: el torso acaba en 12 y sin eso
            #   asomaba media unidad de piel en toda la cintura
            Cubo((-4.6, 11.8, -2.8), (9.2, 2.2, 5.6), MARINO, "cuero"))

    # ⚠ Los dos brazos son ESPEJO, no copia: el derecho vive en x negativa.
    # ⚠⚠ Y la manga llega a 24,2 porque el brazo de vanilla llega a 24: si
    #    acabara antes queda un anillo de piel en el HOMBRO, que es distinto de
    #    la piel del antebrazo -- aquella es un fallo y esta es el diseño
    for lado in ("Right", "Left"):
        x = -8.0 if lado == "Right" else 4.0
        t.poner("armor%sArm" % lado,
                Cubo((-0.25 + x, 18.6, -2.25), (4.5, 5.6, 4.5), BLANCO, "tela"),
                Cubo((-0.35 + x, 13.2, -2.35), (4.7, 1.4, 4.7), MARINO, "tela"))

    # ---- vaqueros ------------------------------------------------------
    for lado, x in (("Right", -3.9), ("Left", -0.1)):
        t.poner("armor%sLeg" % lado,
                Cubo((x - 0.25, 2.8, -2.25), (4.5, 9.6, 4.5), DENIM, "tela"),
                Cubo((x - 0.3, 11.4, -2.3), (4.6, 1.0, 4.6), MARINO, "tela"))

    # ---- zapatillas ----------------------------------------------------
    for lado, x in (("Right", -3.9), ("Left", -0.1)):
        t.poner("armor%sBoot" % lado,
                Cubo((x - 0.4, 0.9, -2.4), (4.8, 2.6, 4.8), BLANCO, "tela"),
                # la suela roja, que en el boceto es lo unico de color del pie
                Cubo((x - 0.45, 0.0, -2.45), (4.9, 1.0, 4.9), ROJO, "tela"))
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

    # ⚠⚠⚠ CADA PIEZA TIENE QUE SOSTENERSE SOLA. Un hueso cuyo padre viva en
    #    OTRA pieza da un .geo.json roto, y eso NO es un aviso: GeckoLib valida
    #    todo lo que encuentra, lanza, y la recarga de recursos falla entera --
    #    el cliente se queda colgado en la pantalla de carga. Paso el 2026-08-28
    #    con `armorRightBoot -> armorRightLeg`.
    for pieza in modelo.PIEZAS:
        dentro = set(t.de_pieza(pieza))
        for hueso in dentro:
            padre = modelo.PADRES[hueso]
            if not padre.startswith("biped") and padre not in dentro:
                fallos.append(
                    "en la pieza %s, %s cuelga de %s, que vive en otro fichero: "
                    "GeckoLib lo rechaza y TUMBA LA RECARGA DE RECURSOS"
                    % (pieza, hueso, padre))

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
