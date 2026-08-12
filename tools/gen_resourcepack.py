#!/usr/bin/env python3
"""
Genera el resource pack de Luna Eternal (D-023).

La idea, en una frase: **el titulo de un menu de cofre se escribe con una
fuente propia cuyos caracteres son imagenes**, asi que el menu deja de
parecer un menu de cofre.

Como funciona
-------------
Un resource pack puede definir fuentes. Una fuente tiene "providers":

  space   asigna a ciertos caracteres una anchura, que puede ser NEGATIVA.
          Sirve para mover el cursor hacia atras y dibujar donde quieras.
  bitmap  asigna a un caracter un PNG. El caracter ya no es una letra:
          es una imagen.

Combinando los dos: retrocedes con espacios negativos hasta el borde del
menu y pintas tu fondo. El servidor solo envia texto — no hay mod de cliente
y P6 queda intacto (el servidor sigue controlandolo todo).

Geometria de un menu de cofre (pixeles de interfaz, escala 1)
-------------------------------------------------------------
El titulo se dibuja en (8, 6) respecto de la esquina superior izquierda del
menu. Para llegar al borde hay que retroceder 8. La linea base del texto cae
7 px por debajo del borde superior del area de titulo, y de ahi sale ASCENT.

No se escribe nada a mano: este script genera el JSON de la fuente y el zip.
"""
import hashlib
import json
import shutil
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw

RAIZ = Path(__file__).resolve().parent.parent
FUENTES = RAIZ / "resourcepack"          # PNG de origen, editables a mano
SALIDA = RAIZ / "build"
NS = "lunaeternal"

# 34 = formato de pack para 1.21.1. Si sube MC, sube esto.
PACK_FORMAT = 34

# Zona de uso privado: estos codepoints no significan nada en Unicode, asi
# que nadie los va a escribir por accidente en el chat.
BASE_BITMAP = 0xE000

# Anchuras de espacio negativo que hacen falta. Con potencias de dos se
# compone cualquier desplazamiento sumando unos pocos caracteres.
DESPLAZAMIENTOS = [-1024, -512, -256, -128, -64, -32, -16, -8, -4, -2, -1,
                   1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024]
BASE_SPACE = 0xE100


def codepoint_espacio(px: int) -> str:
    """Caracter que desplaza `px` pixeles. Negativo = hacia atras."""
    return chr(BASE_SPACE + DESPLAZAMIENTOS.index(px))


def desplazar(px: int) -> str:
    """Cadena que desplaza exactamente `px` pixeles, sumando potencias de dos.

    Se usa para colocar el fondo: hay que retroceder 8 px desde donde el
    juego dibuja el titulo hasta el borde izquierdo del menu."""
    salida = []
    restante = px
    for d in sorted(DESPLAZAMIENTOS, key=lambda x: -abs(x)):
        while (restante <= d < 0) or (0 < d <= restante):
            salida.append(codepoint_espacio(d))
            restante -= d
    if restante:
        raise ValueError(f"no se puede componer un desplazamiento de {px} px")
    return "".join(salida)


# --------------------------------------------------------------- texturas

def huecos(filas: int) -> list[tuple[int, int]]:
    """Esquina superior izquierda de cada casilla, en pixeles de interfaz.

    IMPORTANTE. El titulo se dibuja DESPUES de los objetos, asi que nuestro
    fondo queda POR ENCIMA de ellos. Si es opaco, tapa el inventario entero.
    Por eso cada casilla tiene que ser un agujero transparente.

    Geometria de GenericContainerScreenHandler (vanilla):
      cofre    x = 8 + col*18   y = 18 + fila*18
      mochila  x = 8 + col*18   y = 103 + fila*18 + (filas-4)*18
      barra    x = 8 + col*18   y = 161 + (filas-4)*18
    El hueco visible es de 18x18 y empieza un pixel antes.
    """
    i = (filas - 4) * 18
    ys = [18 + f * 18 for f in range(filas)]           # cofre
    ys += [103 + f * 18 + i for f in range(3)]         # mochila
    ys += [161 + i]                                    # barra rapida
    return [(8 + c * 18 - 1, y - 1) for y in ys for c in range(9)]


