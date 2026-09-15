package app.organicmaps.search.searchch;

import androidx.annotation.NonNull;

/**
 * One address/business entry from the search.ch directory (api.search.ch/tel), shown in a small,
 * clearly-separate panel alongside (never merged into) the core offline search results.
 */
public final class SearchChResult
{
  @NonNull
  public final String mTitle;
  /** Address/description line, built from whatever the feed entry actually had. */
  @NonNull
  public final String mSubtitle;
  public final boolean mHasCoordinates;
  public final double mLat;
  public final double mLon;

  public SearchChResult(@NonNull String title, @NonNull String subtitle, boolean hasCoordinates, double lat, double lon)
  {
    mTitle = title;
    mSubtitle = subtitle;
    mHasCoordinates = hasCoordinates;
    mLat = lat;
    mLon = lon;
  }
}
