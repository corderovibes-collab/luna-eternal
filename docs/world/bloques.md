# Los bloques de obra

## Purpose

Con qué se construye la ciudadela **aparte del neón**: hormigón, metal,
rejilla, vidrio y pavimento. Qué hay, por qué existe y cómo se regenera.

## Dependencies

- [`neon.md`](neon.md) — la sexta familia, y la que fija la paleta de las demás
- [`construccion.md`](construccion.md) — permisos, Axiom, WorldEdit
- [`../technical/client-pack.md`](../technical/client-pack.md) §2-ter — qué va en cada lado

## Related Documents

- [`../../CLAUDE.md`](../../CLAUDE.md) §5 — D-008 (la licencia como criterio), D-029 (por qué `lunaneon` es un mod aparte)

## Current Status

**Desplegado el 2026-08-16. Falta mirarlo dentro del juego.**

```
mod        lunaneon 0.1.0 · neon/ · cliente Y servidor · 1,63 MB
bloques    602 = 96 de neón + 506 de obra
materiales 126 de obra, en 5 familias
recursos   230 texturas · 1 540 modelos · 602 blockstates · 602 botines
           3 911 ficheros en total
generado   python tools/gen_bloques.py      (nada de esto se escribe a mano)
verificado python tools/gen_bloques.py --verificar  ->  correcto
maqueta    python tools/gen_bloques.py --maqueta -> build/bloques/*.png
pack       publicado · lunaneon-0.1.0-54aee8036a.jar
servidor   "Obra: 506 bloques en 126 materiales, 4 pestanas" · Done (7,5 s)
en el juego 12 de 12 piezas colocadas con setblock y sus estados, una por
           familia y por forma — incluidos los tres que más se rompen:
           la escalera en esquina interior, el muro con un lado alto y
           otro bajo, y el panel conectado a dos lados
muestra    ciudadela, de -6 a 5 en x, y=64, z=22. Se quita con //set air
sin romper el neón sigue byte a byte como estaba
```

> ⚠️ **Lo que falta es mirarlo.** Que un bloque se coloque no dice nada de cómo
> se ve: el servidor no dibuja. Las láminas de contacto dicen que las texturas
> encajan consigo mismas y la verificación dice que ningún modelo apunta a la
> nada, pero ninguna de las dos cosas prueba que una escalera de hormigón se
> vea bien en una esquina interior a las tres de la madrugada de la ciudadela.
> Es una vuelta de diez minutos con una pila de cada familia.

---

## 1. Qué hay

| Familia | Materiales | Formas | Bloques |
|---|---|---|---|
| **Hormigón** | 3 acabados × 16 colores | bloque · losa · escalera · **muro** · valla | 240 |
| **Metal** | 8 aleaciones × 4 acabados | bloque · losa · escalera · **pilar** · valla | 160 |
| **Rejilla** | 8 aleaciones | bloque · losa · panel | 24 |
| **Vidrio** | 2 acabados × 16 colores | bloque · panel | 64 |
| **Pavimento** | 6 tipos | bloque · losa · escalera | 18 |
| | | | **506** |

**Las formas no son las mismas a propósito.** El hormigón lleva **muro** porque
un parapeto de azotea es de hormigón; el metal lleva **pilar** porque una viga
es de metal. Al revés no se usan, y cada forma que no se usa son cuatro modelos
más en el jar y una línea más de scroll en el inventario para todo el mundo.

### Los acabados

| Hormigón | qué es |
|---|---|
| `pulido` | hormigón visto: grano fino, veteado amplio, algún poro |
| `rayado` | nervado, cuatro nervaduras verticales de 4 px. Da ritmo a una fachada |
| `panel` | placa prefabricada con junta hundida y cuatro tornillos |

| Metal | qué es |
|---|---|
| `liso` | chapa lisa con un brillo diagonal tenue |
| `cepillado` | rayas horizontales finas, como salido de la lijadora |
| `estriado` | pletina antideslizante. Suelo técnico, rampas, pasarelas |
| `remachado` | panel con costura hundida y ocho remaches |

Aleaciones: `acero` · `acero_oscuro` · `aluminio` · `titanio` · `cromo` ·
`cobre` · `laton` · `grafito`.

Pavimentos: `asfalto` · `asfalto_claro` · `terrazo_claro` · `terrazo_oscuro` ·
`losa_grande` · `adoquin_fino`.

---

## 2. Por qué existen

**Vanilla no tiene ni una escalera, ni una losa, ni un muro, ni una valla de
hormigón.** Tiene el cubo y se acaba ahí. Para una ciudad moderna eso significa
que en cuanto hay que rematar un borde, una cornisa, un parapeto o una rampa,
hay que salirse a la piedra o al ladrillo — y la fachada deja de leerse como
hormigón. Es el agujero más grande de vanilla para construir moderno, y por eso
existen mods enteros dedicados solo a taparlo.

