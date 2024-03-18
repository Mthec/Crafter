package mod.wurmunlimited.npcs;

import com.wurmonline.server.behaviours.MethodsItems;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.skills.Skills;

import java.util.*;

public class CrafterType {
    public static final Integer[] allMetal = new Integer[] { SkillList.SMITHING_BLACKSMITHING, SkillList.GROUP_SMITHING_WEAPONSMITHING, SkillList.SMITHING_GOLDSMITHING, SkillList.SMITHING_ARMOUR_CHAIN, SkillList.SMITHING_ARMOUR_PLATE, SkillList.SMITHING_SHIELDS };
    public static final Integer[] allWood = new Integer[] { SkillList.CARPENTRY, SkillList.CARPENTRY_FINE, SkillList.GROUP_FLETCHING, SkillList.GROUP_BOWYERY };
    public static final Integer[] allArmour = new Integer[] { SkillList.SMITHING_ARMOUR_CHAIN, SkillList.SMITHING_ARMOUR_PLATE, SkillList.LEATHERWORKING, SkillList.CLOTHTAILORING };
    public static final Integer[] allSkills = new Integer[] {
            SkillList.SMITHING_BLACKSMITHING,
            SkillList.GROUP_SMITHING_WEAPONSMITHING,
            SkillList.SMITHING_GOLDSMITHING,
            SkillList.SMITHING_ARMOUR_CHAIN,
            SkillList.SMITHING_ARMOUR_PLATE,
            SkillList.CARPENTRY,
            SkillList.CARPENTRY_FINE,
            SkillList.GROUP_FLETCHING,
            SkillList.GROUP_BOWYERY,
            SkillList.LEATHERWORKING,
            SkillList.CLOTHTAILORING,
            SkillList.STONECUTTING,
            SkillList.SMITHING_SHIELDS,
            SkillList.POTTERY
    };

    private final Set<Integer> skills;

    public CrafterType(Integer... skills) {
        this.skills = new HashSet<>();
        if (CrafterMod.useSingleSkill()) {
            if (skills.length >= 1)
                this.skills.add(skills[0]);
        } else {
            Collections.addAll(this.skills, skills);
        }
    }

    public boolean hasSkillToImprove(Item item) {
        int improveSkill = getNearestSkill(item);
        return skills.contains(improveSkill);
    }

    public List<Skill> getSkillsFor(Creature crafter) {
        List<Skill> toReturn = new ArrayList<>();
        Skills creatureSkills = crafter.getSkills();
        for (Integer skillNum : skills) {
            toReturn.add(creatureSkills.getSkillOrLearn(skillNum));
        }

        return toReturn;
    }

    public Integer[] getAllTypes() {
        return skills.toArray(new Integer[0]);
    }

    public boolean needsForge() {
        List<Integer> metal = Arrays.asList(allMetal);
        return skills.stream().anyMatch(metal::contains);
    }

    public boolean hasSkill(int skill) {
        return skills.contains(skill);
    }

    private boolean hasAll(Integer[] skillsArray) {
        for (int skill : skillsArray) {
            if (!skills.contains(skill))
                return false;
        }
        return true;
    }

    public boolean hasAllMetal() {
        return hasAll(allMetal);
    }

    public boolean hasAllWood() {
        return hasAll(allWood);
    }

    public boolean hasAllArmour() {
        return hasAll(allArmour);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof CrafterType && ((CrafterType)obj).skills.equals(skills);
    }

    /**
     * Returns the skill provided by MethodsItems.getImproveSkill(item), except it coerces some skills into the ones
     * used by the Crafter type system.  To enable compatibility with some modded items.
     */
    public static int getNearestSkill(Item item) {
        int skill = MethodsItems.getImproveSkill(item);
        switch (skill) {
            case SkillList.SMITHING_WEAPON_BLADES:
            case SkillList.SMITHING_WEAPON_HEADS:
                skill = SkillList.GROUP_SMITHING_WEAPONSMITHING;
                break;
            case SkillList.FIREMAKING:
            case SkillList.TOYMAKING:
                skill = SkillList.CARPENTRY;
                break;
            case SkillList.GROUP_SMITHING:
            case SkillList.SMITHING_LOCKSMITHING:
                skill = SkillList.SMITHING_BLACKSMITHING;
                break;
            case SkillList.MASONRY:
            case SkillList.PAVING:
                skill = SkillList.STONECUTTING;
                break;
            case SkillList.GROUP_TAILORING:
                if (item.isLeather()) {
                    skill = SkillList.LEATHERWORKING;
                } else if (item.isCloth()) {
                    skill = SkillList.CLOTHTAILORING;
                }
                break;
        }

        return skill;
    }
}
