# Remnant Retribution

A standalone (vanilla-only) colony crisis mod for Starsector 0.98a.

## What it does

Every Remnant warship your fleet destroys, anywhere in the sector, is noticed
by the surviving nodes of the Remnant network and raises its threat assessment
("grudge") of your polity:

| Kill | Points (default) |
|---|---|
| Frigate | 1 |
| Destroyer | 2 |
| Cruiser | 4 |
| Capital | 8 |
| **Remnant Nexus** | **150** |

Consequences, scaling with the grudge:

- The colony crisis bar gains points immediately after each battle
  ("Remnant forces destroyed: +N") plus a monthly trickle while the network
  plans.
- Remnant hunter-killer groups patrol your systems.
- At the crisis bar's minor-event stage (~300), a **reconnaissance-in-force**
  probes one of your colony systems: a few Remnant fleets raiding and
  assessing, no bombardment - the network's warning shot. Fires at most once
  per campaign (vanilla minor-event rule), needs 50+ grudge.
- If the crisis fires as a Remnant retaliation, a large strike force -
  launched from the nearest surviving Nexus, or a dormant Remnant system if
  none survives - descends on one of your colonies and **saturation-bombards
  it on arrival**. It's a live event: intercept the armada in your system and
  destroy it before it reaches the colony, and nothing is bombed.

**Avert it at the source.** When a retaliation becomes impending, it marshals
from a specific surviving Nexus, and that Nexus's location is revealed on your
map. Race there and destroy it *before the fleet launches* and the retaliation
is cancelled outright - no strike, no bombardment. (If the network is already
down to dormant systems with no destructible Nexus, there's nothing to pre-empt
and the strike comes regardless.)

Defeating the retaliation force halves the grudge, but each defeated strike
makes the next one stronger. Machines don't forgive.

## Network fragmentation & permanent neutralization

The sector generates with roughly 8-15 live Nexus stations. Each one you
destroy permanently weakens the network: monthly progress, hunter-killer
presence, and strike strength all scale down with the surviving-Nexus
fraction (to a configurable floor - default 25 percent).

Destroy the **last** Nexus and the shattered network converges for one final,
oversized strike (default 1.5x the normal cap). Defeat it, and the crisis
ends **permanently**: the factor retires from the Colony Crises event and no
further grudge accrues. Lose it, and the fragments fight on at the floor -
every later strike is another final convergence, and defeating any of them
still ends the war.

Prefer the original cruelty? Turn off "Permanent Neutralization Possible" in
settings for the classic eternal grudge (which also disables the easing).

## Configuration

All numbers are tunable two ways:

- **In-game (recommended):** with **LunaLib** enabled, open the settings menu
  (Shift+F2, or from the main menu) and pick "Remnant Retribution". Changes to
  point values, thresholds, and fleet strength apply live. LunaLib is an
  *optional* soft dependency - the mod runs fine without it.
- **File:** `data/config/settings.json` holds the same values and is used as
  the fallback whenever LunaLib is absent.

### Debug section (in the LunaLib menu)

- **Verbose Logging** - writes grudge/kill/crisis events to `starsector.log`
  (prefix `[RemRet]`).
- **Grant Test Colony on Load** - if you own no colony when a save loads,
  transfers the nearest small NPC market to you so the crisis system activates.
  Turn off again afterward.
- **Injected Starting Grudge** - one-time grudge injection on load, to jump
  straight to a filled crisis bar for testing.

## Building

Run `compile.ps1` (needs a JDK 17; set `JAVA_HOME` if it isn't on PATH).
The game itself only needs the built `jars/RemRet.jar`.

## Save compatibility

Safe to add to an existing save. Removing it from a save that has the crisis
active will break that save (standard for crisis mods).
