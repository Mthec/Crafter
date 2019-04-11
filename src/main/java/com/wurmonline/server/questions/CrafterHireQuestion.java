package com.wurmonline.server.questions;

import com.wurmonline.server.Items;
import com.wurmonline.server.NoSuchItemException;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.behaviours.Methods;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.skills.SkillSystem;
import com.wurmonline.server.structures.Structure;
import com.wurmonline.server.villages.Village;
import com.wurmonline.server.villages.VillageRole;
import com.wurmonline.server.zones.VolaTile;
import com.wurmonline.shared.util.StringUtilities;
import mod.wurmunlimited.bml.BMLBuilder;
import mod.wurmunlimited.npcs.CrafterAIData;
import mod.wurmunlimited.npcs.CrafterMod;
import mod.wurmunlimited.npcs.CrafterType;

import java.util.*;
import java.util.stream.Collectors;

public class CrafterHireQuestion extends CrafterQuestionExtension {
    private static final String[] allCrafterTypes = new String[] {
            String.valueOf(SkillList.SMITHING_BLACKSMITHING),
            String.valueOf(SkillList.GROUP_SMITHING_WEAPONSMITHING),
            String.valueOf(SkillList.SMITHING_GOLDSMITHING),
            String.valueOf(SkillList.SMITHING_ARMOUR_CHAIN),
            String.valueOf(SkillList.SMITHING_ARMOUR_PLATE),
            String.valueOf(SkillList.CARPENTRY),
            String.valueOf(SkillList.CARPENTRY_FINE),
            String.valueOf(SkillList.GROUP_FLETCHING),
            String.valueOf(SkillList.GROUP_BOWYERY),
            String.valueOf(SkillList.LEATHERWORKING),
            String.valueOf(SkillList.CLOTHTAILORING),
            String.valueOf(SkillList.STONECUTTING),
            String.valueOf(SkillList.SMITHING_SHIELDS),
            String.valueOf(SkillList.POTTERY)
    };

    public CrafterHireQuestion(Creature aResponder, long contract) {
        super(aResponder, "Hire Crafter", "", QuestionTypes.MANAGETRADER, contract);
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
        if (name.isEmpty() || name.length() > 20 || QuestionParser.containsIllegalCharacters(name)) {
            if (sex == 0) {
                name = QuestionParser.generateGuardMaleName();
                responder.getCommunicator().sendSafeServerMessage("The crafter didn't like the name, so he chose a new one.");
            } else {
                name = QuestionParser.generateGuardFemaleName();
                responder.getCommunicator().sendSafeServerMessage("The crafter didn't like the name, so she chose a new one.");
            }
        }

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

        for (String val : allCrafterTypes) {
            try {
                if (wasSelected(val))
                    skills.add(Integer.parseInt(val));
            } catch (NumberFormatException e) {
                logger.info("Invalid crafter type (" + answers.getProperty(val) + ") received when hiring.  Ignoring.");
            }
        }
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
            responder.getCommunicator().sendNormalServerMessage("Skill cap value was invalid.");
            return;
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

        String bml = new BMLBuilder(id)
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
                .newLine()
                .text("General crafter options will override specialisations if selected.  Other specialisations are not affected.").italic()
                .text("General crafters:")
                .harray(b -> b
                             .checkbox("All Metal", "all_metal")
                             .checkbox("All Wood", "all_wood")
                             .checkbox("All Armour", "all_armour"))
                .text("Specialists:")
                .table(new String[] { "Metal", "Wood", "Misc."},
                        Arrays.asList(
                            new int[] { SkillList.SMITHING_BLACKSMITHING, SkillList.CARPENTRY, SkillList.LEATHERWORKING },
                            new int[] { SkillList.GROUP_SMITHING_WEAPONSMITHING, SkillList.CARPENTRY_FINE, SkillList.CLOTHTAILORING },
                            new int[] { SkillList.SMITHING_GOLDSMITHING, SkillList.GROUP_FLETCHING, SkillList.STONECUTTING },
                            new int[] { SkillList.SMITHING_ARMOUR_CHAIN, SkillList.GROUP_BOWYERY, SkillList.POTTERY },
                            new int[] { SkillList.SMITHING_ARMOUR_PLATE, 0, 0 },
                            new int[] { SkillList.SMITHING_SHIELDS, 0, 0 }

                        ),
                        (row, bml1) -> bml1.forEach(Arrays.stream(row).boxed().collect(Collectors.toList()), (skill, b) -> {
                            if (skill != 0)
                                return b.checkbox(SkillSystem.getNameFor(skill), Integer.toString(skill));
                            return b.label(" ");
                        }))
                .newLine()
                .If(CrafterMod.canLearn(),
                        b -> b.harray(b2 -> b2.label("Skill Cap: ").entry("skill_cap", Float.toString(CrafterMod.getSkillCap()), 3).text("Max: " + CrafterMod.getSkillCap()).italic()),
                        b -> b.harray(b2 -> b2.label("Skill Cap: ").text(Float.toString(CrafterMod.getSkillCap())))
                        )
                .If(CrafterMod.canUsePriceModifier(), b -> b.harray(b2 -> b2.label("Price Modifier: ").entry("price_modifier", "1.0", 4)))
                .newLine()
                .harray(b -> b.label("Crafter name:").entry("name", 20))
                .text("Leave blank for a random name.").italic()
                .text("Gender:")
                .radio("gender", "male", "Male", responder.getSex() == (byte)0)
                .radio("gender", "female", "Female", responder.getSex() == (byte)1)
                .harray(b -> b.button("Send"))
                .build();

        getResponder().getCommunicator().sendBml(500, 400, true, true, bml, 200, 200, 200, title);
    }
}
