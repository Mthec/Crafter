package mod.wurmunlimited.npcs;


import com.wurmonline.server.Message;
import com.wurmonline.server.Server;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.NoSuchActionException;
import com.wurmonline.server.creatures.CrafterTradeHandler;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.ai.CreatureAI;
import com.wurmonline.server.creatures.ai.CreatureAIData;
import com.wurmonline.server.creatures.ai.PathTile;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.players.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class CrafterAI extends CreatureAI {
    private static final Logger logger = Logger.getLogger(CrafterAI.class.getName());
    static final Set<Creature> allCrafters = new HashSet<>();
    public static final Map<Creature, Item> assignedForges = new HashMap<>();
    private Field tradeHandler;

    public CrafterAI() {
        try {
            tradeHandler = Creature.class.getDeclaredField("tradeHandler");
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        tradeHandler.setAccessible(true);
    }

    @Override
    public boolean pollCreature(@NotNull Creature c, long delta) {
        boolean isDead = super.pollCreature(c, delta);
        try {
            if (tradeHandler != null) {
                CrafterTradeHandler handler = (CrafterTradeHandler)tradeHandler.get(c);
                if (handler != null)
                    handler.balance();
            }
        } catch (IllegalAccessException e) {
            tradeHandler = null;
            logger.warning("TradeHandler field not found, this really shouldn't happen.  Future warnings will be suppressed.");
            e.printStackTrace();
        }

        if (!isDead && !c.isTrading() && !c.isFighting()) {// TODO - Stamina, including letting crafter put items in forge whilst waiting for stamina. && c.getStatus().calcStaminaPercent() == 100) {
            // TODO - Creatures don't seem to update stamina as frequently as players.
            try {
                Action action = c.getCurrentAction();
                CrafterMod.getCrafterLogger(c).info(action.getActionString());
            } catch (NoSuchActionException ignored) {
                CrafterAIData data = ((CrafterAIData)c.getCreatureAIData());
                if (data.canAction)
                    data.sendNextAction();
            }
        }
        return isDead;
    }

    @Override
    protected boolean pollMovement(@NotNull Creature creature, long delta) {
        CrafterAIData data = (CrafterAIData)creature.getCreatureAIData();
        PathTile path = data.getWorkLocation();
        if (path != null) {
            if (creature.getStatus().getPath() == null) {
                creature.startPathingToTile(path);
            } else {
                pathedMovementTick(creature);
                if (creature.getStatus().getPath().isEmpty()) {
                    creature.getStatus().setPath(null);
                    creature.getStatus().setMoving(false);
                    data.arrivedAtWorkLocation();
                }
            }
        }
        return false;
    }

    @Override
    protected boolean pollAttack(@NotNull Creature creature, long delta) {
        return false;
    }

    @Override
    protected boolean pollBreeding(@NotNull Creature creature, long delta) {
        return false;
    }

    @Override
    public CreatureAIData createCreatureAIData() {
        return new CrafterAIData();
    }

    @Override
    public void creatureCreated(@NotNull Creature creature) {
    }

    static void sendCrafterStatusTo(Player player) {
        for (Creature crafter : allCrafters) {
            String status = ((CrafterAIData)crafter.getCreatureAIData()).getStatusFor(player);
            if (status != null) {
                Message mess = new Message(player, (byte)3, "Crafters", "<" + crafter.getName() + "> " + status);
                mess.setReceiver(player.getWurmId());
                Server.getInstance().addMessage(mess);
            }
        }
    }
}
