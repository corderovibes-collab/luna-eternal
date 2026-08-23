# El árbol de misiones y los oficios

## Purpose

**Qué está construido, qué falta y qué trampas ya se pagaron.** Si algo aquí no
coincide con el código, gana el código y se corrige esto.

## Dependencies

- [`dibujado.md`](dibujado.md) — **las ocho reglas. Leerlas antes de tocar
  cualquier pantalla del Pad**
- [`cosmeticos.md`](cosmeticos.md) — de donde salen la geometría, las pestañas y
  las flechas que esta pantalla reutiliza
- CLAUDE.md **P3** (sumideros antes que fuentes), **P4/D-014** (la progresión no
  se vende), **D-039** (los cosméticos no se consiguen jugando)

## Current Status

**2026-08-23 · las tres pantallas funcionando y verificadas en el juego.**

```
Inicial     InicialScreen.java      6 iniciales · se abre SOLA
Misiones    MisionesScreen.java     28 misiones · 6 cadenas · árbol
Trabajos    TrabajosScreen.java     8 Vías · paginado
oficios     progression/Oficios*    minar, pescar, cosechar, criar
avisos      ui/Aviso.java           toast + barra + chat + sonido
autotest    136 → 156
```

---

## 1. ⚠️⚠️ La lección del día, que salió cuatro veces

**Un lado cambia algo y el otro no se entera.** Cuatro fallos distintos, cuatro
síntomas distintos, la misma raíz:

| Síntoma | Qué pasaba |
|---|---|
| «No veo mis cosméticos al reconectar, pero los otros sí» | El servidor difundía al recibir *su* `JOIN`; entre eso y que el cliente tenga mundo hay una **ventana** |
| La pantalla del inicial **dejaba atrapado** al jugador | `conceder()` es asíncrono y **parece síncrono**: encola y vuelve, así que preguntar justo después leía el estado anterior |
| «Ese comando no sirve» | Borraba la fila en la base y el cliente seguía con su copia |
| Reiniciar el inicial dejaba la misión puesta | Son **dos tablas**: `kit_claim` y `quest_progress` |

> **La regla que queda: si el servidor cambia un estado que el cliente dibuja, el
> servidor lo reenvía.** Y si es el cliente quien sabe cuándo está listo —porque
> depende de que su mundo haya cargado— **que pregunte él**.

Ninguno de los cuatro dio un error. Los cuatro se veían como «no pasa nada».

---

## 2. El inicial: lo que faltaba no era código

`feature-gap-analysis.md` lo describía hace meses: **un jugador nuevo no tenía
ningún Pokémon**, y sin Pokémon nada de lo construido servía.

Y `StarterService` estaba **escrito y probado desde el principio** — marca
primero, entrega después, deshace si la entrega falla, da XP de Entrenador y
avanza la misión. Lo único que faltaba era **quién llamara a `conceder()`**,
desde que D-026 borró la interfaz vieja.

### ⚠️ Se abre sola, y esa es la mitad del arreglo

Un icono más en el PokePad no habría servido: **quien acaba de entrar no sabe que
el PokePad existe**.

Y **lo decide el servidor**, leyendo `kit_claim`. La alternativa —«¿tengo algún
Pokémon?»— daría falso positivo con quien guarde su equipo en el PC y falso
negativo con quien acabe de perder el suyo, y las dos formas de equivocarse son
malas.

### ⚠️⚠️ Se abre desde el tick, no al recibir el paquete

Cuando llega `Iniciales`, el jugador está casi siempre en la **pantalla de carga
del terreno**, así que `currentScreen == null` es falso y la apertura se perdía.
Comprobarlo cada tick cuesta dos comparaciones y además respeta lo que el jugador
tuviera abierto.

### ⚠️⚠️ Y dejó atrapado a un jugador

La pantalla no se puede cerrar sin elegir —está justificado: cerrarla deja al
jugador donde estaba el problema— pero eso convierte cualquier fallo en una
trampa. Y hubo uno: esperaba una confirmación que ya había pasado de largo.

**La causa está arreglada y aun así hay salida:** a los 6 s sin respuesta se
suelta el botón, se dice por qué, y `ESC` vuelve a funcionar.

