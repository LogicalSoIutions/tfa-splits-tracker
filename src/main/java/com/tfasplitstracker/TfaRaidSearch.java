package com.tfasplitstracker;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared total-duration search for history and the comparison picker. */
final class TfaRaidSearch
{
	static final String TIME_HELP = "Total raid time: 12:34, <13:00, >=12:00 or 12:00-13:00 (m:ss or m:ss.xx)";
	private static final String TIME = "(\\d+:[0-5]\\d(?:\\.\\d{1,2})?)";
	private static final Pattern LIMIT = Pattern.compile("(<=|>=|<|>)\\s*" + TIME);
	private static final Pattern RANGE = Pattern.compile(TIME + "\\s*-\\s*" + TIME);
	private static final Pattern PREFIX = Pattern.compile("\\d+:[0-5]?\\d?(?:\\.\\d{0,2})?");

	private TfaRaidSearch()
	{
	}

	static boolean matches(TfaRaid raid, String query, String searchableText)
	{
		query = query.trim().toLowerCase(Locale.ROOT);
		Matcher limit = LIMIT.matcher(query);
		Matcher range = RANGE.matcher(query);
		try
		{
			if (limit.matches())
			{
				long target = millis(limit.group(2));
				if (raid.total < 0) { return false; }
				switch (limit.group(1))
				{
					case "<": return raid.total < target;
					case "<=": return raid.total <= target;
					case ">": return raid.total > target;
					default: return raid.total >= target;
				}
			}
			if (range.matches())
			{
				long minimum = millis(range.group(1));
				long maximum = millis(range.group(2));
				return raid.total >= 0 && raid.total >= minimum && raid.total <= maximum;
			}
		}
		catch (ArithmeticException | NumberFormatException ex)
		{
			return false;
		}
		if (PREFIX.matcher(query).matches())
		{
			return raid.total >= 0 && TfaRaid.time(raid.total).startsWith(query);
		}
		return searchableText.toLowerCase(Locale.ROOT).contains(query);
	}

	private static long millis(String time)
	{
		int colon = time.indexOf(':');
		int dot = time.indexOf('.');
		long minutes = Math.multiplyExact(Long.parseLong(time.substring(0, colon)), 60000);
		long seconds = Long.parseLong(time.substring(colon + 1, dot < 0 ? time.length() : dot)) * 1000;
		long fraction = dot < 0 ? 0 : Long.parseLong((time.substring(dot + 1) + "00").substring(0, 2)) * 10;
		return Math.addExact(minutes, seconds + fraction);
	}
}
