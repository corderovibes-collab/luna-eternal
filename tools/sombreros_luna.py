"""Las gorras de Poke Ball: NUESTRAS, generadas, sin bajar arte nuevo.

Las pidio el usuario: «si les puedes editar las texturas hacerlas un poco mas
pokemon seria excelente, eso si bien estructurado auditando todo y haciendolo
bien».

⚠⚠ LO QUE **NO** SE HACE, Y POR QUE

No se repintan los 378 sombreros. Casi todos son arte dibujado a mano --un
hamburguesa, un gato, una tarta-- y pasarles un filtro de color no los hace «mas
Pokemon»: los estropea. Un recoloreado automatico solo sale bien sobre arte
pensado para recolorearse.

Y esa es justamente la gorra de beisbol de Simple Hats: **es gris de fabrica**, y
su propio autor publica variantes (`baseballhatrgb`, `baseballhatjuly`). Sobre
ella el repintado no es un apaño, es el uso previsto.

⚠ CADA PARTE SE PINTA POR SEPARADO, Y NO POR LUMINANCIA

La copa y la visera son las dos grises, asi que un mapa de gradiente no puede
distinguirlas. Lo que si las distingue es **el propio modelo**: cada cubo declara
las coordenadas UV de sus caras. Se leen de ahi y se pinta cubo por cubo.

Eso hace el resultado AUDITABLE: si el pack cambia su modelo, las regiones
cambian con el, en vez de quedarse pintando donde ya no hay nada.

⚠ NO SE DIBUJA EL EMBLEMA DE LA POKE BALL, todavia. Haria falta saber que cara
  del cubo queda AL FRENTE una vez aplicada la transformacion `head` mas el giro
  de 180 grados del renderizador, y eso no se puede comprobar sin mirarlo en el
  juego. Un emblema en la nuca es peor que ningun emblema, asi que la v1 se queda
  en el color -- que ya identifica cada ball sin ambiguedad.
"""

import io
import json

from PIL import Image

# ---------------------------------------------------------------------------
# ⚠ LOS COLORES SE ESCRIBEN, NO SE MUESTREAN DE COBBLEMON.
#
#   Se intento sacarlos de sus texturas de ball y NO ES FIABLE: el color mas
#   repetido de la mitad de arriba sale `#373737` --la banda negra-- en trece de
#   las treinta y tres, porque cada ball tiene su propio reparto de UV y "la
#   mitad de arriba de la imagen" no es "la tapa de la bola".
#
#   Muestrear habria dado trece gorras negras iguales sin que nada fallara.
#   Escribirlos a mano es mas trabajo y es lo unico que da el color correcto.
# ---------------------------------------------------------------------------
# ⚠ Las de ball RARA cuestan mas, y no es un numero al azar: es la unica forma
#   de que la rejilla signifique algo. Con 398 sombreros todos a 1.200, elegir
#   uno es elegir un dibujo; con tramos, una Master Ball dice algo.
#   Sigue siendo PROVISIONAL como el resto de la economia.
RARAS = {"master", "beast", "cherish", "dream", "luxury"}

BALLS = [
    # (id, nombre, copa, visera)
    ("poke", "Gorra Poké Ball", 0xEE3D34, 0xE8E8E8),
    ("great", "Gorra Super Ball", 0x3B6FD4, 0xE8E8E8),
    ("ultra", "Gorra Ultra Ball", 0x2A2A2A, 0xF5C518),
    ("master", "Gorra Master Ball", 0x7B3FA0, 0xE8E8E8),
    ("premier", "Gorra Premier Ball", 0xF2F2F2, 0xE03030),
    ("dusk", "Gorra Ocaso", 0x1E4038, 0xE88020),
    ("heal", "Gorra Sanadora", 0xF2A0BE, 0xE8E8E8),
    ("net", "Gorra Malla", 0x2E9CA0, 0xE8E8E8),
    ("dive", "Gorra Buceo", 0x3EA8D8, 0xE8E8E8),
    ("quick", "Gorra Veloz", 0xF5D000, 0x2A5FC4),
    ("luxury", "Gorra Lujo", 0x1A1A1A, 0xD4AF37),
    ("level", "Gorra Nivel", 0xE03030, 0x2A2A2A),
    ("friend", "Gorra Amiga", 0x7BC043, 0xE8E8E8),
    ("love", "Gorra Amor", 0xF49AC1, 0xE03030),
    ("moon", "Gorra Lunar", 0x2A3A6B, 0xF5D000),
    ("timer", "Gorra Turno", 0xF2F2F2, 0xE03030),
    ("repeat", "Gorra Acopio", 0xF5C518, 0xE03030),
    ("nest", "Gorra Nido", 0xB9CE3A, 0xE8E8E8),
    ("beast", "Gorra Ente", 0x8FD4F0, 0xF5C518),
    ("cherish", "Gorra Gloria", 0xC4302B, 0xF2F2F2),
]

