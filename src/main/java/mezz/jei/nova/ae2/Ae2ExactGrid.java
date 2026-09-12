package mezz.jei.nova.ae2;

import mezz.jei.nova.suite.AePull;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Repair native fuzzy backpack transfers before taking any crafting output. */
public final class Ae2ExactGrid {
    private Ae2ExactGrid() {}
    public static boolean matches(ItemStack actual,NBTTagList alternatives) {
        if(actual.isEmpty()) return alternatives.tagCount()==0;
        for(int i=0;i<alternatives.tagCount();i++)
            if(Ae2Terminal.same(actual,new ItemStack(alternatives.getCompoundTagAt(i))))return true;
        return false;
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    public static boolean repair(Container c,EntityPlayer player,NBTTagCompound recipe) throws ReflectiveOperationException {
        List<Slot> grid=new ArrayList<>();
        for(Slot slot:c.inventorySlots)if(slot.getClass().getName().equals("appeng.container.slot.SlotCraftingMatrix"))grid.add(slot);
        if(grid.size()!=9)return false;
        boolean valid=true;
        for(int i=0;i<9;i++)if(!matches(grid.get(i).getStack(),recipe.getTagList("#"+i,10))) {valid=false;break;}
        if(valid)return true;
        Class permission=Class.forName("appeng.api.config.SecurityPermissions");
        if(!Boolean.TRUE.equals(AePull.method(c,"hasAccess",permission,boolean.class).invoke(c,Enum.valueOf(permission,"EXTRACT"),true)))return false;
        boolean changed=false;
        try {
            // Evacuate only incompatible items. Inventory insertion may be partial; keep the rest in its real slot.
            for(int i=0;i<9;i++) {
                Slot slot=grid.get(i);ItemStack actual=slot.getStack();
                if(!actual.isEmpty() && !matches(actual,recipe.getTagList("#"+i,10))) {
                    mezz.jei.util.Log.get().info("[HEI-DIAG] exact grid replacing slot="+i+" actual="+actual.serializeNBT()+" expected="+recipe.getTagList("#"+i,10));
                    ItemStack remaining=actual.copy();player.inventory.addItemStackToInventory(remaining);
                    slot.putStack(remaining.isEmpty()?ItemStack.EMPTY:remaining);changed=true;
                    if(!remaining.isEmpty())return false;
                }
            }
            Object storage=null,power=null,source=null;Method create=null;
            java.lang.invoke.MethodHandle extract=null;
            for(int i=0;i<9;i++) {
                Slot slot=grid.get(i);NBTTagList candidates=recipe.getTagList("#"+i,10);
                if(!slot.getStack().isEmpty() || candidates.tagCount()==0)continue;
                for(int n=0;n<candidates.tagCount() && slot.getStack().isEmpty();n++) {
                    ItemStack wanted=new ItemStack(candidates.getCompoundTagAt(n));if(wanted.isEmpty())continue;wanted.setCount(1);
                    for(ItemStack held:player.inventory.mainInventory)if(Ae2Terminal.same(held,wanted)) {
                        slot.putStack(held.splitStack(1));changed=true;break;
                    }
                    if(!slot.getStack().isEmpty())break;
                    if(extract==null) {
                        storage=Ae2Terminal.call(c,"getCellInventory");power=Ae2Terminal.call(c,"getPowerSource");source=Ae2Terminal.call(c,"getActionSource");
                        create=Class.forName("appeng.util.item.AEItemStack").getMethod("fromItemStack",ItemStack.class);
                        extract=ExactMethod.poweredExtraction();
                    }
                    Object taken=ExactMethod.invoke(extract,power,storage,create.invoke(null,wanted));
                    if(taken!=null) {
                        ItemStack item=(ItemStack)Ae2Terminal.call(taken,"createItemStack");
                        slot.putStack(item);changed=true;
                    }
                }
                if(slot.getStack().isEmpty())return false;
            }
            for(int i=0;i<9;i++)if(!matches(grid.get(i).getStack(),recipe.getTagList("#"+i,10)))return false;
            return true;
        } finally {
            if(changed) {player.inventory.markDirty();c.onCraftMatrixChanged(null);c.detectAndSendChanges();}
        }
    }
}
