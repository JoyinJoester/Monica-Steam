/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.monica.telegramchat;

import android.content.Context;
import com.monica.telegramchat.upstream.EditTextBoldCursor;

/** Public host name for the extracted Telegram cursor/text watcher implementation. */
public final class TelegramEditText extends EditTextBoldCursor {
    public TelegramEditText(Context context) { super(context); }
}
