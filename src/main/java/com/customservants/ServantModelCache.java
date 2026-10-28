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

import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;

/**
 * Builds and poses the model a house servant is drawn as. Holds the current override, one lit base
 * model, the resolved chathead NPC id and an animation cache. Client thread only.
 */
@Singleton
@Slf4j
public class ServantModelCache
{
	// NPC lighting baseline, added to the override's ambient and (x5) contrast - the values the
	// client lights NPC models with. The no-arg ModelData.light() uses the item constants instead
	private static final int NPC_AMBIENT = 64;
	private static final int NPC_CONTRAST = 850;
	private static final int LIGHT_X = -30;
	private static final int LIGHT_Y = -50;
	private static final int LIGHT_Z = -30;

	// Width/height scale at which a composition's model is drawn at its natural size
	private static final int UNSCALED = 128;

	@Inject
	private Client client;

    @Getter
    private ServantOverride override = ServantOverride.DEFAULT;

	// Lit and unposed, at the model's natural size; null when nothing has been built (or nothing could be)
	private Model base;

	// The composition's width/height scale, applied after posing as the client does. Scaling the
	// vertices before a skeletal (maya) sequence poses them distorts the pose, because the bone
	// transforms assume the unscaled mesh - Crondis and Apmeken (scale 92 and 84) showed that in game
	private int widthScale = UNSCALED;
	private int heightScale = UNSCALED;

	// Pre-scaled copy of base for the no-animation path only; null when the scale is 128
	private Model scaledBase;

	private int chatheadNpcId = -1;

	// A build that produced nothing, remembered so it is not retried every frame
	private boolean unbuildable;

	// Keyed by sequence id. Null results are stored too, so an unknown id is not reloaded every frame
	private final Map<Integer, Animation> animations = new HashMap<>();

	/**
	 * Selects the override to build. When it differs from the current one, {@link #clear()} is called
	 * before it is stored.
	 */
	public void setOverride(ServantOverride override)
	{
		if (override != this.override)
		{
			clear();
		}
		this.override = override;
	}

    /**
	 * Builds the base model for the current override if it has not been attempted yet. Builds at most
	 * once per override and remembers a failure, so an unbuildable override is not retried every frame.
	 *
	 * @return true iff a base model exists
	 */
	public boolean ensureBuilt()
	{
		if (!override.isOverride() || unbuildable)
		{
			return false;
		}
		if (base != null)
		{
			return true;
		}

		// The base compositions are used throughout - transform() would resolve a multinpc against
		// the local player's varbits, which have nothing to do with how the servant should look
		int[] ids = override.getNpcIds();
		int bodyId = ServantOverride.firstMatching(ids, id ->
		{
			NPCComposition c = client.getNpcDefinition(id);
			return c != null && c.getModels() != null && c.getModels().length > 0;
		});
		chatheadNpcId = ServantOverride.firstMatching(ids, id ->
		{
			NPCComposition c = client.getNpcDefinition(id);
			return c != null && c.getChatheadModels() != null && c.getChatheadModels().length > 0;
		});

		NPCComposition body = bodyId == -1 ? null : client.getNpcDefinition(bodyId);
		ModelData merged = body == null ? null : build(body);
		if (merged != null)
		{
			base = light(merged);
			widthScale = body.getWidthScale();
			heightScale = body.getHeightScale();
			if (widthScale != UNSCALED || heightScale != UNSCALED)
			{
				// For the no-animation path, which cannot scale the shared sequence model. The
				// clone keeps the lit base at its natural size
				scaledBase = light(merged.cloneVertices().scale(widthScale, heightScale, widthScale));
			}
		}
		if (base == null)
		{
			unbuildable = true;
			log.debug("No model could be built for servant override {} (body npc {})", override.name(), bodyId);
		}

		return base != null;
	}

	/**
	 * Merges, recolours and retextures a composition's models the way the client builds an NPC's,
	 * minus lighting (see {@link #light}), minus any animation and minus the width/height scale,
	 * which is applied per frame after posing. Null when none of its models load.
	 */
	private ModelData build(NPCComposition c)
	{
		int[] modelIds = c.getModels();
		ModelData[] parts = new ModelData[modelIds.length];
		int found = 0;
		for (int modelId : modelIds)
		{
			ModelData part = client.loadModelData(modelId);
			if (part != null)
			{
				parts[found++] = part;
			}
		}

		if (found == 0)
		{
			return null;
		}

		ModelData merged = found == 1 ? parts[0] : client.mergeModels(parts, found);
		if (merged == null)
		{
			return null;
		}

		short[] find = c.getColorToReplace();
		short[] repl = c.getColorToReplaceWith();
		if (find != null && repl != null)
		{
			// cloneColors first - loadModelData hands
			// back the client's shared arrays, and recoloring in place would repaint the cached
			// model for everything else using it
			merged = merged.cloneColors();
			int n = Math.min(find.length, repl.length);
			for (int i = 0; i < n; i++)
			{
				merged.recolor(find[i], repl[i]);
			}
		}

		short[] textureFind = override.getRetextureFind();
		short[] textureRepl = override.getRetextureReplace();
		if (textureFind.length > 0)
		{
			// cloneTextures first - the face texture ids are the client's shared arrays too, and
			// retexturing in place would change the cached model for everything else using it
			merged = merged.cloneTextures();
			int n = Math.min(textureFind.length, textureRepl.length);
			for (int i = 0; i < n; i++)
			{
				merged.retexture(textureFind[i], textureRepl[i]);
			}
		}

		// Width/height scale is not applied here: the client scales the posed model, after the
		// animation has run, and so does pose()
		return merged;
	}

