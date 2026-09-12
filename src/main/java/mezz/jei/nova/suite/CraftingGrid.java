package mezz.jei.nova.suite;
import net.minecraft.inventory.*;
import java.util.*;
public final class CraftingGrid {
    public static List<Slot> grid(Container c) {
        List<Slot> result=new ArrayList<>();
        if(c!=null && c.getClass()==ContainerWorkbench.class) {for(int i=1;i<=9;i++)result.add(c.getSlot(i));}
        else if(mezz.jei.nova.ae2.Ae2Terminal.supports(c))for(Slot slot:c.inventorySlots)if(slot.getClass().getName().equals("appeng.container.slot.SlotCraftingMatrix"))result.add(slot);
        return result.size()==9?result:Collections.emptyList();
    }
}
