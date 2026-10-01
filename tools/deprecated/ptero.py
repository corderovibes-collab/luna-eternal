#!/usr/bin/env python3
"""
Cliente minimo de la API de Pterodactyl. Lee las credenciales de `.env`.

Lo usan `constructor.py` y `ciudadela.py`. No tiene nada de este proyecto
dentro a proposito: es la capa de "hablar con el panel" y nada mas.

    ptero.leer(ruta)             contenido de un fichero del servidor
    ptero.escribir(ruta, datos)  lo sobrescribe
    ptero.subir(dir, nom, datos) sube un BINARIO (jars) — ver aviso abajo
    ptero.borrar(dir, [nombres]) borra ficheros
    ptero.listar(directorio)     [(nombre, tamano, es_fichero)]
    ptero.comando("list")        encola un comando en la consola
    ptero.potencia("restart")    start / stop / restart / kill
    ptero.estado()               estado y consumo del servidor

> La API **no devuelve la salida** de un comando: solo lo encola. Para leer lo
> que respondio, usa `rcon.enviar()`, que marca el log antes y despues.

> ⚠️ **`escribir()` NO vale para un jar.** Es el endpoint de edicion de texto:
> el cuerpo viaja como texto y un jar sube con bytes cambiados, sin que nadie
> avise. El sintoma llega mucho despues y no se parece a la causa —
> `Unexpected end of ZLIB input stream` al arrancar. Para binarios, `subir()`,
> que usa el endpoint de subida real (URL firmada + multipart).
"""
import hashlib
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


def listar(directorio="/", servidor=None):
    d = pedir("GET", f"/files/list?directory={urllib.parse.quote(directorio)}",
              servidor=servidor)
    return [(a["attributes"]["name"], a["attributes"]["size"],
             a["attributes"]["is_file"]) for a in d["data"]]


def borrar(directorio, nombres, servidor=None):
    return pedir("POST", "/files/delete",
                 {"root": directorio, "files": list(nombres)}, servidor=servidor)


def subir(directorio, nombre, datos, servidor=None):
    """Sube un fichero BINARIO. Devuelve el tamano que reporta el panel.

    Va en dos pasos porque asi lo hace Pterodactyl: primero se pide una URL
    firmada al panel, y despues se sube el fichero al NODO, que es otra maquina
    distinta. Es el mismo camino que usa el boton "Upload" de la web, con lo
    cual es tambien el unico probado contra binarios grandes.

    Se comprueba el tamano al terminar en vez de dar por buena la subida: un
    jar truncado no se queja al subir, se queja al arrancar el servidor.

    ⚠⚠ BORRA EL FICHERO ANTERIOR ANTES DE SUBIR, Y ESO NO ES OPCIONAL.

    SOBRESCRIBIR UN JAR LO CORROMPE. No da error al subir, y el tamano hasta
    coincide: el fallo aparece mas tarde, al CARGAR UNA CLASE, con

        java.util.zip.ZipException: ZipFile invalid LOC header (bad signature)

    que no se parece en nada a "el jar esta mal".

    El 2026-08-22 costo que nadie pudiera entrar al servidor. El sintoma fue
    "Datos del jugador no valido" en la pantalla del jugador, porque la clase que
    fallaba --`Difusion`-- se carga PEREZOSAMENTE al conectarse alguien: el
    servidor arrancaba perfecto y solo reventaba al entrar. Diagnosticarlo desde
    el mensaje del cliente es imposible.

    ⚠ EL BORRADO ESTABA EN `desplegar.py` Y NO AQUI, y por eso paso: se llamo a
      esta funcion directamente --para encadenar subida y publicacion en un
      comando-- y se perdio la proteccion. Una salvaguarda que vive en UN
      llamante protege a ese llamante; puesta en el primitivo, protege a todos.
    """
    for n, _tam, es_fichero in listar(directorio, servidor=servidor):
        if n == nombre and es_fichero:
            borrar(directorio, [nombre], servidor=servidor)
            break

    url = pedir("GET", "/files/upload", servidor=servidor)["attributes"]["url"]
    frontera = "----LunaEternal" + hashlib.sha1(datos[:4096]).hexdigest()[:16]
    cuerpo = (
        f"--{frontera}\r\n"
        f'Content-Disposition: form-data; name="files"; filename="{nombre}"\r\n'
        f"Content-Type: application/java-archive\r\n\r\n"
    ).encode() + datos + f"\r\n--{frontera}--\r\n".encode()

    req = urllib.request.Request(
        f"{url}&directory={urllib.parse.quote(directorio)}", data=cuerpo,
        headers={"Content-Type": f"multipart/form-data; boundary={frontera}",
                 "User-Agent": "Mozilla/5.0"}, method="POST")
    with urllib.request.urlopen(req) as r:
        r.read()

    for n, tam, es_fichero in listar(directorio, servidor=servidor):
        if n == nombre and es_fichero:
            return tam
    raise RuntimeError(f"{nombre} no aparece en {directorio} despues de subirlo")


def comando(cmd):
    return pedir("POST", "/command", {"command": cmd})


def potencia(senal):
    """start · stop · restart · kill. `kill` es el unico que saca al servidor
    de un `stopping` colgado (CLAUDE.md: migracion a medias)."""
    return pedir("POST", "/power", {"signal": senal})


def estado():
    return pedir("GET", "/resources")["attributes"]
