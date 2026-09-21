package com.consolekey.android;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public class PersonalLanguageModel extends SQLiteOpenHelper {
    private static final String DB_NAME =
            "console_keyboard_language.db";

    private static final int DB_VERSION = 1;

    private final Locale pt =
            new Locale("pt", "BR");

    public PersonalLanguageModel(Context context) {
        super(
                context.getApplicationContext(),
                DB_NAME,
                null,
                DB_VERSION
        );
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE word_freq (" +
                "word TEXT PRIMARY KEY," +
                "count INTEGER NOT NULL DEFAULT 0" +
                ")"
        );

        db.execSQL(
                "CREATE TABLE bigram (" +
                "prev TEXT NOT NULL," +
                "word TEXT NOT NULL," +
                "count INTEGER NOT NULL DEFAULT 0," +
                "PRIMARY KEY(prev, word)" +
                ")"
        );

        db.execSQL(
                "CREATE TABLE trigram (" +
                "prev2 TEXT NOT NULL," +
                "prev1 TEXT NOT NULL," +
                "word TEXT NOT NULL," +
                "count INTEGER NOT NULL DEFAULT 0," +
                "PRIMARY KEY(prev2, prev1, word)" +
                ")"
        );

        db.execSQL(
                "CREATE INDEX idx_bigram_prev_count " +
                "ON bigram(prev, count DESC)"
        );

        db.execSQL(
                "CREATE INDEX idx_trigram_prev_count " +
                "ON trigram(prev2, prev1, count DESC)"
        );
    }

    @Override public void onUpgrade(
            SQLiteDatabase db,
            int oldVersion,
            int newVersion
    ) {}

    private String clean(String word) {
        if (word == null) return "";

        return word
                .trim()
                .toLowerCase(pt);
    }

    public void learn(
            String prev2,
            String prev1,
            String word
    ) {
        word = clean(word);
        prev1 = clean(prev1);
        prev2 = clean(prev2);

        if (word.length() < 2) {
            return;
        }

        SQLiteDatabase db =
                getWritableDatabase();

        db.beginTransaction();

        try {
            db.execSQL(
                    "INSERT OR IGNORE INTO word_freq" +
                    "(word,count) VALUES(?,0)",
                    new Object[]{ word }
            );

            db.execSQL(
                    "UPDATE word_freq " +
                    "SET count=count+1 WHERE word=?",
                    new Object[]{ word }
            );

            if (!prev1.isEmpty()) {
                db.execSQL(
                        "INSERT OR IGNORE INTO bigram" +
                        "(prev,word,count) VALUES(?,?,0)",
                        new Object[]{
                                prev1,
                                word
                        }
                );

                db.execSQL(
                        "UPDATE bigram SET count=count+1 " +
                        "WHERE prev=? AND word=?",
                        new Object[]{
                                prev1,
                                word
                        }
                );
            }

            if (!prev2.isEmpty() &&
                    !prev1.isEmpty()) {

                db.execSQL(
                        "INSERT OR IGNORE INTO trigram" +
                        "(prev2,prev1,word,count) " +
                        "VALUES(?,?,?,0)",
                        new Object[]{
                                prev2,
                                prev1,
                                word
                        }
                );

                db.execSQL(
                        "UPDATE trigram SET count=count+1 " +
                        "WHERE prev2=? AND prev1=? AND word=?",
                        new Object[]{
                                prev2,
                                prev1,
                                word
                        }
                );
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public int wordCount(String word) {
        word = clean(word);

        if (word.isEmpty()) {
            return 0;
        }

        try (
                Cursor c =
                        getReadableDatabase().rawQuery(
                                "SELECT count FROM word_freq " +
                                "WHERE word=?",
                                new String[]{ word }
                        )
        ) {
            if (c.moveToFirst()) {
                return c.getInt(0);
            }
        }

        return 0;
    }

    public String[] predictNext(
            String prev2,
            String prev1,
            int limit
    ) {
        prev2 = clean(prev2);
        prev1 = clean(prev1);

        if (limit <= 0) {
            return new String[0];
        }

        Set<String> out =
                new LinkedHashSet<>();

        SQLiteDatabase db =
                getReadableDatabase();

        if (!prev2.isEmpty() &&
                !prev1.isEmpty()) {

            try (
                    Cursor c =
                            db.rawQuery(
                                    "SELECT word FROM trigram " +
                                    "WHERE prev2=? AND prev1=? " +
                                    "ORDER BY count DESC LIMIT ?",
                                    new String[]{
                                            prev2,
                                            prev1,
                                            String.valueOf(limit)
                                    }
                            )
            ) {
                while (c.moveToNext() &&
                        out.size() < limit) {

                    out.add(c.getString(0));
                }
            }
        }

        if (!prev1.isEmpty() &&
                out.size() < limit) {

            try (
                    Cursor c =
                            db.rawQuery(
                                    "SELECT word FROM bigram " +
                                    "WHERE prev=? " +
                                    "ORDER BY count DESC LIMIT ?",
                                    new String[]{
                                            prev1,
                                            String.valueOf(limit * 2)
                                    }
                            )
            ) {
                while (c.moveToNext() &&
                        out.size() < limit) {

                    out.add(c.getString(0));
                }
            }
        }

        if (out.size() < limit) {
            try (
                    Cursor c =
                            db.rawQuery(
                                    "SELECT word FROM word_freq " +
                                    "ORDER BY count DESC LIMIT ?",
                                    new String[]{
                                            String.valueOf(limit * 2)
                                    }
                            )
            ) {
                while (c.moveToNext() &&
                        out.size() < limit) {

                    out.add(c.getString(0));
                }
            }
        }

        return out.toArray(new String[0]);
    }
}
