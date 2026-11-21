# Minecraft Cooks プラグイン 実装計画

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 企画書 第2版どおりに、4〜8チームが同時に遊べるMinecraft Cooks の Paper プラグインを作る。

**Architecture:** ゲームのルール(レシピの一致判定、調理の状態、注文、得点、チーム分け)は、Bukkit に依存しない `jp.minecraftcooks.core` パッケージに置き、JUnit でテストする。`jp.minecraftcooks.paper` パッケージが、Paper のイベント、エンティティ、コマンド、表示を、core につなぐ。時間はすべて tick(1/20秒)で数える。サーバーのメインスレッドで、1 tick ごとに進める。

**Tech Stack:** Java 21、Gradle(Kotlin DSL)、paper-api 1.21.11-R0.1-SNAPSHOT(compileOnly)、JUnit 5、SnakeYAML(paper-api に含まれるものをテストで使う)

**Spec:** `/Users/itakurayakumo/claude code/claude参照可能ファイル/cooking-game-plugin-plan-v2.md`(補足: `docs/design.md`、`docs/textures.md`)

## Global Constraints

- プロジェクトの置き場所: `/Users/itakurayakumo/claude code/coding/javaa/minecraft-cooks`
- Java 21。`JAVA_HOME=~/Library/Java/JavaVirtualMachines/openjdk-21.0.2/Contents/Home` でビルドする
- 依存は paper-api だけ。ほかのプラグインやライブラリは入れない。新しく入れる場合は、依頼者に確認する
- パッケージ `jp.minecraftcooks`、コマンド `/cg`、権限 `minecraft-cooks.admin`、名前空間 `minecraft-cooks`
- 独自アイテムは `Material.PAPER` に、`ItemMeta#setItemModel(new NamespacedKey("minecraft-cooks", id))` を設定する。種類は PDC キー `minecraft-cooks:id`(STRING)で判別する
- プレイヤーに見える文字は、すべて日本語で `messages.yml` から出す
- Java版 1.21.11 だけを対象にする。イベント専用サーバー前提
- git のコミットは、依頼者の同意を得てから行う。`git add` はファイルを個別に指定する

## Review Focus

1. **試合中に抜けて戻った人**: 同じチームとキッチンに戻り、手は空、アドベンチャーモードになる → Task 6 の `TeamRosterTest.rejoinKeepsTeam`
2. **一時停止中、カウントダウン中、休憩中のクリック**: 設備は反応せず、調理タイマーも進まない → Task 4 の `CookerStateTest.noTickNoProgress`、Task 9 の手動確認
3. **皿に、どの料理にも合わない部品を積み続ける**: 6個で受け付けなくなり、左クリックで1つずつ戻せる → Task 4 の `PlateStateTest.capsAtSix`
4. **注文と無関係な料理だけ、または注文より多く入れて送る**: 得点は0未満にならず、間違いは正しく数えられる → Task 5 の `ScoringTest.onlyWrongDishes` と `overDelivery`
5. **recipes.yml に、定義されていない識別子が書かれている**: 起動時に、その識別子と場所を名指しして読み込みが失敗する(実行中に落ちない) → Task 3 の `RecipeBookTest.unknownItemFails`

---

## ファイル構成

```
minecraft-cooks/
  build.gradle.kts, settings.gradle.kts, gradlew…, .gitignore
  src/main/java/jp/minecraftcooks/
    MinecraftCooksPlugin.java                 起動・終了、各部品の組み立て
    core/                                  Bukkit 非依存
      EquipType.java                       設備の種類
      Settings.java                        数値設定(初期値・Map との変換)
      BlockPos.java                        座標(record)
      recipe/RecipeBook.java, Shop.java, Process.java, Dish.java
      cook/CookerState.java, CuttingBoardState.java, PlateState.java
      order/Order.java, OrderGenerator.java, DeliverySlot.java
      score/Scoring.java, Ranking.java, TeamResult.java, TieBreak.java
      team/TeamSplitter.java, TeamRoster.java
      kitchen/KitchenLayout.java           設備定義と複製のずらし計算
    paper/
      item/CustomItems.java
      Messages.java
      pack/ResourcePackSender.java
      kitchen/KitchenRegistry.java, EquipmentEntities.java, WandListener.java
      play/KitchenRuntime.java, InteractionListener.java, HandLockListener.java
      match/MatchManager.java, Hud.java
      command/CgCommand.java
      gui/SettingsGui.java
  src/main/resources/ plugin.yml, config.yml, recipes.yml, messages.yml
  src/test/java/jp/minecraftcooks/core/...    JUnit
  resourcepack/                            モデル JSON の雛形
  docs/運営ガイド.md
```

