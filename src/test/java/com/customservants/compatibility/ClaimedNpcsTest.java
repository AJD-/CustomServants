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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Collections;
import java.util.Set;
import org.junit.Test;

/**
 * The NPC ids the partners claim, which this plugin yields to them.
 */
public class ClaimedNpcsTest
{
	private static final String CNM = ModelSwapProtocol.SOURCE_CUSTOM_NPC_MODELS;
	private static final String RETRO = ModelSwapProtocol.SOURCE_RETRO_NPC_SWAPPER;

	private final ClaimedNpcs claimed = new ClaimedNpcs();

	@Test
	public void testNothingIsClaimedAtFirst()
	{
		assertEquals(Collections.emptySet(), claimed.claimed());
		assertFalse(claimed.isClaimed(1));
	}

	@Test
	public void testTheClaimsOfTwoSourcesAreJoined()
	{
		assertTrue(claimed.put(CNM, Set.of(1, 2)));
		assertTrue(claimed.put(RETRO, Set.of(3)));

		assertEquals(Set.of(1, 2, 3), claimed.claimed());
		assertTrue(claimed.isClaimed(1));
		assertTrue(claimed.isClaimed(3));
		assertFalse(claimed.isClaimed(4));
	}

	@Test
	public void testASourceReplacesOnlyItsOwnClaims()
	{
		claimed.put(CNM, Set.of(1, 2));
		claimed.put(RETRO, Set.of(3));

		assertTrue(claimed.put(CNM, Set.of(2)));

		assertEquals(Set.of(2, 3), claimed.claimed());
	}

	@Test
	public void testAnUnchangedUnionIsReportedAsUnchanged()
	{
		claimed.put(CNM, Set.of(1));

		assertFalse(claimed.put(CNM, Set.of(1)));
		assertFalse(claimed.put(RETRO, Set.of(1)));
		assertFalse(claimed.remove(RETRO));
	}

	@Test
	public void testAnEmptySetAndARemoveBothForgetTheSource()
	{
		claimed.put(CNM, Set.of(1));
		assertTrue(claimed.put(CNM, Collections.emptySet()));
		assertFalse(claimed.isClaimed(1));

		claimed.put(CNM, Set.of(1));
		assertTrue(claimed.remove(CNM));
		assertFalse(claimed.isClaimed(1));
		assertFalse(claimed.remove(CNM));
	}

	@Test
	public void testClearForgetsEverySource()
	{
		claimed.put(CNM, Set.of(1));
		claimed.put(RETRO, Set.of(2));

		claimed.clear();

		assertEquals(Collections.emptySet(), claimed.claimed());
	}
}
