package com.wurmonline.server.creatures;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.wurmonline.server.Items;
import com.wurmonline.server.behaviours.MethodsItems;
import com.wurmonline.server.economy.Change;
import com.wurmonline.server.economy.Economy;
import com.wurmonline.server.economy.Shop;
import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.shared.constants.ItemMaterials;
import mod.wurmunlimited.npcs.CrafterMod;
import mod.wurmunlimited.npcs.Job;
import mod.wurmunlimited.npcs.WorkBook;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

public class CrafterTradeHandler extends TradeHandler {
    private static final Logger logger = Logger.getLogger(CrafterTradeHandler.class.getName());
    private Creature creature;
    private WorkBook workBook;
    private float skillCap;
    private boolean ownerTrade;
    private CrafterTrade trade;
    private Map<Integer, Float> targetQLs = new HashMap<>();
    private boolean balanced = false;
    private boolean mailWhenDone = false;
    private boolean donating = false;
    private float priceModifier;
    private List<Item> optionItems = new ArrayList<>();
    private static BiMap<Integer, ItemTemplate> skillIcons = HashBiMap.create();
    private Set<Item> coinsToCollect = new HashSet<>();

    public CrafterTradeHandler(Creature crafter, CrafterTrade _trade) {
        creature = crafter;
        trade = _trade;
        workBook = _trade.getWorkBook();
        skillCap = workBook.getSkillCap();
        Shop shop = Economy.getEconomy().getShop(crafter);
        priceModifier = shop.getPriceModifier();
        ownerTrade = shop.getOwnerId() == trade.creatureOne.getWurmId();
        if (ownerTrade) {
            trade.creatureOne.getCommunicator().sendSafeServerMessage(crafter.getName() + " says, 'Welcome back, " + trade.creatureOne.getName() + "!'");
        } else {
            trade.creatureOne.getCommunicator().sendSafeServerMessage(crafter.getName() + " says, 'How can I help you today?'");
        }
        if (trade.creatureOne.getPower() >= 3) {
            String changeString = (new Change(shop.getMoney())).getChangeShortString();
            trade.creatureOne.getCommunicator().sendSafeServerMessage(crafter.getName() + " says, 'I have " + (changeString.length() > 0 ? changeString : "0i") + ".'");
        }

        if (skillIcons.isEmpty())
            buildSkillIcons();
    }

    private void addOption(String label, ItemTemplate icon, float ql) throws IOException {
        addOption(label, icon, -1, ql);
    }

    private void addOption(String label, ItemTemplate icon, int price, float ql) throws IOException {
        TradingWindow window = trade.getTradingWindow(1);
        TempItem item = new TempItem(label, icon, ql, "");
        item.setMaterial((byte)0);
        item.setWeight(0, false);
        if (price == -1)
            item.setPrice(getPriceForImproveOption(item, (int)ql));
        else
            item.setPrice(price);
        item.setOwnerId(creature.getWurmId());
        window.addItem(item);
        optionItems.add(item);
    }

    @Override
    public void addItemsToTrade() {
        // Menu option items
        try {
            boolean atSkillCap = true;
            for (Skill skill : workBook.getCrafterType().getSkillsFor(creature)) {
                int current = 20;
                ItemTemplate template = skillIcons.get(skill.getNumber());
                if (atSkillCap && skill.getKnowledge() < CrafterMod.getSkillCap())
                    atSkillCap = false;
                if (template == null)
                    throw new NoSuchTemplateException("ItemTemplate not found for option icons.  Did buildSkillIcons fail?");
                while (current <= skill.getKnowledge() && current <= skillCap && current <= CrafterMod.getSkillCap()) {
                    addOption("Improve to " + current + "ql", template, current);
                    current += 10;
                }
                if (current > skillCap && (float)current - 10.0f != skillCap)
                    addOption(String.format("Improve to %.1fql", skillCap), template, current);
            }

            addOption("Mail to me when done", ItemTemplateFactory.getInstance().getTemplate(ItemList.mailboxWood), CrafterMod.mailPrice(), 1);
            if (CrafterMod.canLearn() && !atSkillCap)
                addOption("Donate Items", ItemTemplateFactory.getInstance().getTemplate(ItemList.backPack), 0, 1);
        } catch (IOException | NoSuchTemplateException e) {
            logger.warning("Could not add menu option to trade window.  Reason follows:");
            e.printStackTrace();
        }

        TradingWindow offerWindow = trade.getTradingWindow(1);

        if (workBook.done() > 0) {
            for (Job job : workBook) {
                if (job.isDonation())
                    break;
                if (job.isDone() && job.isCustomer(trade.creatureOne)) {
                    offerWindow.addItem(job.getItem());
                }
            }
        }

        if (ownerTrade && CrafterMod.getPaymentOption() == CrafterMod.PaymentOption.for_owner) {
            for (Item coin : Economy.getEconomy().getCoinsFor((long)(workBook.getMoneyToCollect() * 0.9f))) {
                offerWindow.addItem(coin);
                coinsToCollect.add(coin);
            }
        }
    }

