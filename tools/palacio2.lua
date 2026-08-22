--[[ ===========================================================================

  PALACIO MULTIGENERACIONAL POKEMON  ·  Lua Script Brush de Axiom
  Minecraft 1.21.1 · SOLO BLOQUES VANILLA · PokeReport: Luna Eternal

  ---------------------------------------------------------------------------
  QUE CAMBIA RESPECTO A LA VERSION ANTERIOR, Y POR QUE

  La primera version (`palacio.lua`) se construyo y no valia. Tres motivos, y
  los tres estan corregidos aqui de raiz:

  1. ⚠⚠ ESTABA TODO SELLADO. Las alas de las regiones no activas se cerraban
     con un panel de vidrio a proposito -- la idea era "esto existe y todavia
     no te toca". Sobre el plano se leia bien; dentro del juego significa que
     SIETE DE DIEZ SALAS ERAN INACCESIBLES. Un edificio al que no se entra no
     es un edificio.

     Aqui NO HAY UN SOLO SELLO. Todas las salas estan abiertas, con arcos de
     7 de ancho y sin puertas. Y hay una comprobacion automatica que lo
     demuestra: ver la seccion 12.

  2. BLOQUES RAROS. Usaba los 602 bloques propios de `lunaneon`. Ademas de que
     su textura no convencio, obligaba a tener el mod instalado -- y cuando no
     resolvio, el palacio salio invisible.

     Aqui TODO ES VANILLA de 1.21.1. Sin dependencias, sin sorpresas.

  3. NO SE VEIA NADA AL ENTRAR. Se entraba a un anillo y habia que adivinar.

     Aqui, desde el umbral se ve: el nucleo al fondo bajo el domo, y la SALA 1
     (Kanto) a mano derecha, iluminada y rotulada.

  ---------------------------------------------------------------------------
  PLANTA — un circuito cerrado, sin callejones sin salida

                              N  (fondo)
        +-------+-------------------------------+-------+
        | TORRE |   G7      G6      G5          | TORRE |
        +-------+-------------------------------+-------+
        |  G8   |  +-------------------------+  |  G4   |
        |       |  |                         |  |       |
        |  G9   |  |    NUCLEO + DOMO        |  |  G3   |
        |       |  |     (Mon Core)          |  |       |
        | serv. |  |                         |  |  G2   |
        +-------+  +-------------------------+  +-------+
        | TORRE |   serv.  VESTIBULO   G1 <---- | TORRE |
        +-------+-------------------------------+-------+
                              S  (entrada)
                                 ||
                          ESCALINATA + PLAZA

  Se entra por el sur. A la derecha, pegada al vestibulo, esta la SALA 1.
  Desde ahi se recorre el anillo en sentido horario pasando por las nueve
  salas y se vuelve al punto de partida. No hay que desandar nada.

  CUATRO SALIDAS: la puerta principal (sur) y las cuatro torres de esquina,
  que tienen puerta a nivel de suelo.

  ---------------------------------------------------------------------------
  LAS NUEVE SALAS, con su tema

     1  KANTO    laboratorio industrial, rojo y blanco, 8 pedestales
     2  JOHTO    templo de madera oscura con oro y plata, altares
     3  HOENN    roca oceanica y raices de arbol gigante, tronos de piedra
     4  SINNOH   ruinas de cuarzo y purpur, el Monte Corona
     5  UNOVA    ciudad moderna, hierro y luz
     6  KALOS    palacio de cuarzo y oro, vitrinas de museo
     7  ALOLA    plataformas de madera y follaje sobre estanques
     8  GALAR    estadio a escala con gradas y podio de campeon
     9  PALDEA   academia de cristal y terracota, pupitres y catedra

  ⚠ ESTRICTAMENTE ESTRUCTURAL. Ni una entidad: ni soportes de armadura, ni
    NPC, ni mobs, ni spawners. Las plataformas de entrenador se dejan HECHAS Y
    VACIAS, con el hueco despejado. Colocar a los entrenadores es un paso
    posterior y manual.

  ---------------------------------------------------------------------------
  COMO SE EJECUTA

    1. Ponte donde quieras el CENTRO del palacio, MIRANDO AL NORTE.
       La entrada sale siempre hacia el sur (+Z), no hacia donde mires.
    2. Herramientas -> Lua Script Brush
    3. Pega este fichero entero
    4. Clic

  ⚠ `$once$` hace que se ejecute UNA vez en el punto donde pinchas. Sin el se
    comportaria como brocha y construiria el palacio una vez por bloque.

  ⚠ Ctrl+Z de Axiom deshace la operacion ENTERA, aunque sean 700 000 bloques.

  ---------------------------------------------------------------------------
  ⚠ LA TABLA `blocks` DE AXIOM VA ANIDADA POR NAMESPACE

  Es la trampa que dejo el palacio anterior invisible:

      blocks.stone                     SI   (vanilla, plano)
      blocks.minecraft.stone           SI   (anidado)
      blocks["minecraft:stone"]        nil  -- y setBlock con nil NO DA ERROR

  Aqui todo es vanilla, asi que la forma plana basta. `B()` prueba las dos de
  todos modos.

  ---------------------------------------------------------------------------
  INDICE

     1. CONFIGURACION
     2. PALETA (vanilla 1.21.1)
     3. MATEMATICAS
     4. PRIMITIVAS
     5. ELEMENTOS ARQUITECTONICOS
     6. LA PLAZA MULTINIVEL
     7. EL CUERPO DEL PALACIO: muros, torres y fachada
     8. EL VESTIBULO
     9. LA GALERIA ANULAR (la circulacion)
    10. EL NUCLEO Y EL DOMO GEODESICO
    11. LAS NUEVE SALAS
    12. PLATAFORMAS AEREAS Y PUENTES
    13. COMPROBACIONES
    14. MAIN

=========================================================================== ]]

$once$
$ignoreMask$

-- ###########################################################################
-- ##  1. CONFIGURACION                                                     ##
-- ###########################################################################

local CFG = {}

--- Se planta donde pinchas. Para fijarlo, pon esto a false y rellena cx/cz.
CFG.usar_punto_del_raton = true
CFG.cx, CFG.cz = 0, 0

--- Cota del suelo del palacio (se camina aqui).
CFG.y = 64

--- Que se construye. Poner a false para iterar rapido sobre una pieza.
CFG.hacer = {
  plaza      = true,
  muros      = true,
  torres     = true,
  fachada    = true,
  vestibulo  = true,
  galeria    = true,
  nucleo     = true,
  domo       = true,
  salas      = true,
  aereas     = true,
  rotulos    = true,
}

--- Solo estas salas (1..9). Vacio = todas.
CFG.solo_salas = {}

--- Vacia el volumen antes de construir.
CFG.limpiar_antes = false

--- ⚠ GEOMETRIA MAESTRA. Los numeros NO son independientes: la galeria vive
--- entre el nucleo y las salas, y las salas entre la galeria y el muro. Si se
--- toca uno hay que mirar los otros. `comprobar_geometria()` lo verifica.
CFG.r_nucleo   = 24      -- radio de la rotonda central
CFG.gal_ext    = 42      -- borde exterior de la galeria anular (rectangular)
CFG.sala_prof  = 30      -- fondo de cada sala, desde la galeria hacia fuera
CFG.muro       = 76      -- semilado interior del palacio
CFG.alto       = 26      -- del suelo al techo de la planta
CFG.torre_lado = 15      -- lado de las torres de esquina
CFG.arco_ancho = 7       -- ⚠ ancho de TODOS los vanos. 7 es a proposito: por
                         --   un arco de 7 se ve la sala entera desde fuera.

--- El domo geodesico, estilo Poke Ball.
CFG.domo_r     = 30
CFG.domo_y     = 30      -- altura de arranque sobre el suelo

--- Plataformas aereas
CFG.aerea_y    = 62      -- sobre el suelo del palacio
CFG.aerea_r    = 14

--- El rotulo de la fachada.
--- ⚠ En las imagenes de referencia pone "1ZON" y "Rita", que son la FIRMA DEL
--- CONSTRUCTOR original, no parte del edificio. Reproducirlas seria poner el
--- nombre de otro en nuestra fachada, asi que va el nuestro. Cambialo aqui.
CFG.rotulo_fachada = "POKEREPORT"
CFG.rotulo_plaza   = "TRAINER PALACE"

local PUESTOS = 0


-- ###########################################################################
-- ##  2. PALETA — vanilla 1.21.1                                           ##
-- ###########################################################################
--
-- ⚠ TODOS LOS IDS EN UN SOLO SITIO. Un id mal escrito devuelve nil y
-- `setBlock` con nil no coloca nada NI DA ERROR: el sintoma es un hueco en un
-- muro que nadie relaciona con una errata. `comprobar_paleta()` los valida
-- todos antes de empezar.

local FALLOS = {}

