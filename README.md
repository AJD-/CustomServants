# Custom Servants

Draws your player-owned house servant (Rick, the Maid, the Cook, the Butler or the Demon butler) as
another NPC, and shows that NPC's chathead in dialogue with the servant. There is one dropdown,
**Servant override**, and it applies to whichever servant you have hired. **Default** leaves the
servant as it normally looks.

## Choices

- Vyrelady (Human)
- Vyrelady (Vampyre)
- Vyrelord (Human)
- Vyrelord (Vampyre)
- Princess Astrid
- Prince Brand
- Elena
- Amy (Mahogany Homes)
- Crondis
- Scabaras
- Amascut
- Apmeken
- Zanik
- Thurgo
- Gnome Child
- Gnome Cheerleader
- Ali Morrisane

## Requirements

**The `GPU` or `117 HD` plugin must be enabled.** The stock CPU renderer and 117 HD's optional
**Legacy renderer** are not supported. The plugin detects this and does nothing, to the body or the
chathead, until a supported renderer holds the renderer slot.

## Compatibility

- **Retro NPC Swapper** : Custom Servants runs alongside, whichever
  order they start in, once those plugins include their `feature/custom-servants-interop` change.
  With it, Retro NPC Swapper also leaves the servant NPCs Custom Servants has claimed alone, through
  the shared `npc-model-swap` plugin-message protocol. With an older build of that plugin (one that 
  does not know this plugin's decorator), one of the two plugins stands down while the other holds 
  the renderer; which one depends on the build and the start order.
- **Interact Highlight**: the **Compatibility** section has a `Fix Interact Highlight outlines`
  checkbox, off by default. The servant is swapped as it is drawn, so anything outlining it through
  the API outlines the original model, which no longer matches what is on screen. With the
  `Interact Highlight` plugin on and the checkbox ticked, while an override is being drawn this
  plugin _turns off_ that plugin's own NPC **Show on hover** and **Show on interact** settings and
  draws those outlines itself: around the override for your servant, and as usual for every other
  NPC, using Interact Highlight's colors and border settings. Its object, ground item and player
  highlights are untouched. Both settings are put back when this plugin stops, when the checkbox is
  unticked or the override is set back to Default, and on the next start if the client closed while
  they were off. The outline traces the drawn model while the clickbox still traces the original
  one, so they can disagree at the edges. Turning either setting back on yourself in the
  `Interact Highlight` config hands the outlines straight back to that plugin and unticks the
  checkbox here.
- With Retro NPC Swapper's interop build the checkbox can be on in either plugin or both: only one
  of them draws the NPC outlines at a time, each around whichever plugin's model is on screen. If
  another plugin with its own `Fix Interact Highlight outlines` option is running and does not
  coordinate with these two, turn the option on in only one of them.

<details>
<summary>How it works</summary>

- The plugin wraps the renderer's draw callbacks and, whenever a servant is drawn, hands the
  renderer a model built from the override NPC's own cache models, with that NPC's recolors, scale
  and NPC lighting.
- **The servant's clickbox and menu are untouched.** The client resolves them from the vanilla model
  before the draw callback runs, so interacting with the servant works exactly as before.
- **Overrides on the same rig as the servant** (Princess Astrid, Prince Brand, Elena, Amy, Amascut
  and Ali Morrisane) are animated by the servant's own animations, so they move and act exactly as
  the servant does.
- **Overrides on another rig** (every other choice) cannot play the servant's animations. They get
  the override NPC's own stand and walk instead, and hold their pose while the servant performs an
  action.
- **The dialogue chathead** is swapped by pointing the chat head widget at the override NPC, so the
  client draws that NPC's chathead from the cache.
- **Nothing is downloaded and nothing is injected.** No models ship with the plugin: everything it
  draws already comes from the live game cache.

</details>

## Known limitations

- Overrides on another rig hold their pose while the servant performs an action, such as greeting
  you or fetching items.
- Large overrides (the Tombs of Amascut deities) are drawn at the servant's tile and may clip
  through house walls.
- The three skeletal-animated deities, Crondis, Scabaras and Apmeken, are the overrides most likely
  to need adjustment after in-game testing.
