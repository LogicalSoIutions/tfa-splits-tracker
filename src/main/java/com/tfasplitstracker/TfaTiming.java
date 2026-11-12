package com.tfasplitstracker;

import java.util.ArrayList;
import java.util.List;

/** Timing presentation shared by chat, overlays and infoboxes. */
final class TfaTiming
{
	private TfaTiming() { }

	static String label(String room)
	{
		TfaRoom known = TfaRoom.find(room);
		return known == null ? room : known.label;
	}

	static List<String> messages(TfaRaid before, TfaRaid after, TfaSplitsTrackerConfig config)
	{
		List<String> messages = new ArrayList<>();
		if (after == null) { return messages; }
		boolean sameRaid = before != null && before.id.equals(after.id);
		if (config.chatSplits())
		{
			for (TfaRaid.Split split : after.splits)
			{
				boolean alreadyCompleted = sameRaid && before.splits.stream()
					.anyMatch(old -> old.room.equals(split.room) && old.duration >= 0);
				if (split.duration >= 0 && !alreadyCompleted)
				{
					messages.add("TFA: " + label(split.room) + " - " + TfaRaid.time(split.duration)
						+ " (raid split " + TfaRaid.time(split.cumulative) + ")");
				}
			}
		}
		if (config.chatTotal() && "Completed".equals(after.status)
			&& (!sameRaid || !"Completed".equals(before.status)))
		{
			messages.add("TFA: Raid complete - " + TfaRaid.time(after.total));
		}
		return messages;
	}
}
