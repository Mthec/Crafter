package mod.wurmunlimited.npcs;

import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;

import java.util.ArrayList;
import java.util.List;

public class CrafterWearItems implements WearItems {
    private final List<Item> jobItems = new ArrayList<>();

    @Override
    public void beforeWearing(Creature creature) {
        CrafterAIData data = ((CrafterAIData)creature.getCreatureAIData());
        WorkBook workBook = data.getWorkBook();
        if (workBook != null) {
            for (Item item : creature.getInventory().getItemsAsArray()) {
                if (workBook.isJobItem(item) || data.isTool(item)) {
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

    @Override
    public boolean isApplicableCreature(Creature creature) {
        return CrafterTemplate.isCrafter(creature);
    }
}
