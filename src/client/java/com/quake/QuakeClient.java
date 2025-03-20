package com.quake;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.api.ClientModInitializer;

public class QuakeClient implements ClientModInitializer {
	public static final Identifier QUAKE_CONVAR_PACKET_ID = new Identifier("quake", "convars");

	@Override
	public void onInitializeClient() {
		// this is so Frutiger Aero
		System.out.println("Init Quake Client! :-)");
		quakecCreateCommands();
	}

	private static void quakecCreateCommands() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(
				literal("quakec")
				.then(
					argument("toggle", BoolArgumentType.bool())
					.executes(QuakeClient::quakecToggle)
				)
			);
		});

		ClientPlayNetworking.registerGlobalReceiver(QUAKE_CONVAR_PACKET_ID, (client, handler, buf, responseSender) -> {
			try {
				QuakeConvars.quakeReadConvarBuffer(buf);
			} catch(Exception e) {
				// Incase :-)
				System.out.println(e.getMessage());
			}
		});
	}

	private static int quakecToggle(CommandContext<FabricClientCommandSource> context) {
		final boolean enabled = BoolArgumentType.getBool(context, "toggle");
		QuakeConvars.pl_enabled = enabled;
		context.getSource()
			.sendFeedback(Text.literal(enabled ? "Enabled quake movement" : "Disabled quake movement"));

		return 1;
	}
}
