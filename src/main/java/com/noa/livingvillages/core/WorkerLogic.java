package com.noa.livingvillages.core;

/** Cerveau des ouvriers de récolte : récolter dans son sac, puis aller déposer au dépôt quand c'est plein ou quand la commande est prête. */
public final class WorkerLogic {
	public enum Kind {HARVEST, DEPOSIT, IDLE}

	public record Action(Kind kind, TownBoard.Request request) {
	}

	private WorkerLogic() {
	}

	/** Quantité à porter avant d'aller déposer (une pile, ou moins si la commande est plus petite). */
	private static int tripTarget(TownBoard.Request r) {
		return Math.min(r.qty, Inventory.maxStack(r.item));
	}

	public static Action next(Town town, String workerId, Role role, Inventory inv) {
		TownBoard.Request mine = town.board.claimedBy(workerId);
		if (mine != null) {
			if (inv.count(mine.item) >= tripTarget(mine) || !inv.canAdd(mine.item, 1)) return new Action(Kind.DEPOSIT, mine);
			return new Action(Kind.HARVEST, mine);
		}
		// plus de commande en cours : on dépose ce qu'on porte avant d'en prendre une nouvelle
		if (!inv.isEmpty()) return new Action(Kind.DEPOSIT, null);
		TownBoard.Request r = town.board.claim(workerId, role);
		return r == null ? new Action(Kind.IDLE, null) : new Action(Kind.HARVEST, r);
	}
}
