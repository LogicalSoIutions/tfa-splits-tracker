package com.tfasplitstracker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Compact, transparent timing overlay. PB comparisons belong in the sidebar and optional chat. */
public class TfaSplitsOverlay extends Overlay
{
	private final TfaSplitsTrackerPlugin plugin;
	private final TfaSplitsTrackerConfig config;
	private Font font;
	private TfaSplitsTrackerConfig.OverlayFont fontChoice;
	private int fontSize;

	@Inject
	TfaSplitsOverlay(TfaSplitsTrackerPlugin plugin, TfaSplitsTrackerConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		TfaRaid raid = plugin.displayRaid();
		if (!config.showOverlay() || raid == null || !plugin.timingVisible()) { return null; }
		List<Row> rows = rows(raid, plugin.liveState(), config);
		Graphics2D text = (Graphics2D) graphics.create();
		try
		{
			text.setFont(overlayFont());
			FontMetrics metrics = text.getFontMetrics();
			int labelWidth = 0;
			int valueWidth = 0;
			for (Row row : rows)
			{
				labelWidth = Math.max(labelWidth, metrics.stringWidth(row.label));
				valueWidth = Math.max(valueWidth, metrics.stringWidth(row.value));
			}
			int width = labelWidth + Math.max(8, fontSize) + valueWidth;
			int y = metrics.getAscent();
			for (Row row : rows)
			{
				drawText(text, row.label, 0, y, config.overlayTextColor());
				drawText(text, row.value, width - metrics.stringWidth(row.value), y, row.color);
				y += metrics.getHeight();
			}
			// Include the one-pixel text shadow in the overlay's drag bounds.
			return new Dimension(width + 1, rows.size() * metrics.getHeight() + 1);
		}
		finally
		{
			text.dispose();
		}
	}

	static List<Row> rows(TfaRaid raid, TfaLiveState live, TfaSplitsTrackerConfig config)
	{
		List<Row> rows = new ArrayList<>();
		long elapsed = raid.active() && live.elapsed >= 0 ? live.elapsed : raid.total;
		rows.add(new Row(raid.active() ? "Raid time:" : "Raid total:", TfaRaid.time(elapsed), config.overlayClockColor()));
		for (TfaRaid.Split split : raid.splits)
		{
			String value = TfaRaid.time(split.duration);
			if (config.overlayCumulative()) { value += " / " + TfaRaid.time(split.cumulative); }
			rows.add(new Row(TfaTiming.label(split.room) + ":", value, config.overlayTextColor()));
			if (config.overlayOcellusPhases())
			{
				for (TfaRaid.Phase phase : split.phases)
				{
					rows.add(new Row("  " + TfaOcellus.label(phase.number) + ":", TfaRaid.time(phase.duration), config.overlayTextColor()));
				}
			}
		}
		return rows;
	}

	private Font overlayFont()
	{
		TfaSplitsTrackerConfig.OverlayFont choice = config.overlayFont();
		int size = Math.max(8, Math.min(36, config.overlayFontSize()));
		if (font == null || choice != fontChoice || size != fontSize)
		{
			switch (choice)
			{
				case RUNESCAPE: font = FontManager.getRunescapeFont(); break;
				case RUNESCAPE_BOLD: font = FontManager.getRunescapeBoldFont(); break;
				case ARIAL: font = new Font("Arial", Font.BOLD, size); break;
				case SERIF: font = new Font(Font.SERIF, Font.BOLD, size); break;
				case MONOSPACED: font = new Font(Font.MONOSPACED, Font.BOLD, size); break;
				default: font = new Font(Font.SANS_SERIF, Font.BOLD, size); break;
			}
			font = font.deriveFont((float) size);
			fontChoice = choice;
			fontSize = size;
		}
		return font;
	}

	private static void drawText(Graphics2D graphics, String value, int x, int y, Color color)
	{
		graphics.setColor(Color.BLACK);
		graphics.drawString(value, x + 1, y + 1);
		graphics.setColor(color);
		graphics.drawString(value, x, y);
	}

	static final class Row
	{
		final String label;
		final String value;
		final Color color;

		Row(String label, String value, Color color)
		{
			this.label = label;
			this.value = value;
			this.color = color;
		}
	}
}
