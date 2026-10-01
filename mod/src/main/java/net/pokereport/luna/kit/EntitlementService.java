package net.pokereport.luna.kit;

import java.util.Set;
import java.util.UUID;

import net.minecraft.server.network.ServerPlayerEntity;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.rank.RankService;
import net.pokereport.luna.ui.Tablist.Rank;

/**
 * Única política de autorización de kits premium.
 *
 * <p>Los permisos operativos, OP y los rangos de staff no son entitlements.
 * La excepción se expresa exclusivamente mediante UUID persistido y auditable.
 */
public final class EntitlementService {

    /** UUID reales verificados contra la tabla player de producción (2026-09-30). */
    private static final Set<UUID> BYPASS = Set.of(
            UUID.fromString("432ef323-8ac3-3ba3-8175-aaf88c5589cf"), // TheJuanCE
            UUID.fromString("2387efe1-8498-3d4d-8b98-4aff8b50790c")  // A1ejandroReport
    );

    public boolean bypass(UUID uuid) {
        return uuid != null && BYPASS.contains(uuid);
    }

    /** Autoriza un kit de rango usando el rango comercial guardado, nunca OP. */
    public boolean canClaimRankKit(ServerPlayerEntity player, KitCatalog.Kit kit) {
        if (player == null || kit == null) return false;
        if (bypass(player.getUuid())) {
            LunaEternal.LOG.info("Bypass de entitlement utilizado por uuid={} para kit={}",
                    player.getUuid(), kit.id());
            return true;
        }
        Rank actual = RankService.enCache(player.getUuid());
        if (actual.equipo) return false;
        if (kit.requiredRank() == null) return true;
        Rank requerido = Rank.de(kit.requiredRank());
        // Los kits periódicos pertenecen al rango exacto. Subir de rango no
        // permite reclamar retrospectivamente todos los kits inferiores.
        return "rank".equals(kit.category()) && !kit.once()
                ? actual == requerido
                : actual.escalon >= requerido.escalon;
    }

    public boolean canClaimLegendArmor(ServerPlayerEntity player) {
        if (player == null) return false;
        return bypass(player.getUuid())
                || RankService.enCache(player.getUuid()) == Rank.LEYENDA;
    }

    /** Un exclusivo requiere compra persistida o uno de los dos UUID autorizados. */
    public boolean canClaimExclusiveKit(ServerPlayerEntity player, boolean owns) {
        if (player == null) return false;
        if (bypass(player.getUuid())) {
            LunaEternal.LOG.info("Bypass de entitlement utilizado por uuid={} para kit exclusivo",
                    player.getUuid());
            return true;
        }
        return owns;
    }
}