> **Una pantalla que no se puede cerrar tiene que tener siempre una salida.** El
> fallo de hoy era nuestro; el siguiente puede ser un corte de red, y para quien
> está dentro el resultado es el mismo.

---

## 3. El árbol: ya existía y nadie lo había visto

`Quest` tiene `chain` (la pestaña), `requires` (la arista) y `order` **desde
PHASE 5**. El tutorial ya ramificaba —`t3_pokedex` y `t4_tienda` cuelgan las dos
de `t2_captura`— y no se veía porque no había pantalla.

```
28 misiones · 6 cadenas
tutorial · entrenador · coleccionista · oficios · comercio · diaria
```

### ⚠️ El reparto se calcula, no se escribe

La columna es la **profundidad** —cuántos `requires` hay hasta la raíz— y **no el
campo `order`**: dos misiones con el mismo orden pueden colgar de padres
distintos. Coordenadas a mano obligarían a recolocar media pestaña al añadir una
en medio, y nadie lo haría: acabarían solapadas.

### ⚠️⚠️ Y el árbol se encoge solo si no cabe

Con el nodo a 72 px, la cadena `oficios` —cuatro misiones a la misma
profundidad— pedía **754 px de alto en una pantalla de 698**. La primera versión
**no lo detectaba**: dibujaba la cuarta fila fuera del marco, y desde dentro del
juego eso se ve como *«faltan misiones»*.

Recortar el diseño hasta que quepa lo de hoy sería arreglar el caso y no el
problema. Medido: cinco cadenas al 100 %, `oficios` al 85 %. Por debajo de 0,55
el icono deja de reconocerse; si algún día se llega ahí, lo que hay que hacer es
**partir la cadena en dos**, no seguir encogiendo.

### El nodo lleva icono, y el nombre va debajo

Dentro de 84 px solo cabía el nombre partido en dos líneas de letra diminuta, y
el resultado eran **cajas grises indistinguibles**: había que *leer* cada una.
Que es lo contrario de lo que un árbol tiene que conseguir.

El icono sale del **tipo de objetivo** —un pico para minar, una caña para pescar,
una Poké Ball para capturar— así que dos misiones parecidas se parecen. Eso
también informa.

### ⚠️ Los tres invariantes que importan

El catálogo es un JSON que se edita a mano, y sus tres fallos posibles son **los
tres invisibles**:

- un `requires` que apunta a una misión que no existe,
- un `requires` que **cruza de cadena**,
- un **ciclo**.

Ninguno da error al cargar. Y un ciclo **cuelga el dibujado**: el juego se
congelaría sin decir por qué, así que el recorrido lleva además tope de saltos.

---

## 4. Los oficios

> **Una Vía desbloquea contenido; un oficio da dinero.**

Por eso **solo los oficios pagan**. Si subir de Vía también pagara, la progresión
sería una fuente de ingresos, y P3 dice sumideros antes que fuentes.

```
nivel   50 · 150 · 400 · 1.000 · 2.500 de Plata
los tres al máximo →  100 LunaCoins, una vez
```

### ⚠️ La escala se bajó diez veces, y con motivo medido

Estaba en `500·1.500·4.000·10.000·25.000` — **41.000 de Plata por exprimir un
oficio**. Puesto al lado de la tienda, que va de 8 (roca) a 3.000 (Revivir), eso
son **trece Revivir por oficio**.

> **El dinero de minar son las menas, no el bono.** Lo que se lleva un jugador
> por picar ya está en lo que pica; esto es un extra por constancia, y un extra
> que supera a la actividad deja de serlo.

### ⚠️ Y con 100 LunaCoins no se compra ningún cosmético

El más barato del catálogo es un sombrero a **1.200**. No es un error —una moneda
premium escasa es defendible— pero **traslada todo el peso a los eventos**. D-039
decía que los eventos son «la mitad que hace funcionar la decisión»; con esta
cifra son prácticamente **la única vía gratuita real**.

Dos palancas si algún día se quiere cambiar: subir el premio a ~1.200, o bajar el
precio de algún cosmético de entrada. Las dos son decisiones de producto.

### ⚠️ La minería excluye el creativo

