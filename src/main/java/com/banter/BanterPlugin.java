package com.banter;

import com.google.inject.Provides;
import java.text.NumberFormat;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Banter",
	description = "Takes the piss out of you for dying, burning food, going AFK and generally being you",
	tags = {"roast", "fun", "joke", "humour", "insult", "banter"}
)
public class BanterPlugin extends Plugin
{
	// Overhead text duration on your own head, in client cycles (20ms each), when the heckler isn't summoned
	private static final int OVERHEAD_CYCLES = 250;
	// Client idle ticks are 20ms each
	private static final int IDLE_TICKS_PER_MINUTE = 50 * 60;
	private static final double GAME_TICK_SECONDS = 0.6;

	@Inject
	private Client client;

	@Inject
	private BanterConfig config;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private Heckler heckler;

	@Inject
	private HecklerOverlay hecklerOverlay;

	private final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
	private final Map<String[], Integer> lastPicked = new HashMap<>();

	private int tickCount;
	private int lastRoastTick = Integer.MIN_VALUE / 2;
	private boolean lowHpRoasted;
	private boolean idleRoasted;
	private boolean greetPending;
	private boolean hopped;
	private int bankOpens;
	private int clicks;

	@Override
	protected void startUp()
	{
		reset();
		greetPending = client.getGameState() == GameState.LOGGED_IN;
		overlayManager.add(hecklerOverlay);
	}

