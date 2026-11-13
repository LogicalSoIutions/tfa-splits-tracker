package com.tfasplitstracker;

import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.*;

public class TfaOcellusTest
{
	static TfaTracker completedFight()
	{
		TfaTracker tracker = new TfaTracker();
		tracker.accept("Challenge started: Ocellus.", 100000);
		TfaRaid.Split split = tracker.current().splits.get(0);
		TfaOcellus phases = new TfaOcellus();
		phases.begin(split, 100);
		phases.begin(split, 101); // Duplicate callback must not move the start.
		assertTrue(phases.observe(split, 16675, 200, true));
		assertFalse(phases.observe(split, 16675, 201, true));
		assertTrue(phases.observe(split, 16677, 300, true));
		assertFalse(phases.destroyQuadrant(split, 340));
		assertFalse(phases.destroyQuadrant(split, 340));
		assertFalse(phases.destroyQuadrant(split, 370));
		assertTrue(phases.destroyQuadrant(split, 400));
		assertFalse(phases.destroyQuadrant(split, 400));
		tracker.accept("Challenge complete: Ocellus. Duration: 3:30.00. Total: 9:00.00", 310000);
		return tracker;
	}

	@Test
	public void recordsFourPhasesAndUsesServerDurationForFinalRemainder()
	{
		TfaTracker tracker = completedFight();
		TfaRaid.Split split = tracker.raids.get(0).splits.get(0);
		assertEquals(4, split.phases.size());
		assertArrayEquals(new long[] {60000, 60000, 60000, 30000}, split.phases.stream().mapToLong(p -> p.duration).toArray());
		assertEquals(split.duration, split.phases.stream().mapToLong(p -> p.duration).sum());
		assertEquals(180000, split.phases.get(2).cumulative);
		TfaRaid copy = tracker.raids.get(0).copy();
		copy.splits.get(0).phases.get(0).duration = 1;
		assertEquals(60000, split.phases.get(0).duration);
		TfaTracker loaded = new Gson().fromJson(new Gson().toJson(tracker), TfaTracker.class);
		assertEquals(30000, loaded.raids.get(0).splits.get(0).phases.get(3).duration);
	}

	@Test
	public void lateNpcSpawnAndReloadDoNotInventMissingPhaseTimes()
	{
		TfaRaid.Split split = new TfaRaid.Split();
		TfaOcellus phases = new TfaOcellus();
		phases.observe(split, 16675, 100, false); // Joining an already-running portal phase.
		phases.observe(split, 16677, 200, true);
		assertEquals(-1, split.phases.get(0).duration);
		assertEquals(-1, split.phases.get(1).duration);
		phases = new TfaOcellus(); // Re-enable halfway through quadrants.
		phases.observe(split, 16677, 250, false);
		phases.destroyQuadrant(split, 300);
		phases.destroyQuadrant(split, 350);
		phases.destroyQuadrant(split, 400);
		split.duration = 210000;
		TfaOcellus.finish(split);
		assertTrue(split.phases.stream().allMatch(p -> p.duration < 0));
		TfaRaid old = new Gson().fromJson("{\"splits\":[{\"room\":\"Ocellus\",\"duration\":1000}]}", TfaRaid.class);
		assertTrue(old.copy().splits.get(0).phases.isEmpty());
	}

	@Test
	public void p1SpawnBeforeLiveStartCallbackStillGetsFullTiming()
	{
		TfaRaid.Split split = new TfaRaid.Split();
		TfaOcellus phases = new TfaOcellus();
		phases.observe(split, 16674, 100, false);
		phases.begin(split, 100);
		phases.observe(split, 16675, 200, true);
		assertEquals(60000, split.phases.get(0).duration);
		TfaRaid.Split nextRun = new TfaRaid.Split();
		phases.begin(nextRun, 300);
		phases.observe(nextRun, 16675, 350, true);
		assertEquals(30000, nextRun.phases.get(0).duration);
	}

	@Test
	public void phaseChatHonorsToggleAndNeverRepeatsCompletedPhases()
	{
		TfaRaid raid = completedFight().raids.get(0);
		TfaSplitsTrackerConfig phaseOnly = new TfaSplitsTrackerConfig() {
			@Override public boolean chatOcellusPhases() { return true; }
			@Override public boolean chatSplits() { return false; }
			@Override public boolean chatTotal() { return false; }
		};
		assertEquals(4, TfaTiming.messages(null, raid, phaseOnly).size());
		assertEquals("TFA: Ocellus P4 Prayer switches - 0:30.00", TfaTiming.messages(null, raid, phaseOnly).get(3));
		assertTrue(TfaTiming.messages(raid.copy(), raid, phaseOnly).isEmpty());
		assertEquals(2, TfaTiming.messages(null, raid, new TfaSplitsTrackerConfig() { }).size());
	}
}
