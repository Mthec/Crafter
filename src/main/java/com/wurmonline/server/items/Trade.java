//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.wurmonline.server.items;

import com.wurmonline.server.MiscConstants;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.economy.Economy;
import com.wurmonline.server.economy.Shop;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.villages.Village;

import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings("ALL")
public class Trade implements MiscConstants {
    private final TradingWindow creatureOneOfferWindow;
    private final TradingWindow creatureTwoOfferWindow;
    private final TradingWindow creatureOneRequestWindow;
    private final TradingWindow creatureTwoRequestWindow;
    public Creature creatureOne;
    public Creature creatureTwo;
    private boolean creatureOneSatisfied = false;
    private boolean creatureTwoSatisfied = false;
    private int currentCounter = -1;
    private static final Logger logger = Logger.getLogger(Trade.class.getName());
    public static final long OFFERWINTWO = 1L;
    public static final long OFFERWINONE = 2L;
    public static final long REQUESTWINONE = 3L;
    public static final long REQUESTWINTWO = 4L;
    private long moneyAdded = 0L;
    private long shopDiff = 0L;
    private long tax = 0L;

    public Trade(Creature aCreatureOne, Creature aCreatureTwo) {
        this.creatureOne = aCreatureOne;
        this.creatureOne.startTrading();
        this.creatureTwo = aCreatureTwo;
        this.creatureTwo.startTrading();
        this.creatureTwoOfferWindow = new TradingWindow(aCreatureTwo, aCreatureOne, true, 1L, this);
        this.creatureOneOfferWindow = new TradingWindow(aCreatureOne, aCreatureTwo, true, 2L, this);
        this.creatureOneRequestWindow = new TradingWindow(aCreatureTwo, aCreatureOne, false, 3L, this);
        this.creatureTwoRequestWindow = new TradingWindow(aCreatureOne, aCreatureTwo, false, 4L, this);
    }

    public Trade() {
        creatureOneOfferWindow = null;
        creatureTwoOfferWindow = null;
        creatureOneRequestWindow = null;
        creatureTwoRequestWindow = null;
        creatureOne = null;
        creatureTwo = null;
    }

    public void setMoneyAdded(long money) {
        this.moneyAdded = money;
    }

    public void addShopDiff(long money) {
        if (this.creatureOne.getPower() >= 5) {
            this.creatureOne.getCommunicator().sendNormalServerMessage("Adding " + money + " to shop diff " + this.shopDiff + "=" + (this.shopDiff + money));
        }

        this.shopDiff += money;
    }

    long getMoneyAdded() {
        return this.moneyAdded;
    }

    public TradingWindow getTradingWindow(long id) {
        if (id == 1L) {
            return this.creatureTwoOfferWindow;
        } else if (id == 2L) {
            return this.creatureOneOfferWindow;
        } else {
            return id == 3L ? this.creatureOneRequestWindow : this.creatureTwoRequestWindow;
        }
    }

    public void setSatisfied(Creature creature, boolean satisfied, int id) {
        if (id == this.currentCounter) {
            if (creature.equals(this.creatureOne)) {
                this.creatureOneSatisfied = satisfied;
            } else {
                this.creatureTwoSatisfied = satisfied;
            }

            if (this.creatureOneSatisfied && this.creatureTwoSatisfied) {
                if (this.makeTrade()) {
                    this.creatureOne.getCommunicator().sendCloseTradeWindow();
                    this.creatureTwo.getCommunicator().sendCloseTradeWindow();
                } else {
                    this.creatureOne.getCommunicator().sendTradeAgree(creature, satisfied);
                    this.creatureTwo.getCommunicator().sendTradeAgree(creature, satisfied);
                }
            } else {
                this.creatureOne.getCommunicator().sendTradeAgree(creature, satisfied);
                this.creatureTwo.getCommunicator().sendTradeAgree(creature, satisfied);
            }
        }

    }

    int getNextTradeId() {
        return ++this.currentCounter;
    }

