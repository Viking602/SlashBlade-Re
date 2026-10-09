# SlashBlade:Re

**English** · [简体中文](README.zh-CN.md)

## A familiar blade. A new flow.

SlashBlade:Re brings SlashBlade to **Minecraft 26.1.2 on NeoForge**, with reworked katana movement from the first draw to the final sheathe.

Chain your cuts. Time your Slash Arts. Feel the motion carry through your hands, your blade, and your view.

**[Get started ↓](#get-started)** · [Controls](#make-your-first-cut) · [Build from source](#build-from-source) · [Report an issue](https://github.com/Viking602/SlashBlade-Re/issues)

> **Development preview · `0.1.2-26.1.2-port.20`**<br>
> This repository provides source code. Build the preview below; a packaged release is not published here yet.

![First-person and third-person views of the same katana animation frame](docs/media/perspective-comparison.gif)

*One motion, two perspectives. First person on the left; third person on the right. Eight seconds, with matching animation frames. [Watch the MP4](docs/media/perspective-comparison.mp4).*

## Every cut, connected.

**Draw. Cut. Return.**<br>
The blade clears its sheath before the cut. Recovery flows into alignment and sheathing. The saya rests at your side and follows your body.

**Your whole body moves.**<br>
Hips lead. Shoulders turn. Elbows and wrists bend. Hands, hilt, and sheath share a coordinated rig, with original choreography inspired by iaido and the pace of Devil May Cry.

**Change your view. Keep your motion.**<br>
First and third person use the same character pose and weapon attachments. The first-person camera follows the animated head with limited rotation, while your gameplay aim stays under your control.

**SlashBlade, carried forward.**<br>
Ground and air combos, Slash Arts, Just SA, and Super SA join the familiar blades, crafting progression, soul materials, and summoned swords. English and Simplified Chinese are built in.

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

[Read the combat guide →](docs/COMBAT.md)

## Built. Played. Compared.

The `port.20` verification run passed **91 required GameTests** and an isolated client check with **73 installed mods**. First- and third-person rendering were compared across **1,704 samples** covering standing, crouching, walking, airborne poses, and both dominant hands.

These checks cover the built-in blades and default animation set. Custom model proportions, other animation mods, and long multiplayer sessions still need separate testing. Real character proportions also mean a low-held sheath or a raised blade can leave the first-person frame.

[Verification scope and port notes →](docs/PORTING.md)

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

Output: `build/libs/SlashBlade-26.1.2-0.1.2-26.1.2-port.20.jar`.

To run the server-side regression suite, use the same wrapper with `runGameTestServer`. See the [developer notes](docs/PORTING.md#development) before using the local instance scripts or client probes.

## Help shape the next cut

[Open an issue](https://github.com/Viking602/SlashBlade-Re/issues) with your Minecraft, NeoForge, and mod versions, steps to reproduce, and the relevant log. For animation issues, include the blade, dominant hand, camera view, and a short recording if possible.

## Credits & licensing

Based on [SlashBlade 2 by Furia / flammpfeil](https://github.com/flammpfeil/SlashBlade_2), through [Viking602/SlashBlade_2](https://github.com/Viking602/SlashBlade_2). NyMmd is by nyatla; the OBJ importer carries upstream Forge attribution. Existing author and license notices are preserved.

The MIT scope for original SlashBlade:Re contributions and the licenses of inherited code and assets are documented in [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). **The complete inherited project is not licensed under MIT.**

Epic Fight and Devil May Cry informed the animation direction. Their code and animation assets are not bundled; this is an independent community project.