---

### Task 1: プロジェクトの土台

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `.gitignore`, `src/main/resources/plugin.yml`, `src/main/java/jp/minecraftcooks/MinecraftCooksPlugin.java`
- Create: Gradle wrapper(`gradle wrapper --gradle-version 8.14` で作る)

**Interfaces:**
- Produces: `MinecraftCooksPlugin extends JavaPlugin`。`getInstance()` は作らず、部品にはコンストラクタで渡す

- [ ] **Step 1:** `build.gradle.kts` を書く。リポジトリは `https://repo.papermc.io/repository/maven-public/`。`compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")`、`testImplementation` に paper-api と `org.junit.jupiter:junit-jupiter:5.11.3`。toolchain は 21
- [ ] **Step 2:** `plugin.yml`: `name: minecraft-cooks`、`main: jp.minecraftcooks.MinecraftCooksPlugin`、`api-version: '1.21.11'`、コマンド `cg`、権限 `minecraft-cooks.admin`(default: op)
- [ ] **Step 3:** `MinecraftCooksPlugin#onEnable` で、起動ログ「Minecraft Cooks 有効化」を出すだけ
- [ ] **Step 4:** `./gradlew build` → `build/libs/minecraft-cooks-0.1.0.jar` ができる
- [ ] **Step 5:** コミット(同意後)

### Task 2: 数値設定(Settings)

**Files:**
- Create: `core/EquipType.java`, `core/Settings.java`, `core/score/TieBreak.java`
- Test: `src/test/java/jp/minecraftcooks/core/SettingsTest.java`

**Interfaces:**
- Produces:
  - `enum EquipType { BOX_MEAT, BOX_FISH, BOX_VEG, BOX_NOODLE, BOX_RICE, CUTTING_BOARD, PAN, POT_BOIL, POT_STEAM, POT_STEW, POT_SET, FRYER, PLATE, COUNTER, TRASH; String key(); static Optional<EquipType> byKey(String); boolean isCooker(); }`。key は小文字のスネークケース(`pot_boil`)
  - `enum TieBreak { LAST_ROUND, FEWER_MISTAKES, SHARE }`
  - `record Tier(int fromK, int minDishes, int maxDishes)`(Settings の中)
  - `record CookTime(int cookTicks, int burnTicks)`(Settings の中)
  - `Settings` のフィールド(getter/setter つき): `roundSeconds=300`, `breakSeconds=30`, `countdownSeconds=5`, `orderSlots=3`, `tiers=[(0,1,2),(6,1,3),(14,2,4)]`, `chopCount=5`, `maxCookerItems=4`, `cookTimes`(PAN 160/160, POT_BOIL 200/200, POT_STEAM 200/200, POT_STEW 240/240, POT_SET 240/240, FRYER 160/160), `pointsPerDish=100`, `multipliers=[1.0,1.1,1.2,1.3]`, `penaltyRate=0.0025`, `tieBreak=LAST_ROUND`, `submitConfirmTicks=60`
  - `Map<String,Object> toMap()`、`static Settings fromMap(Map<String,?>)`。足りないキーは初期値で埋める
  - `Tier tierFor(int k)`: fromK が k 以下のうち、一番大きいもの

- [ ] **Step 1:** テストを書く: `defaultsRoundTrip`(toMap→fromMap で全項目が等しい)、`missingKeysUseDefaults`(空 Map → 初期値)、`tierForK`(k=0→(0,1,2)、k=5→(0,1,2)、k=6→(6,1,3)、k=20→(14,2,4))
- [ ] **Step 2:** `./gradlew test` → コンパイルエラーで FAIL
- [ ] **Step 3:** 実装する
- [ ] **Step 4:** `./gradlew test` → PASS
- [ ] **Step 5:** コミット

### Task 3: レシピ(RecipeBook と recipes.yml)

