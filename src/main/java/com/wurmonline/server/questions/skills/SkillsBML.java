package com.wurmonline.server.questions.skills;

import mod.wurmunlimited.bml.BML;
import mod.wurmunlimited.npcs.CrafterType;

import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Properties;
import java.util.logging.Logger;

public abstract class SkillsBML {
    protected static final Logger logger = Logger.getLogger(SkillsBML.class.getName());

    public abstract BML addBML(BML bml, CrafterType crafterType, float skillCap);

    public abstract Collection<Integer> getSkills(Properties properties);

    boolean wasSelected(String id, Properties properties) {
        String val = properties.getProperty(id);
        return val != null && val.equals("true");
    }

    protected static String format(float f) {
        DecimalFormat df = new DecimalFormat("0.0");
        String floatString = Float.toString(f);
        int idx = floatString.indexOf(".");

        if (idx != -1) {
            df.setMaximumFractionDigits(floatString.length() - idx - 1);
        }

        return df.format(f);
    }
}
