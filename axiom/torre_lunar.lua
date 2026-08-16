$once$
--[[===========================================================================
  TORRE DE BATALLA LUNAR  ·  PokeReport: Luna Eternal
  Lua Script Brush de Axiom

  COMO SE USA
    1. Herramientas -> Lua Script Brush
    2. Pega este fichero entero en el cuadro de codigo
    3. Clic derecho en el suelo donde quieras el CENTRO de la torre
    4. La torre crece desde ahi. Un solo clic.

  `$once$` (arriba del todo) es lo que hace que no sea un pincel: el
  script se ejecuta UNA vez en el punto que senalas, en vez de una vez
  por cada bloque que toque la brocha. Sin esa linea, esto construiria
  una torre por bloque pintado.

  COMO ESTA HECHO
    Todo es funcion de la posicion. No hay "coloca esta pieza aqui": hay
    reglas --octogono, anillo, espiral, creciente-- y cada posicion del
    volumen pregunta si le toca. Cambiar CFG.radio recoloca la torre
    entera sin descuadrar nada, que es lo que permite iterar deprisa.

  QUE PUEDES TOCAR SIN ROMPER NADA
    Solo la tabla CFG de aqui abajo. Lo demas se deduce de ella.
===========================================================================]]

local CFG = {
  plantas       = 6,     -- pisos de arena. Cada uno es un combate
  radio         = 22,    -- radio de la planta baja, en bloques
  altura_planta = 13,    -- alto de cada piso. Menos de 10 agobia al pelear
  luna          = 40,    -- diametro de la luna creciente de arriba
  vueltas_cinta = 2.5,   -- vueltas que da la espiral de luz
  hueca         = true,  -- false = macizo (util para ver la silueta)
}

--[[--------------------------------------------------------------------------
  LA PALETA

  Agrupada por PAPEL y no por nombre: el generador dice PIEDRA y no
  "polished_deepslate", asi que cambiar el aspecto de la torre entera es
  tocar esta tabla. Los nombres de Minecraft solo viven aqui.

  `blocks.x` da el id de minecraft:x. Para otros mods, blocks["mod:id"].
  `withBlockProperty` fija propiedades -- en nuestros neones, la luz.
--------------------------------------------------------------------------]]

--[[
  Busca un bloque por id, con respaldo si no existe.

  ⚠️ ES EL UNICO SITIO DEL SCRIPT QUE PUEDE FALLAR POR ALGO EXTERNO. El
  tooltip de Axiom documenta `blocks.stone` para los de Minecraft, pero
  no dice como se piden los de OTRO MOD. Se prueban las dos formas
  razonables y, si ninguna da nada, se usa un bloque de vanilla parecido.

  Asi, si los neones no se pueden nombrar desde aqui, la torre SE
  CONSTRUYE IGUAL en piedra y cristal en vez de reventar a medias y
  dejarte medio edificio. Y se nota al mirarla, que es la forma de
  enterarse.
]]
local function bloque(id, respaldo)
  local ok, b = pcall(function() return blocks[id] end)
  if ok and b then return b end
  local corto = id:match("^[^:]+:(.+)$") or id
  ok, b = pcall(function() return blocks[corto] end)
  if ok and b then return b end
  return respaldo
end

local function neon(color, forma, luz)
  local id = "lunaneon:neon_" .. color
  if forma then id = id .. "_" .. forma end
  -- Respaldo por color, para que la silueta siga leyendose sin el mod.
  local respaldos = {
    blanco = blocks.white_stained_glass,  cian = blocks.light_blue_stained_glass,
    azul   = blocks.blue_stained_glass,   verde = blocks.lime_stained_glass,
    amarillo = blocks.yellow_stained_glass, naranja = blocks.orange_stained_glass,
    rojo   = blocks.red_stained_glass,    magenta = blocks.magenta_stained_glass,
  }
  local b = bloque(id, respaldos[color] or blocks.glowstone)
  local ok, conluz = pcall(function()
    return withBlockProperty(b, "luz=" .. luz)
  end)
  return (ok and conluz) or b
end

