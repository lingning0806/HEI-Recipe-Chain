package mezz.jei.nova;
import mezz.jei.nova.suite.TransferLimits;
import java.util.*;
public final class SuiteChecks {
 private static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  Map<String,RemainderFlow.Recipe> recipes=new LinkedHashMap<>();
  recipes.put("milk",new RemainderFlow.Recipe("milk",1,Arrays.asList(new RemainderFlow.Input(Arrays.asList("bucket"),1,0,null,0),new RemainderFlow.Input(Arrays.asList("fluid"),1000,0,null,0))));
  recipes.put("cake",new RemainderFlow.Recipe("cake",1,Arrays.asList(new RemainderFlow.Input(Arrays.asList("milk"),3,0,"bucket",1))));
  Map<String,Long> stock=new HashMap<>();stock.put("bucket",3L);stock.put("fluid",6000L);
  RemainderFlow.Result result=new RemainderFlow(recipes,stock).plan(Collections.singletonMap("cake",2L));
  check(result.required.isEmpty());check(result.withdrawn.get("bucket")==3);check(result.consumed.get("bucket")==6);check(result.remaining.get("bucket")==3);
  check(result.withdrawn.get("fluid")==6000);check(result.consumed.get("milk")==6);
  stock.clear();stock.put("milk",3L);result=new RemainderFlow(recipes,stock).plan(Collections.singletonMap("cake",2L));
  check(result.withdrawn.equals(Collections.singletonMap("milk",3L)));check(!result.required.containsKey("bucket"));
  stock.clear();stock.put("cake",5L);stock.put("milk",3L);result=new RemainderFlow(recipes,stock).plan(Collections.singletonMap("cake",1L));
  check(!result.withdrawn.containsKey("cake"));check(result.withdrawn.get("milk")==3L);
  recipes.clear();recipes.put("part",new RemainderFlow.Recipe("part",1,Arrays.asList(new RemainderFlow.Input(Arrays.asList("tool"),1,3,null,0))));
  result=new RemainderFlow(recipes,Collections.singletonMap("tool",1L)).plan(Collections.singletonMap("part",2L));
  check(result.toolsRemaining.get("tool")==1 && result.withdrawn.get("tool")==1);
  check(TransferLimits.outstanding(32,48,16)==32);check(TransferLimits.outstanding(32,48,48)==0);check(TransferLimits.outstanding(32,48,40)==8);
  check(TransferLimits.outstanding(-1,20,0)==0);check(TransferLimits.outstanding(5000,5000,0)==0);
  check(TransferLimits.fill(16,64,64)==16);check(TransferLimits.fill(16,1,64)==1);check(TransferLimits.fill(128,256,256)==64);check(TransferLimits.fill(16,64,8)==8);
  mezz.jei.nova.suite.FillProgress receipt=new mezz.jei.nova.suite.FillProgress();
  for(int i=0;i<4;i++)receipt.record("component","高级合成组件",0,16);
  check(receipt.moved==0 && receipt.missingSlots==4 && receipt.missing.get("component")==64);
  check(receipt.status(16).startsWith("未填入"));check(receipt.missingText().contains("64"));
  receipt.record("other","其他材料",8,8);check(receipt.status(16).startsWith("已填入 8"));
  check(new mezz.jei.nova.suite.FillProgress().status(16).contains("已达到"));
  List<String> target=new ArrayList<>(Collections.nCopies(9,"gold"));
  List<String> actual=new ArrayList<>(Collections.nCopies(9,""));
  actual.set(3,"blackIron");actual.set(4,"blackIron");
  java.util.BitSet conflict=mezz.jei.nova.suite.ProjectionGridState.conflicts(actual,target,String::isEmpty,String::equals);
  check(conflict.cardinality()==2 && conflict.get(3) && conflict.get(4));
  actual.set(3,"");actual.set(4,"");
  check(mezz.jei.nova.suite.ProjectionGridState.conflicts(actual,target,String::isEmpty,String::equals).isEmpty());
  actual.set(0,"gold");
  check(mezz.jei.nova.suite.ProjectionGridState.conflicts(actual,target,String::isEmpty,String::equals).isEmpty());
  target.set(0,"");
  check(mezz.jei.nova.suite.ProjectionGridState.conflicts(actual,target,String::isEmpty,String::equals).get(0));
  target.set(0,"gold:nbt2");
  check(mezz.jei.nova.suite.ProjectionGridState.conflicts(actual,target,String::isEmpty,String::equals).get(0));
  System.out.println("PASS suite: initial withdrawals vs recycled containers, network intermediates, duplicate pull requests, exact fill caps");
 }
}
