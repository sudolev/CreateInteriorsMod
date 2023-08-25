package net.aaw.extendedseating.block.custom;

import com.google.common.base.Optional;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllShapes;
import com.simibubi.create.AllTags;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import com.simibubi.create.foundation.utility.BlockHelper;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.aaw.extendedseating.BigSeatEntity;
import net.aaw.extendedseating.block.ModBlocks;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.common.extensions.IForgeBlockState;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.stream.Stream;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class ChairBlockExtendsSeat extends SeatBlock implements ProperWaterloggedBlock {
    protected final DyeColor color;

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public ChairBlockExtendsSeat(Properties properties, DyeColor color) {
        super(properties, color);
        this.color = color;
        registerDefaultState(defaultBlockState().setValue(WATERLOGGED, false));
    }

    @Override
    public BlockState rotate(BlockState pState, Rotation pRotation) {
        return pState.setValue(FACING, pRotation.rotate(pState.getValue(FACING)));
    }
    public boolean isNormalCube(IForgeBlockState state){
        return false;
    }
    public boolean isOpaqueCube(IForgeBlockState state){
        return false;
    }
    @Override
    public BlockState mirror(BlockState pState, Mirror pMirror) {
        return pState.rotate(pMirror.getRotation(pState.getValue(FACING)));
    }
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        pBuilder.add(WATERLOGGED);
        pBuilder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        return this.defaultBlockState().setValue(FACING, pContext.getHorizontalDirection().getOpposite());
    }

    private static final VoxelShape SHAPE = Shapes.join(
            Block.box(0, 5, 0, 16, 13, 16),
            Block.box(0, 0, 4, 16, 5, 12),
            BooleanOp.OR
    );

    private static final VoxelShape SHAPE_BACK = Stream.of(
            Block.box(0, 0, 4, 16, 7, 12),
            Block.box(0, 7, 0, 16, 13, 16),
            Block.box(0, 13, 12, 16, 20, 16)
    ).reduce((v1, v2) -> Shapes.join(v1, v2, BooleanOp.OR)).get();

    public static void sitDown(Level world, BlockPos pos, Entity entity) {
        if (world.isClientSide)
            return;
        BigSeatEntity seat = new BigSeatEntity(world, pos);
        seat.setPos(pos.getX() + .5f, pos.getY() + .34f, pos.getZ() + .5f);
        world.addFreshEntity(seat);
        entity.startRiding(seat, true);
        if (entity instanceof TamableAnimal ta)
            ta.setInSittingPose(true);
    }
    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult p_225533_6_) {
        if (player.isShiftKeyDown())
            return InteractionResult.PASS;

        ItemStack heldItem = player.getItemInHand(hand);
        DyeColor color = DyeColor.getColor(heldItem);
        if (color != null && color != this.color) {
            if (world.isClientSide)
                return InteractionResult.SUCCESS;
            BlockState newState = BlockHelper.copyProperties(state, AllBlocks.SEATS.get(color)
                    .getDefaultState());
            world.setBlockAndUpdate(pos, newState);
            return InteractionResult.SUCCESS;
        }

        List<SeatEntity> seats = world.getEntitiesOfClass(SeatEntity.class, new AABB(pos));
        if (!seats.isEmpty()) {
            SeatEntity seatEntity = seats.get(0);
            List<Entity> passengers = seatEntity.getPassengers();
            if (!passengers.isEmpty() && passengers.get(0) instanceof Player)
                return InteractionResult.PASS;
            if (!world.isClientSide) {
                seatEntity.ejectPassengers();
                player.startRiding(seatEntity);
            }
            return InteractionResult.SUCCESS;
        }

        if (world.isClientSide)
            return InteractionResult.SUCCESS;
        sitDown(world, pos, getLeashed(world, player).or(player));
        return InteractionResult.SUCCESS;
    }
    @Override
    public void updateEntityAfterFallOn(BlockGetter reader, Entity entity) {
        BlockPos pos = entity.blockPosition();
        if (entity instanceof Player || !(entity instanceof LivingEntity) || !canBePickedUp(entity)
                || isSeatOccupied(entity.level(), pos)) {
            if (entity.isSuppressingBounce()) {
                super.updateEntityAfterFallOn(reader, entity);
                return;
            }

            Vec3 vec3 = entity.getDeltaMovement();
            if (vec3.y < 0.0D) {
                double d0 = entity instanceof LivingEntity ? 1.0D : 0.8D;
                entity.setDeltaMovement(vec3.x, -vec3.y * (double) 0.66F * d0, vec3.z);
            }

            return;
        }
        if (reader.getBlockState(pos)
                .getBlock() != this)
            return;
        sitDown(entity.level(), pos, entity);
    }

    public static VoxelShape rotateShape(Direction from, Direction to, VoxelShape shape) {
        VoxelShape[] buffer = new VoxelShape[]{shape, Shapes.empty()};

        int times = (to.ordinal() - from.get2DDataValue() + 4) % 4;
        for (int i = 0; i < times; i++) {
            buffer[0].forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> buffer[1] = Shapes.or(buffer[1], Shapes.create(1 - maxZ, minY, minX, 1 - minZ, maxY, maxX)));
            buffer[0] = buffer[1];
            buffer[1] = Shapes.empty();
        }

        return buffer[0];
    }
    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        switch ((Direction)pState.getValue(FACING)) {
            case NORTH:
                return SHAPE;
            case SOUTH:
                return rotateShape(Direction.NORTH, Direction.WEST, SHAPE);
            case WEST:
                return rotateShape(Direction.NORTH, Direction.EAST, SHAPE);
            case EAST:
            default:
                return rotateShape(Direction.NORTH, Direction.SOUTH, SHAPE);
        }
    }
    @Override
    public VoxelShape getCollisionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        switch ((Direction)pState.getValue(FACING)) {
            case NORTH:
                return SHAPE_BACK;
            case SOUTH:
                return rotateShape(Direction.NORTH, Direction.WEST, SHAPE_BACK);
            case WEST:
                return rotateShape(Direction.NORTH, Direction.EAST, SHAPE_BACK);
            case EAST:
            default:
                return rotateShape(Direction.NORTH, Direction.SOUTH, SHAPE_BACK);
        }
    }

}