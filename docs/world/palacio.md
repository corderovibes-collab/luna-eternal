# El Palacio Lunar

## Purpose

El edificio central de la ciudadela: un palacio flotante con **un ala por
región**, donde se exhiben y se combaten los entrenadores. Este documento es el
**boceto y la auditoría**; el generador es `tools/palacio.lua`.

## Dependencies

- [`construccion.md`](construccion.md) — cómo se construye en la ciudadela
- [`bloques.md`](bloques.md) — las 6 familias de bloques propios (602 piezas)
- [`../pokemon/generations.md`](../pokemon/generations.md) — **qué regiones están
  activas**, que es lo que decide qué alas se encienden

## Related Documents

- [`neon.md`](neon.md) · [`world-structure.md`](world-structure.md)

## Current Status

**GENERADOR ESCRITO Y VERIFICADO** (2026-08-22). `tools/palacio.lua`, 5 003
líneas, script Lua para el **Lua Script Brush** de Axiom. Solo **estructura**:
las 145 plataformas se dejan vacías y señalizadas. Los entrenadores se colocan
después (decisión del usuario).

Comprobado con un banco de pruebas que simula la API de Axiom: sintaxis válida
(`luac -p`), ejecución completa sin fallos y **0 bloques sin resolver** en los
tres presets. ⚠️ **Sin ejecutar en el juego todavía** — el banco verifica
geometría, no si queda bonito.

## Last Decision

El palacio **encarna físicamente el despliegue de generaciones**: las alas de
las regiones que aún no han llegado están construidas pero selladas y a oscuras.

---

## 1. La idea en una frase

> **Un decágono flotante sobre el vacío, con la luna encima y diez alas que se
> encienden una a una según el mundo va creciendo.**

No es el palacio de piedra europeo de las referencias. Es **su relectura
lunar**: la misma monumentalidad —simetría radial, columnata, cúpula, escalinata
de acceso— pero levantada en hormigón pulido, cromo, vidrio polarizado y neón,
que es la paleta que ya define la ciudadela.

### Por qué un decágono y no una cruz o un rectángulo

Porque hay **diez regiones**, y la planta debe poder decir eso de un vistazo.
Un visitante que entra ve inmediatamente cuántos mundos hay y cuántos faltan.
La geometría **es** la información.

---

## 2. Planta

```
                            N
                    +===============+
              +=====+     UNOVA     +=====+
        +=====+  KALOS         JOHTO *    +=====+
      +=+                                       +=+
      |  ALOLA          (@) NUCLEO       KANTO * |
      +=+                 la luna                +=+
        +=====+  GALAR          HOENN o    +=====+
              +=====+    PALDEA     +=====+
                    +=== HISUI o ===+
                            S

        *  activa      o  latente      (sin marca) reservada
```

| Anillo | Radio | Qué hay |
|---|---|---|
| Pozo | 0 – 12 | Pozo de luz, canal de agua y la escalera a la corona |
| Núcleo | 12 – 36 | La rotonda y sus diez portales |
| Alas | 38 – 87 | Las diez galerías regionales |
| Ambulatorio | 91 – 98 | El anillo que las une todas |
| Perímetro | 100 – 104 | Fachada, torres y contrafuertes |

**Planta: 215 × 221 bloques. 566 045 bloques.** No cabe en la plaza de 56×56, y
no importa: **vuela sobre el vacío**. La ciudadela no tiene terreno que
respetar, y eso —que en cualquier otro servidor sería un problema— aquí es la
mejor baza estética. Lo que sí hace falta es **un paseo de 86 bloques** que una
la plaza con el pie de la escalinata: sin él no se llega andando.

### Tres escalas

| Preset | Bloques | Planta | Alto |
|---|---|---|---|
| `maqueta` | 350 624 | 151 × 157 | 80 |
| `normal` | 566 045 | 215 × 221 | 121 |
| `epico` | 865 149 | 279 × 285 | 191 |

---

## 3. Sección vertical

```
   y=150   o-----------   LA LUNA         esfera de radio 21, suspendida
              \   /                        vidrio polarizado + neon blanco
   y=124   ====+=+====    CORONA          el Alto Mando: 10 tronos en anillo
               | |
   y= 96   ====+=+====    CUPULA          casquete sobre la rotonda
               | |
   y= 74   ##########     PLANTA NOBLE    las 10 alas + la rotonda
           ==========
   y= 70   ##########     BASAMENTO       cornisa, contrafuertes, faros
               | |
   y= 64   ----------     plaza existente + escalinata de acceso
               | |
   y= 48   ==========     LA CRIPTA       los equipos: Rocket, Galactic,
                                           Aqua, Magma y Giovanni
```

Aprovecha el rango completo de construcción de la dimensión (−64 a 319) y la
**noche permanente** (`fixed_time 18000`, `ambient_light 0.45`): un edificio de
neón sobre fondo negro no se ve así a mediodía.

---

## 4. El ala regional — la pieza que se repite diez veces

