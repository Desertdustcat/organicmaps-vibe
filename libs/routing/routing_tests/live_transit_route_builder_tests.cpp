#include "testing/testing.hpp"

#include "routing/live_transit_route_builder.hpp"
#include "routing/route.hpp"
#include "routing/turns.hpp"

namespace live_transit_route_builder_tests
{
using namespace routing;

std::vector<LiveTransitLeg> MakeTwoLegItinerary()
{
  LiveTransitLeg walk;
  walk.m_mode = LiveTransitLegMode::Walk;
  walk.m_fromStopName = "Home";
  walk.m_toStopName = "Bahnhofplatz";
  walk.m_points = {{0.0, 0.0}, {1.0, 1.0}};
  walk.m_startTimeFromRouteBeginS = 0.0;
  walk.m_endTimeFromRouteBeginS = 60.0;

  LiveTransitLeg tram;
  tram.m_mode = LiveTransitLegMode::Tram;
  tram.m_lineName = "Tram 11";
  tram.m_fromStopName = "Bahnhofplatz";
  tram.m_toStopName = "Zoo";
  tram.m_points = {{1.0, 1.0}, {1.0, 2.0}, {1.0, 4.0}};
  tram.m_startTimeFromRouteBeginS = 60.0;
  tram.m_endTimeFromRouteBeginS = 300.0;
  tram.m_delaySeconds = 120.0;

  return {walk, tram};
}

UNIT_TEST(BuildRouteFromLegs_SubrouteCountAndRanges)
{
  Route const route = BuildRouteFromLegs(MakeTwoLegItinerary());

  TEST(route.IsValid(), ());
  TEST_EQUAL(route.GetSubroutes().size(), 2, ());

  auto const & walkSub = route.GetSubrouteAttrs(0);
  TEST_EQUAL(walkSub.GetBeginSegmentIdx(), 0, ());
  TEST_EQUAL(walkSub.GetEndSegmentIdx(), 1, ());

  auto const & tramSub = route.GetSubrouteAttrs(1);
  TEST_EQUAL(tramSub.GetBeginSegmentIdx(), 1, ());
  TEST_EQUAL(tramSub.GetEndSegmentIdx(), 3, ());

  // 1 segment for the 2-point walk leg + 2 segments for the 3-point tram leg.
  TEST_EQUAL(route.GetRouteSegments().size(), 3, ());
  // geometry has one more point than there are segments (the shared route start point).
  TEST_EQUAL(route.GetPoly().GetSize(), 4, ());
}

UNIT_TEST(BuildRouteFromLegs_RoadNameInfoPerLeg)
{
  Route const route = BuildRouteFromLegs(MakeTwoLegItinerary());
  auto const & segments = route.GetRouteSegments();

  // Walk leg segment (index 0) carries no line name.
  TEST(segments[0].GetRoadNameInfo().m_name.empty(), ());

  // Both tram leg segments (indices 1, 2) carry the line name.
  TEST_EQUAL(segments[1].GetRoadNameInfo().m_name, std::string("Tram 11"), ());
  TEST_EQUAL(segments[2].GetRoadNameInfo().m_name, std::string("Tram 11"), ());
}

UNIT_TEST(BuildRouteFromLegs_LastSegmentReachesDestination)
{
  Route const route = BuildRouteFromLegs(MakeTwoLegItinerary());
  auto const & segments = route.GetRouteSegments();

  for (size_t i = 0; i + 1 < segments.size(); ++i)
    TEST(segments[i].GetTurn().m_pedestrianTurn == turns::PedestrianDirection::None, (i));

  TEST(segments.back().GetTurn().m_pedestrianTurn == turns::PedestrianDirection::ReachedYourDestination, ());
}

UNIT_TEST(BuildRouteFromLegs_TimingMatchesLegBoundaries)
{
  Route const route = BuildRouteFromLegs(MakeTwoLegItinerary());
  auto const & segments = route.GetRouteSegments();

  TEST_ALMOST_EQUAL_ABS(segments[0].GetTimeFromBeginningSec(), 60.0, 1e-6, ());
  // Middle point of the 3-point tram leg is interpolated halfway through the leg's duration.
  TEST_ALMOST_EQUAL_ABS(segments[1].GetTimeFromBeginningSec(), 60.0 + 0.5 * (300.0 - 60.0), 1e-6, ());
  TEST_ALMOST_EQUAL_ABS(segments[2].GetTimeFromBeginningSec(), 300.0, 1e-6, ());
  TEST_ALMOST_EQUAL_ABS(route.GetTotalTimeSec(), 300.0, 1e-6, ());
}
}  // namespace live_transit_route_builder_tests
