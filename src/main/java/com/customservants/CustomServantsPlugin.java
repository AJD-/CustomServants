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

import com.customservants.compatibility.InteractHighlightCompat;
import com.customservants.compatibility.InteractTargetTracker;
import com.customservants.compatibility.ModelSwapProtocol;
import com.customservants.compatibility.Partner;
import com.customservants.compatibility.PartnerClaims;
import com.google.inject.Provides;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.WorldView;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Custom Servants",
	description = "Changes how your house servant looks.",
	tags = {"poh", "house", "servant", "butler", "model", "chathead"}
)
public class CustomServantsPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private CustomServantsConfig config;

	@Inject
	private EventBus eventBus;

	@Inject
	private ServantModelCache modelCache;

	@Inject
	private RendererAttachment attachment;

	@Inject
	private ChatheadSwapper chatheadSwapper;

	@Inject
	private PartnerClaims partnerClaims;

	@Inject
	private OutlineTakeover outlineTakeover;

	@Inject
	private InteractTargetTracker targetTracker;

	/** Set on the EDT by startUp/shutDown, read on the client thread. */
	private volatile boolean active;

	/** NPC index to the animations it had before an override's were written over them. Client thread only. */
	private final Map<Integer, OriginalNpcState> originalStates = new HashMap<>();

	@Override
	protected void startUp() throws Exception
	{
		active = true;
		log.info("Custom Servants started");

		// Before the first attach(), which throws without it
		// A decorator buried under one we cannot unwrap keeps calling the substitutor after attach() dropped
		// the wrapper; this keeps body, animations, chathead and claims on the same isAttached() predicate
		attachment.setSubstitutor(npc -> attachment.isAttached() ? modelCache.pose(npc) : null);
		chatheadSwapper.start(this::canSwap);
		outlineTakeover.start(this::canSwap);
		partnerClaims.start(this::canSwap);
		clientThread.invoke(() ->
		{
			// First: a session that died while suppressing left Interact Highlight's NPC outlines
			// off, and that is put right before anything below can fail
			outlineTakeover.restoreStaleStash();
			modelCache.setOverride(config.servantOverride());
			attach();
			// Built now, not only on a servant spawn, so the chathead NPC is resolved before any
			// servant dialogue opens
			modelCache.ensureBuilt();
			recheckLoadedNpcs();
		});

		// Last, as RuneLite registers the plugin itself: the bus does not dedupe, so registering
		// before something that can throw would register it twice on the next start
		eventBus.register(chatheadSwapper);
		eventBus.register(targetTracker);
	}

	@Override
	protected void shutDown() throws Exception
	{
		active = false;
		// Before the client thread work, so Retro NPC Swapper gets the servants back even if it
		// never runs
		partnerClaims.withdraw();
		log.info("Custom Servants stopped");
		eventBus.unregister(chatheadSwapper);
		eventBus.unregister(targetTracker);
		// No early outlines withdraw, unlike the claims: a partner that took the outlines over
		// before the restore below would stash our false values as the user's

		// One runnable, so no frame is drawn between the steps
		clientThread.invoke(() ->
		{
			try
			{
				// A dialogue left open shows the servant's own head again
				quietly("Stopping the chathead swapper", chatheadSwapper::stop);
				quietly("Resetting the servants", this::resetAllNpcs);
			}
			finally
			{
				attachment.detach();
				// Unconditionally, not through sync(): a suppress() that threw partway leaves the
				// suppression on with takenOver false, which sync() would not repair. Puts Interact
				// Highlight's NPC outlines back, removes our overlay and clears the outliner
				quietly("Handing the outlines back", outlineTakeover::letGo);
				// Only after that restore, so a partner that takes the outlines over on hearing this
				// stashes Interact Highlight's own settings rather than our false ones
				quietly("Withdrawing the outlines", outlineTakeover::withdraw);
				// Even when detach left our decorator buried beneath another plugin's: it keeps asking
				// for models, and only an empty cache makes it draw the vanilla ones
				modelCache.clear();
				modelCache.setOverride(ServantOverride.DEFAULT);
				// Again, and last: client thread work runs one item at a time, so this lands after any
				// publish already under way when active went false, and the empty set is the final word
				partnerClaims.withdraw();
			}
		});
	}

	/** Runs one shutdown step, logging rather than throwing if it fails, so the steps after it still run. */
	private static void quietly(String what, Runnable step)
	{
		try
		{
			step.run();
		}
		catch (RuntimeException e)
		{
			log.debug("{} failed", what, e);
		}
	}

	@Provides
	CustomServantsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CustomServantsConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		// First, as it is in Interact Highlight's group: the user turning its NPC outlines back on
		if (outlineTakeover.handleUserOverride(event))
		{
			return;
		}
		// Only our two settings are acted on. Anything else in our group, such as the stash written
		// from inside suppress() and restore(), changes nothing a servant shows, and resetting the
		// servants from inside that write must not happen
		if (!CustomServantsConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		if (CustomServantsConfig.SERVANT_OVERRIDE.equals(event.getKey()))
		{
			log.debug("Config changed: {} = {}", event.getKey(), event.getNewValue());
			clientThread.invoke(() ->
			{
				// Restored before the recheck, so no snapshot is taken of the old override's animations.
				// The chathead swapper re-targets on its own on the next frame.
				resetAllNpcs();
				modelCache.setOverride(config.servantOverride());
				modelCache.ensureBuilt();
				recheckLoadedNpcs();
			});
		}
		else if (CustomServantsConfig.OVERRIDE_INTERACT_HIGHLIGHT.equals(event.getKey()))
		{
			log.debug("Config changed: {} = {}", event.getKey(), event.getNewValue());
			// Only the outlines depend on it
			clientThread.invoke(outlineTakeover::sync);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		log.debug("Game state changed: {}", state);
		switch (state)
		{
			case LOGGED_IN:
				// A build that failed because the cache was still loading is retried once per login:
				// clear() drops the failure memo
				if (!modelCache.isReady())
				{
					modelCache.clear();
				}
				if (attachment.isAttached())
				{
					modelCache.ensureBuilt();
				}
				recheckLoadedNpcs();
				break;
			case LOGIN_SCREEN:
			case HOPPING:
			case CONNECTION_LOST:
				// Those NPCs are gone; there is nothing to restore onto
				originalStates.clear();
				// Nor anything to outline: drop the carrier's reference to a posed model
				outlineTakeover.clearCarrier();
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		processNpc(event.getNpc());
	}

	@Subscribe
	public void onNpcChanged(NpcChanged event)
	{
		// A multi-NPC transform gives the NPC a different id, which the spawn-time check never saw
		processNpc(event.getNpc());
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		// Nothing to restore onto: the NPC is gone, and its index may be reused
		if (originalStates.remove(event.getNpc().getIndex()) != null)
		{
			log.debug("Forgot animations of despawned servant at index {}", event.getNpc().getIndex());
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		// Cheap guard: the GPU plugin and 117 HD overwrite the draw callbacks slot when they restart,
		// sometimes without any plugin event. RuneLite unregisters a plugin before calling shutDown,
		// so the active guard only covers a tick already mid-dispatch when shutDown runs.
		if (active && attachment.isLost())
		{
			attach();
		}
	}

	@Subscribe
	public void onPluginChanged(PluginChanged event)
	{
		Plugin plugin = event.getPlugin();
		Partner partner = Partner.byPluginClass(plugin.getClass().getName());
		if (plugin == this && event.isLoaded())
		{
			// Our own start posted claims before any partner could hear us, and a partner already
			// running cannot know we started: ask it for its state, and send ours. Forced, as the
			// start-up publish went out before anyone was listening.
			log.debug("Started; asking the partners for their state");
			eventBus.post(ModelSwapProtocol.synMessage(ModelSwapProtocol.SOURCE_CUSTOM_SERVANTS));
			clientThread.invoke(() -> partnerClaims.publish(true));
			// The partners answer the syn through invoke, so their answers are queued ahead of this
			clientThread.invokeLater(outlineTakeover::finishHandshake);
		}
		else if (RendererAttachment.isRendererPlugin(plugin))
		{
			log.debug("Renderer plugin {} {}", plugin.getName(), event.isLoaded() ? "started" : "stopped");
			// attach() declines on its own when no supported renderer holds the slot
			clientThread.invoke(this::attach);
		}
		else if (InteractHighlightCompat.isInteractHighlightPlugin(plugin))
		{
			// Its restart undoes the suppression, and its stop leaves nothing to take over
			log.debug("Interact Highlight {}", event.isLoaded() ? "started" : "stopped");
			clientThread.invoke(outlineTakeover::sync);
		}
		else if (partner != null)
		{
			log.debug("Partner {} {}", partner.getSource(), event.isLoaded() ? "started" : "stopped");
			if (event.isLoaded())
			{
				// Forced: a partner ignores claims and outlines while it is off, and its own sync
				// request may have been posted before we were listening
				answerPartner(partner);
			}
			else
			{
				outlineTakeover.onPartnerStopped(partner.getSource());
			}
		}
	}

	@Subscribe
	public void onPluginMessage(PluginMessage event)
	{
		// partnerSource only answers with a source in PARTNERS, each of which is a Partner
		Partner syncFrom = Partner.bySource(ModelSwapProtocol.partnerSource(event, ModelSwapProtocol.SYN));
		if (syncFrom != null)
		{
			log.debug("Sync request from {}", syncFrom.getSource());
			answerPartner(syncFrom);
			return;
		}

		String outlinesFrom = ModelSwapProtocol.partnerSource(event, ModelSwapProtocol.OUTLINES);
		if (outlinesFrom != null)
		{
			ModelSwapProtocol.Outlines outlines = ModelSwapProtocol.readOutlines(event, outlinesFrom);
			if (outlines != null)
			{
				outlineTakeover.onPartnerOutlines(outlinesFrom, outlines);
			}
			return;
		}

		String optOutFrom = ModelSwapProtocol.partnerSource(event, ModelSwapProtocol.OUTLINES_OPT_OUT);
		if (optOutFrom != null)
		{
			outlineTakeover.onPartnerOptOut(optOutFrom);
		}
	}

	@Subscribe
	public void onProfileChanged(ProfileChanged event)
	{
		// Config is per profile, so the takeover starts over under the new one's settings
		log.debug("Profile changed; restarting the outline takeover");
		clientThread.invoke(outlineTakeover::restartForProfile);
	}

	/**
	 * Sends {@code partner} our current state, forced: our claims when it reads them, then our
	 * outlines. On the client thread, through invoke.
	 */
	private void answerPartner(Partner partner)
	{
		clientThread.invoke(() ->
		{
			if (partner.isReadsClaims())
			{
				partnerClaims.publish(true);
			}
			outlineTakeover.publish(true);
		});
	}

	/**
	 * Whether the dialogue chathead may be swapped: the head follows the body, so no renderer means no
	 * head swap either. Client thread only.
	 */
	private boolean canSwap()
	{
		return active && attachment.isAttached() && modelCache.isReady();
	}

	/** Takes the draw callbacks slot, and re-decides every loaded servant if that changed anything. */
	private void attach()
	{
		if (attachment.attach())
		{
			log.debug("Renderer attachment changed; attached: {}", attachment.isAttached());
			recheckLoadedNpcs();
		}
	}

	/** Re-decides every loaded NPC. Only on transitions, never per tick or frame. Client thread only. */
	private void recheckLoadedNpcs()
	{
		// Before the early returns, so a detach or failed build gives the servants back even when
		// there is no scene to recheck
		partnerClaims.publish(false);
		// Likewise, as this is where canSwap changes; free when the decision has not
		outlineTakeover.sync();

		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return;
		}

		for (NPC npc : worldView.npcs())
		{
			if (npc != null)
			{
				processNpc(npc);
			}
		}

		// Again, as processNpc may have built the model just now: claimed in the same transition.
		// Free when nothing changed, as an unchanged set is not posted again.
		partnerClaims.publish(false);
		outlineTakeover.sync();
	}

	/**
	 * Writes the override's stand/walk onto a servant whose own sequences cannot pose the override
	 * model, and puts back anything else that still carries them. Client thread only.
	 */
	void processNpc(NPC npc)
	{
		ServantIds.Servant s = ServantIds.get(npc.getId());
		ServantOverride ov = modelCache.getOverride();
		// Not a servant (eg an index that changed into a non-servant), or nothing to draw it as. Same
		// rig: the servant's own sequences drive the model. Unknown stand: the base is drawn unposed,
		// so the animations are left alone.
		if (s == null || !attachment.isAttached() || !modelCache.ensureBuilt()
			|| s.getRig() == ov.getRig() || ov.getStandAnimation() == -1)
		{
			resetNpc(npc);
			return;
		}
		applyAnimationOverride(npc, ov);
	}

	/**
	 * Snapshots the servant's animations, unless it already shows the override's (the snapshot would
	 * be of our own values), then writes the override's stand/walk over every pose and movement slot.
	 */
	void applyAnimationOverride(NPC npc, ServantOverride ov)
	{
		int idx = npc.getIndex();
		int stand = ov.getStandAnimation();
		// A second snapshot only when the client has put vanilla values back (eg after a server-side
		// change); while it still shows our stand, the existing snapshot is the original one
		if (!originalStates.containsKey(idx) || npc.getIdlePoseAnimation() != stand)
		{
			originalStates.put(idx, OriginalNpcState.capture(npc));
		}

		int walk = ov.getEffectiveWalk();
		npc.setIdlePoseAnimation(stand);
		npc.setPoseAnimation(stand);
		npc.setIdleRotateLeft(stand);
		npc.setIdleRotateRight(stand);
		npc.setWalkAnimation(walk);
		npc.setWalkRotate180(walk);
		npc.setWalkRotateLeft(walk);
		npc.setWalkRotateRight(walk);
		npc.setRunAnimation(walk);
		npc.setPoseAnimationFrame(0);
		log.debug("Applied {} stand {} / walk {} to servant {} at index {}", ov, stand, walk, npc.getId(), idx);
	}

	/** Puts back the animations snapshotted for this NPC's index, if any. Client thread only. */
	void resetNpc(NPC npc)
	{
		OriginalNpcState st = originalStates.remove(npc.getIndex());
		if (st != null)
		{
			st.restore(npc);
			log.debug("Restored animations of NPC {} at index {}", npc.getId(), npc.getIndex());
		}
	}

	/**
	 * Puts back every snapshot whose index still holds a servant, then forgets them all. Indices are
	 * reused, so a snapshot is never written onto a later, different occupant. Client thread only.
	 */
	private void resetAllNpcs()
	{
		WorldView worldView = client.getTopLevelWorldView();
		for (Map.Entry<Integer, OriginalNpcState> e : originalStates.entrySet())
		{
			NPC npc = worldView == null ? null : worldView.npcs().byIndex(e.getKey());
			if (npc != null && ServantIds.isServant(npc.getId()))
			{
				e.getValue().restore(npc);
			}
		}
		originalStates.clear();
	}
}
