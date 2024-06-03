/* -*- Mode: C++; tab-width: 20; indent-tabs-mode: nil; c-basic-offset: 2 -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#include "OpenXRActionSet.h"

using namespace crow;

OpenXRActionSet::OpenXRActionSet(XrInstance instance, XrSession session)
    : mInstance(instance)
    , mSession(session)
{
}

OpenXRActionSet::~OpenXRActionSet()
{
  if (mActionSet != XR_NULL_HANDLE) {
    xrDestroyActionSet(mActionSet);
  }
}


OpenXRActionSetPtr OpenXRActionSet::Create(XrInstance instance, XrSession session)
{
  OpenXRActionSetPtr action_set(new OpenXRActionSet(instance, session));
  CHECK_XRCMD(action_set->Initialize());
  return action_set;
}

XrResult OpenXRActionSet::Initialize() {
  std::string actionSetName = "wolvic_action_set";
  XrActionSetCreateInfo createInfo { XR_TYPE_ACTION_SET_CREATE_INFO };
  std::strncpy(createInfo.actionSetName, actionSetName.c_str(), XR_MAX_ACTION_SET_NAME_SIZE - 1);
  std::strncpy(createInfo.localizedActionSetName, actionSetName.c_str(), XR_MAX_ACTION_SET_NAME_SIZE - 1);

  RETURN_IF_XR_FAILED(xrCreateActionSet(mInstance, &createInfo, &mActionSet), mInstance);

  RETURN_IF_XR_FAILED(xrStringToPath(mInstance, kPathLeftHand, &mSubactionPaths.front()));
  RETURN_IF_XR_FAILED(xrStringToPath(mInstance, kPathRightHand, &mSubactionPaths.back()));

  return XR_SUCCESS;
}

XrResult OpenXRActionSet::CreateAction(XrActionType actionType, const std::string& name, OpenXRHandFlags handFlags, XrAction& action) const
{
  XrActionCreateInfo createInfo { XR_TYPE_ACTION_CREATE_INFO };
  createInfo.actionType = actionType;
  if (handFlags == OpenXRHandFlags::Both) {
    createInfo.countSubactionPaths = 2;
    createInfo.subactionPaths = &mSubactionPaths.front();
  } else {
    createInfo.countSubactionPaths = 1;
    createInfo.subactionPaths = &GetSubactionPath(handFlags);
  }
  std::strncpy(createInfo.actionName, name.c_str(), XR_MAX_ACTION_SET_NAME_SIZE - 1);
  std::strncpy(createInfo.localizedActionName, name.c_str(), XR_MAX_ACTION_SET_NAME_SIZE - 1);

  return xrCreateAction(mActionSet, &createInfo, &action);
}

XrResult OpenXRActionSet::GetOrCreateAction(XrActionType actionType, const std::string& name, OpenXRHandFlags hand, XrAction& action) {
  auto key = mPrefix;
  if (hand != OpenXRHandFlags::Both) {
    key += hand == OpenXRHandFlags::Left ? "left_" : "right_";
  }
  key += name;

  auto it = mActions.find(key);
  if (it != mActions.end()) {
    action = it->second;
    return XR_SUCCESS;
  }


  RETURN_IF_XR_FAILED(CreateAction(actionType, key, hand, action));
  mActions.emplace(key, action);

  return XR_SUCCESS;
}

XrResult OpenXRActionSet::GetOrCreateButtonActions(OpenXRButtonType type, OpenXRButtonFlags flags, OpenXRHandFlags hand, OpenXRButtonActions& actions) {
  std::string key = mPrefix + "button_";
  if (hand != OpenXRHandFlags::Both) {
    key += hand == OpenXRHandFlags::Left ? "left_" : "right_";
  }
  key += OpenXRButtonTypeNames->at(static_cast<int>(type));


  auto it = mButtonActions.find(key);
  if (it != mButtonActions.end()) {
    actions = it->second;
    return XR_SUCCESS;
  }

  if (flags & OpenXRButtonFlags::Click) {
    RETURN_IF_XR_FAILED(CreateAction(XR_ACTION_TYPE_BOOLEAN_INPUT, key + "_click", hand, actions.click));
  }
  if (flags  & OpenXRButtonFlags::Touch) {
    RETURN_IF_XR_FAILED(CreateAction(XR_ACTION_TYPE_BOOLEAN_INPUT, key + "_touch", hand, actions.touch));
  }
  if (flags  & OpenXRButtonFlags::Value) {
    RETURN_IF_XR_FAILED(CreateAction(XR_ACTION_TYPE_FLOAT_INPUT, key + "_value", hand, actions.value));
  }
  if (flags  & OpenXRButtonFlags::Ready) {
    RETURN_IF_XR_FAILED(CreateAction(XR_ACTION_TYPE_BOOLEAN_INPUT, key + "_ready_ext", hand, actions.ready));
  }

  mButtonActions.emplace(key, actions);

  return XR_SUCCESS;
}

XrResult OpenXRActionSet::GetOrCreateAxisAction(OpenXRAxisType axisType, OpenXRHandFlags hand, XrAction& action) {
  std::string key = mPrefix + "axis_";
  if (hand != OpenXRHandFlags::Both) {
    key += hand == OpenXRHandFlags::Left ? "left_" : "right_";
  }
  key += OpenXRAxisTypeNames->at(static_cast<int>(axisType));

  auto it = mActions.find(key);
  if (it != mActions.end()) {
    action = it->second;
    return XR_SUCCESS;
  }

  RETURN_IF_XR_FAILED(CreateAction(XR_ACTION_TYPE_VECTOR2F_INPUT, key, hand, action));
  mActions.emplace(key, action);

  return XR_SUCCESS;
}

const XrPath& OpenXRActionSet::GetSubactionPath(OpenXRHandFlags handeness) const
{
  if (handeness == OpenXRHandFlags::Left) {
    return mSubactionPaths.front();
  } else {
    return mSubactionPaths.back();
  }
}
