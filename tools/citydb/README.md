# 离线城市库生成管线

`app/src/main/assets/cities.json` 是**生成物**，本目录保留生成脚本以便复现与更新。

## 数据流

```
AreaCity ok_geo.csv (GCJ-02 火星坐标)
        │  transform.mjs: gcj02ToWgs84()   ← 关键：必须转换，否则偏 300~700 米
        ▼
   省级 + 地级市记录 (WGS-84)
        │  build.mjs: 补拼音/首字母（pinyin-pro），台湾 20 个单位改用 Photon/OSM
        ▼
   app/src/main/assets/cities.json  (428 条，约 53 KB)
```

## 脚本

| 文件 | 作用 |
|---|---|
| `transform.mjs` | WGS-84 ⇄ GCJ-02 转换（Krasovsky 椭球，迭代反解） |
| `build.mjs` | 主生成脚本：读 CSV → 转换 → 生成拼音 → 写 cities.json |
| `fetch_taiwan.mjs` | ok_geo 中台湾城市无坐标，从 Photon/OSM 抓 WGS-84 坐标 |
| `verify.mjs` | 生成结果自检（坐标范围、拼音完整性、抽样比对） |

## 重新生成步骤

```bash
mkdir -p /tmp/citydb && cd /tmp/citydb

# 1. 取数据源（注意：ok_geo.csv 在 release assets 里，不在仓库根目录）
curl -fL -o ok_geo.csv.7z \
  https://github.com/xiangyuecn/AreaCity-JsSpider-StatsGov/releases/download/2025.251231.260403/ok_geo.csv.7z
7z x ok_geo.csv.7z

# 2. 装依赖（拼音库）
npm init -y && npm i pinyin-pro

# 3. 拷贝脚本并运行
cp /home/orlando/dingwei/tools/citydb/*.mjs .
node fetch_taiwan.mjs     # 生成 taiwan_coords.json
node build.mjs            # 生成 cities.json 到 app/src/main/assets/

# 4. 校验（两层）
node verify.mjs
python3 /home/orlando/dingwei/.tools/verify_cities.py
```

## ⚠️ 坐标系是本项目最容易出错的地方

`ok_geo.csv` 的 `geo` 列是**高德 GCJ-02 火星坐标**（该数据集 README 第 140、238 行明确说明），
而本 App 内部统一使用 WGS-84。直接使用会造成 **265~621 米**的系统性偏移，
且这种偏移用「经纬度是否在中国范围内」这类校验**完全发现不了**。

判定数据集确实是 GCJ-02 的客观证据（详见根目录 README）：
拿北京、澳门行政边界外框的极值与 OSM 同一边界对比，实测偏移向量与 GCJ-02 理论偏移
吻合到约 10 米。
