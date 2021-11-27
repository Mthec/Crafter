package com.wurmonline.server.questions;

import com.wurmonline.server.FailedException;
import com.wurmonline.server.Server;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.Creatures;
import com.wurmonline.server.economy.Change;
import com.wurmonline.server.economy.Economy;
import com.wurmonline.server.economy.Shop;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.NoSuchTemplateException;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.villages.NoSuchRoleException;
import com.wurmonline.server.villages.Village;
import com.wurmonline.server.villages.VillageRole;
import com.wurmonline.server.villages.VillageStatus;
import com.wurmonline.shared.util.StringUtilities;
import mod.wurmunlimited.bml.BMLBuilder;
import mod.wurmunlimited.npcs.*;

import java.io.IOException;
import java.util.Properties;

import static com.wurmonline.server.creatures.CreaturePackageCaller.saveCreatureName;
import static com.wurmonline.server.questions.CrafterHireQuestion.modelOptions;

public class CrafterManagementQuestion extends CrafterQuestionExtension {
    private final Player responder;
    private final Creature crafter;
    private final Shop shop;

    public CrafterManagementQuestion(Player responder, Creature crafter) {
        super(responder, "Crafter Details", "", QuestionTypes.MANAGETRADER, crafter.getWurmId());
        this.responder = responder;
        this.crafter = crafter;
        shop = Economy.getEconomy().getShop(crafter);
    }

    @Override
    public void answer(Properties properties) {
        setAnswer(properties);

        String name = getStringProp("name");
        if (name != null && !name.isEmpty()) {
            String fullName = getPrefix() + StringUtilities.raiseFirstLetter(name);
            if (QuestionParser.containsIllegalCharacters(name)) {
                responder.getCommunicator().sendNormalServerMessage("The crafter didn't like that name, so they shall remain " + crafter.getName() + ".");
            } else if (!fullName.equals(crafter.getName())) {
                try {
                    saveCreatureName(crafter, fullName);
                    crafter.refreshVisible();
                    responder.getCommunicator().sendNormalServerMessage("The crafter will now be known as " + crafter.getName() + ".");
                } catch (IOException e) {
                    logger.warning("Failed to set name (" + fullName + ") for creature (" + crafter.getWurmId() + ").");
                    responder.getCommunicator().sendNormalServerMessage("The crafter looks confused, what exactly is a database?");
                    e.printStackTrace();
                }
            }
        }

        String val = properties.getProperty("price_modifier");
        if (val != null && val.length() > 0) {
            try {
                float priceModifier = Float.parseFloat(val);
                if (priceModifier <= 0)
                    responder.getCommunicator().sendSafeServerMessage("Price modifier must be positive.");
                else {
                    if (priceModifier < CrafterMod.getMinimumPriceModifier()) {
                        responder.getCommunicator().sendSafeServerMessage("Price modifier was too low, setting minimum value.");
                        priceModifier = CrafterMod.getMinimumPriceModifier();
                    }
                    shop.setPriceModifier(priceModifier);
                }
            } catch (NumberFormatException e) {
                responder.getCommunicator().sendSafeServerMessage("Price modifier must be a number.");
            }
        }

        if (wasSelected("dismiss")) {
            dismiss();
        } else if (wasSelected("customise")) {
            new CreatureCustomiserQuestion(responder, crafter, CrafterMod.mod.faceSetter, CrafterMod.mod.modelSetter, modelOptions).sendQuestion();
        } else if (wasSelected("stop")) {
            try {
                WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
                boolean noJobs = true;
                for (Job job : workBook) {
                    if (job.isDone()) {
                        continue;
                    }

                    Item item = job.getItem();
                    job.mailToCustomer();
                    job.refundCustomer();
                    workBook.removeJob(item);
                    noJobs = false;
                    break;
                }

                if (noJobs) {
                    responder.getCommunicator().sendNormalServerMessage(crafter.getName() + " is not currently working on any jobs.");
                } else {
                    responder.getCommunicator().sendNormalServerMessage(crafter.getName() + " successfully refunded the job.");
                }
            } catch (WorkBook.NoWorkBookOnWorker e) {
                logger.warning("Crafter workbook was missing.");
                e.printStackTrace();
                responder.getCommunicator().sendNormalServerMessage(crafter.getName() + " fumbles about and cannot find their workbook.");
            } catch (NoSuchTemplateException | FailedException e) {
                logger.warning("Error occurred when attempting to refund stopped job.");
                e.printStackTrace();
                responder.getCommunicator().sendNormalServerMessage("Could not create refund package while stopping job, the customer was not compensated.");
            }
        } else if (wasSelected("skills")) {
            new CrafterModifySkillsQuestion(responder, crafter).sendQuestion();
        } else if (wasSelected("restrict")) {
            try {
                new CrafterMaterialRestrictionQuestion(getResponder(), crafter).sendQuestion();
            } catch (WorkBook.NoWorkBookOnWorker e) {
                logger.warning("Crafter workbook was missing.");
                e.printStackTrace();
                responder.getCommunicator().sendNormalServerMessage(crafter.getName() + " fumbles about and cannot find their workbook.");
            }
        } else if (wasSelected("block")) {
            try {
                new CrafterBlockedItemsQuestion(getResponder(), crafter).sendQuestion();
            } catch (WorkBook.NoWorkBookOnWorker e) {
                logger.warning("Crafter workbook was missing.");
                e.printStackTrace();
                responder.getCommunicator().sendNormalServerMessage(crafter.getName() + " fumbles about and cannot find their workbook.");
            }
        } else if (wasSelected("invite")) {
            Village village = responder.getCitizenVillage();
            if (village == null) {
                responder.getCommunicator().sendNormalServerMessage("You cannot invite " + crafter.getName() + " to your village because you do not belong to one.");
                return;
            }
            if (village == crafter.getCitizenVillage()) {
                responder.getCommunicator().sendNormalServerMessage(crafter.getName() + " is already a member of your village.");
                return;
            }
            if (!village.getRoleFor(responder).mayInviteCitizens()) {
                responder.getCommunicator().sendNormalServerMessage("You do not have permission to invite citizens to your village.");
                return;
            }
            try {
                VillageRole role = village.getRoleForStatus(VillageStatus.ROLE_CITIZEN);
                village.addCitizen(crafter, role);
                if (!role.mayPickup() || !role.mayImproveAndRepair()) {
                    responder.getCommunicator().sendSafeServerMessage(crafter.getName() + " successfully joined " + village.getName() + ", make sure they have the \"Improve\" permission (and \"Pickup\" if they will be using a forge).");
                }
            } catch (NoSuchRoleException e) {
                responder.getCommunicator().sendAlertServerMessage("Could not find default citizen role, please report.");
                logger.warning("Could not find default citizen role, pleases report.");
            } catch (IOException e) {
                responder.getCommunicator().sendAlertServerMessage("An error occurred in the rifts of the void.  Please report.");
                logger.warning("Error when adding crafter to village, pleases report.");
                e.printStackTrace();
            }
        }
    }

