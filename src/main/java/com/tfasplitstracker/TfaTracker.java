package com.tfasplitstracker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Event reducer shared by live chat, chat-buffer recovery, and recorded-log tests. */
final class TfaTracker
{
	static final Set<String> ROOMS = new LinkedHashSet<>(Arrays.asList("Archive Guardian", "Bork",
		"Dagon'hai Trio", "Ezekiel", "Dreadnoth the Jellied", "Moia", "Xarth", "Ocellus"));
	private static final String TIME = "(\\d+:\\d{2}(?::\\d{2})?(?:\\.\\d{1,3})?)";
	private static final Pattern START = Pattern.compile("^Challenge started: (.+)\\.$");
	private static final Pattern SPLIT = Pattern.compile("^Challenge complete: (.+)\\. Duration: " + TIME + "\\. Total: " + TIME + ".*$");
	private static final Pattern TOTAL = Pattern.compile("^The Fractured Archive: Total completion time: " + TIME + ".*$");
	private static final Pattern READY = Pattern.compile("^\\d+/([1-8]) party members are now ready\\.$");
	final List<TfaRaid> raids = new ArrayList<>();
	// Persist the event journal as well as the records, so re-enabling cannot replay old starts.
	final Set<String> processed = new LinkedHashSet<>();
	long lastEvent;
	TfaRaid deleted;
	int deletedIndex;
	boolean suppressUntilNextRaid;

	boolean delete(String raidId, String room)
	{
		for (int i = 0; i < raids.size(); i++)
		{
			TfaRaid raid = raids.get(i);
			if (!raid.id.equals(raidId) || room != null && raid.active())
			{
				continue;
			}
			if (room != null && raid.splits.stream().noneMatch(s -> s.room.equals(room)))
			{
				return false;
			}
			if (raid.active())
			{
				suppressUntilNextRaid = true;
			}
			else
			{
				deleted = raid.copy();
				deletedIndex = i;
			}
			if (room == null)
			{
				raids.remove(i);
			}
			else
			{
				raid.splits.removeIf(s -> s.room.equals(room));
			}
			// Keep the event journal: retained chat must not resurrect a deleted record.
			return true;
		}
		return false;
	}

	boolean undoDelete()
	{
		if (deleted == null)
		{
			return false;
		}
		raids.removeIf(r -> r.id.equals(deleted.id));
		// A newly started raid must remain first so current() can find it.
		int firstSaved = current() == null ? 0 : 1;
		raids.add(Math.min(raids.size(), Math.max(firstSaved, deletedIndex)), deleted);
		deleted = null;
		while (raids.size() > 200)
		{
			raids.remove(raids.size() - 1);
		}
		return true;
	}

	TfaRaid current()
	{
		return !raids.isEmpty() && raids.get(0).active() ? raids.get(0) : null;
	}

