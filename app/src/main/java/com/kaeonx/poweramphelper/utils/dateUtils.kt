package com.kaeonx.poweramphelper.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun millisToDisplayWithTZ(millis: Long) =
    SimpleDateFormat("HHmmss ddMMyy z", Locale.UK).format(Date(millis))

internal fun millisToDisplayWithoutTZ(millis: Long) =
    SimpleDateFormat("HHmm ddMMyy", Locale.UK).format(Date(millis))