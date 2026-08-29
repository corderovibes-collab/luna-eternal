# Estatuas y props de la ciudadela con Meshy

## Purpose

Cómo convertir una idea en una **estatua construida con bloques** dentro de la
ciudadela, usando IA de 3D. Resuelve lo que más cuesta a mano: las formas
orgánicas y grandes.

## Dependencies

- [trajes-flujo.md](../ui/trajes-flujo.md) §1 — por qué esto vale para estatuas y
  **no** para armaduras
- Litematica y WorldEdit, ya instalados en el perfil Constructor

## Current Status

Sin probar todavía. La primera prueba debería ser el **punto de Monumentos**
(`-63.9 68 142`), que es una parada del moto taxi y está vacío.

---

## 1. Por qué aquí sí y en los trajes no

Es la misma cuenta, con distinto resultado:

| | Traje | Estatua |
|---|---|---|
| Se dibuja | en **cada jugador**, en cada fotograma | **una vez**, en un sitio |
| Con 20 personas delante | ×20 | ×1 |
| 672 cajas cuestan | 54.000 por fotograma | nada |

Y hay una segunda diferencia que importa igual: una estatua sale como **bloques
del mundo**, no como un modelo. O sea que **se puede editar con WorldEdit**
después, como cualquier construcción.

---

## 2. Antes de tocar Meshy: los créditos

**Cada generación son 20 créditos.** Con 100 hay **cinco intentos**, y texturizar
o refinar puede costar aparte.

> ⚠⚠ **LA IMAGEN DE PARTIDA SE PREPARA FUERA, QUE ES GRATIS.** Meshy tiene un
> «Generar imagen» dentro, pero cada vuelta ahí es dinero. Consigue la imagen
> buena con Gemini —que ya usaste para el traje y para las tarjetas de mundos— y
> entra a Meshy **solo cuando la imagen ya te guste**.

**Lo que hace buena a una imagen de partida para 3D** (distinto de los bocetos de
traje, ojo):

- **Un solo objeto, centrado, sobre fondo liso.** Nada de escenas.
- **Tres cuartos**, no de frente plano: la IA necesita ver volumen.
- **Iluminación uniforme**, sin sombras duras ni contraluz.
- **Que se vea entero**, sin recortes.

---

## 3. La licencia — leerlo antes de generar

En la pantalla, abajo, hay dos botones: **`CC BY 4.0`** y **`Privado`** (este con
corona, o sea de pago).

> ⚠⚠⚠ **CON `CC BY 4.0` EL MODELO ES PÚBLICO Y HAY QUE DAR CRÉDITO.**
>
> CC BY **sí permite uso comercial** —así que no choca con la venta de paquetes
> (D-007)— pero **obliga a atribuir**. Para una estatua dentro del servidor eso
> se cumple con un cartel o una línea en la web; no es un problema, pero **es una
> obligación real** y este proyecto ya descartó cosas por licencia (D-006).
>
> Si algún día una estatua va a ser parte de la identidad del servidor —el
> monumento central, algo que salga en el logo— ahí sí conviene el modo
> **Privado**.

---

## 4. Los ajustes de la pantalla

De lo que se ve, esto es lo que importa para nuestro caso:

| Ajuste | Qué poner | Por qué |
|---|---|---|
| **Alto detalle** | déjalo | Lo vamos a voxelizar igual, pero de un modelo sucio salen bultos |
| **Meshy 7 - Flagship** | déjalo | Es el bueno |
| **Mejora de imagen** | **encendido** | Ya viene así. Limpia la imagen de partida, que es de donde sale todo |
| **Visión múltiple** | no hace falta | De pago, y con una imagen buena en 3/4 basta |
| **Modo Ultra / Dividir** | no | De pago, y el detalle extra se pierde al convertir a bloques |
| **Pose (A-Pose / T-Pose)** | **solo si es un personaje** | Una estatua de un entrenador en A-Pose queda de pie y recta |

> ⚠ **El detalle fino no sobrevive.** Sea cual sea el modelo, al pasarlo a bloques
> de un metro se pierde todo lo pequeño. No pagues por detalle que se va a tirar.

---

## 5. Exportar

Cuando el modelo te guste, **descárgalo en `OBJ`** (con su `.mtl` y la textura si
te los da). `GLB` y `FBX` también valen, pero OBJ es el que mejor tragan los
conversores.

---

## 6. De malla a bloques

Con [ObjToSchematic](https://github.com/LucasDower/ObjToSchematic):

1. Cargas el `.obj`.
2. **Le dices cuántos bloques quiere medir de alto.** Este es el ajuste que
   manda: es lo que decide si la estatua tiene 10 bloques o 40.
3. Eliges la paleta de bloques.
4. Exportas a **`.litematic`**.

**Qué altura pedir**, según lo que hayamos visto de la ciudadela:

| Alto | Qué se reconoce |
|---|---|
| 8-12 bloques | La silueta y poco más. Vale para algo lejano |
| **16-24** | **La banda buena.** Se leen la cara y los detalles grandes |
| 32+ | Precioso, y ya es una obra: ocupa media plaza y tarda en pegarse |

> ⚠ **Empieza por 16.** Es rápido de pegar y, si no convence, has perdido cinco
> minutos y ningún crédito. Subir de tamaño después es reexportar, no regenerar.

---

## 7. Pegarlo en el mundo

Con **Litematica**, que ya está instalada en el perfil Constructor:

1. Pon el `.litematic` en `minecraft/schematics/`.
2. En el juego: `M` → cargar el esquema → colocar el fantasma donde va.
3. `Litematica → Easy Place` para construirlo, o **WorldEdit** para llenarlo de
   golpe.

> ⚠ Ponlo **primero en una zona vacía**, no en su sitio definitivo. Una estatua
> de 24 bloques ocupa mucho más de lo que parece en el previsualizador, y
> quitarla después es más trabajo que ponerla.

---

## 8. Qué hago yo con esto

- Si el conversor deja huecos o te cambia bloques que no quieres, **puedo
  reescribir el `.litematic`** — es un NBT y ya generamos esquemas.
- Puedo **cambiar la paleta a nuestros bloques**: `lunaneon` tiene 16 colores de
  hormigón en 5 formas y 16 de neón. Una estatua construida con nuestros propios
  bloques pega con la ciudadela por construcción.
- Y puedo **medir cuántos bloques ocupa** antes de que la pegues, para saber si
  cabe donde quieres.

## Last Decision

2026-08-28 — Meshy entra en el proyecto **para la ciudadela**, no para los
trajes. La cuenta que lo decide está en
[trajes-flujo.md §1](../ui/trajes-flujo.md).

## Next Actions

Probar el camino entero con **una** estatua en el punto de Monumentos
(`-63.9 68 142`), a 16 bloques de alto, antes de gastar más créditos.
