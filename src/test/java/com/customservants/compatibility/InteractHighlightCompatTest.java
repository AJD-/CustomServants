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

import com.customservants.CustomServantsConfig;
import com.google.inject.Guice;
import java.util.HashMap;
import java.util.Map;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.interacthighlight.InteractHighlightConfig;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Stashing, suppressing and restoring Interact Highlight's NPC outline settings, against a
 * ConfigManager backed by a plain map of saved values.
 */
public class InteractHighlightCompatTest
{
	private static final String GROUP = "interacthighlight";
	private static final String[] KEYS = {"npcShowHover", "npcShowInteract"};

	/** Saved config, as group.key to value. */
	private final Map<String, String> saved = new HashMap<>();

	private final ConfigManager configManager = mock(ConfigManager.class);

	private InteractHighlightCompat compat;

	@Before
	public void setUp()
	{
		when(configManager.getConfiguration(anyString(), anyString()))
			.thenAnswer(call -> saved.get(call.getArgument(0) + "." + call.getArgument(1)));
		doAnswer(call -> saved.put(call.getArgument(0) + "." + call.getArgument(1), call.getArgument(2)))
			.when(configManager).setConfiguration(anyString(), anyString(), anyString());
		doAnswer(call -> saved.remove(call.getArgument(0) + "." + call.getArgument(1)))
			.when(configManager).unsetConfiguration(anyString(), anyString());

		compat = Guice.createInjector(binder ->
		{
			binder.bind(ConfigManager.class).toInstance(configManager);
			binder.bind(PluginManager.class).toInstance(mock(PluginManager.class));
		}).getInstance(InteractHighlightCompat.class);
	}

	private String live(String key)
	{
		return saved.get(GROUP + "." + key);
	}

	private void setLive(String key, String value)
	{
		saved.put(GROUP + "." + key, value);
	}

	private void setStash(String key, String value)
	{
		saved.put(CustomServantsConfig.GROUP + "." + InteractHighlightCompat.stashKey(key), value);
	}

	private boolean anythingStashed()
	{
		return saved.keySet().stream().anyMatch(key -> key.startsWith(CustomServantsConfig.GROUP + "."));
	}

	private static ConfigProfile profile(long id)
	{
		ConfigProfile profile = mock(ConfigProfile.class);
		when(profile.getId()).thenReturn(id);
		return profile;
	}

	private static ConfigChanged changed(String group, String key, String newValue)
	{
		ConfigChanged event = new ConfigChanged();
		event.setGroup(group);
		event.setKey(key);
		event.setOldValue("false");
		event.setNewValue(newValue);
		return event;
	}

	private static ConfigChanged hoverTicked()
	{
		return changed(GROUP, "npcShowHover", "true");
	}

	@Test
	public void suppressTurnsOffBothNpcOutlinesAndLeavesObjectsAlone()
	{
		setLive("npcShowHover", "true");
		setLive("objectShowHover", "true");

		compat.suppress();

		for (String key : KEYS)
		{
			assertEquals(key, "false", live(key));
		}
		assertEquals("true", live("objectShowHover"));
		assertEquals("true", compat.stashed("npcShowHover"));
		assertEquals("unset", compat.stashed("npcShowInteract"));
	}

	@Test
	public void restorePutsBackWhatTheUserHad()
	{
		setLive("npcShowHover", "true");

		compat.suppress();
		compat.restore();

		assertEquals("true", live("npcShowHover"));
		// Never saved before, so left unsaved and the plugin's default stands
		assertNull(live("npcShowInteract"));
		assertFalse("the stash is cleared once restored", anythingStashed());
	}

	/** A session that stopped while suppressing is repaired on the next start. */
	@Test
	public void aStaleStashIsRestored()
	{
		setLive("npcShowHover", "true");
		compat.suppress();
		compat.forget();

		compat.restoreStaleStash();

		assertEquals("true", live("npcShowHover"));
		assertNull(live("npcShowInteract"));
		assertFalse(anythingStashed());
	}

	@Test
	public void restoreStaleStashWithNoStashWritesNothing()
	{
		setLive("npcShowHover", "false");

		compat.restoreStaleStash();

		assertEquals("false", live("npcShowHover"));
		assertFalse(anythingStashed());
	}

