package com.noa.livingvillages.mc;

import com.noa.livingvillages.core.BuildSite;
import com.noa.livingvillages.core.ConversionPlanner;
import com.noa.livingvillages.core.Role;
import com.noa.livingvillages.core.Settlement;
import com.noa.livingvillages.core.Stock;
import com.noa.livingvillages.core.Town;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Ce qui se passe quand le joueur clique : donner un objet à un villageois, lire un panneau « en construction ». */
public final class Interactions {
	private Interactions() {
	}

	public static void register() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (!(level instanceof ServerLevel serverLevel) || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
			if (!(entity instanceof Villager villager) || villager.isBaby()) return InteractionResult.PASS;
			ItemStack stack = player.getItemInHand(hand);
			if (stack.isEmpty()) {
				// main vide + accroupi sur un habitant : on ouvre son sac
				Town t = Towns.ofMember(villager.getUUID().toString());
				if (t == null || !player.isShiftKeyDown()) return InteractionResult.PASS;
				BagMenu.open(player, t, villager.getUUID().toString());
				return InteractionResult.SUCCESS;
			}
			String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			Role role = Role.byItem(itemId);
			if (role == null) {
				// accroupi + objet en main sur un habitant : don à la ville (va dans l'entrepôt)
				Town t = Towns.ofMember(villager.getUUID().toString());
				if (t == null || !player.isShiftKeyDown()) return InteractionResult.PASS;
				int n = stack.getCount();
				t.stockpile.add(itemId, n);
				if (!player.getAbilities().instabuild) stack.shrink(n);
				Towns.markDirty();
				player.sendSystemMessage(Component.literal("Don à " + t.name + " : " + itemId.replace("minecraft:", "") + " ×" + n + " (entrepôt).").withStyle(ChatFormatting.GREEN));
				return InteractionResult.SUCCESS;
			}
			// Pour les outils « normaux » (hache, pioche...), il faut s'accroupir : sinon on ouvre le commerce comme d'habitude.
			boolean ownItem = role == Role.MAYOR || role == Role.BUILDER;
			if (!ownItem && !player.isShiftKeyDown()) return InteractionResult.PASS;
			assign(serverLevel, player, villager, role, stack);
			return InteractionResult.SUCCESS;
		});

		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (!(level instanceof ServerLevel) || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
			BlockPos clicked = hitResult.getBlockPos();
			ItemStack held = player.getItemInHand(hand);
			// charte des colons : fonde une colonie à l'endroit visé
			if (!held.isEmpty() && ItemUtil.idOf(held).equals("livingvillages:founders_charter") && level instanceof ServerLevel sl) {
				String err = Settlers.found(sl, clicked.above());
				if (err != null) player.sendSystemMessage(Component.literal(err).withStyle(ChatFormatting.YELLOW));
				else if (!player.getAbilities().instabuild) held.shrink(1);
				return InteractionResult.SUCCESS;
			}
			// coffre de ville : ouvre l'entrepôt de la ville la plus proche (accroupi : on pose un bloc comme d'habitude)
			if (level.getBlockState(clicked).getBlock() == ModBlocks.TOWN_CHEST && !player.isShiftKeyDown()) {
				Town near = Towns.nearest(clicked.getX(), clicked.getZ(), 96);
				if (near == null) player.sendSystemMessage(Component.literal("Ce coffre de ville n'est relié à aucune ville.").withStyle(ChatFormatting.YELLOW));
				else DepotMenu.open(player, near);
				return InteractionResult.SUCCESS;
			}
			for (Town t : Towns.all()) {
				// le coffre du dépôt s'ouvre sur l'entrepôt de la ville
				int[] chest = t.depotChest();
				if (chest != null && clicked.equals(new BlockPos(chest[0], chest[1], chest[2]))) {
					DepotMenu.open(player, t);
					return InteractionResult.SUCCESS;
				}
				for (BuildSite s : t.sites) {
					BlockPos sign = Director.signPos(s);
					boolean onSign = !t.dense && sign.equals(clicked);
					// en ville compacte : un clic sur un bâtiment pas encore terminé affiche son état
					int[] f = s.footprint();
					boolean onBuilding = t.dense && !s.complete() && s.prepDone && clicked.getX() >= f[0] && clicked.getX() <= f[2]
							&& clicked.getZ() >= f[1] && clicked.getZ() <= f[3] && clicked.getY() >= s.oy - 1 && clicked.getY() <= s.oy + s.plan.sizeY + 1;
					if (onSign || onBuilding) {
						showSite(player, t, s);
						return InteractionResult.SUCCESS;
					}
				}
			}
			return InteractionResult.PASS;
		});
	}

	private static void assign(ServerLevel level, Player player, Villager villager, Role role, ItemStack stack) {
		String uuid = villager.getUUID().toString();
		BlockPos pos = villager.blockPosition();
		Town town = Towns.ofMember(uuid);

		if (role == Role.MAYOR) {
			if (town != null && uuid.equals(town.mayorId)) {
				player.sendSystemMessage(Component.literal("Ce villageois est déjà le maire de " + town.name + ".").withStyle(ChatFormatting.YELLOW));
				return;
			}
			Town near = Towns.nearest(pos.getX(), pos.getZ(), 96);
			if (near != null && near.mayorId != null) {
				player.sendSystemMessage(Component.literal("Il y a déjà un maire dans les environs (" + near.name + ").").withStyle(ChatFormatting.YELLOW));
				return;
			}
			if (town == null && near != null) town = near; // mairie vacante : on la reprend
			if (town == null) {
				town = new Town(Towns.newTownId(), "Village " + (Towns.all().size() + 1));
				// le vrai centre d'un village existant, c'est sa cloche
				BlockPos bell = Director.findBell(level, pos);
				BlockPos c = bell != null ? bell : pos;
				town.centerX = c.getX();
				town.centerY = c.getY();
				town.centerZ = c.getZ();
				town.externalWell = Director.detectWell(level, c);
				town.dense = true; // ville compacte par défaut (/lv layout spaced pour l'ancien mode)
				town.stockpile.add("minecraft:emerald", Settlement.TREASURY_EMERALDS); // le trésor de la ville
				Towns.all().add(town);
			}
			town.mayorId = uuid;
			town.members.put(uuid, Role.MAYOR);
			villager.setCustomName(Component.literal("Maire"));
			villager.setCustomNameVisible(true);
			player.sendSystemMessage(Component.literal("Un nouveau maire est élu : " + town.name + " peut désormais planifier des bâtiments (/lv plans, /lv place <plan>).").withStyle(ChatFormatting.GREEN));
		} else {
			if (town == null) town = Towns.nearest(pos.getX(), pos.getZ(), 96);
			if (town == null || town.mayorId == null) {
				player.sendSystemMessage(Component.literal("Il faut d'abord élire un maire (ruban d'élu) dans le village.").withStyle(ChatFormatting.YELLOW));
				return;
			}
			if (uuid.equals(town.mayorId)) {
				player.sendSystemMessage(Component.literal("Le maire garde sa fonction.").withStyle(ChatFormatting.YELLOW));
				return;
			}
			town.members.put(uuid, role);
			villager.setCustomName(Component.literal(role.displayName));
			villager.setCustomNameVisible(true);
			player.sendSystemMessage(Component.literal("Ce villageois est maintenant " + role.displayName.toLowerCase() + " de " + town.name + ".").withStyle(ChatFormatting.GREEN));
		}
		if (!player.getAbilities().instabuild) stack.shrink(1);
		Towns.markDirty();
	}

	/** Le métier d'un habitant, ce qu'il porte, et ce qu'il est en train de faire. */
	static void showMember(Player player, Town town, String id) {
		Role role = town.members.get(id);
		player.sendSystemMessage(Component.literal("— " + (role == null ? "Habitant" : role.displayName) + " de " + town.name + " —").withStyle(ChatFormatting.GOLD));
		var bag = town.inventoryOf(id);
		if (bag.isEmpty()) {
			player.sendSystemMessage(Component.literal("Sac vide.").withStyle(ChatFormatting.GRAY));
		} else {
			for (var e : bag.asMap().entrySet()) {
				player.sendSystemMessage(Component.literal(" • " + e.getKey().replace("minecraft:", "") + " ×" + e.getValue()).withStyle(ChatFormatting.GREEN));
			}
		}
		var req = town.board.claimedBy(id);
		if (req != null) {
			player.sendSystemMessage(Component.literal("Mission : récolter " + req.item.replace("minecraft:", "") + " (reste " + req.qty + ")").withStyle(ChatFormatting.YELLOW));
		}
	}

	/** Le panneau : ressources disponibles en vert, manquantes en gris. */
	static void showSite(Player player, Town town, BuildSite site) {
		player.sendSystemMessage(Component.literal("— En construction : " + site.plan.title + " (" + town.name + ") —").withStyle(ChatFormatting.GOLD));
		player.sendSystemMessage(Component.literal("Avancement : " + site.done() + "/" + site.total() + " blocs, étage " + site.currentLayer()
				+ (site.complete() ? " (terminé !)" : "")).withStyle(ChatFormatting.YELLOW));
		if (site.complete()) return;
		Stock stock = town.stockpile;
		for (var e : site.remainingNeeds().entrySet()) {
			ConversionPlanner.Result r = Towns.PLANNER.plan(stock, e.getKey(), e.getValue());
			String shortName = e.getKey().replace("minecraft:", "");
			if (r.complete()) {
				player.sendSystemMessage(Component.literal("✔ " + shortName + " ×" + e.getValue()).withStyle(ChatFormatting.GREEN));
			} else {
				player.sendSystemMessage(Component.literal("✘ " + shortName + " ×" + e.getValue() + "  (manque : " + describe(r.missing()) + ")").withStyle(ChatFormatting.GRAY));
			}
		}
	}

	private static String describe(java.util.Map<String, Integer> m) {
		StringBuilder sb = new StringBuilder();
		for (var e : m.entrySet()) {
			if (sb.length() > 0) sb.append(", ");
			sb.append(e.getKey().replace("minecraft:", "")).append(" ×").append(e.getValue());
		}
		return sb.toString();
	}
}
