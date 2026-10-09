package com.jadchallenge;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Prayer;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.TextComponent;

class JadChallengeQueueOverlay extends Overlay
{
	private static final int PAD_X = 14;
	private static final int PAD_Y = 10;
	private static final int GAP = 6;
	private static final Color ORANGE = new Color(255, 140, 0);
	private static final Color MAGIC = new Color(30, 120, 255);
	private static final Color RANGED = new Color(0, 180, 60);

	private final Client client;
	private final JadChallengePlugin plugin;
	private final JadChallengeConfig config;

	@Inject
	private JadChallengeQueueOverlay(Client client, JadChallengePlugin plugin, JadChallengeConfig config)
	{
		super(plugin);
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		// Above widgets so the row is not clipped away in fixed mode.
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGH);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (client == null || plugin == null || config == null
			|| client.getGameState() != GameState.LOGGED_IN
			|| !config.showQueue())
		{
			return null;
		}

		List<QueuedAttack> attacks = plugin.getQueue();
		if (attacks == null || attacks.isEmpty())
		{
			return null;
		}

		graphics.setFont(FontManager.getRunescapeFont());
		FontMetrics metrics = graphics.getFontMetrics();
		int boxWidth = boxWidth(metrics);
		int boxHeight = metrics.getHeight() + PAD_Y;
		Stroke previous = graphics.getStroke();
		int x = 0;
		int drawn = 0;
		for (int i = 0; i < attacks.size(); i++)
		{
			QueuedAttack attack = attacks.get(i);
			if (attack == null || attack.prayer == null)
			{
				continue;
			}

			int ticks = plugin.ticksRemaining(attack);
			if (ticks <= 0)
			{
				continue;
			}

			boolean now = drawn == 0;
			boolean urgent = ticks == 1;
			Color style = attack.prayer == Prayer.PROTECT_FROM_MAGIC ? MAGIC : RANGED;
			String text = textFor(attack.prayer, ticks);
			drawBox(graphics, x, boxWidth, boxHeight, style, urgent, now);
			drawBoxText(graphics, metrics, x, boxWidth, boxHeight, text, urgent);
			x += boxWidth + GAP;
			drawn++;
		}
		graphics.setStroke(previous);

		if (drawn == 0)
		{
			return null;
		}
		return new Dimension((drawn * boxWidth) + ((drawn - 1) * GAP), boxHeight);
	}

	private int boxWidth(FontMetrics metrics)
	{
		int labelWidth = Math.max(metrics.stringWidth("Mage"), metrics.stringWidth("Range"));
		if (config.showInfoboxTicks())
		{
			labelWidth = Math.max(metrics.stringWidth("Mage 3"), metrics.stringWidth("Range 3"));
		}
		return labelWidth + (PAD_X * 2);
	}

	private void drawBox(Graphics2D graphics, int x, int boxWidth, int boxHeight, Color style, boolean urgent, boolean now)
	{
		Color fill = new Color(style.getRed(), style.getGreen(), style.getBlue(), now ? 170 : 110);
		graphics.setColor(fill);
		graphics.fillRoundRect(x, 0, boxWidth, boxHeight, 8, 8);
		graphics.setColor(urgent ? ORANGE : style);
		graphics.setStroke(new BasicStroke(now ? 2f : 1f));
		graphics.drawRoundRect(x, 0, boxWidth - 1, boxHeight - 1, 8, 8);
	}

	private void drawBoxText(Graphics2D graphics, FontMetrics metrics, int x, int boxWidth, int boxHeight, String text, boolean urgent)
	{
		int textX = x + (boxWidth - metrics.stringWidth(text)) / 2;
		int textY = ((boxHeight - metrics.getHeight()) / 2) + metrics.getAscent();

		TextComponent component = new TextComponent();
		component.setText(text);
		component.setOutline(true);
		component.setColor(urgent ? ORANGE : Color.WHITE);
		component.setPosition(textX, textY);
		component.render(graphics);
	}

	private String textFor(Prayer prayer, int ticks)
	{
		String name = prayer == Prayer.PROTECT_FROM_MAGIC ? "Mage" : "Range";
		if (!config.showInfoboxTicks())
		{
			return name;
		}
		return name + " " + ticks;
	}
}
