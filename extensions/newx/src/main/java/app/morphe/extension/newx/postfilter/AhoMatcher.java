package app.morphe.extension.newx.postfilter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Multi-pattern substring search in O(text length) regardless of phrase
 * count. Transitions are frozen to parallel arrays (tiny out-degree, no
 * boxing) after a map-based build. Thread-safe after construction.
 */
final class AhoMatcher implements PhraseMatcher {
    private final char[][] symbols;
    private final int[][] targets;
    private final int[] failures;
    private final boolean[] outputs;

    AhoMatcher(List<String> phrases) {
        List<Map<Character, Integer>> next = new ArrayList<>();
        List<Boolean> output = new ArrayList<>();
        next.add(new HashMap<>());
        output.add(false);

        for (String phrase : phrases) {
            if (phrase.isEmpty()) {
                output.set(0, true);
                continue;
            }
            int state = 0;
            for (int index = 0; index < phrase.length(); index++) {
                char symbol = phrase.charAt(index);
                Integer target = next.get(state).get(symbol);
                if (target == null) {
                    target = next.size();
                    next.get(state).put(symbol, target);
                    next.add(new HashMap<>());
                    output.add(false);
                }
                state = target;
            }
            output.set(state, true);
        }

        int[] fail = new int[next.size()];
        Queue<Integer> queue = new ArrayDeque<>();
        for (int target : next.get(0).values()) {
            fail[target] = 0;
            queue.add(target);
        }
        while (!queue.isEmpty()) {
            int state = queue.remove();
            for (Map.Entry<Character, Integer> transition : next.get(state).entrySet()) {
                char symbol = transition.getKey();
                int target = transition.getValue();
                int fallback = fail[state];
                while (fallback != 0 && !next.get(fallback).containsKey(symbol)) {
                    fallback = fail[fallback];
                }
                fail[target] = next.get(fallback).getOrDefault(symbol, 0);
                if (output.get(fail[target])) output.set(target, true);
                queue.add(target);
            }
        }

        symbols = new char[next.size()][];
        targets = new int[next.size()][];
        for (int state = 0; state < next.size(); state++) {
            Map<Character, Integer> edges = next.get(state);
            symbols[state] = new char[edges.size()];
            targets[state] = new int[edges.size()];
            int edge = 0;
            for (Map.Entry<Character, Integer> transition : edges.entrySet()) {
                symbols[state][edge] = transition.getKey();
                targets[state][edge] = transition.getValue();
                edge++;
            }
        }
        failures = fail;
        outputs = new boolean[output.size()];
        for (int index = 0; index < output.size(); index++) {
            outputs[index] = output.get(index);
        }
    }

    @Override public boolean isEmpty() { return false; }

    @Override public boolean matches(CharSequence text) {
        if (outputs[0]) return true;
        int state = 0;
        for (int index = 0; index < text.length(); index++) {
            char symbol = text.charAt(index);
            int target;
            for (;;) {
                target = transition(state, symbol);
                if (target >= 0 || state == 0) break;
                state = failures[state];
            }
            state = target >= 0 ? target : 0;
            if (outputs[state]) return true;
        }
        return false;
    }

    private int transition(int state, char symbol) {
        char[] edgeSymbols = symbols[state];
        for (int edge = 0; edge < edgeSymbols.length; edge++) {
            if (edgeSymbols[edge] == symbol) return targets[state][edge];
        }
        return -1;
    }
}
