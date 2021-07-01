package com.wurmonline.server.questions;

import com.wurmonline.server.Constants;
import com.wurmonline.server.behaviours.BehaviourDispatcher;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.CreatureTemplateFactory;
import com.wurmonline.server.creatures.FakeCreatureStatus;
import com.wurmonline.server.economy.Economy;
import com.wurmonline.server.economy.Shop;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.zones.VolaTile;
import com.wurmonline.server.zones.Zones;
import com.wurmonline.shared.util.StringUtilities;
import mod.wurmunlimited.CrafterObjectsFactory;
import mod.wurmunlimited.npcs.*;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.wurmonline.server.questions.CrafterHireQuestion.modelOptions;
import static mod.wurmunlimited.Assert.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CrafterManagementQuestionTests {
    private static final String dbName = "crafter.db";
    private CrafterObjectsFactory factory;
    private Player owner;
    private Creature crafter;

    @BeforeEach
    void setUp() throws Exception {
        Constants.dbHost = ".";
        factory = new CrafterObjectsFactory();
        BehaviourDispatcher.reset();
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("skillCap"), 99.99999f);
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("basePrice"), 1);
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("minimumPriceModifier"), 0.0000001f);
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("paymentOption"), CrafterMod.PaymentOption.for_owner);
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("canChangeSkill"), true);
        ReflectionUtil.<List<FaceSetter>>getPrivateField(null, FaceSetter.class.getDeclaredField("faceSetters")).clear();
        ReflectionUtil.<List<ModelSetter>>getPrivateField(null, ModelSetter.class.getDeclaredField("modelSetters")).clear();
        owner = factory.createNewPlayer();
        crafter = factory.createNewCrafter(owner, new CrafterType(CrafterType.allMetal), 50);
        factory.createVillageFor(owner, crafter);
        Item contract = factory.createNewItem(CrafterMod.getContractTemplateId());
        contract.setData(crafter.getWurmId());
        owner.getInventory().insertItem(contract);
        setPrefix("");
        new CrafterMod();
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

    private void setFakeCrafter() {
        try {
            crafter = mock(Creature.class);
            AtomicReference<String> name = new AtomicReference<>("Dave");
            when(crafter.getName()).thenAnswer(i -> name.get());
            doAnswer((Answer<Void>)i -> {
                name.set(i.getArgument(0));
                return null;
            }).when(crafter).setName(anyString());
            when(crafter.getFace()).thenAnswer(i -> CrafterMod.mod.faceSetter.getFaceFor(crafter));
            when(crafter.getWurmId()).thenReturn(987654L);
            VolaTile tile = Zones.getOrCreateTile(10, 10, true);
            when(crafter.getCurrentTile()).thenReturn(tile);
            when(crafter.getTileX()).thenReturn(10);
            when(crafter.getTileY()).thenReturn(10);
            when(crafter.isOnSurface()).thenReturn(true);
            when(crafter.getTemplate()).thenReturn(CreatureTemplateFactory.getInstance().getTemplate("crafter"));
            FakeCreatureStatus status = new FakeCreatureStatus(crafter);
            when(crafter.getStatus()).thenReturn(status);
            AtomicBoolean dead = new AtomicBoolean(false);
            when(crafter.isDead()).thenAnswer(i -> dead.get());
            doAnswer(i -> {
                dead.set(true);
                return null;
            }).when(crafter).destroy();
            when(crafter.getHisHerItsString()).thenReturn("their");
            CrafterAIData fakeData = mock(CrafterAIData.class);
            when(crafter.getCreatureAIData()).thenReturn(fakeData);
            when(fakeData.getWorkBook()).thenReturn(WorkBook.createNewWorkBook(new CrafterType(), 20));
            Shop fakeShop = mock(Shop.class);
            when(crafter.getShop()).thenReturn(fakeShop);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // sendQuestion

    @Test
    public void testNameWithPrefixCorrectlySet() throws SQLException {
        setPrefix("Crafter");
        crafter.setName("Crafter_Dave");
        new CrafterManagementQuestion(owner, crafter).sendQuestion();
        assertThat(owner, receivedBMLContaining("input{text=\"Dave\";id=\"name\";maxchars=\"" + CrafterMod.maxNameLength + "\"}"));
    }

    @Test
    public void testNameWithBlankPrefixCorrectlySet() throws SQLException {
        assert CrafterMod.getNamePrefix().isEmpty();
        crafter.setName("Crafter_Dave");
        new CrafterManagementQuestion(owner, crafter).sendQuestion();
        assertThat(owner, receivedBMLContaining("input{text=\"Crafter_Dave\";id=\"name\";maxchars=\"" + CrafterMod.maxNameLength + "\"}"));
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

    private void setPrefix(String prefix) {
        Properties crafterModProperties = new Properties();
        crafterModProperties.setProperty("name_prefix", prefix);
        new CrafterMod().configure(crafterModProperties);
    }

    @Test
    void testSetName() {
        assert CrafterMod.getNamePrefix().equals("");
        String name = StringUtilities.raiseFirstLetter("MyName");
        Properties properties = new Properties();
        properties.setProperty("confirm", "true");
        properties.setProperty("name", name);
        new CrafterManagementQuestion(owner, crafter).answer(properties);

        assertEquals(name, crafter.getName());
        assertEquals(name, ((FakeCreatureStatus)crafter.getStatus()).savedName);
        assertThat(owner, receivedMessageContaining("will now be known as " + name));
    }

    @Test
    void testWithPrefix() {
        setPrefix("MyPrefix");
        String name = crafter.getName();
        Properties properties = new Properties();
        properties.setProperty("confirm", "true");
        properties.setProperty("name", name);
        new CrafterManagementQuestion(owner, crafter).answer(properties);

        assertEquals("MyPrefix_" + name, crafter.getName());
        assertThat(owner, receivedMessageContaining("will now be known as MyPrefix_" + name));
    }

    @Test
    void testSetNameDifferentPrefix() {
        setPrefix("MyPrefix");
        String name = "Dave";
        crafter.setName("Trader_" + name);
        Properties properties = new Properties();
        properties.setProperty("confirm", "true");
        properties.setProperty("name", name);
        new CrafterManagementQuestion(owner, crafter).answer(properties);

        assertEquals("MyPrefix_" + name, crafter.getName());
        assertThat(owner, receivedMessageContaining("will now be known as MyPrefix_" + name));
    }

    @Test
    void testSetNameIllegalCharacters() {
        setPrefix("Crafter");
        String name = crafter.getName();
        Properties properties = new Properties();
        properties.setProperty("confirm", "true");
        properties.setProperty("name", "%Name");
        new CrafterManagementQuestion(owner, crafter).answer(properties);

        assertEquals(name, crafter.getName());
        assertThat(owner, receivedMessageContaining("shall remain " + name));
    }

    @Test
    void testSetNameNoMessageOnSameName() {
        setPrefix("Trader");
        String name = "Name";
        crafter.setName("Trader_" + name);
        Properties properties = new Properties();
        properties.setProperty("confirm", "true");
        properties.setProperty("name", name);
        new CrafterManagementQuestion(owner, crafter).answer(properties);

        assertEquals("Trader_" + name, crafter.getName());
        assertThat(owner, didNotReceiveMessageContaining("will now be known as " + name));
        assertThat(owner, didNotReceiveMessageContaining("will remain " + name));
    }

    private void answer(@Nullable String face) {
        Properties properties = new Properties();
        properties.setProperty("face", face != null ? face : Long.toString(98765));

        new CrafterManagementQuestion(owner, crafter).answer(properties);
    }

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

    @Test
    void testModifySkillsQuestionCreated() {
        Properties properties = new Properties();
        properties.setProperty("skills", "true");
        new CrafterManagementQuestion(owner, crafter).answer(properties);
        new CrafterModifySkillsQuestion(owner, crafter).sendQuestion();

        assertThat(owner, bmlEqual());
    }

    @Test
    void testRestrictedMaterialsQuestionCreated() throws WorkBook.NoWorkBookOnWorker {
        Properties properties = new Properties();
        properties.setProperty("restrict", "true");
        new CrafterManagementQuestion(owner, crafter).answer(properties);
        new CrafterMaterialRestrictionQuestion(owner, crafter).sendQuestion();

        assertThat(owner, bmlEqual());
    }

    @Test
    void testWhenCanChangeSkillIsFalseModifySkillsButtonDoesNotShow() throws NoSuchFieldException, IllegalAccessException {
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("canChangeSkill"), false);
        new CrafterManagementQuestion(owner, crafter).sendQuestion();

        assertFalse(factory.getCommunicator(owner).lastBmlContent.contains("Modify skills"));
    }

    @Test
    void testWhenCanChangeSkillIsTrueModifySkillsButtonDoesShow() {
        assert CrafterMod.canChangeSkill();
        new CrafterManagementQuestion(owner, crafter).sendQuestion();

        assertTrue(factory.getCommunicator(owner).lastBmlContent.contains("Modify skills"));
    }

    @Test
    void testWhenNoJobsStopButtonDoesNotShow() throws WorkBook.NoWorkBookOnWorker {
        assert WorkBook.getWorkBookFromWorker(crafter).todo() == 0;
        new CrafterManagementQuestion(owner, crafter).sendQuestion();

        assertFalse(factory.getCommunicator(owner).lastBmlContent.contains("Stop current job"));
    }

    @Test
    void testWhenOnlyDoneJobsStopButtonDoesNotShow() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        workBook.addJob(1, factory.createNewItem(), 1, false, 100);
        ReflectionUtil.callPrivateMethod(workBook, WorkBook.class.getDeclaredMethod("setDone", Job.class, Creature.class), workBook.iterator().next(), crafter);
        new CrafterManagementQuestion(owner, crafter).sendQuestion();

        assertFalse(factory.getCommunicator(owner).lastBmlContent.contains("Stop current job"));
    }

    @Test
    void testWhenJobsStopButtonDoesShow() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        WorkBook.getWorkBookFromWorker(crafter).addJob(1, factory.createNewItem(), 1, false, 100);
        new CrafterManagementQuestion(owner, crafter).sendQuestion();

        assertTrue(factory.getCommunicator(owner).lastBmlContent.contains("Stop current job"));
    }

    @Test
    void testStopButtonSelected() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        workBook.addJob(1, factory.createNewItem(), 1, false, 100);
        CrafterManagementQuestion question = new CrafterManagementQuestion(owner, crafter);
        Properties properties = new Properties();
        properties.setProperty("stop", "true");
        question.answer(properties);

        assertEquals(0, workBook.todo());
        assertThat(owner, receivedMessageContaining("successfully refunded"));
    }

    @Test
    void testStopButtonSelectedOnlyOneJobRemoved() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        workBook.addJob(1, factory.createNewItem(), 1, false, 100);
        workBook.addJob(2, factory.createNewItem(), 2, false, 100);
        CrafterManagementQuestion question = new CrafterManagementQuestion(owner, crafter);
        Properties properties = new Properties();
        properties.setProperty("stop", "true");
        question.answer(properties);

        assertEquals(1, workBook.todo());
        assertThat(owner, receivedMessageContaining("successfully refunded"));
    }

    @Test
    void testCustomiseAppearanceSent() {
        Properties properties = new Properties();
        properties.setProperty("customise", "true");
        new CrafterManagementQuestion(owner, crafter).answer(properties);

        new CreatureCustomiserQuestion(owner, crafter, CrafterMod.mod.faceSetter, CrafterMod.mod.modelSetter, modelOptions).sendQuestion();
        assertThat(owner, bmlEqual());
    }
}
