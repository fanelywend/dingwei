# 定位模拟器（Dingwei GPS Mock）

一个**免 root** 的安卓 GPS 定位模拟 App。核心用法：**输入城市名称即可把手机定位改到那里**。

基于 Android 官方的「测试 provider」机制（`LocationManager.addTestProvider` + `setTestProviderLocation`），
不修改系统、不需要 Xposed，只需在开发者选项里把本应用选为「模拟位置信息应用」。

---

## 一、功能

| 功能 | 说明 |
|---|---|
| **城市名搜索定位** | 内置中国省市离线库，支持中文名 / 全拼 / 首字母（`北京`、`beijing`、`bj`、`sh` 都能搜到）；库内没有的地点自动走在线搜索（OpenStreetMap）兜底 |
| **地图选点** | 内置 OSM 地图（osmdroid，免 API Key），轻点或长按地图即可取点 |
| **手动输入坐标** | 支持按 WGS-84 / GCJ-02 / BD-09 三种坐标系解释输入 |
| **坐标系转换** | 同一位置的三套坐标实时互转、一键复制（国内 App 必须，见下文「坐标系」） |
| **参数调节** | 海拔、定位精度、速度、航向可调 |
| **收藏地点** | 常用位置一键切换 |
| **后台持续生效** | 前台服务每秒重写一次位置，切到其他 App、锁屏后依然有效；通知栏可一键停止 |

---

## 二、安装与使用

### 1. 安装

构建产物：

| 文件 | 大小 | 说明 |
|---|---|---|
| `app/build/outputs/apk/release/app-release.apk` | 11 MB | **推荐安装这个**，已用项目密钥签名 |
| `app/build/outputs/apk/debug/app-debug.apk` | 16 MB | 调试版，含调试符号 |

```bash
# 安装（二选一）
adb install -r app/build/outputs/apk/release/app-release.apk
# 或把 APK 传到手机后点击安装（需允许「安装未知来源应用」）
```

> release 签名使用项目内 `dingwei-release.jks` + `keystore.properties`（自用开发密钥，
> 口令见 `keystore.properties`）。换机重新构建时请保留该密钥，否则无法覆盖安装。
> 若删除 `keystore.properties`，构建仍会成功，但产出未签名包。

### 2. 授权（首次必做，两步）

1. **开启开发者模式**：设置 → 关于手机 → 连点「版本号」7 次。
2. **选中本应用**：设置 → 系统 → 开发者选项 → **选择模拟位置信息应用** → 选中「定位模拟器」。

> App 首页顶部会显示当前授权状态，未授权时点「打开开发者选项」可直接跳转。
> 授权后回到 App 会自动重新检测。

### 3. 使用

1. 在搜索框输入城市名（如 `深圳`），点选结果 —— 坐标立即生效；
2. 或直接在地图上点选位置、手动输入经纬度、使用收藏；
3. 点底部「开始模拟定位」；
4. 切到其他 App（微信、高德、钉钉…）查看定位。

停止：点底部「停止模拟」或通知栏的「停止」。

---

## 三、坐标系（重要）

中国大陆存在三套坐标系，混用会导致定位偏移 **300~700 米**：

| 坐标系 | 使用者 | 偏移 |
|---|---|---|
| **WGS-84** | GPS 硬件、OSM 地图、Android 系统定位框架 | 无（真实坐标） |
| **GCJ-02** | 高德、腾讯、微信、滴滴等几乎所有国内 App | 国测局加密偏移 |
| **BD-09** | 百度地图 | GCJ-02 基础上再偏移 |

**本 App 的做法**：内部一律以 **WGS-84** 存储和推送。
因为 Android 定位框架给 App 的就是 WGS-84，国内 App 自己会转成 GCJ-02 显示。
如果推送前先转成 GCJ-02，国内 App 会**二次偏移**，反而偏得更远。

界面上的三套坐标只是同一位置的三种表示，可随时复制：
- 想在高德/微信里验证？用 **GCJ-02** 那行的数值去对；
- 想给 OSM/Google 地图用？用 **WGS-84** 那行。

---

## 四、本机构建

镜像与 JDK 都已配好，直接跑构建脚本即可：

```bash
cd /home/orlando/dingwei

./build.sh :app:assembleDebug      # 构建 debug APK
./build.sh :app:assembleRelease    # 构建 release APK（自动签名）
./build.sh :app:testDebugUnitTest  # 跑单元测试
./build.sh :app:lintDebug          # 静态检查
./build.sh clean                   # 清理
```

