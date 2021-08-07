package com.wurmonline.server.questions.skills;

import com.google.common.base.Joiner;
import com.wurmonline.server.skills.SkillSystem;
import mod.wurmunlimited.bml.BML;
import mod.wurmunlimited.npcs.CrafterMod;
import mod.wurmunlimited.npcs.CrafterType;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Properties;

public class SingleSkillBML extends SkillsBML {
    private static String skills = null;

    public SingleSkillBML() {
        if (skills == null) {
            skills = Joiner.on(",").join((Iterable<String>)() -> new Iterator<String>() {
                private int current = -1;

                @Override
                public boolean hasNext() {
                    return current < CrafterType.allSkills.length - 1;
                }

                @Override
                public String next() {
                    return SkillSystem.getNameFor(CrafterType.allSkills[++current]);
                }
            });
        }
    }

    @Override
    public BML addBML(BML bml, CrafterType crafterType, float skillCap) {
        int idx = 0;
        int singleSkill;
        if (crafterType.getAllTypes().length == 0)
            singleSkill = -10;
        else
            singleSkill = crafterType.getAllTypes()[0];


        for (int i = 0; i < CrafterType.allSkills.length; ++i) {
            if (CrafterType.allSkills[i] == singleSkill) {
                idx = i;
                break;
            }
        }

        int finalIdx = idx;
        bml = bml.text("Crafters may only have a single skill on this server.")
                 .spacer()
                 .harray(b -> b.dropdown("skill", skills, finalIdx))
                 .newLine()
                 .If(CrafterMod.canLearn(),
                      b -> b.harray(b2 -> b2.label("Skill Cap: ").entry("skill_cap", format(skillCap), 5).label("Max: " + format(CrafterMod.getSkillCap())).spacer().label("Max Item QL: " + format(CrafterMod.getMaxItemQL()))),
                      b -> b.harray(b2 -> b2.label("Skill Cap: ").text(format(CrafterMod.getSkillCap())).spacer().label("Max Item QL: " + format(CrafterMod.getMaxItemQL()))));

        return bml;
    }

    @Override
    public Collection<Integer> getSkills(Properties properties) {
        try {
            int idx = Integer.parseInt(properties.getProperty("skill"));
            return Collections.singletonList(CrafterType.allSkills[idx]);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            logger.info("Invalid crafter type index (" + properties.getProperty("skill") + ") received when updating skill settings.");
            return Collections.emptyList();
        }
    }
}
