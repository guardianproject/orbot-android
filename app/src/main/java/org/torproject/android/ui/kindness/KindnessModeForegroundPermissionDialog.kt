package org.torproject.android.ui.kindness

import org.torproject.android.R
import org.torproject.android.ui.core.RequestScheduleExactAlarmDialogFragment

class KindnessModeForegroundPermissionDialog : RequestScheduleExactAlarmDialogFragment() {
    override fun getTitleId(): Int = R.string.kindness_mode_permission_title
    override fun getMessageId(): Int = R.string.kindness_mode_permission_msg
    override fun getPositiveButtonId(): Int = R.string.allow_permission
}