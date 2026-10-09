# Port and verification notes

[English overview](../README.md) · [中文概览](../README.zh-CN.md)

## Baseline

SlashBlade:Re is a community continuation of SlashBlade 2, based on [`Viking602/SlashBlade_2` at `3fd99e26708416cc6ca346fc39b7e3881564e72d`](https://github.com/Viking602/SlashBlade_2/tree/3fd99e26708416cc6ca346fc39b7e3881564e72d). The original project is by [Furia / flammpfeil](https://github.com/flammpfeil/SlashBlade_2).

| Component | Current baseline |
| :--- | :--- |
| Mod | `0.1.2-26.1.2-port.20` |
| Minecraft | 26.1.2 |
| NeoForge | 26.1.2.114 |
| Java | 25 |
| Gradle / ModDevGradle | 9.2.1 / 2.0.148 |

## What changed

- Persistent blade state uses data components; player state uses NeoForge attachments. Network messages use payloads and stream codecs.
- Registration, recipes, advancements, loot, damage, projectiles, and summoned swords are adapted to the new APIs and resource paths.
- Rendering uses state extraction and deferred submission, with OBJ blade geometry, dedicated item rendering, the durability ring, HUD, and lock-on indicator.
- Original katana choreography coordinates the pelvis, chest, head, arms, and legs. Fixed-length arm constraints and elbow/wrist skinning keep hands and weapon grips together.
- Draw and sheathe sequences transfer the sheath between the side waist attachment and the support hand. Sheathing uses its own alignment and insertion sequence.
- First person extracts the same native avatar state and final rig used by third person, then projects its arms and equipment into the actual camera. There are no separate first-person shoulder offsets or weapon scale adjustments.
- The visual first-person camera follows head motion with rotation limits. Gameplay aiming remains unchanged.
- English and Simplified Chinese resources cover the blade catalog, materials, controls, advancements, and ability tooltips.

The mod retains its `slashblade` identifier and existing content identifiers. This is not a conversion tool for Forge 1.20.1 saves.

## Verification

The local `port.20` validation completed on **2026-10-09**. Its detailed logs and full fixture remain local; a portable summary is included in [verification/port-20.json](verification/port-20.json).

| Check | Result |
| :--- | :--- |
| Required GameTests | 91 passed |
| Integrated client | 73-mod fixture; load, reload, world and real attack/SA checks passed |
| Same-frame hand / weapon attachments | 4,828 samples; no differing samples |
| Actual first / third-person render submissions | 1,704 comparisons; 4,247,456 vertices |
| Largest compared vertex error | `2.9802322e-7` blocks |
| Sheathing geometry | 12,864 samples; no detected collision frames; all 64 intentional collision controls detected |
| Head / camera synchronization | 14,484 samples |

The render comparison exercises the registered `RenderHandEvent` and actual `AvatarRenderer.submit` path, not just two calls to the same pose helper. It covers the 71 default animation clips, both dominant hands, and standing, actual crouching, walking, and airborne states. Additional attachment checks cover standard and slim player models.

The [eight-second comparison](media/perspective-comparison.mp4) uses fixed matching animation and native idle clocks. Both characters look down 20 degrees; the third-person side uses an external camera. This is a scripted visual comparison. Separate local real-time combat recordings exercise actual server damage and SA effects. The comparison clock is opt-in test code, disabled in normal play.

These results do not establish compatibility with every mod pack, shader, custom OBJ origin, resource pack, or third-party animation override. Long multiplayer sessions and upgrading existing Forge 1.20.1 saves remain unverified. The original reverse-subtract glow is approximated by the modern blend pipeline, not claimed to be pixel-identical.

## Development

Use JDK 25 and the included Gradle wrapper:

```powershell
.\gradlew.bat build
.\gradlew.bat runGameTestServer
```

On macOS / Linux, use `sh ./gradlew` in place of `.\gradlew.bat`. The build produces its JAR under `build/libs/`. The runtime checks reported above were performed on Windows; other operating systems were not exercised in that run.

**Local instance tooling:** `Build-Project02.ps1`, `Start-VerificationServer.ps1`, and the Python files in `tools/` were written for the original development workspace. Some migrate files in place or expect external game and tool directories. They are retained as development history, not needed for a normal Gradle build.

The current Gradle `runClient` configuration points two directories above the checkout, while `runServer` and `runGameTestServer` use sibling test directories. Set dedicated disposable game directories in `build.gradle` before running a development client in another workspace. Never point verification probes at a valued world.

Client probes under `src/main/java/mods/flammpfeil/slashblade/verification` require explicit verification properties; world-mutating probes additionally check an isolated fixture marker. Normal launches leave these probes disabled. Server GameTests are registered only when the NeoForge GameTest facility is enabled.

## Provenance and licensing

See [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md). Creating an independent repository does not remove the upstream attribution or change inherited license terms.

Epic Fight's public source was consulted for the organization of joint hierarchies, skinning, and item attachments. Weapons of Miracles and Devil May Cry informed movement timing; iaido references informed draw and sheathe staging. No code, models, textures, or animations from those reference projects were imported into this implementation. Original SlashBlade resources and embedded third-party components retain their own provenance.
