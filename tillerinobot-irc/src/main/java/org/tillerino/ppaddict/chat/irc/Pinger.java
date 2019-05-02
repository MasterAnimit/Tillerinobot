package org.tillerino.ppaddict.chat.irc;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.pircbotx.Utils;
import org.pircbotx.hooks.events.UnknownEvent;
import org.slf4j.MDC;
import org.tillerino.ppaddict.chat.irc.BotRunnerImpl.CloseableBot;

import lombok.extern.slf4j.Slf4j;
import tillerino.tillerinobot.IRCBot;
import tillerino.tillerinobot.rest.BotInfoService.BotInfo;

@Slf4j
@Singleton
public class Pinger {
	volatile String pingMessage = null;
	volatile CountDownLatch pingLatch = null;
	final AtomicBoolean quit = new AtomicBoolean(false);

	final BotInfo botInfo;

	@Inject
	public Pinger(BotInfo infoService) {
		this.botInfo = infoService;
	}

	long lastquit = 0l;

	final AtomicInteger pingCalls = new AtomicInteger();

	void ping(CloseableBot bot) throws IOException, InterruptedException {
		if (pingCalls.incrementAndGet() % 10 != 0) {
			return;
		}
		if(quit.get()) {
			throw new IOException("ping gate closed");
		}

		if(!bot.isConnected()) {
			throw new IOException("bot is no longer connected");			
		}

		long time = System.currentTimeMillis();

		synchronized (this) {
			pingLatch = new CountDownLatch(1);
			pingMessage = IRCBot.getRandomString(16);
		}

		log.debug("PING {}", pingMessage);
		Utils.sendRawLineToServer(bot, "PING " + pingMessage);

		if(!pingLatch.await(10, TimeUnit.SECONDS)) {
			MDC.put("ping", 10000 + "");
			throw new IOException("ping timed out");
		}

		long ping = System.currentTimeMillis() - time;
		MDC.put("ping", ping + "");
	}

	public void handleUnknownEvent(@SuppressWarnings("rawtypes") UnknownEvent event) {
		synchronized(this) {
			if (pingMessage == null)
				return;

			boolean contains = event.getLine().contains(" PONG ");
			boolean endsWith = event.getLine().endsWith(pingMessage);
			if (contains && endsWith) {
				log.debug("PONG {}", pingMessage);
				pingLatch.countDown();
			} else if(contains) {
				log.warn("unknown pong: {}", event.getLine());
			}
		}
	}
}
