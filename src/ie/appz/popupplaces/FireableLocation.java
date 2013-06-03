package ie.appz.popupplaces;

import android.location.Location;

/* FireableLocation extends the Location class to have a boolean value mHasFired 
 * which is accessed with the functions isFired() and setFired().
 * */

public class FireableLocation extends Location {
	private boolean mHasFired = false;
	private int columnId;

	public FireableLocation(String s) {
		super(s);

	}

	/*
	 * Returns the fired status of this fix.
	 */
	public boolean isFired() {
		return mHasFired;
	}

	/*
	 * Sets the fired status of this fix.
	 */
	public void setFired(boolean hasFired) {
		mHasFired = hasFired;
	}

	/*
	 * Returns the ColumnId associated with this location.
	 */
	public int getColumnId() {
		return columnId;
	}

	/*
	 * Sets the ColumnId associated with this location.
	 */
	public void setColumnId(int columnId) {
		this.columnId = columnId;
	}
}
