# Buhonero — Mercado Negro

Implementación del 15 de septiembre de 2026. Petición directa del usuario; precio y niveles confirmados durante el desarrollo.

## Funcionamiento

- NPC humano con la skin adjunta, en `lunaeternal:ciudadela`, posición `77.526 55 -66.7`, yaw `180` (norte). La Ciudadela se comprobó mediante consulta del servidor: el jugador estaba cerca de esas coordenadas.
- Nombre: **Buhonero - Mercado Negro**. Sin IA de combate, inmóvil, persistente e invulnerable; conserva la protección común de los NPC decorativos.
- Una oferta **personal**, fija durante 24 horas reales desde la primera visita. Los ciclos siguientes conservan ese anclaje aunque el jugador se desconecte o el servidor se reinicie. Dos jugadores pueden coincidir por azar; no se repite la especie de la oferta anterior de un mismo jugador.
- Pokémon implementados de Kanto/Johto: Pokédex 1–251, con exclusión explícita de legendarios y míticos, además de las etiquetas del registro. Sin formas especiales ni shiny garantizado: la creación solicita `shiny=false`.
- Nivel aleatorio entre **1 y 20**, almacenado con la oferta. Precio: **10.000 Plata** (`Currency.POKEDOLLAR`). No usa LunaCoins.
- Una compra por oferta y un intervalo mínimo de **24 horas desde la compra anterior**. Comprar justo antes de la rotación no permite volver a comprar inmediatamente.
- Visor 3D grande y animado, nombre, nivel, precio, saldo, cuenta regresiva y confirmación del cobro. Las respuestas de compra actualizan la misma pantalla.
- El saludo adjunto dura aproximadamente 2,14 s. Se convirtió a OGG Vorbis y solo se reproduce mediante el gestor de sonido del cliente destinatario cuando abre la interfaz con el NPC. Las actualizaciones de oferta no reproducen el saludo. Cerrar la pantalla detiene el sonido.
- Los clientes antiguos reciben la indicación de actualizar desde el launcher.

## Persistencia y validaciones

`V041__mercado_negro.sql` añade `black_market_offer` y `black_market_purchase`, sin modificar tablas de otros sistemas. El débito, el recibo con el NBT completo del Pokémon y el plazo de compra se escriben en la misma transacción. La clave primaria `(player_id, cycle_ms)` y la clave de idempotencia del ledger impiden repetir el cobro.

Los paquetes de cliente solo incluyen el ciclo mostrado y la intención de comprar. El servidor comprueba mundo, distancia, presencia del NPC, estado del jugador, caducidad, compra anterior y saldo. Una oferta vencida se rechaza: no compra automáticamente el reemplazo.

La entrega busca el UUID tanto en el equipo como en el PC antes de añadirlo. Si ambos están llenos o el jugador se desconecta, conserva el recibo pendiente y reintenta al interactuar. El NBT se serializa y el almacenamiento se modifica en el hilo principal; SQL y disco se procesan fuera de él.

Antes de confirmar la entrega en SQL, se fuerza el guardado mediante el adaptador y la cola de escritura de Cobblemon. En esa misma tarea se escribe un recibo local en `<mundo>/lunaeternal/market-receipts/<pokemon-uuid>.receipt`. Permite reconocer una entrega realizada incluso si falla la confirmación SQL y posteriormente el Pokémon cambia de propietario. Los respaldos/restauraciones deben mantener juntos base de datos, almacenamiento Pokémon y recibos. No supone una transacción distribuida frente a corrupción de disco o restauraciones parciales.

Los dos accessors de almacenamiento apuntan a la API instalada de Cobblemon 1.8.0. Si se actualiza ese mod, se deben revisar estos campos y repetir la comprobación de arranque.

## Archivos y arte

- Lógica y red: `mod/src/main/java/net/pokereport/luna/buhonero/`.
- Pantalla: `mod/src/client/java/net/pokereport/luna/client/pokepad/BuhoneroScreen.java`.
- Skin original, copiada sin cambios: `assets/rctmod/textures/trainers/single/luna_buhonero.png`.
- Entrenador: `data/rctmod/trainers/luna_buhonero.json`.
- Audio: `assets/lunaeternal/sounds/buhonero/saludo.ogg`; evento `lunaeternal:buhonero.saludo`.
- Fondo generado: `assets/lunaeternal/textures/gui/pokepad/mercado_negro.png`, 1620×971.

Se utilizó `image_gen` en modo edición sobre `arte/pokepad/fondo_cosmeticos.png`. Instrucción de arte: conservar distribución, silueta, marca y zonas libres; sustituir naranja por bronce envejecido, ciruela oscura, carbón y turquesa frío, con emblema encapuchado y ambiente de cueva. Una segunda edición sustituyó exclusivamente el damero exterior por fondo negro opaco. La skin del usuario no fue modificada.

## Verificación

- `gradlew -p mod build --offline`: correcto, **45 pruebas** sin fallos, incluyendo filtros de especies, límites de nivel, plazos exactos, compra al borde de la rotación y recibos persistentes.
- JAR comparado contra el respaldado del servidor: **ninguna entrada eliminada**; solo se añaden recursos/clases del Buhonero y sus registros. Sonidos y traducciones anteriores conservados clave por clave.
- Respaldo y evidencias: `build/mercado-negro/`. Despliegue acotado a `lunaeternal-0.1.0.jar`, con comprobación de hash y rollback ante fallo de arranque.
- La compilación y los tests no sustituyen una comprobación visual dentro del cliente de Minecraft. Falta observar con el cliente actualizado el encuadre 3D, la skin, el saludo y una compra real; no se gastó el saldo del jugador para probar.

## Entrega

JAR compilado SHA1: `fc04b23d29408a68373ff8308765eb1008255a05`.
Launcher publicado y verificado: `manifest-bf64617519.json`, reemplazando la entrada de Luna Eternal del manifiesto anterior `b14472b58a`. Incluye la remediación de `BuhoneroScreen` para colocar el shader de desenfoque (`applyBlur`) por detrás de la interfaz del mercado negro, la transparencia RGBA de las esquinas del chasis y la retirada del relleno negro opaco de pantalla completa.

Servidor reiniciado con el mismo JAR. La migración V041 se aplicó correctamente y el arranque confirmó 240 especies permitidas. Se cargó temporalmente el chunk `(4,-5)` de la Ciudadela para efectuar la colocación inicial sin jugadores. Después se retiró esa carga forzada y se ejecutó `save-all flush`.

La consulta de entidades confirmó una sola instancia, `TrainerId=luna_buhonero`, `Pos=[77.526,55,-66.7]`, `Rotation=[180,0]`, persistencia, IA desactivada e invulnerabilidad. UUID de la instancia: `[I;961618619,-327400134,-1954850236,-848961780]`. Evidencias: `npc-verificado.log`, `startup.log` y `verificacion-final.json` en la carpeta de despliegue.

Es necesario reiniciar Minecraft desde el launcher para cargar la skin, el sonido y la pantalla nuevos.
