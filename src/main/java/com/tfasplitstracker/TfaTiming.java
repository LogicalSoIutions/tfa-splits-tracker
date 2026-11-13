package com.tfasplitstracker;

import java.util.ArrayList;
import java.util.List;

/** Room server times and observed phase times for the local chatbox. */
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
		if (config.chatOcellusPhases())
		{
			for (TfaRaid.Split split : after.splits)
			{
				for (TfaRaid.Phase phase : split.phases)
				{
					boolean recorded = sameRaid && before.splits.stream().filter(s -> s.room.equals(split.room))
						.flatMap(s -> s.phases.stream()).anyMatch(p -> p.number == phase.number && p.duration >= 0);
					if (phase.duration < 0 || recorded) { continue; }
					TfaRaid.Phase reference = config.chatPersonalBest() && after.personalBest != null
						? after.personalBest.phase(split, phase.number) : null;
					messages.add("TFA: Ocellus " + TfaOcellus.label(phase.number) + " - " + TfaRaid.time(phase.duration)
						+ (reference == null ? "" : " | vs scale " + after.personalBest.scale + " PB: "
							+ TfaPersonalBest.delta(phase.duration - reference.duration)));
				}
			}
		}
		if (config.chatSplits())
		{
			for (TfaRaid.Split split : after.splits)
			{
				boolean alreadyCompleted = sameRaid && before.splits.stream()
					.anyMatch(old -> old.room.equals(split.room) && old.duration >= 0);
				if (split.duration >= 0 && !alreadyCompleted)
				{
					String comparison = config.chatPersonalBest() && after.personalBest != null
						? after.personalBest.comparison(split) : "";
					messages.add("TFA: " + label(split.room) + " - " + TfaRaid.time(split.duration)
						+ " (raid split " + TfaRaid.time(split.cumulative) + ")"
						+ (comparison.isEmpty() ? "" : " | vs scale " + after.personalBest.scale + " PB: " + comparison));
				}
			}
		}
		if (config.chatTotal() && "Completed".equals(after.status)
			&& (!sameRaid || !"Completed".equals(before.status)))
		{
			messages.add("TFA: Raid complete - " + TfaRaid.time(after.total)
				+ (config.chatPersonalBest() && after.personalBest != null
					? " | vs scale " + after.personalBest.scale + " PB: " + TfaPersonalBest.delta(after.total - after.personalBest.total) : ""));
		}
		return messages;
	}
}
