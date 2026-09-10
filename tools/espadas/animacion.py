# -*- coding: utf-8 -*-
"""
LA ELECTRICIDAD: la textura se anima, el modelo NO se toca.

⚠⚠⚠ MINECRAFT ANIMA TEXTURAS DE SERIE, y por eso esto no necesita ni una linea
   de Java ni un cubo mas. Una textura de N fotogramas es UNA TIRA VERTICAL de
   64 x (64*N) con un `.mcmeta` al lado; el juego la cicla solo. El modelo sigue
   apuntando al MISMO nombre de textura y sus UV siguen siendo las del primer
   fotograma -- que es lo que hace que el `pikachu.json` exportado de Blockbench
   NO haya que volver a tocarlo.

⚠⚠⚠ Y EL `.mcmeta` SE VA CON LA TEXTURA O DEJA DE ESTARLO. Esta leccion ya esta
   pagada en este proyecto con el ITEM de la Pokedex: Minecraft busca el
   `.mcmeta` **en el mismo pack que sirvio el PNG**, no en el de debajo. Sin el,
   una tira de 8 fotogramas no da ningun error -- pasa a ser UNA imagen alta y
   estrecha, y el objeto sale con la textura estirada. Por eso los dos ficheros
   se escriben juntos y se comprueban juntos.

⚠⚠⚠ LA REGLA QUE MANDA AQUI: **FUERA DE LA HOJA, TODOS LOS FOTOGRAMAS SON
   IDENTICOS.** La animacion es de la TEXTURA ENTERA, no de una pieza: si un
   solo pixel de la guarda, el mango o el pomo cambiara entre fotogramas, la
   empuñadura parpadearia diez veces por segundo y se leeria como un fallo de
   render, no como electricidad. No da ningun error y es lo unico que puede
   arruinarlo, asi que se comprueba pixel a pixel.

⚠⚠ EL FOGONAZO VIAJA EN EL MODELO, NO EN EL ATLAS. Las caras del nucleo estan
   repartidas por toda la textura --las coloca el empaquetador, no nosotros--
   asi que recorrer la imagen de abajo arriba daria una onda sin sentido. Cada
   pixel se traduce a SU ALTURA EN LA ESPADA y la cresta se mueve sobre esa
   altura: lo que sube es la energia por la hoja, no una fila de pixeles.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from trajes import modelo  # noqa: E402
from . import diseno  # noqa: E402


# ⚠⚠ LOS TONOS SALEN DE LA PALETA DE PIKACHU, como todo lo demas. La tentacion
#    era meter un azul electrico --que es lo que uno dibuja cuando piensa
#    «rayo»-- y habria sido el unico color de la espada que no sale de
#    `pikachu.png`. La energia se hace con los amarillos que ya hay.
BLANCO_PURO = (0xFF, 0xFF, 0xFF)

# Cuantos fotogramas y cuanto dura cada uno, en ticks (1 tick = 1/20 s).
# ⚠ 8 x 2 ticks = 0,8 s de vuelta. Es UN numero: subirlo la calma, bajarlo la
#   pone nerviosa.
FOTOGRAMAS = 8
TICKS = 2


def _altura(pieza, cara, oy, ah, fila):
    """De una fila del atlas a la altura de ese pixel EN LA ESPADA."""
    y0 = pieza.origen[1]
    y1 = y0 + pieza.tam[1]
    # ⚠ Las tapas no tienen recorrido vertical: todo su pixel esta a una altura.
    if cara == "arriba":
        return y1
    if cara == "abajo":
        return y0
    # ⚠⚠ LA FILA 0 DEL RECUADRO ES LA PARTE DE ARRIBA DEL CUBO, no la de abajo.
    #    Lo dice `textura.py`, que aclara la fila `oy` y oscurece la `oy+alto-1`.
    #    Invertirlo haria que la onda bajara mientras el codigo dice que sube.
    return y1 - ((fila - oy + 0.5) / ah) * (y1 - y0)


# ⚠⚠⚠ QUE SE ANIMA Y QUE NO, y esta es LA decision de este fichero.
#    Los dos primeros intentos animaban EL RAYO, y los dos lo estropeaban por el
#    mismo motivo: **le cambiaban el reposo**. Con el rayo virando a amarillo se
#    borraba al pasar la onda; con el rayo amarillo de base desaparecia dentro de
#    la hoja, porque ese amarillo ES el del filo. En los dos casos la señal de
#    identidad de la espada se perdia media vuelta de cada vuelta.
#    LA REGLA QUE QUEDA: **lo que identifica no se anima.** El rayo se queda
#    blanco y quieto, y lo que viaja es un FOGONAZO por el alma de la hoja --
#    identidad quieta, energia en movimiento.
#    ⚠ Y no se vio leyendo el codigo: se vio poniendo los ocho fotogramas en
#      fila y mirandolos. Es la misma leccion que las cuatro pasadas del diseño.
ANIMADOS = ("nucleo", "hoja")


def _pixeles_animados(piezas, cubos):
    """Cada pixel que se mueve, con su papel y la altura a la que le toca."""
    salida = []
    for pieza, cubo in zip(piezas, cubos):
        if pieza.papel not in ANIMADOS:
            continue
        for ox, oy, aw, ah, cara in modelo._caras(cubo):
            for fila in range(oy, oy + ah):
                h = _altura(pieza, cara, oy, ah, fila)
                for col in range(ox, ox + aw):
                    salida.append((col, fila, h, pieza.papel))
    return salida


# La rampa de cada papel: reposo, borde del fogonazo y centro del fogonazo.
# ⚠⚠ EL ALMA LLEVA EL MOVIMIENTO Y EL RAYO SOLO SE ENCIENDE UN PUNTO. El alma va
#    de #E0A226 a #FFDE4C, que es un salto que se ve; el rayo va de #FAFAFA a
#    blanco puro, que es casi nada -- y tiene que ser casi nada, porque un rayo
#    que cambia de color deja de ser el mismo rayo.
# ⚠ Y el centro del alma se queda en el amarillo del FILO, ni un tono mas: si el
#   alma llegara a ser mas clara que el filo, el bisel se invertiria durante dos
#   fotogramas y la hoja parpadearia DE FORMA, que es peor que de color.
RAMPA = {
    "hoja":   (diseno.AMARILLO_BASE, diseno.AMARILLO_MEDIO, diseno.AMARILLO_ALTO),
    "nucleo": (diseno.BLANCO, diseno.BLANCO, BLANCO_PURO),
}

# Lo alto que es el fogonazo, en unidades del modelo: centro y borde.
# ⚠⚠⚠ EMPEZO EN 0,9 Y 2,4 Y ERA INVISIBLE A TAMAÑO DE JUEGO. Es la misma leccion
#    que la punta negra de la oreja: en la lamina, ampliada, una banda estrecha
#    se ve perfectamente; en la mano de un jugador el objeto mide unos pocos
#    pixeles y ahi no llega. **Lo que se juzga es el tamaño al que se va a ver.**
# ⚠⚠ Y SE PROBO UNA VERSION MAS FUERTE QUE LLEGABA A BLANCO EN LA HOJA, Y ERA
#    PEOR: el blanco se comia la hoja entera y **el rayo desaparecia dentro de
#    el** --los dos son blancos-- asi que se leia como un agujero abriendose y
#    cerrandose, no como energia. Es exactamente el fallo de la v1 de la espada,
#    donde el nucleo dominaba y la espada se leia blanca.
#    LA REGLA: **el blanco es del rayo y de nadie mas.** La hoja se enciende en
#    amarillo, y por eso el rayo sigue recortandose contra ella en los ocho.
ALTO_CENTRO = 2.0
ALTO_BORDE = 3.8


def fotogramas(piezas, cubos, base, n=FOTOGRAMAS):
    """Los n fotogramas, partiendo de la textura quieta."""
    pix = _pixeles_animados(piezas, cubos)
    if not pix:
        raise RuntimeError("no hay ni un pixel que animar: el fogonazo no tiene por donde ir")

    bajo = min(h for _, _, h, _ in pix)
    alto = max(h for _, _, h, _ in pix)

    salida = []
    for f in range(n):
        im = base.copy()
        px = im.load()
        # ⚠ El fogonazo entra por debajo y sale por arriba, con margen a los dos
        #   lados: sin el, el fotograma 0 y el ultimo lo tendrian medio dentro y
        #   el bucle daria un tiron visible una vez por vuelta.
        cresta = bajo - 3.0 + (alto - bajo + 6.0) * (f / float(n))
        for col, fila, h, papel in pix:
            reposo, borde, centro = RAMPA[papel]
            d = abs(h - cresta)
            if d < ALTO_CENTRO:
                c = centro
            elif d < ALTO_BORDE:
                c = borde
            else:
                c = reposo
            px[col, fila] = c + (255,)
        salida.append(im)
    return salida


def tira(frames):
    """Los fotogramas apilados, que es lo que Minecraft espera."""
    lado = frames[0].width
    im = Image.new("RGBA", (lado, lado * len(frames)), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        im.paste(f, (0, i * lado))
    return im


def mcmeta(n=FOTOGRAMAS, ticks=TICKS):
    return {
        "animation": {
            # ⚠ `interpolate` va APAGADO a proposito: interpolar inventa tonos
            #   intermedios y eso convierte pixel art en un degradado sucio. La
            #   electricidad tiene que SALTAR de un fotograma al siguiente.
            "interpolate": False,
            "frametime": ticks,
            "frames": list(range(n)),
        }
    }


def escribir(frames, destino_png, ticks=TICKS):
    destino_png = Path(destino_png)
    destino_png.parent.mkdir(parents=True, exist_ok=True)
    tira(frames).save(destino_png)
    meta = destino_png.with_name(destino_png.name + ".mcmeta")
    meta.write_text(json.dumps(mcmeta(len(frames), ticks), indent=2), encoding="utf-8")
    return destino_png, meta


# ------------------------------------------------------------ comprobaciones

def comprobar(piezas, cubos, base, frames, lado):
    """Lo que puede salir mal sin dar ningun error."""
    fallos = []

    # 1 · FUERA DE LA HOJA, TODOS LOS FOTOGRAMAS SON IDENTICOS.
    #     Es LA comprobacion de este fichero: la animacion es de la TEXTURA, no
    #     de una pieza, asi que un pixel de la guarda o del mango que cambie hace
    #     parpadear la empuñadura diez veces por segundo -- y eso no se lee como
    #     electricidad, se lee como un fallo de render.
    movibles = {(c, f) for c, f, _, _ in _pixeles_animados(piezas, cubos)}
    base_px = base.load()
    for i, fr in enumerate(frames):
        px = fr.load()
        for y in range(lado):
            for x in range(lado):
                if (x, y) in movibles:
                    continue
                if px[x, y] != base_px[x, y]:
                    fallos.append("fotograma %d cambia el pixel (%d,%d), que NO es "
                                  "de la hoja ni del rayo: la guarda, el mango o el "
                                  "pomo parpadearian" % (i, x, y))
                    return fallos

    # 2 · LA TIRA MIDE LO QUE DICE EL .mcmeta.
    #     Si sobrara o faltara un fotograma, Minecraft cortaria por donde no es
    #     y la animacion saldria desplazada media espada.
    t = tira(frames)
    if t.width != lado or t.height != lado * len(frames):
        fallos.append("la tira mide %dx%d y tenia que medir %dx%d"
                      % (t.width, t.height, lado, lado * len(frames)))
    if mcmeta(len(frames))["animation"]["frames"] != list(range(len(frames))):
        fallos.append("el .mcmeta no nombra los mismos fotogramas que hay")

    # 3 · LA ONDA SE MUEVE DE VERDAD.
    #     ⚠⚠ Un bucle mal escrito puede dar N fotogramas IDENTICOS, y eso no da
    #        error: da una espada que no se anima, que se lee como «el .mcmeta
    #        no funciona» y manda a buscar el fallo al sitio equivocado.
    if len({fr.tobytes() for fr in frames}) != len(frames):
        fallos.append("hay fotogramas repetidos: la animacion se quedaria quieta")

    # 4 · Y VUELVE AL PRINCIPIO SIN SALTO.
    #     El primero y el ultimo son vecinos en el bucle, asi que si se parecen
    #     menos que dos vecinos cualquiera, se ve un tiron una vez por vuelta.
    def distancia(a, b):
        pa, pb = a.load(), b.load()
        return sum(1 for c, f in movibles if pa[c, f] != pb[c, f])

    saltos = [distancia(frames[i], frames[(i + 1) % len(frames)])
              for i in range(len(frames))]
    if saltos[-1] > 2 * (sum(saltos[:-1]) / max(1, len(saltos) - 1)):
        fallos.append("el bucle da un tiron al volver al principio "
                      "(%d pixeles cambian, frente a %.0f de media)"
                      % (saltos[-1], sum(saltos[:-1]) / max(1, len(saltos) - 1)))
    return fallos
