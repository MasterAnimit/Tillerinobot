package tillerino.tillerinobot.handlers;

import java.io.IOException;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.CheckForNull;
import javax.inject.Inject;

import org.slf4j.MDC;
import org.tillerino.osuApiModel.Mods;
import org.tillerino.osuApiModel.OsuApiUser;
import org.tillerino.ppaddict.chat.GameChatResponse;
import org.tillerino.ppaddict.chat.GameChatResponse.Success;
import org.tillerino.ppaddict.chat.LiveActivity;
import org.tillerino.ppaddict.util.MdcUtils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;
import tillerino.tillerinobot.BeatmapMeta;
import tillerino.tillerinobot.BotBackend;
import tillerino.tillerinobot.CommandHandler;
import tillerino.tillerinobot.UserDataManager.UserData;
import tillerino.tillerinobot.UserDataManager.UserData.BeatmapWithMods;
import tillerino.tillerinobot.UserException;
import tillerino.tillerinobot.diff.PercentageEstimates;
import tillerino.tillerinobot.lang.Language;

@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class NPHandler implements CommandHandler {
	static final Pattern npPattern = Pattern
			.compile("(?:is listening to|is watching|is playing|is editing)"
					+ " \\[https?://osu.ppy.sh"
					// new style
					+ "(/beatmapsets/\\d+(#(?<mode>[a-z]+))?"
					// old style
					+ "|/(?<idtype>b|s))"
					+ "(/(?<id>\\d+))?.*\\]"
					+ "(?<mods>(?: "
					+ "(?:"
					+ "-Easy|-NoFail|-HalfTime"
					+ "|\\+HardRock|\\+SuddenDeath|\\+Perfect|\\+DoubleTime|\\+Nightcore|\\+Hidden|\\+Flashlight"
					+ "|~Relax~|~AutoPilot~|-SpunOut|\\|Autoplay\\|" + "))*)");

	private final BotBackend backend;

	private final LiveActivity live;

	@Override
	public GameChatResponse handle(String message, OsuApiUser apiUser, UserData userData) throws UserException, IOException, SQLException, InterruptedException {
		MDC.put(MdcUtils.MDC_HANDLER, "np");
		
		Language lang = userData.getLanguage();

		BeatmapWithMods pair = parseNP(message, lang);

		if (pair == null)
			return null;

		MdcUtils.getEventId().ifPresent(eventId -> live.propagateMessageDetails(eventId, "/np"));

		BeatmapMeta beatmap = backend.loadBeatmap(pair.getBeatmap(),
				pair.getMods(), lang);

		if (beatmap == null) {
			throw new UserException(lang.unknownBeatmap());
		}

		PercentageEstimates estimates = beatmap.getEstimates();

		String addition = null;
		if (estimates.getMods() != pair.getMods()) {
			addition = "(" + lang.noInformationForModsShort() + ")";
		}
		userData.setLastSongInfo(new BeatmapWithMods(pair
				.getBeatmap(), beatmap.getMods()));
		return new Success(beatmap.formInfoMessage(false, addition, userData.getHearts(), null, null, null))
				.then(lang.optionalCommentOnNP(apiUser, beatmap));
	}

	@CheckForNull
	@SuppressFBWarnings(value = "TQ", justification = "parser")
	public BeatmapWithMods parseNP(String message, Language lang) throws UserException {
		Matcher m = npPattern.matcher(message);

		if (!m.matches()) {
			return null;
		}

		if(m.group("id") == null || "s".equals(m.group("idtype"))) {
			throw new UserException(lang.isSetId());
		}

		if (m.group("mode") != null && !m.group("mode").equals("osu")) {
			throw new UserException("where osu");
		}

		int beatmapid = Integer.parseInt(m.group("id"));

		long mods = 0;

		Pattern words = Pattern.compile("\\w+");

		Matcher mWords = words.matcher(m.group("mods"));

		while (mWords.find()) {
			Mods mod = Mods.valueOf(mWords.group());

			if (mod.isEffective())
				mods |= Mods.getMask(mod);
		}

		return new BeatmapWithMods(beatmapid, mods);
	}
}
