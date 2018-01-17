LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := yogacore

LOCAL_SRC_FILES := \
  yoga/Yoga.cpp \
  yoga/YGEnums.cpp \
	yoga/YGNodePrint.cpp

LOCAL_C_INCLUDES := $(LOCAL_PATH)
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_C_INCLUDES)

LOCAL_CFLAGS := -Wall -Werror -fexceptions -frtti -std=c++1y -O3

include $(BUILD_STATIC_LIBRARY)

