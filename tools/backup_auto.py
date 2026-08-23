#!/usr/bin/env python3
"""
Copia de seguridad AUTOMATICA y ORGANIZADA: servidor -> disco -> Google Drive.

Lo lanza el Programador de tareas los LUNES, MIERCOLES, VIERNES y DOMINGOS.

COMO QUEDA ORGANIZADO

Una carpeta por copia, con la fecha delante para que ordene sola, y el dia de
la semana detras para poder decir "la del viernes" sin mirar un calendario:

    LunaEternal-Backups/
      2026/
        2026-08/
          2026-08-23_domingo/
              mundo.tar.gz        el mundo entero + configuracion
              bd.sql              las 14 tablas de MariaDB
              jars/               lunaeternal.jar y lunaneon.jar
              RESUMEN.txt         que hay dentro y cuanto pesa
          2026-08-24_lunes/
          ...

El ano y el mes existen porque a cuatro copias por semana son mas de 200 al
ano, y una lista plana de 200 carpetas no se mira: se ignora.

⚠ RESUMEN.txt SE ESCRIBE SIEMPRE, y no es un adorno. Dice cuantos ficheros de
  region lleva cada dimension y cuantas filas cada tabla. Sirve para dos cosas
  que solo se agradecen el dia malo: elegir QUE copia restaurar sin bajarse
  varias de 47 MB, y notar que una copia salio a medias --si un dia la
  ciudadela pasa de 58 regiones a 3, se ve leyendo el fichero.

⚠⚠ POR QUE SE SUBE FUERA Y NO BASTA CON `backups/`

  Una copia en el mismo disco que el original no es una copia de seguridad: es
  una segunda copia del mismo punto de fallo. Y antes de ese hay otro: el plan
  del hosting da CERO ranuras de backup (`feature_limits.backups = 0`), asi que
  si el proveedor pierde el disco no hay nada que restaurar.

⚠ EL PERMISO DE DRIVE ES `drive.file`. rclone solo ve y toca los ficheros que
  el mismo ha creado; no puede leer ni borrar nada mas de tu Drive. Para un
  proceso que corre solo y sin vigilancia, esa es la diferencia entre un fallo
  que estropea una copia y un fallo que te vacia el Drive.

RETENCION

  local  21 dias   (~12 copias: la ventana de trabajo)
  Drive  365 dias  (~208 copias de ~92 MB = 19 GB sobre 5 TB)

  Se borra por la FECHA DEL NOMBRE, nunca por la fecha del fichero: copiar o
  mover un fichero le cambia la mtime, y por ahi se borra lo que no toca.

Uso:
    python tools/backup_auto.py              ciclo completo
    python tools/backup_auto.py --sin-subir  solo local
    python tools/backup_auto.py --probar     comprueba que todo esta listo
    python tools/backup_auto.py --inventario que copias hay, aqui y en Drive
"""
import argparse
import re
import shutil
import subprocess
import sys
import tarfile
import time
from datetime import datetime, timedelta
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
DESTINO = RAIZ / "backups"
PY = sys.executable

REMOTO = "luna-drive"
BASE_DRIVE = "LunaEternal-Backups"

DIAS_LOCAL = 21
DIAS_DRIVE = 365

DIAS_SEMANA = ["lunes", "martes", "miercoles", "jueves",
               "viernes", "sabado", "domingo"]

CANDIDATOS_RCLONE = [
    Path(r"C:\Users\JUAN\AppData\Local\Microsoft\WinGet\Packages"
         r"\Rclone.Rclone_Microsoft.Winget.Source_8wekyb3d8bbwe"
         r"\rclone-v1.75.0-windows-amd64\rclone.exe"),
]


def rclone():
    for c in CANDIDATOS_RCLONE:
        if c.exists():
            return str(c)
    hallado = shutil.which("rclone")
    if hallado:
        return hallado
    base = Path.home() / "AppData/Local/Microsoft/WinGet/Packages"
    for p in base.glob("Rclone.Rclone*/**/rclone.exe"):
        return str(p)
    return None


def log(m):
    print("[" + time.strftime("%H:%M:%S") + "] " + m, flush=True)


def mb(b):
    return "{:,.1f} MB".format(b / 1048576)


