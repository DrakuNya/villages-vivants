package com.noa.livingvillages.mc;

import com.noa.livingvillages.core.Inventory;
import com.noa.livingvillages.core.Role;
import com.noa.livingvillages.core.Town;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.item.ItemStack;

/**
 * L'inventaire d'un villageois, ouvert comme un coffre de 9 cases (écran de distributeur) : le joueur peut y prendre
 * et y déposer des objets. Tout changement est recopié aussitôt dans le sac de la ville ; le villageois fait une pause
 * de quelques secondes pour ne pas se marcher dessus.
 */
public final class BagMenu {
	private BagMenu() {
	}

	/** Conteneur qui recopie chaque changement dans le sac. */
	private static final class BagContainer extends SimpleContainer {
		private final Inventory bag;
		private final String memberId;
		private boolean loading = true;

		BagContainer(Inventory bag, String memberId) {
			super(Inventory.SLOTS);
			this.bag = bag;
			this.memberId = memberId;
			for (int i = 0; i < Inventory.SLOTS; i++) {
				String id = bag.itemAt(i);
				if (id == null) continue;
				ItemStack st = ItemUtil.stackOf(id, bag.countAt(i));
				if (!st.isEmpty()) setItem(i, st);
			}
			loading = false;
		}

		@Override
		public void setChanged() {
			super.setChanged();
			if (loading) return;
			for (int i = 0; i < Inventory.SLOTS; i++) {
				ItemStack st = getItem(i);
				if (st.isEmpty()) bag.setSlot(i, null, 0);
				else bag.setSlot(i, ItemUtil.idOf(st), st.getCount());
			}
			Director.pauseMember(memberId);
			Towns.markDirty();
		}
	}

	public static void open(Player player, Town town, String memberId) {
		Role role = town.members.get(memberId);
		BagContainer container = new BagContainer(town.inventoryOf(memberId), memberId);
		Director.pauseMember(memberId);
		String title = (role == null ? "Habitant" : role.displayName) + " — sac";
		player.openMenu(new SimpleMenuProvider((containerId, playerInventory, p) -> new DispenserMenu(containerId, playerInventory, container),
				Component.literal(title)));
	}
}
