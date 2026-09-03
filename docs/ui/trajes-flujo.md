# El camino de un traje: de la idea al jugador

## Purpose

Cómo va un traje de rango desde una idea hasta que un jugador lo lleva puesto en
el servidor. Qué herramienta hace cada paso, cuál **no** sirve, y qué hago yo.

## Dependencies

- [trajes-a-mano.md](trajes-a-mano.md) — el manual de Blockbench, paso a paso
- [prompts-trajes.md](prompts-trajes.md) — los prompts de los bocetos

## Current Status

**CAMPEÓN y LEYENDA están hechos** (2026-09-03): vienen de tres `.bbmodel` del
usuario y entran solos con `python tools/gen_trajes.py --generar`.

Faltan **ENTRENADOR, ELITE y MAESTRO**, y lo que les falta es *su arte*: el
sistema del juego lleva terminado y probado desde el 2026-08-28.

---

## 1. Lo que NO funciona, y por qué

Antes del camino bueno, los dos atajos. **El primero funciona pero no para
esto**, y el segundo es un malentendido.

### Meshy → cubos: **sí se convierte.** Lo que no sirve es para *armadura*

> ⚠ **Corrección.** La primera versión de este documento decía que no se podía
> convertir. **Es falso y hay que decirlo claro.** El usuario insistió en que
> buscara, y buscando aparecen varias herramientas que lo hacen:
>
> - [Convert-Models-Into-Blockbench-Cubes](https://github.com/PublicDark/Convert-Models-Into-Blockbench-Cubes)
>   — OBJ / STL / PLY → cubos de Blockbench
> - [OBJ2MC Addon Studio](https://techno-rope.com/) — en web, con fusión de cajas
> - [ObjToSchematic](https://github.com/LucasDower/ObjToSchematic) — OBJ →
>   `.schematic` / `.litematic`, o sea **bloques del mundo**
> - [objmc / obj-cubed](https://github.com/JagerMeistars/obj-cubed) — mete la
>   malla **de verdad** en Java horneando los vértices en una textura

Así que la pregunta buena no es *«¿se puede convertir?»* sino **«¿cuántas cajas
salen, y dónde se pueden pagar?»**. Eso sí se puede medir, y se midió:

**Un peto orgánico, voxelizado con el detalle que trae una malla:**

| Detalle | Vóxeles | Cajas tras fusionarlas |
|---|---|---|
| 1 vóxel por bloque *(el detalle de vainilla)* | 81 | **38** |
| 2 vóxeles por bloque | 713 | **144** |
| 4 vóxeles por bloque *(lo típico de una malla)* | 5.909 | **672** |

> Un peto de vainilla son **3 cajas**. El traje de ENTRENADOR entero, hecho a mano,
> son **15**.

⚠⚠⚠ **Y una armadura se dibuja EN CADA JUGADOR, EN CADA FOTOGRAMA.** 672 cajas
por pieza × cuatro piezas ≈ **2.700 por jugador**. Con veinte personas a la vista
son **54.000 cajas por fotograma solo en ropa**. Ahí es donde se paga.

**Y hay tres problemas más, aparte del número:**

- Los conversores dan **un color por cubo**, no una textura con UV. O sea que
  **no se puede retocar después**: si el rojo no te gusta, hay que reconvertir.
- No reparten el modelo en **las cuatro piezas** con los nombres de hueso que
  hacen falta (`armorHead`, `armorBody`…). Eso hay que rehacerlo igual.
- `objmc` sí mete mallas de verdad en Java, pero **a costa de core shaders**, y
  nuestro pack lleva Iris con Complementary. Se pelean.

### ⚠⚠ DONDE SÍ VALE, Y ES UNA BUENA IDEA: la ciudadela

**Todo lo anterior se cae si el modelo no se lleva puesto.** Una estatua, un
monumento, una fuente, un Pokémon gigante en una plaza: **se dibuja una vez, en
un sitio**, no veinte veces por fotograma.

Ahí Meshy encaja perfectamente, y el camino ya está montado:

```
  Meshy  →  .obj  →  ObjToSchematic  →  .litematic
                                            ↓
                              Litematica o WorldEdit, que YA están instalados
```

> ⚠ Y de paso resuelve lo que más cuesta de construir a mano: **las formas
> orgánicas y grandes**. Una escalera de hormigón se pone en un minuto con
> WorldEdit; un Charizard de veinte metros, no.

### ⚠⚠ MCreator «para pegarlo al juego de los jugadores»: **no es eso**

Aquí hay un malentendido que conviene deshacer, porque cambia todo el plan.

MCreator **no reparte nada a nadie**. Es un programa para *fabricar un mod* sin
escribir código: sale un `.jar`, y ese jar hay que repartirlo igual que todos los
demás.

**Y nosotros ya tenemos el mod.** `lunaeternal` está en el servidor y en el
cliente de todos los jugadores. La pantalla de KITS, el guardado, el candado por
rango y el dibujado del traje **ya están escritos y probados**. Lo único que le
falta a cada traje son sus ficheros de arte.

Meter MCreator en medio significaría **un segundo jar** que sincronizar en los
dos lados: otra descarga, otra versión que cuadrar, y **otra forma de dejar gente
fuera del servidor**. Ya nos pasó con la mochila: seis minutos sin que nadie
pudiera entrar.

> **MCreator sobra en este camino.** No porque sea malo — es buena herramienta —
> sino porque resuelve un problema que ya está resuelto.

---

## 2. El camino que sí funciona

```
  1. LA IDEA          tú, o una IA de imagen
        ↓             una hoja de personaje: frente, lado, espalda, plana
  2. LOS CUBOS        Blockbench
        ↓             cajas dentro de los huesos armor*
  3. LA TEXTURA       Blockbench, o la genero yo del modelo
        ↓             un PNG de 128x128
  4. AL JUEGO         python tools/gen_trajes.py --generar
                      lee el .bbmodel y escribe las cuatro piezas
```

**Cuatro pasos, y solo el 2 es trabajo de verdad.** Todo lo demás es rápido.

> ⚠⚠ **El paso 4 ya no se hace a mano.** Ponía «copio ocho ficheros y cambio un
> `false` por `true`», y copiar ocho ficheros a mano es donde se cuela el fallo
> que nadie ve: basta con equivocarse en el nombre de la carpeta para que el
> traje se equipe, se sincronice, **no dé ningún error y no se vea nada**. Hoy
> lo hace el importador, y hay dos comprobaciones que no dejan que pase — ver
> §5.

### Paso 1 — La idea

Un boceto **plano y de frente**, no un render bonito. Los prompts están en
[prompts-trajes.md](prompts-trajes.md), con el bloque técnico que hay que pegar
siempre.

> ⚠ **Las tres vistas, siempre.** En el ENTRENADOR, la Poké Ball iba en la espalda y
> de frente no se ve nada: sin la vista de espalda no se habría puesto nunca.

### Paso 2 — Los cubos

Aquí es donde está el trabajo, y **hay tres formas de hacerlo**. Elige una:

| | Quién dibuja | Cómo queda | Cuándo elegirla |
|---|---|---|---|
| **A** | Tú, en Blockbench | Como tú quieras | Tienes tiempo y ganas de aprender la herramienta |
| **B** | Yo, del boceto | Limpio y geométrico | Quieres los cinco esta semana |
| **C** | **Yo la base, tú encima** | Como tú quieras, sin pelearte con lo difícil | **Recomendada** |

**La C es la que recomiendo**, y no es un término medio blando: reparte el
trabajo por dónde duele.

Yo pongo **lo que se rompe en silencio** — los nombres de los huesos, los
pivotes, el inflado que evita el parpadeo, las cuatro piezas que tienen que
sostenerse solas, el reparto de la textura. Todo eso ya nos ha costado un cliente
colgado y un traje que titilaba.

Tú abres el `.bbmodel` y **mueves, añades y pintas** — que es la parte que se
juzga con el ojo y en la que yo no te puedo sustituir. Y empiezas desde algo que
**ya encaja en el cuerpo**, no desde una rejilla vacía.

### Paso 3 — La textura

En Blockbench (`UV → Auto UV` y a pintar), o me mandas el modelo y **te la genero
yo del propio modelo**, que es como salen los 602 bloques del servidor.

### Paso 4 — Al juego

Me mandas el `.bbmodel`. Va a `arte/trajes/`, se le da un reparto en
`tools/trajes/importados.py` (qué grupo es la cabeza, cuál el torso…) y:

```bash
python tools/gen_trajes.py --generar
```

Eso escribe las cuatro piezas dentro del mod, dibuja la lámina para mirarla y
pasa las comprobaciones. Luego compilar, desplegar y publicar el manifiesto.

> ⚠⚠ **Los `.bbmodel` van al repo, no se quedan en Descargas.** La primera
> versión los leía de `~/Downloads` y eso es la lección de las seis pantallas en
> magenta: un generador que depende de un fichero que no está en git **no se
> puede volver a ejecutar**, y nadie se entera hasta que hace falta.

---

## 3. Las tres reglas que no se pueden saltar

Las tres las hemos pagado ya. Están en detalle en
[trajes-a-mano.md](trajes-a-mano.md); aquí en corto:

| | |
|---|---|
| **Inflate por encima de 0,25** | La skin del jugador tiene una capa exterior a **0,25 exactos**. Ponerse ahí encima hace que parpadee entre el traje y la piel. ⚠ Y si el `.bbmodel` ya lo trae puesto, **manda el suyo**: el de Arceus usa 0,75 en torso, brazos y botas y 0,5 en cintura y perneras, que son **dos capas** separadas 0,25. Pisarlas con un número propio hace que parpadeen entre ellas |
| **Cada pieza se sostiene sola** | Casco, peto, perneras y botas son ficheros distintos. Un hueso que cuelgue de otro fichero **tumba la carga de recursos entera** |
| **Nada más fino que medio bloque** | Un cordón de 0,1 no se ve como un cordón: se ve como una línea que parpadea |

---

## 4. Lo que ya está hecho y no hay que volver a tocar

Para que quede claro cuánto camino queda: **esto ya funciona.**

| | |
|---|---|
| La pantalla KITS | Tres pestañas, saldo con «+», previsualizador 3D sobre tu propio personaje |
| El guardado | Tabla `player_suit`, migración V023 aplicada |
| El candado | Se deriva del rango; bajar de rango retira el traje solo |
| Que te vean | Se reparte a todos al cambiarlo, y a quien entra se le pone al día |
| Sin objeto | No ocupa ranura, no se cae al morir, no se regala, no añade nada a ningún registro |
| Sin protección | Se vende identidad, no poder (D-007, D-014) |
| 10 comprobaciones | La que importa: un traje sin arte no se puede poner ni siendo LEYENDA |
| El importador | Lee `.bbmodel` con cubos girados, UV por cara y varias texturas (§5) |

---

## 5. El importador, y las cuatro cosas que hay que acertar

`tools/trajes/importar.py` traduce un `.bbmodel` a nuestro formato. Las cuatro
conversiones están escritas en su cabecera; lo que importa recordar es que
**ninguna de las cuatro da error si se falla**:

| | Si se falla | Cómo se ve |
|---|---|---|
| **Los ejes** | `java = (x, 24 - y - alto, z)` — X y Z **no se tocan** | La corona sale detrás de la cabeza |
| **Los giros** | El paso a Java es un *reflejo*: `pitch = -rx`, `yaw = +ry`, `roll = -rz` | La cresta se dobla hacia dentro |
| **Las caras** | east · north · west · south · up · down, en ese orden | El detalle cae en el lado contrario |
| **La textura** | Se **rehornea**: cada cara se recorta y se pega en su casilla | Media pieza en blanco |

> ⚠⚠⚠ **La Z estaba volteada en `Trajes.java` desde que se escribió**, y no
> podía verse: con cubos simétricos en Z —que era todo lo que había mientras
> ningún traje estuvo `listo`— las dos fórmulas dan **el mismo número**. Con la
> corona del CAMPEÓN, cuyas puntas van delante de la cara, la pieza aparecería
> detrás de la cabeza.

> ⚠⚠⚠ **Un cubo girado es un hueso hijo, no un cubo más.** `ModelPart` gira
> *partes*, nunca cubos sueltos: un giro escrito dentro del cubo se ignora y la
> pieza sale **recta**. Es lo mismo que hace Blockbench al exportar (los
> `bone_r1`). La corona tiene 2 cubos girados y el casco de Arceus **13**.

> ⚠⚠ **Rehornear la textura es lo que hace que los dos modelos entren por la
> misma puerta.** La corona pinta sus 44 cubos con UV **por cara** y el cuerpo
> con UV **de caja**; `ModelPart` solo sabe leer cajas. Recortando cara a cara,
> los dos casos se convierten en uno — y de propina desaparece la bandera
> `mirror`, porque un cubo espejado ya trae sus caras intercambiadas.

### Las dos comprobaciones que lo sostienen

Corren en `--verificar` y en `--generar`:

| | |
|---|---|
| `Traje.java` ↔ el arte | Que el `id` del enum y la carpeta del arte sean **el mismo nombre**, y que un traje `listo` tenga sus cuatro piezas. ⚠⚠⚠ La primera versión registró el traje de Arceus como **«arceus»** en vez de «leyenda»: se habría equipado, sincronizado, **sin un solo error, y sin verse nada** — el fallo de los 62 cosméticos que no existían |
| `modelo.py` ↔ `Trajes.java` | Rehace en Python lo que hace Java, **leyendo el `.geo.json` de disco**, y comprueba que cada cubo acaba donde su autor lo puso. Es el único sitio donde se ve un signo cambiado. Probada contra el fallo real de la Z: lo caza en los dos trajes |

> ⚠⚠ **Ida y vuelta por el fichero, no por la memoria.** Comparar el objeto
> consigo mismo pasaría siempre; lo que se quiere saber es si lo *escrito* se
> lee bien.

> ⚠ Y la comprobación falló primero **por su propia aritmética**: comparaba las
> esquinas *ordenadas*, y dos que caen casi en el mismo sitio se ordenan al
> revés por una millonésima de redondeo. Decía «se ha movido 4,6 unidades»
> cuando no se había movido nada. Hoy compara los dos conjuntos de esquinas.

---

## Last Decision

2026-08-28 — El arte de los trajes se construye **en Blockbench, a base de
cubos**, y el reparto lo hace `lunaeternal`.

**Meshy queda fuera de los TRAJES pero entra en la CIUDADELA.** Convertir una
malla a cubos sí se puede —hay varias herramientas— y lo que no se puede pagar es
llevarla puesta: 672 cajas por pieza contra las 3 de vainilla, dibujadas en cada
jugador y en cada fotograma. Para una estatua, que se dibuja una vez, esa cuenta
no existe.

**MCreator queda fuera del todo**: fabrica un mod que ya tenemos.

## Next Actions

**Verificarlos en el juego.** El importador comprueba la geometría y la lámina
enseña las cuatro vistas, pero ninguna de las dos cosas es un jugador con el
traje puesto. Lo que hay que mirar:

- que la corona no atraviese la cabeza al mirar arriba y abajo;
- que la rueda de Arceus no se meta dentro del cuerpo al agacharse;
- que la cresta del casco no tape la vista en primera persona;
- y que nada parpadee contra la piel.

Después: elegir A, B o C del paso 2 para ENTRENADOR, ELITE y MAESTRO.

Y aparte: probar el camino de Meshy → ObjToSchematic → Litematica con **una
estatua de la ciudadela**, que es donde sí sale a cuenta.
