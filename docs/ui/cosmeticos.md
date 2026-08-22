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

**2026-08-22 · funcionando en el juego, verificado por el usuario.**

Se abre desde el PokePad, dibuja los Pokémon en 3D sin titilar, el catálogo
viene del servidor y comprar/equipar están escritos de punta a punta.

```
pantalla       CosmeticosScreen.java        4 pestañas · rejilla 4x2 · preview
3D             Mascota3D.java               Cobblemon + criaturas de Minecraft
catálogo       cosmetics/Catalogo.java      12 Pokémon + 8 criaturas
compra         cosmetics/CosmeticsService   transacción + idempotencia
difusión       cosmetics/Difusion.java      quién lleva qué, a todos
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

Y la **clave de idempotencia se deriva** del jugador y el cosmético, no de un
UUID nuevo. Con un UUID por petición, dos clics son dos claves distintas y la
idempotencia no protege de nada.

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

## 6. Lo que falta

1. **Que el cosmético se vea en el mundo.** El servidor ya difunde quién lleva
   qué (`Difusion.java`); falta el dibujado en el cliente. **Es lo que convierte
   esto en producto**: hoy solo lo ve su dueño, en la tienda, y
   `monetization.md` avisa de que «un cosmético sin nadie que lo vea no vale
   nada». Decisión tomada: **solo cliente, sin entidad** — cero coste de tick y
   cero interferencia con combates y capturas.
2. **Probar una compra de verdad.** El usuario tiene 0 LunaCoins, así que el
   botón siempre va a rechazar. Hace falta saldo de prueba.
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

1. El dibujado en el mundo (§6.1)
2. Saldo de prueba y una compra completa
3. El arte de las tres pestañas vacías

## Related Documents

- [Las seis reglas de dibujado](dibujado.md) · [La interfaz de cliente](interfaz-cliente.md)
- [Monetización](../economy/monetization.md)
