package com.quake;

import java.lang.reflect.Field;

public class QuakeConvars {
	public static double pl_ground_acceleration = 4.5;
	public static double pl_ladder_accelerate = 4.5;
	public static double pl_fly_accelerate = 6.0;
	public static double pl_air_accelerate = 4.0;
	public static double pl_swim_accelerate = 2.0;
	public static double pl_ground_friction = 20.0;
	public static double pl_fly_friction = 5.0;
	public static double pl_swim_speed = 0.015;
	public static double pl_air_speed = 0.03;
	public static double pl_enabled = 1.0;
	public static double pl_autohop = 1.0;
	public static double pl_speed_cap = 0.0;

	public static void quakeSetConvar(final String name, final double value) {
		try {
			Field field = QuakeConvars.class.getDeclaredField(name);

			field.setDouble(field, value);
		} catch(Exception exception) { }
	}

	public static void quakeSetConvar() {
		// TODO: implement a method for this using a byte array from the server.
		// so then physics variables replicate n ye
	}
}
