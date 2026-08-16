$once$
--[[============================================================================

   T O R R E   D E   B A T A L L A   L U N A R      ·      v2  FUTURISTA
   PokeReport: Luna Eternal   ·   Lua Script Brush de Axiom

   Una Poke Ball gigante partida por su cinturon, alzada sobre una plaza
   escalonada, envuelta en cristal y neon. Seis arenas de PvP dentro, alas
   membranosas a los lados, anillos flotando alrededor y una corona lunar con
   los ojos encendidos. Se sube peleando: cada planta es un combate y un rango.

--------------------------------------------------------------------------------
   QUE CAMBIA RESPECTO A LA v1
--------------------------------------------------------------------------------

   La v1 se levanto bien pero se veia APAGADA, y la causa no era el diseno: los
   materiales por defecto eran bloques de vanilla y ninguno emite luz. Una torre
   nocturna hecha de piedra apagada es una torre gris.

   Lo que se ha hecho:

     * TODOS los defaults pasan a bloques que EMITEN. El script recien pegado ya
       se ve encendido, sin tocar un selector.
     * Ocho ranuras de neon en vez de dos, y una rampa de color por altura: la
       torre pasa de frio abajo a caliente arriba, que es como se lee "esto va
       a mas" sin escribir un cartel.
     * Cristal por defecto en las cristaleras, con celosia tecnologica detras.
     * Piezas nuevas: anillos flotantes, contrafuertes, pasarelas exteriores,
       antena-faro, nucleo de energia, gradas de publico, marcador del cinturon.
     * Pasadas de detalle al final: cornisas iluminadas, remates y limpieza.

   Y dos fallos de la v1, vistos en el juego y corregidos:

     * LA CUPULA FLOTABA. El hombro del fuste cerraba antes de llegar a ella y
       quedaba un vacio. Ahora el perfil y la cupula comparten la misma altura
       de encuentro, calculada una vez.
     * LAS ALAS SALIAN COMO PUNTOS SUELTOS. Eran curvas de un bloque de grosor:
       de lejos, polvo. Ahora son MEMBRANAS -- se rellena entre nervio y nervio.

--------------------------------------------------------------------------------
   COMO SE USA
--------------------------------------------------------------------------------

   1. Axiom -> Herramientas -> Lua Script Brush
   2. Pega este fichero ENTERO, incluida la primera linea `$once$`
   3. Los controles salen solos en "Opciones de Herramienta"
   4. Clic derecho en el suelo, en el CENTRO de donde quieras la torre

   ⚠ ACTIVA EL HISTORIAL ANTES. Son decenas de miles de bloques y sin historial
     no hay Ctrl+Z que valga.

   ⚠ CABE DONDE LE DEJES. Con los valores de fabrica ocupa unos 85 bloques de
     ancho y pasa de 160 de alto. Para la plaza de la ciudadela (56x56), baja
     `Radio de la torre` a 16 y `Vuelo de la plaza` a 8.

--------------------------------------------------------------------------------
   POR QUE LOS MATERIALES SON SELECTORES
--------------------------------------------------------------------------------

   La documentacion de Axiom solo describe `blocks.stone` para bloques de
   Minecraft; NO documenta como pedir uno de OTRO MOD. Escribir
   `blocks["lunaneon:neon_cian"]` seria apostar.

   `$blockState(Titulo, por_defecto)$` da un SELECTOR en el panel: el constructor
   elige ahi el bloque, incluidos nuestros 96 neones (D-029), que aparecen en el
   buscador de Axiom como cualquier otro. El script usa lo que haya elegido.

   Los defaults son de vanilla Y EMISIVOS, para que funcione y BRILLE recien
   pegado. Cambiar a nuestro neon es un clic por ranura.

--------------------------------------------------------------------------------
   ARQUITECTURA
--------------------------------------------------------------------------------

    1  AJUSTES        los controles del panel
    2  MATEMATICAS    redondeo, interpolacion, suavizados, azar reproducible
    3  MATERIALES     paleta por papel + rampa de color
    4  LIENZO         un solo sitio por el que pasan todos los bloques
    5  PRIMITIVAS     linea, caja, disco, anillo, cilindro, esfera, toro,
                      bezier, arco, membrana, helice
    6  TEXTURAS       bandas, desgaste, costillas, paneles, circuito, celosia
    7  COMPONENTES    plaza, fuste, cinturon, cupula, interior, alas, corona,
                      luna, anillos, contrafuertes, pasarelas, antena
    8  DETALLE        cornisas, remates y limpieza final
    9  ENSAMBLAJE     el orden, que no es arbitrario

============================================================================]]


--[[============================================================================
   SECCION 1  ·  AJUSTES
============================================================================]]

-- ---- Tamano -----------------------------------------------------------------

local RADIO        = $int(Radio de la torre, 26, 10, 60)$
local PLANTAS      = $int(Plantas de combate, 6, 1, 12)$
local ALTO_PLANTA  = $int(Alto de cada planta, 14, 8, 24)$

-- ---- Plaza ------------------------------------------------------------------

local PLAZA        = $boolean(Plaza, true)$
local PLAZA_GRADAS = $int(Gradas de la plaza, 5, 1, 12)$
local PLAZA_ANCHO  = $int(Vuelo de la plaza, 16, 4, 40)$
local PASARELAS    = $boolean(Pasarelas exteriores, true)$

-- ---- Poke Ball --------------------------------------------------------------

local CUPULA       = $boolean(Cupula superior, true)$
local CINTURON     = $int(Grosor del cinturon, 5, 2, 12)$
local BOTON        = $boolean(Boton del cinturon, true)$
local MARCADOR     = $boolean(Marcador de ranking, true)$

-- ---- Piezas de fuera --------------------------------------------------------

local ALAS         = $boolean(Alas, true)$
local ALAS_LARGO   = $int(Envergadura, 34, 10, 70)$
local ALAS_PLUMAS  = $int(Nervios por ala, 5, 3, 9)$
local CORONA       = $boolean(Corona lunar, true)$
local LUNA_FONDO   = $boolean(Luna de fondo, true)$
local LUNA_RADIO   = $int(Radio de la luna, 30, 10, 60)$
local ANILLOS      = $int(Anillos flotantes, 3, 0, 6)$
local CONTRAFUERTE = $boolean(Contrafuertes, true)$
local ANTENA       = $boolean(Antena faro, true)$

-- ---- Interior ---------------------------------------------------------------

local INTERIOR     = $boolean(Interior, true)$
local NUCLEO       = $boolean(Nucleo de energia, true)$
local ESCALERAS    = $boolean(Escaleras, true)$
local PORTALES     = $boolean(Portales de arena, true)$
local BARANDILLAS  = $boolean(Barandillas, true)$
local GRADAS       = $boolean(Gradas de publico, true)$
local SUELO_CRISTAL= $boolean(Suelos de cristal, true)$

