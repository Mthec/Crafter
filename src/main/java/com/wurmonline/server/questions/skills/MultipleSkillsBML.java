package com.wurmonline.server.questions.skills;

import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.skills.SkillSystem;
import mod.wurmunlimited.bml.BML;
import mod.wurmunlimited.npcs.CrafterMod;
import mod.wurmunlimited.npcs.CrafterType;

import java.util.*;
import java.util.stream.Collectors;

public class MultipleSkillsBML extends SkillsBML {
    private static final String[] allCrafterTypes = new String[] {
            String.valueOf(SkillList.SMITHING_BLACKSMITHING),
            String.valueOf(SkillList.GROUP_SMITHING_WEAPONSMITHING),
            String.valueOf(SkillList.SMITHING_WEAPON_BLADES),
            String.valueOf(SkillList.SMITHING_WEAPON_HEADS),
            String.valueOf(SkillList.SMITHING_GOLDSMITHING),
            String.valueOf(SkillList.SMITHING_ARMOUR_CHAIN),
            String.valueOf(SkillList.SMITHING_ARMOUR_PLATE),
            String.valueOf(SkillList.CARPENTRY),
            String.valueOf(SkillList.CARPENTRY_FINE),
            String.valueOf(SkillList.GROUP_FLETCHING),
            String.valueOf(SkillList.GROUP_BOWYERY),
            String.valueOf(SkillList.LEATHERWORKING),
            String.valueOf(SkillList.CLOTHTAILORING),
            String.valueOf(SkillList.STONECUTTING),
            String.valueOf(SkillList.SMITHING_SHIELDS),
            String.valueOf(SkillList.POTTERY)
    };

    @Override
    public BML addBML(BML bml, CrafterType crafterType, float skillCap) {
        return bml.text("General crafter options will override specialisations if selected.  Other specialisations are not affected.").italic()
               .text("General crafters:")
               .harray(b -> b
                                    .checkbox("all_metal", "All Metal", crafterType.hasAllMetal())
                                    .checkbox("all_wood", "All Wood", crafterType.hasAllWood())
                                    .checkbox("all_armour", "All Armour", crafterType.hasAllArmour()))
               .text("Specialists:")
               .table(new String[] { "Metal", "Wood", "Misc."},
                       Arrays.asList(
                               new int[] { SkillList.SMITHING_BLACKSMITHING, SkillList.CARPENTRY, SkillList.LEATHERWORKING },
                               new int[] { SkillList.GROUP_SMITHING_WEAPONSMITHING, SkillList.CARPENTRY_FINE, SkillList.CLOTHTAILORING },
                               new int[] { SkillList.SMITHING_GOLDSMITHING, SkillList.GROUP_FLETCHING, SkillList.STONECUTTING },
                               new int[] { SkillList.SMITHING_ARMOUR_CHAIN, SkillList.GROUP_BOWYERY, SkillList.POTTERY },
                               new int[] { SkillList.SMITHING_ARMOUR_PLATE, 0, 0 },
                               new int[] { SkillList.SMITHING_SHIELDS, 0, 0 }

                       ),
                       (row, bml1) -> bml1.forEach(Arrays.stream(row).boxed().collect(Collectors.toList()), (skill, b) -> {
                           if (skill != 0)
                               return b.checkbox(Integer.toString(skill), SkillSystem.getNameFor(skill), crafterType.hasSkill(skill));
                           return b.label(" ");
                       }))
               .newLine()
               .If(CrafterMod.canLearn(),
                       b -> b.harray(b2 -> b2.label("Skill Cap: ").entry("skill_cap", format(skillCap), 5).label("Max: " + format(CrafterMod.getSkillCap())).spacer().label("Max Item QL: " + format(CrafterMod.getMaxItemQL()))),
                       b -> b.harray(b2 -> b2.label("Skill Cap: ").text(format(CrafterMod.getSkillCap())).spacer().label("Max Item QL: " + format(CrafterMod.getMaxItemQL())))
               );
    }

    @Override
    public Collection<Integer> getSkills(Properties properties) {
        Set<Integer> skills = new HashSet<>();
        for (String val : allCrafterTypes) {
            try {
                if (wasSelected(val, properties))
                    skills.add(Integer.parseInt(val));
            } catch (NumberFormatException e) {
                logger.info("Invalid crafter type (" + properties.getProperty(val) + ") received when updating skill settings.  Ignoring.");
            }
        }
        return skills;
    }
}
