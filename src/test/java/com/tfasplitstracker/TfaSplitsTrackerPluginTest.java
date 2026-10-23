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
import net.runelite.api.events.VarClientStrChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
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
		doAnswer(invocation ->
		{
			((Runnable) invocation.getArgument(0)).run();
			return null;
		}).when(thread).invokeLater(any(Runnable.class));
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		Widget hud = mock(Widget.class);
		when(client.getWidget(InterfaceID.TfaHud.UNIVERSE)).thenReturn(hud);
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
			when(hud.isHidden()).thenReturn(true);
			when(client.getVarcStrValue(VarClientID.TFA_CLIENT_NAME7)).thenReturn("Stale Lobby Name");
			plugin.onVarClientStrChanged(new VarClientStrChanged(VarClientID.TFA_CLIENT_NAME7));
			assertFalse(tracker.current().members.contains("Stale Lobby Name"));
			verify(config, atLeastOnce()).setRSProfileConfiguration(eq("tfasplitstracker"), eq("history"), any());
		}
		finally
		{
			SwingUtilities.invokeAndWait(plugin::shutDown);
		}
	}
}
