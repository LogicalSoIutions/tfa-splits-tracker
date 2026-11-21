package com.tfasplitstracker;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(TfaSplitsTrackerConfig.GROUP)
public interface TfaSplitsTrackerConfig extends Config
{
	String GROUP = "tfasplitstracker";

	@ConfigSection(name = "Chat messages", description = "Choose which timings appear in your local chatbox", position = 0)
	String CHAT = "chat";
	@ConfigSection(name = "Room infoboxes", description = "Small completed-room timers beside the game view", position = 1)
	String INFOBOX = "infobox";
	@ConfigSection(name = "Timing overlay", description = "Optional room and raid timers, without PB comparisons", position = 2)
	String OVERLAY = "overlay";

	@ConfigItem(keyName = "chatSplits", name = "Show room times", description = "Print each completed room's duration and cumulative split locally", section = CHAT, position = 0)
	default boolean chatSplits() { return true; }
	@ConfigItem(keyName = "chatTotal", name = "Show raid total", description = "Print the total raid time on completion", section = CHAT, position = 1)
	default boolean chatTotal() { return true; }

	@ConfigItem(keyName = "chatPersonalBest", name = "Include PB comparison", description = "Append PB differences at the same party scale. Sidebar comparisons are always on.", section = CHAT, position = 2)
	default boolean chatPersonalBest() { return false; }
	@ConfigItem(keyName = "chatOcellusPhases", name = "Show Ocellus phases", description = "Print each completed Ocellus phase in local chat", section = CHAT, position = 3)
	default boolean chatOcellusPhases() { return false; }

	@ConfigItem(keyName = "showOverlay", name = "Show overlay", description = "Show room times and the raid clock. No PB comparisons.", section = OVERLAY, position = 0)
	default boolean showOverlay() { return false; }
	@ConfigItem(keyName = "overlayCumulative", name = "Include raid splits", description = "Show cumulative raid time beside each completed room", section = OVERLAY, position = 1)
	default boolean overlayCumulative() { return false; }
	@ConfigItem(keyName = "overlayOcellusPhases", name = "Show Ocellus phases", description = "Include the four Ocellus phase times in the overlay", section = OVERLAY, position = 2)
	default boolean overlayOcellusPhases() { return true; }
	@ConfigItem(keyName = "overlayFont", name = "Font", description = "Choose the overlay typeface", section = OVERLAY, position = 3)
	default OverlayFont overlayFont() { return OverlayFont.RUNESCAPE_BOLD; }
	@Range(min = 8, max = 36)
	@ConfigItem(keyName = "overlayFontSize", name = "Font size", description = "Overlay font size in pixels (8–36)", section = OVERLAY, position = 4)
	default int overlayFontSize() { return 12; }
	@ConfigItem(keyName = "overlayTextColor", name = "Text color", description = "Color of room labels, split times, and Ocellus phases", section = OVERLAY, position = 5)
	default Color overlayTextColor() { return Color.WHITE; }
	@ConfigItem(keyName = "overlayClockColor", name = "Raid clock color", description = "Color of the raid time or completed raid total", section = OVERLAY, position = 6)
	default Color overlayClockColor() { return Color.GREEN; }

	@ConfigItem(keyName = "showInfoBoxes", name = "Show infoboxes", description = "Show one timing infobox per completed room in the latest raid", section = INFOBOX, position = 0)
	default boolean showInfoBoxes() { return true; }
	@ConfigItem(keyName = "infoBoxText", name = "Timer text", description = "Choose which time appears on each room infobox (whole seconds)", section = INFOBOX, position = 1)
	default InfoBoxText infoBoxText() { return InfoBoxText.ROOM_TIME; }
	@ConfigItem(keyName = "infoBoxTooltip", name = "Show tooltips", description = "Show precise room times and recorded PB comparisons on hover", section = INFOBOX, position = 2)
	default boolean infoBoxTooltip() { return true; }
	@ConfigItem(keyName = "infoBoxCumulative", name = "Include raid split", description = "Include the cumulative raid split in each infobox tooltip", section = INFOBOX, position = 3)
	default boolean infoBoxCumulative() { return true; }

	enum OverlayFont
	{
		RUNESCAPE("RuneScape"), RUNESCAPE_BOLD("RuneScape Bold"),
		ARIAL("Arial"), SANS_SERIF("Sans serif"), SERIF("Serif"), MONOSPACED("Monospaced");
		private final String label;
		OverlayFont(String label) { this.label = label; }
		@Override public String toString() { return label; }
	}

	enum InfoBoxText
	{
		ROOM_TIME("Room time"), CUMULATIVE("Cumulative split"), NONE("None");
		private final String label;
		InfoBoxText(String label) { this.label = label; }
		@Override public String toString() { return label; }
	}
}
