# La interfaz a luz de luna

## Purpose

Cómo se reviste la interfaz de Cobblemon con la identidad del servidor, y hasta
dónde se puede llegar sin tocar código ajeno.

## Dependencies

- [`../technical/client-pack.md`](../technical/client-pack.md) §2 — de dónde sale el pack

## Current Status

**Desplegado.** **323 texturas** revestidas (211 KB), incrustadas en `lunaneon`
y **activadas solas**.

| Pantalla | Modo | Revestidas |
|---|---|---|
| Pokédex | cian | 76 |
| El item de la Pokédex (el que llevas en la mano) | cian | 9 |
| Resumen del Pokémon | gris | 72 |
| PC | gris | 46 |
| Combate | gris | 37 |
| Interacción | gris | 47 |
| Equipo · Comercio · Pastos | gris | 36 |

La carcasa de la Pokédex conserva su color; solo cambia la pantalla.

```
genera    python tools/gen_interfaz.py --comparativa
compila   cd neon && bash build.sh
despliega python tools/desplegar.py neon --reiniciar
publica   python tools/gen_manifest.py --publicar
```

---

## 1. Lo que se puede y lo que no

La Pokédex de Cobblemon se dibuja con **114 texturas** en
`assets/cobblemon/textures/gui/pokedex/`. Todo lo que es fondo, casco, ranura,
pestaña o marco es una textura, y un resource pack las sustituye sin más.

**Pero el texto lo pinta el código.** Son dos colores fijos dentro de
Cobblemon, y ningún resource pack los alcanza:

| Color | Usos | Qué es |
|---|---|---|
| `0x606B6E` | 16 | Gris. Los números, los nombres, las descripciones |
| `0x3A96B6` | 3 | Turquesa. Acentos |

Y **ese gris va encima de los paneles claros**. De ahí la regla que gobierna
todo el revestido:

```
la pantalla cambia de TONO pero conserva la LUMINANCIA
el casco sí se oscurece: encima no hay texto
```

Si oscureciéramos la pantalla, los números quedarían ilegibles y no habría
forma de arreglarlo desde aquí. El turquesa fijo, en cambio, encaja de forma
natural en una paleta azul, así que los acentos siguen pegando.

> **Para una Pokédex de verdad oscura** haría falta un mixin de cliente que
> sustituya esos dos colores. Se planteó y **se descartó de momento**: ata el
> servidor a las clases internas de Cobblemon y se rompe cuando ellos toquen su
> GUI. La opción sigue ahí si algún día compensa.

---

## 2. Cómo se reviste

`tools/gen_interfaz.py` lee las texturas **del jar de Cobblemon** —descargado de
Modrinth y cacheado, no de una carpeta de la máquina— y les cambia el tono.
Nada se dibuja a mano: si Cobblemon cambia sus texturas, se reejecuta.

| | |
|---|---|
| **Solo la pantalla** | La carcasa (`pokedex_base_*`) **no se toca**. Su color es el del objeto Pokédex —hay siete: roja, azul, verde, rosa, negra, blanca, amarilla— y lo elige el jugador. Se probó teñirla al 70 % hacia el azul y, visto en el juego, se descartó: pisarla le quita el sentido a tener siete |
| **Solo el cian** | Se desplazan únicamente los píxeles con tono entre 165° y 205°. Así las plataformas de tipo —fuego naranja, planta verde— se quedan intactas **sin tener que listarlas**: no son cian, no se tocan |
| **76 de 114** | Las otras 38 son los 7 cascos y las que no llevan ni un píxel cian (flechas, iconos). No se incluyen: solo pesarían |

---

## 3. Cómo llega activado

**El pack vive dentro del jar de `lunaneon`**, en
`resourcepacks/interfaz_luna/`, y el mod lo registra con
`ResourcePackActivationType.ALWAYS_ENABLED`.

### ⚠️ `DEFAULT_ENABLED` no vale para un resource pack

Fue el segundo intento fallido, y el javadoc de Fabric lo dice con todas las
letras:

> *«a resource pack cannot be enabled by default, only data packs can»*

Con `DEFAULT_ENABLED` el pack se registraba y **se quedaba apagado**, sin un
solo aviso en el log. Lo que lo hizo invisible fue un error mío aparte:
`registerBuiltinResourcePack` **devuelve un booleano** y yo lo ignoraba. Ahora
se registra el resultado:

```
[main/INFO]: Interfaz: revestido de luna activado
```

