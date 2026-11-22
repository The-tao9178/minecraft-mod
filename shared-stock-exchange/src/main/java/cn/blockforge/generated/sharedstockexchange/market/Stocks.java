package cn.blockforge.generated.sharedstockexchange.market;

import java.util.List;

/**
 * 交易所里挂牌的"公司"。发行价参考等价交换（ProjectE）里对应物品的 EMC 价值，
 * 所以装了等价交换的玩家会觉得价格很眼熟。
 */
public final class Stocks {
    public static final List<StockDefinition> ALL = List.of(
            new StockDefinition("iron", "minecraft:iron_ingot", 256.0D, 1000L),
            new StockDefinition("copper", "minecraft:copper_ingot", 128.0D, 1000L),
            new StockDefinition("redstone", "minecraft:redstone", 64.0D, 1000L),
            new StockDefinition("lapis", "minecraft:lapis_lazuli", 864.0D, 1000L),
            new StockDefinition("gold", "minecraft:gold_ingot", 2048.0D, 1000L),
            new StockDefinition("diamond", "minecraft:diamond", 8192.0D, 1000L),
            new StockDefinition("emerald", "minecraft:emerald", 16384.0D, 1000L),
            new StockDefinition("ender_pearl", "minecraft:ender_pearl", 1024.0D, 1000L)
    );

    private Stocks() {
    }

    public static StockDefinition byId(String id) {
        if (id == null) {
            return null;
        }
        for (StockDefinition definition : ALL) {
            if (definition.id().equals(id)) {
                return definition;
            }
        }
        return null;
    }

    public static int indexOf(String id) {
        return ALL.indexOf(byId(id));
    }

    public static String nameKey(String id) {
        return "stock." + "shared_stock_exchange." + id;
    }
}
