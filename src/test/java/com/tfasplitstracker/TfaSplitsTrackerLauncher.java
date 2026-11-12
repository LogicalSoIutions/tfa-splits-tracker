package com.tfasplitstracker;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class TfaSplitsTrackerLauncher
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(TfaSplitsTrackerPlugin.class);
		RuneLite.main(args);
	}
}
