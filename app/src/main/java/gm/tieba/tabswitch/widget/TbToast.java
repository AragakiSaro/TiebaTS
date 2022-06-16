package gm.tieba.tabswitch.widget;

import android.content.Context;

import androidx.annotation.MainThread;

import gm.tieba.tabswitch.Constants;
import gm.tieba.tabswitch.XposedContext;
import gm.tieba.tabswitch.dao.AcRules;
import gm.tieba.tabswitch.util.ReflectUtils;

public class TbToast extends XposedContext {
    public static int LENGTH_SHORT = 2000;
    public static int LENGTH_LONG = 3500;

    @MainThread
    public static void showTbToast(String text, int duration) {
        AcRules.findRule(Constants.getMatchers().get(TbToast.class), (AcRules.Callback) (matcher, clazz, method) -> {
            var md = ReflectUtils.findFirstMethodByExactType(clazz, Context.class, String.class, int.class);
            runOnUiThread(() -> ReflectUtils.callStaticMethod(md, getContext(), text, duration));
        });
    }
}
