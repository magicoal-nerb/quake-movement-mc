package com.quake;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "quake", dist = Dist.CLIENT)
public class QuakeClient {
	public QuakeClient(IEventBus modBus, ModContainer container) {
		// this is so Frutiger Aero
		System.out.println("Init Quake Client! :-)");
		NeoForge.EVENT_BUS.addListener(QuakeClient::quakecCreateCommands);
	}

	private static void quakecCreateCommands(final RegisterClientCommandsEvent event) {
		event.getDispatcher().register(
			Commands.literal("quakec")
			.then(
				Commands.argument("toggle", BoolArgumentType.bool())
				.executes(QuakeClient::quakecToggle)
			)
		);
	}

	private static int quakecToggle(CommandContext<CommandSourceStack> context) {
		final boolean enabled = BoolArgumentType.getBool(context, "toggle");
		QuakeConvars.pl_enabled = enabled;
		context.getSource()
			.sendSuccess(() -> Component.literal(enabled ? "Enabled quake movement" : "Disabled quake movement"), false);

		return 1;
	}
}