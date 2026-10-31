package com.noa.livingvillages.mc;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.noa.livingvillages.core.BuildPlan;
import com.noa.livingvillages.core.BuildSite;
import com.noa.livingvillages.core.Role;
import com.noa.livingvillages.core.Theme;
import com.noa.livingvillages.core.Town;
import com.noa.livingvillages.core.TownBoard;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/**
 * Commandes de test (/lv ...). Le maire « idéal » placera lui-même les plans (voir la feuille de route) ;
 * en attendant, c'est le joueur qui le fait par commande.
 */
public final class LvCommands {
	private LvCommands() {
	}

	private static void say(CommandContext<CommandSourceStack> ctx, String text, ChatFormatting color) {
		ctx.getSource().sendSystemMessage(Component.literal(text).withStyle(color));
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("lv")
						.then(Commands.literal("plans").executes(LvCommands::plans))
						.then(Commands.literal("town").executes(LvCommands::town))
						.then(Commands.literal("auto")
								.then(Commands.literal("on").executes(ctx -> auto(ctx, true)))
								.then(Commands.literal("off").executes(ctx -> auto(ctx, false))))
						.then(Commands.literal("decide").executes(LvCommands::decide))
						.then(Commands.literal("place")
								.then(Commands.argument("plan", StringArgumentType.word()).executes(LvCommands::place)))
						.then(Commands.literal("stock")
								.then(Commands.argument("item", StringArgumentType.word())
										.then(Commands.argument("count", IntegerArgumentType.integer(1, 4096)).executes(LvCommands::stock))))
		));
	}

	private static String biomeOf(ServerLevel level, BlockPos pos) {
		return level.getBiome(pos).unwrapKey().map(k -> k.identifier().toString()).orElse("");
	}

	private static int plans(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String biome = biomeOf(ctx.getSource().getLevel(), player.blockPosition());
		say(ctx, "Plans possibles ici (" + biome + ", style " + Theme.fromBiome(biome).name().toLowerCase() + ") :", ChatFormatting.GOLD);
		for (BuildPlan p : Plans.catalog().eligible(biome)) {
			say(ctx, " • " + p.name + " — " + p.title + " (" + p.sizeX + "×" + p.sizeZ + ", " + p.sizeY + " étages)", ChatFormatting.YELLOW);
		}
		return 1;
	}

	private static int place(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = ctx.getSource().getLevel();
		BlockPos pos = player.blockPosition();
		Town town = Towns.nearest(pos.getX(), pos.getZ(), 96);
		if (town == null || town.mayorId == null) {
			say(ctx, "Pas de maire à proximité : donne d'abord un ruban d'élu à un villageois.", ChatFormatting.RED);
			return 0;
		}
		BuildPlan plan = Plans.catalog().get(StringArgumentType.getString(ctx, "plan"));
		if (plan == null) {
			say(ctx, "Plan inconnu. Essaie /lv plans", ChatFormatting.RED);
			return 0;
		}
		String biome = biomeOf(level, pos);
		if (!plan.allowedIn(biome)) {
			say(ctx, "Ce plan n'est pas adapté à ce biome (" + biome + ").", ChatFormatting.RED);
			return 0;
		}
		Direction d = player.getDirection();
		int rot = (d.get2DDataValue() + 2) % 4; // la porte (côté sud du plan) regarde le joueur
		int fx = plan.footprintX(rot), fz = plan.footprintZ(rot);
		int reach = 3 + Math.max(fx, fz) / 2;
		int cx = pos.getX() + d.getStepX() * reach;
		int cz = pos.getZ() + d.getStepZ() * reach;
		BuildSite site = new BuildSite(town.newSiteId(), plan, Theme.fromBiome(biome), cx - fx / 2, pos.getY() - 1, cz - fz / 2, rot, 0);
		site.priority = plan.meta.priority();
		town.sites.add(site);
		level.setBlock(Director.signPos(site), Blocks.OAK_SIGN.defaultBlockState(), 3);
		Towns.markDirty();
		say(ctx, "Plan « " + plan.title + " » posé. Clic droit sur le panneau pour voir les ressources.", ChatFormatting.GREEN);
		return 1;
	}

	private static Town townNear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		return Towns.nearest(player.blockPosition().getX(), player.blockPosition().getZ(), 96);
	}

	private static int auto(CommandContext<CommandSourceStack> ctx, boolean on) throws CommandSyntaxException {
		Town t = townNear(ctx);
		if (t == null) {
			say(ctx, "Aucune ville à proximité.", ChatFormatting.RED);
			return 0;
		}
		t.auto = on;
		Towns.markDirty();
		say(ctx, "Le maire de " + t.name + (on ? " décide désormais seul des constructions." : " attend tes ordres (/lv place, /lv decide)."), ChatFormatting.GREEN);
		return 1;
	}

	private static int decide(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Town t = townNear(ctx);
		if (t == null || t.mayorId == null) {
			say(ctx, "Aucune ville avec un maire à proximité.", ChatFormatting.RED);
			return 0;
		}
		String msg = Director.mayorThink(ctx.getSource().getLevel(), t, true);
		if (msg == null) {
			say(ctx, "Le maire n'a rien à décider (file pleine, maire absent ou pas de place).", ChatFormatting.YELLOW);
			return 0;
		}
		return 1;
	}

	private static int stock(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Town town = Towns.nearest(player.blockPosition().getX(), player.blockPosition().getZ(), 96);
		if (town == null) {
			say(ctx, "Aucune ville à proximité.", ChatFormatting.RED);
			return 0;
		}
		String item = StringArgumentType.getString(ctx, "item");
		if (!item.contains(":")) item = "minecraft:" + item;
		int n = IntegerArgumentType.getInteger(ctx, "count");
		town.stockpile.add(item, n);
		Towns.markDirty();
		say(ctx, "Entrepôt de " + town.name + " : +" + n + " " + item, ChatFormatting.GREEN);
		return 1;
	}

	private static int town(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Town t = Towns.nearest(player.blockPosition().getX(), player.blockPosition().getZ(), 96);
		if (t == null) {
			say(ctx, "Aucune ville à proximité.", ChatFormatting.RED);
			return 0;
		}
		say(ctx, "— " + t.name + " —", ChatFormatting.GOLD);
		for (Role r : Role.values()) {
			int n = t.membersWith(r).size();
			if (n > 0) say(ctx, r.displayName + " : " + n, ChatFormatting.YELLOW);
		}
		say(ctx, "Maire : " + (t.auto ? "automatique" : "manuel") + " — chantiers : " + t.sites.size() + ", chemins restants : " + t.pendingRoadCells(), ChatFormatting.YELLOW);
		for (BuildSite s : t.activeSites()) {
			say(ctx, " • " + s.plan.title + " : " + s.done() + "/" + s.total() + (s.prepDone ? "" : " (terrassement en attente)") + " [priorité " + s.priority + "]", ChatFormatting.YELLOW);
		}
		say(ctx, "Entrepôt : " + (t.stockpile.asMap().isEmpty() ? "vide" : t.stockpile.toString()), ChatFormatting.GRAY);
		for (TownBoard.Request r : t.board.all()) {
			say(ctx, "Besoin : " + r.item.replace("minecraft:", "") + " ×" + r.qty + (r.status == TownBoard.Status.CLAIMED ? " (pris en charge)" : ""), ChatFormatting.GRAY);
		}
		return 1;
	}
}
