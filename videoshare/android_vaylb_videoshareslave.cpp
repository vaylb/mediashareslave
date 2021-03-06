#include <string.h>
#include <jni.h>
#include "JNIHelp.h"
#include <stdlib.h>
#include <utils/Log.h>
#include "android_runtime/Log.h"
#include "android_runtime/AndroidRuntime.h"

#include <sys/resource.h>
#include "VideoShareSlave.h"
#include <android_runtime/android_view_Surface.h> 

using namespace android;

sp<VideoShareSlave> mVideoShareSlave;
sp<Surface> surface;  

void android_vaylb_SlavePlay_setupVideoPlay(JNIEnv *env, jclass clazz, jstring ip) {
	if(mVideoShareSlave == NULL) mVideoShareSlave = new VideoShareSlave();
	const char* chars = env->GetStringUTFChars(ip, 0);
  	String16 str_16;
  	str_16.append((const char16_t*)chars,strlen(chars));
	mVideoShareSlave->setUpReceiver(str_16);
	env->ReleaseStringUTFChars(ip, chars);
}

void android_vaylb_SlavePlay_exitVideoPlay(JNIEnv *env, jclass clazz) {
	ALOGE("vaylb-->Slave JNI exitVideoPlay");
	if(mVideoShareSlave != NULL){
		mVideoShareSlave->stop_threads();
		mVideoShareSlave.clear();
		// mVideoShareSlave = NULL;
	}
}

jboolean android_vaylb_SlavePlay_setVideoSurface(JNIEnv *env, jobject thiz, jobject jsurface){  
    surface = android_view_Surface_getSurface(env, jsurface);  
    if(android::Surface::isValid(surface)){  
		return mVideoShareSlave->setVideoSurface(surface);
    }else {  
        ALOGE("surface is invalid ");  
        return false;  
    }    
} 

void android_vaylb_SlavePlay_setSplitPlay(JNIEnv *env, jobject thiz, jint split){  
	ALOGE("vaylb-->VideoShareSlave JNI setSPlitPlay to %d",split);
	if(mVideoShareSlave != NULL){
		mVideoShareSlave->setSplitPlay(split);
	}   
} 

static JNINativeMethod gMethods[] = { 
		{ "native_setupVideoPlay", "(Ljava/lang/String;)V", (void*) android_vaylb_SlavePlay_setupVideoPlay },
		{ "native_exitVideoPlay", "()V", (void*) android_vaylb_SlavePlay_exitVideoPlay },
		{ "native_setVideoSurface", "(Landroid/view/Surface;)Z", (void*)android_vaylb_SlavePlay_setVideoSurface},
		{ "native_setSplitPlay", "(I)V", (void*)android_vaylb_SlavePlay_setSplitPlay},
};

static int register_android_vaylb_VideoShareSlave(JNIEnv *env) {
	return AndroidRuntime::registerNativeMethods(env, "com/pzhao/slave/SlavePlay",
			gMethods, NELEM(gMethods));
}

JNIEXPORT jint JNI_OnLoad(JavaVM *jvm, void *reserved) {
	JNIEnv *e;
	int status;
	if (jvm->GetEnv((void**) &e, JNI_VERSION_1_6) != JNI_OK) {
		return JNI_ERR;
	}
	ALOGE("vaylb_test-->JNI: jni_onload().");
	if ((status = register_android_vaylb_VideoShareSlave(e)) < 0) {
		ALOGE("vaylb-->jni Mainactivity registration failure, status: %d", status);
		return JNI_ERR;
	}

	return JNI_VERSION_1_6;
}


