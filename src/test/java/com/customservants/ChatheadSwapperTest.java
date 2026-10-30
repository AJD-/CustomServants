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
package com.customservants;

import com.customservants.ChatheadSwapper.Head;
import com.customservants.ChatheadSwapper.Swap;
import com.customservants.ChatheadSwapper.Target;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.widgets.WidgetModelType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import org.junit.Test;

public class ChatheadSwapperTest
{
	private static final int MAID = NpcID.POH_SERVANT_WAITER_WOMAN;
	private static final int NOBLE = NpcID.MYQ4_NOBLE_2;
	private static final int OTHER_NOBLE = NpcID.TOB_NOBLE_2;
	private static final int MAID_INDEX = 42;
	private static final int EMOTE = 554;

	private static final Target BORROWED = new Target(WidgetModelType.NPC_CHATHEAD, NOBLE);
	private static final Target OTHER_BORROWED = new Target(WidgetModelType.NPC_CHATHEAD, OTHER_NOBLE);

	private static final IntUnaryOperator SINGLE = npcId -> npcId;
	private static final IntUnaryOperator MAID_AT_INDEX = index -> index == MAID_INDEX ? MAID : -1;
	private static final IntFunction<Target> NOTHING = npcId -> null;

	private static IntFunction<Target> maidWears(Target target)
	{
		return npcId -> npcId == MAID ? target : null;
	}

	private static void assertShows(Head head, int type, int id)
	{
		assertEquals(type, head.getType());
		assertEquals(id, head.getId());
	}

	@Test
	public void ownHeadIsSwappedToTheOverridesHead()
	{
		Head head = ChatheadSwapper.decide(WidgetModelType.NPC_CHATHEAD, MAID, EMOTE, null, MAID_AT_INDEX, SINGLE,
			maidWears(BORROWED));

		assertShows(head, WidgetModelType.NPC_CHATHEAD, NOBLE);
		assertEquals(new Swap(WidgetModelType.NPC_CHATHEAD, MAID, EMOTE, MAID, BORROWED), head.getShown());
	}

	@Test
	public void headByNpcIndexIsSwapped()
	{
		Head head = ChatheadSwapper.decide(WidgetModelType.NPC_INDEX_CHATHEAD, MAID_INDEX, EMOTE, null, MAID_AT_INDEX,
			SINGLE, maidWears(BORROWED));

		assertShows(head, WidgetModelType.NPC_CHATHEAD, NOBLE);
		assertEquals(new Swap(WidgetModelType.NPC_INDEX_CHATHEAD, MAID_INDEX, EMOTE, MAID, BORROWED), head.getShown());
	}

	@Test
	public void multiNpcIsSwappedAsTheNpcItShows()
	{
		int demon = NpcID.POH_SERVANT_DEMON;
		int multi = NpcID.POH_SERVANT_MULTI_DEMON;
		Head head = ChatheadSwapper.decide(WidgetModelType.NPC_CHATHEAD, multi, EMOTE, null, MAID_AT_INDEX,
			npcId -> npcId == multi ? demon : npcId, npcId -> npcId == demon ? BORROWED : null);

		assertShows(head, WidgetModelType.NPC_CHATHEAD, NOBLE);
		assertEquals(new Swap(WidgetModelType.NPC_CHATHEAD, multi, EMOTE, demon, BORROWED), head.getShown());
	}

	@Test
	public void multiNpcShowingNothingIsLeftAlone()
	{
		Head head = ChatheadSwapper.decide(WidgetModelType.NPC_CHATHEAD, MAID, EMOTE, null, MAID_AT_INDEX,
			npcId -> -1, maidWears(BORROWED));

		assertShows(head, WidgetModelType.NPC_CHATHEAD, MAID);
		assertNull(head.getShown());
	}

	@Test
	public void noTargetKeepsOwnHead()
	{
		Head head = ChatheadSwapper.decide(WidgetModelType.NPC_CHATHEAD, MAID, EMOTE, null, MAID_AT_INDEX, SINGLE,
			NOTHING);

		assertShows(head, WidgetModelType.NPC_CHATHEAD, MAID);
		assertNull(head.getShown());
	}

	@Test
	public void otherKindsOfHeadAreLeftAlone()
	{
		for (int type : new int[]{WidgetModelType.LOCAL_PLAYER_CHATHEAD, WidgetModelType.ITEM, WidgetModelType.NULL,
			WidgetModelType.MODEL})
		{
			Head head = ChatheadSwapper.decide(type, MAID, EMOTE, null, MAID_AT_INDEX, SINGLE, maidWears(BORROWED));

			assertShows(head, type, MAID);
			assertNull(head.getShown());
		}
	}

