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
 * Registers the castle structure and piece types into Minecraft's built-in (synchronous)
 * registries. The Structure and StructureSet instances come from JSON data files
 * (dynamic/datapack registries): not registered here.
 *
 * <p>Call {@link #register()} from {@link VillageCastles#onInitialize()}.
 */
public final class CastleStructureRegistration {

    private CastleStructureRegistration() {}

    /**
     * The StructurePieceType deserializer for {@link CastleGroundsPiece}, registered under
     * "village-castles:castle_grounds". Attached alongside every village-attached castle to
     * underfill its footprint and seed its garrison.
     */
    public static StructurePieceType CASTLE_GROUNDS_PIECE_TYPE;

    /** The StructureType for procedurally generated ancient castles, "village-castles:ancient_castle". */
    public static StructureType<AncientCastleStructure> ANCIENT_CASTLE_STRUCTURE_TYPE;

    /** The StructurePieceType deserializer for {@link AncientCastlePiece}. */
    public static StructurePieceType ANCIENT_CASTLE_PIECE_TYPE;

    /** The StructurePieceType deserializer for {@link VillagerCastlePiece}. */
    public static StructurePieceType VILLAGER_CASTLE_PIECE_TYPE;

    public static void register() {
        CASTLE_GROUNDS_PIECE_TYPE = Registry.register(
            BuiltInRegistries.STRUCTURE_PIECE,
            Identifier.fromNamespaceAndPath(VillageCastles.MOD_ID, "castle_grounds"),
            (context, tag) -> new CastleGroundsPiece(tag)
        );

        ANCIENT_CASTLE_PIECE_TYPE = Registry.register(
            BuiltInRegistries.STRUCTURE_PIECE,
            Identifier.fromNamespaceAndPath(VillageCastles.MOD_ID, "ancient_castle_piece"),
            (context, tag) -> new AncientCastlePiece(tag)
        );

        VILLAGER_CASTLE_PIECE_TYPE = Registry.register(
            BuiltInRegistries.STRUCTURE_PIECE,
            Identifier.fromNamespaceAndPath(VillageCastles.MOD_ID, "villager_castle_piece"),
            (context, tag) -> new VillagerCastlePiece(tag)
        );

        ANCIENT_CASTLE_STRUCTURE_TYPE = Registry.register(
            BuiltInRegistries.STRUCTURE_TYPE,
            Identifier.fromNamespaceAndPath(VillageCastles.MOD_ID, "ancient_castle"),
            () -> AncientCastleStructure.CODEC
        );

        // Mark both registries as OPTIONAL so clients without village-castles installed
        // don't crash during Fabric's config-phase registry sync.
        RegistryAttributeHolder.get(BuiltInRegistries.STRUCTURE_TYPE).addAttribute(RegistryAttribute.OPTIONAL);
        RegistryAttributeHolder.get(BuiltInRegistries.STRUCTURE_PIECE).addAttribute(RegistryAttribute.OPTIONAL);

        VillageCastles.LOGGER.info("[village-castles] Registered castle structure type and piece type");
    }
}
