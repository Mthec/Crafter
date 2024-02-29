package com.wurmonline.server.behaviours;

import com.wurmonline.server.NoSuchItemException;
import com.wurmonline.server.NoSuchPlayerException;
import com.wurmonline.server.Server;
import com.wurmonline.server.Servers;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.NoSuchCreatureException;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.villages.Village;
import com.wurmonline.server.zones.NoSuchZoneException;
import mod.wurmunlimited.npcs.CrafterAIData;
import mod.wurmunlimited.npcs.CrafterTemplate;
import mod.wurmunlimited.npcs.Job;
import mod.wurmunlimited.npcs.WorkBook;
import org.gotti.wurmunlimited.modsupport.actions.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class ThreatenAction implements ModAction, ActionPerformer, BehaviourProvider {
    private static final Logger logger = Logger.getLogger(ThreatenAction.class.getName());
    private final short actionId;
    private final ActionEntry actionEntry;
    private static final List<ActionEntry> empty = Collections.emptyList();

    public ThreatenAction() {
        actionId = (short) ModActions.getNextActionId();

        actionEntry = new ActionEntryBuilder(actionId, "Threaten", "threatening", ItemBehaviour.emptyIntArr).build();
        ModActions.registerAction(actionEntry);
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Creature target) {
        if (Servers.localServer.PVPSERVER && Servers.localServer.isChallengeOrEpicServer() && !Servers.localServer.HOMESERVER && target.getTemplate().getTemplateId() == CrafterTemplate.getTemplateId() && !target.isFriendlyKingdom(performer.getKingdomId())) {
            return Collections.singletonList(actionEntry);
        }
        return empty;
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item subject, Creature target) {
        return getBehavioursFor(performer, target);
    }

    @Override
    public boolean action(Action action, Creature performer, Creature target, short num, float counter) {
        if (Servers.localServer.PVPSERVER && Servers.localServer.isChallengeOrEpicServer() && !Servers.localServer.HOMESERVER) {
            if (target.getFloorLevel() == performer.getFloorLevel() && performer.getMountVehicle() == null) {
                if (target.isFriendlyKingdom(performer.getKingdomId())) {
                    performer.getCommunicator().sendNormalServerMessage("You can't rob " + target.getName() + "!");
                } else if (target.getTemplate().getTemplateId() != CrafterTemplate.getTemplateId()) {
                    performer.getCommunicator().sendNormalServerMessage(target.getName() + " snorts at you and refuses to yield.");
                } else {
                    int time = action.getTimeLeft();
                    Skill taunting = performer.getSkills().getSkillOrLearn(SkillList.TAUNTING);

                    if (counter == 1.0f) {
                        Village v = target.getCurrentVillage();
                        if (v != null && v.getGuards().length > 0) {
                            performer.getCommunicator().sendNormalServerMessage("There are guards in the vicinity. You can't start robbing " + target.getName() + " now.");
                            return true;
                        }

                        performer.getCommunicator().sendNormalServerMessage("You start to rob " + target.getNameWithGenus() + ".");
                        time = Actions.getSlowActionTime(performer, taunting, null, 0.0) * 10;
                        Server.getInstance().broadCastAction(performer.getNameWithGenus() + " starts robbing " + target.getNameWithGenus(), performer, 10);
                        performer.sendActionControl("threatening", true, time);
                        action.setTimeLeft(time);
                        performer.getStatus().modifyStamina(-500f);
                    }

                    if (!(counter * 10f <= (float)time)) {
                        if (taunting.skillCheck(target.getSoulStrengthVal(), 0.0, false, 20f) <= 0.0) {
                            performer.getCommunicator().sendNormalServerMessage(target.getName() + " snorts and refuses to yield.");
                            Server.getInstance().broadCastAction(performer.getNameWithGenus() + " fails to scare " + target.getNameWithGenus() + ".", performer, 10);
                        } else {
                            performer.getCommunicator().sendNormalServerMessage(target.getNameWithGenus() + " looks really scared and fetches " + target.getHisHerItsString() + " job items.");
                            Server.getInstance().broadCastAction(performer.getNameWithGenus() + " scares " + target.getNameWithGenus() + " into fetching " + target.getHisHerItsString() + " job items.", performer, 10);

                            // Hand over items.
                            CrafterAIData data = (CrafterAIData) target.getCreatureAIData();
                            WorkBook workBook = data.getWorkBook();
                            Set<Item> removed = new HashSet<>();
                            for (Job job : workBook) {
                                try {
                                    Item item = job.getItem();
                                    item.putItemInfrontof(performer);
                                    removed.add(item);
                                } catch (NoSuchCreatureException | NoSuchItemException | NoSuchPlayerException | NoSuchZoneException e) {
                                    logger.info(target.getName() + " : " + e);
                                }
                            }

                            removed.forEach(workBook::removeJob);
                        }

                        return true;
                    }
                }
            } else {
                performer.getCommunicator().sendNormalServerMessage("You can't reach " + target.getName() + " there.");
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean action(Action action, Creature performer, Item source, Creature target, short num, float counter) {
        return action(action, performer, target, num, counter);
    }

    @Override
    public short getActionId() {
        return actionId;
    }
}
