# Despliegue de generaciones

## Purpose

Definir cómo se abre el contenido Pokémon: empezar con Kanto y Johto y añadir
generaciones con el tiempo.

## Dependencies

- [`../analysis/cobblemon-audit.md`](../analysis/cobblemon-audit.md) §4-bis
- [`../game-design/vision.md`](../game-design/vision.md)

## Related Documents

- [`../world/worlds.md`](../world/worlds.md) · [`../progression/progression-model.md`](../progression/progression-model.md)

## Current Status

**IMPLEMENTADO** (2026-08-11) y **CORREGIDO** (2026-08-22). Datapack generado y
desplegado: **251 especies activas, 608 spawns apagados**, y desde el 22-ago
**la Pokédex también está recortada**. Generador en `tools/gen_generaciones.py`.

⚠️ Carga confirmada, **efecto en el juego sin verificar**: `/checkspawn`
requiere un jugador conectado.

> ⚠️⚠️ **El 2026-08-22 se descubrió que llevaba seis días incompleto.** Eran 583
> spawns y son 608: faltaban **25 ficheros que añaden mods**, no Cobblemon. Ver
> §3-ter — es la parte de este documento que más importa si vuelves a tocar esto.

## Last Decision

D-017 · la decisión de **no bloquear las evoluciones que cruzan** (§3-bis) · y
la del **2026-08-22: la Pokédex oculta lo bloqueado en vez de enseñarlo con
candado** (§4), que revoca el diseño anterior.

---

## 1. La decisión

**Al lanzamiento: Kanto (001-151) y Johto (152-251). 251 especies.**
Las siguientes generaciones se añaden progresivamente.

Cobblemon trae las 1 025 de fábrica, así que esto es **quitar**, no añadir. Y
quitar es la decisión difícil, porque parece que se ofrece menos.

---

## 2. Por qué es mejor, y no una limitación

### 2.1 · Con 1 025 especies, ninguna importa

Encontrar un Pokémon entre mil es estadística. Entre 251 es un acontecimiento.
La rareza no es una propiedad del Pokémon: **es una relación con el total**.

Esto es exactamente lo que sostiene `ECO-001`: si la riqueza vive en los
Pokémon, cada uno tiene que valer algo. Con mil especies el mercado se
fragmenta tanto que casi nada tiene precio.

### 2.2 · La Pokédex se puede completar

251 es una meta alcanzable en meses. 1 025 no lo es para nadie que no juegue
a diario durante un año. **Una meta imposible no motiva: se ignora.**

### 2.3 · Contenido nuevo sin producir contenido

Cada generación añadida es una actualización mayor, con evento, y **ya está
hecha** — solo hay que activarla. Es el recurso más barato que tenemos para
mantener vivo el servidor durante años.

```
Lanzamiento   Kanto + Johto        251
+4-6 meses    Hoenn                386
+4-6 meses    Sinnoh               493
…
```

Cada apertura es una razón para que vuelva quien se fue.

### 2.4 · Se puede equilibrar de verdad

Diseñar rarezas, spawns y precios para 251 especies es un trabajo que se puede
hacer bien. Para 1 025, no: se haría por encima y se notaría.

### 2.5 · El inicial recupera su peso

Elegir entre 6 iniciales (Kanto + Johto) es una decisión. Entre 27, es un menú.

---

## 3. Cómo se hace técnicamente

Verificado en el jar de Cobblemon 1.7.3
([cobblemon-audit.md](../analysis/cobblemon-audit.md) §4-bis): cada fichero de
`spawn_pool_world` tiene un campo **`enabled`** — aparece en las 824 entradas.

```json
{ "enabled": false, "spawns": [ ... ] }
```

**Un datapack propio sobrescribe los ficheros de spawn de las especies que aún
no toca y las apaga.** Nada de borrar archivos ni tocar el jar.

| Ventaja | Por qué importa |
|---|---|
| **Reversible** | Activar una generación es cambiar un valor |
| No rompe actualizaciones | Cobblemon puede actualizarse; nuestro datapack sigue encima |
| Los datos siguen ahí | Modelos, movimientos y evoluciones existen desde el día uno |
| Se puede hacer por lotes | Un script genera los 774 ficheros, no se editan a mano |

> Coherente con la práctica del proyecto anterior que conservamos: **nada se
> escribe a mano, todo se regenera desde un script.**

### Lo comprobado

- [x] **23 evoluciones cruzan** fuera de Kanto/Johto — auditadas una a una
- [x] Nombres con caracteres raros normalizados (`farfetch'd`, `porygon-z`,
      `mr. mime`…). **Sin esto, 4 especies de generaciones posteriores
      —porygonz, mimejr, jangmoo, hakamoo— se habrían quedado activas**
