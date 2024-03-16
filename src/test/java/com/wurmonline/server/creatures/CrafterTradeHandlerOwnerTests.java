package com.wurmonline.server.creatures;

import com.wurmonline.server.economy.Economy;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.items.TradingWindow;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.villages.NoSuchRoleException;
import com.wurmonline.server.villages.VillageRole;
import com.wurmonline.server.villages.VillageStatus;
import com.wurmonline.shared.constants.ItemMaterials;
import mod.wurmunlimited.npcs.*;
import mod.wurmunlimited.npcs.db.CrafterDatabase;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static mod.wurmunlimited.Assert.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class CrafterTradeHandlerOwnerTests extends CrafterTradingTest {

    private void addJobToWorkBook(int price, boolean done) {
        try {
            init();
            WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
            Item tool = factory.createNewItem();
            workBook.addJob(123, tool, 1, false, price);
            crafter.getInventory().insertItem(tool);
            if (done) {
                Job job = StreamSupport.stream(workBook.spliterator(), false).filter(j -> j.getItem() == tool).findFirst().orElseThrow(RuntimeException::new);
                setJobDone(workBook, job);
            }
        } catch (WorkBook.NoWorkBookOnWorker | NoSuchMethodException | IllegalAccessException | InvocationTargetException | WorkBook.WorkBookFull e) {
            throw new RuntimeException(e);
        }
    }

    private void addTodoJobToWorkBook(int price) {
        addJobToWorkBook(price, false);
    }

    private void addDoneJobToWorkBook(int price) {
        addJobToWorkBook(price, true);
    }

    @Test
    void testCoinsAddedToTradeIfOwner() {
        int price = 200;
        addDoneJobToWorkBook(price);
        makeNewOwnerCrafterTrade();
        makeHandler();
        handler.addItemsToTrade();

        assertThat(Arrays.asList(trade.getTradingWindow(1).getItems()), containsCoinsOfValue(afterTax(price)));
    }

    @Test
    void testCoinsNotAddedToTradeIfNotOwner() {
        addDoneJobToWorkBook(200);
        makeNewCrafterTrade();
        makeHandler();
        handler.addItemsToTrade();

        assertThat(Arrays.asList(trade.getTradingWindow(1).getItems()), containsCoinsOfValue(0));
    }

    @Test
    void testCoinsRemovedIfTradeCancelled() {
        addDoneJobToWorkBook(100);
        makeNewOwnerCrafterTrade();
        makeHandler();
        handler.addItemsToTrade();

        List<Item> coins = Arrays.stream(trade.getTradingWindow(1).getItems()).filter(Item::isCoin).collect(Collectors.toList());
        assert !coins.isEmpty();

        handler.balance();
        crafter.getCommunicator().sendCloseTradeWindow();

        for (Item coin : coins) {
            assertTrue(coin.isBanked());
        }
    }

    @Test
    void testOnlyNotCollectedCoinsRemoved() {
        addDoneJobToWorkBook(110);
        makeNewOwnerCrafterTrade();
        makeHandler();
        handler.addItemsToTrade();

        List<Item> coins = Arrays.stream(trade.getTradingWindow(1).getItems()).filter(Item::isCoin).collect(Collectors.toList());
        assert coins.size() > 1;
        Item collectedCoin = coins.get(0);
        trade.getTradingWindow(1).removeItem(collectedCoin);
        trade.getTradingWindow(3).addItem(collectedCoin);

        handler.balance();
        setSatisfied(owner);

        for (Item coin : coins) {
            if (coin != collectedCoin)
                assertTrue(coin.isBanked());
            else
                assertFalse(coin.isBanked());
        }
    }

    @Test
    void testCoinsOnlyAddedForCompletedJobs() {
        int todo = 50;
        int done = 100;
        addTodoJobToWorkBook(todo);
        addDoneJobToWorkBook(done);
        makeNewOwnerCrafterTrade();
        makeHandler();
        handler.addItemsToTrade();

        assertThat(Arrays.asList(trade.getTradingWindow(1).getItems()), containsCoinsOfValue(afterTax(done)));
        assertEquals(afterTax(done), crafter.getShop().getMoney());
    }

    @Test
    void testOwnerAddingJobAsWellAsCollectingCoin() throws WorkBook.NoWorkBookOnWorker {
        int done = 100;
        addDoneJobToWorkBook(100);

        makeNewOwnerCrafterTrade();
        makeHandler();
        handler.addItemsToTrade();

        Item tool = factory.createNewItem();
        owner.getInventory().insertItem(tool);
        selectOption("Improve to 20");
        handler.balance();
        getMoneyForItem(tool).forEach(owner.getInventory()::insertItem);
        owner.getInventory().getItems().forEach(trade.getTradingWindow(4)::addItem);
        Stream.of(trade.getTradingWindow(1).getItems()).filter(Item::isCoin).forEach(i -> {
            trade.getTradingWindow(1).removeItem(i);
            trade.getTradingWindow(3).addItem(i);
        });

        handler.balance();
        setSatisfied(owner);

        assertTrue(crafter.getInventory().getItems().contains(tool));
        assertThat(owner, hasCoinsOfValue(afterTax(done)));
        assertEquals(0, crafter.getShop().getMoney());
        assertEquals(1, WorkBook.getWorkBookFromWorker(crafter).todo());
    }

    @Test
    void testMoneyNotGivenForDonations() {
        crafter = factory.createNewCrafter(owner, new CrafterType(SkillList.SMITHING_GOLDSMITHING), 50);
        crafter.getShop().setPriceModifier(9999);

        Item item = factory.createNewItem(ItemList.statuetteMagranon);
        item.setMaterial(ItemMaterials.MATERIAL_GOLD);
        owner.getInventory().insertItem(item);
        item.setQualityLevel(50);

        makeNewOwnerCrafterTrade();
        makeHandler();

        handler.addItemsToTrade();

        selectOption("Donate");
        trade.getTradingWindow(2).addItem(item);

        handler.balance();

        assertEquals(1, trade.getTradingWindow(3).getItems().length);
        assertEquals(1, trade.getTradingWindow(4).getItems().length);
        assertThat(Arrays.asList(trade.getTradingWindow(3).getItems()), containsCoinsOfValue(0));
    }

    @Test
    void testCanDonateAndCollectMoney() throws WorkBook.NoWorkBookOnWorker {
        crafter = factory.createNewCrafter(owner, new CrafterType(SkillList.SMITHING_GOLDSMITHING), 50);
        int done = 100;
        addDoneJobToWorkBook(100);

        Item item = factory.createNewItem(ItemList.statuetteMagranon);
        item.setMaterial(ItemMaterials.MATERIAL_GOLD);
        owner.getInventory().insertItem(item);

        makeNewOwnerCrafterTrade();
        makeHandler();

        handler.addItemsToTrade();
        assertThat(Arrays.asList(trade.getTradingWindow(1).getItems()), containsCoinsOfValue(afterTax(done)));

        selectOption("Donate");
        trade.getTradingWindow(2).addItem(item);

        for (Item i : trade.getTradingWindow(1).getItems()) {
            if (i.isCoin()) {
                trade.getTradingWindow(1).removeItem(i);
                trade.getTradingWindow(3).addItem(i);
            }
        }

        handler.balance();
        setSatisfied(owner);

        assertTrue(crafter.getInventory().getItems().contains(item));
        assertThat(owner, hasCoinsOfValue(afterTax(done)));
        assertEquals(0, crafter.getShop().getMoney());
        assertEquals(1, WorkBook.getWorkBookFromWorker(crafter).donationsTodo());
    }

    private void addDoneJob(Item item, int targetQL) {
        try {
            int price = 100;
            WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
            workBook.addJob(owner.getWurmId(), item, targetQL, false, price);
            Iterator<Job> jobs = workBook.iterator();
            Job lastJob = null;
            while (jobs.hasNext())
                lastJob = jobs.next();
            assert lastJob != null;
            setJobDone(workBook, lastJob);
            crafter.getShop().setMoney(afterTax(price));
        } catch (IllegalAccessException | WorkBook.NoWorkBookOnWorker | NoSuchMethodException | InvocationTargetException | WorkBook.WorkBookFull e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testCoinsStillAvailableForPickupAfterItemCollected() throws WorkBook.NoWorkBookOnWorker {
        crafter = factory.createNewCrafter(owner, crafterType, 50);

        int ql = 20;
        Item item = factory.createNewItem(factory.getIsSmithingId());
        item.setQualityLevel(ql);
        crafter.getInventory().insertItem(item);
        addDoneJob(item, ql);
        long toCollect = crafter.getShop().getMoney();

        makeNewOwnerCrafterTrade();
        makeHandler();
        addItemsToTrade();
        assert Arrays.asList(trade.getTradingWindow(1).getItems()).contains(item);

        TradingWindow offerWindow = trade.getTradingWindow(1);
        for (Item option : offerWindow.getItems()) {
            if (option == item) {
                offerWindow.removeItem(option);
                trade.getCreatureOneRequestWindow().addItem(option);
                break;
            }
        }

        handler.balance();
        setSatisfied(owner);

        assertFalse(crafter.getInventory().getItems().contains(item));
        assertTrue(owner.getInventory().getItems().contains(item));
        assertEquals(0, WorkBook.getWorkBookFromWorker(crafter).todo());
        assertEquals(toCollect, crafter.getShop().getMoney());

        makeNewOwnerCrafterTrade();
        makeHandler();

        handler.addItemsToTrade();
        assertThat(Arrays.asList(trade.getTradingWindow(1).getItems()), containsCoinsOfValue(toCollect));
    }

    @Test
    void testCrafterMoneyNotAffectedByTradeWithChange() throws WorkBook.NoWorkBookOnWorker {
        int startingMoney = 100;
        crafter = factory.createNewCrafter(owner, new CrafterType(SkillList.SMITHING_GOLDSMITHING), 50);
        assert WorkBook.getWorkBookFromWorker(crafter).todo() == 0;
        factory.getShop(crafter).setMoney(startingMoney);

        Item item = factory.createNewItem(ItemList.statuetteMagranon);
        item.setMaterial(ItemMaterials.MATERIAL_GOLD);
        owner.getInventory().insertItem(item);
        item.setQualityLevel(1);

        makeNewOwnerCrafterTrade();
        makeHandler();

        handler.addItemsToTrade();

        selectOption("Improve to 20ql");
        trade.getTradingWindow(2).addItem(item);
        handler.balance();
        assert Arrays.asList(trade.getTradingWindow(4).getItems()).contains(item);
        int price = handler.getTraderBuyPriceForItem(item);
        Arrays.stream(Economy.getEconomy().getCoinsFor(price + 10)).forEach(owner.getInventory()::insertItem);
        owner.getInventory().getItems().forEach(trade.getTradingWindow(2)::addItem);

        setNotBalanced();
        handler.balance();
        setSatisfied(owner);

        assertEquals(startingMoney, factory.getShop(crafter).getMoney());
        assertTrue(crafter.getInventory().getItems().contains(item));
        assertFalse(owner.getInventory().getItems().contains(item));
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        assertEquals(1, workBook.todo());
    }

    @Test
    void testCrafterMoneyNotAffectedByTradeWithChangeWhileCollectingAsWell() throws WorkBook.NoWorkBookOnWorker {
        int startingMoney = 100;
        crafter = factory.createNewCrafter(owner, new CrafterType(SkillList.SMITHING_GOLDSMITHING), 50);
        assert WorkBook.getWorkBookFromWorker(crafter).todo() == 0;
        factory.getShop(crafter).setMoney(startingMoney);

        Item item = factory.createNewItem(ItemList.statuetteMagranon);
        item.setMaterial(ItemMaterials.MATERIAL_GOLD);
        owner.getInventory().insertItem(item);
        item.setQualityLevel(1);

        makeNewOwnerCrafterTrade();
        makeHandler();

        handler.addItemsToTrade();

        selectOption("Improve to 20ql");
        trade.getTradingWindow(2).addItem(item);
        handler.balance();
        assert Arrays.asList(trade.getTradingWindow(4).getItems()).contains(item);
        int price = handler.getTraderBuyPriceForItem(item);
        Arrays.stream(Economy.getEconomy().getCoinsFor(price + 10)).forEach(owner.getInventory()::insertItem);
        owner.getInventory().getItems().forEach(trade.getTradingWindow(2)::addItem);
        for (Item i : trade.getTradingWindow(1).getItems()) {
            if (i.isCoin()) {
                trade.getTradingWindow(1).removeItem(i);
                trade.getTradingWindow(3).addItem(i);
            }
        }

        setNotBalanced();
        handler.balance();
        setSatisfied(owner);

        assertEquals(0, factory.getShop(crafter).getMoney());
        assertTrue(crafter.getInventory().getItems().contains(item));
        assertFalse(owner.getInventory().getItems().contains(item));
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        assertEquals(1, workBook.todo());
    }

    private void setVillagePermissions(boolean improve, boolean pickup) {
        factory.createVillageFor(owner);
        crafter.currentVillage = owner.citizenVillage;
        assert owner.citizenVillage != null;
        assert crafter.getCurrentVillage() == owner.citizenVillage;
        try {
            VillageRole role = crafter.currentVillage.getRoleForStatus(VillageStatus.ROLE_EVERYBODY);
            role.setCanImproveRepair(improve);
            role.setCanPickup(pickup);
        } catch (NoSuchRoleException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testWelcomeMessageNoPermissions() throws WorkBook.NoWorkBookOnWorker {
        makeNewOwnerCrafterTrade();
        setVillagePermissions(false, false);
        assert !WorkBook.getWorkBookFromWorker(crafter).isForgeAssigned();
        makeHandler();

        assertThat(owner, receivedMessageContaining("permission to \"Improve\""));
        assertThat(owner, receivedMessageContaining("permission to \"Pickup\" in this village to use a forge"));
    }

    @Test
    void testWelcomeMessageImprovePermission() throws WorkBook.NoWorkBookOnWorker {
        makeNewOwnerCrafterTrade();
        setVillagePermissions(true, false);
        assert !WorkBook.getWorkBookFromWorker(crafter).isForgeAssigned();
        makeHandler();

        assertThat(owner, didNotReceiveMessageContaining("permission to \"Improve\""));
        assertThat(owner, receivedMessageContaining("permission to \"Pickup\" in this village to use a forge"));
    }

    @Test
    void testWelcomeMessageNoPickupPermissionAndNoForge() throws WorkBook.NoWorkBookOnWorker, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        makeNewOwnerCrafterTrade();
        setVillagePermissions(true, false);
        ReflectionUtil.callPrivateMethod(WorkBook.getWorkBookFromWorker(crafter), WorkBook.class.getDeclaredMethod("setForge", Item.class), factory.createNewItem(ItemList.forge));
        makeHandler();

        assertThat(owner, didNotReceiveMessageContaining("permission to \"Improve\""));
        assertThat(owner, didNotReceiveMessageContaining("permission to \"Pickup\" in this village to use a forge"));
        assertThat(owner, receivedMessageContaining("permission to \"Pickup\""));
    }

    @Test
    void testWelcomeMessagePickupButNoForge() throws WorkBook.NoWorkBookOnWorker, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        makeNewOwnerCrafterTrade();
        setVillagePermissions(true, true);
        assert !WorkBook.getWorkBookFromWorker(crafter).isForgeAssigned();
        makeHandler();

        assertThat(owner, didNotReceiveMessageContaining("permission to \"Improve\""));
        assertThat(owner, didNotReceiveMessageContaining("permission to \"Pickup\""));
    }

    @Test
    void testWelcomeMessagePickupAndForge() throws WorkBook.NoWorkBookOnWorker, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        makeNewOwnerCrafterTrade();
        setVillagePermissions(true, true);
        ReflectionUtil.callPrivateMethod(WorkBook.getWorkBookFromWorker(crafter), WorkBook.class.getDeclaredMethod("setForge", Item.class), factory.createNewItem(ItemList.forge));
        makeHandler();

        assertThat(owner, didNotReceiveMessageContaining("permission to \"Improve\""));
        assertThat(owner, didNotReceiveMessageContaining("permission to \"Pickup\""));
    }

    @Test
    void testGiveOptionAddedIfOwner() {
        makeNewOwnerCrafterTrade();
        makeHandler();
        handler.addItemsToTrade();

        assertTrue(Arrays.stream(trade.getTradingWindow(1).getItems()).anyMatch(it -> it.getName().startsWith("Give")));
    }

    @Test
    void testGiveOptionAddedIfGM() throws IOException {
        makeNewCrafterTrade();
        player.setPower((byte)2);
        makeHandler();
        handler.addItemsToTrade();

        assertTrue(Arrays.stream(trade.getTradingWindow(1).getItems()).anyMatch(it -> it.getName().startsWith("Give")));
    }

    @Test
    void testGiveOptionNotAddedIfNotOwner() {
        makeNewCrafterTrade();
        assert player.getPower() == 0;
        makeHandler();
        handler.addItemsToTrade();

        assertFalse(Arrays.stream(trade.getTradingWindow(1).getItems()).anyMatch(it -> it.getName().startsWith("Give")), Arrays.stream(trade.getTradingWindow(1).getItems()).map(Item::getName).collect(Collectors.joining()));
    }

    @Test
    void testOnlyOneOfGiveAndDonateAccepted() {
        makeNewOwnerCrafterTrade();
        makeHandler();
        handler.addItemsToTrade();

        selectOption("Donate");
        selectOption("Give");
        assertEquals(2, trade.getTradingWindow(3).getItems().length);
        handler.balance();
        assertEquals(1, trade.getTradingWindow(3).getItems().length);
    }

    @Test
    void testGivenToolsAddedProperly() throws SQLException {
        Item item = factory.createNewItem(ItemList.hammerMetal);
        owner.getInventory().insertItem(item);

        makeNewOwnerCrafterTrade();
        makeHandler();
        handler.addItemsToTrade();

        selectOption("Give");
        trade.getTradingWindow(2).addItem(item);

        handler.balance();

        assertEquals(1, trade.getTradingWindow(3).getItems().length);
        assertEquals(1, trade.getTradingWindow(4).getItems().length);
        assertTrue(Arrays.stream(trade.getTradingWindow(3).getItems()).anyMatch(it -> it.getName().startsWith("Give")));

        setSatisfied(owner);
        assertFalse(owner.getInventory().getItems().contains(item));
        assertTrue(crafter.getInventory().getItems().contains(item));
        Iterator<Item> iter = ((CrafterAIData)crafter.getCreatureAIData()).tools.getGivenTools().iterator();
        assertTrue(iter.hasNext());
        assertEquals(item, iter.next());
        assertTrue(CrafterDatabase.getGivenToolsFor(crafter).contains(item.getWurmId()));
    }

    @Test
    void testGivenToolsRemovedProperly() throws SQLException {
        makeNewOwnerCrafterTrade();
        Item item = factory.createNewItem(ItemList.hammerMetal);
        crafter.getInventory().insertItem(item);
        CrafterAIData data = (CrafterAIData)crafter.getCreatureAIData();
        data.tools.addGivenTool(item);

        makeHandler();
        handler.addItemsToTrade();

        selectOption("hammer");
        trade.getTradingWindow(2).addItem(item);

        handler.balance();

        assertEquals(1, trade.getTradingWindow(3).getItems().length);
        assertEquals(0, trade.getTradingWindow(4).getItems().length);
        assertTrue(Arrays.stream(trade.getTradingWindow(3).getItems()).anyMatch(it -> it.getName().contains("hammer")));

        setSatisfied(owner);
        assertTrue(owner.getInventory().getItems().contains(item));
        assertFalse(crafter.getInventory().getItems().contains(item));
        System.out.println(StreamSupport.stream(data.tools.getGivenTools().spliterator(), false).map(Item::getName).collect(Collectors.joining()));
        assertThrows(NoSuchElementException.class, () -> data.tools.getGivenTools().iterator().next(), StreamSupport.stream(data.tools.getGivenTools().spliterator(), false).map(Item::getName).collect(Collectors.joining()));
        assertTrue(CrafterDatabase.getGivenToolsFor(crafter).isEmpty());
    }
}
