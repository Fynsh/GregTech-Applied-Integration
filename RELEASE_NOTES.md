# v0.1.0-beta — Matrix Recipe Executor

This is the first **beta** of GregTech - Applied Integration. The core goal is reached: place an encoded pattern into a matrix, request it from AE2, and the matrix runs the corresponding GT recipe with processing cores, overclocking, speed cores, energy and output buffering.

> **Beta / Survival warning**
>
> - **Survival is not playable yet.** The 199 matrix blocks and 4 encoded pattern items have **no crafting recipes**; they can only be obtained in creative mode or via commands.
> - The executor, AE2 provider, overclock, power-loss, AE-disconnect, structure-break and reload paths have only had smoke testing, not a full regression.
> - Do not use this build in a long-term survival world.

## Highlights

- **199 matrix blocks** with datagen-equivalent assets, blockstates, models, loot tables, lang and pickaxe tags.
- Four matrix families: Assembly, Circuit Assembly, Material, Fluid Material.
- Structure scanning/validation: frame/glass/energy hatch/monitor/core/transformer/cleanroom rules, formation explosions.
- AE2 integration: frame ports, channel accounting, actual-linked-channel overload, monitor-driven `ICraftingProvider`.
- Pattern storage GUI (9×4, scrolling, family lock, JEI drag-in) and information page.
- Four custom encoded processing patterns (assembly, circuit assembly, material, fluid material) generated from JEI.
- Recipe executor:
  - strict pattern-output whitelist (input matching intentionally skipped because JEI drops non-consumed items);
  - highest-voltage available processing core first, GT standard overclock (4× EU/t, 0.5× duration per tier);
  - max 16 parallel per voltage tier, shared across processing types, excess cores shown as unavailable;
  - speed cores bound to the selected core voltage, 25% duration reduction per equivalent, max 2 per task;
  - power loss: default reset progress and keep inputs; configurable to consume inputs;
  - structure break: stop immediately and void task inputs, keep buffered outputs;
  - AE disconnect: keep running, buffer outputs, auto-import on reconnect; item overflow drops, fluid overflow is voided;
  - runtime state and output buffer persisted in NBT;
  - information page runtime section (tasks, EU/t, energy, buffer, parallel, speed-core occupancy, overclock).
- Protocol version 6; client and server must both use this build.

## Requirements

- Minecraft 1.20.1
- Forge 47.4.10
- GregTech CEu Modern 7.5.3+
- Applied Energistics 2 15.4.11+
- GuideME
- JEI 15.20.0.x (for pattern encoding)

## Getting started (creative/testing)

1. Install the dependencies and this jar.
2. In creative mode, build a matrix from the `gtai` creative tab.
3. Right-click a frame/glass to open the pattern page; insert encoded patterns.
4. Connect the frame to AE2, request the craft from an AE2 crafting terminal.
5. `/gtai matrix status <pos>` and `/gtai matrix start <pos> <slot>` are available for debugging (OP level 2).

## Known limitations

- No survival recipes for matrix blocks or patterns (creative/commands only).
- Pattern outputs are used as-is; GT chance byproducts are not additionally generated.
- Standard GT overclock is fixed (4× EU/t, 0.5× duration per tier); not configurable yet.
- No full regression test; report issues on GitHub.
