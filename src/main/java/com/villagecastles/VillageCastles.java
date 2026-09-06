package com.villagecastles;

import com.villagecastles.command.GenerateCastleCommand;
import com.villagecastles.integration.VillageBuilderIntegration;
import com.villagecastles.integration.VillageQuestsIntegration;
import com.villagecastles.worldgen.CastleStructureRegistration;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VillageCastles implements ModInitializer {
    public static final String MOD_ID = "village-castles";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Village Castles initializing...");

        // Register structure type + piece type (must happen before world loads)
        CastleStructureRegistration.register();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            GenerateCastleCommand.register(dispatcher);
        });

        // Initialize optional mod integrations
        VillageBuilderIntegration.init();
        VillageQuestsIntegration.init();

        LOGGER.info("Village Castles initialized!");
    }
}
