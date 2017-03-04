LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := android_vaylb_videoshareslave.cpp VideoShareSlave.cpp DataBuffer.cpp

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_SHARED_LIBRARIES := \
	libcutils libutils libui libgui libEGL libGLESv2 \
    liblog libandroid_runtime libjpeg  libstagefright_foundation libnv12rescale\

LOCAL_C_INCLUDES := \
		$(TOP)/external/jpeg \
#		$(TOP)/frameworks/native/libs/libjpeg-turbo \

LOCAL_MODULE := libvideo_share_slave

include $(BUILD_SHARED_LIBRARY)
