package net.pokereport.luna.santuario;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.item.BlockItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.world.LunaDimensions;

/**
 * Que nadie toque el nicho de otro.
 *
 * <h2>⚠⚠ LA REGLA DEL HILO DEL SERVIDOR OBLIGA A UNA CACHE</h2>
 *
 * Los eventos de romper y usar bloques corren <b>en el hilo del servidor</b>, y
 * ahi consultar la base esta prohibido (regla 1 del proyecto). Por eso la
 * reclamacion vive en una cache en memoria: la geometria sale del
 * {@link NichoCatalogo} (cargado al arrancar) y el dueno, de aqui. La base se
 * relee cada minuto ({@link #recargar()}) y despues de cada alquiler o compra,
 * asi que la cache no se inventa nada -- es una copia fresca de la fila.
 *
 * <h2>⚠⚠ SE PROTEGE CON TRES EVENTOS, NO CON UNO, y cada uno tapa un hueco</h2>
 *
 * <ul>
 *   <li>{@code PlayerBlockBreakEvents.BEFORE} — romper el bloque;</li>
 *   <li>{@code UseBlockCallback} — interactuar con un bloque: abrir el
 *       proyector, pulsar un boton, colocar contra una cara;</li>
 *   <li>{@code UseItemCallback} — colocar un bloque en el aire dentro del
 *       nicho. Se filtra por {@code BlockItem} para que comer, beber o lanzar
 *       una Poké Ball dentro de un nicho ajeno siga funcionando: lo unico que
 *       se corta es construir.</li>
 * </ul>
 *
 * <p>⚠ La regla de Fabric es que {@code BlockPlaceEvents} NO EXISTE en esta
 * version de la API (comprobado en el jar, no supuesto): por eso la colocacion
 * se corta por los dos caminos por los que de verdad se coloca -- contra un
 * bloque y contra el aire.
 *
 * <h2>⚠⚠ EL PROYECTOR SE CANCELA SIEMPRE, TAMBIEN PARA EL DUENO</h2>
 *
 * Su menu es del mod de cartas --un inventario para meter una carta-- y aqui el
 * proyector es NUESTRO pedestal: el clic derecho abre el memorial (ver
 * {@code Red}). Si al dueno se le dejara pasar, tendria un cajon para meter
 * objetos en el monumento, y ese cajon es exactamente lo que este proyecto ha
 * decidido no tener.
 */
public final class SantuarioProteccion {

    /** La reclamacion de un nicho, ya con el UUID y no el id de la base. */
    public record Claim(UUID owner, boolean permanente, long expiraMs) {
        boolean libre(long ahora) {
            return owner == null || (!permanente && expiraMs <= ahora);
        }
    }

    private static NichoCatalogo catalogo = new NichoCatalogo(java.util.List.of());
    private static final Map<String, Claim> CLAIMS = new ConcurrentHashMap<>();

    private SantuarioProteccion() {}    /** La geometria, puesta al arrancar. */
    public static void catalogo(NichoCatalogo c) {
        catalogo = c;
    }

    public static NichoCatalogo catalogo() {
        return catalogo;
    }

