package net.pokereport.neon;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.StairsBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Escalera.
 *
 * <p>Vale bastante más que para subir: con las variantes interior y exterior,
 * una escalera es la única pieza de vanilla que dobla una esquina en 45°. Un
 * borde de tejado o un remate de cornisa en neón se hace con esto.
 */
public class NeonEscalera extends StairsBlock {

    public NeonEscalera(BlockState base, Settings settings) {
        super(base, settings);
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
