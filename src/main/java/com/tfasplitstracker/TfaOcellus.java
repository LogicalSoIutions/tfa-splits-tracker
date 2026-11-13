package com.tfasplitstracker;

/** Client-observed phase boundaries, timed in game ticks; never replayed from old chat. */
final class TfaOcellus
{
	static final int VINES_NPC = 16674;
	static final int PORTALS_NPC = 16675;
	static final int QUADRANTS_NPC = 16677;
	static final int DESTROY_QUADRANT_ANIMATION = 14859;
	private TfaRaid.Split split;
	private int phase;
	private int fightTick = -1;
	private int phaseTick = -1;
	private int quadrantDestructions;
	private int lastDestructionTick = -1;

	void begin(TfaRaid.Split next, int tick)
	{
		if (split == next)
		{
			if (fightTick < 0 && phase == 1 && next.phases.size() == 1)
			{
				fightTick = tick;
				phaseTick = tick;
			}
			return;
		}
		attach(next);
		// Existing partial records must not acquire a fabricated start after reloading.
		if (!next.phases.isEmpty()) { return; }
		fightTick = tick;
		phaseTick = tick;
		phase = 1;
		addThrough(1);
	}

	boolean observe(TfaRaid.Split next, int npcId, int tick, boolean boundary)
	{
		int target = npcId == VINES_NPC ? 1 : npcId == PORTALS_NPC ? 2 : npcId == QUADRANTS_NPC ? 3 : 0;
		if (target == 0) { return false; }
		if (split != next) { attach(next); }
		return transition(target, tick, boundary);
	}

	boolean destroyQuadrant(TfaRaid.Split next, int tick)
	{
		if (split != next || phase != 3 || tick == lastDestructionTick) { return false; }
		lastDestructionTick = tick;
		quadrantDestructions++;
		return quadrantDestructions == 3 && transition(4, tick, true);
	}

	private void attach(TfaRaid.Split next)
	{
		split = next;
		phase = next.phases.isEmpty() ? 0 : next.phases.get(next.phases.size() - 1).number;
		fightTick = -1;
		phaseTick = -1;
		quadrantDestructions = 0;
		lastDestructionTick = -1;
	}

	private boolean transition(int target, int tick, boolean boundary)
	{
		if (target <= phase || split.duration >= 0) { return false; }
		if (boundary && phase > 0 && target == phase + 1 && phaseTick >= 0 && tick >= phaseTick)
		{
			TfaRaid.Phase previous = split.phases.get(phase - 1);
			previous.duration = (tick - phaseTick) * 600L;
			previous.cumulative = fightTick < 0 ? -1 : (tick - fightTick) * 600L;
		}
		addThrough(target);
		phase = target;
		// First seeing an already-active NPC does not establish that phase's start.
		phaseTick = boundary && target > 1 ? tick : -1;
		return true;
	}

	private void addThrough(int target)
	{
		while (split.phases.size() < target)
		{
			TfaRaid.Phase record = new TfaRaid.Phase();
			record.number = split.phases.size() + 1;
			split.phases.add(record);
		}
	}

	static void finish(TfaRaid.Split split)
	{
		if (split.phases.size() != 4) { return; }
		long endOfThird = split.phases.get(2).cumulative;
		if (endOfThird < 0 || split.duration < endOfThird) { return; }
		TfaRaid.Phase last = split.phases.get(3);
		last.duration = split.duration - endOfThird;
		last.cumulative = split.duration;
	}

	static String label(int phase)
	{
		switch (phase)
		{
			case 1: return "P1 Vines";
			case 2: return "P2 Portals";
			case 3: return "P3 Quadrants";
			case 4: return "P4 Prayer switches";
			default: return "Unknown phase";
		}
	}
}
