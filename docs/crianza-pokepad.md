# Crianza PokéPad — reglas vigentes

Actualización: 14 de septiembre de 2026. Sustituye las reglas del documento histórico `DOCUMENTACION_Y_PROMPT_CRIANZA.md`.

| Ranura visible | Desbloqueo |
|---|---|
| 1–2 | Gratis, cualquier jugador |
| 3–5 | 150 LunaCoins por cada ranura; compra independiente y permanente |
| 6 | Maestro o superior |
| 7 | Leyenda |

Las compras previas se conservan, incluidas las antiguas compras de las ranuras 6–7. Los permisos de operador y rangos de staff conservan su equivalencia previa a Leyenda; no regalan las expansiones 3–5.

| Rango | Duración de un ciclo nuevo |
|---|---|
| Sin rango / Entrenador | 25 minutos |
| Élite | 20 minutos |
| Campeón | 15 minutos |
| Maestro | 10 minutos |
| Leyenda | 5 minutos |

El tiempo se fija al completar una pareja compatible y al recoger una cría. Cambiar de rango no altera ciclos ya iniciados. Los ciclos existentes mantienen su duración guardada. El tiempo desconectado cuenta. Cambiar o retirar un progenitor reinicia el ciclo; elegir de nuevo el mismo Pokémon en la misma casilla es una operación sin cambios.

## Interfaz

Utiliza el chasis de cosméticos de la PokéPad. Las siete pestañas muestran disponibilidad, precio, rango, progreso y crías listas. Las tarjetas muestran modelos de los progenitores, nivel y condición shiny. La incubadora muestra cuenta atrás; la barra inferior muestra porcentaje y duración. El panel izquierdo explica la progresión y el tiempo del próximo ciclo. La compra requiere confirmar su precio en el mismo botón y se desactiva con saldo insuficiente.

La selección usa PCGUI de Cobblemon con equipo visible. El estado confirmado por servidor decide compatibilidad, progreso y reclamación; se consulta al abrir, después de acciones y al finalizar un temporizador. No se sincronizan todas las cajas continuamente durante el ciclo.

La guía muestra solo mecánicas implementadas: tres IVs heredados, cinco con Lazo Destino, naturaleza con Piedra Eterna y Poké Ball del progenitor principal. La entrega actual genera una cría de nivel 1, no un objeto huevo. No se prometen movimientos huevo, objetos recio ni probabilidades especiales de habilidad oculta.

## Persistencia y validaciones

- Cargo de LunaCoins y desbloqueo comparten transacción SQL, bloqueo de fila y clave de idempotencia.
- Las acciones de crianza del mismo jugador se serializan; el cliente también limita dobles clics.
- Un Pokémon no puede ocupar dos casillas ni varias ranuras.
- Sin género necesita Ditto; dos Ditto no crían. Se comprueban ambos progenitores al reclamar.
- Las búsquedas de almacén, la construcción de fichas y la entrega se ejecutan en el hilo del servidor; las consultas SQL permanecen en el ejecutor de E/S.
- El siguiente ciclo se guarda antes de entregar. Si no hay espacio o faltan progenitores, se restaura la cría lista. Los errores SQL no se interpretan como ranuras vacías ni como éxito.
- MariaDB y el almacenamiento de Pokémon no comparten transacción: una caída abrupta entre persistir el siguiente ciclo y entregar aún requiere recuperación administrativa. No se garantiza entrega exactamente una vez ante caída del proceso.

## Verificación

`gradlew -p mod -I ../tools/verificar_crianza.gradle verificarCrianza build`

La matriz comprueba seis niveles de rango, siete ranuras, compras anteriores, índices inválidos, precio y los cinco tiempos. La compilación incluye cliente y servidor. La validación visual dentro de Minecraft y las pruebas de compra/PC llena contra una MariaDB activa deben realizarse en un servidor de prueba; la matriz no sustituye estas pruebas de integración.
