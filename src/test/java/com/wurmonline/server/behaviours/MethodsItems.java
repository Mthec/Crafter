package com.wurmonline.server.behaviours;

import com.wurmonline.server.Items;
import com.wurmonline.server.NoSuchItemException;
import com.wurmonline.server.Server;
import com.wurmonline.server.Servers;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.*;
import com.wurmonline.server.players.ItemBonus;
import com.wurmonline.server.players.Titles;
import com.wurmonline.server.skills.NoSuchSkillException;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.Skills;

import java.util.Iterator;

public class MethodsItems {

    public static void setFire(Creature creature, Item target) {
        target.setTemperature((short)100);
    }

    public static int getItemForImprovement(byte material, byte state) {
        int template = -10;
        if (Materials.isWood(material)) {
            switch (state) {
                case 1:
                    template = 8;
                    break;
                case 2:
                    template = 63;
                    break;
                case 3:
                    template = 388;
                    break;
                case 4:
                    template = 313;
                    break;
                default:
                    template = -10;
            }
        } else if (Materials.isMetal(material)) {
            switch (state) {
                case 1:
                    template = 296;
                    break;
                case 2:
                    template = 62;
                    break;
                case 3:
                    template = 128;
                    break;
                case 4:
                    template = 313;
                    break;
                default:
                    template = -10;
            }
        } else if (Materials.isLeather(material)) {
            switch (state) {
                case 1:
                    template = 215;
                    break;
                case 2:
                    template = 390;
                    break;
                case 3:
                    template = 392;
                    break;
                case 4:
                    template = 63;
                    break;
                default:
                    template = -10;
            }
        } else if (Materials.isCloth(material)) {
            switch (state) {
                case 1:
                    template = 215;
                    break;
                case 2:
                    template = 394;
                    break;
                case 3:
                    template = 128;
                    break;
                case 4:
                    template = 215;
                    break;
                default:
                    template = -10;
            }
        } else if (Materials.isStone(material)) {
            switch (state) {
                case 1:
                    template = 97;
                    break;
                case 2:
                    template = 97;
                    break;
                case 3:
                    template = 97;
                    break;
                case 4:
                    template = 97;
                    break;
                default:
                    template = -10;
            }
        } else if (Materials.isClay(material)) {
            switch (state) {
                case 1:
                    template = 14;
                    break;
                case 2:
                    template = 128;
                    break;
                case 3:
                    template = 396;
                    break;
                case 4:
                    template = 397;
                    break;
                default:
                    template = -10;
            }
        }

        return template;
    }

    public static final int getImproveSkill(Item item) {
        int material = item.getMaterial();
        if (material == 0) {
            return -10;
        } else {
            CreationEntry entry = CreationMatrix.getInstance().getCreationEntry(item.getTemplateId());
            if (entry == null) {
                if (item.getTemplateId() != 430 && item.getTemplateId() != 528 && item.getTemplateId() != 638) {
                    return item.getTemplate().isStatue() ? 10074 : -10;
                } else {
                    return 1013;
                }
            } else {
                return item.getTemplateId() != 623 || material != 7 && material != 8 && material != 96 ? entry.getPrimarySkill() : 10043;
            }
        }
    }

    public static final int getImproveTemplateId(Item item) {
        if (item.isNoImprove()) {
            return -10;
        } else {
            byte material = getImproveMaterial(item);
            return material == 0 ? -10 : Materials.getTemplateIdForMaterial(material);
        }
    }

    public static final byte getImproveMaterial(Item item) {
        if (!item.isImproveUsingTypeAsMaterial()) {
            return item.getMaterial();
        } else {
            return item.getTemplate().isCloth() && item.getMaterial() != 69 ? 17 : item.getMaterial();
        }
    }

