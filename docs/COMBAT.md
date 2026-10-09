# Combat guide

[English overview](../README.md) · [中文概览](../README.zh-CN.md)

For SlashBlade:Re `0.1.2-26.1.2-port.20`, Minecraft 26.1.2 and NeoForge 26.1.2.114. Client and server must use the same build. Times below assume the normal 20 ticks per second.

## Attacks and combos

Left click attacks and continues the active combo. Swinging at air also starts the server-side animation state. A short right click performs a draw attack. Ground and airborne states have their own combo routes; direction, timing, and the current blade state determine the continuation.

## Slash Arts and Just SA

Hold right click, then release after charging. The base charge threshold is **9 ticks**, about 0.45 seconds. Releasing too early does not activate SA. The default SA in this build is Judgment Cut.

The base Just SA window is **ticks 9–11**. Soul Speed on boots can extend this window up to five ticks; Soul Speed III gives ticks 9–13. Just SA produces the precision cut and its accompanying sword effects.

## Super SA

Hold **V** for at least **20 ticks**, then release. The key can be changed in Minecraft's Controls menu.

The blade must meet all of these conditions:

- At least **1,000 kills**.
- Full durability; neither broken nor sealed.
- Enchanted and recognized as a bewitched blade, through a custom name or its default bewitched flag.

Release consumes **50% durability**, including when no enemy is nearby or the blade is marked unbreakable. Normal hit durability costs can also apply. Switching blades cancels charging; the same blade must remain held until the delayed attack lands.

The effect covers a 64 × 32 × 64 block region around the release point. Eligible mobs are stunned for **40 ticks**. After **25 ticks**, a melee strike and five Judgment Cut damage pulses resolve. Players receive a slowdown instead of a stopped entity tick. Existing targeting and team rules still apply.

## Motion and camera

Draws and sheathing use separate sequences. The blade is drawn along the sheath axis, clears the mouth, then enters the cut. Sheathing recovers the blade, aligns its tip, inserts it, and releases the grip. During two-handed cuts, the sheath follows the pelvis at the side of the body.

First and third person share the final character rig and weapon attachments. The first-person camera follows head animation with bounded rotation. This visual camera movement does not rewrite the player's gameplay aim or network look direction.

The first-person view uses real character proportions. Low-held equipment and overhead cuts can leave the frame; close hands can appear larger. Custom blade models and other character animation systems require separate compatibility checks.

## Reporting an animation issue

Include the blade model, dominant hand, first/third-person view, action sequence, and any resource packs or animation mods. A short video that includes the lead-in and recovery helps distinguish an interrupted transition from a binding problem.

For the tested scope, see [port and verification notes](PORTING.md).
