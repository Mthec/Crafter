package com.wurmonline.server.behaviours;

import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.FakeCommunicator;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.questions.CrafterHireQuestion;
import mod.wurmunlimited.Assert;
import mod.wurmunlimited.npcs.CrafterMod;
import mod.wurmunlimited.npcs.CrafterTest;
import mod.wurmunlimited.npcs.CrafterType;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static mod.wurmunlimited.Assert.receivedMessageContaining;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PlaceCrafterActionTests extends CrafterTest {
    private static final String actionString = "Crafter";
    private Action action;
    private Player gm;
    private Item wand;
    private short actionId;

    @BeforeEach
    private void create() throws Exception {
        action = mock(Action.class);
        when(action.getActionString()).thenAnswer(i -> actionString);
        actionId = menu.getActionId();
        gm = factory.createNewPlayer();
        gm.setPower((byte)2);
        wand = factory.createNewItem(ItemList.wandGM);
    }

    // GetBehavioursFor

    @Test
    void testCorrectBehaviourReceived() {
        List<ActionEntry> entries = menu.getBehavioursFor(gm, wand, 0, 0, true, 0);
        assertEquals(2, entries.size());
        assertEquals("Place Npc", entries.get(0).getActionString());
        assertEquals("Crafter", entries.get(1).getActionString());
    }

    @Test
    void testPlayersDoNotGetOption() {
        Player player = factory.createNewPlayer();
        assert player.getPower() < 2;
        List<ActionEntry> entries = menu.getBehavioursFor(player, wand, 0, 0, true, 0);
        assertNull(entries);
    }

    @Test
    void testWandRequired() {
        Item item = factory.createNewItem();
        assert !item.isWand();
        List<ActionEntry> entries = menu.getBehavioursFor(gm, item, 0, 0, true, 0);
        assertNull(entries);
    }

    // Action

    @Test
    void testCrafterHireQuestionReceived() throws NoSuchFieldException, IllegalAccessException {
        assertTrue(menu.action(action, gm, wand, 0, 0, true,  0, 0, actionId, 0f));
        assertEquals(1, factory.getCommunicator(gm).getBml().length);
        //noinspection OptionalGetWithoutIsPresent
        new CrafterHireQuestion(gm, gm.getInventory().getItems().stream().filter(i -> i.getTemplateId() == CrafterMod.getContractTemplateId()).findAny().get().getWurmId()).sendQuestion();

        // To account for random gender.
        String[] bml = factory.getCommunicator(gm).getBml();
        List<String> fixed = new ArrayList<>();
        for (String b : bml) {
            fixed.add(b.replace(";selected=\"true\"", ""));
        }
        ReflectionUtil.setPrivateField(factory.getCommunicator(gm), FakeCommunicator.class.getDeclaredField("bml"), fixed);

        assertThat(gm, Assert.bmlEqual());
    }

    @Test
    void testPlayersDoNotReceiveBML() {
        Player player = factory.createNewPlayer();
        assert player.getPower() < 2;
        assertTrue(menu.action(action, player, wand, 0, 0, true, 0, 0, actionId, 0f));
        assertEquals(0, factory.getCommunicator(gm).getBml().length);
    }

    @Test
    void testWandRequiredForBML() {
        Item item = factory.createNewItem();
        assert !item.isWand();
        assertTrue(menu.action(action, gm, item, 0, 0, true, 0, 0, actionId, 0f));
        assertEquals(0, factory.getCommunicator(gm).getBml().length);
    }

    @Test
    void testIncorrectTileInformation() {
        assertTrue(menu.action(action, gm, wand, -250, -250, true, 0, 0, actionId, 0f));
        assertEquals(1, factory.getCommunicator(gm).getMessages().length);
        assertEquals(0, factory.getCommunicator(gm).getBml().length);
        assertThat(gm, receivedMessageContaining("not be located"));
    }

    @Test
    void testAlreadyUsedContractCreatesNew() {
        Creature crafter = factory.createNewCrafter(gm, new CrafterType(CrafterType.allArmour), 50);
        Item contract = factory.createNewItem(CrafterMod.getContractTemplateId());
        contract.setData(crafter.getWurmId());
        gm.getInventory().insertItem(contract);
        assert gm.getInventory().getItemCount() == 1;
        assertTrue(menu.action(action, gm, wand, 0, 0, true, 0, 0, actionId, 0f));
        assertEquals(0, factory.getCommunicator(gm).getMessages().length);
        assertEquals(1, factory.getCommunicator(gm).getBml().length);
        assertEquals(2, gm.getInventory().getItemCount());
        assertTrue(gm.getInventory().getItems().stream().allMatch(i -> i.getTemplateId() == CrafterMod.getContractTemplateId()));
    }

    @Test
    void testAlreadyEmptyContractDoesNotCreateNew() {
        Item contract = factory.createNewItem(CrafterMod.getContractTemplateId());
        gm.getInventory().insertItem(contract);
        assert contract.getData() == -1;
        assert gm.getInventory().getItemCount() == 1;
        assertTrue(menu.action(action, gm, wand, 0, 0, true, 0, 0, actionId, 0f));
        assertEquals(0, factory.getCommunicator(gm).getMessages().length);
        assertEquals(1, factory.getCommunicator(gm).getBml().length);
        assertEquals(1, gm.getInventory().getItemCount());
        assertTrue(gm.getInventory().getItems().stream().allMatch(i -> i.getTemplateId() == CrafterMod.getContractTemplateId()));
    }
}
