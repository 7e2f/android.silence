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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.LinkedList;
import java.util.List;


/**
 * Database access helper
 */
public class DatabaseAccessHelper extends SQLiteOpenHelper {
    private static final String TAG = DatabaseAccessHelper.class.getName();
    public static final String DATABASE_NAME = "blacklist.db";
    private static final int DATABASE_VERSION = 1;
    private static volatile DatabaseAccessHelper sInstance = null;

    @Nullable
    public static DatabaseAccessHelper getInstance(Context context) {
        if (sInstance == null) {
            synchronized (DatabaseAccessHelper.class) {
                if (sInstance == null) {
                    if (Permissions.isGranted(context, Permissions.WRITE_EXTERNAL_STORAGE)) {
                        sInstance = new DatabaseAccessHelper(context.getApplicationContext());
                    }
                }
            }
        }
        return sInstance;
    }


    private DatabaseAccessHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        // helper won't create the database file until we first open it
        SQLiteDatabase db = getWritableDatabase();
        // onConfigure isn't calling in android 2.3
        db.execSQL("PRAGMA foreign_keys=ON");
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(JournalTable.Statement.CREATE);
        db.execSQL(ContactTable.Statement.CREATE);
        db.execSQL(ContactNumberTable.Statement.CREATE);
        db.execSQL(SettingsTable.Statement.CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int i, int i1) {
        if (i != i1) {
            db.execSQL("DROP TABLE IF EXISTS " + SettingsTable.NAME);
            db.execSQL("DROP TABLE IF EXISTS " + ContactNumberTable.NAME);
            db.execSQL("DROP TABLE IF EXISTS " + ContactTable.NAME);
            db.execSQL("DROP TABLE IF EXISTS " + JournalTable.NAME);
            onCreate(db);
        }
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.execSQL("PRAGMA foreign_keys=ON");
    }

//----------------------------------------------------------------



    // Closes cursor if it is empty and returns false
    private boolean validate(Cursor cursor) {
        if (cursor == null || cursor.isClosed()) return false;
        if (cursor.getCount() == 0) {
            cursor.close();
            return false;
        }
        return true;
    }



    // Journal table scheme
    private static class JournalTable {
        static final String NAME = "journal";

        static class Column {
            static final String ID = "_id";
            static final String TIME = "time";
            static final String CALLER = "caller";
            static final String NUMBER = "number";
            static final String TEXT = "text";
        }

        static class Statement {
            static final String CREATE =
                    "CREATE TABLE " + JournalTable.NAME +
                            "(" +
                            Column.ID + " INTEGER PRIMARY KEY NOT NULL, " +
                            Column.TIME + " INTEGER NOT NULL, " +
                            Column.CALLER + " TEXT NOT NULL, " +
                            Column.NUMBER + " TEXT, " +
                            Column.TEXT + " TEXT " +
                            ")";
        }
    }

//----------------------------------------------------------------

    // Contact number table scheme
    private static class ContactNumberTable {
        static final String NAME = "number";

        static class Column {
            static final String ID = "_id";
            static final String NUMBER = "number";
            static final String TYPE = "type";
            static final String CONTACT_ID = "contact_id";
        }

        static class Statement {
            static final String CREATE =
                    "CREATE TABLE " + ContactNumberTable.NAME +
                            "(" +
                            Column.ID + " INTEGER PRIMARY KEY NOT NULL, " +
                            Column.NUMBER + " TEXT NOT NULL, " +
                            Column.TYPE + " INTEGER NOT NULL, " +
                            Column.CONTACT_ID + " INTEGER NOT NULL, " +
                            "FOREIGN KEY(" + Column.CONTACT_ID + ") REFERENCES " +
                            ContactTable.NAME + "(" + ContactTable.Column.ID + ")" +
                            " ON DELETE CASCADE " +
                            ")";

            static final String SELECT_BY_CONTACT_ID =
                    "SELECT * " +
                            " FROM " + ContactNumberTable.NAME +
                            " WHERE " + Column.CONTACT_ID + " = ? " +
                            " ORDER BY " + Column.NUMBER +
                            " ASC";

            static final String SELECT_BY_NUMBER =
                    "SELECT * " +
                            " FROM " + ContactNumberTable.NAME +
                            " WHERE (" +
                            Column.TYPE + " = " + ContactNumber.TYPE_EQUALS + " AND " +
                            " ? = " + Column.NUMBER + ") OR (" +
                            Column.TYPE + " = " + ContactNumber.TYPE_STARTS + " AND " +
                            " ? LIKE " + Column.NUMBER + "||'%') OR (" +
                            Column.TYPE + " = " + ContactNumber.TYPE_ENDS + " AND " +
                            " ? LIKE '%'||" + Column.NUMBER + ") OR (" +
                            Column.TYPE + " = " + ContactNumber.TYPE_CONTAINS + " AND " +
                            " ? LIKE '%'||" + Column.NUMBER + "||'%')";
        }
    }

