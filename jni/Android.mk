
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := libOpenSLTest
LOCAL_SRC_FILES := com_pzhao_slave_SlavePlay.cpp NativeSlave.cpp  NativeBuffer.cpp

# for native audio
LOCAL_LDLIBS    += -lOpenSLES
# for logging
LOCAL_LDLIBS    += -llog
# for native asset manager
LOCAL_LDLIBS    += -landroid

include $(BUILD_SHARED_LIBRARY)
