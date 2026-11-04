package com.tfasplitstracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Persisted server timings. A negative duration means that no completion was received. */
final class TfaRaid
{
	String id = UUID.randomUUID().toString();
	long started;
	long updated;
	long total = -1;
	String status = "In progress";
	int teamSize;
	List<String> members = new ArrayList<>();
	List<Split> splits = new ArrayList<>();

	TfaRaid copy()
	{
		TfaRaid copy = new TfaRaid();
		copy.id = id;
		copy.started = started;
		copy.updated = updated;
		copy.total = total;
		copy.status = status;
		copy.teamSize = teamSize;
		copy.members.addAll(members);
		for (Split split : splits)
		{
			Split cloned = new Split();
			cloned.room = split.room;
			cloned.started = split.started;
			cloned.duration = split.duration;
			cloned.cumulative = split.cumulative;
			copy.splits.add(cloned);
		}
		return copy;
	}

	static final class Split
	{
		String room;
		long started;
		long duration = -1;
		long cumulative = -1;
	}

	boolean active()
	{
		return "In progress".equals(status);
	}

	long combatTime()
	{
		return splits.stream().filter(s -> s.duration >= 0).mapToLong(s -> s.duration).sum();
	}

	boolean completeSplits()
	{
		return splits.size() == 5 && splits.stream().allMatch(s -> s.duration >= 0)
			&& "Archive Guardian".equals(splits.get(0).room)
			&& "Ocellus".equals(splits.get(4).room);
	}

	static String time(long millis)
	{
		if (millis < 0)
		{
			return "--:--";
		}
		return String.format(Locale.ROOT, "%d:%02d.%02d", millis / 60000, millis / 1000 % 60, millis / 10 % 100);
	}
}
