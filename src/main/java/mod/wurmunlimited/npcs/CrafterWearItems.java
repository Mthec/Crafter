package mod.wurmunlimited.npcs;

import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;

import java.util.ArrayList;
import java.util.List;

public class CrafterWearItems implements WearItems {
    private final List<Item> jobItems = new ArrayList<>();

    @Override
    public void beforeWearing(Creature creature) {
        WorkBook workBook = ((CrafterAIData)creature.getCreatureAIData()).getWorkBook();
        if (workBook != null) {
            for (Item item : creature.getInventory().getItemsAsArray()) {
                if (workBook.isJobItem(item)) {
                    jobItems.add(item);
                    creature.getInventory().getItems().remove(item);
                }
            }
        }
    }

    @Override
    public void afterWearing(Creature creature) {
        for (Item item : jobItems) {
            creature.getInventory().getItems().add(item);
        }

        jobItems.clear();
    }
}
