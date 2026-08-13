package net.pokereport.neon;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Media altura. Cornisas, escalones sueltos, rótulos horizontales. */
public class NeonLosa extends SlabBlock {

    public NeonLosa(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(Neon.LUZ, Neon.POR_DEFECTO));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(Neon.LUZ);
    }

    @Override
    protected ActionResult onUse(BlockState estado, World mundo, BlockPos pos,
                                 PlayerEntity jugador, BlockHitResult golpe) {
        return Neon.ciclar(estado, mundo, pos, jugador);
    }
}
