package com.tfasplitstracker;

import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.*;

public class TfaPersonalBestTest
{
	static TfaRaid saved(int scale, long duration)
	{
		TfaRaid raid = new TfaRaid();
		raid.teamSize = scale;
		raid.status = "Completed";
		raid.started = 1000;
		raid.updated = 90000;
		String[] rooms = {"Archive Guardian", "Bork", "Moia", "Xarth", "Ocellus"};
		for (int i = 0; i < rooms.length; i++)
		{
			TfaRaid.Split split = new TfaRaid.Split();
			split.room = rooms[i];
			split.duration = duration;
			split.cumulative = (duration + 2000) * (i + 1);
			raid.splits.add(split);
		}
		raid.total = raid.splits.get(4).cumulative;
		return raid;
	}

	@Test
	public void selectsOnlyCompleteEarlierRunsAtConfirmedScaleAndFreezesSnapshot()
	{
		TfaTracker tracker = new TfaTracker();
		TfaRaid best = saved(3, 10000);
		tracker.raids.add(best);
		tracker.raids.add(saved(3, 12000));
		tracker.raids.add(saved(4, 5000));
		TfaRaid partial = saved(3, 1000);
		partial.splits.remove(2);
		tracker.raids.add(partial);
		TfaRaid invalid = saved(3, 2000);
		invalid.splits.get(2).cumulative = -1;
		tracker.raids.add(invalid);
		TfaRaid future = saved(3, 3000);
		future.updated = 200000;
		tracker.raids.add(future);
		tracker.accept("Challenge started: Archive Guardian.", 100000);
		TfaRaid live = tracker.current();
		live.members.add("Only one name captured");
		assertNull(live.personalBest);
		assertEquals("PB: waiting for party", TfaPersonalBest.status(live));
		tracker.accept("1/3 party members are now ready.", 101000);
		assertEquals(60000, live.personalBest.total);
		best.splits.get(0).duration = 99999;
		tracker.delete(best.id, null);
		tracker.raids.add(saved(3, 5000));
		tracker.accept("2/3 party members are now ready.", 102000);
		assertEquals(10000, live.personalBest.splits.get(0).duration);
		tracker = new Gson().fromJson(new Gson().toJson(tracker), TfaTracker.class);
		assertEquals(60000, tracker.current().personalBest.total);
		tracker.accept("Challenge complete: Archive Guardian. Duration: 0:09.00. Total: 0:11.00", 111000);
		assertEquals("room -0:01.00, raid -0:01.00", tracker.current().personalBest.comparison(tracker.current().splits.get(0)));
	}

	@Test
	public void visiblePartySelectsImmediatelyAndServerConfirmationTakesPriority()
	{
		TfaTracker tracker = new TfaTracker();
		tracker.raids.add(saved(2, 8000));
		TfaRaid threePlayerBest = saved(3, 10000);
		tracker.raids.add(threePlayerBest);
		tracker.raids.add(saved(4, 12000));
		tracker.accept("Challenge started: Archive Guardian.", 100000);
		assertTrue(tracker.party(java.util.Arrays.asList("Alice", "alice", "Bob", "", null)));
		assertEquals(2, tracker.current().scale());
		assertEquals(50000, tracker.current().personalBest.total);
		assertTrue(tracker.party(java.util.Arrays.asList("Alice", "Bob", "Carol")));
		assertEquals(0, tracker.current().teamSize);
		assertEquals("Scale 3 PB: 1:00.00", TfaPersonalBest.status(tracker.current()));
		assertFalse(tracker.party(java.util.Arrays.asList("Alice", "", null)));
		assertFalse(tracker.party(java.util.Arrays.asList("", null)));
		assertEquals(3, tracker.current().scale());
		tracker.delete(threePlayerBest.id, null);
		tracker.accept("1/3 party members are now ready.", 101000);
		assertEquals(60000, tracker.current().personalBest.total);
		tracker = new Gson().fromJson(new Gson().toJson(tracker), TfaTracker.class);
		assertEquals(3, tracker.current().personalBestScale);
		assertEquals(60000, tracker.current().personalBest.total);
		tracker.accept("1/4 party members are now ready.", 102000);
		assertEquals(4, tracker.current().personalBest.scale);
		assertEquals(70000, tracker.current().personalBest.total);
		tracker.party(java.util.Arrays.asList("Alice", "Bob", "Carol", "Dan", "Eve"));
		assertEquals(4, tracker.current().scale());
		assertEquals(4, tracker.current().personalBest.scale);
	}

	@Test
	public void absenceOfReferenceStaysFixedAndNewRaidsCanUseNewRecords()
	{
		TfaTracker tracker = new TfaTracker();
		tracker.accept("Challenge started: Archive Guardian.", 100000);
		tracker.accept("1/3 party members are now ready.", 101000);
		assertNull(tracker.current().personalBest);
		tracker.raids.add(saved(3, 10000));
		tracker.accept("2/3 party members are now ready.", 102000);
		assertNull(tracker.current().personalBest);
		assertEquals("No recorded PB for scale 3", TfaPersonalBest.status(tracker.current()));
		tracker.accept("Challenge started: Archive Guardian.", 200000);
		tracker.accept("1/3 party members are now ready.", 201000);
		assertEquals(60000, tracker.current().personalBest.total);
	}

	@Test
	public void differentBossUsesStageCumulativeButNeverClaimsRoomDelta()
	{
		TfaTracker tracker = new TfaTracker();
		tracker.raids.add(saved(3, 10000));
		tracker.accept("Challenge started: Archive Guardian.", 100000);
		tracker.accept("1/3 party members are now ready.", 101000);
		TfaRaid before = tracker.current().copy();
		tracker.accept("Challenge complete: Ezekiel. Duration: 0:15.00. Total: 0:25.00", 125000);
		TfaRaid live = tracker.current();
		assertEquals("different boss, raid +0:01.00", live.personalBest.comparison(live.splits.get(1)));
		String chat = TfaTiming.messages(before, live, new TfaSplitsTrackerConfig() {
			@Override public boolean chatPersonalBest() { return true; }
		}).get(0);
		assertTrue(chat.contains("vs scale 3 PB: different boss, raid +0:01.00"));
		assertFalse(TfaTiming.messages(before, live, new TfaSplitsTrackerConfig() {
			@Override public boolean chatPersonalBest() { return false; }
		}).get(0).contains("PB"));
		assertEquals("0:00.00", TfaPersonalBest.delta(0));
		live.status = "Completed";
		live.total = 59000;
		assertTrue(TfaTiming.messages(before, live, new TfaSplitsTrackerConfig() {
			@Override public boolean chatPersonalBest() { return true; }
		}).stream()
			.anyMatch(text -> text.endsWith("vs scale 3 PB: -0:01.00")));
	}
}
