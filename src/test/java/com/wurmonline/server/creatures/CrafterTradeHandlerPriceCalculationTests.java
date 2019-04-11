package com.wurmonline.server.creatures;

import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.skills.SkillList;
import mod.wurmunlimited.npcs.CrafterMod;
import mod.wurmunlimited.npcs.CrafterTradingTest;
import mod.wurmunlimited.npcs.CrafterType;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CrafterTradeHandlerPriceCalculationTests extends CrafterTradingTest {
    private Item tool;

    private void create(int startOption) {
        makeNewCrafterTrade();
        makeHandler();
        handler.addItemsToTrade();
        tool = factory.createNewItem(factory.getIsBlacksmithingId());
        tool.setQualityLevel(1);
        selectOption("Improve to " + startOption);
        handler.balance();
    }

    @Test
    void test70QLCrossoverMatches() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        crafter = factory.createNewCrafter(owner, crafterType, 70);
        create(70);
        int basePrice = (int)(CrafterMod.getBasePriceForSkill(SkillList.SMITHING_BLACKSMITHING) * 70);
        assertEquals(
                ReflectionUtil.callPrivateMethod(handler, CrafterTradeHandler.class.getDeclaredMethod("priceCalculationSub70", float.class), basePrice),
                ReflectionUtil.callPrivateMethod(handler, CrafterTradeHandler.class.getDeclaredMethod("priceCalculation", float.class), basePrice)
        );
    }

    @Test
    void test1QLTo20QL() {
        create(20);
        tool.setQualityLevel(1);

        assertEquals(80, handler.getTraderBuyPriceForItem(tool));
    }

    @Test
    void testIncreasingStartingQLDecreasesPrice() {
        create(20);

        long last = Long.MAX_VALUE;
        for (int i = 1; i < 99; i++) {
            tool.setQualityLevel(i);
            long current = handler.getTraderBuyPriceForItem(tool);
            assertTrue(current + " not less than " + last, current < last);
            last = current;
        }

    }

    @Test
    void testDragonArmourAddsAnotherModifier() throws NoSuchFieldException, IllegalAccessException {
        //noinspection unchecked
        ((Map<Integer, Float>)ReflectionUtil.getPrivateField(null, CrafterMod.class.getDeclaredField("skillPrices"))).put(-1, 10.0f);
        crafter = factory.createNewCrafter(owner, new CrafterType(CrafterType.allArmour), 70);
        create(20);

        Item armour = factory.createNewItem(ItemList.chainJacket);
        Item dragonArmour = factory.createNewItem(ItemList.dragonScaleJacket);

        int armourPrice = handler.getTraderBuyPriceForItem(armour);
        assertEquals(armourPrice * 10, handler.getTraderBuyPriceForItem(dragonArmour));
    }

    @Test
    void testAllValuesAtHalfModifier() throws NoSuchFieldException, IllegalAccessException {
        crafter = factory.createNewCrafter(owner, new CrafterType(SkillList.SMITHING_BLACKSMITHING), 100);
        assert CrafterMod.getSkillCap() != 100.0f;
        makeNewCrafterTrade();
        makeHandler();
        handler.addItemsToTrade();
        tool = factory.createNewItem(factory.getIsBlacksmithingId());
        tool.setQualityLevel(1);
        handler.balance();

        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("basePrice"), 0.5f);
        for (Item op : trade.getTradingWindow(1).getItems()) {
            if (op.getName().startsWith("Mail") || op.getName().startsWith("Donate")) {
                trade.getTradingWindow(1).removeItem(op);
                continue;
            }
            trade.getTradingWindow(1).removeItem(op);
            trade.getTradingWindow(3).addItem(op);
            handler.balance();
            int price = handler.getTraderBuyPriceForItem(tool);
            System.out.println(String.format("%s - %s", op.getName(), price));
            assertTrue(price > 0);
            trade.getTradingWindow(3).removeItem(op);
        }
    }

    @Test
    void testAllValuesAt50Modifier() throws NoSuchFieldException, IllegalAccessException {
        crafter = factory.createNewCrafter(owner, new CrafterType(SkillList.SMITHING_BLACKSMITHING), 100);
        assert CrafterMod.getSkillCap() != 100.0f;
        makeNewCrafterTrade();
        makeHandler();
        handler.addItemsToTrade();
        tool = factory.createNewItem(factory.getIsBlacksmithingId());
        tool.setQualityLevel(1);
        handler.balance();

        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("basePrice"), 50);
        for (Item op : trade.getTradingWindow(1).getItems()) {
            if (op.getName().startsWith("Mail") || op.getName().startsWith("Donate")) {
                trade.getTradingWindow(1).removeItem(op);
                continue;
            }
            trade.getTradingWindow(1).removeItem(op);
            trade.getTradingWindow(3).addItem(op);
            handler.balance();
            int price = handler.getTraderBuyPriceForItem(tool);
            System.out.println(String.format("%s - %s", op.getName(), price));
            assertTrue(price < Integer.MAX_VALUE);
            trade.getTradingWindow(3).removeItem(op);
        }
    }

    @Test
    void testPriceModifierIsUsedInCalculation() {
        factory.getShop(crafter).setPriceModifier(1.0f);
        create(20);
        int standardPrice = handler.getTraderBuyPriceForItem(tool);

        factory.getShop(crafter).setPriceModifier(1.1f);
        create(20);
        int higherPrice = handler.getTraderBuyPriceForItem(tool);

        assertTrue(standardPrice < higherPrice);
        assertEquals(standardPrice / 1.0f, higherPrice / 1.1f, 0.1f);
    }

    @Test
    void testPriceModifierIsNotUsedInCalculationIfDisabled() throws NoSuchFieldException, IllegalAccessException {
        try {
            ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("usePriceModifier"), false);

            factory.getShop(crafter).setPriceModifier(1.0f);
            create(20);
            int standardPrice = handler.getTraderBuyPriceForItem(tool);

            factory.getShop(crafter).setPriceModifier(1.1f);
            create(20);
            int higherPrice = handler.getTraderBuyPriceForItem(tool);

            assertEquals(standardPrice, higherPrice);
        } finally {
            ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("usePriceModifier"), true);
        }
    }
}