--- Resuelve un bloque. Vanilla acepta la forma plana; se prueba tambien la
--- anidada por si acaso.
local function B(id)
  local ok, b = pcall(function() return blocks[id] end)
  if ok and b then return b end
  local ok2, b2 = pcall(function() return blocks.minecraft[id] end)
  if ok2 and b2 then return b2 end
  FALLOS[#FALLOS + 1] = id
  return nil
end

local P = {}

-- --- Base blanca y gris ---------------------------------------------------
P.blanco        = B("white_concrete")
P.blanco_terra  = B("white_terracotta")
P.cuarzo        = B("quartz_block")
P.cuarzo_liso   = B("smooth_quartz")
P.cuarzo_pilar  = B("quartz_pillar")
P.cuarzo_ladr   = B("quartz_bricks")
P.cuarzo_cincel = B("chiseled_quartz_block")
P.cuarzo_esc    = B("quartz_stairs")
P.cuarzo_losa   = B("quartz_slab")
P.cuarzo_liso_esc  = B("smooth_quartz_stairs")
P.cuarzo_liso_losa = B("smooth_quartz_slab")
P.calcita       = B("calcite")
P.diorita       = B("polished_diorita") or B("polished_diorite")
P.andesita      = B("polished_andesite")
P.andesita_losa = B("polished_andesite_slab")
P.gris          = B("gray_concrete")
P.gris_claro    = B("light_gray_concrete")
P.gris_terra    = B("light_gray_terracotta")

-- --- Oscuros y estructura -------------------------------------------------
P.pizarra       = B("polished_deepslate")
P.pizarra_ladr  = B("deepslate_bricks")
P.pizarra_tejas = B("deepslate_tiles")
P.pizarra_esc   = B("polished_deepslate_stairs")
P.pizarra_losa  = B("polished_deepslate_slab")
P.pizarra_muro  = B("polished_deepslate_wall")
P.negro         = B("black_concrete")
P.blackstone    = B("polished_blackstone")
P.blackstone_l  = B("polished_blackstone_bricks")
P.blackstone_e  = B("polished_blackstone_stairs")
P.blackstone_lo = B("polished_blackstone_slab")
P.basalto       = B("polished_basalt")

-- --- Metales --------------------------------------------------------------
P.hierro        = B("iron_block")
P.rejas         = B("iron_bars")
P.cadena        = B("chain")
P.cobre         = B("waxed_cut_copper")
P.cobre_esc     = B("waxed_cut_copper_stairs")
P.cobre_losa    = B("waxed_cut_copper_slab")
P.cobre_expu    = B("waxed_exposed_cut_copper")
P.cobre_bloque  = B("waxed_copper_block")
P.oro           = B("gold_block")
P.oro_bruto     = B("raw_gold_block")
P.esmeril       = B("smooth_stone")
P.esmeril_losa  = B("smooth_stone_slab")

-- --- Color ----------------------------------------------------------------
P.rojo          = B("red_concrete")
P.rojo_terra    = B("red_terracotta")
P.rojo_ladr     = B("red_nether_bricks")
P.lima          = B("lime_concrete")
P.verde         = B("green_concrete")
P.azul          = B("blue_concrete")
P.azul_claro    = B("light_blue_concrete")
P.cian          = B("cyan_concrete")
P.morado        = B("purple_concrete")
P.magenta       = B("magenta_concrete")
P.naranja       = B("orange_concrete")
P.amarillo      = B("yellow_concrete")
P.marron        = B("brown_concrete")
P.marron_terra  = B("brown_terracotta")

-- --- Vidrio ---------------------------------------------------------------
P.vidrio        = B("glass")
P.vidrio_tintado= B("tinted_glass")
P.vidrio_blanco = B("white_stained_glass")
P.vidrio_negro  = B("black_stained_glass")
P.vidrio_rojo   = B("red_stained_glass")
P.vidrio_lima   = B("lime_stained_glass")
P.vidrio_cian   = B("cyan_stained_glass")
P.vidrio_azul   = B("blue_stained_glass")
P.vidrio_morado = B("purple_stained_glass")
P.panel_vidrio  = B("glass_pane")
P.panel_blanco  = B("white_stained_glass_pane")
P.panel_cian    = B("cyan_stained_glass_pane")

-- --- Luz ------------------------------------------------------------------
-- ⚠ La luz de Minecraft NO TIENE COLOR: el motor guarda un numero 0-15. Un
-- bloque cian ILUMINA EN BLANCO. El color hay que ponerlo con el MATERIAL.
P.farol_mar     = B("sea_lantern")
P.piedraluminosa= B("glowstone")
P.shroomlight   = B("shroomlight")
P.rana_ocre     = B("ochre_froglight")
P.rana_verde    = B("verdant_froglight")
P.rana_perla    = B("pearlescent_froglight")
P.lampara       = B("redstone_lamp")
P.varilla       = B("end_rod")
P.farol         = B("lantern")
P.farol_alma    = B("soul_lantern")
P.fuego_alma    = B("soul_fire")
P.amatista      = B("amethyst_block")
P.linterna_mar  = B("sea_lantern")

-- --- Naturaleza (Alola, Hoenn) --------------------------------------------
P.agua          = B("water")
P.hoja_jungla   = B("jungle_leaves")
P.tronco_jungla = B("jungle_log")
P.tabla_jungla  = B("jungle_planks")
P.tabla_jungla_e= B("jungle_stairs")
P.tabla_jungla_l= B("jungle_slab")
P.hoja_azalea   = B("flowering_azalea_leaves")
P.musgo         = B("moss_block")
P.raiz          = B("mangrove_roots")
P.prismarina    = B("prismarine")
P.prismarina_l  = B("prismarine_bricks")
P.prismarina_o  = B("dark_prismarine")
P.prismarina_e  = B("prismarine_brick_stairs")
P.tubo_kelp     = B("dried_kelp_block")

-- --- Maderas (Johto, Paldea) ----------------------------------------------
P.roble_osc     = B("dark_oak_planks")
P.roble_osc_e   = B("dark_oak_stairs")
P.roble_osc_l   = B("dark_oak_slab")
P.roble_osc_tr  = B("dark_oak_log")
P.cerezo        = B("cherry_planks")
P.abeto         = B("spruce_planks")
P.abeto_e       = B("spruce_stairs")
P.abeto_tr      = B("stripped_spruce_log")

-- --- Piedra varia ---------------------------------------------------------
P.piedra        = B("stone")
P.ladrillo_p    = B("stone_bricks")
P.ladrillo_p_e  = B("stone_brick_stairs")
P.ladrillo_p_l  = B("stone_brick_slab")
P.ladrillo_p_m  = B("stone_brick_wall")
P.ladrillo_cinc = B("chiseled_stone_bricks")
P.ladrillo_mus  = B("mossy_stone_bricks")
P.adoquin_mus   = B("mossy_cobblestone")
P.purpur        = B("purpur_block")
P.purpur_pilar  = B("purpur_pillar")
P.purpur_esc    = B("purpur_stairs")
P.purpur_losa   = B("purpur_slab")
P.piedrafin     = B("end_stone_bricks")
P.toba          = B("tuff")
P.toba_ladr     = B("polished_tuff")
P.arenisca      = B("smooth_sandstone")
P.arenisca_e    = B("smooth_sandstone_stairs")
P.terracota     = B("terracotta")

-- --- Decoracion sin entidades ---------------------------------------------
P.aire          = B("air")
P.alfombra_roja = B("red_carpet")
P.alfombra_azul = B("blue_carpet")
P.estanteria    = B("bookshelf")
P.mesa_encant   = B("enchanting_table")
P.yunque        = B("anvil")
P.caldero       = B("cauldron")
P.campana       = B("bell")
P.maceta        = B("flower_pot")
P.barril        = B("barrel")
P.humo          = B("campfire")


-- ###########################################################################
-- ##  3. MATEMATICAS                                                       ##
-- ###########################################################################

local floor, ceil, abs = math.floor, math.ceil, math.abs
local sin, cos, sqrt, atan = math.sin, math.cos, math.sqrt, math.atan
local pi, max, min = math.pi, math.max, math.min

local function redondear(v) return floor(v + 0.5) end
local function acotar(v, a, b) if v < a then return a elseif v > b then return b end return v end
local function lerp(a, b, t) return a + (b - a) * t end
local function dist2(dx, dz) return sqrt(dx * dx + dz * dz) end
local function polar(cx, cz, r, a)
  return redondear(cx + r * cos(a)), redondear(cz + r * sin(a))
end
local function damero(x, z, paso)
  paso = paso or 2
  return (floor(x / paso) + floor(z / paso)) % 2 == 0
end
local function ruido(x, y, z, e)
  e = e or 0.08
  local ok, v = pcall(getSimplexNoise, x * e, y * e, z * e, 1211)
  if not ok or not v then return 0.5 end
  return (v + 1) * 0.5
end


-- ###########################################################################
-- ##  4. PRIMITIVAS                                                        ##
-- ###########################################################################

--- ⚠ REDONDEA AQUI. Es el unico punto de salida al mundo, y varias piezas
--- (el domo, los puentes, los arcos) calculan con decimales. `setBlock` con
--- una coordenada fraccionaria hace algo no documentado.
local function poner(x, y, z, b)
  if not b then return end
  setBlock(redondear(x), redondear(y), redondear(z), b)
  PUESTOS = PUESTOS + 1
end

local function orientar(b, ...)
  if not b then return nil end
  local ok, r = pcall(withBlockProperty, b, ...)
  if ok and r then return r end
  return b
end
local function escalera(b, f, media, forma)
  return orientar(b, "facing=" .. f, "half=" .. (media or "bottom"),
                     "shape=" .. (forma or "straight"))
end
local function losa(b, t) return orientar(b, "type=" .. (t or "bottom")) end
local function eje(b, a) return orientar(b, "axis=" .. (a or "y")) end
local function facing_de(dx, dz)
  if abs(dx) > abs(dz) then return dx > 0 and "east" or "west" end
  return dz > 0 and "south" or "north"
end

local function caja(x1, y1, z1, x2, y2, z2, b)
  if x1 > x2 then x1, x2 = x2, x1 end
  if y1 > y2 then y1, y2 = y2, y1 end
  if z1 > z2 then z1, z2 = z2, z1 end
  for x = x1, x2 do for y = y1, y2 do for z = z1, z2 do
    poner(x, y, z, b)
  end end end
end

local function vaciar(x1, y1, z1, x2, y2, z2) caja(x1, y1, z1, x2, y2, z2, P.aire) end
local function rect(x1, z1, x2, z2, y, b) caja(x1, y, z1, x2, y, z2, b) end

local function marco(x1, z1, x2, z2, y, b)
  if x1 > x2 then x1, x2 = x2, x1 end
  if z1 > z2 then z1, z2 = z2, z1 end
  for x = x1, x2 do poner(x, y, z1, b); poner(x, y, z2, b) end
  for z = z1, z2 do poner(x1, y, z, b); poner(x2, y, z, b) end
end

--- Solo las cuatro paredes, sin suelo ni techo.
local function paredes(x1, y1, z1, x2, y2, z2, b)
  if x1 > x2 then x1, x2 = x2, x1 end
  if z1 > z2 then z1, z2 = z2, z1 end
  for y = min(y1, y2), max(y1, y2) do
    for x = x1, x2 do poner(x, y, z1, b); poner(x, y, z2, b) end
    for z = z1 + 1, z2 - 1 do poner(x1, y, z, b); poner(x2, y, z, b) end
  end
end

--- Una sala completa: suelo, cuatro paredes y techo, con el interior VACIO.
--- Es la base de las nueve salas, y deja el hueco limpio para amueblarlo.
local function recinto(x1, z1, x2, z2, y, alto, b_suelo, b_pared, b_techo)
  rect(x1, z1, x2, z2, y - 1, b_suelo)
  paredes(x1, y, z1, x2, y + alto - 1, z2, b_pared)
  rect(x1, z1, x2, z2, y + alto, b_techo or b_pared)
  vaciar(x1 + 1, y, z1 + 1, x2 - 1, y + alto - 1, z2 - 1)
end

-- --- Circulos -------------------------------------------------------------
-- Se recorre el cuadrado envolvente y se filtra por distancia: caminar el
-- perimetro con seno y coseno deja huecos en las diagonales.

local function disco(cx, cz, r, y, b)
  local ri = ceil(r)
  for dx = -ri, ri do for dz = -ri, ri do
    if dist2(dx, dz) <= r + 0.5 then poner(cx + dx, y, cz + dz, b) end
  end end
end

local function anillo(cx, cz, ri_, re, y, b)
  local ri = ceil(re)
  for dx = -ri, ri do for dz = -ri, ri do
    local d = dist2(dx, dz)
    if d <= re + 0.5 and d >= ri_ - 0.5 then poner(cx + dx, y, cz + dz, b) end
  end end
end

local function circulo(cx, cz, r, y, b) anillo(cx, cz, r - 0.5, r + 0.5, y, b) end
local function cilindro(cx, cz, r, y1, y2, b)
  for y = min(y1, y2), max(y1, y2) do disco(cx, cz, r, y, b) end
end
local function tubo(cx, cz, ri_, re, y1, y2, b)
  for y = min(y1, y2), max(y1, y2) do anillo(cx, cz, ri_, re, y, b) end
end
local function pared_cil(cx, cz, r, y1, y2, b)
  for y = min(y1, y2), max(y1, y2) do circulo(cx, cz, r, y, b) end
end

local function esfera(cx, cy, cz, r, b)
  local ri = ceil(r)
  for dx = -ri, ri do for dy = -ri, ri do for dz = -ri, ri do
    if sqrt(dx*dx + dy*dy + dz*dz) <= r + 0.5 then
      poner(cx + dx, cy + dy, cz + dz, b)
    end
  end end end
end

--- Cascara esferica. `y_min` recorta por debajo (para cupulas).
local function cascara(cx, cy, cz, r, b, g, y_min)
  g = g or 1
  local ri = ceil(r)
  for dx = -ri, ri do for dy = -ri, ri do for dz = -ri, ri do
    if (not y_min) or (cy + dy >= y_min) then
      local d = sqrt(dx*dx + dy*dy + dz*dz)
      if d <= r + 0.5 and d >= r - g + 0.5 then
        poner(cx + dx, cy + dy, cz + dz, b)
      end
    end
  end end end
end

local function cono(cx, cy, cz, r, alto, b)
  for i = 0, alto do
    local rr = r * (1 - i / alto)
    if rr >= 0.7 then circulo(cx, cz, rr, cy + i, b)
    else poner(cx, cy + i, cz, b) end
  end
end

local function linea(x1, z1, x2, z2, y, b)
  local dx, dz = x2 - x1, z2 - z1
  local n = max(abs(dx), abs(dz))
  if n == 0 then poner(x1, y, z1, b); return end
  for i = 0, n do
    local t = i / n
    poner(x1 + dx * t, y, z1 + dz * t, b)
  end
end

local function linea3(x1, y1, z1, x2, y2, z2, b)
  local dx, dy, dz = x2 - x1, y2 - y1, z2 - z1
  local n = max(abs(dx), abs(dy), abs(dz))
  if n == 0 then poner(x1, y1, z1, b); return end
  for i = 0, n do
    local t = i / n
    poner(x1 + dx * t, y1 + dy * t, z1 + dz * t, b)
  end
end


-- ###########################################################################
-- ##  5. ELEMENTOS ARQUITECTONICOS                                         ##
-- ###########################################################################

--- Columna con basa y capitel.
local function columna(x, y, z, alto, b, b_rem)
  b_rem = b_rem or b
  for i = 1, alto - 2 do poner(x, y + i, z, eje(b, "y")) end
  poner(x, y, z, b_rem)
  poner(x, y + alto - 1, z, b_rem)
  for _, d in ipairs({ {1,0}, {-1,0}, {0,1}, {0,-1} }) do
    poner(x + d[1], y, z + d[2], losa(b_rem, "bottom"))
    poner(x + d[1], y + alto - 1, z + d[2], losa(b_rem, "top"))
  end
end

--- Columna con una vena de luz. La luz sale de la estructura, no de lamparas.
local function columna_luz(x, y, z, alto, b, b_luz)
  for i = 0, alto - 1 do poner(x, y + i, z, eje(b, "y")) end
  for i = 2, alto - 3 do
    if i % 3 ~= 0 then
      for _, d in ipairs({ {1,0}, {-1,0}, {0,1}, {0,-1} }) do
        poner(x + d[1], y + i, z + d[2], b_luz)
      end
    end
  end
end

--- Columnata recta entre dos puntos.
local function columnata(x1, z1, x2, z2, y, alto, paso, b, b_rem)
  local dx, dz = x2 - x1, z2 - z1
  local n = max(abs(dx), abs(dz))
  if n < paso then return end
  local pasos = floor(n / paso)
  for i = 0, pasos do
    local t = (i * paso) / n
    columna(redondear(lerp(x1, x2, t)), y, redondear(lerp(z1, z2, t)), alto, b, b_rem)
  end
end

--- ⚠⚠ EL ARCO DE PASO. Es la pieza mas importante del edificio.
---
--- El palacio anterior fallo por sellar los accesos. Aqui todos los vanos se
--- abren con esta funcion, que VACIA el hueco de verdad -- suelo despejado,
--- 5 de alto libre -- y luego lo enmarca. No pone puerta, ni valla, ni
--- vidrio: un arco por el que se pasa andando y se ve al otro lado.
---
---   (x,y,z)     centro del vano, a nivel de SUELO
---   dirx,dirz   direccion del MURO (a lo largo)
---   grosor      cuantos bloques de muro hay que atravesar
local function arco_paso(x, y, z, dirx, dirz, semiancho, alto, grosor, b_marco, b_luz)
  grosor = grosor or 2
  local nx, nz = -dirz, dirx        -- normal: hacia donde se atraviesa

  -- 1. VACIAR. Se perfora el muro entero, no solo su cara.
  for g = -grosor, grosor do
    for i = -semiancho, semiancho do
      local h = redondear(sqrt(max(0, semiancho * semiancho - i * i)))
      for j = 0, alto + h do
        poner(x + dirx * i + nx * g, y + j, z + dirz * i + nz * g, P.aire)
      end
    end
  end

  -- 2. Jambas
  for g = -grosor, grosor do
    for j = 0, alto do
      poner(x + dirx * (semiancho + 1) + nx * g, y + j, z + dirz * (semiancho + 1) + nz * g, b_marco)
      poner(x - dirx * (semiancho + 1) + nx * g, y + j, z - dirz * (semiancho + 1) + nz * g, b_marco)
    end
  end

  -- 3. Arco, en las dos caras
  for _, g in ipairs({ -grosor, grosor }) do
    for i = -semiancho - 1, semiancho + 1 do
      local ii = acotar(i, -semiancho, semiancho)
      local h = redondear(sqrt(max(0, semiancho * semiancho - ii * ii)))
      poner(x + dirx * i + nx * g, y + alto + h + 1, z + dirz * i + nz * g, b_marco)
    end
  end

  -- 4. Luz en el dintel: es lo que dice "por aqui se pasa" desde lejos
  if b_luz then
    for i = -semiancho, semiancho do
      poner(x + dirx * i, y + alto + redondear(sqrt(max(0, semiancho*semiancho - i*i))) + 2,
            z + dirz * i, b_luz)
    end
  end
end

--- Ventanal con arco.
local function ventanal(x, y, z, dirx, dirz, semiancho, alto, b_marco, b_vidrio)
  for i = -semiancho, semiancho do
    local h = redondear(sqrt(max(0, semiancho * semiancho - i * i)))
    for j = 0, alto + h do
      poner(x + dirx * i, y + j, z + dirz * i, b_vidrio)
    end
    poner(x + dirx * i, y + alto + h + 1, z + dirz * i, b_marco)
  end
  for j = -1, alto do
    poner(x + dirx * (semiancho + 1), y + j, z + dirz * (semiancho + 1), b_marco)
    poner(x - dirx * (semiancho + 1), y + j, z - dirz * (semiancho + 1), b_marco)
  end
end

--- Cornisa recta de tres hiladas. Separa "pared que acaba" de "pared
--- rematada", que es la mitad de lo que distingue un palacio de una caja.
local function cornisa(x1, z1, x2, z2, y, b, b_losa)
  b_losa = b_losa or b
  marco(x1, z1, x2, z2, y, b)
  marco(x1 - 1, z1 - 1, x2 + 1, z2 + 1, y + 1, b)
  marco(x1 - 2, z1 - 2, x2 + 2, z2 + 2, y + 2, losa(b_losa, "bottom"))
end

--- Friso de luz corrido, a la altura de la vista.
local function friso(x1, z1, x2, z2, y, b)
  marco(x1, z1, x2, z2, y, b)
end

--- Escalinata recta.
local function escalinata(x0, y0, z0, dirx, dirz, ancho, pasos, b_esc, b_masa, prof)
  local px, pz = -dirz, dirx
  local f = facing_de(dirx, dirz)
  local m = floor(ancho / 2)
  prof = prof or 4
  for i = 0, pasos - 1 do
    local bx, bz, by = x0 + dirx * i, z0 + dirz * i, y0 + i
    for l = -m, m do
      local x, z = bx + px * l, bz + pz * l
      poner(x, by, z, escalera(b_esc, f, "bottom"))
      if b_masa then
        for yy = by - prof, by - 1 do poner(x, yy, z, b_masa) end
      end
    end
  end
end

--- Barandilla: muro bajo con pasamanos.
local function barandilla(x1, z1, x2, z2, y, b, b_pas)
  linea(x1, z1, x2, z2, y, b)
  linea(x1, z1, x2, z2, y + 1, losa(b_pas or b, "bottom"))
end

--- Artesonado: techo de casetones.
local function artesonado(x1, z1, x2, z2, y, b_fondo, b_nervio, paso)
  paso = paso or 5
  rect(x1, z1, x2, z2, y, b_fondo)
  for x = x1, x2 do for z = z1, z2 do
    if x % paso == 0 or z % paso == 0 then
      poner(x, y, z, b_nervio)
      poner(x, y - 1, z, losa(b_nervio, "top"))
    end
  end end
end

--- ⚠ LA PLATAFORMA DE ENTRENADOR. Se deja VACIA, por decision del usuario.
---
--- No lleva ni soporte de armadura, ni spawner, ni marcador solido que luego
--- haya que picar. Solo el estrado, su aro de color y las cuatro esquinas
--- marcadas para poder centrar al entrenador a ojo. El hueco de encima se
--- vacia explicitamente: un bloque suelto ahi impediria colocarlo.
local function plataforma(cx, y, cz, r, b_base, b_aro, b_luz)
  disco(cx, cz, r + 1, y, b_base)
  circulo(cx, cz, r + 1, y, b_aro)
  disco(cx, cz, r, y + 1, b_base)
  circulo(cx, cz, r, y + 1, b_aro)
  if b_luz then
    for _, d in ipairs({ {1,1}, {1,-1}, {-1,1}, {-1,-1} }) do
      poner(cx + d[1] * (r + 1), y + 1, cz + d[2] * (r + 1), b_luz)
    end
  end
  caja(cx - r, y + 2, cz - r, cx + r, y + 5, cz + r, P.aire)
end

--- Plataforma rectangular, para podios y estrados grandes.
local function estrado(cx, y, cz, ax, az, b_base, b_borde, b_luz)
  caja(cx - ax - 1, y, cz - az - 1, cx + ax + 1, y, cz + az + 1, b_base)
  caja(cx - ax, y + 1, cz - az, cx + ax, y + 1, cz + az, b_base)
  marco(cx - ax, cz - az, cx + ax, cz + az, y + 1, b_borde)
  if b_luz then
    for _, d in ipairs({ {1,1}, {1,-1}, {-1,1}, {-1,-1} }) do
      poner(cx + d[1] * ax, y + 1, cz + d[2] * az, b_luz)
    end
  end
  caja(cx - ax, y + 2, cz - az, cx + ax, y + 5, cz + az, P.aire)
end

--- Pedestal estrecho, para insignias y vitrinas. Tambien vacio.
local function pedestal(x, y, z, alto, b, b_luz)
  for i = 0, alto - 1 do poner(x, y + i, z, eje(b, "y")) end
  for _, d in ipairs({ {1,0}, {-1,0}, {0,1}, {0,-1} }) do
    poner(x + d[1], y, z + d[2], losa(b, "bottom"))
    poner(x + d[1], y + alto - 1, z + d[2], losa(b, "top"))
  end
  if b_luz then poner(x, y + alto, z, b_luz) end
  caja(x - 1, y + alto + 1, z - 1, x + 1, y + alto + 3, z + 1, P.aire)
end

--- Farola.
local function farola(x, y, z, alto, b, b_luz)
  for i = 0, alto - 2 do poner(x, y + i, z, eje(b, "y")) end
  poner(x, y + alto - 1, z, b_luz)
  for _, d in ipairs({ {1,0}, {-1,0}, {0,1}, {0,-1} }) do
    poner(x + d[1], y + alto - 1, z + d[2], losa(b, "top"))
  end
end


-- ###########################################################################
-- ##  5-bis. EL PLANO — donde cae cada cosa                                ##
-- ###########################################################################
--
-- Todo el edificio sale de esta tabla. Tenerla en UN sitio es lo que permite
-- que la comprobacion de recorrido (seccion 13) sepa donde esta cada sala sin
-- adivinarlo, y lo que hace que mover una pieza no descuadre las demas.
--
-- Coordenadas LOCALES respecto al centro. Se suman cx,cz al construir.
--
--   galeria   el anillo de circulacion, cuadrado, de -42 a 42
--   muro      la cara interior del muro exterior, a 76
--   salas     bandas de 44 a 74, en los cuatro lados
--
-- Las salas NO llegan a las esquinas: ahi van las cuatro torres.

local G  = 42     -- borde interior del muro de la galeria
local GW = 43     -- el muro de la galeria en si
local S1 = 44     -- donde empiezan las salas
local S2 = 74     -- donde acaban
local MU = 76     -- cara interior del muro exterior

--- Las tres franjas paralelas al muro en las que se parten las salas.
local FRANJAS = { { 14, 38 }, { -12, 12 }, { -38, -14 } }

--- Devuelve el rectangulo de una sala.
---   lado  "S", "E", "N", "W"
---   i     1..3 (en el orden de recorrido horario desde la entrada)
--- El rectangulo va en {x1, z1, x2, z2}, coordenadas locales.
local function rect_sala(lado, i)
  local f = FRANJAS[i]
  if lado == "S" then return { f[1], S1, f[2], S2 } end
  if lado == "N" then return { f[1], -S2, f[2], -S1 } end
  if lado == "E" then return { S1, f[1], S2, f[2] } end
  return { -S2, f[1], -S1, f[2] }          -- W
end

--- El punto donde se abre el arco que comunica una sala con la galeria, y la
--- direccion del muro que atraviesa.
local function boca_sala(lado, i)
  local f = FRANJAS[i]
  local c = redondear((f[1] + f[2]) / 2)
  if lado == "S" then return c, GW, 1, 0 end       -- muro corre en X
  if lado == "N" then return c, -GW, 1, 0 end
  if lado == "E" then return GW, c, 0, 1 end       -- muro corre en Z
  return -GW, c, 0, 1                              -- W
end

--- ⚠ EL ORDEN ES EL DEL RECORRIDO, y no es decorativo: define que sala se ve
--- primero. La 1 va pegada al vestibulo, a mano derecha segun se entra.
---
---   { numero, nombre, region, lado, franja }
local SALAS = {
  { 1, "KANTO",  "S", 1 },   -- pegada al vestibulo, al este
  { 2, "JOHTO",  "E", 1 },
  { 3, "HOENN",  "E", 2 },
  { 4, "SINNOH", "E", 3 },
  { 5, "TESELIA","N", 1 },
  { 6, "KALOS",  "N", 2 },
  { 7, "ALOLA",  "N", 3 },
  { 8, "GALAR",  "W", 1 },
  { 9, "PALDEA", "W", 2 },
}

--- El vestibulo: la franja central del lado sur.
local VEST = { -12, S1, 12, S2 }
--- Servicios: lo que queda libre en el sur y en el oeste.
local SERV_S = { -38, S1, -14, S2 }
--- ⚠ SERV_W VA EN LA FRANJA 3, NO EN LA 1. Estaba en {-S2, 14, -S1, 38}, que
--- es EXACTAMENTE el rectangulo de la sala 8 (Galar): el cuarto de servicio se
--- construia encima, con sus cuatro paredes, y la sellaba.
--- Lo encontro `comprobar_recorrido()`, no la vista: sobre el plano las dos
--- piezas estaban bien, y el fallo solo existia en que ocupaban el mismo sitio.
local SERV_W = { -S2, -38, -S1, -14 }

--- ¿Esta sala entra en esta ejecucion?
local function sala_pedida(n)
  if #CFG.solo_salas == 0 then return true end
  for _, k in ipairs(CFG.solo_salas) do if k == n then return true end end
  return false
end


-- ###########################################################################
-- ##  6. LA PLAZA MULTINIVEL                                               ##
-- ###########################################################################
--
-- Tres terrazas que bajan hacia el sur, con la escalinata en el eje. Es lo
-- que da al palacio su "llegada": un edificio que arranca directamente del
-- suelo no impone, y con 3 rellanos y 12 escalones si.
--
-- El rotulo de la plaza va incrustado en el pavimento del rellano bajo, que es
-- justo lo que hacen las imagenes de referencia.

local function construir_plaza(cx, cz)
  local y = CFG.y
  local z0 = cz + MU + 2                 -- pie del muro sur

  -- Explanada delante del palacio, tres terrazas de 5 de caida
  local anchos = { 90, 108, 126 }
  for k = 1, 3 do
    local a = anchos[k]
    local yy = y - (k - 1) * 4
    local zi = z0 + (k - 1) * 22
    local zf = zi + 22

    rect(cx - a, zi, cx + a, zf, yy - 1, P.andesita)
    -- Cenefa de losa clara
    marco(cx - a, zi, cx + a, zf, yy - 1, P.cuarzo_liso)
    -- Damero en el centro
    for x = cx - a + 4, cx + a - 4 do
      for z = zi + 4, zf - 4 do
        if damero(x, z, 4) then poner(x, yy - 1, z, P.esmeril) end
      end
    end
    -- Canto de la terraza, para que no parezca una losa flotando
    for i = 1, 4 do
      marco(cx - a + i, zi + i, cx + a - i, zf, yy - 1 - i, P.pizarra_ladr)
    end

    -- Escalones de bajada a la terraza siguiente, en todo el ancho del eje
    if k < 3 then
      for i = 0, 3 do
        for x = cx - 24, cx + 24 do
          poner(x, yy - 1 - i, zf + i, escalera(P.cuarzo_liso_esc, "north", "bottom"))
          for yb = yy - 5 - i, yy - 2 - i do poner(x, yb, zf + i, P.andesita) end
        end
      end
    end

    -- Farolas en el borde
    for x = cx - a + 8, cx + a - 8, 18 do
      farola(x, yy, zi + 3, 6, P.pizarra, P.farol_mar)
      farola(x, yy, zf - 3, 6, P.pizarra, P.farol_mar)
    end
  end

  -- Escalinata principal, del pie del muro a la primera terraza
  escalinata(cx, y, z0 - 1, 0, 1, 33, 1, P.cuarzo_liso_esc, P.cuarzo_liso, 6)

  -- Parterres verdes a los lados del eje, como en la referencia
  for _, s in ipairs({ -1, 1 }) do
    for k = 0, 2 do
      local bx = cx + s * (34 + k * 20)
      local bz = z0 + 8
      rect(bx - 7, bz, bx + 7, bz + 14, y - 1, P.musgo)
      marco(bx - 7, bz, bx + 7, bz + 14, y - 1, P.cuarzo_liso)
      for x = bx - 6, bx + 6, 3 do
        for z = bz + 2, bz + 12, 3 do
          poner(x, y, z, P.hoja_azalea)
          poner(x, y + 1, z, P.rana_verde)
        end
      end
    end
  end
end


-- ###########################################################################
-- ##  7. EL CUERPO DEL PALACIO: muros, torres y fachada                    ##
-- ###########################################################################

local function construir_muros(cx, cz)
  local y, h = CFG.y, CFG.alto

  -- Losa estructural del edificio entero
  rect(cx - MU - 2, cz - MU - 2, cx + MU + 2, cz + MU + 2, y - 1, P.andesita)
  -- Canto escalonado, que se ve desde la plaza
  for i = 1, 5 do
    marco(cx - MU - 2 + i, cz - MU - 2 + i, cx + MU + 2 - i, cz + MU + 2 - i,
          y - 1 - i, (i % 2 == 0) and P.pizarra_ladr or P.pizarra_tejas)
  end

  -- Muro exterior, dos hojas
  paredes(cx - MU, y, cz - MU, cx + MU, y + h - 1, cz + MU, P.cuarzo_liso)
  paredes(cx - MU - 1, y, cz - MU - 1, cx + MU + 1, y + h - 1, cz + MU + 1, P.blanco)

  -- Zocalo oscuro
  paredes(cx - MU - 1, y, cz - MU - 1, cx + MU + 1, y + 2, cz + MU + 1, P.pizarra)

  -- Orden gigante de pilastras cada 8, en las cuatro caras
  for d = -MU, MU, 8 do
    for _, p in ipairs({
      { cx + d, cz - MU - 1 }, { cx + d, cz + MU + 1 },
      { cx - MU - 1, cz + d }, { cx + MU + 1, cz + d },
    }) do
      for yy = y, y + h - 1 do poner(p[1], yy, p[2], eje(P.cuarzo_pilar, "y")) end
      poner(p[1], y + h, p[2], P.cuarzo_cincel)
      poner(p[1], y - 1, p[2], P.pizarra)
    end
  end

  -- Ventanales altos entre pilastras
  for d = -MU + 4, MU - 4, 8 do
    ventanal(cx + d, y + 5, cz - MU - 1, 1, 0, 2, 6, P.cuarzo, P.vidrio_cian)
    ventanal(cx + d, y + 5, cz + MU + 1, 1, 0, 2, 6, P.cuarzo, P.vidrio_cian)
    ventanal(cx - MU - 1, y + 5, cz + d, 0, 1, 2, 6, P.cuarzo, P.vidrio_cian)
    ventanal(cx + MU + 1, y + 5, cz + d, 0, 1, 2, 6, P.cuarzo, P.vidrio_cian)
  end

  -- Friso de luz corrido a media altura: la linea que dibuja el palacio de
  -- noche, y lo que mas se parece a las referencias
  friso(cx - MU - 2, cz - MU - 2, cx + MU + 2, cz + MU + 2, y + 4, P.farol_mar)

  -- Entablamento y cornisa
  cornisa(cx - MU - 1, cz - MU - 1, cx + MU + 1, cz + MU + 1, y + h, P.cuarzo_liso, P.cuarzo)
  friso(cx - MU - 3, cz - MU - 3, cx + MU + 3, cz + MU + 3, y + h + 3, P.rana_verde)

  -- Antepecho de la azotea
  for i = 0, 1 do
    marco(cx - MU - 1, cz - MU - 1, cx + MU + 1, cz + MU + 1, y + h + 4 + i, P.cuarzo_liso)
  end
  -- Almenado ligero
  for d = -MU, MU, 4 do
    poner(cx + d, y + h + 6, cz - MU - 1, P.cuarzo_cincel)
    poner(cx + d, y + h + 6, cz + MU + 1, P.cuarzo_cincel)
    poner(cx - MU - 1, y + h + 6, cz + d, P.cuarzo_cincel)
    poner(cx + MU + 1, y + h + 6, cz + d, P.cuarzo_cincel)
  end

  -- Techo de la planta (sobre las salas y la galeria; el nucleo lleva domo)
  rect(cx - MU, cz - MU, cx + MU, cz + MU, y + h, P.cuarzo_liso)
  -- Y se abre el hueco del nucleo, que va cubierto por el domo
  disco(cx, cz, CFG.r_nucleo - 1, y + h, P.aire)
end


--- Las cuatro torres de esquina. Ademas de la silueta, son SALIDAS: cada una
--- tiene puerta a nivel de suelo hacia fuera y arco hacia la galeria.
local function construir_torres(cx, cz)
  local y, h = CFG.y, CFG.alto
  local L = 16
  local alto = h + 22

  local esquinas = {
    {  1,  1 }, {  1, -1 }, { -1,  1 }, { -1, -1 },
  }

  for _, e in ipairs(esquinas) do
    local sx, sz = e[1], e[2]
    local x1, z1 = cx + sx * (MU - L), cz + sz * (MU - L)
    local x2, z2 = cx + sx * MU, cz + sz * MU
    if x1 > x2 then x1, x2 = x2, x1 end
    if z1 > z2 then z1, z2 = z2, z1 end
    local mx, mz = redondear((x1 + x2) / 2), redondear((z1 + z2) / 2)

    -- Fuste
    paredes(x1, y, z1, x2, y + alto, z2, P.cuarzo_liso)
    paredes(x1 + 1, y, z1 + 1, x2 - 1, y + alto, z2 - 1, P.blanco_terra)
    vaciar(x1 + 2, y, z1 + 2, x2 - 2, y + alto - 1, z2 - 2)
    rect(x1, z1, x2, z2, y - 1, P.pizarra_tejas)

    -- Aristas marcadas
    for _, c in ipairs({ {x1,z1}, {x1,z2}, {x2,z1}, {x2,z2} }) do
      for yy = y, y + alto do poner(c[1], yy, c[2], eje(P.cuarzo_pilar, "y")) end
    end

    -- Forjados intermedios con hueco, y escalera de caracol de acceso
    for k = 1, 3 do
      local yy = y + k * 8
      rect(x1 + 2, z1 + 2, x2 - 2, z2 - 2, yy, P.pizarra_tejas)
      -- hueco de la escalera
      vaciar(mx - 2, yy, mz - 2, mx + 2, yy, mz + 2)
    end
    -- Caracol
    local pasos = alto - 2
    for i = 0, pasos do
      local a = (i / 8) * (pi / 2)
      local px, pz = polar(mx, mz, 3, a)
      poner(px, y + i, pz, P.pizarra_esc)
      poner(px, y + i - 1, pz, P.pizarra)
      if i % 8 == 0 then poner(px, y + i + 3, pz, P.farol_mar) end
    end

    -- Ventanas por nivel
    for k = 0, 3 do
      local yy = y + 3 + k * 8
      ventanal(mx, yy, z1, 1, 0, 2, 3, P.cuarzo, P.vidrio_cian)
      ventanal(mx, yy, z2, 1, 0, 2, 3, P.cuarzo, P.vidrio_cian)
      ventanal(x1, yy, mz, 0, 1, 2, 3, P.cuarzo, P.vidrio_cian)
      ventanal(x2, yy, mz, 0, 1, 2, 3, P.cuarzo, P.vidrio_cian)
    end

    -- ⚠ PUERTA EXTERIOR: la torre es una SALIDA. Sin esto el palacio tendria
    -- una sola boca y volveria a sentirse cerrado.
    if sz > 0 then
      arco_paso(mx, y, z2, 1, 0, 2, 4, 2, P.cuarzo, P.rana_verde)
    else
      arco_paso(mx, y, z1, 1, 0, 2, 4, 2, P.cuarzo, P.rana_verde)
    end
    -- Y arco hacia el interior del palacio
    if sx > 0 then
      arco_paso(x1, y, mz, 0, 1, 2, 4, 2, P.cuarzo, P.rana_verde)
    else
      arco_paso(x2, y, mz, 0, 1, 2, 4, 2, P.cuarzo, P.rana_verde)
    end

    -- Mirador y remate
    local ym = y + alto
    for i = 0, 2 do
      marco(x1 - i, z1 - i, x2 + i, z2 + i, ym + i, P.cuarzo_liso)
    end
    rect(x1 - 1, z1 - 1, x2 + 1, z2 + 1, ym + 3, P.cuarzo_liso)
    for i = 0, 2 do
      marco(x1 - 1 + i, z1 - 1 + i, x2 + 1 - i, z2 + 1 - i, ym + 4 + i, P.pizarra_ladr)
    end
    -- Cupulita
    cascara(mx, ym + 7, mz, 7, P.pizarra_tejas, 2, ym + 7)
    circulo(mx, mz, 7, ym + 7, P.cobre)
    -- Aguja de luz
    for i = 1, 8 do poner(mx, ym + 13 + i, mz, P.varilla) end
    poner(mx, ym + 22, mz, P.farol_mar)
    -- Emblema de Poke Ball en la cara exterior de la torre
    for dx = -3, 3 do
      for dy = -3, 3 do
        if dx*dx + dy*dy <= 10 then
          local b = (dy > 0) and P.rojo or P.blanco
          if dy == 0 then b = P.negro end
          if dx*dx + dy*dy <= 1 then b = P.blanco end
          local zz = (sz > 0) and z2 + 1 or z1 - 1
          poner(mx + dx, ym - 6 + dy, zz, b)
        end
      end
    end
  end
end


--- La fachada sur: portico, fronton y el gran arco de entrada.
local function construir_fachada(cx, cz)
  local y, h = CFG.y, CFG.alto
  local zf = cz + MU + 1

  -- Cuerpo central adelantado
  local a = 26
  paredes(cx - a, y, zf, cx + a, y + h + 6, zf + 6, P.cuarzo_liso)
  rect(cx - a, zf, cx + a, zf + 6, y - 1, P.cuarzo_liso)
  rect(cx - a, zf, cx + a, zf + 6, y + h + 7, P.cuarzo_liso)
  vaciar(cx - a + 1, y, zf + 1, cx + a - 1, y + h + 6, zf + 5)

  -- Columnata del portico
  for d = -a + 3, a - 3, 6 do
    columna_luz(cx + d, y, zf + 5, h + 6, P.cuarzo_pilar, P.rana_verde)
  end

  -- Entablamento
  for x = cx - a - 2, cx + a + 2 do
    poner(x, y + h + 7, zf + 5, P.cuarzo_liso)
    poner(x, y + h + 8, zf + 5, P.cuarzo_cincel)
    poner(x, y + h + 9, zf + 5, losa(P.cuarzo_losa, "bottom"))
  end

  -- Fronton triangular
  for i = 0, a + 2 do
    for x = cx - a - 2 + i, cx + a + 2 - i do
      poner(x, y + h + 10 + i, zf + 5, P.blanco_terra)
    end
    poner(cx - a - 2 + i, y + h + 10 + i, zf + 5, P.cuarzo_liso)
    poner(cx + a + 2 - i, y + h + 10 + i, zf + 5, P.cuarzo_liso)
  end
  -- Oculo del fronton con Poke Ball
  for dx = -5, 5 do
    for dy = -5, 5 do
      if dx*dx + dy*dy <= 26 then
        local b = (dy > 0) and P.rojo or P.blanco
        if dy == 0 then b = P.negro end
        if dx*dx + dy*dy <= 2 then b = P.blanco end
        poner(cx + dx, y + h + 20 + dy, zf + 5, b)
      end
    end
  end

  -- ⚠ EL GRAN ARCO DE ENTRADA. Atraviesa la fachada Y el muro exterior de
  -- una vez: se perfora todo el grosor para que se entre de verdad.
  arco_paso(cx, y, zf + 3, 1, 0, 6, 8, 6, P.cuarzo_liso, P.rana_verde)
  arco_paso(cx, y, cz + MU, 1, 0, 6, 8, 3, P.cuarzo_liso, P.rana_verde)

  -- Alfombra de acceso
  for z = cz + MU - 2, zf + 8 do
    for x = cx - 5, cx + 5 do poner(x, y - 1, z, P.rojo_terra) end
    poner(cx - 6, y - 1, z, P.oro)
    poner(cx + 6, y - 1, z, P.oro)
  end

  -- Dos torretas menores flanqueando el portico
  for _, s in ipairs({ -1, 1 }) do
    local tx = cx + s * (a + 6)
    paredes(tx - 4, y, zf, tx + 4, y + h + 12, zf + 8, P.cuarzo_liso)
    vaciar(tx - 3, y, zf + 1, tx + 3, y + h + 12, zf + 7)
    rect(tx - 4, zf, tx + 4, zf + 8, y - 1, P.pizarra_tejas)
    rect(tx - 4, zf, tx + 4, zf + 8, y + h + 13, P.cuarzo_liso)
    cascara(tx, y + h + 14, redondear(zf + 4), 5, P.pizarra_tejas, 2, y + h + 14)
    for i = 1, 6 do poner(tx, y + h + 19 + i, zf + 4, P.varilla) end
    poner(tx, y + h + 26, zf + 4, P.farol_mar)
    -- Vena de luz vertical
    for yy = y + 2, y + h + 10, 2 do
      poner(tx + s * 4, yy, zf + 4, P.rana_verde)
    end
  end
end


-- ###########################################################################
-- ##  8. EL VESTIBULO                                                      ##
-- ###########################################################################
--
-- ⚠ ESTA PIEZA EXISTE PARA RESOLVER LA QUEJA PRINCIPAL: al entrar no se veia
-- nada y todo parecia cerrado.
--
-- Desde el umbral se tienen que ver TRES cosas a la vez, y por eso el
-- vestibulo esta alineado y perforado como esta:
--
--   1. Al frente, el arco de la galeria y detras el nucleo bajo el domo.
--   2. A la derecha, el arco de la SALA 1 (Kanto), grande e iluminado.
--   3. Arriba, el techo abierto en linterna, que dice que hay mas plantas.
--
-- No lleva NI UNA puerta. Los tres vanos son arcos abiertos de 7 de ancho.

local function construir_vestibulo(cx, cz)
  local y, h = CFG.y, CFG.alto
  local x1, z1, x2, z2 = VEST[1], VEST[2], VEST[3], VEST[4]
  x1, z1, x2, z2 = cx + x1, cz + z1, cx + x2, cz + z2

  -- Recinto
  rect(x1, z1, x2, z2, y - 1, P.cuarzo_liso)
  paredes(x1, y, z1, x2, y + h - 1, z2, P.blanco_terra)
  vaciar(x1 + 1, y, z1 + 1, x2 - 1, y + h - 1, z2 - 1)

  -- Suelo: alfombra central y cenefa
  for x = x1 + 3, x2 - 3 do
    for z = z1 + 2, z2 - 2 do
      poner(x, y - 1, z, damero(x, z, 3) and P.cuarzo_liso or P.calcita)
    end
  end
  for z = z1 + 2, z2 - 2 do
    for x = cx - 4, cx + 4 do poner(x, y - 1, z, P.rojo_terra) end
    poner(cx - 5, y - 1, z, P.oro)
    poner(cx + 5, y - 1, z, P.oro)
  end

  -- Columnata a los lados
  for z = z1 + 5, z2 - 5, 7 do
    columna_luz(x1 + 3, y, z, h - 4, P.cuarzo_pilar, P.rana_verde)
    columna_luz(x2 - 3, y, z, h - 4, P.cuarzo_pilar, P.rana_verde)
  end

  -- Techo: artesonado con linterna central abierta
  artesonado(x1 + 1, z1 + 1, x2 - 1, z2 - 1, y + h - 1, P.cuarzo_liso, P.cobre, 6)
  local mz = redondear((z1 + z2) / 2)
  vaciar(cx - 5, y + h - 1, mz - 5, cx + 5, y + h - 1, mz + 5)
  for i = 0, 3 do
    marco(cx - 5 - i, mz - 5 - i, cx + 5 + i, mz + 5 + i, y + h + i, P.cuarzo_liso)
  end
  rect(cx - 5, mz - 5, cx + 5, mz + 5, y + h + 4, P.vidrio_tintado)
  friso(cx - 6, mz - 6, cx + 6, mz + 6, y + h - 1, P.farol_mar)

  -- ⚠ LOS TRES VANOS. Sin estos el vestibulo es una caja.
  --
  -- 1. Al fondo (norte), hacia la galeria y el nucleo
  arco_paso(cx, y, z1, 1, 0, 6, 8, 3, P.cuarzo_liso, P.rana_verde)
  -- 2. A la derecha (este), DIRECTO a la sala 1
  arco_paso(x2, y, mz, 0, 1, 6, 8, 2, P.cuarzo_liso, P.rana_verde)
  -- 3. A la izquierda (oeste), a los servicios
  arco_paso(x1, y, mz, 0, 1, 5, 7, 2, P.cuarzo_liso, P.farol_mar)

  -- Bancos corridos contra los muros
  for z = z1 + 3, z2 - 3 do
    poner(x1 + 1, y, z, losa(P.cuarzo_losa, "bottom"))
    poner(x2 - 1, y, z, losa(P.cuarzo_losa, "bottom"))
  end
end


-- ###########################################################################
-- ##  9. LA GALERIA ANULAR — la circulacion                                ##
-- ###########################################################################
--
-- El anillo cuadrado que rodea el nucleo y da acceso a las nueve salas. Es la
-- pieza que convierte el palacio en un CIRCUITO: se entra, se recorre, y se
-- sale por donde se quiera. Sin ella cada sala seria un callejon.
--
-- Los cuatro rincones del anillo son mas anchos que los tramos rectos (el
-- cuadrado contra el circulo del nucleo), y eso se aprovecha: ahi van los
-- accesos a las torres y unos estanques.

local function construir_galeria(cx, cz)
  local y, h = CFG.y, CFG.alto

  -- Suelo del anillo: el cuadrado menos el circulo del nucleo
  for x = -G, G do
    for z = -G, G do
      if dist2(x, z) > CFG.r_nucleo then
        local b = damero(cx + x, cz + z, 4) and P.calcita or P.cuarzo_liso
        -- Banda de rodadura mas clara junto al nucleo
        if dist2(x, z) < CFG.r_nucleo + 5 then b = P.cuarzo_liso end
        poner(cx + x, y - 1, cz + z, b)
      end
    end
  end

  -- Muro exterior de la galeria, con los vanos a las salas
  paredes(cx - GW, y, cz - GW, cx + GW, y + h - 1, cz + GW, P.cuarzo_liso)
  paredes(cx - GW - 1, y, cz - GW - 1, cx + GW + 1, y + h - 1, cz + GW + 1, P.blanco_terra)

  -- Columnata del anillo
  for d = -G + 4, G - 4, 7 do
    columna(cx + d, y, cz - G + 2, h - 3, P.cuarzo_pilar, P.cuarzo_cincel)
    columna(cx + d, y, cz + G - 2, h - 3, P.cuarzo_pilar, P.cuarzo_cincel)
    columna(cx - G + 2, y, cz + d, h - 3, P.cuarzo_pilar, P.cuarzo_cincel)
    columna(cx + G - 2, y, cz + d, h - 3, P.cuarzo_pilar, P.cuarzo_cincel)
  end

  -- Friso de luz corrido a la altura de la vista
  friso(cx - G, cz - G, cx + G, cz + G, y + 5, P.farol_mar)
  -- Cornisa alta
  cornisa(cx - G, cz - G, cx + G, cz + G, y + h - 4, P.cuarzo_liso, P.cobre)

  -- Techo del anillo, con lucernarios
  for x = -GW, GW do
    for z = -GW, GW do
      if dist2(x, z) > CFG.r_nucleo + 1 then
        local b = P.cuarzo_liso
        if (abs(x) % 9 == 0) and (abs(z) % 9 == 0) then b = P.vidrio_tintado end
        poner(cx + x, y + h, cz + z, b)
      end
    end
  end

  -- Rincones: estanques y acceso a las torres
  for _, e in ipairs({ {1,1}, {1,-1}, {-1,1}, {-1,-1} }) do
    local ex, ez = cx + e[1] * 34, cz + e[2] * 34
    disco(ex, ez, 7, y - 1, P.prismarina_o)
    disco(ex, ez, 6, y - 2, P.agua)
    circulo(ex, ez, 7, y - 1, P.prismarina_l)
    circulo(ex, ez, 8, y - 1, P.cuarzo_liso)
    for i = 0, 3 do
      local a = i * (pi / 2) + pi / 4
      local px, pz = polar(ex, ez, 8, a)
      farola(px, y, pz, 5, P.pizarra, P.farol_mar)
    end
    -- Y un paso hacia la torre de esa esquina
    linea(ex, ez, cx + e[1] * (MU - 8), cz + e[2] * (MU - 8), y - 1, P.cuarzo_liso)
  end

  -- ⚠ LOS NUEVE VANOS A LAS SALAS. Se abren aqui y no en cada sala para que
  -- todos midan lo mismo y esten centrados por construccion.
  for _, s in ipairs(SALAS) do
    local n, lado, fr = s[1], s[3], s[4]
    if sala_pedida(n) then
      local bx, bz, dx, dz = boca_sala(lado, fr)
      arco_paso(cx + bx, y, cz + bz, dx, dz, 3, 7, 2, P.cuarzo_liso, P.rana_verde)
    end
  end

  -- Vano del vestibulo (sur, centro) ya lo abre el vestibulo; aqui se abren
  -- los de servicios para que tampoco queden ciegos.
  arco_paso(cx - 26, y, cz + GW, 1, 0, 3, 7, 2, P.cuarzo_liso, P.farol_mar)
  arco_paso(cx - GW, y, cz - 26, 0, 1, 3, 7, 2, P.cuarzo_liso, P.farol_mar)

  -- Salas de servicio, para que los huecos existan de verdad
  for _, r in ipairs({ SERV_S, SERV_W }) do
    local a1, b1, a2, b2 = cx + r[1], cz + r[2], cx + r[3], cz + r[4]
    recinto(a1, b1, a2, b2, y, h - 4, P.calcita, P.blanco_terra, P.cuarzo_liso)
    for x = a1 + 2, a2 - 2, 4 do
      for z = b1 + 2, b2 - 2, 4 do poner(x, y + h - 5, z, P.farol_mar) end
    end
    -- Bancos
    for x = a1 + 2, a2 - 2 do poner(x, y, b1 + 1, losa(P.cuarzo_losa, "bottom")) end
  end
end


-- ###########################################################################
-- ##  10. EL NUCLEO Y EL DOMO GEODESICO                                    ##
-- ###########################################################################
--
-- El corazon del palacio: una rotonda de 48 de diametro abierta por los
-- cuatro puntos cardinales, con el "Mon Core" en el centro -- un pilar de
-- energia que sube hasta el domo -- y encima el domo geodesico con el
-- despiece de una Poke Ball.
--
-- ⚠ EL DOMO SE DIBUJA POR LATITUD, no por bloque suelto: la mitad de arriba
-- roja, la de abajo blanca, y una banda negra con el boton en el ecuador. Asi
-- se lee como Poke Ball desde cualquier angulo, que es lo que pedian las
-- referencias.

local function construir_nucleo(cx, cz)
  local y, h = CFG.y, CFG.alto
  local r = CFG.r_nucleo

  -- Suelo en mandala
  for dx = -r, r do
    for dz = -r, r do
      local d = dist2(dx, dz)
      if d <= r then
        local b
        if d <= 5 then b = P.rana_perla
        elseif d <= 8 then b = P.oro
        elseif d <= 13 then b = P.calcita
        elseif d <= 17 then b = P.cobre
        else b = P.cuarzo_liso end
        poner(cx + dx, y - 1, cz + dz, b)
      end
    end
  end
  -- Radios
  for i = 0, 7 do
    local a = i * (pi / 4)
    local ex, ez = polar(cx, cz, r, a)
    linea(cx, cz, ex, ez, y - 1, P.pizarra_ladr)
  end
  circulo(cx, cz, r, y - 1, P.cobre)

  -- Muro de la rotonda
  pared_cil(cx, cz, r, y, y + h - 1, P.cuarzo_liso)
  pared_cil(cx, cz, r + 1, y, y + h - 1, P.blanco_terra)

  -- Columnata interior
  for i = 0, 15 do
    local a = i * (2 * pi / 16)
    local px, pz = polar(cx, cz, r - 4, a)
    columna_luz(px, y, pz, h - 2, P.cuarzo_pilar, P.rana_verde)
  end
  friso(cx - r, cz - r, cx + r, cz + r, y + 6, P.farol_mar)
  circulo(cx, cz, r, y + 6, P.farol_mar)

  -- ⚠ LOS CUATRO ACCESOS. La rotonda es el centro del recorrido: tiene que
  -- poder atravesarse en las dos direcciones, no ser un mirador cerrado.
  arco_paso(cx, y, cz + r, 1, 0, 5, 8, 3, P.cuarzo_liso, P.rana_verde)
  arco_paso(cx, y, cz - r, 1, 0, 5, 8, 3, P.cuarzo_liso, P.rana_verde)
  arco_paso(cx + r, y, cz, 0, 1, 5, 8, 3, P.cuarzo_liso, P.rana_verde)
  arco_paso(cx - r, y, cz, 0, 1, 5, 8, 3, P.cuarzo_liso, P.rana_verde)

  -- EL MON CORE: pilar de energia del suelo al domo
  cilindro(cx, cz, 3, y, CFG.y + CFG.domo_y - 2, P.vidrio_tintado)
  cilindro(cx, cz, 2, y, CFG.y + CFG.domo_y - 2, P.rana_perla)
  for yy = y + 3, CFG.y + CFG.domo_y - 4, 6 do
    circulo(cx, cz, 4, yy, P.cobre)
    circulo(cx, cz, 5, yy, P.oro)
    circulo(cx, cz, 4, yy + 1, losa(P.cobre_losa, "top"))
  end
  -- Basa del core
  for i = 0, 3 do circulo(cx, cz, 4 + i, y + 3 - i, P.pizarra_ladr) end
  disco(cx, cz, 7, y - 1, P.pizarra_tejas)
  circulo(cx, cz, 7, y - 1, P.rana_perla)

  -- Anillo de balcon a media altura, con acceso desde las torres
  local yb = y + 14
  anillo(cx, cz, r - 7, r - 1, yb, P.cuarzo_liso)
  circulo(cx, cz, r - 7, yb, P.cobre)
  circulo(cx, cz, r - 1, yb, P.cobre)
  for i = 0, 31 do
    local a = i * (2 * pi / 32)
    local px, pz = polar(cx, cz, r - 7, a)
    if i % 2 == 0 then poner(px, yb + 1, pz, P.rejas) end
  end
  -- Escaleras al balcon, dos, enfrentadas
  for _, s in ipairs({ 1, -1 }) do
    for i = 0, 14 do
      local a = pi / 2 + s * (i / 14) * (pi / 2)
      local px, pz = polar(cx, cz, r - 4, a)
      poner(px, y + i, pz, P.cuarzo_liso_esc)
      poner(px, y + i - 1, pz, P.cuarzo_liso)
    end
  end
end


--- El domo geodesico con el despiece de una Poke Ball.
local function construir_domo(cx, cz)
  local y = CFG.y + CFG.domo_y
  local r = CFG.domo_r

  -- Tambor: la banda vertical de la que arranca la curva
  pared_cil(cx, cz, CFG.r_nucleo + 1, CFG.y + CFG.alto, y - 1, P.cuarzo_liso)
  pared_cil(cx, cz, CFG.r_nucleo + 2, CFG.y + CFG.alto, y - 1, P.blanco_terra)
  for i = 0, 15 do
    local a = i * (2 * pi / 16)
    local px, pz = polar(cx, cz, CFG.r_nucleo + 2, a)
    ventanal(px, CFG.y + CFG.alto + 2, pz, redondear(-sin(a)), redondear(cos(a)),
             2, 4, P.cobre, P.vidrio_cian)
  end
  cornisa(cx - CFG.r_nucleo - 2, cz - CFG.r_nucleo - 2,
          cx + CFG.r_nucleo + 2, cz + CFG.r_nucleo + 2, y - 1, P.cobre, P.oro)

  -- ⚠ EL CASQUETE, POR LATITUD. Se recorre la esfera y el color depende de la
  -- ALTURA relativa, que es lo que hace que se lea como Poke Ball desde
  -- cualquier lado. Pintarlo por sectores daria una pelota de playa.
  local ri = ceil(r)
  for dx = -ri, ri do
    for dy = 0, ri do
      for dz = -ri, ri do
        local d = sqrt(dx*dx + dy*dy + dz*dz)
        if d <= r + 0.5 and d >= r - 1.5 then
          local t = dy / r                      -- 0 en el ecuador, 1 arriba
          local b
          if t < 0.10 then
            b = P.negro                          -- banda ecuatorial
          elseif t < 0.16 then
            b = P.blanco                         -- filete claro
          else
            b = P.rojo                           -- casquete superior
          end
          -- El boton: un circulo blanco al frente (sur), sobre la banda
          if dz > 0 and t < 0.13 and (dx*dx + (dy - r*0.05)^2) <= 30 then
            b = P.blanco
            if dx*dx + (dy - r*0.05)^2 <= 8 then b = P.pizarra end
            if dx*dx + (dy - r*0.05)^2 <= 2 then b = P.rana_perla end
          end
          poner(cx + dx, y + dy, cz + dz, b)
        end
      end
    end
  end

  -- Nervios geodesicos de cobre: 16 meridianos y 3 paralelos
  for i = 0, 15 do
    local a = i * (2 * pi / 16)
    for k = 0, ri do
      local rr = r * cos((k / ri) * (pi / 2))
      local yy = y + redondear(r * sin((k / ri) * (pi / 2)))
      local px, pz = polar(cx, cz, rr, a)
      poner(px, yy, pz, P.cobre)
    end
  end
  for _, t in ipairs({ 0.20, 0.45, 0.70 }) do
    local yy = y + redondear(r * t)
    local rr = sqrt(max(0, r * r - (r * t) ^ 2))
    circulo(cx, cz, rr, yy, P.cobre)
  end

  -- Base del domo: anillo de vidrio que deja ver el nucleo desde dentro
  circulo(cx, cz, r, y, P.oro)
  anillo(cx, cz, CFG.r_nucleo + 3, r - 1, y, P.vidrio_tintado)

  -- Remate: linterna y haz de luz
  cilindro(cx, cz, 4, y + r - 2, y + r + 4, P.vidrio_tintado)
  circulo(cx, cz, 5, y + r + 4, P.oro)
  cascara(cx, y + r + 5, cz, 5, P.cobre, 1, y + r + 5)
  for i = 1, 14 do poner(cx, y + r + 10 + i, cz, P.varilla) end
  poner(cx, y + r + 25, cz, P.farol_mar)
end


-- ###########################################################################
-- ##  11. LAS NUEVE SALAS                                                  ##
-- ###########################################################################
--
-- Cada sala mide 31 x 25 (o 25 x 31, segun el lado) y 22 de alto. Todas
-- comparten el mismo esqueleto y cambian de piel:
--
--   - recinto con el vano ya abierto por la galeria
--   - SEIS plataformas de entrenador, tres por lado, VACIAS
--   - UN estrado grande al fondo, tambien vacio
--   - decoracion tematica, sin una sola entidad
--
-- ⚠ EL VANO NO SE TOCA AQUI. Lo abre `construir_galeria`, y por eso todos
-- miden lo mismo y estan centrados. Si cada sala abriera el suyo, con nueve
-- orientaciones distintas, alguno saldria descentrado -- y eso es exactamente
-- como se llega a un edificio que "parece cerrado".

--- Datos utiles de una sala ya resueltos: rectangulo en mundo, eje de fondo y
--- punto central. `haciaX/haciaZ` apunta del vano hacia el fondo.
local function geom_sala(cx, cz, lado, fr)
  local r = rect_sala(lado, fr)
  local x1, z1, x2, z2 = cx + r[1], cz + r[2], cx + r[3], cz + r[4]
  local hx, hz = 0, 0
  if lado == "S" then hz = 1 elseif lado == "N" then hz = -1
  elseif lado == "E" then hx = 1 else hx = -1 end
  return {
    x1 = x1, z1 = z1, x2 = x2, z2 = z2,
    mx = redondear((x1 + x2) / 2), mz = redondear((z1 + z2) / 2),
    hx = hx, hz = hz,
    -- El fondo de la sala: el lado opuesto al vano
    fx = (hx ~= 0) and (hx > 0 and x2 - 4 or x1 + 4) or redondear((x1 + x2) / 2),
    fz = (hz ~= 0) and (hz > 0 and z2 - 4 or z1 + 4) or redondear((z1 + z2) / 2),
  }
end

--- Las seis posiciones de plataforma de una sala, tres por lado.
local function puestos_sala(g)
  local p = {}
  if g.hx ~= 0 then
    -- Sala orientada en X: los puestos van pegados a los muros norte y sur
    local a0 = (g.hx > 0) and g.x1 + 7 or g.x2 - 7
    for k = 0, 2 do
      local x = a0 + g.hx * k * 8
      p[#p + 1] = { x, g.z1 + 5 }
      p[#p + 1] = { x, g.z2 - 5 }
    end
  else
    local b0 = (g.hz > 0) and g.z1 + 7 or g.z2 - 7
    for k = 0, 2 do
      local z = b0 + g.hz * k * 8
      p[#p + 1] = { g.x1 + 5, z }
      p[#p + 1] = { g.x2 - 5, z }
    end
  end
  return p
end

--- Esqueleto comun. Devuelve la geometria para que el decorador la use.
local function sala_base(cx, cz, lado, fr, b_suelo, b_pared, b_techo, b_luz,
                        b_plat, b_aro)
  local y = CFG.y
  local h = CFG.alto - 4
  local g = geom_sala(cx, cz, lado, fr)

  recinto(g.x1, g.z1, g.x2, g.z2, y, h, b_suelo, b_pared, b_techo)

  -- Zocalo y cornisa interiores, que es lo que evita la sensacion de caja
  paredes(g.x1 + 1, y, g.z1 + 1, g.x2 - 1, y + 1, g.z2 - 1, P.pizarra)
  friso(g.x1 + 1, g.z1 + 1, g.x2 - 1, g.z2 - 1, y + h - 3, b_luz)

  -- Iluminacion de techo, en rejilla
  for x = g.x1 + 4, g.x2 - 4, 6 do
    for z = g.z1 + 4, g.z2 - 4, 6 do
      poner(x, y + h - 1, z, b_luz)
    end
  end

  -- Las seis plataformas, VACIAS
  for _, p in ipairs(puestos_sala(g)) do
    plataforma(p[1], y - 1, p[2], 2, b_plat, b_aro, b_luz)
  end

  -- El estrado del fondo, tambien VACIO
  estrado(g.fx, y - 1, g.fz, 5, 4, b_plat, b_aro, b_luz)

  return g, y, h
end


-- --- 1 · KANTO — laboratorio industrial, rojo y blanco --------------------
local function sala_kanto(cx, cz, lado, fr)
  local g, y, h = sala_base(cx, cz, lado, fr,
    P.blanco, P.blanco_terra, P.cuarzo_liso, P.farol_mar, P.esmeril, P.rojo)

  -- Bandas rojas industriales a media altura
  friso(g.x1 + 1, g.z1 + 1, g.x2 - 1, g.z2 - 1, y + 4, P.rojo)
  friso(g.x1 + 1, g.z1 + 1, g.x2 - 1, g.z2 - 1, y + 5, P.blanco)

  -- Conductos y maquinaria contra los muros
  for x = g.x1 + 3, g.x2 - 3, 5 do
    poner(x, y + h - 2, g.z1 + 1, eje(P.hierro, "x"))
    poner(x, y + h - 2, g.z2 - 1, eje(P.hierro, "x"))
  end
  for z = g.z1 + 3, g.z2 - 3, 4 do
    poner(g.x1 + 1, y + 2, z, P.hierro)
    poner(g.x2 - 1, y + 2, z, P.hierro)
    poner(g.x1 + 1, y + 3, z, P.lampara)
    poner(g.x2 - 1, y + 3, z, P.lampara)
  end

  -- ⚠ LOS OCHO PEDESTALES DE INSIGNIA, en dos hileras junto al estrado.
  -- Vacios: la insignia se coloca despues.
  for k = 0, 7 do
    local px = g.mx + (k % 4 - 2) * 3 + 1
    local pz = g.mz + (floor(k / 4) * 4 - 2)
    if g.hx ~= 0 then px, pz = pz, px end
    pedestal(px, y, pz, 2, P.esmeril, P.rana_ocre)
  end

  -- Mesa de trabajo del fondo
  for i = -4, 4 do
    poner(g.fx + i * ((g.hx ~= 0) and 0 or 1), y + 2, g.fz + i * ((g.hx ~= 0) and 1 or 0),
          losa(P.cuarzo_liso_losa, "top"))
  end
end


-- --- 2 · JOHTO — templo de madera oscura, oro y plata ---------------------
local function sala_johto(cx, cz, lado, fr)
  local g, y, h = sala_base(cx, cz, lado, fr,
    P.roble_osc, P.roble_osc, P.roble_osc, P.farol, P.pizarra_ladr, P.oro)

  -- Vigas cruzadas de madera
  for x = g.x1 + 2, g.x2 - 2, 4 do
    for z = g.z1 + 1, g.z2 - 1 do poner(x, y + h - 2, z, eje(P.roble_osc_tr, "z")) end
  end
  -- Zocalo dorado
  friso(g.x1 + 1, g.z1 + 1, g.x2 - 1, g.z2 - 1, y + 2, P.oro)

  -- Faroles colgados
  for x = g.x1 + 4, g.x2 - 4, 6 do
    for z = g.z1 + 4, g.z2 - 4, 6 do
      poner(x, y + h - 3, z, P.cadena)
      poner(x, y + h - 4, z, P.farol)
    end
  end

  -- Altares de piedra, uno por plataforma
  for _, p in ipairs(puestos_sala(g)) do
    for dx = -1, 1 do for dz = -1, 1 do
      poner(p[1] + dx, y + 2, p[2] + dz, losa(P.pizarra_losa, "top"))
    end end
    poner(p[1], y + 3, p[2], P.oro)
  end

  -- Campanas y braseros del fondo
  for _, s in ipairs({ -6, 6 }) do
    local bx = g.fx + ((g.hx ~= 0) and 0 or s)
    local bz = g.fz + ((g.hx ~= 0) and s or 0)
    for i = 0, 3 do poner(bx, y + i, bz, eje(P.roble_osc_tr, "y")) end
    poner(bx, y + 4, bz, P.oro)
    poner(bx, y + 5, bz, P.rana_ocre)
  end
end


-- --- 3 · HOENN — roca oceanica y raices de arbol gigante ------------------
local function sala_hoenn(cx, cz, lado, fr)
  local g, y, h = sala_base(cx, cz, lado, fr,
    P.prismarina_l, P.prismarina, P.prismarina_o, P.farol_mar, P.piedra, P.verde)

  -- Canal de agua perimetral.
  --
  -- ⚠ CON PASOS. La primera version lo hizo como un anillo cerrado, y eso es
  -- un FOSO: separaba la puerta del interior y dejaba la sala inalcanzable.
  -- Lo dijo `comprobar_recorrido()`. Ahora se interrumpe en los dos ejes, de
  -- modo que hay siempre cuatro vados de 5 de ancho.
  local function vado(a, b) return abs(a - b) <= 2 end
  for x = g.x1 + 2, g.x2 - 2 do
    if not vado(x, g.mx) then
      poner(x, y - 1, g.z1 + 2, P.agua); poner(x, y - 1, g.z2 - 2, P.agua)
    end
  end
  for z = g.z1 + 2, g.z2 - 2 do
    if not vado(z, g.mz) then
      poner(g.x1 + 2, y - 1, z, P.agua); poner(g.x2 - 2, y - 1, z, P.agua)
    end
  end
  -- Y una cruz de piedra que une la puerta con el fondo, por si acaso
  for x = g.x1 + 1, g.x2 - 1 do poner(x, y - 1, g.mz, P.prismarina_l) end
  for z = g.z1 + 1, g.z2 - 1 do poner(g.mx, y - 1, z, P.prismarina_l) end

  -- Raices que bajan del techo, con follaje
  for x = g.x1 + 5, g.x2 - 5, 7 do
    for z = g.z1 + 5, g.z2 - 5, 7 do
      for i = 0, 4 do poner(x, y + h - 2 - i, z, P.raiz) end
      for dx = -2, 2 do for dz = -2, 2 do
        if abs(dx) + abs(dz) <= 2 then poner(x + dx, y + h - 1, z + dz, P.hoja_jungla) end
      end end
      poner(x, y + h - 7, z, P.rana_verde)
    end
  end

  -- Tronos de piedra en cada plataforma
  for _, p in ipairs(puestos_sala(g)) do
    poner(p[1], y + 2, p[2], P.piedra)
    poner(p[1], y + 3, p[2], escalera(P.ladrillo_p_e, "north", "bottom"))
    poner(p[1] + 1, y + 2, p[2], losa(P.ladrillo_p_l, "top"))
    poner(p[1] - 1, y + 2, p[2], losa(P.ladrillo_p_l, "top"))
  end

  -- Muro de roca del fondo
  for i = 0, 4 do
    local fx = g.fx + g.hx * (3 + i)
    local fz = g.fz + g.hz * (3 + i)
    for w = -8, 8 do
      local x = fx + ((g.hx ~= 0) and 0 or w)
      local z = fz + ((g.hx ~= 0) and w or 0)
      for yy = y, y + 6 - i do poner(x, yy, z, P.ladrillo_mus) end
    end
  end
end


-- --- 4 · SINNOH — ruinas de cuarzo y purpur, el Monte Corona --------------
local function sala_sinnoh(cx, cz, lado, fr)
  local g, y, h = sala_base(cx, cz, lado, fr,
    P.piedrafin, P.cuarzo_ladr, P.purpur, P.rana_perla, P.purpur, P.amatista)

  -- Columnas rotas: ruinas
  local alturas = { 12, 7, 15, 9, 13, 6 }
  local i = 1
  for x = g.x1 + 4, g.x2 - 4, 6 do
    for z = g.z1 + 4, g.z2 - 4, 8 do
      local a = alturas[(i % #alturas) + 1]
      for k = 0, a do poner(x, y + k, z, eje(P.purpur_pilar, "y")) end
      poner(x, y + a + 1, z, losa(P.purpur_losa, "bottom"))
      i = i + 1
    end
  end

  -- El pico del Monte Corona al fondo, escalonado
  for k = 0, 7 do
    local rr = 8 - k
    for dx = -rr, rr do
      for dz = -rr, rr do
        if abs(dx) + abs(dz) <= rr then
          poner(g.fx + dx, y + k, g.fz + dz, (k % 2 == 0) and P.cuarzo_ladr or P.calcita)
        end
      end
    end
  end
  poner(g.fx, y + 8, g.fz, P.amatista)
  poner(g.fx, y + 9, g.fz, P.rana_perla)

  -- Cristales de amatista en las paredes
  for x = g.x1 + 2, g.x2 - 2, 3 do
    poner(x, y + 6, g.z1 + 1, P.amatista)
    poner(x, y + 7, g.z2 - 1, P.amatista)
  end
end


-- --- 5 · TESELIA (UNOVA) — ciudad moderna, hierro y luz -------------------
local function sala_unova(cx, cz, lado, fr)
  local g, y, h = sala_base(cx, cz, lado, fr,
    P.pizarra_tejas, P.gris, P.pizarra, P.lampara, P.hierro, P.azul_claro)

  -- Rascacielos de fondo: siluetas de bloques con ventanas
  for k = 0, 5 do
    local a = 6 + (k % 3) * 5
    local px = g.fx + ((g.hx ~= 0) and 0 or (k - 3) * 4)
    local pz = g.fz + ((g.hx ~= 0) and (k - 3) * 4 or 0)
    for yy = y, y + a do
      for dx = -1, 1 do for dz = -1, 1 do
        poner(px + dx, yy, pz + dz, P.gris_claro)
      end end
      if yy % 2 == 0 then
        poner(px + 1, yy, pz, P.vidrio_cian)
        poner(px - 1, yy, pz, P.vidrio_cian)
        poner(px, yy, pz + 1, P.vidrio_cian)
        poner(px, yy, pz - 1, P.vidrio_cian)
      end
    end
    poner(px, y + a + 1, pz, P.varilla)
    poner(px, y + a + 2, pz, P.farol_mar)
  end

  -- Vias de luz en el suelo, como una calle
  for x = g.x1 + 3, g.x2 - 3 do
    poner(x, y - 1, g.mz, P.pizarra)
    if x % 3 == 0 then poner(x, y - 1, g.mz, P.amarillo) end
  end

  -- Barandillas metalicas
  for x = g.x1 + 3, g.x2 - 3, 2 do
    poner(x, y, g.z1 + 3, P.rejas)
    poner(x, y, g.z2 - 3, P.rejas)
  end
end


-- --- 6 · KALOS — palacio de cuarzo y oro, vitrinas de museo ---------------
local function sala_kalos(cx, cz, lado, fr)
  local g, y, h = sala_base(cx, cz, lado, fr,
    P.cuarzo_liso, P.cuarzo, P.cuarzo_cincel, P.rana_perla, P.cuarzo_liso, P.oro)

  -- Suelo de damero noble
  for x = g.x1 + 2, g.x2 - 2 do
    for z = g.z1 + 2, g.z2 - 2 do
      poner(x, y - 1, z, damero(x, z, 2) and P.cuarzo_liso or P.calcita)
    end
  end

  -- Molduras doradas
  friso(g.x1 + 1, g.z1 + 1, g.x2 - 1, g.z2 - 1, y + 3, P.oro)
  friso(g.x1 + 1, g.z1 + 1, g.x2 - 1, g.z2 - 1, y + h - 5, P.oro)

  -- Vitrinas de museo, VACIAS: marco de oro y cristal, nada dentro
  for _, p in ipairs(puestos_sala(g)) do
    for dx = -2, 2 do for dz = -2, 2 do
      if abs(dx) == 2 or abs(dz) == 2 then
        for yy = y + 2, y + 5 do
          poner(p[1] + dx, yy, p[2] + dz, (abs(dx) == 2 and abs(dz) == 2)
                and P.oro or P.vidrio)
        end
      end
    end end
    for dx = -2, 2 do for dz = -2, 2 do
      poner(p[1] + dx, y + 6, p[2] + dz, P.oro)
    end end
    poner(p[1], y + 7, p[2], P.rana_perla)
  end

  -- Arañas colgadas
  for x = g.x1 + 6, g.x2 - 6, 8 do
    for z = g.z1 + 6, g.z2 - 6, 8 do
      poner(x, y + h - 2, z, P.cadena)
      for dx = -1, 1 do for dz = -1, 1 do
        if abs(dx) + abs(dz) == 1 then poner(x + dx, y + h - 3, z + dz, P.oro) end
      end end
      poner(x, y + h - 3, z, P.rana_perla)
    end
  end
end


-- --- 7 · ALOLA — plataformas de madera sobre estanques --------------------
local function sala_alola(cx, cz, lado, fr)
  local g, y, h = sala_base(cx, cz, lado, fr,
    P.tabla_jungla, P.tabla_jungla, P.hoja_jungla, P.shroomlight,
    P.tabla_jungla, P.lima)

  -- El suelo se rebaja a agua y las plataformas quedan flotando
  for x = g.x1 + 2, g.x2 - 2 do
    for z = g.z1 + 2, g.z2 - 2 do
      poner(x, y - 1, z, P.prismarina_o)
      poner(x, y - 1, z, P.agua)
    end
  end
  -- Pasarelas de madera que unen todo (si no, no se puede andar)
  for x = g.x1 + 2, g.x2 - 2 do poner(x, y - 1, g.mz, P.tabla_jungla) end
  for z = g.z1 + 2, g.z2 - 2 do poner(g.mx, y - 1, z, P.tabla_jungla) end
  for _, p in ipairs(puestos_sala(g)) do
    linea(p[1], p[2], g.mx, g.mz, y - 1, P.tabla_jungla)
  end
  linea(g.fx, g.fz, g.mx, g.mz, y - 1, P.tabla_jungla)

  -- Vegetacion y troncos
  for x = g.x1 + 4, g.x2 - 4, 7 do
    for z = g.z1 + 4, g.z2 - 4, 7 do
      if dist2(x - g.mx, z - g.mz) > 5 then
        for i = 0, 5 do poner(x, y + i, z, eje(P.tronco_jungla, "y")) end
        for dx = -3, 3 do for dz = -3, 3 do
          if dx*dx + dz*dz <= 10 then poner(x + dx, y + 6, z + dz, P.hoja_jungla) end
        end end
        poner(x, y + 7, z, P.shroomlight)
      end
    end
  end

  -- Cascada al fondo
  for w = -5, 5 do
    local x = g.fx + ((g.hx ~= 0) and 0 or w)
    local z = g.fz + ((g.hx ~= 0) and w or 0)
    for yy = y, y + h - 4 do poner(x + g.hx * 4, yy, z + g.hz * 4, P.prismarina) end
  end
end


-- --- 8 · GALAR — estadio a escala con gradas ------------------------------
local function sala_galar(cx, cz, lado, fr)
  local g, y, h = sala_base(cx, cz, lado, fr,
    P.gris_claro, P.blanco_terra, P.cuarzo_liso, P.farol_mar, P.morado, P.magenta)

  -- Cesped del campo
  for x = g.x1 + 6, g.x2 - 6 do
    for z = g.z1 + 6, g.z2 - 6 do
      poner(x, y - 1, z, damero(x, z, 3) and P.verde or P.lima)
    end
  end
  -- Circulo central
  circulo(g.mx, g.mz, 5, y - 1, P.blanco)
  circulo(g.mx, g.mz, 6, y - 1, P.blanco)

  -- ⚠ GRADAS COMO ESCALERA CONTINUA, NO COMO ANILLOS SUELTOS.
  --
  -- La primera version puso tres anillos a tres alturas distintas y con huecos
  -- entre ellos. El resultado: a dos bloques del muro habia una fila de
  -- bloques a la altura de la CABEZA con el suelo libre debajo, o sea un muro
  -- que no se puede cruzar ni por arriba ni por abajo. La sala entera quedaba
  -- inalcanzable.
  --
  -- Lo dijo `comprobar_recorrido()`. Sobre el plano las gradas "subian hacia
  -- los muros" y parecia bien; lo que no se comprueba mirando es si un
  -- jugador CABE por el camino.
  --
  -- Ahora cada anda se rellena DESDE EL SUELO hasta su altura, asi que las
  -- tres forman una escalera maciza y continua por la que se sube y se baja.
  --
  --     d=3 (mas al centro)  superficie y      -> se pisa en y
  --     d=2                  superficie y+1
  --     d=1 (pegada al muro) superficie y+2
  for d = 1, 3 do
    local off = d + 1
    local alto_grada = 3 - d          -- d=1 -> 2 ;  d=2 -> 1 ;  d=3 -> 0
    for k = 0, alto_grada - 1 do
      for x = g.x1 + off, g.x2 - off do
        poner(x, y + k, g.z1 + off, P.cuarzo_liso)
        poner(x, y + k, g.z2 - off, P.cuarzo_liso)
      end
      for z = g.z1 + off, g.z2 - off do
        poner(g.x1 + off, y + k, z, P.cuarzo_liso)
        poner(g.x2 - off, y + k, z, P.cuarzo_liso)
      end
    end
    -- El peldano visto, en escalera, mirando al campo
    if alto_grada > 0 then
      local yy = y + alto_grada - 1
      for x = g.x1 + off, g.x2 - off do
        poner(x, yy, g.z1 + off, escalera(P.cuarzo_liso_esc, "south", "top"))
        poner(x, yy, g.z2 - off, escalera(P.cuarzo_liso_esc, "north", "top"))
      end
      for z = g.z1 + off, g.z2 - off do
        poner(g.x1 + off, yy, z, escalera(P.cuarzo_liso_esc, "east", "top"))
        poner(g.x2 - off, yy, z, escalera(P.cuarzo_liso_esc, "west", "top"))
      end
    end
  end
  -- ⚠ Y CUATRO PASILLOS que cortan las gradas en los ejes, para bajar al campo
  -- sin tener que trepar. Un estadio sin vomitorios no se puede recorrer.
  for d = 1, 3 do
    local off = d + 1
    for w = -2, 2 do
      caja(g.mx + w, y, g.z1 + off, g.mx + w, y + 3, g.z1 + off, P.aire)
      caja(g.mx + w, y, g.z2 - off, g.mx + w, y + 3, g.z2 - off, P.aire)
      caja(g.x1 + off, y, g.mz + w, g.x1 + off, y + 3, g.mz + w, P.aire)
      caja(g.x2 - off, y, g.mz + w, g.x2 - off, y + 3, g.mz + w, P.aire)
    end
  end
  -- Asientos de color en las gradas
  for x = g.x1 + 4, g.x2 - 4, 2 do
    poner(x, y + 3, g.z1 + 2, P.morado)
    poner(x, y + 3, g.z2 - 2, P.magenta)
  end

  -- Focos del estadio en las cuatro esquinas
  for _, c in ipairs({ {g.x1+3,g.z1+3}, {g.x1+3,g.z2-3}, {g.x2-3,g.z1+3}, {g.x2-3,g.z2-3} }) do
    for i = 0, h - 6 do poner(c[1], y + i, c[2], eje(P.hierro, "y")) end
    for dx = -1, 1 do for dz = -1, 1 do
      poner(c[1] + dx, y + h - 5, c[2] + dz, P.farol_mar)
    end end
  end

  -- Podio de campeon al fondo, de tres alturas
  for k = 0, 2 do
    local rr = 4 - k
    for dx = -rr, rr do for dz = -rr, rr do
      poner(g.fx + dx, y + k, g.fz + dz, (k == 2) and P.oro or P.cuarzo_liso)
    end end
  end
  caja(g.fx - 2, y + 3, g.fz - 2, g.fx + 2, y + 6, g.fz + 2, P.aire)
end


-- --- 9 · PALDEA — academia de cristal y terracota -------------------------
local function sala_paldea(cx, cz, lado, fr)
  local g, y, h = sala_base(cx, cz, lado, fr,
    P.terracota, P.marron_terra, P.cuarzo_liso, P.rana_ocre, P.arenisca, P.naranja)

  -- Ventanales altos de cristal en los muros laterales
  for x = g.x1 + 4, g.x2 - 4, 5 do
    ventanal(x, y + 4, g.z1 + 1, 1, 0, 1, 5, P.marron_terra, P.vidrio)
    ventanal(x, y + 4, g.z2 - 1, 1, 0, 1, 5, P.marron_terra, P.vidrio)
  end

  -- Pupitres: dos hileras enfrentadas al fondo
  for k = 0, 3 do
    for _, s in ipairs({ -1, 1 }) do
      local px = g.mx + ((g.hx ~= 0) and (k * 4 - 6) or s * 5)
      local pz = g.mz + ((g.hx ~= 0) and s * 5 or (k * 4 - 6))
      for dx = -1, 1 do
        local x = px + ((g.hx ~= 0) and 0 or dx)
        local z = pz + ((g.hx ~= 0) and dx or 0)
        poner(x, y, z, P.arenisca)
        poner(x, y + 1, z, losa(P.andesita_losa, "top"))
      end
      -- banqueta
      poner(px - g.hx * 2, y, pz - g.hz * 2, losa(P.cuarzo_losa, "bottom"))
    end
  end

  -- Catedra del profesor sobre el estrado
  for dx = -3, 3 do
    local x = g.fx + ((g.hx ~= 0) and 0 or dx)
    local z = g.fz + ((g.hx ~= 0) and dx or 0)
    poner(x, y + 2, z, P.arenisca)
    poner(x, y + 3, z, losa(P.arenisca_e and P.andesita_losa or P.cuarzo_losa, "top"))
  end
  -- Pizarra detras
  for w = -5, 5 do
    for yy = y + 3, y + 7 do
      local x = g.fx + g.hx * 4 + ((g.hx ~= 0) and 0 or w)
      local z = g.fz + g.hz * 4 + ((g.hx ~= 0) and w or 0)
      poner(x, yy, z, P.negro)
    end
  end

  -- Estanterias contra los muros
  for x = g.x1 + 2, g.x2 - 2, 3 do
    poner(x, y, g.z1 + 1, P.estanteria)
    poner(x, y + 1, g.z1 + 1, P.estanteria)
    poner(x, y, g.z2 - 1, P.estanteria)
    poner(x, y + 1, g.z2 - 1, P.estanteria)
  end
end


--- Despachador. El orden de esta tabla coincide con el de SALAS.
local CONSTRUCTORES = {
  sala_kanto, sala_johto, sala_hoenn, sala_sinnoh, sala_unova,
  sala_kalos, sala_alola, sala_galar, sala_paldea,
}

local function construir_salas(cx, cz, decir)
  for _, s in ipairs(SALAS) do
    local n, nombre, lado, fr = s[1], s[2], s[3], s[4]
    if sala_pedida(n) then
      decir("  sala " .. n .. "  " .. nombre)
      CONSTRUCTORES[n](cx, cz, lado, fr)
    end
  end
end


-- ###########################################################################
-- ##  12. PLATAFORMAS AEREAS Y PUENTES                                     ##
-- ###########################################################################
--
-- Cuatro discos suspendidos sobre las esquinas del palacio, unidos al domo por
-- puentes. Salen de las referencias, donde son lo que le da al edificio su
-- perfil de "complejo" y no de bloque unico.
--
-- ⚠ SE LLEGA A ELLAS. Cada una conecta con la azotea de su torre, que a su vez
-- tiene la escalera de caracol desde el suelo. No son adorno inalcanzable --
-- ese fue justo el error de la version anterior.

local function construir_aereas(cx, cz)
  local y = CFG.y + CFG.aerea_y
  local r = CFG.aerea_r

  for _, e in ipairs({ {1,1}, {1,-1}, {-1,1}, {-1,-1} }) do
    local px = cx + e[1] * (MU - 8)
    local pz = cz + e[2] * (MU - 8)

    -- Disco
    disco(px, pz, r, y - 1, P.cuarzo_liso)
    circulo(px, pz, r, y - 1, P.cobre)
    anillo(px, pz, r - 4, r - 1, y - 1, P.calcita)
    disco(px, pz, 4, y - 1, P.rana_perla)

    -- Canto escalonado: se ve desde abajo, que es desde donde se mira
    for i = 1, 4 do
      anillo(px, pz, r - 2 - i, r - i, y - 1 - i, P.pizarra_ladr)
    end

    -- Antepecho
    for i = 0, 23 do
      local a = i * (2 * pi / 24)
      local bx, bz = polar(px, pz, r - 1, a)
      poner(bx, y, bz, P.rejas)
      poner(bx, y + 1, bz, losa(P.cobre_losa, "bottom"))
    end

    -- Cuatro mastiles de luz
    for i = 0, 3 do
      local a = i * (pi / 2) + pi / 4
      local mx, mz = polar(px, pz, r - 4, a)
      for k = 0, 6 do poner(mx, y + k, mz, P.varilla) end
      poner(mx, y + 7, mz, P.farol_mar)
    end

    -- Torre de entrenamiento en el centro del disco
    paredes(px - 4, y, pz - 4, px + 4, y + 12, pz + 4, P.cuarzo_liso)
    vaciar(px - 3, y, pz - 3, px + 3, y + 12, pz + 3)
    rect(px - 4, pz - 4, px + 4, pz + 4, y + 13, P.cobre)
    arco_paso(px, y, pz - 4, 1, 0, 2, 4, 1, P.cobre, P.rana_verde)
    for k = 2, 10, 4 do
      for dx = -3, 3 do
        poner(px + dx, y + k, pz - 4, P.vidrio_cian)
        poner(px + dx, y + k, pz + 4, P.vidrio_cian)
      end
    end
    -- Plataforma de entrenador dentro de la torreta, VACIA
    plataforma(px, y - 1, pz, 2, P.cuarzo_liso, P.cobre, P.rana_perla)

    -- ⚠ PUENTE al domo: sin esto el disco es inalcanzable
    local dx = cx + e[1] * (CFG.domo_r - 2)
    local dz = cz + e[2] * (CFG.domo_r - 2)
    local dy = CFG.y + CFG.domo_y + 4
    local pasos = redondear(dist2(dx - px, dz - pz))
    for k = 0, pasos do
      local t = k / pasos
      local bx = redondear(lerp(px, dx, t))
      local bz = redondear(lerp(pz, dz, t))
      local by = redondear(lerp(y - 1, dy, t))
      for w = -2, 2 do
        local ox = (abs(dx - px) > abs(dz - pz)) and 0 or w
        local oz = (abs(dx - px) > abs(dz - pz)) and w or 0
        poner(bx + ox, by, bz + oz, P.cuarzo_liso)
      end
      -- barandillas
      local ox = (abs(dx - px) > abs(dz - pz)) and 0 or 3
      local oz = (abs(dx - px) > abs(dz - pz)) and 3 or 0
      poner(bx + ox, by, bz + oz, P.rejas)
      poner(bx - ox, by, bz - oz, P.rejas)
      if k % 6 == 0 then
        poner(bx + ox, by + 1, bz + oz, P.farol_mar)
        poner(bx - ox, by + 1, bz - oz, P.farol_mar)
      end
    end

    -- ⚠ Y ESCALERA desde la azotea de la torre de esa esquina hasta el disco
    local tx = cx + e[1] * (MU - 8)
    local tz = cz + e[2] * (MU - 8)
    local y_torre = CFG.y + CFG.alto + 22 + 3
    for k = 0, (y - 1) - y_torre do
      local a = (k / 10) * (pi / 2)
      local sx, sz = polar(tx, tz, 6, a)
      poner(sx, y_torre + k, sz, P.cuarzo_liso_esc)
      poner(sx, y_torre + k - 1, sz, P.cuarzo_liso)
    end
  end
end


-- ###########################################################################
-- ##  12-bis. ROTULOS — fuente de bloques 5x7                              ##
-- ###########################################################################
--
-- ⚠ NO SE PUEDEN USAR CARTELES. El Script Brush solo coloca BLOQUES: no
-- invoca entidades ni escribe NBT, asi que un `sign` con texto es imposible.
-- Las letras se dibujan con bloques, que ademas es lo unico legible a esta
-- escala -- y es lo que hacen las referencias con su neon.

local FUENTE = {
  A={" ### ","#   #","#   #","#####","#   #","#   #","#   #"},
  B={"#### ","#   #","#   #","#### ","#   #","#   #","#### "},
  C={" ### ","#   #","#    ","#    ","#    ","#   #"," ### "},
  D={"#### ","#   #","#   #","#   #","#   #","#   #","#### "},
  E={"#####","#    ","#    ","#### ","#    ","#    ","#####"},
  F={"#####","#    ","#    ","#### ","#    ","#    ","#    "},
  G={" ### ","#   #","#    ","#  ##","#   #","#   #"," ### "},
  H={"#   #","#   #","#   #","#####","#   #","#   #","#   #"},
  I={" ### ","  #  ","  #  ","  #  ","  #  ","  #  "," ### "},
  J={"  ###","   # ","   # ","   # ","   # ","#  # "," ##  "},
  K={"#   #","#  # ","# #  ","##   ","# #  ","#  # ","#   #"},
  L={"#    ","#    ","#    ","#    ","#    ","#    ","#####"},
  M={"#   #","## ##","# # #","#   #","#   #","#   #","#   #"},
  N={"#   #","##  #","# # #","#  ##","#   #","#   #","#   #"},
  O={" ### ","#   #","#   #","#   #","#   #","#   #"," ### "},
  P={"#### ","#   #","#   #","#### ","#    ","#    ","#    "},
  Q={" ### ","#   #","#   #","#   #","# # #","#  # "," ## #"},
  R={"#### ","#   #","#   #","#### ","# #  ","#  # ","#   #"},
  S={" ####","#    ","#    "," ### ","    #","    #","#### "},
  T={"#####","  #  ","  #  ","  #  ","  #  ","  #  ","  #  "},
  U={"#   #","#   #","#   #","#   #","#   #","#   #"," ### "},
  V={"#   #","#   #","#   #","#   #","#   #"," # # ","  #  "},
  W={"#   #","#   #","#   #","#   #","# # #","## ##","#   #"},
  X={"#   #","#   #"," # # ","  #  "," # # ","#   #","#   #"},
  Y={"#   #","#   #"," # # ","  #  ","  #  ","  #  ","  #  "},
  Z={"#####","    #","   # ","  #  "," #   ","#    ","#####"},
  ["0"]={" ### ","#   #","#  ##","# # #","##  #","#   #"," ### "},
  ["1"]={"  #  "," ##  ","  #  ","  #  ","  #  ","  #  "," ### "},
  ["2"]={" ### ","#   #","    #","   # ","  #  "," #   ","#####"},
  ["3"]={"#####","   # ","  #  ","   # ","    #","#   #"," ### "},
  ["4"]={"   # ","  ## "," # # ","#  # ","#####","   # ","   # "},
  ["5"]={"#####","#    ","#### ","    #","    #","#   #"," ### "},
  ["6"]={"  ## "," #   ","#    ","#### ","#   #","#   #"," ### "},
  ["7"]={"#####","    #","   # ","  #  "," #   "," #   "," #   "},
  ["8"]={" ### ","#   #","#   #"," ### ","#   #","#   #"," ### "},
  ["9"]={" ### ","#   #","#   #"," ####","    #","   # "," ##  "},
  [" "]={"     ","     ","     ","     ","     ","     ","     "},
  ["-"]={"     ","     ","     ","#####","     ","     ","     "},
}

local function ancho_rotulo(t, sep) return #t * (5 + (sep or 1)) - (sep or 1) end

--- Texto en un plano VERTICAL.
local function rotulo(x, y, z, dirx, dirz, texto, b, sep)
  sep = sep or 1
  local c = 0
  for i = 1, #texto do
    local g = FUENTE[string.upper(string.sub(texto, i, i))]
    if g then
      for fila = 1, 7 do
        for col = 1, 5 do
          if string.sub(g[fila], col, col) == "#" then
            poner(x + dirx * (c + col - 1), y + (7 - fila), z + dirz * (c + col - 1), b)
          end
        end
      end
    end
    c = c + 5 + sep
  end
end

local function rotulo_centrado(cx, y, cz, dirx, dirz, texto, b, sep)
  local o = floor(ancho_rotulo(texto, sep) / 2)
  rotulo(cx - dirx * o, y, cz - dirz * o, dirx, dirz, texto, b, sep)
end

--- Texto TUMBADO en el suelo, como el de la plaza en las referencias.
local function rotulo_suelo(cx, y, cz, texto, b, sep)
  sep = sep or 1
  local o = floor(ancho_rotulo(texto, sep) / 2)
  local c = 0
  for i = 1, #texto do
    local g = FUENTE[string.upper(string.sub(texto, i, i))]
    if g then
      for fila = 1, 7 do
        for col = 1, 5 do
          if string.sub(g[fila], col, col) == "#" then
            poner(cx - o + c + col - 1, y, cz + fila - 4, b)
          end
        end
      end
    end
    c = c + 5 + sep
  end
end

local function construir_rotulos(cx, cz)
  local y, h = CFG.y, CFG.alto

  -- Fachada: el nombre sobre el gran arco
  rotulo_centrado(cx, y + h + 1, cz + MU + 7, 1, 0, CFG.rotulo_fachada,
                  P.rana_verde, 1)

  -- Plaza: el rotulo tumbado en el pavimento, delante de la escalinata
  rotulo_suelo(cx, y - 1, cz + MU + 26, CFG.rotulo_plaza, P.lima, 1)
  rotulo_suelo(cx, y - 1, cz + MU + 36, "POKEMON", P.lima, 1)

  -- El numero y el nombre de cada sala, sobre su arco, DENTRO de la galeria
  for _, s in ipairs(SALAS) do
    local n, nombre, lado, fr = s[1], s[2], s[3], s[4]
    if sala_pedida(n) then
      local bx, bz, dx, dz = boca_sala(lado, fr)
      -- Un poco por dentro de la galeria, para que se lea al pasar
      local ix = cx + bx - (dz ~= 0 and (bx > 0 and 1 or -1) or 0)
      local iz = cz + bz - (dx ~= 0 and (bz > 0 and 1 or -1) or 0)
      rotulo_centrado(ix, y + 12, iz, dx, dz, tostring(n), P.rana_verde, 1)
      rotulo_centrado(ix, y + 4, iz, dx, dz, nombre, P.farol_mar, 1)
    end
  end
end


-- ###########################################################################
-- ##  13. COMPROBACIONES                                                   ##
-- ###########################################################################

local function decir(m) pcall(function() print(m) end) end

local function comprobar_paleta()
  if #FALLOS == 0 then return true end
  decir("=====================================================")
  decir(" PALETA INCOMPLETA: " .. #FALLOS .. " ids no existen.")
  for i = 1, min(10, #FALLOS) do decir("   " .. FALLOS[i]) end
  decir(" Se construye igual, pero esos bloques saldran como")
  decir(" HUECOS. Revisa los nombres contra Minecraft 1.21.1.")
  decir("=====================================================")
  return false
end

local function comprobar_geometria()
  local ok = true
  if CFG.gal_ext <= CFG.r_nucleo + 8 then
    decir("GEOMETRIA: la galeria es demasiado estrecha para el nucleo.")
    ok = false
  end
  if MU <= S2 then
    decir("GEOMETRIA: las salas se salen del muro exterior.")
    ok = false
  end
  if CFG.domo_r < CFG.r_nucleo + 4 then
    decir("GEOMETRIA: el domo no cubre el nucleo.")
    ok = false
  end
  return ok
end


--- ⚠⚠ LA COMPROBACION QUE FALTABA: ¿SE PUEDE LLEGAR ANDANDO?
---
--- El palacio anterior se construyo entero y estaba SELLADO. Nada lo aviso
--- porque nada lo comprobaba: se puede verificar que la geometria cuadra, que
--- los ids existen y que no hay coordenadas raras, y aun asi entregar un
--- edificio en el que no se entra.
---
--- Esto lo comprueba de la unica forma que vale: RECORRIENDOLO. Una inundacion
--- desde el umbral de la puerta principal, avanzando solo por donde cabe un
--- jugador --suelo solido debajo, dos bloques de aire encima-- y admitiendo
--- desniveles de un bloque. Al final se mira si el centro de cada sala quedo
--- dentro de la zona alcanzada.
---
--- Si alguna sala sale como INALCANZABLE, el edificio esta mal y hay que
--- arreglarlo antes de darlo por bueno. Es la diferencia entre "creo que se
--- puede pasar" y "se ha pasado".
local function comprobar_recorrido(cx, cz)
  local y = CFG.y

  -- ¿Cabe un jugador de pie aqui?
  local function pisable(x, yy, z)
    local ok1, suelo = pcall(getBlock, x, yy - 1, z)
    local ok2, pies  = pcall(getBlock, x, yy, z)
    local ok3, cabez = pcall(getBlock, x, yy + 1, z)
    if not (ok1 and ok2 and ok3) then return false end
    local s1, s2, s3 = false, true, true
    pcall(function() s1 = isSolid(suelo) end)
    pcall(function() s2 = isSolid(pies) end)
    pcall(function() s3 = isSolid(cabez) end)
    return s1 and (not s2) and (not s3)
  end

  local visto = {}
  local function clave(x, yy, z) return x .. "," .. yy .. "," .. z end

  -- Se arranca en el umbral de la puerta principal
  local px, py, pz = cx, y, cz + MU + 6
  if not pisable(px, py, pz) then
    -- Un poco mas afuera, por si el umbral cae en el escalon
    pz = cz + MU + 10
  end

  local cola = { { px, py, pz } }
  visto[clave(px, py, pz)] = true
  local n = 0
  local TOPE = 220000        -- suficiente para el palacio; evita colgarse

  local DIRS = { {1,0}, {-1,0}, {0,1}, {0,-1} }
  while #cola > 0 and n < TOPE do
    local c = table.remove(cola)
    n = n + 1
    for _, d in ipairs(DIRS) do
      for dy = -1, 1 do                 -- escalones de un bloque
        local nx, ny, nz = c[1] + d[1], c[2] + dy, c[3] + d[2]
        local k = clave(nx, ny, nz)
        if not visto[k] then
          -- Acotado a la caja del palacio y su plaza, para no salir al mundo
          if abs(nx - cx) <= MU + 30 and nz - cz <= MU + 80 and cz - nz <= MU + 30
             and ny >= y - 8 and ny <= y + 6 then
            if pisable(nx, ny, nz) then
              visto[k] = true
              cola[#cola + 1] = { nx, ny, nz }
            end
          end
        end
      end
    end
  end

  -- ¿Se llego al centro de cada sala?
  local function alcanzado(x, z)
    for dy = -2, 4 do
      if visto[clave(x, y + dy, z)] then return true end
    end
    return false
  end

  local malas = {}
  for _, s in ipairs(SALAS) do
    local nn, nombre, lado, fr = s[1], s[2], s[3], s[4]
    if sala_pedida(nn) then
      local g = geom_sala(cx, cz, lado, fr)
      if not alcanzado(g.mx, g.mz) then
        malas[#malas + 1] = nn .. " " .. nombre
      end
    end
  end
  -- Y el nucleo
  local nucleo_ok = alcanzado(cx + CFG.r_nucleo - 6, cz)

  decir("-----------------------------------------------------")
  decir("  RECORRIDO (inundacion desde la puerta)")
  decir("    posiciones alcanzadas: " .. n)
  if #malas == 0 then
    decir("    las 9 salas son ALCANZABLES andando  ✔")
  else
    decir("    ⚠ SALAS INALCANZABLES: " .. table.concat(malas, ", "))
  end
  decir("    nucleo: " .. (nucleo_ok and "alcanzable ✔" or "⚠ INALCANZABLE"))
  if n >= TOPE then
    decir("    (se alcanzo el tope de busqueda; el dato es parcial)")
  end
  return #malas == 0 and nucleo_ok
end


-- ###########################################################################
-- ##  14. MAIN                                                             ##
-- ###########################################################################

local function main()
  local cx, cz
  if CFG.usar_punto_del_raton then cx, cz = x, z else cx, cz = CFG.cx, CFG.cz end

  decir("=====================================================")
  decir("  PALACIO MULTIGENERACIONAL POKEMON")
  decir("  centro " .. tostring(cx) .. ", " .. tostring(cz) ..
        "   suelo y=" .. CFG.y)
  decir("=====================================================")

  comprobar_paleta()
  if not comprobar_geometria() then
    decir("ABORTADO: geometria inconsistente. No se ha tocado nada.")
    return
  end

  if CFG.limpiar_antes then
    decir("limpiando...")
    caja(cx - MU - 40, CFG.y - 12, cz - MU - 40,
         cx + MU + 40, CFG.y + CFG.aerea_y + 40, cz + MU + 90, P.aire)
  end

  -- ⚠ EL ORDEN IMPORTA. Los muros primero y los vanos DESPUES: un arco se abre
  -- perforando, asi que si el muro no esta puesto todavia no perfora nada, y
  -- el resultado es un edificio que parece cerrado. Fue exactamente lo que
  -- paso la vez anterior.
  if CFG.hacer.plaza     then decir("plaza multinivel...");   construir_plaza(cx, cz) end
  if CFG.hacer.muros     then decir("muros y envolvente..."); construir_muros(cx, cz) end
  if CFG.hacer.salas     then decir("las nueve salas...");    construir_salas(cx, cz, decir) end
  if CFG.hacer.galeria   then decir("galeria y vanos...");    construir_galeria(cx, cz) end
  if CFG.hacer.nucleo    then decir("nucleo Mon Core...");    construir_nucleo(cx, cz) end
  if CFG.hacer.domo      then decir("domo geodesico...");     construir_domo(cx, cz) end
  if CFG.hacer.vestibulo then decir("vestibulo...");          construir_vestibulo(cx, cz) end
  if CFG.hacer.torres    then decir("torres de esquina...");  construir_torres(cx, cz) end
  if CFG.hacer.fachada   then decir("fachada y portico...");  construir_fachada(cx, cz) end
  if CFG.hacer.aereas    then decir("plataformas aereas..."); construir_aereas(cx, cz) end
  if CFG.hacer.rotulos   then decir("rotulos...");            construir_rotulos(cx, cz) end

  -- Informe
  local n_salas = 0
  for _, s in ipairs(SALAS) do if sala_pedida(s[1]) then n_salas = n_salas + 1 end end
  local plataformas = n_salas * 7 + 4      -- 6 + estrado por sala, + las aereas

  decir("-----------------------------------------------------")
  decir("  bloques colocados : " .. tostring(PUESTOS))
  decir("  salas construidas : " .. n_salas)
  decir("  plataformas VACIAS: " .. plataformas)
  decir("  (mas 8 pedestales de insignia en Kanto)")
  decir("")
  decir("  NI UNA ENTIDAD colocada: es estructura pura.")

  -- Y la comprobacion que de verdad importa
  if CFG.hacer.galeria and CFG.hacer.salas then
    comprobar_recorrido(cx, cz)
  end
  decir("=====================================================")
end

main()

return getBlock(x, y, z)
