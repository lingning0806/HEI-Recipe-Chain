package mezz.jei.nova.suite;
import mezz.jei.api.gui.IGhostIngredientHandler;
import mezz.jei.bookmarks.BookmarkItem;
import mezz.jei.input.IClickedIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import java.awt.Rectangle;

/** Defer a HEI item click until release so a bookmark drag never opens its recipe first. */
public final class AeSearchDrag {
    private GuiScreen screen;
    private PointerGesture gesture;
    private ItemStack item=ItemStack.EMPTY;
    public boolean active(){if(screen!=Minecraft.getMinecraft().currentScreen)clear();return gesture!=null;}
    public boolean begin(GuiScreen screen,IClickedIngredient<?> clicked,int x,int y) {
        if(clicked==null || AeSearchAccess.find(screen)==null)return false;
        Object value=clicked.getValue();while(value instanceof BookmarkItem)value=((BookmarkItem<?>)value).ingredient;
        if(!(value instanceof ItemStack) || ((ItemStack)value).isEmpty())return false;
        if(Minecraft.getMinecraft().player==null || !Minecraft.getMinecraft().player.inventory.getItemStack().isEmpty())return false;
        this.screen=screen;this.gesture=new PointerGesture(x,y);this.item=((ItemStack)value).copy();item.setCount(1);
        return true;
    }
    public void move(int x,int y){if(active())gesture.move(x,y);}
    public PointerGesture gesture(){return gesture;}
    public void clear(){gesture=null;screen=null;item=ItemStack.EMPTY;}
    public void release(int x,int y) {
        try {
            AeSearchAccess search=AeSearchAccess.find(screen);
            if(search!=null && search.area().contains(x,y)) {search.apply(item);return;}
            if(AeGhost.helper!=null) {
                IGhostIngredientHandler<GuiScreen> handler=AeGhost.helper.getGhostIngredientHandler(screen);
                if(handler!=null)for(IGhostIngredientHandler.Target<ItemStack> target:handler.getTargets(screen,item,true))if(target.getArea().contains(x,y)) {target.accept(item);handler.onComplete();return;}
            }
        }catch(RuntimeException | LinkageError ex){mezz.jei.util.Log.get().warn("AE item drop failed",ex);}
        finally {clear();}
    }
    public void draw(int x,int y) {
        if(!active() || !gesture.isDrag())return;
        Minecraft mc=Minecraft.getMinecraft();AeSearchAccess search=AeSearchAccess.find(screen);
        GlStateManager.disableDepth();
        if(search!=null) {Rectangle area=search.area();net.minecraft.client.gui.Gui.drawRect(area.x,area.y,area.x+area.width,area.y+area.height,area.contains(x,y)?0x8044BB88:0x4444BB88);}
        mc.getRenderItem().renderItemAndEffectIntoGUI(item,x-8,y-8);GlStateManager.enableDepth();
    }
}
