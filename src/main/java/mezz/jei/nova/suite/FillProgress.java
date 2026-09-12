package mezz.jei.nova.suite;
import java.util.*;
/** Receipts describe what moved, rather than implying that an empty fill succeeded. */
public final class FillProgress {
    public long moved;
    public int missingSlots;
    public final Map<String,Long> missing=new LinkedHashMap<>();
    public final Map<String,String> names=new LinkedHashMap<>();
    public void record(String key,String name,long transferred,long shortage) {
        if(transferred<0 || shortage<0)throw new IllegalArgumentException("Negative fill result");
        moved=Math.addExact(moved,transferred);
        if(shortage>0){missingSlots++;missing.merge(key,shortage,Math::addExact);names.put(key,name);}
    }
    public String status(int batches) {
        if(missingSlots==0)return moved==0?"九宫格已达到本次目标，请手动取出成品":"已填入 "+moved+" 个材料（"+batches+" 次配方），请手动取出成品";
        return moved==0?"未填入材料：当前配方的直接材料不足":"已填入 "+moved+" 个材料；还有 "+missingSlots+" 个槽位不足，已填物品保留";
    }
    public String missingText() {
        List<String> parts=new ArrayList<>();missing.forEach((key,count)->parts.add(names.get(key)+" × "+count));return "缺少："+String.join("、",parts);
    }
}