### 验证情况

开发机上**无法做真机/模拟器运行验证**（该机器无 KVM、无硬件虚拟化、无系统镜像），
因此功能验证分为三层：

| 层次 | 手段 | 覆盖内容 |
|---|---|---|
| 单元测试（16 个用例，全通过） | `./build.sh :app:testDebugUnitTest` | 坐标转换数学（对照 eviltransform 公开参考向量）、往返一致性、偏移量级；**用真实 cities.json 验证**中文/全拼/首字母/多音字搜索与排序 |
| 数据校验 | `python3 .tools/verify_cities.py` | cities.json 是否为 WGS-84（抽样 10 城，1~7 米内通过） |
| 构建产物校验 | `aapt2 dump badging` / `apksigner verify` | 包名、minSdk/targetSdk、权限、签名、assets 是否打包 |

**真机自测清单**（需你在手机上确认，我无法代做）：

1. 安装后打开，首页顶部应显示橙红色「尚未授权模拟位置权限」；
2. 按提示进入开发者选项选中本应用，返回首页应变为绿色「已授权」；
3. 搜索「深圳」并点选结果，坐标卡应显示深圳的三套坐标；
4. 点「开始模拟定位」，通知栏出现「正在模拟定位」；
5. 打开高德/微信发送位置，看是否显示在深圳（若偏移几百米，见下文坐标系说明）；
6. 点「停止模拟」，定位应恢复真实位置；
7. 锁屏或切到其他 App 数分钟，回来确认定位仍被模拟（验证前台服务生效）。

### 用 GitHub Actions 构建（推荐，不占本机资源）

本机构建一次约需 5 分钟、峰值占用 3 GB 堆内存。CI 构建跑在 GitHub 的 runner 上
（4 核 16 GB），不消耗本机资源。

**已配置好** [.github/workflows/android.yml](</home/orlando/dingwei/.github/workflows/android.yml>)：
推送代码或手动触发后，CI 会依次跑单元测试 → lint → 构建 debug/release APK，
并把 APK 与测试报告作为 artifact 上传。

> CI 里通过 `GRADLE_USE_MIRRORS=false` 改用官方 Maven 源（runner 在海外，
> 比阿里云镜像更快）；本机默认仍走阿里云镜像，见 [settings.gradle.kts](</home/orlando/dingwei/settings.gradle.kts>)。

**使用步骤**

1. 在 GitHub 上创建一个**空仓库**（不要勾选 README / .gitignore）：
   <https://github.com/new>
2. 关联并推送：

   ```bash
   cd /home/orlando/dingwei
   git remote add origin git@github.com:<你的用户名>/dingwei.git
   git push -u origin main
   ```

3. 打开仓库的 **Actions** 页，等构建变绿；
4. 进入该次运行，页面底部 **Artifacts** 下载：
   - `dingwei-apk` —— 里面是 debug + release 两个 APK
   - `reports` —— 单元测试与 lint 报告
5. 也可在 Actions 页点 **Run workflow** 手动触发构建。

> **不开网页也能确认 CI 状态**：CI 会自己把结果写回仓库，命令行即可查（无需 token）：
>
> ```bash
> # 成功：ci-ok 指向构建成功的那次提交（失败时不会移动）
> git fetch --tags -f && git log -1 --oneline ci-ok
>
> # 失败：ci-logs 分支里有失败报告（含各步骤结果与日志尾部）
> git fetch origin ci-logs -f && git show origin/ci-logs:ci-logs/last-failure.md
> ```
>
> `last-failure.md` 会列出**每一步的 outcome**，能直接看出是哪一步挂了。

### 修改本项目时的两个坑（都踩过）

1. **不要把机器相关路径写进仓库里的配置文件**。
   `gradle.properties` 会提交到仓库，曾因写入 `org.gradle.java.home=/home/orlando/jdk17`
   导致 CI 上 Gradle 直接启动失败（runner 无此路径）。机器相关配置请放：
   - JDK → 用户级 `~/.gradle/gradle.properties` 的 `org.gradle.java.home`
   - Android SDK → `local.properties` 的 `sdk.dir`（已 gitignore）
2. **本机 `./gradlew` 不能用**：wrapper launcher 需要 `JAVA_HOME`，且其
   `distributionUrl` 指向 `services.gradle.org`（本机不可达）。本机构建请用 `./build.sh`
   （走项目内 `.tools/gradle-8.9`）。CI 上则正常使用 `./gradlew`。

**让 CI 产出「已签名」的 release APK（可选，但建议）**

