package com.tfasplitstracker;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.JTextField;
import org.junit.Test;
import static org.junit.Assert.*;

public class TfaRaidComparisonPanelTest
{
	private static List<TfaRaid> recordings() throws Exception
	{
		List<TfaRaid> raids = new ArrayList<>();
		for (TfaTrackerTest.RecordedRaid recording : TfaTrackerTest.recordings())
		{
			TfaTracker tracker = new TfaTracker();
			for (TfaTrackerTest.Event event : recording.events)
			{
				tracker.accept(event.message, event.timestamp);
			}
			raids.add(tracker.raids.get(0));
		}
		return raids;
	}

	private static int row(TfaRaidComparisonPanel.ComparisonModel model, String name)
	{
		for (int i = 0; i < model.getRowCount(); i++)
		{
			if (name.equals(model.getValueAt(i, 0)))
			{
				return i;
			}
		}
		throw new AssertionError("Missing row: " + name);
	}

	@Test
	public void compareRecordedServerSplitsAndChangeBaseline() throws Exception
	{
		List<TfaRaid> raids = recordings();
		TfaRaid first = raids.get(0);
		TfaRaid second = raids.get(1);
		TfaRaidComparisonPanel.ComparisonModel model = new TfaRaidComparisonPanel.ComparisonModel();
		model.setRaids(Arrays.asList(first, second), first.id);
		for (TfaRaid.Split split : first.splits)
		{
			int row = row(model, "Room " + TfaRoom.find(split.room).stage);
			long other = second.splits.stream().filter(s -> s.room.equals(split.room)).findFirst().get().duration;
			assertEquals(TfaRaid.time(split.duration), model.getValueAt(row, 1));
			assertEquals(TfaRaid.time(other), model.getValueAt(row, 2));
			assertEquals(Long.valueOf(other - split.duration), model.delta(row, 2));
		}
		int total = row(model, "Total raid time");
		assertEquals(Long.valueOf(second.total - first.total), model.delta(total, 2));
		assertEquals(TfaRaid.time(first.total - first.combatTime()), model.getValueAt(row(model, "Between rooms"), 1));
		model.setRaids(Arrays.asList(first, second), second.id);
		assertEquals(Long.valueOf(first.total - second.total), model.delta(total, 1));
		assertTrue(model.isBaseline(2));
		assertEquals(Long.valueOf(0), model.delta(total, 2));
	}

	@Test
	public void compareDifferentBossesByStageAndNeverInventMissingTimes() throws Exception
	{
		List<TfaRaid> raids = recordings();
		TfaRaid first = raids.get(0);
		TfaRaid second = raids.get(1);
		TfaRaid.Split replacement = second.splits.stream().filter(s -> s.room.equals("Bork")).findFirst().get();
		replacement.room = "Ezekiel";
		second.splits.stream().filter(s -> s.room.equals("Dreadnoth the Jellied")).findFirst().get().room = "Moia";
		second.splits.stream().filter(s -> s.room.equals("Xarth")).findFirst().get().room = "Dagon'hai Elder and monks";
		Collections.reverse(second.splits);
		second.splits.removeIf(s -> s.room.equals("Archive Guardian"));
		TfaRaidComparisonPanel.ComparisonModel model = new TfaRaidComparisonPanel.ComparisonModel();
		model.setRaids(Arrays.asList(first, second), first.id);
		int roomTwo = row(model, "Room 2");
		assertEquals("Bork & the Smorks", model.boss(roomTwo, 1));
		assertEquals("Ezekiel", model.boss(roomTwo, 2));
		assertEquals(TfaRaid.time(replacement.duration), model.getValueAt(roomTwo, 2));
		long bork = first.splits.stream().filter(s -> s.room.equals("Bork")).findFirst().get().duration;
		assertEquals(Long.valueOf(replacement.duration - bork), model.delta(roomTwo, 2));
		assertEquals("Moia", model.boss(row(model, "Room 3"), 2));
		assertEquals("Dreadnoth the Jellied", model.boss(row(model, "Room 3"), 1));
		assertNotNull(model.delta(row(model, "Room 3"), 2));
		assertEquals("Dagon'hai Elder & monks", model.boss(row(model, "Room 4"), 2));
		assertEquals("Xarth", model.boss(row(model, "Room 4"), 1));
		assertNotNull(model.delta(row(model, "Room 4"), 2));
		assertEquals("—", model.getValueAt(row(model, "Room 1"), 2));
		assertNull(model.delta(row(model, "Room 1"), 2));
		assertEquals("—", model.getValueAt(row(model, "Combat time"), 2));
		assertEquals("—", model.getValueAt(row(model, "Between rooms"), 2));
		assertEquals(TfaRaid.time(second.total), model.getValueAt(row(model, "Total raid time"), 2));
		second.status = "Failed";
		assertEquals("—", model.getValueAt(row(model, "Total raid time"), 2));
		assertNull(model.delta(row(model, "Total raid time"), 2));
	}

