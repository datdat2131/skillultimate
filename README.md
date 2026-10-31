# AstraXUltimate – Hệ thống Skill RPG cao cấp

Plugin **Paper 1.21.x** · **Java 21** · Maven · layout phẳng (mọi file ở thư mục gốc, 1 package `me.astrax.ultimate`) để upload thẳng lên GitHub. Không cần dependency ngoài.

Mục tiêu: mỗi skill là một **ultimate** thực thụ – có animation theo giai đoạn, particle nhiều lớp, âm thanh điện ảnh, chiến đấu thật, và được **tối ưu hiệu năng**.

## 5 skill mẫu (+ 1 dạng tiến hóa)

| Skill | Cơ chế nổi bật |
|---|---|
| ☄ **METEOR FALL** | Vòng ma pháp khổng lồ trên trời → thiên thạch rơi xiên có đuôi lửa/khói → nổ bán kính 12, shockwave, hố nổ giả bằng particle, vùng lửa cháy 5 giây |
| ⚡ **THUNDER JUDGEMENT** | Chỉ tay vào mục tiêu, vòng ma pháp dưới chân, sét giáng + flash, **chain lightning** nhảy sang kẻ địch gần đó, choáng + câm lặng. Tiến hóa thành ⚡ **DIVINE THUNDER** (`/ult evolve`) |
| 🌌 **VOID BREAKER** | Quả cầu hư không + vành xoáy hút mảnh năng lượng; kẻ trong vùng bị **Void Mark**, nổ gây thêm sát thương cho mục tiêu có Mark, Darkness, Slow |
| 🐉 **DRAGON DESCENT** | Bay vút lên lơ lửng, **rồng dựng bằng particle** (cánh vỗ, thân uốn lượn) sau lưng → lao xuống → chạm đất: shockwave + con rồng lửa lao ra theo hướng nhìn quét sát thương |
| 💀 **DEATH DOMAIN** | Lãnh địa bán kính 15: mái vòm tối, kẻ địch bị chậm/Darkness, chủ nhân tăng sức mạnh; hết giờ **sụp đổ** hút mọi thứ vào tâm rồi nổ |

## 4 giai đoạn của mỗi skill

```
CAST   → hạt quanh người, vòng ma pháp dưới chân, âm thanh kích hoạt, ActionBar tên skill
CHARGE → hạt hút vào, vòng năng lượng xoay quanh, âm thanh tăng dần (pitch tăng), khóa di chuyển (tùy skill)
IMPACT → nổ nhiều lớp: flash → shockwave → bolt/beam → debris → smoke → crater giả, sát thương, knockback, rung camera
AFTER  → hiệu ứng còn lại vài giây, gây sát thương/hiệu ứng theo thời gian (pulse), particle tan dần (fade)
```

Toàn bộ chạy bằng **một task 1-tick duy nhất** cho mỗi lần thi triển, tự hủy khi xong/bị gián đoạn/quá `max-run-ticks`.

## Cấu trúc project (flat)

```
AstraXUltimate-flat/
├─ pom.xml
├─ plugin.yml  config.yml  skills.yml  messages.yml
├─ AstraXUltimate.java      # Main
├─ ConfigManager.java       # nạp YAML, tin nhắn
├─ QualityManager.java      # effects-quality, ngân sách particle, tự giảm khi đông người
├─ ParticleSpec.java  SoundSpec.java  EffectSpec.java   # particle/âm thanh/potion cấu hình bằng YAML
├─ VisualLayer.java         # 15 loại layer hiệu ứng
├─ PhaseDef.java  SkillDef.java  CombatProfile.java      # mô hình dữ liệu skill
├─ SkillRun.java            # timeline Cast→Charge→Impact→After (lớp cơ sở)
├─ MeteorFallRun / ThunderJudgementRun / VoidBreakerRun / DragonDescentRun / DeathDomainRun .java
├─ DragonModel.java         # con rồng bằng particle
├─ CombatEngine.java        # sát thương, crit, true damage, xuyên giáp, knockback, trạng thái...
├─ StatusManager.java       # stun, silence, freeze, mark
├─ SkillManager.java        # đăng ký, thi triển, gắn vật phẩm, tiến hóa
├─ CooldownManager.java     # BossBar/ActionBar hồi chiêu
├─ PlayerDataStore.java     # lưu dạng tiến hóa (players.yml)
├─ TargetUtil.java          # ngắm mục tiêu bằng ray trace
├─ CommandManager.java  UltimateListener.java
```

## Build ra file .jar

Yêu cầu **JDK 21** + **Maven 3.8+**:

```bash
mvn clean package
```

Jar: `target/AstraXUltimate-1.0.0.jar`. Dependency duy nhất: `paper-api` (scope `provided`).
GitHub Actions: dùng workflow `Build` như đã hướng dẫn trước (JDK 21 + `mvn --batch-mode clean package`).

## Cài đặt & sử dụng

