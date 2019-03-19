//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
// Modified from version by Code Club AB.
//

package com.wurmonline.server.creatures;

import com.wurmonline.server.*;
import com.wurmonline.server.economy.Change;
import com.wurmonline.server.economy.Economy;
import com.wurmonline.server.economy.MonetaryConstants;
import com.wurmonline.server.economy.Shop;
import com.wurmonline.server.items.*;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings("ALL")
public class TradeHandler implements MiscConstants, ItemTypes, MonetaryConstants {
    private static final Logger logger = Logger.getLogger(TradeHandler.class.getName());
    private Creature creature;
    private Trade trade;
    private boolean balanced = false;
    private boolean waiting = false;
    private final Map<Integer, Set<Item>> itemMap = new HashMap();
    private final Map<Integer, List<Item>> currentDemandMap = new HashMap();
    private final Map<Integer, Set<Item>> purchaseMap = new HashMap();
    private static int maxPersonalItems = 50;
    private final Shop shop;
    private long pdemand = 0L;
    private static final float maxNums = 80.0F;
    private final boolean ownerTrade;
    private final Set<Item> fullPriceItems = new HashSet();
    private boolean hasOtherItems = false;

    TradeHandler(Creature aCreature, Trade _trade) {
        this.creature = aCreature;
        this.trade = _trade;
        this.shop = Economy.getEconomy().getShop(aCreature);
        if (this.shop.isPersonal()) {
            this.ownerTrade = this.shop.getOwnerId() == this.trade.creatureOne.getWurmId();
            if (this.ownerTrade) {
                this.trade.creatureOne.getCommunicator().sendSafeServerMessage(aCreature.getName() + " says, 'Welcome back, " + this.trade.creatureOne.getName() + "!'");
            } else {
                this.trade.creatureOne.getCommunicator().sendSafeServerMessage(aCreature.getName() + " says, 'I will not buy anything, but i can offer these things.'");
            }
        } else {
            this.ownerTrade = false;
            if (this.shop.getMoney() <= 1L) {
                if (this.trade.creatureOne.getPower() >= 3) {
                    this.trade.creatureOne.getCommunicator().sendSafeServerMessage(aCreature.getName() + " says, 'I only have " + this.shop.getMoney() + " and can't buy anything right now.'");
                } else {
                    this.trade.creatureOne.getCommunicator().sendSafeServerMessage(aCreature.getName() + " says, 'I am a bit low on money and can't buy anything right now.'");
                }
            } else if (this.shop.getMoney() < 100L) {
                if (this.trade.creatureOne.getPower() >= 3) {
                    this.trade.creatureOne.getCommunicator().sendSafeServerMessage(aCreature.getName() + " says, 'I only have " + (new Change(this.shop.getMoney())).getChangeShortString() + ".'");
                } else {
                    this.trade.creatureOne.getCommunicator().sendSafeServerMessage(aCreature.getName() + " says, 'I am a bit low on money, but let's see what you have.'");
                }
            } else if (this.trade.creatureOne.getPower() >= 3) {
                this.trade.creatureOne.getCommunicator().sendSafeServerMessage(aCreature.getName() + " says, 'I have " + (new Change(this.shop.getMoney())).getChangeShortString() + ".'");
            }
        }

    }

    TradeHandler() {
        shop = null;
        ownerTrade = false;
    }

    void end() {
        this.creature = null;
        this.trade = null;
    }

    void addToInventory(Item item, long inventoryWindow) {
        if (this.trade != null) {
            if (inventoryWindow == 2L) {
                this.tradeChanged();
                if (logger.isLoggable(Level.FINEST) && item != null) {
                    logger.finest("Added " + item.getName() + " to his offer window.");
                }
            } else if (inventoryWindow == 1L) {
                if (logger.isLoggable(Level.FINEST) && item != null) {
                    logger.finest("Added " + item.getName() + " to my offer window.");
                }
            } else if (inventoryWindow == 3L) {
                if (logger.isLoggable(Level.FINEST) && item != null) {
                    logger.finest("Added " + item.getName() + " to his request window.");
                }
            } else if (inventoryWindow == 4L && logger.isLoggable(Level.FINEST) && item != null) {
                logger.finest("Added " + item.getName() + " to my request window.");
            }
        }

    }

