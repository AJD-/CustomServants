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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.client.events.PluginMessage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

/**
 * The claims half of the protocol shared with Retro NPC Swapper, ported from its own test so the
 * copies stay string-compatible.
 */
public class ModelSwapProtocolTest
{
	private static final String SERVANTS = ModelSwapProtocol.SOURCE_CUSTOM_SERVANTS;
	private static final String RETRO = ModelSwapProtocol.SOURCE_RETRO_NPC_SWAPPER;

	/** A message of this protocol built by hand, as a partner's copy would send it. */
	private static PluginMessage message(String name, Object version, String source)
	{
		Map<String, Object> data = new HashMap<>();
		data.put(ModelSwapProtocol.KEY_VERSION, version);
		if (source != null)
		{
			data.put(ModelSwapProtocol.KEY_SOURCE, source);
		}
		return new PluginMessage(ModelSwapProtocol.NAMESPACE, name, data);
	}

	private static Map<String, Object> claimsData()
	{
		return new HashMap<>(ModelSwapProtocol.claimsMessage(Collections.singleton(5779)).getData());
	}

	@Test
	public void testTheWireStringsMatchThePartners()
	{
		assertEquals("npc-model-swap", ModelSwapProtocol.NAMESPACE);
		assertEquals(1, ModelSwapProtocol.VERSION);
		assertEquals("claims", ModelSwapProtocol.CLAIMS);
		assertEquals("modelSyncReq", ModelSwapProtocol.SYN);
		assertEquals("custom-npc-models", ModelSwapProtocol.SOURCE_CUSTOM_NPC_MODELS);
		assertEquals("retro-npc-swapper", ModelSwapProtocol.SOURCE_RETRO_NPC_SWAPPER);
		assertEquals("custom-servants", ModelSwapProtocol.SOURCE_CUSTOM_SERVANTS);
		assertEquals("version", ModelSwapProtocol.KEY_VERSION);
		assertEquals("source", ModelSwapProtocol.KEY_SOURCE);
		assertEquals("npcIds", ModelSwapProtocol.KEY_NPC_IDS);
	}

	@Test
	public void testClaimsRoundTrip()
	{
		Set<Integer> ids = new HashSet<>(Arrays.asList(5779, 5780));
		PluginMessage message = ModelSwapProtocol.claimsMessage(ids);
		assertEquals(ModelSwapProtocol.NAMESPACE, message.getNamespace());
		assertEquals(ModelSwapProtocol.CLAIMS, message.getName());
		assertEquals(SERVANTS, message.getData().get(ModelSwapProtocol.KEY_SOURCE));
		assertEquals(ids, ModelSwapProtocol.readClaims(message, SERVANTS));

		assertEquals(Collections.emptySet(),
			ModelSwapProtocol.readClaims(ModelSwapProtocol.claimsMessage(Collections.emptySet()), SERVANTS));
	}

	@Test
	public void testClaimsSentAreACopyNobodyCanModify()
	{
		Set<Integer> ids = new HashSet<>(Collections.singleton(5779));
		PluginMessage message = ModelSwapProtocol.claimsMessage(ids);
		ids.add(1);

		Set<?> sent = (Set<?>) message.getData().get(ModelSwapProtocol.KEY_NPC_IDS);
		assertEquals(Collections.singleton(5779), sent);
		try
		{
			sent.clear();
			fail("the claimed set was modifiable");
		}
		catch (UnsupportedOperationException expected)
		{
			// Expected
		}
	}

	@Test
	public void testClaimsReadAreACopyNobodyCanModify()
	{
		Set<Integer> ids = new HashSet<>(Arrays.asList(5779, 5780));
		Map<String, Object> data = claimsData();
		data.put(ModelSwapProtocol.KEY_NPC_IDS, ids);
		PluginMessage message = new PluginMessage(ModelSwapProtocol.NAMESPACE, ModelSwapProtocol.CLAIMS, data);

		Set<Integer> read = ModelSwapProtocol.readClaims(message, SERVANTS);
		ids.add(1);
		assertEquals(new HashSet<>(Arrays.asList(5779, 5780)), read);
		try
		{
			read.clear();
			fail("the read set was modifiable");
		}
		catch (UnsupportedOperationException expected)
		{
			// Expected
		}
	}

	@Test
	public void testOnlyIntegerIdsAreRead()
	{
		Map<String, Object> data = claimsData();
		data.put(ModelSwapProtocol.KEY_NPC_IDS, Arrays.asList(5779, "5780", null, 5781L));
		PluginMessage message = new PluginMessage(ModelSwapProtocol.NAMESPACE, ModelSwapProtocol.CLAIMS, data);
		assertEquals(Collections.singleton(5779), ModelSwapProtocol.readClaims(message, SERVANTS));

		data.put(ModelSwapProtocol.KEY_NPC_IDS, 5779);
		assertNull(ModelSwapProtocol.readClaims(message, SERVANTS));
	}

	@Test
	public void testOtherMessagesAreNotClaims()
	{
		assertNull(ModelSwapProtocol.readClaims(null, SERVANTS));
		assertNull(ModelSwapProtocol.readClaims(new PluginMessage("some-plugin", ModelSwapProtocol.CLAIMS), SERVANTS));
		assertNull(ModelSwapProtocol.readClaims(new PluginMessage(ModelSwapProtocol.NAMESPACE, ModelSwapProtocol.CLAIMS), SERVANTS));
		assertNull(ModelSwapProtocol.readClaims(message(ModelSwapProtocol.SYN, ModelSwapProtocol.VERSION, SERVANTS), SERVANTS));
		assertNull(ModelSwapProtocol.readClaims(new PluginMessage("some-plugin", ModelSwapProtocol.CLAIMS, claimsData()), SERVANTS));
	}

