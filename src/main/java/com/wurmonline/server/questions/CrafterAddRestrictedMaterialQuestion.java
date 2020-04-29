package com.wurmonline.server.questions;

import com.wurmonline.server.creatures.Creature;
import com.wurmonline.shared.constants.ItemMaterials;
import com.wurmonline.shared.util.MaterialUtilities;
import mod.wurmunlimited.bml.BMLBuilder;
import mod.wurmunlimited.npcs.WorkBook;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class CrafterAddRestrictedMaterialQuestion extends CrafterQuestionExtension {
    private final Creature crafter;
    private final WorkBook workBook;
    private final List<Byte> restrictedMaterials;
    private final List<Byte> materials = new ArrayList<>(ItemMaterials.MATERIAL_MAX);

    CrafterAddRestrictedMaterialQuestion(Creature responder, Creature crafter, WorkBook workBook) {
        super(responder, "Add Restricted Material", "", MANAGETRADER, crafter.getWurmId());
        this.crafter = crafter;
        this.workBook = workBook;
        restrictedMaterials = workBook.getRestrictedMaterials();
    }

    @Override
    public void answer(Properties properties) {
        try {
            if (wasSelected("add")) {
                int idx = Integer.parseInt(properties.getProperty("mat"));
                byte material = materials.get(idx);
                restrictedMaterials.add(material);
                workBook.updateRestrictedMaterials(restrictedMaterials);

                getResponder().getCommunicator().sendNormalServerMessage("Crafter will now accept " + MaterialUtilities.getMaterialString(material) + " items.");
            }

            new CrafterMaterialRestrictionQuestion(getResponder(), crafter).sendQuestion();
        } catch (WorkBook.WorkBookFull e) {
            logger.warning("Crafter workbook was full, this probably shouldn't have happened.");
            e.printStackTrace();
            getResponder().getCommunicator().sendNormalServerMessage("Crafter could not add to their list.");
        } catch (WorkBook.NoWorkBookOnWorker e) {
            logger.warning("Crafter workbook was missing, this should have failed earlier.");
            e.printStackTrace();
            getResponder().getCommunicator().sendNormalServerMessage("Crafter could not add to their list.");
        }
    }

    @Override
    public void sendQuestion() {
        List<String> materialNames = new ArrayList<>(ItemMaterials.MATERIAL_MAX);
        for (int x = 1; x <= ItemMaterials.MATERIAL_MAX; ++x) {
            byte y = (byte)x;
            if (!restrictedMaterials.contains(y)) {
                String str = MaterialUtilities.getMaterialString(y);
                if (!str.equals("unknown")) {
                    materials.add(y);
                    materialNames.add(str);
                }
            }
        }

        String bml = new BMLBuilder(id)
                             .text("Choose a material to allow:")
                             .dropdown("mat", materialNames)
                             .harray(b -> b.button("add", "Add").spacer().button("cancel", "Cancel"))
                             .build();

        getResponder().getCommunicator().sendBml(300, 200, true, true, bml, 200, 200, 200, title);
    }
}