**Files:**
- Create: `core/recipe/RecipeBook.java`, `Shop.java`, `Process.java`, `Dish.java`, `src/main/resources/recipes.yml`
- Test: `core/recipe/RecipeBookTest.java`

**Interfaces:**
- Consumes: `EquipType`
- Produces:
  - `record Process(EquipType equip, List<String> inputs, String output)`
  - `record Dish(String id, String name, String item, List<String> parts)`
  - `Shop`: `id()`, `name()`, `List<Dish> dishes()`, `Optional<String> cook(EquipType equip, List<String> contents)`(中身の多重集合が inputs とちょうど一致する工程の output)、`Optional<Dish> plate(List<String> parts)`(同じく一致)、`boolean accepts(EquipType equip, String item)`(その設備のどれかの工程の入力に含まれるか。まな板で、置けるかどうかの判定に使う)
  - `RecipeBook`: `static RecipeBook parse(Map<String,Object> yaml)`、`Map<String,String> itemNames()`、`String rawItemFor(EquipType box)`、`Optional<Shop> shop(String id)`、`List<String> shopIds()`(順番: chinese, french, japanese)
  - 失敗のときは `RecipeException(String message)`。メッセージに識別子と場所を入れる(例: `shops.chinese.processes[2].out: 未定義のアイテム 'meat_cutt'`)

recipes.yml の形:

```yaml
items:
  meat_raw: 肉
  # … textures.md の §1〜§5 の識別子をすべて書く(設備とワンドは除く)
boxes: { box_meat: meat_raw, box_fish: fish_raw, box_veg: veg_raw, box_noodle: noodle_raw, box_rice: rice_raw }
failure: { ruined: ruined, burnt: burnt }
shops:
  chinese:
    name: 中華
    processes:
      - { equip: pot_boil, in: [noodle_raw], out: noodle_boiled }
    dishes:
      ramen: { name: ラーメン, item: dish_ramen, parts: [noodle_boiled, meat_boiled_cut, veg_cut] }
```

工程の中身は、企画書 第2版の「食材・料理・レシピ」の表と、`textures.md` の §2〜§3 どおりにする。まな板の工程(`meat_raw→meat_cut`、`veg_raw→veg_cut` など)は、その店で必要なものだけを書く。

- [ ] **Step 1:** テストを書く:
  - `loadsBundledRecipes`: 同梱の recipes.yml を読み込み、3店、各店3品
  - `everyDishReachableFromBoxes`: 店ごとに、箱から出る生の食材から工程を繰り返し適用して、届く集合を作る。全料理の全部品がその集合に入る
  - `stewDependsOnShop`: french の `cook(POT_STEW, [meat_cut, veg_cut])` が `potofu_stewed`、japanese では `nikujaga_stewed`
  - `terrineAcceptsMeatOrFish`: french の `cook(POT_SET, [fish_cut, veg_cut])` と `[meat_cut, veg_cut]` が、どちらも `terrine_set`
  - `exactMatchOnly`: chinese の `cook(PAN, [veg_cut, meat_cut])`(米なし)が empty。順番を変えた `[rice_raw, meat_cut, veg_cut]` は `chahan_fried`
  - `wrongPotIsEmpty`: chinese の `cook(POT_BOIL, [veg_cut, meat_cut])` が empty
  - `unknownItemFails`: out に未定義の識別子を入れた Map → `RecipeException`。メッセージに、その識別子が含まれる
- [ ] **Step 2:** FAIL を確認
- [ ] **Step 3:** 実装し、recipes.yml を書く
- [ ] **Step 4:** PASS を確認
- [ ] **Step 5:** コミット

### Task 4: 設備の状態(調理器具・まな板・皿)

**Files:**
- Create: `core/cook/CookerState.java`, `CuttingBoardState.java`, `PlateState.java`
- Test: 各 `…Test.java`