# El modelo del que salen. Es de Simple Hats y es GRIS de fabrica.
BASE = "baseballhat"


def _regiones(modelo: dict, escala: float):
    """Los rectangulos de pixel de cada cubo, leidos de sus UV.

    ⚠ Las UV van en el espacio de `texture_size` (16x16 por defecto) y la textura
      puede ser mayor --la de la gorra es 32x32--, asi que hay que escalar. Dar
      por hecho que 1 UV = 1 pixel pinta un cuarto de la imagen y deja el resto
      gris, que es un fallo que se ve pero no se entiende.
    """
    cubos = []
    for elemento in modelo.get("elements", []):
        rects = []
        for cara in (elemento.get("faces") or {}).values():
            uv = cara.get("uv")
            if not uv:
                continue
            x0, y0, x1, y1 = uv
            # Las UV pueden venir invertidas (para voltear la cara): se normaliza.
            x0, x1 = sorted((x0, x1))
            y0, y1 = sorted((y0, y1))
            rects.append((int(x0 * escala), int(y0 * escala),
                          max(int(x0 * escala) + 1, round(x1 * escala)),
                          max(int(y0 * escala) + 1, round(y1 * escala))))
        cubos.append(rects)
    return cubos


def _tenir(im: Image.Image, rects, color: int) -> None:
    """Tine los pixeles de esos rectangulos conservando el SOMBREADO.

    El arte gris lleva su propio volumen --la copa mas clara arriba, la sombra
    bajo la visera-- y eso es lo que hace que no parezca un bloque plano. Se
    multiplica el color por la luminancia en vez de rellenar: rellenar da una
    silueta de color, no una gorra.
    """
    r0, g0, b0 = (color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF
    px = im.load()
    for x0, y0, x1, y1 in rects:
        for y in range(max(0, y0), min(im.height, y1)):
            for x in range(max(0, x0), min(im.width, x1)):
                r, g, b, a = px[x, y]
                if a == 0:
                    continue
                # Luminancia del gris original, en 0..1. El 0,55 de referencia es
                # el gris medio de la textura: por encima aclara, por debajo
                # oscurece, asi que el sombreado sobrevive al tenido.
                lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                k = lum / 0.55
                px[x, y] = (min(255, int(r0 * k)), min(255, int(g0 * k)),
                            min(255, int(b0 * k)), a)


def generar(zips):
    """Las gorras, a partir del `baseballhat` de Simple Hats.

    Devuelve [(id, modelo_dict, bytes_textura, nombre, "Luna")], o [] si el
    modelo base no esta -- <b>y eso ultimo importa</b>: si Simple Hats deja de
    traerlo, es mejor que la familia entera desaparezca del catalogo a que salgan
    veinte gorras invisibles.
    """
    base_modelo = base_tex = None
    for _, z, estilo in zips:
        if estilo != "simplehats":
            continue
        for n in z.namelist():
            if n.endswith("/models/item/%s.json" % BASE):
                base_modelo = json.loads(z.read(n))
            if n.endswith("/hats/%s.png" % BASE):
                base_tex = z.read(n)
    if not base_modelo or not base_tex:
        return []

    plantilla = Image.open(io.BytesIO(base_tex)).convert("RGBA")
    tam = base_modelo.get("texture_size") or [16, 16]
    escala = plantilla.width / float(tam[0])
    cubos = _regiones(base_modelo, escala)
    if len(cubos) < 2:
        # La gorra son DOS cubos: copa y visera. Con uno solo no hay dos partes
        # que pintar y el resultado seria una gorra de un color plano.
        return []

    salida = []
    for ident, nombre, copa, visera in BALLS:
        im = plantilla.copy()
        _tenir(im, cubos[0], copa)
        _tenir(im, cubos[1], visera)
        buf = io.BytesIO()
        im.save(buf, "PNG")

        modelo = json.loads(json.dumps(base_modelo))       # copia honda
        modelo["textures"] = {k: "" for k in (modelo.get("textures") or {"0": ""})}
        salida.append(("gorra_%s" % ident, modelo, buf.getvalue(), nombre, "Luna"))
    return salida


def precio(ident: str) -> int:
    """Lo que vale una gorra nuestra. Ver `RARAS`."""
    corto = ident[len("gorra_"):] if ident.startswith("gorra_") else ident
    return 3500 if corto in RARAS else 2000
