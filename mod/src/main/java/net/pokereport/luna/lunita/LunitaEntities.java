package net.pokereport.luna.lunita;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.pokereport.luna.LunaEternal;

/** Registro común: existe en cliente y servidor, no depende de Cobblemon. */
public final class LunitaEntities {
    public static final EntityType<LunitaEntity> LUNITA = Registry.register(
            Registries.ENTITY_TYPE, Identifier.of(LunaEternal.MOD_ID, "lunita"),
            EntityType.Builder.create(LunitaEntity::new, SpawnGroup.CREATURE)
                    .dimensions(0.90f, 1.00f)
                    .maxTrackingRange(10).trackingTickInterval(2).build());

    private LunitaEntities() {}
    public static void register() { /* fuerza la carga estática desde el entrypoint común */ }
}
