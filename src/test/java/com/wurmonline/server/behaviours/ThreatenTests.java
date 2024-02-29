package com.wurmonline.server.behaviours;

import com.wurmonline.server.Servers;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.players.Player;
import mod.wurmunlimited.npcs.CrafterMod;
import mod.wurmunlimited.npcs.CrafterTest;
import org.gotti.wurmunlimited.modsupport.actions.ActionEntryBuilder;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

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
        when(Servers.localServer.isChallengeOrEpicServer()).thenReturn(true);
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
        Assertions.assertTrue(isEmpty(threaten.getBehavioursFor(foe, notCrafter)));
        Assertions.assertTrue(isEmpty(threaten.getBehavioursFor(friend, notCrafter)));
        Assertions.assertTrue(isEmpty(threaten.getBehavioursFor(foe, factory.createWritFor(foe, crafter), notCrafter)));
        Assertions.assertTrue(isEmpty(threaten.getBehavioursFor(friend, writ, notCrafter)));
    }
}