def casillas_tapadas(img: Image.Image, filas: int) -> int:
    """Cuantas casillas tienen pixeles opacos en el centro. Debe ser 0."""
    rgba = img.convert("RGBA")
    malas = 0
    for (hx, hy) in huecos(filas):
        # Se mira el centro, no el borde: el borde puede llevar marco.
        for dx in (6, 9, 12):
            for dy in (6, 9, 12):
                if rgba.getpixel((hx + dx, hy + dy))[3] > 8:
                    malas += 1
                    break
            else:
                continue
            break
    return malas


def marcador(ancho: int, alto: int, filas: int, titulo: str) -> Image.Image:
    """Textura de relleno hasta que exista arte de verdad (ART-001).

    No es decorativa: el marco y las marcas cada 18 px permiten comprobar que
    la alineacion del espacio negativo es exacta al pixel. Si el fondo baila
    uno solo, aqui se ve."""
    img = Image.new("RGBA", (ancho, alto), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, ancho - 1, alto - 1], fill=(24, 20, 37, 235),
                outline=(120, 96, 200, 255))
    d.rectangle([1, 1, ancho - 2, alto - 2], outline=(60, 48, 100, 255))
    for x in range(0, ancho, 18):
        d.line([(x, 0), (x, 3)], fill=(120, 96, 200, 160))
    d.text((5, 4), titulo[:28], fill=(226, 214, 255, 255))

    # Los agujeros van al final: se pintan a alpha 0 para que los objetos se
    # vean a traves. Con un marco tenue alrededor, para que sigan pareciendo
    # casillas.
    for (hx, hy) in huecos(filas):
        d.rectangle([hx, hy, hx + 17, hy + 17], fill=(0, 0, 0, 0))
        d.rectangle([hx, hy, hx + 17, hy + 17], outline=(120, 96, 200, 90))
    return img


# Geometria de un cofre, en pixeles de interfaz:
#   ancho  = 176 siempre
#   alto   = 17 (barra de titulo) + filas*18 (casillas) + 97 (inventario)
ANCHO = 176


def alto_de(filas: int) -> int:
    return 114 + filas * 18


# La linea base del titulo cae 13 px por debajo del borde superior del menu
# (el titulo se dibuja en y=6 y la fuente de Minecraft tiene ascenso 7).
# Poner ASCENT ahi hace que el borde superior de la textura coincida
# exactamente con el borde del menu.
ASCENT = 13

# Pantalla -> filas del cofre. Tiene que COINCIDIR con el super(...) del menu
# en Java; si no, el fondo queda corto o sobresale.
PANTALLAS = {
    "almanaque": 6,
    "cartera":   6,
    "vias":      6,
    "misiones":  6,
    "gts":       6,
    "pokedex":   6,
    "puerta":    5,
    "tienda":    4,
    "kits":      4,
    "centro":    3,
}


def asegurar_texturas() -> None:
    """Crea los PNG de relleno que falten. NO pisa los que ya existan:
    en cuanto haya arte real, este script deja de tocarlos."""
    destino = FUENTES / "assets" / NS / "textures" / "gui"
    destino.mkdir(parents=True, exist_ok=True)
    for nombre, filas in PANTALLAS.items():
        h = alto_de(filas)
        p = destino / f"{nombre}.png"
        if p.exists():
            img = Image.open(p)
            if img.size != (ANCHO, h):
                raise SystemExit(
                    f"{p.name} mide {img.size} y deberia medir {(ANCHO, h)} "
                    f"({filas} filas). Un pixel de mas descuadra todo el fondo.")
            opacos = casillas_tapadas(img, filas)
            if opacos:
                raise SystemExit(
                    f"{p.name}: {opacos} casillas NO son transparentes. "
                    f"El titulo se dibuja DESPUES de los objetos, asi que un "
                    f"fondo opaco los tapa. Cada casilla de 18x18 tiene que "
                    f"tener alpha 0. Ver docs/ui/art-brief.md")
            print(f"  {nombre:<12} arte propio {img.size[0]}x{img.size[1]}, casillas OK")
            continue
        marcador(ANCHO, h, filas, nombre.upper()).save(p)
        print(f"  {nombre:<12} marcador generado {ANCHO}x{h} ({filas} filas)")