def correr(args, titulo, silencioso=False):
    log(titulo)
    r = subprocess.run(args, capture_output=True, text=True,
                       encoding="utf-8", errors="replace")
    if not silencioso:
        for l in (r.stdout or "").splitlines():
            print("    " + l, flush=True)
    if r.returncode != 0:
        for l in (r.stderr or "").splitlines()[-15:]:
            print("    ! " + l, flush=True)
        raise SystemExit("FALLO en: " + titulo)
    return r


def nombre_carpeta(cuando=None):
    d = cuando or datetime.now()
    return d.strftime("%Y-%m-%d") + "_" + DIAS_SEMANA[d.weekday()]


def ruta_drive(cuando=None):
    d = cuando or datetime.now()
    return "{}/{}/{}/{}".format(BASE_DRIVE, d.strftime("%Y"),
                                d.strftime("%Y-%m"), nombre_carpeta(d))


FECHA = re.compile(r"(\d{4})-(\d{2})-(\d{2})")


def fecha_del_nombre(n):
    m = FECHA.search(n)
    if not m:
        return None
    try:
        return datetime(int(m.group(1)), int(m.group(2)), int(m.group(3)))
    except ValueError:
        return None


def resumen(carpeta):
    """Escribe RESUMEN.txt mirando DENTRO de lo que se acaba de copiar.

    No se fia de lo que deberia haber: abre el tar y cuenta.
    """
    lineas = []
    lineas.append("COPIA DE SEGURIDAD  ·  PokeReport: Luna Eternal")
    lineas.append("Generada el " + time.strftime("%Y-%m-%d %H:%M:%S"))
    lineas.append("=" * 58)
    lineas.append("")

    tar = carpeta / "mundo.tar.gz"
    if tar.exists():
        lineas.append("MUNDO   mundo.tar.gz   " + mb(tar.stat().st_size))
        try:
            t = tarfile.open(tar, "r:gz")
            regiones, pesos, otros = {}, {}, {}
            for m in t.getmembers():
                n = m.name
                if n.endswith((".mca", ".mcc")):
                    if "dimensions/lunaeternal/" in n:
                        d = n.split("dimensions/lunaeternal/")[1].split("/")[0]
                    elif "/DIM-1/" in n:
                        d = "nether"
                    elif "/DIM1/" in n:
                        d = "end"
                    else:
                        d = "overworld"
                    regiones[d] = regiones.get(d, 0) + 1
                    pesos[d] = pesos.get(d, 0) + m.size
                else:
                    for pat, et in (("world/playerdata/", "playerdata"),
                                    ("world/pokemon/", "pokemon"),
                                    ("world/pokedex/", "pokedex"),
                                    ("world/cobblemonplayerdata/", "cobblemon"),
                                    ("config/", "config")):
                        if pat in n and not n.endswith("/"):
                            otros[et] = otros.get(et, 0) + 1
            lineas.append("  ficheros de region (LAS CONSTRUCCIONES):")
            for d in sorted(regiones, key=lambda k: -pesos[k]):
                lineas.append("     {:<12}{:>4} ficheros  {:>10}".format(
                    d, regiones[d], mb(pesos[d])))
            lineas.append("  estado de los jugadores:")
            for k in sorted(otros):
                lineas.append("     {:<12}{:>4} ficheros".format(k, otros[k]))
        except Exception as e:
            lineas.append("  no se pudo inspeccionar: " + str(e))
        lineas.append("")

    bd = carpeta / "bd.sql"
    if bd.exists():
        lineas.append("BASE DE DATOS   bd.sql   {:,.1f} KB".format(
            bd.stat().st_size / 1024))
        try:
            txt = bd.read_text(encoding="utf-8", errors="replace")
            tablas = re.findall(r"CREATE TABLE `([^`]+)`", txt)
            filas = {}
            for t_ in tablas:
                cuenta = 0
                for bloque in re.findall(
                        r"INSERT INTO `" + re.escape(t_) + r"`[^;]+;", txt):
                    cuenta += bloque.count("),\n") + 1
                filas[t_] = cuenta
            lineas.append("  {} tablas:".format(len(tablas)))
            for t_ in tablas:
                lineas.append("     {:<28}{:>7} filas".format(t_, filas.get(t_, 0)))
        except Exception as e:
            lineas.append("  no se pudo inspeccionar: " + str(e))
        lineas.append("")

    jars = carpeta / "jars"
    if jars.is_dir():
        lineas.append("JARS PROPIOS")
        for j in sorted(jars.glob("*.jar")):
            lineas.append("     {:<34}{:>10}".format(j.name, mb(j.stat().st_size)))
        lineas.append("")

    total = sum(p.stat().st_size for p in carpeta.rglob("*") if p.is_file())
    lineas.append("=" * 58)
    lineas.append("TOTAL DE ESTA COPIA: " + mb(total))
    lineas.append("")
    lineas.append("PARA RESTAURAR")
    lineas.append("  1. Para el servidor desde el panel.")
    lineas.append("  2. Sube mundo.tar.gz y descomprimelo en la raiz,")
    lineas.append("     reemplazando la carpeta world/.")
    lineas.append("  3. La base de datos:")
    lineas.append("       mysql -h HOST -u USUARIO -p BASE < bd.sql")
    lineas.append("  4. Arranca. Los mods se rebajan solos del manifiesto.")
    lineas.append("")
    lineas.append("  ⚠ Las dos mitades van JUNTAS. El disco tiene las")
    lineas.append("    construcciones; la base tiene monedas, vias, Pokedex,")
    lineas.append("    kits, misiones, GTS y cosmeticos (D-009). Restaurar una")
    lineas.append("    sola deja el servidor a medias.")

    (carpeta / "RESUMEN.txt").write_text("\n".join(lineas) + "\n",
                                         encoding="utf-8")
    return total


