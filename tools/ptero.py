#!/usr/bin/env python3
"""
Cliente minimo de la API de Pterodactyl. Lee las credenciales de `.env`.

Lo usan `constructor.py` y `ciudadela.py`. No tiene nada de este proyecto
dentro a proposito: es la capa de "hablar con el panel" y nada mas.

    ptero.leer(ruta)             contenido de un fichero del servidor
    ptero.escribir(ruta, datos)  lo sobrescribe
    ptero.listar(directorio)     [(nombre, tamano, es_fichero)]
    ptero.comando("list")        encola un comando en la consola
    ptero.estado()               estado y consumo del servidor

> La API **no devuelve la salida** de un comando: solo lo encola. Para leer lo
> que respondio, usa `rcon.enviar()`, que marca el log antes y despues.
"""
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent

for linea in (RAIZ / ".env").read_text(encoding="utf-8").splitlines():
    linea = linea.strip()
    if linea and not linea.startswith("#") and "=" in linea:
        k, v = linea.split("=", 1)
        os.environ.setdefault(k.strip(), v.strip())

PANEL = os.environ["PTERO_PANEL"].rstrip("/")
KEY = os.environ["PTERO_KEY"]
DEV = os.environ["PTERO_SERVER_DEV"]


def pedir(metodo, ruta, cuerpo=None, crudo=False, servidor=None):
    url = f"{PANEL}/api/client/servers/{servidor or DEV}{ruta}"
    datos = None
    # Cloudflare bloquea el User-Agent por defecto de urllib con un
    # "error 1010: browser signature banned". No es que la clave sea mala:
    # es que la peticion ni llega al panel.
    cab = {"Authorization": f"Bearer {KEY}", "Accept": "application/json",
           "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                         "AppleWebKit/537.36 (KHTML, like Gecko) "
                         "Chrome/126.0.0.0 Safari/537.36"}
    if cuerpo is not None:
        if isinstance(cuerpo, (bytes, str)):
            datos = cuerpo.encode() if isinstance(cuerpo, str) else cuerpo
            cab["Content-Type"] = "text/plain"
        else:
            datos = json.dumps(cuerpo).encode()
            cab["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=datos, headers=cab, method=metodo)
    try:
        with urllib.request.urlopen(req) as r:
            b = r.read()
            if crudo:
                return b
            return json.loads(b) if b else {}
    except urllib.error.HTTPError as e:
        print(f"HTTP {e.code} en {metodo} {ruta}\n{e.read().decode()[:400]}",
              file=sys.stderr)
        raise


def leer(ruta):
    return pedir("GET", f"/files/contents?file={urllib.parse.quote(ruta)}",
                 crudo=True).decode("utf-8", "replace")


def escribir(ruta, contenido):
    return pedir("POST", f"/files/write?file={urllib.parse.quote(ruta)}", contenido)


def listar(directorio="/"):
    d = pedir("GET", f"/files/list?directory={urllib.parse.quote(directorio)}")
    return [(a["attributes"]["name"], a["attributes"]["size"],
             a["attributes"]["is_file"]) for a in d["data"]]


def comando(cmd):
    return pedir("POST", "/command", {"command": cmd})


def estado():
    return pedir("GET", "/resources")["attributes"]
