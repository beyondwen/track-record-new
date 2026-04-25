package com.wenhao.record.data.history

import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryRouteTitleResolverTest {

    @Test
    fun `choosePlaceName prefers specific poi name`() {
        val name = HistoryRouteTitleResolver.choosePlaceName(
            HistoryRouteTitleResolver.AddressParts(
                featureName = "鸿运之星",
                thoroughfare = "学院路",
                locality = "杭州市",
            )
        )

        assertEquals("鸿运之星", name)
    }

    @Test
    fun `choosePlaceName skips unnamed road and falls back to street`() {
        val name = HistoryRouteTitleResolver.choosePlaceName(
            HistoryRouteTitleResolver.AddressParts(
                featureName = "Unnamed Road",
                thoroughfare = "文一西路",
                subThoroughfare = "998号",
                locality = "杭州市",
            )
        )

        assertEquals("文一西路 998号", name)
    }

    @Test
    fun `choosePlaceName falls back to district when street is missing`() {
        val name = HistoryRouteTitleResolver.choosePlaceName(
            HistoryRouteTitleResolver.AddressParts(
                locality = "杭州市",
                subLocality = "西湖区",
            )
        )

        assertEquals("西湖区", name)
    }
}
