package jp.minecraftcooks.core.recipe;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** 順番を無視して、種類と個数が同じかを比べる。 */
final class Multiset {
    private Multiset() {}

    static Map<String, Integer> of(Collection<String> items) {
        Map<String, Integer> m = new HashMap<>();
        for (String s : items) m.merge(s, 1, Integer::sum);
        return m;
    }

    static boolean same(Collection<String> a, Collection<String> b) {
        return a.size() == b.size() && of(a).equals(of(b));
    }
}
