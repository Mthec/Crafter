package mod.wurmunlimited.npcs;

import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.junit.Rule;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

public abstract class GlobalRestrictionsFileWrapper {
    @Rule
    protected TemporaryFolder temp = new TemporaryFolder();
    private Path oldRestrictedMaterialsPath;
    private Path oldBlockedItemsPath;

    @BeforeEach
    protected void setUp() throws Exception {
        oldRestrictedMaterialsPath = CrafterMod.globalRestrictionsPath;
        oldBlockedItemsPath = CrafterMod.globalBlockedItemsPath;
        List<Byte> restrictedMaterials = ReflectionUtil.getPrivateField(null, CrafterMod.class.getDeclaredField("restrictedMaterials"));
        restrictedMaterials.clear();
        Set<Integer> blockedItems = ReflectionUtil.getPrivateField(null, CrafterMod.class.getDeclaredField("blockedItems"));
        blockedItems.clear();
        temp.create();
        String folder = temp.newFolder("mods", "crafter").toString();
        CrafterMod.globalRestrictionsPath = Paths.get(folder, "global_restrictions");
        CrafterMod.globalBlockedItemsPath = Paths.get(folder, "blocked_items");
    }

    @AfterEach
    protected void tearDown() throws NoSuchFieldException, IllegalAccessException {
        List<Byte> restrictedMaterials = ReflectionUtil.getPrivateField(null, CrafterMod.class.getDeclaredField("restrictedMaterials"));
        restrictedMaterials.clear();
        Set<Integer> blockedItems = ReflectionUtil.getPrivateField(null, CrafterMod.class.getDeclaredField("blockedItems"));
        blockedItems.clear();
        temp.delete();
        CrafterMod.globalRestrictionsPath = oldRestrictedMaterialsPath;
        CrafterMod.globalBlockedItemsPath = oldBlockedItemsPath;
    }

    @BeforeAll
    @AfterAll
    private static void clean() {
        CrafterTest.cleanLogs();
    }
}
