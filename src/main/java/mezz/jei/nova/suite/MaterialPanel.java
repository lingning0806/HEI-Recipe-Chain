package mezz.jei.nova.suite;

import mezz.jei.Internal;
import mezz.jei.autocrafting.RecipeBookmarkGroup;
import mezz.jei.config.KeyBindings;
import mezz.jei.input.MouseHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fluids.FluidStack;
import org.lwjgl.input.Keyboard;
import java.awt.Rectangle;
import java.util.*;

/** Categorized material panel rendered above the current container, with no inventory mutations. */
public final class MaterialPanel {
    private static RecipeBookmarkGroup group;
    private static GuiScreen screen;
    private static MaterialPanelLayout layout;
    private static MaterialPanelModel.Section section=MaterialPanelModel.Section.MATERIALS;
    private static java.util.List<MaterialPanelModel.Row> visible=Collections.emptyList();
    private static String selected;
    private static int page;
    private static final Set<String> failedIcons=new HashSet<>();
    public static void toggle(RecipeBookmarkGroup value){if(group==value)close();else{group=value;screen=Minecraft.getMinecraft().currentScreen;page=0;section=MaterialPanelModel.Section.MATERIALS;selected=null;layout=null;visible=Collections.emptyList();}}
    public static boolean active(){if(screen!=Minecraft.getMinecraft().currentScreen)close();return group!=null;}
    public static void close(){group=null;layout=null;selected=null;visible=Collections.emptyList();}
    public static boolean click(int x,int y){return active() && layout!=null && layout.panel.contains(x,y);}
    private static void turn(int direction){page=layout==null?0:layout.clamp(page+direction,visible.size());selected=null;}
    private static void category(int index){section=MaterialPanelModel.Section.values()[Math.floorMod(index,MaterialPanelModel.Section.values().length)];page=0;selected=null;visible=Collections.emptyList();}
    public static boolean key(int key) {
        if(!active())return false;
        if(key==Keyboard.KEY_X || KeyBindings.materialDetails.isActiveAndMatches(key)){close();return true;}
        if(key==Keyboard.KEY_RIGHT){turn(1);return true;}
        if(key==Keyboard.KEY_LEFT){turn(-1);return true;}
        if(key==Keyboard.KEY_TAB){category(section.ordinal()+(GuiScreen.isShiftKeyDown()?-1:1));return true;}
        return false;
    }
    public static boolean mouse(int x,int y,int button,boolean down,int wheel) {
        if(!active())return false;
        if(layout==null)return true;
        if(wheel!=0){turn(wheel>0?-1:1);return true;}
        if(button!=0 || !down)return true;
        if(layout.close.contains(x,y)){close();return true;}
        for(int i=0;i<layout.tabs.length;i++)if(layout.tabs[i].contains(x,y)){category(i);return true;}
        if(layout.previous.contains(x,y)){turn(-1);return true;}
        if(layout.next.contains(x,y)){turn(1);return true;}
        for(int i=0;i<layout.rowCount;i++)if(layout.row(i).contains(x,y)) {
            int index=page*layout.rowCount+i;if(index<visible.size())selected=visible.get(index).key;return true;
        }
        return true;
    }
    private static void text(Minecraft mc,String value,int x,int y,int color){mc.fontRenderer.drawString(value,x,y,color);}
    private static String fit(Minecraft mc,String text,int width){return mc.fontRenderer.getStringWidth(text)<=width?text:mc.fontRenderer.trimStringToWidth(text,Math.max(0,width-6))+"…";}
    private static String amount(Minecraft mc,long value,int width){String number=MaterialPanelModel.number(value);return mc.fontRenderer.getStringWidth(number)<=width?number:MaterialPanelModel.compact(value);}
    private static void number(Minecraft mc,long value,int right,int y,int width,int color){String label=amount(mc,value,width);text(mc,label,right-mc.fontRenderer.getStringWidth(label),y,value==0?0x657281:color);}
    private static void button(Minecraft mc,Rectangle box,String label,boolean enabled,boolean active,int mouseX,int mouseY) {
        Gui.drawRect(box.x,box.y,box.x+box.width,box.y+box.height,active?0xFF29475A:enabled && box.contains(mouseX,mouseY)?0xFF334352:0xFF222C37);
        if(active)Gui.drawRect(box.x,box.y+box.height-2,box.x+box.width,box.y+box.height,0xFF69D2DB);
        String shown=fit(mc,label,box.width-6);text(mc,shown,box.x+(box.width-mc.fontRenderer.getStringWidth(shown))/2,box.y+(box.height-8)/2,enabled?0xE4EDF5:0x657281);
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void icon(Minecraft mc,GroupMaterials data,String key,int x,int y) {
        Gui.drawRect(x-1,y-1,x+17,y+17,0xFF303C49);
        Object value=data.icon(key);if(value==null || failedIcons.contains(key))return;
        if(value instanceof ItemStack){value=((ItemStack)value).copy();((ItemStack)value).setCount(1);}
        if(value instanceof FluidStack){value=((FluidStack)value).copy();((FluidStack)value).amount=1000;}
        try {
            RenderHelper.enableGUIStandardItemLighting();
            mezz.jei.api.ingredients.IIngredientRenderer renderer=Internal.getIngredientRegistry().getIngredientRenderer(value);
            renderer.render(mc,x,y,value);
        } catch(RuntimeException | LinkageError ex){if(failedIcons.add(key))mezz.jei.util.Log.get().warn("Material panel icon failed: "+key,ex);}
        finally {RenderHelper.disableStandardItemLighting();GlStateManager.disableDepth();GlStateManager.color(1,1,1,1);}
    }
    public static void draw() {
        if(!active())return;Minecraft mc=Minecraft.getMinecraft();int mouseX=MouseHelper.getX(),mouseY=MouseHelper.getY();
        layout=new MaterialPanelLayout(screen.width,screen.height);Rectangle panel=layout.panel;
        GlStateManager.pushMatrix();GlStateManager.translate(0,0,400);GlStateManager.disableDepth();GlStateManager.depthMask(false);RenderHelper.disableStandardItemLighting();GlStateManager.color(1,1,1,1);
        try {
            Gui.drawRect(0,0,screen.width,screen.height,0x99000000);
            Gui.drawRect(panel.x-1,panel.y-1,panel.x+panel.width+1,panel.y+panel.height+1,0xFF42596B);
            Gui.drawRect(panel.x,panel.y,panel.x+panel.width,panel.y+panel.height,0xFF151D26);
            text(mc,"§l材料明细",layout.bodyX,panel.y+12,0xF0F5F9);
            button(mc,layout.close,"×",true,false,mouseX,mouseY);
            GroupMaterials data=group.materials();java.util.List<MaterialPanelModel.Row> all=data.detailRows();visible=MaterialPanelModel.filter(all,section);page=layout.clamp(page,visible.size());
            long shortages=all.stream().filter(r->r.missing>0).count();String status=shortages==0?"无材料缺口":"缺口 "+shortages+" 类";
            int statusWidth=mc.fontRenderer.getStringWidth(status);text(mc,status,panel.x+panel.width-40-statusWidth,panel.y+12,shortages==0?0x8FDCBA:0xFF8D88);
            String goal="本次剩余计划";
            if(!data.originalGoals.isEmpty()){Map.Entry<String,Long> first=data.originalGoals.entrySet().iterator().next();goal="目标："+data.name(first.getKey())+" × "+MaterialPanelModel.number(first.getValue());if(data.originalGoals.size()>1)goal+=" 等 "+data.originalGoals.size()+" 项";}
            text(mc,fit(mc,goal,layout.bodyWidth),layout.bodyX,panel.y+32,0xA9BAC9);
            MaterialPanelModel.Section[] categories=MaterialPanelModel.Section.values();
            for(int i=0;i<categories.length;i++)button(mc,layout.tabs[i],categories[i].title,true,section==categories[i],mouseX,mouseY);
            text(mc,"物品 / 流体",layout.bodyX+3,layout.headerY,0x94A7B8);String[] headings=MaterialPanelModel.headers(section);
            for(int j=0;j<3;j++){String heading=fit(mc,headings[j],layout.columnWidth-8);text(mc,heading,layout.columnRight(j)-mc.fontRenderer.getStringWidth(heading),layout.headerY,0x94A7B8);}
            MaterialPanelModel.Row detail=null,hovered=null;
            for(int i=0;i<layout.rowCount;i++) {
                int index=page*layout.rowCount+i;if(index>=visible.size())break;MaterialPanelModel.Row row=visible.get(index);Rectangle box=layout.row(i);
                boolean hover=box.contains(mouseX,mouseY),pinned=row.key.equals(selected);
                Gui.drawRect(box.x,box.y,box.x+box.width,box.y+box.height-1,hover||pinned?0xFF2C4151:i%2==0?0xFF202B36:0xFF1B2530);
                if(row.missing>0)Gui.drawRect(box.x,box.y,box.x+2,box.y+box.height-1,0xFFFF8D88);
                else if(pinned)Gui.drawRect(box.x,box.y,box.x+2,box.y+box.height-1,0xFF69D2DB);
                icon(mc,data,row.key,box.x+4,box.y+3);
                String name=TextFormatting.getTextWithoutFormattingCodes(data.name(row.key));
                text(mc,fit(mc,name==null?row.key:name,layout.nameWidth-28),box.x+26,box.y+7,0xE2EAF1);
                long[] values=row.values(section);
                for(int j=0;j<3;j++){boolean missing=(section==MaterialPanelModel.Section.MISSING && j==0 || (section==MaterialPanelModel.Section.MATERIALS || section==MaterialPanelModel.Section.ALL) && j==2);number(mc,values[j],layout.columnRight(j),box.y+7,layout.columnWidth-8,missing && values[j]>0?0xFF8D88:0xC3DBE8);}
                if(pinned)detail=row;if(hover)hovered=row;
            }
            if(visible.isEmpty()){text(mc,MaterialPanelModel.empty(section),layout.bodyX+8,layout.rowsY+15,section==MaterialPanelModel.Section.MISSING?0x8FDCBA:0xA9BAC9);}
            if(hovered!=null)detail=hovered;
            Gui.drawRect(layout.bodyX,layout.footerY,layout.bodyX+layout.bodyWidth,layout.footerY+1,0xFF354656);
            if(detail==null) {
                mc.fontRenderer.drawSplitString(MaterialPanelModel.description(section),layout.bodyX,layout.footerY+8,layout.bodyWidth,0xA9BAC9);
                text(mc,"悬停 / 点击查看明细 · Shift 查看完整数值",layout.bodyX,layout.footerY+31,0x798D9E);
            } else {
                text(mc,fit(mc,data.name(detail.key)+" · "+data.unit(detail.key),layout.bodyWidth),layout.bodyX,layout.footerY+6,0xE2EAF1);
                String first="总用量 "+MaterialPanelModel.compact(detail.usage)+"    起步需备 "+MaterialPanelModel.compact(detail.needed)+"    背包/格 "+MaterialPanelModel.compact(detail.player);
                String second="AE "+MaterialPanelModel.compact(detail.network)+"    仍缺 "+MaterialPanelModel.compact(detail.missing)+"    预计剩余 "+MaterialPanelModel.compact(detail.remainder);
                text(mc,fit(mc,first,layout.bodyWidth),layout.bodyX,layout.footerY+18,0xA9BAC9);text(mc,fit(mc,second,layout.bodyWidth),layout.bodyX,layout.footerY+30,0xA9BAC9);
            }
            text(mc,fit(mc,"← / → 翻页 · Tab 分类 · X 关闭",layout.bodyWidth-94),layout.bodyX,panel.y+panel.height-17,0x94A7B8);
            button(mc,layout.previous,"←",page>0,false,mouseX,mouseY);button(mc,layout.next,"→",page+1<layout.pages(visible.size()),false,mouseX,mouseY);
            String pageText=(page+1)+"/"+layout.pages(visible.size());text(mc,pageText,layout.previous.x+25,layout.previous.y+4,0xB7C8D6);
            if(hovered!=null && GuiScreen.isShiftKeyDown()) {
                java.util.List<String> tooltip=new ArrayList<>();tooltip.add(data.name(hovered.key)+"（"+data.unit(hovered.key)+"）");
                tooltip.add("总用量："+MaterialPanelModel.number(hovered.usage));tooltip.add("起步需备："+MaterialPanelModel.number(hovered.needed));tooltip.add("背包/格："+MaterialPanelModel.number(hovered.player));tooltip.add("AE："+MaterialPanelModel.number(hovered.network));tooltip.add("缺口："+MaterialPanelModel.number(hovered.missing));tooltip.add("预计剩余："+MaterialPanelModel.number(hovered.remainder));
                mezz.jei.gui.TooltipRenderer.drawHoveringText(mc,tooltip,mouseX,mouseY);
            }
        } catch(RuntimeException ex){text(mc,"无法计算：请检查依赖或减少目标数量",layout.bodyX,layout.rowsY+8,0xFF8D88);}
        finally {RenderHelper.disableStandardItemLighting();GlStateManager.color(1,1,1,1);GlStateManager.depthMask(true);GlStateManager.enableDepth();GlStateManager.popMatrix();}
    }
}
