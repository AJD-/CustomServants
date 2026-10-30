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

import java.util.function.Function;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * What each partner said about Interact Highlight's NPC outlines, and whose poser draws an NPC.
 */
public class PartnerOutlinesTest
{
	private static final String RETRO = ModelSwapProtocol.SOURCE_RETRO_NPC_SWAPPER;
	private static final String CNM = ModelSwapProtocol.SOURCE_CUSTOM_NPC_MODELS;

	private PartnerOutlines outlines;
	private Function<NPC, Model> retroPoser;
	private Function<NPC, Model> cnmPoser;
	private NPC npc;
	private Model retroModel;
	private Model cnmModel;

	@Before
	@SuppressWarnings("unchecked")
	public void setUp()
	{
		outlines = new PartnerOutlines();
		retroPoser = mock(Function.class);
		cnmPoser = mock(Function.class);
		npc = mock(NPC.class);
		retroModel = mock(Model.class);
		cnmModel = mock(Model.class);
	}

	private static ModelSwapProtocol.Outlines state(boolean owning, Function<NPC, Model> poser)
	{
		return new ModelSwapProtocol.Outlines(owning, poser);
	}

	@Test
	public void nobodyOwnsAtFirst()
	{
		assertFalse(outlines.isAnyOwning());
		assertFalse(outlines.isOwning(RETRO));
		assertFalse(outlines.isOwning(CNM));
		assertNull(outlines.pose(npc));
	}

	@Test
	public void anyOwningWhenRetroOwns()
	{
		outlines.accept(RETRO, state(true, null));

		assertTrue(outlines.isAnyOwning());
		assertTrue(outlines.isOwning(RETRO));
		assertFalse(outlines.isOwning(CNM));
	}

	@Test
	public void anyOwningWhenCnmOwns()
	{
		outlines.accept(CNM, state(true, null));

		assertTrue(outlines.isAnyOwning());
		assertTrue(outlines.isOwning(CNM));
		assertFalse(outlines.isOwning(RETRO));
	}

	@Test
	public void notAnyOwningWhenNeitherOwns()
	{
		outlines.accept(RETRO, state(false, retroPoser));
		outlines.accept(CNM, state(false, cnmPoser));

		assertFalse(outlines.isAnyOwning());
	}

	@Test
	public void forgettingRetroLeavesCnmOwning()
	{
		outlines.accept(RETRO, state(true, retroPoser));
		outlines.accept(CNM, state(true, cnmPoser));
		when(cnmPoser.apply(npc)).thenReturn(cnmModel);

		outlines.forget(RETRO);

		assertFalse(outlines.isOwning(RETRO));
		assertTrue(outlines.isOwning(CNM));
		assertTrue(outlines.isAnyOwning());
		assertNull(outlines.pose(RETRO, npc));
		assertSame(cnmModel, outlines.pose(npc));
		verifyNoInteractions(retroPoser);
	}

	@Test
	public void aPoserWithoutOwningNeverFlipsOwning()
	{
		outlines.accept(RETRO, state(false, retroPoser));
		when(retroPoser.apply(npc)).thenReturn(retroModel);

		assertFalse(outlines.isOwning(RETRO));
		assertFalse(outlines.isAnyOwning());
		assertSame(retroModel, outlines.pose(RETRO, npc));
	}

	@Test
	public void aLaterMessageReplacesTheEarlierOne()
	{
		outlines.accept(RETRO, state(true, retroPoser));
		outlines.accept(RETRO, state(false, retroPoser));

		assertFalse(outlines.isOwning(RETRO));
	}

	@Test
	public void aNullPoserWithdrawsTheEarlierOne()
	{
		outlines.accept(RETRO, state(true, retroPoser));
		when(retroPoser.apply(npc)).thenReturn(retroModel);
		assertSame(retroModel, outlines.pose(RETRO, npc));

		outlines.accept(RETRO, state(true, null));

		assertNull(outlines.pose(RETRO, npc));
		assertNull(outlines.pose(npc));
		assertTrue(outlines.isOwning(RETRO));
	}

