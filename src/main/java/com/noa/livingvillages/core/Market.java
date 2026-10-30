package com.noa.livingvillages.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Marché interne : les villageois s'échangent des surplus contre des « jetons » (le joueur n'y a pas accès). */
public final class Market {
	public static final class Listing {
		public final String seller;
		public final String item;
		public int qty;
		public final int unitPrice;

		Listing(String seller, String item, int qty, int unitPrice) {
			this.seller = seller;
			this.item = item;
			this.qty = qty;
			this.unitPrice = unitPrice;
		}
	}

	public record Trade(String seller, String buyer, String item, int qty, int totalPrice) {
	}

	private final List<Listing> listings = new ArrayList<>();
	private final Map<String, Integer> wallets = new HashMap<>();

	public int balance(String who) {
		return wallets.getOrDefault(who, 0);
	}

	public void credit(String who, int n) {
		wallets.merge(who, n, Integer::sum);
	}

	public void post(String seller, String item, int qty, int unitPrice) {
		if (qty > 0) listings.add(new Listing(seller, item, qty, unitPrice));
	}

	public List<Listing> listings() {
		return List.copyOf(listings);
	}

	/** Achète jusqu'à 'qty' objets, aux vendeurs les moins chers, dans la limite des jetons de l'acheteur. */
	public List<Trade> buy(String buyer, String item, int qty) {
		List<Listing> cands = new ArrayList<>();
		for (Listing l : listings) if (l.item.equals(item) && !l.seller.equals(buyer) && l.qty > 0) cands.add(l);
		cands.sort((a, b) -> Integer.compare(a.unitPrice, b.unitPrice));
		List<Trade> trades = new ArrayList<>();
		int left = qty;
		for (Listing l : cands) {
			if (left <= 0) break;
			int affordable = l.unitPrice == 0 ? left : balance(buyer) / l.unitPrice;
			int n = Math.min(Math.min(left, l.qty), affordable);
			if (n <= 0) continue;
			int price = n * l.unitPrice;
			wallets.merge(buyer, -price, Integer::sum);
			wallets.merge(l.seller, price, Integer::sum);
			l.qty -= n;
			left -= n;
			trades.add(new Trade(l.seller, buyer, item, n, price));
		}
		listings.removeIf(l -> l.qty <= 0);
		return trades;
	}
}
