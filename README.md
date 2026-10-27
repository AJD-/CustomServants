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

- **Retro NPC Swapper** and **Custom NPC Models**: Custom Servants runs alongside both, whichever
  order they start in, once those plugins include their `feature/custom-servants-interop` change.
  With it, Retro NPC Swapper also leaves the servant NPCs Custom Servants has claimed alone, through
  the shared `npc-model-swap` plugin-message protocol; for Custom NPC Models the change only covers
  sharing the renderer. Older builds of either plugin stand down while Custom Servants holds the
  renderer, or Custom Servants stands down if they attached first.
- A **Custom NPC Models** pack that binds a servant NPC competes with this plugin for that servant.
  Whichever plugin attached to the renderer first wins.
- With the `Interact Highlight` plugin on, the servant's hover and interact outline traces the
  vanilla model, not the override.

<details>
<summary>How it works</summary>

- The plugin wraps the renderer's draw callbacks and, whenever a servant is drawn, hands the
  renderer a model built from the override NPC's own cache models, with that NPC's recolours, scale
  and NPC lighting.
- **The servant's clickbox and menu are untouched.** The client resolves them from the vanilla model
  before the draw callback runs, so interacting with the servant works exactly as before.
- **Overrides on the same rig as the servant** (the human NPCs) are animated by the servant's own
  animations, so they move and act exactly as the servant does.
- **Overrides on another rig** cannot play the servant's animations. They get the override NPC's own
  stand and walk instead, and hold their pose while the servant performs an action.
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