    static final boolean improveItem(Action act, Creature performer, Item source, Item target, float counter) {
        boolean toReturn = false;
        boolean insta = performer.getPower() >= 5;
        Item parent;
        if (counter == 0.0F || counter == 1.0F || act.justTickedSecond()) {
            if (source.getWurmId() == target.getWurmId()) {
                performer.getCommunicator().sendNormalServerMessage("You cannot improve the " + target.getName() + " using itself as a tool.");
                return true;
            }

            if (!target.isRepairable()) {
                performer.getCommunicator().sendNormalServerMessage("You cannot improve that item.");
                return true;
            }

            if (target.getParentId() != -10L) {
                try {
                    ItemTemplate temp = target.getRealTemplate();
                    if (temp != null && !temp.isVehicle()) {
                        parent = target.getParent();
                        if ((parent.getSizeX() < temp.getSizeX() || parent.getSizeY() < temp.getSizeY() || parent.getSizeZ() <= temp.getSizeZ()) && parent.getTemplateId() != 177 && parent.getTemplateId() != 0) {
                            performer.getCommunicator().sendNormalServerMessage("It's too tight to try and work on the " + target.getName() + " in the " + parent.getName() + ".");
                            return true;
                        }
                    }
                } catch (NoSuchItemException var40) {
                }
            }

            if (target.creationState != 0) {
                performer.getCommunicator().sendNormalServerMessage("You can not improve the " + target.getName() + " by adding more material right now.");
                return true;
            }

            if (!insta) {
                if (target.getDamage() > 0.0F) {
                    performer.getCommunicator().sendNormalServerMessage("Repair the " + target.getName() + " before you try to improve it.");
                    return true;
                }

                if (target.isMetal() && !target.isNoTake() && target.getTemperature() < 3500) {
                    performer.getCommunicator().sendNormalServerMessage("Metal needs to be glowing hot while smithing.");
                    return true;
                }

                if (source.isCombine() && source.isMetal() && source.getTemperature() < 3500) {
                    performer.getCommunicator().sendNormalServerMessage("Metal needs to be glowing hot while smithing.");
                    return true;
                }
            }
        }

        Skills skills = performer.getSkills();
        parent = null;
        int skillNum = getImproveSkill(target);
        if (skillNum != -10 && !target.isNewbieItem() && !target.isChallengeNewbieItem()) {
            int time = 1;
            int templateId = getImproveTemplateId(target);
            if (source.getTemplateId() == templateId) {
                Skill improve;
                try {
                    improve = skills.getSkill(skillNum);
                } catch (NoSuchSkillException var39) {
                    improve = skills.learn(skillNum, 1.0F);
                }

                Skill secondarySkill = null;

                try {
                    secondarySkill = skills.getSkill(source.getPrimarySkill());
                } catch (Exception var38) {
                    try {
                        secondarySkill = skills.learn(source.getPrimarySkill(), 1.0F);
                    } catch (Exception var37) {
                    }
                }

                double power = 0.0D;
                double bonus = 0.0D;
                if (performer.isPriest()) {
                    bonus = -20.0D;
                }

                float runeModifier = 1.0F;
                if (target.getSpellEffects() != null) {
                    runeModifier = target.getSpellEffects().getRuneEffect(RuneUtilities.ModifierEffect.ENCH_IMPPERCENT);
                }

                float imbueEnhancement = 1.0F + source.getSkillSpellImprovement(skillNum) / 100.0F;
                double improveBonus = 0.23047D * (double)imbueEnhancement * (double)runeModifier;
                float improveItemBonus = ItemBonus.getImproveSkillMaxBonus(performer);
                double max = improve.getKnowledge(0.0D) * (double)improveItemBonus + (100.0D - improve.getKnowledge(0.0D) * (double)improveItemBonus) * improveBonus;
                double diff = Math.max(0.0D, max - (double)target.getQualityLevel());
                float skillgainMod = 1.0F;
                if (diff <= 0.0D) {
                    skillgainMod = 2.0F;
                }

                float maxGain;
                if (counter != 1.0F) {
                    time = act.getTimeLeft();
                    maxGain = act.getFailSecond();
                    power = (double)act.getPower();
                    if (counter >= maxGain) {
                        if (secondarySkill != null) {
                            bonus = Math.max(bonus, secondarySkill.skillCheck((double)target.getQualityLevel(), source, bonus, false, performer.isPriest() ? counter / 3.0F : counter / 2.0F));
                        }

                        if (performer.isPriest()) {
                            bonus = Math.min(bonus, 0.0D);
                        }

                        improve.skillCheck((double)target.getQualityLevel(), source, bonus, false, performer.isPriest() ? counter / 2.0F : counter);
                        if (power != 0.0D) {
                            if (!target.isBodyPart()) {
                                if (!target.isLiquid()) {
                                    target.setDamage(target.getDamage() - act.getPower());
                                    performer.getCommunicator().sendNormalServerMessage("You damage the " + target.getName() + " a little.");
                                    Server.getInstance().broadCastAction(performer.getName() + " grunts as " + performer.getHeSheItString() + " damages " + target.getNameWithGenus() + " a little.", performer, 5);
                                } else {
                                    performer.getCommunicator().sendNormalServerMessage("You fail.");
                                    Server.getInstance().broadCastAction(performer.getName() + " grunts as " + performer.getHeSheItString() + " fails.", performer, 5);
                                }
                            }
                        } else {
                            performer.getCommunicator().sendNormalServerMessage("You realize you almost damaged the " + target.getName() + " and stop.");
                            Server.getInstance().broadCastAction(performer.getName() + " stops improving " + target.getNameWithGenus() + ".", performer, 5);
                        }

                        performer.getStatus().modifyStamina(-counter * 1000.0F);
                        return true;
                    }
                } else {
                    if ((source.isCombine() || templateId == 9) && source.getCurrentQualityLevel() <= target.getQualityLevel()) {
                        performer.getCommunicator().sendNormalServerMessage("The " + source.getName() + " is in too poor shape to improve the " + target.getName() + ".");
                        return true;
                    }

                    performer.getCommunicator().sendNormalServerMessage("You start to improve the " + target.getName() + ".");
                    Server.getInstance().broadCastAction(performer.getName() + " starts to improve " + target.getNameWithGenus() + ".", performer, 5);
                    time = Actions.getImproveActionTime(performer, source);
                    performer.sendActionControl(Actions.actionEntrys[192].getVerbString(), true, time);
                    act.setTimeLeft(time);
                    if (performer.getDeity() != null && performer.getDeity().isRepairer() && performer.getFaith() >= 80.0F && performer.getFavor() >= 40.0F) {
                        bonus += 10.0D;
                    }

                    power = improve.skillCheck((double)target.getQualityLevel(), source, bonus, true, 1.0F);
                    double mod = (double)((100.0F - target.getQualityLevel()) / 20.0F / 100.0F * (Server.rand.nextFloat() + Server.rand.nextFloat() + Server.rand.nextFloat() + Server.rand.nextFloat()) / 2.0F);
                    if (power < 0.0D) {
                        act.setFailSecond((float)((int)Math.max(20.0F, (float)time * Server.rand.nextFloat())));
                        act.setPower((float)(-mod * Math.max(1.0D, diff)));
                    } else {
                        if (diff <= 0.0D) {
                            mod *= 0.009999999776482582D;
                        }

                        double regain = 1.0D;
                        if (target.getQualityLevel() < target.getOriginalQualityLevel()) {
                            regain = 2.0D;
                        }

                        diff *= regain;
                        int tid = target.getTemplateId();
                        if (target.isArmour() || target.isCreatureWearableOnly() || target.isWeapon() || target.isShield() || tid == 455 || tid == 454 || tid == 456 || tid == 453 || tid == 451 || tid == 452) {
                            mod *= 2.0D;
                        }

                        if (tid == 455 || tid == 454 || tid == 456 || tid == 453 || tid == 451 || tid == 452) {
                            mod *= 2.0D;
                        }

                        Titles.Title title = performer.getTitle();
                        if (title != null && title.getSkillId() == improve.getNumber() && (target.isArmour() || target.isCreatureWearableOnly())) {
                            mod *= 1.2999999523162842D;
                        }

                        if (target.getRarity() > 0) {
                            mod *= (double)(1.0F + (float)target.getRarity() * 0.1F);
                        }

                        act.setPower((float)(mod * Math.max(1.0D, diff)));
                    }
                }

//                if (act.mayPlaySound()) {
//                    sendImproveSound(performer, source, target, skillNum);
//                }

                if (counter * 10.0F > (float)time || insta) {
                    if (act.getRarity() != 0) {
                        performer.playPersonalSound("sound.fx.drumroll");
                    }

                    maxGain = 1.0F;
                    byte rarity = target.getRarity();
                    float rarityChance = 0.2F;
                    if (target.getSpellEffects() != null) {
                        rarityChance *= target.getSpellEffects().getRuneEffect(RuneUtilities.ModifierEffect.ENCH_RARITYIMP);
                    }

                    if (act.getRarity() > rarity && Server.rand.nextFloat() <= rarityChance) {
                        rarity = act.getRarity();
                    }

                    float switchImproveChance = 1.0F;
                    if (!source.isCombine() && source.getTemplateId() != 9 && source.getTemplateId() != 72 && !source.isDragonArmour()) {
                        if (!source.isBodyPart() && !source.isLiquid()) {
                            source.setDamage(source.getDamage() + 5.0E-4F * source.getDamageModifier());
                        }
                    } else {
                        float mod = 0.05F;
                        if (Servers.localServer.EPIC && source.isDragonArmour()) {
                            mod = 0.01F;
                        }

                        int usedWeight = (int)Math.min(500.0F, Math.max(1.0F, (float)target.getWeightGrams() * mod));
                        if (source.getWeightGrams() < Math.min(source.getTemplate().getWeightGrams(), usedWeight)) {
                            maxGain = Math.min(1.0F, (float)source.getWeightGrams() / (float)usedWeight);
                            switchImproveChance = (float)source.getWeightGrams() / (float)usedWeight;
                        }

                        source.setWeight(source.getWeightGrams() - usedWeight, true);
                        if (source.deleted && source.getRarity() > rarity && Server.rand.nextInt(100) == 0) {
                            rarity = source.getRarity();
                        }
                    }

                    if (secondarySkill != null) {
                        bonus = Math.max(bonus, secondarySkill.skillCheck((double)target.getQualityLevel(), source, bonus, false, skillgainMod * (performer.isPriest() ? counter / 3.0F : counter / 2.0F)));
                    }

                    if (performer.isPriest()) {
                        bonus = Math.min(bonus, 0.0D);
                    }

                    improve.skillCheck((double)target.getQualityLevel(), source, bonus, false, skillgainMod * (performer.isPriest() ? counter / 2.0F : counter));
                    power = (double)act.getPower();
                    if (power > 0.0D) {
                        performer.getCommunicator().sendNormalServerMessage("You improve the " + target.getName() + " a bit.");
                        if (insta) {
                            performer.getCommunicator().sendNormalServerMessage("before: " + target.getQualityLevel() + " now: " + ((double)target.getQualityLevel() + power) + " power=" + power);
                        }

                        if (Servers.isThisATestServer()) {
                            performer.getCommunicator().sendNormalServerMessage("switchImproveChance = " + switchImproveChance);
                        }

                        Server.getInstance().broadCastAction(performer.getName() + " improves " + target.getNameWithGenus() + " a bit.", performer, 5);
                        byte newState = 0;
                        if (switchImproveChance >= Server.rand.nextFloat()) {
                            newState = (byte)Server.rand.nextInt(5);
                        }

                        if (Server.rand.nextFloat() * 20.0F > target.getQualityLevel()) {
                            newState = 0;
                        }

                        Item toRarify = target;
                        if (target.getTemplateId() == 128) {
                            toRarify = source;
                        }

                        if (rarity > toRarify.getRarity()) {
                            toRarify.setRarity(rarity);
                            Iterator var33 = toRarify.getItems().iterator();

                            while(var33.hasNext()) {
                                Item sub = (Item)var33.next();
                                if (sub != null && sub.isComponentItem()) {
                                    sub.setRarity(rarity);
                                }
                            }

                            if (toRarify.getRarity() > 2) {
                                performer.achievement(300);
                            } else if (toRarify.getRarity() == 1) {
                                performer.achievement(301);
                            } else if (toRarify.getRarity() == 2) {
                                performer.achievement(302);
                            }
                        }

                        if (newState != 0) {
                            target.setCreationState(newState);
                            String newString = getNeededCreationAction(getImproveMaterial(target), newState, target);
                            performer.getCommunicator().sendNormalServerMessage(newString);
                        } else if (skillNum != -10) {
                            try {
                                ItemTemplate temp = ItemTemplateFactory.getInstance().getTemplate(templateId);
                                performer.getCommunicator().sendNormalServerMessage("The " + target.getName() + " could be improved with some more " + temp.getName() + ".");
                            } catch (NoSuchTemplateException var36) {
                            }
                        }

                        boolean wasHighest = Items.isHighestQLForTemplate(target.getTemplateId(), target.getQualityLevel(), target.getWurmId(), true);
                        float oldQL = target.getQualityLevel();
                        float modifier = 1.0F;
                        if (target.getSpellEffects() != null) {
                            modifier = target.getSpellEffects().getRuneEffect(RuneUtilities.ModifierEffect.ENCH_IMPQL);
                        }

                        modifier *= target.getMaterialImpBonus();
                        target.setQualityLevel(Math.min(100.0F, (float)((double)target.getQualityLevel() + power * (double)maxGain * (double)modifier)));
                        if (target.getQualityLevel() > target.getOriginalQualityLevel()) {
                            target.setOriginalQualityLevel(target.getQualityLevel());
//                            triggerImproveAchievements(performer, target, improve, wasHighest, oldQL);
                        }
                    } else {
                        if (insta) {
                            performer.getCommunicator().sendNormalServerMessage("Dam before: " + target.getDamage() + " now: " + ((double)target.getDamage() - power) + " power=" + power);
                        }

                        if (!target.isBodyPart()) {
                            if (!target.isLiquid()) {
                                target.setDamage(target.getDamage() - (float)power);
                                performer.getCommunicator().sendNormalServerMessage("You damage the " + target.getName() + " a little.");
                                Server.getInstance().broadCastAction(performer.getName() + " grunts as " + performer.getHeSheItString() + " damages " + target.getNameWithGenus() + " a little.", performer, 5);
                                performer.achievement(206);
                            } else {
                                performer.getCommunicator().sendNormalServerMessage("You fail.");
                                Server.getInstance().broadCastAction(performer.getName() + " grunts as " + performer.getHeSheItString() + " fails.", performer, 5);
                            }
                        }
                    }

                    performer.getStatus().modifyStamina(-counter * 1000.0F);
                    toReturn = true;
                }
            } else {
                performer.getCommunicator().sendNormalServerMessage("You cannot improve the item with that.");
                toReturn = true;
            }

            return toReturn;
        } else {
            performer.getCommunicator().sendNormalServerMessage("You cannot improve that item.");
            return true;
        }
    }

