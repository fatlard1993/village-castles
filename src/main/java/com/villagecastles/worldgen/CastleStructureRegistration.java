package com.villagecastles.worldgen;

import com.villagecastles.VillageCastles;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.fabricmc.fabric.api.event.registry.RegistryAttributeHolder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * Registers the CastleStructure type and CastlePiece type into Minecraft's
 * built-in (synchronous) registries.
 *
 * <p>The Structure instance and StructureSet instance are provided via JSON data files
 * (dynamic/datapack registries) — not registered here.
 *
 * <p>Call {@link #register()} from {@link VillageCastles#onInitialize()}.
 */
public final class CastleStructureRegistration {

    private CastleStructureRegistration() {}

    /** The StructureType codec-holder, registered under "villagecastles:castle". */
    public static StructureType<CastleStructure> CASTLE_STRUCTURE_TYPE;

    /**
     * The StructurePieceType deserializer, registered under "villagecastles:castle_piece".
     * Used to reload CastlePiece instances from saved chunk NBT.
     */
    public static StructurePieceType CASTLE_PIECE_TYPE;

    public static void register() {
        CASTLE_PIECE_TYPE = Registry.register(
            BuiltInRegistries.STRUCTURE_PIECE,
            Identifier.fromNamespaceAndPath(VillageCastles.MOD_ID, "castle_piece"),
            (context, tag) -> new CastlePiece(context.structureTemplateManager(), tag)
        );

        // 2. Register the StructureType so JSON data files referencing
        //    "type": "villagecastles:castle" can be deserialized.
        CASTLE_STRUCTURE_TYPE = Registry.register(
            BuiltInRegistries.STRUCTURE_TYPE,
            Identifier.fromNamespaceAndPath(VillageCastles.MOD_ID, "castle"),
            () -> CastleStructure.CODEC
        );

        // Mark both registries as OPTIONAL so clients without village-castles installed
        // don't crash during Fabric's config-phase registry sync.
        RegistryAttributeHolder.get(BuiltInRegistries.STRUCTURE_TYPE).addAttribute(RegistryAttribute.OPTIONAL);
        RegistryAttributeHolder.get(BuiltInRegistries.STRUCTURE_PIECE).addAttribute(RegistryAttribute.OPTIONAL);

        VillageCastles.LOGGER.info("[villagecastles] Registered castle structure type and piece type");
    }
}
