package mezz.jei.nova.suite;
import mezz.jei.nova.ae2.Ae2Terminal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.*;
import net.minecraft.nbt.*;
import net.minecraft.util.text.TextComponentString;
import java.lang.reflect.*;
import java.util.*;
public final class ProjectionFill {
    public static void execute(EntityPlayer p,int window,int batches,NBTTagCompound plan) {
        Container c=p.openContainer;
        if(c.windowId!=window || !c.canInteractWith(p) || !p.inventory.getItemStack().isEmpty() || batches<1 || batches>64 || plan==null)return;
        List<Slot> slots=CraftingGrid.grid(c);if(slots.size()!=9)return;
        NBTTagList input=plan.getTagList("in",10);if(input.tagCount()!=9)return;
        InventoryCrafting check=new InventoryCrafting(new Container(){public boolean canInteractWith(EntityPlayer x){return false;}},3,3);
        List<ItemStack> items=new ArrayList<>();
        for(int i=0;i<9;i++) {
            ItemStack item=new ItemStack(input.getCompoundTagAt(i));if(!item.isEmpty() && item.getCount()!=1)return;
            items.add(item);check.setInventorySlotContents(i,item.copy());
            if(!item.isEmpty())batches=TransferLimits.fill(batches,item.getMaxStackSize(),slots.get(i).getSlotStackLimit());
        }
        List<ItemStack> actualGrid=new ArrayList<>();for(Slot slot:slots)actualGrid.add(slot.getStack());
        if(!ProjectionGridState.conflicts(actualGrid,items,ItemStack::isEmpty,Ae2Terminal::same).isEmpty()) {
            message(p,"九宫格仍有旧配方材料，请先移走冲突材料；未移动材料，也未合成");return;
        }
        IRecipe recipe=CraftingManager.findMatchingRecipe(check,p.world);ItemStack expected=new ItemStack(plan.getCompoundTag("out"));
        if(recipe==null || expected.isEmpty() || !ItemStack.areItemStacksEqual(recipe.getCraftingResult(check),expected))return;
        try {
            boolean ae=Ae2Terminal.supports(c);Object storage=null,power=null,source=null;Method create=null,size=null;java.lang.invoke.MethodHandle extract=null;
            if(ae) {
                Class permission=Class.forName("appeng.api.config.SecurityPermissions");
                if(!Boolean.TRUE.equals(AePull.method(c,"hasAccess",permission,boolean.class).invoke(c,Enum.valueOf(permission,"EXTRACT"),true))) {message(p,"AE 未供电或无取料权限");return;}
                storage=Ae2Terminal.call(c,"getCellInventory");power=Ae2Terminal.call(c,"getPowerSource");source=Ae2Terminal.call(c,"getActionSource");
                create=Class.forName("appeng.util.item.AEItemStack").getMethod("fromItemStack",ItemStack.class);
                Class stack=Class.forName("appeng.api.storage.data.IAEStack");size=stack.getMethod("setStackSize",long.class);
                extract=mezz.jei.nova.ae2.ExactMethod.poweredExtraction();
            }
            FillProgress progress=new FillProgress();
            mezz.jei.util.Log.get().info("[HEI-DIAG] projection fill output="+expected.getDisplayName()+" batches="+batches+" container="+c.getClass().getName());
            for(int i=0;i<9;i++) {
                ItemStack item=items.get(i);if(item.isEmpty())continue;
                Slot slot=slots.get(i);int target=TransferLimits.fill(batches,item.getMaxStackSize(),slot.getSlotStackLimit()),have=slot.getStack().getCount();int need=Math.max(0,target-have),beforeNeed=need;
                for(ItemStack held:p.inventory.mainInventory)if(need>0 && Ae2Terminal.same(held,item)) {
                    int n=Math.min(need,held.getCount());held.shrink(n);have+=n;need-=n;ItemStack added=item.copy();added.setCount(have);slot.putStack(added);
                }
                if(need>0 && ae) {
                    Object request=create.invoke(null,item);size.invoke(request,(long)need);Object taken=mezz.jei.nova.ae2.ExactMethod.invoke(extract,power,storage,request,source);
                    if(taken!=null) {ItemStack actual=(ItemStack)Ae2Terminal.call(taken,"createItemStack");have+=actual.getCount();need-=actual.getCount();ItemStack added=item.copy();added.setCount(have);slot.putStack(added);}
                }
                progress.record(item.serializeNBT().toString(),item.getDisplayName(),beforeNeed-need,need);
            }
            p.inventory.markDirty();c.detectAndSendChanges();message(p,progress.status(batches));
            if(progress.missingSlots>0) {
                message(p,progress.missingText());
                message(p,"Shift+G 只填当前配方的直接材料；需制作下级配方时，用 Shift+C 执行配方链。若材料已在网络中，请检查终端供电。");
            }
            mezz.jei.util.Log.get().info("[HEI-DIAG] projection moved="+progress.moved+" missing="+progress.missing);
        } catch(ReflectiveOperationException | RuntimeException | LinkageError ex) {mezz.jei.util.Log.get().warn("Projection fill stopped",ex);message(p,"填充已停止，已填入的材料保留");}
    }
    private static void message(EntityPlayer p,String s){p.sendMessage(new TextComponentString("[HEI 填充] "+s));}
}
