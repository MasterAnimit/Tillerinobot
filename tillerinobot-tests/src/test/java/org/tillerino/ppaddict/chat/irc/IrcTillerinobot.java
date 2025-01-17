package org.tillerino.ppaddict.chat.irc;

import javax.inject.Singleton;

import org.tillerino.ppaddict.chat.GameChatClient;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.name.Names;

import tillerino.tillerinobot.AbstractDatabaseTest.CreateInMemoryDatabaseModule;
import tillerino.tillerinobot.BotBackend;
import tillerino.tillerinobot.TestBackend;
import tillerino.tillerinobot.TillerinobotConfigurationModule;

/**
 * Will connect to an actual IRC server with completely fake data. Use environment variables
 * to configure: TILLERINOBOT_IRC_SERVER=localhost
 * TILLERINOBOT_IRC_PORT=6667 TILLERINOBOT_IRC_NICKNAME=Tillerinobot
 * TILLERINOBOT_IRC_PASSWORD=secret TILLERINOBOT_IRC_AUTOJOIN=#osu
 */
public class IrcTillerinobot extends AbstractModule {

	@Override
	protected void configure() {
		bind(GameChatClient.class).to(BotRunnerImpl.class);
		install(new CreateInMemoryDatabaseModule());
		install(new TillerinobotConfigurationModule());

		bind(Boolean.class).annotatedWith(Names.named("tillerinobot.ignore")).toInstance(false);
		bind(BotBackend.class).to(TestBackend.class).in(Singleton.class);
		bind(Boolean.class).annotatedWith(Names.named("tillerinobot.test.persistentBackend")).toInstance(true);
	}

	public static void main(String[] args) {
		Injector injector = Guice.createInjector(new IrcTillerinobot());

		injector.getInstance(GameChatClient.class).run();
	}
}
