#ifndef WOLVICUTILS_H
#define WOLVICUTILS_H

// MACROS to define to-string function utils
#define STRINGIFY(x) #x
#define FILE_AND_LINE __FILE__ ":" STRINGIFY(__LINE__)

// MACROS to define logging functions
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

#define ASSERT_ON_RENDER_THREAD(X)                                          \
  if (m.context && !m.context->IsOnRenderThread()) {                        \
    VRB_ERROR("Function: '%s' not called on render thread.", __FUNCTION__); \
    return X;                                                               \
  }


#endif //WOLVICUTILS_H
