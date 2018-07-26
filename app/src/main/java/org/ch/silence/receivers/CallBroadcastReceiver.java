/*
 * Copyright (C) 2017 Anton Kaliturin <kaliturin@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.ch.silence.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.Nullable;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.ITelephony;
import org.ch.silence.R;
import org.ch.silence.utils.ContactsAccessHelper;
import org.ch.silence.utils.DatabaseAccessHelper;
import org.ch.silence.utils.Permissions;
import org.ch.silence.utils.Settings;

import java.lang.reflect.Method;
import java.util.List;

/**
 * BroadcastReceiver for calls catching
 */
public class CallBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = CallBroadcastReceiver.class.getName();

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (!Permissions.isGranted(context, Permissions.READ_PHONE_STATE) ||
                !Permissions.isGranted(context, Permissions.CALL_PHONE)) {
            return;
        }

        // get telephony service
        TelephonyManager telephony = (TelephonyManager)
                context.getSystemService(Context.TELEPHONY_SERVICE);
        if (telephony.getCallState() != TelephonyManager.CALL_STATE_RINGING) {
            return;
        }

        // get incoming call number
        String number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);

        // private number detected
        if (ContactsAccessHelper.isPrivatePhoneNumber(number)) {
            // if block private numbers
            if (Settings.getBooleanValue(context, Settings.ENABLE_WHITELIST)) {
                String name = context.getString(R.string.Private_number);
                // break call and notify user
                breakCall(context);
            }
            return;
        }

        // normalize number
        number = ContactsAccessHelper.normalizePhoneNumber(number);
        if (number.isEmpty()) {
            Log.w(TAG, "Received call address is empty");
            return;
        }

        // get contacts linked to the current number
        List<DatabaseAccessHelper.Contact> contacts = getContacts(context, number);
        if (contacts == null) {
            return;
        }

        // if contact is from the white list
        DatabaseAccessHelper.Contact contact = findContactByType(contacts, DatabaseAccessHelper.Contact.TYPE_WHITE_LIST);
        if (contact != null) {
            return;
        }

        // get name of contact
        String name = (contacts.size() > 0 ? contacts.get(0).name : null);

        boolean abort = false;

        // if block numbers that are not in the contact list
        if (Settings.getBooleanValue(context, Settings.ENABLE_WHITELIST) &&
                Permissions.isGranted(context, Permissions.READ_CONTACTS)) {
            ContactsAccessHelper db = ContactsAccessHelper.getInstance(context);
            if (db.getContact(context, number) != null) {
                return;
            }
            breakCall(context);
        }

    }

    // Ends phone call
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void breakCall(Context context) {
        if (!Permissions.isGranted(context, Permissions.CALL_PHONE)) {
            return;
        }

        TelephonyManager telephony = (TelephonyManager)
                context.getSystemService(Context.TELEPHONY_SERVICE);
        try {
            Class c = Class.forName(telephony.getClass().getName());
            Method m = c.getDeclaredMethod("getITelephony");
            m.setAccessible(true);
            ITelephony telephonyService = (ITelephony) m.invoke(telephony);
            telephonyService.endCall();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Finds contact by type
    private DatabaseAccessHelper.Contact findContactByType(List<DatabaseAccessHelper.Contact> contacts, int contactType) {
        for (DatabaseAccessHelper.Contact contact : contacts) {
            if (contact.type == contactType) {
                return contact;
            }
        }
        return null;
    }

    // Finds contacts by number
    @Nullable
    private List<DatabaseAccessHelper.Contact> getContacts(Context context, String number) {
        DatabaseAccessHelper db = DatabaseAccessHelper.getInstance(context);
        return (db == null ? null : db.getContacts(number, false));
    }


}
