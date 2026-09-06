package com.villagecastles.integration;

import com.villagecastles.VillageCastles;
import com.villagecastles.generator.ancient.HeightField;
import com.villagecastles.generator.ancient.HeightSampler;
import com.villagecastles.generator.ancient.Landform;
import com.villagecastles.generator.ancient.LandformSurvey;
import com.villagecastles.generator.ancient.Plan;
import com.villagecastles.generator.villager.VillagerCastleDesigner;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Optional integration with Village Builder: villages that grow can raise villager castles.
 *
 * <p>Castles are PROCEDURAL: instead of NBT template ids, three entries (small/medium/large)
 * register with a {@code BuildPlanProvider} that runs {@link VillagerCastleDesigner} at build
 * time against the village's own terrain and biome. All three share one limit group so a village
 * builds at most one castle, and a seeder tells Village Builder about castles that worldgen
 * already attached.
 *
 * <p>Everything is wired reflectively - no compile-time dependency in either direction. Against
 * a Village Builder without the procedural API, the integration logs one warning and no-ops.
 */
public class VillageBuilderIntegration {

    /** All castles count against this group, so a village builds one castle, not one per size. */
    private static final String CASTLE_LIMIT_GROUP = "village-castles:castle";

    /** One castle per village, whatever its size or biome. */
    private static final int CASTLES_PER_VILLAGE = 1;

    private static boolean initialized = false;

    // Cached reflection references
    private static Method registerProceduralMethod;
    private static Constructor<?> materialReqConstructor;
    private static Constructor<?> buildPlanConstructor;
    private static Constructor<?> chestConstructor;
    private static Class<?> providerInterface;
    private static Object needDefense;
    private static Object needHousing;

    public static void init() {
        if (initialized) return;
        initialized = true;

        if (!FabricLoader.getInstance().isModLoaded("village-builder")) {
            VillageCastles.LOGGER.info("Village Builder not found, skipping integration");
            return;
        }

        VillageCastles.LOGGER.info("Village Builder detected, registering procedural castles...");

        try {
            Class<?> apiClass = Class.forName("justfatlard.village_builder.api.VillageBuilderAPI");

            @SuppressWarnings("unchecked")
            Class<Enum<?>> needEnum = (Class<Enum<?>>) Class.forName(
                "justfatlard.village_builder.village.VillageNeedsAnalyzer$VillageNeed");
            needDefense = Enum.valueOf((Class) needEnum, "DEFENSE");
            needHousing = Enum.valueOf((Class) needEnum, "HOUSING");

            Class<?> matReqClass = Class.forName(
                "justfatlard.village_builder.building.StructureType$MaterialRequirement");
            materialReqConstructor = matReqClass.getDeclaredConstructor(Item.class, int.class);

            // The procedural API. Any of these missing means Village Builder predates
            // generator-built structures: one warning, no castles offered, nothing breaks.
            Class<?> buildPlanClass = Class.forName("justfatlard.village_builder.api.BuildPlan");
            buildPlanConstructor = buildPlanClass.getDeclaredConstructor(
                Vec3i.class, Map.class, List.class);
            Class<?> chestClass = Class.forName("justfatlard.village_builder.api.BuildPlan$Chest");
            chestConstructor = chestClass.getDeclaredConstructor(
                BlockPos.class, Direction.class, Identifier.class);
            providerInterface = Class.forName("justfatlard.village_builder.api.BuildPlanProvider");
            registerProceduralMethod = apiClass.getMethod("registerProceduralPersistent",
                Identifier.class, String.class, Set.class, List.class, Set.class, int.class,
                String.class, int.class, providerInterface);

            registerCastleSeeder(apiClass);
            registerProceduralCastles();
            VillageCastles.LOGGER.info("Registered 3 procedural castle sizes with Village Builder");
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            VillageCastles.LOGGER.warn("Village Builder is too old for procedural structures; "
                + "village-built castles disabled ({})", e.getMessage());
        } catch (Exception e) {
            VillageCastles.LOGGER.error("Failed to register with Village Builder: {}", e.getMessage());
        }
    }

    /**
     * Tell Village Builder how to spot a castle it did not build: worldgen-attached castles carry
     * a CastleGroundsPiece, and the census counts those, so a village that generated with a keep
     * is never offered another.
     */
    private static void registerCastleSeeder(Class<?> apiClass) {
        try {
            Class<?> seederInterface = Class.forName(
                "justfatlard.village_builder.api.VillageBuilderAPI$LimitGroupSeeder");
            Method register = apiClass.getMethod("registerLimitGroupSeeder", String.class, seederInterface);

            Object seeder = Proxy.newProxyInstance(
                seederInterface.getClassLoader(),
                new Class<?>[]{seederInterface},
                (proxy, method, args) -> switch (method.getName()) {
                    case "countExisting" -> CastleCensus.countCastlesInVillage(
                        (ServerLevel) args[0], (BlockPos) args[1]);
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> CASTLE_LIMIT_GROUP + " seeder";
                    default -> throw new UnsupportedOperationException(
                        "Village Builder's LimitGroupSeeder gained an unexpected method: " + method.getName());
                });

            register.invoke(null, CASTLE_LIMIT_GROUP, seeder);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            VillageCastles.LOGGER.warn("Village Builder cannot be told about pre-existing castles; "
                + "a village that generated with one may still build another.");
        } catch (Exception e) {
            VillageCastles.LOGGER.warn("Failed to register castle seeder: {}", e.getMessage());
        }
    }