    public static final String getNeededCreationAction(byte material, byte state, Item item) {
        String todo = "";
        String fstring = "improve";
        if (item.getTemplateId() == 386) {
            fstring = "finish";
        }

        if (Materials.isWood(material)) {
            switch(state) {
                case 1:
                    todo = "You notice some notches you must carve away in order to " + fstring + " the " + item.getName() + ".";
                    break;
                case 2:
                    todo = "You must use a mallet on the " + item.getName() + " in order to " + fstring + " it.";
                    break;
                case 3:
                    todo = "You must use a file to smooth out the " + item.getName() + " in order to " + fstring + " it.";
                    break;
                case 4:
                    todo = "You will want to polish the " + item.getName() + " with a pelt to " + fstring + " it.";
                    break;
                default:
                    todo = "";
            }
        } else if (Materials.isMetal(material)) {
            switch(state) {
                case 1:
                    todo = "The " + item.getName() + (item.isNamePlural() ? " need" : " needs") + " to be sharpened with a whetstone.";
                    break;
                case 2:
                    todo = "The " + item.getName() + (item.isNamePlural() ? " have" : " has") + " some dents that must be flattened by a hammer.";
                    break;
                case 3:
                    todo = "You need to temper the " + item.getName() + " by dipping it in water while it's hot.";
                    break;
                case 4:
                    todo = "You need to polish the " + item.getName() + " with a pelt.";
                    break;
                default:
                    todo = "";
            }
        } else if (Materials.isLeather(material)) {
            switch(state) {
                case 1:
                    todo = "The " + item.getName() + (item.isNamePlural() ? " have" : " has") + " some holes and must be tailored with an iron needle to " + fstring + ".";
                    break;
                case 2:
                    todo = "The " + item.getName() + (item.isNamePlural() ? " need" : " needs") + " some holes punched with an awl.";
                    break;
                case 3:
                    todo = "The " + item.getName() + (item.isNamePlural() ? " have" : " has") + " some excess leather that needs to be cut away with a leather knife.";
                    break;
                case 4:
                    todo = "A mallet must be used on the " + item.getName() + " in order to smooth out a quirk.";
                    break;
                default:
                    todo = "";
            }
        } else if (Materials.isCloth(material)) {
            switch(state) {
                case 1:
                    todo = "The " + item.getName() + (item.isNamePlural() ? " have" : " has") + " an open seam that must be backstitched with an iron needle to " + fstring + ".";
                    break;
                case 2:
                    todo = "The " + item.getName() + (item.isNamePlural() ? " have" : " has") + " some excess cloth that needs to be cut away with a scissors.";
                    break;
                case 3:
                    todo = "The " + item.getName() + (item.isNamePlural() ? " have" : " has") + " some stains that must be washed away.";
                    break;
                case 4:
                    todo = "The " + item.getName() + (item.isNamePlural() ? " have" : " has") + " a seam that needs to be hidden by slipstitching with an iron needle.";
                    break;
                default:
                    todo = "";
            }
        } else if (Materials.isStone(material)) {
            switch(state) {
                case 1:
                case 2:
                case 3:
                case 4:
                    todo = "The " + item.getName() + (item.isNamePlural() ? " have" : " has") + " some irregularities that must be removed with a stone chisel.";
                    break;
                default:
                    todo = "";
            }
        } else if (Materials.isClay(material)) {
            switch(state) {
                case 1:
                    todo = "The " + item.getName() + (item.isNamePlural() ? " have" : " has") + " some flaws that must be removed by hand.";
                    break;
                case 2:
                    todo = "The " + item.getName() + " needs water.";
                    break;
                case 3:
                    todo = "The " + item.getName() + (item.isNamePlural() ? " have" : " has") + " some flaws that must be fixed with a clay shaper.";
                    break;
                case 4:
                    todo = "The " + item.getName() + (item.isNamePlural() ? " have" : " has") + " some flaws that must be fixed with a spatula.";
                    break;
                default:
                    todo = "";
            }
        }

        return todo;
    }
}
