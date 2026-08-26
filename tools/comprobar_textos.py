#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Comprueba que toda clave de traduccion que usa el codigo EXISTE.

⚠⚠⚠ POR QUE HACE FALTA ESTO

    `Text.translatable("clave.que.no.existe")` NO DA NINGUN ERROR. Ni al
    compilar, ni al arrancar, ni al dibujar. Minecraft pinta LA CLAVE TAL CUAL,
    asi que el jugador ve:

        pokepad.lunaeternal.mercado.tu_plata

    Paso de verdad el 2026-08-25: al pasar los objetos a escaparate borre las
    claves del libro de ordenes que ya no se usaban, y una de ellas --el rotulo
    «TU PLATA»-- la seguia usando la pantalla del GTS. El unico aviso fue una
    captura del usuario.

⚠⚠ Y COMPRUEBA LOS DOS SENTIDOS:

    faltan   la usa el codigo y no esta en el fichero -> se ve la clave cruda
    sobran   esta en el fichero y no la usa nadie     -> peso muerto que
             confunde al siguiente que edite, y esconde las que si faltan

⚠ Los idiomas se comparan ENTRE SI ademas: una clave que este en `es_es` y no
  en `en_us` deja a un cliente en ingles viendo la clave cruda, que es el mismo
  fallo con otro sombrero.

    python tools/comprobar_textos.py
"""
import json
import pathlib
import re
import sys

RAIZ = pathlib.Path(__file__).resolve().parent.parent
LANG = RAIZ / "mod/src/client/resources/assets/lunaeternal/lang"
FUENTES = [RAIZ / "mod/src/client/java", RAIZ / "mod/src/main/java"]

# ⚠ El prefijo importa: `Text.translatable` tambien se usa con claves de
#   Minecraft y de Cobblemon, que NO estan en nuestro fichero y no deben estarlo.
NUESTRO = "pokepad.lunaeternal."

PATRON = re.compile(r'"(pokepad\.lunaeternal\.[a-zA-Z0-9_.]+)"')


def prefijo(clave):
    """¿Es un TROZO de clave que el codigo completa en tiempo de ejecucion?

    ⚠ Se distingue POR LA FORMA y no con una lista de excepciones: una clave
      de verdad nunca acaba en `.` ni en `_`, y un trozo que se concatena
      siempre acaba en uno de los dos --`"...clan.rol." + rol`--.

      Una lista escrita a mano habria que mantenerla, y acabaria siendo el
      sitio donde se esconden los fallos de verdad: basta con que alguien
      apunte ahi la clave que le molesta.
    """
    return clave.endswith(".") or clave.endswith("_")

def prefijos():
    """Los trozos de clave que el codigo concatena."""
    salida = set()
    for raiz in FUENTES:
        for f in raiz.rglob("*.java"):
            for clave in PATRON.findall(f.read_text(encoding="utf-8")):
                if prefijo(clave):
                    salida.add(clave)
    return salida


def usadas():
    salida = {}
    for raiz in FUENTES:
        for f in raiz.rglob("*.java"):
            for clave in PATRON.findall(f.read_text(encoding="utf-8")):
                if prefijo(clave):
                    continue
                salida.setdefault(clave, []).append(f.relative_to(RAIZ))
    return salida


def main():
    idiomas = {f.stem: json.loads(f.read_text(encoding="utf-8"))
               for f in sorted(LANG.glob("*.json"))}
    if "es_es" not in idiomas:
        print("  no encuentro es_es.json")
        return 1

    en_codigo = usadas()
    # ⚠ Un prefijo dinamico HACE QUE SUS HIJAS SE USEN. Sin esto,
    #   `clan.rol.LIDER` saldria como huerfana en cada ejecucion.
    usadas_prefijo = prefijos()
    problemas = 0

    print(f"\n  {len(en_codigo)} claves usadas por el codigo")
    for idioma, datos in idiomas.items():
        print(f"  {len(datos)} en {idioma}.json")

    # --- 1. las que usa el codigo y no existen: SE VEN CRUDAS
    for idioma, datos in idiomas.items():
        faltan = [k for k in sorted(en_codigo) if k not in datos]
        if faltan:
            problemas += len(faltan)
            print(f"\n  ⚠ {len(faltan)} claves que el codigo USA y no estan en "
                  f"{idioma}.json  (se verian CRUDAS):")
            for k in faltan:
                donde = ", ".join(str(x) for x in sorted(set(en_codigo[k]))[:2])
                print(f"      {k}\n          usada en {donde}")

    # --- 2. las que estan y no usa nadie
    todas = set()
    for datos in idiomas.values():
        todas |= set(datos)
    huerfanas = [k for k in sorted(todas)
                 if k.startswith(NUESTRO) and k not in en_codigo
                 and not any(k.startswith(u) for u in usadas_prefijo)]
    if huerfanas:
        print(f"\n  · {len(huerfanas)} claves que no usa nadie (peso muerto, "
              f"no rompe nada):")
        for k in huerfanas:
            print(f"      {k}")

    # --- 3. un idioma que tenga menos que otro
    nombres = list(idiomas)
    for i, a in enumerate(nombres):
        for b in nombres[i + 1:]:
            solo_a = set(idiomas[a]) - set(idiomas[b])
            solo_b = set(idiomas[b]) - set(idiomas[a])
            for falta, quien, otro in ((solo_a, b, a), (solo_b, a, b)):
                if falta:
                    problemas += len(falta)
                    print(f"\n  ⚠ {len(falta)} claves en {otro} que faltan en "
                          f"{quien} (se verian crudas con ese idioma):")
                    for k in sorted(falta):
                        print(f"      {k}")

    print()
    if problemas:
        print(f"  {problemas} PROBLEMAS. Una clave que falta SE VE CRUDA "
              f"en la pantalla.")
        return 1
    print("  Todas las claves que usa el codigo existen en todos los idiomas.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
