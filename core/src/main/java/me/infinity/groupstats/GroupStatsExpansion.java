//package me.infinity.groupstats;
//
//import lombok.Getter;
//import lombok.RequiredArgsConstructor;
//import lombok.SneakyThrows;
//import me.clip.placeholderapi.expansion.PlaceholderExpansion;
//import me.infinity.groupstats.models.StatisticType;
//import org.bukkit.OfflinePlayer;
//import org.jetbrains.annotations.NotNull;
//import org.jetbrains.annotations.Nullable;
//
//import java.math.BigDecimal;
//import java.math.RoundingMode;
//import java.util.Map;
//import java.util.concurrent.ExecutionException;
//
//@RequiredArgsConstructor
//public class GroupStatsExpansion extends PlaceholderExpansion {
//
//    @Getter
//    private final GroupStatsPlugin instance;
//
//    @Override
//    public boolean persist() {
//        return true;
//    }
//
//    @Override
//    public @NotNull String getIdentifier() {
//        return "groupstats";
//    }
//
//    @Override
//    public @NotNull String getAuthor() {
//        return "infinity";
//    }
//
//    @Override
//    public @NotNull String getVersion() {
//        return "1.0";
//    }
//
//    @Override
//    @SneakyThrows
//    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
//        if (player == null) {
//            return null;
//        }
//
//        String[] args = params.split("_");
//        if (args.length < 2 || args[0] == null || args[1] == null) {
//            return "INVALID_PLACEHOLDER";
//        }
//
//        String groupName = args[0];
//        StatisticType statisticType = StatisticType.fromString(args[1]);
//        if (statisticType == null) {
//            return "0";
//        }
//
//        GroupProfile profile;
//        try {
//            profile = instance.getGson().fromJson(instance.getMongoStorage().loadRawDataAsync(player.getUniqueId()).get().toJson(), GroupProfile.class);
//        } catch (InterruptedException | ExecutionException e) {
//            this.getInstance().getLogger().warning(e.getMessage());
//            return "ERROR; " + e.getMessage();
//        }
//
//        if (profile == null) {
//            return "0";
//        }
//
//        Map<String, GroupNode> stats = profile.getStatistics();
//
//        if (groupName.equalsIgnoreCase("overAll")) {
//            return handleOverallStats(stats, statisticType);
//        }
//
//        GroupNode groupNode = stats.get(groupName);
//        if (groupNode == null) {
//            return "0";
//        }
//
//        return handleGroupStats(groupNode, statisticType);
//    }
//
//    private String handleOverallStats(Map<String, GroupNode> stats, StatisticType type) {
//        if (stats.isEmpty()) {
//            return "0";
//        }
//
//        switch (type) {
//            case GAMESPLAYED:
//                return String.valueOf(stats.values().stream().mapToInt(value -> value.getGamesPlayed().get()).sum());
//            case BEDSBROKEN:
//                return String.valueOf(stats.values().stream().mapToInt(value -> value.getBedsBroken().get()).sum());
//            case BEDSLOST:
//                return String.valueOf(stats.values().stream().mapToInt(value -> value.getBedsLost().get()).sum());
//            case KILLS:
//                return String.valueOf(stats.values().stream().mapToInt(value -> value.getKills().get()).sum());
//            case DEATHS:
//                return String.valueOf(stats.values().stream().mapToInt(value -> value.getDeaths().get()).sum());
//            case FINALKILLS:
//                return String.valueOf(stats.values().stream().mapToInt(value -> value.getFinalKills().get()).sum());
//            case FINALDEATHS:
//                return String.valueOf(stats.values().stream().mapToInt(value -> value.getFinalDeaths().get()).sum());
//            case WINS:
//                return String.valueOf(stats.values().stream().mapToInt(value -> value.getWins().get()).sum());
//            case LOSSES:
//                return String.valueOf(stats.values().stream().mapToInt(value -> value.getLosses().get()).sum());
//            case WINSTREAK:
//                return String.valueOf(stats.values().stream().mapToInt(value -> value.getWinstreak().get()).sum());
//            case HIGHESTWINSTREAK:
//                return String.valueOf(stats.values().stream().mapToInt(value -> value.getHighestWinstreak().get()).max().orElse(0));
//            case KDR:
//                int kills = stats.values().stream().mapToInt(value -> value.getKills().get()).sum();
//                int deaths = stats.values().stream().mapToInt(value -> value.getDeaths().get()).sum();
//                return String.valueOf(getRatio(kills, deaths));
//            case FKDR:
//                int finalKills = stats.values().stream().mapToInt(value -> value.getFinalKills().get()).sum();
//                int finalDeaths = stats.values().stream().mapToInt(value -> value.getFinalDeaths().get()).sum();
//                return String.valueOf(getRatio(finalKills, finalDeaths));
//            case BBLR:
//                int bedsBroken = stats.values().stream().mapToInt(value -> value.getBedsBroken().get()).sum();
//                int bedsLost = stats.values().stream().mapToInt(value -> value.getBedsLost().get()).sum();
//                return String.valueOf(getRatio(bedsBroken, bedsLost));
//            case WLR:
//                int wins = stats.values().stream().mapToInt(value -> value.getWins().get()).sum();
//                int losses = stats.values().stream().mapToInt(value -> value.getLosses().get()).sum();
//                return String.valueOf(getRatio(wins, losses));
//            default:
//                return "0";
//        }
//    }
//
//    private String handleGroupStats(GroupNode groupNode, StatisticType type) {
//        switch (type) {
//            case GAMESPLAYED:
//                return String.valueOf(groupNode.getGamesPlayed().get());
//            case BEDSBROKEN:
//                return String.valueOf(groupNode.getBedsBroken().get());
//            case BEDSLOST:
//                return String.valueOf(groupNode.getBedsLost().get());
//            case KILLS:
//                return String.valueOf(groupNode.getKills().get());
//            case DEATHS:
//                return String.valueOf(groupNode.getDeaths().get());
//            case FINALKILLS:
//                return String.valueOf(groupNode.getFinalKills().get());
//            case FINALDEATHS:
//                return String.valueOf(groupNode.getFinalDeaths().get());
//            case WINS:
//                return String.valueOf(groupNode.getWins().get());
//            case LOSSES:
//                return String.valueOf(groupNode.getLosses().get());
//            case WINSTREAK:
//                return String.valueOf(groupNode.getWinstreak().get());
//            case HIGHESTWINSTREAK:
//                return String.valueOf(groupNode.getHighestWinstreak().get());
//            case KDR:
//                return String.valueOf(getRatio(groupNode.getKills().get(), groupNode.getDeaths().get()));
//            case FKDR:
//                return String.valueOf(getRatio(groupNode.getFinalKills().get(), groupNode.getFinalDeaths().get()));
//            case BBLR:
//                return String.valueOf(getRatio(groupNode.getBedsBroken().get(), groupNode.getBedsLost().get()));
//            case WLR:
//                return String.valueOf(getRatio(groupNode.getWins().get(), groupNode.getLosses().get()));
//            default:
//                return "0";
//        }
//    }
//
//    private double getRatio(int numerator, int denominator) {
//        if (denominator == 0) {
//            return numerator;
//        }
//        double value = (double) numerator / denominator;
//        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
//    }
//}
