package org.tillerino.ppaddict.chat.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.MDC;
import org.tillerino.ppaddict.chat.GameChatMetrics;
import org.tillerino.ppaddict.chat.GameChatResponse;
import org.tillerino.ppaddict.chat.GameChatResponse.Action;
import org.tillerino.ppaddict.chat.GameChatResponse.Message;
import org.tillerino.ppaddict.chat.GameChatResponse.Success;
import org.tillerino.ppaddict.chat.GameChatWriter;
import org.tillerino.ppaddict.chat.Joined;
import org.tillerino.ppaddict.chat.LiveActivity;
import org.tillerino.ppaddict.chat.PrivateMessage;
import org.tillerino.ppaddict.chat.Sighted;
import org.tillerino.ppaddict.util.Clock;
import org.tillerino.ppaddict.util.MdcUtils;
import org.tillerino.ppaddict.util.MdcUtils.MdcAttributes;
import org.tillerino.ppaddict.util.RetryableException;

@RunWith(MockitoJUnitRunner.class)
public class ResponsePostprocessorTest {
	@Mock
	private GameChatMetrics botInfo;

	@Mock
	private Bouncer bouncer;

	@Mock
	private LiveActivity liveActivity;

	@Mock
	private GameChatWriter writer;

	@Mock
	private Clock clock;

	@InjectMocks
	private ResponsePostprocessor responsePostprocessor;

	@Test
	public void testAction() throws Exception {
		PrivateMessage event = new PrivateMessage(1, "nick", 2, "yo");
		responsePostprocessor.onResponse(new Action("xyz"), event);
		verify(writer).action("xyz", event);
		verify(liveActivity).propagateSentMessage("nick", 1);
		verify(bouncer).exit("nick", 1);
	}

	@Test
	public void testMessage() throws Exception {
		PrivateMessage event = new PrivateMessage(1, "nick", 2, "yo");
		responsePostprocessor.onResponse(new Message("xyz"), event);
		verify(writer).message("xyz", event);
		verify(liveActivity).propagateSentMessage("nick", 1);
		verify(bouncer).exit("nick", 1);
		
	}

	@Test
	public void testSuccess() throws Exception {
		PrivateMessage event = new PrivateMessage(1, "nick", 2, "yo");
		responsePostprocessor.onResponse(new Success("xyz"), event);
		verify(writer).message("xyz", event);
		verify(liveActivity).propagateSentMessage("nick", 1);
		verify(bouncer).exit("nick", 1);
	}

	@Test
	public void testList() throws Exception {
		PrivateMessage event = new PrivateMessage(1, "nick", 2, "yo");
		responsePostprocessor.onResponse(new Message("xyz").then(new Action("abc")), event);
		verify(writer).message("xyz", event);
		verify(writer).action("abc", event);
		verify(liveActivity, times(2)).propagateSentMessage("nick", 1);
		verify(bouncer, times(1)).exit("nick", 1);
	}

	@Test
	public void testNoResponse() throws Exception {
		PrivateMessage event = new PrivateMessage(1, "nick", 2, "yo");
		responsePostprocessor.onResponse(GameChatResponse.none(), event);
		verifyNoInteractions(writer);
		verifyNoInteractions(liveActivity);
		verify(bouncer, times(1)).exit("nick", 1);
	}

	@Test
	public void testRecommendation() throws Exception {
		when(clock.currentTimeMillis()).thenReturn(159L);
		try (MdcAttributes mdc = MdcUtils.with("handler", "r")) {
			PrivateMessage event = new PrivateMessage(1, "nick", 2, "yo");
			responsePostprocessor.onResponse(new Success("xyz"), event);
			verify(botInfo).setLastRecommendation(159);
		}
	}

	@Test
	public void testNoBouncerForNonInteractiveEvents() throws Exception {
		Sighted event = new Sighted(1, "nick", 2);
		responsePostprocessor.onResponse(new Success("hai"), event);
		verify(writer).message("hai", event);
		verifyNoInteractions(bouncer);
	}

	@Test
	public void testMdcThrowup() throws Exception {
		// this is a bit ugly, but since we know that this method is called,
		// we can hook into it and validate the MDC :/
		doAnswer(x -> {
			assertThat(MDC.get("state")).isEqualTo("sent");
			assertThat(MDC.get("success")).isEqualTo("true");
			assertThat(MDC.get("duration")).isEqualTo("121");
			assertThat(MDC.get("osuApiRateBlockedTime")).isEqualTo("32");
			return null;
		}).when(botInfo).setLastSentMessage(123L);
		PrivateMessage event = new PrivateMessage(1, "nick", 2, "yo");
		event.getMeta().setRateLimiterBlockedTime(32);
		when(clock.currentTimeMillis()).thenReturn(123L);
		responsePostprocessor.onResponse(new Success("yeah"), event);
		verify(botInfo).setLastSentMessage(123L);
	}

	@Test
	public void testNoMdcThrowupForPlainMessage() throws Exception {
		// this is a bit ugly, but since we know that this method is called,
		// we can hook into it and validate the MDC :/
		doAnswer(x -> {
			assertThat(MDC.get("state")).isEqualTo("sent");
			assertThat(MDC.get("success")).isNull();
			assertThat(MDC.get("duration")).isNull();
			assertThat(MDC.get("osuApiRateBlockedTime")).isNull();
			return null;
		}).when(botInfo).setLastSentMessage(123L);
		PrivateMessage event = new PrivateMessage(1, "nick", 2, "yo");
		event.getMeta().setRateLimiterBlockedTime(32);
		when(clock.currentTimeMillis()).thenReturn(123L);
		responsePostprocessor.onResponse(new Message("yeah"), event);
		verify(botInfo).setLastSentMessage(123L);
	}

	@Test
	public void writingIsRetried() throws Exception {
		Joined event = new Joined(1234, "nick", 0);
		int[] count = { 0 };
		doAnswer(x -> {
			if (count[0]++ > 0) {
				return null;
			}
			throw new RetryableException(0);
		}).when(writer).message("abc", event);
		responsePostprocessor.onResponse(new Message("abc"), event);
		verify(writer, times(2)).message("abc", event);
	}

	@Test
	public void retryingStops() throws Exception {
		Joined event = new Joined(1234, "nick", 0);
		doThrow(new RetryableException(0)).when(writer).message("abc", event);
		responsePostprocessor.onResponse(new Message("abc"), event);
		verify(writer, times(10)).message("abc", event);
	}
}
