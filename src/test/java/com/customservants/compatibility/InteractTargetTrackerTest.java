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

import com.google.inject.Guice;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Which actor the local player is interacting with, as Interact Highlight tracks it, and which
 * NPC clicks count as attacks.
 */
public class InteractTargetTrackerTest
{
	private final Client client = mock(Client.class);
	private final NPC npc = mock(NPC.class);

	private InteractTargetTracker tracker;

	@Before
	public void setUp()
	{
		tracker = Guice.createInjector(binder -> binder.bind(Client.class).toInstance(client))
			.getInstance(InteractTargetTracker.class);
	}

	private static Widget spellbookWidget()
	{
		Widget widget = mock(Widget.class);
		when(widget.getId()).thenReturn(WidgetUtil.packComponentId(InterfaceID.MAGIC_SPELLBOOK, 0));
		return widget;
	}

	private void click(MenuAction action, NPC target)
	{
		MenuEntry entry = mock(MenuEntry.class);
		when(entry.getType()).thenReturn(action);
		when(entry.getNpc()).thenReturn(target);
		tracker.onMenuOptionClicked(new MenuOptionClicked(entry));
	}

	@Test
	public void theAttackOptionIsAnAttack()
	{
		assertTrue(InteractTargetTracker.isAttack(client, MenuAction.NPC_SECOND_OPTION));
	}

	@Test
	public void aSpellFromTheSpellbookIsAnAttack()
	{
		Widget spell = spellbookWidget();
		when(client.getSelectedWidget()).thenReturn(spell);
		assertTrue(InteractTargetTracker.isAttack(client, MenuAction.WIDGET_TARGET_ON_NPC));
	}

	@Test
	public void anItemUsedOnAnNpcIsNotAnAttack()
	{
		Widget item = mock(Widget.class);
		when(item.getId()).thenReturn(WidgetUtil.packComponentId(InterfaceID.INVENTORY, 0));
		when(client.getSelectedWidget()).thenReturn(item);
		assertFalse(InteractTargetTracker.isAttack(client, MenuAction.WIDGET_TARGET_ON_NPC));
	}

	@Test
	public void otherNpcOptionsAreNotAttacks()
	{
		// Even with a spell selected, only a target-on-NPC cast counts
		Widget spell = spellbookWidget();
		when(client.getSelectedWidget()).thenReturn(spell);
		assertFalse(InteractTargetTracker.isAttack(client, MenuAction.NPC_FIRST_OPTION));
	}

	@Test
	public void clickingAnNpcMakesItTheTarget()
	{
		when(client.getTickCount()).thenReturn(10);
		when(client.getGameCycle()).thenReturn(1234);

		click(MenuAction.NPC_FIRST_OPTION, npc);

		assertSame(npc, tracker.getInteractedTarget());
		assertFalse(tracker.isAttacked());
		assertEquals(1234, tracker.getGameCycle());
	}

	@Test
	public void attackingAnNpcMarksItAttacked()
	{
		click(MenuAction.NPC_SECOND_OPTION, npc);

		assertSame(npc, tracker.getInteractedTarget());
		assertTrue(tracker.isAttacked());
	}

	@Test
	public void walkingClearsTheTarget()
	{
		click(MenuAction.NPC_FIRST_OPTION, npc);
		click(MenuAction.WALK, null);

		assertNull(tracker.getInteractedTarget());
	}

	@Test
	public void theTargetDespawningClearsIt()
	{
		click(MenuAction.NPC_FIRST_OPTION, npc);

		tracker.onNpcDespawned(new NpcDespawned(mock(NPC.class)));
		assertSame(npc, tracker.getInteractedTarget());

		tracker.onNpcDespawned(new NpcDespawned(npc));
		assertNull(tracker.getInteractedTarget());
	}

	@Test
	public void reachingTheDestinationClearsTheTarget()
	{
		when(client.getTickCount()).thenReturn(10);
		click(MenuAction.NPC_FIRST_OPTION, npc);

		// Still walking on a later tick
		when(client.getTickCount()).thenReturn(11);
		when(client.getLocalDestinationLocation()).thenReturn(new LocalPoint(64, 64, -1));
		tracker.onGameTick(new GameTick());
		assertSame(npc, tracker.getInteractedTarget());

		// Arrived, but on the tick of the click itself
		when(client.getTickCount()).thenReturn(10);
		when(client.getLocalDestinationLocation()).thenReturn(null);
		tracker.onGameTick(new GameTick());
		assertSame(npc, tracker.getInteractedTarget());

		when(client.getTickCount()).thenReturn(11);
		tracker.onGameTick(new GameTick());
		assertNull(tracker.getInteractedTarget());
	}

	@Test
	public void withoutAClickItFallsBackToWhatThePlayerIsInteractingWith()
	{
		assertNull(tracker.getInteractedTarget());

		Player local = mock(Player.class);
		when(local.getInteracting()).thenReturn(npc);
		when(client.getLocalPlayer()).thenReturn(local);

		assertSame(npc, tracker.getInteractedTarget());
	}
}
