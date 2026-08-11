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

**IMPLEMENTADO** (2026-08-11). Datapack generado y desplegado:
**251 especies activas, 583 spawns apagados**. Generador en
`tools/gen_generaciones.py`.

⚠️ Carga confirmada, **efecto en el juego sin verificar**: `/checkspawn`
requiere un jugador conectado.

## Last Decision

D-017 · y la decisión de **no bloquear las evoluciones que cruzan** (§3-bis).

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
- [ ] Que un Pokémon apagado no aparece por incursión, pesca ni huevo
- [ ] Que la cría no produce especies apagadas

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

## 4. Qué ve el jugador

**Lo bloqueado no se oculta** (P9 y `navigation.md` §5). La Pokédex muestra
las 1 025 entradas, pero las inactivas dicen por qué:

```
┌─ #0387 Turtwig ──────────────── 🔒 ─┐
│ Aún no ha llegado a este mundo.      │
│                                      │
│ Región: Sinnoh                       │
│ Estado: próxima expansión            │
└──────────────────────────────────────┘
```

Eso convierte una limitación en una **promesa**: el jugador ve que el mundo va
a crecer. Es lo contrario de esconderlo, que se leería como carencia.

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

1. Ratificar el arranque con Kanto + Johto
2. `PKM-004` — script que genera el datapack de apagado
3. Auditar cadenas evolutivas que cruzan generaciones
4. `PKM-002` — diseño del inicial

## Related Systems

- [Auditoría de Cobblemon](../analysis/cobblemon-audit.md) · [Los mundos](../world/worlds.md)
- [Economía](../economy/economy-overview.md)
