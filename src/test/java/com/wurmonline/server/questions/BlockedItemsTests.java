package com.wurmonline.server.questions;

import com.wurmonline.server.behaviours.BehaviourDispatcher;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.items.ItemTemplate;
import com.wurmonline.server.items.ItemTemplateFactory;
import com.wurmonline.server.items.NoSuchTemplateException;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.skills.SkillList;
import mod.wurmunlimited.CrafterObjectsFactory;
import mod.wurmunlimited.npcs.CrafterMod;
import mod.wurmunlimited.npcs.CrafterType;
import mod.wurmunlimited.npcs.GlobalRestrictionsFileWrapper;
import mod.wurmunlimited.npcs.WorkBook;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static mod.wurmunlimited.Assert.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class BlockedItemsTests extends GlobalRestrictionsFileWrapper {
    private CrafterObjectsFactory factory;
    private Player owner;
    private Creature crafter;

    @BeforeEach
    protected void setUp() throws Exception {
        super.setUp();
        Set<Integer> blockedItems = ReflectionUtil.getPrivateField(null, CrafterMod.class.getDeclaredField("blockedItems"));
        blockedItems.clear();
        factory = new CrafterObjectsFactory();
        BehaviourDispatcher.reset();
        owner = factory.createNewPlayer();
        crafter = factory.createNewCrafter(owner, new CrafterType(SkillList.COOKING_BAKING), 50);
    }

    @Test
    void testAddBlockedItemQuestionConfirm() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull, NoSuchTemplateException, NoSuchFieldException, IllegalAccessException {
        Properties properties = new Properties();
        properties.setProperty("add", "true");
        ItemTemplate template = ItemTemplateFactory.getInstance().getTemplate(ItemList.pickAxe);
        List<Integer> blocked = Arrays.asList(ItemList.ropeTool, ItemList.shovel);
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        workBook.updateBlockedItems(blocked);
        CrafterAddBlockedItemQuestion question = new CrafterAddBlockedItemQuestion(owner, crafter, workBook);
        CrafterEligibleTemplates items = ReflectionUtil.getPrivateField(question, CrafterAddBlockedItemQuestion.class.getDeclaredField("eligibleTemplates"));
        int idx = items.getIndexOf(template.getTemplateId());
        properties.setProperty("item", Integer.toString(idx));
        question.sendQuestion();
        question.answer(properties);

        assertEquals(3, workBook.getBlockedItems().size());
        assertTrue(workBook.getBlockedItems().contains(template.getTemplateId()));
    }

    @Test
    void testAddBlockedItemQuestionConfirmWithAlreadyBlockedItems() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull, NoSuchTemplateException, NoSuchFieldException, IllegalAccessException {
        Properties properties = new Properties();
        properties.setProperty("add", "true");
        ItemTemplate template = ItemTemplateFactory.getInstance().getTemplate(ItemList.pickAxe);
        List<Integer> blocked = Arrays.asList(ItemList.ropeTool, ItemList.shovel);
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        workBook.updateBlockedItems(blocked);
        CrafterAddBlockedItemQuestion question = new CrafterAddBlockedItemQuestion(owner, crafter, workBook);
        CrafterEligibleTemplates items = ReflectionUtil.getPrivateField(question, CrafterAddBlockedItemQuestion.class.getDeclaredField("eligibleTemplates"));
        int idx = items.getIndexOf(template.getTemplateId());
        properties.setProperty("item", Integer.toString(idx));
        question.sendQuestion();
        question.answer(properties);

        assertEquals(3, workBook.getBlockedItems().size());
        assertTrue(workBook.getBlockedItems().contains(template.getTemplateId()));
    }

    @Test
    void testAddBlockedItemQuestionCancelWithAlreadyBlockedItems() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull, NoSuchTemplateException, NoSuchFieldException, IllegalAccessException {
        Properties properties = new Properties();
        properties.setProperty("cancel", "true");
        ItemTemplate template = ItemTemplateFactory.getInstance().getTemplate(ItemList.pickAxe);
        List<Integer> blocked = Arrays.asList(ItemList.ropeTool, ItemList.shovel);
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        workBook.updateBlockedItems(blocked);
        CrafterAddBlockedItemQuestion question = new CrafterAddBlockedItemQuestion(owner, crafter, workBook);
        CrafterEligibleTemplates items = ReflectionUtil.getPrivateField(question, CrafterAddBlockedItemQuestion.class.getDeclaredField("eligibleTemplates"));
        int idx = items.getIndexOf(template.getTemplateId());
        properties.setProperty("item", Integer.toString(idx));
        question.sendQuestion();
        question.answer(properties);

        assertEquals(2, workBook.getBlockedItems().size());
        assertFalse(workBook.getBlockedItems().contains(template.getTemplateId()));
    }

    @Test
    void testDefaultListIncludesBlockedFromPreviousCrafter() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull, NoSuchTemplateException {
        ItemTemplate template = ItemTemplateFactory.getInstance().getTemplate(ItemList.pickAxe);
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        workBook.updateBlockedItems(Collections.singletonList(template.getTemplateId()));
        new CrafterAddBlockedItemQuestion(owner, crafter, workBook).sendQuestion();
        assertFalse(factory.getCommunicator(owner).lastBmlContent.contains(",pickaxe,"), factory.getCommunicator(owner).lastBmlContent);

        Creature crafter2 = factory.createNewCrafter(owner, new CrafterType(CrafterType.allMetal), 20f);
        WorkBook workBook2 = WorkBook.getWorkBookFromWorker(crafter2);
        new CrafterAddBlockedItemQuestion(owner, crafter2, workBook2).sendQuestion();
        assertTrue(factory.getCommunicator(owner).lastBmlContent.contains(",pickaxe,"), factory.getCommunicator(owner).lastBmlContent);
    }

    @Test
    void testRemoveItemsFromList() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull, NoSuchTemplateException {
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        workBook.updateBlockedItems(Arrays.asList(
                ItemList.ropeTool,
                ItemList.spindle,
                ItemList.shovel,
                ItemList.pickAxe,
                ItemList.hatchet
        ));

        Properties properties = new Properties();
        properties.setProperty("r3", "true");
        properties.setProperty("r4", "true");
        properties.setProperty("save", "true");
        new CrafterBlockedItemsQuestion(owner, crafter).answer(properties);

        assertEquals(3, workBook.getBlockedItems().size());
        assertTrue(workBook.getBlockedItems().containsAll(Arrays.asList(
                ItemList.hatchet,
                ItemList.pickAxe,
                ItemList.ropeTool
        )), workBook.getBlockedItems().toString());
        assertThat(owner, receivedMessageContaining("successfully"));
    }

    @Test
    void testCancelRemoveItemsFromList() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull, NoSuchTemplateException {
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        workBook.updateBlockedItems(Arrays.asList(
                ItemList.ropeTool,
                ItemList.spindle,
                ItemList.shovel,
                ItemList.pickAxe,
                ItemList.hatchet
        ));

        Properties properties = new Properties();
        properties.setProperty("r3", "true");
        properties.setProperty("r4", "true");
        properties.setProperty("cancel", "true");
        new CrafterBlockedItemsQuestion(owner, crafter).answer(properties);

        assertEquals(5, workBook.getBlockedItems().size());
        assertThat(owner, didNotReceiveMessageContaining("successfully"));
    }

    @Test
    void testCancelRemoveItemsFromListWhenAskingAddQuestion() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull, NoSuchTemplateException {
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        workBook.updateBlockedItems(Arrays.asList(
                ItemList.ropeTool,
                ItemList.spindle,
                ItemList.shovel,
                ItemList.pickAxe,
                ItemList.hatchet
        ));

        Properties properties = new Properties();
        properties.setProperty("r3", "true");
        properties.setProperty("r4", "true");
        properties.setProperty("add", "true");
        new CrafterBlockedItemsQuestion(owner, crafter).answer(properties);
        new CrafterAddBlockedItemQuestion(owner, crafter, workBook).sendQuestion();

        assertEquals(5, workBook.getBlockedItems().size());
        assertThat(owner, bmlEqual());
    }

    // Global

    @Test
    void testAddOptionsDontIncludeGloballyBlocked() throws NoSuchFieldException, IllegalAccessException, NoSuchTemplateException, WorkBook.NoWorkBookOnWorker {
        int shovel = ItemList.shovel;
        Set<Integer> blockedItems = ReflectionUtil.getPrivateField(null, CrafterMod.class.getDeclaredField("blockedItems"));
        blockedItems.addAll(Arrays.asList(ItemList.ropeTool, shovel));
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        new CrafterAddBlockedItemQuestion(owner, crafter, workBook).sendQuestion();
        assertFalse(factory.getCommunicator(owner).lastBmlContent.contains(",shovel,"));
        assertFalse(factory.getCommunicator(owner).lastBmlContent.contains(",rope tool,"), factory.getCommunicator(owner).lastBmlContent);

        blockedItems.remove(shovel);
        new CrafterAddBlockedItemQuestion(owner, crafter, workBook).sendQuestion();
        assertTrue(factory.getCommunicator(owner).lastBmlContent.contains(",shovel,"), factory.getCommunicator(owner).lastBmlContent);
        assertFalse(factory.getCommunicator(owner).lastBmlContent.contains(",rope tool,"));
    }

    @Test
    void testAddGloballyBlockedItemQuestionConfirm() throws NoSuchFieldException, IllegalAccessException, NoSuchTemplateException {
        Properties properties = new Properties();
        properties.setProperty("add", "true");
        ItemTemplate template = ItemTemplateFactory.getInstance().getTemplate(ItemList.pickAxe);
        List<Integer> blocked = Arrays.asList(ItemList.ropeTool, ItemList.shovel);
        Set<Integer> blockedItems = ReflectionUtil.getPrivateField(null, CrafterMod.class.getDeclaredField("blockedItems"));
        blockedItems.addAll(blocked);
        CrafterAddBlockedItemQuestion question = new CrafterAddBlockedItemQuestion(owner, null, null);
        CrafterEligibleTemplates items = ReflectionUtil.getPrivateField(question, CrafterAddBlockedItemQuestion.class.getDeclaredField("eligibleTemplates"));
        int idx = items.getIndexOf(template.getTemplateId());
        properties.setProperty("item", Integer.toString(idx));
        question.sendQuestion();
        question.answer(properties);

        assertEquals(3, CrafterMod.blockedItems.size());
        assertTrue(CrafterMod.blockedItems.contains(template.getTemplateId()));
    }

    @Test
    void testAddGloballyBlockedItemQuestionConfirmWithAlreadyBlockedItems() throws NoSuchFieldException, IllegalAccessException, NoSuchTemplateException {
        Properties properties = new Properties();
        properties.setProperty("add", "true");
        ItemTemplate template = ItemTemplateFactory.getInstance().getTemplate(ItemList.pickAxe);
        List<Integer> blocked = Arrays.asList(ItemList.ropeTool, ItemList.shovel);
        Set<Integer> blockedItems = ReflectionUtil.getPrivateField(null, CrafterMod.class.getDeclaredField("blockedItems"));
        blockedItems.addAll(blocked);
        CrafterAddBlockedItemQuestion question = new CrafterAddBlockedItemQuestion(owner, null, null);
        CrafterEligibleTemplates items = ReflectionUtil.getPrivateField(question, CrafterAddBlockedItemQuestion.class.getDeclaredField("eligibleTemplates"));
        int idx = items.getIndexOf(template.getTemplateId());
        properties.setProperty("item", Integer.toString(idx));
        question.sendQuestion();
        question.answer(properties);

        assertEquals(3, CrafterMod.blockedItems.size());
        assertTrue(CrafterMod.blockedItems.contains(template.getTemplateId()));
    }

    @Test
    void testAddGloballyBlockedItemQuestionCancelWithAlreadyBlockedItems() throws NoSuchFieldException, IllegalAccessException, NoSuchTemplateException {
        Properties properties = new Properties();
        properties.setProperty("cancel", "true");
        ItemTemplate template = ItemTemplateFactory.getInstance().getTemplate(ItemList.pickAxe);
        List<Integer> blocked = Arrays.asList(ItemList.ropeTool, ItemList.shovel);
        Set<Integer> blockedItems = ReflectionUtil.getPrivateField(null, CrafterMod.class.getDeclaredField("blockedItems"));
        blockedItems.addAll(blocked);
        CrafterAddBlockedItemQuestion question = new CrafterAddBlockedItemQuestion(owner, null, null);
        CrafterEligibleTemplates items = ReflectionUtil.getPrivateField(question, CrafterAddBlockedItemQuestion.class.getDeclaredField("eligibleTemplates"));
        int idx = items.getIndexOf(template.getTemplateId());
        properties.setProperty("item", Integer.toString(idx));
        question.sendQuestion();
        question.answer(properties);

        assertEquals(2, CrafterMod.blockedItems.size());
        assertFalse(CrafterMod.blockedItems.contains(template.getTemplateId()));
    }

    @Test
    void testRemoveGloballyBlockedItemsFromList() throws NoSuchFieldException, IllegalAccessException, NoSuchTemplateException, WorkBook.NoWorkBookOnWorker {
        Set<Integer> blockedItems = ReflectionUtil.getPrivateField(null, CrafterMod.class.getDeclaredField("blockedItems"));
        blockedItems.addAll(Arrays.asList(
                ItemList.ropeTool,
                ItemList.spindle,
                ItemList.shovel,
                ItemList.pickAxe,
                ItemList.hatchet
        ));

        Properties properties = new Properties();
        properties.setProperty("r3", "true");
        properties.setProperty("r4", "true");
        properties.setProperty("save", "true");
        new CrafterBlockedItemsQuestion(owner, null).answer(properties);

        assertEquals(3, CrafterMod.blockedItems.size());
        assertTrue(CrafterMod.blockedItems.containsAll(Arrays.asList(
                ItemList.hatchet,
                ItemList.pickAxe,
                ItemList.ropeTool
        )), CrafterMod.blockedItems.toString());
        assertThat(owner, receivedMessageContaining("successfully"));
    }

    @Test
    void testConfigureCorrectlyLoadsGlobalBlocks() throws IOException {
        File f = temp.newFile("mods/crafter/blocked_items");
        try (DataOutputStream ds = new DataOutputStream(Files.newOutputStream(f.toPath()))) {
            ds.writeInt(ItemList.ropeTool);
            ds.writeInt(ItemList.shovel);
        }
        new CrafterMod().configure(new Properties());

        assertTrue(CrafterMod.blockedItems.contains(ItemList.ropeTool));
        assertTrue(CrafterMod.blockedItems.contains(ItemList.shovel));
        assertEquals(2, CrafterMod.blockedItems.size());
    }

    @Test
    void testSavingGlobalBlocks() throws IOException {
        CrafterMod.saveBlockedItems(Arrays.asList(ItemList.ropeTool, ItemList.shovel));

        assertTrue(CrafterMod.blockedItems.contains(ItemList.ropeTool));
        assertTrue(CrafterMod.blockedItems.contains(ItemList.shovel));
        assertEquals(2, CrafterMod.blockedItems.size());
        try (DataInputStream di = new DataInputStream(Files.newInputStream(Paths.get(temp.getRoot().getAbsolutePath(), "mods", "crafter", "blocked_items")))) {
            int one = di.readInt();
            int two = di.readInt();
            if (one == ItemList.ropeTool) {
                assertEquals(ItemList.shovel, two, one + "-" + two);
            } else {
                assertEquals(ItemList.ropeTool, two, one + "-" + two);
                assertEquals(ItemList.shovel, one, one + "-" + two);
            }

            assertThrows(EOFException.class, di::readInt);
        }
    }

    @Test
    void testRemoveColumnIsServerOnGlobalAndCheckboxOnCrafter() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        CrafterMod.blockedItems.add(ItemList.pickAxe);
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        workBook.updateBlockedItems(Collections.singletonList(ItemList.shovel));

        new CrafterBlockedItemsQuestion(owner, crafter).sendQuestion();

        assertThat(owner, receivedBMLContaining("pickaxe\"};label{text=\"Server\""));
        assertThat(owner, receivedBMLContaining("shovel\"};checkbox{id=\"r1\""));
    }

    @Test
    void testRemoveColumnIsNotServerForGM() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        CrafterMod.blockedItems.add(ItemList.pickAxe);

        new CrafterBlockedItemsQuestion(owner, null).sendQuestion();

        assertThat(owner, receivedBMLContaining("pickaxe\"};checkbox{id=\"r0\""));
    }
}
