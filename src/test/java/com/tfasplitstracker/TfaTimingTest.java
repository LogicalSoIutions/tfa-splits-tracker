package com.tfasplitstracker;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TfaTimingTest
{
	private final TfaSplitsTrackerConfig config = new TfaSplitsTrackerConfig() { };

	private TfaTracker completedRoom()
	{
		TfaTracker tracker = new TfaTracker();
		tracker.accept("Challenge started: Archive Guardian.", 100000);
		tracker.accept("Challenge complete: Archive Guardian. Duration: 0:59.40. Total: 1:02.40", 162000);
		return tracker;
	}

	@Test
	public void roomMessageUsesServerTimeAndDoesNotRepeatForRosterUpdates()
	{
		TfaRaid raid = completedRoom().current();
		assertEquals("TFA: Archive Guardian - 0:59.40 (raid split 1:02.40)", TfaTiming.messages(null, raid, config).get(0));
		TfaRaid before = raid.copy();
		raid.members.add("Raider");
		assertTrue(TfaTiming.messages(before, raid, config).isEmpty());
	}

	@Test
	public void finalRoomAndOverallMessageAnnounceTotalOnlyOnceInEitherOrder()
	{
		for (boolean totalFirst : new boolean[] {true, false})
		{
			TfaTracker tracker = completedRoom();
			String room = "Challenge complete: Ocellus. Duration: 2:00.00. Total: 8:00.00";
			String total = "The Fractured Archive: Total completion time: 8:00.00";
			TfaRaid before = tracker.current().copy();
			tracker.accept(totalFirst ? total : room, 580000);
			assertEquals(totalFirst ? 1 : 2, TfaTiming.messages(before, tracker.raids.get(0), config).size());
			before = tracker.raids.get(0).copy();
			tracker.accept(totalFirst ? room : total, 580000);
			assertEquals(totalFirst ? 1 : 0, TfaTiming.messages(before, tracker.raids.get(0), config).size());
		}
	}

	@Test
	public void chatSettingsAreIndependent()
	{
		TfaRaid raid = completedRoom().current();
		raid.status = "Completed";
		TfaSplitsTrackerConfig totalOnly = new TfaSplitsTrackerConfig() {
			@Override public boolean chatSplits() { return false; }
		};
		assertEquals(1, TfaTiming.messages(null, raid, totalOnly).size());
		TfaSplitsTrackerConfig neither = new TfaSplitsTrackerConfig() {
			@Override public boolean chatSplits() { return false; }
			@Override public boolean chatTotal() { return false; }
		};
		assertTrue(TfaTiming.messages(null, raid, neither).isEmpty());
		assertEquals(59400, raid.splits.get(0).duration);
	}

	@Test
	public void liveRaidClockReadsHudTextWithoutExtrapolation()
	{
		assertEquals(65400, TfaLiveState.parseClock("<col=ffffff>1:05.40</col>"));
		assertEquals(65400, TfaLiveState.parseClock("Raid time: 1:05.40"));
		assertEquals(3725400, TfaLiveState.parseClock("1:02:05.40"));
		assertEquals(65000, TfaLiveState.parseClock("1:05"));
		assertEquals(-1, TfaLiveState.parseClock("-"));
		assertEquals(-1, TfaLiveState.parseClock(null));
	}

	@Test
	public void leavingClosesOnlyActiveRaidsAndPreservesRecordedTimes()
	{
		TfaTracker tracker = completedRoom();
		assertTrue(tracker.leaveRaid());
		assertNull(tracker.current());
		assertEquals("Left raid", tracker.raids.get(0).status);
		assertEquals(62400, tracker.raids.get(0).total);
		assertEquals(59400, tracker.raids.get(0).splits.get(0).duration);
		assertFalse(tracker.leaveRaid());
		for (String status : new String[] {"Completed", "Failed", "Abandoned"})
		{
			tracker.raids.get(0).status = status;
			assertFalse(tracker.leaveRaid());
			assertEquals(status, tracker.raids.get(0).status);
		}
	}

	@Test
	public void infoboxTextAndTooltipRespondToSettings()
	{
		TfaSplitsTrackerPlugin plugin = mock(TfaSplitsTrackerPlugin.class);
		TfaSplitsTrackerConfig settings = mock(TfaSplitsTrackerConfig.class);
		when(settings.infoBoxText()).thenReturn(TfaSplitsTrackerConfig.InfoBoxText.ROOM_TIME);
		when(settings.showInfoBoxes()).thenReturn(true);
		when(plugin.timingVisible(false)).thenReturn(true);
		TfaSplitInfoBox box = new TfaSplitInfoBox(plugin, settings, completedRoom().current().splits.get(0));
		assertEquals("0:59", box.getText());
		assertTrue(box.render());
		assertEquals("", box.getTooltip());
		when(settings.infoBoxText()).thenReturn(TfaSplitsTrackerConfig.InfoBoxText.CUMULATIVE);
		when(settings.infoBoxTooltip()).thenReturn(true);
		when(settings.infoBoxCumulative()).thenReturn(true);
		assertEquals("1:02", box.getText());
		assertTrue(box.getTooltip().contains("Room time: 0:59.40<br>Raid split: 1:02.40"));
		when(settings.infoBoxText()).thenReturn(TfaSplitsTrackerConfig.InfoBoxText.NONE);
		when(settings.showInfoBoxes()).thenReturn(false);
		assertEquals("", box.getText());
		assertFalse(box.render());
	}

	@Test
	public void overlayShowsRoomAndCumulativeTimesAndHonorsVisibility()
	{
		TfaSplitsTrackerPlugin plugin = mock(TfaSplitsTrackerPlugin.class);
		when(plugin.displayRaid()).thenReturn(completedRoom().current());
		when(plugin.liveState()).thenReturn(new TfaLiveState(true, "In progress", 65400));
		when(plugin.timingVisible(false)).thenReturn(true);
		TfaSplitsOverlay overlay = new TfaSplitsOverlay(plugin, config);
		Graphics2D graphics = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB).createGraphics();
		try
		{
			assertNotNull(overlay.render(graphics));
			when(plugin.timingVisible(false)).thenReturn(false);
			assertNull(overlay.render(graphics));
			TfaSplitsTrackerConfig disabled = new TfaSplitsTrackerConfig() {
				@Override public boolean showOverlay() { return false; }
			};
			when(plugin.timingVisible(false)).thenReturn(true);
			assertNull(new TfaSplitsOverlay(plugin, disabled).render(graphics));
		}
		finally { graphics.dispose(); }
	}
}
