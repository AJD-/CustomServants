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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * When a running partner's persisted stash says it is suppressing Interact Highlight's outlines.
 */
public class PartnerStashProbeTest
{
	private static final String RETRO_CLASS = "com.retronpcswapper.RetroNpcSwapperPlugin";
	private static final String CNM_CLASS = "com.customnpcmodels.CustomNpcModelsPlugin";
	private static final String RETRO_GROUP = "retronpcswapper";
	private static final String CNM_GROUP = "customnpcmodels";

	/** A config lookup holding a stash in exactly the {@code stashed} groups, under the stash key only. */
	private static BiFunction<String, String, String> stashIn(Set<String> stashed)
	{
		return (group, key) ->
		{
			assertEquals(PartnerStashProbe.STASH_KEY, key);
			return stashed.contains(group) ? "true" : null;
		};
	}

	@Test
	public void bothPartnersAreProbedUnderTheirOwnGroups()
	{
		assertEquals(Map.of(RETRO_CLASS, RETRO_GROUP, CNM_CLASS, CNM_GROUP), PartnerStashProbe.PARTNER_STASH_GROUPS);
		assertEquals("interactHighlightStash_npcShowHover", PartnerStashProbe.STASH_KEY);
	}

	@Test
	public void anActivePartnerWithAStashIsSuppressing()
	{
		assertTrue(PartnerStashProbe.anySuppressing(Map.of(RETRO_CLASS, RETRO_GROUP),
			RETRO_CLASS::equals, stashIn(Set.of(RETRO_GROUP))));
	}

	@Test
	public void anActivePartnerWithoutAStashIsNot()
	{
		assertFalse(PartnerStashProbe.anySuppressing(Map.of(RETRO_CLASS, RETRO_GROUP),
			RETRO_CLASS::equals, stashIn(Collections.emptySet())));
	}

	@Test
	public void anInactivePartnerWithAStashIsNot()
	{
		assertFalse(PartnerStashProbe.anySuppressing(Map.of(RETRO_CLASS, RETRO_GROUP),
			className -> false, stashIn(Set.of(RETRO_GROUP))));
	}

	@Test
	public void aStashInAnotherPartnersGroupDoesNotCount()
	{
		// Retro runs with no stash; the sibling plugin has one but is not running
		assertFalse(PartnerStashProbe.anySuppressing(PartnerStashProbe.PARTNER_STASH_GROUPS,
			RETRO_CLASS::equals, stashIn(Set.of(CNM_GROUP))));
	}

	@Test
	public void theSecondPartnerSuppressingIsEnough()
	{
		// Both run, Retro probed first with no stash, then the sibling plugin with one
		Map<String, String> ordered = new LinkedHashMap<>();
		ordered.put(RETRO_CLASS, RETRO_GROUP);
		ordered.put(CNM_CLASS, CNM_GROUP);
		assertTrue(PartnerStashProbe.anySuppressing(ordered, className -> true, stashIn(Set.of(CNM_GROUP))));
		assertTrue(PartnerStashProbe.anySuppressing(PartnerStashProbe.PARTNER_STASH_GROUPS,
			className -> true, stashIn(Set.of(CNM_GROUP))));
	}

	@Test
	public void retroConstantsMirrorRetrosConfig()
	{
		assertEquals(RETRO_CLASS, PartnerStashProbe.RETRO_PLUGIN_CLASS);
		assertEquals("retronpcswapper", PartnerStashProbe.RETRO_CONFIG_GROUP);
		assertEquals("overrideInteractHighlight", PartnerStashProbe.RETRO_OVERRIDE_KEY);
	}

	/** A config lookup holding {@code value} under Retro's override key only. */
	private static BiFunction<String, String, String> retroOverride(String value)
	{
		return (group, key) -> RETRO_GROUP.equals(group) && "overrideInteractHighlight".equals(key) ? value : null;
	}

	@Test
	public void retroRunningWithItsOptionTickedHasTheFixEnabled()
	{
		assertTrue(PartnerStashProbe.retroOutlineFixEnabled(RETRO_CLASS::equals, retroOverride("true")));
	}

	@Test
	public void retroRunningWithItsOptionUntickedOrUnsetDoesNot()
	{
		assertFalse(PartnerStashProbe.retroOutlineFixEnabled(RETRO_CLASS::equals, retroOverride("false")));
		assertFalse(PartnerStashProbe.retroOutlineFixEnabled(RETRO_CLASS::equals, retroOverride(null)));
	}

	@Test
	public void retroStoppedWithItsOptionTickedDoesNot()
	{
		assertFalse(PartnerStashProbe.retroOutlineFixEnabled(className -> false, retroOverride("true")));
		// Only Retro counts, not the sibling plugin
		assertFalse(PartnerStashProbe.retroOutlineFixEnabled(CNM_CLASS::equals, retroOverride("true")));
	}

	@Test
	public void noPartnersMeansNoSuppression()
	{
		assertFalse(PartnerStashProbe.anySuppressing(Collections.emptyMap(),
			className -> true, stashIn(Set.of(RETRO_GROUP, CNM_GROUP))));
	}
}
