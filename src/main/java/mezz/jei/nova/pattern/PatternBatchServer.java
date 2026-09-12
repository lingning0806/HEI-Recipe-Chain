package mezz.jei.nova.pattern;

import mezz.jei.nova.ae2.Ae2Terminal;
import mezz.jei.util.Log;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.*;
import net.minecraft.nbt.*;
import net.minecraft.util.text.TextComponentString;
import java.lang.reflect.*;
import java.util.*;

/** Native encoding with preflight, exact-pattern deduplication and per-entry receipts. */
public final class PatternBatchServer {
    private static final Map<EntityPlayer,Pending> pending=new WeakHashMap<>();
    private static final class Pending {
        final Container container;final NBTTagCompound request;final long at;final Set<NBTTagCompound> approved;
        Pending(Container c,NBTTagCompound r,Set<NBTTagCompound> keys) {container=c;request=r.copy();approved=new HashSet<>(keys);at=System.currentTimeMillis();}
    }
    private static final class Entry {
        final NBTTagCompound data;String status;
        Entry(NBTTagCompound data,String status) {this.data=data;this.status=status;}
        String name() {ItemStack result=new ItemStack(data.getCompoundTag("expected"));return result.isEmpty()?"不支持的配方":result.getDisplayName();}
    }
    private PatternBatchServer() {}
    public static boolean supports(Container c) {return c!=null && PatternBatchPolicy.supportsTerminal(c.getClass().getName());}
    private static void message(EntityPlayer player,String text) {player.sendMessage(new TextComponentString("[HEI 样板] "+text));}
    private static Object field(Object target,String name) throws ReflectiveOperationException {
        for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass()) {
            try {Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    public static void handle(EntityPlayer player,int window,NBTTagCompound request) {
        Container c=player.openContainer;
        if(request==null || !supports(c) || c.windowId!=window || !c.canInteractWith(player)) return;
        NBTTagList raw=request.getTagList("entries",10);
        if(raw.tagCount()==0 || raw.tagCount()>64) {message(player,"每组需要 1～64 个配方。");return;}
        try {
            if(!Boolean.TRUE.equals(Ae2Terminal.call(c,"isPowered")) || !Boolean.TRUE.equals(Ae2Terminal.call(c,"isCraftingMode"))) {
                pending.remove(player);message(player,"请保持终端供电，并切换到合成样板模式。");return;
            }
            Slot in=(Slot)field(c,"patternSlotIN"),out=(Slot)field(c,"patternSlotOUT");
            ItemStack blank=blankPattern();
            if(!out.getStack().isEmpty() || (!in.getStack().isEmpty() && !Ae2Terminal.same(in.getStack(),blank))) {
                pending.remove(player);message(player,"请先取走终端输出槽及输入槽中的已编码样板，避免覆盖。");return;
            }
            craftingGrid(c); // Validate the runtime adapter before promising a successful preview.
            List<Entry> entries=prepare(player,raw);
            Set<NBTTagCompound> known=new HashSet<>();
            for(ItemStack item:player.inventory.mainInventory) existing(item,known);
            existing(in.getStack(),known);existing(out.getStack(),known);
            List<NBTTagCompound> keys=new ArrayList<>();
            for(Entry entry:entries) keys.add(entry.status==null ? signature(entry.data) : null);
            List<PatternBatchPolicy.Status> classified=PatternBatchPolicy.classify(keys,known);
            int writable=0;Set<NBTTagCompound> approved=new HashSet<>();
            for(int i=0;i<entries.size();i++) {
                if(classified.get(i)==PatternBatchPolicy.Status.DUPLICATE) entries.get(i).status="跳过：本组或背包已有相同样板";
                if(classified.get(i)==PatternBatchPolicy.Status.READY) {writable++;approved.add(keys.get(i));}
            }
            int blanks=Ae2Terminal.same(in.getStack(),blank)?in.getStack().getCount():0,free=0;
            for(ItemStack item:player.inventory.mainInventory) {
                if(Ae2Terminal.same(item,blank)) blanks+=item.getCount();
                if(item.isEmpty()) free++;
            }
            Pending prior=pending.remove(player);
            boolean confirmed=prior!=null && prior.container==c && prior.request.equals(request) && prior.approved.equals(approved) && System.currentTimeMillis()-prior.at<30000;
            if(writable==0) {
                report(player,entries);
                message(player,"本次没有需要编码的配方：均已跳过（重复或不支持）；未消耗空白样板。");return;
            }
            if(!PatternBatchPolicy.canEncode(writable,blanks,free)) {
                for(Entry entry:entries) if(entry.status==null) entry.status="未处理：空白样板或背包空间不足";
                report(player,entries);
                message(player,"需 "+writable+" 张空白样板和 "+writable+" 个空背包格；现有 "+blanks+" 张、"+free+" 格。本次未编码。");return;
            }
            if(!confirmed) {
                pending.put(player,new Pending(c,request,approved));
                for(Entry entry:entries) if(entry.status==null) entry.status="待编码（单次配方）";
                report(player,entries);
                message(player,"预检通过：将消耗 "+writable+" 张空白样板。30 秒内再次按样板快捷键编码；材料选用本次预览的具体物品。");
                for(Entry entry:entries) if(entry.status.startsWith("待编码")) message(player,entry.name()+"："+inputsText(entry.data));
                return;
            }
            execute(player,c,in,out,blank,entries);
        } catch(ReflectiveOperationException | RuntimeException ex) {
            pending.remove(player);Log.get().error("AE pattern batch failed",ex);message(player,"样板操作已停止，请查看日志；请勿重复点击。");
        }
    }
    private static List<Entry> prepare(EntityPlayer player,NBTTagList raw) {
        List<Entry> result=new ArrayList<>();
        for(int i=0;i<raw.tagCount();i++) {
            NBTTagCompound data=raw.getCompoundTagAt(i).copy();String status=null;
            try {
                if(data.hasKey("skip")) status="跳过：非九宫格配方或布局无法读取";
                else {
                    NBTTagList inputs=data.getTagList("in",10);
                    if(inputs.tagCount()!=9) throw new IllegalArgumentException();
                    InventoryCrafting grid=new InventoryCrafting(new Container() {public boolean canInteractWith(EntityPlayer p) {return false;}},3,3);
                    for(int j=0;j<9;j++) {
                        ItemStack item=new ItemStack(inputs.getCompoundTagAt(j));
                        if(!item.isEmpty() && item.getCount()!=1) throw new IllegalArgumentException();
                        grid.setInventorySlotContents(j,item);
                    }
                    IRecipe recipe=CraftingManager.findMatchingRecipe(grid,player.world);
                    ItemStack expected=new ItemStack(data.getCompoundTag("expected"));
                    if(recipe==null || expected.isEmpty() || !ItemStack.areItemStacksEqual(recipe.getCraftingResult(grid),expected)) throw new IllegalArgumentException();
                }
            } catch(RuntimeException ex) {status="跳过：服务端无法验证配方或产物";}
            result.add(new Entry(data,status));
        }
        return result;
    }
    private static NBTTagCompound signature(NBTTagCompound data) {
        NBTTagCompound signature=new NBTTagCompound();
        signature.setTag("in",data.getTagList("in",10).copy());
        NBTTagList output=new NBTTagList();output.appendTag(data.getCompoundTag("expected").copy());
        signature.setTag("out",output);return signature;
    }
    private static void existing(ItemStack item,Set<NBTTagCompound> known) {
        if(item.isEmpty() || !item.getItem().getClass().getName().equals("appeng.items.misc.ItemEncodedPattern") || !item.hasTagCompound()) return;
        NBTTagCompound tag=item.getTagCompound();
        if(!tag.getBoolean("crafting") || tag.getBoolean("substitute")) return;
        NBTTagCompound signature=new NBTTagCompound();signature.setTag("in",tag.getTagList("in",10).copy());signature.setTag("out",tag.getTagList("out",10).copy());known.add(signature);
    }
    private static ItemStack blankPattern() throws ReflectiveOperationException {
        Object api=Class.forName("appeng.api.AEApi").getMethod("instance").invoke(null);
        Object definition=Ae2Terminal.call(Ae2Terminal.call(Ae2Terminal.call(api,"definitions"),"materials"),"blankPattern");
        Method method=definition.getClass().getMethod("maybeStack",int.class);method.setAccessible(true);
        return ((Optional<ItemStack>)method.invoke(definition,1)).orElseThrow(()->new IllegalStateException("Missing blank pattern"));
    }
    private static String inputsText(NBTTagCompound data) {
        Map<String,Integer> counts=new LinkedHashMap<>();NBTTagList list=data.getTagList("in",10);
        for(int i=0;i<list.tagCount();i++) {ItemStack item=new ItemStack(list.getCompoundTagAt(i));if(!item.isEmpty()) counts.merge(item.getDisplayName(),item.getCount(),Integer::sum);}
        return counts.toString();
    }
    private static int countBlanks(EntityPlayer player,Slot in,ItemStack blank) {
        int count=Ae2Terminal.same(in.getStack(),blank)?in.getStack().getCount():0;
        for(ItemStack item:player.inventory.mainInventory) if(Ae2Terminal.same(item,blank)) count+=item.getCount();
        return count;
    }
    private static void report(EntityPlayer player,List<Entry> entries) {
        int i=0;for(Entry entry:entries) message(player,(++i)+". "+entry.name()+" — "+entry.status);
    }
    private static Slot[] craftingGrid(Container c) throws ReflectiveOperationException {
        Slot[] all=(Slot[])field(c,"craftingSlots");
        boolean rc=false;
        if(all.length!=9) {
            try {rc=Boolean.TRUE.equals(Ae2Terminal.call(c,"isRCPatternEncoder"));}
            catch(NoSuchMethodException ignored) {}
        }
        Slot[] grid=PatternBatchPolicy.craftingGrid(all,rc);
        for(Slot slot:grid) if(slot==null) throw new IllegalStateException("Missing crafting slot");
        return grid;
    }
    private static void execute(EntityPlayer player,Container c,Slot in,Slot out,ItemStack blank,List<Entry> entries) throws ReflectiveOperationException {
        Slot[] grid=craftingGrid(c);
        List<ItemStack> original=new ArrayList<>();for(Slot slot:grid) original.add(slot.getStack().copy());
        boolean substitute=c.getClass().getField("substitute").getBoolean(c);
        Method setSubstitute=c.getClass().getMethod("setSubstitute",boolean.class);
        boolean stopped=false;int done=0;
        try {
            setSubstitute.invoke(c,false);
            for(Entry entry:entries) {
                if(entry.status!=null) continue;
                if(stopped) {entry.status="未处理：此前编码已停止";continue;}
                List<ItemStack> inventory=new ArrayList<>();for(ItemStack item:player.inventory.mainInventory) inventory.add(item.copy());
                ItemStack beforeIn=in.getStack().copy();
                int beforeBlanks=countBlanks(player,in,blank);
                try {
                    if(player.openContainer!=c || !c.canInteractWith(player) || !out.getStack().isEmpty() || !Boolean.TRUE.equals(Ae2Terminal.call(c,"isPowered"))) throw new IllegalStateException("Terminal changed");
                    int target=-1;for(int i=0;i<inventory.size();i++) if(inventory.get(i).isEmpty()) {target=i;break;}
                    if(target<0) throw new IllegalStateException("Inventory full");
                    if(in.getStack().isEmpty()) {
                        for(int i=0;i<player.inventory.mainInventory.size();i++) if(Ae2Terminal.same(player.inventory.mainInventory.get(i),blank)) {
                            ItemStack moved=player.inventory.mainInventory.get(i).splitStack(1);in.putStack(moved);break;
                        }
                    }
                    if(!Ae2Terminal.same(in.getStack(),blank)) throw new IllegalStateException("Missing blank");
                    NBTTagList inputs=entry.data.getTagList("in",10);
                    for(int i=0;i<9;i++) grid[i].putStack(new ItemStack(inputs.getCompoundTagAt(i)));
                    Ae2Terminal.call(c,"encode");
                    if(countBlanks(player,in,blank)!=beforeBlanks-1) throw new IllegalStateException("Incorrect blank pattern consumption");
                    ItemStack encoded=out.getStack();Set<NBTTagCompound> actual=new HashSet<>();existing(encoded,actual);
                    if(encoded.getCount()!=1 || !actual.contains(signature(entry.data))) throw new IllegalStateException("Encoded pattern mismatch");
                    player.inventory.mainInventory.set(target,encoded.copy());out.putStack(ItemStack.EMPTY);
                    entry.status="成功：已放入背包";done++;
                } catch(ReflectiveOperationException | RuntimeException ex) {
                    // Native encode touches only these slots; no ME extraction or world drops are used.
                    for(int i=0;i<inventory.size();i++) player.inventory.mainInventory.set(i,inventory.get(i));
                    in.putStack(beforeIn);out.putStack(ItemStack.EMPTY);
                    entry.status="失败：本项已回退，后续停止";stopped=true;Log.get().warn("Pattern entry rolled back",ex);
                }
            }
        } finally {
            for(int i=0;i<9;i++) grid[i].putStack(original.get(i));
            setSubstitute.invoke(c,substitute);
            player.inventory.markDirty();c.detectAndSendChanges();
        }
        report(player,entries);message(player,"本次成功编码 "+done+" 张样板。原书签目标保留。");
    }
}
