# Prompt para diseñar la Plaza Central

## Purpose

Un prompt **listo para copiar y pegar** en Gemini (o cualquier otro modelo) que
devuelve un plan de construcción de la plaza, ejecutable en Axiom.

## Dependencies

- [`construccion.md`](construccion.md) §4 — el solar y sus medidas reales

---

## Por qué el prompt es tan largo

Porque **las restricciones son el 80 % del valor**. Un prompt de una línea
—*"diséñame una plaza épica de Minecraft"*— devuelve una fantasía medieval
genérica que no encaja: se sale de la parcela, tapa las avenidas, usa bloques
que no existen en 1.21.1 y contradice el estilo del servidor.

Todo lo que va en el prompt está **medido del servidor real**, no supuesto.

> **Cámbiale una cosa antes de pegarlo:** en el bloque `DECISIÓN PENDIENTE`
> elige día o noche. Lo demás funciona tal cual.

---

## El prompt

```
Eres un arquitecto de Minecraft especializado en servidores públicos de gran
escala. Vas a diseñar la PLAZA CENTRAL de un servidor de Pokémon llamado
"PokeReport: Luna Eternal". Necesito un plan de construcción ejecutable, no
una descripción bonita.

=== EL SITIO (medidas reales, no negociables) ===

Minecraft 1.21.1, Fabric. Solo bloques de vanilla 1.21.1.
Dimensión propia y vacía (void): no hay terreno, solo cielo. La plaza es hoy
una ISLA FLOTANTE aislada: un cuadrado de suelo rodeado de vacío por los
cuatro lados. Eso es una ventaja, no una limitación — el borde de la isla es
parte del diseño y se ve desde abajo.

  Superficie:           X de -28 a 27, Z de -28 a 27   (56 x 56 bloques)
  Suelo actual:         y = 63 (minecraft:smooth_quartz). Se camina en y = 64
  Altura disponible:    hasta y = 319 arriba, y = -64 abajo (vacío)
  Punto de aparición:   0, 64, 0  — el jugador aparece AQUÍ
  Borde del mundo:      208 de diámetro, centrado en 0,0

Se puede construir HACIA ABAJO: la isla puede tener raíces, cimientos
colgantes o cascadas cayendo al vacío. Nadie lo va a tapar nunca.

MÁS ADELANTE, alrededor, se construirán ocho parcelas más de 56x56 separadas
por avenidas de 8 bloques (X de -36 a -29 y de 28 a 35, igual en Z):

                       NORTE (-Z)
        Salón de Medallas | Laboratorio | Gremio
  OESTE Centro Pokémon    |   PLAZA     | Mercado   ESTE
        Sastrería         | Puerta al Mundo | Reservado
                        SUR (+Z)

No existen todavía, pero la plaza tiene que dejarles sitio: cuatro accesos,
uno por lado, centrados y de al menos 8 de ancho.

=== QUÉ TIENE QUE CUMPLIR ===

1. ENTRADAS. Cuatro accesos, uno por lado, centrados en cada lado y de al
   menos 8 bloques de ancho. Nada puede bloquearlos: por ahí saldrán las
   avenidas hacia las otras ocho parcelas.
1-bis. EL BORDE. La isla se ve desde fuera y desde abajo. Su canto no puede
   ser una pared plana de un bloque: es la primera silueta del servidor.
2. PUNTO DE APARICIÓN. La zona alrededor de 0,64,0 tiene que quedar despejada
   y ser lo primero que impresiona: es literalmente lo primero que ve un
   jugador nuevo del servidor.
3. ORIENTACIÓN. Desde el centro se tiene que entender de un vistazo hacia
   dónde está cada salida. Un jugador nuevo no puede perderse en su propia
   plaza.
4. ESCALA. Tiene que sentirse grande. Usa la altura: hay 250 bloques libres
   por encima y ahora mismo no hay nada que rompa la línea del cielo.
5. NO ES UN EDIFICIO. Es un espacio abierto de reunión. La gente se queda
   aquí a hablar y a comerciar.

=== EL ESTILO (esto es lo que más falla si no se dice) ===

Tema: la luna eterna. Cielo nocturno, lunas crecientes, estrellas, brillo
lunar.

PERO el estilo es el de un juego de Pokémon, NO fantasía gótica. Esta
distinción es la más importante de todo el prompt:

  SÍ                              NO
  formas limpias y redondeadas    piedra tallada, gárgolas
  bordes gruesos y definidos      filigrana recargada
  colores planos, degradados      texturas sucias, desgaste, ruinas
    suaves
  legible de un vistazo           detalle que compite consigo mismo
  amable, pulido, acogedor        gótico, medieval, oscuro, opresivo

El tema lunar se expresa con EL COLOR y LA LUZ, no con formas puntiagudas.
Piensa en un parque temático bien cuidado de noche, no en un castillo
abandonado.

=== DECISIÓN PENDIENTE — elige una y dímelo en tu respuesta ===

[A] DÍA. La dimensión está fijada a mediodía permanente. Seguro, luminoso,
    fácil de leer.
[B] NOCHE. Fijar la dimensión a medianoche permanente. En esta dimensión NO
    aparecen monstruos, así que la noche no tiene ningún coste de juego, y el
    servidor se llama "Luna Eternal": una plaza bañada en luz de luna y
    faroles es mucho más memorable y mucho más difícil de confundir con
    cualquier otro servidor.

Si eliges [B], el diseño de la iluminación pasa a ser lo más importante del
trabajo: la plaza tiene que estar perfectamente legible de noche.

=== QUÉ QUIERO QUE ME DEVUELVAS ===

1. CONCEPTO en 3 frases. Qué es la plaza y cuál es su elemento central.

2. PALETA DE BLOQUES. Máximo 12 bloques, con su ID exacto de 1.21.1
   (minecraft:...), y para qué sirve cada uno. Di explícitamente cuál es el
   bloque dominante y cuál es el acento.

3. PLANTA. Descríbeme el suelo por anillos o cuadrantes, con coordenadas
   reales dentro de -28..27. Quiero saber qué bloque va en cada zona.

4. ELEMENTO CENTRAL. Qué hay en el centro (o alrededor de 0,64,0), con sus
   dimensiones y su altura. Es la fotografía que la gente va a compartir.

5. VERTICALIDAD. Qué rompe la línea del cielo y a qué altura. Y qué cuelga por
   debajo de la isla: hasta y = -64 no hay nada, y ese espacio es gratis.

6. ILUMINACIÓN. Qué bloques de luz, dónde, y qué nivel de luz consiguen.

7. ORDEN DE CONSTRUCCIÓN en pasos numerados, del primero al último, de forma
   que se pueda parar a mitad y que lo hecho ya se vea bien.

8. AXIOM. Para cada paso, qué herramienta de Axiom conviene. Las que hay:
   - Selección: box select, lasso select, magic select, floodfill
   - Pintura: painter, gradient painter, noise painter, biome painter
   - Forma: shape, sculpt draw, extrude, slope, path, rock, modelling
   - Suavizado: smooth, blend, melt, roughen, distort
   - Herramientas de constructor: clone, move, stack, smear, erase,
     setup_symmetry
   - Capacidades: no clip, infinite reach, force place, replace mode, no
     updates
   Ten en cuenta que "setup_symmetry" permite construir un cuarto de la plaza
   y espejarlo: si el diseño es radial o simétrico, dilo y aprovéchalo.

9. LO QUE NO HAY QUE HACER. Tres errores concretos que arruinarían esta plaza
   en particular.

=== FORMATO ===

Directo y accionable. Nada de introducciones ni de "¡qué gran proyecto!".
Coordenadas y IDs de bloque siempre que puedas. Si algo de lo que te pido es
mala idea, dilo y propón la alternativa.
```

---

## Después de que responda

1. **Léelo con el solar delante.** Si el diseño se sale de `-28..27` o tapa una
   avenida, no vale por bonito que sea.
2. **Si eliges noche**, el cambio se hace aquí:
   `mod/src/main/resources/data/lunaeternal/dimension_type/ciudadela.json`,
   campo `fixed_time`: `6000` es mediodía, `18000` medianoche. Requiere
   redesplegar el mod y reiniciar.
3. **Guarda un `.schem` al terminar la sesión** (`//pos1 · //pos2 · //copy ·
   //schem save plaza-v1`). Pesa poco y es mejor copia de seguridad que la del
   mundo.
4. **Avísame con las coordenadas finales** para fijar el punto de aparición
   real y colocar los puntos de viaje.

## Para las demás parcelas

El mismo prompt sirve cambiando tres cosas: el nombre de la zona, sus
coordenadas (las de cada parcela están en [`construccion.md`](construccion.md)
§4) y el apartado *QUÉ TIENE QUE CUMPLIR*, que es lo único específico de cada
sitio. El bloque de ESTILO no se toca nunca: es lo que hace que las nueve
parcelas parezcan la misma ciudad.

## Related Systems

- [Construcción](construccion.md) · [Estructura del mundo](world-structure.md)
