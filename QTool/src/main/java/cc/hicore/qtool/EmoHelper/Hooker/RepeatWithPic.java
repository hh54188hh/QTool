package cc.hicore.qtool.EmoHelper.Hooker;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.widget.EditText;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import cc.hicore.HookItemLoader.Annotations.MethodScanner;
import cc.hicore.HookItemLoader.Annotations.UIItem;
import cc.hicore.HookItemLoader.Annotations.VerController;
import cc.hicore.HookItemLoader.Annotations.XPExecutor;
import cc.hicore.HookItemLoader.Annotations.XPItem;
import cc.hicore.HookItemLoader.bridge.BaseXPExecutor;
import cc.hicore.HookItemLoader.bridge.MethodContainer;
import cc.hicore.HookItemLoader.bridge.MethodFinderBuilder;
import cc.hicore.HookItemLoader.bridge.UIInfo;
import cc.hicore.LogUtils.LogUtils;
import cc.hicore.ReflectUtils.MClass;
import cc.hicore.ReflectUtils.MField;
import cc.hicore.ReflectUtils.MMethod;
import cc.hicore.Utils.StringUtils;
import cc.hicore.qtool.QQMessage.QQMsgSendUtils;
import cc.hicore.qtool.QQMessage.QQSessionUtils;
import cc.hicore.qtool.XposedInit.HostInfo;

@XPItem(name = "带图回复",itemType = XPItem.ITEM_Hook)
public class RepeatWithPic {
    private static final HashMap<String, String> picCookies = new HashMap<>();
    public static volatile boolean IsEnable;
    @VerController
    @UIItem
    public UIInfo getUI(){
        UIInfo ui = new UIInfo();
        ui.name = "带图回复";
        ui.groupName = "聊天辅助";
        ui.type = 1;
        ui.targetID = 1;
        return ui;
    }
    @VerController
    @MethodScanner
    public void getHookMethod(MethodContainer container){
        container.addMethod("hook_1",MMethod.FindMethod("com.tencent.imcore.message.BaseQQMessageFacade", "a", void.class, new Class[]{
                MClass.loadClass("com.tencent.mobileqq.data.MessageRecord"),
                MClass.loadClass("com.tencent.mobileqq.app.BusinessObserver")}));
        container.addMethod("hook_2",MMethod.FindMethod("com.tencent.mobileqq.emoticonview.sender.CustomEmotionSenderUtil", "sendCustomEmotion", void.class, new Class[]{
                MClass.loadClass("com.tencent.common.app.business.BaseQQAppInterface"),
                Context.class,
                MClass.loadClass("com.tencent.mobileqq.activity.aio.BaseSessionInfo"),
                String.class,
                boolean.class,
                boolean.class,
                boolean.class,
                String.class,
                MClass.loadClass("com.tencent.mobileqq.emoticon.StickerInfo"),
                String.class,
                Bundle.class
        }));
        container.addMethod("hook_3",MMethod.FindMethod("com.tencent.mobileqq.activity.aio.photo.PhotoListPanel", null, boolean.class, new Class[]{
                MClass.loadClass("com.tencent.mobileqq.activity.aio.core.BaseChatPie"),
                List.class, boolean.class}));
        container.addMethod(MethodFinderBuilder.newFinderByString("basechatpie_init","AIO_doOnCreate_initUI", m ->m.getDeclaringClass().getName().equals("com.tencent.mobileqq.activity.aio.core.BaseChatPie")));
    }
    @VerController
    @XPExecutor(methodID = "basechatpie_init")
    public BaseXPExecutor basechatpie_init(){
        return param -> {
            ed = MField.GetFirstField(param.thisObject, MClass.loadClass("com.tencent.widget.XEditTextEx"));
            chatPie = param.thisObject;
        };
    }
    @VerController
    @XPExecutor(methodID = "hook_1")
    public BaseXPExecutor worker_1(){
        return param -> {
            Object AddMsg = param.args[0];
            int istroop = MField.GetField(AddMsg, "istroop", int.class);
            if (!picCookies.isEmpty() && AddMsg.getClass().getName().contains("MessageForReplyText") && (istroop == 1 || istroop == 0)) {
                String Text = MField.GetField(AddMsg, "msg", String.class);
                if (TextUtils.isEmpty(Text)) {
                    Text = MField.GetFirstField(AddMsg, CharSequence.class) + "";
                }
                if (Text.contains("[PicCookie")) {
                    String strTo = Text.substring(0, Text.indexOf("["));
                    Text = Text.substring(Text.indexOf("["));
                    String GroupUin = QQSessionUtils.getGroupUin();
                    String UserUin = QQSessionUtils.getFriendUin();
                    MField.SetField(AddMsg, "msg", strTo.isEmpty() ? " " : strTo);
                    if (HostInfo.getVerCode() > 7685)
                        MField.SetField(AddMsg, "charStr", strTo.isEmpty() ? " " : strTo);
                    else MField.SetField(AddMsg, "sb", strTo.isEmpty() ? " " : strTo);

                    MMethod.CallMethod(AddMsg, "prewrite", void.class, new Class[0]);

                    String[] Cookies = StringUtils.GetStringMiddleMix(Text, "[PicCookie=", "]");
                    for (String ss : Cookies) {
                        if (picCookies.containsKey(ss)) {
                            Text = Text.replace("[PicCookie=" + ss + "]", "[PicUrl=" + picCookies.get(ss) + "]");
                        }
                    }
                    picCookies.clear();
                    QQMsgSendUtils.decodeAndSendMsg(GroupUin, UserUin, Text, AddMsg);
                    param.setResult(null);
                }
            }
        };
    }
    @VerController
    @XPExecutor(methodID = "hook_2")
    public BaseXPExecutor worker_2(){
        return param -> {
            if (IsAvailable()) {
                String Path = (String) param.args[3];
                AddToPreSendList(Path);
                param.setResult(true);
            }
        };
    }
    @VerController
    @XPExecutor(methodID = "hook_3")
    public BaseXPExecutor worker_3(){
        return param -> {
            if (IsAvailable()) {
                List<String> l = (List) param.args[1];
                for (String str : l) {
                    if (str.toLowerCase(Locale.ROOT).endsWith(".mp4")) continue;
                    AddToPreSendList(str);
                }
                param.setResult(true);
            }
        };
    }


