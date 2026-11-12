package com.tfasplitstracker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

public class TfaSplitsOverlay extends OverlayPanel
{
	private final TfaSplitsTrackerPlugin plugin;
	private final TfaSplitsTrackerConfig config;

	@Inject
	TfaSplitsOverlay(TfaSplitsTrackerPlugin plugin, TfaSplitsTrackerConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		panelComponent.setPreferredSize(new Dimension(270, 0));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		panelComponent.getChildren().clear();
		TfaRaid raid = plugin.displayRaid();
		if (!config.showOverlay() || raid == null || !plugin.timingVisible(config.keepOverlayAfterRaid()))
		{
			return null;
		}
		line("TFA splits", config.overlayCumulative() ? "Room / Raid" : "Room time", Color.CYAN);
		for (TfaRaid.Split split : raid.splits)
		{
			if (split.duration >= 0)
			{
				line(TfaTiming.label(split.room), TfaRaid.time(split.duration)
					+ (config.overlayCumulative() ? " / " + TfaRaid.time(split.cumulative) : ""), Color.WHITE);
			}
		}
		boolean live = raid.active() && plugin.timingVisible(false);
		if (config.overlayLiveTimers() && live)
		{
			TfaRaid.Split last = raid.splits.isEmpty() ? null : raid.splits.get(raid.splits.size() - 1);
			if (last != null && last.duration < 0)
			{
				line(TfaTiming.label(last.room), "In progress", Color.YELLOW);
			}
			else { line("Between rooms", "", Color.YELLOW); }
			line("Raid time", TfaRaid.time(plugin.liveState().elapsed), Color.YELLOW);
		}
		else if (!raid.active())
		{
			line("Completed".equals(raid.status) ? "Raid total" : "Last raid split", TfaRaid.time(raid.total), Color.WHITE);
		}
		return super.render(graphics);
	}

	private void line(String label, String value, Color color)
	{
		panelComponent.getChildren().add(LineComponent.builder().left(label).right(value).rightColor(color).build());
	}
}
