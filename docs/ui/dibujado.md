# Cómo se dibuja una pantalla

## Purpose

Las reglas técnicas que cumple **toda** pantalla del cliente. No es el diseño
—eso está en [`interfaz-cliente.md`](interfaz-cliente.md)— ni el arte —eso está
en [`prompts-arte-pokepad.md`](prompts-arte-pokepad.md)—: es **qué hay que
hacer para que lo que se ve en pantalla sea lo que hay en el PNG**.

Todo lo de aquí se aprendió depurando el PokePad el 2026-08-14, en el juego y
con las capturas delante. Cada regla costó horas, y **cuatro diagnósticos
equivocados**.

## Dependencies

- [`interfaz-cliente.md`](interfaz-cliente.md) — por qué hay mod de cliente
- [`prompts-arte-pokepad.md`](prompts-arte-pokepad.md) §8 — las medidas del Pad

## Current Status

`PokePadScreen` cumple las cinco reglas. Cualquier pantalla nueva las hereda.

---

## 1. ⚠️ Encender la mezcla alfa. SIEMPRE

**Es la regla que más caro sale olvidar, y no da ningún error.**

```java
RenderSystem.enableBlend();
RenderSystem.defaultBlendFunc();
ctx.drawTexture(textura, x, y, ancho, alto, 0f, 0f, natW, natH, natW, natH);
RenderSystem.disableBlend();
```

Sin `enableBlend()`, **el juego trata cualquier alfa mayor que cero como
opaco**. Medido píxel a píxel comparando una captura del juego contra el PNG:

```
alfa 0        ->  dibujado correcto  (4.715 px)
alfa 1..200   ->  sale el COLOR CRUDO del arte, ignorando la transparencia
```

Un píxel con alfa 1 aporta un 0,4 % y salía a todo color.

### Cómo se manifiesta

Se ve como suciedad **alrededor de los dibujos**, y el síntoma cambia según lo
que el arte guarde en sus píxeles invisibles:

| Lo que hay escondido | Lo que se ve |
|---|---|
| Colores saturados del generador | **Motas rojas, verdes y cianes** sobre el contorno |
| El color del propio contorno | Un **cerco negro** rodeando cada icono |

Son el mismo fallo. Parecen dos problemas distintos y llevan a perseguir el
arte, que es donde **no** está la causa.

Cobblemon enciende la mezcla en cada dibujo de interfaz
(`api/gui/GuiUtils.kt`), y por eso a ellos no les pasa.

---

## 2. Dibujar a píxeles reales de pantalla

El arte se dibuja al tamaño que mide, no al tamaño de la textura multiplicado
por el *GUI Scale*:

```java
k = min(1.0, cabe) / guiScale;      // de píxel del arte a unidad de interfaz
```

Ampliar una imagen pequeña es lo que la emborrona. Por eso el chasis mide
**1380 × 828**: los dos números son divisibles entre **1, 2, 3, 4 y 6**, los
valores del *GUI Scale*, así que a tamaño real un texel cae exactamente en un
píxel sea cual sea el ajuste del jugador.

**Las medidas nuevas cumplen esa condición o no valen.** Los botones llegaron
a 120 × 96 en vez de los 128 × 96 previstos y se aceptaron justamente porque
120 y 96 también se dividen entre 1, 2, 3, 4 y 6.

---

## 3. ⚠️ Encoger con vecino más próximo TIRA líneas

Cuando la ventana no da para el tamaño real, el Pad se reduce. Y ahí hay una
trampa: **Minecraft dibuja con vecino más próximo, y encoger con vecino más
próximo no suaviza — descarta filas y columnas enteras**.

A 0,976 se pierde una de cada cuarenta. Se ve como rayas finas cruzando el
chasis y como contornos partidos a trozos alrededor de los iconos.

Por eso se comprueba si ha salido exacto, y **solo si no lo es** se pasa a
filtrado lineal, que reparte el error en vez de tirar líneas:

