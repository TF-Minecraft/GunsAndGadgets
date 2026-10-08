# GunsAndGadgets

> Modular firearms and gunsmithing for TF-Minecraft.

GunsAndGadgets brings together weapon assembly and gun combat. Players build firearms from individual components at a gunsmithing station, then use weapons whose handling and performance follow the parts chosen. Rifles, pistols, shotguns, and launchers each have their own assembly requirements.

## Features

- **Modular assembly** — select a weapon type and combine barrels, loaders, chambers, actions, and stocks where the design requires them.
- **Meaningful parts** — components contribute to accuracy, damage, range, capacity, reload speed, fire rate, spread, and piercing.
- **Ammunition compatibility** — choose supported ammunition for the next reload, with the selection saved on the gun.
- **Reloading and handling** — timed reloads, progress feedback, character attribute effects, and loaded or unloaded appearances communicate weapon state.
- **Projectile combat** — shot trajectories, impacts, sounds, and visual effects support firearm and launcher attacks.
- **Weapon appearances** — skin support keeps custom gun designs connected to their firing and reload states.
- **Maintained weapons** — existing guns pick up changed part stats when players join, open world storage, or handle their inventory.
- **Staff provisioning** — give completed weapons using the same part and design validation as normal assembly.

## From workbench to combat

The assembly interface lets players inspect available components and the resulting weapon before completing a craft. Those choices continue to matter in combat through the gun's statistics, ammunition, and handling, making gunsmithing part of how a character prepares their equipment.

## Documentation

[Project documentation](https://github.com/TF-Minecraft/Docs/blob/main/projects/GunsAndGadgets/README.md)

Technical documentation is maintained in [TF-Minecraft/Docs](https://github.com/TF-Minecraft/Docs).

[Commands and ammunition handling](https://github.com/TF-Minecraft/Docs/blob/main/projects/GunsAndGadgets/operations.md)

## Tests

With Java 21 and the pinned plugin dependencies installed, run `mvn clean verify`.
Tests use JUnit 5, Mockito, and MockBukkit. Surefire test results are in
`target/surefire-reports/`; JaCoCo HTML and XML reports are in `target/site/jacoco/`.
CI uploads both. Verification requires 100% line, branch, and instruction
coverage of production code, with no coverage exclusions.
Live projectile combat, client models and effects, and the complete server plugin
stack require separate in-game checks.

## License

Copyright (c) 2026 TF-Minecraft contributors.

TF-Minecraft-authored material in this repository is licensed under the
[Artistic License 2.0](LICENSE). Third-party dependencies and bundled material
retain their own licenses.
