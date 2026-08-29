# El camino de un traje: de la idea al jugador

## Purpose

Cómo va un traje de rango desde una idea hasta que un jugador lo lleva puesto en
el servidor. Qué herramienta hace cada paso, cuál **no** sirve, y qué hago yo.

## Dependencies

- [trajes-a-mano.md](trajes-a-mano.md) — el manual de Blockbench, paso a paso
- [prompts-trajes.md](prompts-trajes.md) — los prompts de los bocetos

## Current Status

El sistema del juego está terminado y probado. Lo único que falta de cada traje
es **su arte**.

---

## 1. Lo que NO funciona, y por qué

Antes del camino bueno, los dos atajos que parecen buenos y no lo son. Los dos
se rompen por la misma razón.

### ⚠⚠⚠ Meshy (o cualquier IA de 3D) → Blockbench → armadura: **no llega**

Meshy genera **mallas de triángulos**: miles de caras, orgánicas, con la textura
horneada encima. Es la tecnología correcta para un videojuego normal.

**Minecraft no dibuja mallas.** Ni la armadura de vainilla, ni GeckoLib, ni el
formato Bedrock. Todo el arte de personajes de Minecraft son **cajas alineadas a
los ejes** — `desde (x,y,z)` `hasta (x,y,z)`, y se acabó. No hay triángulos, no
hay vértices sueltos, no hay curvas.

Así que un modelo de Meshy en Blockbench **no se puede convertir en armadura**.
Habría que **volver a construirlo entero a base de cubos**, mirándolo — que es
exactamente el trabajo que se quería evitar.

> ⚠ Sí existen conversores de malla a vóxel. El resultado son **miles de cubos
> diminutos**: imposible de texturizar, imposible de animar, y hunde el
> rendimiento con veinte jugadores en pantalla. No es una opción.

**Dónde Meshy sí vale:** para **ver la idea en tres dimensiones antes de
construirla**. Girar el diseño y decidir si funciona por detrás. Eso es útil, y
es exactamente lo mismo que aporta un boceto de tres vistas — solo que girando.

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
  4. AL JUEGO         yo
                      copio ocho ficheros y cambio un `false` por `true`
```

**Cuatro pasos, y solo el 2 es trabajo de verdad.** Todo lo demás es rápido.

### Paso 1 — La idea

Un boceto **plano y de frente**, no un render bonito. Los prompts están en
[prompts-trajes.md](prompts-trajes.md), con el bloque técnico que hay que pegar
siempre.

> ⚠ **Las tres vistas, siempre.** En el NOVATO, la Poké Ball iba en la espalda y
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

Me mandas la carpeta. Yo copio los ficheros, cambio **una línea**, compilo,
despliego y publico. Diez minutos.

---

## 3. Las tres reglas que no se pueden saltar

Las tres las hemos pagado ya. Están en detalle en
[trajes-a-mano.md](trajes-a-mano.md); aquí en corto:

| | |
|---|---|
| **Inflate 0,4 mínimo** | La skin del jugador tiene una capa exterior a **0,25 exactos**. Ponerse ahí encima hace que parpadee entre el traje y la piel |
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

## Last Decision

2026-08-28 — Meshy y MCreator **quedan fuera del camino**: el primero produce
mallas y Minecraft solo dibuja cajas; el segundo fabrica un mod que ya tenemos.
El arte se construye en Blockbench, y el reparto lo hace `lunaeternal`.

## Next Actions

Elegir A, B o C del paso 2 para el NOVATO.
