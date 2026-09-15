#include "app/organicmaps/sdk/Framework.hpp"
#include "app/organicmaps/sdk/core/jni_helper.hpp"

#include "routing/live_transit_route_builder.hpp"

#include "geometry/mercator.hpp"

#include "base/assert.hpp"

#include <vector>

namespace
{
routing::LiveTransitLegMode ToNativeLegMode(jint mode)
{
  switch (mode)
  {
  case 0: return routing::LiveTransitLegMode::Walk;
  case 1: return routing::LiveTransitLegMode::Tram;
  case 2: return routing::LiveTransitLegMode::Bus;
  case 3: return routing::LiveTransitLegMode::Train;
  case 4: return routing::LiveTransitLegMode::Other;
  default: CHECK(false, ("Unknown LiveTransitLeg.mMode ordinal", mode)); return routing::LiveTransitLegMode::Other;
  }
}

// Reads one app.organicmaps.sdk.routing.LiveTransitLeg by field name -- mirrors how
// jni::ToNativeKeyValue reads a Java Pair<String, String> (jni_helper.cpp).
routing::LiveTransitLeg ToNativeLeg(JNIEnv * env, jobject jLeg)
{
  static jclass const clazz = jni::GetGlobalClassRef(env, "app/organicmaps/sdk/routing/LiveTransitLeg");
  static jfieldID const modeId = env->GetFieldID(clazz, "mMode", "I");
  static jfieldID const lineNameId = env->GetFieldID(clazz, "mLineName", "Ljava/lang/String;");
  static jfieldID const fromStopNameId = env->GetFieldID(clazz, "mFromStopName", "Ljava/lang/String;");
  static jfieldID const toStopNameId = env->GetFieldID(clazz, "mToStopName", "Ljava/lang/String;");
  static jfieldID const pointLatsId = env->GetFieldID(clazz, "mPointLats", "[D");
  static jfieldID const pointLonsId = env->GetFieldID(clazz, "mPointLons", "[D");
  static jfieldID const startTimeId = env->GetFieldID(clazz, "mStartTimeFromRouteBeginS", "D");
  static jfieldID const endTimeId = env->GetFieldID(clazz, "mEndTimeFromRouteBeginS", "D");
  static jfieldID const delayId = env->GetFieldID(clazz, "mDelaySeconds", "D");

  routing::LiveTransitLeg leg;
  leg.m_mode = ToNativeLegMode(env->GetIntField(jLeg, modeId));

  jni::TScopedLocalRef const lineName(env, env->GetObjectField(jLeg, lineNameId));
  leg.m_lineName = jni::ToNativeString(env, static_cast<jstring>(lineName.get()));
  jni::TScopedLocalRef const fromStopName(env, env->GetObjectField(jLeg, fromStopNameId));
  leg.m_fromStopName = jni::ToNativeString(env, static_cast<jstring>(fromStopName.get()));
  jni::TScopedLocalRef const toStopName(env, env->GetObjectField(jLeg, toStopNameId));
  leg.m_toStopName = jni::ToNativeString(env, static_cast<jstring>(toStopName.get()));

  jni::ScopedLocalRef<jdoubleArray> const jLats(env, static_cast<jdoubleArray>(env->GetObjectField(jLeg, pointLatsId)));
  jni::ScopedLocalRef<jdoubleArray> const jLons(env, static_cast<jdoubleArray>(env->GetObjectField(jLeg, pointLonsId)));
  auto const pointCount = env->GetArrayLength(jLats.get());
  CHECK_EQUAL(pointCount, env->GetArrayLength(jLons.get()), ("Mismatched lat/lon array lengths"));
  CHECK_GREATER_OR_EQUAL(pointCount, 2, ("A live transit leg needs at least 2 points"));

  std::vector<jdouble> lats(static_cast<size_t>(pointCount));
  std::vector<jdouble> lons(static_cast<size_t>(pointCount));
  env->GetDoubleArrayRegion(jLats.get(), 0, pointCount, lats.data());
  env->GetDoubleArrayRegion(jLons.get(), 0, pointCount, lons.data());

  leg.m_points.reserve(static_cast<size_t>(pointCount));
  for (jsize i = 0; i < pointCount; ++i)
    leg.m_points.push_back(mercator::FromLatLon(lats[static_cast<size_t>(i)], lons[static_cast<size_t>(i)]));

  leg.m_startTimeFromRouteBeginS = env->GetDoubleField(jLeg, startTimeId);
  leg.m_endTimeFromRouteBeginS = env->GetDoubleField(jLeg, endTimeId);
  leg.m_delaySeconds = env->GetDoubleField(jLeg, delayId);

  return leg;
}
}  // namespace

extern "C"
{
JNIEXPORT void Java_app_organicmaps_sdk_routing_LiveTransitRoute_nativeShow(JNIEnv * env, jclass, jobjectArray jLegs)
{
  auto const legCount = env->GetArrayLength(jLegs);
  CHECK_GREATER(legCount, 0, ("Live transit itinerary has no legs"));

  std::vector<routing::LiveTransitLeg> legs;
  std::vector<routing::LiveTransitLegMode> legModes;
  legs.reserve(static_cast<size_t>(legCount));
  legModes.reserve(static_cast<size_t>(legCount));

  for (jsize i = 0; i < legCount; ++i)
  {
    jni::TScopedLocalRef const jLeg(env, env->GetObjectArrayElement(jLegs, i));
    auto leg = ToNativeLeg(env, jLeg.get());
    legModes.push_back(leg.m_mode);
    legs.push_back(std::move(leg));
  }

  frm()->GetRoutingManager().SetExternalRoute(routing::BuildRouteFromLegs(legs), legModes);
}
}  // extern "C"
