package mezz.jei.nova;

import mezz.jei.config.KeyBindings;
import mezz.jei.util.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.client.event.GuiScreenEvent;
import org.lwjgl.input.Keyboard;

/** Temporary diagnostics: no inventory writes, key remapping, or packet changes. */
@Mod.EventBusSubscriber(modid="jei", value=Side.CLIENT)
public final class CraftDiagnostics {
    public static void log(String message) { Log.get().info("[HEI-DIAG] " + message); }
    public static void notice(String message) {
        log(message);
        if (Minecraft.getMinecraft().player != null)
            Minecraft.getMinecraft().player.sendStatusMessage(new TextComponentString("[HEI] " + message), false);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST, receiveCanceled=true)
    public static void key(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (!Keyboard.getEventKeyState() || Keyboard.isRepeatEvent()) return;
        if (Keyboard.getEventKey()!=Keyboard.KEY_C) return;
        if (!Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) && !Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) return;
        log("raw Shift+C; cancelled="+event.isCanceled()+" binding="+KeyBindings.crafting.getKeyCode()
            +" matches="+KeyBindings.crafting.isActiveAndMatches(Keyboard.getEventKey())
            +" leftShift="+Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
            +" gui="+event.getGui().getClass().getName());
    }
}
