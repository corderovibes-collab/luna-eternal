--[[ ===========================================================================

  EL PALACIO LUNAR  ·  generador para el Lua Script Brush de Axiom
  PokeReport: Luna Eternal  ·  docs/world/palacio.md

  QUE CONSTRUYE

  Un decagono flotante sobre el vacio de la ciudadela, con la luna suspendida
  encima y DIEZ ALAS radiales, una por region. Cada ala tiene ocho alcobas de
  lider y un estrado de campeon: 90 plataformas, mas la corona del Alto Mando
  y la cripta de los equipos.

  SOLO ESTRUCTURA. No coloca ni un entrenador -- las plataformas se dejan
  construidas, numeradas y VACIAS. Ponerlos es un paso posterior y manual
  (decision del usuario, 2026-08-22).

  ---------------------------------------------------------------------------
  LO QUE SALE, MEDIDO EN EL BANCO DE PRUEBAS

                        bloques      planta        alto
      maqueta           350 624      151 x 157      80
      normal            566 045      215 x 221     121
      epico             865 149      279 x 285     191

  145 plataformas vacias en el tamano normal:

       90   en las alas        8 alcobas + 1 estrado, por diez regiones
       40   en la corona       el Alto Mando, 4 por region
       15   en la cripta       5 bandas x 3

  Diez regiones, en tres estados: dos ACTIVAS (Kanto y Johto), tres LATENTES
  (Hoenn, Sinnoh e Hisui: tienen entrenadores en el datapack RCT pero su
  generacion esta apagada) y cinco RESERVADAS (sin contenido todavia).

  ---------------------------------------------------------------------------
  COMO SE EJECUTA

    1. Ponte donde quieras el CENTRO del palacio. La escalinata sale hacia el
       norte, asi que conviene mirar al norte al ejecutar.
    2. Herramientas -> Lua Script Brush
    3. Pega este fichero entero en la caja de script
    4. Clic

  El `$once$` de la primera linea es lo que hace que se ejecute UNA VEZ en el
  punto donde pinchas, en vez de comportarse como brocha. Sin el, el script se
  ejecutaria una vez POR CADA bloque que toque la brocha y construiria el
  palacio miles de veces encima de si mismo.

  `$ignoreMask$` para que no dependa de que tengas una seleccion activa ni una
  mascara puesta. Con seleccion activa y sin esta linea, `setBlock` fuera de la
  seleccion no hace nada Y NO AVISA: saldria medio palacio.

  ---------------------------------------------------------------------------
  ⚠ SE EJECUTA DESDE EL CLIENTE, CON AXIOM, Y NO POR RCON.

  El Lua Script Brush coloca bloques directamente. La alternativa --emitir
  decenas de miles de `/fill` y mandarlos por consola-- tiene tres problemas
  que aqui no existen: el limite de 32 768 bloques por comando, el tiempo de
  ida y vuelta de cada uno, y que la carga la aguanta el SERVIDOR, que ya usa
  4,34 GiB de 8 con el mundo vacio.

  A cambio, hay dos cosas que este script NO puede hacer y que un comando si:
  invocar entidades y escribir NBT. Por eso los rotulos son letras de bloque
  y no carteles, y por eso los entrenadores se colocan aparte.

  ---------------------------------------------------------------------------
  ⚠ DESHACER ES Ctrl+Z DE AXIOM, y conviene saberlo ANTES de ejecutar.

  Son cientos de miles de bloques. Si el sitio no te convence, deshaz y mueve
  el ancla; reconstruir encima NO limpia lo anterior -- el palacio nuevo se
  mezclaria con el viejo y quedarian restos incrustados dentro de los muros,
  que es de lo mas dificil de limpiar que hay.

  Si prefieres asegurarte, pon `CFG.limpiar_antes = true`: vacia a aire el
  cilindro entero que ocupa el edificio antes de empezar. Cuesta tiempo pero
  garantiza que no hay restos.

  ---------------------------------------------------------------------------
  ⚠ EL CLIENTE HACE EL TRABAJO, NO EL SERVIDOR, y esa es la razon de fondo
  para usar Axiom en vez de mandar /fill por RCON.

  El servidor de desarrollo tiene 8 GB y ya usa 4,34 GiB con el mundo VACIO
  (CLAUDE.md §0). Meterle decenas de miles de comandos de bloque es
  exactamente lo que no aguanta. Axiom construye en tu maquina y sincroniza,
  asi que el coste lo paga tu PC.

  ---------------------------------------------------------------------------
  LA PALETA ES LA DE LA CIUDADELA, y no por casualidad.

  Se usan los 602 bloques propios de `lunaneon` (D-029, D-032). Los 16 colores
  del hormigon y del vidrio SON los 16 del neon rebajados por formula, asi que
  un neon rojo pega con un hormigon rojo por construccion y no porque alguien
  los haya emparejado a ojo.

  Si `lunaneon` no estuviera instalado, cada bloque suyo saldria como aire y
  el palacio seria un esqueleto invisible. Por eso el arranque COMPRUEBA la
  paleta y aborta con un mensaje claro en vez de construir un fantasma.

  ---------------------------------------------------------------------------
  ESTRUCTURA DEL FICHERO

    1. CONFIGURACION      lo unico que se toca normalmente
    2. PALETA             todos los ids de bloque, en un solo sitio
    3. REGIONES           las diez alas: color, estado, angulo
    4. MATEMATICAS        rotaciones, interpolacion, ruido
    5. PRIMITIVAS         caja, disco, anillo, esfera, cupula, arco...
    6. ELEMENTOS          columna, columnata, cornisa, friso, celosia,
                          plataforma de entrenador, faro, arana
    7. PIEZAS
         7.1  basamento          la plataforma decagonal
         7.2  escalinata         subida desde la plaza
         7.3  nucleo             la rotonda y los diez portales
         7.4  cupula             casquete rebajado + linterna
         7.5  luna               la esfera suspendida
         7.6  ala regional       x10: 8 alcobas + estrado + sello
         7.7  cripta             los equipos, bajo el palacio
         7.8  corona             el Alto Mando, sobre la cupula
         7.9  perimetro          contrafuertes y faros
         7.10 rotulos            fuente de bloques 5x7
         7.11 ambulatorio        el anillo que une las diez alas
         7.12 emblemas           uno por region, en el suelo
         7.13 agua y jardineras  solo en la rotonda
         7.14 entreplantas       galeria alta sobre las alcobas
         7.16 fachada exterior   la cara que se ve desde la plaza
         7.17 pinaculos          la silueta contra el cielo
         7.18 vestibulo          portico de entrada con fronton
         7.19 iluminacion        pasada final de repaso
         7.20 acceso a la corona escalera de caracol por el pozo
         7.21 senalizacion       bandas de color que llevan a cada ala
         7.22 torres             diez torreones octogonales
         7.23 arbotantes         atan la cupula al perimetro
         7.24 mosaico            el suelo del ambulatorio, por sectores
         7.25 testeros           las alas vistas desde el ambulatorio
         7.26 paseo              la calzada que une la plaza con el palacio
         7.15 presets            maqueta / normal / epico
    8. COMPROBACIONES     paleta y geometria, ANTES de tocar nada
    9. MAIN               orquesta todo
   10. RECETAS            como se toca esto sin romperlo
   11. BITACORA           los cinco fallos que cazo el banco de pruebas

  ---------------------------------------------------------------------------
  PLANTA, DE UN VISTAZO

              radio       que hay
              0 - 12      pozo de luz, canal de agua y escalera a la corona
             12 - 36      la rotonda
             38 - 87      las diez alas (alcobas y estrado)
             91 - 98      el ambulatorio
            100 - 104     fachada, torres y perimetro

  ALZADO

              y  47 - 62   la cripta
              y  64        la plaza de la ciudadela
              y  74 - 96   la planta noble
              y  96 - 116  la cupula
              y 124        la corona
              y 150        la luna

=========================================================================== ]]

$once$
$ignoreMask$

-- ###########################################################################
-- ##  1. CONFIGURACION                                                     ##
-- ###########################################################################

local CFG = {}

--- Donde se planta. Por defecto, donde pinchaste.
--- El palacio se centra en X/Z y crece hacia arriba desde `y_base`.
CFG.usar_punto_del_raton = true
CFG.cx = 0
CFG.cz = 0

--- Altura del suelo de la PLANTA NOBLE (donde se camina en las alas).
--- La plaza de la ciudadela tiene el suelo en 63 y se camina en 64; el
--- palacio vuela por encima para que se vea entero desde abajo.
CFG.y_base = 74

--- Cuanto del edificio se levanta. Poner a false para iterar mas rapido:
--- juzgar la proporcion del nucleo no necesita las diez alas cada vez.
CFG.hacer = {
  basamento  = true,
  escalinata = true,
  nucleo     = true,
  cupula     = true,
  luna       = true,
  alas       = true,
  corona     = true,
  cripta     = true,
  faros      = true,
  carteles   = true,
  ambulatorio  = true,
  emblemas     = true,
  agua         = true,
  entreplantas = true,
  fachada      = true,
  pinaculos    = true,
  vestibulo      = true,
  iluminacion    = true,
  acceso_corona  = true,
  senalizacion   = true,
  torres         = true,
  arbotantes     = true,
  mosaico        = true,
  testeros       = true,
  paseo          = true,
}

--- Escala: "maqueta", "normal" o "epico". Ver PRESETS en la seccion 7.15.
--- nil deja los valores que haya escritos abajo.
CFG.preset = nil

--- Solo estas alas. Vacio = todas. Ej: {"kanto"} para probar una sola.
CFG.solo_alas = {}

--- Vacia el volumen antes de construir. Lento pero deja el sitio limpio.
CFG.limpiar_antes = false

--- ⚠ Geometria maestra. Cambiar esto cambia TODO, y los numeros no son
--- independientes: las alas arrancan donde acaba el nucleo, y el perimetro
--- donde acaban las alas. Se comprueba al arrancar (`comprobar_geometria`).
--- ⚠ r_nucleo NO ES LIBRE: tiene que dar arco suficiente para las diez alas.
--- El arco disponible por ala es 2*pi*r_nucleo/10, y hace falta al menos
--- ala_ancho + 4 (los dos muros y su trasdos). Con r_nucleo=28 salian 17,6 de
--- arco para 22 necesarios, y las alas se solapaban entre si: se descubrio con
--- `comprobar_geometria()` ANTES de construir, no mirando el edificio.
---     r_nucleo minimo = (ala_ancho + 4) * 10 / (2*pi) = 35,0  ->  36
CFG.r_nucleo    = 36      -- radio de la rotonda central
CFG.r_alas      = 84      -- hasta donde llegan las alas
CFG.r_perimetro = 104     -- cornisa exterior (fuera del ambulatorio)
CFG.ala_ancho   = 18      -- ancho interior de cada galeria
CFG.alto_planta = 22      -- del suelo de las alas al techo
CFG.alto_basa   = 6       -- espesor del basamento bajo el suelo

--- La luna. El elemento que da nombre al servidor: conviene que sea grande.
--- ⚠ Tiene que quedar POR ENCIMA de la corona: luna_y - luna_radio > corona_y.
--- Con luna_y=132 y corona_y=112 la esfera atravesaba los tronos.
CFG.luna_radio = 21
CFG.luna_y     = 150

--- La cripta, bajo el palacio.
CFG.cripta_y     = 48
CFG.cripta_radio = 46
CFG.cripta_alto  = 14

--- La corona del Alto Mando, sobre la cupula.
--- ⚠ Por encima del remate de la cupula (116) o la corta.
CFG.corona_y     = 124
CFG.corona_radio = 34

--- Semilla del ruido, para las vetas del pavimento y el desgaste.
CFG.semilla = 20260822

--- Cuenta de bloques puestos. Se imprime al final; si sale 0 es que la
--- paleta no resolvio y estarias mirando un palacio de aire.
local PUESTOS = 0


-- ###########################################################################
-- ##  2. PALETA                                                            ##
-- ###########################################################################
--
-- ⚠ TODOS LOS IDS VIVEN AQUI Y EN NINGUN OTRO SITIO.
--
-- No es pulcritud: un id mal escrito en Axiom NO da error, devuelve nil y
-- `setBlock` con nil no pone nada. El sintoma es un hueco en un muro, a 60
-- bloques de altura, que nadie relaciona con una errata. Teniendolos todos
-- juntos, `comprobar_paleta()` los valida de una pasada antes de construir.

--- Resuelve un id de bloque tolerando las dos formas de escribirlo.
--- `blocks.stone` funciona para vanilla; para un bloque con namespace hace
--- falta la forma de indice. Se prueban las dos porque no esta documentado
--- cual acepta cada version de Axiom, y fallar aqui en silencio es justo lo
--- que se quiere evitar.
local function B(id)
  local ok, b = pcall(function() return blocks[id] end)
  if ok and b then return b end
  -- Sin namespace explicito, probar como vanilla.
  if not string.find(id, ":") then
    local ok2, b2 = pcall(function() return blocks["minecraft:" .. id] end)
    if ok2 and b2 then return b2 end
  end
  return nil
end

