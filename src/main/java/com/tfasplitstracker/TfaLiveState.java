package com.tfasplitstracker;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A client-thread snapshot of the game's HUD, never a wall-clock extrapolation. */
final class TfaLiveState
{
	private static final Pattern TIME = Pattern.compile("(?<![\\d:])(\\d+:\\d{2}(?::\\d{2})?(?:\\.\\d{1,3})?)(?![\\d:])");
	static final TfaLiveState UNAVAILABLE = new TfaLiveState(false, "", -1);
	final boolean inRaid;
	final String status;
	final long elapsed;

	TfaLiveState(boolean inRaid, String status, long elapsed)
	{
		this.inRaid = inRaid;
		this.status = status;
		this.elapsed = elapsed;
	}

	static String clean(String text)
	{
		return text == null ? "" : text.replaceAll("<[^>]*>", "").replace('\u00a0', ' ').trim();
	}

	static long parseClock(String text)
	{
		Matcher matcher = TIME.matcher(clean(text));
		return matcher.find() ? TfaTracker.parseTime(matcher.group(1)) : -1;
	}
}
