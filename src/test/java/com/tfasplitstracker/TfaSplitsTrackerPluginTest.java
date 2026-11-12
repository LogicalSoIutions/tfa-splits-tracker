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
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
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
		OverlayManager overlays = mock(OverlayManager.class);
		InfoBoxManager boxes = mock(InfoBoxManager.class);
		TfaSplitsTrackerConfig settings = new TfaSplitsTrackerConfig() { };
		inject(plugin, "config", settings);
		inject(plugin, "chatMessageManager", chat);
		inject(plugin, "overlayManager", overlays);
		inject(plugin, "infoBoxManager", boxes);
		TfaSplitsOverlay overlay = new TfaSplitsOverlay(plugin, settings);
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
			plugin.onGameTick(new GameTick());
			assertEquals(1, tracker.current().splits.size());
			assertEquals(83400, plugin.liveState().elapsed);
			when(clock.getText()).thenReturn("1:24.60");
			plugin.onClientTick(new ClientTick());
			assertEquals(84600, plugin.liveState().elapsed);
			when(client.getGameState()).thenReturn(GameState.LOADING);
			plugin.onGameTick(new GameTick());
			assertNotNull(tracker.current());
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
			verify(boxes, atLeastOnce()).addInfoBox(any(TfaSplitInfoBox.class));
			// Reloading the profile recovers retained chat without printing old results.
			plugin.onProfileChanged(new net.runelite.client.events.ProfileChanged());
			verify(chat, times(1)).queue(any(QueuedMessage.class));
			verify(overlays).add(overlay);
			// The party HUD stays open in the public lobby: it is not an active raid.
			when(hud.isHidden()).thenReturn(false);
			when(world.isInstance()).thenReturn(false);
			Widget status = mock(Widget.class);
			when(status.getText()).thenReturn("No Party");
			when(client.getWidget(InterfaceID.TfaHud.STATUS)).thenReturn(status);
			plugin.onGameTick(new GameTick());
			assertFalse(plugin.liveState().inRaid);
			assertEquals("No Party", plugin.liveState().status);
			assertFalse(plugin.timingVisible(false));
			assertEquals("Left raid", plugin.displayRaid().status);
			assertEquals(60000, plugin.displayRaid().splits.get(0).duration);
			plugin.onGameTick(new GameTick());
			assertEquals("Left raid", plugin.displayRaid().status);
		}
		finally
		{
			SwingUtilities.invokeAndWait(plugin::shutDown);
			verify(overlays).remove(overlay);
			assertNull(plugin.displayRaid());
		}
	}
}
