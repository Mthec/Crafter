package com.wurmonline.server.questions;

import com.wurmonline.server.Constants;
import com.wurmonline.server.behaviours.BehaviourDispatcher;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.Creatures;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.questions.skills.MultipleSkillsBML;
import com.wurmonline.server.questions.skills.SingleSkillBML;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.villages.Villages;
import mod.wurmunlimited.CrafterObjectsFactory;
import mod.wurmunlimited.bml.BML;
import mod.wurmunlimited.bml.BMLBuilder;
import mod.wurmunlimited.npcs.*;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static com.wurmonline.server.questions.CrafterHireQuestion.modelOptions;
import static mod.wurmunlimited.Assert.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class CrafterHireQuestionTests {
    private static final String dbName = "crafter.db";
    private CrafterObjectsFactory factory;
    private Player owner;
    private Item contract;

    @BeforeEach
    void setUp() throws Exception {
        Constants.dbHost = ".";
        factory = new CrafterObjectsFactory();
        BehaviourDispatcher.reset();
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("singleSkill"), false);
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("skillCap"), 99.99999f);
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("basePrice"), 1);
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("minimumPriceModifier"), 0.0000001f);
        ReflectionUtil.<List<FaceSetter>>getPrivateField(null, FaceSetter.class.getDeclaredField("faceSetters")).clear();
        ReflectionUtil.<List<ModelSetter>>getPrivateField(null, ModelSetter.class.getDeclaredField("modelSetters")).clear();
        owner = factory.createNewPlayer();
        factory.createVillageFor(owner);
        contract = factory.createNewItem(CrafterMod.getContractTemplateId());
        Properties crafterModProperties = new Properties();
        crafterModProperties.setProperty("name_prefix", "");
        new CrafterMod().configure(crafterModProperties);
        CrafterMod.mod.faceSetter = new FaceSetter(CrafterTemplate::isCrafter, dbName);
        CrafterMod.mod.modelSetter = new ModelSetter(CrafterTemplate::isCrafter, dbName);
    }

    @AfterEach
    void tearDown() {
        File file = new File("./sqlite/" + dbName);
        if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
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

    private Creature getNewlyCreatedCrafter() {
        return factory.getAllCreatures().stream().filter(CrafterTemplate::isCrafter).findFirst().orElseThrow(RuntimeException::new);
    }

    private long getCrafterCount() {
        return factory.getAllCreatures().stream().filter(CrafterTemplate::isCrafter).count();
    }

    @Test
    void testSpecialCrafterTypeProperlySet() throws WorkBook.NoWorkBookOnWorker {
        for (String type : new String[] { "all_metal", "all_wood", "all_armour" }) {
            new CrafterHireQuestion(owner, contract.getWurmId()).answer(generateProperties(type));
            Creature crafter = getNewlyCreatedCrafter();
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

            assert crafterType != null;
            assertEquals(crafterType, WorkBook.getWorkBookFromWorker(crafter).getCrafterType());
            Creatures.getInstance().permanentlyDelete(crafter);
        }
    }

    @Test
    void testCrafterTypeProperlySet() throws WorkBook.NoWorkBookOnWorker {
        for (int type : CrafterType.allSkills) {
            new CrafterHireQuestion(owner, contract.getWurmId()).answer(generateProperties(new Integer[] { type }));

            Creature crafter = getNewlyCreatedCrafter();
            assertEquals(new CrafterType(type), WorkBook.getWorkBookFromWorker(crafter).getCrafterType());
            Creatures.getInstance().permanentlyDelete(crafter);
        }
    }

    @Test
    void testCrafterNotCreatedIfNoTypeSet() {
        new CrafterHireQuestion(owner, contract.getWurmId()).answer(generateProperties(""));

        assertEquals(0, getCrafterCount());
        assertThat(owner, receivedMessageContaining("You must select"));
    }

    @Test
    void testGenderProperlySet() {
        Properties properties = generateProperties();
        properties.setProperty("gender", "female");
        new CrafterHireQuestion(owner, contract.getWurmId()).answer(properties);

        assertEquals((byte)1, getNewlyCreatedCrafter().getSex());
    }

    @Test
    void testNameProperlySet() {
        Properties properties = generateProperties();
        properties.setProperty("name", "Alfred");
        new CrafterHireQuestion(owner, contract.getWurmId()).answer(properties);

        assertEquals("Alfred", getNewlyCreatedCrafter().getName());
    }

    @Test
    void testNameWithPrefixProperlySet() {
        Properties crafterModProperties = new Properties();
        crafterModProperties.setProperty("name_prefix", "APrefix");
        new CrafterMod().configure(crafterModProperties);
        Properties properties = generateProperties();
        properties.setProperty("name", "Alfred");
        new CrafterHireQuestion(owner, contract.getWurmId()).answer(properties);

        assertEquals("APrefix_Alfred", getNewlyCreatedCrafter().getName());
    }

    @Test
    void testSkillCapProperlySet() throws WorkBook.NoWorkBookOnWorker {
        int skillCap = 75;
        Properties properties = generateProperties();
        properties.setProperty("skill_cap", Integer.toString(skillCap));
        new CrafterHireQuestion(owner, contract.getWurmId()).answer(properties);

        assertEquals(skillCap, WorkBook.getWorkBookFromWorker(getNewlyCreatedCrafter()).getSkillCap());
    }

    @Test
    void testSkillCapTooLow() throws WorkBook.NoWorkBookOnWorker {
        float skillCap = 19.99f;
        Properties properties = generateProperties();
        properties.setProperty("skill_cap", Float.toString(skillCap));
        new CrafterHireQuestion(owner, contract.getWurmId()).answer(properties);

        assertEquals(1, getCrafterCount());
        assertThat(owner, receivedMessageContaining("cap was too low"));
        assertEquals(20, WorkBook.getWorkBookFromWorker(getNewlyCreatedCrafter()).getSkillCap());
    }

    @Test
    void testSkillCapTooHigh() throws WorkBook.NoWorkBookOnWorker {
        int skillCap = 101;
        Properties properties = generateProperties();
        properties.setProperty("skill_cap", Integer.toString(skillCap));
        new CrafterHireQuestion(owner, contract.getWurmId()).answer(properties);

        assertEquals(1, getCrafterCount());
        assertThat(owner, receivedMessageContaining("was too high"));
        assertEquals(CrafterMod.getSkillCap(), WorkBook.getWorkBookFromWorker(getNewlyCreatedCrafter()).getSkillCap());
    }

    @Test
    void testSkillCapInvalid() {
        Properties properties = generateProperties();
        properties.setProperty("skill_cap", "abc");
        new CrafterHireQuestion(owner, contract.getWurmId()).answer(properties);

        assertEquals(0, getCrafterCount());
        assertThat(owner, receivedMessageContaining("was invalid"));
    }

    @Test
    void testSkillCapOnlyMessageWhenAboveMaxItemQL() throws WorkBook.NoWorkBookOnWorker, NoSuchFieldException, IllegalAccessException {
        int skillCap = 75;
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("maxItemQL"), (float)(skillCap - 1));
        Properties properties = generateProperties();
        properties.setProperty("skill_cap", Integer.toString(skillCap));
        new CrafterHireQuestion(owner, contract.getWurmId()).answer(properties);

        assertThat(owner, receivedMessageContaining("higher than the maximum item ql"));
        assertEquals(skillCap, WorkBook.getWorkBookFromWorker(getNewlyCreatedCrafter()).getSkillCap());
    }

    @Test
    void testPriceModifierProperlySet() {
        float priceModifier = 0.5f;
        Properties properties = generateProperties();
        properties.setProperty("price_modifier", Float.toString(priceModifier));
        new CrafterHireQuestion(owner, contract.getWurmId()).answer(properties);

        assertEquals(priceModifier, getNewlyCreatedCrafter().getShop().getPriceModifier());
    }

    @Test
    void testPriceModifierTooLow() throws NoSuchFieldException, IllegalAccessException {
        float priceModifier = 0.5f;
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("minimumPriceModifier"), priceModifier + 0.1f);
        Properties properties = generateProperties();
        properties.setProperty("price_modifier", Float.toString(priceModifier));
        new CrafterHireQuestion(owner, contract.getWurmId()).answer(properties);

        assertEquals(1, getCrafterCount());
        assertThat(owner, receivedMessageContaining("modifier was too low"));
        assertEquals(CrafterMod.getMinimumPriceModifier(), getNewlyCreatedCrafter().getShop().getPriceModifier());
    }

    @Test
    void testNewCrafterAddedToVillage() {
        new CrafterHireQuestion(owner, contract.getWurmId()).answer(generateProperties());

        Creature newCrafter = getNewlyCreatedCrafter();
        assertEquals(owner.citizenVillage, newCrafter.citizenVillage);
    }

    @Test
    void testNewCrafterNotAddedToVillageIfOwnerHasNoVillage() {
        Arrays.stream(Villages.getVillages()).forEach(v -> v.disband("upkeep"));
        new CrafterHireQuestion(factory.createNewPlayer(), contract.getWurmId()).answer(generateProperties());

        Creature newCrafter = getNewlyCreatedCrafter();
        assertNull(newCrafter.citizenVillage);
    }

    @Test
    void testHiringWithoutPriceModifier() {
        Properties properties = generateProperties();
        properties.remove("price_modifier");

        assertDoesNotThrow(() -> new CrafterHireQuestion(owner, contract.getWurmId()).answer(properties));
    }

    @Test
    void testGetSkillBMLUsesProvidedSkillCap() {
        BML bml = new BMLBuilder(1);
        float skillCap = 15;
        bml = new MultipleSkillsBML().addBML(bml, new CrafterType(CrafterType.allMetal), skillCap);
        assertTrue(bml.build().contains("input{text=\"" + skillCap + "\";id=\"skill_cap\""));

        bml = new BMLBuilder(1);
        skillCap = 33;
        bml = new MultipleSkillsBML().addBML(bml, new CrafterType(CrafterType.allMetal), skillCap);
        assertTrue(bml.build().contains("input{text=\"" + skillCap + "\";id=\"skill_cap\""));
    }

    @Test
    void testHireWithEmptySkillCap() {
        Properties properties = generateProperties();
        properties.remove("skill_cap");
        new CrafterHireQuestion(owner, contract.getWurmId()).answer(properties);

        assertEquals(1, getCrafterCount());
        assertThat(owner, didNotReceiveMessageContaining("invalid"));
    }

    @Test
    void testMultipleSkillsBMLAdded() throws NoSuchFieldException, IllegalAccessException {
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("singleSkill"), false);
        new CrafterHireQuestion(owner, contract.getWurmId()).sendQuestion();
        String bml = new MultipleSkillsBML().addBML(new BMLBuilder(0), new CrafterType(), CrafterMod.getSkillCap()).build();
        bml = bml.replaceAll("border\\{center\\{text\\{type=\"bold\";text=\"\"}};null;scroll\\{vertical=\"true\";horizontal=\"false\";varray\\{rescale=\"true\";passthrough\\{id=\"id\";text=\"0\"}", "");
        bml = bml.replaceAll("}}};null;null;}", "");

        assertThat(owner, receivedBMLContaining(bml));
    }

    @Test
    void testSingleSkillsBMLAdded() throws NoSuchFieldException, IllegalAccessException {
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("singleSkill"), true);
        new CrafterHireQuestion(owner, contract.getWurmId()).sendQuestion();
        String bml = new SingleSkillBML().addBML(new BMLBuilder(0), new CrafterType(), CrafterMod.getSkillCap()).build();
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

        new CrafterHireQuestion(owner, contract.getWurmId()).answer(properties);
    }

    @Test
    void testMultipleSkillsSetProperly() throws WorkBook.NoWorkBookOnWorker, NoSuchFieldException, IllegalAccessException {
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("singleSkill"), false);
        int skill = SkillList.SMITHING_GOLDSMITHING;
        Integer[] skills = new Integer[] { SkillList.CARPENTRY, SkillList.CARPENTRY_FINE };
        createCrafterWithSkillsMultipleAndSingle(skill, skills);

        Creature crafter = getNewlyCreatedCrafter();
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

        Creature crafter = getNewlyCreatedCrafter();
        CrafterType crafterType = WorkBook.getWorkBookFromWorker(crafter).getCrafterType();
        Integer[] setSkills = crafterType.getAllTypes();
        assertEquals(1, setSkills.length);
        assertEquals(skill, (int)setSkills[0]);
    }

    @Test
    void testCustomiseAppearanceSent() {
        Properties properties = generateProperties();
        properties.setProperty("customise", "true");
        new CrafterHireQuestion(owner, contract.getWurmId()).answer(properties);

        assertEquals(1, getCrafterCount());
        assertThat(owner, didNotReceiveMessageContaining("invalid"));
        new CreatureCustomiserQuestion(owner, getNewlyCreatedCrafter(), CrafterMod.mod.faceSetter, CrafterMod.mod.modelSetter, modelOptions).sendQuestion();
        assertThat(owner, bmlEqual());
    }

    @Test
    void testCustomiseAppearanceNotSentIfFalse() {
        Properties properties = generateProperties();
        properties.setProperty("customise", "false");
        new CrafterHireQuestion(owner, contract.getWurmId()).answer(properties);

        assertEquals(1, getCrafterCount());
        assertThat(owner, didNotReceiveMessageContaining("invalid"));
        assertEquals(0, factory.getCommunicator(owner).getBml().length);
    }
}