    private boolean makeTrade() {
        if ((!this.creatureOne.isPlayer() || this.creatureOne.hasLink()) && !this.creatureOne.isDead()) {
            if ((!this.creatureTwo.isPlayer() || this.creatureTwo.hasLink()) && !this.creatureTwo.isDead()) {
                if (this.creatureOneRequestWindow.hasInventorySpace() && this.creatureTwoRequestWindow.hasInventorySpace()) {
                    int reqOneWeight = this.creatureOneRequestWindow.getWeight();
                    int reqTwoWeight = this.creatureTwoRequestWindow.getWeight();
                    int diff = reqOneWeight - reqTwoWeight;
                    if (diff > 0 && this.creatureOne instanceof Player && !this.creatureOne.canCarry(diff)) {
                        this.creatureTwo.getCommunicator().sendNormalServerMessage(this.creatureOne.getName() + " cannot carry that much.", (byte)3);
                        this.creatureOne.getCommunicator().sendNormalServerMessage("You cannot carry that much.", (byte)3);
                        if (this.creatureOne.getPower() > 0) {
                            this.creatureOne.getCommunicator().sendNormalServerMessage("You cannot carry that much. You would carry " + diff + " more.");
                        }

                        return false;
                    }

                    diff = reqTwoWeight - reqOneWeight;
                    if (diff > 0 && this.creatureTwo instanceof Player && !this.creatureTwo.canCarry(diff)) {
                        this.creatureOne.getCommunicator().sendNormalServerMessage(this.creatureTwo.getName() + " cannot carry that much.", (byte)3);
                        this.creatureTwo.getCommunicator().sendNormalServerMessage("You cannot carry that much.", (byte)3);
                        return false;
                    }

                    boolean ok = this.creatureOneRequestWindow.validateTrade();
                    if (!ok) {
                        return false;
                    }

                    ok = this.creatureTwoRequestWindow.validateTrade();
                    if (ok) {
                        this.creatureOneRequestWindow.swapOwners();
                        this.creatureTwoRequestWindow.swapOwners();
                        this.creatureTwoOfferWindow.endTrade();
                        this.creatureOneOfferWindow.endTrade();
                        Shop shop = null;
                        Village citizenVillage = null;
                        if (this.creatureOne.isNpcTrader()) {
                            shop = Economy.getEconomy().getShop(this.creatureOne);
                            shop.setMerchantData(this.creatureOne.getNumberOfShopItems());
                            citizenVillage = this.creatureOne.getCitizenVillage();
                        }

                        if (this.creatureTwo.isNpcTrader()) {
                            shop = Economy.getEconomy().getShop(this.creatureTwo);
                            shop.setMerchantData(this.creatureTwo.getNumberOfShopItems());
                            citizenVillage = this.creatureTwo.getCitizenVillage();
                        }

                        if (shop != null && this.shopDiff != 0L) {
                            if (this.shopDiff > 0L) {
                                if (shop.getTax() > 0.0F && !shop.isPersonal() && citizenVillage != null && this.creatureOne.citizenVillage != citizenVillage && citizenVillage.plan != null) {
                                    this.setTax((long)((float)this.shopDiff * shop.getTax()));
                                    logger.log(Level.INFO, this.creatureOne.getName() + " and " + this.creatureTwo.getName() + " adding " + this.getTax() + " tax to " + citizenVillage.getName());
                                    citizenVillage.plan.addMoney(this.getTax());
                                    shop.addTax(this.getTax());
                                }

                                if (this.creatureOne.getPower() >= 5) {
                                    this.creatureOne.getCommunicator().sendNormalServerMessage("Adding " + (this.shopDiff - this.getTax()) + " to shop");
                                }

                                shop.addMoneyEarned(this.shopDiff - this.getTax());
                            } else {
                                if (this.creatureOne.getPower() >= 5) {
                                    this.creatureOne.getCommunicator().sendNormalServerMessage("Shop spending " + this.shopDiff + ".");
                                }

                                shop.addMoneySpent(Math.abs(this.shopDiff));
                            }

                            if (this.creatureOne.getPower() >= 5) {
                                this.creatureOne.getCommunicator().sendNormalServerMessage("Shop setting money " + (shop.getMoney() + this.shopDiff - this.getTax()) + " (" + shop.getMoney() + " before).");
                            }

                            shop.setMoney(shop.getMoney() + this.shopDiff - this.getTax());
                        }

                        this.creatureOne.setTrade((Trade)null);
                        this.creatureTwo.setTrade((Trade)null);
                        return true;
                    }
                }

                return false;
            } else {
                if (this.creatureTwo.hasLink()) {
                    this.creatureTwo.getCommunicator().sendNormalServerMessage("You may not trade right now.", (byte)3);
                }

                this.creatureOne.getCommunicator().sendNormalServerMessage(this.creatureTwo.getName() + " cannot trade right now.", (byte)3);
                this.end(this.creatureTwo, false);
                return true;
            }
        } else {
            if (this.creatureOne.hasLink()) {
                this.creatureOne.getCommunicator().sendNormalServerMessage("You may not trade right now.", (byte)3);
            }

            this.creatureTwo.getCommunicator().sendNormalServerMessage(this.creatureOne.getName() + " cannot trade right now.", (byte)3);
            this.end(this.creatureOne, false);
            return true;
        }
    }

