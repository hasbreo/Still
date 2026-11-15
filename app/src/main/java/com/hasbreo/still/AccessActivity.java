package com.hasbreo.still;

import android.app.*;
import android.os.*;
import android.webkit.*;
import android.widget.*;

public class AccessActivity extends Activity {
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    LinearLayout l = Ui.column(this);
    l.setBackgroundColor(Ui.BG);
    l.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), 0);
    Button done =
        Ui.button(
            this,
            "‹  Return to Still",
            () -> {
              CookieManager.getInstance().flush();
              finish();
            });
    l.addView(done, new LinearLayout.LayoutParams(-1, Ui.dp(this, 50)));
    WebView w = new WebView(this);
    w.getSettings().setJavaScriptEnabled(true);
    w.getSettings().setDomStorageEnabled(true);
    w.setWebViewClient(new WebViewClient());
    l.addView(w, new LinearLayout.LayoutParams(-1, 0, 1));
    setContentView(l);
    String url = getIntent().getStringExtra("url");
    if (url != null && url.startsWith("https://")) w.loadUrl(url);
  }
}
