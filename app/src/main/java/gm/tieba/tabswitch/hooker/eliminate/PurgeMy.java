package gm.tieba.tabswitch.hooker.eliminate;

import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import gm.tieba.tabswitch.XposedContext;
import gm.tieba.tabswitch.dao.AcRules;
import gm.tieba.tabswitch.hooker.IHooker;
import gm.tieba.tabswitch.hooker.Obfuscated;
import gm.tieba.tabswitch.hooker.deobfuscation.Matcher;
import gm.tieba.tabswitch.hooker.deobfuscation.SmaliMatcher;
import gm.tieba.tabswitch.hooker.deobfuscation.StringMatcher;
import gm.tieba.tabswitch.util.ReflectUtils;

public class PurgeMy extends XposedContext implements IHooker, Obfuscated {

    @NonNull
    @Override
    public String key() {
        return "purge_my";
    }

    private final int mGridTopPadding = (int) ReflectUtils.getDimen("tbds25");

    @Override
    public List<? extends Matcher> matchers() {
        return List.of(
                new SmaliMatcher("Lcom/baidu/tieba/personCenter/view/PersonOftenFuncItemView;-><init>(Landroid/content/Context;)V")
        );
    }

    @Override
    public void hook() throws Throwable {
        XposedHelpers.findAndHookMethod("tbclient.Profile.DataRes$Builder", sClassLoader, "build", boolean.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                // 我的贴吧会员
                XposedHelpers.setObjectField(param.thisObject, "vip_banner", null);

                // 横幅广告
                XposedHelpers.setObjectField(param.thisObject, "banner", new ArrayList<>());

                // 度小满 有钱花
                XposedHelpers.setObjectField(param.thisObject, "finance_tab", null);

                // 小程序
                XposedHelpers.setObjectField(param.thisObject, "recom_naws_list", new ArrayList<>());
            }
        });
        XposedHelpers.findAndHookMethod("tbclient.User$Builder", sClassLoader, "build", boolean.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                // 贴吧成长等级
                XposedHelpers.setObjectField(param.thisObject, "user_growth", null);
            }
        });
        // Add padding to the top of 常用功能
        AcRules.findRule(matchers(), (matcher, clazz, method) -> {
            XposedBridge.hookAllConstructors(
                    XposedHelpers.findClass(clazz, sClassLoader),
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            View mView = ReflectUtils.getObjectField(param.thisObject, View.class);
                            mView.setPadding(mView.getPaddingLeft(), mGridTopPadding, mView.getPaddingRight(), 0);
                        }
                    }
            );
        });
    }
}