```java
boolean exacto = Math.round(ancho * gui) == NAT_ANCHO
              && Math.round(alto * gui) == NAT_ALTO;
filtrar(!exacto);
```

La comprobación se hace **de verdad**, no suponiendo que el *GUI Scale* sea uno
de 1, 2, 3, 4 o 6: en pantallas grandes el juego deja elegir también 5 y 7.

> **Y no se reserva margen.** Hubo un 0,98 «para que no pegue con el borde» y
> volvía borrosa cualquier ventana de entre 1380 y 1409 de ancho, a cambio de
> un hueco que nadie mira.

---

## 4. Declarar `clamp` en cada textura

Cada PNG lleva su `.png.mcmeta`:

```json
{
  "texture": {
    "blur": false,
    "clamp": true
  }
}
```

Sin `clamp`, OpenGL repite la textura y al filtrarla el muestreo del borde se
mezcla con **el borde contrario** —el píxel de arriba con el de abajo—, lo que
dibuja un marco fino alrededor de cada icono. Solo aparece cuando hay que
filtrar, que es justo cuando no se puede permitir.

`blur` va en `false` porque el filtro se decide en tiempo real (§3), y eso el
`.mcmeta` no lo sabe.

Los genera `tools/gen_pokepad.py` junto a cada textura, para no tener que
acordarse.

---

## 5. Sangrar el alfa del arte

Un PNG guarda color y transparencia por separado, así que **un píxel puede ser
invisible y llevar un color dentro**. El arte generado con IA llega lleno de
eso: en `explorar` había 383 píxeles con alfa entre 1 y 23 y 22 de ellos
guardaban verde, azul o rojo **puros**.

`sangrar_alfa()` le pone a lo invisible el color de su vecino visible. **No
toca el alfa**, así que la imagen compuesta es idéntica píxel a píxel.

Con la regla §1 aplicada esto ya no salva de nada — pero dejar colores raros
escondidos donde no se ven es una bomba de relojería, y ya explotó una vez.

> ⚠️ **Lo que NO vale para limpiar arte.** Se probaron dos cosas y las dos
> destruían el dibujo: una **mediana 3×3** se come el contorno (es de 1 píxel,
> y para una mediana eso es indistinguible del ruido), y filtrar por «píxel
> aislado» lo adelgaza. Sangrar el alfa es seguro **porque no toca ni un píxel
> visible**.

---

## 6. ⚠️⚠️ Si una API recibe un objeto mutable, va a mutarlo

**La regla que más cara ha salido hasta ahora: cuatro intentos fallidos y una
tarde entera.**

`drawProfilePokemon` de Cobblemon hace esto, en su línea 143:

```kotlin
rotation.conjugate()
entityRenderDispatcher.overrideCameraOrientation(rotation)
```

`conjugate()` **modifica el cuaternión que se le pasa**. No devuelve una copia.
La tienda de cosméticos le pasaba una constante compartida:

```java
private static final Quaternionf GIRO = new Quaternionf().rotationXYZ(...);
```

así que **cada llamada invertía el mismo objeto**.

### El síntoma mentía, y ahí está la lección

Los modelos titilaban. Pero solo **al abrir el previsualizador** — la rejilla
sola se veía estable. Eso hizo buscar el fallo en el panel durante tres
intentos:

| Intento | Hipótesis | Por qué era razonable | Por qué no servía |
|---|---|---|---|
| 1 | El búfer de la interfaz | `DrawContext` acumula y el 3D dibuja ya | Cierto, pero no era la causa |
| 2 | La profundidad (`z=100`) | Cobblemon usa `z=0` | Cierto, y tampoco |
| 3 | Estado compartido por especie | Cobblemon da uno por widget | Cierto, y tampoco |

Los tres eran **correcciones legítimas** —el código quedó mejor— y ninguno
tocaba el problema.

La explicación: con 8 celdas el cuaternión se invierte 8 veces por fotograma.
**Par**, vuelve al valor original, y la rejilla parece estable. Con el
previsualizador son **nueve**: impar, la paridad cambia en cada fotograma y el
modelo alterna entre dos orientaciones.

