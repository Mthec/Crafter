package com.wurmonline.server.creatures;

import java.io.IOException;

public class CreaturePackageCaller {
    public static void saveCreatureName(Creature creature, String name) throws IOException {
        creature.getStatus().saveCreatureName(name);
        creature.setName(name);
    }
}
