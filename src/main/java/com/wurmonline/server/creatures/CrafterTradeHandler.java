package com.wurmonline.server.creatures;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.wurmonline.server.Items;
import com.wurmonline.server.economy.Change;
import com.wurmonline.server.economy.Economy;
import com.wurmonline.server.economy.Shop;
import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.villages.Village;
import com.wurmonline.server.villages.VillageRole;
import com.wurmonline.shared.util.MaterialUtilities;
import mod.wurmunlimited.npcs.*;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class CrafterTradeHandler extends TradeHandler {
    private static final Logger logger = Logger.getLogger(CrafterTradeHandler.class.getName());
    private final Creature creature;
    private final WorkBook workBook;
    private final float skillCap;
    private final float qlCap;
    private final boolean ownerTrade;
    private final CrafterTrade trade;
    private final Map<Integer, Float> targetQLs = new HashMap<>();
    private boolean balanced = false;
    private boolean mailWhenDone = false;
    private DonationType donating = DonationType.not;
    private final float priceModifier;
    private final List<Item> optionItems = new ArrayList<>();
    private static final BiMap<Integer, ItemTemplate> skillIcons = HashBiMap.create();
    private final Set<Item> coinsToCollect = new HashSet<>();

    public enum DonationType {
        donating, giving, not
    }

    public CrafterTradeHandler(Creature crafter, CrafterTrade _trade) {
        creature = crafter;
        trade = _trade;
        workBook = _trade.getWorkBook();
        skillCap = workBook.getSkillCap();
        qlCap = CrafterMod.getMaxItemQL();
        Shop shop = Economy.getEconomy().getShop(crafter);
        if (CrafterMod.canUsePriceModifier())
            priceModifier = shop.getPriceModifier();
        else
            priceModifier = 1.0f;
        ownerTrade = shop.getOwnerId() == trade.creatureOne.getWurmId();
        if (ownerTrade) {
            StringBuilder sb = new StringBuilder();
            sb.append(crafter.getName()).append(" says, 'Welcome back, ").append(trade.creatureOne.getName()).append("!'");

            Village village = crafter.getCurrentVillage();
            if (village != null) {
                VillageRole role = village.getRoleFor(crafter);

                if (!role.mayImproveAndRepair()) {
                    sb.append(" I need permission to \"Improve\" in this village.");
                }

                if (!role.mayPickup()) {
                    if (workBook.isForgeAssigned()) {
                        sb.append(" I need permission to \"Pickup\" in this village.");
                    } else {
                        sb.append(" I would need permission to \"Pickup\" in this village to use a forge.");
                    }
                }
            }

            trade.creatureOne.getCommunicator().sendSafeServerMessage(sb.toString());
        } else {
            trade.creatureOne.getCommunicator().sendSafeServerMessage(crafter.getName() + " says, 'How can I help you today?'");
        }
        if (trade.creatureOne.getPower() >= 3) {
            String changeString = (new Change(shop.getMoney())).getChangeShortString();
            trade.creatureOne.getCommunicator().sendSafeServerMessage(crafter.getName() + " says, 'I have " + (!changeString.isEmpty() ? changeString : "0i") + ".'");
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
                    throw new NoSuchTemplateException("ItemTemplate not found for option icons.  Did buildSkillIcons fail? (" + skill.getNumber() + ")");
                while (current <= skill.getKnowledge() && current <= skillCap && current <= qlCap) {
                    addOption("Improve to " + current + "ql", template, current);
                    current += 10;
                }
                if (current > skillCap && skillCap <= qlCap && (float)current - 10.0f != skillCap)
                    addOption(String.format("Improve to %.1fql", skillCap), template, skillCap);
            }

            if (trade.getTradingWindow(1).getItems().length == 0) {
                if (workBook.getCrafterType().getSkillsFor(creature).isEmpty()) {
                    trade.creatureOne.getCommunicator().sendAlertServerMessage(creature.getName() + " says 'I don't have any skills that would be of use.'");
                } else {
                    trade.creatureOne.getCommunicator().sendAlertServerMessage(creature.getName() + " says 'I can't remember what my skills are.'");
                }
            }

            addOption("Mail to me when done", ItemTemplateFactory.getInstance().getTemplate(ItemList.mailboxWood), CrafterMod.mailPrice(), 1);
            if (CrafterMod.canLearn() && !atSkillCap)
                addOption("Donate Items", ItemTemplateFactory.getInstance().getTemplate(ItemList.backPack), 0, 1);
            if (ownerTrade || trade.creatureOne.getPower() >= 2)
                addOption("Give tools", ItemTemplateFactory.getInstance().getTemplate(ItemList.satchel), 0, 1);
        } catch (IOException | NoSuchTemplateException e) {
            logger.warning("Could not add menu option to trade window.  Reason follows:");
            e.printStackTrace();
        }

        TradingWindow offerWindow = trade.getTradingWindow(1);

        List<Job> toRemove = new ArrayList<>();
        if (workBook.done() > 0) {
            for (Job job : workBook) {
                if (job.isDonation())
                    break;
                // Check for bad entries.
                if (job.isDone() && !creature.getInventory().getItems().contains(job.getItem())) {
                    logger.warning("Item for completed Job could not be found, removing from workbook.");
                    toRemove.add(job);
                    continue;
                }
                if (job.isDone() && job.isCustomer(trade.creatureOne)) {
                    offerWindow.addItem(job.getItem());
                }
            }
        }

        for (Job job : toRemove) {
            workBook.removeJob(job.getItem());
        }

        if (ownerTrade && CrafterMod.getPaymentOption() == CrafterMod.PaymentOption.for_owner) {
            for (Item coin : Economy.getEconomy().getCoinsFor(creature.getShop().getMoney())) {
                offerWindow.addItem(coin);
                coinsToCollect.add(coin);
            }
        }
        if (ownerTrade || trade.creatureOne.getPower() >= 2) {
            CrafterAIData data = (CrafterAIData)creature.getCreatureAIData();
            for (Item tool : data.tools.getGivenTools()) {
                offerWindow.addItem(tool);
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
        float basePrice = CrafterMod.getBasePriceForSkill(CrafterType.getNearestSkill(item)) * priceModifier;
        float itemQL = item.getQualityLevel();
        double current = itemQL >= 70 ? basePrice * priceCalculation(itemQL) : basePrice * priceCalculationSub70(itemQL);
        double target = ql >= 70 ? basePrice * priceCalculation(ql) : basePrice * priceCalculationSub70(ql);

        if (item.isDragonArmour())
            return (int)((target - current) * CrafterMod.getBasePriceForSkill(CrafterMod.DRAGON_ARMOUR));

        if (item.isMoonMetal())
            return (int)((target - current) * CrafterMod.getBasePriceForSkill(CrafterMod.MOON_METAL));

        return (int)(target - current);
    }

    private long priceCalculationSub70(float x) {
        // Curve gets messy when including values below 70.  Using simple curve instead.
        // Thank you, LibreOffice.
        return Math.round(0.190567 * Math.pow(x, 2.016126));
    }

    private long priceCalculation(float x) {
        // Based on 70ql=10c, 80ql=30c, 90ql=90c, 91ql=1s - Thank you The House of Lords one-stop shop thread.
        // Thank you, LibreOffice.
        return Math.round((0.779220779220503 * Math.pow(x, 3)) - (167.01298701292 * Math.pow(x, 2)) + (12083.1168831115 * x) - 293727.27272713);
        //return Math.pow(0.206161 * x, 2) - (28.9442 * x) + 1025.94;
    }

    private void suckInterestingItems() {
        TradingWindow offerWindow = trade.getTradingWindow(2);
        TradingWindow myWindow = trade.getCreatureTwoRequestWindow();
        boolean hasRestrictedMaterial = false;
        boolean hasBlockedItems = false;
        for (Item item : offerWindow.getItems()) {
            if (!item.isCoin()) {
                if (workBook.isRestrictedMaterial(item.getMaterial())) {
                    hasRestrictedMaterial = true;
                    continue;
                }
                if (workBook.isBlockedItem(item.getTemplateId())) {
                    hasBlockedItems = true;
                    continue;
                }
            }

            if (item.isCoin() ||
                        (donating != DonationType.not && workBook.getCrafterType().hasSkillToImprove(item))
                        || (item.getQualityLevel() < getTargetQL(item) && !item.isNoImprove() && item.isRepairable() && !item.isNewbieItem() && !item.isChallengeNewbieItem())) {
                offerWindow.removeItem(item);
                myWindow.addItem(item);
            } else if (item.isWeaponBow() && targetQLs.containsKey(SkillList.GROUP_BOWYERY)) {
                trade.creatureOne.getCommunicator().sendSafeServerMessage(creature.getName() + " says 'Please unstring any bows.'");
            }
        }

        int length = offerWindow.getItems().length;
        if (length > 0) {
            trade.creatureOne.getCommunicator().sendSafeServerMessage(creature.getName() + " says 'I cannot improve " + (length == 1 ? "that item.'" : "those items.'"));

            // Extra detail, for tracking down bugs.
            if (trade.creatureOne.getPower() >= 3) {
                Communicator comm = trade.creatureOne.getCommunicator();
                if (targetQLs.isEmpty()) {
                    comm.sendNormalServerMessage(creature.getName() + " says 'No target QL selected.'");
                } else {
                    for (Item ignoredItem : offerWindow.getItems()) {
                        if (!workBook.getCrafterType().hasSkillToImprove(ignoredItem))
                            comm.sendNormalServerMessage(creature.getName() + " says 'I do not know the skill to improve the " + ignoredItem.getName() + ".'");
                        else if (ignoredItem.getQualityLevel() >= getTargetQL(ignoredItem)) {
                            comm.sendNormalServerMessage(creature.getName() + " says 'The " + ignoredItem.getName() + " is already higher QL than my skill level.'");
                        } else if (ignoredItem.isNoImprove()) {
                            comm.sendNormalServerMessage(creature.getName() + " says 'The " + ignoredItem.getName() + " cannot be improved by anyone.'");
                        } else if (!ignoredItem.isRepairable()) {
                            comm.sendNormalServerMessage(creature.getName() + " says 'The " + ignoredItem.getName() + " cannot be repaired.'");
                        } else if (ignoredItem.isNewbieItem() || ignoredItem.isChallengeNewbieItem()) {
                            comm.sendNormalServerMessage(creature.getName() + " says 'The " + ignoredItem.getName() + " is a new player item.'");
                        } else if (hasRestrictedMaterial) {
                            comm.sendNormalServerMessage(creature.getName() + " says 'The " + ignoredItem.getName() + " is probably made of a restricted material.'");
                        } else if (hasBlockedItems) {
                            comm.sendNormalServerMessage(creature.getName() + " says 'The " + ignoredItem.getName() + " is on the blocked items list.'");
                        }
                    }
                }
            }
        }

        if (hasRestrictedMaterial) {
            List<Byte> materials = workBook.getRestrictedMaterials();
            if (CrafterMod.materialsRestrictedGlobally()) {
                if (materials.isEmpty()) {
                    materials.addAll(CrafterMod.getRestrictedMaterials());
                } else {
                    materials.removeIf(CrafterMod::isGloballyRestrictedMaterial);
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append(creature.getName()).append(" says 'I can only improve ");

            byte penultimate = -1;
            if (materials.size() > 1)
                penultimate = materials.get(materials.size() - 2);
            byte last = materials.get(materials.size() - 1);

            for (byte material : materials) {
                sb.append(MaterialUtilities.getMaterialString(material));
                if (material == last)
                    sb.append(" items.'");
                else if (material == penultimate)
                    sb.append(", and ");
                else
                    sb.append(", ");
            }
            trade.creatureOne.getCommunicator().sendSafeServerMessage(sb.toString());
        }

        if (hasBlockedItems) {
            Set<Integer> blocked = new HashSet<>();
            blocked.addAll(CrafterMod.blockedItems);
            blocked.addAll(workBook.getBlockedItems());
            List<Integer> blockedItems = new ArrayList<>(blocked);

            StringBuilder sb = new StringBuilder().append(creature.getName()).append(" says 'I cannot improve ");

            int penultimate = -1;
            if (blockedItems.size() > 1)
                penultimate = blockedItems.get(blockedItems.size() - 2);
            int last = blockedItems.get(blockedItems.size() - 1);

            for (int templateId : blockedItems) {
                ItemTemplate template = ItemTemplateFactory.getInstance().getTemplateOrNull(templateId);
                if (template != null) {
                    sb.append(template.getPlural());
                    if (templateId == last)
                        sb.append(".'");
                    else if (templateId == penultimate)
                        sb.append(", or ");
                    else
                        sb.append(", ");
                }
            }

            trade.creatureOne.getCommunicator().sendSafeServerMessage(sb.toString());
        }
    }

    private void setOptions() {
        Item mailWhenDoneItem = null;
        Map<Integer, Item> currentHighest = new HashMap<>();
        donating = DonationType.not;
        mailWhenDone = false;

        TradingWindow myOffers = trade.getTradingWindow(1);
        TradingWindow jobDetailsWindow = trade.getCreatureOneRequestWindow();
        for (Item item : jobDetailsWindow.getItems()) {
            if (!optionItems.contains(item)) {
                continue;
            }
            if (donating == DonationType.not) {
                boolean isDonating = item.getName().startsWith("Donate");
                boolean isGiving = item.getName().startsWith("Give");

                if (isDonating || isGiving) {
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
                }

                if (isDonating) {
                    donating = DonationType.donating;
                    continue;
                } else if (isGiving) {
                    donating = DonationType.giving;
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

            if (donating == DonationType.not) {
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
                trade.setMoneyAdded(money);
                trade.setOrderTotal(cost);
            } else if (donating == DonationType.donating) {
                for (Item item : playerWindow.getItems()) {
                    if (item.isCoin()) {
                        playerWindow.removeItem(item);
                        trade.getTradingWindow(2).addItem(item);
                    }
                }
                if (trade.getTradingWindow(4).getItems().length > 0)
                    player.getCommunicator().sendSafeServerMessage(creature.getName() + " says 'If you wish to donate these items, I'll be happy to take them to improve my skills.'");
            } else {
                for (Item item : playerWindow.getItems()) {
                    if (item.isCoin()) {
                        playerWindow.removeItem(item);
                        trade.getTradingWindow(2).addItem(item);
                    }
                }
                if (trade.getTradingWindow(4).getItems().length > 0)
                    player.getCommunicator().sendSafeServerMessage(creature.getName() + " says 'I'll be happy to use any tools you give me in my work.'");
            }

            trade.setSatisfied(creature, true, trade.getCurrentCounter());
            balanced = true;
        }
    }

    private void removeChange() {
        for (Item item : trade.getTradingWindow(3).getItems()) {
            if (item.isCoin() && !coinsToCollect.contains(item))
                trade.getTradingWindow(3).removeItem(item);
        }
    }

    public float getTargetQL(Item item) {
        int skill = CrafterType.getNearestSkill(item);
        Float targetQL = targetQLs.get(skill);
        if (targetQL == null)
            return 0;
        return targetQL;
    }

    public boolean isMailOnDone() {
        return mailWhenDone;
    }

    public DonationType typeOfDonating() {
        return donating;
    }

    public boolean isOptionItem(Item item) {
        return optionItems.contains(item);
    }

    // TODO - Temp.
    public String getSelectedOptions() {
        return Arrays.stream(trade.getCreatureOneRequestWindow().getItems()).filter(i -> !i.isCoin()).map(i -> i.getName() + " (" + i.getQualityLevel() + ")").collect(Collectors.joining(", "));
    }
}
