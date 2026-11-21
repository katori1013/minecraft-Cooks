package jp.minecraftcooks.core;

import jp.minecraftcooks.core.score.TieBreak;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 設定GUIで変えられる数値設定。時間は tick(1/20秒)で持つ。 */
public final class Settings {

    /** k が fromK 以上のとき、料理の個数を min〜max にする段階。 */
    public record Tier(int fromK, int minDishes, int maxDishes) {}

    /** 完成までの tick と、完成から焦げるまでの tick。 */
    public record CookTime(int cookTicks, int burnTicks) {}

    private int roundSeconds = 300;
    private int breakSeconds = 30;
    private int countdownSeconds = 5;
    private int orderSlots = 3;
    private List<Tier> tiers = defaultTiers();
    private int chopCount = 5;
    private int maxCookerItems = 4;
    private final Map<EquipType, CookTime> cookTimes = defaultCookTimes();
    private int pointsPerDish = 100;
    private List<Double> multipliers = new ArrayList<>(List.of(1.0, 1.1, 1.2, 1.3));
    private double penaltyRate = 0.0025;
    private TieBreak tieBreak = TieBreak.LAST_ROUND;
    private int submitConfirmTicks = 60;

    private static List<Tier> defaultTiers() {
        return new ArrayList<>(List.of(new Tier(0, 1, 2), new Tier(6, 1, 3), new Tier(14, 2, 4)));
    }

    private static Map<EquipType, CookTime> defaultCookTimes() {
        Map<EquipType, CookTime> m = new EnumMap<>(EquipType.class);
        m.put(EquipType.PAN, new CookTime(160, 160));
        m.put(EquipType.POT_BOIL, new CookTime(200, 200));
        m.put(EquipType.POT_STEAM, new CookTime(200, 200));
        m.put(EquipType.POT_STEW, new CookTime(240, 240));
        m.put(EquipType.POT_SET, new CookTime(240, 240));
        m.put(EquipType.FRYER, new CookTime(160, 160));
        return m;
    }

    /** fromK が k 以下の段階のうち、fromK が一番大きいもの。 */
    public Tier tierFor(int k) {
        Tier best = null;
        for (Tier t : tiers) {
            if (t.fromK() <= k && (best == null || t.fromK() > best.fromK())) best = t;
        }
        if (best == null) {
            best = tiers.stream().min((a, b) -> Integer.compare(a.fromK(), b.fromK())).orElse(new Tier(0, 1, 1));
        }
        return best;
    }

    public CookTime cookTime(EquipType type) {
        CookTime t = cookTimes.get(type);
        if (t == null) throw new IllegalArgumentException("調理する設備ではありません: " + type);
        return t;
    }

    public void setCookTime(EquipType type, CookTime t) {
        if (!type.isCooker()) throw new IllegalArgumentException("調理する設備ではありません: " + type);
        cookTimes.put(type, t);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("roundSeconds", roundSeconds);
        m.put("breakSeconds", breakSeconds);
        m.put("countdownSeconds", countdownSeconds);
        m.put("orderSlots", orderSlots);
        List<Map<String, Object>> ts = new ArrayList<>();
        for (Tier t : tiers) {
            Map<String, Object> tm = new LinkedHashMap<>();
            tm.put("fromK", t.fromK());
            tm.put("minDishes", t.minDishes());
            tm.put("maxDishes", t.maxDishes());
            ts.add(tm);
        }
        m.put("tiers", ts);
        m.put("chopCount", chopCount);
        m.put("maxCookerItems", maxCookerItems);
        Map<String, Object> ct = new LinkedHashMap<>();
        for (Map.Entry<EquipType, CookTime> e : cookTimes.entrySet()) {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("cookTicks", e.getValue().cookTicks());
            cm.put("burnTicks", e.getValue().burnTicks());
            ct.put(e.getKey().key(), cm);
        }
        m.put("cookTimes", ct);
        m.put("pointsPerDish", pointsPerDish);
        m.put("multipliers", new ArrayList<>(multipliers));
        m.put("penaltyRate", penaltyRate);
        m.put("tieBreak", tieBreak.name());
        m.put("submitConfirmTicks", submitConfirmTicks);
        return m;
    }

