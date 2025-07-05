package me.infinity.groupstats.models;

import lombok.Getter;

@Getter
public enum StatisticType {

    GAMESPLAYED("gs"), BEDSBROKEN("bb"), BEDSLOST("bl"), KILLS("k"), DEATHS("d"), FINALKILLS("fk"), FINALDEATHS("fd"), WINS("w"), LOSSES("l"), WINSTREAK("ws"), HIGHESTWINSTREAK("hws"), KDR("kdr"), FKDR("fkdr"), BBLR("bblr"), WLR("wlr");

    String column;

    StatisticType(String column) {
        this.column = column;
    }

    public static StatisticType fromString(String text) {
        if (text == null) {
            return null;
        }
        try {
            return StatisticType.valueOf(text.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
