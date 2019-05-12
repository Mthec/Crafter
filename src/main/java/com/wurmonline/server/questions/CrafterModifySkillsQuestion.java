package com.wurmonline.server.questions;

import com.wurmonline.server.FailedException;
import com.wurmonline.server.Items;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.NoSuchTemplateException;
import com.wurmonline.server.skills.Skill;
import mod.wurmunlimited.bml.BMLBuilder;
import mod.wurmunlimited.npcs.*;

import java.util.*;

import static com.wurmonline.server.questions.CrafterHireQuestion.allCrafterTypes;

public class CrafterModifySkillsQuestion extends CrafterQuestionExtension {
    private final Creature crafter;

    CrafterModifySkillsQuestion(Creature responder, Creature crafter) {
        super(responder, "Modify Skill set", "", QuestionTypes.MANAGETRADER, crafter.getWurmId());
        this.crafter = crafter;
    }

    @Override
    public void answer(Properties answers) {
        setAnswer(answers);
        Creature responder = getResponder();

        Set<Integer> skills = new HashSet<>();
        if (wasSelected("all_metal"))
            Collections.addAll(skills, CrafterType.allMetal);
        if (wasSelected("all_wood"))
            Collections.addAll(skills, CrafterType.allWood);
        if (wasSelected("all_armour"))
            Collections.addAll(skills, CrafterType.allArmour);

        for (String val : allCrafterTypes) {
            try {
                if (wasSelected(val))
                    skills.add(Integer.parseInt(val));
            } catch (NumberFormatException e) {
                logger.info("Invalid crafter type (" + answers.getProperty(val) + ") received when updating skill settings.  Ignoring.");
            }
        }
        if (skills.size() == 0) {
            responder.getCommunicator().sendNormalServerMessage("You must select at least one crafter type.");
            return;
        }
        CrafterType crafterType = new CrafterType(skills.toArray(new Integer[0]));
        float skillCap;
        try {
            skillCap = Float.parseFloat(getStringProp("skill_cap"));
            if (skillCap < 20) {
                responder.getCommunicator().sendNormalServerMessage("Skill cap was too low, setting minimum value.");
                skillCap = 20;
            } else if (skillCap > CrafterMod.getSkillCap()) {
                responder.getCommunicator().sendNormalServerMessage("Skill cap was too high, setting maximum value.");
                skillCap = CrafterMod.getSkillCap();
            }
        } catch (NumberFormatException e) {
            responder.getCommunicator().sendNormalServerMessage("Skill cap value was invalid.");
            return;
        }

        boolean removeDonationItems = wasSelected("rd");
        boolean refundItems = wasSelected("refund");

        try {
            WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
            List<Job> toRemove = new ArrayList<>();
            List<Job> toRefund = new ArrayList<>();

            for (Job job : workBook) {
                if (!job.isDone()) {
                    if (!crafterType.hasSkillToImprove(job.getItem())) {
                        if (job.isDonation()) {
                            if (removeDonationItems)
                                toRemove.add(job);
                        } else if (refundItems) {
                            toRefund.add(job);
                        } else {
                            responder.getCommunicator().sendNormalServerMessage(crafter.getName() + " still has some jobs that require disabled skills.  Settings not updated.");
                            return;
                        }
                    }
                }
            }

            for (Job remove : toRemove) {
                workBook.removeJob(remove.getItem());
                Items.destroyItem(remove.getItem().getWurmId());
            }

            try {
                for (Job refund : toRefund) {
                    if (!crafter.getInventory().getItems().contains(refund.getItem()))
                        crafter.getInventory().insertItem(refund.getItem());

                    refund.mailToCustomer();
                    refund.refundCustomer();
                    workBook.removeJob(refund.getItem());
                }
            } catch (NoSuchTemplateException | FailedException e) {
                logger.warning("Could not create refund package while changing Crafter skills, customers were not compensated.");
                e.printStackTrace();
            }

            workBook.updateSkillsSettings(crafterType, skillCap);

            for (Skill skill : crafterType.getSkillsFor(crafter)) {
                if (skill.getKnowledge() < CrafterMod.getStartingSkillLevel()) {
                    skill.setKnowledge(CrafterMod.getStartingSkillLevel(), false);
                }
                if (skill.getKnowledge() > skillCap) {
                    skill.setKnowledge(skillCap, false);
                }
                // Parent skills.
                for (int skillId : skill.getDependencies()) {
                    crafter.getSkills().getSkillOrLearn(skillId);
                }
            }

            responder.getCommunicator().sendNormalServerMessage("Crafter successfully updated.");
        } catch (WorkBook.NoWorkBookOnWorker e) {
            logger.warning("Could not find workbook on crafter (" + crafter.getWurmId() + ").");
            responder.getCommunicator().sendNormalServerMessage(crafter.getName() + " seems to have misplaced their workbook");
            e.printStackTrace();
        } catch (WorkBook.WorkBookFull e) {
            responder.getCommunicator().sendNormalServerMessage(crafter.getName() + " has run out of space in their workbook and cannot update their settings.");
            e.printStackTrace();
        }
    }

    @Override
    public void sendQuestion() {
        BMLBuilder builder = new BMLBuilder(id);
        CrafterType crafterType;
        try {
            crafterType = WorkBook.getWorkBookFromWorker(crafter).getCrafterType();
        } catch (WorkBook.NoWorkBookOnWorker e) {
            logger.warning("Could not find workbook on crafter (" + crafter.getWurmId() + ").");
            getResponder().getCommunicator().sendNormalServerMessage(crafter.getName() + " seems to have misplaced their workbook");
            e.printStackTrace();
            return;
        }

        String bml = CrafterHireQuestion.addSkillsBML(builder, crafterType)
                .checkbox("Remove unneeded donation items", "rd")
                .checkbox("Refund items if skill removed", "refund")
                .harray(b -> b.button("Save").spacer().button("cancel", "Cancel"))
                .build();


        getResponder().getCommunicator().sendBml(400, 300, false, true, bml, 200, 200, 200, title);
    }
}