    /** Map から作る。足りないキーや読めない値は初期値のままにする。 */
    public static Settings fromMap(Map<String, ?> m) {
        Settings s = new Settings();
        s.roundSeconds = intOr(m.get("roundSeconds"), s.roundSeconds);
        s.breakSeconds = intOr(m.get("breakSeconds"), s.breakSeconds);
        s.countdownSeconds = intOr(m.get("countdownSeconds"), s.countdownSeconds);
        s.orderSlots = intOr(m.get("orderSlots"), s.orderSlots);
        if (m.get("tiers") instanceof List<?> list && !list.isEmpty()) {
            List<Tier> ts = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> tm) {
                    ts.add(new Tier(intOr(tm.get("fromK"), 0), intOr(tm.get("minDishes"), 1), intOr(tm.get("maxDishes"), 1)));
                }
            }
            if (!ts.isEmpty()) s.tiers = ts;
        }
        s.chopCount = intOr(m.get("chopCount"), s.chopCount);
        s.maxCookerItems = intOr(m.get("maxCookerItems"), s.maxCookerItems);
        if (m.get("cookTimes") instanceof Map<?, ?> ct) {
            for (Map.Entry<?, ?> e : ct.entrySet()) {
                EquipType.byKey(String.valueOf(e.getKey())).filter(EquipType::isCooker).ifPresent(type -> {
                    if (e.getValue() instanceof Map<?, ?> cm) {
                        CookTime def = s.cookTimes.get(type);
                        s.cookTimes.put(type, new CookTime(intOr(cm.get("cookTicks"), def.cookTicks()), intOr(cm.get("burnTicks"), def.burnTicks())));
                    }
                });
            }
        }
        s.pointsPerDish = intOr(m.get("pointsPerDish"), s.pointsPerDish);
        if (m.get("multipliers") instanceof List<?> list && !list.isEmpty()) {
            List<Double> ms = new ArrayList<>();
            for (Object o : list) if (o instanceof Number n) ms.add(n.doubleValue());
            if (!ms.isEmpty()) s.multipliers = ms;
        }
        if (m.get("penaltyRate") instanceof Number n) s.penaltyRate = n.doubleValue();
        if (m.get("tieBreak") != null) {
            try {
                s.tieBreak = TieBreak.valueOf(String.valueOf(m.get("tieBreak")));
            } catch (IllegalArgumentException ignored) {
                // 読めない値は初期値のまま
            }
        }
        s.submitConfirmTicks = intOr(m.get("submitConfirmTicks"), s.submitConfirmTicks);
        return s;
    }

    private static int intOr(Object o, int def) {
        return o instanceof Number n ? n.intValue() : def;
    }

    public int getRoundSeconds() { return roundSeconds; }
    public void setRoundSeconds(int v) { roundSeconds = v; }
    public int getBreakSeconds() { return breakSeconds; }
    public void setBreakSeconds(int v) { breakSeconds = v; }
    public int getCountdownSeconds() { return countdownSeconds; }
    public void setCountdownSeconds(int v) { countdownSeconds = v; }
    public int getOrderSlots() { return orderSlots; }
    public void setOrderSlots(int v) { orderSlots = v; }
    public List<Tier> getTiers() { return tiers; }
    public void setTiers(List<Tier> v) { tiers = new ArrayList<>(v); }
    public int getChopCount() { return chopCount; }
    public void setChopCount(int v) { chopCount = v; }
    public int getMaxCookerItems() { return maxCookerItems; }
    public void setMaxCookerItems(int v) { maxCookerItems = v; }
    public int getPointsPerDish() { return pointsPerDish; }
    public void setPointsPerDish(int v) { pointsPerDish = v; }
    public List<Double> getMultipliers() { return multipliers; }
    public void setMultipliers(List<Double> v) { multipliers = new ArrayList<>(v); }
    public double getPenaltyRate() { return penaltyRate; }
    public void setPenaltyRate(double v) { penaltyRate = v; }
    public TieBreak getTieBreak() { return tieBreak; }
    public void setTieBreak(TieBreak v) { tieBreak = v; }
    public int getSubmitConfirmTicks() { return submitConfirmTicks; }
    public void setSubmitConfirmTicks(int v) { submitConfirmTicks = v; }
}
