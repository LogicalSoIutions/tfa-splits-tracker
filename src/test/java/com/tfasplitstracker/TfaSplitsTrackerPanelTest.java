package com.tfasplitstracker;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class TfaSplitsTrackerPanelTest
{
	@Test
	public void ocellusPhasesAppearInLiveAndExpandedHistoryAtSidebarWidth() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			TfaTracker data = TfaOcellusTest.completedFight();
			TfaRaid raid = data.raids.get(0);
			raid.personalBest = new TfaPersonalBest();
			raid.personalBest.scale = 3;
			raid.personalBest.total = 550000;
			raid.personalBest.splits.addAll(raid.copy().splits);
			raid.personalBest.splits.get(0).phases.get(0).duration = 65000;
			raid.status = "In progress";
			TfaSplitsTrackerPanel panel = new TfaSplitsTrackerPanel();
			try
			{
				panel.update(data, true, new TfaLiveState(true, "Ocellus", 540000));
				assertTrue(labelTexts(panel).contains("P1 Vines"));
				assertTrue(labelTexts(panel).contains("P4 Prayer switches"));
				assertTrue(labelTexts(panel).contains("Phase vs PB"));
				assertTrue(labelTexts(panel).contains("-0:05.00"));
				raid.status = "Completed";
				panel.update(data, true, new TfaLiveState(true, "Completed", 540000));
				assertFalse(labelTexts(panel).contains("P1 Vines"));
				String date = java.time.format.DateTimeFormatter.ofPattern("MMM d, HH:mm")
					.withZone(ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(raid.started));
				findButton(panel, "\u25b8 " + date).doClick();
				assertTrue(labelTexts(panel).contains("P1 Vines"));
				assertTrue(labelTexts(panel).contains("0:30.00"));
				panel.setSize(225, panel.getPreferredSize().height);
				layout(panel);
				checkBounds(panel);
				BufferedImage image = new BufferedImage(225, panel.getHeight(), BufferedImage.TYPE_INT_ARGB);
				Graphics2D graphics = image.createGraphics();
				panel.printAll(graphics);
				graphics.dispose();
				try { ImageIO.write(image, "png", new File("build/tfa-ocellus-panel.png")); }
				catch (java.io.IOException ex) { throw new RuntimeException(ex); }
				raid.personalBest.splits.get(0).phases.clear();
				panel.update(data, true, new TfaLiveState(true, "Completed", 540000));
				assertFalse(labelTexts(panel).contains("Phase vs PB"));
				assertTrue(labelTexts(panel).contains("P1 Vines"));
			}
			finally { panel.stop(); }
		});
	}

	@Test
	public void personalBestDeltasAlwaysAppearAndFitNarrowSidebar() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			TfaTracker data = new TfaTracker();
			data.raids.add(TfaPersonalBestTest.saved(3, 10000));
			data.accept("Challenge started: Archive Guardian.", 100000);
			data.party(Arrays.asList("This Whip", "LogicalHash", "Godly"));
			data.accept("Challenge complete: Archive Guardian. Duration: 0:09.00. Total: 0:11.00", 111000);
			data.accept("Challenge complete: Ezekiel. Duration: 0:15.00. Total: 0:25.00", 125000);
			TfaSplitsTrackerPanel panel = new TfaSplitsTrackerPanel();
			try
			{
				panel.update(data, true, new TfaLiveState(true, "In progress", 25000));
				assertTrue(labelTexts(panel).contains("Scale 3 PB: 1:00.00"));
				assertTrue(labelTexts(panel).contains("Room vs PB"));
				assertTrue(labelTexts(panel).contains("-0:01.00"));
				assertTrue(labelTexts(panel).contains("+0:01.00"));
				assertTrue(labelTexts(panel).contains("PB stage used Bork"));
				panel.setSize(225, panel.getPreferredSize().height);
				layout(panel);
				checkBounds(panel);
				BufferedImage image = new BufferedImage(225, panel.getHeight(), BufferedImage.TYPE_INT_ARGB);
				Graphics2D graphics = image.createGraphics();
				panel.printAll(graphics);
				graphics.dispose();
				try { ImageIO.write(image, "png", new File("build/tfa-pb-panel.png")); }
				catch (java.io.IOException ex) { throw new RuntimeException(ex); }
				panel.update(data, true, new TfaLiveState(true, "In progress", 26000));
				assertTrue(labelTexts(panel).contains("Room vs PB"));
				assertTrue(labelTexts(panel).contains("Raid vs PB"));
			}
			finally { panel.stop(); }
		});
	}

	@Test
	public void lobbyClearsLiveCardAndClockFollowsHudSnapshot() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			TfaTracker data = new TfaTracker();
			data.accept("Challenge started: Archive Guardian.", 100000);
			TfaSplitsTrackerPanel panel = new TfaSplitsTrackerPanel();
			try
			{
				panel.update(data, true, new TfaLiveState(true, "In progress", 83400));
				assertTrue(labelTexts(panel).contains("1:23.40"));
				panel.updateLiveState(new TfaLiveState(true, "In progress", 84600));
				assertTrue(labelTexts(panel).contains("1:24.60"));
				data.accept("Challenge complete: Archive Guardian. Duration: 1:00.00. Total: 1:02.40", 162000);
				panel.update(data, true, new TfaLiveState(true, "Between rooms", -1));
				assertTrue(labelTexts(panel).contains("LIVE RAID"));
				assertTrue(labelTexts(panel).contains("Archive Guardian"));
				assertTrue(labelTexts(panel).contains("1:00.00"));
				assertTrue(labelTexts(panel).contains("1:02.40"));
				assertTrue(labelTexts(panel).contains("Between rooms"));
				assertEquals(1, findComponent(panel, javax.swing.JProgressBar.class).getValue());
				assertFalse(labelTexts(panel).contains("Waiting for challenge start"));
				panel.updateLiveState(new TfaLiveState(true, "Loading raid", -1));
				assertTrue(labelTexts(panel).contains("LIVE RAID"));
				assertTrue(labelTexts(panel).contains("1:02.40"));
				panel.updateLiveState(new TfaLiveState(false, "No Party", -1));
				assertFalse(labelTexts(panel).contains("LIVE RAID"));
				assertTrue(labelTexts(panel).contains("No Party"));
				assertFalse(labelTexts(panel).contains("1:24.60"));
				data.leaveRaid();
				panel.update(data, true, new TfaLiveState(false, "No Party", -1));
				assertTrue(labelTexts(panel).contains("Last raid: Left raid"));
			}
			finally { panel.stop(); }
		});
	}

	@Test
	public void groupByLocalStartDayWithFilteredCountsAndCollapsibleDays() throws Exception
	{
		TfaTracker data = new TfaTracker();
		LocalDate day = LocalDate.of(2026, 10, 23);
		for (int i = 0; i < 3; i++)
		{
			TfaRaid raid = new TfaRaid();
			raid.started = (i == 2 ? day.minusDays(1) : day).atTime(i == 2 ? 23 : 12, i)
				.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
			raid.status = i == 1 ? "Failed" : "Completed";
			raid.teamSize = i == 1 ? 3 : 2;
			raid.members.add(i == 1 ? "Bob" : "Alice");
			data.raids.add(raid);
		}
		SwingUtilities.invokeAndWait(() ->
		{
			TfaSplitsTrackerPanel panel = new TfaSplitsTrackerPanel();
			try
			{
				panel.update(data, true, false);
				List<String> labels = labelTexts(panel);
				assertTrue(labels.contains("2 raids"));
				assertTrue(labels.contains("1 raid"));
				JButton today = findButton(panel, "\u25be Fri, Oct 23, 2026");
				JButton yesterday = findButton(panel, "\u25be Thu, Oct 22, 2026");
				assertNotNull(today);
				assertNotNull(yesterday);
				panel.setSize(225, panel.getPreferredSize().height);
				layout(panel);
				checkBounds(panel);
				assertTrue(today.getParent().getY() < yesterday.getParent().getY());
				today.doClick();
				assertFalse(labelTexts(panel).contains("Failed"));
				assertNotNull(findButton(panel, "\u25b8 Fri, Oct 23, 2026"));
				assertTrue(labelTexts(panel).contains("2 raids"));
				findButton(panel, "\u25b8 Fri, Oct 23, 2026").doClick();
				assertTrue(labelTexts(panel).contains("Failed"));
				findComponent(panel, JComboBox.class).setSelectedIndex(2);
				assertFalse(labelTexts(panel).contains("2 raids"));
				assertFalse(labelTexts(panel).contains("Failed"));
				assertNotNull(findButton(panel, "\u25be Thu, Oct 22, 2026"));
				findComponent(panel, JComboBox.class).setSelectedIndex(0);
				findComponent(panel, JTextField.class).setText("Bob");
				assertTrue(labelTexts(panel).contains("1 raid"));
				assertTrue(labelTexts(panel).contains("Failed"));
				assertNull(findButton(panel, "\u25be Thu, Oct 22, 2026"));
			}
			finally
			{
				panel.stop();
			}
		});
	}

	private static List<String> labelTexts(Container container)
	{
		List<String> texts = new ArrayList<>();
		for (Component child : container.getComponents())
		{
			if (child instanceof JLabel)
			{
				texts.add(((JLabel) child).getText());
			}
			if (child instanceof Container)
			{
				texts.addAll(labelTexts((Container) child));
			}
		}
		return texts;
	}

	private static <T extends Component> T findComponent(Container container, Class<T> type)
	{
		for (Component child : container.getComponents())
		{
			if (type.isInstance(child))
			{
				return type.cast(child);
			}
			if (child instanceof Container)
			{
				T found = findComponent((Container) child, type);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	@Test
	public void contextMenusDeleteSavedRecordsAndResetLiveTracker() throws Exception
	{
		TfaTracker data = new TfaTracker();
		for (TfaTrackerTest.Event event : TfaTrackerTest.recordings()[0].events)
		{
			data.accept(event.message, event.timestamp);
		}
		TfaRaid saved = data.raids.get(0);
		data.accept("Challenge started: Archive Guardian.", System.currentTimeMillis());
		AtomicReference<String> id = new AtomicReference<>();
		AtomicReference<String> room = new AtomicReference<>();
		AtomicBoolean undo = new AtomicBoolean();
		SwingUtilities.invokeAndWait(() ->
		{
			TfaSplitsTrackerPanel panel = new TfaSplitsTrackerPanel((raidId, roomName) ->
			{
				id.set(raidId);
				room.set(roomName);
			}, () -> undo.set(true));
			try
			{
				panel.update(data, true, true);
				findMenuItem(panel, "Delete raid").doClick();
				assertEquals(saved.id, id.get());
				assertNull(room.get());
				clickHistory(panel);
				findMenuItem(panel, "Delete room time").doClick();
				assertEquals(saved.id, id.get());
				assertEquals("Archive Guardian", room.get());
				findMenuItem(panel, "Reset current tracker").doClick();
				assertEquals(data.current().id, id.get());
				assertNull(room.get());
				data.deleted = saved;
				panel.update(data, true, true);
				findButton(panel, "Undo last deletion").doClick();
				assertTrue(undo.get());
			}
			finally
			{
				panel.stop();
			}
		});
	}

	@Test
	public void renderNarrowSidebarWithExpandedHistory() throws Exception
	{
		TfaTracker data = new TfaTracker();
		TfaTrackerTest.RecordedRaid recording = TfaTrackerTest.recordings()[0];
		for (TfaTrackerTest.Event event : recording.events)
		{
			data.accept(event.message, event.timestamp);
		}
		data.raids.get(0).members.addAll(Arrays.asList("Archive Raider", "Long Name 12", "Third Raider"));
		data.accept("Challenge started: Archive Guardian.", System.currentTimeMillis() - 32000);
		data.party(Arrays.asList("Archive Raider", "Long Name 12", "Third Raider"));
		SwingUtilities.invokeAndWait(() ->
		{
			TfaSplitsTrackerPanel panel = new TfaSplitsTrackerPanel();
			try
			{
				panel.update(data, true, new TfaLiveState(true, "In progress", 32400));
				clickHistory(panel);
				panel.setSize(225, panel.getPreferredSize().height);
				layout(panel);
				checkBounds(panel);
				assertTrue(panel.getHeight() > 500);
				BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
				Graphics2D g = image.createGraphics();
				panel.printAll(g);
				g.dispose();
				File output = new File("build/tfa-splits-panel.png");
				try
				{
					ImageIO.write(image, "png", output);
				}
				catch (Exception ex)
				{
					throw new AssertionError(ex);
				}
			}
			finally
			{
				panel.stop();
			}
		});
	}

	private static void clickHistory(Container container)
	{
		for (Component child : container.getComponents())
		{
			if (child instanceof JButton && ((JButton) child).getText().startsWith("\u25b8"))
			{
				((JButton) child).doClick();
				return;
			}
			if (child instanceof Container)
			{
				clickHistory((Container) child);
			}
		}
	}

	private static JButton findButton(Container container, String title)
	{
		for (Component child : container.getComponents())
		{
			if (child instanceof JButton && title.equals(((JButton) child).getText()))
			{
				return (JButton) child;
			}
			if (child instanceof Container)
			{
				JButton result = findButton((Container) child, title);
				if (result != null)
				{
					return result;
				}
			}
		}
		return null;
	}

	private static JMenuItem findMenuItem(Container container, String title)
	{
		if (container instanceof JComponent && ((JComponent) container).getComponentPopupMenu() != null)
		{
			for (Component item : ((JComponent) container).getComponentPopupMenu().getComponents())
			{
				if (item instanceof JMenuItem && title.equals(((JMenuItem) item).getText()))
				{
					return (JMenuItem) item;
				}
			}
		}
		for (Component child : container.getComponents())
		{
			if (child instanceof Container)
			{
				JMenuItem result = findMenuItem((Container) child, title);
				if (result != null)
				{
					return result;
				}
			}
		}
		return null;
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

	private static void checkBounds(Container container)
	{
		for (Component child : container.getComponents())
		{
			assertTrue(child.getClass() + " exceeds " + container.getClass() + ": " + child.getBounds() + " parent width=" + container.getWidth(),
				child.getX() >= 0 && child.getX() + child.getWidth() <= container.getWidth());
			if (child instanceof Container)
			{
				checkBounds((Container) child);
			}
		}
	}
}
