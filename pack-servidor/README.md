# `pack-servidor/` — configuración que vive SOLO en el servidor

Lo que hay aquí **no viaja al cliente**. Son ajustes de mods que se declaran
`environment: server`, así que el jugador no los descarga ni los necesita.

> ⚠ **No confundir con `config/`**, que es la configuración que va **a los dos
> sitios** y se publica en el manifiesto (`config/cobblemon-cards.json`). Si un
> ajuste tiene que verlo el cliente, va allí y no aquí.

## `ClaimBlocks/`

Las protecciones de terreno. `settings.json` define los escalones y
`texts.json` todo lo que lee el jugador, en español.

### Cómo se quita una protección

> ⚠⚠⚠ **ROMPER EL MÓDULO NO LA QUITA, Y ESA ES LA TRAMPA.** Las parcelas no
> viven en el mundo: viven en `config/ClaimBlocks/claims.json`. Quitar el
> bloque deja la protección **viva y sin nada que la señale** — no se ve, no se
> puede mirar, y `/cb delete` (que actúa sobre lo que estás mirando) ya no
> tiene a qué apuntar. El propio mod avisa de esto con un mensaje… que **solo
> sale si el bloque sigue puesto**.

```
/cb menu      abre TUS protecciones y desde ahí se borran. Funciona aunque el
              bloque ya no exista, que es el único camino que queda entonces
/cb delete    borra la que estás mirando. Necesita el módulo puesto
/cb view      dibuja el borde con partículas
```

> ⚠⚠ **Y si hay que tocar los ficheros, EL SERVIDOR TIENE QUE ESTAR PARADO.**
> Se vaciaron con el servidor arriba, se reinició, y el mod los **volvió a
> escribir con su copia en memoria** al apagarse: *«Loaded 1 protections»*. No
> dio ningún error — simplemente ganó el que escribió el último. Es la misma
> familia que congelar el mundo antes de copiarlo.
> Y son **dos** ficheros: `config/ClaimBlocks/claims.json` (las parcelas) y
> `world/claimblocks/player_data.json` (el índice por jugador). Vaciar uno solo
> deja el estado a medias.

### Los cinco escalones

> ⚠⚠⚠ **El número que se configura es el RADIO, no el lado.** Un cuadrado
> centrado en el propio módulo tiene **siempre** lado impar —el módulo más `r`
> a cada lado—, así que un 50×50 exacto **no existe**. Los cinco son radio
> 7 · 25 · 50 · 80 · 125, o sea **15×15 · 51×51 · 101×101 · 161×161 ·
> 251×251**, y se llaman por su tamaño real: llamar «50×50» a algo que protege
> 51 genera la pregunta *«me dijiste 50»* y además descuadra cualquier cuenta
> de solapes.

### Las texturas

> ⚠⚠ **La que trae el mod es un cubo gris liso y se ve como una avería.** Se
> descargó y se midió: `#7d7d73` en las seis caras con una capa de sombrero
> `#999999` **opaca** encima. Su URL responde 200 —no está rota— pero parece
> una textura que no ha cargado. Y su hash tiene **63 caracteres** cuando los
> de Mojang son 64.

Hoy cada escalón lleva **su gema**, y el color sube con el tamaño: verde ·
cian · magenta · naranja · rojo, el mismo orden que el color del nombre. Con
cinco módulos iguales habría que leer el nombre cada vez.

> ⚠ **Salen de una base de datos y se verifican una a una** (`heads.csv` de
> `TheLuca98/MinecraftHeads`): que la URL responda, que el PNG sea 64×64 y que
> la cara esté pintada. Un Base64 con un hash que no existe da **una cabeza de
> Steve sin avisar**.
> ⚠ Y **no hay alternativa a la cabeza**: se abrió el jar y `StoneConfig` tiene
> exactamente cinco campos —`radius_x`, `radius_z`, `head_info`, `head`,
> `display_name`—. No acepta un bloque normal, aunque su README hable de
> «blocks or custom heads».

### Copias de seguridad

> ⚠⚠ **Las parcelas se guardan en `config/`, no en el mundo.** Eso significa
> que `python tools/backup.py --solo-mundo` **NO las copia**: hay que hacer la
> copia completa. El índice por jugador sí está en el mundo, pero es un índice
> — sin `claims.json` no hay protecciones que indexar.
