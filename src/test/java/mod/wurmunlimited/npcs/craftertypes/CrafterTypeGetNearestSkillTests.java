package mod.wurmunlimited.npcs.craftertypes;

import com.wurmonline.server.items.Item;
import com.wurmonline.server.skills.SkillList;
import mod.wurmunlimited.npcs.CrafterTest;
import mod.wurmunlimited.npcs.CrafterType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CrafterTypeGetNearestSkillTests extends CrafterTest {
    @Test
    void testBattleYoyo() {
        Item item = TestItemTemplates.createBattleYoyo(factory);
        assertEquals(SkillList.CARPENTRY, CrafterType.getNearestSkill(item));
    }

    @Test
    void testWarhammer() {
        Item item = TestItemTemplates.createWarhammer(factory);
        assertEquals(SkillList.GROUP_SMITHING_WEAPONSMITHING, CrafterType.getNearestSkill(item));
    }
}
