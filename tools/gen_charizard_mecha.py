# -*- coding: utf-8 -*-
"""
EL CHARIZARD MECHA DEL PASE DE BATALLA: mirarlo, comprobarlo y meterlo en el pack.

    python tools/gen_charizard_mecha.py --ver         laminas en build/mecha, no escribe en el pack
    python tools/gen_charizard_mecha.py --generar     geo + texturas + resolver + .bbmodel
    python tools/gen_charizard_mecha.py --verificar   relee lo escrito en el pack y lo cruza

Lo que hace, en orden, y por que en ese orden (el detalle en cada modulo de
tools/mecha/):

  1. fuentes     el casco del usuario (arte/), el Charizard del jar de Cobblemon
                 1.8.0 y las megas del jar de mega_showdown -- del manifiesto
                 publicado, no de Descargas
  2. ensamblar   el geo oficial TAL CUAL mas cuatro huesos con el casco, cada uno
                 colgado del hueso oficial que se mueve con esa pieza
  3. texturas    el cuerpo del jar + el casco REPINTADO al reparto del juego
                 (Blockbench redondea los tamaños del box-uv; el juego no)
  4. resolver    `luna_mecha` -> nuestro modelo, y las megas REPETIDAS para que
                 al megaevolucionar mande la mega y al volver mande el casco
  5. comprobar   se niega a exportar con un solo fallo
  6. visor       la cabeza de cerca y SEIS POSES de las animaciones reales,
                 con la boca a 85,8 grados: lo que el usuario pidio mirar

⚠ `--ver` no toca el pack a proposito: mirar tiene que ser barato.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.stdout.reconfigure(encoding="utf-8", errors="replace")

from mecha import (bbmodel, comprobar, ensamblar, fuentes, resolver,  # noqa: E402
                   texturas, visor)


def _json(destino: Path, datos):
    destino.parent.mkdir(parents=True, exist_ok=True)
    destino.write_text(json.dumps(datos, indent=2, ensure_ascii=False) + "\n",
                       encoding="utf-8")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ver", action="store_true")
    ap.add_argument("--generar", action="store_true")
    ap.add_argument("--verificar", action="store_true")
    args = ap.parse_args()
    if not (args.ver or args.generar or args.verificar):
        args.ver = True

    jar, jar_mega = fuentes.jars()
    bb = fuentes.bbmodel_usuario()
    cubos = fuentes.cubos_del_casco(bb)
    oficial = fuentes.geo_oficial(jar)
    geo = ensamblar.ensamblar(oficial, cubos)
    res, del_jar = resolver.construir(fuentes.resolver_oficial(jar),
                                      fuentes.resolvers_mega(jar_mega))
    png = fuentes.png_usuario()
    tex_shiny, cuentas = texturas.textura(jar, True, png, cubos)
    tex_normal, _ = texturas.textura(jar, False, png, cubos)
    capas = {r: texturas.rellenar(fuentes.textura_del_jar(jar, r)) for r in sorted(del_jar)
             if not r.endswith("/charizard.png") and not r.endswith("/charizard_shiny.png")}

    print("  CHARIZARD MECHA · %d cubos del casco en %d huesos nuevos · textura 256x%d"
          % (len(cubos), len(ensamblar.PADRE), ensamblar.TEXTURA_ALTO))
    print("     repintado al reparto del juego: %d texels movidos, %d en su sitio"
          % (cuentas["movidos"], cuentas["en_su_sitio"]))

    fallos, avisos = comprobar.todo(bb, cubos, oficial, geo, res, tex_shiny, png,
                                    jar, jar_mega, fuentes.animaciones_oficiales(jar))
    for a in avisos:
        print("     " + a)
    if fallos:
        print()
        print("  %d FALLO(S):" % len(fallos))
        for f in fallos:
            print("     x " + f)
    else:
        print("     comprobaciones: cuerpo == jar, 133 cubos y todos con hueso, UV "
              "dentro y sin pisar el cuerpo, todas las caras pintadas, nada flota, "
              "el menton gira en un eje, resolver completo, megas == mega_showdown "
              "-- TODO EN VERDE")

    # Las laminas van siempre, con fallos o sin ellos: son lo que permite verlos.
    fuentes.BUILD.mkdir(parents=True, exist_ok=True)
    llama = next((im for r, im in capas.items() if "flame1" in r), None)
    tex_visor = tex_shiny.copy()
    if llama is not None:
        tex_visor.alpha_composite(llama)
    visor.lamina_cuerpo(geo, fuentes.BUILD / "charizard.png", tex_visor, visor.IDLE,
                        "CHARIZARD MECHA · shiny · pose idle")
    visor.lamina_cabeza(geo, fuentes.BUILD / "casco.png", tex_visor, visor.IDLE,
                        "EL CASCO · textura repintada al reparto del juego")
    visor.lamina_cabeza(geo, fuentes.BUILD / "casco-huesos.png", tex_visor, visor.IDLE,
                        "QUE VA EN CADA HUESO", resaltar=comprobar.COLORES_HUESOS)
    # ⚠ Y la misma lamina con la textura del usuario SIN repintar, muestreada
    #   como la muestrearia el juego: es la prueba de que el repintado hace
    #   falta, y la que hay que mirar si alguien duda.
    sin = texturas.cuerpo(jar, True)
    sin.alpha_composite(_solo_casco(png))
    visor.lamina_cabeza(geo, fuentes.BUILD / "casco-sin-repintar.png", sin, visor.IDLE,
                        "ASI SALDRIA SIN REPINTAR (la textura del usuario tal cual)")
    visor.lamina_poses(geo, fuentes.BUILD / "casco-poses.png", tex_visor,
                       "SEIS POSES DE LAS ANIMACIONES REALES")
    print("     laminas -> %s" % fuentes.BUILD)

    if fallos:
        print()
        print("  NO se exporta con fallos.")
        return 1

    if args.generar:
        _json(fuentes.PACK / resolver.FICHERO_GEO, geo)
        _json(fuentes.PACK / resolver.FICHERO_RESOLVER, res)
        dir_tex = fuentes.PACK / resolver.DIR_TEXTURAS
        dir_tex.mkdir(parents=True, exist_ok=True)
        tex_normal.save(dir_tex / (fuentes.NOMBRE + ".png"))
        tex_shiny.save(dir_tex / (fuentes.NOMBRE + "_shiny.png"))
        for r, im in capas.items():
            im.save(fuentes.PACK / resolver.fichero_de(resolver.recurso_nuestro(r)))
        destino_bb = fuentes.ARTE / (fuentes.NOMBRE + ".bbmodel")
        bbmodel.escribir(geo, tex_shiny, destino_bb)
        print("     -> %s" % (fuentes.PACK / resolver.FICHERO_GEO))
        print("     -> %s" % (fuentes.PACK / resolver.FICHERO_RESOLVER))
        print("     -> %s  (+ _shiny y %d capas)" % (dir_tex / (fuentes.NOMBRE + ".png"), len(capas)))
        print("     -> %s  (se abre en Blockbench con el reparto del juego)" % destino_bb)

    if args.generar or args.verificar:
        v = comprobar.escrito(geo, res, tex_shiny, capas)
        if v:
            print()
            print("  %d FALLO(S) EN LO ESCRITO EN EL PACK:" % len(v))
            for f in v:
                print("     x " + f)
            return 1
        print("     vuelta por el pack: geo, resolver, %d texturas y el .bbmodel -- TODO EN VERDE"
              % (2 + len(capas)))
    return 0


def _solo_casco(png):
    """El PNG del usuario con la mitad de arriba (el escombro) borrada."""
    from PIL import Image
    im = Image.new("RGBA", png.size, (0, 0, 0, 0))
    im.paste(png.crop((0, 128, 256, 256)), (0, 128))
    return im


if __name__ == "__main__":
    raise SystemExit(main())
