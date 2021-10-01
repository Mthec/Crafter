package com.wurmonline.server.questions;

import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.ItemTemplate;
import mod.wurmunlimited.bml.BMLBuilder;
import mod.wurmunlimited.npcs.CrafterMod;
import mod.wurmunlimited.npcs.WorkBook;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

public class CrafterAddBlockedItemQuestion extends CrafterQuestionExtension {
    private final Creature crafter;
    private final WorkBook workBook;
    private final Set<Integer> currentlyBlockedItems = new HashSet<>();
    private final CrafterEligibleTemplates eligibleTemplates;
    private final String filter;

    CrafterAddBlockedItemQuestion(Creature responder, @Nullable Creature crafter, @Nullable WorkBook workBook) {
        this(responder, crafter, workBook, null);
    }

    CrafterAddBlockedItemQuestion(Creature responder, @Nullable Creature crafter, @Nullable WorkBook workBook, @Nullable String filter) {
        super(responder, "Add Blocked Item", "", MANAGETRADER, crafter == null ? -10 : crafter.getWurmId());
        this.crafter = crafter;
        this.workBook = workBook;
        CrafterEligibleTemplates.init();
        currentlyBlockedItems.addAll(CrafterMod.blockedItems);
        if (crafter != null && workBook != null) {
            currentlyBlockedItems.addAll(workBook.getBlockedItems());
        }

        this.filter = filter == null ? "" : filter;
        eligibleTemplates = new CrafterEligibleTemplates(currentlyBlockedItems, this.filter);
    }

    @Override
    public void answer(Properties properties) {
        setAnswer(properties);
        try {
            if (wasSelected("do_filter")) {
                String filter = (String)properties.getOrDefault("filter", "");
                if (!filter.isEmpty()) {
                    new CrafterAddBlockedItemQuestion(getResponder(), crafter, workBook, filter).sendQuestion();
                    return;
                }
            } else if (wasSelected("add")) {
                int idx = Integer.parseInt(properties.getProperty("item"));
                ItemTemplate template = eligibleTemplates.getTemplate(idx);
                currentlyBlockedItems.add(template.getTemplateId());

                if (crafter == null) {
                    CrafterMod.saveBlockedItems(currentlyBlockedItems);
                    getResponder().getCommunicator().sendNormalServerMessage("Crafters will now not accept " + template.getPlural() + ".");
                } else {
                    workBook.updateBlockedItems(currentlyBlockedItems);
                    getResponder().getCommunicator().sendNormalServerMessage("This crafter will now not accept " + template.getPlural() + ".");
                }
            }

            new CrafterBlockedItemsQuestion(getResponder(), crafter).sendQuestion();
        } catch (WorkBook.WorkBookFull e) {
            getResponder().getCommunicator().sendNormalServerMessage("Crafter has run out of space on their list.");
        } catch (WorkBook.NoWorkBookOnWorker e) {
            logger.warning("Crafter workbook was missing, this should have failed earlier.");
            e.printStackTrace();
            getResponder().getCommunicator().sendNormalServerMessage("Crafter could not add to their list.");
        }
    }

    @Override
    public void sendQuestion() {
        String bml = new BMLBuilder(id)
                             .text("Choose an item type to block:")
                             .text("Filter available templates:")
                             .text("* is a wildcard that stands in for one or more characters.\ne.g. *clay* to find all clay items or lump* to find all types of lump.")
                             .newLine()
                             .harray(b -> b.entry("filter", filter, 25).spacer()
                                     .button("do_filter", "Apply"))
                             .newLine()
                             .dropdown("item", eligibleTemplates.getOptions())
                             .spacer()
                             .harray(b -> b.button("add", "Add").spacer().button("cancel", "Cancel"))
                             .build();

        getResponder().getCommunicator().sendBml(300, 200, true, true, bml, 200, 200, 200, title);
    }
}
