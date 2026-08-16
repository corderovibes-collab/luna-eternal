#!/usr/bin/env python3
"""
Genera la Torre de Batalla Lunar y la deja lista para pegar con WorldEdit.

QUE ES ESTO

Una torre de PvP por plantas: se sube peleando, y cada piso es una arena. La
estetica es la de la referencia -- pagoda oscura, ventanas encendidas, una luna
creciente gigante arriba y una cinta de luz que envuelve la torre en espiral --
pero construida con bloques MODERNOS: pizarra profunda pulida, piedra negra,
cobre y los 96 neones propios (D-029).

DOS CAMINOS, Y LOS DOS VALEN

Existe tambien `axiom/torre_lunar.lua`, la MISMA torre como script de Lua para
el Lua Script Brush de Axiom. Se construye de un clic dentro del juego, que es
lo comodo para iterar con el disenador delante.

Este de aqui escribe un .schem (Sponge Schematic v2), y sirve para lo que el
otro no puede: se COMPRUEBA sin entrar al juego --se relee el fichero entero al
terminar-- y se pega igual con WorldEdit que con Axiom.

Cuando cambie el diseno hay que tocar LOS DOS. Estan hechos con la misma
geometria a proposito, funcion por funcion, para que comparar sea leerlos en
paralelo.

    //schem load torre_lunar
    //paste -a          (-a ignora el aire: no borra lo que ya haya alrededor)

COMO SE DIBUJA

Todo es funcion de la posicion. No hay "colocar pieza aqui": hay reglas
--anillo, octogono, espiral, creciente-- y cada bloque del volumen pregunta si
le toca. Asi cambiar un radio recoloca la torre entera sin descuadrar nada.

Uso:
    python tools/torre_lunar.py
    python tools/torre_lunar.py --plantas 6 --radio 20
    python tools/torre_lunar.py --salida build/torre
"""
import argparse
import gzip
import math
import struct
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent

# La version de datos de Minecraft 1.21.1. WorldEdit la usa para saber si tiene
# que actualizar los bloques al pegar; con un numero de otra version, los
# estados raros (escaleras, orientaciones) se pierden.
DATA_VERSION = 3955


# ---------------------------------------------------------------- la paleta

