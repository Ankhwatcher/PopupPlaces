package ie.appz.popupplaces;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;


public class PlaceOpenHelper extends SQLiteOpenHelper {

    /* Shared Preferences Settings */
    public static final String PREFS_NAME = "popupplaces_prefs";
    public static final String READ_ALOUD_ENABLED = "readaloud_enabled";
    public static final String SERVICE_DISABLED = "service_disabled";
    public static final String FIRST_RUN = "first_run";
    public static final String POP_RADIUS = "pop_radius";
    public static final String PLACE_TABLE_NAME = "place_table";
    public static final String COLUMN_ID = "_id";
    public static final String LATITUDE = "latitude";
    public static final String LONGITUDE = "longitude";
    public static final String POPUP_TEXT = "popup_text";
    /* Database Settings */
    private static final String DATABASE_NAME = "placetable.db";
    private static final int DATABASE_VERSION = 2;
    private static final String CREATE_PLACE_TABLE = "create table "
            + PLACE_TABLE_NAME + "(" + COLUMN_ID
            + " integer primary key autoincrement, " + LATITUDE + " real,"
            + LONGITUDE + " real," + POPUP_TEXT + " text not null" + ");";

    public PlaceOpenHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public static String sanitizeText(String input) {
        String output = "";
        for (int i = 0; i < input.length(); i++) {
            if (input.charAt(i) == '\'') {
                output += "\'";
            }
            output += input.charAt(i);
        }
        return output;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_PLACE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            Log.w(this.getClass().getName(),
                    "Upgrading table from GeoPoint Values to LatLng Values.");
            class ValHolder {
                public double newLatitude;
                public double newLongitude;

                public int newColumnId;

                public ValHolder(double latitude, double longitude, int columnID) {
                    this.newLatitude = latitude;
                    if (this.newLatitude > 90 || this.newLatitude < -90) {
                        this.newLatitude = this.newLatitude / 1E6;
                        Log.v(this.getClass().getName(),
                                "Latitude upgraded from " + latitude + " to "
                                        + this.newLatitude);
                    }
                    this.newLongitude = longitude;
                    if (this.newLongitude > 90 || this.newLongitude < -90) {
                        this.newLongitude = this.newLongitude / 1E6;
                        Log.v(this.getClass().getName(),
                                "Longitude upgraded from " + longitude + " to "
                                        + this.newLongitude);
                    }

                    this.newColumnId = columnID;
                }
            }
            String[] columns = new String[]{LATITUDE, LONGITUDE, POPUP_TEXT,
                    COLUMN_ID};

            Cursor c = db.query(PLACE_TABLE_NAME, columns, columns[0]
                    + " IS NOT NULL ", null, null, null, COLUMN_ID);

            ArrayList<ValHolder> valArray = new ArrayList<ValHolder>();

            if (c.moveToFirst()) {
                do {
                    valArray.add(new ValHolder(c.getDouble(0), c.getDouble(1),
                            c.getInt(3)));

                } while (c.moveToNext());
            }
            for (ValHolder valHolder : valArray) {
                ContentValues values = new ContentValues();
                values.put(LATITUDE, valHolder.newLatitude);
                values.put(LONGITUDE, valHolder.newLongitude);
                db.update(PLACE_TABLE_NAME, values, COLUMN_ID + " = "
                        + valHolder.newColumnId, null);
            }
            valArray.clear();
            c.close();
        }
        /*
         * Log.w(this.getClass().getName(), "Upgrading database from version " +
		 * oldVersion + " to " + newVersion +
		 * ", which will destroy all old data");
		 * db.execSQL("DROP TABLE IF EXISTS " + PLACE_TABLE_NAME); onCreate(db);
		 */
    }

    public void addPlace(LatLng latLngPoint, String popupText) {
        SQLiteDatabase db = getWritableDatabase();
        String insertStatement = "insert into " + PLACE_TABLE_NAME + "("
                + LATITUDE + ", " + LONGITUDE + ", " + POPUP_TEXT + ") "
                + "values(" + latLngPoint.latitude + ", "
                + latLngPoint.longitude + ", '" + sanitizeText(popupText)
                + "');";
        if (db != null) {
            db.execSQL(insertStatement);
            db.close();
        }
    }

    public void editPlace(LatLng latLngPoint, String popupText) {
        SQLiteDatabase db = getWritableDatabase();
        String whereClause = LATITUDE + " = " + latLngPoint.latitude + " AND "
                + LONGITUDE + " = " + latLngPoint.longitude;
        ContentValues values = new ContentValues();
        values.put(POPUP_TEXT, sanitizeText(popupText));
        if ((db != null ? db.update(PLACE_TABLE_NAME, values, whereClause, null) : 0) == 0) {
            if (db != null) {
                db.close();
            }
            addPlace(latLngPoint, popupText);
        } else {
            db.close();
        }
    }

    public void editPlace(int Id, String popupText) {
        SQLiteDatabase db = getWritableDatabase();
        String whereClause = COLUMN_ID + " = " + Id;
        ContentValues values = new ContentValues();
        values.put(POPUP_TEXT, sanitizeText(popupText));
        if (db != null) {
            db.update(PLACE_TABLE_NAME, values, whereClause, null);
        }
        if (db != null) {
            db.close();
        }
    }

    public void movePlace(int Id, LatLng newLatLng) {
        SQLiteDatabase db = getWritableDatabase();
        String whereClause = COLUMN_ID + " = " + Id;
        ContentValues values = new ContentValues();
        values.put(LATITUDE, newLatLng.latitude);
        values.put(LONGITUDE, newLatLng.longitude);
        if (db != null) {
            db.update(PLACE_TABLE_NAME, values, whereClause, null);
        }
        if (db != null) {
            db.close();
        }
    }

    /*
     * public int deletePlace(GeoPoint geoPoint, String popupText) {
     * SQLiteDatabase db = getWritableDatabase();
     *
     * Log.i(this.getClass().getName(), "Deleting Popup Place at " +
     * geoPoint.toString()); int deleteResult = db.delete(PLACE_TABLE_NAME,
     * LATITUDE + " = " + (double) (geoPoint.getLatitudeE6()) / 1E6 + " AND " +
     * LONGITUDE + " = " + (double) (geoPoint.getLongitudeE6()) / 1E6 + " AND "
     * + POPUP_TEXT + " = '" + sanitizeText(popupText) + "'", null);
     *
     * db.close(); return deleteResult;
     *
     * }
     *
     * public void deletePlace(LatLng position, String popupText) {
     * SQLiteDatabase db = getWritableDatabase(); String whereClause = LATITUDE
     * + " = " + (int) (position.latitude * 1E6) + " AND " + LONGITUDE + " = " +
     * (int) (position.longitude * 1E6) + " AND " + POPUP_TEXT + " = '" +
     * sanitizeText(popupText) + "'"; Log.i(this.getClass().getName(),
     * "Deleting Popup Places at " + position.toString() + ", number removed: "
     * + db.delete(PLACE_TABLE_NAME, whereClause, null));
     *
     * db.close();
     *
     * }
     */
    public boolean deletePlace(int columnId) {
        SQLiteDatabase db = getWritableDatabase();
        String whereClause = COLUMN_ID + " = " + columnId;
        Log.i(this.getClass().getName(), "Deleting Popup Places number: "
                + columnId);
        boolean success = ((db != null ? db.delete(PLACE_TABLE_NAME, whereClause, null) : 0) != 0);
        if (db != null) {
            db.close();
        }
        return success;
    }

    public int numberOfPlaces() {
        SQLiteDatabase db = getReadableDatabase();
        String[] columns = new String[]{LATITUDE, COLUMN_ID};

        Cursor c = db != null ? db.query(PLACE_TABLE_NAME, columns, columns[0]
                + " IS NOT NULL ", null, null, null, COLUMN_ID) : null;
        int placesNo = c != null ? c.getCount() : 0;
        if (c != null) {
            c.close();
        }
        return placesNo;
    }

    public Cursor getPlaces() {
        SQLiteDatabase db = getReadableDatabase();

        return db != null ? db.query(PLACE_TABLE_NAME, new String[]{LATITUDE, LONGITUDE,
                POPUP_TEXT, COLUMN_ID}, null, null, null, null, COLUMN_ID) : null;
    }

    public String getPopupText(LatLng latLng) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db != null ? db.query(PLACE_TABLE_NAME, new String[]{POPUP_TEXT,
                COLUMN_ID}, LATITUDE + " = " + latLng.latitude + " AND "
                + LONGITUDE + " = " + latLng.longitude, null, null, null,
                COLUMN_ID) : null;
        if (c != null && c.moveToFirst()) {
            String popupText = c.getString(0);
            return popupText;
        } else {
            c.close();
            return null;
        }
    }

    public String getPopupText(int column_ID) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db != null ? db.query(PLACE_TABLE_NAME, new String[]{POPUP_TEXT,
                COLUMN_ID}, COLUMN_ID + " = " + column_ID, null, null, null,
                COLUMN_ID) : null;
        if (c != null && c.moveToFirst()) {
            String popupText = c.getString(0);
            c.close();
            return popupText;
        } else {
            c.close();
            return null;
        }
    }

}
