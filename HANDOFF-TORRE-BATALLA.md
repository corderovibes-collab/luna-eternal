# HANDOFF — TORRE DE BATALLA (para la siguiente IA / Claude)

> Pegar el bloque «EL PROMPT» tal cual a la nueva IA (Claude). El resto es contexto
> para que la entrega sea completa y la nueva IA no tenga que adivinar nada.

---

## EL PROMPT

Eres un desarrollador senior trabajando en el mod de Minecraft «Luna Eternal» (PokeReport), un servidor Fabric 1.21.1 (Java 21, mappings Yarn, Loom 1.7.4) con Cobblemon 1.7.3. El mod `lunaeternal` es UN SOLO JAR con dos fuentes: `mod/src/main` (servidor) y `mod/src/client` (cliente). Economía en MariaDB (HikariCP, migraciones en `Database.MIGRATIONS[]`).

Trabaja en el worktree `D:\pokereportversionmejorada\.claude\worktrees\santuario-monumentos-ecd873` (rama `claude/santuario-monumentos-ecd873`).
Lee `CLAUDE.md` y `docs/world/torre-batalla.md` antes de tocar nada.

ESTADO ACTUAL (Torre de Batalla y Recompensas):
- **Arena y Plataforma Mapeada**: Coordenadas fijas en `lunaeternal:torre_batalla`:
  * Jugador: 50.489, 117, 62.56 (mira Norte)
  * NPC Entrenador: 50.48, 117, 38.51 (mira Sur)
  * Pokémon NPC 1v1: 50.482, 116, 46.57 (mira Sur)
  * Pokémon NPC 2v2: 53.493, 116, 46.52 y 47.518, 116, 46.502 (mira Sur)
  * Pokémon Jugador 1v1: 50.528, 116, 54.547 (mira Norte)
  * Pokémon Jugador 2v2: 47.484, 116, 54.567 y 53.546, 116, 54.514 (mira Norte)
- **Hologramas de Clasificación**: 3 hologramas independientes (1v1, 2v2, aleatorio), sincronizados por modo, separados del NPC recepcionista. Fondo de alta legibilidad (`0xF40A0E18`, ~96% opaco azul pizarra), texto en colores de alto contraste (`§e§l`, `§b§l`, `§a§l`, `§f§l`). Comandos: `/luna torre_batalla holograma spawn|despawn|tp <modo>`.
- **Sistema de Temporadas y Recompensas**:
  * Archivo `TorreRecompensas.java` y persistencia en `config/luna_torre_recompensas.json`.
  * Reclamo único por temporada (avanzar temporada reinicia reclamos con `/luna torre_batalla nueva_temporada`).
  * Catálogo R1-100 con Cobblemon plushies (R50 Snorlax, R70 Gengar, R90 Marshadow, R100 Kyogre, R100 Trofeo Shiny), Chapas Plateada/Dorada, caramelos, bayas y balls.
  * Bonificaciones: +1,000 Plata cada 5 rondas, +50 LunaCoins cada 30 rondas.
  * Rondas 101+ (Infinito): 1 Master Ball fija garantizada por cada victoria consecutiva.
- **Interfaz Gráfica PokéPad**:
  * `TorreScreen.java`: botón `[ 🏆 RECOMPENSAS ]` en panel izquierdo (`y = 625`) con badge de pendientes.
  * `TorreRecompensasScreen.java`: pestañas [1-20], [21-40], [41-60], [61-80], [81-100], [101+ Infinito].
  * Tarjetas en 2 filas estrictas (cero solapamiento horizontal entre títulos, slots y botones).
  * Slots de ítems oscuros rehundidos (`0xFF0D121B` con borde `0xFF28364D`) con tooltips nativos (`ctx.drawItemTooltip`).
  * Tipografía con sombra nativa Minecraft (`shadow = true`), sin contorno artificial blanco emborronado.
  * Corrección crítica de `marco()`: el borde no rellena la caja interior.
  * Regla innegociable de 2 pasadas (`docs/ui/dibujado.md`): todo el 2D primero -> `ctx.draw()` -> modelos 3D -> tooltips.

REGLAS INNEGOCIABLES DEL PROYECTO:
1. Nunca consultar la base de datos en el hilo del servidor (`LunaEternal.submit`).
2. Nunca confiar en el cliente (P6): precios, permisos y estados los decide el servidor.
3. Prohibido convertir monedas (D-014) y vender poder competitivo por LunaCoins (D-007).
4. NO desplegar ni publicar sin aprobación explícita del usuario.
5. Orden de despliegue: `gen_manifest.py --publicar` (clientes) ANTES de `desplegar.py mod --reiniciar` (servidor).
6. Regla de 2 pasadas de `docs/ui/dibujado.md` es obligatoria para cualquier pantalla con ítems.

CICLO DE TRABAJO: editar → `.\gradlew.bat build -x test` en `mod\` → commit → pedir aprobación → publicar manifiesto → desplegar + reiniciar → avisar al usuario.

---

## CONTEXTO TÉCNICO DETALLADO

- **Ficheros de Torre de Batalla (Servidor)**:
  * `mod/src/main/java/net/pokereport/luna/torrebatalla/TorreBatallaService.java`: control del combate, escalado a nivel 100, hook de victorias.
  * `mod/src/main/java/net/pokereport/luna/torrebatalla/TorreRecompensas.java`: temporadas, catálogo R1-100, Master Balls 101+, bonos de dinero, persistencia JSON.
  * `mod/src/main/java/net/pokereport/luna/torrebatalla/TorreRanking.java`: hologramas con fondo 96% opaco, persistencia de records por modo.
  * `mod/src/main/java/net/pokereport/luna/torrebatalla/TorreArenasManager.java`: coordenadas de la plataforma fija.
  * `mod/src/main/java/net/pokereport/luna/torrebatalla/TorrePools.java`: generación de equipos balanceados Gen 1 y Gen 2.
  * `mod/src/main/java/net/pokereport/luna/command/LunaCommand.java`: comandos `/luna torre_batalla nueva_temporada`, `dar_ronda`, `holograma`, etc.
- **Ficheros de Torre de Batalla (Cliente)**:
  * `mod/src/client/java/net/pokereport/luna/client/pokepad/TorreScreen.java`: pantalla principal con ladder MK y botón de recompensas con contador.
  * `mod/src/client/java/net/pokereport/luna/client/pokepad/TorreRecompensasScreen.java`: chasis PokéPad, pestañas, 2 filas por tarjeta, slots rehundidos, tooltips nativos.
  * `mod/src/client/java/net/pokereport/luna/client/EstadoCliente.java`: caché de `EstadoRecompensasTorre`.
  * `mod/src/client/java/net/pokereport/luna/client/LunaCliente.java`: receptor de red registrado.
- **Red (`Red.java`)**:
  * Payloads: `PedirRecompensasTorre`, `ReclamarRecompensaTorre`, `EstadoRecompensasTorre`.
- **Persistencia**:
  * `config/luna_torre_recompensas.json` almacena la temporada y el set de recompensas cobradas por UUID.
- **Comandos de Prueba / Verificación**:
  * `/luna torre_batalla dar_ronda <jugador> <modo> <ronda>`
  * `/luna torre_batalla nueva_temporada`
  * `/luna torre_batalla holograma spawn 1vs1`
