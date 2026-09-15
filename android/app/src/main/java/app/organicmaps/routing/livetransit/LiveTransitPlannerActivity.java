package app.organicmaps.routing.livetransit;

import android.app.Activity;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import app.organicmaps.base.BaseMwmFragmentActivity;

/**
 * Hosts {@link LiveTransitPlannerFragment}, mirroring how
 * {@link app.organicmaps.settings.DrivingOptionsActivity} hosts its own settings fragment. A
 * separate activity, not a mode within {@link app.organicmaps.routing.RoutingPlanFragment}'s
 * RadioGroup: live transit is an already-computed itinerary picked from a list, not a
 * RouterType-driven graph search, so it does not share that fragment's turn-by-turn-oriented UI.
 */
public class LiveTransitPlannerActivity extends BaseMwmFragmentActivity
{
  @Override
  protected Class<? extends Fragment> getFragmentClass()
  {
    return LiveTransitPlannerFragment.class;
  }

  public static void start(@NonNull Activity activity)
  {
    activity.startActivity(new Intent(activity, LiveTransitPlannerActivity.class));
  }
}
