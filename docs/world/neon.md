# Los bloques de neón

## Purpose

Con qué se construye la ciudadela nocturna. Qué bloques hay, cómo se usan y
por qué existen como mod propio.

## Dependencies

- [`construccion.md`](construccion.md) — permisos, Axiom, WorldEdit
- [`../technical/client-pack.md`](../technical/client-pack.md) §2-ter — qué va en cada lado

## Current Status

**Desplegado y verificado en vivo el 2026-08-13.**

```
mod        lunaneon 0.1.0 · neon/ · cliente Y servidor · 248 KB
bloques    16 colores × 6 formas = 96
recursos   64 texturas · 272 modelos · 96 blockstates · 96 botines
generados  tools/gen_neon.py  (nada de esto se escribe a mano)
recursos   python tools/gen_neon.py --verificar  ->  correcto
servidor   "Neon: 96 bloques en 16 colores" · Done (5,3 s)
en el juego las 6 formas colocadas y leídas con setblock, con sus
           estados (luz, axis, facing, half, type). Las 7 correctas
cliente    publicado en el manifiesto; el jar se descarga byte a byte
           idéntico al local desde raw.githubusercontent
sin romper /luna autotest sigue en 112/112
```

> ⚠️ **Lo único que NO se ha comprobado desde consola es el aspecto**: que
> brillen, y que la luz emitida sea 0/7/15. No hay forma de leer el nivel de
> luz de una posición por comando. Es una mirada de diez segundos dentro del
> juego, y es lo primero que hay que hacer al entrar.

---

## 1. La idea: brillo y luz son cosas distintas

Es lo único que hay que entender para usarlos.

| | qué es | cómo se cambia |
|---|---|---|
| **Brillo** | cómo se dibuja el bloque a sí mismo | **siempre a tope.** No se toca |
| **Luz** | cuánta ilumina a su alrededor | propiedad `luz`: 0 · 7 · 15 |

Un neón con `luz=0` **se ve encendido pero no ilumina nada**.

Eso no es un detalle: es lo que permite que la ciudadela siga siendo nocturna.
Si cada bloque tuviera que iluminar para verse encendido, una plaza con mil
neones acabaría con luz de mediodía y el cielo de noche fija
(`fixed_time 18000`, ver [worlds.md](worlds.md)) no serviría de nada. Los
carteles brillan; la calle sigue a oscuras salvo donde tú decidas.

> **El truco no es nuestro: es el bloque de magma de vanilla**, que se ve al
> rojo vivo y solo da luz 3. Se consigue con `emissiveLighting` en los ajustes
> del bloque — nativo, sin OptiFine, sin Continuity y sin tocar el renderizador.
> Funciona igual con Sodium, que desde la 0.6 implementa la API de renderizado
> de Fabric.

### ⚠️ La luz que sueltan es BLANCA, y no se puede arreglar desde el mod

Un neón cian **se ve** cian pero **ilumina en blanco**. No es un fallo nuestro
ni algo que `lunaneon` pueda cambiar: **el motor de luz de Minecraft no tiene
color**. Guarda un número del 0 al 15 por bloque y nada más. No existe ninguna
API para emitir luz cian, ni en vanilla ni en Fabric.

Se arregla en el **cliente**, y hay dos formas que dan resultados distintos:

| | Qué hace | Coste | Quién lo ve |
|---|---|---|---|
| **Shine** *(instalado)* | Halo de color alrededor del bloque, tomando el color del propio píxel. Se aplica **solo a bloques emisores**, así que los 96 neones entran sin declarar ninguno | casi nada | **todos**, sin tocar nada |
| **Shaders** *(instalados, apagados)* | Luz de color **de verdad**: tiñe el suelo y las paredes de alrededor. Complementary → *Performance* → `COLORED_LIGHTING` (viene en `0`) | FPS | quien los active |

**No son excluyentes y hacen cosas distintas.** Shine da el resplandor; los
shaders dan la iluminación. Para una ciudad de neón, lo que más se nota de
lejos es el resplandor.

