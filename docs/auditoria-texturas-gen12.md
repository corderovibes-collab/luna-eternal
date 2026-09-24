# Auditoría de texturas Pokémon — generaciones 1 y 2

Se revisaron las 251 especies de Kanto y Johto con los recursos efectivos del cliente: 5.727 estados de aspectos (normal, shiny, género y variantes), 1.609 texturas y todos los resolvers, modelos y posers visibles en los paquetes activos.

Se corrigieron los resolvers de Pikachu y se restauraron los modelos que COBBLEVERSE RP sustituía en estas generaciones:

- Arbok, junto con Pidgeot, Tyranitar, Nidoking y otros 52 modelos de Kanto y Johto sustituidos por COBBLEVERSE RP, ahora usa la geometría oficial de Cobblemon 1.8.0. El resolver de Arbok también volvió a su versión oficial para que su atlas y sus variantes sigan la misma geometría.
- Cinco variantes de cosplay regional de Pikachu apuntaban a texturas `*_alola_bias.png` que no existían. Se apuntan a sus texturas de cosplay existentes; se eliminó además una entrada Belle normal que aplicaba una textura shiny sin exigir el aspecto shiny.

La auditoría posterior al parche quedó en cero referencias faltantes, cero JSON inválidos y cero incompatibilidades exactas entre el tamaño declarado del modelo y su atlas. Las cuatro texturas completamente transparentes encontradas son capas de relleno intencionales (`blank`, `blank_layer` y cosméticos), no cuerpos Pokémon.

El informe reproducible está en `build/auditoria-gen12-overlay/auditoria.json` y el script en `tools/auditar_pokemon_gen12.py`. Las referencias de animación que el auditor marca como ausentes son llamadas opcionales de combate o variantes de mods externos; no se cambiaron porque Cobblemon usa fallback de pose y tocar esos animadores podría romper formas fuera de Gen 1–2.

La compilación de `lunaneon` incluye las correcciones en `neon/build/libs/lunaneon-0.1.0.jar`. Se publicó en el manifiesto `manifest-ac8c2f2d09.json`; el launcher descarga solamente ese JAR actualizado. El servidor no requiere reinicio por este cambio, pero el cliente sí necesita cerrar y abrir Minecraft.
