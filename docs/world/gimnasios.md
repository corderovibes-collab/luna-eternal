# Gimnasios

## Purpose

Cómo un jugador reta al líder de un gimnasio, cómo se evita que ocho retadores
se pisen, y por qué el combate **no** pasa por el método obvio.

## Dependencies

- [construccion.md](construccion.md) — la ciudadela, donde está la recepción
- `mod/src/main/java/net/pokereport/luna/gym/` — todo el sistema
- `rctmod` + `rctapi` — los entrenadores y el motor de combate
- `cobblemonbattlepositions` — dónde se coloca cada Pokémon en la arena

## Current Status

**Brock funciona de punta a punta en el código. Sin verificar en el juego.**
Los otros siete gimnasios están declarados y no construidos.

---

## 1. El recorrido

```
Ciudadela                        Dimensión lunaeternal:gimnasios
─────────────────────            ──────────────────────────────────────
Brock, de pie, quieto            ranura 0   MAESTRO   (no se juega aquí)
  + su Geodude al lado           ranura 1   copia     ← un retador
                                 ranura 2   copia     ← otro retador
  clic derecho                   ...
      ↓                          ranura 7   copia
  DIÁLOGO (pantalla propia)
      ↓  «estoy listo»
  se reserva una ranura ─────────→ se clona del maestro si hace falta
                                   aparece Brock en su tarima
                                   viaja el jugador a la entrada
                                        ↓  clic derecho en Brock
                                   COMBATE
                                        ↓
                                   medalla · vuelta a la ciudadela
```

## 2. Por qué se instancia

**Lo señaló el usuario antes de que se escribiera mal**: *«recuerda que
múltiples participantes retan a Brock»*. Con una sola sala y un solo Brock, el
segundo retador entra y ve el combate del primero, sin nadie contra quien
luchar.

Se puede instanciar porque **un combate de Cobblemon no necesita la sala**: el
combate es una interfaz, y la sala es la puesta en escena. Así que cada retador
entra en su propia copia.

```
x = gimnasio * 1024     SEPARACION
z = ranura   * 128      PASO_RANURA
```

### ⚠⚠ La ranura 0 es el maestro y no se juega en ella

Es donde se pega el esquema, una vez. Las demás se clonan de ella **la primera
vez que hacen falta** y la copia se queda hecha: se paga una vez por ranura, no
una vez por combate. Jugar en el maestro sería jugar sobre el original — un
bloque roto ahí estropea la plantilla de la que salen todas las demás.

### ⚠⚠⚠ Las ranuras viven en memoria, no en la base

Guardarlas tendría un fallo mudo y **permanente**: al reiniciar, las ocho
figurarían ocupadas para siempre y nadie podría retar a Brock nunca más, sin un
solo error en el log. En memoria, un reinicio las deja todas libres — que es
exactamente la verdad.

**Y hay que soltarlas en tres caminos: ganar, perder y desconectarse.** El
tercero es el que se olvida.

---

## 3. ⚠⚠⚠ Por qué el combate no usa `startBattleWith`

Es el método obvio de `TrainerMob`, y **habría hecho que el gimnasio no
funcionara**.

Antes de nada llama a `canBattleAgainst`, que comprueba **la progresión de
rctmod**: su tope de nivel, su serie y sus entrenadores requeridos. Y la
configuración real de este servidor —leída de `config/rctmod-server.toml`, no
supuesta— dice:

```toml
initialLevelCap   = 15        # y el Onix de Brock es nivel 20
initialSeries     = "empty"   # y Brock es de la serie "kanto"
allowOverLeveling = false
```

O sea que a un jugador normal le habría dicho **que no**. Y no con un error: con
un **diálogo por el chat**, que es justo lo que el usuario pidió que no hubiera.
El clic derecho habría parecido no hacer nada.

Se usa **`RCTMod.getInstance().makeBattle(mob, jugador)`**, que es público, es
lo mismo que llama rctmod por debajo, y no comprueba nada de eso.

**La puerta del gimnasio es nuestra**: cuántas medallas hacen falta es nuestro
diseño, está en nuestra base y se enseña en nuestra pantalla.

### Y la victoria se escucha en Cobblemon

`CobblemonEvents.BATTLE_VICTORY` da los actores que ganaron y los que perdieron.
Es la fuente más cercana al hecho —«este combate lo ganó este jugador»— y no
depende de la contabilidad de rctmod, que es justo la que hemos rodeado.

⚠ Se filtra por **quién estaba retando**: en el servidor hay combates a todas
horas, y ninguno de ellos da una medalla. Sin ese filtro, ganarle a un Rattata te
haría campeón de Ciudad Plateada.

---

## 4. Las posiciones

**Son desfases, no coordenadas absolutas.** Absolutas parecerían más simples y
en la ranura 3 el jugador aparecería fuera de su copia.

| | Absoluto en el maestro | Desfase |
|---|---|---|
| Entrada | `48 78 17.26` | `(48, 14, 17.26)` |
| Tarima del líder | `48 72 40.45` | `(48, 8, 40.45)` |

