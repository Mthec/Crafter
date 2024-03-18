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
import com.wurmonline.server.structures.NoSuchWallException;
import com.wurmonline.server.zones.VolaTile;
import com.wurmonline.server.zones.Zones;
import com.wurmonline.shared.constants.ItemMaterials;
import mod.wurmunlimited.npcs.db.CrafterDatabase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Logger;

public class CrafterAIData extends CreatureAIData {
    private Logger logger = Logger.getLogger(CrafterAIData.class.getName());
    // glowingFromTheHeat is 3500.
    static final short targetTemperature = 4000;
    private WorkBook workbook;
    private Creature crafter;
    private boolean atWorkLocation;
    private PathTile workLocation;
    public final Tools tools = new Tools();
    // Nearby equipment
    private Item forge;

    public boolean canAction = true;

    public class Tools {
        private final Map<Integer, Item> tools = new HashMap<>();
        private final Map<Integer, List<Item>> givenTools = new HashMap<>();

        private Tools() {}

        public void addGivenTool(Item item) {
            List<Item> t = givenTools.computeIfAbsent(item.getTemplateId(), ArrayList::new);
            if (t.contains(item)) {
                logger.warning("Donated tools list already contained that tool.");
                return;
            }
            t.add(item);
            try {
                CrafterDatabase.addGivenToolFor(crafter, item);
            } catch (SQLException e) {
                logger.warning("Could not add given tool " + item.getWurmId() + " to the database.  This tool won't work following next server restart.");
                e.printStackTrace();
            }
        }

        public void removeGivenTool(Item item) {
            try {
                List<Item> items = givenTools.get(item.getTemplateId());
                if (items != null) {
                    items.remove(item);
                }
                CrafterDatabase.removeGivenToolFor(crafter, item);
            } catch (SQLException e) {
                logger.warning("Could not remove given tool " + item.getWurmId() + " from the database.");
                e.printStackTrace();
            }
        }

        @Nullable
        Item getPreferredTool(int templateId, float targetQL) {
            List<Item> options = givenTools.get(templateId);
            if (options == null || options.isEmpty()) {
                Item tool = tools.get(templateId);
                if (tool != null) {
                    repairTool(tool, targetQL);
                }
                return tool;
            }

            // Donated tool > targetQL
            // Donated tool < targetQL + 20
            Item tool = null;
            if (options.size() == 1) {
                Item maybeTool = options.get(0);
                float ql = maybeTool.getCurrentQualityLevel();
                if (ql > targetQL && ql <= targetQL + 20) {
                    tool = maybeTool;
                }
            } else {
                for (Item item : options) {
                    float ql = item.getCurrentQualityLevel();
                    if ((tool == null  || ql > tool.getCurrentQualityLevel()) && ql > targetQL && ql <= targetQL + 20) {
                        tool = item;
                    }
                }
            }

            if (tool == null) {
                tool = tools.get(templateId);
                if (tool != null) {
                    repairTool(tool, targetQL);
                }
                return tool;
            }

            if (tool.getDamage() > 0f) {
                tool.repair(crafter, (short)0, tool.getDamage());
            }

            return tool;
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

        public boolean isTool(Item item) {
            if (tools.containsValue(item)) {
                return true;
            }
            List<Item> t = givenTools.get(item.getTemplateId());
            return t != null && t.contains(item);
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

            Set<Long> givenToolIds;
            try {
                givenToolIds = CrafterDatabase.getGivenToolsFor(crafter);
            } catch (SQLException e) {
                logger.warning("Failed to load tools give to the crafter from database.");
                e.printStackTrace();
                givenToolIds = Collections.emptySet();
            }

            for (Item item : crafter.getInventory().getItems()) {
                if (item.getOwnerId() != crafter.getWurmId() && item.getLastOwnerId() != crafter.getWurmId())
                    continue;
                if (item.isArmour())
                    continue;
                if (workbook.isJobItem(item))
                    continue;
                if (item.getTemplateId() == ItemList.barrelSmall) {
                    item = item.getFirstContainedItem();
                    if (item != null && item.getTemplateId() == ItemList.water)
                        tools.put(ItemList.water, item);
                    continue;
                }

                if (givenToolIds.contains(item.getWurmId())) {
                    givenTools.computeIfAbsent(item.getTemplateId(), ArrayList::new).add(item);
                } else {
                    tools.put(item.getTemplateId(), item);
                }
            }

            try {
                Item hand = crafter.getBody().getBodyPart(13);
                tools.put(hand.getTemplateId(), hand);
            } catch (NoSpaceException e) {
                logger.warning("Could not find hand item.");
            }
        }

        public Iterable<Item> getGivenTools() {
            return new Iterable<Item>() {
                @NotNull
                @Override
                public Iterator<Item> iterator() {
                    return new Iterator<Item>() {
                        private final Iterator<List<Item>> tools = givenTools.values().iterator();
                        private Iterator<Item> current = null;

                        @Override
                        public boolean hasNext() {
                            if (current == null || !current.hasNext()) {
                                return moveOn();
                            }

                            return false;
                        }

                        @Override
                        public Item next() {
                            if (current == null || !current.hasNext()) {
                                moveOn();
                            }

                            return current.next();
                        }

                        private boolean moveOn() {
                            if (!tools.hasNext()) {
                                return false;
                            }

                            current = tools.next().iterator();

                            while (!current.hasNext()) {
                                if (!tools.hasNext()) {
                                    return false;
                                }

                                current = tools.next().iterator();
                            }

                            return true;
                        }
                    };
                }
            };
        }
    }

