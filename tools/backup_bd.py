#!/usr/bin/env python3
"""
Vuelca la base de datos MariaDB a un fichero .sql en este ordenador.

POR QUE HACE FALTA, ADEMAS DE LA COPIA DEL MUNDO

Porque **la mitad del juego no esta en el mundo**. D-009 decidio MariaDB como
almacen principal, asi que en el disco del servidor NO hay ni un fichero con:

    las tres monedas y su libro de asientos
    las vias y los oficios de cada jugador
    la Pokedex, los kits, las misiones, las cazas
    los listados del GTS y lo que hay en custodia
    los cosmeticos comprados

Perder la base y conservar el mundo deja un servidor con las construcciones
intactas y a todo el mundo sin nada. Y al reves. Las dos copias son la misma
copia partida en dos, y hay que hacerlas juntas.

POR QUE NO SE USA `mysqldump`

Porque no esta instalado en esta maquina y arrastrarlo entero (el cliente de
MySQL son ~300 MB) para volcar una base pequena no compensa. Se hace por SQL
directo, que ademas deja el fichero exactamente como se quiere.

⚠ LAS CREDENCIALES NO SE ESCRIBEN AQUI NI EN NINGUN SITIO DEL REPOSITORIO.
  Se leen del servidor, de `/config/lunaeternal.properties`, que es la copia
  autoritativa (CLAUDE.md §0). El fichero .sql que sale SI lleva datos de
  jugadores, asi que `backups/` esta en .gitignore y tiene que seguir estando.

Uso:
    python tools/backup_bd.py
    python tools/backup_bd.py --solo-esquema     # sin datos, para comparar
"""
import argparse
import datetime
import sys
import time
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(RAIZ / "tools"))
import ptero  # noqa: E402

try:
    import pymysql
except ImportError:
    raise SystemExit("Falta pymysql.  Instalalo con:\n"
                     "    .toolchain/python/python.exe -m pip install pymysql")

DESTINO = RAIZ / "backups"


def credenciales():
    """Se leen del servidor. Nunca del repositorio."""
    txt = ptero.leer("/config/lunaeternal.properties")
    cfg = {}
    for linea in txt.splitlines():
        linea = linea.strip()
        if not linea or linea.startswith("#"):
            continue
        k, _, v = linea.partition("=")
        cfg[k.strip()] = v.strip()
    faltan = [k for k in ("db.host", "db.port", "db.name", "db.user", "db.password")
              if not cfg.get(k)]
    if faltan:
        raise SystemExit(f"Faltan claves en lunaeternal.properties: {faltan}")
    return cfg


def escapar(v):
    """Convierte un valor de Python a literal SQL."""
    if v is None:
        return "NULL"
    if isinstance(v, bool):
        return "1" if v else "0"
    if isinstance(v, (int, float)):
        return str(v)
    if isinstance(v, (bytes, bytearray)):
        return "0x" + v.hex() if v else "''"
    if isinstance(v, (datetime.datetime, datetime.date, datetime.time)):
        return "'" + str(v) + "'"
    s = str(v)
    s = (s.replace("\\", "\\\\").replace("'", "\\'")
          .replace("\n", "\\n").replace("\r", "\\r").replace("\0", "\\0"))
    return "'" + s + "'"


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--solo-esquema", action="store_true")
    args = ap.parse_args()

    cfg = credenciales()
    marca = time.strftime("%Y-%m-%d-%H%M")
    DESTINO.mkdir(parents=True, exist_ok=True)
    salida = DESTINO / f"luna-bd-{marca}.sql"

    print("=" * 60)
    print(f"  VOLCADO DE LA BASE DE DATOS")
    print(f"  {cfg['db.name']} en {cfg['db.host']}  ->  {salida.name}")
    print("=" * 60)

    con = pymysql.connect(host=cfg["db.host"], port=int(cfg["db.port"]),
                          user=cfg["db.user"], password=cfg["db.password"],
                          database=cfg["db.name"], charset="utf8mb4",
                          cursorclass=pymysql.cursors.Cursor)
    filas_total = 0
    try:
        with con.cursor() as cur, open(salida, "w", encoding="utf-8") as f:
            f.write(f"-- Copia de {cfg['db.name']}\n")
            f.write(f"-- Generada por tools/backup_bd.py el "
                    f"{time.strftime('%Y-%m-%d %H:%M:%S')}\n")
            f.write("-- Para restaurar:\n")
            f.write(f"--   mysql -h HOST -u USUARIO -p {cfg['db.name']} < este.sql\n")
            f.write("--\n-- ⚠ Contiene datos de jugadores. No se sube al repositorio.\n\n")
            f.write("SET FOREIGN_KEY_CHECKS=0;\n")
            f.write("SET NAMES utf8mb4;\n\n")

            cur.execute("SHOW TABLES")
            tablas = [r[0] for r in cur.fetchall()]
            print(f"  {len(tablas)} tablas\n")

            for t in tablas:
                cur.execute(f"SHOW CREATE TABLE `{t}`")
                ddl = cur.fetchone()[1]
                f.write(f"-- ---------- {t} ----------\n")
                f.write(f"DROP TABLE IF EXISTS `{t}`;\n{ddl};\n\n")

                if args.solo_esquema:
                    print(f"    {t:<28} (solo esquema)")
                    continue

                cur.execute(f"SELECT * FROM `{t}`")
                cols = [d[0] for d in cur.description]
                n = 0
                lote = []
                while True:
                    fila = cur.fetchone()
                    if fila is None:
                        break
                    lote.append("(" + ",".join(escapar(v) for v in fila) + ")")
                    n += 1
                    # Por lotes: una sentencia por fila haria el fichero
                    # gigantesco y la restauracion lentisima.
                    if len(lote) >= 200:
                        f.write(f"INSERT INTO `{t}` (`" + "`,`".join(cols) + "`) VALUES\n"
                                + ",\n".join(lote) + ";\n")
                        lote = []
                if lote:
                    f.write(f"INSERT INTO `{t}` (`" + "`,`".join(cols) + "`) VALUES\n"
                            + ",\n".join(lote) + ";\n")
                f.write("\n")
                filas_total += n
                print(f"    {t:<28} {n:>7,} filas")

            f.write("SET FOREIGN_KEY_CHECKS=1;\n")
    finally:
        con.close()

    tam = salida.stat().st_size
    print(f"\n  {filas_total:,} filas en total")
    print(f"  -> {salida}  ({tam/1024:,.1f} KB)")
    print("\n" + "=" * 60)
    print("  ⚠ Este fichero lleva datos de jugadores y NO va al repositorio")
    print("    (`backups/` esta en .gitignore). Guardalo fuera de esta maquina.")
    print("=" * 60)


if __name__ == "__main__":
    main()
