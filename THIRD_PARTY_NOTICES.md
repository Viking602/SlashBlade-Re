# Third-party notices

SlashBlade:Re is a derivative community project. An independent GitHub repository does not alter the origin or license of inherited material.

## SlashBlade 2

- Original project: [Furia / flammpfeil, SlashBlade 2](https://github.com/flammpfeil/SlashBlade_2).
- Source used for this port: [Viking602/SlashBlade_2](https://github.com/Viking602/SlashBlade_2).
- Baseline: [`3fd99e26708416cc6ca346fc39b7e3881564e72d`](https://github.com/Viking602/SlashBlade_2/tree/3fd99e26708416cc6ca346fc39b7e3881564e72d).
- Original license declaration, preserved verbatim:

  > NyMmd-MIT:nyatla, ObjModelImporter:forge, All other Rights reserved.

No blanket MIT grant for inherited SlashBlade source or assets has been established. Their original notices remain applicable, including where inherited files have been modified. The MIT grant in this repository does not replace permission that may be required from upstream rights holders for distribution or reuse of their material.

## NyMmd

The embedded `src/main/java/jp/nyatla/nymmd/` implementation includes notices identifying **Copyright (C)2008–2012 nyatla** and the MIT License. Those notices and their permission text remain in the source files. New contributions do not remove the original attribution.

## OBJ model importer

The upstream license declaration attributes the OBJ importer to **forge**. Files under `src/main/java/mods/flammpfeil/slashblade/client/renderer/model/obj/` retain their inherited headers. This project does not replace that attribution with its own MIT grant or infer a broader grant from it.

## Gradle wrapper and dependencies

The Gradle wrapper retains its own copyright and Apache License 2.0 notices. Minecraft, NeoForge, Gradle, and other build/runtime dependencies are governed by their respective licenses and terms. This repository does not include a Minecraft distribution.

## Original SlashBlade:Re contributions

Original contributions made after the baseline above are offered under the scoped [MIT grant](LICENSE), to the extent the contributors hold the necessary rights. That grant covers the original contribution, not any inherited expression inside the same file.

## Movement references

Epic Fight, Weapons of Miracles, and Devil May Cry are credited as design references, not included dependencies or bundled animation libraries. The new katana choreography is authored in this project. Models, textures, and animation files inherited from SlashBlade remain under their upstream terms.

---

中文说明：本项目新增原创贡献采用 MIT；上游主体源码、模型、贴图、动作资源及其他第三方内容保留原许可。修改文件或新建独立仓库不会自动使继承内容变为 MIT。本仓库不声明整份项目统一采用 MIT。

## SlashBlade: Resharped

Reference and adaptation source: [0999312/SlashBlade_Resharped](https://github.com/0999312/SlashBlade_Resharped/tree/6e2a0a092fb794d7ea56fd83452869674f3ab1c7), version 1.9.65, commit `6e2a0a092fb794d7ea56fd83452869674f3ab1c7`.

The built-in blade definitions, progression recipes, requirements, soul material conversions, growth economy, acquisition rules and JEI subtype strategy are adapted from this reference for Minecraft 26.1.2. The port also adapts the eight built-in Slash Arts, Wither Edge special effect, blade-stand art/effect/enchantment operations, server configuration, damage and repair rules, entity-drop and named-blade registries, and related events. Optional integrations targeting Forge 1.20.1 are not included as binary-compatible bridges.

The port adds these 18 model/texture resources from the reference under `assets/slashblade/model/`: `named/agito_false.png`, `named/agito_rust.png`, `named/dios/dios.obj`, `named/dios/koseki.png`, `named/orotiagito.png`, `named/agito_rust_true.png`, `named/agito_true.png`, `rodai_diamond.png`, `rodai_golden.png`, `rodai_iron.png`, `rodai_netherite.png`, `rodai_stone.png`, `rodai_wooden.png`, `named/sange/sange.png`, `named/tagayasan.png`, `named/yasha/yasha.obj`, `named/yasha/yasha.png`, and `named/yasha/yasha_true.obj`. Upstream reserves artwork rights to the respective authors; these resources are not relicensed under this repository's MIT grant. Existing artwork retains its prior notices.

Additional port.26 resources from that same reference are `assets/slashblade/model/util/drive.obj`, `assets/slashblade/combostate/piercing.vmd`, and `assets/slashblade/combostate/piercing_pl.vmd`. Their original resource terms remain applicable.

The Resharped code license follows, preserved verbatim:
MIT License

Copyright (c) 2024 M Mysterious Mountain Forging-shop Group

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