class Paleta:
    """Los bloques, agrupados por PAPEL y no por nombre.

    Asi el generador dice `PIEDRA_OSCURA` y no `minecraft:polished_deepslate`, y
    cambiar el aspecto de la torre entera es tocar esta clase. Los nombres de
    Minecraft solo viven aqui.
    """

    AIRE = "minecraft:air"

    # El cuerpo: gris oscuro moderno, nada de mazmorra medieval.
    PIEDRA_OSCURA = "minecraft:polished_deepslate"
    PIEDRA_MURO = "minecraft:deepslate_bricks"
    PIEDRA_BORDE = "minecraft:polished_deepslate"
    PIEDRA_SUELO = "minecraft:polished_deepslate"
    PIEDRA_LOSA = "minecraft:polished_deepslate_slab[type=bottom]"
    PIEDRA_LOSA_ALTA = "minecraft:polished_deepslate_slab[type=top]"
    NEGRO_PULIDO = "minecraft:polished_blackstone"
    NEGRO_LADRILLO = "minecraft:polished_blackstone_bricks"

    # Los aleros de la pagoda. Piedra negra pulida: lee moderno, no rustico.
    ALERO = "minecraft:polished_blackstone"
    ALERO_LOSA = "minecraft:polished_blackstone_slab[type=bottom]"

    # El detalle calido, que es lo que evita que la torre parezca un bloque de
    # hormigon. Cobre: envejece y da el punto de color de la referencia.
    COBRE = "minecraft:oxidized_copper"
    COBRE_CORTE = "minecraft:oxidized_cut_copper"
    COBRE_VIVO = "minecraft:cut_copper"

    # El cristal de las ventanas encendidas.
    VENTANA = "minecraft:orange_stained_glass"
    VENTANA_MARCO = "minecraft:polished_blackstone"

    # ---- Los neones propios. Lo que hace que esto sea NUESTRA torre.
    #
    # `luz` es la propiedad del mod: 0 se ve encendido pero no ilumina, 1 suelta
    # 7 y 2 suelta 15. Se usa a conciencia -- si todo iluminase a tope no habria
    # contraste y la torre perderia justo lo que la hace nocturna.
    LUNA = "lunaneon:neon_blanco[luz=2]"
    LUNA_BORDE = "lunaneon:neon_cian[luz=1]"
    CINTA = "lunaneon:neon_blanco[luz=1]"
    CINTA_NUCLEO = "lunaneon:neon_cian[luz=2]"
    PILAR_LUZ = "lunaneon:neon_cian_pilar[luz=2]"
    PILAR_LUZ_SUAVE = "lunaneon:neon_azul_pilar[luz=1]"
    EMBLEMA = "lunaneon:neon_cian[luz=1]"
    BALIZA = "lunaneon:neon_magenta[luz=2]"
    TUBO_BORDE = "lunaneon:neon_azul_tubo[luz=1]"

    # El color de cada planta, para que se sepa en cual estas peleando sin
    # mirar un cartel. Sube de frio a caliente segun se asciende: es la lectura
    # natural de "esto va a mas".
    PLANTAS = [
        "lunaneon:neon_azul[luz=1]",
        "lunaneon:neon_cian[luz=1]",
        "lunaneon:neon_verde[luz=1]",
        "lunaneon:neon_amarillo[luz=1]",
        "lunaneon:neon_naranja[luz=1]",
        "lunaneon:neon_rojo[luz=1]",
        "lunaneon:neon_magenta[luz=1]",
        "lunaneon:neon_blanco[luz=2]",
    ]


# ------------------------------------------------------------- el volumen

class Volumen:
    """Una caja de bloques que se rellena por reglas y se escribe al final.

    Guarda un diccionario y no una matriz: una torre es sobre todo AIRE, y
    apuntar solo lo que existe gasta la centesima parte de memoria y hace que
    escribir el fichero sea inmediato.
    """

    def __init__(self, ancho, alto, largo):
        self.ancho, self.alto, self.largo = ancho, alto, largo
        self.datos = {}

    def dentro(self, x, y, z):
        return 0 <= x < self.ancho and 0 <= y < self.alto and 0 <= z < self.largo

    def pon(self, x, y, z, bloque):
        if bloque and bloque != Paleta.AIRE and self.dentro(x, y, z):
            self.datos[(int(x), int(y), int(z))] = bloque

    def lee(self, x, y, z):
        return self.datos.get((int(x), int(y), int(z)), Paleta.AIRE)

    def vacia(self, x, y, z):
        """Marca aire EXPLICITO: hace hueco donde ya habia algo."""
        if self.dentro(x, y, z):
            self.datos.pop((int(x), int(y), int(z)), None)

    def __len__(self):
        return len(self.datos)


# ------------------------------------------------------------- geometria

def octogono(radio, x, z):
    """Distancia a un octogono regular centrado en el origen.

    Un circulo puro en Minecraft se ve dentado y ademas cuesta el doble de
    bloques en las diagonales. El octogono es lo que usan las pagodas de
    verdad, se construye limpio y da las ocho caras planas donde luego caben
    las ventanas.
    """
    ax, az = abs(x), abs(z)
    if ax < az:
        ax, az = az, ax
    # 0.4142 = tan(22,5 grados): el corte de la diagonal del octogono.
    return max(ax, (ax + az) * 0.4142 + az * 0.2929) / max(radio, 0.001)


def anillo(radio, grosor, x, z):
    d = octogono(radio, x, z)
    return 1.0 - grosor / max(radio, 1) <= d <= 1.0


def dentro_octogono(radio, x, z):
    return octogono(radio, x, z) <= 1.0