    public void end(Creature creature, boolean closed) {
        try {
            if (creature.equals(this.creatureOne)) {
                this.creatureTwo.getCommunicator().sendCloseTradeWindow();
                if (!closed) {
                    this.creatureOne.getCommunicator().sendCloseTradeWindow();
                }

                this.creatureTwo.getCommunicator().sendNormalServerMessage(this.creatureOne.getName() + " withdrew from the trade.", (byte)2);
                this.creatureOne.getCommunicator().sendNormalServerMessage("You withdraw from the trade.", (byte)2);
            } else {
                this.creatureOne.getCommunicator().sendCloseTradeWindow();
                if (!closed || !this.creatureTwo.isPlayer()) {
                    this.creatureTwo.getCommunicator().sendCloseTradeWindow();
                }

                this.creatureOne.getCommunicator().sendNormalServerMessage(this.creatureTwo.getName() + " withdrew from the trade.", (byte)2);
                this.creatureTwo.getCommunicator().sendNormalServerMessage("You withdraw from the trade.", (byte)2);
            }
        } catch (Exception var4) {
            logger.log(Level.WARNING, var4.getMessage(), var4);
        }

        this.creatureTwoOfferWindow.endTrade();
        this.creatureOneOfferWindow.endTrade();
        this.creatureOneRequestWindow.endTrade();
        this.creatureTwoRequestWindow.endTrade();
        this.creatureOne.setTrade((Trade)null);
        this.creatureTwo.setTrade((Trade)null);
    }

    boolean isCreatureOneSatisfied() {
        return this.creatureOneSatisfied;
    }

    void setCreatureOneSatisfied(boolean aCreatureOneSatisfied) {
        this.creatureOneSatisfied = aCreatureOneSatisfied;
    }

    boolean isCreatureTwoSatisfied() {
        return this.creatureTwoSatisfied;
    }

    void setCreatureTwoSatisfied(boolean aCreatureTwoSatisfied) {
        this.creatureTwoSatisfied = aCreatureTwoSatisfied;
    }

    public int getCurrentCounter() {
        return this.currentCounter;
    }

    void setCurrentCounter(int aCurrentCounter) {
        this.currentCounter = aCurrentCounter;
    }

    public long getTax() {
        return this.tax;
    }

    public void setTax(long aTax) {
        this.tax = aTax;
    }

    public TradingWindow getCreatureOneRequestWindow() {
        return this.creatureOneRequestWindow;
    }

    public TradingWindow getCreatureTwoRequestWindow() {
        return this.creatureTwoRequestWindow;
    }

    Creature getCreatureOne() {
        return this.creatureOne;
    }

    Creature getCreatureTwo() {
        return this.creatureTwo;
    }
}

