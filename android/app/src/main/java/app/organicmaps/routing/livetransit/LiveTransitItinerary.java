package app.organicmaps.routing.livetransit;

import androidx.annotation.NonNull;
import app.organicmaps.sdk.routing.LiveTransitLeg;

/**
 * One selectable itinerary option returned by {@link SwissTransitApi}. Several of these are shown
 * as a list; the user picks one to render via {@link app.organicmaps.sdk.routing.LiveTransitRoute#show}.
 */
public final class LiveTransitItinerary
{
  /** As returned by the API, e.g. "00d00:34:00". */
  @NonNull
  public final String mDuration;
  public final int mTransfers;
  @NonNull
  public final LiveTransitLeg[] mLegs;
  /** The largest live delay (seconds) reported across this itinerary's legs, 0 if on schedule. */
  public final double mMaxDelaySeconds;

  public LiveTransitItinerary(@NonNull String duration, int transfers, @NonNull LiveTransitLeg[] legs,
                              double maxDelaySeconds)
  {
    mDuration = duration;
    mTransfers = transfers;
    mLegs = legs;
    mMaxDelaySeconds = maxDelaySeconds;
  }
}