local P = {
  -- El cuerpo: gris oscuro moderno, nada de mazmorra medieval.
  piedra      = blocks.polished_deepslate,
  muro        = blocks.deepslate_bricks,
  suelo       = blocks.polished_deepslate,
  negro       = blocks.polished_blackstone,
  negro_lad   = blocks.polished_blackstone_bricks,
  alero       = blocks.polished_blackstone,
  alero_losa  = withBlockProperty(blocks.polished_blackstone_slab, "type=bottom"),

  -- El detalle calido, que es lo que evita que parezca un bloque de
  -- hormigon. El cobre oxidado da el punto verdoso de la referencia.
  cobre       = blocks.oxidized_cut_copper,
  cobre_vivo  = blocks.cut_copper,

  ventana     = blocks.orange_stained_glass,
  marco       = blocks.polished_blackstone,

  -- ---- Los neones propios (D-029). Lo que hace que sea NUESTRA torre.
  --
  -- luz=0 se ve encendido y no ilumina; luz=1 suelta 7; luz=2 suelta 15.
  -- Se usa a conciencia: si todo iluminase a tope no habria contraste y
  -- la torre perderia justo lo que la hace nocturna.
  luna        = neon("blanco", nil, 2),
  luna_borde  = neon("cian",   nil, 1),
  cinta       = neon("blanco", nil, 1),
  cinta_alma  = neon("cian",   nil, 2),
  pilar       = neon("cian",  "pilar", 2),
  pilar_suave = neon("azul",  "pilar", 1),
  tubo        = neon("azul",  "tubo",  1),
}

-- El color de cada planta. Sube de frio a caliente segun se asciende: es
-- la lectura natural de "esto va a mas", y desde fuera se ve por las
-- ventanas en que piso hay pelea.
local COLOR_PLANTA = {
  neon("azul",     nil, 1),
  neon("cian",     nil, 1),
  neon("verde",    nil, 1),
  neon("amarillo", nil, 1),
  neon("naranja",  nil, 1),
  neon("rojo",     nil, 1),
  neon("magenta",  nil, 1),
  neon("blanco",   nil, 2),
}

--[[--------------------------------------------------------------------------
  GEOMETRIA

  Un circulo puro en Minecraft se ve dentado y gasta el doble de bloques
  en las diagonales. El octogono es lo que usan las pagodas de verdad,
  se construye limpio, y da las ocho caras planas donde caben las
  ventanas.
--------------------------------------------------------------------------]]

local floor, abs, max, min = math.floor, math.abs, math.max, math.min
local cos, sin, sqrt, pi = math.cos, math.sin, math.sqrt, math.pi

local function octogono(radio, x, z)
  local ax, az = abs(x), abs(z)
  if ax < az then ax, az = az, ax end
  -- 0.4142 = tan(22.5 grados), el corte de la diagonal del octogono.
  return max(ax, (ax + az) * 0.4142 + az * 0.2929) / max(radio, 0.001)
end

local function dentro(radio, x, z)   return octogono(radio, x, z) <= 1.0 end

local function anillo(radio, grosor, x, z)
  local d = octogono(radio, x, z)
  return d >= 1.0 - grosor / max(radio, 1) and d <= 1.0
end

-- El estrechamiento de cada planta. NO es lineal a proposito: las de
-- arriba encogen menos, y eso es lo que da silueta de aguja en vez de
-- cono.
local RADIOS = {}
for i = 0, CFG.plantas - 1 do
  RADIOS[i] = max(7, floor(CFG.radio * (0.72 ^ (i * 0.62)) + 0.5))
end

-- El origen: donde has hecho clic. `y` es el suelo de la torre.
local ox, oy, oz = x, y, z

local function poner(dx, dy, dz, bloque)
  if bloque then setBlock(ox + dx, oy + dy, oz + dz, bloque) end
end

local function y_de(planta) return 2 + planta * CFG.altura_planta end
local ALTO_CUERPO = 2 + CFG.plantas * CFG.altura_planta

--[[--------------------------------------------------------------------------
  CIMIENTOS

  En la referencia la torre no sale del suelo de golpe: se apoya en algo
  mas ancho. Sin esto parece clavada. Y en juego cumple otra cosa: el
  PvP necesita un borde llano donde caer sin morir.
--------------------------------------------------------------------------]]

