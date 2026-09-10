# -*- coding: utf-8 -*-
"""
LA ESPADA DE PIKACHU: mirarla, comprobarla y exportarla.

    python tools/gen_espada.py --ver         solo dibuja, no escribe nada
    python tools/gen_espada.py --generar     .bbmodel + textura + laminas
    python tools/gen_espada.py --verificar   relee el .bbmodel ya exportado

⚠ `--ver` NO toca ningun fichero de salida a proposito: mirar tiene que ser
  barato, o se deja de mirar.
"""

from __future__ import annotations

import argparse
import base64
import io
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.stdout.reconfigure(encoding="utf-8", errors="replace")

from espadas import bbmodel, diseno, referencia, textura, visor  # noqa: E402

RAIZ = Path(__file__).resolve().parent.parent
SALIDA = RAIZ / "arte" / "espadas"
BUILD = RAIZ / "build" / "espada"
LADO = 64
NOMBRE_TEX = "pikachu_electric_sword.png"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ver", action="store_true", help="solo las laminas")
    ap.add_argument("--generar", action="store_true", help="escribe el .bbmodel")
    ap.add_argument("--verificar", action="store_true",
                    help="relee el .bbmodel exportado y lo cruza con el diseño")
    args = ap.parse_args()
    if not (args.ver or args.generar or args.verificar):
        args.ver = True

    piezas = diseno.piezas()
    im, cubos = textura.pintar(piezas, lado=LADO)
    print("  COLMILLO DE TRUENO · %d piezas · rejilla %.2f · textura %dx%d"
          % (len(piezas), diseno.REJILLA, LADO, LADO))

    # ⚠⚠⚠ LA REFERENCIA SE COMPRUEBA ANTES QUE LA ESPADA, y ese orden es la
    #    decision: de ella salen la ESCALA (26 sobre una coronilla de 32) y el
    #    maniqui contra el que se juzga. Si el fichero dejara de decir lo que
    #    `diseno.py` afirma, la espada seguiria pasando sus cinco pruebas y
    #    estaria dimensionada contra un personaje que ya no existe.
    fallos = referencia.comprobar()
    f2, avisos = bbmodel.comprobar(piezas, cubos, LADO)
    fallos += f2
    for a in avisos:
        print("     " + a)
    if fallos:
        print()
        print("  %d FALLO(S):" % len(fallos))
        for f in fallos:
            print("     x " + f)
    else:
        print("     comprobaciones: rejilla, simetria, nada flotando, "
              "textura sin solapes y tramos sin hueco -- TODO EN VERDE")

    BUILD.mkdir(parents=True, exist_ok=True)
    visor.lamina(cubos, BUILD / "espada.png", textura=im)
    visor.junto_a_pikachu(cubos, BUILD / "espada-junto-a-pikachu.png", textura=im)
    print("     laminas -> %s" % BUILD)

    if fallos:
        print()
        print("  NO se exporta con fallos.")
        return 1

    if args.generar:
        buf = io.BytesIO()
        im.save(buf, format="PNG")
        b64 = base64.b64encode(buf.getvalue()).decode("ascii")
        datos = bbmodel.construir(piezas, cubos, b64, NOMBRE_TEX, LADO)
        bbmodel.escribir(datos, SALIDA / "pikachu_electric_sword.bbmodel")
        im.save(SALIDA / NOMBRE_TEX)
        print("     -> %s" % (SALIDA / "pikachu_electric_sword.bbmodel"))
        print("     -> %s" % (SALIDA / NOMBRE_TEX))

    if args.generar or args.verificar:
        # ⚠⚠⚠ SE VERIFICA LO QUE SE ACABA DE ESCRIBIR, y por eso va DESPUES de
        #    escribir y no en vez de: las cinco comprobaciones de arriba miran
        #    el DISEÑO, y lo que puede salir mal aqui es la SERIALIZACION --
        #    un uuid repetido, un cubo que no cuelga de ningun grupo, una UV
        #    fuera de la textura. Nada de eso da error: da un fichero que abre
        #    y no es el que se prometio.
        v = bbmodel.verificar(SALIDA / "pikachu_electric_sword.bbmodel",
                              piezas, cubos, LADO, referencia.RUTA)
        if v:
            print()
            print("  %d FALLO(S) EN EL FICHERO EXPORTADO:" % len(v))
            for f in v:
                print("     x " + f)
            return 1
        print("     vuelta por el fichero: formato, 45 piezas, arbol, UV y "
              "textura incrustada -- TODO EN VERDE")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
