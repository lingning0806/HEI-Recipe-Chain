package mezz.jei.network.packets;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import mezz.jei.JustEnoughItems;
import mezz.jei.network.*;
import mezz.jei.nova.ae2.Ae2Terminal;
import mezz.jei.util.Log;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.network.PacketBuffer;
import java.io.*;
import java.lang.reflect.*;

/** One server task owns transfer and crafting; no ordering assumption between mod channels. */
public final class PacketAe2Craft extends PacketJei {
    private final int window, batches;
    private final NBTTagCompound recipe;
    private final ItemStack expected;
    public PacketAe2Craft(int window,int batches,NBTTagCompound recipe,ItemStack expected) {
        this.window=window; this.batches=batches; this.recipe=recipe; this.expected=expected;
    }
    public IPacketId getPacketId() { return PacketIdServer.AE2_CRAFT; }
    public void writePacketData(PacketBuffer b) { b.writeVarInt(window); b.writeVarInt(batches); b.writeCompoundTag(recipe); b.writeItemStack(expected); }
    public static void readPacketData(PacketBuffer b,EntityPlayer player) throws IOException {
        int window=b.readVarInt(), batches=b.readVarInt();
        NBTTagCompound recipe=b.readCompoundTag(); ItemStack expected=b.readItemStack();
        if(!(player instanceof EntityPlayerMP)) return;
        int completed=0;
        try {
            Container c=player.openContainer;
            if(!Ae2Terminal.supports(c) || c.windowId!=window || batches<1 || batches>64 || recipe==null || expected.isEmpty() ||
                !c.canInteractWith(player) || !player.inventory.getItemStack().isEmpty() || !Boolean.TRUE.equals(Ae2Terminal.call(c,"isPowered"))) return;
            Slot slot=Ae2Terminal.result(c); if(slot==null) return;
            Class<?> action=Class.forName("appeng.helpers.InventoryAction");
            Object single=Enum.valueOf((Class)action,"CRAFT_ITEM");
            Object stackAction=Enum.valueOf((Class)action,"CRAFT_STACK");
            Method click=slot.getClass().getMethod("doClick",action,EntityPlayer.class);
            ByteArrayOutputStream encoded=new ByteArrayOutputStream();
            CompressedStreamTools.writeCompressed(recipe,encoded);
            byte[] recipeBytes=encoded.toByteArray();
            long started=System.nanoTime();
            java.util.List<ItemStack> previousGrid=null;
            boolean ordinary=false;
            for(int i=0;i<batches;) {
                if(mezz.jei.nova.ae2.Ae2Batch.shouldYield(i,System.nanoTime()-started)) break;
                if(player.openContainer!=c || !c.canInteractWith(player) || !Boolean.TRUE.equals(Ae2Terminal.call(c,"isPowered"))) break;
                // Native transfer performs AE permission, energy and recipe-aware material checks.
                if(previousGrid==null || !ordinary || !sameGrid(c,previousGrid)) {
                    transfer(recipeBytes,player);

                }
                if(!mezz.jei.nova.ae2.Ae2ExactGrid.repair(c,player,recipe)) {
                    Log.get().info("[HEI-DIAG] exact grid incomplete; completed="+completed+" expected="+expected.serializeNBT());break;
                }
                ordinary=isOrdinary(c);
                ItemStack output=slot.getStack();
                if(!Ae2Terminal.same(output,expected) || !hasRoom(player.inventory,output)) break;
                int operations=ordinary ? mezz.jei.nova.ae2.Ae2Batch.nativeStackOperations(output.getCount(),output.getMaxStackSize(),batches-i) : 1;
                ItemStack capacity=output.copy(); capacity.setCount(output.getCount()*operations);
                if(!hasRoom(player.inventory,capacity)) operations=1;
                previousGrid=grid(c);
                // AE's native stack action still performs onTake, permissions and consumption per craft.
                click.invoke(slot,operations>1 ? stackAction : single,player);
                ItemStack received=player.inventory.getItemStack();
                if(received.isEmpty()) break;
                // Count actual native delivery, never the requested quantity or the recipe preview.
                boolean matched=Ae2Terminal.same(received,expected);
                int delivered=received.getCount();
                player.inventory.addItemStackToInventory(received);
                player.inventory.setItemStack(received.isEmpty()?ItemStack.EMPTY:received);
                if(matched) completed=Math.addExact(completed,delivered);
                i+=Math.max(1,delivered/Math.max(1,output.getCount()));
                if(!matched || !received.isEmpty()) break;
            }
        } catch(ReflectiveOperationException | RuntimeException ex) {
            Log.get().error("AE2 native crafting bridge failed",ex);
        } finally {
            player.openContainer.detectAndSendChanges();
            // Delta slot updates suffice; do not resend the entire terminal after every recipe.
            // Cursor normally ends empty; explicitly synchronize it if insertion left a remainder.
            if (!player.inventory.getItemStack().isEmpty())
                ((EntityPlayerMP)player).connection.sendPacket(new net.minecraft.network.play.server.SPacketSetSlot(-1,-1,player.inventory.getItemStack()));
            JustEnoughItems.getProxy().sendPacketToClient(new PacketCraftUpdate(completed>0,completed),(EntityPlayerMP)player);
        }
    }
    private static java.util.List<ItemStack> grid(Container container) {
        java.util.List<ItemStack> result=new java.util.ArrayList<>();
        for(Slot slot:container.inventorySlots)
            if(slot.getClass().getName().equals("appeng.container.slot.SlotCraftingMatrix")) result.add(slot.getStack().copy());
        return result;
    }
    private static boolean sameGrid(Container container,java.util.List<ItemStack> before) {
        java.util.List<ItemStack> after=grid(container);
        if(before.size()!=9 || after.size()!=9) return false;
        for(int i=0;i<9;i++) if(!ItemStack.areItemStacksEqual(before.get(i),after.get(i))) return false;
        return true;
    }
    private static boolean isOrdinary(Container container) {
        try {
            Object recipe=Ae2Terminal.call(container,"getCurrentRecipe");
            if(recipe==null) return false;
            String type=recipe.getClass().getName();
            if(!type.equals("net.minecraft.item.crafting.ShapedRecipes") && !type.equals("net.minecraft.item.crafting.ShapelessRecipes") &&
               !type.equals("net.minecraftforge.oredict.ShapedOreRecipe") && !type.equals("net.minecraftforge.oredict.ShapelessOreRecipe")) return false;
            java.util.List<ItemStack> grid=grid(container);
            if(grid.size()!=9) return false;
            for(ItemStack item:grid) if(!item.isEmpty() && (item.isItemStackDamageable() || item.hasTagCompound() || item.getItem().hasContainerItem(item))) return false;
            return true;
        } catch(ReflectiveOperationException | RuntimeException ex) { return false; }
    }
    private static boolean hasRoom(InventoryPlayer inventory,ItemStack result) {
        int remaining=result.getCount();
        for(ItemStack stack:inventory.mainInventory) {
            if(stack.isEmpty()) remaining-=Math.min(result.getMaxStackSize(),inventory.getInventoryStackLimit());
            else if(Ae2Terminal.same(stack,result)) remaining-=Math.max(0,Math.min(stack.getMaxStackSize(),inventory.getInventoryStackLimit())-stack.getCount());
            if(remaining<=0) return true;
        }
        return false;
    }
    private static void transfer(byte[] recipeBytes,EntityPlayer player) throws ReflectiveOperationException,IOException {
        ByteBuf buffer=Unpooled.wrappedBuffer(recipeBytes);
        try {
            Class<?> type=Class.forName("appeng.core.sync.packets.PacketJEIRecipe");
            Object packet=type.getConstructor(ByteBuf.class).newInstance(buffer);
            type.getMethod("serverPacketData",Class.forName("appeng.core.sync.network.INetworkInfo"),Class.forName("appeng.core.sync.AppEngPacket"),EntityPlayer.class).invoke(packet,null,packet,player);
        } finally { buffer.release(); }
    }
}
