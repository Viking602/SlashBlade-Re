# SlashBlade:Re

**English** · [简体中文](README.zh-CN.md)

## A familiar blade. A new flow.

SlashBlade:Re brings SlashBlade to **Minecraft 26.1.2 on NeoForge**, with reworked katana movement from the first draw to the final sheathe.

Chain your cuts. Time your Slash Arts. Feel the motion carry through your hands, your blade, and your view.

**[Get started ↓](#get-started)** · [Controls](#make-your-first-cut) · [Build from source](#build-from-source) · [Report an issue](https://github.com/Viking602/SlashBlade-Re/issues)

> **Development preview · `0.1.2-26.1.2-port.24`**<br>
> This repository provides source code. Build the preview below; a packaged release is not published here yet.

![Continuous combat against a training Husk: normal combos, Slash Arts and Super SA](media/combat-preview.gif)

**[Watch the full combat showcase](media/combat-showcase.mp4)** · [First-person recording](https://cdn.jsdelivr.net/gh/Viking602/SlashBlade-Re@7bf343ad5039dfab8b6275542618887e4e8f2b81/media/first-person-combat.mp4) · [Original-motion comparison](media/first-person-original.mp4)

*Fresh, continuous gameplay: normal and extended combos, standard SA, Just SA, and Super SA, including recovery and sheathing. The stationary training Husk has extra health; the display shows actual damage, with no healing during the recording. Videos run at original speed and have no audio.*

## Every cut, connected.

**Draw. Cut. Return.**<br>
The blade clears its sheath before the cut. Recovery flows into alignment and sheathing. The saya rests at your side and follows your body.

**Your whole body moves.**<br>
Hips lead. Shoulders turn. Elbows and wrists bend. Hands, hilt, and sheath share a coordinated rig, with original choreography inspired by iaido and the pace of Devil May Cry.

**Original cuts. A clearer first-person view.**<br>
First person restores SlashBlade's original VMD blade and saya tracks. Quick cuts keep their authored timing; a brief, shared transition settles the weapon pair when a combo is interrupted. No hands, arms, or sleeves obstruct the view, and the resting hilt stays visible.

Both views share the action clock and katana size. Third person uses the articulated character rig; first person uses the original weapon tracks, so their paths differ. The camera follows the animated head with limited rotation, while your gameplay aim stays under your control.

**SlashBlade, carried forward.**<br>
Ground and air combos, Slash Arts, Just SA, and Super SA join the familiar blades, crafting progression, soul materials, and summoned swords. English and Simplified Chinese are built in.

**Find your next blade.**<br>
Inspired by [SlashBlade: Resharped](https://github.com/0999312/SlashBlade_Resharped), named blades have distinct JEI entries and direct crafting upgrades. All **13 named variants** are searchable, including Rusted Blade and Doutanuki. **13 direct upgrade recipes** sit alongside the original **9 material-box anvil routes**. Upgrades preserve kills, refine and enchantments; direct upgrades also retain blade identity, owner and custom name.

JEI is optional. Without it, use the recipe book and advancement recipe views. The new Rusted Blade route and Doutanuki's 100-kill requirement are adapted to this port's existing progression; this build does not include Resharped's full weapon roster, Proud Soul counter, special effects or additional SA set.

Creative attacks no longer consume blade durability. A short blade with a pink durability gauge is broken: repair it on an anvil with a Proud Soul. Existing damaged items are not silently reset.

## Get started

| You need | Version |
| :--- | :--- |
| Minecraft | **26.1.2** |
| NeoForge | **26.1.2.114** — tested baseline |
| Java | **25** |
| SlashBlade:Re | The same build on client and server |

1. [Build the mod](#build-from-source) to obtain the JAR.
2. Install NeoForge for Minecraft 26.1.2, then place the JAR in your instance's `mods` folder. Keep only one SlashBlade JAR installed.
3. Start with a new test world. Find the blades in the SlashBlade creative tab, or follow the in-game advancements and recipes in survival.

The animation system runs inside this mod. **Epic Fight and PlayerAnimator are not required.** Existing Forge 1.20.1 worlds and third-party SlashBlade add-ons have not been validated for this port.

## Make your first cut

| Action | Default input |
| :--- | :--- |
| Attack / continue a combo | Left click |
| Draw attack | Tap right click |
| Slash Arts (SA) | Hold right click to charge, then release |
| Just SA | Release in the precision window — normally ticks 9–11 |
| Super SA | Hold **V** for at least one second, then release |

Super SA requires an eligible enchanted blade with at least **1,000 kills**, full durability, and no broken or sealed state. It consumes **50% durability**. Check the blade tooltip for readiness; **V** can be rebound in Controls.

## Built. Played. Compared.

The `port.24` verification run passed **98 required GameTests** and an isolated client check with **73 installed mods**. All 13 direct upgrades passed crafting, progress-retention and network round-trip tests; the actual JEI runtime verified distinct named blades, visible materials and recipe lookups. GUI icons fill a 15.8-pixel span inside a 16-pixel slot, with a smaller durability gauge.

The first-person renderer matched the original VMD weapon tracks across **852 samples**. Another **28,968 transition samples** checked that the blade/saya relationship stays intact and the transition ends within one action tick. Third-person rig checks covered **1,704 samples**, including its visible arms.

Another **1,932 first-person checks** covered the standard skin, both dominant hands, seven look angles from straight up to straight down, and three head-turn angles. The hilt remained visible at rest, with no arm geometry submitted. The articulated third-person sheathing check found no blade/saya collisions across **12,864 samples**; first person preserves the original VMD relationship instead.

These checks cover the built-in blades and default animation set. Custom model proportions, other animation mods, and long multiplayer sessions still need separate testing. A blade can leave the frame briefly during a wide swing.

## Build from source

Install a **JDK 25** and use the included Gradle wrapper. The first build downloads its dependencies.

```sh
git clone https://github.com/Viking602/SlashBlade-Re.git
cd SlashBlade-Re
```

**Windows (PowerShell)**

```powershell
.\gradlew.bat build
```

**macOS / Linux**

```sh
sh ./gradlew build
```

Output: `build/libs/SlashBlade-26.1.2-0.1.2-26.1.2-port.24.jar`.

To run the server-side regression suite, use the same wrapper with `runGameTestServer` in a disposable checkout. The task writes to a sibling `test-gametest` directory.

## Help shape the next cut

[Open an issue](https://github.com/Viking602/SlashBlade-Re/issues) with your Minecraft, NeoForge, and mod versions, steps to reproduce, and the relevant log. For animation issues, include the blade, dominant hand, camera view, and a short recording if possible.

## Credits & licensing

Based on [SlashBlade 2 by Furia / flammpfeil](https://github.com/flammpfeil/SlashBlade_2), through [Viking602/SlashBlade_2](https://github.com/Viking602/SlashBlade_2). NyMmd is by nyatla; the OBJ importer carries upstream Forge attribution. Existing author and license notices are preserved.

Named-blade recipe and JEI improvements draw on [SlashBlade: Resharped by the M Mysterious Mountain Forging-shop Group](https://github.com/0999312/SlashBlade_Resharped/tree/6e2a0a092fb794d7ea56fd83452869674f3ab1c7). Its MIT notice is retained in the third-party notices. This integration uses models and textures already present in the SlashBlade 2 baseline.

The MIT scope for original SlashBlade:Re contributions and the licenses of inherited code and assets are documented in [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). **The complete inherited project is not licensed under MIT.**

Epic Fight and Devil May Cry informed the animation direction. Their code and animation assets are not bundled; this is an independent community project.
