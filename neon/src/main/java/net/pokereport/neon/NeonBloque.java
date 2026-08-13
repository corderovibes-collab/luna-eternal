package net.pokereport.neon;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** El cubo entero. La forma de la que salen todas las demás. */
public class NeonBloque extends Block {

    public NeonBloque(Settings settings) {
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
