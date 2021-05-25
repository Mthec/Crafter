package com.wurmonline.server.behaviours;

import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.Creatures;
import com.wurmonline.server.creatures.NoSuchCreatureException;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.questions.CrafterHireQuestion;
import com.wurmonline.server.questions.CrafterManagementQuestion;
import org.gotti.wurmunlimited.modsupport.actions.*;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public class CrafterContractAction implements ModAction, ActionPerformer, BehaviourProvider {
    private final Logger logger = Logger.getLogger(CrafterContractAction.class.getName());
    private final int contractTemplateId;
    private final short actionId;
    private final ActionEntry actionEntry;

    public CrafterContractAction(int contractTemplateId) {
        this.contractTemplateId = contractTemplateId;

        actionId = (short)ModActions.getNextActionId();

        actionEntry = new ActionEntryBuilder(actionId, "Manage", "managing", ItemBehaviour.emptyIntArr).build();
        ModActions.registerAction(actionEntry);
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item subject, Item target) {
        return getBehavioursFor(performer, target);
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item target) {
        if (target.getTemplateId() == contractTemplateId)
            return Collections.singletonList(actionEntry);
        return null;
    }

    @Override
    public boolean action(Action action, Creature performer, Item subject, Item target, short num, float counter) {
        return action(action, performer, target, num, counter);
    }

    @Override
    public boolean action(Action action, Creature performer, Item target, short num, float counter) {
        if (num == actionId && target.getTemplateId() == contractTemplateId) {
            if (target.getData() == -1) {
                new CrafterHireQuestion(performer, target.getWurmId()).sendQuestion();
            } else {
                try {
                    new CrafterManagementQuestion(performer, Creatures.getInstance().getCreature(target.getData())).sendQuestion();
                } catch (NoSuchCreatureException e) {
                    performer.getCommunicator().sendNormalServerMessage("You attempt to manage the crafter, but they don't exist for some reason.");
                    logger.warning("Could not get crafter creature for some reason.");
                    e.printStackTrace();
                }
            }
        }
        return true;
    }

    @Override
    public short getActionId() {
        return actionId;
    }
}