def limpiar_local(dias):
    if not DESTINO.exists():
        return 0
    corte = datetime.now() - timedelta(days=dias)
    n = 0
    for p in DESTINO.iterdir():
        if p.name == "backup.log":
            continue
        f = fecha_del_nombre(p.name)
        if f and f < corte:
            shutil.rmtree(p, ignore_errors=True) if p.is_dir() else p.unlink(missing_ok=True)
            log("  local: borrado " + p.name)
            n += 1
    return n


def limpiar_drive(rc, dias):
    corte = datetime.now() - timedelta(days=dias)
    r = subprocess.run([rc, "lsf", "-R", "--dirs-only", REMOTO + ":" + BASE_DRIVE],
                       capture_output=True, text=True, encoding="utf-8",
                       errors="replace")
    if r.returncode != 0:
        return 0
    n = 0
    for linea in (r.stdout or "").splitlines():
        linea = linea.strip().rstrip("/")
        hoja = linea.split("/")[-1]
        # Solo carpetas de copia (llevan el dia de la semana detras)
        if not any(hoja.endswith("_" + d) for d in DIAS_SEMANA):
            continue
        f = fecha_del_nombre(hoja)
        if f and f < corte:
            subprocess.run([rc, "purge", REMOTO + ":" + BASE_DRIVE + "/" + linea],
                           capture_output=True)
            log("  drive: borrado " + hoja)
            n += 1
    return n


def inventario(rc):
    print("=" * 62)
    print("  COPIAS LOCALES  (" + str(DESTINO) + ")")
    print("=" * 62)
    if DESTINO.exists():
        carpetas = sorted(p for p in DESTINO.iterdir()
                          if p.is_dir() and fecha_del_nombre(p.name))
        for c in carpetas:
            t = sum(p.stat().st_size for p in c.rglob("*") if p.is_file())
            print("  {:<28}{:>12}".format(c.name, mb(t)))
        if not carpetas:
            print("  (ninguna)")
    if rc:
        print()
        print("=" * 62)
        print("  COPIAS EN GOOGLE DRIVE")
        print("=" * 62)
        r = subprocess.run([rc, "lsf", "-R", "--dirs-only",
                            REMOTO + ":" + BASE_DRIVE],
                           capture_output=True, text=True, encoding="utf-8",
                           errors="replace")
        hojas = [l.strip().rstrip("/").split("/")[-1]
                 for l in (r.stdout or "").splitlines()]
        hojas = sorted(set(h for h in hojas
                           if any(h.endswith("_" + d) for d in DIAS_SEMANA)))
        for h in hojas:
            print("  " + h)
        print("  total: {} copias".format(len(hojas)))


