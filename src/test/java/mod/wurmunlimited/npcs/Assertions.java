package mod.wurmunlimited.npcs;

import com.google.common.base.Joiner;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.TempItem;
import com.wurmonline.server.items.TradingWindow;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Assertions {

    public static class HasDonateOption extends TypeSafeMatcher<TradingWindow> {

        @Override
        protected boolean matchesSafely(TradingWindow window) {
            for (Item item : window.getItems()) {
                if (item.getName().startsWith("Donate") && item instanceof TempItem)
                    return true;
            }
            return false;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("window to have a Donate option");
        }

        @Override
        public void describeMismatchSafely(TradingWindow window, Description description) {
            description.appendText(" only had the following options - " + Joiner.on(", ").join(Arrays.stream(window.getAllItems()).map(Item::getName).collect(Collectors.toList())));
        }
    }

    public static Matcher<TradingWindow> hasDonateOption() {
        return new HasDonateOption();
    }

    public static class HasMailOption extends TypeSafeMatcher<TradingWindow> {

        @Override
        protected boolean matchesSafely(TradingWindow window) {
            for (Item item : window.getItems()) {
                if (item.getName().startsWith("Mail") && item instanceof TempItem)
                    return true;
            }
            return false;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("window to have a Mail option");
        }

        @Override
        public void describeMismatchSafely(TradingWindow window, Description description) {
            description.appendText(" only had the following options - " + Joiner.on(", ").join(Arrays.stream(window.getAllItems()).map(Item::getName).collect(Collectors.toList())));
        }
    }

    public static Matcher<TradingWindow> hasMailOption() {
        return new HasMailOption();
    }

    public static class HasOptionsForQLUpTo extends TypeSafeMatcher<TradingWindow> {

        private static final Pattern qlValue = Pattern.compile(".* ([\\d.]+)ql$");
        private final float maxQl;
        private final Set<String> required;

        HasOptionsForQLUpTo(float maxQl) {
            this.maxQl = maxQl;
            assert maxQl >= 20;
            required = new HashSet<>();
            if (maxQl % 10 != 0)
                required.add(String.format("%.1f", maxQl));

            int current = (int)(maxQl - (maxQl % 10));
            while (current >= 20) {
                required.add(Integer.toString(current));
                current -= 10;
            }
        }

        @Override
        protected boolean matchesSafely(TradingWindow window) {
            // Minus 2 for Mail and Donate options.
            if (window.getItems().length - 2 != required.size())
                return false;
            for (Item item : window.getItems()) {
                java.util.regex.Matcher match = qlValue.matcher(item.getName());
                if (match.find()) {
                    if (!required.contains(match.group(1)))
                        return false;
                }
            }
            return true;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("window to have options up to " + maxQl + "ql");
        }

        @Override
        public void describeMismatchSafely(TradingWindow window, Description description) {
            description.appendText(" only had the following options - " + Joiner.on(", ").join(Arrays.stream(window.getAllItems()).map(Item::getName).collect(Collectors.toList())));
        }
    }

    public static Matcher<TradingWindow> hasOptionsForQLUpTo(float maxQl) {
        return new HasOptionsForQLUpTo(maxQl);
    }
}
