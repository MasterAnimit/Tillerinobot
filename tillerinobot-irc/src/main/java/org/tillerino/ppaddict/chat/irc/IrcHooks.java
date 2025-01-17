package org.tillerino.ppaddict.chat.irc;

import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

import javax.inject.Inject;
import javax.inject.Named;

import org.pircbotx.User;
import org.pircbotx.hooks.CoreHooks;
import org.pircbotx.hooks.Event;
import org.pircbotx.hooks.events.ActionEvent;
import org.pircbotx.hooks.events.ConnectEvent;
import org.pircbotx.hooks.events.DisconnectEvent;
import org.pircbotx.hooks.events.JoinEvent;
import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.events.PrivateMessageEvent;
import org.pircbotx.hooks.events.ServerResponseEvent;
import org.pircbotx.hooks.events.UnknownEvent;
import org.pircbotx.hooks.types.GenericUserEvent;
import org.slf4j.MDC;
import org.tillerino.osuApiModel.types.MillisSinceEpoch;
import org.tillerino.ppaddict.chat.GameChatEventConsumer;
import org.tillerino.ppaddict.chat.GameChatMetrics;
import org.tillerino.ppaddict.chat.IRCName;
import org.tillerino.ppaddict.chat.Joined;
import org.tillerino.ppaddict.chat.PrivateAction;
import org.tillerino.ppaddict.chat.PrivateMessage;
import org.tillerino.ppaddict.chat.Sighted;
import org.tillerino.ppaddict.chat.irc.BotRunnerImpl.CloseableBot;
import org.tillerino.ppaddict.util.Clock;
import org.tillerino.ppaddict.util.MdcUtils;
import org.tillerino.ppaddict.util.MdcUtils.MdcAttributes;

import com.google.common.collect.ImmutableList;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.extern.slf4j.Slf4j;

/**
 * Feeds the {@link MessagePreprocessor} from an IRC client.
 */
@SuppressWarnings("rawtypes")
@Slf4j
public class IrcHooks extends CoreHooks {
	private final GameChatEventConsumer downStream;
	private final GameChatMetrics botInfo;
	private final IrcWriter queue;
	private final boolean silent;
	private final Pinger pinger;

	private final AtomicLong lastSerial;
	private final AtomicLong lastListTime;

	private final Queue<ServerResponseEvent> userListEvents = new LinkedList<>();

	@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Injection")
	@Inject
	public IrcHooks(@Named("messagePreprocessor") GameChatEventConsumer downStream,
			GameChatMetrics botInfo,
			Pinger pinger,
			@Named("tillerinobot.ignore") boolean silent,
			IrcWriter queue,
			Clock clock) {
		this.downStream = downStream;
		this.silent = silent;
		this.botInfo = botInfo;
		this.queue = queue;
		this.pinger = pinger;
		// We want to assign serial numbers to incoming events without repeating
		// serial numbers and without remembering serial numbers across restarts.
		// One possibility is to take the current time, multiply it by X and then
		// increment it for each event. If we take the current time in milliseconds
		// we wil not repeat a serial number as long as we stay below 1000 * X events
		// per second on average.
		// Now here's the tricky part:
		// We use this serial in the JavaScript monitoring frontend.
		// Turns out JavaScript represents _all numbers_ as 64 bit FP numbers.
		// This means that we only have 51 significant bits which is ~15 digits.
		// Current time in millis, e.g. 1571728442000 has 13 digits.
		// So we can choose X = 100 and increase the digits to 15 and still be safe in JS.
		// In this case safe = each serial number will be represented as a _different_
		// number in JS.
		// So here we need to stay below 100_000 events per second on average. This
		// is a pretty safe ceiling.
		// Although we lose a bit of head room we do all of this in base 10 for better
		// debuggability.
		lastSerial = new AtomicLong(clock.currentTimeMillis() * 100);
		lastListTime = new AtomicLong(clock.currentTimeMillis());
	}

	@Override
	public void onConnect(ConnectEvent event) throws Exception {
		botInfo.setRunningSince(timestamp(event));
		log.info("connected");
		queue.setBot((CloseableBot) event.getBot());
	}

	@SuppressFBWarnings("TQ")
	private @MillisSinceEpoch long timestamp(Event event) {
		return event.getTimestamp();
	}

