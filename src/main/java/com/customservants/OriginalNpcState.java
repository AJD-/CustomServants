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

/**
 * Snapshot of a servant's original pose and movement animations, taken before an override's
 * sequences are written over them so the servant can be put back exactly as it was.
 */
class OriginalNpcState
{
	private final int idlePoseAnimation;
	private final int poseAnimation;
	private final int idleRotateLeft;
	private final int idleRotateRight;
	private final int walkAnimation;
	private final int walkRotate180;
	private final int walkRotateLeft;
	private final int walkRotateRight;
	private final int runAnimation;

	private OriginalNpcState(NPC npc)
	{
		idlePoseAnimation = npc.getIdlePoseAnimation();
		poseAnimation = npc.getPoseAnimation();
		idleRotateLeft = npc.getIdleRotateLeft();
		idleRotateRight = npc.getIdleRotateRight();
		walkAnimation = npc.getWalkAnimation();
		walkRotate180 = npc.getWalkRotate180();
		walkRotateLeft = npc.getWalkRotateLeft();
		walkRotateRight = npc.getWalkRotateRight();
		runAnimation = npc.getRunAnimation();
	}

	static OriginalNpcState capture(NPC npc)
	{
		return new OriginalNpcState(npc);
	}

	/**
	 * Writes every captured field back, -1 included - a slot that was originally empty must be emptied
	 * again, or the override's sequence would be left behind on it.
	 */
	void restore(NPC npc)
	{
		npc.setIdlePoseAnimation(idlePoseAnimation);
		npc.setPoseAnimation(poseAnimation);
		npc.setIdleRotateLeft(idleRotateLeft);
		npc.setIdleRotateRight(idleRotateRight);
		npc.setWalkAnimation(walkAnimation);
		npc.setWalkRotate180(walkRotate180);
		npc.setWalkRotateLeft(walkRotateLeft);
		npc.setWalkRotateRight(walkRotateRight);
		npc.setRunAnimation(runAnimation);
	}

	int getIdlePoseAnimation()
	{
		return idlePoseAnimation;
	}
}