# --------------------------------------------------------------- fuente

# El emblema de cada arte, reducido a una insignia para la barra de titulo.
# El panel comun se genera SIN emblema (no cabe: la barra son 17 px y las
# casillas lo partirian por la mitad). Aqui se recupera, pequeno y a la
# derecha, y es lo unico que distingue una pantalla de otra.
BANNER_ALTO = 15
BANNER_DERECHA = 168     # borde derecho donde termina la insignia


def banner(nombre: str) -> Image.Image | None:
    """Recorta el emblema del arte original y lo reduce a la barra."""
    src = RAIZ / "arte-origen" / f"{nombre}.png"
    if not src.exists():
        return None
    b = Image.open(src).convert("RGBA")
    # Misma banda que panel_vanilla descarta, para que encajen.
    banda = b.crop((0, round(b.height * 0.03), b.width, round(b.height * 0.21)))
    # Solo el centro, que es donde esta el emblema.
    w = banda.width
    banda = banda.crop((round(w * 0.34), 0, round(w * 0.66), banda.height))
    escala = BANNER_ALTO / banda.height
    return banda.resize((max(1, round(banda.width * escala)), BANNER_ALTO),
                        Image.LANCZOS)


def construir_fuente(banners: dict) -> dict:
    """El JSON de la fuente: un provider `space` y un `bitmap` por insignia."""
    providers = [{
        "type": "space",
        "advances": {codepoint_espacio(px): px for px in DESPLAZAMIENTOS},
    }]
    for i, nombre in enumerate(PANTALLAS):
        if nombre not in banners:
            continue
        providers.append({
            "type": "bitmap",
            "file": f"{NS}:gui/banner_{nombre}.png",
            "ascent": 12,
            "height": BANNER_ALTO,
            "chars": [chr(BASE_BITMAP + i)],
        })
    return {"providers": providers}


def caracteres_pantalla(banners: dict) -> dict:
    """Para cada pantalla, la cadena que dibuja su insignia y vuelve.

    El titulo se dibuja en x=8. Se avanza hasta dejar la insignia pegada a
    BANNER_DERECHA, se pinta, y se retrocede TODO lo avanzado (incluida la
    anchura del glifo, que avanza w+1) para que el texto del titulo salga en
    su sitio y no desplazado."""
    out = {}
    for i, nombre in enumerate(PANTALLAS):
        img = banners.get(nombre)
        if img is None:
            continue
        w = img.width
        ida = BANNER_DERECHA - w - 8
        out[nombre] = (desplazar(ida) + chr(BASE_BITMAP + i)
                       + desplazar(-(ida + w + 1)))
    return out


# --------------------------------------------------------------- empaquetado

