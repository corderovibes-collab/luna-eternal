# -*- coding: utf-8 -*-
"""
LA COTA DE MALLA DEL KIT ENTRENADOR, PARA EL VISOR DEL POKEPAD.

⚠⚠⚠ ESTO NO ES UN TRAJE QUE SE LLEVE PUESTO: ES LA VISTA PREVIA DE UN KIT. El
   ENTRENADOR entrega cuatro piezas de armadura DE VERDAD (ver `kits.json`), y
   quien las lleve las lleva porque estan en su ranura de armadura, dibujadas por
   Minecraft como cualquier otra. Lo que hay aqui es el modelo que la pantalla de
   KITS pinta encima del muñeco para que se vea QUE te vas a llevar antes de
   gastar el reclamo.

   Por eso el traje ENTRENADOR sigue con `listo = false` en `Traje.java`: no se
   puede equipar. El dibujado solo lo saca cuando lo estas PREVISUALIZANDO.

⚠⚠ AQUI NO SE DIBUJA NADA. La cota de malla ya esta en el cliente de todo el
   mundo desde que instalo Minecraft, asi que se APUNTA a su textura:

       minecraft:textures/models/armor/chainmail_layer_1.png
       minecraft:textures/models/armor/chainmail_layer_2.png

   Cero bytes en nuestro jar, cero arte que mantener, y nada de Mojang
   redistribuido. Es la misma decision que las 16 medallas de gimnasio.

⚠⚠ Y LAS MEDIDAS TAMPOCO SE INVENTAN: son las de `BipedEntityModel`, el modelo
   con el que Minecraft dibuja TODAS las armaduras. Comprobadas contra el maniqui
   que el autor de la corona dejo dentro de su .bbmodel: coinciden hueso a hueso.
   Si estuvieran mal, la vista previa enseñaria una cosa y el juego otra.

LAS DOS CAPAS, QUE NO SON UN CAPRICHO
=====================================

    capa 1   casco, peto, brazos y BOTAS      holgura 1,0
    capa 2   las PERNERAS                     holgura 0,5

⚠⚠ La holgura distinta es lo unico que impide que perneras y botas parpadeen
   entre ellas: ocupan LA MISMA pierna. Igualarlas --que es lo que uno hace sin
   pensar-- rompe justo lo que Mojang resolvio asi.

⚠ Y por eso las perneras van a la capa 2 y las botas a la 1: cada PNG solo tiene
  pintado lo suyo y el resto es transparente. Cruzarlos deja al jugador con las
  botas dibujadas en los muslos.
"""

from __future__ import annotations

from . import modelo as M
from .modelo import Cubo

CAPA1 = "minecraft:textures/models/armor/chainmail_layer_1.png"
CAPA2 = "minecraft:textures/models/armor/chainmail_layer_2.png"

# ⚠ Las texturas de armadura de vainilla son 64x32, NO cuadradas. El cargador
#   usaba el ancho para las dos medidas; con 64x64 la malla saldria a media
#   altura y estirada.
ANCHO, ALTO = 64, 32

# ⚠⚠ LAS HOLGURAS DE MOJANG, NO UNAS NUESTRAS. Ver la cabecera.
FUERA, DENTRO = 1.0, 0.5

# hueso -> (origen bb, tamaño, uv de caja, espejo)
PIEZAS = {
    "armorHead":      ((-4.0, 24.0, -4.0), (8.0, 8.0, 8.0),  (0, 0),   False),
    "armorBody":      ((-4.0, 12.0, -2.0), (8.0, 12.0, 4.0), (16, 16), False),
    "armorRightArm":  ((-8.0, 12.0, -2.0), (4.0, 12.0, 4.0), (40, 16), False),
    # ⚠⚠ EL BRAZO Y LA PIERNA IZQUIERDOS VAN ESPEJADOS, y comparten UV con los
    #    derechos: en una textura de armadura de 64x32 el lado izquierdo NO tiene
    #    dibujo propio. Es la misma regla que se aplica a los trajes importados
    #    (`_espejar_pares`), y aqui hay que escribirla a mano porque estas piezas
    #    no vienen de ningun .bbmodel.
    "armorLeftArm":   ((4.0, 12.0, -2.0),  (4.0, 12.0, 4.0), (40, 16), True),
    "armorRightLeg":  ((-3.9, 0.0, -2.0),  (4.0, 12.0, 4.0), (0, 16),  False),
    "armorLeftLeg":   ((-0.1, 0.0, -2.0),  (4.0, 12.0, 4.0), (0, 16),  True),
    "armorRightBoot": ((-3.9, 0.0, -2.0),  (4.0, 12.0, 4.0), (0, 16),  False),
    "armorLeftBoot":  ((-0.1, 0.0, -2.0),  (4.0, 12.0, 4.0), (0, 16),  True),
}

# Que capa y que holgura le toca a cada pieza nuestra.
CAPA = {
    "head":  (CAPA1, FUERA),
    "body":  (CAPA1, FUERA),
    "legs":  (CAPA2, DENTRO),   # las perneras van por dentro
    "boots": (CAPA1, FUERA),
}


def entrenador():
    """La malla del kit ENTRENADOR, solo para el visor."""
    t = M.Traje("entrenador", "Kit ENTRENADOR · Cota de malla")

    for pieza, (ruta, holgura) in CAPA.items():
        for hueso in M.PIEZAS[pieza]:
            origen, tam, uv, espejo = PIEZAS[hueso]
            c = Cubo(origen=origen, tam=tam, color=(170, 170, 178),
                     material="metal", inflate=holgura)
            # ⚠ El UV es FIJO: es el de vainilla, no uno que repartamos nosotros.
            #   Si el empaquetador lo tocara, saldria el casco pintado en la bota.
            c.uv = uv
            c.espejo = espejo
            t.poner(hueso, c)
        t.texturas[pieza] = (ruta, ANCHO, ALTO)

    # ⚠ El brillo del encantamiento, porque las piezas se entregan con
    #   Proteccion I. La vista previa tiene que enseñar lo que va a llegar.
    t.brillo = True
    return t, []
