package org.torproject.android.ui.connect

import org.torproject.android.R
import org.torproject.android.ui.core.RequestScheduleExactAlarmDialogFragment

class PowerUserForegroundPermDialog : RequestScheduleExactAlarmDialogFragment() {
    override fun getTitleId(): Int = R.string.power_user_mode_permission
    override fun getMessageId(): Int = R.string.power_user_mode_permission_msg
}