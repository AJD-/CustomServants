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
import java.util.List;
import java.util.Objects;

import net.runelite.api.gameval.NpcID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ServantIdsTest
{
	private static final List<Integer> HUMAN_SERVANT_IDS = Arrays.asList(
		NpcID.POH_SERVANT_DOGSBODY,
		NpcID.POH_SERVANT_MULTI_DOGSBODY,
		NpcID.POH_SERVANT_DOGSBODY_CUTSCENE,
		NpcID.POH_SERVANT_WAITER_WOMAN,
		NpcID.POH_SERVANT_MULTI_WAITER_WOMAN,
		NpcID.POH_SERVANT_WAITER_WOMAN_CUTSCENE,
		NpcID.POH_SERVANT_COOK_WOMAN,
		NpcID.POH_SERVANT_MULTI_COOK_WOMAN,
		NpcID.POH_SERVANT_COOK_WOMAN_CUTSCENE,
		NpcID.POH_SERVANT_MAITRE_D_MAN,
		NpcID.POH_SERVANT_MULTI_MAITRE_D_MAN,
		NpcID.POH_SERVANT_MAITRE_D_MAN_CUTSCENE);

	private static final List<Integer> DEMON_BUTLER_IDS = Arrays.asList(
		NpcID.POH_SERVANT_DEMON,
		NpcID.POH_SERVANT_MULTI_DEMON,
		NpcID.POH_SERVANT_DEMON_CUTSCENE,
		NpcID.POH_SERVANT_DEMON_LEAGUES_6);

	@Test
	public void hasExactlySixteenServantIds()
	{
		// 12 for Rick, Maid, Cook and Butler + 4 for the Demon butler
		assertEquals(16, ServantIds.allIds().size());
		assertEquals(16, ServantIds.all().size());
		assertTrue(ServantIds.allIds().containsAll(HUMAN_SERVANT_IDS));
		assertTrue(ServantIds.allIds().containsAll(DEMON_BUTLER_IDS));
	}

	@Test
	public void chiefServantIsAbsent()
	{
		assertFalse(ServantIds.isServant(NpcID.POH_CHIEF_SERVANT));
		assertNull(ServantIds.get(NpcID.POH_CHIEF_SERVANT));
	}

	@Test
	public void everyServantIsHumanRig()
	{
		// The cache puts every servant, the Demon butler included, on the player skeleton (framemap 0)
		assertEquals(16, ServantIds.all().size());
		for (ServantIds.Servant servant : ServantIds.all())
		{
			assertEquals(servant.getRole() + " " + servant.getNpcId(), Rig.HUMAN, servant.getRig());
		}
	}

	@Test
	public void listedIdsAreServantsWithTheirOwnId()
	{
		for (int id : HUMAN_SERVANT_IDS)
		{
			ServantIds.Servant servant = ServantIds.get(id);
			assertNotNull("missing servant " + id, servant);
			assertEquals(id, servant.getNpcId());
			assertEquals(Rig.HUMAN, servant.getRig());
		}
		for (int id : DEMON_BUTLER_IDS)
		{
			ServantIds.Servant servant = ServantIds.get(id);
			assertNotNull("missing servant " + id, servant);
			assertEquals(id, servant.getNpcId());
			assertEquals("Demon butler", servant.getRole());
			assertEquals(Rig.HUMAN, servant.getRig());
		}
	}

	@Test
	public void rolesMatchTheirIds()
	{
		assertEquals("Rick", Objects.requireNonNull(ServantIds.get(NpcID.POH_SERVANT_MULTI_DOGSBODY)).getRole());
		assertEquals("Maid", Objects.requireNonNull(ServantIds.get(NpcID.POH_SERVANT_MULTI_WAITER_WOMAN)).getRole());
		assertEquals("Cook", Objects.requireNonNull(ServantIds.get(NpcID.POH_SERVANT_MULTI_COOK_WOMAN)).getRole());
		assertEquals("Butler", Objects.requireNonNull(ServantIds.get(NpcID.POH_SERVANT_MULTI_MAITRE_D_MAN)).getRole());
	}

	@Test
	public void getOfNonServantReturnsNull()
	{
		assertNull(ServantIds.get(NpcID.THURGO));
		assertFalse(ServantIds.isServant(NpcID.THURGO));
	}

	@Test(expected = UnsupportedOperationException.class)
	public void allIdsIsUnmodifiable()
	{
		ServantIds.allIds().add(NpcID.THURGO);
	}

	@Test(expected = UnsupportedOperationException.class)
	public void allIsUnmodifiable()
	{
		ServantIds.all().clear();
	}
}
