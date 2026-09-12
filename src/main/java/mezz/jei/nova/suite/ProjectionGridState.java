package mezz.jei.nova.suite;

import java.util.BitSet;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/** Whole-grid compatibility: never combine a new ghost recipe with unrelated real inputs. */
public final class ProjectionGridState {
    private ProjectionGridState() {}
    public static <T> BitSet conflicts(List<T> actual, List<T> expected,
            Predicate<T> empty, BiPredicate<T, T> same) {
        if (actual.size() != 9 || expected.size() != 9) throw new IllegalArgumentException("Expected nine slots");
        BitSet conflicts = new BitSet(9);
        for (int i = 0; i < 9; i++) {
            if (!empty.test(actual.get(i)) && !same.test(actual.get(i), expected.get(i))) conflicts.set(i);
        }
        return conflicts;
    }
}
