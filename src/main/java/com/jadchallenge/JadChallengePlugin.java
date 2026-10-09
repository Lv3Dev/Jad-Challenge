package com.jadchallenge;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Jad Challenge",
	description = "Queues attacks in TzHaar-Ket-Rak's Jad challenges and highlights the overhead prayer to flick",
	tags = {"jad", "prayer", "challenge"}
)
public class JadChallengePlugin extends Plugin
{
	// The animation tick counts as 1. The overhead has to already be on three ticks
	// later, so the box shows 3, then 2, then 1. It leaves once that attack should have landed.
	static final int PRAYER_TICKS = 3;
	private static final int FIGHT_CAVES_REGION = 9551;

	@Inject
	private Client client;

	@Inject
	private JadChallengeConfig config;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	private JadChallengePrayerOverlay prayerOverlay;

	@Inject
	private JadChallengeQueueOverlay queueOverlay;

	private final List<QueuedAttack> queue = new ArrayList<>();
	private final Set<NPC> trackedJads = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Set<NPC> meleeJads = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Map<Prayer, BufferedImage> sprites = new EnumMap<>(Prayer.class);

	private int challengeRegionId = -1;
	private boolean running;

	@Override
	protected void startUp()
	{
		running = true;
		overlayManager.add(prayerOverlay);
		overlayManager.add(queueOverlay);
		// startUp runs on the Swing thread. Scanning NPCs touches the client and has to wait.
		clientThread.invokeLater(this::scanIfRunning);
		log.debug("Jad Challenge started");
	}

	@Override
	protected void shutDown()
	{
		running = false;
		overlayManager.remove(prayerOverlay);
		overlayManager.remove(queueOverlay);
		clearFight();
		sprites.clear();
		log.debug("Jad Challenge stopped");
	}

