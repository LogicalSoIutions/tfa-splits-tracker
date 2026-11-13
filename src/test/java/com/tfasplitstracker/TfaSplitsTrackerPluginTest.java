package com.tfasplitstracker;

import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.IterableHashTable;
import net.runelite.api.MessageNode;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.VarClientStrChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.ClientToolbar;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TfaSplitsTrackerPluginTest
{
	private static void inject(Object target, String name, Object value) throws Exception
	{
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void gameTickRecoversMissedChatCallbackAndCapturesAuthoritativeParty() throws Exception
	{
		TfaSplitsTrackerPlugin plugin = new TfaSplitsTrackerPlugin();
		Client client = mock(Client.class);
		ClientThread thread = mock(ClientThread.class);
		ConfigManager config = mock(ConfigManager.class);
		when(config.getRSProfileKey()).thenReturn("test-profile");
		inject(plugin, "client", client);
		inject(plugin, "clientThread", thread);
		inject(plugin, "toolbar", mock(ClientToolbar.class));
		inject(plugin, "configManager", config);
		inject(plugin, "gson", new Gson());
		ChatMessageManager chat = mock(ChatMessageManager.class);
		InfoBoxManager boxes = mock(InfoBoxManager.class);
		TfaSplitsTrackerConfig settings = new TfaSplitsTrackerConfig() { };
		inject(plugin, "config", settings);
		inject(plugin, "chatMessageManager", chat);
		inject(plugin, "infoBoxManager", boxes);
		OverlayManager overlays = mock(OverlayManager.class);
		TfaSplitsOverlay overlay = new TfaSplitsOverlay(plugin, settings);
		inject(plugin, "overlayManager", overlays);
		inject(plugin, "overlay", overlay);
		doAnswer(invocation ->
		{
			((Runnable) invocation.getArgument(0)).run();
			return null;
		}).when(thread).invokeLater(any(Runnable.class));
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		net.runelite.api.WorldView world = mock(net.runelite.api.WorldView.class);
		when(client.getTopLevelWorldView()).thenReturn(world);
		when(world.isInstance()).thenReturn(true);
		when(client.getLocalPlayer()).thenReturn(mock(net.runelite.api.Player.class));
		Widget hud = mock(Widget.class);
		when(client.getWidget(InterfaceID.TfaHud.UNIVERSE)).thenReturn(hud);
		Widget clock = mock(Widget.class);
		when(client.getWidget(InterfaceID.TfaHud.RAID_TIMER)).thenReturn(clock);
		when(clock.getText()).thenReturn("<col=ffffff>1:23.40</col>");
		when(client.getVarcStrValue(VarClientID.TFA_CLIENT_NAME0)).thenReturn("First\u00a0Raider");
		when(client.getVarcStrValue(VarClientID.TFA_CLIENT_NAME7)).thenReturn("Eighth Raider");
		IterableHashTable<MessageNode> table = mock(IterableHashTable.class);
		List<MessageNode> messages = new ArrayList<>();
		when(table.iterator()).thenAnswer(invocation -> messages.iterator());
		when(client.getMessages()).thenReturn(table);
		SwingUtilities.invokeAndWait(plugin::startUp);
		try
		{
			MessageNode start = mock(MessageNode.class);
			when(start.getType()).thenReturn(ChatMessageType.GAMEMESSAGE);
			when(start.getTimestamp()).thenReturn(100);
			when(start.getValue()).thenReturn("Challenge started: Archive Guardian.");
			messages.add(start);
			plugin.onGameTick(new GameTick()); // No ChatMessage callback was delivered.
			Field field = TfaSplitsTrackerPlugin.class.getDeclaredField("tracker");
			field.setAccessible(true);
			TfaTracker tracker = (TfaTracker) field.get(plugin);
			assertNotNull(tracker.current());
			assertEquals(2, tracker.current().members.size());
			assertTrue(tracker.current().members.contains("First Raider"));
			assertTrue(tracker.current().members.contains("Eighth Raider"));
			assertEquals(2, tracker.current().scale());
			assertEquals("No recorded PB for scale 2", TfaPersonalBest.status(plugin.displayRaid()));
			plugin.onGameTick(new GameTick());
			assertEquals(1, tracker.current().splits.size());
			assertEquals(83400, plugin.liveState().elapsed);
			when(clock.getText()).thenReturn("1:24.60");
			plugin.onClientTick(new ClientTick());
			assertEquals(84600, plugin.liveState().elapsed);
			when(client.getGameState()).thenReturn(GameState.LOADING);
			plugin.onGameTick(new GameTick());
			assertNotNull(tracker.current());
			assertTrue(plugin.liveState().inRaid);
			when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
			when(hud.isHidden()).thenReturn(true);
			when(client.getVarcStrValue(VarClientID.TFA_CLIENT_NAME7)).thenReturn("Stale Lobby Name");
			plugin.onVarClientStrChanged(new VarClientStrChanged(VarClientID.TFA_CLIENT_NAME7));
			assertFalse(tracker.current().members.contains("Stale Lobby Name"));
			verify(config, atLeastOnce()).setRSProfileConfiguration(eq("tfasplitstracker"), eq("history"), any());
			MessageNode complete = mock(MessageNode.class);
			when(complete.getType()).thenReturn(ChatMessageType.GAMEMESSAGE);
			when(complete.getTimestamp()).thenReturn(160);
			when(complete.getValue()).thenReturn("Challenge complete: Archive Guardian. Duration: 1:00.00. Total: 1:00.00");
			messages.add(complete);
			plugin.onGameTick(new GameTick());
			ChatMessage callback = new ChatMessage();
			callback.setType(ChatMessageType.GAMEMESSAGE);
			callback.setTimestamp(160);
			callback.setMessage(complete.getValue());
			plugin.onChatMessage(callback);
			plugin.onGameTick(new GameTick());
			verify(chat, times(1)).queue(any(QueuedMessage.class));
			assertEquals(60000, plugin.displayRaid().splits.get(0).duration);
			assertTrue(plugin.liveState().inRaid);
			assertEquals("Between rooms", plugin.liveState().status);
			assertEquals(-1, plugin.liveState().elapsed);
			assertTrue(plugin.timingVisible());
			// Returning HUD and the next room continue the same record.
			String raidId = plugin.displayRaid().id;
			when(hud.isHidden()).thenReturn(false);
			plugin.onClientTick(new ClientTick());
			assertEquals(84600, plugin.liveState().elapsed);
			callback.setTimestamp(170);
			callback.setMessage("Challenge started: Moia.");
			plugin.onChatMessage(callback);
			assertEquals(raidId, plugin.displayRaid().id);
			assertEquals(2, plugin.displayRaid().splits.size());
			assertEquals(60000, plugin.displayRaid().splits.get(0).duration);
			verify(boxes, atLeastOnce()).addInfoBox(any(TfaSplitInfoBox.class));
			// Reloading the profile recovers retained chat without printing old results.
			plugin.onProfileChanged(new net.runelite.client.events.ProfileChanged());
			verify(chat, times(1)).queue(any(QueuedMessage.class));
			// The party HUD stays open in the public lobby: it is not an active raid.
			when(hud.isHidden()).thenReturn(false);
			when(world.isInstance()).thenReturn(false);
			Widget status = mock(Widget.class);
			when(status.getText()).thenReturn("No Party");
			when(client.getWidget(InterfaceID.TfaHud.STATUS)).thenReturn(status);
			plugin.onGameTick(new GameTick());
			assertFalse(plugin.liveState().inRaid);
			assertEquals("No Party", plugin.liveState().status);
			assertFalse(plugin.timingVisible());
			assertEquals("Left raid", plugin.displayRaid().status);
			assertEquals(60000, plugin.displayRaid().splits.get(0).duration);
			plugin.onGameTick(new GameTick());
			assertEquals("Left raid", plugin.displayRaid().status);
			// Entering the next instance clears old splits before Guardian starts.
			tracker = (TfaTracker) field.get(plugin);
			clearInvocations(boxes);
			when(world.isInstance()).thenReturn(true);
			when(clock.getText()).thenReturn("0:00.00");
			plugin.onClientTick(new ClientTick());
			assertTrue(plugin.liveState().inRaid);
			assertNull(plugin.displayRaid());
			verify(boxes).removeIf(any());
			verify(boxes, never()).addInfoBox(any());
			plugin.onGameTick(new GameTick());
			assertNull(plugin.displayRaid());
			assertEquals("Left raid", tracker.raids.get(0).status);
			assertEquals(60000, tracker.raids.get(0).splits.get(0).duration);
			callback.setTimestamp(200);
			callback.setMessage("Challenge started: Archive Guardian.");
			plugin.onChatMessage(callback);
			assertNotNull(plugin.displayRaid());
			assertTrue(plugin.displayRaid().active());
			assertEquals(1, plugin.displayRaid().splits.size());
			assertEquals(-1, plugin.displayRaid().splits.get(0).duration);
			assertEquals(-1, plugin.displayRaid().total);
			// Temporary loading must not treat a continuing run as a new raid.
			callback.setTimestamp(260);
			callback.setMessage(complete.getValue());
			plugin.onChatMessage(callback);
			when(client.getGameState()).thenReturn(GameState.LOADING);
			plugin.onClientTick(new ClientTick());
			when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
			plugin.onClientTick(new ClientTick());
			assertEquals(60000, plugin.displayRaid().splits.get(0).duration);
			callback.setTimestamp(300);
			callback.setMessage("The Fractured Archive: Total completion time: 1:40.00");
			plugin.onChatMessage(callback);
			assertEquals("Completed", plugin.displayRaid().status);
			// Loading after completion also preserves this session's results.
			when(client.getGameState()).thenReturn(GameState.LOADING);
			plugin.onClientTick(new ClientTick());
			when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
			plugin.onClientTick(new ClientTick());
			assertEquals("Completed", plugin.displayRaid().status);
			when(world.isInstance()).thenReturn(false);
			plugin.onGameTick(new GameTick());
			when(world.isInstance()).thenReturn(true);
			plugin.onClientTick(new ClientTick());
			assertNull(plugin.displayRaid());
			assertEquals("Completed", tracker.raids.get(0).status);
			assertEquals(100000, tracker.raids.get(0).total);
			// Capture live Ocellus transitions independently of the encounter helper.
			when(client.getTickCount()).thenReturn(100);
			callback.setTimestamp(400);
			callback.setMessage("Challenge started: Ocellus.");
			plugin.onChatMessage(callback);
			assertEquals(1, plugin.displayRaid().splits.get(0).phases.size());
			net.runelite.api.NPC boss = mock(net.runelite.api.NPC.class);
			when(boss.getId()).thenReturn(16675);
			when(boss.getWorldView()).thenReturn(mock(net.runelite.api.WorldView.class));
			when(client.getTickCount()).thenReturn(200);
			plugin.onNpcChanged(new net.runelite.api.events.NpcChanged(boss, null));
			assertEquals(1, plugin.displayRaid().splits.get(0).phases.size());
			when(boss.getWorldView()).thenReturn(world);
			plugin.onNpcChanged(new net.runelite.api.events.NpcChanged(boss, null));
			assertEquals(60000, plugin.displayRaid().splits.get(0).phases.get(0).duration);
			when(boss.getId()).thenReturn(16677);
			when(client.getTickCount()).thenReturn(300);
			plugin.onNpcChanged(new net.runelite.api.events.NpcChanged(boss, null));
			when(boss.getAnimation()).thenReturn(14859);
			net.runelite.api.events.AnimationChanged animation = new net.runelite.api.events.AnimationChanged();
			animation.setActor(boss);
			for (int tick : new int[] {340, 340, 370, 400})
			{
				when(client.getTickCount()).thenReturn(tick);
				plugin.onAnimationChanged(animation);
			}
			assertEquals(4, plugin.displayRaid().splits.get(0).phases.size());
			callback.setTimestamp(610);
			callback.setMessage("Challenge complete: Ocellus. Duration: 3:30.00. Total: 5:10.00");
			plugin.onChatMessage(callback);
			assertEquals(30000, plugin.displayRaid().splits.get(0).phases.get(3).duration);
		}
		finally
		{
			SwingUtilities.invokeAndWait(plugin::shutDown);
			verify(overlays).add(overlay);
			verify(overlays).remove(overlay);
			assertNull(plugin.displayRaid());
		}
	}
}
