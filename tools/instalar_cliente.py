#!/usr/bin/env python3
"""
Instala el mod en la instancia local de PrismLauncher, con red de seguridad.

Existe por un fallo real y desagradable de diagnosticar. Al copiar el jar con
Minecraft ABIERTO, el juego no se entera: Fabric carga las clases cuando las
necesita, asi que sigue funcionando con las que ya tenia en memoria. La bomba
estalla despues, cuando toca cargar una clase nueva:

    Failed to load class file for 'net.pokereport.luna.net.PadPayloads$Celda'
    Caused by: java.util.zip.ZipException: ZipFile invalid LOC header

Parece un error de red o de protocolo —el stack esta lleno de netty— y no lo
es: el jar cambio bajo los pies del proceso.

Este script se niega a copiar si el juego esta abierto, y comprueba el
resultado en vez de suponerlo.
"""
import hashlib
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
JAR = RAIZ / "mod" / "build" / "libs" / "lunaeternal-0.1.0.jar"
INSTANCIA = Path("C:/Users/JUAN/AppData/Roaming/PrismLauncher/instances"
                 "/PokeReport-LunaEternal-0.1.0/minecraft/mods")


def minecraft_abierto() -> bool:
    """Busca un javaw.exe. Es tosco pero suficiente: en esta maquina el
    unico javaw que corre es el del juego."""
    try:
        salida = subprocess.run(
            ["tasklist", "/FI", "IMAGENAME eq javaw.exe", "/NH"],
            capture_output=True, text=True, timeout=20).stdout
        return "javaw.exe" in salida
    except Exception:
        # Si no se puede comprobar, es mas seguro asumir que si.
        return True


def sha1(p: Path) -> str:
    return hashlib.sha1(p.read_bytes()).hexdigest()


def valido(p: Path) -> bool:
    """Que el zip abra Y que se pueda leer una clase de dentro.

    Comprobar solo el tamano no vale: ya paso una vez que coincidia y el
    contenido estaba roto."""
    try:
        z = zipfile.ZipFile(p)
        if z.testzip() is not None:
            return False
        z.read("net/pokereport/luna/net/PadPayloads$Celda.class")
        return True
    except Exception:
        return False


def main() -> None:
    if not JAR.exists():
        raise SystemExit(f"No existe {JAR}. Compila antes: cd mod && bash build.sh")

    if not valido(JAR):
        raise SystemExit("El jar recien compilado esta corrupto. Recompila.")

    if minecraft_abierto() and "--force" not in sys.argv:
        raise SystemExit(
            "Minecraft esta ABIERTO. Cierralo antes de instalar.\n"
            "\n"
            "Si se copia ahora, el juego seguira funcionando hasta que toque\n"
            "cargar una clase nueva, y entonces reventara con un error de\n"
            "netty que no se parece en nada a la causa real.")

    INSTANCIA.mkdir(parents=True, exist_ok=True)
    destino = INSTANCIA / JAR.name

    # Borrar antes de copiar: sobrescribir un jar deja restos si el destino
    # era mas grande, y el tamano final puede coincidir igualmente.
    if destino.exists():
        destino.unlink()
    shutil.copy2(JAR, destino)

    if not valido(destino) or sha1(destino) != sha1(JAR):
        raise SystemExit("La copia NO coincide con el original. No lances el juego.")

    print(f"Instalado en {destino}")
    print(f"  {destino.stat().st_size:,} bytes · sha1 {sha1(destino)[:16]}")
    print("\nAhora puedes abrir Minecraft.")


if __name__ == "__main__":
    main()
