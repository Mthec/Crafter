package mod.wurmunlimited.npcs;

import com.wurmonline.math.TilePos;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.*;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.behaviours.BehaviourDispatcher;
import com.wurmonline.server.behaviours.MethodsItems;
import com.wurmonline.server.behaviours.NoSuchBehaviourException;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.CreatureTemplateIds;
import com.wurmonline.server.creatures.NoSuchCreatureException;
import com.wurmonline.server.creatures.ai.CreatureAIData;
import com.wurmonline.server.creatures.ai.PathTile;
import com.wurmonline.server.economy.Economy;
import com.wurmonline.server.items.*;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.skills.NoSuchSkillException;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillSystem;
import com.wurmonline.server.structures.NoSuchWallException;
import com.wurmonline.server.zones.VolaTile;
import com.wurmonline.server.zones.Zones;
import com.wurmonline.shared.constants.ItemMaterials;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class CrafterAIData extends CreatureAIData {
    private Logger logger = Logger.getLogger(CrafterAIData.class.getName());
    // glowingFromTheHeat is 3500.
    static final short targetTemperature = 4000;
    private WorkBook workbook;
    private Creature crafter;
    private boolean atWorkLocation;
    private PathTile workLocation;
    private final Map<Integer, Item> tools = new HashMap<>();
    // Nearby equipment
    private Item forge;

    public boolean canAction = true;

    @Override
    public void setCreature(@NotNull Creature crafter) {
        super.setCreature(crafter);
        this.crafter = crafter;
        logger = CrafterMod.getCrafterLogger(crafter);
        CrafterAI.allCrafters.add(crafter);
        if (crafter.getInventory().getItemCount() != 0)
            assignItems();
        if (workbook != null && workbook.isForgeAssigned()) {
            float x = workbook.forge.getPosX();
            float y = workbook.forge.getPosY();
            crafter.turnTo((float)(Math.toDegrees(Math.atan2(y - crafter.getPosY(), x - crafter.getPosX())) + 90.0f));
        }

        // Fix for silly error.
        if (crafter.getShop().getMoney() < 0) {
            if (CrafterMod.getPaymentOption() == CrafterMod.PaymentOption.for_owner) {
                long jobPrices = 0;
                if (workbook != null) {
                    for (Job job : workbook) {
                        jobPrices += job.getPriceCharged();
                    }
                }
                crafter.getShop().setMoney((long)(jobPrices * 0.9f));
            } else
                crafter.getShop().setMoney(0);
        }

        // Clear up empty barrels.
        for (Item item : crafter.getInventory().getItemsAsArray()) {
            if (item.getTemplateId() == ItemList.barrelSmall && item.getItemCount() == 0 && !workbook.isJobItem(item)) {
                Items.destroyItem(item.getWurmId());
            }
        }
    }

    private void assignItems() {
        for (Item item : crafter.getInventory().getItems()) {
            if (WorkBook.isWorkBook(item)) {
                try {
                    workbook = new WorkBook(item);
                } catch (WorkBook.InvalidWorkBookInscription e) {
                    e.printStackTrace();
                    workbook = null;
                }
            }
        }
        if (workbook == null) {
            logger.warning("No workbook found on creature (" + crafter.getWurmId() + ")");
            return;
        }
        setForge(workbook.forge);
        if (forge != null)
            Arrays.asList(forge.getItemsAsArray()).forEach(crafter.getInventory()::insertItem);

        for (Item item : crafter.getInventory().getItems()) {
            if (item.getOwnerId() != crafter.getWurmId() && item.getLastOwnerId() != crafter.getWurmId())
                continue;
            if (workbook.isJobItem(item))
                continue;
            if (item.getTemplateId() == ItemList.barrelSmall) {
                item = item.getFirstContainedItem();
                if (item != null && item.getTemplateId() == ItemList.water)
                    tools.put(ItemList.water, item);
                continue;
            }

            tools.put(item.getTemplateId(), item);
        }

        try {
            Item hand = crafter.getBody().getBodyPart(13);
            tools.put(hand.getTemplateId(), hand);
        } catch (NoSpaceException e) {
            logger.warning("Could not find hand item.");
        }
    }

    Item createMissingItem(int templateId) throws NoSuchTemplateException, FailedException {
        float skillLevel = workbook.getSkillCap();
        if (skillLevel > 100)
            skillLevel = 100;
        Item item;

        if (ItemTemplateFactory.getInstance().getTemplate(templateId).isLiquid()) {
            Item barrel = ItemFactory.createItem(ItemList.barrelSmall, skillLevel, "");
            item = ItemFactory.createItem(ItemList.water, 99.0f, "");
            barrel.insertItem(item);
            crafter.getInventory().insertItem(barrel);
        } else {
            item = ItemFactory.createItem(templateId, skillLevel, "");
            crafter.getInventory().insertItem(item);
        }

        // Extra details
        if (templateId == ItemList.pelt) {
            item.setData2(CreatureTemplateIds.RAT_LARGE_CID);
        } else if (templateId == ItemList.log) {
            item.setMaterial(ItemMaterials.MATERIAL_WOOD_BIRCH);
        }

        tools.put(templateId, item);
        return item;
    }

    private void repairTool(Item item, float ql) {
        ql += 10;
        if (ql > 100)
            ql = 100;
        if (item.isBodyPart())
            return;
        if (item.getDamage() != 0)
            item.setDamage(0);
        if (item.getQualityLevel() < ql || item.getQualityLevel() > 100)
            item.setQualityLevel(ql);
        if (item.isLiquid())
            item.setWeight(5000, false);
        else if (item.isCombine() && item.isMetal())
            item.setWeight(1000, false);
        else
            item.setWeight(item.getTemplate().getWeightGrams(), false);
    }

    private void capSkills() {
        double cap = workbook.getSkillCap();
        for (Skill skill : crafter.getSkills().getSkills()) {
            if (skill.getKnowledge() > cap) {
                skill.setKnowledge(cap, false);
            }
        }
    }

    public Item getForge() {
        return forge;
    }

    public void setForge(@Nullable Item item) {
        forge = item;
        workbook.setForge(item);

        if (item == null) {
            CrafterAI.assignedForges.remove(crafter);
            workLocation = null;
            return;
        }

        CrafterAI.assignedForges.put(crafter, item);
        TilePos pos = item.getTilePos();
        int tilePosX = Zones.safeTileX(pos.x);
        int tilePosY = Zones.safeTileY(pos.y);
        int tile;
        Creature c = crafter;
        if (!item.isOnSurface()) {
            tile = Server.caveMesh.getTile(tilePosX, tilePosY);
            if (!Tiles.isSolidCave(Tiles.decodeType(tile)) && (Tiles.decodeHeight(tile) > -c.getHalfHeightDecimeters() || c.isSwimming() || c.isSubmerged())) {
                workLocation = new PathTile(tilePosX, tilePosY, tile, c.isOnSurface(), -1);
            }
        } else {
            tile = Server.surfaceMesh.getTile(tilePosX, tilePosY);
            if (Tiles.decodeHeight(tile) > -c.getHalfHeightDecimeters() || c.isSwimming() || c.isSubmerged()) {
                workLocation = new PathTile(tilePosX, tilePosY, tile, c.isOnSurface(), c.getFloorLevel());
            }
        }
        atWorkLocation = false;

        try {
            for (Creature creature : item.getWatchers()) {
                creature.getCommunicator().sendCloseInventoryWindow(forge.getWurmId());
            }
        } catch (NoSuchCreatureException ignored) {}
    }

    void arrivedAtWorkLocation() {
        atWorkLocation = true;
    }

    @Nullable
    PathTile getWorkLocation() {
        if (!atWorkLocation && workLocation != null) {
            return workLocation;
        }
        return null;
    }

    public WorkBook getWorkBook() {
        return workbook;
    }

    String getStatusFor(Player player) {
        StringBuilder sb = new StringBuilder();
        List<Job> jobs = workbook.getJobsFor(player);
        if (!jobs.isEmpty()) {
            int ready = (int)jobs.stream().filter(Job::isDone).count();
            int todo = jobs.size() - ready;
            sb.append("I am currently currently working on ").append(todo).append(todo == 1 ? " item" : " items").append(" for you.");
            if (ready > 0) {
                String are;
                String items;
                if (ready == 1) {
                    are = "is";
                    items = "item";
                } else {
                    are = "are";
                    items = "items";
                }
                sb.append("  There ").append(are).append(" ").append(ready).append(" ").append(items).append(" ready for collection.");
            }
            return sb.toString();
        }
        return null;
    }

    void sendNextAction() {
        if (!canAction)
            return;
        if (workbook == null) {
            // Attempt to find WorkBook, in case it's just an early call.
            for (Item item : crafter.getInventory().getItems()) {
                if (WorkBook.isWorkBook(item)) {
                    try {
                        workbook = new WorkBook(item);
                    } catch (WorkBook.InvalidWorkBookInscription e) {
                        logger.warning("WorkBook (" + item.getWurmId() + ") had an invalid inscription.  No longer sending actions.");
                        e.printStackTrace();
                        canAction = false;
                        return;
                    }
                }
            }

            if (workbook == null) {
                try {
                    logger.info("WorkBook not found on Crafter, creating new.");
                    WorkBook.createNewWorkBook(new CrafterType(CrafterType.allSkills), 30f);
                } catch (NoSuchTemplateException | FailedException e) {
                    logger.warning("WorkBook not found on crafter (" + crafter.getWurmId() + "), and could not create new.  No longer sending actions.");
                    canAction = false;
                    e.printStackTrace();
                    return;
                }
            }
        }
        if (workbook.todo() == 0 && workbook.donationsTodo() == 0) {
            if (workbook.isForgeAssigned() && forge.isOnFire()) {
                forge.setTemperature((short)0);
            }
            return;
        }

        for (Job job : workbook) {
            if (!job.isDone()) {
                Item item = job.item;
                if (!item.isRepairable()) {
                    logger.info(item.getName() + " was not supposed to be accepted.  Returning and refunding.");
                    returnErrorJob(job);
                    continue;
                }

                if (job.isDonation()) {
                    if (!workbook.getCrafterType().hasSkillToImprove(item)) {
                        continue;
                    }

                    int skillNum = MethodsItems.getImproveSkill(item);
                    float currentSkill;
                    try {
                        currentSkill = (float)crafter.getSkills().getSkill(skillNum).getKnowledge();
                    } catch (NoSuchSkillException e) {
                        logger.warning(crafter.getName() + "(" + crafter.getWurmId() + ") was missing " + SkillSystem.getNameFor(skillNum) + " skill.");
                        continue;
                    }

                    if (CrafterMod.destroyDonationItem(currentSkill, job.item.getQualityLevel())) {
                        workbook.removeJob(job.item);
                        Items.destroyItem(job.item.getWurmId());
                        continue;
                    } else if (item.getQualityLevel() >= workbook.getSkillCap()) {
                        continue;
                    }
                } else {
                    if (item.getQualityLevel() >= job.targetQL || item.getQualityLevel() >= 99.999999f) {
                        if (forge != null && forge.getItems().contains(item))
                            crafter.getInventory().insertItem(item);
                        workbook.setDone(job, crafter);
                        logger.info(item.getName() + " is done.");
                        // In case a Job is removed at the wrong time.
                        return;
                    }
                }

                if (item.getDamage() > 0.0f) {
                    try {
                        BehaviourDispatcher.action(crafter, crafter.getCommunicator(), -10, item.getWurmId(), Actions.REPAIR);
                        logger.info("Repairing " + item.getName());
                    } catch (NoSuchPlayerException | NoSuchCreatureException | NoSuchItemException | NoSuchBehaviourException | NoSuchWallException | FailedException e) {
                        logger.warning(crafter.getName() + " (" + crafter.getWurmId() + ") could not repair " + item.getName() + " (" + item.getWurmId() + ").  Reason follows:");
                        e.printStackTrace();
                        returnErrorJob(job);
                    }
                    return;
                } else if (item.isMetal()) {
                    // TODO - Baking pottery items?
                    if (forge == null)
                        continue;

                    if (!forge.isOnFire()) {
                        try {
                            Method setFire = MethodsItems.class.getDeclaredMethod("setFire", Creature.class, Item.class);
                            setFire.setAccessible(true);
                            setFire.invoke(null, crafter, forge);
                            logger.info("Lighting forge");
                        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                            logger.warning("Could not light forge.  Reason follows:");
                            e.printStackTrace();
                        }
                    }
                    forge.setTemperature((short)10000);

                    // Clear out ash for Ash produce mod.
                    for (Item it : forge.getAllItems(true)) {
                        if (it.getTemplateId() == ItemList.ash) {
                            Items.destroyItem(it.getWurmId());
                        }
                    }

                    int lumpId = MethodsItems.getImproveTemplateId(item);
                    Item lump = tools.get(lumpId);
                    if (lump == null) {
                        try {
                            lump = createMissingItem(lumpId);
                        } catch (NoSuchTemplateException | FailedException e) {
                            logger.warning("Could not create required improving item (template id - " + lumpId + ").  Reason follows:");
                            e.printStackTrace();
                            continue;
                        }
                    }
                    if (lump != null && !forge.getItems().contains(lump)) {
                        forge.insertItem(lump);
                        // Bug where item is put on surface when inserted.
                        lump.setParentId(forge.getWurmId(), forge.isOnSurface());
                        logger.info("Put the " + lump.getName() + " in the forge");
                    }
                    if (!forge.getItems().contains(item)) {
                        forge.insertItem(item);
                        // Bug where item is put on surface when inserted.
                        item.setParentId(forge.getWurmId(), forge.isOnSurface());
                        logger.info("Put the " + item.getName() + " in the forge");
                    }

                    if (item.getTemperature() < CrafterAIData.targetTemperature) {
                        logger.info("Waiting for item to heat up.");
                        continue;
                    }
                }

                int toolTemplateId = MethodsItems.getItemForImprovement(item.getMaterial(), item.creationState);
                if (toolTemplateId == -10) {
                    toolTemplateId = MethodsItems.getImproveTemplateId(item);
                }

                Item tool = tools.get(toolTemplateId);
                if (tool == null) {
                    try {
                        if (toolTemplateId == ItemList.bodyHand)
                            throw new NoSpaceException("Hand was null.");
                        else
                            tool = createMissingItem(toolTemplateId);
                    } catch (NoSuchTemplateException | FailedException e) {
                        logger.warning("Could not create required improving item (template id - " + toolTemplateId + ").  Reason follows:");
                        e.printStackTrace();
                        continue;
                    } catch (NoSpaceException e) {
                        logger.warning("Could not get hand item for improving.  Reason follows:");
                        e.printStackTrace();
                        continue;
                    }
                }

                if (tool.isCombine() && tool.isMetal()) {
                    if (tool.getTemperature() >= CrafterAIData.targetTemperature) {
                        crafter.getInventory().insertItem(tool);
                    } else {
                        logger.info("Waiting for lump to heat up.");
                        continue;
                    }
                }

                repairTool(tool, job.item.getCurrentQualityLevel());
                capSkills();

                try {
                    BehaviourDispatcher.action(crafter, crafter.getCommunicator(), tool.getWurmId(), item.getWurmId(), Actions.IMPROVE);
                    logger.info("Improving " + item.getName() + " with " + tool.getName());
                } catch (NoSuchPlayerException | NoSuchCreatureException | NoSuchItemException | NoSuchBehaviourException | NoSuchWallException | FailedException e) {
                    logger.warning(crafter.getName() + " (" + crafter.getWurmId() + ") could not improve " + item.getName() + " (" + item.getWurmId() + ") with " + tool.getName() + " (" + tool.getWurmId() + ").  Reason follows:");
                    e.printStackTrace();
                    returnErrorJob(job);
                    continue;
                }
                return;
            }
        }
    }

    private void returnErrorJob(Job job) {
        Item item = job.item;
        if (forge != null && forge.getItems().contains(item))
            crafter.getInventory().insertItem(item);
        job.mailToCustomer();
        try {
            job.refundCustomer();
        } catch (NoSuchTemplateException | FailedException e) {
            logger.warning("Could not create refund package while dismissing Crafter, customers were not compensated.");
            e.printStackTrace();
        }
        workbook.removeJob(item);
    }

    public static Creature createNewCrafter(Creature owner, String name, byte sex, CrafterType crafterType, float skillCap, float priceModifier) throws Exception {
        skillCap = Math.min(skillCap, CrafterMod.getSkillCap());
        VolaTile tile = owner.getCurrentTile();
        Creature crafter = Creature.doNew(CrafterTemplate.getTemplateId(), (float)(tile.getTileX() << 2) + 2.0F, (float)(tile.getTileY() << 2) + 2.0F, 180.0F, owner.getLayer(), name, sex, owner.getKingdomId());

        // Cleaning up.  Hook may not be run after other mods have adjusted the createShop method.
        for (Item item : crafter.getInventory().getItemsAsArray()) {
            Items.destroyItem(item.getWurmId());
        }

        crafter.getInventory().insertItem(WorkBook.createNewWorkBook(crafterType, skillCap).workBookItem);
        Economy.getEconomy().createShop(crafter.getWurmId(), owner.getWurmId()).setPriceModifier(priceModifier);
        for (Skill skill : crafterType.getSkillsFor(crafter)) {
            skill.setKnowledge(CrafterMod.getStartingSkillLevel(), false);
            // Parent skills.
            for (int skillId : skill.getDependencies()) {
                crafter.getSkills().getSkillOrLearn(skillId);
            }
        }
        crafter.getCreatureAIData().setCreature(crafter);
        return crafter;
    }
}
