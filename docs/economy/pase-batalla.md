# El Pase de Batalla Luna

## Purpose

Cómo funciona el **Pase de Batalla** (D-046, revoca D-045): una temporada de
60 días con **100 niveles** que se suben **jugando** —capturando, minando,
pescando, criando, registrando la Pokédex, ganando medallas y escalando la
Torre— y **una sola vía, de pago**: 1.500 LunaCoins.

Cada nivel da un objeto de Cobblemon, organizado en **cinco tramos por
categoría**, y **dos niveles dan un Pokémon**: el 1 un Charizard de nivel 15 y el 100,
el premio mayor, **un Charizard variocolor de nivel 50**.

Este documento es el sitio donde vive la calibración. Los números están
justificados uno a uno; cambiarlos es cambiar constantes en dos ficheros.

## Dependencies

- [`../../CLAUDE.md`](../../CLAUDE.md) — D-007 (F2P + paquetes), D-013/D-014
  (las monedas no se convierten), D-039 (los cosméticos no se juegan), P3
  (sinks antes que sources), P6 (el servidor manda), P9 (interfaz, nunca comando)
- [`monetization.md`](monetization.md) — los cuatro niveles y el test de §6.
  **Es lo que decide qué puede llevar cada vía**
- [`treasures.md`](treasures.md) §4.1 — toda llave se tiene que poder conseguir
  jugando
- [`../ui/dibujado.md`](../ui/dibujado.md) — las seis reglas de la pantalla
- [`../world/torre-batalla.md`](../world/torre-batalla.md) — la Torre da XP del pase

Código:

```
mod/src/main/java/net/pokereport/luna/pase/
    PaseNivel.java      la curva y el tope. FUNCION PURA
    PaseXp.java         cuanto da cada accion
    PaseCatalogo.java   que lleva cada nivel en cada via
    Recompensa.java     el tipo de premio
    PaseService.java    temporada, XP, compra y cobro (base de datos)
    Pase.java           la puerta por la que entra toda la XP
mod/src/client/java/.../pokepad/PaseScreen.java
mod/src/main/resources/db/migration/V032__pase.sql
```

## Current Status

**DESPLEGADO Y EN VIVO** (2026-09-08, 11:16). Reescrito entero por **D-046** y
recalibrado el mismo día por **D-047**. La versión de la mañana (dos vías, 50
niveles) estuvo en vivo unas horas; ésta la sustituye.

```
servidor   V034 aplicada · Done (29,880 s) · AUTOTEST 636/636
clientes   manifiesto c36257eb74 publicado y sirviéndose
/luna pase 100 niveles · 53.700 XP · tope 6.000/día · mínimo 9 días
           Precio 1.500 LunaCoins · 2 Pokémon
```

- 100 niveles, curva y tope recalibrados. **9 días mínimos** de 60 (eran 45 hasta el 2026-09-08: ver §1).
- Los 95 objetos y las 2 especies **validados contra el jar** antes de
  escribirlos, y revalidados contra el registro por el autotest.
- Pantalla rehecha: una fila de cuatro tarjetas de 180x336, texto de 13 a 46 px,
  halos, brillos, marcos vivos, chispas y mini-mapa de los cien niveles.
- Icono definitivo instalado el 2026-09-08.
- **D-047**: precio 15.000 → **1.500**, el 50 y el 98 pagan **100 LunaCoins**
  cada uno (§3.3), y la XP por mena baja de 4 a **1** (§2).

**SIN VERIFICAR VISUALMENTE**: nadie ha abierto la pantalla nueva todavía.

⚠ **Y hay dos cosas sin recalibrar que se parecen a lo que ya falló**: las tasas
de **cosechar** y **pescar** (§2). Están fuera del invariante a propósito.

## Last Decision

**D-047** — El pase baja a **1.500 LunaCoins**, devuelve **100 en el nivel 50 y
100 en el 98**, y la XP por **mena** baja de 4 a **1**. Las tres son órdenes del
usuario; la de la mena vino con el diagnóstico dentro (*«eso es muy roto»*) y
tenía razón: la tabla de §2 llevaba una estimación falsa.

**D-046** — Una sola vía, de pago, con objetos de Cobblemon y Pokémon.
**Revoca D-045**, que decía «solo cosméticos».

> ⚠⚠⚠ **QUEDA ESCRITO QUE ESTO CRUZA T4, y no para volver a discutirlo.**
> [`monetization.md`](monetization.md) §2 pone **shinies** y **objetos
> competitivos exclusivos** en la lista de «nunca, bajo ninguna circunstancia»,
> y el test de §6 se para en la **primera** pregunta. La decisión se tomó **a
> sabiendas** —igual que D-020 con los legendarios de los cofres— y es del
> usuario: *«este pase de batalla si o si es comprando lunacoins... debe tener
> buenos items competitivos para crianza y cositas asi»*.
>
> **El riesgo que queda vivo no es interno**: son las reglas comerciales de
> Mojang (`B-008`, `SEC-004`), que prohíben vender ventaja de juego. Está sin
> verificar desde el 2026-08-11.
>
> ⚠ **Lo único que se ha dejado fuera es la MONEDA.** El pase no da ni un
> PokéDólar: un objeto entra en la economía de objetos, pero moneda vendida por
> dinero real **saltea todos los sumideros a la vez** (P3), y esa es la única
> avería de esta lista que no se arregla bajando un número.

---

## 1. La cuenta que sostiene todo el diseño

El usuario pidió tres cosas: *que se suba con todo lo que se puede hacer en
Cobblemon*, *que esté bien calibrado matemáticamente* y **que no se consiga todo
rápido**. Las tres se resuelven con dos números:

