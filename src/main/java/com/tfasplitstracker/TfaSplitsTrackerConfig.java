package com.tfasplitstracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(TfaSplitsTrackerConfig.GROUP)
public interface TfaSplitsTrackerConfig extends Config
{
	String GROUP = "tfasplitstracker";

	@ConfigSection(name = "Chat messages", description = "Timing messages in your local chatbox", position = 0)
	String CHAT = "chat";
	@ConfigSection(name = "Splits overlay", description = "Movable raid timing panel", position = 1)
	String OVERLAY = "overlay";
	@ConfigSection(name = "Infoboxes", description = "Completed room timing infoboxes", position = 2)
	String INFOBOX = "infobox";

	@ConfigItem(keyName = "chatSplits", name = "Splits in chat", description = "Print each completed room's duration and cumulative split locally", section = CHAT, position = 0)
	default boolean chatSplits() { return true; }
	@ConfigItem(keyName = "chatTotal", name = "Raid time in chat", description = "Print the total raid time on completion", section = CHAT, position = 1)
	default boolean chatTotal() { return true; }

	@ConfigItem(keyName = "showOverlay", name = "Splits on overlay", description = "Show a movable timing panel during raids", section = OVERLAY, position = 0)
	default boolean showOverlay() { return true; }
	@ConfigItem(keyName = "overlayLiveTimers", name = "Live raid clock", description = "Show the game's raid clock and the current room", section = OVERLAY, position = 1)
	default boolean overlayLiveTimers() { return true; }
	@ConfigItem(keyName = "overlayCumulative", name = "Cumulative splits", description = "Show cumulative raid splits alongside room durations", section = OVERLAY, position = 2)
	default boolean overlayCumulative() { return true; }
	@ConfigItem(keyName = "keepOverlayAfterRaid", name = "Keep after raid", description = "Keep the latest raid's timings visible after leaving the raid while logged in", section = OVERLAY, position = 3)
	default boolean keepOverlayAfterRaid() { return false; }

	@ConfigItem(keyName = "showInfoBoxes", name = "Infobox info", description = "Show one timing infobox per completed room in the latest raid", section = INFOBOX, position = 0)
	default boolean showInfoBoxes() { return true; }
	@ConfigItem(keyName = "infoBoxText", name = "Infobox text", description = "Choose which time appears on each room infobox (whole seconds)", section = INFOBOX, position = 1)
	default InfoBoxText infoBoxText() { return InfoBoxText.ROOM_TIME; }
	@ConfigItem(keyName = "infoBoxTooltip", name = "Infobox tooltips", description = "Show the room name and precise room time on hover", section = INFOBOX, position = 2)
	default boolean infoBoxTooltip() { return true; }
	@ConfigItem(keyName = "infoBoxCumulative", name = "Cumulative split in tooltip", description = "Include the cumulative raid split in each infobox tooltip", section = INFOBOX, position = 3)
	default boolean infoBoxCumulative() { return true; }

	enum InfoBoxText
	{
		ROOM_TIME("Room time"), CUMULATIVE("Cumulative split"), NONE("None");
		private final String label;
		InfoBoxText(String label) { this.label = label; }
		@Override public String toString() { return label; }
	}
}
