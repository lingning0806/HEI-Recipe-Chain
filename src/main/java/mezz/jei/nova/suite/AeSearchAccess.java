package mezz.jei.nova.suite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.ItemStack;
import java.awt.Rectangle;

/** Uses the live text-field bounds, including repositioning by terminal GUI extensions. */
public final class AeSearchAccess {
    private static final java.util.Set<Class<?>> warned=new java.util.HashSet<>();
    private static java.lang.reflect.Field widthField,heightField;
    private final GuiScreen screen;
    private final GuiTextField field;
    private AeSearchAccess(GuiScreen screen,GuiTextField field){this.screen=screen;this.field=field;}
    public static AeSearchAccess find(GuiScreen screen) {
        if(screen==null)return null;
        boolean supported=false;for(Class<?> c=screen.getClass();c!=null;c=c.getSuperclass())if(c.getName().equals("appeng.client.gui.implementations.GuiMEMonitorable"))supported=true;
        if(!supported)return null;
        try {
            Object field=AePull.field(screen,"searchField");
            return field instanceof GuiTextField?new AeSearchAccess(screen,(GuiTextField)field):null;
        }catch(ReflectiveOperationException | RuntimeException | LinkageError ex){if(warned.add(screen.getClass()))mezz.jei.util.Log.get().warn("AE search field unavailable: "+screen.getClass().getName(),ex);return null;}
    }
    public Rectangle area(){return bounds(field);}
    public static Rectangle bounds(GuiTextField field){
        try {
            if(widthField==null)widthField=textFieldField("width","field_146218_h");
            if(heightField==null)heightField=textFieldField("height","field_146219_i");
            return new Rectangle(field.x-2,field.y-2,widthField.getInt(field)+4,heightField.getInt(field)+4);
        }catch(ReflectiveOperationException | RuntimeException ex){return new Rectangle();}
    }
    private static java.lang.reflect.Field textFieldField(String...names) throws NoSuchFieldException {
        for(String name:names)try {java.lang.reflect.Field field=GuiTextField.class.getDeclaredField(name);field.setAccessible(true);return field;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(java.util.Arrays.toString(names));
    }
    public boolean apply(ItemStack item) {
        if(Minecraft.getMinecraft().currentScreen!=screen || item.isEmpty())return false;
        try {
            String text=net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(item.getDisplayName());
            if(text==null || text.trim().isEmpty())return false;
            field.setText(text.trim());field.setCursorPositionZero();field.setSelectionPos(0);field.setFocused(true);
            Object repo=AePull.field(screen,"repo");
            repo.getClass().getMethod("setSearchString",String.class).invoke(repo,field.getText());
            mezz.jei.nova.ae2.Ae2Terminal.call(repo,"updateView");AePull.method(screen,"setScrollBar").invoke(screen);
            mezz.jei.nova.CraftDiagnostics.log("AE search drop applied: "+field.getText());
            mezz.jei.nova.CraftDiagnostics.notice("AE 搜索："+field.getText());return true;
        }catch(ReflectiveOperationException | RuntimeException | LinkageError ex) {
            mezz.jei.util.Log.get().warn("AE search drop failed",ex);mezz.jei.nova.CraftDiagnostics.notice("AE 搜索更新失败，详情已记录日志");return false;
        }
    }
}
