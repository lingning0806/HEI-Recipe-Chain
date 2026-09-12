package mezz.jei.bookmarks;
import mezz.jei.nova.InfusionDemand;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.item.ItemStack;

public final class InfusionBookmarkItem extends DummyBookmarkItem<Object> {
    public final InfusionDemand demand;
    public InfusionBookmarkItem(Object icon, BookmarkGroup group, Supplier<Long> amount, InfusionDemand demand) {
        super(icon,group,amount);this.demand=demand;
    }
    public List<String> description() {
        List<String> text=new ArrayList<>();
        text.add(demand.name+"灌注："+getDisplayAmount()+" 单位");
        text.add("图标代表灌注类型，角标不是物品个数");
        text.add("供料方案（任选一种，不相加）：");
        int shown=0;
        for(Map.Entry<ItemStack,Integer> source:demand.sources.entrySet()) {
            long count=InfusionDemand.feedCount(getDisplayAmount(),source.getValue());
            text.add(source.getKey().getDisplayName()+" × "+count+"（每件 "+source.getValue()+" 单位，余 "+(Math.multiplyExact(count,source.getValue())-getDisplayAmount())+"）");
            if(++shown==8){if(demand.sources.size()>shown)text.add("更多供料见原配方页面");break;}
        }
        text.add("未计入机器已有灌注量；未自动选择供料路线");
        return text;
    }
}
