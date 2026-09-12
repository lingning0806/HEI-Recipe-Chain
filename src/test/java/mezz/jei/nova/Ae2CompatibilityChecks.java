package mezz.jei.nova;
import mezz.jei.nova.ae2.Ae2Terminal;
public final class Ae2CompatibilityChecks {
    public static class Filter {
        private final boolean empty;
        public Filter(boolean empty) { this.empty=empty; }
        public boolean isEmpty() { return empty; }
    }
    public static void main(String[] args) throws Exception {
        net.minecraft.init.Bootstrap.register();
        net.minecraft.item.ItemStack gold=new net.minecraft.item.ItemStack(net.minecraft.init.Blocks.GOLD_BLOCK);
        net.minecraft.item.ItemStack iron=new net.minecraft.item.ItemStack(net.minecraft.init.Blocks.IRON_BLOCK);
        net.minecraft.nbt.NBTTagList candidates=new net.minecraft.nbt.NBTTagList();
        candidates.appendTag(gold.serializeNBT());
        if(mezz.jei.nova.ae2.Ae2ExactGrid.matches(iron,candidates))throw new AssertionError("Iron block cannot fill gold block slot");
        gold.setCount(40);
        if(!mezz.jei.nova.ae2.Ae2ExactGrid.matches(gold,candidates))throw new AssertionError("Stack quantity does not change identity");
        gold.setTagCompound(new net.minecraft.nbt.NBTTagCompound());gold.getTagCompound().setString("custom","different");
        if(mezz.jei.nova.ae2.Ae2ExactGrid.matches(gold,candidates))throw new AssertionError("NBT must match");
        if(!mezz.jei.nova.ae2.Ae2ExactGrid.matches(net.minecraft.item.ItemStack.EMPTY,new net.minecraft.nbt.NBTTagList()))throw new AssertionError("Empty recipe slot");

        if(!Ae2Terminal.unrestricted(null)) throw new AssertionError("No view cell must allow network stock");
        if(!Ae2Terminal.unrestricted(new Filter(true))) throw new AssertionError("Empty view filter");
        if(Ae2Terminal.unrestricted(new Filter(false))) throw new AssertionError("View filter must remain active");
        if(!Ae2Terminal.acceptsHandler("com.github.vfyjxf.nee.jei.CraftingTransferHandler")) throw new AssertionError("Installed NEE replacement");
        if(!Ae2Terminal.acceptsHandler("appeng.integration.modules.jei.RecipeTransferHandler")) throw new AssertionError("Native AE handler");
        if(Ae2Terminal.acceptsHandler("other.CraftingTransferHandler")) throw new AssertionError("Unknown handler must not be wrapped");
        if(!mezz.jei.nova.ae2.Ae2Batch.canAppend(4,252,4)) throw new AssertionError("64 batches allowed");
        if(mezz.jei.nova.ae2.Ae2Batch.canAppend(256,4,4)) throw new AssertionError("Batch cap");
        if(mezz.jei.nova.ae2.Ae2Batch.canAppend(Long.MAX_VALUE,4,4)) throw new AssertionError("Overflow");
        if(mezz.jei.nova.ae2.Ae2Batch.canAppend(3,4,4)) throw new AssertionError("Partial output must not merge");
        if(mezz.jei.nova.ae2.Ae2Batch.shouldYield(0,9_000_000)) throw new AssertionError("Always attempt first operation");
        if(!mezz.jei.nova.ae2.Ae2Batch.shouldYield(1,8_000_000)) throw new AssertionError("Yield slow batch");
        if(mezz.jei.nova.ae2.Ae2Batch.nativeStackOperations(4,64,16)!=16) throw new AssertionError("Native full stack");
        if(mezz.jei.nova.ae2.Ae2Batch.nativeStackOperations(4,64,15)!=1) throw new AssertionError("Must not overcraft target");
        if(mezz.jei.nova.ae2.Ae2Batch.nativeStackOperations(1,64,64)!=1) throw new AssertionError("Long uninterruptible stacks remain single");
        if(mezz.jei.nova.ae2.Ae2Batch.nativeStackOperations(8,64,9)!=8) throw new AssertionError("Partial final batch");
        System.out.println("PASS: AE null/empty/active view filters and NEE/native handler compatibility");
    }
}
