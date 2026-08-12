#!/usr/bin/env python3
"""
Importa el arte del Pad desde la carpeta de descargas.

Dos decisiones que conviene entender:

**El marco NO se estira.** Los prompts pedian un bezel hueco para poder
partirlo en nueve rodajas, pero lo que llego es mejor: un aparato completo,
con pantalla azul y paneles laterales verde y morado. Eso esta COMPUESTO —
las esquinas y los salientes no toleran que se estiren. Asi que se dibuja
entero y proporcional, y la rejilla se mete dentro de la pantalla.

**La pantalla se mide, no se adivina.** Se detecta el rectangulo azul
central por color y se guarda en normalizado. Si algun dia cambia el arte,
se vuelve a medir y el codigo no se toca.
"""
import json
from pathlib import Path

from PIL import Image

RAIZ = Path(__file__).resolve().parent.parent
ORIGEN = Path("C:/Users/JUAN/Downloads/interfazpokepad")
PAD = RAIZ / "resourcepack" / "assets" / "lunaeternal" / "textures" / "pad"
ORIGEN_ICONOS = RAIZ / "arte-origen" / "icono"

# El nombre que trae -> el nombre que usa el mod.
ICONOS = {
    "iconpokedex": "pokedex", "cartera": "cartera", "vias": "vias",
    "misiones": "misiones", "kits": "kits", "tienda": "tienda",
    "gts": "gts", "centro": "centro", "puerta": "puerta",
    "gimnasios": "gimnasios", "tesoros": "tesoros", "clan": "clan",
    "cosmeticos": "cosmeticos", "cazas": "cazas", "explorar": "explorar",
}

LADO_ICONO = 128     # se dibuja a ~40 px: 128 da margen de sobra al reducir
ANCHO_PAD = 1024     # 1920 es mas de lo que hace falta y pesa 2,3 MB


def medir_pantalla(im: Image.Image):
    """Rectangulo azul central, en coordenadas normalizadas 0-1.

    Se busca desde el centro hacia fuera. Un barrido completo pillaria
    tambien los paneles verde y morado de los lados."""
    w, h = im.size
    px = im.convert("RGBA").load()

    def azul(p):
        r, g, b, a = p
        return a > 200 and b > 120 and b > r + 40 and g > 60

    def tramo(fijo, horizontal):
        centro = (w if horizontal else h) // 2
        if not azul(px[centro, fijo] if horizontal else px[fijo, centro]):
            raise SystemExit("No encuentro la pantalla azul en el centro")
        a = b = centro
        while a > 0 and azul(px[a - 1, fijo] if horizontal else px[fijo, a - 1]):
            a -= 1
        limite = (w if horizontal else h) - 1
        while b < limite and azul(px[b + 1, fijo] if horizontal else px[fijo, b + 1]):
            b += 1
        return a, b

    x0, x1 = tramo(h // 2, True)
    y0, y1 = tramo(w // 2, False)
    return {"x0": x0 / w, "y0": y0 / h, "x1": (x1 + 1) / w, "y1": (y1 + 1) / h}


def main() -> None:
    if not ORIGEN.exists():
        raise SystemExit(f"No existe {ORIGEN}")

    PAD.mkdir(parents=True, exist_ok=True)
    (PAD / "icono").mkdir(exist_ok=True)

    # --- el aparato
    marco = ORIGEN / "pokepad.png"
    if not marco.exists():
        raise SystemExit("Falta pokepad.png")
    im = Image.open(marco).convert("RGBA")
    pantalla = medir_pantalla(im)
    alto = round(ANCHO_PAD * im.height / im.width)
    im.resize((ANCHO_PAD, alto), Image.LANCZOS).save(PAD / "pokepad.png")
    print(f"  pokepad      {im.width}x{im.height} -> {ANCHO_PAD}x{alto}")
    print(f"  pantalla     x {pantalla['x0']:.4f}-{pantalla['x1']:.4f}  "
          f"y {pantalla['y0']:.4f}-{pantalla['y1']:.4f}")

    # El mod lee esto: la geometria no se escribe a mano en Java.
    destino_json = RAIZ / "mod" / "src" / "main" / "resources" / "pad_layout.json"
    destino_json.write_text(json.dumps(pantalla, indent=2), encoding="utf-8")

    # --- fondos por pantalla, si el usuario los ha generado
    #
    # Uno por app es lo que hace que cada pantalla parezca disenada y no una
    # plantilla rellenada. Si falta alguno, el mod usa el comun; se pueden ir
    # anadiendo de uno en uno.
    n_fondos = 0
    for nombre in ("pokepad", "cazas", "crianza", "cartera", "vias",
                   "misiones", "gts", "tienda", "kits", "tesoros",
                   "mundos", "centro", "clanes", "cosmeticos", "explorar"):
        p_f = ORIGEN / f"fondo_{nombre}.png"
        if not p_f.exists():
            continue
        im_f = Image.open(p_f).convert("RGBA")
        alto_f = round(ANCHO_PAD * im_f.height / im_f.width)
        im_f.resize((ANCHO_PAD, alto_f), Image.LANCZOS)             .save(PAD / f"pokepad_{nombre}.png")
        n_fondos += 1
    if n_fondos:
        print(f"  fondos       {n_fondos} propios por pantalla")

    # --- la celda
    celda = ORIGEN / "celda.png"
    if celda.exists():
        Image.open(celda).convert("RGBA") \
             .resize((128, 128), Image.LANCZOS).save(PAD / "celda.png")
        print("  celda        128x128")

    # --- los iconos
    n = 0
    for origen, nombre in ICONOS.items():
        p = ORIGEN / f"{origen}.png"
        if not p.exists():
            print(f"  {nombre:<12} FALTA ({origen}.png)")
            continue
        img = Image.open(p).convert("RGBA")
        img.resize((LADO_ICONO, LADO_ICONO), Image.LANCZOS) \
           .save(PAD / "icono" / f"{nombre}.png")
        # Y ademas al ORIGEN. Sin esto, la siguiente ejecucion de
        # gen_iconos.py los pisaba con los dibujados por codigo y el arte
        # del usuario desaparecia sin avisar. Paso de verdad.
        ORIGEN_ICONOS.mkdir(parents=True, exist_ok=True)
        img.save(ORIGEN_ICONOS / f"{nombre}.png")
        n += 1
    print(f"  iconos       {n} de {len(ICONOS)}")
    print("\nAhora: python tools/gen_resourcepack.py")


if __name__ == "__main__":
    main()
