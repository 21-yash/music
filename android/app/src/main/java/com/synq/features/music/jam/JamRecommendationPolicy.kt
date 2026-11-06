package com.synq.features.music.jam

fun canDismissRecommendation(isHost: Boolean, perms: JamPermissions): Boolean {
    return isHost || perms.queueAdd || perms.queueManage
}
