package com.wurmonline.server.questions;

import com.wurmonline.server.behaviours.BehaviourDispatcher;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.items.WurmMail;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.questions.skills.MultipleSkillsBML;
import com.wurmonline.server.questions.skills.SingleSkillBML;
import com.wurmonline.server.skills.NoSuchSkillException;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.skills.SkillSystem;
import mod.wurmunlimited.CrafterObjectsFactory;
import mod.wurmunlimited.bml.BMLBuilder;
import mod.wurmunlimited.npcs.CrafterMod;
import mod.wurmunlimited.npcs.CrafterType;
import mod.wurmunlimited.npcs.WorkBook;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

import static mod.wurmunlimited.Assert.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class CrafterModifySkillsQuestionTests {
    private static final int[] allCrafterTypes = new int[] {
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
    private CrafterObjectsFactory factory;
    private Player owner;
    private Creature crafter;

    @BeforeEach
    void setUp() throws Exception {
        factory = new CrafterObjectsFactory();
        BehaviourDispatcher.reset();
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("singleSkill"), false);
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("skillCap"), 99.99999f);
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("basePrice"), 1);
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("minimumPriceModifier"), 0.0000001f);
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("canLearn"), true);
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("maxItemQL"), 99.99999f);
        owner = factory.createNewPlayer();
        crafter = factory.createNewCrafter(owner, new CrafterType(SkillList.COOKING_BAKING), 50);
    }

    private Properties generateProperties() {
        return generateProperties("all_metal");
    }

    private Properties generateProperties(String crafterType) {
        Properties properties = new Properties();
        switch (crafterType) {
            case "all_metal":
                properties.setProperty("all_metal", "true");
                break;
            case "all_wood":
                properties.setProperty("all_wood", "true");
                break;
            case "all_armour":
                properties.setProperty("all_armour", "true");
                break;
        }

        return generateProperties(properties);
    }

    private Properties generateProperties(Integer[] skills) {
        Properties properties = new Properties();
        for (int skill : skills) {
            properties.setProperty(Integer.toString(skill), "true");
        }

        return generateProperties(properties);
    }

    private Properties generateProperties(Properties properties) {
        properties.setProperty("skill_cap", "99.9");
        properties.setProperty("price_modifier", "1.0");
        properties.setProperty("name", "NewCrafter");
        properties.setProperty("gender", "male");

        return properties;
    }

    @Test
    void testSpecialCrafterTypeProperlySet() throws WorkBook.NoWorkBookOnWorker {
        for (String type : new String[] { "all_metal", "all_wood", "all_armour" }) {
            new CrafterModifySkillsQuestion(owner, crafter).answer(generateProperties(type));

            CrafterType crafterType = null;
            switch (type) {
                case "all_metal":
                    crafterType = new CrafterType(CrafterType.allMetal);
                    break;
                case "all_wood":
                    crafterType = new CrafterType(CrafterType.allWood);
                    break;
                case "all_armour":
                    crafterType = new CrafterType(CrafterType.allArmour);
                    break;
            }

            assertEquals(crafterType, WorkBook.getWorkBookFromWorker(crafter).getCrafterType());
        }
    }

    @Test
    void testCrafterTypeProperlySet() throws WorkBook.NoWorkBookOnWorker {
        for (int type : CrafterType.allSkills) {
            new CrafterModifySkillsQuestion(owner, crafter).answer(generateProperties(new Integer[] { type }));

            assertEquals(new CrafterType(type), WorkBook.getWorkBookFromWorker(crafter).getCrafterType());
        }
    }

    @Test
    void testCrafterNotUpdatedIfNoTypeSet() throws WorkBook.NoWorkBookOnWorker {
        CrafterType crafterType = WorkBook.getWorkBookFromWorker(crafter).getCrafterType();
        new CrafterModifySkillsQuestion(owner, crafter).answer(generateProperties(""));

        assertEquals(crafterType, WorkBook.getWorkBookFromWorker(crafter).getCrafterType());
        assertThat(owner, receivedMessageContaining("You must select"));
    }

    @Test
    void testSkillCapProperlyFilledWithDefault() throws WorkBook.NoWorkBookOnWorker {
        float skillCap = WorkBook.getWorkBookFromWorker(crafter).getSkillCap();
        new CrafterModifySkillsQuestion(owner, crafter).sendQuestion();

        assertTrue(factory.getCommunicator(owner).lastBmlContent.contains("input{text=\"" + skillCap + "\";id=\"skill_cap\";"), factory.getCommunicator(owner).lastBmlContent + "\nExpected " + skillCap + "\n");
    }

    @Test
    void testSkillCapProperlyUpdated() throws WorkBook.NoWorkBookOnWorker {
        int skillCap = 75;
        Properties properties = generateProperties();
        properties.setProperty("skill_cap", Integer.toString(skillCap));
        new CrafterModifySkillsQuestion(owner, crafter).answer(properties);

        assertEquals(skillCap, WorkBook.getWorkBookFromWorker(crafter).getSkillCap());
    }

    @Test
    void testSkillCapTooLow() throws WorkBook.NoWorkBookOnWorker {
        float skillCap = 19.99f;
        Properties properties = generateProperties();
        properties.setProperty("skill_cap", Float.toString(skillCap));
        new CrafterModifySkillsQuestion(owner, crafter).answer(properties);

        assertThat(owner, receivedMessageContaining("cap was too low"));
        assertEquals(20, WorkBook.getWorkBookFromWorker(crafter).getSkillCap());
    }

    @Test
    void testSkillCapTooHigh() throws WorkBook.NoWorkBookOnWorker {
        int skillCap = 101;
        Properties properties = generateProperties();
        properties.setProperty("skill_cap", Integer.toString(skillCap));
        new CrafterModifySkillsQuestion(owner, crafter).answer(properties);

        assertThat(owner, receivedMessageContaining("was too high"));
        assertEquals(CrafterMod.getSkillCap(), WorkBook.getWorkBookFromWorker(crafter).getSkillCap());
    }

    @Test
    void testSkillCapInvalid() {
        Properties properties = generateProperties();
        properties.setProperty("skill_cap", "abc");
        new CrafterModifySkillsQuestion(owner, crafter).answer(properties);

        assertThat(owner, receivedMessageContaining("was invalid"));
    }

    @Test
    void testEmptySkillCapUsesCurrent() throws WorkBook.NoWorkBookOnWorker {
        float startingCap = WorkBook.getWorkBookFromWorker(crafter).getSkillCap();
        Properties properties = generateProperties();
        properties.setProperty("skill_cap", "");
        new CrafterModifySkillsQuestion(owner, crafter).answer(properties);

        assertThat(owner, didNotReceiveMessageContaining("was invalid"));
        assertEquals(startingCap, WorkBook.getWorkBookFromWorker(crafter).getSkillCap());
    }

    @Test
    void testCanLearnFalseSkillCapNotChangeable() {
        Properties properties = new Properties();
        properties.setProperty("can_learn", "false");
        new CrafterMod().configure(properties);
        new CrafterModifySkillsQuestion(owner, crafter).sendQuestion();

        String bml = factory.getCommunicator(owner).lastBmlContent;
        assertFalse(bml.contains("input{text=\"99.99999\";id=\"skill_cap\";"));
        assertTrue(bml.contains("label{text=\"Skill Cap: \"};text{text="));
    }

    @Test
    void testCanLearnTrueSkillCapChangeable() {
        Properties properties = new Properties();
        properties.setProperty("can_learn", "true");
        new CrafterMod().configure(properties);
        new CrafterModifySkillsQuestion(owner, crafter).sendQuestion();

        String bml = factory.getCommunicator(owner).lastBmlContent;
        assertTrue(bml.contains("input{text=\"50.0\";id=\"skill_cap\";"), bml);
        assertFalse(bml.contains("label{text=\"Skill Cap: \"};text{text="), bml);
    }

    @Test
    void testSkillCapMaxItemQLProperlySet() throws WorkBook.NoWorkBookOnWorker, NoSuchFieldException, IllegalAccessException {
        new CrafterModifySkillsQuestion(owner, crafter).sendQuestion();

        assertThat(owner, receivedBMLContaining("Max Item QL: 99.99999\""));
    }

    @Test
    void testSkillCapOnlyMessageWhenAboveMaxItemQL() throws WorkBook.NoWorkBookOnWorker, NoSuchFieldException, IllegalAccessException {
        int skillCap = 75;
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("maxItemQL"), (float)(skillCap - 1));
        Properties properties = generateProperties();
        properties.setProperty("skill_cap", Integer.toString(skillCap));
        new CrafterModifySkillsQuestion(owner, crafter).answer(properties);

        assertThat(owner, receivedMessageContaining("higher than the maximum item ql"));
        assertEquals(skillCap, WorkBook.getWorkBookFromWorker(crafter).getSkillCap());
    }

    @Test
    void testAppropriateCrafterSkillsAdded() throws NoSuchSkillException {
        new CrafterModifySkillsQuestion(owner, crafter).answer(generateProperties(new Integer[] { SkillList.SMITHING_ARMOUR_PLATE }));

        assertEquals(CrafterMod.getStartingSkillLevel(), crafter.getSkills().getSkill(SkillList.SMITHING_ARMOUR_PLATE).getKnowledge());
    }

    @Test
    void testOldSkillsNotAffected() throws NoSuchSkillException {
        new CrafterModifySkillsQuestion(owner, crafter).answer(generateProperties(new Integer[] { SkillList.GROUP_SMITHING_WEAPONSMITHING }));
        crafter.getSkills().getSkill(SkillList.GROUP_SMITHING_WEAPONSMITHING).setKnowledge(75, false);

        new CrafterModifySkillsQuestion(owner, crafter).answer(generateProperties(new Integer[] { SkillList.SMITHING_ARMOUR_PLATE }));

        assertEquals(75, crafter.getSkills().getSkill(SkillList.GROUP_SMITHING_WEAPONSMITHING).getKnowledge());
        assertEquals(CrafterMod.getStartingSkillLevel(), crafter.getSkills().getSkill(SkillList.SMITHING_ARMOUR_PLATE).getKnowledge());
    }

    @Test
    void testSkillsReducedIfSkillCapLowered() throws NoSuchSkillException {
        new CrafterModifySkillsQuestion(owner, crafter).answer(generateProperties(new Integer[] { SkillList.GROUP_SMITHING_WEAPONSMITHING }));
        crafter.getSkills().getSkill(SkillList.GROUP_SMITHING_WEAPONSMITHING).setKnowledge(75, false);

        int skillCap = 45;
        Properties properties = generateProperties(new Integer[] { SkillList.GROUP_SMITHING_WEAPONSMITHING });
        properties.setProperty("skill_cap", Integer.toString(skillCap));
        new CrafterModifySkillsQuestion(owner, crafter).answer(properties);

        assertEquals(45, crafter.getSkills().getSkill(SkillList.GROUP_SMITHING_WEAPONSMITHING).getKnowledge());
    }

    @Test
    void testDonationItemsDestroyedIfOptionSet() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        new CrafterModifySkillsQuestion(owner, crafter).answer(generateProperties("all_metal"));
        Item donation = factory.createNewItem(ItemList.pickAxe);
        donation.setQualityLevel(10);
        WorkBook.getWorkBookFromWorker(crafter).addDonation(donation);
        crafter.getInventory().insertItem(donation);

        Properties properties = generateProperties(CrafterType.allMetal);
        properties.setProperty("rd", "true");
        properties.setProperty(String.valueOf(SkillList.SMITHING_BLACKSMITHING), "false");
        new CrafterModifySkillsQuestion(owner, crafter).answer(properties);

        assertFalse(crafter.getInventory().getItems().contains(donation));
    }

    @Test
    void testDonationItemsNotDestroyedIfOptionUnSet() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        new CrafterModifySkillsQuestion(owner, crafter).answer(generateProperties("all_metal"));
        Item donation = factory.createNewItem(ItemList.swordLong);
        donation.setQualityLevel(10);
        WorkBook.getWorkBookFromWorker(crafter).addDonation(donation);
        crafter.getInventory().insertItem(donation);

        Properties properties = generateProperties(CrafterType.allMetal);
        properties.setProperty(String.valueOf(SkillList.GROUP_SMITHING_WEAPONSMITHING), "false");
        properties.setProperty("rd", "false");
        new CrafterModifySkillsQuestion(owner, crafter).answer(properties);

        assertTrue(crafter.getInventory().getItems().contains(donation));
    }

    @Test
    void testChangeBlockedWhenItemRequiresRemovedSkill() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        new CrafterModifySkillsQuestion(owner, crafter).answer(generateProperties("all_metal"));
        Item jobItem = factory.createNewItem(ItemList.pickAxe);
        jobItem.setQualityLevel(10);
        WorkBook.getWorkBookFromWorker(crafter).addJob(owner.getWurmId(), jobItem, 20, false, 1);
        crafter.getInventory().insertItem(jobItem);

        Properties properties = generateProperties(CrafterType.allMetal);
        properties.setProperty(String.valueOf(SkillList.SMITHING_BLACKSMITHING), "false");
        properties.setProperty("refund", "false");
        new CrafterModifySkillsQuestion(owner, crafter).answer(properties);

        assertTrue(crafter.getInventory().getItems().contains(jobItem));
        assertThat(owner, receivedMessageContaining("still has some jobs"));
    }

    @Test
    void testJobItemRefundedWhenRequiresRemovedSkill() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        new CrafterModifySkillsQuestion(owner, crafter).answer(generateProperties("all_metal"));
        Item jobItem = factory.createNewItem(ItemList.pickAxe);
        jobItem.setQualityLevel(10);
        WorkBook.getWorkBookFromWorker(crafter).addJob(owner.getWurmId(), jobItem, 20, false, 1);
        crafter.getInventory().insertItem(jobItem);

        Properties properties = generateProperties(CrafterType.allMetal);
        properties.setProperty(String.valueOf(SkillList.SMITHING_BLACKSMITHING), "false");
        properties.setProperty("refund", "true");
        new CrafterModifySkillsQuestion(owner, crafter).answer(properties);

        assertFalse(crafter.getInventory().getItems().contains(jobItem));
        assertTrue(WurmMail.allMail.stream().anyMatch((m) -> m.itemId == jobItem.getWurmId() && m.ownerId == owner.getWurmId()));
        assertThat(owner, didNotReceiveMessageContaining("still has some jobs"));
    }

    @Test
    void testPlayerDoesNotGetSetSkillsButton() {
        assert owner.getPower() < 2;
        new CrafterModifySkillsQuestion(owner, crafter).sendQuestion();

        assertFalse(factory.getCommunicator(owner).lastBmlContent.contains("button{text=\"Set Skill Levels\";id=\"set\";"), factory.getCommunicator(owner).lastBmlContent);
    }

    @Test
    void testGMDoesGetSetSkillsButton() throws IOException {
        owner.setPower((byte)2);
        new CrafterModifySkillsQuestion(owner, crafter).sendQuestion();

        assertFalse(factory.getCommunicator(owner).lastBmlContent.contains("button{text=\"Set Skill Levels\";id=\"set\";"), factory.getCommunicator(owner).lastBmlContent);
    }

    @Test
    void testSetSkillLevelsButtonOpensQuestion() throws IOException {
        owner.setPower((byte)2);
        Properties properties = new Properties();
        properties.setProperty("set", "true");
        new CrafterModifySkillsQuestion(owner, crafter).answer(properties);

        String[] bml = factory.getCommunicator(owner).getBml();
        assertEquals(1, bml.length);
        new CrafterGMSetSkillLevelsQuestion(owner, crafter).sendQuestion();
        bml = factory.getCommunicator(owner).getBml();
        assertEquals(2, bml.length, Arrays.toString(factory.getCommunicator(owner).getBml()));
        assertThat(owner, bmlEqual());
    }

    @Test
    void testSettingsNotAppliedIfGoingToSetSkillLevels() throws WorkBook.NoWorkBookOnWorker, IOException {
        CrafterType crafterType = WorkBook.getWorkBookFromWorker(crafter).getCrafterType();
        owner.setPower((byte)2);

        for (int type : allCrafterTypes) {
            Properties properties = generateProperties(new Integer[] { type });
            properties.setProperty("set", "true");
            new CrafterModifySkillsQuestion(owner, crafter).answer(properties);

            assertEquals(crafterType, WorkBook.getWorkBookFromWorker(crafter).getCrafterType());
        }
    }

    @Test
    void testMultipleSkillsBMLAdded() throws NoSuchFieldException, IllegalAccessException, WorkBook.NoWorkBookOnWorker {
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("singleSkill"), false);
        new CrafterModifySkillsQuestion(owner, crafter).sendQuestion();
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        String bml = new MultipleSkillsBML().addBML(new BMLBuilder(0), workBook.getCrafterType(), workBook.getSkillCap()).build();
        bml = bml.replaceAll("border\\{center\\{text\\{type=\"bold\";text=\"\"}};null;scroll\\{vertical=\"true\";horizontal=\"false\";varray\\{rescale=\"true\";passthrough\\{id=\"id\";text=\"0\"}", "");
        bml = bml.replaceAll("}}};null;null;}", "");

        assertThat(owner, receivedBMLContaining(bml));
    }

    @Test
    void testSingleSkillsBMLAdded() throws NoSuchFieldException, IllegalAccessException, WorkBook.NoWorkBookOnWorker {
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("singleSkill"), true);
        new CrafterModifySkillsQuestion(owner, crafter).sendQuestion();
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        String bml = new SingleSkillBML().addBML(new BMLBuilder(0), workBook.getCrafterType(), workBook.getSkillCap()).build();
        bml = bml.replaceAll("border\\{center\\{text\\{type=\"bold\";text=\"\"}};null;scroll\\{vertical=\"true\";horizontal=\"false\";varray\\{rescale=\"true\";passthrough\\{id=\"id\";text=\"0\"}", "");
        bml = bml.replaceAll("}}};null;null;}", "");

        assertThat(owner, receivedBMLContaining(bml));
    }

    private void createCrafterWithSkillsMultipleAndSingle(int skill, Integer[] skills) {
        Properties properties = generateProperties(skills);

        int idx = 0;
        for (int i = 0; i < CrafterType.allSkills.length; ++i) {
            if (CrafterType.allSkills[i] == skill) {
                idx = i;
                break;
            }
        }
        assert idx != 0;

        properties.setProperty("skill", Integer.toString(idx));

        new CrafterModifySkillsQuestion(owner, crafter).answer(properties);
    }

    @Test
    void testMultipleSkillsSetProperly() throws WorkBook.NoWorkBookOnWorker, NoSuchFieldException, IllegalAccessException {
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("singleSkill"), false);
        int skill = SkillList.SMITHING_GOLDSMITHING;
        Integer[] skills = new Integer[] { SkillList.CARPENTRY, SkillList.CARPENTRY_FINE };
        createCrafterWithSkillsMultipleAndSingle(skill, skills);

        CrafterType crafterType = WorkBook.getWorkBookFromWorker(crafter).getCrafterType();
        Integer[] setSkills = crafterType.getAllTypes();
        Arrays.sort(setSkills);
        assertArrayEquals(skills, setSkills);
    }

    @Test
    void testSingleSkillsSetProperly() throws WorkBook.NoWorkBookOnWorker, NoSuchFieldException, IllegalAccessException {
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("singleSkill"), true);
        int skill = SkillList.SMITHING_GOLDSMITHING;
        Integer[] skills = new Integer[] { SkillList.CARPENTRY, SkillList.CARPENTRY_FINE };
        createCrafterWithSkillsMultipleAndSingle(skill, skills);

        CrafterType crafterType = WorkBook.getWorkBookFromWorker(crafter).getCrafterType();
        Integer[] setSkills = crafterType.getAllTypes();
        assertEquals(1, setSkills.length);
        assertEquals(skill, (int)setSkills[0]);
    }

    @Test
    void testSingleSkillsDropdownHasFirstSkill() throws NoSuchFieldException, IllegalAccessException {
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("singleSkill"), true);
        new CrafterModifySkillsQuestion(owner, crafter).sendQuestion();
        String first = SkillSystem.getNameFor(CrafterType.allSkills[0]);

        assertThat(owner, receivedBMLContaining(first));
    }

    @Test
    void testMultipleSkillsHasCorrectIds() {
        new CrafterModifySkillsQuestion(owner, crafter).sendQuestion();

        assertThat(owner, receivedBMLContaining("id=\"all_metal\""));
        assertThat(owner, receivedBMLContaining("id=\"all_wood\""));
        assertThat(owner, receivedBMLContaining("id=\"all_armour\""));
    }
}
