package mod.wurmunlimited.npcs;

import com.wurmonline.server.TimeConstants;

import java.time.Clock;
import java.util.logging.Filter;
import java.util.logging.LogRecord;

public class CrafterLogFilter implements Filter {
    private String lastMessage = "";
    private long lastRepeat = 0;
    private final Clock clock = Clock.systemUTC();
    private final boolean why;

    // It just doesn't make any sense!  Why does ConsoleHandler repeat if true, but FileHandler doesn't?
    public CrafterLogFilter(boolean why) {
        this.why = why;
    }

    @Override
    public boolean isLoggable(LogRecord record) {
        String message = record.getMessage();
        if (message.equals(lastMessage)) {
            if (lastRepeat == 0) {
                lastRepeat = clock.millis();
                return why;
            }
            if (clock.millis() - lastRepeat < TimeConstants.FIFTEEN_MINUTES_MILLIS) {
                return false;
            } else {
                lastRepeat = System.currentTimeMillis();
                return true;
            }
        } else {
            lastRepeat = 0;
            lastMessage = message;
            return true;
        }
    }
}