**Interfaces:**
- Consumes: `Shop`, `Settings.CookTime`, `EquipType`
- Produces:
  - `CookerState(EquipType type)`: `enum Phase { EMPTY, COOKING, DONE, RUINED, BURNT }`
    - `boolean add(String item, int maxItems)`: DONE・RUINED・BURNT のとき、または満杯のときは false。追加したら COOKING にして、経過を0に戻す
    - `void tick(Shop shop, CookTime t, String ruinedId, String burntId)`: COOKING で cookTicks に達したら、`shop.cook` の結果があれば DONE(結果は `result()`)、なければ RUINED。DONE で burnTicks に達したら BURNT
    - `Optional<String> takeOut()`: DONE・RUINED・BURNT なら結果を返して EMPTY に。COOKING なら最後に入れた1つを返し、経過を0に戻す(空になったら EMPTY)
    - `double progress()`(0〜1)、`List<String> contents()`、`Phase phase()`、`void reset()`
  - `CuttingBoardState`: `boolean place(String item)`(空のときだけ)、`Optional<String> chop(Shop shop, int chopCount)`(切り終えたら、切ったあとの識別子を返して、その品に置き換える)、`Optional<String> take()`、`boolean isDone()`、`double progress()`、`reset()`。切れない食材は、`place` の前に `shop.accepts(CUTTING_BOARD, item)` で呼び出し側が弾く。切ったあとの品をもう一度切れる工程があれば(天ぷら)、`take` せずに続けて切れる
  - `PlateState`: `static final int MAX_PARTS = 6`。`boolean add(String part)`、`Optional<Dish> tryComplete(Shop shop)`(一致したら空にして料理を返す)、`Optional<String> undo()`(最後の1つ)、`List<String> parts()`、`reset()`

- [ ] **Step 1:** テストを書く:
  - `CookerStateTest`: `cooksThenBurns`(chinese の POT_BOIL に noodle_raw → 200 tick で DONE/noodle_boiled → さらに 200 tick で BURNT)、`wrongMixRuins`、`addResetsTimer`(150 tick 後に追加 → そこから 200 tick で完成)、`takeOutWhileCookingReturnsLast`、`noTickNoProgress`(add のあと tick を呼ばなければ progress は0のまま)、`cannotAddToDone`
  - `CuttingBoardStateTest`: `fiveChopsCut`(4回目までは empty、5回目で `meat_cut`)、`fishCutTwiceForTempura`(japanese: fish_raw を5回 → fish_cut、さらに5回 → fish_cut_fine)、`placeOnlyWhenEmpty`
  - `PlateStateTest`: `completesRamenInAnyOrder`、`undoReturnsLast`、`capsAtSix`(7個目の add が false)、`noMatchKeepsParts`
- [ ] **Step 2〜4:** FAIL → 実装 → PASS
- [ ] **Step 5:** コミット

### Task 5: 注文・納品・得点・順位

**Files:**
- Create: `core/order/Order.java`, `OrderGenerator.java`, `DeliverySlot.java`, `core/score/Scoring.java`, `Ranking.java`, `TeamResult.java`
- Test: `OrderGeneratorTest`, `DeliverySlotTest`, `ScoringTest`, `RankingTest`

**Interfaces:**
- Consumes: `Settings`, `Dish`, `TieBreak`
- Produces:
  - `record Order(int k, List<String> dishIds)`
  - `OrderGenerator(long seed, List<String> dishIds, Settings s)`: `Order orderAt(int k)`。乱数は `new Random(seed ^ (k * 0x9E3779B97F4A7C15L))`。個数は `tierFor(k)` の min〜max から一様に選び、料理は dishIds から一様に選ぶ(重複あり)
  - `DeliverySlot`: `Order order()`、`void add(String dishId)`、`Optional<String> undoLast()`、`List<String> delivered()`、`boolean isEmpty()`、`void next(Order o)`(中身を空にして注文を差し替える)
  - `record Evaluation(int score, int mistakes)`
  - `Scoring(Settings s)`: `int fullScore(int n)` = `round(pointsPerDish * n * multiplier(n))`。n が倍率の数を超えたら、最後の倍率を使う。`Evaluation evaluate(List<String> required, List<String> delivered)`: 間違い = Σ max(0, 必要−入れた) + Σ max(0, 入れた−必要)。得点 = `max(0, full − round(full * mistakes / (double) n))`。`int roundPenalty(int roundTotal, int mistakes)` = `roundTotal <= 0 ? 0 : floor(roundTotal * penaltyRate * mistakes)`
  - `record TeamResult(String teamId, int total, int lastRound, int mistakes)`
  - `Ranking.rank(List<TeamResult>, TieBreak) -> List<Ranked>`。`record Ranked(int place, TeamResult r)`。total の高い順。同点なら TieBreak で比べ、それでも同じ(または SHARE)なら同じ順位にする

