package com.wurmonline.server.behaviours;

import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.questions.CrafterManagementQuestion;
import mod.wurmunlimited.npcs.CrafterMod;
import mod.wurmunlimited.npcs.CrafterTemplate;
import org.gotti.wurmunlimited.modsupport.actions.*;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public class ManageCrafterAction implements ModAction, ActionPerformer, BehaviourProvider {
    private static final Logger logger = Logger.getLogger(ManageCrafterAction.class.getName());
    private final short actionId;
    private final List<ActionEntry> entries;
    private final List<ActionEntry> empty = Collections.emptyList();

    public ManageCrafterAction() {
        actionId = (short)ModActions.getNextActionId();
        ActionEntry actionEntry = new ActionEntryBuilder(actionId, "Manage", "managing").build();
        ModActions.registerAction(actionEntry);
        entries = Collections.singletonList(actionEntry);
    }

    private static boolean writValid(Creature performer, Creature crafter, @Nullable Item writ) {
        return writ != null && writ.getTemplateId() == CrafterMod.getContractTemplateId() &&
                       performer.getInventory().getItems().contains(writ) && writ.getData() == crafter.getWurmId();
    }

    private static boolean canManage(Creature performer, Creature crafter, @Nullable Item item) {
        if (!(performer.isPlayer() && CrafterTemplate.isCrafter(crafter)))
            return false;

        return performer.getPower() >= CrafterMod.gmManagePowerRequired() || writValid(performer, crafter, item);
    }

    private List<ActionEntry> getBehaviours(Creature performer, Creature crafter, @Nullable Item subject) {
        if (canManage(performer, crafter, subject)) {
            return entries;
        }

        return empty;
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item subject, Creature target) {
        return getBehaviours(performer, target, subject);
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Creature target) {
        return getBehaviours(performer, target, null);
    }

    private boolean action(Creature performer, Creature crafter, @Nullable Item item) {
        if (canManage(performer, crafter, item)) {
            new CrafterManagementQuestion((Player)performer, crafter).sendQuestion();
        }

        return true;
    }

    @Override
    public boolean action(Action action, Creature performer, Item source, Creature target, short num, float counter) {
        if (num == actionId) {
            return action(performer, target, source);
        }

        return true;
    }

    @Override
    public boolean action(Action action, Creature performer, Creature target, short num, float counter) {
        if (num == actionId) {
            return action(performer, target, null);
        }

        return true;
    }

    @Override
    public short getActionId() {
        return actionId;
    }
}
