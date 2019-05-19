package com.wurmonline.server.items;

import com.wurmonline.server.economy.Economy;
import mod.wurmunlimited.npcs.CrafterMod;
import mod.wurmunlimited.npcs.CrafterTradingTest;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static mod.wurmunlimited.Assert.hasCoinsOfValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CrafterTradingWindowTests extends CrafterTradingTest {

    @Test
    void testAllMoneyToTax() {
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
        int price = handler.getTraderBuyPriceForItem(tool);
        Economy.getEconomy().getKingsShop().setMoney(0);
        player.getInventory().getItems().forEach(window::addItem);

        handler.balance();
        trade.getCreatureTwoRequestWindow().swapOwners();

        assertEquals(1, crafter.getInventory().getItemCount() - crafterStartingItems);
        assertEquals(0, player.getInventory().getItemCount());
        assertEquals(0, factory.getShop(crafter).getMoney());
        assertEquals(0, crafter.getCitizenVillage().plan.moneyLeft);
        assertEquals(price, Economy.getEconomy().getKingsShop().getMoney());
        verify(Economy.getEconomy(), times(price / 5)).returnCoin(any(Item.class), eq("CrafterTrade"));
    }

    @Test
    void testMoneySplitBetweenTaxAndUpkeep() {
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
        int price = handler.getTraderBuyPriceForItem(tool);
        Economy.getEconomy().getKingsShop().setMoney(0);
        player.getInventory().getItems().forEach(window::addItem);

        setNotBalanced();
        handler.balance();
        setSatisfied(player);

        assertEquals(1, crafter.getInventory().getItemCount() - crafterStartingItems);
        assertEquals(0, player.getInventory().getItemCount());
        assertEquals(0, factory.getShop(crafter).getMoney());
        int upkeepPrice = (int)(price * (upkeepPercentage / 100));
        assertEquals(upkeepPrice, crafter.getCitizenVillage().plan.moneyLeft);
        assertEquals(price - upkeepPrice, Economy.getEconomy().getKingsShop().getMoney());
        verify(Economy.getEconomy(), times(coinCount)).returnCoin(any(Item.class), eq("CrafterTrade"));
    }

    @Test
    void testMoneyLeftOnCrafterTaxed() {
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
        assertEquals(0, factory.getShop(crafter).getMoney());
        assertEquals(0, crafter.getCitizenVillage().plan.moneyLeft);
        assertEquals(kingsCut, Economy.getEconomy().getKingsShop().getMoney());
    }

    @Test
    void testOptionItemsNotGivenToPlayer() {
        player.getInventory().getItems().clear();
        makeNewCrafterTrade();
        makeHandler();
        addItemsToTrade();

        TradingWindow window = trade.getCreatureOneRequestWindow();
        selectOption("Improve to 20ql");

        window.swapOwners();

        assertEquals(0, player.getInventory().getItemCount());
    }

    @Test
    void testUpkeepMoneyGoesToKingIfCrafterHasNoVillage() {
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
        int price = handler.getTraderBuyPriceForItem(tool);
        Economy.getEconomy().getKingsShop().setMoney(0);
        player.getInventory().getItems().forEach(window::addItem);

        setNotBalanced();
        handler.balance();
        setSatisfied(player);

        assertEquals(1, crafter.getInventory().getItemCount() - crafterStartingItems);
        assertEquals(0, player.getInventory().getItemCount());
        assertEquals(0, factory.getShop(crafter).getMoney());
        assertEquals(price, Economy.getEconomy().getKingsShop().getMoney());
        verify(Economy.getEconomy(), times(coinCount)).returnCoin(any(Item.class), eq("CrafterTrade"));
    }
}