    private void buildSkillIcons() {
        try {
            ItemTemplateFactory factory = ItemTemplateFactory.getInstance();
            skillIcons.put(SkillList.SMITHING_BLACKSMITHING, factory.getTemplate(ItemList.hammerMetal));
            skillIcons.put(SkillList.SMITHING_GOLDSMITHING, factory.getTemplate(ItemList.statuette));
            skillIcons.put(SkillList.GROUP_SMITHING_WEAPONSMITHING, factory.getTemplate(ItemList.swordLong));
            skillIcons.put(SkillList.SMITHING_ARMOUR_CHAIN, factory.getTemplate(ItemList.armourChains));
            skillIcons.put(SkillList.SMITHING_ARMOUR_PLATE, factory.getTemplate(ItemList.plateJacket));
            skillIcons.put(SkillList.CARPENTRY, factory.getTemplate(ItemList.hammerWood));
            skillIcons.put(SkillList.CARPENTRY_FINE, factory.getTemplate(ItemList.bedHeadboard));
            skillIcons.put(SkillList.GROUP_FLETCHING, factory.getTemplate(ItemList.arrowHunting));
            skillIcons.put(SkillList.GROUP_BOWYERY, factory.getTemplate(ItemList.bowLongNoString));
            skillIcons.put(SkillList.LEATHERWORKING, factory.getTemplate(ItemList.leather));
            skillIcons.put(SkillList.CLOTHTAILORING, factory.getTemplate(ItemList.clothYard));
            skillIcons.put(SkillList.STONECUTTING, factory.getTemplate(ItemList.grindstone));
            skillIcons.put(SkillList.SMITHING_SHIELDS, factory.getTemplate(ItemList.shieldMedium));
            skillIcons.put(SkillList.POTTERY, factory.getTemplate(ItemList.bowlClay));
        } catch (NoSuchTemplateException e) {
            logger.severe("Error when loading ItemTemplates.  This shouldn't happen.");
            e.printStackTrace();
        }
    }

    @Override
    void addToInventory(Item item, long inventoryWindow) {
        if (trade != null && inventoryWindow == 2L) {
            tradeChanged();
        }
    }

    @Override
    void end() {
        for (Item option : optionItems)
            Items.destroyItem(option.getWurmId());

        for (Item coin : coinsToCollect) {
            if (coin.getOwnerId() == -10)
                Economy.getEconomy().returnCoin(coin, "NoTrade");
        }
    }

    @Override
    void tradeChanged() {
        balanced = false;
    }

    @Override
    public int getTraderSellPriceForItem(Item item, TradingWindow window) {
        return 0;
    }

    @Override
    public int getTraderBuyPriceForItem(Item item) {
        return getPriceForImproveOption(item, getTargetQL(item));
    }

    private int getPriceForImproveOption(Item item, float ql) {
        float basePrice = CrafterMod.getBasePriceForSkill(MethodsItems.getImproveSkill(item)) * priceModifier;
        float itemQL = item.getQualityLevel();
        double current = itemQL >= 70 ? basePrice * priceCalculation(itemQL) : basePrice * priceCalculationSub70(itemQL);
        double target = ql >= 70 ? basePrice * priceCalculation(ql) : basePrice * priceCalculationSub70(ql);

        if (item.isDragonArmour())
            return (int)((target - current) * CrafterMod.getBasePriceForSkill(-1));

        return (int)(target - current);
    }

    private long priceCalculationSub70(float x) {
        // Curve gets messy when including values below 70.  Using simple curve instead.
        // Thank you LibreOffice.
        return Math.round(0.190567 * Math.pow(x, 2.016126));
    }

    private long priceCalculation(float x) {
        // Based on 70ql=10c, 80ql=30c, 90ql=90c, 91ql=1s - Thank you The House of Lords one stop shop thread.
        // Thank you LibreOffice.
        return Math.round((0.779220779220503 * Math.pow(x, 3)) - (167.01298701292 * Math.pow(x, 2)) + (12083.1168831115 * x) - 293727.27272713);
        //return Math.pow(0.206161 * x, 2) - (28.9442 * x) + 1025.94;
    }

    private void suckInterestingItems() {
        TradingWindow offerWindow = trade.getTradingWindow(2);
        TradingWindow myWindow = trade.getCreatureTwoRequestWindow();
        for (Item item : offerWindow.getItems()) {
            if (item.isCoin() || donating || (item.getQualityLevel() < getTargetQL(item) && !item.isNoImprove() && item.isRepairable() && !item.isNewbieItem() && !item.isChallengeNewbieItem())) {
                offerWindow.removeItem(item);
                myWindow.addItem(item);
            } else if (item.isWeaponBow() && targetQLs.keySet().contains(SkillList.GROUP_BOWYERY)) {
                trade.creatureOne.getCommunicator().sendSafeServerMessage(creature.getName() + " says 'Please unstring any bows.'");
            }
        }

        int length = offerWindow.getItems().length;
        if (length > 0) {
            trade.creatureOne.getCommunicator().sendNormalServerMessage(creature.getName() + " says 'I cannot improve " + (length == 1 ? "that item.'" : "those items.'"));
        }
    }

