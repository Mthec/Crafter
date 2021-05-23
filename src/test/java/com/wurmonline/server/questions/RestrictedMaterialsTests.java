package com.wurmonline.server.questions;

import com.wurmonline.server.behaviours.BehaviourDispatcher;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.shared.constants.ItemMaterials;
import com.wurmonline.shared.util.MaterialUtilities;
import mod.wurmunlimited.CrafterObjectsFactory;
import mod.wurmunlimited.npcs.CrafterMod;
import mod.wurmunlimited.npcs.CrafterType;
import mod.wurmunlimited.npcs.GlobalRestrictionsFileWrapper;
import mod.wurmunlimited.npcs.WorkBook;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.*;

import static mod.wurmunlimited.Assert.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class RestrictedMaterialsTests extends GlobalRestrictionsFileWrapper {
    private CrafterObjectsFactory factory;
    private Player owner;
    private Creature crafter;

    @BeforeEach
    protected void setUp() throws Exception {
        super.setUp();
        factory = new CrafterObjectsFactory();
        BehaviourDispatcher.reset();
        owner = factory.createNewPlayer();
        crafter = factory.createNewCrafter(owner, new CrafterType(SkillList.COOKING_BAKING), 50);
    }

    private List<Byte> getSortedMaterials(@Nullable List<Byte> alreadyRestricted) {
        if (alreadyRestricted == null)
            alreadyRestricted = new ArrayList<>();
        List<Byte> materials = new ArrayList<>();
        for (int x = 1; x <= ItemMaterials.MATERIAL_MAX; ++x) {
            byte y = (byte)x;
            if (!alreadyRestricted.contains(y)) {
                String str = MaterialUtilities.getMaterialString(y);
                if (!str.equals("unknown") && (
                        MaterialUtilities.isMetal(y) ||
                                MaterialUtilities.isWood(y) ||
                                MaterialUtilities.isLeather(y) ||
                                MaterialUtilities.isCloth(y) ||
                                MaterialUtilities.isStone(y) ||
                                MaterialUtilities.isClay(y))) {
                    materials.add(y);
                }
            }
        }
        materials.sort(Comparator.comparing(MaterialUtilities::getMaterialString));

        return materials;
    }

    @Test
    void testAddRestrictedMaterialQuestionConfirm() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        Properties properties = new Properties();
        properties.setProperty("add", "true");
        byte material = ItemMaterials.MATERIAL_GOLD;
        List<Byte> restricted = Arrays.asList(ItemMaterials.MATERIAL_IRON, ItemMaterials.MATERIAL_BRONZE);
        List<Byte> materials = getSortedMaterials(restricted);
        int idx = materials.indexOf(material);
        properties.setProperty("mat", Integer.toString(idx));
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        workBook.updateRestrictedMaterials(restricted);
        Question question = new CrafterAddRestrictedMaterialQuestion(owner, crafter, workBook);
        question.sendQuestion();
        question.answer(properties);

        assertEquals(3, workBook.getRestrictedMaterials().size());
        assertTrue(workBook.getRestrictedMaterials().contains(material));
    }

    @Test
    void testAddRestrictedMaterialQuestionConfirmWithAlreadyRestrictedMaterials() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        Properties properties = new Properties();
        properties.setProperty("add", "true");
        byte material = ItemMaterials.MATERIAL_GOLD;
        List<Byte> restricted = Arrays.asList(ItemMaterials.MATERIAL_IRON, ItemMaterials.MATERIAL_BRONZE);
        List<Byte> materials = getSortedMaterials(restricted);
        int idx = materials.indexOf(material);
        properties.setProperty("mat", Integer.toString(idx));
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        workBook.updateRestrictedMaterials(restricted);
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
        List<Byte> restricted = Arrays.asList(ItemMaterials.MATERIAL_IRON, ItemMaterials.MATERIAL_BRONZE);
        List<Byte> materials = getSortedMaterials(restricted);
        int idx = materials.indexOf(material);
        properties.setProperty("mat", Integer.toString(idx));
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        workBook.updateRestrictedMaterials(restricted);
        Question question = new CrafterAddRestrictedMaterialQuestion(owner, crafter, workBook);
        question.sendQuestion();
        question.answer(properties);

        assertEquals(2, workBook.getRestrictedMaterials().size());
        assertFalse(workBook.getRestrictedMaterials().contains(material));
    }

    @Test
    void testDefaultListIncludesRestrictedFromPreviousCrafter() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        byte material = ItemMaterials.MATERIAL_GOLD;
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        workBook.updateRestrictedMaterials(Collections.singletonList(material));
        new CrafterAddRestrictedMaterialQuestion(owner, crafter, workBook).sendQuestion();
        assertFalse(factory.getCommunicator(owner).lastBmlContent.contains("gold"));

        Creature crafter2 = factory.createNewCrafter(owner, new CrafterType(CrafterType.allMetal), 20f);
        WorkBook workBook2 = WorkBook.getWorkBookFromWorker(crafter2);
        new CrafterAddRestrictedMaterialQuestion(owner, crafter2, workBook2).sendQuestion();
        assertTrue(factory.getCommunicator(owner).lastBmlContent.contains("gold"));
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

    // Global

    @Test
    void testAddOptionsOnlyIncludeGloballyAllowed() throws NoSuchFieldException, IllegalAccessException, WorkBook.NoWorkBookOnWorker {
        byte gold = ItemMaterials.MATERIAL_GOLD;
        List<Byte> restrictedMaterials = ReflectionUtil.getPrivateField(null, CrafterMod.class.getDeclaredField("restrictedMaterials"));
        restrictedMaterials.addAll(Arrays.asList(ItemMaterials.MATERIAL_BRONZE, gold));
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        new CrafterAddRestrictedMaterialQuestion(owner, crafter, workBook).sendQuestion();
        assertTrue(factory.getCommunicator(owner).lastBmlContent.contains("gold"));
        assertTrue(factory.getCommunicator(owner).lastBmlContent.contains("bronze"));

        restrictedMaterials.remove((Byte)gold);
        new CrafterAddRestrictedMaterialQuestion(owner, crafter, workBook).sendQuestion();
        assertFalse(factory.getCommunicator(owner).lastBmlContent.contains("gold"), factory.getCommunicator(owner).lastBmlContent);
        assertTrue(factory.getCommunicator(owner).lastBmlContent.contains("bronze"));
    }

    @Test
    void testAddGloballyRestrictedMaterialQuestionConfirm() throws NoSuchFieldException, IllegalAccessException {
        Properties properties = new Properties();
        properties.setProperty("add", "true");
        byte material = ItemMaterials.MATERIAL_GOLD;
        List<Byte> restricted = Arrays.asList(ItemMaterials.MATERIAL_IRON, ItemMaterials.MATERIAL_BRONZE);
        List<Byte> restrictedMaterials = ReflectionUtil.getPrivateField(null, CrafterMod.class.getDeclaredField("restrictedMaterials"));
        restrictedMaterials.addAll(restricted);
        List<Byte> materials = getSortedMaterials(restricted);
        int idx = materials.indexOf(material);
        properties.setProperty("mat", Integer.toString(idx));
        Question question = new CrafterAddRestrictedMaterialQuestion(owner, null, null);
        question.sendQuestion();
        question.answer(properties);

        assertEquals(3, CrafterMod.getRestrictedMaterials().size());
        assertTrue(CrafterMod.getRestrictedMaterials().contains(material));
    }

    @Test
    void testAddGloballyRestrictedMaterialQuestionConfirmWithAlreadyRestrictedMaterials() throws NoSuchFieldException, IllegalAccessException {
        Properties properties = new Properties();
        properties.setProperty("add", "true");
        byte material = ItemMaterials.MATERIAL_GOLD;
        List<Byte> restricted = Arrays.asList(ItemMaterials.MATERIAL_IRON, ItemMaterials.MATERIAL_BRONZE);
        List<Byte> restrictedMaterials = ReflectionUtil.getPrivateField(null, CrafterMod.class.getDeclaredField("restrictedMaterials"));
        restrictedMaterials.addAll(restricted);
        List<Byte> materials = getSortedMaterials(restricted);
        int idx = materials.indexOf(material);
        properties.setProperty("mat", Integer.toString(idx));
        Question question = new CrafterAddRestrictedMaterialQuestion(owner, null, null);
        question.sendQuestion();
        question.answer(properties);

        assertEquals(3, CrafterMod.getRestrictedMaterials().size());
        assertTrue(CrafterMod.getRestrictedMaterials().contains(material));
    }

    @Test
    void testAddGloballyRestrictedMaterialQuestionCancelWithAlreadyRestrictedMaterials() throws NoSuchFieldException, IllegalAccessException {
        Properties properties = new Properties();
        properties.setProperty("cancel", "true");
        byte material = ItemMaterials.MATERIAL_GOLD;
        List<Byte> restricted = Arrays.asList(ItemMaterials.MATERIAL_IRON, ItemMaterials.MATERIAL_BRONZE);
        List<Byte> restrictedMaterials = ReflectionUtil.getPrivateField(null, CrafterMod.class.getDeclaredField("restrictedMaterials"));
        restrictedMaterials.addAll(restricted);
        List<Byte> materials = getSortedMaterials(restricted);
        int idx = materials.indexOf(material);
        properties.setProperty("mat", Integer.toString(idx));
        Question question = new CrafterAddRestrictedMaterialQuestion(owner, null, null);
        question.sendQuestion();
        question.answer(properties);

        assertEquals(2, CrafterMod.getRestrictedMaterials().size());
        assertFalse(CrafterMod.getRestrictedMaterials().contains(material));
    }

    @Test
    void testRemoveGloballyRestrictedMaterialsFromList() throws NoSuchFieldException, IllegalAccessException, WorkBook.NoWorkBookOnWorker {
        List<Byte> restrictedMaterials = ReflectionUtil.getPrivateField(null, CrafterMod.class.getDeclaredField("restrictedMaterials"));
        restrictedMaterials.addAll(Arrays.asList(
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
        new CrafterMaterialRestrictionQuestion(owner, null).answer(properties);

        assertEquals(3, CrafterMod.getRestrictedMaterials().size());
        assertTrue(CrafterMod.getRestrictedMaterials().containsAll(Arrays.asList(
                ItemMaterials.MATERIAL_IRON,
                ItemMaterials.MATERIAL_GOLD,
                ItemMaterials.MATERIAL_BRONZE
        )));
        assertThat(owner, receivedMessageContaining("successfully"));
    }

    @Test
    void testConfigureCorrectlyLoadsGlobalRestrictions() throws IOException {
        File f = temp.newFile("mods/crafter/global_restrictions");
        Files.write(f.toPath(), new byte[] { ItemMaterials.MATERIAL_BRONZE, ItemMaterials.MATERIAL_GOLD }, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        new CrafterMod().configure(new Properties());

        assertFalse(CrafterMod.isGloballyRestrictedMaterial(ItemMaterials.MATERIAL_BRONZE));
        assertFalse(CrafterMod.isGloballyRestrictedMaterial(ItemMaterials.MATERIAL_GOLD));
        assertEquals(2, CrafterMod.getRestrictedMaterials().size());
    }

    @Test
    void testSavingGlobalRestrictions() throws IOException {
        CrafterMod.saveRestrictedMaterials(Arrays.asList(ItemMaterials.MATERIAL_BRONZE, ItemMaterials.MATERIAL_GOLD));

        assertFalse(CrafterMod.isGloballyRestrictedMaterial(ItemMaterials.MATERIAL_BRONZE));
        assertFalse(CrafterMod.isGloballyRestrictedMaterial(ItemMaterials.MATERIAL_GOLD));
        assertEquals(2, CrafterMod.getRestrictedMaterials().size());
        byte[] materials = new byte[2];
        materials[0] = CrafterMod.getRestrictedMaterials().get(0);
        materials[1] = CrafterMod.getRestrictedMaterials().get(1);
        assertArrayEquals(materials, Files.readAllBytes(CrafterMod.globalRestrictionsPath));
    }
}
