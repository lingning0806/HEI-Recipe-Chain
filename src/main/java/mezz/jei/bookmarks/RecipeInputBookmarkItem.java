package mezz.jei.bookmarks;

import mezz.jei.autocrafting.RecipeBookmarkItem;
import java.util.*;

/** Presentation-only aggregation. Recipe inputs and transfer matching remain unchanged. */
public final class RecipeInputBookmarkItem extends DummyBookmarkItem<Object> {
    private final RecipeBookmarkItem<?> owner;
    private final List<RecipeBookmarkItem<?>> requirements;

    public RecipeInputBookmarkItem(RecipeBookmarkItem<?> owner, List<RecipeBookmarkItem<?>> inputs) {
        super(inputs.get(0).aliases.get(0), owner.getGroup(), () -> total(owner, inputs));
        this.owner = owner;
        this.requirements = Collections.unmodifiableList(new ArrayList<>(inputs));
    }

    private static long total(RecipeBookmarkItem<?> owner, List<RecipeBookmarkItem<?>> inputs) {
        long perBatch = 0;
        for (RecipeBookmarkItem<?> input : inputs) perBatch = Math.addExact(perBatch, input.usage.required(input.amount, owner.getMultiplier()));
        return perBatch;
    }

    public List<String> getRequirementDescription() {
        List<String> lines = new ArrayList<>();
        lines.add("此处为本配方用量；额外需准备的材料见配方组缺料提示");
        long exact = 0, alternatives = 0;
        for (RecipeBookmarkItem<?> input : requirements) {
            long amount = input.usage.required(input.amount, owner.getMultiplier());
            if (input.usage.uses == Long.MAX_VALUE) lines.add("可重复使用：按同时占用数量准备");
            else if (input.usage.uses > 0) lines.add("工具预计可用次数：" + input.usage.uses);
            if (input.usage.remainder instanceof net.minecraft.item.ItemStack && input.usage.uses == 0) {
                net.minecraft.item.ItemStack remainder = (net.minecraft.item.ItemStack) input.usage.remainder;
                lines.add("返还：" + remainder.getDisplayName() + " × " + Math.multiplyExact(amount, remainder.getCount()));
                lines.add("返还物在本步完成后可复用；配方组缺料已按执行顺序计算");
            }
            if (input.aliases.size() > 1) alternatives = Math.addExact(alternatives, amount);
            else exact = Math.addExact(exact, amount);
        }
        if (exact > 0) lines.add("指定物品：" + exact);
        if (alternatives > 0) lines.add("可用配方允许的替代材料：" + alternatives);
        if (requirements.size() > 1) lines.add("已合并显示；各材料要求仍按原配方匹配。");
        return lines;
    }
}