Un constructor con Axiom rompe miles de bloques en segundos: sin ese filtro, **la
ciudadela sería la mina más rentable del servidor**. Ahora mismo el trabajo del
proyecto *es* construirla, así que el caso raro aquí no es raro.

Y el evento es `AFTER`, no `BEFORE`: el «antes» se dispara aunque el bloque no
llegue a romperse, y pagaría por trabajo no hecho.

### ⚠️ Cocina no está, y es deliberado

Cobblemon 1.7 trae olla de cocina (`CookingPotMenu`, `CookingPotRecipe`) pero **no
publica ningún evento** para ella — se revisaron sus 98. Engancharla pide un mixin
dentro de su código, y este mod no tiene mixins.

Declarar el oficio sin su enganche dejaría uno que **nunca da XP**: el fallo
silencioso de siempre.

### ⚠️ V012 hizo falta para tres nombres

`player_path.path` es un **ENUM de MariaDB**, no un VARCHAR. Insertar `'MINERO'`
en el enum viejo guarda la **cadena vacía** con un aviso que nadie mira. Y el ENUM
guarda el **índice**: reordenar los cinco viejos convertiría a todos los
Exploradores en Entrenadores, en silencio.

> Al contrario, `quest_progress` guarda `quest_id VARCHAR(48)` y **no** el tipo de
> objetivo, así que añadir tipos de misión no necesita migración.

---

## 5. Los avisos

`ui/Aviso.java` centraliza las tres vías, y las tres hacen falta:

| | |
|---|---|
| **Toast** (esquina) | Se ve estés donde estés, y no lo tapa el chat de nadie |
| **Barra de acción** | Sale donde ya miras cuando estás picando |
| **Chat** | **Persiste.** Lo único que puedes releer para saber cuánta Plata te dieron |

⚠️ **El toast solo llega a quien tiene el mod.** El chat y la barra son de
vanilla, así que quien no lo tenga se entera igual. Por eso no se manda solo el
toast, y por eso va en su propio `try/catch`.

⚠️ **Cada toast lleva su propio objeto de tipo.** Con el de por defecto, dos
subidas seguidas se pisan y solo se ve la última.

⚠️ **El fondo es el de los logros de vanilla**, a propósito: el jugador ya sabe
qué significa ese marco, y reutilizarlo hace que un oficio se lea como un logro
sin explicar nada.

---

## 6. Lo que falta

1. **Tienda y curar**, que son lo que más se nota: hoy un jugador captura pero
   **no puede comprar Poké Balls ni curar su equipo**. Y cuatro misiones
   (`t4_tienda`, `t5_gts`, `m1_comprar`, `m2_vender`) no se pueden completar sin
   ellas.
2. **GTS, kits, cazas y viaje** — la lógica está viva y probada, falta la cara.
3. **Cocina**, que necesita un mixin (§4).
4. **Los números están sin calibrar**, como toda la economía. `/luna economia`
   dirá si sobra o falta cuando alguien juegue de verdad.

## Comandos de prueba

```
/luna via <VIA> <xp>            nivel 3 · inyecta progresión
/luna reiniciarinicial          nivel 4 · borra la marca Y la misión
/luna reiniciarmision <id>      nivel 4 · autocompleta las 28
```

> ⚠️ Los de nivel 4 **no devuelven la recompensa ya cobrada**: quien reinicie una
> misión pagada se queda con el dinero y puede volver a cobrarla. Es un agujero
> deliberado, y por eso están donde no los alcanza nadie que no sea dueño del
> servidor.

## Last Decision

**2026-08-23** — Los oficios pagan Plata y las Vías no. Una Vía desbloquea
contenido; un oficio da dinero. Y el premio por completarlos todos son 100
LunaCoins: pocos a propósito, con la consecuencia de que los eventos pasan a ser
la única vía gratuita real para conseguir cosméticos.

## Next Actions

1. **La tienda** (§6.1) — desbloquea cuatro misiones y es lo que más se nota
2. **Curar**, por lo mismo
3. Calibrar los números con `/luna economia` cuando haya juego real

## Related Documents

- [Las ocho reglas de dibujado](dibujado.md) · [La tienda de cosméticos](cosmeticos.md)
- [Modelo de progresión](../progression/progression-model.md) · [Monetización](../economy/monetization.md)
