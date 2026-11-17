package com.tfasplitstracker;

import java.awt.Component;
import java.awt.Container;
import java.util.Arrays;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class TfaRaidSearchTest
{
	@Test
	public void matchesTotalTimePrefixesLimitsAndInclusiveRanges()
	{
		TfaRaid raid = raid(754560, 2);
		for (String query : Arrays.asList("12:", "12:3", "12:34", "12:34.5", "12:34.56",
			" < 13:00 ", "<=12:34.56", ">12:34.55", ">=12:34.56", "12:00 - 12:34.56"))
		{
			assertTrue(query, TfaRaidSearch.matches(raid, query, ""));
		}
		for (String query : Arrays.asList("2:34", "12:34.57", "<12:34.56", ">12:34.56",
			"<=12:34.55", ">=12:34.57", "12:35-13:00", "13:00-12:00", "12:60",
			"<99999999999999999999:00", "0:00-99999999999999999999:00"))
		{
			assertFalse(query, TfaRaidSearch.matches(raid, query, ""));
		}
		raid.total = 720000;
		assertTrue(TfaRaidSearch.matches(raid, "12:00-13:00", ""));
		raid.total = 780000;
		assertTrue(TfaRaidSearch.matches(raid, "12:00-13:00", ""));
		raid.total = 780010;
		assertFalse(TfaRaidSearch.matches(raid, "12:00-13:00", ""));
	}

	@Test
	public void preservesTextSearchAndExcludesUnknownTimes()
	{
		TfaRaid raid = raid(-1, 2);
		String details = "Nov 16, 2026 Completed Alice";
		for (String query : Arrays.asList("", " ALICE ", "completed", "Nov 16"))
		{
			assertTrue(query, TfaRaidSearch.matches(raid, query, details));
		}
		for (String query : Arrays.asList("0:00", "<13:00", "<=13:00", ">0:00", ">=0:00", "0:00-13:00"))
		{
			assertFalse(query, TfaRaidSearch.matches(raid, query, details));
		}
	}

	@Test
	public void bothPanelsFilterByTimeAndTeamSizeWhileKeepingComparisons() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			TfaTracker data = new TfaTracker();
			data.raids.addAll(Arrays.asList(raid(754560, 2), raid(780000, 3), raid(-1, 2)));
			TfaSplitsTrackerPanel history = new TfaSplitsTrackerPanel();
			try
			{
				history.update(data, true, false);
				TfaRaidComparisonPanel compare = new TfaRaidComparisonPanel(data.raids);
				JTextField historySearch = find(history, JTextField.class);
				JTextField compareSearch = find(compare, JTextField.class);
				JTable picker = find(compare, JTable.class);
				String[] queries = {"12:34", "<13:00", "12:00-13:00", ">13:00", "", "Alice"};
				int[] expected = {1, 1, 2, 0, 3, 3};
				for (int i = 0; i < queries.length; i++)
				{
					historySearch.setText(queries[i]);
					compareSearch.setText(queries[i]);
					assertEquals(queries[i], expected[i], completedLabels(history));
					assertEquals(queries[i], expected[i], picker.getRowCount());
					assertEquals("Filtering preserves selected comparisons", 3, compare.comparisonModel.getColumnCount());
				}
				historySearch.setText("12:00-13:00");
				compareSearch.setText("12:00-13:00");
				find(history, JComboBox.class).setSelectedIndex(2);
				find(compare, JComboBox.class).setSelectedIndex(2);
				assertEquals(1, completedLabels(history));
				assertEquals(1, picker.getRowCount());
				assertEquals(3, compare.comparisonModel.getColumnCount());
			}
			finally { history.stop(); }
		});
	}

	private static TfaRaid raid(long total, int size)
	{
		TfaRaid raid = new TfaRaid();
		raid.status = "Completed";
		raid.total = total;
		raid.teamSize = size;
		raid.members.add("Alice");
		return raid;
	}

	private static int completedLabels(Container container)
	{
		int count = 0;
		for (Component child : container.getComponents())
		{
			if (child instanceof JLabel && "Completed".equals(((JLabel) child).getText())) { count++; }
			if (child instanceof Container) { count += completedLabels((Container) child); }
		}
		return count;
	}

	private static <T> T find(Container container, Class<T> type)
	{
		for (Component child : container.getComponents())
		{
			if (type.isInstance(child)) { return type.cast(child); }
			if (child instanceof Container)
			{
				T result = find((Container) child, type);
				if (result != null) { return result; }
			}
		}
		return null;
	}
}
