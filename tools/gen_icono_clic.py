# -*- coding: utf-8 -*-
"""
INSTALA EL ICONO DEL CLIC DERECHO COMO UN GLIFO DE FUENTE.

    python tools/gen_icono_clic.py                    lee ~/Downloads/clickderecho.png
    python tools/gen_icono_clic.py --origen <ruta>

⚠⚠⚠ MINECRAFT NO TIENE «IMAGE DISPLAY», Y POR ESO ESTO ES UNA FUENTE.

   Las entidades de dibujo que existen son tres --texto, objeto y bloque-- asi
   que un PNG flotando en el mundo solo se puede hacer de dos formas: pintando
   un quad a mano en `WorldRenderEvents` (que es lo que hace el holograma del
   santuario, y son cientos de lineas) o METIENDO EL PNG EN UNA FUENTE.

   Como glifo, el icono es UN CARACTER MAS: se dibuja dentro del cartel que ya
   existe, escala con el texto, mira al jugador solo, y de propina sirve igual
   en el chat y en cualquier pantalla del PokePad. Cero codigo de dibujado.

⚠⚠ EL CARTEL SIGUE DICIENDO «Clic derecho» AL LADO DEL ICONO, A PROPOSITO.
   Un TextDisplay guarda su texto EN EL MUNDO: si algun dia el glifo no
   estuviera --un cliente sin el resource pack, un renombrado del fichero-- lo
   que quedaria plantado en el laboratorio para siempre seria UN CUADRADO
   BLANCO, y solo se arregla volviendo a colocar a Oak. Con la palabra al lado,
   el peor caso es un cuadradito raro delante de un texto que se lee.

⚠ SE SANGRA EL ALFA, y no es opcional aunque el PNG venga limpio. Un pixel
  invisible sigue guardando un color, y al encoger el glifo de 256 px a 12 el
  filtrado MEZCLA ese color con el visible: es el fallo de los micropuntos de
  colores que ya costo tres diagnosticos en los iconos del Pad. La funcion se
  IMPORTA de `gen_pokepad.py` en vez de copiarse: un segundo pipeline daria
  «este icono se ve distinto» sin ningun error.

⚠ Y NO se le escribe `.mcmeta`. `guardar()` de gen_pokepad le pone uno con
  `clamp:true` porque sus texturas son de INTERFAZ; las de fuente las carga el
  gestor de fuentes, y las de vainilla (`textures/font/ascii.png`) no llevan
  ninguno.
"""
import argparse
import json
import pathlib
import sys

from PIL import Image

RAIZ = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(RAIZ / "tools"))

from gen_pokepad import sangrar_alfa  # noqa: E402  (necesita la raiz en sys.path)

ASSETS = RAIZ / "mod" / "src" / "client" / "resources" / "assets" / "lunaeternal"
TEXTURA = ASSETS / "textures" / "font" / "clic_derecho.png"
FUENTE = ASSETS / "font" / "iconos.json"

ORIGEN = pathlib.Path.home() / "Downloads" / "clickderecho.png"

#: El caracter. Zona de uso privado, que es donde no pisa a ninguna letra.
CARACTER = ""

#: Margen que se le deja alrededor, en tanto por uno del lado mayor.
#:
#: ⚠ El arte llegaba PEGADO AL BORDE de arriba (contenido en y=1 de 256). Un
#:   glifo que toca el borde de su celda sale rozando la linea de texto de
#:   encima, y al filtrarlo se come su propio contorno.
MARGEN = 0.07

#: Cuantos pixeles de alto se dibuja el glifo, y donde cae la linea base.
#:
#: ⚠ Las letras de vainilla miden 8 con ascent 7. El raton va a 12 para que se
#:   lea como ICONO y no como letra, y baja 3 por debajo de la linea base --que
#:   es lo que lo centra contra la palabra que tiene al lado--.
ALTO = 12
ASCENT = 9


def preparar(origen: pathlib.Path) -> Image.Image:
    """Recorta al dibujo, le deja aire y le sangra el alfa."""
    im = Image.open(origen).convert("RGBA")
    caja = im.split()[3].getbbox()
    if caja is None:
        raise SystemExit("El PNG esta entero transparente: no hay nada que instalar.")
    recorte = im.crop(caja)

    lado = max(recorte.size)
    aire = int(round(lado * MARGEN))
    lienzo = Image.new("RGBA",
                       (recorte.width + aire * 2, recorte.height + aire * 2),
                       (0, 0, 0, 0))
    lienzo.paste(recorte, (aire, aire))
    return sangrar_alfa(lienzo)


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--origen", type=pathlib.Path, default=ORIGEN)
    args = ap.parse_args()

    if not args.origen.exists():
        raise SystemExit("No esta el arte: %s" % args.origen)

    im = preparar(args.origen)
    print("  recortado y con aire  ->  %dx%d" % im.size)

    TEXTURA.parent.mkdir(parents=True, exist_ok=True)
    im.save(TEXTURA, "PNG")
    print("  %s  (%.1f KB)" % (TEXTURA.relative_to(RAIZ), TEXTURA.stat().st_size / 1024))

    # ⚠ `file` cuelga de `textures/`, no de la raiz del assets: por eso NO lleva
    #   «textures/» delante. Escrito con la carpeta, Minecraft no encuentra nada
    #   y el glifo sale como un cuadrado -- sin un solo error en el log.
    FUENTE.parent.mkdir(parents=True, exist_ok=True)
    FUENTE.write_text(json.dumps({
        "providers": [
            {
                "type": "bitmap",
                "file": "lunaeternal:font/clic_derecho.png",
                "height": ALTO,
                "ascent": ASCENT,
                "chars": [CARACTER],
            }
        ]
    }, indent=2) + "\n", encoding="utf-8")
    print("  %s  ->  fuente `lunaeternal:iconos`, caracter U+E000"
          % FUENTE.relative_to(RAIZ))
    print()
    print("  En Java:  Iconos.clicDerecho()")


if __name__ == "__main__":
    main()