```
curva      coste(n) = 240 + 6n      para n = 0..99   (100 niveles)
           total    = 53.700 XP

tope       6.000 XP de pase al día      (era 1.200 hasta el 2026-09-08)
           ──────────────────────────────────────────
           53.700 / 6.000 =  8,95  ->   9 DIAS COMO MINIMO
```

### ⚠⚠⚠ El mínimo bajó de 45 días a 9, y hay que decir lo que eso hace y lo que no

**Orden del usuario (2026-09-08):** *«vamos a subirle el límite a 6000 así pueden
farmear rápido el pase de batalla»*. El tope diario pasa de 1.200 a **6.000**.

**Lo que hace:** retira a propósito la propiedad que D-045 llamaba *«lo que
impide que se complete en una semana»*. No es una regresión, es una decisión —
igual que D-046 retiró la vía gratuita.

**Lo que NO hace, y es lo que importa:** *subir el tope no acelera a nadie por sí
solo*, porque **el tope no era el límite de casi nadie**. Medido contra la tabla
de §2, una hora seguida de cada actividad paga:

| actividad | XP/hora | horas para llenar 6.000 |
|---|---|---|
| Torre de Batalla (ronda ~20) | 1.000 | 6,0 |
| cosechar | 600 | 10,0 |
| minar | 580 | 10,3 |
| pescar | 560 | 10,7 |
| capturar | 300 | 20,0 |
| ganar combates | 180 | 33,3 |

> ⚠⚠ Con el tope en 1.200, **dos horas de mina lo llenaban**. Con 6.000 harían
> falta **diez horas seguidas**. O sea que el techo ya no lo toca nadie y **quien
> manda ahora es la tabla de fuentes de §2**, no este número. Si el pase sigue
> pareciendo lento, *el sitio donde tocar es `PaseXp`*.

> ⚠ Y una comprobación del autotest tuvo que cambiar de vara por esto: *«minar
> sigue mereciendo la pena»* se medía contra el 10 % del tope diario, que con
> 6.000 son 600 — y minar da 580, así que **se ponía roja sin que la minería
> hubiera cambiado nada**. Hoy se mide contra la **curva** (una hora vale medio
> nivel), que es lo que de verdad significa «avanza el pase» y no se mueve cada
> vez que alguien toca el techo.

> ⚠⚠⚠ **El mínimo no depende de cuánto juegue nadie.** Por muchas
> horas que se echen, por muchas fuentes de XP que se añadan mañana y por muy
> generosa que sea la Torre en la ronda 100, **la XP acumulada en D días
> naturales nunca puede pasar de `TOPE_DIARIO × D`**. Eso sigue siendo lo que
> hace segura a cualquier fuente nueva. La temporada dura 60 días, así que
> quedan **15 de holgura** para quien no juegue a diario.

Y esa es la propiedad que hace **seguras a todas las fuentes**. Sin el tope,
cada vez que se añadiera una forma de ganar XP habría que recalibrar el pase
entero; con él, la única pregunta al añadir una es *¿es la más cómoda del día?*
— nunca *¿rompe el pase?*.

**Es lo primero que comprueba el autotest**, porque es lo único de aquí que no
se ve hasta que alguien ya ha completado el pase en una semana.

### 1.1 · Por qué lineal creciente y no exponencial

| | |
|---|---|
| **Plana** | El nivel 100 se siente igual que el 2. No hay sensación de escalada |
| **Exponencial** | Los últimos niveles se vuelven inalcanzables y el pase **se abandona a la mitad**, que es lo contrario de lo que hace un pase |
| **Lineal creciente** ✅ | El último nivel cuesta 834 y el primero 240: **tres veces y media, no cien** |

> ⚠ **Y el PASO baja de 20 a 6 al doblar los niveles.** Con 20, el nivel 100
> costaría 2.220 y el pase entero 129.000 XP — **107 días**, o sea casi dos
> temporadas. Doblar los niveles sin tocar el paso habría hecho el pase
> imposible sin dar ningún error.

### 1.2 · El descanso acumulado

Un tope diario duro deja fuera a quien solo juega los fines de semana. Así que
**el tope no usado se acumula hasta tres días**:

```
jugaste ayer            ->  6.000
hace 2 dias             ->  2.400
hace 3 dias o mas       ->  3.600   (tope maximo)
```

> ⚠⚠ **Y no rompe el mínimo**, que es lo que hay que ver: lo que se
> acumula son días **que ya han pasado**. Tres días sin jugar dan 3.600 el
> cuarto, que es exactamente lo que se habría ganado jugando los tres.
> **Reparte, no regala.**

La pantalla lo dice (*«Descanso acumulado: tope ×3»*). Un número que sube solo y
sin explicación parece un fallo.

---

## 2. De qué se saca XP

Las cifras salen de **cuántas veces por hora ocurre cada cosa**, no de a ojo.
El objetivo: que **ninguna actividad llene sola el tope en menos de dos horas**,
y que jugar variado sea lo más rápido.

| Acción | XP | veces/hora | XP/hora si SOLO haces eso |
|---|---:|---:|---:|
| Pescar (recoger la caña Poké) | 8 | ~70 | 560 |
| Cosechar (cultivo, baya, bellota) | 3 | ~200 | 600 |
| **Picar una mena común** | **1** | ~400 | 400 |
| Picar diamante o esmeralda | 12 | ~15 | 180 |
| **Picar piedra** | **0** | ~2.500 | — |
| Capturar un Pokémon | 12 | ~25 | 300 |
| **Registrar una especie nueva** | **120** | (251 en toda la partida) | — |
| Eclosionar un huevo | 40 | ~5 | 200 |
| Ganar un combate | 6 | ~30 | 180 |
| Ronda de la Torre | `min(120, 10 + 2·ronda)` | ~20 | ~1.000 |
| Completar una misión | 50 | (28 en total) | — |
| Subir un nivel de Vía | 100 | raro | — |
| Ganar una medalla | 300 | (16 en total) | — |

