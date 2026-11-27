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

import com.customservants.compatibility.ClaimedNpcs;
import com.customservants.compatibility.InteractTargetTracker;
import com.customservants.compatibility.PartnerClaims;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Providers;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.runelite.api.Client;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.NPC;
import net.runelite.api.WorldView;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.PostClientTick;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A servant drawn as an override on another rig keeps the override's stand and walk when the client
 * rewrites its animation slots without a spawn or change event. It does while a servant runs an
 * errand, putting the slots back to its definition's about once a second.
 */
public class ServantAnimationRewriteTest
{
	private static final int INDEX = 7;
	private static final ServantOverride OV = ServantOverride.VYRELORD_HUMAN;
	private static final int STAND = OV.getStandAnimation();
	private static final int WALK = OV.getEffectiveWalk();

	private CustomServantsPlugin plugin;
	private Client client;
	private NPC npc;

	// The slots the client keeps per NPC, so a set is seen by later gets like on a real one
	private final AtomicInteger idle = new AtomicInteger();
	private final AtomicInteger idleLeft = new AtomicInteger();
	private final AtomicInteger idleRight = new AtomicInteger();
	private final AtomicInteger walk = new AtomicInteger();
	private final AtomicInteger walk180 = new AtomicInteger();
	private final AtomicInteger walkLeft = new AtomicInteger();
	private final AtomicInteger walkRight = new AtomicInteger();
	private final AtomicInteger run = new AtomicInteger();
	// The sequence the client is playing of those, and how far into it
	private final AtomicInteger pose = new AtomicInteger();
	private final AtomicInteger poseFrame = new AtomicInteger();

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		ServantModelCache modelCache = mock(ServantModelCache.class);
		RendererAttachment attachment = mock(RendererAttachment.class);
		// Providers.of, as toInstance would inject the mocks' own members and need their whole graph
		Injector injector = Guice.createInjector(binder ->
		{
			binder.bind(Client.class).toProvider(Providers.of(client));
			binder.bind(ClientThread.class).toProvider(Providers.of(mock(ClientThread.class)));
			binder.bind(CustomServantsConfig.class).toProvider(Providers.of(mock(CustomServantsConfig.class)));
			binder.bind(EventBus.class).toProvider(Providers.of(mock(EventBus.class)));
			binder.bind(ServantModelCache.class).toProvider(Providers.of(modelCache));
			binder.bind(RendererAttachment.class).toProvider(Providers.of(attachment));
			binder.bind(ChatheadSwapper.class).toProvider(Providers.of(mock(ChatheadSwapper.class)));
			binder.bind(PartnerClaims.class).toProvider(Providers.of(mock(PartnerClaims.class)));
			binder.bind(ClaimedNpcs.class).toProvider(Providers.of(mock(ClaimedNpcs.class)));
			binder.bind(OutlineTakeover.class).toProvider(Providers.of(mock(OutlineTakeover.class)));
			binder.bind(InteractTargetTracker.class).toProvider(Providers.of(mock(InteractTargetTracker.class)));
		});
		plugin = injector.getInstance(CustomServantsPlugin.class);

		npc = mock(NPC.class);
		when(npc.getId()).thenReturn(NpcID.POH_SERVANT_MAITRE_D_MAN);
		when(npc.getIndex()).thenReturn(INDEX);
		slot(idle, npc::getIdlePoseAnimation, npc::setIdlePoseAnimation);
		slot(idleLeft, npc::getIdleRotateLeft, npc::setIdleRotateLeft);
		slot(idleRight, npc::getIdleRotateRight, npc::setIdleRotateRight);
		slot(walk, npc::getWalkAnimation, npc::setWalkAnimation);
		slot(walk180, npc::getWalkRotate180, npc::setWalkRotate180);
		slot(walkLeft, npc::getWalkRotateLeft, npc::setWalkRotateLeft);
		slot(walkRight, npc::getWalkRotateRight, npc::setWalkRotateRight);
		slot(run, npc::getRunAnimation, npc::setRunAnimation);
		slot(pose, npc::getPoseAnimation, npc::setPoseAnimation);
		slot(poseFrame, npc::getPoseAnimationFrame, npc::setPoseAnimationFrame);
		// As the cache defines every servant
		clientWrites(AnimationID.HUMAN_READY, AnimationID.HUMAN_WALK_F, -1);

