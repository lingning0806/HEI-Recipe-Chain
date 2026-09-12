package mezz.jei.nova;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Mouse;
import java.io.IOException;
import java.util.*;

/** An explicit, read-only plan. Clicking never moves or crafts items. */
public final class PlannerScreen extends GuiScreen {
    private final GuiScreen parent;
    private final PlannerSession session;
    private final List<Row> rows=new ArrayList<>();
    private final Set<String> collapsed=new HashSet<>();
    private int scroll;
    private boolean summary;
    private String selectedKey;
    private Row selectedRow;
    private static final class Row {
        final String key,parent;final int slot,depth;final boolean catalyst;final long amount;
        Row(String key,String parent,int slot,int depth,boolean catalyst,long amount) {
            this.key=key;this.parent=parent;this.slot=slot;this.depth=depth;this.catalyst=catalyst;this.amount=amount;
        }
    }
    public PlannerScreen(GuiScreen parent,PlannerSession session) {this.parent=parent;this.session=session;this.selectedKey=session.root;}
    @Override public void initGui() {
        buttonList.clear();
        buttonList.add(new GuiButton(0,10,32,38,20,"-"));
        buttonList.add(new GuiButton(1,52,32,38,20,"+"));
        buttonList.add(new GuiButton(2,96,32,84,20,summary?"查看配方树":"材料汇总"));
        buttonList.add(new GuiButton(3,184,32,70,20,"复制清单"));
        buttonList.add(new GuiButton(4,10+0*((width-20)/4),height-48,(width-24)/4,20,"换配方"));
        buttonList.add(new GuiButton(5,10+1*((width-20)/4),height-48,(width-24)/4,20,"提供/展开"));
        buttonList.add(new GuiButton(6,10+2*((width-20)/4),height-48,(width-24)/4,20,"催化剂"));
        buttonList.add(new GuiButton(7,10+3*((width-20)/4),height-48,(width-24)/4,20,"换原料"));
        buttonList.add(new GuiButton(8,width-60,32,50,20,"返回"));
        makeRows();
    }
    private void makeRows() {
        rows.clear();
        if(summary) {
            session.plan.materials.forEach((k,v)->rows.add(new Row(k,null,-1,0,false,v)));
            session.plan.catalysts.forEach((k,v)->rows.add(new Row(k,null,-1,0,true,v)));
        } else tree(session.root,null,-1,0,false,new HashSet<>());
        scroll=Math.max(0,Math.min(scroll,Math.max(0,rows.size()-visibleRows())));
    }
    private int visibleRows() {return Math.max(1,(height-144)/22);}
    private void tree(String key,String parent,int slot,int depth,boolean catalyst,Set<String> path) {
        if(rows.size()>=768)return;
        rows.add(new Row(key,parent,slot,depth,catalyst,catalyst?session.plan.catalysts.getOrDefault(key,1L):session.plan.demands.getOrDefault(key,0L)));
        if(catalyst||collapsed.contains(key)||!path.add(key))return;
        ChainPlanner.Recipe recipe=session.plan.recipes.get(key);
        if(recipe!=null)for(int i=0;i<recipe.inputs.size();i++) {
            ChainPlanner.Input in=recipe.inputs.get(i);tree(in.key,key,i,depth+1,in.catalyst,new HashSet<>(path));
        }
    }
    @Override public void drawScreen(int mouseX,int mouseY,float partialTicks) {
        drawDefaultBackground();
        drawString(fontRenderer,"配方规划："+session.name(session.root)+" × "+session.quantity,10,12,0xFFFFFF);
        drawString(fontRenderer,"左键选择；右键折叠。数量为共享节点总需求。",10,59,0xBBBBBB);
        if(!session.plan.complete())drawString(fontRenderer,"存在循环、数量错误或超出上限；不提供材料总数。请停止相关分支展开。",10,73,0xFF8888);
        else if(!session.warnings.isEmpty())drawString(fontRenderer,fontRenderer.trimStringToWidth(session.warnings.get(0),width-20),10,73,0xFFCC66);
        for(int i=scroll;i<Math.min(rows.size(),scroll+visibleRows());i++) {
            Row row=rows.get(i);int y=88+(i-scroll)*22;
            if(row==selectedRow)drawRect(8,y-2,width-8,y+19,0x66557799);
            int x=14+Math.min(row.depth,12)*14;Object ingredient=session.ingredients.get(row.key);
            if(ingredient instanceof ItemStack)mc.getRenderItem().renderItemAndEffectIntoGUI((ItemStack)ingredient,x,y);
            String label=(row.catalyst?"[催化剂] ":session.plan.recipes.containsKey(row.key)?"[配方] ":"[外部材料] ")
                    +session.name(row.key)+(session.plan.complete()?" × "+row.amount:"");
            drawString(fontRenderer,fontRenderer.trimStringToWidth(label,width-x-35),x+20,y+4,row.catalyst?0xE8C135:0xEEEEEE);
        }
        drawString(fontRenderer,"按 JEI 标示数量规划；概率产物、返还物及并行所需工具请单独核对。",10,height-20,0xBBBBBB);
        super.drawScreen(mouseX,mouseY,partialTicks);
    }
    @Override protected void mouseClicked(int x,int y,int button) throws IOException {
        if(y>=88&&y<height-56) {
            int index=scroll+(y-88)/22;
            if(index<rows.size()) {
                selectedRow=rows.get(index);selectedKey=selectedRow.key;
                if(button==1) {if(!collapsed.remove(selectedKey))collapsed.add(selectedKey);makeRows();}
                return;
            }
        }
        super.mouseClicked(x,y,button);
    }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();int wheel=Mouse.getEventDWheel();
        if(wheel!=0){scroll+=wheel>0?-3:3;makeRows();}
    }
    @Override protected void actionPerformed(GuiButton button) {
        switch(button.id) {
            case 0:session.quantity=Math.max(1,session.quantity-(isShiftKeyDown()?64:1));session.rebuild();break;
            case 1:session.quantity=Math.min(1000000000L,session.quantity+(isShiftKeyDown()?64:1));session.rebuild();break;
            case 2:summary=!summary;scroll=0;initGui();return;
            case 3:
                if(!session.plan.complete())return;
                StringBuilder text=new StringBuilder(session.name(session.root)+" × "+session.quantity+"\n消耗材料：\n");
                session.plan.materials.forEach((k,v)->text.append(session.name(k)).append(" × ").append(v).append('\n'));
                text.append("催化剂（顺序复用，不含并行复制）：\n");
                session.plan.catalysts.forEach((k,v)->text.append(session.name(k)).append(" × ").append(v).append('\n'));
                setClipboardString(text.toString());return;
            case 4:session.cycleRoute(selectedKey);break;
            case 5:if(!session.provided.remove(selectedKey))session.provided.add(selectedKey);session.rebuild();break;
            case 6:if(selectedRow!=null&&selectedRow.parent!=null)session.toggleCatalyst(selectedRow.parent,selectedRow.slot);break;
            case 7:if(selectedRow!=null&&selectedRow.parent!=null)session.cycleVariant(selectedRow.parent,selectedRow.slot);break;
            case 8:mc.displayGuiScreen(parent);return;
        }
        selectedRow=null;makeRows();
    }
    @Override protected void keyTyped(char typedChar,int keyCode) throws IOException {
        if(keyCode==1)mc.displayGuiScreen(parent);else super.keyTyped(typedChar,keyCode);
    }
    @Override public void onGuiClosed(){session.save();}
    @Override public boolean doesGuiPauseGame(){return false;}
}
