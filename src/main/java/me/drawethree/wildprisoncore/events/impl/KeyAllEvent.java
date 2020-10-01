package me.drawethree.wildprisoncore.events.impl;

import me.drawethree.wildprisoncore.events.WildPrisonEvent;
import me.lucko.helper.Schedulers;
import me.lucko.helper.random.RandomSelector;
import org.bukkit.Bukkit;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class KeyAllEvent implements WildPrisonEvent {


	private static final String[] KEYS = new String[]{"miner", "epic", "vote"};

	private static KeyAllEvent instance;

	private static final int MIN_MINUTES = 1;
	private static final int MAX_MINUTES = 5;

	private int lastKeysGiven;

	private KeyAllEvent() {
	}

	@Override
	public void start() {
		Schedulers.sync().runLater(() -> {
			int randomKeyAmount = ThreadLocalRandom.current().nextInt(MIN_MINUTES, MAX_MINUTES);

			while (randomKeyAmount == this.lastKeysGiven) {
				randomKeyAmount = ThreadLocalRandom.current().nextInt(MIN_MINUTES, MAX_MINUTES);
			}

			Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "wildcrates givecrateall " + getRandomKey() + " " + randomKeyAmount);

			this.lastKeysGiven = randomKeyAmount;
			this.start();
		}, 1, TimeUnit.MINUTES);
	}

	@Override
	public void stop() {

	}

	public static KeyAllEvent getInstance() {
		if (instance == null) {
			instance = new KeyAllEvent();
		}
		return instance;
	}


	private String getRandomKey() {
		return RandomSelector.uniform(Arrays.asList(KEYS)).pick();
	}
}