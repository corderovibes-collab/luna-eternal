# La tienda de cosméticos

## Purpose

**Qué está construido, qué falta y qué trampas ya se pagaron.** Si algo aquí no
coincide con el código, gana el código y se corrige esto.

## Dependencies

- [`dibujado.md`](dibujado.md) — **las seis reglas. Leerlas antes de tocar la
  pantalla.** La sexta salió de aquí y costó cuatro intentos
- CLAUDE.md **D-039** (cómo se consiguen), **D-013/D-033** (LunaCoins),
  **D-034** (la moneda es dorada)
- [`monetization.md`](../economy/monetization.md) — los cosméticos son **T1 ·
  identidad**, venta libre

## Current Status

**2026-08-22 · funcionando en el juego, y LA COMPRA VERIFICADA CON DINERO.**

Se abre desde el PokePad, dibuja los Pokémon en 3D sin titilar, el catálogo
viene del servidor y **comprar cobra de verdad**. Medido con `/luna economia`
después de cuatro compras reales:

```
REPORTCOINS
  Masa: 500  ✔ cuadra con el libro de asientos
  admin_grant        +10.000    (1 op)
  cosmetico_compra    -9.500    (4 ops)
```

Los 9.500 son exactamente `2500 + 1200 + 1800 + 4000`: cada compra cobró **su**
precio, y hubo **4 operaciones para 4 compras** — ni un doble cobro. El libro de
asientos cuadra, que es lo que dice que la contabilidad es correcta y no solo
que la pantalla parecía funcionar.

> Con esto, los jugadores con actividad económica del servidor pasan de **0 a
> 1**. Es la primera vez que esta economía mueve algo.

```
pantalla       CosmeticosScreen.java        4 pestañas · rejilla 4x2 · preview
3D             Mascota3D.java               Cobblemon + criaturas de Minecraft
catálogo       cosmetics/Catalogo.java      GENERADO de los resolvers · 54 · 52 especies
compra         cosmetics/CosmeticsService   transacción + idempotencia
disfraces      CobblemonMoreCosmetics       SOLO assets (en lunaneon) · NADA de datapack
tablas         V011__cosmeticos.sql         aplicada · autotest 136/136
arte           arte/pokepad/fondo_cosmeticos.png
maqueta        tools/gen_cosmeticos.py
```

---

## 1. Las medidas salen del generador, no de ojo

`tools/gen_cosmeticos.py` las **mide** sobre el arte y las imprime:

```
chasis     1380 x 828
panel      315 x 692  en (63, 70)      preview 3D + LunaCoins
pantalla   801 x 494  en (460, 204)    pestañas + rejilla
celda      183 x 193                   4 x 2 = 8 por página
```

Están copiadas en `CosmeticosScreen` porque en ejecución no se puede analizar el
PNG. **Si el fondo cambia, se reejecuta el generador y se traen los números
nuevos** — el chasis ya se quedó mintiendo cuatro veces por escribirlos a mano.

`medir_panel()` **aborta** si lo que encuentra no es un rectángulo limpio. No es
paranoia: «lo oscuro del tercio izquierdo» se traga el chasis entero y da 745 de
alto en vez de 692. El panel es `(33,36,41)` y el chasis `(52,54,62)`.

### Por qué 4 columnas y no 5

Con 5 la celda mide 144: el precio con su moneda se lleva 80 y quedan 60 para el
botón, donde «COMPRAR» no entra. Habría que apilarlos, y eso cuesta **72 px de
alto** que salen del 3D — dejándolo en 124×90, donde un Charizard no se
distingue. A 4 columnas el pie cabe en una fila de 38 y el 3D sube a 163×147.

> **Pasar a 4 columnas no ganó anchura: ganó la altura que faltaba.**

---

## 2. ⚠️⚠️ El titileo, que costó cuatro intentos

**La causa está en [`dibujado.md` §6](dibujado.md).** Resumen: `conjugate()`
mutaba un `Quaternionf` compartido, y la paridad del número de llamadas hacía que
el modelo alternara entre dos orientaciones.

Lo que importa recordar de aquí es **cómo despistó el síntoma**: titilaba *solo*
al abrir el previsualizador, y eso mandó a buscar el fallo en el panel durante
tres intentos. Era una pista sobre la **paridad**, no sobre el panel.

Los tres intentos fallidos siguen en el código porque los tres eran mejoras
reales:

