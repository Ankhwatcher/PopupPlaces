package ie.appz.popupplaces;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.google.android.maps.GeoPoint;

public class PlaceOpenHelper extends SQLiteOpenHelper {

	private static final String DATABASE_NAME = "placetable.db";
	private static final int DATABASE_VERSION = 1;

	public static final String PLACE_TABLE_NAME = "place_table";
	public static final String COLUMN_ID = "_id";
	public static final String LATITUDE = "latitude";
	public static final String LONGITUDE = "longitude";
	public static final String POPUP_TEXT = "popup_text";
	private static final String CREATE_PLACE_TABLE = "create table " + PLACE_TABLE_NAME + "(" + COLUMN_ID + " integer primary key autoincrement, " + LATITUDE
			+ " real," + LONGITUDE + " real," + POPUP_TEXT + " text not null" + ");";

	public PlaceOpenHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(CREATE_PLACE_TABLE);

	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		Log.w(PlaceOpenHelper.class.getName(), "Upgrading database from version " + oldVersion + " to " + newVersion + ", which will destroy all old data");
		db.execSQL("DROP TABLE IF EXISTS " + PLACE_TABLE_NAME);
		onCreate(db);
	}

	public void addPlace(GeoPoint geoPoint, String popupText) {
		SQLiteDatabase db = getWritableDatabase();
		db.execSQL("insert into " + PLACE_TABLE_NAME + "(" + LATITUDE + ", " + LONGITUDE + ", " + POPUP_TEXT + ") " + "values(" + geoPoint.getLatitudeE6()
				+ ", " + geoPoint.getLongitudeE6() + ", '" + popupText + "');");
		db.close();
	}

	public void deletePlace(GeoPoint geoPoint, String popupText) {
		SQLiteDatabase db = getWritableDatabase();

		Log.i(PlaceOpenHelper.class.getName(), "Deleting Popup Place at " + geoPoint.toString());
		db.delete(PLACE_TABLE_NAME, LATITUDE + " = " + geoPoint.getLatitudeE6() + " AND " + LONGITUDE + " = " + geoPoint.getLongitudeE6() + " AND "
				+ POPUP_TEXT + " = '" + popupText + "'", null);

		db.close();

	}

	public int numberOfPlaces() {
		SQLiteDatabase db = getReadableDatabase();
		String[] columns = new String[] { LATITUDE, COLUMN_ID };
		
		Cursor c = db.query(PLACE_TABLE_NAME, columns, columns[0] + " IS NOT NULL ", null, null, null, COLUMN_ID);
		return c.getCount();
	}

	public Cursor getPlaces() {
		SQLiteDatabase db = getReadableDatabase();

		return db.query(PLACE_TABLE_NAME, new String[] { LATITUDE, LONGITUDE, POPUP_TEXT, COLUMN_ID }, null, null, null, null, COLUMN_ID);
	}

	public String getPopupText(GeoPoint geoPoint) {
		SQLiteDatabase db = getReadableDatabase();
		Cursor c = db.query(PLACE_TABLE_NAME, new String[] { POPUP_TEXT, COLUMN_ID }, LATITUDE + " = " + geoPoint.getLatitudeE6() + " AND " + LONGITUDE + " = "
				+ geoPoint.getLongitudeE6(), null, null, null, COLUMN_ID);
		if (c.moveToFirst())
			return c.getString(0);
		else
			return null;
	}
}
