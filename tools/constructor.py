#!/usr/bin/env python3
"""
Da de alta (o de baja) a un constructor en el servidor.

POR QUE ESTO NO SE HACE A MANO

Un constructor necesita estar en DOS sitios, y con el NIVEL correcto:

    whitelist.json   para poder entrar
    ops.json         nivel 2, para que Axiom y WorldEdit le dejen trabajar

Y ahi esta la trampa: **`/op <nombre>` da nivel 4**, no 2. Con nivel 4 un
constructor puede apagar el servidor, banear o quitarle el OP al dueno — sin
querer, escribiendo un comando de mas. Nivel 2 da creativo, `/tp`, WorldEdit y
Axiom completo, y nada de lo otro.

    nivel 2   creativo, /tp, //paste, Axiom   <- constructores
    nivel 3   ademas /ban, /kick, /op         <- moderacion
    nivel 4   ademas /stop, /save-all         <- solo el dueno

Verificado leyendo los jars, no supuesto:
    Axiom 5.4.2      AxiomServer.isOp()  ->  hasPermissionLevel(2)
    WorldEdit 7.3.8  VanillaPermissionsProvider  ->  isOperator(perfil)

EL UUID

Con `online-mode=false` el UUID es el OFFLINE, que se calcula del nombre:
UUID v3 sobre "OfflinePlayer:<nombre>". Se calcula aqui en vez de pedirselo a
Mojang, porque una cuenta no premium no existe en Mojang.

> El nombre ES la identidad. Quien se lo cambie aparece como otra persona:
> pierde permisos, inventario y progreso. Que cada uno elija bien a la primera.

Uso:
    python tools/constructor.py --listar
    python tools/constructor.py --anadir Pepe Ana Luis
    python tools/constructor.py --quitar Pepe
"""
import argparse
import hashlib
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent

# Nivel de operador de un constructor. NO subir esto (ver cabecera).
NIVEL_CONSTRUCTOR = 2


def cargar_env() -> None:
    env = RAIZ / ".env"
    if not env.exists():
        raise SystemExit("Falta .env. Copia .env.example y rellena PTERO_KEY.")
    for linea in env.read_text(encoding="utf-8").splitlines():
        linea = linea.strip()
        if linea and not linea.startswith("#") and "=" in linea:
            k, v = linea.split("=", 1)
            os.environ.setdefault(k.strip(), v.strip())


def api(metodo, ruta, cuerpo=None, crudo=False):
    panel = os.environ["PTERO_PANEL"].rstrip("/")
    servidor = os.environ["PTERO_SERVER_DEV"]
    url = f"{panel}/api/client/servers/{servidor}{ruta}"

    # Cloudflare rechaza el User-Agent por defecto de urllib con un
    # "error 1010: browser signature banned", y el error PARECE una clave mala.
    cab = {"Authorization": f"Bearer {os.environ['PTERO_KEY']}",
           "Accept": "application/json",
           "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                         "AppleWebKit/537.36 (KHTML, like Gecko) "
                         "Chrome/126.0.0.0 Safari/537.36"}
    datos = None
    if cuerpo is not None:
        if isinstance(cuerpo, str):
            datos, cab["Content-Type"] = cuerpo.encode(), "text/plain"
        else:
            datos, cab["Content-Type"] = json.dumps(cuerpo).encode(), "application/json"

    req = urllib.request.Request(url, data=datos, headers=cab, method=metodo)
    try:
        with urllib.request.urlopen(req) as r:
            b = r.read()
            return b if crudo else (json.loads(b) if b else {})
    except urllib.error.HTTPError as e:
        raise SystemExit(f"HTTP {e.code} en {metodo} {ruta}\n{e.read().decode()[:300]}")


def leer_json(ruta, por_defecto):
    try:
        texto = api("GET", f"/files/contents?file={urllib.parse.quote(ruta)}",
                    crudo=True).decode("utf-8")
        return json.loads(texto) if texto.strip() else por_defecto
    except SystemExit:
        return por_defecto


