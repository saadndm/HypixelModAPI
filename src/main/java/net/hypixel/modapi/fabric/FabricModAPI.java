package net.hypixel.modapi.fabric;

import java.util.HashSet;
import java.util.Set;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.HypixelModAPIImplementation;
import net.hypixel.modapi.fabric.event.HypixelModAPICallback;
import net.hypixel.modapi.fabric.event.HypixelModAPIErrorCallback;
import net.hypixel.modapi.fabric.payload.ClientboundHypixelPayload;
import net.hypixel.modapi.fabric.payload.ServerboundHypixelPayload;
import net.hypixel.modapi.packet.HypixelPacket;
import net.hypixel.modapi.packet.impl.clientbound.ClientboundHelloPacket;
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket;
import net.minecraft.client.Minecraft;
import net.ornithemc.osl.core.api.util.NamespacedIdentifier;
import net.ornithemc.osl.core.api.util.NamespacedIdentifiers;
import net.ornithemc.osl.networking.api.ChannelRegistry;
import net.ornithemc.osl.networking.api.client.ClientConnectionEvents;
import net.ornithemc.osl.networking.api.client.ClientPlayNetworking;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("UnstableApiUsage")
public class FabricModAPI implements ClientModInitializer, HypixelModAPIImplementation {
	private static final Logger LOGGER = LoggerFactory.getLogger(FabricModAPI.class);
	private static final boolean DEBUG_MODE = FabricLoader.getInstance().isDevelopmentEnvironment() || Boolean.getBoolean("net.hypixel.modapi.debug");
	private static final Set<NamespacedIdentifier> REGISTERED_CLIENTBOUND = new HashSet<>();

	private boolean onHypixel;

	@Override
	public void onInitializeClient() {
		HypixelModAPI.getInstance().setModImplementation(this);

	}

	@Override
	public void onInit() {
		HypixelModAPI.getInstance().createHandler(ClientboundHelloPacket.class, packet -> onHypixel = true);
		ClientConnectionEvents.DISCONNECT.register(client -> onHypixel = false);

		if (DEBUG_MODE) {
			LOGGER.info("Debug mode is enabled!");
			registerDebug();
		}

		reloadRegistrations();
	}

	@Override
	public boolean sendPacket(HypixelPacket packet) {
		if (!isConnectedToHypixel()) {
			return false;
		}

		ServerboundHypixelPayload hypixelPayload = new ServerboundHypixelPayload(packet);

		if (Minecraft.getInstance().getNetworkHandler() != null) {
			NamespacedIdentifier id = NamespacedIdentifiers.parse(packet.getIdentifier());
			ClientPlayNetworking.sendNoCheck(id, hypixelPayload);
			return true;
		}

		LOGGER.warn("Failed to send a packet as the client is not connected to a server '{}'", packet);
		return false;
	}

	@Override
	public boolean isConnectedToHypixel() {
		return onHypixel;
	}

	/**
	 * Reloads the identifiers that are registered in the Hypixel Mod API and makes sure that the packets are registered.
	 * <p>
	 * This method is available for internal use by Hypixel to add new packets externally, and is not intended for use by other developers.
	 */
	@ApiStatus.Internal
	public static void reloadRegistrations() {
		for (String identifier : HypixelModAPI.getInstance().getRegistry().getClientboundIdentifiers()) {
			try {
				registerClientbound(identifier);
				LOGGER.info("Registered clientbound packet with identifier '{}'", identifier);
			} catch (Exception e) {
				LOGGER.error("Failed to register clientbound packet with identifier '{}'", identifier, e);
			}
		}

		for (String identifier : HypixelModAPI.getInstance().getRegistry().getServerboundIdentifiers()) {
			try {
				registerServerbound(identifier);
				LOGGER.info("Registered serverbound packet with identifier '{}'", identifier);
			} catch (Exception e) {
				LOGGER.error("Failed to register serverbound packet with identifier '{}'", identifier, e);
			}
		}
	}

	private static void registerClientbound(String identifier) {
		var clientboundId = NamespacedIdentifiers.parse(identifier);
		boolean serverbound = HypixelModAPI.getInstance().getRegistry().getServerboundIdentifiers().contains(identifier);

		ChannelRegistry.register(clientboundId, true, serverbound);

		synchronized (REGISTERED_CLIENTBOUND) {
			if (REGISTERED_CLIENTBOUND.contains(clientboundId)) {
				return;
			}

			// Also register the global receiver for handling incoming packets during PLAY and CONFIGURATION
			ClientPlayNetworking.registerListener(clientboundId, () -> new ClientboundHypixelPayload(identifier), (context, data) -> {
				LOGGER.debug("Received packet with identifier '{}', during PLAY", identifier);
				context.minecraft().executeTask(() -> handleIncomingPayload(identifier, data));
			});

			REGISTERED_CLIENTBOUND.add(clientboundId);
		}
	}

	private static void handleIncomingPayload(String identifier, ClientboundHypixelPayload payload) {
		if (!payload.isSuccess()) {
			LOGGER.warn("Received an error response for packet {}: {}", identifier, payload.getErrorReason());
			try {
				HypixelModAPI.getInstance().handleError(identifier, payload.getErrorReason());
			} catch (Exception e) {
				LOGGER.error("An error occurred while handling error response for packet {}", identifier, e);
			}

			try {
				HypixelModAPIErrorCallback.EVENT.invoker().onError(identifier, payload.getErrorReason());
			} catch (Exception e) {
				LOGGER.error("An error occurred while handling error response for packet {}", identifier, e);
			}
			return;
		}

		try {
			HypixelModAPI.getInstance().handle(payload.getPacket());
		} catch (Exception e) {
			LOGGER.error("An error occurred while handling packet {}", identifier, e);
		}

		try {
			HypixelModAPICallback.EVENT.invoker().onPacketReceived(payload.getPacket());
		} catch (Exception e) {
			LOGGER.error("An error occurred while handling packet {}", identifier, e);
		}
	}

	private static void registerServerbound(String identifier) {
		if (HypixelModAPI.getInstance().getRegistry().getClientboundIdentifiers().contains(identifier)) {
			return;
		}

		ChannelRegistry.register(NamespacedIdentifiers.parse(identifier), false, true);
	}

	private static void registerDebug() {
		// Register events
		HypixelModAPI.getInstance().subscribeToEventPacket(ClientboundLocationPacket.class);

		HypixelModAPI.getInstance().createHandler(ClientboundLocationPacket.class, packet -> LOGGER.info("Received location packet {}", packet))
				.onError(error -> LOGGER.error("Received error response for location packet: {}", error));

		HypixelModAPICallback.EVENT.register(packet -> LOGGER.info("Received packet {}", packet));
		HypixelModAPIErrorCallback.EVENT.register((identifier, error) -> LOGGER.error("Received error response for packet {}: {}", identifier, error));
	}
}
