# Trajes de rango — cómo se hacen a mano

## Purpose

Cómo dibujar un traje de rango en Blockbench y meterlo en el servidor. El
sistema (pantalla KITS, guardado, sincronización y dibujado) **ya está hecho**;
lo único que falta de cada traje es su arte.

## Dependencies

- `mod/src/client/java/net/pokereport/luna/client/Trajes.java` — lo dibuja
- `mod/src/main/java/net/pokereport/luna/traje/Traje.java` — la escalera de rangos

## Current Status

Los cinco trajes están declarados y **ninguno tiene arte**. El arte generado por
script se retiró el 2026-08-28 por decisión del usuario.

---

## 1. Por qué titilaba, y la regla que sale de ahí

> ⚠⚠⚠ **EL CUERPO DEL JUGADOR TIENE DOS CAPAS, NO UNA.**
>
> Está el cuerpo, y encima **la capa exterior de la skin** — la chaqueta, la
> manga, el pelo. Esa capa va **inflada exactamente 0,25**.
>
> Mi manga medía 4,5 sobre un brazo de 4,0: **0,25 por lado**. Es decir,
> **exactamente encima de la capa exterior de la skin**. Dos superficies en el
> mismo plano, y la tarjeta gráfica no puede decidir cuál va delante: por eso
> parpadeaba entre el traje y la skin.

**La regla, y es la más importante de todo este documento:**

| Parte del cuerpo | Tamaño real | Tu cubo tiene que medir **al menos** |
|---|---|---|
| Cabeza | 8 × 8 × 8 | **8,8** (inflado 0,4) |
| Torso | 8 × 12 × 4 | **8,8 × 12,8 × 4,8** |
| Brazo | 4 × 12 × 4 | **4,8 × 12,8 × 4,8** |
| Pierna | 4 × 12 × 4 | **4,8 × 12,8 × 4,8** |

En Blockbench eso es el campo **Inflate = 0.4** (o más). Con 0,25 titila. Con
0,3 titila a veces, según la distancia. **Con 0,4 no titila nunca.**

> ⚠ Y hay un caso en que **sí** quieres pegarte al cuerpo: cuando la parte va
> **al aire**, como los antebrazos del ENTRENADOR. Ahí sencillamente **no pongas
> cubo**, y se ve la piel del jugador. No lo tapes con algo transparente.

---

## 2. El programa: Blockbench

**Es gratis, es de código abierto (GPL-3.0), y lo que hagas con él es tuyo,
uso comercial incluido.** Lo dice su propia licencia.

- **Descarga:** https://blockbench.net/ (hay versión de escritorio y también
  funciona en el navegador sin instalar nada)
- **El plugin que hace falta:** en Blockbench, `File → Plugins`, busca
  **«GeckoLib Animation Utils»** y pulsa Install.

> ⚠ El plugin no es opcional aunque nosotros no usemos GeckoLib para dibujar:
> es lo que añade la plantilla **Armor**, que crea los huesos ya colocados y con
> los nombres correctos. Sin ella hay que escribirlos a mano y **un nombre mal
> escrito no da ningún error: la pieza sencillamente no aparece.**

---

## 3. Los pasos, uno a uno

### 3.1 Crear el proyecto

1. `File → New → GeckoLib Animated Model`
2. En el diálogo, elige **Armor** como tipo.
3. Pon la textura en **128 × 128**.

Blockbench te crea el esqueleto con estos huesos, ya en su sitio:

```
bipedHead        ← ancla, va VACÍA
  armorHead      ← aquí van los cubos del casco
bipedBody
  armorBody      ← el peto
bipedRightArm
  armorRightArm  ← la manga derecha
bipedLeftArm
  armorLeftArm
bipedRightLeg
  armorRightLeg  ← la pernera
bipedLeftLeg
  armorLeftLeg
```

> ⚠⚠ **Los cubos van SIEMPRE dentro de un hueso `armor*`, nunca dentro de un
> `biped*`.** Los `biped*` son anclas vacías: es lo que el juego pega al cuerpo
> del jugador. Un cubo suelto en un `biped*` sale descolocado.

### 3.2 Dibujar

- Añade cubos dentro del hueso que toque.
- **Inflate 0,4 como mínimo** (§1).
- Puedes añadir huesos hijos dentro de un `armor*` para las cosas que
  sobresalen: orejas, cuernos, la visera de una gorra, una coleta.