仓库未配置密钥时，CI 产出的 release APK 是**未签名**的（无法直接安装，debug APK 仍可装）。
要产出已签名包，在仓库 **Settings → Secrets and variables → Actions** 添加 4 个 Secret：

| Secret 名称 | 值 |
|---|---|
| `KEYSTORE_BASE64` | 见下方命令输出 |
| `KEYSTORE_PASSWORD` | `dingwei2026` |
| `KEY_ALIAS` | `dingwei` |
| `KEY_PASSWORD` | `dingwei2026` |

生成 `KEYSTORE_BASE64` 的值：

```bash
base64 -w0 /home/orlando/dingwei/dingwei-release.jks
```

> 签名密钥（`dingwei-release.jks` 与 `keystore.properties`）**不会提交到仓库**
> （已列入 `.gitignore`），只以 Secret 形式注入 CI，避免密钥泄露。

### 环境说明

| 组件 | 位置 / 版本 | 备注 |
|---|---|---|
| JDK | `/home/orlando/jdk17`（Temurin 17.0.12） | 由 `build.sh` 与 `gradle.properties` 指定 |
| Android SDK | `/home/orlando/android` | 见 `local.properties`；platforms 33/34/35，build-tools 34.0.0 |
| Gradle | `.tools/gradle-8.9`（项目内） | 官方源在本机不可达，从腾讯镜像下载 |
| AGP / Kotlin | 8.6.1 / 1.9.24 | compileSdk 34、minSdk 24、targetSdk 34 |

**依赖镜像**：本机实测 Gradle 官方源不可达（0 KB/s）、Maven Central 仅 ~74 KB/s，
因此 `settings.gradle.kts` 里把**阿里云镜像放在最前**，官方源仅作兜底。

> Windows/macOS 上构建只需装好 JDK 17 与 Android SDK，用 Android Studio 打开项目即可，
> 但请把 `gradle.properties` 里的 `org.gradle.java.home` 改成本机 JDK 路径。

---

## 五、已知限制

### 5.1 非 root 方案的能力边界（实测 + AOSP 源码确认）

用 App 内置的「**自检 / 诊断**」卡片可以确认注入是否真的生效。实测报告（Android 12 / API 31）：

```
[gps]     已注册/启用 isMock=true 读回=30.575400, 104.063800 偏差=0m 年龄=689ms
[network] 已注册/启用 isMock=true 读回=30.575400, 104.063800 偏差=0m 年龄=690ms
[fused]   已注册/启用 isMock=true 读回=30.575400, 104.063800 偏差=0m 年龄=690ms
```

**系统确实把模拟坐标提供给了所有读取定位的 App**（连 `fused` 融合定位都是，偏差 0 米、
延迟不到 1 秒）。因此「其他 App 仍显示真实位置」**不是注入失败**。

根因是那个 `isMock=true`：**系统强制给每个模拟位置打上该标记，App 无法去除** ——
AOSP `MockLocationProvider.setProviderLocation()` 的实现：

```java
public void setProviderLocation(Location l) {
    Location location = new Location(l);
    location.setIsFromMockProvider(true);   // 系统强制；App 传什么都会被覆盖
    mLocation = location;
    reportLocation(LocationResult.wrap(location).validate());
}
```

于是结果分两类：

| 目标 App 的行为 | 结果 |
|---|---|
| 不检查该标记（多数地图、GPS 测试工具） | ✅ 正常显示模拟位置 |
| 主动检测并拒绝模拟位置（部分社交/打卡/金融/风控类） | ❌ 丢弃该位置，改用自带的 WiFi/基站定位或服务端定位 → 显示真实位置 |

**这是非 root 方案的能力边界，不是本 App 的缺陷。** 若必须去掉该标记，只有：

- root + LSPosed/Xposed 模块（hook 掉 `Location.isMock()` / `isFromMockProvider()`）；
- 把 App 装进 `/system/priv-app`（系统级安装）；
- 直接改 GNSS HAL。

### 5.2 其他

1. **部分 App 可能仍显示真实定位**：另有部分 App 根本不用系统定位（自带 WiFi/基站定位
   或服务端定位），这类 App 也无法通过系统级模拟影响。
   本 App 已同时模拟 `gps` / `network` / `fused` 三个 provider
   （Android 12 实测三个都能成功接管，`fused` 不再被 Google 服务独占）。
2. **OSM 地图在国内加载较慢**：瓦片来自 `tile.openstreetmap.org`，网络不畅时地图会空白，
   但不影响搜索定位与手动输入坐标。
