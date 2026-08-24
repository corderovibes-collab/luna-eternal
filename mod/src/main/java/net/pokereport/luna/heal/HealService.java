package net.pokereport.luna.heal;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.pokereport.luna.LunaEternal;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Curación del equipo.
 *
 * <p><b>Gratis.</b> En Diosesmon curar es una función premium; aquí no, porque
 * un jugador sin curación se queda bloqueado, y cobrar por desbloquearse es
 * cobrar por jugar (P4).
 *
 * <p>Lo que sí tiene es un <b>cooldown</b>. Sin él, la salud deja de ser un
 * recurso y los combates dejan de tener consecuencia.
 */
public final class HealService {

    /** Minutos entre curaciones gratuitas. */
    public static final int COOLDOWN_MIN = 30;

    /**
     * Cooldown en memoria, y aquí sí es correcto.
     *
     * <p>Al contrario que el de los kits, este no protege valor económico: si un
     * reinicio lo borra, lo peor que pasa es que alguien cure diez minutos
     * antes. No merece una tabla ni una consulta por uso.
     */
    private static final Map<UUID, Long> ULTIMA = new ConcurrentHashMap<>();

    private HealService() {}

    /** Segundos que faltan para poder curar. 0 = ya se puede. */
    public static long restante(ServerPlayerEntity player) {
        Long t = ULTIMA.get(player.getUuid());
        if (t == null) return 0;
        long pasado = (System.currentTimeMillis() - t) / 1000;
        return Math.max(0, COOLDOWN_MIN * 60L - pasado);
    }

    /** ¿Hay algo que curar? Evita gastar el cooldown para nada. */
    public static boolean necesitaCura(ServerPlayerEntity player) {
        try {
            for (Pokemon p : Cobblemon.INSTANCE.getStorage().getParty(player)) {
                if (p != null && herido(p)) return true;
            }
        } catch (Throwable t) {
            LunaEternal.LOG.error("No se pudo leer el equipo", t);
        }
        return false;
    }

    private static boolean herido(Pokemon p) {
        return p.getCurrentHealth() < p.getMaxHealth() || p.getStatus() != null;
    }

    /** Cura el equipo si toca. Devuelve si se curó de verdad. */
    public static boolean curar(ServerPlayerEntity player) {
        if (restante(player) > 0) {
            player.sendMessage(Text.literal("§cTodavía no puedes curar."), false);
            return false;
        }
        try {
            Cobblemon.INSTANCE.getStorage().getParty(player).heal();
            ULTIMA.put(player.getUuid(), System.currentTimeMillis());
            player.playSoundToPlayer(SoundEvents.BLOCK_BEACON_ACTIVATE,
                SoundCategory.MASTER, 0.3f, 1.6f);
            player.sendMessage(Text.literal("§aTu equipo está como nuevo."), false);
            return true;
        } catch (Throwable t) {
            LunaEternal.LOG.error("No se pudo curar el equipo", t);
            player.sendMessage(Text.literal("§cNo se pudo curar."), false);
            return false;
        }
    }

    /** Al desconectar. Si no, el mapa crecería sin límite. */
    public static void olvidar(ServerPlayerEntity player) {
        ULTIMA.remove(player.getUuid());
    }
}
