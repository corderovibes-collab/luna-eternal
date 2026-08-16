#!/usr/bin/env python3
"""Genera TODO el contenido del mod de bloques (`neon/`): 602 bloques.

    NEON       16 colores x 6 formas                 96
    HORMIGON   3 acabados x 16 colores x 5 formas   240
    METAL      8 aleaciones x 4 acabados x 5 formas 160
    REJILLA    8 aleaciones x 3 formas               24
    VIDRIO     2 acabados x 16 colores x 2 formas    64
    PAVIMENTO  6 tipos x 3 formas                    18

QUE ES ESTO, EN UNA FRASE

Cada bloque necesita entre 3 y 8 ficheros JSON —blockstate, modelos, modelo de
objeto, loot table— mas su nombre en dos idiomas y sus tags. A mano son casi
cuatro mil ficheros y un error tipografico invisible hasta que un constructor
ve un cubo negro y morado en mitad de la plaza.

    UNA SOLA FUENTE DE VERDAD:  las tablas de `tools/bloques/`.

De ahi sale todo, incluidos `Paleta.java` y `Catalogo.java`. El mod NO tiene la
lista de materiales escrita en Java: la lee de aqui. Asi es imposible que un
material exista en el codigo y no en las texturas, que es exactamente el fallo
que produce el cubo negro y morado.

POR QUE UN SOLO PUNTO DE ENTRADA

Porque las cinco familias escriben en el MISMO arbol de recursos y comparten
tres ficheros unicos: los dos idiomas y los tags. Con un generador por familia,
el ultimo en ejecutarse pisaba a los demas y los bloques de las primeras se
quedaban sin nombre. Antes esto vivia en `tools/gen_neon.py`, que borraba el
arbol entero al empezar; ese borrado es ahora el primer paso de aqui y el
unico.

Uso:
    python tools/gen_bloques.py             # regenera neon/src/main/resources
    python tools/gen_bloques.py --verificar # cruza bloques, modelos y texturas
    python tools/gen_bloques.py --listar    # solo imprime los IDs que generaria
    python tools/gen_bloques.py --maqueta   # lamina de contacto de las texturas
"""
import argparse
import json
import re
import shutil
from pathlib import Path

from PIL import Image

from bloques import ciudad, comun, neon
from bloques.comun import JAVA, NS, RAIZ, RES, escribir

MAQUETAS = RAIZ / "build" / "bloques"


# ---------------------------------------------------------------------------
# GENERACION
# ---------------------------------------------------------------------------

def generar() -> tuple:
    """Escribe el arbol entero. Devuelve (ids, materiales de ciudad)."""
    # Se borra antes de generar: si se quita un material de las tablas, sus
    # ficheros tienen que desaparecer. Si no, quedan modelos huerfanos apuntando
    # a bloques que ya no existen y el log del cliente se llena de avisos.
    #
    # Ojo con el alcance: `resourcepacks/` vive al lado y NO se toca — ahi esta
    # el revestido azul luna de la interfaz, que lo genera otro script.
    for carpeta in (RES / f"assets/{NS}", RES / f"data/{NS}",
                    RES / "data/minecraft"):
        if carpeta.exists():
            shutil.rmtree(carpeta)

    ids, es, en, tags = [], {}, {}, {}

    wn = neon.escritor()
    neon.generar(wn)
    wc = ciudad.escritor()
    materiales = ciudad.generar(wc)

    for w in (wn, wc):
        ids += w.ids
        es.update(w.es)
        en.update(w.en)
        for tag, valores in w.tags.items():
            tags.setdefault(tag, []).extend(valores)

    # Los tres ficheros unicos del mod. Se escriben aqui, una vez, con lo que
    # han acumulado las dos familias.
    es[f"itemGroup.{NS}.neon"] = "Luna Eternal · Neón"
    en[f"itemGroup.{NS}.neon"] = "Luna Eternal · Neon"
    for gid, nombre in GRUPOS:
        es[f"itemGroup.{NS}.{gid}"] = f"Luna Eternal · {nombre[0]}"
        en[f"itemGroup.{NS}.{gid}"] = f"Luna Eternal · {nombre[1]}"
    escribir(RES / f"assets/{NS}/lang/es_es.json", es)
    escribir(RES / f"assets/{NS}/lang/en_us.json", en)

    for tag, valores in sorted(tags.items()):
        espacio, nombre = tag.split(":", 1)
        escribir(RES / f"data/{espacio}/tags/block/{nombre}.json",
                 {"replace": False, "values": [f"{NS}:{i}" for i in valores]})

    (JAVA / "Paleta.java").write_text(neon.paleta_java(), encoding="utf-8")
    (JAVA / "Catalogo.java").write_text(catalogo_java(materiales), encoding="utf-8")
    return ids, materiales


