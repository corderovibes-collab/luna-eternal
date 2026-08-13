package net.pokereport.neon;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.PillarBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Columna: carcasa oscura con una línea de luz que la recorre.
 *
 * <p>Es el bloque entero, no una barra: se apila y se orienta como un tronco,
 * así que sirve igual de columna vertical que de viga o de moldura horizontal.
 * El que sí es una barra fina es {@link NeonTubo}.
 */
public class NeonPilar extends PillarBlock {

    public NeonPilar(Settings settings) {
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
