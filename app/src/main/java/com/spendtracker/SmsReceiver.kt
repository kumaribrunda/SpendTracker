package com.spendtracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val first = msgs.firstOrNull() ?: return
        val sender = first.originatingAddress ?: return
        val body = msgs.joinToString("") { it.messageBody ?: "" }
        val d = SmsParser.parse(sender, body) ?: return   // credits & everything else dropped here
        val ts = first.timestampMillis
        if (Db(ctx).insert("DEBIT", d.amount, d.label, ts, "$sender|$ts|${body.hashCode()}")) Bus.tick++
    }
}
