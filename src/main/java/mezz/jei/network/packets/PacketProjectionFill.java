package mezz.jei.network.packets;
import mezz.jei.network.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import java.io.IOException;
public final class PacketProjectionFill extends PacketJei {
 private final int window,batches;private final NBTTagCompound plan;
 public PacketProjectionFill(int window,int batches,NBTTagCompound plan){this.window=window;this.batches=batches;this.plan=plan;}
 public IPacketId getPacketId(){return PacketIdServer.PROJECTION_FILL;}
 public void writePacketData(PacketBuffer b){b.writeVarInt(window);b.writeVarInt(batches);b.writeCompoundTag(plan);}
 public static void readPacketData(PacketBuffer b,EntityPlayer p)throws IOException{int window=b.readVarInt(),batches=b.readVarInt();mezz.jei.nova.suite.ProjectionFill.execute(p,window,batches,b.readCompoundTag());}
}
