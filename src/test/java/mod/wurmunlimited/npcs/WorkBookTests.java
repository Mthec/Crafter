package mod.wurmunlimited.npcs;

import com.google.common.base.Joiner;
import com.wurmonline.server.FailedException;
import com.wurmonline.server.Items;
import com.wurmonline.server.NoSuchItemException;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.items.NoSuchTemplateException;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.shared.constants.ItemMaterials;
import mod.wurmunlimited.CrafterObjectsFactory;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class WorkBookTests extends GlobalRestrictionsFileWrapper {
    private CrafterObjectsFactory factory;
    private CrafterType crafterType;

    @BeforeEach
    protected void setUp() throws Exception {
        super.setUp();
        factory = new CrafterObjectsFactory();
        crafterType = new CrafterType(CrafterType.allMetal);
    }

    private Item createBlankWorkbookItem() {
        Item workbook = factory.createNewItem(ItemList.book);
        workbook.setDescription(WorkBook.workBookDescription);
        Item contentsPage = factory.createNewItem(ItemList.papyrusSheet);
        contentsPage.setDescription("Contents");
        contentsPage.setInscription("", "");
        workbook.insertItem(contentsPage);
        return workbook;
    }

    private WorkBook getWorkBookFromItem(Item item) {
        try {
            return new WorkBook(item);
        } catch (WorkBook.InvalidWorkBookInscription e) {
            throw new RuntimeException(e);
        }
    }

    private WorkBook createNewWorkbook(int skill) {
        return createNewWorkbook(crafterType, skill, -10);
    }

    private WorkBook createNewWorkbook(CrafterType crafterType, int skill, long forgeId) {
        Item workbook = createBlankWorkbookItem();
        workbook.getFirstContainedItem().setInscription(Joiner.on("\n").join(skill, forgeId, (Object[])crafterType.getAllTypes()), "");
        return getWorkBookFromItem(workbook);
    }

    private Item getOrCreatePageOne(Item workBookItem) {
        try {
            return workBookItem.getItems().stream().filter(i -> i.getDescription().equals("Page 1")).findAny().orElseThrow(() -> new NoSuchItemException(""));
        } catch (NoSuchItemException e) {
            Item page1 = factory.createNewItem(ItemList.papyrusSheet);
            page1.setDescription("Page 1");
            page1.setInscription("", "");
            workBookItem.insertItem(page1);
            return page1;
        }
    }

    @Test
    void testDoneValueSetCorrectlyOnServerReload() throws Exception {
        Creature crafter = factory.createNewCrafter(factory.createNewPlayer(), crafterType, 10);
        Item tool = factory.createNewItem();
        Item page1 = getOrCreatePageOne(WorkBook.getWorkBookFromWorker(crafter).workBookItem);
        page1.setInscription("1," + tool.getWurmId() + ",10.0,0,1,1", "");

        CrafterAIData data = (CrafterAIData)crafter.getCreatureAIData();
        data.setCreature(crafter);
        crafter.postLoad();

        assertTrue(data.getWorkBook().iterator().next().done);
    }

    @Test
    void testWorkBookLoadingCrafterType() {
        for (Integer type : Arrays.stream(new Integer[][] {CrafterType.allMetal, CrafterType.allWood, CrafterType.allArmour})
                                                        .flatMap(Arrays::stream)
                                                        .toArray(Integer[]::new)) {
            Item workBookItem = createBlankWorkbookItem();
            workBookItem.getFirstContainedItem().setInscription(Joiner.on("\n").join(
                    "20", "-10", type), "");
            WorkBook workBook = getWorkBookFromItem(workBookItem);
            assertEquals(1, workBook.getCrafterType().getAllTypes().length);
            assertEquals(type, workBook.getCrafterType().getAllTypes()[0]);
        }
    }

    @Test
    void testWorkBookLoadingInvalidCrafterType() {
        //noinspection SpellCheckingInspection
        String notCrafterType = "asdjhgfiaohet";
        Item workBookItem = createBlankWorkbookItem();
        workBookItem.getFirstContainedItem().setInscription(Joiner.on("\n").join(
                "20", "-10", notCrafterType), "");
        assertThrows(WorkBook.InvalidWorkBookInscription.class, () -> new WorkBook(workBookItem));
    }

    @Test
    void testWorkBookLoadingSkillCap() {
        int cap = 20;
        assert CrafterMod.getSkillCap() > cap + 10;
        Item workBookItem = createBlankWorkbookItem();
        workBookItem.getFirstContainedItem().setInscription(Joiner.on("\n").join(
                cap + 10, "-10", (Object[])crafterType.getAllTypes()), "");
        WorkBook workBook = getWorkBookFromItem(workBookItem);
        assertEquals(cap + 10, workBook.getSkillCap());

        workBookItem.getFirstContainedItem().setInscription(Joiner.on("\n").join(
                cap - 10, "-10", (Object[])crafterType.getAllTypes()), "");
        WorkBook other = getWorkBookFromItem(workBookItem);
        assertEquals(cap - 10, other.getSkillCap());
    }

    @Test
    void testWorkBookLoadingInvalidSkillCap() {
        Item workBookItem = createBlankWorkbookItem();
        workBookItem.getFirstContainedItem().setInscription(Joiner.on("\n").join(
                "abc", "-10", (Object[])crafterType.getAllTypes()), "");
        WorkBook workBook = getWorkBookFromItem(workBookItem);
        assertEquals(CrafterMod.getSkillCap(), workBook.getSkillCap());

        workBookItem.getFirstContainedItem().setInscription(Joiner.on("\n").join(
                crafterType, "0.153", "-10"), "");
        WorkBook other = getWorkBookFromItem(workBookItem);
        assertEquals(CrafterMod.getSkillCap(), other.getSkillCap());
    }

    @Test
    void testWorkBookLoadingSkillCapCappedByCrafterMod() {
        float cap = CrafterMod.getSkillCap();
        Item workBookItem = createBlankWorkbookItem();
        workBookItem.getFirstContainedItem().setInscription(Joiner.on("\n").join(
                cap + 1, "-10", (Object[])crafterType.getAllTypes()), "");
        WorkBook workBook = getWorkBookFromItem(workBookItem);
        assertEquals(cap, workBook.getSkillCap());

        workBookItem.getFirstContainedItem().setInscription(Joiner.on("\n").join(
                cap - 1, "-10", (Object[])crafterType.getAllTypes()), "");
        WorkBook other = getWorkBookFromItem(workBookItem);
        assertEquals(cap - 1, other.getSkillCap());
    }

    @Test
    void testWorkBookLoadingForge() {
        WorkBook noForgeWorkBook = createNewWorkbook(crafterType, 20, -10);
        assertNull(noForgeWorkBook.forge);

        for (int i = 0; i < 10; ++i) {
            Item forge = factory.createNewItem(ItemList.forge);
            WorkBook workBook = createNewWorkbook(crafterType, 20, forge.getWurmId());
            assertEquals(forge, workBook.forge);
        }
    }

    @Test
    void testWorkBookLoadingInvalidForge() {
        Item forge = factory.createNewItem(ItemList.forge);
        Item workBookItem = createBlankWorkbookItem();
        workBookItem.getFirstContainedItem().setInscription(Joiner.on("\n").join(
                "20", forge.getWurmId() + 1, (Object[])crafterType.getAllTypes()), "");
        WorkBook workBook = getWorkBookFromItem(workBookItem);
        assertNull(workBook.forge);
        assertEquals("20\n-10\n", Objects.requireNonNull(workBookItem.getFirstContainedItem().getInscription()).getInscription().substring(0, 7));
    }

    @Test
    void testWorkBookLoadingJobs() {
        Item workBookItem = createBlankWorkbookItem();
        workBookItem.getFirstContainedItem().setInscription(Joiner.on("\n").join(
                "20", "-10", (Object[])crafterType.getAllTypes()), "");
        Item tool = factory.createNewItem();
        for (int i = 1; i < 11; ++i) {
            Item page1 = getOrCreatePageOne(workBookItem);
            page1.setInscription(Objects.requireNonNull(page1.getInscription()).getInscription() +
                                         "123," + tool.getWurmId() + ",25.0,0,1,0\n", "");
            WorkBook workBook = getWorkBookFromItem(workBookItem);
            assertEquals(i, workBook.todo());
        }
    }

    @Test
    void testWorkBookLoadingIllegalJobCustomerId() {
        Item workBookItem = createBlankWorkbookItem();
        workBookItem.getFirstContainedItem().setInscription(Joiner.on("\n").join(
                "20", "-10", (Object[])crafterType.getAllTypes()), "");
        Item tool = factory.createNewItem();
        Item page1 = getOrCreatePageOne(workBookItem);
        page1.setInscription(Objects.requireNonNull(page1.getInscription()).getInscription() +
                                     "123," + tool.getWurmId() + ",25.0,0,1,0\n", "");
        page1.setInscription(Objects.requireNonNull(page1.getInscription()).getInscription() +
                                     "abc," + tool.getWurmId() + ",25.0,0,1,0\n", "");
        page1.setInscription(Objects.requireNonNull(page1.getInscription()).getInscription() +
                                     "1.06456," + tool.getWurmId() + ",25.0,0,1,0\n", "");

        WorkBook workBook = getWorkBookFromItem(workBookItem);
        assertEquals(1, workBook.todo());
    }

    @Test
    void testWorkBookLoadingIllegalJobItemId() {
        Item workBookItem = createBlankWorkbookItem();
        workBookItem.getFirstContainedItem().setInscription(Joiner.on("\n").join(
                "20", "-10", (Object[])crafterType.getAllTypes()), "");
        Item tool = factory.createNewItem();
        Item page1 = getOrCreatePageOne(workBookItem);
        page1.setInscription(Objects.requireNonNull(page1.getInscription()).getInscription() +
                                     "123," + tool.getWurmId() + ",25.0,0,1,0\n", "");
        page1.setInscription(Objects.requireNonNull(page1.getInscription()).getInscription() +
                                     "123," + "abc" + ",25.0,0,1,0\n", "");
        page1.setInscription(Objects.requireNonNull(page1.getInscription()).getInscription() +
                                     "123," + "1.06456" + ",25.0,0,1,0\n", "");

        WorkBook workBook = getWorkBookFromItem(workBookItem);
        assertEquals(1, workBook.todo());
    }

    @Test
    void testWorkBookLoadingIllegalJobItemNonExistent() {
        Item workBookItem = createBlankWorkbookItem();
        workBookItem.getFirstContainedItem().setInscription(Joiner.on("\n").join(
                "20", "-10", (Object[])crafterType.getAllTypes()), "");
        Item tool = factory.createNewItem();
        assertThrows(NoSuchItemException.class, () -> Items.getItem(tool.getWurmId() + 1));
        Item page1 = getOrCreatePageOne(workBookItem);
        page1.setInscription(Objects.requireNonNull(page1.getInscription()).getInscription() +
                                     "123," + tool.getWurmId() + ",25.0,0,1,0\n", "");
        page1.setInscription(Objects.requireNonNull(page1.getInscription()).getInscription() +
                                     "123," + tool.getWurmId() + 1 + ",25.0,0,1,0\n", "");

        WorkBook workBook = getWorkBookFromItem(workBookItem);
        assertEquals(1, workBook.todo());
    }

    @Test
    void testWorkBookLoadingIllegalJobTargetQL() {
        Item workBookItem = createBlankWorkbookItem();
        workBookItem.getFirstContainedItem().setInscription(Joiner.on("\n").join(
                "20", "-10", (Object[])crafterType.getAllTypes()), "");
        Item tool = factory.createNewItem();
        Item page1 = getOrCreatePageOne(workBookItem);
        page1.setInscription(Objects.requireNonNull(page1.getInscription()).getInscription() +
                                     "123," + tool.getWurmId() + ",25.0,0,1,0\n", "");
        page1.setInscription(Objects.requireNonNull(page1.getInscription()).getInscription() +
                                     "123," + tool.getWurmId() + ",abc,0,1,0\n", "");

        WorkBook workBook = getWorkBookFromItem(workBookItem);
        assertEquals(1, workBook.todo());
    }

    @Test
    void testWorkBookLoadingIllegalJobMailedValue() {
        Item workBookItem = createBlankWorkbookItem();
        workBookItem.getFirstContainedItem().setInscription(Joiner.on("\n").join(
                "20", "-10", (Object[])crafterType.getAllTypes()), "");
        Item tool = factory.createNewItem();
        Item page1 = getOrCreatePageOne(workBookItem);
        page1.setInscription(Objects.requireNonNull(page1.getInscription()).getInscription() +
                                     "123," + tool.getWurmId() + ",25.0,0,1,0\n", "");
        page1.setInscription(Objects.requireNonNull(page1.getInscription()).getInscription() +
                                     "123," + tool.getWurmId() + ",25.0,1,1,0\n", "");
        page1.setInscription(Objects.requireNonNull(page1.getInscription()).getInscription() +
                                     "123," + tool.getWurmId() + ",25.0,1.5,1,0\n", "");
        page1.setInscription(Objects.requireNonNull(page1.getInscription()).getInscription() +
                                     "123," + tool.getWurmId() + ",25.0,abc,1,0\n", "");

        WorkBook workBook = getWorkBookFromItem(workBookItem);
        assertEquals(2, workBook.todo());
    }

    @Test
    void testWorkBookLoadingIllegalJobDoneValue() {
        Item workBookItem = createBlankWorkbookItem();
        workBookItem.getFirstContainedItem().setInscription(Joiner.on("\n").join(
                "20", "-10", (Object[])crafterType.getAllTypes()), "");
        Item tool = factory.createNewItem();
        Item page1 = getOrCreatePageOne(workBookItem);
        page1.setInscription(Objects.requireNonNull(page1.getInscription()).getInscription() +
                                     "123," + tool.getWurmId() + ",25.0,0,1,0\n", "");
        page1.setInscription(Objects.requireNonNull(page1.getInscription()).getInscription() +
                                     "123," + tool.getWurmId() + ",25.0,0,1,1\n", "");
        page1.setInscription(Objects.requireNonNull(page1.getInscription()).getInscription() +
                                     "123," + tool.getWurmId() + ",25.0,0,1,1.5\n", "");
        page1.setInscription(Objects.requireNonNull(page1.getInscription()).getInscription() +
                                     "123," + tool.getWurmId() + ",25.0,0,1,abc\n", "");

        WorkBook workBook = getWorkBookFromItem(workBookItem);
        assertEquals(2, workBook.todo() + workBook.done());
    }

    @Test
    void testWorkBookLoadingIllegalPriceCharged() {
        Item workBookItem = createBlankWorkbookItem();
        workBookItem.getFirstContainedItem().setInscription(Joiner.on("\n").join(
                "20", "-10", (Object[])crafterType.getAllTypes()), "");
        Item tool = factory.createNewItem();
        Item page1 = getOrCreatePageOne(workBookItem);
        page1.setInscription(Objects.requireNonNull(page1.getInscription()).getInscription() +
                                     "123," + tool.getWurmId() + ",25.0,0,1,0\n", "");
        page1.setInscription(Objects.requireNonNull(page1.getInscription()).getInscription() +
                                     "123," + tool.getWurmId() + ",25.0,0,1.1,0\n", "");
        page1.setInscription(Objects.requireNonNull(page1.getInscription()).getInscription() +
                                     "123," + tool.getWurmId() + ",25.0,0,abc,0\n", "");

        WorkBook workBook = getWorkBookFromItem(workBookItem);
        assertEquals(1, workBook.todo());
    }

    @Test
    void testWorkBookLoadingInvalidHeader() {
        Item workBookItem = createBlankWorkbookItem();
        assertThrows(WorkBook.InvalidWorkBookInscription.class, () -> new WorkBook(workBookItem));
    }

    @Test
    void testGetSkillCap() {
        int cap = 30;
        assertEquals(cap, createNewWorkbook(cap).getSkillCap());
    }

    @Test
    void testGetCrafterType() {
        assertEquals(new CrafterType(SkillList.SMITHING_GOLDSMITHING), createNewWorkbook(new CrafterType(SkillList.SMITHING_GOLDSMITHING), 30, -10).getCrafterType());
    }

    @Test
    void testCreateNewWorkBook() throws NoSuchTemplateException, FailedException {
        WorkBook workBook = WorkBook.createNewWorkBook(new CrafterType(SkillList.SMITHING_BLACKSMITHING), 45);

        assertEquals(ItemList.book, workBook.workBookItem.getTemplateId());
        assertEquals("45.0\n-10\n" + SkillList.SMITHING_BLACKSMITHING, Objects.requireNonNull(workBook.workBookItem.getFirstContainedItem().getInscription()).getInscription());
    }

    @Test
    void testIsWorkBook() throws NoSuchTemplateException, FailedException {
        WorkBook workBook = WorkBook.createNewWorkBook(new CrafterType(SkillList.SMITHING_BLACKSMITHING), 45);

        assertTrue(WorkBook.isWorkBook(workBook.workBookItem));
        assertFalse(WorkBook.isWorkBook(factory.createNewItem(ItemList.book)));
    }

    @Test
    void testGetWorkBookFromWorker() throws WorkBook.NoWorkBookOnWorker {
        int cap = 35;
        Creature crafter = factory.createNewCrafter(factory.createNewPlayer(), new CrafterType(SkillList.SMITHING_GOLDSMITHING), cap);
        WorkBook workBook = getWorkBookFromItem(crafter.getInventory().getItems().stream().filter(item -> item.getTemplateId() == ItemList.book)
                                                        .findAny().orElseThrow(RuntimeException::new));
        WorkBook other = WorkBook.getWorkBookFromWorker(crafter);

        assertEquals(workBook.workBookItem, other.workBookItem);
        assertEquals(workBook.getSkillCap(), other.getSkillCap());
        assertEquals(workBook.getCrafterType(), other.getCrafterType());
    }

    @Test
    void testGetNoWorkBookFromWorker() {
        Creature crafter = factory.createNewCreature();
        assertThrows(WorkBook.NoWorkBookOnWorker.class, () -> WorkBook.getWorkBookFromWorker(crafter));
    }

    @Test
    void testTodoDone() throws WorkBook.WorkBookFull, WorkBook.NoWorkBookOnWorker {
        Creature player = factory.createNewPlayer();
        Creature crafter = factory.createNewCrafter(factory.createNewPlayer(), new CrafterType(CrafterType.allMetal), 20);
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        workBook.addJob(123, factory.createNewItem(), 25.0f, false, 1);
        workBook.addJob(player.getWurmId(), factory.createNewItem(), 25.0f, false, 1);
        assert player.getWurmId() != 123;

        assertEquals(2, workBook.todo());
        assertEquals(0, workBook.done());

        workBook.setDone(workBook.getJobsFor(player).get(0), crafter);

        assertEquals(1, workBook.todo());
        assertEquals(1, workBook.done());
    }

    @Test
    void testGetJobsFor() throws WorkBook.WorkBookFull {
        Creature player = factory.createNewPlayer();
        WorkBook workBook = createNewWorkbook(20);
        workBook.addJob(123, factory.createNewItem(), 24.0f, false, 1);
        workBook.addJob(player.getWurmId(), factory.createNewItem(), 25.0f, false, 1);
        workBook.addJob(player.getWurmId(), factory.createNewItem(), 26.0f, false, 1);
        assert player.getWurmId() != 123;

        List<Job> jobs = workBook.getJobsFor(player);
        assertEquals(2, jobs.size());
        assertEquals(25.0f, jobs.get(0).targetQL);
        assertEquals(26.0f, jobs.get(1).targetQL);
    }

    @Test
    void testIsForgeAssigned() {
        WorkBook workBook = createNewWorkbook(20);

        assertFalse(workBook.isForgeAssigned());
        workBook.setForge(factory.createNewItem(ItemList.forge));
        assertTrue(workBook.isForgeAssigned());
    }

    @Test
    void testIsJobItem() throws WorkBook.WorkBookFull {
        WorkBook workBook = createNewWorkbook(20);
        Item item1 = factory.createNewItem();
        Item item2 = factory.createNewItem();
        workBook.addJob(123, item1, 24.0f, false, 1);

        assertTrue(workBook.isJobItem(item1));
        assertFalse(workBook.isJobItem(item2));
    }

    @Test
    void testAddJob() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        Creature player = factory.createNewPlayer();
        Creature crafter = factory.createNewCrafter(player, new CrafterType(SkillList.SMITHING_BLACKSMITHING), 20);
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        Item item = factory.createNewItem();
        workBook.addJob(player.getWurmId(), item, 24.0f, true, 1);

        WorkBook postLoad = WorkBook.getWorkBookFromWorker(crafter);
        assertEquals(1, postLoad.todo());

        Job job = workBook.getJobsFor(player).get(0);
        assertEquals(item, job.item);
        assertEquals(24.0f, job.targetQL);
        assertTrue(job.mailWhenDone);
    }

    @Test
    void testRemoveJob() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        Creature player = factory.createNewPlayer();
        Creature crafter = factory.createNewCrafter(player, new CrafterType(SkillList.SMITHING_BLACKSMITHING), 20);
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        Item item = factory.createNewItem();
        workBook.addJob(player.getWurmId(), item, 24.0f, true, 1);

        assertEquals(1, workBook.todo());
        workBook.removeJob(item);

        WorkBook postLoad = WorkBook.getWorkBookFromWorker(crafter);
        assertEquals(0, postLoad.todo());
        assertEquals(0, workBook.getJobsFor(player).size());
    }

    @Test
    void testSetForge() {
        WorkBook workBook = createNewWorkbook(20);
        Item forge = factory.createNewItem(ItemList.forge);
        workBook.setForge(forge);

        assertEquals(forge, workBook.forge);
    }

    @Test
    void testSetDone() throws WorkBook.WorkBookFull, WorkBook.NoWorkBookOnWorker {
        Creature crafter = factory.createNewCrafter(factory.createNewPlayer(), new CrafterType(CrafterType.allMetal), 20);
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        workBook.addJob(123, factory.createNewItem(), 24.0f, false, 1);
        assert workBook.todo() == 1;

        assertFalse(workBook.iterator().next().isDone());
        workBook.setDone(workBook.iterator().next(), crafter);
        assertTrue(workBook.iterator().next().isDone());
    }

    @Test
    void testSetDoneWhenMailWhenDone() throws WorkBook.WorkBookFull, WorkBook.NoWorkBookOnWorker {
        Creature crafter = factory.createNewCrafter(factory.createNewPlayer(), new CrafterType(CrafterType.allMetal), 20);
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        workBook.addJob(123, factory.createNewItem(), 24.0f, true, 1);
        assert workBook.todo() == 1;

        assertFalse(workBook.iterator().next().isDone());
        workBook.setDone(workBook.iterator().next(), crafter);
        assertEquals(0, workBook.done());
    }

    @Test
    void testSavingWorkbookPages() throws NoSuchTemplateException, FailedException, WorkBook.WorkBookFull {
        WorkBook workBook = WorkBook.createNewWorkBook(new CrafterType(SkillList.SMITHING_BLACKSMITHING), 20);

        assertEquals(1, workBook.workBookItem.getItemCount());

        workBook.addJob(123, factory.createNewItem(), 1, false, 1);

        assertEquals(2, workBook.workBookItem.getItemCount());
        Iterator<Item> pages = workBook.workBookItem.getItems().stream().sorted(Comparator.comparing(Item::getDescription)).iterator();
        assertEquals("Contents", pages.next().getDescription());
        assertEquals("Page 1", pages.next().getDescription());
    }

    @Test
    void testSavingMaxPages() throws NoSuchTemplateException, FailedException, WorkBook.WorkBookFull {
        WorkBook workBook = WorkBook.createNewWorkBook(new CrafterType(SkillList.SMITHING_BLACKSMITHING), 20);

        assertEquals(1, workBook.workBookItem.getItemCount());

        Item tool = factory.createNewItem();
        int newJobLength = Job.toString(123, tool, 1, false, 1, false).length();
        int pageCount = 9;
        int length = 0;
        while (pageCount > 0) {
            length += newJobLength;
            if (length > 500) {
                --pageCount;
                length = 0;
            } else {
                workBook.addJob(123, tool, 1, false, 1);
            }
        }

        assertEquals(10, workBook.workBookItem.getItemCount());
        Iterator<Item> pages = workBook.workBookItem.getItems().stream().sorted(Comparator.comparing(Item::getDescription)).iterator();
        assertEquals("Contents", pages.next().getDescription());

        for (int i = 1; i <= 9; i++) {
            assertEquals("Page " + i, pages.next().getDescription());
        }
    }

    @Test
    void testLoadingMaxPages() throws NoSuchTemplateException, FailedException, WorkBook.WorkBookFull {
        WorkBook workBook = WorkBook.createNewWorkBook(new CrafterType(SkillList.SMITHING_BLACKSMITHING), 20);

        Item tool = factory.createNewItem();
        int newJobLength = Job.toString(0, tool, 1, false, 1, false).length() - 1;
        int pageCount = 9;
        int length = 0;
        int customerId = 1;
        while (pageCount > 0) {
            int customerIdLength = Integer.toString(customerId).length();
            length += customerIdLength + newJobLength;
            if (length > 500) {
                --pageCount;
                length = 0;
            } else {
                workBook.addJob(customerId, tool, 1, false, 1);
                ++customerId;
            }
        }

        assertEquals(10, workBook.workBookItem.getItemCount());

        customerId = 1;
        for (Job job : workBook) {
            assertEquals(customerId, job.customerId);
            ++customerId;
        }
    }

    @Test
    void testSavingAboveMaxPages() throws NoSuchTemplateException, FailedException, WorkBook.WorkBookFull {
        WorkBook workBook = WorkBook.createNewWorkBook(new CrafterType(SkillList.SMITHING_BLACKSMITHING), 20);

        assertEquals(1, workBook.workBookItem.getItemCount());

        Item tool = factory.createNewItem();
        int newJobLength = Job.toString(123, tool, 1, false, 1, false).length();
        int pageCount = 9;
        int length = 0;
        while (pageCount > 0) {
            length += newJobLength;
            if (length > 500) {
                --pageCount;
                length = 0;
            } else {
                workBook.addJob(123, tool, 1, false, 1);
            }
        }

        assertEquals(10, workBook.workBookItem.getItemCount());
        assertThrows(WorkBook.WorkBookFull.class, () -> workBook.addJob(123, tool, 1, false, 1));
    }

    @Test
    void testHasEnoughSpace() {
        List<String> lines = new ArrayList<>();
        int max = 9 * 500;
        WorkBook workBook = createNewWorkbook(SkillList.SMITHING_BLACKSMITHING);
        String line = new Job(123456789, factory.createNewItem(ItemList.pickAxe), 56.65f, true, 987654321, false).toString();

        while (lines.stream().mapToInt(String::length).sum() + line.length() < max) {
            lines.add(line);
            assertTrue(workBook.hasEnoughSpaceFor(lines), "Reached:  " + lines.stream().mapToInt(String::length).sum());
        }

        lines.add(line);
        assertFalse(workBook.hasEnoughSpaceFor(lines));
    }

    @Test
    void testDonationsLastInIterator() throws NoSuchTemplateException, FailedException, WorkBook.WorkBookFull {
        WorkBook workBook = WorkBook.createNewWorkBook(new CrafterType(SkillList.SMITHING_BLACKSMITHING), 20);

        for (int i = 0; i < 10; i++) {
            Item toolJob = factory.createNewItem();
            Item toolDonation = factory.createNewItem();
            workBook.addJob(123, toolJob, 100, false, 1);
            workBook.addDonation(toolDonation);
        }

        Iterator<Job> jobs = workBook.iterator();
        Job first = jobs.next();
        assertFalse(first.isDonation());

        //noinspection StatementWithEmptyBody
        while (!jobs.next().isDonation());

        jobs.forEachRemaining(job -> assertTrue(job.isDonation()));

    }

    @Test
    void testDonationsInIteratorLowestQLFirst() throws NoSuchTemplateException, FailedException, WorkBook.WorkBookFull {
        WorkBook workBook = WorkBook.createNewWorkBook(new CrafterType(SkillList.SMITHING_BLACKSMITHING), 20);

        float[] qls = new float[] { 10, 15, 5 };
        float[] sortedQls = qls.clone();
        Arrays.sort(sortedQls);


        for (float i : qls) {
            Item tool = factory.createNewItem();
            tool.setQualityLevel(i);
            workBook.addDonation(tool);
        }

        int idx = 0;
        for (Job job : workBook) {
            assertEquals(sortedQls[idx], job.item.getQualityLevel());
            ++idx;
        }
    }

    @Test
    void testLoadWorkBookWithRestrictedMaterials() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        Creature crafter = factory.createNewCrafter(factory.createNewPlayer(), new CrafterType(SkillList.SMITHING_BLACKSMITHING), 20);
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);

        assert workBook.getRestrictedMaterials().size() == 0;

        workBook.updateRestrictedMaterials(Collections.singletonList(ItemMaterials.MATERIAL_IRON));

        WorkBook workBook2 = WorkBook.getWorkBookFromWorker(crafter);
        List<Byte> restricted = workBook2.getRestrictedMaterials();
        assertEquals(1, restricted.size());
        assertEquals(ItemMaterials.MATERIAL_IRON, (byte)restricted.get(0));
    }

    @Test
    void testWorkBookInscriptionRestrictedMaterials() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull, WorkBook.InvalidWorkBookInscription {
        Creature crafter = factory.createNewCrafter(factory.createNewPlayer(), new CrafterType(SkillList.SMITHING_BLACKSMITHING), 20);
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);

        assert workBook.getRestrictedMaterials().size() == 0;

        workBook.updateRestrictedMaterials(Collections.singletonList(ItemMaterials.MATERIAL_IRON));

        WorkBook workBook2 = new WorkBook(workBook.workBookItem);
        assertEquals("20.0\n-10\nrestrict11\n10015", Objects.requireNonNull(workBook2.workBookItem.getItems().stream().filter(n -> n.getDescription().equals("Contents")).findAny().orElseThrow(RuntimeException::new).getInscription()).getInscription());
    }

    @Test
    void testReadRestrictedMaterials() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull, WorkBook.InvalidWorkBookInscription {
        Creature crafter = factory.createNewCrafter(factory.createNewPlayer(), new CrafterType(SkillList.SMITHING_BLACKSMITHING), 20);
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        Item contents = workBook.workBookItem.getItems().stream().filter(n -> n.getDescription().equals("Contents")).findAny().orElseThrow(RuntimeException::new);
        contents.setInscription("20.0\n-10\nrestrict7\n10015", "");

        WorkBook workBook2 = new WorkBook(workBook.workBookItem);
        List<Byte> restrictedMaterials = workBook2.getRestrictedMaterials();
        assertEquals(1, restrictedMaterials.size());
        assertEquals(ItemMaterials.MATERIAL_GOLD, restrictedMaterials.get(0));
    }

    @Test
    void testUpdateRestrictedMaterials() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        Creature crafter = factory.createNewCrafter(factory.createNewPlayer(), new CrafterType(SkillList.SMITHING_BLACKSMITHING), 20);
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        workBook.updateRestrictedMaterials(Collections.singletonList(ItemMaterials.MATERIAL_IRON));

        WorkBook workBook2 = WorkBook.getWorkBookFromWorker(crafter);
        workBook2.updateRestrictedMaterials(Collections.singletonList(ItemMaterials.MATERIAL_GOLD));

        WorkBook workBook3 = WorkBook.getWorkBookFromWorker(crafter);
        List<Byte> restricted = workBook3.getRestrictedMaterials();
        assertEquals(1, restricted.size());
        assertEquals(ItemMaterials.MATERIAL_GOLD, (byte)restricted.get(0));
    }

    @Test
    void testIsRestrictedMaterialWhenNoneRestricted() throws WorkBook.NoWorkBookOnWorker {
        Creature crafter = factory.createNewCrafter(factory.createNewPlayer(), new CrafterType(SkillList.SMITHING_BLACKSMITHING), 20);
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);

        for (int x = 1; x <= ItemMaterials.MATERIAL_MAX; ++x) {
            assertFalse(workBook.isRestrictedMaterial((byte)x));
        }
    }

    @Test
    void testIsRestrictedMaterial() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        Creature crafter = factory.createNewCrafter(factory.createNewPlayer(), new CrafterType(SkillList.SMITHING_BLACKSMITHING), 20);
        byte notRestricted = ItemMaterials.MATERIAL_ADAMANTINE;
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        workBook.updateRestrictedMaterials(Collections.singletonList(notRestricted));

        for (int x = 1; x <= ItemMaterials.MATERIAL_MAX; ++x) {
            if ((byte)x == notRestricted)
                assertFalse(workBook.isRestrictedMaterial((byte)x));
            else
                assertTrue(workBook.isRestrictedMaterial((byte)x));
        }
    }

    @Test
    void testIsRestrictedMaterialAffectedByGlobal() throws WorkBook.NoWorkBookOnWorker, NoSuchFieldException, IllegalAccessException {
        Creature crafter = factory.createNewCrafter(factory.createNewPlayer(), new CrafterType(SkillList.SMITHING_BLACKSMITHING), 20);
        byte notRestricted = ItemMaterials.MATERIAL_ADAMANTINE;
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        assert workBook.getRestrictedMaterials().size() == 0;
        List<Byte> restricted = ReflectionUtil.getPrivateField(null, CrafterMod.class.getDeclaredField("restrictedMaterials"));
        restricted.add(notRestricted);

        for (int x = 1; x <= ItemMaterials.MATERIAL_MAX; ++x) {
            if ((byte)x == notRestricted)
                assertFalse(workBook.isRestrictedMaterial((byte)x));
            else
                assertTrue(workBook.isRestrictedMaterial((byte)x));
        }
    }

    @Test
    void testIsRestrictedMaterialDoesNotOverrideGlobal() throws WorkBook.NoWorkBookOnWorker, NoSuchFieldException, IllegalAccessException, WorkBook.WorkBookFull {
        Creature crafter = factory.createNewCrafter(factory.createNewPlayer(), new CrafterType(SkillList.SMITHING_BLACKSMITHING), 20);
        byte notRestricted = ItemMaterials.MATERIAL_ADAMANTINE;
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        workBook.updateRestrictedMaterials(Collections.singletonList((byte)(notRestricted + 1)));
        List<Byte> restricted = ReflectionUtil.getPrivateField(null, CrafterMod.class.getDeclaredField("restrictedMaterials"));
        restricted.add(notRestricted);

        for (int x = 1; x <= ItemMaterials.MATERIAL_MAX; ++x) {
            assertTrue(workBook.isRestrictedMaterial((byte)x));
        }
    }

    @Test
    void testGetRestrictedMaterialsDoesNotIncludeOnlyGlobal() throws WorkBook.NoWorkBookOnWorker, NoSuchFieldException, IllegalAccessException, WorkBook.WorkBookFull {
        Creature crafter = factory.createNewCrafter(factory.createNewPlayer(), new CrafterType(SkillList.SMITHING_BLACKSMITHING), 20);
        byte restricted = ItemMaterials.MATERIAL_ADAMANTINE;
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        List<Byte> restrictedMaterials = ReflectionUtil.getPrivateField(null, CrafterMod.class.getDeclaredField("restrictedMaterials"));
        restrictedMaterials.add(restricted);

        assertFalse(workBook.getRestrictedMaterials().contains(restricted));
    }

    @Test
    void testLoadWorkBookWithBlockedItems() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        Creature crafter = factory.createNewCrafter(factory.createNewPlayer(), new CrafterType(SkillList.SMITHING_BLACKSMITHING), 20);
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);

        assert workBook.getBlockedItems().size() == 0;

        workBook.updateBlockedItems(Collections.singletonList(ItemList.pickAxe));

        WorkBook workBook2 = WorkBook.getWorkBookFromWorker(crafter);
        List<Integer> blockedItems = workBook2.getBlockedItems();
        assertEquals(1, blockedItems.size());
        assertEquals(ItemList.pickAxe, blockedItems.get(0));
    }

    @Test
    void testWorkBookInscriptionBlockedItems() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull, WorkBook.InvalidWorkBookInscription {
        Creature crafter = factory.createNewCrafter(factory.createNewPlayer(), new CrafterType(SkillList.SMITHING_BLACKSMITHING), 20);
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);

        workBook.updateBlockedItems(Collections.singletonList(ItemList.shovel));

        WorkBook workBook2 = new WorkBook(workBook.workBookItem);
        assertEquals("20.0\n-10\nblocked25\n10015", Objects.requireNonNull(workBook2.workBookItem.getItems().stream().filter(n -> n.getDescription().equals("Contents")).findAny().orElseThrow(RuntimeException::new).getInscription()).getInscription());
    }

    @Test
    void testReadBlockedItems() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull, WorkBook.InvalidWorkBookInscription {
        Creature crafter = factory.createNewCrafter(factory.createNewPlayer(), new CrafterType(SkillList.SMITHING_BLACKSMITHING), 20);
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        Item contents = workBook.workBookItem.getItems().stream().filter(n -> n.getDescription().equals("Contents")).findAny().orElseThrow(RuntimeException::new);
        contents.setInscription("20.0\n-10\nblocked7\n10015", "");

        WorkBook workBook2 = new WorkBook(workBook.workBookItem);
        List<Integer> blockedItems = workBook2.getBlockedItems();
        assertEquals(1, blockedItems.size());
        assertEquals(ItemList.hatchet, blockedItems.get(0));
    }

    @Test
    void testUpdateBlockedItems() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        Creature crafter = factory.createNewCrafter(factory.createNewPlayer(), new CrafterType(SkillList.SMITHING_BLACKSMITHING), 20);
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        workBook.updateBlockedItems(Collections.singletonList(ItemList.hatchet));

        WorkBook workBook2 = WorkBook.getWorkBookFromWorker(crafter);
        workBook2.updateBlockedItems(Collections.singletonList(ItemList.shovel));

        WorkBook workBook3 = WorkBook.getWorkBookFromWorker(crafter);
        List<Integer> blockedItems = workBook3.getBlockedItems();
        assertEquals(1, blockedItems.size());
        assertEquals(ItemList.shovel, blockedItems.get(0));
    }

    @Test
    void testIsNotBlockedWhenNoneBlocked() throws WorkBook.NoWorkBookOnWorker {
        Creature crafter = factory.createNewCrafter(factory.createNewPlayer(), new CrafterType(SkillList.SMITHING_BLACKSMITHING), 20);
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);

        for (int x = 1; x <= CrafterMod.getContractTemplateId(); ++x) {
            assertFalse(workBook.isBlockedItem(x));
        }
    }

    @Test
    void testIsBlockedItem() throws WorkBook.NoWorkBookOnWorker, WorkBook.WorkBookFull {
        Creature crafter = factory.createNewCrafter(factory.createNewPlayer(), new CrafterType(SkillList.SMITHING_BLACKSMITHING), 20);
        int blocked = ItemList.shovel;
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        workBook.updateBlockedItems(Collections.singletonList(blocked));

        for (int x = 1; x <= CrafterMod.getContractTemplateId(); ++x) {
            if (x == blocked)
                assertTrue(workBook.isBlockedItem(x));
            else
                assertFalse(workBook.isBlockedItem(x));
        }
    }

    @Test
    void testIsBlockedItemAffectedByGlobal() throws WorkBook.NoWorkBookOnWorker, NoSuchFieldException, IllegalAccessException, WorkBook.WorkBookFull {
        Creature crafter = factory.createNewCrafter(factory.createNewPlayer(), new CrafterType(SkillList.SMITHING_BLACKSMITHING), 20);
        int blocked = ItemList.pickAxe;
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        workBook.updateBlockedItems(Collections.singletonList(blocked));
        int globalBlocked = ItemList.hatchet;
        Set<Integer> blockedItems = ReflectionUtil.getPrivateField(null, CrafterMod.class.getDeclaredField("blockedItems"));
        blockedItems.add(globalBlocked);

        for (int x = 1; x <= CrafterMod.getContractTemplateId(); ++x) {
            if (x == blocked || x == globalBlocked)
                assertTrue(workBook.isBlockedItem(x), Integer.toString(x));
            else
                assertFalse(workBook.isBlockedItem(x));
        }
    }

    @Test
    void testGetBlockedItemsDoesNotIncludeOnlyGlobal() throws WorkBook.NoWorkBookOnWorker, NoSuchFieldException, IllegalAccessException, WorkBook.WorkBookFull {
        Creature crafter = factory.createNewCrafter(factory.createNewPlayer(), new CrafterType(SkillList.SMITHING_BLACKSMITHING), 20);
        int blocked = ItemMaterials.MATERIAL_ADAMANTINE;
        WorkBook workBook = WorkBook.getWorkBookFromWorker(crafter);
        Set<Integer> blockedItems = ReflectionUtil.getPrivateField(null, CrafterMod.class.getDeclaredField("blockedItems"));
        blockedItems.add(blocked);

        assertFalse(workBook.getBlockedItems().contains(blocked));
    }
}