def escribir_json(ruta, datos):
    api("POST", f"/files/write?file={urllib.parse.quote(ruta)}",
        json.dumps(datos, indent=2))


def uuid_offline(nombre: str) -> str:
    """UUID v3 sobre "OfflinePlayer:<nombre>", igual que lo hace el servidor.

    Contrastado con el UUID real de TheJuanCE que ya estaba en ops.json:
    432ef323-8ac3-3ba3-8175-aaf88c5589cf."""
    h = bytearray(hashlib.md5(f"OfflinePlayer:{nombre}".encode("utf-8")).digest())
    h[6] = (h[6] & 0x0F) | 0x30      # version 3
    h[8] = (h[8] & 0x3F) | 0x80      # variante RFC 4122
    s = h.hex()
    return f"{s[:8]}-{s[8:12]}-{s[12:16]}-{s[16:20]}-{s[20:]}"


def listar():
    ops = leer_json("/ops.json", [])
    lista = leer_json("/whitelist.json", [])
    nombres = {e["name"] for e in lista} | {e["name"] for e in ops}

    print(f"{'nombre':<20} {'whitelist':<11} {'nivel':<6}")
    print("-" * 40)
    for n in sorted(nombres, key=str.lower):
        en_lista = "si" if any(e["name"] == n for e in lista) else "NO"
        op = next((e for e in ops if e["name"] == n), None)
        nivel = str(op["level"]) if op else "-"
        aviso = "  <- nivel 4: es el dueno" if op and op["level"] == 4 else ""
        print(f"{n:<20} {en_lista:<11} {nivel:<6}{aviso}")


def anadir(nombres):
    ops = leer_json("/ops.json", [])
    lista = leer_json("/whitelist.json", [])

    for nombre in nombres:
        uid = uuid_offline(nombre)

        if not any(e["name"] == nombre for e in lista):
            lista.append({"uuid": uid, "name": nombre})
            print(f"  {nombre}: anadido a la whitelist")

        existente = next((e for e in ops if e["name"] == nombre), None)
        if existente:
            # No se toca a quien ya tiene un nivel mayor: seria degradar al
            # dueno por escribir su nombre sin darse cuenta.
            if existente["level"] >= NIVEL_CONSTRUCTOR:
                print(f"  {nombre}: ya es operador de nivel {existente['level']}, "
                      f"no se toca")
            else:
                existente["level"] = NIVEL_CONSTRUCTOR
                print(f"  {nombre}: subido a nivel {NIVEL_CONSTRUCTOR}")
        else:
            ops.append({"uuid": uid, "name": nombre,
                        "level": NIVEL_CONSTRUCTOR, "bypassesPlayerLimit": False})
            print(f"  {nombre}: operador de nivel {NIVEL_CONSTRUCTOR}  ({uid})")

    escribir_json("/whitelist.json", lista)
    escribir_json("/ops.json", ops)
    recargar()


def quitar(nombres):
    ops = [e for e in leer_json("/ops.json", []) if e["name"] not in nombres]
    lista = [e for e in leer_json("/whitelist.json", []) if e["name"] not in nombres]
    escribir_json("/ops.json", ops)
    escribir_json("/whitelist.json", lista)
    for n in nombres:
        print(f"  {n}: fuera de whitelist y de ops")
    recargar()


def recargar():
    """El servidor lee esos ficheros al arrancar. Sin esto habria que
    reiniciar, y reiniciar por dar de alta a alguien es absurdo."""
    api("POST", "/command", {"command": "whitelist reload"})
    print("\nwhitelist recargada en caliente.")
    print("OJO: ops.json SI necesita reinicio para que el servidor lo relea.")


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--listar", action="store_true")
    g.add_argument("--anadir", nargs="+", metavar="NOMBRE")
    g.add_argument("--quitar", nargs="+", metavar="NOMBRE")
    args = ap.parse_args()

    cargar_env()
    if args.listar:
        listar()
    elif args.anadir:
        anadir(args.anadir)
    else:
        quitar(args.quitar)


if __name__ == "__main__":
    main()
