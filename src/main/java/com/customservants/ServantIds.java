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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.Value;
import net.runelite.api.gameval.NpcID;

/**
 * Every NPC id a player-owned house servant can appear as, with the servant's role and rig.
 * <p>
 * Every servant is {@link Rig#HUMAN}: the cache shows all of them, the Demon butler included
 * (its Leagues variant has its own stand/walk ids), standing on the player skeleton, framemap 0.
 * <p>
 * {@link NpcID#POH_CHIEF_SERVANT} is deliberately absent: it is the Servants' Guild NPC, not a
 * house servant.
 */
public final class ServantIds
{
	@Value
	public static class Servant
	{
		int npcId;
		String role;
		Rig rig;
	}

	private static final Map<Integer, Servant> SERVANTS;
	private static final int[] IDS;

	static
	{
		Map<Integer, Servant> servants = new LinkedHashMap<>();
		add(servants, "Rick", Rig.HUMAN,
			NpcID.POH_SERVANT_DOGSBODY,
			NpcID.POH_SERVANT_MULTI_DOGSBODY,
			NpcID.POH_SERVANT_DOGSBODY_CUTSCENE);
		add(servants, "Maid", Rig.HUMAN,
			NpcID.POH_SERVANT_WAITER_WOMAN,
			NpcID.POH_SERVANT_MULTI_WAITER_WOMAN,
			NpcID.POH_SERVANT_WAITER_WOMAN_CUTSCENE);
		add(servants, "Cook", Rig.HUMAN,
			NpcID.POH_SERVANT_COOK_WOMAN,
			NpcID.POH_SERVANT_MULTI_COOK_WOMAN,
			NpcID.POH_SERVANT_COOK_WOMAN_CUTSCENE);
		add(servants, "Butler", Rig.HUMAN,
			NpcID.POH_SERVANT_MAITRE_D_MAN,
			NpcID.POH_SERVANT_MULTI_MAITRE_D_MAN,
			NpcID.POH_SERVANT_MAITRE_D_MAN_CUTSCENE);
		add(servants, "Demon butler", Rig.HUMAN,
			NpcID.POH_SERVANT_DEMON,
			NpcID.POH_SERVANT_MULTI_DEMON,
			NpcID.POH_SERVANT_DEMON_CUTSCENE,
			NpcID.POH_SERVANT_DEMON_LEAGUES_6);
		SERVANTS = Collections.unmodifiableMap(servants);
		IDS = servants.keySet().stream().mapToInt(Integer::intValue).sorted().toArray();
	}

	private ServantIds()
	{
	}

	private static void add(Map<Integer, Servant> servants, String role, Rig rig, int... npcIds)
	{
		for (int npcId : npcIds)
		{
			servants.put(npcId, new Servant(npcId, role, rig));
		}
	}

	/**
	 * @return the servant with this NPC id, or null when the id is not a house servant
	 */
	public static Servant get(int npcId)
	{
		// No boxing for the non-servant NPCs this is asked about every frame
		return isServant(npcId) ? SERVANTS.get(npcId) : null;
	}

	public static boolean isServant(int npcId)
	{
		return Arrays.binarySearch(IDS, npcId) >= 0;
	}

	/**
	 * @return every servant, unmodifiable
	 */
	public static Collection<Servant> all()
	{
		return SERVANTS.values();
	}

	/**
	 * @return every servant NPC id, unmodifiable
	 */
	public static Set<Integer> allIds()
	{
		return SERVANTS.keySet();
	}
}
