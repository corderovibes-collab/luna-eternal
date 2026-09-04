# Cobblemon Cards — auditoría del mod antes de adoptarlo

## Purpose

Decidir **qué entra, qué se apaga y qué hay que parchear** de
[CobblemonCards](https://github.com/Howlite-UI/CobblemonCards) antes de que
toque el servidor. Todo lo de aquí está **leído del código y del jar
publicado**, no del README — que en tres puntos dice cosas que el código
contradice.

## Dependencies

- [CLAUDE.md](../../CLAUDE.md) — D-007 · D-008 · D-014 · D-017 · D-020 · D-028 · D-039 · P3 · P4 · P10
- [monetization.md](../economy/monetization.md) — los cuatro niveles y la línea roja
- [client-pack.md](../technical/client-pack.md) — cómo entra un mod en el pack

## Current Status

**Auditado, y NUESTRA mitad ya construida (2026-09-02).** El jar se ha
descargado y abierto; el repositorio se ha clonado y leído.

| | |
|---|---|
| Icono `cartas` | ✅ en la página 1, hueco 10 (§4.4) |
| Pantalla `CartasScreen` | ✅ las tres zonas, con su arte |
| `CartasService` + `V028` | ✅ los dos relojes y el registro de entregas |
| Protocolo | ✅ `PedirCartas` · `EstadoCartas` · `AbrirSobre` |
| Config | ✅ [`config/cobblemon-cards.json`](../../config/cobblemon-cards.json) |
| **El mod** | ✅ **instalado**, y es una versión **nuestra parcheada** (§6) |
| Los cuatro parches | ✅ aplicados y verificados en el jar |
| Manifiesto | ✅ publicado — `manifest-263f619293.json` |
| Servidor | ✅ jars y config subidos, **inertes hasta reiniciar** |
| **Reinicio** | ⏳ **pendiente de aviso a los jugadores** |

⚠ Hasta que el servidor reinicie, la pantalla **se abre y funciona** y los tres
botones salen apagados diciendo «las cartas no están activas en este servidor».
Es a propósito: se comprueba contra el registro, no con una bandera.

---

## 1. Qué es, y de dónde sale

| | |
|---|---|
| Proyecto | `cobblemon-cards` (Modrinth `9asBGJMf`) · 17.303 descargas |
| Versión | **1.0.4 Fabric**, publicada 2026-07-13 |
| Jar | 23.429.081 bytes · sha1 `767b7d93ee1342852cc92b89e4d163f3e142b368` |
| Minecraft | 1.21.1 — **la nuestra** |
| Cobblemon | `>=1.6.0` — tenemos 1.7.3 |
| Licencia | **CC0-1.0** en el `LICENSE` y en el `fabric.mod.json`; Modrinth la lista como MIT |
| Lados | `environment: "*"`, y Modrinth dice `server_side: required` |

> **La licencia no es un problema, y conviene dejarlo escrito.** CC0 es dominio
> público: uso comercial, obra derivada y redistribución, todo permitido y sin
> atribución obligatoria. Es lo contrario del caso que descartó CobbleVerse en
> D-006, y por eso **este mod sí se puede parchear** — que es justo lo que hace
> falta (§4).

**Aun así no lo redistribuimos**: el manifiesto guarda URL y hash y el jar lo
baja el jugador del CDN de Modrinth, igual que los otros 147. Un fichero menos
del que responder.

---

## 2. Lo que trae, medido

### 2.1 · El registro

```
49 objetos    22 sobres (uno normal + 9 generaciones + 12 tipos)
              6 archivadores (cuero, hierro, oro, diamante, netherita, álbum)
              la CARTA, el polvo y sus dos empaquetados, el disco de
              estructura, el Instant-Dex, la CardDex, el boleto de God Pack
 8 bloques    proyector holo, mini, avanzado, cabina, recicladora,
              restauradora, estación de calificación, saco de polvo
 7 blockstates
18 recetas    todas las máquinas y los seis archivadores. La CARTA y los
              SOBRES no se craftean
20 logros
2.920 texturas de carta — arte propio para cada especie, normal y shiny
```

> ⚠⚠⚠ **57 entradas en registros QUE SE SINCRONIZAN.** Esto es exactamente lo
> del 2026-08-27: un tipo de contenedor nuevo dejó seis minutos sin que nadie
> pudiera entrar. **Hay que avisar a la gente ANTES de reiniciar, no después**,
> y publicar el manifiesto **antes** que el reinicio del servidor.

### 2.2 · Las dependencias — **ya están todas**

Comprobado contra el manifiesto vivo (`manifest-d5cc954ea7.json`, 140 mods):

| Pide | Tenemos | |
|---|---|---|
| Cobblemon `>=1.6.0` | 1.7.3 | ✅ |
| fabric-language-kotlin `>=1.13.0` | 1.13.13 | ✅ |
| Architectury `13.0.6+` | 13.0.8 | ✅ |
| Cloth Config `15.0.140+` | 15.0.140 | ✅ exacta |
| Accessories `1.1.0-beta.53+` | 1.1.0-beta.53 | ✅ exacta |
| MidnightLib `1.9.2+` | 1.7.5 suelto — **demasiado viejo** | ✅ **viaja DENTRO del jar** |

> ⚠⚠ **MidnightLib se salvó por el jar anidado (JiJ).** El nuestro es 1.7.5 y
> pide 1.9.2, así que sobre el papel esto era un `Incompatible mods found!` en
> el arranque — el fallo del `letmedespawn`/`almanac`, que solo aparece al
> reiniciar. Pero `META-INF/jars/` del propio cards trae
> `midnightlib-1.9.2+1.21.1-fabric.jar`, y **Fabric se queda con la versión más
> alta de cada módulo**. Es la misma regla que `gen_modpack.py` ya aplica.
>
> Se comprobó abriendo el jar, no leyendo el README. **No hay que instalar
> nada nuevo.**

> ⚠⚠ **Y TRINKETS NO HACE FALTA, aunque el README lo exija.** El README lista
> Trinkets 3.10.0 como requisito de Fabric. En las 129 clases del mod
> **`trinkets` no aparece ni una vez**: lo único que usa es `accessories`, y
> detrás de un `isModLoaded`. Importa porque Trinkets es justo el mod que el
> 2026-08-17 dejó a todo el mundo fuera con `StructFieldException:
> [exported_slots]` y que se retiró del cliente a propósito. **Adoptar este mod
> NO obliga a devolverlo.**

### 2.3 · Peso

22,3 MB (20 de sonidos, 8,5 de texturas antes de comprimir). El pack pasa de
**434 a ~456 MB, un +5 %**. P10 lo tolera; queda anotado.

---

## 3. Lo que HACE, y aquí empiezan los problemas

### 3.1 · De dónde salen las cartas — cuatro fuentes, tres no son nuestras

| Fuente | Dónde | Por defecto |
|---|---|---|
| Capturar y derrotar | `ModEvents` → `POKEMON_CAPTURED` y `BATTLE_FAINTED` | 1 % |
| **Cofres** | `LootTableEvents.MODIFY` | 2 %, **en TODAS las tablas de cofre** |
| **Mercader errante** | `registerWanderingTraderOffers` | 5 ofertas en el nivel 1, 2 en el 2 |
| Sobres | se abren y sueltan cartas | — |

> ⚠⚠⚠ **LA INYECCIÓN EN COFRES ES MUCHO MÁS ANCHA DE LO QUE PARECE.** El filtro
> es `path.contains("chest")` — no «tablas de cofre», sino *cualquier tabla cuya
> ruta contenga la palabra*. Y va contra el diseño de **Tesoros** (D-020), que
> es la forma pensada de que un cofre dé algo. Dos sistemas repartiendo premios
> por el mismo sitio, uno de ellos sin que lo decidiéramos.

> El **mercader errante** cobra en polvo, y el polvo sale de reciclar cartas.
> O sea que **es un sumidero, no una fuente** (P3): ese se queda.

### 3.2 · ⚠⚠⚠ LAS CARTAS DAN PODER, Y ESO CRUZA LA LÍNEA ROJA

Un archivador equipado en una ranura de Accessories aplica las estadísticas de
las cartas que lleva dentro. `CardStat` tiene **26 valores**:

```
MINING_SPEED  MOVEMENT_SPEED  ATTACK_DAMAGE  ATTACK_SPEED
LUCK  ARMOR  MAX_HEALTH  CARD_DROP_CHANCE
+ 18 multiplicadores de aparición, uno por tipo
```

y el config los escala con `globalStatMultiplier = 10.0` y
`maxSpawnBoostMultiplier = 100.0`.

> **Hoy no se vende, así que hoy no rompe nada.** Rompe **el día que un sobre
> entre en la tienda**, que es lo obvio que va a pasar: en ese momento un objeto
> de pago pasa a dar daño, armadura, vida y tasas de aparición. Eso es **T4 — la
> línea roja de D-007 y D-014**, la misma por la que D-019 rechazó los
> Modificadores de estadística.
>
> ⚠⚠ **Y los 18 de aparición son peores que los de combate.** Los de combate no
> tocan un combate Pokémon (los gimnasios ya igualan nivel por `adjustLevel`),
> pero un ×100 de apariciones de un tipo **vacía la exploración y se lleva por
> delante las Cazas**, que es un sistema entero.

**Se apaga con una línea**: `enableCardStats = false`. Las cartas pasan a ser
**colección pura** — que es T1 · identidad, venta libre, y lo que las hace
compatibles con LunaCoins sin discutir nada.

### 3.3 · ⚠⚠⚠ CARTAS DE POKÉMON QUE AQUÍ NO EXISTEN

`BoosterLootTable` construye su lista con `PokemonSpecies.getImplemented()` —
**las 1.025**. Y hay sobres `gen3`…`gen9` **como objetos del registro**, con su
filtro por número de Pokédex ya escrito.

> Nuestro datapack de generaciones (`gen_generaciones.py`) apaga **pools de
> aparición** y **vacía la Pokédex**. No marca ninguna especie como no
> implementada — no tenía por qué, porque hasta hoy nada más leía esa lista.
>
> ⚠⚠⚠ **Resultado: un servidor que se anuncia Kanto + Johto repartiendo cartas
> de Miraidon.** No da ningún error. Es el mismo fallo que las 29 especies de
> Gen 3-8 que se colaron seis días por los spawns de `mega_showdown`, y
> contradice **D-017**, que es la decisión de identidad más fuerte del proyecto.

### 3.4 · ⚠⚠ `/givecard` Y `/customboosterset` SON NIVEL 2

```java
.requires(source -> source.hasPermission(2))
```

Y **nuestros constructores son OP nivel 2** por D-028, para Axiom y WorldEdit.
O sea que cualquiera de ellos puede acuñar una carta Mythic Grade 10. Mientras
una carta no valga dinero da igual; en cuanto valga, es una imprenta.

### 3.5 · ⚠ NO HAY ESPAÑOL, Y NO SE ARREGLA SOLO CON UN `lang`

El jar trae `en_us.json` y `fr_fr.json`. Falta `es_es`. Y además hay **texto
inglés escrito a pelo dentro de las pantallas**, que un fichero de idioma no
alcanza:

```
BinderScreen       "Click: -1 page | Shift+Click: -10 pages"
CardCabinetScreen  "Type a page number and press Enter"
CardWorkshopScreen "Search..."  "Rarity: "  "Shiny: "  "Holo: "
CardRestorerScreen "Stored Dust: "  "Grade "
```

### 3.6 · ⚠ RUIDO EN EL LOG

`ModEvents` escribe **tres líneas `LOGGER.info` por captura y por derrota**,
incluida `"Drop failed."` — o sea el 99 % de las veces. Con combates en marcha
es ruido constante en un log que se usa para diagnosticar.

### 3.7 · Dónde guarda los datos

Attachments de Fabric en el `playerdata` del mundo: `discovered_cards`,
`opened_boosters`, `has_guaranteed_god_pack`. **No pasa por MariaDB**, así que
`backup_bd.py` no lo cubre — pero `backup.py` sí, porque va dentro del mundo.
No hay que hacer nada; hay que **saberlo** el día que se restaure.

### 3.8 · Los mixins no chocan con los nuestros

Suyo: `HumanoidModel.setupAnim` en `TAIL`, pone los brazos al enseñar una carta.
Nuestro: `BipedEntityModel.setAngles` en `TAIL`, esconde la capa exterior de la
piel bajo el traje. **Es el mismo método** (nombres Mojang frente a Yarn), los
dos en `TAIL` — pero tocan campos distintos, así que se componen. Sin conflicto.

---

## 4. Lo que hay que hacer, en orden

### 4.1 · Config — sin tocar código

```properties
enableCardStats           = false   # §3.2 · la línea roja
enableBoosterChestSpawn   = false   # §3.1 · choca con Tesoros
cardDropChance            = 1.0     # se queda: es lo que ata carta y juego
allowFakemonCards         = false   # ya viene así
```

### 4.2 · Parches al jar — cuatro, y CC0 los permite

| # | Qué | Por qué |
|---|---|---|
| 1 | Filtrar la lista de especies a **dex ≤ 251** y retirar los sobres `gen3`…`gen9` | §3.3 · D-017 |
| 2 | `hasPermission(2)` → `4` en los dos comandos | §3.4 · D-028 |
| 3 | `LOGGER.info` → `debug` en `ModEvents` | §3.6 |
| 4 | `es_es.json` + sacar los literales ingleses a claves | §3.5 |

### 4.3 · El icono del PokePad

> **La integración sale casi gratis, y es un hallazgo del código:**
> `OpenBinderPayload` es un **record vacío C2S** registrado globalmente, y
> `findActiveBinderLocator` busca el archivador en accesorios → mano → mano
> secundaria → **todo el inventario**. O sea que el cliente puede pedir que se
> abra el archivador **sin una sola línea de servidor nuestra**.

⚠ **Pero si no tienes archivador, `locator` es `null` y no pasa absolutamente
nada** — un icono que no hace nada y no dice por qué. Por eso el icono **no
manda el paquete a ciegas**: abre una pantalla nuestra (`CARTAS`) que enseña
cuántas llevas descubiertas —el cliente ya las tiene, se las manda
`SyncDiscoveredCardsPayload`— y un botón **ABRIR ARCHIVADOR**, apagado y con el
motivo al lado si no tienes ninguno. Es la regla de Viajes: *un botón gris sin
explicación parece roto*.

⚠ Y todo lo que toque su jar va detrás de `isModLoaded("cobblemon-cards")`,
igual que `hayEntrenadores()` con rctmod: sin esa guarda, un servidor sin el mod
se cae al arrancar con un `NoClassDefFoundError` que no nombra al culpable.

### 4.4 · Sitio en la rejilla — ✅ hecho (2026-09-02)

**Decisión del usuario: `cartas` a la primera página y `wiki` a la segunda.**
`cartas` ocupa el hueco 10, que era el de la wiki, y la wiki baja al final.

|  | |
|---|---|
| Página 1 | pokedex · cosmeticos · trabajos · misiones · warps · clan · gts · tienda · tesoros · **cartas** · cazas · kits · mochila · gyms · explorar |
| Página 2 | curar · **wiki** |

Tiene sentido más allá del gusto: `wiki` lleva `abierta = false` desde que
existe el Pad —nunca ha llevado a ningún sitio— y estaba gastando uno de los
quince huecos que se ven al abrir.

> ⚠⚠ **Y hubo que tocar `OrdenPad` para que esto valiera para todo el mundo.**
> El jugador puede reordenar sus iconos y eso se guarda; al releerlo, una
> aplicación nueva **se añadía al final**. O sea que a quien hubiera tocado su
> orden alguna vez le habría pasado justo lo contrario de lo pedido —cartas en
> la página 2 y la wiki donde estaba—, sin ningún error y solo a una parte de la
> gente. Ahora una aplicación nueva entra **en su sitio de fábrica**, y lo que el
> jugador movió sigue movido.

> ⚠⚠⚠ **Y falta el PNG: `cartas.png` no existe todavía, así que el autotest
> está EN ROJO a propósito.** Una celda dibuja **su propio icono aunque esté
> bloqueada** —el candado es solo para huecos sin aplicación—, así que sin el
> arte saldría un **cuadro magenta en la pantalla principal**. Es lo que pasó el
> 2026-08-23 por el otro lado, y solo se vio abriendo las pantallas.
>
> La comprobación nueva —*toda aplicación del PokePad tiene su icono dentro del
> jar*— lo caza antes de desplegar. Se pone verde sola en cuanto el PNG entre en
> `mod/src/client/resources/assets/lunaeternal/textures/gui/pokepad/`.

---

## 5. La pantalla CARTAS — decisión del usuario (2026-09-02)

**Tres zonas, y las tres son la misma acción: abrir un sobre.**

| Zona | Cuesta | Cada cuánto | Qué puede salir |
|---|---|---|---|
| Diaria | **gratis** | 1 / 24 h | **poco común hacia abajo, y nada más** |
| De Plata | Plata | 1 / 24 h | **poco común hacia abajo, y nada más** |
| Dorada | LunaCoins | **sin límite** | cualquier cosa · **el único con boleto divino** |

### ⚠⚠⚠ Y hoy los tres daban exactamente lo mismo

Leído del código: **todo sobre reparte 3 comunes + 1 poco común + 1 RARA O
MEJOR GARANTIZADA**, venga de donde venga. O sea que pagar LunaCoins no
compraba nada distinto de lo que te dan gratis una vez al día — y el boleto
divino se tiraba en todos con la misma probabilidad global.

**La quinta carta es toda la diferencia, y con eso basta** (decisión del
usuario, 2026-09-03):

| | Dorado | Diario y Plata |
|---|---|---|
| 5.ª carta | rara garantizada — dentro, **80 % rara · 15 % épica · 5 % legendaria** | **poco común, siempre** |
| Boleto divino | **2 %** | **nunca** |

> ⚠⚠ **Es un TOPE DURO, no una probabilidad baja, y por eso no va a config.**
> «Lo gratis nunca da raras» es un invariante del diseño: en cuanto es un
> número editable, alguien lo pone al 50 y la regla se cae sin que salte nada.
> Lo único que queda en config es la tasa del boleto, que sí es un número de
> ajuste.

> ⚠⚠ **La calidad viaja en `minecraft:custom_data` del propio sobre**, no en
> una tabla nuestra ni en tres objetos distintos. Tres sobres serían tres
> entradas más en un registro que se sincroniza —tres razones más para echar a
> quien no actualice— más tres texturas, tres modelos y tres traducciones.
> `custom_data` es de vainilla: existe siempre y no registra nada.
>
> ⚠ Y escrita **dentro del objeto** en vez de en una fila: un sobre se guarda,
> se deja en un cofre y se abre tres días después. Si la calidad viviera en la
> base, un sobre movido dejaría de saber lo que es.
>
> ⚠ Sin marca, un sobre vale como **dorado**: uno que llegue por otra vía no se
> degrada en silencio.

> ⚠⚠⚠ **ESTO DECIDE §3.2, YA NO ES UNA RECOMENDACIÓN.** «Sobres sin límite por
> LunaCoins» significa que una carta se compra con dinero real tantas veces como
> se quiera. Si las cartas dieran estadísticas —daño, armadura, vida y ×100 de
> apariciones—, eso sería **comprar poder sin techo**: T4, la línea roja de
> D-007 y D-014, y la misma por la que D-019 rechazó los Modificadores.
>
> Con `enableCardStats = false` la zona de LunaCoins es **T1 · identidad** y no
> hay nada que discutir. **La tercera zona y las estadísticas encendidas no
> pueden convivir.**

**Los dos relojes son NUESTROS, no del mod.** El mod no tiene ningún cooldown;
el diario y el de Plata se guardan en MariaDB, como `kit_claim` y como el de 30
minutos de `HealService`. Son **dos marcas distintas**: fundirlas en una haría
que cobrar la gratis gastara también la de pago.

**La pantalla entrega el OBJETO sobre, no abre su pantalla.** `BOOSTER_PACK` es
un `ItemStack`: se da y el jugador lo abre con clic derecho, que es cuando sale
la pantalla de apertura *del mod* — con sus animaciones y sus 20 efectos
holográficos, que no vamos a rehacer. Conducir su `OpenBoosterPayload` desde
nuestro código sería más trabajo para un resultado peor.

> ⚠ Y el inventario puede estar lleno. El sobre **se suelta al suelo** en ese
> caso, como hace el propio mod — pero **el reloj ya se ha gastado**, así que el
> orden importa: se comprueba el hueco *antes* de marcar el cobro.

> ⚠⚠ **Los tres importes quedan sin fijar a propósito**, igual que los de la
> tienda: no se tocan precios hasta que haya un análisis general de economía.
> Van en constantes, en un solo sitio, para que aplicarlo sea cambiar tres
> números.

---

## 6. ⚠⚠⚠ POR QUÉ HAY UN FORK, Y NO ERA EL PLAN

El plan era instalar el jar publicado (1.0.4, 17.303 descargas) y parchearlo
solo si hacía falta. **Al ir a hacerlo, la premisa se cayó.**

### Lo que se midió

Se bajó el jar del CDN y se abrió con `javap`:

```
1.0.4 publicado     CobblemonCardsConfig  ->   9 campos
HEAD (2026-08-15)   CobblemonCardsConfig  ->  44 campos
```

Y entre los **35 que faltan** está `enableCardStats` — **el interruptor que
apaga lo único aquí inadmisible**: que una carta comprada con dinero real dé
daño, armadura, vida y hasta ×100 de apariciones (§3.2, y la zona de LunaCoins
sin límite lo convierte en T4).

Peor: el `BinderSpawnModifier` de 1.0.4 es la versión que **transforma
entidades ya generadas**, que es exactamente lo que su propio CHANGELOG
describe como *«rompía el equilibrio del juego»* y por lo que la reescribieron
después.

### O sea que la elección real no era la que parecía

| | |
|---|---|
| Lo que parecía | «probado» contra «sin publicar» |
| Lo que era | **sin interruptor y con el mecanismo malo** contra **con interruptor y con el bueno** |

Se eligió lo segundo. **Y la contrapartida es real y hay que decirla: se está
ejecutando código sin publicar.** Si algo se comporta raro con las cartas, el
primer sospechoso es esto.

### Cómo se mantiene

`cards/parchear.py` clona, parchea y compila. El commit va **fijado**
(`c02aafb5`, 2026-08-15) y no a una rama:

> ⚠⚠ **El repositorio no tiene ni una etiqueta** — `git tag -l` sale vacío. Por
> eso «la versión 1.0.4» no se puede pedir por nombre, y por eso el fork va
> clavado a un SHA: con `main` a secas, dos compilaciones separadas por un día
> darían jars distintos y nadie se enteraría hasta que algo fallara.

Los cuatro parches, verificados **en el jar compilado**, no en el fuente:

| # | Qué | Cómo se comprobó |
|---|---|---|
| 1 | `maxNationalDex` (251) | el campo existe en la clase de config |
| 2 | comandos a nivel 4 | `iconst_4` en el bytecode de `GiveCardCommand` |
| 3 | `LOGGER.debug` | cambio de fuente |
| 4 | `es_es.json` | 32 KB dentro del jar, **387 de 387 claves** |

> ⚠ El tope de generación va como **config y no a fuego**: el día que se abra
> Gen 3 es cambiar un número en `config/cobblemon-cards.json`, no recompilar.
>
> ⚠⚠ Y cubre **la lista de emergencia**, que traía `rayquaza` (384) y
> `greninja` (658). Se usa cuando el registro de especies aún no está listo —o
> sea **en el arranque, cuando nadie mira**— así que un tope sin ella habría
> sido un tope con una puerta abierta.

---

## 7. La habilidad de aparición — decisión del usuario (2026-09-03)

Cada carta se puede activar (sneak + clic derecho con ella en la mano) para
que **su especie** tenga más probabilidad de aparecer salvaje: **5 minutos**
de ventana, **1 hora** de espera entre activaciones, una sola carta activa por
jugador.

### ⚠⚠⚠ El techo sale de pesos REALES de Cobblemon, no de un número inventado

Se extrajo `spawn_pool_world` del jar de Cobblemon 1.7.3 que corre este
servidor (no del README, no de memoria) y se leyeron los pesos de verdad:

```
común       4,5 – 9,0
poco común  0,3 – 84   (mucha varianza YA de fábrica)
rara        1,5 – 7,5
```

Con esa vara de medir, un techo de **×1,3 a ×2,7** según la **rareza de la
carta** es un movimiento real —se nota buscando esa especie— sin salirse del
rango que el propio juego ya maneja entre sus propias entradas. «No que le
aparezca de golpe» (orden del usuario) se cumple: incluso en mítica y nota 10,
sigue siendo cuestión de minutos de búsqueda, no un spawn garantizado.

| Rareza de la carta | Suelo (sin calificar) | Techo (nota 10) |
|---|---|---|
| Común | ×1,12 | ×1,30 |
| Poco común | ×1,20 | ×1,50 |
| Rara | ×1,32 | ×1,80 |
| Épica | ×1,44 | ×2,10 |
| Legendaria (carta) | ×1,56 | ×2,40 |
| Mítica (carta) | ×1,68 | ×2,70 |

> ⚠ **«Legendaria»/«mítica» aquí son rarezas de CARTA** (una entre seis, que
> le toca a la carta por sorteo al abrir un sobre), **no que el Pokémon sea un
> legendario de verdad.** Una carta mítica de un Rattata es tan válida como
> una común: es una etiqueta de lo buena que salió la carta, no de qué
> Pokémon es.

### ⚠⚠⚠ Y los legendarios de verdad quedan fuera — comprobado, no supuesto

Se comprobó en el jar: **los once legendarios que ya tiene este proyecto en
Tesoros (Mewtwo, Mew, Lugia, Ho-Oh, Celebi y el resto) no tienen ni una
entrada en `spawn_pool_world`.** No aparecen salvajes en absoluto en el
Cobblemon base — solo existen aquí vía el cofre de D-020, que ya trae
probabilidades públicas, piedad e idempotencia.

Por eso la habilidad **no necesita una lista de especies prohibidas**: el
multiplicador actúa sobre un peso que ya existe, y si el peso es cero, cero
por cualquier cosa sigue siendo cero. Una lista habría que mantenerla a mano
—y una lista sin mantener es donde se esconden los fallos de verdad, la misma
lección de las tres listas de medallas—; esto se protege solo, con los datos
del propio juego. Si algún día se decide dar spawn salvaje a esos once (una
decisión aparte, del calibre de D-020), la habilidad los cubriría sin tocar
una línea aquí.

### ⚠⚠ La estación de calificación vuelve a tener motivo

Al retirar la restauradora (§6), calificar se había quedado en «pon un número
bonito». Con la nota afinando el multiplicador dentro de su techo —del suelo
al techo, lineal de nota 0 a 10—, calificar una carta legendaria o mítica
**sube de verdad** la fuerza de su habilidad. El polvo vuelve a tener sus dos
sumideros con sentido: el disco (qué especie y qué rareza) y la calificación
(cuánto aprieta el techo).

### ⚠⚠ Sin compilar contra el jar de cartas

Qué especie y qué rareza tiene una carta se lee del `custom_data` de
vainilla que `cards/parchear.py` espeja en **cada carta al crearse**
(`luna_especie`/`luna_rareza`/`luna_nota`) — no del `DataComponentType`
propio del mod, que exigiría una dependencia de compilación que
deliberadamente no tenemos (§6). El espejo se engancha con un ayudante
(`CardMirror.mirror(stack, data)`) y una sustitución por patrón que cubre los
diez sitios donde el mod crea una carta —calificar, dar por comando, escanear,
abrir un sobre, el sobre personalizado— sin tocar cada uno a mano.

Resolver la especie y mirar si es un legendario de verdad **sí** usa una
dependencia real: la de Cobblemon, que este mod ya tiene desde hace meses en
media docena de sitios (`HealService`, `StarterService`...). No es una
dependencia nueva, es la de siempre.

### Cómo se activa

Sneak + clic derecho con la carta en la mano. Sin sneak, se deja pasar
(`PASS`) para que el examinador de cartas del propio mod (clic derecho a
secas) siga funcionando exactamente igual que siempre — no se toca ni se
duplica su comportamiento.

## Last Decision

Las cuatro que mandan —apagar las estadísticas (§3.2), recortar a
Kanto+Johto (§3.3), las tres calidades de sobre (§5) y el techo de la
habilidad ligado a pesos reales (§7)— **no son gusto: son D-014, D-017 y
decisión directa del usuario, con los números medidos contra el juego**.

## Next Actions

1. ⏳ **Reiniciar el servidor** — jars y config subidos, inertes hasta
   arrancar. Va **con aviso previo a los jugadores**: son 49 objetos y 8
   bloques en registros que se sincronizan, más una migración nueva (V029)
2. Verificar en el juego: las tres zonas de sobres, que ninguna carta pase del
   251, y **la habilidad**: activar una carta, comprobar el mensaje, y que
   activar una segunda antes de la hora se rechace
3. `ECO-005` sigue vivo — las Marcas continúan sin gastarse en nada
