#ifndef VRBROWSER_BROWSER_WORLD_FILE_READER_DOT_H
#define VRBROWSER_BROWSER_WORLD_FILE_READER_DOT_H

#include "vrb/FileReader.h"
#include <jni.h>
#include <memory>


class BrowserWorldFileReader;
typedef std::shared_ptr<BrowserWorldFileReader> BrowserWorldFileReaderPtr;

class BrowserWorldFileReader : public vrb::FileReader {
public:
  static BrowserWorldFileReaderPtr Create();
  void ReadRawFile(const std::string& aFileName, vrb::FileHandlerPtr aHandler) override;
  void ReadImageFile(const std::string& aFileName, vrb::FileHandlerPtr aHandler) override;
  void SetActivity(JNIEnv* aEnv, jobject& aActivity);
  void ClearActivity();
protected:
  BrowserWorldFileReader();
  ~BrowserWorldFileReader();

  struct State;
  State& m;
private:
  BrowserWorldFileReader(const BrowserWorldFileReader&) = delete;
  BrowserWorldFileReader& operator=(const BrowserWorldFileReader&) = delete;
};


#endif //VRBROWSER_BROWSER_WORLD_FILE_READER_DOT_H
