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

import com.customservants.ServantDrawCallbacks;
import java.util.function.Supplier;
import net.runelite.api.hooks.DrawCallbacks;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

/**
 * Walking the stack of model-substituting decorators in the draw callbacks slot.
 *
 * <p>Custom NPC Models' and Retro NPC Swapper's decorators are only ever named here, never stubbed:
 * a class declared under their real names would sit on the {@code run} task's classpath, and a Hub
 * plugin's parent-first classloader could resolve the partner's own decorator to it. So the partners
 * are covered by name through {@link RendererChain#canUnwrap}, and chains are built from this
 * plugin's real decorator.
 */
public class RendererChainTest
{
	private static final String CUSTOM_NPC_MODELS = "com.customnpcmodels.CustomDrawCallbacks";

	private static final String RETRO = "com.retronpcswapper.RetroDrawCallbacks";

	private final DrawCallbacks renderer = mock(DrawCallbacks.class);

	private static ServantDrawCallbacks decorate(DrawCallbacks delegate)
	{
		return new ServantDrawCallbacks(delegate, npc -> null);
	}

	private static DrawCallbacks supplier()
	{
		return mock(DrawCallbacks.class, withSettings().extraInterfaces(Supplier.class));
	}

	@Test
	public void testCustomNpcModelsIsSeenThroughWhenItSuppliesItsDelegate()
	{
		assertTrue(RendererChain.canUnwrap(CUSTOM_NPC_MODELS, supplier()));
	}

	@Test
	public void testRetroIsSeenThroughWhenItSuppliesItsDelegate()
	{
		assertTrue(RendererChain.canUnwrap(RETRO, supplier()));
	}

	@Test
	public void testAPartnerTooOldToSupplyItsDelegateIsNotSeenThrough()
	{
		// Partner builds from before the Supplier handshake: stand each other down, exactly as before
		assertFalse(RendererChain.canUnwrap(RETRO, mock(DrawCallbacks.class)));
		assertFalse(RendererChain.canUnwrap(CUSTOM_NPC_MODELS, mock(DrawCallbacks.class)));
	}

	@Test
	public void testAnUnknownSupplierIsNotSeenThrough()
	{
		assertFalse(RendererChain.canUnwrap("com.example.Decorator", supplier()));
	}

	@Test
	public void testThisPluginsDecoratorIsKnown()
	{
		assertTrue(RendererChain.canUnwrap(ServantDrawCallbacks.class.getName(), decorate(renderer)));
	}

	@Test
	public void testBaseIsTheRendererBeneathEveryDecorator()
	{
		assertSame(renderer, RendererChain.base(renderer));
		assertSame(renderer, RendererChain.base(decorate(renderer)));
		assertSame(renderer, RendererChain.base(decorate(decorate(renderer))));
		assertNull(RendererChain.base(null));
	}

	@Test
	public void testAnOverlongChainStopsWalking()
	{
		DrawCallbacks top = renderer;
		for (int i = 0; i < 50; i++)
		{
			top = decorate(top);
		}

		// Gives up at the depth cap rather than walking on, so this lands on a decorator
		assertTrue(RendererChain.base(top) instanceof ServantDrawCallbacks);
	}

	@Test
	public void testContainsFindsADecoratorAtAnyDepth()
	{
		ServantDrawCallbacks inner = decorate(renderer);
		ServantDrawCallbacks outer = decorate(inner);

		assertTrue(RendererChain.contains(outer, outer));
		assertTrue(RendererChain.contains(outer, inner));
		assertTrue(RendererChain.contains(outer, renderer));
		assertFalse(RendererChain.contains(inner, outer));
		assertFalse(RendererChain.contains(outer, decorate(renderer)));
		assertFalse(RendererChain.contains(outer, null));
		assertFalse(RendererChain.contains(null, inner));
	}

	@Test
	public void testFindReturnsTheTopmostDecoratorOfAType()
	{
		ServantDrawCallbacks inner = decorate(renderer);
		ServantDrawCallbacks outer = decorate(inner);

		assertSame(outer, RendererChain.find(outer, ServantDrawCallbacks.class));
		assertSame(inner, RendererChain.find(inner, ServantDrawCallbacks.class));
		assertNull(RendererChain.find(renderer, ServantDrawCallbacks.class));
	}
}
