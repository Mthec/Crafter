package com.wurmonline.server.creatures;

import com.wurmonline.server.economy.Economy;
import com.wurmonline.server.economy.Shop;
import com.wurmonline.server.items.Item;
import mod.wurmunlimited.npcs.CrafterTradingTest;
import mod.wurmunlimited.npcs.Job;
import mod.wurmunlimited.npcs.WorkBook;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
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
            if (done) {
                Job job = StreamSupport.stream(workBook.spliterator(), false).filter(j -> j.getItem() == tool).findFirst().orElseThrow(RuntimeException::new);
                ReflectionUtil.callPrivateMethod(workBook, WorkBook.class.getDeclaredMethod("setDone", Job.class), job);
            }
            Shop shop = Economy.getEconomy().getShop(crafter);
            shop.setMoney(shop.getMoney() + afterTax(price));
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
        assertEquals(afterTax(todo + done), crafter.getShop().getMoney());
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
        int price = handler.getTraderBuyPriceForItem(tool);
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
        assertEquals(crafter.getShop().getMoney(), afterTax(price));
        assertEquals(1, WorkBook.getWorkBookFromWorker(crafter).todo());
    }
}
