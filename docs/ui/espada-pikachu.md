# COLMILLO DE TRUENO: cómo se diseñó la espada de Pikachu

## Purpose

El proceso entero, de un `.bbmodel` de referencia a un `.bbmodel` nuevo: qué se
midió, qué se decidió y **por qué**, qué se intentó y se retiró, y qué comprueba
el generador para que nada de esto se pueda romper en silencio.

Está escrito para quien tenga que hacer **la segunda** espada.

## Dependencies

- [trajes-flujo.md](trajes-flujo.md) — el camino equivalente para un traje; de
  ahí salen el visor, el empaquetador de textura y el contrato de orientación
- [trajes-a-mano.md](trajes-a-mano.md) — el manual de Blockbench
- [dibujado.md](dibujado.md) — las reglas de dibujado (no aplica todavía: la
  espada aún no se dibuja en el juego)

## Related Documents

- `tools/espadas/` — el código, con las decisiones en sus comentarios
- `arte/espadas/referencia/pikachu-skin.bbmodel` — la copia de la referencia

## Current Status

**Exportada y comprobada** (2026-09-10). 45 cuboides, 5 grupos, textura 64×64.
`python tools/gen_espada.py --generar` la rehace entera.

**NO está en el juego.** Es arte y herramienta; nadie la ha registrado como
objeto, ni la equipa nadie, ni hay pantalla que la enseñe. Ver §9.

## Last Decision

El rayo **no se pega encima de una espada**: es el núcleo de la hoja, y la
guarda es la cara de Pikachu. Ver §3.

---

## 1. El encargo, y la restricción que lo define

El usuario mandó `pikachu skin.bbmodel` —su modelo de personaje— con un encargo
de catorce fases y un orden explícito:

> Primero ANALIZA. Después DISEÑA. Después CONSTRUYE. Después REVISA. Después
> CORRIGE. Finalmente EXPORTA. **NO entregues la primera versión sin revisarla.**

Y dos límites que mandan sobre todo lo demás:

> **NO debes modificar, sobrescribir ni alterar este archivo original.**

> **NO QUIERO UNA ESPADA GENÉRICA.**

⚠⚠⚠ **«No copies piezas, extrae REGLAS» es la frase que decide el diseño
entero.** La salida fácil era abrir la referencia, sacarle las orejas y las
mejillas y pegarlas sobre una espada de vainilla. Eso habría cumplido la letra
del encargo y ninguna de sus dos prohibiciones: es exactamente *«una espada
normal con un rayo amarillo pegado»*.

Lo que se hizo en su lugar: medir la referencia, sacarle **cuatro reglas**
(finura, densidad de textura, paleta y escala) y construir con ellas una pieza
que no comparte ni un cubo con el original.

---

## 2. FASE 1 — Lo que dice la referencia, y las dos cosas que yo dije mal

### 2.1 · Lo que resistió la medición

| Qué | Medido |
|---|---|
| Esqueleto | unidades **enteras** de Minecraft |
| `cabeza` | `from (-4,24,-4)`, tam `8×8×8` |
| `torso` | `from (-4,12,-2)`, tam `8×12×4` |
| `brazo derecho` / `izquierdo` | tam `4×12×4`, empiezan en **y=12** |
| Coronilla | **y = 32** |
| Densidad de textura | la cabeza mide 8 y ocupa 8 px → **1 texel = 1 unidad** |
| Placa más delgada del disfraz | **0,264** |
| Cubos con rotación | **cero** de 59 |

⚠⚠ **Sus cubos se llaman EN ESPAÑOL** (`cabeza`, `torso`, `brazo derecho`).
El primer sondeo buscó `head` y `body`, devolvió **cero cubos**, y no imprimió
ni una línea: parecía que la referencia no traía esqueleto. **Una comprobación
que no encuentra nada pasa sola**, y esa es la forma silenciosa de este fallo.

### 2.2 · Y dos afirmaciones mías que el fichero desmiente

Las dos estaban escritas en el docstring de `diseno.py` **como si fueran
medidas**, que es la peor forma de estar equivocado: suenan comprobadas.

