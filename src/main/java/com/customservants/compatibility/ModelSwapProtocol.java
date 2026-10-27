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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.client.events.PluginMessage;

/**
 * The messages this plugin exchanges over the event bus with Retro NPC Swapper, so the two can run
 * at once.
 * <p>
 * This plugin posts a {@code claims} message naming every servant NPC id while it draws the servant
 * override, and the empty set when it does not, and Retro NPC Swapper leaves the claimed NPCs alone
 * entirely - no retro model, animations or chathead. Retro NPC Swapper posts {@code modelSyncReq}
 * once it is listening, which this plugin answers by posting its claims again, so start order does
 * not matter. A plugin hears its own messages, so every read names the partner it expects them from.
 * <p>
 * Retro NPC Swapper and Custom NPC Models also exchange {@code outlines} and {@code outlinesOptOut}
 * messages over Interact Highlight's NPC outlines. This plugin takes no part in the outlines, so its
 * copy of the protocol leaves them out.
 * <p>
 * The plugins are loaded by different classloaders, so everything in a message is a JDK type or a
 * RuneLite API type, which all of them see through the client's classloader. A copy of this class
 * lives in each of the three plugins (Retro NPC Swapper, Custom NPC Models and this one) and the
 * copies must stay in step; {@link #VERSION} is bumped on any incompatible change, and a message of
 * another version is ignored.
 */
public final class ModelSwapProtocol
{
	public static final String NAMESPACE = "npc-model-swap";
	public static final int VERSION = 1;

	/** Posted by each partner: the NPC ids it draws, which Retro NPC Swapper must leave alone. */
	public static final String CLAIMS = "claims";

	/** Posted by a plugin once it is listening, asking for the others' current state. */
	public static final String SYN = "modelSyncReq";

	public static final String SOURCE_CUSTOM_NPC_MODELS = "custom-npc-models";
	public static final String SOURCE_RETRO_NPC_SWAPPER = "retro-npc-swapper";
	public static final String SOURCE_CUSTOM_SERVANTS = "custom-servants";

	static final String KEY_VERSION = "version";
	static final String KEY_SOURCE = "source";
	static final String KEY_NPC_IDS = "npcIds";

	private ModelSwapProtocol()
	{
	}

	/**
	 * This plugin's claims message naming {@code npcIds}. The set in it is a copy and cannot be
	 * modified.
	 */
	public static PluginMessage claimsMessage(Set<Integer> npcIds)
	{
		Map<String, Object> data = header(SOURCE_CUSTOM_SERVANTS);
		data.put(KEY_NPC_IDS, Set.copyOf(npcIds));
		return new PluginMessage(NAMESPACE, CLAIMS, data);
	}

	/**
	 * Whether {@code message} is {@code fromSource} asking for this plugin's state, in a version
	 * this plugin speaks.
	 */
	public static boolean isSyncReq(PluginMessage message, String fromSource)
	{
		return isFrom(message, SYN, fromSource);
	}

	/**
	 * The NPC ids in a claims message from {@code fromSource}, or null when {@code message} is
	 * anything else. The set is a copy and cannot be modified.
	 */
	public static Set<Integer> readClaims(PluginMessage message, String fromSource)
	{
		if (!isFrom(message, CLAIMS, fromSource))
		{
			return null;
		}

		Object ids = message.getData().get(KEY_NPC_IDS);
		if (!(ids instanceof Collection))
		{
			return null;
		}

		Set<Integer> claims = new HashSet<>();
		for (Object id : (Collection<?>) ids)
		{
			if (id instanceof Integer)
			{
				claims.add((Integer) id);
			}
		}
		return Set.copyOf(claims);
	}

	private static Map<String, Object> header(String source)
	{
		Map<String, Object> data = new HashMap<>();
		data.put(KEY_VERSION, VERSION);
		data.put(KEY_SOURCE, source);
		return data;
	}

	/** Whether {@code message} is a {@code name} message from {@code source}, in a version this plugin speaks. */
	private static boolean isFrom(PluginMessage message, String name, String source)
	{
		return source != null
			&& message != null
			&& NAMESPACE.equals(message.getNamespace())
			&& name.equals(message.getName())
			&& message.getData() != null
			&& Integer.valueOf(VERSION).equals(message.getData().get(KEY_VERSION))
			&& source.equals(message.getData().get(KEY_SOURCE));
	}
}
