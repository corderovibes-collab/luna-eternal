# Combate de gimnasio — auditoría y diseño

## Purpose

Cómo se pelea contra un líder de gimnasio: paridad de nivel, paridad de equipo,
adaptación del rival al jugador, y dificultad de la IA.

## Dependencies

- `docs/world/gimnasios.md` — la sala, las ranuras y el recorrido
- `CLAUDE.md` §Gimnasios · §Medallas
- rctmod / rctapi (`modCompileOnly`) · Cobblemon 1.7.3

## Last Decision

2026-08-30. Nivel por gimnasio (tabla del usuario), equipo espejo, y el líder se
recompone en cada combate contra lo que trae el jugador **sin dejar de ser de su
tipo**.

---

## 1. CURRENT STATE

`Combate.empezar` hace esto, y nada más:

```java
RCTMod.getInstance().makeBattle(mob, jugador);
```

y `makeBattle`, leído del bytecode, hace:

```java
TrainerPlayer  tp  = registry.getById(trainerManager.getTrainerId(jugador), …);
TrainerNPC     tn  = registry.getById(mob.getTrainerId(), …);
TrainerTeam  team  = trainerManager.getData(mob).getTrainerTeam();
tn.setEntity(mob);
battleManager.startBattle(List.of(tp), List.of(tn),
                          team.getBattleFormat(), team.getBattleRules());
```

El equipo del líder **no se le pasa**: sale del `TrainerNPC` que está registrado
en el `TrainerRegistry`, y ese se construyó **una vez** al cargar el datapack.

El de Brock, leído del datapack real que hay puesto en el servidor
(`COBBLEVERSE-RCT-DP-v20.zip`, `data/rctmod/trainers/kanto_brock.json`):

| | especie | nivel | naturaleza | habilidad | objeto | movimientos |
|---|---|---|---|---|---|---|
| 1 | geodude  | 16 | bashful | sturdy      | — | protect · rocktomb · rockthrow · bulldoze |
| 2 | bonsly   | 16 | mild    | rockhead    | focus_sash | block · rockthrow · flail · copycat |
| 3 | onix     | 20 | bashful | sturdy      | rocky_helmet | rocktomb · bulldoze · sleeptalk · rockslide |
| 4 | cranidos | 18 | impish  | moldbreaker | — | takedown · rocktomb · rocksmash · headbutt |

IVs a 31 y EVs 100 PS / 40 velocidad. Bolsa: 2 Full Restore, `maxItemUses: 2`.
IA: `"type": "rct"` con `moveBias 1 · switchBias 0.5 · statMoveBias 1 ·
itemBias 0.8 · maxSelectMargin 0.15`. Formato `GEN_9_SINGLES`.

---

## 2. PROBLEM

Cinco huecos, y ninguno da error:

1. **No hay paridad de nivel.** Un jugador de nivel 60 entra con un Pokémon y
   barre a Brock. La medalla se gana sin combatir.
2. **No hay paridad de equipo.** Seis contra cuatro.
3. **El equipo del líder es fijo.** La primera vez es un combate; la segunda es
   una lista de la compra. Y como es el mismo para todos, se comparte por chat.
4. **La IA la elige el datapack**, no nosotros, y el gimnasio no puede subir la
   dificultad al avanzar por las medallas.
5. **En el mundo no se lee el nivel por ninguna parte.** El jugador descubre que
   viene grande cuando ya está dentro.

---

## 3. ROOT CAUSE

**Un entrenador de rctmod es un fichero de datos, y un fichero de datos es
estático por definición.** Todo lo que tenga que reaccionar a lo que trae el
jugador —el número de Pokémon, sus tipos, su nivel— no puede vivir ahí.

El error de fondo sería intentar arreglarlo *dentro* del datapack: se pueden
editar los cuatro Pokémon de Brock, pero no se puede escribir «trae los mismos
que el jugador».

---

## 4. OPTIONS

### A · Editar el datapack por gimnasio

Bajar el Onix de 20 a 15 y ya. **Se descarta**: arregla el nivel del líder y no
toca ninguno de los otros cuatro problemas. El jugador de nivel 60 sigue
barriendo.

### B · Mutar el array de `TrainerNPC.getTeam()`

`getTeam()` devuelve **el campo**, no una copia — comprobado en el bytecode
(`getfield team; areturn`). Así que se puede sobrescribir posición a posición.

**Se descarta, y por dos motivos que parecen uno solo hasta que se miran:**

- ⚠ **La longitud del array es fija.** Brock tiene cuatro; un jugador con seis no
  cabe. La paridad de equipo es imposible por construcción.
