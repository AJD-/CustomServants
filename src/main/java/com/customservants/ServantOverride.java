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

import java.util.function.IntPredicate;
import lombok.AccessLevel;
import lombok.Getter;
import net.runelite.api.gameval.NpcID;

/**
 * The NPC a house servant can be drawn as. Each entry lists the NPC ids to borrow the body model and
 * chathead from (first id that has one wins), plus what is needed to animate and light that model.
 */
@Getter
public enum ServantOverride
{
	DEFAULT("Default", new int[0],
		Rig.OTHER, -1, -1, 0, 0, Unknown.NO_SHORTS, Unknown.NO_SHORTS, ChatheadEmote.KEEP),
	// T3: fill from the cache dump
	VYRELADY_HUMAN("Vyrelady (Human)", new int[]{NpcID.MYQ4_NOBLE_2, NpcID.TOB_NOBLE_2},
		Rig.OTHER, -1, -1, 0, 0, Unknown.NO_SHORTS, Unknown.NO_SHORTS, ChatheadEmote.KEEP),
	// T3: fill from the cache dump
	VYRELADY_VAMPYRE("Vyrelady (Vampyre)", new int[]{NpcID.TOB_NOBLE_4},
		Rig.OTHER, -1, -1, 0, 0, Unknown.NO_SHORTS, Unknown.NO_SHORTS, ChatheadEmote.KEEP),
	// T3: fill from the cache dump
	VYRELORD_HUMAN("Vyrelord (Human)", new int[]{NpcID.MYQ4_NOBLE_1, NpcID.TOB_NOBLE_1},
		Rig.OTHER, -1, -1, 0, 0, Unknown.NO_SHORTS, Unknown.NO_SHORTS, ChatheadEmote.KEEP),
	// T3: fill from the cache dump
	VYRELORD_VAMPYRE("Vyrelord (Vampyre)", new int[]{NpcID.TOB_NOBLE_3},
		Rig.OTHER, -1, -1, 0, 0, Unknown.NO_SHORTS, Unknown.NO_SHORTS, ChatheadEmote.KEEP),
	// T3: fill from the cache dump
	PRINCESS_ASTRID("Princess Astrid", new int[]{NpcID.MISC_PRINCESS_ASTRID, NpcID.ROYAL_CUTSCENE_PRINCESS_ASTRID},
		Rig.OTHER, -1, -1, 0, 0, Unknown.NO_SHORTS, Unknown.NO_SHORTS, ChatheadEmote.KEEP),
	// T3: fill from the cache dump
	PRINCE_BRAND("Prince Brand", new int[]{NpcID.MISC_PRINCE_BRAND, NpcID.ROYAL_CUTSCENE_PRINCE_BRAND},
		Rig.OTHER, -1, -1, 0, 0, Unknown.NO_SHORTS, Unknown.NO_SHORTS, ChatheadEmote.KEEP),
	// T3: fill from the cache dump
	ELENA("Elena", new int[]{NpcID.ELENA2_VIS},
		Rig.OTHER, -1, -1, 0, 0, Unknown.NO_SHORTS, Unknown.NO_SHORTS, ChatheadEmote.KEEP),
	// T3: fill from the cache dump
	AMY("Amy (Mahogany Homes)", new int[]{NpcID.CON_CONTRACTOR_1OP, NpcID.CON_CONTRACTOR_3OP},
		Rig.OTHER, -1, -1, 0, 0, Unknown.NO_SHORTS, Unknown.NO_SHORTS, ChatheadEmote.KEEP),
	// T3: fill from the cache dump
	CRONDIS("Crondis", new int[]{NpcID.TOA_CRONDIS_FREED_VIS, NpcID.TOA_CRONDIS_NEXUS_VIS},
		Rig.OTHER, -1, -1, 0, 0, Unknown.NO_SHORTS, Unknown.NO_SHORTS, ChatheadEmote.KEEP),
	// T3: fill from the cache dump
	SCABARAS("Scabaras", new int[]{NpcID.TOA_SCABARAS_FREED_VIS, NpcID.TOA_SCABARAS_NEXUS_VIS},
		Rig.OTHER, -1, -1, 0, 0, Unknown.NO_SHORTS, Unknown.NO_SHORTS, ChatheadEmote.KEEP),
	// T3: fill from the cache dump
	AMASCUT("Amascut", new int[]{NpcID.TOA_AMASCUT_VIS},
		Rig.OTHER, -1, -1, 0, 0, Unknown.NO_SHORTS, Unknown.NO_SHORTS, ChatheadEmote.KEEP),
	// T3: fill from the cache dump
	APMEKEN("Apmeken", new int[]{NpcID.TOA_APMEKEN_FREED_VIS, NpcID.TOA_APMEKEN_NEXUS_VIS},
		Rig.OTHER, -1, -1, 0, 0, Unknown.NO_SHORTS, Unknown.NO_SHORTS, ChatheadEmote.KEEP),
	// T3: fill from the cache dump
	ZANIK("Zanik", new int[]{NpcID.DTTD_ZANIK_UNMARKED, NpcID.DTTD_ZANIK_WITH_TOG_BOWL},
		Rig.OTHER, -1, -1, 0, 0, Unknown.NO_SHORTS, Unknown.NO_SHORTS, ChatheadEmote.KEEP),
	// T3: fill from the cache dump
	THURGO("Thurgo", new int[]{NpcID.THURGO},
		Rig.OTHER, -1, -1, 0, 0, Unknown.NO_SHORTS, Unknown.NO_SHORTS, ChatheadEmote.KEEP),
	// T3: fill from the cache dump
	GNOME_CHILD("Gnome Child", new int[]{NpcID.GNOMECHILDGREEN},
		Rig.OTHER, -1, -1, 0, 0, Unknown.NO_SHORTS, Unknown.NO_SHORTS, ChatheadEmote.KEEP),
	// T3: fill from the cache dump
	GNOME_CHEERLEADER("Gnome Cheerleader", new int[]{NpcID.GNOMECHEERLEADER},
		Rig.OTHER, -1, -1, 0, 0, Unknown.NO_SHORTS, Unknown.NO_SHORTS, ChatheadEmote.KEEP),
	// T3: fill from the cache dump
	ALI_MORRISANE("Ali Morrisane", new int[]{NpcID.FEUD_ALI_M},
		Rig.OTHER, -1, -1, 0, 0, Unknown.NO_SHORTS, Unknown.NO_SHORTS, ChatheadEmote.KEEP);