> ❌ *«el disfraz va en una rejilla de U = 0,75·√2 = 1,0607 cuantizada a U/4»*

**Falso.** Contado sobre el fichero: de las **144 coordenadas** del disfraz,
**18** caen en `U/4`; de sus **72 tamaños**, 28. Ni en `U`, ni en `U/2`, ni en
`U/4`.

⚠⚠⚠ **No hay rejilla que copiar** — y eso *refuerza* la decisión en vez de
tumbarla. Si la referencia tuviera una rejilla propia habría un argumento para
heredarla; como lo que tiene son números sueltos de una importación, la espada
define la suya (**0,25**) y no arrastra el artefacto.

> ❌ *«ningún elemento tiene rotación»*

**Falso: doce rotan.** Los doce son `locator`; de los 59 **cubos**, cero. La
frase era cierta de los cubos y falsa tal y como estaba escrita.

### 2.3 · La consecuencia: `espadas/referencia.py`

De esas afirmaciones salen **la escala de la espada** y **el maniquí del visor**.
Mientras vivieran en un párrafo, nadie podía saber si seguían siendo ciertas.

Hoy `referencia.comprobar()` lee la copia del repo y cruza cada una en cada
pasada — esqueleto, coronilla a 32, mano a 12, ningún cubo girado, y la placa
más delgada. **Y si falta el fichero, la exportación se niega**: probado
escondiéndolo, sale rojo y código de salida 1.

⚠ **Se lee la COPIA del repo, no la de Descargas.** Es la lección de las seis
pantallas en magenta y la de los `.bbmodel` de los trajes: *un generador que
depende de un fichero que no está en git no se puede volver a ejecutar*. El
original del usuario no se toca en ningún momento — esto solo lee, y lee una
copia.

---

## 3. FASE 2 — El diseño: las señas entran como ARQUITECTURA

| Seña de Pikachu | Cómo entra | Grupo |
|---|---|---|
| Las **orejas** | son las alas de la guarda: salen, se estrechan y **acaban en negro** | `Guard` |
| Las **mejillas** | dos puntos rojos en la guarda, **por delante Y por detrás** | `Guard` |
| Las **rayas de la cola** | las tres bandas marrones de la empuñadura, en **relieve** | `Handle` |
| El **rayo** | **el núcleo de la hoja**, en zigzag, 18 cubos | `Energy_Core` |

⚠⚠⚠ **La guarda no lleva una cara: la guarda ES la cara.** Barra oscura +
mejillas + orejas. Por eso la espada no necesita ningún adorno pegado para que
se sepa de quién es.

⚠⚠ **Y por eso el rayo va DENTRO.** Un rayo en la silueta compite con el perfil
de la espada; un rayo en el núcleo puede zigzaguear todo lo que quiera sin
tocar el contorno. La silueta se queda limpia **y** el rayo se ve. Costó dos
intentos llegar ahí (§6).

⚠ Las mejillas van **por delante y por detrás** a propósito: la espada se ve
igual de bien desde la espalda, que es la vista que siempre se olvida. Es la
misma lección que se pagó con la Poké Ball del traje de ENTRENADOR, que no se
ve de frente.

### La escala, y por qué **26**

El personaje mide **32 hasta la coronilla** y su mano cae en **y=12**. Con la
espada empuñada por el mango:

- **26** → la punta queda justo por encima de la cabeza: épica y todavía
  creíble en una mano de 4 de ancho.
- **32** → más alta que el personaje: un *prop* de anime, no un arma.
- **20** → cuchillo.

⚠ Los tres números salen de la referencia, no de un gusto. Por eso la escala es
lo primero que `referencia.py` vigila: si el esqueleto cambiara, la espada
seguiría pasando sus propias pruebas **estando dimensionada contra un personaje
que ya no existe**.

### Los cuatro tramos

```
POMO    y 0,0 .. 2,0     3 piezas
MANGO   y 2,0 .. 7,0     4 piezas
GUARDA  y 7,0 .. 8,5    11 piezas
HOJA    y 8,5 .. 26,0    9 piezas + 18 de núcleo
```