    public static void AddToPreSendList(String LocalPath) {
        String cookie = Integer.toString(LocalPath.hashCode());
        picCookies.put(cookie, LocalPath);
        AddToEditText(cookie);
    }

    public static boolean IsAvailable() {
        return IsNowReplying() && (QQSessionUtils.getSessionID() == 1 || QQSessionUtils.getSessionID() == 0);
    }

    private static void AddToEditText(String Cookie) {
        if (ed != null) {
            String Text = "[PicCookie=" + Cookie + "]";
            int pos = ed.getSelectionStart();
            Editable e = ed.getText();
            e.insert(pos, Text);
            ed.setText(e);
            ed.setSelection(pos + Text.length());
        }
    }

    static EditText ed = null;
    static Object chatPie = null;
    public static boolean IsNowReplying() {
        try {
            Object HelperProvider = MField.GetFirstField(chatPie, MClass.loadClass("com.tencent.mobileqq.activity.aio.helper.HelperProvider"));
            Method IsNowReplyingMethod = MMethod.FindMethod(HelperProvider.getClass(),null,MClass.loadClass("com.tencent.mobileqq.activity.aio.helper.IHelper"),new Class[]{int.class});
            Object ReplyHelper = IsNowReplyingMethod.invoke(HelperProvider, 119);

            Object SourceInfo = MMethod.CallMethod(ReplyHelper, null, MClass.loadClass("com.tencent.mobileqq.data.MessageForReplyText$SourceMsgInfo"), new Class[0]);
            return SourceInfo != null;
        } catch (Exception e) {
            LogUtils.error("IsNowReplying", e);
            return false;
        }

    }
}
