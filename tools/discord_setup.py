#!/usr/bin/env python3
"""
Monta el servidor de Discord de PokeReport: roles, categorias, canales y
permisos.

Sin dependencias: habla con la API REST de Discord por urllib. Instalar
discord.py en esta maquina era arriesgado —Python 3.14 es muy nuevo— y para
crear canales no hace falta una libreria.

  1. Crea el servidor TU a mano en Discord (30 segundos).
  2. Crea una aplicacion en https://discord.com/developers/applications,
     pestaña Bot, copia el token.
  3. Invita al bot con permiso de Administrador (el script imprime el enlace).
  4. Pon el token y el id del servidor en .env  (git-ignorado):
         DISCORD_TOKEN=...
         DISCORD_GUILD=...
  5. python tools/discord_setup.py            <- solo ENSEÑA el plan
     python tools/discord_setup.py --aplicar  <- lo crea de verdad

Es IDEMPOTENTE: lo que ya existe con ese nombre no se toca ni se duplica.
Se puede volver a ejecutar sin miedo.
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

API = "https://discord.com/api/v10"
RAIZ = Path(__file__).resolve().parent.parent


# --------------------------------------------------------------- permisos
# Bits de permiso de Discord. Solo los que se usan aqui.
VER = 1 << 10            # VIEW_CHANNEL
ESCRIBIR = 1 << 11       # SEND_MESSAGES
HILOS = 1 << 15          # CREATE_PUBLIC_THREADS
HILOS_PRIV = 1 << 16
ESCRIBIR_HILO = 1 << 18
ADJUNTAR = 1 << 15       # (ATTACH_FILES = 1<<15 en canales de texto)
REACCIONAR = 1 << 6
HISTORIAL = 1 << 16
CONECTAR = 1 << 20
HABLAR = 1 << 21
ADMIN = 1 << 3
MODERAR = (1 << 1) | (1 << 2) | (1 << 13) | (1 << 40)   # kick, ban, gestionar
GESTIONAR_MSJ = 1 << 13


# ----------------------------------------------------------------- roles
#
# El orden IMPORTA: Discord los apila de abajo arriba, asi que se crean de
# menos a mas poder y el ultimo queda arriba.
ROLES = [
    # nombre,            color,     separado, permisos extra
    ("🤖 Bots",          0x99AAB5,  False,    0),
    ("💎 Booster",       0xF47FFF,  True,     0),
    ("⭐ Beta Tester",   0xFFD166,  True,     0),
    ("🔨 Constructor",   0x4EC9B0,  True,     0),
    ("🛡️ Moderador",     0x5865F2,  True,     MODERAR | GESTIONAR_MSJ),
    ("🌙 Fundador",      0x9B6BFF,  True,     ADMIN),
]


# --------------------------------------------------------------- canales
#
# FASE 1 es lo que se abre AHORA. Menos es mas: un servidor vacio con
# veinticinco canales parece muerto, y con seis parece que acaba de empezar.
# Los de FASE 2 se crean con --fase2 cuando haya gente y servidor jugable.

def texto(nombre, topic, *, escribir=True, hilos=False, tipo=0):
    return {"nombre": nombre, "topic": topic, "tipo": tipo,
            "escribir": escribir, "hilos": hilos}


FASE1 = [
    ("📌 INFORMACIÓN", [
        texto("bienvenida", "Empieza por aquí. Qué es Luna Eternal y cómo "
              "entrar cuando abramos.", escribir=False),
        # tipo 5 = canal de anuncios: otros servidores pueden SEGUIRLO y ver
        # nuestras noticias. Es publicidad gratis y solo funciona con este tipo.
        texto("anuncios", "Novedades importantes del proyecto.",
              escribir=False, tipo=5),
        texto("actualizaciones", "Qué se ha construido esta semana. El diario "
              "de desarrollo.", escribir=False),
    ]),
    ("💬 COMUNIDAD", [
        texto("general", "Charla general. Sé majo."),
        texto("sugerencias", "¿Qué te gustaría ver en el servidor?",
              hilos=True),
        texto("multimedia", "Capturas, vídeos y arte."),
    ]),
    ("🛠️ SOPORTE", [
        texto("ayuda", "¿Dudas? Pregunta aquí.", hilos=True),
    ]),
    ("🔒 STAFF", [
        texto("staff", "Coordinación interna."),
        texto("registro", "Registro automático de moderación.",
              escribir=False),
    ]),
]

FASE2 = [
    ("🎮 EL JUEGO", [
        texto("guías", "Cómo empezar, vías, gimnasios.", escribir=False),
        texto("comercio", "Ofertas del GTS y trueques.", hilos=True),
        texto("capturas", "Presume de shiny."),
        texto("busco-equipo", "Encuentra con quién jugar."),
    ]),
    ("🐛 REPORTES", [
        texto("reportar-bug", "Algo no funciona: cuéntalo aquí.", hilos=True),
    ]),
]

# Canales de voz. Sobran al principio: uno general y uno de staff.
VOZ = [("🔊 VOZ", [("Sala general", False), ("Staff", True)])]


# ------------------------------------------------------------------ API

class Discord:
    def __init__(self, token, guild, aplicar):
        self.token = token
        self.guild = guild
        self.aplicar = aplicar

    def _req(self, metodo, ruta, cuerpo=None):
        url = API + ruta
        datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
        req = urllib.request.Request(url, data=datos, method=metodo)
        req.add_header("Authorization", f"Bot {self.token}")
        req.add_header("Content-Type", "application/json")
        req.add_header("User-Agent", "PokeReportSetup (luna-eternal, 0.1)")
        for intento in range(5):
            try:
                with urllib.request.urlopen(req, timeout=30) as r:
                    return json.loads(r.read() or b"null")
            except urllib.error.HTTPError as e:
                # 429 = demasiadas peticiones. Discord dice cuanto esperar.
                if e.code == 429:
                    espera = json.loads(e.read()).get("retry_after", 2)
                    time.sleep(float(espera) + 0.3)
                    continue
                detalle = e.read().decode("utf-8", "replace")[:400]
                raise SystemExit(f"Discord respondio {e.code} en {ruta}\n{detalle}")
        raise SystemExit("Discord sigue limitando las peticiones; reintenta luego")

    def get(self, ruta):
        return self._req("GET", ruta)

    def post(self, ruta, cuerpo):
        if not self.aplicar:
            return {"id": "(simulado)", **cuerpo}
        return self._req("POST", ruta, cuerpo)


def main() -> None:
    aplicar = "--aplicar" in sys.argv
    fase2 = "--fase2" in sys.argv

    token = os.environ.get("DISCORD_TOKEN")
    guild = os.environ.get("DISCORD_GUILD")
    env = RAIZ / ".env"
    if env.exists():
        for linea in env.read_text(encoding="utf-8").splitlines():
            if linea.startswith("DISCORD_TOKEN="):
                token = token or linea.split("=", 1)[1].strip()
            if linea.startswith("DISCORD_GUILD="):
                guild = guild or linea.split("=", 1)[1].strip()

    if not token or not guild:
        raise SystemExit(
            "Falta DISCORD_TOKEN o DISCORD_GUILD.\n"
            "Ponlos en .env (que esta git-ignorado):\n"
            "  DISCORD_TOKEN=...\n"
            "  DISCORD_GUILD=...")

    d = Discord(token, guild, aplicar)
    print("MODO: " + ("APLICAR — se va a crear de verdad"
                      if aplicar else "SIMULACION — no se crea nada"))

    yo = d.get("/users/@me")
    print(f"Bot: {yo['username']}  ·  servidor: {guild}\n")

    # --- roles
    existentes = {r["name"]: r for r in d.get(f"/guilds/{guild}/roles")}
    roles = {}
    print("ROLES")
    for nombre, color, separado, permisos in ROLES:
        if nombre in existentes:
            roles[nombre] = existentes[nombre]["id"]
            print(f"  ya existe   {nombre}")
            continue
        r = d.post(f"/guilds/{guild}/roles", {
            "name": nombre, "color": color, "hoist": separado,
            "permissions": str(permisos), "mentionable": False})
        roles[nombre] = r["id"]
        print(f"  creado      {nombre}")

    # --- canales
    canales = {c["name"]: c for c in d.get(f"/guilds/{guild}/channels")}
    plan = FASE1 + (FASE2 if fase2 else [])

    print("\nCANALES")
    for categoria, hijos in plan:
        privada = categoria.startswith("🔒")
        if categoria in canales:
            padre = canales[categoria]["id"]
            print(f"  ya existe   {categoria}")
        else:
            permisos = []
            if privada:
                # @everyone no ve la categoria; el staff si. En Discord el
                # rol @everyone tiene el mismo id que el servidor.
                permisos = [{"id": guild, "type": 0, "deny": str(VER)}]
                for r in ("🛡️ Moderador", "🌙 Fundador"):
                    permisos.append({"id": roles[r], "type": 0,
                                     "allow": str(VER | ESCRIBIR)})
            padre = d.post(f"/guilds/{guild}/channels", {
                "name": categoria, "type": 4,
                "permission_overwrites": permisos})["id"]
            print(f"  creada      {categoria}")

        for ch in hijos:
            if ch["nombre"] in canales:
                print(f"    ya existe   {ch['nombre']}")
                continue
            permisos = []
            if not ch["escribir"] and not privada:
                # Solo lectura: se quita ESCRIBIR a @everyone, no VER. Un
                # canal que no se ve no informa a nadie.
                permisos = [{"id": guild, "type": 0, "deny": str(ESCRIBIR)}]
                for r in ("🛡️ Moderador", "🌙 Fundador"):
                    permisos.append({"id": roles[r], "type": 0,
                                     "allow": str(ESCRIBIR)})
            d.post(f"/guilds/{guild}/channels", {
                "name": ch["nombre"], "type": ch["tipo"],
                "topic": ch["topic"], "parent_id": padre,
                "permission_overwrites": permisos})
            marca = " (solo lectura)" if not ch["escribir"] else ""
            print(f"    creado      {ch['nombre']}{marca}")

    # --- voz
    for categoria, salas in VOZ:
        if categoria in canales:
            padre = canales[categoria]["id"]
            print(f"  ya existe   {categoria}")
        else:
            padre = d.post(f"/guilds/{guild}/channels",
                           {"name": categoria, "type": 4})["id"]
            print(f"  creada      {categoria}")
        for nombre, solo_staff in salas:
            if nombre in canales:
                print(f"    ya existe   {nombre}")
                continue
            permisos = []
            if solo_staff:
                permisos = [{"id": guild, "type": 0, "deny": str(VER)}]
                for r in ("🛡️ Moderador", "🌙 Fundador"):
                    permisos.append({"id": roles[r], "type": 0,
                                     "allow": str(VER | CONECTAR | HABLAR)})
            d.post(f"/guilds/{guild}/channels", {
                "name": nombre, "type": 2, "parent_id": padre,
                "permission_overwrites": permisos})
            print(f"    creado      {nombre}")

    print("\nHecho." if aplicar else
          "\nNada creado. Vuelve a lanzarlo con --aplicar cuando lo veas bien.")


if __name__ == "__main__":
    main()
