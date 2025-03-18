package com.quake;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuakeServer implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("quake");

	@Override
	public void onInitialize() {
		// There's nothing here yet
		LOGGER.info("Init quake thread! :-)");
	}
}
