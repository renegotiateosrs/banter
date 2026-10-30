package com.banter;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.AnimationController;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;

/**
 * A client-side tramp who follows you around and judges you, despite his own life choices.
 * Only visible to you.
 */
@Singleton
class Heckler
{
	private static final int ANIM_READY = AnimationID.HUMAN_READY;
	private static final int ANIM_WALK = AnimationID.HUMAN_WALK_F;
	private static final int ANIM_RUN = AnimationID.HUMAN_RUNNING;

	private static final int TILE = 128;
	// One tile per game tick (600ms) is 30 client ticks (20ms)
	private static final double WALK_SPEED = TILE / 30.0;
	private static final double RUN_SPEED = WALK_SPEED * 2;
	private static final int SNAP_DISTANCE = TILE * 12;
	private static final int TURN_SPEED = 64;
	static final int TEXT_Z_OFFSET = 240;

	@Inject
	private Client client;

	private RuneLiteObject object;
	private double x;
	private double y;
	private int plane;
	private int pose = -1;
	private boolean needsSnap = true;

	private WorldPoint target;
	private WorldPoint lastPlayerTile;

	private String text;
	private int textExpiresCycle;

	boolean isSpawned()
	{
		return object != null;
	}

	/**
	 * Must be called on the client thread.
	 */
	void spawn()
	{
		if (object != null)
		{
			return;
		}

		// Borrow the look of the tramp NPC, rags and all
		NPCComposition tramp = client.getNpcDefinition(NpcID.TRAMP);
		int[] modelIds = tramp.getModels();
		ModelData[] parts = new ModelData[modelIds.length];
		for (int i = 0; i < modelIds.length; i++)
		{
			parts[i] = client.loadModelData(modelIds[i]);
			if (parts[i] == null)
			{
				return;
			}
		}

		ModelData merged = client.mergeModels(parts).cloneColors();
		short[] from = tramp.getColorToReplace();
		short[] to = tramp.getColorToReplaceWith();
		if (from != null && to != null)
		{
			for (int i = 0; i < from.length; i++)
			{
				merged.recolor(from[i], to[i]);
			}
		}

		// Standard NPC model lighting
		Model model = merged.light(64, 850, -30, -50, -30);
		object = client.createRuneLiteObject();
		object.setModel(model);
		pose = -1;
		setPose(ANIM_READY);
		needsSnap = true;
	}

	/**
	 * Must be called on the client thread.
	 */
	void despawn()
	{
		if (object != null)
		{
			object.setActive(false);
			object = null;
		}
		text = null;
		target = null;
		lastPlayerTile = null;
	}

	/**
	 * The scene is being rebuilt, so local coordinates are about to change.
	 */
	void sceneChanged()
	{
		needsSnap = true;
		lastPlayerTile = null;
		target = null;
	}

	void say(String text)
	{
		this.text = text;
		textExpiresCycle = client.getGameCycle() + 150 + text.length() * 3;
	}

	void emote(int animationId)
	{
		if (object == null)
		{
			return;
		}

		AnimationController emote = new AnimationController(client, animationId);
		emote.setOnFinished(ac -> object.setAnimationController(null));
		object.setAnimationController(emote);
	}

	LocalPoint getLocation()
	{
		return object != null && object.isActive() ? object.getLocation() : null;
	}

	String getCurrentText()
	{
		if (text != null && client.getGameCycle() > textExpiresCycle)
		{
			text = null;
		}
		return text;
	}

	void onGameTick()
	{
		Player player = client.getLocalPlayer();
		if (object == null || player == null)
		{
			return;
		}

		// Trail one tile behind: always head for where the player just was
		WorldPoint here = player.getWorldLocation();
		if (lastPlayerTile != null && !lastPlayerTile.equals(here))
		{
			target = lastPlayerTile;
		}
		lastPlayerTile = here;

		if (target == null)
		{
			target = here.dx(-1);
		}

		LocalPoint targetLocal = LocalPoint.fromWorld(client.getTopLevelWorldView(), target);
		if (targetLocal == null)
		{
			return;
		}

		double dx = targetLocal.getX() - x;
		double dy = targetLocal.getY() - y;
		if (needsSnap || target.getPlane() != plane || Math.hypot(dx, dy) > SNAP_DISTANCE)
		{
			x = targetLocal.getX();
			y = targetLocal.getY();
			plane = target.getPlane();
			needsSnap = false;
			place();
		}
	}

	void onClientTick()
	{
		Player player = client.getLocalPlayer();
		if (object == null || player == null || target == null || needsSnap)
		{
			return;
		}

		WorldView wv = client.getTopLevelWorldView();
		LocalPoint targetLocal = LocalPoint.fromWorld(wv, target);
		if (targetLocal == null)
		{
			return;
		}

		double dx = targetLocal.getX() - x;
		double dy = targetLocal.getY() - y;
		double distance = Math.hypot(dx, dy);
		int facing;

		if (distance > 1)
		{
			// Run if he's falling behind, like a real follower
			double speed = distance > TILE * 1.5 ? RUN_SPEED : WALK_SPEED;
			double step = Math.min(speed, distance);
			x += dx / distance * step;
			y += dy / distance * step;
			setPose(speed == RUN_SPEED ? ANIM_RUN : ANIM_WALK);
			facing = orientation(dx, dy);
		}
		else
		{
			setPose(ANIM_READY);
			LocalPoint playerLocal = player.getLocalLocation();
			facing = orientation(playerLocal.getX() - x, playerLocal.getY() - y);
		}

		object.setOrientation(turnTowards(object.getOrientation(), facing));
		place();
	}

	private void place()
	{
		object.setLocation(new LocalPoint((int) x, (int) y, client.getTopLevelWorldView()), plane);
		if (!object.isActive())
		{
			object.setActive(true);
		}
	}

	private void setPose(int animationId)
	{
		if (pose != animationId)
		{
			pose = animationId;
			object.setPoseAnimationController(new AnimationController(client, animationId));
		}
	}

	/**
	 * Jagex orientation: 0 is south, 512 west, 1024 north, 1536 east.
	 */
	private static int orientation(double dx, double dy)
	{
		return (int) Math.round(Math.atan2(-dx, -dy) * 1024 / Math.PI) & 2047;
	}

	private static int turnTowards(int current, int desired)
	{
		int diff = ((desired - current + 3072) & 2047) - 1024;
		if (Math.abs(diff) <= TURN_SPEED)
		{
			return desired;
		}
		return (current + Integer.signum(diff) * TURN_SPEED) & 2047;
	}
}
