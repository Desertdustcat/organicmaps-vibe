package app.organicmaps.search.searchch;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import app.organicmaps.R;
import java.util.ArrayList;
import java.util.List;

/**
 * A short, horizontally-scrollable strip of search.ch directory results -- kept visually and
 * structurally separate from {@link app.organicmaps.search.SearchAdapter}'s core, ranked results.
 */
public class SearchChResultAdapter extends RecyclerView.Adapter<SearchChResultAdapter.ViewHolder>
{
  public interface ItemClickListener
  {
    void onResultClick(@NonNull SearchChResult result);
  }

  @NonNull
  private final List<SearchChResult> mItems = new ArrayList<>();
  @NonNull
  private final ItemClickListener mListener;

  public SearchChResultAdapter(@NonNull ItemClickListener listener)
  {
    mListener = listener;
  }

  public void setItems(@NonNull List<SearchChResult> items)
  {
    mItems.clear();
    mItems.addAll(items);
    notifyDataSetChanged();
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
  {
    final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.search_ch_result_item, parent, false);
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
    private final TextView mTitle;
    @NonNull
    private final TextView mSubtitle;

    ViewHolder(@NonNull View itemView)
    {
      super(itemView);
      mTitle = itemView.findViewById(R.id.search_ch_title);
      mSubtitle = itemView.findViewById(R.id.search_ch_subtitle);
    }

    void bind(@NonNull SearchChResult result, @NonNull ItemClickListener listener)
    {
      mTitle.setText(result.mTitle);
      mSubtitle.setText(result.mSubtitle);
      itemView.setEnabled(result.mHasCoordinates);
      itemView.setOnClickListener(result.mHasCoordinates ? v -> listener.onResultClick(result) : null);
    }
  }
}
