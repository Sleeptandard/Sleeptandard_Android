package com.example.sleeptandard_mvp_demo.ClassFile

object QnARepository {
    val items = listOf(
        QnAItem(
            id = "watch_not_wear",
            title = "워치를 착용하지 않고 자면 어떻게 되나요",
            question = "이러쿵저러쿵해서 알람이 안 울려요.\n어떻게하죠\n엉엉",
            answer = "이러쿵저러쿵했군요.\n이렇게 저렇게 하시면 됩니다.\n죄송합니다."
        ),
        QnAItem(
            id = "watch_without_battery",
            title = "자는 도중에 워치 배터리가 나갔어요",
            question = "워치랑 어떻게 연결시키는거죠?\n아무것도 모르겠어요.\n엉엉엉",
            answer = "워치에 연결이 안되시는군요.\n워치 설정 가셔서~ 어쩌구 저쩌구~\n그래도 안되면.."
        ),
        QnAItem(
            id = "watch_go_away",
            title = "자는 도중에 워치가 풀렸어요",
            question = "워치랑 핸드폰 둘 다 울려요\n어떻게하죠\nㅠㅠㅠ",
            answer = "저런\n그럴일은 없습니다\n감사합니다."
        ),
        QnAItem(
            id = "feedback_change",
            title = "피드백을 (잘)못 했어요",
            question = "모르고 피드백을 안하고 꺼버렸어요.\n" +
                    "혹은, 피드백을 잘못 체크한채로 제출해버렸어요.",
            answer = "한 번 제출한 피드백은 다시 수정할 수 없어요.\n" +
                    "하지만 걱정하지 않으셔도 됩니다.\n\n" +
                    "알람모델은 한 번의 피드백이 아니라 지속적으로 누적되는 여러 피드백을 바탕으로 학습해요.\n" +
                    "간혹 잘못 체크하거나 피드백을 건너뛰더라도, 전체 학습 결과에 큰 오차를 만들지는 않아요. 이후에 남겨주시는 피드백들이 점점 더 정확한 기상 패턴을 만들어줍니다."
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