package com.quake;

import static net.minecraft.server.command.CommandManager.*;
import java.lang.reflect.Field;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.Event;

import net.minecraft.server.command.ServerCommandSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

public class QuakeServer implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("quake");
	public static Event<CommandRegistrationCallback> dispatcher = CommandRegistrationCallback.EVENT;

	@Override
	public void onInitialize() {
		LOGGER.info("Init quake thread! :-)");
		quakeCreateCommands();
	}

	private static void quakeCreateCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
				literal("quake")
				.then(
					argument("field", StringArgumentType.string())
					.suggests((context, builder) -> {
						Field[] fields = QuakeConvars.class.getFields();
						for(Field f: fields)
							builder.suggest(f.getName());
						return builder.buildFuture();
					})
					.then(
						argument("value", DoubleArgumentType.doubleArg())
						.executes(QuakeServer::quakeSetConvar)
					)
				)
			);
		});
	}

	private static int quakeSetConvar(CommandContext<ServerCommandSource> context) {
		final String field = StringArgumentType.getString(context, "field");
		final double value = DoubleArgumentType.getDouble(context, "value");
		QuakeConvars.quakeSetConvar(field, value);

		return 1;
	}
}
