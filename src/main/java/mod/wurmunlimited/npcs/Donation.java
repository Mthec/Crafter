package mod.wurmunlimited.npcs;

import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;

public class Donation extends Job {

    Donation(Item item) {
        super(0, item, 100, false, 0, false);
    }

    @Override
    public String toString() {
        return item.getWurmId() + "\n";
    }

    @Override
    public boolean isDonation() {
        return true;
    }

    @Override
    public boolean isCustomer(Creature creature) {
        return false;
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public void mailToCustomer() {
    }

    @Override
    public void refundCustomer() {
    }


}
