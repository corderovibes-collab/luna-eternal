# Voces de la Pokédex

## Purpose

Cómo se producen, se empaquetan y se reproducen las descripciones habladas que
suenan al escanear un Pokémon con la Pokédex.

## Dependencies

- [`generations.md`](generations.md) — solo tienen voz las generaciones activas
- [`../technical/distribucion.md`](../technical/distribucion.md) — el jar viaja
  en el pack del cliente

## Related Documents

- [`../ui/interfaz-cliente.md`](../ui/interfaz-cliente.md)

## Current Status

**IMPLEMENTADO Y DESPLEGADO** (2026-08-22). **256 voces**: Kanto **completo**
(151 de 151), Johto **completo** (100 de 100) y 5 formas de Alola. En el
arranque del servidor: `Pokédex: 256 voces listas`. Cubiertas por
`/luna autotest` (136 comprobaciones).

## Last Decision

Las voces se generan con un script y **nunca se editan a mano** los tres
ficheros que tienen que ir sincronizados.

---

## 1. Las tres salidas que tienen que ir sincronizadas

Ese es el problema real que resuelve el generador. Son tres sitios y basta con
olvidarse de uno para que el jugador escanee y **no suene nada, sin un solo
error en ningún log**:

```
mod/src/client/resources/assets/lunaeternal/sounds/pokedex/<clave>.ogg
mod/src/client/resources/assets/lunaeternal/sounds.json
mod/src/main/resources/voces.txt          <- la lista que lee VozService
```

Por eso hay **una** fuente —los MP3 de origen— y las tres salidas se rehacen
juntas:

```
python tools/gen_voces.py --origen "RUTA"
```

`voces.txt` vive en `main/resources` **a propósito**: ese conjunto se compila en
los **dos** lados, así que el servidor sabe a quién mandarle voz y el cliente
sabe si pintar el botón encendido, leyendo la misma lista.

> ⚠️ **El generador borra y rehace el catálogo ENTERO en cada ejecución.**
> Pasarle solo una generación deja el mod sin las demás. Hay que stagear
> siempre **todo**.

---

## 2. De dónde salen los nombres

El origen son carpetas `NNNN - Nombre`. El número se tira —Cobblemon no lo
usa— y el nombre se pasa a la forma que usa el juego:

| Origen | Clave | Por qué |
|---|---|---|
| `Bulbasaur` | `bulbasaur` | el caso normal |
| `Nidoran♀` | `nidoran_f` | su especie se llama `Nidoran-F` |
| `Mr. Mime` | `mr_mime` | |
| `Farfetch'd` | `farfetchd` | |
| `Ho-Oh` | `ho_oh` | **lleva guion** |
| `Porygon2` | `porygon2` | **lleva dígito** |
| `Rattata de Alola` | `rattata_alola` | en Cobblemon es una **forma**, no una especie |

Ho-Oh y Porygon2 tienen comprobación propia en el autotest: son los dos nombres
que la normalización podía estropear **en silencio**.

### Las formas regionales caen a la especie base

Un Rattata de Alola es la especie `rattata` con la forma `Alola`. Si esa forma
tiene grabación propia se usa; si no, cae en la de la especie base, que es mejor
que el silencio — describe al mismo bicho.

---

## 3. ⚠️ Las trampas, todas pagadas en horas

### 3.1 · El desfase de Kanto: los ficheros 71-150 iban +1

En la sesión de grabación **se saltó Victreebel (dex 71)** pero la numeración de
los ficheros siguió corrida. Resultado: `71.MP3` contenía **Tentacool** (dex
72), `72.MP3` Tentacruel, y así hasta `150.MP3` = Mew (dex 151).

**Johto NO lleva ese desfase.** Los ficheros empiezan en 152 —el 151 no
existe— y son exactamente 100 para las exactamente 100 especies: **el hueco del
151 fue la realineación**. A partir de ahí el número de fichero vuelve a ser el
de Pokédex.

