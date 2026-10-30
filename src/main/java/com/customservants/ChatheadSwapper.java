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

import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.WorldView;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetModelType;
import net.runelite.client.eventbus.Subscribe;

/**
 * Shows the override NPC's chathead in dialogue with a house servant, in place of the servant's own.
 * <p>
 * The dialogue head is a widget the client draws from the cache, so the widget is pointed at the
 * override NPC ({@link ServantModelCache#getChatheadNpcId()}) and the client draws that NPC's head. It
 * keeps its animation, the emote for the line, unless the override's {@link ChatheadEmote} is
 * {@link ChatheadEmote#NONE}, which clears it.
 * <p>
 * Checked every frame rather than on a script event, so a page that opens with the servant's own head
 * is swapped before it is first drawn, and every new page, which sets the head again, is swapped too.
 * Client thread only.
 */
@Slf4j
@Singleton
public class ChatheadSwapper
{
	private final Client client;
	private final ServantModelCache modelCache;

	// Held once so the per-frame decide call does not allocate a method reference each frame
	private final IntUnaryOperator speakerAt = this::speakerAt;
	private final IntUnaryOperator resolve = this::resolve;
	private final IntFunction<Target> targetFor = this::targetFor;

	/** Set from startUp on the EDT, read on the client thread. */
	private volatile BooleanSupplier canSwap = () -> false;

	/** The swap the head is showing, or null while it shows what the client gave it. Client thread only. */
	private Swap shown;

	/** The last target handed out, reused while the chathead NPC is unchanged. Client thread only. */
	private Target target;

	@Inject
	ChatheadSwapper(Client client, ServantModelCache modelCache)
	{
		this.client = client;
		this.modelCache = modelCache;
	}

	/** What the head is swapped to: a borrowed cache chathead. */
	@Value
	static class Target
	{
		int type;
		int id;
	}

	/** What the head showed before a swap (model and emote), whose head it is, and what it was swapped to. */
	@Value
	static class Swap
	{
		int type;
		int id;
		int animation;
		int speaker;
		Target target;
	}

	/** What the head should show, and the swap that leaves it showing, if any. */
	@Value
	static class Head
	{
		int type;
		int id;
		Swap shown;
	}

	/** Swaps only while the plugin says servants are drawn as their override. */
	public void start(BooleanSupplier canSwap)
	{
		this.canSwap = canSwap;
	}

	/**
	 * Puts back the head (and the emote, if it was cleared) a dialogue still open would have shown,
	 * and forgets the swap. Client thread only. Leaves {@link #canSwap} alone: the swapper is off the
	 * event bus by then anyway, and this can run after the next startUp.
	 */
	public void stop()
	{
		Widget head = client.getWidget(InterfaceID.ChatLeft.HEAD);
		if (head != null && shown != null && shows(head.getModelType(), head.getModelId(), shown.getTarget()))
		{
			head.setModelType(shown.getType());
			head.setModelId(shown.getId());
			restoreEmote(head, shown);
		}
		shown = null;
		target = null;
	}

	@Subscribe
	public void onBeforeRender(BeforeRender event)
	{
		Widget head = client.getWidget(InterfaceID.ChatLeft.HEAD);
		if (head == null)
		{
			shown = null;
			return;
		}
		if (head.isHidden())
		{
			// Hidden, not gone: it still shows the swap when it comes back, so the swap is kept
			return;
		}

		int type = head.getModelType();
		int id = head.getModelId();
		if (!keeps(type, id, shown, targetFor))
		{
			Swap previous = shown;
			Head next = decide(type, id, head.getAnimationId(), previous, speakerAt, resolve, targetFor);
			shown = next.getShown();
			if (next.getType() != type || next.getId() != id)
			{
				head.setModelType(next.getType());
				head.setModelId(next.getId());
			}
			if (previous != null && shows(type, id, previous.getTarget()))
			{
				// Leaving a swap the head still showed (reverted or re-targeted): its emote goes back
				// too, and a re-target applies its own policy below
				restoreEmote(head, previous);
			}
			if (shown != previous)
			{
				log.debug("Chathead swap {} -> {}", previous, shown);
			}
		}

		if (shown != null && shows(head.getModelType(), head.getModelId(), shown.getTarget()))
		{
			int current = head.getAnimationId();
			int emote = emoteFor(modelCache.getOverride().getChatheadEmote(), current);
			if (emote != current)
			{
				head.setAnimationId(emote);
			}
		}
	}