| | Sigue puesto porque |
|---|---|
| **Dos pasadas**: todo lo 2D, un `ctx.draw()`, luego los modelos | `DrawContext` acumula y el 3D dibuja ya; intercalarlos deja el orden al azar |
| **`z = 0`** y no 100 | Es lo que hace el PC de Cobblemon |
| **Estado por ranura**, no por cosmético | El previsualizador y su celda son el mismo cosmético: compartir `FloatingState` los hace pisarse |

---

## 3. Cómo se dibuja un Pokémon en 3D

`drawProfilePokemon` de `PokemonGuiUtilsKt` — lo mismo que usa el PC de
Cobblemon. La **variante cosmética va en `state.currentAspects`**, que es
exactamente como `CobblemonMoreCosmetics` declara sus 66 cosméticos.

Se llama **directo** y no por reflexión: es una función de nivel superior de
Kotlin y Cobblemon entra como `modCompileOnly`, así que Loom la remapea a
nuestros mapeos.

> ⚠️⚠️ **`vendor/cobblemon` es HEAD, NO 1.7.3.** Se clonó con `--depth 1`. Allí
> la función toma un `ProfileTransformType` y un `blockLight` que **en 1.7.3 no
> existen**. Para una firma concreta, lo fiable es `javap` sobre el jar
> instalado, o el fuente en la etiqueta exacta:
> `gitlab.com/cable-mc/cobblemon/-/raw/1.7.3/...`

### El anclaje, que no es el centro

El modelo **cuelga hacia abajo desde su origen**. El PC de Cobblemon lo traslada
a `posY + 1.0` sobre una celda de 25 — al **4 % desde arriba**. Ponerlo al 62 %
lo saca por el pie de la celda. La escala también sale de ellos: 2,5 de matriz
por 4,5 de parámetro sobre 25 px, o sea **0,45 por píxel de caja**.

### Criaturas de Minecraft

Van por `InventoryScreen.drawEntity`, no por Cobblemon. Se distinguen por el
**espacio de nombres** (`minecraft:` vs `cobblemon:`) y no por una bandera: el
identificador ya lleva la respuesta, y una bandera podría contradecirlo.

Dos reglas de catálogo:

- **Solo especies pequeñas.** Una vaca tapa el precio en la celda y en el mundo
  es un estorbo.
- **Ninguna hostil.** Un Creeper de mascota es gracioso hasta que alguien no
  distingue el tuyo de uno de verdad y muere por ello.

---

## 4. El servidor manda, y con D-039 eso es un invariante

Si los cosméticos **solo** se consiguen comprándolos o en un evento, el servidor
es la única fuente que hay: un cliente que pudiera concederse uno sería la única
forma de saltárselo.

| | |
|---|---|
| **Viaja el identificador y nada más** | Ni precio, ni categoría, ni si se puede pagar. Aceptar eso del cliente sería aceptar el precio que él diga |
| **El catálogo va completo**, no por diferencias | Un paquete perdido dejaría la tienda mintiendo hasta reiniciar. Se reenvía tras cada acción, saliera bien o mal |
| **La pantalla no pinta la compra antes de que ocurra** | Adelantarse hace que un fallo de saldo se vea como un cosmético que desaparece al reabrir |
| **Equipar comprueba la posesión** | Sin eso, un cliente modificado se pone cualquier cosa — y como el equipado es lo que ven los demás, sería indistinguible de haberla comprado |

### La clave primaria es la defensa, no un índice

`PRIMARY KEY (player_id, cosmetic_id)` es lo que impide comprar dos veces. La
comprobación previa del servicio evita cobrar en el caso normal, pero **dos clics
rápidos pueden pasarla los dos**: lo que sostiene el invariante es que la segunda
inserción choca contra la clave y deshace su transacción entera.

### ⚠️ La clave de idempotencia es un UUID, y estuvo mal a propósito

Se escribió **derivada** del jugador y el cosmético —`cosm:<jugador>:<pieza>`—
con el razonamiento de que así dos clics rápidos comparten clave. Era un error,
y de los que solo se ven pensando en el caso raro:

> Si algún día se le **retira** un cosmético a alguien —reembolso, corrección de
> un evento— y lo vuelve a comprar, esa clave **ya está usada**. La economía
> contesta `ALREADY_APPLIED`, **el cobro se salta**, y la anotación sí entra.
> Cosmético gratis, sin ningún error.

Y no hacía falta: el cobro y la anotación van en la **misma transacción**, así
que si la inserción choca contra la clave primaria se deshace todo, cobro
incluido. Los dos clics ya estaban cubiertos por ahí.

