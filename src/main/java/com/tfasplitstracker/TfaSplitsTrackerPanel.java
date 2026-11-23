package com.tfasplitstracker;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Dialog;
import java.awt.Font;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.PluginPanel;

final class TfaSplitsTrackerPanel extends PluginPanel
{
	private static final Color BACKGROUND = new Color(30, 30, 30);
	private static final Color CARD = new Color(39, 39, 39);
	private static final Color BORDER = new Color(57, 57, 57);
	private static final Color TEXT = new Color(225, 225, 225);
	private static final Color MUTED = new Color(165, 165, 165);
	private static final Color GREEN = new Color(139, 190, 153);
	private static final Color RED = new Color(213, 145, 140);
	private static final Color GOLD = new Color(207, 184, 129);
	private static final Color BLUE = new Color(151, 178, 207);
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault());
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.ENGLISH);
	private final JPanel live = column();
	private final JPanel history = column();
	private final JLabel count = label("0", 13, TEXT);
	private final JLabel best = label("--:--", 13, TEXT);
	private final JLabel total = label("--:--", 26, TEXT);
	private final JLabel roomClock = label("", 11, MUTED);
	private final JLabel notice = label("", 11, TEXT);
	private final JComboBox<String> sizes = new JComboBox<>(new String[]{"All team sizes", "Solo", "2 players", "3 players", "4 players", "5 players", "6 players", "7 players", "8 players"});
	private final JTextField search = new JTextField();
	private final JCheckBox hideUnfinished = new JCheckBox("Hide Failed/Abandoned Raids");
	private final Set<String> expanded = new HashSet<>();
	private final Set<LocalDate> collapsedDays = new HashSet<>();
	private TfaTracker data = new TfaTracker();
	private TfaLiveState liveState = TfaLiveState.UNAVAILABLE;
	private final BiConsumer<String, String> delete;
	private final JButton undo = new JButton("Undo last deletion");
	private final JButton compare = new JButton("Compare Raids");
	private JDialog comparisonWindow;
	private TfaRaidComparisonPanel comparisonPanel;

	TfaSplitsTrackerPanel()
	{
		this((id, room) ->
		{
		}, () ->
		{
		});
	}

	TfaSplitsTrackerPanel(BiConsumer<String, String> delete, Runnable undoDelete)
	{
		this.delete = delete;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(BACKGROUND);
		setBorder(BorderFactory.createEmptyBorder(12, 8, 12, 8));
		JPanel header = column();
		header.add(label("TFA Splits Tracker", 17, TEXT));
		header.add(Box.createVerticalStrut(3));
		header.add(label("The Fractured Archive", 11, MUTED));
		header.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER), BorderFactory.createEmptyBorder(0, 0, 10, 0)));
		count.setForeground(GREEN);
		best.setForeground(GOLD);
		total.setForeground(BLUE);
		add(header);
		add(Box.createVerticalStrut(12));
		JPanel stats = column();
		stats.add(stat("Completed raids", count));
		stats.add(Box.createVerticalStrut(6));
		stats.add(stat("Best raid", best));
		add(stats);
		add(Box.createVerticalStrut(12));
		add(live);
		add(Box.createVerticalStrut(14));
		add(label("Raid history", 12, TEXT));
		compare.setAlignmentX(LEFT_ALIGNMENT);
		compare.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		compare.setBackground(CARD);
		compare.setForeground(TEXT);
		compare.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		compare.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),
			BorderFactory.createEmptyBorder(6, 8, 6, 8)));
		compare.setFocusPainted(false);
		compare.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		compare.addActionListener(e -> openComparison());
		add(Box.createVerticalStrut(7));
		add(compare);
		undo.setForeground(MUTED);
		undo.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
		undo.setAlignmentX(LEFT_ALIGNMENT);
		undo.setContentAreaFilled(false);
		undo.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
		undo.addActionListener(e -> undoDelete.run());
		add(undo);
		add(Box.createVerticalStrut(7));
		sizes.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		sizes.setAlignmentX(LEFT_ALIGNMENT);
		sizes.setBackground(CARD);
		sizes.setForeground(TEXT);
		sizes.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		sizes.addActionListener(e -> rebuildHistory());
		add(sizes);
		add(Box.createVerticalStrut(7));
		hideUnfinished.setAlignmentX(LEFT_ALIGNMENT);
		hideUnfinished.setOpaque(false);
		hideUnfinished.setForeground(TEXT);
		hideUnfinished.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
		hideUnfinished.setToolTipText("Show only completed raids; also hides left and interrupted attempts. Saved raids are kept.");
		hideUnfinished.addActionListener(e -> rebuildHistory());
		add(hideUnfinished);
		add(Box.createVerticalStrut(7));
		add(label("Search player, status or time", 10, MUTED));
		add(Box.createVerticalStrut(4));
		search.setToolTipText("Filter by party member or raid status. " + TfaRaidSearch.TIME_HELP);
		search.setAlignmentX(LEFT_ALIGNMENT);
		search.setBackground(CARD);
		search.setForeground(TEXT);
		search.setCaretColor(TEXT);
		search.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		search.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),
			BorderFactory.createEmptyBorder(4, 7, 4, 7)));
		search.putClientProperty("JTextField.placeholderText", "Player, status or 12:34...");
		search.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		search.setPreferredSize(new Dimension(190, 28));
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			public void insertUpdate(DocumentEvent e)
			{
				rebuildHistory();
			}

			public void removeUpdate(DocumentEvent e)
			{
				rebuildHistory();
			}

			public void changedUpdate(DocumentEvent e)
			{
				rebuildHistory();
			}
		});
		add(search);
		add(Box.createVerticalStrut(8));
		add(history);
		add(notice);
		update(data, true, false);
	}

	void stop()
	{
		Runnable close = () ->
		{
			if (comparisonWindow != null)
			{
				comparisonWindow.dispose();
				comparisonWindow = null;
				comparisonPanel = null;
			}
		};
		if (SwingUtilities.isEventDispatchThread())
		{
			close.run();
		}
		else
		{
			SwingUtilities.invokeLater(close);
		}
	}

	void update(TfaTracker snapshot, boolean storageHealthy, boolean available)
	{
		update(snapshot, storageHealthy, new TfaLiveState(available, "", -1));
	}

	void update(TfaTracker snapshot, boolean storageHealthy, TfaLiveState state)
	{
		data = snapshot;
		liveState = state;
		undo.setVisible(data.deleted != null);
		compare.setEnabled(data.raids.stream().anyMatch(r -> !r.active()));
		if (comparisonWindow != null && comparisonWindow.isDisplayable())
		{
			comparisonPanel.setRaids(data.raids);
		}
		notice.setText(storageHealthy ? "" : "History could not be loaded/saved.");
		long completed = data.raids.stream().filter(r -> "Completed".equals(r.status)).count();
		count.setText(Long.toString(completed));
		long pb = data.raids.stream().filter(r -> "Completed".equals(r.status) && r.completeSplits())
			.mapToLong(r -> r.total).filter(t -> t >= 0).min().orElse(-1);
		best.setText(TfaRaid.time(pb));
		best.setToolTipText("Best total among fully recorded raids; history can be filtered by team size below");
		rebuildLive();
		rebuildHistory();
	}

	void updateLiveState(TfaLiveState state)
	{
		boolean rebuild = liveState.inRaid != state.inRaid || !liveState.status.equals(state.status);
		liveState = state;
		if (rebuild) { rebuildLive(); }
		else { updateClock(); }
	}

	private void openComparison()
	{
		if (comparisonWindow == null || !comparisonWindow.isDisplayable())
		{
			comparisonPanel = new TfaRaidComparisonPanel(data.raids);
			comparisonWindow = new JDialog(SwingUtilities.getWindowAncestor(this), "TFA Splits Tracker — Compare Raids", Dialog.ModalityType.MODELESS);
			comparisonWindow.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
			comparisonWindow.setContentPane(comparisonPanel);
			comparisonWindow.setMinimumSize(new Dimension(900, 450));
			comparisonWindow.setSize(1200, 760);
			comparisonWindow.setLocationRelativeTo(SwingUtilities.getWindowAncestor(this));
		}
		comparisonWindow.setVisible(true);
		comparisonWindow.toFront();
	}

	private void rebuildLive()
	{
		live.removeAll();
		JPanel card = card();
		TfaRaid raid = liveState.inRaid ? data.current() : null;
		accent(card, raid == null ? BORDER : BLUE);
		card.add(label(raid == null ? "RAID STATUS" : "LIVE RAID", 10, raid == null ? MUTED : BLUE));
		card.add(Box.createVerticalStrut(6));
		if (raid == null)
		{
			card.add(label(liveState.status.isEmpty() ? "Waiting for challenge start" : liveState.status, 12, TEXT));
			card.add(Box.createVerticalStrut(5));
			card.add(label(data.raids.isEmpty() || data.raids.get(0).active() ? "Raids are recorded automatically."
				: "Last raid: " + data.raids.get(0).status, 10, MUTED));
		}
		else
		{
			card.add(total);
			card.add(roomClock);
			card.add(Box.createVerticalStrut(7));
			int completedRooms = (int) raid.splits.stream().filter(s -> s.duration >= 0).count();
			JProgressBar progress = new JProgressBar(0, 5);
			progress.setValue(completedRooms);
			progress.setForeground(GREEN);
			progress.setBackground(BORDER);
			progress.setBorderPainted(false);
			progress.setAlignmentX(LEFT_ALIGNMENT);
			progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 4));
			progress.setToolTipText(completedRooms + " of 5 rooms recorded");
			card.add(progress);
			card.add(Box.createVerticalStrut(7));
			card.add(label(TfaPersonalBest.status(raid), 10, raid.personalBest == null ? MUTED : GOLD));
			card.add(Box.createVerticalStrut(10));
			addSplits(card, raid);
			addParty(card, raid);
			JPopupMenu menu = new JPopupMenu();
			JMenuItem reset = new JMenuItem("Reset current tracker");
			reset.setToolTipText("Discard this run and wait for the next Archive Guardian start");
			reset.addActionListener(e -> delete.accept(raid.id, null));
			menu.add(reset);
			attachPopup(card, menu);
		}
		live.add(card);
		updateClock();
		live.revalidate();
		live.repaint();
	}

	private void updateClock()
	{
		TfaRaid raid = data.current();
		if (raid == null)
		{
			return;
		}
		if (!liveState.inRaid)
		{
			total.setText(TfaRaid.time(raid.total));
			roomClock.setText("Waiting for raid data");
			return;
		}
		total.setText(TfaRaid.time(liveState.elapsed >= 0 ? liveState.elapsed : raid.total));
		total.setToolTipText(liveState.elapsed >= 0 ? "Current raid time from the game's HUD"
			: "Last recorded raid split; live HUD clock unavailable");
		TfaRaid.Split last = raid.splits.isEmpty() ? null : raid.splits.get(raid.splits.size() - 1);
		roomClock.setText(last != null && last.duration < 0
			? shortRoom(last.room) + "  /  In progress"
			: liveState.status.isEmpty() ? "Between rooms" : liveState.status);
	}

	private void rebuildHistory()
	{
		history.removeAll();
		String query = search.getText().trim().toLowerCase(Locale.ROOT);
		int selectedSize = sizes.getSelectedIndex();
		Map<LocalDate, List<TfaRaid>> days = data.raids.stream()
			.filter(raid -> !raid.active())
			.filter(raid -> !hideUnfinished.isSelected() || "Completed".equals(raid.status))
			.filter(raid -> selectedSize == 0 || raid.displayTeamSize() == selectedSize)
			.filter(raid -> TfaRaidSearch.matches(raid, query, raid.status + " " + String.join(" ", raid.members)))
			.sorted(Comparator.comparingLong((TfaRaid raid) -> raid.started).reversed())
			.collect(Collectors.groupingBy(raid -> Instant.ofEpochMilli(raid.started).atZone(ZoneId.systemDefault()).toLocalDate(),
				() -> new TreeMap<LocalDate, List<TfaRaid>>(Comparator.reverseOrder()), Collectors.toList()));
		for (Map.Entry<LocalDate, List<TfaRaid>> entry : days.entrySet())
		{
			LocalDate day = entry.getKey();
			List<TfaRaid> raids = entry.getValue();
			boolean collapsed = collapsedDays.contains(day);
			JPanel heading = new JPanel(new BorderLayout(4, 0));
			heading.setOpaque(false);
			heading.setAlignmentX(LEFT_ALIGNMENT);
			heading.setBorder(BorderFactory.createEmptyBorder(6, 0, 7, 0));
			JButton toggle = new JButton((collapsed ? "\u25b8 " : "\u25be ") + DAY.format(day));
			toggle.setName("day-" + day);
			toggle.setHorizontalAlignment(SwingConstants.LEFT);
			toggle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
			toggle.setForeground(TEXT);
			toggle.setBorder(BorderFactory.createEmptyBorder());
			toggle.setContentAreaFilled(false);
			toggle.setFocusPainted(false);
			toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			toggle.addActionListener(e ->
			{
				if (!collapsedDays.remove(day))
				{
					collapsedDays.add(day);
				}
				rebuildHistory();
			});
			heading.add(toggle, BorderLayout.CENTER);
			heading.add(label(raids.size() + (raids.size() == 1 ? " raid" : " raids"), 11, TEXT), BorderLayout.EAST);
			long completed = raids.stream().filter(r -> "Completed".equals(r.status)).count();
			heading.setToolTipText(completed + " completed / " + (raids.size() - completed)
				+ " other attempts. Saved raids by local start date; counts reflect the current filters.");
			history.add(heading);
			if (collapsed)
			{
				continue;
			}
			for (TfaRaid raid : raids)
			{
				history.add(historyCard(raid));
				history.add(Box.createVerticalStrut(7));
			}
		}
		if (days.isEmpty())
		{
			history.add(label(data.raids.isEmpty() ? "Your raid history will appear here." : "No matching raids.", 11, MUTED));
		}
		history.revalidate();
		history.repaint();
	}

	private JPanel historyCard(TfaRaid raid)
	{
		int team = raid.displayTeamSize();
		JPanel card = card();
		boolean open = expanded.contains(raid.id);
		JButton toggle = new JButton((open ? "\u25be " : "\u25b8 ") + DATE.format(Instant.ofEpochMilli(raid.started)));
		toggle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
		toggle.setForeground(TEXT);
		toggle.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
		toggle.setContentAreaFilled(false);
		toggle.setFocusPainted(false);
		toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		toggle.setAlignmentX(LEFT_ALIGNMENT);
		toggle.addActionListener(e ->
		{
			if (!expanded.remove(raid.id))
			{
				expanded.add(raid.id);
			}
			rebuildHistory();
		});
		card.add(toggle);
		boolean completed = "Completed".equals(raid.status);
		Color statusColor = completed ? GREEN : "Failed".equals(raid.status) ? RED : MUTED;
		accent(card, statusColor);
		card.add(row(raid.status, TfaRaid.time(raid.total), statusColor, 15));
		card.add(Box.createVerticalStrut(4));
		card.add(label((team == 0 ? "Unknown team" : team + (team == 1 ? " player" : " players"))
			+ "  /  " + raid.splits.stream().filter(s -> s.duration >= 0).count() + " rooms", 10, MUTED));
		if (completed && !raid.completeSplits())
		{
			card.add(label("Partial room history", 10, MUTED));
		}
		if (open)
		{
			card.add(Box.createVerticalStrut(10));
			addSplits(card, raid);
			card.add(Box.createVerticalStrut(7));
			card.add(row("Combat", TfaRaid.time(raid.combatTime()), MUTED, 11));
			if (raid.completeSplits() && raid.total >= raid.combatTime())
			{
				card.add(row("Between rooms", TfaRaid.time(raid.total - raid.combatTime()), MUTED, 11));
			}
			addParty(card, raid);
		}
		JPopupMenu menu = new JPopupMenu();
		JMenuItem remove = new JMenuItem("Delete raid");
		remove.addActionListener(e -> delete.accept(raid.id, null));
		menu.add(remove);
		attachPopup(card, menu);
		return card;
	}

	private void addSplits(JPanel card, TfaRaid raid)
	{
		card.add(row("Room", "Time", MUTED, 10));
		for (TfaRaid.Split split : raid.splits)
		{
			card.add(Box.createVerticalStrut(7));
			JPanel line = row(shortRoom(split.room), TfaRaid.time(split.duration), split.duration < 0 ? MUTED : TEXT, 11);
			line.setToolTipText(split.room + " | Cumulative: " + TfaRaid.time(split.cumulative));
			if (!raid.active())
			{
				JPopupMenu menu = new JPopupMenu();
				JMenuItem remove = new JMenuItem("Delete room time");
				remove.addActionListener(e -> delete.accept(raid.id, split.room));
				menu.add(remove);
				JMenuItem removeRaid = new JMenuItem("Delete raid");
				removeRaid.addActionListener(e -> delete.accept(raid.id, null));
				menu.add(removeRaid);
				attachPopup(line, menu);
			}
			card.add(line);
			addPhases(card, raid, split);
			if (raid.personalBest != null && split.duration >= 0)
			{
				TfaRaid.Split reference = raid.personalBest.stage(split);
				if (reference != null && split.cumulative >= 0)
				{
					if (reference.room.equals(split.room))
					{
						long roomDelta = split.duration - reference.duration;
						card.add(row("Room vs PB", TfaPersonalBest.delta(roomDelta),
							roomDelta == 0 ? MUTED : roomDelta < 0 ? GREEN : RED, 10));
					}
					Color deltaColor = split.cumulative == reference.cumulative ? MUTED
						: split.cumulative < reference.cumulative ? GREEN : RED;
					JPanel delta = row("Raid vs PB", TfaPersonalBest.delta(split.cumulative - reference.cumulative), deltaColor, 10);
					delta.setToolTipText("Against the same stage of your PB run: " + raid.personalBest.comparison(split));
					card.add(delta);
					if (!reference.room.equals(split.room)) { card.add(label("PB stage used " + shortRoom(reference.room), 10, MUTED)); }
				}
			}
		}
	}

	private static void attachPopup(JComponent component, JPopupMenu menu)
	{
		if (component.getComponentPopupMenu() == null)
		{
			component.setComponentPopupMenu(menu);
		}
		for (Component child : component.getComponents())
		{
			if (child instanceof JComponent)
			{
				attachPopup((JComponent) child, component.getComponentPopupMenu());
			}
		}
	}

	private static void addPhases(JPanel card, TfaRaid raid, TfaRaid.Split split)
	{
		for (TfaRaid.Phase phase : split.phases)
		{
			boolean active = raid.active() && split.duration < 0 && phase == split.phases.get(split.phases.size() - 1);
			JPanel line = row(TfaOcellus.label(phase.number), phase.duration >= 0 ? TfaRaid.time(phase.duration)
				: active ? "In progress" : "Unrecorded", MUTED, 10);
			line.setBorder(BorderFactory.createEmptyBorder(3, 6, 0, 0));
			line.setToolTipText("Game-tick phase timing. Ocellus split: " + TfaRaid.time(phase.cumulative));
			card.add(line);
			TfaRaid.Phase reference = raid.personalBest == null ? null : raid.personalBest.phase(split, phase.number);
			if (reference != null && phase.duration >= 0)
			{
				long delta = phase.duration - reference.duration;
				JPanel comparison = row("Phase vs PB", TfaPersonalBest.delta(delta), delta < 0 ? GREEN : delta > 0 ? RED : MUTED, 10);
				comparison.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));
				card.add(comparison);
			}
		}
	}

	private static void addParty(JPanel card, TfaRaid raid)
	{
		card.add(Box.createVerticalStrut(12));
		card.add(label("Party", 10, MUTED));
		card.add(Box.createVerticalStrut(5));
		if (raid.members.isEmpty())
		{
			card.add(label("Roster not captured", 11, MUTED));
		}
		for (String member : raid.members)
		{
			JLabel name = label(member, 11, TEXT);
			name.setToolTipText(member);
			card.add(name);
		}
	}

	private static String shortRoom(String name)
	{
		return name.equals("Dreadnoth the Jellied") ? "Dreadnoth" : name;
	}

	private static JPanel stat(String title, JLabel value)
	{
		JPanel panel = new JPanel(new BorderLayout(8, 0));
		panel.setOpaque(false);
		panel.setAlignmentX(LEFT_ALIGNMENT);
		panel.add(label(title, 11, MUTED), BorderLayout.CENTER);
		panel.add(value, BorderLayout.EAST);
		return panel;
	}

	private static JPanel row(String name, String value, Color color, int size)
	{
		JPanel row = new JPanel(new BorderLayout(5, 0));
		row.setOpaque(false);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.add(label(name, Math.min(size, 12), color), BorderLayout.CENTER);
		JLabel right = label(value, size, color);
		right.setHorizontalAlignment(SwingConstants.RIGHT);
		row.add(right, BorderLayout.EAST);
		return row;
	}

	private static JLabel label(String text, int size, Color color)
	{
		JLabel label = new JLabel(text);
		// Player names are plain text, including names beginning with an HTML-like prefix.
		label.putClientProperty("html.disable", Boolean.TRUE);
		label.setFont(new Font(Font.SANS_SERIF, size >= 17 ? Font.BOLD : Font.PLAIN, size));
		label.setForeground(color);
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private static JPanel column()
	{
		JPanel panel = new JPanel();
		panel.setOpaque(false);
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setAlignmentX(LEFT_ALIGNMENT);
		return panel;
	}

	private static JPanel card()
	{
		JPanel panel = new JPanel();
		panel.setBackground(CARD);
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setAlignmentX(LEFT_ALIGNMENT);
		panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),
			BorderFactory.createEmptyBorder(9, 9, 9, 9)));
		return panel;
	}

	private static void accent(JPanel card, Color color)
	{
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 3, 1, 1, color), BorderFactory.createEmptyBorder(9, 7, 9, 9)));
	}
}