# Las pestañas del inventario creativo. Seiscientos bloques en una sola pestaña
# no son una paleta, son un listin: hay que poder llegar al material sin
# recordar como se llama.
GRUPOS = [
    ("hormigon",  ("Hormigón", "Concrete")),
    ("metal",     ("Metal", "Metal")),
    ("vidrio",    ("Vidrio", "Glass")),
    ("pavimento", ("Pavimento", "Paving")),
]


def catalogo_java(materiales: list) -> str:
    """`Catalogo.java`: los 126 materiales de obra tal y como los ve el mod."""
    formas = {
        "HORMIGON": ciudad.FORMAS_HORMIGON,
        "METAL": ciudad.FORMAS_METAL,
        "REJILLA": ciudad.FORMAS_REJILLA,
        "VIDRIO": ciudad.FORMAS_VIDRIO,
        "PAVIMENTO": ciudad.FORMAS_PAVIMENTO,
    }
    tablas = "\n".join(
        f'    public static final String[] {nombre} = {{'
        + ", ".join(f'"{s}"' for s in lista) + "};"
        for nombre, lista in formas.items())
    ancho = max(len(m.id) for m in materiales) + 3
    lineas = []
    for m in materiales:
        etiqueta = '"' + m.id + '",'
        lineas.append(f'        new Catalogo({etiqueta:<{ancho}} '
                      f'Ciudad.Familia.{m.familia}, MapColor.{m.mapa}, {m.familia})')
    filas = ",\n".join(lineas)
    return f"""\
package net.pokereport.neon;

import net.minecraft.block.MapColor;

/**
 * Los {len(materiales)} materiales de obra de la ciudadela.
 *
 * <p><b>GENERADO por {{@code tools/gen_bloques.py}} — no editar a mano.</b> Las
 * tablas vivas están en {{@code tools/bloques/ciudad.py}}, que además dibuja las
 * texturas y escribe los modelos. Tenerlas en dos sitios es como se registra un
 * bloque cuya textura no existe, que en la pantalla del jugador es el cubo
 * negro y morado.
 *
 * <p>Cada material se despliega en las formas de su familia, y no son las
 * mismas: el hormigón lleva muro porque un parapeto de azotea es de hormigón, y
 * el metal lleva pilar porque una viga es de metal.
 *
 * @param id      raíz del identificador: {{@code <id>}}, {{@code <id>_losa}}...
 * @param familia de dónde salen la dureza, el sonido y la capa de dibujado
 * @param mapa    color en el mapa y en la brújula de localizador
 * @param formas  sufijos que hay que registrar, en orden
 */
public record Catalogo(String id, Ciudad.Familia familia, MapColor mapa,
                       String[] formas) {{

{tablas}

    public static final Catalogo[] MATERIALES = {{
{filas},
    }};
}}
"""


# ---------------------------------------------------------------------------
# VERIFICACION
#
# El equivalente aqui de la regla MOD-006 ("cada sistema anade sus invariantes
# antes de desplegarse"). El autotest del mod grande comprueba economia y base
# de datos; esto comprueba lo unico que puede romperse en un mod de bloques.
#
# Y el fallo es especialmente traicionero: el SERVIDOR no lee los assets, asi
# que arranca tan contento con un modelo roto. El error solo aparece en la
# pantalla del jugador, como el cubo negro y morado, y para entonces ya se ha
# publicado el pack.
# ---------------------------------------------------------------------------