	@Test
	public void enforceFiveRaidLimitAndReconcileDeletedSelections() throws Exception
	{
		List<TfaRaid> raids = recordings();
		SwingUtilities.invokeAndWait(() ->
		{
			TfaRaidComparisonPanel panel = new TfaRaidComparisonPanel(raids);
			raids.forEach(r -> panel.selectRaid(r.id, false));
			for (int i = 0; i < 5; i++)
			{
				assertTrue(panel.selectRaid(raids.get(i).id, true));
			}
			assertFalse(panel.selectRaid(raids.get(5).id, true));
			assertEquals(6, panel.comparisonModel.getColumnCount());
			panel.setBaseline(raids.get(3).id);
			assertTrue(panel.comparisonModel.isBaseline(4));
			panel.setRaids(raids.stream().filter(r -> !r.id.equals(raids.get(3).id)).collect(Collectors.toList()));
			assertEquals(5, panel.comparisonModel.getColumnCount());
			assertTrue(panel.comparisonModel.isBaseline(1));
			assertTrue(panel.selectRaid(raids.get(5).id, true));
			assertEquals(6, panel.comparisonModel.getColumnCount());
			panel.setRaids(Collections.emptyList());
			assertEquals(1, panel.comparisonModel.getColumnCount());
		});
	}

	@Test
	public void teamSizeFilterCombinesWithSearchAndPreservesComparison()
	{
		List<TfaRaid> raids = new ArrayList<>();
		for (int i = 0; i < 3; i++)
		{
			TfaRaid raid = new TfaRaid();
			raid.status = "Completed";
			raid.started = 1000 + i;
			raid.teamSize = i == 0 ? 2 : i == 1 ? 3 : 0;
			raid.members.add(i == 2 ? "Bob" : "Alice");
			if (i == 2)
			{
				raid.members.add("Second raider");
			}
			raids.add(raid);
		}
		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				TfaRaidComparisonPanel panel = new TfaRaidComparisonPanel(raids);
				raids.forEach(r -> panel.selectRaid(r.id, false));
				panel.selectRaid(raids.get(0).id, true);
				panel.selectRaid(raids.get(1).id, true);
				panel.setBaseline(raids.get(1).id);
				JComboBox<?> sizes = (JComboBox<?>) findByName(panel, "comparisonTeamSize");
				JTable picker = (JTable) findByName(panel, "comparisonRaidList");
				JTextField search = (JTextField) findByName(panel, "comparisonRaidSearch");
				assertEquals(3, picker.getRowCount());
				sizes.setSelectedIndex(2);
				assertEquals("Two players includes roster fallback", 2, picker.getRowCount());
				assertEquals(3, panel.comparisonModel.getColumnCount());
				assertTrue(panel.comparisonModel.isBaseline(2));
				search.setText("Alice");
				assertEquals(1, picker.getRowCount());
				search.setText("No matching raider");
				assertEquals(0, picker.getRowCount());
				assertEquals("Hidden selections stay compared", 3, panel.comparisonModel.getColumnCount());
				assertTrue(panel.comparisonModel.isBaseline(2));
				search.setText("");
				sizes.setSelectedIndex(1);
				assertEquals(0, picker.getRowCount());
				sizes.setSelectedIndex(3);
				assertEquals(1, picker.getRowCount());
				sizes.setSelectedIndex(0);
				assertEquals(3, picker.getRowCount());
			});
		}
		catch (Exception ex)
		{
			throw new AssertionError(ex);
		}
	}

	private static Component findByName(Container container, String name)
	{
		for (Component child : container.getComponents())
		{
			if (name.equals(child.getName()))
			{
				return child;
			}
			if (child instanceof Container)
			{
				Component found = findByName((Container) child, name);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	@Test
	public void renderComparisonWindowContents() throws Exception
	{
		List<TfaRaid> raids = recordings();
		raids.sort(java.util.Comparator.comparingLong((TfaRaid r) -> r.started).reversed());
		for (TfaRaid.Split split : raids.get(1).splits)
		{
			if (split.room.equals("Bork")) { split.room = "Ezekiel"; }
			if (split.room.equals("Dreadnoth the Jellied")) { split.room = "Moia"; }
			if (split.room.equals("Xarth")) { split.room = "Dagon'hai Elder and monks"; }
		}
		raids.get(0).members.addAll(Arrays.asList("Archive Raider", "Second Raider", "Third Raider"));
		SwingUtilities.invokeAndWait(() ->
		{
			TfaRaidComparisonPanel panel = new TfaRaidComparisonPanel(raids);
			panel.setSize(1200, 740);
			layout(panel);
			BufferedImage image = new BufferedImage(1200, 740, BufferedImage.TYPE_INT_RGB);
			Graphics2D graphics = image.createGraphics();
			panel.printAll(graphics);
			graphics.dispose();
			try
			{
				ImageIO.write(image, "png", new File("build/tfa-raid-comparison.png"));
			}
			catch (Exception ex)
			{
				throw new AssertionError(ex);
			}
		});
	}

	private static void layout(Container container)
	{
		container.doLayout();
		for (Component child : container.getComponents())
		{
			if (child instanceof Container)
			{
				layout((Container) child);
			}
		}
	}
}
