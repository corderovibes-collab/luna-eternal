# EL CHARIZARD MECHA: el premio del nivel 100 del Pase de Batalla

## Purpose

Cómo el casco que modeló el usuario en Blockbench acaba puesto sobre un
Charizard shiny **de verdad** —con sus habilidades, sus megas y todo lo que
tiene un Charizard shiny— que **solo se obtiene en el nivel 100 del pase**: qué
se midió antes de tocar nada, qué se decidió y por qué, qué comprueba el
generador, y qué enseñó el visor.

Está escrito para quien tenga que hacer el **segundo** Pokémon con pieza
propia, o retocar este.

## Dependencies

- [../economy/pase-batalla.md](../economy/pase-batalla.md) — el pase, y D-046:
  el nivel 100 da un Charizard variocolor de nivel 50
- [../ui/cosmeticos.md](../ui/cosmeticos.md) — el pack de cosméticos dentro de
  `lunaneon.jar` y por qué los aspectos se **fuerzan** (`forcedAspects`)
- [../ui/espada-pikachu.md](../ui/espada-pikachu.md) — el patrón de herramienta
  (fuentes en el repo, visor, comprobaciones que se niegan a exportar)

## Related Documents

- `tools/mecha/` y `tools/gen_charizard_mecha.py` — el código, con las
  decisiones en sus comentarios
- `arte/charizard-mecha/` — el `.bbmodel` y el PNG del usuario (copias; los
  originales siguen en `D:\blockbench\cascocharizard`) y el `.bbmodel` de vista
- `neon/.../resourcepacks/cosmeticos/assets/cobblemon/{bedrock/pokemon/models,
  bedrock/pokemon/resolvers,textures/pokemon}/luna/` — lo que va al juego

## Current Status

**Generado, comprobado, compilado en los dos jars y SIN DESPLEGAR** (2026-09-10).
`python tools/gen_charizard_mecha.py --generar` lo rehace entero.

⚠ **SIN VERIFICAR EN EL JUEGO.** Lo que sí está verificado: el visor con seis
poses de las animaciones reales (§6), las ocho comprobaciones y la vuelta por
el pack (§7), y el autotest de compilación. Falta entrar, subir a alguien al
nivel 100 (`/luna pase nivel <jugador> 100`), reclamar, mirar, sacar el
Charizard, hacerle abrir la boca en combate, megaevolucionarlo y volver.

---

## 1. El encargo

> *«es el charizard shiny pero con un casco en la cabeza, tiene que ser nuevo
> el pokemon con todo lo mismo de charizard shiny, habilidades etc... es el
> charizard que se va a dar en el pase de batalla... que el casco sí quepa bien
> ... ese charizard solo se da en el pase de batalla, no aparece en el mundo ni
> nada ... si mega evoluciona evoluciona normal y si se quita la mega vuelve a
> quedar con el casco, esto debe de ser único»*

Y a mitad: *«revisa bien que cuando haga una animación no se salga ni cosas
así, esté bien anclado, si abre la boca eso también, necesito algo bien hecho».*

Cuatro condiciones, y **cada una descarta una forma de hacerlo**:

| Condición | Lo que descarta |
|---|---|
| «todo lo mismo de un Charizard shiny» | una **especie nueva** (datapack de especie: Pokédex, cría, aprendizaje, spawns... todo a rehacer y a mantener) |
| «solo se da en el pase, no aparece en el mundo» | una **forma** de Cobblemon (las formas salen en spawns y en la Pokédex) |
| «si mega evoluciona evoluciona normal y al volver queda con el casco» | un modelo que **sustituya** al de la mega |
| «esto debe de ser único» | un **objeto cosmético** (`cosmetic_items`: se craftea y se regala; ver cosmeticos.md) |

Lo que queda es exactamente lo que ya usan los disfraces del PokePad desde el
22-ago: **un aspecto forzado** (`luna_mecha`) que el pack de cosméticos
convierte en modelo y textura. Un Charizard shiny normal, generado con
`charizard level=50 shiny=true`, al que la entrega del pase le añade ese
aspecto. Persiste con el Pokémon, se sincroniza, viaja en el GTS, y **nadie
más lo pone**: la única línea que lo escribe es la del nivel 100.

---

## 2. Lo que había que saber ANTES de tocar nada

Todo medido, nada supuesto. Los cinco puntos de esta sección son los que
decidieron el diseño; el código de `tools/mecha/` los repite en cada pasada.