    private void setOptions() {
        Item mailWhenDoneItem = null;
        Map<Integer, Item> currentHighest = new HashMap<>();
        donating = false;
        mailWhenDone = false;

        TradingWindow myOffers = trade.getTradingWindow(1);
        TradingWindow jobDetailsWindow = trade.getCreatureOneRequestWindow();
        for (Item item : jobDetailsWindow.getItems()) {
            if (!optionItems.contains(item)) {
                continue;
            }
            if (!donating) {
                if (item.getName().startsWith("Donate")) {
                    donating = true;
                    if (!currentHighest.isEmpty()) {
                        for (Item it : currentHighest.values()) {
                            jobDetailsWindow.removeItem(it);
                            myOffers.addItem(it);
                        }
                        currentHighest.clear();
                    }
                    if (mailWhenDoneItem != null) {
                        jobDetailsWindow.removeItem(mailWhenDoneItem);
                        myOffers.addItem(mailWhenDoneItem);
                        mailWhenDone = false;
                        mailWhenDoneItem = null;
                    }
                    continue;
                } else if (item.getName().startsWith("Mail")) {
                    mailWhenDoneItem = item;
                    mailWhenDone = true;
                    continue;
                }

                int skill = skillIcons.inverse().get(item.getTemplate());
                Item currentHighestQL = currentHighest.get(skill);
                if (currentHighestQL == null || item.getQualityLevel() > currentHighestQL.getQualityLevel()) {
                    if (currentHighestQL != null) {
                        jobDetailsWindow.removeItem(currentHighestQL);
                        myOffers.addItem(currentHighestQL);
                    }
                    currentHighest.put(skill, item);
                }
            } else {
                jobDetailsWindow.removeItem(item);
                myOffers.addItem(item);
            }
        }

        targetQLs.clear();
        for (Map.Entry<Integer, Item> target : currentHighest.entrySet()) {
            targetQLs.put(target.getKey(), target.getValue().getQualityLevel());
        }
    }

    @Override
    public void balance() {
        Creature player = trade.creatureOne;
        TradingWindow jobDetailsWindow = trade.getCreatureOneRequestWindow();
        TradingWindow playerWindow = trade.getCreatureTwoRequestWindow();

        if (!balanced) {
            setOptions();
            if (jobDetailsWindow.getItems().length == 0) {
                for (Item item : playerWindow.getItems()) {
                    playerWindow.removeItem(item);
                    trade.getTradingWindow(2).addItem(item);
                }
                trade.setSatisfied(creature, true, trade.getCurrentCounter());
                balanced = true;
                return;
            }

            suckInterestingItems();
            removeChange();
            for (Item item : playerWindow.getItems()) {
                if (player.getWurmId() != item.getOwnerId()) {
                    playerWindow.removeItem(item);
                    logger.warning("Player (" + player.getWurmId() + ") tried to trade an item (" + item.getWurmId() + ") they did not own.");
                }
            }

            if (!donating || ownerTrade) {
                int cost = 0;
                int money = 0;
                for (Item item : playerWindow.getItems()) {
                    if (item.isCoin()) {
                        money += Economy.getValueFor(item.getTemplateId());
                    } else {
                        cost += getPriceForImproveOption(item, getTargetQL(item));
                        if (mailWhenDone)
                            cost += CrafterMod.mailPrice();
                    }
                }

                int diff = cost - money;

                if (diff > 0) {
                    player.getCommunicator().sendSafeServerMessage(creature.getName() + " says 'I will need " + new Change(diff).getChangeShortString() + " more to accept the job.'");
                    balanced = true;
                    return;
                } else if (diff < 0) {
                    long needed = Math.abs(diff);
                    for (Item coin : Economy.getEconomy().getCoinsFor(needed)) {
                        jobDetailsWindow.addItem(coin);
                    }
                }
                trade.setMoneyAdded(cost);
                trade.setSatisfied(creature, true, trade.getCurrentCounter());
                balanced = true;
            } else if (donating) {
                for (Item item : playerWindow.getItems()) {
                    if (item.isCoin()) {
                        playerWindow.removeItem(item);
                        trade.getTradingWindow(2).addItem(item);
                    }
                }
                player.getCommunicator().sendSafeServerMessage(creature.getName() + " says 'If you wish to donate these items, I'll be happy to take them to improve my skills.'");
                trade.setSatisfied(creature, true, trade.getCurrentCounter());
                balanced = true;
            }
        }
    }

    private void removeChange() {
        for (Item item : trade.getTradingWindow(3).getItems()) {
            if (item.isCoin() && !coinsToCollect.contains(item))
                trade.getTradingWindow(3).removeItem(item);
        }
    }

    public float getTargetQL(Item item) {
        Float targetQL = targetQLs.get(MethodsItems.getImproveSkill(item));
        if (targetQL == null)
            return 0;
        return targetQL;
    }

    public boolean isMailOnDone() {
        return mailWhenDone;
    }

    public boolean isDonating() {
        return donating;
    }

    public boolean isOptionItem(Item item) {
        return optionItems.contains(item);
    }
}