-- ---- Acabado ----------------------------------------------------------------

local SEMILLA      = $int(Semilla, 1337, 0, 99999)$
local DESGASTE     = $float(Desgaste, 0.10, 0.0, 0.6)$
local VENTANAS     = $float(Densidad de cristaleras, 0.72, 0.0, 1.0)$
local NEON         = $float(Intensidad de neon, 0.85, 0.0, 1.0)$
local CIRCUITO     = $float(Circuito en los muros, 0.35, 0.0, 1.0)$
local DETALLE      = $boolean(Pasadas de detalle, true)$

-- ---- Materiales -------------------------------------------------------------
--
-- ⚠ TODOS LOS DEFAULTS EMITEN LUZ O SON CRISTAL. Es el arreglo del "se ve muy
--   apagado": la v1 traia piedra de vanilla y una torre nocturna de piedra
--   apagada es una torre gris. Aqui se cambia por nuestro neon con un clic.

local M_ESTRUCTURA = $blockState(Estructura, deepslate_tiles)$
local M_MURO       = $blockState(Muro, polished_deepslate)$
local M_BORDE      = $blockState(Borde, polished_blackstone)$
local M_SUELO      = $blockState(Suelo, polished_deepslate)$
local M_CINTURON   = $blockState(Cinturon, polished_blackstone_bricks)$
local M_METAL      = $blockState(Metal, waxed_oxidized_cut_copper)$

local M_CRISTAL    = $blockState(Cristal principal, tinted_glass)$
local M_CRISTAL_2  = $blockState(Cristal claro, light_blue_stained_glass)$
local M_CRISTAL_3  = $blockState(Cristal profundo, blue_stained_glass)$

local M_NEON_1     = $blockState(Neon 1 frio, sea_lantern)$
local M_NEON_2     = $blockState(Neon 2, verdant_froglight)$
local M_NEON_3     = $blockState(Neon 3, pearlescent_froglight)$
local M_NEON_4     = $blockState(Neon 4 calido, ochre_froglight)$
local M_NEON_5     = $blockState(Neon 5 magenta, crying_obsidian)$
local M_NEON_6     = $blockState(Neon 6 intenso, glowstone)$
local M_NEON_LINEA = $blockState(Neon de lineas, amethyst_block)$
local M_NEON_NUCLEO= $blockState(Neon del nucleo, beacon)$

local M_ALA        = $blockState(Alas, blackstone)$
local M_ALA_MEMBR  = $blockState(Membrana de las alas, tinted_glass)$
local M_LUNA       = $blockState(Luna, calcite)$
local M_OJOS       = $blockState(Ojos, sea_lantern)$
local M_PLAZA      = $blockState(Plaza, smooth_quartz)$
local M_PLAZA_2    = $blockState(Plaza contraste, quartz_bricks)$


--[[============================================================================
   SECCION 2  ·  MATEMATICAS
============================================================================]]

local floor, ceil, abs = math.floor, math.ceil, math.abs
local max, min, sqrt   = math.max, math.min, math.sqrt
local cos, sin, atan   = math.cos, math.sin, math.atan
local pi, huge         = math.pi, math.huge
local TAU              = pi * 2

local function redondear(v) return floor(v + 0.5) end

local function pinza(v, lo, hi)
  if v < lo then return lo end
  if v > hi then return hi end
  return v
end

local function mezcla(a, b, t) return a + (b - a) * t end

-- Suavizado en S. Un cambio lineal de radio da un cono; este da hombros, que
-- es lo que tiene una Poke Ball.
local function suave(t)
  t = pinza(t, 0, 1)
  return t * t * (3 - 2 * t)
end

-- Entrada suave, salida seca. Para los perfiles que arrancan despacio.
local function suave_ent(t) t = pinza(t, 0, 1) return t * t end
local function suave_sal(t) t = pinza(t, 0, 1) return 1 - (1 - t) * (1 - t) end

local function dist2(dx, dz) return sqrt(dx * dx + dz * dz) end
local function dist3(dx, dy, dz) return sqrt(dx * dx + dy * dy + dz * dz) end

local function vuelta(dx, dz)
  local a = atan(dz, dx) / TAU
  if a < 0 then a = a + 1 end
  return a
end

--[[
  Azar REPRODUCIBLE y ligado a la posicion.

  Con `math.random` a secas, dos ejecuciones dan torres distintas y el disenador
  no puede pedir "la de antes pero mas alta". Sembrando con la posicion y la
  semilla del panel, la misma semilla da SIEMPRE la misma torre.
]]
local function azar(a, b, c)
  local n = (a * 73856093 + b * 19349663 + c * 83492791 + SEMILLA * 6151) % 2147483647
  n = (n * 1103515245 + 12345) % 2147483647
  return n / 2147483647
end

-- Ruido suave a partir del azar por celdas. No es Perlin, pero para manchar
-- piedra y repartir luces sobra, y no depende de que Axiom exponga su ruido.
local function ruido(x1, y1, z1, escala)
  local e = escala or 6
  local cx, cy, cz = floor(x1 / e), floor(y1 / e), floor(z1 / e)
  local fx, fy, fz = (x1 / e) - cx, (y1 / e) - cy, (z1 / e) - cz
  fx, fy, fz = suave(fx), suave(fy), suave(fz)
  local function v(a, b, c) return azar(cx + a, cy + b, cz + c) end
  local x00 = mezcla(v(0,0,0), v(1,0,0), fx)
  local x10 = mezcla(v(0,1,0), v(1,1,0), fx)
  local x01 = mezcla(v(0,0,1), v(1,0,1), fx)
  local x11 = mezcla(v(0,1,1), v(1,1,1), fx)
  return mezcla(mezcla(x00, x10, fy), mezcla(x01, x11, fy), fz)
end


--[[============================================================================
   SECCION 3  ·  MATERIALES
============================================================================]]

local MAT = {
  estructura = M_ESTRUCTURA,
  muro       = M_MURO,
  borde      = M_BORDE,
  suelo      = M_SUELO,
  cinturon   = M_CINTURON,
  metal      = M_METAL,
  cristal    = M_CRISTAL,
  cristal2   = M_CRISTAL_2,
  cristal3   = M_CRISTAL_3,
  ala        = M_ALA,
  membrana   = M_ALA_MEMBR,
  luna       = M_LUNA,
  ojos       = M_OJOS,
  plaza      = M_PLAZA,
  plaza2     = M_PLAZA_2,
  linea      = M_NEON_LINEA,
  nucleo     = M_NEON_NUCLEO,
  aire       = blocks.air,
}