### 2.1 El cuerpo del `.bbmodel` es el del jar, cubo a cubo

El usuario modeló el casco sobre un `charizardshiny.bbmodel` bajado de
`cobblemon-assets-master`. Se cruzó contra el `charizard.geo.json` **del jar de
Cobblemon 1.8.0 que corre este servidor**: 123 cubos, 130 huesos, 26
localizadores, pivotes, rotaciones y padres **idénticos** (con la regla de
exportación de Blockbench: espejo en X y rotaciones X e Y negadas — la única
regla que cuadra con los 123).

⚠ Importa porque el casco se ajustó **contra esa cabeza**. Si el jar trajera
otro Charizard (pasa: los modelos cambian entre versiones), el casco quedaría
ajustado a una cabeza que ya no existe sin dar ningún error. Es la
comprobación 1, y es la primera que corre.

### 2.2 La mitad de arriba del PNG del usuario es escombro

`charizard_helmet_equipped.png` es 256×256: la mitad de abajo (filas 130–184)
es el casco; la de arriba, donde iría el cuerpo, tiene **1.632 píxeles sueltos
donde el shiny del jar tiene 11.912**. No es su textura del cuerpo: es lo que
quedó de una exportación. Usada tal cual, el Charizard habría salido
**invisible con un casco flotando**.

El cuerpo se pega **del jar**, byte a byte (`charizard_shiny.png` para la
variante shiny y `charizard.png` para la normal). Del PNG del usuario solo
vale el casco.

### 2.3 ⚠⚠⚠ Los 133 cubos son fraccionarios, y Blockbench y el juego reparten el box-UV de forma distinta

Es el hallazgo que manda en el flujo entero.

- **Blockbench 5** reparte el box-UV con los tamaños **redondeados hacia
  arriba** (2,2 → 3; 7,6 → 8). Se ve en los `faces[].uv` de su fichero.
- **Cobblemon 1.8.0** lee `Cube.uv` como `List<Integer>` (solo box-UV; el UV
  por cara no existe para él) y pasa los tamaños **flotantes** a
  `ModelPart$Cuboid`, que reparte con `u + sizeZ`, `u + sizeZ + sizeX`...
  **sin redondear**. Leído en el bytecode del jar de Cobblemon y en el de
  Minecraft 1.21.1, no en el fuente de HEAD.

Con tamaños enteros las dos reglas coinciden, **y por eso nadie lo nota**: los
89 geos cosméticos del pack suman unos 10.000 cubos y **16 son fraccionarios**.
El casco tiene **133 de 133**. En el juego, una cara pintada en las columnas
5..13 se muestrearía en 4,4..12: un texel corrido; la cara de atrás, hasta 1,8.
Sobre placas de tres texels con vetas rojas y remaches de uno, eso es **la
pintura fuera de sitio en todas las piezas** — y el visor lo enseña
(`build/mecha/casco-sin-repintar.png`).

Las salidas que no valen: UV por cara (Cobblemon no lo lee), redondear los
cubos (cambia su diseño), subir la resolución (el box-UV es 1 texel por unidad
pase lo que pase). La que vale: **repintar la textura al reparto del juego**
(§4.2) y entregar un `.bbmodel` de vista con UV por cara para que Blockbench
enseñe lo mismo que el juego (§4.5).

### 2.4 Qué se mueve, y cuánto

De `charizard.animation.json` del jar (34 animaciones), lo que afecta a la
cabeza:

```
jaw          17 animaciones   -12 .. +85,8    (special: la boca del todo)
jaw2          8 animaciones     0 .. +7       (y -7,5 estático)
head_angle   23 animaciones   -21,3 .. +10  (y ±5,7 en Y y Z)
head         29 animaciones   ±34 en X, ±18 en Y, ±22 en Z
neck4        31 animaciones   -11,8 .. +41,4
muzzle · nose · horns · eyes   CERO keyframes
```

De ahí sale a qué hueso va cada pieza (§4.1) y las seis poses del visor (§6).

### 2.5 Cómo resuelve Cobblemon, y la trampa de las megas

`VaryingRenderableResolver` (fuente de Cobblemon): junta **todos** los ficheros
de resolver de la especie ordenados por `order` ascendente, concatena sus
variaciones, y para poser, modelo y textura gana **la última variación que
encaje** (encajar = tener todos sus aspectos). Las capas se funden **por
nombre**, y la última manda.