    void tradeChanged() {
        this.balanced = false;
        this.waiting = false;
    }

    void addItemsToTrade() {
        if (this.trade != null) {
            boolean foundDeclaration = false;
            boolean foundWagonerContract = false;
            Set<Item> ite = this.creature.getInventory().getItems();
            Item[] itarr = (Item[])ite.toArray(new Item[ite.size()]);
            TradingWindow myOffers = this.trade.getTradingWindow(1L);

            myOffers.startReceivingItems();

            for(int x = 0; x < itarr.length; ++x) {
                int templateId = itarr[x].getTemplateId();
                Set<Item> its = (Set)this.itemMap.get(templateId);
                if (its == null) {
                    its = new HashSet();
                }

                ((Set)its).add(itarr[x]);
                if (this.shop.isPersonal()) {
                    if (this.ownerTrade) {
                        myOffers.addItem(itarr[x]);
                    } else if (!itarr[x].isCoin()) {
                        myOffers.addItem(itarr[x]);
                    }
                } else {
                    if (itarr[x].getTemplateId() == 843) {
                        if (!Features.Feature.NAMECHANGE.isEnabled()) {
                            ((Set)its).remove(itarr[x]);
                            Items.destroyItem(itarr[x].getWurmId());
                            continue;
                        }

                        foundDeclaration = true;
                    }

                    if (itarr[x].getTemplateId() == 1129) {
                        if (!Features.Feature.WAGONER.isEnabled()) {
                            ((Set)its).remove(itarr[x]);
                            Items.destroyItem(itarr[x].getWurmId());
                            continue;
                        }

                        foundWagonerContract = true;
                    }

                    if (((Set)its).size() < 10 && itarr[x].getLockId() == -10L) {
                        myOffers.addItem(itarr[x]);
                    } else if (((Set)its).size() > 50) {
                        ((Set)its).remove(itarr[x]);
                        Items.destroyItem(itarr[x].getWurmId());
                    }
                }

                this.itemMap.put(templateId, its);
            }

            if (!this.shop.isPersonal()) {
                boolean newMerchandise = false;
                Item item;
                Item inventory;
                if (Features.Feature.NAMECHANGE.isEnabled() && !foundDeclaration) {
                    try {
                        inventory = this.creature.getInventory();
                        item = Creature.createItem(843, (float)(60 + Server.rand.nextInt(40)));
                        inventory.insertItem(item);
                        newMerchandise = true;
                    } catch (Exception var11) {
                        logger.log(Level.INFO, "Failed to create name change cert for creature.", var11);
                    }
                }

                if (Features.Feature.WAGONER.isEnabled() && !foundWagonerContract) {
                    try {
                        inventory = this.creature.getInventory();
                        item = Creature.createItem(1129, (float)(60 + Server.rand.nextInt(40)));
                        inventory.insertItem(item);
                        newMerchandise = true;
                    } catch (Exception var10) {
                        logger.log(Level.INFO, "Failed to create wagoner contract for creature.", var10);
                    }
                }

                if (newMerchandise) {
                    this.trade.creatureOne.getCommunicator().sendSafeServerMessage(this.creature.getName() + " says, 'Oh, I forgot I have some new merchandise. Let us trade again and I will show them to you.'");
                }
            }

            myOffers.stopReceivingItems();
        }

    }

    public int getTraderSellPriceForItem(Item item, TradingWindow window) {
        if (item.isFullprice()) {
            return item.getValue();
        } else if (this.shop.isPersonal() && item.getPrice() > 0) {
            return item.getPrice();
        } else {
            int numberSold = 1;
            if (window == this.trade.getCreatureOneRequestWindow() && (!this.shop.isPersonal() || this.shop.usesLocalPrice())) {
                if (item.isCombine()) {
                    ItemTemplate temp = item.getTemplate();
                    numberSold = Math.max(1, item.getWeightGrams() / temp.getWeightGrams());
                }

                Item[] whatPlayerWants = this.trade.getCreatureOneRequestWindow().getItems();

                for(int x = 0; x < whatPlayerWants.length; ++x) {
                    if (whatPlayerWants[x] != item && whatPlayerWants[x].getTemplateId() == item.getTemplateId()) {
                        if (whatPlayerWants[x].isCombine()) {
                            ItemTemplate temp = whatPlayerWants[x].getTemplate();
                            numberSold += Math.max(1, whatPlayerWants[x].getWeightGrams() / temp.getWeightGrams());
                        } else {
                            ++numberSold;
                        }
                    }
                }
            }

            double localPrice = this.shop.getLocalTraderSellPrice(item, 100, numberSold);
            if (logger.isLoggable(Level.FINEST)) {
                logger.finest("localSellPrice for " + item.getName() + "=" + localPrice + " numberSold=" + numberSold);
            }

            return (int)Math.max(2.0D, localPrice * (double)this.shop.getPriceModifier());
        }
    }

