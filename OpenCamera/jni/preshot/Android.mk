LOCAL_PATH := $(call my-dir)

include $(LOCAL_PATH)/../Flags.mk

LOCAL_CFLAGS += -D__STDC_CONSTANT_MACROS

LOCAL_ALLOW_UNDEFINED_SYMBOLS=false
LOCAL_MODULE    := preshot
LOCAL_SRC_FILES := preshot.cpp
LOCAL_STATIC_LIBRARIES := utils-image

LOCAL_LDLIBS := -llog

include $(BUILD_SHARED_LIBRARY)