1. Bỏ jar vào `plugins/`, khởi động lại server (Paper 1.21.x, Java 21).
2. Skill mặc định chỉ dành cho OP. Cấp quyền cho người chơi: `astraxultimate.skill.<id>` (ví dụ `astraxultimate.skill.meteor-fall`).
3. Thi triển bằng **vật phẩm**: cầm một vật phẩm rồi `/ult bind meteor-fall` – chuột phải để dùng. Admin phát gậy: `/ult wand meteor-fall [người]`. Hoặc `/ult cast <skill>`.
4. Ngắm vào mặt đất/kẻ địch (tùy skill) trước khi thi triển.

## Lệnh & quyền

| Lệnh | Mô tả | Quyền |
|---|---|---|
| `/ult list` | Danh sách skill + trạng thái hồi chiêu | `astraxultimate.use` |
| `/ult info <skill>` | Thông tin skill | `astraxultimate.use` |
| `/ult cast <skill>` | Thi triển skill (đang ngắm) | quyền skill |
| `/ult bind <skill>` · `/ult unbind` | Gắn/gỡ skill trên vật phẩm đang cầm | quyền skill |
| `/ult evolve <skill>` | Tiến hóa (tốn cấp EXP theo `evolve-cost`) | `astraxultimate.use` |
| `/ult cancel` | Hủy skill đang thi triển | `astraxultimate.use` |
| `/ult reload` · `/ult quality <mức>` | Nạp lại cấu hình / đổi chất lượng hiệu ứng | `astraxultimate.admin` |
| `/ult wand <skill> [người]` · `/ult resetcd [người]` · `/ult devolve <người> <skill>` | Quản trị | `astraxultimate.admin` |
| `/ult cast <skill> force` | Thi triển bỏ qua quyền/cooldown | `astraxultimate.admin` |

Quyền: `astraxultimate.use` (mặc định true), `astraxultimate.admin` (op), `astraxultimate.bypass` (op – bỏ cooldown & quyền skill), `astraxultimate.skill.<id>` (mặc định op).

## Hệ thống chiến đấu (mục `combat` của giai đoạn impact/pulse)

| Khóa | Tác dụng |
|---|---|
| `damage`, `falloff` | Sát thương gốc; giảm dần về rìa bán kính. Nhân với `combat.damage-multiplier` (config.yml) và `power` |
| `crit: {chance, multiplier}` | Chí mạng |
| `true-damage` | Tỉ lệ sát thương **bỏ qua giáp** (trừ thẳng máu) |
| `armor-penetration` | Tỉ lệ **giáp bị bỏ qua** cho phần sát thương thường |
| `knockback: {strength, lift, mode}` | `RADIAL` (đẩy ra) · `PULL` (kéo vào tâm) · `DIRECTIONAL` (theo hướng skill); giảm theo knockback resistance của mục tiêu |
| `burn`, `freeze`, `stun`, `silence`, `darkness` | Tick. Stun = bất động (người chơi bị khóa vị trí, mob tắt AI); Silence = không thể thi triển skill; Freeze = đóng băng + chậm |
| `slow: {duration, amplifier}` | Làm chậm |
| `mark: {id, duration}` · `consume-mark: {id, bonus}` | Đánh dấu mục tiêu / gây thêm `bonus` sát thương nếu có Mark rồi gỡ Mark |
| `execute: {threshold, multiplier}` | Máu ≤ ngưỡng thì nhân sát thương |
| `shield-break` | Khóa khiên của người chơi đang đỡ (tick) |
| `effects` | Danh sách potion tùy ý |
| `max-targets`, `hit-players`, `hit-mobs`, `hit-passive` | Giới hạn và đối tượng bị ảnh hưởng (mặc định: chỉ mob hostile và người chơi ở thế giới có PvP) |

Skill **không bao giờ phá block**: chỉ dùng particle, âm thanh và sát thương.

## Hiệu ứng hình ảnh

Mỗi giai đoạn có `layers` (nhiều lớp chạy song song). Ví dụ:

```yaml
shock1: {type: SHOCKWAVE, at: TARGET, radius: 12, rings: 3, duration: 12, delay: 0,
         particle: DUST, color: '#FFB060', size: 1.6, density: 1.0, interval: 1, fade: false,
         accent: {particle: FLAME}}
```

| Loại | Hình dạng |
|---|---|
| `CIRCLE` | Vòng ma pháp: vòng tròn đồng tâm + ngôi sao (`sides`) + ký tự xoay |
| `AURA`, `SPIRAL`, `ORBIT` | Hào quang bay lên, xoắn ốc, vòng năng lượng xoay quanh |
| `CONVERGE` | Mảnh năng lượng bay hút vào tâm (`swirl` xoáy, `flat` dẹt như đĩa xoáy) |
| `SHOCKWAVE`, `SPHERE`, `DOME` | Sóng xung kích, cầu nổ, mái vòm/cầu bao phủ |
| `BEAM`, `LIGHTNING`, `LINE` | Cột sáng, tia sét zigzag có nhánh, tia nối tay → mục tiêu |
| `DEBRIS`, `CRATER`, `SMOKE`, `FLASH` | Mảnh vỡ bay (particle block), hố nổ giả có vết nứt, cột khói, chớp sáng trắng |

