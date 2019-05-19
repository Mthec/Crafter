package mod.wurmunlimited;

import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.CreatureTemplateFactory;
import com.wurmonline.server.economy.FakeShop;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.zones.Zones;
import mod.wurmunlimited.npcs.*;

public class CrafterObjectsFactory extends WurmObjectsFactory {

    public CrafterObjectsFactory() throws Exception {
        super();

        new CrafterMod().onItemTemplatesCreated();
        new CrafterTemplate().createCreateTemplateBuilder().build().setCreatureAI(new CrafterAI());
        assert CreatureTemplateFactory.getInstance().getTemplate(CrafterTemplate.getTemplateId()) != null;
        Zones.resetStatic();
        CrafterAI.assignedForges.clear();
    }

    public Creature createNewCrafter(Creature owner, CrafterType type, float skillCap) {
        try {
            final double finalSkillCap = Math.min(skillCap, 99.999999d);
            assert finalSkillCap < 100.0d;
            Creature crafter = CrafterAIData.createNewCrafter(owner, "Crafter" + (creatures.size() + 1), (byte)0, type, skillCap, 1.0f);
            creatures.put(crafter.getWurmId(), crafter);
            attachFakeCommunicator(crafter);
            type.getSkillsFor(crafter).forEach(skill -> skill.setKnowledge(finalSkillCap, false));

            return crafter;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public int getIsSmithingId() {
        return ItemList.hatchet;
    }

    public int getIsBlacksmithingId() {
        return ItemList.shovel;
    }

    public int getIsWeaponsmithingId() {
        return ItemList.swordLong;
    }

    public int getIsJewellerysmithingId() {
        return ItemList.statuetteMagranon;
    }
}
