package mezz.jei.nova;

import mezz.jei.config.KeyBindings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Keyboard;

/** Pack default requested by the user: recover an unbound craft action once per session. */
@Mod.EventBusSubscriber(modid="jei", value=Side.CLIENT)
public final class CraftingKeyDefaults {
    private static boolean checked;
    @SubscribeEvent
    public static void onGui(GuiScreenEvent.InitGuiEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (checked || mc.player == null) return;
        checked = true;
        if (KeyBindings.crafting.getKeyCode() == Keyboard.KEY_NONE) {
            KeyBindings.crafting.setKeyModifierAndCode(KeyModifier.SHIFT, Keyboard.KEY_C);
            KeyBinding.resetKeyBindingArrayAndHash();
            mc.gameSettings.saveOptions();
            CraftDiagnostics.log("restored unbound crafting default to Shift+C; code="+KeyBindings.crafting.getKeyCode());
        }
    }
}
