#include "BrowserWorldFileReader.h"

#include <jni.h>

namespace {

class BrowserWorldFileReaderAlloc : public BrowserWorldFileReader {
public:
  BrowserWorldFileReaderAlloc(){}
};

}

struct BrowserWorldFileReader::State {
  JNIEnv* env;
  jobject activity;
  State()
      : env(nullptr)
      , activity(0)
  {}
};

BrowserWorldFileReaderPtr
BrowserWorldFileReader::Create() {
  return std::make_shared<BrowserWorldFileReaderAlloc>();
}

void
BrowserWorldFileReader::ReadRawFile(const std::string& aFileName, vrb::FileHandlerPtr aHandler) {
  if (m.env) {

  }
}

void
BrowserWorldFileReader::ReadImageFile(const std::string& aFileName, vrb::FileHandlerPtr aHandler) {
  if (m.env) {

  }
}

void
BrowserWorldFileReader::SetActivity(JNIEnv* aEnv, jobject &aActivity) {
  m.env = aEnv;
  m.activity = aEnv->NewGlobalRef(aActivity);
}

void
BrowserWorldFileReader::ClearActivity() {
  if (m.env) {
    m.env->DeleteGlobalRef(m.activity);
    m.activity = 0;
    m.env = nullptr;
  }
}


BrowserWorldFileReader::BrowserWorldFileReader() : m(*(new State)) {}

BrowserWorldFileReader::~BrowserWorldFileReader() {
  delete &m;
}
