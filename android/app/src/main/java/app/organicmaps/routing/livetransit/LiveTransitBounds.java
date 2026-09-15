package app.organicmaps.routing.livetransit;

/**
 * A light UX guard, not a hard architectural constraint: transport.opendata.ch only has useful
 * data for Switzerland (plus a thin cross-border fringe served by Swiss operators), so the "Live
 * transit" entry point is only offered when both endpoints look Swiss -- rather than letting the
 * user pick it for a trip the API can't answer.
 */
public final class LiveTransitBounds
{
  private LiveTransitBounds() {}

  private static final double MIN_LAT = 45.8;
  private static final double MAX_LAT = 47.9;
  private static final double MIN_LON = 5.9;
  private static final double MAX_LON = 10.5;

  public static boolean contains(double lat, double lon)
  {
    return lat >= MIN_LAT && lat <= MAX_LAT && lon >= MIN_LON && lon <= MAX_LON;
  }
}
