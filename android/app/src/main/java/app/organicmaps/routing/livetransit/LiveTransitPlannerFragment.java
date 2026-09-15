package app.organicmaps.routing.livetransit;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import app.organicmaps.R;
import app.organicmaps.base.BaseMwmToolbarFragment;
import app.organicmaps.sdk.routing.LiveTransitRoute;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Lets the user pick a Swiss public-transport connection (start/end, depart-at or arrive-by time)
 * and shows the live results from {@link SwissTransitApi} to choose from. Picking one renders it
 * on the map via {@link LiveTransitRoute#show} and returns to the map -- there is no turn-by-turn
 * follow mode for this route type, only the itinerary overview.
 */
public class LiveTransitPlannerFragment
    extends BaseMwmToolbarFragment implements LiveTransitItineraryAdapter.ItemClickListener
{
  private final SwissTransitApi mApi = new SwissTransitApi();
  private final Calendar mDateTime = Calendar.getInstance();

  private EditText mFrom;
  private EditText mTo;
  private RadioButton mArriveBy;
  private Button mTimePickerBtn;
  private View mProgress;
  private TextView mMessage;
  private RecyclerView mResults;
  private LiveTransitItineraryAdapter mAdapter;

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.fragment_live_transit_planner, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    super.onViewCreated(view, savedInstanceState);

    mFrom = view.findViewById(R.id.live_transit_from);
    mTo = view.findViewById(R.id.live_transit_to);
    mArriveBy = view.findViewById(R.id.live_transit_arrive_by);
    mTimePickerBtn = view.findViewById(R.id.live_transit_time_picker);
    mProgress = view.findViewById(R.id.live_transit_progress);
    mMessage = view.findViewById(R.id.live_transit_message);
    mResults = view.findViewById(R.id.live_transit_results);

    mAdapter = new LiveTransitItineraryAdapter(this);
    mResults.setLayoutManager(new LinearLayoutManager(requireContext()));
    mResults.setAdapter(mAdapter);

    updateTimePickerLabel();
    mTimePickerBtn.setOnClickListener(v -> showDateTimePicker());
    view.findViewById(R.id.live_transit_search).setOnClickListener(v -> search());
  }

  private void showDateTimePicker()
  {
    final Calendar now = mDateTime;
    new DatePickerDialog(requireContext(), (view, year, month, dayOfMonth) -> {
      now.set(Calendar.YEAR, year);
      now.set(Calendar.MONTH, month);
      now.set(Calendar.DAY_OF_MONTH, dayOfMonth);
      new TimePickerDialog(requireContext(), (timeView, hourOfDay, minute) -> {
        now.set(Calendar.HOUR_OF_DAY, hourOfDay);
        now.set(Calendar.MINUTE, minute);
        updateTimePickerLabel();
      }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show();
    }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show();
  }

  private void updateTimePickerLabel()
  {
    mTimePickerBtn.setText(String.format(Locale.getDefault(), "%1$te.%1$tm. %1$tH:%1$tM", mDateTime));
  }

  private void search()
  {
    final String from = mFrom.getText().toString().trim();
    final String to = mTo.getText().toString().trim();
    if (from.isEmpty() || to.isEmpty())
      return;

    mProgress.setVisibility(View.VISIBLE);
    mMessage.setVisibility(View.GONE);
    mAdapter.setItems(Collections.emptyList());

    mApi.findConnections(from, to, mDateTime.getTime(), mArriveBy.isChecked(), new SwissTransitApi.ResultCallback() {
      @Override
      public void onSuccess(@NonNull List<LiveTransitItinerary> itineraries)
      {
        mProgress.setVisibility(View.GONE);
        if (itineraries.isEmpty())
        {
          mMessage.setText(R.string.live_transit_no_results);
          mMessage.setVisibility(View.VISIBLE);
          return;
        }
        mAdapter.setItems(itineraries);
      }

      @Override
      public void onFailure(@NonNull String message)
      {
        mProgress.setVisibility(View.GONE);
        mMessage.setText(getString(R.string.live_transit_error, message));
        mMessage.setVisibility(View.VISIBLE);
      }
    });
  }

  @Override
  public void onItineraryClick(@NonNull LiveTransitItinerary itinerary)
  {
    LiveTransitRoute.show(itinerary.mLegs);
    Toast.makeText(requireContext(), R.string.live_transit_shown_on_map, Toast.LENGTH_SHORT).show();
    requireActivity().finish();
  }
}
