package com.wurmonline.server.behaviours;

import com.wurmonline.server.Server;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.questions.CrafterHireQuestion;
import com.wurmonline.server.zones.VolaTile;
import mod.wurmunlimited.npcs.CrafterMod;

public class PlaceCrafterAction implements NpcMenuEntry {
    public PlaceCrafterAction() {
        PlaceNpcMenu.addNpcAction(this);
    }

    @Override
    public String getName() {
        return "Crafter";
    }

    @Override
    public boolean doAction(Action action, short num, Creature performer, Item source, VolaTile tile, int floorLevel) {
        Item contract = null;
        try {
            for (Item item : performer.getAllItems()) {
                if (item.getTemplateId() == CrafterMod.getContractTemplateId()) {
                    contract = item;
                    break;
                }
            }

            if (contract == null) {
                contract = Creature.createItem(CrafterMod.getContractTemplateId(), (float)(10 + Server.rand.nextInt(80)));
                performer.getInventory().insertItem(contract);
            }

            new CrafterHireQuestion(performer, contract.getWurmId()).sendQuestion();
        } catch (Exception e) {
            e.printStackTrace();
            performer.getCommunicator().sendNormalServerMessage("A new contract could not be created.");
        }

        return true;
    }
}
