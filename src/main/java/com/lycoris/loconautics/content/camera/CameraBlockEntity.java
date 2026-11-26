package com.lycoris.loconautics.content.camera;

import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Persistent world anchor for a Loconautics security camera.
 *
 * The live image is deliberately client-rendered; this block entity only
 * stores the authoritative camera location/orientation in the world.
 */
public final class CameraBlockEntity extends BlockEntity {

    public CameraBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public Direction facing() {
        if (getBlockState().hasProperty(CameraBlock.FACING)) {
            return getBlockState().getValue(CameraBlock.FACING);
        }
        return Direction.NORTH;
    }

    public float yaw() {
        return switch (facing()) {
            case SOUTH -> 0.0f;
            case WEST -> 90.0f;
            case NORTH -> 180.0f;
            case EAST -> -90.0f;
            default -> 0.0f;
        };
    }

    public float pitch() {
        return 0.0f;
    }
}