	/**
	 * Lights with the NPC constants rather than {@code light()}'s item/scenery defaults.
	 */
	private Model light(ModelData merged)
	{
		return merged.light(NPC_AMBIENT + override.getAmbient(), NPC_CONTRAST + 5 * override.getContrast(),
			LIGHT_X, LIGHT_Y, LIGHT_Z);
	}

	/**
	 * @return true when a base model has been built
	 */
	public boolean isReady()
	{
		return base != null;
	}

	/**
	 * @return the NPC id whose chathead to borrow for the current override, -1 when none
	 */
	public int chatheadNpcId()
	{
		return chatheadNpcId;
	}

	/**
	 * Render path: poses the base model with the servant's current animation state. One servant
	 * lookup and one {@code applyTransformations} call per frame.
	 *
	 * <p>The returned model is shared and is invalidated by the next applyTransformations call,
	 * including the client's own, so it has to be consumed before anything else runs - the draw
	 * callback hands it straight to the renderer. Must be called on the client thread.
	 *
	 * @return the posed model, or null to draw the vanilla model
	 */
	public Model pose(NPC npc)
	{
		if (base == null)
		{
			return null;
		}
		ServantIds.Servant s = ServantIds.get(npc.getId());
		if (s == null)
		{
			return null;
		}

		PosePlan p = plan(s.getRig(), override.getRig(), npc.getAnimation(), npc.getPoseAnimation(),
			override.getStandAnimation(), override.getWalkAnimation());
		Animation action = animation(p.getAction());
		Animation pose = animation(p.getPose());
		int af = p.isUseNpcActionFrame() ? clampFrame(action, npc.getAnimationFrame()) : 0;
		int pf = p.isUseNpcPoseFrame() ? clampFrame(pose, npc.getPoseAnimationFrame()) : 0;
		if (action == null && pose == null)
		{
			// Nothing to pose. applyTransformations may hand the base itself back here, so it is
			// not scaled in place; a pre-scaled copy stands in for it
			return scaledBase();
		}

		Model posed = client.applyTransformations(base, action, af, pose, pf);
		if (posed != null && (widthScale != UNSCALED || heightScale != UNSCALED))
		{
			// The posed model is the client's shared sequence model, rebuilt from base on every
			// call, so scaling it in place is per frame and never accumulates - the same order the
			// client uses for its own NPCs (animate, then scale)
			posed.scale(widthScale, heightScale, widthScale);
		}
		return posed;
	}

	private Model scaledBase()
	{
		return scaledBase != null ? scaledBase : base;
	}

	/**
	 * Drops the built model, cached animations and the failure memo. Keeps the current override.
	 */
	public void clear()
	{
		base = null;
		scaledBase = null;
		widthScale = UNSCALED;
		heightScale = UNSCALED;
		chatheadNpcId = -1;
		unbuildable = false;
		animations.clear();
	}

	/**
	 * Resolves a sequence id, caching the result. Returns null for a negative or unknown id.
	 */
	Animation animation(int id)
	{
		if (id < 0)
		{
			return null;
		}

		// computeIfAbsent is avoided so a null from loadAnimation is not retried every frame
		if (animations.containsKey(id))
		{
			return animations.get(id);
		}

		Animation animation = client.loadAnimation(id);
		animations.put(id, animation);
		return animation;
	}

	/**
	 * @return {@code frame}, or 0 when {@code animation} is null or {@code frame} is negative or
	 * {@code >= animation.getNumFrames()}
	 */
	public static int clampFrame(Animation animation, int frame)
	{
		return animation == null || frame < 0 || frame >= animation.getNumFrames() ? 0 : frame;
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
		if (servantRig == overrideRig)
		{
			return new PosePlan(npcAction, npcPose, true, true);
		}

		if (overrideStand == -1)
		{
			return new PosePlan(-1, -1, false, false);
		}

		int effectiveWalk = overrideWalk != -1 ? overrideWalk : overrideStand;
		if (npcPose == overrideStand || npcPose == effectiveWalk)
		{
			return new PosePlan(-1, npcPose, false, true);
		}
		return new PosePlan(-1, overrideStand, false, false);
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
