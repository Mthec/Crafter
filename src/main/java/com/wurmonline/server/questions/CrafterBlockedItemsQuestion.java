package com.wurmonline.server.questions;

import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.ItemTemplate;
import com.wurmonline.server.items.ItemTemplateFactory;
import com.wurmonline.server.items.NoSuchTemplateException;
import mod.wurmunlimited.bml.BMLBuilder;
import mod.wurmunlimited.npcs.CrafterMod;
import mod.wurmunlimited.npcs.WorkBook;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CrafterBlockedItemsQuestion extends CrafterQuestionExtension {
    private final Creature crafter;
    private final List<Integer> blockedItems = new ArrayList<>();
    private final WorkBook workBook;

    public CrafterBlockedItemsQuestion(Creature responder, @Nullable Creature crafter) throws WorkBook.NoWorkBookOnWorker {
        super(responder, "Blocked Items", "", MANAGETRADER, crafter == null ? -10 : crafter.getWurmId());
        if (crafter == null) {
            this.crafter = null;
            workBook = null;
            blockedItems.addAll(CrafterMod.blockedItems);
        } else {
            this.crafter = crafter;
            workBook = WorkBook.getWorkBookFromWorker(crafter);
            Set<Integer> items = new HashSet<>();
            items.addAll(workBook.getBlockedItems());
            items.addAll(CrafterMod.blockedItems);
            blockedItems.addAll(items);
            blockedItems.sort((i1, i2) -> {
                try {
                    ItemTemplate one = ItemTemplateFactory.getInstance().getTemplate(i1);
                    ItemTemplate two = ItemTemplateFactory.getInstance().getTemplate(i2);
                    return one.compareTo(two);
                } catch (NoSuchTemplateException ignored) {
                    return 0;
                }
            });
        }
    }

    @Override
    public void answer(Properties properties) {
        setAnswer(properties);
        if (!wasSelected("cancel")) {
            if (wasSelected("save")) {
                List<Integer> toRemove = new ArrayList<>();
                CrafterMaterialRestrictionQuestion.parseRemoved(blockedItems, properties, toRemove);

                blockedItems.removeAll(toRemove);

                if (crafter == null) {
                    CrafterMod.saveBlockedItems(blockedItems);
                    getResponder().getCommunicator().sendNormalServerMessage("Globally blocked items were updated successfully.");
                } else {
                    try {
                        workBook.updateBlockedItems(blockedItems);
                    } catch (WorkBook.WorkBookFull e) {
                        getResponder().getCommunicator().sendNormalServerMessage("Crafter was unable to update their list of blocked items.");
                        return;
                    }

                    getResponder().getCommunicator().sendNormalServerMessage("Crafter successfully updated their list of blocked items.");
                }
            } else if (wasSelected("add")) {
                new CrafterAddBlockedItemQuestion(getResponder(), crafter, workBook).sendQuestion();
            }
        }
    }

    @Override
    public void sendQuestion() {
        AtomicInteger i = new AtomicInteger(0);
        String bml = new BMLBuilder(id)
                             .If(crafter == null,
                                     b -> b.text("Choose which items all crafters will be prevented from working on."),
                                     b -> b.text("Choose which items this crafter will be prevented from working on."))
                             .table(new String[] { "Item", "Remove?" }, blockedItems,
                                     (row, b) -> b.label(ItemTemplateFactory.getInstance().getTemplateName(row))
                                                         .If(crafter != null && CrafterMod.blockedItems.contains(row),
                                                                 b2 -> { i.getAndIncrement(); return b2.label("Server"); },
                                                                 b2 -> b2.checkbox("r" + i.getAndIncrement(), "")))
                             .spacer()
                             .harray(b -> b.button("save", "Confirm").spacer()
                                           .button("add", "Add").spacer()
                                           .button("cancel", "Cancel"))
                             .build();

        getResponder().getCommunicator().sendBml(300, 400, true, true, bml, 200, 200, 200, title);
    }
}
