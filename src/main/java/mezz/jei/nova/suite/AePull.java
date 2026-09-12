package mezz.jei.nova.suite;

import mezz.jei.JustEnoughItems;
import mezz.jei.autocrafting.RecipeBookmarkGroup;
import mezz.jei.nova.CraftDiagnostics;
import mezz.jei.nova.ae2.Ae2Terminal;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.text.TextComponentString;
import java.lang.reflect.*;

/** Extract through the terminal's native powered storage and player action source. */
public final class AePull {
    public static void request(RecipeBookmarkGroup group) {
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.player==null || !Ae2Terminal.supportsStorage(mc.player.openContainer)) {CraftDiagnostics.notice("请在已适配的 AE 有线终端取料");return;}
        GroupMaterials report=new GroupMaterials(group);NBTTagList entries=new NBTTagList();
        report.pull.forEach((key,count)->{Object value=report.items.get(key);if(value instanceof ItemStack && count>0 && entries.tagCount()<64) {
            NBTTagCompound entry=((ItemStack)value).writeToNBT(new NBTTagCompound());entry.setLong("wanted",Math.min(4096,count));entry.setLong("target",Math.addExact(report.player.getOrDefault(key,0L),Math.min(4096,count)));entries.appendTag(entry);
        }});
        if(entries.tagCount()==0) {CraftDiagnostics.notice("没有需要从 AE 取出的可用物品；流体和灌注资源需在对应设备处理");return;}
        JustEnoughItems.getProxy().sendPacketToServer(new mezz.jei.network.packets.PacketAePull(mc.player.openContainer.windowId,entries));
    }
    public static Object field(Object target,String name) throws ReflectiveOperationException {
        for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())try {Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(target);} catch(NoSuchFieldException ignored) {}
        throw new NoSuchFieldException(name);
    }
    public static Method method(Object target,String name,Class<?>...types) throws ReflectiveOperationException {
        for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass())try {Method m=c.getDeclaredMethod(name,types);m.setAccessible(true);return m;}catch(NoSuchMethodException ignored){}
        throw new NoSuchMethodException(name);
    }
    public static void execute(EntityPlayer player,int window,NBTTagList entries) {
        Container c=player.openContainer;
        if(!Ae2Terminal.supportsStorage(c) || c.windowId!=window || !c.canInteractWith(player) || entries.tagCount()>64 || !player.inventory.getItemStack().isEmpty())return;
        long done=0;boolean stopped=false;
        try {
            Class permissions=Class.forName("appeng.api.config.SecurityPermissions");
            if(!Boolean.TRUE.equals(method(c,"hasAccess",permissions,boolean.class).invoke(c,Enum.valueOf(permissions,"EXTRACT"),false)) || !Boolean.TRUE.equals(Ae2Terminal.call(c,"isPowered"))) {message(player,"无法取料：终端未供电或没有提取权限");return;}
            Object storage=Ae2Terminal.call(c,"getCellInventory"),power=Ae2Terminal.call(c,"getPowerSource"),source=Ae2Terminal.call(c,"getActionSource");
            Method create=Class.forName("appeng.util.item.AEItemStack").getMethod("fromItemStack",ItemStack.class);
            Class stackType=Class.forName("appeng.api.storage.data.IAEStack");
            Method size=stackType.getMethod("setStackSize",long.class);
            java.lang.invoke.MethodHandle extract=mezz.jei.nova.ae2.ExactMethod.poweredExtraction();
            long deadline=System.nanoTime()+8_000_000;int chunks=0;
            java.util.Set<String> seen=new java.util.HashSet<>();
            for(int i=0;i<entries.tagCount();i++) {
                NBTTagCompound data=entries.getCompoundTagAt(i);ItemStack item=new ItemStack(data);long wanted=data.getLong("wanted"),moved=0;
                if(item.isEmpty() || wanted<=0 || wanted>4096)continue;
                ItemStack unit=item.copy();unit.setCount(1);if(!seen.add(unit.serializeNBT().toString()))continue;
                long held=0;for(ItemStack present:player.inventory.mainInventory)if(Ae2Terminal.same(present,item))held+=present.getCount();
                for(net.minecraft.inventory.Slot slot:c.inventorySlots)if(slot.getClass().getName().equals("appeng.container.slot.SlotCraftingMatrix") && Ae2Terminal.same(slot.getStack(),item))held+=slot.getStack().getCount();
                wanted=TransferLimits.outstanding(wanted,data.getLong("target"),held);
                while(moved<wanted) {
                    if(chunks>=64 || System.nanoTime()>deadline && chunks>0) {stopped=true;break;}
                    int target=-1,room=0;
                    for(int j=0;j<player.inventory.mainInventory.size();j++) {
                        ItemStack slot=player.inventory.mainInventory.get(j);int limit=Math.min(item.getMaxStackSize(),player.inventory.getInventoryStackLimit());
                        int free=slot.isEmpty()?limit:Ae2Terminal.same(slot,item)?limit-slot.getCount():0;
                        if(free>0) {target=j;room=free;break;}
                    }
                    if(target<0) {stopped=true;break;}
                    Object request=create.invoke(null,item);size.invoke(request,Math.min(room,wanted-moved));
                    Object actual=mezz.jei.nova.ae2.ExactMethod.invoke(extract,power,storage,request,source);chunks++;
                    if(actual==null)break;
                    ItemStack taken=(ItemStack)Ae2Terminal.call(actual,"createItemStack");if(taken.isEmpty())break;
                    int count=taken.getCount();
                    // Native insertion handles intervening inventory events. Preserve any remainder in hand.
                    player.inventory.addItemStackToInventory(taken);
                    if(!taken.isEmpty()) {
                        if(player.inventory.getItemStack().isEmpty())player.inventory.setItemStack(taken);
                        else player.dropItem(taken,false); // Last-resort preservation, matching native inventory actions.
                        stopped=true;
                    }
                    moved+=count;done+=count;if(stopped)break;
                }
                message(player,item.getDisplayName()+"：已取 "+moved+" / "+wanted);
                if(stopped)break;
            }
            player.inventory.markDirty();c.detectAndSendChanges();
            if(player instanceof net.minecraft.entity.player.EntityPlayerMP && !player.inventory.getItemStack().isEmpty())
                ((net.minecraft.entity.player.EntityPlayerMP)player).connection.sendPacket(new net.minecraft.network.play.server.SPacketSetSlot(-1,-1,player.inventory.getItemStack()));
            message(player,"本次取出 "+done+" 个物品"+(stopped?"；空间或单次执行额度已满，可再次按取料键继续":"；缺口将随库存刷新"));
        } catch(ReflectiveOperationException | RuntimeException | LinkageError ex) {mezz.jei.util.Log.get().warn("AE pull stopped",ex);message(player,"取料停止，已取物品保留；请查看日志");}
    }
    private static void message(EntityPlayer p,String text) {p.sendMessage(new TextComponentString("[HEI 取料] "+text));}
}
