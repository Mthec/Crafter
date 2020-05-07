package com.wurmonline.server.questions;

import com.wurmonline.server.creatures.Creature;
import com.wurmonline.shared.util.MaterialUtilities;
import mod.wurmunlimited.bml.BMLBuilder;
import mod.wurmunlimited.npcs.CrafterMod;
import mod.wurmunlimited.npcs.WorkBook;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

public class CrafterMaterialRestrictionQuestion extends CrafterQuestionExtension {
    private final Creature crafter;
    private final List<Byte> restrictedMaterials;
    private final WorkBook workBook;

    public CrafterMaterialRestrictionQuestion(Creature responder, @Nullable Creature crafter) throws WorkBook.NoWorkBookOnWorker {
        super(responder, "Restrict Materials", "", MANAGETRADER, crafter == null ? -10 : crafter.getWurmId());
        if (crafter == null) {
            this.crafter = null;
            workBook = null;
            restrictedMaterials = CrafterMod.getRestrictedMaterials();
        } else {
            this.crafter = crafter;
            workBook = WorkBook.getWorkBookFromWorker(crafter);
            restrictedMaterials = workBook.getRestrictedMaterials();
        }

    }

    @Override
    public void answer(Properties properties) {
        setAnswer(properties);
        if (!wasSelected("cancel")) {
            if (wasSelected("save")) {
                List<Byte> toRemove = new ArrayList<>();
                for (int i = 0; i <= restrictedMaterials.size(); ++i) {
                    String value = properties.getProperty("r" + i);
                    if (value != null && value.equals("true")) {
                        toRemove.add(restrictedMaterials.get(i));
                    }
                }

                restrictedMaterials.removeAll(toRemove);

                if (crafter == null) {
                    CrafterMod.saveRestrictedMaterials(restrictedMaterials);
                    getResponder().getCommunicator().sendNormalServerMessage("Globally restricted materials were updated successfully.");
                } else {
                    try {
                        workBook.updateRestrictedMaterials(restrictedMaterials);
                    } catch (WorkBook.WorkBookFull e) {
                        getResponder().getCommunicator().sendNormalServerMessage("Crafter was unable to update their list of restricted materials.");
                        return;
                    }

                    getResponder().getCommunicator().sendNormalServerMessage("Crafter successfully updated their list of restricted materials.");
                }
            } else if (wasSelected("add")) {
                new CrafterAddRestrictedMaterialQuestion(getResponder(), crafter, workBook).sendQuestion();
            }
        }
    }

    @Override
    public void sendQuestion() {
        AtomicInteger i = new AtomicInteger(0);
        String bml = new BMLBuilder(id)
                             .If(crafter == null,
                                     b -> b.text("Choose which materials all crafters will be allowed to use."),
                                     b -> b.text("Choose which materials the crafter will be allowed to use."))
                             .text("Empty list to allow all.")
                             .table(new String[] { "Skill", "Remove?" }, restrictedMaterials,
                                     (row, b) -> b.label(MaterialUtilities.getMaterialString(row))
                                                  .checkbox("r" + i.getAndIncrement(), ""))
                             .spacer()
                             .harray(b -> b.button("save", "Confirm").spacer()
                                           .button("add", "Add").spacer()
                                           .button("cancel", "Cancel"))
                             .build();

        getResponder().getCommunicator().sendBml(300, 400, true, true, bml, 200, 200, 200, title);
    }
}
