package com.cmtaro.app.hitandblowgame.domain.rule

import com.cmtaro.app.hitandblowgame.domain.model.JudgeResult

class HitBlowCalculator {

    fun judge(answer: String, input: String): JudgeResult {
        var hit = 0
        var blow = 0

        for (i in answer.indices) {
            if (answer[i] == input[i]) {
                hit++
            } else if (answer.contains(input[i])) {
                blow++
            }
        }
        return JudgeResult(hit, blow)
    }

}
