package mod.wurmunlimited.npcs;

import com.google.common.base.Joiner;
import com.wurmonline.server.FailedException;
import com.wurmonline.server.Items;
import com.wurmonline.server.NoSuchItemException;
import com.wurmonline.server.WurmId;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.ai.CreatureAIData;
import com.wurmonline.server.economy.Economy;
import com.wurmonline.server.economy.Shop;
import com.wurmonline.server.items.*;
import com.wurmonline.server.villages.Village;
import com.wurmonline.shared.exceptions.WurmServerException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class WorkBook implements Iterable<Job> {
    private final Logger logger = Logger.getLogger(WorkBook.class.getName());
    private static final int MAX_INSCRIPTION_LENGTH = 500;
    static final String workBookDescription = "Work Book";
    @SuppressWarnings("WeakerAccess")
    public final Item workBookItem;
    Item forge;
    private CrafterType crafterType;
    private float skillCap;
    private final List<Job> jobs = new ArrayList<>();
    private final Map<Item, Job> jobItems = new HashMap<>();
    private final List<Byte> restrictedMaterials = new ArrayList<>();

    public static class NoWorkBookOnWorker extends WurmServerException {
        NoWorkBookOnWorker(String message) {
            super(message);
        }
    }

    public static class InvalidWorkBookInscription extends WurmServerException {
        InvalidWorkBookInscription(String message) {
            super(message);
        }
    }

    public static class WorkBookFull extends WurmServerException {
        WorkBookFull(String message) {
            super(message);
        }
    }

    WorkBook(Item workBookItem) throws InvalidWorkBookInscription {
        if (!isWorkBook(workBookItem))
            throw new InvalidWorkBookInscription("Work book item does not fit the criteria.");
        this.workBookItem = workBookItem;

        Iterator<Item> pages = workBookItem.getItems().stream().sorted(Comparator.comparing(Item::getDescription)).iterator();
        if (!pages.hasNext())
            throw new InvalidWorkBookInscription("No contents page found.");

        Item contentsPage = pages.next();
        InscriptionData contentsInscription = contentsPage.getInscription();
        if (contentsInscription == null)
            throw new InvalidWorkBookInscription("Contents page (" + contentsPage.getWurmId() + ") has no inscription.");

        String[] header = contentsInscription.getInscription().split("\n");
        if (header.length < 3) {
            throw new InvalidWorkBookInscription("Invalid work book header - " + Arrays.toString(header));
        }

        boolean rewriteHeader = false;
        try {
            skillCap = Float.parseFloat(header[0]);
            skillCap = Math.min(skillCap, CrafterMod.getSkillCap());
        } catch (NumberFormatException e) {
            logger.warning("Invalid skill cap value in workbook (" + header[0] + ") - Setting server cap.");
            skillCap = CrafterMod.getSkillCap();
            header[0] = Float.toString(skillCap);
            rewriteHeader = true;
        }

        try {
            long forgeId = Long.parseLong(header[1]);
            if (forgeId != -10) {
                try {
                    forge = Items.getItem(forgeId);
                } catch (NoSuchItemException e) {
                    logger.warning("Could not find forge - " + forgeId + ".  Was it destroyed?  Removing.");
                    e.printStackTrace();
                    header[1] = "-10";
                    rewriteHeader = true;
                }
            }
        } catch (NumberFormatException e) {
            logger.warning("Invalid forge id in workbook (" + header[1] + ") - Removing.");
            header[1] = "-10";
            rewriteHeader = true;
        }

        if (rewriteHeader) {
            contentsInscription.setInscription(Joiner.on("\n").join(header));
        }

        boolean hasRestricted = false;
        if (header[2].startsWith("restrict")) {
            hasRestricted = true;
            for (String material : header[2].split(",")) {
                if (!material.equals("restrict")) {
                    try {
                        restrictedMaterials.add(Byte.parseByte(material));
                    } catch (NumberFormatException e) {
                        logger.warning("Invalid restricted material in workbook (" + material + ") - Ignoring.");
                    }
                }
            }
        }

        try {
            List<Integer> skills = new ArrayList<>();

            int firstIndex = hasRestricted ? 3 : 2;
            while (firstIndex < header.length) {
                skills.add(Integer.parseInt(header[firstIndex]));
                ++firstIndex;
            }

            crafterType = new CrafterType(skills.toArray(new Integer[0]));
        } catch (IllegalArgumentException e) {
            throw new InvalidWorkBookInscription("Invalid work book crafter type - " + header[0]);
        }

        AtomicBoolean reSave = new AtomicBoolean(false);
        pages.forEachRemaining(page -> {
            InscriptionData inscription = page.getInscription();
            if (inscription == null) {
                logger.warning(page.getName() + " in workbook has no inscription.  Removing.");
                Items.destroyItem(page.getWurmId());
            } else {
                String[] lines = inscription.getInscription().split("\n");
                for (String line : lines) {
                    String[] values = line.split(",");

                    try {
                        if (values.length == 1) {
                            addDonation(Items.getItem(Long.parseLong(values[0])), false);
                        } else {
                            addJob(Long.parseLong(values[0]), Items.getItem(Long.parseLong(values[1])), Float.parseFloat(values[2]),
                                Integer.parseInt(values[3]) == 1, Long.parseLong(values[4]), Integer.parseInt(values[5]) == 1, false);
                        }
                    } catch (ArrayIndexOutOfBoundsException | NoSuchItemException | NumberFormatException e) {
                        logger.warning("Invalid line in workbook - " + line);
                        // Try to recover owner and item.
                        try {
                            if (values.length >= 2 && values[0] != null && values[1] != null) {
                                long customerId = Long.parseLong(values[0]);
                                long itemId = Long.parseLong(values[1]);

                                // Check ids are correct type as an extra precaution.
                                if (WurmId.getType(customerId) == 0 && WurmId.getType(itemId) == 2) {
                                    new Job(customerId, Items.getItem(itemId), 1, false, 0, false).mailToCustomer();
                                }
                                logger.warning("Item recovery attempted successfully.  Maybe?");
                            }
                        } catch (NumberFormatException | NoSuchItemException ignored) {}
                        // Re save workbook after loading the rest of the entries.
                        reSave.set(true);
                        e.printStackTrace();
                    } catch (WorkBookFull ignored) {}
                    // Exception should never happen as saveWorkbook is never called this way.
                }
            }
        });

        if (reSave.get()) {
            try {
                saveWorkBook();
            } catch (WorkBookFull ignored) {}
        }
    }

    private WorkBook(CrafterType crafterType, float skillCap) throws NoSuchTemplateException, FailedException {
        this.crafterType = crafterType;
        this.skillCap = Math.min(skillCap, CrafterMod.getSkillCap());
        workBookItem = ItemFactory.createItem(ItemList.book, 10.0f, "");
        workBookItem.setDescription(workBookDescription);
        workBookItem.setHasNoDecay(true);
        Item page = getNewPage("Contents");
        page.setInscription(Joiner.on("\n").join(this.skillCap, -10, (Object[])crafterType.getAllTypes()), "");
    }

    private Item getNewPage(String description) throws NoSuchTemplateException, FailedException {
        Item page = ItemFactory.createItem(ItemList.papyrusSheet, 10.0f, "");
        page.setDescription(description);
        page.setHasNoDecay(true);
        workBookItem.insertItem(page);
        return page;
    }

    @Nonnull
    public Iterator<Job> iterator() {
        return new Iterator<Job>() {
            private Iterator<Job> jobIterator = jobs.stream().filter(job -> !job.isDonation()).iterator();
            private Iterator<Job> donationsIterator = jobs.stream().filter(Job::isDonation).sorted((i, j) -> Float.compare(i.item.getQualityLevel(), j.item.getQualityLevel())).iterator();

            @Override
            public boolean hasNext() {
                return jobIterator.hasNext() || donationsIterator.hasNext();
            }

            @Override
            public Job next() {
                if (jobIterator.hasNext())
                    return jobIterator.next();
                else
                    return donationsIterator.next();
            }
        };
    }

    public float getSkillCap() {
        return skillCap;
    }

    public CrafterType getCrafterType() {
        return crafterType;
    }

    public static WorkBook createNewWorkBook(CrafterType crafterType, float skillLevel) throws NoSuchTemplateException, FailedException {
        return new WorkBook(crafterType, skillLevel);
    }

    public static boolean isWorkBook(Item item) {
        return item.getTemplateId() == ItemList.book && item.getDescription().equals(workBookDescription) && item.getItemCount() > 0;
    }

    public static WorkBook getWorkBookFromWorker(Creature crafter) throws NoWorkBookOnWorker {
        CreatureAIData aiData = crafter.getCreatureAIData();
        if (aiData instanceof CrafterAIData)
            return ((CrafterAIData)aiData).getWorkBook();
        else
            throw new NoWorkBookOnWorker("Not a Worker or no work book found.");
    }

    public int todo() {
        return (int)jobs.stream().filter(job -> !job.done && !job.isDonation()).count();
    }

    public int donationsTodo() {
        return (int)jobs.stream().filter(Job::isDonation).count();
    }

    public int done() {
        return (int)jobs.stream().filter(job -> job.done).count();
    }

    public List<Job> getJobsFor(Creature creature) {
        return jobs.stream().filter(job -> job.customerId == creature.getWurmId()).collect(Collectors.toList());
    }

    public boolean isForgeAssigned() {
        return forge != null;
    }

    public boolean isJobItem(Item item) {
        return jobItems.keySet().contains(item);
    }

    public void addJob(long customerId, Item item, float targetQL, boolean mailWhenDone, long priceCharged) throws WorkBookFull {
        addJob(customerId, item, targetQL, mailWhenDone, priceCharged, false, true);
    }

    private void addJob(long customerId, Item item, float targetQL, boolean mailWhenDone, long priceCharged, boolean done, boolean save) throws WorkBookFull {
        targetQL = Math.min(targetQL, CrafterMod.getSkillCap());
        jobs.add(new Job(customerId, item, targetQL, mailWhenDone, priceCharged, done));
        jobItems.put(item, jobs.get(jobs.size() - 1));
        if (save)
            saveWorkBook();
    }

    public void removeJob(Item item) {
        Job job = jobItems.remove(item);
        jobs.remove(job);
        try {
            saveWorkBook();
        } catch (WorkBookFull ignored) {}
        // Exception should never happen as removeJob should only be reducing the page count.
    }

    public void addDonation(Item item) throws WorkBookFull {
        addDonation(item, true);
    }

    private void addDonation(Item item, boolean save) throws WorkBookFull {
        jobs.add(new Donation(item));
        jobItems.put(item, jobs.get(jobs.size() - 1));
        if (save)
            saveWorkBook();
    }

    public void updateSkillsSettings(CrafterType newCrafterType, float newSkillCap) throws WorkBookFull {
        crafterType = newCrafterType;
        skillCap = newSkillCap;
        saveWorkBook();
    }

    private void saveWorkBook() throws WorkBookFull {
        try {
            Iterator<Item> pages = workBookItem.getItems().stream().sorted(Comparator.comparing(Item::getDescription)).iterator();
            Item contentsPage;
            if (!pages.hasNext()) {
                logger.warning("Contents page missing when saving workbook. Adding a new one.");
                contentsPage = getNewPage("Contents");
            } else {
                contentsPage = pages.next();
            }

            StringBuilder contentsSb = new StringBuilder();
            contentsSb.append(skillCap).append("\n");
            contentsSb.append((forge == null ? "-10" : forge.getWurmId())).append("\n");
            if (restrictedMaterials.size() > 0)
                contentsSb.append("restrict").append(Joiner.on(",").join(restrictedMaterials)).append("\n");
            contentsSb.append(Joiner.on("\n").join(crafterType.getAllTypes()));
            contentsPage.setInscription(contentsSb.toString(), "");

            int pageNumber = 1;
            int length = 0;
            StringBuilder sb = new StringBuilder();
            for (Job job : jobs) {
                String jobString = job.toString();
                if (jobString.length() + length > MAX_INSCRIPTION_LENGTH) {
                    if (pageNumber == 9) {
                        throw new WorkBookFull("Work book is already full.");
                    }

                    addPage(pageNumber, sb.toString(), pages);
                    ++pageNumber;
                    length = 0;
                    sb = new StringBuilder();
                }
                length += jobString.length();
                sb.append(jobString);
            }
            if (sb.length() > 0)
                addPage(pageNumber, sb.toString(), pages);

            pages.forEachRemaining(page -> Items.destroyItem(page.getWurmId()));
        } catch (NoSuchTemplateException | FailedException e) {
            logger.severe("A server error occurred when creating a new item.  Aborting.");
            e.printStackTrace();
        }
    }

    private void addPage(int num, String inscription, Iterator<Item> pages) throws NoSuchTemplateException, FailedException {
        Item page;
        if (pages.hasNext()) {
            page = pages.next();
            page.setDescription("Page " + num);
        }
        else
            page = getNewPage("Page " + num);
        page.setInscription(inscription, "");
    }

    void setForge(@Nullable Item forge) {
        if (forge != null && forge.getTemplateId() != ItemList.forge)
            return;
        if (this.forge != forge) {
            this.forge = forge;
            try {
                saveWorkBook();
            } catch (WorkBookFull ignored) {}
            // Exception should never happen as forge is saved to the contents page.
        }
    }

    void setDone(Job job, Creature crafter) {
        if (job.isDonation())
            return;

        job.done = true;

        Shop shop = crafter.getShop();

        final long price = job.getPriceCharged();
        long forCrafter = 0;
        long forKing = 0;
        long forUpkeep = 0;

        switch (CrafterMod.getPaymentOption()) {
            case all_tax:
                forKing = price;
                break;
            case tax_and_upkeep:
                forUpkeep = (long)(price * CrafterMod.getUpkeepPercentage());
                forKing = price - forUpkeep;
                break;
            case for_owner:
                forCrafter = (long)(price * 0.9F);
                forKing = price - forCrafter;
                break;
        }

        if (forCrafter != 0L) {
            shop.setMoney(shop.getMoney() + forCrafter);
            shop.addMoneyEarned(forCrafter);
        }

        if (forUpkeep != 0L) {
            Village v = crafter.getCitizenVillage();
            if (v == null)
                forKing += forUpkeep;
            else {
                v.plan.addMoney(forUpkeep);
                // Using MoneySpent to show how much upkeep is accumulated over time.
                shop.addMoneySpent(forUpkeep);
            }
        }

        if (forKing != 0L) {
            Shop kingsMoney = Economy.getEconomy().getKingsShop();
            kingsMoney.setMoney(kingsMoney.getMoney() + forKing);
            shop.addTax(forKing);
        }

        shop.setLastPolled(System.currentTimeMillis());

        if (job.mailWhenDone()) {
            job.mailToCustomer();
            removeJob(job.item);
            return;
        }
        try {
            saveWorkBook();
        } catch (WorkBookFull ignored) {}
        // Exception should never happen as done is a single character for true or false.
    }

    public boolean hasEnoughSpaceFor(List<String> lines) {
        int currentPageNumber = workBookItem.getItemCount() - 1;
        int charactersRequired = lines.stream().mapToInt(String::length).sum();
        if (currentPageNumber < 9 && charactersRequired < 500) {
            return true;
        }
        Optional<Item> maybeItem = workBookItem.getItems().stream().max(Comparator.comparing(Item::getDescription));
        if (maybeItem.isPresent()) {
            InscriptionData inscription = maybeItem.get().getInscription();
            if (inscription != null) {
                return charactersRequired <= 500 - inscription.getInscription().length();
            }
        }
        return false;
    }

    // TODO - Rename, something different as they could be seen as restricted from or restricted to.
    public List<Byte> getRestrictedMaterials() {
        return new ArrayList<>(restrictedMaterials);
    }

    public void updateRestrictedMaterials(List<Byte> materials) throws WorkBookFull {
        restrictedMaterials.clear();
        restrictedMaterials.addAll(materials);
        saveWorkBook();
    }

    public boolean isRestrictedMaterial(byte b) {
        if (CrafterMod.materialsRestrictedGlobally()) {
            if (CrafterMod.isGloballyRestrictedMaterial(b))
                return true;
        }
        return restrictedMaterials.size() != 0 && !restrictedMaterials.contains(b);
    }
}
