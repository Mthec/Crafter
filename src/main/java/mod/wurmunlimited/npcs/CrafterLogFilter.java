package mod.wurmunlimited.npcs;

import com.wurmonline.server.TimeConstants;

import java.util.logging.Filter;
import java.util.logging.LogRecord;

public class CrafterLogFilter implements Filter {
    private String lastMessage = "";
    private long lastRepeat = 0;

    @Override
    public boolean isLoggable(LogRecord record) {
        String message = record.getMessage();
        if (message.equals(lastMessage)) {
            if (lastRepeat == 0) {
                lastRepeat = System.currentTimeMillis();
            }
            if (System.currentTimeMillis() - lastRepeat < TimeConstants.FIFTEEN_MINUTES_MILLIS) {
                return false;
            } else
                lastRepeat = System.currentTimeMillis();
        } else {
            lastRepeat = 0;
        }

        lastMessage = message;
        return true;
    }
}
