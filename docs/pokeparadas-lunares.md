# Paradas lunares

Modelo propio de 2,5 bloques de altura: pedestal de pizarra, detalles de amatista/oro y luna creciente clara. Conserva las dos mitades registradas para no reemplazar paradas existentes. La mitad superior emite luz de nivel 15. Geometría vanilla, sin dependencia de DiosesMon ni GeckoLib. El material extraído de DiosesMon se conserva exclusivamente como referencia en `build/pokeparadas/referencia-diosesmon`.

## Colocación

Un operador de nivel 2 o superior obtiene el bloque con:

```mcfunction
/give @s lunaeternal:pokeparada_lunar
```

Colocarlo sobre una superficie sólida dejando tres bloques libres para la altura de la corona. Ambas mitades responden al clic derecho. No hay receta para jugadores; la colocación requiere permisos de operador. El poste es irrompible en supervivencia. Proteger también su suelo mediante las protecciones del servidor.

La revisión visual elimina los cubos blancos/dorados superpuestos de la luna, que producían z-fighting. La luna utiliza una sola malla sin caras interiores y una textura opaca de 16 fotogramas interpolados, con brillo suave en un ciclo de cuatro segundos. Destellos blancos END_ROD orbitan y ascienden alrededor de la corona desde `randomDisplayTick` del cliente; no se añaden tareas por tick del servidor. `tools/verificar_modelo_pokeparada.py` comprueba que no reaparezcan caras coplanares superpuestas.

## Reglas

- Un paquete contiene 4 o 5 tipos distintos, escogidos de una lista cerrada de 22 objetos básicos.
- Cada tipo entrega 1–4 unidades; Poké Balls y pan entregan como máximo 2.
- Incluye Poké Balls normales, bayas básicas, bonguris, semillas, troncos, palos, roca, zanahorias, patatas, pan y antorchas. No hay objetos legendarios, Master Balls, caramelos raros, diamantes o netherita.
- El plazo es de 24 horas exactas desde la última reclamación aceptada, no un reinicio a medianoche.
- Cada jugador conserva un contador separado por dimensión y coordenadas de la base de cada parada.
- Esa misma parada concede 100 Lunacoins en las visitas 50, 100, 150… Ninguna visita a otra parada aumenta ese contador.
- Romper y reconstruir en la misma ubicación conserva el contador y cooldown. Cambiarla de ubicación constituye otra parada.
- Si faltan huecos, el paquete sorteado queda persistido. Se recupera al tocar una parada con espacio; no se vuelve a sortear. Se requieren conservadoramente 4–5 huecos libres incluso si algunos objetos podrían apilarse.
- El progreso mostrado al interactuar corresponde a esa parada. El bono se ingresa en la misma transacción que el contador y el paquete.

## Persistencia y concurrencia

Migración V040: `lunar_stop_progress` y `lunar_stop_delivery`. Se usa bloqueo SQL de la fila jugador/parada; espera, contador, paquete y movimiento de Lunacoins se confirman juntos. Una cola propia de 64 tareas y una operación activa por jugador limitan los clics repetidos.

Los objetos se entregan en el hilo servidor. Un recibo de la entrega se guarda con el inventario en playerdata; se comprueba su presencia en disco antes de marcar el paquete como entregado en SQL. Un fallo SQL conserva el recibo para evitar volver a insertar los objetos al reintentar. No se entrega durante el JOIN previo al login de EasyAuth: la recuperación se hace al interactuar autenticado.

MariaDB y playerdata deben respaldarse/restaurarse de forma coordinada. Restaurar uno a un momento diferente del otro puede invalidar cualquier protocolo de entrega entre ambos almacenamientos.

## Validación

- Compilación servidor/cliente y suite Gradle.
- `tools/VerificarPokeparadas.java`: 100.000 sorteos con semilla fija, tipos únicos, lista cerrada, cantidades, fronteras del cooldown y bonos 1–1.000.
- Comprobación de todos los IDs de recompensa al iniciar el servidor.
- CRC del JAR y comparación de entradas contra el respaldo remoto antes de desplegar.

La imagen `build/pokeparadas/modelo-preview.png` es una vista geométrica, no una captura del juego. Queda por comprobar con un jugador la colocación, apariencia con los shaders usados e interacción física completa; las pruebas automáticas no sustituyen esa comprobación visual.

## Despliegue verificado

Servidor reiniciado correctamente; V040 aplicada y los 22 IDs de premios validados al arrancar. Evidencia en `build/pokeparadas/startup.log`. JAR anterior respaldado en `build/pokeparadas/server-before.jar`.

Launcher publicado y verificado: `manifest-45a3899122.json`. Único archivo actualizado en el manifiesto: `mods/lunaeternal-0.1.0.jar`, SHA1 `2427661713d543c6d5c19913f224afbeeb3da8f8`. Manifiesto anterior: `56a9ea87ff`. Cerrar Minecraft y abrirlo desde el launcher para recibir el bloque nuevo.

Revisión visual publicada: `manifest-dc2387f192.json`, SHA1 del JAR `3a4167d1d351c5fedc33779c92da6fb9ff3090ff`. Servidor reiniciado y arranque comprobado. Respaldo y log de esta revisión: `build/pokeparadas/visual-v2/`. No cambia las tablas ni la lógica de recompensas. Se comprobaron todas las caras del modelo para descartar solapamientos; queda por confirmar el resultado con los shaders del jugador en el cliente actualizado.
