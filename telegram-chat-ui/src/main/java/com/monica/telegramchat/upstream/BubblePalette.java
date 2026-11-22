/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.monica.telegramchat.upstream;

/** Explicit color input replacing Telegram's account-wide Theme singleton. */
public interface BubblePalette {
    int key_chat_inBubble = 0;
    int key_chat_inBubbleSelected = 1;
    int key_chat_inBubbleShadow = 2;
    int key_chat_outBubble = 3;
    int key_chat_outBubbleSelected = 4;
    int key_chat_outBubbleShadow = 5;
    int key_chat_outBubbleGradientSelectedOverlay = 6;
    boolean shouldDrawGradientIcons = false;

    int getColor(int key);
    default int getCurrentColor(int key) { return getColor(key); }

    BubblePalette DEFAULT = key -> {
        switch (key) {
            case key_chat_inBubble: return 0xffffffff;
            case key_chat_inBubbleSelected: return 0xffdce9ed;
            case key_chat_outBubble: return 0xffe0ffc7;
            case key_chat_outBubbleSelected: return 0xffc5e5b0;
            case key_chat_inBubbleShadow:
            case key_chat_outBubbleShadow: return 0x225f6569;
            default: return 0x18000000;
        }
    };
}