- [x] Comprobación cruzada: cero fugas entre los 823 ficheros
- [x] La carpeta `herds/` solo tiene a Bulbasaur (Gen 1): no hay fuga por ahí
- [x] **Los spawns que añaden los mods** (2026-08-22, §3-ter) — 25 más
- [x] **Ningún fichero de spawn mezcla** especies dentro y fuera de rango: son
      0, comprobado. Es lo que rompería el criterio de «la primera especie
      manda» y obligaría a editar spawns en vez de apagarlos enteros
- [ ] Que un Pokémon apagado no aparece por incursión, pesca ni huevo
- [ ] Que la cría no produce especies apagadas

---

## 3-ter. ⚠️ No basta con leer el fuente de Cobblemon

**Esta es la lección cara de este documento.**

La primera versión del generador leía solo `vendor/cobblemon`, y durante seis
días el datapack pareció correcto. El 2026-08-17 entró CobbleVerse (D-037) con
mods que meten spawns **suyos** en el **mismo namespace**
`data/cobblemon/spawn_pool_world/`, que el generador no miraba:

| Mod | Spawns | Especies que aparecían |
|---|---|---|
| `mega_showdown` | 24 | Castform, Burmy, Wormadam, Mothim, Cherubi, Cherrim, Snover, Abomasnow, Rotom, Audino, Darumaka, Darmanitan, Oricorio, Rockruff, Lycanroc, Minior, Blipbug, Dottler, Orbeetle, Duraludon |
| `cobblemon-additions` | 1 | Hatenna, Hatterene, Hattrem, Liepard, Purrloin |

O sea **29 especies de Gen 3-8 apareciendo en un servidor que se anuncia como
Kanto + Johto**. Y nadie se enteró, porque **no hay nada que avise**: el
datapack se genera igual de bien, solo que incompleto.

Lo que se cambió, y por qué así:

- El generador **lee también los jars instalados** y respeta la ruta tal cual
  viene — sobrescribir exige la **misma** ruta. Como todos usan el namespace de
  Cobblemon, el mecanismo de siempre los tapa sin inventar nada.
- **Aborta** si no encuentra la carpeta de mods, en vez de publicar un datapack
  con agujeros. Un fallo ruidoso es mejor que seis días de silencio.

> **La regla general:** cada mod nuevo que toque Cobblemon puede abrir un
> agujero aquí, y no lo va a decir. Regenerar el datapack es parte de instalar
> un mod, no una tarea aparte.

---

## 3-bis. Las 23 evoluciones que cruzan — decisión

Apagar el spawn **no** impide evolucionar. Podría haberlas bloqueado; **no lo
hago, y es deliberado.**

Esas 23 especies pasan a existir **únicamente por evolución**, nunca por
aparición natural. Eso las convierte en las más difíciles del servidor: hay
que tener la base, saber el requisito y cumplirlo.

El caso que lo resume:

```
ursaring → ursaluna    requiere LUNA LLENA y de noche
```

Es literalmente el pilar de [vision.md](../game-design/vision.md) §3.1 —
*saber cuándo es la progresión*— implementado de fábrica por Cobblemon.
Bloquearlo sería tirar identidad a la basura.

Las otras 22: ambipom, dudunsparce, sylveon, leafeon, glaceon, electivire,
farigiraf, gliscor, lickilicky, magmortar, magnezone, mismagius, honchkrow,
mamoswine, annihilape, rhyperior, kleavor, weavile, wyrdeer, tangrowth,
togekiss, yanmega.

> **Pendiente:** varias piden objetos de generaciones posteriores
> (`electirizer`, `magmarizer`, `protector`, `razor_fang`, `razor_claw`). Si
> esos objetos no son obtenibles, la evolución es un callejón sin salida que
> frustra. Deben entrar en la tienda o en Tesoros — tarea `PKM-005`.

---

## 4. Qué ve el jugador — **cambiado el 2026-08-22**

### Lo que está implementado hoy: se oculta

**Decisión del usuario, 2026-08-22, literal:** *«en la pokedex solo deben
aparecer los pokemones de la primera y segunda generacion, los otros quedan
bloqueados»*. Así que:

| | |
|---|---|
| `national.json` | pasa a agregar **solo** `kanto` y `johto` |
| Las otras 9 regiones | se **vacían** (`entries: []`) |
| Kanto | 151 → **162** entradas |
| Johto | 100 → **112** entradas |

