package com.wurmonline.server.items;

import com.wurmonline.server.economy.Economy;
import mod.wurmunlimited.npcs.CrafterMod;
import mod.wurmunlimited.npcs.CrafterTradingTest;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Properties;

import static mod.wurmunlimited.Assert.hasCoinsOfValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CrafterTradingWindowTests extends CrafterTradingTest {

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
    void testShopMoneyCollectedByOwner() {
        CrafterMod mod = new CrafterMod();
        Properties properties = new Properties();
        properties.setProperty("payment", CrafterMod.PaymentOption.for_owner.name());
        mod.configure(properties);

        init();
        int crafterStartingItems = crafter.getInventory().getItemCount();
        long toCollect = 100L;
        crafter.getShop().setMoney(toCollect);
        Economy.getEconomy().getKingsShop().setMoney(0);

        makeNewOwnerCrafterTrade();
        makeHandler();
        addItemsToTrade();

        TradingWindow window = trade.getTradingWindow(1);
        for (Item item : window.getItems()) {
            if (item.isCoin()) {
                window.removeItem(item);
                trade.getTradingWindow(3).addItem(item);
            }
        }
        handler.balance();
        setSatisfied(owner);

        assertEquals(0, crafter.getInventory().getItemCount() - crafterStartingItems);
        assertThat(crafter, hasCoinsOfValue(0));
        assertThat(owner, hasCoinsOfValue(toCollect));
        assertEquals(0, factory.getShop(crafter).getMoney());
    }

    @Test
    void testPartOfShopMoneyCollectedByOwner() {
        CrafterMod mod = new CrafterMod();
        Properties properties = new Properties();
        properties.setProperty("payment", CrafterMod.PaymentOption.for_owner.name());
        mod.configure(properties);

        init();
        int crafterStartingItems = crafter.getInventory().getItemCount();
        long toCollect = 110L;
        crafter.getShop().setMoney(toCollect);
        Economy.getEconomy().getKingsShop().setMoney(0);

        makeNewOwnerCrafterTrade();
        makeHandler();
        addItemsToTrade();

        TradingWindow window = trade.getTradingWindow(1);
        int ironCoins = 0;
        for (Item item : window.getItems()) {
            if (item.isCoin()) {
                if (item.getTemplateId() == ItemList.coinCopper) {
                    window.removeItem(item);
                    trade.getTradingWindow(3).addItem(item);
                } else if (item.getTemplateId() == ItemList.coinIronFive)
                    ironCoins += 1;
            }
        }
        assert Arrays.stream(window.getItems()).anyMatch(Item::isCoin);
        assert ironCoins == 2;

        handler.balance();
        setSatisfied(owner);

        assertEquals(0, crafter.getInventory().getItemCount() - crafterStartingItems);
        assertThat(crafter, hasCoinsOfValue(0));
        assertThat(owner, hasCoinsOfValue(100L));
        assertEquals(10L, factory.getShop(crafter).getMoney());
    }
}
