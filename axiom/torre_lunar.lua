$once$
--[[============================================================================

   T O R R E   D E   B A T A L L A   L U N A R
   PokeReport: Luna Eternal  ·  Lua Script Brush de Axiom

   Una Poke Ball gigante partida por su cinturon, alzada sobre una plaza
   escalonada, con seis arenas de PvP dentro, alas a los lados y una corona
   lunar arriba. Se sube peleando: cada planta es un combate y un rango.

--------------------------------------------------------------------------------
   COMO SE USA
--------------------------------------------------------------------------------

   1. Axiom -> Herramientas -> Lua Script Brush
   2. Pega este fichero ENTERO en el cuadro de codigo
   3. Los controles aparecen solos en "Opciones de Herramienta": ahi se
      ajusta todo sin tocar una linea
   4. Clic derecho en el suelo, en el CENTRO de donde quieras la torre

   La primera linea, `$once$`, es lo que hace que no sea un pincel: el script
   corre UNA vez en el punto que senalas. Sin ella, construiria una torre por
   cada bloque que tocara la brocha.

--------------------------------------------------------------------------------
   POR QUE LOS MATERIALES SON SELECTORES Y NO NOMBRES ESCRITOS
--------------------------------------------------------------------------------

   La documentacion de Axiom solo describe `blocks.stone` para bloques de
   Minecraft; NO documenta como pedir uno de otro mod. Escribir
   `blocks["lunaneon:neon_cian"]` seria apostar.

   `$blockState(Titulo, por_defecto)$` da un SELECTOR en el panel, y el
   constructor elige ahi el bloque que quiera -- incluidos nuestros 96 neones
   (D-029), que aparecen en el buscador de Axiom como cualquier otro. El script
   usa lo que haya elegido y deja de tener que adivinar.

   Los valores por defecto son de vanilla a proposito: recien pegado, el script
   FUNCIONA. Cambiar a neon es cosa de tres clics.

--------------------------------------------------------------------------------
   COMO ESTA HECHO
--------------------------------------------------------------------------------

   Todo es funcion de la posicion y de la tabla de ajustes. No hay "coloca esta
   pieza aqui": hay primitivas geometricas, y encima componentes que las
   combinan. Subir el radio recoloca la torre entera --plaza, cristaleras,
   cinturon, alas, corona-- sin descuadrar nada.

   Secciones, en orden:

      1  AJUSTES          los controles del panel
      2  MATEMATICAS      lo que Lua no trae
      3  MATERIALES       la paleta, agrupada por papel
      4  LIENZO           poner un bloque, con contadores
      5  PRIMITIVAS       linea, caja, cilindro, anillo, esfera, arco, ala...
      6  TEXTURAS         bandas, ruido, celosia, degradados
      7  COMPONENTES      plaza, cilindro, forjados, arenas, cinturon, cupula,
                          alas, corona, luna
      8  ENSAMBLAJE       el orden en que se levanta
      9  INFORME          que se ha construido

============================================================================]]


--[[============================================================================
   SECCION 1  ·  AJUSTES
   ----------------------------------------------------------------------------
   Todo lo que se puede tocar sin abrir el codigo. Aparecen como deslizadores
   y selectores en "Opciones de Herramienta" de Axiom.
============================================================================]]

-- ---- Tamano general ---------------------------------------------------------

local RADIO        = $int(Radio de la torre, 26, 12, 60)$
local PLANTAS      = $int(Plantas de combate, 6, 1, 12)$
local ALTO_PLANTA  = $int(Alto de cada planta, 14, 8, 24)$

-- ---- Plaza ------------------------------------------------------------------

local PLAZA        = $boolean(Construir la plaza, true)$
local PLAZA_GRADAS = $int(Gradas de la plaza, 5, 1, 12)$
local PLAZA_ANCHO  = $int(Vuelo de la plaza, 16, 4, 40)$

-- ---- Poke Ball --------------------------------------------------------------

local CUPULA       = $boolean(Cupula superior, true)$
local CINTURON     = $int(Grosor del cinturon, 5, 2, 12)$
local BOTON        = $boolean(Boton del cinturon, true)$

-- ---- Alas y corona ----------------------------------------------------------

local ALAS         = $boolean(Alas, true)$
local ALAS_LARGO   = $int(Envergadura, 34, 10, 70)$
local ALAS_PLUMAS  = $int(Plumas por ala, 5, 3, 9)$
local CORONA       = $boolean(Corona lunar, true)$
local LUNA_FONDO   = $boolean(Luna de fondo, true)$
local LUNA_RADIO   = $int(Radio de la luna, 30, 10, 60)$

-- ---- Interior ---------------------------------------------------------------

local INTERIOR     = $boolean(Construir interior, true)$
local NUCLEO       = $boolean(Nucleo central, true)$
local ESCALERAS    = $boolean(Escaleras entre plantas, true)$
local PORTALES     = $boolean(Portales de arena, true)$
local BARANDILLAS  = $boolean(Barandillas, true)$

-- ---- Acabado ----------------------------------------------------------------

local SEMILLA      = $int(Semilla, 1337, 0, 99999)$
local DESGASTE     = $float(Desgaste de la piedra, 0.12, 0.0, 0.6)$
local VENTANAS     = $float(Densidad de cristaleras, 0.62, 0.0, 1.0)$

-- ---- Materiales -------------------------------------------------------------
--
-- Aqui es donde entran los neones. Por defecto van bloques de vanilla para que
-- el script funcione nada mas pegarlo; el constructor cambia los que quiera
-- desde el selector.