> ⚠⚠ **La piedra vale cero, y no es un olvido.** El oficio de MINERO **sí** paga
> la piedra, porque *«cavar un túnel también es minar»*. Para el pase eso no
> sirve: son **2.500 bloques a la hora**, así que a 1 XP el pase sería un
> temporizador y **toda la tabla de arriba daría igual**. Lo que cuenta para el
> pase son las **menas**.

> ⚠⚠⚠ **Y LA MENA VALÍA 4, QUE ERA DEMASIADO — Y ESTA MISMA TABLA MENTÍA**
> (2026-09-08, D-047). Aquí ponía *«mena común · 4 · ~90 veces/hora · 360»*, y
> **las 90 veces por hora eran falsas**: el carbón y el cobre salen en **vetas de
> veinte y treinta bloques**, así que un minero dedicado saca del orden de
> **400 menas a la hora**. Lo de verdad eran **1.600 XP/hora**, o sea **el tope
> diario entero en cuarenta y cinco minutos** — y con eso el resto de esta tabla
> era decoración.
>
> ⚠⚠ **Es exactamente la regla del párrafo de arriba, un escalón más arriba.** La
> piedra vale cero *porque son 2.500 bloques a la hora*; la mena valía 4 y son
> 400. **La regla estaba escrita y aun así falló**, porque la estimación de
> cuántas veces por hora pasa algo vivía en un comentario — y **un comentario no
> se comprueba**.
>
> Hoy las tasas son constantes en `PaseXp` (`VECES_HORA_MENA`) y el autotest
> cruza XP × tasa contra el tope: *«MINAR SOLO NO LLENA EL TOPE DIARIO EN MENOS
> DE DOS HORAS»*. Volver a poner 4 **se pone rojo**.
>
> ⚠ **Lo destapó el usuario picando**, no una revisión ni el autotest.

> ⚠⚠ **La mena RARA no se tocó, y es la mitad de la decisión.** Lo que estaba
> roto era el **volumen**, y un diamante no tiene volumen: no sale en vetas de
> treinta y aparece del orden de quince veces en una sesión larga de mina.
> Bajarla también habría arreglado un problema que no tenía, y habría dejado el
> hallazgo que de verdad importa pagando lo mismo que picar cobre.

> ⚠⚠ **Y de la otra idea del usuario —«5 de experiencia cada 10 menas»— se
> descartó el mecanismo, no la intención.** Es media XP por mena, o sea la mitad
> otra vez; pero **pagar por tandas obliga a recordar cuántas lleva rotas entre
> pago y pago**: una columna nueva, su migración y una escritura por bloque
> picado. Y se ve raro desde dentro: rompes nueve menas y no pasa nada. Si hace
> falta bajar más, **la palanca sigue siendo este número**.

> ⚠⚠ **COSECHAR Y PESCAR TIENEN LA MISMA FORMA DE PROBLEMA, y siguen sin
> recalibrar.** Cosecha son 3 × ~200 = 600/h y pesca 8 × ~70 = 560/h, los dos
> rozando el límite, **y sus tasas no se han vuelto a medir** — igual que la de
> la mena llevaba una semana siendo falsa. Quedan **fuera** del invariante a
> propósito: meterlas con una tasa inventada sería la confianza falsa que ya
> mordió en los gimnasios. Entran el día que se midan.

> ⚠ **Y el escaneo tampoco da XP: la da el REGISTRO.** Escanear se repite sobre
> el mismo Pokémon; registrar una especie ocurre **una vez** y hay 251. Es la
> misma decisión que ya tomaron la pesca (cuenta el recogido, no el lanzamiento)
> y la cría (cuenta el nacimiento, no el huevo).

> ⚠ **Los hitos pagan mucho y no desequilibran nada, por el tope.** 300 de una
> medalla es un tercio del día, no un salto gratis. Sin tope habría que
> rebajarlos; con tope, lo que hacen es que **un día en el que consigues algo
> importante llene el tope sin farmear**.

#### 2.0 · ⚠⚠ Que cada fuente PAGUE DE VERDAD, y cómo se comprueba

La tabla de arriba dice de qué se saca XP. Que una constante exista **no
significa que nadie la use**: si `PESCA` valiera 8 y ningún sitio llamara a
`Pase.ganar` al pescar, el pase sencillamente **no subiría al pescar** — sin
error al compilar, sin error al arrancar y sin una línea en el log, con la tabla
de este documento prometiéndolo igual. Es el fallo de los 62 cosméticos que no
existían, y el de `KitService.claim`, que apuntaba la fecha y no entregaba nada.

Se comprueba en **dos mitades, porque son dos preguntas distintas**:

| | pregunta | dónde |
|---|---|---|
| el camino | ¿dar esa XP la suma? | `AutoTest.testFuentesDelPase` — recorre las **doce** fuentes con su valor real, y exige que el total del jugador suba exactamente eso |
| el cableado | ¿alguien llama a `ganar` cuando el jugador pesca? | `python tools/comprobar_pase.py` |

> ⚠⚠ **El autotest no puede comprobar el cableado**: sabe que `ganar` acredita,
> pero no si alguien lo llama al pescar — eso es la suscripción a los eventos y
> solo se ve leyendo el código. Por eso el segundo es un script, de la misma
> familia que `comprobar_textos.py`: dos listas que tienen que estar de acuerdo
> y nada las obliga.

> ⚠ **Lo que ninguno de los dos dice** es si el evento del que cuelga la llamada
> se dispara de verdad. Eso solo se ve jugando. Verificado el 2026-09-08: las 13
> fuentes tienen quien las llame.

