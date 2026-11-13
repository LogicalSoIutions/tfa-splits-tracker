package com.tfasplitstracker;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import net.runelite.client.ui.overlay.infobox.InfoBox;

final class TfaSplitInfoBox extends InfoBox
{
	private final TfaSplitsTrackerPlugin plugin;
	private final TfaSplitsTrackerConfig config;
	private final TfaRaid.Split split;

	TfaSplitInfoBox(TfaSplitsTrackerPlugin plugin, TfaSplitsTrackerConfig config, TfaRaid.Split split)
	{
		super(image(split.room), plugin);
		this.plugin = plugin;
		this.config = config;
		this.split = split;
	}

	@Override public String getText()
	{
		if (config.infoBoxText() == TfaSplitsTrackerConfig.InfoBoxText.NONE) { return ""; }
		long time = config.infoBoxText() == TfaSplitsTrackerConfig.InfoBoxText.CUMULATIVE ? split.cumulative : split.duration;
		String precise = TfaRaid.time(time);
		return time < 0 ? precise : precise.substring(0, precise.indexOf('.'));
	}

	@Override public Color getTextColor() { return Color.GREEN; }
	@Override public boolean render() { return config.showInfoBoxes() && plugin.timingVisible(); }
	@Override public String getTooltip()
	{
		TfaRaid raid = plugin.displayRaid();
		String comparison = raid != null && raid.personalBest != null
			? raid.personalBest.comparison(split) : "";
		return !config.infoBoxTooltip() ? "" : "TFA: " + TfaTiming.label(split.room)
			+ "<br>Room time: " + TfaRaid.time(split.duration)
			+ (config.infoBoxCumulative() ? "<br>Raid split: " + TfaRaid.time(split.cumulative) : "")
			+ (comparison.isEmpty() ? "" : "<br>vs scale " + raid.personalBest.scale + " PB: " + comparison);
	}

	private static BufferedImage image(String room)
	{
		BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.drawImage(TfaSplitsTrackerPlugin.icon(), 4, 0, null);
		graphics.setColor(Color.WHITE);
		graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
		TfaRoom known = TfaRoom.find(room);
		graphics.drawString(known == null ? "?" : Integer.toString(known.stage), 24, 10);
		graphics.dispose();
		return image;
	}
}
