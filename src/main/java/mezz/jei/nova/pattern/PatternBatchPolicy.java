package mezz.jei.nova.pattern;
import java.util.*;
/** One pattern per distinct validated recipe, independent of bookmark production amount. */
public final class PatternBatchPolicy {
    public enum Status { READY, INVALID, DUPLICATE }
    private PatternBatchPolicy() {}
    public static <T> List<Status> classify(List<T> recipes,Set<T> existing) {
        Set<T> seen=new HashSet<>(existing);List<Status> result=new ArrayList<>();
        for(T recipe:recipes) result.add(recipe==null ? Status.INVALID : seen.add(recipe) ? Status.READY : Status.DUPLICATE);
        return result;
    }
    public static boolean supportsTerminal(String name) {
        return "appeng.container.implementations.ContainerPatternTerm".equals(name) ||
            "com.glodblock.github.client.container.ContainerFluidPatternTerminal".equals(name);
    }
    /** RC exposes 81 storage slots but its native crafting getInputs uses only the first nine. */
    public static <T> T[] craftingGrid(T[] slots,boolean rc) {
        if(slots.length!=9 && !(rc && slots.length==81)) throw new IllegalStateException("Unsupported crafting slot layout: "+slots.length);
        return Arrays.copyOf(slots,9);
    }
    public static boolean canEncode(int count,int blanks,int freeSlots) {
        return count>0 && count<=64 && blanks>=count && freeSlots>=count;
    }
}