### 2.1 · La Torre de Batalla

Quedaba pendiente en la Torre y ahora está: **superar una ronda da XP del pase**.

```
XP(ronda) = min(120, 10 + 2 · ronda)      techo en la ronda 55
```

Crece con la ronda porque la ronda crece en dificultad, y crece **despacio**:
una tanda hasta la ronda 20 son 620 XP en unos cuarenta minutos, del mismo orden
que pescar ese rato.

> ⚠ **Repetir rondas bajas no es rentable**: cada intento empieza en la 1, así
> que quien se quede en la 3 cobra 46 por tanda. Llegar lejos paga más que
> reiniciar, que es exactamente lo que la Torre quiere premiar.

> ⚠ **Y hay techo aunque el tope diario ya lo tapara.** En la ronda 100 sin
> techo serían 210 por un combate de tres minutos: el tope lo cortaría igual,
> pero el número en pantalla enseñaría que **la Torre es la única fuente que
> importa** — y lo que se pidió es que *no esté chetado*, no que esté chetado y
> contenido. El autotest comprueba que ninguna ronda vale más de **un quinto del
> tope diario**.

### 2.2 · ⚠⚠ Dónde se engancha, y por qué ahí

**El pase no se suscribe a ningún evento.** Cuelga de los embudos que ya
existen:

| Embudo | Qué le da al pase |
|---|---|
| `OficiosListener.anotar` | minar, pescar, cosechar, criar, ganar combates |
| `CaptureListener.handle` | capturar, y **si la especie era nueva** |
| `OficiosService.ganar` | subir de nivel de Vía |
| `Combate` (gimnasios) | la medalla, **dentro del `if (nueva)`** |
| `Red` (reclamar misión) | la misión, **dentro del `if (claim)`** |
| `TorreRecompensas.hookExpPaseBatalla` | la ronda de la Torre |

El motivo está escrito en el javadoc de `anotar`: *«si las misiones se avanzaran
desde otro listener, el día que alguien cambie de qué evento cuelga la pesca lo
cambiaría en uno solo, y el otro se quedaría mirando un evento que ya no
ocurre — sin dar ningún error»*. **Colgados del mismo embudo, no pueden
discrepar.**

Y hay un caso donde eso es más que higiene: **la captura**. Saber si la especie
era nueva sólo se puede en `CaptureListener`, porque un segundo oyente
preguntaría cuando **ya está registrada** y siempre saldría *«no era nueva»*.

> ⚠ Dos casos van **dentro de un `if`** y ahí está el detalle: la medalla sólo
> la primera vez (repetir un gimnasio ganado serían 300 XP por combate, o sea el
> tope en tres combates) y la misión sólo si `claim` dice que estaba completa y
> sin cobrar (pulsar dos veces no paga dos veces).

> ⚠ **El creativo está excluido de todo el pase, en un solo sitio** (`Pase.ganar`).
> Es el mismo filtro que ya aplicaba el oficio de MINERO, subido un nivel para
> que valga para todas las fuentes: un constructor con Axiom rompiendo bloques
> no es un jugador ganando XP, es la ciudadela en obras.

---

## 3. Los cien niveles, en cinco tramos

**Una sola vía y es de pago.** Quien no compra el pase **gana XP igual** y ve su
nivel subir; lo que no puede es cobrar. Al comprarlo se le abre de golpe todo lo
que ya tenía por nivel — así comprarlo a mitad de temporada no castiga.

> ⚠ **El pase compra el derecho a COBRAR, no el derecho a progresar.** Es lo que
> hace que la pantalla tenga sentido para quien no lo ha comprado: ve su nivel,
> ve lo que se está perdiendo, y sabe exactamente cuánto lleva ganado.

El usuario pidió *«que los determines por categoría, mientras más nivel tenga
puede reclamar mejores recompensas»*. El orden de los tramos **no es
decorativo**: es el orden en que un jugador necesita las cosas.

| Tramo | Niveles | Qué lleva |
|---|---|---|
| **PREPARACIÓN** | 1-20 | Balls, pociones, bayas, Caramelos EXP S/M. Para poder jugar en serio |
| **CRIANZA** | 21-40 | **Destiny Knot**, Everstone, los **seis objetos de poder**, Lucky Egg, Love/Friend/Moon/Dream Ball |
| **ENTRENAMIENTO** | 41-60 | Las seis vitaminas, PP Up, Caramelos Raros, Caramelos EXP L/XL. **El 50 paga 100 LunaCoins** (§3.3) |
| **COMBATE** | 61-80 | Restos, Vidasfera, Banda/Gafas/Pañuelo Elegido, Chaleco Asalto, Eviolita, Casco Dentado, Botas Gruesas |
| **MAESTRÍA** | 81-100 | Mentas de naturaleza, **Cápsula y Parche de Habilidad**, PP Max, **Master Ball**. **El 98 paga 100 LunaCoins** (§3.3) |

> ⚠ **Los seis objetos de poder van repartidos del 26 al 35 a propósito.** Sirven
> de uno en uno —cada uno fija una estadística al heredar— así que darlos juntos
> convertiría diez niveles en uno.

### 3.1 · Los DOS Pokémon

| Nivel | Pokémon |
|---|---|
| **1** | Charizard nivel 15 |
| **100** | **Charizard VARIOCOLOR nivel 50** — el premio mayor |

**Y no hay más.**

> ⚠⚠⚠ **La primera versión metía tres más** —Gengar en el 25, Tyranitar en el 50
> y Dragonite en el 75— *«para que la mitad del carril tuviera a dónde mirar»*.
> **Nadie los había pedido.** El usuario lo corrigió: *«no te dije que me dieras
> más pokemons, solo charizard y charizard variocolor»*, y tenía razón:
> **ensanchar el encargo por tu cuenta es exactamente igual de malo que
> recortarlo**.
>
> Los tres huecos pasaron a ser hitos del tramo que les toca: Huevo Suerte
> (25, crianza), 15 Caramelos Raros (50) y dos Capas Furtivas (75, combate).
> **El del 50 volvió a cambiar con D-047 y hoy son 100 LunaCoins** (§3.3).

