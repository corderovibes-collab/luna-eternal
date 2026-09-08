# El Pase de Batalla Luna

## Purpose

Cómo funciona el **Pase de Batalla** (D-045): una temporada de 60 días con
50 niveles que se suben **jugando** —capturando, minando, pescando, criando,
registrando la Pokédex, ganando medallas y escalando la Torre— y dos vías de
recompensa: una **gratuita** con premios de juego y una **de pago** (15.000
LunaCoins) con **solo cosméticos**.

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

**Construido, compilado y con sus invariantes en `/luna autotest`.**
**SIN VERIFICAR EN EL JUEGO** y sin desplegar. El icono de la rejilla es
**provisional** (`tools/gen_icono_pase.py`) hasta que llegue el arte de Gemini
—el prompt está en [`../ui/prompts-arte-pokepad.md`](../ui/prompts-arte-pokepad.md) §5.4-quater.

## Last Decision

**D-045** — El pase tiene dos vías; la de pago **solo lleva cosméticos**.

---

## 1. La cuenta que sostiene todo el diseño

El usuario pidió tres cosas: *que se suba con todo lo que se puede hacer en
Cobblemon*, *que esté bien calibrado matemáticamente* y **que no se consiga todo
rápido**. Las tres se resuelven con dos números:

```
curva      coste(n) = 300 + 20n     para n = 0..49
           total    = 39.500 XP

tope       900 XP de pase al día
           ────────────────────────────────────────
           39.500 / 900 = 43,9  ->  44 DIAS COMO MINIMO
```

> ⚠⚠⚠ **El mínimo de 44 días no depende de cuánto juegue nadie.** Por muchas
> horas que se echen, por muchas fuentes de XP que se añadan mañana y por muy
> generosa que sea la Torre en la ronda 100, **la XP acumulada en D días
> naturales nunca puede pasar de `900 × D`**. La temporada dura 60 días, así que
> quedan **16 de holgura** para quien no juegue a diario.

Y esa es la propiedad que hace **seguras a todas las fuentes**. Sin el tope,
cada vez que se añadiera una forma de ganar XP habría que recalibrar el pase
entero; con él, la única pregunta al añadir una es *¿es la más cómoda del día?*
— nunca *¿rompe el pase?*.

**Es lo primero que comprueba el autotest**, porque es lo único de aquí que no
se ve hasta que alguien ya ha completado el pase en una semana.

### 1.1 · Por qué lineal creciente y no exponencial

| | |
|---|---|
| **Plana** | El nivel 50 se siente igual que el 2. No hay sensación de escalada |
| **Exponencial** | Los últimos niveles se vuelven inalcanzables y el pase **se abandona a la mitad**, que es lo contrario de lo que hace un pase |
| **Lineal creciente** ✅ | El último nivel cuesta 1.280 y el primero 300: **cuatro veces más, no cien** |

### 1.2 · El descanso acumulado

Un tope diario duro deja fuera a quien solo juega los fines de semana. Así que
**el tope no usado se acumula hasta tres días**:

```
jugaste ayer            ->  900
hace 2 dias             ->  1.800
hace 3 dias o mas       ->  2.700   (tope maximo)
```

> ⚠⚠ **Y no rompe el mínimo de 44 días**, que es lo que hay que ver: lo que se
> acumula son días **que ya han pasado**. Tres días sin jugar dan 2.700 el
> cuarto, que es exactamente lo que se habría ganado jugando los tres.
> **Reparte, no regala.**

La pantalla lo dice (*«Descanso acumulado: tope ×3»*). Un número que sube solo y
sin explicación parece un fallo.

---

## 2. De qué se saca XP

Las cifras salen de **cuántas veces por hora ocurre cada cosa**, no de a ojo.
El objetivo: que **ninguna actividad llene sola el tope en menos de hora y
media**, y que jugar variado sea lo más rápido.

| Acción | XP | veces/hora | XP/hora si SOLO haces eso |
|---|---:|---:|---:|
| Pescar (recoger la caña Poké) | 8 | ~70 | 560 |
| Cosechar (cultivo, baya, bellota) | 3 | ~200 | 600 |
| Picar una mena común | 4 | ~90 | 360 |
| Picar diamante o esmeralda | 12 | ~12 | 144 |
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

> ⚠ **Y el escaneo tampoco da XP: la da el REGISTRO.** Escanear se repite sobre
> el mismo Pokémon; registrar una especie ocurre **una vez** y hay 251. Es la
> misma decisión que ya tomaron la pesca (cuenta el recogido, no el lanzamiento)
> y la cría (cuenta el nacimiento, no el huevo).

> ⚠ **Los hitos pagan mucho y no desequilibran nada, por el tope.** 300 de una
> medalla es un tercio del día, no un salto gratis. Sin tope habría que
> rebajarlos; con tope, lo que hacen es que **un día en el que consigues algo
> importante llene el tope sin farmear**.

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

## 3. Las dos vías

> ⚠⚠⚠ **NO LLEVAN LO MISMO, Y NO ES UNA PREFERENCIA.**

