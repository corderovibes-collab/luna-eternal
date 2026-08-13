package net.pokereport.neon;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * Chapa de 1 píxel pegada a cualquiera de las seis caras. El cartel.
 *
 * <p>{@code facing} es hacia dónde <b>mira</b> la cara visible, no dónde está
 * pegada, así que el volumen se apoya en el lado contrario del bloque. Puesto
 * de otro modo: al hacer clic en una pared, el panel aparece pegado a esa
 * pared mirándote — que es lo que espera cualquiera que coloque un cartel.
 */
public class NeonPanel extends Block {

    public static final DirectionProperty FACING = Properties.FACING;

    /** Indexado por {@link Direction#getId()}: DOWN, UP, NORTH, SOUTH, WEST, EAST. */
    private static final VoxelShape[] FORMAS = {
            createCuboidShape(0, 15, 0, 16, 16, 16),  // DOWN  — pegado al techo
            createCuboidShape(0, 0, 0, 16, 1, 16),    // UP    — pegado al suelo
            createCuboidShape(0, 0, 15, 16, 16, 16),  // NORTH — pegado al sur
            createCuboidShape(0, 0, 0, 16, 16, 1),    // SOUTH — pegado al norte
            createCuboidShape(15, 0, 0, 16, 16, 16),  // WEST  — pegado al este
            createCuboidShape(0, 0, 0, 1, 16, 16),    // EAST  — pegado al oeste
    };

    public NeonPanel(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState()
                .with(FACING, Direction.UP)
                .with(Neon.LUZ, Neon.POR_DEFECTO));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, Neon.LUZ);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getSide());
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState estado, BlockView mundo, BlockPos pos,
                                         ShapeContext contexto) {
        return FORMAS[estado.get(FACING).getId()];
    }

    @Override
    protected BlockState rotate(BlockState estado, BlockRotation rotacion) {
        return estado.with(FACING, rotacion.rotate(estado.get(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState estado, BlockMirror espejo) {
        return estado.with(FACING, espejo.apply(estado.get(FACING)));
    }

    @Override
    protected ActionResult onUse(BlockState estado, World mundo, BlockPos pos,
                                 PlayerEntity jugador, BlockHitResult golpe) {
        return Neon.ciclar(estado, mundo, pos, jugador);
    }
}
