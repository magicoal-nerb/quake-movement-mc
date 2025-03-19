package com.quake;

import static net.minecraft.server.command.CommandManager.*;
import java.lang.reflect.Field;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.Event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mojang.brigadier.arguments.StringArgumentType;

public class QuakeServer implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("quake");
	public static Event<CommandRegistrationCallback> dispatcher = CommandRegistrationCallback.EVENT;

	@Override
	public void onInitialize() {
		LOGGER.info("Init Quake server! :-)");
		quakeCreateCommands();
	}

	private static void quakeCreateCommands() {
		// Initializes convar commands
		try { QuakeConvars.init(); } catch(Exception e) {}
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
				literal("quake")
				.then(
					argument("field", StringArgumentType.string())
					.suggests((context, builder) -> {
						Field[] fields = QuakeConvars.class.getFields();
						for(Field f: fields) {
							if(f.getType() == double.class || f.getType() == boolean.class) {
								builder.suggest(f.getName());
							}
						}

						return builder.buildFuture();
					})
					.then(argument("value", StringArgumentType.string()).executes(QuakeConvars::quakeCmdSetConvar))
				)
			);
		});
	}
}
