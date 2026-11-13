package com.example.totalcheck.ui.shell

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

enum class VisualStyle(val label: String) {
    StyleA("風格 A"),
    StyleB("風格 B"),
    StyleC("風格 C")
}

data class MockCheckItem(
    val id: Long,
    val label: String,
    val isChecked: Boolean = false
)

data class MockChecklist(
    val id: Long,
    val name: String,
    val items: SnapshotStateList<MockCheckItem>
)

fun createInitialMockChecklists(): SnapshotStateList<MockChecklist> = mutableStateListOf(
    MockChecklist(
        id = 1L,
        name = "隨身包",
        items = mutableStateListOf(
            MockCheckItem(101L, "鑰匙與門禁卡", isChecked = false),
            MockCheckItem(102L, "行動電源 (10000mAh)", isChecked = false),
            MockCheckItem(103L, "Type-C 充電線", isChecked = false),
            MockCheckItem(104L, "抗噪耳機", isChecked = false),
            MockCheckItem(105L, "錢包與悠遊卡", isChecked = true),
            MockCheckItem(106L, "摺疊傘", isChecked = true),
        )
    ),
    MockChecklist(
        id = 2L,
        name = "實驗室",
        items = mutableStateListOf(
            MockCheckItem(201L, "筆記型電腦", isChecked = false),
            MockCheckItem(202L, "實驗紀錄本", isChecked = false),
            MockCheckItem(203L, "防靜電手環", isChecked = false),
            MockCheckItem(204L, "USB-C 擴充塢", isChecked = false),
            MockCheckItem(205L, "識別證", isChecked = true),
            MockCheckItem(206L, "白袍", isChecked = true),
        )
    ),
    MockChecklist(
        id = 3L,
        name = "返鄉行李",
        items = mutableStateListOf(
            MockCheckItem(301L, "換洗衣物 (3 天份)", isChecked = false),
            MockCheckItem(302L, "筆電充電器", isChecked = false),
            MockCheckItem(303L, "個人盥洗包", isChecked = false),
            MockCheckItem(304L, "高鐵車票 / 證件", isChecked = false),
            MockCheckItem(305L, "備用眼鏡", isChecked = true),
            MockCheckItem(306L, "常備藥品", isChecked = true),
        )
    )
)
