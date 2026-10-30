# DragonBuff (Paper 1.21.11)

Build:   mvn clean package     (needs JDK 21 + Maven)  - or push to GitHub, Actions builds it
Install: copy the jar into /plugins

## Custom models (optional)
1. Install BetterModel (Paper jar) from Modrinth/Hangar.
2. Start the server once with DragonBuff. It copies models/*.bbmodel into plugins/BetterModel/models.
3. RESTART the server. BetterModel builds its resource pack; Java players must accept it.
4. Spawn the dragon - the model is attached to it (and to every summoned vex).

Models are plain Blockbench files: open src/main/resources/models/*.bbmodel in Blockbench to edit.

Bedrock (Geyser) players cannot see BetterModel models. See models.hide-original in config.yml.
