package com.quake;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.api.ClientModInitializer;

public class QuakeClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// this is so Frutiger Aero
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
	}

	private static int quakecToggle(CommandContext<FabricClientCommandSource> context) {
		final boolean enabled = BoolArgumentType.getBool(context, "toggle");
		QuakeConvars.pl_enabled = enabled ? 1.0 : 0.0;

		return 1;
	}
}
