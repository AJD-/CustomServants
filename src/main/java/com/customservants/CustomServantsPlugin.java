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

import com.customservants.compatibility.ModelSwapProtocol;
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
	/** Another Hub plugin, so recognized by class name alone, never imported. */
	private static final String RETRO_PLUGIN_CLASS = "com.retronpcswapper.RetroNpcSwapperPlugin";

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
		attachment.setSubstitutor(modelCache::pose);
		chatheadSwapper.start(this::canSwap);
		clientThread.invoke(() ->
		{
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

		// One runnable, so no frame is drawn between the steps
		clientThread.invoke(() ->
		{
			try
			{
				// A dialogue left open shows the servant's own head again
				chatheadSwapper.stop();
				resetAllNpcs();
			}
			finally
			{
				attachment.detach();
				// Even when detach left our decorator buried beneath another plugin's: it keeps asking
				// for models, and only an empty cache makes it draw the vanilla ones
				modelCache.clear();
				modelCache.setOverride(ServantOverride.DEFAULT);
			}
		});
	}

	@Provides
	CustomServantsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CustomServantsConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!CustomServantsConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

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
		if (RendererAttachment.isRendererPlugin(event.getPlugin()))
		{
			log.debug("Renderer plugin {} {}", event.getPlugin().getName(), event.isLoaded() ? "started" : "stopped");
			// attach() declines on its own when no supported renderer holds the slot
			clientThread.invoke(this::attach);
		}

		// Forced: Retro NPC Swapper ignores claims while it is off, and its own sync request may
		// have been posted before we were listening. Our own start likewise posted claims before
		// Retro NPC Swapper could hear anything from us.
		boolean retroStarted = RETRO_PLUGIN_CLASS.equals(event.getPlugin().getClass().getName()) && event.isLoaded();
		boolean weStarted = event.getPlugin() == this && event.isLoaded();
		if (retroStarted || weStarted)
		{
			clientThread.invoke(() -> partnerClaims.publish(canSwap(), true));
		}
	}

	@Subscribe
	public void onPluginMessage(PluginMessage event)
	{
		// We never ask Retro NPC Swapper for anything ourselves; we only answer it
		if (ModelSwapProtocol.isSyncReq(event, ModelSwapProtocol.SOURCE_RETRO_NPC_SWAPPER))
		{
			clientThread.invoke(() -> partnerClaims.publish(canSwap(), true));
		}
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
		// Before the early returns, so a logged-out or detached state gives the servants back too
		partnerClaims.publish(canSwap(), false);

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
	}

	/**
	 * Writes the override's stand/walk onto a servant whose own sequences cannot pose the override
	 * model, and puts back anything else that still carries them. Client thread only.
	 */
	void processNpc(NPC npc)
	{
		ServantIds.Servant s = ServantIds.get(npc.getId());
		if (s == null)
		{
			// Eg an index that changed into a non-servant
			resetNpc(npc);
			return;
		}
		if (!attachment.isAttached() || !modelCache.ensureBuilt())
		{
			resetNpc(npc);
			return;
		}

		ServantOverride ov = modelCache.getOverride();
		if (s.getRig() == ov.getRig() || ov.getStandAnimation() == -1)
		{
			// Same rig: the servant's own sequences drive the model. Unknown stand: the base is drawn
			// unposed, so the animations are left alone.
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

		int walk = ov.getWalkAnimation() != -1 ? ov.getWalkAnimation() : stand;
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
