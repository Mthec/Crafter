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
import com.wurmonline.shared.util.StringUtilities;
import mod.wurmunlimited.bml.BMLBuilder;
import mod.wurmunlimited.npcs.*;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Properties;

import static com.wurmonline.server.creatures.CreaturePackageCaller.saveCreatureName;

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

        String faceString = getStringProp("face");
        long face;
        if (faceString.isEmpty()) {
            face = crafter.getFace();
        } else {
            try {
                face = Long.parseLong(faceString);
            } catch (NumberFormatException e) {
                responder.getCommunicator().sendAlertServerMessage("Invalid face value, ignoring.");
                face = crafter.getFace();
            }
        }

        if (faceString.isEmpty()) {
            try {
                responder.getCommunicator().sendCustomizeFace(face, CrafterMod.faceSetters.createIdFor(crafter, responder));
            } catch (CrafterFaceSetters.TooManyTransactionsException e) {
                logger.warning(e.getMessage());
                responder.getCommunicator().sendAlertServerMessage(e.getMessage());
            }
        } else if (face != crafter.getFace()) {
            try {
                CrafterDatabase.setFaceFor(crafter, face);
                responder.getCommunicator().sendNormalServerMessage("The crafter's face seems to shift about and takes a new form.");
            } catch (SQLException e) {
                logger.warning("Failed to set face (" + face + ") for crafter (" + crafter.getWurmId() + ").");
                e.printStackTrace();
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
        } else if (wasSelected("stop")) {
            try {
                WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
                boolean noJobs = true;
                for (Job job : workBook) {
                    if (job.isDone()) {
                        continue;
                    }

                    Item item = job.getItem();
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
                                 .harray(b -> b.label("Face:").entry("face", Long.toString(crafter.getFace()), CrafterHireQuestion.faceMaxChars))
                                 .text("Blank to create a face on the next screen, or paste a face code here.").italic()
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
                                 .harray(b -> b.button("Send").spacer().button("dismiss", "Dismiss").confirm("You are about to dismiss " + crafter.getName() + ".", "Do you really want to do that?").spacer()
                                                      .If(workBook.todo() > 0, b2 -> b2.button("stop", "Stop current job").confirm("Stop current job.", "Are you sure you wish to stop the current job?  This will refund the order and return the item to the customer.").spacer())
                                                      .If(CrafterMod.canChangeSkill(), b2 -> b2.button("skills", "Modify skills").spacer())
                                                      .button("restrict", "Restrict Materials"))
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
