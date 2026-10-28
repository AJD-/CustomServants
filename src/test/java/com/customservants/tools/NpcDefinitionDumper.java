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
package com.customservants.tools;

import com.customservants.Rig;
import com.customservants.ServantIds;
import com.customservants.ServantOverride;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.NpcManager;
import net.runelite.cache.definitions.NpcDefinition;
import net.runelite.cache.definitions.SequenceDefinition;
import net.runelite.cache.definitions.loaders.SequenceLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.ArchiveFiles;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;

/**
 * Dev-only tool that prints NPC definitions from the live OSRS cache.
 * <p>
 * The RuneLite API cannot read an NPC's stand/walk sequences, ambient, contrast or retextures
 * from {@code NPCComposition}, so {@link ServantOverride} hardcodes them. This tool is where those
 * numbers come from: it decodes the NPC definitions with {@code net.runelite:cache}, follows each
 * animation into the sequence and frame archives to name the framemap (or skeleton) it animates,
 * and derives each override's {@link Rig} by comparing its stand framemap with the servants'.
 * <p>
 * Run with {@code ./gradlew dumpNpcDefinitions} for every override and servant NPC, or
 * {@code -Pnpc=1173,thurgo} for ids or name substrings, and optionally {@code -PcacheDir=...}.
 * Test sourceSet only, never shipped, so console output and reading the jagexcache are fine here.
 */
public final class NpcDefinitionDumper
{
	private static final String CACHE_DIR_PROPERTY = "customservants.cacheDir";

	private static final String[] DEFAULT_CACHE_DIRS = {
		".runelite/jagexcache/oldschool/LIVE",
		"jagexcache/oldschool/LIVE"
	};

	/**
	 * Guard against a broad name search printing thousands of definitions.
	 */
	private static final int MAX_NAME_MATCHES = 40;

	/**
	 * Framemap of the player/humanoid skeleton, which every house servant is expected to stand on.
	 */
	private static final int PLAYER_FRAMEMAP = 0;

	private final Store store;
	private final NpcManager npcManager;
	private final SequenceLoader sequenceLoader;
	private final Map<Integer, SequenceInfo> sequences = new HashMap<>();

	private NpcDefinitionDumper(Store store, NpcManager npcManager, int sequenceArchiveRevision)
	{
		this.store = store;
		this.npcManager = npcManager;
		this.sequenceLoader = new SequenceLoader().configureForRevision(sequenceArchiveRevision);
	}

