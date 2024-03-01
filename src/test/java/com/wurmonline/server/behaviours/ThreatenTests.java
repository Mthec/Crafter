package com.wurmonline.server.behaviours;

import com.google.common.base.Joiner;
import com.wurmonline.server.Servers;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.skills.NoSuchSkillException;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.structures.BridgePart;
import com.wurmonline.server.structures.Fence;
import com.wurmonline.server.structures.Floor;
import com.wurmonline.server.structures.Wall;
import com.wurmonline.server.zones.VolaTile;
import com.wurmonline.server.zones.Zones;
import mod.wurmunlimited.npcs.CrafterAIData;
import mod.wurmunlimited.npcs.CrafterMod;
import mod.wurmunlimited.npcs.CrafterTest;
import mod.wurmunlimited.npcs.WorkBook;
import org.gotti.wurmunlimited.modsupport.actions.ActionEntryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static mod.wurmunlimited.Assert.receivedMessageContaining;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

public class ThreatenTests extends CrafterTest {
    private static ThreatenAction threaten;
    private Player friend;
    private Player foe;
    private static boolean wasPVP = false;
    private static boolean wasHomeServer = false;

    @BeforeAll
    public static void create() {
        ActionEntryBuilder.init();
        threaten = new ThreatenAction();
    }

    @BeforeEach
    protected void setUp() throws Exception {
        super.setUp();
        friend = factory.createNewPlayer();
        assert friend.isFriendlyKingdom(crafter.getKingdomId());
        foe = factory.createNewPlayer();
        foe.setKingdomId((byte)(crafter.getKingdomId() + 1));
        assert !foe.isFriendlyKingdom(crafter.getKingdomId());
        Properties properties = new Properties();
        properties.setProperty("allow_threatening", "true");
        new CrafterMod().configure(properties);
        wasPVP = Servers.localServer.PVPSERVER;
        Servers.localServer.PVPSERVER = true;
        wasHomeServer = Servers.localServer.HOMESERVER;
        Servers.localServer.HOMESERVER = false;
    }

    @AfterEach
    protected void tearDown() {
        Servers.localServer.PVPSERVER = wasPVP;
        Servers.localServer.HOMESERVER = wasHomeServer;
    }

    // getBehavioursFor

    private boolean isBehaviour(List<ActionEntry> entries) {
        System.out.println(entries);
        return entries.size() == 1 && entries.get(0).getActionString().equals("Threaten");
    }

    private boolean isEmpty(List<ActionEntry> entries) {
        return entries.isEmpty();
    }

    @Test
    public void testGetBehavioursFor() {
        assertTrue(isBehaviour(threaten.getBehavioursFor(foe, crafter)));
        assertTrue(isEmpty(threaten.getBehavioursFor(friend, crafter)));
    }

    @Test
    public void testGetBehavioursForItem() {
        Item writ = factory.createWritFor(factory.createNewPlayer(), crafter);
        assertTrue(isBehaviour(threaten.getBehavioursFor(foe, writ, crafter)));
        assertTrue(isEmpty(threaten.getBehavioursFor(friend, writ, crafter)));
    }

    @Test
    public void testGetBehavioursForNotCrafter() {
        Item writ = factory.createWritFor(friend, crafter);
        Creature notCrafter = factory.createNewCreature();
        assertTrue(isEmpty(threaten.getBehavioursFor(foe, notCrafter)));
        assertTrue(isEmpty(threaten.getBehavioursFor(friend, notCrafter)));
        assertTrue(isEmpty(threaten.getBehavioursFor(foe, factory.createWritFor(foe, crafter), notCrafter)));
        assertTrue(isEmpty(threaten.getBehavioursFor(friend, writ, notCrafter)));
    }

    @Test
    public void testGetBehavioursForNotPVPServer() {
        Servers.localServer.PVPSERVER = false;
        assertTrue(isEmpty(threaten.getBehavioursFor(foe, crafter)));
        assertTrue(isEmpty(threaten.getBehavioursFor(friend, crafter)));
    }