> ⚠⚠ **El autotest comprueba que sean EXACTAMENTE DOS**, y en el 1 y en el 100.
> Un `>= 2` habría dejado pasar el mismo error dentro de seis meses sin decir
> nada: lo que hay que fijar es **el número exacto**, porque cuántos Pokémon da
> el pase **no es una decisión mía**.

> ⚠⚠ **Si el equipo está lleno, el Pokémon va al PC y se avisa por el chat.**
> `getParty().add()` devuelve `false` con seis dentro: sin mirarlo, el jugador
> cobraría el Charizard variocolor del nivel 100, vería «RECOGIDO» y **no tendría
> nada**.

### 3.2 · La rareza, y para qué sirve

Cada premio lleva una de cuatro: **COMÚN · RARA · ÉPICA · LEGENDARIA**. No cambia
lo que se entrega ni lo que cuesta: cambia **el color del marco, el brillo y si
la tarjeta echa chispas**.

> ⚠⚠ Existe porque **un carril de cien tarjetas iguales no se lee**: el jugador
> no sabe dónde mirar. Con rareza, los hitos se ven desde la otra punta del
> carril y el mini-mapa puede marcar dónde están los Pokémon.

Y el autotest comprueba una cosa que un reordenado accidental rompería sin dar
error: **la segunda mitad del pase premia más que la primera**.

### 3.3 · Las 200 LunaCoins que devuelve el pase

**Nivel 50 → 100 LunaCoins. Nivel 98 → 100 LunaCoins.** Petición del usuario, el
mismo día que el precio bajó a 1.500 (D-047).

> ⚠⚠⚠ **Es la ÚNICA recompensa del pase que no se entrega: se INGRESA.** Un
> objeto y un Pokémon van a un inventario —que no es una tabla— y por eso se
> reparten **después** del commit que apunta el cobro. Esto es **dinero**, así
> que le aplica R3 y va **dentro de la misma transacción** que la fila de
> `pase_reclamo`: o se apunta y se paga, o no pasa ninguna de las dos cosas.
>
> Aquí además sale gratis, porque esa fila **ya está en esa transacción**.
> Pagarlas fuera dejaría el nivel marcado como cobrado y las LunaCoins sin
> ingresar: el jugador ve «RECOGIDO» y **no tiene nada**, y no hay forma de saber
> a quién le pasó.

> ⚠⚠ **Y `Red.entregarPase` las salta explícitamente.** Su javadoc decía *«R3 no
> aplica aquí porque no se mueve dinero»* — y **eso dejó de ser verdad** el día
> que el 50 y el 98 pagaron moneda. Si ese método también las pagara, **se
> cobrarían dos veces**.

> ⚠⚠ **No cruza D-014**, que es la regla de la que cuelga todo el modelo de pago:
> **no convierte una moneda en otra**. El pase se compra con LunaCoins y devuelve
> LunaCoins — es un **reembolso**, no un tipo de cambio. Y no es una categoría
> nueva: **la Torre ya hacía lo mismo** (+50 LunaCoins cada 30 rondas).

> ⚠⚠⚠ **LO QUE HAY QUE VIGILAR NO ES QUE DEVUELVA, SINO CUÁNTO.** Un pase que
> devuelve lo que cuesta **se paga solo para siempre**: se compra una vez, se
> completa, y la temporada siguiente sale gratis — a partir de ahí el producto
> deja de venderse **sin que nadie toque una línea de código**. Es el único fallo
> de esto que no se ve mirando la pantalla: se ve en la facturación, meses
> después.
>
> Hoy son **200 sobre 1.500, el 13 %**, y harían falta 7,5 temporadas de
> reembolso para pagar una. El autotest exige que **no llegue ni a la mitad** del
> precio, y comprueba también que los dos premios sigan **en el 50 y en el 98**:
> moverlos al 1 y al 2 cumpliría todo lo demás y regalaría el reembolso el primer
> día.

> ⚠ **Con 200 LunaCoins no se compra ningún cosmético** (el más barato son
> 1.200). No es un error —es la misma nota que ya tiene el bono de los oficios—
> pero conviene saberlo: el reembolso **acumula**, no compra solo.

> ⚠ **V034 suelta esos dos reclamos, y sólo esos dos.** Una fila de
> `pase_reclamo` dice «este nivel está cobrado», **no dice QUÉ se cobró**: quien
> hubiera cobrado el 50 cuando daba 15 Caramelos Raros se quedaría con la tarjeta
> en gris para siempre y no vería nunca las LunaCoins. Y **no se vacía la tabla
> entera como en la V033** — eso le devolvería a cualquiera los cien premios para
> volver a cobrarlos, que es la vuelta atrás más cara posible por un cambio de
> dos filas.

---

## 4. La temporada

```json
{ "numero": 1, "empieza_ms": ..., "acaba_ms": ... }   // pase_temporada, UNA fila
```

- **60 días.** `/luna pase nueva_temporada [dias]` la rota (nivel 4).
- Rotar **no borra nada**: las filas viejas llevan el número de temporada en la
  clave, así que la nueva empieza vacía sin tocar la anterior. Y permite mirar
  hacia atrás: *cuánta gente terminó la temporada 1*.
- La vía Luna se compra **por temporada**. Sin eso sería una suscripción que se
  paga una vez, y el pase no tendría nada que ofrecer a partir de la segunda.