	@Override
	public void onAction(ActionEvent event) throws Exception {
		botInfo.setLastReceivedMessage(timestamp(event));
		if(silent)
			return;

		String nick = getNick(event);
		if (event.getChannel() == null) {
			downStream.onEvent(new PrivateAction(
					MdcUtils.getLong(MdcUtils.MDC_EVENT).orElseThrow(IllegalStateException::new),
					nick, timestamp(event), event.getMessage()));
		}
	}

	@Override
	public void onPrivateMessage(PrivateMessageEvent event) throws Exception {
		botInfo.setLastReceivedMessage(timestamp(event));
		if(silent)
			return;

		downStream.onEvent(new PrivateMessage(
				MdcUtils.getLong(MdcUtils.MDC_EVENT).orElseThrow(IllegalStateException::new),
				getNick(event), timestamp(event), event.getMessage()));
	}

	@Override
	public void onMessage(MessageEvent event) throws Exception {
		botInfo.setLastReceivedMessage(timestamp(event));
	}

	@Override
	public void onDisconnect(DisconnectEvent event) throws Exception {
		log.info("received DisconnectEvent");
	}

	@Override
	public void onEvent(Event event) throws Exception {
		botInfo.setLastInteraction(timestamp(event));
		MDC.clear();
		try (MdcAttributes mdc = MdcUtils.with(MdcUtils.MDC_EVENT, lastSerial.getAndIncrement())) {
			if (lastListTime.get() <= timestamp(event) - 60 * 60 * 1000) {
				lastListTime.set(timestamp(event));

				event.getBot().sendRaw().rawLine("NAMES #osu");
			}

			User user = null;
			if (event instanceof GenericUserEvent<?> generic) {
				user = generic.getUser();
				if (user != null) {
					// looks like the mode event can have user == null ¯\_(ツ)_/¯
					mdc.add(MdcUtils.MDC_USER, user.getNick());
				}
			}

			super.onEvent(event);
		}

		if (event instanceof PrivateMessageEvent || event instanceof ActionEvent) {
			// we want to process these on events which are regular but not too frequent to clog up things.
			ServerResponseEvent listEvent = userListEvents.poll();
			if (listEvent != null) {
				processUserListEvent(listEvent);
			}
		}
	}

	@Override
	public void onUnknown(UnknownEvent event) throws Exception {
		pinger.handleUnknownEvent(event);
	}

	@Override
	public void onJoin(JoinEvent event) throws Exception {
		if (silent) {
			return;
		}

		if (Objects.equals(event.getBot().getConfiguration().getName(), event.getUser().getNick())) {
			// The bot itself joined the channel. This isn't interesting to us.
			return;
		}

		downStream.onEvent(new Joined(MdcUtils.getLong(MdcUtils.MDC_EVENT).orElseThrow(IllegalStateException::new),
				getNick(event), timestamp(event)));
	}

	@Override
	public void onServerResponse(ServerResponseEvent event) throws Exception {
		if(event.getCode() == 353) {
			// these come in bursts and we don't want them to clog up our processing pipeline.
			// especially when going online, this is awkward since the bot doesn't answer for a while.
			// since the original event is the most compact form for this, we store it for later processing.
			userListEvents.add(event);
		} else {
			super.onServerResponse(event);
		}
	}

	@SuppressFBWarnings("TQ")
	private void processUserListEvent(ServerResponseEvent event) throws InterruptedException {
		ImmutableList<String> parsedResponse = event.getParsedResponse();

		String[] usernames = parsedResponse.get(parsedResponse.size() - 1).split(" ");

		for (int i = 0; i < usernames.length; i++) {
			try (MdcAttributes mdc = MdcUtils.with(MdcUtils.MDC_EVENT, lastSerial.getAndIncrement())) {
				String nick = usernames[i];

				if (nick.startsWith("@") || nick.startsWith("+"))
					nick = nick.substring(1);

				downStream.onEvent(new Sighted(MdcUtils.getLong(MdcUtils.MDC_EVENT).orElseThrow(IllegalStateException::new),
						nick, timestamp(event)));
			}
		}

		System.out.println("processed user list event " + userListEvents.size() + " remaining");
	}

	@SuppressFBWarnings("TQ")
	@IRCName private static String getNick(GenericUserEvent event) {
		return event.getUser().getNick();
	}
}
