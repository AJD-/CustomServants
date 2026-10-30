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

import java.util.Set;
import net.runelite.api.GameObject;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.Projection;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.SceneTileModel;
import net.runelite.api.SceneTilePaint;
import net.runelite.api.Texture;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.hooks.DrawCallbacks;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * The decorator swaps only the NPC model handed to {@code drawTemp} and forwards every other
 * {@link DrawCallbacks} method untouched. Each forward is checked with distinct argument values so a
 * transposed parameter is caught, not just a missing call.
 */
public class ServantDrawCallbacksTest
{
	private final DrawCallbacks delegate = mock(DrawCallbacks.class);
	private final ModelSubstitutor substitutor = mock(ModelSubstitutor.class);
	private final ServantDrawCallbacks callbacks = new ServantDrawCallbacks(delegate, substitutor);

	private final Projection projection = mock(Projection.class);
	private final Scene scene = mock(Scene.class);
	private final Model model = mock(Model.class);
	private final Renderable renderable = mock(Renderable.class);
	private final TileObject tileObject = mock(TileObject.class);
	private final WorldView worldView = mock(WorldView.class);

	// --- drawTemp -----------------------------------------------------------------------------

	private GameObject gameObjectOf(Renderable r)
	{
		GameObject gameObject = mock(GameObject.class);
		when(gameObject.getRenderable()).thenReturn(r);
		return gameObject;
	}

	@Test
	public void testDrawTempHandsTheSubstituteForAnNpc()
	{
		NPC npc = mock(NPC.class);
		Model substitute = mock(Model.class);
		when(substitutor.substitute(npc)).thenReturn(substitute);
		GameObject gameObject = gameObjectOf(npc);

		callbacks.drawTemp(projection, scene, gameObject, model, 11, 12, 13, 14);

		verify(substitutor).substitute(npc);
		verify(delegate).drawTemp(projection, scene, gameObject, substitute, 11, 12, 13, 14);
		verifyNoMoreInteractions(delegate);
	}

	@Test
	public void testDrawTempKeepsTheVanillaModelWhenThereIsNoSubstitute()
	{
		NPC npc = mock(NPC.class);
		when(substitutor.substitute(npc)).thenReturn(null);
		GameObject gameObject = gameObjectOf(npc);

		callbacks.drawTemp(projection, scene, gameObject, model, 21, 22, 23, 24);

		verify(substitutor).substitute(npc);
		verify(delegate).drawTemp(projection, scene, gameObject, model, 21, 22, 23, 24);
	}

	@Test
	public void testDrawTempSurvivesAThrowingSubstitutor()
	{
		NPC npc = mock(NPC.class);
		when(substitutor.substitute(npc)).thenThrow(new IllegalStateException("boom"));
		GameObject gameObject = gameObjectOf(npc);

		// Twice: the first failure is warned about, the second only logged at debug; neither throws
		callbacks.drawTemp(projection, scene, gameObject, model, 31, 32, 33, 34);
		callbacks.drawTemp(projection, scene, gameObject, model, 31, 32, 33, 34);

		verify(delegate, times(2)).drawTemp(projection, scene, gameObject, model, 31, 32, 33, 34);
	}

	@Test
	public void testDrawTempNeverAsksAboutANonNpc()
	{
		GameObject gameObject = gameObjectOf(renderable);

		callbacks.drawTemp(projection, scene, gameObject, model, 41, 42, 43, 44);

		verifyNoInteractions(substitutor);
		verify(delegate).drawTemp(projection, scene, gameObject, model, 41, 42, 43, 44);
	}

	@Test
	public void testDrawTempWithNoRenderableNeverAsks()
	{
		GameObject gameObject = gameObjectOf(null);

		callbacks.drawTemp(projection, scene, gameObject, model, 51, 52, 53, 54);

		verifyNoInteractions(substitutor);
		verify(delegate).drawTemp(projection, scene, gameObject, model, 51, 52, 53, 54);
	}

	@Test
	public void testGetSuppliesTheDelegate()
	{
		assertSame(delegate, callbacks.get());
	}

	// --- Verbatim forwards --------------------------------------------------------------------

	@Test
	public void testDrawRenderableForwards()
	{
		callbacks.draw(projection, scene, renderable, 1, 2, 3, 4, 5L);
		verify(delegate).draw(projection, scene, renderable, 1, 2, 3, 4, 5L);
		verifyNoInteractions(substitutor);
	}

	@Test
	public void testDrawScenePaintForwards()
	{
		SceneTilePaint paint = mock(SceneTilePaint.class);
		callbacks.drawScenePaint(scene, paint, 1, 2, 3);
		verify(delegate).drawScenePaint(scene, paint, 1, 2, 3);
	}

	@Test
	public void testDrawSceneTileModelForwards()
	{
		SceneTileModel tileModel = mock(SceneTileModel.class);
		callbacks.drawSceneTileModel(scene, tileModel, 4, 5);
		verify(delegate).drawSceneTileModel(scene, tileModel, 4, 5);
	}

	@Test
	public void testDrawOverlayColorForwards()
	{
		callbacks.draw(0x123456);
		verify(delegate).draw(0x123456);
	}

