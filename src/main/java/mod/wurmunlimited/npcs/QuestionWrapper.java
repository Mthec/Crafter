package mod.wurmunlimited.npcs;

import com.wurmonline.server.players.Player;
import com.wurmonline.server.questions.CrafterBlockedItemsQuestion;
import com.wurmonline.server.questions.CrafterMaterialRestrictionQuestion;

public class QuestionWrapper {
    public static void materialRestrictionQuestion(Player player) {
        try {
            new CrafterMaterialRestrictionQuestion(player, null).sendQuestion();
        } catch (WorkBook.NoWorkBookOnWorker ignored) {}
    }

    public static void blockCrafterItems(Player player) {
        try {
            new CrafterBlockedItemsQuestion(player, null).sendQuestion();
        } catch (WorkBook.NoWorkBookOnWorker ignored) {}
    }
}
