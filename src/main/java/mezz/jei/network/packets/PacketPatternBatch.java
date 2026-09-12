package mezz.jei.network.packets;
import mezz.jei.network.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import java.io.IOException;
public final class PacketPatternBatch extends PacketJei {
    private final int window;
    private final NBTTagCompound request;
    public PacketPatternBatch(int window,NBTTagCompound request) {this.window=window;this.request=request;}
    public IPacketId getPacketId() {return PacketIdServer.AE2_PATTERN_BATCH;}
    public void writePacketData(PacketBuffer buffer) {buffer.writeVarInt(window);buffer.writeCompoundTag(request);}
    public static void readPacketData(PacketBuffer buffer,EntityPlayer player) throws IOException {
        mezz.jei.nova.pattern.PatternBatchServer.handle(player,buffer.readVarInt(),buffer.readCompoundTag());
    }
}
