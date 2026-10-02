# Lunita — implementación y validación, 2 de octubre de 2026

Lunita conserva su función de guardiana y ahora también existe como especie Pokémon independiente `lunaeternal:lunita`, Hada/Psíquico. Los archivos originales entregados permanecen intactos.

## Diseño conmemorativo

Hembra, amistad inicial 255, tamaño de Eevee y estadísticas estándar de Sylveon (525 total). Conserva Fuga, Adaptabilidad y Anticipación oculta; cada ejemplar tiene una habilidad según las reglas normales de Cobblemon. Conserva todos los movimientos originales de Eevee y añade movimientos Hada/Psíquico y de apoyo, incluyendo Fuerza Lunar, Psíquico, Deseo y Gota Vital.

Descripción: «Lunita es una compañera de tipo Hada y Psíquico, tan tierna que todos se derriten de amor con tan solo verla. Su mirada guarda la luz de los recuerdos felices. Le encanta descansar en el hombro de quien ama y acompañarlo en cada aventura. El cariño que dejó sigue brillando para siempre.»

Sección propia «En memoria de Lunita» en la Pokédex. Sin reproducción ni evoluciones. No tiene entrada de aparición; además se cancela cualquier intento del generador natural de Cobblemon de crear esta especie. `/lunita dar <jugador> [nivel]` requiere permiso administrativo 4, nivel por defecto 50, rango 1–100; entrega al equipo o PC y registra administrador, destinatario y UUID. `/lunita verificar` valida sin entregar ejemplares.

Sonido de Eevee y efectos normales de Cobblemon conservados; corazones y destellos breves al sacarla. Montaje en ambos hombros mediante el sistema nativo.

## Modelo y navegación

Modelo original: 150 huesos y 88 cubos, más 22 localizadores. La reparación añade dos huesos para que nariz y pelaje del mentón sigan la cabeza: 152 huesos, mismos 88 cubos y posiciones de reposo conservadas. No se añadieron alas ni una aureola visible.

Textura original 128×64 para GeckoLib. Atlas Pokémon 128×128 conserva esa imagen y corrige seis cubos fraccionarios para el tratamiento UV de Cobblemon. Conserva cuatro animaciones originales de Eevee y sonido; añade estados de descanso, saludo y combate. Las poses de hombro desplazan el modelo completo.

Guardiana: estado sincronizado, seguimiento de cabeza, parpadeo, saludo de 29 ticks incluyendo transición, caminar según desplazamiento real y retorno navegando al superar 12 bloques del hogar. Recalcula cada segundo; recuperación al hogar certificado tras 30 segundos sin progreso, deteniendo navegación, velocidad y caída. Incluye nado.

Prueba real: avanzó de aproximadamente (-208,73,133.5) a (-197.93,73,132.805) en RETURN_HOME y luego volvió a WANDER cerca del hogar (-136,73,132). Se liberaron los siete chunks cargados temporalmente. No hubo duplicación ni entrega de Pokémon de prueba.

## Evidencia y límites

Se revisaron registro de especies, propiedades, generadores naturales, eventos de envío, hombros, modelos, poses y Pokédex del repositorio oficial Cobblemon, tag 1.8.0, commit b33536e8721c9419d664fee15aa9e35cbf1900a8: https://gitlab.com/cable-mc/cobblemon/-/tree/1.8.0

Compilación correcta: 182 pruebas, 180 aprobadas y dos omitidas. Verificadores específicos comprueban jerarquía, referencias, textura, UV, geometría conservada, habilidades, movimientos, animaciones, sonido y hombros. Se inspeccionaron renders y animación desde los recursos reales. No se observó un cliente Minecraft conectado: falta confirmar visualmente en juego la apariencia y montaje.

La validación del servidor final confirma especie, Pokédex, tipos, habilidades, movimientos, hombros y exclusión de cría. El autotest general ya presentaba seis fallos de 742: ledger de Marcas, flujos recientes, jugadores activos, balance de pagos, gimnasio/líder y fuente del logo del tablist. El usuario autorizó explícitamente instalar pese a ese estado; esos controles no se presentan como aprobados.

