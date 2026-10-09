package com.jadchallenge;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("jadchallenge")
public interface JadChallengeConfig extends Config
{
	@ConfigItem(
		keyName = "highlightPrayerTab",
		name = "Highlight prayer tab",
		description = "Highlight the overhead prayer in the open prayer book",
		position = 1
	)
	default boolean highlightPrayerTab()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showQueue",
		name = "Show queue",
		description = "Show the queued overhead prayers, oldest on the left",
		position = 2
	)
	default boolean showQueue()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showInfoboxTicks",
		name = "Show tick countdown on infobox",
		description = "Show the ticks remaining on the front queue box",
		position = 3
	)
	default boolean showInfoboxTicks()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showPrayerTicks",
		name = "Show tick countdown on prayer tab",
		description = "Show the ticks remaining on the highlighted prayer",
		position = 4
	)
	default boolean showPrayerTicks()
	{
		return true;
	}

	@ConfigItem(
		keyName = "autoPray",
		name = "Auto pray",
		description = "Turn on the queued overhead one tick before that attack lands",
		warning = "This clicks overheads for you.",
		position = 5
	)
	default boolean autoPray()
	{
		return false;
	}
}
