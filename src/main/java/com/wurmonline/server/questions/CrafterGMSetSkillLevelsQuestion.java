package com.wurmonline.server.questions;

import com.wurmonline.server.FailedException;
import com.wurmonline.server.Items;
import com.wurmonline.server.behaviours.MethodsItems;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.NoSuchTemplateException;
import com.wurmonline.server.skills.NoSuchSkillException;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillSystem;
import mod.wurmunlimited.bml.BMLBuilder;
import mod.wurmunlimited.npcs.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class CrafterGMSetSkillLevelsQuestion extends CrafterQuestionExtension {
    private final Creature crafter;

    public CrafterGMSetSkillLevelsQuestion(Creature aResponder, Creature crafter) {
        super(aResponder, "Set Crafter Skill Levels", "", QuestionTypes.MANAGETRADER, crafter.getWurmId());
        this.crafter = crafter;
    }

    @Override
    public void answer(Properties properties) {
        Creature responder = getResponder();
        if (responder.getPower() < 2)
            return;

        setAnswer(properties);

        if (wasSelected("cancel")) {
            responder.getCommunicator().sendNormalServerMessage("No changes were made to the crafter.");
        } else {
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
                if (getStringProp("skill_cap").isEmpty()) {
                    skillCap = -1;
                } else {
                    responder.getCommunicator().sendNormalServerMessage("Skill cap value was invalid.");
                    return;
                }
            }

            boolean removeDonationItems = wasSelected("rd");
            boolean refundItems = wasSelected("refund");

            try {
                WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
                CrafterType crafterType = workBook.getCrafterType();
                List<Job> toRemove = new ArrayList<>();
                List<Job> toRefund = new ArrayList<>();

                if (skillCap == -1)
                    skillCap = workBook.getSkillCap();

                workBook.updateSkillsSettings(crafterType, skillCap);

                for (Skill skill : crafterType.getSkillsFor(crafter)) {
                    float newSkillLevel = getFloatOrDefault(String.valueOf(skill.getNumber()), (float)skill.getKnowledge());
                    skill.setKnowledge(newSkillLevel, false);

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

                for (Job job : workBook) {
                    if (!job.isDone()) {
                        try {
                            double knowledge = crafter.getSkills().getSkill(MethodsItems.getImproveSkill(job.getItem())).getKnowledge();
                            if (!job.isDonation() && job.getTargetQL() > knowledge) {
                                if (refundItems) {
                                    toRefund.add(job);
                                } else {
                                    responder.getCommunicator().sendNormalServerMessage(crafter.getName() + " still has some jobs that require certain skill levels.  Settings not updated.");
                                    return;
                                }
                            } else if (job.isDonation() && removeDonationItems) {
                                float itemQL = job.getItem().getQualityLevel();
                                if (!CrafterMod.canLearn() || itemQL > skillCap + 10) {
                                    toRemove.add(job);
                                }
                            }
                        } catch (NoSuchSkillException e) {
                            logger.warning("Could not find " + SkillSystem.getNameFor(MethodsItems.getImproveSkill(job.getItem())) + " skill for " + crafter.getName() + " (" + crafter.getWurmId() + ")");
                            e.printStackTrace();
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

                responder.getCommunicator().sendNormalServerMessage("Crafter successfully updated their workbook.");
            } catch (WorkBook.NoWorkBookOnWorker e) {
                logger.warning("Could not find workbook on crafter (" + crafter.getWurmId() + ").");
                responder.getCommunicator().sendNormalServerMessage(crafter.getName() + " seems to have misplaced their workbook");
                e.printStackTrace();
            } catch (WorkBook.WorkBookFull e) {
                responder.getCommunicator().sendNormalServerMessage(crafter.getName() + " has run out of space in their workbook and cannot update their settings.");
                e.printStackTrace();
            }
        }
    }

    @Override
    public void sendQuestion() {
        CrafterAIData data = (CrafterAIData)crafter.getCreatureAIData();
        WorkBook workBook = data.getWorkBook();

        String bml = new BMLBuilder(id)
                             .text("Name - " + crafter.getName())
                             .text("The minimum skill level is " + CrafterMod.getStartingSkillLevel())
                             .If(CrafterMod.canLearn(),
                                     b -> b.harray(b2 -> b2.label("Skill Cap: ").entry("skill_cap", Float.toString(workBook.getSkillCap()), 5).text("Max: " + CrafterMod.getSkillCap()).italic()),
                                     b -> b.text("Skills will be capped at " + CrafterMod.getSkillCap() + "."))
                             .newLine()
                             .table(new String[] { "Skill", "Current", "New" }, workBook.getCrafterType().getSkillsFor(crafter),
                                     (skill, b) -> b.label(skill.getName()).label(String.format("%.2f", skill.getKnowledge())).entry(String.valueOf(skill.getNumber()), "", 7))
                             .newLine()
                             .checkbox("rd", "Remove unneeded donation items", true)
                             .checkbox("refund", "Refund items if skill level insufficient", true)
                             .harray(b -> b.button("Save").spacer().button("cancel", "Cancel"))
                             .build();

        getResponder().getCommunicator().sendBml(300, 400, false, true, bml, 200, 200, 200, title);
    }
}
