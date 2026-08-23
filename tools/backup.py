#!/usr/bin/env python3
"""
Baja una copia de seguridad del servidor a este ordenador.

POR QUE EXISTE ESTO Y NO SE USA EL BOTON DEL PANEL

Porque no hay boton. El plan contratado da **cero** ranuras de backup:

    feature_limits.backups = 0

O sea que el servidor de desarrollo no tiene ninguna red de seguridad, y todo
lo que se construya en la ciudadela vive en un unico disco de un hosting.
Es el `INF-002` / `INF-007` del backlog, y esto lo cierra.

QUE SE COPIA

  world/          TODO el mundo, y ahi dentro lo que de verdad importa:
                    dimensions/lunaeternal/ciudadela   LAS CONSTRUCCIONES
                    dimensions/lunaeternal/{lobby,salvaje}
                    region/                            el overworld
                    playerdata/ pokemon/ pokedex/      el estado de la gente
                    cobblemonplayerdata/
                    datapacks/                         generaciones y RCT
  config/         configuracion de los 57 mods
  *.json, *.properties   ops, whitelist, server.properties

  NO se copian los jars: son 400 MB que ya estan en el manifiesto publicado y
  se rebajan solos. Copiarlos multiplicaria por cinco el peso de cada copia
  para guardar algo que no es nuestro y que ya esta versionado.

⚠⚠ EL MUNDO SE CONGELA ANTES DE COPIAR, Y ESO NO ES OPCIONAL.

  Minecraft escribe los ficheros de region continuamente. Comprimir un mundo
  vivo captura ficheros A MEDIO ESCRIBIR, y el resultado es una copia que
  parece correcta --pesa lo que debe, se descomprime bien-- y tiene chunks
  corruptos. El fallo no aparece hasta que alguien camina hasta ese chunk,
  semanas despues.

  Se resuelve con `save-off` + `save-all flush`: el servidor vuelca todo a
  disco y deja de escribir. Se copia, y se hace `save-on`.

  ⚠ Y `save-on` VA EN UN `finally`. Si el script muere entre medias y el
    mundo se queda congelado, el servidor sigue funcionando y NO GUARDA NADA:
    a la siguiente caida se pierde todo lo jugado desde la copia. Es peor que
    no haber hecho la copia.

Uso:
    python tools/backup.py                  # copia completa a backups/
    python tools/backup.py --solo-mundo     # solo world/, mas rapido
    python tools/backup.py --sin-congelar   # NO recomendado; ver arriba
    python tools/backup.py --listar         # que copias hay ya
"""
import argparse
import os
import sys
import time
import urllib.request
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(RAIZ / "tools"))
import ptero  # noqa: E402

DESTINO = RAIZ / "backups"

# Lo que entra en la copia. `world` es el que importa; el resto es barato.
RUTAS_COMPLETO = [
    "world", "config", "defaultconfigs",
    "server.properties", "ops.json", "whitelist.json",
    "banned-players.json", "banned-ips.json", "usercache.json",
    "eula.txt",
]
RUTAS_MUNDO = ["world"]


def mb(b):
    return f"{b / 1048576:,.1f} MB"


def congelar():
    """Vuelca a disco y deja de escribir. Devuelve True si se congelo."""
    print("  congelando el mundo (save-off + save-all flush)...")
    ptero.comando("save-off")
    time.sleep(1)
    ptero.comando("save-all flush")
    # El flush de un mundo con varias dimensiones tarda. No hay evento que
    # avisar, asi que se espera un margen generoso: es mucho mas barato que
    # una copia con chunks a medias.
    time.sleep(8)
    return True


def descongelar():
    print("  reanudando el guardado (save-on)")
    ptero.comando("save-on")


def comprimir(rutas):
    """Crea un .tar.gz en el servidor y devuelve su nombre."""
    print(f"  comprimiendo {len(rutas)} rutas en el servidor...")
    r = ptero.pedir("POST", "/files/compress", {"root": "/", "files": rutas})
    a = r["attributes"]
    print(f"  archivo creado: {a['name']}  ({mb(a['size'])})")
    return a["name"], a["size"]


def url_descarga(nombre):
    import urllib.parse
    r = ptero.pedir("GET", "/files/download?file=" +
                    urllib.parse.quote("/" + nombre))
    return r["attributes"]["url"]


