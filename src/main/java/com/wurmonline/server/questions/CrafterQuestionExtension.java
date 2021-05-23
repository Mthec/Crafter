package com.wurmonline.server.questions;

import com.wurmonline.server.creatures.Creature;
import mod.wurmunlimited.npcs.CrafterMod;

abstract class CrafterQuestionExtension extends Question {
    CrafterQuestionExtension(Creature aResponder, String aTitle, String aQuestion, int aType, long aTarget) {
        super(aResponder, aTitle, aQuestion, aType, aTarget);
    }

    boolean wasSelected(String id) {
        String val = getAnswer().getProperty(id);
        return val != null && val.equals("true");
    }

    float getFloatOrDefault(String id, float _default) {
        String f = getAnswer().getProperty(id);
        if (f != null) {
            try {
                return Float.parseFloat(f);
            } catch (NumberFormatException e) {
                return _default;
            }
        }
        return _default;
    }

    String getPrefix() {
        String prefix = CrafterMod.getNamePrefix();
        if (prefix.isEmpty()) {
            return "";
        } else {
            return prefix + "_";
        }
    }

    String getNameWithoutPrefix(String name) {
        String prefix = CrafterMod.getNamePrefix();
        if (prefix.isEmpty() || name.length() < prefix.length() + 1) {
            return name;
        } else {
            return name.substring(prefix.length() + 1);
        }
    }
}