	boolean accept(String raw, long timestamp)
	{
		if (raw == null)
		{
			return false;
		}
		String message = raw.replaceAll("<[^>]*>", "").replace('\u00a0', ' ').trim();
		Matcher start = START.matcher(message);
		Matcher split = SPLIT.matcher(message);
		Matcher total = TOTAL.matcher(message);
		Matcher ready = READY.matcher(message);
		boolean isStart = start.matches() && ROOMS.contains(start.group(1));
		boolean isSplit = split.matches() && ROOMS.contains(split.group(1));
		boolean isTotal = total.matches();
		boolean isReady = ready.matches() && current() != null;
		boolean failed = message.equals("You failed to survive the Fractured Archive.");
		boolean abandoned = message.equals("You abandon the raid and leave the Fractured Archive.");
		if (!isStart && !isSplit && !isTotal && !isReady && !failed && !abandoned)
		{
			return false;
		}
		String key = timestamp + ":" + message;
		if (processed.contains(key) || timestamp < lastEvent)
		{
			return false;
		}
		processed.add(key);
		while (processed.size() > 4096)
		{
			processed.remove(processed.iterator().next());
		}
		lastEvent = timestamp;
		if (suppressUntilNextRaid)
		{
			if (isStart && start.group(1).equals("Archive Guardian"))
			{
				suppressUntilNextRaid = false;
			}
			else
			{
				return true;
			}
		}
		if (isStart)
		{
			String room = start.group(1);
			TfaRaid raid = current();
			TfaRaid.Split previous = raid == null || raid.splits.isEmpty() ? null : raid.splits.get(raid.splits.size() - 1);
			if (previous != null && room.equals(previous.room) && previous.duration < 0 && previous.started == timestamp)
			{
				return true;
			}
			if (raid != null && room.equals("Archive Guardian"))
			{
				raid.status = "Interrupted";
				raid = null;
			}
			if (raid == null)
			{
				raid = create(timestamp);
			}
			TfaRaid.Split next = new TfaRaid.Split();
			next.room = room;
			next.started = timestamp;
			raid.splits.add(next);
			raid.updated = timestamp;
		}
		else if (isSplit)
		{
			String room = split.group(1);
			long duration = parseTime(split.group(2));
			long cumulative = parseTime(split.group(3));
			TfaRaid raid = current();
			if (raid != null && room.equals("Archive Guardian")
				&& raid.splits.stream().anyMatch(s -> s.room.equals(room) && s.duration >= 0))
			{
				// The next run's first completion also recovers a missed first-room start.
				raid.status = "Interrupted";
				raid = null;
			}
			if (raid == null)
			{
				// Ocellus and the overall message are separate signals for the same finish.
				TfaRaid last = raids.isEmpty() ? null : raids.get(0);
				if (last != null && "Completed".equals(last.status) && Math.abs(timestamp - last.updated) <= 2000)
				{
					raid = last;
				}
				else
				{
					raid = create(timestamp - cumulative);
				}
			}
			TfaRaid.Split result = raid.splits.stream().filter(s -> s.room.equals(room)).findFirst().orElse(null);
			if (result == null)
			{
				result = new TfaRaid.Split();
				result.room = room;
				result.started = timestamp - duration;
				raid.splits.add(result);
			}
			result.duration = duration;
			result.cumulative = cumulative;
			raid.total = cumulative;
			raid.updated = timestamp;
			if (room.equals("Ocellus"))
			{
				raid.status = "Completed";
			}
		}
		else if (isTotal)
		{
			TfaRaid raid = current();
			if (raid == null && !raids.isEmpty() && "Completed".equals(raids.get(0).status)
				&& Math.abs(timestamp - raids.get(0).updated) <= 2000)
			{
				raid = raids.get(0);
			}
			long duration = parseTime(total.group(1));
			if (raid == null)
			{
				raid = create(timestamp - duration);
			}
			raid.total = duration;
			raid.updated = timestamp;
			raid.status = "Completed";
		}
		else if (current() != null)
		{
			TfaRaid raid = current();
			if (isReady)
			{
				raid.teamSize = Integer.parseInt(ready.group(1));
			}
			else
			{
				raid.status = failed ? "Failed" : "Abandoned";
				raid.updated = timestamp;
			}
		}
		return true;
	}

	private TfaRaid create(long timestamp)
	{
		TfaRaid raid = new TfaRaid();
		raid.started = timestamp;
		raid.updated = timestamp;
		raids.add(0, raid);
		while (raids.size() > 200)
		{
			raids.remove(raids.size() - 1);
		}
		return raid;
	}

	boolean party(List<String> names)
	{
		TfaRaid raid = current();
		if (raid == null || names.isEmpty())
		{
			return false;
		}
		boolean changed = false;
		for (String raw : names)
		{
			String name = raw == null ? "" : raw.replaceAll("<[^>]*>", "").replace('\u00a0', ' ').trim();
			if (!name.isEmpty() && raid.members.stream().noneMatch(n -> n.equalsIgnoreCase(name)))
			{
				raid.members.add(name);
				changed = true;
			}
		}
		return changed;
	}

	static long parseTime(String value)
	{
		String[] parts = value.split(":");
		long seconds = 0;
		for (int i = 0; i < parts.length - 1; i++)
		{
			seconds = (seconds + Long.parseLong(parts[i])) * 60;
		}
		return seconds * 1000 + new java.math.BigDecimal(parts[parts.length - 1]).movePointRight(3).longValueExact();
	}
}
