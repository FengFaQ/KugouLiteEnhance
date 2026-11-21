package com.example.kugouliteenhance.ui;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import com.example.kugouliteenhance.R;

/** Minimal launcher Activity explaining how to activate the module. */
public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("酷狗概念版增强模块\n\n"
                + "1. 在 LSPosed 中启用本模块\n"
                + "2. 勾选作用域：com.kugou.android.lite\n"
                + "3. 重启酷狗概念版\n\n"
                + "Hook 类型与特征见 assets/hook_config.json");
        tv.setTextSize(16f);
        tv.setPadding(40, 40, 40, 40);
        setContentView(tv);
    }
}