| Vía | Qué lleva | Por qué |
|---|---|---|
| **LIBRE** (gratis) | Plata, objetos y llaves | Se gana **jugando**. Es una fuente más del juego, como las Cazas o la Torre, y se calibra como tal (P3) |
| **LUNA** (15.000 LunaCoins) | **Solo cosméticos** | Se **compra** con moneda premium, y el test de [`monetization.md`](monetization.md) §6 se para en la segunda pregunta |

El test de §6, aplicado literalmente:

```
1. ¿Da estadísticas, IVs/EVs, shinies, legendarios o acceso a progresión?
   Vía Luna: NO (solo cosméticos)                            -> pasa
2. ¿Crea moneda u objetos comerciables?
   Vía Luna: NO                                              -> pasa
3. ¿Sustituye a un sink que diseñamos?      NO               -> pasa
4. ¿Se aplica en PvP clasificatorio?        NO (es cosmético)-> pasa
5. ¿Tiene tope y cooldown?     Sí: una temporada, y se vuelve a comprar
6. ¿Un jugador sin pagar puede conseguir lo mismo con tiempo?
   NO, y está permitido: "salvo en T1 puro"                  -> SE VENDE
```

> ⚠⚠ **Un Caramelo Raro en la vía libre es un premio por jugar; el mismo
> Caramelo en la vía de pago es COMPRAR PROGRESIÓN**, que es T4 y la línea roja
> de D-007 y D-014. CLAUDE.md ya lo decía de la tienda: *«si algún día vuelven,
> que no sea por LunaCoins»*. Por eso los Caramelos Raros están en los niveles
> 44 y 47 de la vía **libre** y no hay ni uno en la de Luna.

**El autotest lo comprueba**, porque es la clase de regla que se cae sola cuando
alguien edita una tabla y no da ningún error.

### 3.1 · La vía libre, y su presupuesto

50 niveles con premio en todos. Reparte **15.300 de Plata** por temporada, unos
**255 al día**.

Para comparar: completar las seis Cazas da ~10.000 al día. **El pase es
deliberadamente un goteo, no una fuente.**

Lo demás son Poké Balls, pociones, bayas, caramelos EXP, y:

| Nivel | Hito | Por qué |
|---|---|---|
| 10 · 30 | Llaves de **Gacha Diario** | El cofre gratuito |
| 20 · 40 | Llaves de **Gachapón** | ⚠ Es lo que cumple la regla dura de `treasures.md` §4.1 —*toda llave se tiene que poder conseguir jugando*— para un cofre que por lo demás solo se abre pagando |
| 44 · 47 | Caramelos Raros | Progresión ganada, nunca comprada |
| **50** | **Master Ball** | El remate de **44 días de temporada como mínimo**. No se vende en la tienda y no se compra con LunaCoins: se juega |

### 3.2 · La vía Luna, y por qué solo premia en los pares

Con un cosmético en cada uno de los 50 niveles serían **50 × 1.200 = 60.000**
LunaCoins de valor de tienda por un pase de 15.000 — **cuatro veces lo que
cuesta**. Y entonces **nadie vuelve a comprar un sombrero suelto**: el pase se
comería la tienda de cosméticos, que según `monetization.md` §2 *«debe ser el
grueso de la facturación»* y *«es lo único que no rompe nada»*.

Con **25 premios** (los niveles pares) el pase devuelve:

```
18 sombreros x 1.200                     21.600
aura Notas                                1.500
mascota Gardevoir Dragón de Hielo         1.500
aura Escarcha                             2.000
mascota Decidueye Ninja                   2.500
mascota Charizard Caballero               2.500
aura Polvo Estelar (nivel 46)                 0   <- NO se vende
aura Eclipse (nivel 50)                       0   <- NO se vende
                                        ────────
                                         31.600  =  2,1 x  el precio
```

**El autotest vigila ese múltiplo** (entre 1,5× y 3×). Es el número que decide
si el pase canibaliza la tienda.

> ⚠ **Los dos hitos finales son auras que no están a la venta** (`precio 0`, el
> mecanismo que D-039 ya usaba para los eventos). Un cosmético que además se
> puede comprar **solo ahorra LunaCoins**; uno que no, dice **dónde estabas esa
> temporada**. Es lo único del pase que no se consigue de ninguna otra forma —
> y sin ello la vía Luna sería un descuento, no una temporada.

