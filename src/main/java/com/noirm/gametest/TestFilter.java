package com.noirm.gametest;

import com.noirm.Noirm;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.TestFunction;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The quick test runner's filter ({@code tools/dev/test.sh hound}). When the system property {@code noirm.tests}
 * is set (comma-separated words), only the game tests that match one of the words run; unset, everything runs.
 * A word matches a test when it is part of the test's name, its batch name, or the name of its test class
 * ({@code hound} picks every test in {@code HoundGameTests}). Prefix a word with {@code =} for an exact test or
 * batch name ({@code =noirm_hound_spawn}). Applied by {@code mixin/GameTestRegistryMixin}.
 */
public final class TestFilter {
    public static final String PROPERTY = "noirm.tests";
    private static final String PACKAGE = "com.noirm.gametest.";

    private TestFilter() {}

    public static boolean active() {
        String p = System.getProperty(PROPERTY);
        return p != null && !p.isBlank();
    }

    public static List<String> words(String spec) {
        List<String> out = new ArrayList<>();
        if (spec == null) {
            return out;
        }
        for (String w : spec.split(",")) {
            w = w.trim().toLowerCase(Locale.ROOT);
            if (!w.isEmpty()) {
                out.add(w);
            }
        }
        return out;
    }

    /** The tests the property selects (a new list; the registry's own collection is left alone). */
    public static Collection<TestFunction> apply(Collection<TestFunction> all) {
        List<String> words = words(System.getProperty(PROPERTY));
        if (words.isEmpty()) {
            return all;
        }
        Set<String> byClass = methodsOfMatchingClasses(words);
        List<TestFunction> kept = new ArrayList<>();
        for (TestFunction f : all) {
            if (byClass.contains(f.getTestName().toLowerCase(Locale.ROOT)) || matches(words, f.getTestName(), f.getBatchName())) {
                kept.add(f);
            }
        }
        Noirm.LOGGER.info("[noirm.tests] filter {} selected {} of {} game tests", words, kept.size(), all.size());
        return kept;
    }

    /** Does a test with this name and batch match one of the words (class matches aside)? */
    public static boolean matches(List<String> words, String testName, String batchName) {
        String t = testName.toLowerCase(Locale.ROOT);
        String b = batchName == null ? "" : batchName.toLowerCase(Locale.ROOT);
        for (String w : words) {
            if (w.startsWith("=")) {
                String exact = w.substring(1);
                if (t.equals(exact) || b.equals(exact) || t.endsWith("." + exact)) {
                    return true;
                }
            } else if (t.contains(w) || b.contains(w)) {
                return true;
            }
        }
        return false;
    }

    /** Lower-case test names of every {@code @GameTest} method in a test class whose simple name contains a word. */
    private static Set<String> methodsOfMatchingClasses(List<String> words) {
        Set<String> names = new HashSet<>();
        for (String simple : GameTestRegistry.getAllTestClassNames()) {
            String lower = simple.toLowerCase(Locale.ROOT);
            boolean hit = false;
            for (String w : words) {
                if (!w.startsWith("=") && lower.contains(w) || w.startsWith("=") && lower.equals(w.substring(1))) {
                    hit = true;
                }
            }
            if (!hit) {
                continue;
            }
            try {
                Class<?> c = Class.forName(PACKAGE + simple);
                for (Method m : c.getDeclaredMethods()) {
                    if (m.isAnnotationPresent(GameTest.class)) {
                        String n = m.getName().toLowerCase(Locale.ROOT);
                        names.add(n);
                        names.add(lower + "." + n);
                    }
                }
            } catch (ClassNotFoundException e) {
                // a test class from another package or mod: only name and batch matches apply
            }
        }
        return names;
    }
}