> **Sobre los shaders y nuestros bloques:** el `block.properties` de
> Complementary lista los emisores de vanilla uno a uno, y `lunaneon:` no está.
> Euphoria Patches tiene un canal oficial para añadir bloques de mods
> —[EuphoriaPatches/propertiesFiles](https://github.com/EuphoriaPatches/propertiesFiles)—
> así que la vía existe y es legítima. **Sin verificar en el juego** si sin esa
> contribución el shader los trata como emisores genéricos o los ignora.

### Los tres niveles

```
luz=0   apagado   no ilumina           rótulos, revestimientos, paredes enteras
luz=1   suave     ilumina 7            ambiente, callejones, interiores
luz=2   pleno     ilumina 15           farolas, plazas, focos
```

Se coloca **en `luz=2`** por defecto: es lo que espera quien pone un bloque que
se llama «neón». Apagarlo después es un clic; descubrir por qué no ilumina es
media tarde.

---

## 2. Las seis formas

| Forma | ID | Para qué |
|---|---|---|
| **Bloque** | `neon_<color>` | muro y suelo |
| **Losa** | `neon_<color>_losa` | cornisas, escalones sueltos |
| **Escalera** | `neon_<color>_escalera` | rampas y, sobre todo, **esquinas a 45°** |
| **Pilar** | `neon_<color>_pilar` | columna con carcasa oscura y una línea de luz. Se orienta como un tronco |
| **Panel** | `neon_<color>_panel` | chapa de 1 px pegada a cualquiera de las 6 caras. **El cartel** |
| **Tubo** | `neon_<color>_tubo` | barra de 4×4 a lo largo de un eje. El tubo de neón clásico |

**Panel y tubo son los que hacen que parezca neón** y no una pared de colores.
El bloque entero y la escalera son estructura; el panel y el tubo son el trazo.

### Los 16 colores

Son los nombres de los tintes de vanilla —para no tener que aprenderse una
tabla nueva— pero con los valores **subidos a intensidad de neón**: más
saturados y más claros que la lana o el hormigón del mismo nombre.

```
blanco   gris_claro  gris     negro
marron   rojo        naranja  amarillo
lima     verde       cian     azul_claro
azul     morado      magenta  rosa
```

> **`negro` no es un neón: es el grafito con el que se enmarca todo lo demás.**
> Un neón necesita algo oscuro al lado o no se lee como neón. Es también la
> carcasa que llevan los pilares por dentro.

---

## 3. Cómo se usa

### Con el ratón

Están **todos en su propia pestaña del inventario creativo**
(*Luna Eternal · Neon*), agrupados por color y no por forma: se elige la paleta
primero y la pieza después, que es como se construye.

**Clic derecho con la mano vacía cambia el brillo** entre apagado, suave y
pleno. Suena un tono distinto en cada uno, así que se oye cuál has puesto sin
mirar. Hace falta creativo o **OP nivel 2** — el mismo que ya exige Axiom
(D-028), para no inventar un segundo sistema de permisos.

### Con WorldEdit

```
//set lunaneon:neon_cian                 pone el neón cian encendido
//set lunaneon:neon_cian[luz=0]          ... apagado
//set lunaneon:neon_magenta_tubo[axis=x] una línea de tubo en el eje X
//replace lunaneon:neon_cian lunaneon:neon_magenta
```

### Con Axiom

Aparecen en su paleta como cualquier otro bloque, con su selector de estados
para `luz`, `facing` y `axis`. **Por eso el mod tiene que estar en el cliente**
— ver §5.

---

## 4. Por qué un mod, y no un datapack o un resource pack

Se descartaron en este orden (P5: mínimo de dependencias, de menos a más).

| Vía | Por qué no |
|---|---|
| **Bloques de vanilla** | No existe ninguna escalera ni losa que emita luz. Y de bloques enteros que iluminen hay ~8, así que no llegan ni a 8 colores, mucho menos 16 × 6 |
| **Datapack** | La luz que emite un bloque **no es un dato**: está en el código del bloque. Un datapack no puede cambiarla |
| **Resource pack** | Puede repintar la glowstone de rosa. Pero sigue habiendo un solo bloque, y sigue sin haber escaleras que brillen |
| **Adoptar un mod de neón de terceros** | Los hay (*Simple Neon Lights*, *Neon Lights*) y hacen casi esto. **Ninguno declara licencia comercial clara**, que es criterio de selección antes que la funcionalidad (D-008) — es exactamente lo que descartó CobbleVerse. Además ata la ciudadela a que un tercero porte su mod a 1.22 |
| **Polymer** (bloques falsos, servidor solo) | Mantiene el cliente sin mods, pero **Axiom no los vería**: Axiom es de cliente y solo conoce los bloques del registro del cliente. Es decir, no se podría construir la ciudadela con la herramienta con la que se está construyendo la ciudadela |

Lo que queda es escribirlo, y es poco código: el contenido son ~600 ficheros
generados y la lógica son seis clases pequeñas.

---

## 5. Por qué va en el cliente, si el otro mod no

`lunaeternal` es solo de servidor y así se queda. `lunaneon` **tiene que estar
en los dos lados**, y no es una excepción caprichosa a D-026:

- Un bloque no existe hasta que las dos partes saben que existe. Sin el jar en
  el cliente, un neón se vería como **el cubo negro y morado** de textura
  ausente.
- **Fabric sincroniza el registro al conectar.** Un cliente al que le falte un
  bloque que el servidor sí tiene **no entra**: lo echa con
  *«Registry remapping failed»*. No es opcional para nadie.
- Axiom es de cliente. Sin el mod, no puede ofrecer los bloques.

**Son dos mods separados a propósito**, no una carpeta más dentro de `mod/`:
así el jar que se reparte a los jugadores **solo tiene bloques**. Ni economía,
ni base de datos, ni una línea de lógica de juego que valga la pena decompilar.

> **Esto no toca P9-bis ni D-026.** Aquello va de **pantallas** —que se harán
> con arte propio cuando llegue (`ART-002`)— y de no fijar el diseño a la
> rejilla de un cofre. Un bloque no es una pantalla.

---

## 6. Cómo se regenera

**Nada de esto se escribe a mano.** La lista de colores vive en un solo sitio:
la constante `PALETA` de `tools/gen_neon.py`. De ahí salen las texturas, los
modelos, los blockstates, los botines, los idiomas **y `Paleta.java`**, que es
la lista tal y como la ve el mod.

```
python tools/gen_neon.py             # regenera los ~600 ficheros
python tools/gen_neon.py --verificar # cruza bloques, modelos y texturas
cd neon && bash build.sh             # compila
```

> **Por qué se genera también el Java:** si la lista estuviera en dos sitios,
> antes o después existiría un color registrado en el código cuya textura no
> se generó. Eso es, literalmente, el cubo negro y morado.

`--verificar` es el equivalente aquí de la regla `MOD-006`. Comprueba que cada
bloque tiene blockstate, que cada variante fija `luz`, que cada modelo
referenciado existe, que cada textura existe, que hay botín y nombre en los dos
idiomas, y que no hay modelos ni texturas huérfanos.

> **Hace falta porque el servidor no lee los assets**, así que arranca tan
> contento con un modelo roto. El fallo solo aparece en la pantalla del
> jugador — y para entonces el pack ya está publicado.

---

## 7. Desplegar: el orden importa

**El cliente va primero.** Si el servidor registra los bloques antes de que los
jugadores tengan el mod, la sincronización de registro **los deja fuera a
todos**.

```
1. python tools/gen_manifest.py --publicar     el jar y el manifiesto, al repo público
2. python tools/desplegar.py neon --reiniciar  el jar, al servidor
3. todo el mundo relanza el launcher           se actualiza solo
```

`tools/desplegar.py` borra el jar anterior antes de subir, usa el endpoint de
binarios y **compara el tamaño** al terminar. Los tres pasos existen por la
misma tarde perdida: una subida se corrompió sin dar error, con el tamaño
coincidiendo, y el servidor murió con `Unexpected end of ZLIB input stream`
dentro de una traza que no se parecía en nada a la causa.

> ⚠️ **Quien entre con un `.mrpack` importado a mano tiene que reimportarlo.**
> El launcher se actualiza solo; PrismLauncher no.

## 8. Lo que todavía no tiene

Ninguna de estas cosas bloquea construir. Se anotan para no volver a pensarlas.

| | |
|---|---|
| **Recetas** | No se craftean. Son bloques de construcción, y la ciudadela se hace en creativo. Cuando haya que venderlos, van a la tienda ([shop](../economy/)) antes que a una mesa de crafteo |
| **Neón translúcido** | Un «cristal de neón» que deje ver a través necesita capa de renderizado *translucent*, y eso sí es una línea de código de cliente. Cabe, no se ha hecho |
| **Tubos que se conectan en esquina** | Hoy una esquina de tubo se hace con dos tubos. Conectarlos como una valla es más blockstates y bastante más modelo |
| **Colores fuera de los 16** | Se añade una fila a `PALETA` y se regenera. Es barato a propósito |

## Next Actions

1. **Entrar y mirarlos.** Es lo único sin verificar: el brillo y los tres
   niveles de luz (ver *Current Status*)
2. Construir con ellos y ver qué falta de verdad — la lista de §8 es una
   hipótesis hasta que alguien haga una fachada

## Related Systems

- [Construcción](construccion.md) · [Los mundos](worlds.md)
- [El pack de cliente](../technical/client-pack.md)
