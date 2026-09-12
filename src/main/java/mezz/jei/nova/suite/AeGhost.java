package mezz.jei.nova.suite;
import mezz.jei.api.gui.IGhostIngredientHandler;
import mezz.jei.autocrafting.RecipeBookmarkGroup;
import mezz.jei.bookmarks.BookmarkItem;
import mezz.jei.gui.GuiScreenHelper;
import mezz.jei.nova.CraftDiagnostics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import java.awt.Rectangle;
import java.util.*;

/** Delegate configuration writes to the registered native JEI ghost targets. */
@SuppressWarnings({"unchecked","rawtypes"})
public final class AeGhost {
    public static GuiScreenHelper helper;
    private static boolean ae(GuiScreen gui) {
        for(Class<?> c=gui.getClass();c!=null;c=c.getSuperclass())if(c.getName().equals("appeng.client.gui.AEBaseGui"))return true;return false;
    }
    public static <T extends GuiScreen> IGhostIngredientHandler<T> wrap(T gui,IGhostIngredientHandler<T> base) {
        if(!ae(gui))return base;
        return new IGhostIngredientHandler<T>() {
            public <I> List<Target<I>> getTargets(T screen,I ingredient,boolean start) {
                List<Target<I>> targets=new ArrayList<>();if(base!=null)targets.addAll(base.getTargets(screen,ingredient,start));
                AeSearchAccess search=ingredient instanceof ItemStack?AeSearchAccess.find(screen):null;
                if(search!=null) targets.add(new Target<I>() {
                    public Rectangle getArea(){return search.area();}
                    public void accept(I value){search.apply((ItemStack)value);}
                });
                return targets;
            }
            public void onComplete() {if(base!=null)base.onComplete();}
        };
    }
    public static boolean drop(RecipeBookmarkGroup group,int x,int y) {
        GuiScreen gui=Minecraft.getMinecraft().currentScreen;
        if(!(gui instanceof GuiContainer) || !ae(gui) || helper==null)return false;
        // Only configuration containers, never crafting/pattern grids.
        String name=((GuiContainer)gui).inventorySlots.getClass().getName();
        if(!Arrays.asList("appeng.container.implementations.ContainerUpgradeable","appeng.container.implementations.ContainerCellWorkbench","appeng.container.implementations.ContainerStorageBus","appeng.container.implementations.ContainerImportBus","appeng.container.implementations.ContainerExportBus").contains(name))return false;
        IGhostIngredientHandler<GuiScreen> handler=helper.getGhostIngredientHandler(gui);if(handler==null)return false;
        List<BookmarkItem<?>> source=group.getItems();Set<String> seen=new HashSet<>();Set<Rectangle> used=new HashSet<>();
        Rectangle start=null;int done=0;
        for(BookmarkItem<?> bookmark:source) {
            if(!(bookmark.ingredient instanceof ItemStack))continue;ItemStack item=((ItemStack)bookmark.ingredient).copy();item.setCount(1);
            if(!seen.add(mezz.jei.nova.preferences.Preferences.identity(item)))continue;
            List<IGhostIngredientHandler.Target<ItemStack>> targets=handler.getTargets(gui,item,true);
            boolean already=false;
            for(IGhostIngredientHandler.Target<ItemStack> target:targets)for(net.minecraft.inventory.Slot slot:((GuiContainer)gui).inventorySlots.inventorySlots) {
                if(target.getArea().contains(((GuiContainer)gui).getGuiLeft()+slot.xPos+8,((GuiContainer)gui).getGuiTop()+slot.yPos+8) && mezz.jei.nova.ae2.Ae2Terminal.same(slot.getStack(),item))already=true;
            }
            if(already)continue;
            targets.sort(Comparator.comparingInt((IGhostIngredientHandler.Target<ItemStack> t)->t.getArea().y).thenComparingInt(t->t.getArea().x));
            if(start==null)for(IGhostIngredientHandler.Target<ItemStack> target:targets)if(target.getArea().contains(x,y)) {start=target.getArea();break;}
            if(start==null)return false;
            for(IGhostIngredientHandler.Target<ItemStack> target:targets) {
                Rectangle area=target.getArea();if(area.y<start.y || area.y==start.y && area.x<start.x || used.contains(area))continue;
                boolean empty=false;for(net.minecraft.inventory.Slot slot:((GuiContainer)gui).inventorySlots.inventorySlots) {
                    if(area.contains(((GuiContainer)gui).getGuiLeft()+slot.xPos+8,((GuiContainer)gui).getGuiTop()+slot.yPos+8) && slot.getStack().isEmpty()) {empty=true;break;}
                }
                if(!empty)continue;
                target.accept(item);used.add(new Rectangle(area));done++;break;
            }
        }
        handler.onComplete();CraftDiagnostics.notice("已提交 "+done+" 项过滤设置；重复物品去重，已有槽位保留");return true;
    }
}
