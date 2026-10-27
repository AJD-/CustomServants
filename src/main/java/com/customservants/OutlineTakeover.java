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
import com.customservants.compatibility.ModelSwapProtocol;
import com.customservants.compatibility.PartnerOutlines;
import com.customservants.compatibility.PartnerStashProbe;
import com.customservants.compatibility.ServantInteractHighlightOverlay;
import com.customservants.compatibility.ServantOutliner;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.interacthighlight.InteractHighlightPlugin;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * Draws Interact Highlight's NPC outlines in its place while servant models are drawn, so the
 * outline follows the model on screen, and hands them back otherwise. Client thread only, except
 * where said.
 * <p>
 * A partner (Retro NPC Swapper, or the sibling plugin) can do the same, and is told over
 * {@link ModelSwapProtocol} whether this plugin has, along with how to pose the servants this plugin
 * swaps so its outlines can follow them too. The poser handed out only ever poses our own servants:
 * it never asks a partner's poser, so a partner relaying to it cannot loop back.
 * <p>
 * With three plugins able to take the outlines over, only one may at a time:
 * <ul>
 * <li>The first {@link #sync} to take them over wins. Each plugin decides on the client thread and
 * posts its {@code outlines} message synchronously as it does, so a later decision elsewhere always
 * sees an earlier takeover.</li>
 * <li>A partner too old to read our messages still stashes Interact Highlight's setting as it
 * suppresses it, and {@link PartnerStashProbe} reads that stash, so its takeover is seen too.</li>
 * <li>The one unguarded case is when we already own the outlines and a partner that does not
 * cooperate takes them over as well. The README documents it.</li>
 * </ul>
 */
@Singleton
@Slf4j
class OutlineTakeover
{
	@Inject
	private CustomServantsConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private InteractHighlightCompat interactHighlight;

	@Inject
	private ServantInteractHighlightOverlay interactHighlightOverlay;

	@Inject
	private ServantOutliner outliner;

	@Inject
	private EventBus eventBus;

	@Inject
	private PartnerOutlines partnerOutlines;

	@Inject
	private ServantModelCache modelCache;

	@Inject
	private PartnerStashProbe stashProbe;

	/** Set from startUp on the EDT, read on the client thread. */
	private volatile BooleanSupplier canSwap = () -> false;

	// Whether we are currently drawing Interact Highlight's NPC outlines in its place
	private boolean takenOver;

	// What the partners were last told about takenOver, or null when they have been told nothing
	// this start
	private Boolean posted;

	// Handed to the partners, which call it on the client thread while they draw the outlines.
	// Reads canSwap on every call, so a stopped plugin does not pose for a partner before it hears
	// the poser was withdrawn, and a servant is posed only while it is actually being drawn. Never
	// asks a partner's poser: a partner relays its claimed servants here, so asking it back would
	// loop.
	private final Function<NPC, Model> poser = npc -> canSwap.getAsBoolean() ? modelCache.pose(npc) : null;

	/**
	 * Arms the takeover for this start. {@code canSwap} says whether servant models are actually
	 * being drawn right now. Called from startUp.
	 */
	void start(BooleanSupplier canSwap)
	{
		this.canSwap = canSwap;
	}

	/**
	 * Takes over Interact Highlight's NPC outlines, or hands them back.
	 * <p>
	 * Only worth doing while servants are actually being swapped - otherwise the vanilla model is
	 * what gets drawn, and that plugin's own outline already fits it.
	 */
	void sync()
	{
		boolean wanted = config.overrideInteractHighlight()
			&& canSwap.getAsBoolean()
			&& interactHighlight.isInteractHighlightActive();
		boolean next = shouldTakeOver(wanted, takenOver, partnerOutlines.isHandshakeDone(), isPartnerOwning());

		if (next == takenOver)
		{
			return;
		}

		if (next)
		{
			interactHighlight.suppress();
			overlayManager.add(interactHighlightOverlay);
		}
		else
		{
			interactHighlight.restore();
			overlayManager.remove(interactHighlightOverlay);
			outliner.clear();
		}

		// Last, so a failure to write config does not leave us recorded as having taken over
		takenOver = next;
		publish(false);
	}

	/**
	 * Whether a partner has the outlines: by its own message, or, for a partner that does not read
	 * ours, by the stash it keeps while it suppresses them.
	 */
	private boolean isPartnerOwning()
	{
		return partnerOutlines.isAnyOwning() || stashProbe.isPartnerSuppressing();
	}

	/**
	 * Whether to draw the outlines, given whether they are {@code wanted} here at all.
	 * <p>
	 * Only one of this plugin and its partners may: two would both turn Interact Highlight's
	 * settings off, and the second would stash the first one's false as the user's choice, so the
	 * user's real setting would never come back. Whichever took them first keeps them until it lets
	 * go, and none takes them before its handshake, when it cannot yet know whether a partner
	 * already has.
	 */
	static boolean shouldTakeOver(boolean wanted, boolean takenOver, boolean handshakeDone, boolean partnerOwning)
	{
		return wanted && handshakeDone && (takenOver || !partnerOwning);
	}

	/**
	 * Tells the partners whether we draw the outlines, and how to pose our servants. Posted only
	 * when that changes, unless {@code always}.
	 */
	void publish(boolean always)
	{
		if (!always && posted != null && posted == takenOver)
		{
			return;
		}

		posted = takenOver;
		eventBus.post(ModelSwapProtocol.outlinesMessage(ModelSwapProtocol.SOURCE_CUSTOM_SERVANTS, takenOver, poser));
	}

	/**
	 * Tells the partners we have let go of the outlines and the poser, as the plugin stops. Called
	 * after {@link #sync} has handed the outlines back, so a partner that takes them over stashes
	 * Interact Highlight's restored settings rather than our false ones.
	 */
	void withdraw()
	{
		eventBus.post(ModelSwapProtocol.outlinesMessage(ModelSwapProtocol.SOURCE_CUSTOM_SERVANTS, false, null));
		partnerOutlines.reset();
		posted = null;
	}

	/**
	 * Finishes the handshake: a running partner has answered the sync request by now, so deciding
	 * whether to take the outlines over can no longer race its answer.
	 */
	void finishHandshake()
	{
		log.debug("Handshake done; {} the outlines", isPartnerOwning() ? "a partner draws" : "no partner draws");
		partnerOutlines.setHandshakeDone();
		publish(true);
		sync();
	}

	/**
	 * Takes in what a partner says about the outlines. Recorded straight away, so a decision made
	 * after this sees it; acted on later, on the client thread. Any thread.
	 */
	void onPartnerOutlines(String source, ModelSwapProtocol.Outlines outlines)
	{
		partnerOutlines.accept(source, outlines);
		clientThread.invokeLater(this::onPartnerChanged);
	}

	/**
	 * Turns the fix off here too when a partner was drawing the outlines and the user turned
	 * Interact Highlight's back on. Written straight away, so it is off before the partner posts
	 * that it has let go and this plugin would otherwise take the outlines over again. The
	 * ConfigChanged that write posts drives {@link #sync} through the plugin. Any thread.
	 */
	void onPartnerOptOut(String source)
	{
		if (config.overrideInteractHighlight())
		{
			log.debug("Interact Highlight NPC outlines re-enabled by the user under {}; turning the fix off", source);
			configManager.setConfiguration(CustomServantsConfig.GROUP,
				CustomServantsConfig.OVERRIDE_INTERACT_HIGHLIGHT, false);
		}
	}

	/**
	 * Forgets a partner's state as it stops. Any thread.
	 * <p>
	 * Queued rather than done here: the partner hands the outlines back on the client thread after
	 * its shutDown, and a sync done before that restore, seeing the partner gone, would take the
	 * outlines over and stash the partner's false values as the user's.
	 */
	void onPartnerStopped(String source)
	{
		clientThread.invokeLater(() ->
		{
			partnerOutlines.forget(source);
			onPartnerChanged();
		});
	}

	private void onPartnerChanged()
	{
		// The carrier may still hold a model a partner posed
		outliner.clear();
		sync();
	}

	/**
	 * A session that died while suppressing left Interact Highlight's NPC outlines off. Puts them
	 * back, before the next {@link #sync} decides whether to suppress again.
	 */
	void restoreStaleStash()
	{
		interactHighlight.restoreStaleStash();
	}

	/**
	 * Starts over under a new profile. Config is per profile, so the new profile has its own
	 * Interact Highlight settings and none of the stash written under the old one. The takeover is
	 * dropped outright rather than letting {@link #sync} see no change and leave the new profile
	 * unsuppressed.
	 */
	void restartForProfile()
	{
		interactHighlight.forget();
		overlayManager.remove(interactHighlightOverlay);
		outliner.clear();
		takenOver = false;

		interactHighlight.restoreStaleStash();
		sync();
		// sync() sees no change when the new profile is not taken over either, but a partner may
		// still think we draw the outlines
		publish(false);
	}

	/**
	 * Hands the outlines back for good when the user turned Interact Highlight's NPC outlines back on
	 * themselves, rather than fighting over the setting. Any thread.
	 *
	 * @return whether {@code event} was that, and has been dealt with
	 */
	boolean handleUserOverride(ConfigChanged event)
	{
		if (!interactHighlight.isUserOverride(event))
		{
			return false;
		}

		log.debug("Interact Highlight NPC outlines re-enabled by the user; turning the fix off");
		// invoke, not invokeLater: inline when already on the client thread. A partner's restore
		// posts its ConfigChanged on the client thread, and our opt-out has to run inside that
		// restore for Interact Highlight's settings to end up right once it finishes.
		final String changedKey = event.getKey();
		clientThread.invoke(() ->
		{
			// First, so a partner turns its fix off before we post that we have let go
			eventBus.post(ModelSwapProtocol.optOutMessage(ModelSwapProtocol.SOURCE_CUSTOM_SERVANTS));

			// optOut() has to clear the suppression before the write below, or the
			// ConfigChanged it posts comes back through sync() into restore(),
			// which would put the stash back over the value the user just chose.
			interactHighlight.optOut(changedKey);

			configManager.setConfiguration(CustomServantsConfig.GROUP,
				CustomServantsConfig.OVERRIDE_INTERACT_HIGHLIGHT, false);
			sync();
		});
		return true;
	}

	/**
	 * Whether {@code plugin} is Interact Highlight, whose restart undoes the takeover. The same
	 * check as {@link InteractHighlightCompat#isInteractHighlightPlugin}, static so it needs no
	 * instance. Any thread.
	 */
	static boolean isInteractHighlight(Plugin plugin)
	{
		return plugin instanceof InteractHighlightPlugin;
	}

	/** Drops the outline carrier's reference to a posed model, which may be a partner's. */
	void clearCarrier()
	{
		outliner.clear();
	}
}
