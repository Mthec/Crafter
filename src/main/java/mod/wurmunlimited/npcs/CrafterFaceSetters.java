package mod.wurmunlimited.npcs;

import com.wurmonline.server.TimeConstants;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.players.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class CrafterFaceSetters {
    private final Map<Long, Creature> transactions = new HashMap<>();
    private final Map<Long, TimePlayer> transactionStartTimes = new HashMap<>();
    private static final long firstId = -1024;

    private static class TimePlayer {
        private final long time;
        private final Player player;

        private TimePlayer(long time, Player player) {
            this.time = time;
            this.player = player;
        }
    }

    public static class TooManyTransactionsException extends Exception {
        TooManyTransactionsException() {
            super("Too many faces are being changed at the moment, this shouldn't happen unless the server is ridiculously busy.");
        }
    }

    public @Nullable Creature retrieveCrafterOrNull(Player creator, long id) {
        TimePlayer timePlayer = transactionStartTimes.get(id);
        if (timePlayer != null && timePlayer.player == creator) {
            transactionStartTimes.remove(id);
            return transactions.remove(id);
        }

        return null;
    }

    public long createIdFor(Creature crafter, Player creator) throws TooManyTransactionsException {
        long nextId = firstId;

        long time = System.currentTimeMillis() - TimeConstants.HOUR_MILLIS;
        Iterator<Map.Entry<Long, TimePlayer>> iter = transactionStartTimes.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Long, TimePlayer> entry = iter.next();
            if (entry.getValue().time < time) {
                transactions.remove(entry.getKey());
                iter.remove();
            }
        }

        while (transactions.containsKey(nextId)) {
            ++nextId;

            if (nextId >= -10) {
                throw new TooManyTransactionsException();
            }
        }

        transactions.put(nextId, crafter);
        transactionStartTimes.put(nextId, new TimePlayer(System.currentTimeMillis(), creator));

        return nextId;
    }
}