## Instalación

Paquetes parcheados sobre originales distintos de servidor y cliente: 24 entradas actualizadas, 1891 entradas ajenas preservadas byte por byte y fusión únicamente de traducciones conmemorativas. Informes exactos en build/*.audit.json. Reparaciones locales de PlayerService y AutoTest para compilar no sustituyen sus clases de producción.

Servidor SHA256: 6a4642198448b0395bdae0739c4ad3d79bc2b86bc11a19d8bc120af5203838f9
Cliente SHA256: 6b93ff8fa3aa8889a70e42e9f7fa0711998829e4646ab2329b8a58d2d15b2205

Instalación con guardado y reinicio controlado; respaldos en backups/lunita-v2 del volumen del servidor. Cliente publicado con tools/publicar_luna_dirigido.py --jar build/lunaeternal-lunita-v2-client.jar; solo cambia el JAR Luna Eternal del launcher.

Reversión: guardar y detener servidor, restaurar backups/lunita-v2/lunaeternal-before.jar, verificar SHA256 d86b6c120228e74778c5ce421e1b3b31350949ca31230991996da05e749a7cf0, conservar propietario pterodactyl y arrancar. Restaurar puntero del launcher desde build/pointer-before-lunita.json. Antes de retirar la especie, preservar cualquier Lunita entregada.

Launcher verificado: manifest-19f1ded5ea.json; SHA1 cliente 310045f2bd67a1b6a7776361c1f4ebf95ee69004. Las otras 159 entradas coinciden exactamente con el manifiesto previo.

## Corrección visual de las 00:22 (hora Colombia)

El crash real al recibir a Lunita fue PoseAdapter/JsonPose: getPart("Lunita") no existe en el registro del poser. JsonModelAdapter registra la raíz seleccionada con el alias __root, y sus descendientes por sus nombres. Ambas poses de hombro ahora usan __root. Se corrigieron generador, recurso y pruebas; el verificador comprueba transformedParts contra ese registro. Compilación y verificación aprobadas. Parche de cliente cambia solo el poser y conserva 1914 entradas; SHA256 016cde5aa41123411a9d2b4fc768c763c8f87c4c3507a56be8a9e06dfe77c465. Aplicado localmente con Minecraft cerrado y publicado en launcher. No cambia datos ni requiere reiniciar servidor. El servidor confirma la entrega original a TheJuanCE, nivel 100, UUID 0aeefb3e-a9e5-4014-9d69-fe86d38cf5d3; no se eliminó ni duplicó. Pendiente nueva confirmación visual en juego.

## Reincidencia a las 00:28

El crash segu�a mostrando part=Lunita y el JAR local ten�a SHA256 b2e426f88d4cdff2d7c1d32ba3ba0995f9a74eb1c2a8120bc5e7f353a249cb8e: se ejecut� nuevamente la versi�n anterior, no el parche __root. Se restaur� el paquete correcto 016cde5aa41123411a9d2b4fc768c763c8f87c4c3507a56be8a9e06dfe77c465 y se sincroniz� luna-installed.json (copia previa guardada). El usuario cerr� completamente el launcher para descartar el manifiesto en memoria. El manifiesto principal se verific� con SHA1 2890f8af4d48230137c8d746abdfcfb87a828790. El respaldo master/manifest.json se actualiz� mediante commit 3f1a3ec701c66888907fb855d6414e089e3961bd; GitHub API confirma el contenido nuevo, aunque la URL raw todav�a devolv�a contenido antiguo durante esta comprobaci�n. El publicador dirigido ahora actualiza tambi�n el respaldo para evitar divergencia futura. Pendiente nueva entrada a ciudadela con el cliente correcto.

## Confirmación del usuario

El 2 de octubre de 2026, después de cerrar y reabrir el launcher con el paquete corregido, el usuario informó: «ahora si creo que quedo todo bien». Se registra como confirmación de uso; no sustituye una comprobación exhaustiva de todas las animaciones o sistemas del servidor. Se conserva el estado final en la rama codex/lunita-model-navigation.

