package ie.appz.popupplaces;

import android.location.Location;

/* FireableLocation extends the Location class to have a boolean value mHasFired 
 * which is accessed with the functions isFired() and setFired().
 * */

public class FireableLocation extends Location {
	private boolean mHasFired = false;

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
}
