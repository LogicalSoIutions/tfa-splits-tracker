package com.tfasplitstracker;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MessageNode;
import net.runelite.api.NPC;
import net.runelite.api.WorldView;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarClientStrChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;

@Slf4j
@PluginDescriptor(
	name = "TFA Splits Tracker",
	description = "Fractured Archive raid history, server room splits, total times and party members",
	tags = {"tfa", "fractured", "archive", "raid", "splits", "timer", "party"},
	enabledByDefault = true
)
public class TfaSplitsTrackerPlugin extends Plugin
{
	private static final String GROUP = TfaSplitsTrackerConfig.GROUP;
	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private ClientToolbar toolbar;
	@Inject private ConfigManager configManager;
	@Inject private Gson gson;
	@Inject private TfaSplitsTrackerConfig config;
	@Inject private ChatMessageManager chatMessageManager;
	@Inject private InfoBoxManager infoBoxManager;
	@Inject private OverlayManager overlayManager;
	@Inject private TfaSplitsOverlay overlay;
	private TfaOcellus ocellus = new TfaOcellus();
	private TfaTracker tracker = new TfaTracker();
	private TfaSplitsTrackerPanel panel;
	private NavigationButton navigation;
	private volatile boolean running;
	private boolean storageHealthy = true;
	private volatile boolean liveAvailable;
	private volatile TfaRaid displayRaid;
	private volatile TfaLiveState liveState = TfaLiveState.UNAVAILABLE;
	private boolean raidSessionActive;
	private boolean awaitingRaidStart;

	@Provides
	TfaSplitsTrackerConfig provideConfig(ConfigManager manager)
	{
		return manager.getConfig(TfaSplitsTrackerConfig.class);
	}

	@Override
	protected void startUp()
	{
		running = true;
		overlayManager.add(overlay);
		panel = new TfaSplitsTrackerPanel(this::delete, this::undoDelete);
		navigation = NavigationButton.builder().tooltip("TFA Splits Tracker").icon(icon()).priority(7).panel(panel).build();
		toolbar.addNavigation(navigation);
		clientThread.invokeLater(() ->
		{
			if (running)
			{
				load();
				reconcile(false);
				refreshRaidState();
				publish();
			}
		});
	}

	@Override
	protected void shutDown()
	{
		running = false;
		overlayManager.remove(overlay);
		ocellus = new TfaOcellus();
		infoBoxManager.removeIf(box -> box instanceof TfaSplitInfoBox);
		displayRaid = null;
		liveState = TfaLiveState.UNAVAILABLE;
		raidSessionActive = false;
		awaitingRaidStart = false;
		toolbar.removeNavigation(navigation);
		panel.stop();
		// Every accepted timing and roster update has already been persisted.
	}

	@Subscribe
	public void onProfileChanged(ProfileChanged event)
	{
		clientThread.invokeLater(() ->
		{
			if (running)
			{
				load();
				reconcile(false);
				refreshRaidState();
				publish();
			}
		});
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		onProfileChanged(new ProfileChanged());
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (running && configManager.getRSProfileKey() != null && event.getType() == ChatMessageType.GAMEMESSAGE)
		{
			// Reconcile older messages first, including when a callback was not delivered.
			reconcile(true);
			boolean changed = accept(event.getMessage(), event.getTimestamp() * 1000L, true);
			TfaRaid.Split room = liveOcellus();
			if (room != null && room.started == event.getTimestamp() * 1000L
				&& "Challenge started: Ocellus.".equals(TfaLiveState.clean(event.getMessage())))
			{
				int previous = room.phases.size();
				ocellus.begin(room, client.getTickCount());
				changed |= room.phases.size() != previous;
			}
			if (changed)
			{
				captureParty();
				saveAndPublish();
			}
		}
	}

	@Subscribe
	public void onNpcChanged(NpcChanged event) { observeOcellus(event.getNpc(), false, true); }

