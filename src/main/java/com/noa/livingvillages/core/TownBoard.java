package com.noa.livingvillages.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Le « tableau des besoins » d'une ville : les chantiers y affichent ce qu'il leur manque, les ouvriers viennent le prendre. */
public final class TownBoard {
	public enum Status {OPEN, CLAIMED}

	public static final class Request {
		public final int id;
		public final String siteId;
		public final String item;
		public int qty;
		public Status status = Status.OPEN;
		public String claimedBy;

		Request(int id, String siteId, String item, int qty) {
			this.id = id;
			this.siteId = siteId;
			this.item = item;
			this.qty = qty;
		}
	}

	private final List<Request> requests = new ArrayList<>();
	private int nextId = 1;

	public List<Request> all() {
		return List.copyOf(requests);
	}

	/** Aligne les demandes d'un chantier sur la liste des matières premières manquantes. */
	public void sync(String siteId, Map<String, Integer> missingRaw) {
		Iterator<Request> it = requests.iterator();
		while (it.hasNext()) {
			Request r = it.next();
			if (!r.siteId.equals(siteId)) continue;
			Integer need = missingRaw.get(r.item);
			if (need == null || need <= 0) it.remove();
			else r.qty = need;
		}
		for (Map.Entry<String, Integer> e : missingRaw.entrySet()) {
			if (e.getValue() <= 0) continue;
			boolean exists = false;
			for (Request r : requests) if (r.siteId.equals(siteId) && r.item.equals(e.getKey())) exists = true;
			if (!exists) requests.add(new Request(nextId++, siteId, e.getKey(), e.getValue()));
		}
	}

	public void removeSite(String siteId) {
		requests.removeIf(r -> r.siteId.equals(siteId));
	}

	/** Prend la première demande ouverte que ce métier sait satisfaire. */
	public Request claim(String workerId, Role role) {
		for (Request r : requests) {
			if (r.status == Status.OPEN && role.canProduce(r.item)) {
				r.status = Status.CLAIMED;
				r.claimedBy = workerId;
				return r;
			}
		}
		return null;
	}

	public void release(String workerId) {
		for (Request r : requests) {
			if (workerId.equals(r.claimedBy)) {
				r.status = Status.OPEN;
				r.claimedBy = null;
			}
		}
	}

	/** Livraison de 'n' objets : la demande diminue (et disparaît à zéro). */
	public void delivered(Request r, int n) {
		r.qty -= n;
		if (r.qty <= 0) requests.remove(r);
		else {
			r.status = Status.OPEN;
			r.claimedBy = null;
		}
	}
}
