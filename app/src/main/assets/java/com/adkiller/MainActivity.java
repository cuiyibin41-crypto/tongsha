package com.adkiller;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;

/**
 * 配置界面：调倍率 / 固定eCPM / 开关
 * 改完点“保存”，目标App重启后生效
 */
public class MainActivity extends Activity {

    private SeekBar multiBar;
    private TextView multiLabel;
    private EditText fixedInput;
    private EditText minInput;
    private Switch enableSwitch;
    private Switch skipSwitch;
    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = Prefs.sp(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 64, 48, 48);
        root.setBackgroundColor(Color.parseColor("#101418"));

        addTitle(root, "广告奖励放大器");
        addSub(root, "看广告前先在这里调好倍率，改完保存 → 重启目标App");

        // 总开关
        enableSwitch = new Switch(this);
        enableSwitch.setText("启用模块");
        enableSwitch.setTextColor(Color.WHITE);
        enableSwitch.setChecked(Prefs.enable(this));
        root.addView(enableSwitch);

        // 倍率滑块 1~100
        multiLabel = addLabel(root, "放大倍率：x" + (int) Prefs.multiplier(this));
        multiBar = new SeekBar(this);
        multiBar.setMax(99); // 1..100
        multiBar.setProgress((int) Prefs.multiplier(this) - 1);
        multiBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                multiLabel.setText("放大倍率：x" + (p + 1));
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        root.addView(multiBar);

        // 固定 eCPM
        fixedInput = addInput(root, "固定 eCPM（填0=不用固定，用倍率）", String.valueOf(Prefs.fixedEcpm(this)));

        // 最低 eCPM 才放大
        minInput = addInput(root, "仅当真实eCPM≥此值才放大（填0=全部）", String.valueOf(Prefs.minEcpm(this)));

        // 秒过10秒
        skipSwitch = new Switch(this);
        skipSwitch.setText("秒过“看广告10秒”门槛");
        skipSwitch.setTextColor(Color.WHITE);
        skipSwitch.setChecked(Prefs.skip10s(this));
        root.addView(skipSwitch);

        // 保存按钮
        Button save = new Button(this);
        save.setText("保存配置");
        save.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { save(); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 40;
        root.addView(save, lp);

        addSub(root, "提示：倍率越高越易风控，建议先试 x5~x20。");

        setContentView(root);
    }

    private void save() {
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
        Toast.makeText(this, "已保存：倍率x" + multi + (fixed > 0 ? "，固定eCPM=" + fixed : "") + "\n请重启目标App生效", Toast.LENGTH_LONG).show();
    }

    private int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private void addTitle(LinearLayout r, String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(Color.parseColor("#38e0d0"));
        v.setTextSize(22);
        r.addView(v);
    }

    private void addSub(LinearLayout r, String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(Color.parseColor("#7d8fa8"));
        v.setTextSize(13);
        v.setPadding(0, 8, 0, 24);
        r.addView(v);
    }

    private TextView addLabel(LinearLayout r, String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(Color.WHITE);
        v.setTextSize(16);
        v.setPadding(0, 24, 0, 8);
        r.addView(v);
        return v;
    }

    private EditText addInput(LinearLayout r, String hint, String val) {
        addLabel(r, hint);
        EditText e = new EditText(this);
        e.setText(val);
        e.setTextColor(Color.WHITE);
        e.setHint(hint);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        r.addView(e);
        return e;
    }
}