**Una clave derivada del OBJETO en vez de la OPERACIÓN** convierte «esta
operación ya se hizo» en «este objeto ya se compró alguna vez», y eso deja de ser
lo mismo en cuanto algo se pueda deshacer.

---

## 5. ⚠️ El mod tiene DOS destinos

Esto costó una ronda entera de «pulso el icono y no abre nada»:

```
servidor   python tools/desplegar.py mod --reiniciar
clientes   python tools/gen_manifest.py --publicar     <- ESTE se olvida
```

El jar que descarga el launcher **no sale del servidor**: sale del manifiesto del
pack. Subir solo al servidor deja a todos los clientes con el jar anterior, y el
síntoma es una pantalla que no existe todavía — se comporta exactamente como
debe, que es lo que despista.

> ⚠️ Y una migración que falla deja el servidor **en bucle de arranque**. Se sale
> con `kill` y luego `start`, no con `restart`.

---

## 5-bis. ⚠️⚠️ El disfraz se aplica en TRES sitios, y faltaban los tres

La tienda vendía 62 disfraces, los cobraba, y salía el Pokémon **normal**. Sin
error, sin aviso, sin nada en el log. Eran tres fallos encadenados, y cada uno
por sí solo bastaba para producir exactamente el mismo síntoma:

| Dónde | Qué faltaba |
|---|---|
| **El pack** | `CobblemonMoreCosmetics` **no estaba instalado en ningún sitio** |
| **El catálogo** | Prometía cosméticos declarados de una forma que la versión publicada no usa |
| **El cliente** | Aunque el servidor aplicara bien el aspecto, sin los modelos dibuja el Pokémon de siempre |

### El catálogo se GENERA, y ese es el arreglo de verdad

Estaba escrito a mano, con los identificadores copiados de mirar el repositorio.
Y el repositorio miente sobre la versión publicada:

```
GitHub (HEAD)      species_features   se aplica encendiendo una BANDERA
release publicada  cosmetic_items     se aplica dando un OBJETO al Pokemon
```

Son sistemas distintos. `cosmetic_items` es el nativo de Cobblemon 1.7, y se usa
con `pokemon.swapCosmeticItem(objeto)`.

> **El aviso de que esto podía pasar estaba escrito en el propio
> `Catalogo.java`** —`vendor/cobblemon` es HEAD, no 1.7.3— y aun así pasó. Un
> comentario advirtiendo de algo no comprueba nada. Generándolo del zip, el
> catálogo **no puede** prometer un disfraz que el pack no tenga.

```
python tools/gen_catalogo_cosmeticos.py <zip de CobblemonMoreCosmetics>
```

### ⚠️ El par (especie, objeto) es lo que identifica un cosmético

El identificador que inventamos aquí —`charizard_knight`— **no lo conoce
Cobblemon**. Él aplica el objeto y saca el aspecto. Así que dos cosméticos de la
misma especie con el mismo objeto serían indistinguibles: comprar uno aplicaría
el otro la mitad de las veces.

Hoy no hay ninguno de los 62, y **el generador aborta** si algún día lo hay. El
síntoma sería «a veces sale el cosmético equivocado», que nadie relacionaría con
esto.

### Los assets van DENTRO del jar de lunaneon

Mismo mecanismo que el revestido de la interfaz, y por el mismo motivo: es **el
único que ha funcionado en este proyecto**. Un `.zip` suelto en `resourcepacks/`
depende de que el jugador lo active, y `DEFAULT_ENABLED` no existe para resource
packs — lo dice el javadoc de Fabric.

```
servidor   world/datapacks/CobblemonMoreCosmetics.zip   el data/  (que objeto -> que aspecto)
cliente    lunaneon.jar!/resourcepacks/cosmeticos/      el assets/ (modelos y texturas)
```

**Solo los assets** van al jar: el `data/` ya viaja como datapack, y meterlo en
los dos sitios lo cargaría dos veces.

Es **MIT**, así que redistribuirlo está permitido — al contrario que el pack de
CobbleVerse (D-037), del que solo se guardan URL y hash.

> **Verificado en el log del arranque**, que es lo que dice que está puesto de
> verdad y no solo subido:
> ```
> Registered the cobblemon:cosmetic_items registry
> Cosmeticos: modelos de disfraces activados
> 46 data pack(s) enabled: ... [file/CobblemonMoreCosmetics.zip (world)]
> ```

