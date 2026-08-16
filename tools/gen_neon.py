#!/usr/bin/env python3
"""OBSOLETO — el generador del neon vive ahora en `tools/gen_bloques.py`.

Este fichero se queda como puente, y no por nostalgia: durante meses la
documentacion, el historial y varias sesiones de trabajo han dicho
«python tools/gen_neon.py». Borrarlo a secas convertiria ese comando en un
«no such file», y el que lo escriba se quedara sin saber que hay que ejecutar
en su lugar.

QUE PASO

El neon dejo de ser el unico habitante del mod. Hoy son seis familias —neon,
hormigon, metal, rejilla, vidrio y pavimento— y las seis escriben en el MISMO
arbol de recursos y comparten tres ficheros unicos: los dos idiomas y los tags.
Con un generador por familia, el ultimo en ejecutarse se llevaba por delante a
los demas. Peor: este script empezaba borrando `assets/lunaneon` entero, asi que
ejecutarlo hoy dejaria el mod con 96 bloques y 506 huecos.

De ahi que delegue en vez de fallar: lo que se queria hacer al escribirlo sigue
haciendose, y ademas sale bien.
"""
import runpy
import sys
from pathlib import Path

print("tools/gen_neon.py esta obsoleto: el generador es tools/gen_bloques.py")
print("(el neon es ahora una de las seis familias del mod)\n")

sys.argv[0] = str(Path(__file__).with_name("gen_bloques.py"))
runpy.run_path(sys.argv[0], run_name="__main__")
