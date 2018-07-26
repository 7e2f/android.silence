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

package org.ch.silence.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Contacts/SMS/Calls list access helper
 */
public class ContactsAccessHelper {
    private static volatile ContactsAccessHelper sInstance = null;
    private ContentResolver contentResolver = null;

    private ContactsAccessHelper(Context context) {
        contentResolver = context.getApplicationContext().getContentResolver();
    }

    public static ContactsAccessHelper getInstance(Context context) {
        if (sInstance == null) {
            synchronized (ContactsAccessHelper.class) {
                if (sInstance == null) {
                    sInstance = new ContactsAccessHelper(context);
                }
            }
        }
        return sInstance;
    }

    private boolean validate(Cursor cursor) {
        if (cursor == null || cursor.isClosed()) return false;
        if (cursor.getCount() == 0) {
            cursor.close();
            return false;
        }
        return true;
    }

    // Selects contact from contacts list by phone number
    @Nullable
    private ContactCursorWrapper getContactCursor(String number) {
        Uri lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number));
        Cursor cursor = contentResolver.query(lookupUri,
                new String[]{Contacts._ID, Contacts.DISPLAY_NAME},
                null,
                null,
                null);

        return (validate(cursor) ? new ContactCursorWrapper(cursor) : null);
    }

    @Nullable
    private DatabaseAccessHelper.Contact getContact(String number) {
        DatabaseAccessHelper.Contact contact = null;
        ContactCursorWrapper cursor = getContactCursor(number);
        if (cursor != null) {
            contact = cursor.getContact(false);
            cursor.close();
        }

        return contact;
    }

    @Nullable
    public DatabaseAccessHelper.Contact getContact(Context context, String number) {
        if (!Permissions.isGranted(context, Permissions.READ_CONTACTS)) {
            return null;
        }

        return getContact(number);
    }

    // Contact's cursor wrapper
    private class ContactCursorWrapper extends CursorWrapper implements DatabaseAccessHelper.ContactSource {
        private final int ID;
        private final int NAME;

        private ContactCursorWrapper(Cursor cursor) {
            super(cursor);
            cursor.moveToFirst();
            ID = getColumnIndex(Contacts._ID);
            NAME = getColumnIndex(Contacts.DISPLAY_NAME);
        }

        @Override
        public DatabaseAccessHelper.Contact getContact() {
            return getContact(true);
        }

        DatabaseAccessHelper.Contact getContact(boolean withNumbers) {
            long id = getLong(ID);
            String name = getString(NAME);
            List<DatabaseAccessHelper.ContactNumber> numbers = new LinkedList<>();
            if (withNumbers) {
                ContactNumberCursorWrapper cursor = getContactNumbers(id);
                if (cursor != null) {
                    do {
                        // normalize the phone number (remove spaces and brackets)
                        String number = normalizePhoneNumber(cursor.getNumber());
                        // create and add contact number instance
                        DatabaseAccessHelper.ContactNumber contactNumber =
                                new DatabaseAccessHelper.ContactNumber(cursor.getPosition(), number, id);
                        numbers.add(contactNumber);
                    } while (cursor.moveToNext());
                    cursor.close();
                }
            }

            return new DatabaseAccessHelper.Contact(id, name, 0, numbers);
        }
    }

    // Contact's number cursor wrapper
    private static class ContactNumberCursorWrapper extends CursorWrapper {
        private final int NUMBER;

        private ContactNumberCursorWrapper(Cursor cursor) {
            super(cursor);
            cursor.moveToFirst();
            NUMBER = cursor.getColumnIndex(Phone.NUMBER);
        }

        String getNumber() {
            return getString(NUMBER);
        }
    }

    // Selects all numbers of specified contact
    @Nullable
    private ContactNumberCursorWrapper getContactNumbers(long contactId) {
        Cursor cursor = contentResolver.query(
                Phone.CONTENT_URI,
                new String[]{Phone.NUMBER},
                Phone.NUMBER + " IS NOT NULL AND " +
                        Phone.CONTACT_ID + " = " + contactId,
                null,
                null);

        return (validate(cursor) ? new ContactNumberCursorWrapper(cursor) : null);
    }

//--------------------------------------------------------------------------------

    // For the sake of performance we don't use comprehensive phone number pattern.
    // We just want to detect whether a phone number is digital but not symbolic.
    private static final Pattern digitalPhoneNumberPattern = Pattern.compile("[+]?[0-9-() ]+");
    // Is used for normalizing a phone number, removing from it brackets, dashes and spaces.
    private static final Pattern normalizePhoneNumberPattern = Pattern.compile("[-() ]");

    /**
     * If passed phone number is digital and not symbolic then normalizes
     * it, removing brackets, dashes and spaces.
     */
    public static String normalizePhoneNumber(@NonNull String number) {
        number = number.trim();
        if (digitalPhoneNumberPattern.matcher(number).matches()) {
            number = normalizePhoneNumberPattern.matcher(number).replaceAll("");
        }
        return number;
    }

    /**
     * Checks whether passed phone number is private
     */
    public static boolean isPrivatePhoneNumber(@Nullable String number) {
        try {
            if (number == null) {
                return true;
            }
            number = number.trim();
            if (number.isEmpty() || Long.valueOf(number) < 0) {
                return true;
            }
        } catch (NumberFormatException ignored) {
        }
        return false;
    }
}
