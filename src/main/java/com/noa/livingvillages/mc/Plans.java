package com.noa.livingvillages.mc;

import com.noa.livingvillages.core.PlanCatalog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Charge les plans (.plan) depuis les ressources du mod. */
public final class Plans {
	private static PlanCatalog catalog;

	private Plans() {
	}

	private static String read(String path) throws IOException {
		try (InputStream in = Plans.class.getResourceAsStream(path)) {
			if (in == null) throw new IOException("Ressource introuvable : " + path);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	public static synchronized PlanCatalog catalog() {
		if (catalog == null) {
			try {
				List<String> texts = new ArrayList<>();
				for (String name : read("/data/livingvillages/plans/index.txt").split("\\R")) {
					if (!name.isBlank()) texts.add(read("/data/livingvillages/plans/" + name.strip() + ".plan"));
				}
				catalog = PlanCatalog.fromTexts(texts);
			} catch (IOException e) {
				throw new IllegalStateException("Impossible de charger les plans", e);
			}
		}
		return catalog;
	}
}
