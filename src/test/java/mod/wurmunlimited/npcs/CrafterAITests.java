package mod.wurmunlimited.npcs;

import com.wurmonline.server.FailedException;
import com.wurmonline.server.Items;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.behaviours.BehaviourDispatcher;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.NoSuchSkillException;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.shared.constants.ItemMaterials;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.internal.util.reflection.FieldSetter;

import java.util.*;

import static mod.wurmunlimited.Assert.didNotReceiveMessageContaining;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CrafterAITests extends CrafterTest {

    private CrafterAIData data;
    private WorkBook workBook;
    private Job job;
    private Item lump;
    private Item hammer;
    private Item pelt;
    private Item water;
    private Item whetstone;

    @Override
    @BeforeEach
    void setUp() throws Exception {
        super.setUp();
        data = (CrafterAIData)crafter.getCreatureAIData();
        workBook = data.getWorkBook();
        workBook.addJob(player.getWurmId(), tool, 10, false, 100);
        job = workBook.iterator().next();
        lump = data.createMissingItem(ItemList.ironBar);
        hammer = data.createMissingItem(ItemList.hammerMetal);
        pelt = data.createMissingItem(ItemList.pelt);
        water = data.createMissingItem(ItemList.water);
        whetstone = data.createMissingItem(ItemList.whetStone);

        assert workBook != null && lump != null && hammer != null && pelt != null && water != null && whetstone != null;
    }
    
    private void warmUp() {
        forge.setTemperature((short)100);
        lump.setTemperature((CrafterAIData.targetTemperature));
        forge.insertItem(lump);
        tool.setTemperature(CrafterAIData.targetTemperature);
        forge.insertItem(tool);
    }

    @Test
    void testJobDone() throws NoSuchFieldException, IllegalAccessException {
        crafter.getInventory().insertItem(tool);
        tool.setQualityLevel(11);
        long price = 111L;
        ReflectionUtil.setPrivateField(job, Job.class.getDeclaredField("targetQL"), 10);
        ReflectionUtil.setPrivateField(job, Job.class.getDeclaredField("priceCharged"), price);

        data.sendNextAction();
        assertEquals(0, workBook.todo());
        assertEquals(1, workBook.done());
        assertEquals((long)(price * 0.9f), crafter.getShop().getMoney());
    }

    @Test
    void testRemoveFromForgeWhenJobDone() throws NoSuchFieldException, IllegalAccessException {
        forge.insertItem(tool);
        tool.setQualityLevel(11);
        ReflectionUtil.setPrivateField(job, Job.class.getDeclaredField("targetQL"), 10);

        data.sendNextAction();
        assertTrue(crafter.getInventory().getItems().contains(tool));
        assertFalse(forge.getItems().contains(tool));
    }

    @Test
    void testItemRepairedIfDamaged() {
        tool.setDamage(50);

        data.sendNextAction();
        assertTrue(BehaviourDispatcher.wasDispatched(tool, Actions.REPAIR));
    }

    // Metal Items

    @Test
    void testForgeLitWhenStartingWork() {
        assert tool.isMetal();
        forge.setTemperature((short)0);

        data.sendNextAction();
        assertTrue(forge.getTemperature() >= CrafterAIData.targetTemperature);
    }

    @Test
    void testLumpPutIntoForge() {
        assert tool.isMetal();

        data.sendNextAction();
        assertTrue(forge.getItems().contains(lump));
    }

    @Test
    void testLumpPutIntoForgeWhenNotCreated() {
        assert tool.isMetal();
        assert !forge.getItems().contains(lump);
        Items.destroyItem(lump.getWurmId());
        lump = null;

        data.sendNextAction();
        assertTrue(forge.getItems().stream().anyMatch(i -> i.getTemplateId() == ItemList.ironBar));
    }

    @Test
    void testToolPutIntoForge() {
        assert tool.isMetal();

        data.sendNextAction();
        assertTrue(forge.getItems().contains(tool));
    }

    @Test
    void testToolNotHotEnough() {
        assert tool.isMetal();

        for (short i = 0; i < CrafterAIData.targetTemperature; i++) {
            tool.setTemperature(i);
            data.sendNextAction();
            assertTrue(BehaviourDispatcher.nothingDispatched());
        }
    }

    @Test
    void testLumpTakenFromForgeWhenItAndToolAreGlowing() {
        assert tool.isMetal();
        warmUp();

        data.sendNextAction();
        assertTrue(crafter.getInventory().getItems().contains(lump));
    }

    @Test
    void testLumpNotTakenFromForgeIfItIsNotGlowing() {
        assert tool.isMetal();
        tool.setTemperature(CrafterAIData.targetTemperature);
        lump.setTemperature((short)(CrafterAIData.targetTemperature - 500));

        data.sendNextAction();
        assertTrue(forge.getItems().contains(lump));
        assertTrue(BehaviourDispatcher.nothingDispatched());
    }

    @Test
    void testCrafterToolsRepairedBeforeUse() {
        warmUp();
        lump.setDamage(50);

        data.sendNextAction();
        assertEquals(0, BehaviourDispatcher.getLastDispatchSubject().getDamage());
    }

    @Test
    void testLumpAndWaterWeightRestoredBeforeUse() {
        warmUp();
        lump.setWeight(1, false);
        assert lump.getTemplate().getWeightGrams() > 1;
        water.setWeight(1, false);
        assert water.getTemplate().getWeightGrams() > 1;

        data.sendNextAction();
        assertEquals(lump.getTemplate().getWeightGrams(), BehaviourDispatcher.getLastDispatchSubject().getWeightGrams());
        data.sendNextAction();
        assertEquals(water.getTemplate().getWeightGrams(), BehaviourDispatcher.getLastDispatchSubject().getWeightGrams());
    }

    @Test
    void testSpecialLumpWeightRestoredBeforeUse() throws NoSuchTemplateException, FailedException {
        lump = data.createMissingItem(ItemList.bronzeBar);
        tool.setMaterial(ItemMaterials.MATERIAL_BRONZE);
        warmUp();

        data.sendNextAction();
        assertEquals(1000, BehaviourDispatcher.getLastDispatchSubject().getWeightGrams());
        data.sendNextAction();
        assertEquals(1000, BehaviourDispatcher.getLastDispatchSubject().getWeightGrams());
    }

    @Test
    void testSkillsCappedBeforeUse() {
        warmUp();
        crafter.getSkills().getSkills()[0].setKnowledge(workBook.getSkillCap() + 10, false);

        data.sendNextAction();
        assertTrue(crafter.getSkills().getSkills()[0].getKnowledge() < workBook.getSkillCap() + 1);
    }

    @Test
    void testImproveActionDispatched() {
        warmUp();

        data.sendNextAction();
        assertTrue(BehaviourDispatcher.wasDispatched(tool, Actions.IMPROVE));
    }

    @Test
    void testCorrectToolActionDispatched() {
        warmUp();
        Map<Byte, Item> tools = new HashMap<>();
        tools.put((byte)-10, lump);
        tools.put((byte)2, hammer);
        tools.put((byte)4, pelt);
        tools.put((byte)3, water);
        tools.put((byte)1, whetstone);

        for (byte item : tools.keySet()) {
            tool.creationState = item;
            data.sendNextAction();
            assertEquals(tools.get(item), BehaviourDispatcher.getLastDispatchSubject());
        }
    }

    @Test
    void testGetSkillsFor() throws NoSuchSkillException, WorkBook.NoWorkBookOnWorker {
        Creature crafter = factory.createNewCrafter(factory.createNewPlayer(), new CrafterType(CrafterType.allMetal), 50);
        double knowledge = 95.0d;
        crafter.getSkills().getSkill(SkillList.SMITHING_GOLDSMITHING).setKnowledge(knowledge, true);
        Set<Integer> skills = new HashSet<>();
        WorkBook.getWorkBookFromWorker(crafter).getCrafterType().getSkillsFor(crafter).forEach(s -> skills.add(s.getNumber()));

        assertEquals(6, skills.size());
        assertTrue(skills.contains(SkillList.SMITHING_BLACKSMITHING));
        assertTrue(skills.contains(SkillList.SMITHING_GOLDSMITHING));
        assertTrue(skills.contains(SkillList.GROUP_SMITHING_WEAPONSMITHING));
        assertTrue(skills.contains(SkillList.SMITHING_ARMOUR_CHAIN));
        assertTrue(skills.contains(SkillList.SMITHING_ARMOUR_PLATE));
        assertTrue(skills.contains(SkillList.SMITHING_SHIELDS));

        Skill goldSmithing = null;
        for (Skill skill : WorkBook.getWorkBookFromWorker(crafter).getCrafterType().getSkillsFor(crafter))
            if (skill.getNumber() == SkillList.SMITHING_GOLDSMITHING)
                goldSmithing = skill;
        assertNotNull(goldSmithing);
        assertEquals(knowledge, goldSmithing.getKnowledge());
    }

    @Test
    void testInventoryProperlyAssigned() {
        warmUp();
        crafter.getCreatureAIData().setCreature(crafter);

        Map<Integer, Byte> tools = new HashMap<>();
        tools.put(ItemList.ironBar, (byte)-10);
        tools.put(ItemList.hammerMetal, (byte)2);
        tools.put(ItemList.pelt, (byte)4);
        tools.put(ItemList.water, (byte)3);
        tools.put(ItemList.whetStone, (byte)1);

        for (Map.Entry<Integer, Byte> item : tools.entrySet()) {
            tool.creationState = item.getValue();
            data.sendNextAction();
            assertEquals((int)item.getKey(), BehaviourDispatcher.getLastDispatchSubject().getTemplateId());
        }
    }

    @Test
    void testImprovingStoneMineDoors() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        Creature crafter = factory.createNewCrafter(factory.createNewPlayer(), new CrafterType(SkillList.STONECUTTING), 50);
        data = (CrafterAIData)crafter.getCreatureAIData();
        Item stoneDoor = factory.createNewItem(ItemList.mineDoorStone);
        stoneDoor.setQualityLevel(29.99f);
        WorkBook.getWorkBookFromWorker(crafter).addJob(player.getWurmId(), stoneDoor, 30, false, 100);
        crafter.getInventory().insertItem(stoneDoor);

        data.sendNextAction();
        assertTrue(BehaviourDispatcher.wasDispatched(stoneDoor, Actions.IMPROVE));
        assertThat(crafter, didNotReceiveMessageContaining("cannot improve"));
    }

    @Test
    void testStoneBrickDoesNotBlockCrafting() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        Creature crafter = factory.createNewCrafter(factory.createNewPlayer(), new CrafterType(SkillList.STONECUTTING), 50);
        data = (CrafterAIData)crafter.getCreatureAIData();
        Item brick = factory.createNewItem(ItemList.stoneBrick);
        WorkBook.getWorkBookFromWorker(crafter).addJob(player.getWurmId(), brick, 30, false, 100);
        crafter.getInventory().insertItem(brick);

        data.sendNextAction();
        assertFalse(BehaviourDispatcher.wasDispatched(brick, Actions.IMPROVE));
        assertThat(crafter, didNotReceiveMessageContaining("cannot improve"));
        assertTrue(brick.isMailed());
    }

    // Clay items

    @Test
    void testToolNotRepairedIfDamagedAndBodyPart() throws NoSpaceException, WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        crafter = factory.createNewCrafter(owner, new CrafterType(SkillList.POTTERY), 30);
        Item clayBowl = factory.createNewItem(ItemList.bowlClay);
        clayBowl.creationState = 1;
        WorkBook.getWorkBookFromWorker(crafter).addJob(123, clayBowl, 20, false, 1);
        Item tool = crafter.getBody().getBodyPart(13);
        tool.setDamage(50);

        data.sendNextAction();
        assertEquals(50, tool.getDamage());
    }

    @Test
    void testToolImprovedWithHand() throws NoSpaceException, WorkBook.WorkBookFull {
        tool = factory.createNewItem(ItemList.bowlClay);
        tool.creationState = 1;
        workBook.removeJob(workBook.iterator().next().item);
        workBook.addJob(player.getWurmId(), tool, 10, false, 1);
        data.sendNextAction();
        assertEquals(crafter.getBody().getBodyPart(13), BehaviourDispatcher.getLastDispatchSubject());
        assertEquals(tool, BehaviourDispatcher.getLastDispatchTarget());
    }

    // Donations

    @Test
    void testDonationItemRepairedIfDamaged() throws WorkBook.WorkBookFull {
        workBook.removeJob(tool);
        workBook.addDonation(tool);

        tool.setDamage(50);

        data.sendNextAction();
        assertTrue(BehaviourDispatcher.wasDispatched(tool, Actions.REPAIR));
    }

    @Test
    void testDonationImproveActionDispatched() throws WorkBook.WorkBookFull {
        warmUp();

        workBook.removeJob(tool);
        workBook.addDonation(tool);

        data.sendNextAction();
        assertTrue(BehaviourDispatcher.wasDispatched(tool, Actions.IMPROVE));
    }

    @Test
    void testDestroyDonationItem() throws WorkBook.WorkBookFull, NoSuchSkillException, WorkBook.NoWorkBookOnWorker {
        Properties properties = new Properties();
        properties.setProperty("remove_donations_at", "10");
        new CrafterMod().configure(properties);
        float currentSkill = (float)crafter.getSkills().getSkill(SkillList.SMITHING_BLACKSMITHING).getKnowledge();
        warmUp();

        workBook.removeJob(tool);
        workBook.addDonation(tool);
        tool.setQualityLevel(currentSkill + 10);

        data.sendNextAction();
        assertFalse(BehaviourDispatcher.wasDispatched(tool, Actions.IMPROVE));
        assertFalse(Items.exists(tool.getWurmId()));
        assertEquals(0, WorkBook.getWorkBookFromWorker(crafter).donationsTodo());
    }

    @Test
    void testDonationItemNotDestroyed() throws WorkBook.WorkBookFull, WorkBook.NoWorkBookOnWorker {
        warmUp();

        workBook.removeJob(tool);
        workBook.addDonation(tool);
        tool.setQualityLevel(100);

        data.sendNextAction();
        assertFalse(BehaviourDispatcher.wasDispatched(tool, Actions.IMPROVE));
        assertTrue(Items.exists(tool.getWurmId()));
        assertEquals(1, WorkBook.getWorkBookFromWorker(crafter).donationsTodo());
    }

    // Large anvil

    @Test
    void testLargeAnvilImproveActionDispatched() throws WorkBook.WorkBookFull {
        workBook.removeJob(tool);
        tool = factory.createNewItem(ItemList.anvilLarge);
        workBook.addJob(player.getWurmId(), tool, 20, false, 1);

        warmUp();
        data.sendNextAction();

        assertTrue(BehaviourDispatcher.wasDispatched(tool, Actions.IMPROVE));
    }

    @Test
    void testMailedWhenJobDone() throws NoSuchFieldException, IllegalAccessException {
        crafter.getInventory().insertItem(tool);
        tool.setQualityLevel(11);
        long price = 111L;
        ReflectionUtil.setPrivateField(job, Job.class.getDeclaredField("targetQL"), 10);
        ReflectionUtil.setPrivateField(job, Job.class.getDeclaredField("mailWhenDone"), true);
        ReflectionUtil.setPrivateField(job, Job.class.getDeclaredField("priceCharged"), price);

        data.sendNextAction();
        assertEquals(0, workBook.todo());
        assertEquals(0, workBook.done());
        assertTrue(WurmMail.allMail.stream().anyMatch(m -> m.itemId == tool.getWurmId()));
        assertEquals((long)(price * 0.9f), crafter.getShop().getMoney());
    }

    @Test
    void testNotMailedMultipleTimesWhenJobDone() throws NoSuchFieldException, IllegalAccessException {
        crafter.getInventory().insertItem(tool);
        tool.setQualityLevel(11);
        long price = 111L;
        ReflectionUtil.setPrivateField(job, Job.class.getDeclaredField("targetQL"), 10);
        ReflectionUtil.setPrivateField(job, Job.class.getDeclaredField("mailWhenDone"), true);
        ReflectionUtil.setPrivateField(job, Job.class.getDeclaredField("priceCharged"), price);

        data.sendNextAction();
        assertEquals(0, workBook.todo());
        assertEquals(0, workBook.done());
        assertTrue(WurmMail.allMail.stream().anyMatch(m -> m.itemId == tool.getWurmId()));
        assertEquals((long)(price * 0.9f), crafter.getShop().getMoney());

        WurmMail.allMail.clear();

        data.sendNextAction();
        assertFalse(WurmMail.allMail.stream().anyMatch(m -> m.itemId == tool.getWurmId()));
    }

    // Not a great test.
    @Test
    void testNullWorkBookWhenSendNextAction() throws NoSuchFieldException, IllegalAccessException {
        ReflectionUtil.setPrivateField(data, CrafterAIData.class.getDeclaredField("workbook"), null);
        assertDoesNotThrow(() -> data.sendNextAction());
    }

    @Test
    void testCrafterNotPolledIfCanActionIsFalse() throws NoSuchFieldException, IllegalAccessException {
        CrafterAIData aiData = mock(CrafterAIData.class);
        FieldSetter.setField(aiData, CrafterAIData.class.getDeclaredField("canAction"), false);
        ReflectionUtil.setPrivateField(crafter, com.wurmonline.server.creatures.Creature.class.getDeclaredField("aiData"), aiData);

        crafter.getTemplate().getCreatureAI().pollCreature(crafter, 0);
        verify(aiData, never()).sendNextAction();
    }

    @Test
    void testCrafterPolledIfCanActionIsTrue() throws NoSuchFieldException, IllegalAccessException {
        CrafterAIData aiData = mock(CrafterAIData.class);
        FieldSetter.setField(aiData, CrafterAIData.class.getDeclaredField("canAction"), true);
        ReflectionUtil.setPrivateField(crafter, Creature.class.getDeclaredField("aiData"), aiData);

        crafter.getTemplate().getCreatureAI().pollCreature(crafter, 0);
        verify(aiData, times(1)).sendNextAction();
    }

    @Test
    void testCanActionSetToFalseIfNoWorkBook() throws NoSuchFieldException, IllegalAccessException {
        Items.destroyItem(workBook.workBookItem.getWurmId());
        CrafterAIData aiData = mock(CrafterAIData.class);
        ReflectionUtil.setPrivateField(crafter, Creature.class.getDeclaredField("aiData"), aiData);

        crafter.getTemplate().getCreatureAI().pollCreature(crafter, 0);
        assertFalse(aiData.canAction);
    }

    @Test
    void testAshRemovedIfAshProduceModIsUsed() {
        assert tool.isMetal();
        warmUp();
        for (int i = 0; i < 10; ++i) {
            forge.insertItem(factory.createNewItem(ItemList.ash));
        }

        data.sendNextAction();
        assertTrue(forge.getItems().stream().noneMatch(i -> i.getTemplateId() == ItemList.ash));
    }
}
