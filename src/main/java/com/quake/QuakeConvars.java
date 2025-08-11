package com.quake;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

@SuppressWarnings("rawtypes")
public class QuakeConvars {
	private static final String CONVAR_SUCCESS_FORMAT = "Successfully set convar %s to %s";
	private static final String CONVAR_RESET_FORMAT = "Successfully reverted convar %s";
	private static final String CONVAR_FAIL_FORMAT = "Could not set convar '%s'";

	// Convars
	public static double pl_ground_acceleration = 4.5;
	public static double pl_ladder_accelerate = 4.5;
	public static double pl_fly_accelerate = 6.0;
	public static double pl_air_accelerate = 4.0;
	public static double pl_swim_accelerate = 2.0;
	public static double pl_ground_friction = 20.0;
	public static double pl_fly_friction = 5.0;
	public static double pl_swim_speed = 0.015;
	public static double pl_air_speed = 0.03;
	public static double pl_speed_cap = 0.0;
	public static boolean pl_enabled = true;
	public static boolean pl_autohop = true;

	// Keep track of original values
	private static HashMap<String, Double> originalDoubles = new HashMap<String, Double>();
	private static HashMap<String, Boolean> originalBools = new HashMap<String, Boolean>();
	public static ArrayList<String> accessibleFields = new ArrayList<String>();

	public static void init() throws Exception {
		final Field[] fields = QuakeConvars.class.getFields();
		for(Field f: fields) {
			final Class t = f.getType();
			final String name = f.getName();

			// Add to reflection list
			if(t == double.class) {
				accessibleFields.add(name);
				originalDoubles.put(name, f.getDouble(f));
			} else if(t == boolean.class) {
				accessibleFields.add(name);
				originalBools.put(name, f.getBoolean(f));
			}
		}

		// Sort quake convars so our packet order
		// is correct.
		accessibleFields.sort((a, b) -> a.compareTo(b));
	}

	public static void quakeReadConvarBuffer(PacketByteBuf buf) throws Exception {
		// From the server to synchronize client physics variables.
		for(final String fieldName: accessibleFields) {
			final Field f = QuakeConvars.class.getField(fieldName);
			final Class t = f.getType();

			if(t == double.class) {
				f.setDouble(f, buf.readDouble());
			} else if(t == boolean.class) {
				f.setBoolean(f, buf.readBoolean());
			} else {
				throw new Exception("Invalid convar list argument!");
			}
		}
	}

	public static void quakeWriteConvarBuffer(PacketByteBuf buf) throws Exception {
		// From the server to send new data to the clients.
		for(final String fieldName: accessibleFields) {
			final Field f = QuakeConvars.class.getField(fieldName);
			final Class t = f.getType();

			if(t == double.class) {
				buf.writeDouble(f.getDouble(f));
			} else if(t == boolean.class) {
				buf.writeBoolean(f.getBoolean(f));
			} else {
				throw new Exception("Invalid convar list argument!");
			}
		}
	}

	public static int quakeCmdSetConvar(CommandContext<ServerCommandSource> context) {
		// Called from the server side.
		final String field = StringArgumentType.getString(context, "field");
		final String value = StringArgumentType.getString(context, "value");
		try {
			final Field f = QuakeConvars.class.getField(field);
			final boolean shouldReset = value.equals("reset");
			if(f.getType() == double.class) {
				// Got a number
				final double num = shouldReset ? originalDoubles.get(field) : Double.parseDouble(value);
				f.setDouble(f, num);
			} else if(f.getType() == boolean.class) {
				// Got a boolean
				final boolean bool = shouldReset ? originalBools.get(field) : Boolean.parseBoolean(value);
				f.setBoolean(f, bool);
			} else {
				throw new Error("Invalid field");
			}

			if(shouldReset) {
				// Reset if provided
				context.getSource()
					.sendFeedback(() -> Text.literal(String.format(CONVAR_RESET_FORMAT, field)), false);
			} else {
				context.getSource()
					.sendFeedback(() -> Text.literal(String.format(CONVAR_SUCCESS_FORMAT, field, value)), false);
			}

			// Now, send an update to everyone on the server
			// about our convars changing.
			PacketByteBuf buf = PacketByteBufs.create();
			quakeWriteConvarBuffer(buf);

			for(final ServerPlayerEntity player: PlayerLookup.all(context.getSource().getServer())) {
				ServerPlayNetworking.send(
					player,
					QuakeServer.QUAKE_CONVAR_PACKET_ID,
					buf
				);
			}
		} catch(Exception e) {
			// Error lol
			context.getSource()
				.sendFeedback(() -> Text.literal(String.format(CONVAR_FAIL_FORMAT, field)), false);
		}

		return 1;
	}

}
