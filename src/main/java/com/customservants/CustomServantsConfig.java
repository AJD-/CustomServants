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

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(CustomServantsConfig.GROUP)
public interface CustomServantsConfig extends Config
{
	String GROUP = "custom-servants";

	String SERVANT_OVERRIDE = "servantOverride";
	String OVERRIDE_INTERACT_HIGHLIGHT = "overrideInteractHighlight";

	@ConfigItem(
		keyName = SERVANT_OVERRIDE,
		name = "Servant override",
		description = "Draws your house servant as another NPC, with that NPC's chathead in dialogue. Needs the GPU plugin or 117 HD.",
		position = 0
	)
	default ServantOverride servantOverride()
	{
		return ServantOverride.DEFAULT;
	}

	@ConfigSection(
		name = "Compatibility",
		description = "Settings for working alongside other plugins",
		position = 1,
		closedByDefault = true
	)
	String compatibilitySection = "compatibilitySection";

	@ConfigItem(
		keyName = OVERRIDE_INTERACT_HIGHLIGHT,
		name = "Fix Interact Highlight outlines",
		description = "<html><body style='width:170px'>Draw the Interact Highlight plugin's NPC "
			+ "outlines around your servant's override instead of the original model.<br><br>While "
			+ "this is on and an override is being drawn, Interact Highlight's own <b>NPCs: Show on "
			+ "hover</b> and <b>Show on interact</b> are turned off and this plugin draws those "
			+ "outlines in their place, using that plugin's own colors and border settings. Both are "
			+ "put back to your own settings when this plugin stops.</body></html>",
		section = compatibilitySection,
		position = 1
	)
	default boolean overrideInteractHighlight()
	{
		return false;
	}
}
