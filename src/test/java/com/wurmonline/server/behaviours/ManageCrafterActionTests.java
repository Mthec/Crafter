package com.wurmonline.server.behaviours;

import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.questions.CrafterManagementQuestion;
import mod.wurmunlimited.npcs.CrafterMod;
import mod.wurmunlimited.npcs.CrafterTest;
import org.gotti.wurmunlimited.modsupport.actions.ActionEntryBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static mod.wurmunlimited.Assert.bmlEqual;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

public class ManageCrafterActionTests extends CrafterTest {
    private static ManageCrafterAction manage;
    private Player gm;
    
    @BeforeAll
    public static void create() {
        ActionEntryBuilder.init();
        manage = new ManageCrafterAction();
    }

    @BeforeEach
    protected void setUp() throws Exception {
        super.setUp();
        gm = factory.createNewPlayer();
        gm.setPower((byte)2);
        Properties properties = new Properties();
        properties.setProperty("gm_manage_power_required", "2");
        new CrafterMod().configure(properties);
    }

    // getBehavioursFor

    private boolean isBehaviour(List<ActionEntry> entries) {
        return entries.size() == 1 && entries.get(0).getActionString().equals("Manage");
    }

    private boolean isEmpty(List<ActionEntry> entries) {
        return entries.isEmpty();
    }

    @Test
    public void testGetBehavioursFor() {
        assertTrue(isBehaviour(manage.getBehavioursFor(gm, crafter)));
        assertTrue(isEmpty(manage.getBehavioursFor(player, crafter)));
    }

    @Test
    public void testGetBehavioursForItem() {
        Item writ = factory.createWritFor(factory.createNewPlayer(), crafter);
        assertTrue(isBehaviour(manage.getBehavioursFor(gm, writ, crafter)));
        assertTrue(isEmpty(manage.getBehavioursFor(player, writ, crafter)));
    }

    @Test
    public void testGetBehavioursForNotCrafter() {
        Item writ = factory.createWritFor(player, crafter);
        Creature notCrafter = factory.createNewCreature();
        assertTrue(isEmpty(manage.getBehavioursFor(gm, notCrafter)));
        assertTrue(isEmpty(manage.getBehavioursFor(player, notCrafter)));
        assertTrue(isEmpty(manage.getBehavioursFor(gm, factory.createWritFor(gm, crafter), notCrafter)));
        assertTrue(isEmpty(manage.getBehavioursFor(player, writ, notCrafter)));
    }

    @Test
    public void testGetBehavioursForGmManagePower() {
        assert CrafterMod.gmManagePowerRequired() == 2;
        assertTrue(isBehaviour(manage.getBehavioursFor(gm, crafter)));
    }

    @Test
    public void testGetBehavioursForGmManagePowerTooHigh() {
        Properties properties = new Properties();
        properties.setProperty("gm_manage_power_required", "10");
        new CrafterMod().configure(properties);
        assertTrue(isEmpty(manage.getBehavioursFor(gm, crafter)));
    }

    // action

    private void sendQuestion() {
        new CrafterManagementQuestion(gm, crafter).sendQuestion();
        new CrafterManagementQuestion(player, crafter).sendQuestion();
    }

    @Test
    public void testAction() {
        Action action = mock(Action.class);
        assertTrue(manage.action(action, gm, crafter, manage.getActionId(), 0));
        assertTrue(manage.action(action, player, crafter, manage.getActionId(), 0));

        assertEquals(1, factory.getCommunicator(gm).getBml().length);
        assertEquals(0, factory.getCommunicator(player).getBml().length);
        sendQuestion();
        assertThat(gm, bmlEqual());
    }

    @Test
    public void testActionItem() {
        Action action = mock(Action.class);
        Item item = factory.createNewItem(CrafterMod.getContractTemplateId());
        assertTrue(manage.action(action, gm, factory.createWritFor(gm, crafter), crafter, manage.getActionId(), 0));
        assertTrue(manage.action(action, player, item, crafter, manage.getActionId(), 0));

        assertEquals(1, factory.getCommunicator(gm).getBml().length);
        assertEquals(0, factory.getCommunicator(player).getBml().length);
        sendQuestion();
        assertThat(gm, bmlEqual());
    }

    @Test
    public void testActionNotCrafter() {
        Action action = mock(Action.class);
        Creature notCrafter = factory.createNewCreature();
        Item writ = factory.createWritFor(player, notCrafter);
        assertTrue(manage.action(action, gm, notCrafter, manage.getActionId(), 0));
        assertTrue(manage.action(action, player, notCrafter, manage.getActionId(), 0));
        assertTrue(manage.action(action, gm, writ, notCrafter, manage.getActionId(), 0));
        assertTrue(manage.action(action, player, writ, notCrafter, manage.getActionId(), 0));

        assertEquals(0, factory.getCommunicator(gm).getBml().length);
        assertEquals(0, factory.getCommunicator(player).getBml().length);
    }

    @Test
    public void testActionWrongActionId() {
        Action action = mock(Action.class);
        assertTrue(manage.action(action, gm, crafter, (short)(manage.getActionId() + 1), 0));
        assertTrue(manage.action(action, player, crafter, (short)(manage.getActionId() + 1), 0));

        assertEquals(0, factory.getCommunicator(gm).getBml().length);
        assertEquals(0, factory.getCommunicator(player).getBml().length);
    }

    @Test
    public void testActionGmPowerRequired() {
        assert CrafterMod.gmManagePowerRequired() == 2;
        Action action = mock(Action.class);
        assertTrue(manage.action(action, gm, crafter, manage.getActionId(), 0));

        assertEquals(1, factory.getCommunicator(gm).getBml().length);
        sendQuestion();
        assertThat(gm, bmlEqual());
    }

    @Test
    public void testActionGmPowerRequiredTooHigh() {
        Properties properties = new Properties();
        properties.setProperty("gm_manage_power_required", "10");
        new CrafterMod().configure(properties);
        Action action = mock(Action.class);
        assertTrue(manage.action(action, gm, crafter, manage.getActionId(), 0));

        assertEquals(0, factory.getCommunicator(gm).getBml().length);
    }
}