- [ ] **Step 1:** テストを書く:
  - `OrderGeneratorTest`: `sameSeedSameOrder`(別インスタンスでも `orderAt(7)` が等しい)、`countsFollowTiers`(k=0〜5 は1〜2個、k=14〜100 は2〜4個)、`onlyShopDishes`
  - `ScoringTest`: `fullScores`(n=1→100、2→220、3→360、4→520)、`specExample`(必要 [ramen,ramen,chahan]、入れた [ramen,shumai] → mistakes 3、score 0)、`perfect`(mistakes 0、score=full)、`shortDeliveryCountsMissing`(必要 [ramen,ramen]、入れた [ramen] → mistakes 1、score 110)、`onlyWrongDishes`(必要 [ramen]、入れた [soba,soba] → mistakes 3、score 0)、`overDelivery`(必要 [ramen]、入れた [ramen,ramen] → mistakes 1、score 0)、`penalty`(roundPenalty(10000, 3) == 75、roundPenalty(-50, 3) == 0)
  - `RankingTest`: `lastRoundBreaksTie`、`fewerMistakesBreaksTie`、`shareGivesSamePlace`(1位,1位,3位)
  - `DeliverySlotTest`: `undoLast`、`nextClears`
- [ ] **Step 2〜4:** FAIL → 実装 → PASS
- [ ] **Step 5:** コミット

### Task 6: チーム分けと名簿

**Files:**
- Create: `core/team/TeamSplitter.java`, `TeamRoster.java`
- Test: `TeamSplitterTest`, `TeamRosterTest`

**Interfaces:**
- Produces:
  - `record Split(List<List<UUID>> teams, List<UUID> spectators)`
  - `TeamSplitter.split(List<UUID> players, Random rnd, int teamSize, int maxTeams) -> Split`: シャッフルして、`min(n / teamSize, maxTeams)` チームぶんを先頭から5人ずつ。残りは観戦
  - `TeamRoster`: チームID(`team-1`…)ごとに、名前、色(`NamedTextColor` ではなく文字列キー `red` など。core は Bukkit 非依存のため)、メンバー、キッチンID を持つ。`createTeam(String id, String name, String color)`、`addMember(String teamId, UUID)`(ほかのチームからは外す)、`removeMember(UUID)`、`Optional<String> teamOf(UUID)`、`void assignKitchen(String teamId, String kitchenId)`、`Optional<String> kitchenOf(String teamId)`、`void lock()` / `unlock()`(lock 中は add/remove が `IllegalStateException`)、`List<String> teamIds()`、`clear()`
  - 色の初期順: red, blue, green, yellow, purple, aqua, gold, light_purple。名前の初期値: 赤チーム…

- [ ] **Step 1:** テストを書く: `split23`(4チーム、観戦3)、`split47`(8チーム、観戦7)、`split4`(0チーム、観戦4)、`noDuplicates`、`rejoinKeepsTeam`(lock 後も `teamOf` は同じ値を返し続ける。名簿は、オンラインかどうかを持たない)、`lockedRejectsChanges`、`addMovesBetweenTeams`
- [ ] **Step 2〜4:** FAIL → 実装 → PASS
- [ ] **Step 5:** コミット

### Task 7: 独自アイテム、メッセージ、リソースパック送信

**Files:**
- Create: `paper/item/CustomItems.java`, `paper/Messages.java`, `paper/pack/ResourcePackSender.java`, `src/main/resources/messages.yml`, `src/main/resources/config.yml`
- Modify: `MinecraftCooksPlugin.java`(recipes.yml、settings.yml、messages.yml を読み込み、データフォルダにないものはコピーする)