Lo que ya hay para Charizard: oficial `0`, `cms/0_charizard_adept` `0`,
mega_showdown `1` (mega_y), `2` (mega_x), `3` (gmax), `charizard_knight` `50`.

⚠⚠⚠ El nuestro tiene que ir **por encima de 50** para que un disfraz comprado
no tape el casco — pero entonces, con `{luna_mecha, mega_x}`, nuestra variación
`[luna_mecha]` sería la última con modelo y **volvería a poner el casco encima
de la mega**: un Mega Charizard X con cuerpo de Charizard normal y casco, sin
un solo error. Por eso el resolver **repite** las seis variaciones de las megas
con `luna_mecha` delante, **copiadas del jar de mega_showdown** al generar, no
escritas a mano. Es el mismo truco que `charizard_knight` hace en
`msd/100_charizard_knight_mega_x.json`.

⚠⚠ **Y las megas no tocan los aspectos forzados.** Se buscó en las 400 clases
de mega_showdown: la única que menciona `forcedAspects` es un mixin de
**cliente** que copia los aspectos de la entidad al Pokémon de la pantalla. Las
formas se aplican con `FlagSpeciesFeature` / `StringSpeciesFeature`
(`AspectUtils.applyAspects`, bytecode), o sea por *features*, y
`updateAspects()` hace *aspectos = proveedores + forzados*. Megaevolucionar
**añade** `mega_x`; revertir lo **quita**; `luna_mecha` no se mueve. **De ahí
sale «si se quita la mega vuelve a quedar con el casco»** sin escribir una
línea. Y `CosmeticsService` conserva lo que no sea del catálogo, así que
ponerle un disfraz tampoco lo quita (el autotest exige que ningún cosmético
use este aspecto).

---

## 3. El flujo

```
arte/charizard-mecha/charizard_helmet_equipped.bbmodel  (el casco del usuario)
jar de Cobblemon 1.8.0     (geo, resolver, animaciones, texturas)   } del manifiesto
jar de mega_showdown       (sus tres resolvers de Charizard)        } publicado
        │
        ▼  tools/gen_charizard_mecha.py
  fuentes → ensamblar (geo) → texturas (repintado) → resolver → comprobar → visor
        │                                                            │
        ▼  --generar                                                 ▼  siempre
  neon/.../cosmeticos/assets/cobblemon/.../luna/*            build/mecha/*.png
  arte/charizard-mecha/charizard_luna_mecha.bbmodel  (vista)
```

```
python tools/gen_charizard_mecha.py --ver         láminas, no escribe en el pack
python tools/gen_charizard_mecha.py --generar     geo + texturas + resolver + .bbmodel
python tools/gen_charizard_mecha.py --verificar   relee el pack y lo cruza
```

⚠ Los jars se bajan **del manifiesto publicado** (`gen_tienda.jar_de`, que ganó
un parámetro `prefijo` para esto) y se cachean por huella. Nada depende de
`vendor/` (que es HEAD) ni de Descargas.

---

## 4. Cómo está hecho

### 4.1 El geo: el oficial tal cual, más cuatro huesos

El `charizard.geo.json` del jar **no se toca**: se copia y se le añaden
cuatro huesos al final, cada uno colgado del hueso oficial **que se mueve con
esa pieza**. El `.bbmodel` del usuario lo traía **todo** bajo `head_angle`; con
eso la barbilla se quedaría clavada mientras la mandíbula se abre 85,8° por
debajo.

| Hueso nuestro | Cuelga de | Piezas | Por qué |
|---|---|---|---|
| `luna_casco` (90) | `head_angle` | gafas, placa craneal, escapes de los cuernos, sensores, mejillas, placa de la garganta | envuelven los dos cubos de `head_angle`; los cuernos no animan |
| `luna_hocico` (27) | `muzzle` | el respirador del hocico | envuelve el cubo del `muzzle` (mandíbula superior) |
| `luna_menton` (7) | `jaw2` | las siete `Chin_*` | envuelven `jaw2` (48,5–52,5, z −4..−2): la punta de la mandíbula inferior |
| `luna_gola` (9) | `neck4` | la gola | rodea el final de `neck4`, entre su pivote y el de la cabeza |

