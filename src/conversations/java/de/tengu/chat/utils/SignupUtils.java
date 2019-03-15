package de.tengu.chat.utils;

import android.app.Activity;
import android.content.Intent;

import de.tengu.chat.Config;
import de.tengu.chat.entities.Account;
import de.tengu.chat.services.XmppConnectionService;
import de.tengu.chat.ui.ConversationsActivity;
import de.tengu.chat.ui.EditAccountActivity;
import de.tengu.chat.ui.ManageAccountActivity;
import de.tengu.chat.ui.StartConversationActivity;
import de.tengu.chat.ui.WelcomeActivity;

public class SignupUtils {

    public static Intent getSignUpIntent(final Activity activity) {
        Intent intent = new Intent(activity, WelcomeActivity.class);
        StartConversationActivity.addInviteUri(intent, activity.getIntent());
        return intent;
    }

    public static Intent getRedirectionIntent(final ConversationsActivity activity) {
        final XmppConnectionService service = activity.xmppConnectionService;
        Account pendingAccount = AccountUtils.getPendingAccount(service);
        Intent intent;
        if (pendingAccount != null) {
            intent = new Intent(activity, EditAccountActivity.class);
            intent.putExtra("jid", pendingAccount.getJid().asBareJid().toString());
        } else {
            if (service.getAccounts().size() == 0) {
                if (Config.X509_VERIFICATION) {
                    intent = new Intent(activity, ManageAccountActivity.class);
                } else if (Config.MAGIC_CREATE_DOMAIN != null) {
                    intent = getSignUpIntent(activity);
                } else {
                    intent = new Intent(activity, EditAccountActivity.class);
                }
            } else {
                intent = new Intent(activity, StartConversationActivity.class);
            }
        }
        intent.putExtra("init", true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return intent;
    }
}