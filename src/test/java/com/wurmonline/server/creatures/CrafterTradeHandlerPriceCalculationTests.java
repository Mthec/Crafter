package com.wurmonline.server.creatures;

import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.shared.constants.ItemMaterials;
import mod.wurmunlimited.npcs.CrafterMod;
import mod.wurmunlimited.npcs.CrafterTradingTest;
import mod.wurmunlimited.npcs.CrafterType;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                (long)ReflectionUtil.callPrivateMethod(handler, CrafterTradeHandler.class.getDeclaredMethod("priceCalculationSub70", float.class), basePrice),
                (long)ReflectionUtil.callPrivateMethod(handler, CrafterTradeHandler.class.getDeclaredMethod("priceCalculation", float.class), basePrice)
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
            assertTrue(current < last, current + " not less than " + last);
            last = current;
        }

    }

    @Test
    void testDragonArmourAddsAnotherModifier() throws NoSuchFieldException, IllegalAccessException {
        (ReflectionUtil.<Map<Integer, Float>>getPrivateField(null, CrafterMod.class.getDeclaredField("skillPrices"))).put(CrafterMod.DRAGON_ARMOUR, 10.0f);
        crafter = factory.createNewCrafter(owner, new CrafterType(CrafterType.allArmour), 70);
        create(20);

        Item armour = factory.createNewItem(ItemList.chainJacket);
        Item dragonArmour = factory.createNewItem(ItemList.dragonScaleJacket);

        int armourPrice = handler.getTraderBuyPriceForItem(armour);
        assertEquals(armourPrice * 10, handler.getTraderBuyPriceForItem(dragonArmour));
    }

    @Test
    void testDragonArmourModifierWithDifferentBasePrice() throws NoSuchFieldException, IllegalAccessException {
        ReflectionUtil.<Map<Integer, Float>>getPrivateField(null, CrafterMod.class.getDeclaredField("skillPrices")).put(CrafterMod.DRAGON_ARMOUR, 10.0f);
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("basePrice"), 2.5f);
        crafter = factory.createNewCrafter(owner, new CrafterType(CrafterType.allArmour), 70);
        create(20);

        Item armour = factory.createNewItem(ItemList.chainJacket);
        Item dragonArmour = factory.createNewItem(ItemList.dragonScaleJacket);

        int armourPrice = handler.getTraderBuyPriceForItem(armour);
        assertEquals(armourPrice * 10, handler.getTraderBuyPriceForItem(dragonArmour));
    }

    @Test
    void testMoonMetalModifierAddsAnotherModifier() throws NoSuchFieldException, IllegalAccessException {
        ReflectionUtil.<Map<Integer, Float>>getPrivateField(null, CrafterMod.class.getDeclaredField("skillPrices")).put(CrafterMod.MOON_METAL, 10.0f);
        crafter = factory.createNewCrafter(owner, new CrafterType(CrafterType.allMetal), 70);
        create(20);

        Item pickaxe = factory.createNewItem(ItemList.pickAxe);
        pickaxe.setMaterial(ItemMaterials.MATERIAL_IRON);
        Item adamantinePickaxe = factory.createNewItem(ItemList.pickAxe);
        adamantinePickaxe.setMaterial(ItemMaterials.MATERIAL_ADAMANTINE);
        Item glimmerPickaxe = factory.createNewItem(ItemList.pickAxe);
        glimmerPickaxe.setMaterial(ItemMaterials.MATERIAL_GLIMMERSTEEL);
        Item seryll = factory.createNewItem(ItemList.pickAxe);
        seryll.setMaterial(ItemMaterials.MATERIAL_SERYLL);

        int normalPrice = handler.getTraderBuyPriceForItem(pickaxe);
        assertEquals(normalPrice * 10, handler.getTraderBuyPriceForItem(adamantinePickaxe));
        assertEquals(normalPrice * 10, handler.getTraderBuyPriceForItem(glimmerPickaxe));
        assertEquals(normalPrice * 10, handler.getTraderBuyPriceForItem(seryll));
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
            System.out.printf("%s - %s%n", op.getName(), price);
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
            System.out.printf("%s - %s%n", op.getName(), price);
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
        assertEquals(standardPrice, higherPrice / 1.1f, 0.1f);
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
