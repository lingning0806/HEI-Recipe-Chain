package mezz.jei.nova.suite;
import mezz.jei.JustEnoughItems;
import mezz.jei.api.gui.*;
import mezz.jei.autocrafting.*;
import mezz.jei.nova.*;
import mezz.jei.nova.pattern.PatternMaterialGuides;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import java.util.*;

/** Selected nine-slot material projection; filling never takes the recipe output. */
public final class RecipeProjection {
    private static Object screen;private static Container container;private static NBTTagCompound plan;private static ItemStack output;private static int batches;private static RecipeBookmarkItem<?> selected;private static long selectedAmount;
    public static void clear() {selected=null;plan=null;screen=null;container=null;output=ItemStack.EMPTY;}
    public static List<Slot> grid(Container c) {return CraftingGrid.grid(c);}
    private static NBTTagCompound id(ItemStack stack) {ItemStack copy=stack.copy();copy.setCount(1);return copy.writeToNBT(new NBTTagCompound());}
    public static void select(RecipeBookmarkItem<?> recipe) {
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.player==null || recipe.category==null || grid(mc.player.openContainer).size()!=9 || !"minecraft.crafting".equals(recipe.category.getUid())) {
            CraftDiagnostics.notice("请在原版工作台或 AE 合成终端投影九宫格配方；样板终端请用 Shift+P 编码");return;
        }
        try {
            PatternMaterialGuides<NBTTagCompound> guides=new PatternMaterialGuides<>();
            for(RecipeBookmarkItem<?> input:recipe.inputs)if(input.ingredient instanceof ItemStack) {
                List<NBTTagCompound> family=new ArrayList<>();for(Object alias:input.aliases)if(alias instanceof ItemStack && !((ItemStack)alias).isEmpty())family.add(id((ItemStack)alias));
                guides.add(family,id((ItemStack)input.ingredient),input.amount);
            }
            NBTTagList slots=new NBTTagList();ItemStack expected=ItemStack.EMPTY;
            for(IGuiIngredient<ItemStack> slot:new TreeMap<>(recipe.createLayout().getItemStacks().getGuiIngredients()).values()) {
                if(slot.isInput()) {List<NBTTagCompound> family=new ArrayList<>();for(ItemStack item:slot.getAllIngredients())if(item!=null && !item.isEmpty())family.add(id(item));slots.appendTag(family.isEmpty()?new NBTTagCompound():guides.take(family));}
                else if(expected.isEmpty() && slot.getDisplayedIngredient()!=null)expected=slot.getDisplayedIngredient().copy();
            }
            if(slots.tagCount()!=9 || expected.isEmpty())throw new IllegalArgumentException();
            plan=new NBTTagCompound();plan.setTag("in",slots);plan.setTag("out",expected.writeToNBT(new NBTTagCompound()));
            selected=recipe;selectedAmount=recipe.getMultiplier();
            batches=(int)Math.min(64,Math.max(1,recipe.getMultiplier()));
            List<Slot> grid=grid(mc.player.openContainer);
            for(int i=0;i<9;i++){ItemStack item=new ItemStack(slots.getCompoundTagAt(i));if(!item.isEmpty())batches=TransferLimits.fill(batches,item.getMaxStackSize(),grid.get(i).getSlotStackLimit());}
            screen=mc.currentScreen;container=mc.player.openContainer;output=expected;
            CraftDiagnostics.notice((conflicts().isEmpty()?"已投影 ":"已选择投影，需先移走冲突材料：")+batches+" 次配方（"+expected.getDisplayName()+"）；再次按投影键填充，成品由你手动取出；X 取消投影");
        } catch(RuntimeException ex) {mezz.jei.util.Log.get().warn("Recipe projection selection failed",ex);CraftDiagnostics.notice("无法映射书签材料到九宫格，未提交填充");clear();}
    }
    private static BitSet conflicts() {
        List<ItemStack> actual=new ArrayList<>(), expected=new ArrayList<>();
        NBTTagList items=plan.getTagList("in",10);
        for(Slot slot:grid(container))actual.add(slot.getStack());
        for(int i=0;i<9;i++)expected.add(new ItemStack(items.getCompoundTagAt(i)));
        return ProjectionGridState.conflicts(actual,expected,ItemStack::isEmpty,mezz.jei.nova.ae2.Ae2Terminal::same);
    }
    public static boolean isSelected(RecipeBookmarkItem<?> recipe) {return selected==recipe && selectedAmount==recipe.getMultiplier();}
    public static boolean fill() {
        Minecraft mc=Minecraft.getMinecraft();if(plan==null || mc.player==null || container!=mc.player.openContainer || screen!=mc.currentScreen)return false;
        if(!conflicts().isEmpty()) {
            CraftDiagnostics.notice("投影暂停：请先移走红框中的旧材料；无需合成旧配方，移走后自动恢复预览");return true;
        }
        JustEnoughItems.getProxy().sendPacketToServer(new mezz.jei.network.packets.PacketProjectionFill(container.windowId,batches,plan.copy()));return true;
    }
    public static void draw() {
        Minecraft mc=Minecraft.getMinecraft();if(plan==null)return;
        if(mc.currentScreen!=screen || mc.player==null || mc.player.openContainer!=container) {clear();return;}
        GuiContainer gui=(GuiContainer)mc.currentScreen;List<Slot> slots=grid(container);NBTTagList items=plan.getTagList("in",10);
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.translate(0,0,200);
        net.minecraft.client.renderer.GlStateManager.disableDepth();
        net.minecraft.client.renderer.GlStateManager.depthMask(false);
        try {
            BitSet conflicts=conflicts();
            if(!conflicts.isEmpty()) {
                for(int i=conflicts.nextSetBit(0);i>=0;i=conflicts.nextSetBit(i+1)) {
                    int x=gui.getGuiLeft()+slots.get(i).xPos,y=gui.getGuiTop()+slots.get(i).yPos;
                    net.minecraft.client.gui.Gui.drawRect(x-1,y-1,x+17,y,0xFFFF6868);
                    net.minecraft.client.gui.Gui.drawRect(x-1,y+16,x+17,y+17,0xFFFF6868);
                    net.minecraft.client.gui.Gui.drawRect(x-1,y,x,y+16,0xFFFF6868);
                    net.minecraft.client.gui.Gui.drawRect(x+16,y,x+17,y+16,0xFFFF6868);
                }
                String title="投影暂停："+output.getDisplayName()+" × "+batches+" 次";
                String hint="移走红框旧材料后恢复；输出槽仍是实际配方";
                int width=Math.min(gui.width-8,Math.max(mc.fontRenderer.getStringWidth(title),mc.fontRenderer.getStringWidth(hint))+8);
                int x=Math.max(4,Math.min(gui.width-width-4,gui.getGuiLeft()+slots.get(0).xPos-2));
                int y=Math.max(4,gui.getGuiTop()+slots.get(0).yPos-28);
                net.minecraft.client.gui.Gui.drawRect(x,y,x+width,y+25,0xFF202832);
                mc.fontRenderer.drawStringWithShadow(mc.fontRenderer.trimStringToWidth(title,width-8),x+4,y+3,0xFF9292);
                mc.fontRenderer.drawStringWithShadow(mc.fontRenderer.trimStringToWidth(hint,width-8),x+4,y+14,0xFFFFFF);
                return;
            }
            for(int i=0;i<slots.size();i++)if(slots.get(i).getStack().isEmpty()) {
                ItemStack stack=new ItemStack(items.getCompoundTagAt(i));if(stack.isEmpty())continue;
                int x=gui.getGuiLeft()+slots.get(i).xPos,y=gui.getGuiTop()+slots.get(i).yPos;
                net.minecraft.client.gui.Gui.drawRect(x-1,y-1,x+17,y+17,0xAA508A9D);
                net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
                mc.getRenderItem().renderItemAndEffectIntoGUI(stack,x,y);
                net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
                net.minecraft.client.renderer.GlStateManager.disableDepth();
                net.minecraft.client.gui.Gui.drawRect(x,y,x+16,y+16,0x66304555);
                net.minecraft.client.renderer.GlStateManager.pushMatrix();
                net.minecraft.client.renderer.GlStateManager.translate(x+16,y+16,0);
                net.minecraft.client.renderer.GlStateManager.scale(0.65f,0.65f,1);
                String count=String.valueOf(batches);mc.fontRenderer.drawStringWithShadow(count,-mc.fontRenderer.getStringWidth(count),-9,0xA8F5FF);
                net.minecraft.client.renderer.GlStateManager.popMatrix();
            }
        } finally {
            net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
            net.minecraft.client.renderer.GlStateManager.color(1,1,1,1);
            net.minecraft.client.renderer.GlStateManager.depthMask(true);
            net.minecraft.client.renderer.GlStateManager.enableDepth();
            net.minecraft.client.renderer.GlStateManager.popMatrix();
        }
    }
}
