# Tillerinobot

[![Build Status](https://github.com/Tillerino/Tillerinobot/actions/workflows/build.yml/badge.svg)](https://github.com/Tillerino/Tillerinobot/actions/workflows/build.yml)
[![codecov](https://codecov.io/gh/Tillerino/Tillerinobot/branch/master/graph/badge.svg)](https://codecov.io/gh/Tillerino/Tillerinobot)

This project contains the IRC frontend and a growing part of the backend for ppaddict recommendations and similar services.
The web frontend can be found in the [ppaddict](https://github.com/Tillerino/ppaddict) project.

**Just want to use the bot? Message Tillerino in-game!**

Please visit the [wiki](https://github.com/Tillerino/Tillerinobot/wiki) for documentation.

Join the discussion on [discord](https://discord.gg/0ww19XGd9XsiJ4LI)!


<p align="center">
  <a href="https://discordapp.com/invite/0ww19XGd9XsiJ4LI">
    <img alt="Logo" src="https://discordapp.com/api/guilds/170177781257207808/widget.png?style=banner2">
  </a>
</p>

## Technology

Since a lot of people ask, the IRC frontend is built using [PircBotX](https://github.com/TheLQ/pircbotx).

For the [osu! API](https://github.com/ppy/osu-api/wiki), I rolled my own [Java library](https://github.com/Tillerino/osuApiConnector). It's available in [maven central](https://mvnrepository.com/artifact/com.github.tillerino/osu-api-connector). It also does some of the AR/OD calculations.

Since it came out, [oppai](https://github.com/Francesco149/oppai-ng) (or rather [a port](https://github.com/Francesco149/koohii)) is being used to make beatmap difficulty calculation more stable and to calculate difficulties for unranked beatmaps.

## Building/Running Tillerinobot (for developing purposes)

Check out [the wiki](https://github.com/Tillerino/Tillerinobot/wiki/Working-on-Tillerinobot) to find out how to build and run Tillerinobot locally for developing purposes.