	@Test
	public void testAnotherNameIsNotClaims()
	{
		PluginMessage message = new PluginMessage(ModelSwapProtocol.NAMESPACE, "outlines", claimsData());
		assertNull(ModelSwapProtocol.readClaims(message, SERVANTS));
	}

	@Test
	public void testAnotherVersionIsIgnored()
	{
		Map<String, Object> data = claimsData();
		data.put(ModelSwapProtocol.KEY_VERSION, ModelSwapProtocol.VERSION + 1);
		assertNull(ModelSwapProtocol.readClaims(new PluginMessage(ModelSwapProtocol.NAMESPACE, ModelSwapProtocol.CLAIMS, data), SERVANTS));

		data.put(ModelSwapProtocol.KEY_VERSION, String.valueOf(ModelSwapProtocol.VERSION));
		assertNull(ModelSwapProtocol.readClaims(new PluginMessage(ModelSwapProtocol.NAMESPACE, ModelSwapProtocol.CLAIMS, data), SERVANTS));
	}

	@Test
	public void testClaimsFromAnotherSourceAreIgnored()
	{
		PluginMessage ours = ModelSwapProtocol.claimsMessage(Collections.singleton(5779));
		assertNull(ModelSwapProtocol.readClaims(ours, ModelSwapProtocol.SOURCE_CUSTOM_NPC_MODELS));
		assertNull(ModelSwapProtocol.readClaims(ours, RETRO));

		Map<String, Object> data = claimsData();
		data.put(ModelSwapProtocol.KEY_SOURCE, RETRO);
		assertNull(ModelSwapProtocol.readClaims(new PluginMessage(ModelSwapProtocol.NAMESPACE, ModelSwapProtocol.CLAIMS, data), SERVANTS));
	}

	@Test
	public void testClaimsWithoutASourceAreIgnored()
	{
		Map<String, Object> data = claimsData();
		data.remove(ModelSwapProtocol.KEY_SOURCE);
		PluginMessage sourceless = new PluginMessage(ModelSwapProtocol.NAMESPACE, ModelSwapProtocol.CLAIMS, data);
		assertNull(ModelSwapProtocol.readClaims(sourceless, SERVANTS));
		assertNull(ModelSwapProtocol.readClaims(sourceless, null));
	}

	@Test
	public void testSyncReqIsRecognisedFromRetroOnly()
	{
		PluginMessage syn = message(ModelSwapProtocol.SYN, ModelSwapProtocol.VERSION, RETRO);
		assertTrue(ModelSwapProtocol.isSyncReq(syn, RETRO));
		assertFalse(ModelSwapProtocol.isSyncReq(syn, ModelSwapProtocol.SOURCE_CUSTOM_NPC_MODELS));
		assertFalse(ModelSwapProtocol.isSyncReq(syn, null));

		// Another plugin's request, or claims, are not Retro NPC Swapper asking
		assertFalse(ModelSwapProtocol.isSyncReq(message(ModelSwapProtocol.SYN, ModelSwapProtocol.VERSION,
			ModelSwapProtocol.SOURCE_CUSTOM_NPC_MODELS), RETRO));
		assertFalse(ModelSwapProtocol.isSyncReq(ModelSwapProtocol.claimsMessage(Collections.emptySet()), RETRO));
		assertFalse(ModelSwapProtocol.isSyncReq(message(ModelSwapProtocol.CLAIMS, ModelSwapProtocol.VERSION, RETRO), RETRO));

		// Malformed or foreign
		assertFalse(ModelSwapProtocol.isSyncReq(null, RETRO));
		assertFalse(ModelSwapProtocol.isSyncReq(new PluginMessage(ModelSwapProtocol.NAMESPACE, ModelSwapProtocol.SYN), RETRO));
		assertFalse(ModelSwapProtocol.isSyncReq(message(ModelSwapProtocol.SYN, ModelSwapProtocol.VERSION, null), RETRO));
		assertFalse(ModelSwapProtocol.isSyncReq(message(ModelSwapProtocol.SYN, ModelSwapProtocol.VERSION + 1, RETRO), RETRO));
		assertFalse(ModelSwapProtocol.isSyncReq(new PluginMessage("some-plugin", ModelSwapProtocol.SYN, new HashMap<>(syn.getData())), RETRO));
	}

	@Test
	public void testThePayloadHoldsOnlyJdkTypes()
	{
		Map<String, Object> data = ModelSwapProtocol.claimsMessage(new HashSet<>(Arrays.asList(5779, 5780))).getData();
		assertEquals(3, data.size());
		assertEquals(Integer.class, data.get(ModelSwapProtocol.KEY_VERSION).getClass());
		assertEquals(String.class, data.get(ModelSwapProtocol.KEY_SOURCE).getClass());

		Object ids = data.get(ModelSwapProtocol.KEY_NPC_IDS);
		assertTrue(ids instanceof Set);
		for (Object id : (Collection<?>) ids)
		{
			assertEquals(Integer.class, id.getClass());
		}
		// A JDK collection, loaded by the bootstrap classloader every plugin's classloader sees
		assertNull(ids.getClass().getClassLoader());
	}
}
