package mod.wurmunlimited.npcs;

import com.wurmonline.server.Constants;
import com.wurmonline.server.behaviours.BehaviourDispatcher;
import com.wurmonline.server.behaviours.PlaceCrafterAction;
import com.wurmonline.server.behaviours.PlaceNpcMenu;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.CrafterTrade;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.players.Player;
import mod.wurmunlimited.CrafterObjectsFactory;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;

public class CrafterTest {
    protected CrafterObjectsFactory factory;
    protected Player player;
    protected Player owner;
    protected Creature crafter;
    protected Item tool;
    protected Item forge;
    protected CrafterTrade trade;
    protected static PlaceNpcMenu menu;
    private static boolean init = false;

    @BeforeEach
    protected void setUp() throws Exception {
        Constants.dbHost = ".";
        factory = new CrafterObjectsFactory();
        BehaviourDispatcher.reset();
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("skillCap"), 99.99999f);
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("basePrice"), 1);
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("removeDonationsAt"), Integer.MIN_VALUE);
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("maxItemQL"), 99.99999f);
        player = factory.createNewPlayer();
        tool = factory.createNewItem(ItemList.pickAxe);
        player.getInventory().insertItem(tool);
        owner = factory.createNewPlayer();
        crafter = factory.createNewCrafter(owner, new CrafterType(CrafterType.allMetal), 50);
        factory.createVillageFor(owner, crafter);
        forge = factory.createNewItem(ItemList.forge);
        setForgeWithoutPathing();

        Field field = CrafterMod.class.getDeclaredField("output");
        field.setAccessible(true);
        field.set(field.getType().asSubclass(Enum.class), field.getType().getDeclaredMethod("valueOf", String.class).invoke(null, "save_and_print"));

        if (!init) {
            new PlaceCrafterAction();
            menu = PlaceNpcMenu.register();
            init = true;
        }
    }

    @BeforeAll
    private static void cleanLogs() {
        try {
            //noinspection ResultOfMethodCallIgnored
            Files.walk(Paths.get(".")).filter(it -> (it.getFileName().toString().startsWith("worker") || it.getFileName().toString().startsWith("crafter_")) && it.getFileName().toString().endsWith("log"))
                    .forEach(it -> it.toFile().delete());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void setForgeWithoutPathing() {
        try {
            CrafterAIData data = (CrafterAIData)crafter.getCreatureAIData();
            ReflectionUtil.setPrivateField(data, CrafterAIData.class.getDeclaredField("forge"), forge);
            data.getWorkBook().setForge(forge);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }


    }
}
