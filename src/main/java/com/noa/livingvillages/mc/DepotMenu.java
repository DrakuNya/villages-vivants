package com.noa.livingvillages.mc;

import com.noa.livingvillages.core.Stock;
import com.noa.livingvillages.core.Town;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;

/**
 * L'entrepôt de la ville, ouvert comme un grand coffre (54 cases). Le joueur y prend et y dépose des objets : chaque
 * changement est recopié dans l'entrepôt. S'il y a plus de 54 piles, le surplus reste en réserve (invisible, intact).
 * Les villageois font une pause au dépôt pendant ce temps.
 */
public final class DepotMenu {
	private static final int SLOTS = 54;

	private DepotMenu() {
	}

	private static final class DepotContainer extends SimpleContainer {
		private final Town town;
		private final Stock reserve = new Stock();
		private boolean loading = true;

		DepotContainer(Town town) {
			super(SLOTS);
			this.town = town;
			int slot = 0;
			for (var e : town.stockpile.asMap().entrySet()) {
				String id = e.getKey();
				int left = e.getValue();
				ItemStack probe = ItemUtil.stackOf(id, 1);
				if (probe.isEmpty()) {
					reserve.add(id, left);
					continue;
				}
				int max = Math.max(1, probe.getMaxStackSize());
				while (left > 0) {
					if (slot >= SLOTS) {
						reserve.add(id, left);
						break;
					}
					int take = Math.min(left, max);
					setItem(slot++, ItemUtil.stackOf(id, take));
					left -= take;
				}
			}
			loading = false;
		}

		@Override
		public void setChanged() {
			super.setChanged();
			if (loading) return;
			Stock sum = reserve.copy();
			for (int i = 0; i < SLOTS; i++) {
				ItemStack st = getItem(i);
				if (!st.isEmpty()) sum.add(ItemUtil.idOf(st), st.getCount());
			}
			town.stockpile.replaceWith(sum);
			Director.pauseDepot();
			Towns.markDirty();
		}
	}

	public static void open(Player player, Town town) {
		DepotContainer container = new DepotContainer(town);
		Director.pauseDepot();
		player.openMenu(new SimpleMenuProvider((containerId, playerInventory, p) -> ChestMenu.sixRows(containerId, playerInventory, container),
				Component.literal("Entrepôt de " + town.name)));
	}
}
