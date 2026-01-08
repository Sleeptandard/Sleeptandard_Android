package com.example.sleeptandard_mvp_demo.ClassFile

object QnARepository {
    val items = listOf(
        QnAItem(
            id = "alarm_not_ringing",
            title = "알람이 안 울려요.",
            question = "이러쿵저러쿵해서 알람이 안 울려요.\n어떻게하죠\n엉엉",
            answer = "이러쿵저러쿵했군요.\n이렇게 저렇게 하시면 됩니다.\n죄송합니다."
        ),
        QnAItem(
            id = "watch_how_pairing",
            title = "워치랑 어떻게 연동하나요?",
            question = "워치랑 어떻게 연결시키는거죠?\n아무것도 모르겠어요.\n엉엉엉",
            answer = "워치에 연결이 안되시는군요.\n워치 설정 가셔서~ 어쩌구 저쩌구~\n그래도 안되면.."
        ),
        QnAItem(
            id = "alarm_only_phone",
            title = "핸드폰에서만 울리게 할 순 없나요?",
            question = "워치랑 핸드폰 둘 다 울려요\n어떻게하죠\nㅠㅠㅠ",
            answer = "저런\n그럴일은 없습니다\n감사합니다."
        ),
        QnAItem(
            id = "alarm_not_ringing2",
            title = "알람이 안 울려요.",
            question = "이러쿵저러쿵해서 알람이 안 울려요.\n어떻게하죠\n영영영",
            answer = "이러쿵저러쿵했군요.\n이렇게 저렇게 하시면 됩니다.\n죄송합니다."
        ),
        QnAItem(
            id = "alarm_not_ringing3 ",
            title = "알람이 안 울려요.",
            question = "이러쿵저러쿵해서 알람이 안 울려요.\n어떻게하죠\n영영영",
            answer = "이러쿵저러쿵했군요.\n이렇게 저렇게 하시면 됩니다.\n죄송합니다."
        ),
    )

    fun findById(id: String): QnAItem? = items.firstOrNull { it.id == id }
}