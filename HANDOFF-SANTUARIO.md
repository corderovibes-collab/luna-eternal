# HANDOFF — SANTUARIO DE MONUMENTOS (para la siguiente IA)

> Pegar el bloque «EL PROMPT» tal cual a la nueva IA. El resto es contexto
> para que la entrega sea completa y la nueva IA no tenga que adivinar nada.

---

## EL PROMPT

Eres un desarrollador trabajando en el mod de Minecraft «Luna Eternal»
(PokeReport), un servidor Fabric 1.21.1 (Java 21, mappings Yarn, Loom 1.7.4)
con Cobblemon 1.7.3. El mod `lunaeternal` es UN SOLO JAR con dos fuentes:
`mod/src/main` (servidor) y `mod/src/client` (cliente). Economía en MariaDB
(HikariCP, migraciones en `Database.MIGRATIONS[]`).

Trabaja SIEMPRE en el worktree
`D:\pokereportversionmejorada\.claude\worktrees\santuario-monumentos-ecd873`
(rama `claude/santuario-monumentos-ecd873`, ya subida a origin). Lee
`CLAUDE.md` (el del worktree) antes de tocar nada, y
`docs/world/santuario.md` para el sistema del santuario.

ESTADO ACTUAL (desplegado en vivo, autotest 589/589): sistema «Santuario»
completo — nichos 3x3 en Monumentos; alquiler 24 h por 5.000 Plata; compra
permanente por 300 LunaCoins (provisional); 1 nicho por jugador (CAMPEON+
puede más); subida de foto desde el PokePad (diálogo tinyfd, troceada 16 KB,
reensamblada por tamaño en `Subidas`), moderación con vista previa
«MODERAR FOTOS» en el PokePad (solo nivel 3: miniatura, dueño,
APROBAR/RECHAZAR); holograma de la foto sobre el proyector (opaco, derecho,
con clic derecho con mano vacía que abre el memorial); MemorialScreen
rediseñada (dueño en oro, título grande, historia con aire, ♡ honores y
botón HONRAR); honores 10/día/jugador/nicho con ventana de 24 h; NPC Chansey
(`/luna santuario npc`); protección de bloques en los nichos.

REGLAS INNEGOCIABLES:
1. Nunca consultar la base de datos en el hilo del servidor (`LunaEternal.submit`).
2. Nunca confiar en el cliente (P6): precios, permisos y estados los decide el servidor.
3. Prohibido convertir monedas (D-014) y vender poder competitivo por LunaCoins (D-007).
4. NO desplegar ni publicar sin aprobación explícita del usuario.
5. Orden de despliegue: `gen_manifest.py --publicar` (clientes) ANTES de
   `desplegar.py mod --reiniciar` (servidor). El jar de los clientes sale
   del manifiesto, no del servidor.
6. Si dos ramas compilan el MISMO jar, la segunda que despliegue borra a la
   primera: commitear al terminar es lo único que hace existir el trabajo.
7. Toda regla nueva añade invariantes al autotest (`AutoTest.java`) ANTES de
   desplegar. El autotest se corre UNA SOLA VEZ (dos autotests concurrentes
   se pisan la base de prueba y dan fallos falsos; ojo: un comando RCON
   enviado durante el arranque queda encolado y puede ejecutarse junto al
   siguiente).
8. Python embebido: `D:\python312\python.exe` (solo stdlib), y para RCON
   `$env:PYTHONIOENCODING='utf-8'`.

CICLO DE TRABAJO: editar → `.\gradlew.bat build -x test` en `mod\` →
commit → pedir aprobación → publicar manifiesto → desplegar + reiniciar →
`luna autotest` por RCON → avisar al usuario de que reabra el juego.

LO QUE QUEDA PENDIENTE (verificar en el juego, de la mano del usuario):
- Construir los nichos reales en Monumentos y escribir sus coordenadas en
  `config/lunaeternal/santuario.json` (en el servidor, no en el repo; la
  config se lee solo al arrancar, así que tocarla exige reiniciar).
- Colocar la Chansey con `/luna santuario npc`.
- Prueba de recorrido con DOS cuentas: subir foto → moderar desde el PokePad
  → holograma derecho y opaco → clic derecho (mano vacía) abre el memorial →
  honores (10/día, ventana 24 h).

MEJORAS OPCIONALES SI LAS PIDEN: comando `/luna santuario recargar` (la
config hoy se lee solo al arrancar); sonidos CC0 propios (hoy suena la
campanilla de amatista de vainilla); regenerar el icono canónico del
santuario con `tools/gen_pokepad.py` (necesita numpy/PIL).

---

## CONTEXTO PARA LA NUEVA IA (si quiere más detalle)

- Servidor dev: `7dc30799` (Pterodactyl), MC 1.21.1 Fabric, whitelist,
  TheJuanCE op nivel 4. Deploy con `.env` en la raíz del worktree
  (git-ignored; PTERO_PANEL/KEY). `gh` autenticado como
  `corderovibes-collab`.
- Ficheros del santuario (servidor):
  `mod/src/main/java/net/pokereport/luna/santuario/{SantuarioService,Subidas,
  NichoCatalogo,SantuarioProteccion,SantuarioNpc}.java`,
  migración `V031__santuario.sql`, payloads en `net/pokereport/luna/net/Red.java`
  (PedirSantuario/EstadoSantuario, Alquilar/Comprar/Honrar/Textos, SubirFoto/
  FotoTramo/ResultadoFoto, PedirFotos/EstadoFotos/PonerFoto/QuitarFoto,
  PedirPendientes/EstadoPendientes/ModerarFoto, AbrirSantuario/AbrirMemorial,
  RespuestaHonor), autotest `net/pokereport/luna/test/AutoTest.java`.
- Ficheros del santuario (cliente):
  `pokepad/SantuarioScreen.java` (lista + mi nicho + moderación),
  `pokepad/MemorialScreen.java`, `HologramaSantuario.java` (quad a mano,
  clic por rayo), `TexturasFoto.java` (caché por sha1), `DialogoFoto.java`
  (tinyfd), cachés en `EstadoCliente.java`.
- Precios y topes: `PRECIO_ALQUILER=5000`, `PRECIO_PERMANENTE=300`,
  `HONORES_DIA=10`, `FOTO_MAX_BYTES=2_500_000`, `FOTO_LADO_MAX=512`,
  `PENDIENTES_MAX=3` (en `SantuarioService`).
- Nicho de prueba en vivo: `nicho_prueba`, proyector en `-111,73,156`
  (ciudadela), holograma a +1,55 del proyector.
- Últimos commits desplegados: `1b86e7a` (holograma derecho y opaco, clic
  al holograma, memorial rediseñada, fuera memorial_luz), `9ae1b2a`
  (moderación con vista previa), `4a5db15` (Subidas), `11b8268` (tinyfd),
  `039f03e` (mcmeta).
- Lecciones pagadas con sangre (no repetir): los `.mcmeta` con `\n` literal
  crashean el cliente (GeckoLib); el diálogo AWT se abre invisible detrás de
  GLFW; comparar índice de trozo contra tamaño total nunca completa; `NO`
  editar CLAUDE.md con PowerShell Get-Content/Set-Content (mojibake CP1252);
  `NO` lanzar dos autotests a la vez.
