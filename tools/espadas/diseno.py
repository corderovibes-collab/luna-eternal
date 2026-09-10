# -*- coding: utf-8 -*-
"""
COLMILLO DE TRUENO — la espada de Pikachu, pieza a pieza.

⚠⚠⚠ TODO LO DE AQUI SALE DE MEDIR EL MODELO DE REFERENCIA, NO DE INVENTAR.
   Y desde hoy no hay que creerselo: `espadas/referencia.py` lee la copia del
   repo y COMPRUEBA cada afirmacion de esta lista en cada pasada.

     · Son DOS sistemas de coordenadas en un fichero. El esqueleto de jugador
       va en unidades ENTERAS de Minecraft (cabeza 8x8x8 en y=24, torso 8x12x4,
       brazo 4x12x4, orejas 4x5, cola 6/8/13) y el DISFRAZ va en numeros sueltos
       de una importacion.
     · ⚠⚠⚠ AQUI PONIA QUE EL DISFRAZ IBA EN UNA REJILLA DE U = 0,75*raiz(2)
       CUANTIZADA A U/4, Y ES FALSO. Medido sobre el fichero: de sus 144
       coordenadas, **18** caen en U/4; de sus 72 tamaños, 28. Ni en U, ni en
       U/2, ni en U/4. NO HAY REJILLA QUE COPIAR.
       ⚠ Y eso refuerza la decision en vez de tumbarla: como la referencia no
         tiene rejilla propia, la espada define la suya (0,25) sin heredar el
         artefacto de la importacion.
     · ⚠⚠ Y AQUI PONIA «ningun elemento tiene rotacion»: **doce SI rotan**.
       Los doce son `locator`; de los 59 CUBOS, cero. La frase era cierta de
       los cubos y falsa como estaba escrita -- que es la peor forma de estar
       equivocado, porque suena comprobada.
     · Lo que SI se copia es la FINURA: la placa mas delgada del disfraz mide
       0,264. Aqui la rejilla es 0,25, que da el mismo detalle visual y ademas
       cuadra con el esqueleto entero y se edita comodo en Blockbench.
     · Densidad de textura: la cabeza mide 8 unidades y ocupa 8 pixeles ->
       UN TEXEL POR UNIDAD. La espada usa la misma.
     · La paleta son los colores reales de `pikachu.png`, contados por
       frecuencia. No se invento ni un tono.

<h2>LA ESCALA, Y POR QUE 26</h2>

El personaje mide 32 hasta la coronilla y su mano cae a y=12. Con la espada
empuñada por el mango, 26 de hoja total ponen la punta justo por encima de la
cabeza: epica de verdad y todavia creible en una mano de 4 de ancho. A 32
seria mas alta que el personaje --un anime prop, no un arma-- y a 20 se
quedaria en cuchillo.

<h2>DE DONDE SALE CADA FORMA</h2>

Nada de pegar un rayo amarillo sobre una espada normal. Las cuatro señas de
Pikachu entran como ARQUITECTURA:

  · las ALAS DE LA GUARDA son sus orejas -- suben, se estrechan y terminan en
    NEGRO, que es exactamente como el modelo pinta las puntas de las orejas;
  · el RAYO recorre la hoja por dentro, en zigzag, como NUCLEO y no como
    dibujo: tiene relieve de verdad y no toca el contorno;
  · las BANDAS del mango son las rayas MARRONES de la cola;
  · los dos puntos ROJOS de la guarda son las mejillas.

⚠⚠ AQUI PONIA QUE LA HOJA LLEVABA PUAS, Y YA NO LAS LLEVA. Salian a la misma
   altura que las alas de la guarda, asi que la espada tenia CUATRO brazos y la
   silueta se leia como un candelabro. Se retiraron mirando la lamina, y el
   rayo se metio DENTRO de la hoja -- que es donde no compite con el perfil.
   Queda escrito porque el texto sobrevivio al cambio y estuvo describiendo una
   pieza que no existe.
"""

from __future__ import annotations

import sys
from dataclasses import dataclass
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from trajes.modelo import Cubo  # noqa: E402