	/**
	 * Holder so the enum constants above can use the empty array without an illegal forward reference.
	 */
	private static final class Unknown
	{
		private static final short[] NO_SHORTS = new short[0];
	}

	private final String label;
	@Getter(AccessLevel.NONE)
	private final int[] npcIds;
	/**
	 * Skeleton class of the override's body model.
	 */
	private final Rig rig;
	/**
	 * Stand sequence id, -1 when unknown.
	 */
	private final int standAnimation;
	/**
	 * Walk sequence id, -1 when unknown.
	 */
	private final int walkAnimation;
	/**
	 * Raw ambient byte from the NPC's cache definition.
	 */
	private final int ambient;
	/**
	 * Raw contrast byte from the NPC's cache definition.
	 */
	private final int contrast;
	@Getter(AccessLevel.NONE)
	private final short[] retextureFind;
	@Getter(AccessLevel.NONE)
	private final short[] retextureReplace;
	private final ChatheadEmote chatheadEmote;

	ServantOverride(String label, int[] npcIds, Rig rig, int standAnimation, int walkAnimation,
		int ambient, int contrast, short[] retextureFind, short[] retextureReplace, ChatheadEmote chatheadEmote)
	{
		this.label = label;
		this.npcIds = npcIds;
		this.rig = rig;
		this.standAnimation = standAnimation;
		this.walkAnimation = walkAnimation;
		this.ambient = ambient;
		this.contrast = contrast;
		this.retextureFind = retextureFind;
		this.retextureReplace = retextureReplace;
		this.chatheadEmote = chatheadEmote;
	}

	/**
	 * @return a copy of the NPC ids to borrow the model and chathead from, in priority order
	 */
	public int[] getNpcIds()
	{
		return npcIds.clone();
	}

	/**
	 * @return a copy of the texture ids to replace, paired by index with {@link #getRetextureReplace()}
	 */
	public short[] getRetextureFind()
	{
		return retextureFind.clone();
	}

	/**
	 * @return a copy of the replacement texture ids, paired by index with {@link #getRetextureFind()}
	 */
	public short[] getRetextureReplace()
	{
		return retextureReplace.clone();
	}

	/**
	 * @return false only for {@link #DEFAULT}, which draws the servant unchanged
	 */
	public boolean isOverride()
	{
		return this != DEFAULT;
	}

	@Override
	public String toString()
	{
		return label;
	}

	/**
	 * @return the first id for which {@code has} is true, or -1 when none match or {@code ids} is null or empty
	 */
	public static int firstMatching(int[] ids, IntPredicate has)
	{
		if (ids == null)
		{
			return -1;
		}
		for (int id : ids)
		{
			if (has.test(id))
			{
				return id;
			}
		}
		return -1;
	}
}
