#include <jni.h>
#include <string>

#define JNI_METHOD(return_type, method_name) \
  JNIEXPORT return_type JNICALL              \
      Java_com_kazuki_replaceobject_1v2_JniInterface_##method_name

int YUV2RGB(int y, int u, int v);

extern "C" {
JNI_METHOD(jstring , stringFromJNI)
(JNIEnv *env, jobject thiz) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

}   // extern "C"