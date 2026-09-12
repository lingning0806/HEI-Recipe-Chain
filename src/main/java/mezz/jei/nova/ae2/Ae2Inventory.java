package mezz.jei.nova.ae2;

import mezz.jei.Internal;
import mezz.jei.util.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import java.lang.reflect.Field;
import java.util.Map;

/** Read the terminal's complete synchronized repository, independent of search text. */
public final class Ae2Inventory {
    private static boolean warned;
    private Ae2Inventory() {}
    public static void addTo(Map<String,Long> counts) {addTo(counts,true);}
    public static void addTo(Map<String,Long> counts,boolean grid) {
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.player==null || !Ae2Terminal.supportsStorage(mc.player.openContainer) || mc.currentScreen==null) return;
        try {
            if(!Boolean.TRUE.equals(Ae2Terminal.call(mc.player.openContainer,"isPowered"))) return;
            Class<?> gui=Class.forName("appeng.client.gui.implementations.GuiMEMonitorable");
            if(!gui.isInstance(mc.currentScreen)) return;
            Field field=gui.getDeclaredField("repo"); field.setAccessible(true);
            Object repo=field.get(mc.currentScreen);
            Object list=Ae2Terminal.call(repo,"getList");
            Object filter=Class.forName("appeng.items.storage.ItemViewCell").getMethod("createFilter",ItemStack[].class)
                .invoke(null,Ae2Terminal.call(mc.player.openContainer,"getViewCells"));
            java.lang.reflect.Method isListed=Class.forName("appeng.util.prioritylist.IPartitionList")
                .getMethod("isListed",Class.forName("appeng.api.storage.data.IAEStack"));
            boolean unrestricted=Ae2Terminal.unrestricted(filter);
            for(Object entry:(Iterable<?>)list) {
                if(!unrestricted && !Boolean.TRUE.equals(isListed.invoke(filter,entry))) continue;
                long amount=((Number)Ae2Terminal.call(entry,"getStackSize")).longValue();
                if(amount<=0) continue; // Craftable-only entries are not stock.
                ItemStack stack=(ItemStack)Ae2Terminal.call(entry,"getDefinition");
                if(!stack.isEmpty()) counts.merge(Internal.getIngredientRegistry().getUniqueId(stack),amount,Math::addExact);
            }
            // The grid holds real items removed from network stock; count them exactly once.
            if(grid) for(net.minecraft.inventory.Slot slot:mc.player.openContainer.inventorySlots) {
                if(!slot.getClass().getName().equals("appeng.container.slot.SlotCraftingMatrix")) continue;
                ItemStack stack=slot.getStack();
                if(!stack.isEmpty()) counts.merge(Internal.getIngredientRegistry().getUniqueId(stack),(long)stack.getCount(),Math::addExact);
            }
        } catch(ReflectiveOperationException | RuntimeException ex) {
            if(!warned) { warned=true; Log.get().warn("AE2 network inventory unavailable",ex); }
        }
    }
}
