# `pack-servidor/` — configuración que vive SOLO en el servidor

Lo que hay aquí **no viaja al cliente**. Son ajustes de mods que se declaran
`environment: server`, así que el jugador no los descarga ni los necesita.

> ⚠ **No confundir con `config/`**, que es la configuración que va **a los dos
> sitios** y se publica en el manifiesto (`config/cobblemon-cards.json`). Si un
> ajuste tiene que verlo el cliente, va allí y no aquí.

## `ClaimBlocks/`

Las protecciones de terreno. `settings.json` define los escalones y
`texts.json` todo lo que lee el jugador, en español.

> ⚠⚠⚠ **El número que se configura es el RADIO, no el lado.** Un cuadrado
> centrado en el propio módulo tiene **siempre** lado impar —el módulo más `r`
> a cada lado—, así que un 50×50 exacto **no existe**. Los cinco escalones son
> radio 7 · 25 · 50 · 80 · 125, o sea **15×15 · 51×51 · 101×101 · 161×161 ·
> 251×251**, y se llaman por su tamaño real: llamar «50×50» a algo que protege
> 51 genera la pregunta *«me dijiste 50»* y además descuadra cualquier cuenta
> de solapes.

> ⚠⚠ **Las parcelas se guardan en `config/ClaimBlocks/claims.json`, no en el
> mundo.** Eso significa que `python tools/backup.py --solo-mundo` **NO las
> copia**: hay que hacer la copia completa. El índice por jugador
> (`world/claimblocks/player_data.json`) sí está en el mundo, pero es un índice
> — sin `claims.json` no hay protecciones que indexar.

> ⚠ **Las cinco comparten textura.** Va en Base64 desde `minecraft-heads.com`,
> y un Base64 inventado da una cabeza de Steve **sin avisar**. Está pendiente
> de arte, no de código: hoy se distinguen por el nombre y el color.