**Interfaces:**
- Consumes: `RecipeBook.itemNames()`
- Produces:
  - `CustomItems(RecipeBook book)`: `ItemStack create(String id)`(数量1、名前は斜体なしの白、`item_model` と PDC を設定)、`Optional<String> idOf(ItemStack)`、`ItemStack wand(String kitchenId, String mode, int slot)`(PDC に `minecraft-cooks:wand` = `"<kitchen>|<mode>|<slot>"`)、`Optional<WandMode> wandModeOf(ItemStack)`
  - `Messages(YamlConfiguration)`: `Component get(String key, TagResolver... args)`。MiniMessage 形式
  - `ResourcePackSender(Plugin, String url, String sha1Hex, boolean required, String prompt)`: `start()` で、sha1 が空なら `HttpClient` で非同期にダウンロードして SHA-1 を計算する。`PlayerJoinEvent` で `player.addResourcePack(固定UUID, url, hash, prompt, required)` を呼ぶ。url が空なら何もしない
  - config.yml: `resource-pack: { url: "", sha1: "", required: true, prompt: "Minecraft Cooks のリソースパックです" }`

- [ ] **Step 1:** 実装する
- [ ] **Step 2:** `./gradlew build` が通る
- [ ] **Step 3:** コミット

(Bukkit の API に依存するため、自動テストはしない。Task 9 で、ゲーム内で確かめる)

### Task 8: キッチンの登録(レジストリ、ワンド、設備エンティティ)

**Files:**
- Create: `core/BlockPos.java`, `core/kitchen/KitchenLayout.java`, `paper/kitchen/KitchenRegistry.java`, `EquipmentEntities.java`, `WandListener.java`, `paper/command/CgCommand.java`(kitchen と wand のサブコマンドだけ)
- Test: `core/kitchen/KitchenLayoutTest.java`

**Interfaces:**
- Produces:
  - `record BlockPos(int x, int y, int z)`: `plus(BlockPos)`、`minus(BlockPos)`、`static BlockPos min(BlockPos a, BlockPos b)`(成分ごと)
  - `record EquipmentDef(EquipType type, int slot, BlockPos pos)`(slot は COUNTER 以外は0)
  - `KitchenLayout`: `static List<EquipmentDef> cloneShifted(List<EquipmentDef> src, BlockPos fromCorner1, BlockPos fromCorner2, BlockPos toCorner1, BlockPos toCorner2)`。ずれ = `min(to) − min(from)`
  - `Kitchen`(paper 側): `id`, `displayName`, `world`, `corner1`, `corner2`, `spawn`(Location), `List<EquipmentDef>`
  - `KitchenRegistry(File kitchensYml)`: `load()`、`save()`(変更のたびに呼ぶ)、`create`、`remove`、`Optional<Kitchen> get(String)`、`Collection<Kitchen> all()`、`Optional<Located> at(World, BlockPos)`(`record Located(Kitchen k, EquipmentDef def)`)、`addEquipment` / `removeEquipment`
  - `EquipmentEntities`: `spawnAll(Kitchen)`、`removeAll(Kitchen)`、`removeOrphans()`(起動時に、タグ `minecraft-cooks_equip` のついたエンティティを全部消してから作り直す)。1設備につき、バリアブロック、ItemDisplay(モデル `equip_<key>`。COUNTER は `equip_counter`)、Interaction(幅・高さ 1.01)、TextDisplay(上に1つ。進み具合と注文に使う)。Interaction の PDC に `"<kitchenId>|<x>,<y>,<z>"` を入れる。`Optional<Located> lookup(Entity interaction)`、`TextDisplay label(Kitchen, EquipmentDef)`、`ItemDisplay contentsDisplay(…)`
  - `WandListener`: ワンドで右クリックすると登録、左クリックすると解除。area モードは、左で corner1、右で corner2
  - コマンド: `/cg kitchen create <id> <表示名>`、`remove <id>`、`list`、`setspawn <id>`、`clone <from> <to>`、`/cg wand <kitchenId> <type|area> [slot]`

- [ ] **Step 1:** テストを書く: `KitchenLayoutTest.shiftsByMinCorner`(角が (0,64,0)-(10,70,10) → (100,64,0)-(110,70,10) のとき、(3,65,4) が (103,65,4) になる)、`cornerOrderIrrelevant`(角を逆に渡しても同じ結果)
- [ ] **Step 2〜4:** FAIL → 実装 → PASS
- [ ] **Step 5:** `./gradlew build` が通る
- [ ] **Step 6:** コミット

### Task 9: キッチンで遊べる(設備の操作、1つだけ持つ)

**Files:**
- Create: `paper/play/KitchenRuntime.java`, `InteractionListener.java`, `HandLockListener.java`
- Modify: `CgCommand.java`(`/cg practice <kitchenId>`: 自分ひとりをそのキッチンの練習プレイヤーにする。試合外の動作確認用で、そのまま残す)

