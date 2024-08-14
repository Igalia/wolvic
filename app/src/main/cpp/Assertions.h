#pragma once

#include <string>

// MACROS to define to-string and logging function utils
#define STRINGIFY(x) #x
#define FILE_AND_LINE_INTERNAL(LINE) __FILE__ ":" STRINGIFY(LINE)
#define FILE_AND_LINE FILE_AND_LINE_INTERNAL(__LINE__)

inline std::string Fmt(const char* fmt, ...) {
    va_list vl;
    va_start(vl, fmt);
    int size = vsnprintf(nullptr, 0, fmt, vl);
    va_end(vl);

    if (size != -1) {
        std::unique_ptr<char[]> buffer(new char[size + 1]);

        va_start(vl, fmt);
        size = vsnprintf(buffer.get(), size + 1, fmt, vl);
        va_end(vl);
        if (size != -1) {
            return std::string(buffer.get(), size);
        }
    }

    throw std::runtime_error("Unexpected vsnprintf failure");
}

[[noreturn]] inline void Throw(std::string failureMessage, const char* originator = nullptr, const char* sourceLocation = nullptr) {
    if (originator != nullptr) {
        failureMessage += Fmt("\n    Origin: %s", originator);
    }
    if (sourceLocation != nullptr) {
        failureMessage += Fmt("\n    Source: %s", sourceLocation);
    }

    throw std::logic_error(failureMessage);
}

#define THROW(msg) Throw(msg, nullptr, FILE_AND_LINE);

#define CHECK(exp)                                      \
    {                                                   \
        if (!(exp)) {                                   \
            Throw("Check failed", #exp, FILE_AND_LINE); \
        }                                               \
    }
#define CHECK_MSG(exp, msg)                  \
    {                                        \
        if (!(exp)) {                        \
            Throw(msg, #exp, FILE_AND_LINE); \
        }                                    \
    }

#define ASSERT(exp)                         \
{                                           \
  if (!(exp)) {                             \
    throw std::logic_error(FILE_AND_LINE);  \
  }                                         \
}
