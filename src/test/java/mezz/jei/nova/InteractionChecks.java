package mezz.jei.nova;
import mezz.jei.nova.suite.AeSearchAccess;
import net.minecraft.client.gui.GuiTextField;
import java.awt.Rectangle;
public final class InteractionChecks {
    public static void main(String[] args) throws Exception {
        GuiTextField text=new GuiTextField(0,null,20,30,90,12);
        if(!AeSearchAccess.bounds(text).equals(new Rectangle(18,28,94,16)))throw new AssertionError("Initial search bounds");
        text.x=140;text.y=210;
        text.width=120;
        text.height=18;
        if(!AeSearchAccess.bounds(text).equals(new Rectangle(138,208,124,22)))throw new AssertionError("Search bounds must follow a repositioned/resized widget");
        System.out.println("PASS interaction: live search target follows actual GuiTextField coordinates and private dimensions");
    }
}
