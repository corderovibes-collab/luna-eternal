package net.pokereport.luna.pokestop;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** Poste de dos bloques: ambas mitades identifican la misma parada. */
public final class LunarStopBlock extends TallPlantBlock {
    public static final MapCodec<LunarStopBlock> CODEC = createCodec(LunarStopBlock::new);
    public LunarStopBlock(Settings settings) { super(settings); }
    @Override public MapCodec<? extends TallPlantBlock> getCodec() { return CODEC; }
    @Override public BlockState getPlacementState(ItemPlacementContext ctx) {
        if (ctx.getPlayer() == null || !ctx.getPlayer().hasPermissionLevel(2)) return null;
        // La corona sobresale medio bloque; reservar aire para el remate.
        if (ctx.getBlockPos().getY() + 2 >= ctx.getWorld().getTopY()
                || !ctx.getWorld().getBlockState(ctx.getBlockPos().up(2)).isAir()) return null;
        return super.getPlacementState(ctx);
    }
    @Override protected boolean canPlantOnTop(BlockState floor, BlockView world, BlockPos pos) {
        return floor.isSideSolidFullSquare(world, pos, net.minecraft.util.math.Direction.UP);
    }
    @Override protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
        return state.get(HALF) == DoubleBlockHalf.LOWER
                ? Block.createCuboidShape(2, 0, 2, 14, 16, 14)
                : Block.createCuboidShape(2, 0, 4, 14, 24, 12);
    }
    @Override public void randomDisplayTick(BlockState state, World world, BlockPos pos,
                                            net.minecraft.util.math.random.Random random) {
        if (state.get(HALF) != DoubleBlockHalf.UPPER || random.nextInt(3) != 0) return;
        double angle = world.getTime() * .045 + (pos.asLong() & 255);
        double x = pos.getX() + .5 + Math.cos(angle) * .48;
        double y = pos.getY() + 1.1 + Math.sin(angle * 1.7) * .3;
        double z = pos.getZ() + .5 + Math.sin(angle) * .26;
        world.addParticle(net.minecraft.particle.ParticleTypes.END_ROD, x, y, z, 0, .006, 0);
        if (random.nextBoolean()) world.addParticle(net.minecraft.particle.ParticleTypes.END_ROD,
                pos.getX() + .15 + random.nextDouble() * .7,
                pos.getY() + .8 + random.nextDouble() * .65,
                pos.getZ() + .5, 0, .012, 0);
    }
    @Override protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (player instanceof ServerPlayerEntity sp) {
            BlockPos base = state.get(HALF) == DoubleBlockHalf.UPPER ? pos.down() : pos;
            LunarStops.claim(sp, base);
        }
        return ActionResult.SUCCESS;
    }
}