Chung: `radius`, `height`, `count`, `density`, `speed`, `duration`, `delay`, `interval`, `rotate`, `grow`, `fade` (tan dần), `at` (`CASTER|TARGET|SKY|HAND`), `trigger` (ví dụ `LAND`). Particle hỗ trợ dữ liệu: `DUST` (`color`, `size`), `DUST_COLOR_TRANSITION` (`color`, `to-color`), `BLOCK`/`FALLING_DUST` (`block`), `ITEM` (`item`).

**Camera/visual:** rung màn hình (`shake`, mô phỏng bằng xoay nhẹ góc nhìn), flash trắng bằng particle `FLASH`, làm tối bằng hiệu ứng `Darkness`. Số sát thương nổi trên đầu mục tiêu (TextDisplay) và ActionBar phản hồi cho người dùng skill.

## Âm thanh theo giai đoạn

```yaml
sounds:
  rise:  {key: block.beacon.ambient, volume: 1.5, pitch: 0.5, pitch-end: 2.0, interval: 4}  # tăng dần
  boom:  {key: entity.generic.explode, volume: 4.0, pitch: 0.5, delay: 2}                    # một lần, trễ 2 tick
  land:  {key: entity.lightning_bolt.thunder, trigger: LAND}                                 # chờ trigger
```

`interval` > 0: lặp lại suốt giai đoạn (pitch chạy từ `pitch` đến `pitch-end`). `volume` lớn = nghe xa hơn.

## Hồi chiêu

BossBar thanh tiến độ của skill vừa dùng, ActionBar `⏳ 12.3s [████░░░░]` khi thử dùng lúc đang hồi, thông báo + âm thanh khi sẵn sàng (`cooldown.*` trong config.yml). Cooldown giữ nguyên khi thoát/vào lại. Nếu skill bị hủy trước Impact, cooldown còn tối đa `cooldown.cancel-penalty-seconds`.

## Tối ưu hiệu năng

```yaml
effects-quality: HIGH     # LOW | MEDIUM | HIGH | ULTRA  (đổi nhanh: /ult quality ULTRA)
```

- Mỗi mức có `density` (nhân mật độ mọi layer), `budget` (số lệnh spawn particle tối đa **mỗi tick toàn server**), `layer-interval` (vẽ thưa tick) và bật/tắt `camera-shake`. Hết ngân sách → bỏ bớt particle, không bao giờ vượt.
- `auto-scale`: server đông hơn `players-threshold` thì tự giảm mật độ/ngân sách (tối thiểu `min-factor`).
- `limits`: `max-concurrent-runs`, `max-targets-per-hit`, `max-run-ticks`, `max-damage-indicators`.
- Không có vòng lặp vô hạn; mọi task tự hủy; entity hiển thị (số sát thương) không lưu xuống đĩa và được dọn khi tắt plugin; trạng thái (stun/mark...) có hạn và dọn khi thoát/chết; mob bị stun được khôi phục AI khi tắt plugin.

## Kế thừa & tiến hóa

Skill con kế thừa skill cha và chỉ ghi đè những khóa khác:

```yaml
divine-thunder:
  extends: thunder-judgement
  evolution: none                # chặn kế thừa khóa evolution của skill cha
  impact: {radius: 9, combat: {damage: 120}}
```

Skill cha khai báo `evolution: divine-thunder` và `evolve-cost: 30` (cấp EXP). Người chơi dùng `/ult evolve thunder-judgement`; từ đó thi triển `thunder-judgement` sẽ chạy dạng DIVINE THUNDER (lưu trong `players.yml`).

## API cho plugin khác

```java
AstraXUltimate ult = (AstraXUltimate) Bukkit.getPluginManager().getPlugin("AstraXUltimate");
ult.skills().cast(player, ult.skills().get("meteor-fall"), false, 1.5);   // power = 1.5
```

`power` nhân sát thương (ví dụ nhân theo level AstraXRace). Lưu ý: sát thương skill gây qua `Entity#damage(amount, player)` nên các plugin khác lắng nghe sự kiện sát thương (kể cả AstraXRace) vẫn xử lý như đòn của người chơi.

## Thêm skill mới

1. **Chỉ bằng YAML:** thêm một skill vào `skills.yml` với `type` có sẵn (hoặc `extends` skill khác).
2. **Cơ chế riêng:** tạo class kế thừa `SkillRun` (ghi đè `onPhaseStart/onPhaseTick/onImpact/...`), thêm vào `TYPES` và `createRun` trong `SkillManager`.

## Giới hạn đã biết

- Rung camera mô phỏng bằng xoay nhẹ góc nhìn nên có thể hơi giật với người chơi; tắt bằng `quality.<mức>.camera-shake: false`.
- Flash toàn màn hình thật cần resource pack; plugin dùng particle `FLASH`/vụ nổ trắng thay thế.
- Hố nổ (crater) chỉ là particle, không đổi block.
