package com.wurmonline.server.questions;

import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Materials;
import com.wurmonline.shared.constants.ItemMaterials;
import com.wurmonline.shared.util.MaterialUtilities;
import mod.wurmunlimited.bml.BMLBuilder;
import mod.wurmunlimited.npcs.CrafterMod;
import mod.wurmunlimited.npcs.WorkBook;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class CrafterAddRestrictedMaterialQuestion extends CrafterQuestionExtension {
    private final Creature crafter;
    private final WorkBook workBook;
    private final List<Byte> restrictedMaterials;
    private final List<Byte> materials = new ArrayList<>(ItemMaterials.MATERIAL_MAX);
    private final List<String> materialNames = new ArrayList<>(ItemMaterials.MATERIAL_MAX);

    CrafterAddRestrictedMaterialQuestion(Creature responder, @Nullable Creature crafter, @Nullable WorkBook workBook) {
        super(responder, "Add Restricted Material", "", MANAGETRADER, crafter == null ? -10 : crafter.getWurmId());
        this.crafter = crafter;
        this.workBook = workBook;
        if (crafter == null && workBook == null)
            restrictedMaterials = CrafterMod.getRestrictedMaterials();
        else {
            assert workBook != null;
            restrictedMaterials = workBook.getRestrictedMaterials();
        }

        if (crafter != null && CrafterMod.materialsRestrictedGlobally()) {
            for (byte y : CrafterMod.getRestrictedMaterials()) {
                if (!restrictedMaterials.contains(y)) {
                    materialNames.add(MaterialUtilities.getMaterialString(y));
                }
            }
        } else {
            for (int x = 1; x <= ItemMaterials.MATERIAL_MAX; ++x) {
                byte y = (byte)x;
                if (!restrictedMaterials.contains(y)) {
                    String str = MaterialUtilities.getMaterialString(y);
                    if (!str.equals("unknown") && (
                            MaterialUtilities.isMetal(y) ||
                                    MaterialUtilities.isWood(y) ||
                                    MaterialUtilities.isLeather(y) ||
                                    MaterialUtilities.isCloth(y) ||
                                    MaterialUtilities.isStone(y) ||
                                    MaterialUtilities.isClay(y))) {
                        materialNames.add(str);
                    }
                }
            }
        }

        materialNames.sort(String::compareTo);

        for (String materialName : materialNames)
            materials.add(Materials.convertMaterialStringIntoByte(materialName));
    }

    @Override
    public void answer(Properties properties) {
        setAnswer(properties);
        try {
            if (wasSelected("add")) {
                int idx = Integer.parseInt(properties.getProperty("mat"));
                byte material = materials.get(idx);
                restrictedMaterials.add(material);

                if (crafter == null) {
                    CrafterMod.saveRestrictedMaterials(restrictedMaterials);
                    getResponder().getCommunicator().sendNormalServerMessage("Crafters will now accept " + MaterialUtilities.getMaterialString(material) + " items.");
                } else {
                    workBook.updateRestrictedMaterials(restrictedMaterials);
                    getResponder().getCommunicator().sendNormalServerMessage("This Crafter will now accept " + MaterialUtilities.getMaterialString(material) + " items.");
                }
            }

            new CrafterMaterialRestrictionQuestion(getResponder(), crafter).sendQuestion();
        } catch (WorkBook.WorkBookFull e) {
            getResponder().getCommunicator().sendNormalServerMessage("Crafter has run out fo space on their list.");
        } catch (WorkBook.NoWorkBookOnWorker e) {
            logger.warning("Crafter workbook was missing, this should have failed earlier.");
            e.printStackTrace();
            getResponder().getCommunicator().sendNormalServerMessage("Crafter could not add to their list.");
        }
    }

    @Override
    public void sendQuestion() {
        String bml = new BMLBuilder(id)
                             .text("Choose a material to allow:")
                             .dropdown("mat", materialNames)
                             .spacer()
                             .harray(b -> b.button("add", "Add").spacer().button("cancel", "Cancel"))
                             .build();

        getResponder().getCommunicator().sendBml(300, 200, true, true, bml, 200, 200, 200, title);
    }
}
