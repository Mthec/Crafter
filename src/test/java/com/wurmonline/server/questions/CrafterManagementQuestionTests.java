package com.wurmonline.server.questions;

import com.wurmonline.server.behaviours.BehaviourDispatcher;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.economy.Economy;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.players.Player;
import mod.wurmunlimited.CrafterObjectsFactory;
import mod.wurmunlimited.npcs.*;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static mod.wurmunlimited.Assert.receivedMessageContaining;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class CrafterManagementQuestionTests {
    private CrafterObjectsFactory factory;
    private Player owner;
    private Creature crafter;
    private Item contract;

    @BeforeEach
    void setUp() throws Exception {
        factory = new CrafterObjectsFactory();
        BehaviourDispatcher.reset();
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("skillCap"), 99.99999f);
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("basePrice"), 1);
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("minimumPriceModifier"), 0.0000001f);
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("paymentOption"), CrafterMod.PaymentOption.for_owner);
        owner = factory.createNewPlayer();
        crafter = factory.createNewCrafter(owner, new CrafterType(CrafterType.allMetal), 50);
        factory.createVillageFor(owner, crafter);
        contract = factory.createNewItem(CrafterMod.getContractTemplateId());
        contract.setData(crafter.getWurmId());
    }

    // sendQuestion

    @Test
    void testNameValueAddedCorrectlyToBML() {
        new CrafterManagementQuestion(owner, crafter).sendQuestion();
        assertTrue(factory.getCommunicator(owner).lastBmlContent.contains(String.format("text{text=\"Name - %s\"}", crafter.getName())), factory.getCommunicator(owner).lastBmlContent);
    }

    @Test
    void testCurrentJobsValueAddedCorrectlyToBML() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        assert workBook.todo() == 0;
        new CrafterManagementQuestion(owner, crafter).sendQuestion();
        assertTrue(factory.getCommunicator(owner).lastBmlContent.contains("text{text=\"Current jobs - 0\"}"), factory.getCommunicator(owner).lastBmlContent);

        workBook.addJob(1, factory.createNewItem(), 1, false, 1);
        workBook.addJob(2, factory.createNewItem(), 1, false, 1);
        workBook.addJob(3, factory.createNewItem(), 1, false, 1);
        new CrafterManagementQuestion(owner, crafter).sendQuestion();
        assertTrue(factory.getCommunicator(owner).lastBmlContent.contains("text{text=\"Current jobs - 3\"}"), factory.getCommunicator(owner).lastBmlContent);
    }

    @Test
    void testAwaitingCollectionValueAddedCorrectlyToBML() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull, NoSuchFieldException {
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        assert workBook.done() == 0;
        new CrafterManagementQuestion(owner, crafter).sendQuestion();
        assertTrue(factory.getCommunicator(owner).lastBmlContent.contains("text{text=\"Awaiting collection - 0\"}"), factory.getCommunicator(owner).lastBmlContent);

        workBook.addJob(1, factory.createNewItem(), 1, false, 1);
        workBook.addJob(2, factory.createNewItem(), 1, false, 1);
        workBook.addJob(3, factory.createNewItem(), 1, false, 1);
        Field done = Job.class.getDeclaredField("done");
        workBook.iterator().forEachRemaining(job -> {
            try {
                ReflectionUtil.setPrivateField(job, done, true);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        });
        new CrafterManagementQuestion(owner, crafter).sendQuestion();
        assertTrue(factory.getCommunicator(owner).lastBmlContent.contains("text{text=\"Awaiting collection - 3\"}"), factory.getCommunicator(owner).lastBmlContent);
    }

    @Test
    void testForgeValueAddedCorrectlyToBML() throws WorkBook.NoWorkBookOnWorker {
        assert !WorkBook.getWorkBookFromWorker(crafter).isForgeAssigned();
        new CrafterManagementQuestion(owner, crafter).sendQuestion();
        assertTrue(factory.getCommunicator(owner).lastBmlContent.contains("text{color=\"255,0,0\";text=\"Forge - Not Assigned\"}"), factory.getCommunicator(owner).lastBmlContent);

        ((CrafterAIData)crafter.getCreatureAIData()).setForge(factory.createNewItem(ItemList.forge));
        new CrafterManagementQuestion(owner, crafter).sendQuestion();
        assertTrue(factory.getCommunicator(owner).lastBmlContent.contains("text{text=\"Forge - Assigned\"}"), factory.getCommunicator(owner).lastBmlContent);
    }

    @Test
    void testMoneyToCollectValueAddedCorrectlyToBML() {
        assert Economy.getEconomy().getShop(crafter).getMoney() == 0;
        new CrafterManagementQuestion(owner, crafter).sendQuestion();
        assertTrue(factory.getCommunicator(owner).lastBmlContent.contains("text{text=\"Money to collect - Nothing\"}"), factory.getCommunicator(owner).lastBmlContent);

        factory.getShop(crafter).setMoney(1010101);
        new CrafterManagementQuestion(owner, crafter).sendQuestion();
        assertTrue(factory.getCommunicator(owner).lastBmlContent.contains("text{text=\"Money to collect - 1g, 1s, 1c, 1i\"}"), factory.getCommunicator(owner).lastBmlContent);
    }

    @Test
    void testMoneyToCollectIgnoredForSpecificPaymentOptions() {
        Properties crafterProperties = new Properties();
        crafterProperties.setProperty("payment", "tax_and_upkeep");
        new CrafterMod().configure(crafterProperties);
        new CrafterManagementQuestion(owner, crafter).sendQuestion();
        assertFalse(factory.getCommunicator(owner).lastBmlContent.contains("text{text=\"Money to collect -"), factory.getCommunicator(owner).lastBmlContent);

        crafterProperties = new Properties();
        crafterProperties.setProperty("payment", "all_tax");
        new CrafterMod().configure(crafterProperties);
        new CrafterManagementQuestion(owner, crafter).sendQuestion();
        assertFalse(factory.getCommunicator(owner).lastBmlContent.contains("text{text=\"Money to collect -"), factory.getCommunicator(owner).lastBmlContent);

        crafterProperties = new Properties();
        crafterProperties.setProperty("payment", "for_owner");
        new CrafterMod().configure(crafterProperties);
        new CrafterManagementQuestion(owner, crafter).sendQuestion();
        assertTrue(factory.getCommunicator(owner).lastBmlContent.contains("text{text=\"Money to collect -"), factory.getCommunicator(owner).lastBmlContent);
    }

    @Test
    void testPriceModifierValueAddedCorrectlyToBML() {
        new CrafterManagementQuestion(owner, crafter).sendQuestion();
        assertTrue(factory.getCommunicator(owner).lastBmlContent.contains("input{text=\"1.0\";id=\"price_modifier\";maxchars=\"4\"}"), factory.getCommunicator(owner).lastBmlContent);

        Economy.getEconomy().getShop(crafter).setPriceModifier(5.0f);
        new CrafterManagementQuestion(owner, crafter).sendQuestion();
        assertTrue(factory.getCommunicator(owner).lastBmlContent.contains("input{text=\"5.0\";id=\"price_modifier\";maxchars=\"4\"}"), factory.getCommunicator(owner).lastBmlContent);
    }

    @Test
    void testDismissButtonRequiresConfirmation() {
        // Get rid of any numbers from name.
        crafter.setName("CrafterName");
        new CrafterManagementQuestion(owner, crafter).sendQuestion();
        Matcher matcher = Pattern.compile("button\\{text=\"Dismiss\";id=\"dismiss\";confirm=\"[A-Za-z. ]+\";question=\"[A-Za-z? ]+\"}").matcher(factory.getCommunicator(owner).lastBmlContent);

        assertTrue(matcher.find(), factory.getCommunicator(owner).lastBmlContent);
    }

    // answer

    @Test
    void testPriceModifierUpdated() {
        assert Economy.getEconomy().getShop(crafter).getPriceModifier() == 1.0f;
        Properties properties = new Properties();
        properties.setProperty("price_modifier", "1.3");
        new CrafterManagementQuestion(owner, crafter).answer(properties);

        assertEquals(1.3f, Economy.getEconomy().getShop(crafter).getPriceModifier());
    }

    @Test
    void testPriceModifierTooLow() throws NoSuchFieldException, IllegalAccessException {
        float priceModifier = 0.01f;
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("minimumPriceModifier"), priceModifier + 0.1f);
        Properties properties = new Properties();
        properties.setProperty("price_modifier", Float.toString(priceModifier));
        new CrafterManagementQuestion(owner, crafter).answer(properties);

        assertThat(owner, receivedMessageContaining("modifier was too low"));
        assertEquals(CrafterMod.getMinimumPriceModifier(), crafter.getShop().getPriceModifier());
    }

    @Test
    void testPriceModifierInvalidOptions() {
        assert Economy.getEconomy().getShop(crafter).getPriceModifier() == 1.0f;
        Properties properties = new Properties();
        properties.setProperty("price_modifier", "-1.0");
        new CrafterManagementQuestion(owner, crafter).answer(properties);

        assertEquals(1.0f, Economy.getEconomy().getShop(crafter).getPriceModifier());
        assertThat(owner, receivedMessageContaining("be positive"));

        properties = new Properties();
        properties.setProperty("price_modifier", "abc");
        new CrafterManagementQuestion(owner, crafter).answer(properties);

        assertEquals(1.0f, Economy.getEconomy().getShop(crafter).getPriceModifier());
        assertThat(owner, receivedMessageContaining("be a number"));
    }

    @Test
    void testDismiss() {
        Properties properties = new Properties();
        properties.setProperty("dismiss", "true");
        new CrafterManagementQuestion(owner, crafter).answer(properties);

        assertThat(owner, receivedMessageContaining("You dismiss"));
    }

    @Test
    void testForgeUnassignedOnDismiss() {
        CrafterAI.assignedForges.put(crafter, factory.createNewItem(ItemList.forge));

        Properties properties = new Properties();
        properties.setProperty("dismiss", "true");
        new CrafterManagementQuestion(owner, crafter).answer(properties);
        assertFalse(CrafterAI.assignedForges.containsKey(crafter));
    }
}
