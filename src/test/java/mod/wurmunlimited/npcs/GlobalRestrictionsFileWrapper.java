package mod.wurmunlimited.npcs;

import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.junit.Rule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public abstract class GlobalRestrictionsFileWrapper {
    @Rule
    protected TemporaryFolder temp = new TemporaryFolder();
    private Path oldPath;

    @BeforeEach
    protected void setUp() throws Exception {
        oldPath = CrafterMod.globalRestrictionsPath;
        List<Byte> restrictedMaterials = ReflectionUtil.getPrivateField(null, CrafterMod.class.getDeclaredField("restrictedMaterials"));
        restrictedMaterials.clear();
        temp.create();
        CrafterMod.globalRestrictionsPath = Paths.get(temp.newFolder("mods", "crafter").toString(), "global_restrictions");
    }

    @AfterEach
    protected void tearDown() throws NoSuchFieldException, IllegalAccessException {
        List<Byte> restrictedMaterials = ReflectionUtil.getPrivateField(null, CrafterMod.class.getDeclaredField("restrictedMaterials"));
        restrictedMaterials.clear();
        temp.delete();
        CrafterMod.globalRestrictionsPath = oldPath;
    }
}