---

## 5-ter. ⚠️⚠️⚠️ El disfraz se crafteaba, y eso se llevaba la tienda entera

**Lo reportó el usuario, y no lo habría encontrado ninguna prueba nuestra:** no
era un fallo del código, era el diseño del sistema que estábamos usando.

`cosmetic_items` aplica el disfraz **dándole un objeto al Pokémon**. Y el objeto
de `charizard_knight` es un `minecraft:iron_helmet`:

```
craftear un yelmo de hierro    = el disfraz de 2.500 LunaCoins, gratis
quitarlo por el menu de ellos  = y ademas te quedas el objeto
```

`cosmetic_items` **está pensado para conseguirse jugando**, y
[D-039](../../CLAUDE.md) dice exactamente lo contrario: solo con LunaCoins o en
eventos. Los dos diseños no pueden convivir, y el suyo es una puerta que no se
puede cerrar desde el mod.

> **No se vigila la puerta: se quita.** El **datapack no se instala**. Sin él,
> `cosmetic_items` no registra nada, y el objeto no aplica ni quita nada.

### El aspecto se fuerza directo

```java
pokemon.setForcedAspects(aspectos);   // ni bandera, ni objeto
pokemon.updateAspects();
```

Verificado **en el bytecode de 1.7.3** antes de escribirlo, porque de esto ya me
equivoqué dos veces leyendo el repositorio en vez del jar:

| | |
|---|---|
| `updateAspects()` | hace `aspectos = proveedores + forcedAspects` |
| `PokemonP3` | lo **guarda** — persiste entre sesiones |
| `ClientPokemonP3` | lo **sincroniza** — lo ven los demás |

**Los assets siguen incrustados en `lunaneon`**: el resolver se activa por
**aspecto** y vive en `assets/`, no en `data/`. El dibujo nunca dependió del
datapack.

### Las tres formas, en el orden en que se probaron

| | Qué pasó |
|---|---|
| `FlagSpeciesFeature` | Nada. Se leyó de GitHub (HEAD), que declara `species_features`; la versión publicada no usa eso |
| `swapCosmeticItem` | **Funcionaba** — y por eso se tardó en ver el problema. Abría el agujero de arriba |
| `setForcedAspects` | Lo que hay. Sin objeto de por medio, así que no hay nada que craftear |

> ⚠️ **Quien tuviera un disfraz puesto por el sistema viejo lo ha perdido.** Se
> vuelve a equipar desde el PokePad, sin pagar. `disfrazar()` limpia el
> `cosmeticItem` que quedara, para que no reaparezca si alguien reinstalara el
> datapack algún día.

### El campo `objeto` se quitó del catálogo

No se usa. **Un dato que sigue ahí es una invitación a volver a usarlo**, y esta
vez sabemos a dónde lleva.

---

## 5-quater. Poner y quitar, los dos desde el PokePad

El botón de un cosmético puesto decía **EQUIPADO** y no hacía nada. Un botón se
etiqueta con la **acción**, no con el estado:

```
COMPRAR      [moneda] 2500   COMPRAR
EQUIPAR      TUYO             EQUIPAR
EQUIPADO     PUESTO (verde)   QUITAR
```

Que está puesto se sigue viendo, pero en la **etiqueta de la izquierda**, que es
donde va el estado. **Quitarlo no lo devuelve al catálogo**: sigue comprado, la
posesión está en `player_cosmetics` y `desvestir()` no la toca.

> ⚠️ **QUITAR tampoco lleva ranura.** Podría mandarse la que se dibujó, pero el
> equipo puede haber cambiado desde entonces —basta con reordenarlo— y se le
> quitaría el disfraz al Pokémon equivocado. El servidor busca cuál lo lleva en
> el momento de quitarlo.

### ⚠️ Y las flechas arreglaron algo que nadie había visto

`pagina` se usaba en los tres sitios que tocaba —dibujar la rejilla, dibujar los
modelos, detectar el clic— pero **nada lo cambiaba nunca**. La tienda enseñaba
los ocho primeros de 62 y **54 eran inalcanzables**. Ni flechas, ni rueda, ni
teclas, y ningún error.

Las medidas de la banda naranja salen de **recorrer el PNG**, no de mirarlo:

```
banda calida   y = 698..745
adornos        x = 732..744, 763..774, 936..947, 966..978
huecos libres  x = 437..731, 775..935, 979..1273
```

