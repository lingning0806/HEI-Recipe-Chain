package mezz.jei.nova;

import java.util.*;

/** Assign concrete ledger choices to overlapping ore-dictionary slots without stealing exact inputs. */
public final class MaterialBinding {
    private MaterialBinding() {}
    public static List<String> bind(List<List<String>> candidates,List<Long> amounts,Map<String,Long> stock) {
        if(candidates.size()!=amounts.size() || candidates.size()>9) return null;
        List<Integer> order=new ArrayList<>();
        for(int i=0;i<candidates.size();i++) { if(amounts.get(i)<=0) return null;order.add(i); }
        order.sort(Comparator.comparingInt(i->candidates.get(i).size()));
        List<String> result=new ArrayList<>(Collections.nCopies(candidates.size(),null));
        return solve(0,order,candidates,amounts,new HashMap<>(stock),result,new int[]{0}) ? result : null;
    }
    private static boolean solve(int position,List<Integer> order,List<List<String>> candidates,List<Long> amounts,
                                 Map<String,Long> stock,List<String> result,int[] attempts) {
        if(position==order.size()) return true;
        if(++attempts[0]>65536) return false;
        int index=order.get(position);long amount=amounts.get(index);
        for(String key:candidates.get(index)) {
            long available=stock.getOrDefault(key,0L);
            if(available<amount) continue;
            stock.put(key,available-amount);result.set(index,key);
            if(solve(position+1,order,candidates,amounts,stock,result,attempts)) return true;
            stock.put(key,available);
        }
        result.set(index,null);return false;
    }
}