- ⚠⚠⚠ **El `TrainerNPC` es COMPARTIDO.** Hay uno por identificador en el
  registro, así que dos jugadores retando a Brock a la vez **escribirían el mismo
  array**. El segundo le cambiaría el equipo al primero en mitad del combate, y
  eso no daría ningún error: daría un combate en el que salen Pokémon que no
  estaban. Es la misma familia que las ranuras — y las ranuras existen justo
  porque este servidor sí instancia.

### C · Construir un `TrainerNPC` propio por combate ✅

Todo lo que hace falta es **público**:

```java
JTO<BattleAI> ia = JTO.of(() -> new StrongBattleAI(pericia));
TrainerModel modelo = new TrainerModel(nombre, ia, bolsa, equipo);
TrainerNPC npc = registry.registerNPC("luna_" + gimnasio + "_" + uuid, modelo);
npc.setEntity(mob);
UUID id = battleManager.startBattle(List.of(tp), List.of(npc), formato, reglas);
```

Da control de todo: especies, cantidad, niveles, naturalezas, habilidades,
movimientos, IVs, EVs, objetos, bolsa e IA. Y **un NPC por combate** es lo que
hace que dos retadores no se pisen.

---

## 5. RECOMMENDED SOLUTION

**Opción C**, con la paridad de nivel resuelta por el motor y no por nosotros.

### 5.1 · La paridad de nivel la hace Cobblemon

`BattleFormat` tiene `setAdjustLevel(int)`, y rctapi lo respeta con dos
interruptores de `BattleRules`:

```java
var formato = new BattleFormat("cobblemon", BattleType.SINGLES, reglas, 9, 0);
formato.setAdjustLevel(gimnasio.nivel());
var reglas = new BattleRules.Builder()
        .withAdjustPlayerLevels(true)
        .withAdjustNPCLevels(true)
        .withHealPlayers(true)
        .withMaxItemUses(2)
        .build();
```

> ⚠⚠⚠ **Y NO TOCA LOS POKÉMON DEL JUGADOR. Esto se comprobó en el bytecode
> antes de escribir una línea, porque si fuera falso el sistema le bajaría el
> nivel a la gente de verdad y no habría vuelta atrás.**
>
> `BattleManager.toBattlePokemons(trainer, copiar)` con `copiar = adjustLevel > 0`
> hace `original.clone(true, null)`, le aplica `BattleCloneProperty` y
> `UncatchableProperty`, y construye `new BattlePokemon(original, clon, …)`.
> Después `initParty` llama a `setLevel` sobre **`getEffectedPokemon()`**, que es
> el clon.
>
> El Pokémon real del jugador no se toca. La copia se tira al acabar.

⚠ `adjustLevel` **iguala, no acota**: un Pokémon de nivel 5 sube a 15 igual que
uno de 60 baja. Eso es exactamente lo que pidió el usuario —«todo en igualdad y
mismo nivel»— y hay que decirlo en la pantalla, porque un jugador que ve subir a
su inicial va a pensar que es un fallo.

### 5.2 · La paridad de equipo

El líder saca **tantos como el jugador**, de 1 a 6. Ni uno más ni uno menos.

⚠ Se cuentan los que **no están debilitados**: si no, alguien entraría con cinco
KO y un titular y el líder sacaría seis.

### 5.3 · La adaptación, y su límite

De cada combate el servidor mira el equipo del jugador —especies, tipos, y si
pega por físico o por especial— y **elige del repertorio del líder** los que
mejor le responden, con el conjunto de movimientos que mejor cubre lo que trae.

> ⚠⚠⚠ **EL LÍDER NO DEJA DE SER DE SU TIPO, Y ESTE ES EL LÍMITE QUE NO SE CRUZA.**
>
> Un rival generado *solo* para contrarrestar es un espejo, no un gimnasio: si el
> jugador trae Agua y Brock responde con Planta, Brock ha dejado de ser Brock. Lo
> único que hace un gimnasio memorable es que **sabes a qué vas**.
>
> Lo que se adapta es **nivel, cantidad, quién de los suyos sale, qué movimientos
> lleva, qué habilidad, qué EVs y qué objeto**. La lista de especies es de roca y
> se queda de roca.

⚠ Y el repertorio se declara **en el código nuestro**, no en el datapack: el
datapack sirve la plantilla canónica —la del anime— y nosotros añadimos las
alternativas del mismo tipo y tramo. Así, si mañana se actualiza CobbleVerse, lo
suyo sigue siendo suyo.

### 5.4 · La IA

