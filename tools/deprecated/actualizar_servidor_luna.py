"""Respalda el JAR remoto, detiene el servidor y activa el JAR Luna verificado."""
import hashlib
import io
import time
import urllib.request
import zipfile
from pathlib import Path

import ptero


def main():
    raiz = Path(__file__).resolve().parent.parent
    nombre = "lunaeternal-0.1.0.jar"
    nuevos = (raiz / "mod/build/libs" / nombre).read_bytes()
    with zipfile.ZipFile(io.BytesIO(nuevos)) as z:
        assert z.testzip() is None

    easyauth_path = raiz / "build/auditoria-forense/snapshot/servidor/config/EasyAuth/main.conf"
    cobbreeding_path = raiz / "build/auditoria-forense/snapshot/servidor/config/cobbreeding/main.json"
    easyauth_nuevo = easyauth_path.read_text(encoding="utf-8")
    cobbreeding_nuevo = cobbreeding_path.read_text(encoding="utf-8")

    anteriores = [n for n, _, fichero in ptero.listar("/mods")
                  if fichero and n.startswith("lunaeternal-") and n.endswith(".jar")]
    assert anteriores == [nombre], f"Jars inesperados: {anteriores}"
    url = ptero.pedir("GET", "/files/download?file=/mods/" + nombre)["attributes"]["url"]
    with urllib.request.urlopen(url, timeout=60) as r:
        viejo = r.read()
    with zipfile.ZipFile(io.BytesIO(viejo)) as z:
        assert z.testzip() is None
    backup_dir = raiz / "build/backups/remediacion"
    backup_dir.mkdir(parents=True, exist_ok=True)
    backup_jar = backup_dir / ("server-before-" + hashlib.sha256(viejo).hexdigest()[:12] + ".jar")
    backup_jar.write_bytes(viejo)
    print(f"Respaldo JAR verificado: {backup_jar}", flush=True)

    # Backup configs
    easyauth_viejo = ptero.leer("/config/EasyAuth/main.conf")
    (backup_dir / "server-before-easyauth-main.conf").write_text(easyauth_viejo, encoding="utf-8")
    cobbreeding_viejo = ptero.leer("/config/cobbreeding/main.json")
    (backup_dir / "server-before-cobbreeding-main.json").write_text(cobbreeding_viejo, encoding="utf-8")
    print("Respaldos de configuracion guardados.", flush=True)

    print("Deteniendo servidor...", flush=True)
    ptero.potencia("stop")
    for _ in range(60):
        if ptero.estado()["current_state"] == "offline":
            break
        time.sleep(3)
    else:
        raise RuntimeError("No se detuvo el servidor; no se ha sustituido el JAR")

    try:
        print("Subiendo nuevo JAR...", flush=True)
        assert ptero.subir("/mods", nombre, nuevos) == len(nuevos)
        url = ptero.pedir("GET", "/files/download?file=/mods/" + nombre)["attributes"]["url"]
        with urllib.request.urlopen(url, timeout=60) as r:
            remoto = r.read()
        assert hashlib.sha256(remoto).digest() == hashlib.sha256(nuevos).digest()
        print("JAR verificado por SHA-256.", flush=True)

        print("Actualizando configuraciones de servidor...", flush=True)
        ptero.escribir("/config/EasyAuth/main.conf", easyauth_nuevo)
        ptero.escribir("/config/cobbreeding/main.json", cobbreeding_nuevo)
        print("Configuraciones actualizadas.", flush=True)
    except Exception:
        print("Error durante despliegue. Restaurando respaldo...", flush=True)
        ptero.subir("/mods", nombre, viejo)
        ptero.escribir("/config/EasyAuth/main.conf", easyauth_viejo)
        ptero.escribir("/config/cobbreeding/main.json", cobbreeding_viejo)
        ptero.potencia("start")
        raise

    print("Despliegue verificado; iniciando servidor...", flush=True)
    ptero.potencia("start")


if __name__ == "__main__":
    main()