De metal, directamente, no hay: hay bloque de hierro y bloque de cobre. Ni
chapa lisa, ni cepillado, ni pletina estriada, ni panel remachado, ni rejilla.

---

## 3. Por qué las texturas se dibujan y no se bajan

**Por licencia (D-008).** Casi todo el arte de bloques que circula por GitHub y
CurseForge es ARR o CC-BY-NC, y el **NC** choca de frente con el plan de vender
paquetes (D-007): es exactamente la cláusula que descartó CobbleVerse (D-006).
Un servidor con tienda que reparte texturas no comerciales tiene el mismo
problema que tendría con CobbleVerse, solo que más pequeño y más difícil de ver.

Dibujarlas cuesta un fichero de Python y no hipoteca nada.

**Y sale ganando**, que es lo que no era obvio: los 16 colores del hormigón y
del vidrio son **los mismos 16 del neón**, rebajados a tono de obra por una
fórmula (`tono_obra` en `tools/bloques/ciudad.py`). Un neón cian pega con un
hormigón cian porque *son el mismo color*, no porque alguien los haya
emparejado a ojo.

```
tono_obra:  se le quita el 60 % de saturación   -> deja de ser un rótulo
            se le comprime el valor a 0,16-0,78 -> deja de ser papel o carbón
            NO se le toca el tono               -> sigue siendo el mismo cian
```

---

## 4. La regla de dibujo: contraste bajo, y que encaje

Dos reglas, y las dos se pagan si se olvidan.

**Contraste bajo.** Una fachada moderna son superficies grandes y planas. Todo
el relieve va entre el 2 % y el 20 % de variación, y solo pasa de ahí donde hay
una junta o un remache de verdad. Con más ruido el bloque se lee como piedra
rústica, que es justo lo contrario de lo que se busca.

**Que encaje consigo misma.** Todo el ruido es **periódico en 16**: la columna
15 pega con la columna 0 del bloque de al lado. Sin eso, una pared de 40
bloques enseña la retícula. Por eso el brillo del metal liso va en
`sin(2π(x+y)/16)` y no en un degradado de arriba abajo — un degradado deja una
raya cada 16 px.

> `python tools/gen_bloques.py --maqueta` dibuja cada textura **repetida 2×2**
> justamente para eso: es la única forma de *ver* si encaja. Una textura que no
> encaja no se nota en el editor y se nota muchísimo en una fachada.

### Lo que se quitó después de mirar la maqueta

| Se quitó | Por qué |
|---|---|
| El **destello del vidrio** (3 px claros en diagonal) | Un reflejo pintado en la textura se repite idéntico en las mil ventanas de una torre, y un reflejo que se repite mil veces no es un reflejo: es una mancha. En la lámina se leía como suciedad — el mismo defecto que costó una noche entera en el PokePad |
| Dos tercios de los **poros del hormigón** | Empezaron en el 3 % de los píxeles y se leían como una salpicadura. A esta escala, tres píxeles oscuros por bloque ya son un dibujo |
| Un cuarto acabado de hormigón, **`liso`** | A diez bloques era indistinguible del hormigón de vanilla. Esa es la prueba que importa: si dos acabados no se separan a diez bloques, uno de los dos sobra |

---

## 5. Cómo está organizado el generador

Un solo punto de entrada, y es deliberado:

```
tools/gen_bloques.py        el orquestador: borra, genera, verifica, maqueta
tools/bloques/comun.py      color, ruido encajable y LAS FORMAS
tools/bloques/neon.py       la familia neón
tools/bloques/ciudad.py     las cinco familias de obra
```

**Por qué uno y no seis.** Las familias escriben en el **mismo** árbol de
recursos y comparten tres ficheros únicos: `es_es.json`, `en_us.json` y los
tags. Con un generador por familia, el último en ejecutarse pisaba a los demás
y los bloques de los primeros se quedaban sin nombre. Y el antiguo
`tools/gen_neon.py` empezaba **borrando `assets/lunaneon` entero**: ejecutarlo
hoy dejaría el mod con 96 bloques y 506 huecos. Por eso ese fichero ya no
genera nada — delega, y avisa.

**Las formas se escriben una sola vez.** Son las mismas 40 variantes de
escalera y el mismo multipart de muro para el neón, el hormigón y el metal.
Duplicarlas es como se acaba con una escalera que mira al revés en las esquinas
interiores, y nadie se entera hasta que hay una plaza construida encima.

> Las plantillas de muro, valla y panel **no están inventadas**: se leyeron del
> propio jar de Minecraft 1.21.1 y se apunta a sus modelos padre
> (`minecraft:block/template_wall_post`, `fence_post`,
> `template_glass_pane_*`), que es exactamente lo que hace vanilla con la
> piedra o el cristal.

