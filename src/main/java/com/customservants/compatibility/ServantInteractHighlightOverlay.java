/*
 * Copyright (c) 2021, Adam <Adam@sigterm.info>
 * Copyright (c) 2026, AJD
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.customservants.compatibility;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Point;
import net.runelite.client.plugins.interacthighlight.InteractHighlightConfig;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;
import net.runelite.client.util.ColorUtil;

/**
 * Draws Interact Highlight's NPC outlines, so that a servant is outlined around the model on screen
 * rather than the vanilla one the API still reports.
 * <p>
 * Registered only while {@link InteractHighlightCompat} has that plugin's own NPC outlines
 * suppressed, so the two never draw at once. Every color, border width and feather value is read
 * from {@link InteractHighlightConfig}, so the result is that plugin's appearance and settings, not
 * a second set of them. NPCs a partner plugin swaps are outlined around that partner's model, and
 * NPCs nobody swaps take the ordinary path and look unchanged.
 * <p>
 * The hover and target logic is Interact Highlight's, reproduced because its own is package
 * private. Only the NPC half is here; that plugin still draws objects, ground items and players.
 * Per frame this costs one menu entry array and at most two outlines.
 */
public class ServantInteractHighlightOverlay extends Overlay
{
	private static final Color INTERACT_CLICK_COLOR = new Color(0x90ffffff);

	/** Height of the menu's Choose Option header. */
	private static final int MENU_HEADER_HEIGHT = 19;

	/** Height of each menu option. */
	private static final int MENU_ROW_HEIGHT = 15;

	private final Client client;
	private final InteractTargetTracker tracker;
	private final ServantOutliner outliner;
	private final ModelOutlineRenderer modelOutlineRenderer;
	private final InteractHighlightCompat compat;

	@Inject
	private ServantInteractHighlightOverlay(Client client,
		InteractTargetTracker tracker, ServantOutliner outliner,
		ModelOutlineRenderer modelOutlineRenderer, InteractHighlightCompat compat)
	{
		this.client = client;
		this.tracker = tracker;
		this.outliner = outliner;
		this.modelOutlineRenderer = modelOutlineRenderer;
		this.compat = compat;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(PRIORITY_HIGH);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		renderHover();
		renderTarget();
		return null;
	}

	private void renderHover()
	{
		if (!compat.npcShowHover())
		{
			return;
		}

		Menu menu = client.getMenu();
		MenuEntry[] menuEntries = menu.getMenuEntries();
		if (menuEntries.length == 0)
		{
			return;
		}

		MenuEntry entry = client.isMenuOpen() ? hoveredMenuEntry(menu, menuEntries) : menuEntries[menuEntries.length - 1];
		MenuAction menuAction = entry.getType();
		if (!isNpcAction(menuAction))
		{
			return;
		}

		NPC npc = entry.getNpc();
		if (npc == null || (npc == tracker.getInteractedTarget() && compat.npcShowInteract()))
		{
			// The target pass draws this one, and drawing both would double the outline
			return;
		}

		InteractHighlightConfig appearance = compat.config();
		Color color = InteractTargetTracker.isAttack(client, menuAction)
			? appearance.npcAttackHoverHighlightColor() : appearance.npcHoverHighlightColor();
		drawOutline(npc, color);
	}

	private void renderTarget()
	{
		if (!compat.npcShowInteract())
		{
			return;
		}

		Actor target = tracker.getInteractedTarget();
		if (!(target instanceof NPC))
		{
			return;
		}

		InteractHighlightConfig appearance = compat.config();
		Color startColor = tracker.isAttacked() ? appearance.npcAttackHoverHighlightColor() : appearance.npcHoverHighlightColor();
		Color endColor = tracker.isAttacked() ? appearance.npcAttackHighlightColor() : appearance.npcInteractHighlightColor();
		drawOutline((NPC) target, clickColor(startColor, endColor, client.getGameCycle() - tracker.getGameCycle()));
	}

	private void drawOutline(NPC npc, Color color)
	{
		InteractHighlightConfig appearance = compat.config();
		int borderWidth = appearance.borderWidth();
		int feather = appearance.outlineFeather();

		// Servant or partner geometry when this is an NPC one of us swaps, so the outline traces
		// what is on screen. Declines for anything else, which is what falls through to the
		// vanilla outline below.
		if (outliner.drawOutline(npc, borderWidth, color, feather))
		{
			return;
		}

		modelOutlineRenderer.drawOutline(npc, borderWidth, color, feather);
	}

	/**
	 * Whether a menu entry of this type targets an NPC, and so whether hovering it outlines one.
	 */
	static boolean isNpcAction(MenuAction menuAction)
	{
		switch (menuAction)
		{
			case WIDGET_TARGET_ON_NPC:
			case NPC_FIRST_OPTION:
			case NPC_SECOND_OPTION:
			case NPC_THIRD_OPTION:
			case NPC_FOURTH_OPTION:
			case NPC_FIFTH_OPTION:
			case EXAMINE_NPC:
				return true;
			default:
				return false;
		}
	}

	/**
	 * The color of a target outline {@code time} game cycles after the click: from {@code start}
	 * through a white flash to {@code end}, which it holds from 10 cycles on.
	 */
	static Color clickColor(Color start, Color end, long time)
	{
		if (time < 5)
		{
			return ColorUtil.colorLerp(start, INTERACT_CLICK_COLOR, time / 5f);
		}
		else if (time < 10)
		{
			return ColorUtil.colorLerp(INTERACT_CLICK_COLOR, end, (time - 5) / 5f);
		}
		return end;
	}

	private MenuEntry hoveredMenuEntry(final Menu menu, final MenuEntry[] menuEntries)
	{
		final Point mousePosition = client.getMouseCanvasPosition();
		int idx = hoveredIndex(menu.getMenuX(), menu.getMenuY(), menu.getMenuWidth(),
			mousePosition.getX(), mousePosition.getY(), menuEntries.length);
		return idx >= 0 ? menuEntries[idx] : menuEntries[menuEntries.length - 1];
	}

	/**
	 * The index into the menu entry array of the option under the mouse in an open menu, or -1
	 * when the mouse is on the header or outside the options. Entries are listed bottom up, so the
	 * top option is the last entry.
	 */
	static int hoveredIndex(int menuX, int menuY, int menuWidth, int mouseX, int mouseY, int entryCount)
	{
		int dy = mouseY - menuY;
		dy -= MENU_HEADER_HEIGHT;
		if (dy < 0)
		{
			return -1;
		}

		int idx = dy / MENU_ROW_HEIGHT;
		idx = entryCount - 1 - idx;

		if (mouseX > menuX && mouseX < menuX + menuWidth
			&& idx >= 0 && idx < entryCount)
		{
			return idx;
		}
		return -1;
	}
}
