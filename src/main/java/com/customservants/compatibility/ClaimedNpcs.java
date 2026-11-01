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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Singleton;

/**
 * The NPC ids each partner says it is drawing, kept per source so one partner's claims never
 * replace another's, and the union of them all, which this plugin yields: Custom NPC Models takes
 * precedence over this plugin for any servant it holds a claim on, so that servant gets no servant
 * model, animations, chathead or claim from here.
 * <p>
 * Written on whatever thread posted the claims - a partner can post from its shutDown as well as
 * from the client thread - so the writes are synchronized and the union is published through a
 * volatile field, read per NPC per frame without locking.
 */
@Singleton
public class ClaimedNpcs
{
	private final Map<String, Set<Integer>> claimsBySource = new HashMap<>();

	private volatile Set<Integer> claimed = Collections.emptySet();

	/**
	 * Records {@code npcIds} as everything {@code source} now claims, replacing what it claimed
	 * before. Returns whether the union changed.
	 */
	public synchronized boolean put(String source, Set<Integer> npcIds)
	{
		if (npcIds.isEmpty())
		{
			return remove(source);
		}
		claimsBySource.put(source, Set.copyOf(npcIds));
		return rebuild();
	}

	/** Forgets everything {@code source} claimed, as it stops. Returns whether the union changed. */
	public synchronized boolean remove(String source)
	{
		return claimsBySource.remove(source) != null && rebuild();
	}

	/** Forgets every partner's claims, as this plugin stops. */
	public synchronized void clear()
	{
		claimsBySource.clear();
		claimed = Collections.emptySet();
	}

	/** Whether any partner claims {@code npcId}. */
	public boolean isClaimed(int npcId)
	{
		return claimed.contains(npcId);
	}

	/** Every NPC id some partner claims. Cannot be modified. */
	public Set<Integer> claimed()
	{
		return claimed;
	}

	private boolean rebuild()
	{
		Set<Integer> union = new HashSet<>();
		for (Set<Integer> ids : claimsBySource.values())
		{
			union.addAll(ids);
		}

		if (union.equals(claimed))
		{
			return false;
		}
		claimed = Collections.unmodifiableSet(union);
		return true;
	}
}