3. **在线搜索是公共服务**：`photon.komoot.io` 为免费服务，请勿高频调用。
4. **请遵守各 App 的服务条款与当地法律**：模拟定位用于测试、隐私保护等正当用途；
   用于考勤作弊、游戏作弊等可能违反平台规则或法律，后果自负。

---

## 六、项目结构

```
dingwei/
├── build.sh                       构建入口（封装 JDK/Gradle 环境）
├── settings.gradle.kts            仓库镜像配置
├── gradle/libs.versions.toml      依赖版本目录
├── keystore.properties            release 签名口令（配 .jks 使用）
├── dingwei-release.jks            release 签名密钥
├── tools/citydb/                  离线城市库生成管线（见其中 README）
├── .tools/gradle-8.9/             项目内 Gradle（gitignore）
└── app/src/
    ├── main/
    │   ├── AndroidManifest.xml    权限、Activity、前台服务声明
    │   ├── assets/cities.json     离线中国城市库（428 条，WGS-84）
    │   └── java/com/dingwei/gpsmock/
    │       ├── MainActivity.kt
    │       ├── DingweiApp.kt      osmdroid 全局配置
    │       ├── data/              城市库、在线地理编码、收藏持久化
    │       │   ├── CityMatcher.kt     搜索匹配打分（纯函数，可测试）
    │       │   ├── CityRepository.kt  离线城市库加载与搜索
    │       │   ├── OnlineGeocoder.kt  Photon 在线兜底
    │       │   └── PrefsStore.kt
    │       ├── geo/
    │       │   └── CoordinateConverter.kt   WGS84 / GCJ02 / BD09 互转
    │       ├── location/
    │       │   ├── MockLocationEngine.kt    测试 provider 注册与位置写入
    │       │   ├── MockLocationService.kt   前台服务 + 1Hz 重写
    │       │   └── MockState.kt             进程内状态
    │       └── ui/
    │           ├── MainScreen.kt      主界面（状态/坐标/参数/收藏卡）
    │           ├── CitySearchCard.kt  城市搜索卡
    │           ├── MapCard.kt         osmdroid 地图卡
    │           ├── MockViewModel.kt   状态与业务动作
    │           └── Permissions.kt     权限与设置跳转
    └── test/java/com/dingwei/gpsmock/
        ├── geo/CoordinateConverterTest.kt   坐标数学（对照公开参考向量）
        └── data/CityMatcherTest.kt          用真实城市库验证搜索链路
```

---

## 七、数据来源

- **离线城市库**：中国行政区划名称（省 / 地级市）与坐标，**坐标已统一转换为 WGS-84**；
  拼音与首字母由构建脚本生成。
- **地图瓦片**：OpenStreetMap（经由 osmdroid，遵循 OSM 瓦片使用政策，已设置 User-Agent）。
- **在线搜索**：Photon（`photon.komoot.io`，基于 OSM 数据，免 Key）。

### ⚠️ 关于城市坐标的一个重要坑（已处理）

原始数据集 [AreaCity-JsSpider-StatsGov](https://github.com/xiangyuecn/AreaCity-JsSpider-StatsGov)
的 `ok_geo.csv` 坐标是**高德地图的 GCJ-02 火星坐标**（其 README 明确说明），
而本 App 内部统一使用 WGS-84。若直接把 GCJ-02 当 WGS-84 用，会造成 **265~621 米**的系统性偏移。

**验证方式**（不是靠文档自述，而是拿客观地理数据对比）：
取数据集里北京市、澳门特别行政区的**行政边界外框**极值，与 OSM 的同一边界对比，
实测偏移向量为：

| 对象 | 实测偏移 (lon, lat) | GCJ-02 理论偏移 | 结论 |
|---|---|---|---|
| 北京市（西/北边界） | +0.0066, +0.0016 | +0.0062, +0.0014 | 吻合 |
| 澳门（西/北边界） | +0.0051, −0.0030 | +0.0050, −0.0030 | 吻合到约 10 米 |

因此确认数据集为 GCJ-02，构建时**统一用标准算法反解为 WGS-84**后再写入 `cities.json`
（反解用迭代逼近，3~8 轮收敛到 1e-9 度）。

校验脚本：`.tools/verify_cities.py`，可独立判定 `cities.json` 是否为 WGS-84：

```bash
python3 .tools/verify_cities.py          # 基础校验 + 抽样对比
python3 .tools/verify_cities.py --osm    # 额外用 OSM 做第三方交叉验证
```