> ⚠⚠ **La temporada NO rota sola.** Las Cazas rotan al mirar y está bien: lo que
> se pierde es un ciclo de 24 h. Aquí rotar **borra el progreso de sesenta días
> y la vía Luna que alguien pagó con 1.500 LunaCoins**, así que no puede
> dispararlo un reloj.

> ⚠ **Y pasada la fecha de fin se sigue ganando XP.** Pararla castigaría al
> jugador por un despiste del operador, que no es suyo. La pantalla dice
> *«Temporada terminada — reclama lo tuyo»*.

---

## 5. Persistencia

`V032__pase.sql`. Tres tablas y una decisión que las explica:

> ⚠⚠⚠ **SE GUARDA LA XP TOTAL DE LA TEMPORADA, NO EL NIVEL.**
>
> Guardar *«nivel 12 y 340 sueltos»* es lo que hace `player_path`, y allí está
> bien porque las Vías no cambian de curva. **Un pase sí**: la curva es
> calibración, y calibrar es justo lo que este proyecto tiene pendiente en toda
> la economía. Con el nivel guardado, tocar la curva **no recalcularía a nadie**
> — la gente se quedaría con el nivel viejo y una XP suelta que ya no significa
> lo mismo, **sin un solo error**.
>
> Con la XP total, el nivel es una **función pura de un número** (`PaseNivel`),
> así que cambiar la curva recoloca a todo el mundo de forma consistente. Es la
> misma decisión que *«la máscara de medallas se compone al leer»*.

> ⚠⚠ **Y por eso `pase_reclamo` es por nivel y no se deduce de la XP**: lo
> cobrado está cobrado.
>
> ⚠⚠⚠ **Y eso mismo obligó a `V033`.** Al pasar de 50 niveles a 100, el nivel 3
> dejó de dar «900 de Plata» y pasó a dar «10 Pociones»: una fila que decía «ya
> cobré el 3» pasaría a significar **ya cobraste algo que nunca recibiste**, y el
> premio de hoy se quedaría bloqueado para siempre. No es un fallo que dé error:
> es un nivel en gris que nadie sabe por qué está en gris. Se pudo borrar sin
> mirar a nadie porque **nadie había comprado el pase todavía**; con el pase vivo
> la migración correcta habría sido otra —rotar la temporada—. Si el nivel se recalculara y alguien bajara, no se le
> puede quitar lo que ya tiene en el inventario — y sin esa tabla se le podría
> volver a cobrar al subir otra vez.

> ⚠⚠⚠ **La clave primaria de `pase_reclamo` es `(jugador, temporada, nivel, vía)`.**
> No lo dice una comprobación en Java: lo dice la clave, así que cobrar dos
> veces el mismo premio **falla en la base venga de donde venga la petición** —
> dos clics, un cliente modificado, un reintento de red. Misma decisión que
> `gym_badge` y que `clan_member`.

**El orden de cobro**: se apunta **antes** de entregar. Al revés, un fallo entre
los dos pasos regala el premio otra vez — es la misma decisión que ya tomó
`StarterService`. La Plata y las llaves se dan **dentro de la misma
transacción** (R3); los objetos y los cosméticos no pueden (un inventario no es
una tabla) y se entregan después del commit con `offerOrDrop`, que no puede
fallar.

---

## 6. La pantalla

`PaseScreen`, sobre el chasis compartido `pokepad_cosmeticos.png`.

```
PANEL IZQUIERDO                CARRIL (4 tarjetas de 180x336)
  anillo de nivel, animado         cabecera del TRAMO + flechas de pagina
  nivel / 100                      ┌────┐ ┌────┐ ┌────┐ ┌────┐
  XP / siguiente nivel             │ 41 │ │ 42 │ │ 43 │ │ 44 │
  XP de hoy y el tope  + [?]       └────┘ └────┘ └────┘ └────┘
  caja del PASE (comprar/activo)   ▓▓▓▓▓▓░░░░░░░░  el mini-mapa de los 100
  RECLAMAR TODO (n)                rueda = 1 nivel · flechas = 1 pagina
  una fuente de XP cada 3 s        clic en el mapa = saltar
```

> ⚠⚠⚠ **Ni la curva ni los premios viajan por la red.** La pantalla lee
> `PaseNivel` y `PaseCatalogo` **directamente**, porque los dos viven en `main` y
> `main` corre en los dos lados — la misma decisión que `CatalogoPad`. Mandar el
> nivel y la lista de premios sería un **segundo sitio donde vive la misma
> verdad**, y este proyecto ya sabe cómo acaba eso: las tres listas de medallas,
> los dos órdenes del PokePad. **Aquí no puede desincronizarse porque no hay dos
> copias.**

Lo que sí viaja es el **estado**: XP, si tienes la vía Luna, lo cobrado (dos
máscaras de bits, una por vía) y el tope de hoy. P6 sigue intacto: pulsar
RECLAMAR manda **el nivel y la vía**, nunca el premio.

### 6.1 · Las tres animaciones, y por qué cada una

| | |
|---|---|
| **El anillo barre al abrirse** (700 ms, con frenada) y **respira** | Dice de un vistazo cuánto falta para el siguiente nivel **sin leer un número**, y el halo hace que sea lo primero que se mira |
| **Los premios cobrables** llevan halo, **brillo que barre** y un **marco cuya luz da la vuelta** | ⚠ Un premio disponible dibujado **igual** que uno bloqueado **no se ve**: el jugador abre, no encuentra nada y cierra |
| **Los legendarios alcanzados** echan cuatro destellos girando | Con cien tarjetas hay que poder ver los hitos desde lejos |
| **Al cobrar salen chispas** del centro de la tarjeta, más si es legendaria | Es la única respuesta inmediata: el premio tarda en llegar al inventario |
| **El carril se desliza** en vez de saltar | Con 100 niveles y 4 a la vista, saltar hace que el jugador pierda el sitio |
| **El mini-mapa** enseña los 100 niveles, los cinco tramos por color, lo conseguido en oro y **dónde estás** | El carril enseña el 4 % del pase; sin el mapa no hay forma de saber cuánto queda — y **se pulsa para saltar** |

