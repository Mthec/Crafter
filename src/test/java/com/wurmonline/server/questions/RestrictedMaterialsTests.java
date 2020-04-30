package com.wurmonline.server.questions;

import com.wurmonline.server.behaviours.BehaviourDispatcher;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.shared.constants.ItemMaterials;
import com.wurmonline.shared.util.MaterialUtilities;
import mod.wurmunlimited.CrafterObjectsFactory;
import mod.wurmunlimited.npcs.CrafterType;
import mod.wurmunlimited.npcs.WorkBook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Properties;

import static mod.wurmunlimited.Assert.*;
import static org.junit.Assert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class RestrictedMaterialsTests {
    private CrafterObjectsFactory factory;
    private Player owner;
    private Creature crafter;

    @BeforeEach
    void setUp() throws Exception {
        factory = new CrafterObjectsFactory();
        BehaviourDispatcher.reset();
        owner = factory.createNewPlayer();
        crafter = factory.createNewCrafter(owner, new CrafterType(SkillList.COOKING_BAKING), 50);
    }

    @Test
    void testAddRestrictedMaterialQuestionConfirm() throws WorkBook.NoWorkBookOnWorker {
        Properties properties = new Properties();
        properties.setProperty("add", "true");
        byte material = ItemMaterials.MATERIAL_GOLD;
        int idx = 0;
        for (int x = 1; x <= ItemMaterials.MATERIAL_MAX; ++x) {
            byte y = (byte)x;
            String str = MaterialUtilities.getMaterialString(y);
            if (!str.equals("unknown") && (
                    MaterialUtilities.isMetal(y) ||
                            MaterialUtilities.isWood(y) ||
                            MaterialUtilities.isLeather(y) ||
                            MaterialUtilities.isCloth(y) ||
                            MaterialUtilities.isStone(y) ||
                            MaterialUtilities.isClay(y))) {
                if (x == material) {
                    break;
                }

                ++idx;
            }
        }
        properties.setProperty("mat", Integer.toString(idx));
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        Question question = new CrafterAddRestrictedMaterialQuestion(owner, crafter, workBook);
        question.sendQuestion();
        question.answer(properties);

        assertEquals(1, workBook.getRestrictedMaterials().size());
        assertEquals(material, (byte)workBook.getRestrictedMaterials().get(0));
    }

    @Test
    void testAddRestrictedMaterialQuestionConfirmWithAlreadyRestrictedMaterials() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        Properties properties = new Properties();
        properties.setProperty("add", "true");
        byte material = ItemMaterials.MATERIAL_GOLD;
        int idx = 0;
        for (int x = 1; x <= ItemMaterials.MATERIAL_MAX; ++x) {
            byte y = (byte)x;
            String str = MaterialUtilities.getMaterialString(y);
            if (!str.equals("unknown") && (
                    MaterialUtilities.isMetal(y) ||
                            MaterialUtilities.isWood(y) ||
                            MaterialUtilities.isLeather(y) ||
                            MaterialUtilities.isCloth(y) ||
                            MaterialUtilities.isStone(y) ||
                            MaterialUtilities.isClay(y))) {
                if (x == material) {
                    break;
                }

                ++idx;
            }
        }
        properties.setProperty("mat", Integer.toString(idx));
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        workBook.updateRestrictedMaterials(Arrays.asList(ItemMaterials.MATERIAL_IRON, ItemMaterials.MATERIAL_BRONZE));
        Question question = new CrafterAddRestrictedMaterialQuestion(owner, crafter, workBook);
        question.sendQuestion();
        question.answer(properties);

        assertEquals(3, workBook.getRestrictedMaterials().size());
        assertTrue(workBook.getRestrictedMaterials().contains(material));
    }

    @Test
    void testAddRestrictedMaterialQuestionCancelWithAlreadyRestrictedMaterials() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        Properties properties = new Properties();
        properties.setProperty("cancel", "true");
        byte material = ItemMaterials.MATERIAL_GOLD;
        int idx = 0;
        for (int x = 1; x <= ItemMaterials.MATERIAL_MAX; ++x) {
            byte y = (byte)x;
            String str = MaterialUtilities.getMaterialString(y);
            if (!str.equals("unknown") && (
                    MaterialUtilities.isMetal(y) ||
                            MaterialUtilities.isWood(y) ||
                            MaterialUtilities.isLeather(y) ||
                            MaterialUtilities.isCloth(y) ||
                            MaterialUtilities.isStone(y) ||
                            MaterialUtilities.isClay(y))) {
                if (x == material) {
                    break;
                }

                ++idx;
            }
        }
        properties.setProperty("mat", Integer.toString(idx));
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        workBook.updateRestrictedMaterials(Arrays.asList(ItemMaterials.MATERIAL_IRON, ItemMaterials.MATERIAL_BRONZE));
        Question question = new CrafterAddRestrictedMaterialQuestion(owner, crafter, workBook);
        question.sendQuestion();
        question.answer(properties);

        assertEquals(2, workBook.getRestrictedMaterials().size());
        assertFalse(workBook.getRestrictedMaterials().contains(material));
    }

    @Test
    void testRemoveMaterialsFromList() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        workBook.updateRestrictedMaterials(Arrays.asList(
                ItemMaterials.MATERIAL_IRON,
                ItemMaterials.MATERIAL_GOLD,
                ItemMaterials.MATERIAL_BRONZE,
                ItemMaterials.MATERIAL_SERYLL,
                ItemMaterials.MATERIAL_ADAMANTINE
        ));

        Properties properties = new Properties();
        properties.setProperty("r3", "true");
        properties.setProperty("r4", "true");
        properties.setProperty("save", "true");
        new CrafterMaterialRestrictionQuestion(owner, crafter).answer(properties);

        assertEquals(3, workBook.getRestrictedMaterials().size());
        assertTrue(workBook.getRestrictedMaterials().containsAll(Arrays.asList(
                ItemMaterials.MATERIAL_IRON,
                ItemMaterials.MATERIAL_GOLD,
                ItemMaterials.MATERIAL_BRONZE
        )));
        assertThat(owner, receivedMessageContaining("successfully"));
    }

    @Test
    void testCancelRemoveMaterialsFromList() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        workBook.updateRestrictedMaterials(Arrays.asList(
                ItemMaterials.MATERIAL_IRON,
                ItemMaterials.MATERIAL_GOLD,
                ItemMaterials.MATERIAL_BRONZE,
                ItemMaterials.MATERIAL_SERYLL,
                ItemMaterials.MATERIAL_ADAMANTINE
        ));

        Properties properties = new Properties();
        properties.setProperty("r3", "true");
        properties.setProperty("r4", "true");
        properties.setProperty("cancel", "true");
        new CrafterMaterialRestrictionQuestion(owner, crafter).answer(properties);

        assertEquals(5, workBook.getRestrictedMaterials().size());
        assertThat(owner, didNotReceiveMessageContaining("successfully"));
    }

    @Test
    void testCancelRemoveMaterialsFromListWhenAskingAddQuestion() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        workBook.updateRestrictedMaterials(Arrays.asList(
                ItemMaterials.MATERIAL_IRON,
                ItemMaterials.MATERIAL_GOLD,
                ItemMaterials.MATERIAL_BRONZE,
                ItemMaterials.MATERIAL_SERYLL,
                ItemMaterials.MATERIAL_ADAMANTINE
        ));

        Properties properties = new Properties();
        properties.setProperty("r3", "true");
        properties.setProperty("r4", "true");
        properties.setProperty("add", "true");
        new CrafterMaterialRestrictionQuestion(owner, crafter).answer(properties);
        new CrafterAddRestrictedMaterialQuestion(owner, crafter, workBook).sendQuestion();

        assertEquals(5, workBook.getRestrictedMaterials().size());
        assertThat(owner, bmlEqual());
    }
}
