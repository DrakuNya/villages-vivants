package com.noa.livingvillages.mc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Stockage dans le monde : un simple texte (le format est géré par TownStore, voir core). */
public class TownSavedData extends SavedData {
	private static final Codec<TownSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("data").forGetter(TownSavedData::getText)
	).apply(instance, TownSavedData::new));

	private static final SavedDataType<TownSavedData> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath(ModItems.MOD_ID, "towns"), TownSavedData::new, CODEC, null);

	private String text = "";

	public TownSavedData() {
	}

	private TownSavedData(String text) {
		this.text = text;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		if (!text.equals(this.text)) {
			this.text = text;
			setDirty();
		}
	}

	public static TownSavedData get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}
}
