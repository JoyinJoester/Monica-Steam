package takagi.ru.monica.steam.navigation;

import android.inputmethodservice.InputMethodService;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Test APK only. Platform IME with deterministic bounds, no vault or network access. */
public class SteamSheetTestInputMethod extends InputMethodService {
    @Override public View onCreateInputView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xffe6e6e6);
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (240 * getResources().getDisplayMetrics().density)));
        TextView label = new TextView(this);
        label.setText("Synthetic test keyboard");
        label.setTextColor(0xff111111);
        label.setGravity(Gravity.CENTER);
        root.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, .6f));
        for (String letters : new String[]{"qwertyuiop", "asdfghjkl", "zxcvbnm"}) {
            LinearLayout row = new LinearLayout(this);
            for (int index = 0; index < letters.length(); index++) {
                String letter = String.valueOf(letters.charAt(index));
                Button key = new Button(this);
                key.setText(letter);
                key.setMinWidth(0);
                key.setMinimumWidth(0);
                key.setPadding(0, 0, 0, 0);
                key.setOnClickListener(view -> {
                    if (getCurrentInputConnection() != null) getCurrentInputConnection().commitText(letter, 1);
                });
                row.addView(key, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            }
            root.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        return root;
    }
}
