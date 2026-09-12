package mezz.jei.nova.suite;
/** Shared bounds for client plans and repeated server requests. */
public final class TransferLimits {
 private TransferLimits(){}
 public static long outstanding(long request,long target,long held){
  if(request<=0 || request>4096 || target<0 || held<0)return 0;
  return Math.min(request,Math.max(0,target-held));
 }
 public static int fill(int batches,int itemLimit,int slotLimit){
  return Math.max(0,Math.min(Math.min(64,batches),Math.min(itemLimit,slotLimit)));
 }
}