--- Igual que B() pero recordando el fallo, para el informe del arranque.
local FALLOS = {}
local function Bq(id)
  local b = B(id)
  if not b then FALLOS[#FALLOS + 1] = id end
  return b
end

local N = "lunaneon:"

-- Los 16 colores, con el nombre que usa lunaneon.
local COLORES = {
  "blanco", "gris_claro", "gris", "negro",
  "rojo", "naranja", "amarillo", "lima",
  "verde", "cian", "azul_claro", "azul",
  "morado", "magenta", "rosa", "marron",
}

local P = {}   -- la paleta

-- --- Estructura -----------------------------------------------------------
P.muro          = Bq(N .. "hormigon_pulido_blanco")
P.muro_alt      = Bq(N .. "hormigon_panel_gris_claro")
P.muro_oscuro   = Bq(N .. "hormigon_pulido_gris")
P.muro_negro    = Bq(N .. "hormigon_pulido_negro")
P.nervio        = Bq(N .. "hormigon_rayado_blanco")
P.nervio_gris   = Bq(N .. "hormigon_rayado_gris_claro")

-- --- Metal ----------------------------------------------------------------
P.cromo         = Bq(N .. "metal_cromo_liso")
P.cromo_pilar   = Bq(N .. "metal_cromo_liso_pilar")
P.cromo_losa    = Bq(N .. "metal_cromo_liso_losa")
P.cromo_esc     = Bq(N .. "metal_cromo_liso_escalera")
P.titanio       = Bq(N .. "metal_titanio_cepillado")
P.titanio_pilar = Bq(N .. "metal_titanio_cepillado_pilar")
P.titanio_losa  = Bq(N .. "metal_titanio_cepillado_losa")
P.acero_osc     = Bq(N .. "metal_acero_oscuro_estriado")
P.acero_osc_losa= Bq(N .. "metal_acero_oscuro_estriado_losa")
P.acero_osc_esc = Bq(N .. "metal_acero_oscuro_estriado_escalera")
P.acero_osc_pil = Bq(N .. "metal_acero_oscuro_estriado_pilar")
P.grafito       = Bq(N .. "metal_grafito_remachado")
P.grafito_pilar = Bq(N .. "metal_grafito_remachado_pilar")
P.grafito_losa  = Bq(N .. "metal_grafito_remachado_losa")
P.laton         = Bq(N .. "metal_laton_liso")
P.laton_losa    = Bq(N .. "metal_laton_liso_losa")
P.laton_pilar   = Bq(N .. "metal_laton_liso_pilar")

-- --- Suelos ---------------------------------------------------------------
P.suelo         = Bq(N .. "pavimento_losa_grande")
P.suelo_esc     = Bq(N .. "pavimento_losa_grande_escalera")
P.suelo_losa    = Bq(N .. "pavimento_losa_grande_losa")
P.terrazo       = Bq(N .. "pavimento_terrazo_claro")
P.terrazo_osc   = Bq(N .. "pavimento_terrazo_oscuro")
P.terrazo_losa  = Bq(N .. "pavimento_terrazo_claro_losa")
P.asfalto       = Bq(N .. "pavimento_asfalto")
P.asfalto_claro = Bq(N .. "pavimento_asfalto_claro")
P.adoquin       = Bq(N .. "pavimento_adoquin_fino")
P.adoquin_esc   = Bq(N .. "pavimento_adoquin_fino_escalera")

-- --- Rejilla (huecos de verdad, para pasarelas y celosias) ----------------
P.rejilla       = Bq(N .. "rejilla_cromo")
P.rejilla_losa  = Bq(N .. "rejilla_cromo_losa")
P.rejilla_panel = Bq(N .. "rejilla_cromo_panel")
P.rejilla_ti    = Bq(N .. "rejilla_titanio")
P.rejilla_ti_losa = Bq(N .. "rejilla_titanio_losa")

-- --- Vidrio ---------------------------------------------------------------
P.vidrio        = Bq(N .. "vidrio_claro_blanco")
P.vidrio_panel  = Bq(N .. "vidrio_claro_blanco_panel")

-- --- Vanilla de apoyo -----------------------------------------------------
P.aire          = Bq("air")
P.barrera       = Bq("barrier")
P.luz           = Bq("light")
P.agua          = Bq("water")

--- Neon por color y forma. Se construye la tabla entera para poder pedir
--- `P.neon("rojo", "pilar")` sin concatenar ids por ahi sueltos.
local NEON = {}
for _, c in ipairs(COLORES) do
  NEON[c] = {
    bloque   = Bq(N .. "neon_" .. c),
    losa     = Bq(N .. "neon_" .. c .. "_losa"),
    escalera = Bq(N .. "neon_" .. c .. "_escalera"),
    pilar    = Bq(N .. "neon_" .. c .. "_pilar"),
    panel    = Bq(N .. "neon_" .. c .. "_panel"),
    tubo     = Bq(N .. "neon_" .. c .. "_tubo"),
  }
end

--- Hormigon de color, por acabado y forma.
local HORM = {}
for _, c in ipairs(COLORES) do
  HORM[c] = {
    pulido        = Bq(N .. "hormigon_pulido_" .. c),
    pulido_losa   = Bq(N .. "hormigon_pulido_" .. c .. "_losa"),
    pulido_esc    = Bq(N .. "hormigon_pulido_" .. c .. "_escalera"),
    pulido_muro   = Bq(N .. "hormigon_pulido_" .. c .. "_muro"),
    rayado        = Bq(N .. "hormigon_rayado_" .. c),
    rayado_losa   = Bq(N .. "hormigon_rayado_" .. c .. "_losa"),
    panel         = Bq(N .. "hormigon_panel_" .. c),
    panel_losa    = Bq(N .. "hormigon_panel_" .. c .. "_losa"),
    panel_esc     = Bq(N .. "hormigon_panel_" .. c .. "_escalera"),
  }
end

--- Vidrio de color, claro y polarizado.
local VID = {}
for _, c in ipairs(COLORES) do
  VID[c] = {
    claro       = Bq(N .. "vidrio_claro_" .. c),
    claro_panel = Bq(N .. "vidrio_claro_" .. c .. "_panel"),
    pol         = Bq(N .. "vidrio_polarizado_" .. c),
    pol_panel   = Bq(N .. "vidrio_polarizado_" .. c .. "_panel"),
  }
end

--- Neon con nivel de luz.
---   luz=0  brilla pero NO ilumina   <- las alas dormidas
---   luz=1  ilumina 7                <- ambiente
---   luz=2  ilumina 15               <- plazas y focos
--- Es la propiedad que hace posible el estado "latente" del diseno: un ala
--- que se ve encendida y aun asi deja el espacio a oscuras.
local function neon(color, forma, luz)
  local t = NEON[color]
  if not t then return nil end
  local b = t[forma or "bloque"]
  if not b then return nil end
  if luz then
    local ok, r = pcall(withBlockProperty, b, "luz=" .. tostring(luz))
    if ok and r then return r end
  end
  return b
end


-- ###########################################################################
-- ##  3. REGIONES                                                          ##
-- ###########################################################################
--
-- El orden de esta tabla ES el orden en que se reparten las alas alrededor
-- del decagono, empezando al norte y girando en sentido horario.
--
-- ⚠ LOS ESTADOS NO SON DECORACION. Ver docs/world/palacio.md §5:
--
--   activa     la generacion esta encendida en el servidor  (D-017)
--   latente    hay entrenadores en el datapack RCT, pero la generacion no
--              esta activa todavia
--   reservada  no hay ni contenido: 0 entrenadores en RCT
--
-- La diferencia entre latente y reservada es informacion util para quien
-- construye: dice si lo que falta es ACTIVAR o si falta PRODUCIR.

local REGIONES = {
  { id = "kanto",  nombre = "KANTO",  color = "rojo",       estado = "activa",    entrenadores = 14 },
  { id = "johto",  nombre = "JOHTO",  color = "amarillo",   estado = "activa",    entrenadores = 14 },
  { id = "hoenn",  nombre = "HOENN",  color = "verde",      estado = "latente",   entrenadores = 16 },
  { id = "sinnoh", nombre = "SINNOH", color = "azul_claro", estado = "latente",   entrenadores = 16 },
  { id = "hisui",  nombre = "HISUI",  color = "lima",       estado = "latente",   entrenadores = 2  },
  { id = "unova",  nombre = "TESELIA",color = "gris_claro", estado = "reservada", entrenadores = 0  },
  { id = "kalos",  nombre = "KALOS",  color = "azul",       estado = "reservada", entrenadores = 0  },
  { id = "alola",  nombre = "ALOLA",  color = "naranja",    estado = "reservada", entrenadores = 0  },
  { id = "galar",  nombre = "GALAR",  color = "morado",     estado = "reservada", entrenadores = 0  },
  { id = "paldea", nombre = "PALDEA", color = "magenta",    estado = "reservada", entrenadores = 0  },
}

--- Los equipos de la cripta. Salen del mismo datapack: 159 entrenadores
--- repartidos en cinco bandas.
local EQUIPOS = {
  { id = "rocket",   nombre = "TEAM ROCKET",   color = "negro",   acento = "rojo"    },
  { id = "galactic", nombre = "TEAM GALACTIC", color = "azul",    acento = "cian"    },
  { id = "aqua",     nombre = "TEAM AQUA",     color = "azul",    acento = "azul_claro" },
  { id = "magma",    nombre = "TEAM MAGMA",    color = "rojo",    acento = "naranja" },
  { id = "boss",     nombre = "GIOVANNI",      color = "morado",  acento = "amarillo"},
}

--- ¿Esta ala entra en esta ejecucion?
local function ala_pedida(id)
  if #CFG.solo_alas == 0 then return true end
  for _, s in ipairs(CFG.solo_alas) do
    if s == id then return true end
  end
  return false
end


-- ###########################################################################
-- ##  4. MATEMATICAS                                                       ##
-- ###########################################################################

local floor, ceil, abs = math.floor, math.ceil, math.abs
local sin, cos, atan, sqrt = math.sin, math.cos, math.atan, math.sqrt
local pi = math.pi
local max, min = math.max, math.min

local function redondear(v) return floor(v + 0.5) end

local function acotar(v, lo, hi)
  if v < lo then return lo end
  if v > hi then return hi end
  return v
end

local function lerp(a, b, t) return a + (b - a) * t end

--- Distancia en el plano XZ. Se usa constantemente para los anillos.
local function dist2(dx, dz) return sqrt(dx * dx + dz * dz) end

--- Angulo de un ala, en radianes. El ala `i` (1..10) mira hacia:
---   i=1 -> norte (-Z), y de ahi girando en sentido horario.
local function angulo_ala(i, total)
  return (i - 1) * (2 * pi / total) - pi / 2
end

--- Convierte polar a cartesiano, redondeado a bloque.
local function polar(cx, cz, r, ang)
  return redondear(cx + r * cos(ang)), redondear(cz + r * sin(ang))
end

--- Base ortonormal de un ala: `fx,fz` apunta hacia fuera, `sx,sz` es el
--- lateral. Se calcula UNA vez por ala y se reutiliza; asi la geometria
--- interior del ala se escribe en coordenadas locales (avance, lado) y no
--- hay que rotar a mano en cada llamada, que es donde salen los errores de
--- un bloque que luego no cuadran en el perimetro.
local function base_ala(ang)
  local fx, fz = cos(ang), sin(ang)
  local sx, sz = -sin(ang), cos(ang)
  return fx, fz, sx, sz
end

--- Punto del ala en coordenadas locales.
---   a = avance desde el centro (radio)
---   l = desplazamiento lateral (0 = eje del ala)
local function pt_ala(cx, cz, fx, fz, sx, sz, a, l)
  return redondear(cx + fx * a + sx * l), redondear(cz + fz * a + sz * l)
end

--- Ruido suave 0..1, para vetas del pavimento y desgaste.
local function ruido(x, y, z, escala)
  escala = escala or 0.08
  local ok, v = pcall(getSimplexNoise, x * escala, y * escala, z * escala, CFG.semilla)
  if not ok or not v then return 0.5 end
  return (v + 1) * 0.5
end

--- Ruido de aristas de Voronoi, para juntas y despieces.
local function ruido_junta(x, y, z, escala)
  escala = escala or 0.12
  local ok, v = pcall(getVoronoiEdgeNoise, x * escala, y * escala, z * escala, CFG.semilla)
  if not ok or not v then return 1 end
  return v
end

--- ¿Es este punto parte de un patron de damero?
local function damero(x, z, paso)
  paso = paso or 2
  return (floor(x / paso) + floor(z / paso)) % 2 == 0
end


-- ###########################################################################
-- ##  5. PRIMITIVAS DE COLOCACION                                          ##
-- ###########################################################################
--
-- Todo lo que toca el mundo pasa por `poner()`. Un solo punto de entrada
-- permite contar bloques, ignorar los nil (id mal escrito) y acotar el
-- volumen si algun dia hace falta.

--- Coloca un bloque. Es la UNICA funcion que llama a setBlock.
---
--- ⚠ REDONDEA AQUI, Y ESO NO ES DEFENSIVO: ES NECESARIO.
---
--- El palacio es un decagono, asi que las direcciones de nueve de las diez
--- alas son DIAGONALES: `-sin(a), cos(a)` con `a` no multiplo de 90 grados da
--- numeros como 0,809 y 0,587. Cualquier elemento que multiplique por esa
--- direccion produce coordenadas fraccionarias.
---
--- Se descubrio en el banco de pruebas, no en el juego: `setBlock` recibio
--- x=16.954915028125 construyendo los portales del nucleo. Que Axiom haga con
--- eso --truncar, redondear o ignorar-- no esta documentado, y depender de ello
--- seria construir sobre una suposicion.
---
--- Redondear en el unico punto de salida lo arregla de una vez para las ~60
--- funciones que llaman aqui, en vez de parchear cada una.
local function poner(x, y, z, b)
  if not b then return end          -- id que no resolvio: no revienta nada
  setBlock(redondear(x), redondear(y), redondear(z), b)
  PUESTOS = PUESTOS + 1
end

--- Coloca solo si el sitio esta vacio. Para no pisar lo ya construido cuando
--- dos piezas se solapan: las alas muerden el nucleo por su arranque.
local function poner_si_aire(x, y, z, b)
  if not b then return end
  local rx, ry, rz = redondear(x), redondear(y), redondear(z)
  local actual = getBlock(rx, ry, rz)
  if actual == P.aire or actual == nil then
    setBlock(rx, ry, rz, b)
    PUESTOS = PUESTOS + 1
  end
end

--- Coloca con probabilidad segun ruido. Para vetas y desgaste.
local function poner_ruido(x, y, z, b, umbral, escala)
  if ruido(x, y, z, escala) > (umbral or 0.5) then poner(x, y, z, b) end
end

--- Orienta un bloque. Envuelve withBlockProperty tolerando el fallo: si la
--- propiedad no existe en ese bloque se devuelve el bloque SIN orientar, en
--- vez de un nil que dejaria un hueco silencioso en mitad de un muro.
local function orientar(b, ...)
  if not b then return nil end
  local ok, r = pcall(withBlockProperty, b, ...)
  if ok and r then return r end
  return b
end

--- Devuelve el `facing` que mejor apunta en la direccion (dx,dz).
local function facing_de(dx, dz)
  if abs(dx) > abs(dz) then
    return dx > 0 and "east" or "west"
  else
    return dz > 0 and "south" or "north"
  end
end

--- El facing contrario. Una escalera mira hacia donde BAJA el peldano, que
--- suele ser al reves de lo que uno escribe la primera vez.
local function facing_opuesto(f)
  if f == "north" then return "south" end
  if f == "south" then return "north" end
  if f == "east"  then return "west"  end
  return "east"
end

--- Escalera orientada, con media (bottom/top) y forma.
local function escalera(b, facing, media, forma)
  return orientar(b, "facing=" .. facing,
                     "half=" .. (media or "bottom"),
                     "shape=" .. (forma or "straight"))
end

--- Losa: bottom, top o double.
local function losa(b, tipo)
  return orientar(b, "type=" .. (tipo or "bottom"))
end

--- Pilar tumbado en un eje: x, y o z.
local function pilar_eje(b, eje)
  return orientar(b, "axis=" .. (eje or "y"))
end

--- Muro/valla con sus cuatro conexiones abiertas, para barandillas rectas.
local function muro_conectado(b, ns, ew)
  return orientar(b, "north=" .. (ns and "low" or "none"),
                     "south=" .. (ns and "low" or "none"),
                     "east="  .. (ew and "low" or "none"),
                     "west="  .. (ew and "low" or "none"))
end


-- --------------------------------------------------------------------------
-- 5.1 · Volumenes rectos
-- --------------------------------------------------------------------------

local function caja(x1, y1, z1, x2, y2, z2, b)
  if x1 > x2 then x1, x2 = x2, x1 end
  if y1 > y2 then y1, y2 = y2, y1 end
  if z1 > z2 then z1, z2 = z2, z1 end
  for x = x1, x2 do
    for y = y1, y2 do
      for z = z1, z2 do
        poner(x, y, z, b)
      end
    end
  end
end

--- Caja hueca: solo las seis caras.
local function caja_hueca(x1, y1, z1, x2, y2, z2, b)
  if x1 > x2 then x1, x2 = x2, x1 end
  if y1 > y2 then y1, y2 = y2, y1 end
  if z1 > z2 then z1, z2 = z2, z1 end
  for x = x1, x2 do
    for y = y1, y2 do
      for z = z1, z2 do
        if x == x1 or x == x2 or y == y1 or y == y2 or z == z1 or z == z2 then
          poner(x, y, z, b)
        end
      end
    end
  end
end

--- Solo las cuatro paredes verticales: sin suelo ni techo.
local function paredes(x1, y1, z1, x2, y2, z2, b)
  if x1 > x2 then x1, x2 = x2, x1 end
  if z1 > z2 then z1, z2 = z2, z1 end
  for y = min(y1, y2), max(y1, y2) do
    for x = x1, x2 do
      poner(x, y, z1, b)
      poner(x, y, z2, b)
    end
    for z = z1 + 1, z2 - 1 do
      poner(x1, y, z, b)
      poner(x2, y, z, b)
    end
  end
end

--- Rectangulo plano a una altura.
local function rect(x1, z1, x2, z2, y, b)
  caja(x1, y, z1, x2, y, z2, b)
end

--- Solo el borde de un rectangulo plano.
local function marco(x1, z1, x2, z2, y, b)
  if x1 > x2 then x1, x2 = x2, x1 end
  if z1 > z2 then z1, z2 = z2, z1 end
  for x = x1, x2 do
    poner(x, y, z1, b)
    poner(x, y, z2, b)
  end
  for z = z1 + 1, z2 - 1 do
    poner(x1, y, z, b)
    poner(x2, y, z, b)
  end
end

local function vaciar(x1, y1, z1, x2, y2, z2)
  caja(x1, y1, z1, x2, y2, z2, P.aire)
end


-- --------------------------------------------------------------------------
-- 5.2 · Circulos, anillos y discos
-- --------------------------------------------------------------------------
--
-- ⚠ SE RECORRE EL CUADRADO QUE ENVUELVE EL CIRCULO Y SE FILTRA POR DISTANCIA.
--
-- Es O(r²) en vez de O(r), y aun asi es la unica forma de que el borde salga
-- liso: caminar el perimetro con seno y coseno DEJA HUECOS en las diagonales,
-- porque dos angulos consecutivos pueden saltar mas de un bloque. Ese fallo no
-- se ve en el editor y si en la fachada, cuando ya hay medio palacio encima.

local function disco(cx, cz, r, y, b)
  local ri = ceil(r)
  for dx = -ri, ri do
    for dz = -ri, ri do
      if dist2(dx, dz) <= r + 0.5 then
        poner(cx + dx, y, cz + dz, b)
      end
    end
  end
end

--- Anillo plano entre dos radios, ambos inclusive.
local function anillo(cx, cz, r_int, r_ext, y, b)
  local ri = ceil(r_ext)
  for dx = -ri, ri do
    for dz = -ri, ri do
      local d = dist2(dx, dz)
      if d <= r_ext + 0.5 and d >= r_int - 0.5 then
        poner(cx + dx, y, cz + dz, b)
      end
    end
  end
end

--- Circunferencia de un bloque de grosor.
local function circulo(cx, cz, r, y, b)
  anillo(cx, cz, r - 0.5, r + 0.5, y, b)
end

local function cilindro(cx, cz, r, y1, y2, b)
  for y = min(y1, y2), max(y1, y2) do
    disco(cx, cz, r, y, b)
  end
end

--- Tubo: cilindro hueco entre dos radios.
local function tubo(cx, cz, r_int, r_ext, y1, y2, b)
  for y = min(y1, y2), max(y1, y2) do
    anillo(cx, cz, r_int, r_ext, y, b)
  end
end

local function pared_cilindrica(cx, cz, r, y1, y2, b)
  for y = min(y1, y2), max(y1, y2) do
    circulo(cx, cz, r, y, b)
  end
end

--- Sector de anillo: entre dos angulos. Se usa para partir el perimetro en
--- los diez tramos del decagono sin tener que rotar geometria a mano.
local function sector(cx, cz, r_int, r_ext, a1, a2, y, b)
  local ri = ceil(r_ext)
  for dx = -ri, ri do
    for dz = -ri, ri do
      local d = dist2(dx, dz)
      if d <= r_ext + 0.5 and d >= r_int - 0.5 then
        local a = atan(dz, dx)
        -- Normalizar el angulo al intervalo [a1, a1+2pi)
        local aa = a
        while aa < a1 do aa = aa + 2 * pi end
        while aa >= a1 + 2 * pi do aa = aa - 2 * pi end
        if aa <= a2 then
          poner(cx + dx, y, cz + dz, b)
        end
      end
    end
  end
end


-- --------------------------------------------------------------------------
-- 5.3 · Poligonos regulares — la planta del palacio es un decagono
-- --------------------------------------------------------------------------

--- Los vertices de un poligono regular de `n` lados y radio `r`.
local function vertices(cx, cz, r, n, giro)
  giro = giro or -pi / 2
  local v = {}
  for i = 0, n - 1 do
    local a = giro + i * (2 * pi / n)
    v[#v + 1] = { x = cx + r * cos(a), z = cz + r * sin(a) }
  end
  return v
end

--- ¿Esta el punto dentro del poligono? Producto vectorial contra cada lado;
--- como el poligono es convexo, basta con que todos den el mismo signo.
local function dentro_poligono(px, pz, v)
  local n = #v
  local signo = nil
  for i = 1, n do
    local a, b = v[i], v[(i % n) + 1]
    local cruz = (b.x - a.x) * (pz - a.z) - (b.z - a.z) * (px - a.x)
    if abs(cruz) > 1e-9 then
      local s = cruz > 0
      if signo == nil then signo = s
      elseif signo ~= s then return false end
    end
  end
  return true
end

--- Suelo poligonal macizo.
local function poligono(cx, cz, r, n, y, b, giro)
  local v = vertices(cx, cz, r, n, giro)
  local ri = ceil(r) + 1
  for dx = -ri, ri do
    for dz = -ri, ri do
      local x, z = cx + dx, cz + dz
      if dentro_poligono(x, z, v) then poner(x, y, z, b) end
    end
  end
end

--- Corona poligonal: entre dos poligonos concentricos.
local function anillo_poligonal(cx, cz, r_int, r_ext, n, y, b, giro)
  local ve = vertices(cx, cz, r_ext, n, giro)
  local vi = vertices(cx, cz, r_int, n, giro)
  local ri = ceil(r_ext) + 1
  for dx = -ri, ri do
    for dz = -ri, ri do
      local x, z = cx + dx, cz + dz
      if dentro_poligono(x, z, ve) and not dentro_poligono(x, z, vi) then
        poner(x, y, z, b)
      end
    end
  end
end

--- Muro poligonal: el contorno extruido en altura.
local function muro_poligonal(cx, cz, r, n, y1, y2, b, grosor, giro)
  grosor = grosor or 1
  for y = min(y1, y2), max(y1, y2) do
    anillo_poligonal(cx, cz, r - grosor, r, n, y, b, giro)
  end
end

--- Linea recta en el plano. Interpolacion simple: para estas longitudes da
--- el mismo resultado que Bresenham y ocupa un tercio.
local function linea(x1, z1, x2, z2, y, b)
  local dx, dz = x2 - x1, z2 - z1
  local pasos = max(abs(dx), abs(dz))
  if pasos == 0 then poner(x1, y, z1, b); return end
  for i = 0, pasos do
    local t = i / pasos
    poner(redondear(x1 + dx * t), y, redondear(z1 + dz * t), b)
  end
end

--- Linea en 3D.
local function linea3(x1, y1, z1, x2, y2, z2, b)
  local dx, dy, dz = x2 - x1, y2 - y1, z2 - z1
  local pasos = max(abs(dx), abs(dy), abs(dz))
  if pasos == 0 then poner(x1, y1, z1, b); return end
  for i = 0, pasos do
    local t = i / pasos
    poner(redondear(x1 + dx * t), redondear(y1 + dy * t), redondear(z1 + dz * t), b)
  end
end


-- --------------------------------------------------------------------------
-- 5.4 · Esferas, cupulas y bovedas
-- --------------------------------------------------------------------------

local function esfera(cx, cy, cz, r, b)
  local ri = ceil(r)
  for dx = -ri, ri do
    for dy = -ri, ri do
      for dz = -ri, ri do
        if sqrt(dx*dx + dy*dy + dz*dz) <= r + 0.5 then
          poner(cx + dx, cy + dy, cz + dz, b)
        end
      end
    end
  end
end

--- Cascara esferica de grosor `g`.
local function esfera_hueca(cx, cy, cz, r, b, g)
  g = g or 1
  local ri = ceil(r)
  for dx = -ri, ri do
    for dy = -ri, ri do
      for dz = -ri, ri do
        local d = sqrt(dx*dx + dy*dy + dz*dz)
        if d <= r + 0.5 and d >= r - g + 0.5 then
          poner(cx + dx, cy + dy, cz + dz, b)
        end
      end
    end
  end
end

--- Elipsoide hueco. La luna no es una bola perfecta: se achata un poco para
--- que desde abajo se lea como DISCO y no como pelota, que es como se ve una
--- luna de verdad en el cielo.
local function elipsoide_hueco(cx, cy, cz, rx, ry, rz, b, g)
  g = g or 1
  local rix, riy, riz = ceil(rx), ceil(ry), ceil(rz)
  for dx = -rix, rix do
    for dy = -riy, riy do
      for dz = -riz, riz do
        local d  = sqrt((dx/rx)^2 + (dy/ry)^2 + (dz/rz)^2)
        local di = sqrt((dx/max(1,rx-g))^2 + (dy/max(1,ry-g))^2 + (dz/max(1,rz-g))^2)
        if d <= 1.0 and di > 1.0 then
          poner(cx + dx, cy + dy, cz + dz, b)
        end
      end
    end
  end
end

--- Cupula: media esfera hueca, solo la mitad de arriba.
local function cupula(cx, cy, cz, r, b, g)
  g = g or 1
  local ri = ceil(r)
  for dx = -ri, ri do
    for dy = 0, ri do
      for dz = -ri, ri do
        local d = sqrt(dx*dx + dy*dy + dz*dz)
        if d <= r + 0.5 and d >= r - g + 0.5 then
          poner(cx + dx, cy + dy, cz + dz, b)
        end
      end
    end
  end
end

--- Cupula rebajada: achatada por un factor. Una semiesfera pura sobre un
--- radio de 28 subiria 28 bloques y se comeria la luna; rebajada cabe.
local function cupula_rebajada(cx, cy, cz, r, altura, b, g)
  g = g or 1
  local k = altura / r
  local ri = ceil(r)
  for dx = -ri, ri do
    for dz = -ri, ri do
      local dh = dist2(dx, dz)
      if dh <= r + 0.5 then
        local h = k * sqrt(max(0, r*r - dh*dh))
        local hy = redondear(h)
        for gy = 0, g - 1 do
          if hy - gy >= 0 then
            poner(cx + dx, cy + hy - gy, cz + dz, b)
          end
        end
      end
    end
  end
end

--- Cono hueco, para chapiteles y remates.
local function cono(cx, cy, cz, r, altura, b)
  for i = 0, altura do
    local rr = r * (1 - i / altura)
    if rr >= 0.7 then
      circulo(cx, cz, rr, cy + i, b)
    else
      poner(cx, cy + i, cz, b)
    end
  end
end

--- Boveda de canon sobre un pasillo, extruida a lo largo del eje del ala.
local function boveda(cx, cz, fx, fz, sx, sz, a1, a2, semiancho, y, b)
  for a = a1, a2 do
    for l = -semiancho, semiancho do
      local h = redondear(sqrt(max(0, semiancho*semiancho - l*l)))
      local x, z = pt_ala(cx, cz, fx, fz, sx, sz, a, l)
      poner(x, y + h, z, b)
    end
  end
end

--- Arco de medio punto en un plano vertical, entre dos jambas.
local function arco(cx, cy, cz, dirx, dirz, semiancho, b)
  for i = -semiancho, semiancho do
    local h = redondear(sqrt(max(0, semiancho*semiancho - i*i)))
    poner(cx + dirx * i, cy + h, cz + dirz * i, b)
  end
end

--- Arco apuntado, mas esbelto que el de medio punto. Para los huecos altos
--- de las alcobas, donde el de medio punto se ve achaparrado.
local function arco_apuntado(cx, cy, cz, dirx, dirz, semiancho, altura, b)
  for i = -semiancho, semiancho do
    local t = abs(i) / semiancho
    local h = redondear(altura * (1 - t * t))
    poner(cx + dirx * i, cy + h, cz + dirz * i, b)
  end
end


-- --------------------------------------------------------------------------
-- 5.5 · Escaleras
-- --------------------------------------------------------------------------

--- Escalinata recta de `pasos` peldanos, cada uno de `ancho` bloques.
--- Se rellena por debajo para que no se vea el vacio por los lados.
local function escalinata(x0, y0, z0, dirx, dirz, ancho, pasos, b_esc, b_masa, prof)
  local px, pz = -dirz, dirx
  local f = facing_de(dirx, dirz)
  prof = prof or 6
  local mitad = floor(ancho / 2)
  for i = 0, pasos - 1 do
    local bx = x0 + dirx * i
    local bz = z0 + dirz * i
    local by = y0 + i
    for l = -mitad, mitad do
      local x = bx + px * l
      local z = bz + pz * l
      poner(x, by, z, escalera(b_esc, f, "bottom"))
      if b_masa then
        for yy = by - prof, by - 1 do
          poner(x, yy, z, b_masa)
        end
      end
    end
  end
end

--- Escalera de caracol alrededor de un eje central.
local function caracol(cx, cz, r, y1, y2, vueltas, ancho, b)
  local total = y2 - y1
  if total <= 0 then return end
  for i = 0, total do
    local t = i / total
    local a = t * vueltas * 2 * pi
    for w = 0, ancho - 1 do
      local x, z = polar(cx, cz, r - w, a)
      poner(x, y1 + i, z, b)
    end
  end
end


-- ###########################################################################
-- ##  6. ELEMENTOS ARQUITECTONICOS                                         ##
-- ###########################################################################
--
-- La diferencia entre una caja y un edificio esta aqui. Un muro liso de 22 de
-- alto se lee como pared de almacen; el mismo muro con basa, fuste, cornisa y
-- un friso a la altura de la vista se lee como palacio. Son las mismas piedras.
--
-- Todo lo de esta seccion trabaja en coordenadas ABSOLUTAS y recibe la
-- direccion a la que mira, para poder usarse igual en las diez alas sin
-- reescribir nada.


-- --------------------------------------------------------------------------
-- 6.1 · Columnas
-- --------------------------------------------------------------------------

--- Columna completa: basa, fuste y capitel.
---
--- El fuste va con el pilar orientado en Y (la veta vertical), y la basa y el
--- capitel se ensanchan un bloque con losas para que la columna "apoye" en
--- vez de salir del suelo como un palo clavado.
local function columna(x, y1, z, alto, b_fuste, b_remate)
  b_remate = b_remate or b_fuste
  local y2 = y1 + alto - 1

  -- Fuste
  for y = y1 + 1, y2 - 1 do
    poner(x, y, z, pilar_eje(b_fuste, "y"))
  end

  -- Basa: el bloque y un collar de losas alrededor
  poner(x, y1, z, b_remate)
  poner(x + 1, y1, z, losa(b_remate, "bottom"))
  poner(x - 1, y1, z, losa(b_remate, "bottom"))
  poner(x, y1, z + 1, losa(b_remate, "bottom"))
  poner(x, y1, z - 1, losa(b_remate, "bottom"))

  -- Capitel: igual pero invertido
  poner(x, y2, z, b_remate)
  poner(x + 1, y2, z, losa(b_remate, "top"))
  poner(x - 1, y2, z, losa(b_remate, "top"))
  poner(x, y2, z + 1, losa(b_remate, "top"))
  poner(x, y2, z - 1, losa(b_remate, "top"))
end

--- Columna con una vena de neon corrida por dentro. Es el recurso que hace
--- que el edificio se lea de noche: la luz sale de la propia estructura, no
--- de lamparas colgadas.
local function columna_neon(x, y1, z, alto, b_fuste, color, luz)
  local y2 = y1 + alto - 1
  for y = y1, y2 do
    poner(x, y, z, pilar_eje(b_fuste, "y"))
  end
  -- La vena: cuatro caras, alternando para que no sea un tubo macizo de luz
  for y = y1 + 2, y2 - 2 do
    if (y - y1) % 3 ~= 0 then
      poner(x + 1, y, z, neon(color, "panel", luz))
      poner(x - 1, y, z, neon(color, "panel", luz))
      poner(x, y, z + 1, neon(color, "panel", luz))
      poner(x, y, z - 1, neon(color, "panel", luz))
    end
  end
  poner(x, y1, z, b_fuste)
  poner(x, y2, z, b_fuste)
end

--- Columna gruesa de 3x3, para el nucleo y los contrafuertes del perimetro.
local function columna_gruesa(x, y1, z, alto, b, b_remate)
  b_remate = b_remate or b
  local y2 = y1 + alto - 1
  for y = y1, y2 do
    for dx = -1, 1 do
      for dz = -1, 1 do
        -- Esquinas achaflanadas: sin ellas parece un pilar de hormigon
        if not (abs(dx) == 1 and abs(dz) == 1) then
          poner(x + dx, y, z + dz, pilar_eje(b, "y"))
        end
      end
    end
  end
  -- Basa y capitel de 5x5 con losas
  for _, yy in ipairs({ y1, y2 }) do
    local t = (yy == y1) and "bottom" or "top"
    for dx = -2, 2 do
      for dz = -2, 2 do
        if abs(dx) + abs(dz) <= 3 and not (abs(dx) <= 1 and abs(dz) <= 1) then
          poner(x + dx, yy, z + dz, losa(b_remate, t))
        end
      end
    end
  end
end

--- Pilastra: media columna adosada a un muro. Da ritmo a un paramento largo
--- sin comerse espacio de circulacion.
local function pilastra(x, y1, z, alto, b)
  for y = y1, y1 + alto - 1 do
    poner(x, y, z, pilar_eje(b, "y"))
  end
  poner(x, y1, z, b)
  poner(x, y1 + alto - 1, z, b)
end

--- Columnata recta: `n` columnas equiespaciadas entre dos puntos.
local function columnata(x1, z1, x2, z2, y, alto, n, b_fuste, b_remate)
  if n < 2 then return end
  for i = 0, n - 1 do
    local t = i / (n - 1)
    local x = redondear(lerp(x1, x2, t))
    local z = redondear(lerp(z1, z2, t))
    columna(x, y, z, alto, b_fuste, b_remate)
  end
end

--- Columnata circular: `n` columnas repartidas en un anillo.
local function columnata_circular(cx, cz, r, y, alto, n, b_fuste, b_remate, giro)
  giro = giro or 0
  for i = 0, n - 1 do
    local a = giro + i * (2 * pi / n)
    local x, z = polar(cx, cz, r, a)
    columna(x, y, z, alto, b_fuste, b_remate)
  end
end

--- Columnata circular con vena de neon.
local function columnata_circular_neon(cx, cz, r, y, alto, n, b, color, luz, giro)
  giro = giro or 0
  for i = 0, n - 1 do
    local a = giro + i * (2 * pi / n)
    local x, z = polar(cx, cz, r, a)
    columna_neon(x, y, z, alto, b, color, luz)
  end
end


-- --------------------------------------------------------------------------
-- 6.2 · Cornisas, entablamentos y molduras
-- --------------------------------------------------------------------------

--- Cornisa recta: un vuelo de tres hiladas que remata un muro. Es lo que
--- separa "pared que acaba" de "pared rematada".
local function cornisa_recta(x1, z1, x2, z2, y, b, b_losa)
  b_losa = b_losa or b
  -- hilada 1: retranqueada
  rect(x1, z1, x2, z2, y, b)
  -- hilada 2: vuela un bloque
  rect(x1 - 1, z1 - 1, x2 + 1, z2 + 1, y + 1, b)
  -- hilada 3: losas de remate, vuelan dos
  for x = x1 - 2, x2 + 2 do
    poner(x, y + 2, z1 - 2, losa(b_losa, "bottom"))
    poner(x, y + 2, z2 + 2, losa(b_losa, "bottom"))
  end
  for z = z1 - 2, z2 + 2 do
    poner(x1 - 2, y + 2, z, losa(b_losa, "bottom"))
    poner(x2 + 2, y + 2, z, losa(b_losa, "bottom"))
  end
end

--- Cornisa circular. Misma idea sobre un anillo.
local function cornisa_circular(cx, cz, r, y, b, b_losa)
  b_losa = b_losa or b
  circulo(cx, cz, r, y, b)
  anillo(cx, cz, r, r + 1, y + 1, b)
  anillo(cx, cz, r + 1, r + 2, y + 2, losa(b_losa, "bottom"))
end

--- Cornisa poligonal, para el perimetro del decagono.
local function cornisa_poligonal(cx, cz, r, n, y, b, b_losa, giro)
  b_losa = b_losa or b
  anillo_poligonal(cx, cz, r - 1, r, n, y, b, giro)
  anillo_poligonal(cx, cz, r, r + 1, n, y + 1, b, giro)
  anillo_poligonal(cx, cz, r + 1, r + 2, n, y + 2, losa(b_losa, "bottom"), giro)
end

--- Entablamento: la banda horizontal entre el capitel y la cornisa. Lleva
--- arquitrabe (liso), friso (decorado) y una moldura.
local function entablamento_circular(cx, cz, r, y, b_liso, b_friso, color, luz)
  circulo(cx, cz, r, y, b_liso)                       -- arquitrabe
  circulo(cx, cz, r, y + 1, b_friso)                  -- friso
  if color then
    circulo(cx, cz, r, y + 2, neon(color, "bloque", luz))
  end
  anillo(cx, cz, r, r + 1, y + 3, losa(b_liso, "bottom"))
end

--- Moldura corrida de neon a media altura. La banda de luz que recorre el
--- edificio entero y le da unidad.
local function friso_neon_circular(cx, cz, r, y, color, luz)
  circulo(cx, cz, r, y, neon(color, "bloque", luz))
end

--- Friso de neon recto.
local function friso_neon_recto(x1, z1, x2, z2, y, color, luz)
  linea(x1, z1, x2, z2, y, neon(color, "bloque", luz))
end

--- Zocalo: la banda oscura de la base de un muro. Ensucia menos la vista y
--- hace que el edificio "pise" el suelo.
local function zocalo_circular(cx, cz, r, y, alto, b)
  for i = 0, alto - 1 do
    circulo(cx, cz, r, y + i, b)
  end
  anillo(cx, cz, r, r + 1, y + alto, losa(b, "bottom"))
end


-- --------------------------------------------------------------------------
-- 6.3 · Huecos: ventanales, celosias y arcos
-- --------------------------------------------------------------------------

--- Ventanal vertical en un muro recto, con jambas, dintel y vidrio.
--- `dirx,dirz` es la direccion del MURO (a lo largo), no de la mirada.
local function ventanal(x, y, z, dirx, dirz, ancho, alto, b_marco, b_vidrio)
  local mitad = floor(ancho / 2)
  -- Vidrio
  for i = -mitad, mitad do
    for j = 0, alto - 1 do
      poner(x + dirx * i, y + j, z + dirz * i, b_vidrio)
    end
  end
  -- Jambas
  for j = -1, alto do
    poner(x + dirx * (mitad + 1), y + j, z + dirz * (mitad + 1), b_marco)
    poner(x - dirx * (mitad + 1), y + j, z - dirz * (mitad + 1), b_marco)
  end
  -- Dintel y alfeizar
  for i = -mitad - 1, mitad + 1 do
    poner(x + dirx * i, y + alto, z + dirz * i, b_marco)
    poner(x + dirx * i, y - 1, z + dirz * i, b_marco)
  end
end

--- Ventanal rematado en arco. Mas caro de dibujar y mucho mas noble.
local function ventanal_arco(x, y, z, dirx, dirz, ancho, alto, b_marco, b_vidrio)
  local mitad = floor(ancho / 2)
  for i = -mitad, mitad do
    local h = redondear(sqrt(max(0, mitad*mitad - i*i)))
    for j = 0, alto - 1 + h do
      poner(x + dirx * i, y + j, z + dirz * i, b_vidrio)
    end
    -- El arco del marco por encima
    poner(x + dirx * i, y + alto + h, z + dirz * i, b_marco)
  end
  for j = -1, alto - 1 do
    poner(x + dirx * (mitad + 1), y + j, z + dirz * (mitad + 1), b_marco)
    poner(x - dirx * (mitad + 1), y + j, z - dirz * (mitad + 1), b_marco)
  end
end

--- Celosia: paño de rejilla con un bastidor. Deja pasar la vista y la luz.
local function celosia(x, y, z, dirx, dirz, ancho, alto, b_marco, b_rejilla)
  local mitad = floor(ancho / 2)
  for i = -mitad, mitad do
    for j = 0, alto - 1 do
      -- Damero: rejilla y hueco, para que se vea el otro lado
      if (i + j) % 2 == 0 then
        poner(x + dirx * i, y + j, z + dirz * i, b_rejilla)
      end
    end
  end
  for j = -1, alto do
    poner(x + dirx * (mitad + 1), y + j, z + dirz * (mitad + 1), b_marco)
    poner(x - dirx * (mitad + 1), y + j, z - dirz * (mitad + 1), b_marco)
  end
end

--- Portal: hueco de paso rematado en arco, con jambas gruesas.
local function portal(x, y, z, dirx, dirz, semiancho, alto, b_marco, color, luz)
  -- Vaciar el hueco
  for i = -semiancho, semiancho do
    local h = redondear(sqrt(max(0, semiancho*semiancho - i*i)))
    for j = 0, alto + h do
      poner(x + dirx * i, y + j, z + dirz * i, P.aire)
    end
  end
  -- Arco
  for i = -semiancho - 1, semiancho + 1 do
    local ii = acotar(i, -semiancho, semiancho)
    local h = redondear(sqrt(max(0, semiancho*semiancho - ii*ii)))
    poner(x + dirx * i, y + alto + h + 1, z + dirz * i, b_marco)
  end
  -- Jambas
  for j = 0, alto do
    poner(x + dirx * (semiancho + 1), y + j, z + dirz * (semiancho + 1), b_marco)
    poner(x - dirx * (semiancho + 1), y + j, z - dirz * (semiancho + 1), b_marco)
  end
  -- Vena de luz en las jambas
  if color then
    for j = 1, alto - 1 do
      poner(x + dirx * (semiancho + 2), y + j, z + dirz * (semiancho + 2), neon(color, "panel", luz))
      poner(x - dirx * (semiancho + 2), y + j, z - dirz * (semiancho + 2), neon(color, "panel", luz))
    end
  end
end


-- --------------------------------------------------------------------------
-- 6.4 · Barandillas y pasarelas
-- --------------------------------------------------------------------------

--- Barandilla recta: muro bajo con pasamanos de losa.
local function barandilla(x1, z1, x2, z2, y, b_muro, b_pasamanos)
  linea(x1, z1, x2, z2, y, b_muro)
  linea(x1, z1, x2, z2, y + 1, losa(b_pasamanos or b_muro, "bottom"))
end

--- Barandilla circular.
local function barandilla_circular(cx, cz, r, y, b_muro, b_pasamanos)
  circulo(cx, cz, r, y, b_muro)
  circulo(cx, cz, r, y + 1, losa(b_pasamanos or b_muro, "bottom"))
end

--- Balaustrada: barandilla calada, con balaustres cada dos bloques y un
--- pasamanos continuo encima.
local function balaustrada_circular(cx, cz, r, y, b_balaustre, b_pasamanos, paso)
  paso = paso or 2
  local n = max(8, redondear(2 * pi * r / paso))
  for i = 0, n - 1 do
    local a = i * (2 * pi / n)
    local x, z = polar(cx, cz, r, a)
    poner(x, y, z, b_balaustre)
  end
  circulo(cx, cz, r, y + 1, losa(b_pasamanos, "bottom"))
end

--- Pasarela de rejilla entre dos puntos, con barandilla a los dos lados.
local function pasarela(x1, z1, x2, z2, y, ancho, b_suelo, b_baranda)
  local dx, dz = x2 - x1, z2 - z1
  local largo = max(abs(dx), abs(dz))
  if largo == 0 then return end
  local px, pz = -dz / largo, dx / largo
  local mitad = floor(ancho / 2)
  for i = 0, largo do
    local t = i / largo
    local bx = lerp(x1, x2, t)
    local bz = lerp(z1, z2, t)
    for l = -mitad, mitad do
      poner(redondear(bx + px * l), y, redondear(bz + pz * l), b_suelo)
    end
    poner(redondear(bx + px * (mitad + 1)), y, redondear(bz + pz * (mitad + 1)), b_baranda)
    poner(redondear(bx - px * (mitad + 1)), y, redondear(bz - pz * (mitad + 1)), b_baranda)
  end
end


-- --------------------------------------------------------------------------
-- 6.5 · Suelos con dibujo
-- --------------------------------------------------------------------------

--- Suelo de damero entre dos materiales.
local function suelo_damero(cx, cz, r, y, b1, b2, paso)
  paso = paso or 2
  local ri = ceil(r)
  for dx = -ri, ri do
    for dz = -ri, ri do
      if dist2(dx, dz) <= r + 0.5 then
        local b = damero(cx + dx, cz + dz, paso) and b1 or b2
        poner(cx + dx, y, cz + dz, b)
      end
    end
  end
end

--- Mandala: anillos concentricos alternando materiales, con radios marcados.
--- Es el dibujo de suelo de la rotonda, y lo que hace que el centro se lea
--- como centro aunque este vacio.
local function mandala(cx, cz, r, y, capas, radios, b_radio)
  -- capas = lista de {hasta = radio, bloque = b}
  local ri = ceil(r)
  for dx = -ri, ri do
    for dz = -ri, ri do
      local d = dist2(dx, dz)
      if d <= r + 0.5 then
        local b = nil
        for _, capa in ipairs(capas) do
          if d <= capa.hasta + 0.5 then b = capa.bloque; break end
        end
        if b then poner(cx + dx, y, cz + dz, b) end
      end
    end
  end
  -- Radios: lineas desde el centro hacia fuera
  if radios and radios > 0 and b_radio then
    for i = 0, radios - 1 do
      local a = i * (2 * pi / radios) - pi / 2
      local ex, ez = polar(cx, cz, r, a)
      linea(cx, cz, ex, ez, y, b_radio)
    end
  end
end

--- Alfombra: un rectangulo con borde de otro color, para marcar recorrido.
local function alfombra(x1, z1, x2, z2, y, b_centro, b_borde)
  rect(x1, z1, x2, z2, y, b_centro)
  marco(x1, z1, x2, z2, y, b_borde)
end


-- --------------------------------------------------------------------------
-- 6.6 · Techos
-- --------------------------------------------------------------------------

--- Artesonado: techo de casetones. Un techo plano de 18 de ancho es una losa
--- de aparcamiento; con casetones tiene escala.
local function artesonado(x1, z1, x2, z2, y, b_fondo, b_nervio, paso)
  paso = paso or 4
  if x1 > x2 then x1, x2 = x2, x1 end
  if z1 > z2 then z1, z2 = z2, z1 end
  rect(x1, z1, x2, z2, y, b_fondo)
  for x = x1, x2 do
    for z = z1, z2 do
      if (x % paso == 0) or (z % paso == 0) then
        poner(x, y, z, b_nervio)
        poner(x, y - 1, z, losa(b_nervio, "top"))
      end
    end
  end
end

--- Lucernario: hueco de luz en un techo, con marco y vidrio.
local function lucernario(cx, cz, r, y, b_marco, b_vidrio)
  disco(cx, cz, r, y, b_vidrio)
  circulo(cx, cz, r + 1, y, b_marco)
end


-- --------------------------------------------------------------------------
-- 6.7 · LA PLATAFORMA DE ENTRENADOR
-- --------------------------------------------------------------------------
--
-- ⚠ ESTA ES LA PIEZA QUE MAS IMPORTA DEL FICHERO, y la que se deja VACIA.
--
-- El usuario decidio que los entrenadores se colocan despues y a mano. Asi que
-- el script construye el sitio, lo senaliza y no pone nada encima: ni un
-- spawner, ni una armadura, ni un marcador solido que luego haya que picar.
--
-- Lo que si deja:
--   - un estrado de dos alturas, para que el entrenador quede por encima
--   - el color de la region en el borde
--   - un aro de neon que dice si el ala esta viva o dormida
--   - las cuatro esquinas marcadas, para poder centrar al entrenador de un
--     vistazo sin contar bloques
--
-- El hueco de encima se deja a AIRE explicitamente: si se construye sobre algo
-- previo, un bloque suelto ahi impediria colocar al entrenador y costaria
-- encontrarlo.

local function plataforma_entrenador(cx, y, cz, radio, color, luz, b_base, b_borde)
  radio = radio or 2

  -- Peldano inferior, un bloque mas ancho
  disco(cx, cz, radio + 1, y, b_base)
  circulo(cx, cz, radio + 1, y, b_borde)

  -- Estrado
  disco(cx, cz, radio, y + 1, b_base)

  -- Aro de neon en el canto: es lo que se ve desde el otro extremo del ala
  circulo(cx, cz, radio, y + 1, neon(color, "losa", luz))

  -- Las cuatro esquinas del cuadrado que lo envuelve, para centrar a ojo
  for _, d in ipairs({ {1,1}, {1,-1}, {-1,1}, {-1,-1} }) do
    poner(cx + d[1] * (radio + 1), y + 1, cz + d[2] * (radio + 1),
          neon(color, "bloque", luz))
  end

  -- Y el hueco, vacio de verdad
  caja(cx - radio, y + 2, cz - radio, cx + radio, y + 4, cz + radio, P.aire)
end

--- Plataforma grande, para el estrado del campeon: rectangular y con dos
--- escalones de acceso por delante.
local function estrado_campeon(cx, y, cz, ancho, fondo, dirx, dirz, color, luz,
                               b_base, b_borde)
  local ax, az = floor(ancho / 2), floor(fondo / 2)

  -- Dos escalones
  caja(cx - ax - 2, y, cz - az - 2, cx + ax + 2, y, cz + az + 2, b_base)
  caja(cx - ax - 1, y + 1, cz - az - 1, cx + ax + 1, y + 1, cz + az + 1, b_base)
  -- Tarima
  caja(cx - ax, y + 2, cz - az, cx + ax, y + 2, cz + az, b_base)
  marco(cx - ax, cz - az, cx + ax, cz + az, y + 2, b_borde)

  -- Cordon de neon en el perimetro de la tarima
  for x = cx - ax, cx + ax do
    poner(x, y + 2, cz - az, neon(color, "losa", luz))
    poner(x, y + 2, cz + az, neon(color, "losa", luz))
  end
  for z = cz - az, cz + az do
    poner(cx - ax, y + 2, z, neon(color, "losa", luz))
    poner(cx + ax, y + 2, z, neon(color, "losa", luz))
  end

  -- Hueco vacio
  caja(cx - ax + 1, y + 3, cz - az + 1, cx + ax - 1, y + 6, cz + az - 1, P.aire)
end

--- Pedestal alto y estrecho, para las figuras de la corona.
local function pedestal(cx, y, cz, alto, color, luz, b)
  for i = 0, alto - 1 do
    poner(cx, y + i, cz, pilar_eje(b, "y"))
  end
  for _, d in ipairs({ {1,0}, {-1,0}, {0,1}, {0,-1} }) do
    poner(cx + d[1], y, cz + d[2], losa(b, "bottom"))
    poner(cx + d[1], y + alto - 1, cz + d[2], losa(b, "top"))
  end
  poner(cx, y + alto, cz, neon(color, "losa", luz))
  caja(cx - 1, y + alto + 1, cz - 1, cx + 1, y + alto + 3, cz + 1, P.aire)
end


-- --------------------------------------------------------------------------
-- 6.8 · Faros y luminarias
-- --------------------------------------------------------------------------

--- Faro del perimetro: un mastil rematado en una linterna de neon.
--- Van en los diez vertices del decagono y son lo que dibuja la silueta del
--- palacio contra el cielo negro.
local function faro(x, y, z, alto, color, luz, b)
  -- Base
  for dx = -1, 1 do
    for dz = -1, 1 do
      poner(x + dx, y, z + dz, b)
    end
  end
  -- Mastil
  for i = 1, alto - 4 do
    poner(x, y + i, z, pilar_eje(b, "y"))
    if i % 4 ~= 0 then
      poner(x + 1, y + i, z, neon(color, "panel", 0))
      poner(x - 1, y + i, z, neon(color, "panel", 0))
      poner(x, y + i, z + 1, neon(color, "panel", 0))
      poner(x, y + i, z - 1, neon(color, "panel", 0))
    end
  end
  -- Linterna
  local ly = y + alto - 3
  for dx = -1, 1 do
    for dz = -1, 1 do
      poner(x + dx, ly, z + dz, neon(color, "bloque", luz))
      poner(x + dx, ly + 1, z + dz, neon(color, "bloque", luz))
    end
  end
  -- Capuchon
  for dx = -1, 1 do
    for dz = -1, 1 do
      poner(x + dx, ly + 2, z + dz, losa(b, "top"))
    end
  end
  poner(x, ly + 3, z, b)
end

--- Aplique de pared: una pequena luz adosada, para los pasillos.
local function aplique(x, y, z, dirx, dirz, color, luz, b)
  poner(x, y, z, b)
  poner(x + dirx, y, z + dirz, neon(color, "losa", luz))
  poner(x, y + 1, z, losa(b, "top"))
end

--- Arana: luminaria colgada del techo, para la rotonda.
local function arana(cx, cy, cz, radio, color, luz, b)
  -- Cable
  for i = 0, 3 do
    poner(cx, cy + i, cz, pilar_eje(b, "y"))
  end
  -- Corona
  circulo(cx, cz, radio, cy, neon(color, "bloque", luz))
  circulo(cx, cz, radio, cy - 1, losa(b, "top"))
  -- Radios hacia el centro
  for i = 0, 5 do
    local a = i * (2 * pi / 6)
    local ex, ez = polar(cx, cz, radio, a)
    linea(cx, cz, ex, ez, cy, b)
  end
  poner(cx, cy - 2, cz, neon(color, "bloque", luz))
end


-- ###########################################################################
-- ##  7. PIEZAS DEL PALACIO                                                ##
-- ###########################################################################
--
-- De aqui para abajo ya no hay geometria generica: es este edificio y no otro.
-- Cada funcion construye una pieza completa y se puede llamar sola, que es lo
-- que permite iterar sobre el nucleo sin esperar a que salgan las diez alas.


-- --------------------------------------------------------------------------
-- 7.1 · EL BASAMENTO
-- --------------------------------------------------------------------------
--
-- La plataforma decagonal sobre la que se apoya todo. Vuela sobre el vacio, y
-- eso obliga a resolver algo que en un edificio con terreno no existe: EL
-- FONDO SE VE. Desde la plaza, 10 bloques mas abajo, lo que se mira es la
-- panza del palacio.
--
-- Se resuelve escalonandola hacia dentro, como una piramide invertida. Ademas
-- de que se lee mucho mejor, esconde el canto plano que delataria que esto es
-- una losa flotando.

local function construir_basamento(cx, cz)
  local y0 = CFG.y_base
  local r  = CFG.r_perimetro
  local n  = 10

  -- Suelo estructural de la planta noble
  poligono(cx, cz, r, n, y0 - 1, P.muro, -pi / 2)

  -- Escalonado hacia abajo: cada hilada mete el borde un bloque
  for i = 0, CFG.alto_basa - 1 do
    local rr = r - i
    local b = (i % 2 == 0) and P.muro_alt or P.muro
    anillo_poligonal(cx, cz, rr - 3, rr, n, y0 - 2 - i, b, -pi / 2)
  end

  -- Y un fondo macizo que tape el hueco central por debajo
  poligono(cx, cz, r - CFG.alto_basa, n, y0 - 1 - CFG.alto_basa, P.muro_oscuro, -pi / 2)

  -- Nervios: una linea de metal desde cada vertice hacia el centro, por la
  -- cara de abajo. Es lo que hace que la panza parezca estructura y no relleno.
  local v = vertices(cx, cz, r - 1, n, -pi / 2)
  for _, p in ipairs(v) do
    linea3(redondear(p.x), y0 - 2, redondear(p.z),
           cx, y0 - 1 - CFG.alto_basa, cz, P.acero_osc)
  end

  -- Cornisa de coronacion del basamento, ya a la altura del suelo pisable
  cornisa_poligonal(cx, cz, r, n, y0 - 1, P.cromo, P.cromo, -pi / 2)

  -- Zocalo de neon corrido: la linea de luz que dibuja el contorno del
  -- palacio visto desde abajo. Va en blanco, que es el color de la casa; los
  -- colores de region se reservan para las alas.
  anillo_poligonal(cx, cz, r - 1, r, n, y0 - 3, neon("blanco", "bloque", 1), -pi / 2)
end


-- --------------------------------------------------------------------------
-- 7.2 · LA ESCALINATA DE ACCESO
-- --------------------------------------------------------------------------
--
-- Sube desde la plaza de la ciudadela (se camina en 64) hasta la planta noble.
-- Sale al NORTE porque es la direccion desde la que se llega: el punto de
-- aterrizaje de la ciudadela esta en 4,69,0 (CLAUDE.md), al sur del solar.
--
-- Va flanqueada por dos rampas macizas para que no parezca una escalera de
-- incendios pegada a la fachada.

local function construir_escalinata(cx, cz)
  local y0 = CFG.y_base
  local pasos = y0 - 64
  if pasos <= 0 then return end

  local z_arranque = cz - CFG.r_perimetro - pasos
  local ancho = 21

  -- La escalinata en si, subiendo hacia el sur (+Z)
  escalinata(cx, 64, z_arranque, 0, 1, ancho, pasos + 1,
             P.suelo_esc, P.muro_alt, 8)

  -- Machones laterales
  local mitad = floor(ancho / 2)
  for lado = -1, 1, 2 do
    local x = cx + lado * (mitad + 2)
    for i = 0, pasos do
      caja(x, 64 + i - 8, z_arranque + i, x + lado * 2, 64 + i, z_arranque + i, P.muro)
    end
    -- Barandilla de neon corrida
    for i = 0, pasos do
      poner(x, 64 + i + 1, z_arranque + i, neon("blanco", "losa", 1))
    end
  end

  -- Rellano de llegada
  rect(cx - mitad - 2, cz - CFG.r_perimetro - 2, cx + mitad + 2, cz - CFG.r_perimetro + 1,
       y0 - 1, P.terrazo)

  -- Dos faros flanqueando la entrada
  faro(cx - mitad - 3, y0, cz - CFG.r_perimetro - 1, 14, "blanco", 2, P.cromo)
  faro(cx + mitad + 3, y0, cz - CFG.r_perimetro - 1, 14, "blanco", 2, P.cromo)
end


-- --------------------------------------------------------------------------
-- 7.3 · EL NUCLEO — la rotonda central
-- --------------------------------------------------------------------------
--
-- El corazon del palacio: una rotonda de 56 de diametro con el suelo en
-- mandala, un anillo de columnas gruesas y DIEZ PORTALES, uno hacia cada ala.
--
-- ⚠ Los portales se abren DESPUES de levantar el muro, no antes. Levantar el
-- muro con los huecos ya previstos parece mas limpio y es mucho peor: cada
-- hueco hay que calcularlo contra el angulo del ala, y un error de un bloque
-- deja el portal descentrado respecto al pasillo que llega. Vaciando despues,
-- el portal se abre EN el eje del ala por construccion.

local function construir_nucleo(cx, cz)
  local y0 = CFG.y_base
  local r  = CFG.r_nucleo
  local h  = CFG.alto_planta

  -- --- Suelo -------------------------------------------------------------
  -- Mandala de cuatro capas. El centro es el pozo de luz.
  mandala(cx, cz, r, y0 - 1, {
    { hasta = 4,      bloque = neon("blanco", "bloque", 2) },
    { hasta = 7,      bloque = P.laton },
    { hasta = 11,     bloque = P.terrazo },
    { hasta = 14,     bloque = P.cromo },
    { hasta = 20,     bloque = P.terrazo },
    { hasta = r,      bloque = P.suelo },
  }, 10, P.acero_osc)

  -- Anillo de junta entre el suelo del nucleo y el de las alas
  circulo(cx, cz, r, y0 - 1, P.cromo)

  -- --- Muro perimetral ---------------------------------------------------
  pared_cilindrica(cx, cz, r, y0, y0 + h - 1, P.muro)
  pared_cilindrica(cx, cz, r + 1, y0, y0 + h - 1, P.muro_alt)

  -- Zocalo oscuro abajo
  zocalo_circular(cx, cz, r + 1, y0, 3, P.muro_oscuro)

  -- --- Columnata interior -------------------------------------------------
  -- Veinte columnas gruesas: dos por ala, de modo que cada portal queda
  -- flanqueado. Se calcula con el mismo paso angular que las alas para que
  -- caigan simetricas y no "casi" simetricas, que se nota.
  local n_alas = #REGIONES
  for i = 1, n_alas do
    local a = angulo_ala(i, n_alas)
    local sep = (2 * pi / n_alas) * 0.33
    for _, da in ipairs({ -sep, sep }) do
      local x, z = polar(cx, cz, r - 5, a + da)
      columna_gruesa(x, y0, z, h - 4, P.cromo_pilar, P.cromo)
    end
  end

  -- --- Entablamento y cornisa --------------------------------------------
  entablamento_circular(cx, cz, r - 3, y0 + h - 4, P.muro, P.nervio, "blanco", 1)
  cornisa_circular(cx, cz, r + 1, y0 + h - 1, P.cromo, P.cromo)

  -- Friso de neon a la altura de la vista
  friso_neon_circular(cx, cz, r, y0 + 5, "blanco", 1)

  -- --- El pozo de luz ----------------------------------------------------
  -- Un cilindro de vidrio que sube desde el suelo hasta la cupula. Es lo que
  -- conecta visualmente el suelo con la luna, y lo que justifica que la
  -- rotonda este vacia: el vacio ES la pieza.
  tubo(cx, cz, 3, 4, y0, CFG.corona_y - 2, P.vidrio)
  cilindro(cx, cz, 3, y0, CFG.corona_y - 2, neon("blanco", "bloque", 0))
  -- Aros de refuerzo cada 8, para que el tubo tenga escala
  for y = y0 + 4, CFG.corona_y - 4, 8 do
    circulo(cx, cz, 5, y, P.cromo)
    circulo(cx, cz, 5, y + 1, losa(P.cromo, "top"))
  end

  -- --- Aranas ------------------------------------------------------------
  for i = 1, 5 do
    local a = (i - 1) * (2 * pi / 5) + pi / 5
    local x, z = polar(cx, cz, r - 12, a)
    arana(x, y0 + h - 6, z, 3, "blanco", 2, P.laton)
  end

  -- --- Los diez portales -------------------------------------------------
  for i = 1, n_alas do
    local reg = REGIONES[i]
    local a = angulo_ala(i, n_alas)
    local fx, fz = cos(a), sin(a)
    local dirx, dirz = -sin(a), cos(a)      -- perpendicular: el ancho del hueco

    -- El portal se abre en el muro, a radio r
    local px, pz = polar(cx, cz, r, a)
    local color = reg.color
    local luz = (reg.estado == "activa") and 2 or 0

    portal(px, y0, pz, dirx, dirz, 5, 8, P.cromo, color, luz)
    -- Y una segunda vuelta en el muro exterior
    local px2, pz2 = polar(cx, cz, r + 1, a)
    portal(px2, y0, pz2, dirx, dirz, 5, 8, P.cromo, nil, nil)

    -- Dintel con el color de la region, para identificar el ala desde dentro
    for l = -6, 6 do
      poner(redondear(px + dirx * l), y0 + 10, redondear(pz + dirz * l),
            neon(color, "bloque", luz))
    end
  end
end


-- --------------------------------------------------------------------------
-- 7.4 · LA CUPULA
-- --------------------------------------------------------------------------
--
-- Cubre la rotonda. Va REBAJADA (mas ancha que alta) por dos motivos: una
-- semiesfera pura sobre un radio de 28 subiria 28 bloques y se comeria el
-- espacio de la luna, y ademas desde la plaza se veria como un huevo.
--
-- Lleva nervios radiales de metal y panos de vidrio entre ellos, que de noche
-- convierten la cupula en una linterna.

local function construir_cupula(cx, cz)
  local y0 = CFG.y_base + CFG.alto_planta
  local r  = CFG.r_nucleo + 1
  local altura = 16

  -- Tambor: una banda vertical antes de que arranque la curva. Sin el, la
  -- cupula nace directamente del muro y el encuentro queda seco.
  pared_cilindrica(cx, cz, r, y0, y0 + 3, P.muro_alt)
  cornisa_circular(cx, cz, r, y0 + 3, P.cromo, P.cromo)

  -- Ventanas del tambor, una por ala
  local n_alas = #REGIONES
  for i = 1, n_alas do
    local a = angulo_ala(i, n_alas)
    local x, z = polar(cx, cz, r, a)
    local dirx, dirz = -sin(a), cos(a)
    ventanal_arco(redondear(x), y0 + 1, redondear(z),
                  redondear(dirx), redondear(dirz), 5, 2, P.cromo, P.vidrio)
  end

  -- Casquete
  local yc = y0 + 4
  cupula_rebajada(cx, yc, cz, r, altura, P.muro, 2)

  -- Nervios radiales: 20, alternando con los panos
  for i = 0, 19 do
    local a = i * (2 * pi / 20)
    for d = 0, ceil(r) do
      local dh = d
      if dh <= r then
        local hh = redondear((altura / r) * sqrt(max(0, r*r - dh*dh)))
        local x, z = polar(cx, cz, dh, a)
        poner(x, yc + hh, z, P.cromo)
        poner(x, yc + hh - 1, z, P.cromo)
      end
    end
  end

  -- Panos de vidrio entre nervios: se calan agujeros regulares en el casquete
  for i = 0, 19 do
    local a = (i + 0.5) * (2 * pi / 20)
    for d = ceil(r * 0.35), ceil(r * 0.85) do
      local hh = redondear((altura / r) * sqrt(max(0, r*r - d*d)))
      local x, z = polar(cx, cz, d, a)
      if d % 3 ~= 0 then
        poner(x, yc + hh, z, P.vidrio)
      end
    end
  end

  -- Oculo: el hueco central por donde sube el pozo de luz hacia la luna
  cilindro(cx, cz, 6, yc + altura - 3, yc + altura + 2, P.aire)
  tubo(cx, cz, 6, 7, yc + altura - 3, yc + altura + 2, P.cromo)
  circulo(cx, cz, 7, yc + altura + 2, neon("blanco", "bloque", 2))

  -- Linterna sobre el oculo
  pared_cilindrica(cx, cz, 7, yc + altura + 3, yc + altura + 8, P.vidrio)
  columnata_circular(cx, cz, 7, yc + altura + 3, 6, 8, P.cromo_pilar, P.cromo)
  cupula_rebajada(cx, yc + altura + 9, cz, 8, 5, P.cromo, 1)
  cono(cx, yc + altura + 14, cz, 3, 6, P.laton)
end


-- --------------------------------------------------------------------------
-- 7.5 · LA LUNA
-- --------------------------------------------------------------------------
--
-- El elemento que da nombre al servidor. Flota sobre la cupula, sin sujecion
-- visible: la ciudadela tiene noche permanente (fixed_time 18000) y eso hace
-- que una esfera luminosa a 130 de altura sea LO PRIMERO que se ve al llegar.
--
-- ⚠ NO ES UNA ESFERA PERFECTA. Se achata un 15 % en vertical. Una esfera
-- exacta, vista desde 60 bloques mas abajo, se lee como pelota; achatada se
-- lee como disco, que es como se ve una luna de verdad.
--
-- ⚠ Y NO ES MACIZA. Una bola solida de radio 21 son ~38 000 bloques y por
-- dentro no se ve nada: se hace hueca con la cascara de 2, y dentro se pone
-- un nucleo de neon que la hace brillar desde el interior a traves del vidrio.

local function construir_luna(cx, cz)
  local cy = CFG.luna_y
  local r  = CFG.luna_radio

  -- Cascara exterior de vidrio, achatada
  elipsoide_hueco(cx, cy, cz, r, r * 0.85, r, P.vidrio, 2)

  -- Nucleo luminoso
  esfera(cx, cy, cz, r - 6, neon("blanco", "bloque", 2))

  -- Los "mares": manchas mas oscuras. Se sacan del ruido simplex, no a mano,
  -- para que no queden simetricas -- una luna con manchas simetricas parece
  -- una pelota de playa.
  local ri = ceil(r)
  for dx = -ri, ri do
    for dy = -ri, ri do
      for dz = -ri, ri do
        local d = sqrt((dx/r)^2 + (dy/(r*0.85))^2 + (dz/r)^2)
        if d <= 1.0 and d > 0.86 then
          local nz = ruido(cx + dx, cy + dy, cz + dz, 0.07)
          if nz > 0.62 then
            poner(cx + dx, cy + dy, cz + dz, P.vidrio_panel)
          elseif nz < 0.34 then
            poner(cx + dx, cy + dy, cz + dz, neon("gris_claro", "bloque", 1))
          end
        end
      end
    end
  end

  -- Anillo ecuatorial de metal: le da un eje y evita que parezca solo una
  -- bola de luz. Ademas ancla visualmente la luna al edificio de abajo.
  for i = 0, 71 do
    local a = i * (2 * pi / 72)
    local x, z = polar(cx, cz, r + 2, a)
    poner(x, cy, z, P.cromo)
    poner(x, cy + 1, z, losa(P.cromo, "bottom"))
    poner(x, cy - 1, z, losa(P.cromo, "top"))
  end
  -- Un segundo anillo inclinado, mas fino
  for i = 0, 95 do
    local a = i * (2 * pi / 96)
    local rr = r + 5
    local x = redondear(cx + rr * cos(a))
    local z = redondear(cz + rr * sin(a) * 0.7)
    local y = redondear(cy + rr * sin(a) * 0.5)
    poner(x, y, z, neon("blanco", "losa", 1))
  end
end


-- --------------------------------------------------------------------------
-- 7.6 · EL ALA REGIONAL
-- --------------------------------------------------------------------------
--
-- La pieza que se repite DIEZ veces, una por region. Todo se escribe en
-- coordenadas LOCALES del ala:
--
--     a  avance desde el centro del palacio (es el radio)
--     l  desplazamiento lateral (0 = eje del ala, negativo = izquierda)
--
-- y `pt_ala()` las convierte a mundo. Escribirlo asi es lo unico que hace
-- manejable la simetria de orden 10: la geometria se piensa UNA vez, en un
-- pasillo recto, y sale bien en los diez angulos. Rotar a mano cada pieza es
-- de donde salen los errores de un bloque que luego no cierran en el
-- perimetro, y que ya no se pueden arreglar sin rehacer el ala entera.
--
-- ⚠ LOS TRES ESTADOS NO SON UN FILTRO DE COLOR. Cambian material, luz y si el
-- ala esta abierta o sellada. Ver docs/world/palacio.md §5.
--
--     activa      hormigon del color, neon a luz=2, paso libre
--     latente     hormigon del color, neon a luz=0 (brilla y NO ilumina),
--                 sellada con vidrio polarizado
--     reservada   grafito y acero, sin color, sin neon, sellada
--
-- La diferencia latente/reservada dice si lo que falta es ACTIVAR la
-- generacion o si falta PRODUCIR el contenido. Es informacion util, no adorno.

--- Materiales de un ala segun su estado.
local function materiales_ala(reg)
  local e = reg.estado
  if e == "reservada" then
    return {
      muro    = P.grafito,
      muro2   = P.muro_oscuro,
      suelo   = P.asfalto,
      suelo2  = P.terrazo_osc,
      col     = P.grafito_pilar,
      remate  = P.grafito,
      luz     = nil,          -- sin neon
      color   = nil,
      vidrio  = P.vidrio_panel,
      sellada = true,
    }
  elseif e == "latente" then
    return {
      muro    = HORM[reg.color].panel,
      muro2   = P.muro_alt,
      suelo   = P.terrazo_osc,
      suelo2  = HORM[reg.color].pulido,
      col     = P.titanio_pilar,
      remate  = P.titanio,
      luz     = 0,            -- brilla pero no ilumina: esa es la idea
      color   = reg.color,
      vidrio  = VID[reg.color].pol,
      sellada = true,
    }
  else -- activa
    return {
      muro    = P.muro,
      muro2   = HORM[reg.color].panel,
      suelo   = P.suelo,
      suelo2  = HORM[reg.color].pulido,
      col     = P.cromo_pilar,
      remate  = P.cromo,
      luz     = 2,
      color   = reg.color,
      vidrio  = VID[reg.color].claro,
      sellada = false,
    }
  end
end

--- Construye un ala completa.
local function construir_ala(cx, cz, indice, reg)
  local n_alas = #REGIONES
  local ang = angulo_ala(indice, n_alas)
  local fx, fz, sx, sz = base_ala(ang)
  local M = materiales_ala(reg)

  local y0    = CFG.y_base
  local h     = CFG.alto_planta
  local a_ini = CFG.r_nucleo + 2          -- arranca justo fuera del nucleo
  local a_fin = CFG.r_alas
  local semi  = floor(CFG.ala_ancho / 2)  -- semiancho interior

  --- Atajo: punto local -> mundo
  local function pw(a, l) return pt_ala(cx, cz, fx, fz, sx, sz, a, l) end

  --- Rellena una banda del ala a una altura dada.
  local function banda(a1, a2, l1, l2, y, b)
    for a = a1, a2 do
      for l = l1, l2 do
        local x, z = pw(a, l)
        poner(x, y, z, b)
      end
    end
  end

  -- =========================================================================
  -- 1. SUELO
  -- =========================================================================
  -- Alfombra central del color de la region, flanqueada por pavimento neutro.
  -- El color va en el SUELO y no en los muros a proposito: el suelo se ve
  -- entero desde la puerta, y los muros quedan tapados por las alcobas.
  banda(a_ini - 3, a_fin + 2, -semi - 2, semi + 2, y0 - 1, M.suelo)
  banda(a_ini, a_fin, -4, 4, y0 - 1, M.suelo2)

  -- Junta de neon a los dos lados de la alfombra
  if M.color then
    for a = a_ini, a_fin do
      local x1, z1 = pw(a, -5)
      local x2, z2 = pw(a, 5)
      poner(x1, y0 - 1, z1, neon(M.color, "bloque", M.luz))
      poner(x2, y0 - 1, z2, neon(M.color, "bloque", M.luz))
    end
  end

  -- Bandas transversales cada 6, para dar ritmo al recorrido
  for a = a_ini + 4, a_fin - 2, 6 do
    for l = -semi, semi do
      local x, z = pw(a, l)
      poner(x, y0 - 1, z, P.cromo)
    end
  end

  -- =========================================================================
  -- 2. MUROS LATERALES
  -- =========================================================================
  for lado = -1, 1, 2 do
    local l = lado * (semi + 1)
    for a = a_ini - 2, a_fin + 2 do
      for y = y0, y0 + h - 1 do
        local x, z = pw(a, l)
        poner(x, y, z, M.muro)
      end
      -- Trasdos, para que el muro tenga dos bloques y no se vea el vacio
      local x2, z2 = pw(a, lado * (semi + 2))
      for y = y0, y0 + h - 1 do
        poner(x2, y, z2, M.muro2)
      end
    end
    -- Zocalo
    for a = a_ini - 2, a_fin + 2 do
      for y = y0, y0 + 2 do
        local x, z = pw(a, l)
        poner(x, y, z, P.muro_oscuro)
      end
    end
  end

  -- =========================================================================
  -- 3. PILASTRAS Y FRISO
  -- =========================================================================
  for a = a_ini, a_fin, 4 do
    for lado = -1, 1, 2 do
      local x, z = pw(a, lado * semi)
      pilastra(x, y0, z, h - 3, M.col)
    end
  end

  -- Friso corrido a la altura de la vista, a los dos lados
  if M.color then
    for a = a_ini - 2, a_fin + 2 do
      for lado = -1, 1, 2 do
        local x, z = pw(a, lado * semi)
        poner(x, y0 + 6, z, neon(M.color, "bloque", M.luz))
      end
    end
  end

  -- =========================================================================
  -- 4. TECHO
  -- =========================================================================
  -- Boveda de canon, mas noble que un techo plano y mas barata de leer.
  boveda(cx, cz, fx, fz, sx, sz, a_ini - 2, a_fin + 2, semi + 1, y0 + h - semi - 2, M.muro)

  -- Nervios de la boveda cada 4
  for a = a_ini, a_fin, 4 do
    for l = -semi - 1, semi + 1 do
      local hh = redondear(sqrt(max(0, (semi+1)*(semi+1) - l*l)))
      local x, z = pw(a, l)
      poner(x, y0 + h - semi - 2 + hh, z, M.remate)
    end
  end

  -- Lucernarios entre nervios: una linea de vidrio en la clave
  for a = a_ini + 2, a_fin - 2, 4 do
    local x, z = pw(a, 0)
    poner(x, y0 + h - 1, z, M.vidrio)
    if M.color then
      local x2, z2 = pw(a + 1, 0)
      poner(x2, y0 + h - 1, z2, neon(M.color, "bloque", M.luz))
    end
  end

  -- =========================================================================
  -- 5. LAS OCHO ALCOBAS DE LIDER
  -- =========================================================================
  --
  -- Cuatro por lado, enfrentadas. Cada una es un nicho excavado en el muro
  -- con su arco, su columna a cada lado y LA PLATAFORMA VACIA en el centro.
  --
  -- Se numeran 1..8 en zigzag (1 izquierda, 2 derecha, 3 izquierda...) para
  -- que el recorrido sea alternado y no dos filas paralelas.

  local alcoba_prof = 5          -- lo que se mete en el muro
  local alcoba_ancho = 5
  local n_por_lado = 4
  local paso = floor((a_fin - a_ini - 12) / n_por_lado)

  local numero = 0
  for k = 0, n_por_lado - 1 do
    local a_centro = a_ini + 6 + k * paso
    for lado = -1, 1, 2 do
      numero = numero + 1
      local l_muro = lado * (semi + 1)

      -- Excavar el nicho
      for da = -floor(alcoba_ancho/2), floor(alcoba_ancho/2) do
        for dl = 0, alcoba_prof - 1 do
          for y = y0, y0 + 7 do
            local x, z = pw(a_centro + da, l_muro + lado * dl)
            poner(x, y, z, P.aire)
          end
        end
      end

      -- Fondo y laterales del nicho
      for da = -floor(alcoba_ancho/2) - 1, floor(alcoba_ancho/2) + 1 do
        for y = y0 - 1, y0 + 8 do
          local x, z = pw(a_centro + da, l_muro + lado * alcoba_prof)
          poner(x, y, z, M.muro2)
        end
      end
      for dl = 0, alcoba_prof do
        for y = y0 - 1, y0 + 8 do
          local x1, z1 = pw(a_centro - floor(alcoba_ancho/2) - 1, l_muro + lado * dl)
          local x2, z2 = pw(a_centro + floor(alcoba_ancho/2) + 1, l_muro + lado * dl)
          poner(x1, y, z1, M.muro)
          poner(x2, y, z2, M.muro)
        end
      end

      -- Suelo del nicho
      for da = -floor(alcoba_ancho/2), floor(alcoba_ancho/2) do
        for dl = 0, alcoba_prof - 1 do
          local x, z = pw(a_centro + da, l_muro + lado * dl)
          poner(x, y0 - 1, z, M.suelo2)
        end
      end

      -- Techo del nicho
      for da = -floor(alcoba_ancho/2), floor(alcoba_ancho/2) do
        for dl = 0, alcoba_prof - 1 do
          local x, z = pw(a_centro + da, l_muro + lado * dl)
          poner(x, y0 + 8, z, M.remate)
        end
      end

      -- Columnas del hueco, en el plano del muro
      for _, da in ipairs({ -floor(alcoba_ancho/2) - 1, floor(alcoba_ancho/2) + 1 }) do
        local x, z = pw(a_centro + da, l_muro)
        columna(x, y0, z, 9, M.col, M.remate)
      end

      -- Arco del hueco
      for da = -floor(alcoba_ancho/2) - 1, floor(alcoba_ancho/2) + 1 do
        local dda = acotar(da, -floor(alcoba_ancho/2), floor(alcoba_ancho/2))
        local hh = redondear(sqrt(max(0, (alcoba_ancho/2)^2 - dda*dda)))
        local x, z = pw(a_centro + da, l_muro)
        poner(x, y0 + 8 + hh, z, M.remate)
      end

      -- Fondo iluminado: el panel de color detras del entrenador. Es lo que
      -- recorta su silueta y hace que se vea desde el otro extremo del ala.
      if M.color then
        for da = -floor(alcoba_ancho/2), floor(alcoba_ancho/2) do
          for y = y0 + 1, y0 + 6 do
            local x, z = pw(a_centro + da, l_muro + lado * (alcoba_prof - 1))
            poner(x, y, z, neon(M.color, "panel", M.luz))
          end
        end
      end

      -- LA PLATAFORMA. Vacia, como se decidio.
      local px, pz = pw(a_centro, l_muro + lado * 2)
      plataforma_entrenador(px, y0 - 1, pz, 1, M.color or "gris",
                            M.luz or 0, M.remate, M.suelo2)

      -- Numero de la alcoba, marcado en el suelo con neon delante del hueco
      if M.color then
        local nx, nz = pw(a_centro, l_muro - lado * 1)
        poner(nx, y0 - 1, nz, neon(M.color, "bloque", M.luz))
      end
    end
  end

  -- =========================================================================
  -- 6. EL ESTRADO DEL CAMPEON
  -- =========================================================================
  -- Al fondo del ala, elevado y bajo un baldaquino. Es el unico punto del ala
  -- donde el techo sube, para que se lea como destino.

  local a_estrado = a_fin - 4
  local ex, ez = pw(a_estrado, 0)

  -- Ensanche de la sala del fondo
  banda(a_fin - 10, a_fin + 2, -semi - 1, semi + 1, y0 - 1, M.suelo2)

  -- Tarima
  estrado_campeon(ex, y0 - 1, ez, 9, 7, fx, fz, M.color or "gris",
                  M.luz or 0, M.remate, M.suelo)

  -- Baldaquino: cuatro columnas altas y un dosel
  for _, d in ipairs({ {-5,-4}, {-5,4}, {5,-4}, {5,4} }) do
    local bx, bz = pw(a_estrado + d[1], d[2])
    if M.color then
      columna_neon(bx, y0, bz, 14, M.col, M.color, M.luz)
    else
      columna(bx, y0, bz, 14, M.col, M.remate)
    end
  end
  -- Dosel
  for da = -5, 5 do
    for l = -4, 4 do
      local x, z = pw(a_estrado + da, l)
      poner(x, y0 + 14, z, M.remate)
      if abs(da) <= 3 and abs(l) <= 2 then
        poner(x, y0 + 14, z, M.vidrio)
      end
    end
  end
  -- Corona de neon del dosel
  if M.color then
    for da = -5, 5 do
      poner(select(1, pw(a_estrado + da, -4)), y0 + 15, select(2, pw(a_estrado + da, -4)), neon(M.color, "losa", M.luz))
      poner(select(1, pw(a_estrado + da,  4)), y0 + 15, select(2, pw(a_estrado + da,  4)), neon(M.color, "losa", M.luz))
    end
  end

  -- Muro de fondo del ala, con un gran ventanal
  for l = -semi - 1, semi + 1 do
    for y = y0, y0 + h - 1 do
      local x, z = pw(a_fin + 3, l)
      poner(x, y, z, M.muro)
    end
  end
  local vx, vz = pw(a_fin + 3, 0)
  ventanal_arco(vx, y0 + 3, vz, redondear(sx), redondear(sz), 9, 7, M.remate, M.vidrio)

  -- =========================================================================
  -- 7. EL SELLO — solo si el ala no esta activa
  -- =========================================================================
  --
  -- ⚠ Es lo que convierte una limitacion en una promesa. Un ala vacia y
  -- abierta se lee como "esto esta sin terminar"; un ala sellada tras vidrio,
  -- iluminada por dentro y con un cartel, se lee como "esto ESTA HECHO y
  -- todavia no te toca". Es exactamente el argumento de generations.md §4 que
  -- no se pudo aplicar en la Pokedex, y que aqui si cabe.

  if M.sellada then
    local a_sello = a_ini - 1
    for l = -semi, semi do
      for y = y0, y0 + 11 do
        local x, z = pw(a_sello, l)
        poner(x, y, z, M.vidrio)
      end
    end
    -- Bastidor del sello
    for l = -semi - 1, semi + 1 do
      local x, z = pw(a_sello, l)
      poner(x, y0 + 12, z, M.remate)
      poner(x, y0 - 1, z, M.remate)
    end
    for y = y0 - 1, y0 + 12 do
      local x1, z1 = pw(a_sello, -semi - 1)
      local x2, z2 = pw(a_sello, semi + 1)
      poner(x1, y, z1, M.remate)
      poner(x2, y, z2, M.remate)
    end
    -- Barra de neon del sello, a la altura de la vista
    if M.color then
      for l = -semi, semi do
        local x, z = pw(a_sello, l)
        poner(x, y0 + 5, z, neon(M.color, "bloque", M.luz))
      end
    end
  end
end


-- --------------------------------------------------------------------------
-- 7.7 · LA CRIPTA — los equipos
-- --------------------------------------------------------------------------
--
-- Bajo el palacio, sin conexion visual con las alas. Aqui van los 159
-- entrenadores de las bandas: Rocket, Galactic, Aqua, Magma y el despacho de
-- Giovanni.
--
-- ⚠ EL TONO ES EL CONTRARIO AL DE ARRIBA, y es deliberado. La planta noble es
-- blanca, alta y con luz que sale de la estructura. La cripta es baja, de
-- hormigon oscuro, con la luz colgada y rasante. Si la cripta se construyera
-- con la misma paleta, seria "mas palacio" y perderia todo el sentido: son los
-- que se esconden.

local function construir_cripta(cx, cz)
  local y0 = CFG.cripta_y
  local r  = CFG.cripta_radio
  local h  = CFG.cripta_alto

  -- Suelo y techo
  disco(cx, cz, r, y0 - 1, P.asfalto)
  disco(cx, cz, r, y0 + h, P.muro_oscuro)

  -- Muro perimetral
  pared_cilindrica(cx, cz, r, y0, y0 + h - 1, P.muro_negro)
  pared_cilindrica(cx, cz, r - 1, y0, y0 + h - 1, P.grafito)

  -- Pilares de la sala: dos anillos
  columnata_circular(cx, cz, r - 10, y0, h, 12, P.grafito_pilar, P.acero_osc)
  columnata_circular(cx, cz, r - 22, y0, h, 8, P.grafito_pilar, P.acero_osc)

  -- Vigas entre pilares
  for i = 0, 11 do
    local a1 = i * (2 * pi / 12)
    local a2 = (i + 1) * (2 * pi / 12)
    local x1, z1 = polar(cx, cz, r - 10, a1)
    local x2, z2 = polar(cx, cz, r - 10, a2)
    linea(x1, z1, x2, z2, y0 + h - 1, P.acero_osc)
  end

  -- Suelo central: un damero de asfalto, con el circulo de combate marcado
  suelo_damero(cx, cz, 14, y0 - 1, P.asfalto, P.asfalto_claro, 3)
  circulo(cx, cz, 14, y0 - 1, neon("rojo", "bloque", 1))
  circulo(cx, cz, 15, y0 - 1, P.acero_osc)

  -- --- Las cinco salas de banda -----------------------------------------
  -- Reparto radial, como arriba pero con cinco sectores en vez de diez.
  local n = #EQUIPOS
  for i, eq in ipairs(EQUIPOS) do
    local a = (i - 1) * (2 * pi / n) - pi / 2
    local fx, fz, sx, sz = base_ala(a)

    local function pw(av, l) return pt_ala(cx, cz, fx, fz, sx, sz, av, l) end

    -- Nicho de la banda, excavado hacia fuera
    for av = r - 9, r - 1 do
      for l = -6, 6 do
        for y = y0, y0 + 8 do
          local x, z = pw(av, l)
          poner(x, y, z, P.aire)
        end
      end
    end
    -- Fondo de color de la banda
    for l = -6, 6 do
      for y = y0, y0 + 8 do
        local x, z = pw(r - 1, l)
        poner(x, y, z, HORM[eq.color].panel)
      end
    end
    -- Franja de acento
    for l = -6, 6 do
      local x, z = pw(r - 1, l)
      poner(x, y0 + 4, z, neon(eq.acento, "bloque", 1))
    end
    -- Suelo del nicho
    for av = r - 9, r - 2 do
      for l = -6, 6 do
        local x, z = pw(av, l)
        poner(x, y0 - 1, z, P.terrazo_osc)
      end
    end
    -- Techo
    for av = r - 9, r - 2 do
      for l = -6, 6 do
        local x, z = pw(av, l)
        poner(x, y0 + 9, z, P.grafito)
      end
    end
    -- Jambas del nicho
    for y = y0, y0 + 9 do
      local x1, z1 = pw(r - 9, -7)
      local x2, z2 = pw(r - 9, 7)
      poner(x1, y, z1, P.acero_osc_pil)
      poner(x2, y, z2, P.acero_osc_pil)
    end

    -- Tres plataformas por banda: el jefe al fondo y dos secuaces delante
    local jx, jz = pw(r - 3, 0)
    plataforma_entrenador(jx, y0 - 1, jz, 2, eq.acento, 1, P.acero_osc, P.grafito)
    for _, l in ipairs({ -4, 4 }) do
      local px, pz = pw(r - 6, l)
      plataforma_entrenador(px, y0 - 1, pz, 1, eq.color, 0, P.acero_osc, P.grafito)
    end

    -- Aplique de pared a cada lado
    aplique(select(1, pw(r - 8, -6)), y0 + 5, select(2, pw(r - 8, -6)),
            redondear(sx), redondear(sz), eq.acento, 1, P.acero_osc)
  end

  -- --- Acceso ------------------------------------------------------------
  -- Un pozo con escalera de caracol desde la planta noble. Va descentrado a
  -- proposito: entrar a la cripta no debe ser lo primero que se encuentra.
  local px, pz = polar(cx, cz, CFG.r_nucleo - 8, pi / 4)
  cilindro(px, pz, 5, y0, CFG.y_base, P.aire)
  tubo(px, pz, 5, 6, y0, CFG.y_base, P.grafito)
  caracol(px, pz, 4, y0, CFG.y_base, 3.5, 3, P.rejilla)
  for y = y0 + 4, CFG.y_base - 4, 6 do
    circulo(px, pz, 5, y, neon("rojo", "bloque", 1))
  end
end


-- --------------------------------------------------------------------------
-- 7.8 · LA CORONA — el Alto Mando
-- --------------------------------------------------------------------------
--
-- Un anillo abierto sobre la cupula, a cielo descubierto, con diez tronos
-- mirando hacia dentro. Se llega por el pozo de luz.
--
-- Va SIN techo a proposito: es el unico punto del palacio desde el que se ve
-- la luna entera y de cerca. Cubrirlo seria tirar la mejor vista del edificio.

local function construir_corona(cx, cz)
  local y0 = CFG.corona_y
  local r  = CFG.corona_radio
  local n  = #REGIONES

  -- Plataforma anular
  anillo(cx, cz, 8, r, y0 - 1, P.suelo)
  anillo(cx, cz, r - 3, r, y0 - 1, P.terrazo)
  circulo(cx, cz, r, y0 - 1, P.cromo)
  circulo(cx, cz, 8, y0 - 1, P.cromo)

  -- Canto: se ve desde abajo, asi que se escalona igual que el basamento
  for i = 0, 3 do
    anillo(cx, cz, r - 2 - i, r - i, y0 - 2 - i, P.muro_alt)
  end
  circulo(cx, cz, r, y0 - 2, neon("blanco", "bloque", 1))

  -- Balaustrada exterior
  balaustrada_circular(cx, cz, r, y0, P.cromo_pilar, P.cromo, 2)
  -- Barandilla interior, alrededor del pozo
  barandilla_circular(cx, cz, 8, y0, P.cromo, P.cromo)

  -- Los diez tronos, uno por region, en el mismo angulo que su ala
  for i = 1, n do
    local reg = REGIONES[i]
    local a = angulo_ala(i, n)
    local fx, fz, sx, sz = base_ala(a)
    local function pw(av, l) return pt_ala(cx, cz, fx, fz, sx, sz, av, l) end

    local M = materiales_ala(reg)
    local luz = M.luz or 0
    local color = M.color or "gris"

    -- Peana del trono
    for av = r - 9, r - 3 do
      for l = -4, 4 do
        local x, z = pw(av, l)
        poner(x, y0 - 1, z, M.suelo2 or P.terrazo)
      end
    end
    -- Respaldo: un paramento alto con el color de la region
    for l = -4, 4 do
      for y = y0, y0 + 9 do
        local x, z = pw(r - 2, l)
        poner(x, y, z, M.muro2)
      end
    end
    -- Remate del respaldo en arco
    for l = -4, 4 do
      local hh = redondear(sqrt(max(0, 16 - l * l)))
      local x, z = pw(r - 2, l)
      poner(x, y0 + 9 + hh, z, M.remate)
    end
    -- Vena de luz
    if M.color then
      for y = y0 + 2, y0 + 7 do
        local x, z = pw(r - 2, 0)
        poner(x, y, z, neon(M.color, "panel", luz))
      end
    end
    -- Columnas flanqueando
    for _, l in ipairs({ -5, 5 }) do
      local x, z = pw(r - 2, l)
      columna(x, y0, z, 11, M.col, M.remate)
    end

    -- Cuatro pedestales: el Alto Mando de esa region
    for k = 0, 3 do
      local l = -4.5 + k * 3
      local x, z = pw(r - 6, redondear(l))
      pedestal(x, y0, z, 2, color, luz, M.remate)
    end
  end

  -- Remate del pozo de luz al llegar arriba
  tubo(cx, cz, 6, 7, y0 - 1, y0 + 4, P.cromo)
  circulo(cx, cz, 7, y0 + 4, neon("blanco", "bloque", 2))
end


-- --------------------------------------------------------------------------
-- 7.9 · EL PERIMETRO — contrafuertes y faros
-- --------------------------------------------------------------------------
--
-- Los diez vertices del decagono. Es lo que dibuja la silueta del palacio
-- contra el cielo negro, y lo unico que se ve desde lejos.

local function construir_perimetro(cx, cz)
  local y0 = CFG.y_base
  local r  = CFG.r_perimetro
  local n  = 10

  -- Cornisa de coronacion de los muros exteriores
  cornisa_poligonal(cx, cz, r, n, y0 + CFG.alto_planta, P.cromo, P.cromo, -pi / 2)

  -- Contrafuertes en los vertices, que son los puntos ENTRE alas
  local v = vertices(cx, cz, r - 1, n, -pi / 2 + pi / n)
  for i, p in ipairs(v) do
    local x, z = redondear(p.x), redondear(p.z)
    -- Machon
    columna_gruesa(x, y0 - 4, z, CFG.alto_planta + 6, P.muro, P.cromo)
    -- Y el faro encima
    if CFG.hacer.faros then
      faro(x, y0 + CFG.alto_planta + 2, z, 18, "blanco", 2, P.cromo)
    end
  end
end


-- --------------------------------------------------------------------------
-- 7.10 · ROTULOS — una fuente de bloques
-- --------------------------------------------------------------------------
--
-- Cada ala se rotula con el nombre de su region sobre el portal.
--
-- ⚠ NO SE PUEDEN USAR CARTELES NI text_display. El Lua Script Brush de Axiom
-- solo coloca BLOQUES: no invoca entidades ni escribe NBT. Asi que las letras
-- se dibujan con bloques, que ademas encaja mucho mejor con la escala del
-- edificio -- un cartel de madera en un vano de 12 de alto no se leeria.
--
-- Fuente de 5x7. Cada glifo son siete cadenas de cinco caracteres; `#` es
-- bloque y cualquier otra cosa es hueco. Es tediosa de escribir y se escribe
-- UNA vez.

local FUENTE = {
  ["A"] = { " ### ", "#   #", "#   #", "#####", "#   #", "#   #", "#   #" },
  ["B"] = { "#### ", "#   #", "#   #", "#### ", "#   #", "#   #", "#### " },
  ["C"] = { " ### ", "#   #", "#    ", "#    ", "#    ", "#   #", " ### " },
  ["D"] = { "#### ", "#   #", "#   #", "#   #", "#   #", "#   #", "#### " },
  ["E"] = { "#####", "#    ", "#    ", "#### ", "#    ", "#    ", "#####" },
  ["F"] = { "#####", "#    ", "#    ", "#### ", "#    ", "#    ", "#    " },
  ["G"] = { " ### ", "#   #", "#    ", "#  ##", "#   #", "#   #", " ### " },
  ["H"] = { "#   #", "#   #", "#   #", "#####", "#   #", "#   #", "#   #" },
  ["I"] = { " ### ", "  #  ", "  #  ", "  #  ", "  #  ", "  #  ", " ### " },
  ["J"] = { "  ###", "   # ", "   # ", "   # ", "   # ", "#  # ", " ##  " },
  ["K"] = { "#   #", "#  # ", "# #  ", "##   ", "# #  ", "#  # ", "#   #" },
  ["L"] = { "#    ", "#    ", "#    ", "#    ", "#    ", "#    ", "#####" },
  ["M"] = { "#   #", "## ##", "# # #", "#   #", "#   #", "#   #", "#   #" },
  ["N"] = { "#   #", "##  #", "# # #", "#  ##", "#   #", "#   #", "#   #" },
  ["O"] = { " ### ", "#   #", "#   #", "#   #", "#   #", "#   #", " ### " },
  ["P"] = { "#### ", "#   #", "#   #", "#### ", "#    ", "#    ", "#    " },
  ["Q"] = { " ### ", "#   #", "#   #", "#   #", "# # #", "#  # ", " ## #" },
  ["R"] = { "#### ", "#   #", "#   #", "#### ", "# #  ", "#  # ", "#   #" },
  ["S"] = { " ####", "#    ", "#    ", " ### ", "    #", "    #", "#### " },
  ["T"] = { "#####", "  #  ", "  #  ", "  #  ", "  #  ", "  #  ", "  #  " },
  ["U"] = { "#   #", "#   #", "#   #", "#   #", "#   #", "#   #", " ### " },
  ["V"] = { "#   #", "#   #", "#   #", "#   #", "#   #", " # # ", "  #  " },
  ["W"] = { "#   #", "#   #", "#   #", "#   #", "# # #", "## ##", "#   #" },
  ["X"] = { "#   #", "#   #", " # # ", "  #  ", " # # ", "#   #", "#   #" },
  ["Y"] = { "#   #", "#   #", " # # ", "  #  ", "  #  ", "  #  ", "  #  " },
  ["Z"] = { "#####", "    #", "   # ", "  #  ", " #   ", "#    ", "#####" },
  ["0"] = { " ### ", "#   #", "#  ##", "# # #", "##  #", "#   #", " ### " },
  ["1"] = { "  #  ", " ##  ", "  #  ", "  #  ", "  #  ", "  #  ", " ### " },
  ["2"] = { " ### ", "#   #", "    #", "   # ", "  #  ", " #   ", "#####" },
  ["3"] = { "#####", "   # ", "  #  ", "   # ", "    #", "#   #", " ### " },
  ["4"] = { "   # ", "  ## ", " # # ", "#  # ", "#####", "   # ", "   # " },
  ["5"] = { "#####", "#    ", "#### ", "    #", "    #", "#   #", " ### " },
  ["6"] = { "  ## ", " #   ", "#    ", "#### ", "#   #", "#   #", " ### " },
  ["7"] = { "#####", "    #", "   # ", "  #  ", " #   ", " #   ", " #   " },
  ["8"] = { " ### ", "#   #", "#   #", " ### ", "#   #", "#   #", " ### " },
  ["9"] = { " ### ", "#   #", "#   #", " ####", "    #", "   # ", " ##  " },
  [" "] = { "     ", "     ", "     ", "     ", "     ", "     ", "     " },
  ["-"] = { "     ", "     ", "     ", "#####", "     ", "     ", "     " },
  ["."] = { "     ", "     ", "     ", "     ", "     ", "  ## ", "  ## " },
  [":"] = { "     ", "  ## ", "  ## ", "     ", "  ## ", "  ## ", "     " },
  ["'"] = { "  #  ", "  #  ", "     ", "     ", "     ", "     ", "     " },
}

--- Ancho total de un rotulo, en bloques.
local function ancho_rotulo(texto, separacion)
  separacion = separacion or 1
  return #texto * (5 + separacion) - separacion
end

--- Dibuja texto en un plano vertical.
---   (x,y,z)      esquina de arranque (izquierda, abajo)
---   dirx,dirz    hacia donde avanza el texto
---   b            bloque de las letras
--- El texto se dibuja de arriba abajo segun la fuente, asi que `y` es la base.
local function rotulo(x, y, z, dirx, dirz, texto, b, separacion)
  separacion = separacion or 1
  local cursor = 0
  for i = 1, #texto do
    local ch = string.upper(string.sub(texto, i, i))
    local g = FUENTE[ch]
    if g then
      for fila = 1, 7 do
        local linea_f = g[fila]
        for col = 1, 5 do
          if string.sub(linea_f, col, col) == "#" then
            local off = cursor + (col - 1)
            poner(x + dirx * off, y + (7 - fila), z + dirz * off, b)
          end
        end
      end
    end
    cursor = cursor + 5 + separacion
  end
end

--- Rotulo centrado respecto a un punto.
local function rotulo_centrado(cx, y, cz, dirx, dirz, texto, b, separacion)
  local w = ancho_rotulo(texto, separacion)
  local off = floor(w / 2)
  rotulo(cx - dirx * off, y, cz - dirz * off, dirx, dirz, texto, b, separacion)
end

--- Rotula cada ala sobre su portal, desde dentro de la rotonda.
local function construir_rotulos(cx, cz)
  local n = #REGIONES
  local y = CFG.y_base + 12
  for i = 1, n do
    local reg = REGIONES[i]
    local M = materiales_ala(reg)
    local a = angulo_ala(i, n)
    -- Un poco por dentro del muro del nucleo, para que se lea desde la rotonda
    local x, z = polar(cx, cz, CFG.r_nucleo - 1, a)
    local dirx, dirz = redondear(-sin(a)), redondear(cos(a))
    if dirx == 0 and dirz == 0 then dirz = 1 end
    local b = M.color and neon(M.color, "bloque", M.luz) or P.grafito
    rotulo_centrado(x, y, z, dirx, dirz, reg.nombre, b, 1)
  end
end



-- --------------------------------------------------------------------------
-- 7.11 · LA GALERIA PERIMETRAL — el ambulatorio
-- --------------------------------------------------------------------------
--
-- ⚠ SIN ESTO LAS DIEZ ALAS SON CALLEJONES SIN SALIDA.
--
-- Es el fallo de recorrido que tiene toda planta radial mal resuelta: entras
-- por la rotonda, recorres un ala hasta el fondo, y para ver la siguiente
-- tienes que desandarla entera. Con diez alas, eso son veinte recorridos
-- completos para verlo todo.
--
-- El ambulatorio es un pasillo anular que une los fondos de las diez alas por
-- fuera. Convierte el edificio en un circuito: se puede entrar por una, salir
-- por la de al lado y seguir dando la vuelta. Es exactamente lo que hace la
-- girola de una catedral, y por el mismo motivo.
--
-- Va por FUERA de los estrados, no por dentro: si cortara el ala, el campeon
-- dejaria de ser el fondo del recorrido y perderia su condicion de destino.

--- ⚠ EL RADIO NO ES LIBRE: TIENE QUE QUEDAR FUERA DE LAS ALAS.
---
--- La primera version lo puso en `r_alas + 5` con 7 de ancho, o sea ocupando
--- de 82 a 89 -- y el ala llega hasta 86, con su muro de fondo en 87. El
--- anillo ATRAVESABA el ala justo por encima del estrado del campeon.
---
--- No se vio leyendo el codigo: se vio en el banco de pruebas, mirando la caja
--- envolvente y comparandola con los radios. Es el fallo tipico de una planta
--- radial -- cada pieza se disena en su sistema de coordenadas y nadie
--- comprueba que no se pisen.
---
--- Ahora arranca en `r_alas + 7` (91) y el ala acaba en `r_alas + 3` (87), con
--- los cuatro bloques de holgura ocupados por los pasos de conexion.
local function construir_ambulatorio(cx, cz)
  local y0 = CFG.y_base
  local ancho = 7
  local r  = CFG.r_alas + 7 + ancho      -- borde EXTERIOR del anillo
  local h  = 12

  -- Suelo del anillo
  anillo(cx, cz, r - ancho, r, y0 - 1, P.suelo)
  anillo(cx, cz, r - ancho + 1, r - 1, y0 - 1, P.terrazo)
  circulo(cx, cz, r, y0 - 1, P.cromo)
  circulo(cx, cz, r - ancho, y0 - 1, P.cromo)

  -- Muro exterior con ventanales
  pared_cilindrica(cx, cz, r, y0, y0 + h - 1, P.muro)
  pared_cilindrica(cx, cz, r + 1, y0, y0 + h - 1, P.muro_alt)
  zocalo_circular(cx, cz, r + 1, y0, 2, P.muro_oscuro)

  -- Muro interior: solo entre alas, porque enfrente de cada ala hay que dejar
  -- el paso abierto.
  local n = #REGIONES
  for i = 1, n do
    local a1 = angulo_ala(i, n) + (2 * pi / n) * 0.18
    local a2 = angulo_ala(i + 1 > n and 1 or (i + 1), n) - (2 * pi / n) * 0.18
    if a2 < a1 then a2 = a2 + 2 * pi end
    local pasos = max(4, redondear((a2 - a1) * (r - ancho)))
    for k = 0, pasos do
      local a = lerp(a1, a2, k / pasos)
      local x, z = polar(cx, cz, r - ancho, a)
      for y = y0, y0 + h - 1 do
        poner(x, y, z, P.muro_alt)
      end
    end
  end

  -- Columnata del ambulatorio: cuarenta columnas, cuatro por tramo
  columnata_circular(cx, cz, r - 2, y0, h - 2, 40, P.cromo_pilar, P.cromo)

  -- Boveda anular: se resuelve como una sucesion de tramos rectos cortos.
  -- Una boveda anular exacta no aporta nada a esta escala y cuesta el triple.
  for i = 0, 119 do
    local a = i * (2 * pi / 120)
    for w = 0, ancho do
      local rr = r - w
      local x, z = polar(cx, cz, rr, a)
      local l = w - ancho / 2
      local hh = redondear(sqrt(max(0, (ancho/2)^2 - l*l)))
      poner(x, y0 + h - 4 + hh, z, P.muro)
    end
  end

  -- Ventanales al exterior, uno entre cada par de columnas
  for i = 0, 39 do
    local a = (i + 0.5) * (2 * pi / 40)
    local x, z = polar(cx, cz, r, a)
    local dirx, dirz = -sin(a), cos(a)
    ventanal_arco(x, y0 + 2, z, dirx, dirz, 3, 4, P.cromo, P.vidrio)
  end

  -- Friso corrido de neon blanco: es lo que hace legible el anillo de noche
  friso_neon_circular(cx, cz, r - 1, y0 + 5, "blanco", 1)

  -- Cornisa
  cornisa_circular(cx, cz, r + 1, y0 + h - 1, P.cromo, P.cromo)

  -- Bancos corridos contra el muro exterior, entre ventanales
  for i = 0, 39 do
    local a = i * (2 * pi / 40)
    local x, z = polar(cx, cz, r - 1, a)
    poner(x, y0, z, losa(P.terrazo, "bottom"))
  end

  -- --- LOS PASOS DE CONEXION --------------------------------------------
  --
  -- Sin esto el ambulatorio es un anillo precioso al que no se puede entrar, y
  -- las alas siguen siendo callejones sin salida -- o sea, no habria servido
  -- de nada.
  --
  -- Van DOS por ala, flanqueando el eje, de modo que se pasa por DETRAS del
  -- estrado del campeon. Es exactamente lo que hace la girola de una catedral
  -- alrededor del altar: se rodea sin cruzarlo.
  local r_int = r - ancho
  for i = 1, n do
    local reg = REGIONES[i]
    local M = materiales_ala(reg)
    local a = angulo_ala(i, n)
    local fx, fz, sx, sz = base_ala(a)

    for _, l in ipairs({ -6, 6 }) do
      -- Vaciar el paso desde el muro de fondo del ala hasta el anillo
      for av = CFG.r_alas + 2, r_int + 1 do
        for w = -2, 2 do
          for y = y0, y0 + 6 do
            local x, z = pt_ala(cx, cz, fx, fz, sx, sz, av, l + w)
            poner(x, y, z, P.aire)
          end
          local x, z = pt_ala(cx, cz, fx, fz, sx, sz, av, l + w)
          poner(x, y0 - 1, z, M.suelo or P.suelo)
          poner(x, y0 + 7, z, P.muro_alt)
        end
        -- Jambas del paso
        for y = y0, y0 + 7 do
          local x1, z1 = pt_ala(cx, cz, fx, fz, sx, sz, av, l - 3)
          local x2, z2 = pt_ala(cx, cz, fx, fz, sx, sz, av, l + 3)
          poner(x1, y, z1, P.muro)
          poner(x2, y, z2, P.muro)
        end
      end
      -- Y una cinta de luz en el techo del paso, del color de la region
      if M.color then
        for av = CFG.r_alas + 2, r_int + 1, 2 do
          local x, z = pt_ala(cx, cz, fx, fz, sx, sz, av, l)
          poner(x, y0 + 7, z, neon(M.color, "bloque", M.luz))
        end
      end
    end
  end
end


-- --------------------------------------------------------------------------
-- 7.12 · EMBLEMAS REGIONALES
-- --------------------------------------------------------------------------
--
-- Cada ala lleva su emblema en el suelo, a la entrada. Es lo que identifica el
-- ala desde lejos sin tener que leer el rotulo, y lo que hace que el suelo no
-- sea una alfombra de color y ya.
--
-- Se dibujan en una rejilla de 15x15 con dos niveles de intensidad:
--     #  color pleno (neon)
--     +  color apagado (hormigon del color)
--     .  fondo (pavimento)
--
-- No son los logos oficiales de cada region: son figuras geometricas que
-- evocan su tema. Copiar los logos seria material con dueno, y el proyecto ya
-- tiene una regla sobre eso (D-008).

local EMBLEMAS = {
  -- Kanto: la espiral de una Poke Ball estilizada, la region original
  kanto = {
    "......###......",
    "...##.....##...",
    "..#.........#..",
    ".#....###....#.",
    ".#...#...#...#.",
    "#....#...#....#",
    "###############",
    "#....#...#....#",
    "#....#####....#",
    ".#...........#.",
    ".#....###....#.",
    "..#.........#..",
    "...##.....##...",
    "......###......",
    "...............",
  },
  -- Johto: el sol naciente sobre las montanas
  johto = {
    ".......#.......",
    "...#...#...#...",
    "....#..#..#....",
    "......###......",
    "....#######....",
    "...#########...",
    "..###########..",
    "...#########...",
    "....#######....",
    "......###......",
    "...............",
    "......+++......",
    "....++++++++...",
    "..++++++++++++.",
    "+++++++++++++++",
  },
  -- Hoenn: la doble espiral de tierra y mar
  hoenn = {
    "...............",
    "..#######......",
    ".#.......#.....",
    "#....###..#....",
    "#...#...#..#...",
    "#..#.....#.#...",
    "#..#..+..#.#...",
    "#..#.....#.#...",
    "...#...#..#..#.",
    "....###..#...#.",
    ".....#..#....#.",
    "......##....#..",
    "..........##...",
    "......####.....",
    "...............",
  },
  -- Sinnoh: el diamante y la perla, la montana en medio
  sinnoh = {
    ".......#.......",
    "......###......",
    ".....#####.....",
    "....#######....",
    "...#########...",
    "..###########..",
    ".#############.",
    "..###########..",
    "...#########...",
    "....#######....",
    ".....#####.....",
    "......###......",
    ".......#.......",
    "...............",
    "..++.......++..",
  },
  -- Hisui: el arco antiguo, region del pasado
  hisui = {
    "...............",
    "....#######....",
    "..##.......##..",
    ".#....###....#.",
    "#....#...#....#",
    "#...#.....#...#",
    "#...#..+..#...#",
    "#...#.....#...#",
    "#....#...#....#",
    "#.....###.....#",
    "#.............#",
    "#.+.+.+.+.+.+.#",
    "#.............#",
    "###############",
    "...............",
  },
  -- Teselia (Unova): los dos opuestos, blanco y negro
  unova = {
    "#######.#######",
    "#######.#######",
    "##.....#.....##",
    "#...+...+...+.#",
    "#.............#",
    "#....#####....#",
    "#...#.....#...#",
    "....#.....#....",
    "#...#.....#...#",
    "#....#####....#",
    "#.............#",
    "#.+...+...+...#",
    "##.....#.....##",
    "#######.#######",
    "#######.#######",
  },
  -- Kalos: la estrella de cinco puntas
  kalos = {
    ".......#.......",
    ".......#.......",
    "......###......",
    "......###......",
    "#######.#######",
    ".#############.",
    "..###########..",
    "...#########...",
    "...##.....##...",
    "..###.....###..",
    "..##.......##..",
    ".##.........##.",
    ".#...........#.",
    "...............",
    "...............",
  },
  -- Alola: el sol y la luna, las islas
  alola = {
    "...............",
    "....#####......",
    "..##.....##....",
    ".#.........#...",
    "#....###....#..",
    "#...#...#...#..",
    "#...#...#...#..",
    "#....###....#..",
    ".#.........#...",
    "..##.....##....",
    "....#####......",
    "...............",
    "..++..++..++...",
    ".++++.++++.++..",
    "...............",
  },
  -- Galar: la corona
  galar = {
    "...............",
    "#.....#.....#..",
    "#.....#.....#..",
    "##...###...##..",
    "#.#.#...#.#.#..",
    "#..#.....#..#..",
    "#...........#..",
    "#....+++....#..",
    "#...........#..",
    "#############..",
    "#############..",
    "#..+..+..+..#..",
    "#############..",
    "...............",
    "...............",
  },
  -- Paldea: el triangulo del descubrimiento
  paldea = {
    ".......#.......",
    "......#.#......",
    ".....#...#.....",
    "....#.....#....",
    "...#..###..#...",
    "..#..#...#..#..",
    ".#..#..+..#..#.",
    "#..#.......#..#",
    "#.#.........#.#",
    "##...........##",
    "#.............#",
    "###############",
    "...............",
    "..+++++++++++..",
    "...............",
  },
}

--- Dibuja el emblema de una region en el suelo, orientado con el ala.
local function emblema(cx, cz, indice, reg)
  local grid = EMBLEMAS[reg.id]
  if not grid then return end

  local n = #REGIONES
  local ang = angulo_ala(indice, n)
  local fx, fz, sx, sz = base_ala(ang)
  local M = materiales_ala(reg)
  local y = CFG.y_base - 1

  -- Se coloca a la entrada del ala, justo pasado el sello
  local a0 = CFG.r_nucleo + 8
  local filas = #grid
  local cols = #grid[1]

  for fila = 1, filas do
    local linea_e = grid[fila]
    for col = 1, cols do
      local ch = string.sub(linea_e, col, col)
      if ch ~= "." and ch ~= " " then
        local a = a0 + (filas - fila)
        local l = col - ceil(cols / 2)
        local x, z = pt_ala(cx, cz, fx, fz, sx, sz, a, l)
        local b
        if ch == "#" then
          b = M.color and neon(M.color, "bloque", M.luz) or P.grafito
        else
          b = M.color and HORM[M.color].pulido or P.muro_oscuro
        end
        poner(x, y, z, b)
      end
    end
  end

  -- Marco del emblema
  for l = -ceil(cols/2) - 1, ceil(cols/2) + 1 do
    local x1, z1 = pt_ala(cx, cz, fx, fz, sx, sz, a0 - 1, l)
    local x2, z2 = pt_ala(cx, cz, fx, fz, sx, sz, a0 + filas, l)
    poner(x1, y, z1, M.remate)
    poner(x2, y, z2, M.remate)
  end
  for a = a0 - 1, a0 + filas do
    local x1, z1 = pt_ala(cx, cz, fx, fz, sx, sz, a, -ceil(cols/2) - 1)
    local x2, z2 = pt_ala(cx, cz, fx, fz, sx, sz, a, ceil(cols/2) + 1)
    poner(x1, y, z1, M.remate)
    poner(x2, y, z2, M.remate)
  end
end

local function construir_emblemas(cx, cz)
  for i, reg in ipairs(REGIONES) do
    if ala_pedida(reg.id) then emblema(cx, cz, i, reg) end
  end
end


-- --------------------------------------------------------------------------
-- 7.13 · AGUA Y JARDINERAS
-- --------------------------------------------------------------------------
--
-- Las referencias que dieron pie a esto tenian vegetacion por todas partes, y
-- eso funciona en un palacio de piedra. Aqui hay que dosificarlo: la ciudadela
-- es de noche permanente y neon, y llenarla de plantas la convertiria en otra
-- cosa.
--
-- La solucion: agua y jardineras SOLO en la rotonda, que es el espacio de
-- estar. Las alas se quedan minerales. El contraste hace que la rotonda se
-- lea como el sitio donde se para uno.

local function construir_agua(cx, cz)
  local y0 = CFG.y_base
  local r  = CFG.r_nucleo

  -- Canal anular alrededor del pozo de luz
  anillo(cx, cz, 8, 11, y0 - 1, P.aire)
  anillo(cx, cz, 8, 11, y0 - 2, P.cromo)
  anillo(cx, cz, 8, 11, y0 - 1, P.agua)
  circulo(cx, cz, 7, y0 - 1, P.cromo)
  circulo(cx, cz, 12, y0 - 1, P.cromo)

  -- Cuatro puentes sobre el canal, alineados con las alas 1, 4, 6 y 9
  for _, i in ipairs({ 1, 4, 6, 9 }) do
    local a = angulo_ala(i, #REGIONES)
    for rr = 7, 12 do
      local x, z = polar(cx, cz, rr, a)
      for l = -1, 1 do
        local px, pz = polar(cx, cz, rr, a + l * 0.06)
        poner(px, y0 - 1, pz, P.suelo)
      end
    end
  end

  -- Luz bajo el agua: el canal se ilumina desde el fondo
  for i = 0, 59 do
    local a = i * (2 * pi / 60)
    local x, z = polar(cx, cz, 9, a)
    poner(x, y0 - 2, z, neon("azul_claro", "bloque", 1))
  end

  -- Jardineras entre las columnas de la rotonda
  local n = #REGIONES
  for i = 1, n do
    local a = angulo_ala(i, n) + (2 * pi / n) * 0.5
    local x, z = polar(cx, cz, r - 8, a)
    -- Cubeta
    for dx = -2, 2 do
      for dz = -2, 2 do
        if abs(dx) + abs(dz) <= 3 then
          poner(x + dx, y0, z + dz, P.cromo)
          poner(x + dx, y0 - 1, z + dz, P.terrazo)
        end
      end
    end
    for dx = -1, 1 do
      for dz = -1, 1 do
        poner(x + dx, y0, z + dz, P.aire)
      end
    end
    -- Borde de neon
    poner(x + 2, y0 + 1, z, neon("blanco", "losa", 1))
    poner(x - 2, y0 + 1, z, neon("blanco", "losa", 1))
    poner(x, y0 + 1, z + 2, neon("blanco", "losa", 1))
    poner(x, y0 + 1, z - 2, neon("blanco", "losa", 1))
  end
end


-- --------------------------------------------------------------------------
-- 7.14 · LA ENTREPLANTA
-- --------------------------------------------------------------------------
--
-- Una galeria alta corrida por los dos lados de cada ala, sobre las alcobas.
-- Cumple tres cosas a la vez:
--
--   1. Rompe la altura: 22 bloques de muro liso sobre las alcobas se leen
--      como un almacen por muy bien rematados que esten.
--   2. Da un punto de vista alto sobre el ala, que es desde donde mejor se ve
--      un combate en el estrado.
--   3. Justifica la boveda: sin la entreplanta, la boveda arranca demasiado
--      alto y no se relaciona con nada.

local function construir_entreplanta(cx, cz, indice, reg)
  local n = #REGIONES
  local ang = angulo_ala(indice, n)
  local fx, fz, sx, sz = base_ala(ang)
  local M = materiales_ala(reg)

  local y = CFG.y_base + 11
  local semi = floor(CFG.ala_ancho / 2)
  local a_ini = CFG.r_nucleo + 2
  local a_fin = CFG.r_alas

  local function pw(a, l) return pt_ala(cx, cz, fx, fz, sx, sz, a, l) end

  for lado = -1, 1, 2 do
    -- Forjado: tres bloques de vuelo desde el muro
    for a = a_ini, a_fin - 8 do
      for w = 0, 3 do
        local l = lado * (semi - w)
        local x, z = pw(a, l)
        poner(x, y, z, P.suelo_losa and losa(P.suelo, "top") or P.suelo)
      end
      -- Canto visto
      local x, z = pw(a, lado * (semi - 4))
      poner(x, y, z, M.remate)
    end

    -- Menssulas cada 4, que es lo que hace creible el vuelo
    for a = a_ini, a_fin - 8, 4 do
      local x, z = pw(a, lado * semi)
      poner(x, y - 1, z, escalera(M.remate, facing_de(sx * lado, sz * lado), "top"))
      poner(x, y - 2, z, losa(M.remate, "top"))
    end

    -- Balaustrada
    for a = a_ini, a_fin - 8 do
      local x, z = pw(a, lado * (semi - 4))
      if a % 2 == 0 then
        poner(x, y + 1, z, M.col)
      end
      poner(x, y + 2, z, losa(M.remate, "bottom"))
    end

    -- Vena de luz bajo el forjado: ilumina las alcobas de abajo sin que se
    -- vea la fuente, que es el mejor truco de iluminacion que hay.
    if M.color then
      for a = a_ini, a_fin - 8 do
        local x, z = pw(a, lado * (semi - 4))
        poner(x, y - 1, z, neon(M.color, "losa", M.luz))
      end
    end
  end

  -- Escaleras de subida, una a cada lado, junto al arranque del ala
  for lado = -1, 1, 2 do
    local a0 = a_ini + 1
    for i = 0, 11 do
      local l = lado * (semi - 1)
      local x, z = pw(a0 + i, l)
      poner(x, CFG.y_base + i, z, escalera(P.suelo_esc, facing_de(fx, fz), "bottom"))
      for yy = CFG.y_base + i - 3, CFG.y_base + i - 1 do
        poner(x, yy, z, M.muro2)
      end
    end
  end
end

local function construir_entreplantas(cx, cz)
  for i, reg in ipairs(REGIONES) do
    if ala_pedida(reg.id) then construir_entreplanta(cx, cz, i, reg) end
  end
end



-- --------------------------------------------------------------------------
-- 7.16 · LA FACHADA EXTERIOR
-- --------------------------------------------------------------------------
--
-- ⚠ ES LA CARA QUE MAS SE VE Y LA QUE SE OLVIDA SIEMPRE.
--
-- Todo lo anterior resuelve el interior. Pero el palacio flota sobre el vacio
-- en una dimension de noche permanente: desde la plaza, y desde cualquier
-- punto de la ciudadela, lo que se mira es el ANILLO EXTERIOR. Si se deja el
-- muro liso del ambulatorio, el edificio se lee como un deposito de agua por
-- muy bien resuelto que este por dentro.
--
-- Lo que se anade:
--   - un orden gigante de pilastras que abarca toda la altura
--   - los diez panos identificados con el color de su region
--   - una arqueria ciega entre pilastras
--   - el coronamiento con pinaculos
--
-- Los panos llevan el color del ala que hay detras. Eso convierte la fachada
-- en un indice: desde fuera se ve cuantas regiones estan vivas antes de entrar.

--- ⚠ Por fuera del ambulatorio, que ocupa hasta `r_alas + 14`.
local function construir_fachada(cx, cz)
  local y0 = CFG.y_base
  local r  = CFG.r_alas + 16
  local h  = 12
  local n  = #REGIONES

  -- Basamento de la fachada, escalonado hacia fuera
  for i = 0, 3 do
    anillo(cx, cz, r - 1, r + i, y0 - 2 - i, P.muro_alt)
  end
  circulo(cx, cz, r + 3, y0 - 5, P.acero_osc)

  -- Orden gigante: cuarenta pilastras que abarcan toda la altura
  for i = 0, 39 do
    local a = i * (2 * pi / 40)
    local x, z = polar(cx, cz, r, a)
    for y = y0, y0 + h + 3 do
      poner(x, y, z, pilar_eje(P.cromo_pilar, "y"))
    end
    -- Basa y capitel
    poner(x, y0 - 1, z, P.cromo)
    poner(x, y0 + h + 4, z, P.cromo)
    local x2, z2 = polar(cx, cz, r + 1, a)
    poner(x2, y0 + h + 4, z2, losa(P.cromo, "bottom"))
    poner(x2, y0 - 1, z2, losa(P.cromo, "top"))
  end

  -- Panos entre pilastras, con el color de la region que hay detras.
  -- Cada region ocupa cuatro de los cuarenta huecos.
  for i = 1, n do
    local reg = REGIONES[i]
    local M = materiales_ala(reg)
    local a_centro = angulo_ala(i, n)
    for k = -2, 1 do
      local a = a_centro + (k + 0.5) * (2 * pi / 40)
      -- Arqueria ciega
      for w = -1, 1 do
        local aa = a + w * 0.012
        local x, z = polar(cx, cz, r, aa)
        for y = y0 + 1, y0 + 8 do
          poner(x, y, z, M.muro2)
        end
        -- Arco
        local hh = redondear(sqrt(max(0, 4 - w * w)))
        poner(x, y0 + 9 + hh, z, M.remate)
      end
      -- Vena de luz en el centro del pano
      if M.color then
        local x, z = polar(cx, cz, r, a)
        for y = y0 + 3, y0 + 7 do
          poner(x, y, z, neon(M.color, "panel", M.luz))
        end
      end
    end
  end

  -- Entablamento corrido
  circulo(cx, cz, r, y0 + h + 5, P.muro)
  circulo(cx, cz, r, y0 + h + 6, P.nervio)
  anillo(cx, cz, r, r + 1, y0 + h + 7, losa(P.cromo, "bottom"))

  -- Friso de neon del coronamiento: la linea que dibuja el palacio de noche
  circulo(cx, cz, r + 1, y0 + h + 7, neon("blanco", "bloque", 2))
end


-- --------------------------------------------------------------------------
-- 7.17 · PINACULOS Y REMATES
-- --------------------------------------------------------------------------
--
-- La silueta. Un edificio que acaba en horizontal se lee como una caja; los
-- remates verticales son lo que le da perfil contra el cielo.

local function construir_pinaculos(cx, cz)
  local y0 = CFG.y_base + 12 + 8
  local r  = CFG.r_alas + 16
  local n  = #REGIONES

  -- Un pinaculo sobre cada eje de ala, del color de la region
  for i = 1, n do
    local reg = REGIONES[i]
    local M = materiales_ala(reg)
    local a = angulo_ala(i, n)
    local x, z = polar(cx, cz, r, a)

    -- Cuerpo
    for dy = 0, 8 do
      local rr = 2 - floor(dy / 4)
      for dx = -rr, rr do
        for dz = -rr, rr do
          if abs(dx) + abs(dz) <= rr + 1 then
            poner(x + dx, y0 + dy, z + dz, M.remate)
          end
        end
      end
    end
    -- Farol del remate
    if M.color then
      poner(x, y0 + 9, z, neon(M.color, "bloque", M.luz))
      poner(x, y0 + 10, z, neon(M.color, "bloque", M.luz))
      poner(x + 1, y0 + 9, z, neon(M.color, "losa", M.luz))
      poner(x - 1, y0 + 9, z, neon(M.color, "losa", M.luz))
      poner(x, y0 + 9, z + 1, neon(M.color, "losa", M.luz))
      poner(x, y0 + 9, z - 1, neon(M.color, "losa", M.luz))
    else
      poner(x, y0 + 9, z, P.grafito)
      poner(x, y0 + 10, z, P.grafito)
    end
    -- Aguja
    poner(x, y0 + 11, z, P.cromo_pilar)
    poner(x, y0 + 12, z, P.cromo_pilar)
  end

  -- Pinaculos menores entre alas, sin color
  for i = 1, n do
    local a = angulo_ala(i, n) + pi / n
    local x, z = polar(cx, cz, r, a)
    for dy = 0, 5 do
      poner(x, y0 + dy, z, pilar_eje(P.cromo_pilar, "y"))
    end
    poner(x, y0 + 6, z, neon("blanco", "bloque", 1))
    poner(x, y0 + 7, z, P.cromo)
  end
end


-- --------------------------------------------------------------------------
-- 7.18 · EL VESTIBULO
-- --------------------------------------------------------------------------
--
-- Lo que hay entre lo alto de la escalinata y el ambulatorio. Sin esta pieza,
-- el jugador sube diez escalones y aparece de golpe en un pasillo anular sin
-- saber hacia donde ir.
--
-- Es un portico de entrada rematado en fronton, con el eje puesto EXACTAMENTE
-- en la direccion de la escalinata, y detras un vano que perfora el
-- ambulatorio hasta ver la rotonda. Esa perforacion es lo importante: desde
-- la puerta se ve el pozo de luz al fondo, y eso orienta sin necesidad de un
-- solo cartel.

local function construir_vestibulo(cx, cz)
  local y0 = CFG.y_base
  local r_ext = CFG.r_alas + 16
  local ancho = 13
  local semi = floor(ancho / 2)

  -- El eje del vestibulo mira al norte (-Z), igual que la escalinata
  local zf = cz - r_ext

  -- Portico: seis columnas gigantes
  for _, l in ipairs({ -semi, -semi + 5, -semi + 10, semi - 10, semi - 5, semi }) do
    columna_gruesa(cx + l, y0, zf - 4, 18, P.cromo_pilar, P.cromo)
  end

  -- Suelo del portico
  rect(cx - semi - 2, zf - 7, cx + semi + 2, zf + 2, y0 - 1, P.terrazo)
  marco(cx - semi - 2, zf - 7, cx + semi + 2, zf + 2, y0 - 1, P.cromo)

  -- Entablamento del portico
  for x = cx - semi - 2, cx + semi + 2 do
    poner(x, y0 + 18, zf - 4, P.muro)
    poner(x, y0 + 19, zf - 4, P.nervio)
    poner(x, y0 + 20, zf - 4, losa(P.cromo, "bottom"))
  end

  -- Fronton triangular
  for i = 0, semi + 2 do
    local yy = y0 + 21 + i
    for x = cx - semi - 2 + i, cx + semi + 2 - i do
      poner(x, yy, zf - 4, P.muro_alt)
    end
    -- Bordes del fronton
    poner(cx - semi - 2 + i, yy, zf - 4, P.cromo)
    poner(cx + semi + 2 - i, yy, zf - 4, P.cromo)
  end
  -- Oculo del fronton
  circulo(cx, zf - 4, 3, y0 + 25, P.cromo)
  disco(cx, zf - 4, 2, y0 + 25, neon("blanco", "bloque", 2))

  -- Perforacion hacia el interior: el vano que deja ver el pozo de luz
  caja(cx - 4, y0, zf - 6, cx + 4, y0 + 9, cz - CFG.r_nucleo - 1, P.aire)
  -- Jambas de la perforacion
  for z = zf - 6, cz - CFG.r_nucleo - 1 do
    for y = y0, y0 + 10 do
      poner(cx - 5, y, z, P.muro)
      poner(cx + 5, y, z, P.muro)
    end
    poner(cx - 5, y0 + 5, z, neon("blanco", "bloque", 1))
    poner(cx + 5, y0 + 5, z, neon("blanco", "bloque", 1))
    poner(cx - 4, y0 + 10, z, P.cromo)
    poner(cx + 4, y0 + 10, z, P.cromo)
  end
  -- Techo del vano
  for z = zf - 6, cz - CFG.r_nucleo - 1 do
    for x = cx - 4, cx + 4 do
      poner(x, y0 + 11, z, P.muro_alt)
    end
  end
  -- Suelo del vano: alfombra que apunta al centro
  for z = zf - 6, cz - CFG.r_nucleo - 1 do
    for x = cx - 4, cx + 4 do
      poner(x, y0 - 1, z, P.terrazo)
    end
    poner(cx - 2, y0 - 1, z, neon("blanco", "bloque", 1))
    poner(cx + 2, y0 - 1, z, neon("blanco", "bloque", 1))
  end
end


-- --------------------------------------------------------------------------
-- 7.19 · ILUMINACION DE REPASO
-- --------------------------------------------------------------------------
--
-- Una pasada final que reparte luz por donde ha quedado oscuro.
--
-- ⚠ SE HACE AL FINAL Y A PROPOSITO. La luz de Minecraft NO TIENE COLOR: el
-- motor guarda un numero 0-15 y ya (docs/world/neon.md §1). Un neon cian
-- ILUMINA EN BLANCO. Asi que el color hay que ponerlo con el material y la
-- luz con la cantidad, y son dos decisiones separadas.
--
-- En las alas latentes y reservadas NO se anade luz: que esten oscuras es la
-- informacion que transmiten.

local function construir_iluminacion(cx, cz)
  local y0 = CFG.y_base
  local n  = #REGIONES

  -- Apliques en el ambulatorio
  local r = CFG.r_alas + 14
  for i = 0, 39 do
    local a = i * (2 * pi / 40)
    local x, z = polar(cx, cz, r - 2, a)
    poner(x, y0 + 6, z, neon("blanco", "losa", 2))
  end

  -- Balizas de suelo en la rotonda
  for i = 0, 19 do
    local a = i * (2 * pi / 20)
    local x, z = polar(cx, cz, CFG.r_nucleo - 4, a)
    poner(x, y0 - 1, z, neon("blanco", "bloque", 2))
  end

  -- En las alas ACTIVAS: luz de refuerzo en el eje del pasillo
  for i = 1, n do
    local reg = REGIONES[i]
    if ala_pedida(reg.id) and reg.estado == "activa" then
      local ang = angulo_ala(i, n)
      local fx, fz, sx, sz = base_ala(ang)
      for a = CFG.r_nucleo + 4, CFG.r_alas - 2, 6 do
        local x, z = pt_ala(cx, cz, fx, fz, sx, sz, a, 0)
        poner(x, y0 + CFG.alto_planta - 3, z, neon(reg.color, "bloque", 2))
      end
    end
  end
end



-- --------------------------------------------------------------------------
-- 7.20 · EL ACCESO A LA CORONA
-- --------------------------------------------------------------------------
--
-- ⚠ ESTO FALTABA, y es el tipo de fallo que solo se ve recorriendo el
-- edificio mentalmente: la corona esta a y=124 y no habia NINGUNA forma de
-- subir. Diez tronos del Alto Mando inalcanzables.
--
-- Se resuelve con una escalera de caracol que envuelve el pozo de luz. Es la
-- solucion correcta y no un parche: el pozo ya era el eje vertical del
-- edificio y lo unico que lo atravesaba de arriba abajo. Darle una escalera
-- convierte un elemento decorativo en la circulacion principal.
--
-- La escalera es de REJILLA a proposito. Con peldanos macizos, el pozo de luz
-- quedaria tapado desde abajo y se perderia la vista que lo justifica; la
-- rejilla deja pasar la luz y se ve el hueco entero.

local function construir_acceso_corona(cx, cz)
  local y1 = CFG.y_base
  local y2 = CFG.corona_y
  local total = y2 - y1
  if total <= 0 then return end

  -- Hueco para la escalera: un anillo alrededor del tubo de vidrio
  tubo(cx, cz, 6, 9, y1, y2, P.aire)

  -- Camisa exterior del hueco, con celosia para que no sea un tubo ciego
  for y = y1, y2 do
    for i = 0, 47 do
      local a = i * (2 * pi / 48)
      local x, z = polar(cx, cz, 10, a)
      -- Celosia: se cala uno de cada tres, alternando por altura
      if (i + y) % 3 ~= 0 then
        poner(x, y, z, P.rejilla_panel or P.rejilla)
      else
        poner(x, y, z, P.cromo)
      end
    end
  end

  -- La escalera propiamente dicha: tres vueltas y media
  local vueltas = 3.5
  for i = 0, total do
    local t = i / total
    local a = t * vueltas * 2 * pi
    for w = 0, 2 do
      local x, z = polar(cx, cz, 7 + w, a)
      poner(x, y1 + i, z, P.rejilla)
    end
    -- Pasamanos exterior
    local hx, hz = polar(cx, cz, 9.6, a)
    poner(hx, y1 + i + 1, hz, P.cromo)
    -- Y una luz cada ocho peldanos
    if i % 8 == 0 then
      local lx, lz = polar(cx, cz, 9, a)
      poner(lx, y1 + i + 2, lz, neon("blanco", "losa", 1))
    end
  end

  -- Rellanos cada 16, para que la subida no sea un caracol continuo de 50
  for y = y1 + 16, y2 - 8, 16 do
    anillo(cx, cz, 7, 9, y, P.suelo)
    circulo(cx, cz, 9, y, P.cromo)
    -- Ventanuco del rellano
    for i = 0, 3 do
      local a = i * (pi / 2)
      local x, z = polar(cx, cz, 10, a)
      for dy = 1, 3 do
        poner(x, y + dy, z, P.vidrio)
      end
    end
  end

  -- Embocadura abajo: un arco que invita a subir
  local ax, az = polar(cx, cz, 10, -pi / 2)
  portal(ax, y1, az, 1, 0, 3, 5, P.cromo, "blanco", 2)

  -- Y arriba, la salida a la corona
  tubo(cx, cz, 6, 10, y2 - 1, y2 + 1, P.aire)
  anillo(cx, cz, 6, 10, y2 - 2, P.suelo)
end


-- --------------------------------------------------------------------------
-- 7.21 · SENALIZACION DEL SUELO
-- --------------------------------------------------------------------------
--
-- Bandas de color incrustadas en el pavimento del ambulatorio que llevan a
-- cada ala. Es lo que hace que no haga falta un mapa: el suelo lleva.
--
-- Se usa el mismo recurso que los aeropuertos y los hospitales, y funciona
-- por el mismo motivo -- el jugador no tiene que leer nada, solo seguir su
-- color.

local function construir_senalizacion(cx, cz)
  local y = CFG.y_base - 1
  local n = #REGIONES
  local r_amb = CFG.r_alas + 14

  for i = 1, n do
    local reg = REGIONES[i]
    if ala_pedida(reg.id) then
      local M = materiales_ala(reg)
      if M.color then
        local a = angulo_ala(i, n)
        -- Del ambulatorio hacia la boca del ala
        for rr = r_amb - 7, r_amb do
          local x, z = polar(cx, cz, rr, a)
          poner(x, y, z, neon(M.color, "bloque", M.luz))
        end
        -- Y un tramo del anillo, en los dos sentidos, que se apaga al
        -- alejarse: indica "por aqui se llega" sin llenar el suelo de rayas
        for k = 1, 10 do
          for signo = -1, 1, 2 do
            local aa = a + signo * k * 0.02
            local x, z = polar(cx, cz, r_amb - 3, aa)
            if k % 2 == 1 then
              poner(x, y, z, neon(M.color, "bloque", M.luz))
            end
          end
        end
      end
    end
  end

  -- Y en la rotonda: un radio de color desde el centro a cada portal
  for i = 1, n do
    local reg = REGIONES[i]
    if ala_pedida(reg.id) then
      local M = materiales_ala(reg)
      if M.color then
        local a = angulo_ala(i, n)
        for rr = 13, CFG.r_nucleo - 1 do
          local x, z = polar(cx, cz, rr, a)
          if rr % 2 == 0 then
            poner(x, y, z, neon(M.color, "bloque", M.luz))
          end
        end
      end
    end
  end
end



-- --------------------------------------------------------------------------
-- 7.22 · LAS TORRES DE LOS VERTICES
-- --------------------------------------------------------------------------
--
-- Diez torres octogonales en los vertices del decagono, o sea en los puntos
-- ENTRE alas. Vienen directamente de las referencias que dieron pie al
-- proyecto -- los dos torreones que flanquean la fachada del palacio de las
-- imagenes -- pero reinterpretadas: octogonales en vez de cuadradas, de cromo
-- y vidrio en vez de ladrillo, y con la luz por dentro.
--
-- Cumplen tres funciones, y esa es la razon de que valgan lo que cuestan:
--
--   1. SILUETA. Es lo que se ve desde la plaza, a 200 bloques. Sin ellas el
--      palacio es una tarta plana con una bola encima.
--   2. ESCALA. Dan una referencia vertical contra la que medir la cupula. Un
--      edificio sin elementos verticales no se sabe como de grande es.
--   3. ESQUINA. Un decagono tiene diez esquinas y hay que resolverlas: el
--      encuentro entre dos panos de fachada en angulo es feo si no hay una
--      pieza que lo absorba. La torre ES esa pieza.
--
-- Llevan escalera interior y mirador, asi que ademas se pueden subir.

--- Un anillo octogonal a una altura. Se usa para el fuste de la torre.
local function octogono(cx, cz, r, y, b, giro)
  anillo_poligonal(cx, cz, r - 1, r, 8, y, b, giro or 0)
end

--- Una torre completa.
local function torre(cx, cz, y_base_t, alto, color, b_muro, b_remate, con_escalera)
  local r = 5
  local y_top = y_base_t + alto

  -- --- Basamento: tres retallos hacia fuera ------------------------------
  for i = 0, 2 do
    poligono(cx, cz, r + 2 - i, 8, y_base_t - 3 + i, b_remate, 0)
  end

  -- --- Fuste --------------------------------------------------------------
  for y = y_base_t, y_top - 8 do
    octogono(cx, cz, r, y, b_muro, 0)
  end
  -- Las ocho aristas, marcadas con pilar
  for i = 0, 7 do
    local a = i * (2 * pi / 8) + pi / 8
    local x, z = polar(cx, cz, r - 1, a)
    for y = y_base_t, y_top - 8 do
      poner(x, y, z, pilar_eje(b_remate, "y"))
    end
  end

  -- --- Ventanas: cuatro alturas, cuatro caras ----------------------------
  for nivel = 0, 3 do
    local yv = y_base_t + 4 + nivel * 7
    if yv < y_top - 12 then
      for i = 0, 3 do
        local a = i * (pi / 2) + pi / 8
        local x, z = polar(cx, cz, r, a)
        for dy = 0, 3 do
          poner(x, yv + dy, z, P.vidrio)
        end
        -- Dintel y alfeizar
        poner(x, yv - 1, z, b_remate)
        poner(x, yv + 4, z, b_remate)
      end
      -- Anillo de forjado a cada nivel
      octogono(cx, cz, r - 1, yv - 2, b_remate, 0)
    end
  end

  -- --- Cinta de neon en vertical, en las cuatro caras libres --------------
  if color then
    for i = 0, 3 do
      local a = i * (pi / 2) + pi / 8 + pi / 4
      local x, z = polar(cx, cz, r, a)
      for y = y_base_t + 2, y_top - 11 do
        if y % 4 ~= 0 then
          poner(x, y, z, neon(color, "panel", 1))
        end
      end
    end
  end

  -- --- Escalera interior --------------------------------------------------
  if con_escalera then
    -- Vaciar el interior
    for y = y_base_t, y_top - 9 do
      poligono(cx, cz, r - 2, 8, y, P.aire, 0)
    end
    caracol(cx, cz, r - 2, y_base_t, y_top - 10, 3.0, 2, P.rejilla)
  end

  -- --- Cornisa y mirador --------------------------------------------------
  local ym = y_top - 8
  for i = 0, 2 do
    poligono(cx, cz, r + i, 8, ym + i, b_remate, 0)
  end
  -- Suelo del mirador
  poligono(cx, cz, r + 1, 8, ym + 3, P.suelo, 0)
  -- Antepecho
  anillo_poligonal(cx, cz, r + 1, r + 2, 8, ym + 4, b_remate, 0)
  anillo_poligonal(cx, cz, r + 1, r + 2, 8, ym + 5, losa(b_remate, "bottom"), 0)
  -- Columnitas del mirador
  for i = 0, 7 do
    local a = i * (2 * pi / 8)
    local x, z = polar(cx, cz, r + 1, a)
    for y = ym + 4, ym + 7 do
      poner(x, y, z, pilar_eje(b_remate, "y"))
    end
  end

  -- --- Chapitel -----------------------------------------------------------
  poligono(cx, cz, r + 2, 8, ym + 8, b_remate, 0)
  cono(cx, ym + 9, cz, r + 1, 7, b_muro)
  -- Nervios del chapitel
  for i = 0, 7 do
    local a = i * (2 * pi / 8) + pi / 8
    for j = 0, 6 do
      local rr = (r + 1) * (1 - j / 7)
      if rr >= 0.6 then
        local x, z = polar(cx, cz, rr, a)
        poner(x, ym + 9 + j, z, b_remate)
      end
    end
  end

  -- --- Farol del remate ---------------------------------------------------
  poner(cx, ym + 16, cz, neon(color or "blanco", "bloque", 2))
  poner(cx, ym + 17, cz, neon(color or "blanco", "bloque", 2))
  poner(cx, ym + 18, cz, P.cromo_pilar)
  poner(cx, ym + 19, cz, P.cromo_pilar)
end

local function construir_torres(cx, cz)
  local n = #REGIONES
  local r = CFG.r_alas + 16
  local y0 = CFG.y_base
  local alto = CFG.alto_planta + 22

  for i = 1, n do
    -- En los vertices, o sea ENTRE alas
    local a = angulo_ala(i, n) + pi / n
    local x, z = polar(cx, cz, r, a)
    -- Solo la mitad llevan escalera, para no perforar el edificio de mas
    torre(x, z, y0 - 3, alto, "blanco", P.muro, P.cromo, (i % 2 == 1))
  end
end


-- --------------------------------------------------------------------------
-- 7.23 · CONTRAFUERTES VOLADOS
-- --------------------------------------------------------------------------
--
-- Arbotantes que salen de las torres y van a morir contra el tambor de la
-- cupula, por encima de las alas.
--
-- ⚠ ESTRUCTURALMENTE SON MENTIRA, Y ESO ESTA BIEN. En una catedral el
-- arbotante contrarresta el empuje de la boveda; aqui no hay fisica que
-- contrarrestar. Lo que hacen es VISUAL: atan la cupula al perimetro y
-- rellenan el vacio que queda sobre las alas, que es donde el edificio se
-- veia mas flojo desde fuera.
--
-- Un edificio puede permitirse un elemento que solo hace falta para que se
-- entienda. Lo que no puede es tener un hueco donde el ojo espera estructura.

local function construir_arbotantes(cx, cz)
  local n = #REGIONES
  local r_torre = CFG.r_alas + 16
  local r_tambor = CFG.r_nucleo + 2
  local y_torre = CFG.y_base + CFG.alto_planta + 4
  local y_tambor = CFG.y_base + CFG.alto_planta + 8

  for i = 1, n do
    local a = angulo_ala(i, n) + pi / n     -- desde el vertice, no desde el ala
    local x1, z1 = polar(cx, cz, r_torre - 4, a)
    local x2, z2 = polar(cx, cz, r_tambor, a)

    -- El arco: se interpola en planta y se le da una parabola en altura, para
    -- que suba y luego baje en vez de ser una rampa recta.
    local pasos = redondear(dist2(x2 - x1, z2 - z1))
    if pasos > 2 then
      for k = 0, pasos do
        local t = k / pasos
        local x = redondear(lerp(x1, x2, t))
        local z = redondear(lerp(z1, z2, t))
        -- Parabola: 0 en los extremos, maximo en el centro
        local combadura = 7 * (1 - (2 * t - 1) ^ 2)
        local y = redondear(lerp(y_torre, y_tambor, t) + combadura)
        poner(x, y, z, P.cromo)
        poner(x, y - 1, z, P.muro_alt)
        -- Los montantes que bajan del arbotante, cada 5
        if k % 5 == 0 and k > 0 and k < pasos then
          for dy = 1, 3 do
            poner(x, y - 1 - dy, z, P.cromo_pilar)
          end
          poner(x, y - 5, z, neon("blanco", "bloque", 1))
        end
      end
      -- Cinta de luz corrida por el trasdos
      for k = 0, pasos do
        local t = k / pasos
        local x = redondear(lerp(x1, x2, t))
        local z = redondear(lerp(z1, z2, t))
        local combadura = 7 * (1 - (2 * t - 1) ^ 2)
        local y = redondear(lerp(y_torre, y_tambor, t) + combadura)
        if k % 3 == 0 then
          poner(x, y + 1, z, neon("blanco", "losa", 1))
        end
      end
    end
  end
end



-- --------------------------------------------------------------------------
-- 7.24 · EL MOSAICO DEL AMBULATORIO
-- --------------------------------------------------------------------------
--
-- El suelo del anillo, dividido en diez sectores con el color de la region que
-- tienen detras. Es la pieza que convierte el ambulatorio en un indice: al
-- recorrerlo, el suelo va cambiando de color y cada cambio anuncia un ala.
--
-- Los sectores de las regiones dormidas van en gris. No es que "falte" el
-- color: es que todavia no lo tienen, y se nota al pisarlo.

local function construir_mosaico(cx, cz)
  local y = CFG.y_base - 1
  local n = #REGIONES
  local ancho = 7
  local r_ext = CFG.r_alas + 7 + ancho
  local r_int = r_ext - ancho

  for i = 1, n do
    local reg = REGIONES[i]
    local M = materiales_ala(reg)
    local a_c = angulo_ala(i, n)
    local media = pi / n

    -- Cada sector se dibuja punto a punto sobre el anillo
    local pasos = redondear(2 * media * r_ext) + 2
    for k = 0, pasos do
      local a = a_c - media + (2 * media) * (k / pasos)
      for rr = r_int, r_ext do
        local x, z = polar(cx, cz, rr, a)
        local b
        if M.color then
          -- Damero de dos intensidades del mismo color
          if damero(x, z, 3) then
            b = HORM[M.color].pulido
          else
            b = HORM[M.color].panel
          end
        else
          b = damero(x, z, 3) and P.terrazo_osc or P.asfalto
        end
        poner(x, y, z, b)
      end
    end

    -- Junta entre sectores: una linea de cromo radial
    for _, borde in ipairs({ a_c - media, a_c + media }) do
      for rr = r_int, r_ext do
        local x, z = polar(cx, cz, rr, borde)
        poner(x, y, z, P.cromo)
      end
    end

    -- Y una banda del color en el borde interior, que es la que se ve al
    -- caminar mirando al frente
    if M.color then
      for k = 0, pasos do
        local a = a_c - media + (2 * media) * (k / pasos)
        local x, z = polar(cx, cz, r_int + 1, a)
        poner(x, y, z, neon(M.color, "bloque", M.luz))
      end
    end
  end

  -- Cenefa exterior corrida
  circulo(cx, cz, r_ext - 1, y, P.cromo)
  circulo(cx, cz, r_int, y, P.cromo)
end



-- --------------------------------------------------------------------------
-- 7.26 · EL PASEO DE ACCESO
-- --------------------------------------------------------------------------
--
-- ⚠ SIN ESTO NO SE PUEDE LLEGAR AL PALACIO. NI ANDANDO NI DE NINGUNA FORMA.
--
-- Es el fallo mas grave que quedaba, y es invisible leyendo el codigo: cada
-- pieza estaba bien, pero entre la plaza y el edificio hay VACIO.
--
--     la plaza de la ciudadela   es una isla de 56x56  ->  llega hasta z = -28
--     el pie de la escalinata    esta en                   z = -114
--     en medio                                             86 bloques de nada
--
-- La ciudadela flota sobre el vacio (CLAUDE.md §0), asi que "el suelo" no
-- existe: hay que construirlo. Sin este paseo, el palacio es un edificio
-- perfecto al que solo se llega volando en creativo -- y los jugadores no
-- vuelan.
--
-- Se resuelve con una calzada elevada de 15 de ancho, con pretil, farolas y
-- tres descansillos. Va a la altura de la plaza (se camina en 64) y muere en
-- el arranque de la escalinata.
--
-- Los descansillos no son decoracion: 86 bloques de pasillo recto sin nada se
-- hacen larguisimos. Partirlo en tramos con un ensanche cada 25 cambia por
-- completo la sensacion de longitud, que es el mismo truco de cualquier puente
-- largo de verdad.

local function construir_paseo(cx, cz)
  local y = 64                       -- la cota de la plaza: se camina aqui
  local y_suelo = y - 1
  local ancho = 15
  local mitad = floor(ancho / 2)

  -- Del borde de la plaza al pie de la escalinata
  local z_ini = cz - 28              -- borde de la isla de 56x56
  local z_fin = cz - CFG.r_perimetro - (CFG.y_base - 64)

  if z_fin >= z_ini then return end   -- ya se tocan: no hace falta paseo

  -- --- Tablero ------------------------------------------------------------
  for z = z_fin, z_ini do
    for l = -mitad, mitad do
      poner(cx + l, y_suelo, z, P.suelo)
    end
    -- Banda central mas clara, que guia la vista hacia el palacio
    poner(cx, y_suelo, z, P.terrazo)
    poner(cx - 1, y_suelo, z, P.terrazo)
    poner(cx + 1, y_suelo, z, P.terrazo)
    -- Cantos
    poner(cx - mitad, y_suelo, z, P.cromo)
    poner(cx + mitad, y_suelo, z, P.cromo)
  end

  -- --- Estructura por debajo ---------------------------------------------
  -- Se ve desde abajo, igual que el basamento: se escalona.
  for z = z_fin, z_ini do
    for i = 1, 3 do
      for l = -mitad + i, mitad - i do
        poner(cx + l, y_suelo - i, z, (i % 2 == 0) and P.muro_alt or P.muro)
      end
    end
  end

  -- Nervios transversales cada 6, colgando
  for z = z_fin, z_ini, 6 do
    for l = -mitad, mitad do
      poner(cx + l, y_suelo - 4, z, P.acero_osc)
    end
    -- Y una menssula a cada lado
    poner(cx - mitad - 1, y_suelo - 1, z, escalera(P.acero_osc, "east", "top"))
    poner(cx + mitad + 1, y_suelo - 1, z, escalera(P.acero_osc, "west", "top"))
  end

  -- --- Pretil -------------------------------------------------------------
  for z = z_fin, z_ini do
    for _, l in ipairs({ -mitad, mitad }) do
      if z % 2 == 0 then
        poner(cx + l, y, z, P.cromo_pilar)
      end
      poner(cx + l, y + 1, z, losa(P.cromo, "bottom"))
    end
  end

  -- --- Farolas ------------------------------------------------------------
  for z = z_fin + 6, z_ini - 4, 12 do
    for _, l in ipairs({ -mitad - 1, mitad + 1 }) do
      faro(cx + l, y, z, 9, "blanco", 2, P.cromo)
    end
  end

  -- --- Descansillos -------------------------------------------------------
  -- Tres ensanches redondos que parten el recorrido.
  local largo = z_ini - z_fin
  for k = 1, 3 do
    local zc = redondear(z_fin + largo * (k / 4))
    disco(cx, zc, 11, y_suelo, P.suelo)
    circulo(cx, zc, 11, y_suelo, P.cromo)
    circulo(cx, zc, 10, y_suelo, P.terrazo)
    -- Anillo de luz en el suelo
    circulo(cx, zc, 8, y_suelo, neon("blanco", "bloque", 1))
    -- Estructura debajo
    for i = 1, 4 do
      disco(cx, zc, 11 - i, y_suelo - i, P.muro_alt)
    end
    -- Pretil del descansillo, dejando abiertos los dos accesos
    for i = 0, 47 do
      local a = i * (2 * pi / 48)
      local px, pz = polar(cx, zc, 11, a)
      -- Se deja hueco donde entra y sale la calzada
      if abs(px - cx) > mitad then
        if i % 2 == 0 then poner(px, y, pz, P.cromo_pilar) end
        poner(px, y + 1, pz, losa(P.cromo, "bottom"))
      end
    end
    -- Cuatro faroles
    for i = 0, 3 do
      local a = i * (pi / 2) + pi / 4
      local px, pz = polar(cx, zc, 9, a)
      faro(px, y, pz, 7, "blanco", 2, P.cromo)
    end
  end

  -- --- Arranque en la plaza ----------------------------------------------
  -- Un pequeno umbral que anuncia el paseo, ya sobre la isla
  rect(cx - mitad - 2, z_ini - 1, cx + mitad + 2, z_ini + 3, y_suelo, P.terrazo)
  marco(cx - mitad - 2, z_ini - 1, cx + mitad + 2, z_ini + 3, y_suelo, P.cromo)
  for _, l in ipairs({ -mitad - 2, mitad + 2 }) do
    columna(cx + l, y, z_ini + 1, 10, P.cromo_pilar, P.cromo)
  end
  -- Dintel entre las dos columnas
  for l = -mitad - 2, mitad + 2 do
    poner(cx + l, y + 10, z_ini + 1, P.cromo)
  end
  for l = -mitad, mitad do
    poner(cx + l, y + 11, z_ini + 1, neon("blanco", "bloque", 2))
  end
end


-- --------------------------------------------------------------------------
-- 7.15 · PRESETS DE ESCALA
-- --------------------------------------------------------------------------
--
-- Tres tamanos. No es solo multiplicar: al reducir hay que quitar piezas, no
-- encogerlas, porque una alcoba de 3 de ancho deja de ser una alcoba.
--
--   maqueta   para juzgar la planta rapido, sin detalle
--   normal    el del diseno
--   epico     para cuando la ciudadela tenga sitio de sobra
--
-- Se aplica poniendo CFG.preset antes de ejecutar.

local PRESETS = {
  -- ⚠ r_nucleo=26 y no 24: (12+4)*10/(2*pi) = 25,5. Con 24 las alas se
  -- solapaban, y lo dijo `comprobar_geometria()` -- no se vio construyendo.
  maqueta = {
    r_nucleo = 26, r_alas = 52, r_perimetro = 72, ala_ancho = 12,
    alto_planta = 16, luna_radio = 12, luna_y = 108,
    corona_y = 92, corona_radio = 22,
    cripta_y = 52, cripta_radio = 32, cripta_alto = 10,
  },
  normal = {
    r_nucleo = 36, r_alas = 84, r_perimetro = 104, ala_ancho = 18,
    alto_planta = 22, luna_radio = 21, luna_y = 150,
    corona_y = 124, corona_radio = 34,
    cripta_y = 48, cripta_radio = 46, cripta_alto = 14,
  },
  epico = {
    r_nucleo = 48, r_alas = 116, r_perimetro = 136, ala_ancho = 24,
    alto_planta = 30, luna_radio = 30, luna_y = 200,
    corona_y = 164, corona_radio = 46,
    cripta_y = 36, cripta_radio = 62, cripta_alto = 18,
  },
}

--- Aplica un preset sobre CFG. Se llama al principio del main.
local function aplicar_preset(nombre)
  local p = PRESETS[nombre]
  if not p then return false end
  for k, v in pairs(p) do CFG[k] = v end
  return true
end



-- --------------------------------------------------------------------------
-- 7.25 · LOS TESTEROS — las alas vistas desde el ambulatorio
-- --------------------------------------------------------------------------
--
-- Al mover el ambulatorio hacia fuera aparecio una cara nueva que antes no
-- existia: el MURO DE FONDO DE CADA ALA, visto desde el anillo. Son diez
-- panos de 20 de ancho por 12 de alto, y sin tratar quedan como diez paredes
-- ciegas con dos agujeros -- justo enfrente de por donde pasa todo el mundo.
--
-- Es el efecto secundario tipico de mover una pieza: se arregla un problema
-- (las alas eran callejones) y se crea una superficie que nadie habia
-- disenado. Merece la pena mirarlo cada vez que se mueve algo.
--
-- Cada testero lleva:
--   - un frontispicio con el emblema de la region, otra vez, pero en vertical
--   - el nombre en letras de bloque
--   - los dos pasos ya abiertos, ahora enmarcados como puertas
--   - y el estado del ala, legible sin leer: encendido, apagado o en obra

local function construir_testero(cx, cz, indice, reg)
  local n = #REGIONES
  local ang = angulo_ala(indice, n)
  local fx, fz, sx, sz = base_ala(ang)
  local M = materiales_ala(reg)
  local y0 = CFG.y_base
  local a_muro = CFG.r_alas + 3

  local function pw(a, l) return pt_ala(cx, cz, fx, fz, sx, sz, a, l) end

  -- --- Paramento del testero ---------------------------------------------
  for l = -11, 11 do
    for y = y0 - 1, y0 + 13 do
      local x, z = pw(a_muro, l)
      poner(x, y, z, M.muro2)
    end
  end

  -- Zocalo
  for l = -11, 11 do
    for y = y0 - 1, y0 + 1 do
      local x, z = pw(a_muro, l)
      poner(x, y, z, P.muro_oscuro)
    end
  end

  -- --- Pilastras que enmarcan --------------------------------------------
  for _, l in ipairs({ -11, -3, 3, 11 }) do
    for y = y0, y0 + 13 do
      local x, z = pw(a_muro, l)
      poner(x, y, z, pilar_eje(M.col, "y"))
    end
    local x, z = pw(a_muro, l)
    poner(x, y0 + 14, z, M.remate)
  end

  -- --- Enmarcado de los dos pasos ----------------------------------------
  for _, lc in ipairs({ -6, 6 }) do
    -- Arco sobre el hueco
    for l = -4, 4 do
      local ll = acotar(l, -3, 3)
      local hh = redondear(sqrt(max(0, 9 - ll * ll)))
      local x, z = pw(a_muro, lc + l)
      poner(x, y0 + 8 + hh, z, M.remate)
    end
    -- Jambas resaltadas
    for _, l in ipairs({ -4, 4 }) do
      for y = y0, y0 + 8 do
        local x, z = pw(a_muro, lc + l)
        poner(x, y, z, M.col)
      end
    end
    -- Y la luz del dintel, que es lo que dice si se puede pasar
    if M.color then
      for l = -3, 3 do
        local x, z = pw(a_muro, lc + l)
        poner(x, y0 + 12, z, neon(M.color, "bloque", M.luz))
      end
    end
  end

  -- --- El escudo central --------------------------------------------------
  -- Entre los dos pasos queda un pano de 7 de ancho: ahi va el emblema, esta
  -- vez en vertical y a media escala (se toman filas alternas de la rejilla).
  local grid = EMBLEMAS[reg.id]
  if grid then
    local filas = #grid
    local cols = #grid[1]
    for fila = 1, filas, 2 do
      local linea_e = grid[fila]
      for col = 1, cols, 2 do
        local ch = string.sub(linea_e, col, col)
        if ch ~= "." and ch ~= " " then
          local l = redondear((col - ceil(cols / 2)) / 2)
          local yy = y0 + 10 - redondear(fila / 2)
          if abs(l) <= 2 and yy >= y0 + 2 then
            local x, z = pw(a_muro - 1, l)
            local b
            if ch == "#" then
              b = M.color and neon(M.color, "bloque", M.luz) or P.grafito
            else
              b = M.remate
            end
            poner(x, yy, z, b)
          end
        end
      end
    end
    -- Marco del escudo
    for l = -3, 3 do
      local x1, z1 = pw(a_muro - 1, l)
      poner(x1, y0 + 11, z1, M.remate)
      poner(x1, y0 + 1, z1, M.remate)
    end
    for y = y0 + 1, y0 + 11 do
      local x1, z1 = pw(a_muro - 1, -3)
      local x2, z2 = pw(a_muro - 1, 3)
      poner(x1, y, z1, M.remate)
      poner(x2, y, z2, M.remate)
    end
  end

  -- --- Cornisa del testero ------------------------------------------------
  for l = -12, 12 do
    local x, z = pw(a_muro, l)
    poner(x, y0 + 14, z, M.remate)
    local x2, z2 = pw(a_muro - 1, l)
    poner(x2, y0 + 15, z2, losa(M.remate, "bottom"))
  end

  -- --- El nombre, en letras de bloque -------------------------------------
  -- Va por DENTRO del ambulatorio, o sea en la cara que mira al anillo.
  local dirx, dirz = redondear(sx), redondear(sz)
  if dirx == 0 and dirz == 0 then dirz = 1 end
  local nx, nz = pw(a_muro + 1, 0)
  local b_letra = M.color and neon(M.color, "bloque", M.luz) or P.grafito
  rotulo_centrado(nx, y0 + 16, nz, dirx, dirz, reg.nombre, b_letra, 1)
end

local function construir_testeros(cx, cz)
  for i, reg in ipairs(REGIONES) do
    if ala_pedida(reg.id) then construir_testero(cx, cz, i, reg) end
  end
end


-- ###########################################################################
-- ##  8. COMPROBACIONES                                                    ##
-- ###########################################################################
--
-- Se ejecutan ANTES de tocar un bloque. Construir medio palacio y descubrir
-- luego que la paleta no resolvia obliga a deshacer 300 000 bloques.

--- Imprime, si Axiom expone algo donde imprimir. No esta garantizado, asi que
--- va envuelto: que no haya consola no puede tumbar la construccion.
local function decir(msg)
  pcall(function() print(msg) end)
end

--- ¿Resolvio la paleta? Si `lunaneon` no esta instalado, TODOS sus bloques
--- salen nil y el palacio seria un esqueleto invisible.
local function comprobar_paleta()
  if #FALLOS == 0 then
    decir("Paleta: los " .. tostring(#COLORES * 6 + 40) .. " ids resolvieron.")
    return true
  end
  decir("=====================================================")
  decir(" PALETA INCOMPLETA: " .. #FALLOS .. " bloques no existen.")
  decir(" Los primeros:")
  for i = 1, min(8, #FALLOS) do
    decir("   " .. FALLOS[i])
  end
  decir("")
  decir(" Causa casi segura: el mod `lunaneon` no esta instalado")
  decir(" en este cliente, o su version no trae esos bloques.")
  decir(" Se construye igual, pero esos bloques saldran como")
  decir(" HUECOS. Instala lunaneon y vuelve a ejecutar.")
  decir("=====================================================")
  return false
end

--- La geometria tiene que ser consistente: las alas arrancan donde acaba el
--- nucleo y el perimetro donde acaban las alas. Si alguien toca un numero de
--- CFG sin mirar los otros, el edificio sale con las piezas separadas -- y eso
--- no da error, solo queda mal.
local function comprobar_geometria()
  local ok = true
  if CFG.r_alas <= CFG.r_nucleo + 10 then
    decir("GEOMETRIA: r_alas debe superar r_nucleo en al menos 10.")
    ok = false
  end
  if CFG.r_perimetro < CFG.r_alas then
    decir("GEOMETRIA: r_perimetro no puede ser menor que r_alas.")
    ok = false
  end
  -- ¿Caben diez alas del ancho pedido en la circunferencia del nucleo?
  local arco_disponible = 2 * pi * CFG.r_nucleo / #REGIONES
  if CFG.ala_ancho + 4 > arco_disponible then
    decir("GEOMETRIA: con r_nucleo=" .. CFG.r_nucleo .. " no caben " ..
          #REGIONES .. " alas de ancho " .. CFG.ala_ancho ..
          ". Sube r_nucleo o baja ala_ancho.")
    ok = false
  end
  -- El ambulatorio va de r_alas+7 a r_alas+14, y el muro de fondo del ala
  -- esta en r_alas+3. El perimetro tiene que quedar por fuera de todo.
  if CFG.r_perimetro < CFG.r_alas + 16 then
    decir("GEOMETRIA: r_perimetro (" .. CFG.r_perimetro .. ") debe ser al menos " ..
          "r_alas + 16 = " .. (CFG.r_alas + 16) ..
          ", o la fachada y las torres caen dentro del ambulatorio.")
    ok = false
  end
  if CFG.luna_y - CFG.luna_radio <= CFG.corona_y then
    decir("GEOMETRIA: la luna corta con la corona. Sube luna_y.")
    ok = false
  end
  return ok
end

--- Vacia el volumen que va a ocupar el palacio.
local function limpiar(cx, cz)
  local r = CFG.r_perimetro + 6
  local y1 = CFG.cripta_y - 4
  local y2 = CFG.luna_y + CFG.luna_radio + 6
  decir("Limpiando el volumen... (esto tarda)")
  cilindro(cx, cz, r, y1, y2, P.aire)
end


-- ###########################################################################
-- ##  9. MAIN                                                              ##
-- ###########################################################################

local function main()
  -- --- Donde ------------------------------------------------------------
  local cx, cz
  if CFG.usar_punto_del_raton then
    cx, cz = x, z            -- x,z son las coords del punto donde pinchaste
  else
    cx, cz = CFG.cx, CFG.cz
  end

  if CFG.preset then
    if aplicar_preset(CFG.preset) then
      decir("preset: " .. CFG.preset)
    else
      decir("AVISO: preset desconocido '" .. tostring(CFG.preset) ..
            "'. Se usan los valores de CFG.")
    end
  end

  decir("=====================================================")
  decir("  EL PALACIO LUNAR")
  decir("  centro " .. tostring(cx) .. ", " .. tostring(cz) ..
        "   planta noble y=" .. CFG.y_base)
  decir("=====================================================")

  -- --- Comprobar antes de tocar nada -------------------------------------
  comprobar_paleta()
  if not comprobar_geometria() then
    decir("ABORTADO: la geometria no es consistente. No se ha tocado nada.")
    return
  end

  if CFG.limpiar_antes then limpiar(cx, cz) end

  -- --- Construir ---------------------------------------------------------
  -- El orden importa: primero lo que sostiene, luego lo que se apoya, y las
  -- alas DESPUES del nucleo porque muerden su muro para abrir los portales.

  if CFG.hacer.basamento then
    decir("basamento...")
    construir_basamento(cx, cz)
  end

  if CFG.hacer.cripta then
    decir("cripta...")
    construir_cripta(cx, cz)
  end

  if CFG.hacer.nucleo then
    decir("nucleo...")
    construir_nucleo(cx, cz)
  end

  if CFG.hacer.alas then
    for i, reg in ipairs(REGIONES) do
      if ala_pedida(reg.id) then
        decir("ala " .. i .. "/" .. #REGIONES .. "  " ..
              reg.nombre .. "  [" .. reg.estado .. "]")
        construir_ala(cx, cz, i, reg)
      end
    end
  end

  if CFG.hacer.entreplantas then
    decir("entreplantas...")
    construir_entreplantas(cx, cz)
  end

  if CFG.hacer.ambulatorio then
    decir("ambulatorio...")
    construir_ambulatorio(cx, cz)
  end

  if CFG.hacer.mosaico then
    decir("mosaico del ambulatorio...")
    construir_mosaico(cx, cz)
  end

  if CFG.hacer.emblemas then
    decir("emblemas...")
    construir_emblemas(cx, cz)
  end

  if CFG.hacer.agua then
    decir("agua y jardineras...")
    construir_agua(cx, cz)
  end

  if CFG.hacer.fachada then
    decir("fachada exterior...")
    construir_fachada(cx, cz)
  end

  if CFG.hacer.basamento then
    decir("perimetro...")
    construir_perimetro(cx, cz)
  end

  if CFG.hacer.torres then
    decir("torres de los vertices...")
    construir_torres(cx, cz)
  end

  if CFG.hacer.arbotantes then
    decir("arbotantes...")
    construir_arbotantes(cx, cz)
  end

  if CFG.hacer.pinaculos then
    decir("pinaculos...")
    construir_pinaculos(cx, cz)
  end

  if CFG.hacer.cupula then
    decir("cupula...")
    construir_cupula(cx, cz)
  end

  if CFG.hacer.corona then
    decir("corona...")
    construir_corona(cx, cz)
  end

  -- ⚠ Despues de la corona y de la cupula: perfora las dos.
  if CFG.hacer.acceso_corona then
    decir("acceso a la corona...")
    construir_acceso_corona(cx, cz)
  end

  if CFG.hacer.luna then
    decir("la luna...")
    construir_luna(cx, cz)
  end

  if CFG.hacer.escalinata then
    decir("escalinata...")
    construir_escalinata(cx, cz)
  end

  -- ⚠ Despues de la escalinata: muere justo en su arranque.
  if CFG.hacer.paseo then
    decir("paseo de acceso desde la plaza...")
    construir_paseo(cx, cz)
  end

  if CFG.hacer.vestibulo then
    decir("vestibulo...")
    construir_vestibulo(cx, cz)
  end

  if CFG.hacer.testeros then
    decir("testeros de las alas...")
    construir_testeros(cx, cz)
  end

  if CFG.hacer.senalizacion then
    decir("senalizacion del suelo...")
    construir_senalizacion(cx, cz)
  end

  if CFG.hacer.carteles then
    decir("rotulos...")
    construir_rotulos(cx, cz)
  end

  -- La iluminacion va la ULTIMA: reparte luz por donde ha quedado oscuro, y
  -- para eso tiene que estar todo lo demas puesto.
  if CFG.hacer.iluminacion then
    decir("iluminacion de repaso...")
    construir_iluminacion(cx, cz)
  end

  -- --- Informe -----------------------------------------------------------
  local activas, latentes, reservadas = 0, 0, 0
  local plataformas = 0
  for _, r in ipairs(REGIONES) do
    if ala_pedida(r.id) then
      if r.estado == "activa" then activas = activas + 1
      elseif r.estado == "latente" then latentes = latentes + 1
      else reservadas = reservadas + 1 end
      plataformas = plataformas + 9        -- 8 alcobas + 1 estrado
    end
  end
  if CFG.hacer.corona then plataformas = plataformas + #REGIONES * 4 end
  if CFG.hacer.cripta then plataformas = plataformas + #EQUIPOS * 3 end

  decir("-----------------------------------------------------")
  decir("  bloques colocados : " .. tostring(PUESTOS))
  decir("  alas activas      : " .. activas)
  decir("      latentes      : " .. latentes)
  decir("      reservadas    : " .. reservadas)
  decir("  plataformas VACIAS: " .. plataformas)
  decir("")
  decir("  Los entrenadores NO se han colocado: es un paso")
  decir("  posterior y manual. Las plataformas estan hechas,")
  decir("  con su aro de color y el hueco despejado.")
  if PUESTOS == 0 then
    decir("")
    decir("  ATENCION: 0 bloques. Revisa la paleta -- casi")
    decir("  seguro que falta el mod lunaneon.")
  end
  decir("=====================================================")
end

main()

-- El Lua Script Brush exige que el script devuelva un bloque para el punto
-- ancla. Se devuelve el que ya habia: el ancla no debe modificarse, todo el
-- trabajo lo ha hecho setBlock.
return getBlock(x, y, z)


--[[ ===========================================================================

  10. RECETAS — como se toca esto sin romperlo

  Todo lo de aqui abajo son comentarios. Se deja EN EL FICHERO y no en la
  documentacion aparte a proposito: quien vaya a modificar el palacio va a
  tener este fichero abierto y no la carpeta docs/.

  ---------------------------------------------------------------------------
  A) ACTIVAR UNA REGION cuando se encienda su generacion

  Es el cambio mas frecuente y el mas facil de hacer mal.

  En la tabla REGIONES (seccion 3), cambiar el `estado`:

      { id = "hoenn", ..., estado = "latente" }   ->   estado = "activa"

  Y volver a ejecutar SOLO esa ala, para no reconstruir el palacio entero:

      CFG.solo_alas = { "hoenn" }
      CFG.hacer.nucleo = false        -- el portal de la rotonda tambien
      CFG.hacer.cupula = false        -- cambia de color, ver el aviso
      ... el resto a false

  ⚠ PERO EL PORTAL DEL NUCLEO Y LA FACHADA SE QUEDAN CON EL COLOR VIEJO.
    El dintel del portal, el pano de fachada, el pinaculo y el rotulo de esa
    region se dibujan desde `construir_nucleo`, `construir_fachada`,
    `construir_pinaculos` y `construir_rotulos`, que recorren las diez
    regiones. Ejecutar solo el ala deja el ala encendida y su puerta apagada.

    Lo correcto al activar una region es reconstruir TODO. Cuesta lo mismo que
    la primera vez y no deja restos incoherentes. Si de verdad hace falta ir
    por partes, hay que activar tambien nucleo, fachada, pinaculos y carteles.

  ---------------------------------------------------------------------------
  B) CAMBIAR EL COLOR DE UNA REGION

  En REGIONES, campo `color`. Tiene que ser uno de los 16 de lunaneon:

      blanco  gris_claro  gris  negro
      rojo    naranja     amarillo  lima
      verde   cian        azul_claro  azul
      morado  magenta     rosa  marron

  ⚠ Un color que no este en esa lista NO da error: `NEON[color]` devuelve nil,
    `neon()` devuelve nil y `poner()` no coloca nada. El sintoma es un ala sin
    una sola linea de luz, y nada que lo explique.

  ---------------------------------------------------------------------------
  C) ANADIR UNA REGION (pasar de 10 a 11 alas)

  Basta con anadir una entrada a REGIONES: todo lo demas se reparte solo,
  porque cada pieza usa `#REGIONES` y `angulo_ala(i, #REGIONES)`.

  ⚠ PERO HAY QUE SUBIR `r_nucleo`. El arco que le toca a cada ala es
    2*pi*r_nucleo / n, y con una region mas el reparto se estrecha. La formula
    esta en `comprobar_geometria()`:

        r_nucleo minimo = (ala_ancho + 4) * n / (2*pi)

    Con 11 alas de ancho 18: (18+4)*11/(2*pi) = 38,5  ->  r_nucleo = 39.

    Si se olvida, `comprobar_geometria()` lo dice y ABORTA antes de construir.
    Esa comprobacion existe precisamente porque el diseno original tenia este
    fallo: 10 alas de 18 con r_nucleo=28 daban 17,6 de arco para 22 necesarios,
    y las alas se solapaban entre si.

  Ademas hay que anadirle un emblema en EMBLEMAS (seccion 7.12); si no lo
  tiene, sencillamente no se dibuja y el resto funciona igual.

  ---------------------------------------------------------------------------
  D) CAMBIAR LA ESCALA

  Poner `CFG.preset = "maqueta"`, `"normal"` o `"epico"` (seccion 7.15).

  `maqueta` es la que conviene para juzgar la planta: sale en un tercio del
  tiempo y con la misma disposicion. La proporcion de las alcobas no se puede
  juzgar en maqueta -- para eso hace falta `normal`.

  ---------------------------------------------------------------------------
  E) PROBAR UNA SOLA PIEZA

  Todo `CFG.hacer.*` a false menos la que interese. Por ejemplo, para iterar
  sobre la cupula:

      CFG.hacer = { cupula = true }      -- las demas claves ausentes = nil = false

  ⚠ Algunas piezas dependen de otras para verse bien:
      - `alas` sin `nucleo` deja las alas sin portal de entrada
      - `cupula` sin `nucleo` deja la cupula flotando
      - `iluminacion` sola no hace nada visible
      - `corona` sin `acceso_corona` deja los tronos inalcanzables

  ---------------------------------------------------------------------------
  F) MOVERLO DE SITIO

  Por defecto se planta donde pinches (`CFG.usar_punto_del_raton = true`).
  Para fijarlo:

      CFG.usar_punto_del_raton = false
      CFG.cx, CFG.cz = 0, 0

  ⚠ La ciudadela es `lunaeternal:ciudadela` y su plaza es una isla de 56x56
    centrada en el origen, con el suelo en y=63. El palacio de tamano `normal`
    mide 189x195, o sea que SOBRESALE de la plaza por los cuatro lados. Es
    correcto: vuela sobre el vacio y no hay terreno que respetar.

  ---------------------------------------------------------------------------
  G) SI SALE MAL

  Ctrl+Z de Axiom deshace la operacion entera. Es una sola operacion aunque
  sean 450 000 bloques, asi que un unico Ctrl+Z basta.

  Si ya se ha guardado y sincronizado y hay que limpiar:

      CFG.limpiar_antes = true
      CFG.hacer = { }        -- nada que construir: solo limpia

  ---------------------------------------------------------------------------
  H) LO QUE ESTE SCRIPT NO HACE, Y NO PUEDE HACER

  - **No coloca entrenadores.** Por decision del usuario (2026-08-22): las
    plataformas se dejan hechas y vacias. Ademas, el Lua Script Brush solo
    coloca BLOQUES -- no invoca entidades ni escribe NBT, asi que un
    `rctmod:trainer` no se puede poner desde aqui de ninguna manera. Lo que si
    se podria poner es el bloque `rctmod:trainer_spawner`, y se ha dejado
    fuera a proposito para no adelantar una decision de diseno.

  - **No pone carteles de texto.** Por lo mismo: un `minecraft:sign` necesita
    NBT para llevar texto. Por eso los rotulos son letras de bloque (seccion
    7.10), que ademas es lo unico legible a esta escala.

  - **No genera terreno.** El palacio flota. Si alguna vez se quiere apoyado,
    hay que construirle el soporte aparte.

  ---------------------------------------------------------------------------
  I) COMO SE PROBO ESTO ANTES DE EJECUTARLO EN EL JUEGO

  Con un banco de pruebas que simula la API de Axiom (`setBlock`, `getBlock`,
  `blocks`, `withBlockProperty`, los dos ruidos) y ejecuta el script entero
  fuera del juego, contando bloques y midiendo la caja envolvente.

  Cazo tres fallos reales que no se habrian visto hasta tener medio palacio
  construido:

    1. Diez alas de ancho 18 NO CABEN alrededor de un nucleo de radio 28.
    2. La luna a y=132 ATRAVESABA la corona a y=112.
    3. `setBlock` recibia coordenadas FRACCIONARIAS (16.954915...) en nueve de
       las diez alas, porque sus direcciones son diagonales. Se arreglo
       redondeando en `poner()`, que es el unico punto de salida.

  El banco vive en el scratchpad de la sesion, no en el repositorio. Si hay
  que rehacerlo, lo que tiene que simular esta listado arriba.