    /**
     * Vuelve a leer las reclamaciones de la base.
     *
     * <p>⚠ Va por el executor de E/S, nunca en el hilo del servidor. Se llama
     * al arrancar, cada minuto y despues de cada alquiler o compra. El JOIN con
     * {@code player} es lo que da el UUID: la cache se compara contra el jugador
     * de Minecraft, y el id de la base aqui no sirve para nada.
     */
    public static void recargar() {
        var nueva = new ConcurrentHashMap<String, Claim>();
        try (Connection c = LunaEternal.database().connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT s.nicho_id, p.mc_uuid, s.permanente, s.expira_ms "
                             + "FROM santuario s LEFT JOIN player p "
                             + "ON p.player_id = s.owner_id")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID u = null;
                    String uuid = rs.getString("mc_uuid");
                    if (uuid != null) {
                        try {
                            u = UUID.fromString(uuid);
                        } catch (IllegalArgumentException ignorado) {
                            // Una fila con uuid roto es una fila sin dueno a
                            // efectos de proteger: se trata como libre.
                        }
                    }
                    nueva.put(rs.getString("nicho_id"),
                            new Claim(u, rs.getBoolean("permanente"),
                                    rs.getLong("expira_ms")));
                }
            }
        } catch (SQLException e) {
            LunaEternal.LOG.error("Santuario: no se pudieron leer las reclamaciones", e);
            return;
        }
        CLAIMS.clear();
        CLAIMS.putAll(nueva);
    }

    /** La reclamacion de un nicho, segun la cache. {@code null} si no hay fila. */
    public static Claim claim(String nichoId) {
        return CLAIMS.get(nichoId);
    }

    // ------------------------------------------------------------- eventos

    /** Se registra UNA vez, al arrancar (junto a los de Decorativos). */
    public static void registrar() {
        PlayerBlockBreakEvents.BEFORE.register((mundo, jugador, pos, estado, be) -> {
            if (mundo.isClient() || !(jugador instanceof ServerPlayerEntity sp)
                    || sp.hasPermissionLevel(2)) {
                return true;
            }
            return puedeTocar(sp, pos);
        });

        // ⚠ Una sola mano: sin esto el evento llega dos veces y el aviso sale
        //   doble. Es la misma guarda que el clic en las paradas.
        UseBlockCallback.EVENT.register((jugador, mundo, mano, golpe) -> {
            if (mundo.isClient() || !(jugador instanceof ServerPlayerEntity sp)
                    || sp.hasPermissionLevel(2)) {
                return ActionResult.PASS;
            }
            BlockPos pos = golpe.getBlockPos();
            var nicho = catalogo.en(pos);
            if (nicho == null) {
                return ActionResult.PASS;
            }
            // ⚠ El proyector SIEMPRE es nuestro: su clic abre el memorial,
            //   sea quien sea quien lo toque. Sin este corte, el mod de cartas
            //   abriria su menu de inventario encima del nuestro.
            if (pos.equals(nicho.proyector())) {
                return ActionResult.SUCCESS;
            }
            if (mano != Hand.MAIN_HAND) {
                return ActionResult.SUCCESS;
            }
            if (!puedeTocar(sp, pos)) {
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });

        UseItemCallback.EVENT.register((jugador, mundo, mano) -> {
            if (mundo.isClient() || !(jugador instanceof ServerPlayerEntity sp)
                    || sp.hasPermissionLevel(2)) {
                return TypedActionResult.pass(jugador.getStackInHand(mano));
            }
            // ⚠ Solo se corta COLOCAR: sin el filtro de BlockItem, comer una
            //   baya dentro del nicho de un amigo tambien quedaria cortado, y
            //   eso no protege nada -- solo molesta.
            if (!(jugador.getStackInHand(mano).getItem() instanceof BlockItem)) {
                return TypedActionResult.pass(jugador.getStackInHand(mano));
            }
            BlockPos pos = sp.getBlockPos();
            if (catalogo.en(pos) != null && !puedeTocar(sp, pos)) {
                return TypedActionResult.fail(jugador.getStackInHand(mano));
            }
            return TypedActionResult.pass(jugador.getStackInHand(mano));
        });
    }

    /**
     * ¿Puede este jugador tocar este bloque?
     *
     * <p>⚠ Sobre la CACHE, nunca sobre la base: esto corre en el hilo del
     * servidor. Y avisa al cortar -- un bloque que no se rompe sin explicacion
     * parece un fallo, y el proyecto ya pago esa confusion con los decorativos.
     */
    private static boolean puedeTocar(ServerPlayerEntity jugador, BlockPos pos) {
        if (!LunaDimensions.CIUDADELA.equals(
                jugador.getServerWorld().getRegistryKey())) {
            return true;
        }
        var nicho = catalogo.en(pos);
        if (nicho == null) {
            return true;
        }
        Claim c = CLAIMS.get(nicho.id());
        if (c == null || c.libre(System.currentTimeMillis())) {
            return true;
        }
        if (jugador.getUuid().equals(c.owner())) {
            return true;
        }
        jugador.sendMessage(net.minecraft.text.Text.translatable(
                "pokepad.lunaeternal.santuario.protegido"), true);
        return false;
    }
}
