package app.organicmaps.routing.livetransit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.sdk.routing.LiveTransitLeg;
import app.organicmaps.sdk.util.concurrency.UiThread;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Client for the free, keyless Swiss public-transport journey planner API at
 * https://transport.opendata.ch (built on SBB/search.ch timetable + realtime data).
 * Switzerland-only by nature of the underlying data -- see {@link LiveTransitBounds} for the
 * UI-level guard.
 */
public final class SwissTransitApi
{
  private static final String BASE_URL = "https://transport.opendata.ch/v1/connections";
  private static final int RESULT_LIMIT = 4;

  public interface ResultCallback
  {
    void onSuccess(@NonNull List<LiveTransitItinerary> itineraries);
    void onFailure(@NonNull String message);
  }

  private final OkHttpClient mClient = new OkHttpClient();

  /**
   * @param dateTime the requested departure or arrival instant, per isArrivalTime.
   */
  public void findConnections(@NonNull String from, @NonNull String to, @NonNull Date dateTime, boolean isArrivalTime,
                              @NonNull ResultCallback callback)
  {
    final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.US);

    final HttpUrl url = Objects.requireNonNull(HttpUrl.parse(BASE_URL))
                            .newBuilder()
                            .addQueryParameter("from", from)
                            .addQueryParameter("to", to)
                            .addQueryParameter("date", dateFormat.format(dateTime))
                            .addQueryParameter("time", timeFormat.format(dateTime))
                            .addQueryParameter("isArrivalTime", isArrivalTime ? "1" : "0")
                            .addQueryParameter("limit", String.valueOf(RESULT_LIMIT))
                            .build();

    mClient.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
      @Override
      public void onFailure(@NonNull Call call, @NonNull IOException e)
      {
        UiThread.run(() -> callback.onFailure(e.getMessage() == null ? "Network error" : e.getMessage()));
      }