		when(modelCache.getOverride()).thenReturn(OV);
		when(modelCache.isOurs(anyInt())).thenReturn(true);
		when(modelCache.ensureBuilt()).thenReturn(true);
		when(attachment.isAttached()).thenReturn(true);

		WorldView worldView = mock(WorldView.class);
		IndexedObjectSet<NPC> npcs = mock(IndexedObjectSet.class);
		when(client.getTopLevelWorldView()).thenReturn(worldView);
		doReturn(npcs).when(worldView).npcs();
		when(npcs.byIndex(INDEX)).thenReturn(npc);
	}

	/** Backs one getter/setter pair of the mock NPC with {@code value}. */
	private void slot(AtomicInteger value, IntSupplier getter, IntConsumer setter)
	{
		when(getter.getAsInt()).thenAnswer(i -> value.get());
		// when(npc) hands the mock back in stubbing mode; the setter reference is bound to that mock
		doAnswer(i ->
		{
			value.set(i.getArgument(0));
			return null;
		}).when(npc);
		setter.accept(anyInt());
	}

	/** What the client does to an NPC's slots when it rewrites them: all of them, at once. */
	private void clientWrites(int stand, int walkAnimation, int runAnimation)
	{
		idle.set(stand);
		idleLeft.set(-1);
		idleRight.set(-1);
		walk.set(walkAnimation);
		walk180.set(walkAnimation);
		walkLeft.set(walkAnimation);
		walkRight.set(walkAnimation);
		run.set(runAnimation);
	}

	/** The pose the client has the NPC in, as it picks one from the slots every client tick. */
	private void clientPoses(int animation, int frame)
	{
		pose.set(animation);
		poseFrame.set(frame);
	}

	/** The rewrite seen in game: every slot back to what the cache defines for a servant. */
	private void clientPutsTheDefinitionBack()
	{
		clientWrites(AnimationID.HUMAN_READY, AnimationID.HUMAN_WALK_F, -1);
	}

	@Test
	public void testARewriteWithNoEventIsWrittenOverAgain()
	{
		plugin.onNpcSpawned(new NpcSpawned(npc));
		assertEquals(STAND, idle.get());
		assertEquals(WALK, walk.get());

		// No spawn or change event is posted for this
		clientPutsTheDefinitionBack();
		plugin.onPostClientTick(new PostClientTick());

		assertEquals(STAND, idle.get());
		assertEquals(STAND, idleLeft.get());
		assertEquals(STAND, idleRight.get());
		assertEquals(WALK, walk.get());
		assertEquals(WALK, walk180.get());
		assertEquals(WALK, walkLeft.get());
		assertEquals(WALK, walkRight.get());
		assertEquals(WALK, run.get());
	}

	@Test
	public void testARestoreAfterARewritePutsBackWhatTheClientWroteLast()
	{
		plugin.onNpcSpawned(new NpcSpawned(npc));
		// A set other than the one snapshotted at the spawn, to tell the two apart
		clientWrites(AnimationID.HUMAN_BUTLER_IDLE_MALE_01, AnimationID.HUMAN_BUTLER_WALK_MALE_01,
			AnimationID.HUMAN_BUTLER_RUN_MALE_01);
		plugin.onPostClientTick(new PostClientTick());

		plugin.resetNpc(npc);

		assertEquals(AnimationID.HUMAN_BUTLER_IDLE_MALE_01, idle.get());
		assertEquals(-1, idleLeft.get());
		assertEquals(AnimationID.HUMAN_BUTLER_WALK_MALE_01, walk.get());
		assertEquals(AnimationID.HUMAN_BUTLER_RUN_MALE_01, run.get());
	}

	@Test
	public void testARewriteMidWalkCarriesTheWalkOnFromWhereItWas()
	{
		plugin.onNpcSpawned(new NpcSpawned(npc));
		clientPoses(WALK, 5);
		plugin.onPostClientTick(new PostClientTick());

		clientPutsTheDefinitionBack();
		// By the end of the client tick the client has the servant on its own walk, the frame
		// wrapped to that walk's length
		clientPoses(AnimationID.HUMAN_WALK_F, 0);
		plugin.onPostClientTick(new PostClientTick());

		assertEquals(WALK, pose.get());
		assertEquals(5, poseFrame.get());
	}

	@Test
	public void testARewriteOnTheVeryNextTickStillCarriesTheWalkOn()
	{
		plugin.onNpcSpawned(new NpcSpawned(npc));
		clientPoses(WALK, 5);
		plugin.onPostClientTick(new PostClientTick());
		clientPutsTheDefinitionBack();
		clientPoses(AnimationID.HUMAN_WALK_F, 0);
		plugin.onPostClientTick(new PostClientTick());

		// No tick in between on which the servant was seen showing the override
		clientPutsTheDefinitionBack();
		clientPoses(AnimationID.HUMAN_WALK_F, 0);
		plugin.onPostClientTick(new PostClientTick());

		assertEquals(WALK, pose.get());
		assertEquals(5, poseFrame.get());
	}

	@Test
	public void testARewriteWhileStandingCarriesTheStandOnFromWhereItWas()
	{
		plugin.onNpcSpawned(new NpcSpawned(npc));
		clientPoses(STAND, 9);
		plugin.onPostClientTick(new PostClientTick());

		clientPutsTheDefinitionBack();
		clientPoses(AnimationID.HUMAN_READY, 0);
		plugin.onPostClientTick(new PostClientTick());

		assertEquals(STAND, pose.get());
		assertEquals(9, poseFrame.get());
	}

	@Test
	public void testAServantSettingOffAsItIsRewrittenStartsTheWalk()
	{
		plugin.onNpcSpawned(new NpcSpawned(npc));
		clientPoses(STAND, 9);
		plugin.onPostClientTick(new PostClientTick());

		clientPutsTheDefinitionBack();
		// The client keeps the frame when it changes the sequence
		clientPoses(AnimationID.HUMAN_WALK_F, 9);
		plugin.onPostClientTick(new PostClientTick());

		assertEquals(WALK, pose.get());
		assertEquals(0, poseFrame.get());
	}

	@Test
	public void testAPoseStillTheOverridesIsNotTouchedByARewrite()
	{
		plugin.onNpcSpawned(new NpcSpawned(npc));
		clientPoses(WALK, 5);
		plugin.onPostClientTick(new PostClientTick());

		// Should the client not have picked a pose from the rewritten slots yet
		clientPutsTheDefinitionBack();
		clientPoses(WALK, 6);
		clearInvocations(npc);
		plugin.onPostClientTick(new PostClientTick());

		assertEquals(WALK, walk.get());
		verify(npc, never()).setPoseAnimation(anyInt());
		verify(npc, never()).setPoseAnimationFrame(anyInt());
	}

	@Test
	public void testAServantStillShowingTheOverrideIsLeftAlone()
	{
		plugin.onNpcSpawned(new NpcSpawned(npc));
		clearInvocations(npc);

		plugin.onPostClientTick(new PostClientTick());

		// Above all not the pose frame, which would hold the stand on its first frame
		verify(npc, never()).setPoseAnimationFrame(anyInt());
		verify(npc, never()).setWalkAnimation(anyInt());
	}

	@Test
	public void testNothingIsLookedUpWhileNoServantIsOverridden()
	{
		plugin.onPostClientTick(new PostClientTick());

		verify(client, never()).getTopLevelWorldView();
	}
}