local M_ESTRUCTURA = $blockState(Estructura, deepslate_tiles)$
local M_MURO       = $blockState(Muro, polished_deepslate)$
local M_BORDE      = $blockState(Borde, polished_blackstone)$
local M_SUELO      = $blockState(Suelo, polished_deepslate)$
local M_CINTURON   = $blockState(Cinturon, polished_blackstone_bricks)$
local M_CRISTAL    = $blockState(Cristal, tinted_glass)$
local M_METAL      = $blockState(Metal, oxidized_cut_copper)$
local M_LUZ_FRIA   = $blockState(Luz fria, soul_lantern)$
local M_LUZ_CALIDA = $blockState(Luz calida, shroomlight)$
local M_NEON_A     = $blockState(Neon principal, warped_hyphae)$
local M_NEON_B     = $blockState(Neon secundario, purpur_block)$
local M_ALA        = $blockState(Alas, blackstone)$
local M_LUNA       = $blockState(Luna, calcite)$
local M_OJOS       = $blockState(Ojos, sea_lantern)$
local M_PLAZA      = $blockState(Plaza, smooth_quartz)$
local M_PLAZA_2    = $blockState(Plaza contraste, quartz_bricks)$


--[[============================================================================
   SECCION 2  ·  MATEMATICAS
   ----------------------------------------------------------------------------
   Lo que hace falta y Lua no trae de serie. Nada exotico: redondeo honesto,
   interpolacion, y un generador de azar reproducible.
============================================================================]]

local floor, ceil, abs = math.floor, math.ceil, math.abs
local max, min, sqrt   = math.max, math.min, math.sqrt
local cos, sin, atan   = math.cos, math.sin, math.atan
local pi, huge         = math.pi, math.huge
local TAU              = pi * 2

-- Lua redondea hacia cero al convertir; esto redondea al mas cercano, que es
-- lo que se espera al pasar de una curva a una rejilla de bloques.
local function redondear(v)
  return floor(v + 0.5)
end

local function pinza(v, lo, hi)
  if v < lo then return lo end
  if v > hi then return hi end
  return v
end

local function mezcla(a, b, t)
  return a + (b - a) * t
end

-- Suavizado en S. Se usa para los perfiles: un cambio lineal de radio da un
-- cono, y este da una silueta con hombros, que es lo que tiene una Poke Ball.
local function suave(t)
  t = pinza(t, 0, 1)
  return t * t * (3 - 2 * t)
end

local function dist2(dx, dz)
  return sqrt(dx * dx + dz * dz)
end

local function dist3(dx, dy, dz)
  return sqrt(dx * dx + dy * dy + dz * dz)
end

-- Angulo en vueltas (0..1), que se lee mejor que radianes al repartir cosas
-- alrededor de un circulo.
local function vuelta(dx, dz)
  local a = atan(dz, dx) / TAU
  if a < 0 then a = a + 1 end
  return a
end

--[[
  Azar REPRODUCIBLE y ligado a la posicion.

  Con `math.random` a secas, dos ejecuciones dan torres distintas y el
  disenador no puede pedir "la de antes pero mas alta". Sembrando con la
  posicion y la semilla del panel, la misma semilla da SIEMPRE la misma torre,
  y aun asi el desgaste no se repite en patron.
]]
local function azar(a, b, c)
  local n = (a * 73856093 + b * 19349663 + c * 83492791 + SEMILLA * 6151) % 2147483647
  n = (n * 1103515245 + 12345) % 2147483647
  return n / 2147483647
end


--[[============================================================================
   SECCION 3  ·  MATERIALES
   ----------------------------------------------------------------------------
   Agrupados por PAPEL, no por nombre. El resto del script dice `MAT.muro` y
   nunca un id, asi que reskinnear la torre entera es cambiar esta tabla.
============================================================================]]

local MAT = {
  estructura = M_ESTRUCTURA,
  muro       = M_MURO,
  borde      = M_BORDE,
  suelo      = M_SUELO,
  cinturon   = M_CINTURON,
  cristal    = M_CRISTAL,
  metal      = M_METAL,
  luz_fria   = M_LUZ_FRIA,
  luz_calida = M_LUZ_CALIDA,
  neon_a     = M_NEON_A,
  neon_b     = M_NEON_B,
  ala        = M_ALA,
  luna       = M_LUNA,
  ojos       = M_OJOS,
  plaza      = M_PLAZA,
  plaza2     = M_PLAZA_2,
  aire       = blocks.air,
}

--[[
  El color de cada planta.

  Sube de frio a caliente segun se asciende: es la lectura natural de "esto va
  a mas", y desde fuera se ve por las cristaleras en que piso hay pelea. Se
  alternan los dos neones del panel con los cristales tintados de vanilla para
  tener escala de color sin pedir dieciseis selectores.
]]
local COLORES = {
  blocks.light_blue_stained_glass,
  blocks.cyan_stained_glass,
  blocks.lime_stained_glass,
  blocks.yellow_stained_glass,
  blocks.orange_stained_glass,
  blocks.red_stained_glass,
  blocks.magenta_stained_glass,
  blocks.purple_stained_glass,
  blocks.white_stained_glass,
  blocks.blue_stained_glass,
  blocks.green_stained_glass,
  blocks.pink_stained_glass,
}

