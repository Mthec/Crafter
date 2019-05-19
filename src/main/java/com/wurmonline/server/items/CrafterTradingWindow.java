//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
// Modified from version by Code Club AB.
//

package com.wurmonline.server.items;

import com.wurmonline.server.*;
import com.wurmonline.server.creatures.CrafterTradeHandler;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.economy.Economy;
import com.wurmonline.server.economy.MonetaryConstants;
import com.wurmonline.server.economy.Shop;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.villages.*;
import com.wurmonline.shared.constants.CreatureTypes;
import com.wurmonline.shared.util.MaterialUtilities;
import mod.wurmunlimited.npcs.CrafterMod;
import mod.wurmunlimited.npcs.CrafterTemplate;
import mod.wurmunlimited.npcs.WorkBook;

import java.io.IOException;
import java.util.*;
import java.util.logging.*;

public class CrafterTradingWindow extends TradingWindow implements MiscConstants, ItemTypes, VillageStatus, CreatureTypes, MonetaryConstants {
    private final Creature windowOwner;
    private final Creature watcher;
    private final boolean offer;
    private final long wurmId;
    private Set<Item> items;
    private final CrafterTrade trade;
    private static final Logger logger = Logger.getLogger(CrafterTradingWindow.class.getName());
    private static final Map<String, Logger> loggers = new HashMap<>();

    CrafterTradingWindow(Creature aOwner, Creature aWatcher, boolean aOffer, long aWurmId, CrafterTrade aTrade) {
        this.windowOwner = aOwner;
        this.watcher = aWatcher;
        this.offer = aOffer;
        this.wurmId = aWurmId;
        this.trade = aTrade;
    }

    public static void stopLoggers() {
        Iterator var0 = loggers.values().iterator();

        while(true) {
            Logger logger;
            do {
                if (!var0.hasNext()) {
                    return;
                }

                logger = (Logger)var0.next();
            } while(logger == null);

            for (Handler h : logger.getHandlers()) {
                h.close();
            }
        }
    }

    private static Logger getLogger(long wurmId) {
        String name = "worker" + wurmId;
        Logger personalLogger = loggers.get(name);
        if (personalLogger == null) {
            personalLogger = Logger.getLogger(name);
            personalLogger.setUseParentHandlers(false);
            Handler[] h = logger.getHandlers();

            for(int i = 0; i != h.length; ++i) {
                personalLogger.removeHandler(h[i]);
            }

            try {
                FileHandler fh = new FileHandler(name + ".log", 0, 1, true);
                fh.setFormatter(new SimpleFormatter());
                personalLogger.addHandler(fh);
            } catch (IOException var6) {
                Logger.getLogger(name).log(Level.WARNING, name + ":no redirection possible!");
            }

            loggers.put(name, personalLogger);
        }

        return personalLogger;
    }

    @Override
    public boolean mayMoveItemToWindow(Item item, Creature creature, long window) {
        boolean toReturn = false;
        if (this.wurmId == 3L) {
            if (window == 1L) {
                toReturn = true;
            }
        } else if (this.wurmId == 4L) {
            if (window == 2L) {
                toReturn = true;
            }
        } else if (this.wurmId == 2L) {
            if (!this.windowOwner.equals(creature)) {
                if (creature.isPlayer() && item.isCoin() && !this.windowOwner.isPlayer()) {
                    return false;
                }

                if (window == 4L) {
                    toReturn = true;
                }
            }
        } else if (this.wurmId == 1L && !this.windowOwner.equals(creature) && window == 3L && this.watcher == creature && item.getOwnerId() == this.windowOwner.getWurmId()) {
            toReturn = true;
        }

        return toReturn;
    }

