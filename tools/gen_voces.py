#!/usr/bin/env python3
"""
Convierte las descripciones habladas de la Pokedex y las deja listas.

QUE HACE

Coge los MP3 de origen, los convierte a OGG Vorbis --que es lo UNICO que
reproduce Minecraft-- y escribe de una vez las tres cosas que tienen que ir
sincronizadas o el sonido no suena:

    mod/src/client/resources/assets/lunaeternal/sounds/pokedex/<clave>.ogg
    mod/src/client/resources/assets/lunaeternal/sounds.json
    mod/src/main/resources/voces.txt        <- la lista que lee VozService

POR QUE UNA LISTA GENERADA Y NO ESCRITA A MANO

Porque son tres sitios y basta con olvidarse de uno para que el jugador
escanee y no suene nada, sin ningun error en ningun log. Con esto hay UNA
fuente --los ficheros de origen-- y las tres salidas se rehacen juntas.

`voces.txt` va en `main/resources` a proposito: ese conjunto se compila en los
DOS lados, asi que el servidor sabe a quien mandarle voz y el cliente sabe si
pintar el boton encendido, leyendo la misma lista.

DE DONDE SALEN LOS NOMBRES

Las carpetas vienen como `0001 - Bulbasaur` o `0019 - Rattata de Alola`. El
numero se tira --Cobblemon no lo usa-- y el nombre se pasa a la forma que usa
el juego:

    Bulbasaur              -> bulbasaur
    Nidoran♀               -> nidoran_f      (su especie se llama Nidoran-F)
    Mr. Mime               -> mr_mime
    Farfetch'd             -> farfetchd
    Rattata de Alola       -> rattata_alola  (en Cobblemon es una FORMA)

Uso:
    python tools/gen_voces.py                 # con el origen por defecto
    python tools/gen_voces.py --origen RUTA
    python tools/gen_voces.py --copiar-a "D:/sonidos pokedex"
"""
import argparse
import json
import re
import shutil
import subprocess
import sys
import unicodedata
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
SONIDOS = (RAIZ / "mod" / "src" / "client" / "resources" / "assets"
           / "lunaeternal" / "sounds" / "pokedex")
SOUNDS_JSON = SONIDOS.parent.parent / "sounds.json"
LISTA = RAIZ / "mod" / "src" / "main" / "resources" / "voces.txt"

ORIGEN_POR_DEFECTO = Path(
    r"C:\Users\JUAN\Downloads\Pokemon_0001-0151-20260816T031600Z-1-001"
    r"\Pokemon_0001-0151")

# Las formas regionales de Cobblemon no son especies aparte: son FORMAS de la
# especie base. La clave las junta con un guion bajo para que no se pisen.
REGIONES = ("alola", "galar", "hisui", "paldea")

# Los que no salen bien de la regla general. Son pocos y son estos.
A_MANO = {
    "nidoran♀": "nidoran_f",     # su especie se llama Nidoran-F
    "nidoran♂": "nidoran_m",
    "mr. mime": "mr_mime",
    "farfetch’d": "farfetchd",
    "farfetch'd": "farfetchd",
}


def numero(carpeta: str) -> str:
    """`0019 - Rattata de Alola` -> `19`. Sin ceros delante."""
    m = re.match(r"^\s*(\d+)", carpeta)
    return str(int(m.group(1))) if m else ""


def clave(carpeta: str) -> str:
    """`0019 - Rattata de Alola` -> `rattata_alola`."""
    nombre = re.sub(r"^\s*\d+\s*-\s*", "", carpeta).strip().lower()
    region = ""
    for r in REGIONES:
        if nombre.endswith(" de " + r):
            nombre = nombre[: -(len(r) + 4)].strip()
            region = "_" + r
            break
    if nombre in A_MANO:
        return A_MANO[nombre] + region
    # Sin acentos, y todo lo que no sea letra o numero pasa a guion bajo.
    plano = "".join(c for c in unicodedata.normalize("NFD", nombre)
                    if unicodedata.category(c) != "Mn")
    plano = re.sub(r"[^a-z0-9]+", "_", plano).strip("_")
    return plano + region


