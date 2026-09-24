# Corrección de armaduras de rango — 16/09/2026

Las 16 piezas seguían registradas como objetos y conservaban sus modelos `builtin/entity`, geometrías y texturas. Sin embargo, `LunaCliente.onInitializeClient()` había perdido los registros de `RankArmorRenderer` y `RankItemRenderer`. Esto explica los huecos invisibles del inventario y la armadura vanilla en el visor de kits.

Se restauran ambos registros para `LunaItems.TODOS`, después de inicializar `Trajes`. Se comprobó su presencia en el bytecode final y la integridad de las 16 geometrías/texturas y sus encuadres GUI/mundo.

Se revisó el manifiesto público vigente `523a95b1f7f696c41d3c71efa01dfc02c362377c`, JAR `2c576ecd54a9ba0123dfc80d7d9d7d4a9805a072`. La comparación del JAR corregido contra esa versión muestra **una única entrada modificada: `net/pokereport/luna/client/LunaCliente.class`**. No hay entradas añadidas o eliminadas. Se conservan byte por byte las insignias, fuentes, logo/tabulador, corrección del Buhonero y demás cambios publicados por la otra IA.

También se corrigió el classpath de la tarea de pruebas para incluir los recursos del cliente que necesita `FontLoadingTest`. No modifica el contenido del JAR. Resultado: compilación correcta, 46 pruebas sin fallos y verificación específica de las 16 piezas.

JAR corregido SHA1: `f57d4a0305d70175937ab9bffefffcc036c759eb`. Evidencias y respaldo: `build/rank-repair/`. El cambio es exclusivo del cliente y se distribuye mediante el launcher; requiere cerrar Minecraft y volver a iniciarlo para cargar el registro restaurado. La apariencia final en juego necesita comprobarse con ese cliente actualizado.
