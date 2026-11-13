package com.tfasplitstracker;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TfaSplitsOverlayTest
{
	private static class InspectableOverlay extends TfaSplitsOverlay
	{
		InspectableOverlay(TfaSplitsTrackerPlugin plugin, TfaSplitsTrackerConfig config)
		{
			super(plugin, config);
			setClearChildren(false);
		}
		int rows() { return panelComponent.getChildren().size(); }
		String text() throws Exception
		{
			StringBuilder text = new StringBuilder();
			for (Object line : panelComponent.getChildren())
			{
				for (String name : new String[] {"left", "right"})
				{
					java.lang.reflect.Field field = line.getClass().getDeclaredField(name);
					field.setAccessible(true);
					text.append(field.get(line)).append('|');
				}
			}
			return text.toString();
		}
	}

	@Test
	public void overlayIsOptionalAndNeverRendersPersonalBestComparisons() throws Exception
	{
		TfaSplitsTrackerPlugin plugin = mock(TfaSplitsTrackerPlugin.class);
		TfaRaid raid = TfaOcellusTest.completedFight().raids.get(0);
		when(plugin.displayRaid()).thenReturn(raid);
		when(plugin.timingVisible()).thenReturn(true);
		when(plugin.liveState()).thenReturn(new TfaLiveState(true, "", 540000));
		TfaSplitsTrackerConfig enabled = new TfaSplitsTrackerConfig() {
			@Override public boolean showOverlay() { return true; }
			@Override public boolean chatPersonalBest() { return true; }
		};
		InspectableOverlay overlay = new InspectableOverlay(plugin, enabled);
		BufferedImage before = new BufferedImage(400, 350, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = before.createGraphics();
		assertNull(new TfaSplitsOverlay(plugin, new TfaSplitsTrackerConfig() { }).render(graphics));
		assertNotNull(overlay.render(graphics));
		assertEquals(7, overlay.rows());
		String timingText = overlay.text();
		assertTrue(timingText.contains("P4 Prayer switches"));
		assertFalse(timingText.contains("PB"));
		graphics.dispose();
		raid.personalBest = new TfaPersonalBest();
		raid.personalBest.scale = 3;
		raid.personalBest.total = 1;
		raid.personalBest.splits.addAll(raid.copy().splits);
		BufferedImage after = new BufferedImage(400, 350, BufferedImage.TYPE_INT_ARGB);
		graphics = after.createGraphics();
		assertNotNull(overlay.render(graphics));
		graphics.dispose();
		assertEquals(timingText, overlay.text());
		TfaSplitsTrackerConfig noPhases = new TfaSplitsTrackerConfig() {
			@Override public boolean showOverlay() { return true; }
			@Override public boolean overlayOcellusPhases() { return false; }
			@Override public boolean overlayCumulative() { return true; }
		};
		InspectableOverlay compact = new InspectableOverlay(plugin, noPhases);
		graphics = after.createGraphics();
		assertNotNull(compact.render(graphics));
		assertEquals(3, compact.rows());
		when(plugin.timingVisible()).thenReturn(false);
		assertNull(overlay.render(graphics));
		assertEquals(0, overlay.rows());
		graphics.dispose();
	}
}