⚠ **Se comprueba que encajen sin hueco.** Un hueco de 0,25 entre la guarda y la
hoja no da ningún error: da una espada partida que solo se ve de cerca.

---

## 4. FASE 3 — Las herramientas, y por qué son cinco ficheros

```
tools/espadas/diseno.py      QUÉ es la espada: paleta, tramos y las 45 piezas
tools/espadas/textura.py     el PNG. Reutiliza el empaquetador de los trajes
tools/espadas/visor.py       las láminas. Reutiliza las primitivas del visor de trajes
tools/espadas/bbmodel.py     el .bbmodel, sus comprobaciones y la vuelta
tools/espadas/referencia.py  lo que dice el fichero del usuario, comprobado
tools/gen_espada.py          --ver | --generar | --verificar
```

⚠⚠⚠ **EL VISOR ES LA PIEZA QUE IMPORTA, y esta lección ya estaba pagada con los
trajes.** Sin él, un modelo se escribe **a ciegas**: se genera un fichero que
dice «cubo aquí, cubo allá» y no se ve el resultado hasta abrir Blockbench.
Con él son diez intentos en dos minutos. **Las cuatro correcciones de diseño de
esta rama salieron de mirar la lámina; ninguna de leer el código.**

⚠⚠ **Y dibuja a Pikachu al lado**, que es la mitad que de verdad importa aquí.
Flotando sola en negro, *cualquier* escala parece correcta — la de un cuchillo y
la de una lanza. El encargo no era «una espada bonita»: era una espada que
**puesta junto a ESE Pikachu parezca del mismo set**.

⚠⚠ **Se reutilizan las primitivas del visor de trajes** (`_matriz`, `_quads`,
`_pintar_quad`, los dos muestreos). Ahí está escrito que la orientación de las
caras *«es un acuerdo entre dos ficheros»*: usando las suyas, ese acuerdo **no
se puede romper**.

⚠⚠ **El reparto de la textura SE CALCULA** (`trajes.modelo.empaquetar`).
Escrito a mano cuadra hasta que alguien cambia un cubo, y entonces dos cubos
comparten píxeles — *la cara de una pieza dibujada encima de otra*.

---

## 5. FASE 4 — Lo que se comprueba, y por qué cada cosa

### Las siete del diseño

| # | Comprueba | Si fallara, el síntoma sería |
|---|---|---|
| 1 | **La referencia** dice lo que `diseno.py` afirma | una espada dimensionada contra otro personaje |
| 2 | Todo en la **rejilla** de 0,25 | medidas imposibles de editar a mano en Blockbench |
| 3 | **Simetría** en X | un lado más ancho que el otro; solo se ve de frente y con cuidado |
| 4 | **Nada flotando** | una pieza suelta en el aire |
| 5 | La **textura** no se pisa ni se sale | la cara de una pieza pintada encima de otra |
| 6 | Ningún **papel muerto** en la paleta | promete una pieza que no existe |
| 7 | Los **tramos** encajan sin hueco | una espada partida |

⚠⚠⚠ **LA SIMETRÍA SE CONSTRUYE, NO SE COMPRUEBA DESPUÉS.** `_par()` crea la
pieza **y su reflejo** de una vez. La comprobación existe igualmente, pero como
red: lo que de verdad impide una espada torcida es que no haya forma de escribir
un solo lado.

⚠⚠ **Y las 18 asimétricas se DECLARAN, no se exceptúan.** El rayo del núcleo es
asimétrico a propósito —*un rayo simétrico no es un rayo*— así que la pieza lo
dice (`simetrica=False`) y el generador **imprime la lista en cada pasada**.
Apagar la comprobación habría sido lo cómodo: *una excepción que no se ve deja
de ser excepción y pasa a ser un agujero*.

⚠⚠ **`pua` y `chispa` aparecieron solos.** Sobrevivieron en `PAPEL` a las piezas
que los usaban, y no daban ningún error —nadie los pedía— pero le decían al
siguiente que la espada tiene púas y chispas. La dirección contraria ya estaba
cubierta sola: pedir un papel inexistente revienta al pintar. Hoy la número 6
cubre esta.

