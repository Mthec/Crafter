package com.wurmonline.server.questions;

import com.wurmonline.server.Items;
import com.wurmonline.server.NoSuchItemException;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.behaviours.Methods;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.questions.skills.MultipleSkillsBML;
import com.wurmonline.server.questions.skills.SingleSkillBML;
import com.wurmonline.server.questions.skills.SkillsBML;
import com.wurmonline.server.structures.Structure;
import com.wurmonline.server.villages.Village;
import com.wurmonline.server.villages.VillageRole;
import com.wurmonline.server.zones.VolaTile;
import com.wurmonline.shared.util.StringUtilities;
import mod.wurmunlimited.bml.BML;
import mod.wurmunlimited.bml.BMLBuilder;
import mod.wurmunlimited.npcs.CrafterAIData;
import mod.wurmunlimited.npcs.CrafterMod;
import mod.wurmunlimited.npcs.CrafterType;

import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

public class CrafterHireQuestion extends CrafterQuestionExtension {
    static final ModelOption[] modelOptions = new ModelOption[] { ModelOption.HUMAN, ModelOption.TRADER, ModelOption.CUSTOM };
    private final SkillsBML skillsBML;

    public CrafterHireQuestion(Creature aResponder, long contract) {
        super(aResponder, "Hire Crafter", "", QuestionTypes.MANAGETRADER, contract);
        skillsBML = CrafterMod.useSingleSkill() ? new SingleSkillBML() : new MultipleSkillsBML();
    }

    @Override
    public void answer(Properties answers) {
        setAnswer(answers);
        Creature responder = getResponder();
        Item contract;
        try {
            contract = Items.getItem(target);
        } catch (NoSuchItemException e) {
            responder.getCommunicator().sendNormalServerMessage("You feel like the contract is slipping out of your hands, you jolt and try to recompose yourself.");
            e.printStackTrace();
            return;
        }

        byte sex = 0;
        if (getStringProp("gender").equals("female"))
            sex = 1;

        String name = StringUtilities.raiseFirstLetter(getStringProp("name"));
        if (name.isEmpty() || name.length() > CrafterMod.maxNameLength || QuestionParser.containsIllegalCharacters(name)) {
            if (sex == 0) {
                name = QuestionParser.generateGuardMaleName();
                responder.getCommunicator().sendSafeServerMessage("The crafter didn't like the name, so he chose a new one.");
            } else {
                name = QuestionParser.generateGuardFemaleName();
                responder.getCommunicator().sendSafeServerMessage("The crafter didn't like the name, so she chose a new one.");
            }
        }
        name = getPrefix() + name;

        float priceModifier = getFloatOrDefault("price_modifier", 1.0f);
        if (priceModifier < CrafterMod.getMinimumPriceModifier()) {
            responder.getCommunicator().sendSafeServerMessage("Price modifier was too low, setting minimum value.");
            priceModifier = CrafterMod.getMinimumPriceModifier();
        }

        Set<Integer> skills = new HashSet<>();
        if (wasSelected("all_metal"))
            Collections.addAll(skills, CrafterType.allMetal);
        if (wasSelected("all_wood"))
            Collections.addAll(skills, CrafterType.allWood);
        if (wasSelected("all_armour"))
            Collections.addAll(skills, CrafterType.allArmour);

        skills.addAll(skillsBML.getSkills(answers));

        if (skills.size() == 0) {
            responder.getCommunicator().sendNormalServerMessage("You must select at least one crafter type in order to hire.");
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
            if (getStringProp("skill_cap").isEmpty()) {
                skillCap = CrafterMod.getSkillCap();
            } else {
                responder.getCommunicator().sendNormalServerMessage("Skill cap value was invalid.");
                return;
            }
        }
        if (skillCap >= CrafterMod.getMaxItemQL()) {
            responder.getCommunicator().sendNormalServerMessage("Note: Skill cap is higher than the maximum item ql for crafters on this server.");
        }

        if (locationIsValid(responder)) {
            try {
                Creature crafter = CrafterAIData.createNewCrafter(responder,
                        name, sex, crafterType, skillCap, priceModifier);
                contract.setData(crafter.getWurmId());
                Village v = responder.getCitizenVillage();
                if (v != null) {
                    v.addCitizen(crafter, v.getRoleForStatus(VillageRole.ROLE_CITIZEN));
                }
                logger.info(responder.getName() + " created a crafter: " + crafter);

                if (wasSelected("customise")) {
                    new CreatureCustomiserQuestion(responder, crafter, CrafterMod.mod.faceSetter, CrafterMod.mod.modelSetter, modelOptions).sendQuestion();
                }
            } catch (Exception e) {
                responder.getCommunicator().sendAlertServerMessage("An error occurred in the rifts of the void. The crafter was not created.");
                e.printStackTrace();
            }
        }
    }

    private boolean locationIsValid(Creature responder) {
        VolaTile tile = responder.getCurrentTile();
        if (tile != null) {
            if (!Methods.isActionAllowed(responder, Actions.MANAGE_TRADERS)) {
                return false;
            }
            for (Creature creature : tile.getCreatures()) {
                if (!creature.isPlayer()) {
                    responder.getCommunicator().sendNormalServerMessage("The crafter will only set up shop where no other creatures except you are standing.");
                    return false;
                }
            }

            Structure struct = tile.getStructure();
            if (struct != null && !struct.mayPlaceMerchants(responder)) {
                responder.getCommunicator().sendNormalServerMessage("You do not have permission to place a crafter in this building.");
            } else {
                return true;
            }
        }
        return false;
    }

    @Override
    public void sendQuestion() {
        Creature responder = getResponder();

        BML builder = new BMLBuilder(id)
                .text("Hire Crafter:").bold()
                .text("Use this contract to hire a Crafter.")
                .text("The crafter will improve items for customers for a price.")
                .text("If you select any crafter types that work with metal you will need to Assign them a forge after summoning with this contract.")
                .text("Set the price modifier to change how much the crafter charges.")
                .text("Prices scale up with item quality level.")
                .If(CrafterMod.getPaymentOption() == CrafterMod.PaymentOption.for_owner,
                        b -> b.text("Under current laws the crafter will charge 10% for each order, you can retrieve the rest by visiting the crafter."))
                .If(CrafterMod.getPaymentOption() == CrafterMod.PaymentOption.tax_and_upkeep,
                        b -> b.text("Under current laws the crafter will contribute" + CrafterMod.getUpkeepPercentage() + "% of each order to village upkeep.  The rest will go to the King."))
                .If(CrafterMod.getPaymentOption() == CrafterMod.PaymentOption.all_tax,
                        b -> b.text("Under current laws all proceeds will go to the King."))
                .newLine();

        String bml = skillsBML.addBML(builder, new CrafterType(), CrafterMod.getSkillCap())
                .If(CrafterMod.canUsePriceModifier(), b -> b.harray(b2 -> b2.label("Price Modifier: ").entry("price_modifier", "1.0", 4)))
                .newLine()
                .harray(b -> b.label("Crafter name:").entry("name", CrafterMod.maxNameLength))
                .text("Leave blank for a random name.").italic()
                .text("Gender:")
                .radio("gender", "male", "Male", responder.getSex() == (byte)0)
                .radio("gender", "female", "Female", responder.getSex() == (byte)1)
                .newLine()
                .checkbox("customise" ,"Open appearance customiser when done?", true)
                .newLine()
                .harray(b -> b.button("Send"))
                .build();

        getResponder().getCommunicator().sendBml(500, 400, true, true, bml, 200, 200, 200, title);
    }
}