⚠⚠⚠ **El mentón cuelga de `jaw2` con la rotación inversa a la estática de
`jaw2`** (`-7,5` en el jar → `+7,5` en el nuestro). El usuario modeló la
barbilla **recta** alrededor de la mandíbula **ya girada** que le enseñaba
Blockbench. Colgada sin más, el juego le sumaría esos 7,5° y saldría hundida
en el labio; colgada de `jaw` (que no tiene estática) se quedaría atrás en las
ocho animaciones donde `jaw2` añade hasta 7° propios. Con la inversa, en
reposo cae **exactamente donde la puso** y en movimiento suma solo lo que
anime `jaw2`. Vale porque `jaw2` gira en **un solo eje** — se comprueba, y si
un día animara otro eje se pondría rojo.

⚠ Los escapes de los cuernos van rectos en `head_angle` y no en `horn_right`
/`horn_left`: los cuernos tienen rotación estática y **cero** animaciones, así
que da lo mismo dónde cuelguen y en `head_angle` quedan donde el usuario los
vio. Si un día el jar animara los cuernos, la comprobación 6 lo dice.

Los cubos llevan `name` (el que les puso el usuario). Bedrock no lo usa y
Cobblemon lo ignora (Gson descarta campos que `Cube` no tiene); hace legible
el fichero y permite casar los cubos por nombre en la verificación.

`texture_height` pasa de 128 a 256. Las UV del cuerpo son absolutas y no se
mueven; lo que obliga es a rellenar las capas (§4.3).

### 4.2 Las texturas: el cuerpo del jar y el casco repintado

Dos PNG de 256×256: `charizard_luna_mecha.png` (cuerpo normal del jar + casco)
y `charizard_luna_mecha_shiny.png` (shiny del jar + casco). El pase entrega
shiny; la normal existe para que el aspecto tenga sentido también si algún
día se fuerza sobre uno que no lo sea.

**El repintado**, texel a texel, por cada uno de los 133 cubos: cada texel
pertenece a la cara del juego (reparto flotante) que contiene **su centro**, y
toma el color de la posición **relativa** equivalente dentro de la casilla
que el usuario pintó (reparto redondeado). Un texel fuera de todas las caras
del juego —el margen que sobra al redondear— queda transparente: nadie lo
muestrea. Resultado: **1.907 texels movidos, 705 en su sitio**.

Lo que no puede arreglar: en una frontera fraccionaria (4,4) el texel 4 es de
una sola cara y la vecina le roba 0,4 de texel. Es el precio de un modelo
fraccionario con box-UV — el mismo que pagaría en Bedrock.

⚠ Si el casco se remodela con tamaños enteros, el repintado es la identidad
(0 movidos) y sobra. Es la salida limpia, y es del usuario.

### 4.3 Las capas: rellenadas a 256×256

Una capa (`flame`, `alpha_eyes`) se dibuja con las UV del geo. Las del jar
miden 256×128; sobre un geo de 256 de alto se dibujarían **encogidas a la
mitad**, con la llama de la cola en el cuerpo. Se rellenan con transparente
por abajo — lo mismo que hace `charizard_knight`, cuyas llamas ya son 256×256.

⚠ El shiny lleva **la llama normal** (`charizard_flame1-4`), porque es la que
el resolver oficial de 1.8.0 le da al shiny: las `charizard_shiny_flame*` viajan
en el jar y **no las usa nadie**. No se decidió; se copió del jar.

### 4.4 El resolver: `60_charizard_luna_mecha.json`

Nueve variaciones, en este orden (gana la última):

```
[luna_mecha]                poser cobblemon:charizard · modelo nuestro · textura normal · llama
[luna_mecha, shiny]         textura shiny · llama
[luna_mecha, alpha_eyes]    capa alpha_eyes rellenada
[luna_mecha, mega_y]        } copiadas de mega_showdown, campo a campo
[luna_mecha, mega_y, shiny] }   (poser charizard_mega_y, su geo, su textura,
[luna_mecha, mega_x]        }    sus capas eyes/flame)
[luna_mecha, mega_x, shiny] }
[luna_mecha, gmax]          }
[luna_mecha, gmax, shiny]   }
```

Las tres primeras **se derivan** del resolver oficial (cada variación suya
pasa a tener `luna_mecha` delante, nuestro modelo, nuestra textura y sus capas
rellenadas), así que si Cobblemon añade una variación base, regenerar la
recoge.

