package fr.skynex.worldx.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConditionEvaluator {

    private static final Pattern LEVEL_PATTERN = Pattern.compile("level\\s*(>=|<=|>|<|==|!=)\\s*(\\d+)");
    private static final Pattern TIME_PATTERN = Pattern.compile("time\\s*(>=|<=|>|<|==|!=)\\s*(\\d+)");
    private static final Pattern WORLD_AGE_PATTERN = Pattern.compile("world_age\\s*(>=|<=|>|<|==|!=)\\s*(\\d+)");
    private static final Pattern HEALTH_PATTERN = Pattern.compile("health\\s*(>=|<=|>|<|==|!=)\\s*([0-9.]+)");
    private static final Pattern GAMEMODE_PATTERN = Pattern.compile("gamemode\\s*(==|!=)\\s*([a-zA-Z]+)");
    private static final Pattern WEATHER_PATTERN = Pattern.compile("weather\\s*(==|!=)\\s*([a-zA-Z]+)");
    private static final Pattern BIOME_PATTERN = Pattern.compile("biome\\s*(==|!=)\\s*([a-zA-Z0-9_:-]+)");
    private static final Pattern GROUP_PATTERN = Pattern.compile("group\\s*=\\s*([a-zA-Z0-9_-]+)");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("placeholder\\s*(%[^%]+%)\\s*(==|!=)\\s*(.+)");

    public static boolean evaluate(Player player, String expression) {
        long start = System.nanoTime();
        try {
            return evaluateInternal(player, expression);
        } finally {
            long elapsed = System.nanoTime() - start;
            fr.skynex.worldx.WorldX plugin = fr.skynex.worldx.WorldX.getInstance();
            if (plugin != null && plugin.getRegionProfiler().isActive()) {
                plugin.getRegionProfiler().record("Condition: " + expression, elapsed);
            }
        }
    }

    private static boolean evaluateInternal(Player player, String expression) {
        if (player == null) return false;

        expression = expression.trim();
        String result;
        String condition;
        boolean isIf;

        if (expression.contains(" if ")) {
            int idx = expression.indexOf(" if ");
            result = expression.substring(0, idx).trim();
            condition = expression.substring(idx + 4).trim();
            isIf = true;
        } else if (expression.contains(" unless ")) {
            int idx = expression.indexOf(" unless ");
            result = expression.substring(0, idx).trim();
            condition = expression.substring(idx + 8).trim();
            isIf = false;
        } else {
            // Default flat value
            return expression.equalsIgnoreCase("allow");
        }

        boolean isResultAllow = result.equalsIgnoreCase("allow");
        boolean isConditionTrue = evaluateCondition(player, condition);

        if (isIf) {
            return isConditionTrue ? isResultAllow : !isResultAllow;
        } else {
            return isConditionTrue ? !isResultAllow : isResultAllow;
        }
    }

    private static boolean evaluateCondition(Player player, String condition) {
        ExpressionParser parser = new ExpressionParser(player, condition);
        return parser.parse();
    }

    private static boolean evaluateSimpleCondition(Player player, String subExpr) {
        String condLower = subExpr.toLowerCase();

        // 1. Group Check: group=vip
        Matcher groupMatcher = GROUP_PATTERN.matcher(condLower);
        if (groupMatcher.find()) {
            String groupName = groupMatcher.group(1);
            return player.hasPermission("group." + groupName);
        }

        // 2. Level Check: level >= 10
        Matcher levelMatcher = LEVEL_PATTERN.matcher(condLower);
        if (levelMatcher.find()) {
            String op = levelMatcher.group(1);
            int val = Integer.parseInt(levelMatcher.group(2));
            int playerLevel = player.getLevel();
            return compareInt(playerLevel, op, val);
        }

        // 3. Time Check: time >= 13000
        Matcher timeMatcher = TIME_PATTERN.matcher(condLower);
        if (timeMatcher.find()) {
            String op = timeMatcher.group(1);
            long val = Long.parseLong(timeMatcher.group(2));
            long time = player.getWorld().getTime();
            return compareLong(time, op, val);
        }

        // 4. World Age Check: world_age >= 24000
        Matcher ageMatcher = WORLD_AGE_PATTERN.matcher(condLower);
        if (ageMatcher.find()) {
            String op = ageMatcher.group(1);
            long val = Long.parseLong(ageMatcher.group(2));
            long age = player.getWorld().getFullTime();
            return compareLong(age, op, val);
        }

        // 5. Health Check: health <= 10.0
        Matcher healthMatcher = HEALTH_PATTERN.matcher(condLower);
        if (healthMatcher.find()) {
            String op = healthMatcher.group(1);
            double val = Double.parseDouble(healthMatcher.group(2));
            double health = player.getHealth();
            return compareDouble(health, op, val);
        }

        // 6. Gamemode Check: gamemode == creative
        Matcher gmMatcher = GAMEMODE_PATTERN.matcher(condLower);
        if (gmMatcher.find()) {
            String op = gmMatcher.group(1);
            String expected = gmMatcher.group(2);
            String current = player.getGameMode().toString().toLowerCase();
            return op.equals("==") ? current.equals(expected) : !current.equals(expected);
        }

        // 7. Weather Check: weather == storm
        Matcher weatherMatcher = WEATHER_PATTERN.matcher(condLower);
        if (weatherMatcher.find()) {
            String op = weatherMatcher.group(1);
            String expected = weatherMatcher.group(2);
            String current = "clear";
            if (player.getWorld().isThundering()) {
                current = "thunder";
            } else if (player.getWorld().hasStorm()) {
                current = "storm";
            }
            return op.equals("==") ? current.equals(expected) : !current.equals(expected);
        }

        // 8. Biome Check: biome == plains
        Matcher biomeMatcher = BIOME_PATTERN.matcher(condLower);
        if (biomeMatcher.find()) {
            String op = biomeMatcher.group(1);
            String expected = biomeMatcher.group(2);
            String current = player.getLocation().getBlock().getBiome().getKey().getKey().toLowerCase();
            if (current.contains(":")) {
                current = current.substring(current.indexOf(':') + 1);
            }
            if (expected.contains(":")) {
                expected = expected.substring(expected.indexOf(':') + 1);
            }
            return op.equals("==") ? current.equals(expected) : !current.equals(expected);
        }

        // 9. Placeholder Check (case-sensitive)
        Matcher papiMatcher = PLACEHOLDER_PATTERN.matcher(subExpr);
        if (papiMatcher.find()) {
            String placeholder = papiMatcher.group(1);
            String op = papiMatcher.group(2);
            String expected = papiMatcher.group(3).trim();
            String resolved = resolvePlaceholder(player, placeholder).trim();

            if (op.equals("==")) {
                return resolved.equalsIgnoreCase(expected);
            } else if (op.equals("!=")) {
                return !resolved.equalsIgnoreCase(expected);
            }
        }

        return false;
    }

    private static boolean compareInt(int a, String op, int b) {
        return switch (op) {
            case ">" -> a > b;
            case ">=" -> a >= b;
            case "<" -> a < b;
            case "<=" -> a <= b;
            case "==" -> a == b;
            case "!=" -> a != b;
            default -> false;
        };
    }

    private static boolean compareLong(long a, String op, long b) {
        return switch (op) {
            case ">" -> a > b;
            case ">=" -> a >= b;
            case "<" -> a < b;
            case "<=" -> a <= b;
            case "==" -> a == b;
            case "!=" -> a != b;
            default -> false;
        };
    }

    private static boolean compareDouble(double a, String op, double b) {
        return switch (op) {
            case ">" -> a > b;
            case ">=" -> a >= b;
            case "<" -> a < b;
            case "<=" -> a <= b;
            case "==" -> Math.abs(a - b) < 0.001;
            case "!=" -> Math.abs(a - b) >= 0.001;
            default -> false;
        };
    }

    private static String resolvePlaceholder(Player player, String placeholder) {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                Class<?> clazz = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                Method m = clazz.getMethod("setPlaceholders", Player.class, String.class);
                return (String) m.invoke(null, player, placeholder);
            } catch (Exception ignored) {}
        }
        return placeholder; // Fallback to raw string
    }

    private static class ExpressionParser {
        private final Player player;
        private final String input;
        private int pos = 0;

        public ExpressionParser(Player player, String input) {
            this.player = player;
            this.input = input;
        }

        private char peek() {
            if (pos >= input.length()) return '\0';
            return input.charAt(pos);
        }

        private char next() {
            if (pos >= input.length()) return '\0';
            return input.charAt(pos++);
        }

        private void skipWhitespace() {
            while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
                pos++;
            }
        }

        public boolean parse() {
            boolean result = parseOr();
            skipWhitespace();
            return result;
        }

        private boolean parseOr() {
            boolean left = parseAnd();
            while (true) {
                skipWhitespace();
                if (pos + 1 < input.length() && input.charAt(pos) == '|' && input.charAt(pos + 1) == '|') {
                    pos += 2;
                    boolean right = parseAnd();
                    left = left || right;
                } else {
                    break;
                }
            }
            return left;
        }

        private boolean parseAnd() {
            boolean left = parseNot();
            while (true) {
                skipWhitespace();
                if (pos + 1 < input.length() && input.charAt(pos) == '&' && input.charAt(pos + 1) == '&') {
                    pos += 2;
                    boolean right = parseNot();
                    left = left && right;
                } else {
                    break;
                }
            }
            return left;
        }

        private boolean parseNot() {
            skipWhitespace();
            if (peek() == '!') {
                next();
                return !parseNot();
            }
            return parsePrimary();
        }

        private boolean parsePrimary() {
            skipWhitespace();
            if (peek() == '(') {
                next(); // consume '('
                boolean val = parseOr();
                skipWhitespace();
                if (peek() == ')') {
                    next(); // consume ')'
                }
                return val;
            }

            // Otherwise, it's a simple condition
            int start = pos;
            while (pos < input.length()) {
                char c = peek();
                if (c == ')' || c == '(' || c == '!' || 
                    (pos + 1 < input.length() && ( (c == '&' && input.charAt(pos + 1) == '&') || (c == '|' && input.charAt(pos + 1) == '|') ))) {
                    break;
                }
                next();
            }
            String subExpr = input.substring(start, pos).trim();
            return evaluateSimpleCondition(player, subExpr);
        }
    }
}
