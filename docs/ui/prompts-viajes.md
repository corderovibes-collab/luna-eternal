# Los siete destinos de Viajes — el arte y sus prompts

## Purpose

De dónde salen las siete ilustraciones de `ViajesScreen`, y con qué reglas se
pidieron. Sirve para regenerar una si hace falta y para pedir la octava el día
que exista.

## Dependencies

- `docs/ui/dibujado.md` — las seis reglas de dibujado
- `CLAUDE.md` §Viajes

## Last Decision

2026-08-31. Ilustraciones propias que llenan la ficha, en vez de un icono
sobre un cuadro de color.

---

## 1. Por dónde pasó esto, que es la mitad de la lección

| | |
|---|---|
| 1.º | **Formas dibujadas a mano por código** — una espada de tres rectángulos a 58 px no parece una espada: parece tres rectángulos |
| 2.º | **Objetos del juego** (espada de netherita, esmeralda, Poké Ball curativa). Se veían bien y traían su arte hecho, pero eran de **Minecraft**, no del servidor |
| 3.º | **Ilustraciones propias** — las de hoy |

⚠ El paso 2 no fue un error: fue lo correcto **mientras no hubo arte**. Un icono
que se lee mal es peor que uno prestado.

## 2. Las tres decisiones que están dentro de los prompts

### 2.1 · Todas de noche, con la misma luna

La ciudadela tiene **noche permanente** (`fixed_time 18000`) y el servidor se
llama **Luna Eternal**. Siete escenas nocturnas con la misma luna las convierte
en **una serie**; sin eso serían siete dibujos sueltos que casualmente están
juntos.

### 2.2 · Cada una lleva el color que ya tenía en el código

| parada | color | |
|---|---|---|
| `torre_batalla` | `#8C3A2E` | rojo terroso |
| `laboratorio` | `#2E6E8C` | azul acero |
| `palacio` | `#7A5C1E` | oro oscuro |
| `monumentos` | `#5A5A6E` | gris violáceo |
| `torre_comercial` | `#2E7A4E` | verde esmeralda |
| `centro_curacion` | `#9E3A5C` | rosa vino |
| `montana` | `#4A6E8C` | azul montaña |

⚠⚠ **No se eligieron para el arte: ya estaban.** `CLAUDE.md` dice que el color
por parada es deliberado —«a la tercera visita vas al cuadro naranja sin
leer»— así que el arte se pidió **con ese tono como dominante**. La serie pega
por construcción, igual que los 16 colores del neón y del hormigón (D-032), y
no porque alguien la emparejara a ojo.

### 2.3 · «Se va a ver a 186 píxeles»

Esa frase está en los siete prompts, y es la que decide la composición: **un
motivo grande y centrado, silueta clara, y el fondo más apagado que él**. Una
ilustración cargada a ese tamaño es una mancha.

## 3. La plantilla técnica

```
- Formato CUADRADO 1:1, 1024x1024 px.
- Fondo COMPLETO, opaco, sin transparencia.
- Ilustración de sombreado plano (cel shading), estilo fondo del anime de
  Pokémon. NO pixel art. NO realista. NO textura de pintura.
- La geometría, de Minecraft: todo cúbico, aristas rectas.
- SIN TEXTO NI LETRAS de ningún tipo.
- SIN marco, SIN borde, SIN esquinas redondeadas.
- Viñeta suave en las cuatro esquinas.
```

⚠ **Sin transparencia y sin el truco del magenta**, al revés que las medallas.
Con fondo completo no hace falta recortar nada, y una IA hace mucho mejor una
escena opaca que un recorte.

## 4. El caso especial: Monumentos

**No son ruinas antiguas: es un memorial.** Un jardín donde los jugadores dejan
un recuerdo a las personas y a los animales que ya no están.

El primer prompt que escribí lo describía como ruinas, y estaba mal. El bueno
gasta **un tercio de su extensión en prohibiciones**, y hacen falta:

```
PROHIBIDO: cruces y símbolos religiosos, calaveras, huesos, esqueletos,
zombis, niebla, telarañas, árboles secos, cuervos, verjas oxidadas y
cualquier cosa de cementerio de terror.
```

⚠⚠⚠ **Sin esa lista sale un cementerio con niebla y zombis.** Es lo que hay en
los datos de entrenamiento de cualquier IA para «memorial en Minecraft».

⚠⚠ **Sin cruces ni símbolos religiosos**, porque en el servidor hay gente de
todo tipo y un memorial que asume una religión deja fuera a parte de ella.

⚠⚠ **Las placas van LISAS, sin nombres.** Dos motivos: una IA escribiendo texto
siempre falla, y aquí un nombre mal escrito no sería un fallo gracioso. Los
nombres los ponen los jugadores construyendo, que es de lo que va el sitio.

⚠ **El calor de los faroles es lo que hace que no se lea como tristeza.** El
gris violáceo le va bien al sitio, pero solo con gris la ficha diría «esto da
pena»; con luz cálida encima dice «esto se cuida».

Y el texto de la pantalla se corrigió con él: decía *«Lo que el servidor decide
recordar»* y ahora dice *«Para recordar a los que ya no están. Aquí cada jugador
tiene su sitio»* — lo que se recuerda lo elige cada jugador, no el servidor.

## 5. El tamaño, y por qué 512

```
chasis            1380 x 828, escala a medios pasos SIN TOPE (1 · 1,5 · 2 · 2,5)
en 4K             k = 2,5
ficha de Viajes   186 x 200 del chasis
dibujada          186 x 2,5 = 465 px  <- lo más grande que se ve nunca
```

Se generaron a **1024** y se guardan a **512**: cubre los 465 px sin ampliar
nada y baja el peso de 5,3 MB a 1,8.

⚠ Potencia de dos, por los mipmaps.

⚠⚠ **El PNG es cuadrado y los dos huecos no lo son** —la ficha es 186×200 y la
vista del panel 255×120— así que `arte()` **recorta el trozo centrado** con la
proporción del hueco en vez de estirar. Estirar era una línea menos y se notaba:
en la rejilla la luna salía ovalada.

## 6. Si hay que regenerar una

Los siete prompts completos están en el historial de la sesión del 2026-08-31.
La receta corta:

1. la escena, en cuatro o cinco líneas
2. el color dominante, **con su código hexadecimal**
3. el bloque de estilo de §3, tal cual
4. el aviso de los 186 píxeles
5. y, si el sitio tiene una carga emocional, la lista de prohibiciones