> ⚠ **Todas van con `System.currentTimeMillis()`**, nunca con el tiempo del
> mundo: la ciudadela tiene la hora **congelada** (noche permanente), así que
> una animación colgada del reloj del mundo se queda clavada. Es la lección que
> ya pagó `Auras`.

### 6.2 · La maqueta que mide

```bash
python tools/gen_maqueta_pase.py
```

Dibuja la pantalla **sobre el chasis real** con **las anchuras reales de la
fuente del juego** y avisa de desbordes y solapes. Es la práctica que estableció
`gen_maqueta_mercado.py` el 2026-08-25, cuando en su primera pasada seria
encontró cuatro fallos que **no daban ningún error**.

> ⚠⚠ **Las medidas se LEEN de `PaseScreen.java`**, no se copian. Copiarlas sería
> una segunda lista de constantes que nada obliga a coincidir con la que dibuja
> el juego — y sería el peor caso posible: una maqueta que dice *«cabe»*
> midiendo unos números mientras el juego pinta otros. Si el fichero cambia de
> forma, el script **aborta** en vez de medir a medias.

Estado actual: **limpia**. Las seis columnas ocupan 770 de los 771 disponibles,
con 15 px de margen a cada lado — y como `COLS` se calcula, ensanchar una
tarjeta no las saca del marco: caben menos, y ya está.

### 6.3 · ⚠⚠ Lo que se calcula en vez de escribirse

Tres cosas, y las tres son la misma lección de este proyecto:

1. **El número de columnas.** `COLS = (PANT_W - 2·MARGEN + GAP) / (CARD_W + GAP)`.
   Escrito a mano, un 7 pintaría la séptima tarjeta fuera del chasis **sin dar
   ningún error**. Es la quinta vez que aquí se tropieza con una rejilla que
   *«cabía por casualidad»*: los quince iconos del Pad, los 62 cosméticos, las 8
   paradas de Viajes, las 23 medallas de la Liga.
2. **Dónde empieza la caja del pase y el botón de reclamar.** Los usan el
   **dibujado y el clic**. Escritos dos veces, mover una línea del panel dejaría
   el botón pintado en un sitio y respondiendo en otro.
3. **Las cifras de «CÓMO SUBE EL PASE»**, que salen de `PaseXp`. Escritas a mano
   serían una segunda lista que nada obliga a coincidir con la que paga el
   servidor: al recalibrar la pesca, la pantalla seguiría prometiendo lo de
   antes **y el jugador creería que le pagamos de menos**.

---

## 6.4 · ⚠⚠⚠ Los tres fallos que solo se vieron EN EL JUEGO

La maqueta mide geometría y salió limpia. Estos tres **no son geometría**, y por
eso hicieron falta las capturas del usuario:

**1. La sombra nativa, a esta escala, es un contorno negro.**
`TorreRecompensasScreen` dejó escrito —y es cierto— que la sombra nativa es
mejor que dibujar cuatro copias desplazadas. **Lo que aquella nota no dice es
que eso vale a escala 1.** Minecraft desplaza la sombra *una unidad de fuente*, y
aquí la matriz está escalada: un texto de 19 px de arte se dibuja con
`escala = 19/9 = 2,1`, así que esa unidad se convierte en **dos píxeles y medio**
de pantalla, y en un 4K en cinco. Deja de ser sombra y pasa a ser un contorno
grueso pegado a cada letra. Palabras del usuario: *«tiene como un contorno negro
y no se ve bien»*.

> **La regla que queda:** en una pantalla del PokePad, donde todo el texto se
> dibuja con la matriz escalada y sobre rellenos sólidos, **la sombra va
> apagada**. El contraste lo da el fondo.

**2. `DrawContext` no dibuja en el orden en que se le pide.**
El panel de ayuda se pintaba con un relleno del 95 % **después** de las tarjetas,
y aun así se veían las tarjetas por encima. No era transparencia: **el texto va
en una capa que se vuelca la última**, así que el texto de las tarjetas se
dibujaba sobre el panel. Se arregla con `ctx.draw()` **antes** de tapar, que
fuerza el volcado de todo lo anterior.

> Es la misma regla de las 2 pasadas de `dibujado.md` vista desde otro lado: allí
> se vacía el búfer para meter 3D, aquí para tapar 2D.

**3. `/luna pase xp` no hacía nada, y la causa era correcta.**
Llama a `Pase.ganar`, que **descarta a quien está en creativo** —un constructor
con Axiom no es un jugador ganando XP—. Pero **quien prueba el pase es un
operador, y un operador está en creativo**: la única forma de probarlo chocaba
con la única protección del sistema, y el comando decía «hecho».

> ⚠⚠ Y **aunque el filtro no hubiera estado, habría seguido pareciendo roto**:
> pedir 50.000 con un tope de 1.200 concede 1.200. Hoy el comando dice **lo que
> de verdad ha entrado** y, si se ha topado, lo explica y remite a
> `/luna pase nivel`.

---

## 7. Comandos

