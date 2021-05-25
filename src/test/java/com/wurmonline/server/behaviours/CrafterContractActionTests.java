package com.wurmonline.server.behaviours;

import com.wurmonline.server.Constants;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.FakeCommunicator;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.questions.CrafterHireQuestion;
import com.wurmonline.server.questions.CrafterManagementQuestion;
import mod.wurmunlimited.CrafterObjectsFactory;
import mod.wurmunlimited.npcs.CrafterMod;
import mod.wurmunlimited.npcs.CrafterType;
import org.gotti.wurmunlimited.modsupport.actions.ActionEntryBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static mod.wurmunlimited.Assert.bmlEqual;
import static mod.wurmunlimited.Assert.receivedMessageContaining;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class CrafterContractActionTests {
    private CrafterObjectsFactory factory;
    private CrafterContractAction action;
    private Player owner;
    private Creature crafter;
    private Item contract;
    private final Action act = mock(Action.class);

    @BeforeEach
    void setUp() throws Exception {
        Constants.dbHost = ".";
        factory = new CrafterObjectsFactory();
        ActionEntryBuilder.init();
        action = new CrafterContractAction(CrafterMod.getContractTemplateId());
        owner = factory.createNewPlayer();
        crafter = factory.createNewCrafter(owner, new CrafterType(CrafterType.allMetal), 50);
        contract = factory.createNewItem(CrafterMod.getContractTemplateId());
        contract.setData(crafter.getWurmId());
        owner.getInventory().insertItem(contract);
        Properties crafterModProperties = new Properties();
        crafterModProperties.setProperty("name_prefix", "");
        new CrafterMod().configure(crafterModProperties);
    }

    // getBehaviourFor

    @Test
    void testGetBehaviourFor() {
        List<ActionEntry> entry = action.getBehavioursFor(owner, contract);
        assertNotNull(entry);
        assertEquals(1, entry.size());
        assertEquals(action.getActionId(), entry.get(0).getNumber());
        assertEquals("Manage", entry.get(0).getActionString());
    }

    @Test
    void testGetBehaviourForActiveItem() {
        List<ActionEntry> entry = action.getBehavioursFor(owner, factory.createNewItem(), contract);
        assertNotNull(entry);
        assertEquals(1, entry.size());
        assertEquals(action.getActionId(), entry.get(0).getNumber());
        assertEquals("Manage", entry.get(0).getActionString());
    }

    @Test
    void testGetBehaviourForNonContract() {
        assertNull(action.getBehavioursFor(owner, factory.createNewItem()));
    }

    // action

    @Test
    void testActionHire() {
        contract.setData(-1);
        assertTrue(action.action(act, owner, contract, action.getActionId(), 0));
        new CrafterHireQuestion(owner, contract.getWurmId()).sendQuestion();
        assertThat(owner, bmlEqual());
    }

    @Test
    void testActionManage() {
        assertTrue(action.action(act, owner, contract, action.getActionId(), 0));
        new CrafterManagementQuestion(owner, crafter).sendQuestion();
        assertThat(owner, bmlEqual());
    }

    @Test
    void testActionManageActiveItem() {
        assertTrue(action.action(act, owner, factory.createNewItem(), contract, action.getActionId(), 0));
        new CrafterManagementQuestion(owner, crafter).sendQuestion();
        assertThat(owner, bmlEqual());
    }

    @Test
    void testActionInvalidCreatureId() {
        contract.setData(12345);
        assertTrue(action.action(act, owner, contract, action.getActionId(), 0));
        assertEquals(FakeCommunicator.empty, factory.getCommunicator(owner).lastBmlContent);
        assertThat(owner, receivedMessageContaining("don't exist"));
    }

    @Test
    void testActionIncorrectActionId() {
        assertTrue(action.action(act, owner, contract, (short)(action.getActionId() + 1), 0));
    }

    @Test
    void testActionNotAContract() {
        assertTrue(action.action(act, owner, factory.createNewItem(), action.getActionId(), 0));
    }
}