    private static void registerProceduralCastles() throws Exception {
        register(VillagerCastleDesigner.Size.SMALL, "Watch Keep",
            Set.of(needDefense),
            Map.of(Items.COBBLESTONE, 96, Items.OAK_PLANKS, 32, Items.OAK_LOG, 16, Items.TORCH, 8));
        register(VillagerCastleDesigner.Size.MEDIUM, "Walled Fort",
            Set.of(needDefense),
            Map.of(Items.COBBLESTONE, 256, Items.OAK_PLANKS, 64, Items.OAK_LOG, 32, Items.TORCH, 16));
        register(VillagerCastleDesigner.Size.LARGE, "Castle Seat",
            Set.of(needDefense, needHousing),
            Map.of(Items.COBBLESTONE, 448, Items.OAK_PLANKS, 128, Items.OAK_LOG, 64, Items.TORCH, 24));
    }

    private static void register(VillagerCastleDesigner.Size size, String displayName,
                                 Set<Object> needs, Map<Item, Integer> materials) throws Exception {
        List<Object> requirements = new ArrayList<>();
        for (Map.Entry<Item, Integer> entry : materials.entrySet()) {
            requirements.add(materialReqConstructor.newInstance(entry.getKey(), entry.getValue()));
        }

        Object provider = Proxy.newProxyInstance(
            providerInterface.getClassLoader(),
            new Class<?>[]{providerInterface},
            (proxy, method, args) -> switch (method.getName()) {
                case "generate" -> generatePlan(size,
                    (ServerLevel) args[0], (BlockPos) args[1], (RandomSource) args[2],
                    (String) args[3], (Direction) args[4]);
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "village-castles " + size.id() + " castle provider";
                default -> throw new UnsupportedOperationException(
                    "Village Builder's BuildPlanProvider gained an unexpected method: " + method.getName());
            });

        registerProceduralMethod.invoke(null,
            Identifier.fromNamespaceAndPath(VillageCastles.MOD_ID, "villager_castle_" + size.id()),
            displayName, needs, requirements, Set.of(), size.clearance,
            CASTLE_LIMIT_GROUP, CASTLES_PER_VILLAGE, provider);
    }

    /**
     * The provider body: run the villager designer against the live terrain at the chosen site,
     * then hand the plan back in Village Builder's origin-relative shape.
     */
    private static Object generatePlan(VillagerCastleDesigner.Size size, ServerLevel world,
                                       BlockPos origin, RandomSource random, String biomeKey,
                                       Direction facing) {
        try {
            // Village Builder's origin y is the first free block; the designer anchors on the
            // ground block itself, centred on the footprint the clearance declared.
            BlockPos anchor = new BlockPos(
                origin.getX() + size.clearance / 2, origin.getY() - 1,
                origin.getZ() + size.clearance / 2);
            HeightSampler sampler = HeightSampler.live(world);
            HeightField field = LandformSurvey.sampleField(sampler,
                new LandformSurvey.Site(Landform.HILLTOP, anchor, size.clearance / 2, facing));

            long seed = random.nextLong();
            Plan plan = VillagerCastleDesigner.draw(
                new VillagerCastleDesigner.Site(anchor, size, facing), field, biomeKey, seed);

            // Origin-relative per the BuildPlan contract: y = 0 lands on Village Builder's
            // ground course, which is exactly the designer's standing course.
            Map<BlockPos, BlockState> relative = new LinkedHashMap<>();
            for (Map.Entry<BlockPos, BlockState> entry : plan.blocks().entrySet()) {
                relative.put(entry.getKey().subtract(origin), entry.getValue());
            }
            List<Object> chests = new ArrayList<>();
            for (Plan.Chest chest : plan.chests()) {
                chests.add(chestConstructor.newInstance(
                    chest.pos().subtract(origin), chest.facing(),
                    chest.table().identifier()));
            }

            return buildPlanConstructor.newInstance(
                new Vec3i(size.clearance, 34, size.clearance), relative, chests);
        } catch (Exception e) {
            VillageCastles.LOGGER.error("Villager castle provider failed at {}: {}",
                origin, e.getMessage());
            return null;
        }
    }
}