--[[
  LA RAMPA DE NEON.

  Seis ranuras ordenadas de frio a calido. La torre las recorre de abajo arriba,
  asi que el color dice a que altura estas sin necesidad de un cartel -- y desde
  fuera, por las cristaleras, se ve en que planta hay pelea.

  Se pide por un valor 0..1 en vez de por indice para que el mismo codigo sirva
  con seis plantas que con doce: el reparto se ajusta solo.
]]
local RAMPA = { M_NEON_1, M_NEON_2, M_NEON_3, M_NEON_4, M_NEON_5, M_NEON_6 }

local function neon_de(t)
  local i = floor(pinza(t, 0, 0.999) * #RAMPA) + 1
  return RAMPA[i]
end

local function neon_planta(i)
  if PLANTAS <= 1 then return RAMPA[1] end
  return neon_de(i / (PLANTAS - 1))
end

-- Los cristales tambien tienen rampa, para que la cristalera de cada planta
-- acompane a su neon en vez de pelearse con el.
local CRISTALES = {
  blocks.light_blue_stained_glass, blocks.cyan_stained_glass,
  blocks.lime_stained_glass,       blocks.yellow_stained_glass,
  blocks.orange_stained_glass,     blocks.red_stained_glass,
  blocks.magenta_stained_glass,    blocks.purple_stained_glass,
  blocks.blue_stained_glass,       blocks.green_stained_glass,
  blocks.pink_stained_glass,       blocks.white_stained_glass,
}

local function cristal_planta(i)
  return CRISTALES[(i % #CRISTALES) + 1]
end

--[[
  Aplica una propiedad SOLO si el bloque la tiene.

  Sin esta comprobacion, pedir `type=bottom` a algo que no es losa aborta el
  script entero y te deja media torre. Y como el constructor puede elegir
  cualquier bloque en los selectores, aqui puede llegar cualquier cosa.
]]
local function con(bloque, propiedad, valor)
  local ok, r = pcall(function()
    if getBlockProperty(bloque, propiedad) ~= nil then
      return withBlockProperty(bloque, propiedad .. "=" .. valor)
    end
    return bloque
  end)
  if ok and r then return r end
  return bloque
end

-- ¿Toca poner neon aqui? La intensidad del panel decide cuanta luz lleva la
-- torre sin tener que rehacer ningun componente.
local function hay_neon(dx, dy, dz)
  if NEON >= 1 then return true end
  if NEON <= 0 then return false end
  return azar(dx, dy, dz) < NEON
end


--[[============================================================================
   SECCION 4  ·  EL LIENZO
============================================================================]]

local OX, OY, OZ = x, y, z
local puestos, saltados = 0, 0
local LIMITE_ALTO, LIMITE_BAJO = 319, -64

local function pon(dx, dy, dz, bloque)
  if bloque == nil then saltados = saltados + 1 return false end
  local ay = OY + dy
  if ay > LIMITE_ALTO or ay < LIMITE_BAJO then
    saltados = saltados + 1
    return false
  end
  setBlock(OX + dx, ay, OZ + dz, bloque)
  puestos = puestos + 1
  return true
end

local function pon_si_aire(dx, dy, dz, bloque)
  if getBlock(OX + dx, OY + dy, OZ + dz) == blocks.air then
    return pon(dx, dy, dz, bloque)
  end
  return false
end

local function vaciar(dx, dy, dz) return pon(dx, dy, dz, MAT.aire) end


--[[============================================================================
   SECCION 5  ·  PRIMITIVAS
============================================================================]]

local function linea_y(dx, dz, y0, y1, bloque)
  if y0 > y1 then y0, y1 = y1, y0 end
  for dy = y0, y1 do pon(dx, dy, dz, bloque) end
end

local function linea3(x0, y0, z0, x1, y1, z1, bloque)
  local pasos = max(abs(x1 - x0), abs(y1 - y0), abs(z1 - z0))
  if pasos == 0 then return pon(x0, y0, z0, bloque) end
  for i = 0, pasos do
    local t = i / pasos
    pon(redondear(mezcla(x0, x1, t)), redondear(mezcla(y0, y1, t)),
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

-- El +0.5 del radio no es capricho: sin el, un circulo de bloques queda mordido
-- en los cuatro polos y se nota mucho en piezas grandes.
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

local function disco_fn(cy, radio, hueco, bloque_fn)
  local r = radio + 0.5
  local ri = hueco and (hueco + 0.5) or -1
  local lim = ceil(r)
  for dx = -lim, lim do
    for dz = -lim, lim do
      local d = dist2(dx, dz)
      if d <= r and d > ri then
        local b = bloque_fn(dx, dz, d)
        if b then pon(dx, cy, dz, b) end
      end
    end
  end
end

local function anillo(cy, radio, grosor, bloque)
  local re, ri = radio + 0.5, radio - grosor + 0.5
  local lim = ceil(re)
  for dx = -lim, lim do
    for dz = -lim, lim do
      local d = dist2(dx, dz)
      if d <= re and d > ri then pon(dx, cy, dz, bloque) end
    end
  end
end

local function anillo_fn(cy, radio, grosor, bloque_fn)
  local re, ri = radio + 0.5, radio - grosor + 0.5
  local lim = ceil(re)
  for dx = -lim, lim do
    for dz = -lim, lim do
      local d = dist2(dx, dz)
      if d <= re and d > ri then
        local b = bloque_fn(dx, dz, d)
        if b then pon(dx, cy, dz, b) end
      end
    end
  end
end

local function cilindro(y0, y1, radio, grosor, bloque)
  for dy = y0, y1 do anillo(dy, radio, grosor, bloque) end
end

-- Cilindro de radio VARIABLE. Es lo que permite que la Poke Ball se cierre
-- arriba y abajo sin escribir la forma a mano.
local function cilindro_perfil(y0, y1, grosor, perfil, bloque_fn)
  for dy = y0, y1 do
    local r = perfil(dy)
    if r and r >= 1 then
      local re, ri = r + 0.5, r - grosor + 0.5
      local lim = ceil(re)
      for dx = -lim, lim do
        for dz = -lim, lim do
          local d = dist2(dx, dz)
          if d <= re and d > ri then
            local b = bloque_fn(dx, dy, dz, d, r)
            if b then pon(dx, dy, dz, b) end
          end
        end
      end
    end
  end
end

local function esfera(cx, cy, cz, radio, bloque, hueca)
  local re = radio + 0.5
  local ri = hueca and (radio - hueca + 0.5) or -1
  local lim = ceil(re)
  for dx = -lim, lim do
    for dy = -lim, lim do
      for dz = -lim, lim do
        local d = dist3(dx, dy, dz)
        if d <= re and d > ri then pon(cx + dx, cy + dy, cz + dz, bloque) end
      end
    end
  end
end

--[[
  Media esfera achatada.

  A 0.72 a proposito: una cupula hemisferica perfecta se ve globo, y achatada se
  ve arquitectura.
]]
local function media_esfera(cy, radio, achatado, grosor, arriba, bloque_fn)
  local ry = radio * achatado
  local lim, limy = ceil(radio + 0.5), ceil(ry + 0.5)
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

--[[
  Un toro: la rosquilla de los anillos flotantes.

  `inclinacion` lo ladea, que es lo que hace que tres anillos concentricos no
  parezcan tres platos apilados.
]]
local function toro(cy, radio, grosor, inclinacion, bloque_fn)
  local pasos = ceil(radio * TAU * 1.6)
  for i = 0, pasos do
    local a = i / pasos * TAU
    local bx, bz = cos(a) * radio, sin(a) * radio
    local by = sin(a) * radio * inclinacion
    for gx = -grosor, grosor do
      for gy = -grosor, grosor do
        if dist2(gx, gy) <= grosor + 0.3 then
          local nx = bx + gx * cos(a)
          local nz = bz + gx * sin(a)
          local b = bloque_fn(a / TAU)
          if b then pon(redondear(nx), redondear(cy + by + gy), redondear(nz), b) end
        end
      end
    end
  end
end

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

-- El punto de una bezier, sin dibujar. Lo necesita la membrana de las alas para
-- saber por donde van dos nervios y rellenar entre ellos.
local function bezier_punto(p0, p1, p2, t)
  local u = 1 - t
  return u * u * p0[1] + 2 * u * t * p1[1] + t * t * p2[1],
         u * u * p0[2] + 2 * u * t * p1[2] + t * t * p2[2],
         u * u * p0[3] + 2 * u * t * p1[3] + t * t * p2[3]
end

--[[
  ⚠ LA MEMBRANA. Es el arreglo del fallo mas visible de la v1.

  Las alas eran curvas de un bloque de grosor y de lejos se veian como puntos
  sueltos de polvo negro. Un ala no es un contorno: es una superficie.

  Esto toma DOS curvas y rellena el hueco entre ellas, que es como esta hecha
  un ala de murcielago -- nervios y piel tirante entre nervio y nervio.
]]
local function membrana(a0, a1, a2, b0, b1, b2, pasos, ancho_fn, bloque_fn)
  for i = 0, pasos do
    local t = i / pasos
    local ax, ay, az = bezier_punto(a0, a1, a2, t)
    local bx, by, bz = bezier_punto(b0, b1, b2, t)
    local tramos = max(1, redondear(dist3(bx - ax, by - ay, bz - az)))
    local hasta = ancho_fn and ancho_fn(t) or 1.0
    for j = 0, tramos do
      local s = j / tramos
      if s <= hasta then
        local b = bloque_fn(t, s)
        if b then
          pon(redondear(mezcla(ax, bx, s)), redondear(mezcla(ay, by, s)),
              redondear(mezcla(az, bz, s)), b)
        end
      end
    end
  end
end

local function arco(cx, cy, cz, radio, ux, uz, grosor, bloque)
  for i = 0, 180 do
    local a = i / 180 * pi
    for g = 0, grosor - 1 do
      local r = radio - g
      pon(cx + redondear(cos(a) * r * ux), cy + redondear(sin(a) * r),
          cz + redondear(cos(a) * r * uz), bloque)
    end
  end
end

-- Una helice alrededor del eje. Escaleras, cintas de luz, cables.
local function helice(y0, y1, radio_fn, vueltas, grosor, bloque_fn)
  local pasos = (y1 - y0) * 6
  for i = 0, pasos do
    local t = i / pasos
    local dy = redondear(mezcla(y0, y1, t))
    local a = t * vueltas * TAU
    local r = radio_fn(t, dy)
    for g = 0, grosor - 1 do
      local px = redondear(cos(a) * (r + g))
      local pz = redondear(sin(a) * (r + g))
      local b = bloque_fn(t, g)
      if b then pon(px, dy, pz, b) end
    end
  end
end


--[[============================================================================
   SECCION 6  ·  TEXTURAS
============================================================================]]

local function desgastar(dx, dy, dz, base, alterno)
  if DESGASTE <= 0 then return base end
  if azar(dx, dy, dz) < DESGASTE then return alterno end
  return base
end

local function bandear(dy, cada, base, banda)
  if dy % cada == 0 then return banda end
  return base
end

-- Costillas repartidas por ANGULO, no por coordenada: asi se abren como los
-- radios de una rueda en vez de amontonarse en las diagonales.
local function costilla(dx, dz, cuantas, ancho)
  local v = vuelta(dx, dz) * cuantas
  local f = v - floor(v)
  return f < ancho or f > (1 - ancho)
end

--[[
  Circuito: lineas quebradas por el muro, como una placa.

  Es lo que hace que un panel liso se lea como tecnologia y no como cemento. Va
  por ruido y no por azar puro para que las lineas SIGAN, en vez de salir como
  puntitos sueltos.
]]
local function hay_circuito(dx, dy, dz)
  if CIRCUITO <= 0 then return false end
  local n = ruido(dx * 2, dy, dz * 2, 5)
  return n > (1 - CIRCUITO * 0.35) and n < (1 - CIRCUITO * 0.30)
end

-- Franjas horizontales finas, tipo escaner. Muy futurista y muy barato.
local function escaner(dy, cada)
  return dy % cada == 0
end

-- Paneles: divide la superficie en rectangulos con junta. Da escala sin
-- necesidad de mas materiales.
local function junta_panel(dx, dy, dz, alto, ancho)
  local v = redondear(vuelta(dx, dz) * ancho)
  local vv = vuelta(dx, dz) * ancho
  return (dy % alto == 0) or (abs(vv - v) < 0.06)
end


--[[============================================================================
   SECCION 7  ·  COMPONENTES
============================================================================]]

local Y_PLAZA    = 0
local Y_BASE     = PLAZA and (PLAZA_GRADAS + 1) or 1
local ALTO_FUSTE = PLANTAS * ALTO_PLANTA
local Y_CINTURON = Y_BASE + redondear(ALTO_FUSTE * 0.52)
local Y_TAPA     = Y_BASE + ALTO_FUSTE

--[[
  ⚠ EL ENCUENTRO FUSTE-CUPULA, CALCULADO UNA VEZ.

  En la v1 la cupula flotaba: el hombro del fuste cerraba a un radio y la cupula
  arrancaba en otro, y quedaba un anillo de vacio entre las dos. Ahora las dos
  leen de aqui, asi que se tocan por construccion y no por suerte.
]]
local R_HOMBRO   = max(4, RADIO - 3)
local R_CUPULA   = R_HOMBRO
local Y_CUPULA   = Y_TAPA
local Y_CORONA   = Y_CUPULA + redondear(R_CUPULA * 0.72) + 2

local function y_de_planta(i) return Y_BASE + i * ALTO_PLANTA end

-- El perfil de la Poke Ball. De esta sola funcion sale la silueta entera.
local function perfil_fuste(dy)
  local h = dy - Y_BASE
  if h < 0 then return RADIO end
  local t = h / max(1, ALTO_FUSTE)
  if t < 0.06 then
    return RADIO + redondear(mezcla(2, 0, suave(t / 0.06)))
  end
  if t < 0.88 then return RADIO end
  -- Cierra hasta R_HOMBRO exactamente, que es donde empieza la cupula.
  return redondear(mezcla(RADIO, R_HOMBRO, suave((t - 0.88) / 0.12)))
end


-- ---------------------------------------------------------------- 7.1 PLAZA

local function construir_plaza()
  if not PLAZA then return end
  local r0 = RADIO + PLAZA_ANCHO

  for g = 0, PLAZA_GRADAS do
    local r = r0 - g * 2
    local dy = Y_PLAZA + g
    disco_fn(dy, r, nil, function(dx, dz, d)
      local b = MAT.plaza
      if (floor(dx / 3) + floor(dz / 3)) % 2 == 0 then b = MAT.plaza2 end
      if d > r - 0.5 then b = MAT.borde end
      -- Un aro de luz en el canto de cada grada. Es lo que dibuja la plaza de
      -- noche en vez de dejarla como una mancha gris.
      if d > r - 1.5 and d <= r - 0.5 and hay_neon(dx, dy, dz) then
        return neon_de(g / max(1, PLAZA_GRADAS))
      end
      return desgastar(dx, dy, dz, b, MAT.plaza2)
    end)
  end

  -- Las cuatro escalinatas.
  for lado = 0, 3 do
    local a = lado / 4 * TAU
    local ux, uz = cos(a), sin(a)
    for g = 0, PLAZA_GRADAS do
      local r = r0 - g * 2
      for w = -3, 3 do
        local px = redondear(ux * (r + 1) - uz * w)
        local pz = redondear(uz * (r + 1) + ux * w)
        pon(px, Y_PLAZA + g, pz, MAT.borde)
        if abs(w) == 3 then pon(px, Y_PLAZA + g + 1, pz, MAT.linea) end
      end
    end
  end

  -- Las dos plataformas de duelo del frente, con emblema de Poke Ball.
  for lado = -1, 1, 2 do
    local px = redondear(r0 - 6)
    local pz = lado * redondear(RADIO * 0.55)
    local yy = Y_PLAZA + PLAZA_GRADAS + 1
    for dx = -5, 5 do
      for dz = -5, 5 do
        local d = dist2(dx, dz)
        if d <= 5.5 then
          local b = MAT.borde
          if d <= 4.5 then b = MAT.plaza end
          if d <= 1.3 then b = M_NEON_6
          elseif d <= 2.1 then b = MAT.cinturon
          elseif d <= 4.3 and dz < 0 then b = M_NEON_1
          elseif d <= 4.3 then b = MAT.plaza2 end
          pon(px + dx, yy, pz + dz, b)
        end
      end
    end
    -- Cuatro balizas alrededor de cada plataforma.
    for i = 0, 3 do
      local a = i / 4 * TAU + pi / 4
      linea_y(px + redondear(cos(a) * 6), yy + 1, pz + redondear(sin(a) * 6),
              yy + 4, MAT.linea)
      pon(px + redondear(cos(a) * 6), yy + 5, pz + redondear(sin(a) * 6), M_NEON_1)
    end
  end
end


-- --------------------------------------------------- 7.2 PASARELAS Y APOYOS

--[[
  Pasarelas exteriores: los balcones que rodean la torre a la altura de cada
  forjado.

  En la referencia son las cornisas que separan las plantas. En juego valen
  para asomarse y para dar la vuelta por fuera sin pasar por dentro, que en un
  sitio de PvP cambia como se juega.
]]
local function construir_pasarelas()
  if not PASARELAS then return end
  for i = 1, PLANTAS - 1 do
    local yy = y_de_planta(i)
    local r = perfil_fuste(yy) + 3
    anillo_fn(yy, r, 4, function(dx, dz, d)
      if d > r - 1 then return MAT.borde end
      return MAT.estructura
    end)
    -- Barandilla y su linea de luz.
    anillo(yy + 1, r, 1, MAT.borde)
    anillo_fn(yy + 2, r, 1, function(dx, dz, d)
      if hay_neon(dx, yy, dz) then return neon_planta(i) end
      return nil
    end)
    -- Y por debajo, la cornisa vuela un poco menos: da sombra y peso.
    anillo(yy - 1, r - 1, 3, MAT.estructura)
  end
end

--[[
  Contrafuertes: los nervios que bajan del cinturon a la plaza.

  Sin ellos, un cilindro de 85 metros apoyado en un disco se ve inestable. Con
  ellos, la carga "se ve" bajar -- que es de lo que va la arquitectura.
]]
local function construir_contrafuertes()
  if not CONTRAFUERTE then return end
  local n = 8
  for i = 0, n - 1 do
    local a = i / n * TAU
    local ux, uz = cos(a), sin(a)
    local y_alto = Y_CINTURON - 2
    for dy = Y_BASE, y_alto do
      local t = (dy - Y_BASE) / max(1, y_alto - Y_BASE)
      -- Se separa del fuste segun baja: es lo que le da el perfil de arbotante.
      local sep = mezcla(RADIO + 5, RADIO + 1, suave_sal(t))
      local px, pz = redondear(ux * sep), redondear(uz * sep)
      for w = -1, 1 do
        pon(px + redondear(-uz * w), dy, pz + redondear(ux * w),
            desgastar(px, dy, pz, MAT.estructura, MAT.muro))
      end
      if dy % 6 == 0 and hay_neon(px, dy, pz) then
        pon(px, dy, pz, neon_de(t))
      end
    end
  end
end


-- ------------------------------------------------------------- 7.3 EL FUSTE

local function construir_fuste()
  cilindro_perfil(Y_BASE, Y_TAPA, 2, perfil_fuste,
    function(dx, dy, dz, d, r)
      local h = dy - Y_BASE
      local planta = floor(h / ALTO_PLANTA)
      local dentro = h % ALTO_PLANTA
      local t_torre = h / max(1, ALTO_FUSTE)

      -- Forjado y remate de planta: macizos, es donde apoya el piso de arriba.
      if dentro <= 1 or dentro >= ALTO_PLANTA - 1 then
        if hay_neon(dx, dy, dz) and dentro == 0 then
          return neon_planta(planta)
        end
        return bandear(dy, 1, MAT.estructura, MAT.borde)
      end

      -- Costillas verticales: estructura, y ademas separan las cristaleras en
      -- panos, que es lo que las hace legibles.
      if costilla(dx, dz, 16, 0.15) then
        -- Con su propia linea de luz encastrada, que es lo que da el aspecto
        -- de nave y no de castillo.
        if abs((vuelta(dx, dz) * 16) % 1 - 0.5) < 0.02 and hay_neon(dx, dy, dz) then
          return MAT.linea
        end
        return desgastar(dx, dy, dz, MAT.estructura, MAT.muro)
      end

      -- La franja central es cristalera; arriba y abajo, antepecho y dintel.
      local franja = dentro / ALTO_PLANTA
      if franja > 0.20 and franja < 0.84 then
        if escaner(dy, 4) then return MAT.cristal3 end
        if azar(dx, dy, dz) < VENTANAS then return cristal_planta(planta) end
        return MAT.cristal
      end

      if hay_circuito(dx, dy, dz) then return MAT.linea end
      return desgastar(dx, dy, dz, MAT.muro, MAT.estructura)
    end)

  -- Tiras de neon verticales entre costilla y costilla, de arriba abajo.
  for i = 0, 15 do
    local a = (i + 0.5) / 16 * TAU
    for dy = Y_BASE + 2, Y_TAPA - 2 do
      local r = perfil_fuste(dy)
      if dy % 9 ~= 0 and hay_neon(i, dy, 0) then
        pon(redondear(cos(a) * r), dy, redondear(sin(a) * r),
            neon_de((dy - Y_BASE) / max(1, ALTO_FUSTE)))
      end
    end
  end
end


-- ------------------------------------------------------- 7.4 EL CINTURON

local function construir_cinturon()
  local y0 = Y_CINTURON - floor(CINTURON / 2)
  local y1 = y0 + CINTURON - 1

  for dy = y0, y1 do
    local r = perfil_fuste(dy) + 2
    if dy == y0 or dy == y1 then r = r - 1 end
    anillo_fn(dy, r, 3, function(dx, dz, d)
      if (dy == y0 or dy == y1) and hay_neon(dx, dy, dz) then return MAT.linea end
      return desgastar(dx, dy, dz, MAT.cinturon, MAT.borde)
    end)
  end

  -- Los dos filos encendidos.
  anillo(y0 - 1, perfil_fuste(y0) + 1, 1, M_NEON_1)
  anillo(y1 + 1, perfil_fuste(y1) + 1, 1, M_NEON_1)

  if BOTON then
    local rb = max(3, redondear(RADIO * 0.22))
    local cy, r = Y_CINTURON, perfil_fuste(Y_CINTURON) + 2
    for dy = -rb, rb do
      for dz = -rb, rb do
        local d = dist2(dy, dz)
        if d <= rb + 0.5 then
          local prof = redondear(mezcla(3, 0, d / (rb + 0.5)))
          for p = 0, prof do
            local b = MAT.borde
            if d <= rb * 0.4 then b = MAT.ojos
            elseif d <= rb * 0.7 then b = M_NEON_6
            elseif d <= rb * 0.9 then b = MAT.linea end
            pon(redondear(r + p), cy + dy, dz, b)
          end
        end
      end
    end
  end

  --[[
    El marcador de ranking: una retícula de luces en el cinturon, detras del
    boton.

    Es donde se anunciara quien manda en la torre. Se deja construido y
    encendido; el contenido lo pondra el mod cuando exista el sistema de rangos.
  ]]
  if MARCADOR then
    local r = perfil_fuste(Y_CINTURON) + 2
    for fila = -1, 1 do
      for col = -6, 6 do
        local yy = Y_CINTURON + fila * 2
        local a = col * 0.055
        local px = redondear(cos(a) * r)
        local pz = redondear(sin(a) * r)
        if abs(col) > 2 then
          pon(px, yy, pz, (fila == 0) and M_NEON_3 or MAT.linea)
        end
      end
    end
  end
end


-- --------------------------------------------------------- 7.5 LA CUPULA

local function construir_cupula()
  if not CUPULA then return end

  -- El anillo de encuentro. Cose la cupula al fuste: es lo que evita el vacio
  -- que se vio en la v1.
  anillo(Y_CUPULA, R_CUPULA + 1, 3, MAT.borde)
  anillo(Y_CUPULA - 1, R_CUPULA + 2, 3, MAT.estructura)

  media_esfera(Y_CUPULA, R_CUPULA, 0.72, 2, true, function(dx, dy, dz, n)
    if costilla(dx, dz, 16, 0.12) then
      if hay_neon(dx, dy, dz) and dy % 3 == 0 then return MAT.linea end
      return desgastar(dx, dy, dz, MAT.estructura, MAT.muro)
    end
    if dy % 5 == 0 then return MAT.borde end
    if escaner(dy, 3) then return MAT.cristal2 end
    return MAT.cristal
  end)

  -- El oculo y su corona de luz.
  local cima = Y_CUPULA + redondear(R_CUPULA * 0.72)
  disco(cima, max(2, redondear(RADIO * 0.16)), MAT.ojos, nil)
  anillo(cima - 1, max(3, redondear(RADIO * 0.22)), 1, M_NEON_1)
  anillo(cima - 2, max(4, redondear(RADIO * 0.28)), 1, MAT.linea)
end


-- ----------------------------------------------------------- 7.6 INTERIOR

local function forjado(i)
  local yy = y_de_planta(i)
  local r = perfil_fuste(yy) - 2
  local color = neon_planta(i)

  disco_fn(yy, r, nil, function(dx, dz, d)
    local b = MAT.suelo
    if d > r * 0.93 then b = MAT.borde
    elseif d > r * 0.34 and d < r * 0.40 then b = color
    elseif d < r * 0.14 then b = MAT.estructura end
    -- Suelo de cristal en dos gajos opuestos: se ve la planta de abajo, y eso
    -- en un sitio de espectaculo es media gracia.
    if SUELO_CRISTAL and d > r * 0.45 and d < r * 0.88 then
      local v = vuelta(dx, dz)
      if (v > 0.10 and v < 0.35) or (v > 0.60 and v < 0.85) then
        return MAT.cristal3
      end
    end
    return desgastar(dx, yy, dz, b, MAT.estructura)
  end)

  if BARANDILLAS then
    anillo(yy + 1, r, 1, MAT.borde)
    for p = 0, 47 do
      local a = p / 48 * TAU
      pon(redondear(cos(a) * (r - 0.5)), yy + 2, redondear(sin(a) * (r - 0.5)),
          hay_neon(p, yy, 0) and color or MAT.linea)
    end
  end

  -- Gradas de publico: dos medias lunas escalonadas contra el muro.
  if GRADAS then
    for grada = 0, 2 do
      local rg = r - 2 - grada
      anillo_fn(yy + 1 + grada, rg, 2, function(dx, dz, d)
        local v = vuelta(dx, dz)
        if (v > 0.06 and v < 0.44) or (v > 0.56 and v < 0.94) then
          return MAT.borde
        end
        return nil
      end)
    end
  end
end

--[[
  El nucleo de energia: el eje por el que se sube y la columna de luz que se ve
  desde fuera por las cristaleras.

  Va hueco y con su propia luz. En juego es la unica ruta entre plantas, y eso
  da a los combates un cuello de botella claro.
]]
local function construir_nucleo()
  if not NUCLEO then return end
  local rn = max(3, redondear(RADIO * 0.16))

  cilindro(Y_BASE, Y_TAPA, rn, 1, MAT.estructura)
  for dy = Y_BASE + 1, Y_TAPA - 1 do
    disco(dy, rn - 1, MAT.aire, nil)
    if dy % 4 == 0 then
      anillo(dy, rn - 1, 1, neon_de((dy - Y_BASE) / max(1, ALTO_FUSTE)))
    end
  end
  -- La columna central encendida, de suelo a cupula.
  linea_y(0, Y_BASE, 0, Y_TAPA + 2, MAT.nucleo)
  for dy = Y_BASE, Y_TAPA, 3 do
    for i = 0, 3 do
      local a = i / 4 * TAU + dy * 0.3
      pon(redondear(cos(a) * 2), dy, redondear(sin(a) * 2), MAT.ojos)
    end
  end
end

local function construir_escaleras()
  if not ESCALERAS then return end
  local rn = max(3, redondear(RADIO * 0.16)) + 2
  helice(Y_BASE + 1, Y_TAPA - 2,
    function() return rn end,
    PLANTAS * 1.5, 3,
    function(t, g)
      if g == 2 then return hay_neon(g, redondear(t * 100), 0) and MAT.linea or MAT.borde end
      return MAT.borde
    end)
end

local function construir_portales()
  if not PORTALES then return end
  for i = 0, PLANTAS - 1 do
    local yy = y_de_planta(i) + 1
    local r = perfil_fuste(yy)
    local color = neon_planta(i)
    for lado = 0, 1 do
      local a = lado * pi
      local ux, uz = cos(a), sin(a)
      local cx, cz = redondear(ux * r), redondear(uz * r)
      local rad = max(3, redondear(ALTO_PLANTA * 0.34))

      for h = 0, rad do
        for w = -rad, rad do
          if dist2(w, h) <= rad then
            local px = cx + redondear(-uz * w)
            local pz = cz + redondear(ux * w)
            for p = -3, 3 do
              vaciar(px + redondear(ux * p), yy + h, pz + redondear(uz * p))
            end
          end
        end
      end
      arco(cx, yy, cz, rad + 1, -uz, ux, 2, MAT.estructura)
      arco(cx, yy, cz, rad, -uz, ux, 1, color)
      for w = -1, 1, 2 do
        linea_y(cx + redondear(-uz * (rad + 1) * w), yy,
                cz + redondear(ux * (rad + 1) * w), yy + 1, MAT.linea)
      end
    end
  end
end

local function construir_interior()
  if not INTERIOR then return end
  for dy = Y_BASE + 1, Y_TAPA - 1 do
    disco(dy, perfil_fuste(dy) - 2, MAT.aire, nil)
  end
  for i = 0, PLANTAS - 1 do forjado(i) end
  construir_nucleo()
  construir_escaleras()
  construir_portales()
end


-- --------------------------------------------------------------- 7.7 ALAS

--[[
  Las alas, ahora con MEMBRANA.

  En la v1 eran curvas de un bloque y de lejos se veian como puntos sueltos.
  Un ala no es un contorno: es una superficie tirante entre nervios. Se dibujan
  los nervios y se rellena entre uno y el siguiente.
]]
local function construir_alas()
  if not ALAS then return end
  local cy = Y_CINTURON + redondear(RADIO * 0.25)

  for lado = -1, 1, 2 do
    -- Se calculan TODOS los nervios primero, para poder coser entre pares.
    local nervios = {}
    for p = 0, ALAS_PLUMAS - 1 do
      local t = p / max(1, ALAS_PLUMAS - 1)
      local largo = ALAS_LARGO * mezcla(1.0, 0.55, t)
      local subida = mezcla(RADIO * 0.9, -RADIO * 0.35, t)
      nervios[p] = {
        { lado * (RADIO + 1), cy, 0 },
        { lado * (RADIO + largo * 0.45), cy + subida * 1.15, -largo * 0.30 },
        { lado * (RADIO + largo), cy + subida * 0.35, -largo * 0.62 },
        largo
      }
    end

    -- La piel entre nervio y nervio.
    for p = 0, ALAS_PLUMAS - 2 do
      local A, B = nervios[p], nervios[p + 1]
      membrana(A[1], A[2], A[3], B[1], B[2], B[3],
        redondear(A[4] * 2.0),
        function(t) return mezcla(1.0, 0.82, t) end,
        function(t, s)
          -- El borde de salida se deshilacha: un ala con el canto recto parece
          -- una vela.
          if s > 0.90 and azar(redondear(t * 200), p, redondear(s * 50)) < 0.45 then
            return nil
          end
          if t > 0.80 and hay_neon(p, redondear(t * 100), redondear(s * 100)) then
            return MAT.membrana
          end
          return MAT.ala
        end)
    end

    -- Y los nervios encima, con su filo encendido.
    for p = 0, ALAS_PLUMAS - 1 do
      local N = nervios[p]
      bezier(N[1], N[2], N[3], redondear(N[4] * 2.2), function(t)
        return (t > 0.84) and neon_de(1 - t) or MAT.ala
      end)
      bezier({ N[1][1], N[1][2] + 1, N[1][3] },
             { N[2][1], N[2][2] + 1, N[2][3] },
             { N[3][1], N[3][2] + 1, N[3][3] },
             redondear(N[4] * 2.2), function(t)
        if t < 0.25 then return nil end
        return hay_neon(p, redondear(t * 100), 0) and MAT.linea or nil
      end)
    end
  end
end


-- ------------------------------------------------------------- 7.8 CORONA

local function construir_corona()
  if not CORONA then return end
  local rc = max(6, redondear(RADIO * 0.55))
  local cy = Y_CORONA

  media_esfera(cy, rc, 0.95, 3, true, function(dx, dy, dz, n)
    if costilla(dx, dz, 10, 0.14) then return MAT.estructura end
    if dy % 4 == 0 and hay_neon(dx, dy, dz) then return MAT.linea end
    return desgastar(dx, dy, dz, MAT.ala, MAT.estructura)
  end)
  disco(cy, rc, MAT.ala, nil)
  anillo(cy, rc + 1, 2, MAT.borde)
  anillo(cy - 1, rc + 1, 1, M_NEON_5)

  -- Los dos cuernos que cierran el creciente.
  for lado = -1, 1, 2 do
    local largo = rc * 1.9
    local p0 = { lado * rc * 0.72, cy + rc * 0.25, 0 }
    local p1 = { lado * rc * 1.35, cy + largo * 0.72, 0 }
    local p2 = { lado * rc * 0.42, cy + largo * 1.18, 0 }
    for g = -2, 2 do
      bezier({ p0[1], p0[2], p0[3] + g }, { p1[1], p1[2], p1[3] + g },
             { p2[1], p2[2], p2[3] + g }, redondear(largo * 2.4),
             function(t) return (t > 0.80) and MAT.luna or MAT.ala end)
    end
    bezier({ p0[1] + lado, p0[2], p0[3] }, { p1[1] + lado, p1[2], p1[3] },
           { p2[1] + lado, p2[2], p2[3] }, redondear(largo * 2.4),
           function(t) return (t > 0.35) and M_NEON_1 or nil end)
  end

  -- Los ojos: dos, encendidos, al frente. Es lo que hace que la torre "mire".
  for lado = -1, 1, 2 do
    local ex = lado * redondear(rc * 0.42)
    local ey = cy + redondear(rc * 0.46)
    for dx = -2, 2 do
      for dy = -1, 1 do
        if abs(dx) + abs(dy) <= 2 then
          pon(ex + dx, ey + dy, redondear(rc * 0.86), MAT.ojos)
          pon(ex + dx, ey + dy, redondear(rc * 0.86) + 1, M_NEON_1)
        end
      end
    end
  end
end


-- ------------------------------------------------- 7.9 ANILLOS Y ANTENA

--[[
  Anillos flotando alrededor de la corona.

  Es puro efecto, y es lo que separa "torre" de "torre de un mundo con
  tecnologia lunar". Se ladean distinto cada uno para que no parezcan platos
  apilados.
]]
local function construir_anillos()
  if ANILLOS <= 0 then return end
  local cy = Y_CORONA + redondear(RADIO * 0.30)
  for i = 1, ANILLOS do
    local t = i / ANILLOS
    local r = RADIO * mezcla(1.35, 2.10, t)
    local incl = mezcla(0.18, -0.26, t)
    toro(cy + redondear(mezcla(-4, 14, t)), r, 1, incl, function(a)
      -- Un tramo apagado por vuelta: sugiere rotacion aunque no se mueva.
      if a > 0.42 and a < 0.58 then return MAT.borde end
      return neon_de(t)
    end)
  end
end

--[[
  La antena-faro del remate.

  Cierra la silueta hacia arriba. Sin ella la corona termina en seco, y una
  torre asi pide un punto final.
]]
local function construir_antena()
  if not ANTENA then return end
  local base = Y_CORONA + redondear(RADIO * 0.55)
  local alto = redondear(RADIO * 1.1)
  for dy = 0, alto do
    local t = dy / alto
    local r = max(0, redondear(mezcla(3, 0, suave_ent(t))))
    if r <= 0 then
      pon(0, base + dy, 0, MAT.nucleo)
    else
      anillo_fn(base + dy, r, 1, function(dx, dz, d)
        if dy % 4 == 0 then return neon_de(t) end
        return MAT.estructura
      end)
    end
  end
  -- El faro de la punta.
  esfera(0, base + alto + 3, 0, 2, MAT.ojos, nil)
  anillo(base + alto + 3, 4, 1, M_NEON_1)
end


-- ---------------------------------------------------------------- 7.10 LUNA

local function construir_luna()
  if not LUNA_FONDO then return end
  local cy = Y_CORONA + redondear(LUNA_RADIO * 0.55)
  local cz = -redondear(LUNA_RADIO * 1.25)
  esfera(0, cy, cz, LUNA_RADIO, MAT.luna, 2)
  for i = 1, 16 do
    local a = azar(i, 1, 1) * TAU
    local b = azar(i, 2, 2) * pi - pi / 2
    local rr = 2 + floor(azar(i, 3, 3) * 4)
    esfera(redondear(cos(b) * cos(a) * LUNA_RADIO),
           cy + redondear(sin(b) * LUNA_RADIO),
           cz + redondear(cos(b) * sin(a) * LUNA_RADIO),
           rr, MAT.borde, 1)
  end
end


--[[============================================================================
   SECCION 8  ·  PASADAS DE DETALLE
   ----------------------------------------------------------------------------
   Se hacen AL FINAL a proposito: cualquier componente que se ejecute despues
   pisaria estos remates, y son justo lo que hace que la torre no parezca salida
   de un generador.
============================================================================]]

--[[
  Cornisas iluminadas: una linea de luz bajo cada vuelo.

  Es el truco mas barato que existe para que un edificio nocturno se lea. La luz
  no va en el canto sino UN BLOQUE POR DEBAJO Y HACIA DENTRO, que es como se
  ilumina una cornisa de verdad: se ve el resplandor, no la bombilla.
]]
local function detalle_cornisas()
  for i = 1, PLANTAS - 1 do
    local yy = y_de_planta(i)
    local r = perfil_fuste(yy) + 2
    anillo_fn(yy - 2, r, 1, function(dx, dz, d)
      if hay_neon(dx, yy, dz) then return neon_planta(i) end
      return nil
    end)
  end
end

-- Balizas verticales en el arranque del fuste. Marcan la puerta de noche.
local function detalle_balizas()
  for i = 0, 7 do
    local a = i / 8 * TAU + pi / 8
    local r = RADIO + 3
    local px, pz = redondear(cos(a) * r), redondear(sin(a) * r)
    linea_y(px, Y_BASE, pz, Y_BASE + 6, MAT.estructura)
    pon(px, Y_BASE + 7, pz, M_NEON_1)
    pon(px, Y_BASE + 8, pz, MAT.linea)
  end
end

local function pasadas_de_detalle()
  if not DETALLE then return end
  detalle_cornisas()
  detalle_balizas()
end


--[[============================================================================
   SECCION 9  ·  ENSAMBLAJE
   ----------------------------------------------------------------------------
   El orden NO es arbitrario: lo estructural primero, lo que vacia despues, lo
   que sobresale a continuacion, y el detalle al final para que nada lo pise.
============================================================================]]

construir_plaza()
construir_fuste()
construir_interior()        -- vacia y amuebla: despues del fuste
construir_cinturon()        -- sobresale: despues de vaciar
construir_cupula()
construir_pasarelas()
construir_contrafuertes()
construir_alas()
construir_corona()
construir_anillos()
construir_antena()
construir_luna()
pasadas_de_detalle()        -- siempre el ultimo

-- El pincel tiene que devolver algo. `nil` deja intacto el bloque que
-- senalaste: todo lo ha colocado `setBlock`. (Es valido; lo usan los propios
-- scripts de ejemplo de Axiom.)
return nil
