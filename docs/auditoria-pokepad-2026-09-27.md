# Auditoría de PokePad — 2026-09-27

## Alcance comprobado

Se siguió cada aplicación declarada en `CatalogoPad` hasta su destino en
`Apps`: Pokédex, cosméticos, trabajos, misiones, viajes, clanes, GTS, tienda,
tesoros, cartas, cazas, kits, pase, gimnasios, explorar, mochila,
protecciones, santuario, curar, torre y crianza. Las aplicaciones abiertas
llevan a una pantalla propia o, en el caso de Mochila, a una apertura de
contenedor solicitada al servidor. `wiki` permanece explícitamente cerrada y
el clic devuelve estado bloqueado; no se presenta como una función disponible.

El autotest del servidor ya verifica que cada aplicación tenga icono, nombre,
descripción y `.mcmeta` JSON válido dentro del JAR; también comprueba que no
haya identificadores repetidos. Esto cubre los fallos de textura magenta,
traducción expuesta y celdas inaccesibles que no aparecen al compilar.

## Hallazgos corregidos

1. **Recompensa de misión parcialmente aplicada (crítico).**
   `QuestService.claim` marcaba la misión como reclamada antes de acreditar
   Plata, Marcas y XP. Un fallo posterior podía dejar una recompensa perdida.
   Ahora los cuatro efectos comparten una única transacción de base de datos:
   se confirman juntos o se revierten juntos. Las entradas económicas usan una
   clave idempotente estable por jugador, misión y periodo.

2. **Árbol de misiones contradictorio (alto).**
   El servidor exige cobrar la misión anterior para habilitar la siguiente,
   pero el paquete del cliente la mostraba abierta al completarla. El paquete
   ahora refleja la misma condición autoritativa y evita botones que prometen
   una operación que el servidor rechazará.

3. **Reinicio diario/semanal dependiente de la JVM (alto).**
   El periodo usaba la zona horaria implícita del proceso. Se fijó una fuente
   temporal única, `America/Bogota`, para que los reinicios no varíen según el
   host o una actualización de infraestructura.

4. **Cobertura de regresión.**
   `/luna autotest` ahora mide que reclamar una misión incremente exactamente
   Plata, Marcas y XP de Vía, y que un segundo intento no pague otra vez.

## Estado de Misiones, Trabajos y Clanes

- **Misiones:** cliente solicita estado, servidor calcula progreso y condiciones
  de cobro, y el cliente solo presenta los datos. Las recompensas y el progreso
  son persistentes.
- **Trabajos:** las cinco Vías y los oficios se solicitan al servidor; los
  niveles, XP y umbral se calculan en servidor. La interfaz no tiene autoridad
  económica.
- **Clanes:** fundación, invitaciones, roles, tesoro, retiro, límite diario y
  disolución pasan por `ClanService` con transacciones y comprobaciones de rol.
  El autotest existente valida permisos, conservación del tesoro e historial.

## Deuda de producto, no maquillada

La ficha lateral del PokePad recibe el clan real, niveles y medallas. Los campos
`Trabajo` y `División` aún no tienen una fuente de verdad de producto; por eso
se muestran como guion y no se ha inventado un valor local. Antes de activarlos
deben definirse sus reglas de negocio y su persistencia/autoridad en servidor.

## Verificación realizada

`mod/gradlew.bat test --rerun-tasks --no-daemon` terminó correctamente después
de cada cambio. Falta ejecutar `/luna autotest` sobre el servidor con este JAR
antes de emitir una certificación de producción.
