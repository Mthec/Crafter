//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
// Modified from version by CodeClub AB
//

package com.wurmonline.server.items;

import com.wurmonline.server.creatures.CrafterTradeHandler;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.economy.Economy;
import com.wurmonline.server.economy.Shop;
import com.wurmonline.server.players.Player;
import mod.wurmunlimited.npcs.CrafterMod;
import mod.wurmunlimited.npcs.CrafterTemplate;
import mod.wurmunlimited.npcs.Job;
import mod.wurmunlimited.npcs.WorkBook;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CrafterTrade extends Trade {
    private static final Logger logger = Logger.getLogger(CrafterTrade.class.getName());
    private final TradingWindow creatureOneOfferWindow;
    private final TradingWindow creatureTwoOfferWindow;
    private final TradingWindow creatureOneRequestWindow;
    private final TradingWindow creatureTwoRequestWindow;
    private boolean creatureOneSatisfied = false;
    private boolean creatureTwoSatisfied = false;
    private int currentCounter = -1;
    private long moneyAdded;
    private WorkBook workBook;
    private long orderTotal;

    public CrafterTrade(Creature player, Creature crafter) throws WorkBook.NoWorkBookOnWorker {
        workBook = WorkBook.getWorkBookFromWorker(crafter);
        creatureOne = player;
        creatureOne.startTrading();
        creatureTwo = crafter;
        creatureTwo.startTrading();
        creatureTwoOfferWindow = new CrafterTradingWindow(crafter, player, true, 1L, this);
        creatureOneOfferWindow = new CrafterTradingWindow(player, crafter, true, 2L, this);
        creatureOneRequestWindow = new CrafterTradingWindow(crafter, player, false, 3L, this);
        creatureTwoRequestWindow = new CrafterTradingWindow(player, crafter, false, 4L, this);
    }

    public WorkBook getWorkBook() {
        return workBook;
    }

    @Override
    public void setMoneyAdded(long money) {
        moneyAdded = money;
    }

    @Override
    public void addShopDiff(long money) {
    }

    @Override
    long getMoneyAdded() {
        return moneyAdded;
    }

    public void setOrderTotal(long cost) {
        orderTotal = cost;
    }

    public long getOrderTotal() {
        return orderTotal;
    }

    @Override
    public TradingWindow getTradingWindow(long id) {
        switch ((int)id) {
            case 1:
                return this.creatureTwoOfferWindow;
            case 2:
                return this.creatureOneOfferWindow;
            case 3:
                return this.creatureOneRequestWindow;
            case 4:
            default:
                return this.creatureTwoRequestWindow;
        }
    }

    @Override
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

    @Override
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


                    CrafterTradeHandler handler = (CrafterTradeHandler)creatureTwo.getTradeHandler();
                    List<String> requiredSpace = Stream.of(getTradingWindow(4).getItems()).filter(i -> !i.isCoin()).map(i -> Job.toString(creatureOne.getWurmId(),
                            i, handler.getTargetQL(i), handler.isMailOnDone(), handler.getTraderBuyPriceForItem(i) + (handler.isMailOnDone() ? CrafterMod.mailPrice() : 0), false))
                    .collect(Collectors.toList());
                    if (!workBook.hasEnoughSpaceFor(requiredSpace)) {
                        creatureOne.getCommunicator().sendNormalServerMessage(creatureTwo.getName() + " says, 'I already have too many orders to be working on.  Sorry for the inconvenience.'", (byte)3);
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
                        Shop shop;
                        if (CrafterTemplate.isCrafter(creatureOne)) {
                            shop = Economy.getEconomy().getShop(creatureOne);
                            shop.setMerchantData(creatureOne.getNumberOfShopItems());
                        }

                        if (CrafterTemplate.isCrafter(creatureTwo)) {
                            shop = Economy.getEconomy().getShop(creatureTwo);
                            shop.setMerchantData(creatureTwo.getNumberOfShopItems());
                        }

                        this.creatureOne.setTrade(null);
                        this.creatureTwo.setTrade(null);
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

    @Override
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
        this.creatureOne.setTrade(null);
        this.creatureTwo.setTrade(null);
    }

    @Override
    boolean isCreatureOneSatisfied() {
        return this.creatureOneSatisfied;
    }

    @Override
    void setCreatureOneSatisfied(boolean aCreatureOneSatisfied) {
        this.creatureOneSatisfied = aCreatureOneSatisfied;
    }

    @Override
    boolean isCreatureTwoSatisfied() {
        return this.creatureTwoSatisfied;
    }

    @Override
    void setCreatureTwoSatisfied(boolean aCreatureTwoSatisfied) {
        this.creatureTwoSatisfied = aCreatureTwoSatisfied;
    }

    @Override
    public int getCurrentCounter() {
        return this.currentCounter;
    }

    @Override
    void setCurrentCounter(int aCurrentCounter) {
        this.currentCounter = aCurrentCounter;
    }

    @Override
    public long getTax() {
        throw new UnsupportedOperationException("Method not used");
    }

    @Override
    public void setTax(long aTax) {
        throw new UnsupportedOperationException("Method not used");
    }

    @Override
    public TradingWindow getCreatureOneRequestWindow() {
        return this.creatureOneRequestWindow;
    }

    @Override
    public TradingWindow getCreatureTwoRequestWindow() {
        return this.creatureTwoRequestWindow;
    }

    @Override
    Creature getCreatureOne() {
        return this.creatureOne;
    }

    @Override
    Creature getCreatureTwo() {
        return this.creatureTwo;
    }
}

