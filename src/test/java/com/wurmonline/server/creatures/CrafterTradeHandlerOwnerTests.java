package com.wurmonline.server.creatures;

import com.wurmonline.server.economy.Economy;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.items.TradingWindow;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.shared.constants.ItemMaterials;
import mod.wurmunlimited.npcs.CrafterTradingTest;
import mod.wurmunlimited.npcs.CrafterType;
import mod.wurmunlimited.npcs.Job;
import mod.wurmunlimited.npcs.WorkBook;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static mod.wurmunlimited.Assert.containsCoinsOfValue;
import static mod.wurmunlimited.Assert.hasCoinsOfValue;
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
}
