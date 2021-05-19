package gm.tieba.tabswitch.hooker;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.jf.dexlib.ClassDefItem;
import org.jf.dexlib.DexFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

import bin.zip.ZipEntry;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import gm.tieba.tabswitch.BaseHooker;
import gm.tieba.tabswitch.IHooker;
import gm.tieba.tabswitch.R;
import gm.tieba.tabswitch.dao.Preferences;
import gm.tieba.tabswitch.dao.RulesDbHelper;
import gm.tieba.tabswitch.util.IO;

public class AntiConfusion extends BaseHooker implements IHooker {
    private static final String SPRINGBOARD_ACTIVITY = "com.baidu.tieba.tblauncher.MainTabActivity";

    public AntiConfusion(ClassLoader classLoader, Resources res) {
        super(classLoader, res);
    }

    public void hook() throws Throwable {
        for (Method method : sClassLoader.loadClass("com.baidu.tieba.LogoActivity").getDeclaredMethods()) {
            if (!method.getName().startsWith("on") && Arrays.toString(method.getParameterTypes()).equals("[class android.os.Bundle]")) {
                XposedBridge.hookMethod(method, new XC_MethodReplacement() {
                    @SuppressLint({"ApplySharedPref", "SetTextI18n"})
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        Activity activity = (Activity) param.thisObject;
                        if (Preferences.getBoolean("purify")) {
                            SharedPreferences.Editor editor = activity.getSharedPreferences("settings", Context.MODE_PRIVATE).edit();
                            editor.putString("key_location_request_dialog_last_show_version", AntiConfusionHelper.getTbVersion(activity));
                            editor.commit();
                        }
                        if (AntiConfusionHelper.isDexChanged(activity)) {
                            activity.deleteDatabase("Rules.db");
                        } else if (AntiConfusionHelper.getRulesLost().size() != 0) {
                            AntiConfusionHelper.matcherList = AntiConfusionHelper.getRulesLost();
                        } else {
                            AntiConfusionHelper.saveAndRestart(activity, AntiConfusionHelper.getTbVersion(activity), sClassLoader.loadClass(SPRINGBOARD_ACTIVITY));
                        }
                        new RulesDbHelper(activity.getApplicationContext()).getReadableDatabase();

                        TextView title = new TextView(activity);
                        title.setTextSize(16);
                        title.setPadding(0, 0, 0, 20);
                        title.setGravity(Gravity.CENTER);
                        title.setTextColor(sRes.getColor(R.color.colorPrimaryDark, null));
                        title.setText("贴吧TS正在定位被混淆的类和方法，请耐心等待");
                        TextView textView = new TextView(activity);
                        textView.setTextSize(16);
                        textView.setTextColor(sRes.getColor(R.color.colorPrimaryDark, null));
                        textView.setText("读取ZipEntry");
                        TextView progressBackground = new TextView(activity);
                        progressBackground.setBackgroundColor(sRes.getColor(R.color.colorProgress, null));
                        RelativeLayout progressContainer = new RelativeLayout(activity);
                        progressContainer.addView(progressBackground);
                        progressContainer.addView(textView);
                        RelativeLayout.LayoutParams tvLp = (RelativeLayout.LayoutParams) textView.getLayoutParams();
                        tvLp.addRule(RelativeLayout.CENTER_IN_PARENT);
                        textView.setLayoutParams(tvLp);
                        RelativeLayout.LayoutParams rlLp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                        progressContainer.setLayoutParams(rlLp);
                        LinearLayout linearLayout = new LinearLayout(activity);
                        linearLayout.setOrientation(LinearLayout.VERTICAL);
                        linearLayout.setGravity(Gravity.CENTER);
                        linearLayout.addView(title);
                        linearLayout.addView(progressContainer);
                        activity.setContentView(linearLayout);
                        new Thread(() -> {
                            File dexDir = new File(activity.getCacheDir().getAbsolutePath(), "dex");
                            try {
                                IO.deleteRecursively(dexDir);
                                dexDir.mkdirs();
                                bin.zip.ZipFile zipFile = new bin.zip.ZipFile(new File(activity.getPackageResourcePath()));
                                Enumeration<ZipEntry> enumeration = zipFile.getEntries();
                                int entryCount = 0;
                                int entrySize = zipFile.getEntrySize();
                                while (enumeration.hasMoreElements()) {
                                    entryCount++;
                                    float progress = (float) entryCount / entrySize;
                                    activity.runOnUiThread(() -> {
                                        textView.setText("解压");
                                        ViewGroup.LayoutParams lp = progressBackground.getLayoutParams();
                                        lp.height = textView.getHeight();
                                        lp.width = (int) (progressContainer.getWidth() * progress);
                                        progressBackground.setLayoutParams(lp);
                                    });
                                    ZipEntry ze = enumeration.nextElement();
                                    if (ze.getName().matches("classes[0-9]*?\\.dex")) {
                                        IO.copy(zipFile.getInputStream(ze), new File(dexDir, ze.getName()));
                                    }
                                }
                                File[] fs = dexDir.listFiles();
                                if (fs == null) throw new FileNotFoundException("解压失败");
                                Arrays.sort(fs, (o1, o2) -> {
                                    int i1;
                                    int i2;
                                    try {
                                        i1 = Integer.parseInt(o1.getName().replaceAll("[a-z.]", ""));
                                    } catch (NumberFormatException e) {
                                        i1 = 1;
                                    }
                                    try {
                                        i2 = Integer.parseInt(o2.getName().replaceAll("[a-z.]", ""));
                                    } catch (NumberFormatException e) {
                                        i2 = 1;
                                    }
                                    return i1 - i2;
                                });
                                List<List<Integer>> itemList = new ArrayList<>();
                                int totalItemCount = 0;
                                boolean isSkip = false;
                                for (int i = 0; i < fs.length; i++) {
                                    DexFile dex = new DexFile(fs[i]);
                                    List<ClassDefItem> classes = dex.ClassDefsSection.getItems();
                                    List<Integer> arrayList = new ArrayList<>();
                                    for (int j = 0; j < classes.size(); j++) {
                                        float progress = (float) j / fs.length / classes.size() + (float) i / fs.length;
                                        activity.runOnUiThread(() -> {
                                            textView.setText("读取类签名");
                                            ViewGroup.LayoutParams lp = progressBackground.getLayoutParams();
                                            lp.height = textView.getHeight();
                                            lp.width = (int) (progressContainer.getWidth() * progress);
                                            progressBackground.setLayoutParams(lp);
                                        });
                                        String signature = classes.get(j).getClassType().getTypeDescriptor();
                                        if (signature.matches("Ld/[a-b]/.*")) {
                                            arrayList.add(classes.get(j).getIndex());
                                            isSkip = true;
                                        } else if (signature.startsWith("Lcom/baidu/tbadk") || !isSkip && (signature.startsWith("Lcom/baidu/tieba"))) {
                                            arrayList.add(classes.get(j).getIndex());
                                        }
                                    }
                                    totalItemCount += arrayList.size();
                                    itemList.add(arrayList);
                                }
                                int itemCount = 0;
                                SQLiteDatabase db = activity.openOrCreateDatabase("Rules.db", Context.MODE_PRIVATE, null);
                                for (int i = 0; i < fs.length; i++) {
                                    DexFile dex = new DexFile(fs[i]);
                                    List<Integer> arrayList = itemList.get(i);
                                    for (int j = 0; j < arrayList.size(); j++) {
                                        itemCount++;
                                        float progress = (float) itemCount / totalItemCount;
                                        activity.runOnUiThread(() -> {
                                            textView.setText("搜索");
                                            ViewGroup.LayoutParams lp = progressBackground.getLayoutParams();
                                            lp.height = textView.getHeight();
                                            lp.width = (int) (progressContainer.getWidth() * progress);
                                            progressBackground.setLayoutParams(lp);
                                        });
                                        ClassDefItem classItem = dex.ClassDefsSection.getItemByIndex(arrayList.get(j));
                                        AntiConfusionHelper.searchAndSave(classItem, 0, db);
                                        AntiConfusionHelper.searchAndSave(classItem, 1, db);
                                    }
                                }
                                activity.runOnUiThread(() -> textView.setText("保存反混淆信息"));
                                byte[] bytes = new byte[32];
                                new FileInputStream(fs[0]).read(bytes);
                                DexFile.calcSignature(bytes);
                                Preferences.putSignature(Arrays.hashCode(bytes));
                                IO.deleteRecursively(dexDir);
                                XposedBridge.log("anti-confusion accomplished, current version: " + AntiConfusionHelper.getTbVersion(activity));
                                AntiConfusionHelper.saveAndRestart(activity, AntiConfusionHelper.getTbVersion(activity), sClassLoader.loadClass(SPRINGBOARD_ACTIVITY));
                            } catch (Throwable throwable) {
                                activity.runOnUiThread(() -> textView.setText(String.format("处理失败\n%s", Log.getStackTraceString(throwable))));
                                XposedBridge.log(throwable);
                            }
                        }).start();
                        return null;
                    }
                });
            }
        }
    }
}