=========================================================================== ]]


--[[ ===========================================================================

  11. BITACORA — los fallos que cazo el banco de pruebas

  Se deja escrito porque los cinco son del mismo tipo, y ese tipo es el que
  mas duele en una planta radial: PIEZAS QUE SE DISENAN POR SEPARADO Y NADIE
  COMPRUEBA QUE NO SE PISEN. Ninguno da error. Todos se habrian visto en el
  juego, con el palacio ya construido y con la unica salida de deshacerlo.

  ---------------------------------------------------------------------------
  1. LAS DIEZ ALAS NO CABIAN ALREDEDOR DEL NUCLEO

     r_nucleo = 28 daba 2*pi*28/10 = 17,6 de arco por ala, y cada ala necesita
     ala_ancho + 4 = 22. Las alas se habrian solapado entre si cerca del
     arranque, mezclando los muros de Kanto con los de Johto.

     Arreglado subiendo r_nucleo a 36, y anadida la comprobacion que lo dice.

  ---------------------------------------------------------------------------
  2. LA LUNA ATRAVESABA LA CORONA

     luna_y = 132 con radio 21 baja hasta 111, y la corona estaba en 112.
     Los diez tronos del Alto Mando habrian quedado DENTRO de la esfera.

     Arreglado: corona a 124 (por encima del remate de la cupula, que acaba en
     116) y luna a 150.

  ---------------------------------------------------------------------------
  3. setBlock RECIBIA COORDENADAS FRACCIONARIAS

     x = 16.954915028125 construyendo los portales del nucleo.

     La causa: el palacio es un DECAGONO, asi que nueve de las diez alas miran
     en diagonal, y sus vectores de direccion son numeros como 0,809 y 0,587.
     Cualquier elemento que multiplique por esa direccion produce fracciones.

     Que hace Axiom con una coordenada fraccionaria --truncar, redondear o
     ignorarla-- no esta documentado. Depender de ello seria construir sobre
     una suposicion.

     Arreglado redondeando dentro de `poner()`, que es el unico punto de salida
     al mundo: una linea arregla las ~60 funciones que llaman ahi.

  ---------------------------------------------------------------------------
  4. EL PRESET `maqueta` TENIA EL MISMO FALLO QUE EL 1

     r_nucleo = 24 con ala_ancho = 12 necesita 25,5. Se colo porque el preset
     se escribio DESPUES de arreglar el caso normal, copiando la estructura
     pero no la formula.

     Lo cazo la misma comprobacion, que ya estaba puesta. Es el argumento
     entero a favor de escribir la comprobacion en vez de tener cuidado.

  ---------------------------------------------------------------------------
  5. EL AMBULATORIO ATRAVESABA LAS ALAS

     Estaba en `r_alas + 5` con 7 de ancho, o sea ocupando de 82 a 89. El ala
     llega a 86 y su muro de fondo esta en 87. El anillo cortaba las diez alas
     justo por encima del estrado del campeon.

     Este es el mas instructivo de los cinco: las dos piezas estaban BIEN cada
     una por su lado. El fallo solo existe en la relacion entre ellas, y esa
     relacion no la comprueba nadie salvo que se escriba explicitamente.

     Arreglado moviendo el anillo a `r_alas + 7 .. r_alas + 14`, empujando
     fachada, torres y perimetro detras, y --lo que faltaba de verdad--
     anadiendo los pasos que conectan cada ala con el anillo. Sin ellos el
     ambulatorio habria sido un pasillo perfecto al que no se puede entrar.

  ---------------------------------------------------------------------------
  LO QUE SIGUE SIN COMPROBAR

  El banco de pruebas verifica GEOMETRIA: que no haya coordenadas invalidas,
  que las piezas no se pisen, que los ids resuelvan y cuantos bloques salen.

  NO verifica que quede bonito. Eso solo se ve en el juego, de noche, desde la
  plaza y desde dentro. Las proporciones --altura de las alcobas, tamano de la
  luna contra la cupula, ancho del ambulatorio-- estan elegidas sobre plano y
  puede que alguna no funcione al ojo.

  Recomendado: ejecutar primero con `CFG.preset = "maqueta"`, mirar, y solo
  entonces lanzar el tamano normal.

=========================================================================== ]]