⚠⚠ **Esto ata el pack a mega_showdown**: Cobblemon carga al arrancar todos
los modelos que nombre cualquier variación (`initialize` →
`texturedModels[id]!!`), así que sin mega_showdown en el cliente **el
resolver de Charizard entero reventaría al cargar recursos**. Hoy viaja en
todos los clientes (base CobbleVerse, D-037) y el generador comprueba que cada
modelo, poser y textura nombrados existen en nuestro pack o en uno de los dos
jars. Si mega_showdown se fuera, este resolver tendría que perder sus seis
últimas variaciones el mismo día.

### 4.5 El `.bbmodel` de vista

`arte/charizard-mecha/charizard_luna_mecha.bbmodel`: el geo final convertido a
Blockbench 5 (regla inversa a la de exportación), con el cuerpo en box-UV y
**el casco con UV por cara con las casillas exactas del juego**, sobre la
textura repintada incrustada. Abrirlo enseña lo que dibuja el juego, texel a
texel. Las casillas de `up`/`down` van invertidas como las escribe Blockbench
(copiado de su fichero: `up` con u y v al revés, `down` con u al revés).

⚠ **No sustituye al fichero del usuario.** Si quiere cambiar el casco, cambia
el suyo y se regenera; este es una vista.

### 4.6 Java

- `Recompensa` gana el componente `aspecto` y la constante `ASPECTO_MECHA`
  (`"luna_mecha"`), con `pokemonUnico(...)` y `aspectos()` (lo que se dibuja:
  `shiny` + el forzado). `propiedades()` **no cambia**: `PokemonProperties.parse`
  no sabe de aspectos forzados.
- `PaseCatalogo`: el nivel 100 es
  `pokemonUnico("charizard", 50, true, ASPECTO_MECHA, LEGENDARIA)`. Única fila
  con aspecto.
- `Red.entregarPokemonDelPase`: tras `create()`, `setForcedAspects` con el
  patrón de `CosmeticsService` (conservando lo que hubiera) y `updateAspects()`,
  **antes** de meterlo en el equipo, para que la primera sincronización ya
  lleve el casco.
- `Mascota3D.dibujarEspecie` gana la sobrecarga con `Set<String>`: la tarjeta
  del nivel 100 se dibuja con `shiny` **y** `luna_mecha`; con uno solo saldría
  el shiny sin casco o el casco sin shiny.
- Autotest, en `testPase` (+12): el 100 lleva el aspecto y es el **único**;
  sus propiedades siguen siendo `charizard level=50 shiny=true`; el resolver
  está en el pack (se lee **desde el jar de lunaneon**, mismo classloader),
  habla del mismo aspecto, repite las tres megas y tiene `order` > 50; el geo,
  la textura shiny y la llama existen; y **ningún cosmético usa el aspecto**.

---

## 5. Qué cambió por el camino, y por qué

- Lo primero fue meter los cubos del casco **dentro** de los huesos oficiales
  (`head_angle.cubes += ...`). Se cambió a huesos nuevos con el pivote del
  padre: deja los cubos oficiales intactos (la comprobación «cuerpo == jar»
  sigue valiendo sobre el geo final), y es lo único que permite la rotación
  inversa del mentón.
- El mentón iba a `jaw`. Se movió a `jaw2` con la inversa al medir que `jaw2`
  anima hasta 7° propios en ocho animaciones (§4.1).
- La comprobación «nada flota» exigía que **cada cubo** tocara la cabeza, y
  ponía en rojo los cuatro últimos eslabones de los escapes (a 1,0–2,0 de la
  cabeza) estando soldados al anterior. Hoy exige que cada **racimo de cubos
  conectados** toque la cabeza: lo que de verdad es un fallo es una pieza que
  no toque nada.
- El primer `.bbmodel` de vista asignaba la primera casilla del box-UV a la
  cara `west`. Es `east`: Blockbench enseña el geo espejado en X, y su propio
  fichero lo dice (el `torso` del usuario lleva `u..u+d` en `east`). Se
  comprobó rehaciendo sus caras y comparándolas con las suyas: idénticas.

---

## 6. Lo que enseñó el visor

`build/mecha/`: `charizard.png` (el cuerpo entero, pose idle), `casco.png` (la
cabeza de cerca), `casco-huesos.png` (cada hueso de un color),
`casco-sin-repintar.png` (la prueba de §2.3) y `casco-poses.png`.