    // ContactsNumber table item
    public static class ContactNumber {
        public static final int TYPE_EQUALS = 0;
        public static final int TYPE_CONTAINS = 1;
        public static final int TYPE_STARTS = 2;
        public static final int TYPE_ENDS = 3;

        public final long id;
        public final String number;
        public final int type;
        public final long contactId;

        public ContactNumber(long id, @NonNull String number, long contactId) {
            this(id, number, TYPE_EQUALS, contactId);
        }

        public ContactNumber(long id, @NonNull String number, int type, long contactId) {
            this.id = id;
            this.number = number;
            this.type = type;
            this.contactId = contactId;
        }
    }

    // ContactsNumber item cursor wrapper
    private class ContactNumberCursorWrapper extends CursorWrapper {
        private final int ID;
        private final int NUMBER;
        private final int TYPE;
        private final int CONTACT_ID;

        ContactNumberCursorWrapper(Cursor cursor) {
            super(cursor);
            cursor.moveToFirst();
            ID = cursor.getColumnIndex(ContactNumberTable.Column.ID);
            NUMBER = cursor.getColumnIndex(ContactNumberTable.Column.NUMBER);
            TYPE = cursor.getColumnIndex(ContactNumberTable.Column.TYPE);
            CONTACT_ID = cursor.getColumnIndex(ContactNumberTable.Column.CONTACT_ID);
        }

        ContactNumber getNumber() {
            long id = getLong(ID);
            String number = getString(NUMBER);
            int type = getInt(TYPE);
            long contactId = getLong(CONTACT_ID);
            return new ContactNumber(id, number, type, contactId);
        }
    }

    // Selects contact numbers by contact id
    @Nullable
    private ContactNumberCursorWrapper getContactNumbersByContactId(long contactId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                ContactNumberTable.Statement.SELECT_BY_CONTACT_ID,
                new String[]{String.valueOf(contactId)});

        return (validate(cursor) ? new ContactNumberCursorWrapper(cursor) : null);
    }

    // Searches contact numbers by number value
    @Nullable
    private ContactNumberCursorWrapper getContactNumbersByNumber(String number) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                ContactNumberTable.Statement.SELECT_BY_NUMBER,
                new String[]{number, number, number, number});

        return (validate(cursor) ? new ContactNumberCursorWrapper(cursor) : null);
    }

    // Searches contact numbers by number value
    private List<ContactNumber> getContactNumbers(String number) {
        List<ContactNumber> list = new LinkedList<>();
        ContactNumberCursorWrapper cursor = getContactNumbersByNumber(number);
        if (cursor != null) {
            do {
                list.add(cursor.getNumber());
            } while (cursor.moveToNext());
            cursor.close();
        }

        return list;
    }

