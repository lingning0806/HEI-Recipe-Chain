package mezz.jei.nova;

import mezz.jei.util.StringUtil;
import java.util.*;
import java.util.concurrent.*;

/** Parallel unique inserts force resizes while other threads repeat existing keys. */
public final class StringInternerChecks {
    public static void main(String[] args) throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        try {
            for (int t=0;t<8;t++) {
                final int thread=t;
                results.add(workers.submit(() -> {
                    try { start.await(); } catch (InterruptedException e) { throw new RuntimeException(e); }
                    for (int i=0;i<30000;i++) {
                        String key="interner-"+thread+"-"+i;
                        String first=StringUtil.intern(key);
                        if (first!=StringUtil.intern(new String(key))) throw new AssertionError("Lost canonical identity");
                        String shared="shared-"+(i%1000);
                        if (StringUtil.intern(shared)!=StringUtil.intern(new String(shared))) throw new AssertionError("Shared key corrupted");
                    }
                }));
            }
            start.countDown();
            for (Future<?> result:results) result.get(30,TimeUnit.SECONDS);
            if (StringUtil.intern(null)!=null) throw new AssertionError("Null compatibility changed");
        } finally { workers.shutdownNow(); }
        System.out.println("String interner concurrent resize checks passed");
    }
}
