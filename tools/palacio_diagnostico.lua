--[[ ===========================================================================

  DIAGNOSTICO DE PALETA  ·  Lua Script Brush de Axiom

  PARA QUE ES

  El palacio salio vacio: solo aparecio el agua, que es el unico bloque VANILLA
  del edificio. Eso significa que `blocks["lunaneon:..."]` devuelve nil y todos
  los bloques del mod se descartan en silencio.

  El mod SI esta instalado y los ids SI son correctos
  (`Identifier.of("lunaneon", "hormigon_pulido_blanco")`, comprobado en el
  fuente). Lo que no sabemos es COMO hay que escribirlos para Axiom: su
  documentacion solo enseña ejemplos vanilla (`blocks.stone`) y no menciona los
  namespaces ni una sola vez.

  Asi que se prueba a las bravas.

  COMO SE LEE EL RESULTADO

  Se construyen OCHO PILARES en fila hacia el este (+X), separados 3 bloques.
  Cada pilar corresponde a una forma de escribir el id:

     1  blocks["lunaneon:neon_rojo"]        <- la que usa el palacio ahora
     2  blocks.lunaneon.neon_rojo           <- tabla anidada por namespace
     3  blocks["neon_rojo"]                 <- solo la ruta, sin namespace
     4  blocks.neon_rojo                    <- igual, con notacion de punto
     5  blocks["lunaneon.neon_rojo"]        <- punto en vez de dos puntos
     6  blocks["lunaneon_neon_rojo"]        <- guion bajo
     7  blocks["minecraft:lunaneon:..."]    <- por si acaso lo prefija
     8  getBlock del bloque que pongas TU   <- ver abajo

  Y debajo de cada pilar hay una BALIZA:

     ORO       la forma FUNCIONA
     REDSTONE  la forma devuelve nil

  El pilar de una forma que funciona esta hecho DEL PROPIO BLOQUE lunaneon, o
  sea que ademas se ve el neon rojo de verdad.

  ---------------------------------------------------------------------------
  LA PRUEBA 8 ES LA IMPORTANTE SI FALLAN LAS DEMAS

  Antes de ejecutar, PON A MANO un bloque de neon rojo
  (`lunaneon:neon_rojo`, de la pestaña del creativo) TRES BLOQUES ENCIMA del
  punto donde vas a pinchar.

  El script lo lee con `getBlock` y construye el pilar 8 con lo que devuelva.
  Si el 8 sale y los demas no, quiere decir que Axiom SI sabe manejar bloques
  del mod pero no por nombre -- y entonces la solucion del palacio es otra:
  leer una paleta que tu coloques, en vez de nombrarla.

  ---------------------------------------------------------------------------
  Ponte en un sitio despejado. Ocupa 24 de ancho y 6 de alto.

=========================================================================== ]]

$once$
$ignoreMask$

local ORO      = blocks.gold_block or blocks["minecraft:gold_block"]
local ROJO     = blocks.redstone_block or blocks["minecraft:redstone_block"]
local PIEDRA   = blocks.stone or blocks["minecraft:stone"]

--- Prueba una forma de resolver el bloque, sin que un error tumbe el script.
local function probar(fn)
  local ok, b = pcall(fn)
  if ok and b then return b end
  return nil
end

local FORMAS = {
  { "corchete con dos puntos", function() return blocks["lunaneon:neon_rojo"] end },
  { "tabla anidada",           function() return blocks.lunaneon.neon_rojo end },
  { "solo la ruta, corchete",  function() return blocks["neon_rojo"] end },
  { "solo la ruta, punto",     function() return blocks.neon_rojo end },
  { "namespace con punto",     function() return blocks["lunaneon.neon_rojo"] end },
  { "namespace con guion bajo",function() return blocks["lunaneon_neon_rojo"] end },
  { "prefijado minecraft",     function() return blocks["minecraft:lunaneon:neon_rojo"] end },
  { "leido del mundo",         function() return getBlock(x, y + 3, z) end },
}

-- Suelo de referencia, para que se vea la fila aunque este todo en el aire
for i = -2, #FORMAS * 3 + 2 do
  for dz = -2, 2 do
    setBlock(x + i, y - 1, z + dz, PIEDRA)
  end
end

local funcionan = {}

for i, f in ipairs(FORMAS) do
  local nombre, fn = f[1], f[2]
  local b = probar(fn)
  local px = x + (i - 1) * 3

  -- Baliza: oro si resuelve, redstone si no
  setBlock(px, y, z, b and ORO or ROJO)

  -- Pilar del propio bloque, para verlo
  if b then
    for dy = 1, 4 do
      setBlock(px, y + dy, z, b)
    end
    funcionan[#funcionan + 1] = i .. " (" .. nombre .. ")"
  end

  -- Marca de numero: tantos bloques de piedra a la derecha como el indice
  for k = 1, i do
    setBlock(px, y, z + 1 + k, PIEDRA)
  end
end

-- Informe, por si la consola de Axiom lo enseña en algun sitio
pcall(function()
  print("=================================================")
  print(" DIAGNOSTICO DE PALETA")
  if #funcionan == 0 then
    print("  NINGUNA forma resolvio el bloque del mod.")
    print("  Mira si el pilar 8 salio: si tampoco, es que")
    print("  no pusiste el neon rojo 3 bloques por encima.")
  else
    print("  FUNCIONAN estas formas:")
    for _, s in ipairs(funcionan) do print("    " .. s) end
  end
  print("=================================================")
end)

return getBlock(x, y, z)
