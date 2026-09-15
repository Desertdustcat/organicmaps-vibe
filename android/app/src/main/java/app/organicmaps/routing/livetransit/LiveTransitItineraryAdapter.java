package app.organicmaps.routing.livetransit;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import app.organicmaps.R;
import app.organicmaps.sdk.routing.LiveTransitLeg;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shows a list of {@link LiveTransitItinerary} options for the user to pick from, the same
 * "several results, tap one" shape as the core search results list.
 */
public class LiveTransitItineraryAdapter extends RecyclerView.Adapter<LiveTransitItineraryAdapter.ViewHolder>
{
  public interface ItemClickListener
  {
    void onItineraryClick(@NonNull LiveTransitItinerary itinerary);
  }

  @NonNull
  private final List<LiveTransitItinerary> mItems = new ArrayList<>();
  @NonNull
  private final ItemClickListener mListener;

  public LiveTransitItineraryAdapter(@NonNull ItemClickListener listener)
  {
    mListener = listener;
  }

  public void setItems(@NonNull List<LiveTransitItinerary> items)
  {
    mItems.clear();
    mItems.addAll(items);
    notifyDataSetChanged();
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
  {
    final View view =
        LayoutInflater.from(parent.getContext()).inflate(R.layout.live_transit_itinerary_item, parent, false);
    return new ViewHolder(view);
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position)
  {
    holder.bind(mItems.get(position), mListener);
  }

  @Override
  public int getItemCount()
  {
    return mItems.size();
  }

  static class ViewHolder extends RecyclerView.ViewHolder
  {
    @NonNull
    private final TextView mSummary;
    @NonNull
    private final TextView mLegs;

    ViewHolder(@NonNull View itemView)
    {
      super(itemView);
      mSummary = itemView.findViewById(R.id.live_transit_summary);
      mLegs = itemView.findViewById(R.id.live_transit_legs);
    }

    void bind(@NonNull LiveTransitItinerary itinerary, @NonNull ItemClickListener listener)
    {
      final String duration = formatDuration(itinerary.mDuration);
      final String transfers =
          itinerary.mTransfers == 0
              ? itemView.getContext().getString(R.string.live_transit_no_transfers)
              : itemView.getContext().getString(R.string.live_transit_transfers, itinerary.mTransfers);
      final StringBuilder summary = new StringBuilder(duration).append(" · ").append(transfers);
      if (itinerary.mMaxDelaySeconds >= 60)
      {
        summary.append(" · ").append(itemView.getContext().getString(R.string.live_transit_delay,
                                                                     Math.round(itinerary.mMaxDelaySeconds / 60.0)));
      }
      mSummary.setText(summary);

      final StringBuilder legsText = new StringBuilder();
      for (LiveTransitLeg leg : itinerary.mLegs)
      {
        if (legsText.length() > 0)
          legsText.append('\n');
        if (leg.mMode == LiveTransitLeg.MODE_WALK)
          legsText.append("⭢ ").append(leg.mFromStopName).append(" → ").append(leg.mToStopName);
        else
          legsText.append(leg.mLineName).append(": ").append(leg.mFromStopName).append(" → ").append(leg.mToStopName);
      }
      mLegs.setText(legsText);

      itemView.setOnClickListener(v -> listener.onItineraryClick(itinerary));
    }

    // The API returns duration as "DDdHH:MM:SS"; show it as e.g. "1h 34min" or "34min".
    @NonNull
    private static String formatDuration(@NonNull String apiDuration)
    {
      final int timePartIdx = apiDuration.indexOf('d');
      final String timePart = timePartIdx >= 0 ? apiDuration.substring(timePartIdx + 1) : apiDuration;
      final String[] hms = timePart.split(":");
      if (hms.length < 2)
        return apiDuration;
      final int hours = Integer.parseInt(hms[0]);
      final int minutes = Integer.parseInt(hms[1]);
      return hours > 0 ? String.format(Locale.getDefault(), "%dh %02dmin", hours, minutes)
                       : String.format(Locale.getDefault(), "%dmin", minutes);
    }
  }
}