> ⚠ **Nada más fino que medio bloque.** Cadenas, correas y cordones de 0,1 se
> ven como una línea de puntos que parpadea, no como un cordón.

### 3.3 La textura

`Texture → Create Texture`, 128 × 128, y píntala en Blockbench o expórtala para
pintarla fuera. Blockbench reparte las caras solo (**UV → Auto UV**).

### 3.4 Exportar: **cuatro ficheros, uno por pieza**

Aquí está la trampa que ya nos tumbó el cliente una vez:

> ⚠⚠⚠ **CADA PIEZA ES UN FICHERO, Y TIENE QUE SOSTENERSE SOLA.**
>
> Casco, peto, perneras y botas se ponen por separado, así que se exportan por
> separado. **Un hueso no puede colgar de otro que viva en otro fichero.**
>
> A mí me pasó exactamente eso: puse la bota colgando de la pernera —que es lo
> natural, una bota va en una pierna— y dentro del fichero de las botas ese
> padre no existe. **No dio un aviso: tumbó la carga de recursos entera y el
> juego se quedó colgado en la pantalla de carga.**

Así que se hacen **cuatro proyectos** (o uno y se borra lo que no toca antes de
cada exportación), y en cada uno solo van sus huesos:

| Fichero | Lleva |
|---|---|
| `<traje>_head.geo.json` | `bipedHead` + `armorHead` |
| `<traje>_body.geo.json` | `bipedBody` + `armorBody` + los dos brazos |
| `<traje>_legs.geo.json` | las dos `bipedLeg` + las dos `armorLeg` |
| `<traje>_boots.geo.json` | las dos `bipedLeg` + `armorRightBoot`/`armorLeftBoot` |

Exportar: `File → Export → Export Bedrock Geometry`.

> ⚠ Las botas van dentro de `bipedRightLeg`/`bipedLeftLeg` **directamente**, no
> dentro de `armorRightLeg`. Ver el aviso de arriba.

---

## 4. Qué me mandas

Por cada traje, **ocho ficheros** en una carpeta con el nombre del rango:

```
entrenador/
  novato_head.geo.json      novato_head.png
  novato_body.geo.json      novato_body.png
  novato_legs.geo.json      novato_legs.png
  novato_boots.geo.json     novato_boots.png
```

Los nombres de rango son exactamente: `entrenador`, `elite`, `campeon`, `maestro`,
`leyenda`.

**No hace falta que estén los cuatro.** Un traje que solo sea una gorra se manda
con `_head` y ya: lo que falte sencillamente no se dibuja.

> ⚠ Opcional: si algo tiene que **brillar de noche**, mándame también
> `<traje>_<pieza>_glowmask.png` — la misma imagen con **todo en negro menos lo
> que brilla**.

---

## 5. Qué hago yo con eso

1. Los copio a `mod/src/client/resources/assets/lunaeternal/trajes/<rango>/` y
   `.../textures/armor/<rango>/`.
2. Cambio el `false` por `true` de ese rango en `Traje.java`. **Una línea.**
3. Compilo, despliego y publico el manifiesto.

Y eso es todo. **Todo lo demás ya está hecho y probado:** la pantalla KITS con
sus tres pestañas, el previsualizador 3D, el candado por rango, el guardado en
la base de datos, y que los demás jugadores te vean con el traje puesto.

---

## 6. Lo que el sistema ya garantiza

| | |
|---|---|
| Un traje sin arte **no se puede poner** | Aunque seas LEYENDA. Lo vigila el autotest |
| Bajar de rango **retira el traje solo** | El permiso se deriva del rango, no se copia |
| El traje **lo ven los demás** | Se reparte al cambiarlo y al entrar |
| **No hay objeto** | No ocupa ranura, no se cae al morir, no se regala |
| **No protege** | Se vende identidad, no poder (D-007, D-014) |

## Last Decision

2026-08-28 — El arte de los trajes **se hace a mano**. Se retiró el generado por
script: cumplía, pero el usuario lo quiere dibujado. El generador
(`tools/gen_trajes.py`) se conserva como banco de pruebas y escribe a `build/`,
nunca al mod.

## Next Actions

Dibujar ENTRENADOR en Blockbench y mandar los ocho ficheros.
