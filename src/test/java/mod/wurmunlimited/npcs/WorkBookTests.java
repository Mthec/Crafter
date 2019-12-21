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
import mod.wurmunlimited.CrafterObjectsFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class WorkBookTests {
    private CrafterObjectsFactory factory;
    private CrafterType crafterType;

    @BeforeEach
    void setUp() throws Exception {
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
    void testTodoDone() throws WorkBook.WorkBookFull {
        Creature player = factory.createNewPlayer();
        WorkBook workBook = createNewWorkbook(20);
        workBook.addJob(123, factory.createNewItem(), 25.0f, false, 1);
        workBook.addJob(player.getWurmId(), factory.createNewItem(), 25.0f, false, 1);
        assert player.getWurmId() != 123;

        assertEquals(2, workBook.todo());
        assertEquals(0, workBook.done());

        workBook.setDone(workBook.getJobsFor(player).get(0));

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
    void testSetDone() throws WorkBook.WorkBookFull {
        WorkBook workBook = createNewWorkbook(20);
        workBook.addJob(123, factory.createNewItem(), 24.0f, false, 1);
        assert workBook.todo() == 1;

        assertFalse(workBook.iterator().next().isDone());
        workBook.setDone(workBook.iterator().next());
        assertTrue(workBook.iterator().next().isDone());
    }

    @Test
    void testSetDoneWhenMailWhenDone() throws WorkBook.WorkBookFull {
        WorkBook workBook = createNewWorkbook(20);
        workBook.addJob(123, factory.createNewItem(), 24.0f, true, 1);
        assert workBook.todo() == 1;

        assertFalse(workBook.iterator().next().isDone());
        workBook.setDone(workBook.iterator().next());
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
}