	/**
	 * What a dialogue head showing {@code type} and {@code id}, playing {@code animation}, should
	 * show instead.
	 * <p>
	 * A head still showing the last swap keeps it (the same instance) while the speaker's target is
	 * unchanged, goes back to what it was once there is no target, and follows a changed target.
	 * Anything else the head shows was set by the client, so it is looked at afresh: an NPC's own
	 * head, by id or by the index of the NPC in the scene, is swapped when it has a target, and every
	 * other kind of head (player, item, model, none) is left alone.
	 *
	 * @param speakerAt the NPC id at a scene index, or -1 when there is none
	 * @param resolve   the NPC a multi NPC shows as, -1 for none; any other NPC is itself
	 * @param targetFor what an NPC id's head is swapped to, or null to keep its own
	 */
	static Head decide(int type, int id, int animation, Swap shown, IntUnaryOperator speakerAt,
		IntUnaryOperator resolve, IntFunction<Target> targetFor)
	{
		if (shown != null && shows(type, id, shown.getTarget()))
		{
			Target target = targetFor.apply(shown.getSpeaker());
			if (shown.getTarget().equals(target))
			{
				return new Head(type, id, shown);
			}
			if (target == null)
			{
				return new Head(shown.getType(), shown.getId(), null);
			}
			return new Head(target.getType(), target.getId(),
				new Swap(shown.getType(), shown.getId(), shown.getAnimation(), shown.getSpeaker(), target));
		}

		int speaker = type == WidgetModelType.NPC_CHATHEAD ? id
			: type == WidgetModelType.NPC_INDEX_CHATHEAD ? speakerAt.applyAsInt(id)
			: -1;
		// The dialogue may name a multi NPC, which is swapped as the NPC it shows as
		if (speaker >= 0)
		{
			speaker = resolve.applyAsInt(speaker);
		}
		Target target = speaker < 0 ? null : targetFor.apply(speaker);
		if (target == null || shows(type, id, target))
		{
			return new Head(type, id, null);
		}
		return new Head(target.getType(), target.getId(), new Swap(type, id, animation, speaker, target));
	}

	/** The emote a swapped head plays: the dialogue's own for KEEP, none (-1) for NONE. */
	static int emoteFor(ChatheadEmote policy, int current)
	{
		return policy == ChatheadEmote.NONE ? -1 : current;
	}

	/**
	 * Whether {@link #decide} would keep {@code shown} as it is. Checked first each frame so the
	 * kept-swap path allocates nothing.
	 */
	private static boolean keeps(int type, int id, Swap shown, IntFunction<Target> targetFor)
	{
		return shown != null && shows(type, id, shown.getTarget())
			&& shown.getTarget().equals(targetFor.apply(shown.getSpeaker()));
	}

	private static boolean shows(int type, int id, Target target)
	{
		return type == target.getType() && id == target.getId();
	}

	/** Puts back the emote a swap cleared, if it did. */
	private static void restoreEmote(Widget head, Swap swap)
	{
		if (head.getAnimationId() == -1 && swap.getAnimation() != -1)
		{
			head.setAnimationId(swap.getAnimation());
		}
	}

	/** The NPC a multi NPC shows as right now, -1 for none, or the NPC itself when it is not one. */
	private int resolve(int npcId)
	{
		// Servant ids, multi and base alike, are all in the servant table: no transform needed
		if (ServantIds.isServant(npcId))
		{
			return npcId;
		}
		NPCComposition npc = client.getNpcDefinition(npcId);
		if (npc != null && npc.getConfigs() != null)
		{
			NPCComposition shows = npc.transform();
			return shows == null ? -1 : shows.getId();
		}
		return npcId;
	}

	private int speakerAt(int index)
	{
		WorldView worldView = client.getTopLevelWorldView();
		NPC npc = worldView == null ? null : worldView.npcs().byIndex(index);
		return npc == null ? -1 : npc.getId();
	}

	private Target targetFor(int npcId)
	{
		int chathead = modelCache.getChatheadNpcId();
		if (!canSwap.getAsBoolean() || !ServantIds.isServant(npcId) || chathead < 0)
		{
			return null;
		}
		if (target == null || target.getId() != chathead)
		{
			target = new Target(WidgetModelType.NPC_CHATHEAD, chathead);
		}
		return target;
	}
}
