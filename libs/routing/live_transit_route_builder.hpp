#pragma once

#include "routing/route.hpp"

#include "geometry/point2d.hpp"

#include <string>
#include <vector>

namespace routing
{
/// \brief Travel mode of a LiveTransitLeg, used by the caller to pick a subroute's map style.
/// (BuildRouteFromLegs itself does not interpret this — see the ordering contract below.)
enum class LiveTransitLegMode
{
  Walk,
  Tram,
  Bus,
  Train,
  Other
};

/// \brief One leg of an already-computed itinerary fetched from an external live journey-planner
/// API (e.g. transport.opendata.ch), to be rendered as its own subroute. Not the result of a graph
/// search: no real mwm FeatureIDs are involved, only the leg's own stop-to-stop geometry.
struct LiveTransitLeg
{
  LiveTransitLegMode m_mode = LiveTransitLegMode::Walk;
  std::string m_lineName;  // E.g. "Tram 11". Empty for walk legs.
  std::string m_fromStopName;
  std::string m_toStopName;

  /// Leg geometry, at least 2 points. For a leg with only endpoint data (the common case for
  /// transport.opendata.ch connections, which give stops but not full vehicle-path shapes) this
  /// is just [from, to] and the leg renders as a straight line between served stops.
  std::vector<m2::PointD> m_points;

  /// Elapsed seconds from the whole route's start to this leg's departure/arrival, used to
  /// linearly interpolate each point's ETA. Must be non-decreasing across legs.
  double m_startTimeFromRouteBeginS = 0.0;
  double m_endTimeFromRouteBeginS = 0.0;

  /// Live delay (from the API's prognosis data) in seconds, 0 if none reported. Not consumed by
  /// BuildRouteFromLegs; carried here so callers building UI (leg list, delay badges) have it
  /// alongside the same leg list used to build the Route.
  double m_delaySeconds = 0.0;
};

/// \brief Builds a routing::Route directly from a pre-fetched, already-selected itinerary, with
/// no graph search involved. One subroute per leg, in the same order as |legs| — callers that need
/// per-leg styling (e.g. RoutingManager::SetExternalRoute) rely on
/// route.GetSubroutes()[i] <-> legs[i] to pick a style per LiveTransitLegMode.
/// \pre Every leg has at least 2 points.
Route BuildRouteFromLegs(std::vector<LiveTransitLeg> const & legs);
}  // namespace routing
