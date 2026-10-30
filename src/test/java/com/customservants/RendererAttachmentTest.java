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

import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.hooks.DrawCallbacks;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.gpu.GpuPlugin;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Taking and handing back the draw callbacks slot, and reporting only real state changes.
 */
public class RendererAttachmentTest
{
	private final Client client = mock(Client.class);
	private final PluginManager pluginManager = mock(PluginManager.class);
	private final GpuPlugin gpu = mock(GpuPlugin.class);
	private final ModelSubstitutor substitutor = npc -> null;

	private final RendererAttachment attachment = new RendererAttachment(client, pluginManager);

	// What the client's draw callbacks slot currently holds
	private DrawCallbacks slot;

	@Before
	public void setUp()
	{
		when(pluginManager.getPlugins()).thenReturn(List.of(gpu));
		when(client.getDrawCallbacks()).thenAnswer(invocation -> slot);
		doAnswer(invocation ->
		{
			slot = invocation.getArgument(0);
			return null;
		}).when(client).setDrawCallbacks(any());
		attachment.setSubstitutor(substitutor);
	}

	@Test(expected = IllegalStateException.class)
	public void testAttachWithoutASubstitutorThrows()
	{
		new RendererAttachment(client, pluginManager).attach();
	}

	@Test
	public void testAttachWrapsTheGpuPlugin()
	{
		slot = gpu;

		assertTrue(attachment.attach());

		assertTrue(slot instanceof ServantDrawCallbacks);
		assertSame(gpu, ((ServantDrawCallbacks) slot).get());
		assertTrue(attachment.isAttached());
		assertFalse(attachment.isLost());
	}

	@Test
	public void testAttachingAgainIsNotAChange()
	{
		slot = gpu;
		attachment.attach();

		assertFalse(attachment.attach());
		verify(client, times(1)).setDrawCallbacks(any());
	}

	@Test
	public void testAnUnsupportedRendererIsDeclined()
	{
		slot = mock(DrawCallbacks.class);

		assertFalse(attachment.attach());
		assertFalse(attachment.attach());

		verify(client, never()).setDrawCallbacks(any());
		assertFalse(attachment.isAttached());
		assertTrue(attachment.isLost());
	}

	@Test
	public void testAnEmptySlotIsDeclined()
	{
		slot = null;

		assertFalse(attachment.attach());
		verify(client, never()).setDrawCallbacks(any());
	}

	@Test
	public void testABuriedDecoratorIsReadoptedRatherThanStacked()
	{
		ServantDrawCallbacks leftover = new ServantDrawCallbacks(gpu, substitutor);
		slot = leftover;

		assertTrue(attachment.attach());

		verify(client, never()).setDrawCallbacks(any());
		assertSame(leftover, slot);
		assertTrue(attachment.isAttached());
		assertFalse(attachment.isLost());
	}

	@Test
	public void testLosingTheSlotIsNoticedAndReportedAsAChange()
	{
		slot = gpu;
		attachment.attach();

		// Another renderer took the slot without wrapping us
		slot = mock(DrawCallbacks.class);
		assertTrue(attachment.isLost());

		assertTrue(attachment.attach());
		assertFalse(attachment.isAttached());
	}

	@Test
	public void testDetachRestoresTheRenderer()
	{
		slot = gpu;
		attachment.attach();

		attachment.detach();

		assertSame(gpu, slot);
		assertFalse(attachment.isAttached());
		verify(client, never()).setDrawCallbacks(isNull());
	}

	@Test
	public void testDetachLeavesASlotThatIsNoLongerOursAlone()
	{
		slot = gpu;
		attachment.attach();
		DrawCallbacks ours = slot;

		// Another decorator wrapped ours; we cannot unlink from beneath it
		DrawCallbacks outer = new ServantDrawCallbacks(ours, substitutor);
		slot = outer;

		attachment.detach();

		assertSame(outer, slot);
		assertFalse(attachment.isAttached());
		verify(client, times(1)).setDrawCallbacks(any());
	}

	@Test
	public void testDetachWhenNeverAttachedDoesNothing()
	{
		slot = gpu;

		attachment.detach();

		assertSame(gpu, slot);
		verify(client, never()).setDrawCallbacks(any());
	}

	@Test
	public void testReattachAfterDetachWrapsAfresh()
	{
		slot = gpu;
		attachment.attach();
		DrawCallbacks first = slot;
		attachment.detach();

		assertTrue(attachment.attach());
		assertNotSame(first, slot);
		assertSame(gpu, ((ServantDrawCallbacks) slot).get());
	}

	@Test
	public void testTheGpuPluginIsARendererPlugin()
	{
		assertTrue(RendererAttachment.isRendererPlugin(gpu));
		assertFalse(RendererAttachment.isRendererPlugin(mock(Plugin.class)));
		assertFalse(RendererAttachment.isRendererPlugin(null));
	}
}