# ---------------------------------------------------------------- la paleta
#
# ⚠ Los once tonos salen de contar los pixeles de `pikachu.png`. Los cinco
#   amarillos son su rampa completa; el marron es el de las rayas de la cola;
#   los carbones son las puntas de las orejas y los ojos; el rojo son las
#   mejillas. Añadir un color de fuera se notaria: la espada dejaria de
#   pertenecer al mismo set.
AMARILLO_ALTO = (0xFF, 0xDE, 0x4C)
AMARILLO = (0xF7, 0xCC, 0x3D)
AMARILLO_MEDIO = (0xEA, 0xB6, 0x31)
AMARILLO_BASE = (0xE0, 0xA2, 0x26)
AMBAR = (0xD6, 0x8E, 0x1B)
AMBAR_OSCURO = (0xA8, 0x66, 0x10)
MARRON = (0x7F, 0x35, 0x00)
MARRON_OSCURO = (0x72, 0x2A, 0x00)
CARBON_CLARO = (0x34, 0x34, 0x38)
CARBON = (0x2C, 0x2B, 0x30)
NEGRO = (0x22, 0x21, 0x26)
ROJO = (0xF2, 0x42, 0x36)
ROJO_CLARO = (0xFE, 0x58, 0x46)
BLANCO = (0xFA, 0xFA, 0xFA)


# ⚠⚠ EL PAPEL, NO EL COLOR. Cada pieza dice QUE ES y el color sale de aqui:
#    asi retocar la paleta es tocar una linea, y no puede pasar que dos piezas
#    del mismo material acaben con tonos distintos por un descuido.
PAPEL = {
    # ⚠⚠ EL ALMA VA MAS OSCURA QUE EL FILO, y es lo que hace que se lea
    #    «afilada». En la primera pasada alma y filo eran tonos vecinos y la
    #    hoja salia plana: un rectangulo amarillo, no una espada.
    "hoja":      AMARILLO_BASE,
    "filo":      AMARILLO_ALTO,
    "punta":     AMARILLO_ALTO,
    # ⚠⚠⚠ EL NUCLEO ES BLANCO ENTERO Y LO QUE ZIGZAGUEA ES SU FORMA. Aqui
    #    hubo dos intentos peores: pintarle una chispa EN LA TEXTURA --que a un
    #    texel por unidad es imposible, porque una tira de 0,5 de ancho ocupa
    #    UN pixel-- y despues alternarle dos tonos por tramos, que a tamaño de
    #    juego se lee como suciedad. Lo que hace el rayo es la GEOMETRIA.
    "nucleo":    BLANCO,
    "guarda":    CARBON,
    "ala":       AMARILLO_BASE,
    "ala_punta": NEGRO,
    "mejilla":   ROJO,
    "mango":     NEGRO,
    "banda":     MARRON,
    "pomo":      AMARILLO_BASE,
    "pomo_rayo": AMARILLO_ALTO,
}


@dataclass
class Pieza:
    """Un cubo con su nombre y su sitio en el arbol."""

    nombre: str
    grupo: str
    papel: str
    origen: tuple
    tam: tuple
    # ⚠⚠⚠ UNA EXCEPCION DECLARADA, NO UN AGUJERO. La comprobacion de simetria
    #    existe porque un lado mas ancho que el otro NO da ningun error y solo
    #    se ve de frente y con cuidado. Pero el rayo del nucleo es asimetrico
    #    A PROPOSITO --un rayo simetrico no es un rayo-- asi que en vez de
    #    apagar la comprobacion, la pieza lo DICE. Asi la lista de piezas
    #    asimetricas se imprime en cada pasada y nadie puede colar una sin
    #    querer.
    simetrica: bool = True

    def cubo(self) -> Cubo:
        return Cubo(origen=self.origen, tam=self.tam, color=PAPEL[self.papel],
                    material="metal")


# ------------------------------------------------------------- las medidas
#
# ⚠ Un solo sitio para los tramos. Escritos en cada pieza, mover la guarda
#   obligaria a corregir a mano quince numeros y el primero que se olvidara
#   dejaria una pieza flotando -- sin dar ningun error.
POMO_Y = (0.0, 2.0)
MANGO_Y = (2.0, 7.0)
GUARDA_Y = (7.0, 8.5)
HOJA_Y = (8.5, 26.0)

REJILLA = 0.25          # la unidad minima; ver el docstring
LARGO_TOTAL = HOJA_Y[1]