	/**
	 * A stash already there - left by a session that never restored it - holds the user's real
	 * value; suppressing again must not stash our own false over it.
	 */
	@Test
	public void anExistingStashIsKeptOnASecondSuppress()
	{
		setStash("npcShowHover", "true");
		setLive("npcShowHover", "false");

		compat.suppress();
		assertEquals("true", compat.stashed("npcShowHover"));
		compat.restore();

		assertEquals("true", live("npcShowHover"));
		assertFalse(anythingStashed());
	}

	@Test
	public void suppressAfterForgetKeepsTheFirstStash()
	{
		setLive("npcShowHover", "true");
		compat.suppress();
		compat.forget();

		compat.suppress();
		compat.restore();

		assertEquals("true", live("npcShowHover"));
		assertNull(live("npcShowInteract"));
	}

	/** The key the user just set keeps their value; the other goes back to what they had. */
	@Test
	public void optingOutKeepsTheUsersChoiceAndRestoresTheOther()
	{
		setLive("npcShowInteract", "true");
		compat.suppress();
		// The user ticks hover back on; this is the value they chose, not what the stash says
		setLive("npcShowHover", "true");

		compat.optOut("npcShowHover");

		assertEquals("true", live("npcShowHover"));
		assertEquals("true", live("npcShowInteract"));
		assertFalse(anythingStashed());
		assertFalse("no longer suppressing", compat.isUserOverride(hoverTicked()));
	}

	@Test
	public void tickingHoverWhileSuppressingIsAnOverride()
	{
		compat.suppress();

		assertTrue(compat.isUserOverride(hoverTicked()));
		assertTrue(compat.isUserOverride(changed(GROUP, "npcShowInteract", "true")));
	}

	@Test
	public void unTickingIsNotAnOverride()
	{
		compat.suppress();

		assertFalse(compat.isUserOverride(changed(GROUP, "npcShowHover", "false")));
	}

	@Test
	public void anotherGroupOrKeyIsNotAnOverride()
	{
		compat.suppress();

		assertFalse(compat.isUserOverride(changed("npchighlight", "npcShowHover", "true")));
		assertFalse(compat.isUserOverride(changed(GROUP, "objectShowHover", "true")));
	}

	@Test
	public void tickingWhileNotSuppressingIsNotAnOverride()
	{
		assertFalse(compat.isUserOverride(hoverTicked()));
	}

	@Test
	public void tickingWhileSuppressedOnTheSameProfileIsAnOverride()
	{
		ConfigProfile profile = profile(1);
		when(configManager.getProfile()).thenReturn(profile);
		compat.suppress();

		assertTrue(compat.isUserOverride(hoverTicked()));
	}

	/**
	 * A profile switch posts the new profile's saved values before ProfileChanged, after ConfigManager
	 * has moved to that profile. A saved true there is not the user turning the outlines back on.
	 */
	@Test
	public void aProfileSwitchIsNotAnOverride()
	{
		ConfigProfile first = profile(1);
		ConfigProfile second = profile(2);
		when(configManager.getProfile()).thenReturn(first);
		compat.suppress();

		when(configManager.getProfile()).thenReturn(second);
		assertFalse(compat.isUserOverride(hoverTicked()));
	}

	/** While suppressing, the live value is our own false; the stash holds the user's real choice. */
	@Test
	public void whileSuppressingTheAccessorsReadTheStash()
	{
		InteractHighlightConfig ihConfig = mock(InteractHighlightConfig.class);
		when(configManager.getConfig(InteractHighlightConfig.class)).thenReturn(ihConfig);
		// The user had hover on; the config proxy answers false for both, as our suppression leaves them
		setLive("npcShowHover", "true");

		compat.suppress();

		assertEquals("true", compat.stashed("npcShowHover"));
		assertTrue("stashed true", compat.npcShowHover());
		assertEquals("unset", compat.stashed("npcShowInteract"));
		assertTrue("stashed unset counts as the plugin's default, on", compat.npcShowInteract());
	}

	@Test
	public void notSuppressingTheAccessorsReadTheLiveConfig()
	{
		InteractHighlightConfig ihConfig = mock(InteractHighlightConfig.class);
		when(configManager.getConfig(InteractHighlightConfig.class)).thenReturn(ihConfig);
		when(ihConfig.npcShowInteract()).thenReturn(true);
		setStash("npcShowHover", "true");

		assertFalse(compat.npcShowHover());
		assertTrue(compat.npcShowInteract());
	}

	@Test
	public void stashKeyIsPrefixed()
	{
		assertEquals("interactHighlightStash_npcShowHover", InteractHighlightCompat.stashKey("npcShowHover"));
	}
}
