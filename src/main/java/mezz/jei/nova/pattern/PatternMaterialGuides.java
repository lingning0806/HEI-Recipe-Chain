package mezz.jei.nova.pattern;

import java.util.*;

/** Bookmarked per-recipe quantities, matched by the complete alternative family, not overlap. */
public final class PatternMaterialGuides<K> {
    private static final class Guide<K> {
        final K selected; long remaining;
        Guide(K selected,long remaining) {this.selected=selected;this.remaining=remaining;}
    }
    private final Map<Set<K>,List<Guide<K>>> families=new HashMap<>();
    public void add(Collection<K> family,K selected,long amount) {
        if(amount<=0 || !family.contains(selected)) throw new IllegalArgumentException("Invalid bookmarked material");
        families.computeIfAbsent(new HashSet<>(family),ignored->new ArrayList<>()).add(new Guide<>(selected,amount));
    }
    public K take(Collection<K> family) {
        List<Guide<K>> guides=families.get(new HashSet<>(family));
        if(guides!=null) for(Guide<K> guide:guides) if(guide.remaining>0) {guide.remaining--;return guide.selected;}
        throw new IllegalArgumentException("Recipe slot no longer matches bookmarked inputs");
    }
}
