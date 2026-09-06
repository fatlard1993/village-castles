package com.villagecastles.generator.ancient;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Two heightmap questions, answerable both at worldgen time (through the chunk generator's noise
 * columns, before any chunk exists) and against a live world (through real chunk heightmaps). The
 * classifier and the field sampler run on this interface so the debug commands exercise the same
 * code worldgen does.
 *
 * <p>Both methods return heightmap convention values: the Y of the first free block above the
 * surface, not the surface block itself. {@code surface} includes water; {@code floor} does not,
 * so {@code surface - floor} is water depth.
 */
public interface HeightSampler {

    int surface(int x, int z);

    int floor(int x, int z);

    static HeightSampler worldgen(Structure.GenerationContext context) {
        return new HeightSampler() {
            @Override
            public int surface(int x, int z) {
                return context.chunkGenerator().getFirstOccupiedHeight(
                    x, z, Heightmap.Types.WORLD_SURFACE_WG,
                    context.heightAccessor(), context.randomState());
            }

            @Override
            public int floor(int x, int z) {
                return context.chunkGenerator().getFirstOccupiedHeight(
                    x, z, Heightmap.Types.OCEAN_FLOOR_WG,
                    context.heightAccessor(), context.randomState());
            }
        };
    }

    /**
     * Live-world sampler for the debug commands. Runtime WORLD_SURFACE counts trees and buildings,
     * so readings drift from worldgen's view of the same terrain; a tuning instrument, not ground
     * truth.
     */
    static HeightSampler live(ServerLevel level) {
        return new HeightSampler() {
            @Override
            public int surface(int x, int z) {
                return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            }

            @Override
            public int floor(int x, int z) {
                return level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
            }
        };
    }
}
