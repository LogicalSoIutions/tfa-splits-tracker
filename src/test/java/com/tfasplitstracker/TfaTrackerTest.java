package com.tfasplitstracker;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class TfaTrackerTest
{
	static final class Event
	{
		long timestamp;
		String message;
	}

	static final class RecordedRaid
	{
		int teamSize;
		String expectedTotal;
		Event[] events;
	}

	static RecordedRaid[] recordings() throws Exception
	{
		try (InputStreamReader reader = new InputStreamReader(TfaTrackerTest.class.getResourceAsStream("recorded-raids.json"), StandardCharsets.UTF_8))
		{
			return new Gson().fromJson(reader, RecordedRaid[].class);
		}
	}

	@Test
	public void replayServerMessagesForThreeFourFiveAndSixPlayers() throws Exception
	{
		Set<Integer> sizes = new HashSet<>();
		for (RecordedRaid recording : recordings())
		{
			TfaTracker tracker = new TfaTracker();
			for (Event event : recording.events)
			{
				assertTrue(event.message, tracker.accept(event.message, event.timestamp));
				assertFalse("Duplicate event", tracker.accept(event.message, event.timestamp));
			}
			assertNull(tracker.current());
			assertEquals(1, tracker.raids.size());
			TfaRaid raid = tracker.raids.get(0);
			assertEquals("Completed", raid.status);
			assertEquals(recording.expectedTotal, TfaRaid.time(raid.total));
			assertEquals(recording.teamSize, raid.teamSize);
			assertTrue(raid.completeSplits());
			assertEquals(5, raid.splits.size());
			assertTrue("Overall includes transitions", raid.total > raid.combatTime());
			for (TfaRaid.Split split : raid.splits)
			{
				Event completion = Arrays.stream(recording.events).filter(e -> e.message.startsWith("Challenge complete: " + split.room + ".")).findFirst().get();
				String plain = completion.message.replaceAll("<[^>]*>", "");
				assertTrue(plain, plain.contains("Duration: " + TfaRaid.time(split.duration) + ". Total: " + TfaRaid.time(split.cumulative)));
			}
			sizes.add(raid.teamSize);
		}
		assertEquals(new HashSet<>(Arrays.asList(3, 4, 5, 6)), sizes);
	}

	@Test
	public void recoverWithoutAnyStartCallbacks() throws Exception
	{
		RecordedRaid recording = recordings()[0];
		TfaTracker tracker = new TfaTracker();
		for (Event event : recording.events)
		{
			if (!event.message.startsWith("Challenge started:"))
			{
				tracker.accept(event.message, event.timestamp);
			}
		}
		assertEquals(1, tracker.raids.size());
		assertEquals(recording.expectedTotal, TfaRaid.time(tracker.raids.get(0).total));
		assertTrue(tracker.raids.get(0).completeSplits());
	}

	@Test
	public void recoverFinalTotalWithoutOcellusCallback() throws Exception
	{
		RecordedRaid recording = recordings()[0];
		TfaTracker tracker = new TfaTracker();
		for (Event event : recording.events)
		{
			if (!event.message.startsWith("Challenge complete: Ocellus"))
			{
				tracker.accept(event.message, event.timestamp);
			}
		}
		assertEquals("Completed", tracker.raids.get(0).status);
		assertEquals(recording.expectedTotal, TfaRaid.time(tracker.raids.get(0).total));
		assertFalse("Never invent a missing split", tracker.raids.get(0).completeSplits());
	}

	@Test
	public void journalSurvivesRestartAndConsecutiveRuns() throws Exception
	{
		RecordedRaid recording = recordings()[0];
		TfaTracker tracker = new TfaTracker();
		Gson gson = new Gson();
		for (Event event : recording.events)
		{
			tracker.accept(event.message, event.timestamp);
			tracker = gson.fromJson(gson.toJson(tracker), TfaTracker.class);
			assertFalse(tracker.accept(event.message, event.timestamp));
		}
		for (Event event : recording.events)
		{
			tracker.accept(event.message, event.timestamp + 86400000);
		}
		assertEquals(2, tracker.raids.size());
		assertNotEquals(tracker.raids.get(0).id, tracker.raids.get(1).id);
		assertTrue(tracker.raids.stream().allMatch(TfaRaid::completeSplits));
	}

	@Test
	public void localDeathIsNotRaidFailureAndAllEightPartySlotsAreSupported()
	{
		TfaTracker tracker = new TfaTracker();
		tracker.accept("Challenge started: Archive Guardian.", 1000);
		assertFalse(tracker.accept("You will respawn when your party completes or fails the challenge.", 2000));
		assertNotNull(tracker.current());
		assertTrue(tracker.party(Arrays.asList("One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight")));
		assertFalse(tracker.party(Arrays.asList("oNe", "Two", null, "")));
		assertEquals(8, tracker.current().members.size());
		tracker.accept("You failed to survive the Fractured Archive.", 3000);
		assertNull(tracker.current());
		assertEquals("Failed", tracker.raids.get(0).status);
		tracker.accept("Challenge started: Archive Guardian.", 4000);
		tracker.accept("You abandon the raid and leave the Fractured Archive.", 5000);
		assertEquals("Abandoned", tracker.raids.get(0).status);
		assertEquals(2, tracker.raids.size());
	}

	@Test
	public void detectNewRunEvenWhenItsFirstStartWasMissed()
	{
		TfaTracker tracker = new TfaTracker();
		tracker.accept("Challenge started: Archive Guardian.", 1000);
		tracker.accept("Challenge complete: Archive Guardian. Duration: 2:00.00. Total: 2:00.00", 121000);
		tracker.accept("Challenge started: Bork.", 130000);
		tracker.accept("Challenge complete: Archive Guardian. Duration: 1:58.20. Total: 1:58.20", 400000);
		assertEquals(2, tracker.raids.size());
		assertEquals("Interrupted", tracker.raids.get(1).status);
		assertEquals("1:58.20", TfaRaid.time(tracker.current().total));
	}

	@Test
	public void supportHourLongRunsAndRepeatedFirstRoomAttempts()
	{
		TfaTracker tracker = new TfaTracker();
		tracker.accept("Challenge started: Archive Guardian.", 1000);
		tracker.accept("Challenge started: Archive Guardian.", 180000);
		assertEquals(2, tracker.raids.size());
		assertEquals("Interrupted", tracker.raids.get(1).status);
		tracker.accept("The Fractured Archive: Total completion time: 1:03:35.40. Personal best: 23:19.20", 4000000);
		assertEquals("Completed", tracker.raids.get(0).status);
		assertEquals(3815400, tracker.raids.get(0).total);
	}

	@Test
	public void deletedRaidStaysDeletedAfterPersistenceAndChatReplay() throws Exception
	{
		RecordedRaid recording = recordings()[0];
		TfaTracker tracker = new TfaTracker();
		for (Event event : recording.events)
		{
			tracker.accept(event.message, event.timestamp);
		}
		String id = tracker.raids.get(0).id;
		assertTrue(tracker.delete(id, null));
		assertTrue(tracker.raids.isEmpty());
		tracker = new Gson().fromJson(new Gson().toJson(tracker), TfaTracker.class);
		for (Event event : recording.events)
		{
			assertFalse(tracker.accept(event.message, event.timestamp));
		}
		assertTrue(tracker.raids.isEmpty());
		assertTrue(tracker.undoDelete());
		assertEquals(id, tracker.raids.get(0).id);
		assertEquals(recording.expectedTotal, TfaRaid.time(tracker.raids.get(0).total));
		assertFalse(tracker.undoDelete());
	}

	@Test
	public void deleteRoomPreservesServerTotalAndUndoRestoresCompleteHistory() throws Exception
	{
		RecordedRaid recording = recordings()[0];
		TfaTracker tracker = new TfaTracker();
		for (Event event : recording.events)
		{
			tracker.accept(event.message, event.timestamp);
		}
		TfaRaid raid = tracker.raids.get(0);
		assertFalse(tracker.delete(raid.id, "Not a room"));
		assertTrue(tracker.delete(raid.id, "Bork"));
		assertFalse(raid.completeSplits());
		assertEquals(4, raid.splits.size());
		assertEquals(recording.expectedTotal, TfaRaid.time(raid.total));
		assertTrue(tracker.undoDelete());
		assertTrue(tracker.raids.get(0).completeSplits());
	}

	@Test
	public void resettingActiveRunSuppressesRemainingEventsUntilNextRaid() throws Exception
	{
		RecordedRaid recording = recordings()[0];
		TfaTracker tracker = new TfaTracker();
		Event start = recording.events[0];
		tracker.accept(start.message, start.timestamp);
		assertFalse(tracker.delete(tracker.current().id, "Archive Guardian"));
		assertTrue(tracker.delete(tracker.current().id, null));
		assertNull(tracker.current());
		tracker = new Gson().fromJson(new Gson().toJson(tracker), TfaTracker.class);
		for (Event event : recording.events)
		{
			tracker.accept(event.message, event.timestamp);
		}
		assertTrue(tracker.raids.isEmpty());
		assertFalse(tracker.undoDelete());
		tracker.accept(start.message, start.timestamp + 86400000);
		assertNotNull(tracker.current());
		assertFalse(tracker.suppressUntilNextRaid);
	}

	@Test
	public void undoOldDeletionDoesNotReplaceNewActiveRun() throws Exception
	{
		RecordedRaid recording = recordings()[0];
		TfaTracker tracker = new TfaTracker();
		for (Event event : recording.events)
		{
			tracker.accept(event.message, event.timestamp);
		}
		tracker.delete(tracker.raids.get(0).id, null);
		tracker.accept("Challenge started: Archive Guardian.", recording.events[0].timestamp + 86400000);
		String active = tracker.current().id;
		tracker.undoDelete();
		assertEquals(active, tracker.current().id);
		assertEquals(2, tracker.raids.size());
	}

	@Test
	public void ignoreOtherRaidsAndClanBroadcastsAndMarkMissingRooms()
	{
		TfaTracker tracker = new TfaTracker();
		assertFalse(tracker.accept("Challenge started: Akkha.", 1000));
		assertFalse(tracker.accept("Challenge complete: Akkha. Duration: 1:20.40. Total: 4:10.80", 2000));
		assertFalse(tracker.accept("Someone has achieved a new The Fractured Archive (team size: 5) Normal mode Overall personal best: 23:19.20", 3000));
		assertTrue(tracker.raids.isEmpty());
		tracker.accept("Challenge complete: Ocellus. Duration: <col=ef1020>9:23.40</col>. Total: <col=ef1020>19:16.20</col> (new personal best)", 2000000);
		assertEquals(1156200, tracker.raids.get(0).total);
		assertFalse(tracker.raids.get(0).completeSplits());
	}
}