	@Subscribe
	public void onNpcSpawned(NpcSpawned event) { observeOcellus(event.getNpc(), false, false); }

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (event.getActor() instanceof NPC && event.getActor().getAnimation() == TfaOcellus.DESTROY_QUADRANT_ANIMATION)
		{
			observeOcellus((NPC) event.getActor(), true, true);
		}
	}

	private void observeOcellus(NPC npc, boolean quadrantDestroyed, boolean boundary)
	{
		if (!running || configManager.getRSProfileKey() == null || client.getGameState() != GameState.LOGGED_IN
			|| npc.getWorldView() != client.getTopLevelWorldView()
			|| npc.getId() != TfaOcellus.VINES_NPC && npc.getId() != TfaOcellus.PORTALS_NPC
				&& npc.getId() != TfaOcellus.QUADRANTS_NPC) { return; }
		reconcile(true);
		TfaRaid.Split room = liveOcellus();
		if (room == null) { return; }
		TfaRaid before = tracker.current().copy();
		boolean changed = quadrantDestroyed
			? npc.getId() == TfaOcellus.QUADRANTS_NPC && ocellus.destroyQuadrant(room, client.getTickCount())
			: ocellus.observe(room, npc.getId(), client.getTickCount(), boundary);
		if (changed)
		{
			announce(before);
			saveAndPublish();
		}
	}

	private TfaRaid.Split liveOcellus()
	{
		TfaRaid raid = tracker.current();
		if (raid == null || raid.splits.isEmpty()) { return null; }
		TfaRaid.Split last = raid.splits.get(raid.splits.size() - 1);
		return "Ocellus".equals(last.room) && last.duration < 0 ? last : null;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (running)
		{
			reconcile(true);
			refreshRaidState();
			if (captureParty())
			{
				saveAndPublish();
			}
			if (liveAvailable != raidHudVisible())
			{
				publish();
			}
		}
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (!running) { return; }
		updateLiveState(readLiveState());
	}

	private void updateLiveState(TfaLiveState state)
	{
		boolean enteredRaid = state.inRaid && !raidSessionActive;
		if (enteredRaid)
		{
			raidSessionActive = true;
			awaitingRaidStart = tracker.current() == null;
		}
		WorldView world = client.getTopLevelWorldView();
		if (client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null
			&& world != null && !world.isInstance())
		{
			raidSessionActive = false;
			awaitingRaidStart = false;
		}
		TfaLiveState previous = liveState;
		if (state.inRaid == previous.inRaid && state.elapsed == previous.elapsed && state.status.equals(previous.status)) { return; }
		liveState = state;
		if (enteredRaid)
		{
			// A new instance starts with no splits, before the first challenge chat arrives.
			publish();
		}
		TfaSplitsTrackerPanel target = panel;
		SwingUtilities.invokeLater(() ->
		{
			if (running && panel == target) { target.updateLiveState(state); }
		});
	}

	private TfaLiveState readLiveState()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return client.getGameState() == GameState.LOADING && raidSessionActive
				? new TfaLiveState(true, "Loading raid", -1) : TfaLiveState.UNAVAILABLE;
		}
		boolean hudVisible = raidHudVisible();
		WorldView world = client.getTopLevelWorldView();
		// The game hides the HUD between encounters; the instance/session outlives it.
		boolean inRaid = world != null && world.isInstance()
			&& (hudVisible || raidSessionActive || tracker.current() != null);
		Widget status = client.getWidget(inRaid ? InterfaceID.TfaHud.RAID_STATE : InterfaceID.TfaHud.STATUS);
		Widget clock = client.getWidget(InterfaceID.TfaHud.RAID_TIMER);
		String text = !hudVisible || status == null || status.isHidden() ? "" : TfaLiveState.clean(status.getText());
		if (inRaid && !hudVisible)
		{
			TfaRaid raid = tracker.current();
			TfaRaid.Split last = raid == null || raid.splits.isEmpty() ? null : raid.splits.get(raid.splits.size() - 1);
			text = last != null && last.duration >= 0 ? "Between rooms" : "Raid HUD unavailable";
		}
		return new TfaLiveState(inRaid, text,
			!inRaid || !hudVisible || clock == null || clock.isHidden() ? -1 : TfaLiveState.parseClock(clock.getText()));
	}

	private void refreshRaidState()
	{
		updateLiveState(readLiveState());
		// Loading, reconnecting and temporary HUD loss are not evidence of leaving.
		WorldView world = client.getTopLevelWorldView();
		if (client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null
			&& world != null && !world.isInstance() && tracker.leaveRaid())
		{
			saveAndPublish();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (running)
		{
			publish();
		}
	}

	@Subscribe
	public void onVarClientStrChanged(VarClientStrChanged event)
	{
		if (running && event.getIndex() >= VarClientID.TFA_CLIENT_NAME0 && event.getIndex() <= VarClientID.TFA_CLIENT_NAME7)
		{
			// The game refreshes all eight slots in one script; wait for that refresh to finish.
			clientThread.invokeLater(() ->
			{
				if (running && captureParty())
				{
					saveAndPublish();
				}
			});
		}
	}

	private void reconcile(boolean announce)
	{
		if (configManager.getRSProfileKey() == null || client.getGameState() != GameState.LOGGED_IN || client.getMessages() == null)
		{
			return;
		}
		List<MessageNode> messages = new ArrayList<>();
		for (MessageNode node : client.getMessages())
		{
			if (node.getType() == ChatMessageType.GAMEMESSAGE && node.getTimestamp() * 1000L >= tracker.lastEvent)
			{
				messages.add(node);
			}
		}
		messages.sort(Comparator.comparingInt(MessageNode::getTimestamp).thenComparingInt(MessageNode::getId));
		boolean changed = false;
		for (MessageNode node : messages)
		{
			changed |= accept(node.getValue(), node.getTimestamp() * 1000L, announce);
		}
		if (changed)
		{
			captureParty();
			saveAndPublish();
		}
	}

	private boolean accept(String message, long timestamp, boolean announce)
	{
		TfaRaid before = tracker.raids.isEmpty() ? null : tracker.raids.get(0).copy();
		if (!tracker.accept(message, timestamp)) { return false; }
		TfaRaid latest = tracker.raids.isEmpty() ? null : tracker.raids.get(0);
		if (latest != null && (latest.active() || before == null || !before.id.equals(latest.id)))
		{
			awaitingRaidStart = false;
		}
		if (announce)
		{
			announce(before);
		}
		return true;
	}

	private void announce(TfaRaid before)
	{
		TfaRaid after = tracker.raids.isEmpty() ? null : tracker.raids.get(0);
		for (String text : TfaTiming.messages(before, after, config))
		{
			chatMessageManager.queue(QueuedMessage.builder().type(ChatMessageType.CONSOLE)
				.runeLiteFormattedMessage(text).build());
		}
	}

	TfaRaid displayRaid() { return displayRaid; }
	TfaLiveState liveState() { return liveState; }

	boolean timingVisible()
	{
		return running && client.getGameState() == GameState.LOGGED_IN && liveState.inRaid;
	}

	private boolean captureParty()
	{
		WorldView world = client.getTopLevelWorldView();
		if (tracker.current() == null || world == null || !world.isInstance() || !raidHudVisible())
		{
			return false;
		}
		List<String> names = new ArrayList<>();
		for (int slot = VarClientID.TFA_CLIENT_NAME0; slot <= VarClientID.TFA_CLIENT_NAME7; slot++)
		{
			names.add(client.getVarcStrValue(slot));
		}
		return tracker.party(names);
	}

	private boolean raidHudVisible()
	{
		Widget hud = client.getWidget(InterfaceID.TfaHud.UNIVERSE);
		return client.getGameState() == GameState.LOGGED_IN && hud != null && !hud.isHidden();
	}

	private void load()
	{
		ocellus = new TfaOcellus();
		tracker = new TfaTracker();
		storageHealthy = true;
		try
		{
			String json = configManager.getRSProfileConfiguration(GROUP, "history");
			if (json != null)
			{
				TfaTracker saved = gson.fromJson(json, TfaTracker.class);
				if (saved != null)
				{
					tracker = saved;
				}
			}
		}
		catch (RuntimeException ex)
		{
			storageHealthy = false;
			log.warn("Unable to read TFA splits history; preserving the stored data", ex);
		}
	}

	private void saveAndPublish()
	{
		if (storageHealthy && configManager.getRSProfileKey() != null)
		{
			configManager.setRSProfileConfiguration(GROUP, "history", gson.toJson(tracker));
		}
		publish();
	}

	private void delete(String raidId, String room)
	{
		String profile = configManager.getRSProfileKey();
		clientThread.invokeLater(() ->
		{
			if (running && profile != null && profile.equals(configManager.getRSProfileKey()) && tracker.delete(raidId, room))
			{
				saveAndPublish();
			}
		});
	}

	private void undoDelete()
	{
		String profile = configManager.getRSProfileKey();
		clientThread.invokeLater(() ->
		{
			if (running && profile != null && profile.equals(configManager.getRSProfileKey()) && tracker.undoDelete())
			{
				saveAndPublish();
			}
		});
	}

	private void publish()
	{
		// Swing receives its own immutable-in-practice copy; the game thread owns the reducer.
		TfaTracker snapshot = gson.fromJson(gson.toJson(tracker), TfaTracker.class);
		displayRaid = awaitingRaidStart || snapshot.raids.isEmpty() ? null : snapshot.raids.get(0);
		infoBoxManager.removeIf(box -> box instanceof TfaSplitInfoBox);
		if (displayRaid != null)
		{
			for (TfaRaid.Split split : displayRaid.splits)
			{
				if (split.duration >= 0) { infoBoxManager.addInfoBox(new TfaSplitInfoBox(this, config, split)); }
			}
		}
		TfaSplitsTrackerPanel target = panel;
		boolean healthy = storageHealthy;
		liveAvailable = raidHudVisible();
		TfaLiveState state = liveState;
		SwingUtilities.invokeLater(() ->
		{
			if (running && panel == target)
			{
				target.update(snapshot, healthy, state);
			}
		});
	}

	static BufferedImage icon()
	{
		BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(99, 218, 204));
		g.setStroke(new BasicStroke(2));
		g.drawOval(4, 5, 16, 16);
		g.drawLine(12, 8, 12, 13);
		g.drawLine(12, 13, 16, 15);
		g.drawLine(9, 2, 15, 2);
		g.drawLine(12, 2, 12, 5);
		g.dispose();
		return image;
	}
}
