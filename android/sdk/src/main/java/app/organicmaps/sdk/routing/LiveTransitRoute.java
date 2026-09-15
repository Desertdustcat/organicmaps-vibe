package app.organicmaps.sdk.routing;

import androidx.annotation.NonNull;

/**
 * Renders an already-selected live public-transport itinerary on the map. Unlike the other
 * routing modes (see {@link app.organicmaps.sdk.Router}), this does not go through
 * RoutingController.build()/RouterType at all: the itinerary is already fully known (fetched and
 * picked by the caller), so this only has to draw it. See RoutingManager::SetExternalRoute
 * (libs/map/routing_manager.hpp) on the native side.
 * <p>
 * Remove the same way as any other route, via Framework.nativeRemoveRoute().
 */
public final class LiveTransitRoute
{
  private LiveTransitRoute() {}

  public static void show(@NonNull LiveTransitLeg[] legs)
  {
    nativeShow(legs);
  }

  private static native void nativeShow(@NonNull LiveTransitLeg[] legs);
}