def comprobar(rc):
    print("=" * 62)
    print("  COMPROBACION")
    print("=" * 62)
    ok = True
    print("  python   : " + PY)
    if not rc:
        print("  rclone   : NO ENCONTRADO")
        ok = False
    else:
        print("  rclone   : " + rc)
        r = subprocess.run([rc, "listremotes"], capture_output=True, text=True)
        if REMOTO + ":" in (r.stdout or "").split():
            print("  remoto   : " + REMOTO + ": configurado")
            r2 = subprocess.run([rc, "about", REMOTO + ":"],
                                capture_output=True, text=True)
            if r2.returncode == 0:
                for l in (r2.stdout or "").splitlines()[:4]:
                    print("      " + l)
            else:
                print("      el remoto no responde. Reautoriza:")
                print("        rclone config reconnect " + REMOTO + ":")
                ok = False
        else:
            print("  remoto   : NO configurado")
            ok = False
    for t in ("backup.py", "backup_bd.py"):
        e = (RAIZ / "tools" / t).exists()
        print("  {:<9}: {}".format(t, "ok" if e else "FALTA"))
        ok = ok and e
    print("  proximas copias: lunes, miercoles, viernes y domingo")
    print("=" * 62)
    print("  LISTO" if ok else "  HAY COSAS SIN RESOLVER")
    return ok


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--sin-subir", action="store_true")
    ap.add_argument("--probar", action="store_true")
    ap.add_argument("--inventario", action="store_true")
    args = ap.parse_args()

    rc = rclone()
    if args.probar:
        raise SystemExit(0 if comprobar(rc) else 1)
    if args.inventario:
        inventario(rc)
        return

    t0 = time.time()
    ahora = datetime.now()
    carpeta = DESTINO / nombre_carpeta(ahora)
    print("=" * 62)
    print("  COPIA DE SEGURIDAD  ·  " + nombre_carpeta(ahora))
    print("=" * 62)

    DESTINO.mkdir(parents=True, exist_ok=True)
    antes = set(p.name for p in DESTINO.iterdir())

    # --- 1 y 2: sacar los datos del servidor -----------------------------
    correr([PY, str(RAIZ / "tools" / "backup.py")], "1/5  mundo y configuracion")
    correr([PY, str(RAIZ / "tools" / "backup_bd.py")], "2/5  base de datos")
    correr([PY, str(RAIZ / "tools" / "backup.py"), "--nuestros-jars"],
           "3/5  jars propios")

    # --- 3: ordenarlo en la carpeta del dia ------------------------------
    log("4/5  organizando en " + carpeta.name)
    carpeta.mkdir(parents=True, exist_ok=True)
    (carpeta / "jars").mkdir(exist_ok=True)
    for p in sorted(set(p.name for p in DESTINO.iterdir()) - antes):
        origen = DESTINO / p
        if origen == carpeta:
            continue
        if p.endswith(".tar.gz"):
            shutil.move(str(origen), str(carpeta / "mundo.tar.gz"))
        elif p.endswith(".sql"):
            shutil.move(str(origen), str(carpeta / "bd.sql"))
        elif origen.is_dir() and p.startswith("jars-propios"):
            for j in origen.glob("*.jar"):
                shutil.move(str(j), str(carpeta / "jars" / j.name))
            shutil.rmtree(origen, ignore_errors=True)
    total = resumen(carpeta)
    log("     " + mb(total) + " en " + carpeta.name)

    # --- 4: subir --------------------------------------------------------
    if args.sin_subir:
        log("5/5  subida OMITIDA (--sin-subir)")
    elif not rc:
        log("5/5  rclone no encontrado: la copia se queda SOLO en este disco")
    else:
        destino_remoto = REMOTO + ":" + ruta_drive(ahora)
        correr([rc, "copy", str(carpeta), destino_remoto,
                "--retries", "5", "--low-level-retries", "20",
                "--drive-chunk-size", "32M", "--stats-one-line"],
               "5/5  subiendo a " + ruta_drive(ahora))
        # ⚠ Y SE COMPRUEBA POR HASH. Subir no es lo mismo que haber subido:
        # una copia que llego a medias pesa distinto y no lo dice nadie.
        v = subprocess.run([rc, "check", str(carpeta), destino_remoto, "--one-way"],
                           capture_output=True, text=True, encoding="utf-8",
                           errors="replace")
        salida = (v.stderr or "") + (v.stdout or "")
        if "0 differences found" in salida:
            log("     verificado por hash: identico ✔")
        else:
            log("     ⚠ LA VERIFICACION NO CUADRA. Revisa la subida.")
            for l in salida.splitlines()[-6:]:
                print("       " + l)

    log("limpieza")
    limpiar_local(DIAS_LOCAL)
    if rc and not args.sin_subir:
        limpiar_drive(rc, DIAS_DRIVE)

    print("=" * 62)
    print("  TERMINADO en {:,.0f} s".format(time.time() - t0))
    print("=" * 62)


if __name__ == "__main__":
    main()