```
   desde el nucleo ------------------------------------>  hacia fuera

   +----+   +-----+-----+-----+-----+          +===========+
   |    |   |  1  |  3  |  5  |  7  |          |           |
   | ## |===+=====+=====+=====+=====+==========|  ESTRADO  |
   |    |   |  2  |  4  |  6  |  8  |          |  campeon  |
   +----+   +-----+-----+-----+-----+          +===========+
    arco        8 alcobas de lider               dais elevado
   de entrada    (5x5 cada una)                    (11x9)

   ancho del ala: 18      largo: 44
```

**Nueve plataformas por ala**, que es exactamente lo que pide el reparto real de
entrenadores del datapack RCT: 8 líderes de gimnasio y 1 campeón. El Alto Mando
sube a la corona, porque en los juegos tampoco está en el mismo sitio.

Cada alcoba lleva: suelo del color de la región, un friso de neón a media
altura, dos columnas de cromo enmarcando y **el hueco de la plataforma vacío y
marcado** — es donde irá el entrenador.

> **Solo estructura.** El script no coloca ni un entrenador: deja las 105
> plataformas construidas, numeradas y vacías. Ponerlos es un paso posterior y
> manual, por decisión del usuario.

---

## 5. Los tres estados de un ala, y por qué esto importa

Esta es la parte del diseño que no es decorativa.

| Estado | Regiones | Aspecto |
|---|---|---|
| **ACTIVA** | Kanto · Johto | Neón a `luz=2`, ala abierta, suelo del color pleno |
| **LATENTE** | Hoenn · Sinnoh · Hisui | Neón a `luz=0` —**brilla pero no ilumina**—, sellada con vidrio polarizado, cartel «Aún no ha llegado a este mundo» |
| **RESERVADA** | Unova · Kalos · Alola · Galar · Paldea | Obra vista: grafito y acero, sin color, sin neón, sellada |

**La distinción latente/reservada no es un capricho:** «latente» son las
regiones cuyos entrenadores **ya existen** en el datapack RCT (Hoenn 16,
Sinnoh 16, Hisui 2) y solo esperan a que se active la generación. «Reservada»
son las que **no tienen ni contenido** (0 entrenadores). Un constructor que
mire el palacio sabe de un vistazo qué falta por activar y qué falta por
producir.

> **Y recupera algo que se perdió.** [`generations.md`](../pokemon/generations.md)
> §4 diseñaba una Pokédex que enseñaba lo bloqueado con candado —*«Aún no ha
> llegado a este mundo»*— para convertir la limitación en **promesa**. Eso no se
> pudo hacer: la Pokédex de Cobblemon no sabe marcar una entrada como bloqueada
> desde un datapack, así que las regiones inactivas se vaciaron y punto. **El
> palacio sí puede decirlo**, y con mucha más fuerza que una lista: son cinco
> alas construidas y a oscuras que el jugador atraviesa cada vez que entra.

---

## 6. Paleta por región

Los colores salen de los juegos, no de un gusto:

| Región | Color | De dónde | Entrenadores en RCT |
|---|---|---|---|
| Kanto | `rojo` | Red | 14 |
| Johto | `amarillo` | Gold | 14 |
| Hoenn | `verde` | Emerald | 16 |
| Sinnoh | `azul_claro` | Diamond | 16 |
| Unova | `gris_claro` | Black · White | 0 |
| Kalos | `azul` | X · Y | 0 |
| Alola | `naranja` | Sun · Moon | 0 |
| Galar | `morado` | Sword · Shield | 0 |
| Hisui | `lima` | Legends: Arceus | 2 |
| Paldea | `magenta` | Scarlet · Violet | 0 |

Y encajan por construcción: los 16 colores del hormigón y del vidrio **son los
16 del neón rebajados por fórmula** (D-032), así que un neón rojo pega con un
hormigón rojo sin que nadie los haya emparejado a ojo.

### Materiales estructurales

| Uso | Bloque |
|---|---|
| Estructura y muros | `hormigon_pulido_blanco` · `hormigon_panel_gris_claro` |
| Columnas | `metal_cromo_liso_pilar` · `metal_titanio_cepillado` |
| Vigas y cornisas | `metal_acero_oscuro_estriado` |
| Suelos | `pavimento_losa_grande` · `pavimento_terrazo_claro` |
| Celosías y barandillas | `rejilla_cromo` · `rejilla_titanio` |
| Ventanales | `vidrio_polarizado_<color>` |
| Obra vista (reservadas) | `metal_grafito_remachado` |

---

## 7. Auditoría técnica

### CURRENT STATE

- La ciudadela es **una isla de 56×56** flotando en el vacío (plaza central),
  suelo `y=63`, se camina en 64. Rango de construcción −64 a 319.
- `tools/ciudadela.py` ya replantea 9 parcelas y manda comandos por RCON.
- Existen **602 bloques propios** (`lunaneon`) en 6 familias.
- El datapack RCT trae **456 ficheros** de entrenador; los nombres llevan la
  región delante, así que el reparto por ala es dato, no invención.

### PROBLEM

Construir a mano un edificio radial de 156 de diámetro con simetría de orden 10
es inviable: cada ala son ~40 primitivas y un error de un bloque en el ángulo se
propaga hasta el perímetro, donde ya no cuadra nada.

