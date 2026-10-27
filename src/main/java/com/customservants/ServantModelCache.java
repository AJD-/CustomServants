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

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.NPC;

/**
 * Builds and poses the model a house servant is drawn as. Holds the current override, one lit base
 * model, the resolved chathead NPC id and an animation cache. Client thread only.
 */
@Singleton
public class ServantModelCache
{
	@Inject
	private Client client;

	private ServantOverride override = ServantOverride.DEFAULT;

	/**
	 * Selects the override to build. When it differs from the current one, {@link #clear()} is called
	 * before it is stored.
	 */
	public void setOverride(ServantOverride override)
	{
		// T5: clear() when the override changes
		this.override = override;
	}

	/**
	 * @return the current override, {@link ServantOverride#DEFAULT} until one is set
	 */
	public ServantOverride getOverride()
	{
		return override;
	}

	/**
	 * Builds the base model for the current override if it has not been attempted yet. Builds at most
	 * once per override and remembers a failure, so an unbuildable override is not retried every frame.
	 *
	 * @return true iff a base model exists
	 */
	public boolean ensureBuilt()
	{
		// T5
		return false;
	}

	/**
	 * @return true when a base model has been built
	 */
	public boolean isReady()
	{
		// T5
		return false;
	}

	/**
	 * @return the NPC id whose chathead to borrow for the current override, -1 when none
	 */
	public int chatheadNpcId()
	{
		// T5
		return -1;
	}

	/**
	 * Render path: poses the base model with the servant's current animation state. The returned model
	 * is shared and only valid until the next call.
	 *
	 * @return the posed model, or null to draw the vanilla model
	 */
	public Model pose(NPC npc)
	{
		// T5
		return null;
	}

	/**
	 * Drops the built model, cached animations and the failure memo. Keeps the current override.
	 */
	public void clear()
	{
		// T5
	}

	/**
	 * @return {@code frame}, or 0 when {@code animation} is null or {@code frame} is negative or
	 * {@code >= animation.getNumFrames()}
	 */
	public static int clampFrame(Animation animation, int frame)
	{
		// T5
		return -1;
	}

	/**
	 * Decides which sequences pose the override model for one frame.
	 * <ul>
	 * <li>Same rig: action and pose are the NPC's own, with the NPC's frames.</li>
	 * <li>Different rig: action is -1 (the model holds its pose during bows and serving). The pose is
	 * the NPC's when it equals the override's stand or walk (with the NPC's frame), otherwise the
	 * override's stand at frame 0. A walk of -1 falls back to the stand; a stand of -1 gives pose -1
	 * (base model drawn unposed).</li>
	 * </ul>
	 */
	public static PosePlan plan(Rig servantRig, Rig overrideRig, int npcAction, int npcPose, int overrideStand, int overrideWalk)
	{
		// T5
		return null;
	}

	/**
	 * Sequences to apply to the base model, and whether each one uses the NPC's current frame (true)
	 * or frame 0 (false). A sequence id of -1 means none.
	 */
	@Value
	public static class PosePlan
	{
		int action;
		int pose;
		boolean useNpcActionFrame;
		boolean useNpcPoseFrame;
	}
}
