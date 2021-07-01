package mod.wurmunlimited.npcs;

import com.wurmonline.server.Constants;
import com.wurmonline.server.Servers;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.creatures.CrafterTradeHandler;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.CreatureTemplate;
import com.wurmonline.server.creatures.CreatureTemplateIds;
import com.wurmonline.server.items.*;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.questions.CrafterHireQuestion;
import com.wurmonline.server.questions.CreatureCreationQuestion;
import com.wurmonline.server.questions.Question;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import mod.wurmunlimited.CrafterObjectsFactory;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Properties;

import static mod.wurmunlimited.Assert.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CrafterModTests {
    private CrafterObjectsFactory factory;
    private CrafterType crafterType;

    @BeforeEach
    void setUp() throws Exception {
        factory = new CrafterObjectsFactory();
        Constants.dbHost = ".";
        CrafterAI.assignedForges.clear();
        crafterType = new CrafterType(SkillList.SMITHING_BLACKSMITHING);
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("removeDonationsAt"), Integer.MIN_VALUE);
    }

    @Test
    void testBlockOpenActionOnAssignedForges() throws Throwable {
        CrafterMod crafterMod = new CrafterMod();
        Creature player = factory.createNewPlayer();
        Creature crafter = factory.createNewCrafter(factory.createNewPlayer(), crafterType, 20);
        Item forge = factory.createNewItem(ItemList.forge);
        ((CrafterAIData)crafter.getCreatureAIData()).setForge(forge);

        InvocationHandler handler = crafterMod::behaviourDispatcher;
        Method method = mock(Method.class);
        Object[] args = new Object[] { player, player.getCommunicator(), 1, forge.getWurmId(), Actions.OPEN };

        assertNull(handler.invoke(null, method, args));
        verify(method, never()).invoke(any(), any());
    }

    @Test
    void testStillBlockOpenActionOnAssignedForgesAfterServerLoad() throws Throwable {
        CrafterMod crafterMod = new CrafterMod();
        Creature player = factory.createNewPlayer();
        Creature crafter = factory.createNewCrafter(factory.createNewPlayer(), crafterType, 20);
        Item forge = factory.createNewItem(ItemList.forge);
        ((CrafterAIData)crafter.getCreatureAIData()).setForge(forge);

        CrafterAI.assignedForges.clear();
        ReflectionUtil.setPrivateField(crafter, Creature.class.getDeclaredField("aiData"), null);
        crafter.getCreatureAIData();

        InvocationHandler handler = crafterMod::behaviourDispatcher;
        Method method = mock(Method.class);
        Object[] args = new Object[] { player, player.getCommunicator(), 1, forge.getWurmId(), Actions.OPEN };

        assertNull(handler.invoke(null, method, args));
        verify(method, never()).invoke(any(), any());
    }

    @Test
    void testDoesNotBlockOpenActionOnUnassignedForges() throws Throwable {
        CrafterMod crafterMod = new CrafterMod();
        Creature player = factory.createNewPlayer();
        Item forge = factory.createNewItem(ItemList.forge);
        assert !CrafterAI.assignedForges.containsValue(forge);

        InvocationHandler handler = crafterMod::behaviourDispatcher;
        Method method = mock(Method.class);
        Object[] args = new Object[] { player, player.getCommunicator(), 1, forge.getWurmId(), Actions.OPEN };

        assertDoesNotThrow(() -> handler.invoke(null, method, args));
        verify(method, times(1)).invoke(null, args);
    }

    @Test
    void testDoesNotBlockOpenActionOnAssignedForgesForGMs() throws Throwable {
        CrafterMod crafterMod = new CrafterMod();
        Player player = factory.createNewPlayer();
        Creature crafter = factory.createNewCrafter(factory.createNewPlayer(), crafterType, 20);
        Item forge = factory.createNewItem(ItemList.forge);
        ((CrafterAIData)crafter.getCreatureAIData()).setForge(forge);
        player.setPower((byte)2);

        InvocationHandler handler = crafterMod::behaviourDispatcher;
        Method method = mock(Method.class);
        Object[] args = new Object[] { player, player.getCommunicator(), 1, forge.getWurmId(), Actions.OPEN };

        assertNull(handler.invoke(null, method, args));
        verify(method, times(1)).invoke(null, args);
        assertThat(player, receivedMessageContaining("This forge is assigned"));
    }

    @Test
    void testDoesNotBlockOtherActionsOnForges() throws Throwable {
        CrafterMod crafterMod = new CrafterMod();
        Creature player = factory.createNewPlayer();
        Creature crafter = factory.createNewCrafter(factory.createNewPlayer(), crafterType, 20);
        Item forge = factory.createNewItem(ItemList.forge);
        ((CrafterAIData)crafter.getCreatureAIData()).setForge(forge);

        InvocationHandler handler = crafterMod::behaviourDispatcher;
        Method method = mock(Method.class);
        Object[] args = new Object[] { player, player.getCommunicator(), 1, forge.getWurmId(), Actions.EXAMINE };

        assertDoesNotThrow(() -> handler.invoke(null, method, args));
        verify(method, times(1)).invoke(null, args);
    }

    @Test
    void testDoesNotBlockOpenActionOnNotForges() throws Throwable {
        CrafterMod crafterMod = new CrafterMod();
        Creature player = factory.createNewPlayer();
        Item chest = factory.createNewItem(ItemList.chestLarge);

        InvocationHandler handler = crafterMod::behaviourDispatcher;
        Method method = mock(Method.class);
        Object[] args = new Object[] { player, player.getCommunicator(), 1, chest.getWurmId(), Actions.OPEN };

        assertDoesNotThrow(() -> handler.invoke(null, method, args));
        verify(method, times(1)).invoke(null, args);
    }

    @Test
    void testGetTradeHandlerNormal() throws Throwable {
        CrafterMod crafterMod = new CrafterMod();
        Creature merchant = factory.createNewMerchant(factory.createNewPlayer());

        InvocationHandler handler = crafterMod::getTradeHandler;
        Method method = mock(Method.class);
        Object[] args = new Object[0];

        assertNull(handler.invoke(merchant, method, args));
        verify(method, times(1)).invoke(merchant, args);
    }

    @Test
    void testGetTradeHandlerCrafter() throws Throwable {
        CrafterMod crafterMod = new CrafterMod();
        Creature crafter = factory.createNewCrafter(factory.createNewPlayer(), crafterType, 20);
        Creature player = factory.createNewPlayer();

        InvocationHandler handler = crafterMod::getTradeHandler;
        Method method = mock(Method.class);
        Object[] args = new Object[0];

        CrafterTrade trade = new CrafterTrade(player, crafter);
        player.setTrade(trade);
        crafter.setTrade(trade);
        assertTrue(handler.invoke(crafter, method, args) instanceof CrafterTradeHandler);
        verify(method, never()).invoke(crafter, args);
    }

    @Test
    void testSwapOwners() throws Throwable {
        CrafterMod crafterMod = new CrafterMod();
        Player owner = factory.createNewPlayer();
        Player player = factory.createNewPlayer();
        Creature crafter = factory.createNewCrafter(owner, new CrafterType(CrafterType.allMetal), 10);
        Item contract = factory.createNewItem(CrafterMod.getContractTemplateId());
        owner.getInventory().insertItem(contract);
        contract.setData(crafter.getWurmId());
        factory.getShop(crafter).setOwner(owner.getWurmId());

        Trade trade = new Trade(owner, player);
        owner.setTrade(trade);
        player.setTrade(trade);
        TradingWindow window = trade.getTradingWindow(4);
        window.addItem(contract);

        InvocationHandler handler = crafterMod::swapOwners;
        Method method = mock(Method.class);
        Object[] args = new Object[0];

        assertNull(handler.invoke(window, method, args));
        verify(method, times(1)).invoke(window, args);
        assertEquals(contract.getData(), crafter.getWurmId());
        assertEquals(player.getWurmId(), factory.getShop(crafter).getOwnerId());
    }

    @Test
    void testSwapOwnersBlankContract() throws Throwable {
        CrafterMod crafterMod = new CrafterMod();
        Player owner = factory.createNewPlayer();
        Player player = factory.createNewPlayer();
        Item contract = factory.createNewItem(CrafterMod.getContractTemplateId());
        owner.getInventory().insertItem(contract);

        Trade trade = new Trade(owner, player);
        owner.setTrade(trade);
        player.setTrade(trade);
        TradingWindow window = trade.getTradingWindow(4);
        window.addItem(contract);

        InvocationHandler handler = crafterMod::swapOwners;
        Method method = mock(Method.class);
        Object[] args = new Object[0];

        assertNull(handler.invoke(window, method, args));
        verify(method, times(1)).invoke(window, args);
        assertEquals(contract.getData(), -1);
    }

    @Test
    void testJobItemsRemovedFromInventoryDuringWearItems() throws Throwable {
        CrafterMod crafterMod = new CrafterMod();
        com.wurmonline.server.creatures.Creature crafter = factory.createNewCrafter(factory.createNewPlayer(), crafterType, 50);
        Item job = factory.createNewItem();
        crafter.getInventory().insertItem(job);
        WorkBook.getWorkBookFromWorker(crafter).addJob(1, job, 10, false, 1);
        crafter.getInventory().insertItem(factory.createNewItem(ItemList.plateJacket));
        crafter.getInventory().insertItem(factory.createNewItem(ItemList.hammerMetal));

        InvocationHandler handler = crafterMod::wearItems;
        Method method = mock(Method.class);
        Object[] args = new Object[0];
        when(method.invoke(crafter, args)).thenAnswer((Answer<Void>)i -> {
            for (Item item : crafter.getInventory().getItems()) {
                assertNotEquals(job, item);
            }
            return null;
        });

        assertNull(handler.invoke(crafter, method, args));
        verify(method, times(1)).invoke(crafter, args);
    }

    @Test
    void testCrafterSkillGainRateApplied() throws Throwable {
        CrafterMod crafterMod = new CrafterMod();
        ReflectionUtil.setPrivateField(crafterMod, CrafterMod.class.getDeclaredField("crafterSkillGainRate"), 5f);
        when(Servers.localServer.getSkillGainRate()).thenReturn(1f);
        Creature creature = factory.createNewCrafter(factory.createNewPlayer(), crafterType, 50);

        InvocationHandler handler = crafterMod::alterSkill;
        Skill skill = creature.getSkills().getSkill(SkillList.SMITHING_BLACKSMITHING);
        Method method = mock(Method.class);
        Object[] args = new Object[] { 2d, false, 2f, false, 0d };
        Object[] expectedArgs = new Object[] { 10d, false, 2f, false, 0d };

        assertNull(handler.invoke(skill, method, args));
        verify(method, times(1)).invoke(skill, expectedArgs);
    }

    @Test
    void testCrafterSkillGainRateNotAppliedIfNotSet() throws Throwable {
        CrafterMod crafterMod = new CrafterMod();
        ReflectionUtil.setPrivateField(crafterMod, CrafterMod.class.getDeclaredField("crafterSkillGainRate"), 1f);
        when(Servers.localServer.getSkillGainRate()).thenReturn(2f);
        Creature creature = factory.createNewCrafter(factory.createNewPlayer(), crafterType, 50);

        InvocationHandler handler = crafterMod::alterSkill;
        Skill skill = creature.getSkills().getSkill(SkillList.SMITHING_BLACKSMITHING);
        Method method = mock(Method.class);
        Object[] args = new Object[] { 2d, false, 1f, false, 0d };
        Object[] expectedArgs = new Object[] { 2d, false, 1f, false, 0d };

        assertNull(handler.invoke(skill, method, args));
        verify(method, times(1)).invoke(skill, expectedArgs);
    }

    @Test
    void testNormalSkillGainRateNotAppliedToCraftersIfCrafterSkillGainRateIsSet() throws Throwable {
        CrafterMod crafterMod = new CrafterMod();
        ReflectionUtil.setPrivateField(crafterMod, CrafterMod.class.getDeclaredField("crafterSkillGainRate"), 1f);
        when(Servers.localServer.getSkillGainRate()).thenReturn(10f);
        Creature creature = factory.createNewCrafter(factory.createNewPlayer(), crafterType, 50);

        InvocationHandler handler = crafterMod::alterSkill;
        Skill skill = creature.getSkills().getSkill(SkillList.SMITHING_BLACKSMITHING);
        Method method = mock(Method.class);
        Object[] args = new Object[] { 1d, false, 1f, false, 0d };
        Object[] expectedArgs = new Object[] { 1d, false, 1f, false, 0d };

        assertNull(handler.invoke(skill, method, args));
        verify(method, times(1)).invoke(skill, expectedArgs);
    }

    @Test
    void testCrafterSkillGainRateNotAppliedToOtherCreatures() throws Throwable {
        CrafterMod crafterMod = new CrafterMod();
        ReflectionUtil.setPrivateField(crafterMod, CrafterMod.class.getDeclaredField("crafterSkillGainRate"), 5f);
        when(Servers.localServer.getSkillGainRate()).thenReturn(2f);
        Creature creature = factory.createNewCreature(CreatureTemplateIds.HORSE_CID);

        InvocationHandler handler = crafterMod::alterSkill;
        Skill skill = creature.getSkills().getSkill(SkillList.SMITHING_BLACKSMITHING);
        Method method = mock(Method.class);
        Object[] args = new Object[] { 2d, false, 1f, false, 0d };
        Object[] expectedArgs = new Object[] { 2d, false, 1f, false, 0d };

        assertNull(handler.invoke(skill, method, args));
        verify(method, times(1)).invoke(skill, expectedArgs);
    }

    @Test
    void testDestroyDonationItemNoValueSet() {
        for (int i = 1; i < 101; i++) {
            assertFalse(CrafterMod.destroyDonationItem(i, 10));
        }
    }

    @Test
    void testDestroyDonationItemPositiveValue() throws NoSuchFieldException, IllegalAccessException {
        int plusCap = 10;
        Properties properties = new Properties();
        properties.setProperty("remove_donations_at", String.valueOf(plusCap));
        new CrafterMod().configure(properties);
        assert (int)ReflectionUtil.getPrivateField(null, CrafterMod.class.getDeclaredField("removeDonationsAt")) == plusCap;

        assertFalse(CrafterMod.destroyDonationItem(10, 19));
        assertTrue(CrafterMod.destroyDonationItem(10, 20));
    }

    @Test
    void testDestroyDonationItemNegativeValue() {
        int minusCap = -10;
        Properties properties = new Properties();
        properties.setProperty("remove_donations_at", String.valueOf(minusCap));
        new CrafterMod().configure(properties);

        assertFalse(CrafterMod.destroyDonationItem(20, 9));
        assertTrue(CrafterMod.destroyDonationItem(20, 10));
    }

    private TradingWindow newTradingWindow(Creature one, Creature two, Item contract) throws NoSuchFieldException, IllegalAccessException {
        TradingWindow window = new TradingWindow();
        ReflectionUtil.setPrivateField(window, TradingWindow.class.getDeclaredField("windowowner"), one);
        ReflectionUtil.setPrivateField(window, TradingWindow.class.getDeclaredField("watcher"), two);
        window.addItem(contract);
        return window;
    }

    @Test
    void testDoubleTradeCrafterMissingItemForCompletedJob() throws Throwable {
        CrafterMod crafterMod = new CrafterMod();
        com.wurmonline.server.creatures.Creature player1 = factory.createNewPlayer();
        Creature player2 = factory.createNewPlayer();
        Creature player3 = factory.createNewPlayer();
        Creature crafter = factory.createNewCrafter(player1, new CrafterType(SkillList.SMITHING_BLACKSMITHING), 30);

        Item contract = factory.createNewItem(CrafterMod.getContractTemplateId());
        contract.setData(crafter.getWurmId());
        player1.getInventory().insertItem(contract, true);

        Item tool = factory.createNewItem();
        tool.setQualityLevel(30);
        crafter.getInventory().insertItem(tool, true);
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        workBook.addJob(factory.createNewPlayer().getWurmId(), tool, 30, false, 100);

        InvocationHandler handler = crafterMod::swapOwners;
        Method method = mock(Method.class);
        when(method.invoke(any(), any())).then((Answer<Void>)invocation -> {
            TradingWindow window = invocation.getArgument(0);
            Item contractItem = window.getItems()[0];
            ((Creature)ReflectionUtil.getPrivateField(window, TradingWindow.class.getDeclaredField("watcher")))
                    .getInventory().insertItem(contractItem);
            contractItem.setTradeWindow(null);
            return null;
        });
        Object[] args = new Object[0];

        TradingWindow window;

        // Trade 1 - 1 > 2
        window = newTradingWindow(player1, player2, contract);
        assertNull(handler.invoke(window, method, args));
        verify(method, times(1)).invoke(window, args);
        assertEquals(player2.getWurmId(), crafter.getShop().getOwnerId());
        assertDoesNotThrow(() -> workBook.iterator().hasNext());

        // Trade 2 - 2 > 3
        window = newTradingWindow(player2, player3, contract);
        assertNull(handler.invoke(window, method, args));
        verify(method, times(1)).invoke(window, args);
        assertEquals(player3.getWurmId(), crafter.getShop().getOwnerId());
        assertDoesNotThrow(() -> workBook.iterator().hasNext());

        // Trade 3 - 3 > 2
        window = newTradingWindow(player3, player2, contract);
        assertNull(handler.invoke(window, method, args));
        verify(method, times(1)).invoke(window, args);
        assertEquals(player2.getWurmId(), crafter.getShop().getOwnerId());
        assertDoesNotThrow(() -> workBook.iterator().hasNext());

        assertEquals(1, WorkBook.getWorkBookFromWorker(crafter).todo());
        assertTrue(crafter.getInventory().getItems().contains(tool));
    }

    @Test
    void testCreatureCreation() throws Throwable {
        Player gm = factory.createNewPlayer();
        Item wand = factory.createNewItem(ItemList.wandGM);
        String name = "Name";
        int tileX = 250;
        int tileY = 250;
        int templateId = ReflectionUtil.getPrivateField(null, CrafterTemplate.class.getDeclaredField("templateId"));

        InvocationHandler handler = new CrafterMod()::creatureCreation;
        Method method = mock(Method.class);
        Object[] args = new Object[] { new CreatureCreationQuestion(gm, "", "", wand.getWurmId(), tileX, tileY, -1, -10) };
        ((CreatureCreationQuestion)args[0]).sendQuestion();
        factory.getCommunicator(gm).clear();
        int templateIndex = -1;
        int i = 0;
        for (CreatureTemplate template : ReflectionUtil.<List<CreatureTemplate>>getPrivateField(args[0], CreatureCreationQuestion.class.getDeclaredField("cretemplates"))) {
            if (template.getTemplateId() == templateId) {
                templateIndex = i;
                break;
            }
            ++i;
        }
        assert templateIndex != -1;
        Properties answers = new Properties();
        answers.setProperty("data1", String.valueOf(i));
        answers.setProperty("cname", name);
        answers.setProperty("gender", "female");
        ReflectionUtil.setPrivateField(args[0], Question.class.getDeclaredField("answer"), answers);

        assertNull(handler.invoke(null, method, args));
        verify(method, never()).invoke(null, args);
        assertThat(gm, didNotReceiveMessageContaining("An error occurred"));
        new CrafterHireQuestion(gm, gm.getInventory().getFirstContainedItem().getWurmId()).sendQuestion();
        assertThat(gm, bmlEqual());
    }

    @Test
    void testNonCustomTraderCreatureCreation() throws Throwable {
        Player gm = factory.createNewPlayer();
        Item wand = factory.createNewItem(ItemList.wandGM);
        int tileX = 250;
        int tileY = 250;

        InvocationHandler handler = new CrafterMod()::creatureCreation;
        Method method = mock(Method.class);
        Object[] args = new Object[] { new CreatureCreationQuestion(gm, "", "", wand.getWurmId(), tileX, tileY, -1, -10) };
        ((CreatureCreationQuestion)args[0]).sendQuestion();
        Properties answers = new Properties();
        answers.setProperty("data1", String.valueOf(0));
        answers.setProperty("cname", "MyName");
        answers.setProperty("gender", "female");
        ReflectionUtil.setPrivateField(args[0], Question.class.getDeclaredField("answer"), answers);

        for (Creature creature : factory.getAllCreatures().toArray(new Creature[0])) {
            factory.removeCreature(creature);
        }

        assertNull(handler.invoke(null, method, args));
        verify(method, times(1)).invoke(null, args);
        assertEquals(0, factory.getAllCreatures().size());
        assertThat(gm, didNotReceiveMessageContaining("An error occurred"));
    }

    @Test
    void testWillLeaveServerEmptyContract() throws Throwable {
        Player gm = factory.createNewPlayer();

        InvocationHandler handler = new CrafterMod()::willLeaveServer;
        Item item = factory.createNewItem(CrafterMod.getContractTemplateId());
        Method method = mock(Method.class);
        Object[] args = new Object[] { true, false, false };

        assertTrue((Boolean)handler.invoke(item, method, args));
        assertFalse(item.isTransferred());
        verify(method, never()).invoke(item, args);
    }

    @Test
    void testWillLeaveServerUsedContract() throws Throwable {
        Player gm = factory.createNewPlayer();

        InvocationHandler handler = new CrafterMod()::willLeaveServer;
        Item item = factory.createNewItem(CrafterMod.getContractTemplateId());
        item.setData(gm.getWurmId());
        Method method = mock(Method.class);
        Object[] args = new Object[] { true, false, false };

        assertFalse((Boolean)handler.invoke(item, method, args));
        assertTrue(item.isTransferred());
        verify(method, never()).invoke(item, args);
    }

    @Test
    void testWillLeaveServerNotContract() throws Throwable {
        Player gm = factory.createNewPlayer();

        InvocationHandler handler = new CrafterMod()::willLeaveServer;
        Item item = factory.createNewItem(ItemList.lunchbox);
        item.setData(gm.getWurmId());
        boolean isTransferred = item.isTransferred();
        Method method = mock(Method.class);
        Object[] args = new Object[] { true, false, false };

        assertNull(handler.invoke(item, method, args));
        assertEquals(isTransferred, item.isTransferred());
        verify(method, times(1)).invoke(item, args);
    }
}