> ⚠⚠ **La tarima está seis bloques más abajo que la entrada**, y no es un error
> de medida: se entra por arriba y se combate abajo. Si algún día alguien
> «corrige» uno de los dos para que cuadren, el jugador aparecerá dentro del
> suelo.

En la ciudadela, medido por el usuario:

| | |
|---|---|
| Brock | `-137.95 69 49.37` |
| Su Geodude | `-137.544 69 52` |

⚠ **Geodude** porque es el primero de su equipo (Geodude 16, Bonsly 16, Cranidos
18, Onix 20), leído del datapack. Y porque entre los dos puntos hay **2,66
bloques**: un Onix mide casi nueve de largo y se comería la sala.

⚠ El giro se pasa por comando (`/luna gimnasio ciudadela <grados>`): hacia dónde
mira Brock depende de cómo quede la sala, y eso no se sabe desde el código.

---

## 5. ⚠⚠⚠ Brock no puede retar solo, y casi lo hace

La configuración del servidor trae:

```toml
forceBattleOnSight    = true
forceBattleMaxDistance = 8.0
```

O sea que un Brock puesto en la plaza **retaría a quien pase por delante**, sin
tocarlo, en mitad de la ciudadela.

Eso vive en `ForceIntoBattleGoal`, que es un **Goal** — y los Goals no corren con
`setAiDisabled(true)`. Comprobado abriendo el jar (`world/entities/goals/`), no
supuesto.

La lista completa de lo que se le apaga:

| | |
|---|---|
| `setAiDisabled(true)` | no anda, no mira, **no reta solo** |
| `setInvulnerable(true)` | no se le puede pegar… |
| etiqueta `luna_decorativo` | …**ni en creativo**, que es lo que la bandera no cubre |
| `setSilent(true)` | callado |
| `setPersistent(true)` | no se lo lleva el despawn de rctmod |

⚠ La etiqueta de decorativo no es pereza: de ella cuelga la única protección que
cubre el creativo, y **aquí todos los que construyen son operadores en
creativo**. La lección ya está pagada una vez; esto es reutilizarla.

---

## 6. Los bloques de Battle Position

`cobblemonbattlepositions` (MIT, ya instalado en servidor y cliente) coloca a los
Pokémon y a los entrenadores en la arena. Se ponen **una vez en el maestro** y
las ocho copias los heredan al clonarse.

| Bloque | |
|---|---|
| `player_pokemon_position` | **obligatorio** |
| `trainer_pokemon_position` | **obligatorio** |
| `player_stand_position` | opcional — teletransporta al jugador |
| `trainer_stand_position` | opcional — teletransporta al líder |

> ⚠⚠ **Sin los dos obligatorios el combate no falla: sale mal y se calla.**
> Cobblemon coloca los Pokémon donde caiga —encima de una grada, dentro de una
> pared, detrás del jugador— y no hay ni un aviso.
> `/luna gimnasio brock posiciones` lo comprueba.

### ⚠⚠⚠ Y busca el bloque más cercano en un radio de 48

Leído de `config/cobblemonbattlepositions.json`, no supuesto. Las ranuras van a
**128** una de otra y son copias idénticas, así que:

> si los bloques quedaran muy al norte de una sala de 86 de fondo, **desde el
> fondo el bloque de la ranura siguiente estaría más cerca que el propio** — y el
> combate colocaría los Pokémon en la sala de otro jugador. Sin error.

Lo comprueba `posiciones`, que es **donde están los dos números que hacen falta**:
cuánto mide la sala y dónde están los bloques. En el autotest sería comparar
constantes contra constantes, o sea la confianza falsa que ya nos mordió una vez.

⚠ El mod **también reserva la arena por su cuenta** (`ArenaKey` = dimensión + las
dos posiciones de Pokémon), y como cada ranura está en otra Z, cada copia es una
arena distinta para él. El instanciado encaja con su diseño sin tocar nada.

---

## 6-bis. ⚠⚠⚠ Medir la sala: dos fallos con la misma raíz

`Arenas.medir` recorre el maestro y devuelve la caja de lo que no es aire.
`clonar` la llama **antes de copiar**, así que si miente, se copia mal.

Ha mentido dos veces en dos días, y las dos veces por lo mismo.

### Primera: el límite era el número que se validaba

El barrido se limitaba a `PASO_RANURA`. Medí el gimnasio y salió **«64 de
fondo»** — que era exactamente el límite del barrido.

> **Medir con la regla que intentas validar solo te devuelve la regla.**

### Segunda: barrer ancho se come las copias

Lo arreglé barriendo `SEPARACION / 2`. Y en cuanto existió una copia, el barrido
se la comió: el gimnasio mide 86, la copia de la ranura 1 empieza en 128, y la
medición dio **214** — «el maestro más su copia».

Y no era un número feo y ya: la siguiente copia habría sido de 214 de fondo y
habría escrito **encima de las ranuras 1 y 2**.

### La raíz, que es la misma

