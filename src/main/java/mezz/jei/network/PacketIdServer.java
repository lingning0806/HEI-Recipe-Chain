package mezz.jei.network;

public enum PacketIdServer implements IPacketId {
	RECIPE_TRANSFER,
	DELETE_ITEM,
	GIVE_ITEM,
	SET_HOTBAR_ITEM,
	CHEAT_PERMISSION_REQUEST,
	AE2_CRAFT,
	AE2_PATTERN_BATCH, AE2_PULL, PROJECTION_FILL;

	public static final PacketIdServer[] VALUES = values();
}
