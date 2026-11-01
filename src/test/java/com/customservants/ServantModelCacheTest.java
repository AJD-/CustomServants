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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import com.customservants.compatibility.ClaimedNpcs;
import com.customservants.compatibility.ModelSwapProtocol;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.gameval.NpcID;
import org.junit.Before;
import org.junit.Test;

/**
 * Which NPCs the cache poses: the servants, less any a partner claims.
 */
public class ServantModelCacheTest
{
	private static final int MAID = NpcID.POH_SERVANT_WAITER_WOMAN;

	private ServantModelCache cache;
	private ClaimedNpcs claimed;

	@Before
	public void setUp()
	{
		Injector injector = Guice.createInjector(binder -> binder.bind(Client.class).toInstance(mock(Client.class)));
		cache = injector.getInstance(ServantModelCache.class);
		claimed = injector.getInstance(ClaimedNpcs.class);
	}

	@Test
	public void testAServantIsOursUntilAPartnerClaimsIt()
	{
		assertTrue(cache.isOurs(MAID));

		claimed.put(ModelSwapProtocol.SOURCE_CUSTOM_NPC_MODELS, Set.of(MAID));
		assertFalse(cache.isOurs(MAID));

		claimed.remove(ModelSwapProtocol.SOURCE_CUSTOM_NPC_MODELS);
		assertTrue(cache.isOurs(MAID));
	}

	@Test
	public void testANonServantIsNeverOurs()
	{
		assertFalse(cache.isOurs(NpcID.MYQ4_NOBLE_2));
	}
}
