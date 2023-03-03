#ifndef ASSERTIONS_H
#define ASSERTIONS_H

// MACROS to define to-string and logging function utils
#define STRINGIFY(x) #x
#define FILE_AND_LINE __FILE__ ":" STRINGIFY(__LINE__)

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

#endif // ASSERTIONS_H
