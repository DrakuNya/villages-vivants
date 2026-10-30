package com.noa.livingvillages;

import com.noa.livingvillages.mc.Director;
import com.noa.livingvillages.mc.Interactions;
import com.noa.livingvillages.mc.LvCommands;
import com.noa.livingvillages.mc.ModItems;
import com.noa.livingvillages.mc.Towns;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LivingVillages implements ModInitializer {
	public static final String MOD_ID = "livingvillages";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModItems.init();
		Interactions.register();
		LvCommands.register();
		ServerLifecycleEvents.SERVER_STARTED.register(Towns::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(Towns::save);
		ServerTickEvents.END_SERVER_TICK.register(Director::tick);
		LOGGER.info("Villages Vivants chargé : {} recettes internes, {} plans.", Towns.RECIPES.size(), com.noa.livingvillages.mc.Plans.catalog().all().size());
	}
}
