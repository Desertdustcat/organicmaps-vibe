package app.organicmaps.sdk.routing;

import androidx.annotation.NonNull;

/**
 * One leg of a live public-transport itinerary fetched from an external journey-planner API
 * (e.g. transport.opendata.ch), passed to native to be rendered as its own subroute.
 * <p>
 * Field meaning mirrors routing::LiveTransitLeg (libs/routing/live_transit_route_builder.hpp).
 * Read by JNI (LiveTransitJni.cpp) by field name -- keep field names and MODE_* values in sync
 * with routing::LiveTransitLegMode.
 */
public final class LiveTransitLeg
{
  public static final int MODE_WALK = 0;
  public static final int MODE_TRAM = 1;
  public static final int MODE_BUS = 2;
  public static final int MODE_TRAIN = 3;
  public static final int MODE_OTHER = 4;

  public final int mMode;
  @NonNull
  public final String mLineName;
  @NonNull
  public final String mFromStopName;
  @NonNull
  public final String mToStopName;
  @NonNull
  public final double[] mPointLats;
  @NonNull
  public final double[] mPointLons;
  public final double mStartTimeFromRouteBeginS;
  public final double mEndTimeFromRouteBeginS;
  public final double mDelaySeconds;

  public LiveTransitLeg(int mode, @NonNull String lineName, @NonNull String fromStopName, @NonNull String toStopName,
                        @NonNull double[] pointLats, @NonNull double[] pointLons, double startTimeFromRouteBeginS,
                        double endTimeFromRouteBeginS, double delaySeconds)
  {
    if (pointLats.length != pointLons.length || pointLats.length < 2)
      throw new IllegalArgumentException("A leg needs at least 2 matching lat/lon points");

    mMode = mode;
    mLineName = lineName;
    mFromStopName = fromStopName;
    mToStopName = toStopName;
    mPointLats = pointLats;
    mPointLons = pointLons;
    mStartTimeFromRouteBeginS = startTimeFromRouteBeginS;
    mEndTimeFromRouteBeginS = endTimeFromRouteBeginS;
    mDelaySeconds = delaySeconds;
  }
}