### La vuelta: releer el fichero escrito

⚠⚠⚠ **Se vuelve a leer el `.bbmodel` DE DISCO, no el diccionario en memoria.**
*Comparar el objeto consigo mismo pasaría siempre*, y esa lección ya está pagada
aquí dos veces —el JSON de los nichos y la conversión de los trajes—: lo que
rompe un exportador es lo que pasa **al serializar**.

La vuelta cruza: el `meta` **contra el de la referencia** (el único formato del
que consta que Blockbench abre), las 45 cajas nombre a nombre, el árbol (uuid
únicos, ningún cubo huérfano, ninguno colgando de dos grupos), las UV dentro de
la textura, y que la textura **viaje incrustada**.

⚠⚠ **Un uuid repetido no da error: da un fichero que abre y no es el que se
prometió.** Igual un cubo sin grupo — aparece suelto en el árbol y la jerarquía
que se prometió no está. Y una textura que no viaje dentro abre el modelo **en
blanco**, que se lee como «el arte está mal hecho».

⚠ **Y el resumen NOMBRA las siete.** Un «todo en verde» que no dice qué miró es
lo que deja pasar una comprobación que se quedó sin correr.

---

## 6. FASE 5 — Las cuatro pasadas, y qué enseñó cada lámina

Esto es el núcleo del documento: **ninguna de las cuatro se ve leyendo el
código.** Las tres primeras se vieron mirando; la cuarta, midiendo.

### v1 → el núcleo se comía la hoja

El canal de energía medía **1,0 sobre una hoja de 2,0**, o sea la mitad. La
espada se leía **blanca con borde amarillo**: el color de Pikachu dejaba de ser
el protagonista. Además los filos y el alma eran tonos vecinos, así que la hoja
salía **plana** — un rectángulo amarillo, no una espada.

**Arreglo:** núcleo de 1,0 → **0,5**, y el alma **más oscura** que el filo. Eso
último es lo que hace que se lea *afilada*: el bisel es lo único que separa
«espada» de «lingote».

### v2 → cuatro brazos: candelabro

Las alas de la guarda y las púas de la hoja quedaban **a la misma altura**. Dos
pares de salientes es uno de más, y la silueta dejaba de leerse como espada.

⚠⚠⚠ **La salida no fue quitar el rayo: fue SACARLO DE LA SILUETA.** Las púas se
retiraron y el rayo se metió en el núcleo, donde zigzaguea de verdad porque no
toca el contorno. **Se ganó por los dos lados**: la silueta se limpió y el rayo
se ve más.

⚠ Y de paso cayó un truco que no podía funcionar: pintarle una «chispa» al
núcleo **en la textura**. A un texel por unidad, una tira de 0,5 de ancho ocupa
**un píxel** — cualquier dibujo dentro se pierde. Queda escrito en `textura.py`
para que a nadie le tiente volver a intentarlo.

### v3 → las orejas no se leían como orejas

Eran anchas, subían poco, y su punta negra medía un pellizco. Una oreja de
Pikachu hace **tres cosas** —sale, se estrecha y acaba en negro— y el negro
tiene que ser **un tercio del largo** o no se ve a tamaño de juego.

### v4 → el pomo no cerraba la espada

⚠⚠⚠ **Y esta no se vio: se MIDIÓ.** El perfil de la vista de frente, de abajo
arriba, decía:

```
y 0,0 .. 0,5   ancho 1,5   CARBÓN     <- mismo ancho Y mismo color que el mango
y 0,5 .. 2,0   ancho 2,5   amarillo
y 0,75 .. 1,25 ancho 4,0   amarillo   (el destello cruzado)
y 2,0 .. 7,0   ancho 1,5   NEGRO      <- el mango
```

El `Pommel_Cap` medía **lo mismo que la empuñadura y era del mismo tono**, así
que lo que asomaba bajo el pomo se leía como *«el mango sigue»*: una espiga sin
rematar, no un pomo.

