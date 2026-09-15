#include "routing/live_transit_route_builder.hpp"

#include "routing/routing_helpers.hpp"
#include "routing/segment.hpp"
#include "routing/turns.hpp"

#include "geometry/point_with_altitude.hpp"

#include "base/assert.hpp"

namespace routing
{
namespace
{
geometry::PointWithAltitude ToPointWA(m2::PointD const & p)
{
  return geometry::PointWithAltitude(p, 0 /* altitude */);
}
}  // namespace

// Modeled on RulerRouter::CalculateRoute (ruler_router.cpp), which also builds a Route with no
// graph search — but subroutes there are per via-point pair, while here each leg (which may cover
// several points if the caller has intermediate shape points) is its own subroute, so a single
// SubrouteStyle can be applied per leg mode by the caller.
Route BuildRouteFromLegs(std::vector<LiveTransitLeg> const & legs)
{
  CHECK(!legs.empty(), ());

  Segment const fakeSegment(kFakeNumMwmId, 0, 0, false);

  std::vector<RouteSegment> routeSegments;
  std::vector<double> times;
  std::vector<m2::PointD> geometry;
  std::vector<Route::SubrouteAttrs> subroutes;

  geometry.push_back(legs.front().m_points.front());

  for (size_t legIdx = 0; legIdx < legs.size(); ++legIdx)
  {
    auto const & leg = legs[legIdx];
    CHECK_GREATER_OR_EQUAL(leg.m_points.size(), 2, ("Leg", legIdx, "has too few points"));

    RouteSegment::RoadNameInfo roadNameInfo;
    if (leg.m_mode != LiveTransitLegMode::Walk)
      roadNameInfo = RouteSegment::RoadNameInfo(leg.m_lineName);

    size_t const beginSegmentIdx = routeSegments.size();
    double const legDurationS = leg.m_endTimeFromRouteBeginS - leg.m_startTimeFromRouteBeginS;
    size_t const legPointCount = leg.m_points.size();

    for (size_t i = 1; i < legPointCount; ++i)
    {
      bool const isVeryLastSegment = (legIdx + 1 == legs.size()) && (i + 1 == legPointCount);
      turns::TurnItem const turn(
          static_cast<uint32_t>(routeSegments.size() + 1),
          isVeryLastSegment ? turns::PedestrianDirection::ReachedYourDestination : turns::PedestrianDirection::None);

      routeSegments.emplace_back(fakeSegment, turn, ToPointWA(leg.m_points[i]), roadNameInfo);

      // Interpolate this point's ETA linearly across the leg's own point count — exact when a leg
      // is just [from, to] (the common case), an approximation when the caller supplies interior
      // shape points without per-point timing.
      double const t = legPointCount > 2 ? static_cast<double>(i) / static_cast<double>(legPointCount - 1) : 1.0;
      times.push_back(leg.m_startTimeFromRouteBeginS + t * legDurationS);

      geometry.push_back(leg.m_points[i]);
    }

    subroutes.emplace_back(ToPointWA(leg.m_points.front()), ToPointWA(leg.m_points.back()), beginSegmentIdx,
                           routeSegments.size());
  }

  FillSegmentInfo(times, routeSegments);

  Route route;
  route.SetRouteSegments(std::move(routeSegments));
  route.SetGeometry(geometry.begin(), geometry.end());
  route.SetSubroutes(std::move(subroutes));
  return route;
}
}  // namespace routing
