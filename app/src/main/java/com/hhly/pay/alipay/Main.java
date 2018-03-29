package com.hhly.pay.alipay;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import org.json.JSONObject;
import java.lang.reflect.Field;
import java.util.Map;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import com.hhly.pay.alipay.util.XmlToJson;

import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static com.hhly.pay.alipay.VersionParam.ALIPAY_PACKAGE_NAME;


public class Main implements IXposedHookLoadPackage {
    private static Activity launcherUiActivity = null;

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (lpparam.packageName.equals(ALIPAY_PACKAGE_NAME)) {
            // hook微信插入数据的方法，监听红包消息
            findAndHookMethod("com.tencent.wcdb.database.SQLiteDatabase", lpparam.classLoader, "insertWithOnConflict", String.class, String.class, ContentValues.class, int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    // 打印插入数据信息
                    log("------------------------insert start---------------------" + "\n\n");
                    log("param args1:" + param.args[0]);
                    log("param args1:" + param.args[1]);
                    ContentValues contentValues = (ContentValues) param.args[2];
                    log("param args3 contentValues:");
                    for (Map.Entry<String, Object> item : contentValues.valueSet())
                    {
                        if (item.getValue() != null) {
                            log(item.getKey() + "---------" + item.getValue().toString());
                        } else {
                            log(item.getKey() + "---------" + "null");
                        }
                    }
                    log("------------------------insert over---------------------" + "\n\n");

                    // 判断插入的数据是否是发送过来的消息
                    String tableName = (String) param.args[0];
                    if (TextUtils.isEmpty(tableName) || !tableName.equals("message")) {
                        return;
                    }
                    // 判断是否是红包消息类型
                    Integer type = contentValues.getAsInteger("type");
                    if (null == type) {
                        return;
                    }
                    if (type == 436207665) {
                        // 处理红包消息
                        handleLuckyMoney(contentValues, lpparam);
                    }
                }
            });

            // hook 微信主界面的onCreate方法，获得主界面对象
            findAndHookMethod("com.tencent.mm.ui.LauncherUI", lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    log("com.tencent.mm.ui.LauncherUI onCreated" + "\n");
                    launcherUiActivity = (Activity) param.thisObject;
                }
            });

            // hook领取红包页面的onCreate方法，打印Intent中的参数（只起到调试作用）
            findAndHookMethod("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyReceiveUI", lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Activity activity = (Activity) param.thisObject;
                    String key_native_url = activity.getIntent().getStringExtra("key_native_url");
                    String key_username = activity.getIntent().getStringExtra("key_username");
                    int key_way = activity.getIntent().getIntExtra("key_way", 0);
                    log("key_native_url: " + key_native_url + "\n");
                    log("key_way: " + key_way + "\n");
                    log("key_username: " + key_username + "\n");
                }
            });

            // hook红包界面初始化“开”按钮的方法，在该方法完成后自动点击开按钮领取红包
            findAndHookMethod("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyReceiveUI", lpparam.classLoader, "d", int.class, int.class, String.class, findClass("com.tencent.mm.ae.k", lpparam.classLoader), new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    log("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyReceiveUI: Method d called" + "\n");
                    Field buttonField = XposedHelpers.findField(param.thisObject.getClass(), "ogX");
                    final Button kaiButton = (Button) buttonField.get(param.thisObject);
                    kaiButton.performClick();
                }
            });
        }

    }

    // 处理红包消息
    private void handleLuckyMoney(ContentValues contentValues, LoadPackageParam lpparam) throws Exception {
        // 获得发送人
        String talker = contentValues.getAsString("talker");

        // 从插入的数据库中获得nativeurl
        String content = contentValues.getAsString("content");
        if (!content.startsWith("<msg")) {
            content = content.substring(content.indexOf("<msg"));
        }

        JSONObject wcpayinfo = new XmlToJson.Builder(content).build()
                .getJSONObject("msg").getJSONObject("appmsg").getJSONObject("wcpayinfo");

        String nativeUrlString = wcpayinfo.getString("nativeurl");
        log("nativeurl: " + nativeUrlString + "\n");

        // 启动红包页面
        if (launcherUiActivity != null) {
            log("call method com.tencent.mm.bm.d b, start LuckyMoneyReceiveUI" + "\n");
            Intent paramau = new Intent();
            paramau.putExtra("key_way", 1);
            paramau.putExtra("key_native_url", nativeUrlString);
            paramau.putExtra("key_username", talker);
            callStaticMethod(findClass("com.tencent.mm.bm.d", lpparam.classLoader), "b", launcherUiActivity, "luckymoney", ".ui.LuckyMoneyReceiveUI", paramau);
        } else {
            log("launcherUiActivity == null" + "\n");
        }
    }
}