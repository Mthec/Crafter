package com.wurmonline.server.items;

import com.wurmonline.server.economy.Economy;
import mod.wurmunlimited.npcs.CrafterAIData;
import mod.wurmunlimited.npcs.CrafterTradingTest;
import mod.wurmunlimited.npcs.Job;
import mod.wurmunlimited.npcs.WorkBook;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

import static mod.wurmunlimited.Assert.didNotReceiveMessageContaining;
import static mod.wurmunlimited.Assert.receivedMessageContaining;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class CrafterTradeTests extends CrafterTradingTest {

    @Test
    void testTradeCompletion() {
        makeNewCrafterTrade();
        makeHandler();
        addItemsToTrade();

        selectOption("Improve to 20ql");
        handler.balance();

        assertTrue(trade.getTradingWindow(2).mayAddFromInventory(player, tool));
        getMoneyForItem(tool).forEach(player.getInventory()::insertItem);
        player.getInventory().getItems().forEach(trade.getTradingWindow(2)::addItem);

        setNotBalanced();
        handler.balance();
        setSatisfied(player);

        assertFalse(player.getInventory().getItems().contains(tool));
        assertTrue(crafter.getInventory().getItems().contains(tool));
        assertThat(player, receivedMessageContaining("completed successfully"));
    }

    @Test
    void testCorrectTradingWindowsCreated() {
        makeNewCrafterTrade();
        assertAll(
                () -> assertTrue(trade.getTradingWindow(1) instanceof CrafterTradingWindow),
                () -> assertTrue(trade.getTradingWindow(2) instanceof CrafterTradingWindow),
                () -> assertTrue(trade.getTradingWindow(3) instanceof CrafterTradingWindow),
                () -> assertTrue(trade.getTradingWindow(4) instanceof CrafterTradingWindow)
        );
    }

    @Test
    void testTradeDoesNotCompleteIfPlayerDoesNotAccept() {
        makeNewCrafterTrade();
        makeHandler();
        addItemsToTrade();

        assertTrue(trade.getTradingWindow(2).mayAddFromInventory(player, tool));
        Arrays.asList(Economy.getEconomy().getCoinsFor(20)).forEach(player.getInventory()::insertItem);
        player.getInventory().getItems().forEach(trade.getTradingWindow(2)::addItem);

        selectOption("Improve to 20ql");
        handler.balance();

        assertTrue(player.getInventory().getItems().contains(tool));
        assertFalse(crafter.getInventory().getItems().contains(tool));
        assertThat(player, didNotReceiveMessageContaining("completed successfully"));
    }

    @Test
    void testTradeDoesNotCompleteIfCrafterDoesNotAccept() {
        makeNewCrafterTrade();
        makeHandler();
        addItemsToTrade();

        selectOption("Improve to 20ql");
        handler.balance();

        assertTrue(trade.getTradingWindow(2).mayAddFromInventory(player, tool));
        getMoneyForItem(tool).forEach(player.getInventory()::insertItem);
        player.getInventory().getItems().forEach(trade.getTradingWindow(2)::addItem);

        handler.balance();
        trade.setSatisfied(crafter, false, trade.getCurrentCounter());
        setSatisfied(player);

        assertTrue(player.getInventory().getItems().contains(tool));
        assertFalse(crafter.getInventory().getItems().contains(tool));
        assertThat(player, didNotReceiveMessageContaining("I will need"));
        assertThat(player, didNotReceiveMessageContaining("completed successfully"));
    }

    @Test
    void testTradeWindowClosesOnTradeEnd() {
        makeNewCrafterTrade();
        makeHandler();

        setSatisfied(crafter);
        setSatisfied(player);

        assertNull(crafter.getTrade());
        assertNull(player.getTrade());
        assertThat(player, didNotReceiveMessageContaining("withdraw from the trade"));
    }

    @Test
    void testTradeWindowClosesOnCollectionTradeEnd() throws WorkBook.NoWorkBookOnWorker, NoSuchMethodException, InvocationTargetException, IllegalAccessException, WorkBook.WorkBookFull {
        init();
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        Item item = factory.createNewItem();
        crafter.getInventory().insertItem(item);
        workBook.addJob(player.getWurmId(), item, 100, false, 1);
        ReflectionUtil.callPrivateMethod(workBook, WorkBook.class.getDeclaredMethod("setDone", Job.class),
                workBook.iterator().next());

        makeNewCrafterTrade();
        makeHandler();
        addItemsToTrade();
        trade.getTradingWindow(1).removeItem(item);
        trade.getTradingWindow(3).addItem(item);
        handler.balance();
        setSatisfied(player);

        assertNull(crafter.getTrade());
        assertNull(player.getTrade());
        assertThat(player, didNotReceiveMessageContaining("withdraw from the trade"));
        assertTrue(factory.getCommunicator(player).tradeWindowClosed);
    }
}
