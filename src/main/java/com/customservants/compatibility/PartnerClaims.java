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
import java.util.Collections;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.eventbus.EventBus;

/**
 * Tells Retro NPC Swapper which NPC ids this plugin is drawing, so it leaves them alone. Client
 * thread only, except where said.
 */
@Singleton
@Slf4j
public class PartnerClaims
{
	private final EventBus eventBus;

	// The NPC ids last claimed, so an unchanged set is not posted again. Volatile, as withdraw may
	// run on any thread.
	private volatile Set<Integer> posted = Collections.emptySet();

	@Inject
	PartnerClaims(EventBus eventBus)
	{
		this.eventBus = eventBus;
	}

	/**
	 * Posts our claims: {@link ServantIds#allIds()} when claiming, else the empty set, so Retro NPC
	 * Swapper can have the servants back. Posted only when the set changed, unless {@code always}.
	 */
	public void publish(boolean claiming, boolean always)
	{
		Set<Integer> claims = claiming ? ServantIds.allIds() : Collections.emptySet();

		if (!always && claims.equals(posted))
		{
			return;
		}

		posted = claims;
		log.debug("Claiming {} servant NPC id(s)", claims.size());
		eventBus.post(ModelSwapProtocol.claimsMessage(claims));
	}

	/**
	 * Posts the empty set unconditionally, as the plugin stops. Nothing is computed, so any thread
	 * will do, which lets Retro NPC Swapper take the servants back even if queued client thread work
	 * never runs. Also forgets what was posted, so the next start claims afresh.
	 */
	public void withdraw()
	{
		posted = Collections.emptySet();
		eventBus.post(ModelSwapProtocol.claimsMessage(Collections.emptySet()));
	}
}
