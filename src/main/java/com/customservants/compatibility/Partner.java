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

import lombok.Getter;

/**
 * The partner plugins this one shares servants and Interact Highlight's NPC outlines with. Each is
 * another Hub plugin loaded by another classloader, so it is named by class name alone, never
 * referenced. In the order of {@link ModelSwapProtocol#PARTNERS}.
 */
@Getter
public enum Partner
{
	RETRO_NPC_SWAPPER(ModelSwapProtocol.SOURCE_RETRO_NPC_SWAPPER,
		"com.retronpcswapper.RetroNpcSwapperPlugin", "retronpcswapper", true),
	CUSTOM_NPC_MODELS(ModelSwapProtocol.SOURCE_CUSTOM_NPC_MODELS,
		"com.customnpcmodels.CustomNpcModelsPlugin", "customnpcmodels", false);

	/** The source its {@link ModelSwapProtocol} messages carry. */
	private final String source;

	/** Its plugin class name. */
	private final String pluginClass;

	// Mirrors the partner's own config group constant, which cannot be referenced from here
	/** The config group it keeps its Interact Highlight stash in. */
	private final String stashGroup;

	/** Whether it reads our claims; only Retro NPC Swapper does, the other reads only the outlines. */
	private final boolean readsClaims;

	Partner(String source, String pluginClass, String stashGroup, boolean readsClaims)
	{
		this.source = source;
		this.pluginClass = pluginClass;
		this.stashGroup = stashGroup;
		this.readsClaims = readsClaims;
	}

	/** The partner whose plugin class is named {@code className}, or null when none is. */
	public static Partner byPluginClass(String className)
	{
		for (Partner partner : values())
		{
			if (partner.pluginClass.equals(className))
			{
				return partner;
			}
		}
		return null;
	}

	/** The partner that posts as {@code source}, or null when none does. */
	public static Partner bySource(String source)
	{
		for (Partner partner : values())
		{
			if (partner.source.equals(source))
			{
				return partner;
			}
		}
		return null;
	}
}
