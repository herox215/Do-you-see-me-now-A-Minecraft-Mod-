# Do You See Me Now

Fabric mod for Minecraft 1.20.1. Replaces vanilla mob awareness with an actual stealth system.

Vanilla mobs see you through 360°, through darkness, instantly. That's boring. This mod gives them eyes, ears, and a brain.

## How it works

**Field of View** — Mobs see in a 120° cone. Get behind them and they have no idea.

**Detection Meter** — Mobs don't aggro instantly anymore. A bar fills above their head as they become aware of you. How fast depends on distance, light, and whether you're moving. In darkness it's slow. Next to a torch, fast. Within 3 blocks, instant — you can't sneak past their nose.

The bar goes green → yellow → red. Break line of sight and it decays. Some mobs skip the meter entirely (Dragon, Wither, Warden, Enderman).

**Investigation** — At 50% detection, mobs get suspicious and start walking toward you. If you disappear before full detection, they go to your last known position and look around. Hit a mob from the shadows and it investigates where the hit came from instead of magically knowing your position.

**Sound** — Mobs hear you now. Sprinting is loud (16 blocks), walking less so (8), landing depends on fall height (up to 48). Attacks and projectile impacts are heard at 12 blocks. Heavy armor makes you louder (up to 1.75x). Sneaking is silent. One-hit kills are silent — proper stealth kills. You can shoot an arrow somewhere to create a distraction.

Only hostile mobs react to sound (26 vanilla types, configurable).

**Stealth HUD** — Shows up while sneaking. Eye icon with a score bar based on light (35%), mob proximity (25%), armor (15%), movement (15%), and whether you're in a mob's FOV (10%). Uses raycasting, so blocks actually block line of sight.

**Visual feedback** — `!` particle when a mob spots you, `?` when it's searching.

**Aggro grace period** — If a mob loses you briefly during combat (ducking behind a pillar), it re-aggros immediately within 2 seconds. No cheese.

## Config

Everything is configurable — detection speed, decay, FOV angle, range, sound radii, armor multiplier, mob blacklist, individual systems can be toggled on/off.

## Requirements

- Minecraft 1.20.1
- Fabric Loader ≥ 0.15.0
- Fabric API
- Java 17+

## License

MIT
