package net.pokereport.luna.cosmetics;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.net.Red;

import java.util.Map;

/**
 * Cuenta a todo el mundo qué mascota lleva puesta cada jugador.
 *
 * <p>Es lo que convierte la tienda en algo que existe. Un cosmético que solo ve
 * su dueño no vale nada — {@code monetization.md} lo dice con esas palabras—, y
 * sin esto comprar y equipar funcionan y no se nota.
 *
 * <h2>Qué se manda y cuándo</h2>
 *
 * <ul>
 *   <li><b>Al equipar</b>: a todos, incluido quien lo equipa.</li>
 *   <li><b>Al entrar</b>: al recién llegado se le manda el estado de todos los
 *       demás, y a todos los demás el suyo.</li>
 * </ul>
 *
 * <p>⚠ <b>Las dos direcciones hacen falta.</b> Mandando solo la del recién
 * llegado, él no vería a nadie; mandando solo la de los demás, nadie le vería a
 * él. Y las dos se olvidan fácil porque cada una funciona a medias: se prueba
 * entrando uno mismo, se ve bien, y el fallo solo aparece con dos personas.
 *
 * <h2>Por qué se difunde a TODOS y no solo a quien está cerca</h2>
 *
 * Porque el estado es diminuto —dos cadenas por jugador— y filtrar por
 * distancia obligaría a reenviarlo cuando alguien entra en rango, que es un
 * caso más que se puede perder. Con veinte jugadores esto son veinte paquetes
 * de cien bytes al entrar y uno al equipar. Si algún día hay cientos, se
 * revisa; hoy la simplicidad vale más.
 */
public final class Difusion {

    private Difusion() {
    }

    /**
     * Qué lleva puesto este jugador, listo para mandar.
     *
     * <p>Especie vacía = no lleva nada. Es el mismo paquete para poner y para
     * quitar: con dos paquetes distintos podría llegar el de poner y perderse el
     * de quitar, y el cosmético se quedaría pegado en la pantalla de los demás.
     */
    private static Red.CosmeticoPuesto estado(ServerPlayerEntity jugador) {
        try {
            long id = LunaEternal.players()
                    .resolve(jugador.getUuid(), jugador.getName().getString());
            Map<String, String> puestos = LunaEternal.cosmetics().equipados(id);
            String cosmeticoId = puestos.get(Catalogo.MASCOTAS);
            Catalogo.Pieza pieza = cosmeticoId == null ? null : Catalogo.de(cosmeticoId);
            if (pieza == null) {
                return new Red.CosmeticoPuesto(jugador.getUuid(), "", "");
            }
            return new Red.CosmeticoPuesto(jugador.getUuid(), pieza.especie(), pieza.aspecto());
        } catch (Exception e) {
            LunaEternal.LOG.warn("No se pudo leer el cosmetico puesto de {}: {}",
                    jugador.getName().getString(), e.toString());
            // Ante la duda, "no lleva nada". Es preferible a no mandar nada:
            // sin paquete, el cliente se queda con lo ultimo que supo, que
            // puede ser un cosmetico que el jugador ya se ha quitado.
            return new Red.CosmeticoPuesto(jugador.getUuid(), "", "");
        }
    }

    /**
     * Difunde lo que lleva un jugador a toda la partida.
     *
     * <p>⚠ Se llama desde el executor de E/S —lee la base—, así que vuelve al
     * hilo del servidor para enviar. Enviar desde un hilo cualquiera no es
     * seguro, y consultar desde el principal congela a todo el mundo.
     */
    public static void difundir(ServerPlayerEntity jugador) {
        Red.CosmeticoPuesto carga = estado(jugador);
        MinecraftServer servidor = jugador.getServer();
        if (servidor == null) {
            return;
        }
        servidor.execute(() -> {
            for (ServerPlayerEntity otro : servidor.getPlayerManager().getPlayerList()) {
                ServerPlayNetworking.send(otro, carga);
            }
        });
    }

    /**
     * Al entrar: ponerse al día en las dos direcciones.
     *
     * <p>Se hace en el executor de E/S porque consulta la base una vez por
     * jugador conectado. Con la partida llena eso son varias consultas, y
     * hacerlas en el hilo del servidor se notaría como un tirón cada vez que
     * alguien entra.
     */
    public static void alEntrar(ServerPlayerEntity nuevo) {
        LunaEternal.submit(() -> {
            MinecraftServer servidor = nuevo.getServer();
            if (servidor == null) {
                return;
            }
            // Lo que llevan los demás, para él.
            for (ServerPlayerEntity otro : servidor.getPlayerManager().getPlayerList()) {
                if (otro == nuevo) {
                    continue;
                }
                Red.CosmeticoPuesto suyo = estado(otro);
                servidor.execute(() -> ServerPlayNetworking.send(nuevo, suyo));
            }
            // Y lo suyo, para todos.
            difundir(nuevo);
        });
    }
}