local function cimientos()
  local r = CFG.radio + 5
  for capa = 0, 2 do
    local rr = r - capa * 2
    for dx = -rr - 1, rr + 1 do
      for dz = -rr - 1, rr + 1 do
        if dentro(rr, dx, dz) then
          local b = (capa < 2) and P.muro or P.suelo
          if anillo(rr, 1, dx, dz) then b = P.negro_lad end
          poner(dx, capa, dz, b)
        end
      end
    end
  end

  -- Los ocho pilares de luz del pie. Es lo que se ve desde lejos.
  for i = 0, 7 do
    local a = i / 8 * 2 * pi
    local px = floor(cos(a) * (CFG.radio + 2) + 0.5)
    local pz = floor(sin(a) * (CFG.radio + 2) + 0.5)
    for dy = 2, 10 do
      poner(px, dy, pz, (dy < 9) and P.pilar or P.pilar_suave)
    end
    poner(px, 11, pz, P.cobre)
  end
end

--[[--------------------------------------------------------------------------
  UNA PLANTA

  Muro octogonal, arena por dentro, ventanas altas y alero volado.
  El interior se vacia entero salvo el suelo: pelear entre columnas es
  pelear contra el escenario, no contra el otro jugador.
--------------------------------------------------------------------------]]

-- La textura del muro. Una torre de 80 bloques de una sola textura se
-- lee como una pared; alternando pizarra y ladrillo con una banda de
-- cobre a media altura, la vertical se rompe y el ojo encuentra escala.
local function textura_muro(alt, dx, dz)
  if alt == 1 then return P.negro_lad end
  if alt == 6 then return P.cobre end
  if (dx + dz + alt) % 7 == 0 then return P.piedra end
  return P.muro
end

local function ventanas(r, y0, color)
  for cara = 0, 7 do
    local a = cara / 8 * 2 * pi
    for ancho = -1, 1 do
      for alt = 3, 8 do
        local px = floor(cos(a) * r + cos(a + pi / 2) * ancho + 0.5)
        local pz = floor(sin(a) * r + sin(a + pi / 2) * ancho + 0.5)
        if alt == 3 or alt == 8 or abs(ancho) == 1 then
          poner(px, y0 + alt, pz, P.marco)
        else
          poner(px, y0 + alt, pz, P.ventana)
          -- La luz va DETRAS del cristal, no en el cristal: asi el
          -- resplandor sale hacia fuera, como en la referencia.
          local bx = floor(cos(a) * (r - 1) + 0.5)
          local bz = floor(sin(a) * (r - 1) + 0.5)
          poner(bx, y0 + alt, bz, color)
        end
      end
    end
  end
end

-- El tejado volado. Es LA pieza que dice "pagoda": sin ella, un
-- octogono apilado es una chimenea.
local function alero(r, yy)
  for paso = 0, 1 do
    local rr = r + 2 + paso
    for dx = -rr - 1, rr + 1 do
      for dz = -rr - 1, rr + 1 do
        if anillo(rr, 2, dx, dz) then
          poner(dx, yy + paso, dz, (paso == 0) and P.alero or P.alero_losa)
        end
      end
    end
  end
  for i = 0, 7 do
    local a = i / 8 * 2 * pi
    local px = floor(cos(a) * (r + 4) + 0.5)
    local pz = floor(sin(a) * (r + 4) + 0.5)
    poner(px, yy + 1, pz, P.cobre)
    poner(px, yy + 2, pz, P.tubo)
  end
end