	@Override
	protected void shutDown()
	{
		reset();
		overlayManager.remove(hecklerOverlay);
		clientThread.invoke(heckler::despawn);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals(BanterConfig.GROUP) && !config.summonHeckler())
		{
			clientThread.invoke(heckler::despawn);
		}
	}

	private void reset()
	{
		levels.clear();
		lastPicked.clear();
		lowHpRoasted = false;
		idleRoasted = false;
		greetPending = false;
		hopped = false;
		bankOpens = 0;
		clicks = 0;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGIN_SCREEN:
				reset();
				greetPending = true;
				heckler.sceneChanged();
				break;
			case HOPPING:
				levels.clear();
				hopped = true;
				heckler.sceneChanged();
				break;
			case LOADING:
				heckler.sceneChanged();
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		tickCount++;

		if (config.summonHeckler() && !heckler.isSpawned())
		{
			heckler.spawn();
		}
		heckler.onGameTick();

		if (greetPending && client.getTotalLevel() > 0)
		{
			greetPending = false;
			if (config.login())
			{
				int total = client.getTotalLevel();
				String[] pool = total < 500 ? Roasts.LOGIN_LOW : total < 1750 ? Roasts.LOGIN_MID : Roasts.LOGIN_HIGH;
				roast(pick(pool).replace("{total}", String.valueOf(total)), true, AnimationID.EMOTE_WAVE);
			}
		}

		if (hopped)
		{
			hopped = false;
			if (config.hop())
			{
				roast(pick(Roasts.HOP), false, AnimationID.EMOTE_WAVE);
			}
		}

		if (config.idle())
		{
			int idleTicks = Math.min(client.getMouseIdleTicks(), client.getKeyboardIdleTicks());
			if (idleTicks >= config.idleMinutes() * IDLE_TICKS_PER_MINUTE)
			{
				if (!idleRoasted)
				{
					idleRoasted = true;
					roast(pick(Roasts.IDLE), true, AnimationID.EMOTE_YAWN);
				}
			}
			else
			{
				idleRoasted = false;
			}
		}
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		heckler.onClientTick();
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (config.death() && event.getActor() == client.getLocalPlayer())
		{
			roast(pick(Roasts.DEATH), true, AnimationID.EMOTE_LAUGH);
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		Skill skill = event.getSkill();

		if (skill == Skill.HITPOINTS && config.lowHp())
		{
			int max = event.getLevel();
			int hp = event.getBoostedLevel();
			boolean low = max > 0 && hp > 0 && hp * 100 < max * config.lowHpThreshold();
			if (low && !lowHpRoasted)
			{
				lowHpRoasted = roast(pick(Roasts.LOW_HP).replace("{hp}", String.valueOf(hp)), false, AnimationID.EMOTE_PANIC);
			}
			else if (!low)
			{
				lowHpRoasted = false;
			}
		}

		Integer previous = levels.put(skill, event.getLevel());
		if (previous == null || event.getLevel() <= previous || !config.levelUp())
		{
			return;
		}

		int level = event.getLevel();
		int xpLeft = Math.max(0, Experience.getXpForLevel(Experience.MAX_REAL_LEVEL) - event.getXp());
		String line = pick(level >= Experience.MAX_REAL_LEVEL ? Roasts.LEVEL_99 : Roasts.LEVEL_UP)
			.replace("{skill}", skill.getName())
			.replace("{level}", String.valueOf(level))
			.replace("{xpLeft}", NumberFormat.getIntegerInstance().format(xpLeft));
		roast(line, true, AnimationID.EMOTE_CLAP);
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.SPAM && event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		String message = event.getMessage();
		if (config.burn() && message.startsWith("You accidentally burn"))
		{
			roast(pick(Roasts.BURN), false, AnimationID.EMOTE_NO);
		}
		else if (config.pickpocket() && message.startsWith("You fail to pick"))
		{
			roast(pick(Roasts.PICKPOCKET_FAIL), false, AnimationID.EMOTE_LAUGH);
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (config.wilderness() && event.getVarbitId() == VarbitID.INSIDE_WILDERNESS && event.getValue() == 1)
		{
			roast(pick(Roasts.WILDERNESS), false, AnimationID.EMOTE_SHRUG);
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() != InterfaceID.BANKMAIN)
		{
			return;
		}

		bankOpens++;
		if (config.bank() && bankOpens % config.bankEvery() == 0)
		{
			roast(pick(Roasts.BANK).replace("{count}", String.valueOf(bankOpens)), false, AnimationID.EMOTE_THINK);
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		clicks++;
		if (config.clicks() && clicks % config.clicksEvery() == 0)
		{
			roast(pick(Roasts.CLICKS).replace("{count}", NumberFormat.getIntegerInstance().format(clicks)), false, AnimationID.EMOTE_YA_BOO_SUCKS);
		}
	}

	/**
	 * Delivers a roast, with an emote from the heckler. Returns false if it was swallowed by the cooldown.
	 */
	private boolean roast(String text, boolean ignoreCooldown, int emote)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return false;
		}

		int cooldownTicks = (int) Math.ceil(config.cooldown() / GAME_TICK_SECONDS);
		if (!ignoreCooldown && tickCount - lastRoastTick < cooldownTicks)
		{
			return false;
		}
		lastRoastTick = tickCount;
		log.debug("Roasting: {}", text);

		BanterConfig.Delivery delivery = config.delivery();
		if (heckler.isSpawned())
		{
			heckler.emote(emote);
		}

		if (delivery != BanterConfig.Delivery.CHATBOX)
		{
			Player player = client.getLocalPlayer();
			if (heckler.isSpawned())
			{
				heckler.say(text);
			}
			else if (player != null)
			{
				player.setOverheadText(text);
				player.setOverheadCycle(OVERHEAD_CYCLES);
			}
		}

		if (delivery != BanterConfig.Delivery.OVERHEAD)
		{
			String message = new ChatMessageBuilder()
				.append(ChatColorType.HIGHLIGHT)
				.append("[" + config.hecklerName() + "] ")
				.append(ChatColorType.NORMAL)
				.append(text)
				.build();

			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.CONSOLE)
				.runeLiteFormattedMessage(message)
				.build());
		}

		return true;
	}

	/**
	 * Picks a random line from the pool, avoiding the one used last time.
	 */
	private String pick(String[] pool)
	{
		int last = lastPicked.getOrDefault(pool, -1);
		int index = ThreadLocalRandom.current().nextInt(pool.length);
		if (index == last && pool.length > 1)
		{
			index = (index + 1) % pool.length;
		}
		lastPicked.put(pool, index);
		return pool[index];
	}

	@Provides
	BanterConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BanterConfig.class);
	}
}
