# Configuración de mods ajenos

Ficheros que **nosotros** decidimos y que van a **los dos lados** —servidor y
clientes— porque son de mods que registran cosas o aplican reglas de juego.

> ⚠️ **No son la configuración del jugador.** Lo que el jugador ajusta a su
> gusto (teclas, gráficos, shaders) llega por `keepExisting` y **no se pisa
> nunca**. Lo de aquí son **reglas del servidor**, y esas no las elige él.

| Fichero | Mod | Por qué está aquí |
|---|---|---|
| `cobblemon-cards.json` | Cobblemon Cards | Apaga lo que cruza la línea roja y lo que choca con Tesoros |

## `cobblemon-cards.json`

Razonamiento completo en
[docs/analysis/cobblemon-cards.md](../docs/analysis/cobblemon-cards.md) §4.1.
En corto, y **ninguna de las tres primeras es una preferencia**:

| Ajuste | Por qué |
|---|---|
| `enableCardStats: false` | ⚠⚠⚠ Una carta daba daño, armadura, vida y hasta ×100 de apariciones. Con la zona de LunaCoins **sin límite**, eso sería comprar poder con dinero real sin techo: T4, la línea roja de D-007 y D-014 |
| `enablePlayerStats` · `enableSpawnBoostStats` | Los dos interruptores de detalle, apagados también. Con el general en `false` ya no se aplican, pero dejarlos encendidos deja **una línea de config** entre nosotros y la línea roja |
| `enableBoosterChestSpawn: false` | ⚠⚠ Metía sobres en **todas** las tablas de cofre —su filtro es `path.contains("chest")`, mucho más ancho de lo que parece— y eso choca de frente con Tesoros (D-020) |
| `cardDropChance: 1.0` | **Se queda el que trae.** Es lo único que ata una carta a jugar: capturas algo y a veces sale su carta |
| `allowFakemonCards: false` | Ya viene así. Queda escrito para que nadie lo encienda sin saber que mete especies que aquí no existen |

> ⚠️ **Va en los DOS lados, y no es por simetría.** Las estadísticas las aplica
> el servidor, pero el tooltip de la carta lo pinta el cliente: con el servidor
> en `false` y el cliente en `true`, la carta **anunciaría un bono que no
> existe**. Un número que miente es peor que no enseñarlo.

> ⚠️ **Lo que esto NO arregla es la lista de especies.** `BoosterLootTable` usa
> `PokemonSpecies.getImplemented()` —las 1.025— y no hay ningún ajuste que lo
> recorte a Kanto + Johto. Eso pide parche (§4.2 del análisis) y sigue
> pendiente.
