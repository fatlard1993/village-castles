package com.villagecastles.util;

import com.villagecastles.VillageCastles;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Captures generated structures from the world and saves them as NBT files.
 * This enables the generate → export pipeline without manual Structure Block usage.
 */
public class NbtExporter {

    /**
     * Capture a region of the world and save it as a compressed NBT structure file.
     *
     * @param world  The server world containing the generated structure
     * @param min    The minimum corner of the region
     * @param max    The maximum corner of the region
     * @param output The file path to write the NBT to
     * @return true if export succeeded
     */
    public static boolean exportRegion(ServerWorld world, BlockPos min, BlockPos max, Path output) {
        Vec3i size = new Vec3i(
            max.getX() - min.getX() + 1,
            max.getY() - min.getY() + 1,
            max.getZ() - min.getZ() + 1
        );

        try {
            StructureTemplate template = new StructureTemplate();
            template.saveFromWorld(world, min, size, true, List.of(Blocks.STRUCTURE_VOID));

            NbtCompound nbt = template.writeNbt(new NbtCompound());

            // Ensure parent directories exist
            Files.createDirectories(output.getParent());

            NbtIo.writeCompressed(nbt, output);

            VillageCastles.LOGGER.info("Exported structure to {}", output);
            return true;

        } catch (IOException e) {
            VillageCastles.LOGGER.error("Failed to write NBT file: {}", output, e);
            return false;
        } catch (Exception e) {
            VillageCastles.LOGGER.error("Failed to export structure", e);
            return false;
        }
    }

    /**
     * Get the output path for a structure NBT file within the mod's resource directory.
     * Resolves relative to the project source tree for development use.
     *
     * @param structurePath e.g., "plains/castle_small" or "village_walls/plains/wall_straight"
     * @param runDir        the server's run directory (used to find the project root)
     * @return the Path to write the NBT file to
     */
    public static Path getStructureOutputPath(String structurePath, Path runDir) {
        // From the run directory, go up to project root, then into source resources
        Path absoluteRunDir = runDir.toAbsolutePath();
        Path projectRoot = absoluteRunDir.getParent();
        return projectRoot.resolve("src/main/resources/data/villagecastles/structure")
            .resolve(structurePath + ".nbt");
    }
}