//----------------------------------------------------------------

    // Table of contacts (black/white lists)
    private static class ContactTable {
        static final String NAME = "contact";

        static class Column {
            static final String ID = "_id";
            static final String NAME = "name";
            static final String TYPE = "type"; // black/white type
        }

        static class Statement {
            static final String CREATE =
                    "CREATE TABLE " + ContactTable.NAME +
                            "(" +
                            Column.ID + " INTEGER PRIMARY KEY NOT NULL, " +
                            Column.NAME + " TEXT NOT NULL, " +
                            Column.TYPE + " INTEGER NOT NULL DEFAULT 0 " +
                            ")";

            static final String SELECT_BY_ID =
                    "SELECT * " +
                            " FROM " + ContactTable.NAME +
                            " WHERE " + Column.ID + " = ? ";
        }
    }

    // The contact
    public static class Contact {
//        public static final int TYPE_BLACK_LIST = 1;
        public static final int TYPE_WHITE_LIST = 2;

        public final long id;
        public final String name;
        public final int type;
        public final List<ContactNumber> numbers;

        Contact(long id, @NonNull String name, int type, @NonNull List<ContactNumber> numbers) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.numbers = numbers;
        }
    }

    // Source of the contact
    public interface ContactSource {
        Contact getContact();
    }

    // Contact cursor wrapper
    public class ContactCursorWrapper extends CursorWrapper implements ContactSource {
        private final int ID;
        private final int NAME;
        private final int TYPE;

        ContactCursorWrapper(Cursor cursor) {
            super(cursor);
            cursor.moveToFirst();
            ID = cursor.getColumnIndex(ContactTable.Column.ID);
            NAME = getColumnIndex(ContactTable.Column.NAME);
            TYPE = getColumnIndex(ContactTable.Column.TYPE);
        }

        @Override
        public Contact getContact() {
            return getContact(true);
        }

        Contact getContact(boolean withNumbers) {
            long id = getLong(ID);
            String name = getString(NAME);
            int type = getInt(TYPE);

            List<ContactNumber> numbers = new LinkedList<>();
            if (withNumbers) {
                ContactNumberCursorWrapper cursor = getContactNumbersByContactId(id);
                if (cursor != null) {
                    do {
                        numbers.add(cursor.getNumber());
                    } while (cursor.moveToNext());
                    cursor.close();
                }
            }

            return new Contact(id, name, type, numbers);
        }
    }

    // Searches contact by id
    @Nullable
    public ContactCursorWrapper getContact(long contactId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                ContactTable.Statement.SELECT_BY_ID,
                new String[]{String.valueOf(contactId)});

        return (validate(cursor) ? new ContactCursorWrapper(cursor) : null);
    }

    // Searches contacts by contact numbers (retrieving them by ContactNumber.contactId)
    private List<Contact> getContacts(List<ContactNumber> numbers, boolean withNumbers) {
        List<Contact> contacts = new LinkedList<>();
        for (ContactNumber contactNumber : numbers) {
            ContactCursorWrapper cursor = getContact(contactNumber.contactId);
            if (cursor != null) {
                contacts.add(cursor.getContact(withNumbers));
                cursor.close();
            }
        }
        return contacts;
    }

    // Searches contacts by contact number
    public List<Contact> getContacts(String number, boolean withNumbers) {
        List<ContactNumber> numbers = getContactNumbers(number);
        return getContacts(numbers, withNumbers);
    }





//----------------------------------------------------------------

    // Table of settings
    private static class SettingsTable {
        static final String NAME = "settings";

        static class Column {
            static final String ID = "_id";
            static final String NAME = "name";
            static final String VALUE = "value";
        }

        static class Statement {
            static final String CREATE =
                    "CREATE TABLE " + SettingsTable.NAME +
                            "(" +
                            Column.ID + " INTEGER PRIMARY KEY NOT NULL, " +
                            Column.NAME + " TEXT NOT NULL, " +
                            Column.VALUE + " TEXT " +
                            ")";

            static final String SELECT_BY_NAME =
                    "SELECT * " +
                            " FROM " + SettingsTable.NAME +
                            " WHERE " + Column.NAME + " = ? ";
        }
    }

    // Settings item
    private class SettingsItem {
        final long id;
        final String name;
        final String value;

        SettingsItem(long id, String name, String value) {
            this.id = id;
            this.name = name;
            this.value = value;
        }
    }

    // SettingsItem cursor wrapper
    private class SettingsItemCursorWrapper extends CursorWrapper {
        private final int ID;
        private final int NAME;
        private final int VALUE;

        SettingsItemCursorWrapper(Cursor cursor) {
            super(cursor);
            cursor.moveToFirst();
            ID = cursor.getColumnIndex(SettingsTable.Column.ID);
            NAME = cursor.getColumnIndex(SettingsTable.Column.NAME);
            VALUE = cursor.getColumnIndex(SettingsTable.Column.VALUE);
        }

        SettingsItem getSettings() {
            long id = getLong(ID);
            String name = getString(NAME);
            String value = getString(VALUE);
            return new SettingsItem(id, name, value);
        }
    }

    // Selects settings by name
    @Nullable
    private SettingsItemCursorWrapper getSettings(@NonNull String name) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                SettingsTable.Statement.SELECT_BY_NAME,
                new String[]{name});

        return (validate(cursor) ? new SettingsItemCursorWrapper(cursor) : null);
    }

    // Selects value of settings by name
    @Nullable
    public String getSettingsValue(@NonNull String name) {
        SettingsItemCursorWrapper cursor = getSettings(name);
        if (cursor != null) {
            SettingsItem item = cursor.getSettings();
            cursor.close();
            return item.value;
        }
        return null;
    }

    // Sets value of settings with specified name
    public boolean setSettingsValue(@NonNull String name, @NonNull String value) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(SettingsTable.Column.VALUE, value);
        // try to update value
        int n = db.update(SettingsTable.NAME,
                values,
                SettingsTable.Column.NAME + " = ? ",
                new String[]{name});
        if (n == 0) {
            // try to add name/value
            values.put(SettingsTable.Column.NAME, name);
            return db.insert(SettingsTable.NAME, null, values) >= 0;
        }

        return true;
    }
}