    @Override
    public void sendQuestion() {
        if (shop != null) {
            CrafterAIData data = (CrafterAIData)crafter.getCreatureAIData();
            WorkBook workBook = data.getWorkBook();

            String bml = new BMLBuilder(id)
                                 .harray(b -> b.label("Name: " + getPrefix()).entry("name", getNameWithoutPrefix(crafter.getName()), CrafterMod.maxNameLength))
                                 .table(new String[] { "Skill", "Current", "Max" }, workBook.getCrafterType().getSkillsFor(crafter),
                                         (skill, b) -> b.label(skill.getName()).label(String.format("%.2f", skill.getKnowledge())).label(Float.toString(workBook.getSkillCap())))
                                 .newLine()
                                 .text("Current jobs - " + workBook.todo())
                                 .text("Awaiting collection - " + workBook.done())
                                 .If(!workBook.getCrafterType().needsForge(),
                                         b -> b.text("Forge - Not needed"),
                                         b -> b.If(workBook.isForgeAssigned(),
                                                 b2 -> b2.text("Forge - Assigned"),
                                                 b2 -> b2.text("Forge - Not Assigned").error())
                                         )
                                 .If(CrafterMod.getPaymentOption() == CrafterMod.PaymentOption.for_owner,
                                         b -> b.text("Money to collect - " + (crafter.getShop().getMoney() == 0 ? "Nothing" : new Change(crafter.getShop().getMoney()).getChangeShortString())))
                                 .If(CrafterMod.canUsePriceModifier(),
                                         b -> b.harray(b2 -> b2.label("Price Modifier: ").entry("price_modifier", Float.toString(shop.getPriceModifier()), 4)))
                                 .newLine()
                                 .harray(b -> b.button("Send").spacer()
                                               .button("customise", "Appearance").spacer()
                                               .If(responder.getCitizenVillage() != crafter.getCitizenVillage(), b2 -> b2.button("invite", "Invite to settlement").spacer()))
                                 .harray(b -> b.If(CrafterMod.canChangeSkill(), b2 -> b2.button("skills", "Modify skills").spacer())
                                               .button("restrict", "Restrict Materials").spacer()
                                               .button("block", "Block Items").spacer()
                                               .If(workBook.todo() > 0, b2 -> b2.button("stop", "Stop current job").confirm("Stop current job.", "Are you sure you wish to stop the current job?  This will refund the order and return the item to the customer.").spacer()))
                                 .harray(b -> b.button("dismiss", "Dismiss").confirm("You are about to dismiss " + crafter.getName() + ".", "Do you really want to do that?"))
                                 .build();

            getResponder().getCommunicator().sendBml(300, 400, false, true, bml, 200, 200, 200, title);
        }
    }

    private void dismiss() {
        // Get again in case crafter was removed between question asking and answering.
        Creature crafter = Creatures.getInstance().getCreatureOrNull(target);
        Creature responder = getResponder();

        if (crafter != null) {
            if (!crafter.isTrading()) {
                Server.getInstance().broadCastAction(crafter.getName() + " grunts, packs " + crafter.getHisHerItsString() + " things and is off.", crafter, 5);
                responder.getCommunicator().sendNormalServerMessage("You dismiss " + crafter.getName() + ".");
                logger.info(responder.getName() + " dismisses crafter " + crafter.getName() + " with WurmID:" + target);

                for (Item item : responder.getInventory().getAllItems(true)) {
                    if (item.getTemplateId() == CrafterMod.getContractTemplateId() && item.getData() == target) {
                        item.setData(-1);
                        break;
                    }
                }

                try {
                    for (Job job : WorkBook.getWorkBookFromWorker(crafter)) {
                        job.mailToCustomer();
                        job.refundCustomer();
                    }
                } catch (WorkBook.NoWorkBookOnWorker e) {
                    logger.warning("Could not find Work Book while dismissing Crafter, customers were not compensated.");
                    e.printStackTrace();
                } catch (NoSuchTemplateException | FailedException e) {
                    logger.warning("Could not create refund package while dismissing Crafter, customers were not compensated.");
                    e.printStackTrace();
                }

                CrafterAI.assignedForges.remove(crafter);
                crafter.destroy();
            } else
                responder.getCommunicator().sendNormalServerMessage(crafter.getName() + " is trading.  Try later.");
        } else
            responder.getCommunicator().sendNormalServerMessage("The crafter cannot be dismissed now.");

    }
}