def _ids_de_java() -> list:
    """Lee los identificadores que el MOD va a registrar, de los .java.

    Es la mitad que importa de la comprobacion: los assets se generan a la vez
    que el Java, asi que compararlos entre si detecta justo el caso que produce
    el cubo negro y morado — que el codigo registre algo que el arte no tiene, o
    al reves. Se leen del disco, no de las tablas de Python, para que
    `--verificar` diga la verdad sobre lo que hay compilado.
    """
    ids = []
    paleta = (JAVA / "Paleta.java").read_text(encoding="utf-8")
    for cid in re.findall(r'new Paleta\("([^"]+)"', paleta):
        ids += [f"neon_{cid}{sufijo}" for sufijo, _, _ in neon.FORMAS]

    catalogo = (JAVA / "Catalogo.java").read_text(encoding="utf-8")
    tablas = {nombre: re.findall(r'"([^"]*)"', cuerpo)
              for nombre, cuerpo in re.findall(
                  r'String\[\] (\w+) = \{([^}]*)\}', catalogo)}
    for mid, tabla in re.findall(
            r'new Catalogo\("([^"]+)",\s*Ciudad\.Familia\.\w+,\s*MapColor\.\w+,\s*(\w+)\)',
            catalogo):
        ids += [f"{mid}{sufijo}" for sufijo in tablas[tabla]]
    return ids


def verificar() -> int:
    fallos = []
    a = RES / f"assets/{NS}"
    d = RES / f"data/{NS}"

    def json_de(ruta: Path):
        return json.loads(ruta.read_text(encoding="utf-8"))

    def ref(valor: str, carpeta: str, ext: str):
        """Traduce `lunaneon:block/x` a un fichero. None si es de vanilla."""
        if not valor.startswith(f"{NS}:"):
            return None
        return a / carpeta / (valor.split(":", 1)[1] + ext)

    esperados = _ids_de_java()
    if len(esperados) != len(set(esperados)):
        vistos, repes = set(), set()
        for i in esperados:
            (repes if i in vistos else vistos).add(i)
        fallos.append(f"identificadores repetidos: {sorted(repes)[:5]}")

    es = json_de(a / "lang/es_es.json")
    en = json_de(a / "lang/en_us.json")
    modelos_usados, texturas_usadas = set(), set()

    for bid in esperados:
        estado = a / f"blockstates/{bid}.json"
        if not estado.exists():
            fallos.append(f"{bid}: sin blockstate")
            continue

        datos = json_de(estado)
        if "variants" in datos:
            aplicaciones = list(datos["variants"].values())
            # Toda variante tiene que fijar `luz` donde esa propiedad existe, o
            # Minecraft se queda sin modelo para los estados que falten y los
            # dibuja como cubo de textura ausente.
            if bid.startswith("neon_"):
                sin_luz = [k for k in datos["variants"] if "luz=" not in k]
                if sin_luz:
                    fallos.append(f"{bid}: {len(sin_luz)} variantes sin `luz`")
        else:
            aplicaciones = [p["apply"] for p in datos["multipart"]]

        for aplica in aplicaciones:
            m = ref(aplica["model"], "models", ".json")
            if m is None or not m.exists():
                fallos.append(f"{bid}: modelo ausente {aplica['model']}")
            else:
                modelos_usados.add(m)

        if not (d / f"loot_table/blocks/{bid}.json").exists():
            fallos.append(f"{bid}: sin loot table, se rompe y no suelta nada")
        for idioma, tabla in (("es_es", es), ("en_us", en)):
            if f"block.{NS}.{bid}" not in tabla:
                fallos.append(f"{bid}: sin nombre en {idioma}")

        item = a / f"models/item/{bid}.json"
        if not item.exists():
            fallos.append(f"{bid}: sin modelo de objeto")
        else:
            datos = json_de(item)
            padre = ref(datos.get("parent", ""), "models", ".json")
            if padre is not None:
                if padre.exists():
                    modelos_usados.add(padre)
                else:
                    fallos.append(f"{bid}: el modelo de objeto apunta a la nada")
            for valor in datos.get("textures", {}).values():
                t = ref(valor, "textures", ".png")
                if t is None or not t.exists():
                    fallos.append(f"{bid}: textura de objeto ausente {valor}")
                else:
                    texturas_usadas.add(t)

    for modelo_ruta in sorted(a.glob("models/block/*.json")):
        datos = json_de(modelo_ruta)
        padre = datos.get("parent")
        if padre and (p := ref(padre, "models", ".json")) and not p.exists():
            fallos.append(f"{modelo_ruta.name}: padre ausente {padre}")
        for valor in datos.get("textures", {}).values():
            if valor.startswith("#"):
                continue
            t = ref(valor, "textures", ".png")
            if t is None or not t.exists():
                fallos.append(f"{modelo_ruta.name}: textura ausente {valor}")
            else:
                texturas_usadas.add(t)

    # Blockstates que nadie va a registrar: arte que llega a la descarga del
    # jugador y no se puede colocar. Siempre es sintoma de un renombrado a
    # medias.
    conocidos = set(esperados)
    for ruta in a.glob("blockstates/*.json"):
        if ruta.stem not in conocidos:
            fallos.append(f"blockstate sin bloque en Java: {ruta.name}")

    # Huerfanos: no rompen nada, pero engordan el jar.
    for ruta in a.glob("textures/block/*.png"):
        if ruta not in texturas_usadas:
            fallos.append(f"textura huerfana: {ruta.name}")
    for ruta in a.glob("models/block/*.json"):
        if ruta not in modelos_usados:
            fallos.append(f"modelo huerfano: {ruta.name}")

    print(f"VERIFICACION  ·  {len(esperados)} bloques declarados en Java")
    if fallos:
        for f in fallos[:30]:
            print(f"  FALLO  {f}")
        if len(fallos) > 30:
            print(f"  ... y {len(fallos) - 30} mas")
        print(f"\n  {len(fallos)} fallos. NO desplegar.")
        return 1
    print(f"  {len(modelos_usados)} modelos y {len(texturas_usadas)} texturas, "
          f"todos referenciados y presentes")
    print("  correcto")
    return 0


