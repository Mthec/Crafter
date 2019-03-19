package mod.wurmunlimited.npcs;

import com.wurmonline.server.MiscConstants;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.ItemTypes;
import com.wurmonline.shared.constants.CreatureTypes;
import com.wurmonline.shared.constants.ItemMaterials;
import org.gotti.wurmunlimited.modsupport.CreatureTemplateBuilder;
import org.gotti.wurmunlimited.modsupport.creatures.ModCreature;

public class CrafterTemplate implements ModCreature {
    private static int templateId;

    @Override
    public CreatureTemplateBuilder createCreateTemplateBuilder() {
        int[] types = new int[] {
                CreatureTypes.C_TYPE_INVULNERABLE,
                CreatureTypes.C_TYPE_HUMAN,
                CreatureTypes.C_TYPE_OPENDOORS
        };

        CreatureTemplateBuilder crafter = new CreatureTemplateBuilder(
            "mod.creature.crafter",
                "crafter",
                "A local crafter who will improve items for coin.",
                "model.creature.humanoid.human.player",
                types,
                (byte)0,
                (short)2,
                MiscConstants.SEX_MALE,
                (short)180,
                (short)20,
                (short)35,
                "sound.death.male", "sound.death.female", "sound.combat.hit.male", "sound.combat.hit.female",
                1.0f,
                1.0f,
                2.0f,
                0.0f,
                0.0f,
                0.0f,
                0.8f,
                1,
                new int[0],
                20,
                0,
                ItemMaterials.MATERIAL_MEAT_HUMAN
        );

        crafter.defaultSkills();
        crafter.baseCombatRating(70.0f);
        crafter.hasHands(true);
        templateId = crafter.getTemplateId();

        return crafter;
    }

    public static int getTemplateId() {
        return templateId;
    }

    public static boolean isCrafter(Creature creature) {
        return creature.getTemplate().getTemplateId() == templateId;
    }
}
