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

import net.runelite.api.NPC;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Capturing a servant's nine animation slots and writing all of them back.
 */
public class OriginalNpcStateTest
{
	@Test
	public void captureReadsAndRestoreWritesAllNineSlotsIncludingEmptyOnes()
	{
		NPC original = mock(NPC.class);
		when(original.getIdlePoseAnimation()).thenReturn(11);
		when(original.getPoseAnimation()).thenReturn(12);
		when(original.getIdleRotateLeft()).thenReturn(13);
		when(original.getIdleRotateRight()).thenReturn(14);
		when(original.getWalkAnimation()).thenReturn(15);
		when(original.getWalkRotate180()).thenReturn(-1);
		when(original.getWalkRotateLeft()).thenReturn(17);
		when(original.getWalkRotateRight()).thenReturn(18);
		when(original.getRunAnimation()).thenReturn(19);

		OriginalNpcState state = OriginalNpcState.capture(original);

		verify(original).getIdlePoseAnimation();
		verify(original).getPoseAnimation();
		verify(original).getIdleRotateLeft();
		verify(original).getIdleRotateRight();
		verify(original).getWalkAnimation();
		verify(original).getWalkRotate180();
		verify(original).getWalkRotateLeft();
		verify(original).getWalkRotateRight();
		verify(original).getRunAnimation();
		verifyNoMoreInteractions(original);
		assertEquals(11, state.getIdlePoseAnimation());

		NPC target = mock(NPC.class);
		state.restore(target);

		verify(target).setIdlePoseAnimation(11);
		verify(target).setPoseAnimation(12);
		verify(target).setIdleRotateLeft(13);
		verify(target).setIdleRotateRight(14);
		verify(target).setWalkAnimation(15);
		// The slot was empty originally, so it must be emptied again rather than skipped
		verify(target).setWalkRotate180(-1);
		verify(target).setWalkRotateLeft(17);
		verify(target).setWalkRotateRight(18);
		verify(target).setRunAnimation(19);
		verifyNoMoreInteractions(target);
	}
}