def ffmpeg() -> str:
    ruta = shutil.which("ffmpeg")
    if ruta:
        return ruta
    # Instalado por winget, que no siempre deja el PATH puesto.
    for c in Path.home().glob("AppData/Local/Microsoft/WinGet/Packages/"
                              "Gyan.FFmpeg*/**/bin/ffmpeg.exe"):
        return str(c)
    raise SystemExit("No se encuentra ffmpeg. Sin el no hay conversion a OGG.")


def convertir(exe: str, origen: Path, destino: Path) -> int:
    """MP3 -> OGG Vorbis. Mono, que es lo que pide un sonido de interfaz."""
    subprocess.run(
        [exe, "-hide_banner", "-loglevel", "error", "-y", "-i", str(origen),
         "-c:a", "libvorbis", "-q:a", "4", "-ac", "1", "-ar", "44100",
         str(destino)],
        check=True)
    return destino.stat().st_size


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--origen", type=Path, default=ORIGEN_POR_DEFECTO)
    ap.add_argument("--copiar-a", type=Path, default=None,
                    help="ademas, deja los MP3 renombrados aqui")
    args = ap.parse_args()

    if not args.origen.exists():
        raise SystemExit(f"No existe el origen: {args.origen}")

    exe = ffmpeg()
    SONIDOS.mkdir(parents=True, exist_ok=True)
    for viejo in SONIDOS.glob("*.ogg"):
        viejo.unlink()

    print(f"VOCES DE LA POKEDEX\n  origen  {args.origen}")
    hechas, vacias, bytes_total = [], [], 0
    for carpeta in sorted(p for p in args.origen.iterdir() if p.is_dir()):
        mp3 = sorted(carpeta.glob("*.mp3"))
        if not mp3:
            vacias.append(carpeta.name)
            continue          # todavia sin grabar: no es un error
        k = clave(carpeta.name)
        bytes_total += convertir(exe, mp3[0], SONIDOS / f"{k}.ogg")
        if args.copiar_a:
            # En la carpeta de trabajo se usa LA CONVENCION DEL USUARIO:
            # `1-bulbasaur_sound.mp3`. Dentro del mod, en cambio, el fichero se
            # llama solo `bulbasaur.ogg`: ahi el nombre ES el identificador del
            # sonido y meterle el numero de Pokedex lo ataria a un orden que
            # Cobblemon no usa para nada.
            args.copiar_a.mkdir(parents=True, exist_ok=True)
            shutil.copy2(mp3[0],
                         args.copiar_a / f"{numero(carpeta.name)}-{k}_sound.mp3")
        hechas.append(k)

    # sounds.json. `stream` va en true porque son de varios segundos, que es
    # justo el caso para el que Minecraft recomienda transmitir en vez de
    # cargar entero en memoria.
    SOUNDS_JSON.write_text(json.dumps({
        f"pokedex.{k}": {
            "category": "voice",
            "subtitle": "subtitles.lunaeternal.pokedex.voz",
            "sounds": [{"name": f"lunaeternal:pokedex/{k}", "stream": True}],
        } for k in hechas
    }, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    LISTA.parent.mkdir(parents=True, exist_ok=True)
    LISTA.write_text("\n".join(hechas) + "\n", encoding="utf-8")

    print(f"  {len(hechas)} voces convertidas  ({bytes_total // 1024} KB)")
    if vacias:
        print(f"  {len(vacias)} carpetas sin grabar todavia "
              f"(p. ej. {vacias[0]})")
    regionales = [k for k in hechas if k.endswith(REGIONES)]
    if regionales:
        print(f"  de ellas {len(regionales)} son formas regionales: "
              f"{', '.join(regionales)}")
    if args.copiar_a:
        print(f"  MP3 renombrados en {args.copiar_a}")
    print(f"  -> {SONIDOS.relative_to(RAIZ)}")
    print(f"  -> {SOUNDS_JSON.relative_to(RAIZ)}")
    print(f"  -> {LISTA.relative_to(RAIZ)}")
    print("     recompila el mod:  cd mod && bash build.sh")


if __name__ == "__main__":
    main()