> Esto no se dedujo, **se verificó**: el usuario escuchó `152.MP3` y era
> Chikorita. Equivocarse habría sido asignar mal 100 voces, y nada lo habría
> detectado — cada Pokémon habría descrito al siguiente.

Victreebel se grabó el 2026-08-22 y con él **Kanto quedó completo**.

### 3.2 · Los .ogg salen siempre como «modificados» en git

Un flujo Ogg lleva un **número de serie aleatorio**, así que reconvertir el
mismo MP3 nunca da los mismos bytes. Regenerar el catálogo marca los 251 como
modificados aunque no haya cambiado ni una nota. **Es esperado, no es un
problema**: el jar viaja entero de todos modos.

### 3.3 · Las 5 formas de Alola hay que conservarlas a mano

Vienen de un lote anterior cuya **carpeta de origen ya no existe** en Descargas.
Como el generador rehace el catálogo desde cero, las perdería. El procedimiento
antes de regenerar:

1. copiar los 5 `.ogg` (`rattata_alola`, `raticate_alola`, `raichu_alola`,
   `sandshrew_alola`, `sandslash_alola`) fuera
2. correr `gen_voces.py`
3. devolverlos y volver a añadir sus entradas a `sounds.json` y `voces.txt`

> **Deuda pendiente:** lo suyo sería recuperar esos 5 MP3 y meterlos en la
> carpeta de origen con el resto, para que el pipeline vuelva a tener **una**
> sola fuente. Mientras no estén, este paso manual es obligatorio.

### 3.4 · Hace falta ffmpeg

Minecraft solo reproduce **OGG Vorbis**. La conversión es mono, `-q:a 4`,
44 100 Hz — un sonido de interfaz no necesita estéreo. Sin ffmpeg en el PATH el
generador aborta.

### 3.5 · `stream: true` en `sounds.json`

Son clips de varios segundos, que es justo el caso para el que Minecraft
recomienda transmitir en vez de cargar entero en memoria.

---

## 4. Lo que pesa

| | |
|---|---|
| 256 `.ogg` | ~37 MB |
| `lunaeternal.jar` | 24 MB → **41 MB** |
| Sobre el pack completo | 474 → 490 MB |

Es el mayor consumidor de espacio del mod con diferencia. Aceptado: sobre un
pack de 490 MB no mueve la aguja, y es contenido que **no se puede conseguir en
ningún otro servidor** (P1 del criterio de aceptación de CLAUDE.md §1).

---

## 5. El catálogo en código

`VozService` (`mod/src/main/java/net/pokereport/luna/pokedex/`) es **el catálogo
y nada más**: no toca red, ni base de datos, ni sonido. Se separa a propósito
para poder comprobarlo en `/luna autotest` sin necesidad de un jugador
escaneando (`MOD-006`).

```java
VozService.clave("Rattata", "Alola")  // -> "rattata_alola"
VozService.clave("Pikachu", "Gmax")   // -> "pikachu"  (cae a la base)
VozService.clave("Treecko", "")       // -> ""         (Gen 3, sin voz)
```

> ⚠️ **El caso «sin voz» del autotest es Treecko, y antes era Victreebel.** Se
> usaba Victreebel porque era el único hueco **real**; al grabarlo, esa
> comprobación habría empezado a fallar. Treecko es de Gen 3 y no va a tener voz
> mientras solo estén activas Kanto y Johto ([generations.md](generations.md)),
> así que sigue siendo un caso real y no uno inventado.

### El cooldown, que no es opcional

El cliente de Cobblemon manda un «he terminado» **por tick** hasta que el
servidor le confirma. Sin cooldown la voz **se oye duplicada** — se oyó.

---

## Next Actions

1. **Recuperar los 5 MP3 de las formas de Alola** y meterlos en la carpeta de
   origen, para eliminar el paso manual de §3.3
2. Grabar las formas regionales que faltan (Galar, Hisui, Paldea) si algún día
   se activan esas generaciones
3. Cuando exista la Pokédex propia (`ART-002`), decidir si la voz se dispara
   también desde la pantalla y no solo al escanear