    @Test
    public void testGetBehavioursForHomeServerServer() {
        Servers.localServer.PVPSERVER = true;
        Servers.localServer.HOMESERVER = true;
        assertTrue(isEmpty(threaten.getBehavioursFor(foe, crafter)));
        assertTrue(isEmpty(threaten.getBehavioursFor(friend, crafter)));
    }

    // action

    @Test
    public void testAction() {
        Action action = mock(Action.class);
        AtomicInteger atom = new AtomicInteger(0);
        when(action.getTimeLeft()).thenAnswer(i -> atom.get());
        doAnswer(i -> {
            atom.set(i.getArgument(0));
            return null;
        }).when(action).setTimeLeft(anyInt());
        assertFalse(threaten.action(action, friend, crafter, threaten.getActionId(), 1f));
        assertEquals(0, atom.get());
        assertThat(friend, receivedMessageContaining("You can't rob"));
        assertFalse(threaten.action(action, foe, crafter, threaten.getActionId(), 1f));
        assertTrue(atom.get() > 0);
        assertThat(foe, receivedMessageContaining("You start to rob"));
    }

    private void addTauntingSkillToPlayer(Creature player, double skillCheckResult) {
        Skill taunting = mock(Skill.class);
        when(taunting.skillCheck(anyDouble(), anyDouble(), anyBoolean(), anyFloat())).thenReturn(skillCheckResult);
        foe.getSkills().getSkillTree().put(SkillList.TAUNTING, taunting);
    }

    @Test
    public void testActionSkillTooLow() throws NoSuchSkillException {
        Action action = mock(Action.class);
        addTauntingSkillToPlayer(foe, 0.0);
        assertTrue(threaten.action(action, foe, crafter, threaten.getActionId(), Float.MAX_VALUE));
        assertThat(foe, receivedMessageContaining("snorts and refuses to yield."));
    }

    @Test
    public void testActionSkillSufficient() throws NoSuchSkillException {
        Action action = mock(Action.class);
        addTauntingSkillToPlayer(foe, 1.0);
        assertTrue(threaten.action(action, foe, crafter, threaten.getActionId(), Float.MAX_VALUE));
        assertThat(foe, receivedMessageContaining("looks really scared"));
    }

    private void mockTile(VolaTile tile) {
        when(tile.getWalls()).thenReturn(new Wall[0]);
        when(tile.getFences()).thenReturn(new Fence[0]);
        when(tile.getFloors()).thenReturn(new Floor[0]);
        when(tile.getBridgeParts()).thenReturn(new BridgePart[0]);
    }

    @Test
    public void testActionOnlyJobItemsDropped() throws NoSuchSkillException, WorkBook.WorkBookFull {
        Action action = mock(Action.class);
        Item tool = factory.createNewItem(ItemList.hammerMetal);
        Item job = factory.createNewItem(ItemList.hammerMetal);
        WorkBook workBook = ((CrafterAIData)crafter.getCreatureAIData()).getWorkBook();
        workBook.addJob(player.getWurmId(), job, 100f, false, 0);
        assert workBook.todo() == 1;
        crafter.getInventory().insertItem(tool);
        crafter.getInventory().insertItem(job);
        addTauntingSkillToPlayer(foe, 1.0);
        mockTile(Objects.requireNonNull(Zones.getOrCreateTile((int)crafter.getStatus().getPositionX() >> 2, (int)crafter.getStatus().getPositionY() >> 2, true)));
        mockTile(Objects.requireNonNull(Zones.getOrCreateTile((int)crafter.getStatus().getPositionX() >> 2, ((int)crafter.getStatus().getPositionY() >> 2) - 1, true)));

        assertTrue(threaten.action(action, foe, crafter, threaten.getActionId(), Float.MAX_VALUE));
        assertThat(foe, receivedMessageContaining("looks really scared"));
        // Workbook + tool.
        assertEquals(2, crafter.getInventory().getItems().size(), Joiner.on(",").join(crafter.getInventory().getItems().stream().map(Item::getName).iterator()));
        assertEquals(crafter.getInventory(), tool.getParentOrNull());
        assertNull(job.getParentOrNull());
        assertEquals(0, workBook.todo());
    }
}
