package com.banter;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Draws the heckler's lines above his head, like real overhead chat.
 */
class HecklerOverlay extends Overlay
{
	private final Client client;
	private final Heckler heckler;

	@Inject
	HecklerOverlay(Client client, Heckler heckler)
	{
		this.client = client;
		this.heckler = heckler;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		String text = heckler.getCurrentText();
		LocalPoint location = heckler.getLocation();
		if (text == null || location == null)
		{
			return null;
		}

		graphics.setFont(FontManager.getRunescapeBoldFont());
		Point point = Perspective.getCanvasTextLocation(client, graphics, location, text, Heckler.TEXT_Z_OFFSET);
		if (point != null)
		{
			OverlayUtil.renderTextLocation(graphics, point, text, Color.YELLOW);
		}
		return null;
	}
}
