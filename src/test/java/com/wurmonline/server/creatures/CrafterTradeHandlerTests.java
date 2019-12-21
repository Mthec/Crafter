package com.wurmonline.server.creatures;

import com.google.common.collect.BiMap;
import com.wurmonline.server.Items;
import com.wurmonline.server.NoSuchItemException;
import com.wurmonline.server.economy.Change;
import com.wurmonline.server.economy.Economy;
import com.wurmonline.server.economy.MonetaryConstants;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.items.ItemTemplate;
import com.wurmonline.server.items.TradingWindow;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.shared.constants.ItemMaterials;
import mod.wurmunlimited.npcs.*;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static mod.wurmunlimited.Assert.*;
import static mod.wurmunlimited.npcs.Assertions.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class CrafterTradeHandlerTests extends CrafterTradingTest {

    private static final Pattern priceMessageCost = Pattern.compile("need ([\\d\\s\\w,]+) more");
    private static final Pattern priceDenominations = Pattern.compile("([\\d]+)([\\w])");

    private int countOptions(float skill) {
        int current = 20;
        int options = 0;
        while (current <= skill) {
            current += 10;
            options += 1;
        }
        if (current > skill && current - 10 != skill)
            options += 1;
        // Donate and Mail.
        return options + 2;
    }

    private String getPriceFromMessage(String message) {
        Matcher match = priceMessageCost.matcher(message);
        if (match.find())
            return match.group(1);
        return "";
    }

    private long getIronsFromString(String changeString) {
        Matcher matches = priceDenominations.matcher(changeString);
        long irons = 0;

        while (matches.find()) {
            String[] element = new String[] { matches.group(1), matches.group(2) };
            switch (element[1]) {
                case "g":
                    irons += MonetaryConstants.COIN_GOLD * Integer.parseInt(element[0]);
                    break;
                case "s":
                    irons += MonetaryConstants.COIN_SILVER * Integer.parseInt(element[0]);
                    break;
                case "c":
                    irons += MonetaryConstants.COIN_COPPER * Integer.parseInt(element[0]);
                    break;
                case "i":
                    irons += MonetaryConstants.COIN_IRON * Integer.parseInt(element[0]);
                    break;
            }
        }

        return irons;
    }

    private void addDoneJob(Item item, int targetQL) {
        try {
            WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
            workBook.addJob(player.getWurmId(), item, targetQL, false, 1);
            Iterator<Job> jobs = workBook.iterator();
            Job lastJob = null;
            while (jobs.hasNext())
                lastJob = jobs.next();
            assert lastJob != null;
            ReflectionUtil.callPrivateMethod(workBook, WorkBook.class.getDeclaredMethod("setDone", Job.class), lastJob);
        } catch (IllegalAccessException | WorkBook.NoWorkBookOnWorker | NoSuchMethodException | InvocationTargetException | WorkBook.WorkBookFull e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testTradeOptionsAdded() {
        int skill = 50;
        crafter = factory.createNewCrafter(owner, new CrafterType(SkillList.SMITHING_BLACKSMITHING), skill);

        makeNewCrafterTrade();
        makeHandler();

        handler.addItemsToTrade();

        TradingWindow window = trade.getTradingWindow(1);
        assertThat(window, hasDonateOption());
        assertThat(window, hasMailOption());
        assertThat(window, hasOptionsForQLUpTo(skill));
        assertEquals(countOptions(skill), window.getItems().length);
    }

    @Test
    void testMaxCapBetweenQLIntervals() {
        float skill = 55.54f;
        crafter = factory.createNewCrafter(owner, new CrafterType(SkillList.SMITHING_BLACKSMITHING), skill);

        makeNewCrafterTrade();
        makeHandler();

        handler.addItemsToTrade();

        TradingWindow window = trade.getTradingWindow(1);
        assertThat(window, hasOptionsForQLUpTo(skill));
        assertEquals(countOptions(skill), window.getItems().length);
    }

    @Test
    void testItemsReadyToCollectAdded() {
        crafter = factory.createNewCrafter(owner, crafterType, 50);

        int ql = 20;
        Item item = factory.createNewItem();
        item.setQualityLevel(ql);
        crafter.getInventory().insertItem(item);
        addDoneJob(item, ql);

        makeNewCrafterTrade();
        makeHandler();

        handler.addItemsToTrade();

        assertTrue(new HashSet<>(Arrays.asList(trade.getTradingWindow(1).getItems())).contains(item));
    }

    @Test
    void testDonateOptionRemainsSoLongAsOneSkillCanUseIt() {
        Properties properties = new Properties();
        properties.setProperty("max_skill", "30");
        new CrafterMod().configure(properties);
        assert CrafterMod.getSkillCap() == 30;
        crafter = factory.createNewCrafter(owner, new CrafterType(SkillList.SMITHING_BLACKSMITHING, SkillList.CARPENTRY), 20);
        Skill blacksmithing = crafter.getSkills().getSkillOrLearn(SkillList.SMITHING_BLACKSMITHING);
        Skill carpentry = crafter.getSkills().getSkillOrLearn(SkillList.CARPENTRY);

        makeNewCrafterTrade();
        makeHandler();

        handler.addItemsToTrade();
        assertThat(trade.getTradingWindow(1), hasDonateOption());
        Arrays.asList(trade.getTradingWindow(1).getItems()).forEach(trade.getTradingWindow(1)::removeItem);

        blacksmithing.setKnowledge(30, false);
        handler.addItemsToTrade();
        assertThat(trade.getTradingWindow(1), hasDonateOption());
        Arrays.asList(trade.getTradingWindow(1).getItems()).forEach(trade.getTradingWindow(1)::removeItem);

        carpentry.setKnowledge(30, false);
        handler.addItemsToTrade();
        assertFalse(hasDonateOption().matches(trade.getTradingWindow(1)));

    }

    @Test
    void testDonateOptionCancelsOthers() {
        int skill = 50;
        crafter = factory.createNewCrafter(owner, new CrafterType(SkillList.SMITHING_BLACKSMITHING), skill);

        makeNewCrafterTrade();
        makeHandler();

        handler.addItemsToTrade();
        TradingWindow window = trade.getTradingWindow(1);

        selectOption("Donate");
        selectOption("Improve");
        selectOption("Mail");
        TradingWindow requestWindow = trade.getCreatureOneRequestWindow();

        int options = countOptions(skill);
        assertEquals(options - 3, window.getItems().length);
        assertEquals(3, requestWindow.getItems().length);

        handler.balance();

        assertEquals(options - 1, window.getItems().length);
        assertThat(requestWindow, hasDonateOption());
        assertEquals(1, requestWindow.getItems().length);
    }

    @Test
    void testImproveCosts() {
        crafter = factory.createNewCrafter(owner, crafterType, 50);

        makeNewCrafterTrade();
        makeHandler();

        handler.addItemsToTrade();

        selectOption("Improve to 20ql");
        Item pickaxe = player.getInventory().getFirstContainedItem();
        trade.getTradingWindow(2).addItem(pickaxe);

        handler.balance();

        assertEquals(new Change(handler.getTraderBuyPriceForItem(pickaxe)).getChangeShortString(), getPriceFromMessage(factory.getCommunicator(player).getLastMessage()));
    }

    @Test
    void testManyImproveCosts() {
        crafter = factory.createNewCrafter(owner, crafterType, 50);
        int numberOfItems = 4;
        assert numberOfItems * 20 < MonetaryConstants.COIN_COPPER;

        makeNewCrafterTrade();
        makeHandler();

        handler.addItemsToTrade();

        selectOption("Improve to 20ql");
        handler.balance();
        Items.destroyItem(player.getInventory().getFirstContainedItem().getWurmId());
        Iterable<Item> pickaxes = factory.createManyItems(numberOfItems, ItemList.pickAxe);
        pickaxes.forEach(player.getInventory()::insertItem);
        player.getInventory().getItems().forEach(trade.getTradingWindow(2)::addItem);

        setNotBalanced();
        handler.balance();

        assertEquals(new Change(handler.getTraderBuyPriceForItem(player.getInventory().getFirstContainedItem()) * numberOfItems).getChangeShortString(), getPriceFromMessage(factory.getCommunicator(player).getLastMessage()));
    }

    @Test
    void testImproveChoosesHighestQL() {
        crafter = factory.createNewCrafter(owner, crafterType, 50);

        makeNewCrafterTrade();
        makeHandler();

        handler.addItemsToTrade();

        selectOption("Improve to 20ql");
        selectOption("Improve to 30ql");
        Item pickaxe = player.getInventory().getFirstContainedItem();
        trade.getTradingWindow(2).addItem(pickaxe);

        handler.balance();

        assertEquals("Improve to 30ql", trade.getTradingWindow(3).getItems()[0].getName());
    }

    @Test
    void testImproveChoosesHighestQLPerSkill() throws NoSuchFieldException, IllegalAccessException {
        crafter = factory.createNewCrafter(owner, new CrafterType(SkillList.SMITHING_BLACKSMITHING, SkillList.CARPENTRY), 40);
        crafter.getSkills().getSkillOrLearn(SkillList.SMITHING_BLACKSMITHING).setKnowledge(40, false);
        crafter.getSkills().getSkillOrLearn(SkillList.CARPENTRY).setKnowledge(30, false);

        makeNewCrafterTrade();
        makeHandler();
        handler.addItemsToTrade();

        for (Item option : trade.getTradingWindow(1).getItems()) {
            if (option.getName().startsWith("Improve")) {
                trade.getTradingWindow(1).removeItem(option);
                trade.getTradingWindow(3).addItem(option);
            }
        }

        handler.balance();
        Item[] options = trade.getTradingWindow(3).getItems();
        assertEquals(2, options.length);
        Item blacksmithing;
        Item carpentry;

        BiMap<Integer, ItemTemplate> skillIcons = ReflectionUtil.getPrivateField(null, CrafterTradeHandler.class.getDeclaredField("skillIcons"));

        if (skillIcons.inverse().get(options[0].getTemplate()) == SkillList.SMITHING_BLACKSMITHING) {
            blacksmithing = options[0];
            carpentry = options[1];
        } else {
            blacksmithing = options[1];
            carpentry = options[0];
        }

        assertEquals(SkillList.SMITHING_BLACKSMITHING, (int)skillIcons.inverse().get(blacksmithing.getTemplate()));
        assertEquals("Improve to 40ql", blacksmithing.getName());
        assertEquals(SkillList.CARPENTRY, (int)skillIcons.inverse().get(carpentry.getTemplate()));
        assertEquals("Improve to 30ql", carpentry.getName());
    }

    @Test
    void testMailAddsToCost() {
        crafter = factory.createNewCrafter(owner, crafterType, 50);

        makeNewCrafterTrade();
        makeHandler();

        handler.addItemsToTrade();

        selectOption("Improve to 20ql");
        selectOption("Mail");
        Item pickaxe = player.getInventory().getFirstContainedItem();
        trade.getTradingWindow(2).addItem(pickaxe);

        handler.balance();

        assertThat(trade.getCreatureOneRequestWindow(), hasMailOption());
        assertEquals(handler.getTraderBuyPriceForItem(pickaxe) + CrafterMod.mailPrice(),
                getIronsFromString(factory.getCommunicator(player).getLastMessage()));
    }

    // Trade finalised

    @Test
    void testItemCollected() throws WorkBook.NoWorkBookOnWorker {
        crafter = factory.createNewCrafter(owner, crafterType, 50);

        int ql = 20;
        Item item = factory.createNewItem(factory.getIsSmithingId());
        item.setQualityLevel(ql);
        crafter.getInventory().insertItem(item);
        addDoneJob(item, ql);

        makeNewCrafterTrade();
        makeHandler();

        handler.addItemsToTrade();

        TradingWindow offerWindow = trade.getTradingWindow(1);
        for (Item option : offerWindow.getItems()) {
            if (option == item) {
                offerWindow.removeItem(option);
                trade.getCreatureOneRequestWindow().addItem(option);
                break;
            }
        }

        handler.balance();
        setSatisfied(player);

        assertFalse(crafter.getInventory().getItems().contains(item));
        assertTrue(player.getInventory().getItems().contains(item));
        assertEquals(0, WorkBook.getWorkBookFromWorker(crafter).todo());
    }

    @Test
    void testNewJobAdded() throws WorkBook.NoWorkBookOnWorker {
        crafter = factory.createNewCrafter(owner, crafterType, 50);
        assert WorkBook.getWorkBookFromWorker(crafter).todo() == 0;

        Item item = player.getInventory().getFirstContainedItem();
        item.setQualityLevel(1);

        makeNewCrafterTrade();
        makeHandler();

        handler.addItemsToTrade();

        selectOption("Improve to 20ql");
        handler.balance();
        trade.getTradingWindow(2).addItem(item);
        int price = handler.getTraderBuyPriceForItem(item);
        Arrays.stream(Economy.getEconomy().getCoinsFor(price)).forEach(player.getInventory()::insertItem);
        player.getInventory().getItems().forEach(trade.getTradingWindow(2)::addItem);

        setNotBalanced();
        handler.balance();
        setSatisfied(player);

        long finalPrice = afterTax(price);
        assertEquals(finalPrice, factory.getShop(crafter).getMoneyEarnedLife());
        assertTrue(crafter.getInventory().getItems().contains(item));
        assertFalse(player.getInventory().getItems().contains(item));
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        assertEquals(1, workBook.todo());
        assertEquals(price, workBook.iterator().next().getPriceCharged());
    }

    @Test
    void testItemDonated() throws WorkBook.NoWorkBookOnWorker {
        crafter = factory.createNewCrafter(owner, crafterType, 50);
        assert WorkBook.getWorkBookFromWorker(crafter).todo() == 0;

        Item item = player.getInventory().getFirstContainedItem();

        makeNewCrafterTrade();
        makeHandler();

        handler.addItemsToTrade();

        selectOption("Donate");
        trade.getTradingWindow(2).addItem(item);

        handler.balance();
        setSatisfied(player);

        assertEquals(0, factory.getShop(crafter).getMoney());
        assertTrue(crafter.getInventory().getItems().contains(item));
        assertFalse(player.getInventory().getItems().contains(item));
        assertEquals(0, WorkBook.getWorkBookFromWorker(crafter).iterator().next().getPriceCharged());
        assertTrue(WorkBook.getWorkBookFromWorker(crafter).iterator().next() instanceof Donation);
    }

    @Test
    void testPlayerCoinsRejectedIfDonating() throws WorkBook.NoWorkBookOnWorker {
        crafter = factory.createNewCrafter(owner, crafterType, 50);
        assert WorkBook.getWorkBookFromWorker(crafter).todo() == 0;

        Item item = player.getInventory().getFirstContainedItem();
        Arrays.asList(Economy.getEconomy().getCoinsFor(100)).forEach(player.getInventory()::insertItem);

        makeNewCrafterTrade();
        makeHandler();

        handler.addItemsToTrade();

        selectOption("Donate");
        player.getInventory().getItems().forEach(trade.getTradingWindow(2)::addItem);

        handler.balance();
        setSatisfied(player);

        assertEquals(0, factory.getShop(crafter).getMoney());
        assertEquals(2, crafter.getInventory().getItemCount());
        assertTrue(crafter.getInventory().getItems().contains(item));
        assertFalse(player.getInventory().getItems().contains(item));
        assertThat(player, hasCoinsOfValue(100));
        assertEquals(0, WorkBook.getWorkBookFromWorker(crafter).iterator().next().getPriceCharged());
        assertTrue(WorkBook.getWorkBookFromWorker(crafter).iterator().next() instanceof Donation);
    }

    @Test
    void testNewJobAddedWithMailOption() throws WorkBook.NoWorkBookOnWorker {
        crafter = factory.createNewCrafter(owner, crafterType, 50);
        assert WorkBook.getWorkBookFromWorker(crafter).todo() == 0;

        Item item = player.getInventory().getFirstContainedItem();

        makeNewCrafterTrade();
        makeHandler();

        handler.addItemsToTrade();

        selectOption("Improve to 20ql");
        selectOption("Mail");
        handler.balance();
        trade.getTradingWindow(2).addItem(item);
        int price = handler.getTraderBuyPriceForItem(item) + CrafterMod.mailPrice();
        Arrays.stream(Economy.getEconomy().getCoinsFor(price)).forEach(player.getInventory()::insertItem);
        player.getInventory().getItems().forEach(trade.getTradingWindow(2)::addItem);

        setNotBalanced();
        handler.balance();
        setSatisfied(player);

        assertThat(crafter, hasCoinsOfValue(0));
        assertTrue(crafter.getInventory().getItems().contains(item));
        assertFalse(player.getInventory().getItems().contains(item));
        assertEquals(1, WorkBook.getWorkBookFromWorker(crafter).todo());
        Job job = WorkBook.getWorkBookFromWorker(crafter).iterator().next();
        assertTrue(job.mailWhenDone());
        assertEquals(price, job.getPriceCharged());
    }

    @Test
    void testInsufficientMoneyAdded() {
        crafter = factory.createNewCrafter(owner, crafterType, 50);

        Item item = player.getInventory().getFirstContainedItem();

        makeNewCrafterTrade();
        makeHandler();

        handler.addItemsToTrade();

        selectOption("Improve to 20ql");
        handler.balance();
        int cost = handler.getTraderBuyPriceForItem(item);
        int notEnough = cost - 1;

        trade.getTradingWindow(2).addItem(item);
        Arrays.stream(Economy.getEconomy().getCoinsFor(notEnough)).forEach(player.getInventory()::insertItem);
        player.getInventory().getItems().forEach(trade.getTradingWindow(2)::addItem);

        setNotBalanced();
        handler.balance();
        setSatisfied(player);

        assertThat(Arrays.asList(trade.getCreatureTwoRequestWindow().getItems()), containsCoinsOfValue(notEnough));
        assertThat(player, receivedMessageContaining("I will need 1i more"));
        assertEquals(0, factory.getShop(crafter).getMoney());
        assertFalse(crafter.getInventory().getItems().contains(item));
        assertTrue(player.getInventory().getItems().contains(item));
    }

    @Test
    void testItemQLOverSkillCap() {
        int skill = 50;
        crafter = factory.createNewCrafter(owner, crafterType, skill);

        Item item = factory.createNewItem(factory.getIsSmithingId());
        item.setQualityLevel(skill + 1);
        player.getInventory().insertItem(item);

        makeNewCrafterTrade();
        makeHandler();

        handler.addItemsToTrade();

        selectOption("Improve to 50ql");
        trade.getTradingWindow(2).addItem(item);

        handler.balance();

        assertEquals(0, trade.getCreatureTwoRequestWindow().getItems().length);
        assertEquals(1, trade.getTradingWindow(2).getItems().length);
    }

    @Test
    void testItemQLOverOptionQL() {
        int skill = 50;
        crafter = factory.createNewCrafter(owner, crafterType, skill);

        Item item = factory.createNewItem(factory.getIsSmithingId());
        item.setQualityLevel(skill);
        player.getInventory().insertItem(item);

        makeNewCrafterTrade();
        makeHandler();

        handler.addItemsToTrade();

        selectOption("Improve to 20ql");
        trade.getTradingWindow(2).addItem(item);

        handler.balance();

        assertEquals(0, trade.getCreatureTwoRequestWindow().getItems().length);
        assertEquals(1, trade.getTradingWindow(2).getItems().length);
    }

    @Test
    void testMailOnlyOptionDoesNothing() {
        crafter = factory.createNewCrafter(owner, crafterType, 50);

        Item item = player.getInventory().getFirstContainedItem();

        makeNewCrafterTrade();
        makeHandler();

        handler.addItemsToTrade();

        selectOption("Mail");
        trade.getTradingWindow(2).addItem(item);

        handler.balance();

        assertEquals(0, trade.getCreatureTwoRequestWindow().getItems().length);
        assertEquals(1, trade.getTradingWindow(2).getItems().length);
        assertThat(player, didNotReceiveMessageContaining("I need"));
    }

    @Test
    void testCrafterDoesNotAddMoneyIfNotEnoughForJob() {
        crafter = factory.createNewCrafter(owner, crafterType, 50);

        makeNewCrafterTrade();
        makeHandler();

        handler.addItemsToTrade();

        selectOption("Improve to 20ql");
        Arrays.stream(Economy.getEconomy().getCoinsFor(20)).forEach(player.getInventory()::insertItem);
        player.getInventory().insertItem(factory.createNewItem(factory.getIsSmithingId()));
        player.getInventory().getItems().forEach(i -> trade.getTradingWindow(2).addItem(i));

        handler.balance();

        assertFalse(!containsCoinsOfValue(0).matches(Arrays.asList(trade.getCreatureOneRequestWindow().getItems())));
        assertThat(player, receivedMessageContaining("I will need"));
    }

    @Test
    void testCrafterDoesNotRepeatMessageIfInsufficientFundsOffered() {
        crafter = factory.createNewCrafter(owner, crafterType, 50);

        makeNewCrafterTrade();
        makeHandler();

        handler.addItemsToTrade();

        selectOption("Improve to 20ql");
        Arrays.stream(Economy.getEconomy().getCoinsFor(20)).forEach(player.getInventory()::insertItem);
        player.getInventory().insertItem(factory.createNewItem(factory.getIsSmithingId()));
        player.getInventory().getItems().forEach(i -> trade.getTradingWindow(2).addItem(i));

        assertFalse(!containsCoinsOfValue(0).matches(Arrays.asList(trade.getCreatureOneRequestWindow().getItems())));

        handler.balance();
        assertThat(player, receivedMessageContaining("I will need"));
        int numberOfMessages = factory.getCommunicator(player).getMessages().length;
        handler.balance();
        assertEquals(numberOfMessages, factory.getCommunicator(player).getMessages().length);
    }

    @Test
    void testCrafterDoesRepeatMessageIfTradeChanged() {
        crafter = factory.createNewCrafter(owner, crafterType, 50);

        makeNewCrafterTrade();
        makeHandler();

        handler.addItemsToTrade();

        selectOption("Improve to 20ql");
        Arrays.stream(Economy.getEconomy().getCoinsFor(20)).forEach(player.getInventory()::insertItem);
        player.getInventory().insertItem(factory.createNewItem(factory.getIsSmithingId()));
        player.getInventory().getItems().forEach(i -> trade.getTradingWindow(2).addItem(i));

        assertFalse(!containsCoinsOfValue(0).matches(Arrays.asList(trade.getCreatureOneRequestWindow().getItems())));

        handler.balance();
        assertThat(player, receivedMessageContaining("I will need"));
        int numberOfMessages = factory.getCommunicator(player).getMessages().length;
        handler.tradeChanged();
        handler.balance();
        assertNotEquals(numberOfMessages, factory.getCommunicator(player).getMessages().length);
    }

    @Test
    void testCrafterDoesNotSuckItemsPurelyOnQL() throws WorkBook.NoWorkBookOnWorker {
        crafter = factory.createNewCrafter(owner, crafterType, 50);
        assert WorkBook.getWorkBookFromWorker(crafter).todo() == 0;

        Item item = player.getInventory().getFirstContainedItem();
        item.setQualityLevel(1);

        makeNewCrafterTrade();
        makeHandler();

        handler.addItemsToTrade();

        selectOption("Improve to 20ql");
        handler.balance();
        trade.getTradingWindow(2).addItem(item);
        getMoneyForItem(item).forEach(i -> {
            i.setQualityLevel(100);
            player.getInventory().insertItem(i);
        });
        player.getInventory().getItems().forEach(trade.getTradingWindow(2)::addItem);

        setNotBalanced();
        handler.balance();
        setSatisfied(player);

        assertThat(player, didNotReceiveMessageContaining("I will need"));
        assertThat(player, receivedMessageContaining("completed successfully"));
        assertTrue(crafter.getInventory().getItems().contains(item));
        assertFalse(player.getInventory().getItems().contains(item));
    }

    @Test
    void testOptionItemsDestroyedOnTradeEnd() {
        makeNewCrafterTrade();
        makeHandler();
        handler.addItemsToTrade();

        List<Item> options = Arrays.asList(trade.getTradingWindow(1).getItems());
        assert !options.isEmpty();

        handler.balance();
        handler.end();

        for (Item item : options) {
            assertThrows(NoSuchItemException.class, () -> Items.getItem(item.getWurmId()));
        }
    }

    @Test
    void testOptionItemsDestroyedOnTradeEndByCloseWindow() {
        makeNewCrafterTrade();
        makeHandler();
        handler.addItemsToTrade();

        List<Item> options = Arrays.asList(trade.getTradingWindow(1).getItems());
        assert !options.isEmpty();

        handler.balance();
        crafter.getCommunicator().sendCloseTradeWindow();

        for (Item item : options) {
            assertThrows(NoSuchItemException.class, () -> Items.getItem(item.getWurmId()));
        }
    }

    @Test
    void testDecreaseInOptionPriceResetsChangeProperly() {
        makeNewCrafterTrade();
        makeHandler();
        handler.addItemsToTrade();

        selectOption("Improve to 20");
        selectOption("Mail");

        player.getInventory().insertItem(factory.createNewItem(ItemList.coinSilver));
        player.getInventory().getItems().forEach(trade.getTradingWindow(2)::addItem);

        handler.balance();
        assert trade.getTradingWindow(2).getItems().length == 0;
        assertThat(trade.getTradingWindow(3), windowContainsCoinsOfValue(MonetaryConstants.COIN_SILVER - handler.getTraderBuyPriceForItem(tool) - CrafterMod.mailPrice()));

        deselectOption("Mail");

        handler.balance();
        assertThat(trade.getTradingWindow(3), windowContainsCoinsOfValue(MonetaryConstants.COIN_SILVER - handler.getTraderBuyPriceForItem(tool)));
    }

    @Test
    void testNoImproveAndNewbieItemsNotAllowed() {
        crafter = factory.createNewCrafter(owner, new CrafterType(SkillList.GROUP_SMITHING_WEAPONSMITHING), 50);
        makeNewCrafterTrade();
        makeHandler();
        handler.addItemsToTrade();
        selectOption("Improve to 20");

        Item notNewbie = factory.createNewItem(ItemList.swordLong);
        Item newbie = factory.createNewItem(ItemList.swordLong);
        newbie.setAuxData((byte)1);
        Item noImprove = factory.createNewItem(ItemList.swordLong);
        noImprove.setIsNoImprove(true);
        player.getInventory().getItems().forEach(i -> Items.destroyItem(i.getWurmId()));
        player.getInventory().insertItem(notNewbie);
        player.getInventory().insertItem(newbie);
        player.getInventory().insertItem(noImprove);
        Arrays.asList(Economy.getEconomy().getCoinsFor(MonetaryConstants.COIN_SILVER)).forEach(player.getInventory()::insertItem);

        player.getInventory().getItems().forEach(trade.getTradingWindow(2)::addItem);
        handler.balance();

        assertEquals(2, trade.getTradingWindow(2).getItems().length);
        assertEquals(2, trade.getTradingWindow(4).getItems().length);
        assertTrue(Arrays.asList(trade.getTradingWindow(2).getItems()).contains(newbie));
        assertTrue(Arrays.asList(trade.getTradingWindow(2).getItems()).contains(noImprove));
        assertTrue(Arrays.asList(trade.getTradingWindow(4).getItems()).contains(notNewbie));
    }

    @Test
    void testSendsMessageForStrungBows() {
        crafter = factory.createNewCrafter(owner, new CrafterType(SkillList.GROUP_BOWYERY), 50);
        makeNewCrafterTrade();
        makeHandler();
        handler.addItemsToTrade();
        selectOption("Improve to 20");
        trade.getTradingWindow(2).addItem(factory.createNewItem(ItemList.bowLong));
        handler.balance();

        assertThat(player, receivedMessageContaining("unstring"));
    }

    @Test
    void testDoesNotSendStrungBowMessageIfCannotImproveBows() {
        crafter = factory.createNewCrafter(owner, new CrafterType(SkillList.SMITHING_BLACKSMITHING), 50);
        makeNewCrafterTrade();
        makeHandler();
        handler.addItemsToTrade();
        selectOption("Improve to 20");
        trade.getTradingWindow(2).addItem(factory.createNewItem(ItemList.bowLong));
        handler.balance();

        assertThat(player, didNotReceiveMessageContaining("unstring"));
        assertThat(player, receivedMessageContaining("cannot improve"));
    }

    // Stone brick bug report.
    @Test
    void testNonRepairableItemsNotAccepted() {
        crafter = factory.createNewCrafter(factory.createNewPlayer(), new CrafterType(SkillList.STONECUTTING), 50);
        Item brick = factory.createNewItem(ItemList.stoneBrick);
        player.getInventory().insertItem(brick);

        makeNewCrafterTrade();
        makeHandler();
        handler.addItemsToTrade();
        selectOption("Improve to 20");
        trade.getTradingWindow(2).addItem(brick);
        handler.balance();

        assertEquals(0, trade.getTradingWindow(4).getItems().length);
        assertThat(player, receivedMessageContaining("cannot improve"));
    }

    // For exploratory testing.
//    @Test
//    void testPrintOutAllAcceptedItems() {
//        List<String> accepted = new ArrayList<>();
//        List<Integer> allTypes = new ArrayList<>();
//        Collections.addAll(allTypes, CrafterType.allMetal);
//        Collections.addAll(allTypes, CrafterType.allWood);
//        Collections.addAll(allTypes, CrafterType.allArmour);
//        crafter = factory.createNewCrafter(factory.createNewPlayer(), new CrafterType(allTypes.toArray(new Integer[0])), 50);
//        makeNewCrafterTrade();
//        makeHandler();
//        handler.addItemsToTrade();
//
//        for (Item item : trade.getTradingWindow(1).getItems()) {
//            if (item.getName().startsWith("Improve to 20")) {
//                trade.getTradingWindow(1).removeItem(item);
//                trade.getTradingWindow(3).addItem(item);
//            }
//        }
//
//        for (int i = 0; i < 10000; i++) {
//            try {
//                ItemTemplateFactory.getInstance().getTemplate(i);
//            } catch (NoSuchTemplateException e) {
//                continue;
//            }
//            Item item = factory.createNewItem(i);
//            player.getInventory().insertItem(item, true);
//
//            setNotBalanced();
//            for (Item it : trade.getTradingWindow(2).getItems())
//                trade.getTradingWindow(2).removeItem(it);
//            for (Item it : trade.getTradingWindow(4).getItems())
//                trade.getTradingWindow(4).removeItem(it);
//
//            trade.getTradingWindow(2).addItem(item);
//            handler.balance();
//
//            if (!factory.getCommunicator(player).getLastMessage().contains("cannot improve")) {
//                accepted.add(item.getName());
//            }
//        }
//
//        for (String s : accepted) {
//            System.out.println(s);
//        }
//    }

    @Test
    void testCrafterMoneyNotAffectedByTrade() throws WorkBook.NoWorkBookOnWorker {
        int startingMoney = 100;
        crafter = factory.createNewCrafter(owner, crafterType, 50);
        assert WorkBook.getWorkBookFromWorker(crafter).todo() == 0;
        factory.getShop(crafter).setMoney(startingMoney);

        Item item = player.getInventory().getFirstContainedItem();
        item.setQualityLevel(1);

        makeNewCrafterTrade();
        makeHandler();

        handler.addItemsToTrade();

        selectOption("Improve to 20ql");
        trade.getTradingWindow(2).addItem(item);
        handler.balance();
        int price = handler.getTraderBuyPriceForItem(item);
        Arrays.stream(Economy.getEconomy().getCoinsFor(price)).forEach(player.getInventory()::insertItem);
        player.getInventory().getItems().forEach(trade.getTradingWindow(2)::addItem);

        long jobPrice = handler.getTraderBuyPriceForItem(item);
        setNotBalanced();
        handler.balance();
        setSatisfied(player);

        assertEquals(startingMoney + afterTax(jobPrice), factory.getShop(crafter).getMoney());
        assertTrue(crafter.getInventory().getItems().contains(item));
        assertFalse(player.getInventory().getItems().contains(item));
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        assertEquals(1, workBook.todo());
    }

    @Test
    void testOnlyAcceptsDonationsThatCanBeUsed() throws WorkBook.NoWorkBookOnWorker {
        crafter = factory.createNewCrafter(owner, new CrafterType(SkillList.SMITHING_BLACKSMITHING), 50);
        assert WorkBook.getWorkBookFromWorker(crafter).donationsTodo() == 0;

        Item item1 = factory.createNewItem(factory.getIsBlacksmithingId());
        Item item2 = factory.createNewItem(factory.getIsJewellerysmithingId());
        item2.setMaterial(ItemMaterials.MATERIAL_GOLD);
        player.getInventory().insertItem(item1);
        player.getInventory().insertItem(item2);


        makeNewCrafterTrade();
        makeHandler();

        handler.addItemsToTrade();

        selectOption("Donate");
        trade.getTradingWindow(2).addItem(item1);
        trade.getTradingWindow(2).addItem(item2);

        handler.balance();
        setSatisfied(player);

        assertEquals(0, factory.getShop(crafter).getMoney());
        assertTrue(crafter.getInventory().getItems().contains(item1));
        assertFalse(player.getInventory().getItems().contains(item1));
        assertFalse(crafter.getInventory().getItems().contains(item2));
        assertTrue(player.getInventory().getItems().contains(item2));
        assertEquals(0, WorkBook.getWorkBookFromWorker(crafter).iterator().next().getPriceCharged());
        assertTrue(WorkBook.getWorkBookFromWorker(crafter).iterator().next() instanceof Donation);
    }
}
