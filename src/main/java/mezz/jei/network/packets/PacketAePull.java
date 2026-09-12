package mezz.jei.network.packets;
import mezz.jei.network.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.*;
import net.minecraft.network.PacketBuffer;
import java.io.IOException;
public final class PacketAePull extends PacketJei {
    private final int window;private final NBTTagList entries;
    public PacketAePull(int window,NBTTagList entries) {this.window=window;this.entries=entries;}
    public IPacketId getPacketId() {return PacketIdServer.AE2_PULL;}
    public void writePacketData(PacketBuffer b) {b.writeVarInt(window);NBTTagCompound tag=new NBTTagCompound();tag.setTag("entries",entries);b.writeCompoundTag(tag);}
    public static void readPacketData(PacketBuffer b,EntityPlayer p) throws IOException {int window=b.readVarInt();NBTTagCompound tag=b.readCompoundTag();if(tag!=null)mezz.jei.nova.suite.AePull.execute(p,window,tag.getTagList("entries",10));}
}
