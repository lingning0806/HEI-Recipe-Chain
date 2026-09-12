package mezz.jei.nova.ae2;

/** Bounded transport batching; native crafting still runs once per operation. */
public final class Ae2Batch {
    private Ae2Batch() {}
    public static boolean canAppend(long current,long next,long output) {
        return output>0 && current>0 && next>0 && current%output==0 && next%output==0 &&
            current/output<=64 && next/output<=64-current/output;
    }
    public static int nativeStackOperations(int outputCount,int stackLimit,int remaining) {
        if(outputCount<=0 || stackLimit<=0) return 1;
        int operations=stackLimit/outputCount;
        return operations>1 && operations<=16 && operations<=remaining ? operations : 1;
    }
    public static boolean shouldYield(int executed,long elapsedNanos) {
        return executed>0 && elapsedNanos>=8_000_000L;
    }
}
