package com.jadchallenge;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class JadChallengePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(JadChallengePlugin.class);
		RuneLite.main(args);
	}
}
