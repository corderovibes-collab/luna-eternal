package net.pokereport.luna.item;

import java.util.List;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import net.pokereport.luna.ui.Tablist;

/**
 * Pieza física de armadura asociada a un rango (Élite, Campeón, Maestro, Leyenda).
 *
 * <p>Solo los jugadores que cuenten con el rango requerido o superior pueden equiparla.
 * Si un usuario intenta equipársela sin cumplir el rango, la acción se cancela y se le
 * notifica con un mensaje de advertencia.
 */
public class ArmaduraRangoItem extends ArmorItem {

    private final Tablist.Rank rango;
    private final String suitId;
    private final String pieza;

    public ArmaduraRangoItem(RegistryEntry<ArmorMaterial> material, Type type,
                             Settings settings, Tablist.Rank rango, String suitId, String pieza) {
        super(material, type, settings);
        this.rango = rango;
        this.suitId = suitId;
        this.pieza = pieza;
    }

    public Tablist.Rank rango() {
        return rango;
    }

    public String suitId() {
        return suitId;
    }

    public String pieza() {
        return pieza;
    }

    /**
     * Comprueba si el jugador tiene el rango necesario para equiparse esta armadura.
     */
    public boolean puedeEquipar(PlayerEntity player) {
        if (player == null) {
            return false;
        }
        if (player instanceof ServerPlayerEntity sp) {
            int escalon = Tablist.escalonDe(sp);
            return escalon >= this.rango.escalon;
        }
        return true;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!puedeEquipar(user)) {
            if (!world.isClient()) {
                user.sendMessage(Text.literal("§cNo tienes el rango " + rango.titulo + " §cpara equipar esta armadura."), true);
            }
            return TypedActionResult.fail(user.getStackInHand(hand));
        }
        return super.use(world, user, hand);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("§6Requisito de Rango: ").append(rango.conNombre()));
        tooltip.add(Text.literal("§7Solo miembros con rango " + rango.titulo + " §7o superior pueden equiparla."));
        tooltip.add(Text.literal("§cNo se puede vender en el mercado."));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
