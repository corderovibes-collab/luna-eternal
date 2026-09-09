# -*- coding: utf-8 -*-
"""
COMPRUEBA QUE NINGUNA FUENTE DE XP DEL PASE SE QUEDE SIN QUIEN LA LLAME.

    python tools/comprobar_pase.py

⚠⚠⚠ UNA FUENTE DECLARADA Y NO ENGANCHADA DA CERO XP PARA SIEMPRE, EN SILENCIO.

   `PaseXp` declara de que se saca XP y cuanta. Que una constante exista NO
   significa que nadie la use: si `PESCA` valiera 8 y ningun sitio llamara a
   `Pase.ganar` al pescar, el pase simplemente NO SUBIRIA AL PESCAR. Sin error
   al compilar, sin error al arrancar, sin una linea en el log -- y la tabla de
   la documentacion seguiria prometiendolo.

   Es EXACTAMENTE el fallo de los 62 cosmeticos que no existian, el del traje
   registrado como «arceus» y el de `KitService.claim`, que apuntaba la fecha y
   no entregaba nada. Los tres se ven igual desde fuera: funciona todo menos lo
   que importa.

⚠⚠ Y ESTO NO SE PUEDE COMPROBAR EN EL AUTOTEST. El autotest prueba que `ganar`
   acredita --y lo hace, ver `testFuentesDelPase`-- pero no puede saber si
   alguien LLAMA a `ganar` cuando el jugador pesca: eso es el cableado a los
   eventos, y solo se ve leyendo el codigo. Por eso es un script, como
   `comprobar_textos.py`: la misma familia de comprobacion (dos listas que
   tienen que estar de acuerdo y nada las obliga).

⚠ Lo que este script NO dice: si el evento del que cuelga la llamada se dispara
  de verdad. Eso solo se ve jugando. Aqui se caza el hueco mas grande y mas
  barato: la constante que no menciona nadie.
"""
import pathlib
import re
import sys

RAIZ = pathlib.Path(__file__).resolve().parent.parent
XP = RAIZ / "mod/src/main/java/net/pokereport/luna/pase/PaseXp.java"
FUENTES = RAIZ / "mod/src/main/java"

#: Constantes que NO tienen que tener quien las llame, y por que.
#:
#: ⚠ La lista es corta y cada linea lleva su motivo. Una lista de excepciones
#:   sin motivos acaba siendo el sitio donde se esconden los fallos de verdad
#:   -- es lo que dice `comprobar_textos.py` de los prefijos dinamicos.
EXENTAS = {
    "TORRE_BASE": "privada: la usa torre() aqui mismo",
    "TORRE_PASO": "privada: la usa torre() aqui mismo",
    "TORRE_TECHO": "privada: la usa torre() aqui mismo",
    "VECES_HORA_MENA": "es una TASA del mundo, no XP: la cruza el autotest",
    "VECES_HORA_MENA_RARA": "es una TASA del mundo, no XP: la cruza el autotest",
}


def constantes():
    """Las constantes publicas de XP y la funcion torre()."""
    texto = XP.read_text(encoding="utf-8")
    nombres = re.findall(r"static final long ([A-Z_]+)\s*=", texto)
    nombres += re.findall(r"static final int ([A-Z_]+)\s*=", texto)
    if "torre" in texto:
        nombres.append("torre")
    return nombres, texto


def usos(nombre):
    """En cuantos ficheros (que no sean PaseXp) se menciona."""
    encontrados = []
    for f in FUENTES.rglob("*.java"):
        if f.name in ("PaseXp.java", "AutoTest.java"):
            continue
        t = f.read_text(encoding="utf-8", errors="replace")
        if re.search(r"PaseXp\.%s\b" % re.escape(nombre), t):
            encontrados.append(f.relative_to(RAIZ))
    return encontrados


def main():
    nombres, _ = constantes()
    print("  %d constantes en PaseXp\n" % len(nombres))

    huerfanas = []
    for n in sorted(set(nombres)):
        if n in EXENTAS:
            print("  ~ %-22s exenta: %s" % (n, EXENTAS[n]))
            continue
        donde = usos(n)
        if donde:
            print("  OK %-22s %s" % (n, ", ".join(str(d) for d in donde)))
        else:
            huerfanas.append(n)
            print("  ** %-22s NADIE LA LLAMA" % n)

    print()
    if huerfanas:
        print("  ERROR: %d fuentes de XP declaradas que no engancha nadie:"
              % len(huerfanas))
        for n in huerfanas:
            print("     PaseXp.%s" % n)
        print()
        print("  Una fuente sin quien la llame da CERO XP para siempre y no da")
        print("  ningun error. O se engancha, o se quita de la tabla.")
        return 1

    print("  Todas las fuentes de XP del pase tienen quien las llame.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
