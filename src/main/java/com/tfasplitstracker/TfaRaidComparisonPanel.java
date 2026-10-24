package com.tfasplitstracker;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
	private static final Color MUTED = new Color(190, 201, 214);
	private static final Color ACCENT = new Color(99, 218, 204);
	private static final Font UI_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
	private static final Font SMALL_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
	private static final Font TIME_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 16);
	private static final Font DELTA_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 14);
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm:ss").withZone(ZoneId.systemDefault());
	private static final DateTimeFormatter COLUMN_DATE = DateTimeFormatter.ofPattern("MMM d HH:mm").withZone(ZoneId.systemDefault());
	private final Set<String> selected = new LinkedHashSet<>();
	private List<TfaRaid> raids = new ArrayList<>();
	private List<TfaRaid> filtered = new ArrayList<>();
	private final JTextField search = new JTextField();
	private final JComboBox<String> teamSize = new JComboBox<>(new String[]{"All team sizes", "Solo", "2 players", "3 players", "4 players", "5 players", "6 players", "7 players", "8 players"});
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
		heading.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
		heading.setForeground(TEXT);
		title.add(heading, BorderLayout.NORTH);
		count.setForeground(ACCENT);
		count.setFont(UI_FONT);
		title.add(count, BorderLayout.SOUTH);
		add(title, BorderLayout.NORTH);

		JPanel picker = new JPanel(new BorderLayout(0, 8));
		picker.setOpaque(false);
		search.putClientProperty("JTextField.placeholderText", "Find a date, player or raid status...");
		search.setName("comparisonRaidSearch");
		search.setToolTipText("Filter saved raids by date, party member, status or team size");
		search.setBackground(CARD);
		search.setForeground(TEXT);
		search.setCaretColor(ACCENT);
		search.setFont(UI_FONT);
		search.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(75, 88, 104)),
			BorderFactory.createEmptyBorder(6, 8, 6, 8)));
		JPanel filters = new JPanel(new BorderLayout(0, 6));
		filters.setOpaque(false);
		teamSize.setName("comparisonTeamSize");
		teamSize.setFont(UI_FONT);
		teamSize.setForeground(TEXT);
		teamSize.setBackground(CARD);
		teamSize.setToolTipText("Filter the saved raid list by team size; selected comparisons stay selected");
		teamSize.addActionListener(e -> filter());
		filters.add(teamSize, BorderLayout.NORTH);
		filters.add(search, BorderLayout.SOUTH);
		picker.add(filters, BorderLayout.NORTH);
		styleTable(selection);
		selection.setName("comparisonRaidList");
		selection.setRowHeight(54);
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
				text.setFont(SMALL_FONT);
				text.setBackground(selectedRow ? table.getSelectionBackground() : CARD);
				text.setForeground(TEXT);
				text.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
				text.setToolTipText(details(raid));
				return text;
			}
		});
		picker.add(scroll(selection), BorderLayout.CENTER);
		JLabel limit = new JLabel("Select up to 5 raids. Uncheck to swap.");
		limit.setForeground(MUTED);
		limit.setFont(SMALL_FONT);
		picker.add(limit, BorderLayout.SOUTH);
		picker.setMinimumSize(new Dimension(240, 180));

		JPanel results = new JPanel(new BorderLayout(0, 10));
		results.setOpaque(false);
		JPanel controls = new JPanel(new BorderLayout(8, 0));
		controls.setOpaque(false);
		JLabel baselineLabel = new JLabel("Compare against");
		baselineLabel.setForeground(TEXT);
		baselineLabel.setFont(UI_FONT);
		controls.add(baselineLabel, BorderLayout.WEST);
		baseline.setBackground(CARD);
		baseline.setForeground(TEXT);
		baseline.setFont(UI_FONT);
		controls.add(baseline, BorderLayout.CENTER);
		results.add(controls, BorderLayout.NORTH);
		styleTable(comparison);
		comparison.setRowHeight(48);
		comparison.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		comparison.setDefaultRenderer(Object.class, new TimingRenderer());
		comparison.getTableHeader().setReorderingAllowed(false);
		results.add(scroll(comparison), BorderLayout.CENTER);
		JLabel legend = new JLabel("Green = faster   |   Red = slower   |   — = missing   |   Hover for details");
		legend.setForeground(MUTED);
		legend.setFont(SMALL_FONT);
		results.add(legend, BorderLayout.SOUTH);
		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, picker, results);
		split.setBorder(BorderFactory.createEmptyBorder());
		split.setOpaque(false);
		split.setDividerLocation(330);
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

	@Override
	public void paint(Graphics graphics)
	{
		Graphics2D smooth = (Graphics2D) graphics.create();
		smooth.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		smooth.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		super.paint(smooth);
		smooth.dispose();
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
		int size = teamSize.getSelectedIndex();
		filtered = raids.stream().filter(r -> size == 0 || (r.teamSize > 0 ? r.teamSize : r.members.size()) == size)
			.filter(r -> details(r).toLowerCase(Locale.ROOT).contains(query)).collect(Collectors.toList());
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
		count.setText(chosen.size() + " / 5 raids selected" + (chosen.isEmpty() ? " — select raids from the list" : " — differences vs. baseline"));
		for (int i = 0; i < comparison.getColumnCount(); i++)
		{
			comparison.getColumnModel().getColumn(i).setPreferredWidth(i == 0 ? 175 : 235);
		}
		for (int row = 0; row < comparison.getRowCount(); row++)
		{
			comparison.setRowHeight(row, row < 3 ? 46 : comparisonModel.isStage(row) ? 78 : 64);
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
		table.setFont(UI_FONT);
		table.getTableHeader().setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
		table.getTableHeader().setPreferredSize(new Dimension(0, 34));
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
			for (int stage = 1; stage <= 5; stage++)
			{
				rows.add("Room " + stage);
			}
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

		boolean isStage(int row)
		{
			return rows.get(row).startsWith("Room ");
		}

		String boss(int row, int column)
		{
			if (!isStage(row) || column == 0)
			{
				return "";
			}
			TfaRaid.Split split = stageSplit(raids.get(column - 1), rows.get(row));
			return split == null ? "Not recorded" : TfaRoom.find(split.room).label;
		}

		private static TfaRaid.Split stageSplit(TfaRaid raid, String metric)
		{
			if (!metric.startsWith("Room "))
			{
				return null;
			}
			int stage = Integer.parseInt(metric.substring(5));
			return raid.splits.stream().filter(split ->
			{
				TfaRoom room = TfaRoom.find(split.room);
				return room != null && room.stage == stage;
			}).findFirst().orElse(null);
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
			TfaRaid.Split split = stageSplit(raid, metric);
			return split == null ? -1 : split.duration;
		}
	}

	private final class TimingRenderer extends JPanel implements javax.swing.table.TableCellRenderer
	{
		private final JLabel value = new JLabel();
		private final JLabel difference = new JLabel();
		private final JLabel boss = new JLabel();

		private TimingRenderer()
		{
			super(new BorderLayout(0, 3));
			setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
			value.putClientProperty("html.disable", Boolean.TRUE);
			value.setFont(TIME_FONT);
			difference.setFont(DELTA_FONT);
			boss.setFont(SMALL_FONT);
			boss.setForeground(MUTED);
			boss.putClientProperty("html.disable", Boolean.TRUE);
			add(boss, BorderLayout.NORTH);
			add(value, BorderLayout.CENTER);
			add(difference, BorderLayout.SOUTH);
		}

		public Component getTableCellRendererComponent(JTable table, Object text, boolean selectedRow, boolean focus, int row, int column)
		{
			setBackground(column > 0 && comparisonModel.isBaseline(column) ? new Color(36, 55, 59) : CARD);
			value.setText(String.valueOf(text));
			value.setFont(column > 0 && row >= 3 ? TIME_FONT : UI_FONT);
			boss.setText(comparisonModel.boss(row, column));
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
			setToolTipText(column == 0 ? String.valueOf(text) : details(comparisonModel.raids.get(column - 1)) + " | " + boss.getText() + " | " + text
				+ (difference.getText().isEmpty() ? "" : " | " + difference.getText()));
			setSize(table.getColumnModel().getColumn(column).getWidth(), table.getRowHeight(row));
			doLayout();
			return this;
		}
	}
}
