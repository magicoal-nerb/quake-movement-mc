package com.quake;

import static net.minecraft.server.command.CommandManager.*;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mojang.brigadier.arguments.StringArgumentType;

public class QuakeServer implements ModInitializer {
	public static final Identifier QUAKE_CONVAR_PACKET_ID = new Identifier("quake", "convars");
    public static final Logger QUAKE_LOGGER = LoggerFactory.getLogger("quake");
	public static Event<CommandRegistrationCallback> dispatcher = CommandRegistrationCallback.EVENT;

	@Override
	public void onInitialize() {
		QUAKE_LOGGER.info("Init Quake server! :-)");
		quakeCreateCommands();
	}

	private static void quakeCreateCommands() {
		try {
			// Initialize convar system
			QuakeConvars.init();
		} catch(Exception e) {
			QUAKE_LOGGER.info(e.getMessage());
		}

		// Initializes convar commands
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
				literal("quake")
				.then(
					argument("field", StringArgumentType.string())
					.suggests((context, builder) -> {
						// Suggest all accessible fields for the server.
						QuakeConvars.accessibleFields.forEach(builder::suggest);
						return builder.buildFuture();
					})
					.then(argument("value", StringArgumentType.string()).executes(QuakeConvars::quakeCmdSetConvar))
				)
			);
		});
	}
}
