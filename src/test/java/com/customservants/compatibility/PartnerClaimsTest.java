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

import com.customservants.ServantIds;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * What this plugin claims to Retro NPC Swapper, and when.
 */
public class PartnerClaimsTest
{
	private EventBus eventBus;
	private PartnerClaims claims;
	// What the plugin's canSwap answers
	private final AtomicBoolean claiming = new AtomicBoolean();

	@Before
	public void setUp()
	{
		eventBus = mock(EventBus.class);
		claims = new PartnerClaims(eventBus);
		claims.start(claiming::get);
	}

	private void publish(boolean claim, boolean always)
	{
		claiming.set(claim);
		claims.publish(always);
	}

	/**
	 * The claims in each message posted so far, oldest first, after checking exactly {@code count}
	 * were posted and each is a claims message from this plugin.
	 */
	private List<Set<Integer>> posted(int count)
	{
		ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
		verify(eventBus, times(count)).post(captor.capture());

		List<Set<Integer>> all = new ArrayList<>();
		for (Object event : captor.getAllValues())
		{
			Set<Integer> ids = ModelSwapProtocol.readClaims((PluginMessage) event, ModelSwapProtocol.SOURCE_CUSTOM_SERVANTS);
			assertNotNull("not a claims message from this plugin: " + event, ids);
			all.add(ids);
		}
		return all;
	}

	@Test
	public void claimingPostsEveryServantId()
	{
		assertFalse(ServantIds.allIds().isEmpty());

		publish(true, false);

		assertEquals(List.of(ServantIds.allIds()), posted(1));
	}

	@Test
	public void anUnchangedClaimIsNotPostedAgain()
	{
		publish(true, false);
		publish(true, false);

		assertEquals(List.of(ServantIds.allIds()), posted(1));
	}

	@Test
	public void aForcedClaimIsPostedAgain()
	{
		publish(true, false);
		publish(true, true);

		assertEquals(List.of(ServantIds.allIds(), ServantIds.allIds()), posted(2));
	}

	@Test
	public void notClaimingAfterAClaimPostsTheEmptySet()
	{
		publish(true, false);
		publish(false, false);

		assertEquals(List.of(ServantIds.allIds(), Collections.emptySet()), posted(2));
	}

	@Test
	public void notClaimingFromTheStartPostsNothingUnlessForced()
	{
		publish(false, false);
		verifyNoInteractions(eventBus);

		publish(false, true);
		assertEquals(List.of(Collections.emptySet()), posted(1));
	}

	@Test
	public void withdrawPostsTheEmptySetEvenWhenNothingWasPosted()
	{
		claims.withdraw();

		assertEquals(List.of(Collections.emptySet()), posted(1));
	}

	@Test
	public void afterAWithdrawTheNextClaimIsPostedUnforced()
	{
		publish(true, false);
		claims.withdraw();
		// A restart's first recheck claims again without needing to be forced
		publish(true, false);

		assertEquals(List.of(ServantIds.allIds(), Collections.emptySet(), ServantIds.allIds()), posted(3));
	}
}
