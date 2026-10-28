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
import java.util.function.BiFunction;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;

/**
 * Whether a partner plugin is already suppressing Interact Highlight's NPC outlines, judged from its
 * persisted stash rather than from a message. A partner too old to read our {@code outlines}
 * messages still turns Interact Highlight's hover outline off while it draws its own, stashing the
 * user's setting under {@link #STASH_KEY} in its own config group; a stash present while that
 * plugin runs means it has the outlines, and we must not take them over too.
 * <p>
 * Read-only: nothing here writes config. The partners are named by class name strings only, never
 * referenced, as they are loaded by other classloaders and may not be installed at all. Checked
 * only on takeover transitions, not per frame, so nothing is cached: a Hub install or update makes
 * a new plugin instance.
 */
@Singleton
public class PartnerStashProbe
{
	/** Each partner plugin's class name, mapped to the config group it keeps its stash in. */
	static final Map<String, String> PARTNER_STASH_GROUPS = stashGroups();

	/**
	 * The key both partners persist their stashed Interact Highlight hover setting under, the same
	 * key this plugin stashes it under in its own group.
	 */
	static final String STASH_KEY = InteractHighlightCompat.stashKey("npcShowHover");

	private final ConfigManager configManager;
	private final PluginManager pluginManager;

	@Inject
	PartnerStashProbe(ConfigManager configManager, PluginManager pluginManager)
	{
		this.configManager = configManager;
		this.pluginManager = pluginManager;
	}

	/** Whether any running partner has stashed Interact Highlight's hover setting. */
	public boolean isPartnerSuppressing()
	{
		return anySuppressing(PARTNER_STASH_GROUPS, this::isActiveByClassName, configManager::getConfiguration);
	}

	private static Map<String, String> stashGroups()
	{
		Map<String, String> groups = new LinkedHashMap<>();
		for (Partner partner : Partner.values())
		{
			groups.put(partner.getPluginClass(), partner.getStashGroup());
		}
		return Collections.unmodifiableMap(groups);
	}

	/** Whether the plugin of class {@code className} is loaded and running. */
	private boolean isActiveByClassName(String className)
	{
		for (Plugin plugin : pluginManager.getPlugins())
		{
			if (plugin.getClass().getName().equals(className))
			{
				return pluginManager.isPluginActive(plugin);
			}
		}
		return false;
	}

	/**
	 * Whether any entry of {@code groupsByClass} (plugin class name to config group) names a plugin
	 * {@code activeByClass} says is running, whose group holds a value under {@link #STASH_KEY}
	 * according to {@code getConfig(group, key)}.
	 */
	static boolean anySuppressing(Map<String, String> groupsByClass, Predicate<String> activeByClass,
		BiFunction<String, String, String> getConfig)
	{
		for (Map.Entry<String, String> entry : groupsByClass.entrySet())
		{
			if (activeByClass.test(entry.getKey()) && getConfig.apply(entry.getValue(), STASH_KEY) != null)
			{
				return true;
			}
		}
		return false;
	}
}
