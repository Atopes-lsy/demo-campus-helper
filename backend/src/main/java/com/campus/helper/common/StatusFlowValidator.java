package com.campus.helper.common;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class StatusFlowValidator {

    private static final Map<Integer, Set<Integer>> TRANSITIONS;

    static {
        Map<Integer, Set<Integer>> map = new HashMap<>();
        map.put(0, Set.of(1, 6));
        map.put(1, Set.of(2, 6));
        map.put(2, Set.of(3));
        map.put(3, Set.of(4));
        map.put(4, Set.of(5));
        map.put(5, Collections.emptySet());
        map.put(6, Collections.emptySet());
        TRANSITIONS = Collections.unmodifiableMap(map);
    }

    private StatusFlowValidator() {
    }

    public static boolean canTransition(Integer from, Integer to) {
        Set<Integer> allowed = TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    public static void validate(Integer from, Integer to) {
        if (!canTransition(from, to)) {
            throw new BusinessException(400, "非法的状态流转: " + from + " -> " + to);
        }
    }
}