def main() -> None:
    print("Texturas")
    asegurar_texturas()

    raiz = SALIDA / "resourcepack"
    if raiz.exists():
        shutil.rmtree(raiz)
    shutil.copytree(FUENTES, raiz)

    (raiz / "pack.mcmeta").write_text(json.dumps({
        "pack": {
            "pack_format": PACK_FORMAT,
            "description": "§6PokeReport §f: §bLuna Eternal",
        }
    }, indent=2), encoding="utf-8")

    # Panel comun: sustituye la textura del cofre de vanilla.
    #
    # OJO: sale del arte ORIGINAL, no de la textura de resourcepack/, que ya
    # esta perforada. Construirlo desde la perforada dejaba el panel lleno de
    # agujeros — y ahi se dibuja ANTES que los objetos, asi que no hacen falta.
    base = RAIZ / "arte-origen" / f"{PANEL_BASE}.png"
    if base.exists():
        destino_panel = (raiz / "assets" / "minecraft" / "textures" / "gui"
                         / "container" / "generic_54.png")
        destino_panel.parent.mkdir(parents=True, exist_ok=True)
        panel_vanilla(Image.open(base)).save(destino_panel)
        print(f"  panel comun generado desde {PANEL_BASE}")

    # Insignias por pantalla, a partir del arte original.
    banners = {}
    dir_gui = raiz / "assets" / NS / "textures" / "gui"
    dir_gui.mkdir(parents=True, exist_ok=True)
    for nombre in PANTALLAS:
        img = banner(nombre)
        if img is None:
            continue
        banners[nombre] = img
        img.save(dir_gui / f"banner_{nombre}.png")
        # El panel ya dibuja el fondo, asi que la textura de pantalla
        # completa deja de usarse: se quita del zip para no cargarla en balde.
        antigua = dir_gui / f"{nombre}.png"
        if antigua.exists():
            antigua.unlink()
    print(f"  {len(banners)} insignias generadas")

    n = texturas_pad(raiz / "assets" / NS / "textures" / "pad")
    print(f"  Pad: panel, 3 celdas y {n} iconos")

    fuente = raiz / "assets" / NS / "font" / "gui.json"
    fuente.parent.mkdir(parents=True, exist_ok=True)
    fuente.write_text(json.dumps(construir_fuente(banners), indent=2),
                      encoding="utf-8")

    SALIDA.mkdir(parents=True, exist_ok=True)
    zip_path = SALIDA / "lunaeternal-resourcepack.zip"
    # El zip tiene que ser REPRODUCIBLE: el servidor publica un SHA-1 y el
    # cliente rechaza el pack si no coincide. Un zip normal guarda la fecha
    # de cada fichero, asi que volver a generarlo daba un hash distinto con
    # el mismo contenido — y eso acaba en un desajuste imposible de explicar.
    # Fecha fija y orden fijo lo resuelven.
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
        for f in sorted(raiz.rglob("*")):
            if not f.is_file():
                continue
            info = zipfile.ZipInfo(f.relative_to(raiz).as_posix(),
                                   date_time=(2020, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            z.writestr(info, f.read_bytes())

    sha1 = hashlib.sha1(zip_path.read_bytes()).hexdigest()

    # El mod lee esto para no repetir la geometria en Java.
    mapa = RAIZ / "mod" / "src" / "main" / "resources" / "gui_chars.json"
    mapa.parent.mkdir(parents=True, exist_ok=True)
    mapa.write_text(json.dumps(caracteres_pantalla(banners), indent=2,
                               ensure_ascii=False), encoding="utf-8")

    print(f"\n{zip_path}")
    print(f"  {zip_path.stat().st_size // 1024} KB · {len(PANTALLAS)} pantallas")
    print(f"  sha1 = {sha1}")
    print(f"\nPara server.properties:")
    print(f"  resource-pack-sha1={sha1}")




# --------------------------------------------------- panel de vanilla
#
# HALLAZGO que obligo a rediseñar (verificado leyendo el bytecode de
# HandledScreen en el jar del cliente):
#
#     drawBackground  ->  drawSlot (objetos)  ->  drawForeground (titulo)
#
# ...y con disableDepthTest, asi que manda el orden. Nuestro fondo, que es el
# titulo, va POR ENCIMA de los objetos. Por eso hay que perforar 90 agujeros
# — y con 90 agujeros del arte solo se ve el borde.
#
# La salida es sustituir la textura del cofre, que se dibuja ANTES que los
# objetos. Es UNA textura para todos los menus, asi que el panel es comun y
# la identidad de cada pantalla la da su banda de titulo.

PANEL_BASE = "almanaque"   # de que arte sale el panel comun


def panel_vanilla(base: Image.Image) -> Image.Image:
    """Construye assets/minecraft/textures/gui/container/generic_54.png.

    Es una hoja de 256x256: arriba el cofre de 6 filas (176x222) y, a partir
    de y=126, el trozo del inventario del jugador que el juego pega debajo
    cuando el cofre tiene menos filas."""
    hoja = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    base = base.convert("RGBA")

    # El arte trae un emblema grande arriba, pero un cofre solo deja 17 px de
    # barra de titulo: las casillas empiezan en y=17 y lo taparian por la
    # mitad, que se ve como un error. Se empalma el borde superior del marco
    # directamente con el cuerpo, saltandose la banda del emblema.
    borde = round(base.height * 0.03)      # filo del marco, se conserva
    emblema = round(base.height * 0.21)    # emblema + aurora, se descarta
    sin_emblema = Image.new("RGBA", (base.width, base.height - (emblema - borde)))
    sin_emblema.paste(base.crop((0, 0, base.width, borde)), (0, 0))
    sin_emblema.paste(base.crop((0, emblema, base.width, base.height)), (0, borde))

    panel = sin_emblema.resize((ANCHO, alto_de(6)), Image.LANCZOS)
    hoja.paste(panel, (0, 0))

    # El trozo del inventario del jugador tiene que estar tambien en y=126,
    # porque para 1-5 filas el juego lo copia de ahi.
    inv = panel.crop((0, alto_de(6) - 97, ANCHO, alto_de(6)))
    hoja.paste(inv, (0, 126))

    d = ImageDraw.Draw(hoja)

    def pozo(x, y):
        """Una casilla: hueco oscuro con luz arriba-izquierda y brillo abajo,
        que es como se lee un hundido."""
        d.rectangle([x, y, x + 17, y + 17], fill=(18, 14, 32, 220))
        d.line([(x, y), (x + 17, y)], fill=(10, 8, 20, 255))
        d.line([(x, y), (x, y + 17)], fill=(10, 8, 20, 255))
        d.line([(x, y + 17), (x + 17, y + 17)], fill=(120, 96, 200, 130))
        d.line([(x + 17, y), (x + 17, y + 17)], fill=(120, 96, 200, 130))

    # Casillas del cofre de 6 filas, y las del inventario en su sitio.
    for (hx, hy) in huecos(6):
        pozo(hx, hy)
    # Y otra vez sobre la copia de y=126, con el desplazamiento que toca.
    desplazo = 126 - (alto_de(6) - 97)
    for (hx, hy) in huecos(6):
        if hy >= alto_de(6) - 97:
            pozo(hx, hy + desplazo)
    return hoja




# ------------------------------------------------- texturas del Pad (D-025)
#
# El Pad es una pantalla propia, asi que ya no manda la geometria del cofre:
# el panel se estira desde una textura de 64x64 en nueve rodajas, y los
# iconos son PNG de 16x16. Nada de esto depende de casillas de 18 px.

ICONOS_PAD = ["pokedex", "cartera", "vias", "misiones", "kits", "tienda",
              "gts", "centro", "puerta", "gimnasios", "tesoros", "clan",
              "cosmeticos", "cazas", "explorar"]


def _redondeado(w, h, r, relleno, borde):
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, w - 1, h - 1], radius=r, fill=relleno,
                        outline=borde, width=2)
    return img


