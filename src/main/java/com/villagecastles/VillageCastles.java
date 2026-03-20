package com.villagecastles;

import com.villagecastles.command.GenerateCastleCommand;
import com.villagecastles.export.StructureExporter;
import com.villagecastles.integration.VillageBuilderIntegration;
import com.villagecastles.integration.VillageQuestsIntegration;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VillageCastles implements ModInitializer {
    public static final String MOD_ID = "villagecastles";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Set via -Dvillagecastles.exportall=true to auto-export all structures on first tick. */
    public static final boolean AUTO_EXPORT = Boolean.getBoolean("villagecastles.exportall");

    @Override
    public void onInitialize() {
        LOGGER.info("Village Castles initializing...");

        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            GenerateCastleCommand.register(dispatcher);
        });

        // Initialize optional mod integrations
        VillageBuilderIntegration.init();
        VillageQuestsIntegration.init();

        // Auto-export mode: generate and save all structures on first server tick, then stop
        if (AUTO_EXPORT) {
            LOGGER.info("Auto-export mode enabled — will generate all structures on first tick");
            ServerTickEvents.END_SERVER_TICK.register(new StructureExporter());
        }

        LOGGER.info("Village Castles initialized!");
    }
}
