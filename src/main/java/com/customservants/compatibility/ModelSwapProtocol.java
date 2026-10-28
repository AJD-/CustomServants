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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import lombok.Value;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.client.events.PluginMessage;

/**
 * The messages this plugin exchanges over the event bus with its partners, Retro NPC Swapper and
 * the sibling plugin, so they can all run at once.
 * <p>
 * This plugin posts a {@code claims} message naming every servant NPC id while it draws the servant
 * override, and the empty set when it does not, and Retro NPC Swapper leaves the claimed NPCs alone
 * entirely - no retro model, animations or chathead.
 * <p>
 * Any of the plugins may take Interact Highlight's NPC outlines over and draw them in its place, and
 * only one may at a time: two would each turn that plugin's settings off and stash the other's
 * {@code false} as the user's choice. Each posts an {@code outlines} message saying whether it has
 * taken the outlines over, and the first to do so keeps them until it posts that it has let go. The
 * message also carries a poser, a {@code Function<NPC, Model>} - a JDK type, so it is safe across the
 * plugins' classloaders - so the one drawing can outline the NPCs the others swap around the model on
 * screen. The one drawing posts {@code outlinesOptOut} when the user turns Interact Highlight's own
 * outlines back on, and the others turn their fix off too rather than taking the outlines over again.
 * <p>
 * Each plugin posts {@code modelSyncReq} once it is listening, which the others answer by posting
 * their claims and outlines again, so start order does not matter. A plugin hears its own messages,
 * so each reads {@code outlines}, {@code outlinesOptOut} and {@code modelSyncReq} from every source in
 * {@link #PARTNERS}, and never from its own.
 * <p>
 * The plugins are loaded by different classloaders, so everything in a message is a JDK type or a
 * RuneLite API type, which all of them see through the client's classloader. A copy of this class
 * lives in each of the three plugins (Retro NPC Swapper, the sibling plugin and this one) and the
 * copies must stay in step; {@link #VERSION} is bumped only on an incompatible change, and a message
 * of another version is ignored. The wire format here is identical to Retro NPC Swapper's copy; only
 * the reading side differs, in that {@link #readClaims} returns an unmodifiable copy and a null source
 * never matches.
 */
public final class ModelSwapProtocol
{
	public static final String NAMESPACE = "npc-model-swap";
	public static final int VERSION = 1;

	/** Posted by each partner: the NPC ids it draws, which Retro NPC Swapper must leave alone. */
	public static final String CLAIMS = "claims";

	/** Posted by a plugin once it is listening, asking for the others' current state. */
	public static final String SYN = "modelSyncReq";

	/** Posted by each plugin: whether it draws Interact Highlight's NPC outlines, and its poser. */
	public static final String OUTLINES = "outlines";

	/** Posted by the plugin drawing the outlines when the user turned Interact Highlight's back on. */
	public static final String OUTLINES_OPT_OUT = "outlinesOptOut";

	public static final String SOURCE_CUSTOM_NPC_MODELS = "custom-npc-models";
	public static final String SOURCE_RETRO_NPC_SWAPPER = "retro-npc-swapper";
	public static final String SOURCE_CUSTOM_SERVANTS = "custom-servants";

	/** The other sources this plugin reads from, in the order their posers are tried. */
	public static final List<String> PARTNERS = List.of(SOURCE_RETRO_NPC_SWAPPER, SOURCE_CUSTOM_NPC_MODELS);

	static final String KEY_VERSION = "version";
	static final String KEY_SOURCE = "source";
	static final String KEY_NPC_IDS = "npcIds";
	static final String KEY_OWNING = "owning";
	static final String KEY_POSER = "poser";

	private ModelSwapProtocol()
	{
	}

	/**
	 * A partner's outlines state: whether it draws Interact Highlight's NPC outlines, and how to pose
	 * the NPCs it swaps.
	 */
	@Value
	public static class Outlines
	{
		/** Whether the partner has taken Interact Highlight's NPC outlines over. */
		boolean owning;

		/**
		 * The model the partner draws an NPC with, or null when it draws none for it. Null when the
		 * partner has withdrawn it. Client thread only, and the model is overwritten by the partner's
		 * next pose, so it has to be used straight away.
		 */
		@Nullable
		Function<NPC, Model> poser;
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
	 * The message a plugin posts once it is listening, from {@code source}.
	 */
	public static PluginMessage synMessage(String source)
	{
		return new PluginMessage(NAMESPACE, SYN, header(source));
	}

	/**
	 * The outlines message from {@code source}. A null {@code poser} withdraws the one sent before.
	 */
	public static PluginMessage outlinesMessage(String source, boolean owning, Function<NPC, Model> poser)
	{
		Map<String, Object> data = header(source);
		data.put(KEY_OWNING, owning);
		if (poser != null)
		{
			data.put(KEY_POSER, poser);
		}
		return new PluginMessage(NAMESPACE, OUTLINES, data);
	}

	/**
	 * The opt-out message from {@code source}.
	 */
	public static PluginMessage optOutMessage(String source)
	{
		return new PluginMessage(NAMESPACE, OUTLINES_OPT_OUT, header(source));
	}

	/**
	 * The partner a {@code name} message is from, or null when {@code message} is anything else - of
	 * another name or version, or from a source not in {@link #PARTNERS}.
	 */
	public static String partnerSource(PluginMessage message, String name)
	{
		if (!isMessage(message, name))
		{
			return null;
		}

		// List.of throws on a null lookup, and a malformed message must not throw in the handler
		Object source = message.getData().get(KEY_SOURCE);
		return source instanceof String && PARTNERS.contains(source) ? (String) source : null;
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

	/**
	 * The outlines state in an outlines message from {@code fromSource}, or null when
	 * {@code message} is anything else.
	 */
	@SuppressWarnings("unchecked")
	public static Outlines readOutlines(PluginMessage message, String fromSource)
	{
		if (!isFrom(message, OUTLINES, fromSource))
		{
			return null;
		}

		Object owning = message.getData().get(KEY_OWNING);
		if (!(owning instanceof Boolean))
		{
			return null;
		}

		// Generics are erased, so Function is as far as this can check; the partners are the only
		// senders and put nothing else there
		Object poser = message.getData().get(KEY_POSER);
		return new Outlines((Boolean) owning, poser instanceof Function ? (Function<NPC, Model>) poser : null);
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
		return source != null && isMessage(message, name) && source.equals(message.getData().get(KEY_SOURCE));
	}

	/** Whether {@code message} is a {@code name} message of this protocol, in a version it speaks. */
	private static boolean isMessage(PluginMessage message, String name)
	{
		return message != null
			&& NAMESPACE.equals(message.getNamespace())
			&& name.equals(message.getName())
			&& message.getData() != null
			&& Integer.valueOf(VERSION).equals(message.getData().get(KEY_VERSION));
	}
}
