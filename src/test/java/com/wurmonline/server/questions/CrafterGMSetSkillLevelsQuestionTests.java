package com.wurmonline.server.questions;

import com.wurmonline.server.behaviours.BehaviourDispatcher;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemFactory;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.items.WurmMail;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.skills.NoSuchSkillException;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.shared.constants.ItemMaterials;
import mod.wurmunlimited.CrafterObjectsFactory;
import mod.wurmunlimited.npcs.CrafterMod;
import mod.wurmunlimited.npcs.CrafterType;
import mod.wurmunlimited.npcs.WorkBook;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static mod.wurmunlimited.Assert.*;
import static org.junit.Assert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class CrafterGMSetSkillLevelsQuestionTests {
    private CrafterObjectsFactory factory;
    private Player owner;
    private Creature crafter;

    @BeforeEach
    void setUp() throws Exception {
        factory = new CrafterObjectsFactory();
        BehaviourDispatcher.reset();
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("skillCap"), 99.99999f);
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("basePrice"), 1);
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("minimumPriceModifier"), 0.0000001f);
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("canLearn"), true);
        owner = factory.createNewPlayer();
        owner.setPower((byte)2);
        crafter = factory.createNewCrafter(owner, new CrafterType(CrafterType.allMetal), 50);
    }

    @Test
    void testMaxSkillCapIfCanLearnFalse() {
        Properties properties = new Properties();
        properties.setProperty("can_learn", "false");
        new CrafterMod().configure(properties);
        new CrafterGMSetSkillLevelsQuestion(owner, crafter).sendQuestion();

        assertThat(owner, receivedBMLContaining("Skills will be capped at " + CrafterMod.getSkillCap()));
    }

    @Test
    void testCrafterSkillCapIfCanLearnTrue() throws WorkBook.NoWorkBookOnWorker {
        Properties properties = new Properties();
        properties.setProperty("can_learn", "true");
        new CrafterMod().configure(properties);
        new CrafterGMSetSkillLevelsQuestion(owner, crafter).sendQuestion();

        assertThat(owner, receivedBMLContaining("input{text=\"" + WorkBook.getWorkBookFromWorker(crafter).getSkillCap() + "\";id=\"skill_cap"));
    }

    @Test
    void testCancelButtonSelected() {
        Properties properties = new Properties();
        properties.setProperty("cancel", "true");
        new CrafterGMSetSkillLevelsQuestion(owner, crafter).answer(properties);

        assertThat(owner, receivedMessageContaining("No change"));
    }

    @Test
    void testSkillsInList() {
        crafter = factory.createNewCrafter(owner, new CrafterType(SkillList.CARPENTRY), 50);
        new CrafterGMSetSkillLevelsQuestion(owner, crafter).sendQuestion();
        assertThat(owner, receivedBMLContaining("label{text=\"Carpentry\"};label{text=\"50.00\"};input{text=\"\";id=\"" + SkillList.CARPENTRY + "\""));
    }

    @Test
    void testSkillsUpdated() throws NoSuchSkillException {
        crafter = factory.createNewCrafter(owner, new CrafterType(SkillList.CARPENTRY, SkillList.SMITHING_BLACKSMITHING), 99);
        float carpentry = 54;
        float blacksmithing = 72;
        assert crafter.getSkills().getSkill(SkillList.CARPENTRY).getKnowledge() != carpentry;
        assert crafter.getSkills().getSkill(SkillList.SMITHING_BLACKSMITHING).getKnowledge() != blacksmithing;

        Properties properties = new Properties();
        properties.setProperty(String.valueOf(SkillList.CARPENTRY), String.valueOf(carpentry));
        properties.setProperty(String.valueOf(SkillList.SMITHING_BLACKSMITHING), String.valueOf(blacksmithing));
        new CrafterGMSetSkillLevelsQuestion(owner, crafter).answer(properties);

        assertEquals(carpentry, crafter.getSkills().getSkill(SkillList.CARPENTRY).getKnowledge());
        assertEquals(blacksmithing, crafter.getSkills().getSkill(SkillList.SMITHING_BLACKSMITHING).getKnowledge());
    }

    @Test
    void testSkillsNotSetAboveMax() throws NoSuchSkillException {
        float maxSkill = 20;
        Properties cap = new Properties();
        cap.setProperty("max_skill", String.valueOf(maxSkill));
        new CrafterMod().configure(cap);
        crafter = factory.createNewCrafter(owner, new CrafterType(SkillList.CARPENTRY, SkillList.SMITHING_BLACKSMITHING), maxSkill);
        float carpentry = 54;
        float blacksmithing = 72;
        assert crafter.getSkills().getSkill(SkillList.CARPENTRY).getKnowledge() != carpentry;
        assert crafter.getSkills().getSkill(SkillList.SMITHING_BLACKSMITHING).getKnowledge() != blacksmithing;

        Properties properties = new Properties();
        properties.setProperty(String.valueOf(SkillList.CARPENTRY), String.valueOf(carpentry));
        properties.setProperty(String.valueOf(SkillList.SMITHING_BLACKSMITHING), String.valueOf(blacksmithing));
        new CrafterGMSetSkillLevelsQuestion(owner, crafter).answer(properties);

        assertEquals(maxSkill, crafter.getSkills().getSkill(SkillList.CARPENTRY).getKnowledge());
        assertEquals(maxSkill, crafter.getSkills().getSkill(SkillList.SMITHING_BLACKSMITHING).getKnowledge());
    }

    @Test
    void testSkillsNotSetBelowStarting() throws NoSuchSkillException {
        float minSkill = 20;
        Properties cap = new Properties();
        cap.setProperty("starting_skill", String.valueOf(minSkill));
        new CrafterMod().configure(cap);
        crafter = factory.createNewCrafter(owner, new CrafterType(SkillList.CARPENTRY, SkillList.SMITHING_BLACKSMITHING), minSkill);
        float carpentry = 5;
        float blacksmithing = 7;
        assert crafter.getSkills().getSkill(SkillList.CARPENTRY).getKnowledge() != carpentry;
        assert crafter.getSkills().getSkill(SkillList.SMITHING_BLACKSMITHING).getKnowledge() != blacksmithing;

        Properties properties = new Properties();
        properties.setProperty(String.valueOf(SkillList.CARPENTRY), String.valueOf(carpentry));
        properties.setProperty(String.valueOf(SkillList.SMITHING_BLACKSMITHING), String.valueOf(blacksmithing));
        new CrafterGMSetSkillLevelsQuestion(owner, crafter).answer(properties);

        assertEquals(minSkill, crafter.getSkills().getSkill(SkillList.CARPENTRY).getKnowledge());
        assertEquals(minSkill, crafter.getSkills().getSkill(SkillList.SMITHING_BLACKSMITHING).getKnowledge());
    }
    
    // Copied from CrafterGMSetSkillLevelsQuestionTests

    @Test
    void testSkillCapProperlyFilledWithDefault() throws WorkBook.NoWorkBookOnWorker {
        float skillCap = WorkBook.getWorkBookFromWorker(crafter).getSkillCap();
        new CrafterGMSetSkillLevelsQuestion(owner, crafter).sendQuestion();

        assertTrue(factory.getCommunicator(owner).lastBmlContent.contains("input{text=\"" + skillCap + "\";id=\"skill_cap\";"), factory.getCommunicator(owner).lastBmlContent);
    }

    @Test
    void testSkillCapProperlyUpdated() throws WorkBook.NoWorkBookOnWorker {
        int skillCap = 75;
        Properties properties = new Properties();
        properties.setProperty("skill_cap", Integer.toString(skillCap));
        new CrafterGMSetSkillLevelsQuestion(owner, crafter).answer(properties);

        assertEquals(skillCap, WorkBook.getWorkBookFromWorker(crafter).getSkillCap());
    }

    @Test
    void testSkillCapTooLow() throws WorkBook.NoWorkBookOnWorker {
        float skillCap = 19.99f;
        Properties properties = new Properties();
        properties.setProperty("skill_cap", Float.toString(skillCap));
        new CrafterGMSetSkillLevelsQuestion(owner, crafter).answer(properties);

        MatcherAssert.assertThat(owner, receivedMessageContaining("cap was too low"));
        assertEquals(20, WorkBook.getWorkBookFromWorker(crafter).getSkillCap());
    }

    @Test
    void testSkillCapTooHigh() throws WorkBook.NoWorkBookOnWorker {
        int skillCap = 101;
        Properties properties = new Properties();
        properties.setProperty("skill_cap", Integer.toString(skillCap));
        new CrafterGMSetSkillLevelsQuestion(owner, crafter).answer(properties);

        MatcherAssert.assertThat(owner, receivedMessageContaining("was too high"));
        assertEquals(CrafterMod.getSkillCap(), WorkBook.getWorkBookFromWorker(crafter).getSkillCap());
    }

    @Test
    void testSkillCapInvalid() {
        Properties properties = new Properties();
        properties.setProperty("skill_cap", "abc");
        new CrafterGMSetSkillLevelsQuestion(owner, crafter).answer(properties);

        MatcherAssert.assertThat(owner, receivedMessageContaining("was invalid"));
    }

    @Test
    void testEmptySkillCapUsesCurrent() throws WorkBook.NoWorkBookOnWorker {
        float startingCap = WorkBook.getWorkBookFromWorker(crafter).getSkillCap();
        Properties properties = new Properties();
        properties.setProperty("skill_cap", "");
        new CrafterGMSetSkillLevelsQuestion(owner, crafter).answer(properties);

        MatcherAssert.assertThat(owner, didNotReceiveMessageContaining("was invalid"));
        assertEquals(startingCap, WorkBook.getWorkBookFromWorker(crafter).getSkillCap());
    }

    @Test
    void testCanLearnFalseSkillCapNotChangeable() {
        Properties properties = new Properties();
        properties.setProperty("can_learn", "false");
        new CrafterMod().configure(properties);
        new CrafterGMSetSkillLevelsQuestion(owner, crafter).sendQuestion();

        String bml = factory.getCommunicator(owner).lastBmlContent;
        assertFalse(bml.contains("input{text=\"50.0\";id=\"skill_cap\";"), bml);
        assertTrue(bml.contains("text{text=\"Skills will be capped at " + CrafterMod.getSkillCap()), bml);
    }

    @Test
    void testCanLearnTrueSkillCapChangeable() {
        Properties properties = new Properties();
        properties.setProperty("can_learn", "true");
        new CrafterMod().configure(properties);
        new CrafterGMSetSkillLevelsQuestion(owner, crafter).sendQuestion();

        String bml = factory.getCommunicator(owner).lastBmlContent;
        assertTrue(bml.contains("input{text=\"50.0\";id=\"skill_cap\";"), bml);
        assertFalse(bml.contains("label{text=\"Skill Cap: \"};text{text="), bml);
    }

    @Test
    void testSkillsReducedIfSkillCapLowered() throws NoSuchSkillException {
        crafter = factory.createNewCrafter(owner, new CrafterType(SkillList.GROUP_SMITHING_WEAPONSMITHING), 99);
        crafter.getSkills().getSkill(SkillList.GROUP_SMITHING_WEAPONSMITHING).setKnowledge(75, false);

        int skillCap = 45;
        Properties properties = new Properties();
        properties.setProperty("skill_cap", Integer.toString(skillCap));
        new CrafterGMSetSkillLevelsQuestion(owner, crafter).answer(properties);

        assertEquals(45, crafter.getSkills().getSkill(SkillList.GROUP_SMITHING_WEAPONSMITHING).getKnowledge());
    }

    @Test
    void testDonationItemsDestroyedIfOptionSetAndItemQLTooHigh() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        workBook.updateSkillsSettings(workBook.getCrafterType(), 80);
        Item donation = ItemFactory.createItem(ItemList.pickAxe, 91, "");
        donation.setMaterial(ItemMaterials.MATERIAL_IRON);
        workBook.addDonation(donation);
        crafter.getInventory().insertItem(donation);

        Properties properties = new Properties();
        properties.setProperty("rd", "true");
        new CrafterGMSetSkillLevelsQuestion(owner, crafter).answer(properties);

        assertFalse(crafter.getInventory().getItems().contains(donation));
    }

    @Test
    void testDonationItemsDestroyedIfOptionSetAndCanLearnFalse() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        Properties canLearn = new Properties();
        canLearn.setProperty("can_learn", "false");
        new CrafterMod().configure(canLearn);
        Item donation = ItemFactory.createItem(ItemList.pickAxe, 10, "");
        donation.setMaterial(ItemMaterials.MATERIAL_IRON);
        WorkBook.getWorkBookFromWorker(crafter).addDonation(donation);
        crafter.getInventory().insertItem(donation);

        Properties properties = new Properties();
        properties.setProperty("rd", "true");
        new CrafterGMSetSkillLevelsQuestion(owner, crafter).answer(properties);

        assertFalse(crafter.getInventory().getItems().contains(donation));
    }

    @Test
    void testDonationItemsNotDestroyedIfOptionUnSetAndItemQLTooHigh() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        workBook.updateSkillsSettings(workBook.getCrafterType(), 80);
        Item donation = ItemFactory.createItem(ItemList.pickAxe, 91, "");
        donation.setMaterial(ItemMaterials.MATERIAL_IRON);
        workBook.addDonation(donation);
        crafter.getInventory().insertItem(donation);

        Properties properties = new Properties();
        properties.setProperty("rd", "false");
        new CrafterGMSetSkillLevelsQuestion(owner, crafter).answer(properties);

        assertTrue(crafter.getInventory().getItems().contains(donation));
    }

    @Test
    void testDonationItemsNotDestroyedIfOptionUnSetAndCanLearnFalse() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        Properties canLearn = new Properties();
        canLearn.setProperty("can_learn", "false");
        new CrafterMod().configure(canLearn);
        Item donation = ItemFactory.createItem(ItemList.swordLong, 10, "");
        WorkBook.getWorkBookFromWorker(crafter).addDonation(donation);
        crafter.getInventory().insertItem(donation);

        Properties properties = new Properties();
        properties.setProperty("rd", "false");
        new CrafterGMSetSkillLevelsQuestion(owner, crafter).answer(properties);

        assertTrue(crafter.getInventory().getItems().contains(donation));
    }

    @Test
    void testChangeBlockedWhenItemImprovementSkillLevelInsufficient() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        int targetQL = 20;
        Item jobItem = ItemFactory.createItem(ItemList.pickAxe, 10, "");
        jobItem.setMaterial(ItemMaterials.MATERIAL_IRON);
        WorkBook.getWorkBookFromWorker(crafter).addJob(owner.getWurmId(), jobItem, targetQL, false, 1);
        crafter.getInventory().insertItem(jobItem);

        Properties properties = new Properties();
        properties.setProperty(String.valueOf(SkillList.SMITHING_BLACKSMITHING), String.valueOf(targetQL - 1));
        properties.setProperty("refund", "false");
        new CrafterGMSetSkillLevelsQuestion(owner, crafter).answer(properties);

        assertTrue(crafter.getInventory().getItems().contains(jobItem));
        MatcherAssert.assertThat(owner, receivedMessageContaining("still has some jobs"));
    }

    @Test
    void testJobItemRefundedWhenSkillLevelInsufficient() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        Properties startingSkill = new Properties();
        startingSkill.setProperty("starting_skill", "1");
        new CrafterMod().configure(startingSkill);
        int targetQL = 20;
        Item jobItem = ItemFactory.createItem(ItemList.pickAxe, 10, "");
        jobItem.setMaterial(ItemMaterials.MATERIAL_IRON);
        WorkBook.getWorkBookFromWorker(crafter).addJob(owner.getWurmId(), jobItem, targetQL, false, 1);
        crafter.getInventory().insertItem(jobItem);

        Properties properties = new Properties();
        properties.setProperty(String.valueOf(SkillList.SMITHING_BLACKSMITHING), String.valueOf(targetQL - 1));
        properties.setProperty("refund", "true");
        new CrafterGMSetSkillLevelsQuestion(owner, crafter).answer(properties);

        assertFalse(crafter.getInventory().getItems().contains(jobItem));
        assertTrue(WurmMail.allMail.stream().anyMatch((m) -> m.itemId == jobItem.getWurmId() && m.ownerId == owner.getWurmId()));
        MatcherAssert.assertThat(owner, didNotReceiveMessageContaining("still has some jobs"));
    }

    @Test
    void testJobItemNotRefundedWhenSkillLevelStillSufficient() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        int targetQL = 20;
        Item jobItem = ItemFactory.createItem(ItemList.pickAxe, 10, "");
        jobItem.setMaterial(ItemMaterials.MATERIAL_IRON);
        WorkBook.getWorkBookFromWorker(crafter).addJob(owner.getWurmId(), jobItem, targetQL, false, 1);
        crafter.getInventory().insertItem(jobItem);

        Properties properties = new Properties();
        properties.setProperty(String.valueOf(SkillList.SMITHING_BLACKSMITHING), String.valueOf(targetQL));
        properties.setProperty("refund", "true");
        new CrafterGMSetSkillLevelsQuestion(owner, crafter).answer(properties);

        assertTrue(crafter.getInventory().getItems().contains(jobItem));
        assertFalse(WurmMail.allMail.stream().anyMatch((m) -> m.itemId == jobItem.getWurmId() && m.ownerId == owner.getWurmId()));
        MatcherAssert.assertThat(owner, didNotReceiveMessageContaining("still has some jobs"));
    }
}