> **Las dos versiones usaban un LÍMITE en vez de mirar LO QUE HAY.**

Una sala tiene suelo, así que **sus capas de Z están todas ocupadas**; entre una
copia y la siguiente hay **aire**. Hoy corta en el aire (`AIRE_QUE_CORTA = 8`),
que es un dato del mundo y no un número nuestro.

Y **dice lo que se deja fuera**, en vez de ignorarlo en silencio:

```
Gimnasio brock: medido hasta z=86 (86 de fondo). Hay 86 capas más allá,
separadas por aire: son las copias de las ranuras y NO se cuentan.
```

### Y lo destapó una comprobación escrita para otra cosa

`/luna gimnasio brock posiciones` existía para vigilar que las copias no se
robaran los bloques de posición unas a otras. Con la medición mal, escupió un
número imposible —«del fondo de una sala al bloque de la siguiente hay **−20**»—
y eso fue lo que mandó a mirar.

⚠ Verificado antes de tocar nada, no supuesto:

```
execute if blocks 40 70 40  55 85 55  40 70 168 all
  → "LA COPIA EN z=128 EXISTE Y ES IDENTICA"
y entre z=86 y z=127 no hay un solo bloque
```

### `limpiarranuras`

`clonar` **no copia el aire**: recorre el maestro y escribe lo que no es aire.
Así que un bloque que se *quite* del maestro **se queda en la copia para
siempre**, y volver a clonar no lo arregla.

`/luna gimnasio <cual> limpiarranuras` las borra. Es lento a propósito —46.354
bloques y 2,8 s de retraso, medidos en vivo— y por eso no se llama solo.

---

## 7. Las medallas

**No son un objeto**, y es orden del usuario: *«obtiene la medalla pero no
física, la obtienen ya en el PokePad»*.

Es la misma decisión que los trajes (V023) y por el mismo motivo: un objeto se
tira, se pierde al morir y **se puede regalar** — y una medalla regalada deja de
decir «yo gané a Brock», que es lo único que una medalla significa.

```sql
PRIMARY KEY (player_id, gym)
```

> ⚠⚠⚠ **Esa clave es la regla.** «Una medalla por gimnasio y por jugador» no lo
> dice una comprobación en Java: lo dice la clave, así que ganarla dos veces falla
> **en la base** venga de donde venga la petición — dos combates que acaban a la
> vez, un cliente modificado, un reintento. Misma decisión que `clan_member`.

⚠⚠ **La máscara se compone al leer, no se guarda.** Guardarla sería más compacto
y no se podría consultar: «cuánta gente tiene la de Brock» pasaría a ser un
barrido con aritmética de bits, y «cuándo la ganó» no cabría en ninguna parte.

⚠⚠ **La caché no es una optimización, es un requisito**: la máscara la pregunta
el diálogo en el momento del clic, en el hilo del servidor. Se lee una vez al
entrar.

### ⚠⚠⚠ Tres listas de medallas eran una sola

El orden estaba escrito a mano en `Gimnasio.TODOS`, en `PokePadScreen` y en la
pantalla nueva, y **nada las obligaba a coincidir**. Desordenadas, ganar a Brock
encendería la medalla de Misty **sin dar ningún error**: el jugador vería una
medalla que no ha ganado y no vería la que sí.

Hoy las pantallas leen de `Gimnasio.insignias()`. **Es mejor que una comprobación
que lo detecte: así no puede pasar.**

---

## 8. Los comandos

```
/luna gimnasio                    dónde está cada uno y cuántas ranuras libres
/luna gimnasio brock              te lleva a su maestro
/luna gimnasio brock plataforma   pone el ancla 9×9 (el bloque de oro = origen)
/luna gimnasio brock medir        cuánto mide lo construido
/luna gimnasio brock lider        pone a Brock en el maestro, para mirarlo
/luna gimnasio brock posiciones   comprueba los bloques de Battle Position
/luna gimnasio ciudadela [grados] pone a Brock y su Geodude en la plaza
/luna gimnasio ciudadela quitar
/luna gimnasio reclonar           al cambiar el maestro
```

> ⚠ **`reclonar` hace falta mientras se construye.** Una ranura se clona una sola
> vez por arranque, así que mover a Brock o poner los bloques de posición **no
> llega a las copias ya hechas** — y eso no da ningún error: da un gimnasio en el
> que unos jugadores ven una cosa y otros otra.
>
> Solo olvida la marca: los bloques viejos siguen en el mundo y el clonado no
> borra el aire, así que lo que se quitó del maestro se queda en la copia. Para
> una copia limpia de verdad hace falta reiniciar.

---

## 9. Next Actions

| | |
|---|---|
| **Poner los cuatro bloques de Battle Position** en el maestro de Brock | y comprobar con `posiciones` |
| **Verificar en el juego** | el diálogo, el viaje, el combate y la medalla |
| Los otros siete gimnasios | falta construirlos y medir sus dos puntos |
| La pantalla del icono `gyms` | hoy está bloqueada; el usuario la ha pedido «para tener en cuenta» |
