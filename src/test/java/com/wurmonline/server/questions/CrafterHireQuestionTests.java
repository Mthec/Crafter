package com.wurmonline.server.questions;

import com.wurmonline.server.behaviours.BehaviourDispatcher;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.Creatures;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.zones.Zones;
import mod.wurmunlimited.CrafterObjectsFactory;
import mod.wurmunlimited.bml.BML;
import mod.wurmunlimited.bml.BMLBuilder;
import mod.wurmunlimited.npcs.CrafterMod;
import mod.wurmunlimited.npcs.CrafterTemplate;
import mod.wurmunlimited.npcs.CrafterType;
import mod.wurmunlimited.npcs.WorkBook;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static mod.wurmunlimited.Assert.didNotReceiveMessageContaining;
import static mod.wurmunlimited.Assert.receivedMessageContaining;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class CrafterHireQuestionTests {
    private static final String[] allCrafterTypes = new String[] {
            String.valueOf(SkillList.SMITHING_BLACKSMITHING),
            String.valueOf(SkillList.GROUP_SMITHING_WEAPONSMITHING),
            String.valueOf(SkillList.SMITHING_GOLDSMITHING),
            String.valueOf(SkillList.SMITHING_ARMOUR_CHAIN),
            String.valueOf(SkillList.SMITHING_ARMOUR_PLATE),
            String.valueOf(SkillList.CARPENTRY),
            String.valueOf(SkillList.CARPENTRY_FINE),
            String.valueOf(SkillList.GROUP_FLETCHING),
            String.valueOf(SkillList.GROUP_BOWYERY),
            String.valueOf(SkillList.LEATHERWORKING),
            String.valueOf(SkillList.CLOTHTAILORING),
            String.valueOf(SkillList.STONECUTTING),
            String.valueOf(SkillList.SMITHING_SHIELDS),
            String.valueOf(SkillList.POTTERY)
    };
    private CrafterObjectsFactory factory;
    private Player owner;
    private Item contract;

    @BeforeEach
    void setUp() throws Exception {
        factory = new CrafterObjectsFactory();
        BehaviourDispatcher.reset();
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("skillCap"), 99.99999f);
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("basePrice"), 1);
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("minimumPriceModifier"), 0.0000001f);
        owner = factory.createNewPlayer();
        factory.createVillageFor(owner);
        contract = factory.createNewItem(CrafterMod.getContractTemplateId());
    }

    private Properties generateProperties() {
        return generateProperties("all_metal");
    }

    private Properties generateProperties(String crafterType) {
        Properties properties = new Properties();
        properties.setProperty("all_metal", crafterType.equals("all_metal") ? "true" : "false");
        properties.setProperty("all_wood", crafterType.equals("all_wood") ? "true" : "false");
        properties.setProperty("all_armour", crafterType.equals("all_armour") ? "true" : "false");
        for (String type : allCrafterTypes)
            properties.setProperty(type, crafterType.equals(type) ? "true" : "false");

        properties.setProperty("skill_cap", "100");
        properties.setProperty("price_modifier", "1.0");
        properties.setProperty("name", "NewCrafter");
        properties.setProperty("gender", "male");

        return properties;
    }

    private Creature getNewlyCreatedCrafter() {
        return Creatures.getInstance().getAllCreatures().stream().filter(CrafterTemplate::isCrafter).findFirst().orElseThrow(RuntimeException::new);
    }

    private long getCrafterCount() {
        return Creatures.getInstance().getAllCreatures().stream().filter(CrafterTemplate::isCrafter).count();
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
        for (String type : allCrafterTypes) {
            new CrafterHireQuestion(owner, contract.getWurmId()).answer(generateProperties(type));

            Creature crafter = getNewlyCreatedCrafter();
            assertEquals(new CrafterType(Integer.parseInt(type)), WorkBook.getWorkBookFromWorker(crafter).getCrafterType());
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
        Zones.villages.clear();
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
        bml = CrafterHireQuestion.addSkillsBML(bml, new CrafterType(CrafterType.allMetal), skillCap);
        assertTrue(bml.build().contains("input{text=\"" + skillCap + "\";id=\"skill_cap\""));

        bml = new BMLBuilder(1);
        skillCap = 33;
        bml = CrafterHireQuestion.addSkillsBML(bml, new CrafterType(CrafterType.allMetal), skillCap);
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
}