`StrongBattleAI(int pericia)`, la de Cobblemon. Leído del bytecode:

```java
public final boolean checkSkillLevel() {
    if (skill == 5) return true;              // siempre juega bien
    return Random.nextInt(100) < skill * 20;  // si no, el 20 % por punto
}
```

O sea que **la pericia es 0..5 y es literalmente el porcentaje de turnos en que
juega óptimo**. Es la palanca de dificultad, y sube por tramo de gimnasio:

| tramo | pericia | qué significa |
|---|---|---|
| Brock → Surge | 3 | juega bien el 60 % de los turnos |
| Erika → Blaine | 4 | el 80 % |
| Giovanni y los campeones | 5 | **siempre** |

⚠ El defecto de rctapi es **2**, o sea el 40 %. Los primeros gimnasios suben a 3
a propósito: la paridad de nivel ya quita la ventaja del jugador, así que si
además la IA fallara más de la mitad de los turnos, Brock volvería a ser un
trámite por el otro lado.

### 5.5 · El cartel flotante

Encima de cada líder, un `TextDisplay` con nombre, tipo, nivel del combate y
medallas que hace falta. **No es `setCustomName`**: eso es una línea y aquí hacen
falta cuatro.

---

## 6. DEPENDENCIES

| | |
|---|---|
| `rctapi` | `TrainerRegistry.registerNPC` · `TrainerModel` · `PokemonModel` · `JTO.of` · `BattleManager.startBattle` · `BattleRules.Builder` |
| `Cobblemon` | `BattleFormat.setAdjustLevel` · `StrongBattleAI` · `PokemonSpecies` · `ElementalType` |
| datapack | los identificadores de entrenador. **Los campeones existen**: `kanto_champion_blue` y `johto_champion_lance`, leídos del zip del servidor |
| medallas | `kanto_league_trophy` y `johto_league_trophy` **ya vienen** en `CobbleverseBadges-1.3.jar`, que todos tienen instalado. No hay que dibujar nada |

---

## 7. RISKS

| ⚠ | Riesgo | Qué se hace |
|---|---|---|
| ⚠⚠⚠ | **El registro se llena.** Un `TrainerNPC` por combate y nadie los borra = fuga de memoria que crece con cada reto | `unregisterById` al acabar, **en los tres caminos**: ganar, perder y desconectarse. Es la misma lista que las ranuras |
| ⚠⚠⚠ | **Dos retadores comparten NPC** si el identificador no es único | El id lleva el UUID del jugador |
| ⚠⚠ | **Una especie mal escrita.** `PokemonModel` no valida al construirse | `RCTErrors.check()` lanza al registrar, y el autotest recorre el repertorio entero contra `PokemonSpecies` |
| ⚠⚠ | **Un movimiento que esa especie no puede aprender** no da error: sale un Pokémon con menos ataques | El autotest lo comprueba contra los datos de Cobblemon |
| ⚠ | `adjustLevel` **sube** a los de nivel bajo | Se dice en la pantalla del reto |
| ⚠ | El equipo del jugador se lee en el hilo del servidor | Es memoria (`PlayerPartyStore`), no base de datos |

---

## 8. IMPLEMENTATION PLAN

**Fase 1 — Brock, completo.** Tabla de niveles de los 18, los dos campeones, el
motor adaptativo, la IA, el cartel flotante, el Onix de la ciudadela y hacia
dónde mira.

**Fase 2** — el repertorio de los otros siete de Kanto y su sala construida.

**Fase 3** — Johto y los dos campeones.

⚠ La tabla de niveles entra **entera en la fase 1** aunque solo Brock se pueda
jugar: un número que falta es un número que alguien inventa después.

---

## 9. TEST PLAN

En `/luna autotest`, y las que importan:

- cada gimnasio tiene nivel, y **los niveles suben**: si dos empataran o uno
  bajara, el orden de las medallas dejaría de significar nada
- el nivel del líder cabe en 1..100
- **cada especie del repertorio existe** en `PokemonSpecies`
- **cada movimiento lo puede aprender esa especie**
- **cada habilidad es de esa especie**
- el repertorio de un líder es **todo de su tipo** (el invariante de §5.3)
- el repertorio tiene al menos 6, o no se puede llenar un equipo de 6
- la pericia está en 0..5
- las 18 insignias existen y no se repiten
- el identificador de entrenador de los 18 existe **según rctmod**

---

## 10. DOCUMENTATION CHANGES

- este documento
- `CLAUDE.md` §Gimnasios: la tabla de niveles y las tres trampas nuevas
- `docs/world/gimnasios.md`: el cartel y el repertorio
