package com.noa.livingvillages;

import com.noa.livingvillages.core.Town;
import com.noa.livingvillages.mc.Director;
import com.noa.livingvillages.mc.Interactions;
import com.noa.livingvillages.mc.LvCommands;
import com.noa.livingvillages.mc.ModItems;
import com.noa.livingvillages.mc.Towns;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LivingVillages implements ModInitializer {
	public static final String MOD_ID = "livingvillages";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static void memberLost(LivingEntity entity) {
		if (!(entity instanceof Villager) || !(entity.level() instanceof ServerLevel level)) return;
		String id = entity.getUUID().toString();
		Town town = Towns.ofMember(id);
		if (town == null) return;
		boolean wasMayor = id.equals(town.mayorId);
		town.removeMember(id);
		Director.forget(id);
		Towns.markDirty();
		Director.broadcast(level, town, wasMayor ? "Le maire de " + town.name + " a disparu : il faut élire un nouveau maire (ruban d'élu)." : "Un habitant de " + town.name + " a disparu.");
	}

	@Override
	public void onInitialize() {
		com.noa.livingvillages.mc.ModBlocks.init();
		ModItems.init();
		Interactions.register();
		LvCommands.register();
		ServerLifecycleEvents.SERVER_STARTED.register(Towns::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(Towns::save);
		ServerTickEvents.END_SERVER_TICK.register(Director::tick);
		// un habitant tué ou transformé en zombie : on récupère son sac et on libère ses commandes
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> memberLost(entity));
		ServerLivingEntityEvents.MOB_CONVERSION.register((previous, converted, keepEquipment) -> memberLost(previous));
		LOGGER.info("Villages Vivants chargé : {} recettes internes, {} plans.", Towns.RECIPES.size(), com.noa.livingvillages.mc.Plans.catalog().all().size());
	}
}
