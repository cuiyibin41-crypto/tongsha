package com.adkiller;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** 配置界面：调倍率 / 固定eCPM / 底限 / 秒过开关 */
public class MainActivity extends Activity {

    private SharedPreferences sp;
    private SeekBar multiBar;
    private TextView multiLabel;
    private EditText fixedInput, minInput;
    private Switch enableSwitch, skipSwitch;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            sp = Prefs.sp(this);
            setContentView(buildRoot());
        } catch (Throwable t) {
            TextView tv = new TextView(this);
            tv.setPadding(40, 60, 40, 40);
            tv.setTextSize(13);
            tv.setText("页面构建失败：\n" + t);
            setContentView(tv);
        }
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private View buildRoot() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#0F1418"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        scroll.addView(root);

        // 标题
        TextView title = new TextView(this);
        title.setText("AdKiller 广告奖励放大器");
        title.setTextColor(Color.parseColor("#38E0D0"));
        title.setTextSize(20);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Hook 四大联盟 eCPM 读取点，放大奖励。\n改完保存 → 重启目标App（如吉人天相）生效。");
        sub.setTextColor(Color.parseColor("#8A9BB0"));
        sub.setTextSize(12);
        sub.setPadding(0, dp(6), 0, dp(18));
        root.addView(sub);

        // 总开关
        enableSwitch = new Switch(this);
        enableSwitch.setText("启用模块");
        enableSwitch.setTextColor(Color.WHITE);
        enableSwitch.setChecked(sp.getBoolean(Prefs.KEY_ENABLE, true));
        root.addView(enableSwitch);

        // 倍率滑块（1~100）
        multiLabel = label(root, "放大倍率：x" + curMulti());
        multiBar = new SeekBar(this);
        multiBar.setMax(99);
        multiBar.setProgress(Math.max(0, curMulti() - 1));
        multiBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                multiLabel.setText("放大倍率：x" + (p + 1));
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        root.addView(multiBar);

        fixedInput = input(root, "固定 eCPM（0=不用；>0 时忽略倍率强制此值）",
                String.valueOf(sp.getInt(Prefs.KEY_FIXED, 0)));
        minInput = input(root, "最低 eCPM 才放大（0=全部放大）",
                String.valueOf(sp.getInt(Prefs.KEY_MIN, 0)));

        // 秒过开关
        skipSwitch = new Switch(this);
        skipSwitch.setText("秒过“看广告 10 秒”门槛（吉人天相）");
        skipSwitch.setTextColor(Color.WHITE);
        skipSwitch.setChecked(sp.getBoolean(Prefs.KEY_SKIP, false));
        root.addView(skipSwitch);

        // 保存
        Button save = new Button(this);
        save.setText("保存配置");
        save.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { doSave(); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(22);
        root.addView(save, lp);

        TextView tip = new TextView(this);
        tip.setText("建议先 x10 测试；被风控就降倍率。\n仅朱学习研究使用。");
        tip.setTextColor(Color.parseColor("#8A9BB0"));
        tip.setTextSize(12);
        tip.setPadding(0, dp(14), 0, 0);
        root.addView(tip);
        return scroll;
    }

    private int curMulti() {
        int v = (int) sp.getFloat(Prefs.KEY_MULTI, 20f);
        if (v < 1) v = 1;
        if (v > 100) v = 100;
        return v;
    }

    private void doSave() {
        int multi = multiBar.getProgress() + 1;
        int fixed = parseIntSafe(fixedInput.getText().toString(), 0);
        int min = parseIntSafe(minInput.getText().toString(), 0);
        sp.edit()
          .putBoolean(Prefs.KEY_ENABLE, enableSwitch.isChecked())
          .putFloat(Prefs.KEY_MULTI, (float) multi)
          .putInt(Prefs.KEY_FIXED, fixed)
          .putInt(Prefs.KEY_MIN, min)
          .putBoolean(Prefs.KEY_SKIP, skipSwitch.isChecked())
          .apply();
        Prefs.makeReadable(this);
        Toast.makeText(this,
                "已保存：x" + multi + (fixed > 0 ? "，固定" + fixed : "")
                        + "\n请强制停止并重启目标App",
                Toast.LENGTH_LONG).show();
    }

    private int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Throwable t) { return def; }
    }

    private TextView label(LinearLayout r, String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(Color.WHITE);
        v.setTextSize(15);
        v.setPadding(0, dp(16), 0, dp(4));
        r.addView(v);
        return v;
    }

    private EditText input(LinearLayout r, String hint, String val) {
        label(r, hint);
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        e.setText(val);
        e.setTextColor(Color.WHITE);
        e.setTextSize(15);
        r.addView(e);
        return e;
    }
}