	@Test
	public void testDrawSceneForwards()
	{
		callbacks.drawScene(1.5, 2.5, 3.5, 4.5, 5.5, 6);
		verify(delegate).drawScene(1.5, 2.5, 3.5, 4.5, 5.5, 6);
	}

	@Test
	public void testPostDrawSceneForwards()
	{
		callbacks.postDrawScene();
		verify(delegate).postDrawScene();
	}

	@Test
	public void testAnimateForwards()
	{
		Texture texture = mock(Texture.class);
		callbacks.animate(texture, 7);
		verify(delegate).animate(texture, 7);
	}

	@Test
	public void testLoadSceneForwards()
	{
		callbacks.loadScene(scene);
		verify(delegate).loadScene(scene);
	}

	@Test
	public void testSwapSceneForwards()
	{
		callbacks.swapScene(scene);
		verify(delegate).swapScene(scene);
	}

	@Test
	public void testTileInFrustumForwardsAndReturns()
	{
		when(delegate.tileInFrustum(scene, 0.1f, 0.2f, 0.3f, 0.4f, 5, 6, 7, 8, 9, 10)).thenReturn(true);

		assertTrue(callbacks.tileInFrustum(scene, 0.1f, 0.2f, 0.3f, 0.4f, 5, 6, 7, 8, 9, 10));
		assertFalse(callbacks.tileInFrustum(scene, 0.1f, 0.2f, 0.3f, 0.4f, 5, 6, 7, 8, 10, 9));
		verify(delegate).tileInFrustum(scene, 0.1f, 0.2f, 0.3f, 0.4f, 5, 6, 7, 8, 9, 10);
	}

	@Test
	public void testZoneInFrustumForwardsAndReturns()
	{
		when(delegate.zoneInFrustum(1, 2, 3, 4)).thenReturn(true);

		assertTrue(callbacks.zoneInFrustum(1, 2, 3, 4));
		assertFalse(callbacks.zoneInFrustum(2, 1, 3, 4));
		verify(delegate).zoneInFrustum(1, 2, 3, 4);
	}

	@Test
	public void testLoadSceneWithWorldViewForwards()
	{
		callbacks.loadScene(worldView, scene);
		verify(delegate).loadScene(worldView, scene);
	}

	@Test
	public void testDespawnWorldViewForwards()
	{
		callbacks.despawnWorldView(worldView);
		verify(delegate).despawnWorldView(worldView);
	}

	@Test
	public void testPreSceneDrawForwards()
	{
		Set<Integer> hideRoofIds = Set.of(42);
		callbacks.preSceneDraw(scene, projection, 1f, 2f, 3f, 4f, 5f, 6, 7, 8, hideRoofIds);
		verify(delegate).preSceneDraw(scene, projection, 1f, 2f, 3f, 4f, 5f, 6, 7, 8, hideRoofIds);
	}

	@Test
	@SuppressWarnings("deprecation")
	public void testDeprecatedPreSceneDrawForwards()
	{
		Set<Integer> hideRoofIds = Set.of(43);
		callbacks.preSceneDraw(scene, 1f, 2f, 3f, 4f, 5f, 6, 7, 8, hideRoofIds);
		verify(delegate).preSceneDraw(scene, 1f, 2f, 3f, 4f, 5f, 6, 7, 8, hideRoofIds);
	}

	@Test
	public void testPostSceneDrawForwards()
	{
		callbacks.postSceneDraw(scene);
		verify(delegate).postSceneDraw(scene);
	}

	@Test
	public void testDrawPassForwards()
	{
		callbacks.drawPass(projection, scene, 3);
		verify(delegate).drawPass(projection, scene, 3);
	}

	@Test
	public void testDrawZoneOpaqueForwards()
	{
		callbacks.drawZoneOpaque(projection, scene, 4, 5);
		verify(delegate).drawZoneOpaque(projection, scene, 4, 5);
	}

	@Test
	public void testDrawZoneAlphaForwards()
	{
		callbacks.drawZoneAlpha(projection, scene, 1, 2, 3);
		verify(delegate).drawZoneAlpha(projection, scene, 1, 2, 3);
	}

	@Test
	public void testDrawDynamicForwards()
	{
		callbacks.drawDynamic(projection, scene, tileObject, renderable, model, 1, 2, 3, 4);
		verify(delegate).drawDynamic(projection, scene, tileObject, renderable, model, 1, 2, 3, 4);
		verifyNoInteractions(substitutor);
	}

	@Test
	public void testDrawDynamicOnARenderThreadForwards()
	{
		callbacks.drawDynamic(9, projection, scene, tileObject, renderable, model, 1, 2, 3, 4);
		verify(delegate).drawDynamic(9, projection, scene, tileObject, renderable, model, 1, 2, 3, 4);
		verify(delegate, never()).drawDynamic(projection, scene, tileObject, renderable, model, 1, 2, 3, 4);
	}

	@Test
	public void testInvalidateZoneForwards()
	{
		callbacks.invalidateZone(scene, 6, 7);
		verify(delegate).invalidateZone(scene, 6, 7);
	}
}
