package com.tfasplitstracker;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

final class TfaRaidComparisonPanel extends JPanel
{
	private static final Color BACKGROUND = new Color(24, 28, 34);
	private static final Color CARD = new Color(34, 40, 48);
	private static final Color TEXT = new Color(229, 234, 240);
	private static final Color MUTED = new Color(150, 164, 180);
	private static final Color ACCENT = new Color(99, 218, 204);
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm:ss").withZone(ZoneId.systemDefault());
	private static final DateTimeFormatter COLUMN_DATE = DateTimeFormatter.ofPattern("MMM d HH:mm").withZone(ZoneId.systemDefault());
	private final Set<String> selected = new LinkedHashSet<>();
	private List<TfaRaid> raids = new ArrayList<>();
	private List<TfaRaid> filtered = new ArrayList<>();
	private final JTextField search = new JTextField();
	private final JLabel count = new JLabel();
	private final JComboBox<Choice> baseline = new JComboBox<>();
	private final SelectionModel selectionModel = new SelectionModel();
	private final JTable selection = new JTable(selectionModel);
	final ComparisonModel comparisonModel = new ComparisonModel();
	private final JTable comparison = new JTable(comparisonModel);
	private boolean updating;
	private boolean initialized;

	TfaRaidComparisonPanel(List<TfaRaid> savedRaids)
	{
		super(new BorderLayout(12, 12));
		setBackground(BACKGROUND);
		setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
		JPanel title = new JPanel(new BorderLayout(0, 6));
		title.setOpaque(false);
		JLabel heading = new JLabel("Compare Raids");
		heading.setFont(heading.getFont().deriveFont(Font.BOLD, 22f));
		heading.setForeground(TEXT);
		title.add(heading, BorderLayout.NORTH);
		count.setForeground(ACCENT);
		title.add(count, BorderLayout.SOUTH);
		add(title, BorderLayout.NORTH);

		JPanel picker = new JPanel(new BorderLayout(0, 8));
		picker.setOpaque(false);
		search.putClientProperty("JTextField.placeholderText", "Find a date, player or raid status...");
		search.setToolTipText("Filter saved raids by date, party member, status or team size");
		search.setBackground(CARD);
		search.setForeground(TEXT);
		search.setCaretColor(ACCENT);
		picker.add(search, BorderLayout.NORTH);
		styleTable(selection);
		selection.setRowHeight(46);
		selection.getColumnModel().getColumn(0).setMaxWidth(30);
		selection.getColumnModel().getColumn(0).setMinWidth(30);
		selection.setDefaultRenderer(String.class, new DefaultTableCellRenderer()
		{
			private final JTextArea text = new JTextArea();

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean selectedRow, boolean focus, int row, int column)
			{
				TfaRaid raid = filtered.get(row);
				int team = raid.teamSize > 0 ? raid.teamSize : raid.members.size();
				text.setText(DATE.format(Instant.ofEpochMilli(raid.started)) + "\n" + raid.status + " · " + team + " players · " + TfaRaid.time(raid.total));
				text.setFont(table.getFont().deriveFont(11f));
				text.setBackground(selectedRow ? table.getSelectionBackground() : CARD);
				text.setForeground(TEXT);
				text.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
				text.setToolTipText(details(raid));
				return text;
			}
		});
		picker.add(scroll(selection), BorderLayout.CENTER);
		JLabel limit = new JLabel("Select up to 5 raids. Uncheck one to swap it.");
		limit.setForeground(MUTED);
		limit.setFont(limit.getFont().deriveFont(11f));
		picker.add(limit, BorderLayout.SOUTH);
		picker.setMinimumSize(new Dimension(240, 180));