**Interfaces:**
- Consumes: Task 3〜8 の全部
- Produces:
  - `KitchenRuntime(Kitchen, Shop, Settings, OrderGenerator)`: 設備ごとの状態(`Map<BlockPos, CookerState | CuttingBoardState | PlateState>`)と、`DeliverySlot[]`(Settings.orderSlots 個)、`int nextK`、`int roundScore`、`int roundMistakes` を持つ。`tick()`(全調理器具を進めて、ラベルを更新する)、`reset()`、`boolean active`(false のときはクリックにも tick にも反応しない)
  - `KitchenAccess` インターフェース: `Optional<KitchenRuntime> runtimeFor(Player)`。最初は practice 用の実装、Task 10 で MatchManager が実装する
  - `InteractionListener`: `PlayerInteractEntityEvent`(メインハンドだけ)で右クリック、`io.papermc.paper.event.player.PrePlayerAttackEntityEvent` で左クリック(イベントはキャンセルする)。設備の種類ごとに、企画書 第2版の「設備の一覧」「自動で調理する設備」「皿」「納品」のとおりに動かす。送信は、同じプレイヤーが同じ枠を `submitConfirmTicks` 以内に2回左クリックしたとき
  - `HandLockListener`: 試合中のプレイヤーについて、`InventoryClickEvent`、`InventoryDragEvent`、`PlayerSwapHandItemsEvent`、`PlayerItemHeldEvent` をキャンセルする(持つ場所は0番に固定)。`EntityPickupItemEvent` は、手がふさがっていればキャンセルする。`PlayerDropItemEvent` は許可する。ダメージ(`EntityDamageEvent`)と空腹(`FoodLevelChangeEvent`)をキャンセルする

- [ ] **Step 1:** 実装する
- [ ] **Step 2:** `./gradlew build` が通る
- [ ] **Step 3:** 手動確認(依頼者のテストサーバー)。`/cg practice kitchen-1` で中華を練習し、次を確かめる:
  - 箱 → まな板 → 茹で鍋 → 皿 → 納品場所でラーメンが完成し、2回の左クリックで送れる
  - 焦げ、失敗、ゴミ箱、皿の左クリックで1つ戻る、納品場所のスニーク右クリックで1つ戻る
  - 2つ目のアイテムを拾えない
  - 他のキッチンの設備に反応しない
  - `active=false` のとき、クリックしても反応せず、タイマーも進まない
- [ ] **Step 4:** コミット

### Task 10: 試合の進行と表示

**Files:**
- Create: `paper/match/MatchManager.java`, `Hud.java`
- Modify: `CgCommand.java`(match のサブコマンド)

**Interfaces:**
- Consumes: `TeamRoster`, `TeamSplitter`, `KitchenRuntime`, `Scoring`, `Ranking`, `KitchenAccess`
- Produces:
  - `MatchManager implements KitchenAccess`: `enum Mode { QUICK, NORMAL }`、`enum State { IDLE, COUNTDOWN, PLAYING, PAUSED, BREAK, FINISHED }`。`start(Mode, Optional<String> shopId)`、`pause()`、`resume()`、`stop()`、`redoRound()`
    - start の確認: チーム数 ≥ 1、キッチン数 ≥ チーム数、どのチームも空でない。5人でないチームがあれば警告を出す
    - ラウンドの開始: 種 `seed = System.nanoTime()` を全キッチンで共有する。インベントリと地面のアイテムを空にして、アドベンチャーモードでテレポートする。カウントダウン → PLAYING
    - ラウンドの終了: 各チームの `roundScore − roundPenalty` を記録し、地面のアイテムを消し、結果を表示する → BREAK → 次のラウンド、または FINISHED
    - NORMAL は chinese → french → japanese。QUICK は、指定がなければランダム
    - 試合中に抜けて戻ったら(`PlayerJoinEvent`)、同じキッチンへテレポートし、手を空にする
  - `Hud`: チームごとの BossBar(残り時間、`mm:ss`)、全員共通のサイドバー(店、ラウンド、順位)、納品場所ごとの TextDisplay(`注文 #k` と、料理ごとの `入れた数/必要数`)。毎秒更新する。順位が変わったときだけ、サイドバーを書き直す
  - コマンド: `/cg match start quick [shop]`、`start normal`、`pause`、`resume`、`stop`、`redo-round`

