package com.wurmonline.server.behaviours;

import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.villages.NoSuchRoleException;
import com.wurmonline.server.villages.Village;
import com.wurmonline.server.villages.VillageStatus;
import com.wurmonline.server.zones.NoSuchZoneException;
import com.wurmonline.server.zones.Zones;
import mod.wurmunlimited.CrafterObjectsFactory;
import mod.wurmunlimited.npcs.CrafterAIData;
import mod.wurmunlimited.npcs.CrafterTemplate;
import mod.wurmunlimited.npcs.CrafterType;
import org.gotti.wurmunlimited.modsupport.actions.ActionEntryBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static mod.wurmunlimited.Assert.didNotReceiveMessageContaining;
import static mod.wurmunlimited.Assert.receivedMessageContaining;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AssignActionTests {
    private static final int contractTemplateId = CrafterTemplate.getTemplateId();
    private static final String assignMessageFragment = " assign this forge";
    private static final String unassignMessageFragment = "unassign this forge";
    private CrafterObjectsFactory factory;
    private AssignAction action;
    private Creature player;
    private Creature owner;
    private Creature crafter;
    private Item contract;
    private Item forge;
    private Action act;
    private CrafterAIData data;

    @BeforeEach
    void setUp() throws Exception {
        factory = new CrafterObjectsFactory();
        ActionEntryBuilder.init();
        action = new AssignAction(contractTemplateId);
        player = factory.createNewPlayer();
        owner = factory.createNewPlayer();
        crafter = factory.createNewCrafter(owner, new CrafterType(CrafterType.allMetal), 50);
        contract = factory.createNewItem(contractTemplateId);
        contract.setData(crafter.getWurmId());
        owner.getInventory().insertItem(contract);
        forge = factory.createNewItem(ItemList.forge);
        act = mock(Action.class);
        data = (CrafterAIData)crafter.getCreatureAIData();
        assert !data.getWorkBook().isForgeAssigned();
    }

    // getBehavioursFor

    @Test
    void testGetBehavioursForUnassigned() {
        assert !data.getWorkBook().isForgeAssigned();
        List<ActionEntry> entries = action.getBehavioursFor(owner, contract, forge);
        assertEquals(1, entries.size());
        assertEquals("Assign", entries.get(0).getActionString());
        assertEquals(action.getActionId(), entries.get(0).getNumber());
    }

    @Test
    void testGetBehavioursForAssigned() {
        data.setForge(forge);
        List<ActionEntry> entries = action.getBehavioursFor(owner, contract, forge);
        assertEquals(1, entries.size());
        assertEquals("Unassign", entries.get(0).getActionString());
        assertEquals(action.getActionId(), entries.get(0).getNumber());
    }

    @Test
    void testGetBehavioursForNotOwner() {
        assertNull(action.getBehavioursFor(player, contract, forge));
    }

    @Test
    void testGetBehavioursForNotContract() {
        Item item = factory.createNewItem();
        assert item.getTemplateId() != contractTemplateId;
        assertNull(action.getBehavioursFor(player, item, forge));
    }

    @Test
    void testGetBehavioursForCrafterNotPlaced() {
        contract.setData(-1);
        assertNull(action.getBehavioursFor(player, contract, forge));
    }

    @Test
    void testGetBehavioursForNotForge() {
        Item item = factory.createNewItem();
        assert item.getTemplateId() != ItemList.forge;
        assertNull(action.getBehavioursFor(player, contract, item));
    }

    // action

    @Test
    void testAssign() {
        assertTrue(action.action(act, owner, contract, forge, action.getActionId(), 0));
        assertTrue(data.getWorkBook().isForgeAssigned());
        assertThat(owner, receivedMessageContaining(assignMessageFragment));
    }

    @Test
    void testUnassign() {
        data.setForge(forge);
        assertTrue(action.action(act, owner, contract, forge, action.getActionId(), 0));
        assertFalse(data.getWorkBook().isForgeAssigned());
        assertThat(owner, receivedMessageContaining(unassignMessageFragment));
    }

    @Test
    void testAssignNotUnassignedOnFailure() {
        data.setForge(forge);
        assert data.getWorkBook().isForgeAssigned();
        assertFalse(action.action(act, owner, contract, forge, (short)(action.getActionId() + 1), 0));
        assertTrue(data.getWorkBook().isForgeAssigned());
        assertThat(owner, didNotReceiveMessageContaining(assignMessageFragment));
        assertThat(owner, didNotReceiveMessageContaining(unassignMessageFragment));
    }

    @Test
    void testAssignIncorrectActionId() {
        assertFalse(action.action(act, owner, contract, forge, (short)(action.getActionId() + 1), 0));
        assertFalse(data.getWorkBook().isForgeAssigned());
        assertThat(owner, didNotReceiveMessageContaining(assignMessageFragment));
    }

    @Test
    void testAssignNotForge() {
        Item item = factory.createNewItem();
        assert item.getTemplateId() != ItemList.forge;
        assertFalse(action.action(act, owner, contract, item, action.getActionId(), 0));
        assertFalse(data.getWorkBook().isForgeAssigned());
        assertThat(owner, didNotReceiveMessageContaining(assignMessageFragment));
    }

    @Test
    void testAssignCrafterNotPlaced() {
        contract.setData(-1);
        assertFalse(action.action(act, owner, contract, forge, action.getActionId(), 0));
        assertFalse(data.getWorkBook().isForgeAssigned());
        assertThat(owner, didNotReceiveMessageContaining(assignMessageFragment));
    }

    @Test
    void testUnassignRemovesItemsFromForgeWithNoConcurrentModificationException() {
        data.setForge(forge);
        List<Item> tools = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Item tool = factory.createNewItem();
            crafter.getInventory().insertItem(tool);
            forge.insertItem(tool);
            tools.add(tool);
        }

        assertTrue(action.action(act, owner, contract, forge, action.getActionId(), 0));
        assertThat(owner, receivedMessageContaining(unassignMessageFragment));
        for (Item tool : tools) {
            assertTrue(crafter.getInventory().getItems().contains(tool));
            assertFalse(forge.getItems().contains(tool));
        }
    }

    @Test
    void testAssigningToAnotherCraftersForgeBlocked() {
        assertTrue(action.action(act, owner, contract, forge, action.getActionId(), 0));
        assertTrue(data.getWorkBook().isForgeAssigned());

        Item contract2 = factory.createNewItem(contractTemplateId);
        Creature crafter2 = factory.createNewCrafter(owner, new CrafterType(CrafterType.allMetal), 50);
        contract2.setData(crafter2.getWurmId());
        assertTrue(action.action(act, owner, contract2, forge, action.getActionId(), 0));
        assertFalse(((CrafterAIData)crafter2.getCreatureAIData()).getWorkBook().isForgeAssigned());
        assertThat(owner, receivedMessageContaining("already assigned"));
    }

    @Test
    void testForgeNotEmpty() {
        Item item = factory.createNewItem();
        forge.insertItem(item);
        assertTrue(action.action(act, owner, contract, forge, action.getActionId(), 0));
        assertFalse(data.getWorkBook().isForgeAssigned());
        assertThat(owner, receivedMessageContaining("must empty"));
    }

    @Test
    void testInsufficientPermissionsForForge() throws NoSuchRoleException, NoSuchZoneException {
        Village v = factory.createVillageFor(player);
        crafter.getStatus().setPosition(player.getStatus().getPosition());
        crafter.currentTile = null;
        assert Zones.getOrCreateTile(crafter.getTileX(), crafter.getTileY(), crafter.isOnSurface()).getVillage() != null;
        assert !v.getRoleForStatus(VillageStatus.ROLE_EVERYBODY).mayPickup();

        assertTrue(action.action(act, owner, contract, forge, action.getActionId(), 0));
        assertFalse(data.getWorkBook().isForgeAssigned());
        assertThat(owner, receivedMessageContaining("would not have permission"));
    }

    @Test
    void testCrafterTooFarAwayFromForge() {
        int dist = crafter.getMaxHuntDistance();
        forge.setTempPositions(forge.getPosX() + dist + 1, forge.getPosY() + dist + 1, forge.getPosZ(), forge.getRotation());
        assert !crafter.isWithinDistanceTo(forge, dist);
        assertTrue(action.action(act, owner, contract, forge, action.getActionId(), 0));
        assertFalse(data.getWorkBook().isForgeAssigned());
        assertThat(owner, receivedMessageContaining("too far"));
        factory.getCommunicator(owner).clear();

        forge.setTempPositions(crafter.getPosX(), crafter.getPosY(), forge.getPosZ(), forge.getRotation());
        assert crafter.isWithinDistanceTo(forge, dist);
        assertTrue(action.action(act, owner, contract, forge, action.getActionId(), 0));
        assertTrue(data.getWorkBook().isForgeAssigned());
        assertThat(owner, didNotReceiveMessageContaining("too far"));
    }
}
