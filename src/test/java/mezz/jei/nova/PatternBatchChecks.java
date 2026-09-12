package mezz.jei.nova;
import mezz.jei.nova.pattern.PatternBatchPolicy;
import java.util.*;
public final class PatternBatchChecks {
    private static void check(boolean value) {if(!value) throw new AssertionError();}
    public static void main(String[] args) {
        check(PatternBatchPolicy.supportsTerminal("appeng.container.implementations.ContainerPatternTerm"));
        check(PatternBatchPolicy.supportsTerminal("com.glodblock.github.client.container.ContainerFluidPatternTerminal"));
        check(!PatternBatchPolicy.supportsTerminal("com.glodblock.github.client.container.ContainerFluidPatternEncoder"));
        check(!PatternBatchPolicy.supportsTerminal("appeng.container.implementations.ContainerExpandedProcessingPatternTerm"));
        check(!PatternBatchPolicy.supportsTerminal("appeng.container.implementations.ContainerCraftingTerm"));
        mezz.jei.nova.pattern.PatternMaterialGuides<String> guides=new mezz.jei.nova.pattern.PatternMaterialGuides<>();
        guides.add(Arrays.asList("oak","birch"),"oak",4);
        for(int i=0;i<4;i++) check(guides.take(Arrays.asList("birch","oak")).equals("oak"));
        try {guides.take(Arrays.asList("oak","birch"));throw new AssertionError();} catch(IllegalArgumentException expected) {}
        guides.add(Arrays.asList("iron","copper"),"copper",1);
        guides.add(Collections.singletonList("iron"),"iron",1);
        check(guides.take(Collections.singletonList("iron")).equals("iron"));
        check(guides.take(Arrays.asList("copper","iron")).equals("copper"));
        try {guides.take(Collections.singletonList("unknown"));throw new AssertionError();} catch(IllegalArgumentException expected) {}
        Integer[] expanded=new Integer[81];for(int i=0;i<81;i++) expanded[i]=i;
        check(Arrays.equals(PatternBatchPolicy.craftingGrid(expanded,true),new Integer[]{0,1,2,3,4,5,6,7,8}));
        check(expanded[80]==80);
        check(PatternBatchPolicy.craftingGrid(new Integer[9],false).length==9);
        try {PatternBatchPolicy.craftingGrid(expanded,false);throw new AssertionError();} catch(IllegalStateException expected) {}
        try {PatternBatchPolicy.craftingGrid(new Integer[27],true);throw new AssertionError();} catch(IllegalStateException expected) {}
        List<PatternBatchPolicy.Status> result=PatternBatchPolicy.classify(Arrays.asList("A:iron->part","A:iron->part",null,"B:copper->part","C:wood->table"),Collections.singleton("C:wood->table"));
        check(result.equals(Arrays.asList(PatternBatchPolicy.Status.READY,PatternBatchPolicy.Status.DUPLICATE,PatternBatchPolicy.Status.INVALID,PatternBatchPolicy.Status.READY,PatternBatchPolicy.Status.DUPLICATE)));
        check(PatternBatchPolicy.canEncode(2,2,2));
        check(!PatternBatchPolicy.canEncode(2,1,3));
        check(!PatternBatchPolicy.canEncode(2,3,1));
        check(!PatternBatchPolicy.canEncode(0,3,3));
        check(!PatternBatchPolicy.canEncode(65,100,100));
        check(PatternBatchPolicy.classify(Collections.nCopies(16,"same recipe"),Collections.emptySet()).stream().filter(x->x==PatternBatchPolicy.Status.READY).count()==1);
        System.out.println("PASS: exact recipe dedup, different routes retained, invalid skip, blanks/space preflight, one pattern per recipe");
    }
}