local function color_planta(i)
  return COLORES[(i % #COLORES) + 1]
end

-- Una propiedad solo se aplica si el bloque la tiene. Sin esta comprobacion,
-- pedir `type=bottom` a un bloque que no es losa aborta el script entero y te
-- quedas con media torre.
local function con(bloque, propiedad, valor)
  local ok, resultado = pcall(function()
    if getBlockProperty(bloque, propiedad) ~= nil then
      return withBlockProperty(bloque, propiedad .. "=" .. valor)
    end
    return bloque
  end)
  if ok and resultado then return resultado end
  return bloque
end


--[[============================================================================
   SECCION 4  ·  EL LIENZO
   ----------------------------------------------------------------------------
   Un solo sitio por el que pasan TODOS los bloques. Eso permite contar, cortar
   por altura y, sobre todo, cambiar el comportamiento global tocando una
   funcion en vez de doscientas llamadas.
============================================================================]]

local OX, OY, OZ = x, y, z          -- el punto donde has hecho clic
local puestos, saltados = 0, 0
local alto_max = -huge

local LIMITE_ALTO  = 319            -- techo del mundo en 1.21
local LIMITE_BAJO  = -64

--[[
  Pone un bloque en coordenadas RELATIVAS al clic.

  Devuelve si lo puso, que sirve para encadenar decisiones sin repetir la
  comprobacion de limites.
]]
local function pon(dx, dy, dz, bloque)
  if bloque == nil then
    saltados = saltados + 1
    return false
  end
  local ay = OY + dy
  if ay > LIMITE_ALTO or ay < LIMITE_BAJO then
    saltados = saltados + 1
    return false
  end
  setBlock(OX + dx, ay, OZ + dz, bloque)
  puestos = puestos + 1
  if dy > alto_max then alto_max = dy end
  return true
end

-- Pone solo si ahi no hay ya algo nuestro solido. Se usa en las pasadas de
-- decoracion, para que un detalle no borre la estructura que lo sostiene.
local function pon_si_aire(dx, dy, dz, bloque)
  if getBlock(OX + dx, OY + dy, OZ + dz) == blocks.air then
    return pon(dx, dy, dz, bloque)
  end
  return false
end

local function vaciar(dx, dy, dz)
  return pon(dx, dy, dz, MAT.aire)
end


--[[============================================================================
   SECCION 5  ·  PRIMITIVAS GEOMETRICAS
   ----------------------------------------------------------------------------
   El vocabulario con el que se describe la torre. Cada una hace UNA forma y no
   sabe nada de Poke Balls: por eso se pueden combinar sin sorpresas.
============================================================================]]

-- ---- Lineas y cajas ---------------------------------------------------------

local function linea_y(dx, dz, y0, y1, bloque)
  if y0 > y1 then y0, y1 = y1, y0 end
  for dy = y0, y1 do pon(dx, dy, dz, bloque) end
end

local function linea3(x0, y0, z0, x1, y1, z1, bloque)
  local pasos = max(abs(x1 - x0), abs(y1 - y0), abs(z1 - z0))
  if pasos == 0 then return pon(x0, y0, z0, bloque) end
  for i = 0, pasos do
    local t = i / pasos
    pon(redondear(mezcla(x0, x1, t)),
        redondear(mezcla(y0, y1, t)),
        redondear(mezcla(z0, z1, t)), bloque)
  end
end

local function caja(x0, y0, z0, x1, y1, z1, bloque)
  for dx = x0, x1 do
    for dy = y0, y1 do
      for dz = z0, z1 do pon(dx, dy, dz, bloque) end
    end
  end
end

-- ---- Circulos y cilindros ---------------------------------------------------

--[[
  Un disco relleno.

  El +0.5 del radio no es un capricho: sin el, un circulo de bloques queda
  mordido en los cuatro polos y se nota mucho en piezas grandes.
]]
local function disco(cy, radio, bloque, hueco)
  local r = radio + 0.5
  local ri = hueco and (hueco + 0.5) or -1
  local lim = ceil(r)
  for dx = -lim, lim do
    for dz = -lim, lim do
      local d = dist2(dx, dz)
      if d <= r and d > ri then pon(dx, cy, dz, bloque) end
    end
  end
end

--[[
  Un anillo de un grosor dado. La base de casi todo lo que hay aqui:
  cristaleras, forjados, cinturon, barandillas.
]]
local function anillo(cy, radio, grosor, bloque)
  local re = radio + 0.5
  local ri = radio - grosor + 0.5
  local lim = ceil(re)
  for dx = -lim, lim do
    for dz = -lim, lim do
      local d = dist2(dx, dz)
      if d <= re and d > ri then pon(dx, cy, dz, bloque) end
    end
  end
end

local function cilindro(y0, y1, radio, grosor, bloque)
  for dy = y0, y1 do anillo(dy, radio, grosor, bloque) end
end

--[[
  Cilindro de radio VARIABLE, con una funcion que dice cuanto mide a cada
  altura. Es lo que permite que la Poke Ball se cierre arriba y abajo sin
  escribir la forma a mano.
]]
local function cilindro_perfil(y0, y1, grosor, perfil, bloque_fn)
  for dy = y0, y1 do
    local t = (y1 == y0) and 0 or (dy - y0) / (y1 - y0)
    local r = perfil(t, dy)
    if r and r >= 1 then
      local re, ri = r + 0.5, r - grosor + 0.5
      local lim = ceil(re)
      for dx = -lim, lim do
        for dz = -lim, lim do
          local d = dist2(dx, dz)
          if d <= re and d > ri then
            local b = bloque_fn(dx, dy, dz, d, r, t)
            if b then pon(dx, dy, dz, b) end
          end
        end
      end
    end
  end
end

-- ---- Esferas ----------------------------------------------------------------

local function esfera(cx, cy, cz, radio, bloque, hueca)
  local re = radio + 0.5
  local ri = hueca and (radio - hueca + 0.5) or -1
  local lim = ceil(re)
  for dx = -lim, lim do
    for dy = -lim, lim do
      for dz = -lim, lim do
        local d = dist3(dx, dy, dz)
        if d <= re and d > ri then
          pon(cx + dx, cy + dy, cz + dz, bloque)
        end
      end
    end
  end
end

--[[
  Media esfera. `arriba` decide que mitad.

  Es la cupula de la Poke Ball, y tambien la base de la corona. Se hace con
  elipsoide en vez de esfera para poder achatarla: una cupula perfectamente
  hemisferica se ve globo, y una achatada se ve arquitectura.
]]
local function media_esfera(cy, radio, achatado, grosor, arriba, bloque_fn)
  local ry = radio * achatado
  local lim = ceil(radio + 0.5)
  local limy = ceil(ry + 0.5)
  for dy = 0, limy do
    local ay = arriba and (cy + dy) or (cy - dy)
    for dx = -lim, lim do
      for dz = -lim, lim do
        local n = sqrt((dx * dx + dz * dz) / (radio * radio) + (dy * dy) / (ry * ry))
        local ni = sqrt((dx * dx + dz * dz) / ((radio - grosor) ^ 2)
                      + (dy * dy) / ((ry - grosor) ^ 2))
        if n <= 1.02 and ni > 1.0 then
          local b = bloque_fn(dx, dy, dz, n)
          if b then pon(dx, ay, dz, b) end
        end
      end
    end
  end
end

-- ---- Arcos y curvas ---------------------------------------------------------

--[[
  Bezier cuadratica en 3D. Es lo que dibuja las plumas de las alas: una recta
  no se parece a un ala, y una circunferencia tampoco. Una curva con un punto
  de control si, y ademas se controla con un solo numero.
]]
local function bezier(p0, p1, p2, pasos, bloque_fn)
  for i = 0, pasos do
    local t = i / pasos
    local u = 1 - t
    local px = u * u * p0[1] + 2 * u * t * p1[1] + t * t * p2[1]
    local py = u * u * p0[2] + 2 * u * t * p1[2] + t * t * p2[2]
    local pz = u * u * p0[3] + 2 * u * t * p1[3] + t * t * p2[3]
    local b = bloque_fn(t)
    if b then pon(redondear(px), redondear(py), redondear(pz), b) end
  end
end

--[[
  Un arco de medio punto en vertical, para las puertas y los portales de arena.
  Se dibuja en el plano que le digas con dos vectores directores, asi el mismo
  codigo sirve mirando al norte que al este.
]]
local function arco(cx, cy, cz, radio, ux, uz, grosor, bloque)
  for i = 0, 180 do
    local a = i / 180 * pi
    for g = 0, grosor - 1 do
      local r = radio - g
      local px = cx + redondear(cos(a) * r * ux)
      local pz = cz + redondear(cos(a) * r * uz)
      local py = cy + redondear(sin(a) * r)
      pon(px, py, pz, bloque)
    end
  end
end


--[[============================================================================
   SECCION 6  ·  TEXTURAS
   ----------------------------------------------------------------------------
   Lo que evita que 40.000 bloques del mismo material se lean como una pared.
   Todo devuelve UN bloque a partir de la posicion, asi que se enchufan
   directamente en las primitivas que aceptan `bloque_fn`.
============================================================================]]

--[[
  Desgasta una superficie mezclando un segundo material.

  El ruido va por posicion, no por `math.random`, para que dos ejecuciones con
  la misma semilla den exactamente la misma piedra. Sin eso, cada vez que el
  disenador retoca un parametro cambia hasta la ultima mancha y no puede
  comparar.
]]
local function desgastar(dx, dy, dz, base, alterno)
  if DESGASTE <= 0 then return base end
  if azar(dx, dy, dz) < DESGASTE then return alterno end
  return base
end

-- Bandas horizontales. Es lo que da escala a una torre alta: sin una linea
-- cada pocos bloques, 90 metros de muro no se leen como 90 metros.
local function bandear(dy, cada, base, banda)
  if dy % cada == 0 then return banda end
  return base
end

--[[
  Costillas verticales alrededor del cilindro.

  `cuantas` reparte por angulo y no por coordenada, que es la diferencia entre
  costillas que se abren como los radios de una rueda y costillas que se
  amontonan en las diagonales.
]]
local function costilla(dx, dz, cuantas, ancho)
  local v = vuelta(dx, dz) * cuantas
  local f = v - floor(v)
  return f < ancho or f > (1 - ancho)
end


--[[============================================================================
   SECCION 7  ·  COMPONENTES
   ----------------------------------------------------------------------------
   Aqui empieza la torre. Cada funcion es una pieza reconocible del dibujo, y
   se puede leer y tocar sin entender las demas.
============================================================================]]

-- Alturas de referencia, calculadas una vez para que todos los componentes
-- hablen de lo mismo. Cambiar PLANTAS o ALTO_PLANTA las recoloca todas.
local Y_PLAZA    = 0
local Y_BASE     = PLAZA and (PLAZA_GRADAS + 1) or 1
local ALTO_FUSTE = PLANTAS * ALTO_PLANTA
local Y_CINTURON = Y_BASE + redondear(ALTO_FUSTE * 0.52)
local Y_TAPA     = Y_BASE + ALTO_FUSTE
local Y_CUPULA   = Y_TAPA
local R_CUPULA   = RADIO
local Y_CORONA   = Y_CUPULA + redondear(RADIO * 0.62)

local function y_de_planta(i) return Y_BASE + i * ALTO_PLANTA end

--[[
  El perfil de la Poke Ball.

  Devuelve el radio a cada altura. Es UNA funcion y de ella salen la silueta
  entera: el ensanche de abajo, el cuerpo recto, el estrechamiento de arriba.
  Tocarla aqui cambia la torre completa y nada se descuadra, porque todos los
  componentes preguntan por este mismo perfil.
]]
local function perfil_fuste(dy)
  local h = dy - Y_BASE
  if h < 0 then return RADIO end
  local t = h / max(1, ALTO_FUSTE)

  -- Pie ensanchado: los primeros bloques abren un poco, como una basa.
  if t < 0.06 then
    return RADIO + redondear(mezcla(2, 0, suave(t / 0.06)))
  end
  -- Cuerpo: recto, que es lo que deja las cristaleras verticales limpias.
  if t < 0.88 then
    return RADIO
  end
  -- Hombro: cierra hacia la cupula con una S, no con una recta.
  return RADIO - redondear(mezcla(0, 3, suave((t - 0.88) / 0.12)))
end


-- ---------------------------------------------------------------- 7.1 PLAZA

--[[
  La plaza escalonada.

  En la referencia la torre no sale del suelo: se apoya en una plataforma con
  gradas y escalinatas. Cumple tres cosas a la vez -- da monumentalidad, marca
  el limite del recinto, y en juego es el sitio donde se espera turno sin
  estorbar a los que pelean.
]]
local function construir_plaza()
  if not PLAZA then return end

  local r0 = RADIO + PLAZA_ANCHO
  for g = 0, PLAZA_GRADAS do
    local r = r0 - g * 2
    local dy = Y_PLAZA + g
    for dx = -r - 1, r + 1 do
      for dz = -r - 1, r + 1 do
        local d = dist2(dx, dz)
        if d <= r + 0.5 then
          local b = MAT.plaza
          -- Un damero MUY suave. Un suelo liso de 60 metros se ve plano;
          -- con dos tonos alternos se lee la escala al caminarlo.
          if (floor(dx / 3) + floor(dz / 3)) % 2 == 0 then b = MAT.plaza2 end
          -- El canto de cada grada, marcado.
          if d > r - 0.5 then b = MAT.borde end
          pon(dx, dy, dz, desgastar(dx, dy, dz, b, MAT.plaza2))
        end
      end
    end
  end

  -- Las cuatro escalinatas, a los cuatro puntos cardinales.
  for lado = 0, 3 do
    local a = lado / 4 * TAU
    local ux, uz = cos(a), sin(a)
    for g = 0, PLAZA_GRADAS do
      local r = r0 - g * 2
      for w = -3, 3 do
        local px = redondear(ux * (r + 1) - uz * w)
        local pz = redondear(uz * (r + 1) + ux * w)
        pon(px, Y_PLAZA + g, pz, MAT.borde)
      end
    end
  end

  -- Las dos plataformas de combate del frente. En la referencia son los dos
  -- discos con emblema a los lados de la entrada: aqui son las losas donde
  -- aparecen los duelistas antes de entrar.
  for lado = -1, 1, 2 do
    local px = redondear(cos(0) * (r0 - 6))
    local pz = redondear(sin(0) * (r0 - 6)) + lado * redondear(RADIO * 0.55)
    for dx = -4, 4 do
      for dz = -4, 4 do
        local d = dist2(dx, dz)
        if d <= 4.5 then
          local b = MAT.borde
          if d <= 3.5 then b = MAT.plaza end
          -- El emblema: circulo exterior, banda y boton, como una Poke Ball.
          if d <= 1.2 then b = MAT.neon_a
          elseif d <= 2.0 then b = MAT.cinturon
          elseif d <= 3.4 and dz < 0 then b = MAT.neon_b end
          pon(px + dx, Y_PLAZA + PLAZA_GRADAS + 1, pz + dz, b)
        end
      end
    end
    linea_y(px, Y_PLAZA + PLAZA_GRADAS + 2, pz, Y_PLAZA + PLAZA_GRADAS + 2, MAT.luz_fria)
  end
end


-- ------------------------------------------------------------- 7.2 EL FUSTE

--[[
  El cuerpo cilindrico: muro, costillas y cristaleras.

  Las tres cosas se deciden en la misma pasada porque comparten la misma
  superficie, y separarlas obligaria a recorrerla tres veces y a mantener tres
  copias de la misma geometria.
]]
local function construir_fuste()
  cilindro_perfil(Y_BASE, Y_TAPA, 2,
    function(t, dy) return perfil_fuste(dy) end,
    function(dx, dy, dz, d, r, t)
      local h = dy - Y_BASE
      local planta = floor(h / ALTO_PLANTA)
      local dentro_planta = h % ALTO_PLANTA

      -- Los forjados y el remate de cada planta son macizos: es donde apoya
      -- el suelo del piso de arriba.
      if dentro_planta <= 1 or dentro_planta >= ALTO_PLANTA - 1 then
        return bandear(dy, 1, MAT.estructura, MAT.borde)
      end

      -- Las costillas verticales. Son estructura y ademas separan las
      -- cristaleras en panos, que es lo que las hace legibles.
      if costilla(dx, dz, 16, 0.16) then
        return desgastar(dx, dy, dz, MAT.estructura, MAT.muro)
      end

      -- Y entre costilla y costilla, cristal del color de la planta -- pero
      -- solo en la franja central, para que quede antepecho abajo y dintel
      -- arriba en vez de una pared de vidrio de suelo a techo.
      local franja = dentro_planta / ALTO_PLANTA
      if franja > 0.22 and franja < 0.82 and VENTANAS > 0 then
        if azar(dx, dy, dz) < VENTANAS then
          return color_planta(planta)
        end
        return MAT.cristal
      end

      return desgastar(dx, dy, dz, MAT.muro, MAT.estructura)
    end)

  -- Las lineas de neon que recorren el fuste de arriba abajo, entre costillas.
  for i = 0, 15 do
    local a = (i + 0.5) / 16 * TAU
    for dy = Y_BASE + 2, Y_TAPA - 2 do
      local r = perfil_fuste(dy)
      local px = redondear(cos(a) * r)
      local pz = redondear(sin(a) * r)
      if dy % 7 ~= 0 then
        pon(px, dy, pz, MAT.neon_a)
      end
    end
  end
end


-- ------------------------------------------------------- 7.3 EL CINTURON

--[[
  El cinturon de la Poke Ball.

  Es LA pieza que convierte un cilindro en una Poke Ball, y por eso sobresale:
  si quedara a ras del muro se leeria como una banda pintada, no como un
  cinturon. Lleva su propio reborde arriba y abajo para que la sombra lo
  despegue.
]]
local function construir_cinturon()
  local y0 = Y_CINTURON - floor(CINTURON / 2)
  local y1 = y0 + CINTURON - 1

  for dy = y0, y1 do
    local r = perfil_fuste(dy) + 2
    -- Los cantos vuelan un bloque menos: da un chaflan y evita el canto vivo.
    if dy == y0 or dy == y1 then r = r - 1 end
    anillo(dy, r, 3, MAT.cinturon)
  end

  -- Los dos filos de neon, arriba y abajo del cinturon.
  anillo(y0 - 1, perfil_fuste(y0) + 1, 1, MAT.neon_b)
  anillo(y1 + 1, perfil_fuste(y1) + 1, 1, MAT.neon_b)

  if not BOTON then return end

  -- El boton, al frente. En la referencia es el foco de la fachada, asi que
  -- se hace saliente y encendido en vez de plano.
  local rb = max(3, redondear(RADIO * 0.22))
  local cy = Y_CINTURON
  local r = perfil_fuste(cy) + 2
  for dy = -rb, rb do
    for dz = -rb, rb do
      local d = dist2(dy, dz)
      if d <= rb + 0.5 then
        local prof = redondear(mezcla(3, 0, d / (rb + 0.5)))
        for p = 0, prof do
          local b = MAT.borde
          if d <= rb * 0.45 then b = MAT.ojos
          elseif d <= rb * 0.75 then b = MAT.neon_a end
          pon(redondear(r + p), cy + dy, dz, b)
        end
      end
    end
  end
end


-- --------------------------------------------------------- 7.4 LA CUPULA

--[[
  La media Poke Ball de arriba.

  Achatada a 0.72 a proposito: una cupula hemisferica perfecta se ve globo, y
  achatada se ve arquitectura. Y va acristalada por gajos, para que desde
  dentro se vea la luna.
]]
local function construir_cupula()
  if not CUPULA then return end

  media_esfera(Y_CUPULA, R_CUPULA, 0.72, 2, true,
    function(dx, dy, dz, n)
      -- Los nervios que van del borde a la cuspide.
      if costilla(dx, dz, 16, 0.13) then
        return desgastar(dx, dy, dz, MAT.estructura, MAT.muro)
      end
      -- Anillos horizontales cada pocos bloques, o los gajos se ven flotando.
      if dy % 4 == 0 then return MAT.borde end
      -- Y el resto, cristal.
      return MAT.cristal
    end)

  -- El oculo de la cuspide y su corona de luz.
  disco(Y_CUPULA + redondear(R_CUPULA * 0.72), max(2, redondear(RADIO * 0.18)),
        MAT.ojos, nil)
  anillo(Y_CUPULA + redondear(R_CUPULA * 0.72) - 1,
         max(3, redondear(RADIO * 0.24)), 1, MAT.neon_a)
end


-- ----------------------------------------------------------- 7.5 INTERIOR

--[[
  El interior: forjados, arenas, nucleo, escaleras y portales.

  Es lo que separa una maqueta de un sitio jugable. Cada planta es una arena
  de PvP, y todo lo de aqui existe para que se pueda pelear: suelo despejado,
  un borde por el que no caerse, luz suficiente y una forma clara de subir.
]]
local function forjado(i)
  local yy = y_de_planta(i)
  local r = perfil_fuste(yy) - 2

  -- El suelo, con un aro del color de la planta marcando el centro.
  for dx = -r - 1, r + 1 do
    for dz = -r - 1, r + 1 do
      local d = dist2(dx, dz)
      if d <= r + 0.5 then
        local b = MAT.suelo
        if d > r * 0.92 then b = MAT.borde
        elseif d > r * 0.34 and d < r * 0.40 then b = color_planta(i)
        elseif d < r * 0.14 then b = MAT.estructura end
        pon(dx, yy, dz, desgastar(dx, yy, dz, b, MAT.estructura))
      end
    end
  end

  -- El techo de la planta, que es el forjado de la siguiente. Se deja el hueco
  -- del nucleo para que la escalera pase.
  if BARANDILLAS then
    anillo(yy + 1, r, 1, MAT.borde)
    for paso = 0, 31 do
      local a = paso / 32 * TAU
      local px = redondear(cos(a) * (r - 0.5))
      local pz = redondear(sin(a) * (r - 0.5))
      pon(px, yy + 2, pz, MAT.neon_b)
    end
  end
end

--[[
  El nucleo central: el eje por el que se sube.

  Va hueco y con su propia luz. En la referencia es la columna vertical que se
  ve por las cristaleras; en juego es la unica ruta entre plantas, y eso hace
  que los combates tengan un cuello de botella claro.
]]
local function construir_nucleo()
  if not NUCLEO then return end
  local rn = max(3, redondear(RADIO * 0.16))

  cilindro(Y_BASE, Y_TAPA, rn, 1, MAT.estructura)
  for dy = Y_BASE + 1, Y_TAPA - 1 do
    disco(dy, rn - 1, MAT.aire, nil)
    if dy % 5 == 0 then
      anillo(dy, rn - 1, 1, MAT.neon_a)
    end
  end
  linea_y(0, Y_BASE, 0, Y_TAPA, MAT.ojos)
end

--[[
  La escalera de caracol que envuelve el nucleo.

  De caracol y no recta porque una recta necesita un hueco rectangular que
  rompe el cilindro, y ademas obliga a mirar la pared mientras subes. En
  caracol se sube viendo la arena, que es lo que se quiere en un sitio de
  espectaculo.
]]
local function construir_escaleras()
  if not ESCALERAS then return end
  local rn = max(3, redondear(RADIO * 0.16)) + 2
  local vueltas = PLANTAS * 1.5
  local pasos = (Y_TAPA - Y_BASE) * 4

  for i = 0, pasos do
    local t = i / pasos
    local dy = redondear(mezcla(Y_BASE + 1, Y_TAPA - 2, t))
    local a = t * vueltas * TAU
    for w = 0, 2 do
      local r = rn + w
      local px = redondear(cos(a) * r)
      local pz = redondear(sin(a) * r)
      pon(px, dy, pz, MAT.borde)
      if w == 2 then pon(px, dy + 1, pz, MAT.neon_b) end
    end
  end
end

--[[
  Los portales de cada arena.

  En la referencia cada planta tiene un arco encendido de su color, y eso es
  exactamente lo que se necesita en juego: ver desde fuera en que piso hay
  pelea, y desde dentro por donde se entra y se sale.
]]
local function construir_portales()
  if not PORTALES then return end

  for i = 0, PLANTAS - 1 do
    local yy = y_de_planta(i) + 1
    local r = perfil_fuste(yy)
    local color = color_planta(i)
    -- Uno a cada lado, para que la arena tenga dos accesos y no se pueda
    -- taponar con un solo jugador.
    for lado = 0, 1 do
      local a = lado * pi
      local ux, uz = cos(a), sin(a)
      local cx = redondear(ux * r)
      local cz = redondear(uz * r)
      local rad = max(3, redondear(ALTO_PLANTA * 0.34))

      -- El hueco, vaciado antes de enmarcarlo.
      for h = 0, rad do
        for w = -rad, rad do
          if dist2(w, h) <= rad then
            local px = cx + redondear(-uz * w)
            local pz = cz + redondear(ux * w)
            for p = -2, 2 do
              vaciar(px + redondear(ux * p), yy + h, pz + redondear(uz * p))
            end
          end
        end
      end
      -- El marco y su luz.
      arco(cx, yy, cz, rad + 1, -uz, ux, 2, MAT.estructura)
      arco(cx, yy, cz, rad, -uz, ux, 1, color)
      linea_y(cx + redondear(-uz * (rad + 1)), yy, cz + redondear(ux * (rad + 1)),
              yy + 1, MAT.estructura)
      linea_y(cx + redondear(uz * (rad + 1)), yy, cz + redondear(-ux * (rad + 1)),
              yy + 1, MAT.estructura)
    end
  end
end

local function construir_interior()
  if not INTERIOR then return end
  -- Primero se vacia todo el tubo: es mucho mas barato que ir esquivando.
  for dy = Y_BASE + 1, Y_TAPA - 1 do
    disco(dy, perfil_fuste(dy) - 2, MAT.aire, nil)
  end
  for i = 0, PLANTAS - 1 do forjado(i) end
  construir_nucleo()
  construir_escaleras()
  construir_portales()
end


-- --------------------------------------------------------------- 7.6 ALAS

--[[
  Las alas.

  Son lo que da caracter en la referencia, y lo que mas facil es que salga mal:
  unas alas rectas parecen tablones. Cada pluma es una bezier con su punto de
  control desplazado, y las plumas se abren en abanico con longitudes
  decrecientes -- que es como esta hecha un ala de verdad.
]]
local function construir_alas()
  if not ALAS then return end

  local cy = Y_CINTURON + redondear(RADIO * 0.25)
  for lado = -1, 1, 2 do
    for p = 0, ALAS_PLUMAS - 1 do
      local t = p / max(1, ALAS_PLUMAS - 1)
      -- Las de arriba son mas largas y suben mas: es lo que abre el abanico.
      local largo = ALAS_LARGO * mezcla(1.0, 0.55, t)
      local subida = mezcla(RADIO * 0.85, -RADIO * 0.30, t)
      local grosor = redondear(mezcla(3, 1, t))

      local p0 = { lado * (RADIO + 1), cy, 0 }
      local p1 = { lado * (RADIO + largo * 0.45), cy + subida * 1.15,
                   -largo * 0.30 }
      local p2 = { lado * (RADIO + largo), cy + subida * 0.35,
                   -largo * 0.62 }

      bezier(p0, p1, p2, redondear(largo * 2.2), function(tt)
        return (tt > 0.86) and MAT.neon_b or MAT.ala
      end)

      -- Grosor: la misma curva desplazada. Una pluma de un bloque se ve
      -- transparente de perfil.
      for g = 1, grosor do
        bezier({ p0[1], p0[2] - g, p0[3] },
               { p1[1], p1[2] - g, p1[3] },
               { p2[1], p2[2] - g, p2[3] },
               redondear(largo * 2.2), function() return MAT.ala end)
      end

      -- El filo encendido del borde de ataque.
      bezier({ p0[1], p0[2] + 1, p0[3] },
             { p1[1], p1[2] + 1, p1[3] },
             { p2[1], p2[2] + 1, p2[3] },
             redondear(largo * 2.2), function(tt)
        return (tt > 0.30) and MAT.neon_a or nil
      end)
    end
  end
end


-- ------------------------------------------------------------- 7.7 CORONA

--[[
  La corona lunar: la cabeza que remata la torre.

  Es la firma del sitio y lo que se ve desde el otro lado del mapa. Se compone
  de tres cosas: el casco, los dos cuernos que dibujan el creciente, y los
  ojos.
]]
local function construir_corona()
  if not CORONA then return end

  local rc = max(6, redondear(RADIO * 0.55))
  local cy = Y_CORONA

  -- El casco: media esfera maciza, con nervios.
  media_esfera(cy, rc, 0.95, 3, true, function(dx, dy, dz, n)
    if costilla(dx, dz, 10, 0.14) then return MAT.estructura end
    return desgastar(dx, dy, dz, MAT.ala, MAT.estructura)
  end)
  disco(cy, rc, MAT.ala, nil)

  -- Los dos cuernos. Curvan hacia dentro arriba, que es lo que cierra el
  -- creciente en vez de dejar dos pinchos.
  for lado = -1, 1, 2 do
    local largo = rc * 1.9
    bezier({ lado * rc * 0.72, cy + rc * 0.25, 0 },
           { lado * rc * 1.35, cy + largo * 0.72, 0 },
           { lado * rc * 0.42, cy + largo * 1.18, 0 },
           redondear(largo * 2.4), function(t)
      return (t > 0.78) and MAT.luna or MAT.ala
    end)
    for g = 1, 3 do
      bezier({ lado * rc * 0.72, cy + rc * 0.25, g - 2 },
             { lado * rc * 1.35, cy + largo * 0.72, g - 2 },
             { lado * rc * 0.42, cy + largo * 1.18, g - 2 },
             redondear(largo * 2.4), function() return MAT.ala end)
    end
  end

  -- Los ojos. Dos, encendidos, hacia el frente. Es el detalle que hace que la
  -- torre "mire" a quien llega.
  for lado = -1, 1, 2 do
    local ox2 = lado * redondear(rc * 0.42)
    local oy2 = cy + redondear(rc * 0.46)
    for dx = -2, 2 do
      for dy = -1, 1 do
        if abs(dx) + abs(dy) <= 2 then
          pon(ox2 + dx, oy2 + dy, redondear(rc * 0.86), MAT.ojos)
        end
      end
    end
  end
end


-- ---------------------------------------------------------------- 7.8 LUNA

--[[
  La luna de fondo.

  Va DETRAS y desplazada, no encima: en la referencia enmarca la corona en vez
  de coronarla. Hueca, porque una esfera maciza de radio 30 son 113.000 bloques
  para algo que solo se ve por fuera.
]]
local function construir_luna()
  if not LUNA_FONDO then return end
  local cy = Y_CORONA + redondear(LUNA_RADIO * 0.55)
  local cz = -redondear(LUNA_RADIO * 1.25)
  esfera(0, cy, cz, LUNA_RADIO, MAT.luna, 2)

  -- Unos cuantos crateres, para que no sea una bola lisa.
  for i = 1, 14 do
    local a = azar(i, 1, 1) * TAU
    local b = azar(i, 2, 2) * pi - pi / 2
    local rr = 2 + floor(azar(i, 3, 3) * 4)
    local px = redondear(cos(b) * cos(a) * LUNA_RADIO)
    local py = redondear(sin(b) * LUNA_RADIO)
    local pz = redondear(cos(b) * sin(a) * LUNA_RADIO)
    esfera(px, cy + py, cz + pz, rr, MAT.borde, 1)
  end
end


--[[============================================================================
   SECCION 8  ·  ENSAMBLAJE
   ----------------------------------------------------------------------------
   El orden importa y no es arbitrario: lo estructural primero, lo que vacia
   despues, y lo decorativo al final para que nada lo pise.
============================================================================]]

construir_plaza()
construir_fuste()
construir_interior()      -- vacia y amuebla: va DESPUES del fuste
construir_cinturon()      -- sobresale del muro: va despues de vaciar
construir_cupula()
construir_alas()
construir_corona()
construir_luna()


--[[============================================================================
   SECCION 9  ·  INFORME
============================================================================]]

-- El pincel tiene que devolver algo. Se devuelve nil para que el clic en si no
-- cambie el bloque que senalaste: todo lo ha colocado `setBlock`.
--
-- (`return nil` es valido: lo usan los propios scripts de ejemplo de Axiom.)
return nil
