package net.pokereport.neon;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * Barra de 4×4 a lo largo de un eje. El tubo de neón de toda la vida.
 *
 * <p>Se orienta como un tronco: sale alineado con la cara sobre la que haces
 * clic, así que una línea larga se traza sin pensar en la orientación.
 */
public class NeonTubo extends Block {

    public static final EnumProperty<Direction.Axis> AXIS = Properties.AXIS;

    private static final VoxelShape EJE_Y = createCuboidShape(6, 0, 6, 10, 16, 10);
    private static final VoxelShape EJE_X = createCuboidShape(0, 6, 6, 16, 10, 10);
    private static final VoxelShape EJE_Z = createCuboidShape(6, 6, 0, 10, 10, 16);

    public NeonTubo(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState()
                .with(AXIS, Direction.Axis.Y)
                .with(Neon.LUZ, Neon.POR_DEFECTO));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(AXIS, Neon.LUZ);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(AXIS, ctx.getSide().getAxis());
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState estado, BlockView mundo, BlockPos pos,
                                         ShapeContext contexto) {
        return switch (estado.get(AXIS)) {
            case X -> EJE_X;
            case Z -> EJE_Z;
            default -> EJE_Y;
        };
    }

    @Override
    protected BlockState rotate(BlockState estado, BlockRotation rotacion) {
        // Girar 90° intercambia X y Z; el eje Y no se entera. Es exactamente lo
        // que hace un tronco de vanilla, y hay que escribirlo o WorldEdit y
        // Axiom rotarían una fila de tubos dejándolos todos apuntando mal.
        if (rotacion == BlockRotation.CLOCKWISE_90
                || rotacion == BlockRotation.COUNTERCLOCKWISE_90) {
            return switch (estado.get(AXIS)) {
                case X -> estado.with(AXIS, Direction.Axis.Z);
                case Z -> estado.with(AXIS, Direction.Axis.X);
                default -> estado;
            };
        }
        return estado;
    }

    @Override
    protected ActionResult onUse(BlockState estado, World mundo, BlockPos pos,
                                 PlayerEntity jugador, BlockHitResult golpe) {
        return Neon.ciclar(estado, mundo, pos, jugador);
    }
}
