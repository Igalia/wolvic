#pragma once

#include <algorithm>
#include <sstream>

namespace crow {

#define ANDROID_OS_BUILD_ID "ro.build.id"

// Get the Build ID of the current Android system.
inline const char* GetBuildIdString(char* out) {
  __system_property_get(ANDROID_OS_BUILD_ID, out);
  return out;
}

// Parse a version string into an array of integers of size resultSize.
inline void ParseVersionString(const std::string& aString, int result[], int resultSize) {
  std::istringstream stringStream(aString);
  // Read the first number
  stringStream >> result[0];
  for(int i = 1; i < resultSize; i++) {
    // Skip period
    stringStream.get();
    // Read the next number
    stringStream >> result[i];
  }
}

// Compare two strings containing semantic versions in the MAJOR.MINOR.PATCH format.
// Return: true if str1 is a lower version than str2, and false if it is the same or higher.
inline bool CompareSemanticVersionStrings(const std::string& str1, const std::string& str2) {
  int parsedStr1[3]{}, parsedStr2[3]{};
  ParseVersionString(str1, parsedStr1, 3);
  ParseVersionString(str2, parsedStr2, 3);
  return std::lexicographical_compare(parsedStr1, parsedStr1 + 3, parsedStr2, parsedStr2 + 3);
}

}  // namespace crow