local function planta(i)
  local r = RADIOS[i]
  local y0 = y_de(i)
  local y1 = y0 + CFG.altura_planta
  local color = COLOR_PLANTA[(i % #COLOR_PLANTA) + 1]

  -- Suelo de la arena, con un aro del color de la planta marcando el
  -- centro del combate.
  for dx = -r - 1, r + 1 do
    for dz = -r - 1, r + 1 do
      if dentro(r, dx, dz) then
        local d = octogono(r, dx, dz)
        local b = P.suelo
        if d >= 0.30 and d <= 0.36 then b = color
        elseif d < 0.12 then b = P.negro end
        poner(dx, y0, dz, b)
      end
    end
  end

  -- El muro, hueco por dentro.
  for dy = y0 + 1, y1 - 1 do
    for dx = -r - 1, r + 1 do
      for dz = -r - 1, r + 1 do
        if anillo(r, 1, dx, dz) then
          poner(dx, dy, dz, textura_muro(dy - y0, dx, dz))
        elseif not CFG.hueca and dentro(r, dx, dz) then
          poner(dx, dy, dz, P.muro)
        end
      end
    end
  end

  ventanas(r, y0, color)
  alero(r, y1 - 1)
end

--[[--------------------------------------------------------------------------
  AGUJA Y LUNA
--------------------------------------------------------------------------]]

local function aguja()
  for paso = 0, 5 do
    local rr = max(1, 5 - paso)
    for dx = -rr - 1, rr + 1 do
      for dz = -rr - 1, rr + 1 do
        if dentro(rr, dx, dz) then
          poner(dx, ALTO_CUERPO + paso, dz,
                (paso % 2 == 1) and P.negro_lad or P.cobre)
        end
      end
    end
  end
  for paso = 6, 11 do poner(0, ALTO_CUERPO + paso, 0, P.pilar) end
end

--[[
  La luna creciente. Es la firma del sitio.

  Se hace RESTANDO dos circulos: uno grande y otro mas pequeno desplazado
  que le muerde el interior. Es como se dibuja un creciente desde que
  existe el dibujo, y garantiza que las dos puntas salgan simetricas.

  Va VERTICAL, de canto, mirando a quien llega. Tumbada no se recorta
  contra el cielo, que es justo el efecto de la referencia.
]]
local function luna()
  local radio  = floor(CFG.luna / 2)
  local cy     = ALTO_CUERPO + 14 + radio
  local mr     = radio * 0.82     -- radio del circulo que muerde
  local mdx    = radio * 0.42     -- cuanto se desplaza: mas = luna mas fina
  local mdy    = radio * 0.30

  for dy = -radio - 2, radio + 2 do
    for dx = -radio - 2, radio + 2 do
      local fuera  = sqrt(dx * dx + dy * dy)
      local muerde = sqrt((dx - mdx) ^ 2 + (dy - mdy) ^ 2)
      if fuera <= radio and muerde >= mr then
        local borde = (fuera > radio - 1.6) or (muerde < mr + 1.6)
        local b = borde and P.luna_borde or P.luna
        -- Dos bloques de grosor: de canto se ve, y de lejos tiene cuerpo.
        poner(dx, cy + dy, 0, b)
        poner(dx, cy + dy, 1, b)
      end
    end
  end
end

--[[
  La cinta de luz en espiral.

  Es lo que ata las plantas en una sola pieza y lo que hace que la torre
  se reconozca desde el otro lado del mapa. Sube dando CFG.vueltas_cinta
  vueltas y se estrecha con la torre, asi que nunca se despega del muro.
]]
local function cinta()
  local y0, y1 = 8, ALTO_CUERPO + 8
  local pasos = (y1 - y0) * 7      -- de sobra para que no queden huecos
  for paso = 0, pasos do
    local t  = paso / pasos
    local yy = y0 + t * (y1 - y0)
    local a  = t * CFG.vueltas_cinta * 2 * pi

    -- El radio sigue al de la planta en la que esta, mas un respiro.
    local pl = min(CFG.plantas - 1, floor((yy - 2) / CFG.altura_planta))
    if pl < 0 then pl = 0 end
    local r = RADIOS[pl] + 4.5

    for g = -1, 1 do
      local px = floor(cos(a) * (r + g * 0.6) + 0.5)
      local pz = floor(sin(a) * (r + g * 0.6) + 0.5)
      poner(px, floor(yy + 0.5), pz, (g == 0) and P.cinta_alma or P.cinta)
      -- Un poco de cuerpo vertical, o la cinta se ve rota al subir.
      if paso % 3 == 0 then poner(px, floor(yy + 0.5) + 1, pz, P.cinta) end
    end
  end
end

--[[--------------------------------------------------------------------------
  MONTAJE
--------------------------------------------------------------------------]]

cimientos()
for i = 0, CFG.plantas - 1 do planta(i) end
aguja()
luna()
cinta()

-- El pincel tiene que devolver un bloque. Se devuelve el que ya habia,
-- para que el clic en si no cambie nada: todo lo ha colocado setBlock.
return getBlock(x, y, z)