    public int getTraderBuyPriceForItem(Item item) {
        if (item.isFullprice()) {
            return item.getValue();
        } else {

            List<Item> itlist = (List)this.currentDemandMap.get(item.getTemplateId());
            int extra = 1;
            if (itlist == null) {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.finest("Weird. We're trading items which don't exist.");
                }

                extra = 1;
            } else if (item.isCombine()) {
                ItemTemplate temp = item.getTemplate();

                Item i;
                for(Iterator var6 = itlist.iterator(); var6.hasNext(); extra += Math.max(1, i.getWeightGrams() / temp.getWeightGrams())) {
                    i = (Item)var6.next();
                }
            } else {
                extra += itlist.size();
            }

            int price = (int)this.shop.getLocalTraderBuyPrice(item, 1, extra);
            if (logger.isLoggable(Level.FINEST)) {
                logger.finest("localBuyPrice for " + item.getName() + "=" + price + " extra=" + extra + " price for extra+1=" + (int)this.shop.getLocalTraderBuyPrice(item, 1, extra + 1));
            }

            return Math.max(0, price);
        }
    }

    private long getDiff() {
        if (this.shop.isPersonal() && this.ownerTrade) {
            return 0L;
        } else {
            Item[] whatPlayerWants = this.trade.getCreatureOneRequestWindow().getItems();
            Item[] whatIWant = this.trade.getCreatureTwoRequestWindow().getItems();
            this.pdemand = 0L;
            long mydemand = 0L;

            this.fullPriceItems.clear();
            this.hasOtherItems = false;
            this.purchaseMap.clear();

            int x;
            int tid;
            ItemTemplate temp;
            Iterator var13;
            Item i;
            for(x = 0; x < whatPlayerWants.length; ++x) {
                if (whatPlayerWants[x].isFullprice()) {
                    this.pdemand += (long)whatPlayerWants[x].getValue();
                    this.fullPriceItems.add(whatPlayerWants[x]);
                } else {
                    double localPrice = 2.0D;
                    if (this.shop.isPersonal() && whatPlayerWants[x].getPrice() > 0) {
                        localPrice = (double)whatPlayerWants[x].getPrice();
                        this.pdemand = (long)((double)this.pdemand + localPrice);
                    } else {
                        int templateId = whatPlayerWants[x].getTemplateId();
                        tid = 0;

                        try {
                            if (!this.shop.isPersonal() || this.shop.usesLocalPrice()) {
                                tid = whatPlayerWants[x].getTemplateId();
                                Set<Item> itlist = (Set)this.purchaseMap.get(tid);
                                if (itlist == null) {
                                    itlist = new HashSet();
                                }

                                ((Set)itlist).add(whatPlayerWants[x]);
                                this.purchaseMap.put(tid, itlist);
                                if (whatPlayerWants[x].isCombine()) {
                                    temp = ItemTemplateFactory.getInstance().getTemplate(templateId);

                                    for(var13 = ((Set)itlist).iterator(); var13.hasNext(); tid += Math.max(1, i.getWeightGrams() / temp.getWeightGrams())) {
                                        i = (Item)var13.next();
                                    }
                                } else {
                                    tid = ((Set)itlist).size();
                                }
                            }

                            localPrice = this.shop.getLocalTraderSellPrice(whatPlayerWants[x], 100, tid);
                            if (logger.isLoggable(Level.FINEST)) {
                                logger.finest("LocalSellPrice for " + whatPlayerWants[x].getName() + "=" + localPrice + " mod=" + this.shop.getPriceModifier() + " sum=" + Math.max(2.0D, localPrice * (double)this.shop.getPriceModifier()));
                            }

                            this.pdemand = (long)((double)this.pdemand + Math.max(2.0D, localPrice * (double)this.shop.getPriceModifier()));
                        } catch (NoSuchTemplateException var16) {
                            logger.log(Level.WARNING, var16.getMessage(), var16);
                        }
                    }
                }
            }

            this.purchaseMap.clear();

            for(x = 0; x < whatIWant.length; ++x) {
                if (whatIWant[x].isFullprice()) {
                    mydemand += (long)whatIWant[x].getValue();
                } else {
                    this.hasOtherItems = true;
                    if (this.fullPriceItems.isEmpty()) {
                        long price = 1L;
                        tid = whatIWant[x].getTemplateId();
                        Set<Item> itlist = (Set)this.purchaseMap.get(tid);
                        if (itlist == null) {
                            itlist = new HashSet();
                        }

                        ((Set)itlist).add(whatIWant[x]);
                        this.purchaseMap.put(tid, itlist);
                        int extra = 0;
                        if (whatIWant[x].isCombine()) {
                            try {
                                temp = ItemTemplateFactory.getInstance().getTemplate(whatIWant[x].getTemplateId());

                                for(var13 = ((Set)itlist).iterator(); var13.hasNext(); extra += Math.max(1, i.getWeightGrams() / temp.getWeightGrams())) {
                                    i = (Item)var13.next();
                                }
                            } catch (NoSuchTemplateException var15) {
                                logger.log(Level.WARNING, var15.getMessage(), var15);
                            }
                        } else {
                            extra = ((Set)itlist).size();
                        }

                        price = this.shop.getLocalTraderBuyPrice(whatIWant[x], 1, extra);
                        mydemand += Math.max(0L, price);
                    }
                }
            }

            long diff = this.pdemand - mydemand;
            return diff;
        }
    }

    public static int getMaxNumPersonalItems() {
        return maxPersonalItems;
    }

    private void suckInterestingItems() {
        TradingWindow currWin = this.trade.getTradingWindow(2L);
        TradingWindow targetWin = this.trade.getTradingWindow(4L);
        Item[] offItems = currWin.getItems();
        Item[] setItems = targetWin.getItems();
        int templateId;
        if (!this.shop.isPersonal()) {
            this.currentDemandMap.clear();

            int x;
            List<Item> itlist;
            for(templateId = 0; templateId < setItems.length; ++templateId) {
                x = setItems[templateId].getTemplateId();
                itlist = (List)this.currentDemandMap.get(x);
                if (itlist == null) {
                    itlist = new LinkedList();
                }

                ((List)itlist).add(setItems[templateId]);
                this.currentDemandMap.put(x, itlist);
            }


            targetWin.startReceivingItems();

            for(x = 0; x < offItems.length; ++x) {
                if (!offItems[x].isArtifact() && offItems[x].isPurchased() && offItems[x].getLockId() == -10L) {
                    Item parent = offItems[x];

                    try {
                        parent = offItems[x].getParent();
                    } catch (NoSuchItemException var12) {
                        ;
                    }

                    if (offItems[x] == parent || parent.isViewableBy(this.creature)) {
                        if (offItems[x].isHollow() && !offItems[x].isEmpty(true)) {
                            this.trade.creatureOne.getCommunicator().sendSafeServerMessage(this.creature.getName() + " says, 'Please empty the " + offItems[x].getName() + " first.'");
                        } else {
                            templateId = offItems[x].getTemplateId();
                            itlist = (List)this.currentDemandMap.get(templateId);
                            if (itlist == null) {
                                itlist = new LinkedList();
                            }

                            if ((float)((List)itlist).size() < 80.0F) {
                                currWin.removeItem(offItems[x]);
                                targetWin.addItem(offItems[x]);
                                ((List)itlist).add(offItems[x]);
                                this.currentDemandMap.put(templateId, itlist);
                            }
                        }
                    }
                } else if ((offItems[x].isHomesteadDeed() || offItems[x].isVillageDeed()) && offItems[x].getData2() <= 0) {
                    templateId = offItems[x].getTemplateId();
                    itlist = (List)this.currentDemandMap.get(templateId);
                    if (itlist == null) {
                        itlist = new LinkedList();
                    }

                    currWin.removeItem(offItems[x]);
                    targetWin.addItem(offItems[x]);
                    ((List)itlist).add(offItems[x]);
                    this.currentDemandMap.put(templateId, itlist);
                }
            }

            targetWin.stopReceivingItems();
        } else if (this.ownerTrade) {
            TradingWindow myOffers = this.trade.getTradingWindow(1L);
            Item[] currItems = myOffers.getItems();
            int size = 0;

            for(int c = 0; c < currItems.length; ++c) {
                if (!currItems[c].isCoin()) {
                    ++size;
                }
            }

            size += setItems.length;
            if (size > maxPersonalItems) {
                this.trade.creatureOne.getCommunicator().sendNormalServerMessage(this.creature.getName() + " says, 'I cannot add more items to my stock right now.'");
            } else {
                TradingWindow hisReq = this.trade.getTradingWindow(3L);
                Item[] reqItems = hisReq.getItems();

                int x;
                for(x = 0; x < reqItems.length; ++x) {
                    if (!reqItems[x].isCoin()) {
                        ++size;
                    }
                }

                if (size > maxPersonalItems) {
                    this.trade.creatureOne.getCommunicator().sendNormalServerMessage(this.creature.getName() + " says, 'I cannot add more items to my stock right now.'");
                } else {
                    targetWin.startReceivingItems();

                    for(x = 0; x < offItems.length; ++x) {
                        if (offItems[x].getTemplateId() != 272 && offItems[x].getTemplateId() != 781 && !offItems[x].isArtifact() && !offItems[x].isRoyal() && (!offItems[x].isVillageDeed() && !offItems[x].isHomesteadDeed() || !offItems[x].hasData()) && (offItems[x].getTemplateId() != 300 || offItems[x].getData2() == -1)) {
                            // Bug? - will add till 51 instead of 50.
                            if (size > maxPersonalItems) {
                                if (offItems[x].isCoin()) {
                                    currWin.removeItem(offItems[x]);
                                    targetWin.addItem(offItems[x]);
                                }
                            } else if ((!offItems[x].isLockable() || !offItems[x].isLocked()) && (!offItems[x].isHollow() || offItems[x].isEmpty(true) || offItems[x].isSealedByPlayer())) {
                                currWin.removeItem(offItems[x]);
                                targetWin.addItem(offItems[x]);
                                ++size;
                            } else if (offItems[x].isLockable() && offItems[x].isLocked()) {
                                this.trade.creatureOne.getCommunicator().sendSafeServerMessage(this.creature.getName() + " says, 'I don't accept locked items any more. Sorry for the inconvenience.'");
                            } else {
                                this.trade.creatureOne.getCommunicator().sendSafeServerMessage(this.creature.getName() + " says, 'Please empty the " + offItems[x].getName() + " first.'");
                            }
                        }
                    }

                    targetWin.stopReceivingItems();
                }
            }
        } else {
            targetWin.startReceivingItems();

            for(templateId = 0; templateId < offItems.length; ++templateId) {
                if (offItems[templateId].isCoin()) {
                    Item parent = offItems[templateId];

                    try {
                        parent = offItems[templateId].getParent();
                    } catch (NoSuchItemException var11) {
                        ;
                    }

                    if (offItems[templateId] == parent || parent.isViewableBy(this.creature)) {
                        currWin.removeItem(offItems[templateId]);
                        targetWin.addItem(offItems[templateId]);
                    }
                }
            }

            targetWin.stopReceivingItems();
        }

    }

    void balance() {
        if (!this.balanced) {
            if (this.ownerTrade) {
                this.suckInterestingItems();
                this.trade.setSatisfied(this.creature, true, this.trade.getCurrentCounter());
                this.balanced = true;
            } else {
                long diff;
                Change change;
                String toSend;
                Item[] mon;
                int x;
                if (this.shop.isPersonal() && !this.waiting) {
                    this.suckInterestingItems();
                    this.removeCoins(this.trade.getCreatureOneRequestWindow().getItems());
                    diff = this.getDiff();
                    if (diff > 0L) {
                        this.waiting = true;
                        change = new Change(diff);
                        toSend = this.creature.getName() + " demands ";
                        toSend = toSend + change.getChangeString();
                        toSend = toSend + " coins to make the trade.";
                        this.trade.creatureOne.getCommunicator().sendSafeServerMessage(toSend);
                    } else if (diff < 0L) {
                        mon = Economy.getEconomy().getCoinsFor(Math.abs(diff));
                        this.trade.getCreatureOneRequestWindow().startReceivingItems();

                        for(x = 0; x < mon.length; ++x) {
                            this.trade.getCreatureOneRequestWindow().addItem(mon[x]);
                        }

                        this.trade.getCreatureOneRequestWindow().stopReceivingItems();
                        this.trade.setSatisfied(this.creature, true, this.trade.getCurrentCounter());
                        this.trade.setMoneyAdded(this.pdemand);
                        this.balanced = true;
                    } else {
                        this.trade.setMoneyAdded(this.pdemand);
                        this.trade.setSatisfied(this.creature, true, this.trade.getCurrentCounter());
                        this.balanced = true;
                    }
                } else if (!this.waiting) {
                    this.suckInterestingItems();
                    this.removeCoins(this.trade.getCreatureOneRequestWindow().getItems());
                    diff = this.getDiff();
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.finest("diff is " + diff);
                    }

                    if (!this.fullPriceItems.isEmpty() && this.hasOtherItems) {
                        Iterator var9 = this.fullPriceItems.iterator();

                        while(var9.hasNext()) {
                            Item lItem = (Item)var9.next();
                            this.trade.creatureOne.getCommunicator().sendSafeServerMessage(this.creature.getName() + " says, 'Sorry, " + this.trade.creatureOne.getName() + ". I must charge full price in coin value for the " + lItem.getName() + ".'");
                        }

                        this.waiting = true;
                    } else if (diff > 0L) {
                        this.waiting = true;
                        change = new Change(diff);
                        toSend = this.creature.getName() + " demands ";
                        toSend = toSend + change.getChangeString();
                        toSend = toSend + " coins to make the trade.";
                        this.trade.creatureOne.getCommunicator().sendSafeServerMessage(toSend);
                    } else if (diff < 0L) {
                        if (Math.abs(diff) > this.shop.getMoney()) {
                            mon = this.trade.getCreatureTwoRequestWindow().getAllItems();
                            x = mon.length;

                            for(int var5 = 0; var5 < x; ++var5) {
                                Item i = mon[var5];
                                if (!i.isCoin()) {
                                    toSend = this.creature.getName() + " says, 'I am low on cash and can not purchase those items.'";
                                    this.waiting = true;
                                    this.trade.creatureOne.getCommunicator().sendSafeServerMessage(toSend);
                                    return;
                                }
                            }

                            mon = Economy.getEconomy().getCoinsFor(Math.abs(diff));
                            this.trade.getCreatureOneRequestWindow().startReceivingItems();

                            for(x = 0; x < mon.length; ++x) {
                                this.trade.getCreatureOneRequestWindow().addItem(mon[x]);
                            }

                            this.trade.getCreatureOneRequestWindow().stopReceivingItems();
                            this.trade.setSatisfied(this.creature, true, this.trade.getCurrentCounter());
                            this.balanced = true;
                        } else {
                            mon = Economy.getEconomy().getCoinsFor(Math.abs(diff));
                            this.trade.getCreatureOneRequestWindow().startReceivingItems();

                            for(x = 0; x < mon.length; ++x) {
                                this.trade.getCreatureOneRequestWindow().addItem(mon[x]);
                            }

                            this.trade.getCreatureOneRequestWindow().stopReceivingItems();
                            this.trade.setSatisfied(this.creature, true, this.trade.getCurrentCounter());
                            this.balanced = true;
                        }
                    } else {
                        this.trade.setSatisfied(this.creature, true, this.trade.getCurrentCounter());
                        this.balanced = true;
                    }
                }
            }
        }

    }

    private boolean removeCoins(Item[] items) {
        boolean foundCoins = false;

        for(int x = 0; x < items.length; ++x) {
            if (items[x].isCoin()) {
                foundCoins = true;
                this.trade.getCreatureOneRequestWindow().removeItem(items[x]);
            }
        }

        return foundCoins;
    }
}