	@Test
	public void keptSwapIsTheSameInstance()
	{
		Swap swap = new Swap(WidgetModelType.NPC_CHATHEAD, MAID, EMOTE, MAID, BORROWED);

		Head head = ChatheadSwapper.decide(WidgetModelType.NPC_CHATHEAD, NOBLE, EMOTE, swap, MAID_AT_INDEX, SINGLE,
			maidWears(new Target(WidgetModelType.NPC_CHATHEAD, NOBLE)));

		assertShows(head, WidgetModelType.NPC_CHATHEAD, NOBLE);
		assertSame(swap, head.getShown());
	}

	/** The override switched off, or swapping disabled. */
	@Test
	public void revertsToTheOriginalWhenTheTargetGoes()
	{
		Swap swap = new Swap(WidgetModelType.NPC_INDEX_CHATHEAD, MAID_INDEX, EMOTE, MAID, BORROWED);

		Head head = ChatheadSwapper.decide(WidgetModelType.NPC_CHATHEAD, NOBLE, -1, swap, MAID_AT_INDEX, SINGLE,
			NOTHING);

		assertShows(head, WidgetModelType.NPC_INDEX_CHATHEAD, MAID_INDEX);
		assertNull(head.getShown());
	}

	@Test
	public void followsAChangedTarget()
	{
		Swap swap = new Swap(WidgetModelType.NPC_CHATHEAD, MAID, EMOTE, MAID, BORROWED);

		Head head = ChatheadSwapper.decide(WidgetModelType.NPC_CHATHEAD, NOBLE, -1, swap, MAID_AT_INDEX, SINGLE,
			maidWears(OTHER_BORROWED));

		assertShows(head, WidgetModelType.NPC_CHATHEAD, OTHER_NOBLE);
		assertEquals("keeps the original head and emote", new Swap(WidgetModelType.NPC_CHATHEAD, MAID, EMOTE, MAID,
			OTHER_BORROWED), head.getShown());
	}

	/** Each new page sets the servant's own head again: a fresh swap with that page's emote. */
	@Test
	public void newPageIsAFreshSwap()
	{
		int nextEmote = EMOTE + 1;
		Swap swap = new Swap(WidgetModelType.NPC_CHATHEAD, MAID, EMOTE, MAID, BORROWED);

		Head head = ChatheadSwapper.decide(WidgetModelType.NPC_CHATHEAD, MAID, nextEmote, swap, MAID_AT_INDEX, SINGLE,
			maidWears(BORROWED));

		assertShows(head, WidgetModelType.NPC_CHATHEAD, NOBLE);
		assertEquals(new Swap(WidgetModelType.NPC_CHATHEAD, MAID, nextEmote, MAID, BORROWED), head.getShown());
		assertNotSame(swap, head.getShown());
	}

	/** The dialogue moves on to another speaker without closing: that head is not touched. */
	@Test
	public void anotherSpeakersHeadIsLeftAlone()
	{
		Swap swap = new Swap(WidgetModelType.NPC_CHATHEAD, MAID, EMOTE, MAID, BORROWED);
		int other = NpcID.POH_SERVANT_DEMON;

		Head head = ChatheadSwapper.decide(WidgetModelType.NPC_CHATHEAD, other, EMOTE, swap, MAID_AT_INDEX, SINGLE,
			maidWears(BORROWED));

		assertShows(head, WidgetModelType.NPC_CHATHEAD, other);
		assertNull(head.getShown());
	}

	@Test
	public void npcWhoseHeadIsTheTargetIsLeftAlone()
	{
		Head head = ChatheadSwapper.decide(WidgetModelType.NPC_CHATHEAD, NOBLE, EMOTE, null, MAID_AT_INDEX, SINGLE,
			npcId -> BORROWED);

		assertShows(head, WidgetModelType.NPC_CHATHEAD, NOBLE);
		assertNull(head.getShown());
	}

	@Test
	public void swapRecordsTheOriginalAnimation()
	{
		Head head = ChatheadSwapper.decide(WidgetModelType.NPC_CHATHEAD, MAID, EMOTE, null, MAID_AT_INDEX, SINGLE,
			maidWears(BORROWED));

		assertEquals(EMOTE, head.getShown().getAnimation());
	}

	@Test
	public void emoteForKeepLeavesTheDialoguesEmote()
	{
		assertEquals(5, ChatheadSwapper.emoteFor(ChatheadEmote.KEEP, 5));
	}

	@Test
	public void emoteForNoneClearsIt()
	{
		assertEquals(-1, ChatheadSwapper.emoteFor(ChatheadEmote.NONE, 5));
	}
}