	@Provides
	JadChallengeConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(JadChallengeConfig.class);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			scanNpcs();
			return;
		}

		if (event.getGameState() == GameState.LOADING
			|| event.getGameState() == GameState.HOPPING
			|| event.getGameState() == GameState.LOGIN_SCREEN
			|| event.getGameState() == GameState.CONNECTION_LOST)
		{
			clearFight();
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		NPC npc = event.getNpc();
		if (npc == null || !isChallengeJad(npc))
		{
			return;
		}

		track(npc);
		handleAnimation(npc, npc.getAnimation());
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		NPC npc = event.getNpc();
		if (npc == null || !trackedJads.remove(npc))
		{
			return;
		}

		meleeJads.remove(npc);
		if (trackedJads.isEmpty())
		{
			clearFight();
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Actor actor = event.getActor();
		if (!(actor instanceof NPC))
		{
			return;
		}

		NPC npc = (NPC) actor;
		if (!trackedJads.contains(npc))
		{
			if (!isChallengeJad(npc))
			{
				return;
			}
			track(npc);
		}

		handleAnimation(npc, npc.getAnimation());
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (client == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		if (hasLeftChallenge())
		{
			clearFight();
			return;
		}

		meleeJads.removeIf(npc -> npc == null || !isMeleeAnimation(npc.getAnimation()));
		advanceQueue();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!running || config == null || !config.autoPray())
		{
			return;
		}

		if (client == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		// The countdown shows 1 on this tick. The click is applied next tick, when the attack lands.
		Prayer prayer = prayerDueThisTick();
		if (prayer == null || client.getVarbitValue(prayer.getVarbit()) == 1)
		{
			return;
		}

		int componentId = prayerComponentId(prayer);
		Widget widget = client.getWidget(componentId);
		if (widget == null)
		{
			log.debug("Auto pray skipped {}, prayer book is not open", prayer);
			return;
		}

		client.menuAction(-1, componentId, MenuAction.CC_OP, 1, -1, "Activate", prayerTarget(prayer));
		log.debug("Auto prayed {}", prayer);
	}

	List<QueuedAttack> getQueue()
	{
		return queue;
	}

	boolean isMeleeActive()
	{
		return !meleeJads.isEmpty();
	}

	int ticksRemaining(QueuedAttack attack)
	{
		if (attack == null || client == null)
		{
			return 0;
		}

		int elapsed = client.getTickCount() - attack.startTick;
		return Math.max(0, PRAYER_TICKS - elapsed);
	}

	BufferedImage getPrayerSprite(Prayer prayer)
	{
		if (prayer == null || spriteManager == null)
		{
			return null;
		}

		BufferedImage sprite = sprites.get(prayer);
		if (sprite != null)
		{
			return sprite;
		}

		sprite = spriteManager.getSprite(spriteId(prayer), 0);
		if (sprite != null)
		{
			sprites.put(prayer, sprite);
		}
		return sprite;
	}

	private void scanIfRunning()
	{
		if (!running || client == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		scanNpcs();
	}

	private void scanNpcs()
	{
		if (!running || client == null)
		{
			return;
		}

		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return;
		}

		WorldView worldView = player.getWorldView();
		if (worldView == null)
		{
			return;
		}

		for (NPC npc : worldView.npcs())
		{
			if (!running)
			{
				return;
			}

			if (npc != null && isChallengeJad(npc))
			{
				track(npc);
				handleAnimation(npc, npc.getAnimation());
			}
		}
	}

	private void track(NPC npc)
	{
		if (trackedJads.add(npc))
		{
			rememberArea();
		}
	}

	private void handleAnimation(NPC npc, int animation)
	{
		if (client == null || npc == null)
		{
			return;
		}

		if (isMeleeAnimation(animation))
		{
			meleeJads.add(npc);
			return;
		}

		meleeJads.remove(npc);
		Prayer prayer = queuedPrayer(animation);
		if (prayer == null || hasPendingAttack(npc))
		{
			return;
		}

		queue.add(new QueuedAttack(prayer, npc, client.getTickCount()));
		log.debug("Queued {} from npc {}", prayer, npc.getId());
	}

	private void advanceQueue()
	{
		if (client == null || queue.isEmpty())
		{
			return;
		}

		int tick = client.getTickCount();
		queue.removeIf(attack -> attack == null || tick - attack.startTick >= PRAYER_TICKS);
	}

	private Prayer prayerDueThisTick()
	{
		Prayer due = null;
		for (QueuedAttack attack : queue)
		{
			if (attack == null || attack.prayer == null || ticksRemaining(attack) != 1)
			{
				continue;
			}

			if (due == null)
			{
				due = attack.prayer;
				continue;
			}

			if (due != attack.prayer)
			{
				log.debug("Auto pray keeps {} and skips {} due on the same tick", due, attack.prayer);
			}
		}
		return due;
	}

	private static int prayerComponentId(Prayer prayer)
	{
		if (prayer == Prayer.PROTECT_FROM_MAGIC)
		{
			return InterfaceID.Prayerbook.PRAYER13;
		}
		if (prayer == Prayer.PROTECT_FROM_MISSILES)
		{
			return InterfaceID.Prayerbook.PRAYER14;
		}
		return InterfaceID.Prayerbook.PRAYER15;
	}

	private static String prayerTarget(Prayer prayer)
	{
		if (prayer == Prayer.PROTECT_FROM_MAGIC)
		{
			return "<col=ff9040>Protect from Magic</col>";
		}
		if (prayer == Prayer.PROTECT_FROM_MISSILES)
		{
			return "<col=ff9040>Protect from Missiles</col>";
		}
		return "<col=ff9040>Protect from Melee</col>";
	}

	private boolean hasPendingAttack(NPC npc)
	{
		for (QueuedAttack attack : queue)
		{
			if (attack.npc == npc)
			{
				return true;
			}
		}
		return false;
	}

	private boolean hasLeftChallenge()
	{
		if (trackedJads.isEmpty() && queue.isEmpty() && challengeRegionId == -1)
		{
			return false;
		}

		if (client == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return true;
		}

		Player player = client.getLocalPlayer();
		if (player == null || player.getWorldView() == null || player.getLocalLocation() == null)
		{
			return true;
		}

		// A live challenge Jad keeps the queue. The arena can reuse other instance chunks.
		if (!trackedJads.isEmpty())
		{
			return false;
		}

		WorldView worldView = player.getWorldView();
		if (!worldView.isInstance())
		{
			return true;
		}

		if (challengeRegionId == -1)
		{
			return false;
		}

		int region = templateRegion(player);
		return region != -1 && region != challengeRegionId;
	}

	private void rememberArea()
	{
		if (client == null)
		{
			return;
		}

		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return;
		}

		WorldView worldView = player.getWorldView();
		if (worldView == null || !worldView.isInstance())
		{
			return;
		}

		int region = templateRegion(player);
		if (region != -1)
		{
			challengeRegionId = region;
		}
	}

	private int templateRegion(Player player)
	{
		if (client == null || player == null)
		{
			return -1;
		}

		LocalPoint local = player.getLocalLocation();
		if (local == null)
		{
			return -1;
		}

		WorldPoint point = WorldPoint.fromLocalInstance(client, local);
		if (point == null)
		{
			return -1;
		}
		return point.getRegionID();
	}

	private boolean inChallengeArea()
	{
		if (client == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return false;
		}

		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return false;
		}

		WorldView worldView = player.getWorldView();
		if (worldView == null || !worldView.isInstance())
		{
			return false;
		}

		// Ket-Rak's challenges are instanced inside the Inferno. Fight Caves is a different map.
		int region = templateRegion(player);
		if (region == FIGHT_CAVES_REGION)
		{
			return region == challengeRegionId && !trackedJads.isEmpty();
		}

		if (challengeRegionId == -1)
		{
			return true;
		}
		return region == challengeRegionId;
	}

	private boolean isChallengeJad(NPC npc)
	{
		if (npc == null)
		{
			return false;
		}

		int id = npc.getId();
		if (id == NpcID.JAD_CHALLENGE_JAD)
		{
			return true;
		}

		if (isExcludedJad(id) || !inChallengeArea())
		{
			return false;
		}

		String name = npc.getName();
		if (name == null)
		{
			return false;
		}

		String lower = name.toLowerCase();
		return lower.contains("jad") && !lower.contains("rek");
	}

	private static boolean isExcludedJad(int id)
	{
		return id == NpcID.TZHAAR_FIGHTCAVE_SWARM_BOSS
			|| id == NpcID.CLANCUP_TZHAAR_FIGHTCAVE_SWARM_BOSS
			|| id == NpcID.INFERNO_JAD
			|| id == NpcID.INFERNO_JAD_FINALWAVE
			|| id == NpcID.DEADMAN_BREACH_JAD
			|| id == NpcID.DEADMAN_BREACH_JAD_MINION
			|| id == NpcID.TZHAAR_FIGHTPIT_SWARM_BOSS
			|| id == NpcID.DEADMAN_ALL_STARS_MISSION_JAD;
	}

	private static boolean isMeleeAnimation(int animation)
	{
		return animation == AnimationID.LORDMAGMUS_ATTACK
			|| animation == AnimationID.JALTOKJAD_ATTACK_MELEE;
	}

	private static Prayer queuedPrayer(int animation)
	{
		if (animation == AnimationID.LORDMAGMUS_FIRE || animation == AnimationID.JALTOKJAD_ATTACK_MAGIC)
		{
			return Prayer.PROTECT_FROM_MAGIC;
		}
		if (animation == AnimationID.LORDMAGMUS_SMASH || animation == AnimationID.JALTOKJAD_ATTACK_RANGED)
		{
			return Prayer.PROTECT_FROM_MISSILES;
		}
		return null;
	}

	private static int spriteId(Prayer prayer)
	{
		if (prayer == Prayer.PROTECT_FROM_MAGIC)
		{
			return SpriteID.Prayeron.PROTECT_FROM_MAGIC;
		}
		if (prayer == Prayer.PROTECT_FROM_MISSILES)
		{
			return SpriteID.Prayeron.PROTECT_FROM_MISSILES;
		}
		return SpriteID.Prayeron.PROTECT_FROM_MELEE;
	}

	private void clearFight()
	{
		queue.clear();
		meleeJads.clear();
		trackedJads.clear();
		challengeRegionId = -1;
	}
}
