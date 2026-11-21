# テクスチャ一覧

リソースパックの名前空間は `minecraft-cooks`。どのアイテムもバニラの紙(`paper`)をベースにし、`item_model` に `minecraft-cooks:<識別子>` を指定する。

## リソースパックの中の置き場所(1.21.4 以降の形式)

識別子が `veg_raw` の場合:

| ファイル | 中身 | 作る人 |
| --- | --- | --- |
| `assets/minecraft-cooks/items/veg_raw.json` | アイテムモデルの定義 | 実装者が雛形を出す |
| `assets/minecraft-cooks/models/item/veg_raw.json` | モデル | 実装者が雛形を出す(平面のアイテム。設備は立方体) |
| `assets/minecraft-cooks/textures/item/veg_raw.png` | 画像 | **依頼者** |

画像のサイズは 16×16 を基本にする(大きくしてもよい)。設備を Blockbench で立体にする場合は、モデルの JSON も依頼者のものに差し替える。

## 1. 調理前の食材(3個)

| 識別子 | 表示名 | 何の絵か |
| --- | --- | --- |
| `veg_raw` | 野菜 | 生の野菜 |
| `noodle_raw` | 麺 | 茹でる前の麺 |
| `rice_raw` | 米 | 炊いたご飯(チャーハン用) |

## 2. 下ごしらえ・途中の食材(8個)

| 識別子 | 表示名 | どうやってできるか |
| --- | --- | --- |
| `meat_cut` | 切った肉 | 肉 → まな板 |
| `meat_boiled` | 茹でた肉 | 肉 → 茹で鍋 |
| `meat_boiled_cut` | 切った茹で肉 | 茹でた肉 → まな板(ラーメンのチャーシュー) |
| `fish_cut` | 切った魚 | 魚 → まな板 |
| `fish_cut_fine` | 細かく切った魚 | 切った魚 → まな板(天ぷら) |
| `fish_grilled2` | よく焼いた魚 | 焼いた魚 → フライパン(ムニエル) |
| `veg_cut` | 切った野菜 | 野菜 → まな板 |
| `noodle_boiled` | 茹でた麺 | 麺 → 茹で鍋 |

## 3. 調理済み(皿に載せる前)(6個)

| 識別子 | 表示名 | どうやってできるか |
| --- | --- | --- |
| `shumai_steamed` | 蒸したシュウマイ | 切った野菜+切った肉 → 蒸し鍋 |
| `chahan_fried` | 炒めたチャーハン | 切った野菜+切った肉+米 → フライパン |
| `terrine_set` | 固めたテリーヌ | 切った肉か魚+切った野菜 → 固め鍋 |
| `potofu_stewed` | 煮込んだポトフ | 切った野菜+切った肉 → 煮込み鍋(フランス) |
| `nikujaga_stewed` | 煮込んだ肉じゃが | 切った肉+切った野菜 → 煮込み鍋(和食) |
| `tempura_fried` | 揚げた天ぷら | 細かく切った魚 → フライヤー |

## 4. 失敗(1個)

| 識別子 | 表示名 | どうやってできるか |
| --- | --- | --- |
| `ruined` | 失敗した料理 | レシピに合わないものを調理した |

## 5. 盛り付け済みの料理(9個)

皿に載った完成品の絵。納品するのはこれ。

| 識別子 | 表示名 | 店 |
| --- | --- | --- |
| `dish_ramen` | ラーメン | 中華 |
| `dish_shumai` | シュウマイ | 中華 |
| `dish_chahan` | チャーハン | 中華 |
| `dish_terrine` | テリーヌ | フランス |
| `dish_meuniere` | ムニエル | フランス |
| `dish_potofu` | ポトフ | フランス |
| `dish_tempura` | 天ぷら | 和食 |
| `dish_nikujaga` | 肉じゃが | 和食 |
| `dish_soba` | おそば | 和食 |

## 6. 設備(15個)

ブロック1マスぶんの大きさで表示する。中身の食材は、設備の上に小さく別に表示されるので、設備の絵には描かなくてよい。

| 識別子 | 表示名 | 補足 |
| --- | --- | --- |
| `equip_box_meat` | 肉の食材箱 | 何の箱か分かる絵にする |
| `equip_box_fish` | 魚の食材箱 | |
| `equip_box_veg` | 野菜の食材箱 | |
| `equip_box_noodle` | 麺の食材箱 | |
| `equip_box_rice` | 米の食材箱 | |
| `equip_cutting_board` | まな板 | |
| `equip_pan` | フライパン | |
| `equip_pot_boil` | 茹で鍋 | 鍋は4種類。見分けがつくようにする |
| `equip_pot_steam` | 蒸し鍋 | せいろなど |
| `equip_pot_stew` | 煮込み鍋 | |
| `equip_pot_set` | 固め鍋 | テリーヌ型など |
| `equip_fryer` | フライヤー | |
| `equip_plate` | 皿(盛り付け台) | |
| `equip_counter` | 納品場所 | 1〜3番の数字は、上に浮かぶ文字で出すので描かなくてよい |
| `equip_trash` | ゴミ箱 | |

## 7. バニラの見た目を使うもの(6個・画像はいらない)

ベースは紙のままで、見た目だけバニラのアイテムを借りる。本物の牛肉などとは別物として扱われ、食べられない。

| 識別子 | 表示名 | 使うバニラの見た目 |
| --- | --- | --- |
| `meat_raw` | 肉 | 生の牛肉(`minecraft:beef`) |
| `fish_raw` | 魚 | 生のタラ(`minecraft:cod`) |
| `meat_grilled` | 焼いた肉 | ステーキ(`minecraft:cooked_beef`) |
| `fish_grilled` | 焼いた魚 | 焼き鱈(`minecraft:cooked_cod`) |
| `burnt` | 焦げ | 木炭(`minecraft:charcoal`) |
| `wand` | 設備登録ワンド | ブレイズロッド(`minecraft:blaze_rod`) |

`fish_grilled2`(よく焼いた魚)は自作の画像なので、焼き鱈と見分けがつく絵にする。
対応はコードの `CustomItems.VANILLA_MODELS` に書いてある。

## 合計

描く画像は42個(食材3 + 途中8 + 調理済み6 + 失敗1 + 料理9 + 設備15)。ほかに6個はバニラの見た目を使う。

レシピを YAML で増やしたときは、増えた識別子の画像も足す。画像がないアイテムは、紫と黒の模様(テクスチャ抜け)で表示される。
