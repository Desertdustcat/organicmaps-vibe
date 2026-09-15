package app.organicmaps.search.searchch;

import androidx.annotation.NonNull;
import app.organicmaps.BuildConfig;
import app.organicmaps.sdk.util.concurrency.UiThread;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Client for the search.ch Swiss phone/address/business directory (api.search.ch/tel). Needs a
 * free API key from https://tel.search.ch/api/getkey, supplied at build time via the
 * `searchChApiKey` Gradle property (see app/build.gradle) -- never checked into the repo.
 * <p>
 * <b>Response schema caveat:</b> this API returns an Atom feed with search.ch's own namespaced
 * fields (name/street/zip/city/phone per its docs), not JSON. Those field names could not be
 * verified against a live response while writing this client (network access to search.ch was
 * blocked in that environment) -- parsing here deliberately falls back to the plain Atom
 * title/content/georss:point elements (which are standard and unambiguous) whenever the
 * search.ch-specific fields aren't found, so a namespace/tag-name mismatch degrades to a plainer
 * result rather than silently returning nothing. Treat the structured fields as best-effort until
 * verified against a real response.
 * <p>
 * Also assumes each entry's Atom {@code <content>} is plain text, not nested markup -- fine for a
 * directory API's short address blob, but worth revisiting if real responses use HTML content.
 */
public final class SearchChDirectoryApi
{
  private static final String BASE_URL = "https://search.ch/tel/api/";

  public interface ResultCallback
  {
    void onSuccess(@NonNull List<SearchChResult> results);
    void onFailure(@NonNull String message);
  }

  private final OkHttpClient mClient = new OkHttpClient();

  public boolean isConfigured()
  {
    return !BuildConfig.SEARCH_CH_API_KEY.isEmpty();
  }

  public void search(@NonNull String query, @NonNull ResultCallback callback)
  {
    if (!isConfigured())
    {
      callback.onSuccess(new ArrayList<>());
      return;
    }

    final HttpUrl url = Objects.requireNonNull(HttpUrl.parse(BASE_URL))
                            .newBuilder()
                            .addQueryParameter("was", query)
                            .addQueryParameter("key", BuildConfig.SEARCH_CH_API_KEY)
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
          final List<SearchChResult> results = parseAtomFeed(response.body().byteStream());
          UiThread.run(() -> callback.onSuccess(results));
        }
        catch (IOException | XmlPullParserException e)
        {
          final String message = e.getMessage() == null ? "Malformed response" : e.getMessage();
          UiThread.run(() -> callback.onFailure(message));
        }
      }
    });
  }

  @NonNull
  private static List<SearchChResult> parseAtomFeed(@NonNull InputStream body)
      throws XmlPullParserException, IOException
  {
    final XmlPullParser parser = android.util.Xml.newPullParser();
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
    parser.setInput(body, null);

    final List<SearchChResult> results = new ArrayList<>();
    String title = null;
    String content = null;
    String street = null, zip = null, city = null;
    double lat = 0, lon = 0;
    boolean hasPoint = false;
    boolean inEntry = false;

    int eventType = parser.getEventType();
    while (eventType != XmlPullParser.END_DOCUMENT)
    {
      // readText() itself advances past its element's closing tag (standard XmlPullParser text-
      // reading idiom), so a branch that calls it must not be followed by another next() this
      // iteration -- alreadyAdvanced tracks that.
      boolean alreadyAdvanced = false;

      if (eventType == XmlPullParser.START_TAG)
      {
        // Namespace prefixes are ignored (FEATURE_PROCESS_NAMESPACES=false), so "tel:name" and
        // "georss:point" show up under their prefixed local names here -- matched below without
        // depending on the exact namespace URI, since that part is the least certain.
        final String tag = localName(parser.getName());
        switch (tag)
        {
        case "entry":
          inEntry = true;
          title = null;
          content = null;
          street = null;
          zip = null;
          city = null;
          hasPoint = false;
          break;
        case "title":
          if (inEntry)
          {
            title = readText(parser);
            alreadyAdvanced = true;
          }
          break;
        case "content":
          if (inEntry)
          {
            content = readText(parser);
            alreadyAdvanced = true;
          }
          break;
        case "point":
          if (inEntry)
          {
            final String[] parts = readText(parser).trim().split("\\s+");
            alreadyAdvanced = true;
            if (parts.length == 2)
            {
              try
              {
                lat = Double.parseDouble(parts[0]);
                lon = Double.parseDouble(parts[1]);
                hasPoint = true;
              }
              catch (NumberFormatException ignored2)
              {
                hasPoint = false;
              }
            }
          }
          break;
        case "street":
          if (inEntry)
          {
            street = readText(parser);
            alreadyAdvanced = true;
          }
          break;
        case "zip":
          if (inEntry)
          {
            zip = readText(parser);
            alreadyAdvanced = true;
          }
          break;
        case "city":
          if (inEntry)
          {
            city = readText(parser);
            alreadyAdvanced = true;
          }
          break;
        default: break;
        }
      }
      else if (eventType == XmlPullParser.END_TAG && localName(parser.getName()).equals("entry"))
      {
        inEntry = false;
        if (title != null && !title.isEmpty())
        {
          final String subtitle = buildSubtitle(street, zip, city, content);
          results.add(new SearchChResult(title, subtitle, hasPoint, lat, lon));
        }
      }

      eventType = alreadyAdvanced ? parser.getEventType() : parser.next();
    }

    return results;
  }

  @NonNull
  private static String buildSubtitle(String street, String zip, String city, String content)
  {
    final StringBuilder structured = new StringBuilder();
    if (street != null && !street.isEmpty())
      structured.append(street);
    if (zip != null || city != null)
    {
      if (structured.length() > 0)
        structured.append(", ");
      if (zip != null)
        structured.append(zip).append(' ');
      if (city != null)
        structured.append(city);
    }
    if (structured.length() > 0)
      return structured.toString();
    // Fall back to the plain Atom <content>, which every entry has regardless of whether the
    // search.ch-specific fields above were found under the names this client expects.
    return content == null ? "" : content.replaceAll("\\s+", " ").trim();
  }

  @NonNull
  private static String localName(@NonNull String possiblyPrefixed)
  {
    final int colon = possiblyPrefixed.indexOf(':');
    return colon < 0 ? possiblyPrefixed : possiblyPrefixed.substring(colon + 1);
  }

  @NonNull
  private static String readText(@NonNull XmlPullParser parser) throws XmlPullParserException, IOException
  {
    if (parser.next() != XmlPullParser.TEXT)
      return "";
    final String text = parser.getText();
    parser.nextTag();
    return text == null ? "" : text;
  }
}
