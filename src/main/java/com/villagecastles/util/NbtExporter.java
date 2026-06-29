package com.villagecastles.util;

import com.villagecastles.VillageCastles;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;

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
    public static boolean exportRegion(ServerLevel world, BlockPos min, BlockPos max, Path output) {
        Vec3i size = new Vec3i(
            max.getX() - min.getX() + 1,
            max.getY() - min.getY() + 1,
            max.getZ() - min.getZ() + 1
        );

        try {
            StructureTemplate template = new StructureTemplate();
            template.fillFromWorld(world, min, size, true, List.of(Blocks.STRUCTURE_VOID));

            CompoundTag nbt = template.save(new CompoundTag());

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
        // Walk up from the run directory looking for the village-castles project root,
        // so this works whether the mod is running from its own server or from village-quests.
        Path dir = runDir.toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("src/main/resources/data/villagecastles/structure");
            if (candidate.toFile().exists()) {
                return candidate.resolve(structurePath + ".nbt");
            }
            // Also check sibling directories (e.g. running from village-quests/run/)
            Path parent = dir.getParent();
            if (parent != null) {
                Path sibling = parent.resolve("village-castles/src/main/resources/data/villagecastles/structure");
                if (sibling.toFile().exists()) {
                    return sibling.resolve(structurePath + ".nbt");
                }
            }
            dir = parent;
        }
        // Fallback: original behaviour
        return runDir.toAbsolutePath().getParent()
            .resolve("src/main/resources/data/villagecastles/structure")
            .resolve(structurePath + ".nbt");
    }

    /**
     * Get the .polished marker path for a structure NBT.
     */
    public static Path getPolishedMarkerPath(Path nbtPath) {
        String name = nbtPath.getFileName().toString().replace(".nbt", ".polished");
        return nbtPath.getParent().resolve(name);
    }

    /**
     * Check if a structure has been marked as hand-polished.
     */
    public static boolean isPolished(Path nbtPath) {
        return Files.exists(getPolishedMarkerPath(nbtPath));
    }

    /**
     * Mark a structure as hand-polished. Creates a .polished marker file
     * that prevents export/exportall from overwriting it.
     */
    public static void markPolished(Path nbtPath) throws IOException {
        Path marker = getPolishedMarkerPath(nbtPath);
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "Hand-polished. Use 'export <biome> <size> force' to overwrite.\n");
        VillageCastles.LOGGER.info("Marked as polished: {}", marker);
    }
}
