package net.pokereport.luna.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.pokereport.luna.LunaEternal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.sql.Connection;
import java.sql.PreparedStatement;

@Mixin(BlockItem.class)
public class MixinBlockItem {

    @Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;", at = @At("RETURN"))
    private void luna$onBlockPlaced(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
        if (!cir.getReturnValue().isAccepted()) return;
        if (context.getWorld().isClient()) return;

        BlockState state = context.getWorld().getBlockState(context.getBlockPos());
        // Solo nos importan los minerales (Ores)
        if (state.isIn(BlockTags.DIAMOND_ORES) || state.isIn(BlockTags.EMERALD_ORES) ||
            state.isIn(BlockTags.GOLD_ORES) || state.isIn(BlockTags.IRON_ORES) ||
            state.isIn(BlockTags.REDSTONE_ORES) || state.isIn(BlockTags.LAPIS_ORES) ||
            state.isIn(BlockTags.COPPER_ORES) || state.isIn(BlockTags.COAL_ORES)) {
            
            BlockPos pos = context.getBlockPos();
            String dim = context.getWorld().getRegistryKey().getValue().toString();
            
            net.pokereport.luna.progression.PlacedOreManager.markPlaced(pos, dim);

        }
    }
}