    public void log(String message) {
        logger.info(message);
    }

    @Override
    public void setCreature(@NotNull Creature crafter) {
        super.setCreature(crafter);
        this.crafter = crafter;
        logger = CrafterMod.getCrafterLogger(crafter);
        CrafterAI.allCrafters.add(crafter);
        if (crafter.getInventory().getItemCount() != 0)
            tools.assignItems();
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

    private void capSkills() {
        double cap = workbook.getSkillCap();
        double starting = CrafterMod.getStartingSkillLevel();
        for (Skill skill : crafter.getSkills().getSkills()) {
            double knowledge = skill.getKnowledge();
            if (knowledge > cap) {
                skill.setKnowledge(cap, false);
            } else if (knowledge < starting) {
                skill.setKnowledge(starting, false);
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
                    float currentSkill = (float)crafter.getSkills().getSkillOrLearn(skillNum).getKnowledge();

                    if (CrafterMod.destroyDonationItem(currentSkill, job.item.getQualityLevel())) {
                        workbook.removeJob(job.item);
                        Items.destroyItem(job.item.getWurmId());
                        continue;
                    } else if (item.getQualityLevel() >= workbook.getSkillCap()) {
                        continue;
                    }
                } else {
                    if (item.getQualityLevel() >= job.targetQL || item.getQualityLevel() >= CrafterMod.getMaxItemQL()) {
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
                    Item lump = tools.getPreferredTool(lumpId, job.item.getCurrentQualityLevel());
                    if (lump == null) {
                        try {
                            lump = tools.createMissingItem(lumpId);
                        } catch (NoSuchTemplateException | FailedException e) {
                            logger.warning("Could not create required improving item (template id - " + lumpId + ").  Reason follows:");
                            e.printStackTrace();
                            continue;
                        }
                    }
                    if (!forge.getItems().contains(lump)) {
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

                Item tool = tools.getPreferredTool(toolTemplateId, job.item.getCurrentQualityLevel());
                if (tool == null) {
                    try {
                        if (toolTemplateId == ItemList.bodyHand)
                            throw new NoSpaceException("Hand was null.");
                        else
                            tool = tools.createMissingItem(toolTemplateId);
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

                capSkills();

                try {
                    if (!job.isDonation()) {
                        double skill = -1.0;
                        try {
                            Skill requiredSkill = crafter.getSkills().getSkill(MethodsItems.getImproveSkill(item));
                            skill = requiredSkill.getKnowledge();
                        } catch (NoSuchSkillException e) {
                            e.printStackTrace();
                        }
                        logger.info("Improving " + item.getName() + " - QL " + item.getQualityLevel() + "/" + job.targetQL + "/" + CrafterMod.getMaxItemQL() + " - Skill " + skill + "/" + workbook.getSkillCap() + "/" + CrafterMod.getSkillCap());
                    }

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

    public static Creature createNewCrafter(Creature owner, String name, byte sex, CrafterType crafterType, float skillCap, float priceModifier, Map<Integer, Double> skills) throws Exception {
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
            double knowledge = CrafterMod.getStartingSkillLevel();
            double saved = skills.getOrDefault(skill.getNumber(), -10.0);
            if (saved > knowledge) {
                knowledge = Math.min(skillCap, saved);
            }
            skill.setKnowledge(knowledge, false);
            // Parent skills.
            for (int skillId : skill.getDependencies()) {
                crafter.getSkills().getSkillOrLearn(skillId);
            }
        }
        crafter.getCreatureAIData().setCreature(crafter);
        return crafter;
    }
}