- [ ] **Step 1:** 実装する
- [ ] **Step 2:** `./gradlew build` が通る
- [ ] **Step 3:** 手動確認: 2チーム(1人ずつでもよい)でクイックマッチを最後まで。ノーマルマッチで店が3回替わる。一時停止中に調理が止まる。やり直しで得点が戻る。抜けて戻ったら同じキッチンに戻る
- [ ] **Step 4:** コミット

### Task 11: 運営コマンドの残りと観戦

**Files:**
- Modify: `CgCommand.java`(タブ補完も含める)
- Modify: `MatchManager.java`(得点の手動調整、観戦)

**Interfaces:**
- コマンド: `/cg team auto|add <team> <player>|remove <player>|move <player> <team>|name <team> <名前>|color <team> <色>|kitchen <team> <kitchenId>|list|clear`、`/cg exclude add|remove <player>`、`/cg score add <team> <点>`、`/cg kitchen reset <id>`、`/cg spectate [kitchenId]`、`/cg reload`
- `team auto` は、exclude 以外のオンラインの人で `TeamSplitter.split(…, 5, 8)` を行い、編成と観戦者をチャットに出す
- `spectate`: 権限があるか、観戦者か、試合が終わったチームのプレイヤーであれば、スペクテイターモードで、指定キッチンのスポーンへ行ける
- `reload` は、IDLE のときだけ行う。recipes.yml を読み込めなかったら、前の内容のまま、エラーを表示する

- [ ] **Step 1:** 実装する
- [ ] **Step 2:** `./gradlew build` が通る
- [ ] **Step 3:** 手動確認: auto で人数の端数が観戦になる。試合中の `team add` が拒否される。reload の失敗表示
- [ ] **Step 4:** コミット

### Task 12: 設定GUI

**Files:**
- Create: `paper/gui/SettingsGui.java`
- Modify: `CgCommand.java`(`/cg settings`)

**Interfaces:**
- 54枠のチェスト。項目1つにつき、アイテム1つ(名前に現在の値、説明に操作方法)。左クリックで+、右クリックで−。シフトを押しながらだと、変わる量が10倍になる。対象は、`Settings` の全項目(cookTimes は設備ごと、tiers は段階ごとに分ける。TieBreak はクリックで順番に切り替える)
- 変えるたびに、`settings.yml` に `Settings.toMap()` を保存する。試合中に変えた値は、次のラウンドから効く
- `InventoryHolder` を自作して、GUI かどうかを判別する

- [ ] **Step 1:** 実装する
- [ ] **Step 2:** `./gradlew build` が通る
- [ ] **Step 3:** 手動確認: 値を変える → サーバーを再起動 → 値が残っている
- [ ] **Step 4:** コミット

### Task 13: リソースパックの雛形、運営ガイド、最終確認

**Files:**
- Create: `resourcepack/pack.mcmeta`、`resourcepack/assets/minecraft-cooks/items/<id>.json`、`resourcepack/assets/minecraft-cooks/models/item/<id>.json`(textures.md の48個)、`docs/運営ガイド.md`

**Interfaces:**
- `items/<id>.json`: `{"model":{"type":"minecraft:model","model":"minecraft-cooks:item/<id>"}}`
- `models/item/<id>.json`: 食材・料理・ワンドは `{"parent":"minecraft:item/generated","textures":{"layer0":"minecraft-cooks:item/<id>"}}`、設備は `{"parent":"minecraft:block/cube_all","textures":{"all":"minecraft-cooks:item/<id>"}}`
- `pack.mcmeta` の pack_format は、1.21.11 の値を確かめてから書く
- 運営ガイド: キッチンの建築から登録、clone、リソースパックの公開、チーム分け、試合の進行までを、手順の形で書く

- [ ] **Step 1:** 雛形を作る(textures.md の識別子の一覧から、スクリプトで生成する)
- [ ] **Step 2:** 運営ガイドを書く
- [ ] **Step 3:** `./gradlew build` で、テストがすべて PASS し、jar ができる
- [ ] **Step 4:** コミット
