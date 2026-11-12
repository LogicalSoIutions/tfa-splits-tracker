package com.tfasplitstracker;

import java.util.Arrays;

/** Boss identity and its position in the five-stage raid. */
enum TfaRoom
{
	GUARDIAN(1, "Archive Guardian", "Archive Guardian"),
	EZEKIEL(2, "Ezekiel", "Ezekiel"),
	BORK(2, "Bork", "Bork & the Smorks", "Bork and the Smorks"),
	MOIA(3, "Moia", "Moia"),
	DREADNOTH(3, "Dreadnoth the Jellied", "Dreadnoth the Jellied"),
	DAGONHAI(4, "Dagon'hai Trio", "Dagon'hai Elder & monks", "Dagon'hai Elder and monks", "Dagon'hai Elder"),
	XARTH(4, "Xarth", "Xarth"),
	OCELLUS(5, "Ocellus", "Ocellus");

	final int stage;
	final String key;
	final String label;
	private final String[] aliases;

	TfaRoom(int stage, String key, String label, String... aliases)
	{
		this.stage = stage;
		this.key = key;
		this.label = label;
		this.aliases = aliases;
	}

	static TfaRoom find(String name)
	{
		if (name == null)
		{
			return null;
		}
		String normalized = name.replace('\u2019', '\'').trim();
		return Arrays.stream(values()).filter(room -> room.key.equalsIgnoreCase(normalized)
			|| room.label.equalsIgnoreCase(normalized)
			|| Arrays.stream(room.aliases).anyMatch(alias -> alias.equalsIgnoreCase(normalized))).findFirst().orElse(null);
	}
}