--[[ ===========================================================================

  12. PASO A PASO — la primera vez

  ---------------------------------------------------------------------------
  ANTES DE NADA: COMPRUEBA QUE TIENES LOS BLOQUES

  El palacio usa 344 bloques del mod `lunaneon`. Si no lo tienes instalado en
  ESTE cliente, el script se ejecuta igual y sale un esqueleto con agujeros --
  no da error, porque `setBlock` con un bloque inexistente simplemente no hace
  nada.

  El propio script lo avisa nada mas arrancar. Si ves

      PALETA INCOMPLETA: 344 bloques no existen.

  para y instala el mod. Construir asi y luego deshacerlo es media hora tirada.

  ---------------------------------------------------------------------------
  PASO 1 · PONTE EN LA CIUDADELA

      /luna ir ciudadela          (hace falta nivel 2)

  Llegas a 4, 69, 0 -- el centro de la plaza.

  ---------------------------------------------------------------------------
  PASO 2 · PRUEBA PRIMERO EN MAQUETA

  Edita la linea de CFG:

      CFG.preset = "maqueta"

  Sale en un tercio del tiempo, con la misma disposicion y la mitad de tamano
  (151x157 en vez de 215x221). Sirve para decidir DONDE va y si la orientacion
  te convence. No sirve para juzgar el detalle de las alcobas.

  ---------------------------------------------------------------------------
  PASO 3 · COLOCALO

    1. Herramientas -> Lua Script Brush
    2. Pega este fichero ENTERO en la caja de script
    3. Situate donde quieras el CENTRO del palacio
    4. Clic

  ⚠ Mira al norte al pinchar si quieres que la entrada te quede de frente: la
    escalinata, el vestibulo y el paseo salen siempre hacia el norte (-Z), no
    hacia donde mires.

  ---------------------------------------------------------------------------
  PASO 4 · MIRALO DE NOCHE Y DESDE ABAJO

  La ciudadela tiene noche permanente (`fixed_time 18000`), asi que ya la
  tienes. Los dos sitios desde los que hay que juzgarlo:

    - desde el arranque del paseo, a ras de plaza: es la vista de llegada
    - desde el vacio, por debajo: es la que nadie disena y todo el mundo ve

  ---------------------------------------------------------------------------
  PASO 5 · SI TE CONVENCE, LANZA EL TAMANO BUENO

      CFG.preset = "normal"

  Deshaz la maqueta con Ctrl+Z ANTES de lanzar el normal. Si no, se mezclan y
  quedan restos de la maqueta dentro de los muros del palacio grande, que es
  de lo mas dificil de limpiar que hay.

  ---------------------------------------------------------------------------
  PASO 6 · LOS ENTRENADORES

  Eso ya no lo hace el script, por decision tuya. Lo que te deja hecho:

      90   plataformas en las alas    (8 alcobas + 1 estrado x 10)
      40   pedestales en la corona    (el Alto Mando, 4 por region)
      15   plataformas en la cripta   (5 bandas x 3)
     ---
     145   sitios, todos vacios y con el hueco despejado

  Cada uno lleva su aro del color de la region y las cuatro esquinas marcadas,
  para que centrar al entrenador sea a ojo y no contando bloques.

  ⚠ EL ORDEN DE LAS ALCOBAS ES EN ZIGZAG, no dos filas paralelas: la 1 a la
    izquierda, la 2 a la derecha, la 3 a la izquierda... Es asi para que el
    recorrido sea alternado. Si vas a poner los ocho lideres en orden de
    medalla, ese es el orden que sigue el jugador.

  ---------------------------------------------------------------------------
  EL RECORRIDO COMPLETO, PARA COMPROBAR QUE NO FALTA NADA

      plaza (y 64)
        -> paseo de acceso, 86 de calzada con tres descansillos
        -> escalinata, 10 peldanos
        -> vestibulo, con el fronton y el oculo
        -> perforacion: desde aqui ya se ve el pozo de luz al fondo
        -> ambulatorio: el anillo, con el suelo de diez colores
        -> testero del ala que elijas, por cualquiera de sus dos pasos
        -> el ala: ocho alcobas y el estrado del campeon al fondo
        -> entreplanta, si subes por la escalera lateral
        -> portal del nucleo
        -> la rotonda, el agua y el pozo de luz
        -> escalera de caracol por el pozo
        -> la corona: los diez tronos, a cielo abierto, con la luna encima
        -> y por el pozo descentrado de la rotonda se baja a la cripta

  Cada una de esas flechas existe y es transitable. Tres de ellas --el paseo,
  los pasos del ambulatorio y la escalera de la corona-- se anadieron DESPUES
  de escribir el edificio, porque al recorrerlo mentalmente aparecieron tres
  sitios sin salida. Ver la seccion 11.

=========================================================================== ]]
