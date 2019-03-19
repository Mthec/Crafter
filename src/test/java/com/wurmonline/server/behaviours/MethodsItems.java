package com.wurmonline.server.behaviours;

import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.CreationEntry;
import com.wurmonline.server.items.CreationMatrix;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.Materials;

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
}
