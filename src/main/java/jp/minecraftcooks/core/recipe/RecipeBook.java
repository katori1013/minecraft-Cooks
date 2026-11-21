package jp.minecraftcooks.core.recipe;

import jp.minecraftcooks.core.EquipType;

import java.util.*;

/** recipes.yml の中身。読み込みの時点で、識別子の誤りをすべて弾く。 */
public final class RecipeBook {
    private final Map<String, String> itemNames;
    private final Map<EquipType, String> boxes;
    private final String ruinedId;
    private final String burntId;
    private final Map<String, Shop> shops;

    private RecipeBook(Map<String, String> itemNames, Map<EquipType, String> boxes, String ruinedId, String burntId, Map<String, Shop> shops) {
        this.itemNames = Collections.unmodifiableMap(itemNames);
        this.boxes = Collections.unmodifiableMap(boxes);
        this.ruinedId = ruinedId;
        this.burntId = burntId;
        this.shops = Collections.unmodifiableMap(shops);
    }

    public Map<String, String> itemNames() { return itemNames; }
    public String ruinedId() { return ruinedId; }
    public String burntId() { return burntId; }

    public String rawItemFor(EquipType box) {
        String s = boxes.get(box);
        if (s == null) throw new IllegalArgumentException("食材箱ではありません: " + box);
        return s;
    }

    public Optional<Shop> shop(String id) {
        return Optional.ofNullable(shops.get(id));
    }

    /** 書かれた順(chinese, french, japanese)。 */
    public List<String> shopIds() {
        return List.copyOf(shops.keySet());
    }

    /** 表示名。知らない識別子はそのまま返す。 */
    public String nameOf(String itemId) {
        return itemNames.getOrDefault(itemId, itemId);
    }

    public static RecipeBook parse(Map<String, Object> yaml) {
        if (yaml == null) throw new RecipeException("recipes.yml が空です");
        Map<String, String> items = new LinkedHashMap<>();
        Map<?, ?> itemMap = map(yaml.get("items"), "items");
        for (Map.Entry<?, ?> e : itemMap.entrySet()) {
            items.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
        }

        Map<EquipType, String> boxes = new EnumMap<>(EquipType.class);
        Map<?, ?> boxMap = map(yaml.get("boxes"), "boxes");
        for (Map.Entry<?, ?> e : boxMap.entrySet()) {
            String where = "boxes." + e.getKey();
            EquipType t = EquipType.byKey(String.valueOf(e.getKey())).filter(EquipType::isBox)
                    .orElseThrow(() -> new RecipeException(where + ": 食材箱ではない設備 '" + e.getKey() + "'"));
            boxes.put(t, item(items, e.getValue(), where));
        }
        for (EquipType t : EquipType.values()) {
            if (t.isBox() && !boxes.containsKey(t)) throw new RecipeException("boxes: '" + t.key() + "' がありません");
        }

        Map<?, ?> failure = map(yaml.get("failure"), "failure");
        String ruined = item(items, failure.get("ruined"), "failure.ruined");
        String burnt = item(items, failure.get("burnt"), "failure.burnt");

        Map<String, Shop> shops = new LinkedHashMap<>();
        Map<?, ?> shopMap = map(yaml.get("shops"), "shops");
        if (shopMap.isEmpty()) throw new RecipeException("shops: 店が1つもありません");
        for (Map.Entry<?, ?> se : shopMap.entrySet()) {
            String sid = String.valueOf(se.getKey());
            String base = "shops." + sid;
            Map<?, ?> s = map(se.getValue(), base);
            String name = s.get("name") == null ? sid : String.valueOf(s.get("name"));

            List<Process> processes = new ArrayList<>();
            List<?> procList = list(s.get("processes"), base + ".processes");
            for (int i = 0; i < procList.size(); i++) {
                String where = base + ".processes[" + i + "]";
                Map<?, ?> p = map(procList.get(i), where);
                String ek = String.valueOf(p.get("equip"));
                EquipType equip = EquipType.byKey(ek)
                        .filter(t -> t.isCooker() || t == EquipType.CUTTING_BOARD)
                        .orElseThrow(() -> new RecipeException(where + ".equip: 調理に使えない設備 '" + ek + "'"));
                List<?> in = list(p.get("in"), where + ".in");
                if (in.isEmpty()) throw new RecipeException(where + ".in: 入力が空です");
                List<String> inputs = new ArrayList<>();
                for (int j = 0; j < in.size(); j++) inputs.add(item(items, in.get(j), where + ".in[" + j + "]"));
                if (equip == EquipType.CUTTING_BOARD && inputs.size() != 1) {
                    throw new RecipeException(where + ".in: まな板の入力は1つだけです");
                }
                processes.add(new Process(equip, inputs, item(items, p.get("out"), where + ".out")));
            }

            List<Dish> dishes = new ArrayList<>();
            Map<?, ?> dishMap = map(s.get("dishes"), base + ".dishes");
            for (Map.Entry<?, ?> de : dishMap.entrySet()) {
                String did = String.valueOf(de.getKey());
                String where = base + ".dishes." + did;
                Map<?, ?> d = map(de.getValue(), where);
                String dname = d.get("name") == null ? did : String.valueOf(d.get("name"));
                String ditem = item(items, d.get("item"), where + ".item");
                List<?> ps = list(d.get("parts"), where + ".parts");
                if (ps.isEmpty()) throw new RecipeException(where + ".parts: 部品が空です");
                List<String> parts = new ArrayList<>();
                for (int j = 0; j < ps.size(); j++) parts.add(item(items, ps.get(j), where + ".parts[" + j + "]"));
                dishes.add(new Dish(did, dname, ditem, parts));
            }
            if (dishes.isEmpty()) throw new RecipeException(base + ".dishes: 料理が1つもありません");
            shops.put(sid, new Shop(sid, name, processes, dishes));
        }
        return new RecipeBook(items, boxes, ruined, burnt, shops);
    }

    private static String item(Map<String, String> items, Object v, String where) {
        if (v == null) throw new RecipeException(where + ": アイテムが書かれていません");
        String id = String.valueOf(v);
        if (!items.containsKey(id)) throw new RecipeException(where + ": 未定義のアイテム '" + id + "'");
        return id;
    }

    private static Map<?, ?> map(Object o, String where) {
        if (o instanceof Map<?, ?> m) return m;
        throw new RecipeException(where + ": 項目の一覧(マップ)が必要です");
    }

    private static List<?> list(Object o, String where) {
        if (o instanceof List<?> l) return l;
        throw new RecipeException(where + ": リストが必要です");
    }
}