Las flechas van en el primero y el tercero, a la misma distancia del centro
(x=860); el contador «3 / 8» va en el hueco de en medio. Con este chasis,
escribir medidas a ojo ya ha salido mal cuatro veces.

Se dibujan **apagadas en los extremos, no escondidas**: una flecha que
desaparece mueve la que queda y deja al jugador sin saber si ha llegado al final
o si ha dejado de funcionar algo.

---

## 5-quinquies. Ocho cosméticos que no se podían dibujar, y el que salía en blanco

El usuario los fue nombrando uno a uno: *«el garchomp, el gardevoir, los que
dicen sinnoh… el lucario sinnoh… hay uno que se llama operator y ni aparece,
nada en blanco»*. Eran **dos fallos distintos del pack**, y el catálogo se los
tragaba los dos:

| | |
|---|---|
| `26sinnohbundle` | Declara **seis** cosméticos —charizard, decidueye, garchomp, gardevoir, greninja y lucario con aspecto `sinnoh`— **cuyo arte no viene en el pack**: es un paquete que se vende aparte |
| `ninetales aurora` | Lo mismo, y este no lo había visto nadie |
| `pangoro_operator.json` | Pone `"pokemon": ["operator"]` — **errata suya**: debería decir `pangoro`. Por eso salía una celda en blanco: `cobblemon:operator` no existe |

### El catálogo pasa a generarse de los RESOLVERS

```
antes   data/cobblemon/cosmetic_items/       lo que el pack DECLARA
ahora   assets/.../resolvers/cosmetic/…      lo que el pack puede DIBUJAR
```

**Un resolver *es* el dibujo**: si está, se puede pintar, y lleva la especie de
verdad. Los seis del bundle no tienen resolver, y el de `operator` dice
`pangoro`. Los dos problemas se caen solos.

```
62 declarados  ->  54 dibujables  (52 especies)
```

Dos filtros, y los dos con motivo medido:

- **Solo la carpeta raíz.** `dex/` y `msd/` son variantes del *mismo* cosmético
  para formas concretas (megas). Contarlas daría tres Charizard `knight` en la
  rejilla.
- **Se exige `model`.** Una variación sin modelo es un recoloreado (shiny,
  hembra) que hereda el del cosmético base. **Comprobado que no pierde ninguno**:
  los 55 de un solo aspecto tienen modelo.

### ⚠️ Y hubo una tercera vuelta: faltaba un *poser*

`pangoro_operator` **sí** tenía resolver, modelo y textura — y aun así salía como
un bulto verde sin forma. El resolver pide `poser: cobblemon:pangoro_operator`, y
**ese fichero no viene en el pack**. Cobblemon no cae al poser base de la
especie: dibuja el modelo sin postura.

```
existe el resolver     no basta
existe el modelo       no basta
existe la textura      no basta
existe el POSER        <- la cuarta pieza, y era la que faltaba
```

Lo vio el usuario antes que ninguna comprobación nuestra, **otra vez**. Ahora el
generador exige las cuatro. `54 dibujables`.

Y el generador **imprime los que quedan fuera** en vez de callárselos: si un día
ese número cambia, es que el pack ha cambiado — y nos enteramos ahí, no porque
alguien compre un disfraz invisible.

> **Es la misma lección que 5-bis, una vuelta más:** el catálogo tiene que salir
> de lo que **existe**, no de lo que alguien **declara**. La primera vez la
> fuente equivocada fue GitHub; esta vez, un fichero del propio pack.

---

## 5-sexies. «Yo no compré el Snorlax chef»

Lo preguntó el usuario y **la respuesta salió del libro de asientos, no de la
memoria**. Se cobraron 9.500 REPORTCOINS en cuatro operaciones, y con los precios
del catálogo de entonces solo hay una combinación:

```
2500  charizard_knight
1200  eevee_valentines
1800  snorlax_chef        <- el unico articulo a 1.800
4000  mewtwo_boundary
```

Sí lo había comprado: fue una de las cuatro compras de prueba del 22-ago, con el
catálogo escrito a mano, que usaba **los mismos identificadores** que el
generado. La posesión sobrevivió al cambio de catálogo porque lo que guarda
`player_cosmetics` es el identificador, y ese no cambió.

**No era un fallo, pero la pregunta va a repetirse**, así que ahora se contesta
mirando:

```
/luna cosmeticos          (nivel 3, desde el juego)
```

