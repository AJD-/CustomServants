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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.gameval.NpcID;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ServantOverrideTest
{
	private static final List<String> LABELS = Arrays.asList(
		"Default",
		"Vyrelady (Human)",
		"Vyrelady (Vampyre)",
		"Vyrelord (Human)",
		"Vyrelord (Vampyre)",
		"Princess Astrid",
		"Prince Brand",
		"Elena",
		"Amy (Mahogany Homes)",
		"Crondis",
		"Scabaras",
		"Amascut",
		"Apmeken",
		"Zanik",
		"Thurgo",
		"Gnome Child",
		"Gnome Cheerleader",
		"Ali Morrisane");

	@Test
	public void hasEighteenConstantsWithDefaultFirst()
	{
		assertEquals(18, ServantOverride.values().length);
		assertEquals(ServantOverride.DEFAULT, ServantOverride.values()[0]);
	}

	@Test
	public void toStringIsTheLabelInOrder()
	{
		ServantOverride[] values = ServantOverride.values();
		for (int i = 0; i < values.length; i++)
		{
			assertEquals(LABELS.get(i), values[i].toString());
			assertEquals(LABELS.get(i), values[i].getLabel());
		}
	}

	@Test
	public void onlyDefaultHasNoIds()
	{
		assertEquals(0, ServantOverride.DEFAULT.getNpcIds().length);
		for (ServantOverride override : ServantOverride.values())
		{
			if (override != ServantOverride.DEFAULT)
			{
				assertTrue(override + " has no ids", override.getNpcIds().length >= 1);
			}
		}
	}

	@Test
	public void noIdAppearsTwice()
	{
		Set<Integer> seen = new HashSet<>();
		for (ServantOverride override : ServantOverride.values())
		{
			for (int id : override.getNpcIds())
			{
				assertTrue(override + " repeats id " + id, seen.add(id));
			}
		}
	}

	@Test
	public void noIdIsAServant()
	{
		for (ServantOverride override : ServantOverride.values())
		{
			for (int id : override.getNpcIds())
			{
				assertFalse(override + " uses servant id " + id, ServantIds.isServant(id));
			}
		}
	}

	@Test
	public void retextureArraysHaveEqualLength()
	{
		for (ServantOverride override : ServantOverride.values())
		{
			assertEquals(override.toString(), override.getRetextureFind().length, override.getRetextureReplace().length);
		}
	}

	@Test
	public void getNpcIdsReturnsACopy()
	{
		int[] ids = ServantOverride.THURGO.getNpcIds();
		ids[0] = NpcID.POH_CHIEF_SERVANT;
		assertArrayEquals(new int[]{NpcID.THURGO}, ServantOverride.THURGO.getNpcIds());
	}

	@Test
	public void firstMatchingReturnsTheFirstMatch()
	{
		int[] ids = {NpcID.MYQ4_NOBLE_2, NpcID.TOB_NOBLE_2, NpcID.TOB_NOBLE_4};
		assertEquals(NpcID.TOB_NOBLE_2, ServantOverride.firstMatching(ids, id -> id != NpcID.MYQ4_NOBLE_2));
		assertEquals(NpcID.MYQ4_NOBLE_2, ServantOverride.firstMatching(ids, id -> true));
	}

	@Test
	public void firstMatchingReturnsMinusOneWhenNoneMatch()
	{
		int[] ids = {NpcID.MYQ4_NOBLE_2, NpcID.TOB_NOBLE_2};
		assertEquals(-1, ServantOverride.firstMatching(ids, id -> false));
		assertEquals(-1, ServantOverride.firstMatching(new int[0], id -> true));
		assertEquals(-1, ServantOverride.firstMatching(null, id -> true));
	}

	@Test
	public void isOverrideFalseOnlyForDefault()
	{
		for (ServantOverride override : ServantOverride.values())
		{
			assertEquals(override.toString(), override != ServantOverride.DEFAULT, override.isOverride());
		}
	}
}
