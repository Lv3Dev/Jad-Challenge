package com.jadchallenge;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Prayer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.TextComponent;

class JadChallengePrayerOverlay extends Overlay
{
	private static final int FILL_ALPHA = 80;
	private static final int MELEE_FLASH_CYCLES = 10;
	private static final Color MAGIC_FILL = new Color(30, 120, 255, FILL_ALPHA);
	private static final Color MAGIC_OUTLINE = new Color(30, 120, 255);
	private static final Color RANGED_FILL = new Color(0, 180, 60, FILL_ALPHA);
	private static final Color RANGED_OUTLINE = new Color(0, 180, 60);
	private static final Color MELEE_FILL = new Color(220, 40, 40, FILL_ALPHA);
	private static final Color MELEE_OUTLINE = new Color(220, 40, 40);
	private static final Color ORANGE = new Color(255, 140, 0);

	private final Client client;
	private final JadChallengePlugin plugin;
	private final JadChallengeConfig config;

	@Inject
	private JadChallengePrayerOverlay(Client client, JadChallengePlugin plugin, JadChallengeConfig config)
	{
		super(plugin);
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGH);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (client == null || plugin == null || config == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}

		if (config.highlightPrayerTab() && plugin.isMeleeActive() && meleeFlashOn())
		{
			drawHighlight(graphics, Prayer.PROTECT_FROM_MELEE, MELEE_FILL, MELEE_OUTLINE);
		}

		QueuedAttack front = null;
		for (QueuedAttack attack : plugin.getQueue())
		{
			if (attack != null && attack.prayer != null && plugin.ticksRemaining(attack) > 0)
			{
				front = attack;
				break;
			}
		}
		if (front == null)
		{
			return null;
		}

		int ticks = plugin.ticksRemaining(front);
		boolean urgent = ticks == 1;
		if (config.highlightPrayerTab())
		{
			Color outline = urgent ? ORANGE : outlineFor(front.prayer);
			drawHighlight(graphics, front.prayer, fillFor(front.prayer), outline);
		}
		if (config.showPrayerTicks() && ticks > 0)
		{
			drawTick(graphics, front.prayer, ticks, urgent);
		}
		return null;
	}

	private boolean meleeFlashOn()
	{
		return client.getGameCycle() / MELEE_FLASH_CYCLES % 2 == 0;
	}

	private void drawHighlight(Graphics2D graphics, Prayer prayer, Color fill, Color outline)
	{
		Rectangle bounds = prayerBounds(prayer);
		if (bounds == null)
		{
			return;
		}

		Stroke previous = graphics.getStroke();
		graphics.setColor(fill);
		graphics.fill(bounds);
		graphics.setColor(outline);
		graphics.setStroke(new BasicStroke(2));
		graphics.draw(bounds);
		graphics.setStroke(previous);
	}

	private void drawTick(Graphics2D graphics, Prayer prayer, int ticks, boolean urgent)
	{
		Rectangle bounds = prayerBounds(prayer);
		if (bounds == null || ticks <= 0)
		{
			return;
		}

		String text = Integer.toString(ticks);
		graphics.setFont(FontManager.getRunescapeFont());
		FontMetrics metrics = graphics.getFontMetrics();
		int x = bounds.x + (bounds.width - metrics.stringWidth(text)) / 2;
		int y = bounds.y + ((bounds.height - metrics.getHeight()) / 2) + metrics.getAscent();

		TextComponent component = new TextComponent();
		component.setText(text);
		component.setOutline(true);
		component.setColor(urgent ? ORANGE : Color.WHITE);
		component.setPosition(x, y);
		component.render(graphics);
	}

	private Rectangle prayerBounds(Prayer prayer)
	{
		if (client == null || prayer == null)
		{
			return null;
		}

		Widget widget = client.getWidget(componentId(prayer));
		if (widget == null || widget.isHidden())
		{
			return null;
		}

		Rectangle bounds = widget.getBounds();
		if (bounds == null || bounds.width <= 0 || bounds.height <= 0)
		{
			return null;
		}
		return bounds;
	}

	private static int componentId(Prayer prayer)
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

	private static Color fillFor(Prayer prayer)
	{
		if (prayer == Prayer.PROTECT_FROM_MAGIC)
		{
			return MAGIC_FILL;
		}
		return RANGED_FILL;
	}

	private static Color outlineFor(Prayer prayer)
	{
		if (prayer == Prayer.PROTECT_FROM_MAGIC)
		{
			return MAGIC_OUTLINE;
		}
		return RANGED_OUTLINE;
	}
}