Las seis poses son estados por los que el juego pasa **de verdad** (el término
constante de las expresiones del jar, o el extremo de sus keyframes): idle,
combate (boca a 17,5 permanente), **boca abierta a 85,8 con `jaw2` a 7**,
cabeza arriba (−34/−21), cabeza abajo (+33/+15/+10 con el cuello a 41), y
mirando de lado (los máximos de `q.look`).

- **La boca abierta**: el mentón baja con `jaw2`, envolviendo su cara frontal;
  la lengua queda entre el respirador y el mentón; la placa de la garganta
  (en `head_angle`) no se separa. Es la lámina que responde a «si abre la
  boca eso también».
- **Los escapes de los cuernos** no envuelven los cuernos: van rectos hacia
  atrás desde la nuca y los cuernos oficiales asoman por debajo, hacia
  arriba y afuera. Es como el usuario lo modeló mirando el modelo con los
  cuernos ya girados en Blockbench; se deja como está y se dice.
- **Cabeza arriba, abajo y de lado**: todo lo de `head_angle` se mueve en
  bloque; la gola se queda con el cuello y la cabeza gira dentro del aro, que
  es lo que hace el cubo oficial de `neck4` con el de la cabeza (los modelos de
  Cobblemon solapan las articulaciones a propósito).

⚠ El visor dibuja **como el juego** (marco del geo, espejado en X al final,
como `LivingEntityRenderer` con su `scale(-1,-1,1)`) y muestrea la textura
**con el reparto del juego**. Sin eso no habría forma de ver la pintura
corrida de §2.3 sin entrar.

---

## 7. Las comprobaciones

Con un solo fallo **no se exporta**; con `--verificar` se relee lo escrito.

1. **Cuerpo == jar** — cubos, pivotes, rotaciones y padres de los 130 huesos
2. **133 cubos, todos con hueso** — y ninguno con rotación, inflate ni
   `mirror_uv` (saldría recto y sin ellos), ni con UV por cara (Cobblemon no lo lee)
3. **UV dentro y sin pisar** — ninguna casilla fuera de 256×256, sobre el
   cuerpo, ni sobre otra pieza
4. **Todas las caras pintadas** — ni un texel transparente dentro de una cara
   del juego en la textura final, ni en las de Blockbench en el PNG del usuario
5. **Nada flota** — cada racimo de cubos conectados toca (a 0,5) la cabeza en reposo
6. **El mentón gira en un eje** — y los cuernos siguen sin animar
7. **Resolver completo** — `[luna_mecha]` y `[luna_mecha, shiny]` con lo suyo;
   las seis megas **iguales campo a campo** a las de mega_showdown; cada
   modelo, poser y textura nombrados existen
8. **El aspecto está en Java** — `"luna_mecha"` escrito en `Recompensa.java`

Y la vuelta por el pack: geo y resolver releídos iguales, las 7 texturas
píxel a píxel, y el `.bbmodel` de vista con los 133 cubos por nombre y sus
UV iguales a las del juego.

---

## 8. Lo que falta y lo que hay que saber

- **Desplegar, y en este orden**: `python tools/gen_manifest.py --publicar`
  (lunaneon nuevo a los clientes) **y después** `python tools/desplegar.py mod
  --reiniciar`. Nada de esto registra bloques ni objetos, así que un cliente
  viejo entra igual: solo vería un shiny sin casco hasta reabrir el launcher.
  ⚠ Avisar antes de reiniciar.
- **Verificar en el juego** (§Current Status). Lo primero que mirar: que en la
  tarjeta del nivel 100 del PokePad salga el casco (es lo más barato de
  comprobar), después la entrega, el mundo, el combate y la mega.
- **El pack depende de mega_showdown** (§4.4).
- **El casco es fraccionario** (§2.3). Si el usuario lo remodela, mejor en
  tamaños enteros; mientras, el repintado lo cubre.
- El geo deriva del `charizard.geo.json` de Cobblemon, cuya carpeta lleva una
  licencia Creative Commons (`models/0006_charizard/license` en el jar). Es lo
  mismo que ya hace `charizard_knight` en el pack; las licencias las lleva el
  usuario.
- El aspecto no sale en ningún sitio más: ni comandos, ni tienda, ni eventos.
  Si algún día se quiere dar fuera del pase, la única vía es `setForcedAspects`
  con `Recompensa.ASPECTO_MECHA` — y entonces deja de ser único.