# ---------------------------------------------------------------------------
# MAQUETA
#
# Una lamina de contacto por familia. Cada celda es la textura repetida 2x2, y
# eso no es decorativo: es la unica forma de VER si encaja consigo misma. Una
# textura que no encaja no se nota en el editor y se nota muchisimo en una
# fachada de cuarenta bloques.
# ---------------------------------------------------------------------------

TILE = 3          # aumento de cada textura
REPETIR = 2       # cuantas veces se repite en la celda
ETIQUETA = 13     # alto de la linea de texto


def _fuente(tam: int):
    from PIL import ImageFont
    for ruta in (r"C:\Windows\Fonts\segoeui.ttf", r"C:\Windows\Fonts\arial.ttf"):
        if Path(ruta).exists():
            try:
                return ImageFont.truetype(ruta, tam)
            except OSError:
                pass
    return ImageFont.load_default()


def _celda(ruta: Path) -> Image.Image:
    """La textura repetida 2x2 sobre un tablero, para que el alfa se vea."""
    tex = Image.open(ruta).convert("RGBA")
    lado = tex.width * REPETIR
    # Tablero oscuro: la ciudadela es de noche, y un cristal sobre blanco
    # engaña sobre como se va a ver de verdad.
    fondo = Image.new("RGBA", (lado, lado))
    for y in range(lado):
        for x in range(lado):
            claro = ((x // 4) + (y // 4)) % 2 == 0
            fondo.putpixel((x, y), (38, 42, 52) if claro else (28, 31, 39))
    for j in range(REPETIR):
        for i in range(REPETIR):
            fondo.alpha_composite(tex, (i * tex.width, j * tex.height))
    return fondo.resize((lado * TILE, lado * TILE), Image.NEAREST)


def maqueta(nombre: str, filas: list) -> Path:
    """:param filas: lista de (titulo, [(etiqueta, ruta_png), ...])"""
    from PIL import ImageDraw

    lado = 16 * REPETIR * TILE
    fuente = _fuente(11)
    titulo_f = _fuente(15)
    cols = max(len(c) for _, c in filas)
    margen, hueco = 16, 10
    ancho = margen * 2 + cols * (lado + hueco) - hueco
    alto = margen
    for _, celdas in filas:
        alto += 22 + lado + ETIQUETA + hueco
    alto += margen

    img = Image.new("RGBA", (ancho, alto), (18, 20, 26, 255))
    dib = ImageDraw.Draw(img)
    y = margen
    for titulo, celdas in filas:
        dib.text((margen, y), titulo, font=titulo_f, fill=(240, 178, 70))
        y += 22
        for i, (etiqueta, ruta) in enumerate(celdas):
            x = margen + i * (lado + hueco)
            img.alpha_composite(_celda(ruta), (x, y))
            dib.text((x, y + lado + 1), etiqueta, font=fuente, fill=(198, 206, 218))
        y += lado + ETIQUETA + hueco

    MAQUETAS.mkdir(parents=True, exist_ok=True)
    destino = MAQUETAS / f"{nombre}.png"
    img.convert("RGB").save(destino)
    return destino


def maquetas() -> list:
    tex = RES / f"assets/{NS}/textures/block"
    hechas = []

    filas = []
    for cid, col_es, _, _, _ in neon.PALETA:
        filas.append((col_es, [(ac_es, tex / f"hormigon_{aid}_{cid}.png")
                               for aid, ac_es, _, _ in ciudad.HORMIGON]))
    hechas.append(maqueta("hormigon", filas))

    filas = []
    for mid, met_es, _, _, _, _ in ciudad.ALEACIONES:
        celdas = [(ac_es, tex / f"metal_{mid}_{aid}.png")
                  for aid, ac_es, _, _ in ciudad.ACABADOS_METAL]
        celdas.append(("rejilla", tex / f"rejilla_{mid}.png"))
        filas.append((met_es, celdas))
    hechas.append(maqueta("metal", filas))

    filas = []
    for vid, v_es, _, _, _ in ciudad.VIDRIOS:
        filas.append((v_es, [(col_es, tex / f"vidrio_{vid}_{cid}.png")
                             for cid, col_es, _, _, _ in neon.PALETA[:8]]))
        filas.append((f"{v_es} (2)", [(col_es, tex / f"vidrio_{vid}_{cid}.png")
                                      for cid, col_es, _, _, _ in neon.PALETA[8:]]))
    hechas.append(maqueta("vidrio", filas))

    filas = [("Pavimento", [(p_es, tex / f"pavimento_{pid}.png")
                            for pid, p_es, _, _, _, _ in ciudad.PAVIMENTOS])]
    filas.append(("Neón (referencia)",
                  [(col_es, tex / f"neon_{cid}.png")
                   for cid, col_es, _, _, _ in neon.PALETA[:6]]))
    hechas.append(maqueta("pavimento", filas))
    return hechas


# ---------------------------------------------------------------------------

def listar() -> None:
    total = 0
    print(f"NEON       {len(neon.PALETA)} colores x {len(neon.FORMAS)} formas"
          f" = {len(neon.PALETA) * len(neon.FORMAS)}")
    total += len(neon.PALETA) * len(neon.FORMAS)
    for nombre, n, formas in (
            ("HORMIGON", len(ciudad.HORMIGON) * len(neon.PALETA), ciudad.FORMAS_HORMIGON),
            ("METAL", len(ciudad.ALEACIONES) * len(ciudad.ACABADOS_METAL), ciudad.FORMAS_METAL),
            ("REJILLA", len(ciudad.ALEACIONES), ciudad.FORMAS_REJILLA),
            ("VIDRIO", len(ciudad.VIDRIOS) * len(neon.PALETA), ciudad.FORMAS_VIDRIO),
            ("PAVIMENTO", len(ciudad.PAVIMENTOS), ciudad.FORMAS_PAVIMENTO)):
        print(f"{nombre:<10} {n} materiales x {len(formas)} formas = {n * len(formas)}")
        total += n * len(formas)
    print(f"\n  {total} bloques")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--listar", action="store_true",
                    help="imprime la cuenta por familia y no escribe nada")
    ap.add_argument("--verificar", action="store_true",
                    help="comprueba lo ya generado y no escribe nada")
    ap.add_argument("--maqueta", action="store_true",
                    help="laminas de contacto en build/bloques/")
    args = ap.parse_args()

    if args.verificar:
        raise SystemExit(verificar())
    if args.listar:
        return listar()
    if args.maqueta:
        for ruta in maquetas():
            print(f"  {ruta.relative_to(RAIZ)}")
        return

    ids, materiales = generar()
    listar()
    n = sum(1 for p in RES.rglob("*") if p.is_file())
    print(f"\n  -> {len(ids)} bloques, {n} ficheros en {RES.relative_to(RAIZ)}")
    print(f"  -> Paleta.java ({len(neon.PALETA)} colores) y "
          f"Catalogo.java ({len(materiales)} materiales) regenerados")


if __name__ == "__main__":
    main()