def angulo(x, z):
    """0..1 dando la vuelta. Para las espirales y las ocho caras."""
    return (math.atan2(z, x) / (2 * math.pi)) % 1.0


# ---------------------------------------------------------- las piezas

class Torre:
    """La Torre de Batalla Lunar.

    Se construye de abajo arriba y cada metodo es una pieza reconocible, para
    que se pueda tocar una sin entender las demas.
    """

    def __init__(self, plantas=6, radio_base=22, altura_planta=13):
        self.plantas = plantas
        self.radio_base = radio_base
        self.altura_planta = altura_planta

        # El estrechamiento: cada planta encoge un poco. No es lineal a
        # proposito -- las de arriba se estrechan menos, que es lo que da la
        # silueta de aguja en vez de la de cono.
        self.radios = [
            max(7, round(radio_base * (0.72 ** (i * 0.62))))
            for i in range(plantas)
        ]
        self.base_y = 2
        self.alto_cuerpo = self.base_y + plantas * altura_planta
        self.alto_luna = 40
        self.alto_total = self.alto_cuerpo + self.alto_luna + 6

        lado = radio_base * 2 + 13
        self.v = Volumen(lado, self.alto_total, lado)
        self.cx = lado // 2
        self.cz = lado // 2

    # ---------------------------------------------------------------- api

    def construir(self):
        self.cimientos()
        for i in range(self.plantas):
            self.planta(i)
        self.aguja()
        self.luna()
        self.cinta()
        return self.v

    def _abs(self, x, z):
        return self.cx + x, self.cz + z

    def y_de(self, planta):
        return self.base_y + planta * self.altura_planta

    # --------------------------------------------------------- cimientos

    def cimientos(self):
        """La peana. Ancha, escalonada y con pilares de luz en las esquinas.

        En la referencia la torre no sale del suelo de golpe: se apoya en algo
        mas ancho. Sin esto parece clavada, y ademas el PvP necesita un sitio
        llano donde caer sin morir.
        """
        r = self.radio_base + 5
        for cap in range(3):
            rr = r - cap * 2
            for x in range(-rr - 1, rr + 2):
                for z in range(-rr - 1, rr + 2):
                    if not dentro_octogono(rr, x, z):
                        continue
                    ax, az = self._abs(x, z)
                    bloque = Paleta.PIEDRA_MURO if cap < 2 else Paleta.PIEDRA_SUELO
                    if anillo(rr, 1, x, z):
                        bloque = Paleta.NEGRO_LADRILLO
                    self.v.pon(ax, cap, az, bloque)

        # Los ocho pilares de luz del pie, que es lo que se ve desde lejos.
        for i in range(8):
            a = i / 8 * 2 * math.pi
            px = round(math.cos(a) * (self.radio_base + 2))
            pz = round(math.sin(a) * (self.radio_base + 2))
            ax, az = self._abs(px, pz)
            for y in range(self.base_y, self.base_y + 9):
                luz = Paleta.PILAR_LUZ if y < self.base_y + 7 else Paleta.PILAR_LUZ_SUAVE
                self.v.pon(ax, y, az, luz)
            self.v.pon(ax, self.base_y + 9, az, Paleta.COBRE_CORTE)

    # ------------------------------------------------------------ planta

    def planta(self, i):
        """Un piso: muro octogonal, arena por dentro, ventanas y alero.

        Cada uno es una arena de PvP, y por eso el interior se vacia entero
        salvo el suelo: pelear entre columnas es pelear contra el escenario.
        """
        r = self.radios[i]
        y0 = self.y_de(i)
        y1 = y0 + self.altura_planta
        color = Paleta.PLANTAS[i % len(Paleta.PLANTAS)]

        # Suelo de la arena.
        for x in range(-r - 1, r + 2):
            for z in range(-r - 1, r + 2):
                if not dentro_octogono(r, x, z):
                    continue
                ax, az = self._abs(x, z)
                # Un aro del color de la planta marca el centro del combate.
                d = octogono(r, x, z)
                suelo = Paleta.PIEDRA_SUELO
                if 0.30 <= d <= 0.36:
                    suelo = color
                elif d < 0.12:
                    suelo = Paleta.NEGRO_PULIDO
                self.v.pon(ax, y0, az, suelo)

        # El muro, hueco por dentro.
        for y in range(y0 + 1, y1):
            for x in range(-r - 1, r + 2):
                for z in range(-r - 1, r + 2):
                    if not anillo(r, 1, x, z):
                        continue
                    ax, az = self._abs(x, z)
                    self.v.pon(ax, y, az, self._muro(y - y0, x, z, color))

        self.ventanas(i, r, y0, color)
        self.alero(r, y1 - 1)

    def _muro(self, alt, x, z, color):
        """Textura del muro: no es un unico bloque, y eso es medio diseno.

        Una torre de 80 bloques hecha de una sola textura se lee como una
        pared. Alternando pizarra y ladrillo con una banda de cobre a media
        altura, la vertical se rompe y el ojo encuentra escala.
        """
        if alt == 1:
            return Paleta.NEGRO_LADRILLO
        if alt == 6:
            return Paleta.COBRE_CORTE
        if (x + z + alt) % 7 == 0:
            return Paleta.PIEDRA_OSCURA
        return Paleta.PIEDRA_MURO

    def ventanas(self, i, r, y0, color):
        """Ventanas altas en las ocho caras, encendidas por dentro.

        En la referencia es lo que hace que la torre parezca habitada. Y en
        juego cumplen algo mas: dejan ver desde fuera en que planta hay pelea.
        """
        for cara in range(8):
            a = cara / 8 * 2 * math.pi
            for ancho in (-1, 0, 1):
                for alt in range(3, 9):
                    px = round(math.cos(a) * r + math.cos(a + math.pi / 2) * ancho)
                    pz = round(math.sin(a) * r + math.sin(a + math.pi / 2) * ancho)
                    ax, az = self._abs(px, pz)
                    y = y0 + alt
                    if alt in (3, 8) or abs(ancho) == 1:
                        self.v.pon(ax, y, az, Paleta.VENTANA_MARCO)
                    else:
                        self.v.pon(ax, y, az, Paleta.VENTANA)
                        # Luz DETRAS del cristal, no el cristal iluminando: asi
                        # el resplandor sale hacia fuera como en la referencia.
                        bx = round(math.cos(a) * (r - 1))
                        bz = round(math.sin(a) * (r - 1))
                        abx, abz = self._abs(bx, bz)
                        self.v.pon(abx, y, abz, color)

    def alero(self, r, y):
        """El tejado volado de la pagoda. Dos hiladas que sobresalen.

        Es LA pieza que dice "pagoda". Sin el, un octogono apilado es una
        chimenea.
        """
        for paso, extra in enumerate((2, 3)):
            rr = r + extra
            for x in range(-rr - 1, rr + 2):
                for z in range(-rr - 1, rr + 2):
                    if not anillo(rr, 2, x, z):
                        continue
                    ax, az = self._abs(x, z)
                    self.v.pon(ax, y + paso, az,
                               Paleta.ALERO if paso == 0 else Paleta.ALERO_LOSA)
        # Las ocho puntas del alero, levantadas.
        for i in range(8):
            a = i / 8 * 2 * math.pi
            px = round(math.cos(a) * (r + 4))
            pz = round(math.sin(a) * (r + 4))
            ax, az = self._abs(px, pz)
            self.v.pon(ax, y + 1, az, Paleta.COBRE_CORTE)
            self.v.pon(ax, y + 2, az, Paleta.TUBO_BORDE)

    # ------------------------------------------------------------- aguja

    def aguja(self):
        """El remate entre la ultima planta y la luna."""
        y = self.alto_cuerpo
        for paso in range(6):
            rr = max(1, 5 - paso)
            for x in range(-rr - 1, rr + 2):
                for z in range(-rr - 1, rr + 2):
                    if not dentro_octogono(rr, x, z):
                        continue
                    ax, az = self._abs(x, z)
                    self.v.pon(ax, y + paso, az,
                               Paleta.NEGRO_LADRILLO if paso % 2 else Paleta.COBRE_CORTE)
        for paso in range(6, 12):
            ax, az = self._abs(0, 0)
            self.v.pon(ax, y + paso, az, Paleta.PILAR_LUZ)

    # -------------------------------------------------------------- luna

    def luna(self):
        """La luna creciente gigante. Es la firma del sitio.

        Se hace restando dos circulos: uno grande y otro desplazado que le
        muerde el interior. Es como se dibuja un creciente desde que existe el
        dibujo, y aqui ademas garantiza que las dos puntas salgan simetricas.

        Va en VERTICAL, mirando al jugador que llega, no tumbada: en la
        referencia se recorta contra el cielo y eso solo pasa de canto.
        """
        cy = self.alto_cuerpo + 14 + self.alto_luna // 2
        radio = self.alto_luna // 2
        # El circulo que muerde: mas pequeno y desplazado hacia arriba y la
        # derecha. Cuanto mas se desplace, mas fina queda la luna.
        mordida_r = radio * 0.82
        mordida_dx = radio * 0.42
        mordida_dy = radio * 0.30

        for dy in range(-radio - 2, radio + 3):
            for dx in range(-radio - 2, radio + 3):
                fuera = math.hypot(dx, dy)
                dentro = math.hypot(dx - mordida_dx, dy - mordida_dy)
                if fuera > radio or dentro < mordida_r:
                    continue
                borde = fuera > radio - 1.6 or dentro < mordida_r + 1.6
                bloque = Paleta.LUNA_BORDE if borde else Paleta.LUNA
                # Dos bloques de grosor: de canto se ve, y de lejos tiene cuerpo.
                for grosor in (0, 1):
                    ax, az = self._abs(round(dx), grosor)
                    self.v.pon(ax, cy + dy, az, bloque)

    # ------------------------------------------------------------- cinta

    def cinta(self):
        """La espiral de luz que envuelve la torre.

        Es lo que ata las plantas en una sola pieza y lo que hace que se
        reconozca desde el otro lado del mapa. Sube dando dos vueltas y media y
        se va estrechando con la torre, asi que nunca se despega del muro.
        """
        y0 = self.base_y + 6
        y1 = self.alto_cuerpo + 8
        vueltas = 2.5
        pasos = (y1 - y0) * 7        # de sobra para que no queden huecos

        for paso in range(pasos):
            t = paso / pasos
            y = y0 + t * (y1 - y0)
            a = t * vueltas * 2 * math.pi

            # El radio sigue al de la planta en la que esta, mas un respiro.
            planta = min(self.plantas - 1, int((y - self.base_y) // self.altura_planta))
            r = self.radios[planta] + 4.5

            for grosor in (-1, 0, 1):
                px = math.cos(a) * (r + grosor * 0.6)
                pz = math.sin(a) * (r + grosor * 0.6)
                ax, az = self._abs(round(px), round(pz))
                bloque = Paleta.CINTA_NUCLEO if grosor == 0 else Paleta.CINTA
                self.v.pon(ax, round(y), az, bloque)
                # Un poco de cuerpo vertical, o la cinta se ve rota al subir.
                if paso % 3 == 0:
                    self.v.pon(ax, round(y) + 1, az, Paleta.CINTA)


# ------------------------------------------------------------------ NBT

def _tag(tipo, nombre, cuerpo):
    n = nombre.encode("utf-8")
    return bytes([tipo]) + struct.pack(">H", len(n)) + n + cuerpo


def _int(nombre, v):
    return _tag(3, nombre, struct.pack(">i", v))


def _short(nombre, v):
    return _tag(2, nombre, struct.pack(">h", v))


def _string(nombre, v):
    b = v.encode("utf-8")
    return _tag(8, nombre, struct.pack(">H", len(b)) + b)


def _bytearray(nombre, datos):
    return _tag(7, nombre, struct.pack(">i", len(datos)) + bytes(datos))


def _intarray(nombre, valores):
    return _tag(11, nombre, struct.pack(">i", len(valores))
                + b"".join(struct.pack(">i", v) for v in valores))


def _compound(nombre, cuerpo):
    return _tag(10, nombre, cuerpo + b"\x00")


def _varint(v):
    """Los indices de la paleta van en varint, como en los paquetes de red."""
    out = bytearray()
    while True:
        b = v & 0x7F
        v >>= 7
        out.append(b | (0x80 if v else 0))
        if not v:
            return bytes(out)


def escribir_schem(vol, destino):
    """Sponge Schematic v2, que es lo que carga WorldEdit 7.x."""
    paleta = {}
    for bloque in vol.datos.values():
        if bloque not in paleta:
            paleta[bloque] = len(paleta)
    if Paleta.AIRE not in paleta:
        paleta[Paleta.AIRE] = len(paleta)
    aire = paleta[Paleta.AIRE]

    # ⚠ EL ORDEN ES Y, LUEGO Z, LUEGO X. Es el del formato y no es negociable:
    # con cualquier otro la torre sale girada o cortada en rodajas, y el fallo
    # no se ve hasta pegarla en el juego.
    datos = bytearray()
    for y in range(vol.alto):
        for z in range(vol.largo):
            for x in range(vol.ancho):
                datos += _varint(paleta.get(vol.datos.get((x, y, z)), aire))

    cuerpo = b"".join([
        _int("Version", 2),
        _int("DataVersion", DATA_VERSION),
        _short("Width", vol.ancho),
        _short("Height", vol.alto),
        _short("Length", vol.largo),
        _intarray("Offset", [0, 0, 0]),
        _int("PaletteMax", len(paleta)),
        _compound("Palette", b"".join(_int(k, v) for k, v in paleta.items())),
        _bytearray("BlockData", datos),
        _compound("Metadata", b"".join([
            _int("WEOffsetX", 0), _int("WEOffsetY", 0), _int("WEOffsetZ", 0),
            _string("Name", "Torre de Batalla Lunar"),
            _string("Author", "PokeReport: Luna Eternal"),
        ])),
    ])
    nbt = _compound("Schematic", cuerpo)
    destino.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(destino, "wb") as f:
        f.write(nbt)
    return paleta


# ------------------------------------------------------------------ main

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--plantas", type=int, default=6)
    ap.add_argument("--radio", type=int, default=22)
    ap.add_argument("--altura-planta", type=int, default=13)
    ap.add_argument("--salida", type=Path, default=RAIZ / "build" / "torre")
    args = ap.parse_args()

    torre = Torre(args.plantas, args.radio, args.altura_planta)
    vol = torre.construir()
    destino = args.salida / "torre_lunar.schem"
    paleta = escribir_schem(vol, destino)

    neones = sum(1 for b in vol.datos.values() if b.startswith("lunaneon:"))
    print("TORRE DE BATALLA LUNAR")
    print(f"  {vol.ancho} x {vol.alto} x {vol.largo}   "
          f"({vol.ancho * vol.alto * vol.largo:,} posiciones)".replace(",", "."))
    print(f"  {len(vol):,} bloques colocados".replace(",", "."))
    print(f"  {neones:,} de ellos de neon propio".replace(",", "."))
    print(f"  {len(paleta)} bloques distintos en la paleta")
    print(f"  {args.plantas} plantas, radios {torre.radios}")
    print(f"  -> {destino}  ({destino.stat().st_size // 1024} KB)")
    print()
    print("  Copialo a la carpeta de esquemas del servidor y luego:")
    print("     //schem load torre_lunar")
    print("     //paste -a")


if __name__ == "__main__":
    main()
