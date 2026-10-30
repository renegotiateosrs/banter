package com.banter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(BanterConfig.GROUP)
public interface BanterConfig extends Config
{
	String GROUP = "banter-roasts";

	enum Delivery
	{
		OVERHEAD,
		CHATBOX,
		BOTH
	}

	@ConfigSection(
		name = "Triggers",
		description = "What you'd like to get mocked for",
		position = 10
	)
	String triggers = "triggers";

	@ConfigItem(
		keyName = "hecklerName",
		name = "Heckler name",
		description = "Who's taking the piss out of you in the chatbox",
		position = 0
	)
	default String hecklerName()
	{
		return "Dave the Tramp";
	}

	@ConfigItem(
		keyName = "summonHeckler",
		name = "Summon heckler",
		description = "A tramp follows you around and delivers the roasts in person. Only you can see him.",
		position = 1
	)
	default boolean summonHeckler()
	{
		return true;
	}

	@ConfigItem(
		keyName = "delivery",
		name = "Delivery",
		description = "Overhead text (above the heckler, or above you if he's not summoned), the chatbox, or both. Only you can see either.",
		position = 2
	)
	default Delivery delivery()
	{
		return Delivery.BOTH;
	}

	@Range(min = 0, max = 600)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "cooldown",
		name = "Cooldown",
		description = "Minimum time between roasts. Deaths and level ups ignore this, because some things can't wait.",
		position = 3
	)
	default int cooldown()
	{
		return 20;
	}

	@ConfigItem(keyName = "death", name = "Dying", description = "Roast you when you die", section = triggers, position = 0)
	default boolean death()
	{
		return true;
	}

	@ConfigItem(keyName = "levelUp", name = "Levelling up", description = "Belittle your level ups", section = triggers, position = 1)
	default boolean levelUp()
	{
		return true;
	}

	@ConfigItem(keyName = "burn", name = "Burning food", description = "Roast you when you burn food", section = triggers, position = 2)
	default boolean burn()
	{
		return true;
	}

	@ConfigItem(keyName = "pickpocket", name = "Failed pickpockets", description = "Roast you when you get caught", section = triggers, position = 3)
	default boolean pickpocket()
	{
		return true;
	}

	@ConfigItem(keyName = "lowHp", name = "Low hitpoints", description = "Mock you for being nearly dead", section = triggers, position = 4)
	default boolean lowHp()
	{
		return true;
	}

	@Range(min = 1, max = 99)
	@Units(Units.PERCENT)
	@ConfigItem(keyName = "lowHpThreshold", name = "Low HP threshold", description = "Hitpoints percentage that counts as 'low'", section = triggers, position = 5)
	default int lowHpThreshold()
	{
		return 25;
	}

	@ConfigItem(keyName = "idle", name = "Going AFK", description = "Mock you for standing around", section = triggers, position = 6)
	default boolean idle()
	{
		return true;
	}

	@Range(min = 1, max = 60)
	@Units(Units.MINUTES)
	@ConfigItem(keyName = "idleMinutes", name = "AFK time", description = "How long with no input before you get called out", section = triggers, position = 7)
	default int idleMinutes()
	{
		return 3;
	}

	@ConfigItem(keyName = "wilderness", name = "Entering the Wilderness", description = "Question your life choices on entering the Wilderness", section = triggers, position = 8)
	default boolean wilderness()
	{
		return true;
	}

	@ConfigItem(keyName = "bank", name = "Banking", description = "Count your bank trips and judge you for them", section = triggers, position = 9)
	default boolean bank()
	{
		return true;
	}

	@Range(min = 1, max = 1000)
	@ConfigItem(keyName = "bankEvery", name = "Bank trips per roast", description = "Roast every this many bank opens", section = triggers, position = 10)
	default int bankEvery()
	{
		return 10;
	}

	@ConfigItem(keyName = "clicks", name = "Clicking", description = "Count your clicks and judge you for them", section = triggers, position = 11)
	default boolean clicks()
	{
		return true;
	}

	@Range(min = 10, max = 100000)
	@ConfigItem(keyName = "clicksEvery", name = "Clicks per roast", description = "Roast every this many clicks", section = triggers, position = 12)
	default int clicksEvery()
	{
		return 1000;
	}

	@ConfigItem(keyName = "hop", name = "World hopping", description = "Roast you for hopping worlds", section = triggers, position = 13)
	default boolean hop()
	{
		return true;
	}

	@ConfigItem(keyName = "login", name = "Logging in", description = "Appraise your total level on login", section = triggers, position = 14)
	default boolean login()
	{
		return true;
	}
}
