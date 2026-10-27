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

import com.customservants.ServantModelCache.PosePlan;
import net.runelite.api.Animation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Which sequences pose the override model, and when the NPC's own frame is used.
 */
public class PosePlanTest
{
	// Arbitrary sequence ids; only their equality matters to plan()
	private static final int NPC_ACTION = 101;
	private static final int STAND = 200;
	private static final int WALK = 201;
	private static final int FOREIGN_POSE = 300;

	@Test
	public void sameRigPassesActionAndPoseThroughWithNpcFrames()
	{
		PosePlan p = ServantModelCache.plan(Rig.HUMAN, Rig.HUMAN, NPC_ACTION, FOREIGN_POSE, STAND, WALK);

		assertEquals(new PosePlan(NPC_ACTION, FOREIGN_POSE, true, true), p);
	}

	@Test
	public void differentRigDropsTheAction()
	{
		PosePlan p = ServantModelCache.plan(Rig.DEMON, Rig.HUMAN, NPC_ACTION, STAND, STAND, WALK);

		assertEquals(-1, p.getAction());
		assertFalse(p.isUseNpcActionFrame());
	}

	@Test
	public void differentRigKeepsNpcPoseWhenItIsTheOverrideStand()
	{
		PosePlan p = ServantModelCache.plan(Rig.DEMON, Rig.HUMAN, NPC_ACTION, STAND, STAND, WALK);

		assertEquals(new PosePlan(-1, STAND, false, true), p);
	}

	@Test
	public void differentRigKeepsNpcPoseWhenItIsTheOverrideWalk()
	{
		PosePlan p = ServantModelCache.plan(Rig.DEMON, Rig.HUMAN, NPC_ACTION, WALK, STAND, WALK);

		assertEquals(new PosePlan(-1, WALK, false, true), p);
	}

	@Test
	public void missingWalkFallsBackToStand()
	{
		PosePlan p = ServantModelCache.plan(Rig.DEMON, Rig.HUMAN, NPC_ACTION, STAND, STAND, -1);

		assertEquals(new PosePlan(-1, STAND, false, true), p);
	}

	@Test
	public void foreignPoseGivesOverrideStandAtFrameZero()
	{
		PosePlan p = ServantModelCache.plan(Rig.DEMON, Rig.HUMAN, NPC_ACTION, FOREIGN_POSE, STAND, WALK);

		assertEquals(new PosePlan(-1, STAND, false, false), p);
	}

	@Test
	public void missingStandDrawsTheBaseUnposed()
	{
		PosePlan p = ServantModelCache.plan(Rig.DEMON, Rig.HUMAN, NPC_ACTION, FOREIGN_POSE, -1, WALK);

		assertEquals(new PosePlan(-1, -1, false, false), p);
	}

	@Test
	public void clampFrameOfNullAnimationIsZero()
	{
		assertEquals(0, ServantModelCache.clampFrame(null, 5));
	}

	@Test
	public void clampFrameOfNegativeFrameIsZero()
	{
		assertEquals(0, ServantModelCache.clampFrame(animationOf(10), -1));
	}

	@Test
	public void clampFramePastTheLastFrameIsZero()
	{
		assertEquals(0, ServantModelCache.clampFrame(animationOf(10), 10));
		assertEquals(0, ServantModelCache.clampFrame(animationOf(10), 11));
	}

	@Test
	public void clampFrameInRangeIsUnchanged()
	{
		assertEquals(0, ServantModelCache.clampFrame(animationOf(10), 0));
		assertEquals(9, ServantModelCache.clampFrame(animationOf(10), 9));
	}

	private static Animation animationOf(int numFrames)
	{
		Animation animation = mock(Animation.class);
		when(animation.getNumFrames()).thenReturn(numFrames);
		return animation;
	}
}