### La comprobación de que el traslado no rompió el neón

Se movieron 96 bloques de sitio. La prueba de que salieron intactos no es
mirarlos: es que **`git status` no marca ni un fichero de neón como
modificado**. Los 96 blockstates, los 272 modelos, las 64 texturas y los 96
botines salieron byte a byte idénticos. Lo único que cambió son los tres
ficheros compartidos, que ahora llevan también las otras cinco familias.

---

## 6. Los tres detalles que no se ven venir

**1 · Los tags no son decoración.** `minecraft:walls` es lo que hace que dos
muros **se peguen** entre sí. Sin él, cada muro se queda solo como un poste y
una barandilla de veinte metros son veinte postes sueltos. Igual
`minecraft:fences` con las vallas.

**2 · La capa de dibujado la decide el cliente, y el servidor no puede
avisar.** Un bloque con transparencia dibujado en la capa sólida se ve
**negro**, no transparente. El mundo carga, el bloque existe, se puede romper y
colocar — y en la pantalla es un cubo negro. Van dos capas distintas:

```
translucent  el VIDRIO   alfa a medias (46 o 168 de 255), se ordena de atrás
                         adelante para que se vea a través
cutout       la REJILLA  alfa de 0 o de 255 y nada en medio. Más barata, y no
                         necesita ordenar nada
```

**3 · Los ajustes del vidrio van los cuatro o no va ninguno.** Sin
`suffocates` el jugador se asfixia dentro de una vidriera; sin `blockVision` la
pantalla se le pone negra; sin `solidBlock` no pasa la luz. Lo mismo con la
rejilla: sin `solidBlock(nunca)` una pasarela proyecta una sombra maciza y se
ve como una chapa negra.

---

## 7. Lo que cuesta

| | |
|---|---|
| Jar | de 248 KB a **1,63 MB**. Sobre un pack de 185 MB no mueve la aguja (P10) |
| Ficheros en el jar | 3 911 |
| **Estados de bloque** | **29 330** — ver abajo |

**Los estados son el número que hay que mirar, no los bloques.** Un muro tiene
324 estados él solo (arriba × 4 lados × 3 alturas × anegado), y de los 29 330
del mod, **19 776 son del hormigón** y casi todos de sus 48 muros. Vanilla anda
por los 26 000, así que el mod aproximadamente lo duplica.

Es asumible —vanilla paga lo mismo por sus 22 tipos de muro, y hay mods de
bloques con bastante más— pero es la cifra que hay que vigilar si algún día se
añaden más familias con muro. **Si hubiera que recortar, el sitio es ese**: los
muros de hormigón, no el número de colores.

---

## 8. Regenerar

```bash
python tools/gen_bloques.py             # regenera los 3 911 ficheros
python tools/gen_bloques.py --verificar # cruza bloques, modelos y texturas
python tools/gen_bloques.py --listar    # la cuenta por familia
python tools/gen_bloques.py --maqueta   # láminas de contacto
cd neon && ./build.sh                   # compilar (JDK 21, ver el script)
```

La lista viva de materiales está en las tablas de `tools/bloques/ciudad.py`
(`HORMIGON`, `ALEACIONES`, `ACABADOS_METAL`, `VIDRIOS`, `PAVIMENTOS`) y la de
colores en `PALETA` de `tools/bloques/neon.py`. De ahí salen las texturas, los
modelos, los nombres, los tags **y `Catalogo.java` y `Paleta.java`**.

> ⚠️ **`Catalogo.java` y `Paleta.java` se generan. Tocarlos a mano los pisa.**
> Y están generados por un motivo: si la lista viviera en dos sitios, un día
> habría un material registrado en Java cuya textura no existe, y eso en la
> pantalla del jugador es el cubo negro y morado.

---

## 9. Desplegar: el cliente PRIMERO

Igual que con el neón, y por lo mismo. Fabric sincroniza el registro: **el
servidor tiene que ser un subconjunto del cliente**. Si el servidor conoce 602
bloques y el cliente 96, a la gente la echa con `Registry remapping failed`.

```
1. compilar          cd neon && ./build.sh
2. publicar el pack  python tools/gen_manifest.py --publicar
3. que la gente actualice (el launcher lo hace solo)
4. y ENTONCES subir el jar al servidor
```

## Last Decision

**2026-08-16 — se añaden las cinco familias de obra**, generadas con texturas
propias y colgando del mod `lunaneon` que ya existía. No es un mod nuevo: el
jar que se reparte sigue teniendo solo bloques, y añadir un segundo mod sería
un jar más que sincronizar y una forma más de dejar a alguien fuera.

## Next Actions

- ⬜ Mirarlo dentro del juego: una pila de cada familia, de noche, en la ciudadela
- ⬜ Decidir si el hormigón necesita los 48 muros o basta con los grises
