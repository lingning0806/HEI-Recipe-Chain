package mezz.jei.nova.ae2;

import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import java.lang.reflect.*;

/** Optional AE2 UEL bridge: no hard dependency and no generic-container fallback. */
public final class Ae2Terminal {
    private Ae2Terminal() {}
    public static boolean supports(Container container) {
        return container != null && container.getClass().getName().equals("appeng.container.implementations.ContainerCraftingTerm");
    }
    public static boolean supportsStorage(Container container) {
        if(container==null)return false;
        String name=container.getClass().getName();
        return supports(container) || name.equals("appeng.container.implementations.ContainerMEMonitorable") || mezz.jei.nova.pattern.PatternBatchPolicy.supportsTerminal(name);
    }
    public static boolean acceptsHandler(String name) {
        return name.equals("appeng.integration.modules.jei.RecipeTransferHandler") ||
            name.equals("com.github.vfyjxf.nee.jei.CraftingTransferHandler");
    }
    public static boolean unrestricted(Object filter) throws ReflectiveOperationException {
        return filter == null || Boolean.TRUE.equals(call(filter,"isEmpty"));
    }
    public static Object call(Object target, String name) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }
    public static Slot result(Container container) {
        for (Slot slot : container.inventorySlots)
            if (slot.getClass().getName().equals("appeng.container.slot.SlotCraftingTerm")) return slot;
        return null;
    }
    public static boolean same(ItemStack a, ItemStack b) {
        return !a.isEmpty() && !b.isEmpty() && ItemStack.areItemsEqual(a,b) && ItemStack.areItemStackTagsEqual(a,b);
    }
}
