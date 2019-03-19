package com.wurmonline.server.questions;

import com.wurmonline.server.creatures.Creature;

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
        try {
            return Float.parseFloat(f);
        } catch (NumberFormatException e) {
            return _default;
        }
    }
}