**Arreglo:** el cap pasa a **3,0 de ancho y del color del pomo**, o sea un
**pie**. El punto más bajo es también el más ancho y la silueta termina.

⚠ **No hizo falta oscurecerlo a mano:** `textura.py` ya pinta la cara de abajo
más oscura, así que el escalón se ve solo.

⚠⚠ **La técnica vale para la próxima:** recortar la vista de frente y listar el
ancho de la silueta fila a fila. Da el perfil en números, y **un perfil en
números delata lo que el ojo perdona** — el ojo veía «un pomo» y lo que había
eran dos piezas del mismo ancho y el mismo color, una encima de otra.

---

## 7. Lo que se descartó, y por qué

| Se intentó | Por qué NO |
|---|---|
| Copiar piezas de la referencia | es lo que el encargo prohíbe: *extrae reglas, no copies piezas* |
| Heredar su rejilla de `0,75·√2` | **no tiene rejilla** (§2.2), y ese √2 es un artefacto de importación |
| Mallas / triángulos | el encargo pide cuboides editables; una malla no se toca en Blockbench |
| Degradados suaves en la textura | delatan un render 3D, y aquí todo lo demás es Minecraft |
| «Chispa» pintada en el núcleo | a 1 texel/unidad, esa cara mide **un píxel** |
| Alternar dos tonos por tramos del núcleo | a tamaño de juego se lee como suciedad |
| Púas saliendo de la hoja | daban cuatro brazos: **candelabro** (§6, v2) |

---

## 8. Cómo se vuelve a ejecutar

```bash
python tools/gen_espada.py --ver         # solo dibuja. NO toca ningún fichero de salida
python tools/gen_espada.py --generar     # .bbmodel + textura + láminas, y verifica lo escrito
python tools/gen_espada.py --verificar   # relee el .bbmodel ya exportado
```

⚠ **`--ver` no escribe nada a propósito: mirar tiene que ser barato, o se deja
de mirar.**

Salidas:

```
arte/espadas/pikachu_electric_sword.bbmodel   45 cuboides · 5 grupos
arte/espadas/pikachu_electric_sword.png       64×64 · 466 px usados (11 %)
build/espada/espada.png                       4 vistas
build/espada/espada-junto-a-pikachu.png       la comparativa de escala
```

### El reparto de las 45 piezas

```
Energy_Core  18    el rayo, en zigzag, por delante y por detrás
Guard        11    la barra, 4 mejillas, 4 tramos de oreja y 2 puntas negras
Blade         9    alma (2), filos (4) y punta en 3 escalones
Handle        4    el puño y las 3 bandas de la cola
Pommel        3    el cuerpo, el destello cruzado y el pie
```

### La paleta

Los tonos salen de **contar los píxeles de `pikachu.png`** por frecuencia. No se
inventó ninguno.

```
hoja       #E0A226      guarda     #2C2B30      mejilla   #F24236
filo/punta #FFDE4C      ala        #E0A226      mango     #222126
núcleo     #FAFAFA      ala_punta  #222126      banda     #7F3500
pomo       #E0A226      pomo_rayo  #FFDE4C
```

---

## 9. Next Actions

**Lo que existe hoy es arte y herramienta.** Para que un jugador la vea hay que
decidir *qué es*, y ninguna de las tres opciones es gratis:

1. **Objeto de verdad** → ⚠⚠⚠ **un objeto es una entrada más en un registro que
   se sincroniza**, o sea una razón más para echar a quien no se actualice
   (*«un registro que se sincroniza no degrada, ECHA»*). Es exactamente el
   motivo por el que los trajes **no** son objetos.
2. **Cosmético de Cobblemon** → sin registro nuevo, pero lo lleva un Pokémon, no
   el jugador.
3. **Pieza decorativa** → colocada en el mundo, sin registro y sin dueño.

⚠ Y si algún día se equipa, **antes hay que leer
[dibujado.md](dibujado.md)** y el §5 de [trajes-flujo.md](trajes-flujo.md): la
conversión de Bedrock a Java es donde se rompe todo, y ahí ya está pagada la
lección de que **la Y se voltea y la Z no**.