> **«Solo falla al previsualizar» no era una pista sobre el previsualizador: era
> una pista sobre la PARIDAD del número de llamadas.** Leer bien el síntoma y mal
> lo que significa cuesta lo mismo que no leerlo.

### Qué hacer

Pasar siempre un objeto **nuevo**, o una copia:

```java
private static Quaternionf giro() {          // NO una constante
    return new Quaternionf().rotationXYZ(0.35f, -0.55f, 0f);
}
```

`static final` protege la **referencia**, no el **contenido**. Con cuaterniones,
matrices, vectores y listas, esa distinción es la que hace daño — y Cobblemon
crea uno nuevo en cada llamada (`StorageSlot.render`) justo por esto.

## 7. Cómo se depura esto, que es lo que más tiempo ahorra

**Una captura pegada en un chat no sirve.** La compresión inventa exactamente
el mismo tipo de motas de colores que se están buscando, y lleva a diagnosticar
el arte cuando el fallo está en el código. Pasó, y tres veces.

El procedimiento que sí funciona:

1. En el juego, **F2**. Se guarda como PNG sin pérdida en
   `.lunaeternal/instance/screenshots/`.
2. Leer ese fichero **del disco**, no de una imagen reenviada.
3. Localizar el Pad por las **líneas cian**, que son inconfundibles, y de paso
   medir la escala real: si no da `1,0000`, el Pad se encogió y eso ya explica
   parte de lo que se ve.
4. Componer el PNG de origen sobre el color de la celda y **restar**.
5. Mirar la tabla `RENDER · ESPERADO · ARTE RGB · alfa`. Ahí se ve de un
   vistazo si el juego está pintando el color crudo, si hay desplazamiento o si
   el arte trae basura.

Ese último paso es el que encontró el fallo real: `RENDER` era idéntico a
`ARTE RGB` con `alfa 1`.

> **Comprueba también qué jar tiene puesto el jugador.** Varias veces la
> captura era anterior al arreglo que se estaba evaluando. Se compara el SHA1
> del jar instalado contra el compilado; si no coinciden, la captura no sirve
> para juzgar nada.

---

## Next Actions

Ninguna. Estas reglas se aplican al construir cada pantalla nueva.

## Related Documents

- [La interfaz de cliente](interfaz-cliente.md) · [El arte del PokePad](prompts-arte-pokepad.md)
- [El catálogo de pantallas](interfaces-catalog.md)

---

## 8. Encoger un 0,5 % cuesta tanto como encogerlo mucho

**Síntoma:** el bisel naranja del chasis v4 «se veía de baja calidad», con
escalones sucios, mientras que el mismo Pad con el chasis v3 se veía limpio.

**No era el arte.** Medido: el v4 tiene **26 128 colores** frente a los 13 509
del v3 y los bordes *más* suaves (29,8 % de saltos duros frente al 37,1 %). Lo
que cambió fue la superficie: el acento naranja pasó de 35 460 a **97 370
píxeles**, casi el triple.

**La causa:** una ventana de **1373** de ancho — siete píxeles corta para un Pad
de 1380. Eso obliga a encoger un 0,5 %, y encoger enciende el filtrado lineal,
que mezcla cada píxel de salida con su vecino. Sobre líneas finas apenas se
nota; sobre una banda naranja enorme con esquinas achaflanadas duras, se ve como
escalones sucios.

**La regla:** cuando lo que falta cabe en el margen **transparente** del arte,
no se encoge — se dibuja a tamaño real y se deja sobresalir.

```
chasis 1380 x 828
margen transparente medido:  12 columnas a cada lado
                              4 filas arriba
-> se dibuja nativo hasta una ventana de 1356 x 820
```

Perder tres píxeles de una esquina que ya era transparente no se ve. Emborronar
el Pad entero, sí.

> Es la misma familia que la regla 2 (*una textura se dibuja al tamaño al que se
> guardó*), vista desde el otro lado: **ahí** se evita que el juego encoja una
> textura; **aquí**, que la encoja la pantalla.