def descargar(url, destino, esperado):
    """Baja el archivo mostrando progreso. Comprueba el tamano al terminar."""
    destino.parent.mkdir(parents=True, exist_ok=True)
    req = urllib.request.Request(url, headers={"User-Agent": "luna-eternal/1.0"})
    leido = 0
    t0 = time.time()
    with urllib.request.urlopen(req, timeout=120) as r, open(destino, "wb") as f:
        while True:
            trozo = r.read(1 << 20)
            if not trozo:
                break
            f.write(trozo)
            leido += len(trozo)
            if leido % (20 << 20) < (1 << 20):
                seg = max(0.1, time.time() - t0)
                print(f"    {mb(leido)}  ({leido / seg / 1048576:,.1f} MB/s)")
    return leido


def borrar_del_servidor(nombre):
    """El archivo ocupa disco del servidor: se quita en cuanto esta bajado."""
    try:
        ptero.borrar("/", [nombre])
        print(f"  archivo temporal borrado del servidor")
    except Exception as e:
        print(f"  AVISO: no se pudo borrar {nombre} del servidor: {e}")
        print(f"         Ocupa disco alli. Borralo a mano desde el panel.")


def listar():
    if not DESTINO.exists():
        print("No hay ninguna copia todavia.")
        return
    copias = sorted(DESTINO.glob("*.tar.gz"))
    if not copias:
        print("No hay ninguna copia todavia.")
        return
    print(f"{len(copias)} copias en {DESTINO}:")
    total = 0
    for c in copias:
        t = c.stat().st_size
        total += t
        print(f"  {c.name:<46} {mb(t):>12}  "
              f"{time.strftime('%Y-%m-%d %H:%M', time.localtime(c.stat().st_mtime))}")
    print(f"  {'TOTAL':<46} {mb(total):>12}")


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--solo-mundo", action="store_true",
                    help="solo world/, sin configuracion")
    ap.add_argument("--sin-congelar", action="store_true",
                    help="NO congela el mundo. Arriesga chunks corruptos")
    ap.add_argument("--listar", action="store_true")
    args = ap.parse_args()

    if args.listar:
        listar()
        return

    rutas = RUTAS_MUNDO if args.solo_mundo else RUTAS_COMPLETO
    marca = time.strftime("%Y-%m-%d-%H%M")
    etiqueta = "mundo" if args.solo_mundo else "completo"
    destino = DESTINO / f"luna-{etiqueta}-{marca}.tar.gz"

    print("=" * 60)
    print(f"  COPIA DE SEGURIDAD  ·  {etiqueta}")
    print(f"  servidor {ptero.DEV}   ->   {destino.name}")
    print("=" * 60)

    estado = ptero.estado().get("current_state")
    print(f"  estado del servidor: {estado}")

    congelado = False
    try:
        if estado == "running" and not args.sin_congelar:
            congelado = congelar()
        elif args.sin_congelar:
            print("  ⚠ SIN CONGELAR: la copia puede tener chunks a medio escribir")
        else:
            print("  el servidor no esta corriendo: no hace falta congelar")

        nombre, tam = comprimir(rutas)
    finally:
        # ⚠ Pase lo que pase, el mundo vuelve a guardarse.
        if congelado:
            descongelar()

    print("  pidiendo enlace de descarga...")
    url = url_descarga(nombre)
    print(f"  bajando a {destino}")
    leido = descargar(url, destino, tam)

    print(f"\n  bajado: {mb(leido)}")
    if abs(leido - tam) > 1024:
        print(f"  ⚠⚠ EL TAMANO NO CUADRA: el panel decia {mb(tam)}.")
        print(f"     NO borres el archivo del servidor y repite la descarga.")
        return
    print(f"  tamano verificado contra el panel ✔")

    borrar_del_servidor(nombre)

    print("\n" + "=" * 60)
    print(f"  COPIA COMPLETA: {destino}")
    print(f"  ⚠ Esta en el MISMO disco que el proyecto. Para que sirva de algo")
    print(f"    hay que sacarla de aqui: disco externo, Drive, lo que sea.")
    print(f"    Una copia en la misma maquina no protege de que muera la maquina.")
    print("=" * 60)


if __name__ == "__main__":
    main()
