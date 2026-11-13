package com.tfasplitstracker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

/** Timing-only overlay. PB comparisons belong in the sidebar and optional chat. */
public class TfaSplitsOverlay extends OverlayPanel
{
	private static final Color MUTED = new Color(170, 170, 170);
	private final TfaSplitsTrackerPlugin plugin;
	private final TfaSplitsTrackerConfig config;

	@Inject
	TfaSplitsOverlay(TfaSplitsTrackerPlugin plugin, TfaSplitsTrackerConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		panelComponent.setPreferredSize(new Dimension(240, 0));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		panelComponent.getChildren().clear();
		TfaRaid raid = plugin.displayRaid();
		if (!config.showOverlay() || raid == null || !plugin.timingVisible()) { return null; }
		line("TFA", config.overlayCumulative() ? "Room / Raid" : "Room time", MUTED);
		for (TfaRaid.Split split : raid.splits)
		{
			line(TfaTiming.label(split.room), split.duration < 0 ? "In progress" : TfaRaid.time(split.duration)
				+ (config.overlayCumulative() ? " / " + TfaRaid.time(split.cumulative) : ""), Color.WHITE);
			if (config.overlayOcellusPhases())
			{
				for (TfaRaid.Phase phase : split.phases)
				{
					boolean active = raid.active() && split.duration < 0 && phase == split.phases.get(split.phases.size() - 1);
					line("  " + TfaOcellus.label(phase.number), phase.duration >= 0 ? TfaRaid.time(phase.duration)
						: active ? "In progress" : "Unrecorded", MUTED);
				}
			}
		}
		long elapsed = raid.active() && plugin.liveState().elapsed >= 0 ? plugin.liveState().elapsed : raid.total;
		line(raid.active() ? "Raid time" : "Raid total", TfaRaid.time(elapsed), Color.WHITE);
		return super.render(graphics);
	}

	private void line(String label, String value, Color color)
	{
		panelComponent.getChildren().add(LineComponent.builder().left(label).right(value).rightColor(color).build());
	}
}
