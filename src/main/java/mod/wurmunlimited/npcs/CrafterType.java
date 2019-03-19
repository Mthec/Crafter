package mod.wurmunlimited.npcs;

import com.wurmonline.server.behaviours.MethodsItems;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.skills.Skills;

import java.util.*;

public class CrafterType {
    public static final Integer[] allMetal = new Integer[] { SkillList.SMITHING_BLACKSMITHING, SkillList.GROUP_SMITHING_WEAPONSMITHING, SkillList.SMITHING_GOLDSMITHING, SkillList.SMITHING_ARMOUR_CHAIN, SkillList.SMITHING_ARMOUR_PLATE };
    public static final Integer[] allWood = new Integer[] { SkillList.CARPENTRY, SkillList.CARPENTRY_FINE, SkillList.GROUP_FLETCHING, SkillList.GROUP_BOWYERY };
    public static final Integer[] allArmour = new Integer[] { SkillList.SMITHING_ARMOUR_CHAIN, SkillList.SMITHING_ARMOUR_PLATE, SkillList.LEATHERWORKING, SkillList.CLOTHTAILORING };

    private final Set<Integer> skills;

    public CrafterType(Integer... skills) {
        this.skills = new HashSet<>();
        Collections.addAll(this.skills, skills);
    }

    public boolean hasSkillToImprove(Item item) {
        int improveSkill = MethodsItems.getImproveSkill(item);
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

    @Override
    public boolean equals(Object obj) {
        return obj instanceof CrafterType && ((CrafterType)obj).skills.equals(skills);
    }
}
