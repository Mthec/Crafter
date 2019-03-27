package com.wurmonline.server.behaviours;

import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.Creatures;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.items.ItemTypes;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.structures.Structure;
import com.wurmonline.server.zones.VolaTile;
import com.wurmonline.server.zones.Zones;
import mod.wurmunlimited.npcs.CrafterAI;
import mod.wurmunlimited.npcs.CrafterAIData;
import org.gotti.wurmunlimited.modsupport.actions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public class AssignAction implements ModAction, BehaviourProvider, ActionPerformer {
    private final short actionId;
    private final int contractTemplateId;

    public AssignAction(int contractTemplateId) {
        this.contractTemplateId = contractTemplateId;
        actionId = (short)ModActions.getNextActionId();
        ModActions.registerAction(new ActionEntry(actionId, "Assign", "Assigning"));
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item subject, Item target) {
        if (performer instanceof Player && subject != null && target != null && subject.getTemplateId() == contractTemplateId
                    && subject.getOwnerId() == performer.getWurmId() && subject.getData() != -1 && target.getTemplateId() == ItemList.forge) {
            Creature crafter = Creatures.getInstance().getCreatureOrNull(subject.getData());
            if (crafter != null) {
                if (((CrafterAIData)crafter.getCreatureAIData()).getWorkBook().isForgeAssigned()) {
                    return Collections.singletonList(new ActionEntry(actionId, "Unassign", "unassigning"));
                } else {
                    return Collections.singletonList(new ActionEntry(actionId, "Assign", "assigning"));
                }
            }
        }
        return null;
    }

    @Override
    public boolean action(Action action, Creature performer, Item source, Item target, short num, float counter) {
        if (num != actionId || target == null || target.getTemplateId() != ItemList.forge)
            return false;
        Creature crafter = Creatures.getInstance().getCreatureOrNull(source.getData());
        if (crafter != null) {
            CrafterAIData data = (CrafterAIData)crafter.getCreatureAIData();
            if (data.getWorkBook().isForgeAssigned()) {
                for (Item item : data.getForge().getItemsAsArray()) {
                    crafter.getInventory().insertItem(item);
                }
                data.setForge(null);
                performer.getCommunicator().sendNormalServerMessage("You unassign this forge from " + crafter.getName() + ".");
            } else {
                if (CrafterAI.assignedForges.values().contains(target)) {
                    performer.getCommunicator().sendNormalServerMessage("That forge is already assigned to another crafter.");
                } else if (!Methods.isActionAllowed(crafter, Actions.TAKE)) {
                    performer.getCommunicator().sendNormalServerMessage(crafter.getName() + " would not have permission to access this forge.");
                } else if (target.getItemCount() > 0) {
                    performer.getCommunicator().sendNormalServerMessage("You must empty the forge before assigning it.");
                } else if (!crafter.isWithinDistanceTo(target, crafter.getMaxHuntDistance())) {
                    performer.getCommunicator().sendNormalServerMessage("That forge is too far away.");
                } else {
                    data.setForge(target);
                    performer.getCommunicator().sendNormalServerMessage("You assign this forge to " + crafter.getName() + ".");
                }
            }
            return true;
        }

        return false;
    }

    @Override
    public short getActionId() {
        return actionId;
    }
}