> ⚠ Y **no se retiran al acabar la temporada**: quien la tenga la conserva.
> Retirarla convertiría un recuerdo en un alquiler.

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
> y la vía Luna que alguien pagó con 15.000 LunaCoins**, así que no puede
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
> cobrado está cobrado. Si el nivel se recalculara y alguien bajara, no se le
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
PANEL IZQUIERDO                    CARRIL (6 columnas de 50 niveles)
  anillo de nivel animado            fila 1  VIA LIBRE
  XP / siguiente nivel               ── carril con nodos numerados ──
  XP de hoy y el tope                fila 2  VIA LUNA
  caja de la Via Luna                rueda del raton / flechas / ← →
  RECLAMAR TODO (n)
  dias de temporada
  COMO SUBE EL PASE (la tabla)
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
| **El anillo barre al abrirse** (700 ms, con frenada) | Dice de un vistazo cuánto falta para el siguiente nivel **sin leer un número** |
| **Los premios cobrables laten** en oro | ⚠ Un premio disponible dibujado **igual** que uno bloqueado **no se ve**: el jugador abre, no encuentra nada y cierra |
| **El carril se llena** hasta el nivel alcanzado, con un brillo que lo recorre | Es el hilo que une las cincuenta columnas |

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
2. **Dónde empieza la caja de la Vía Luna y el botón de reclamar.** Los usan el
   **dibujado y el clic**. Escritos dos veces, mover una línea del panel dejaría
   el botón pintado en un sitio y respondiendo en otro.
3. **Las cifras de «CÓMO SUBE EL PASE»**, que salen de `PaseXp`. Escritas a mano
   serían una segunda lista que nada obliga a coincidir con la que paga el
   servidor: al recalibrar la pesca, la pantalla seguiría prometiendo lo de
   antes **y el jugador creería que le pagamos de menos**.

---

## 7. Comandos

| | |
|---|---|
| `/luna pase` | (nivel 3) La temporada, la curva, el tope, el mínimo de días y los dos valores de calibración |
| `/luna pase nueva_temporada [dias]` | (nivel 4) Rota. Avisa por difusión y refresca a todos |
| `/luna pase xp <jugador> <cantidad>` | (nivel 3) Para probar sin jugar sesenta días. **El tope se aplica igual** |
| `/luna pase via_luna <jugador>` | (nivel 4) Da la vía Luna **sin cobrar**: reembolsos y premios de evento |

> ⚠ `via_luna` no pasa por la economía a propósito. Un regalo no es una compra, y
> meterlo por `comprarLuna` con importe cero ensuciaría el libro de asientos con
> movimientos de 0 LunaCoins que no significan nada. Quien quiera saber quién
> pagó mira `ledger_entry`; quien tenga la vía y no salga ahí, la recibió.

---

## 8. Lo que el autotest comprueba, y por qué esas cosas

| Comprobación | Qué caza |
|---|---|
| **El pase no se puede completar en menos de 40 días** | La propiedad de diseño del sistema entero. Sale de dos números en ficheros distintos que nada obliga a mirar juntos |
| **La vía Luna solo lleva cosméticos** | Un Caramelo Raro colado ahí es un producto que vende progresión por dinero real. **T4** |
| El múltiplo de valor está entre 1,5× y 3× | Si el pase se come la tienda de cosméticos |
| `nivelDe` y `acumulada` cuadran en los 51 bordes | Un jugador con la XP justa del nivel 12 que se queda en el 11: **ve el premio y no lo puede cobrar**, una vez de cada cincuenta y sin traza |
| El tope diario recorta una concesión desmedida | Que el diseño de arriba sea una garantía y no una intención |
| El mismo premio no se cobra dos veces | La clave primaria, ejercitada |
| No se cobra un nivel que no se ha alcanzado | **P6**. Sin esto un cliente modificado pide el nivel 50 el primer día |
| No se cobra la vía Luna sin comprarla | Lo mismo, por el otro lado |
| La vía Luna no se cobra dos veces | 15.000 LunaCoins cobrados por duplicado |
| Todo objeto existe en el registro | El fallo de las Cazas: el jugador hace el trabajo y **no recibe nada** |
| Todo cosmético existe en el catálogo | El fallo de los 62 cosméticos que no existían |
| La vía libre no regala cosméticos | **D-039** |
| Ninguna ronda de la Torre vale más de un quinto del tope | Que la Torre no sea la única fuente que importa |

---

## Next Actions

1. **Verificarlo en el juego.** Nada de esto se ha visto en pantalla.
2. **El arte del icono** — prompt en `../ui/prompts-arte-pokepad.md` §5.4-quater.
   Hoy hay un provisional.
3. **Calibrar con datos reales.** Como todo lo económico de este proyecto, los
   importes son provisionales. Aquí hay **cuatro palancas** y ninguna más:
   `PaseNivel.BASE`, `PaseNivel.PASO`, `PaseNivel.TOPE_DIARIO` y la tabla de
   `PaseXp`.
4. **Retos diarios y semanales**, si algún día hace falta. Hoy la XP es un goteo
   por acción con tope; los retos son la otra mitad de un pase moderno y
   encajarían sin tocar nada de lo de arriba — entrarían como una fuente más por
   `Pase.ganar`, y el tope las contendría igual que a las demás.

## Related Documents

- [Monetización](monetization.md) · [Tesoros](treasures.md) · [Tienda](tienda.md)
- [La Torre de Batalla](../world/torre-batalla.md)
- [Cómo se dibuja una pantalla](../ui/dibujado.md) · [El arte del PokePad](../ui/prompts-arte-pokepad.md)
