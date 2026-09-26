package com.dingwei.gpsmock.data

/**
 * 城市搜索匹配：纯逻辑、不依赖 Android，便于单元测试。
 *
 * 匹配维度与优先级：中文名 > 拼音全拼 > 拼音首字母 > 省份名。
 * `cities.json` 里的拼音是全名音译（北京市 → `beijingshi` / `bjs`），
 * 因此「beijing」「beijingshi」「bj」「bjs」都能命中。
 */
object CityMatcher {

    /**
     * 匹配打分，越小越优先；无匹配返回 null。
     *
     * | 分值 | 含义 |
     * |---|---|
     * | 0 | 中文名完全相等 |
     * | 1 | 中文名前缀 |
     * | 2 | 中文名包含 |
     * | 3 | 首字母完全相等 |
     * | 4 | 全拼完全相等 |
     * | 5 | 全拼前缀 |
     * | 6 | 首字母前缀 |
     * | 7 | 全拼包含 |
     * | 8 | 省份完全相等 |
     * | 9 | 省份前缀 |
     */
    fun score(city: City, query: String): Int? {
        val q = query.trim()
        if (q.isEmpty()) return null
        val qLower = q.lowercase()

        val name = city.name
        if (name == q) return 0
        if (name.startsWith(q)) return 1
        if (name.contains(q)) return 2

        val pinyin = city.pinyin.lowercase()
        val py = city.py.lowercase()
        if (py.isNotEmpty() && py == qLower) return 3
        if (pinyin.isNotEmpty() && pinyin == qLower) return 4
        if (pinyin.isNotEmpty() && pinyin.startsWith(qLower)) return 5
        if (py.isNotEmpty() && py.startsWith(qLower)) return 6
        if (pinyin.isNotEmpty() && pinyin.contains(qLower)) return 7

        // 输入省份名时，列出该省下的城市
        if (city.province == q) return 8
        if (city.province.isNotEmpty() && city.province.startsWith(q)) return 9

        return null
    }

    /** 行政级别排序权重：省级优先于地级市，最后才是区县。 */
    fun levelRank(city: City): Int = when (city.level) {
        "province" -> 0
        "city" -> 1
        else -> 2
    }

    /** 结果排序：匹配分 → 行政级别 → 名称长度。 */
    val ordering: Comparator<Pair<City, Int>> =
        compareBy({ it.second }, { levelRank(it.first) }, { it.first.name.length })

    /** 用给定查询词搜索并排序（纯内存操作，供 [CityRepository] 与测试共用）。 */
    fun search(cities: List<City>, query: String, limit: Int = 20): List<PlaceResult> {
        if (query.isBlank()) return emptyList()
        return cities
            .mapNotNull { city -> score(city, query)?.let { city to it } }
            .sortedWith(ordering)
            .take(limit)
            .map { toPlaceResult(it.first) }
    }

    fun toPlaceResult(city: City): PlaceResult = PlaceResult(
        name = city.name,
        detail = listOf(city.province, levelLabel(city.level))
            .filter { it.isNotEmpty() && it != city.name }
            .distinct()
            .joinToString(" · "),
        lat = city.lat,
        lon = city.lon,
        source = PlaceResult.Source.OFFLINE
    )

    fun levelLabel(level: String): String = when (level) {
        "province" -> "省级"
        "city" -> "地级市"
        "district" -> "区县"
        else -> ""
    }
}