def _grad(w, h, arriba, abajo):
    """Degradado vertical. Es lo que da volumen sin dibujar sombras a mano."""
    img = Image.new("RGBA", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / max(1, h - 1)
        d.line([(0, y), (w, y)], fill=tuple(
            round(a + (b - a) * t) for a, b in zip(arriba, abajo)))
    return img


def _pieza(w, h, r, arriba, abajo, borde, luz=True):
    """Pieza redondeada con degradado, contorno y brillo superior.

    El brillo es lo que hace que se lea como un boton fisico y no como un
    rectangulo de color. Es todo el truco del estilo PokePad."""
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    mascara = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mascara).rounded_rectangle([0, 0, w - 1, h - 1], r, fill=255)
    img.paste(_grad(w, h, arriba, abajo), (0, 0), mascara)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, w - 1, h - 1], r, outline=borde, width=2)
    if luz:
        # Brillo interior arriba: una linea clara a media opacidad.
        d.rounded_rectangle([3, 3, w - 4, h // 2], max(1, r - 2),
                            outline=(255, 255, 255, 70), width=2)
    return img


# Paleta PokePad: rojo Pokedex fuera, azul amable dentro.
ROJO       = (206, 62, 48, 255)
ROJO_OSC   = (142, 34, 26, 255)
ROJO_CLARO = (236, 106, 92, 255)
AZUL       = (86, 170, 224, 255)
AZUL_OSC   = (44, 120, 180, 255)
AZUL_CLARO = (150, 212, 245, 255)
BLANCO     = (245, 250, 255, 255)


def texturas_pad(destino: Path) -> int:
    """Piezas del Pad que NO vengan ya importadas.

    tools/importar_pad.py trae el arte de verdad. Lo de aqui es relleno para
    lo que falte, para que el Pad nunca salga con cuadros de textura ausente."""
    destino.mkdir(parents=True, exist_ok=True)

    def falta(nombre):
        return not (destino / nombre).exists()

    if not falta("pokepad.png"):
        # Hay arte importado: solo se completan los estados de celda.
        base = Image.open(destino / "celda.png").convert("RGBA")             if not falta("celda.png") else None
        if base is not None:
            if falta("celda_encima.png"):
                claro = Image.new("RGBA", base.size, (255, 255, 255, 60))
                enc = base.copy(); enc.alpha_composite(claro)
                enc.save(destino / "celda_encima.png")
            if falta("celda_bloqueada.png"):
                gris = base.convert("LA").convert("RGBA")
                oscuro = Image.new("RGBA", base.size, (0, 0, 0, 90))
                gris.alpha_composite(oscuro)
                gris.putalpha(base.split()[3])
                gris.save(destino / "celda_bloqueada.png")
        return 3

    # Marco exterior rojo (se estira: 9 rodajas de 64x64, esquinas de 16).
    _pieza(64, 64, 14, ROJO_CLARO, ROJO_OSC, (92, 20, 14, 255))         .save(destino / "panel.png")

    # Area interior azul, donde van las celdas.
    _pieza(64, 64, 10, AZUL_CLARO, AZUL_OSC, (28, 84, 130, 255))         .save(destino / "interior.png")

    # Pestana del titulo, la que sobresale por arriba.
    # 64x64 como todas: nueveRodajas asume esa hoja. Con 64x32 muestreaba
    # fuera de la textura y la pestana salia cortada.
    _pieza(64, 64, 10, ROJO, ROJO_OSC, (92, 20, 14, 255))         .save(destino / "pestana.png")

    # Celdas: reposo, encima y bloqueada. Se distinguen por LUMINOSIDAD
    # ademas de por color, para que se lean con daltonismo.
    _pieza(48, 48, 8, BLANCO, AZUL_CLARO, (34, 96, 146, 255))         .save(destino / "celda.png")
    _pieza(48, 48, 8, (255, 255, 255, 255), (196, 232, 255, 255),
           (255, 236, 150, 255)).save(destino / "celda_encima.png")
    _pieza(48, 48, 8, (150, 158, 170, 255), (96, 104, 118, 255),
           (60, 66, 78, 255), luz=False).save(destino / "celda_bloqueada.png")

    # Botones de la cabecera.
    _pieza(24, 24, 6, AZUL_CLARO, AZUL_OSC, (28, 84, 130, 255))         .save(destino / "boton.png")
    _pieza(24, 24, 6, (255, 255, 255, 255), AZUL_CLARO,
           (255, 236, 150, 255)).save(destino / "boton_encima.png")

    return 7


if __name__ == "__main__":
    main()
