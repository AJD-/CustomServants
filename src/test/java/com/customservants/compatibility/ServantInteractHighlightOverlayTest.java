/*
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
import net.runelite.api.MenuAction;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The pure parts of the Interact Highlight NPC outline overlay: the click flash, which open-menu
 * option is under the mouse, and which menu actions target an NPC.
 */
public class ServantInteractHighlightOverlayTest
{
	private static final Color START = new Color(0, 50, 100, 100);
	private static final Color END = new Color(200, 100, 50, 0);

	// Color(int) ignores the top byte, so the click flash is opaque white, as in Interact Highlight
	private static final Color CLICK = new Color(0x90ffffff);

	// An open menu of four options at (100, 200), 120 px wide: header 200-218, rows of 15 from 219
	private static final int MENU_X = 100;
	private static final int MENU_Y = 200;
	private static final int MENU_WIDTH = 120;
	private static final int ENTRIES = 4;
	private static final int INSIDE_X = 150;

	@Test
	public void clickColorStartsAtTheStartColor()
	{
		assertEquals(START, ServantInteractHighlightOverlay.clickColor(START, END, 0));
	}

	@Test
	public void clickColorIsTheWhiteFlashAtFiveCycles()
	{
		Color flash = ServantInteractHighlightOverlay.clickColor(START, END, 5);
		assertEquals(CLICK, flash);
		assertEquals(255, flash.getRed());
		assertEquals(255, flash.getGreen());
		assertEquals(255, flash.getBlue());
		assertEquals(255, flash.getAlpha());
	}

	@Test
	public void clickColorHoldsTheEndColorFromTenCycles()
	{
		assertSame(END, ServantInteractHighlightOverlay.clickColor(START, END, 10));
		assertSame(END, ServantInteractHighlightOverlay.clickColor(START, END, 500));
	}

	@Test
	public void clickColorInterpolatesTowardTheFlash()
	{
		// Two cycles in: 40% of the way from START to white
		assertEquals(new Color(102, 132, 162, 162), ServantInteractHighlightOverlay.clickColor(START, END, 2));
	}

	@Test
	public void clickColorInterpolatesFromTheFlashTowardTheEnd()
	{
		// Seven cycles in: 40% of the way from white to END
		assertEquals(new Color(233, 193, 173, 153), ServantInteractHighlightOverlay.clickColor(START, END, 7));
	}

	@Test
	public void hoveredIndexIsNoneOnTheHeader()
	{
		assertEquals(-1, hovered(INSIDE_X, MENU_Y + 10));
		assertEquals(-1, hovered(INSIDE_X, MENU_Y + 18));
	}

	@Test
	public void hoveredIndexCountsRowsFromTheLastEntry()
	{
		// The top row is the last entry
		assertEquals(3, hovered(INSIDE_X, MENU_Y + 19));
		assertEquals(3, hovered(INSIDE_X, MENU_Y + 19 + 14));
		// Row 2
		assertEquals(1, hovered(INSIDE_X, MENU_Y + 19 + 2 * 15 + 7));
		// The bottom row is the first entry
		assertEquals(0, hovered(INSIDE_X, MENU_Y + 19 + 3 * 15 + 14));
	}

	@Test
	public void hoveredIndexIsNoneOutsideTheMenuHorizontally()
	{
		int y = MENU_Y + 19 + 7;
		assertEquals(-1, hovered(MENU_X, y));
		assertEquals(-1, hovered(MENU_X - 30, y));
		assertEquals(-1, hovered(MENU_X + MENU_WIDTH, y));
		assertEquals(-1, hovered(MENU_X + MENU_WIDTH + 30, y));
	}

	@Test
	public void hoveredIndexIsNoneBeyondTheLastRow()
	{
		assertEquals(-1, hovered(INSIDE_X, MENU_Y + 19 + 4 * 15));
		assertEquals(-1, hovered(INSIDE_X, MENU_Y + 19 + 10 * 15));
	}

	@Test
	public void npcActionsAreTheOnesThatTargetAnNpc()
	{
		MenuAction[] npc = {
			MenuAction.NPC_FIRST_OPTION,
			MenuAction.NPC_SECOND_OPTION,
			MenuAction.NPC_THIRD_OPTION,
			MenuAction.NPC_FOURTH_OPTION,
			MenuAction.NPC_FIFTH_OPTION,
			MenuAction.WIDGET_TARGET_ON_NPC,
			MenuAction.EXAMINE_NPC,
		};
		for (MenuAction action : npc)
		{
			assertTrue(action.name(), ServantInteractHighlightOverlay.isNpcAction(action));
		}

		MenuAction[] other = {
			MenuAction.WALK,
			MenuAction.CC_OP,
			MenuAction.PLAYER_FIRST_OPTION,
			MenuAction.GAME_OBJECT_FIRST_OPTION,
		};
		for (MenuAction action : other)
		{
			assertFalse(action.name(), ServantInteractHighlightOverlay.isNpcAction(action));
		}
	}

	private static int hovered(int mouseX, int mouseY)
	{
		return ServantInteractHighlightOverlay.hoveredIndex(MENU_X, MENU_Y, MENU_WIDTH, mouseX, mouseY, ENTRIES);
	}
}