		JPanel results = new JPanel(new BorderLayout(0, 10));
		results.setOpaque(false);
		JPanel controls = new JPanel(new BorderLayout(8, 0));
		controls.setOpaque(false);
		JLabel baselineLabel = new JLabel("Compare against");
		baselineLabel.setForeground(TEXT);
		controls.add(baselineLabel, BorderLayout.WEST);
		baseline.setBackground(CARD);
		baseline.setForeground(TEXT);
		controls.add(baseline, BorderLayout.CENTER);
		results.add(controls, BorderLayout.NORTH);
		styleTable(comparison);
		comparison.setRowHeight(48);
		comparison.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		comparison.setDefaultRenderer(Object.class, new TimingRenderer());
		comparison.getTableHeader().setReorderingAllowed(false);
		results.add(scroll(comparison), BorderLayout.CENTER);
		JLabel legend = new JLabel("Green = faster   |   Red = slower   |   — = not recorded   |   Hover cells for details");
		legend.setForeground(MUTED);
		legend.setFont(legend.getFont().deriveFont(11f));
		results.add(legend, BorderLayout.SOUTH);
		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, picker, results);
		split.setBorder(BorderFactory.createEmptyBorder());
		split.setOpaque(false);
		split.setDividerLocation(290);
		split.setResizeWeight(0);
		add(split, BorderLayout.CENTER);
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			public void insertUpdate(DocumentEvent event) { filter(); }
			public void removeUpdate(DocumentEvent event) { filter(); }
			public void changedUpdate(DocumentEvent event) { filter(); }
		});
		baseline.addActionListener(e ->
		{
			if (!updating)
			{
				Choice choice = (Choice) baseline.getSelectedItem();
				comparisonModel.baselineId = choice == null ? null : choice.raid.id;
				comparisonModel.fireTableDataChanged();
			}
		});
		setRaids(savedRaids);
	}

	void setRaids(List<TfaRaid> savedRaids)
	{
		raids = savedRaids.stream().filter(r -> !r.active()).map(TfaRaid::copy)
			.sorted(Comparator.comparingLong((TfaRaid r) -> r.started).reversed()).collect(Collectors.toList());
		Set<String> available = raids.stream().map(r -> r.id).collect(Collectors.toSet());
		selected.retainAll(available);
		if (!initialized)
		{
			raids.stream().limit(2).forEach(r -> selected.add(r.id));
			initialized = true;
		}
		filter();
		rebuildComparison();
	}

	boolean selectRaid(String id, boolean checked)
	{
		if (checked)
		{
			if (selected.size() >= 5 || raids.stream().noneMatch(r -> r.id.equals(id)))
			{
				return false;
			}
			selected.add(id);
		}
		else
		{
			selected.remove(id);
		}
		selectionModel.fireTableDataChanged();
		rebuildComparison();
		return true;
	}

	void setBaseline(String id)
	{
		for (int i = 0; i < baseline.getItemCount(); i++)
		{
			if (baseline.getItemAt(i).raid.id.equals(id))
			{
				baseline.setSelectedIndex(i);
				return;
			}
		}
	}

	private void filter()
	{
		String query = search.getText().trim().toLowerCase(Locale.ROOT);
		filtered = raids.stream().filter(r -> details(r).toLowerCase(Locale.ROOT).contains(query)).collect(Collectors.toList());
		selectionModel.fireTableDataChanged();
	}

	private void rebuildComparison()
	{
		List<TfaRaid> chosen = new ArrayList<>();
		for (String id : selected)
		{
			raids.stream().filter(r -> r.id.equals(id)).findFirst().ifPresent(chosen::add);
		}
		String previous = comparisonModel.baselineId;
		updating = true;
		baseline.removeAllItems();
		for (int i = 0; i < chosen.size(); i++)
		{
			baseline.addItem(new Choice(chosen.get(i), i + 1));
		}
		baseline.setEnabled(!chosen.isEmpty());
		setBaseline(previous == null ? "" : previous);
		Choice choice = (Choice) baseline.getSelectedItem();
		comparisonModel.setRaids(chosen, choice == null ? null : choice.raid.id);
		updating = false;
		count.setText(chosen.size() + " / 5 raids selected" + (chosen.isEmpty() ? " — select raids from the list" : " — differences are relative to the selected baseline"));
		for (int i = 0; i < comparison.getColumnCount(); i++)
		{
			comparison.getColumnModel().getColumn(i).setPreferredWidth(i == 0 ? 175 : 205);
		}
	}

	private static String details(TfaRaid raid)
	{
		int team = raid.teamSize > 0 ? raid.teamSize : raid.members.size();
		return DATE.format(Instant.ofEpochMilli(raid.started)) + " | " + raid.status + " | " + team
			+ " players | " + TfaRaid.time(raid.total) + " | " + String.join(", ", raid.members);
	}

	private static void styleTable(JTable table)
	{
		table.setBackground(CARD);
		table.setForeground(TEXT);
		table.setGridColor(new Color(50, 58, 69));
		table.setSelectionBackground(new Color(48, 76, 79));
		table.setSelectionForeground(TEXT);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setFillsViewportHeight(true);
	}

	private static JScrollPane scroll(JTable table)
	{
		JScrollPane pane = new JScrollPane(table);
		pane.getViewport().setBackground(CARD);
		pane.setColumnHeaderView(table.getTableHeader());
		return pane;
	}

	private static final class Choice
	{
		private final TfaRaid raid;
		private final int number;

		private Choice(TfaRaid raid, int number)
		{
			this.raid = raid;
			this.number = number;
		}

		@Override
		public String toString()
		{
			return "Raid " + number + " · " + DATE.format(Instant.ofEpochMilli(raid.started));
		}
	}

	private final class SelectionModel extends AbstractTableModel
	{
		public int getRowCount() { return filtered.size(); }
		public int getColumnCount() { return 2; }
		public String getColumnName(int column) { return column == 0 ? "" : "Saved raids"; }
		public Class<?> getColumnClass(int column) { return column == 0 ? Boolean.class : String.class; }

		public Object getValueAt(int row, int column)
		{
			TfaRaid raid = filtered.get(row);
			return column == 0 ? selected.contains(raid.id) : details(raid);
		}

		public boolean isCellEditable(int row, int column)
		{
			return column == 0 && (selected.size() < 5 || selected.contains(filtered.get(row).id));
		}

		public void setValueAt(Object value, int row, int column)
		{
			if (column == 0 && value instanceof Boolean)
			{
				selectRaid(filtered.get(row).id, (Boolean) value);
			}
		}
	}

	static final class ComparisonModel extends AbstractTableModel
	{
		private List<TfaRaid> raids = new ArrayList<>();
		private final List<String> rows = new ArrayList<>();
		private String baselineId;

		void setRaids(List<TfaRaid> selected, String baseline)
		{
			raids = new ArrayList<>(selected);
			baselineId = baseline;
			rows.clear();
			rows.add("Status");
			rows.add("Team size");
			rows.add("Party");
			rows.add("Total raid time");
			Set<String> rooms = new LinkedHashSet<>();
			for (TfaRaid raid : raids)
			{
				raid.splits.forEach(s -> rooms.add(s.room));
			}
			rows.addAll(rooms);
			rows.add("Combat time");
			rows.add("Between rooms");
			fireTableStructureChanged();
		}

		public int getRowCount() { return rows.size(); }
		public int getColumnCount() { return raids.size() + 1; }
		public String getColumnName(int column)
		{
			return column == 0 ? "Room / metric" : "Raid " + column + " · " + COLUMN_DATE.format(Instant.ofEpochMilli(raids.get(column - 1).started));
		}

		public Object getValueAt(int row, int column)
		{
			if (column == 0)
			{
				return rows.get(row);
			}
			TfaRaid raid = raids.get(column - 1);
			if (row == 0) { return raid.status; }
			if (row == 1) { return Integer.toString(raid.teamSize > 0 ? raid.teamSize : raid.members.size()); }
			if (row == 2) { return raid.members.isEmpty() ? "Roster not captured" : String.join(", ", raid.members); }
			long value = time(raid, rows.get(row));
			return value < 0 ? "—" : TfaRaid.time(value);
		}

		Long delta(int row, int column)
		{
			if (row < 3 || column == 0)
			{
				return null;
			}
			TfaRaid baseline = raids.stream().filter(r -> r.id.equals(baselineId)).findFirst().orElse(null);
			long value = time(raids.get(column - 1), rows.get(row));
			long reference = baseline == null ? -1 : time(baseline, rows.get(row));
			return value < 0 || reference < 0 ? null : value - reference;
		}

		boolean isBaseline(int column)
		{
			return column > 0 && raids.get(column - 1).id.equals(baselineId);
		}

		private static long time(TfaRaid raid, String metric)
		{
			if (metric.equals("Total raid time"))
			{
				return "Completed".equals(raid.status) ? raid.total : -1;
			}
			if (metric.equals("Combat time"))
			{
				return raid.completeSplits() ? raid.combatTime() : -1;
			}
			if (metric.equals("Between rooms"))
			{
				return raid.completeSplits() && raid.total >= raid.combatTime() ? raid.total - raid.combatTime() : -1;
			}
			return raid.splits.stream().filter(s -> s.room.equals(metric)).mapToLong(s -> s.duration).findFirst().orElse(-1);
		}
	}

	private final class TimingRenderer extends JPanel implements javax.swing.table.TableCellRenderer
	{
		private final JLabel value = new JLabel();
		private final JLabel difference = new JLabel();

		private TimingRenderer()
		{
			super(new BorderLayout(0, 3));
			setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
			value.putClientProperty("html.disable", Boolean.TRUE);
			difference.setFont(difference.getFont().deriveFont(11f));
			add(value, BorderLayout.CENTER);
			add(difference, BorderLayout.SOUTH);
		}

		public Component getTableCellRendererComponent(JTable table, Object text, boolean selectedRow, boolean focus, int row, int column)
		{
			setBackground(column > 0 && comparisonModel.isBaseline(column) ? new Color(36, 55, 59) : CARD);
			value.setText(String.valueOf(text));
			value.setForeground(TEXT);
			difference.setText("");
			difference.setForeground(MUTED);
			Long delta = comparisonModel.delta(row, column);
			if (row >= 3 && column > 0 && !"—".equals(text))
			{
				if (comparisonModel.isBaseline(column))
				{
					difference.setText("Baseline");
				}
				else if (delta == null)
				{
					difference.setText("No baseline time");
				}
				else
				{
					difference.setText((delta < 0 ? "−" : delta > 0 ? "+" : "±") + TfaRaid.time(Math.abs(delta)));
					difference.setForeground(delta < 0 ? ACCENT : delta > 0 ? new Color(245, 132, 132) : MUTED);
				}
			}
			setToolTipText(column == 0 ? String.valueOf(text) : details(comparisonModel.raids.get(column - 1)) + " | " + text
				+ (difference.getText().isEmpty() ? "" : " | " + difference.getText()));
			setSize(table.getColumnModel().getColumn(column).getWidth(), table.getRowHeight(row));
			doLayout();
			return this;
		}
	}
}
