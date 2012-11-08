package ie.appz.popupplaces;

import android.location.Location;

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