### OPTIONS

| | Vía | Veredicto |
|---|---|---|
| A | A mano con Axiom | Inviable a esta escala, y no versionable |
| B | Blueprint `.bp` de Axiom | Binario: no se revisa en un diff ni se parametriza |
| C | **Script que emite comandos** | ✅ Es lo que ya hace `ciudadela.py`, y sale gratis |

> ⚠️ **Axiom SÍ tiene scripting, y es Lua — no está en su documentación de
> comandos.** `axiomdocs` solo lista seis comandos y unas gamerules, así que
> buscarlo ahí da un falso negativo. La facilidad está **en la interfaz**: la
> herramienta **Lua Script Brush**, con esta API:
>
> ```
>   x, y, z                      coordenadas del punto
>   setBlock(x, y, z, block)     coloca un bloque en cualquier sitio
>   getBlock / getBlockState     lee lo que ya hay
>   withBlockProperty(b, "k=v")  orienta escaleras, losas, pilares, luz del neon
>   blocks.<id>                  referencia a un bloque
>   getSimplexNoise / getVoronoiEdgeNoise      ruido, para texturas y desgaste
>   findClosestBlockToRGB(rgb)   elige el bloque mas parecido a un color
>   player_x/y/z, player_yaw     donde estaba el jugador al ejecutar
>   $once$                       ejecuta UNA vez en un punto, no como brocha
>   $ignoreMask$                 ignora mascara y seleccion
> ```

### RECOMMENDED SOLUTION

**Opción C, como script Lua de Axiom** (`tools/palacio.lua`), con `$once$`.

Es mejor que emitir comandos, y por tres motivos medidos:

| | Comandos `/fill` | Lua de Axiom |
|---|---|---|
| Volumen | decenas de miles de comandos, por lotes y con pausa | **una ejecución** |
| Límite de 32 768 bloques | hay que trocear a mano | no existe |
| Orientar una escalera | otro comando con `[facing=...]` | `withBlockProperty` |
| Deshacer | reconstruir a la inversa | **Ctrl+Z de Axiom** |
| Carga del servidor | la aguanta el servidor | la aguanta **el cliente** |

Ese último punto es el que decide: el servidor de desarrollo tiene 8 GB y ya va
al 54 % con el mundo vacío. Construir desde el cliente no le cuesta nada.

### RISKS

| Riesgo | Mitigación |
|---|---|
| `/fill` corta en **32 768 bloques** y el error («Too many blocks») no dice dónde | Trocear siempre; la misma regla que ya usa `ciudadela.py` |
| Decenas de miles de comandos tumban el servidor si van de golpe | Salida a fichero + envío por lotes con pausa |
| Un bloque mal escrito no da error: **simplemente no aparece** | `--verificar` comprueba cada id contra los 602 blockstates |
| Reconstruir encima deja restos de la versión anterior | `--limpiar` vacía el volumen completo antes |
| 29 330 estados de bloque ya en el registro | No añade bloques nuevos: solo usa los que hay |

### TEST PLAN — y lo que encontró

Se escribió un banco de pruebas que simula `setBlock`, `getBlock`, `blocks`,
`withBlockProperty` y los dos generadores de ruido, y ejecuta el script entero
fuera del juego contando bloques y midiendo la caja envolvente.

**Cazó seis fallos reales**, todos del mismo tipo —piezas diseñadas por
separado que se pisan— y ninguno de los cuales da error:

| # | Fallo | Cómo se vería en el juego |
|---|---|---|
| 1 | Diez alas de ancho 18 **no caben** en un núcleo de radio 28 (17,6 de arco para 22 necesarios) | Muros de Kanto mezclados con los de Johto |
| 2 | La luna a `y=132` **atravesaba** la corona a `y=112` | Diez tronos dentro de la esfera |
| 3 | `setBlock` recibía coordenadas **fraccionarias** (16.954915…) en nueve de las diez alas | Comportamiento no documentado de Axiom |
| 4 | El preset `maqueta` repetía el fallo 1 | Igual que 1, en pequeño |
| 5 | El ambulatorio **atravesaba** las alas (radio 82-89 contra 82-86) | El anillo cortando los diez estrados |
| 6 | La escalinata arrancaba a **86 bloques de vacío** de la plaza | **No se podía llegar al palacio** |

Los tres últimos son de recorrido y aparecieron al recorrer el edificio
mentalmente: el ambulatorio sin pasos, la corona sin escalera y el palacio sin
acceso desde la plaza. Están los tres resueltos.

**Lo que el banco NO comprueba:** si queda bonito. Las proporciones están
elegidas sobre plano. Por eso el paso 2 de la guía es ejecutar en `maqueta`,
mirarlo, y solo entonces lanzar el tamaño normal.

---

## Next Actions

1. Ejecutar `--solo nucleo` y juzgar la escala **en el juego**
2. Colocar los entrenadores en las 105 plataformas (queda para después)
3. Decidir si la cripta de equipos entra en la primera versión
4. Cuando se active Hoenn: `--estado hoenn=activa` y regenerar esa ala
