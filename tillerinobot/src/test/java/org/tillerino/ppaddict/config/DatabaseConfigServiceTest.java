package org.tillerino.ppaddict.config;

import static org.assertj.core.api.Assertions.assertThat;

import javax.inject.Inject;

import org.junit.Test;
import org.tillerino.ppaddict.util.TestModule;

import tillerino.tillerinobot.AbstractDatabaseTest;
import tillerino.tillerinobot.data.BotConfig;

@TestModule({ CachedDatabaseConfigServiceModule.class })
public class DatabaseConfigServiceTest extends AbstractDatabaseTest {
	@Inject
	ConfigService config;

	@Test
	public void noConfigIsDefault() throws Exception {
		assertThat(config.scoresMaintenance()).isFalse();
	}

	@Test
	public void falsee() throws Exception {
		BotConfig botConfig = new BotConfig();
		botConfig.setPath("api-scores-maintenance");
		botConfig.setValue("false");
		botConfigRepository.save(botConfig);
		assertThat(config.scoresMaintenance()).isFalse();
	}

	@Test
	public void truee() throws Exception {
		BotConfig botConfig = new BotConfig();
		botConfig.setPath("api-scores-maintenance");
		botConfig.setValue("true");
		botConfigRepository.save(botConfig);
		assertThat(config.scoresMaintenance()).isTrue();
	}
}