	public static void main(String[] args) throws IOException
	{
		File cacheDir = resolveCacheDir();
		if (cacheDir == null)
		{
			System.err.println("Could not find an OSRS cache. Looked for:");
			for (String candidate : DEFAULT_CACHE_DIRS)
			{
				System.err.println("  " + new File(System.getProperty("user.home"), candidate));
			}
			System.err.println("Pass one with -PcacheDir=<path>");
			System.exit(1);
			return;
		}

		try (Store store = new Store(cacheDir))
		{
			store.load();

			NpcManager npcManager = new NpcManager(store);
			npcManager.load();

			// Same pattern as NpcManager.load: a loader is configured by its own archive's revision
			Archive sequenceArchive = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.SEQUENCE.getId());
			int revision = sequenceArchive == null ? 0 : sequenceArchive.getRevision();

			System.out.println("Cache: " + cacheDir);
			System.out.println("NPC definitions: " + npcManager.getNpcs().size());
			System.out.println("Sequence archive revision: " + revision);
			System.out.println("ambient/contrast are printed exactly as NpcLoader decodes them (signed byte, unscaled)");
			System.out.println();

			NpcDefinitionDumper dumper = new NpcDefinitionDumper(store, npcManager, revision);
			if (args.length == 0)
			{
				dumper.dumpKnown();
			}
			else
			{
				for (String arg : args)
				{
					dumper.dumpQuery(arg.trim());
				}
			}
		}
	}

	/**
	 * The cache directory from the system property, else the first default that exists, else null.
	 */
	private static File resolveCacheDir()
	{
		String configured = System.getProperty(CACHE_DIR_PROPERTY);
		if (configured != null && !configured.isEmpty())
		{
			File dir = new File(configured);
			return dir.isDirectory() ? dir : null;
		}

		File home = new File(System.getProperty("user.home"));
		for (String candidate : DEFAULT_CACHE_DIRS)
		{
			File dir = new File(home, candidate);
			if (dir.isDirectory())
			{
				return dir;
			}
		}
		return null;
	}

	/**
	 * Prints every NPC the plugin knows - each override's ids, then each servant - and the summary.
	 */
	private void dumpKnown()
	{
		System.out.println("===== Overrides =====");
		System.out.println();
		for (ServantOverride override : ServantOverride.values())
		{
			if (!override.isOverride())
			{
				continue;
			}
			System.out.println("--- " + override.name() + " (" + override.getLabel() + ") ---");
			for (int id : override.getNpcIds())
			{
				printId(id);
			}
		}

		System.out.println("===== Servants =====");
		System.out.println();
		for (ServantIds.Servant servant : ServantIds.all())
		{
			System.out.println("--- " + servant.getRole() + " (rig tag " + servant.getRig() + ") ---");
			printId(servant.getNpcId());
		}

		printSummary();
	}

	/**
	 * Prints every definition matching a numeric id or a case-insensitive name substring.
	 */
	private void dumpQuery(String query)
	{
		if (query.isEmpty())
		{
			return;
		}

		if (query.chars().allMatch(Character::isDigit))
		{
			printId(Integer.parseInt(query));
			return;
		}

		String needle = query.toLowerCase(Locale.ROOT);
		List<NpcDefinition> matches = new ArrayList<>();
		for (NpcDefinition def : npcManager.getNpcs())
		{
			if (def.name != null && def.name.toLowerCase(Locale.ROOT).contains(needle))
			{
				matches.add(def);
			}
		}
		matches.sort((a, b) -> Integer.compare(a.id, b.id));

		if (matches.isEmpty())
		{
			System.out.println("No NPC name contains '" + query + "'");
			System.out.println();
			return;
		}

		int shown = Math.min(matches.size(), MAX_NAME_MATCHES);
		for (int i = 0; i < shown; i++)
		{
			print(matches.get(i));
		}
		if (matches.size() > shown)
		{
			System.out.println("... and " + (matches.size() - shown) + " more matches for '" + query + "'");
			System.out.println();
		}
	}

	private void printId(int id)
	{
		NpcDefinition def = npcManager.get(id);
		if (def == null)
		{
			System.out.println("id " + id + ": MISSING FROM CACHE");
			System.out.println();
			return;
		}
		print(def);
	}

	private void print(NpcDefinition def)
	{
		System.out.println(def.name + " (id " + def.id + ")");
		System.out.println("  models                    " + Arrays.toString(def.models));
		System.out.println("  chatheadModels            " + Arrays.toString(def.chatheadModels));
		System.out.println("  widthScale                " + def.widthScale);
		System.out.println("  heightScale               " + def.heightScale);
		System.out.println("  size                      " + def.size);
		System.out.println("  ambient                   " + def.ambient);
		System.out.println("  contrast                  " + def.contrast);
		System.out.println("  recolor                   " + Arrays.toString(def.recolorToFind)
			+ " -> " + Arrays.toString(def.recolorToReplace));
		System.out.println("  retexture                 " + Arrays.toString(def.retextureToFind)
			+ " -> " + Arrays.toString(def.retextureToReplace));
		System.out.println("  configs                   " + Arrays.toString(def.configs));
		System.out.println("  varbitId                  " + def.varbitId);
		System.out.println("  varpIndex                 " + def.varpIndex);
		printSequence("standingAnimation", def.standingAnimation);
		printSequence("walkingAnimation", def.walkingAnimation);
		printSequence("rotate180Animation", def.rotate180Animation);
		printSequence("rotateLeftAnimation", def.rotateLeftAnimation);
		printSequence("rotateRightAnimation", def.rotateRightAnimation);
		printSequence("idleRotateLeftAnimation", def.idleRotateLeftAnimation);
		printSequence("idleRotateRightAnimation", def.idleRotateRightAnimation);
		printSequence("runAnimation", def.runAnimation);
		printSequence("runRotate180Animation", def.runRotate180Animation);
		printSequence("runRotateLeftAnimation", def.runRotateLeftAnimation);
		printSequence("runRotateRightAnimation", def.runRotateRightAnimation);
		printSequence("crawlAnimation", def.crawlAnimation);
		System.out.println();
	}

	private void printSequence(String field, int sequenceId)
	{
		System.out.printf("  %-25s %s%n", field, sequence(sequenceId).describe());
	}

	private void printSummary()
	{
		System.out.println("===== Summary =====");
		System.out.println();

		// Reference framemaps come from the servants' own rig tags, so no NPC ids live in this tool
		Map<String, Set<Integer>> framemapsByRole = new LinkedHashMap<>();
		Set<Integer> humanFramemaps = new TreeSet<>();
		System.out.println("Servants (role, id, rig tag, stand):");
		for (ServantIds.Servant servant : ServantIds.all())
		{
			NpcDefinition def = npcManager.get(servant.getNpcId());
			SequenceInfo stand = def == null ? SequenceInfo.none() : sequence(def.standingAnimation);
			System.out.printf("  %-13s %5d  %-5s  %s%n", servant.getRole(), servant.getNpcId(),
				servant.getRig(), def == null ? "MISSING FROM CACHE" : stand.describe());

			Set<Integer> roleFramemaps = framemapsByRole.computeIfAbsent(servant.getRole(), k -> new TreeSet<>());
			Integer framemap = stand.framemapOrNull();
			if (framemap == null)
			{
				System.out.println("    FLAG: " + servant.getRole() + " id " + servant.getNpcId()
					+ " has no cleanly decoded frame-based stand sequence");
				continue;
			}

			roleFramemaps.add(framemap);
			if (servant.getRig() == Rig.HUMAN)
			{
				humanFramemaps.add(framemap);
			}
		}
		System.out.println();
		System.out.println("Stand framemaps by role: " + framemapsByRole);
		System.out.println("Rig HUMAN servant framemaps: " + humanFramemaps);

		Integer humanFramemap = humanFramemaps.size() == 1 ? humanFramemaps.iterator().next() : null;
		if (humanFramemap == null)
		{
			System.out.println("FLAG: the HUMAN-tagged servants do not share exactly one stand framemap; "
				+ "override rigs cannot be derived and print as UNKNOWN");
		}
		else if (humanFramemap != PLAYER_FRAMEMAP)
		{
			System.out.println("FLAG: the HUMAN-tagged servants stand on framemap " + humanFramemap
				+ ", not the player skeleton's framemap " + PLAYER_FRAMEMAP);
		}
		for (ServantIds.Servant servant : ServantIds.all())
		{
			NpcDefinition def = npcManager.get(servant.getNpcId());
			Integer framemap = def == null ? null : sequence(def.standingAnimation).framemapOrNull();
			Rig derived = deriveRig(framemap, humanFramemap);
			if (derived != null && derived != servant.getRig())
			{
				System.out.println("FLAG: " + servant.getRole() + " id " + servant.getNpcId() + " is tagged "
					+ servant.getRig() + " but its stand framemap groups it as " + derived);
			}
		}
		System.out.println();

		System.out.println("Overrides:");
		for (ServantOverride override : ServantOverride.values())
		{
			if (override.isOverride())
			{
				printOverrideSummary(override, humanFramemap);
			}
		}
	}

	private void printOverrideSummary(ServantOverride override, Integer humanFramemap)
	{
		int[] ids = override.getNpcIds();
		int bodyId = ServantOverride.firstMatching(ids, id -> hasModels(npcManager.get(id), true));
		int chatheadId = ServantOverride.firstMatching(ids, id -> hasModels(npcManager.get(id), false));

		System.out.println("  " + override.name());
		System.out.println("    ids         " + Arrays.toString(ids));
		System.out.println("    bodyId      " + (bodyId == -1 ? "NONE" : String.valueOf(bodyId)));
		System.out.println("    chatheadId  " + (chatheadId == -1 ? "NONE" : String.valueOf(chatheadId)));
		if (bodyId == -1)
		{
			System.out.println("    FLAG: no id has body models");
			System.out.println();
			return;
		}

		NpcDefinition body = npcManager.get(bodyId);
		SequenceInfo stand = sequence(body.standingAnimation);
		SequenceInfo walk = sequence(body.walkingAnimation);
		String rig;
		if (stand.skeletal)
		{
			rig = "OTHER (skeletal stand, skeleton " + stand.skeleton + "; not framemap-derivable)";
		}
		else if (humanFramemap == null)
		{
			rig = "UNKNOWN (no single HUMAN servant framemap)";
		}
		else
		{
			Rig derived = deriveRig(stand.framemapOrNull(), humanFramemap);
			rig = derived == null ? "UNKNOWN (stand has no cleanly decoded framemap)" : derived.name();
		}

		System.out.println("    stand       " + stand.describe() + "  [from id " + bodyId + "]");
		System.out.println("    walk        " + walk.describe() + "  [from id " + bodyId + "]");
		System.out.println("    rig         " + rig);
		System.out.println("    ambient     " + body.ambient);
		System.out.println("    contrast    " + body.contrast);
		System.out.println("    retexture   " + Arrays.toString(body.retextureToFind)
			+ " -> " + Arrays.toString(body.retextureToReplace));

		// Other ids in the list whose animation or lighting differs from the body id's: provenance for the sheet
		for (int id : ids)
		{
			NpcDefinition other = npcManager.get(id);
			if (id == bodyId || other == null)
			{
				continue;
			}
			if (other.standingAnimation != body.standingAnimation || other.walkingAnimation != body.walkingAnimation
				|| other.ambient != body.ambient || other.contrast != body.contrast
				|| !Arrays.equals(other.retextureToFind, body.retextureToFind)
				|| !Arrays.equals(other.retextureToReplace, body.retextureToReplace))
			{
				System.out.println("    note: id " + id + " differs: stand " + other.standingAnimation
					+ ", walk " + other.walkingAnimation + ", ambient " + other.ambient
					+ ", contrast " + other.contrast + ", retexture " + Arrays.toString(other.retextureToFind)
					+ " -> " + Arrays.toString(other.retextureToReplace));
			}
		}
		if (stand.id == -1 || walk.id == -1)
		{
			System.out.println("    FLAG: body id has no stand or walk sequence");
		}
		else if (stand.skeletal != walk.skeletal
			|| (stand.skeletal ? stand.skeleton != walk.skeleton : stand.framemap != walk.framemap))
		{
			System.out.println("    FLAG: stand and walk animate different framemaps/skeletons");
		}
		System.out.println();
	}

	/**
	 * @return HUMAN when the framemap is the servants' shared stand framemap, OTHER when it is not,
	 * null when there is no framemap to compare
	 */
	private static Rig deriveRig(Integer framemap, Integer humanFramemap)
	{
		if (framemap == null)
		{
			return null;
		}
		return framemap.equals(humanFramemap) ? Rig.HUMAN : Rig.OTHER;
	}

	private static boolean hasModels(NpcDefinition def, boolean body)
	{
		if (def == null)
		{
			return false;
		}
		int[] models = body ? def.models : def.chatheadModels;
		return models != null && models.length > 0;
	}

	private SequenceInfo sequence(int sequenceId)
	{
		if (sequenceId == -1)
		{
			return SequenceInfo.none();
		}
		return sequences.computeIfAbsent(sequenceId, this::readSequence);
	}

	private SequenceInfo readSequence(int sequenceId)
	{
		SequenceInfo info = new SequenceInfo(sequenceId);
		try
		{
			SequenceDefinition def = loadSequence(sequenceId);
			if (def == null)
			{
				info.error = "sequence not in cache";
				return info;
			}

			if (def.animMayaID != -1)
			{
				info.skeletal = true;
				info.mayaStart = def.animMayaStart;
				info.mayaEnd = def.animMayaEnd;
				byte[] data = loadFile(IndexType.ANIMAYAS, def.animMayaID >> 16, def.animMayaID & 0xFFFF);
				if (data == null || data.length < 3)
				{
					info.error = "skeletal animation " + def.animMayaID + " unreadable";
				}
				else
				{
					info.skeleton = (data[1] & 0xFF) << 8 | data[2] & 0xFF;
				}
				return info;
			}

			if (def.frameIDs == null || def.frameIDs.length == 0)
			{
				info.error = "no frames";
				return info;
			}

			info.frames = def.frameIDs.length;
			// The framemap is named by each frame's first two bytes; read every frame so a mixed sequence is caught
			Set<Integer> framemaps = new LinkedHashSet<>();
			for (int packed : def.frameIDs)
			{
				byte[] data = loadFile(IndexType.ANIMATIONS, packed >> 16, packed & 0xFFFF);
				if (data == null || data.length < 2)
				{
					info.error = "frame " + packed + " unreadable";
					continue;
				}
				framemaps.add((data[0] & 0xFF) << 8 | data[1] & 0xFF);
			}
			if (!framemaps.isEmpty())
			{
				info.framemap = framemaps.iterator().next();
			}
			if (framemaps.size() > 1)
			{
				info.error = "frames name several framemaps " + framemaps;
			}
		}
		catch (IOException | RuntimeException e)
		{
			info.error = "decode failed: " + e;
		}
		return info;
	}

	private SequenceDefinition loadSequence(int sequenceId) throws IOException
	{
		byte[] data = loadFile(IndexType.CONFIGS, ConfigType.SEQUENCE.getId(), sequenceId);
		return data == null ? null : sequenceLoader.load(sequenceId, data);
	}

	private byte[] loadFile(IndexType indexType, int archiveId, int fileId) throws IOException
	{
		Index index = store.getIndex(indexType);
		if (index == null)
		{
			return null;
		}

		Archive archive = index.getArchive(archiveId);
		if (archive == null)
		{
			return null;
		}

		byte[] container = store.getStorage().loadArchive(archive);
		if (container == null)
		{
			return null;
		}

		if (archive.getFileData() != null && archive.getFileData().length == 1)
		{
			return archive.decompress(container);
		}

		ArchiveFiles files = archive.getFiles(container);
		FSFile file = files.findFile(fileId);
		return file == null ? null : file.getContents();
	}

	/**
	 * What a sequence animates: a framemap and frame count, or a skeleton.
	 */
	private static final class SequenceInfo
	{
		private final int id;
		private int frames;
		private int framemap = -1;
		private boolean skeletal;
		private int skeleton = -1;
		private int mayaStart;
		private int mayaEnd;
		private String error;

		private SequenceInfo(int id)
		{
			this.id = id;
		}

		private static SequenceInfo none()
		{
			return new SequenceInfo(-1);
		}

		/**
		 * @return the framemap of a frame-based sequence that decoded cleanly, else null
		 */
		private Integer framemapOrNull()
		{
			return id == -1 || skeletal || error != null || framemap == -1 ? null : framemap;
		}

		private String describe()
		{
			if (id == -1)
			{
				return "-1";
			}
			String body = skeletal
				? "skeletal, skeleton " + skeleton + ", maya frames " + mayaStart + ".." + mayaEnd
				: frames + " frames, framemap " + framemap;
			return id + " (" + body + (error == null ? "" : "; ERROR " + error) + ")";
		}
	}
}