      @Override
      public void onResponse(@NonNull Call call, @NonNull Response response)
      {
        try (Response ignored = response)
        {
          if (!response.isSuccessful() || response.body() == null)
          {
            final int code = response.code();
            UiThread.run(() -> callback.onFailure("HTTP " + code));
            return;
          }
          final List<LiveTransitItinerary> itineraries = parseConnections(response.body().string());
          UiThread.run(() -> callback.onSuccess(itineraries));
        }
        catch (IOException | JSONException e)
        {
          final String message = e.getMessage() == null ? "Malformed response" : e.getMessage();
          UiThread.run(() -> callback.onFailure(message));
        }
      }
    });
  }

  @NonNull
  private static List<LiveTransitItinerary> parseConnections(@NonNull String json) throws JSONException
  {
    final JSONArray connections = new JSONObject(json).getJSONArray("connections");
    final List<LiveTransitItinerary> result = new ArrayList<>(connections.length());
    for (int i = 0; i < connections.length(); ++i)
    {
      try
      {
        result.add(parseConnection(connections.getJSONObject(i)));
      }
      catch (JSONException | ParseException e)
      {
        // Skip a single malformed connection rather than failing the whole result set -- this is
        // a live third-party API response, not internal data.
      }
    }
    return result;
  }

  @NonNull
  private static LiveTransitItinerary parseConnection(@NonNull JSONObject connection)
      throws JSONException, ParseException
  {
    final JSONArray sections = connection.getJSONArray("sections");
    final List<LiveTransitLeg> legs = new ArrayList<>();
    // Every leg's times are expressed relative to the itinerary's own start, matching what
    // routing::BuildRouteFromLegs expects (see live_transit_route_builder.hpp).
    Long routeStartMs = null;
    double maxDelaySeconds = 0;

    for (int i = 0; i < sections.length(); ++i)
    {
      final JSONObject section = sections.getJSONObject(i);
      final JSONObject departure = section.getJSONObject("departure");
      final JSONObject arrival = section.getJSONObject("arrival");

      final long schedDepartureMs = parseIsoDate(departure.optString("departure", null));
      final long schedArrivalMs = parseIsoDate(arrival.optString("arrival", null));
      if (schedDepartureMs < 0 || schedArrivalMs < 0)
        continue; // A section with no departure/arrival timestamp can't be placed on the timeline.

      if (routeStartMs == null)
        routeStartMs = schedDepartureMs;

      final double delaySeconds = Math.max(sectionDelaySeconds(departure), sectionDelaySeconds(arrival));
      maxDelaySeconds = Math.max(maxDelaySeconds, delaySeconds);

      final JSONObject journey = section.optJSONObject("journey");
      final boolean isWalk = journey == null;

      final List<double[]> points = new ArrayList<>();
      points.add(stationLatLon(departure.getJSONObject("station")));
      if (!isWalk)
      {
        final JSONArray passList = journey.optJSONArray("passList");
        if (passList != null)
        {
          // passList includes the section's own departure/arrival stops; only the intermediate
          // ones add real shape detail beyond a straight line.
          for (int p = 1; p < passList.length() - 1; ++p)
            points.add(stationLatLon(passList.getJSONObject(p).getJSONObject("station")));
        }
      }
      points.add(stationLatLon(arrival.getJSONObject("station")));

      final double[] lats = new double[points.size()];
      final double[] lons = new double[points.size()];
      for (int p = 0; p < points.size(); ++p)
      {
        lats[p] = points.get(p)[0];
        lons[p] = points.get(p)[1];
      }

      final int mode = isWalk ? LiveTransitLeg.MODE_WALK : legMode(journey.optString("category", ""));
      final String lineName = isWalk ? "" : journeyLineName(journey);

      legs.add(new LiveTransitLeg(mode, lineName, departure.getJSONObject("station").optString("name", ""),
                                  arrival.getJSONObject("station").optString("name", ""), lats, lons,
                                  (schedDepartureMs - routeStartMs) / 1000.0, (schedArrivalMs - routeStartMs) / 1000.0,
                                  delaySeconds));
    }

    if (legs.isEmpty())
      throw new JSONException("Connection has no usable sections");

    return new LiveTransitItinerary(connection.optString("duration", ""), connection.optInt("transfers", 0),
                                    legs.toArray(new LiveTransitLeg[0]), maxDelaySeconds);
  }

  @NonNull
  private static double[] stationLatLon(@NonNull JSONObject station) throws JSONException
  {
    final JSONObject coordinate = station.getJSONObject("coordinate");
    // This API's WGS84 coordinate object uses x = latitude, y = longitude.
    return new double[] {coordinate.getDouble("x"), coordinate.getDouble("y")};
  }

  @NonNull
  private static String journeyLineName(@NonNull JSONObject journey)
  {
    final String category = journey.optString("category", "");
    final String number = journey.optString("number", "");
    return number.isEmpty() ? category : (category + " " + number).trim();
  }

  private static int legMode(@NonNull String category)
  {
    if (category.equals("T"))
      return LiveTransitLeg.MODE_TRAM;
    if (category.startsWith("B"))
      return LiveTransitLeg.MODE_BUS;
    if (category.isEmpty())
      return LiveTransitLeg.MODE_OTHER;
    return LiveTransitLeg.MODE_TRAIN;
  }

  /**
   * Prefers the live prognosis time over the scheduled one; a checkpoint with no prognosis is
   * treated as on schedule (0s) -- this API omits the prognosis object when it has no realtime
   * data for that checkpoint.
   */
  private static double sectionDelaySeconds(@NonNull JSONObject checkpoint) throws ParseException
  {
    final JSONObject prognosis = checkpoint.optJSONObject("prognosis");
    if (prognosis == null)
      return 0;

    final long schedDeparture = parseIsoDate(checkpoint.optString("departure", null));
    final long liveDeparture = parseIsoDate(prognosis.optString("departure", null));
    if (schedDeparture >= 0 && liveDeparture >= 0)
      return (liveDeparture - schedDeparture) / 1000.0;

    final long schedArrival = parseIsoDate(checkpoint.optString("arrival", null));
    final long liveArrival = parseIsoDate(prognosis.optString("arrival", null));
    if (schedArrival >= 0 && liveArrival >= 0)
      return (liveArrival - schedArrival) / 1000.0;

    return 0;
  }

  /**
   * @return -1 for a null/unparseable timestamp instead of throwing -- a walk section, or a
   *     section with no realtime data, legitimately has no value in some of these date fields.
   */
  private static long parseIsoDate(@Nullable String iso) throws ParseException
  {
    if (iso == null || iso.isEmpty())
      return -1;
    // Matches this API's timestamps, e.g. "2026-09-15T08:05:00+0200". A fresh formatter per call
    // since SimpleDateFormat is not thread-safe and OkHttp's callback runs off the caller's thread.
    return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).parse(iso).getTime();
  }
}