Las 23 entradas de más son las **evoluciones que cruzan** (§3-bis), puestas en
la dex de su **preevolución**: Ursaluna aparece en Johto porque Ursaring es de
Johto. Sin eso, quien consiguiera un Ursaluna tendría un Pokémon que la Pokédex
no reconoce — parece un fallo, no una recompensa.

> ⚠️ **Se vacían, no se borran, y quedan 9 pestañas de región vacías.** Un
> datapack puede sobrescribir un fichero pero no eliminarlo, y la interfaz lista
> **todas** las dex cargadas sin filtrar las vacías
> (`PokedexGUI.kt:173`: `availableRegions = Dexes.dexEntryMap.keys.toList()`).
> Es lo único que se puede hacer **desde datos**.

> ⚠️ Se comprueba que la entrada de dex **exista** antes de referenciarla:
> `getEntries()` hace `mapNotNull`, o sea que un id inventado se descarta **en
> silencio** y esa especie no saldría sin que nada avise.

### Lo que se había diseñado: enseñarlo con candado

El diseño anterior era **el contrario**, y se deja escrito porque el argumento
sigue siendo bueno:

```
┌─ #0387 Turtwig ──────────────── 🔒 ─┐
│ Aún no ha llegado a este mundo.      │
│                                      │
│ Región: Sinnoh                       │
│ Estado: próxima expansión            │
└──────────────────────────────────────┘
```

Eso convertía la limitación en una **promesa**: el jugador veía que el mundo va
a crecer, en vez de leerlo como carencia.

**Por qué no está así:** además de que el usuario pidió ocultarlas, **la Pokédex
de Cobblemon no tiene forma de marcar una entrada como bloqueada desde un
datapack**. Una entrada está o no está. Ese diseño exige **nuestra propia
pantalla de Pokédex** en el mod de cliente, que hoy no existe (`ART-002`,
D-026). Cuando exista, esto se puede recuperar: el dato de qué generación está
activa ya lo tiene el servidor.

---

## 5. El inicial

Solo Kanto y Johto:

| Región | Iniciales |
|---|---|
| **Kanto** | Bulbasaur · Charmander · Squirtle |
| **Johto** | Chikorita · Cyndaquil · Totodile |

Diosesmon pregunta *"¿de qué generación quieres elegir tu compañero?"* con dos
botones y luego muestra los tres. **El flujo es correcto y lo adoptamos.**

Lo que añadimos: que la elección tenga consecuencia más allá del Pokémon —
ver [world-structure.md](../world/world-structure.md) §6.

---

## 6. Criterios para abrir una generación nueva

No se abre por calendario, sino cuando se cumplen las tres:

1. **La Pokédex activa está completada por alguien** — si nadie ha llegado al
   final, añadir más es tapar un problema de ritmo, no resolverlo
2. **La economía está estable** — sin inflación pendiente de corregir
3. **Los spawns están equilibrados** — no se añade encima de algo mal calibrado

> Añadir contenido para tapar un diseño que no funciona es la forma más rápida
> de acumular deuda de diseño.

---

## 7. Riesgos

| Riesgo | Mitigación |
|---|---|
| *"Este servidor tiene menos Pokémon"* | Comunicarlo como **temporadas**, no como recorte. La Pokédex enseña lo que viene |
| Cadenas evolutivas rotas entre generaciones | Comprobación obligatoria de §3 antes de cada despliegue |
| Un jugador ya tiene un Pokémon apagado (venido de otro sitio) | Se conserva; apagar el spawn no borra nada |
| Abrir generaciones demasiado rápido | Los tres criterios de §6 |

---

## Next Actions

1. ~~Ratificar el arranque con Kanto + Johto~~ ✅ D-017
2. ~~`PKM-004` — script que genera el datapack de apagado~~ ✅ y corregido
   el 2026-08-22 (§3-ter)
3. ~~Auditar cadenas evolutivas que cruzan generaciones~~ ✅ §3-bis
4. **Verificar en el juego** — lo único que queda y no puedo hacer yo:
   `/checkspawn common` en el Mundo Salvaje no debe sacar nada de Hoenn en
   adelante, y la Pokédex debe abrir en National con 251
5. `PKM-005` — que los objetos de evolución de §3-bis sean obtenibles, o esas
   23 evoluciones son un callejón sin salida
6. `PKM-002` — diseño del inicial
7. Cuando exista la Pokédex propia (`ART-002`): recuperar las entradas
   bloqueadas con candado en vez de ocultas (§4)

## Related Systems

- [Auditoría de Cobblemon](../analysis/cobblemon-audit.md) · [Los mundos](../world/worlds.md)
- [Economía](../economy/economy-overview.md)
