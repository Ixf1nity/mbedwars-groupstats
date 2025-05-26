package me.infinity.groupstats.models;

import lombok.Getter;

@Getter
public enum GroupEnum {
    SOLO(1, "solos"),
    DUOS(2,"doubles"),
    TRIPLES(3, "triples"),
    QUADS(4, "quads");

    private final int maxPlayers;
    private final String jsonFormat;
    GroupEnum(int maxPlayers, String jsonFormat) {
        this.maxPlayers = maxPlayers;
        this.jsonFormat = jsonFormat;
    }

    public static GroupEnum which(int maxPlayers) {
        switch (maxPlayers) {
            case 4:
                return GroupEnum.QUADS;
            case 3:
                return GroupEnum.TRIPLES;
            case 2:
                return GroupEnum.DUOS;
            case 1:
                return GroupEnum.SOLO;
            default:
                throw new RuntimeException("you suck mf, get a life");
        }
    }
}