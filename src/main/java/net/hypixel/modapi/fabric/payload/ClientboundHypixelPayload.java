package net.hypixel.modapi.fabric.payload;

import java.io.IOException;

import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.error.ErrorReason;
import net.hypixel.modapi.packet.ClientboundHypixelPacket;
import net.hypixel.modapi.serializer.PacketSerializer;
import net.ornithemc.osl.networking.api.PacketBuffer;
import net.ornithemc.osl.networking.api.PacketPayload;

public class ClientboundHypixelPayload implements PacketPayload {
	private final String id;
	private ClientboundHypixelPacket packet;
	private ErrorReason errorReason;

	public ClientboundHypixelPayload(String identifier) {
		this.id = identifier;
	}

	public boolean isSuccess() {
		return packet != null;
	}

	public ClientboundHypixelPacket getPacket() {
		return packet;
	}

	public ErrorReason getErrorReason() {
		return errorReason;
	}

	@Override
	public void read(PacketBuffer buf) throws IOException {
		try {
			PacketSerializer serializer = new PacketSerializer(buf);
			boolean success = serializer.readBoolean();
			if (!success) {
				this.errorReason = ErrorReason.getById(serializer.readVarInt());
				return;
			}

			this.packet = HypixelModAPI.getInstance().getRegistry().createClientboundPacket(id, serializer);
		} finally {
			buf.release();
		}
	}

	@Override
	public void write(PacketBuffer buffer) throws IOException {
		throw new UnsupportedOperationException("Cannot write ClientboundHypixelPayload");
	}
}
