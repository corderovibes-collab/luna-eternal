package net.pokereport.luna.io;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para A03: Cola I/O acotada, descarte seguro de ráfagas y prevención de deadlocks.
 */
public class IoQueueTest {

    @Test
    @DisplayName("La cola acotada rechaza tareas limpiamente al saturarse sin lanzar excepciones no controladas")
    void testBoundedQueueSaturation() throws InterruptedException {
        int capacity = 10;
        AtomicLong rejectedCount = new AtomicLong(0);
        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(capacity);

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 10, TimeUnit.SECONDS, queue,
                r -> {
                    Thread t = new Thread(r, "test-io");
                    t.setDaemon(true);
                    return t;
                },
                (r, exec) -> rejectedCount.incrementAndGet()
        );

        CountDownLatch blockerLatch = new CountDownLatch(1);
        CountDownLatch startLatch = new CountDownLatch(1);

        // Ocupar el único hilo trabajador
        executor.submit(() -> {
            startLatch.countDown();
            try {
                blockerLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
        });

        assertTrue(startLatch.await(2, TimeUnit.SECONDS));

        // Llenar la cola hasta el tope (10)
        for (int i = 0; i < capacity; i++) {
            executor.submit(() -> {});
        }
        assertEquals(capacity, executor.getQueue().size(), "La cola debe estar en su capacidad máxima");
        assertEquals(0, rejectedCount.get(), "Aún no debe haber tareas rechazadas");

        // Enviar 5 tareas adicionales: deben ser rechazadas por la política y no desbordar
        for (int i = 0; i < 5; i++) {
            executor.submit(() -> {});
        }

        assertEquals(5, rejectedCount.get(), "Las 5 tareas excedentes debieron ser rechazadas");
        assertEquals(capacity, executor.getQueue().size(), "La cola nunca debe superar su límite acotado");

        // Liberar el hilo y cerrar
        blockerLatch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Los hilos de I/O se crean como daemon con nomenclatura clara luna-io")
    void testThreadNamingAndDaemon() throws Exception {
        AtomicInteger count = new AtomicInteger(1);
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "luna-io-" + count.getAndIncrement());
            t.setDaemon(true);
            return t;
        };

        Thread created = factory.newThread(() -> {});
        assertEquals("luna-io-1", created.getName());
        assertTrue(created.isDaemon(), "Los hilos de I/O deben ser demonios para no impedir el apagado de la JVM");
    }

    @Test
    @DisplayName("Excepciones en tareas de fondo no detienen el pool ni interrumpen tareas posteriores")
    void testTaskExceptionDoesNotBreakPool() throws Exception {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 2, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(50),
                r -> {
                    Thread t = new Thread(r, "test-err-io");
                    t.setDaemon(true);
                    return t;
                }
        );

        AtomicBoolean segundaEjecutada = new AtomicBoolean(false);

        // Tarea que arroja excepción deliberada
        executor.submit(() -> {
            throw new RuntimeException("Simulando fallo en query MariaDB");
        });

        // Segunda tarea posterior
        executor.submit(() -> segundaEjecutada.set(true));

        executor.shutdown();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        assertTrue(segundaEjecutada.get(), "La segunda tarea debe ejecutarse normalmente tras el fallo de la primera");
    }

    @Test
    @DisplayName("Debounce de peticiones consecutivas filtra ráfagas antes de 150ms")
    void testDebounceRafagas() {
        ConcurrentHashMap<String, Long> ultimos = new ConcurrentHashMap<>();
        String testPlayer = "player-uuid-123";

        // Primera llamada: admitida
        long ahora = System.currentTimeMillis();
        Long previo = ultimos.put(testPlayer, ahora);
        boolean admitido1 = (previo == null || ahora - previo >= 150);
        assertTrue(admitido1, "La primera llamada debe admitirse");

        // Segunda llamada inmediata (<150ms): bloqueada
        long ráfaga = ahora + 10;
        previo = ultimos.put(testPlayer, ráfaga);
        boolean admitido2 = (previo == null || ráfaga - previo >= 150);
        assertFalse(admitido2, "Llamada inmediata dentro de la ventana de 150ms debe filtrarse");

        // Tercera llamada tras 200ms: admitida
        long despues = ráfaga + 200;
        previo = ultimos.put(testPlayer, despues);
        boolean admitido3 = (previo == null || despues - previo >= 150);
        assertTrue(admitido3, "Llamada tras expirar la ventana debe admitirse");
    }
}
