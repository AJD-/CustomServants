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

import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.NPC;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The snapshot / override / restore sequence for a servant whose own sequences cannot pose the
 * override model.
 */
public class CustomServantsPluginLogicTest
{
	private static final int VANILLA_IDLE = 808;
	private static final int VANILLA_WALK = 819;
	private static final ServantOverride OV = ServantOverride.VYRELORD_HUMAN;

	private CustomServantsPlugin plugin;
	private NPC npc;
	// Backs the NPC's idle pose slot, so a set is seen by later gets like on a real NPC
	private AtomicInteger idle;

	@Before
	public void setUp()
	{
		plugin = new CustomServantsPlugin();
		npc = mock(NPC.class);
		idle = new AtomicInteger(VANILLA_IDLE);
		when(npc.getIndex()).thenReturn(7);
		when(npc.getIdlePoseAnimation()).thenAnswer(i -> idle.get());
		doAnswer(i ->
		{
			idle.set(i.getArgument(0));
			return null;
		}).when(npc).setIdlePoseAnimation(anyInt());
		when(npc.getWalkAnimation()).thenReturn(VANILLA_WALK);
	}

	@Test
	public void overrideWritesStandAndWalkOverEverySlot()
	{
		assertNotEquals(-1, OV.getStandAnimation());
		assertNotEquals(-1, OV.getWalkAnimation());

		plugin.applyAnimationOverride(npc, OV);

		int stand = OV.getStandAnimation();
		int walk = OV.getWalkAnimation();
		assertEquals(stand, idle.get());
		verify(npc).setPoseAnimation(stand);
		verify(npc).setIdleRotateLeft(stand);
		verify(npc).setIdleRotateRight(stand);
		verify(npc).setWalkAnimation(walk);
		verify(npc).setWalkRotate180(walk);
		verify(npc).setWalkRotateLeft(walk);
		verify(npc).setWalkRotateRight(walk);
		verify(npc).setRunAnimation(walk);
		verify(npc).setPoseAnimationFrame(0);
	}

	@Test
	public void reapplyingWhileOverrideIsShownKeepsTheVanillaSnapshot()
	{
		plugin.applyAnimationOverride(npc, OV);
		// The NPC now shows our stand; a second pass (eg a recheck) must not snapshot it
		plugin.applyAnimationOverride(npc, OV);
		clearInvocations(npc);

		plugin.resetNpc(npc);

		assertEquals(VANILLA_IDLE, idle.get());
		verify(npc).setWalkAnimation(VANILLA_WALK);
	}

	@Test
	public void reapplyingAfterTheClientPutVanillaBackSnapshotsAgain()
	{
		plugin.applyAnimationOverride(npc, OV);
		// The client reset the animation family after a server-side change
		idle.set(VANILLA_IDLE + 1);
		plugin.applyAnimationOverride(npc, OV);
		clearInvocations(npc);

		plugin.resetNpc(npc);

		assertEquals(VANILLA_IDLE + 1, idle.get());
	}

	@Test
	public void resetWithoutSnapshotTouchesNothingAndResetForgetsTheSnapshot()
	{
		plugin.resetNpc(npc);
		verify(npc, never()).setIdlePoseAnimation(anyInt());

		plugin.applyAnimationOverride(npc, OV);
		plugin.resetNpc(npc);
		clearInvocations(npc);

		plugin.resetNpc(npc);
		verify(npc, never()).setIdlePoseAnimation(anyInt());
		verify(npc, never()).setWalkAnimation(anyInt());
	}
}
