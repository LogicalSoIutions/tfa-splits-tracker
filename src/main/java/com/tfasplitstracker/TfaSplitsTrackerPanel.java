package com.tfasplitstracker;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
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
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.PluginPanel;

final class TfaSplitsTrackerPanel extends PluginPanel
{
	private static final Color BACKGROUND = new Color(24, 28, 34);
	private static final Color CARD = new Color(34, 40, 48);
	private static final Color ACCENT = new Color(99, 218, 204);
	private static final Color GOLD = new Color(236, 194, 119);
	private static final Color TEXT = new Color(229, 234, 240);
	private static final Color MUTED = new Color(150, 164, 180);
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault());
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.ENGLISH);
	private final JPanel live = column();
	private final JPanel history = column();
	private final JLabel count = label("0", 19, TEXT);
	private final JLabel best = label("--:--", 14, GOLD);
	private final JLabel total = label("--:--", 30, ACCENT);
	private final JLabel roomClock = label("", 11, MUTED);
	private final JLabel notice = label("", 11, GOLD);
	private final JComboBox<String> sizes = new JComboBox<>(new String[]{"All team sizes", "Solo", "2 players", "3 players", "4 players", "5 players", "6 players", "7 players", "8 players"});
	private final JTextField search = new JTextField();
	private final Set<String> expanded = new HashSet<>();
	private final Set<LocalDate> collapsedDays = new HashSet<>();
	private TfaTracker data = new TfaTracker();
	private boolean liveAvailable;
	private final Timer clock;
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
		setBorder(BorderFactory.createEmptyBorder(14, 8, 12, 8));
		JPanel header = column();
		header.add(label("THE FRACTURED ARCHIVE", 10, MUTED));
		header.add(label("TFA Splits Tracker", 19, TEXT));
		add(header);
		add(Box.createVerticalStrut(14));
		JPanel stats = new JPanel(new GridLayout(1, 2, 8, 0));
		stats.setOpaque(false);
		stats.setAlignmentX(LEFT_ALIGNMENT);
		stats.add(stat("COMPLETED", count));
		stats.add(stat("BEST TOTAL", best));
		add(stats);
		add(Box.createVerticalStrut(12));
		add(live);
		add(Box.createVerticalStrut(14));
		add(label("RAID HISTORY", 11, MUTED));
		compare.setAlignmentX(LEFT_ALIGNMENT);
		compare.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		compare.setBackground(CARD);
		compare.setForeground(ACCENT);
		compare.addActionListener(e -> openComparison());
		add(Box.createVerticalStrut(7));
		add(compare);
		undo.setForeground(ACCENT);
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
		add(Box.createVerticalStrut(5));
		search.setToolTipText("Filter by party member or raid status");
		search.setAlignmentX(LEFT_ALIGNMENT);
		search.setBackground(CARD);
		search.setForeground(TEXT);
		search.setCaretColor(ACCENT);
		search.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(57, 67, 80)),
			BorderFactory.createEmptyBorder(4, 7, 4, 7)));
		search.putClientProperty("JTextField.placeholderText", "Find a party member...");
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
		clock = new Timer(600, e -> updateClock());
		clock.start();
		update(data, true, false);
	}

	void stop()
	{
		Runnable close = () ->
		{
			clock.stop();
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
		data = snapshot;
		liveAvailable = available;
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
		TfaRaid raid = data.current();
		card.add(label(raid == null ? "READY FOR YOUR NEXT RUN" : "LIVE RAID", 10, ACCENT));
		card.add(Box.createVerticalStrut(6));
		if (raid == null)
		{
			card.add(label("Waiting for challenge start", 12, TEXT));
			card.add(Box.createVerticalStrut(5));
			card.add(label("Raids are recorded automatically.", 10, MUTED));
		}
		else
		{
			card.add(total);
			card.add(roomClock);
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
		if (!liveAvailable)
		{
			total.setText(TfaRaid.time(raid.total));
			roomClock.setText("Waiting for raid data");
			return;
		}
		long now = System.currentTimeMillis();
		long elapsed = raid.total < 0 ? now - raid.started : raid.total + Math.max(0, now - raid.updated);
		total.setText(TfaRaid.time(Math.max(0, elapsed)));
		total.setToolTipText("Live estimate; replaced with the exact server total on each room completion");
		TfaRaid.Split last = raid.splits.isEmpty() ? null : raid.splits.get(raid.splits.size() - 1);
		roomClock.setText(last != null && last.duration < 0
			? shortRoom(last.room) + "  ~" + TfaRaid.time(Math.max(0, now - last.started)) : "Between rooms  /  live estimate");
	}

	private void rebuildHistory()
	{
		history.removeAll();
		String query = search.getText().trim().toLowerCase(Locale.ROOT);
		int selectedSize = sizes.getSelectedIndex();
		Map<LocalDate, List<TfaRaid>> days = data.raids.stream()
			.filter(raid -> !raid.active())
			.filter(raid -> selectedSize == 0 || (raid.teamSize > 0 ? raid.teamSize : raid.members.size()) == selectedSize)
			.filter(raid -> query.isEmpty() || (raid.status + " " + String.join(" ", raid.members)).toLowerCase(Locale.ROOT).contains(query))
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
			toggle.setForeground(ACCENT);
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
		int team = raid.teamSize > 0 ? raid.teamSize : raid.members.size();
		JPanel card = card();
		boolean open = expanded.contains(raid.id);
		JButton toggle = new JButton((open ? "\u25be " : "\u25b8 ") + DATE.format(Instant.ofEpochMilli(raid.started)));
		toggle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
		toggle.setForeground(MUTED);
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
		card.add(row(raid.status, TfaRaid.time(raid.total), completed ? ACCENT : GOLD, 17));
		card.add(Box.createVerticalStrut(4));
		card.add(label((team == 0 ? "Unknown team" : team + (team == 1 ? " player" : " players"))
			+ "  /  " + raid.splits.stream().filter(s -> s.duration >= 0).count() + " rooms", 10, MUTED));
		if (completed && !raid.completeSplits())
		{
			card.add(label("Partial room history", 10, GOLD));
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
		card.add(row("ROOM", "SPLIT", MUTED, 10));
		for (TfaRaid.Split split : raid.splits)
		{
			card.add(Box.createVerticalStrut(7));
			JPanel line = row(shortRoom(split.room), TfaRaid.time(split.duration), split.duration < 0 ? GOLD : TEXT, 11);
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

	private static void addParty(JPanel card, TfaRaid raid)
	{
		card.add(Box.createVerticalStrut(12));
		card.add(label("RAID PARTY", 10, MUTED));
		card.add(Box.createVerticalStrut(5));
		if (raid.members.isEmpty())
		{
			card.add(label("Roster not captured", 11, GOLD));
		}
		for (String member : raid.members)
		{
			JLabel name = label("\u2022  " + member, 11, TEXT);
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
		JPanel panel = card();
		panel.add(label(title, 9, MUTED));
		panel.add(Box.createVerticalStrut(4));
		panel.add(value);
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
		JPanel panel = new JPanel()
		{
			@Override
			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g = (Graphics2D) graphics.create();
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.setColor(CARD);
				g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
				g.dispose();
				super.paintComponent(graphics);
			}
		};
		panel.setOpaque(false);
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setAlignmentX(LEFT_ALIGNMENT);
		panel.setBorder(BorderFactory.createEmptyBorder(11, 10, 11, 10));
		return panel;
	}
}
