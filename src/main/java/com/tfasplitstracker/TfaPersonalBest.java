package com.tfasplitstracker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Frozen server splits from one completed run, rather than a sum of best rooms. */
final class TfaPersonalBest
{
	int scale;
	long total;
	List<TfaRaid.Split> splits = new ArrayList<>();

	static void select(TfaRaid raid, List<TfaRaid> history)
	{
		int scale = raid.scale();
		if (scale <= 0 || raid.personalBestSelected && raid.personalBestScale == scale) { return; }
		raid.personalBestSelected = true;
		raid.personalBestScale = scale;
		raid.personalBest = null;
		history.stream().filter(r -> !r.id.equals(raid.id) && r.scale() == scale
			&& "Completed".equals(r.status) && valid(r) && r.updated <= raid.started)
			.min(Comparator.comparingLong(r -> r.total)).ifPresent(best ->
			{
				TfaPersonalBest reference = new TfaPersonalBest();
				reference.scale = scale;
				reference.total = best.total;
				reference.splits.addAll(best.copy().splits);
				raid.personalBest = reference;
			});
	}

	private static boolean valid(TfaRaid raid)
	{
		if (!raid.completeSplits() || raid.total < 0) { return false; }
		long previous = 0;
		for (int i = 0; i < raid.splits.size(); i++)
		{
			TfaRaid.Split split = raid.splits.get(i);
			TfaRoom room = TfaRoom.find(split.room);
			if (room == null || room.stage != i + 1 || split.cumulative < previous + split.duration) { return false; }
			previous = split.cumulative;
		}
		return raid.total >= previous;
	}

	TfaRaid.Split stage(TfaRaid.Split split)
	{
		TfaRoom room = TfaRoom.find(split.room);
		return room == null ? null : splits.stream().filter(s ->
			TfaRoom.find(s.room) != null && TfaRoom.find(s.room).stage == room.stage).findFirst().orElse(null);
	}

	String comparison(TfaRaid.Split split)
	{
		TfaRaid.Split reference = stage(split);
		if (reference == null || split.duration < 0 || split.cumulative < 0) { return ""; }
		String room = reference.room.equals(split.room) ? "room " + delta(split.duration - reference.duration) : "different boss";
		return room + ", raid " + delta(split.cumulative - reference.cumulative);
	}

	static String delta(long millis)
	{
		return (millis > 0 ? "+" : millis < 0 ? "-" : "") + TfaRaid.time(Math.abs(millis));
	}

	TfaRaid.Phase phase(TfaRaid.Split split, int number)
	{
		TfaRaid.Split reference = stage(split);
		return reference == null ? null : reference.phases.stream()
			.filter(p -> p.number == number && p.duration >= 0).findFirst().orElse(null);
	}

	static String status(TfaRaid raid)
	{
		if (raid.scale() <= 0) { return "PB: waiting for party"; }
		return raid.personalBest == null ? "No recorded PB for scale " + raid.scale()
			: "Scale " + raid.personalBest.scale + " PB: " + TfaRaid.time(raid.personalBest.total);
	}
}
