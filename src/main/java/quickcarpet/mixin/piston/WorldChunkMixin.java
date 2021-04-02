package quickcarpet.mixin.piston;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import quickcarpet.utils.extensions.ExtendedWorldChunk;

import javax.annotation.Nullable;
import java.util.Map;

import static net.minecraft.world.chunk.WorldChunk.EMPTY_SECTION;

@Mixin(WorldChunk.class)
public abstract class WorldChunkMixin implements ExtendedWorldChunk, Chunk {
    @Shadow @Final private ChunkSection[] sections;
    @Shadow @Final private Map<Heightmap.Type, Heightmap> heightmaps;
    @Shadow private boolean shouldSave;
    @Shadow @Final private World world;
    @Nullable @Shadow public abstract BlockEntity getBlockEntity(BlockPos blockPos_1, WorldChunk.CreationType worldChunk$CreationType_1);
    @Shadow public abstract void addBlockEntity(BlockEntity blockEntity);

    @Shadow protected abstract <T extends BlockEntity> void updateTicker(T blockEntity);

    @Shadow public abstract void removeBlockEntity(BlockPos pos);

    /**
     * Sets the Blockstate and the BlockEntity.
     * Only sets BlockEntity if Block is BlockEntityProvider, but doesn't check if it actually matches (e.g. can assign beacon to chest entity).
     *
     * @author 2No2Name
     */
    @Nullable
    public BlockState setBlockStateWithBlockEntity(BlockPos pos, BlockState newBlockState, BlockEntity newBlockEntity, boolean moved) {
        int y = pos.getY();
        int sectionY = this.getSectionIndex(y);
        ChunkSection chunkSection = this.sections[sectionY];
        if (chunkSection == EMPTY_SECTION) {
            if (newBlockState.isAir()) {
                return null;
            }

            chunkSection = new ChunkSection(ChunkSectionPos.getSectionCoord(y));
            this.sections[sectionY] = chunkSection;
        }

        boolean sectionWasEmpty = chunkSection.isEmpty();
        int x = pos.getX() & 15;
        int inSectionY = y & 15;
        int z = pos.getZ() & 15;
        BlockState oldBlockState = chunkSection.setBlockState(x, inSectionY, z, newBlockState);
        if (oldBlockState == newBlockState) {
            return null;
        } else {
            Block newBlock = newBlockState.getBlock();
            this.heightmaps.get(Heightmap.Type.MOTION_BLOCKING).trackUpdate(x, y, z, newBlockState);
            this.heightmaps.get(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES).trackUpdate(x, y, z, newBlockState);
            this.heightmaps.get(Heightmap.Type.OCEAN_FLOOR).trackUpdate(x, y, z, newBlockState);
            this.heightmaps.get(Heightmap.Type.WORLD_SURFACE).trackUpdate(x, y, z, newBlockState);
            boolean sectionIsEmpty = chunkSection.isEmpty();
            if (sectionWasEmpty != sectionIsEmpty) {
                this.world.getChunkManager().getLightingProvider().setSectionStatus(pos, sectionIsEmpty);
            }

            boolean bl3 = oldBlockState.hasBlockEntity();
            if (!this.world.isClient) {
                //this is a movableBlockEntities special case, if condition wasn't there it would remove the blockentity that was carried for some reason
                if (!oldBlockState.isOf(Blocks.MOVING_PISTON)) {
                    oldBlockState.onStateReplaced(this.world, pos, newBlockState, moved); //this kills it
                }
            } else if (!oldBlockState.isOf(newBlock) && bl3) {
                this.removeBlockEntity(pos);
            }

            if (!chunkSection.getBlockState(x, inSectionY, z).isOf(newBlock)) {
                return null;
            } else {
                if (!this.world.isClient) {
                    newBlockState.onBlockAdded(this.world, pos, oldBlockState, moved);
                }

                if (newBlockState.hasBlockEntity()) {
                    BlockEntity blockEntity = this.getBlockEntity(pos, WorldChunk.CreationType.CHECK);
                    if (blockEntity == null && newBlockEntity != null) {
                        this.addBlockEntity(newBlockEntity);
                        blockEntity = newBlockEntity;
                    }
                    if (blockEntity == null) {
                        blockEntity = ((BlockEntityProvider)newBlock).createBlockEntity(pos, newBlockState);
                        if (blockEntity != null) {
                            this.addBlockEntity(blockEntity);
                        }
                    } else {
                        blockEntity.setCachedState(newBlockState);
                        this.updateTicker(blockEntity);
                    }
                }

                this.shouldSave = true;
                return oldBlockState;
            }
        }
    }
}