| | |
|---|---|
| `/luna pase` | (nivel 3) La temporada, la curva, el tope, el mínimo de días y los dos valores de calibración |
| `/luna pase nueva_temporada [dias]` | (nivel 4) Rota. Avisa por difusión y refresca a todos |
| `/luna pase xp <jugador> <cantidad>` | (nivel 3) Para probar sin jugar sesenta días. **El tope se aplica igual** |
| `/luna pase nivel <jugador> <0-100>` | (nivel 4) **Lo salta al nivel exacto.** Es lo único que permite ver el final del carril: con `xp` y el tope diario harían falta 45 días |
| `/luna pase reiniciar <jugador>` | (nivel 4) Devuelve su pase a cero —XP, compra y reclamos— sin rotar la temporada, que afectaría a todo el mundo |
| `/luna pase via_luna <jugador>` | (nivel 4) Da el pase **sin cobrar**: reembolsos y premios de evento |

> ⚠ `via_luna` no pasa por la economía a propósito. Un regalo no es una compra, y
> meterlo por `comprarLuna` con importe cero ensuciaría el libro de asientos con
> movimientos de 0 LunaCoins que no significan nada. Quien quiera saber quién
> pagó mira `ledger_entry`; quien tenga la vía y no salga ahí, la recibió.

---

## 8. Lo que el autotest comprueba, y por qué esas cosas

| Comprobación | Qué caza |
|---|---|
| **El pase no se puede completar en menos de 40 días** | La propiedad de diseño del sistema entero. Sale de dos números en ficheros distintos que nada obliga a mirar juntos |

| `nivelDe` y `acumulada` cuadran en los 101 bordes | Un jugador con la XP justa del nivel 73 que se queda en el 72: **ve el premio y no lo puede cobrar**, una vez de cada cien y sin traza |
| El tope diario recorta una concesión desmedida | Que el diseño de arriba sea una garantía y no una intención |
| El mismo premio no se cobra dos veces | La clave primaria, ejercitada |
| No se cobra un nivel que no se ha alcanzado | **P6**. Sin esto un cliente modificado pide el nivel 100 el primer día y se lleva el shiny |

| El pase no se cobra dos veces | 1.500 LunaCoins cobrados por duplicado |
| **Todo objeto existe en el registro** | El fallo de las Cazas, **con factura**: el jugador soltó 1.500 LunaCoins, hizo 45 días de trabajo y no recibe nada |
| **Exactamente DOS Pokémon**, en el 1 y en el 100 | Cuántos Pokémon da el pase **no es una decisión mía**: el número exacto es lo que impide que vuelva a ensancharse solo |
| Toda especie existe en Cobblemon | Se le pregunta **a Cobblemon**, no a una lista nuestra: una lista repetiría el mismo error que intenta cazar |
| **Sin el pase no se cobra ni el nivel 1** | Es la regla que sostiene D-046: el pase es de PAGO. Un fallo en ese `if` regalaría los cien premios a todo el servidor **y no daría ningún error**, porque entregar funciona igual de bien |
| El nivel 1 da Charizard 15 y **el 100 Charizard shiny 50** | Son los dos que fijó el usuario: si alguien los toca sin querer, rojo antes de desplegar |
| Los tramos cubren los 100 niveles sin huecos | Un hueco deja niveles sin categoría, y la cabecera enseñaría la del tramo anterior — o sea, mentiría |
| La segunda mitad premia más que la primera | Reordenar la tabla al revés no da ningún error y deja el pase sin sentido |
| Ninguna ronda de la Torre vale más de un quinto del tope | Que la Torre no sea la única fuente que importa |
| El pase **cabe** en una temporada de 60 días | Con un pase de PAGO importa igual que el mínimo: quien lo compra tiene que poder terminarlo |
| **Minar solo no llena el tope en menos de dos horas** | Nace de un fallo real: la mena valía 4 con una tasa falsa, o sea el día entero en 45 min. No daba error — el pase subía perfectamente, sólo que sólo se subía picando |
| …y minar **sigue mereciendo la pena** (≥ 10 % del día por hora) | El otro lado: una fuente apagada de hecho sobra de la tabla |
| **El pase no devuelve lo que cuesta**, ni la mitad | Un pase que se paga solo deja de venderse **para siempre**, y eso no se ve en la pantalla: se ve en la facturación meses después |
| Los dos premios de LunaCoins siguen **en el 50 y en el 98** | Moverlos al 1 y al 2 cumpliría todo lo demás y regalaría el reembolso el primer día |

---

## Next Actions

1. **Mirarlo en el juego.** Esta desplegado y probado, pero **nadie ha abierto
   la pantalla**: falta ver que el anillo, el carril y los latidos se vean como
   deben, y cobrar un premio de verdad.
2. ~~El arte del icono~~ ✅ **instalado el 2026-09-08.**
3. **Medir de verdad las tasas de cosechar y pescar.** Es lo que queda abierto
   de D-047: los dos rozan el límite con una estimación que nadie ha vuelto a
   comprobar, y la de la mena llevaba una semana siendo falsa. Cuando se midan,
   entran en el invariante de §8 junto a la minería.
4. **Calibrar con datos reales.** Como todo lo económico de este proyecto, los
   importes son provisionales. Aquí hay **cuatro palancas** y ninguna más:
   `PaseNivel.BASE`, `PaseNivel.PASO`, `PaseNivel.TOPE_DIARIO` y la tabla de
   `PaseXp`. La tabla de premios es una quinta, y es la que más se va a tocar.
5. **Retos diarios y semanales**, si algún día hace falta. Hoy la XP es un goteo
   por acción con tope; los retos son la otra mitad de un pase moderno y
   encajarían sin tocar nada de lo de arriba — entrarían como una fuente más por
   `Pase.ganar`, y el tope las contendría igual que a las demás.

## Related Documents

- [Monetización](monetization.md) · [Tesoros](treasures.md) · [Tienda](tienda.md)
- [La Torre de Batalla](../world/torre-batalla.md)
- [Cómo se dibuja una pantalla](../ui/dibujado.md) · [El arte del PokePad](../ui/prompts-arte-pokepad.md)