	@Test
	public void retrosModelWinsWhenBothPose()
	{
		outlines.accept(RETRO, state(false, retroPoser));
		outlines.accept(CNM, state(true, cnmPoser));
		when(retroPoser.apply(npc)).thenReturn(retroModel);
		when(cnmPoser.apply(npc)).thenReturn(cnmModel);

		assertSame(retroModel, outlines.pose(npc));
		verify(cnmPoser, never()).apply(npc);
	}

	@Test
	public void cnmsModelIsUsedWhenRetroDrawsNone()
	{
		outlines.accept(RETRO, state(false, retroPoser));
		outlines.accept(CNM, state(false, cnmPoser));
		when(retroPoser.apply(npc)).thenReturn(null);
		when(cnmPoser.apply(npc)).thenReturn(cnmModel);

		assertSame(cnmModel, outlines.pose(npc));
		verify(retroPoser).apply(npc);
	}

	@Test
	public void cnmsModelIsUsedWhenRetrosPoserThrows()
	{
		outlines.accept(RETRO, state(false, retroPoser));
		outlines.accept(CNM, state(false, cnmPoser));
		when(retroPoser.apply(npc)).thenThrow(new IllegalStateException("partner bug"));
		when(cnmPoser.apply(npc)).thenReturn(cnmModel);

		assertSame(cnmModel, outlines.pose(npc));
		verify(cnmPoser).apply(npc);
	}

	@Test
	public void aThrowingPoserPosesNothing()
	{
		outlines.accept(RETRO, state(false, retroPoser));
		when(retroPoser.apply(npc)).thenThrow(new IllegalStateException("partner bug"));

		assertNull(outlines.pose(RETRO, npc));
	}

	@Test
	public void posingOneSourceAsksOnlyThatSource()
	{
		outlines.accept(RETRO, state(false, retroPoser));
		outlines.accept(CNM, state(false, cnmPoser));
		when(cnmPoser.apply(npc)).thenReturn(cnmModel);

		assertSame(cnmModel, outlines.pose(CNM, npc));
		verify(cnmPoser).apply(npc);
		verifyNoInteractions(retroPoser);
	}

	@Test
	public void ourOwnMessagesAreIgnored()
	{
		String self = ModelSwapProtocol.SOURCE_CUSTOM_SERVANTS;
		outlines.accept(self, state(true, retroPoser));

		assertFalse(outlines.isOwning(self));
		assertFalse(outlines.isAnyOwning());
		assertNull(outlines.pose(self, npc));
		assertNull(outlines.pose(npc));
		verifyNoInteractions(retroPoser);
	}

	@Test
	public void unknownSourcesAreIgnored()
	{
		outlines.accept("someone-else", state(true, retroPoser));
		outlines.accept(null, state(true, retroPoser));
		outlines.accept(RETRO, null);

		assertFalse(outlines.isOwning("someone-else"));
		assertFalse(outlines.isOwning(null));
		assertFalse(outlines.isAnyOwning());
		assertNull(outlines.pose("someone-else", npc));
		assertNull(outlines.pose(null, npc));
		assertNull(outlines.pose(npc));
		verifyNoInteractions(retroPoser);
	}

	@Test
	public void handshakeIsNotDoneUntilSet()
	{
		assertFalse(outlines.isHandshakeDone());

		outlines.setHandshakeDone();

		assertTrue(outlines.isHandshakeDone());
	}

	@Test
	public void resetForgetsEverything()
	{
		outlines.accept(RETRO, state(true, retroPoser));
		outlines.accept(CNM, state(true, cnmPoser));
		outlines.setHandshakeDone();

		outlines.reset();

		assertFalse(outlines.isAnyOwning());
		assertFalse(outlines.isOwning(RETRO));
		assertFalse(outlines.isOwning(CNM));
		assertNull(outlines.pose(npc));
		assertFalse(outlines.isHandshakeDone());
		verifyNoInteractions(retroPoser, cnmPoser);
	}
}
