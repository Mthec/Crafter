package com.wurmonline.server.items;

import com.wurmonline.server.economy.Economy;
import mod.wurmunlimited.npcs.CrafterMod;
import mod.wurmunlimited.npcs.CrafterTradingTest;
import mod.wurmunlimited.npcs.WorkBook;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Properties;

import static mod.wurmunlimited.Assert.hasCoinsOfValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class CrafterTradeWorkBookTests extends CrafterTradingTest {
    @Test
    void testAllMoneyToTax() throws WorkBook.NoWorkBookOnWorker, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        CrafterMod mod = new CrafterMod();
        Properties properties = new Properties();
        properties.setProperty("payment", CrafterMod.PaymentOption.all_tax.name());
        mod.configure(properties);

        init();
        factory.createVillageFor(owner, crafter);
        int crafterStartingItems = crafter.getInventory().getItemCount();

        makeNewCrafterTrade();
        makeHandler();
        addItemsToTrade();

        TradingWindow window = trade.getTradingWindow(2);
        selectOption("Improve to 50ql");
        getMoneyForItem(tool).forEach(coin -> player.getInventory().insertItem(coin));
        Economy.getEconomy().getKingsShop().setMoney(0);
        player.getInventory().getItems().forEach(window::addItem);

        handler.balance();
        int price = handler.getTraderBuyPriceForItem(tool);
        trade.getCreatureTwoRequestWindow().swapOwners();

        assertEquals(1, crafter.getInventory().getItemCount() - crafterStartingItems);
        assertEquals(0, player.getInventory().getItemCount());

        setJobDone(WorkBook.getWorkBookFromWorker(crafter));
        assertEquals(0, factory.getShop(crafter).getMoney());
        assertEquals(0, crafter.getCitizenVillage().plan.moneyLeft);
        assertEquals(price, Economy.getEconomy().getKingsShop().getMoney());
    }

    @Test
    void testMoneySplitBetweenTaxAndUpkeep() throws NoSuchMethodException, WorkBook.NoWorkBookOnWorker, InvocationTargetException, IllegalAccessException, NoSuchFieldException {
        float upkeepPercentage = 25;
        CrafterMod mod = new CrafterMod();
        Properties properties = new Properties();
        properties.setProperty("payment", CrafterMod.PaymentOption.tax_and_upkeep.name());
        properties.setProperty("upkeep_percentage", String.valueOf(upkeepPercentage));
        mod.configure(properties);

        init();
        factory.createVillageFor(owner, crafter);
        int crafterStartingItems = crafter.getInventory().getItemCount();

        makeNewCrafterTrade();
        makeHandler();
        addItemsToTrade();

        TradingWindow window = trade.getTradingWindow(2);
        selectOption("Improve to 50ql");
        handler.balance();
        getMoneyForItem(tool).forEach(coin -> player.getInventory().insertItem(coin));
        int coinCount = player.getInventory().getItemCount() - 1;
        Economy.getEconomy().getKingsShop().setMoney(0);
        player.getInventory().getItems().forEach(window::addItem);

        setNotBalanced();
        handler.balance();
        int price = handler.getTraderBuyPriceForItem(tool);
        setSatisfied(player);

        assertEquals(1, crafter.getInventory().getItemCount() - crafterStartingItems);
        assertEquals(0, player.getInventory().getItemCount());

        setJobDone(WorkBook.getWorkBookFromWorker(crafter));
        assertEquals(0, factory.getShop(crafter).getMoney());
        int upkeepPrice = (int)(price * (upkeepPercentage / 100));
        assertEquals(upkeepPrice, crafter.getCitizenVillage().plan.moneyLeft);
        assertEquals(price - upkeepPrice, Economy.getEconomy().getKingsShop().getMoney());
        verify(Economy.getEconomy(), times(coinCount)).returnCoin(any(Item.class), eq("CrafterTrade"));
    }

    @Test
    void testMoneyLeftOnCrafterTaxed() throws WorkBook.NoWorkBookOnWorker, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        CrafterMod mod = new CrafterMod();
        Properties properties = new Properties();
        properties.setProperty("payment", CrafterMod.PaymentOption.for_owner.name());
        mod.configure(properties);

        init();
        factory.createVillageFor(owner, crafter);
        int crafterStartingItems = crafter.getInventory().getItemCount();

        makeNewCrafterTrade();
        makeHandler();
        addItemsToTrade();

        TradingWindow window = trade.getTradingWindow(2);
        selectOption("Improve to 50ql");
        handler.balance();
        getMoneyForItem(tool).forEach(coin -> player.getInventory().insertItem(coin));
        int price = handler.getTraderBuyPriceForItem(tool);
        Economy.getEconomy().getKingsShop().setMoney(0);
        player.getInventory().getItems().forEach(window::addItem);

        setNotBalanced();
        handler.balance();
        setSatisfied(player);

        int craftersCut = (int)(price * 0.9f);
        int kingsCut = price - craftersCut;

        assertEquals(1, crafter.getInventory().getItemCount() - crafterStartingItems);
        assertThat(crafter, hasCoinsOfValue(0));
        assertEquals(0, player.getInventory().getItemCount());

        setJobDone(WorkBook.getWorkBookFromWorker(crafter));
        assertEquals(craftersCut, factory.getShop(crafter).getMoney());
        assertEquals(0, crafter.getCitizenVillage().plan.moneyLeft);
        assertEquals(kingsCut, Economy.getEconomy().getKingsShop().getMoney());
    }

    @Test
    void testUpkeepMoneyGoesToKingIfCrafterHasNoVillage() throws NoSuchMethodException, WorkBook.NoWorkBookOnWorker, InvocationTargetException, IllegalAccessException {
        float upkeepPercentage = 25;
        CrafterMod mod = new CrafterMod();
        Properties properties = new Properties();
        properties.setProperty("payment", CrafterMod.PaymentOption.tax_and_upkeep.name());
        properties.setProperty("upkeep_percentage", String.valueOf(upkeepPercentage));
        mod.configure(properties);

        init();
        int crafterStartingItems = crafter.getInventory().getItemCount();

        makeNewCrafterTrade();
        makeHandler();
        addItemsToTrade();

        TradingWindow window = trade.getTradingWindow(2);
        selectOption("Improve to 50ql");
        handler.balance();
        getMoneyForItem(tool).forEach(coin -> player.getInventory().insertItem(coin));
        int coinCount = player.getInventory().getItemCount() - 1;
        Economy.getEconomy().getKingsShop().setMoney(0);
        player.getInventory().getItems().forEach(window::addItem);

        setNotBalanced();
        handler.balance();
        int price = handler.getTraderBuyPriceForItem(tool);
        setSatisfied(player);

        assertEquals(1, crafter.getInventory().getItemCount() - crafterStartingItems);
        assertEquals(0, player.getInventory().getItemCount());

        setJobDone(WorkBook.getWorkBookFromWorker(crafter));
        assertEquals(0, factory.getShop(crafter).getMoney());
        assertEquals(price, Economy.getEconomy().getKingsShop().getMoney());
        verify(Economy.getEconomy(), times(coinCount)).returnCoin(any(Item.class), eq("CrafterTrade"));
    }

    private long runTrade() {
        makeNewCrafterTrade();
        makeHandler();
        addItemsToTrade();
        Item item = factory.createNewItem();
        player.getInventory().insertItem(item);

        TradingWindow window = trade.getTradingWindow(2);
        selectOption("Improve to 50ql");
        selectOption("Mail");
        window.addItem(item);
        handler.balance();
        long price = handler.getTraderBuyPriceForItem(item) + CrafterMod.mailPrice();
        Arrays.stream(Economy.getEconomy().getCoinsFor(price)).forEach(c -> {
            player.getInventory().insertItem(c);
            trade.getTradingWindow(2).addItem(c);
        });

        setNotBalanced();
        handler.balance();
        setSatisfied(player);

        return price;
    }

    @Test
    void testTaxWorksProperlyForMoneyGivenToKing() throws NoSuchMethodException, WorkBook.NoWorkBookOnWorker, InvocationTargetException, IllegalAccessException {
        CrafterMod mod = new CrafterMod();
        Properties properties = new Properties();
        properties.setProperty("payment", CrafterMod.PaymentOption.for_owner.name());
        mod.configure(properties);

        init();
        factory.createVillageFor(owner, crafter);

        long price = runTrade();
        long craftersCut = (long)(price * 0.9f);
        long kingsCut = price - craftersCut;
        setJobDone(WorkBook.getWorkBookFromWorker(crafter));
        long taxPaid = crafter.getShop().getTaxPaid();
        assertEquals(kingsCut, taxPaid);

        properties.setProperty("payment", CrafterMod.PaymentOption.all_tax.name());
        mod.configure(properties);

        kingsCut = runTrade();

        setJobDone(WorkBook.getWorkBookFromWorker(crafter));
        assertEquals(kingsCut, crafter.getShop().getTaxPaid() - taxPaid);
    }
}