Marca en gris lo que ya **no está en el catálogo** — por ejemplo, alguno de los
ocho que se acaban de retirar. Sin esa marca, el recuento de la tienda y el de
la tabla no cuadran y parece un fallo nuevo.

---

## 5-septies. El contador de páginas se veía pixelado

`texto()` escala por `alto * k / fontHeight`, y **`k` casi nunca es redondo**: con
`alto=18` sale 1,26 o 2,4 según la ventana. La fuente de Minecraft es un mapa de
bits, así que a escala fraccionaria cada píxel de letra cae a caballo entre dos
de pantalla.

**Y el contorno lo multiplica:** son cuatro copias desplazadas ±1 *en coordenadas
ya escaladas*, o sea ±1,26 px reales — eso no es un borde, es una mancha.

Se nota poco en «COMPRAR» —pequeño y sobre color plano— y muchísimo en el
contador, que es el texto más grande de la pantalla.

`textoNitido()` **redondea la escala a entero** (mínimo 1) y usa la sombra propia
de `drawText`, que va a un píxel *de pantalla*. **No sustituye a `texto()`**: el
tamaño final no es exactamente el pedido, y para algo que tiene que caber en un
hueco medido eso importa. Para una etiqueta suelta, no.

---

## 5-octies. A Rotom le faltaba la boca

En el PokePad principal sale sonriendo; en la pantalla de cosméticos, la cabeza
era un blanco liso. `pokepad_cosmeticos.png` se derivó del chasis **sin ese
trozo**.

Se recuperó **midiendo la diferencia entre los dos chasis**, no recortando a ojo:

```
diferencia en la zona de la cabeza  ->  x = 827..882,  y = 184..221   (55x37)
```

Y antes de pegarla se comprobó que **el blanco de alrededor fuera idéntico** en
las dos imágenes —0 píxeles distintos en un marco de 6 px—, porque un parche con
borde visible no aparece en una comparación de `sha1`, solo en la pantalla.

> El arte y la textura del mod son **el mismo fichero byte a byte**, así que se
> escriben los dos. Si algún día dejan de serlo, esto se rompe en silencio.

---

## 6. Lo que falta

1. ~~Que el cosmético se vea en el mundo.~~ ✅ **Sale gratis, y no por
   casualidad.** `cosmetic_items` guarda el aspecto **en el Pokémon**, no en una
   tabla nuestra: cuando sale de su Poké Ball lo dibuja Cobblemon con su aspecto,
   y lo ven todos los que tengan el resource pack — que es todo el mundo, porque
   va incrustado en un jar del pack. **Esto era el punto 1 de esta lista y era el
   trabajo más grande que quedaba**; se resolvió al dejar de inventarnos un
   sistema paralelo y usar el que Cobblemon ya tiene. `monetization.md` avisa de
   que «un cosmético sin nadie que lo vea no vale nada», y ese riesgo se cierra.
   **Falta verlo en el juego.**
2. ~~Probar una compra de verdad.~~ ✅ **Hecho el 2026-08-22**, ver *Current
   Status*. Para dar saldo: `/luna dar REPORTCOIN <cantidad>` desde el juego
   (nivel 3). **Falta probar el caso de saldo insuficiente**, que es el que
   enseña el mensaje de error.
3. **Capas, Sombreros y Auras están vacías.** Las mascotas se llenan solas con
   los 66 cosméticos MIT; esas tres hay que generarlas (D-032: se dibujan, no se
   bajan).
4. **Los precios son de relleno.** CLAUDE.md lo dice de toda la economía: se
   calibra con datos reales.
5. La categoría se llama «Mascotas» y son Pokémon disfrazados. El usuario lo
   aceptó al ver que `knight` es Charizard con armadura, pero **si algún día la
   palabra estorba, «Compañeros» era la alternativa**.

## Last Decision

**2026-08-22** — D-039: los cosméticos no se consiguen jugando, solo con
LunaCoins o en eventos. Los eventos no son un adorno de esa decisión: son la
mitad que la hace funcionar, porque si todo fuera de pago el escaparate se apaga
solo.

## Next Actions

1. El dibujado en el mundo (§6.1) — **lo único que falta para que esto sea un
   producto y no una pantalla bonita**
2. Probar el rechazo por saldo insuficiente y el estado EQUIPAR/EQUIPADO
3. El arte de las tres pestañas vacías

## Related Documents

- [Las seis reglas de dibujado](dibujado.md) · [La interfaz de cliente](interfaz-cliente.md)
- [Monetización](../economy/monetization.md)
