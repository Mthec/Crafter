package mod.wurmunlimited.npcs.craftertypes;

import com.wurmonline.server.MiscConstants;
import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.SkillList;
import mod.wurmunlimited.WurmObjectsFactory;
import org.gotti.wurmunlimited.modsupport.ItemTemplateBuilder;

import java.io.IOException;

public class TestItemTemplates {
    private static int battleYoyoId = -1;
    private static int warhammerId = -1;

    // Source - https://github.com/Sindusk/wyvernmods/blob/master/src/main/java/mod/sin/weapons/BattleYoyo.java
    public static Item createBattleYoyo(WurmObjectsFactory factory) {
        if (battleYoyoId == -1) {
            ItemTemplateBuilder itemBuilder = new ItemTemplateBuilder("mod.item.battle.yoyo");
            itemBuilder.name("battle yoyo", "battle yoyos", "A reinforced yoyo meant for combat. Designed to see whether walking the dog is an effective murder technique.");
            itemBuilder.itemTypes(new short[]{ // new short[]{108, 44, 147, 22, 37, 14, 189} - Large Maul
                    ItemTypes.ITEM_TYPE_NAMED,
                    ItemTypes.ITEM_TYPE_REPAIRABLE,
                    ItemTypes.ITEM_TYPE_WOOD,
                    ItemTypes.ITEM_TYPE_WEAPON,
                    ItemTypes.ITEM_TYPE_WEAPON_CRUSH
            });
            itemBuilder.imageNumber((short) 761);
            itemBuilder.behaviourType((short) 35);
            itemBuilder.combatDamage(35);
            itemBuilder.decayTime(Long.MAX_VALUE);
            itemBuilder.dimensions(5, 10, 20);
            itemBuilder.primarySkill(SkillList.YOYO);
            itemBuilder.bodySpaces(MiscConstants.EMPTY_BYTE_PRIMITIVE_ARRAY);
            itemBuilder.modelName("model.toy.yoyo.");
            itemBuilder.difficulty(40.0f);
            itemBuilder.weightGrams(1000);
            itemBuilder.material(Materials.MATERIAL_WOOD_BIRCH);
            itemBuilder.value(1000);

            try {
                battleYoyoId = itemBuilder.build().getTemplateId();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            CreationEntryCreator.createSimpleEntry(SkillList.TOYMAKING, ItemList.clothString, ItemList.shaft,
                    battleYoyoId, false, true, 0.0f, false, false, CreationCategories.WEAPONS);
        }

        return factory.createNewItem(battleYoyoId);
    }

    // Source - https://github.com/Sindusk/wyvernmods/blob/master/src/main/java/mod/sin/weapons/Warhammer.java
    public static Item createWarhammer(WurmObjectsFactory factory) {
        if (warhammerId == -1) {
            ItemTemplateBuilder itemBuilder = new ItemTemplateBuilder("mod.item.warhammer");
            itemBuilder.name("warhammer", "warhammers", "A warhammer.");
            itemBuilder.itemTypes(new short[]{ // new short[]{108, 44, 147, 22, 37, 14, 189} - Large Maul
                    ItemTypes.ITEM_TYPE_NAMED,
                    ItemTypes.ITEM_TYPE_REPAIRABLE,
                    ItemTypes.ITEM_TYPE_METAL,
                    ItemTypes.ITEM_TYPE_WEAPON,
                    ItemTypes.ITEM_TYPE_WEAPON_CRUSH
            });
            itemBuilder.imageNumber((short)1339);
            itemBuilder.behaviourType((short)35);
            itemBuilder.combatDamage(40);
            itemBuilder.decayTime(Long.MAX_VALUE);
            itemBuilder.dimensions(5, 10, 80);
            itemBuilder.primarySkill(SkillList.WARHAMMER);
            itemBuilder.bodySpaces(MiscConstants.EMPTY_BYTE_PRIMITIVE_ARRAY);
            itemBuilder.modelName("model.artifact.hammerhuge.");
            itemBuilder.difficulty(35.0f);
            itemBuilder.weightGrams(7000);
            itemBuilder.material(Materials.MATERIAL_IRON);
            itemBuilder.value(1000);

            try {
                warhammerId = itemBuilder.build().getTemplateId();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            CreationEntryCreator.createSimpleEntry(SkillList.SMITHING_WEAPON_HEADS, ItemList.shaft, ItemList.hammerHeadMetal,
                    warhammerId, true, true, 0.0f, false, false, CreationCategories.WEAPONS);
        }

        return factory.createNewItem(warhammerId);
    }
}
