package jp.minecraftcooks.core.score;

/** 順位を決めるための、チームの成績。 */
public record TeamResult(String teamId, int total, int lastRound, int mistakes) {}