Si algún día vuelve a fallar, saldrá `el revestido NO se registro` en el log en
vez de no salir nada.

> **`ALWAYS_ENABLED` significa que el jugador no puede quitarlo** desde el menú
> de paquetes. Es una decisión consciente: es la identidad visual del servidor,
> como el resto del pack. Si algún día se quiere que sea opcional, hay que
> volver a `NORMAL` y aceptar que casi nadie lo activará.

### El intento que falló, y por qué

Primero se hizo como un `.zip` en `resourcepacks/` más una línea añadida a la
plantilla `config/yosbr/options.txt`. **El zip se instalaba y se quedaba
apagado.** No fue un descuido: YOSBR copia esa plantilla **solo si
`options.txt` no existe**, así que a quien ya había jugado una vez no le
llegaba nunca — es decir, a todo el mundo.

La vía buena estaba a la vista en el `options.txt` de cualquier jugador:

```
resourcePacks:["vanilla","fabric","cobblemon:gyaradosjump","cobblemon:regionbiasforms",…]
```

Esos `cobblemon:` son packs que Cobblemon lleva **dentro de su jar** y registra
igual. Por eso aparecen solos.

> El registro va en un `try/catch`: es puramente cosmético y de cliente, pero el
> mismo jar corre en el servidor. Que falle no puede llevarse por delante el
> registro de los 96 bloques de neón, que sí es esencial.

---

## 4. Licencia

Cobblemon es **MPL-2.0**, que permite obra derivada y uso comercial — al
contrario que la CC-BY-NC-ND de `stendhal`, que por eso se excluyó (D-031).

El MPL es copyleft **por fichero**: estas texturas derivan de las suyas, así que
siguen siendo MPL-2.0. El pack lleva dentro `LICENSE-COBBLEMON.txt` con la
atribución y el aviso. No hace falta nada más.

## Next Actions

1. Verlo en el juego y ajustar tono/saturación si hace falta — es una constante
   en `gen_interfaz.py`
2. Decidir si el resto de interfaces de Cobblemon (PC, resumen, combate) se
   revisten igual: el mismo script sirve cambiando la ruta

## Related Systems

- [El pack de cliente](../technical/client-pack.md) · [La interfaz propia](interfaz-cliente.md)

---

## 5. El haz de la Poké Ball

Al guardar un Pokémon con **R** —y al sacarlo— Cobblemon dibuja un haz **rojo**.
Ahora es azul luna, el mismo de la pantalla.

### Por qué no vale un resource pack

La textura del haz (`textures/phase_beam.png`) es **blanca** (`#DFDFDF`). El rojo
sale de multiplicarla por un vector escrito en el código:

```kotlin
// PokemonRenderer.kt:71
val recallBeamColour = Vector4f(1F, 0.1F, 0.1F, 1F)
```

Repintar la textura de cian daría un haz **casi negro**: el verde y el azul
quedan al 10 %. No hay ninguna opción por datos ni por configuración — ese
`Vector4f` es el único sitio donde vive el color.

### Por qué basta con reflexión, sin mixin

`val` en Kotlin fija la **referencia**, no el contenido, y `Vector4f` es
mutable. Así que se le cambia el valor por dentro:

```java
Field campo = Class.forName(CLASE).getDeclaredField("recallBeamColour");
campo.setAccessible(true);
((Vector4f) campo.get(null)).set(R, G, B, 1F);
```

Va **por reflexión y no compilando contra Cobblemon** a propósito: es un campo
interno de su renderizador, no una API que ellos mantengan. Con una dependencia
de compilación, el día que lo renombren el mod reventaría en la pantalla del
jugador. Así, como mucho, el haz se queda rojo y aparece una línea en el log:

```
[main/INFO]: Haz de la Poke Ball: azul luna        ← funcionó
[main/WARN]: No se pudo tenir el haz ...           ← Cobblemon lo movió
```

Se hace en `CLIENT_STARTED`, no durante la inicialización: tocarlo antes
obligaría a cargar la clase del renderizador cuando aún no están listas sus
texturas. El haz no se dibuja hasta que alguien saca un Pokémon, así que llega
de sobra.

> El color está apagado a propósito (`#8B93D8`, el de la Pokédex). El original
> es rojo a plena saturación y contra la noche fija de la ciudadela se come todo
> lo demás. Si se ve demasiado lavado, son tres constantes en
> `LunaNeonCliente.java`.
