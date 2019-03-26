package mod.wurmunlimited.npcs;

import com.google.common.base.Joiner;
import com.wurmonline.server.FailedException;
import com.wurmonline.server.Servers;
import com.wurmonline.server.TimeConstants;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.economy.Economy;
import com.wurmonline.server.items.*;

public class Job {
    final long customerId;
    final Item item;
    final float targetQL;
    final boolean mailWhenDone;
    boolean done;
    private final long priceCharged;

    Job(long customerId, Item item, float targetQL, boolean mailWhenDone, long priceCharged, boolean done) {
        this.customerId = customerId;
        this.item = item;
        this.targetQL = targetQL;
        this.mailWhenDone = mailWhenDone;
        this.done = done;
        this.priceCharged = priceCharged;
    }

    public String toString() {
        return Joiner.on(",").join(customerId, item.getWurmId(), targetQL, mailWhenDone ? "1" : "0", priceCharged, done ? "1" : "0") + "\n";
    }

    // Is it good practice to a have a separate version like this?
    public static String toString(long customerId, Item item, float targetQL, boolean mailWhenDone, long priceCharged, boolean done) {
        return Joiner.on(",").join(customerId, item.getWurmId(), targetQL, mailWhenDone ? "1" : "0", priceCharged, done ? "1" : "0") + "\n";
    }

    public boolean isDonation() {
        return false;
    }

    public boolean isCustomer(Creature creature) {
        return creature.getWurmId() == customerId;
    }

    public Item getItem() {
        return item;
    }

    public boolean isDone() {
        return done;
    }

    public boolean mailWhenDone() {
        return mailWhenDone;
    }

    public long getPriceCharged() {
        return priceCharged;
    }

    private void mailToCustomer(Item itemToMail) {
        itemToMail.setBusy(false);
        WurmMail mail = new WurmMail(WurmMail.MAIL_TYPE_PREPAID, itemToMail.getWurmId(), 1, customerId, 0, System.currentTimeMillis() + TimeConstants.MINUTE_MILLIS, System.currentTimeMillis() + (Servers.isThisATestServer() ? 3600000L : 14515200000L), Servers.localServer.id, false, false);
        WurmMail.addWurmMail(mail);
        mail.createInDatabase();
        itemToMail.putInVoid();
        itemToMail.setMailed(true);
        itemToMail.setMailTimes((byte)(itemToMail.getMailTimes() + 1));
    }

    public void mailToCustomer() {
        mailToCustomer(item);
    }

    public void refundCustomer() throws NoSuchTemplateException, FailedException {
        Item box = ItemFactory.createItem(ItemList.jarPottery, 1, "");
        Item[] coins = Economy.getEconomy().getCoinsFor(priceCharged);
        for (Item coin : coins) {
            box.insertItem(coin, true);
        }

        mailToCustomer(box);
    }
}