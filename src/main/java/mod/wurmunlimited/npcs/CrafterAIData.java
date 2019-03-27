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
import com.wurmonline.server.skills.Skill;
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
    }

    Item createMissingItem(int templateId) throws NoSuchTemplateException, FailedException {
        float skillLevel = workbook.getSkillCap();
        Item item;

        if (ItemTemplateFactory.getInstance().getTemplate(templateId).isLiquid()) {
            Item barrel = ItemFactory.createItem(ItemList.barrelSmall, skillLevel + 10, "");
            item = ItemFactory.createItem(ItemList.water, 99.0f, "");
            barrel.insertItem(item);
            crafter.getInventory().insertItem(barrel);
        } else {
            item = ItemFactory.createItem(templateId, skillLevel + 10, "");
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

    private void repairTool(Item item) {
        if (item.getDamage() != 0)
            item.setDamage(0);
        if (item.getQualityLevel() < workbook.getSkillCap())
            item.setQualityLevel(workbook.getSkillCap());
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
        if (workbook.todo() == 0)
            return;

        for (Job job : workbook) {
            if (!job.isDone()) {
                Item item = job.item;
                if (!item.isRepairable()) {
                    logger.info(item.getName() + " was not supposed to be accepted.  Returning and refunding.");
                    returnErrorJob(job);
                    continue;
                }

                if (item.getQualityLevel() >= job.targetQL || (job.isDonation() && (!workbook.getCrafterType().hasSkillToImprove(item) || item.getQualityLevel() >= workbook.getSkillCap()))) {
                    workbook.setDone(job);
                    if (forge != null && forge.getItems().contains(item))
                        crafter.getInventory().insertItem(item);
                    logger.info(item.getName() + " is done.");
                    continue;
                } else if (item.getDamage() > 0.0f) {
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

                    Item lump = tools.get(MethodsItems.getImproveTemplateId(item));
                    if (lump != null && !forge.getItems().contains(lump)) {
                        forge.insertItem(lump);
                        logger.info("Put the " + lump.getName() + " in the forge");
                    }
                    if (!forge.getItems().contains(item)) {
                        forge.insertItem(item);
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
                        tool = createMissingItem(toolTemplateId);
                    } catch (NoSuchTemplateException | FailedException e) {
                        logger.warning("Could not create required improving item (template id - " + toolTemplateId + ").  Reason follows:");
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

                repairTool(tool);
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
        workbook.setDone(job);
        workbook.removeJob(item);
    }

    public static Creature createNewCrafter(Creature owner, String name, byte sex, CrafterType crafterType, float skillCap, float priceModifier) throws Exception {
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
