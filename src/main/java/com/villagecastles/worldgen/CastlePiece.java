package com.villagecastles.worldgen;

import com.villagecastles.VillageCastles;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.block.Rotation;

public class CastlePiece extends TemplateStructurePiece {

    public CastlePiece(
            StructureTemplateManager templateManager,
            String biome,
            String size,
            BlockPos pos,
            Rotation rotation) {
        super(
            CastleStructureRegistration.CASTLE_PIECE_TYPE,
            0,
            templateManager,
            makeTemplateId(biome, size),
            makeTemplateId(biome, size).toString(),
            new StructurePlaceSettings().setRotation(rotation),
            pos
        );
    }

    public CastlePiece(StructureTemplateManager templateManager, CompoundTag tag) {
        super(
            CastleStructureRegistration.CASTLE_PIECE_TYPE,
            tag,
            templateManager,
            id -> new StructurePlaceSettings()
        );
    }

    private static Identifier makeTemplateId(String biome, String size) {
        return Identifier.fromNamespaceAndPath(VillageCastles.MOD_ID, biome + "/castle_" + size);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
    }

    @Override
    protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level,
            RandomSource random, BoundingBox boundingBox) {
        // Castle NBTs do not use data markers.
    }
}