def _par(nombre, grupo, papel, x0, x1, y0, y1, z0, z1):
    """
    Una pieza y su espejo en X, de golpe.

    ⚠⚠ LA SIMETRIA SE CONSTRUYE, NO SE COMPRUEBA DESPUES. Escribiendo los dos
       lados a mano, un 2,25 tecleado como 2,5 en el lado izquierdo da una
       espada torcida que NO da ningun error y que solo se ve de frente y con
       cuidado. Aqui el lado izquierdo no se escribe: se calcula.
    """
    izq = Pieza(nombre + "_Left", grupo, papel,
                (x0, y0, z0), (x1 - x0, y1 - y0, z1 - z0))
    der = Pieza(nombre + "_Right", grupo, papel,
                (-x1, y0, z0), (x1 - x0, y1 - y0, z1 - z0))
    return [izq, der]


def _centro(nombre, grupo, papel, ancho, y0, y1, z0, z1):
    """Una pieza centrada en X. El ancho se reparte solo."""
    return Pieza(nombre, grupo, papel,
                 (-ancho / 2.0, y0, z0), (ancho, y1 - y0, z1 - z0))


def piezas():
    """Las piezas de la espada, en orden de lectura."""
    p = []

    # ---------------------------------------------------------------- POMO
    # Contrapeso con un destello de rayo cruzado. Cierra la silueta por abajo
    # y da peso visual al mango, que si no parece un palo.
    # ⚠⚠⚠ LA TAPA MEDIA LO MISMO QUE LA EMPUÑADURA Y ERA DEL MISMO TONO, asi
    #    que NO CERRABA LA ESPADA: lo que se veia debajo del pomo era «el mango
    #    sigue», o sea una espiga sin rematar. Y no se caza mirando el codigo
    #    --las dos piezas son correctas por separado-- sino MIDIENDO LA SILUETA:
    #    el perfil decia 1,5 · 2,5 · 4,0 · 2,5 · 1,5, y el 1,5 de abajo era
    #    identico al del mango.
    #    Hoy es un PIE: mas ancho que el pomo y de su color, asi que el punto
    #    mas bajo es tambien el mas ancho y la silueta termina.
    #    ⚠ No hace falta oscurecerlo a mano: `textura.py` ya pinta la cara de
    #      abajo mas oscura, asi que el escalon se ve solo.
    p.append(_centro("Pommel_Cap", "Pommel", "pomo", 3.0, 0.0, 0.5, -1.0, 1.0))
    p.append(_centro("Pommel_Main", "Pommel", "pomo", 2.5, 0.5, 2.0, -1.0, 1.0))
    p.append(_centro("Pommel_Bolt", "Pommel", "pomo_rayo", 4.0, 0.75, 1.25, -0.5, 0.5))

    # --------------------------------------------------------------- MANGO
    p.append(_centro("Handle_Main", "Handle", "mango", 1.5, *MANGO_Y, -0.75, 0.75))
    # Las tres bandas de la cola. Sobresalen 0,25 por cada lado: son relieve,
    # no pintura.
    for i, y in enumerate((2.75, 4.0, 5.25), start=1):
        p.append(_centro("Handle_Grip_%d" % i, "Handle", "banda",
                         2.0, y, y + 0.5, -1.0, 1.0))

    # -------------------------------------------------------------- GUARDA
    p.append(_centro("Guard_Main", "Guard", "guarda", 4.0, *GUARDA_Y, -0.75, 0.75))
    # Las mejillas, una a cada lado y por delante Y por detras: la espada se
    # ve igual de bien desde la espalda, que es la vista que siempre se olvida.
    p += _par("Guard_Cheek_Front", "Guard", "mejilla", 0.5, 1.5, 7.25, 8.0, -1.0, -0.75)
    p += _par("Guard_Cheek_Back", "Guard", "mejilla", 0.5, 1.5, 7.25, 8.0, 0.75, 1.0)
    # Las alas-oreja: suben, se estrechan y acaban en negro.
    # ⚠⚠ EN LA PRIMERA PASADA NO SE LEIAN COMO OREJAS: eran anchas, subian poco
    #    y su punta negra media un pellizco. Una oreja de Pikachu hace tres
    #    cosas -- SALE, SE ESTRECHA y ACABA EN NEGRO-- y el negro tiene que ser
    #    un tercio del largo o no se ve a tamaño de juego.
    p += _par("Guard_Wing_Base", "Guard", "ala", 2.0, 3.25, 7.25, 8.5, -0.5, 0.5)
    p += _par("Guard_Wing_Mid", "Guard", "ala", 2.75, 3.75, 8.5, 10.0, -0.5, 0.5)
    p += _par("Guard_Wing_Tip", "Guard", "ala_punta", 3.0, 3.75, 10.0, 11.5, -0.5, 0.5)

    # ---------------------------------------------------------------- HOJA
    # El alma. Se estrecha una vez a dos tercios: un cubo no se afila, pero un
    # escalon a la altura correcta basta para que deje de leerse como barra.
    p.append(_centro("Blade_Main_Lower", "Blade", "hoja", 2.0, HOJA_Y[0], 18.0, -0.5, 0.5))
    p.append(_centro("Blade_Main_Upper", "Blade", "hoja", 1.5, 18.0, 22.5, -0.5, 0.5))

    # Los filos, mas finos que el alma (0,25 contra 1): eso es el bisel, y es
    # lo unico que separa «espada» de «lingote». Siguen al alma en su escalon.
    p += _par("Blade_Edge_Low", "Blade", "filo", 1.0, 1.5, HOJA_Y[0], 18.0, -0.25, 0.25)
    p += _par("Blade_Edge_Up", "Blade", "filo", 0.75, 1.25, 18.0, 22.5, -0.25, 0.25)

    # ⚠⚠⚠ AQUI HUBO UNAS PUAS QUE SALIAN DE LA HOJA, Y SE RETIRARON MIRANDO
    #    LA LAMINA. El problema no era su forma: era que quedaban A LA MISMA
    #    ALTURA que las alas de la guarda, asi que la espada tenia CUATRO
    #    brazos y se leia como un candelabro. Dos pares de salientes es uno de
    #    mas, y eso no se ve en el codigo -- se ve dibujado.
    #
    #    La salida no fue renunciar al rayo, sino SACARLO DE LA SILUETA: el
    #    perfil se queda limpio de espada y el rayo lo hace el NUCLEO, que
    #    puede zigzaguear de verdad porque no toca el contorno. Es mejor que lo
    #    que habia: la silueta gana y el rayo tambien.
    #
    #    ⚠ Y por eso el nucleo es ASIMETRICO en X, declarado pieza a pieza: un
    #      rayo simetrico no es un rayo, es un adorno.
    RAYO = [
        (0.00, 0.50, 9.0, 11.0), (-0.50, 0.50, 11.0, 11.5),
        (-0.50, 0.00, 11.5, 13.5), (-0.50, 0.50, 13.5, 14.0),
        (0.00, 0.50, 14.0, 16.0), (-0.50, 0.50, 16.0, 16.5),
        (-0.50, 0.00, 16.5, 18.5), (-0.50, 0.50, 18.5, 19.0),
        (0.00, 0.50, 19.0, 21.0),
    ]
    for i, (x0, x1, y0, y1) in enumerate(RAYO, start=1):
        for cara, z0 in (("Front", -0.75), ("Back", 0.5)):
            p.append(Pieza("Core_%s_%d" % (cara, i), "Energy_Core", "nucleo",
                           (x0, y0, z0), (x1 - x0, y1 - y0, 0.25),
                           simetrica=False))

    # La punta, en tres escalones. Un cubo no se estrecha, asi que la conica se
    # hace escalonando -- y tres pasos bastan para que a tamaño de juego se lea
    # como punta y no como escalera.
    # ⚠ Un ancho CENTRADO solo cae en la rejilla si es multiplo de 0,5: 1,25
    #   deja los bordes en 0,625. Lo cazo la comprobacion de rejilla, no la
    #   vista -- a ojo se veia perfecta.
    p.append(_centro("Blade_Tip_1", "Blade", "punta", 1.5, 22.5, 24.0, -0.5, 0.5))
    p.append(_centro("Blade_Tip_2", "Blade", "punta", 1.0, 24.0, 25.25, -0.5, 0.5))
    p.append(_centro("Blade_Tip_3", "Blade", "punta", 0.5, 25.25, 26.0, -0.25, 0.25))

    return p


# ⚠⚠ EL ARBOL SE DECLARA, no se deduce del nombre. Deducirlo por prefijo
#    funciona hasta que alguien llama a una pieza «Blade_Core» y se va al
#    grupo equivocado sin avisar.
ORDEN_GRUPOS = ["Blade", "Energy_Core", "Guard", "Handle", "Pommel"]
