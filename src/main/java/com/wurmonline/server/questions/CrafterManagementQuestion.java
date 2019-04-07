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
import mod.wurmunlimited.bml.BMLBuilder;
import mod.wurmunlimited.npcs.*;

import java.util.Properties;

public class CrafterManagementQuestion extends CrafterQuestionExtension {
    private final Creature crafter;
    private final Shop shop;

    public CrafterManagementQuestion(Creature aResponder, Creature crafter) {
        super(aResponder, "Crafter Details", "", QuestionTypes.MANAGETRADER, crafter.getWurmId());
        this.crafter = crafter;
        shop = Economy.getEconomy().getShop(crafter);
    }

    @Override
    public void answer(Properties properties) {
        setAnswer(properties);

        String val = properties.getProperty("price_modifier");
        if (val != null && val.length() > 0) {
            try {
                float modifier = Float.parseFloat(val);
                if (modifier <= 0)
                    getResponder().getCommunicator().sendSafeServerMessage("Price modifier must be positive.");
                else
                    shop.setPriceModifier(modifier);
            } catch (NumberFormatException e) {
                getResponder().getCommunicator().sendSafeServerMessage("Price modifier must be a number.");
            }
        }
        if (wasSelected("dismiss")) {
            dismiss();
        }
    }

    @Override
    public void sendQuestion() {
        if (shop != null) {
            CrafterAIData data = (CrafterAIData)crafter.getCreatureAIData();
            WorkBook workBook = data.getWorkBook();

            String bml = new BMLBuilder(id)
                                 .text("Name - " + crafter.getName())
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
                                         b -> b.text("Money to collect - " + (shop.getMoney() == 0 ? "Nothing" : new Change(shop.getMoney()).getChangeShortString())))
                                 .harray(b -> b.label("Price Modifier: ").entry("price_modifier", Float.toString(shop.getPriceModifier()), 4))
                                 .newLine()
                                 .harray(b -> b.button("Send").spacer().button("dismiss", "Dismiss").confirm("You are about to dismiss " + crafter.getName() + ".", "Do you really want to do that?"))
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
