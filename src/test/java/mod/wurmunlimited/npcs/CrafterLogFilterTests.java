package mod.wurmunlimited.npcs;

import com.wurmonline.server.TimeConstants;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CrafterLogFilterTests {
    CrafterLogFilter filter;
    final String msg = "test";

    @BeforeEach
    void setUp() {
        filter = new CrafterLogFilter(false);
    }

    @Test
    void testNotRepeatedMessage() {
        assertTrue(filter.isLoggable(new LogRecord(Level.INFO, msg + "1")));
        assertTrue(filter.isLoggable(new LogRecord(Level.INFO, msg + "2")));
    }

    @Test
    void testRepeatedMessage() {
        assertTrue(filter.isLoggable(new LogRecord(Level.INFO, msg)));
        assertFalse(filter.isLoggable(new LogRecord(Level.INFO, msg)));
    }

    @Test
    void testRepeatedMessageAfterTime() throws NoSuchFieldException, IllegalAccessException {
        assertTrue(filter.isLoggable(new LogRecord(Level.INFO, msg)));
        Instant start = Instant.now();

        for (int i = 1; i <= 15; ++i) {
            assertFalse(filter.isLoggable(new LogRecord(Level.INFO, msg)));
            ReflectionUtil.setPrivateField(filter, CrafterLogFilter.class.getDeclaredField("clock"),
                    Clock.fixed(start.plusMillis(TimeConstants.MINUTE_MILLIS * i), ZoneOffset.UTC));
        }

        assertTrue(filter.isLoggable(new LogRecord(Level.INFO, msg)));
    }

    @Test
    void testAlternatingMessages() {
        assertTrue(filter.isLoggable(new LogRecord(Level.INFO, msg + "1")));
        assertFalse(filter.isLoggable(new LogRecord(Level.INFO, msg + "1")));
        assertTrue(filter.isLoggable(new LogRecord(Level.INFO, msg + "2")));
        assertFalse(filter.isLoggable(new LogRecord(Level.INFO, msg + "2")));
    }
}