    @Override
    public boolean mayAddFromInventory(Creature creature, Item item) {
        if (!item.isTraded()) {
            if (item.isNoTrade()) {
                creature.getCommunicator().sendSafeServerMessage(item.getNameWithGenus() + " is not tradable.");
            } else if (this.windowOwner.equals(creature)) {
                try {
                    long owneri = item.getOwner();
                    if (owneri != this.watcher.getWurmId() && owneri != this.windowOwner.getWurmId()) {
                        this.windowOwner.setCheated("Traded " + item.getName() + "[" + item.getWurmId() + "] with " + this.watcher.getName() + " owner=" + owneri);
                    }
                } catch (NotOwnedException var8) {
                    this.windowOwner.setCheated("Traded " + item.getName() + "[" + item.getWurmId() + "] with " + this.watcher.getName() + " not owned?");
                }

                if (this.wurmId == 2L || this.wurmId == 1L) {
                    if (item.isHollow()) {
                        Item[] its = item.getAllItems(true);

                        for (Item lIt : its) {
                            if (lIt.isNoTrade() || lIt.isVillageDeed() || lIt.isHomesteadDeed() || lIt.getTemplateId() == 781) {
                                creature.getCommunicator().sendSafeServerMessage(item.getNameWithGenus() + " contains a non-tradable item.");
                                return false;
                            }
                        }
                    }

                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public long getWurmId() {
        return this.wurmId;
    }

    @Override
    public Item[] getItems() {
        return this.items != null ? this.items.toArray(new Item[0]) : new Item[0];
    }

    private void removeExistingContainedItems(Item item) {
        if (item.isHollow()) {
            for (Item lElement : item.getItemsAsArray()) {
                this.removeExistingContainedItems(lElement);
                if (lElement.getTradeWindow() == this) {
                    this.removeFromTrade(lElement, false);
                } else if (lElement.getTradeWindow() != null) {
                    lElement.getTradeWindow().removeItem(lElement);
                }
            }
        }

    }

    @Override
    public Item[] getAllItems() {
        if (this.items == null) {
            return new Item[0];
        } else {
            Set<Item> toRet = new HashSet<>();

            for (Item item : this.items) {
                toRet.add(item);
                Item[] toAdd = item.getAllItems(false);

                for (Item lElement : toAdd) {
                    if (lElement.tradeWindow == this) {
                        toRet.add(lElement);
                    }
                }
            }

            return toRet.toArray(new Item[0]);
        }
    }

    @Override
    public void stopReceivingItems() {
    }

    @Override
    public void startReceivingItems() {
    }

    @Override
    public void addItem(Item item) {
        if (this.items == null) {
            this.items = new HashSet<>();
        }

        if (item.tradeWindow == null) {
            this.removeExistingContainedItems(item);
            Item parent = item;

            try {
                parent = item.getParent();
            } catch (NoSuchItemException ignored) {}

            this.items.add(item);
            this.addToTrade(item);
            if (item == parent || parent.isViewableBy(this.windowOwner)) {
                if (!this.windowOwner.isPlayer()) {
                    this.windowOwner.getCommunicator().sendAddToInventory(item, this.wurmId, parent.tradeWindow == this ? parent.getWurmId() : 0L, 0);
                } else if (!this.watcher.isPlayer()) {
                    this.windowOwner.getCommunicator().sendAddToInventory(item, this.wurmId, parent.tradeWindow == this ? parent.getWurmId() : 0L, this.watcher.getTradeHandler().getTraderBuyPriceForItem(item));
                } else {
                    this.windowOwner.getCommunicator().sendAddToInventory(item, this.wurmId, parent.tradeWindow == this ? parent.getWurmId() : 0L, item.getPrice());
                }
            }

            if (item == parent || parent.isViewableBy(this.watcher)) {
                if (!this.watcher.isPlayer()) {
                    this.watcher.getCommunicator().sendAddToInventory(item, this.wurmId, parent.tradeWindow == this ? parent.getWurmId() : 0L, 0);
                } else if (!this.windowOwner.isPlayer()) {
                    this.watcher.getCommunicator().sendAddToInventory(item, this.wurmId, parent.tradeWindow == this ? parent.getWurmId() : 0L, this.windowOwner.getTradeHandler().getTraderSellPriceForItem(item, this));
                } else {
                    this.watcher.getCommunicator().sendAddToInventory(item, this.wurmId, parent.tradeWindow == this ? parent.getWurmId() : 0L, item.getPrice());
                }
            }
        }

        this.tradeChanged();
    }

    private void addToTrade(Item item) {
        if (item.tradeWindow != this) {
            item.setTradeWindow(this);
        }

        for (Item lIt : item.getItems()) {
            addToTrade(lIt);
        }
    }

    private void removeFromTrade(Item item, boolean noSwap) {
        this.windowOwner.getCommunicator().sendRemoveFromInventory(item, this.wurmId);
        this.watcher.getCommunicator().sendRemoveFromInventory(item, this.wurmId);
        if (noSwap && item.isCoin()) {
            if (item.getOwnerId() == -10L) {
                Economy.getEconomy().returnCoin(item, "Notrade", true);
            }
        }
        item.setTradeWindow(null);

    }

    @Override
    public void removeItem(Item item) {
        if (this.items != null && item.tradeWindow == this) {
            this.removeExistingContainedItems(item);
            this.items.remove(item);
            this.removeFromTrade(item, true);
            this.tradeChanged();
        }

    }

    @Override
    public void updateItem(Item item) {
        if (this.items != null && item.tradeWindow == this) {
            if (!this.windowOwner.isPlayer()) {
                this.windowOwner.getCommunicator().sendUpdateInventoryItem(item, this.wurmId, 0);
            } else if (!this.watcher.isPlayer()) {
                this.windowOwner.getCommunicator().sendUpdateInventoryItem(item, this.wurmId, this.watcher.getTradeHandler().getTraderBuyPriceForItem(item));
            } else {
                this.windowOwner.getCommunicator().sendUpdateInventoryItem(item, this.wurmId, item.getPrice());
            }

            if (!this.watcher.isPlayer()) {
                this.watcher.getCommunicator().sendUpdateInventoryItem(item, this.wurmId, 0);
            } else if (!this.windowOwner.isPlayer()) {
                this.watcher.getCommunicator().sendUpdateInventoryItem(item, this.wurmId, this.windowOwner.getTradeHandler().getTraderSellPriceForItem(item, this));
            } else {
                this.watcher.getCommunicator().sendUpdateInventoryItem(item, this.wurmId, item.getPrice());
            }

            this.tradeChanged();
        }

    }

    private void tradeChanged() {
        if (this.wurmId == 2L && !this.trade.creatureTwo.isPlayer()) {
            this.trade.setCreatureTwoSatisfied(false);
        }

        if (this.wurmId == 3L || this.wurmId == 4L) {
            this.trade.setCreatureOneSatisfied(false);
            this.trade.setCreatureTwoSatisfied(false);
            int c = this.trade.getNextTradeId();
            this.windowOwner.getCommunicator().sendTradeChanged(c);
            this.watcher.getCommunicator().sendTradeChanged(c);
        }

    }

    @Override
    boolean hasInventorySpace() {
        if (this.offer) {
            this.windowOwner.getCommunicator().sendAlertServerMessage("There is a bug in the trade system. This shouldn't happen. Please report.");
            this.watcher.getCommunicator().sendAlertServerMessage("There is a bug in the trade system. This shouldn't happen. Please report.");
            logger.log(Level.WARNING, "Inconsistency! This is offer window number " + this.wurmId + ". Traders are " + this.watcher.getName() + ", " + this.windowOwner.getName());
            return false;
        } else if (!(this.watcher instanceof Player)) {
            return true;
        } else {
            Item inventory = this.watcher.getInventory();
            if (inventory == null) {
                this.windowOwner.getCommunicator().sendAlertServerMessage("Could not find inventory for " + this.watcher.getName() + ". Trade aborted.");
                this.watcher.getCommunicator().sendAlertServerMessage("Could not find your inventory item. Trade aborted. Please contact administrators.");
                logger.log(Level.WARNING, "Failed to locate inventory for " + this.watcher.getName());
                return false;
            } else {
                if (this.items != null) {
                    int nums = 0;

                    for (Item item : this.items) {
                        if (!inventory.testInsertItem(item)) {
                            return false;
                        }

                        if (!item.isCoin()) {
                            ++nums;
                        }

                        if (!item.canBeDropped(false) && this.watcher.isGuest()) {
                            this.windowOwner.getCommunicator().sendAlertServerMessage("Guests cannot receive the item " + item.getName() + ".");
                            this.watcher.getCommunicator().sendAlertServerMessage("Guests cannot receive the item " + item.getName() + ".");
                            return false;
                        }
                    }

                    if (this.watcher.getPower() <= 0 && nums + inventory.getNumItemsNotCoins() > 99) {
                        this.watcher.getCommunicator().sendAlertServerMessage("You may not carry that many items in your inventory.");
                        this.windowOwner.getCommunicator().sendAlertServerMessage(this.watcher.getName() + " may not carry that many items in " + this.watcher.getHisHerItsString() + " inventory.");
                        return false;
                    }
                }

                return true;
            }
        }
    }

    @Override
    int getWeight() {
        int toReturn = 0;
        Item item;
        if (this.items != null) {
            for(Iterator var2 = this.items.iterator(); var2.hasNext(); toReturn += item.getFullWeight()) {
                item = (Item)var2.next();
            }
        }

        return toReturn;
    }

    @Override
    boolean validateTrade() {
        if (this.windowOwner.isDead()) {
            return false;
        } else if (this.windowOwner instanceof Player && !this.windowOwner.hasLink()) {
            return false;
        } else {
            if (this.items != null) {

                for (Item tit : this.items) {
                    if ((this.windowOwner instanceof Player || !tit.isCoin()) && tit.getOwnerId() != this.windowOwner.getWurmId()) {
                        this.windowOwner.getCommunicator().sendAlertServerMessage(tit.getName() + " is not owned by you. Trade aborted.");
                        this.watcher.getCommunicator().sendAlertServerMessage(tit.getName() + " is not owned by " + this.windowOwner.getName() + ". Trade aborted.");
                        return false;
                    }

                    Item[] allItems = tit.getAllItems(false);

                    for (Item lAllItem : allItems) {
                        if ((this.windowOwner instanceof Player || !lAllItem.isCoin()) && lAllItem.getOwnerId() != this.windowOwner.getWurmId()) {
                            this.windowOwner.getCommunicator().sendAlertServerMessage(lAllItem.getName() + " is not owned by you. Trade aborted.");
                            this.watcher.getCommunicator().sendAlertServerMessage(lAllItem.getName() + " is not owned by " + this.windowOwner.getName() + ". Trade aborted.");
                            return false;
                        }
                    }
                }
            }

            return true;
        }
    }

    @Override
    void swapOwners() {
        if (!this.offer) {
            Item inventory = this.watcher.getInventory();
            CrafterTradeHandler handler;
            WorkBook workBook;
            Shop shop;
            int moneyLost = 0;
            try {
                if (CrafterTemplate.isCrafter(windowOwner)) {
                    shop = Economy.getEconomy().getShop(this.windowOwner);
                    workBook = WorkBook.getWorkBookFromWorker(windowOwner);
                    handler = (CrafterTradeHandler)windowOwner.getTradeHandler();
                } else {
                    shop = Economy.getEconomy().getShop(this.watcher);
                    workBook = WorkBook.getWorkBookFromWorker(watcher);
                    handler = (CrafterTradeHandler)watcher.getTradeHandler();
                }
            } catch (WorkBook.NoWorkBookOnWorker e) {
                windowOwner.getCommunicator().sendAlertServerMessage("The crafter fumbles about for their work book but does not find it.");
                watcher.getCommunicator().sendAlertServerMessage("The crafter fumbles about for their work book but does not find it.");
                logger.warning("Could not find work book on crafter " + windowOwner.getName() + "(" + windowOwner.getWurmId() + ") or " + watcher.getName() + "(" + watcher.getWurmId() + ")");
                e.printStackTrace();
                return;
            }

            if (this.items != null) {
                for (Item item : items) {
                    this.removeExistingContainedItems(item);
                    this.removeFromTrade(item, false);
                    boolean coin = item.isCoin();

                    if (!(this.windowOwner instanceof Player)) {
                        if (this.watcher.isLogged()) {
                            this.watcher.getLogger().log(Level.INFO, this.windowOwner.getName() + " trading " + item.getName() + " with id " + item.getWurmId() + " from " + this.watcher.getName());
                        }
                    } else if (!(this.watcher instanceof Player)) {
                        if (this.windowOwner.isLogged()) {
                            this.windowOwner.getLogger().log(Level.INFO, this.windowOwner.getName() + " selling " + item.getName() + " with id " + item.getWurmId() + " to " + this.watcher.getName());
                        }
                    }

                    try {
                        Item parent = Items.getItem(item.getParentId());
                        parent.dropItem(item.getWurmId(), false);
                    } catch (NoSuchItemException var36) {
                        if (!(coin || handler.isOptionItem(item))) {
                            logger.log(Level.WARNING, "Parent not found for item " + item.getWurmId());
                        }
                    }

                    // Window 4
                    if (!(this.watcher instanceof Player)) {
                        if (coin) {
                            getLogger(shop.getWurmId()).log(Level.INFO, this.watcher.getName() + " received " + MaterialUtilities.getMaterialString(item.getMaterial()) + " " + item.getName() + ", id: " + item.getWurmId() + ", QL: " + item.getQualityLevel());
                            Economy.getEconomy().returnCoin(item, "CrafterTrade");
                        } else {
                            try {
                                if (!handler.isDonating()) {
                                    workBook.addJob(windowOwner.getWurmId(), item, handler.getTargetQL(item), handler.isMailOnDone(), watcher.getTradeHandler().getTraderBuyPriceForItem(item) + (handler.isMailOnDone() ? CrafterMod.mailPrice() : 0));
                                } else {
                                    workBook.addDonation(item);
                                }
                            } catch (WorkBook.WorkBookFull e) {
                                // This should never happen because it should be cleared by CrafterTrade.makeTrade().
                                windowOwner.getCommunicator().sendAlertServerMessage("An error occurred with the order.  Please report.");
                                logger.warning("Item WurmId(" + item.getWurmId() + ") Price Charged(" + watcher.getTradeHandler().getTraderBuyPriceForItem(item) + (handler.isMailOnDone() ? CrafterMod.mailPrice() : 0) + ") could not be added to Work Book, and player money was still taken.  This should never happen, please report.");
                                e.printStackTrace();
                            }
                            inventory.insertItem(item);
                            getLogger(shop.getWurmId()).log(Level.INFO, this.watcher.getName() + " received " + MaterialUtilities.getMaterialString(item.getMaterial()) + " " + item.getName() + ", id: " + item.getWurmId() + ", QL: " + item.getQualityLevel());
                        }
                    // Window 3
                    } else {
                        if (!handler.isOptionItem(item))
                            inventory.insertItem(item);
                        if (coin) {
                            if (shop.getOwnerId() == this.watcher.getWurmId()) {
                                moneyLost += Economy.getValueFor(item.getTemplateId());
                                getLogger(shop.getWurmId()).log(Level.INFO, this.watcher.getName() + " received " + MaterialUtilities.getMaterialString(item.getMaterial()) + " " + item.getName() + ", id: " + item.getWurmId() + ", QL: " + item.getQualityLevel());
                            }
                        } else {
                            workBook.removeJob(item);
                        }
                    }
                }
            }

            this.windowOwner.getCommunicator().sendNormalServerMessage("The trade was completed successfully.");

            if (CrafterTemplate.isCrafter(windowOwner)) {
                long diff = trade.getMoneyAdded() - trade.getOrderTotal() - moneyLost;
                if (diff != 0) {
                    shop.setMoney(shop.getMoney() + diff);
                }
            } else {
                long forCrafter = 0;
                long forKing = 0;
                long forUpkeep = 0;

                switch (CrafterMod.getPaymentOption()) {
                    case all_tax:
                        forKing = this.trade.getOrderTotal();
                        break;
                    case tax_and_upkeep:
                        forUpkeep = (long)(this.trade.getOrderTotal() * CrafterMod.getUpkeepPercentage());
                        forKing = trade.getOrderTotal() - forUpkeep;
                        break;
                    case for_owner:
                        forCrafter = (long)((float)this.trade.getOrderTotal() * 0.9F);
                        forKing = this.trade.getOrderTotal() - forCrafter;
                        break;
                }

                if (forCrafter != 0L) {
                    shop.addMoneyEarned(forCrafter);
                }
                if (forUpkeep != 0L) {
                    Village v = watcher.getCitizenVillage();
                    if (v == null)
                        forKing += forUpkeep;
                    else {
                        v.plan.addMoney(forUpkeep);
                        // Using MoneySpent to show how much upkeep is accumulated over time.
                        shop.addMoneySpent(forUpkeep);
                    }
                }
                if (forKing != 0L) {
                    Shop kingsMoney = Economy.getEconomy().getKingsShop();
                    kingsMoney.setMoney(kingsMoney.getMoney() + forKing);
                    shop.addMoneySpent(forKing);
                }

                shop.setLastPolled(System.currentTimeMillis());
            }
        } else {
            this.windowOwner.getCommunicator().sendAlertServerMessage("There is a bug in the trade system. This shouldn't happen. Please report.");
            this.watcher.getCommunicator().sendAlertServerMessage("There is a bug in the trade system. This shouldn't happen. Please report.");
            logger.log(Level.WARNING, "Inconsistency! This is offer window number " + this.wurmId + ". Traders are " + this.watcher.getName() + ", " + this.windowOwner.getName());
        }

    }

    @Override
    void endTrade() {
        if (this.items != null) {
            for (Item item : items.toArray(new Item[0])) {
                this.removeExistingContainedItems(item);
                this.items.remove(item);
                this.removeFromTrade(item, true);
            }
        }

        this.items = null;
    }
}

